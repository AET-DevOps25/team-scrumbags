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
from pydantic import UUID4, BaseModel
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


# Response models for better Swagger documentation
class ContentResponse(BaseModel):
    status: str

class SummaryResponse(BaseModel):
    id: int
    projectId: str
    startTime: int
    endTime: int
    userIds: List[str]
    generatedAt: str
    loading: bool
    summary: str

class MessageResponse(BaseModel):
    id: int
    userId: str
    projectId: str
    timestamp: str
    content: str
    loading: bool

class QuestionRequest(BaseModel):
    question: str


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


app = FastAPI(
    title="Content Processing and Q&A API",
    description="API for processing content entries, generating summaries, and answering questions about project data using AI",
    version="1.0.0",
    lifespan=lifespan
)


@app.post(
    "/content",
    summary="Submit content entries for processing",
    description="Publishes a list of content entries to the processing queue. Each entry must include projectId and timestamp metadata.",
    response_model=ContentResponse,
    responses={
        200: {"description": "Content entries successfully queued for processing"},
        422: {"description": "Validation error - missing required fields"},
        500: {"description": "Internal server error - RabbitMQ not available"}
    },
    tags=["Content Processing"]
)
async def post_content(
        entries: List[ContentEntry] = Body(
            ...,
            description="List of content entries to be processed",
            example=[{
                "content": "Sample content text",
                "metadata": {
                    "projectId": "123e4567-e89b-12d3-a456-426614174000",
                    "timestamp": 1640995200
                }
            }]
        )
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


@app.post(
    "/project/{projectId}/summary",
    summary="Generate or retrieve project summary",
    description="Generates an AI summary of content entries for a project within a specified time range. If a summary already exists for the same parameters, it returns the cached version. Otherwise, generates a new summary asynchronously.",
    response_model=SummaryResponse,
    responses={
        200: {"description": "Summary generated or retrieved successfully"},
        422: {"description": "Invalid time range or parameters"},
        500: {"description": "Internal server error"}
    },
    tags=["Summaries"]
)
async def get_summary(
        projectId: UUID4 = Path(..., description="Project UUID (must be UUID4)", example="123e4567-e89b-12d3-a456-426614174000"),
        startTime: int = Query(-1, ge=-1, description="Start UNIX timestamp. Use -1 for no start limit", example=1640995200),
        endTime: int = Query(-1, ge=-1, description="End UNIX timestamp. Use -1 for no end limit", example=1641081600),
        userIds: List[UUID4] = Query([], description="Optional list of user UUIDs to focus the summary on specific users", example=["456e7890-e12f-34a5-b678-526614174111"])
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


@app.put(
    "/project/{projectId}/summary",
    summary="Regenerate project summary",
    description="Forces regeneration of a project summary by deleting any existing summary for the specified parameters and creating a new one. The summary generation happens asynchronously.",
    response_model=SummaryResponse,
    status_code=status.HTTP_201_CREATED,
    responses={
        201: {"description": "Summary regeneration initiated successfully"},
        422: {"description": "Invalid time range or parameters"},
        500: {"description": "Internal server error"}
    },
    tags=["Summaries"]
)
async def refresh_summary(
        projectId: UUID4 = Path(..., description="Project UUID (must be UUID4)", example="123e4567-e89b-12d3-a456-426614174000"),
        startTime: int = Query(-1, ge=-1, description="Start UNIX timestamp. Use -1 for no start limit", example=1640995200),
        endTime: int = Query(-1, ge=-1, description="End UNIX timestamp. Use -1 for no end limit", example=1641081600),
        userIds: List[UUID4] = Query([], description="Optional list of user UUIDs to focus the summary on specific users", example=["456e7890-e12f-34a5-b678-526614174111"])
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


@app.get(
    "/project/{projectId}/summary",
    summary="Get all summaries for a project",
    description="Retrieves all existing summaries for a specific project, including their generation status and metadata.",
    response_model=List[SummaryResponse],
    responses={
        200: {"description": "List of summaries retrieved successfully"},
        404: {"description": "Project not found or no summaries exist"},
        500: {"description": "Internal server error"}
    },
    tags=["Summaries"]
)
async def get_summaries(
        projectId: UUID4 = Path(..., description="Project UUID (must be UUID4)", example="123e4567-e89b-12d3-a456-426614174000")
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


@app.post(
    "/project/{projectId}/messages",
    summary="Ask a question about project content",
    description="Submits a question about project content and generates an AI-powered answer based on the project's processed content. The answer generation happens asynchronously.",
    response_model=MessageResponse,
    responses={
        200: {"description": "Question submitted successfully, answer generation in progress"},
        422: {"description": "Invalid question format or empty question"},
        500: {"description": "Internal server error"}
    },
    tags=["Q&A"]
)
async def query_project(
        projectId: UUID4 = Path(..., description="Project UUID (must be UUID4)", example="123e4567-e89b-12d3-a456-426614174000"),
        userId: UUID4 = Query(..., description="User UUID who is asking the question (must be UUID4)", example="456e7890-e12f-34a5-b678-526614174111"),
        question: QuestionRequest = Body(..., description="Question data", example={"question": "What are the main topics discussed in this project?"})
):
    question = question.question
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


@app.get(
    "/project/{projectId}/messages",
    summary="Get chat history for a user",
    description="Retrieves the complete question and answer history for a specific user within a project, ordered by timestamp.",
    response_model=List[MessageResponse],
    responses={
        200: {"description": "Chat history retrieved successfully"},
        404: {"description": "No messages found for this user/project combination"},
        500: {"description": "Internal server error"}
    },
    tags=["Q&A"]
)
async def get_chat_history(
        projectId: UUID4 = Path(..., description="Project UUID (must be UUID4)", example="123e4567-e89b-12d3-a456-426614174000"),
        userId: UUID4 = Query(..., description="User UUID whose chat history to retrieve (must be UUID4)", example="456e7890-e12f-34a5-b678-526614174111")
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