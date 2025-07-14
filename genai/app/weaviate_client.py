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

# Initialize client and embeddings lazily
_client = None
_embeddings = None


def get_client():
    global _client
    if _client is None:
        # Parse URL to extract host and port correctly
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
    if client.collections.exists(COLLECTION_NAME):
        client.collections.delete(COLLECTION_NAME)

    client.collections.create(
        name=COLLECTION_NAME,
        vectorizer_config=Configure.Vectorizer.none(),
        properties=[
            weaviate.classes.config.Property(
                name="type",
                data_type=weaviate.classes.config.DataType.TEXT,
            ),
            weaviate.classes.config.Property(
                name="user",
                data_type=weaviate.classes.config.DataType.TEXT,
            ),
            weaviate.classes.config.Property(
                name="timestamp",
                data_type=weaviate.classes.config.DataType.INT,
            ),
            weaviate.classes.config.Property(
                name="projectId",
                data_type=weaviate.classes.config.DataType.TEXT,
            ),
            weaviate.classes.config.Property(
                name="content",
                data_type=weaviate.classes.config.DataType.TEXT,
            ),
        ],
    )
    print(f"Collection {COLLECTION_NAME} created successfully")


async def store_entry_async(entry_data: dict):
    """Store a content entry in Weaviate"""
    try:
        client = get_client()
        collection = client.collections.get(COLLECTION_NAME)

        metadata = entry_data.get("metadata", {})
        content = entry_data.get("content", {})

        data_object = {
            "type": metadata.get("type", "unknown"),
            "user": str(metadata.get("user", "")),
            "timestamp": metadata.get("timestamp", 0),
            "projectId": str(metadata.get("projectId", "")),
            "content": str(content)
        }

        collection.data.insert(
            properties=data_object,
            uuid=uuid.uuid4()
        )

        print(f"Entry stored successfully for project {data_object['projectId']}")

    except Exception as e:
        print(f"Error storing entry in Weaviate: {e}")


def get_entries(project_id: str, start_time: int, end_time: int) -> List:
    """Retrieve entries from Weaviate based on project ID and time range"""
    try:
        client = get_client()
        collection = client.collections.get(COLLECTION_NAME)

        # Build the where filter
        where_filter = weaviate.classes.query.Filter.by_property("projectId").equal(project_id)

        if start_time != -1 and end_time != -1:
            time_filter = (
                    weaviate.classes.query.Filter.by_property("timestamp").greater_or_equal(start_time) &
                    weaviate.classes.query.Filter.by_property("timestamp").less_or_equal(end_time)
            )
            where_filter = where_filter & time_filter
        elif start_time != -1:
            time_filter = weaviate.classes.query.Filter.by_property("timestamp").greater_or_equal(start_time)
            where_filter = where_filter & time_filter
        elif end_time != -1:
            time_filter = weaviate.classes.query.Filter.by_property("timestamp").less_or_equal(end_time)
            where_filter = where_filter & time_filter

        response = collection.query.fetch_objects(
            where=where_filter,
            limit=1000
        )

        return response.objects

    except Exception as e:
        print(f"Error retrieving entries from Weaviate: {e}")
        return []


# For backward compatibility
@property
def client():
    return get_client()