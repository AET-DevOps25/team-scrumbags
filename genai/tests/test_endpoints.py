import asyncio
import json
import pytest
import uuid
from datetime import datetime, UTC
from unittest.mock import AsyncMock, MagicMock, patch

from fastapi.testclient import TestClient
from sqlalchemy.ext.asyncio import create_async_engine, async_sessionmaker
from sqlalchemy.pool import StaticPool

from app.main import app, _blocking_summary_job, _blocking_qa_job
from app.db import Base, Summary, Message, async_session
from app.models import ContentEntry, Metadata

# Test database setup
TEST_DATABASE_URL = "sqlite+aiosqlite:///:memory:"


@pytest.fixture
async def test_db():
    # Create test engine with in-memory SQLite
    engine = create_async_engine(
        TEST_DATABASE_URL,
        connect_args={"check_same_thread": False},
        poolclass=StaticPool,
        echo=True
    )

    # Create tables
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)

    # Create session factory
    test_async_session = async_sessionmaker(engine, expire_on_commit=False)

    # Yield session maker
    yield test_async_session

    # Cleanup
    await engine.dispose()


@pytest.fixture
def client(test_db):
    # Override the dependency with our test database
    app.dependency_overrides[async_session] = lambda: test_db

    # Mock RabbitMQ connection and channel
    with patch('app.main.rabbit_connection'), patch('app.main.rabbit_channel'):
        # Mock Weaviate client
        with patch('app.main.wc.init_collection'), patch('app.main.wc.store_entry_async'):
            # Mock the consumer task
            with patch('app.main.consume', return_value=asyncio.Future()):
                client = TestClient(app)
                yield client

    # Clean up dependency overrides after test
    app.dependency_overrides.clear()


@pytest.fixture
def sample_project_id():
    return str(uuid.uuid4())


@pytest.fixture
def sample_user_id():
    return str(uuid.uuid4())


@pytest.fixture
def sample_content_entry(sample_project_id, sample_user_id):
    return ContentEntry(
        metadata=Metadata(
            type="message",
            user=sample_user_id,
            timestamp=1234567890,
            projectId=sample_project_id
        ),
        content={"text": "Test message content"}
    )


class TestPostContent:
    @patch('app.main.rabbit_channel')
    def test_post_content_success(self, mock_channel, client, sample_content_entry):
        mock_channel.default_exchange.publish = AsyncMock()

        entry_dict = sample_content_entry.model_dump()
        entry_dict['metadata']['user'] = str(entry_dict['metadata']['user'])
        entry_dict['metadata']['projectId'] = str(entry_dict['metadata']['projectId'])

        response = client.post("/content", json=[entry_dict])

        assert response.status_code == 200
        assert "Published 1 message(s) to queue" in response.json()["status"]
        mock_channel.default_exchange.publish.assert_called_once()

    @patch('app.main.rabbit_channel')
    def test_post_content_multiple_entries(self, mock_channel, client, sample_project_id, sample_user_id):
        mock_channel.default_exchange.publish = AsyncMock()

        entries = []
        for i in range(3):
            entry = {
                "metadata": {
                    "type": "message",
                    "user": str(sample_user_id),
                    "timestamp": 1234567890 + i,
                    "projectId": str(sample_project_id)
                },
                "content": {"text": f"Test message {i}"}
            }
            entries.append(entry)

        response = client.post("/content", json=entries)

        assert response.status_code == 200
        assert "Published 3 message(s) to queue" in response.json()["status"]
        assert mock_channel.default_exchange.publish.call_count == 3

    def test_post_content_missing_project_id(self, client, sample_user_id):
        entry = {
            "metadata": {
                "type": "message",
                "user": str(sample_user_id),
                "timestamp": 1234567890,
                "projectId": None
            },
            "content": {"text": "Test message"}
        }

        response = client.post("/content", json=[entry])
        assert response.status_code == 422

    def test_post_content_missing_timestamp(self, client, sample_project_id, sample_user_id):
        entry = {
            "metadata": {
                "type": "message",
                "user": str(sample_user_id),
                "timestamp": None,
                "projectId": str(sample_project_id)
            },
            "content": {"text": "Test message"}
        }

        response = client.post("/content", json=[entry])
        assert response.status_code == 422

    @patch('app.main.rabbit_channel', None)
    def test_post_content_rabbitmq_not_initialized(self, client, sample_content_entry):
        entry_dict = sample_content_entry.model_dump()
        entry_dict['metadata']['user'] = str(entry_dict['metadata']['user'])
        entry_dict['metadata']['projectId'] = str(entry_dict['metadata']['projectId'])

        response = client.post("/content", json=[entry_dict])
        assert response.status_code == 500
        assert "RabbitMQ not initialized" in response.json()["detail"]


class TestGetSummary:
    @pytest.mark.asyncio
    async def test_get_summary_existing(self, test_db, client, sample_project_id, sample_user_id):
        # Create existing summary
        async with test_db() as session:
            existing_summary = Summary(
                projectId=sample_project_id,
                startTime=1000,
                endTime=2000,
                userIds=[str(sample_user_id)],
                loading=False,
                generatedAt=datetime.now(UTC),
                summary="Existing summary"
            )
            session.add(existing_summary)
            await session.commit()

        response = client.post(
            f"/projects/{sample_project_id}/summary",
            params={
                "startTime": 1000,
                "endTime": 2000,
                "userIds": [sample_user_id]
            }
        )

        assert response.status_code == 200
        data = response.json()
        assert data["output_text"] == "Existing summary"
        assert data["projectId"] == sample_project_id

    @patch('app.main._blocking_summary_job')
    @patch('app.langchain_provider.summarize_entries')
    def test_get_summary_new(self, mock_summarize, mock_job, client, sample_project_id, sample_user_id):
        response = client.post(
            f"/projects/{sample_project_id}/summary",
            params={
                "startTime": 1000,
                "endTime": 2000,
                "userIds": [sample_user_id]
            }
        )

        assert response.status_code == 200
        data = response.json()
        assert data["projectId"] == sample_project_id
        assert data["loading"] is True
        assert data["summary"] == ""

    def test_get_summary_invalid_time_range(self, client, sample_project_id):
        response = client.post(
            f"/projects/{sample_project_id}/summary",
            params={
                "startTime": 2000,
                "endTime": 1000
            }
        )
        assert response.status_code == 422
        assert "startTime must be ≤ endTime" in response.json()["detail"]

    def test_get_summary_invalid_project_id(self, client):
        response = client.post(
            "/projects/invalid-uuid/summary",
            params={
                "startTime": 1000,
                "endTime": 2000
            }
        )
        assert response.status_code == 422

    def test_get_summary_default_params(self, client, sample_project_id):
        response = client.post(f"/projects/{sample_project_id}/summary")
        assert response.status_code == 200


class TestRefreshSummary:
    @pytest.mark.asyncio
    async def test_refresh_summary_success(self, test_db, client, sample_project_id, sample_user_id):
        # Create existing summary to delete
        async with test_db() as session:
            existing_summary = Summary(
                projectId=sample_project_id,
                startTime=1000,
                endTime=2000,
                userIds=[str(sample_user_id)],
                loading=False,
                generatedAt=datetime.now(UTC),
                summary="Existing summary"
            )
            session.add(existing_summary)
            await session.commit()

        response = client.put(
            f"/projects/{sample_project_id}/summary",
            params={
                "startTime": 1000,
                "endTime": 2000,
                "userIds": [sample_user_id]
            }
        )

        assert response.status_code == 201
        data = response.json()
        assert data["projectId"] == sample_project_id
        assert data["loading"] is True

    def test_refresh_summary_invalid_time_range(self, client, sample_project_id):
        response = client.put(
            f"/projects/{sample_project_id}/summary",
            params={
                "startTime": 2000,
                "endTime": 1000
            }
        )
        assert response.status_code == 422


class TestGetSummaries:
    @pytest.mark.asyncio
    async def test_get_summaries_success(self, test_db, client, sample_project_id, sample_user_id):
        # Create test summaries
        async with test_db() as session:
            summary1 = Summary(
                projectId=sample_project_id,
                startTime=1000,
                endTime=2000,
                userIds=[str(sample_user_id)],
                loading=False,
                generatedAt=datetime.now(UTC),
                summary="Summary 1"
            )
            summary2 = Summary(
                projectId=sample_project_id,
                startTime=3000,
                endTime=4000,
                userIds=[str(sample_user_id)],
                loading=False,
                generatedAt=datetime.now(UTC),
                summary="Summary 2"
            )
            session.add_all([summary1, summary2])
            await session.commit()

        response = client.get(f"/projects/{sample_project_id}/summary")
        assert response.status_code == 200
        data = response.json()
        assert len(data) == 2

    def test_get_summaries_empty(self, client, sample_project_id):
        response = client.get(f"/projects/{sample_project_id}/summary")
        assert response.status_code == 200
        assert response.json() == []

    def test_get_summaries_invalid_project_id(self, client):
        response = client.get("/projects/invalid-uuid/summary")
        assert response.status_code == 422


class TestQueryProject:
    @patch('app.main._blocking_qa_job')
    def test_query_project_success(self, mock_job, client, sample_project_id, sample_user_id):
        question = "What happened in the project?"
        response = client.post(
            f"/projects/{sample_project_id}/messages",
            params={"userId": sample_user_id},
            json=question
        )

        assert response.status_code == 200
        data = response.json()
        assert data["projectId"] == sample_project_id
        assert data["userId"] == str(sample_user_id)
        assert data["loading"] is True

    def test_query_project_empty_question(self, client, sample_project_id, sample_user_id):
        response = client.post(
            f"/projects/{sample_project_id}/messages",
            params={"userId": sample_user_id},
            json=""
        )
        assert response.status_code == 422
        assert "Question cannot be empty" in response.json()["detail"]

    def test_query_project_whitespace_question(self, client, sample_project_id, sample_user_id):
        response = client.post(
            f"/projects/{sample_project_id}/messages",
            params={"userId": sample_user_id},
            json="   "
        )
        assert response.status_code == 422

    def test_query_project_invalid_project_id(self, client, sample_user_id):
        response = client.post(
            "/projects/invalid-uuid/messages",
            params={"userId": sample_user_id},
            json="Test question"
        )
        assert response.status_code == 422

    def test_query_project_invalid_user_id(self, client, sample_project_id):
        response = client.post(
            f"/projects/{sample_project_id}/messages",
            params={"userId": "invalid-uuid"},
            json="Test question"
        )
        assert response.status_code == 422


class TestGetChatHistory:
    @pytest.mark.asyncio
    async def test_get_chat_history_success(self, test_db, client, sample_project_id, sample_user_id):
        # Create test messages
        async with test_db() as session:
            message1 = Message(
                projectId=sample_project_id,
                userId=str(sample_user_id),
                content="Question 1",
                timestamp=datetime.now(UTC),
                loading=False
            )
            message2 = Message(
                projectId=sample_project_id,
                userId=str(sample_user_id),
                content="Answer 1",
                timestamp=datetime.now(UTC),
                loading=False
            )
            session.add_all([message1, message2])
            await session.commit()

        response = client.get(
            f"/projects/{sample_project_id}/messages",
            params={"userId": sample_user_id}
        )
        assert response.status_code == 200
        data = response.json()
        assert len(data) == 2

    def test_get_chat_history_empty(self, client, sample_project_id, sample_user_id):
        response = client.get(
            f"/projects/{sample_project_id}/messages",
            params={"userId": sample_user_id}
        )
        assert response.status_code == 200
        assert response.json() == []

    def test_get_chat_history_invalid_project_id(self, client, sample_user_id):
        response = client.get(
            "/projects/invalid-uuid/messages",
            params={"userId": sample_user_id}
        )
        assert response.status_code == 422

    def test_get_chat_history_invalid_user_id(self, client, sample_project_id):
        response = client.get(
            f"/projects/{sample_project_id}/messages",
            params={"userId": "invalid-uuid"}
        )
        assert response.status_code == 422


class TestBlockingJobs:
    @patch('app.langchain_provider.summarize_entries')
    @patch('app.main.async_session')
    def test_blocking_summary_job(self, mock_session, mock_summarize):
        mock_summarize.return_value = {"output_text": "Test summary"}

        _blocking_summary_job(1, "project_id", 1000, 2000, ["user_id"])

        mock_summarize.assert_called_once_with("project_id", 1000, 2000, ["user_id"])

    @patch('app.langchain_provider.answer_question')
    @patch('app.main.async_session')
    def test_blocking_qa_job(self, mock_session, mock_answer):
        mock_answer.return_value = {"result": "Test answer"}

        _blocking_qa_job(1, "project_id", "Test question")

        mock_answer.assert_called_once_with("project_id", "Test question")

    @patch('app.langchain_provider.answer_question')
    @patch('app.main.async_session')
    def test_blocking_qa_job_error_handling(self, mock_session, mock_answer):
        mock_answer.return_value = None

        _blocking_qa_job(1, "project_id", "Test question")

        mock_answer.assert_called_once()


class TestEdgeCases:
    def test_negative_timestamps(self, client, sample_project_id):
        response = client.post(
            f"/projects/{sample_project_id}/summary",
            params={
                "startTime": -2,
                "endTime": -1
            }
        )
        assert response.status_code == 422

    def test_large_user_ids_list(self, client, sample_project_id):
        user_ids = [str(uuid.uuid4()) for _ in range(100)]
        response = client.post(
            f"/projects/{sample_project_id}/summary",
            params={
                "startTime": 1000,
                "endTime": 2000,
                "userIds": user_ids
            }
        )
        assert response.status_code == 200

    @patch('app.main.rabbit_channel')
    def test_post_content_large_payload(self, mock_channel, client, sample_project_id, sample_user_id):
        mock_channel.default_exchange.publish = AsyncMock()

        # Create a large content entry
        large_content = {"data": "x" * 10000}
        entry = ContentEntry(
            metadata=Metadata(
                type="message",
                user=sample_user_id,
                timestamp=1234567890,
                projectId=sample_project_id
            ),
            content=large_content
        )

        entry_dict = entry.model_dump()
        entry_dict['metadata']['user'] = str(entry_dict['metadata']['user'])
        entry_dict['metadata']['projectId'] = str(entry_dict['metadata']['projectId'])

        response = client.post("/content", json=[entry_dict])

        assert response.status_code == 200