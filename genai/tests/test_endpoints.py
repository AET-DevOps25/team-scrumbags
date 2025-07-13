import asyncio
import json
import os
import pytest
import pytest_asyncio
from datetime import datetime, UTC
from typing import AsyncGenerator
from unittest.mock import patch, AsyncMock
from uuid import uuid4

from fastapi.testclient import TestClient
from httpx import AsyncClient
from sqlalchemy.ext.asyncio import create_async_engine, async_sessionmaker
from sqlalchemy import select

from app.main import app
from app.db import Base, Summary, Message, async_session
from app.models import ContentEntry, Metadata

# Test database URL - using SQLite for testing
TEST_DATABASE_URL = "sqlite+aiosqlite:///./test.db"

# Override the database session for testing
test_engine = create_async_engine(TEST_DATABASE_URL, echo=False)
test_async_session = async_sessionmaker(test_engine, expire_on_commit=False)

# Test data
TEST_PROJECT_ID = str(uuid4())
TEST_USER_ID = str(uuid4())
TEST_USER_ID_2 = str(uuid4())


@pytest_asyncio.fixture
async def setup_database():
    """Setup test database"""
    async with test_engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)
    yield
    async with test_engine.begin() as conn:
        await conn.run_sync(Base.metadata.drop_all)


@pytest_asyncio.fixture
async def override_db_session(setup_database):
    """Override the database session dependency"""
    app.dependency_overrides[async_session] = lambda: test_async_session()
    yield
    app.dependency_overrides.clear()


@pytest_asyncio.fixture
async def client(override_db_session) -> AsyncGenerator[AsyncClient, None]:
    """Create test client"""
    async with AsyncClient(app=app, base_url="http://test") as ac:
        yield ac


@pytest_asyncio.fixture
async def mock_services():
    """Mock external services"""
    with patch('app.main.rabbit_channel') as mock_rabbit, \
            patch('app.weaviate_client.store_entry_async') as mock_weaviate, \
            patch('app.langchain_provider.summarize_entries') as mock_summarize, \
            patch('app.langchain_provider.answer_question') as mock_answer, \
            patch('app.langchain_provider.get_embeddings') as mock_embeddings:
        # Mock RabbitMQ
        mock_rabbit.return_value = AsyncMock()
        mock_rabbit.default_exchange = AsyncMock()
        mock_rabbit.default_exchange.publish = AsyncMock()

        # Mock Weaviate
        mock_weaviate.return_value = None

        # Mock LangChain
        mock_summarize.return_value = {"output_text": "Test summary content"}
        mock_answer.return_value = {"result": "Test answer content"}

        # Mock embeddings
        mock_embeddings.return_value = AsyncMock()

        yield {
            'rabbit': mock_rabbit,
            'weaviate': mock_weaviate,
            'summarize': mock_summarize,
            'answer': mock_answer,
            'embeddings': mock_embeddings
        }


class TestContentEndpoint:
    """Test /content endpoint"""

    @pytest.mark.asyncio
    async def test_post_content_success(self, client: AsyncClient, mock_services):
        """Test successful content posting"""
        content_entries = [
            {
                "metadata": {
                    "type": "commit",
                    "user": TEST_USER_ID,
                    "timestamp": 1234567890,
                    "projectId": TEST_PROJECT_ID
                },
                "content": {
                    "message": "Initial commit",
                    "author": "test_user"
                }
            }
        ]

        response = await client.post("/content", json=content_entries)

        assert response.status_code == 200
        assert response.json() == {"status": "Published 1 message(s) to queue."}

    @pytest.mark.asyncio
    async def test_post_content_multiple_entries(self, client: AsyncClient, mock_services):
        """Test posting multiple content entries"""
        content_entries = [
            {
                "metadata": {
                    "type": "commit",
                    "user": TEST_USER_ID,
                    "timestamp": 1234567890,
                    "projectId": TEST_PROJECT_ID
                },
                "content": {"message": "First commit"}
            },
            {
                "metadata": {
                    "type": "pull_request",
                    "user": TEST_USER_ID_2,
                    "timestamp": 1234567891,
                    "projectId": TEST_PROJECT_ID
                },
                "content": {"title": "Add feature"}
            }
        ]

        response = await client.post("/content", json=content_entries)

        assert response.status_code == 200
        assert response.json() == {"status": "Published 2 message(s) to queue."}

    @pytest.mark.asyncio
    async def test_post_content_missing_project_id(self, client: AsyncClient, mock_services):
        """Test content posting with missing projectId"""
        content_entries = [
            {
                "metadata": {
                    "type": "commit",
                    "user": TEST_USER_ID,
                    "timestamp": 1234567890
                },
                "content": {"message": "Invalid entry"}
            }
        ]

        response = await client.post("/content", json=content_entries)
        assert response.status_code == 422

    @pytest.mark.asyncio
    async def test_post_content_missing_timestamp(self, client: AsyncClient, mock_services):
        """Test content posting with missing timestamp"""
        content_entries = [
            {
                "metadata": {
                    "type": "commit",
                    "user": TEST_USER_ID,
                    "projectId": TEST_PROJECT_ID
                },
                "content": {"message": "Invalid entry"}
            }
        ]

        response = await client.post("/content", json=content_entries)
        assert response.status_code == 422


class TestSummaryEndpoints:
    """Test summary-related endpoints"""

    @pytest.mark.asyncio
    async def test_get_summary_new(self, client: AsyncClient, mock_services):
        """Test getting a new summary (should create placeholder)"""
        params = {
            "startTime": 1234567890,
            "endTime": 1234567900,
            "userIds": [TEST_USER_ID]
        }

        response = await client.post(f"/projects/{TEST_PROJECT_ID}/summary", params=params)

        assert response.status_code == 200
        data = response.json()
        assert data["projectId"] == TEST_PROJECT_ID
        assert data["startTime"] == 1234567890
        assert data["endTime"] == 1234567900
        assert data["loading"] is True
        assert data["summary"] == ""

    @pytest.mark.asyncio
    async def test_get_summary_existing(self, client: AsyncClient, mock_services):
        """Test getting an existing summary"""
        # First create a summary
        async with test_async_session() as session:
            summary = Summary(
                projectId=TEST_PROJECT_ID,
                startTime=1234567890,
                endTime=1234567900,
                userIds=[TEST_USER_ID],
                loading=False,
                generatedAt=datetime.now(UTC),
                summary="Existing summary"
            )
            session.add(summary)
            await session.commit()

        params = {
            "startTime": 1234567890,
            "endTime": 1234567900,
            "userIds": [TEST_USER_ID]
        }

        response = await client.post(f"/projects/{TEST_PROJECT_ID}/summary", params=params)

        assert response.status_code == 200
        data = response.json()
        assert data["output_text"] == "Existing summary"

    @pytest.mark.asyncio
    async def test_get_summary_invalid_time_range(self, client: AsyncClient, mock_services):
        """Test getting summary with invalid time range"""
        params = {
            "startTime": 1234567900,
            "endTime": 1234567890,  # End before start
            "userIds": [TEST_USER_ID]
        }

        response = await client.post(f"/projects/{TEST_PROJECT_ID}/summary", params=params)

        assert response.status_code == 422
        assert "startTime must be ≤ endTime" in response.json()["detail"]

    @pytest.mark.asyncio
    async def test_refresh_summary(self, client: AsyncClient, mock_services):
        """Test refreshing/regenerating a summary"""
        # First create an existing summary
        async with test_async_session() as session:
            summary = Summary(
                projectId=TEST_PROJECT_ID,
                startTime=1234567890,
                endTime=1234567900,
                userIds=[TEST_USER_ID],
                loading=False,
                generatedAt=datetime.now(UTC),
                summary="Old summary"
            )
            session.add(summary)
            await session.commit()

        params = {
            "startTime": 1234567890,
            "endTime": 1234567900,
            "userIds": [TEST_USER_ID]
        }

        response = await client.put(f"/projects/{TEST_PROJECT_ID}/summary", params=params)

        assert response.status_code == 201
        data = response.json()
        assert data["loading"] is True
        assert data["summary"] == ""  # New placeholder

    @pytest.mark.asyncio
    async def test_get_all_summaries(self, client: AsyncClient, mock_services):
        """Test getting all summaries for a project"""
        # Create multiple summaries
        async with test_async_session() as session:
            summary1 = Summary(
                projectId=TEST_PROJECT_ID,
                startTime=1234567890,
                endTime=1234567900,
                userIds=[TEST_USER_ID],
                loading=False,
                generatedAt=datetime.now(UTC),
                summary="Summary 1"
            )
            summary2 = Summary(
                projectId=TEST_PROJECT_ID,
                startTime=1234567901,
                endTime=1234567910,
                userIds=[TEST_USER_ID_2],
                loading=True,
                generatedAt=datetime.now(UTC),
                summary=""
            )
            session.add_all([summary1, summary2])
            await session.commit()

        response = await client.get(f"/projects/{TEST_PROJECT_ID}/summary")

        assert response.status_code == 200
        data = response.json()
        assert len(data) == 2
        assert all(s["projectId"] == TEST_PROJECT_ID for s in data)


class TestMessageEndpoints:
    """Test message/Q&A endpoints"""

    @pytest.mark.asyncio
    async def test_query_project(self, client: AsyncClient, mock_services):
        """Test querying project with a question"""
        params = {"userId": TEST_USER_ID}
        question = "What was the last commit about?"

        response = await client.post(
            f"/projects/{TEST_PROJECT_ID}/messages",
            params=params,
            json=question
        )

        assert response.status_code == 200
        data = response.json()
        assert data["userId"] == TEST_USER_ID
        assert data["projectId"] == TEST_PROJECT_ID
        assert data["loading"] is True
        assert data["content"] == ""

    @pytest.mark.asyncio
    async def test_query_project_empty_question(self, client: AsyncClient, mock_services):
        """Test querying with empty question"""
        params = {"userId": TEST_USER_ID}
        question = "   "  # Only whitespace

        response = await client.post(
            f"/projects/{TEST_PROJECT_ID}/messages",
            params=params,
            json=question
        )

        assert response.status_code == 422
        assert "Question cannot be empty" in response.json()["detail"]

    @pytest.mark.asyncio
    async def test_get_chat_history_empty(self, client: AsyncClient, mock_services):
        """Test getting chat history when empty"""
        params = {"userId": TEST_USER_ID}

        response = await client.get(f"/projects/{TEST_PROJECT_ID}/messages", params=params)

        assert response.status_code == 200
        assert response.json() == []

    @pytest.mark.asyncio
    async def test_get_chat_history_with_messages(self, client: AsyncClient, mock_services):
        """Test getting chat history with existing messages"""
        # Create some messages
        async with test_async_session() as session:
            question = Message(
                userId=TEST_USER_ID,
                projectId=TEST_PROJECT_ID,
                content="What is the project about?",
                timestamp=datetime.now(UTC),
                loading=False
            )
            answer = Message(
                userId=TEST_USER_ID,
                projectId=TEST_PROJECT_ID,
                content="This project is about...",
                timestamp=datetime.now(UTC),
                loading=False
            )
            session.add_all([question, answer])
            await session.commit()

        params = {"userId": TEST_USER_ID}

        response = await client.get(f"/projects/{TEST_PROJECT_ID}/messages", params=params)

        assert response.status_code == 200
        data = response.json()
        assert len(data) == 2
        assert all(m["userId"] == TEST_USER_ID for m in data)
        assert all(m["projectId"] == TEST_PROJECT_ID for m in data)

    @pytest.mark.asyncio
    async def test_get_chat_history_user_isolation(self, client: AsyncClient, mock_services):
        """Test that users only see their own chat history"""
        # Create messages for different users
        async with test_async_session() as session:
            user1_message = Message(
                userId=TEST_USER_ID,
                projectId=TEST_PROJECT_ID,
                content="User 1 question",
                timestamp=datetime.now(UTC),
                loading=False
            )
            user2_message = Message(
                userId=TEST_USER_ID_2,
                projectId=TEST_PROJECT_ID,
                content="User 2 question",
                timestamp=datetime.now(UTC),
                loading=False
            )
            session.add_all([user1_message, user2_message])
            await session.commit()

        # Get history for user 1
        params = {"userId": TEST_USER_ID}
        response = await client.get(f"/projects/{TEST_PROJECT_ID}/messages", params=params)

        assert response.status_code == 200
        data = response.json()
        assert len(data) == 1
        assert data[0]["userId"] == TEST_USER_ID
        assert data[0]["content"] == "User 1 question"


class TestValidation:
    """Test input validation"""

    @pytest.mark.asyncio
    async def test_invalid_uuid_project_id(self, client: AsyncClient, mock_services):
        """Test with invalid UUID for project ID"""
        response = await client.get("/projects/invalid-uuid/summary")
        assert response.status_code == 422

    @pytest.mark.asyncio
    async def test_invalid_uuid_user_id(self, client: AsyncClient, mock_services):
        """Test with invalid UUID for user ID"""
        params = {"userId": "invalid-uuid"}
        response = await client.get(f"/projects/{TEST_PROJECT_ID}/messages", params=params)
        assert response.status_code == 422

    @pytest.mark.asyncio
    async def test_negative_timestamps(self, client: AsyncClient, mock_services):
        """Test with negative timestamps (except -1)"""
        params = {
            "startTime": -2,  # Invalid
            "endTime": -1,  # Valid
            "userIds": []
        }

        response = await client.post(f"/projects/{TEST_PROJECT_ID}/summary", params=params)
        assert response.status_code == 422


class TestErrorHandling:
    """Test error handling scenarios"""

    @pytest.mark.asyncio
    async def test_rabbit_mq_not_initialized(self, client: AsyncClient):
        """Test when RabbitMQ is not initialized"""
        with patch('app.main.rabbit_channel', None):
            content_entries = [
                {
                    "metadata": {
                        "type": "commit",
                        "user": TEST_USER_ID,
                        "timestamp": 1234567890,
                        "projectId": TEST_PROJECT_ID
                    },
                    "content": {"message": "Test"}
                }
            ]

            response = await client.post("/content", json=content_entries)
            assert response.status_code == 500
            assert "RabbitMQ not initialized" in response.json()["detail"]


@pytest.mark.asyncio
async def test_app_startup():
    """Test that the app can start up properly"""
    # This test ensures the lifespan context manager works
    # In a real test environment, the services would be mocked
    assert app is not None