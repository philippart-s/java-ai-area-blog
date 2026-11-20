///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 26+
//DEPS org.springframework.boot:spring-boot-dependencies:4.1.2@pom
//DEPS org.springframework.ai:spring-ai-bom:2.0.2@pom
//DEPS org.springframework.boot:spring-boot-starter:4.1.2
// AspectJ weaver, required by Spring AI's ChatClient advisors (AOP).
//DEPS org.aspectj:aspectjweaver:1.9.26
//DEPS org.springframework.ai:spring-ai-starter-model-openai:2.0.2
// Servlet API present only so Boot's web-servlet config classes resolve during
// bean introspection; the app stays non-web (spring.main.web-application-type=none).
//DEPS jakarta.servlet:jakarta.servlet-api:6.1.0
//
// Function calling example calling OVHcloud AI Endpoints (gpt-oss-120b)
// through Spring AI (https://docs.spring.io/spring-ai/reference/).
//
// Single-file Spring Boot application run by JBang. A method annotated with
// @Tool is handed to the ChatClient. The model does not answer the question: it
// asks for the method to be called. Spring AI runs it, sends the result back,
// and the model then writes the final answer.
//
// Docs: https://www.ovhcloud.com/en/public-cloud/ai-endpoints/catalog/gpt-oss-120b/
// Docs: https://docs.spring.io/spring-ai/reference/api/tools.html

//FILES application.properties

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;

import java.util.concurrent.ThreadLocalRandom;

record RoomCapacity(String room, int capacity) {}

// The function the model is allowed to ask for. The annotations are its
// description, sent to the model as a JSON schema. A random number stands in for a
// real lookup: the model cannot guess it, so the answer has to come from here.
class RoomTools {
    @Tool(description = "Returns the number of seats of a conference room")
    RoomCapacity getRoomCapacity(@ToolParam(description = "Name of the room, e.g. Coral") String room) {
        var capacity = ThreadLocalRandom.current().nextInt(50, 501);

        IO.println("🐞 getRoomCapacity called for room \"" + room + "\", returning capacity " + capacity);
        IO.println();

        return new RoomCapacity(room, capacity);
    }
}

// Wraps a tool to print what goes in and what comes out. Not needed for the tool
// to work: it only shows the call happening.
record LoggedTool(ToolCallback tool) implements ToolCallback {
    @Override
    public ToolDefinition getToolDefinition() {
        return tool.getToolDefinition();
    }

    @Override
    public String call(String arguments) {
        IO.println("===== 🛠️ TOOL CALL (requested by the model) 🛠️ =====");
        IO.println(getToolDefinition().name() + " " + arguments);
        IO.println();

        var result = tool.call(arguments);

        IO.println("===== 🛠️ TOOL RESULT (sent back to the model) 🛠️ =====");
        IO.println(result);
        IO.println();
        return result;
    }
}

// NOT @SpringBootApplication: this file lives in the default package, so its
// @ComponentScan would scan the whole classpath and break Spring's own config.
@SpringBootConfiguration
@EnableAutoConfiguration
public class _05_06_FunctionCalling {

    public static void main(String[] args) {
        SpringApplication.run(_05_06_FunctionCalling.class, args);
    }

    @Bean
    CommandLineRunner run(ChatClient.Builder builder) {
        return args -> {
            var question = "What is the capacity of the Nautilus room?";

            // ToolCallbacks.from() turns the @Tool methods into callbacks. Without the
            // logging wrapper, .defaultTools(new RoomTools()) does the same in one line.
            var chatClient = builder
                    .defaultSystem("provide a concise answer")
                    .defaultTools(new LoggedTool(ToolCallbacks.from(new RoomTools())[0]))
                    .build();

            IO.println("===== ❓ QUESTION ❓ =====");
            IO.println(question);
            IO.println();

            // The ChatClient calls the model, runs the tool the model asks for and
            // calls the model again with the result. The tool banners are printed
            // during this call, hence the answer kept aside.
            var answer = chatClient.prompt()
                    .user(question)
                    .call()
                    .content();

            IO.println("===== 🤖 ANSWER 🤖 =====");
            IO.println(answer);
        };
    }
}
