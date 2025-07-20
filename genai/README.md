# GenAI (genai) Microservice

This microservice is a FastAPI app is addressed by the other microservices via the `/content` endpoint. It is responsible for processing the data collected from the other microservices, generating summaries, and providing answers to user queries. The service uses a large language model (LLM), specifically LLaMa3.3 (cloud) or LLaMa3.2 (local) to understand and process natural language queries and generate relevant responses. To do so, all collected data is stored in a vectorized database and retrieved via Retrieval Augmented Generation (RAG).


## Table of Contents

- [GenAI (genai) Microservice](#genai-genai-microservice)

    - [Table of Contents](#table-of-contents)
    - [Setup](#setup)
    - [Testing Endpoints (Swagger Documentation)](#testing-endpoints-swagger-documentation)
    - [Software Design](#software-design)
    - [Integration Tests](#integration-tests)
    - [CI/CD Pipeline](#cicd-pipeline)

## Setup

In order the use the build the microservice by itself:

- Copy the contents of `.env.example` into a new file with the name `.env`
- Fill in the `.env` file with the required values, specifically setting `OPEN_WEBUI_BEARER` and `NOMIC_API_KEY`. Contact the project maintainers if you do not have access to these values.
- Run the docker compose file via `docker compose up -d`
- Test the API endpoints using the provided Bruno collection and the preconfigured examples.


## Testing Endpoints (Swagger Documentation)

After starting the GenAI container, Swagger API docs can be viewed at http://localhost:4242/docs.

In the case that the whole project is run using `../docker-compose.local.yml`, then the docs can be accessed via http://localhost:4242/docs.

For testing the `/content` endpoint, make sure to use a valid json body, such as:

```json
[{
  "metadata": {
    "type": "communicationMessage",
    "user": "3e6c5c2c-ecee-4166-9e5d-8867e8c7c824",
    "timestamp": 1640995200,
    "projectId": "6f31ffb4-be67-40b6-892b-d1ff02d93d8d"
  },
  "content": {
    "platform": "DISCORD",
    "message": "this is a test message",
    "platformUserId": "userId",
    "platformGlobalName": "GlobalUserName"
  },
  ...
}]
```

## Software Design

The implementation incorporates multiple frameworks and sub-services: 
- FastAPI for the REST API
- RabbitMQ for message queuing (specifically for the `/content` endpoint) to improve performance and scalability when processing large amounts of data
- Pydantic for data validation
- SQLAlchemy for database interactions
- LangChain for the `summarize_chain` and `RetrievalQA` chains for generating summaries and answering questions
- Weaviate as the vectorized database for storing and retrieving data for the RAG functionality
- MySQL database (`genai-db`) for storing finished summaries (reports) and question and answer messages that are not part of the RAG functionality, but need to be stored for client display

The service supports parallel processing of multiple requests, allowing for efficient handling of large volumes of data. It also includes error handling and logging to ensure robustness and traceability.
It uses a ThreadPoolExecutor to handle multiple requests concurrently, and asynchronous functions for handling summarization and question answering tasks.
RabbitMQ asynchronously queues the incoming data from the `/content` endpoint, allowing for efficient processing of large amounts of data without blocking the main thread, while parallelizing the data insertion into the Weaviate vectorized database.

## Integration Tests

There are integration tests implemented that test the functionality of each endpoint separately. This is implemented using the `pytest` framework and the `httpx` library for making HTTP requests to the FastAPI application. The tests mock the Weaviate DB, RabbitMQ, and LangChain components to ensure that the tests can run independently of the actual services.


## CI/CD Pipeline

The microservice image is automatically rebuilt and tested via Github Actions upon creating a pull request going into main. The actions for this microservice are only run in the case that the microservice directory `/genai` actually has file changes.