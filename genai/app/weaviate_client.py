import os
import uuid
from datetime import datetime, timezone
from typing import List

import weaviate
from dotenv import load_dotenv
from langchain_nomic import NomicEmbeddings
from weaviate.classes.config import Configure

load_dotenv()

WEAVIATE_URL = os.getenv("WEAVIATE_URL", "http://weaviate:6969/")
NOMIC_API_KEY = os.getenv("NOMIC_API_KEY", None)
COLLECTION_NAME = "ContentEntries"

client = weaviate.connect_to_custom(
    http_host=WEAVIATE_URL.replace("http://", "").replace("https://", "").rstrip("/"),
    http_port=int(WEAVIATE_URL.split(":")[-1].rstrip("/")),
    http_secure=False,
    grpc_host=WEAVIATE_URL.replace("http://", "").replace("https://", "").rstrip("/"),
    grpc_port=50051,
    grpc_secure=False,
)

# Initialize embeddings lazily
_embeddings = None


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
    if client.collections.exists(COLLECTION_NAME):
        print(f"Collection {COLLECTION_NAME} already exists, skipping creation")
        return

    client.collections.create(
        name=COLLECTION_NAME,
        vectorizer_config=Configure.Vectorizer.none(),
        properties=[
            weaviate.classes.config.Property(name="content", data_type=weaviate.classes.config.DataType.TEXT),
            weaviate.classes.config.Property(name="type", data_type=weaviate.classes.config.DataType.TEXT),
            weaviate.classes.config.Property(name="user", data_type=weaviate.classes.config.DataType.TEXT),
            weaviate.classes.config.Property(name="timestamp", data_type=weaviate.classes.config.DataType.INT),
            weaviate.classes.config.Property(name="projectId", data_type=weaviate.classes.config.DataType.TEXT),
        ]
    )
    print(f"Collection {COLLECTION_NAME} created successfully")


async def store_entry_async(entry_data: dict):
    """Store a content entry in Weaviate"""
    try:
        metadata = entry_data.get("metadata", {})
        content = entry_data.get("content", {})

        # Convert content dict to string for storage
        content_str = str(content) if content else ""

        # Generate embedding
        embeddings = get_embeddings()  # Use lazy initialization
        vector = embeddings.embed_query(content_str)

        # Store in Weaviate
        collection = client.collections.get(COLLECTION_NAME)
        collection.data.insert(
            uuid=uuid.uuid4(),
            properties={
                "content": content_str,
                "type": metadata.get("type", "unknown"),
                "user": str(metadata.get("user", "unknown")),
                "timestamp": metadata.get("timestamp", 0),
                "projectId": str(metadata.get("projectId", "unknown")),
            },
            vector=vector
        )
        print(f"Stored entry for project {metadata.get('projectId')}")

    except Exception as e:
        print(f"Error storing entry in Weaviate: {e}")
        raise


def get_entries(project_id: str, start_time: int, end_time: int) -> List:
    """Retrieve entries from Weaviate based on project ID and time range"""
    try:
        collection = client.collections.get(COLLECTION_NAME)

        # Build the where filter
        where_filter = weaviate.classes.query.Filter.by_property("projectId").equal(project_id)

        if start_time != -1 and end_time != -1:
            where_filter = where_filter & weaviate.classes.query.Filter.by_property("timestamp").greater_or_equal(
                start_time)
            where_filter = where_filter & weaviate.classes.query.Filter.by_property("timestamp").less_or_equal(end_time)
        elif start_time != -1:
            where_filter = where_filter & weaviate.classes.query.Filter.by_property("timestamp").greater_or_equal(
                start_time)
        elif end_time != -1:
            where_filter = where_filter & weaviate.classes.query.Filter.by_property("timestamp").less_or_equal(end_time)

        response = collection.query.fetch_objects(
            where=where_filter,
            limit=1000
        )

        return response.objects

    except Exception as e:
        print(f"Error retrieving entries from Weaviate: {e}")
        return []