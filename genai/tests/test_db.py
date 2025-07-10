import pytest

from app import db
from sqlalchemy.ext.asyncio import create_async_engine, async_sessionmaker
import asyncio

@pytest.fixture(autouse=True)
def setup_test_db(monkeypatch):
    engine = create_async_engine("sqlite+aiosqlite:///:memory:", echo=False)
    async_session = async_sessionmaker(engine, expire_on_commit=False)
    monkeypatch.setattr(db, "engine", engine)
    monkeypatch.setattr(db, "async_session", async_session)
    async def init():
        async with engine.begin() as conn:
            await conn.run_sync(db.Base.metadata.create_all)
    asyncio.get_event_loop().run_until_complete(init())
