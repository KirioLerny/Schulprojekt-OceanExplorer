#!/usr/bin/env python3
"""Test verschiedene JSON-Varianten"""
import socket
import json
import time

def test_variant(cmd_dict, description):
    print(f"\n{'='*60}")
    print(f"Test: {description}")
    print(f"{'='*60}")

    try:
        sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        sock.settimeout(3)
        sock.connect(('localhost', 8150))

        cmd_str = json.dumps(cmd_dict, separators=(',', ':')) + '\n'
        print(f"Sende: {cmd_str.strip()}")
        sock.sendall(cmd_str.encode('utf-8'))

        time.sleep(0.5)
        response = sock.recv(4096).decode('utf-8')
        print(f"Antwort: {response.strip()}")

        sock.close()
        return response

    except Exception as e:
        print(f"Fehler: {e}")
        return None

# Test 1: Minimales Format
test_variant(
    {"cmd": "launch"},
    "Nur cmd"
)

# Test 2: Mit command statt cmd
test_variant(
    {"command": "launch", "name": "Test", "type": "ship", "sector": {"vec2": [50, 50]}, "direction": {"vec2": [0, 1]}},
    "command statt cmd"
)

# Test 3: Sektor als Array
test_variant(
    {"cmd": "launch", "name": "Test", "type": "ship", "sector": [50, 50], "direction": [0, 1]},
    "sector und direction als einfache Arrays"
)

# Test 4: Mit expliziter Reihenfolge
from collections import OrderedDict
cmd = OrderedDict([
    ("cmd", "launch"),
    ("name", "Test"),
    ("type", "ship"),
    ("sector", {"vec2": [50, 50]}),
    ("direction", {"vec2": [0, 1]})
])
test_variant(cmd, "OrderedDict mit fester Reihenfolge")

