import os
import asyncio
import pytest
import uuid
from datetime import datetime, timezone
from fastapi.testclient import TestClient
from httpx import AsyncClient
from sqlalchemy.ext.asyncio import create_async_engine, async_sessionmaker
from sqlalchemy import select, Column, String, Text, Integer, DateTime, UniqueConstraint, JSON, Boolean, MetaData
from sqlalchemy.orm import declarative_base
from unittest.mock import AsyncMock, MagicMock, patch

# Import the FastAPI app and modules
import app.main as main
import app.db as db
from app.db import Message
from app.langchain_provider import summarize_entries, answer_question

TestBase = declarative_base(metadata=MetaData())


# Create a test-specific Summary model without the computed column
class TestSummary(TestBase):
    __tablename__ = "summaries"
    id = Column(Integer, primary_key=True, index=True)
    projectId = Column(String(length=36), index=True)
    startTime = Column(Integer, index=True)
    endTime = Column(Integer, index=True)
    generatedAt = Column(DateTime)
    summary = Column(Text)
    userIds = Column(JSON, nullable=False, default=list)
    loading = Column(Boolean, nullable=False)

    __table_args__ = (
        UniqueConstraint("projectId", "startTime", "endTime", name="uq_project_timeframe_test"),
    )


# Test Message model using the same TestBase
class TestMessage(TestBase):
    __tablename__ = "messages"
    id = Column(Integer, primary_key=True, index=True)
    projectId = Column(String(length=36), index=True)
    userId = Column(String(length=36), index=True)
    content = Column(Text)
    timestamp = Column(DateTime, index=True)
    loading = Column(Boolean, nullable=False)


@pytest.fixture(scope="function", autouse=True)
def setup_environment(monkeypatch):
    """Set up test environment variables and mocks."""
    # Use SQLite for testing
    monkeypatch.setenv("DATABASE_URL", "sqlite+aiosqlite:///test.db")
    monkeypatch.setenv("MYSQL_URL", "sqlite+aiosqlite:///test.db")

    # Mock external services
    mock_weaviate_client = MagicMock()
    mock_weaviate_client.connect.return_value = None
    mock_weaviate_client.close.return_value = None
    mock_weaviate_client.collections.list_all.return_value = ["ProjectContent"]
    mock_weaviate_client.collections.create.return_value = None

    monkeypatch.setattr("app.weaviate_client.client", mock_weaviate_client)
    monkeypatch.setattr("app.main.wc.client", mock_weaviate_client)
    monkeypatch.setattr("app.main.wc.init_collection", MagicMock())

    # Mock RabbitMQ
    mock_connection = AsyncMock()
    mock_channel = AsyncMock()
    mock_channel.set_qos = AsyncMock()
    mock_channel.default_exchange = AsyncMock()
    mock_channel.default_exchange.publish = AsyncMock()

    monkeypatch.setattr("app.main.rabbit_connection", mock_connection)
    monkeypatch.setattr("app.main.rabbit_channel", mock_channel)
    monkeypatch.setattr("aio_pika.connect_robust", AsyncMock(return_value=mock_connection))

    # Mock queue consumer
    monkeypatch.setattr("app.main.consume", AsyncMock())

    # Mock langchain functions
    monkeypatch.setattr("app.langchain_provider.summarize_entries",
                        lambda *args: {"output_text": "Test summary"})
    monkeypatch.setattr("app.langchain_provider.answer_question",
                        lambda *args: {"result": "Test answer"})


@pytest.fixture(scope="session")
def init_test_db():
    """Initialize test database with SQLite."""
    # Create async engine for SQLite
    engine = create_async_engine("sqlite+aiosqlite:///test.db", echo=False)

    async def create_tables():
        async with engine.begin() as conn:
            await conn.run_sync(TestBase.metadata.create_all)

    # Run the coroutine
    asyncio.run(create_tables())

    yield

    # Cleanup
    async def drop_tables():
        async with engine.begin() as conn:
            await conn.run_sync(TestBase.metadata.drop_all)

    asyncio.run(drop_tables())


@pytest.fixture
def client(init_test_db):
    """Provide a TestClient for the FastAPI app with mocked database."""
    # Patch the database session to use SQLite
    test_engine = create_async_engine("sqlite+aiosqlite:///test.db", echo=False)
    test_session = async_sessionmaker(test_engine, expire_on_commit=False)

    with patch.object(db, 'async_session', test_session):
        with TestClient(main.app) as c:
            yield c


@pytest.fixture
async def async_client(init_test_db):
    """Provide an AsyncClient for the FastAPI app."""
    test_engine = create_async_engine("sqlite+aiosqlite:///test.db", echo=False)
    test_session = async_sessionmaker(test_engine, expire_on_commit=False)

    with patch.object(db, 'async_session', test_session):
        async with AsyncClient(app=main.app, base_url="http://test") as ac:
            yield ac


def test_post_content_success(client):
    """
    A valid content entry should be accepted.
    """
    valid_entry = {
        "metadata": {
            "type": "commit",
            "user": str(uuid.uuid4()),
            "timestamp": 1609459200,
            "projectId": str(uuid.uuid4())
        },
        "content": {"message": "Initial commit"}
    }
    response = client.post("/content", json=[valid_entry])
    assert response.status_code == 200
    data = response.json()
    assert "Published 1 message(s) to queue" in data["status"]


def test_post_content_missing_fields(client):
    """
    Content missing required fields should be rejected.
    """
    invalid_entry = {
        "metadata": {
            "type": "commit"
            # missing user, timestamp, projectId
        },
        "content": {"message": "Test"}
    }
    response = client.post("/content", json=[invalid_entry])
    assert response.status_code == 422


test_project_id = uuid.uuid4()
test_start = 1000
test_end = 2000


def test_get_summaries_empty(client):
    """
    Initially, no summaries exist for the project; GET should return an empty list.
    """
    response = client.get(f"/projects/{test_project_id}/summary")
    assert response.status_code == 200
    data = response.json()
    assert data == []


def test_post_summary_create(client):
    """
    Creating a summary (POST) should return a placeholder with loading=True.
    """
    response = client.post(f"/projects/{test_project_id}/summary?startTime={test_start}&endTime={test_end}")
    assert response.status_code == 200
    data = response.json()
    assert data["projectId"] == str(test_project_id)
    assert data["startTime"] == test_start
    assert data["endTime"] == test_end
    assert data["loading"] is True
    assert data["summary"] == ""


def test_get_summary_list(client):
    """
    After creating a summary, it should appear in the GET list.
    """
    # Create a summary first
    client.post(f"/projects/{test_project_id}/summary?startTime={test_start}&endTime={test_end}")

    # Now list all summaries
    response = client.get(f"/projects/{test_project_id}/summary")
    assert response.status_code == 200
    data = response.json()
    assert len(data) >= 1
    assert any(s["startTime"] == test_start and s["endTime"] == test_end for s in data)


def test_put_summary_refresh(client):
    """
    PUT should overwrite an existing summary.
    """
    # Create initial summary
    client.post(f"/projects/{test_project_id}/summary?startTime={test_start}&endTime={test_end}")

    # Refresh it
    response = client.put(f"/projects/{test_project_id}/summary?startTime={test_start}&endTime={test_end}")
    assert response.status_code == 201
    data = response.json()
    assert data["loading"] is True


def test_summary_time_validation(client):
    """
    startTime > endTime should result in a validation error.
    """
    response = client.post(f"/projects/{test_project_id}/summary?startTime=5000&endTime=1000")
    assert response.status_code == 422


test_user_id = uuid.uuid4()


def test_post_message_and_get_history(client):
    """
    Posting a question should create a question entry and an answer placeholder.
    Then GET should return both entries.
    """
    question_text = "What is the current sprint goal?"
    response = client.post(
        f"/projects/{test_project_id}/messages?userId={test_user_id}",
        json=question_text
    )
    assert response.status_code == 200
    data = response.json()
    # The response is the answer placeholder (loading)
    assert data["userId"] == str(test_user_id)
    assert data["projectId"] == str(test_project_id)
    assert data["content"] == ""
    assert data["loading"] is True

    # Now retrieve history
    history_resp = client.get(f"/projects/{test_project_id}/messages?userId={test_user_id}")
    assert history_resp.status_code == 200
    history = history_resp.json()
    # Should have 2 entries: question (loading False) and answer placeholder (loading True)
    assert len(history) == 2
    question_entry, answer_entry = history
    assert question_entry["content"] == question_text
    assert question_entry["loading"] is False
    assert answer_entry["content"] == ""
    assert answer_entry["loading"] is True


def test_post_message_question_validation(client):
    """
    An empty question parameter should yield a 422 error.
    """
    response = client.post(f"/projects/{test_project_id}/messages?userId={test_user_id}", json="")
    assert response.status_code == 422


def test_get_chat_history_no_messages(client):
    """
    GET chat history for a user/project with no messages should return an empty list.
    """
    other_project = uuid.uuid4()
    response = client.get(f"/projects/{other_project}/messages?userId={test_user_id}")
    assert response.status_code == 200
    data = response.json()
    assert isinstance(data, list) and len(data) == 0