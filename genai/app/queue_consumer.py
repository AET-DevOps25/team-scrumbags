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
    max_retries = 5
    retry_delay = 10

    for attempt in range(max_retries):
        try:
            print(f"Queue consumer connecting to RabbitMQ (attempt {attempt + 1}/{max_retries})")

            # Single long-lived connection
            connection: AbstractRobustConnection = await connect_robust(RABBIT_URL)
            channel: AbstractRobustChannel = await connection.channel()
            await channel.set_qos(prefetch_count=10)
            queue = await channel.declare_queue(QUEUE_NAME, durable=True)

            print("Queue consumer connected successfully")

            async with queue.iterator() as queue_iter:
                print("Queue consumer listening for messages...")
                async for message in queue_iter:
                    asyncio.create_task(handle_message(message))

        except Exception as e:
            print(f"Queue consumer error (attempt {attempt + 1}/{max_retries}): {e}")
            if attempt < max_retries - 1:
                print(f"Retrying in {retry_delay} seconds...")
                await asyncio.sleep(retry_delay)
            else:
                print("Queue consumer failed after all retries")
                raise


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