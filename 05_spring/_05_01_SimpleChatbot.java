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
//
// Single-file Spring Boot application run by JBang. Base URL, model and api-key
// live in application.properties, included via //FILES below.
//
// Docs: https://www.ovhcloud.com/en/public-cloud/ai-endpoints/catalog/gpt-oss-120b/
// Docs: https://docs.spring.io/spring-ai/reference/api/chatclient.html

//FILES application.properties

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;

// NOT @SpringBootApplication: this file lives in the default package, so its
// @ComponentScan would scan the whole classpath and break Spring's own config.
@SpringBootConfiguration
@EnableAutoConfiguration
public class _05_01_SimpleChatbot {

    public static void main(String[] args) {
        SpringApplication.run(_05_01_SimpleChatbot.class, args);
    }

    @Bean
    CommandLineRunner run(ChatClient.Builder builder) {
        return args -> {
            var chatClient = builder.build();

            var userPrompt = IO.readln("⌨️ Your prompt: ");
            IO.println();

            IO.println("===== 🤖 ANSWER 🤖 =====");
            IO.println(chatClient.prompt()
                    .user(userPrompt)
                    .call()
                    .content());
        };
    }
}
