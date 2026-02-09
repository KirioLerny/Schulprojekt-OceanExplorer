#!/usr/bin/env python3
"""Test ob Server bestimmtes Format oder Header erwartet"""
import socket
import time

def send_raw(data_str):
    print(f"\n{'='*60}")
    print(f"Sende (mit Repr): {repr(data_str)}")
    print(f"{'='*60}")

    try:
        sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        sock.settimeout(3)
        sock.connect(('localhost', 8150))

        # Sende genau so wie angegeben
        sock.sendall(data_str.encode('utf-8'))

        time.sleep(1)
        response = sock.recv(4096).decode('utf-8')
        print(f"Antwort: {response}")

        sock.close()

    except Exception as e:
        print(f"Fehler: {e}")

# Test 1: Mit \r\n statt \n
send_raw('{"cmd":"launch","name":"T1","type":"ship","sector":{"vec2":[50,50]},"direction":{"vec2":[0,1]}}\r\n')

# Test 2: Ohne Zeilenumbruch
send_raw('{"cmd":"launch","name":"T2","type":"ship","sector":{"vec2":[50,50]},"direction":{"vec2":[0,1]}}')

# Test 3: Doppelter Zeilenumbruch
send_raw('{"cmd":"launch","name":"T3","type":"ship","sector":{"vec2":[50,50]},"direction":{"vec2":[0,1]}}\n\n')

# Test 4: Mit Leerzeichen
send_raw('{"cmd": "launch", "name": "T4", "type": "ship", "sector": {"vec2": [50, 50]}, "direction": {"vec2": [0, 1]}}\n')

