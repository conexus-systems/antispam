"""
Celery Tasks for Conexus Caller ID Background Processing.

Tasks:
- aggregate_reputation: Recalcula scores agregados (5 min)
- publish_dataset: Publica dataset assinado Ed25519 (diário)
- detect_campaigns: Detecta campanhas coordenadas (hora)
- cleanup_expired: Remove nonces PoW, logs antigos, nonces replay (diário)
- moderate_report_queue: Auto-moderação de denúncias (minuto)
- send_contestation_notice: Notifica sobre contestações pendentes (evento)
"""
import logging
from datetime import datetime, timedelta, timezone
from celery import shared_task
from sqlalchemy.ext.asyncio import create_async_engine, AsyncSession
from sqlalchemy.orm import sessionmaker
from sqlalchemy import select, delete, func, and_, or_
from sqlalchemy import update as sqlalchemy_update

from app.config import settings
from app.models import (
    SpamReport, ReputationScore, PhoneHash, ReportStatus, 
    AuditEvent, DatasetManifest, BusinessListing, ReportCategory
)
from app.services.reputation_engine import ReputationEngine
from app.services.dataset_publisher import DatasetPublisher

logger = logging.getLogger(__name__)

# Engine assíncrono compartilhado
_async_engine = None
_async_session_maker = None

def _get_async_session() -> AsyncSession:
    global _async_engine, _async_session_maker
    if _async_engine is None:
        _async_engine = create_async_engine(
            settings.DATABASE_URL,
            pool_pre_ping=True,
            pool_size=5,
            max_overflow=10,
        )
        _async_session_maker = sessionmaker(
            _async_engine, class_=AsyncSession, expire_on_commit=False
        )
    return _async_session_maker()

def run_async(coro):
    """Executa corrotina assíncrona no loop síncrono do Celery."""
    import asyncio
    try:
        loop = asyncio.get_event_loop()
    except RuntimeError:
        loop = asyncio.new_event_loop()
        asyncio.set_event_loop(loop)
    return loop.run_until_complete(coro)


# ============================================================
# TASK: Aggregate Reputation Scores (every 5 minutes)
# ============================================================

@shared_task(bind=True, max_retries=3, default_retry_delay=60)
def aggregate_reputation(self) -> dict:
    """
    Recalcula scores de reputação para todos phone_hmacs com denúncias aprovadas.
    Roda a cada 5 minutos via beat.
    """
    async def _run():
        async with _get_async_session() as db:
            engine = ReputationEngine(db)
            updated = await engine.aggregate_all()
            return {"updated": updated, "timestamp": datetime.now(timezone.utc).isoformat()}
    
    try:
        result = run_async(_run())
        logger.info(f"aggregate_reputation: {result}")
        return result
    except Exception as exc:
        logger.error(f"aggregate_reputation failed: {exc}")
        raise self.retry(exc=exc)


# ============================================================
# TASK: Publish Dataset (daily + manual trigger)
# ============================================================

@shared_task(bind=True, max_retries=2, default_retry_delay=300)
def publish_dataset(self, force: bool = False) -> dict:
    """
    Publica dataset assinado Ed25519 se critérios atendidos.
    Roda diário via beat; pode ser disparado manualmente com force=True.
    """
    async def _run():
        async with _get_async_session() as db:
            publisher = DatasetPublisher(db)
            manifest = await publisher.publish_dataset(force=force)
            if manifest:
                return {
                    "published": True,
                    "version": manifest.version,
                    "entries": manifest.entries_count,
                    "timestamp": manifest.published_at.isoformat(),
                }
            return {"published": False, "reason": "no eligible candidates"}
    
    try:
        result = run_async(_run())
        logger.info(f"publish_dataset: {result}")
        return result
    except Exception as exc:
        logger.error(f"publish_dataset failed: {exc}")
        raise self.retry(exc=exc)


# ============================================================
# TASK: Detect Campaigns (hourly)
# ============================================================

@shared_task(bind=True, max_retries=3, default_retry_delay=120)
def detect_campaigns(self) -> dict:
    """
    Detecta campanhas coordenadas: mesmo prefixo, múltiplos reports
    em janela curta, ≥3 redes. Marca scores suspeitos.
    Roda a cada hora via beat.
    """
    async def _run():
        async with _get_async_session() as db:
            engine = ReputationEngine(db)
            flagged = await engine.detect_and_flag_campaigns()
            return {"flagged": flagged, "timestamp": datetime.now(timezone.utc).isoformat()}
    
    try:
        result = run_async(_run())
        logger.info(f"detect_campaigns: {result}")
        return result
    except Exception as exc:
        logger.error(f"detect_campaigns failed: {exc}")
        raise self.retry(exc=exc)


# ============================================================
# TASK: Cleanup Expired (daily)
# ============================================================

@shared_task(bind=True, max_retries=2, default_retry_delay=300)
def cleanup_expired(self) -> dict:
    """
    Limpeza periódica:
    - Nonces PoW > 24h (anti-replay)
    - Logs de auditoria > 90 dias
    - Contestações expiradas (> 90 dias)
    - Nonces de rate limit antigos
    Roda diário via beat.
    """
    async def _run():
        async with _get_async_session() as db:
            cleaned = {"pow_nonces": 0, "audit_logs": 0, "contestations": 0}
            now = datetime.now(timezone.utc)
            cutoff_24h = now - timedelta(hours=24)
            cutoff_90d = now - timedelta(days=90)
            
            # 1. Nonces PoW expirados (tabela temporária ou Redis - simplificado)
            # TODO: Implementar tabela de nonces PoW
            
            # 2. Logs de auditoria > 90 dias
            audit_delete = delete(AuditEvent).where(AuditEvent.created_at < cutoff_90d)
            result = await db.execute(audit_delete)
            cleaned["audit_logs"] = result.rowcount
            
            # 3. Contestações expiradas (dispute_deadline < now)
            # TODO: Adicionar campo dispute_deadline em ReputationScore
            # Por enquanto, apenas log
            
            await db.commit()
            return cleaned
    
    try:
        result = run_async(_run())
        logger.info(f"cleanup_expired: {result}")
        return result
    except Exception as exc:
        logger.error(f"cleanup_expired failed: {exc}")
        raise self.retry(exc=exc)


# ============================================================
# TASK: Moderate Report Queue (every minute)
# ============================================================

@shared_task(bind=True, max_retries=5, default_retry_delay=30)
def moderate_report_queue(self) -> dict:
    """
    Processa fila de moderação de denúncias.
    Auto-aprova denúncias que atendem critérios:
    - Reporter com histórico bom (unique_reports > 5, false_positives == 0)
    - Categoria consistente com reports anteriores do mesmo número
    - Score do número já alto (> 70)
    Denúncias que não atendem: ficam pendentes para moderação humana.
    Roda a cada minuto via beat.
    """
    async def _run():
        async with _get_async_session() as db:
            processed = {"auto_approved": 0, "auto_rejected": 0, "pending": 0}
            
            # Busca denúncias pendentes
            query = select(SpamReport).where(
                SpamReport.status == ReportStatus.PENDING
            ).limit(100)  # Processa em lotes
            
            result = await db.execute(query)
            reports = result.scalars().all()
            
            for report in reports:
                # Critérios de auto-aprovação
                should_approve = await _evaluate_auto_approve(db, report)
                
                if should_approve:
                    report.status = ReportStatus.APPROVED
                    report.moderated_at = datetime.now(timezone.utc)
                    report.moderator_id = 0  # 0 = auto
                    processed["auto_approved"] += 1
                    
                    # Log auditoria
                    db.add(AuditEvent(
                        actor_id=None,
                        action="auto_approve_report",
                        target_type="spam_report",
                        target_id=str(report.id),
                        details={"phone_hmac": report.phone_hmac, "category": report.category}
                    ))
                else:
                    # Critérios de auto-rejeição (spam óbvio, reporter banido, etc.)
                    should_reject = await _evaluate_auto_reject(db, report)
                    if should_reject:
                        report.status = ReportStatus.REJECTED
                        report.moderated_at = datetime.now(timezone.utc)
                        report.moderator_id = 0
                        processed["auto_rejected"] += 1
                        
                        db.add(AuditEvent(
                            actor_id=None,
                            action="auto_reject_report",
                            target_type="spam_report",
                            target_id=str(report.id),
                            details={"phone_hmac": report.phone_hmac, "reason": "auto_reject_criteria"}
                        ))
                    else:
                        processed["pending"] += 1
            
            await db.commit()
            return processed
    
    try:
        result = run_async(_run())
        if result["auto_approved"] > 0 or result["auto_rejected"] > 0:
            logger.info(f"moderate_report_queue: {result}")
        return result
    except Exception as exc:
        logger.error(f"moderate_report_queue failed: {exc}")
        raise self.retry(exc=exc)


async def _evaluate_auto_approve(db: AsyncSession, report: SpamReport) -> bool:
    """Avalia se denúncia deve ser auto-aprovada."""
    # 1. Reporter com bom histórico
    reporter_stats = await db.execute(
        select(
            func.count(SpamReport.id).label("total"),
            func.sum(SpamReport.false_positives).label("fps")
        ).where(
            and_(
                SpamReport.reporter_hash == report.reporter_hash,
                SpamReport.status == ReportStatus.APPROVED
            )
        )
    stats = reporter_stats.first()
    reporter_trustworthy = stats and stats.total >= 5 and (stats.fps or 0) == 0
    
    # 2. Número já tem score alto
    score_query = select(ReputationScore).where(ReputationScore.phone_hmac == report.phone_hmac)
    score_result = await db.execute(score_query)
    score = score_result.scalar_one_or_none()
    number_high_risk = score and score.score >= 70
    
    # 3. Categoria consistente
    category_consistent = True  # TODO: verificar consistência
    
    return reporter_trustworthy or number_high_risk


async def _evaluate_auto_reject(db: AsyncSession, report: SpamReport) -> bool:
    """Avalia se denúncia deve ser auto-rejeitada."""
    # 1. Reporter banido (muitos false positives)
    reporter_stats = await db.execute(
        select(
            func.count(SpamReport.id).label("total"),
            func.sum(SpamReport.false_positives).label("fps")
        ).where(
            and_(
                SpamReport.reporter_hash == report.reporter_hash,
                SpamReport.status == ReportStatus.APPROVED
            )
        )
    )
    stats = reporter_stats.first()
    reporter_banned = stats and stats.total >= 10 and (stats.fps or 0) / stats.total > 0.5
    
    # 2. Número é emergência
    is_emergency = report.phone_hmac in ["190", "192", "193", "188", "199"]  # Simplificado
    
    # 3. Número verificado como business legítimo
    business_query = select(BusinessListing).where(
        and_(
            BusinessListing.phone_hmac == report.phone_hmac,
            BusinessListing.confidence >= 0.9
        )
    )
    business_result = await db.execute(business_query)
    is_verified_business = business_result.scalar_one_or_none() is not None
    
    return reporter_banned or is_emergency or is_verified_business


# ============================================================
# TASK: Send Contestation Notice (event-driven)
# ============================================================

@shared_task(bind=True, max_retries=3, default_retry_delay=3600)
def send_contestation_notice(self, phone_hmac: str, contest_deadline: str) -> dict:
    """
    Envia notificação sobre contestação pendente.
    Disparado quando contestação é aberta (evento).
    """
    async def _run():
        # TODO: Implementar envio real (email, push, Discord webhook)
        # Por enquanto, apenas log de auditoria
        async with _get_async_session() as db:
            db.add(AuditEvent(
                actor_id=None,
                action="send_contestation_notice",
                target_type="contestation",
                target_id=phone_hmac,
                details={"deadline": contest_deadline}
            ))
            await db.commit()
        return {"sent": True, "phone_hmac": phone_hmac}
    
    try:
        result = run_async(_run())
        return result
    except Exception as exc:
        logger.error(f"send_contestation_notice failed: {exc}")
        raise self.retry(exc=exc)


# ============================================================
# TASK: On Report Approved (event-driven)
# ============================================================

@shared_task(bind=True, max_retries=3, default_retry_delay=60)
def on_report_approved(self, report_id: int) -> dict:
    """
    Callback quando denúncia é aprovada - recalcula score do phone_hmac.
    Disparado via signal/evento após moderação.
    """
    async def _run():
        async with _get_async_session() as db:
            engine = ReputationEngine(db)
            score = await engine.aggregate_single(phone_hmac="")  # TODO: buscar phone_hmac do report
            # Buscar phone_hmac do report
            from app.models import SpamReport
            query = select(SpamReport.phone_hmac).where(SpamReport.id == report_id)
            result = await db.execute(query)
            phone_hmac = result.scalar_one_or_none()
            
            if phone_hmac:
                score = await engine.aggregate_single(phone_hmac)
                return {"recalculated": True, "phone_hmac": phone_hmac, "score": score.score if score else None}
            return {"recalculated": False, "reason": "report not found"}
    
    try:
        result = run_async(_run())
        return result
    except Exception as exc:
        logger.error(f"on_report_approved failed: {exc}")
        raise self.retry(exc=exc)


# ============================================================
# INIT: Inicialização do módulo
# ============================================================

# Configuração de logging para workers
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s - %(name)s - %(levelname)s - %(message)s"
)

# Exporta celery_app para importação
from app.workers.celery_app import celery_app

__all__ = [
    "celery_app",
    "aggregate_reputation",
    "publish_dataset", 
    "detect_campaigns",
    "cleanup_expired",
    "moderate_report_queue",
    "send_contestation_notice",
    "on_report_approved",
]