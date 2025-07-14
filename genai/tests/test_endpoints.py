import asyncio
import json
import os
import pytest
import pytest_asyncio
from datetime import datetime, UTC
from typing import AsyncGenerator
from unittest.mock import patch, AsyncMock, MagicMock
from uuid import uuid4

from httpx import AsyncClient, ASGITransport
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

# Test data - convert to strings for JSON serialization
TEST_PROJECT_ID = str(uuid4())
TEST_USER_ID = str(uuid4())
TEST_USER_ID_2 = str(uuid4())

# Configure pytest-asyncio to use function scope
pytestmark = pytest.mark.asyncio(scope="function")


@pytest_asyncio.fixture(scope="session")
async def test_engine():
    """Create test engine for session"""
    engine = create_async_engine(
        TEST_DATABASE_URL,
        echo=False,
        pool_pre_ping=True,
        pool_recycle=300,
        pool_reset_on_return='rollback'
    )
    yield engine
    await engine.dispose()


@pytest_asyncio.fixture(scope="session")
async def test_session_factory(test_engine):
    """Create session factory for session"""
    return async_sessionmaker(test_engine, expire_on_commit=False)


@pytest_asyncio.fixture(scope="function", autouse=True)
async def setup_database(test_engine):
    """Setup test database for each test"""
    # Create tables
    async with test_engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)

    yield

    # Clean up data after test
    async with test_engine.begin() as conn:
        await conn.run_sync(Base.metadata.drop_all)


@pytest_asyncio.fixture(scope="function")
async def override_db_session(test_session_factory):
    """Override database session for testing"""

    async def get_test_db():
        session = test_session_factory()
        try:
            yield session
        finally:
            await session.close()

    app.dependency_overrides[async_session] = get_test_db
    yield
    app.dependency_overrides.clear()


@pytest_asyncio.fixture(scope="function")
async def client(override_db_session) -> AsyncGenerator[AsyncClient, None]:
    """Create test client"""
    with patch('app.main.rabbit_connection') as mock_rabbit_conn, \
            patch('app.main.rabbit_channel') as mock_rabbit_channel, \
            patch('app.weaviate_client.get_entries') as mock_get_entries, \
            patch('app.langchain_provider.summarize_entries') as mock_summarize, \
            patch('app.langchain_provider.answer_question') as mock_answer:
        # Mock RabbitMQ
        mock_channel = AsyncMock()
        mock_rabbit_channel.return_value = mock_channel
        mock_rabbit_conn.return_value = AsyncMock()

        # Mock Weaviate and LangChain
        mock_get_entries.return_value = []
        mock_summarize.return_value = {"output_text": "Test summary"}
        mock_answer.return_value = {"result": "Test answer"}

        async with AsyncClient(
                transport=ASGITransport(app=app), base_url="http://testserver"
        ) as ac:
            yield ac


@pytest_asyncio.fixture
async def sample_content_entry():
    """Create sample content entry with string UUIDs"""
    return ContentEntry(
        metadata=Metadata(
            type="commit",
            user=TEST_USER_ID,  # Already a string
            timestamp=1640995200,
            projectId=TEST_PROJECT_ID  # Already a string
        ),
        content={
            "message": "Initial commit",
            "files": ["README.md"]
        }
    )


class TestContentEndpoint:
    @pytest.mark.asyncio
    async def test_post_content_success(self, client: AsyncClient, sample_content_entry):
        """Test successful content posting"""
        response = await client.post("/content", json=[sample_content_entry.model_dump()])
        assert response.status_code == 200
        data = response.json()
        assert "status" in data
        assert "1 message(s)" in data["status"]

    @pytest.mark.asyncio
    async def test_post_content_invalid_entry(self, client: AsyncClient):
        """Test posting invalid content entry"""
        invalid_entry = {
            "metadata": {"type": "commit"},  # Missing required fields
            "content": {}
        }
        response = await client.post("/content", json=[invalid_entry])
        assert response.status_code == 422


class TestSummaryEndpoints:
    @pytest.mark.asyncio
    async def test_get_summary_new(self, client: AsyncClient):
        """Test getting a new summary"""
        response = await client.post(
            f"/projects/{TEST_PROJECT_ID}/summary",
            params={"startTime": 1640995200, "endTime": 1641081600}
        )
        assert response.status_code == 200
        data = response.json()
        assert data["projectId"] == TEST_PROJECT_ID
        assert data["loading"] is True

    @pytest.mark.asyncio
    async def test_get_summary_invalid_time_range(self, client: AsyncClient):
        """Test getting summary with invalid time range"""
        response = await client.post(
            f"/projects/{TEST_PROJECT_ID}/summary",
            params={"startTime": 1641081600, "endTime": 1640995200}  # start > end
        )
        assert response.status_code == 422

    @pytest.mark.asyncio
    async def test_refresh_summary(self, client: AsyncClient):
        """Test refreshing an existing summary"""
        response = await client.put(
            f"/projects/{TEST_PROJECT_ID}/summary",
            params={"startTime": 1640995200, "endTime": 1641081600}
        )
        assert response.status_code == 201
        data = response.json()
        assert data["projectId"] == TEST_PROJECT_ID

    @pytest.mark.asyncio
    async def test_get_all_summaries(self, client: AsyncClient):
        """Test getting all summaries for a project"""
        response = await client.get(f"/projects/{TEST_PROJECT_ID}/summary")
        assert response.status_code == 200
        data = response.json()
        assert isinstance(data, list)


class TestMessageEndpoints:
    @pytest.mark.asyncio
    async def test_query_project_success(self, client: AsyncClient):
        """Test successful project query"""
        response = await client.post(
            f"/projects/{TEST_PROJECT_ID}/messages",
            params={"userId": TEST_USER_ID},
            json="What happened in this project?"
        )
        assert response.status_code == 200
        data = response.json()
        assert data["projectId"] == TEST_PROJECT_ID
        assert data["userId"] == TEST_USER_ID

    @pytest.mark.asyncio
    async def test_query_project_empty_question(self, client: AsyncClient):
        """Test project query with empty question"""
        response = await client.post(
            f"/projects/{TEST_PROJECT_ID}/messages",
            params={"userId": TEST_USER_ID},
            json=""
        )
        assert response.status_code == 422

    @pytest.mark.asyncio
    async def test_get_chat_history(self, client: AsyncClient):
        """Test getting chat history"""
        response = await client.get(
            f"/projects/{TEST_PROJECT_ID}/messages",
            params={"userId": TEST_USER_ID}
        )
        assert response.status_code == 200
        data = response.json()
        assert isinstance(data, list)


class TestValidation:
    @pytest.mark.asyncio
    async def test_invalid_uuid_format(self, client: AsyncClient):
        """Test endpoint with invalid UUID format"""
        response = await client.get("/projects/invalid-uuid/summary")
        assert response.status_code == 422

    @pytest.mark.asyncio
    async def test_negative_timestamp(self, client: AsyncClient):
        """Test endpoint with negative timestamp"""
        response = await client.post(
            f"/projects/{TEST_PROJECT_ID}/summary",
            params={"startTime": -2, "endTime": 1641081600}
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