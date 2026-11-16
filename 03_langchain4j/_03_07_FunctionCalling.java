///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 26+
//DEPS dev.langchain4j:langchain4j:1.22.0
//DEPS dev.langchain4j:langchain4j-open-ai:1.22.0
//DEPS org.slf4j:slf4j-nop:2.0.20
//
// Function calling example calling OVHcloud AI Endpoints (gpt-oss-120b)
// through LangChain4j AI Services (https://github.com/langchain4j/langchain4j).
//
// A method annotated with @Tool is registered on the AI Service. The model does
// not answer the question: it asks for the method to be called. LangChain4j runs
// it, sends the result back, and the model then writes the final answer.
//
// Docs: https://www.ovhcloud.com/en/public-cloud/ai-endpoints/catalog/gpt-oss-120b/
// Docs: https://docs.langchain4j.dev/tutorials/tools

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;

interface Assistant {
    @SystemMessage("provide a concise answer")
    String chat(String userMessage);
}

record RoomCapacity(String room, int capacity) {}

// The function the model is allowed to ask for. The annotations are its
// description, sent to the model as a JSON schema. A random number stands in for a
// real lookup: the model cannot guess it, so the answer has to come from here.
class RoomTools {
    @Tool("Returns the number of seats of a conference room")
    RoomCapacity getRoomCapacity(@P("Name of the room, e.g. Coral") String room) {
        return new RoomCapacity(room, ThreadLocalRandom.current().nextInt(50, 501));
    }
}

void main() {
    // baseUrl must include the /v1 suffix. The token is read from
    // OVH_AI_ENDPOINTS_ACCESS_TOKEN, exported by run.sh from .env.
    final String baseUrl = "https://oai.endpoints.kepler.ai.cloud.ovh.net/v1";
    final String model = "gpt-oss-120b";
    final String token = System.getenv("OVH_AI_ENDPOINTS_ACCESS_TOKEN");

    var question = "What is the capacity of the Nautilus room?";

    ChatModel chatModel = OpenAiChatModel.builder()
            .baseUrl(baseUrl)
            .apiKey(token)
            .modelName(model)
            // .logRequests(true)
            // .logResponses(true)
            .build();

    // The AI Service calls the model, runs the tool the model asks for and calls
    // the model again with the result. The two callbacks only show it happening.
    Assistant assistant = AiServices.builder(Assistant.class)
            .chatModel(chatModel)
            .tools(new RoomTools())
            .beforeToolExecution(before -> {
                IO.println("===== 🛠️ TOOL CALL (requested by the model) 🛠️ =====");
                IO.println(before.request().name() + " " + before.request().arguments());
                IO.println();
            })
            .afterToolExecution(after -> {
                IO.println("===== 🛠️ TOOL RESULT (sent back to the model) 🛠️ =====");
                IO.println(after.result());
                IO.println();
            })
            .build();

    IO.println("===== ❓ QUESTION ❓ =====");
    IO.println(question);
    IO.println();

    // The tool banners are printed during this call, hence the answer kept aside.
    var answer = assistant.chat(question);

    IO.println("===== 🤖 ANSWER 🤖 =====");
    IO.println(answer);
}
