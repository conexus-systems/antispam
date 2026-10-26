"""
Configuration settings for Conexus Caller ID Backend.
"""
from functools import lru_cache
from pydantic_settings import BaseSettings, SettingsConfigDict
from typing import List


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        case_sensitive=False,
        extra="ignore"
    )

    # App
    APP_NAME: str = "Conexus Caller ID API"
    APP_VERSION: str = "1.0.0"
    ENVIRONMENT: str = "development"  # development, staging, production

    # Server
    HOST: str = "0.0.0.0"
    PORT: int = 8000
    WORKERS: int = 4

    # Database
    DATABASE_URL: str = "postgresql+asyncpg://postgres:postgres@localhost:5432/conexus_caller_id"
    DATABASE_POOL_SIZE: int = 10
    DATABASE_MAX_OVERFLOW: int = 20

    # Redis
    REDIS_URL: str = "redis://localhost:6379/0"
    REDIS_MAX_CONNECTIONS: int = 50

    # Security
    SECRET_KEY: str = "dev-secret-change-in-production"
    API_KEY_HEADER: str = "X-API-Key"
    HMAC_SECRET: str = "dev-hmac-secret-change-in-production"
    ED25519_PRIVATE_KEY_PATH: str = "keys/ed25519_private.pem"
    ED25519_PUBLIC_KEY_PATH: str = "keys/ed25519_public.pem"

    # Rate Limiting
    RATE_LIMIT_LOOKUP_PER_MINUTE: int = 30
    RATE_LIMIT_REPORT_PER_MINUTE: int = 10
    RATE_LIMIT_DEFAULT_PER_MINUTE: int = 60

    # Proof of Work
    POW_DIFFICULTY: int = 4  # leading zeros in SHA256
    POW_MAX_DIFFICULTY: int = 8

    # k-anonymity
    K_ANONYMITY_MIN_PREFIX_LEN: int = 6
    K_ANONYMITY_MAX_PREFIX_LEN: int = 8
    K_ANONYMITY_MIN_BUCKET_SIZE: int = 100

    # Reputation Engine
    REPUTATION_BLOCK_THRESHOLD: int = 70
    REPUTATION_PUBLICATION_MIN_WEIGHT: float = 3.0
    REPUTATION_PUBLICATION_MIN_HOURS: int = 48
    REPUTATION_PUBLICATION_MIN_NETWORKS: int = 3
    REPUTATION_PUBLICATION_MATURE_WEIGHT_RATIO: float = 0.5
    REPUTATION_CONTEST_WINDOW_DAYS: int = 90

    # CORS
    CORS_ORIGINS: List[str] = ["*"]

    # External APIs (Conta Conexus, etc.)
    CONEXUS_ACCOUNT_API_URL: str = "https://api.conexus-systems.com.br"
    CONEXUS_ACCOUNT_API_KEY: str = ""

    # Object Storage (para datasets)
    STORAGE_ENDPOINT: str = ""
    STORAGE_BUCKET: str = "conexus-caller-id-datasets"
    STORAGE_ACCESS_KEY: str = ""
    STORAGE_SECRET_KEY: str = ""

    # Observability
    OTEL_EXPORTER_OTLP_ENDPOINT: str = ""
    SENTRY_DSN: str = ""


@lru_cache()
def get_settings() -> Settings:
    return Settings()


settings = get_settings()