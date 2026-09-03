import httpx

from .config import get_settings
from .errors import ApiError


async def verify_turnstile(token: str, remote_ip: str) -> None:
    settings = get_settings()
    if not settings.is_production:
        return
    async with httpx.AsyncClient(timeout=8) as client:
        response = await client.post(
            "https://challenges.cloudflare.com/turnstile/v0/siteverify",
            data={
                "secret": settings.turnstile_secret_key,
                "response": token,
                "remoteip": remote_ip,
            },
        )
    try:
        success = bool(response.json().get("success"))
    except (ValueError, AttributeError):
        success = False
    if not success:
        raise ApiError(422, "captcha_failed", "Não foi possível validar o desafio de segurança.")
