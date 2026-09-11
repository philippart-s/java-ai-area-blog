///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 26+
//DEPS com.fasterxml.jackson.core:jackson-databind:2.18.2
//
// Streaming chatbot example calling OVHcloud AI Endpoints (gpt-oss-120b),
// with the JDK HttpClient and Jackson.
//
// The answer is printed token by token.
//
// Docs: https://www.ovhcloud.com/en/public-cloud/ai-endpoints/catalog/gpt-oss-120b/

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
  body.put("stream", true);
  var messages = body.putArray("messages");
  messages.addObject()
      .put("role", "system")
      .put("content", "provide a concise answer");
  messages.addObject()
      .put("role", "user")
      .put("content", userPrompt);

  IO.println("===== ⬆️  JSON REQUEST (payload sent to the model) ⬆️  =====");
  IO.println(mapper.writerWithDefaultPrettyPrinter()
      .writeValueAsString(body));
  IO.println();

  // ofInputStream() gives us the SSE stream to read line by line.
  // The client must stay open while we read it, so no try-with-resources here:
  // closing it would close the stream.
  var client = HttpClient.newHttpClient();
  var request = HttpRequest.newBuilder(URI.create(endpoint))
      .header("Content-Type", "application/json")
      .header("Authorization", "Bearer " + token)
      .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
      .build();
  var response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());

  IO.println("===== 🤖 ANSWER (streaming) 🤖 =====");
  try (var reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
    String line;
    while ((line = reader.readLine()) != null) {
      // Uncomment to see the raw SSE lines.
      // IO.println(line);

      // SSE lines look like "data: {json}", and the stream ends with "data: [DONE]".
      if (!line.startsWith("data: ")) continue;
      var data = line.substring("data: ".length());
      if (data.equals("[DONE]")) break;

      var content = mapper.readTree(data)
          .at("/choices/0/delta/content");
      if (!content.isMissingNode()) {
        IO.print(content.asText());
      }
    }
  }

  IO.println();
}
