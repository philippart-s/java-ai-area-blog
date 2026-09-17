#!/usr/bin/env bash
#
# Launcher for the OpenAI Java SDK examples: loads the token from the project-root .env,
# then runs the chosen example with JBang from this directory.
#
# Usage:
#   ./run.sh                        # defaults to _02_01_SimpleChatbot.java
#   ./run.sh _02_02_StreamingChatbot.java
#   ./run.sh _02_03_StreamingChatbotMemory.java
#   ./run.sh _02_04_StreamingChatbotFileMemory.java

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

# Run the selected example with JBang, from this script's own directory: the
# examples that persist their memory write .memory/ next to the script, and the
# JVM launched by JBang has no way to locate the .java file it runs.
cd "$DIR"
jbang "$SCRIPT"
