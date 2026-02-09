#!/usr/bin/env python3
"""
Einfacher Test-Client für OceanServer
Zeigt genau was gesendet und empfangen wird
"""
import socket
import json
import sys
import time

def test_oceanserver(host='localhost', port=8150):
    print(f"Verbinde zu {host}:{port}...")

    try:
        sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        sock.settimeout(5)
        sock.connect((host, port))
        print("✓ Verbindung hergestellt!")

        # Warte kurz
        time.sleep(0.5)

        # Prüfe ob Server etwas sendet
        try:
            sock.settimeout(1)
            initial = sock.recv(4096).decode('utf-8')
            print(f"Server sendet initial: {initial}")
        except socket.timeout:
            print("Server sendet keine initiale Nachricht")

        # Sende Launch-Befehl
        launch_cmd = {
            "cmd": "launch",
            "name": "PythonTest",
            "type": "ship",
            "sector": {"vec2": [50, 50]},
            "direction": {"vec2": [0, 1]}
        }

        cmd_str = json.dumps(launch_cmd) + '\n'
        print(f"\n>>> Sende:\n{cmd_str}")
        sock.sendall(cmd_str.encode('utf-8'))

        # Warte auf Antwort
        sock.settimeout(5)
        response = sock.recv(4096).decode('utf-8')
        print(f"\n<<< Empfangen:\n{response}")

        if response:
            try:
                resp_json = json.loads(response)
                print(f"\nParsed JSON: {json.dumps(resp_json, indent=2)}")
            except:
                print("Keine gültige JSON-Antwort")

        sock.close()

    except Exception as e:
        print(f"✗ Fehler: {e}")
        return False

    return True

if __name__ == '__main__':
    test_oceanserver()

