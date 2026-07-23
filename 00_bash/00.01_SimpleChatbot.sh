#!/usr/bin/env bash
#
# Simple chatbot example calling OVHcloud AI Endpoints (gpt-oss-120b).
# It asks the user for a prompt, sends it to the OpenAI-compatible
# chat completions endpoint, and prints the RAW JSON response as
# returned by the endpoint (no parsing / no pretty printing).
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
read -rp "⌨️ Your prompt: " USER_PROMPT

echo

# 2️⃣ Build the JSON request body.
# jq safely encodes the user input into valid JSON.
#    { role: "system", content: "provide a concise answer" },
BODY=$(jq -n --arg model "$MODEL" --arg content "$USER_PROMPT" '{
  model: $model,
  messages: [
    { role: "user", content: $content }
  ]
}')

# Print the JSON payload sent to the model, pretty-printed with jq.
echo "===== ⬆️ JSON REQUEST (payload sent to the model) ⬆️ ====="
echo "$BODY" | jq .
echo

# Send the request and store the raw response returned by the endpoint.
RESPONSE=$(curl -s "$ENDPOINT" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${OVH_AI_ENDPOINTS_ACCESS_TOKEN}" \
  -d "$BODY")

# 1) Print the JSON response, pretty-printed with jq for readability.
echo "===== ⬇️ JSON RESPONSE ⬇️ ====="
echo "$RESPONSE" | jq .

# 2) Print only the answer, extracted from the JSON with jq.
echo
echo "===== 🤖 ANSWER 🤖 ====="
echo "$RESPONSE" | jq -r '.choices[0].message.content'
