from datetime import datetime
from enum import StrEnum

from pydantic import BaseModel, Field, HttpUrl


class MediaMode(StrEnum):
    VIDEO = "video"
    AUDIO = "audio"


class JobStatus(StrEnum):
    QUEUED = "queued"
    PROCESSING = "processing"
    COMPLETED = "completed"
    FAILED = "failed"
    CANCELLED = "cancelled"


class InspectRequest(BaseModel):
    url: str = Field(min_length=10, max_length=500)


class QualityOption(BaseModel):
    id: str
    label: str
    mode: MediaMode
    height: int | None = None
    container: str
    estimated_bytes: int | None = None


class MediaInspection(BaseModel):
    media_id: str
    title: str
    thumbnail_url: HttpUrl | None = None
    duration_seconds: int
    video_options: list[QualityOption]
    audio_options: list[QualityOption]


class CreateDownloadRequest(BaseModel):
    media_id: str = Field(pattern=r"^[A-Za-z0-9_-]{6,20}$")
    mode: MediaMode
    quality_id: str = Field(min_length=3, max_length=100)
    rights_confirmed: bool
    turnstile_token: str = Field(min_length=1, max_length=4096)


class CreateDownloadResponse(BaseModel):
    job_id: str
    status: JobStatus


class JobError(BaseModel):
    code: str
    message: str


class DownloadJob(BaseModel):
    job_id: str
    status: JobStatus
    progress: int = Field(ge=0, le=100)
    stage: str
    filename: str | None = None
    download_url: str | None = None
    expires_at: datetime | None = None
    error: JobError | None = None


class HealthResponse(BaseModel):
    status: str
    redis: str
    storage: str
