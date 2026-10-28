///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 26+
//DEPS com.fasterxml.jackson.core:jackson-databind:2.22.4
//
// Simple RAG example calling OVHcloud AI Endpoints (gpt-oss-120b),
// with the JDK HttpClient and Jackson.
//
// Every markdown file of ../documents is turned into a vector, the question is
// turned into a vector too, and the closest document is pasted into the prompt.
//
// Docs: https://www.ovhcloud.com/en/public-cloud/ai-endpoints/catalog/gpt-oss-120b/

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

record Document(String name, String text, double[] vector) {}

final String CHAT_ENDPOINT = "https://oai.endpoints.kepler.ai.cloud.ovh.net/v1/chat/completions";
final String EMBEDDINGS_ENDPOINT = "https://oai.endpoints.kepler.ai.cloud.ovh.net/v1/embeddings";
final String MODEL = "gpt-oss-120b";
final String EMBEDDING_MODEL = "bge-m3";

// The token is read from OVH_AI_ENDPOINTS_ACCESS_TOKEN, exported by run.sh from .env.
final String TOKEN = System.getenv("OVH_AI_ENDPOINTS_ACCESS_TOKEN");

final ObjectMapper MAPPER = new ObjectMapper();
final HttpClient CLIENT = HttpClient.newHttpClient();

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

            IO.println("  " + file.getFileName() + " -> " + vector.length + " floats, starting with "
                    + Arrays.toString(Arrays.copyOf(vector, 4)));
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
double[] embed(String text) throws Exception {
    var body = MAPPER.createObjectNode();
    body.put("model", EMBEDDING_MODEL);
    body.put("input", text);

    JsonNode json = post(EMBEDDINGS_ENDPOINT, body.toString());
    JsonNode values = json.at("/data/0/embedding");

    var vector = new double[values.size()];
    for (int i = 0; i < vector.length; i++) {
        vector[i] = values.get(i).asDouble();
    }
    return vector;
}

double cosine(double[] a, double[] b) {
    double dot = 0, normA = 0, normB = 0;
    for (int i = 0; i < a.length; i++) {
        dot += a[i] * b[i];
        normA += a[i] * a[i];
        normB += b[i] * b[i];
    }
    return dot / (Math.sqrt(normA) * Math.sqrt(normB));
}

String chat(String prompt) throws Exception {
    var body = MAPPER.createObjectNode();
    body.put("model", MODEL);
    var messages = body.putArray("messages");
    messages.addObject()
            .put("role", "system")
            .put("content", "provide a concise answer");
    messages.addObject()
            .put("role", "user")
            .put("content", prompt);

    return post(CHAT_ENDPOINT, body.toString())
            .at("/choices/0/message/content")
            .asText();
}

JsonNode post(String endpoint, String body) throws Exception {
    var request = HttpRequest.newBuilder(URI.create(endpoint))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + TOKEN)
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
    return MAPPER.readTree(CLIENT.send(request, HttpResponse.BodyHandlers.ofString()).body());
}
