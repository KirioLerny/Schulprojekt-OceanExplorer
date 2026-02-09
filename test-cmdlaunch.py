#!/usr/bin/env python3
"""Test mit CmdLaunch statt launch"""
import socket
import time

sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
sock.settimeout(2)  # 2 Sekunden Timeout
sock.connect(('localhost', 8150))

# Test: CmdLaunch als cmd-Wert
cmd = '{"cmd":"launch","name":"Test","typ":"ship","sector":{"vec2":[50,50]},"dir":{"vec2":[0,1]}}\n'
print(f"Sende: {cmd.strip()}")
sock.sendall(cmd.encode('utf-8'))

try:
    response = sock.recv(4096).decode('utf-8')
    print(f"Antwort: {response}")
except socket.timeout:
    print("Timeout - keine Antwort vom Server (das könnte gut sein!)")

sock.close()

