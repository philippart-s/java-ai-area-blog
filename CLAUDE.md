# CLAUDE.md

Guidance for Claude Code (and any AI assistant) working in this repository.

## What this project is

A **companion code repository for a series of blog posts** about building a chatbot in
Java against an OpenAI-compatible LLM endpoint. It is not a product: **the code is the
teaching material**. Readability and the step-by-step progression matter more than
abstraction, reuse or robustness.

Two axes structure the whole repo:

- **Horizontally — the stack**: the same chatbot is rewritten with less and less
  plumbing, from raw `curl` to a full framework.
- **Vertically — the feature**: each stack starts with the simplest call and adds one
  capability at a time (streaming, then memory, ...).

Every example is a **standalone JBang script**. There is no `pom.xml`, no `build.gradle`,
no module. This is deliberate: a reader must be able to copy one file into a blog post
and run it.

## Progression matrix

| Step                   | `00_bash`                       | `01_pure_java`                | `02_sdk_java`                 | `03_langchain4j`                            | `04_quarkus`                  | `05_spring`                   |
| ---------------------- | ------------------------------- | ----------------------------- | ----------------------------- | ------------------------------------------- | ----------------------------- | ----------------------------- |
| 1. Simple call         | `00.01_SimpleChatbot.sh`        | `_01_01_SimpleChatbot`        | `_02_01_SimpleChatbot`        | `_03_01_SimpleChatbot`                      | `_04_01_SimpleChatbot`        | `_05_01_SimpleChatbot`        |
| 2. Streaming           | `00.02_StreamingChatbot.sh`     | `_01_02_StreamingChatbot`     | `_02_02_StreamingChatbot`     | `_03_02_StreamingChatbot`                   | `_04_02_StreamingChatbot`     | `_05_02_StreamingChatbot`     |
| 3. Conversation memory | `00.03_StreamingChatbotMemory.sh` | `_01_03_StreamingChatbotMemory` | `_02_03_StreamingChatbotMemory` | `_03_03_StreamingChatbotMemory`           | `_04_03_StreamingChatbotMemory` | `_05_03_StreamingChatbotMemory` |
| 4. Multi-session memory | —                              | —                             | —                             | `_03_04_StreamingChatbotMultiSessionMemory` | folded into `_04_03` (`@MemoryId`) | folded into `_05_03` (conversation id) |

**Rule of thumb**: an example of column N is a *port* of the same example in column N-1,
and an example of row M is the same example as row M-1 *plus one idea*. The header
comment of each file states both explicitly ("Java 26 + JBang port of ...", "Same idea as
_04_02 but with ..."). Keep that chain intact when you add a file.

## Repository layout

```
.
├── .devcontainer/devcontainer.json   # wilda/java-devcontainer:26.1.1 (JDK 26 + JBang)
├── .env                              # 🤫 git-ignored, holds the OVHcloud token
├── 00_bash/*.sh                      # curl + jq baseline, no launcher
├── 01_pure_java/                     # JDK HttpClient + Jackson, no AI lib
├── 02_sdk_java/                      # official OpenAI Java SDK
├── 03_langchain4j/                   # LangChain4j AI Services
├── 04_quarkus/                       # Quarkus command mode + quarkus-langchain4j
│   └── application.properties        # injected via //FILES
├── 05_spring/                        # Spring Boot command mode + Spring AI
│   └── application.properties        # injected via //FILES
└── 0X_*/run.sh                       # per-directory launcher (sources ../.env, calls jbang)
```

## Invariants — do not diverge from these

These are shared by **every** example, in every language and every stack. They are what
makes the files comparable side by side in an article.

| Invariant            | Value                                                          |
| -------------------- | -------------------------------------------------------------- |
| Provider             | OVHcloud AI Endpoints (OpenAI-compatible)                        |
| Base URL             | `https://oai.endpoints.kepler.ai.cloud.ovh.net/v1`               |
| Model                | `gpt-oss-120b`                                                   |
| Credential           | env var `OVH_AI_ENDPOINTS_ACCESS_TOKEN`, read from `.env`         |
| System prompt        | `provide a concise answer`                                        |
| Memory window        | 10 messages                                                       |
| Multi-session ids    | `cli-session`, or `stephane` / `fanny` for the scripted demo      |
| Comment language     | **English** (the blog posts are multilingual)                     |
| Console I/O          | `IO.println` / `IO.print` / `IO.readln` — **never** `System.out`   |
| Exit condition       | typing `exit`, or Ctrl+D / Ctrl+C                                 |

Never hard-code the token. Never read or print `.env`: if a value is needed, ask the user
to check it.

## Anatomy of a Java example

Every `.java` file follows the same skeleton. Reproduce it exactly.

```java
///usr/bin/env jbang "$0" "$@" ; exit $?     // 1. shebang, no space after ///
//JAVA 26+                                    // 2. Java version
//DEPS group:artifact:version                 // 3. one dependency per line
//
// <One-line summary> calling OVHcloud AI Endpoints (gpt-oss-120b)
// through <the library> (<link to its repo/docs>).
// <"Java 26 + JBang port of X" or "Same idea as Y but with Z">
//
// <Two or three paragraphs explaining the ONE new idea of this file,
//  and explicitly comparing it to the previous step.>
//
// Docs: https://www.ovhcloud.com/en/public-cloud/ai-endpoints/catalog/gpt-oss-120b/
// Docs: <framework doc page for the feature being shown>

//FILES application.properties                // 4. only 04_quarkus / 05_spring

import ...;

void main() {                                 // 5. compact source file (JEP 512)
    // OVHcloud AI Endpoints configuration.
    final String baseUrl = "...";
    final String model = "gpt-oss-120b";
    final String token = System.getenv("OVH_AI_ENDPOINTS_ACCESS_TOKEN");
    ...
}
```

Notes:

- `04_quarkus` and `05_spring` cannot use a compact source file: they need a `public class`
  named exactly like the file (`@QuarkusMain` / `@SpringBootConfiguration` entry point).
- Numbered inline comments (`// 1)`, `// 2)`, ...) walk the reader through the flow in the
  more involved examples. Use them when the file has more than ~4 meaningful stages.
- Emoji banners delimit the output and must stay identical across stacks:
  `===== ⬆️ JSON REQUEST ... ⬆️ =====`, `===== ⬇️ JSON RESPONSE ⬇️ =====`,
  `===== 🤖 ANSWER 🤖 =====`, `===== 🤖 ANSWER (streaming) 🤖 =====`,
  `===== 🧠 MEMORY (resent to the model with the prompt) 🧠 =====`,
  `===== 🧠 FINAL MEMORY (the whole conversation) 🧠 =====`.
- The user prompt is always read with `IO.readln("⌨️  Your prompt: ")`.
- Commented-out lines are pedagogical on purpose (`// .logRequests(true)`,
  `//memoryStore.deleteMessages(...)`, `// @SystemMessage(...)`). Do not "clean them up".

## Running

```bash
# .env at the repo root must contain: OVH_AI_ENDPOINTS_ACCESS_TOKEN=...

00_bash/00.01_SimpleChatbot.sh        # bash examples are run directly (need curl + jq)

01_pure_java/run.sh                              # runs _01_01 by default
01_pure_java/run.sh _01_03_StreamingChatbotMemory.java
```

Each `run.sh` does the same three things: resolve its own directory, `source ../.env` with
`set -a`, then `jbang <script>`. A new stack directory needs its own copy, with the usage
block and the default script updated.

First run of `04_quarkus` / `05_spring` downloads a lot and takes a while; that is expected.

## Adding a new step

Checklist, in order:

1. **Decide the scope**: one new idea, and only one. If it needs two, it is two steps.
2. **Start from bash** (`00_bash`) when the idea is visible at the HTTP level (streaming,
   memory, tools). It is the reference implementation everything else is compared against.
3. **Port upwards**, one directory at a time, `01` → `05`. Each port keeps the same output
   and the same banners; only the code that produces them shrinks.
4. **Numbering**: `_0<stack>_0<step>_<PascalCaseName>.java`, bash uses `00.0<step>_...sh`.
   The name is the same across stacks so the files line up in a table.
5. **Header comment**: state the port/parent relationship and add the `Docs:` links for the
   feature being introduced.
6. **Update the `run.sh` usage block** of every touched directory.
7. **Update the progression matrix** in this file and in `README.md`.
8. **Verify** the example actually runs (`./run.sh <file>`) before proposing a commit.

Dependency versions are pinned per file, on purpose: a blog post must keep working. When
bumping a version, bump it in **all** files of the directory in the same commit.

## Conventions & workflow

- **Java version**: `//JAVA 26+`, and Java 25/26 idioms — records, pattern matching, `var`,
  text blocks, `IO.*`, compact source files. Never `java.util.Date`, never `System.out`.
- **Work one validated step at a time.** Never deliver a whole feature, or a batch of
  example files, in a single pass — even a small one. Present a numbered plan, get it
  agreed, then implement one step, STOP, and wait for validation before the next.
- **A step ends with its commit, and the commit ends the turn.** implement → summarise and
  test → propose the commit message → get it validated → ask permission → commit → then ask
  whether to move on. Never chain into the next step on your own, even when the plan says
  what it is.
- **The `java-dev` skill applies to code changes in this repo** (five-phase methodology:
  gather info → git check → plan → implement step by step with validation → validate).
  It does not apply to pure documentation edits like this file.
- **Branching**: work on a feature branch, never commit straight to `main`. Ask before
  creating the branch.
- **Commits**: conventional format with one emoji right after the type, one idea per commit —
  `feat: ✨ add tool calling example for LangChain4j`, `docs: 📝 ...`, `fix: 🐛 ...`.
  Never commit without being asked.
- **Commit approval is a two-step gate, never implied.** Propose the message and the file
  list as text, wait for it to be validated, THEN ask permission to commit, and only then
  run the command. A general remark about commits (their size, their format) is not an
  authorization; neither is approval of the work itself. Same rule for `git push`.
- **Commit authorship**: commits belong to the repository owner. Author and committer are
  whatever `git config user.name` / `user.email` hold — never add a `Co-Authored-By: Claude`
  trailer, a "Generated with Claude Code" line, or any other AI attribution.
- **Never** delete existing code or restructure an example without agreement: an example
  that looks redundant is usually the point of comparison of an article.

## Reference links

**Provider**

- OVHcloud AI Endpoints — https://www.ovhcloud.com/en/public-cloud/ai-endpoints/
- `gpt-oss-120b` model card — https://www.ovhcloud.com/en/public-cloud/ai-endpoints/catalog/gpt-oss-120b/
- OpenAI Chat Completions API reference — https://platform.openai.com/docs/api-reference/chat

**Tooling**

- JBang — https://www.jbang.dev/documentation/guide/latest/
- JDK 26 — https://openjdk.org/projects/jdk/26/
- Dev container image — https://hub.docker.com/r/wilda/java-devcontainer

**Libraries, per directory**

- `01_pure_java` — `java.net.http.HttpClient` https://docs.oracle.com/en/java/javase/25/docs/api/java.net.http/java/net/http/HttpClient.html · Jackson https://github.com/FasterXML/jackson-databind
- `02_sdk_java` — OpenAI Java SDK https://github.com/openai/openai-java
- `03_langchain4j` — https://docs.langchain4j.dev/ · AI Services https://docs.langchain4j.dev/tutorials/ai-services · Chat Memory https://docs.langchain4j.dev/tutorials/chat-memory · repo https://github.com/langchain4j/langchain4j
- `04_quarkus` — Quarkus https://quarkus.io/guides/ · command mode https://quarkus.io/guides/command-mode-reference · quarkus-langchain4j https://docs.quarkiverse.io/quarkus-langchain4j/dev/ · repo https://github.com/quarkiverse/quarkus-langchain4j
- `05_spring` — Spring AI https://docs.spring.io/spring-ai/reference/ · Chat Client https://docs.spring.io/spring-ai/reference/api/chatclient.html · Chat Memory https://docs.spring.io/spring-ai/reference/api/chat-memory.html · repo https://github.com/spring-projects/spring-ai

## Pinned versions (as of the current tree)

| Directory        | Key dependencies                                                             |
| ---------------- | ---------------------------------------------------------------------------- |
| `01_pure_java`   | `jackson-databind:2.18.2`                                                     |
| `02_sdk_java`    | `com.openai:openai-java:4.52.0`                                               |
| `03_langchain4j` | `dev.langchain4j:langchain4j{,-open-ai}:1.18.0`, `slf4j-simple:2.0.17`        |
| `04_quarkus`     | `quarkus-bom:3.33.2`, `quarkus-langchain4j-openai:1.12.0`                     |
| `05_spring`      | `spring-boot-dependencies:4.1.0`, `spring-ai-bom:2.0.0`, `aspectjweaver:1.9.25.1` |

Stack-specific gotchas already encoded in the files, keep them:

- Quarkus needs `//JAVA_OPTIONS --add-opens=java.base/java.lang=ALL-UNNAMED` on Java 24+
  (jboss-threads).
- Spring AI 2.x wraps the OpenAI SDK, which only appends `/chat/completions`: the base URL
  **must** include `/v1`.
- Spring examples use `@SpringBootConfiguration` + `@EnableAutoConfiguration`, **not**
  `@SpringBootApplication`, because a single file in the default package would component-scan
  the whole classpath.
- Spring needs `jakarta.servlet-api` on the classpath even though the app is non-web.
