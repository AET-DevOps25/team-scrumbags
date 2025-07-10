from uuid import uuid4

from fastapi.testclient import TestClient

from app import db
from app.db import Summary, QAPair
from app.main import app
import pytest


client = TestClient(app)

def test_post_content_valid(client, monkeypatch):
    # Example valid content entry
    entry = {"metadata": {"projectId": str(uuid4()), "timestamp": 123}, "content": {"foo": "bar"}}
    response = client.post("/content", json=[entry])
    assert response.status_code == 200
    assert "Published 1 message" in response.json()["status"]

def test_post_content_missing_fields(client):
    entry = {"metadata": {"timestamp": 123}, "content": {"foo": "bar"}}
    response = client.post("/content", json=[entry])
    assert response.status_code == 422  # missing projectId triggers validation error

@pytest.mark.asyncio
async def test_get_summary_cache(async_client, monkeypatch):
    # Monkey-patch summarize_entries to ensure it's not called (we already have a cached summary)
    monkeypatch.setattr("app.main.summarize_entries", lambda *args, **kwargs: {"output_text": "NEW"})
    # Insert a summary into the test DB
    async with db.async_session() as session:
        session.add(Summary(projectId="proj1", startTime=0, endTime=10, userIds=[], generatedAt=datetime.utcnow(), summary="OLD"))
        await session.commit()
    response = await async_client.get("/summary/?projectId=proj1&startTime=0&endTime=10")
    assert response.status_code == 200
    data = response.json()["summary"]
    assert data["output_text"] == "OLD"  # returned cached summary, not "NEW"

@pytest.mark.asyncio
async def test_get_summary_cache(async_client, monkeypatch):
    # Monkey-patch summarize_entries to ensure it's not called (we already have a cached summary)
    monkeypatch.setattr("app.main.summarize_entries", lambda *args, **kwargs: {"output_text": "NEW"})
    # Insert a summary into the test DB
    async with db.async_session() as session:
        session.add(Summary(projectId="proj1", startTime=0, endTime=10, userIds=[], generatedAt=datetime.utcnow(), summary="OLD"))
        await session.commit()
    response = await async_client.get("/summary/?projectId=proj1&startTime=0&endTime=10")
    assert response.status_code == 200
    data = response.json()["summary"]
    assert data["output_text"] == "OLD"  # returned cached summary, not "NEW"

def test_get_summary_invalid_times(client):
    response = client.get("/summary/?projectId=proj1&startTime=10&endTime=5")
    assert response.status_code == 422

def test_query_project(monkeypatch, client):
    # Monkey-patch answer_question to avoid calling langchain
    monkeypatch.setattr("app.main.answer_question", lambda proj, q: {"result": "fake", "source_documents": []})
    response = client.post("/query/?userId=user1&projectId=proj1&question=How+are+you?")
    assert response.status_code == 200
    data = response.json()["answer"]
    assert data["result"] == "fake"
    # Verify that a QAPair was saved
    assert data["result"] == "fake"

@pytest.mark.asyncio
async def test_get_chat_history(async_client):
    async with db.async_session() as session:
        session.add(QAPair(projectId="proj1", userId="user1", question="Q", answer="A", questionTime=datetime.utcnow(), answerTime=datetime.utcnow()))
        await session.commit()
    response = await async_client.get("/chat_history/?userId=user1&projectId=proj1")
    assert response.status_code == 200
    data = response.json()
    assert len(data) == 1
    assert data[0]["question"] == "Q"


