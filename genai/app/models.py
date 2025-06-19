import os
from weaviate import WeaviateClient

class WeaviateClientSingleton:
    _client: WeaviateClient = None

    @classmethod
    def get(cls) -> WeaviateClient:
        if cls._client is None:
            cls._client = WeaviateClient(
                url=os.getenv('WEAVIATE_URL', 'http://weaviate:8080'),
            )
            # Define class schema
            class_schema = {
                'class': 'MicroContent',
                'properties': [
                    {'name': 'type', 'dataType': ['string']},
                    {'name': 'user', 'dataType': ['string']},
                    {'name': 'timestamp', 'dataType': ['int']},
                    {'name': 'projectId', 'dataType': ['string']},
                    {'name': 'content', 'dataType': ['text']},
                ]
            }
            try:
                cls._client.collections.create(class_schema)
            except Exception as e:
                print(f"Error creating Weaviate class: {e}")
        return cls._client