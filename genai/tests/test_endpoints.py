import asyncio
import json
import os
import pytest
import pytest_asyncio
from datetime import datetime, UTC
from typing import AsyncGenerator
from unittest.mock import patch, AsyncMock
from uuid import uuid4

from httpx import AsyncClient
from sqlalchemy.ext.asyncio import create_async_engine, async_sessionmaker
from sqlalchemy import select, text

from app.main import app
from app.db import Base, Summary, Message, async_session
from app.models import ContentEntry, Metadata

# Use MySQL for testing - matches CI environment
TEST_DATABASE_URL = os.getenv(
    "MYSQL_URL",
    "mysql+asyncmy://user:password@127.0.0.1:3306/summaries"
)

# Override the database session for testing
test_engine = create_async_engine(TEST_DATABASE_URL, echo=False)
test_async_session = async_sessionmaker(test_engine, expire_on_commit=False)

# Test data
TEST_PROJECT_ID = str(uuid4())
TEST_USER_ID = str(uuid4())
TEST_USER_ID_2 = str(uuid4())


@pytest_asyncio.fixture
async def setup_database():
    async with test_engine.begin() as conn:
        await conn.run_sync(Base.metadata.drop_all)
        await conn.run_sync(Base.metadata.create_all)
    yield
    # (optional cleanup after tests)

@pytest_asyncio.fixture
async def override_db_session(setup_database):
    async def get_test_db():
        async with test_async_session() as session:
            yield session
    app.dependency_overrides[async_session] = get_test_db
    yield
    app.dependency_overrides.clear()

@pytest_asyncio.fixture
async def client(override_db_session) -> AsyncGenerator[AsyncClient, None]:
    async with AsyncClient(base_url="http://testserver") as ac:
        yield ac


@pytest_asyncio.fixture
async def mock_services():
    """Mock external services"""
    with patch('app.main.rabbit_channel') as mock_rabbit, \
            patch('app.weaviate_client.store_entry_async') as mock_weaviate, \
            patch('app.langchain_provider.summarize_entries') as mock_summarize, \
            patch('app.langchain_provider.answer_question') as mock_answer, \
            patch('app.langchain_provider.get_embeddings') as mock_langchain_embeddings, \
            patch('app.weaviate_client.get_embeddings') as mock_weaviate_embeddings, \
            patch('app.weaviate_client.get_client') as mock_weaviate_client:

        # Mock RabbitMQ
        mock_rabbit.return_value = AsyncMock()
        mock_rabbit.default_exchange = AsyncMock()
        mock_rabbit.default_exchange.publish = AsyncMock()

        # Mock Weaviate
        mock_weaviate.return_value = None
        mock_weaviate_client.return_value = AsyncMock()

        # Mock LangChain
        mock_summarize.return_value = {"output_text": "Test summary content"}
        mock_answer.return_value = {"result": "Test answer content"}

        # Mock embeddings in both modules
        mock_langchain_embeddings.return_value = AsyncMock()
        mock_weaviate_embeddings.return_value = AsyncMock()

        yield {
            'rabbit': mock_rabbit,
            'weaviate': mock_weaviate,
            'summarize': mock_summarize,
            'answer': mock_answer,
            'langchain_embeddings': mock_langchain_embeddings,
            'weaviate_embeddings': mock_weaviate_embeddings,
            'weaviate_client': mock_weaviate_client
        }


@pytest_asyncio.fixture
async def sample_content_entry():
    """Create a sample content entry for testing"""
    return ContentEntry(
        metadata=Metadata(
            type="commit",
            user=uuid4(),
            timestamp=1609459200,
            projectId=uuid4()
        ),
        content={"message": "Test commit message", "files": ["test.py"]}
    )


class TestContentEndpoint:

    @pytest.mark.asyncio
    async def test_post_content_success(self, client: AsyncClient, mock_services, sample_content_entry):
        """Test successful content posting"""
        response = await client.post(
            f"{app.url_path_for('post_content')}",
            json=[sample_content_entry.model_dump()],
            headers={"Host": "testserver"}
        )

        assert response.status_code == 200
        data = response.json()
        assert data["status"] == "Published 1 message(s) to queue."

    @pytest.mark.asyncio
    async def test_post_content_invalid_entry(self, client: AsyncClient, mock_services):
        """Test posting content with invalid entry"""
        invalid_entry = {
            "metadata": {
                "type": "commit",
                "user": "invalid-uuid",  # Invalid UUID
                "timestamp": 1609459200,
                "projectId": str(uuid4())
            },
            "content": {"message": "Test"}
        }

        response = await client.post(
            f"{app.url_path_for('post_content')}",
            json=[invalid_entry],
            headers={"Host": "testserver"}
        )
        assert response.status_code == 422


class TestSummaryEndpoints:

    @pytest.mark.asyncio
    async def test_get_summary_new(self, client: AsyncClient, mock_services):
        """Test getting a new summary"""
        response = await client.post(
            f"{app.url_path_for('get_summary', projectId=TEST_PROJECT_ID)}",
            params={
                "startTime": 1609459200,
                "endTime": 1609545600,
                "userIds": [TEST_USER_ID]
            },
            headers={"Host": "testserver"}
        )

        assert response.status_code == 200
        data = response.json()
        assert data["projectId"] == TEST_PROJECT_ID
        assert data["loading"] is True

    @pytest.mark.asyncio
    async def test_get_summary_invalid_time_range(self, client: AsyncClient, mock_services):
        """Test summary with invalid time range"""
        response = await client.post(
            f"{app.url_path_for('get_summary', projectId=TEST_PROJECT_ID)}",
            params={
                "startTime": 1609545600,  # End before start
                "endTime": 1609459200,
                "userIds": []
            },
            headers={"Host": "testserver"}
        )

        assert response.status_code == 422

    @pytest.mark.asyncio
    async def test_refresh_summary(self, client: AsyncClient, mock_services):
        """Test refreshing a summary"""
        response = await client.put(
            f"{app.url_path_for('refresh_summary', projectId=TEST_PROJECT_ID)}",
            params={
                "startTime": 1609459200,
                "endTime": 1609545600,
                "userIds": [TEST_USER_ID]
            },
            headers={"Host": "testserver"}
        )

        assert response.status_code == 201
        data = response.json()
        assert data["projectId"] == TEST_PROJECT_ID

    @pytest.mark.asyncio
    async def test_get_all_summaries(self, client: AsyncClient, mock_services):
        """Test getting all summaries for a project"""
        response = await client.get(
            f"{app.url_path_for('get_summaries', projectId=TEST_PROJECT_ID)}",
            headers={"Host": "testserver"}
        )

        assert response.status_code == 200
        data = response.json()
        assert isinstance(data, list)


class TestMessageEndpoints:

    @pytest.mark.asyncio
    async def test_query_project_success(self, client: AsyncClient, mock_services):
        """Test successful project query"""
        response = await client.post(
            f"{app.url_path_for('query_project', projectId=TEST_PROJECT_ID)}",
            params={"userId": TEST_USER_ID},
            json="What happened in this project?",
            headers={"Host": "testserver"}
        )

        assert response.status_code == 200
        data = response.json()
        assert data["projectId"] == TEST_PROJECT_ID
        assert data["userId"] == TEST_USER_ID
        assert data["loading"] is True

    @pytest.mark.asyncio
    async def test_query_project_empty_question(self, client: AsyncClient, mock_services):
        """Test query with empty question"""
        response = await client.post(
            f"{app.url_path_for('query_project', projectId=TEST_PROJECT_ID)}",
            params={"userId": TEST_USER_ID},
            json="",
            headers={"Host": "testserver"}
        )

        assert response.status_code == 422

    @pytest.mark.asyncio
    async def test_get_chat_history(self, client: AsyncClient, mock_services):
        """Test getting chat history"""
        response = await client.get(
            f"{app.url_path_for('get_chat_history', projectId=TEST_PROJECT_ID)}",
            params={"userId": TEST_USER_ID},
            headers={"Host": "testserver"}
        )

        assert response.status_code == 200
        data = response.json()
        assert isinstance(data, list)


class TestValidation:

    @pytest.mark.asyncio
    async def test_invalid_uuid_format(self, client: AsyncClient, mock_services):
        """Test invalid UUID format"""
        response = await client.post(
            "/projects/invalid-uuid/summary",
            params={
                "startTime": 1609459200,
                "endTime": 1609545600,
                "userIds": []
            },
            headers={"Host": "testserver"}
        )

        assert response.status_code == 422

    @pytest.mark.asyncio
    async def test_negative_timestamp(self, client: AsyncClient, mock_services):
        """Test negative timestamp validation"""
        response = await client.post(
            f"{app.url_path_for('get_summary', projectId=TEST_PROJECT_ID)}",
            params={
                "startTime": -10,  # Invalid negative timestamp
                "endTime": 1609545600,
                "userIds": []
            },
            headers={"Host": "testserver"}
        )

        assert response.status_code == 422

@pytest.mark.asyncio
async def test_app_startup():
    """Test that the app can be created without errors"""
    # Mock the services to avoid initialization issues
    with patch('app.main.init_db') as mock_init_db, \
            patch('app.weaviate_client.init_collection') as mock_init_collection, \
            patch('app.main.connect_robust') as mock_connect, \
            patch('app.weaviate_client.get_embeddings') as mock_embeddings, \
            patch('app.weaviate_client.get_client') as mock_client:

        mock_init_db.return_value = None
        mock_init_collection.return_value = None
        mock_connect.return_value = AsyncMock()
        mock_embeddings.return_value = AsyncMock()
        mock_client.return_value = AsyncMock()

        # In a real test environment, the services would be mocked
        assert app is not None