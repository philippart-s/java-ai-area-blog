///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 26+
//DEPS com.openai:openai-java:4.52.0
//
// Streaming chatbot with a PERSISTENT memory example calling OVHcloud AI
// Endpoints (gpt-oss-120b) through the official OpenAI Java SDK
// (https://github.com/openai/openai-java).
// Java 26 + JBang port of 01_pure_java/_01_04_StreamingChatbotFileMemory.java.
//
// Same idea as _02_03 but the memory outlives the process: it is written to a
// file, so the conversation survives quitting the program.
//
// The interesting part of this example is what the SDK does and does NOT give
// us.
//
// It does NOT give us a memory. There is no store, no session, no persistence
// helper: the whole com.openai.helpers package is two stream accumulators.
//
// The SDK does have stateful APIs — the Responses service (store=true plus
// previous_response_id) and the Conversations service — but they keep the
// conversation on the PROVIDER's servers, not in a file. And they are not an
// option here: AI Endpoints documents that "statefulness for v1/responses is
// currently not managed", requires store=false, and supports neither
// previous_response_id nor server-side conversation objects. Asking for it
// answers 400 "stateful mode not supported (store=false is required)".
// What that documentation calls a multi-turn conversation is precisely what we
// already do: resend the whole history on every call. So the conversation stays
// ours to keep, exactly as in _02_03.
//
// It DOES give us serialization. ObjectMappers.jsonMapper() is the SDK's own
// Jackson mapper, already configured for its own types, so the typed messages
// round-trip to JSON and back without us writing a mapping layer — union types
// included. It has to be that mapper: a plain new ObjectMapper() produces
// something that looks right but cannot be read back (see the comment on it
// below). Jackson is therefore not a dependency we add for ourselves, and there
// is no extra //DEPS above: it comes with the SDK.
//
// The result is that saving stays a one-liner, and the file it produces is the
// same {"role": ..., "content": ...} array as 00.04 and _01_04 — the SDK simply
// spells assistant messages out in full, adding "refusal" and "tool_calls".
// Both are valid fields of the wire format, so the files stay interchangeable in
// BOTH directions: a conversation started in bash can be continued here, and a
// conversation started here can be continued in _01_04.
//
// So what will a framework add, if not this? Everything AROUND the bytes: when
// to save, how much conversation to keep, how to name it, when to drop it.
// _03_05 answers all of that with one ChatMemoryStore interface.
//
// Run the program twice: the second run picks the conversation up where you left
// it, and the model still knows your name.
//
// Docs: https://www.ovhcloud.com/en/public-cloud/ai-endpoints/catalog/gpt-oss-120b/
// Docs: https://docs.ovhcloud.com/en/guides/public-cloud/ai-machine-learning/ai-endpoints-responses-api#multi-turn-conversations

import com.fasterxml.jackson.core.type.TypeReference;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.core.ObjectMappers;
import com.openai.core.http.StreamResponse;
import com.openai.helpers.ChatCompletionAccumulator;
import com.openai.models.chat.completions.ChatCompletionChunk;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionMessageParam;

void main() throws Exception {
    // OVHcloud AI Endpoints configuration.
    // baseUrl points at the OpenAI-compatible endpoint (note the /v1 suffix);
    // the token is read from the OVH_AI_ENDPOINTS_ACCESS_TOKEN environment variable.
    final String baseUrl = "https://oai.endpoints.kepler.ai.cloud.ovh.net/v1";
    final String model = "gpt-oss-120b";
    final String token = System.getenv("OVH_AI_ENDPOINTS_ACCESS_TOKEN");

    // Where the conversation is stored. The path is relative to the CURRENT
    // directory, not to this file: the JVM started by JBang has no idea where the
    // .java it runs lives, which is why run.sh does a "cd" into the example
    // directory before calling JBang.
    // The conversation is named, like a session id would be: one file per name, so
    // running with another sessionId gives you another, separate conversation.
    final String sessionId = "cli-session";
    final var memoryFile = Path.of(".memory", sessionId + ".json");

    Files.createDirectories(memoryFile.getParent());

    // The SDK's own Jackson mapper, from com.openai.core. It is not a matter of
    // taste: a plain new ObjectMapper() does NOT round-trip these types. It walks
    // the getters, emits a spurious "valid": true on every message, and fails to
    // read its own output back. The SDK's mapper knows how to handle them.
    var mapper = ObjectMappers.jsonMapper();

    // Build the SDK client, pointed at OVHcloud AI Endpoints.
    OpenAIClient client = OpenAIOkHttpClient.builder()
            .baseUrl(baseUrl)
            .apiKey(token)
            //.logLevel(LogLevel.DEBUG)
            .build();

    // The params builder is still the conversation memory, exactly as in _02_03:
    // build() is called at each turn and returns the WHOLE conversation.
    var paramsBuilder = ChatCompletionCreateParams.builder()
            .model(model);

    // 1) Load the memory. messages() REPLACES the whole list, so restoring a
    // conversation is a single call: no need to replay the messages one by one,
    // and no need to know which role each one had.
    if (Files.exists(memoryFile) && Files.size(memoryFile) > 0) {
        List<ChatCompletionMessageParam> restored = mapper.readValue(
                Files.readString(memoryFile),
                new TypeReference<List<ChatCompletionMessageParam>>() {});
        paramsBuilder.messages(restored);
        IO.println("===== 🧠 MEMORY RESTORED FROM DISK (" + restored.size() + " messages) 🧠 =====");
        restored.forEach(IO::println);
        IO.println();
    } else {
        // First run: start a new conversation seeded with the system message.
        paramsBuilder.addSystemMessage("provide a concise answer");
        IO.println("===== 🧠 NO MEMORY YET, STARTING A NEW CONVERSATION 🧠 =====");
        IO.println();
    }

    IO.println("===== 🧠 CHATBOT WITH PERSISTENT MEMORY (type \"exit\" to quit) 🧠 =====");
    IO.println("💾 stored in " + memoryFile.toAbsolutePath());
    IO.println();

    while (true) {
        // Ask the user for a prompt.
        var userPrompt = IO.readln("⌨️  Your prompt: ");
        IO.println();

        // Leave the loop on "exit", or on end of input (Ctrl+D).
        if (userPrompt == null || userPrompt.equals("exit")) break;
        if (userPrompt.isBlank()) continue;

        // 2) Append the user message to the memory.
        paramsBuilder.addUserMessage(userPrompt);

        // 3) Print the messages of the request (typed SDK objects, the rest of
        // the params is left out to keep the output readable): on a second run,
        // the list does not start empty anymore. The WHOLE memory is sent again,
        // restored part included.
        var params = paramsBuilder.build();
        IO.println("===== ⬆️ REQUEST (memory sent to the model) ⬆️ =====");
        params.messages().forEach(IO::println);
        IO.println();

        // 4) Call the endpoint in streaming mode and print the answer token by
        // token. The accumulator collects the chunks along the way (peek) and
        // rebuilds the complete ChatCompletion, exactly as the non-streaming
        // call would have returned it.
        // The StreamResponse is AutoCloseable, so we close it with try-with-resources.
        IO.println("===== 🤖 ANSWER (streaming) 🤖 =====");
        var accumulator = ChatCompletionAccumulator.create();
        try (StreamResponse<ChatCompletionChunk> stream =
                     client.chat().completions().createStreaming(params)) {
            stream.stream()
                    .peek(accumulator::accumulate)
                    .flatMap(chunk -> chunk.choices().stream())
                    .flatMap(choice -> choice.delta().content().stream())
                    .forEach(IO::print);
        }

        // Newlines once the stream is complete.
        IO.println();
        IO.println();

        // 5) Append the model answer to the memory, so the next call gets the
        // full conversation: this is what makes the model look like it
        // remembers. This is NOT a second pass over the stream: what we add is
        // the single message reassembled by the accumulator, taken as is.
        paramsBuilder.addMessage(accumulator.chatCompletion()
                .choices()
                .getFirst()
                .message());

        // 6) Save the memory. The SDK's mapper writes its own typed messages
        // straight to JSON, so this stays as cheap as it was in _01_04 — and it
        // produces the very same {"role": ..., "content": ...} array.
        // Writing after every answer (rather than once at the end) means an
        // interrupted session is still saved.
        var updated = paramsBuilder.build().messages();
        Files.writeString(memoryFile, mapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(updated));
        IO.println("💾 memory saved to " + memoryFile + " (" + updated.size() + " messages)");
        IO.println();
    }

    // Print the final memory: the whole conversation, now kept on disk. Run the
    // program again and this is exactly what it will start from.
    IO.println("===== 🧠 FINAL MEMORY (kept in " + memoryFile + ") 🧠 =====");
    paramsBuilder.build().messages().forEach(IO::println);
    IO.println();
    IO.println("🗑️  Delete " + memoryFile + " to start a fresh conversation.");
}
