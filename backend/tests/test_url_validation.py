import pytest

from app.errors import ApiError
from app.url_validation import normalize_youtube_url


@pytest.mark.parametrize(
    ("raw", "video_id"),
    [
        ("https://www.youtube.com/watch?v=dQw4w9WgXcQ", "dQw4w9WgXcQ"),
        ("https://youtu.be/dQw4w9WgXcQ?t=10", "dQw4w9WgXcQ"),
        ("https://youtube.com/shorts/dQw4w9WgXcQ", "dQw4w9WgXcQ"),
    ],
)
def test_accepts_single_video_urls(raw: str, video_id: str) -> None:
    normalized, extracted_id = normalize_youtube_url(raw)
    assert extracted_id == video_id
    assert normalized == f"https://www.youtube.com/watch?v={video_id}"


@pytest.mark.parametrize(
    "raw",
    [
        "http://youtube.com/watch?v=dQw4w9WgXcQ",
        "https://evil.example/watch?v=dQw4w9WgXcQ",
        "https://youtube.com/playlist?list=abc",
        "https://youtube.com.evil.example/watch?v=dQw4w9WgXcQ",
        "https://user:password@youtube.com/watch?v=dQw4w9WgXcQ",
    ],
)
def test_rejects_unsafe_or_unsupported_urls(raw: str) -> None:
    with pytest.raises(ApiError):
        normalize_youtube_url(raw)
