from langchain.chains.summarize import load_summarize_chain
from langchain_community.chat_models import ChatOllama
from langchain_community.vectorstores import Weaviate as LCWeaviate
from langchain.chains import RetrievalQA
from . import weaviate_client as wc

llm = ChatOllama(temperature=0)

def summarize_entries(project_id: str, start: int, end: int) -> str:
    # todo
    return ""

def answer_question(project_id: str, start: int, end: int, question: str) -> str:
    #todo
    return ""
