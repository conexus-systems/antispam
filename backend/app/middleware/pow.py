"""
Proof-of-Work middleware for anti-spam on report endpoints.
Validates Hashcash-style PoW: SHA256(nonce + data) has leading zeros.
"""
import hashlib
import json
from starlette.middleware.base import BaseHTTPMiddleware
from starlette.requests import Request
from starlette.responses import JSONResponse
from app.config import settings


class PoWMiddleware(BaseHTTPMiddleware):
    """
    Middleware que valida Proof-of-Work em endpoints de denúncia.
    O cliente deve enviar header X-PoW-Nonce com o nonce que satisfaz a dificuldade.
    """

    def __init__(self, app):
        super().__init__(app)

    async def dispatch(self, request: Request, call_next):
        # Só validar PoW em POST /v1/reports
        if request.method == "POST" and request.url.path == "/v1/reports":
            # Ler body para validar
            body = await request.body()
            try:
                data = json.loads(body) if body else {}
            except json.JSONDecodeError:
                return JSONResponse(
                    status_code=400,
                    content={"error": "Invalid JSON body", "code": 400}
                )

            # Verificar PoW
            pow_nonce = request.headers.get("X-PoW-Nonce", "")
            pow_difficulty = data.get("pow_difficulty", settings.POW_DIFFICULTY)

            if not pow_nonce:
                return JSONResponse(
                    status_code=400,
                    content={
                        "error": "Proof-of-Work required",
                        "code": 400,
                        "required_difficulty": pow_difficulty,
                        "header": "X-PoW-Nonce"
                    }
                )

            # Validar: SHA256(nonce + canonical_data) deve ter 'difficulty' leading zeros
            canonical_data = self._canonicalize_for_pow(data)
            hash_input = f"{pow_nonce}{canonical_data}".encode()
            hash_result = hashlib.sha256(hash_input).hexdigest()

            if not hash_result.startswith("0" * pow_difficulty):
                return JSONResponse(
                    status_code=400,
                    content={
                        "error": "Invalid Proof-of-Work",
                        "code": 400,
                        "required_difficulty": pow_difficulty,
                        "provided_hash": hash_result[:16] + "..."
                    }
                )

            # Validar dificuldade não excede máximo
            if pow_difficulty > settings.POW_MAX_DIFFICULTY:
                return JSONResponse(
                    status_code=400,
                    content={
                        "error": "Difficulty too high",
                        "code": 400,
                        "max_difficulty": settings.POW_MAX_DIFFICULTY
                    }
                )

            # Recriar request com body para downstream
            request._body = body

        return await call_next(request)

    def _canonicalize_for_pow(self, data: dict) -> str:
        """Cria representação canônica dos dados para PoW."""
        # Campos que entram no PoW (determinísticos)
        pow_fields = [
            "phone_hash",
            "category",
            "occurred_at",
            "client_id"
        ]
        parts = []
        for field in pow_fields:
            if field in data:
                parts.append(f"{field}:{data[field]}")
        return "|".join(parts)