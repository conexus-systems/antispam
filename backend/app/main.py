"""
Conexus Caller ID - Backend API
FastAPI application for reputation lookup, reporting, and dataset publishing.
"""
import structlog
from contextlib import asynccontextmanager
from fastapi import FastAPI, Request, Depends, HTTPException, status
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field
from typing import Optional, List
import time
import hashlib
import hmac
import os

from app.config import settings
from app.database import init_db, close_db
from app.middleware.rate_limit import RateLimitMiddleware
from app.middleware.pow import PoWMiddleware
from app.routes import lookup, reports, reputation, blocks, datasets, health

# Configurar logging estruturado
structlog.configure(
    processors=[
        structlog.processors.add_log_level,
        structlog.processors.TimeStamper(fmt="iso"),
        structlog.processors.JSONRenderer()
    ]
)
logger = structlog.get_logger()


@asynccontextmanager
async def lifespan(app: FastAPI):
    # Startup
    logger.info("starting_application", version=settings.APP_VERSION)
    await init_db()
    yield
    # Shutdown
    logger.info("shutting_down_application")
    await close_db()


app = FastAPI(
    title="Conexus Caller ID API",
    description="API de reputação e bloqueio de chamadas spam - Privacy First",
    version=settings.APP_VERSION,
    lifespan=lifespan,
    docs_url="/docs" if settings.ENVIRONMENT != "production" else None,
    redoc_url="/redoc" if settings.ENVIRONMENT != "production" else None,
)

# CORS
app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.CORS_ORIGINS,
    allow_credentials=True,
    allow_methods=["GET", "POST", "DELETE", "OPTIONS"],
    allow_headers=["*"],
)

# Middlewares customizados
app.add_middleware(RateLimitMiddleware)
app.add_middleware(PoWMiddleware)


# Exception handlers
@app.exception_handler(HTTPException)
async def http_exception_handler(request: Request, exc: HTTPException):
    logger.warning("http_exception", path=request.url.path, status=exc.status_code, detail=exc.detail)
    return JSONResponse(
        status_code=exc.status_code,
        content={"error": exc.detail, "code": exc.status_code}
    )


@app.exception_handler(Exception)
async def generic_exception_handler(request: Request, exc: Exception):
    logger.exception("unhandled_exception", path=request.url.path, error=str(exc))
    return JSONResponse(
        status_code=500,
        content={"error": "Erro interno do servidor", "code": 500}
    )


# Request ID middleware
@app.middleware("http")
async def add_request_id(request: Request, call_next):
    request_id = request.headers.get("X-Request-ID", hashlib.md5(str(time.time()).encode()).hexdigest()[:8])
    structlog.contextvars.clear_contextvars()
    structlog.contextvars.bind_contextvars(request_id=request_id)
    response = await call_next(request)
    response.headers["X-Request-ID"] = request_id
    return response


# Rotas
app.include_router(health.router, tags=["health"])
app.include_router(lookup.router, prefix="/v1", tags=["lookup"])
app.include_router(reports.router, prefix="/v1", tags=["reports"])
app.include_router(reputation.router, prefix="/v1", tags=["reputation"])
app.include_router(blocks.router, prefix="/v1", tags=["blocks"])
app.include_router(datasets.router, prefix="/v1", tags=["datasets"])


@app.get("/")
async def root():
    return {
        "name": "Conexus Caller ID API",
        "version": settings.APP_VERSION,
        "status": "operational",
        "docs": "/docs" if settings.ENVIRONMENT != "production" else "disabled"
    }


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(
        "app.main:app",
        host="0.0.0.0",
        port=8000,
        reload=settings.ENVIRONMENT == "development",
        log_config=None  # Usar structlog
    )