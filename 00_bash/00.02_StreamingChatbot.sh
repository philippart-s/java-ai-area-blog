#!/usr/bin/env bash
#
# Streaming chatbot example calling OVHcloud AI Endpoints (gpt-oss-120b).
# With "stream": true the endpoint answers with Server-Sent Events, so the
# answer is printed token by token as the chunks arrive.
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

read -rp "⌨️  Your prompt: " USER_PROMPT

echo

# jq -n builds the body and safely encodes the user input into valid JSON.
BODY=$(jq -n --arg model "$MODEL" --arg content "$USER_PROMPT" '{
  model: $model,
  stream: true,
  messages: [
    { role: "system", content: "provide a concise answer" },
    { role: "user", content: $content }
  ]
}')

echo "===== ⬆️ JSON REQUEST (payload sent to the model) ⬆️ ====="
echo "$BODY" | jq .
echo

# -N disables curl output buffering so the chunks are printed live.
echo "===== 🤖 ANSWER (streaming) 🤖 ====="
curl -sN "$ENDPOINT" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${OVH_AI_ENDPOINTS_ACCESS_TOKEN}" \
  -d "$BODY" \
| while IFS= read -r line; do
    # Uncomment to see the raw SSE lines.
    #echo "$line"

    # SSE lines look like "data: {json}", and the stream ends with "data: [DONE]".
    line="${line#data: }"
    [ -z "$line" ] && continue
    [ "$line" = "[DONE]" ] && break

    printf '%s' "$(echo "$line" | jq -rj '.choices[0].delta.content // empty')"
  done

echo
