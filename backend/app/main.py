from asyncio import to_thread
from typing import Annotated
from uuid import uuid4

from fastapi import Depends, FastAPI, Request, Response, status
from fastapi.middleware.cors import CORSMiddleware

from .celery_app import celery_app
from .config import get_settings
from .errors import ApiError, install_error_handlers
from .media import inspect_media
from .schemas import (
    CreateDownloadRequest,
    CreateDownloadResponse,
    DownloadJob,
    HealthResponse,
    InspectRequest,
    JobStatus,
    MediaInspection,
)
from .security import verify_turnstile
from .storage import ObjectStorage
from .store import Store

app = FastAPI(title="Baixaí API", version="0.1.0", docs_url="/docs", redoc_url=None)
settings = get_settings()
app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.allowed_origins,
    allow_credentials=False,
    allow_methods=["GET", "POST", "DELETE"],
    allow_headers=["Content-Type"],
)
install_error_handlers(app)


def get_store() -> Store:
    return Store()


def client_ip(request: Request) -> str:
    return request.client.host if request.client else "unknown"


StoreDependency = Annotated[Store, Depends(get_store)]
IpDependency = Annotated[str, Depends(client_ip)]


@app.post("/api/v1/media/inspect", response_model=MediaInspection)
async def inspect_endpoint(
    body: InspectRequest,
    store: StoreDependency,
    ip_address: IpDependency,
) -> MediaInspection:
    if not store.rate_limit(f"inspect:{ip_address}", limit=20, window_seconds=60):
        raise ApiError(429, "rate_limit", "Muitas consultas. Tente novamente em instantes.")
    inspection, normalized_url = await to_thread(inspect_media, body.url)
    store.save_media(
        inspection.media_id,
        {"inspection": inspection.model_dump(mode="json"), "url": normalized_url},
    )
    return inspection


@app.post(
    "/api/v1/downloads",
    response_model=CreateDownloadResponse,
    status_code=status.HTTP_202_ACCEPTED,
)
async def create_download(
    body: CreateDownloadRequest,
    store: StoreDependency,
    ip_address: IpDependency,
) -> CreateDownloadResponse:
    if not body.rights_confirmed:
        raise ApiError(422, "rights_required", "Confirme que você tem autorização para baixar.")
    if not store.rate_limit(f"create:{ip_address}", limit=5, window_seconds=3600):
        raise ApiError(429, "rate_limit", "Limite de cinco downloads por hora atingido.")
    await verify_turnstile(body.turnstile_token, ip_address)

    cached = store.get_media(body.media_id)
    if not cached:
        raise ApiError(410, "inspection_expired", "A consulta expirou. Analise o link novamente.")
    inspection = cached["inspection"]
    options = (
        inspection["video_options"] if body.mode.value == "video" else inspection["audio_options"]
    )
    selected_option = next(
        (option for option in options if option["id"] == body.quality_id),
        None,
    )
    if not selected_option:
        raise ApiError(422, "invalid_quality", "Escolha uma qualidade disponível.")

    job_id = str(uuid4())
    if not store.acquire_job_slot(ip_address, job_id):
        raise ApiError(429, "active_job_limit", "Aguarde um dos seus downloads ativos terminar.")
    job = {
        "job_id": job_id,
        "status": JobStatus.QUEUED.value,
        "progress": 0,
        "stage": "Na fila",
        "media_id": body.media_id,
        "url": cached["url"],
        "mode": body.mode.value,
        "quality_id": body.quality_id,
        "output_container": selected_option["container"],
        "ip_address": ip_address,
        "filename": None,
        "download_url": None,
        "expires_at": None,
        "error": None,
    }
    store.save_job(job_id, job)
    try:
        celery_app.send_task("app.tasks.process_download", args=[job_id], task_id=job_id)
    except Exception as exc:
        store.release_job_slot(ip_address, job_id)
        store.update_job(
            job_id,
            status="failed",
            stage="Fila indisponível",
            error={"code": "queue_unavailable", "message": "Tente novamente em instantes."},
        )
        raise ApiError(503, "queue_unavailable", "A fila está indisponível.") from exc
    return CreateDownloadResponse(job_id=job_id, status=JobStatus.QUEUED)


@app.get("/api/v1/downloads/{job_id}", response_model=DownloadJob)
async def get_download(job_id: str, store: StoreDependency) -> DownloadJob:
    job = store.get_job(job_id)
    if not job:
        raise ApiError(404, "job_not_found", "Download não encontrado ou expirado.")
    return DownloadJob.model_validate(job)


@app.delete("/api/v1/downloads/{job_id}", status_code=status.HTTP_204_NO_CONTENT)
async def cancel_download(job_id: str, store: StoreDependency) -> Response:
    job = store.get_job(job_id)
    if not job:
        raise ApiError(404, "job_not_found", "Download não encontrado ou expirado.")
    if job["status"] == "completed" and job.get("object_key"):
        ObjectStorage().delete(job["object_key"])
        store.mark_file_removed(job["object_key"])
    if job["status"] not in {"failed", "cancelled"}:
        store.update_job(job_id, status="cancelled", progress=0, stage="Download cancelado")
        celery_app.control.revoke(job_id, terminate=False)
        store.release_job_slot(job["ip_address"], job_id)
    return Response(status_code=status.HTTP_204_NO_CONTENT)


@app.get("/health", response_model=HealthResponse)
async def health(store: StoreDependency) -> HealthResponse:
    redis_status = "ok"
    storage_status = "ok"
    try:
        store.client.ping()
    except Exception:
        redis_status = "error"
    try:
        ObjectStorage().check()
    except Exception:
        storage_status = "error"
    overall = "ok" if redis_status == storage_status == "ok" else "degraded"
    return HealthResponse(status=overall, redis=redis_status, storage=storage_status)
