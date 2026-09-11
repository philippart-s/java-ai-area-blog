///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 26+
// jboss-threads needs java.lang opened on Java 24+ (thread-local reset capability).
//JAVA_OPTIONS --add-opens=java.base/java.lang=ALL-UNNAMED
//DEPS io.quarkus.platform:quarkus-bom:3.33.2@pom
//DEPS io.quarkiverse.langchain4j:quarkus-langchain4j-openai:1.12.0
//
// Streaming chatbot example calling OVHcloud AI Endpoints (gpt-oss-120b)
// through Quarkus + quarkus-langchain4j
// (https://github.com/quarkiverse/quarkus-langchain4j).
//
// Single-file Quarkus application run by JBang. Base URL, model and api-key
// live in application.properties, included via //FILES below.
// The answer is printed token by token.
//
// Docs: https://www.ovhcloud.com/en/public-cloud/ai-endpoints/catalog/gpt-oss-120b/
// Docs: https://docs.quarkiverse.io/quarkus-langchain4j/dev/

//FILES application.properties

import dev.langchain4j.service.SystemMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import io.smallrye.mutiny.Multi;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;

// Returning Multi<String> switches the extension to the streaming model.
@RegisterAiService
interface Assistant {
    @SystemMessage("provide a concise answer")
    Multi<String> chat(String userMessage);
}

@QuarkusMain
public class _04_02_StreamingChatbot implements QuarkusApplication {

    @Inject
    Assistant assistant;

    // @ActivateRequestContext makes the CDI request scope available for the call.
    @Override
    @ActivateRequestContext
    public int run(String... args) {
        var userPrompt = IO.readln("⌨️  Your prompt: ");
        IO.println();

        // asStream() blocks this thread until the reactive stream completes.
        IO.println("===== 🤖 ANSWER (streaming) 🤖 =====");
        assistant.chat(userPrompt)
                .subscribe().asStream()
                .forEach(IO::print);

        return 0;
    }
}
