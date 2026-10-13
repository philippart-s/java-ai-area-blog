///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 26+
//DEPS org.springframework.boot:spring-boot-dependencies:4.1.0@pom
//DEPS org.springframework.ai:spring-ai-bom:2.0.0@pom
//DEPS org.springframework.boot:spring-boot-starter:4.1.0
// AspectJ weaver, required by Spring AI's ChatClient advisors (AOP).
//DEPS org.aspectj:aspectjweaver:1.9.25.1
//DEPS org.springframework.ai:spring-ai-starter-model-openai:2.0.0
//DEPS org.springframework.ai:spring-ai-vector-store:2.0.0
//DEPS org.springframework.ai:spring-ai-rag:2.0.0
// Servlet API present only so Boot's web-servlet config classes resolve during
// bean introspection; the app stays non-web (spring.main.web-application-type=none).
//DEPS jakarta.servlet:jakarta.servlet-api:6.1.0
//
// Simple RAG example calling OVHcloud AI Endpoints (gpt-oss-120b)
// through Spring AI (https://docs.spring.io/spring-ai/reference/).
//
// The markdown files of ../documents are read into an in-memory vector store,
// and a retrieval advisor is registered on the ChatClient: the question is
// matched against the store and the best document is added to the prompt
// before the model sees it.
//
// Docs: https://www.ovhcloud.com/en/public-cloud/ai-endpoints/catalog/gpt-oss-120b/
// Docs: https://docs.spring.io/spring-ai/reference/api/retrieval-augmented-generation.html

// Mounted as application.properties, so the other examples keep theirs.
//FILES application.properties=rag.properties

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.FileSystemResource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

// NOT @SpringBootApplication: this file lives in the default package, so its
// @ComponentScan would scan the whole classpath and break Spring's own config.
@SpringBootConfiguration
@EnableAutoConfiguration
public class _05_05_SimpleRag {

    // Relative to the current directory: run.sh cd's into the example directory.
    private static final Path DOCUMENTS_DIR = Path.of("..", "documents");

    public static void main(String[] args) {
        SpringApplication.run(_05_05_SimpleRag.class, args);
    }

    @Bean
    CommandLineRunner run(ChatClient.Builder builder, EmbeddingModel embeddingModel) {
        return args -> {
            var question = "Who is speaking about quantum compilers, and in which room?";

            // TextReader keeps one document per file, no splitting.
            IO.println("===== 📚 DOCUMENTS 📚 =====");
            var documents = new ArrayList<Document>();
            try (var files = Files.list(DOCUMENTS_DIR)) {
                for (var file : files.filter(p -> p.toString().endsWith(".md")).sorted().toList()) {
                    IO.println("  " + file.getFileName());
                    documents.addAll(new TextReader(new FileSystemResource(file)).get());
                }
            }

            // add() embeds every document and keeps the vectors in memory.
            var vectorStore = SimpleVectorStore.builder(embeddingModel).build();
            vectorStore.add(documents);
            IO.println();

            // Registered as a default advisor: every call searches the store and
            // prepends what it finds to the prompt.
            var advisor = RetrievalAugmentationAdvisor.builder()
                    .documentRetriever(VectorStoreDocumentRetriever.builder()
                            .vectorStore(vectorStore)
                            .topK(1)
                            .build())
                    .build();

            var chatClient = builder
                    .defaultSystem("provide a concise answer")
                    .defaultAdvisors(advisor)
                    .build();

            IO.println("===== ❓ QUESTION ❓ =====");
            IO.println(question);
            IO.println();

            IO.println("===== 🤖 ANSWER 🤖 =====");
            IO.println(chatClient.prompt()
                    .user(question)
                    .call()
                    .content());
        };
    }
}
