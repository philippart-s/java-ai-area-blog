///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 26+
//DEPS dev.langchain4j:langchain4j:1.18.0
//DEPS dev.langchain4j:langchain4j-open-ai:1.18.0
//DEPS org.slf4j:slf4j-simple:2.0.17
//
// Streaming chatbot with a PERSISTENT memory example calling OVHcloud AI
// Endpoints (gpt-oss-120b) through LangChain4j AI Services
// (https://github.com/langchain4j/langchain4j).
// Java 26 + JBang port of 02_sdk_java/_02_04_StreamingChatbotFileMemory.java.
//
// Same idea as _03_03 but the memory outlives the process. This is the example
// the three previous ones were building up to.
//
// In 00.04, _01_04 and _02_04 we wrote the persistence ourselves, and the code
// kept growing around it: where to put the file, when to write it, how to turn
// the messages into something writable, how to get them back in. LangChain4j
// asks none of those questions. It defines a seam — ChatMemoryStore, three
// methods — and calls it for us:
//   - getMessages(memoryId)              before sending the prompt,
//   - updateMessages(memoryId, messages) once the answer is complete,
//   - deleteMessages(memoryId)           when a conversation is dropped.
// The default implementation keeps everything in a map. We swap it for one that
// keeps everything in a file, and nothing else in the program changes.
//
// Read the loop below and notice what is MISSING: there is no "save the memory"
// step any more. _01_04 and _02_04 both had one, numbered 6). Here we never call
// the store at all — it is called for us, at the right moments. That is the
// difference between having serialization and having a memory.
//
// Two more things come for free with the interface:
//   - ChatMessageSerializer / ChatMessageDeserializer handle the ChatMessage
//     hierarchy (system, user, AI, tool result) for us. Compare with _02_04,
//     which needed the SDK's own mapper to survive the same problem;
//   - the store is keyed by memoryId, so it is already multi-session: one file
//     per conversation, exactly like the two memories of _03_04, except they now
//     survive a restart.
//
// Run the program twice: the second run picks the conversation up where you left
// it, and the model still knows your name.
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

// The whole point of this example: the persistent memory, as an implementation
// of the interface LangChain4j already calls. One file per memoryId, so several
// conversations can live side by side without knowing about each other.
//
// Note that nothing here decides WHEN to read or write. The methods just answer
// the questions they are asked; LangChain4j chooses when to ask them.
class FileChatMemoryStore implements ChatMemoryStore {

    private final Path directory;

    FileChatMemoryStore(Path directory) {
        this.directory = directory;
    }

    private Path fileFor(Object memoryId) {
        return directory.resolve(memoryId + ".json");
    }

    // Called before every request, to build the context sent to the model.
    // An unknown conversation is not an error: it is an empty one.
    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        var file = fileFor(memoryId);
        try {
            if (!Files.exists(file) || Files.size(file) == 0) {
                return List.of();
            }
            // ChatMessage is a polymorphic type (system, user, AI, tool result).
            // This is the piece we would otherwise have to write by hand.
            return ChatMessageDeserializer.messagesFromJson(Files.readString(file));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read the memory of " + memoryId, e);
        }
    }

    // Called once the answer is complete, with the memory already updated and
    // already trimmed to the window size. We only have to store it.
    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        try {
            Files.createDirectories(directory);
            Files.writeString(fileFor(memoryId), ChatMessageSerializer.messagesToJson(messages));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write the memory of " + memoryId, e);
        }
    }

    // Called when a conversation is dropped. The in-memory default made this
    // almost pointless; with a file behind it, this is what stops the disk from
    // filling up with dead conversations.
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
    // OVHcloud AI Endpoints configuration.
    // baseUrl points at the OpenAI-compatible endpoint (note the /v1 suffix);
    // the token is read from the OVH_AI_ENDPOINTS_ACCESS_TOKEN environment variable.
    final String baseUrl = "https://oai.endpoints.kepler.ai.cloud.ovh.net/v1";
    final String model = "gpt-oss-120b";
    final String token = System.getenv("OVH_AI_ENDPOINTS_ACCESS_TOKEN");

    // Where the conversations are stored. The path is relative to the CURRENT
    // directory, not to this file: the JVM started by JBang has no idea where the
    // .java it runs lives, which is why run.sh does a "cd" into the example
    // directory before calling JBang.
    final String sessionId = "cli-session";
    final var memoryDir = Path.of(".memory");

    // Build the underlying LangChain4j streaming model, pointed at OVHcloud AI Endpoints.
    StreamingChatModel chatModel = OpenAiStreamingChatModel.builder()
            .baseUrl(baseUrl)
            .apiKey(token)
            .modelName(model)
            .logRequests(false)
            //.logResponses(true)
            .build();

    // 1) The memory. Same sliding window of 10 messages as _03_03 — only the
    // store behind it changes. Because the store is addressed by id, the memory
    // now needs one: it is the name of the file the conversation lands in.
    ChatMemoryStore memoryStore = new FileChatMemoryStore(memoryDir);
    ChatMemory chatMemory = MessageWindowChatMemory.builder()
            .id(sessionId)
            .maxMessages(10)
            .chatMemoryStore(memoryStore)
            .build();

    // Create the AI Service backed by the streaming model, with memory.
    // Unchanged from _03_03: the service has no idea the memory is persistent.
    Assistant assistant = AiServices.builder(Assistant.class)
            .streamingChatModel(chatModel)
            .chatMemory(chatMemory)
            .build();

    // 2) Nothing to restore: reading the memory already reads the file. The
    // conversation is simply there, or empty on a first run.
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
        // Ask the user for a prompt.
        var userPrompt = IO.readln("⌨️  Your prompt: ");
        IO.println();

        // Leave the loop on "exit", or on end of input (Ctrl+D).
        if (userPrompt == null || userPrompt.equals("exit")) break;
        if (userPrompt.isBlank()) continue;

        // 3) Print the memory as it is BEFORE the call: this is the context that
        // LangChain4j is about to resend to the model, in front of our prompt.
        // Unlike _03_03, it is NOT empty on the first turn of a second run: it
        // was read back from the file by getMessages().
        IO.println("===== 🧠 MEMORY (resent to the model with the prompt) 🧠 =====");
        chatMemory.messages().forEach(IO::println);
        IO.println();

        // 4) Call the endpoint in streaming mode and print the answer token by
        // token. As in _03_03, streaming is asynchronous: the CompletableFuture
        // is completed by onCompleteResponse (or failed by onError) and join()
        // blocks until the answer is complete.
        IO.println("===== 🤖 ANSWER (streaming) 🤖 =====");
        var futureResponse = new CompletableFuture<ChatResponse>();
        assistant.chat(userPrompt)
                .onPartialResponse(IO::print)
                .onCompleteResponse(futureResponse::complete)
                .onError(futureResponse::completeExceptionally)
                .start();
        futureResponse.join();

        // Newlines once the stream is complete.
        IO.println();
        IO.println();

        // 5) ...and that is all. There is no step 6) here: LangChain4j called
        // updateMessages() on our store while completing the answer, so the file
        // on disk is already up to date. Compare with _01_04 and _02_04, where
        // saving was our job, on every single turn.
    }

    // Print the final memory: the whole conversation (up to the window size),
    // including the system message added by LangChain4j itself. Run the program
    // again and this is exactly what it will start from.
    IO.println("===== 🧠 FINAL MEMORY (the whole conversation) 🧠 =====");
    chatMemory.messages().forEach(IO::println);
    IO.println();

    // A real application would delete a conversation when it ends. It matters
    // more now than it did in _03_04: the entries are files, and they outlive
    // the process.
    //memoryStore.deleteMessages(sessionId);
    IO.println("🗑️  Delete " + memoryDir.resolve(sessionId + ".json")
            + " to start a fresh conversation.");
}
