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
// Spring Boot command-mode port of 04_quarkus/_04_03_StreamingChatbotMemory.java.
//
// Same idea as _05_02 but with a conversation memory. The chat completions API
// is still stateless, so the conversation must still be resent in full on every
// request: the difference is that we no longer write that code. In Spring AI the
// memory is not a setting of the client, it is an ADVISOR: an interceptor placed
// in front of the model call, which reads the memory into the prompt on the way
// in and writes the answer back to it on the way out (the same advisor mechanism
// used for RAG, logging, guardrails...). So there is no "add the answer back to
// the memory" step in this example, and no need to reassemble the streamed answer
// ourselves (compare with the ChatCompletionAccumulator of _02_03, or the
// StringBuilder of _01_03).
//
// Two pieces build that memory, and they map onto the previous examples:
//   - MessageWindowChatMemory keeps a sliding window of the N last messages,
//     exactly like its LangChain4j namesake in _03_03;
//   - a ChatMemoryRepository is where the conversations are stored. The default
//     one is in memory, so everything is lost when the app stops.
// The conversation is named with a conversation id, passed as an advisor
// parameter: the Spring AI equivalent of the @MemoryId of _03_04 / _04_03, which
// lets one client serve several users without mixing their contexts.
//
// The program loops so you can chat with the model. Type "exit" (or press
// Ctrl+D) to quit.
//
// Docs: https://www.ovhcloud.com/en/public-cloud/ai-endpoints/catalog/gpt-oss-120b/
// Docs: https://docs.spring.io/spring-ai/reference/api/chat-memory.html

// 1) Include application.properties as a classpath resource so Spring reads it.
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

// NOT @SpringBootApplication: this single file lives in the default package, and
// its @ComponentScan would scan the whole classpath (breaking Spring's internal
// config). We only need auto-config + the local @Bean, so we drop the scan.
@SpringBootConfiguration
@EnableAutoConfiguration
public class _05_03_StreamingChatbotMemory {

    // A single conversation here, so a constant id is enough. A web chatbot
    // would use one id per user or per session instead.
    private static final String CONVERSATION_ID = "cli-session";

    public static void main(String[] args) {
        // SpringApplication.run() alone is not enough to end a STREAMING example:
        // the OpenAI client underneath Spring AI opens a cached thread pool on the
        // first streamed call, its threads are not daemons, and the JVM then waits
        // out their 60s idle timeout before exiting. SpringApplication.exit()
        // closes the context and returns the exit code, System.exit() then leaves
        // immediately instead of waiting. This is the documented shape for a
        // Spring Boot command-line application that must terminate.
        System.exit(SpringApplication.exit(
                SpringApplication.run(_05_03_StreamingChatbotMemory.class, args)));
    }

    // CommandLineRunner is the Spring Boot equivalent of a command-mode entry
    // point: it runs after the context starts, then the app exits (no web server).
    @Bean
    CommandLineRunner run(ChatClient.Builder builder) {
        return args -> {
            // 2) The conversation memory: a sliding window of the 10 last messages,
            // kept in the default in-memory repository. Declaring the repository
            // explicitly changes nothing for the client (it is the default), we do
            // it only to show where the conversations actually live.
            ChatMemory chatMemory = MessageWindowChatMemory.builder()
                    .chatMemoryRepository(new InMemoryChatMemoryRepository())
                    .maxMessages(10)
                    .build();

            // 3) Build the ChatClient with the memory advisor registered by default,
            // so every call made with this client goes through it.
            var chatClient = builder
                    .defaultSystem("provide a concise answer")
                    .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                    .build();

            IO.println("===== 🧠 CHATBOT WITH MEMORY (type \"exit\" to quit) 🧠 =====");
            IO.println();

            while (true) {
                // Ask the user for a prompt.
                var userPrompt = IO.readln("⌨️  Your prompt: ");
                IO.println();

                // Leave the loop on "exit", or on end of input (Ctrl+D).
                if (userPrompt == null || userPrompt.equals("exit")) break;
                if (userPrompt.isBlank()) continue;

                // 4) Print the memory as it is BEFORE the call: this is the context
                // the advisor is about to resend to the model, in front of our
                // prompt. It is empty on the first turn (nothing was said yet), and
                // grows by two messages (ours + the model's) at every turn.
                // The system message is not in there: unlike LangChain4j, Spring AI
                // re-applies defaultSystem on every call, so the advisor only ever
                // stores the user and assistant messages.
                IO.println("===== 🧠 MEMORY (resent to the model with the prompt) 🧠 =====");
                chatMemory.get(CONVERSATION_ID).forEach(IO::println);
                IO.println();

                // 5) Call the endpoint in streaming mode and print the answer token
                // by token. The conversation id tells the advisor WHICH memory to
                // use; blockLast() keeps the runner alive until the stream ends.
                // Nothing else to do: the advisor stores our prompt on the way in
                // and the complete answer on the way out, so the next turn already
                // knows about them.
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

            // Print the final memory: the whole conversation (up to the window size),
            // as stored by the advisor.
            IO.println("===== 🧠 FINAL MEMORY (the whole conversation) 🧠 =====");
            chatMemory.get(CONVERSATION_ID).forEach(IO::println);

            // A real application would also delete the conversation when it ends,
            // otherwise the repository keeps one entry per conversation id forever:
            //chatMemory.clear(CONVERSATION_ID);
        };
    }
}
