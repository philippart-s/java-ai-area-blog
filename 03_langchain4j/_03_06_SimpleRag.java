///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 26+
//DEPS dev.langchain4j:langchain4j:1.18.0
//DEPS dev.langchain4j:langchain4j-open-ai:1.18.0
//DEPS org.slf4j:slf4j-nop:2.0.17
//
// Simple RAG example calling OVHcloud AI Endpoints (gpt-oss-120b)
// through LangChain4j AI Services (https://github.com/langchain4j/langchain4j).
//
// The markdown files of ../documents are loaded into an in-memory embedding
// store, and a content retriever is registered on the AI Service: the question
// is matched against the store and the best document is added to the prompt
// before the model sees it.
//
// Docs: https://www.ovhcloud.com/en/public-cloud/ai-endpoints/catalog/gpt-oss-120b/
// Docs: https://docs.langchain4j.dev/tutorials/rag

import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;

interface Assistant {
    @SystemMessage("provide a concise answer")
    String chat(String userMessage);
}

void main() {
    // baseUrl must include the /v1 suffix. The token is read from
    // OVH_AI_ENDPOINTS_ACCESS_TOKEN, exported by run.sh from .env.
    final String baseUrl = "https://oai.endpoints.kepler.ai.cloud.ovh.net/v1";
    final String token = System.getenv("OVH_AI_ENDPOINTS_ACCESS_TOKEN");

    // Relative to the current directory: run.sh cd's into the example directory.
    var documentsDir = Path.of("..", "documents");
    var question = "Who is speaking about quantum compilers, and in which room?";

    ChatModel chatModel = OpenAiChatModel.builder()
            .baseUrl(baseUrl)
            .apiKey(token)
            .modelName("gpt-oss-120b")
            .build();

    EmbeddingModel embeddingModel = OpenAiEmbeddingModel.builder()
            .baseUrl(baseUrl)
            .apiKey(token)
            .modelName("bge-m3")
            .build();

    EmbeddingStore<TextSegment> store = new InMemoryEmbeddingStore<>();

    // No documentSplitter: one segment per file, so a whole document is matched
    // and retrieved at once.
    IO.println("===== 📚 DOCUMENTS 📚 =====");
    var documents = FileSystemDocumentLoader.loadDocuments(documentsDir);
    documents.forEach(d -> IO.println("  " + d.metadata().getString("file_name")));
    EmbeddingStoreIngestor.builder()
            .embeddingModel(embeddingModel)
            .embeddingStore(store)
            .build()
            .ingest(documents);
    IO.println();

    // Registered on the AI Service: every call goes through it, the matching
    // document is prepended to the prompt and the model never sees the rest.
    var retriever = EmbeddingStoreContentRetriever.builder()
            .embeddingStore(store)
            .embeddingModel(embeddingModel)
            .maxResults(1)
            .build();

    Assistant assistant = AiServices.builder(Assistant.class)
            .chatModel(chatModel)
            .contentRetriever(retriever)
            .build();

    IO.println("===== ❓ QUESTION ❓ =====");
    IO.println(question);
    IO.println();

    IO.println("===== 🤖 ANSWER 🤖 =====");
    IO.println(assistant.chat(question));
}
