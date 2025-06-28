from langchain.chains.summarize import load_summarize_chain
from langchain_ollama import ChatOllama
from langchain_community.vectorstores import Weaviate as LCWeaviate
from langchain.chains import RetrievalQA
from app import weaviate_client as wc

llm = ChatOllama(temperature=0.0, model="llama3")

def summarize_entries(project_id: str, start: int, end: int) -> str:
    # todo
    return "summary dummy"

def answer_question(project_id: str, start: int, end: int, question: str) -> str:
    #todo
    return "answer dummy"
