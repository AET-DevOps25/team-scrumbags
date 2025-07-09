import asyncio
import logging
import traceback
from datetime import datetime, timezone

import weaviate
import weaviate.classes.config as wc
from weaviate.collections.classes.filters import Filter
from weaviate.util import generate_uuid5

# Init v4 client
client = weaviate.connect_to_local(
    host="weaviate",  # Use a string to specify the host
    port=6969,
    grpc_port=50051,
)
COLLECTION_NAME = "ProjectContent"


def init_collection():
    if COLLECTION_NAME not in [c for c in client.collections.list_all()]:
        client.collections.create(
            name=COLLECTION_NAME,
            properties=[
                wc.Property(name="type", data_type=wc.DataType.TEXT),
                wc.Property(name="user", data_type=wc.DataType.UUID),
                wc.Property(name="timestamp", data_type=wc.DataType.DATE),
                wc.Property(name="projectId", data_type=wc.DataType.UUID),
                wc.Property(name="content", data_type=wc.DataType.TEXT),
            ]
        )


async def blocking_store(entry):
    # put blocking code here
    store_entry(entry)


logger = logging.getLogger(__name__)


async def store_entry_async(entry):
    try:
        await asyncio.to_thread(store_entry, entry)  # if store_entry is blocking
    except Exception as e:
        logger.error(e)
        traceback.print_exc()  # Full traceback for debugging


def store_entry(entry: dict):
    # Assume already validated ContentEntry upstream
    collection = client.collections.get(COLLECTION_NAME)

    # Efficient UUID5 generation
    content_uuid = generate_uuid5(
        f"{entry['metadata']['projectId']}{entry['metadata']['timestamp']}{entry['content']}"
    )

    # Normalize metadata
    metadata = entry["metadata"]
    entry_obj = {
        "type": metadata.get("type") if metadata.get("type") not in [None, "None", "null"] else None,
        "user": metadata.get("user") if metadata.get("user") not in [None, "None", "null"] else None,
        "timestamp": datetime.fromtimestamp(metadata["timestamp"], tz=timezone.utc).isoformat(),
        "projectId": str(metadata["projectId"]),
        "content": str(entry["content"]),
    }

    try:

        with collection.batch.fixed_size(batch_size=1) as batch:
            batch.add_object(
                properties=entry_obj,
                uuid=content_uuid
            )
        print(f"Stored entry with UUID {content_uuid} in collection {COLLECTION_NAME}")
    except weaviate.exceptions as e:
        logger.error(f"Failed to store entry: {e}")
        traceback.print_exc()


def get_entries(projectId: str, start: int, end: int):
    collection = client.collections.get(COLLECTION_NAME)

    if start == -1:
        start = 0
    if end == -1:
        end = int(datetime.now(timezone.utc).timestamp())

    start_dt = datetime.fromtimestamp(start, tz=timezone.utc).isoformat()
    end_dt = datetime.fromtimestamp(end, tz=timezone.utc).isoformat()

    results = collection.query.fetch_objects(
        filters=(
                Filter.by_property("projectId").equal(projectId) &
                Filter.by_property("timestamp").greater_or_equal(start_dt) &
                Filter.by_property("timestamp").less_or_equal(end_dt)
        ),
        limit=1000
    )

    print(f"Found {len(results.objects)} objects in collection {COLLECTION_NAME}")

    return results.objects
