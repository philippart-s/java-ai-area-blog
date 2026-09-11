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
// Streaming chatbot with a persistent memory example calling OVHcloud AI
// Endpoints (gpt-oss-120b) through Spring AI
// (https://docs.spring.io/spring-ai/reference/).
//
// Single-file Spring Boot application run by JBang. The conversation is stored
// in .memory/<conversationId>.json, so it survives quitting the program.
// Run it twice. Type "exit" (or press Ctrl+D) to quit.
//
// Docs: https://www.ovhcloud.com/en/public-cloud/ai-endpoints/catalog/gpt-oss-120b/
// Docs: https://docs.spring.io/spring-ai/reference/api/chat-memory.html

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

// Spring AI has no message serializer, so we store the two things a message is
// made of — the same two columns its own JdbcChatMemoryRepository uses.
record StoredMessage(String type, String text) {}

// One JSON file per conversation.
class FileChatMemoryRepository implements ChatMemoryRepository {

    private final Path directory;
    private final ObjectMapper mapper = new ObjectMapper();

    FileChatMemoryRepository(Path directory) {
        this.directory = directory;
    }

    private Path fileFor(String conversationId) {
        return directory.resolve(conversationId + ".json");
    }

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

    // An unknown conversation is not an error: it is an empty one.
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

    @Override
    public void deleteByConversationId(String conversationId) {
        try {
            Files.deleteIfExists(fileFor(conversationId));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot delete the conversation " + conversationId, e);
        }
    }

    private static Message toMessage(StoredMessage stored) {
        return switch (stored.type()) {
            case "SYSTEM" -> new SystemMessage(stored.text());
            case "USER" -> new UserMessage(stored.text());
            case "ASSISTANT" -> new AssistantMessage(stored.text());
            default -> throw new IllegalStateException("Unsupported message type: " + stored.type());
        };
    }
}

// NOT @SpringBootApplication: this file lives in the default package, so its
// @ComponentScan would scan the whole classpath and break Spring's own config.
@SpringBootConfiguration
@EnableAutoConfiguration
public class _05_04_StreamingChatbotFileMemory {

    // Also the name of the file the conversation is stored in.
    private static final String CONVERSATION_ID = "cli-session";

    // Path relative to the current directory: a JBang script cannot locate its
    // own .java file, which is why run.sh cd's into the example directory first.
    private static final Path MEMORY_DIR = Path.of(".memory");

    public static void main(String[] args) {
        // The OpenAI client underneath opens a cached thread pool on the first
        // streamed call; its threads are not daemons, so the JVM would wait out
        // their 60s idle timeout. exit() closes the context, System.exit() leaves.
        System.exit(SpringApplication.exit(
                SpringApplication.run(_05_04_StreamingChatbotFileMemory.class, args)));
    }

    @Bean
    CommandLineRunner run(ChatClient.Builder builder) {
        return args -> {
            ChatMemoryRepository repository = new FileChatMemoryRepository(MEMORY_DIR);
            ChatMemory chatMemory = MessageWindowChatMemory.builder()
                    .chatMemoryRepository(repository)
                    .maxMessages(10)
                    .build();

            // Registered as a default advisor, so every call goes through it.
            var chatClient = builder
                    .defaultSystem("provide a concise answer")
                    .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                    .build();

            // Nothing to restore: reading the memory already reads the file.
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
            IO.println("🗂️  conversations on disk: " + repository.findConversationIds());
            IO.println();

            while (true) {
                var userPrompt = IO.readln("⌨️  Your prompt: ");
                IO.println();

                if (userPrompt == null || userPrompt.equals("exit")) break;
                if (userPrompt.isBlank()) continue;

                // No system message in there: defaultSystem is re-applied on
                // every call, so the advisor only stores user and assistant ones.
                IO.println("===== 🧠 MEMORY (resent to the model with the prompt) 🧠 =====");
                chatMemory.get(CONVERSATION_ID).forEach(IO::println);
                IO.println();

                // The advisor calls saveAll() on the way out, so there is
                // nothing to save here.
                IO.println("===== 🤖 ANSWER (streaming) 🤖 =====");
                chatClient.prompt()
                        .user(userPrompt)
                        .advisors(advisor -> advisor.param(ChatMemory.CONVERSATION_ID, CONVERSATION_ID))
                        .stream()
                        .content()
                        .doOnNext(IO::print)
                        .blockLast();

                IO.println();
                IO.println();
            }

            IO.println("===== 🧠 FINAL MEMORY (the whole conversation) 🧠 =====");
            chatMemory.get(CONVERSATION_ID).forEach(IO::println);
            IO.println();

            // Dropping the conversation is an explicit act:
            //chatMemory.clear(CONVERSATION_ID);
            IO.println("🗑️  Delete " + MEMORY_DIR.resolve(CONVERSATION_ID + ".json")
                    + " to start a fresh conversation.");
        };
    }
}
