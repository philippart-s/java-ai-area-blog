///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 26+
// jboss-threads needs java.lang opened on Java 24+ (thread-local reset capability).
//JAVA_OPTIONS --add-opens=java.base/java.lang=ALL-UNNAMED
//DEPS io.quarkus.platform:quarkus-bom:3.33.2@pom
//DEPS io.quarkiverse.langchain4j:quarkus-langchain4j-openai:1.12.0
//
// Streaming chatbot example calling OVHcloud AI Endpoints (gpt-oss-120b)
// through Quarkus + quarkus-langchain4j (https://github.com/quarkiverse/quarkus-langchain4j).
// Quarkus command-mode port of 03_langchain4j/_03_02_StreamingChatbot.java.
//
// Same idea as _04_01 but with streaming: the AI service method returns a
// Mutiny Multi<String> instead of a String. quarkus-langchain4j detects that
// return type and switches to the streaming chat model automatically. We turn
// the reactive stream into a blocking Stream (subscribe().asStream()) so the
// command-mode run() prints each token as it arrives and returns when done.
//
// OVHcloud AI Endpoints is OpenAI-compatible, so we use the OpenAI extension and
// point its base-url at OVH. All settings (base-url, model, api-key) live in
// application.properties; the api-key there is a ${OVH_AI_ENDPOINTS_ACCESS_TOKEN}
// expression resolved at runtime from the environment.
//
// Docs: https://www.ovhcloud.com/en/public-cloud/ai-endpoints/catalog/gpt-oss-120b/

// 1️⃣ Include application.properties as a classpath resource so Quarkus reads it.
//FILES application.properties

import dev.langchain4j.service.SystemMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import io.smallrye.mutiny.Multi;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;

// 2️⃣ The AI Service: returning Multi<String> switches quarkus-langchain4j to
// streaming mode. 
@RegisterAiService
interface Assistant {
    @SystemMessage("provide a concise answer")
    Multi<String> chat(String userMessage);
}

// 3️⃣ Command-mode entry point: @QuarkusMain + QuarkusApplication run in a shell.
@QuarkusMain
public class _04_02_StreamingChatbot implements QuarkusApplication {

    // 4️⃣ The generated AI service is a CDI bean, injected here.
    @Inject
    Assistant assistant;

    // @ActivateRequestContext makes the CDI request scope available for the call.
    @Override
    @ActivateRequestContext
    public int run(String... args) {
        // Ask the user for a prompt.
        var userPrompt = IO.readln("⌨️  Your prompt: ");
        IO.println();

        // 5️⃣ Call the endpoint in streaming mode and print the answer token by token.
        // asStream() blocks this thread until the reactive stream completes.
        IO.println("===== 🤖 ANSWER (streaming) 🤖 =====");
        assistant.chat(userPrompt)
                .subscribe().asStream()
                .forEach(IO::print);

        return 0;
    }
}