import asyncio
import os
import time
from concurrent.futures import ThreadPoolExecutor
from contextlib import asynccontextmanager
from typing import List

import aio_pika
from aio_pika import connect_robust, RobustChannel, RobustConnection
from app import weaviate_client as wc
from app.db import async_session, Summary, Message
from app.db import init_db
from app.langchain_provider import summarize_entries, answer_question
from app.models import ContentEntry
from app.queue_consumer import consume
from dotenv import load_dotenv
from fastapi import FastAPI, Query, Body, HTTPException, Path
from fastapi import status
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from pydantic import UUID4
from sqlalchemy import select, update

load_dotenv()

RABBIT_URL = (f"{os.getenv('RABBITMQ_URL', 'amqp://guest:guest@rabbitmq/')}")
QUEUE_NAME = "content_queue"

rabbit_connection: RobustConnection | None = None
rabbit_channel: RobustChannel | None = None

executor = ThreadPoolExecutor()


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
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


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


@app.post("/projects/{projectId}/summary", summary="Get a summary of content entries")
async def generate_summary(
        projectId: UUID4 = Path(..., description="Project UUID (must be UUID4)"),
        startTime: int = Query(-1, ge=-1, description="Start UNIX timestamp (>=-1)"),
        endTime: int = Query(-1, ge=-1, description="End UNIX timestamp (>=-1)"),
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
                (Summary.projectId == str(projectId))
                & (Summary.startTime == startTime)
                & (Summary.endTime == endTime)
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
                "id": existing_summary.id,
                "projectId": existing_summary.projectId,
                "startTime": existing_summary.startTime,
                "endTime": existing_summary.endTime,
                "userIds": existing_summary.userIds,
                "generatedAt": existing_summary.generatedAt,
                "loading": existing_summary.loading,
                "summary": existing_summary.summary,
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
            generatedAt=int(time.time() * 1000),
            summary="",
        )
        session.add(placeholder)
        await session.commit()
        await session.refresh(placeholder)

        asyncio.create_task(_background_summary_task(placeholder.id, str(projectId), startTime, endTime, input_user_ids))

    return JSONResponse(
        status_code=202 if placeholder.loading else 200,
        content={
            "id": placeholder.id,
            "projectId": placeholder.projectId,
            "startTime": placeholder.startTime,
            "endTime": placeholder.endTime,
            "userIds": placeholder.userIds,
            "generatedAt": placeholder.generatedAt,
            "loading": placeholder.loading,
            "summary": "",
        },
    )


@app.put("/projects/{projectId}/summary", summary="Regenerate and overwrite summary for given time frame",
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
                (Summary.projectId == str(projectId))
                & (Summary.startTime == startTime)
                & (Summary.endTime == endTime)
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
            generatedAt=int(time.time() * 1000),
            summary="",
        )
        session.add(placeholder)
        await session.commit()
        await session.refresh(placeholder)

        asyncio.create_task(_background_summary_task(placeholder.id, str(projectId), startTime, endTime, input_user_ids))

    return {
        "id": placeholder.id,
        "projectId": placeholder.projectId,
        "startTime": placeholder.startTime,
        "endTime": placeholder.endTime,
        "userIds": placeholder.userIds,
        "generatedAt": placeholder.generatedAt,
        "loading": placeholder.loading,
        "summary": placeholder.summary,
    }


@app.get("/projects/{projectId}/summary", summary="Get all summaries for a project")
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
            "generatedAt": s.generatedAt,
            "loading": s.loading,
            "summary": s.summary,
        }
        for s in summaries
    ]


@app.get("/projects/{projectId}/summary/{summaryId}", summary="Get a summary by it's id")
async def get_summary_by_id(
        projectId: UUID4 = Path(..., description="Project UUID (must be UUID4)"),
        summaryId: int = Path(..., description="Summary ID")
):
    async with async_session() as session:
        result = await session.execute(
            select(Summary).where(
                (Summary.projectId == str(projectId)) & (Summary.id == summaryId)
            )
        )
        summary = result.scalar_one_or_none()

    if not summary:
        raise HTTPException(
            status_code=404,
            detail=f"Summary with ID {summaryId} not found for project {projectId}.",
        )

    return JSONResponse(
        status_code=202 if summary.loading else 200,
        content={
            "id": summary.id,
            "projectId": summary.projectId,
            "startTime": summary.startTime,
            "endTime": summary.endTime,
            "userIds": summary.userIds,
            "generatedAt": summary.generatedAt,
            "loading": summary.loading,
            "summary": summary.summary,
        },
    )


@app.post("/projects/{projectId}/messages", summary="Query the project for answers")
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
            isGenerated=False,
            projectId=str(projectId),
            content=question.strip(),
            timestamp=int(time.time() * 1000),  # Store as milliseconds
            loading=False,
        )

        answer_placeholder = Message(
            userId=str(userId),
            isGenerated=True,
            projectId=str(projectId),
            timestamp=(int(time.time() * 1000) + 1000),  # Store as milliseconds + 1 second,
            content="",
            loading=True,
        )

        session.add(question_obj)
        session.add(answer_placeholder)
        await session.commit()
        await session.refresh(answer_placeholder)

        # Use async task instead of executor
        asyncio.create_task(_background_qa_task(
            answer_placeholder.id,
            answer_placeholder.projectId,
            question.strip()
        ))

    return [
        {
            "id": question_obj.id,
            "userId": question_obj.userId,
            "isGenerated": question_obj.isGenerated,
            "projectId": question_obj.projectId,
            "timestamp": question_obj.timestamp,
            "content": question_obj.content,
            "loading": question_obj.loading,
        },
        {
            "id": answer_placeholder.id,
            "userId": answer_placeholder.userId,
            "isGenerated": answer_placeholder.isGenerated,
            "projectId": answer_placeholder.projectId,
            "timestamp": answer_placeholder.timestamp,
            "content": answer_placeholder.content,
            "loading": answer_placeholder.loading,
        },
    ]


@app.get("/projects/{projectId}/messages", summary="Get Q&A history for a user")
async def get_chat_history(
        projectId: UUID4 = Path(..., description="Optional Project UUID"),
        userId: UUID4 = Query(..., description="User UUID (must be UUID4)")
):
    async with async_session() as session:
        query = select(Message).where(
            (Message.projectId == str(projectId)) & (Message.userId == str(userId))
        )
        result = await session.execute(query)
        history = result.scalars().all()
    return [
        {
            "id": entry.id,
            "userId": entry.userId,
            "isGenerated": entry.isGenerated,
            "projectId": entry.projectId,
            "timestamp": entry.timestamp,
            "content": entry.content,
            "loading": entry.loading,
        }
        for entry in history
    ]


async def _background_summary_task(summary_id: int, project_id: str, start_time: int, end_time: int,
                                   user_ids: list[str]):
    """Generate summary in background"""
    try:
        # Get entries from Weaviate
        entries = wc.get_entries(project_id, start_time, end_time, user_ids)

        # Generate summary using LangChain
        summary_md = await summarize_entries(entries)

        # Update database with the generated summary
        async with async_session() as session:
            stmt = (
                update(Summary)
                .where(Summary.id == summary_id)
                .values(
                    summary=(summary_md or {}).get("output_text", "Error generating summary"),
                    generatedAt=int(time.time() * 1000),
                    loading=False,
                )
            )
            await session.execute(stmt)
            await session.commit()
    except Exception as e:
        # Handle errors by updating the summary with error state
        async with async_session() as session:
            stmt = (
                update(Summary)
                .where(Summary.id == summary_id)
                .values(
                    summary=f"Error generating summary: {str(e)}",
                    generatedAt=int(time.time() * 1000),
                    loading=False,
                )
            )
            await session.execute(stmt)
            await session.commit()


async def _background_qa_task(message_id: int, project_id: str, question: str):
    """Generate answer in background"""
    try:
        # Get entries from Weaviate for context
        entries = wc.get_entries(project_id, -1, -1, [])  # Get all entries for context

        # Generate answer using LangChain
        answer_result = await answer_question(question, entries)

        # Update database with the generated answer
        async with async_session() as session:
            stmt = (
                update(Message)
                .where(Message.id == message_id)
                .values(
                    content=(answer_result or {}).get("result", "Error generating answer"),
                    loading=False,
                )
            )
            await session.execute(stmt)
            await session.commit()
    except Exception as e:
        # Handle errors by updating the message with error state
        async with async_session() as session:
            stmt = (
                update(Message)
                .where(Message.id == message_id)
                .values(
                    content=f"Error generating answer: {str(e)}",
                    loading=False,
                )
            )
            await session.execute(stmt)
            await session.commit()
