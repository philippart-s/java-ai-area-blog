///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 26+
//DEPS com.openai:openai-java:4.42.0
//
// Streaming chatbot example calling OVHcloud AI Endpoints (gpt-oss-120b)
// through the official OpenAI Java SDK (https://github.com/openai/openai-java).
// Java 26 + JBang port of 01_pure_java/_01_02_StreamingChatbot.java.
//
// Same idea as _02_01 but with streaming: createStreaming() returns a
// StreamResponse of chunks, and we print each chunk's delta content as it
// arrives. The SDK takes care of the SSE parsing for us.
//
// Docs: https://www.ovhcloud.com/en/public-cloud/ai-endpoints/catalog/gpt-oss-120b/

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.core.LogLevel;
import com.openai.core.http.StreamResponse;
import com.openai.models.chat.completions.ChatCompletionChunk;
import com.openai.models.chat.completions.ChatCompletionCreateParams;

void main() {
    // OVHcloud AI Endpoints configuration.
    // baseUrl points at the OpenAI-compatible endpoint (note the /v1 suffix);
    // the token is read from the OVH_AI_ENDPOINTS_ACCESS_TOKEN environment variable.
    final String baseUrl = "https://oai.endpoints.kepler.ai.cloud.ovh.net/v1";
    final String model = "gpt-oss-120b";
    final String token = System.getenv("OVH_AI_ENDPOINTS_ACCESS_TOKEN");

    // Ask the user for a prompt.
    var userPrompt = IO.readln("⌨️ Your prompt: ");
    IO.println();

    // Build the SDK client, pointed at OVHcloud AI Endpoints.
    OpenAIClient client = OpenAIOkHttpClient.builder()
            .baseUrl(baseUrl)
            .apiKey(token)
            //.logLevel(LogLevel.DEBUG)
            .build();

    // Build the request with the SDK's typed builder (no manual JSON).
    // No need to set "stream": true — createStreaming() does that for us.
    var params = ChatCompletionCreateParams.builder()
            .model(model)
            .addSystemMessage("provide a concise answer")
            .addUserMessage(userPrompt)
            .build();

    // Call the endpoint in streaming mode and print the answer token by token.
    // The StreamResponse is AutoCloseable, so we close it with try-with-resources.
    IO.println("===== 🤖 ANSWER (streaming) 🤖 =====");
    try (StreamResponse<ChatCompletionChunk> stream =
                 client.chat().completions().createStreaming(params)) {
        stream.stream()
                .flatMap(chunk -> chunk.choices().stream())
                .flatMap(choice -> choice.delta().content().stream())
                .forEach(IO::print);
    }

    // Final newline once the stream is complete.
    IO.println();
}