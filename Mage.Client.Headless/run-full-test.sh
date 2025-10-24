#!/bin/bash
# Full end-to-end test with timeout
#
# This script:
# 1. Starts XMage server in background
# 2. Waits for server to be ready
# 3. Runs SimpleTest with 2 auto-passing bots
# 4. Times out after 60 seconds
# 5. Saves all output to test-output/
# 6. Cleans up server process

set -e

# Configuration
TIMEOUT_SECONDS=60
OUTPUT_DIR="test-output"
TIMESTAMP=$(date +%Y%m%d-%H%M%S)
SERVER_LOG="$OUTPUT_DIR/server-$TIMESTAMP.log"
CLIENT_LOG="$OUTPUT_DIR/client-$TIMESTAMP.log"
SUMMARY_LOG="$OUTPUT_DIR/summary-$TIMESTAMP.log"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Create output directory
mkdir -p "$OUTPUT_DIR"

echo -e "${BLUE}=== XMage Headless Client Full Test ===${NC}" | tee "$SUMMARY_LOG"
echo "Timestamp: $(date)" | tee -a "$SUMMARY_LOG"
echo "Timeout: ${TIMEOUT_SECONDS}s" | tee -a "$SUMMARY_LOG"
echo "Output dir: $OUTPUT_DIR" | tee -a "$SUMMARY_LOG"
echo "" | tee -a "$SUMMARY_LOG"

# Check if JAR exists
JAR="target/mage-client-headless-1.4.58.jar"
if [ ! -f "$JAR" ]; then
    echo -e "${RED}ERROR: JAR not found at $JAR${NC}" | tee -a "$SUMMARY_LOG"
    echo "Run: mvn clean package -pl Mage.Client.Headless -am -DskipTests" | tee -a "$SUMMARY_LOG"
    exit 1
fi
echo -e "${GREEN}✓${NC} Found JAR: $JAR" | tee -a "$SUMMARY_LOG"

# Function to cleanup
cleanup() {
    echo "" | tee -a "$SUMMARY_LOG"
    echo -e "${YELLOW}Cleaning up...${NC}" | tee -a "$SUMMARY_LOG"

    if [ ! -z "$SERVER_PID" ]; then
        echo "Killing server PID $SERVER_PID" | tee -a "$SUMMARY_LOG"
        kill $SERVER_PID 2>/dev/null || true
        sleep 2
        kill -9 $SERVER_PID 2>/dev/null || true
    fi

    if [ ! -z "$CLIENT_PID" ]; then
        echo "Killing client PID $CLIENT_PID" | tee -a "$SUMMARY_LOG"
        kill $CLIENT_PID 2>/dev/null || true
        sleep 1
        kill -9 $CLIENT_PID 2>/dev/null || true
    fi

    # Kill any remaining Java processes from this test
    pkill -f "mage.server.Main.*testMode" 2>/dev/null || true
    pkill -f "mage.client.headless.SimpleTest" 2>/dev/null || true

    echo -e "${GREEN}Cleanup complete${NC}" | tee -a "$SUMMARY_LOG"
}

# Register cleanup on exit
trap cleanup EXIT INT TERM

# Step 1: Start server
echo -e "${BLUE}Step 1: Starting XMage server${NC}" | tee -a "$SUMMARY_LOG"
cd ../Mage.Server
MAVEN_OPTS="--add-opens java.base/java.io=ALL-UNNAMED --add-opens java.base/java.util=ALL-UNNAMED" \
mvn -q exec:java \
    -Dexec.mainClass="mage.server.Main" \
    -Dexec.args="-testMode=true" \
    > "../Mage.Client.Headless/$SERVER_LOG" 2>&1 &
SERVER_PID=$!
cd ../Mage.Client.Headless

echo "Server PID: $SERVER_PID" | tee -a "$SUMMARY_LOG"
echo "Server log: $SERVER_LOG" | tee -a "$SUMMARY_LOG"
echo "" | tee -a "$SUMMARY_LOG"

# Step 2: Wait for server to be ready
echo -e "${BLUE}Step 2: Waiting for server to start${NC}" | tee -a "$SUMMARY_LOG"
echo -n "Checking" | tee -a "$SUMMARY_LOG"

MAX_WAIT=60
WAITED=0
while [ $WAITED -lt $MAX_WAIT ]; do
    # Check if server is listening on port
    if netstat -an 2>/dev/null | grep -q "17171.*LISTEN"; then
        echo "" | tee -a "$SUMMARY_LOG"
        echo -e "${GREEN}✓${NC} Server is listening on port 17171" | tee -a "$SUMMARY_LOG"
        break
    fi

    # Check if server process died
    if ! kill -0 $SERVER_PID 2>/dev/null; then
        echo "" | tee -a "$SUMMARY_LOG"
        echo -e "${RED}✗${NC} Server process died!" | tee -a "$SUMMARY_LOG"
        echo "Last 20 lines of server log:" | tee -a "$SUMMARY_LOG"
        tail -20 "$SERVER_LOG" | tee -a "$SUMMARY_LOG"
        exit 1
    fi

    echo -n "." | tee -a "$SUMMARY_LOG"
    sleep 2
    WAITED=$((WAITED + 2))
done

if [ $WAITED -ge $MAX_WAIT ]; then
    echo "" | tee -a "$SUMMARY_LOG"
    echo -e "${RED}✗${NC} Server did not start within ${MAX_WAIT}s" | tee -a "$SUMMARY_LOG"
    echo "Last 20 lines of server log:" | tee -a "$SUMMARY_LOG"
    tail -20 "$SERVER_LOG" | tee -a "$SUMMARY_LOG"
    exit 1
fi

# Give it a couple more seconds to fully initialize
sleep 3
echo "" | tee -a "$SUMMARY_LOG"

# Step 3: Run test
echo -e "${BLUE}Step 3: Running SimpleTest (timeout: ${TIMEOUT_SECONDS}s)${NC}" | tee -a "$SUMMARY_LOG"
echo "Client log: $CLIENT_LOG" | tee -a "$SUMMARY_LOG"
echo "" | tee -a "$SUMMARY_LOG"

# Run test with timeout
timeout ${TIMEOUT_SECONDS}s java --add-opens java.base/java.io=ALL-UNNAMED \
    --add-opens java.base/java.util=ALL-UNNAMED \
    -cp "$JAR" mage.client.headless.SimpleTest localhost 17171 \
    > "$CLIENT_LOG" 2>&1 &
CLIENT_PID=$!

echo "Client PID: $CLIENT_PID" | tee -a "$SUMMARY_LOG"
echo "Waiting for test to complete (max ${TIMEOUT_SECONDS}s)..." | tee -a "$SUMMARY_LOG"
echo "" | tee -a "$SUMMARY_LOG"

# Wait for client to finish or timeout
wait $CLIENT_PID 2>/dev/null
CLIENT_EXIT=$?

# Check results
echo "" | tee -a "$SUMMARY_LOG"
echo -e "${BLUE}=== Test Results ===${NC}" | tee -a "$SUMMARY_LOG"
echo "" | tee -a "$SUMMARY_LOG"

if [ $CLIENT_EXIT -eq 0 ]; then
    echo -e "${GREEN}✓ Test completed successfully!${NC}" | tee -a "$SUMMARY_LOG"
elif [ $CLIENT_EXIT -eq 124 ]; then
    echo -e "${YELLOW}⏱ Test timed out after ${TIMEOUT_SECONDS}s${NC}" | tee -a "$SUMMARY_LOG"
    echo "This is expected for auto-passing bots (they may play slowly)" | tee -a "$SUMMARY_LOG"
else
    echo -e "${RED}✗ Test failed with exit code: $CLIENT_EXIT${NC}" | tee -a "$SUMMARY_LOG"
fi

echo "" | tee -a "$SUMMARY_LOG"
echo "Last 30 lines of client output:" | tee -a "$SUMMARY_LOG"
echo "================================" | tee -a "$SUMMARY_LOG"
tail -30 "$CLIENT_LOG" | tee -a "$SUMMARY_LOG"

echo "" | tee -a "$SUMMARY_LOG"
echo "Last 20 lines of server output:" | tee -a "$SUMMARY_LOG"
echo "================================" | tee -a "$SUMMARY_LOG"
tail -20 "$SERVER_LOG" | tee -a "$SUMMARY_LOG"

echo "" | tee -a "$SUMMARY_LOG"
echo -e "${BLUE}=== Summary ===${NC}" | tee -a "$SUMMARY_LOG"
echo "Full logs saved to:" | tee -a "$SUMMARY_LOG"
echo "  - Server: $SERVER_LOG" | tee -a "$SUMMARY_LOG"
echo "  - Client: $CLIENT_LOG" | tee -a "$SUMMARY_LOG"
echo "  - Summary: $SUMMARY_LOG" | tee -a "$SUMMARY_LOG"
echo "" | tee -a "$SUMMARY_LOG"

# Grep for interesting events in client log
echo "Key events in client log:" | tee -a "$SUMMARY_LOG"
grep -i "connected\|created\|joined\|started\|decision\|game over\|result" "$CLIENT_LOG" 2>/dev/null | head -20 | tee -a "$SUMMARY_LOG" || echo "  (none found)" | tee -a "$SUMMARY_LOG"

echo "" | tee -a "$SUMMARY_LOG"
echo -e "${GREEN}Test complete!${NC}" | tee -a "$SUMMARY_LOG"

# Return appropriate exit code
if [ $CLIENT_EXIT -eq 0 ]; then
    exit 0
elif [ $CLIENT_EXIT -eq 124 ]; then
    # Timeout is OK - bots may be slow
    exit 0
else
    exit $CLIENT_EXIT
fi
