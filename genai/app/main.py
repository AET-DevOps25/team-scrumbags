import asyncio
from contextlib import asynccontextmanager
from datetime import datetime, UTC
from typing import List

from aio_pika import connect_robust, Message, RobustChannel, RobustConnection
from fastapi import FastAPI, Query, Body, HTTPException
from fastapi import status
from pydantic import UUID4
from sqlalchemy import delete, select

from app import weaviate_client as wc
from app.db import async_session, Summary, QAPair
from app.db import init_db
from app.langchain_provider import summarize_entries, answer_question
from app.models import ContentEntry
from app.queue_consumer import consume

RABBIT_URL = "amqp://guest:guest@rabbitmq/"
QUEUE_NAME = "content_queue"

rabbit_connection: RobustConnection | None = None
rabbit_channel: RobustChannel | None = None


@asynccontextmanager
async def lifespan(app: FastAPI):
    await init_db()
    wc.init_collection()
    task = asyncio.create_task(consume())
    global rabbit_connection, rabbit_channel
    # Establish one robust connection and channel for the lifetime
    rabbit_connection = await connect_robust(RABBIT_URL)
    rabbit_channel = await rabbit_connection.channel()
    # Optionally configure publisher confirms
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
        message = Message(body=raw.encode(), delivery_mode=2)  # persistent
        await rabbit_channel.default_exchange.publish(
            message,
            routing_key=QUEUE_NAME
        )

    # Use gather with fail-fast to process all entries concurrently
    await asyncio.gather(*(publish(e) for e in entries))

    return {"status": f"Published {len(entries)} message(s) to queue."}


@app.get("/summary/", summary="Get a summary of content entries")
async def get_summary(
        projectId: UUID4 = Query(..., description="Project UUID (must be UUID4)"),
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
                Summary.projectId == str(projectId),
                Summary.startTime == startTime,
                Summary.endTime == endTime,
                Summary.userIds.contains([str(user) for user in userIds]) if userIds else False
            )
        )
        existing_summary = result.scalars().first()
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
            return {"summary": summary}

        print("No existing summary found, generating new one...")
        # Generate and store new summary
        summary_md = summarize_entries(str(projectId), startTime, endTime,
                                       [str(user) for user in userIds] if userIds else [])

        if not summary_md or "output_text" not in summary_md:
            return {"summary": "No content found for the given parameters."}

        new_summary = Summary(
            projectId=str(projectId),
            startTime=startTime,
            endTime=endTime,
            userIds=sorted([str(user) for user in userIds]) if userIds else [],
            generatedAt=datetime.now(UTC),
            summary=summary_md["output_text"]  # Assuming the summary is in this field
        )
        session.add(new_summary)
        await session.commit()

    return {"summary": summary_md}


@app.post("/summary/refresh/", summary="Regenerate and overwrite summary for given time frame",
          status_code=status.HTTP_201_CREATED)
async def refresh_summary(
        projectId: UUID4 = Query(..., description="Project UUID (must be UUID4)"),
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
        await session.execute(
            delete(Summary).where(
                Summary.projectId == str(projectId),
                Summary.startTime == startTime,
                Summary.endTime == endTime,
                Summary.userIds.contains([str(user) for user in userIds]) if userIds else True
            )
        )

        # Generate and insert new summary
        summary_md = summarize_entries(str(projectId), startTime, endTime,
                                       [str(user) for user in userIds] if userIds else [])
        new_summary = Summary(
            projectId=str(projectId),
            startTime=startTime,
            endTime=endTime,
            userIds=sorted([str(user) for user in userIds]) if userIds else [],
            generatedAt=datetime.now(UTC),
            summary=str(summary_md)
        )
        session.add(new_summary)
        await session.commit()

    return {"summary": summary_md}


@app.get("/summaries/", summary="Get all summaries for a project")
async def get_summaries(
        projectId: UUID4 = Query(..., description="Project UUID (must be UUID4)")
):
    async with async_session() as session:
        result = await session.execute(
            select(Summary).where(Summary.projectId == str(projectId))
        )
        summaries = result.scalars().all()

    return [
        {
            "projectId": s.projectId,
            "startTime": s.startTime,
            "endTime": s.endTime,
            "userIds": s.userIds,
            "generatedAt": s.generatedAt.isoformat(),
            "summary": s.summary,
        }
        for s in summaries
    ]


@app.post("/query/", summary="Query the project for answers")
async def query_project(
        userId: UUID4 = Query(..., description="User UUID (must be UUID4)"),
        projectId: UUID4 = Query(..., description="Project UUID (must be UUID4)"),
        question: str = Query(..., description="Question to ask about the project content")
):
    q_time = datetime.now(UTC)

    # Call the existing QA chain to get an answer
    answer = answer_question(str(projectId), question)

    a_time = datetime.now(UTC)

    # Save the Q&A pair to the database
    new_qapair = QAPair(
        projectId=str(projectId),
        userId=str(userId),
        question=question,
        answer=answer["result"],
        questionTime=q_time,
        answerTime=a_time
    )
    async with async_session() as session:
        session.add(new_qapair)
        await session.commit()

    return {"answer": answer}


@app.get("/chat_history/", summary="Get Q&A history for a user (optionally filtered by project)")
async def get_chat_history(
        userId: UUID4 = Query(..., description="User UUID (must be UUID4)"),
        projectId: UUID4 | None = Query(None, description="Optional Project UUID")
):
    async with async_session() as session:
        query = select(QAPair).where(QAPair.userId == str(userId))
        if projectId is not None:
            query = query.where(QAPair.projectId == str(projectId))
        result = await session.execute(query)
        history = result.scalars().all()
    return [
        {
            "userId": entry.userId,
            "projectId": entry.projectId,
            "question": entry.question,
            "answer": entry.answer,
            "questionTime": entry.questionTime.isoformat(),
            "answerTime": entry.answerTime.isoformat()
        }
        for entry in history
    ]
