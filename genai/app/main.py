import asyncio
import os
from concurrent.futures import ProcessPoolExecutor
from contextlib import asynccontextmanager
from datetime import datetime, UTC
from typing import List

import aio_pika
from aio_pika import connect_robust, RobustChannel, RobustConnection
from fastapi import FastAPI, Query, Body, HTTPException, Path
from fastapi import status
from pydantic import UUID4
from sqlalchemy import select, update

from app import weaviate_client as wc
from app.db import async_session, Summary, Message
from app.db import init_db
from app.langchain_provider import summarize_entries, answer_question
from app.models import ContentEntry
from app.queue_consumer import consume
from dotenv import load_dotenv

load_dotenv()

RABBIT_URL = (f"{os.getenv('RABBITMQ_URL', 'amqp://guest:guest@rabbitmq/')}")
QUEUE_NAME = "content_queue"

rabbit_connection: RobustConnection | None = None
rabbit_channel: RobustChannel | None = None

executor = ProcessPoolExecutor()


@asynccontextmanager
async def lifespan(app: FastAPI):
    await init_db()
    wc.init_collection()
    task = asyncio.create_task(consume())
    global rabbit_connection, rabbit_channel
    rabbit_connection = await connect_robust(RABBIT_URL)
    rabbit_channel = await rabbit_connection.channel()
    await rabbit_channel.set_qos(prefetch_count=100)
    yield
    task.cancel()
    wc.client.close()


app = FastAPI(lifespan=lifespan)


@app.post(
    "/content",
    summary="Post content to the queue for processing"
)
async def post_content(
        entries: List[ContentEntry] = Body(..., description="List of content entries to be processed")
):
    if rabbit_channel is None:
        raise HTTPException(status_code=500, detail="RabbitMQ not initialized")

    # Validate and publish in parallel
    async def publish(entry):
        if not entry.metadata.projectId or not entry.metadata.timestamp:
            raise HTTPException(
                status_code=422,
                detail="Each entry must have a projectId and timestamp."
            )
        raw = entry.model_dump_json()
        message = aio_pika.Message(body=raw.encode(), delivery_mode=2)  # persistent
        await rabbit_channel.default_exchange.publish(
            message,
            routing_key=QUEUE_NAME
        )

    # Use gather with fail-fast to process all entries concurrently
    await asyncio.gather(*(publish(e) for e in entries))

    return {"status": f"Published {len(entries)} message(s) to queue."}


@app.post("/project/{projectId}/summary", summary="Get a summary of content entries")
async def get_summary(
        projectId: UUID4 = Path(..., description="Project UUID (must be UUID4)"),
        startTime: int = Query(-1, ge=-1, description="Start UNIX timestamp (>=-1)"),
        endTime: int = Query(-1, ge=-1, description="End   UNIX timestamp (>=-1)"),
        userIds: List[UUID4] = Query([], description="Optional list of user UUIDs to make the LLM focus on")
):
    if startTime > endTime:
        raise HTTPException(
            status_code=422,
            detail="startTime must be ≤ endTime",
        )

    async with async_session() as session:
        result = await session.execute(
            select(Summary).where(
                Summary.projectId == str(projectId) and
                Summary.startTime == startTime and
                Summary.endTime == endTime
            )
        )

        summaries = result.scalars().all()

        input_user_ids = sorted([str(uid) for uid in userIds])

        existing_summary = next(
            (s for s in summaries if s.userIds == input_user_ids),
            None
        )

        if existing_summary:
            print("Existing summary found, returning it...")
            summary = {
                "projectId": existing_summary.projectId,
                "startTime": existing_summary.startTime,
                "endTime": existing_summary.endTime,
                "userIds": existing_summary.userIds,
                "generatedAt": existing_summary.generatedAt.isoformat(),
                "output_text": existing_summary.summary,
            }
            return summary

        print("No existing summary found, generating new one...")
        # Generate and store new summary

        placeholder = Summary(
            projectId=str(projectId),
            startTime=startTime,
            endTime=endTime,
            userIds=input_user_ids,
            loading=True,
            generatedAt=datetime.now(UTC),
            summary=""
        )
        session.add(placeholder)
        await session.commit()
        await session.refresh(placeholder)

        loop = asyncio.get_running_loop()
        loop.run_in_executor(
            executor,
            _blocking_summary_job,
            placeholder.id,
            placeholder.projectId,
            placeholder.startTime,
            placeholder.endTime,
            placeholder.userIds
        )

    return {
        "id": placeholder.id,
        "projectId": placeholder.projectId,
        "startTime": placeholder.startTime,
        "endTime": placeholder.endTime,
        "userIds": placeholder.userIds,
        "generatedAt": placeholder.generatedAt,
        "loading": placeholder.loading,
        "summary": ""
    }


@app.put("/project/{projectId}/summary", summary="Regenerate and overwrite summary for given time frame",
         status_code=status.HTTP_201_CREATED)
async def refresh_summary(
        projectId: UUID4 = Path(..., description="Project UUID (must be UUID4)"),
        startTime: int = Query(-1, ge=-1, description="Start UNIX timestamp (>=1)"),
        endTime: int = Query(-1, ge=-1, description="End UNIX timestamp (>=1"),
        userIds: List[UUID4] = Query([], description="Optional list of user UUIDs to make the LLM focus on")
):
    if startTime > endTime:
        raise HTTPException(
            status_code=422,
            detail="startTime must be ≤ endTime",
        )

    async with async_session() as session:
        # Delete any existing summary for this time frame
        result = await session.execute(
            select(Summary).where(
                Summary.projectId == str(projectId) and
                Summary.startTime == startTime and
                Summary.endTime == endTime
            )
        )
        summaries = result.scalars().all()

        input_user_ids = sorted([str(uid) for uid in userIds])

        summary_to_delete = next(
            (s for s in summaries if sorted(s.userIds) == input_user_ids),
            None
        )

        if summary_to_delete:
            await session.delete(summary_to_delete)
            await session.commit()

        placeholder = Summary(
            projectId=str(projectId),
            startTime=startTime,
            endTime=endTime,
            userIds=input_user_ids,
            loading=True,
            generatedAt=datetime.now(UTC),
            summary=""
        )
        session.add(placeholder)
        await session.commit()
        await session.refresh(placeholder)

        loop = asyncio.get_running_loop()
        loop.run_in_executor(
            executor,
            _blocking_summary_job,
            placeholder.id,
            placeholder.projectId,
            placeholder.startTime,
            placeholder.endTime,
            placeholder.userIds
        )

    return {
        "id": placeholder.id,
        "projectId": placeholder.projectId,
        "startTime": placeholder.startTime,
        "endTime": placeholder.endTime,
        "userIds": placeholder.userIds,
        "generatedAt": placeholder.generatedAt,
        "loading": placeholder.loading,
        "summary": ""
    }


@app.get("/project/{projectId}/summary", summary="Get all summaries for a project")
async def get_summaries(
        projectId: UUID4 = Path(..., description="Project UUID (must be UUID4)")
):
    async with async_session() as session:
        result = await session.execute(
            select(Summary).where(Summary.projectId == str(projectId))
        )
        summaries = result.scalars().all()

    return [
        {
            "id": s.id,
            "projectId": s.projectId,
            "startTime": s.startTime,
            "endTime": s.endTime,
            "userIds": s.userIds,
            "generatedAt": s.generatedAt.isoformat(),
            "loading": s.loading,
            "summary": s.summary,
        }
        for s in summaries
    ]


@app.post("/project/{projectId}/messages", summary="Query the project for answers")
async def query_project(
        projectId: UUID4 = Path(..., description="Project UUID (must be UUID4)"),
        userId: UUID4 = Query(..., description="User UUID (must be UUID4)"),
        question: str = Body(..., description="Question to ask about the project content")
):
    if not question or len(question.strip()) == 0:
        raise HTTPException(
            status_code=422,
            detail="Question cannot be empty."
        )
    async with async_session() as session:
        question_obj = Message(
            userId=str(userId),
            projectId=str(projectId),
            content=question.strip(),
            timestamp=datetime.now(UTC),
            loading=False
        )

        answer_placeholder = Message(
            userId=str(userId),
            projectId=str(projectId),
            timestamp=datetime.now(UTC),
            content="",
            loading=True
        )

        session.add(question_obj)
        session.add(answer_placeholder)
        await session.commit()
        await session.refresh(answer_placeholder)

        loop = asyncio.get_running_loop()
        loop.run_in_executor(
            executor,
            _blocking_qa_job,
            answer_placeholder.id,
            answer_placeholder.projectId,
            question.strip()
        )

    return {
        "id": answer_placeholder.id,
        "userId": answer_placeholder.userId,
        "projectId": answer_placeholder.projectId,
        "timestamp": answer_placeholder.timestamp.isoformat(),
        "content": answer_placeholder.content,
        "loading": answer_placeholder.loading
    }


@app.get("/project/{projectId}/messages", summary="Get Q&A history for a user")
async def get_chat_history(
        projectId: UUID4 = Path(..., description="Optional Project UUID"),
        userId: UUID4 = Query(..., description="User UUID (must be UUID4)")
):
    async with async_session() as session:
        query = select(Message).where(Message.projectId == str(projectId) and Message.userId == str(userId))
        result = await session.execute(query)
        history = result.scalars().all()
    return [
        {
            "id": entry.id,
            "userId": entry.userId,
            "projectId": entry.projectId,
            "timestamp": entry.timestamp.isoformat(),
            "content": entry.content,
            "loading": entry.loading
        }
        for entry in history
    ]


def _blocking_summary_job(summary_id: int,
                          project_id: str,
                          start_time: int,
                          end_time: int,
                          user_ids: list[str]):
    async def _do_update():
        summary_md = summarize_entries(project_id, start_time, end_time, user_ids)

        async with async_session() as session:
            stmt = (
                update(Summary)
                .where(Summary.id == summary_id)
                .values(
                    summary=(summary_md or {}).get("output_text", "Error generating summary"),
                    generatedAt=datetime.now(UTC),
                    loading=False
                )
            )
            await session.execute(stmt)
            await session.commit()

    asyncio.run(_do_update())


def _blocking_qa_job(qa_id: int,
                     project_id: str,
                     question: str):
    async def _do_update():
        answer_md = answer_question(project_id, question)

        if not answer_md or "result" not in answer_md:
            answer_md = {"result": "Error generating answer. No content found."}

        async with async_session() as session:
            stmt = (
                update(Message)
                .where(Message.id == qa_id)
                .values(
                    content=answer_md["result"],
                    loading=False,
                    timestamp=datetime.now(UTC)
                )
            )
            await session.execute(stmt)
            await session.commit()

    asyncio.run(_do_update())
