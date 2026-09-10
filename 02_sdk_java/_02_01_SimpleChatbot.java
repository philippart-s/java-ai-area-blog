///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 26+
//DEPS com.openai:openai-java:4.52.0
//
// Simple chatbot example calling OVHcloud AI Endpoints (gpt-oss-120b)
// through the official OpenAI Java SDK (https://github.com/openai/openai-java).
//
// AI Endpoints is OpenAI-compatible: point the SDK's baseUrl at it and pass the
// OVH token as the apiKey. The SDK does the HTTP and the JSON.
//
// Docs: https://www.ovhcloud.com/en/public-cloud/ai-endpoints/catalog/gpt-oss-120b/

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;

void main() {
    // baseUrl must include the /v1 suffix. The token is read from
    // OVH_AI_ENDPOINTS_ACCESS_TOKEN, exported by run.sh from .env.
    final String baseUrl = "https://oai.endpoints.kepler.ai.cloud.ovh.net/v1";
    final String model = "gpt-oss-120b";
    final String token = System.getenv("OVH_AI_ENDPOINTS_ACCESS_TOKEN");

    var userPrompt = IO.readln("⌨️ Your prompt: ");
    IO.println();

    OpenAIClient client = OpenAIOkHttpClient.builder()
            .baseUrl(baseUrl)
            .apiKey(token)
            .build();

    var params = ChatCompletionCreateParams.builder()
            .model(model)
            // .addSystemMessage("provide a concise answer")
            .addUserMessage(userPrompt)
            .build();

    IO.println("===== ⬆️ REQUEST (SDK params) ⬆️ =====");
    IO.println(params.toString());
    IO.println();

    ChatCompletion response = client.chat().completions().create(params);

    IO.println("===== ⬇️ RESPONSE (SDK object) ⬇️ =====");
    IO.println(response.toString());

    // choices() is a list because the API can return several completions.
    IO.println();
    IO.println("===== 🤖 ANSWER 🤖 =====");
    response.choices().stream()
            .flatMap(choice -> choice.message().content().stream())
            .forEach(IO::println);
}
