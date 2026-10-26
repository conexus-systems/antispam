"""
SQLAlchemy models for Conexus Caller ID.
"""
import enum
from datetime import datetime
from sqlalchemy import (
    BigInteger, String, Integer, Numeric, DateTime, Text, ForeignKey, UniqueConstraint, Index
)
from sqlalchemy.orm import Mapped, mapped_column, relationship
from app.database import Base


class ReportStatus(str, enum.Enum):
    PENDING = "pending"
    APPROVED = "approved"
    REJECTED = "rejected"
    CONTESTED = "contested"


class ReportCategory(str, enum.Enum):
    SPAM = "spam"
    TELEMARKETING = "telemarketing"
    FRAUD = "fraud"
    ROBOCALL = "robocall"
    COLLECTION = "cobranca"
    SURVEY = "pesquisa"
    SCAM = "golpe"
    BANK = "banco"
    CARRIER = "operadora"
    DELIVERY = "entrega"
    OTHER = "outro"


class BlockAction(str, enum.Enum):
    BLOCK = "block"
    ALLOW = "allow"


class PhoneHash(Base):
    """Hash HMAC de números de telefone (não reversível sem segredo)."""
    __tablename__ = "phone_hashes"

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    phone_hmac: Mapped[str] = mapped_column(String(16), unique=True, nullable=False, index=True)
    country: Mapped[str] = mapped_column(String(2), default="BR", nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=datetime.utcnow, nullable=False)

    business_listings = relationship("BusinessListing", back_populates="phone_hash", cascade="all, delete-orphan")
    spam_reports = relationship("SpamReport", back_populates="phone_hash", cascade="all, delete-orphan")
    reputation_score = relationship("ReputationScore", back_populates="phone_hash", uselist=False, cascade="all, delete-orphan")


class BusinessListing(Base):
    """Listagens comerciais verificadas."""
    __tablename__ = "business_listings"

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    phone_hmac: Mapped[str] = mapped_column(String(16), ForeignKey("phone_hashes.phone_hmac", ondelete="CASCADE"), nullable=False, index=True)
    name: Mapped[str] = mapped_column(String(200), nullable=False)
    category: Mapped[str] = mapped_column(String(50), nullable=False)
    source: Mapped[str] = mapped_column(String(100), nullable=False)
    source_url: Mapped[str | None] = mapped_column(Text, nullable=True)
    verified_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    confidence: Mapped[float] = mapped_column(Numeric(3, 2), nullable=False)

    phone_hash = relationship("PhoneHash", back_populates="business_listings")

    __table_args__ = (
        UniqueConstraint("phone_hmac", "source", name="uq_business_phone_source"),
        Index("ix_business_category", "category"),
    )


class SpamReport(Base):
    """Denúncias de spam."""
    __tablename__ = "spam_reports"

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    phone_hmac: Mapped[str] = mapped_column(String(16), ForeignKey("phone_hashes.phone_hmac", ondelete="CASCADE"), nullable=False, index=True)
    category: Mapped[ReportCategory] = mapped_column(String(30), nullable=False)
    reporter_hash: Mapped[str] = mapped_column(String(16), nullable=False, index=True)
    occurred_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    status: Mapped[ReportStatus] = mapped_column(String(20), default=ReportStatus.PENDING, nullable=False, index=True)
    moderated_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    moderator_id: Mapped[int | None] = mapped_column(BigInteger, nullable=True)
    contest_deadline: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=datetime.utcnow, nullable=False)

    phone_hash = relationship("PhoneHash", back_populates="spam_reports")

    __table_args__ = (
        Index("ix_spam_report_status_created", "status", "created_at"),
        Index("ix_spam_report_phone_status", "phone_hmac", "status"),
    )


class ReputationScore(Base):
    """Scores de reputação agregados."""
    __tablename__ = "reputation_scores"

    phone_hmac: Mapped[str] = mapped_column(String(16), ForeignKey("phone_hashes.phone_hmac", ondelete="CASCADE"), primary_key=True)
    score: Mapped[int] = mapped_column(Integer, nullable=False)
    confidence: Mapped[float] = mapped_column(Numeric(3, 2), nullable=False)
    total_reports: Mapped[int] = mapped_column(Integer, default=0, nullable=False)
    unique_reporters: Mapped[int] = mapped_column(Integer, default=0, nullable=False)
    weight_sum: Mapped[float] = mapped_column(Numeric(6, 2), default=0, nullable=False)
    mature_weight: Mapped[float] = mapped_column(Numeric(6, 2), default=0, nullable=False)
    last_updated: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=datetime.utcnow, onupdate=datetime.utcnow, nullable=False)

    phone_hash = relationship("PhoneHash", back_populates="reputation_score")

    __table_args__ = (
        Index("ix_reputation_score_score", "score"),
    )


class UserBlocklist(Base):
    """Bloqueios por usuário (sync via Conta Conexus)."""
    __tablename__ = "user_blocklists"

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    user_id: Mapped[str] = mapped_column(String(36), nullable=False, index=True)
    phone_hmac: Mapped[str] = mapped_column(String(16), ForeignKey("phone_hashes.phone_hmac", ondelete="CASCADE"), nullable=False, index=True)
    action: Mapped[BlockAction] = mapped_column(String(10), nullable=False)
    source: Mapped[str] = mapped_column(String(20), default="local", nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=datetime.utcnow, nullable=False)

    __table_args__ = (
        UniqueConstraint("user_id", "phone_hmac", "action", name="uq_user_blocklist"),
    )


class AuditEvent(Base):
    """Auditoria administrativa."""
    __tablename__ = "audit_events"

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    actor_id: Mapped[str | None] = mapped_column(String(36), nullable=True)
    action: Mapped[str] = mapped_column(String(50), nullable=False)
    target_type: Mapped[str] = mapped_column(String(30), nullable=False)
    target_id: Mapped[str] = mapped_column(String(100), nullable=False)
    details: Mapped[dict | None] = mapped_column(Text, nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=datetime.utcnow, nullable=False)

    __table_args__ = (
        Index("ix_audit_event_actor_created", "actor_id", "created_at"),
        Index("ix_audit_event_target", "target_type", "target_id"),
    )


class DatasetManifest(Base):
    """Manifests de dataset publicados (assinados Ed25519)."""
    __tablename__ = "dataset_manifests"

    version: Mapped[int] = mapped_column(Integer, primary_key=True)
    entries_count: Mapped[int] = mapped_column(Integer, nullable=False)
    digest_sha256: Mapped[str] = mapped_column(String(64), nullable=False)
    signature_ed25519: Mapped[str] = mapped_column(String(128), nullable=False)
    public_key_ed25519: Mapped[str] = mapped_column(String(64), nullable=False)
    published_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=datetime.utcnow, nullable=False)
    previous_version: Mapped[int | None] = mapped_column(Integer, ForeignKey("dataset_manifests.version"), nullable=True)

    __table_args__ = (
        Index("ix_dataset_manifest_published", "published_at"),
    )
