///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 26+
// jboss-threads needs java.lang opened on Java 24+ (thread-local reset capability).
//JAVA_OPTIONS --add-opens=java.base/java.lang=ALL-UNNAMED
//DEPS io.quarkus.platform:quarkus-bom:3.33.2@pom
//DEPS io.quarkiverse.langchain4j:quarkus-langchain4j-openai:1.12.0
//
// Streaming chatbot with memory example calling OVHcloud AI Endpoints
// (gpt-oss-120b) through Quarkus + quarkus-langchain4j
// (https://github.com/quarkiverse/quarkus-langchain4j).
// Quarkus command-mode port of 03_langchain4j/_03_03_StreamingChatbotMemory.java.
//
// Same idea as _04_02 but with a conversation memory. Here the punch line is
// that there is nothing to add: quarkus-langchain4j enables memory by DEFAULT on
// every @RegisterAiService, with a MessageWindowChatMemory of 10 messages backed
// by an in-memory store. Compared to _03_03, even the ChatMemory declaration and
// the AiServices.builder() wiring are gone: the extension does the wiring, and
// the window size becomes a configuration property (see application.properties).
//
// What we DO add is @MemoryId: it names the conversation, so one service can
// serve several users/sessions without mixing their contexts. It is also what
// lets us look the conversation up in the ChatMemoryStore to print it.
//
// The program loops so you can chat with the model. Type "exit" (or press
// Ctrl+D) to quit.
//
// Docs: https://www.ovhcloud.com/en/public-cloud/ai-endpoints/catalog/gpt-oss-120b/
// Docs: https://docs.quarkiverse.io/quarkus-langchain4j/dev/messages-and-memory.html

// 1️⃣ Include application.properties as a classpath resource so Quarkus reads it.
//FILES application.properties

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import io.smallrye.mutiny.Multi;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;

// 2️⃣ The AI Service. Memory is already on: the only new thing is @MemoryId,
// which identifies the conversation this call belongs to. As soon as a
// parameter is annotated, the prompt itself must be annotated too, hence the
// @UserMessage on the second parameter.
@RegisterAiService
interface Assistant {
    @SystemMessage("provide a concise answer")
    Multi<String> chat(@MemoryId String sessionId, @UserMessage String userMessage);
}

// 3️⃣ Command-mode entry point: @QuarkusMain + QuarkusApplication run in a shell.
@QuarkusMain
public class _04_03_StreamingChatbotMemory implements QuarkusApplication {

    // A single conversation here, so a constant id is enough. A web chatbot
    // would use one id per user or per session instead.
    private static final String SESSION_ID = "cli-session";

    // 4️⃣ The generated AI service is a CDI bean, injected here.
    @Inject
    Assistant assistant;

    // The store where the extension keeps the conversations, injected only so we
    // can print ours. Nothing forces us to touch it: the AI service reads it and
    // writes to it on its own. Being in-memory, it is lost when the app stops.
    @Inject
    ChatMemoryStore memoryStore;

    // @ActivateRequestContext makes the CDI request scope available for the call.
    @Override
    @ActivateRequestContext
    public int run(String... args) {
        IO.println("===== 🧠 CHATBOT WITH MEMORY (type \"exit\" to quit) 🧠 =====");
        IO.println();

        while (true) {
            // Ask the user for a prompt.
            var userPrompt = IO.readln("⌨️  Your prompt: ");
            IO.println();

            // Leave the loop on "exit", or on end of input (Ctrl+D).
            if (userPrompt == null || userPrompt.equals("exit")) break;
            if (userPrompt.isBlank()) continue;

            // 5️⃣ Print the memory as it is BEFORE the call: this is the context
            // the extension is about to resend to the model, in front of our
            // prompt. It is empty on the first turn (nothing was said yet), and
            // grows by two messages (ours + the model's) at every turn.
            IO.println("===== 🧠 MEMORY (resent to the model with the prompt) 🧠 =====");
            memoryStore.getMessages(SESSION_ID).forEach(IO::println);
            IO.println();

            // 6️⃣ Call the endpoint in streaming mode and print the answer token
            // by token. asStream() blocks this thread until the reactive stream
            // completes; on completion the extension stores the full answer in
            // the memory, so the next turn already knows about it.
            IO.println("===== 🤖 ANSWER (streaming) 🤖 =====");
            assistant.chat(SESSION_ID, userPrompt)
                    .subscribe().asStream()
                    .forEach(IO::print);

            // Newlines once the stream is complete.
            IO.println();
            IO.println();
        }

        // Print the final memory: the whole conversation (up to the window size),
        // this time including the system message added by the extension itself.
        IO.println("===== 🧠 FINAL MEMORY (the whole conversation) 🧠 =====");
        memoryStore.getMessages(SESSION_ID).forEach(IO::println);

        // A real application would also delete the conversation when it ends,
        // otherwise the store keeps one entry per memory id forever:
        //memoryStore.deleteMessages(SESSION_ID);

        return 0;
    }
}
