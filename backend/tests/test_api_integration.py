"""
Integration tests for Conexus Caller ID Backend API.
Tests: lookup k-anônimo, reports com PoW, reputation, blocks sync, datasets.
"""
import pytest
import json
import hashlib
from datetime import datetime, timezone
from httpx import AsyncClient, ASGITransport
from sqlalchemy.ext.asyncio import create_async_engine, AsyncSession
from sqlalchemy.orm import sessionmaker
from sqlalchemy import select

from app.main import app
from app.database import Base, get_db
from app.models import PhoneHash, ReputationScore, SpamReport, ReportStatus, ReportCategory
from app.config import settings

# Test database URL (use test database)
TEST_DATABASE_URL = "postgresql+asyncpg://postgres:postgres@localhost:5432/conexus_caller_id_test"

@pytest.fixture(scope="session")
def event_loop():
    import asyncio
    loop = asyncio.new_event_loop()
    yield loop
    loop.close()

@pytest.fixture(scope="session")
async def test_engine():
    engine = create_async_engine(TEST_DATABASE_URL, pool_pre_ping=True)
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)
    yield engine
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.drop_all)
    await engine.dispose()

@pytest.fixture
async def db_session(test_engine):
    async_session = sessionmaker(test_engine, class_=AsyncSession, expire_on_commit=False)
    async with async_session() as session:
        yield session

@pytest.fixture
async def client(db_session):
    """HTTP client with test database override."""
    async def override_get_db():
        yield db_session
    
    app.dependency_overrides[get_db] = override_get_db
    
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as ac:
        yield ac
    
    app.dependency_overrides.clear()

@pytest.fixture
async def sample_phone_hash(db_session):
    """Cria phone_hash de teste."""
    phone = PhoneHash(phone_hmac="a1b2c3d4e5f67890", country="BR")
    db_session.add(phone)
    await db_session.commit()
    return phone

@pytest.fixture
async def sample_reputation_score(db_session, sample_phone_hash):
    """Cria reputation_score de teste."""
    score = ReputationScore(
        phone_hmac=sample_phone_hash.phone_hmac,
        score=85,
        confidence=0.9,
        total_reports=100,
        unique_reporters=50,
        weight_sum=10.0,
        mature_weight=6.0,
    )
    db_session.add(score)
    await db_session.commit()
    return score

@pytest.fixture
async def sample_business_listing(db_session, sample_phone_hash):
    """Cria business_listing de teste."""
    from app.models import BusinessListing
    listing = BusinessListing(
        phone_hmac=sample_phone_hash.phone_hmac,
        name="Banco Teste",
        category="banco",
        source="cnpj",
        source_url="https://example.com",
        verified_at=datetime.now(timezone.utc),
        confidence=0.95,
    )
    db_session.add(listing)
    await db_session.commit()
    return listing


class TestHealthEndpoints:
    """Testes de health check."""
    
    @pytest.mark.asyncio
    async def test_health_basic(self, client):
        response = await client.get("/health")
        assert response.status_code == 200
        data = response.json()
        assert data["status"] == "healthy"
        assert data["service"] == "conexus-caller-id-api"
    
    @pytest.mark.asyncio
    async def test_health_ready(self, client):
        response = await client.get("/health/ready")
        assert response.status_code == 200
        data = response.json()
        assert data["status"] in ["ready", "not_ready"]
        assert "checks" in data


class TestLookupEndpoint:
    """Testes do endpoint /v1/lookup/{prefix} (k-anônimo)."""
    
    @pytest.mark.asyncio
    async def test_lookup_valid_prefix(self, client, sample_reputation_score):
        response = await client.get("/v1/lookup/55119999")
        assert response.status_code == 200
        data = response.json()
        assert "items" in data
        assert "dataset_version" in data
        assert "prefix_used" in data
        assert data["prefix_used"] == "55119999"
    
    @pytest.mark.asyncio
    async def test_lookup_invalid_prefix_non_digits(self, client):
        response = await client.get("/v1/lookup/abc123")
        assert response.status_code == 400
        assert "digits" in response.json()["error"].lower()
    
    @pytest.mark.asyncio
    async def test_lookup_prefix_too_short(self, client):
        response = await client.get("/v1/lookup/12345")
        assert response.status_code == 400
        assert "at least" in response.json()["error"].lower()
    
    @pytest.mark.asyncio
    async def test_lookup_prefix_too_long(self, client):
        response = await client.get("/v1/lookup/123456789")
        assert response.status_code == 400
        assert "at most" in response.json()["error"].lower()
    
    @pytest.mark.asyncio
    async def test_lookup_rate_limit(self, client):
        # Testa rate limit (30 req/min)
        for i in range(35):
            response = await client.get(f"/v1/lookup/5511999{i%10}")
            if i >= 30:
                assert response.status_code == 429
                data = response.json()
                assert "rate limit" in data["error"].lower()
                break


class TestReportsEndpoint:
    """Testes do endpoint /v1/reports (com PoW)."""
    
    def _generate_pow(self, data: dict, difficulty: int = 4) -> str:
        """Gera nonce PoW válido para teste."""
        canonical = "|".join(f"{k}:{data[k]}" for k in ["phone_hash", "category", "occurred_at", "client_id"] if k in data)
        nonce = 0
        while True:
            hash_input = f"{nonce}{canonical}".encode()
            hash_result = hashlib.sha256(hash_input).hexdigest()
            if hash_result.startswith("0" * difficulty):
                return str(nonce)
            nonce += 1
            if nonce > 1000000:  # Safety
                break
        return "0"  # Fallback para teste
    
    @pytest.mark.asyncio
    async def test_report_valid_with_pow(self, client, sample_phone_hash):
        report_data = {
            "phone_hash": sample_phone_hash.phone_hmac,
            "category": "telemarketing",
            "occurred_at": datetime.now(timezone.utc).isoformat(),
            "pow_difficulty": 1,  # Baixa para teste
            "client_id": "test_client_123",
        }
        # Gera PoW válido
        report_data["pow_nonce"] = self._generate_pow(report_data, 1)
        
        response = await client.post(
            "/v1/reports",
            json=report_data,
            headers={"X-PoW-Nonce": report_data["pow_nonce"]}
        )
        assert response.status_code == 200
        data = response.json()
        assert data["success"] is True
        assert "report_id" in data
    
    @pytest.mark.asyncio
    async def test_report_invalid_pow(self, client, sample_phone_hash):
        report_data = {
            "phone_hash": sample_phone_hash.phone_hmac,
            "category": "telemarketing",
            "occurred_at": datetime.now(timezone.utc).isoformat(),
            "pow_difficulty": 4,
            "client_id": "test_client_123",
            "pow_nonce": "invalid_nonce",
        }
        
        response = await client.post(
            "/v1/reports",
            json=report_data,
            headers={"X-PoW-Nonce": "invalid"}
        )
        assert response.status_code == 400
        data = response.json()
        assert "proof" in data["error"].lower() or "pow" in data["error"].lower()
    
    @pytest.mark.asyncio
    async def test_report_missing_pow_header(self, client, sample_phone_hash):
        report_data = {
            "phone_hash": sample_phone_hash.phone_hmac,
            "category": "telemarketing",
            "occurred_at": datetime.now(timezone.utc).isoformat(),
            "pow_difficulty": 4,
            "client_id": "test_client_123",
        }
        
        response = await client.post("/v1/reports", json=report_data)
        assert response.status_code == 400
        data = response.json()
        assert "proof" in data["error"].lower()
    
    @pytest.mark.asyncio
    async def test_report_invalid_phone_hash(self, client):
        report_data = {
            "phone_hash": "invalid",  # Não é hex 16 chars
            "category": "telemarketing",
            "occurred_at": datetime.now(timezone.utc).isoformat(),
            "pow_difficulty": 1,
            "client_id": "test_client_123",
            "pow_nonce": "0",
        }
        
        response = await client.post(
            "/v1/reports",
            json=report_data,
            headers={"X-PoW-Nonce": "0"}
        )
        assert response.status_code == 400
    
    @pytest.mark.asyncio
    async def test_report_rate_limit(self, client, sample_phone_hash):
        report_data = {
            "phone_hash": sample_phone_hash.phone_hmac,
            "category": "telemarketing",
            "occurred_at": datetime.now(timezone.utc).isoformat(),
            "pow_difficulty": 1,
            "client_id": "test_client_123",
            "pow_nonce": "0",
        }
        
        for i in range(15):
            response = await client.post(
                "/v1/reports",
                json=report_data,
                headers={"X-PoW-Nonce": "0"}
            )
            if i >= 10:
                assert response.status_code == 429
                break


class TestReputationEndpoint:
    """Testes do endpoint /v1/reputation/{phone_hash}."""
    
    @pytest.mark.asyncio
    async def test_reputation_found(self, client, sample_reputation_score, sample_phone_hash):
        response = await client.get(f"/v1/reputation/{sample_phone_hash.phone_hmac}")
        assert response.status_code == 200
        data = response.json()
        assert data["phone_hash"] == sample_phone_hash.phone_hmac
        assert data["score"] == 85
        assert data["confidence"] == 0.9
        assert data["total_reports"] == 100
        assert data["unique_reporters"] == 50
    
    @pytest.mark.asyncio
    async def test_reputation_not_found(self, client):
        response = await client.get("/v1/reputation/ffffffffffffffff")
        assert response.status_code == 404
    
    @pytest.mark.asyncio
    async def test_reputation_invalid_hash(self, client):
        response = await client.get("/v1/reputation/invalid")
        assert response.status_code == 400


class TestDatasetEndpoints:
    """Testes dos endpoints /v1/datasets/*."""
    
    @pytest.mark.asyncio
    async def test_datasets_latest_no_manifest(self, client):
        response = await client.get("/v1/datasets/latest")
        assert response.status_code == 404
    
    @pytest.mark.asyncio
    async def test_datasets_history_empty(self, client):
        response = await client.get("/v1/datasets/history")
        assert response.status_code == 200
        data = response.json()
        assert isinstance(data, list)
        assert len(data) == 0


class TestBlocksEndpoint:
    """Testes do endpoint /v1/blocks (requer auth)."""
    
    @pytest.mark.asyncio
    async def test_blocks_without_auth(self, client):
        response = await client.get("/v1/blocks")
        assert response.status_code == 422  # Missing header
    
    @pytest.mark.asyncio
    async def test_blocks_with_auth(self, client, db_session):
        user_id = "test-user-123"
        response = await client.get("/v1/blocks", headers={"X-User-ID": user_id})
        assert response.status_code == 200
        data = response.json()
        assert "items" in data
        assert "total" in data
        assert data["total"] == 0


# ============================================================
# Testes de Contrato (Contract Tests)
# ============================================================

class TestAPIContracts:
    """Valida contratos da API (schemas de resposta)."""
    
    @pytest.mark.asyncio
    async def test_lookup_response_schema(self, client, sample_reputation_score):
        response = await client.get("/v1/lookup/55119999")
        assert response.status_code == 200
        data = response.json()
        
        # Valida estrutura obrigatória
        assert "items" in data
        assert "dataset_version" in data
        assert "prefix_used" in data
        assert isinstance(data["items"], list)
        assert isinstance(data["dataset_version"], int)
        assert isinstance(data["prefix_used"], str)
        
        if data["items"]:
            item = data["items"][0]
            assert "phone_hash" in item
            assert "country" in item
            assert "category" in item
            assert "score" in item
            assert "confidence" in item
            assert "last_seen" in item
            assert 0 <= item["score"] <= 100
            assert 0.0 <= item["confidence"] <= 1.0
    
    @pytest.mark.asyncio
    async def test_report_response_schema(self, client, sample_phone_hash):
        report_data = {
            "phone_hash": sample_phone_hash.phone_hmac,
            "category": "telemarketing",
            "occurred_at": datetime.now(timezone.utc).isoformat(),
            "pow_difficulty": 1,
            "client_id": "test_client_123",
            "pow_nonce": "0",
        }
        
        response = await client.post(
            "/v1/reports",
            json=report_data,
            headers={"X-PoW-Nonce": "0"}
        )
        
        if response.status_code == 200:
            data = response.json()
            assert "success" in data
            assert "message" in data
            assert isinstance(data["success"], bool)
            assert isinstance(data["message"], str)


# ============================================================
# Testes de Performance Básicos
# ============================================================

class TestPerformance:
    """Testes básicos de performance (latência)."""
    
    @pytest.mark.asyncio
    async def test_lookup_latency_p95(self, client, sample_reputation_score):
        import time
        latencies = []
        
        for _ in range(50):
            start = time.perf_counter()
            response = await client.get("/v1/lookup/55119999")
            elapsed = (time.perf_counter() - start) * 1000  # ms
            latencies.append(elapsed)
            assert response.status_code == 200
        
        latencies.sort()
        p95 = latencies[int(0.95 * len(latencies))]
        # Meta: p95 < 200ms para lookup k-anônimo
        assert p95 < 200, f"P95 latency {p95:.1f}ms exceeds 200ms threshold"
    
    @pytest.mark.asyncio
    async def test_report_latency_p95(self, client, sample_phone_hash):
        import time
        latencies = []
        
        for i in range(20):
            report_data = {
                "phone_hash": sample_phone_hash.phone_hmac,
                "category": "telemarketing",
                "occurred_at": datetime.now(timezone.utc).isoformat(),
                "pow_difficulty": 1,
                "client_id": f"test_client_{i}",
                "pow_nonce": "0",
            }
            
            start = time.perf_counter()
            response = await client.post(
                "/v1/reports",
                json=report_data,
                headers={"X-PoW-Nonce": "0"}
            )
            elapsed = (time.perf_counter() - start) * 1000
            latencies.append(elapsed)
        
        latencies.sort()
        p95 = latencies[int(0.95 * len(latencies))]
        # Meta: p95 < 500ms para report (inclui validação PoW)
        assert p95 < 500, f"P95 latency {p95:.1f}ms exceeds 500ms threshold"


if __name__ == "__main__":
    pytest.main([__file__, "-v", "--tb=short"])