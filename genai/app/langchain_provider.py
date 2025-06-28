from langchain.chains.summarize import load_summarize_chain
from langchain_ollama import OllamaLLM
from langchain_community.vectorstores import Weaviate as LCWeaviate
from langchain.chains import RetrievalQA
from langchain.schema import Document

from app import weaviate_client as wc

llm = OllamaLLM(model="llama3.2",
                base_url="http://ollama:11434")

def summarize_entries(project_id: str, start: int, end: int) -> str:
    #raw content strings
    contents = wc.get_entries(project_id, start, end)
    if not contents:
        return "No entries found for the given parameters."

    # LangChain Documents
    docs = [Document(page_content=txt) for txt in contents]

    chain = load_summarize_chain(llm, chain_type="map_reduce")

    summary = chain.invoke(docs)
    return summary

def answer_question(project_id: str, start: int, end: int, question: str) -> str:
    #todo
    return "answer dummy"
