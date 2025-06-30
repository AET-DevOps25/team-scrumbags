import os

from dotenv import load_dotenv
from sqlalchemy import Column, String, Text, Integer, DateTime, UniqueConstraint
from sqlalchemy.ext.asyncio import create_async_engine, async_sessionmaker
from sqlalchemy.orm import declarative_base

load_dotenv()

DATABASE_URL = f"mysql+asyncmy://{os.getenv('MYSQL_USER')}:{os.getenv('MYSQL_PASSWORD')}@mysql:3306/{os.getenv('MYSQL_DATABASE', 'summaries')}"

engine = create_async_engine(DATABASE_URL, echo=True)
async_session = async_sessionmaker(engine, expire_on_commit=False)

Base = declarative_base()


class Summary(Base):
    __tablename__ = "summaries"
    id = Column(Integer, primary_key=True, index=True)
    project_id = Column(String(length=36), index=True)
    start_time = Column(Integer, index=True)
    end_time = Column(Integer, index=True)
    generated_at = Column(DateTime)
    summary = Column(Text)

    __table_args__ = (
        UniqueConstraint("project_id", "start_time", "end_time", name="uq_project_timeframe"),
    )


async def init_db():
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)
