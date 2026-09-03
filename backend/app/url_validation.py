from urllib.parse import parse_qs, urlparse

from .errors import ApiError

ALLOWED_HOSTS = {"youtube.com", "www.youtube.com", "m.youtube.com", "youtu.be"}


def normalize_youtube_url(raw_url: str) -> tuple[str, str]:
    candidate = raw_url.strip()
    try:
        parsed = urlparse(candidate)
    except ValueError as exc:
        raise ApiError(422, "invalid_url", "Informe uma URL válida do YouTube.") from exc

    host = (parsed.hostname or "").lower().rstrip(".")
    if parsed.scheme != "https" or host not in ALLOWED_HOSTS or parsed.username or parsed.password:
        raise ApiError(422, "invalid_url", "Informe uma URL HTTPS válida do YouTube.")

    video_id: str | None = None
    if host == "youtu.be":
        video_id = parsed.path.strip("/").split("/")[0]
    elif parsed.path == "/watch":
        video_id = parse_qs(parsed.query).get("v", [None])[0]
    elif parsed.path.startswith("/shorts/"):
        parts = parsed.path.strip("/").split("/")
        video_id = parts[1] if len(parts) == 2 else None

    if not video_id or not 6 <= len(video_id) <= 20 or not all(
        char.isalnum() or char in "-_" for char in video_id
    ):
        raise ApiError(422, "unsupported_url", "Use o link de um único vídeo ou Short.")

    return f"https://www.youtube.com/watch?v={video_id}", video_id
