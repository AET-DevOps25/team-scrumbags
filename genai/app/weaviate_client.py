import asyncio
import os
from datetime import datetime, timezone
from typing import List

import weaviate
import weaviate.classes.config as wc
from dotenv import load_dotenv
from langchain_nomic import NomicEmbeddings
from weaviate.collections.classes.filters import Filter
from weaviate.util import generate_uuid5

load_dotenv()

WEAVIATE_URL = os.getenv("WEAVIATE_URL", "http://weaviate:6969/")
NOMIC_API_KEY = os.getenv("NOMIC_API_KEY", None)
COLLECTION_NAME = "ContentEntries"

# Initialize client and embeddings lazily
_client = None
_embeddings = None


def get_client():
    global _client
    if _client is None:
        url = WEAVIATE_URL.rstrip('/')
        if '://' in url:
            protocol, rest = url.split('://', 1)
        else:
            protocol, rest = 'http', url

        if ':' in rest and '/' not in rest.split(':')[-1]:
            host, port_str = rest.rsplit(':', 1)
            port = int(port_str)
        else:
            host = rest
            port = 8080 if protocol == 'http' else 443

        _client = weaviate.connect_to_custom(
            http_host=host,
            http_port=port,
            http_secure=protocol == 'https',
            grpc_host=host,
            grpc_port=50051,
            grpc_secure=protocol == 'https',
        )
    return _client


def get_embeddings():
    global _embeddings
    if _embeddings is None:
        _embeddings = NomicEmbeddings(
            model="nomic-embed-text-v1.5",
            dimensionality=256,
            nomic_api_key=NOMIC_API_KEY
        )
    return _embeddings


def init_collection():
    client = get_client()
    if COLLECTION_NAME not in [c.name for c in client.collections.list_all().values()]:
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
    print(f"Collection {COLLECTION_NAME} created successfully")


async def store_entry_async(entry: dict):
    try:
        await asyncio.to_thread(store_entry, entry)
    except Exception as e:
        print(e)


def store_entry(entry: dict):
    try:
        client = get_client()

        # Expecting timestamp in milliseconds
        ts_millis = entry["metadata"]["timestamp"]
        ts_seconds = ts_millis / 1000.0

        entry["content"]["userId"] = entry["metadata"]["user"]
        entry["content"]["contentType"] = entry["metadata"]["type"]
        entry["content"]["contentTimestamp"] = datetime.fromtimestamp(ts_seconds,
                                                                      tz=timezone.utc).isoformat()

        content_text = str(entry["content"])
        vector = get_embeddings().embed_documents([content_text])[0]

        metadata = entry["metadata"]

        entry_obj = {
            "type": metadata.get("type") if metadata.get("type") not in [None, "None", "null"] else None,
            "user": metadata.get("user") if metadata.get("user") not in [None, "None", "null"] else None,
            "timestamp": datetime.fromtimestamp(ts_seconds, tz=timezone.utc).isoformat(),
            "projectId": str(metadata["projectId"]),
            "content": content_text,
        }

        content_uuid = generate_uuid5(f"{metadata['projectId']}{ts_millis}{content_text}")

        try:
            collection = client.collections.get(COLLECTION_NAME)
            with collection.batch.fixed_size(batch_size=1) as batch:
                batch.add_object(properties=entry_obj, uuid=content_uuid, vector=vector)
            # print(f"Stored entry with UUID {content_uuid} in collection {COLLECTION_NAME}")
        except weaviate.exceptions as e:
            print(f"Failed to store entry: {e}")

    except Exception as e:
        print(f"Error storing entry in Weaviate: {e}")


def get_entries(projectId: str, start: int, end: int) -> List:
    try:
        client = get_client()
        collection = client.collections.get(COLLECTION_NAME)

        if start == -1:
            start = 0
        if end == -1:
            end = int(datetime.now(timezone.utc).timestamp() * 1000)

        # Convert milliseconds to datetime
        start_dt = datetime.fromtimestamp(start / 1000.0, tz=timezone.utc).isoformat()
        end_dt = datetime.fromtimestamp(end / 1000.0, tz=timezone.utc).isoformat()

        results = collection.query.fetch_objects(
            filters=(
                    Filter.by_property("projectId").equal(projectId) &
                    Filter.by_property("timestamp").greater_or_equal(start_dt) &
                    Filter.by_property("timestamp").less_or_equal(end_dt)
            ),
            limit=150
        )

        print(f"Found {len(results.objects)} objects in collection {COLLECTION_NAME}")
        return results.objects

    except Exception as e:
        print(f"Error retrieving entries from Weaviate: {e}")
        return []


# For backward compatibility
client = get_client()
