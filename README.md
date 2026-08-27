# Java AI Area — chatbot, step by step

Companion code for a series of blog posts showing how to build the **same chatbot** in
Java, from a raw `curl` call to a full framework — one stack at a time, one feature at a
time.

Every example is a **single self-contained file**, runnable with
[JBang](https://www.jbang.dev/). No `pom.xml`, no build, nothing to set up beyond a token:
you can copy any file out of this repo and run it as is.

The models come from [OVHcloud AI Endpoints](https://www.ovhcloud.com/en/public-cloud/ai-endpoints/),
which exposes an **OpenAI-compatible API** — so every OpenAI client library here works
unchanged, just pointed at a different base URL.

## The two axes

**Less and less plumbing, left to right:**

| Directory        | What it uses                                          | What disappears                             |
| ---------------- | ----------------------------------------------------- | ------------------------------------------- |
| `00_bash`        | `curl` + `jq`                                         | the baseline: everything is manual          |
| `01_pure_java`   | JDK `HttpClient` + Jackson, no AI library             | the shell, but you still build the JSON     |
| `02_sdk_java`    | official [OpenAI Java SDK](https://github.com/openai/openai-java) | the JSON: typed requests and responses |
| `03_langchain4j` | [LangChain4j](https://docs.langchain4j.dev/) AI Services | the request itself: you declare an interface |
| `04_quarkus`     | Quarkus + [quarkus-langchain4j](https://docs.quarkiverse.io/quarkus-langchain4j/dev/) | the wiring: annotations + configuration |
| `05_spring`      | Spring Boot + [Spring AI](https://docs.spring.io/spring-ai/reference/) | the same, the Spring way (advisors) |

**One new idea at a time, top to bottom:**

| Step                    | `00_bash`                         | `01_pure_java`                  | `02_sdk_java`                   | `03_langchain4j`                            | `04_quarkus`                    | `05_spring`                     |
| ----------------------- | --------------------------------- | ------------------------------- | ------------------------------- | ------------------------------------------- | ------------------------------- | ------------------------------- |
| 1. Simple call          | `00.01_SimpleChatbot.sh`          | `_01_01_SimpleChatbot`          | `_02_01_SimpleChatbot`          | `_03_01_SimpleChatbot`                      | `_04_01_SimpleChatbot`          | `_05_01_SimpleChatbot`          |
| 2. Streaming            | `00.02_StreamingChatbot.sh`       | `_01_02_StreamingChatbot`       | `_02_02_StreamingChatbot`       | `_03_02_StreamingChatbot`                   | `_04_02_StreamingChatbot`       | `_05_02_StreamingChatbot`       |
| 3. Conversation memory  | `00.03_StreamingChatbotMemory.sh` | `_01_03_StreamingChatbotMemory` | `_02_03_StreamingChatbotMemory` | `_03_03_StreamingChatbotMemory`             | `_04_03_StreamingChatbotMemory` | `_05_03_StreamingChatbotMemory` |
| 4. Multi-session memory | —                                 | —                               | —                               | `_03_04_StreamingChatbotMultiSessionMemory` | shown in `_04_03`               | shown in `_05_03`               |

Reading a line across gives you the same program written six ways. Reading a column down
gives you one stack learning to stream, then to remember.

## Prerequisites

- **JDK 26+** and **[JBang](https://www.jbang.dev/download/)** — or just open the repo in
  its dev container (`.devcontainer/`, image `wilda/java-devcontainer:26.1.1`), which has
  both.
- **`curl` and `jq`** for the `00_bash` examples.
- An **OVHcloud AI Endpoints** access token —
  [get one here](https://www.ovhcloud.com/en/public-cloud/ai-endpoints/).

## Setup

Create a `.env` file at the repository root containing your token:

```bash
echo 'OVH_AI_ENDPOINTS_ACCESS_TOKEN=<your-token>' > .env
```

`.env` is git-ignored. Every example reads the token from the
`OVH_AI_ENDPOINTS_ACCESS_TOKEN` environment variable — nothing is ever hard-coded.

## Running

**Bash examples** run directly:

```bash
00_bash/00.01_SimpleChatbot.sh
```

**Java examples** go through the `run.sh` of their directory, which loads `.env` and calls
JBang:

```bash
01_pure_java/run.sh                                    # runs the first example by default
01_pure_java/run.sh _01_03_StreamingChatbotMemory.java # or pick one
03_langchain4j/run.sh _03_04_StreamingChatbotMultiSessionMemory.java
```

> The first run of `04_quarkus` or `05_spring` downloads the whole framework and takes a
> while. Later runs are fast.

Type `exit` (or Ctrl+D) to leave the examples that loop.

## What is shared by every example

So that the files can be compared side by side:

- endpoint `https://oai.endpoints.kepler.ai.cloud.ovh.net/v1`, model `gpt-oss-120b`;
- system prompt `provide a concise answer`, memory window of 10 messages;
- the same emoji-delimited output sections (`⬆️ JSON REQUEST`, `🤖 ANSWER`, `🧠 MEMORY`…);
- comments in **English**, because the blog posts are published in several languages.

## Contributing

Contributions that extend the progression are welcome. A few things to know first:

- **The code is the article.** Clarity beats cleverness: no shared helper module, no
  abstraction layer, some deliberate duplication between files. Each file must be readable
  on its own, in a browser, out of context.
- **One idea per example.** If a change introduces two concepts, it is two examples.
- **Keep the chain intact.** Each file's header comment says which file it is a port of, or
  which one it extends. New files must do the same.
- **Keep the invariants.** Same endpoint, same model, same prompts, same output banners.
- **Pin dependency versions** per file, and bump a whole directory in one commit — a
  published article must keep working.
- Commits follow the conventional format with an emoji: `feat: ✨ …`, `fix: 🐛 …`,
  `docs: 📝 …`.

[`CLAUDE.md`](CLAUDE.md) holds the detailed conventions — file anatomy, naming, checklist
for adding a step, stack-specific gotchas. It is written for AI assistants, but it is the
most precise contributor guide in the repo.

## Documentation links

| Topic               | Link                                                                        |
| ------------------- | --------------------------------------------------------------------------- |
| AI Endpoints        | https://www.ovhcloud.com/en/public-cloud/ai-endpoints/                      |
| `gpt-oss-120b`      | https://www.ovhcloud.com/en/public-cloud/ai-endpoints/catalog/gpt-oss-120b/ |
| JBang               | https://www.jbang.dev/documentation/guide/latest/                           |
| OpenAI Java SDK     | https://github.com/openai/openai-java                                       |
| LangChain4j         | https://docs.langchain4j.dev/                                               |
| quarkus-langchain4j | https://docs.quarkiverse.io/quarkus-langchain4j/dev/                        |
| Quarkus             | https://quarkus.io/guides/                                                  |
| Spring AI           | https://docs.spring.io/spring-ai/reference/                                 |

## License

See [LICENSE](LICENSE).
