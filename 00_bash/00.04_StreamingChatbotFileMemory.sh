#!/usr/bin/env bash
#
# Streaming chatbot with a PERSISTENT memory example calling OVHcloud AI
# Endpoints (gpt-oss-120b).
# Same idea as 00.03 but the memory outlives the process: it is written to a
# file, so the conversation survives quitting the script.
#
# 00.03 already showed the important part: the chat completions API is
# stateless, so the "memory" is just a messages array kept client side and
# resent in full on every call. That array lived in a shell variable, and died
# with the shell.
#
# The only new idea here is where that array lives. And at this level the answer
# is almost embarrassing: the memory is ALREADY JSON, so persisting it is one
# redirection, and reloading it is one cat. Nothing to serialize, nothing to
# convert. Remember this when the same feature comes back in the framework
# examples: the higher the abstraction, the more work it takes to get the
# conversation back out of it.
#
# Run the script twice: the second run picks the conversation up where you left
# it, and the model still knows your name.
#
# The memory is written after EVERY answer, not once at the end, so quitting
# with Ctrl+C loses nothing.
#
# Docs: https://www.ovhcloud.com/en/public-cloud/ai-endpoints/catalog/gpt-oss-120b/

# Load the access token from the .env file located at the project root.
# The .env file is expected to contain: OVH_AI_ENDPOINTS_ACCESS_TOKEN=...
set -a
source "$(dirname "$0")/../.env"
set +a

# OVHcloud AI Endpoints configuration.
ENDPOINT="https://oai.endpoints.kepler.ai.cloud.ovh.net/v1/chat/completions"
MODEL="gpt-oss-120b"

# Where the conversation is stored, next to this script. $0 is the path of the
# script itself, so the memory always lands in 00_bash/.memory/ no matter which
# directory the script is called from.
# The conversation is named, like a session id would be: one file per name, so
# running with another SESSION_ID gives you another, separate conversation.
SESSION_ID="cli-session"
MEMORY_DIR="$(dirname "$0")/.memory"
MEMORY_FILE="$MEMORY_DIR/$SESSION_ID.json"

mkdir -p "$MEMORY_DIR"

# 1) Load the memory: the whole conversation is the file, so reading it back is
# just cat. If there is no file yet (first run), start a new conversation seeded
# with the system message, exactly as 00.03 did.
if [ -s "$MEMORY_FILE" ]; then
  MESSAGES=$(cat "$MEMORY_FILE")
  echo "===== 🧠 MEMORY RESTORED FROM DISK ($(echo "$MESSAGES" | jq 'length') messages) 🧠 ====="
  echo "$MESSAGES" | jq .
  echo
else
  MESSAGES=$(jq -n '[
    { role: "system", content: "provide a concise answer" }
  ]')
  echo "===== 🧠 NO MEMORY YET, STARTING A NEW CONVERSATION 🧠 ====="
  echo
fi

echo "===== 🧠 CHATBOT WITH PERSISTENT MEMORY (type \"exit\" to quit) 🧠 ====="
echo "💾 stored in $MEMORY_FILE"
echo

while true; do
  # Ask the user for a prompt.
  read -rp "⌨️  Your prompt: " USER_PROMPT

  echo

  # Leave the loop on "exit" or on an empty prompt.
  [ -z "$USER_PROMPT" ] && continue
  [ "$USER_PROMPT" = "exit" ] && break

  # 2) Append the user message to the memory.
  # jq safely encodes the user input into valid JSON.
  MESSAGES=$(echo "$MESSAGES" | jq --arg content "$USER_PROMPT" \
    '. + [ { role: "user", content: $content } ]')

  # 3) Build the JSON request body from the WHOLE memory
  BODY=$(jq -n --arg model "$MODEL" --argjson messages "$MESSAGES" '{
    model: $model,
    stream: true,
    messages: $messages
  }')

  # Print the JSON payload sent to the model, pretty-printed with jq: on a
  # second run, the messages array does not start empty anymore.
  echo "===== ⬆️ JSON REQUEST (memory sent to the model) ⬆️ ====="
  echo "$BODY" | jq .
  echo

  # 4) Send the request and stream the answer token by token, while rebuilding
  # the full answer in ANSWER: we need it complete to store it in the memory.
  # -N (--no-buffer) disables curl output buffering so chunks are printed live.
  # NOTE: the SSE loop is fed by a process substitution < <(curl ...) and not by
  # a pipe, because a pipe would run the loop in a subshell and ANSWER would be
  # lost as soon as the loop ends.
  echo "===== 🤖 ANSWER (streaming) 🤖 ====="
  ANSWER=""
  while IFS= read -r line; do
    # SSE lines look like: "data: {json}" and end with "data: [DONE]".
    line="${line#data: }"                 # strip the "data: " prefix
    [ -z "$line" ] && continue            # skip empty keep-alive lines
    [ "$line" = "[DONE]" ] && break       # end of the stream

    # Extract the incremental text from this chunk, print it without newline
    # and append it to the answer being rebuilt.
    CHUNK=$(echo "$line" | jq -rj '.choices[0].delta.content // empty')
    printf '%s' "$CHUNK"
    ANSWER="${ANSWER}${CHUNK}"
  done < <(curl -sN "$ENDPOINT" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer ${OVH_AI_ENDPOINTS_ACCESS_TOKEN}" \
    -d "$BODY")

  # Final newline once the stream is complete.
  echo
  echo

  # 5) Append the model answer to the memory, so the next call gets the full
  # conversation: this is what makes the model look like it remembers.
  MESSAGES=$(echo "$MESSAGES" | jq --arg content "$ANSWER" \
    '. + [ { role: "assistant", content: $content } ]')

  # 6) Save the memory. THIS is the whole point of the example, and it is a
  # single redirection: the memory never stopped being JSON, so there is nothing
  # to serialize. Writing after every answer (rather than once at the end) means
  # an interrupted session is still saved.
  echo "$MESSAGES" > "$MEMORY_FILE"
  echo "💾 memory saved to $MEMORY_FILE ($(echo "$MESSAGES" | jq 'length') messages)"
  echo
done

# Print the final memory: the whole conversation, now kept on disk. Run the
# script again and this is exactly what it will start from.
echo "===== 🧠 FINAL MEMORY (kept in $MEMORY_FILE) 🧠 ====="
echo "$MESSAGES" | jq .
echo
echo "🗑️  Delete $MEMORY_FILE to start a fresh conversation."
