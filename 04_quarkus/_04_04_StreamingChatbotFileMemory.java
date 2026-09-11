///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 26+
// jboss-threads needs java.lang opened on Java 24+ (thread-local reset capability).
//JAVA_OPTIONS --add-opens=java.base/java.lang=ALL-UNNAMED
//DEPS io.quarkus.platform:quarkus-bom:3.33.2@pom
//DEPS io.quarkiverse.langchain4j:quarkus-langchain4j-openai:1.12.0
//
// Streaming chatbot with a persistent memory example calling OVHcloud AI
// Endpoints (gpt-oss-120b) through Quarkus + quarkus-langchain4j
// (https://github.com/quarkiverse/quarkus-langchain4j).
//
// Single-file Quarkus application run by JBang. The conversation is stored in
// .memory/<memoryId>.json, so it survives quitting the program. Run it twice.
// Type "exit" (or press Ctrl+D) to quit.
//
// In command mode the CDI scope ends when the program exits, and the extension
// clears the memory then — which would delete the file. PersistentChatMemory
// below ignores that clear(), so a conversation is only dropped on purpose.
//
// Docs: https://www.ovhcloud.com/en/public-cloud/ai-endpoints/catalog/gpt-oss-120b/
// Docs: https://docs.quarkiverse.io/quarkus-langchain4j/dev/messages-and-memory.html

//FILES application.properties

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import io.smallrye.mutiny.Multi;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

// Annotating one parameter forces the prompt to be annotated too, hence @UserMessage.
@RegisterAiService
interface Assistant {
    @SystemMessage("provide a concise answer")
    Multi<String> chat(@MemoryId String sessionId, @UserMessage String userMessage);
}

// @ApplicationScoped is enough to replace the extension's default store, which
// is declared @DefaultBean. One file per memoryId.
@ApplicationScoped
class FileChatMemoryStore implements ChatMemoryStore {

    private final Path directory = Path.of(".memory");

    private Path fileFor(Object memoryId) {
        return directory.resolve(memoryId + ".json");
    }

    // An unknown conversation is not an error: it is an empty one.
    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        var file = fileFor(memoryId);
        try {
            if (!Files.exists(file) || Files.size(file) == 0) {
                return List.of();
            }
            // ChatMessage is polymorphic; the serializer handles that.
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

// Delegates everything except clear(), so the store is never emptied by the
// framework. Not a record: the wrapped delegate is mutable.
final class PersistentChatMemory implements ChatMemory {

    private final ChatMemory delegate;

    PersistentChatMemory(ChatMemory delegate) {
        this.delegate = delegate;
    }

    @Override
    public Object id() {
        return delegate.id();
    }

    @Override
    public void add(ChatMessage message) {
        delegate.add(message);
    }

    @Override
    public List<ChatMessage> messages() {
        return delegate.messages();
    }

    // Deliberately empty: dropping a conversation goes through deleteMessages().
    @Override
    public void clear() {
    }
}

// Replaces the extension's default provider.
@Singleton
class PersistentChatMemoryProvider implements ChatMemoryProvider {

    @Inject
    ChatMemoryStore store;

    // The window still comes from application.properties.
    @ConfigProperty(name = "quarkus.langchain4j.chat-memory.memory-window.max-messages",
            defaultValue = "10")
    int maxMessages;

    @Override
    public ChatMemory get(Object memoryId) {
        return new PersistentChatMemory(MessageWindowChatMemory.builder()
                .id(memoryId)
                .maxMessages(maxMessages)
                .chatMemoryStore(store)
                .build());
    }
}

@QuarkusMain
public class _04_04_StreamingChatbotFileMemory implements QuarkusApplication {

    // Also the name of the file the conversation is stored in.
    private static final String SESSION_ID = "cli-session";

    @Inject
    Assistant assistant;

    // Injected only to print the conversation; resolves to the bean above.
    @Inject
    ChatMemoryStore memoryStore;

    // @ActivateRequestContext makes the CDI request scope available for the call.
    @Override
    @ActivateRequestContext
    public int run(String... args) {
        // Nothing to restore: reading the store already reads the file.
        var restored = memoryStore.getMessages(SESSION_ID);
        if (restored.isEmpty()) {
            IO.println("===== 🧠 NO MEMORY YET, STARTING A NEW CONVERSATION 🧠 =====");
        } else {
            IO.println("===== 🧠 MEMORY RESTORED FROM DISK (" + restored.size() + " messages) 🧠 =====");
            restored.forEach(IO::println);
        }
        IO.println();

        IO.println("===== 🧠 CHATBOT WITH PERSISTENT MEMORY (type \"exit\" to quit) 🧠 =====");
        IO.println("💾 stored in " + Path.of(".memory", SESSION_ID + ".json").toAbsolutePath());
        IO.println();

        while (true) {
            var userPrompt = IO.readln("⌨️  Your prompt: ");
            IO.println();

            if (userPrompt == null || userPrompt.equals("exit")) break;
            if (userPrompt.isBlank()) continue;

            IO.println("===== 🧠 MEMORY (resent to the model with the prompt) 🧠 =====");
            memoryStore.getMessages(SESSION_ID).forEach(IO::println);
            IO.println();

            // asStream() blocks until the stream completes; updateMessages() is
            // called on the store then, so there is nothing to save here.
            IO.println("===== 🤖 ANSWER (streaming) 🤖 =====");
            assistant.chat(SESSION_ID, userPrompt)
                    .subscribe().asStream()
                    .forEach(IO::print);

            // Newlines once the stream is complete.
            IO.println();
            IO.println();
        }

        // Includes the system message, added by the extension itself.
        IO.println("===== 🧠 FINAL MEMORY (the whole conversation) 🧠 =====");
        memoryStore.getMessages(SESSION_ID).forEach(IO::println);
        IO.println();

        // Dropping the conversation is an explicit act:
        //memoryStore.deleteMessages(SESSION_ID);
        IO.println("🗑️  Delete " + Path.of(".memory", SESSION_ID + ".json")
                + " to start a fresh conversation.");

        return 0;
    }
}
