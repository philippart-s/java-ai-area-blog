///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 26+
// jboss-threads needs java.lang opened on Java 24+ (thread-local reset capability).
//JAVA_OPTIONS --add-opens=java.base/java.lang=ALL-UNNAMED
// easy-rag pulls the whole Apache Tika suite, which loads a native library.
//JAVA_OPTIONS --enable-native-access=ALL-UNNAMED
//DEPS io.quarkus.platform:quarkus-bom:3.33.2@pom
//DEPS io.quarkiverse.langchain4j:quarkus-langchain4j-openai:1.12.0
//DEPS io.quarkiverse.langchain4j:quarkus-langchain4j-easy-rag:1.12.0
// Tika logs through log4j-api, which ships without a binding: route it into the
// Quarkus log manager instead of letting it print "could not find a provider".
//DEPS org.apache.logging.log4j:log4j-to-slf4j:2.25.3
//
// Simple RAG example calling OVHcloud AI Endpoints (gpt-oss-120b)
// through Quarkus + quarkus-langchain4j
// (https://github.com/quarkiverse/quarkus-langchain4j).
//
// Single-file Quarkus application run by JBang. The easy-rag extension ingests
// ../documents at startup and registers the retriever on the AI service, so the
// only thing left here is the question. Everything is in rag.properties.
//
// Docs: https://www.ovhcloud.com/en/public-cloud/ai-endpoints/catalog/gpt-oss-120b/
// Docs: https://docs.quarkiverse.io/quarkus-langchain4j/dev/easy-rag.html

// Mounted as application.properties, so the other examples keep theirs.
//FILES application.properties=rag.properties

import dev.langchain4j.service.SystemMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;

@RegisterAiService
interface Assistant {
    @SystemMessage("provide a concise answer")
    String chat(String userMessage);
}

@QuarkusMain
public class _04_05_SimpleRag implements QuarkusApplication {

    @Inject
    Assistant assistant;

    // @ActivateRequestContext makes the CDI request scope available for the call.
    @Override
    @ActivateRequestContext
    public int run(String... args) {
        var question = "Who is speaking about quantum compilers, and in which room?";

        IO.println("===== ❓ QUESTION ❓ =====");
        IO.println(question);
        IO.println();

        IO.println("===== 🤖 ANSWER 🤖 =====");
        IO.println(assistant.chat(question));
        return 0;
    }
}
