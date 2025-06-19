from pydantic import BaseModel, Field
from uuid import UUID
from typing import Any, Dict

class Metadata(BaseModel):
    type: str
    user: UUID
    timestamp: int
    projectId: UUID

class Item(BaseModel):
    metadata: Metadata
    content: Dict[str, Any] = Field(...)