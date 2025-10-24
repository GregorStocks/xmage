#!/bin/bash
# Start XMage server in test mode for headless client testing
#
# Test mode features:
# - No password required
# - Easy registration
# - Fast game buttons
# - Cheat commands available

set -e

cd "$(dirname "$0")/Mage.Server"

echo "Starting XMage server in test mode..."
echo "Server will listen on localhost:17171"
echo ""
echo "Press Ctrl+C to stop the server"
echo ""

# Run via Maven to get proper classpath
# Add JVM flags for Java 9+ compatibility with JBoss Remoting
MAVEN_OPTS="--add-opens java.base/java.io=ALL-UNNAMED --add-opens java.base/java.util=ALL-UNNAMED" \
mvn -q exec:java \
    -Dexec.mainClass="mage.server.Main" \
    -Dexec.args="-testMode=true"
