import time

import fakeredis

from app.store import Store


def make_store() -> Store:
    return Store(fakeredis.FakeRedis(decode_responses=True))


def test_rate_limit_enforces_window() -> None:
    store = make_store()
    assert store.rate_limit("inspect:127.0.0.1", 2, 60)
    assert store.rate_limit("inspect:127.0.0.1", 2, 60)
    assert not store.rate_limit("inspect:127.0.0.1", 2, 60)


def test_limits_active_jobs_and_releases_terminal_jobs() -> None:
    store = make_store()
    for job_id in ("one", "two"):
        store.save_job(job_id, {"job_id": job_id, "status": "processing"})
        assert store.acquire_job_slot("127.0.0.1", job_id)
    assert not store.acquire_job_slot("127.0.0.1", "three")
    store.update_job("one", status="completed")
    assert store.acquire_job_slot("127.0.0.1", "three")


def test_tracks_expired_files() -> None:
    store = make_store()
    store.schedule_file_removal("expired", time.time() - 1)
    store.schedule_file_removal("future", time.time() + 100)
    assert store.expired_files(time.time()) == ["expired"]
    store.mark_file_removed("expired")
    assert store.expired_files(time.time()) == []
