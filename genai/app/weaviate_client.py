from datetime import datetime, timezone

import weaviate
import weaviate.classes.config as wc
from weaviate.collections.classes.filters import Filter
from weaviate.util import generate_uuid5

from app.models import ContentEntry

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


def store_entry(entry):
    collection = client.collections.get(COLLECTION_NAME)
    entry = ContentEntry(**entry)  # Validate and convert to ContentEntry model
    print(str(entry.metadata.projectId) + str(entry.metadata.timestamp))
    # Generate a UUID5 based on projectId and hash of content
    content_uuid = generate_uuid5(str(entry.metadata.projectId) + str(entry.metadata.timestamp) + str(entry.content))
    # Convert UNIX timestamp to ISO 8601 with timezone
    dt = datetime.fromtimestamp(entry.metadata.timestamp, tz=timezone.utc)

    entry.metadata.user = None if entry.metadata.user in [None, "None", "null"] else entry.metadata.user
    entry.metadata.type = None if entry.metadata.type in [None, "None", "null"] else entry.metadata.type

    entry_obj = {
        # "uuid": str(content_uuid),
        "type": entry.metadata.type,
        "user": entry.metadata.user,
        "timestamp": dt.isoformat(),
        "projectId": str(entry.metadata.projectId),
        "content": str(entry.content)  # serialize nested content
    }

    print("Adding object to collection: ", entry_obj)

    with collection.batch.fixed_size(batch_size=200) as batch:
        batch.add_object(
            properties=entry_obj,
            uuid=content_uuid
        )

    print("Failed objects: ", collection.batch.failed_objects)

    print(collection.batch)

    if len(collection.batch.failed_objects) > 0:
        print(f"Failed to import {len(collection.batch.failed_objects)} objects")


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
