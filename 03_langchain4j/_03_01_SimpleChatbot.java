///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 26+
//DEPS dev.langchain4j:langchain4j:1.18.0
//DEPS dev.langchain4j:langchain4j-open-ai:1.18.0
//DEPS org.slf4j:slf4j-nop:2.0.17
//
// Simple chatbot example calling OVHcloud AI Endpoints (gpt-oss-120b)
// through LangChain4j AI Services (https://github.com/langchain4j/langchain4j).
// Java 26 + JBang port of 02_sdk_java/_02_01_SimpleChatbot.java.
//
// AI Services is the high-level, declarative API of LangChain4j: instead of
// building requests by hand, we declare a plain Java interface (Assistant) and
// LangChain4j generates the implementation for us, wiring it to a ChatModel.
// OVHcloud AI Endpoints is OpenAI-compatible, so the underlying model is an
// OpenAiChatModel pointed at the OVH baseUrl with the OVH token as apiKey.
//
// Docs: https://www.ovhcloud.com/en/public-cloud/ai-endpoints/catalog/gpt-oss-120b/

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;

// The AI Service: a plain interface describing what we want.
// LangChain4j generates the implementation at runtime.
interface Assistant {
    // Uncomment to give the assistant a system prompt:
    // @SystemMessage("provide a concise answer")
    String chat(String userMessage);
}

void main() {
    // OVHcloud AI Endpoints configuration.
    // baseUrl points at the OpenAI-compatible endpoint (note the /v1 suffix);
    // the token is read from the OVH_AI_ENDPOINTS_ACCESS_TOKEN environment variable.
    final String baseUrl = "https://oai.endpoints.kepler.ai.cloud.ovh.net/v1";
    final String model = "gpt-oss-120b";
    final String token = System.getenv("OVH_AI_ENDPOINTS_ACCESS_TOKEN");

    // Ask the user for a prompt.
    var userPrompt = IO.readln("⌨️ Your prompt: ");
    IO.println();

    // Build the underlying LangChain4j chat model, pointed at OVHcloud AI Endpoints.
    ChatModel chatModel = OpenAiChatModel.builder()
            .baseUrl(baseUrl)
            .apiKey(token)
            .modelName(model)
            // .logRequests(true)
            // .logResponses(true)
            .build();

    // Create the AI Service backed by that model. No manual request/response
    // objects: we just call the interface method.
    Assistant assistant = AiServices.create(Assistant.class, chatModel);

    // Call the endpoint (blocking, non-streaming) and print the answer.
    IO.println("===== 🤖 ANSWER 🤖 =====");
    IO.println(assistant.chat(userPrompt));
}
