from celery import Celery

from .config import get_settings

settings = get_settings()
celery_app = Celery(
    "baixai",
    broker=settings.celery_broker_url,
    backend=settings.celery_result_backend,
    include=["app.tasks"],
)
celery_app.conf.update(
    task_track_started=True,
    task_acks_late=True,
    worker_prefetch_multiplier=1,
    task_reject_on_worker_lost=True,
    timezone="UTC",
    beat_schedule={
        "remove-expired-media": {
            "task": "app.tasks.cleanup_expired_media",
            "schedule": 300.0,
        }
    },
)
