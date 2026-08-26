#!/usr/bin/env bash
#
# Simple launcher for the OpenAI-SDK Java (JBang) examples.
# It loads the API key from the project-root .env, then runs the chosen
# example with JBang.
#
# Usage:
#   ./run.sh                              # runs the simple chatbot by default
#   ./run.sh _02_01_SimpleChatbot.java    # runs a specific example
#   ./run.sh _02_02_StreamingChatbot.java
#   ./run.sh _02_03_StreamingChatbotMemory.java

set -euo pipefail

# Directory of this script (so it works from anywhere).
DIR="$(dirname "$0")"

# Load environment variables from the project-root .env.
# The .env file is expected to contain: OVH_AI_ENDPOINTS_ACCESS_TOKEN=...
set -a
source "$DIR/../.env"
set +a

# Default to the simple example if none is provided.
SCRIPT="${1:-_02_01_SimpleChatbot.java}"

# Run the selected example with JBang.
jbang "$DIR/$SCRIPT"