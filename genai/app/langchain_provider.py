from langchain.chains.summarize import load_summarize_chain
from langchain.chat_models import ChatOllama
from langchain.vectorstores import Weaviate as LCWeaviate
from langchain.chains import RetrievalQA
from weaviate_client import client, COLLECTION_NAME, get_entries

llm = ChatOllama(temperature=0)

def summarize_entries(project_id: str, start: int, end: int) -> str:
    # todo
    return ""

def answer_question(project_id: str, start: int, end: int, question: str) -> str:
    #todo
    return ""
