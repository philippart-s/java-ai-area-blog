#!/usr/bin/env bash
#
# Streaming chatbot with a persistent memory example calling OVHcloud AI
# Endpoints (gpt-oss-120b).
#
# The conversation is stored in .memory/<session>.json, so it survives quitting
# the script. Run it twice. Type "exit" (or press Ctrl+C) to quit.
# Needs curl and jq.
#
# Docs: https://www.ovhcloud.com/en/public-cloud/ai-endpoints/catalog/gpt-oss-120b/

set -a
source "$(dirname "$0")/../.env"
set +a

ENDPOINT="https://oai.endpoints.kepler.ai.cloud.ovh.net/v1/chat/completions"
MODEL="gpt-oss-120b"

# Stored next to this script whatever the current directory, thanks to $0.
# One file per session id, so another id is another conversation.
SESSION_ID="cli-session"
MEMORY_DIR="$(dirname "$0")/.memory"
MEMORY_FILE="$MEMORY_DIR/$SESSION_ID.json"

mkdir -p "$MEMORY_DIR"

# The memory is already JSON, so loading it is a cat.
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

  # Written after every answer, so an interrupted session is still saved.
  echo "$MESSAGES" > "$MEMORY_FILE"
  echo "💾 memory saved to $MEMORY_FILE ($(echo "$MESSAGES" | jq 'length') messages)"
  echo
done

echo "===== 🧠 FINAL MEMORY (kept in $MEMORY_FILE) 🧠 ====="
echo "$MESSAGES" | jq .
echo
echo "🗑️  Delete $MEMORY_FILE to start a fresh conversation."
