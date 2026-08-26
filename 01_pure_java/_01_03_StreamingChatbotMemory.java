/// usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 26+
//DEPS com.fasterxml.jackson.core:jackson-databind:2.18.2
//
// Streaming chatbot with memory example calling OVHcloud AI Endpoints
// (gpt-oss-120b).
// Java 26 + JBang port of 00_bash/00.03_StreamingChatbotMemory.sh.
//
// Same idea as 01.02 but with a conversation memory: the chat completions API
// is stateless, so the model remembers nothing between two calls. The "memory"
// is entirely on the client side: we keep the whole conversation in a messages
// array and send it back, in full, on every request.
//
// The answer is still streamed token by token, and rebuilt on the fly so it can
// be stored in the memory once complete.
//
// The program loops so you can chat with the model, and prints the messages
// array before each call: you literally see the memory growing turn after turn.
// Type "exit" (or press Ctrl+D) to quit.
//
// HTTP  -> java.net.http.HttpClient with BodyHandlers.ofInputStream() to
//          read the SSE stream line by line.
// JSON  -> Jackson (the messages ArrayNode IS the memory)
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

  // The JSON request body is built once, and its messages array is the
  // conversation memory: it is seeded with the system message, then every user
  // prompt and every model answer is appended to it.
  ObjectNode body = mapper.createObjectNode();
  body.put("model", model);
  body.put("stream", true);
  var messages = body.putArray("messages");
  messages.addObject()
      .put("role", "system")
      .put("content", "provide a concise answer");

  IO.println("===== 🧠 CHATBOT WITH MEMORY (type \"exit\" to quit) 🧠 =====");
  IO.println();

  // The same client is reused for every turn of the conversation. It is only
  // closed when the chat is over: each streamed response is fully read inside
  // the loop, so closing it here never cuts a stream short.
  try (var client = HttpClient.newHttpClient()) {
    while (true) {
      // Ask the user for a prompt.
      var userPrompt = IO.readln("⌨️  Your prompt: ");
      IO.println();

      // Leave the loop on "exit", or on end of input (Ctrl+D).
      if (userPrompt == null || userPrompt.equals("exit")) break;
      if (userPrompt.isBlank()) continue;

      // 1) Append the user message to the memory.
      messages.addObject()
          .put("role", "user")
          .put("content", userPrompt);

      // 2) Print the JSON payload sent to the model, pretty-printed: notice how
      // the messages array grows at each turn. The WHOLE memory is sent again.
      IO.println("===== ⬆️  JSON REQUEST (memory sent to the model) ⬆️  =====");
      IO.println(mapper.writerWithDefaultPrettyPrinter()
          .writeValueAsString(body));
      IO.println();

      // 3) Send the request. BodyHandlers.ofInputStream() lets us read the SSE
      // stream ourselves, line by line, as chunks arrive.
      var request = HttpRequest.newBuilder(URI.create(endpoint))
          .header("Content-Type", "application/json")
          .header("Authorization", "Bearer " + token)
          .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
          .build();
      var response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());

      // Print the answer token by token, while rebuilding the full answer:
      // we need it complete to store it in the memory.
      IO.println("===== 🤖 ANSWER (streaming) 🤖 =====");
      var answer = new StringBuilder();
      try (var reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
        String line;
        while ((line = reader.readLine()) != null) {
          // SSE lines look like: "data: {json}" and end with "data: [DONE]".
          if (!line.startsWith("data: ")) continue;   // skip empty / keep-alive lines
          var data = line.substring("data: ".length());
          if (data.equals("[DONE]")) break;           // end of the stream: stop before reading further

          // Extract the incremental text from this chunk, print it without
          // newline and append it to the answer being rebuilt.
          var content = mapper.readTree(data)
              .at("/choices/0/delta/content");
          if (!content.isMissingNode()) {
            IO.print(content.asText());
            answer.append(content.asText());
          }
        }
      }

      // Final newline once the stream is complete.
      IO.println();
      IO.println();

      // 4) Append the model answer to the memory, so the next call gets the full
      // conversation: this is what makes the model look like it remembers.
      messages.addObject()
          .put("role", "assistant")
          .put("content", answer.toString());
    }
  }

  // Print the final memory: the whole conversation, kept client side.
  IO.println("===== 🧠 FINAL MEMORY (the whole conversation) 🧠 =====");
  IO.println(mapper.writerWithDefaultPrettyPrinter()
      .writeValueAsString(messages));
}
