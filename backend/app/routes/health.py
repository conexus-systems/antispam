"""
Health check endpoints.
"""
from fastapi import APIRouter, Depends
from sqlalchemy import text
from sqlalchemy.ext.asyncio import AsyncSession
import redis.asyncio as redis
from app.database import get_db
from app.config import settings
import structlog

logger = structlog.get_logger()
router = APIRouter()


@router.get("/health")
async def health_check():
    """Health check básico - não verifica dependências."""
    return {
        "status": "healthy",
        "service": "conexus-caller-id-api",
        "version": settings.APP_VERSION
    }


@router.get("/health/ready")
async def readiness_check(db: AsyncSession = Depends(get_db)):
    """Readiness check - verifica DB e Redis."""
    checks = {}
    overall = "ready"

    # Check database
    try:
        await db.execute(text("SELECT 1"))
        checks["database"] = "ok"
    except Exception as e:
        checks["database"] = f"error: {str(e)}"
        overall = "not_ready"

    # Check Redis
    try:
        redis_client = redis.from_url(settings.REDIS_URL)
        await redis_client.ping()
        await redis_client.close()
        checks["redis"] = "ok"
    except Exception as e:
        checks["redis"] = f"error: {str(e)}"
        overall = "not_ready"

    return {
        "status": overall,
        "checks": checks,
        "service": "conexus-caller-id-api",
        "version": settings.APP_VERSION
    }


@router.get("/health/live")
async def liveness_check():
    """Liveness check - apenas verifica se o processo está vivo."""
    return {
        "status": "alive",
        "service": "conexus-caller-id-api"
    }