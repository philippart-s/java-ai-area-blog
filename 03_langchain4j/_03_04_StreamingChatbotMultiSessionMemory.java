///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 26+
//DEPS dev.langchain4j:langchain4j:1.18.0
//DEPS dev.langchain4j:langchain4j-open-ai:1.18.0
//DEPS org.slf4j:slf4j-simple:2.0.17
//
// Multi-session memory example calling OVHcloud AI Endpoints (gpt-oss-120b)
// through LangChain4j AI Services (https://github.com/langchain4j/langchain4j).
//
// One memory per @MemoryId. The conversation is scripted, nothing to type:
// Stephane and Fanny each introduce themselves, then both ask the same question.
//
// Docs: https://www.ovhcloud.com/en/public-cloud/ai-endpoints/catalog/gpt-oss-120b/
// Docs: https://docs.langchain4j.dev/tutorials/chat-memory

import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import dev.langchain4j.store.memory.chat.InMemoryChatMemoryStore;

import java.util.List;
import java.util.concurrent.CompletableFuture;


// Annotating one parameter forces the prompt to be annotated too, hence @UserMessage.
interface Assistant {
    @SystemMessage("provide a concise answer")
    TokenStream chat(@MemoryId String sessionId, @UserMessage String userMessage);
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

    // The default store, declared explicitly only to read the conversations back.
    ChatMemoryStore memoryStore = new InMemoryChatMemoryStore();

    // chatMemoryProvider instead of chatMemory: it is called once per new id.
    Assistant assistant = AiServices.builder(Assistant.class)
            .streamingChatModel(chatModel)
            .chatMemoryProvider(memoryId -> MessageWindowChatMemory.builder()
                    .id(memoryId)
                    .maxMessages(10)
                    .chatMemoryStore(memoryStore)
                    .build())
            .build();

    final String stephane = "stephane";
    final String fanny = "fanny";

    ask(assistant, stephane, "My name is Stéphane");
    ask(assistant, fanny, "My name is Fanny");
    ask(assistant, stephane, "What is my name?");
    ask(assistant, fanny, "What is my name?");

    for (var sessionId : List.of(stephane, fanny)) {
        IO.println("===== 🧠 MEMORY of \"" + sessionId + "\" 🧠 =====");
        memoryStore.getMessages(sessionId).forEach(IO::println);
        IO.println();
    }

    // A real application would delete a conversation when it ends, otherwise
    // the store keeps one entry per memory id forever:
    //memoryStore.deleteMessages(stephane);
}

// One turn on behalf of a session.
void ask(Assistant assistant, String sessionId, String userPrompt) {
    IO.println("===== 💬 \"" + sessionId + "\" says: " + userPrompt + " 💬 =====");

    var futureResponse = new CompletableFuture<ChatResponse>();
    assistant.chat(sessionId, userPrompt)
            .onPartialResponse(IO::print)
            .onCompleteResponse(futureResponse::complete)
            .onError(futureResponse::completeExceptionally)
            .start();
    futureResponse.join();

    IO.println();
    IO.println();
}
