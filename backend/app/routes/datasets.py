"""
Signed dataset manifest and delta endpoints.
"""
from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession
from pydantic import BaseModel
from typing import List, Optional
from datetime import datetime
from app.database import get_db
from app.models import DatasetManifest, ReputationScore, PhoneHash
from app.config import settings
import structlog
import json
import hashlib

logger = structlog.get_logger()
router = APIRouter()


class DatasetManifestResponse(BaseModel):
    version: int
    entries_count: int
    digest_sha256: str
    signature_ed25519: str
    public_key_ed25519: str
    published_at: str
    previous_version: Optional[int] = None


class DeltaEntry(BaseModel):
    key: str
    country: str
    category: str
    score: int
    reports: int
    unique_reporters: int
    first_seen: str
    last_seen: str
    confidence: float
    false_positives: int


class DeltaResponse(BaseModel):
    version: int
    entries: List[DeltaEntry]


@router.get("/datasets/latest", response_model=DatasetManifestResponse)
async def get_latest_dataset_manifest(db: AsyncSession = Depends(get_db)):
    """
    Obter manifest do dataset assinado mais recente (Ed25519).
    Público - não requer autenticação.
    """
    query = select(DatasetManifest).order_by(DatasetManifest.version.desc()).limit(1)
    result = await db.execute(query)
    manifest = result.scalar_one_or_none()

    if not manifest:
        raise HTTPException(404, "No dataset published yet")

    return DatasetManifestResponse(
        version=manifest.version,
        entries_count=manifest.entries_count,
        digest_sha256=manifest.digest_sha256,
        signature_ed25519=manifest.signature_ed25519,
        public_key_ed25519=manifest.public_key_ed25519,
        published_at=manifest.published_at.isoformat(),
        previous_version=manifest.previous_version
    )


@router.get("/datasets/{version}/delta", response_model=DeltaResponse)
async def get_dataset_delta(
    version: int,
    db: AsyncSession = Depends(get_db)
):
    """
    Obter delta do dataset para uma versão específica.
    Público - não requer autenticação.
    """
    # Verificar se manifest existe
    manifest = await db.execute(
        select(DatasetManifest).where(DatasetManifest.version == version)
    )
    manifest = manifest.scalar_one_or_none()
    
    if not manifest:
        raise HTTPException(404, f"Dataset version {version} not found")

    # Buscar entradas dessa versão (simplificado - em produção viria de Object Storage)
    # Aqui retornamos scores atuais como delta
    query = (
        select(ReputationScore, PhoneHash)
        .join(PhoneHash, ReputationScore.phone_hmac == PhoneHash.phone_hmac)
        .where(ReputationScore.score >= 30)
        .order_by(ReputationScore.score.desc())
        .limit(10000)
    )
    
    result = await db.execute(query)
    rows = result.all()

    entries = []
    for score, phone in rows:
        entries.append(DeltaEntry(
            key=phone.phone_hmac,
            country=phone.country,
            category="unknown",
            score=score.score,
            reports=score.total_reports,
            unique_reporters=score.unique_reporters,
            first_seen=score.last_updated.isoformat(),
            last_seen=score.last_updated.isoformat(),
            confidence=score.confidence,
            false_positives=0
        ))

    return DeltaResponse(version=version, entries=entries)


@router.get("/datasets/history")
async def dataset_history(
    limit: int = Query(20, ge=1, le=100),
    db: AsyncSession = Depends(get_db)
):
    """Histórico de manifests publicados."""
    query = select(DatasetManifest).order_by(DatasetManifest.version.desc()).limit(limit)
    result = await db.execute(query)
    manifests = result.scalars().all()

    return [
        DatasetManifestResponse(
            version=m.version,
            entries_count=m.entries_count,
            digest_sha256=m.digest_sha256,
            signature_ed25519=m.signature_ed25519,
            public_key_ed25519=m.public_key_ed25519,
            published_at=m.published_at.isoformat(),
            previous_version=m.previous_version
        )
        for m in manifests
    ]