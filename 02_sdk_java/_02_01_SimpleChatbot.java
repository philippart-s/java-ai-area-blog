///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 26+
//DEPS com.openai:openai-java:4.42.0
//
// Simple chatbot example calling OVHcloud AI Endpoints (gpt-oss-120b)
// through the official OpenAI Java SDK (https://github.com/openai/openai-java).
// Java 26 + JBang port of 01_pure_java/_01_01_SimpleChatbot.java.
//
// OVHcloud AI Endpoints is OpenAI-compatible, so we simply point the SDK's
// baseUrl at it and pass the OVH token as the apiKey. No manual HTTP, no
// manual JSON: the SDK handles serialization and parsing for us.
//
// Docs: https://www.ovhcloud.com/en/public-cloud/ai-endpoints/catalog/gpt-oss-120b/

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.chat.completions.ChatCompletion;
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
            .build();

    // Build the request with the SDK's typed builder (no manual JSON).
    var params = ChatCompletionCreateParams.builder()
            .model(model)
            // .addSystemMessage("provide a concise answer")
            .addUserMessage(userPrompt)
            .build();

    // Print what we send (typed SDK object).
    IO.println("===== ⬆️ REQUEST (SDK params) ⬆️ =====");
    IO.println(params.toString());
    IO.println();

    // Call the endpoint (blocking, non-streaming).
    ChatCompletion response = client.chat().completions().create(params);

    // Print the full typed response object.
    IO.println("===== ⬇️ RESPONSE (SDK object) ⬇️ =====");
    IO.println(response.toString());

    // Print only the answer.
    IO.println();
    IO.println("===== 🤖 ANSWER 🤖 =====");
    response.choices().stream()
            .flatMap(choice -> choice.message().content().stream())
            .forEach(IO::println);
}