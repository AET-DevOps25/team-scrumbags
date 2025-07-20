import os

import httpx
from dotenv import load_dotenv
from langchain.chains import RetrievalQA
from langchain.chains.summarize import load_summarize_chain
from langchain.prompts import PromptTemplate
from langchain.schema import Document
from langchain_nomic import NomicEmbeddings
from langchain_ollama import OllamaLLM, ChatOllama
from langchain_weaviate.vectorstores import WeaviateVectorStore
from pydantic import SecretStr

from app import weaviate_client as wc

load_dotenv()

OLLAMA_CLOUD_URL = os.getenv("OLLAMA_CLOUD_URL", "https://gpu.aet.cit.tum.de/ollama")
OLLAMA_LOCAL_URL = os.getenv("OLLAMA_LOCAL_URL", "http://ollama:11434")
NOMIC_API_KEY = os.getenv("NOMIC_API_KEY", None)

# Initialize embeddings lazily
_embeddings = None


def get_embeddings():
    global _embeddings
    if _embeddings is None:
        _embeddings = NomicEmbeddings(
            model="nomic-embed-text-v1.5",
            dimensionality=256,
            nomic_api_key=NOMIC_API_KEY
        )
    return _embeddings


def is_reachable(url: str) -> bool:
    try:
        response = httpx.get(f"{url}/tags", timeout=3)
        return response.status_code == 200
    except httpx.RequestError:
        return False


def is_local():
    if OLLAMA_LOCAL_URL and is_reachable(OLLAMA_LOCAL_URL):
        return True
    elif OLLAMA_CLOUD_URL and is_reachable(OLLAMA_CLOUD_URL):
        return False
    else:
        raise RuntimeError("Neither local nor cloud Ollama server is reachable.")


if is_local():
    llm = OllamaLLM(model="llama3.2",
                    base_url=os.getenv("OLLAMA_LOCAL_URL", "http://ollama:11434"),
                    temperature=0.1)
else:
    TOKEN = SecretStr(os.getenv("OPEN_WEBUI_BEARER"))

    llm = ChatOllama(
        model="llama3.3:latest",
        base_url=OLLAMA_CLOUD_URL,
        client_kwargs={"headers": {
            "Authorization": f"Bearer {TOKEN.get_secret_value()}"
        }}
    )


async def summarize_entries(projectId: str, start: int, end: int, userIds: list[str]):
    # raw content strings
    contents = wc.get_entries(projectId, start, end)

    if not contents:
        return {"output_text": "No content found for the given parameters. Error generating summary."}

    prompt = PromptTemplate(
        template="""You are a summarizer of source control information (pull requests, commits, branches, etc.),
        transcripts of meetings with assigned speakers, and messages between team members over
        collaboration / messaging platforms.

        Below is a list of IDs that correspond to users you should primarily focus on when summarizing.
        Do not expose the IDs in the summary, but use them to focus on the relevant users:

        {users}

        Given the following documents containing
        information about the project dealings, produce a
        detailed summary in Markdown format. Use headings, bullet points, and code
        blocks where appropriate. Do not use any other formatting than Markdown.
        Use a title that accurately reflects the content.
        Create an introduction paragraph that provides an overview of the topic.
        Create bullet points that list the key points of the text, where appropriate.
        Create a conclusion paragraph that summarizes the key points of the text.

        Below is the content to summarize:
        //

        {input_documents}

        //

        Use the format given below for summarizing:
        ### [Summary Name]:
        [Summary Content]""".strip(),
        input_variables=["input_documents", "users"],
    )

    # LangChain Documents
    docs = [Document(id=str(obj.uuid),
                     metadata={
                         "type": obj.properties.get("type", "unknown"),
                         "user": obj.properties.get("user", "unknown"),
                         "timestamp": obj.properties["timestamp"],
                         "projectId": obj.properties["projectId"]
                     },
                     page_content=obj.properties.get("content", "empty")) for obj in contents]

    chain = load_summarize_chain(llm, chain_type="stuff", verbose=True, prompt=prompt,
                                 document_variable_name="input_documents")

    joined = ", ".join(userIds)
    summary = await chain.ainvoke({"input_documents": docs, "users": joined})
    return summary


async def answer_question(projectId: str, question: str):
    contents = wc.get_entries(projectId, -1, -1)
    if not contents or len(contents) == 0:
        return {"result": "No entries found for the given parameters."}

    # LangChain Documents
    docs = [Document(id=str(obj.uuid),
                     metadata={
                         "type": obj.properties.get("type", "unknown"),
                         "user": obj.properties.get("user", "unknown"),
                         "timestamp": obj.properties["timestamp"],
                         "projectId": obj.properties["projectId"]
                     },
                     page_content=obj.properties["content"]) for obj in contents]

    vectorstore = WeaviateVectorStore.from_documents(
        documents=docs,
        embedding=get_embeddings(),
        client=wc.client,
        collection_name=wc.COLLECTION_NAME,
    )

    retriever = vectorstore.as_retriever(search_kwargs={"k": 8, "alpha": 0.5})
    qa_chain = RetrievalQA.from_chain_type(
        llm=llm,
        chain_type="stuff",
        retriever=retriever,
        return_source_documents=True
    )
    response = await qa_chain.ainvoke(question)

    # Remove ID from source_documents if present to avoid null values in JSON response
    for doc in response["source_documents"]:
        if hasattr(doc, "id"):
            delattr(doc, "id")

    return response
