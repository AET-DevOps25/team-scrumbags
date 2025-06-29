from langchain.chains.summarize import load_summarize_chain
from langchain_ollama import OllamaLLM, OllamaEmbeddings
import weaviate
from langchain_weaviate.vectorstores import WeaviateVectorStore
from langchain.chains import RetrievalQA
from langchain.schema import Document
from langchain.prompts import PromptTemplate

from app import weaviate_client as wc

llm = OllamaLLM(model="llama3.2",
                base_url="http://ollama:11434",
                temperature=0.1)

embeddings = OllamaEmbeddings(model="llama3.2", base_url="http://ollama:11434")

def summarize_entries(project_id: str, start: int, end: int):
    #raw content strings
    contents = wc.get_entries(project_id, start, end)
    if not contents:
        return "No entries found for the given parameters."

    markdown_stuff_prompt = PromptTemplate(
        template=(
            "You are a summarizer of source control information (pull requests, commits, branches, etc.), transcripts "
            "of meetings with assigned speakers, and written communication (e.g., messages) between team members over "
            "collaboration platforms like, e.g., Discord or Microsoft Teams. Given the following documents containing "
            "information about the project dealings, produce a "
            "detailed summary in Markdown format. Use headings, bullet points, and code "
            "blocks where appropriate.\n\n"
            "### Documents:\n"
            "{text}\n\n"
            "### Summary (in Markdown):\n"
        ),
        input_variables=["text"],
    )

    # LangChain Documents
    docs = [Document(id=str(obj.uuid),
                        metadata={
                            "type": obj.properties["type"],
                            "user": obj.properties["user"],
                            "timestamp": obj.properties["timestamp"],
                            "projectId": obj.properties["projectId"]
                        },
                        page_content=obj.properties["content"]
    ) for obj in contents]

    chain = load_summarize_chain(llm, chain_type="stuff", verbose=False, prompt=markdown_stuff_prompt,)

    summary = chain.invoke(docs)
    return summary

def answer_question(project_id: str, start: int, end: int, question: str) -> str:
    contents = wc.get_entries(project_id, start, end)
    if not contents:
        return "No entries found for the given parameters."

    # LangChain Documents
    docs = [Document(id=str(obj.uuid),
                        metadata={
                            "type": obj.properties["type"],
                            "user": obj.properties["user"],
                            "timestamp": obj.properties["timestamp"],
                            "projectId": obj.properties["projectId"]
                        },
                        page_content=obj.properties["content"]
    ) for obj in contents]

    vectorstore = WeaviateVectorStore.from_documents(
        documents=docs,
        embedding=embeddings,
        client=wc.client,
        collection_name=wc.COLLECTION_NAME
    )

    retriever = vectorstore.as_retriever(search_kwargs={"k": 5})
    qa_chain = RetrievalQA.from_chain_type(
        llm=llm,
        chain_type="stuff",
        retriever=retriever,
        return_source_documents=True
    )
    response = qa_chain.invoke(question)
    return response
