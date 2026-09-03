import mimetypes
import re
import shutil
import time
from collections.abc import Callable
from datetime import UTC, datetime, timedelta
from pathlib import Path
from tempfile import mkdtemp
from typing import Any

import yt_dlp

from .celery_app import celery_app
from .config import get_settings
from .storage import ObjectStorage
from .store import Store


class DownloadCancelled(Exception):
    pass


def _safe_filename(value: str) -> str:
    cleaned = re.sub(r"[^\w.-]+", "-", value, flags=re.UNICODE).strip("-.")
    return cleaned[:120] or "midia"


def _progress_hook(job_id: str, store: Store) -> Callable[[dict[str, Any]], None]:
    def update(data: dict[str, Any]) -> None:
        current = store.get_job(job_id)
        if not current or current.get("status") == "cancelled":
            raise DownloadCancelled()
        if data.get("status") != "downloading":
            return
        total = data.get("total_bytes") or data.get("total_bytes_estimate") or 0
        downloaded = data.get("downloaded_bytes") or 0
        progress = min(85, max(2, int(downloaded / total * 85))) if total else 5
        store.update_job(job_id, status="processing", progress=progress, stage="Baixando mídia")

    return update


def _find_output(directory: Path) -> Path:
    candidates = [
        path
        for path in directory.iterdir()
        if path.is_file() and not path.name.endswith(".part")
    ]
    if not candidates:
        raise RuntimeError("output_missing")
    return max(candidates, key=lambda path: path.stat().st_mtime)


def _download_options(job: dict[str, Any], directory: Path, hook: Any) -> dict[str, Any]:
    output_template = str(directory / "%(title).120B [%(id)s].%(ext)s")
    common: dict[str, Any] = {
        "outtmpl": output_template,
        "noplaylist": True,
        "quiet": True,
        "no_warnings": True,
        "progress_hooks": [hook],
        "socket_timeout": 20,
        "retries": 3,
        "overwrites": True,
    }
    if job["mode"] == "audio":
        return {
            **common,
            "format": "bestaudio/best",
            "postprocessors": [
                {"key": "FFmpegExtractAudio", "preferredcodec": "mp3", "preferredquality": "0"}
            ],
        }

    height = int(job["quality_id"].split("-")[-1])
    container = "webm" if job.get("output_container") == "webm" else "mp4"
    audio_container = "webm" if container == "webm" else "m4a"
    return {
        **common,
        "format": (
            f"bestvideo[height={height}][ext={container}]+bestaudio[ext={audio_container}]/"
            f"best[height={height}][ext={container}]/"
            f"bestvideo[height={height}]+bestaudio/best[height={height}]"
        ),
        "format_sort": [f"ext:{container}:{audio_container}", "res"],
        "merge_output_format": container,
    }


def _run_download(job: dict[str, Any], directory: Path, hook: Any) -> None:
    options = _download_options(job, directory, hook)
    try:
        with yt_dlp.YoutubeDL(options) as ydl:
            ydl.download([job["url"]])
    except yt_dlp.utils.DownloadError:
        if job["mode"] != "audio":
            raise
        for path in directory.iterdir():
            if path.is_file():
                path.unlink()
        fallback = {
            key: value
            for key, value in options.items()
            if key != "postprocessors"
        }
        fallback["format"] = "bestaudio[ext=m4a]/bestaudio"
        with yt_dlp.YoutubeDL(fallback) as ydl:
            ydl.download([job["url"]])


@celery_app.task(name="app.tasks.process_download", bind=True)  # type: ignore[untyped-decorator]
def process_download(self: Any, job_id: str) -> None:
    settings = get_settings()
    store = Store()
    storage = ObjectStorage()
    job = store.get_job(job_id)
    if not job or job.get("status") == "cancelled":
        return

    directory = Path(mkdtemp(prefix=f"baixai-{job_id}-", dir="/tmp"))
    object_key: str | None = None
    try:
        store.update_job(job_id, status="processing", progress=1, stage="Preparando download")
        _run_download(job, directory, _progress_hook(job_id, store))

        current = store.get_job(job_id)
        if not current or current.get("status") == "cancelled":
            raise DownloadCancelled()
        output = _find_output(directory)
        if output.stat().st_size > settings.max_output_bytes:
            raise ValueError("size_limit")

        store.update_job(job_id, progress=90, stage="Enviando arquivo")
        extension = output.suffix.lower().lstrip(".") or "bin"
        filename = f"{_safe_filename(output.stem)}.{extension}"
        object_key = f"jobs/{job_id}/{filename}"
        content_type = mimetypes.guess_type(filename)[0] or "application/octet-stream"
        storage.upload(output, object_key, content_type)

        expires_at = datetime.now(UTC) + timedelta(seconds=settings.media_retention_seconds)
        store.schedule_file_removal(object_key, expires_at.timestamp())
        store.update_job(
            job_id,
            status="completed",
            progress=100,
            stage="Pronto para baixar",
            filename=filename,
            object_key=object_key,
            download_url=storage.presigned_download(object_key),
            expires_at=expires_at.isoformat(),
        )
    except DownloadCancelled:
        if object_key:
            storage.delete(object_key)
        store.update_job(job_id, status="cancelled", progress=0, stage="Download cancelado")
    except ValueError as exc:
        code = "size_limit" if str(exc) == "size_limit" else "processing_failed"
        message = (
            "O arquivo ultrapassou o limite de 1 GB."
            if code == "size_limit"
            else "Não foi possível processar essa mídia."
        )
        store.update_job(
            job_id,
            status="failed",
            progress=0,
            stage="Falha no processamento",
            error={"code": code, "message": message},
        )
    except Exception:
        store.update_job(
            job_id,
            status="failed",
            progress=0,
            stage="Falha no processamento",
            error={
                "code": "processing_failed",
                "message": "Não foi possível processar essa mídia.",
            },
        )
    finally:
        store.release_job_slot(job["ip_address"], job_id)
        shutil.rmtree(directory, ignore_errors=True)


@celery_app.task(name="app.tasks.cleanup_expired_media")  # type: ignore[untyped-decorator]
def cleanup_expired_media() -> int:
    store = Store()
    storage = ObjectStorage()
    removed = 0
    for object_key in store.expired_files(time.time()):
        try:
            storage.delete(object_key)
        except Exception:
            continue
        store.mark_file_removed(object_key)
        removed += 1
    return removed
