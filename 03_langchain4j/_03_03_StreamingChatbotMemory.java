///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 26+
//DEPS dev.langchain4j:langchain4j:1.18.0
//DEPS dev.langchain4j:langchain4j-open-ai:1.18.0
//DEPS org.slf4j:slf4j-simple:2.0.17
//
// Streaming chatbot with memory example calling OVHcloud AI Endpoints
// (gpt-oss-120b) through LangChain4j AI Services
// (https://github.com/langchain4j/langchain4j).
//
// Sliding window of the 10 last messages, kept in memory.
// Type "exit" (or press Ctrl+D) to quit.
//
// Docs: https://www.ovhcloud.com/en/public-cloud/ai-endpoints/catalog/gpt-oss-120b/
// Docs: https://docs.langchain4j.dev/tutorials/chat-memory

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;

import java.util.concurrent.CompletableFuture;


interface Assistant {
    @SystemMessage("provide a concise answer")
    TokenStream chat(String userMessage);
}

void main() {
    // baseUrl must include the /v1 suffix. The token is read from
    // OVH_AI_ENDPOINTS_ACCESS_TOKEN, exported by run.sh from .env.
    final String baseUrl = "https://oai.endpoints.kepler.ai.cloud.ovh.net/v1";
    final String model = "gpt-oss-120b";
    final String token = System.getenv("OVH_AI_ENDPOINTS_ACCESS_TOKEN");

    StreamingChatModel chatModel = OpenAiStreamingChatModel.builder()
            .baseUrl(baseUrl)
            .apiKey(token)
            .modelName(model)
            .logRequests(false)
            //.logResponses(true)
            .build();

    // Kept in a variable only to print it; the AI Service reads and writes it.
    ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(10);

    Assistant assistant = AiServices.builder(Assistant.class)
            .streamingChatModel(chatModel)
            .chatMemory(chatMemory)
            .build();

    IO.println("===== 🧠 CHATBOT WITH MEMORY (type \"exit\" to quit) 🧠 =====");
    IO.println();

    while (true) {
        var userPrompt = IO.readln("⌨️  Your prompt: ");
        IO.println();

        if (userPrompt == null || userPrompt.equals("exit")) break;
        if (userPrompt.isBlank()) continue;

        IO.println("===== 🧠 MEMORY (resent to the model with the prompt) 🧠 =====");
        chatMemory.messages().forEach(IO::println);
        IO.println();

        // The answer is added to the memory on completion, nothing to do here.
        IO.println("===== 🤖 ANSWER (streaming) 🤖 =====");
        var futureResponse = new CompletableFuture<ChatResponse>();
        assistant.chat(userPrompt)
                .onPartialResponse(IO::print)
                .onCompleteResponse(futureResponse::complete)
                .onError(futureResponse::completeExceptionally)
                .start();
        futureResponse.join();

        IO.println();
        IO.println();
    }

    // Includes the system message, added by LangChain4j itself.
    IO.println("===== 🧠 FINAL MEMORY (the whole conversation) 🧠 =====");
    chatMemory.messages().forEach(IO::println);
}
