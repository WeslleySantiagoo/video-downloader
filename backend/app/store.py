import json
import time
from typing import Any

from redis import Redis

from .config import get_settings


class Store:
    def __init__(self, client: Redis | None = None) -> None:
        settings = get_settings()
        self.client: Any = client or Redis.from_url(settings.redis_url, decode_responses=True)
        self.job_ttl = settings.job_ttl_seconds

    def save_media(self, media_id: str, payload: dict[str, Any]) -> None:
        self.client.setex(f"media:{media_id}", 900, json.dumps(payload))

    def get_media(self, media_id: str) -> dict[str, Any] | None:
        raw = self.client.get(f"media:{media_id}")
        return json.loads(raw) if raw else None

    def save_job(self, job_id: str, payload: dict[str, Any]) -> None:
        self.client.setex(f"job:{job_id}", self.job_ttl, json.dumps(payload))

    def get_job(self, job_id: str) -> dict[str, Any] | None:
        raw = self.client.get(f"job:{job_id}")
        return json.loads(raw) if raw else None

    def update_job(self, job_id: str, **changes: Any) -> dict[str, Any] | None:
        job = self.get_job(job_id)
        if not job:
            return None
        job.update(changes)
        self.save_job(job_id, job)
        return job

    def rate_limit(self, key: str, limit: int, window_seconds: int) -> bool:
        bucket = f"rate:{key}:{int(time.time()) // window_seconds}"
        with self.client.pipeline() as pipe:
            pipe.incr(bucket)
            pipe.expire(bucket, window_seconds + 5)
            count, _ = pipe.execute()
        return int(count) <= limit

    def acquire_job_slot(self, ip_address: str, job_id: str, maximum: int = 2) -> bool:
        key = f"active:{ip_address}"
        terminal_jobs = self._terminal_job_ids(self.client.smembers(key))
        if terminal_jobs:
            self.client.srem(key, *terminal_jobs)
        if self.client.scard(key) >= maximum:
            return False
        self.client.sadd(key, job_id)
        self.client.expire(key, self.job_ttl)
        return True

    def release_job_slot(self, ip_address: str, job_id: str) -> None:
        self.client.srem(f"active:{ip_address}", job_id)

    def _terminal_job_ids(self, job_ids: set[str]) -> list[str]:
        terminal: list[str] = []
        for job_id in job_ids:
            job = self.get_job(job_id)
            if not job or job.get("status") in {"completed", "failed", "cancelled"}:
                terminal.append(job_id)
        return terminal

    def schedule_file_removal(self, object_key: str, expires_at: float) -> None:
        self.client.zadd("files:expires", {object_key: expires_at})

    def expired_files(self, now: float) -> list[str]:
        return list(self.client.zrangebyscore("files:expires", 0, now))

    def mark_file_removed(self, object_key: str) -> None:
        self.client.zrem("files:expires", object_key)
