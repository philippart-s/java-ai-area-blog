#!/usr/bin/env bash
#
# Streaming chatbot example calling OVHcloud AI Endpoints (gpt-oss-120b).
# Same idea as 00.01 but with streaming enabled: the endpoint returns
# Server-Sent Events (SSE) and the answer is printed token by token,
# as soon as each chunk arrives.
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

# Ask the user for a prompt.
read -rp "⌨️  Your prompt: " USER_PROMPT

echo

# Build the JSON request body.
# The key difference with the simple version is "stream": true.
# jq safely encodes the user input into valid JSON.
BODY=$(jq -n --arg model "$MODEL" --arg content "$USER_PROMPT" '{
  model: $model,
  stream: true,
  messages: [
    { role: "system", content: "provide a concise answer" },
    { role: "user", content: $content }
  ]
}')

# Print the JSON payload sent to the model, pretty-printed with jq.
echo "===== ⬆️ JSON REQUEST (payload sent to the model) ⬆️ ====="
echo "$BODY" | jq .
echo

# Send the request and stream the answer.
# -N (--no-buffer) disables curl output buffering so chunks are printed live.
echo "===== 🤖 ANSWER (streaming) 🤖 ====="
curl -sN "$ENDPOINT" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${OVH_AI_ENDPOINTS_ACCESS_TOKEN}" \
  -d "$BODY" \
| while IFS= read -r line; do
    # DEBUG: uncomment to print the raw SSE line exactly as received.
    #echo "$line"

    # SSE lines look like: "data: {json}" and end with "data: [DONE]".
    line="${line#data: }"                 # strip the "data: " prefix
    [ -z "$line" ] && continue            # skip empty keep-alive lines
    [ "$line" = "[DONE]" ] && break       # end of the stream

    # Extract the incremental text from this chunk and print it without newline.
    printf '%s' "$(echo "$line" | jq -rj '.choices[0].delta.content // empty')"
  done

# Final newline once the stream is complete.
echo