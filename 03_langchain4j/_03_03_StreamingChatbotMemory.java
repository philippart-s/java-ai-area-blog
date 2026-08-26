///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 26+
//DEPS dev.langchain4j:langchain4j:1.18.0
//DEPS dev.langchain4j:langchain4j-open-ai:1.18.0
//DEPS  org.slf4j:slf4j-simple:2.0.17
//
// Streaming chatbot with memory example calling OVHcloud AI Endpoints
// (gpt-oss-120b) through LangChain4j AI Services
// (https://github.com/langchain4j/langchain4j).
// Java 26 + JBang port of 02_sdk_java/_02_03_StreamingChatbotMemory.java.
//
// Same idea as _03_02 but with a conversation memory. The chat completions API
// is still stateless, so the conversation must still be resent in full on every
// request: the difference is that we no longer write that code. We give the AI
// Service a ChatMemory and LangChain4j does the rest, on every call:
//   - it appends our prompt to the memory,
//   - it sends the whole memory to the model,
//   - it appends the complete answer to the memory once the stream is over.
// So there is no "add the answer back to the memory" step in this example, and
// no need to reassemble the streamed answer ourselves (compare with the
// ChatCompletionAccumulator of _02_03, or the StringBuilder of _01_03).
//
// MessageWindowChatMemory also fixes something the previous examples ignored:
// an unbounded memory grows forever, and so does the number of tokens sent (and
// billed) at every call. It keeps a sliding window of the N last messages (the
// system message is always kept) and evicts the oldest ones.
//
// The program loops so you can chat with the model. Type "exit" (or press
// Ctrl+D) to quit.
//
// Docs: https://www.ovhcloud.com/en/public-cloud/ai-endpoints/catalog/gpt-oss-120b/
// Docs: https://docs.langchain4j.dev/tutorials/chat-memory

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
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
    // OVHcloud AI Endpoints configuration.
    // baseUrl points at the OpenAI-compatible endpoint (note the /v1 suffix);
    // the token is read from the OVH_AI_ENDPOINTS_ACCESS_TOKEN environment variable.
    final String baseUrl = "https://oai.endpoints.kepler.ai.cloud.ovh.net/v1";
    final String model = "gpt-oss-120b";
    final String token = System.getenv("OVH_AI_ENDPOINTS_ACCESS_TOKEN");

    // Build the underlying LangChain4j streaming model, pointed at OVHcloud AI Endpoints.
    StreamingChatModel chatModel = OpenAiStreamingChatModel.builder()
            .baseUrl(baseUrl)
            .apiKey(token)
            .modelName(model)
            .logRequests(false)
            //.logResponses(true)
            .build();

    // The conversation memory: a sliding window of the 10 last messages.
    // We keep a reference on it only to print it; the AI Service is the one
    // that reads it and writes to it.
    ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(10);

    // Create the AI Service backed by the streaming model, with memory.
    // AiServices.builder() is used instead of AiServices.create() because we
    // have something to configure: the chat memory.
    Assistant assistant = AiServices.builder(Assistant.class)
            .streamingChatModel(chatModel)
            .chatMemory(chatMemory)
            .build();

    IO.println("===== 🧠 CHATBOT WITH MEMORY (type \"exit\" to quit) 🧠 =====");
    IO.println();

    while (true) {
        // Ask the user for a prompt.
        var userPrompt = IO.readln("⌨️  Your prompt: ");
        IO.println();

        // Leave the loop on "exit", or on end of input (Ctrl+D).
        if (userPrompt == null || userPrompt.equals("exit")) break;
        if (userPrompt.isBlank()) continue;

        // 1) Print the memory as it is BEFORE the call: this is the context
        // that LangChain4j is about to resend to the model, in front of our
        // prompt. It is empty on the first turn (nothing was said yet), and
        // grows by two messages (ours + the model's) at every turn.
        IO.println("===== 🧠 MEMORY (resent to the model with the prompt) 🧠 =====");
        chatMemory.messages().forEach(IO::println);
        IO.println();

        // 2) Call the endpoint in streaming mode and print the answer token by
        // token. As in _03_02, streaming is asynchronous: the CompletableFuture
        // is completed by onCompleteResponse (or failed by onError) and join()
        // blocks until the answer is complete.
        // Nothing else to do: onCompleteResponse is also where LangChain4j adds
        // the answer to the memory, so the next turn already knows about it.
        IO.println("===== 🤖 ANSWER (streaming) 🤖 =====");
        var futureResponse = new CompletableFuture<ChatResponse>();
        assistant.chat(userPrompt)
                .onPartialResponse(IO::print)
                .onCompleteResponse(futureResponse::complete)
                .onError(futureResponse::completeExceptionally)
                .start();
        futureResponse.join();

        // Newlines once the stream is complete.
        IO.println();
        IO.println();
    }

    // Print the final memory: the whole conversation (up to the window size),
    // this time including the system message added by LangChain4j itself.
    IO.println("===== 🧠 FINAL MEMORY (the whole conversation) 🧠 =====");
    chatMemory.messages().forEach(IO::println);
}
