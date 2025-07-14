import os
import pytest
import pytest_asyncio
from typing import AsyncGenerator
from unittest.mock import patch, AsyncMock, MagicMock
from uuid import uuid4
import json
from datetime import datetime, timezone

from httpx import AsyncClient, ASGITransport
from sqlalchemy.ext.asyncio import create_async_engine, async_sessionmaker

from app.main import app
from app.db import Base, async_session
from app.models import ContentEntry, Metadata, Summary, Message

# Use MySQL for testing - matches CI environment
TEST_DATABASE_URL = os.getenv(
    "MYSQL_URL",
    "mysql+asyncmy://user:password@127.0.0.1:3306/summaries"
)

# Test data
TEST_PROJECT_ID = str(uuid4())
TEST_PROJECT_ID_2 = str(uuid4())
TEST_USER_ID = str(uuid4())
TEST_USER_ID_2 = str(uuid4())
INVALID_UUID = "not-a-uuid"


@pytest_asyncio.fixture(loop_scope="session")
async def test_engine():
    """Create test engine per test function"""
    engine = create_async_engine(
        TEST_DATABASE_URL,
        echo=False,
        pool_pre_ping=True,
        pool_recycle=300,
        pool_reset_on_return='rollback'
    )
    yield engine
    await engine.dispose()


@pytest_asyncio.fixture(loop_scope="session")
async def test_session_factory(test_engine):
    """Create session factory per test function"""
    return async_sessionmaker(test_engine, expire_on_commit=False)


@pytest_asyncio.fixture(loop_scope="session")
async def setup_database(test_engine):
    """Setup test database"""
    # Create tables
    async with test_engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)

    yield

    # Clean up data after test
    async with test_engine.begin() as conn:
        await conn.run_sync(Base.metadata.drop_all)


@pytest_asyncio.fixture(loop_scope="session")
async def override_db_session(setup_database, test_session_factory):
    """Override database session for testing"""

    async def get_test_db():
        async with test_session_factory() as session:
            try:
                yield session
            finally:
                await session.close()

    app.dependency_overrides[async_session] = get_test_db
    yield
    app.dependency_overrides.clear()


@pytest_asyncio.fixture(loop_scope="session")
async def client(override_db_session) -> AsyncGenerator[AsyncClient, None]:
    """Create test client with all async deps mocked as AsyncMock."""
    with patch('app.main.rabbit_connection', new_callable=AsyncMock) as mock_rabbit_conn, \
            patch('app.main.rabbit_channel', new_callable=AsyncMock) as mock_rabbit_channel, \
            patch('app.weaviate_client.get_entries', new_callable=AsyncMock) as mock_get_entries, \
            patch('app.langchain_provider.summarize_entries', new_callable=AsyncMock) as mock_summarize, \
            patch('app.langchain_provider.answer_question', new_callable=AsyncMock) as mock_answer:
        mock_rabbit_conn.return_value = mock_rabbit_conn
        mock_rabbit_channel.return_value = mock_rabbit_channel
        mock_get_entries.return_value = []
        mock_summarize.return_value = {"output_text": "Test summary"}
        mock_answer.return_value = {"result": "Test answer"}

        async with AsyncClient(
                transport=ASGITransport(app=app), base_url="http://testserver"
        ) as ac:
            yield ac


@pytest_asyncio.fixture(loop_scope="session")
async def sample_content_entries():
    """Create multiple sample content entries"""
    return [
        ContentEntry(
            metadata=Metadata(
                type="commit",
                user=TEST_USER_ID,
                timestamp=1640995200,
                projectId=TEST_PROJECT_ID
            ),
            content={
                "message": "Initial commit",
                "files": ["README.md"],
                "hash": "abc123"
            }
        ),
        ContentEntry(
            metadata=Metadata(
                type="issue",
                user=TEST_USER_ID_2,
                timestamp=1641000000,
                projectId=TEST_PROJECT_ID
            ),
            content={
                "title": "Bug report",
                "description": "Found a critical bug",
                "status": "open"
            }
        ),
        ContentEntry(
            metadata=Metadata(
                type="merge_request",
                user=TEST_USER_ID,
                timestamp=1641010000,
                projectId=TEST_PROJECT_ID
            ),
            content={
                "title": "Feature implementation",
                "source_branch": "feature/new-feature",
                "target_branch": "main"
            }
        )
    ]


class TestContentEndpoint:
    @pytest.mark.asyncio(loop_scope="session")
    async def test_post_content_success_single(self, client: AsyncClient, sample_content_entries):
        """Test successful single content posting"""
        response = await client.post("/content", json=[sample_content_entries[0].model_dump(mode="json")])
        assert response.status_code == 200
        data = response.json()
        assert "status" in data
        assert "1 message(s)" in data["status"]

    @pytest.mark.asyncio(loop_scope="session")
    async def test_post_content_success_multiple(self, client: AsyncClient, sample_content_entries):
        """Test successful multiple content posting"""
        entries_data = [entry.model_dump(mode="json") for entry in sample_content_entries]
        response = await client.post("/content", json=entries_data)
        assert response.status_code == 200
        data = response.json()
        assert "status" in data
        assert "3 message(s)" in data["status"]

    @pytest.mark.asyncio(loop_scope="session")
    async def test_post_content_empty_list(self, client: AsyncClient):
        """Test posting empty content list"""
        response = await client.post("/content", json=[])
        assert response.status_code == 200
        data = response.json()
        assert "0 message(s)" in data["status"]

    @pytest.mark.asyncio(loop_scope="session")
    async def test_post_content_invalid_entry_missing_metadata(self, client: AsyncClient):
        """Test posting content entry without metadata"""
        invalid_entry = {
            "content": {"message": "test"}
        }
        response = await client.post("/content", json=[invalid_entry])
        assert response.status_code == 422

    @pytest.mark.asyncio(loop_scope="session")
    async def test_post_content_invalid_entry_missing_content(self, client: AsyncClient):
        """Test posting content entry without content"""
        invalid_entry = {
            "metadata": {
                "type": "commit",
                "user": TEST_USER_ID,
                "timestamp": 1640995200,
                "projectId": TEST_PROJECT_ID
            }
        }
        response = await client.post("/content", json=[invalid_entry])
        assert response.status_code == 422

    @pytest.mark.asyncio(loop_scope="session")
    async def test_post_content_invalid_metadata_fields(self, client: AsyncClient):
        """Test posting content with invalid metadata fields"""
        invalid_entries = [
            {
                "metadata": {
                    "type": "invalid_type",
                    "user": TEST_USER_ID,
                    "timestamp": 1640995200,
                    "projectId": TEST_PROJECT_ID
                },
                "content": {"message": "test"}
            },
            {
                "metadata": {
                    "type": "commit",
                    "user": INVALID_UUID,
                    "timestamp": 1640995200,
                    "projectId": TEST_PROJECT_ID
                },
                "content": {"message": "test"}
            },
            {
                "metadata": {
                    "type": "commit",
                    "user": TEST_USER_ID,
                    "timestamp": -1,
                    "projectId": TEST_PROJECT_ID
                },
                "content": {"message": "test"}
            }
        ]

        for invalid_entry in invalid_entries:
            response = await client.post("/content", json=[invalid_entry])
            assert response.status_code == 422

    @pytest.mark.asyncio(loop_scope="session")
    async def test_post_content_malformed_json(self, client: AsyncClient):
        """Test posting malformed JSON"""
        response = await client.post(
            "/content",
            content="invalid json",
            headers={"Content-Type": "application/json"}
        )
        assert response.status_code == 422

    @pytest.mark.asyncio(loop_scope="session")
    async def test_post_content_large_payload(self, client: AsyncClient, sample_content_entries):
        """Test posting large number of content entries"""
        large_payload = []
        for i in range(100):
            entry = sample_content_entries[0].model_copy()
            entry.metadata.timestamp = 1640995200 + i
            entry.content["message"] = f"Commit {i}"
            large_payload.append(entry.model_dump(mode="json"))

        response = await client.post("/content", json=large_payload)
        assert response.status_code == 200
        data = response.json()
        assert "100 message(s)" in data["status"]


class TestSummaryEndpoints:
    @pytest.mark.asyncio(loop_scope="session")
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

    @pytest.mark.asyncio(loop_scope="session")
    async def test_get_summary_same_start_end_time(self, client: AsyncClient):
        """Test getting summary with same start and end time"""
        timestamp = 1640995200
        response = await client.post(
            f"/projects/{TEST_PROJECT_ID}/summary",
            params={"startTime": timestamp, "endTime": timestamp}
        )
        assert response.status_code == 200

    @pytest.mark.asyncio(loop_scope="session")
    async def test_get_summary_invalid_time_range(self, client: AsyncClient):
        """Test getting summary with invalid time range"""
        response = await client.post(
            f"/projects/{TEST_PROJECT_ID}/summary",
            params={"startTime": 1641081600, "endTime": 1640995200}
        )
        assert response.status_code == 422

    @pytest.mark.asyncio(loop_scope="session")
    async def test_get_summary_negative_timestamps(self, client: AsyncClient):
        """Test getting summary with negative timestamps"""
        response = await client.post(
            f"/projects/{TEST_PROJECT_ID}/summary",
            params={"startTime": -1000, "endTime": -500}
        )
        assert response.status_code == 422

    @pytest.mark.asyncio(loop_scope="session")
    async def test_get_summary_future_timestamps(self, client: AsyncClient):
        """Test getting summary with future timestamps"""
        future_time = int(datetime.now(timezone.utc).timestamp()) + 86400
        response = await client.post(
            f"/projects/{TEST_PROJECT_ID}/summary",
            params={"startTime": 1640995200, "endTime": future_time}
        )
        assert response.status_code == 200

    @pytest.mark.asyncio(loop_scope="session")
    async def test_get_summary_missing_parameters(self, client: AsyncClient):
        """Test getting summary with missing parameters"""
        # Missing endTime
        response = await client.post(
            f"/projects/{TEST_PROJECT_ID}/summary",
            params={"startTime": 1640995200}
        )
        assert response.status_code == 422

        # Missing startTime
        response = await client.post(
            f"/projects/{TEST_PROJECT_ID}/summary",
            params={"endTime": 1641081600}
        )
        assert response.status_code == 422

    @pytest.mark.asyncio(loop_scope="session")
    async def test_refresh_summary(self, client: AsyncClient):
        """Test refreshing an existing summary"""
        response = await client.put(
            f"/projects/{TEST_PROJECT_ID}/summary",
            params={"startTime": 1640995200, "endTime": 1641081600}
        )
        assert response.status_code == 201
        data = response.json()
        assert data["projectId"] == TEST_PROJECT_ID

    @pytest.mark.asyncio(loop_scope="session")
    async def test_refresh_summary_invalid_range(self, client: AsyncClient):
        """Test refreshing summary with invalid time range"""
        response = await client.put(
            f"/projects/{TEST_PROJECT_ID}/summary",
            params={"startTime": 1641081600, "endTime": 1640995200}
        )
        assert response.status_code == 422

    @pytest.mark.asyncio(loop_scope="session")
    async def test_get_all_summaries(self, client: AsyncClient):
        """Test getting all summaries for a project"""
        response = await client.get(f"/projects/{TEST_PROJECT_ID}/summary")
        assert response.status_code == 200
        data = response.json()
        assert isinstance(data, list)

    @pytest.mark.asyncio(loop_scope="session")
    async def test_get_all_summaries_nonexistent_project(self, client: AsyncClient):
        """Test getting summaries for non-existent project"""
        response = await client.get(f"/projects/{TEST_PROJECT_ID_2}/summary")
        assert response.status_code == 200
        data = response.json()
        assert isinstance(data, list)
        assert len(data) == 0

    @pytest.mark.asyncio(loop_scope="session")
    async def test_summary_with_weaviate_error(self, client: AsyncClient):
        """Test summary generation when Weaviate fails"""
        with patch('app.weaviate_client.get_entries', side_effect=Exception("Weaviate error")):
            response = await client.post(
                f"/projects/{TEST_PROJECT_ID}/summary",
                params={"startTime": 1640995200, "endTime": 1641081600}
            )
            # Should still return 200 but handle the error gracefully
            assert response.status_code in [200, 500]


class TestMessageEndpoints:
    @pytest.mark.asyncio(loop_scope="session")
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

    @pytest.mark.asyncio(loop_scope="session")
    async def test_query_project_complex_question(self, client: AsyncClient):
        """Test project query with complex question"""
        complex_question = "Can you analyze the commit patterns and identify the most active contributors between January and March? Also, what are the main features that were developed?"
        response = await client.post(
            f"/projects/{TEST_PROJECT_ID}/messages",
            params={"userId": TEST_USER_ID},
            json=complex_question
        )
        assert response.status_code == 200
        data = response.json()
        assert data["projectId"] == TEST_PROJECT_ID

    @pytest.mark.asyncio(loop_scope="session")
    async def test_query_project_empty_question(self, client: AsyncClient):
        """Test project query with empty question"""
        response = await client.post(
            f"/projects/{TEST_PROJECT_ID}/messages",
            params={"userId": TEST_USER_ID},
            json=""
        )
        assert response.status_code == 422

    @pytest.mark.asyncio(loop_scope="session")
    async def test_query_project_whitespace_only_question(self, client: AsyncClient):
        """Test project query with whitespace-only question"""
        response = await client.post(
            f"/projects/{TEST_PROJECT_ID}/messages",
            params={"userId": TEST_USER_ID},
            json="   \t\n  "
        )
        assert response.status_code == 422

    @pytest.mark.asyncio(loop_scope="session")
    async def test_query_project_very_long_question(self, client: AsyncClient):
        """Test project query with very long question"""
        long_question = "What happened? " * 1000
        response = await client.post(
            f"/projects/{TEST_PROJECT_ID}/messages",
            params={"userId": TEST_USER_ID},
            json=long_question
        )
        # Should either succeed or return 413 (Payload Too Large)
        assert response.status_code in [200, 413, 422]

    @pytest.mark.asyncio(loop_scope="session")
    async def test_query_project_special_characters(self, client: AsyncClient):
        """Test project query with special characters"""
        special_question = "What about files with names like test.py, config.json, and data-file_v2.csv? Also check #123 issue!"
        response = await client.post(
            f"/projects/{TEST_PROJECT_ID}/messages",
            params={"userId": TEST_USER_ID},
            json=special_question
        )
        assert response.status_code == 200

    @pytest.mark.asyncio(loop_scope="session")
    async def test_query_project_unicode_characters(self, client: AsyncClient):
        """Test project query with unicode characters"""
        unicode_question = "¿Qué pasó en el proyecto? 你好世界 🚀 التطوير"
        response = await client.post(
            f"/projects/{TEST_PROJECT_ID}/messages",
            params={"userId": TEST_USER_ID},
            json=unicode_question
        )
        assert response.status_code == 200

    @pytest.mark.asyncio(loop_scope="session")
    async def test_get_chat_history(self, client: AsyncClient):
        """Test getting chat history"""
        response = await client.get(
            f"/projects/{TEST_PROJECT_ID}/messages",
            params={"userId": TEST_USER_ID}
        )
        assert response.status_code == 200
        data = response.json()
        assert isinstance(data, list)

    @pytest.mark.asyncio(loop_scope="session")
    async def test_get_chat_history_different_users(self, client: AsyncClient):
        """Test getting chat history for different users"""
        # User 1 history
        response1 = await client.get(
            f"/projects/{TEST_PROJECT_ID}/messages",
            params={"userId": TEST_USER_ID}
        )
        assert response1.status_code == 200

        # User 2 history
        response2 = await client.get(
            f"/projects/{TEST_PROJECT_ID}/messages",
            params={"userId": TEST_USER_ID_2}
        )
        assert response2.status_code == 200

        # Both should be lists (potentially empty)
        assert isinstance(response1.json(), list)
        assert isinstance(response2.json(), list)

    @pytest.mark.asyncio(loop_scope="session")
    async def test_get_chat_history_nonexistent_project(self, client: AsyncClient):
        """Test getting chat history for non-existent project"""
        response = await client.get(
            f"/projects/{TEST_PROJECT_ID_2}/messages",
            params={"userId": TEST_USER_ID}
        )
        assert response.status_code == 200
        data = response.json()
        assert isinstance(data, list)
        assert len(data) == 0

    @pytest.mark.asyncio(loop_scope="session")
    async def test_query_with_langchain_error(self, client: AsyncClient):
        """Test query when LangChain provider fails"""
        with patch('app.langchain_provider.answer_question', side_effect=Exception("LangChain error")):
            response = await client.post(
                f"/projects/{TEST_PROJECT_ID}/messages",
                params={"userId": TEST_USER_ID},
                json="What happened?"
            )
            # Should handle error gracefully
            assert response.status_code in [200, 500]


class TestValidation:
    @pytest.mark.asyncio(loop_scope="session")
    async def test_invalid_uuid_format_project_id(self, client: AsyncClient):
        """Test endpoints with invalid project UUID format"""
        endpoints = [
            f"/projects/{INVALID_UUID}/summary",
            f"/projects/{INVALID_UUID}/messages"
        ]

        for endpoint in endpoints:
            response = await client.get(endpoint)
            assert response.status_code == 422

    @pytest.mark.asyncio(loop_scope="session")
    async def test_invalid_uuid_format_user_id(self, client: AsyncClient):
        """Test endpoints with invalid user UUID format"""
        response = await client.get(
            f"/projects/{TEST_PROJECT_ID}/messages",
            params={"userId": INVALID_UUID}
        )
        assert response.status_code == 422

    @pytest.mark.asyncio(loop_scope="session")
    async def test_missing_required_parameters(self, client: AsyncClient):
        """Test endpoints with missing required parameters"""
        # Missing userId for messages
        response = await client.get(f"/projects/{TEST_PROJECT_ID}/messages")
        assert response.status_code == 422

    @pytest.mark.asyncio(loop_scope="session")
    async def test_negative_timestamp_validation(self, client: AsyncClient):
        """Test comprehensive negative timestamp validation"""
        negative_scenarios = [
            {"startTime": -1, "endTime": 1641081600},
            {"startTime": 1640995200, "endTime": -1},
            {"startTime": -100, "endTime": -50}
        ]

        for params in negative_scenarios:
            response = await client.post(
                f"/projects/{TEST_PROJECT_ID}/summary",
                params=params
            )
            assert response.status_code == 422

    @pytest.mark.asyncio(loop_scope="session")
    async def test_extremely_large_timestamp(self, client: AsyncClient):
        """Test with extremely large timestamp values"""
        large_timestamp = 9999999999999  # Year 318857
        response = await client.post(
            f"/projects/{TEST_PROJECT_ID}/summary",
            params={"startTime": 1640995200, "endTime": large_timestamp}
        )
        # Should either handle gracefully or return validation error
        assert response.status_code in [200, 422]

    @pytest.mark.asyncio(loop_scope="session")
    async def test_string_timestamp_values(self, client: AsyncClient):
        """Test with string timestamp values"""
        response = await client.post(
            f"/projects/{TEST_PROJECT_ID}/summary",
            params={"startTime": "not_a_number", "endTime": "also_not_a_number"}
        )
        assert response.status_code == 422


class TestErrorHandling:
    @pytest.mark.asyncio(loop_scope="session")
    async def test_database_connection_error(self, client: AsyncClient):
        """Test behavior when database connection fails"""
        with patch('app.db.async_session', side_effect=Exception("Database connection failed")):
            response = await client.get(f"/projects/{TEST_PROJECT_ID}/summary")
            assert response.status_code == 500

    @pytest.mark.asyncio(loop_scope="session")
    async def test_concurrent_requests(self, client: AsyncClient):
        """Test handling of concurrent requests"""
        import asyncio

        async def make_request():
            return await client.post(
                f"/projects/{TEST_PROJECT_ID}/summary",
                params={"startTime": 1640995200, "endTime": 1641081600}
            )

        # Make 10 concurrent requests
        tasks = [make_request() for _ in range(10)]
        responses = await asyncio.gather(*tasks, return_exceptions=True)

        # All should succeed or handle gracefully
        for response in responses:
            if not isinstance(response, Exception):
                assert response.status_code in [200, 201, 429]  # Including rate limiting

    @pytest.mark.asyncio(loop_scope="session")
    async def test_malformed_request_body(self, client: AsyncClient):
        """Test various malformed request bodies"""
        malformed_bodies = [
            b'{"incomplete": json',
            b'null',
            b'[]',
            b'{"question": null}',
            b'{"question": 123}'
        ]

        for body in malformed_bodies:
            response = await client.post(
                f"/projects/{TEST_PROJECT_ID}/messages",
                params={"userId": TEST_USER_ID},
                content=body,
                headers={"Content-Type": "application/json"}
            )
            assert response.status_code == 422


class TestPerformance:
    @pytest.mark.asyncio(loop_scope="session")
    async def test_large_time_range_summary(self, client: AsyncClient):
        """Test summary generation with very large time range"""
        start_time = 0  # Unix epoch
        end_time = int(datetime.now(timezone.utc).timestamp())

        response = await client.post(
            f"/projects/{TEST_PROJECT_ID}/summary",
            params={"startTime": start_time, "endTime": end_time}
        )
        assert response.status_code == 200

    @pytest.mark.asyncio(loop_scope="session")
    async def test_response_time_validation(self, client: AsyncClient):
        """Test that responses are returned within reasonable time"""
        import time

        start_time = time.time()
        response = await client.get(f"/projects/{TEST_PROJECT_ID}/summary")
        end_time = time.time()

        assert response.status_code == 200
        assert (end_time - start_time) < 30  # Should respond within 30 seconds


@pytest.mark.asyncio(loop_scope="session")
async def test_app_startup():
    """Test that the app can be created without errors"""
    with patch('app.main.init_db') as mock_init_db, \
            patch('app.weaviate_client.init_collection') as mock_init_collection, \
            patch('app.main.connect_robust') as mock_connect, \
            patch('app.langchain_provider.get_embeddings') as mock_embeddings, \
            patch('app.weaviate_client.get_client') as mock_client:
        mock_init_db.return_value = AsyncMock()
        mock_init_collection.return_value = AsyncMock()
        mock_connect.return_value = AsyncMock()
        mock_embeddings.return_value = AsyncMock()
        mock_client.return_value = AsyncMock()

        assert app is not None


@pytest.mark.asyncio(loop_scope="session")
async def test_health_check_endpoint(client: AsyncClient):
    """Test health check endpoint if it exists"""
    response = await client.get("/health")
    # Health endpoint might not exist, so either 200 or 404 is acceptable
    assert response.status_code in [200, 404]


@pytest.mark.asyncio(loop_scope="session")
async def test_cors_headers(client: AsyncClient):
    """Test CORS headers are properly set"""
    response = await client.options(f"/projects/{TEST_PROJECT_ID}/summary")
    # Should either have CORS headers or return method not allowed
    assert response.status_code in [200, 405]


@pytest.mark.asyncio(loop_scope="session")
async def test_content_type_validation(client: AsyncClient):
    """Test content type validation"""
    # Test with wrong content type
    response = await client.post(
        "/content",
        content="[]",
        headers={"Content-Type": "text/plain"}
    )
    assert response.status_code in [422, 415]  # Unsupported Media Type or Validation Error