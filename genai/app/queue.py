import os
import aio_pika

RABBIT_URL = os.getenv('RABBIT_URL', 'amqp://guest:guest@rabbitmq/')

async def publish_message(queue_name: str, message_body: bytes):
    connection = await aio_pika.connect_robust(RABBIT_URL)
    async with connection:
        channel = await connection.channel()
        await channel.default_exchange.publish(
            aio_pika.Message(body=message_body),
            routing_key=queue_name,
        )