"""
Rate limiting middleware using Redis.
"""
import time
from starlette.middleware.base import BaseHTTPMiddleware
from starlette.requests import Request
from starlette.responses import Response, JSONResponse
from app.config import settings
import redis.asyncio as redis


class RateLimitMiddleware(BaseHTTPMiddleware):
    def __init__(self, app, redis_client: redis.Redis = None):
        super().__init__(app)
        self.redis = redis_client or redis.from_url(settings.REDIS_URL, decode_responses=True)

    async def dispatch(self, request: Request, call_next):
        # Pular health check e docs
        if request.url.path in ["/health", "/docs", "/redoc", "/openapi.json"]:
            return await call_next(request)

        # Identificar cliente (IP + API Key se houver)
        client_ip = request.client.host if request.client else "unknown"
        api_key = request.headers.get(settings.API_KEY_HEADER, "")
        client_id = f"{client_ip}:{api_key[:8]}" if api_key else client_ip

        # Determinar limite baseado no endpoint
        path = request.url.path
        if path.startswith("/v1/lookup"):
            limit = settings.RATE_LIMIT_LOOKUP_PER_MINUTE
            window = 60
        elif path.startswith("/v1/reports"):
            limit = settings.RATE_LIMIT_REPORT_PER_MINUTE
            window = 60
        else:
            limit = settings.RATE_LIMIT_DEFAULT_PER_MINUTE
            window = 60

        key = f"ratelimit:{client_id}:{path}"
        current = await self.redis.incr(key)

        if current == 1:
            await self.redis.expire(key, window)

        if current > limit:
            ttl = await self.redis.ttl(key)
            return JSONResponse(
                status_code=429,
                content={
                    "error": "Rate limit exceeded",
                    "code": 429,
                    "retry_after": ttl,
                    "limit": limit,
                    "window_seconds": window
                },
                headers={
                    "X-RateLimit-Limit": str(limit),
                    "X-RateLimit-Remaining": "0",
                    "X-RateLimit-Reset": str(int(time.time()) + ttl)
                }
            )

        response = await call_next(request)
        response.headers["X-RateLimit-Limit"] = str(limit)
        response.headers["X-RateLimit-Remaining"] = str(max(0, limit - current))
        response.headers["X-RateLimit-Reset"] = str(int(time.time()) + window)
        return response