#!/usr/bin/env python3
"""
Simple test script to demonstrate MCP protocol interaction with headless client.

This script connects to the headless client via stdin/stdout and demonstrates
the long-polling pattern for game decisions.

Usage:
    # In one terminal, start the headless client
    java -jar target/mage-client-headless.jar localhost 17171 TestBot

    # In another terminal, run this script (piping to the first terminal)
    # Or test manually by piping stdin/stdout
"""

import json
import sys
import subprocess
import time

def send_request(process, method, params=None):
    """Send a JSON-RPC request to the MCP server"""
    request = {
        "jsonrpc": "2.0",
        "id": int(time.time() * 1000),
        "method": method,
        "params": params or {}
    }

    request_json = json.dumps(request)
    print(f">>> Sending: {request_json}", file=sys.stderr)

    process.stdin.write(request_json + "\n")
    process.stdin.flush()

    # Read response
    response_line = process.stdout.readline()
    print(f"<<< Received: {response_line}", file=sys.stderr)

    return json.loads(response_line)

def main():
    """Simple bot that always passes priority / says no"""

    # For manual testing, you can just pipe stdin/stdout
    # Here's an example that spawns the process

    print("Starting headless client...", file=sys.stderr)
    process = subprocess.Popen(
        ["java", "-jar", "target/mage-client-headless.jar", "localhost", "17171", "TestBot"],
        stdin=subprocess.PIPE,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
        bufsize=1
    )

    # Give it a moment to connect
    time.sleep(3)

    print("Initializing MCP protocol...", file=sys.stderr)
    response = send_request(process, "initialize")
    print(f"Initialized: {response}", file=sys.stderr)

    print("Listing tools...", file=sys.stderr)
    response = send_request(process, "tools/list")
    print(f"Tools: {json.dumps(response, indent=2)}", file=sys.stderr)

    print("\nWaiting for game to start and decisions to be needed...\n", file=sys.stderr)

    turn = 0
    while True:
        try:
            turn += 1
            print(f"\n=== Turn {turn} ===", file=sys.stderr)

            # Wait for decision (blocking)
            print("Calling wait_for_my_turn (blocking)...", file=sys.stderr)
            response = send_request(process, "tools/call", {
                "name": "wait_for_my_turn",
                "arguments": {}
            })

            decision_text = response["result"]["content"][0]["text"]
            print(f"\nDecision needed:\n{decision_text}\n", file=sys.stderr)

            # Simple strategy: always pass priority / say no
            # In a real implementation, you'd parse decision_text and use Claude to decide

            if "TARGET" in decision_text or "CHOOSE" in decision_text:
                # For now, just submit a dummy UUID (will likely fail)
                print("Submitting boolean decision: false (pass)", file=sys.stderr)
                response = send_request(process, "tools/call", {
                    "name": "submit_boolean_decision",
                    "arguments": {"value": False}
                })
            else:
                # Default to passing priority
                print("Submitting boolean decision: false (pass)", file=sys.stderr)
                response = send_request(process, "tools/call", {
                    "name": "submit_boolean_decision",
                    "arguments": {"value": False}
                })

            print(f"Submitted: {response}", file=sys.stderr)

        except KeyboardInterrupt:
            print("\nShutting down...", file=sys.stderr)
            process.terminate()
            break
        except Exception as e:
            print(f"Error: {e}", file=sys.stderr)
            time.sleep(1)

if __name__ == "__main__":
    main()
