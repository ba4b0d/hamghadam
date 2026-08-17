"""Daily ingest use case: validate provenance, upsert per (user, metric, date).

Repeat-safe: a second POST for the same user+date is an idempotent upsert —
same payload is a no-op value-wise, changed payload overwrites (latest wins).
Anti-cheat v1: only `source == health_connect` is accepted; anything else is
rejected before touching the DB. Sanity bounds (steps <= 250k/day etc.) are
log-only, per the PM plan.
"""

import logging

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.models import DailyScore
from app.models.daily_score import (
    ALLOWED_SOURCES,
    METRIC_AVG_HR,
    METRIC_SLEEP_SECONDS,
    METRIC_STEPS,
)
from app.schemas.daily import (
    AVG_HR_SANITY_MAX,
    SLEEP_SECONDS_SANITY_MAX,
    STEPS_SANITY_MAX,
    DailyIngestRequest,
)

logger = logging.getLogger("app.ingest")


class ManualEntryRejected(Exception):
    """Raised when a payload claims a source other than Health Connect."""


def _log_sanity_warnings(payload: DailyIngestRequest) -> None:
    if payload.steps > STEPS_SANITY_MAX:
        logger.warning(
            "sanity: steps=%d exceeds %d (user date=%s) — accepted, log-only",
            payload.steps, STEPS_SANITY_MAX, payload.date,
        )
    if payload.sleep_seconds is not None and payload.sleep_seconds > SLEEP_SECONDS_SANITY_MAX:
        logger.warning(
            "sanity: sleep_seconds=%s exceeds %s (user date=%s) — accepted, log-only",
            payload.sleep_seconds, SLEEP_SECONDS_SANITY_MAX, payload.date,
        )
    if payload.avg_hr is not None and payload.avg_hr > AVG_HR_SANITY_MAX:
        logger.warning(
            "sanity: avg_hr=%s exceeds %s (user date=%s) — accepted, log-only",
            payload.avg_hr, AVG_HR_SANITY_MAX, payload.date,
        )


def _metric_rows(payload: DailyIngestRequest) -> list[tuple[str, float]]:
    rows: list[tuple[str, float]] = [(METRIC_STEPS, float(payload.steps))]
    if payload.sleep_seconds is not None:
        rows.append((METRIC_SLEEP_SECONDS, float(payload.sleep_seconds)))
    if payload.avg_hr is not None:
        rows.append((METRIC_AVG_HR, float(payload.avg_hr)))
    return rows


def upsert_daily(db: Session, user_id: int, payload: DailyIngestRequest) -> tuple[list[DailyScore], bool]:
    """Validate provenance, then upsert rows. Returns (rows, created_any)."""
    if payload.source not in ALLOWED_SOURCES:
        raise ManualEntryRejected(
            f"source '{payload.source}' is not accepted; only Health Connect-synced "
            "data (source='health_connect') is allowed in v1 — manual entry is not supported"
        )

    _log_sanity_warnings(payload)

    source_apps = list(dict.fromkeys(payload.source_apps))  # dedupe, preserve order
    created_any = False
    rows: list[DailyScore] = []

    for metric, value in _metric_rows(payload):
        row = db.scalar(
            select(DailyScore).where(
                DailyScore.user_id == user_id,
                DailyScore.metric == metric,
                DailyScore.date == payload.date,
            )
        )
        if row is None:
            row = DailyScore(
                user_id=user_id,
                metric=metric,
                date=payload.date,
                value=value,
                tz_offset=payload.tz_offset,
                source=payload.source,
                source_apps=source_apps,
            )
            db.add(row)
            created_any = True
        else:
            row.value = value
            row.tz_offset = payload.tz_offset
            row.source = payload.source
            row.source_apps = source_apps
        rows.append(row)

    db.commit()
    for row in rows:
        db.refresh(row)
    return rows, created_any
