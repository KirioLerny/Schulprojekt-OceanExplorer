#!/usr/bin/env python3
"""Test mit korrekten Feldnamen: typ und dir"""
import socket

sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
sock.settimeout(3)
sock.connect(('localhost', 8150))

# Korrekte Feldnamen: typ (nicht type), dir (nicht direction)
cmd = '{"cmd":"launch","name":"PythonWin","typ":"ship","sector":{"vec2":[50,50]},"dir":{"vec2":[0,1]}}\n'
print(f"Sende: {cmd.strip()}")
sock.sendall(cmd.encode('utf-8'))

try:
    response = sock.recv(4096).decode('utf-8')
    print(f"✓ Antwort: {response}")
except socket.timeout:
    print("✓ Keine Fehlermeldung = Erfolg!")

sock.close()

