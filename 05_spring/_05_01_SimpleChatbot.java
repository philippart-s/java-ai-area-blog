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
// Simple chatbot example calling OVHcloud AI Endpoints (gpt-oss-120b)
// through Spring AI (https://docs.spring.io/spring-ai/reference/).
// Spring Boot command-mode port of 04_quarkus/_04_01_SimpleChatbot.java.
//
// This is a single-file Spring Boot application run by JBang. Spring AI's OpenAI
// starter auto-configures a ChatClient.Builder from application.properties; we
// inject it into a CommandLineRunner and talk to the model with the fluent
// ChatClient API. No manual HTTP, no manual client wiring.
//
// OVHcloud AI Endpoints is OpenAI-compatible, so we use the OpenAI starter and
// point its base-url at OVH. All settings (base-url, model, api-key) live in
// application.properties; the api-key there is a ${OVH_AI_ENDPOINTS_ACCESS_TOKEN}
// placeholder resolved at runtime from the environment.
//
// Docs: https://www.ovhcloud.com/en/public-cloud/ai-endpoints/catalog/gpt-oss-120b/

// Include application.properties as a classpath resource so Spring reads it.
//FILES application.properties

import org.springframework.ai.chat.client.ChatClient;
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
public class _05_01_SimpleChatbot {

    public static void main(String[] args) {
        SpringApplication.run(_05_01_SimpleChatbot.class, args);
    }

    // CommandLineRunner is the Spring Boot equivalent of a command-mode entry
    // point: it runs after the context starts, then the app exits (no web server).
    @Bean
    CommandLineRunner run(ChatClient.Builder builder) {
        return args -> {
            var chatClient = builder.build();

            // Ask the user for a prompt.
            var userPrompt = IO.readln("⌨️ Your prompt: ");
            IO.println();

            // Call the endpoint (blocking, non-streaming) and print the answer.
            IO.println("===== 🤖 ANSWER 🤖 =====");
            IO.println(chatClient.prompt()
                    .user(userPrompt)
                    .call()
                    .content());
        };
    }
}
