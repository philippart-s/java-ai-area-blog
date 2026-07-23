/// usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 26+
//DEPS com.fasterxml.jackson.core:jackson-databind:2.18.2
//
// Streaming chatbot example calling OVHcloud AI Endpoints (gpt-oss-120b).
// Java 26 + JBang port of 00_bash/00.02_StreamingChatbot.sh.
//
// Same idea as 01.01 but with streaming enabled: the endpoint returns
// Server-Sent Events (SSE) and the answer is printed token by token,
// as soon as each chunk arrives.
//
// HTTP  -> java.net.http.HttpClient with BodyHandlers.ofInputStream() to
//          read the SSE stream line by line.
// JSON  -> Jackson (serialization + parsing each streamed chunk)
//
// Docs: https://www.ovhcloud.com/en/public-cloud/ai-endpoints/catalog/gpt-oss-120b/

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

void main() throws Exception {
  // OVHcloud AI Endpoints configuration.
  // The token is read from the OVH_AI_ENDPOINTS_ACCESS_TOKEN environment variable.
  final String endpoint = "https://oai.endpoints.kepler.ai.cloud.ovh.net/v1/chat/completions";
  final String model = "gpt-oss-120b";
  final String token = System.getenv("OVH_AI_ENDPOINTS_ACCESS_TOKEN");

  var mapper = new ObjectMapper();

  // Ask the user for a prompt.
  var userPrompt = IO.readln("⌨️ Your prompt: ");
  IO.println();

  // Build the JSON request body. The key difference is "stream": true.
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

  // Print the JSON payload sent to the model, pretty-printed.
  IO.println("===== ⬆️  JSON REQUEST (payload sent to the model) ⬆️  =====");
  IO.println(mapper.writerWithDefaultPrettyPrinter()
      .writeValueAsString(body));
  IO.println();

  // Send the request. BodyHandlers.ofInputStream() lets us read the SSE
  // stream ourselves, line by line, as chunks arrive.
  // NOTE: the client must stay open while we read the streaming body, so we
  // do NOT wrap it in a try-with-resources (closing it would close the stream).
  var client = HttpClient.newHttpClient();
  var request = HttpRequest.newBuilder(URI.create(endpoint))
      .header("Content-Type", "application/json")
      .header("Authorization", "Bearer " + token)
      .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
      .build();
  var response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());

  // Print the answer token by token.
  IO.println("===== 🤖 ANSWER (streaming) 🤖 =====");
  try (var reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
    String line;
    while ((line = reader.readLine()) != null) {
      // DEBUG: uncomment to print the raw SSE line exactly as received.
      // IO.println(line);

      // SSE lines look like: "data: {json}" and end with "data: [DONE]".
      if (!line.startsWith("data: ")) continue;   // skip empty / keep-alive lines
      var data = line.substring("data: ".length());
      if (data.equals("[DONE]")) break;           // end of the stream: stop before reading further

      // Extract the incremental text from this chunk and print it without newline.
      var content = mapper.readTree(data)
          .at("/choices/0/delta/content");
      if (!content.isMissingNode()) {
        IO.print(content.asText());
      }
    }
  }

  // Final newline once the stream is complete.
  IO.println();
}
