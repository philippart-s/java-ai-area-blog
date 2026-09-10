///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 26+
//DEPS org.springframework.boot:spring-boot-dependencies:4.1.0@pom
//DEPS org.springframework.ai:spring-ai-bom:2.0.0@pom
//DEPS org.springframework.boot:spring-boot-starter:4.1.0
// AspectJ weaver, required by Spring AI's ChatClient advisors (AOP).
//DEPS org.aspectj:aspectjweaver:1.9.25.1
//DEPS org.springframework.ai:spring-ai-starter-model-openai:2.0.0
// Servlet API present only so Boot's web-servlet config classes resolve during
// bean introspection; the app stays non-web (spring.main.web-application-type=none).
//DEPS jakarta.servlet:jakarta.servlet-api:6.1.0
// Jackson, to write the messages ourselves — see the comment on StoredMessage.
// Pinned to the 2.x already resolved here: Spring Boot 4 also brings Jackson 3
// (package tools.jackson.*), and the two coexist on the classpath.
//DEPS com.fasterxml.jackson.core:jackson-databind:2.21.4
//
// Streaming chatbot with a PERSISTENT memory example calling OVHcloud AI
// Endpoints (gpt-oss-120b) through Spring AI
// (https://docs.spring.io/spring-ai/reference/).
// Spring Boot command-mode port of 04_quarkus/_04_04_StreamingChatbotFileMemory.java.
//
// Same idea as _05_03 but the memory outlives the process.
//
// Spring AI names the seam differently — ChatMemoryRepository, not
// ChatMemoryStore — and puts one more method on it: besides read, write and
// delete, a repository can list the conversations it holds. Swapping it is the
// simplest of the whole row: it is a constructor argument of the memory, so a
// single line changes. No bean discovery, no @DefaultBean, no wiring to undo:
//     .chatMemoryRepository(new InMemoryChatMemoryRepository())   // _05_03
//     .chatMemoryRepository(new FileChatMemoryRepository(...))    // here
//
// Two things this example does NOT need, and one it brings back.
//
// It does not need the lifecycle precaution of _04_04. Here the memory is ours:
// we build it in a @Bean and hand it to the advisor. Nothing owns it on our
// behalf, so nothing clears it when the application stops — MessageWindowChatMemory
// .clear() does call deleteByConversationId(), but only if WE call it.
//
// It does not need an advisor change either: MessageChatMemoryAdvisor is
// unchanged, and so is the rest of the run.
//
// What it brings back is the mapping we stopped writing in _03_05. LangChain4j
// hands out ChatMessageSerializer / ChatMessageDeserializer; Spring AI has no
// equivalent — Message is an interface, and its own JdbcChatMemoryRepository
// stores a type column and a content column and rebuilds the objects from them.
// So we do the same, with a two-field record. It is the shortest mapping of the
// series, but it is a mapping, and it is worth noticing that the framework with
// the cleanest seam is also the one that leaves this to you.
//
// Run the program twice: the second run picks the conversation up where you left
// it, and the model still knows your name.
//
// Docs: https://www.ovhcloud.com/en/public-cloud/ai-endpoints/catalog/gpt-oss-120b/
// Docs: https://docs.spring.io/spring-ai/reference/api/chat-memory.html

// 1) Include application.properties as a classpath resource so Spring reads it.
//FILES application.properties

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

// 2) What we store. Spring AI's Message is an interface with several
// implementations, and nothing in the library turns one into JSON, so we keep the
// two things a message is made of: its type, and its text. Exactly the two
// columns Spring AI's own JdbcChatMemoryRepository uses.
record StoredMessage(String type, String text) {}

// 3) The persistent repository. Four methods this time: Spring AI adds
// findConversationIds() to the read/write/delete trio, which a file layout of one
// JSON per conversation answers by listing the directory.
class FileChatMemoryRepository implements ChatMemoryRepository {

    private final Path directory;
    private final ObjectMapper mapper = new ObjectMapper();

    FileChatMemoryRepository(Path directory) {
        this.directory = directory;
    }

    private Path fileFor(String conversationId) {
        return directory.resolve(conversationId + ".json");
    }

    // The method LangChain4j's store does not have. With one file per
    // conversation, the answer is the directory listing.
    @Override
    public List<String> findConversationIds() {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(directory)) {
            return files.map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".json"))
                    .map(name -> name.substring(0, name.length() - ".json".length()))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot list the conversations", e);
        }
    }

    // Called by the advisor before every request, to build the context sent to
    // the model. An unknown conversation is not an error: it is an empty one.
    @Override
    public List<Message> findByConversationId(String conversationId) {
        var file = fileFor(conversationId);
        try {
            if (!Files.exists(file) || Files.size(file) == 0) {
                return List.of();
            }
            List<StoredMessage> stored = mapper.readValue(Files.readString(file),
                new TypeReference<>() {
                });
            return stored.stream().map(FileChatMemoryRepository::toMessage).toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read the conversation " + conversationId, e);
        }
    }

    // Called by the advisor once the answer is complete, with the conversation
    // already trimmed to the window size.
    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        try {
            Files.createDirectories(directory);
            var stored = messages.stream()
                    .map(message -> new StoredMessage(message.getMessageType().name(),
                            message.getText()))
                    .toList();
            Files.writeString(fileFor(conversationId), mapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(stored));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write the conversation " + conversationId, e);
        }
    }

    // Called only when the application asks for it — unlike _04_04, nothing in
    // Spring AI triggers this on shutdown.
    @Override
    public void deleteByConversationId(String conversationId) {
        try {
            Files.deleteIfExists(fileFor(conversationId));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot delete the conversation " + conversationId, e);
        }
    }

    // Rebuilding the right implementation from the stored type. This switch is
    // the whole of the mapping LangChain4j did for us.
    private static Message toMessage(StoredMessage stored) {
        return switch (stored.type()) {
            case "SYSTEM" -> new SystemMessage(stored.text());
            case "USER" -> new UserMessage(stored.text());
            case "ASSISTANT" -> new AssistantMessage(stored.text());
            default -> throw new IllegalStateException("Unsupported message type: " + stored.type());
        };
    }
}

// NOT @SpringBootApplication: this single file lives in the default package, and
// its @ComponentScan would scan the whole classpath (breaking Spring's internal
// config). We only need auto-config + the local @Bean, so we drop the scan.
@SpringBootConfiguration
@EnableAutoConfiguration
public class _05_04_StreamingChatbotFileMemory {

    // A single conversation here, so a constant id is enough. It is also the name
    // of the file it is stored in. A web chatbot would use one id per user or per
    // session, and get one file each, for free.
    private static final String CONVERSATION_ID = "cli-session";

    // Where the conversations are stored. The path is relative to the CURRENT
    // directory, not to this file: the JVM started by JBang has no idea where the
    // .java it runs lives, which is why run.sh does a "cd" into the example
    // directory before calling JBang.
    private static final Path MEMORY_DIR = Path.of(".memory");

    public static void main(String[] args) {
        // SpringApplication.run() alone is not enough to end a STREAMING example:
        // the OpenAI client underneath Spring AI opens a cached thread pool on the
        // first streamed call, its threads are not daemons, and the JVM then waits
        // out their 60s idle timeout before exiting. SpringApplication.exit()
        // closes the context and returns the exit code, System.exit() then leaves
        // immediately instead of waiting. This is the documented shape for a
        // Spring Boot command-line application that must terminate.
        System.exit(SpringApplication.exit(
                SpringApplication.run(_05_04_StreamingChatbotFileMemory.class, args)));
    }

    // CommandLineRunner is the Spring Boot equivalent of a command-mode entry
    // point: it runs after the context starts, then the app exits (no web server).
    @Bean
    CommandLineRunner run(ChatClient.Builder builder) {
        return args -> {
            // 4) The conversation memory. Same sliding window of 10 messages as
            // _05_03 — the ONLY change in the whole setup is the repository handed
            // to it. That is the entire cost of persistence here.
            ChatMemoryRepository repository = new FileChatMemoryRepository(MEMORY_DIR);
            ChatMemory chatMemory = MessageWindowChatMemory.builder()
                    .chatMemoryRepository(repository)
                    .maxMessages(10)
                    .build();

            // 5) Build the ChatClient with the memory advisor registered by default,
            // so every call made with this client goes through it. Unchanged from
            // _05_03: the advisor has no idea the repository is now a file.
            var chatClient = builder
                    .defaultSystem("provide a concise answer")
                    .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                    .build();

            // 6) Nothing to restore: reading the memory already reads the file.
            var restored = chatMemory.get(CONVERSATION_ID);
            if (restored.isEmpty()) {
                IO.println("===== 🧠 NO MEMORY YET, STARTING A NEW CONVERSATION 🧠 =====");
            } else {
                IO.println("===== 🧠 MEMORY RESTORED FROM DISK (" + restored.size() + " messages) 🧠 =====");
                restored.forEach(IO::println);
            }
            IO.println();

            IO.println("===== 🧠 CHATBOT WITH PERSISTENT MEMORY (type \"exit\" to quit) 🧠 =====");
            IO.println("💾 stored in " + MEMORY_DIR.resolve(CONVERSATION_ID + ".json").toAbsolutePath());
            // The extra method of the Spring AI seam, put to use: every
            // conversation the repository holds, which here is every file in the
            // directory. Give the program another CONVERSATION_ID and it shows up.
            IO.println("🗂️  conversations on disk: " + repository.findConversationIds());
            IO.println();

            while (true) {
                // Ask the user for a prompt.
                var userPrompt = IO.readln("⌨️  Your prompt: ");
                IO.println();

                // Leave the loop on "exit", or on end of input (Ctrl+D).
                if (userPrompt == null || userPrompt.equals("exit")) break;
                if (userPrompt.isBlank()) continue;

                // 7) Print the memory as it is BEFORE the call: this is the context
                // the advisor is about to resend to the model, in front of our
                // prompt. Unlike _05_03, it is NOT empty on the first turn of a
                // second run: it was read back from the file.
                // The system message is not in there: Spring AI re-applies
                // defaultSystem on every call, so the advisor only ever stores the
                // user and assistant messages — which is also why the file holds
                // no SYSTEM entry.
                IO.println("===== 🧠 MEMORY (resent to the model with the prompt) 🧠 =====");
                chatMemory.get(CONVERSATION_ID).forEach(IO::println);
                IO.println();

                // 8) Call the endpoint in streaming mode and print the answer token
                // by token. The conversation id tells the advisor WHICH memory to
                // use; blockLast() keeps the runner alive until the stream ends.
                // On the way out the advisor calls saveAll() on our repository, so
                // the file on disk is already up to date. As in _03_05 and _04_04,
                // there is no "save the memory" step of our own.
                IO.println("===== 🤖 ANSWER (streaming) 🤖 =====");
                chatClient.prompt()
                        .user(userPrompt)
                        .advisors(advisor -> advisor.param(ChatMemory.CONVERSATION_ID, CONVERSATION_ID))
                        .stream()
                        .content()
                        .doOnNext(IO::print)
                        .blockLast();

                // Newlines once the stream is complete.
                IO.println();
                IO.println();
            }

            // Print the final memory: the whole conversation (up to the window
            // size), as stored by the advisor. Run the program again and this is
            // exactly what it will start from.
            IO.println("===== 🧠 FINAL MEMORY (the whole conversation) 🧠 =====");
            chatMemory.get(CONVERSATION_ID).forEach(IO::println);
            IO.println();

            // Dropping the conversation stays an explicit act — and here, unlike
            // _04_04, no precaution was needed to keep it that way:
            //chatMemory.clear(CONVERSATION_ID);
            IO.println("🗑️  Delete " + MEMORY_DIR.resolve(CONVERSATION_ID + ".json")
                    + " to start a fresh conversation.");
        };
    }
}
