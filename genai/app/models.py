import weaviate
import os

class WeaviateClient:
    _client = None

    @classmethod
    def get(cls):
        if cls._client is None:
            cls._client = weaviate.Client(
                url=os.getenv('WEAVIATE_URL', 'http://weaviate:8080')
            )
            # Ensure class exists
            schema = {
                'class': 'MicroContent',
                'properties': [
                    {'name': 'type', 'dataType': ['string']},
                    {'name': 'user', 'dataType': ['string']},
                    {'name': 'timestamp', 'dataType': ['int']},
                    {'name': 'projectId', 'dataType': ['string']},
                    {'name': 'content', 'dataType': ['text']}
                ]
            }
            try:
                cls._client.schema.create_class(schema)
            except weaviate.exceptions.SchemaException:
                pass
        return cls._client