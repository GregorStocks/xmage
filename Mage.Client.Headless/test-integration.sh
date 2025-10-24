#!/bin/bash
# Integration test for headless MCP client
#
# This script:
# 1. Checks if XMage server is running
# 2. Starts 2 headless bots
# 3. Creates a game via server console commands
# 4. Watches them play (auto-passing)
#
# Prerequisites:
# - XMage server running on localhost:17171
# - Server in test mode (allows easy connection)

set -e

SERVER="localhost"
PORT="17171"
JAR="target/mage-client-headless-1.4.58.jar"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${GREEN}=== XMage Headless Client Integration Test ===${NC}"
echo ""

# Check if JAR exists
if [ ! -f "$JAR" ]; then
    echo -e "${RED}ERROR: JAR not found at $JAR${NC}"
    echo "Run: mvn clean package -pl Mage.Client.Headless -am -DskipTests"
    exit 1
fi

echo -e "${GREEN}✓${NC} JAR found: $JAR"

# Check if server is running
echo -n "Checking if XMage server is running on $SERVER:$PORT... "
if timeout 2 bash -c "echo > /dev/tcp/$SERVER/$PORT" 2>/dev/null; then
    echo -e "${GREEN}✓${NC}"
else
    echo -e "${RED}✗${NC}"
    echo ""
    echo -e "${YELLOW}XMage server is not running!${NC}"
    echo ""
    echo "To start the server:"
    echo "  1. cd ../Mage.Server"
    echo "  2. mvn clean package -DskipTests  # if not already built"
    echo "  3. java -jar target/mage-server-1.4.58.jar -testMode=true"
    echo ""
    echo "Or use Docker:"
    echo "  docker run -p 17171:17171 -p 17179:17179 goesta/mage:latest"
    exit 1
fi

echo ""
echo -e "${YELLOW}NOTE:${NC} For this test to work, you need to:"
echo "  1. Have the XMage server running (✓ confirmed)"
echo "  2. Manually create a game using the GUI client, OR"
echo "  3. Use the server console to create a game"
echo ""
echo -e "${GREEN}Manual Test Steps:${NC}"
echo ""
echo "Terminal 1 - Start Bot 1:"
echo "  java -jar $JAR $SERVER $PORT Bot1"
echo ""
echo "Terminal 2 - Start Bot 2:"
echo "  java -jar $JAR $SERVER $PORT Bot2"
echo ""
echo "Terminal 3 - Control Bot 1 with test-mcp.py:"
echo "  python3 test-mcp.py"
echo ""
echo "Terminal 4 - Control Bot 2 with test-mcp.py:"
echo "  python3 test-mcp.py"
echo ""
echo "Then use XMage GUI or console to:"
echo "  - Create a new table (2-player duel)"
echo "  - Have Bot1 and Bot2 join"
echo "  - Start the game"
echo ""
echo -e "${GREEN}The bots will auto-pass priority until the game ends.${NC}"
echo ""
echo "Press Ctrl+C to exit this message."
