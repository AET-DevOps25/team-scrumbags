import weaviate
from weaviate.collections import Collection
from weaviate.util import generate_uuid5
from datetime import datetime, timezone
from typing import List
from weaviate.collections.classes.filters import Filter

import weaviate.classes.config as wc
# Init v4 client
client = weaviate.connect_to_local(
    host="weaviate",  # Use a string to specify the host
    port=8080,
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
                    wc.Property(name="timestamp", data_type=wc.DataType.INT),
                    wc.Property(name="projectId", data_type=wc.DataType.UUID),
                    wc.Property(name="content", data_type=wc.DataType.TEXT),
                ]
            )

def store_entry(entry):
    collection: Collection = client.collections.get(COLLECTION_NAME)
    content_uuid = generate_uuid5(str(entry.projectId) + str(entry.timestamp))
    # Convert UNIX timestamp to ISO 8601 with timezone
    dt = datetime.fromtimestamp(entry.timestamp, tz=timezone.utc)

    entry_obj = {
        "type": entry.metadata.type,
        "user": str(entry.metadata.user),
        "timestamp": dt,
        "projectId": str(entry.metadata.projectId),
        "content": str(entry.content),  # serialize nested content
    }

    with collection.batch.fixed_size(batch_size=10) as batch:
        batch.add_object(
            properties=entry_obj,
            uuid=content_uuid
        )

    if len(collection.batch.failed_objects) > 0:
        print(f"Failed to import {len(collection.batch.failed_objects)} objects")

def get_entries(project_id: str, start: int, end: int) -> List[str]:
    collection: Collection = client.collections.get(COLLECTION_NAME)
    start_dt = datetime.fromtimestamp(start, tz=timezone.utc).isoformat()
    end_dt = datetime.fromtimestamp(end, tz=timezone.utc).isoformat()
    filter_obj = Filter.by_property("projectId").equal(project_id) & \
                 Filter.by_property("timestamp").greater_or_equal(start_dt) & \
                 Filter.by_property("timestamp").less_or_equal(end_dt)

    results = collection.query.bm25(
        query="",
        filters=filter_obj,
        limit=100
    )

    return [obj.properties["content"] for obj in results.objects]