# Testing Guide

This guide explains how to test the headless XMage client with MCP server.

## Prerequisites

### 1. Build the headless client

```bash
cd /Users/gregorstocks/code/xmage
mvn clean package -pl Mage.Client.Headless -am -DskipTests
```

This creates: `Mage.Client.Headless/target/mage-client-headless-1.4.58.jar`

### 2. Start XMage Server

You need a running XMage server. Either:

**Option A: Build and run from source**
```bash
cd Mage.Server
mvn clean package -DskipTests
java -jar target/mage-server-1.4.58.jar -testMode=true
```

**Option B: Use Docker**
```bash
docker run -p 17171:17171 -p 17179:17179 goesta/mage:latest
```

The server should be running on `localhost:17171`.

## Test 1: Simple Auto-Pass Test (Easiest)

This test creates 2 bots that play against each other, both automatically passing priority. It's fully automated and requires no manual intervention.

```bash
cd Mage.Client.Headless
./test-simple.sh
```

**What it does:**
1. Bot1 connects and creates a 2-player game table
2. Bot1 joins the table with a simple deck (24 Mountains, 36 Lightning Bolts)
3. Bot2 connects and joins the same table
4. Bot1 starts the match
5. Both bots auto-pass priority on every decision
6. The game plays out until someone wins

**Expected output:**
```
=== SimpleTest: 2 Bots Auto-Passing ===
Server: localhost:17171

--- Creating Bot 1 ---
TestBot1 connected successfully

--- Creating Game Table ---
Table created with ID: ...

--- Bot 1 Joining Table ---
Successfully joined table

--- Creating Bot 2 ---
TestBot2 connected successfully

--- Bot 2 Joining Table ---
Successfully joined table

--- Starting Match ---

=== Game Started ===
Bots will auto-pass until game ends...

TestBot1 auto-pass thread started
TestBot2 auto-pass thread started
TestBot1 received decision: ASK
TestBot2 received decision: ASK
...
```

The game will eventually end (may take a few minutes as both players just pass).

## Test 2: MCP Protocol Test (Manual)

This test demonstrates the MCP protocol by having you send MCP commands via stdin.

### Terminal 1: Start the headless client

```bash
cd Mage.Client.Headless
java -jar target/mage-client-headless-1.4.58.jar localhost 17171 MCPBot1
```

This starts the client and exposes the MCP server on stdin/stdout.

### Terminal 2: Create a game (using GUI client)

1. Download and start XMage GUI client
2. Connect to localhost:17171
3. Create a 2-player duel table
4. Join with any deck
5. Note: Don't start the game yet, wait for the bot

### Terminal 3: Send MCP commands

In Terminal 1, type these JSON-RPC commands:

**Initialize:**
```json
{"jsonrpc":"2.0","id":1,"method":"initialize"}
```

**List tools:**
```json
{"jsonrpc":"2.0","id":2,"method":"tools/list"}
```

**Wait for your turn (this will block until a decision is needed):**
```json
{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"wait_for_my_turn","arguments":{}}}
```

Now in the GUI client, start the game. The bot will receive the first decision and `wait_for_my_turn` will return with game state.

**Submit a decision (pass priority):**
```json
{"jsonrpc":"2.0","id":4,"method":"tools/call","params":{"name":"submit_boolean_decision","arguments":{"value":false}}}
```

Then call `wait_for_my_turn` again to wait for the next decision.

## Test 3: Python MCP Client Test

This uses the provided `test-mcp.py` script to control a bot.

### Terminal 1: Start headless client

```bash
cd Mage.Client.Headless
java -jar target/mage-client-headless-1.4.58.jar localhost 17171 PythonBot > bot.out 2>&1 &
BOT_PID=$!
```

### Terminal 2: Run test-mcp.py

```bash
python3 test-mcp.py
```

This will:
1. Connect to the headless client's stdin/stdout
2. Initialize MCP protocol
3. List available tools
4. Enter a loop of `wait_for_my_turn` → submit decision

**Note:** You still need to create a game manually (via GUI) and have PythonBot join it.

## Test 4: Complete End-to-End with Claude

Once the basic tests work, you can integrate with Claude:

1. **Start headless client**
2. **Your orchestrator** calls Claude API with the game state from `wait_for_my_turn`
3. **Claude analyzes** and returns a decision
4. **Your orchestrator** submits via `submit_*_decision`
5. Loop

Example Python orchestrator:
```python
import subprocess
import json
from anthropic import Anthropic

# Start headless client
bot = subprocess.Popen(
    ["java", "-jar", "target/mage-client-headless-1.4.58.jar", "localhost", "17171", "ClaudeBot"],
    stdin=subprocess.PIPE,
    stdout=subprocess.PIPE,
    text=True
)

anthropic = Anthropic(api_key="...")

while True:
    # Wait for decision
    request = {
        "jsonrpc": "2.0",
        "id": 1,
        "method": "tools/call",
        "params": {"name": "wait_for_my_turn", "arguments": {}}
    }
    bot.stdin.write(json.dumps(request) + "\n")
    bot.stdin.flush()

    response = json.loads(bot.stdout.readline())
    game_state = response["result"]["content"][0]["text"]

    # Ask Claude
    message = anthropic.messages.create(
        model="claude-3-5-sonnet-20241022",
        messages=[{"role": "user", "content": f"You're playing Magic. {game_state}\n\nWhat do you do?"}]
    )

    # Parse Claude's response and submit decision
    # ... (implementation depends on Claude's response format)
```

## Troubleshooting

### "Cannot connect to server"

- Ensure XMage server is running: `ps aux | grep mage-server`
- Check port is listening: `netstat -an | grep 17171`
- Try telnet: `telnet localhost 17171`

### "Failed to create table"

- Server might not be in test mode
- Try with `-testMode=true` flag when starting server

### "Game doesn't start"

- Need 2 players to start a 2-player game
- Check server logs for errors
- Try manually with GUI client first

### "Bot gets stuck"

- Check stderr logs: `tail -f bot.out` (if you redirected stderr)
- Look for exceptions in Java stack traces
- The auto-pass logic might need adjustment for certain decision types

## Files Reference

| File | Purpose |
|------|---------|
| `test-simple.sh` | Automated 2-bot test (easiest) |
| `test-integration.sh` | Manual test instructions |
| `test-mcp.py` | Python MCP client example |
| `SimpleTest.java` | Java test with 2 auto-passing bots |
| `GameCreator.java` | Helper to create games programmatically |

## What's Working

✅ Connection to XMage server
✅ Game creation via API
✅ Joining tables with decks
✅ Auto-payment of mana
✅ MCP protocol over stdin/stdout
✅ Long-polling with `wait_for_my_turn`
✅ Decision submission
✅ Auto-pass logic

## Known Limitations

- Game must have 2+ players before starting
- Bots need valid decks (currently hardcoded simple deck)
- No reconnection logic if connection drops
- Limited error handling for malformed decisions

## Next Steps

1. **Test simple.sh** - verify basic flow works
2. **Test MCP protocol** - verify stdin/stdout communication
3. **Integrate Claude** - build orchestrator that calls Claude API
4. **Scale to 4 players** - Commander game with 4 Claude bots
5. **Dockerize** - deploy with docker-compose

---

**Ready to test!** Start with `./test-simple.sh` - it's the easiest way to verify everything works.
