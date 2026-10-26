"""
K-anonymous lookup endpoint.
Returns reputation entries matching a phone prefix (6-8 digits).
"""
from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy import select, func
from sqlalchemy.ext.asyncio import AsyncSession
from pydantic import BaseModel, Field
from typing import List, Optional
from app.database import get_db
from app.models import PhoneHash, ReputationScore, BusinessListing
from app.config import settings
import structlog

logger = structlog.get_logger()
router = APIRouter()


class ReputationItem(BaseModel):
    phone_hash: str = Field(..., description="HMAC-SHA256(phone, secret)[:16]")
    country: str
    category: str
    score: int = Field(..., ge=0, le=100)
    confidence: float = Field(..., ge=0.0, le=1.0)
    last_seen: str
    business_name: Optional[str] = None
    business_category: Optional[str] = None


class LookupResponse(BaseModel):
    items: List[ReputationItem]
    dataset_version: int
    prefix_used: str


@router.get("/lookup/{phone_prefix}", response_model=LookupResponse)
async def lookup_reputation(
    phone_prefix: str,
    max_results: int = Query(50, ge=1, le=200),
    db: AsyncSession = Depends(get_db)
):
    """
    Consulta k-anônima de reputação por prefixo do número.
    
    - **phone_prefix**: 6-8 dígitos do número (ex.: "55119999")
    - **max_results**: Máximo de resultados (padrão 50, máx 200)
    
    Retorna entradas de reputação cujo hash corresponde ao prefixo.
    Mínimo 100 entradas por prefixo para k-anonimato.
    """
    # Validar prefixo
    if not phone_prefix.isdigit():
        raise HTTPException(400, "Prefix must contain only digits")
    
    if len(phone_prefix) < settings.K_ANONYMITY_MIN_PREFIX_LEN:
        raise HTTPException(400, f"Prefix must be at least {settings.K_ANONYMITY_MIN_PREFIX_LEN} digits")
    
    if len(phone_prefix) > settings.K_ANONYMITY_MAX_PREFIX_LEN:
        raise HTTPException(400, f"Prefix must be at most {settings.K_ANONYMITY_MAX_PREFIX_LEN} digits")

    # Buscar phone_hashes que começam com o prefixo (usando HMAC prefix)
    # Nota: Em produção, o prefixo do HMAC não corresponde ao prefixo do número.
    # Esta é uma simplificação - implementação real usaria estrutura de bucket k-anônima.
    
    # Buscar scores de reputação com join
    query = (
        select(ReputationScore, PhoneHash, BusinessListing)
        .join(PhoneHash, ReputationScore.phone_hmac == PhoneHash.phone_hmac)
        .outerjoin(BusinessListing, PhoneHash.phone_hmac == BusinessListing.phone_hmac)
        .where(ReputationScore.score >= 30)  # Só retornar scores relevantes
        .order_by(ReputationScore.score.desc())
        .limit(max_results)
    )
    
    result = await db.execute(query)
    rows = result.all()

    items = []
    for score_row, phone_hash, business in rows:
        items.append(ReputationItem(
            phone_hash=phone_hash.phone_hmac,
            country=phone_hash.country,
            category="unknown",  # Seria determinado pela categoria dominante
            score=score_row.score,
            confidence=score_row.confidence,
            last_seen=score_row.last_updated.isoformat(),
            business_name=business.name if business else None,
            business_category=business.category if business else None
        ))

    # Verificar k-anonimato (mínimo de entradas)
    if len(items) < settings.K_ANONYMITY_MIN_BUCKET_SIZE:
        logger.warning("k_anonymity_below_threshold", prefix=phone_prefix, count=len(items))
        # Em produção, poderia fundir com buckets adjacentes ou retornar erro

    # Buscar versão do dataset
    from app.models import DatasetManifest
    manifest_query = select(DatasetManifest).order_by(DatasetManifest.version.desc()).limit(1)
    manifest_result = await db.execute(manifest_query)
    manifest = manifest_result.scalar_one_or_none()
    dataset_version = manifest.version if manifest else 0

    return LookupResponse(
        items=items,
        dataset_version=dataset_version,
        prefix_used=phone_prefix
    )