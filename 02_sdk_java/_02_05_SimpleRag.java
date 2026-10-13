///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 26+
//DEPS com.openai:openai-java:4.52.0
//
// Simple RAG example calling OVHcloud AI Endpoints (gpt-oss-120b)
// through the official OpenAI Java SDK (https://github.com/openai/openai-java).
//
// Every markdown file of ../documents is turned into a vector, the question is
// turned into a vector too, and the closest document is pasted into the prompt.
//
// Docs: https://www.ovhcloud.com/en/public-cloud/ai-endpoints/catalog/gpt-oss-120b/
// Docs: https://platform.openai.com/docs/api-reference/embeddings

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.embeddings.EmbeddingCreateParams;

record Document(String name, String text, List<Float> vector) {}

// baseUrl must include the /v1 suffix. The token is read from
// OVH_AI_ENDPOINTS_ACCESS_TOKEN, exported by run.sh from .env.
final OpenAIClient CLIENT = OpenAIOkHttpClient.builder()
        .baseUrl("https://oai.endpoints.kepler.ai.cloud.ovh.net/v1")
        .apiKey(System.getenv("OVH_AI_ENDPOINTS_ACCESS_TOKEN"))
        .build();

final String MODEL = "gpt-oss-120b";
final String EMBEDDING_MODEL = "bge-m3";

void main() throws Exception {
    // Relative to the current directory: run.sh cd's into the example directory.
    var documentsDir = Path.of("..", "documents");
    var question = "Who is speaking about quantum compilers, and in which room?";

    IO.println("===== 📚 DOCUMENTS (one vector each) 📚 =====");
    var documents = new ArrayList<Document>();
    try (var files = Files.list(documentsDir)) {
        for (var file : files.filter(p -> p.toString().endsWith(".md")).sorted().toList()) {
            var text = Files.readString(file);
            var vector = embed(text);
            documents.add(new Document(file.getFileName().toString(), text, vector));

            IO.println("  " + file.getFileName() + " -> " + vector.size() + " floats, starting with "
                    + vector.subList(0, 4));
        }
    }
    IO.println();

    IO.println("===== ❓ QUESTION ❓ =====");
    IO.println(question);
    IO.println();
    var questionVector = embed(question);

    IO.println("===== 📐 SIMILARITY (closest first) 📐 =====");
    var ranked = documents.stream()
            .sorted(Comparator.comparingDouble((Document d) -> cosine(d.vector(), questionVector)).reversed())
            .toList();
    ranked.forEach(d -> IO.println(
            String.format(Locale.ROOT, "  %.3f  %s", cosine(d.vector(), questionVector), d.name())));
    IO.println();

    // Only the best document is handed to the model.
    var best = ranked.getFirst();
    IO.println("===== 📄 RETRIEVED DOCUMENT 📄 =====");
    IO.println(best.name());
    IO.println();

    var prompt = """
            Answer the question using only the context below.

            # Context
            %s

            # Question
            %s""".formatted(best.text(), question);

    IO.println("===== 🤖 ANSWER 🤖 =====");
    IO.println(chat(prompt));
}

// Turns a text into a vector.
List<Float> embed(String text) {
    var params = EmbeddingCreateParams.builder()
            .model(EMBEDDING_MODEL)
            .input(text)
            .build();

    return CLIENT.embeddings()
            .create(params)
            .data()
            .getFirst()
            .embedding();
}

double cosine(List<Float> a, List<Float> b) {
    double dot = 0, normA = 0, normB = 0;
    for (int i = 0; i < a.size(); i++) {
        dot += a.get(i) * b.get(i);
        normA += a.get(i) * a.get(i);
        normB += b.get(i) * b.get(i);
    }
    return dot / (Math.sqrt(normA) * Math.sqrt(normB));
}

String chat(String prompt) {
    var params = ChatCompletionCreateParams.builder()
            .model(MODEL)
            .addSystemMessage("provide a concise answer")
            .addUserMessage(prompt)
            .build();

    return CLIENT.chat().completions().create(params)
            .choices()
            .getFirst()
            .message()
            .content()
            .orElse("");
}
