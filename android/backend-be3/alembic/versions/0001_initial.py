"""initial schema: users, devices, daily_scores

Revision ID: 0001_initial
Revises:
Create Date: 2026-08-16

"""
from alembic import op
import sqlalchemy as sa

revision = "0001_initial"
down_revision = None
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table(
        "users",
        sa.Column("id", sa.Integer(), autoincrement=True, nullable=False),
        sa.Column("email", sa.String(length=255), nullable=False),
        sa.Column("password_hash", sa.String(length=255), nullable=False),
        sa.Column("display_name", sa.String(length=100), nullable=True),
        sa.Column("premium", sa.Boolean(), nullable=False),
        sa.Column("tz_offset", sa.Integer(), nullable=True),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.text("CURRENT_TIMESTAMP"), nullable=False),
        sa.Column("updated_at", sa.DateTime(timezone=True), server_default=sa.text("CURRENT_TIMESTAMP"), nullable=False),
        sa.PrimaryKeyConstraint("id"),
    )
    op.create_index(op.f("ix_users_email"), "users", ["email"], unique=True)

    op.create_table(
        "devices",
        sa.Column("id", sa.Integer(), autoincrement=True, nullable=False),
        sa.Column("user_id", sa.Integer(), nullable=False),
        sa.Column("device_token", sa.String(length=512), nullable=False),
        sa.Column("kind", sa.String(length=20), server_default=sa.text("'android'"), nullable=False),
        sa.Column("model", sa.String(length=100), nullable=True),
        sa.Column("last_seen_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.text("CURRENT_TIMESTAMP"), nullable=False),
        sa.ForeignKeyConstraint(["user_id"], ["users.id"], ondelete="CASCADE"),
        sa.PrimaryKeyConstraint("id"),
        sa.UniqueConstraint("user_id", "device_token", name="uq_device_user_token"),
    )
    op.create_index(op.f("ix_devices_user_id"), "devices", ["user_id"], unique=False)

    op.create_table(
        "daily_scores",
        sa.Column("id", sa.Integer(), autoincrement=True, nullable=False),
        sa.Column("user_id", sa.Integer(), nullable=False),
        sa.Column("metric", sa.String(length=32), nullable=False),
        sa.Column("date", sa.Date(), nullable=False),
        sa.Column("value", sa.Float(), nullable=False),
        sa.Column("tz_offset", sa.Integer(), nullable=False),
        sa.Column("source", sa.String(length=32), server_default=sa.text("'health_connect'"), nullable=False),
        sa.Column("source_apps", sa.JSON(), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.text("CURRENT_TIMESTAMP"), nullable=False),
        sa.Column("updated_at", sa.DateTime(timezone=True), server_default=sa.text("CURRENT_TIMESTAMP"), nullable=False),
        sa.ForeignKeyConstraint(["user_id"], ["users.id"], ondelete="CASCADE"),
        sa.PrimaryKeyConstraint("id"),
        sa.UniqueConstraint("user_id", "metric", "date", name="uq_daily_user_metric_date"),
    )
    op.create_index("ix_daily_user_date", "daily_scores", ["user_id", "date"], unique=False)
    op.create_index(op.f("ix_daily_scores_user_id"), "daily_scores", ["user_id"], unique=False)


def downgrade() -> None:
    op.drop_index(op.f("ix_daily_scores_user_id"), table_name="daily_scores")
    op.drop_index("ix_daily_user_date", table_name="daily_scores")
    op.drop_table("daily_scores")
    op.drop_index(op.f("ix_devices_user_id"), table_name="devices")
    op.drop_table("devices")
    op.drop_index(op.f("ix_users_email"), table_name="users")
    op.drop_table("users")
