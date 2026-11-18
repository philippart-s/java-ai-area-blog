///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 26+
// Keeps the parameter names in the bytecode: the name of the tool parameter is
// read from there.
//JAVAC_OPTIONS -parameters
// jboss-threads needs java.lang opened on Java 24+ (thread-local reset capability).
//JAVA_OPTIONS --add-opens=java.base/java.lang=ALL-UNNAMED
//DEPS io.quarkus.platform:quarkus-bom:3.33.4.1@pom
//DEPS io.quarkiverse.langchain4j:quarkus-langchain4j-openai:1.14.2
//
// Function calling example calling OVHcloud AI Endpoints (gpt-oss-120b)
// through Quarkus + quarkus-langchain4j
// (https://github.com/quarkiverse/quarkus-langchain4j).
//
// Single-file Quarkus application run by JBang. A bean method annotated with
// @Tool is declared on the AI service. The model does not answer the question:
// it asks for the method to be called. The extension runs it, sends the result
// back, and the model then writes the final answer.
//
// Docs: https://www.ovhcloud.com/en/public-cloud/ai-endpoints/catalog/gpt-oss-120b/
// Docs: https://docs.quarkiverse.io/quarkus-langchain4j/dev/function-calling.html

//FILES application.properties

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.observability.api.event.ToolExecutedEvent;
import dev.langchain4j.service.SystemMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import java.util.concurrent.ThreadLocalRandom;

record RoomCapacity(String room, int capacity) {}

// The function the model is allowed to ask for. The annotations are its
// description, sent to the model as a JSON schema. A random number stands in for a
// real lookup: the model cannot guess it, so the answer has to come from here.
@ApplicationScoped
class RoomTools {
    @Tool("Returns the number of seats of a conference room")
    RoomCapacity getRoomCapacity(@P("Name of the room, e.g. Coral") String room) {
        var capacity = ThreadLocalRandom.current().nextInt(50, 501);

        IO.println("🐞 getRoomCapacity called for room \"" + room + "\", returning capacity " + capacity);
        IO.println();

        return new RoomCapacity(room, capacity);
    }
}

// The AI service calls the model, runs the tool the model asks for and calls the
// model again with the result.
@RegisterAiService(tools = RoomTools.class)
interface Assistant {
    @SystemMessage("provide a concise answer")
    String chat(String userMessage);
}

@QuarkusMain
public class _04_06_FunctionCalling implements QuarkusApplication {

    @Inject
    Assistant assistant;

    // @ActivateRequestContext makes the CDI request scope available for the call.
    @Override
    @ActivateRequestContext
    public int run(String... args) {
        var question = "What is the capacity of the Nautilus room?";

        IO.println("===== ❓ QUESTION ❓ =====");
        IO.println(question);
        IO.println();

        // The tool banners are printed during this call, hence the answer kept aside.
        var answer = assistant.chat(question);

        IO.println("===== 🤖 ANSWER 🤖 =====");
        IO.println(answer);
        return 0;
    }

    // Fired by the extension once a tool has run: it only shows what happened.
    void onToolExecuted(@Observes ToolExecutedEvent event) {
        IO.println("===== 🛠️ TOOL CALL (requested by the model) 🛠️ =====");
        IO.println(event.request().name() + " " + event.request().arguments());
        IO.println();

        IO.println("===== 🛠️ TOOL RESULT (sent back to the model) 🛠️ =====");
        IO.println(event.resultText());
        IO.println();
    }
}
