import os
import asyncio
import pytest
from datetime import datetime, timezone
from fastapi.testclient import TestClient
from httpx import AsyncClient
from sqlalchemy.ext.asyncio import create_async_engine, async_sessionmaker
from sqlalchemy import select, Column, String, Text, Integer, DateTime, UniqueConstraint, JSON, Boolean, MetaData
from sqlalchemy.orm import declarative_base

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

    # For SQLite testing, we'll skip the computed column and unique constraint
    # that depends on MD5 function
    __table_args__ = (
        UniqueConstraint("projectId", "startTime", "endTime", name="uq_project_timeframe_test"),
    )

# Test Message model using the same TestBase
class TestMessage(TestBase):
    __tablename__ = "messages"
    id = Column(Integer, primary_key=True, index=True)
    userId = Column(String(length=36), index=True)
    projectId = Column(String(length=36), index=True)
    timestamp = Column(DateTime)
    content = Column(Text)
    loading = Column(Boolean, nullable=False)

# -------- Setup Fixtures --------

@pytest.fixture(scope="function", autouse=True)
def setup_environment(monkeypatch):
    """
    Mock environment for testing
    """
    # Use SQLite in-memory
    monkeypatch.setenv("MYSQL_USER", "test")
    monkeypatch.setenv("MYSQL_PASSWORD", "test")
    monkeypatch.setenv("MYSQL_DATABASE", "testdb")

    # Replace the models with our test versions
    monkeypatch.setattr(db, "Summary", TestSummary)
    monkeypatch.setattr(main, "Summary", TestSummary)
    monkeypatch.setattr(db, "Message", TestMessage)
    monkeypatch.setattr(main, "Message", TestMessage)

    # Mock RabbitMQ
    class DummyConnection:
        async def channel(self):
            class DummyChannel:
                async def set_qos(self, **kwargs):
                    pass

                @property
                def default_exchange(self):
                    class DummyExchange:
                        async def publish(self, message, routing_key):
                            return
                    return DummyExchange()

                async def declare_queue(self, *args, **kwargs):
                    class DummyQueue:
                        async def iterator(self):
                            class DummyIterator:
                                def __aiter__(self): return self
                                async def __anext__(self): raise StopAsyncIteration
                            return DummyIterator()
                    return DummyQueue()
            return DummyChannel()

    async def fake_connect(url):
        return DummyConnection()

    monkeypatch.setattr(main, "connect_robust", fake_connect)

    # Disable external services
    import app.weaviate_client as wc
    monkeypatch.setattr(wc, "store_entry_async", lambda entry: asyncio.Future())
    monkeypatch.setattr(main, "_blocking_summary_job", lambda *args, **kwargs: None)
    monkeypatch.setattr(main, "_blocking_qa_job", lambda *args, **kwargs: None)

    # Mock LLM functions
    monkeypatch.setattr(summarize_entries, "__call__", lambda *args, **kwargs: {"output_text": "MOCK SUMMARY"})
    monkeypatch.setattr(answer_question, "__call__", lambda *args, **kwargs: {"result": "MOCK ANSWER"})


@pytest.fixture(scope="session")
def init_test_db():
    """
    Create an in-memory SQLite database and initialize tables.
    Override the app's DB engine and session to use this test database.
    """
    engine = create_async_engine("sqlite+aiosqlite:///:memory:", echo=False)
    db.async_session = async_sessionmaker(engine, expire_on_commit=False)
    db.engine = engine

    loop = asyncio.get_event_loop()

    async def init_tables():
        async with engine.begin() as conn:
            await conn.run_sync(TestBase.metadata.create_all)

    loop.run_until_complete(init_tables())

    yield

    # Dispose engine after tests
    loop.run_until_complete(engine.dispose())


@pytest.fixture
def client(init_test_db):
    """
    Provide a TestClient for the FastAPI app, ensuring the startup events (DB init, etc.) run.
    """
    with TestClient(main.app) as c:
        yield c


@pytest.fixture
async def async_client(init_test_db):
    """
    Provide an AsyncClient for async testing if needed.
    """
    async with AsyncClient(app=main.app, base_url="http://testserver") as ac:
        yield ac


def test_post_content_success(client):
    """
    Valid content entry should be published to the queue successfully.
    """
    entry = {
        "metadata": {
            "type": "message",
            "user": "123e4567-e89b-12d3-a456-426614174000",
            "timestamp": int(datetime.now(timezone.utc).timestamp()),
            "projectId": "123e4567-e89b-12d3-a456-426614174001"
        },
        "content": {"text": "Hello team"}
    }
    response = client.post("/content", json=[entry])
    assert response.status_code == 200
    data = response.json()
    # Should confirm the message was published
    assert data["status"].startswith("Published 1")


def test_post_content_missing_fields(client):
    """
    Missing projectId or timestamp in metadata should yield a validation error (422).
    """
    entry = {
        "metadata": {
            "type": "message",
            "user": "123e4567-e89b-12d3-a456-426614174000",
            # Missing timestamp or projectId
        },
        "content": {"text": "Incomplete data"}
    }
    response = client.post("/content", json=[entry])
    assert response.status_code == 422


import uuid

test_project_id = uuid.uuid4()
test_start = 1000
test_end = 2000


def test_get_summaries_empty(client):
    """
    Initially, no summaries exist for the project; GET should return an empty list.
    """
    response = client.get(f"/projects/{test_project_id}/summary")  # Fixed URL
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
    # Verify it's stored in the database
    session = db.async_session()
    result = asyncio.get_event_loop().run_until_complete(
        session.execute(select(TestSummary).where(TestSummary.projectId == str(test_project_id)))
    )
    summaries = result.scalars().all()
    assert len(summaries) == 1
    assert summaries[0].loading is True


def test_get_summary_list(client):
    """
    After creation, GET should return the summary placeholder.
    """
    response = client.get(f"/projects/{test_project_id}/summary")
    assert response.status_code == 200
    data = response.json()
    assert len(data) == 1
    summary = data[0]
    assert summary["startTime"] == test_start
    assert summary["endTime"] == test_end
    assert summary["loading"] is True


def test_put_summary_refresh(client):
    """
    Refreshing (PUT) the same summary should delete the old and create a new placeholder.
    """
    response = client.put(f"/projects/{test_project_id}/summary?startTime={test_start}&endTime={test_end}")
    assert response.status_code == 201
    data = response.json()
    # After refresh, exactly one summary should exist (old deleted).
    response_all = client.get(f"/projects/{test_project_id}/summary")
    all_summaries = response_all.json()
    assert len(all_summaries) == 1


def test_summary_time_validation(client):
    """
    Invalid time range (startTime > endTime) should return 422.
    """
    response = client.post(f"/project/{test_project_id}/summary?startTime=5000&endTime=1000")
    assert response.status_code == 422


test_user_id = uuid.uuid4()


def test_post_message_and_get_history(client):
    """
    Posting a question should create a question entry and an answer placeholder.
    Then GET should return both entries.
    """
    question_text = "What is the current sprint goal?"
    response = client.post(
        f"/project/{test_project_id}/messages?userId={test_user_id}&question={question_text}"
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
    response = client.post(f"/projects/{test_project_id}/messages?userId={test_user_id}&question=")
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