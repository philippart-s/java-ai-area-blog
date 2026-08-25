///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 26+
//DEPS dev.langchain4j:langchain4j:1.18.0
//DEPS dev.langchain4j:langchain4j-open-ai:1.18.0
//DEPS  org.slf4j:slf4j-simple:2.0.17
//
// Multi-session memory example calling OVHcloud AI Endpoints (gpt-oss-120b)
// through LangChain4j AI Services
// (https://github.com/langchain4j/langchain4j).
//
// _03_03 gave the assistant ONE memory, which is enough for a CLI chatbot but
// not for a real one: a single service usually serves many users at the same
// time, and their conversations must not be mixed up. This example is only about
// that: @MemoryId, the parameter that names the conversation a call belongs to.
// LangChain4j then keeps one memory per name.
//
// To keep the focus on that single idea, there is nothing to type here: the
// conversation is scripted. Stéphane and Fanny both introduce themselves in
// their own session, then both ask the same question. Each one gets their own
// name back, and the two memories printed at the end never mixed.
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


// @MemoryId marks the parameter identifying the conversation. Any type works
// (a user id, a UUID, a Long...), a String is enough here. As soon as a
// parameter is annotated, the prompt itself must be annotated too, hence the
// @UserMessage on the second parameter.
interface Assistant {
    @SystemMessage("provide a concise answer")
    TokenStream chat(@MemoryId String sessionId, @UserMessage String userMessage);
}

void main() {
    // OVHcloud AI Endpoints configuration.
    // baseUrl points at the OpenAI-compatible endpoint (note the /v1 suffix);
    // the token is read from the OVH_AI_ENDPOINTS_ACCESS_TOKEN environment variable.
    final String baseUrl = "https://oai.endpoints.kepler.ai.cloud.ovh.net/v1";
    final String model = "gpt-oss-120b";
    final String token = System.getenv("OVH_AI_ENDPOINTS_ACCESS_TOKEN");

    // Build the underlying LangChain4j streaming model, pointed at OVHcloud AI Endpoints.
    StreamingChatModel chatModel = OpenAiStreamingChatModel.builder()
            .baseUrl(baseUrl)
            .apiKey(token)
            .modelName(model)
            .logRequests(false)
            //.logResponses(true)
            .build();

    // Where the conversations are kept. The default store is already in memory,
    // so declaring it explicitly changes nothing for the assistant: we do it
    // only to be able to read the conversations back and print them. As its name
    // says, everything is lost when the program stops (a real application would
    // plug a persistent store here, which is the subject of the next part).
    ChatMemoryStore memoryStore = new InMemoryChatMemoryStore();

    // Create the AI Service backed by the streaming model, with memory.
    // Because of @MemoryId there is not ONE memory but one per conversation, so
    // we register a chatMemoryProvider instead of a single chatMemory:
    // LangChain4j calls it with a memory id the first time it sees that id, and
    // reuses the memory it returns for the next calls.
    Assistant assistant = AiServices.builder(Assistant.class)
            .streamingChatModel(chatModel)
            .chatMemoryProvider(memoryId -> MessageWindowChatMemory.builder()
                    .id(memoryId)
                    .maxMessages(10)
                    .chatMemoryStore(memoryStore)
                    .build())
            .build();

    // The two conversations, told apart by their memory id only.
    final String stephane = "stephane";
    final String fanny = "fanny";

    // The scripted conversation: who is talking, and what they say. Both
    // introduce themselves, then both ask the same question.
    ask(assistant, stephane, "My name is Stéphane");
    ask(assistant, fanny, "My name is Fanny");
    ask(assistant, stephane, "What is my name?");
    ask(assistant, fanny, "What is my name?");

    // Print both memories: same assistant, same model, but two conversations
    // that never saw each other. Each one only knows what was said to it.
    for (var sessionId : List.of(stephane, fanny)) {
        IO.println("===== 🧠 MEMORY of \"" + sessionId + "\" 🧠 =====");
        memoryStore.getMessages(sessionId).forEach(IO::println);
        IO.println();
    }

    // A real application would also delete a conversation when it ends,
    // otherwise the store keeps one entry per memory id forever:
    //memoryStore.deleteMessages(stephane);
}

// One turn of conversation: send the prompt on behalf of a session and print the
// answer token by token. As in _03_02 and _03_03, streaming is asynchronous: the
// CompletableFuture is completed by onCompleteResponse (or failed by onError)
// and join() blocks until the answer is complete. Nothing to do about the
// memory: LangChain4j appends the prompt and the complete answer to the memory
// of THIS session, and to no other.
void ask(Assistant assistant, String sessionId, String userPrompt) {
    IO.println("===== 💬 \"" + sessionId + "\" says: " + userPrompt + " 💬 =====");

    var futureResponse = new CompletableFuture<ChatResponse>();
    assistant.chat(sessionId, userPrompt)
            .onPartialResponse(IO::print)
            .onCompleteResponse(futureResponse::complete)
            .onError(futureResponse::completeExceptionally)
            .start();
    futureResponse.join();

    // Newlines once the stream is complete.
    IO.println();
    IO.println();
}
