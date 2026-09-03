# Baixaí

Monorepo de um baixador de mídia para conteúdo próprio ou expressamente autorizado. O frontend React/Vite é publicado na Vercel; a API FastAPI, o worker Celery e o FFmpeg rodam em containers separados.

> **Importante:** ter direitos sobre o conteúdo não significa, por si só, ter autorização da plataforma para extraí-lo. O uso em produção depende da aprovação aplicável do YouTube e do cumprimento de suas políticas.

## Desenvolvimento local

Requisitos: Docker + Docker Compose, ou Node.js 22 e Python 3.12 para executar os serviços fora de containers.

```bash
cp .env.example .env
docker compose up --build
```

- Frontend: http://localhost:5173
- API e documentação: http://localhost:8000/docs
- MinIO: http://localhost:9001 (`minioadmin` / `minioadmin`)

Em desenvolvimento o Turnstile fica desativado. Para produção, defina `APP_ENV=production`, `TURNSTILE_SECRET_KEY`, `VITE_TURNSTILE_SITE_KEY`, origens CORS explícitas e credenciais reais de um storage S3 compatível.

## Comandos sem Docker

```bash
npm --prefix frontend install
npm --prefix frontend run dev

python -m venv .venv
source .venv/bin/activate
pip install -e './backend[dev]'
uvicorn app.main:app --app-dir backend --reload
```

O worker e o scheduler são iniciados, respectivamente, com:

```bash
cd backend
celery -A app.celery_app worker --loglevel=info
celery -A app.celery_app beat --loglevel=info
```

## Estrutura

- `frontend/`: React, TypeScript, Tailwind, Vitest e Playwright.
- `backend/`: FastAPI, Celery, Redis, `yt-dlp`, FFmpeg e S3.
- `docker-compose.yml`: ambiente completo com Redis e MinIO.
- `.github/workflows/ci.yml`: testes, análise estática e builds.

O frontend consulta a API por `VITE_API_URL`. O backend mantém apenas estado temporário no Redis; resultados expiram após uma hora e são removidos do storage pelo scheduler.

Para publicar, crie três serviços a partir de `backend/Dockerfile`, sobrescrevendo o comando para API, worker e scheduler como no Compose. O proxy do provedor deve encaminhar corretamente o IP do cliente; configure as opções de proxy confiável do Uvicorn conforme a rede do host para que as cotas por IP não sejam compartilhadas entre todos os visitantes.
