"""
Shared test fixtures and configuration for Conexus Caller ID Backend tests.
"""
import pytest
import pytest_asyncio
from sqlalchemy.ext.asyncio import create_async_engine, AsyncSession
from sqlalchemy.orm import sessionmaker
from sqlalchemy import event

from app.database import Base
from app.config import settings

# Test database URL
TEST_DATABASE_URL = "postgresql+asyncpg://postgres:postgres@localhost:5432/conexus_caller_id_test"

@pytest_asyncio.fixture(scope="session")
async def test_engine():
    """Cria engine de teste e tabelas."""
    engine = create_async_engine(TEST_DATABASE_URL, pool_pre_ping=True)
    
    # Cria todas as tabelas
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)
    
    yield engine
    
    # Cleanup
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.drop_all)
    await engine.dispose()

@pytest_asyncio.fixture
async def db_session(test_engine):
    """Sessão de banco para cada teste (com rollback automático)."""
    async_session = sessionmaker(test_engine, class_=AsyncSession, expire_on_commit=False)
    
    async with async_session() as session:
        # Inicia transação
        async with session.begin():
            yield session
            # Rollback automático ao sair do context manager

@pytest_asyncio.fixture
async def client(db_session):
    """Cliente HTTP de teste com override de dependência."""
    from app.main import app
    from app.database import get_db
    from httpx import AsyncClient, ASGITransport
    
    async def override_get_db():
        yield db_session
    
    app.dependency_overrides[get_db] = override_get_db
    
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as ac:
        yield ac
    
    app.dependency_overrides.clear()

# Fixtures de dados de teste

@pytest_asyncio.fixture
async def sample_phone_hash(db_session):
    from app.models import PhoneHash
    phone = PhoneHash(phone_hmac="a1b2c3d4e5f67890", country="BR")
    db_session.add(phone)
    await db_session.commit()
    await db_session.refresh(phone)
    return phone

@pytest_asyncio.fixture
async def sample_reputation_score(db_session, sample_phone_hash):
    from app.models import ReputationScore
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
    await db_session.refresh(score)
    return score

@pytest_asyncio.fixture
async def sample_business_listing(db_session, sample_phone_hash):
    from app.models import BusinessListing
    from datetime import datetime, timezone
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
    await db_session.refresh(listing)
    return listing

@pytest_asyncio.fixture
async def sample_spam_report(db_session, sample_phone_hash):
    from app.models import SpamReport, ReportStatus
    from datetime import datetime, timezone
    report = SpamReport(
        phone_hmac="a1b2c3d4e5f67890",
        category="telemarketing",
        reporter_hash="reporter123",
        occurred_at=datetime.now(timezone.utc),
        status=ReportStatus.PENDING,
    )
    db_session.add(report)
    await db_session.commit()
    await db_session.refresh(report)
    return report

# Configuração de evento para rollback automático em transações aninhadas
@event.listens_for(AsyncSession, "after_transaction_end")
def restart_savepoint(session, transaction):
    if transaction.nested and not transaction._parent.nested:
        session.expire_all()
        session.begin_nested()