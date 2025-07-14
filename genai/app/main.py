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
from fastapi.openapi.utils import get_openapi

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


def custom_openapi():
    if app.openapi_schema:
        return app.openapi_schema

    openapi_schema = get_openapi(
        title="GenAI Content Processing API",
        version="1.0.0",
        description="""
        A FastAPI application for processing content entries, generating summaries, and answering questions using AI.

        ## Features

        * **Content Processing**: Submit content entries to a queue for asynchronous processing
        * **Summary Generation**: Generate AI-powered summaries of content for specific time periods
        * **Q&A System**: Ask questions about project content and get AI-generated answers
        * **Chat History**: Retrieve conversation history for users

        ## Usage

        1. Post content entries using `/content` endpoint
        2. Generate summaries using `/projects/{projectId}/summary` endpoints
        3. Query content using `/projects/{projectId}/messages` endpoints
        """,
        routes=app.routes,
        servers=[
            {"url": "http://localhost:8000", "description": "Development server"},
            {"url": "https://api.example.com", "description": "Production server"}
        ]
    )

    # Add tags metadata
    openapi_schema["tags"] = [
        {
            "name": "Content",
            "description": "Operations for submitting and processing content entries"
        },
        {
            "name": "Summaries",
            "description": "Operations for generating and managing content summaries"
        },
        {
            "name": "Messages",
            "description": "Operations for Q&A and chat functionality"
        }
    ]

    app.openapi_schema = openapi_schema
    return app.openapi_schema


app.openapi = custom_openapi


@app.post(
    "/content",
    summary="Submit content entries for processing",
    description="Submit a list of content entries to the RabbitMQ queue for asynchronous processing",
    response_description="Confirmation of successful submission",
    tags=["Content"],
    responses={
        200: {
            "description": "Content successfully submitted to queue",
            "content": {
                "application/json": {
                    "example": {"status": "Published 3 message(s) to queue."}
                }
            }
        },
        422: {
            "description": "Validation error - missing projectId or timestamp",
            "content": {
                "application/json": {
                    "example": {"detail": "Each entry must have a projectId and timestamp."}
                }
            }
        },
        500: {
            "description": "RabbitMQ connection error",
            "content": {
                "application/json": {
                    "example": {"detail": "RabbitMQ not initialized"}
                }
            }
        }
    }
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


@app.post(
    "/projects/{projectId}/summary",
    summary="Generate content summary",
    description="Generate an AI-powered summary of content entries for a specific project and time period",
    response_description="Summary object with generation status",
    tags=["Summaries"],
    responses={
        200: {
            "description": "Existing summary found and returned",
            "content": {
                "application/json": {
                    "example": {
                        "id": 1,
                        "projectId": "123e4567-e89b-12d3-a456-426614174000",
                        "startTime": 1640995200000,
                        "endTime": 1641081600000,
                        "userIds": [],
                        "generatedAt": 1641081600000,
                        "loading": False,
                        "summary": "Project summary content..."
                    }
                }
            }
        },
        202: {
            "description": "New summary generation started",
            "content": {
                "application/json": {
                    "example": {
                        "id": 2,
                        "projectId": "123e4567-e89b-12d3-a456-426614174000",
                        "startTime": 1640995200000,
                        "endTime": 1641081600000,
                        "userIds": [],
                        "generatedAt": 1641081600000,
                        "loading": True,
                        "summary": ""
                    }
                }
            }
        },
        422: {
            "description": "Invalid time range",
            "content": {
                "application/json": {
                    "example": {"detail": "startTime must be ≤ endTime"}
                }
            }
        }
    }
)
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


@app.put(
    "/projects/{projectId}/summary",
    summary="Regenerate content summary",
    description="Force regeneration of summary by deleting existing one and creating new",
    response_description="New summary object with loading status",
    tags=["Summaries"],
    status_code=status.HTTP_201_CREATED,
    responses={
        201: {
            "description": "Summary regeneration started",
            "content": {
                "application/json": {
                    "example": {
                        "id": 3,
                        "projectId": "123e4567-e89b-12d3-a456-426614174000",
                        "startTime": 1640995200000,
                        "endTime": 1641081600000,
                        "userIds": [],
                        "generatedAt": 1641081600000,
                        "loading": True,
                        "summary": ""
                    }
                }
            }
        }
    }
)
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


@app.get(
    "/projects/{projectId}/summary",
    summary="Get all project summaries",
    description="Retrieve all summaries generated for a specific project",
    response_description="List of all summaries for the project",
    tags=["Summaries"],
    responses={
        200: {
            "description": "List of summaries",
            "content": {
                "application/json": {
                    "example": [
                        {
                            "id": 1,
                            "projectId": "123e4567-e89b-12d3-a456-426614174000",
                            "startTime": 1640995200000,
                            "endTime": 1641081600000,
                            "userIds": [],
                            "generatedAt": 1641081600000,
                            "loading": False,
                            "summary": "Project summary content..."
                        }
                    ]
                }
            }
        }
    }
)
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


@app.get(
    "/projects/{projectId}/summary/{summaryId}",
    summary="Get specific summary by ID",
    description="Retrieve a specific summary by its ID within a project",
    response_description="Summary object or loading status",
    tags=["Summaries"],
    responses={
        200: {
            "description": "Summary found and completed",
        },
        202: {
            "description": "Summary found but still generating",
        },
        404: {
            "description": "Summary not found",
            "content": {
                "application/json": {
                    "example": {"detail": "Summary with ID 1 not found for project 123e4567-e89b-12d3-a456-426614174000."}
                }
            }
        }
    }
)
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


@app.post(
    "/projects/{projectId}/messages",
    summary="Ask question about project",
    description="Submit a question about project content and receive an AI-generated answer",
    response_description="Question and answer message objects",
    tags=["Messages"],
    responses={
        200: {
            "description": "Question submitted and answer generation started",
            "content": {
                "application/json": {
                    "example": [
                        {
                            "id": 1,
                            "userId": "123e4567-e89b-12d3-a456-426614174000",
                            "isGenerated": False,
                            "projectId": "123e4567-e89b-12d3-a456-426614174000",
                            "timestamp": 1641081600000,
                            "content": "What is this project about?",
                            "loading": False
                        },
                        {
                            "id": 2,
                            "userId": "123e4567-e89b-12d3-a456-426614174000",
                            "isGenerated": True,
                            "projectId": "123e4567-e89b-12d3-a456-426614174000",
                            "timestamp": 1641081601000,
                            "content": "",
                            "loading": True
                        }
                    ]
                }
            }
        },
        422: {
            "description": "Empty question",
            "content": {
                "application/json": {
                    "example": {"detail": "Question cannot be empty."}
                }
            }
        }
    }
)
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


@app.get(
    "/projects/{projectId}/messages",
    summary="Get chat history",
    description="Retrieve the complete Q&A history for a user within a project",
    response_description="List of messages (questions and answers) ordered by timestamp",
    tags=["Messages"],
    responses={
        200: {
            "description": "Chat history retrieved successfully",
            "content": {
                "application/json": {
                    "example": [
                        {
                            "id": 1,
                            "userId": "123e4567-e89b-12d3-a456-426614174000",
                            "isGenerated": False,
                            "projectId": "123e4567-e89b-12d3-a456-426614174000",
                            "timestamp": 1641081600000,
                            "content": "What is this project about?",
                            "loading": False
                        },
                        {
                            "id": 2,
                            "userId": "123e4567-e89b-12d3-a456-426614174000",
                            "isGenerated": True,
                            "projectId": "123e4567-e89b-12d3-a456-426614174000",
                            "timestamp": 1641081601000,
                            "content": "This project focuses on...",
                            "loading": False
                        }
                    ]
                }
            }
        }
    }
)
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
