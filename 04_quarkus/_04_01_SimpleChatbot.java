///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 21+
//JAVAC_OPTIONS -parameters
// jboss-threads needs java.lang opened on Java 24+ (thread-local reset capability).
//JAVA_OPTIONS --add-opens=java.base/java.lang=ALL-UNNAMED
//DEPS io.quarkus.platform:quarkus-bom:3.33.2@pom
//DEPS io.quarkiverse.langchain4j:quarkus-langchain4j-openai:1.12.0
//
// Simple chatbot example calling OVHcloud AI Endpoints (gpt-oss-120b)
// through Quarkus + quarkus-langchain4j (https://github.com/quarkiverse/quarkus-langchain4j).
// Quarkus command-mode port of 03_langchain4j/_03_01_SimpleChatbot.java.
//
// This is a single-file Quarkus application run by JBang: JBang sees the Quarkus
// BOM/extension and triggers Quarkus build-time augmentation automatically.
// Instead of building a model by hand, we DECLARE the model in configuration
// (in application.properties, included via the //FILES directive below) and
// DECLARE the AI service as an interface (@RegisterAiService). Quarkus generates
// the implementation and injects it.
//
// OVHcloud AI Endpoints is OpenAI-compatible, so we use the OpenAI extension and
// point its base-url at OVH. All settings (base-url, model, api-key) live in
// application.properties; the api-key there is a ${OVH_AI_ENDPOINTS_ACCESS_TOKEN}
// expression resolved at runtime from the environment.
//
// Docs: https://www.ovhcloud.com/en/public-cloud/ai-endpoints/catalog/gpt-oss-120b/

// Include application.properties as a classpath resource so Quarkus reads it.
//FILES application.properties

import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;

import java.util.Scanner;

// The AI Service: a plain interface. Quarkus generates the implementation at
// build time and wires it to the configured OpenAI model. No manual client.
@RegisterAiService
interface Assistant {
    // Uncomment to give the assistant a system prompt:
    // @dev.langchain4j.service.SystemMessage("provide a concise answer")
    String chat(String userMessage);
}

// Command-mode entry point: @QuarkusMain + QuarkusApplication run in a shell.
@QuarkusMain
public class _04_01_SimpleChatbot implements QuarkusApplication {

    // The generated AI service is a CDI bean, injected here.
    @Inject
    Assistant assistant;

    // @ActivateRequestContext makes the CDI request scope available for the call.
    @Override
    @ActivateRequestContext
    public int run(String... args) {
        // Ask the user for a prompt.
        var userPrompt = IO.readln("⌨️ Your prompt: ");

        // Call the endpoint (blocking, non-streaming) and print the answer.
        IO.println("===== 🤖 ANSWER 🤖 =====");
        IO.println(assistant.chat(userPrompt));
        return 0;
    }
}