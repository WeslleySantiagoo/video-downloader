from typing import Any

import yt_dlp

from .config import get_settings
from .errors import ApiError
from .schemas import MediaInspection, MediaMode, QualityOption
from .url_validation import normalize_youtube_url


def _estimated_size(item: dict[str, Any], duration: int) -> int | None:
    direct = item.get("filesize") or item.get("filesize_approx")
    if direct:
        return int(direct)
    bitrate = item.get("tbr")
    return int(float(bitrate) * 1000 / 8 * duration) if bitrate and duration else None


def inspect_media(raw_url: str) -> tuple[MediaInspection, str]:
    settings = get_settings()
    normalized_url, expected_id = normalize_youtube_url(raw_url)
    options = {
        "quiet": True,
        "no_warnings": True,
        "noplaylist": True,
        "skip_download": True,
        "socket_timeout": 15,
    }
    try:
        with yt_dlp.YoutubeDL(options) as ydl:
            info = ydl.extract_info(normalized_url, download=False)
    except yt_dlp.utils.DownloadError as exc:
        raise ApiError(422, "media_unavailable", "Não foi possível acessar esse vídeo.") from exc

    if not info or info.get("_type") == "playlist" or info.get("entries"):
        raise ApiError(422, "playlist_not_supported", "Playlists não são aceitas no MVP.")
    if str(info.get("id")) != expected_id:
        raise ApiError(422, "unexpected_media", "O link redirecionou para outra mídia.")
    if info.get("is_live") or info.get("live_status") in {"is_live", "is_upcoming"}:
        raise ApiError(422, "live_not_supported", "Transmissões ao vivo não são aceitas.")

    duration = int(info.get("duration") or 0)
    if duration <= 0:
        raise ApiError(422, "unknown_duration", "Não foi possível determinar a duração do vídeo.")
    if duration > settings.max_duration_seconds:
        raise ApiError(422, "duration_limit", "O vídeo ultrapassa o limite de 30 minutos.")

    by_height: dict[int, QualityOption] = {}
    audio_estimates: list[int] = []
    for item in info.get("formats") or []:
        estimate = _estimated_size(item, duration)
        if item.get("acodec") != "none" and estimate:
            audio_estimates.append(estimate)
        height = item.get("height")
        if item.get("vcodec") == "none" or not isinstance(height, int):
            continue
        combined_estimate = estimate + (max(audio_estimates, default=0)) if estimate else None
        if combined_estimate and combined_estimate > settings.max_output_bytes:
            continue
        container = "mp4" if item.get("ext") == "mp4" else "webm"
        candidate = QualityOption(
            id=f"video-{height}",
            label=f"{height}p",
            mode=MediaMode.VIDEO,
            height=height,
            container=container,
            estimated_bytes=combined_estimate,
        )
        existing = by_height.get(height)
        if not existing or (candidate.container == "mp4" and existing.container != "mp4"):
            by_height[height] = candidate

    if not by_height:
        raise ApiError(422, "no_formats", "Nenhuma qualidade compatível foi encontrada.")

    best_audio = max(audio_estimates, default=0) or None
    audio_options = [
        QualityOption(
            id="audio-best",
            label="Melhor qualidade",
            mode=MediaMode.AUDIO,
            container="mp3",
            estimated_bytes=best_audio,
        )
    ]
    inspection = MediaInspection(
        media_id=str(info.get("id") or expected_id),
        title=str(info.get("title") or "Vídeo sem título")[:200],
        thumbnail_url=info.get("thumbnail"),
        duration_seconds=duration,
        video_options=list(sorted(by_height.values(), key=lambda option: option.height or 0)),
        audio_options=audio_options,
    )
    return inspection, normalized_url
