import asyncio
import json
import os

from aio_pika import connect_robust
from aio_pika.abc import AbstractRobustConnection, AbstractRobustChannel, AbstractIncomingMessage
from dotenv import load_dotenv

from app.weaviate_client import store_entry_async

load_dotenv()
RABBIT_URL = os.getenv("RABBITMQ_URL", "amqp://guest:guest@rabbitmq/")
QUEUE_NAME = "content_queue"


async def consume():
    # Single long-lived connection
    connection: AbstractRobustConnection = await connect_robust(RABBIT_URL)
    channel: AbstractRobustChannel = await connection.channel()
    await channel.set_qos(prefetch_count=10)
    queue = await channel.declare_queue(QUEUE_NAME, durable=True)

    async with queue.iterator() as queue_iter:
        print("Queue iterator: " + str(queue_iter))
        async for message in queue_iter:
            asyncio.create_task(handle_message(message))


async def handle_message(message: AbstractIncomingMessage):
    async with message.process(requeue=False):
        try:
            payload = json.loads(message.body)
            meta = payload.get("metadata", {})
            if not meta.get("projectId") or not meta.get("timestamp"):
                print("Invalid message format, skipping...")
                return
            await store_entry_async(payload)
            print("Message processed successfully")
        except Exception as e:
            print(f"Error processing message: {e}")
