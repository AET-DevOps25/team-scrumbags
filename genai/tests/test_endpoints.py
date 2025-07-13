import asyncio
import json
import os
import pytest
import pytest_asyncio
from datetime import datetime, UTC
from typing import AsyncGenerator
from unittest.mock import patch, AsyncMock, MagicMock
from uuid import uuid4

from httpx import AsyncClient
from sqlalchemy.ext.asyncio import create_async_engine, async_sessionmaker
from sqlalchemy import select, delete

from app.main import app
from app.db import Base, Summary, Message, async_session
from app.models import ContentEntry, Metadata

# Use MySQL for testing - matches CI environment
TEST_DATABASE_URL = os.getenv(
    "MYSQL_URL",
    "mysql+asyncmy://user:password@127.0.0.1:3306/summaries"
)

# Create test engine with different pool settings to avoid conflicts
test_engine = create_async_engine(
    TEST_DATABASE_URL,
    echo=False,
    pool_pre_ping=True,
    pool_recycle=300
)
test_async_session = async_sessionmaker(test_engine, expire_on_commit=False)

# Test data
TEST_PROJECT_ID = str(uuid4())
TEST_USER_ID = str(uuid4())
TEST_USER_ID_2 = str(uuid4())


@pytest_asyncio.fixture(scope="function")
async def setup_database():
    """Setup test database"""
    async with test_engine.begin() as conn:
        # Create all tables
        await conn.run_sync(Base.metadata.create_all)

    yield

    # Clean up data after test
    async with test_async_session() as session:
        await session.execute(delete(Summary))
        await session.execute(delete(Message))
        await session.commit()


@pytest_asyncio.fixture
async def override_db_session(setup_database):
    """Override database session for testing"""

    async def get_test_db():
        async with test_async_session() as session:
            yield session

    app.dependency_overrides[async_session] = get_test_db
    yield
    app.dependency_overrides.clear()


@pytest_asyncio.fixture
async def client(override_db_session) -> AsyncGenerator[AsyncClient, None]:
    """Create test client"""
    async with AsyncClient(base_url="http://testserver") as ac:
        yield ac


@pytest_asyncio.fixture
async def mock_services():
    """Mock external services"""
    with patch('app.main.rabbit_connection') as mock_rabbit_conn, \
            patch('app.main.rabbit_channel') as mock_rabbit_channel, \
            patch('app.weaviate_client.get_entries') as mock_get_entries, \
            patch('app.langchain_provider.summarize_entries') as mock_summarize, \
            patch('app.langchain_provider.answer_question') as mock_answer:
        # Setup mocks
        mock_rabbit_channel.default_exchange.publish = AsyncMock()
        mock_get_entries.return_value = []
        mock_summarize.return_value = {"output_text": "Test summary"}
        mock_answer.return_value = {"result": "Test answer"}

        yield {
            "rabbit_conn": mock_rabbit_conn,
            "rabbit_channel": mock_rabbit_channel,
            "get_entries": mock_get_entries,
            "summarize": mock_summarize,
            "answer": mock_answer
        }


@pytest_asyncio.fixture
async def sample_content_entry():
    """Create sample content entry"""
    return ContentEntry(
        metadata=Metadata(
            type="commit",
            user=TEST_USER_ID,
            timestamp=1234567890,
            projectId=TEST_PROJECT_ID
        ),
        content={"message": "Test commit"}
    )


class TestContentEndpoint:
    @pytest.mark.asyncio
    async def test_post_content_success(self, client: AsyncClient, mock_services, sample_content_entry):
        """Test successful content posting"""
        response = await client.post(
            "/content",
            json=[sample_content_entry.model_dump()]
        )
        assert response.status_code == 200
        data = response.json()
        assert "Published 1 message(s) to queue" in data["status"]

    @pytest.mark.asyncio
    async def test_post_content_invalid_entry(self, client: AsyncClient, mock_services):
        """Test content posting with invalid entry"""
        invalid_entry = {
            "metadata": {"type": "commit"},  # Missing required fields
            "content": {"message": "Test"}
        }
        response = await client.post("/content", json=[invalid_entry])
        assert response.status_code == 422


class TestSummaryEndpoints:
    @pytest.mark.asyncio
    async def test_get_summary_new(self, client: AsyncClient, mock_services):
        """Test getting a new summary"""
        response = await client.post(
            f"/projects/{TEST_PROJECT_ID}/summary?startTime=1000&endTime=2000"
        )
        assert response.status_code == 200
        data = response.json()
        assert data["projectId"] == TEST_PROJECT_ID
        assert data["startTime"] == 1000
        assert data["endTime"] == 2000

    @pytest.mark.asyncio
    async def test_get_summary_invalid_time_range(self, client: AsyncClient, mock_services):
        """Test getting summary with invalid time range"""
        response = await client.post(
            f"/projects/{TEST_PROJECT_ID}/summary?startTime=2000&endTime=1000"
        )
        assert response.status_code == 422

    @pytest.mark.asyncio
    async def test_refresh_summary(self, client: AsyncClient, mock_services):
        """Test refreshing a summary"""
        response = await client.put(
            f"/projects/{TEST_PROJECT_ID}/summary?startTime=1000&endTime=2000"
        )
        assert response.status_code == 201
        data = response.json()
        assert data["projectId"] == TEST_PROJECT_ID

    @pytest.mark.asyncio
    async def test_get_all_summaries(self, client: AsyncClient, mock_services):
        """Test getting all summaries for a project"""
        response = await client.get(f"/projects/{TEST_PROJECT_ID}/summary")
        assert response.status_code == 200
        data = response.json()
        assert isinstance(data, list)


class TestMessageEndpoints:
    @pytest.mark.asyncio
    async def test_query_project_success(self, client: AsyncClient, mock_services):
        """Test querying project successfully"""
        response = await client.post(
            f"/projects/{TEST_PROJECT_ID}/messages?userId={TEST_USER_ID}",
            json="What happened in this project?"
        )
        assert response.status_code == 200
        data = response.json()
        assert data["projectId"] == TEST_PROJECT_ID
        assert data["userId"] == TEST_USER_ID

    @pytest.mark.asyncio
    async def test_query_project_empty_question(self, client: AsyncClient, mock_services):
        """Test querying with empty question"""
        response = await client.post(
            f"/projects/{TEST_PROJECT_ID}/messages?userId={TEST_USER_ID}",
            json=""
        )
        assert response.status_code == 422

    @pytest.mark.asyncio
    async def test_get_chat_history(self, client: AsyncClient, mock_services):
        """Test getting chat history"""
        response = await client.get(
            f"/projects/{TEST_PROJECT_ID}/messages?userId={TEST_USER_ID}"
        )
        assert response.status_code == 200
        data = response.json()
        assert isinstance(data, list)


class TestValidation:
    @pytest.mark.asyncio
    async def test_invalid_uuid_format(self, client: AsyncClient, mock_services):
        """Test with invalid UUID format"""
        response = await client.get("/projects/invalid-uuid/summary")
        assert response.status_code == 422

    @pytest.mark.asyncio
    async def test_negative_timestamp(self, client: AsyncClient, mock_services):
        """Test with negative timestamp (should be allowed as -1 is valid)"""
        response = await client.post(
            f"/projects/{TEST_PROJECT_ID}/summary?startTime=-2&endTime=1000"
        )
        assert response.status_code == 422


@pytest.mark.asyncio
async def test_app_startup():
    """Test that the app can be created without errors"""
    with patch('app.main.init_db') as mock_init_db, \
            patch('app.weaviate_client.init_collection') as mock_init_collection, \
            patch('app.main.connect_robust') as mock_connect, \
            patch('app.langchain_provider.get_embeddings') as mock_embeddings, \
            patch('app.weaviate_client.get_client') as mock_client:
        mock_init_db.return_value = None
        mock_init_collection.return_value = None
        mock_connect.return_value = AsyncMock()
        mock_embeddings.return_value = AsyncMock()
        mock_client.return_value = AsyncMock()

        assert app is not None