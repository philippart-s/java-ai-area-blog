///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 26+
//DEPS com.fasterxml.jackson.core:jackson-databind:2.18.2
//
// Simple chatbot example calling OVHcloud AI Endpoints (gpt-oss-120b),
// with the JDK HttpClient and Jackson.
//
// Docs: https://www.ovhcloud.com/en/public-cloud/ai-endpoints/catalog/gpt-oss-120b/

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

void main() throws Exception {
  // The token is read from OVH_AI_ENDPOINTS_ACCESS_TOKEN, exported by run.sh from .env.
  final String endpoint = "https://oai.endpoints.kepler.ai.cloud.ovh.net/v1/chat/completions";
  final String model = "gpt-oss-120b";
  final String token = System.getenv("OVH_AI_ENDPOINTS_ACCESS_TOKEN");

  var mapper = new ObjectMapper();

  var userPrompt = IO.readln("⌨️ Your prompt: ");
  IO.println();

  ObjectNode body = mapper.createObjectNode();
  body.put("model", model);
  var messages = body.putArray("messages");
  messages.addObject()
      .put("role", "system")
      .put("content", "provide a concise answer");
  messages.addObject()
      .put("role", "user")
      .put("content", userPrompt);

  IO.println("===== ⬆️ JSON REQUEST (payload sent to the model) ⬆️ =====");
  IO.println(mapper.writerWithDefaultPrettyPrinter()
      .writeValueAsString(body));
  IO.println();

  HttpResponse<String> response;
  try (var client = HttpClient.newHttpClient()) {
    var request = HttpRequest.newBuilder(URI.create(endpoint))
        .header("Content-Type", "application/json")
        .header("Authorization", "Bearer " + token)
        .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
        .build();
    response = client.send(request, HttpResponse.BodyHandlers.ofString());
  }

  JsonNode json = mapper.readTree(response.body());
  IO.println("===== ⬇️ JSON RESPONSE ⬇️ =====");
  IO.println(mapper.writerWithDefaultPrettyPrinter()
      .writeValueAsString(json));

  IO.println();
  IO.println("===== 🤖 ANSWER 🤖 =====");
  IO.println(json.at("/choices/0/message/content")
      .asText());
}
