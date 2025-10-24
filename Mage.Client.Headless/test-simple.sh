#!/bin/bash
# Run SimpleTest - 2 bots playing against each other, both auto-passing
#
# Prerequisites:
# - XMage server running on localhost:17171 in test mode

set -e

SERVER="${1:-localhost}"
PORT="${2:-17171}"
JAR="target/mage-client-headless-1.4.58.jar"

echo "=== SimpleTest: 2 Bots Auto-Passing ==="
echo "Server: $SERVER:$PORT"
echo ""

# Check if JAR exists
if [ ! -f "$JAR" ]; then
    echo "ERROR: JAR not found at $JAR"
    echo "Run: mvn clean package -pl Mage.Client.Headless -am -DskipTests"
    exit 1
fi

# Check if server is running
echo -n "Checking server... "
if timeout 2 bash -c "echo > /dev/tcp/$SERVER/$PORT" 2>/dev/null; then
    echo "✓"
else
    echo "✗"
    echo ""
    echo "ERROR: XMage server is not running on $SERVER:$PORT"
    echo ""
    echo "To start the server:"
    echo "  cd ../Mage.Server"
    echo "  mvn clean package -DskipTests  # if not already built"
    echo "  java -jar target/mage-server-1.4.58.jar -testMode=true"
    echo ""
    exit 1
fi

echo ""
echo "Starting test..."
echo "The bots will create a game and auto-pass until it ends."
echo "This may take a few minutes."
echo "Press Ctrl+C to stop."
echo ""

java --add-opens java.base/java.io=ALL-UNNAMED \
     --add-opens java.base/java.util=ALL-UNNAMED \
     -cp "$JAR" mage.client.headless.SimpleTest "$SERVER" "$PORT"
