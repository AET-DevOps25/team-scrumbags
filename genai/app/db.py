import os

from dotenv import load_dotenv
from sqlalchemy import Column, String, Text, Integer, BigInteger, UniqueConstraint, JSON, Computed, Boolean
from sqlalchemy.ext.asyncio import create_async_engine, async_sessionmaker
from sqlalchemy.orm import declarative_base

load_dotenv()

DATABASE_URL = (f"mysql+asyncmy://{os.getenv('MYSQL_USER')}:{os.getenv('MYSQL_PASSWORD')}"
                f"@{os.getenv('MYSQL_HOST')}:{os.getenv('MYSQL_PORT', 3306)}/{os.getenv('MYSQL_DB', 'summaries')}")

if os.getenv('MYSQL_URL'):
    DATABASE_URL = os.getenv('MYSQL_URL')

engine = create_async_engine(DATABASE_URL, echo=True)
async_session = async_sessionmaker(engine, expire_on_commit=False)

Base = declarative_base()


class Summary(Base):
    __tablename__ = "summaries"
    id = Column(Integer, primary_key=True, index=True)
    projectId = Column(String(length=36), index=True)
    startTime = Column(BigInteger, index=True)
    endTime = Column(BigInteger, index=True)
    generatedAt = Column(BigInteger)
    summary = Column(Text)
    userIds = Column(JSON, nullable=False, default=list)
    loading = Column(Boolean, nullable=False)

    # MySQL will store the MD5 of the JSON text
    userIdsHash = Column(
        String(32),
        Computed("MD5(userIds)", persisted=True),
        index=True
    )

    __table_args__ = (
        UniqueConstraint("projectId", "startTime", "endTime", "userIdsHash", name="uq_project_timeframe"),
    )


class Message(Base):
    __tablename__ = "messages"
    id = Column(Integer, primary_key=True, index=True)
    projectId = Column(String(length=36), index=True)
    userId = Column(String(length=36), index=True)
    isGenerated = Column(Boolean, nullable=False, default=False)
    content = Column(Text)
    timestamp = Column(BigInteger, index=True)
    loading = Column(Boolean, nullable=False)


async def init_db():
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)
