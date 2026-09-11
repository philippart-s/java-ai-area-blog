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
//
// Streaming chatbot with memory example calling OVHcloud AI Endpoints
// (gpt-oss-120b) through Spring AI (https://docs.spring.io/spring-ai/reference/).
//
// Single-file Spring Boot application run by JBang. The memory is a sliding
// window of the 10 last messages, held by a MessageChatMemoryAdvisor and kept
// in the default in-memory repository.
// Type "exit" (or press Ctrl+D) to quit.
//
// Docs: https://www.ovhcloud.com/en/public-cloud/ai-endpoints/catalog/gpt-oss-120b/
// Docs: https://docs.spring.io/spring-ai/reference/api/chat-memory.html

//FILES application.properties

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;

// NOT @SpringBootApplication: this file lives in the default package, so its
// @ComponentScan would scan the whole classpath and break Spring's own config.
@SpringBootConfiguration
@EnableAutoConfiguration
public class _05_03_StreamingChatbotMemory {

    private static final String CONVERSATION_ID = "cli-session";

    public static void main(String[] args) {
        // The OpenAI client underneath opens a cached thread pool on the first
        // streamed call; its threads are not daemons, so the JVM would wait out
        // their 60s idle timeout. exit() closes the context, System.exit() leaves.
        System.exit(SpringApplication.exit(
                SpringApplication.run(_05_03_StreamingChatbotMemory.class, args)));
    }

    @Bean
    CommandLineRunner run(ChatClient.Builder builder) {
        return args -> {
            // The repository is the default one; declared explicitly only to
            // show where the conversations live.
            ChatMemory chatMemory = MessageWindowChatMemory.builder()
                    .chatMemoryRepository(new InMemoryChatMemoryRepository())
                    .maxMessages(10)
                    .build();

            // Registered as a default advisor, so every call goes through it.
            var chatClient = builder
                    .defaultSystem("provide a concise answer")
                    .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                    .build();

            IO.println("===== 🧠 CHATBOT WITH MEMORY (type \"exit\" to quit) 🧠 =====");
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

                // The conversation id tells the advisor which memory to use.
                // It stores the prompt and the answer, nothing to do here.
                // blockLast() keeps the runner alive until the stream ends.
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

            // A real application would drop the conversation when it ends:
            //chatMemory.clear(CONVERSATION_ID);
        };
    }
}
