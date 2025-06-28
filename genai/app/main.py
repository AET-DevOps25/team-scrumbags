import asyncio
from contextlib import asynccontextmanager
from fastapi import FastAPI, Query, Body, HTTPException
from aio_pika import connect_robust, Message
from app.models import ContentEntry
from app.langchain_provider import summarize_entries, answer_question
from app import weaviate_client as wc
from app.queue_consumer import consume
from pydantic import UUID4
from typing import List

RABBIT_URL = "amqp://guest:guest@rabbitmq/"
QUEUE_NAME = "content_queue"

@asynccontextmanager
async def lifespan(app: FastAPI):
    wc.init_collection()
    task = asyncio.create_task(consume())
    print(task)
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
def get_summary(
    project_id: UUID4 = Query(..., description="Project UUID (must be UUID4)"),
    start_time: int = Query(..., ge=0, description="Start UNIX timestamp (>=0)"),
    end_time:   int = Query(..., ge=0, description="End   UNIX timestamp (>=0)")
):
    if start_time > end_time:
        raise HTTPException(
            status_code=422,
            detail="start_time must be ≤ end_time",
        )
    summary = summarize_entries(str(project_id), start_time, end_time)
    return {"summary": summary}

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
