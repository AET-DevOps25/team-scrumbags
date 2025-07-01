from pydantic import BaseModel, UUID4
from typing import Dict, Any

class Metadata(BaseModel):
    type: str
    user: UUID4
    timestamp: int  # UNIX time
    projectId: UUID4

class ContentEntry(BaseModel):
    metadata: Metadata
    content: Dict[str, Any]