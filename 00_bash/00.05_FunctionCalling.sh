#!/usr/bin/env bash
#
# Function calling example calling OVHcloud AI Endpoints (gpt-oss-120b).
#
# The request describes a get_room_capacity function. The model does not answer
# the question: it asks for the function to be called. The script runs it, sends
# the result back, and the model then writes the final answer. Needs curl and jq.
#
# Docs: https://www.ovhcloud.com/en/public-cloud/ai-endpoints/catalog/gpt-oss-120b/
# Docs: https://platform.openai.com/docs/guides/function-calling

set -a
source "$(dirname "$0")/../.env"
set +a

ENDPOINT="https://oai.endpoints.kepler.ai.cloud.ovh.net/v1/chat/completions"
MODEL="gpt-oss-120b"
QUESTION="What is the capacity of the Nautilus room?"

# The function the model is allowed to ask for. A random number stands in for a
# real lookup: the model cannot guess it, so the answer has to come from here.
get_room_capacity() {
  local capacity=$(( RANDOM % 451 + 50 ))

  # Printed on stderr: stdout is the result handed back to the model.
  echo "🐞 get_room_capacity called for room \"$1\", returning capacity $capacity" >&2
  echo >&2

  jq -nc --arg room "$1" --argjson capacity "$capacity" \
    '{ room: $room, capacity: $capacity }'
}

# The description of the function, as a JSON schema. This is all the model knows
# about it: the code above never leaves this machine.
TOOLS=$(jq -n '[
  {
    type: "function",
    function: {
      name: "get_room_capacity",
      description: "Returns the number of seats of a conference room",
      parameters: {
        type: "object",
        properties: {
          room: { type: "string", description: "Name of the room, e.g. Coral" }
        },
        required: ["room"]
      }
    }
  }
]')

MESSAGES=$(jq -n --arg content "$QUESTION" '[
  { role: "system", content: "provide a concise answer" },
  { role: "user", content: $content }
]')

# Sends the conversation, along with the functions available, and prints the response.
chat() {
  jq -n --arg model "$MODEL" --argjson messages "$MESSAGES" --argjson tools "$TOOLS" '{
    model: $model,
    messages: $messages,
    tools: $tools
  }' | curl -s "$ENDPOINT" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer ${OVH_AI_ENDPOINTS_ACCESS_TOKEN}" \
    -d @-
}

# 1) First call: the question and the description of the function.
echo "===== ⬆️ JSON REQUEST (question + tools sent to the model) ⬆️ ====="
echo "$MESSAGES" | jq --argjson tools "$TOOLS" '{ messages: ., tools: $tools }'
echo

RESPONSE=$(chat)

echo "===== ⬇️ JSON RESPONSE ⬇️ ====="
echo "$RESPONSE" | jq .
echo

# 2) The model answered with a tool call instead of a text: a function name and
#    its arguments, themselves a JSON document carried in a string.
ASSISTANT_MESSAGE=$(echo "$RESPONSE" | jq '.choices[0].message')
TOOL_CALL_ID=$(echo "$ASSISTANT_MESSAGE" | jq -r '.tool_calls[0].id')
FUNCTION_NAME=$(echo "$ASSISTANT_MESSAGE" | jq -r '.tool_calls[0].function.name')
ARGUMENTS=$(echo "$ASSISTANT_MESSAGE" | jq -r '.tool_calls[0].function.arguments')

echo "===== 🛠️ TOOL CALL (requested by the model) 🛠️ ====="
echo "$FUNCTION_NAME $ARGUMENTS"
echo

# 3) Run the function. The model only named it, calling it is up to the script.
ROOM=$(echo "$ARGUMENTS" | jq -r '.room')
RESULT=$(get_room_capacity "$ROOM")

echo "===== 🛠️ TOOL RESULT (sent back to the model) 🛠️ ====="
echo "$RESULT"
echo

# 4) Second call: the conversation so far, plus the tool call of the model and
#    its result, tied together by the tool call id.
MESSAGES=$(echo "$MESSAGES" | jq \
  --argjson assistant "$ASSISTANT_MESSAGE" \
  --arg id "$TOOL_CALL_ID" \
  --arg result "$RESULT" \
  '. + [ $assistant, { role: "tool", tool_call_id: $id, content: $result } ]')

echo "===== ⬆️ JSON REQUEST (tool call + tool result sent to the model) ⬆️ ====="
echo "$MESSAGES" | jq '{ messages: . }'
echo

RESPONSE=$(chat)

echo "===== 🤖 ANSWER 🤖 ====="
echo "$RESPONSE" | jq -r '.choices[0].message.content'
