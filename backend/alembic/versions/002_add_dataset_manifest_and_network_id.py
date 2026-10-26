"""
Alembic migration: Add DatasetManifest table and update SpamReport with network_id.
"""
from alembic import op
import sqlalchemy as sa
from sqlalchemy.dialects import postgresql

revision = "002_add_dataset_manifest_and_network_id"
down_revision = "001_initial_schema"
branch_labels = None
depends_on = None


def upgrade() -> None:
    # Add network_id column to spam_reports
    op.add_column("spam_reports", sa.Column("network_id", sa.String(50), nullable=True))
    op.create_index("ix_spam_reports_network_id", "spam_reports", ["network_id"])
    
    # Add false_positives column to spam_reports
    op.add_column("spam_reports", sa.Column("false_positives", sa.Integer(), nullable=False, server_default="0"))
    
    # Add dispute fields to reputation_scores
    op.add_column("reputation_scores", sa.Column("dispute_status", sa.String(20), nullable=True))
    op.add_column("reputation_scores", sa.Column("dispute_deadline", sa.DateTime(timezone=True), nullable=True))
    op.add_column("reputation_scores", sa.Column("campaign_flag", sa.Boolean(), nullable=False, server_default="false"))
    
    # Create dataset_manifests table
    op.create_table(
        "dataset_manifests",
        sa.Column("version", sa.Integer(), nullable=False, primary_key=True),
        sa.Column("entries_count", sa.Integer(), nullable=False),
        sa.Column("digest_sha256", sa.String(64), nullable=False),
        sa.Column("signature_ed25519", sa.String(128), nullable=False),
        sa.Column("public_key_ed25519", sa.String(64), nullable=False),
        sa.Column("published_at", sa.DateTime(timezone=True), nullable=False, server_default=sa.func.now()),
        sa.Column("previous_version", sa.Integer(), sa.ForeignKey("dataset_manifests.version"), nullable=True),
        sa.Column("entries_json", sa.Text(), nullable=True),  # Para verificação
    )
    op.create_index("ix_dataset_manifest_published", "dataset_manifests", ["published_at"])
    
    # Add campaign detection fields to phone_hashes
    op.add_column("phone_hashes", sa.Column("last_campaign_check", sa.DateTime(timezone=True), nullable=True))
    op.add_column("phone_hashes", sa.Column("campaign_detected", sa.Boolean(), nullable=False, server_default="false"))


def downgrade() -> None:
    op.drop_index("ix_dataset_manifest_published", table_name="dataset_manifests")
    op.drop_table("dataset_manifests")
    
    op.drop_column("reputation_scores", "campaign_flag")
    op.drop_column("reputation_scores", "dispute_deadline")
    op.drop_column("reputation_scores", "dispute_status")
    
    op.drop_column("spam_reports", "false_positives")
    op.drop_index("ix_spam_reports_network_id", table_name="spam_reports")
    op.drop_column("spam_reports", "network_id")
    
    op.drop_column("phone_hashes", "campaign_detected")
    op.drop_column("phone_hashes", "last_campaign_check")