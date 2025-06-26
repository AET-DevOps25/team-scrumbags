import json
import asyncio
from contextlib import asynccontextmanager
from fastapi import FastAPI
from aio_pika import connect_robust, Message
from .models import ContentEntry, QueryRequest
from .langchain_provider import summarize_entries, answer_question
from . import weaviate_client as wc
from .queue_consumer import consume

RABBIT_URL = "amqp://guest:guest@rabbitmq/"
QUEUE_NAME = "content_queue"

@asynccontextmanager
async def lifespan(app: FastAPI):
    wc.init_collection()
    task = asyncio.create_task(consume())
    yield
    task.cancel()
    wc.client.close()

app = FastAPI(lifespan=lifespan)

@app.post("/content")
async def post_content(entry: ContentEntry):
    conn = await connect_robust(RABBIT_URL)
    ch = await conn.channel()
    message = Message(body=json.dumps(entry.model_dump()).encode())
    await ch.default_exchange.publish(
        message,
        routing_key=QUEUE_NAME
    )
    await conn.close()
    return {"status": "queued"}


@app.get("/summary/")
def get_summary(project_id: str, start_time: int, end_time: int):
    return {"summary": summarize_entries(project_id, start_time, end_time)}

@app.post("/query/")
def query_project(req: QueryRequest):
    answer = answer_question(
        str(req.projectId), req.startTime, req.endTime, req.question
    )
    return {"answer": answer}