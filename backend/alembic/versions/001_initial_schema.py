"""
Alembic initial migration: Create all tables for Conexus Caller ID.
"""
from alembic import op
import sqlalchemy as sa
from sqlalchemy.dialects import postgresql

revision = "001_initial_schema"
down_revision = None
branch_labels = None
depends_on = None


def upgrade() -> None:
    # phone_hashes
    op.create_table(
        "phone_hashes",
        sa.Column("id", sa.BigInteger(), primary_key=True, autoincrement=True),
        sa.Column("phone_hmac", sa.String(16), unique=True, nullable=False, index=True),
        sa.Column("country", sa.String(2), default="BR", nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), default=sa.func.now(), nullable=False),
    )
    
    # business_listings
    op.create_table(
        "business_listings",
        sa.Column("id", sa.BigInteger(), primary_key=True, autoincrement=True),
        sa.Column("phone_hmac", sa.String(16), sa.ForeignKey("phone_hashes.phone_hmac", ondelete="CASCADE"), nullable=False, index=True),
        sa.Column("name", sa.String(200), nullable=False),
        sa.Column("category", sa.String(50), nullable=False, index=True),
        sa.Column("source", sa.String(100), nullable=False),
        sa.Column("source_url", sa.Text(), nullable=True),
        sa.Column("verified_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("confidence", sa.Numeric(3, 2), nullable=False),
    )
    op.create_unique_constraint("uq_business_phone_source", "business_listings", ["phone_hmac", "source"])
    
    # spam_reports
    op.create_table(
        "spam_reports",
        sa.Column("id", sa.BigInteger(), primary_key=True, autoincrement=True),
        sa.Column("phone_hmac", sa.String(16), sa.ForeignKey("phone_hashes.phone_hmac", ondelete="CASCADE"), nullable=False, index=True),
        sa.Column("category", sa.String(30), nullable=False),
        sa.Column("reporter_hash", sa.String(16), nullable=False, index=True),
        sa.Column("network_id", sa.String(50), nullable=True, index=True),
        sa.Column("occurred_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("status", sa.String(20), default="pending", nullable=False, index=True),
        sa.Column("moderated_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column("moderator_id", sa.BigInteger(), nullable=True),
        sa.Column("contest_deadline", sa.DateTime(timezone=True), nullable=True),
        sa.Column("created_at", sa.DateTime(timezone=True), default=sa.func.now(), nullable=False),
        sa.Column("false_positives", sa.Integer(), default=0, nullable=False),
    )
    op.create_index("ix_spam_report_status_created", "spam_reports", ["status", "created_at"])
    op.create_index("ix_spam_report_phone_status", "spam_reports", ["phone_hmac", "status"])
    
    # reputation_scores
    op.create_table(
        "reputation_scores",
        sa.Column("phone_hmac", sa.String(16), sa.ForeignKey("phone_hashes.phone_hmac", ondelete="CASCADE"), primary_key=True),
        sa.Column("score", sa.SmallInteger(), nullable=False),
        sa.Column("confidence", sa.Numeric(3, 2), nullable=False),
        sa.Column("total_reports", sa.Integer(), default=0, nullable=False),
        sa.Column("unique_reporters", sa.Integer(), default=0, nullable=False),
        sa.Column("weight_sum", sa.Numeric(6, 2), default=0, nullable=False),
        sa.Column("mature_weight", sa.Numeric(6, 2), default=0, nullable=False),
        sa.Column("last_updated", sa.DateTime(timezone=True), default=sa.func.now(), onupdate=sa.func.now(), nullable=False),
        sa.Column("dispute_status", sa.String(20), nullable=True),
        sa.Column("dispute_deadline", sa.DateTime(timezone=True), nullable=True),
        sa.Column("campaign_flag", sa.Boolean(), default=False, nullable=False),
    )
    op.create_index("ix_reputation_score_score", "reputation_scores", ["score"])
    
    # user_blocklists
    op.create_table(
        "user_blocklists",
        sa.Column("id", sa.BigInteger(), primary_key=True, autoincrement=True),
        sa.Column("user_id", sa.String(36), nullable=False, index=True),
        sa.Column("phone_hmac", sa.String(16), sa.ForeignKey("phone_hashes.phone_hmac", ondelete="CASCADE"), nullable=False, index=True),
        sa.Column("action", sa.String(10), nullable=False),
        sa.Column("source", sa.String(20), default="local", nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), default=sa.func.now(), nullable=False),
    )
    op.create_unique_constraint("uq_user_blocklist", "user_blocklists", ["user_id", "phone_hmac", "action"])
    
    # audit_events
    op.create_table(
        "audit_events",
        sa.Column("id", sa.BigInteger(), primary_key=True, autoincrement=True),
        sa.Column("actor_id", sa.String(36), nullable=True),
        sa.Column("action", sa.String(50), nullable=False),
        sa.Column("target_type", sa.String(30), nullable=False),
        sa.Column("target_id", sa.String(100), nullable=False),
        sa.Column("details", sa.Text(), nullable=True),  # JSON
        sa.Column("created_at", sa.DateTime(timezone=True), default=sa.func.now(), nullable=False),
    )
    op.create_index("ix_audit_event_actor_created", "audit_events", ["actor_id", "created_at"])
    op.create_index("ix_audit_event_target", "audit_events", ["target_type", "target_id"])
    
    # dataset_manifests
    op.create_table(
        "dataset_manifests",
        sa.Column("version", sa.Integer(), primary_key=True),
        sa.Column("entries_count", sa.Integer(), nullable=False),
        sa.Column("digest_sha256", sa.String(64), nullable=False),
        sa.Column("signature_ed25519", sa.String(128), nullable=False),
        sa.Column("public_key_ed25519", sa.String(64), nullable=False),
        sa.Column("published_at", sa.DateTime(timezone=True), default=sa.func.now(), nullable=False),
        sa.Column("previous_version", sa.Integer(), sa.ForeignKey("dataset_manifests.version"), nullable=True),
        sa.Column("entries_json", sa.Text(), nullable=True),
    )
    op.create_index("ix_dataset_manifest_published", "dataset_manifests", ["published_at"])
    
    # phone_hashes extra columns
    op.add_column("phone_hashes", sa.Column("last_campaign_check", sa.DateTime(timezone=True), nullable=True))
    op.add_column("phone_hashes", sa.Column("campaign_detected", sa.Boolean(), default=False, nullable=False))


def downgrade() -> None:
    op.drop_table("dataset_manifests")
    op.drop_table("audit_events")
    op.drop_table("user_blocklists")
    op.drop_table("reputation_scores")
    op.drop_table("spam_reports")
    op.drop_table("business_listings")
    op.drop_table("phone_hashes")