"""
Dataset Publisher - Gera dataset assinado Ed25519 + deltas incrementais.
Publica no Object Storage (Linode S3-compat) com manifest verificável.
"""
import json
import hashlib
import logging
from datetime import datetime, timezone
from pathlib import Path
from typing import List, Dict, Any, Optional
from cryptography.hazmat.primitives import serialization, hashes
from cryptography.hazmat.primitives.asymmetric import ed25519
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.models import ReputationScore, PhoneHash, DatasetManifest, BusinessListing, SpamReport, ReportStatus, ReportCategory
from app.config import settings
from app.services.reputation_engine import ReputationEngine

logger = logging.getLogger(__name__)


class DatasetPublisher:
    """
    Publica dataset de reputação assinado com Ed25519.
    
    Formato:
    - Manifest: version, entries_count, digest_sha256, signature_ed25519, public_key, published_at, previous_version
    - Delta: JSON array de entradas (phone_hmac, country, category, score, reports, unique_reporters, first_seen, last_seen, confidence, false_positives)
    - Armazenado em Object Storage (S3-compat)
    """
    
    def __init__(self, db: AsyncSession):
        self.db = db
        self.reputation_engine = ReputationEngine(db)
        self.private_key = self._load_private_key()
        self.public_key = self.private_key.public_key()

    def _load_private_key(self) -> ed25519.Ed25519PrivateKey:
        """Carrega chave privada Ed25519."""
        # PRODUÇÃO: usar HSM/vault (AWS KMS, HashiCorp Vault, Azure Key Vault)
        # DEV: arquivo local
        key_path = Path(settings.ED25519_PRIVATE_KEY_PATH)
        if not key_path.exists():
            raise FileNotFoundError(f"Ed25519 private key not found: {key_path}")
        
        with open(key_path, "rb") as f:
            return serialization.load_pem_private_key(f.read(), password=None)

    def _get_public_key_der_hex(self) -> str:
        """Retorna chave pública em DER hex para inclusão no manifest."""
        public_key_der = self.public_key.public_bytes(
            encoding=serialization.Encoding.DER,
            format=serialization.PublicFormat.SubjectPublicKeyInfo
        )
        return public_key_der.hex()

    async def publish_dataset(self, force: bool = False) -> Optional[DatasetManifest]:
        """
        Publica novo dataset se houver mudanças significativas.
        
        Critérios:
        - Pelo menos uma entrada elegível (ReputationEngine.get_publication_candidates)
        - Force=True ignora verificações
        
        Retorna DatasetManifest criado ou None se nada para publicar.
        """
        # 1. Busca candidatos elegíveis
        candidates = await self.reputation_engine.get_publication_candidates()
        if not candidates and not force:
            logger.info("DatasetPublisher: no eligible candidates for publication")
            return None

        # 2. Busca última versão publicada
        last_manifest = await self._get_latest_manifest()
        new_version = (last_manifest.version + 1) if last_manifest else 1

        # 3. Monta entries do dataset
        entries = await self._build_dataset_entries(candidates)
        if not entries and not force:
            logger.info("DatasetPublisher: no entries to publish")
            return None

        # 4. Gera manifest
        manifest = await self._create_manifest(
            version=new_version,
            entries=entries,
            previous_version=last_manifest.version if last_manifest else None
        )

        # 5. Salva manifest no banco
        self.db.add(manifest)
        await self.db.commit()

        # 6. Upload delta para Object Storage
        await self._upload_delta(manifest.version, manifest.entries_json)

        logger.info(f"DatasetPublisher: published v{manifest.version} with {manifest.entries_count} entries")
        return manifest

    async def _build_dataset_entries(self, candidates: List[tuple]) -> List[Dict[str, Any]]:
        """Constrói lista de entradas do dataset a partir dos candidatos."""
        entries = []
        
        for phone_hmac, weight_sum, mature_weight, network_count in candidates:
            # Busca score completo
            score_query = select(ReputationScore, PhoneHash).join(
                PhoneHash, ReputationScore.phone_hmac == PhoneHash.phone_hmac
            ).where(ReputationScore.phone_hmac == phone_hmac)
            
            result = await self.db.execute(score_query)
            row = result.first()
            
            if not row:
                continue
                
            score, phone = row
            
            # Busca categoria dominante (de business_listings ou denúncias)
            category = await self._get_dominant_category(phone_hmac)
            
            # Busca business listing se houver
            business = await self._get_business_listing(phone_hmac)
            
            entry = {
                "key": phone.phone_hmac,
                "country": phone.country,
                "category": category,
                "score": score.score,
                "reports": score.total_reports,
                "uniqueReporters": score.unique_reporters,
                "firstSeen": score.last_updated.isoformat(),  # Simplificado
                "lastSeen": score.last_updated.isoformat(),
                "confidence": score.confidence,
                "falsePositives": 0,
            }
            
            if business:
                entry["businessName"] = business.name
                entry["businessCategory"] = business.category
                entry["businessSource"] = business.source
                entry["businessConfidence"] = business.confidence
            
            entries.append(entry)
        
        return entries

    async def _get_dominant_category(self, phone_hmac: str) -> str:
        """Determina categoria dominante baseada em denúncias aprovadas."""
        query = select(SpamReport.category, func.count(SpamReport.id)).where(
            and_(
                SpamReport.phone_hmac == phone_hmac,
                SpamReport.status == ReportStatus.APPROVED
            )
        ).group_by(SpamReport.category).order_by(func.count(SpamReport.id).desc()).limit(1)
        
        result = await self.db.execute(query)
        row = result.first()
        return row.category if row else "unknown"

    async def _get_business_listing(self, phone_hmac: str) -> Optional[BusinessListing]:
        """Busca business listing verificado para o phone_hmac."""
        query = select(BusinessListing).where(
            BusinessListing.phone_hmac == phone_hmac
        ).order_by(BusinessListing.confidence.desc()).limit(1)
        
        result = await self.db.execute(query)
        return result.scalar_one_or_none()

    async def _create_manifest(
        self,
        version: int,
        entries: List[Dict[str, Any]],
        previous_version: Optional[int]
    ) -> DatasetManifest:
        """Cria manifest assinado Ed25519."""
        now = datetime.now(timezone.utc)
        entries_json = json.dumps(entries, separators=(",", ":"), ensure_ascii=False)
        digest = hashlib.sha256(entries_json.encode()).hexdigest()
        
        # Dados para assinar (sem entriesJson)
        manifest_data = {
            "version": version,
            "entries": len(entries),
            "digest": digest,
            "publishedAt": now.isoformat().replace("+00:00", "Z"),
            "previousVersion": previous_version,
        }
        manifest_json = json.dumps(manifest_data, separators=(",", ":"), ensure_ascii=False)
        
        # Assinatura Ed25519
        signature = self.private_key.sign(manifest_json.encode()).hex()
        public_key_hex = self._get_public_key_der_hex()
        
        manifest = DatasetManifest(
            version=version,
            entries_count=len(entries),
            digest_sha256=digest,
            signature_ed25519=signature,
            public_key_ed25519=public_key_hex,
            published_at=now,
            previous_version=previous_version,
            entries_json=entries_json,  # Armazenado para verificação
        )
        
        return manifest

    async def _upload_delta(self, version: int, entries_json: str) -> None:
        """Upload delta para Object Storage (Linode S3-compat / Tigris)."""
        # TODO: Implementar upload real
        # Exemplo com boto3:
        # import boto3
        # s3 = boto3.client('s3', endpoint_url=settings.STORAGE_ENDPOINT, ...)
        # s3.put_object(
        #     Bucket=settings.STORAGE_BUCKET,
        #     Key=f"datasets/v{version}/delta.json",
        #     Body=entries_json.encode(),
        #     ContentType="application/json",
        # )
        
        # Por enquanto, salva localmente para dev
        delta_dir = Path("storage/deltas")
        delta_dir.mkdir(parents=True, exist_ok=True)
        delta_path = delta_dir / f"v{version}_delta.json"
        delta_path.write_text(entries_json)
        logger.info(f"Delta saved locally: {delta_path}")

    async def _get_latest_manifest(self) -> Optional[DatasetManifest]:
        """Busca manifest mais recente publicado."""
        query = select(DatasetManifest).order_by(DatasetManifest.version.desc()).limit(1)
        result = await self.db.execute(query)
        return result.scalar_one_or_none()

    async def get_delta(self, version: int) -> Optional[Dict[str, Any]]:
        """Busca delta de uma versão específica (para sync do device)."""
        # Tenta ler do Object Storage
        # delta_path = f"datasets/v{version}/delta.json"
        # Por enquanto, lê do banco
        query = select(DatasetManifest).where(DatasetManifest.version == version)
        result = await self.db.execute(query)
        manifest = result.scalar_one_or_none()
        
        if not manifest:
            return None
            
        return {
            "version": manifest.version,
            "entries": json.loads(manifest.entries_json) if manifest.entries_json else [],
            "digest": manifest.digest_sha256,
            "signature": manifest.signature_ed25519,
            "publicKey": manifest.public_key_ed25519,
        }

    async def get_latest_manifest_response(self) -> Optional[Dict[str, Any]]:
        """Retorna manifest mais recente para endpoint /v1/datasets/latest."""
        manifest = await self._get_latest_manifest()
        if not manifest:
            return None
            
        return {
            "version": manifest.version,
            "entries": manifest.entries_count,
            "digest": manifest.digest_sha256,
            "signature": manifest.signature_ed25519,
            "publicKey": manifest.public_key_ed25519,
            "publishedAt": manifest.published_at.isoformat().replace("+00:00", "Z"),
            "previousVersion": manifest.previous_version,
        }

    async def get_history(self, limit: int = 20) -> List[Dict[str, Any]]:
        """Retorna histórico de manifests publicados."""
        query = select(DatasetManifest).order_by(DatasetManifest.version.desc()).limit(limit)
        result = await self.db.execute(query)
        manifests = result.scalars().all()
        
        return [
            {
                "version": m.version,
                "entries": m.entries_count,
                "digest": m.digest_sha256,
                "signature": m.signature_ed25519,
                "publicKey": m.public_key_ed25519,
                "publishedAt": m.published_at.isoformat().replace("+00:00", "Z"),
                "previousVersion": m.previous_version,
            }
            for m in manifests
        ]