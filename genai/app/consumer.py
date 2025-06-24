import asyncio
import json
import os
import aio_pika
import weaviate
from .models import WeaviateClientSingleton

RABBIT_URL = os.getenv('RABBIT_URL', 'amqp://guest:guest@rabbitmq/')
QUEUE_NAME = 'items'

async def handle_message(message: aio_pika.IncomingMessage):
    async with message.process():
        payload = json.loads(message.body)
        # Insert into Weaviate
        client = WeaviateClientSingleton.get()
        client.data_object.create(
            data_object={
                **payload['metadata'],
                'content': payload['content']
            },
            class_name='MicroContent'
        )
        print('Stored object', payload['metadata']['projectId'])

async def main():
    connection = await aio_pika.connect_robust(RABBIT_URL)
    channel = await connection.channel()
    queue = await channel.declare_queue(QUEUE_NAME, durable=True)
    await queue.consume(handle_message)
    print('Consumer started...')
    await asyncio.Future()

if __name__ == '__main__':
    asyncio.run(main())