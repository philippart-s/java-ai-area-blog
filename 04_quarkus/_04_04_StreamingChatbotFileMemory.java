///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 26+
// jboss-threads needs java.lang opened on Java 24+ (thread-local reset capability).
//JAVA_OPTIONS --add-opens=java.base/java.lang=ALL-UNNAMED
//DEPS io.quarkus.platform:quarkus-bom:3.33.2@pom
//DEPS io.quarkiverse.langchain4j:quarkus-langchain4j-openai:1.12.0
//
// Streaming chatbot with a PERSISTENT memory example calling OVHcloud AI
// Endpoints (gpt-oss-120b) through Quarkus + quarkus-langchain4j
// (https://github.com/quarkiverse/quarkus-langchain4j).
// Quarkus command-mode port of 03_langchain4j/_03_05_StreamingChatbotFileMemory.java.
//
// Same idea as _04_03 but the memory outlives the process. This example has two
// halves, and the second one is the interesting one.
//
// FIRST HALF — swapping the store costs one annotation.
// The extension produces its default store with @Produces @Singleton @DefaultBean,
// and Quarkus drops a default bean as soon as the application provides its own of
// the same type. So the @ApplicationScoped FileChatMemoryStore below silently
// takes its place: no @Alternative, no priority, no configuration. Compare with
// _03_05, where the store had to be handed to the memory, and the memory to the
// service. Here nothing is wired.
//
// SECOND HALF — the framework also owns the memory's LIFECYCLE, and command mode
// collapses that lifecycle onto the program's own.
// The extension ties a memory to the CDI scope of the AI Service. When that scope
// ends it calls QuarkusAiServiceContext.close() -> clearChatMemory() ->
// ChatMemory.clear(), and MessageWindowChatMemory.clear() calls deleteMessages()
// on the store. The documentation says so plainly: the store is asked to delete
// "when the exchange ends or the memory is no longer needed, like when the scope
// is terminated".
//
// That rule is right everywhere except here. A web service is @SessionScoped or
// @ApplicationScoped, so the scope ends when the user leaves or when the server
// stops — rarely, and by then the conversation really is over. That is the world
// the official Redis store is written for, and why its deleteMessages() is a
// plain DEL with no precaution. A command-mode CLI is the pathological case: the
// application stops every single time you quit, so "the scope ended" and "you
// pressed Ctrl+D" are the same event. With the in-memory store of _04_03 this was
// invisible; with a file, quitting DELETES the conversation. Marking the service
// @ApplicationScoped does not help — it only moves the same clear() to shutdown,
// which here is the same instant.
//
// So persistence is not only "where do the bytes go", it is also "who decides the
// conversation is over". We take that decision back, and it costs a second bean:
// a ChatMemoryProvider handing out memories that ignore clear(). Dropping a
// conversation becomes an explicit act — deleting the file, or calling
// deleteMessages() — instead of a side effect of exiting.
//
// Two beans, then, and still no wiring: @RegisterAiService is bare, exactly as in
// _04_03, and application.properties is untouched.
//
// Run the program twice: the second run picks the conversation up where you left
// it, and the model still knows your name.
//
// Docs: https://www.ovhcloud.com/en/public-cloud/ai-endpoints/catalog/gpt-oss-120b/
// Docs: https://docs.quarkiverse.io/quarkus-langchain4j/dev/messages-and-memory.html

// 1) Include application.properties as a classpath resource so Quarkus reads it.
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

// 2) The AI Service. Byte for byte the same as in _04_03: it does not know, and
// does not need to know, that its memory is now a file that outlives it.
@RegisterAiService
interface Assistant {
    @SystemMessage("provide a concise answer")
    Multi<String> chat(@MemoryId String sessionId, @UserMessage String userMessage);
}

// 3) The persistent store. The methods are those of _03_05; the annotation is the
// news. @ApplicationScoped is enough to replace the extension's default store,
// because that one is declared @DefaultBean.
@ApplicationScoped
class FileChatMemoryStore implements ChatMemoryStore {

    // Hard-coded to stay comparable with _03_05. A real application would read it
    // from configuration, like the window size below.
    private final Path directory = Path.of(".memory");

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
    // already trimmed to the configured window.
    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        try {
            Files.createDirectories(directory);
            Files.writeString(fileFor(memoryId), ChatMessageSerializer.messagesToJson(messages));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write the memory of " + memoryId, e);
        }
    }

    // Still a real delete: this is how a conversation is dropped on purpose. What
    // changes below is only WHO is allowed to trigger it.
    @Override
    public void deleteMessages(Object memoryId) {
        try {
            Files.deleteIfExists(fileFor(memoryId));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot delete the memory of " + memoryId, e);
        }
    }
}

// 4) A ChatMemory that survives being cleared. Every method delegates, except
// clear(): that is the one the framework calls when it decides the conversation
// is over. Sensible for a long-running service — which is why the official Redis
// store deletes without a second thought — but wrong for a CLI whose whole point
// is to be resumed tomorrow, and which shuts down after every conversation.
// A plain decorator, not a record: the delegate it wraps is mutable (add() changes
// it), so the value semantics a record advertises would be misleading here.
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

    // Deliberately empty. Dropping a conversation stays possible, but only
    // explicitly, through ChatMemoryStore.deleteMessages().
    @Override
    public void clear() {
    }
}

// 5) The provider that hands out those memories. A plain CDI bean is all it
// takes — this is the documented way — and it replaces the extension's default
// provider, the one that used to build disposable memories.
@Singleton
class PersistentChatMemoryProvider implements ChatMemoryProvider {

    // Our store, resolved by CDI like any other bean.
    @Inject
    ChatMemoryStore store;

    // The window still comes from application.properties, so the "configuration
    // instead of code" point of _04_03 survives.
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

// 6) Command-mode entry point: @QuarkusMain + QuarkusApplication run in a shell.
@QuarkusMain
public class _04_04_StreamingChatbotFileMemory implements QuarkusApplication {

    // A single conversation here, so a constant id is enough. It is also the name
    // of the file it is stored in. A web chatbot would use one id per user or per
    // session, and get one file each, for free.
    private static final String SESSION_ID = "cli-session";

    // 7) The generated AI service is a CDI bean, injected here.
    @Inject
    Assistant assistant;

    // The store, injected only so we can print the conversation. Note that this
    // injection point is unchanged from _04_03: it used to resolve to the
    // extension's in-memory default, and now resolves to the bean above. Nothing
    // at the call site says which one it is.
    @Inject
    ChatMemoryStore memoryStore;

    // @ActivateRequestContext makes the CDI request scope available for the call.
    @Override
    @ActivateRequestContext
    public int run(String... args) {
        // 8) Nothing to restore: reading the store already reads the file.
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
            // Ask the user for a prompt.
            var userPrompt = IO.readln("⌨️  Your prompt: ");
            IO.println();

            // Leave the loop on "exit", or on end of input (Ctrl+D).
            if (userPrompt == null || userPrompt.equals("exit")) break;
            if (userPrompt.isBlank()) continue;

            // 9) Print the memory as it is BEFORE the call: this is the context
            // the extension is about to resend to the model, in front of our
            // prompt. Unlike _04_03, it is NOT empty on the first turn of a
            // second run: it was read back from the file by getMessages().
            IO.println("===== 🧠 MEMORY (resent to the model with the prompt) 🧠 =====");
            memoryStore.getMessages(SESSION_ID).forEach(IO::println);
            IO.println();

            // 10) Call the endpoint in streaming mode and print the answer token
            // by token. asStream() blocks this thread until the reactive stream
            // completes; on completion the extension calls updateMessages() on
            // our store, so the file on disk is already up to date. As in _03_05,
            // there is no "save the memory" step of our own.
            IO.println("===== 🤖 ANSWER (streaming) 🤖 =====");
            assistant.chat(SESSION_ID, userPrompt)
                    .subscribe().asStream()
                    .forEach(IO::print);

            // Newlines once the stream is complete.
            IO.println();
            IO.println();
        }

        // Print the final memory: the whole conversation (up to the window size),
        // including the system message added by the extension itself. Run the
        // program again and this is exactly what it will start from — which only
        // works because PersistentChatMemory ignored the clear() that is about to
        // happen when this method returns.
        IO.println("===== 🧠 FINAL MEMORY (the whole conversation) 🧠 =====");
        memoryStore.getMessages(SESSION_ID).forEach(IO::println);
        IO.println();

        // Dropping the conversation is now an explicit act, not a side effect of
        // quitting:
        //memoryStore.deleteMessages(SESSION_ID);
        IO.println("🗑️  Delete " + Path.of(".memory", SESSION_ID + ".json")
                + " to start a fresh conversation.");

        return 0;
    }
}
