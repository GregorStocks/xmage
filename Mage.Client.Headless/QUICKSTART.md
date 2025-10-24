# Quick Start - Testing the Headless Client

## Current Status (2025-10-23)

✅ **FULLY WORKING - COMPLETE END-TO-END TEST:**
- Server starts with JVM flags for Java 9+ compatibility
- Client connects successfully to server
- Game table creation works
- Deck card lookups work with correct format: DeckCardInfo(name, number, set)
- Both bots join table successfully
- Match starts and game initializes
- Auto-pass bot handles all decision types:
  - TARGET (selects first available target)
  - ASK (always says no/pass)
  - SELECT (always passes priority)
  - CHOOSE_ABILITY, CHOOSE_PILE, PLAY_MANA (picks first UUID option)
  - PLAY_XMANA, GET_AMOUNT (always chooses 0)
  - CHOOSE_CHOICE (submits empty string)
- **Game plays through to completion (winner determined)**
- Both bots disconnect cleanly

🎯 **READY FOR MCP INTEGRATION:**
The headless client infrastructure is complete and tested. Next step: integrate with Claude via MCP protocol.

---

# Quick Start - Testing the Headless Client

## Problem You Hit

When you tried:
```bash
java -jar target/mage-server.jar -testMode=true
```

You got:
```
Error: Unable to initialize main class mage.server.Main
Caused by: java.lang.NoClassDefFoundError: org/jboss/remoting/transporter/TransporterServer
```

**Why?** The `mage-server.jar` doesn't include dependencies. You need to run via Maven to get the proper classpath.

## Solution: Run Server via Maven

### Terminal 1: Start XMage Server

```bash
cd ~/code/xmage
chmod +x start-test-server.sh
./start-test-server.sh
```

Or manually:
```bash
cd ~/code/xmage/Mage.Server
mvn exec:java -Dexec.mainClass="mage.server.Main" -Dexec.args="-testMode=true"
```

**Wait for:**
```
INFO Started MAGE server - listening on ...
```

This takes about 30 seconds on first run (loads all 87,000+ cards into database).

### Terminal 2: Run Simple Test

```bash
cd ~/code/xmage/Mage.Client.Headless
./test-simple.sh
```

This will:
1. ✅ Connect Bot1 and Bot2 to server
2. ✅ Create a 2-player duel
3. ✅ Both bots join with simple decks
4. ✅ Start the match
5. ✅ Play until someone wins (both auto-passing)

## What You Should See

**Terminal 1 (Server):**
```
INFO Starting MAGE SERVER version: 1.4.58
INFO Loading cards...
INFO Done.
INFO Started MAGE server - listening on 0.0.0.0:17171
```

**Terminal 2 (Bots):**
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

TestBot1 received decision: ASK
TestBot2 received decision: ASK
...
```

The game will play out automatically!

## Files You Need

All built and ready:
- ✅ `/Users/gregorstocks/code/xmage/start-test-server.sh` - Server launcher
- ✅ `/Users/gregorstocks/code/xmage/Mage.Client.Headless/target/mage-client-headless-1.4.58.jar` - Headless client
- ✅ `/Users/gregorstocks/code/xmage/Mage.Client.Headless/test-simple.sh` - Test script

## Troubleshooting

### "Server not running"
- Make sure Terminal 1 shows "Started MAGE server - listening"
- First startup takes 30+ seconds to load cards
- Check: `netstat -an | grep 17171` should show LISTEN

### "Connection refused"
- Wait for server to fully start
- Check server logs for errors
- Try: `telnet localhost 17171`

### "Failed to create table"
- Server must be in test mode (`-testMode=true`)
- Check server config at `Mage.Server/config/config.xml`

## Next Steps

Once `test-simple.sh` works:

1. **Test MCP protocol manually** - See `TESTING.md` for details
2. **Integrate with Claude** - Build Python orchestrator
3. **4-player Commander** - Scale up to 4 Claude bots
4. **Docker deployment** - Use `docker-compose.yml`

---

**TL;DR:**
```bash
# Terminal 1
cd ~/code/xmage
./start-test-server.sh

# Wait 30 seconds for "Started MAGE server"

# Terminal 2
cd ~/code/xmage/Mage.Client.Headless
./test-simple.sh
```

That's it! 🎉
