import asyncio
import json
from contextlib import asynccontextmanager

import uuid
from fastapi import FastAPI, Query, Body, HTTPException
from aio_pika import connect_robust, Message
from app.models import ContentEntry
from app.langchain_provider import summarize_entries, answer_question
from app import weaviate_client as wc
from app.queue_consumer import consume
from app.db import init_db
from pydantic import UUID4
from typing import List
from app.db import async_session, Summary
from sqlalchemy import delete, select
from fastapi import status
from datetime import datetime, UTC

RABBIT_URL = "amqp://guest:guest@rabbitmq/"
QUEUE_NAME = "content_queue"

@asynccontextmanager
async def lifespan(app: FastAPI):
    await init_db()
    wc.init_collection()
    task = asyncio.create_task(consume())
    yield
    task.cancel()
    wc.client.close()

app = FastAPI(lifespan=lifespan)

@app.post("/content", summary="Post content to the queue for processing")
async def post_content(
    entries: List[ContentEntry] = Body(..., description="List of content entries to be processed")
):
    conn = await connect_robust(RABBIT_URL)
    ch = await conn.channel()

    for entry in entries:
        raw = entry.model_dump_json()
        message = Message(body=raw.encode())
        await ch.default_exchange.publish(
            message,
            routing_key=QUEUE_NAME
        )

    await conn.close()
    return {"status": f"Published {len(entries)} message(s) to queue."}

@app.get("/summary/", summary="Get a summary of content entries")
async def get_summary(
    project_id: UUID4 = Query(..., description="Project UUID (must be UUID4)"),
    start_time: int = Query(..., ge=0, description="Start UNIX timestamp (>=0)"),
    end_time:   int = Query(..., ge=0, description="End   UNIX timestamp (>=0)")
):
    if start_time > end_time:
        raise HTTPException(
            status_code=422,
            detail="start_time must be ≤ end_time",
        )

    async with async_session() as session:
        result = await session.execute(
            select(Summary).where(
                Summary.project_id == str(project_id),
                Summary.start_time == start_time,
                Summary.end_time == end_time
            )
        )
        existing_summary = result.scalars().first()
        if existing_summary:
            print("Existing summary found, returning it...")
            summary_md = {
                "output_text": existing_summary.summary,
                "generated_at": existing_summary.generated_at.isoformat()
            }
            return {"summary": summary_md}

        print("No existing summary found, generating new one...")
        # Generate and store new summary
        summary_md = summarize_entries(str(project_id), start_time, end_time)

        new_summary = Summary(
            project_id=str(project_id),
            start_time=start_time,
            end_time=end_time,
            generated_at=datetime.now(UTC),
            summary=summary_md["output_text"]  # Assuming the summary is in this field
        )
        session.add(new_summary)
        await session.commit()

    return {"summary": summary_md}

@app.post("/summary/refresh/", summary="Regenerate and overwrite summary for given time frame",
          status_code=status.HTTP_201_CREATED)
async def refresh_summary(
    project_id: UUID4 = Query(..., description="Project UUID (must be UUID4)"),
    start_time: int = Query(..., ge=0, description="Start UNIX timestamp (>=0)"),
    end_time:   int = Query(..., ge=0, description="End   UNIX timestamp (>=0)")
):
    if start_time > end_time:
        raise HTTPException(
            status_code=422,
            detail="start_time must be ≤ end_time",
        )

    async with async_session() as session:
        # Delete any existing summary for this time frame
        await session.execute(
            delete(Summary).where(
                Summary.project_id == str(project_id),
                Summary.start_time == start_time,
                Summary.end_time == end_time
            )
        )

        # Generate and insert new summary
        summary_md = summarize_entries(str(project_id), start_time, end_time)
        new_summary = Summary(
            project_id=str(project_id),
            start_time=start_time,
            end_time=end_time,
            generated_at=datetime.now(UTC),
            summary=str(summary_md)
        )
        session.add(new_summary)
        await session.commit()

    return {"summary": summary_md}

@app.get("/summaries/", summary="Get all summaries for a project")
async def get_summaries(
    project_id: UUID4 = Query(..., description="Project UUID (must be UUID4)")
):
    async with async_session() as session:
        result = await session.execute(
            select(Summary).where(Summary.project_id == str(project_id))
        )
        summaries = result.scalars().all()

    return [
        {
            "start_time": s.start_time,
            "end_time": s.end_time,
            "generated_at": s.generated_at.isoformat(),
            "summary": s.summary_md,
        }
        for s in summaries
    ]

@app.post("/query/", summary="Query the project for answers")
def query_project(
    project_id: UUID4 = Query(..., description="Project UUID (must be UUID4)"),
    start_time: int = Query(..., ge=0, description="Start UNIX timestamp (>=0)"),
    end_time:   int = Query(..., ge=0, description="End   UNIX timestamp (>=0)"),
    question: str = Query(..., description="Question to ask about the project content")
):
    if start_time > end_time:
        raise HTTPException(
            status_code=422,
            detail="start_time must be ≤ end_time",
        )
    answer = answer_question(str(project_id), start_time, end_time, question)
    return {"answer": answer}
