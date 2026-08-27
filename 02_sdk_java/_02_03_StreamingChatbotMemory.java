///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 26+
//DEPS com.openai:openai-java:4.52.0
//
// Streaming chatbot with memory example calling OVHcloud AI Endpoints
// (gpt-oss-120b) through the official OpenAI Java SDK
// (https://github.com/openai/openai-java).
// Java 26 + JBang port of 01_pure_java/_01_03_StreamingChatbotMemory.java.
//
// Same idea as _02_02 but with a conversation memory: the chat completions API
// is stateless, so the model remembers nothing between two calls. The "memory"
// is entirely on the client side and must be sent back, in full, on every
// request. With the SDK there is no messages array to maintain by hand: we keep
// the ChatCompletionCreateParams.Builder alive and keep adding messages to it,
// so the builder IS the memory.
//
// Streaming makes one thing slightly harder: the answer arrives in pieces, but
// the memory needs it whole. ChatCompletionAccumulator rebuilds the complete
// ChatCompletion from the chunks, so we can add the answer back to the memory.
//
// The program loops so you can chat with the model. Type "exit" (or press
// Ctrl+D) to quit.
//
// Docs: https://www.ovhcloud.com/en/public-cloud/ai-endpoints/catalog/gpt-oss-120b/

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.core.http.StreamResponse;
import com.openai.helpers.ChatCompletionAccumulator;
import com.openai.models.chat.completions.ChatCompletionChunk;
import com.openai.models.chat.completions.ChatCompletionCreateParams;

void main() {
    // OVHcloud AI Endpoints configuration.
    // baseUrl points at the OpenAI-compatible endpoint (note the /v1 suffix);
    // the token is read from the OVH_AI_ENDPOINTS_ACCESS_TOKEN environment variable.
    final String baseUrl = "https://oai.endpoints.kepler.ai.cloud.ovh.net/v1";
    final String model = "gpt-oss-120b";
    final String token = System.getenv("OVH_AI_ENDPOINTS_ACCESS_TOKEN");

    // Build the SDK client, pointed at OVHcloud AI Endpoints.
    OpenAIClient client = OpenAIOkHttpClient.builder()
            .baseUrl(baseUrl)
            .apiKey(token)
            //.logLevel(LogLevel.DEBUG)
            .build();

    // The params builder is the conversation memory: it is seeded with the
    // system message, then every user prompt and every model answer is added to
    // it. build() is called at each turn and returns the WHOLE conversation.
    var paramsBuilder = ChatCompletionCreateParams.builder()
            .model(model)
            .addSystemMessage("provide a concise answer");

    IO.println("===== 🧠 CHATBOT WITH MEMORY (type \"exit\" to quit) 🧠 =====");
    IO.println();

    while (true) {
        // Ask the user for a prompt.
        var userPrompt = IO.readln("⌨️  Your prompt: ");
        IO.println();

        // Leave the loop on "exit", or on end of input (Ctrl+D).
        if (userPrompt == null || userPrompt.equals("exit")) break;
        if (userPrompt.isBlank()) continue;

        // 1) Append the user message to the memory.
        paramsBuilder.addUserMessage(userPrompt);

        // 2) Print the messages of the request (typed SDK objects, the rest of
        // the params is left out to keep the output readable): notice how the
        // list grows at each turn. The WHOLE memory is sent again.
        var params = paramsBuilder.build();
        IO.println("===== ⬆️ REQUEST (memory sent to the model) ⬆️ =====");
        params.messages().forEach(IO::println);
        IO.println();

        // 3) Call the endpoint in streaming mode and print the answer token by
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

        // 4) Append the model answer to the memory, so the next call gets the
        // full conversation: this is what makes the model look like it
        // remembers. This is NOT a second pass over the stream: the stream is
        // already consumed and only gave us text fragments. What we add here is
        // the single message reassembled by the accumulator, taken as is.
        // (choices() is a list because the API can return several completions,
        // but there is only one here since we did not ask for more.)
        paramsBuilder.addMessage(accumulator.chatCompletion()
                .choices()
                .getFirst()
                .message());
    }

    // Print the final memory: the whole conversation, kept client side.
    IO.println("===== 🧠 FINAL MEMORY (the whole conversation) 🧠 =====");
    paramsBuilder.build().messages().forEach(IO::println);
}
