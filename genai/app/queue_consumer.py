import json

from aio_pika import connect_robust

from app import weaviate_client as wc

RABBIT_URL = "amqp://guest:guest@rabbitmq/"
QUEUE_NAME = "content_queue"


async def consume():
    connection = await connect_robust(RABBIT_URL)
    channel = await connection.channel()
    queue = await channel.declare_queue(QUEUE_NAME, durable=True)

    print(f"Queue {QUEUE_NAME} is ready for consumption")

    async with queue.iterator() as queue_iter:
        async for message in queue_iter:
            async with message.process():
                print("Processing message...")
                payload = json.loads(message.body)
                wc.store_entry(payload)
                print("Message processed successfully")
