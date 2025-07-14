import asyncio
import pytest

@pytest.fixture(scope="session", autouse=True)
def event_loop():
    """
    Override pytest-asyncio’s default, to use one loop per session.
    This prevents ‘Future attached to a different loop’ errors.
    """
    loop = asyncio.get_event_loop_policy().new_event_loop()
    asyncio.set_event_loop(loop)
    yield loop
    loop.close()