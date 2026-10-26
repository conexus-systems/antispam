"""
Reputation score lookup by phone hash (HMAC).
"""
from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession
from pydantic import BaseModel
from typing import Optional
from app.database import get_db
from app.models import ReputationScore, PhoneHash, BusinessListing
import structlog

logger = structlog.get_logger()
router = APIRouter()


class ReputationDetail(BaseModel):
    phone_hash: str
    country: str
    score: int
    confidence: float
    total_reports: int
    unique_reporters: int
    weight_sum: float
    mature_weight: float
    last_updated: str
    business_name: Optional[str] = None
    business_category: Optional[str] = None
    business_source: Optional[str] = None
    business_confidence: Optional[float] = None


@router.get("/reputation/{phone_hash}", response_model=ReputationDetail)
async def get_reputation(
    phone_hash: str,
    db: AsyncSession = Depends(get_db)
):
    """
    Obter detalhes de reputação por hash do telefone.
    
    - **phone_hash**: HMAC-SHA256(phone, secret)[:16] (16 chars hex)
    """
    if len(phone_hash) != 16 or not all(c in "0123456789abcdef" for c in phone_hash.lower()):
        raise HTTPException(400, "Invalid phone_hash format")

    # Buscar score + phone_hash + business listing
    query = (
        select(ReputationScore, PhoneHash, BusinessListing)
        .join(PhoneHash, ReputationScore.phone_hmac == PhoneHash.phone_hmac)
        .outerjoin(BusinessListing, PhoneHash.phone_hmac == BusinessListing.phone_hmac)
        .where(ReputationScore.phone_hmac == phone_hash.lower())
    )
    
    result = await db.execute(query)
    row = result.first()

    if not row:
        raise HTTPException(404, "Reputation not found for this phone hash")

    score, phone, business = row

    return ReputationDetail(
        phone_hash=phone.phone_hmac,
        country=phone.country,
        score=score.score,
        confidence=score.confidence,
        total_reports=score.total_reports,
        unique_reporters=score.unique_reporters,
        weight_sum=score.weight_sum,
        mature_weight=score.mature_weight,
        last_updated=score.last_updated.isoformat(),
        business_name=business.name if business else None,
        business_category=business.category if business else None,
        business_source=business.source if business else None,
        business_confidence=business.confidence if business else None
    )