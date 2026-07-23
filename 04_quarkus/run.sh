#!/usr/bin/env bash
#
# Simple launcher for the Quarkus + quarkus-langchain4j (JBang) examples.
# It loads the API token from the project-root .env into the environment
# (application.properties reads it via the ${OVH_AI_ENDPOINTS_ACCESS_TOKEN} config
# expression), then runs the chosen example with JBang, which builds and runs it
# as a Quarkus app.
#
# Usage:
#   ./run.sh                                 # runs the simple chatbot by default
#   ./run.sh _04_01_SimpleChatbot.java       # runs a specific example
#   ./run.sh _04_02_StreamingChatbot.java

set -euo pipefail

# Directory of this script (so it works from anywhere).
DIR="$(dirname "$0")"

# Load environment variables from the project-root .env.
# The .env file is expected to contain: OVH_AI_ENDPOINTS_ACCESS_TOKEN=...
set -a
source "$DIR/../.env"
set +a

# Default to the simple example if none is provided.
SCRIPT="${1:-_04_01_SimpleChatbot.java}"

# Run the selected example with JBang (JBang drives the Quarkus build).
jbang "$DIR/$SCRIPT"