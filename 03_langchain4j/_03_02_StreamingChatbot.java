///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 26+
//DEPS dev.langchain4j:langchain4j:1.18.0
//DEPS dev.langchain4j:langchain4j-open-ai:1.18.0
//DEPS org.slf4j:slf4j-simple:2.0.17
//
// Streaming chatbot example calling OVHcloud AI Endpoints (gpt-oss-120b)
// through LangChain4j AI Services (https://github.com/langchain4j/langchain4j).
//
// The answer is printed token by token.
//
// Docs: https://www.ovhcloud.com/en/public-cloud/ai-endpoints/catalog/gpt-oss-120b/
// Docs: https://docs.langchain4j.dev/tutorials/ai-services#streaming

import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;

import java.util.concurrent.CompletableFuture;

interface Assistant {
    @SystemMessage("provide a concise answer")
    TokenStream chat(String userMessage);
}

void main() {
    // baseUrl must include the /v1 suffix. The token is read from
    // OVH_AI_ENDPOINTS_ACCESS_TOKEN, exported by run.sh from .env.
    final String baseUrl = "https://oai.endpoints.kepler.ai.cloud.ovh.net/v1";
    final String model = "gpt-oss-120b";
    final String token = System.getenv("OVH_AI_ENDPOINTS_ACCESS_TOKEN");

    var userPrompt = IO.readln("⌨️  Your prompt: ");
    IO.println();

    StreamingChatModel chatModel = OpenAiStreamingChatModel.builder()
            .baseUrl(baseUrl)
            .apiKey(token)
            .modelName(model)
            .logRequests(true)
            //.logResponses(true)
            .build();

    Assistant assistant = AiServices.create(Assistant.class, chatModel);

    // The callbacks run on another thread, so the future bridges the answer
    // back to main(): completed by onCompleteResponse, failed by onError.
    var futureResponse = new CompletableFuture<ChatResponse>();

    IO.println("===== 🤖 ANSWER (streaming) 🤖 =====");
    assistant.chat(userPrompt)
            .onPartialResponse(IO::print)
            .onCompleteResponse(futureResponse::complete)
            .onError(futureResponse::completeExceptionally)
            .start();

    futureResponse.join();

    IO.println();
}
