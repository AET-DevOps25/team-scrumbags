from fastapi import FastAPI, HTTPException
from .schemas import Item
from .queue import publish_message
import json

app = FastAPI()
QUEUE_NAME = 'items'

@app.post('/items/')
async def create_item(item: Item):
    try:
        body = json.dumps(item.dict()).encode()
        await publish_message(QUEUE_NAME, body)
        return {'status': 'queued'}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))