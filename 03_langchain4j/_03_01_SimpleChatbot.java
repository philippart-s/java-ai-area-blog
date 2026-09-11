///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 26+
//DEPS dev.langchain4j:langchain4j:1.18.0
//DEPS dev.langchain4j:langchain4j-open-ai:1.18.0
//DEPS org.slf4j:slf4j-nop:2.0.17
//
// Simple chatbot example calling OVHcloud AI Endpoints (gpt-oss-120b)
// through LangChain4j AI Services (https://github.com/langchain4j/langchain4j).
//
// Docs: https://www.ovhcloud.com/en/public-cloud/ai-endpoints/catalog/gpt-oss-120b/
// Docs: https://docs.langchain4j.dev/tutorials/ai-services

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;

interface Assistant {
    // Uncomment to give the assistant a system prompt:
    // @SystemMessage("provide a concise answer")
    String chat(String userMessage);
}

void main() {
    // baseUrl must include the /v1 suffix. The token is read from
    // OVH_AI_ENDPOINTS_ACCESS_TOKEN, exported by run.sh from .env.
    final String baseUrl = "https://oai.endpoints.kepler.ai.cloud.ovh.net/v1";
    final String model = "gpt-oss-120b";
    final String token = System.getenv("OVH_AI_ENDPOINTS_ACCESS_TOKEN");

    var userPrompt = IO.readln("⌨️ Your prompt: ");
    IO.println();

    ChatModel chatModel = OpenAiChatModel.builder()
            .baseUrl(baseUrl)
            .apiKey(token)
            .modelName(model)
            // .logRequests(true)
            // .logResponses(true)
            .build();

    Assistant assistant = AiServices.create(Assistant.class, chatModel);

    IO.println("===== 🤖 ANSWER 🤖 =====");
    IO.println(assistant.chat(userPrompt));
}
