# XMage Headless Client with MCP Server

A headless XMage client that exposes game state and actions via the Model Context Protocol (MCP), allowing Claude or other AI agents to play Magic: The Gathering.

## Features

- **Headless**: No GUI, runs in Docker or terminal
- **MCP Server**: Exposes game state via stdio-based MCP protocol
- **Long-polling**: `wait_for_my_turn()` blocks until a decision is needed
- **Nothing up my sleeve**: Each client is independent, connects to neutral server

## Build

```bash
# From the xmage root directory
mvn clean package -pl Mage.Client.Headless -am
```

This creates `Mage.Client.Headless/target/mage-client-headless-1.4.53.jar`

## Usage

### Connect to Existing Game

```bash
java -jar target/mage-client-headless-1.4.53.jar localhost 17171 ClaudeBot1
```

Arguments:
- `localhost` - XMage server host
- `17171` - XMage server port
- `ClaudeBot1` - Username for this client

### Join Specific Game

```bash
java -jar target/mage-client-headless-1.4.53.jar localhost 17171 ClaudeBot1 550e8400-e29b-41d4-a716-446655440000
```

Fourth argument is the game UUID to join.

## MCP Protocol

The client communicates via JSON-RPC 2.0 over stdio:
- **stdin**: Receives MCP requests from Claude
- **stdout**: Sends MCP responses back
- **stderr**: Logs (doesn't interfere with MCP)

### Available Tools

#### 1. `wait_for_my_turn`

Blocks until the game needs you to make a decision.

**Request:**
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "tools/call",
  "params": {
    "name": "wait_for_my_turn",
    "arguments": {}
  }
}
```

**Response:**
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "result": {
    "content": [{
      "type": "text",
      "text": "=== DECISION NEEDED ===\nType: SELECT\nMessage: Choose yes or no\n\n=== GAME STATE ===\nTurn: 3\nPhase: MAIN\n..."
    }]
  }
}
```

#### 2. `submit_uuid_decision`

Submit a decision by UUID (for targets, abilities, cards).

**Request:**
```json
{
  "jsonrpc": "2.0",
  "id": 2,
  "method": "tools/call",
  "params": {
    "name": "submit_uuid_decision",
    "arguments": {
      "uuid": "550e8400-e29b-41d4-a716-446655440000"
    }
  }
}
```

#### 3. `submit_boolean_decision`

Submit yes/no, pass priority, mulligan decisions.

**Request:**
```json
{
  "jsonrpc": "2.0",
  "id": 3,
  "method": "tools/call",
  "params": {
    "name": "submit_boolean_decision",
    "arguments": {
      "value": false
    }
  }
}
```

#### 4. `submit_integer_decision`

Submit numeric decisions (X values, amounts).

**Request:**
```json
{
  "jsonrpc": "2.0",
  "id": 4,
  "method": "tools/call",
  "params": {
    "name": "submit_integer_decision",
    "arguments": {
      "value": 5
    }
  }
}
```

#### 5. `peek_game_state`

Get current game state without blocking (for context queries).

**Request:**
```json
{
  "jsonrpc": "2.0",
  "id": 5,
  "method": "tools/call",
  "params": {
    "name": "peek_game_state",
    "arguments": {}
  }
}
```

## Claude Integration Example

```python
# In your Claude MCP client orchestrator
import json
import subprocess

# Start the headless client
process = subprocess.Popen(
    ["java", "-jar", "mage-client-headless.jar", "localhost", "17171", "ClaudeBot1"],
    stdin=subprocess.PIPE,
    stdout=subprocess.PIPE,
    stderr=subprocess.PIPE,
    text=True
)

while True:
    # Wait for your turn (blocks until decision needed)
    request = {
        "jsonrpc": "2.0",
        "id": 1,
        "method": "tools/call",
        "params": {
            "name": "wait_for_my_turn",
            "arguments": {}
        }
    }
    process.stdin.write(json.dumps(request) + "\n")
    process.stdin.flush()

    # Read response
    response = json.loads(process.stdout.readline())
    decision_text = response["result"]["content"][0]["text"]

    # Give to Claude to analyze
    claude_decision = ask_claude(decision_text)

    # Submit decision
    submit_request = {
        "jsonrpc": "2.0",
        "id": 2,
        "method": "tools/call",
        "params": {
            "name": "submit_boolean_decision",  # or submit_uuid_decision
            "arguments": {"value": claude_decision}
        }
    }
    process.stdin.write(json.dumps(submit_request) + "\n")
    process.stdin.flush()
```

## Docker Compose Example

```yaml
version: '3.8'

services:
  xmage-server:
    image: xmage/xmage-server:latest
    ports:
      - "17171:17171"

  claude-bot-1:
    build: .
    depends_on:
      - xmage-server
    environment:
      - XMAGE_SERVER=xmage-server
      - XMAGE_PORT=17171
      - USERNAME=ClaudeBot1
    stdin_open: true
    tty: true

  claude-bot-2:
    build: .
    depends_on:
      - xmage-server
    environment:
      - XMAGE_SERVER=xmage-server
      - XMAGE_PORT=17171
      - USERNAME=ClaudeBot2
    stdin_open: true
    tty: true

  # ... claude-bot-3, claude-bot-4
```

## Decision Types

The `wait_for_my_turn` tool returns different decision types:

- **TARGET**: Choose a target (UUID) - creatures, players, etc.
- **SELECT**: Pass priority or take action (boolean)
- **ASK**: Yes/no question (boolean)
- **CHOOSE_ABILITY**: Pick an ability from a list (UUID)
- **CHOOSE_PILE**: Pick a pile of cards (UUID)
- **CHOOSE_CHOICE**: Pick from text options (string)
- **PLAY_MANA**: Choose which mana source to tap (UUID)
- **PLAY_XMANA**: Choose X value (integer)
- **GET_AMOUNT**: Choose a number in range (integer)
- **GET_MULTI_AMOUNT**: Choose multiple numbers (map)

## Game State Format

The game state returned includes:

```
Turn: 3
Phase: POSTCOMBAT_MAIN
Step: MAIN
Priority Player: Alice
Active Player: Bob

=== PLAYERS ===
Alice - Life: 20, Hand: 7, Library: 53, Graveyard: 0
Bob - Life: 18, Hand: 5, Library: 50, Graveyard: 2

=== MY HAND ===
550e8400-e29b-41d4-a716-446655440000: Lightning Bolt
660e8400-e29b-41d4-a716-446655440001: Mountain
...

=== PLAYABLE ===
550e8400-e29b-41d4-a716-446655440000: 1 abilities
660e8400-e29b-41d4-a716-446655440001: 1 abilities
...

=== STACK ===
Giant Growth
...
```

## Auto-Payment

The client automatically enables:
- `MANA_AUTO_PAYMENT_ON` - Auto-tap lands for mana
- `USE_FIRST_MANA_ABILITY_ON` - Use first available mana ability

This reduces the number of decisions needed, letting Claude focus on strategic choices.

## Troubleshooting

### No decisions arriving

Check stderr logs:
```bash
java -jar target/mage-client-headless.jar localhost 17171 ClaudeBot1 2> debug.log
```

### Connection refused

Ensure XMage server is running:
```bash
# In Mage.Server directory
java -jar mage-server.jar
```

### Game not found

Create a game through the XMage GUI client first, then have the headless client join using the game UUID.

## Architecture

```
XMage Server (neutral referee)
       ↑
       | JBoss Remoting RPC
       |
HeadlessClient (Java)
       ↓
   [Game State Queue]
       ↓
MCPServer (stdio)
       ↓
    Claude / Your Orchestrator
```

Each client is independent, "nothing up my sleeve" - they only know what the server tells them.
