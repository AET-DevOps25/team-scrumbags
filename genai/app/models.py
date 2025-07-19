from typing import Dict, Any, Union

from pydantic import BaseModel, UUID4


class Metadata(BaseModel):
    type: str
    user: Union[UUID4, None]
    timestamp: int  # UNIX time
    projectId: UUID4


class ContentEntry(BaseModel):
    metadata: Metadata
    content: Dict[str, Any]
