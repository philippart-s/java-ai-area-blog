#!/usr/bin/env bash
#
# Streaming chatbot with memory example calling OVHcloud AI Endpoints
# (gpt-oss-120b).
#
# The chat completions API is stateless: the memory is a JSON array of messages
# kept client side and resent in full on every request. The streamed answer is
# reassembled on the fly so it can be appended to it.
#
# The messages array is printed before each call, so the memory can be seen
# growing turn after turn. Type "exit" (or press Ctrl+C) to quit.
#
# Needs curl and jq.
#
# Docs: https://www.ovhcloud.com/en/public-cloud/ai-endpoints/catalog/gpt-oss-120b/

# The .env at the project root must contain: OVH_AI_ENDPOINTS_ACCESS_TOKEN=...
set -a
source "$(dirname "$0")/../.env"
set +a

ENDPOINT="https://oai.endpoints.kepler.ai.cloud.ovh.net/v1/chat/completions"
MODEL="gpt-oss-120b"

# The memory, seeded with the system message.
MESSAGES=$(jq -n '[
  { role: "system", content: "provide a concise answer" }
]')

echo "===== 🧠 CHATBOT WITH MEMORY (type \"exit\" to quit) 🧠 ====="
echo

while true; do
  read -rp "⌨️  Your prompt: " USER_PROMPT

  echo

  [ -z "$USER_PROMPT" ] && continue
  [ "$USER_PROMPT" = "exit" ] && break

  MESSAGES=$(echo "$MESSAGES" | jq --arg content "$USER_PROMPT" \
    '. + [ { role: "user", content: $content } ]')

  BODY=$(jq -n --arg model "$MODEL" --argjson messages "$MESSAGES" '{
    model: $model,
    stream: true,
    messages: $messages
  }')

  # The whole memory is sent again, and it grows by two messages per turn.
  echo "===== ⬆️ JSON REQUEST (memory sent to the model) ⬆️ ====="
  echo "$BODY" | jq .
  echo

  # Print the answer token by token while rebuilding it: the memory needs it whole.
  # Fed by a process substitution rather than a pipe: a pipe would run the loop
  # in a subshell and ANSWER would be lost when the loop ends.
  echo "===== 🤖 ANSWER (streaming) 🤖 ====="
  ANSWER=""
  while IFS= read -r line; do
    # SSE lines look like "data: {json}", and the stream ends with "data: [DONE]".
    line="${line#data: }"
    [ -z "$line" ] && continue
    [ "$line" = "[DONE]" ] && break

    CHUNK=$(echo "$line" | jq -rj '.choices[0].delta.content // empty')
    printf '%s' "$CHUNK"
    ANSWER="${ANSWER}${CHUNK}"
  done < <(curl -sN "$ENDPOINT" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer ${OVH_AI_ENDPOINTS_ACCESS_TOKEN}" \
    -d "$BODY")

  echo
  echo

  MESSAGES=$(echo "$MESSAGES" | jq --arg content "$ANSWER" \
    '. + [ { role: "assistant", content: $content } ]')
done

echo "===== 🧠 FINAL MEMORY (the whole conversation) 🧠 ====="
echo "$MESSAGES" | jq .
