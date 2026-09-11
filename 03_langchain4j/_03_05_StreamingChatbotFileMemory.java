///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 26+
//DEPS dev.langchain4j:langchain4j:1.18.0
//DEPS dev.langchain4j:langchain4j-open-ai:1.18.0
//DEPS org.slf4j:slf4j-simple:2.0.17
//
// Streaming chatbot with a persistent memory example calling OVHcloud AI
// Endpoints (gpt-oss-120b) through LangChain4j AI Services
// (https://github.com/langchain4j/langchain4j).
//
// The conversation is stored in .memory/<memoryId>.json, one file per id, so it
// survives quitting the program. Run it twice.
// Type "exit" (or press Ctrl+D) to quit.
//
// Docs: https://www.ovhcloud.com/en/public-cloud/ai-endpoints/catalog/gpt-oss-120b/
// Docs: https://docs.langchain4j.dev/tutorials/chat-memory#persistence

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;

import java.util.concurrent.CompletableFuture;


interface Assistant {
    @SystemMessage("provide a concise answer")
    TokenStream chat(String userMessage);
}

// One file per memoryId, so several conversations can live side by side.
class FileChatMemoryStore implements ChatMemoryStore {

    private final Path directory;

    FileChatMemoryStore(Path directory) {
        this.directory = directory;
    }

    private Path fileFor(Object memoryId) {
        return directory.resolve(memoryId + ".json");
    }

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        var file = fileFor(memoryId);
        try {
            if (!Files.exists(file) || Files.size(file) == 0) {
                return List.of();
            }
            // ChatMessage is polymorphic (system, user, AI, tool result);
            // the serializer handles that.
            return ChatMessageDeserializer.messagesFromJson(Files.readString(file));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read the memory of " + memoryId, e);
        }
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        try {
            Files.createDirectories(directory);
            Files.writeString(fileFor(memoryId), ChatMessageSerializer.messagesToJson(messages));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write the memory of " + memoryId, e);
        }
    }

    @Override
    public void deleteMessages(Object memoryId) {
        try {
            Files.deleteIfExists(fileFor(memoryId));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot delete the memory of " + memoryId, e);
        }
    }
}

void main() {
    // baseUrl must include the /v1 suffix. The token is read from
    // OVH_AI_ENDPOINTS_ACCESS_TOKEN, exported by run.sh from .env.
    final String baseUrl = "https://oai.endpoints.kepler.ai.cloud.ovh.net/v1";
    final String model = "gpt-oss-120b";
    final String token = System.getenv("OVH_AI_ENDPOINTS_ACCESS_TOKEN");

    // Path relative to the current directory: a JBang script cannot locate its
    // own .java file, which is why run.sh cd's into the example directory first.
    final String sessionId = "cli-session";
    final var memoryDir = Path.of(".memory");

    StreamingChatModel chatModel = OpenAiStreamingChatModel.builder()
            .baseUrl(baseUrl)
            .apiKey(token)
            .modelName(model)
            .logRequests(false)
            //.logResponses(true)
            .build();

    // The id names the file the conversation lands in.
    ChatMemoryStore memoryStore = new FileChatMemoryStore(memoryDir);
    ChatMemory chatMemory = MessageWindowChatMemory.builder()
            .id(sessionId)
            .maxMessages(10)
            .chatMemoryStore(memoryStore)
            .build();

    Assistant assistant = AiServices.builder(Assistant.class)
            .streamingChatModel(chatModel)
            .chatMemory(chatMemory)
            .build();

    // Nothing to restore: reading the memory already reads the file.
    var restored = chatMemory.messages();
    if (restored.isEmpty()) {
        IO.println("===== 🧠 NO MEMORY YET, STARTING A NEW CONVERSATION 🧠 =====");
    } else {
        IO.println("===== 🧠 MEMORY RESTORED FROM DISK (" + restored.size() + " messages) 🧠 =====");
        restored.forEach(IO::println);
    }
    IO.println();

    IO.println("===== 🧠 CHATBOT WITH PERSISTENT MEMORY (type \"exit\" to quit) 🧠 =====");
    IO.println("💾 stored in " + memoryDir.resolve(sessionId + ".json").toAbsolutePath());
    IO.println();

    while (true) {
        var userPrompt = IO.readln("⌨️  Your prompt: ");
        IO.println();

        if (userPrompt == null || userPrompt.equals("exit")) break;
        if (userPrompt.isBlank()) continue;

        IO.println("===== 🧠 MEMORY (resent to the model with the prompt) 🧠 =====");
        chatMemory.messages().forEach(IO::println);
        IO.println();

        // updateMessages() is called on the store on completion, nothing to save here.
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

    IO.println("===== 🧠 FINAL MEMORY (the whole conversation) 🧠 =====");
    chatMemory.messages().forEach(IO::println);
    IO.println();

    // Dropping the conversation is an explicit act:
    //memoryStore.deleteMessages(sessionId);
    IO.println("🗑️  Delete " + memoryDir.resolve(sessionId + ".json")
            + " to start a fresh conversation.");
}
