///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 26+
//DEPS com.fasterxml.jackson.core:jackson-databind:2.18.2
//
// Streaming chatbot with a PERSISTENT memory example calling OVHcloud AI
// Endpoints (gpt-oss-120b).
// Java 26 + JBang port of 00_bash/00.04_StreamingChatbotFileMemory.sh.
//
// Same idea as _01_03 but the memory outlives the process: it is written to a
// file, so the conversation survives quitting the program.
//
// _01_03 already showed the important part: the chat completions API is
// stateless, so the "memory" is just a messages array kept client side and
// resent in full on every call. That array lived in an ArrayNode, and died with
// the JVM.
//
// The only new idea here is where that array lives. And at this level the answer
// is still almost embarrassing: the memory is ALREADY a JSON node, so saving it
// is one writeValueAsString and reloading it is one readTree. Nothing to map,
// nothing to convert. Remember this when the same feature comes back in
// _02_04: as soon as the SDK gives us typed objects instead of JSON, this stops
// being free.
//
// Run the program twice: the second run picks the conversation up where you left
// it, and the model still knows your name.
//
// The memory is written after EVERY answer, not once at the end, so quitting
// with Ctrl+C loses nothing.
//
// HTTP  -> java.net.http.HttpClient with BodyHandlers.ofInputStream() to
//          read the SSE stream line by line.
// JSON  -> Jackson (the messages ArrayNode IS the memory, and IS the file)
//
// Docs: https://www.ovhcloud.com/en/public-cloud/ai-endpoints/catalog/gpt-oss-120b/

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
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

  // Where the conversation is stored. The path is relative to the CURRENT
  // directory, not to this file: unlike a shell script, which can locate itself
  // with $0, the JVM started by JBang has no idea where the .java it runs lives
  // (its code source points into the JBang cache). This is why run.sh does a
  // "cd" into the example directory before calling JBang.
  // The conversation is named, like a session id would be: one file per name, so
  // running with another sessionId gives you another, separate conversation.
  final String sessionId = "cli-session";
  final var memoryFile = Path.of(".memory", sessionId + ".json");

  Files.createDirectories(memoryFile.getParent());

  var mapper = new ObjectMapper();

  // The JSON request body is built once, and its messages array is the
  // conversation memory.
  ObjectNode body = mapper.createObjectNode();
  body.put("model", model);
  body.put("stream", true);

  // 1) Load the memory: the whole conversation is the file, so reading it back
  // is just readTree. If there is no file yet (first run), start a new
  // conversation seeded with the system message, exactly as _01_03 did.
  ArrayNode messages;
  if (Files.exists(memoryFile) && Files.size(memoryFile) > 0) {
    messages = (ArrayNode) mapper.readTree(Files.readString(memoryFile));
    body.set("messages", messages);
    IO.println("===== 🧠 MEMORY RESTORED FROM DISK (" + messages.size() + " messages) 🧠 =====");
    IO.println(mapper.writerWithDefaultPrettyPrinter()
        .writeValueAsString(messages));
    IO.println();
  } else {
    messages = body.putArray("messages");
    messages.addObject()
        .put("role", "system")
        .put("content", "provide a concise answer");
    IO.println("===== 🧠 NO MEMORY YET, STARTING A NEW CONVERSATION 🧠 =====");
    IO.println();
  }

  IO.println("===== 🧠 CHATBOT WITH PERSISTENT MEMORY (type \"exit\" to quit) 🧠 =====");
  IO.println("💾 stored in " + memoryFile.toAbsolutePath());
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

      // 2) Append the user message to the memory.
      messages.addObject()
          .put("role", "user")
          .put("content", userPrompt);

      // 3) Print the JSON payload sent to the model, pretty-printed: on a second
      // run, the messages array does not start empty anymore. The WHOLE memory
      // is sent again, restored part included.
      IO.println("===== ⬆️  JSON REQUEST (memory sent to the model) ⬆️  =====");
      IO.println(mapper.writerWithDefaultPrettyPrinter()
          .writeValueAsString(body));
      IO.println();

      // 4) Send the request. BodyHandlers.ofInputStream() lets us read the SSE
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

      // 5) Append the model answer to the memory, so the next call gets the full
      // conversation: this is what makes the model look like it remembers.
      messages.addObject()
          .put("role", "assistant")
          .put("content", answer.toString());

      // 6) Save the memory. THIS is the whole point of the example, and it is a
      // single call: the memory never stopped being a JSON node, so there is
      // nothing to serialize by hand. Writing after every answer (rather than
      // once at the end) means an interrupted session is still saved.
      Files.writeString(memoryFile, mapper.writerWithDefaultPrettyPrinter()
          .writeValueAsString(messages));
      IO.println("💾 memory saved to " + memoryFile + " (" + messages.size() + " messages)");
      IO.println();
    }
  }

  // Print the final memory: the whole conversation, now kept on disk. Run the
  // program again and this is exactly what it will start from.
  IO.println("===== 🧠 FINAL MEMORY (kept in " + memoryFile + ") 🧠 =====");
  IO.println(mapper.writerWithDefaultPrettyPrinter()
      .writeValueAsString(messages));
  IO.println();
  IO.println("🗑️  Delete " + memoryFile + " to start a fresh conversation.");
}
