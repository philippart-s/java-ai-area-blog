///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 26+
//DEPS com.openai:openai-java:4.94.0
//
// Function calling example calling OVHcloud AI Endpoints (gpt-oss-120b)
// through the official OpenAI Java SDK (https://github.com/openai/openai-java).
//
// The request describes a get_room_capacity function. The model does not answer
// the question: it asks for the function to be called. The program runs it, sends
// the result back, and the model then writes the final answer.
//
// Docs: https://www.ovhcloud.com/en/public-cloud/ai-endpoints/catalog/gpt-oss-120b/
// Docs: https://github.com/openai/openai-java#function-calling-with-json-schemas

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionMessage;
import com.openai.models.chat.completions.ChatCompletionToolMessageParam;

// The function the model is allowed to ask for. The record is its description
// (name, purpose, parameters) and call() is its code. A random number stands in
// for a real lookup: the model cannot guess it, so the answer has to come from here.
@JsonTypeName("get_room_capacity")
@JsonClassDescription("Returns the number of seats of a conference room")
record GetRoomCapacity(@JsonPropertyDescription("Name of the room, e.g. Coral") String room) {
    RoomCapacity call() {
        var capacity = ThreadLocalRandom.current().nextInt(50, 501);

        IO.println("🐞 GetRoomCapacity called for room \"" + room + "\", returning capacity " + capacity);
        IO.println();

        return new RoomCapacity(room, capacity);
    }
}

record RoomCapacity(String room, int capacity) {}

void main() {
    // baseUrl must include the /v1 suffix. The token is read from
    // OVH_AI_ENDPOINTS_ACCESS_TOKEN, exported by run.sh from .env.
    final String baseUrl = "https://oai.endpoints.kepler.ai.cloud.ovh.net/v1";
    final String model = "gpt-oss-120b";
    final String token = System.getenv("OVH_AI_ENDPOINTS_ACCESS_TOKEN");

    var question = "What is the capacity of the Nautilus room?";

    OpenAIClient client = OpenAIOkHttpClient.builder()
            .baseUrl(baseUrl)
            .apiKey(token)
            //.logLevel(LogLevel.DEBUG)
            .build();

    // The same builder serves both calls: only its messages grow in between.
    var paramsBuilder = ChatCompletionCreateParams.builder()
            .model(model)
            .addSystemMessage("provide a concise answer")
            .addUserMessage(question)
            // The SDK derives the JSON schema of the function from the record.
            .addTool(GetRoomCapacity.class);

    // 1) First call: the question and the description of the function.
    var params = paramsBuilder.build();
    IO.println("===== ⬆️ REQUEST (question + tools sent to the model) ⬆️ =====");
    params.messages().forEach(IO::println);
    params.tools().orElseThrow().forEach(IO::println);
    IO.println();

    ChatCompletion response = client.chat().completions().create(params);

    IO.println("===== ⬇️ RESPONSE (SDK object) ⬇️ =====");
    IO.println(response.toString());
    IO.println();

    // 2) The model answered with a tool call instead of a text: a function name and
    //    its arguments, themselves a JSON document carried in a string.
    ChatCompletionMessage assistantMessage = response.choices().getFirst().message();
    var toolCalls = assistantMessage.toolCalls().orElse(List.of());

    // Asking for a function is a choice of the model, not an obligation: without a
    // tool call its message already holds the answer and there is nothing to run.
    if (toolCalls.isEmpty()) {
        IO.println("===== 🤖 ANSWER 🤖 =====");
        IO.println(assistantMessage.content().orElse(""));
        return;
    }

    var toolCall = toolCalls.getFirst().asFunction();

    IO.println("===== 🛠️ TOOL CALL (requested by the model) 🛠️ =====");
    IO.println(toolCall.function().name() + " " + toolCall.function().arguments());
    IO.println();

    // 3) Run the function. The model only named it, calling it is up to the program,
    //    which dispatches on the name instead of trusting it to be the right one.
    //    arguments(Class) parses the JSON arguments into the record.
    if (!"get_room_capacity".equals(toolCall.function().name())) {
        IO.println("Unexpected function requested by the model: " + toolCall.function().name());
        return;
    }

    var result = toolCall.function().arguments(GetRoomCapacity.class).call();

    var toolMessage = ChatCompletionToolMessageParam.builder()
            .toolCallId(toolCall.id())
            .contentAsJson(result)
            .build();

    IO.println("===== 🛠️ TOOL RESULT (sent back to the model) 🛠️ =====");
    IO.println(toolMessage.content().asText());
    IO.println();

    // 4) Second call: the conversation so far, plus the tool call of the model and
    //    its result, tied together by the tool call id.
    paramsBuilder
            .addMessage(assistantMessage)
            .addMessage(toolMessage);

    params = paramsBuilder.build();
    IO.println("===== ⬆️ REQUEST (tool call + tool result sent to the model) ⬆️ =====");
    params.messages().forEach(IO::println);
    IO.println();

    response = client.chat().completions().create(params);

    IO.println("===== 🤖 ANSWER 🤖 =====");
    response.choices().stream()
            .flatMap(choice -> choice.message().content().stream())
            .forEach(IO::println);
}
