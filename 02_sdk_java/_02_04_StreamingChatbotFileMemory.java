///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 26+
//DEPS com.openai:openai-java:4.52.0
//
// Streaming chatbot with a persistent memory example calling OVHcloud AI
// Endpoints (gpt-oss-120b) through the official OpenAI Java SDK
// (https://github.com/openai/openai-java).
//
// The chat completions API is stateless, so the conversation must be resent in
// full on every call. The ChatCompletionCreateParams.Builder holds it, and its
// messages are read from and written to a JSON file, so the conversation
// survives quitting the program. Run it twice.
//
// The SDK has no memory or persistence API. Its stateful endpoints (Responses
// with store=true, Conversations) keep the conversation on the provider's
// servers, and AI Endpoints does not support them: it requires store=false and
// answers 400 "stateful mode not supported".
//
// Type "exit" (or press Ctrl+D) to quit.
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
    // baseUrl must include the /v1 suffix. The token is read from
    // OVH_AI_ENDPOINTS_ACCESS_TOKEN, exported by run.sh from .env.
    final String baseUrl = "https://oai.endpoints.kepler.ai.cloud.ovh.net/v1";
    final String model = "gpt-oss-120b";
    final String token = System.getenv("OVH_AI_ENDPOINTS_ACCESS_TOKEN");

    // Path relative to the current directory: a JBang script cannot locate its
    // own .java file, which is why run.sh cd's into the example directory first.
    // One file per session id, so another id is another conversation.
    final String sessionId = "cli-session";
    final var memoryFile = Path.of(".memory", sessionId + ".json");

    Files.createDirectories(memoryFile.getParent());

    // The SDK's own mapper, from com.openai.core. A plain new ObjectMapper()
    // does NOT round-trip these types: it walks the getters, emits a spurious
    // "valid": true on every message and cannot read its own output back.
    var mapper = ObjectMappers.jsonMapper();

    OpenAIClient client = OpenAIOkHttpClient.builder()
            .baseUrl(baseUrl)
            .apiKey(token)
            //.logLevel(LogLevel.DEBUG)
            .build();

    // The memory. build() is called at each turn and returns the whole conversation.
    var paramsBuilder = ChatCompletionCreateParams.builder()
            .model(model);

    // messages() replaces the whole list, so restoring is a single call: no need
    // to replay the messages one by one or to know which role each one had.
    if (Files.exists(memoryFile) && Files.size(memoryFile) > 0) {
        List<ChatCompletionMessageParam> restored = mapper.readValue(
                Files.readString(memoryFile),
                new TypeReference<>() {
                });
        paramsBuilder.messages(restored);
        IO.println("===== 🧠 MEMORY RESTORED FROM DISK (" + restored.size() + " messages) 🧠 =====");
        restored.forEach(IO::println);
        IO.println();
    } else {
        paramsBuilder.addSystemMessage("provide a concise answer");
        IO.println("===== 🧠 NO MEMORY YET, STARTING A NEW CONVERSATION 🧠 =====");
        IO.println();
    }

    IO.println("===== 🧠 CHATBOT WITH PERSISTENT MEMORY (type \"exit\" to quit) 🧠 =====");
    IO.println("💾 stored in " + memoryFile.toAbsolutePath());
    IO.println();

    while (true) {
        var userPrompt = IO.readln("⌨️  Your prompt: ");
        IO.println();

        if (userPrompt == null || userPrompt.equals("exit")) break;
        if (userPrompt.isBlank()) continue;

        paramsBuilder.addUserMessage(userPrompt);

        // Only the messages are printed; the rest of the params would drown the output.
        var params = paramsBuilder.build();
        IO.println("===== ⬆️ REQUEST (memory sent to the model) ⬆️ =====");
        params.messages().forEach(IO::println);
        IO.println();

        // peek() feeds the accumulator, which rebuilds the complete
        // ChatCompletion the non-streaming call would have returned.
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

        IO.println();
        IO.println();

        paramsBuilder.addMessage(accumulator.chatCompletion()
                .choices()
                .getFirst()
                .message());

        // Written after every answer, so an interrupted session is still saved.
        var updated = paramsBuilder.build().messages();
        Files.writeString(memoryFile, mapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(updated));
        IO.println("💾 memory saved to " + memoryFile + " (" + updated.size() + " messages)");
        IO.println();
    }

    IO.println("===== 🧠 FINAL MEMORY (kept in " + memoryFile + ") 🧠 =====");
    paramsBuilder.build().messages().forEach(IO::println);
    IO.println();
    IO.println("🗑️  Delete " + memoryFile + " to start a fresh conversation.");
}
