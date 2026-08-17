"""Verify the alembic migration chain applies cleanly on a fresh SQLite DB.

This exercises the same `alembic upgrade head` command docker-compose runs.
"""

import os
import subprocess
import sys

from sqlalchemy import create_engine, inspect

PROJECT_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def test_alembic_upgrade_head_on_fresh_sqlite(tmp_path):
    db_path = tmp_path / "migrated.db"
    env = {
        **os.environ,
        "DATABASE_URL": f"sqlite:///{db_path.as_posix()}",
        "APP_ENV": "test",
    }
    result = subprocess.run(
        [sys.executable, "-m", "alembic", "upgrade", "head"],
        cwd=PROJECT_ROOT,
        env=env,
        capture_output=True,
        text=True,
        timeout=120,
    )
    assert result.returncode == 0, f"alembic failed:\n{result.stdout}\n{result.stderr}"

    engine = create_engine(f"sqlite:///{db_path.as_posix()}")
    tables = set(inspect(engine).get_table_names())
    assert {"users", "devices", "daily_scores"} <= tables, tables
    assert {"challenges", "challenge_participants"} <= tables, tables
    assert {"challenge_invites", "fcm_deliveries"} <= tables, tables

    # BE-C3: challenges gained the optional participant cap column.
    challenge_cols = {c["name"] for c in inspect(engine).get_columns("challenges")}
    assert "max_participants" in challenge_cols, challenge_cols
    invite_cols = {c["name"] for c in inspect(engine).get_columns("challenge_invites")}
    assert {"code", "expires_at", "created_by", "challenge_id"} <= invite_cols, invite_cols

    # downgrade back to base to prove reversibility
    result = subprocess.run(
        [sys.executable, "-m", "alembic", "downgrade", "base"],
        cwd=PROJECT_ROOT,
        env=env,
        capture_output=True,
        text=True,
        timeout=120,
    )
    assert result.returncode == 0, f"alembic downgrade failed:\n{result.stdout}\n{result.stderr}"
    engine.dispose()
