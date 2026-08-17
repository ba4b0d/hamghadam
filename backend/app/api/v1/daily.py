"""Daily ingest + read-back endpoints."""

import logging
from datetime import date

from fastapi import APIRouter, Depends, HTTPException, Query, Response, status
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.api.deps import get_current_user
from app.core.database import get_db
from app.models import DailyScore, User
from app.models.daily_score import (
    METRIC_AVG_HR,
    METRIC_SLEEP_SECONDS,
    METRIC_STEPS,
    SUPPORTED_METRICS,
)
from app.schemas.daily import DailyIngestRequest, DailyListOut, DailyOut
from app.services.challenge_service import capture_standings, notify_overtaken, utcnow
from app.services.daily_ingest import ManualEntryRejected, upsert_daily

logger = logging.getLogger("app.api.daily")

router = APIRouter(prefix="/daily", tags=["daily"])


def _to_out(rows: list[DailyScore]) -> DailyOut:
    by_metric = {r.metric: r for r in rows}
    steps_row = by_metric.get(METRIC_STEPS)
    first = rows[0]
    return DailyOut(
        date=first.date,
        tz_offset=first.tz_offset,
        steps=int(steps_row.value) if steps_row else 0,
        sleep_seconds=by_metric.get(METRIC_SLEEP_SECONDS).value if METRIC_SLEEP_SECONDS in by_metric else None,
        avg_hr=by_metric.get(METRIC_AVG_HR).value if METRIC_AVG_HR in by_metric else None,
        source_apps=first.source_apps or [],
        source=first.source,
        updated_at=first.updated_at,
    )


@router.post("", response_model=DailyOut)
def ingest_daily(
    payload: DailyIngestRequest,
    response: Response,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> DailyOut:
    """Upsert one local day of Health Connect metrics for the authenticated user.

    Repeat-safe: re-POSTing the same user+date updates in place (latest wins).
    Returns 201 on first ingest, 200 on an update of an existing day.
    Only source='health_connect' is accepted — manual entry has no API path.

    After a successful ingest, anyone the user just overtook in an active
    challenge gets a beat-you push (throttled 1/day/user; fire-and-forget).
    """
    try:
        before = capture_standings(db, current_user.id, utcnow())
        rows, created = upsert_daily(db, current_user.id, payload)
        notify_overtaken(db, before, current_user.id, utcnow())
    except ManualEntryRejected as exc:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail=str(exc)) from exc
    response.status_code = status.HTTP_201_CREATED if created else status.HTTP_200_OK
    return _to_out(rows)


@router.post("/ingest", response_model=DailyOut)
def ingest_daily_alias(
    payload: DailyIngestRequest,
    response: Response,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> DailyOut:
    return ingest_daily(payload, response, current_user, db)


@router.get("", response_model=DailyOut)
def get_daily(
    date: date | None = Query(default=None, description="Local date YYYY-MM-DD"),
    from_: date | None = Query(default=None, alias="from", description="Range start (inclusive)"),
    to: date | None = Query(default=None, description="Range end (inclusive)"),
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> DailyOut:
    """Read back one day's metrics (local date). Use ?from=&to= for ranges later."""
    if date is None:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail="Query param 'date' is required")

    rows = list(
        db.scalars(
            select(DailyScore)
            .where(DailyScore.user_id == current_user.id, DailyScore.date == date)
            .order_by(DailyScore.metric)
        )
    )
    if not rows:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="No daily record for that date")
    return _to_out(rows)


@router.get("/range", response_model=DailyListOut)
def get_daily_range(
    from_: date = Query(alias="from"),
    to: date = Query(),
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> DailyListOut:
    if from_ > to:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail="'from' must be <= 'to'")
    rows = list(
        db.scalars(
            select(DailyScore)
            .where(
                DailyScore.user_id == current_user.id,
                DailyScore.date >= from_,
                DailyScore.date <= to,
            )
            .order_by(DailyScore.date, DailyScore.metric)
        )
    )
    # group rows by date into DailyOut
    by_date: dict[date, list[DailyScore]] = {}
    for r in rows:
        by_date.setdefault(r.date, []).append(r)
    return DailyListOut(items=[_to_out(by_date[d]) for d in sorted(by_date)])
