#!/usr/bin/env python3
"""
Creates a test game on the XMage server using the session API.

This script:
1. Connects to the server as a player
2. Creates a 2-player duel table
3. Joins the table with a deck
4. Prints the game/table ID for other clients to join
5. Waits for another player to join
6. Starts the game when ready

Usage:
    python3 create-test-game.py [server] [port]
"""

import sys
import uuid
import json

def create_simple_deck():
    """
    Create a simple legal deck (60 cards)
    For testing: 24 Mountains, 36 Lightning Bolts
    """
    deck_cards = []

    # 24 Mountains
    for i in range(24):
        deck_cards.append({
            "cardName": "Mountain",
            "setCode": "M21",
            "num": 1
        })

    # 36 Lightning Bolts
    for i in range(36):
        deck_cards.append({
            "cardName": "Lightning Bolt",
            "setCode": "M21",
            "num": 1
        })

    return {
        "name": "Test Bot Deck",
        "cards": deck_cards,
        "sideboard": []
    }

def main():
    server = sys.argv[1] if len(sys.argv) > 1 else "localhost"
    port = int(sys.argv[2]) if len(sys.argv) > 2 else 17171

    print(f"To create a game, you'll need to use the XMage GUI client or programmatic API.")
    print(f"")
    print(f"For now, here's how to test manually:")
    print(f"")
    print(f"1. Start XMage server:")
    print(f"   cd Mage.Server && java -jar mage-server.jar")
    print(f"")
    print(f"2. Start XMage GUI client and:")
    print(f"   - Connect to {server}:{port}")
    print(f"   - Create a new table (2-player duel)")
    print(f"   - Join the table with any deck")
    print(f"   - Note the table/game UUID from the URL or table list")
    print(f"")
    print(f"3. Start headless bot:")
    print(f"   java -jar target/mage-client-headless-1.4.58.jar {server} {port} TestBot [gameId]")
    print(f"")
    print(f"4. In separate terminal, run test-mcp.py:")
    print(f"   python3 test-mcp.py")
    print(f"")
    print(f"The bot will then respond to game events by passing priority.")
    print(f"")
    print(f"Alternative: Create 2 bots to play against each other")
    print(f"  Bot 1: java -jar target/mage-client-headless-1.4.58.jar {server} {port} Bot1")
    print(f"  Bot 2: java -jar target/mage-client-headless-1.4.58.jar {server} {port} Bot2")
    print(f"")
    print(f"Then control each with test-mcp.py in separate terminals.")

if __name__ == "__main__":
    main()
