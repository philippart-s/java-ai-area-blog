#!/usr/bin/env bash
#
# Simple launcher for the LangChain4j Java (JBang) examples.
# It loads the API key from the project-root .env, then runs the chosen
# example with JBang.
#
# Usage:
#   ./run.sh                                 # runs the simple chatbot by default
#   ./run.sh _03_01_SimpleChatbot.java       # runs a specific example
#   ./run.sh _03_02_StreamingChatbot.java
#   ./run.sh _03_03_StreamingChatbotMemory.java
#   ./run.sh _03_04_StreamingChatbotMultiSessionMemory.java

set -euo pipefail

# Directory of this script (so it works from anywhere).
DIR="$(dirname "$0")"

# Load environment variables from the project-root .env.
# The .env file is expected to contain: OVH_AI_ENDPOINTS_ACCESS_TOKEN=...
set -a
source "$DIR/../.env"
set +a

# Default to the simple example if none is provided.
SCRIPT="${1:-_03_01_SimpleChatbot.java}"

# Run the selected example with JBang, from this script's own directory: the
# examples that persist their memory write .memory/ next to the script, and the
# JVM launched by JBang has no way to locate the .java file it runs.
cd "$DIR"
jbang "$SCRIPT"
