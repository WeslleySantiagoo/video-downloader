from typing import Any
from unittest.mock import Mock

import fakeredis
from httpx import ASGITransport, AsyncClient

from app.main import app, client_ip, get_store
from app.schemas import MediaInspection, MediaMode, QualityOption
from app.store import Store


def inspection() -> MediaInspection:
    return MediaInspection(
        media_id="dQw4w9WgXcQ",
        title="Vídeo autorizado",
        thumbnail_url=None,
        duration_seconds=120,
        video_options=[
            QualityOption(
                id="video-720",
                label="720p",
                mode=MediaMode.VIDEO,
                height=720,
                container="mp4",
                estimated_bytes=12_000_000,
            )
        ],
        audio_options=[
            QualityOption(
                id="audio-best",
                label="Melhor qualidade",
                mode=MediaMode.AUDIO,
                container="mp3",
                estimated_bytes=2_000_000,
            )
        ],
    )


async def test_inspection_and_job_flow(monkeypatch) -> None:
    store = Store(fakeredis.FakeRedis(decode_responses=True))
    async def store_override() -> Store:
        return store

    async def ip_override() -> str:
        return "127.0.0.1"

    async def immediate(func: Any, *args: Any) -> Any:
        return func(*args)

    app.dependency_overrides[get_store] = store_override
    app.dependency_overrides[client_ip] = ip_override
    monkeypatch.setattr("app.main.to_thread", immediate)
    monkeypatch.setattr("app.main.inspect_media", lambda _: (inspection(), "https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
    send_task = Mock()
    monkeypatch.setattr("app.main.celery_app.send_task", send_task)
    monkeypatch.setattr("app.main.celery_app.control.revoke", Mock())

    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
        inspected = await client.post("/api/v1/media/inspect", json={"url": "https://youtu.be/dQw4w9WgXcQ"})
        assert inspected.status_code == 200
        assert inspected.json()["video_options"][0]["label"] == "720p"

        created = await client.post(
            "/api/v1/downloads",
            json={
                "media_id": "dQw4w9WgXcQ",
                "mode": "video",
                "quality_id": "video-720",
                "rights_confirmed": True,
                "turnstile_token": "development-token",
            },
        )
        assert created.status_code == 202
        job_id = created.json()["job_id"]
        send_task.assert_called_once()

        response = await client.get(f"/api/v1/downloads/{job_id}")
        assert response.status_code == 200
        assert response.json()["status"] == "queued"

        cancelled = await client.delete(f"/api/v1/downloads/{job_id}")
        assert cancelled.status_code == 204
        assert store.get_job(job_id)["status"] == "cancelled"
    app.dependency_overrides.clear()


async def test_requires_rights_confirmation(monkeypatch) -> None:
    store = Store(fakeredis.FakeRedis(decode_responses=True))
    store.save_media("dQw4w9WgXcQ", {"inspection": inspection().model_dump(mode="json"), "url": "https://www.youtube.com/watch?v=dQw4w9WgXcQ"})
    async def store_override() -> Store:
        return store

    async def ip_override() -> str:
        return "127.0.0.1"

    app.dependency_overrides[get_store] = store_override
    app.dependency_overrides[client_ip] = ip_override
    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
        response = await client.post(
            "/api/v1/downloads",
            json={
                "media_id": "dQw4w9WgXcQ",
                "mode": "video",
                "quality_id": "video-720",
                "rights_confirmed": False,
                "turnstile_token": "development-token",
            },
        )
    assert response.status_code == 422
    assert response.json()["code"] == "rights_required"
    app.dependency_overrides.clear()
