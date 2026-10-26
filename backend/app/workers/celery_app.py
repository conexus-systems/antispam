"""
Celery App Configuration for Conexus Caller ID Background Workers.
"""
from celery import Celery
from app.config import settings

celery_app = Celery(
    "conexus_caller_id",
    broker=settings.REDIS_URL,
    backend=settings.REDIS_URL,
    include=[
        "app.workers.tasks",
    ]
)

celery_app.conf.update(
    # Serialization
    task_serializer="json",
    accept_content=["json"],
    result_serializer="json",
    
    # Timezone
    timezone="UTC",
    enable_utc=True,
    
    # Task routing
    task_routes={
        "app.workers.tasks.aggregate_reputation": {"queue": "reputation"},
        "app.workers.tasks.publish_dataset": {"queue": "dataset"},
        "app.workers.tasks.detect_campaigns": {"queue": "reputation"},
        "app.workers.tasks.cleanup_expired": {"queue": "maintenance"},
        "app.workers.tasks.moderate_report_queue": {"queue": "moderation"},
        "app.workers.tasks.send_contestation_notice": {"queue": "notifications"},
    },
    
    # Worker settings
    worker_prefetch_multiplier=4,
    worker_max_tasks_per_child=1000,
    worker_disable_rate_limits=False,
    
    # Result backend settings
    result_expires=3600,  # 1 hour
    result_compression="gzip",
    
    # Beat schedule (periodic tasks)
    beat_schedule={
        "aggregate-reputation-every-5-min": {
            "task": "app.workers.tasks.aggregate_reputation",
            "schedule": 300.0,  # 5 minutes
            "options": {"queue": "reputation"},
        },
        "publish-dataset-daily": {
            "task": "app.workers.tasks.publish_dataset",
            "schedule": 86400.0,  # 24 hours
            "options": {"queue": "dataset"},
        },
        "detect-campaigns-hourly": {
            "task": "app.workers.tasks.detect_campaigns",
            "schedule": 3600.0,  # 1 hour
            "options": {"queue": "reputation"},
        },
        "cleanup-expired-daily": {
            "task": "app.workers.tasks.cleanup_expired",
            "schedule": 86400.0,  # 24 hours
            "options": {"queue": "maintenance"},
        },
        "moderate-report-queue-every-minute": {
            "task": "app.workers.tasks.moderate_report_queue",
            "schedule": 60.0,  # 1 minute
            "options": {"queue": "moderation"},
        },
    },
    
    # Monitoring
    worker_send_task_events=True,
    task_send_sent_event=True,
)

# Auto-discover tasks
celery_app.autodiscover_tasks(["app.workers"])

if __name__ == "__main__":
    celery_app.start()