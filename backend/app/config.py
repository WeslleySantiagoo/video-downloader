from functools import lru_cache

from pydantic import Field, model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    app_env: str = "development"
    api_host: str = "0.0.0.0"
    api_port: int = 8000
    cors_origins: str = "http://localhost:5173"
    redis_url: str = "redis://localhost:6379/0"
    celery_broker_url: str = "redis://localhost:6379/1"
    celery_result_backend: str = "redis://localhost:6379/2"
    s3_endpoint_url: str | None = "http://localhost:9000"
    s3_public_endpoint_url: str | None = None
    s3_region: str = "us-east-1"
    s3_bucket: str = "media-downloads"
    s3_access_key_id: str = "minioadmin"
    s3_secret_access_key: str = "minioadmin"
    turnstile_secret_key: str | None = None
    media_retention_seconds: int = Field(default=3600, ge=300)
    job_ttl_seconds: int = Field(default=7200, ge=600)
    max_duration_seconds: int = Field(default=1800, ge=60)
    max_output_bytes: int = Field(default=1_073_741_824, ge=10_000_000)

    @property
    def is_production(self) -> bool:
        return self.app_env.lower() == "production"

    @property
    def allowed_origins(self) -> list[str]:
        return [origin.strip() for origin in self.cors_origins.split(",") if origin.strip()]

    @model_validator(mode="after")
    def validate_production(self) -> "Settings":
        if self.is_production and not self.turnstile_secret_key:
            raise ValueError("TURNSTILE_SECRET_KEY é obrigatório em produção")
        if self.is_production and "*" in self.allowed_origins:
            raise ValueError("CORS_ORIGINS não pode usar '*' em produção")
        return self


@lru_cache
def get_settings() -> Settings:
    return Settings()
