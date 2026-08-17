"""challenges + challenge_participants (BE-C2)

Revision ID: 0002_challenges
Revises: 0001_initial
Create Date: 2026-08-16

"""

import sqlalchemy as sa

from alembic import op

revision = "0002_challenges"
down_revision = "0001_initial"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table(
        "challenges",
        sa.Column("id", sa.Integer(), autoincrement=True, nullable=False),
        sa.Column("title", sa.String(length=120), nullable=False),
        sa.Column("metric", sa.String(length=32), nullable=False),
        sa.Column("starts_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("ends_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("status", sa.String(length=16), nullable=False),
        sa.Column("invite_only", sa.Boolean(), nullable=False),
        sa.Column("creator_id", sa.Integer(), nullable=False),
        sa.Column(
            "created_at",
            sa.DateTime(timezone=True),
            server_default=sa.text("CURRENT_TIMESTAMP"),
            nullable=False,
        ),
        sa.Column(
            "updated_at",
            sa.DateTime(timezone=True),
            server_default=sa.text("CURRENT_TIMESTAMP"),
            nullable=False,
        ),
        sa.ForeignKeyConstraint(["creator_id"], ["users.id"], ondelete="CASCADE"),
        sa.PrimaryKeyConstraint("id"),
    )
    op.create_index(op.f("ix_challenges_creator_id"), "challenges", ["creator_id"], unique=False)

    op.create_table(
        "challenge_participants",
        sa.Column("id", sa.Integer(), autoincrement=True, nullable=False),
        sa.Column("challenge_id", sa.Integer(), nullable=False),
        sa.Column("user_id", sa.Integer(), nullable=False),
        sa.Column(
            "joined_at",
            sa.DateTime(timezone=True),
            server_default=sa.text("CURRENT_TIMESTAMP"),
            nullable=False,
        ),
        sa.ForeignKeyConstraint(["challenge_id"], ["challenges.id"], ondelete="CASCADE"),
        sa.ForeignKeyConstraint(["user_id"], ["users.id"], ondelete="CASCADE"),
        sa.PrimaryKeyConstraint("id"),
        sa.UniqueConstraint("challenge_id", "user_id", name="uq_participant_challenge_user"),
    )
    op.create_index(
        op.f("ix_challenge_participants_challenge_id"),
        "challenge_participants",
        ["challenge_id"],
        unique=False,
    )
    op.create_index(
        op.f("ix_challenge_participants_user_id"),
        "challenge_participants",
        ["user_id"],
        unique=False,
    )


def downgrade() -> None:
    op.drop_index(op.f("ix_challenge_participants_user_id"), table_name="challenge_participants")
    op.drop_index(
        op.f("ix_challenge_participants_challenge_id"), table_name="challenge_participants"
    )
    op.drop_table("challenge_participants")
    op.drop_index(op.f("ix_challenges_creator_id"), table_name="challenges")
    op.drop_table("challenges")
