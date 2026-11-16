///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 26+
//DEPS com.fasterxml.jackson.core:jackson-databind:2.22.4
//
// Function calling example calling OVHcloud AI Endpoints (gpt-oss-120b),
// with the JDK HttpClient and Jackson.
//
// The request describes a get_room_capacity function. The model does not answer
// the question: it asks for the function to be called. The program runs it, sends
// the result back, and the model then writes the final answer.
//
// Docs: https://www.ovhcloud.com/en/public-cloud/ai-endpoints/catalog/gpt-oss-120b/
// Docs: https://platform.openai.com/docs/guides/function-calling

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

final String ENDPOINT = "https://oai.endpoints.kepler.ai.cloud.ovh.net/v1/chat/completions";
final String MODEL = "gpt-oss-120b";

// The token is read from OVH_AI_ENDPOINTS_ACCESS_TOKEN, exported by run.sh from .env.
final String TOKEN = System.getenv("OVH_AI_ENDPOINTS_ACCESS_TOKEN");

final ObjectMapper MAPPER = new ObjectMapper();
final HttpClient CLIENT = HttpClient.newHttpClient();

// The function the model is allowed to ask for. A random number stands in for a
// real lookup: the model cannot guess it, so the answer has to come from here.
String getRoomCapacity(String room) {
    return MAPPER.createObjectNode()
            .put("room", room)
            .put("capacity", ThreadLocalRandom.current().nextInt(50, 501))
            .toString();
}

void main() throws Exception {
    var question = "What is the capacity of the Nautilus room?";

    // The description of the function, as a JSON schema. This is all the model
    // knows about it: the Java code above never leaves this machine.
    var tools = MAPPER.readTree("""
            [
              {
                "type": "function",
                "function": {
                  "name": "get_room_capacity",
                  "description": "Returns the number of seats of a conference room",
                  "parameters": {
                    "type": "object",
                    "properties": {
                      "room": { "type": "string", "description": "Name of the room, e.g. Coral" }
                    },
                    "required": ["room"]
                  }
                }
              }
            ]""");

    // The same body is sent twice: only its messages array grows in between.
    var body = MAPPER.createObjectNode();
    body.put("model", MODEL);
    var messages = body.putArray("messages");
    messages.addObject()
            .put("role", "system")
            .put("content", "provide a concise answer");
    messages.addObject()
            .put("role", "user")
            .put("content", question);
    body.set("tools", tools);

    // 1) First call: the question and the description of the function.
    IO.println("===== ⬆️ JSON REQUEST (question + tools sent to the model) ⬆️ =====");
    IO.println(body.toPrettyString());
    IO.println();

    JsonNode response = post(body.toString());

    IO.println("===== ⬇️ JSON RESPONSE ⬇️ =====");
    IO.println(response.toPrettyString());
    IO.println();

    // 2) The model answered with a tool call instead of a text: a function name and
    //    its arguments, themselves a JSON document carried in a string.
    JsonNode assistantMessage = response.at("/choices/0/message");
    JsonNode toolCall = assistantMessage.at("/tool_calls/0");
    var functionName = toolCall.at("/function/name").asText();
    var arguments = toolCall.at("/function/arguments").asText();

    IO.println("===== 🛠️ TOOL CALL (requested by the model) 🛠️ =====");
    IO.println(functionName + " " + arguments);
    IO.println();

    // 3) Run the function. The model only named it, calling it is up to the program.
    var room = MAPPER.readTree(arguments).get("room").asText();
    var result = getRoomCapacity(room);

    IO.println("===== 🛠️ TOOL RESULT (sent back to the model) 🛠️ =====");
    IO.println(result);
    IO.println();

    // 4) Second call: the conversation so far, plus the tool call of the model and
    //    its result, tied together by the tool call id.
    messages.add(assistantMessage);
    messages.addObject()
            .put("role", "tool")
            .put("tool_call_id", toolCall.get("id").asText())
            .put("content", result);

    IO.println("===== ⬆️ JSON REQUEST (tool call + tool result sent to the model) ⬆️ =====");
    IO.println(body.toPrettyString());
    IO.println();

    response = post(body.toString());

    IO.println("===== 🤖 ANSWER 🤖 =====");
    IO.println(response.at("/choices/0/message/content").asText());
}

JsonNode post(String body) throws Exception {
    var request = HttpRequest.newBuilder(URI.create(ENDPOINT))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + TOKEN)
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
    return MAPPER.readTree(CLIENT.send(request, HttpResponse.BodyHandlers.ofString()).body());
}
