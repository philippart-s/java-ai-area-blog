#!/usr/bin/env bash
#
# Streaming chatbot with memory example calling OVHcloud AI Endpoints
# (gpt-oss-120b).
# Same idea as 00.02 but with a conversation memory: the chat completions API
# is stateless, so the model remembers nothing between two calls. The "memory"
# is entirely on the client side: we keep the whole conversation in a messages
# array and send it back, in full, on every request.
#
# The answer is still streamed token by token, and rebuilt on the fly so it can
# be stored in the memory once complete.
#
# The script loops so you can chat with the model, and prints the messages
# array before each call: you literally see the memory growing turn after turn.
# Type "exit" (or press Ctrl+C) to quit.
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

# The conversation memory: a JSON array of messages, seeded with the system
# message. Every user prompt and every model answer is appended to it.
MESSAGES=$(jq -n '[
  { role: "system", content: "provide a concise answer" }
]')

echo "===== 🧠 CHATBOT WITH MEMORY (type \"exit\" to quit) 🧠 ====="
echo

while true; do
  # Ask the user for a prompt.
  read -rp "⌨️  Your prompt: " USER_PROMPT

  echo

  # Leave the loop on "exit" or on an empty prompt.
  [ -z "$USER_PROMPT" ] && continue
  [ "$USER_PROMPT" = "exit" ] && break

  # 1) Append the user message to the memory.
  # jq safely encodes the user input into valid JSON.
  MESSAGES=$(echo "$MESSAGES" | jq --arg content "$USER_PROMPT" \
    '. + [ { role: "user", content: $content } ]')

  # 2) Build the JSON request body from the WHOLE memory
  BODY=$(jq -n --arg model "$MODEL" --argjson messages "$MESSAGES" '{
    model: $model,
    stream: true,
    messages: $messages
  }')

  # Print the JSON payload sent to the model, pretty-printed with jq:
  # notice how the messages array grows at each turn.
  echo "===== ⬆️ JSON REQUEST (memory sent to the model) ⬆️ ====="
  echo "$BODY" | jq .
  echo

  # 3) Send the request and stream the answer token by token, while rebuilding
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

  # 4) Append the model answer to the memory, so the next call gets the full
  # conversation: this is what makes the model look like it remembers.
  MESSAGES=$(echo "$MESSAGES" | jq --arg content "$ANSWER" \
    '. + [ { role: "assistant", content: $content } ]')
done

# Print the final memory: the whole conversation, kept client side.
echo "===== 🧠 FINAL MEMORY (the whole conversation) 🧠 ====="
echo "$MESSAGES" | jq .
