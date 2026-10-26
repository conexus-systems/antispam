"""
Spam reporting endpoint with PoW validation.
"""
from fastapi import APIRouter, Depends, HTTPException, Header
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession
from pydantic import BaseModel, Field
from datetime import datetime
from typing import Optional
from app.database import get_db
from app.models import PhoneHash, SpamReport, ReportCategory, ReportStatus
from app.config import settings
import structlog
import hmac
import hashlib

logger = structlog.get_logger()
router = APIRouter()


class ReportRequest(BaseModel):
    phone_hash: str = Field(..., min_length=16, max_length=16, description="HMAC-SHA256(phone, secret)[:16]")
    category: ReportCategory
    occurred_at: datetime
    pow_nonce: str = Field(..., description="Proof-of-work nonce (validated by middleware)")
    pow_difficulty: int = Field(..., ge=1, le=8, description="PoW difficulty used")
    client_id: str = Field(..., description="HMAC(device_id, salt) - anonymous reporter ID")


class ReportResponse(BaseModel):
    success: bool
    report_id: Optional[int] = None
    message: str


@router.post("/reports", response_model=ReportResponse)
async def create_report(
    request: ReportRequest,
    db: AsyncSession = Depends(get_db),
    api_key: Optional[str] = Header(None, alias="X-API-Key")
):
    """
    Registrar denúncia de spam.
    
    Requer:
    - **phone_hash**: HMAC do número (16 chars hex)
    - **category**: Categoria da denúncia
    - **occurred_at**: Timestamp da chamada (ISO 8601)
    - **pow_nonce**: Nonce do Proof-of-Work (header X-PoW-Nonce validado por middleware)
    - **pow_difficulty**: Dificuldade usada
    - **client_id**: ID anônimo do denunciante (HMAC device)
    
    Header opcional:
    - **X-API-Key**: Chave de API para rate limit diferenciado
    """
    # Validar phone_hash (formato hex 16 chars)
    if len(request.phone_hash) != 16 or not all(c in "0123456789abcdef" for c in request.phone_hash.lower()):
        raise HTTPException(400, "Invalid phone_hash format (must be 16 hex chars)")

    # Verificar/criar PhoneHash
    phone_hash_obj = await db.execute(
        select(PhoneHash).where(PhoneHash.phone_hmac == request.phone_hash.lower())
    )
    phone_hash_obj = phone_hash_obj.scalar_one_or_none()

    if not phone_hash_obj:
        phone_hash_obj = PhoneHash(
            phone_hmac=request.phone_hash.lower(),
            country="BR"  # Default, poderia extrair do prefixo
        )
        db.add(phone_hash_obj)
        await db.flush()

    # Criar denúncia
    report = SpamReport(
        phone_hmac=request.phone_hash.lower(),
        category=request.category.value,
        reporter_hash=request.client_id,
        occurred_at=request.occurred_at,
        status=ReportStatus.PENDING,
        contest_deadline=datetime.utcnow().replace(day=datetime.utcnow().day + settings.REPUTATION_CONTEST_WINDOW_DAYS)
    )
    db.add(report)
    await db.flush()

    # Log de auditoria
    from app.models import AuditEvent
    audit = AuditEvent(
        actor_id=None,  # Anônimo
        action="create_report",
        target_type="spam_report",
        target_id=str(report.id),
        details={
            "phone_hash": request.phone_hash,
            "category": request.category.value,
            "client_id": request.client_id[:8] + "..."  # Truncado para log
        }
    )
    db.add(audit)

    await db.commit()

    logger.info("report_created", report_id=report.id, phone_hash=request.phone_hash, category=request.category.value)

    return ReportResponse(
        success=True,
        report_id=report.id,
        message="Denúncia registrada. Será processada pela moderação."
    )


@router.get("/reports/stats")
async def report_stats(db: AsyncSession = Depends(get_db)):
    """Estatísticas de denúncias (para dashboard/admin)."""
    from sqlalchemy import func
    
    # Total por status
    status_query = select(SpamReport.status, func.count(SpamReport.id)).group_by(SpamReport.status)
    status_result = await db.execute(status_query)
    by_status = dict(status_result.all())
    
    # Total por categoria
    cat_query = select(SpamReport.category, func.count(SpamReport.id)).group_by(SpamReport.category)
    cat_result = await db.execute(cat_query)
    by_category = dict(cat_result.all())
    
    # Últimas 24h
    from datetime import timedelta
    since = datetime.utcnow() - timedelta(hours=24)
    recent_query = select(func.count(SpamReport.id)).where(SpamReport.created_at >= since)
    recent_result = await db.execute(recent_query)
    recent_24h = recent_result.scalar() or 0

    return {
        "by_status": by_status,
        "by_category": by_category,
        "recent_24h": recent_24h
    }