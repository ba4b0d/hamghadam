"""challenge_invites + fcm_deliveries + challenges.max_participants (BE-C3)

Revision ID: 0003_invites_fcm
Revises: 0002_challenges
Create Date: 2026-08-16

"""

import sqlalchemy as sa

from alembic import op

revision = "0003_invites_fcm"
down_revision = "0002_challenges"
branch_labels = None
depends_on = None


def upgrade() -> None:
    # Optional participant cap (includes creator). NULL = unlimited.
    op.add_column("challenges", sa.Column("max_participants", sa.Integer(), nullable=True))

    op.create_table(
        "challenge_invites",
        sa.Column("id", sa.Integer(), autoincrement=True, nullable=False),
        sa.Column("challenge_id", sa.Integer(), nullable=False),
        sa.Column("code", sa.String(length=16), nullable=False),
        sa.Column("created_by", sa.Integer(), nullable=False),
        sa.Column("expires_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column(
            "created_at",
            sa.DateTime(timezone=True),
            server_default=sa.text("CURRENT_TIMESTAMP"),
            nullable=False,
        ),
        sa.ForeignKeyConstraint(["challenge_id"], ["challenges.id"], ondelete="CASCADE"),
        sa.ForeignKeyConstraint(["created_by"], ["users.id"], ondelete="CASCADE"),
        sa.PrimaryKeyConstraint("id"),
    )
    op.create_index(op.f("ix_challenge_invites_code"), "challenge_invites", ["code"], unique=True)
    op.create_index(
        op.f("ix_challenge_invites_challenge_id"), "challenge_invites", ["challenge_id"], unique=False
    )
    op.create_index(
        op.f("ix_challenge_invites_created_by"), "challenge_invites", ["created_by"], unique=False
    )

    op.create_table(
        "fcm_deliveries",
        sa.Column("id", sa.Integer(), autoincrement=True, nullable=False),
        sa.Column("user_id", sa.Integer(), nullable=False),
        sa.Column("type", sa.String(length=32), nullable=False),
        sa.Column("success", sa.Boolean(), nullable=False),
        sa.Column("message_id", sa.String(length=255), nullable=True),
        sa.Column("error", sa.String(length=512), nullable=True),
        sa.Column("payload", sa.JSON(), nullable=False),
        sa.Column(
            "sent_at",
            sa.DateTime(timezone=True),
            server_default=sa.text("CURRENT_TIMESTAMP"),
            nullable=False,
        ),
        sa.ForeignKeyConstraint(["user_id"], ["users.id"], ondelete="CASCADE"),
        sa.PrimaryKeyConstraint("id"),
    )
    op.create_index(op.f("ix_fcm_deliveries_user_id"), "fcm_deliveries", ["user_id"], unique=False)
    op.create_index(
        op.f("ix_fcm_user_type_sent"), "fcm_deliveries", ["user_id", "type", "sent_at"], unique=False
    )


def downgrade() -> None:
    op.drop_index(op.f("ix_fcm_user_type_sent"), table_name="fcm_deliveries")
    op.drop_index(op.f("ix_fcm_deliveries_user_id"), table_name="fcm_deliveries")
    op.drop_table("fcm_deliveries")
    op.drop_index(op.f("ix_challenge_invites_created_by"), table_name="challenge_invites")
    op.drop_index(op.f("ix_challenge_invites_challenge_id"), table_name="challenge_invites")
    op.drop_index(op.f("ix_challenge_invites_code"), table_name="challenge_invites")
    op.drop_table("challenge_invites")
    op.drop_column("challenges", "max_participants")
