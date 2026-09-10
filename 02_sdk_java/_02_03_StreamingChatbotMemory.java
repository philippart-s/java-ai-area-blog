///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 26+
//DEPS com.openai:openai-java:4.52.0
//
// Streaming chatbot with memory example calling OVHcloud AI Endpoints
// (gpt-oss-120b) through the official OpenAI Java SDK
// (https://github.com/openai/openai-java).
//
// The chat completions API is stateless, so the conversation must be resent in
// full on every call. There is no messages array to maintain by hand: the
// ChatCompletionCreateParams.Builder is kept alive and messages are added to
// it, so the builder IS the memory.
//
// Streaming makes one thing harder: the answer arrives in pieces but the memory
// needs it whole, so ChatCompletionAccumulator rebuilds it from the chunks.
//
// Type "exit" (or press Ctrl+D) to quit.
//
// Docs: https://www.ovhcloud.com/en/public-cloud/ai-endpoints/catalog/gpt-oss-120b/

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.core.http.StreamResponse;
import com.openai.helpers.ChatCompletionAccumulator;
import com.openai.models.chat.completions.ChatCompletionChunk;
import com.openai.models.chat.completions.ChatCompletionCreateParams;

void main() {
    // baseUrl must include the /v1 suffix. The token is read from
    // OVH_AI_ENDPOINTS_ACCESS_TOKEN, exported by run.sh from .env.
    final String baseUrl = "https://oai.endpoints.kepler.ai.cloud.ovh.net/v1";
    final String model = "gpt-oss-120b";
    final String token = System.getenv("OVH_AI_ENDPOINTS_ACCESS_TOKEN");

    OpenAIClient client = OpenAIOkHttpClient.builder()
            .baseUrl(baseUrl)
            .apiKey(token)
            //.logLevel(LogLevel.DEBUG)
            .build();

    // The memory. build() is called at each turn and returns the whole conversation.
    var paramsBuilder = ChatCompletionCreateParams.builder()
            .model(model)
            .addSystemMessage("provide a concise answer");

    IO.println("===== 🧠 CHATBOT WITH MEMORY (type \"exit\" to quit) 🧠 =====");
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

        // The stream is consumed and only gave text fragments; what goes into
        // the memory is the message reassembled by the accumulator.
        paramsBuilder.addMessage(accumulator.chatCompletion()
                .choices()
                .getFirst()
                .message());
    }

    IO.println("===== 🧠 FINAL MEMORY (the whole conversation) 🧠 =====");
    paramsBuilder.build().messages().forEach(IO::println);
}
