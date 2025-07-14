import os
import pytest
import pytest_asyncio
from typing import AsyncGenerator
from unittest.mock import patch, AsyncMock, MagicMock
from uuid import uuid4
import json
import time

from httpx import AsyncClient, ASGITransport
from sqlalchemy.ext.asyncio import create_async_engine, async_sessionmaker
from sqlalchemy import select

from app.main import app
from app.db import Base, async_session, Summary, Message
from app.models import ContentEntry, Metadata

# Use MySQL for testing - matches CI environment
TEST_DATABASE_URL = os.getenv(
    "MYSQL_URL",
    "mysql+asyncmy://user:password@127.0.0.1:3306/summaries"
)

# Test data
TEST_PROJECT_ID = str(uuid4())
TEST_USER_ID = str(uuid4())
TEST_USER_ID_2 = str(uuid4())
TEST_PROJECT_ID_2 = str(uuid4())


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
async def sample_content_entry():
    """Create sample content entry"""
    return ContentEntry(
        metadata=Metadata(
            type="commit",
            user=TEST_USER_ID,
            timestamp=1640995200,
            projectId=TEST_PROJECT_ID
        ),
        content={
            "message": "Initial commit",
            "files": ["README.md"]
        }
    )


@pytest_asyncio.fixture(loop_scope="session")
async def multiple_content_entries():
    """Create multiple content entries for testing"""
    return [
        ContentEntry(
            metadata=Metadata(
                type="commit",
                user=TEST_USER_ID,
                timestamp=1640995200,
                projectId=TEST_PROJECT_ID
            ),
            content={"message": "Initial commit", "files": ["README.md"]}
        ),
        ContentEntry(
            metadata=Metadata(
                type="pull_request",
                user=TEST_USER_ID_2,
                timestamp=1641081600,
                projectId=TEST_PROJECT_ID
            ),
            content={"title": "Add feature", "description": "New feature implementation"}
        ),
        ContentEntry(
            metadata=Metadata(
                type="message",
                user=TEST_USER_ID,
                timestamp=1641168000,
                projectId=TEST_PROJECT_ID_2
            ),
            content={"text": "Team meeting notes"}
        )
    ]


class TestContentEndpoint:
    @pytest.mark.asyncio(loop_scope="session")
    async def test_post_content_success(self, client: AsyncClient, sample_content_entry):
        """Test successful content posting"""
        response = await client.post("/content", json=[sample_content_entry.model_dump(mode="json")])
        assert response.status_code == 200
        data = response.json()
        assert "status" in data
        assert "1 message(s)" in data["status"]

    @pytest.mark.asyncio(loop_scope="session")
    async def test_post_multiple_content_entries(self, client: AsyncClient, multiple_content_entries):
        """Test posting multiple content entries"""
        entries_json = [entry.model_dump(mode="json") for entry in multiple_content_entries]
        response = await client.post("/content", json=entries_json)
        assert response.status_code == 200
        data = response.json()
        assert "3 message(s)" in data["status"]

    @pytest.mark.asyncio(loop_scope="session")
    async def test_post_content_invalid_entry(self, client: AsyncClient):
        """Test posting invalid content entry"""
        invalid_entry = {
            "metadata": {"type": "commit"},  # Missing required fields
            "content": {}
        }
        response = await client.post("/content", json=[invalid_entry])
        assert response.status_code == 422

    @pytest.mark.asyncio(loop_scope="session")
    async def test_post_content_missing_project_id(self, client: AsyncClient):
        """Test posting content without projectId"""
        invalid_entry = {
            "metadata": {
                "type": "commit",
                "user": TEST_USER_ID,
                "timestamp": 1640995200
                # projectId missing
            },
            "content": {"message": "test"}
        }

        response = await client.post("/content", json=[invalid_entry])
        assert response.status_code == 422

    @pytest.mark.asyncio(loop_scope="session")
    async def test_post_content_empty_list(self, client: AsyncClient):
        """Test posting empty content list"""
        response = await client.post("/content", json=[])
        assert response.status_code == 200
        data = response.json()
        assert "0 message(s)" in data["status"]

    @pytest.mark.asyncio(loop_scope="session")
    async def test_post_content_malformed_json(self, client: AsyncClient):
        """Test posting malformed JSON"""
        response = await client.post("/content", content="invalid json", headers={"Content-Type": "application/json"})
        assert response.status_code == 422

    @pytest.mark.asyncio(loop_scope="session")
    async def test_post_content_rabbitmq_not_initialized(self, client: AsyncClient, sample_content_entry):
        """Test posting content when RabbitMQ is not initialized"""
        with patch('app.main.rabbit_channel', None):
            response = await client.post("/content", json=[sample_content_entry.model_dump(mode="json")])
            assert response.status_code == 500
            data = response.json()
            assert "RabbitMQ not initialized" in data["detail"]


class TestSummaryEndpoints:
    @pytest.mark.asyncio(loop_scope="session")
    async def test_get_summary_new(self, client: AsyncClient):
        """Test getting a new summary"""
        response = await client.post(
            f"/projects/{TEST_PROJECT_ID}/summary",
            params={"startTime": 1640995200, "endTime": 1641081600}
        )
        assert response.status_code == 202  # Should be 202 for loading
        data = response.json()
        assert data["projectId"] == TEST_PROJECT_ID
        assert data["loading"] is True
        assert data["startTime"] == 1640995200
        assert data["endTime"] == 1641081600

    @pytest.mark.asyncio(loop_scope="session")
    async def test_get_summary_with_user_ids(self, client: AsyncClient):
        """Test getting summary with specific user IDs"""
        response = await client.post(
            f"/projects/{TEST_PROJECT_ID}/summary",
            params={
                "startTime": 1640995200,
                "endTime": 1641081600,
                "userIds": [TEST_USER_ID, TEST_USER_ID_2]
            }
        )
        assert response.status_code == 202
        data = response.json()
        assert set(data["userIds"]) == {TEST_USER_ID, TEST_USER_ID_2}

    @pytest.mark.asyncio(loop_scope="session")
    async def test_get_summary_existing(self, client: AsyncClient, test_session_factory):
        """Test getting an existing summary from database"""
        # First create a summary in the database
        async with test_session_factory() as session:
            existing_summary = Summary(
                projectId=TEST_PROJECT_ID,
                startTime=1640995200,
                endTime=1641081600,
                userIds=[TEST_USER_ID],
                summary="Existing test summary",
                loading=False,
                generatedAt=int(time.time() * 1000)
            )
            session.add(existing_summary)
            await session.commit()

        response = await client.post(
            f"/projects/{TEST_PROJECT_ID}/summary",
            params={
                "startTime": 1640995200,
                "endTime": 1641081600,
                "userIds": [TEST_USER_ID]
            }
        )
        assert response.status_code == 200
        data = response.json()
        assert data["summary"] == "Existing test summary"

    @pytest.mark.asyncio(loop_scope="session")
    async def test_get_summary_invalid_time_range(self, client: AsyncClient):
        """Test getting summary with invalid time range"""
        response = await client.post(
            f"/projects/{TEST_PROJECT_ID}/summary",
            params={"startTime": 1641081600, "endTime": 1640995200}  # start > end
        )
        assert response.status_code == 422
        data = response.json()
        assert "startTime must be ≤ endTime" in data["detail"]

    @pytest.mark.asyncio(loop_scope="session")
    async def test_get_summary_default_time_values(self, client: AsyncClient):
        """Test getting summary with default time values"""
        response = await client.post(f"/projects/{TEST_PROJECT_ID}/summary")
        assert response.status_code == 202
        data = response.json()
        assert data["startTime"] == -1
        assert data["endTime"] == -1

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
        assert data["loading"] is True

    @pytest.mark.asyncio(loop_scope="session")
    async def test_refresh_summary_deletes_existing(self, client: AsyncClient, test_session_factory):
        """Test that refresh summary deletes existing summary"""
        # Create existing summary
        async with test_session_factory() as session:
            existing_summary = Summary(
                projectId=TEST_PROJECT_ID,
                startTime=1640995200,
                endTime=1641081600,
                userIds=[TEST_USER_ID],
                summary="Old summary",
                loading=False,
                generatedAt=int(time.time() * 1000)
            )
            session.add(existing_summary)
            await session.commit()
            summary_id = existing_summary.id

        # Refresh summary
        response = await client.put(
            f"/projects/{TEST_PROJECT_ID}/summary",
            params={
                "startTime": 1640995200,
                "endTime": 1641081600,
                "userIds": [TEST_USER_ID]
            }
        )
        assert response.status_code == 201

        # Verify old summary was deleted
        async with test_session_factory() as session:
            result = await session.execute(select(Summary).where(Summary.id == summary_id))
            deleted_summary = result.scalar_one_or_none()
            assert deleted_summary is None

    @pytest.mark.asyncio(loop_scope="session")
    async def test_get_all_summaries(self, client: AsyncClient, test_session_factory):
        """Test getting all summaries for a project"""
        # Create multiple summaries
        async with test_session_factory() as session:
            summaries = [
                Summary(
                    projectId=TEST_PROJECT_ID,
                    startTime=1640995200,
                    endTime=1641081600,
                    userIds=[TEST_USER_ID],
                    summary="Summary 1",
                    loading=False,
                    generatedAt=int(time.time() * 1000)
                ),
                Summary(
                    projectId=TEST_PROJECT_ID,
                    startTime=1641081600,
                    endTime=1641168000,
                    userIds=[TEST_USER_ID_2],
                    summary="Summary 2",
                    loading=True,
                    generatedAt=int(time.time() * 1000)
                )
            ]
            session.add_all(summaries)
            await session.commit()

        response = await client.get(f"/projects/{TEST_PROJECT_ID}/summary")
        assert response.status_code == 200
        data = response.json()
        assert isinstance(data, list)
        assert len(data) >= 2

    @pytest.mark.asyncio(loop_scope="session")
    async def test_get_summaries_empty_project(self, client: AsyncClient):
        """Test getting summaries for project with no summaries"""
        new_project_id = str(uuid4())
        response = await client.get(f"/projects/{new_project_id}/summary")
        assert response.status_code == 200
        data = response.json()
        assert data == []

    @pytest.mark.asyncio(loop_scope="session")
    async def test_get_summary_by_id(self, client: AsyncClient, test_session_factory):
        """Test getting a summary by its ID"""
        # Create a summary
        async with test_session_factory() as session:
            summary = Summary(
                projectId=TEST_PROJECT_ID,
                startTime=1640995200,
                endTime=1641081600,
                userIds=[TEST_USER_ID],
                summary="Test summary",
                loading=False,
                generatedAt=int(time.time() * 1000)
            )
            session.add(summary)
            await session.commit()
            summary_id = summary.id

        response = await client.get(f"/projects/{TEST_PROJECT_ID}/summary/{summary_id}")
        assert response.status_code == 200
        data = response.json()
        assert data["summary"] == "Test summary"
        assert data["id"] == summary_id

    @pytest.mark.asyncio(loop_scope="session")
    async def test_get_summary_by_id_not_found(self, client: AsyncClient):
        """Test getting a non-existent summary by ID"""
        response = await client.get(f"/projects/{TEST_PROJECT_ID}/summary/99999")
        assert response.status_code == 404
        data = response.json()
        assert "not found" in data["detail"]


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
        assert isinstance(data, list)
        assert len(data) == 2  # question and answer

        question, answer = data
        assert question["projectId"] == TEST_PROJECT_ID
        assert question["userId"] == TEST_USER_ID
        assert question["isGenerated"] is False
        assert answer["isGenerated"] is True
        assert answer["loading"] is True

    @pytest.mark.asyncio(loop_scope="session")
    async def test_query_project_empty_question(self, client: AsyncClient):
        """Test project query with empty question"""
        response = await client.post(
            f"/projects/{TEST_PROJECT_ID}/messages",
            params={"userId": TEST_USER_ID},
            json=""
        )
        assert response.status_code == 422
        data = response.json()
        assert "Question cannot be empty" in data["detail"]

    @pytest.mark.asyncio(loop_scope="session")
    async def test_query_project_whitespace_question(self, client: AsyncClient):
        """Test project query with whitespace-only question"""
        response = await client.post(
            f"/projects/{TEST_PROJECT_ID}/messages",
            params={"userId": TEST_USER_ID},
            json="   \n\t   "
        )
        assert response.status_code == 422

    @pytest.mark.asyncio(loop_scope="session")
    async def test_query_project_long_question(self, client: AsyncClient):
        """Test project query with very long question"""
        long_question = "What happened? " * 1000
        response = await client.post(
            f"/projects/{TEST_PROJECT_ID}/messages",
            params={"userId": TEST_USER_ID},
            json=long_question
        )
        assert response.status_code == 200

    @pytest.mark.asyncio(loop_scope="session")
    async def test_get_chat_history(self, client: AsyncClient, test_session_factory):
        """Test getting chat history"""
        # Create some messages
        async with test_session_factory() as session:
            messages = [
                Message(
                    projectId=TEST_PROJECT_ID,
                    userId=TEST_USER_ID,
                    isGenerated=False,
                    content="What is the project status?",
                    timestamp=int(time.time() * 1000),
                    loading=False
                ),
                Message(
                    projectId=TEST_PROJECT_ID,
                    userId=TEST_USER_ID,
                    isGenerated=True,
                    content="The project is on track.",
                    timestamp=int(time.time() * 1000),
                    loading=False
                )
            ]
            session.add_all(messages)
            await session.commit()

        response = await client.get(
            f"/projects/{TEST_PROJECT_ID}/messages",
            params={"userId": TEST_USER_ID}
        )
        assert response.status_code == 200
        data = response.json()
        assert isinstance(data, list)
        assert len(data) >= 2

    @pytest.mark.asyncio(loop_scope="session")
    async def test_get_chat_history_empty(self, client: AsyncClient):
        """Test getting chat history for user with no messages"""
        new_user_id = str(uuid4())
        response = await client.get(
            f"/projects/{TEST_PROJECT_ID}/messages",
            params={"userId": new_user_id}
        )
        assert response.status_code == 200
        data = response.json()
        assert data == []

    @pytest.mark.asyncio(loop_scope="session")
    async def test_get_chat_history_different_users(self, client: AsyncClient, test_session_factory):
        """Test that chat history is user-specific"""
        # Create messages for different users
        async with test_session_factory() as session:
            messages = [
                Message(
                    projectId=TEST_PROJECT_ID,
                    userId=TEST_USER_ID,
                    isGenerated=False,
                    content="User 1 message",
                    timestamp=int(time.time() * 1000),
                    loading=False
                ),
                Message(
                    projectId=TEST_PROJECT_ID,
                    userId=TEST_USER_ID_2,
                    isGenerated=False,
                    content="User 2 message",
                    timestamp=int(time.time() * 1000),
                    loading=False
                )
            ]
            session.add_all(messages)
            await session.commit()

        # Get history for user 1
        response1 = await client.get(
            f"/projects/{TEST_PROJECT_ID}/messages",
            params={"userId": TEST_USER_ID}
        )
        data1 = response1.json()

        # Get history for user 2
        response2 = await client.get(
            f"/projects/{TEST_PROJECT_ID}/messages",
            params={"userId": TEST_USER_ID_2}
        )
        data2 = response2.json()

        # Verify separation
        user1_messages = [msg for msg in data1 if msg["content"] == "User 1 message"]
        user2_messages = [msg for msg in data2 if msg["content"] == "User 2 message"]

        assert len(user1_messages) >= 1
        assert len(user2_messages) >= 1


class TestValidation:
    @pytest.mark.asyncio(loop_scope="session")
    async def test_invalid_uuid_format(self, client: AsyncClient):
        """Test endpoint with invalid UUID format"""
        response = await client.get("/projects/invalid-uuid/summary")
        assert response.status_code == 422

    @pytest.mark.asyncio(loop_scope="session")
    async def test_invalid_project_id_in_messages(self, client: AsyncClient):
        """Test messages endpoint with invalid project UUID"""
        response = await client.get(
            "/projects/not-a-uuid/messages",
            params={"userId": TEST_USER_ID}
        )
        assert response.status_code == 422

    @pytest.mark.asyncio(loop_scope="session")
    async def test_invalid_user_id_in_query(self, client: AsyncClient):
        """Test query endpoint with invalid user UUID"""
        response = await client.post(
            f"/projects/{TEST_PROJECT_ID}/messages",
            params={"userId": "not-a-uuid"},
            json="Test question"
        )
        assert response.status_code == 422

    @pytest.mark.asyncio(loop_scope="session")
    async def test_negative_timestamp(self, client: AsyncClient):
        """Test endpoint with negative timestamp (should be rejected)"""
        response = await client.post(
            f"/projects/{TEST_PROJECT_ID}/summary",
            params={"startTime": -2, "endTime": 1641081600}
        )
        assert response.status_code == 422

    @pytest.mark.asyncio(loop_scope="session")
    async def test_boundary_timestamp_values(self, client: AsyncClient):
        """Test endpoint with boundary timestamp values"""
        # Test with -1 (allowed)
        response = await client.post(
            f"/projects/{TEST_PROJECT_ID}/summary",
            params={"startTime": -1, "endTime": -1}
        )
        assert response.status_code == 202

        # Test with 0 (allowed)
        response = await client.post(
            f"/projects/{TEST_PROJECT_ID}/summary",
            params={"startTime": 0, "endTime": 0}
        )
        assert response.status_code == 202

    @pytest.mark.asyncio(loop_scope="session")
    async def test_very_large_timestamp(self, client: AsyncClient):
        """Test endpoint with very large timestamp"""
        large_timestamp = 2147483647  # Max 32-bit integer
        response = await client.post(
            f"/projects/{TEST_PROJECT_ID}/summary",
            params={"startTime": 0, "endTime": large_timestamp}
        )
        assert response.status_code == 202


class TestErrorHandling:
    @pytest.mark.asyncio(loop_scope="session")
    async def test_malformed_request_body(self, client: AsyncClient):
        """Test handling of malformed request bodies"""
        # Test invalid JSON for content endpoint
        response = await client.post(
            "/content",
            content="{invalid json}",
            headers={"Content-Type": "application/json"}
        )
        assert response.status_code == 422

    @pytest.mark.asyncio(loop_scope="session")
    async def test_missing_required_parameters(self, client: AsyncClient):
        """Test endpoints with missing required parameters"""
        # Query without userId
        response = await client.post(
            f"/projects/{TEST_PROJECT_ID}/messages",
            json="Test question"
        )
        assert response.status_code == 422

        # Chat history without userId
        response = await client.get(f"/projects/{TEST_PROJECT_ID}/messages")
        assert response.status_code == 422

    @pytest.mark.asyncio(loop_scope="session")
    async def test_content_type_validation(self, client: AsyncClient):
        """Test content type validation"""
        # Send form data instead of JSON
        response = await client.post(
            "/content",
            data={"key": "value"},
            headers={"Content-Type": "application/x-www-form-urlencoded"}
        )
        assert response.status_code == 422


@pytest.mark.asyncio(loop_scope="session")
async def test_concurrent_summary_requests(self, client: AsyncClient):
    """Test concurrent summary requests for same project"""
    import asyncio
    from uuid import uuid4

    # Use different project IDs to avoid constraint violations
    project_ids = [str(uuid4()) for _ in range(3)]

    async def make_request(project_id: str):
        return await client.post(
            f"/projects/{project_id}/summary",
            params={"startTime": 1640995200, "endTime": 1641081600}
        )

    # Make multiple concurrent requests with different project IDs
    responses = await asyncio.gather(*[make_request(pid) for pid in project_ids])

    # All should succeed
    for response in responses:
        assert response.status_code in [200, 202]

    @pytest.mark.asyncio(loop_scope="session")
    async def test_concurrent_message_requests(self, client: AsyncClient):
        """Test concurrent message requests"""
        import asyncio

        async def make_request(question: str):
            return await client.post(
                f"/projects/{TEST_PROJECT_ID}/messages",
                params={"userId": TEST_USER_ID},
                json=question
            )

        questions = [f"Question {i}?" for i in range(3)]
        responses = await asyncio.gather(*[make_request(q) for q in questions])

        # All should succeed
        for response in responses:
            assert response.status_code == 200


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


class TestEdgeCases:
    @pytest.mark.asyncio(loop_scope="session")
    async def test_unicode_content(self, client: AsyncClient):
        """Test handling of unicode content"""
        unicode_entry = ContentEntry(
            metadata=Metadata(
                type="message",
                user=TEST_USER_ID,
                timestamp=1640995200,
                projectId=TEST_PROJECT_ID
            ),
            content={
                "text": "Hello 世界! 🌍 Émojis and ñoñó"
            }
        )

        response = await client.post("/content", json=[unicode_entry.model_dump(mode="json")])
        assert response.status_code == 200

    @pytest.mark.asyncio(loop_scope="session")
    async def test_very_large_content(self, client: AsyncClient):
        """Test handling of very large content"""
        large_content = "x" * 10000  # 10KB content
        large_entry = ContentEntry(
            metadata=Metadata(
                type="document",
                user=TEST_USER_ID,
                timestamp=1640995200,
                projectId=TEST_PROJECT_ID
            ),
            content={"text": large_content}
        )

        response = await client.post("/content", json=[large_entry.model_dump(mode="json")])
        assert response.status_code == 200

    @pytest.mark.asyncio(loop_scope="session")
    async def test_null_user_in_content(self, client: AsyncClient):
        """Test content entry with null user"""
        null_user_entry = ContentEntry(
            metadata=Metadata(
                type="system",
                user=None,
                timestamp=1640995200,
                projectId=TEST_PROJECT_ID
            ),
            content={"message": "System message"}
        )

        response = await client.post("/content", json=[null_user_entry.model_dump(mode="json")])
        assert response.status_code == 200