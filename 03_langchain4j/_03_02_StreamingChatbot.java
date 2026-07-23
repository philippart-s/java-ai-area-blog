///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 26+
//DEPS dev.langchain4j:langchain4j:1.18.0
//DEPS dev.langchain4j:langchain4j-open-ai:1.18.0
//DEPS  org.slf4j:slf4j-simple:2.0.17
//
// Streaming chatbot example calling OVHcloud AI Endpoints (gpt-oss-120b)
// through LangChain4j AI Services (https://github.com/langchain4j/langchain4j).
// Java 26 + JBang port of 02_sdk_java/_02_02_StreamingChatbot.java.
//
// Same idea as _03_01 but with streaming: the AI Service method returns a
// TokenStream instead of a String. We register callbacks on it
// (onPartialResponse / onCompleteResponse / onError) and start() the stream.
// Because streaming is asynchronous, we follow the LangChain4j docs and use a
// CompletableFuture<ChatResponse>: onCompleteResponse completes it, onError
// fails it, and join() blocks main() until the response is done. The service is
// backed by an OpenAiStreamingChatModel pointed at OVHcloud AI Endpoints.
//
// Docs: https://www.ovhcloud.com/en/public-cloud/ai-endpoints/catalog/gpt-oss-120b/

import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;

import java.util.concurrent.CompletableFuture;

// The AI Service: a plain interface returning a TokenStream for streaming.
interface Assistant {
    @SystemMessage("provide a concise answer")
    TokenStream chat(String userMessage);
}

void main() {
    // OVHcloud AI Endpoints configuration.
    // baseUrl points at the OpenAI-compatible endpoint (note the /v1 suffix);
    // the token is read from the OVH_AI_ENDPOINTS_ACCESS_TOKEN environment variable.
    final String baseUrl = "https://oai.endpoints.kepler.ai.cloud.ovh.net/v1";
    final String model = "gpt-oss-120b";
    final String token = System.getenv("OVH_AI_ENDPOINTS_ACCESS_TOKEN");

    // Ask the user for a prompt.
    var userPrompt = IO.readln("⌨️  Your prompt: ");
    IO.println();

    // Build the underlying LangChain4j streaming model, pointed at OVHcloud AI Endpoints.
    StreamingChatModel chatModel = OpenAiStreamingChatModel.builder()
            .baseUrl(baseUrl)
            .apiKey(token)
            .modelName(model)
            .logRequests(true)
            //.logResponses(true)
            .build();

    // Create the AI Service backed by the streaming model.
    Assistant assistant = AiServices.create(Assistant.class, chatModel);

    // Streaming is asynchronous: the callbacks run on another thread. Following
    // the LangChain4j docs, we bridge that back to main() with a CompletableFuture
    // that is completed on onCompleteResponse (or failed on onError).
    var futureResponse = new CompletableFuture<ChatResponse>();

    // Call the endpoint in streaming mode and print the answer token by token.
    IO.println("===== 🤖 ANSWER (streaming) 🤖 =====");
    assistant.chat(userPrompt)
            .onPartialResponse(IO::print)
            .onCompleteResponse(futureResponse::complete)
            .onError(futureResponse::completeExceptionally)
            .start();

    // Block until the stream completes (or throw if it failed).
    futureResponse.join();

    // Final newline once the stream is complete.
    IO.println();
}
