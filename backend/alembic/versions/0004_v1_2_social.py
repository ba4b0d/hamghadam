"""users social fields + friendships (V1.2 Social)

Revision ID: 0004_v1_2_social
Revises: 0003_invites_fcm
Create Date: 2026-08-18

"""

import sqlalchemy as sa
from alembic import op

revision = "0004_v1_2_social"
down_revision = "0003_invites_fcm"
branch_labels = None
depends_on = None


def upgrade() -> None:
    # 1. Add social columns to users
    with op.batch_alter_table("users") as batch_op:
        batch_op.add_column(sa.Column("bio", sa.String(length=255), nullable=True))
        batch_op.add_column(sa.Column("avatar_url", sa.String(length=512), nullable=True))
        batch_op.add_column(sa.Column("location", sa.String(length=100), nullable=True))
        batch_op.add_column(sa.Column("google_id", sa.String(length=255), nullable=True))
        batch_op.add_column(
            sa.Column("auth_provider", sa.String(length=32), server_default="email", nullable=False)
        )
        batch_op.create_index(op.f("ix_users_google_id"), ["google_id"], unique=True)

    # 2. Friendships table
    op.create_table(
        "friendships",
        sa.Column("id", sa.Integer(), autoincrement=True, nullable=False),
        sa.Column("requester_id", sa.Integer(), nullable=False),
        sa.Column("addressee_id", sa.Integer(), nullable=False),
        sa.Column("status", sa.String(length=32), server_default="PENDING", nullable=False),
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
        sa.ForeignKeyConstraint(["requester_id"], ["users.id"], ondelete="CASCADE"),
        sa.ForeignKeyConstraint(["addressee_id"], ["users.id"], ondelete="CASCADE"),
        sa.PrimaryKeyConstraint("id"),
        sa.UniqueConstraint("requester_id", "addressee_id", name="uq_friendship_requester_addressee"),
        sa.CheckConstraint("requester_id != addressee_id", name="ck_friendship_no_self"),
    )
    op.create_index(op.f("ix_friendships_requester_id"), "friendships", ["requester_id"], unique=False)
    op.create_index(op.f("ix_friendships_addressee_id"), "friendships", ["addressee_id"], unique=False)
    op.create_index(
        "idx_friendships_requester_status", "friendships", ["requester_id", "status"], unique=False
    )
    op.create_index(
        "idx_friendships_addressee_status", "friendships", ["addressee_id", "status"], unique=False
    )


def downgrade() -> None:
    op.drop_index("idx_friendships_addressee_status", table_name="friendships")
    op.drop_index("idx_friendships_requester_status", table_name="friendships")
    op.drop_index(op.f("ix_friendships_addressee_id"), table_name="friendships")
    op.drop_index(op.f("ix_friendships_requester_id"), table_name="friendships")
    op.drop_table("friendships")

    with op.batch_alter_table("users") as batch_op:
        batch_op.drop_index(op.f("ix_users_google_id"))
        batch_op.drop_column("auth_provider")
        batch_op.drop_column("google_id")
        batch_op.drop_column("location")
        batch_op.drop_column("avatar_url")
        batch_op.drop_column("bio")
