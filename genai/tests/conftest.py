import asyncio
import pytest

@pytest.fixture(scope="function", autouse=True)
def event_loop():
    """Create a new event loop for each test function."""
    loop = asyncio.new_event_loop()
    asyncio.set_event_loop(loop)
    yield loop
    loop.close()