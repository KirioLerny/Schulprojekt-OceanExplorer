# 🎯 OCEANSERVER KOMMUNIKATIONS-PROTOKOLL

## 📡 TCP/JSON Kommunikation

Der OceanServer nutzt **Socket-basierte JSON-Kommunikation** über TCP.

---

## 🔌 VERBINDUNGSAUFBAU

### Ports:
- **Ship Port:** `8150`
- **Submarine Port:** `8151`

### Connect-Vorgang:
```java
Socket socket = new Socket("localhost", 8150);
PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
```

⚠️ **WICHTIG:** Der Server sendet **KEINE** Config-Nachricht beim Connect!  
→ Kein `readLine()` nach Connect ausführen, sonst blockiert das Programm!

---

## 📤 BEFEHLE SENDEN

Alle Befehle sind **JSON-Strings** mit abschließendem `\n`:

### Format:
```
{"cmd":"befehlsname", ...parameter...}\n
```

### Beispiele:

#### 1. LAUNCH (Schiff starten)
```json
{
  "cmd": "launch",
  "name": "Explorer-1",
  "typ": "ship",
  "sector": {"vec2": [50, 50]},
  "dir": {"vec2": [0, 1]}
}
```

**Antwort:**
```json
{
  "cmd": "launched",
  "id": "#1#Explorer-1",
  "abspos": {"vec2": [5050, 5050]}
}
```

⚠️ **WICHTIG:** Server antwortet mit `"cmd":"launched"` (NICHT `"status":"ok"`!)

Bei Fehler:
```json
{"error": "Sektor bereits belegt"}
```

---

#### 2. RADAR (8 umliegende Sektoren scannen)
```json
{"cmd": "radar"}
```

**Antwort:**
```json
{
  "cmd": "radarresponse",
  "id": "#1#Explorer-1",
  "echos": [
    {"sector": {"vec2": [49, 51]}, "ground": "WATER", "height": 0},
    {"sector": {"vec2": [50, 51]}, "ground": "WATER", "height": 0},
    {"sector": {"vec2": [51, 51]}, "ground": "LAND", "height": 150}
  ]
}
```

⚠️ **WICHTIG:** Feld heißt `"echos"` (nicht `"echoes"`!)

**Ground-Typen:** `WATER`, `LAND`, `NONE` (außerhalb Karte)  
**Height > 0:** Sektor blockiert (nicht befahrbar)

---

#### 3. SCAN (Tiefenmessung)
```json
{"cmd": "scan"}
```

**Antwort:**
```json
{
  "cmd": "scanned",
  "id": "#1#Explorer-1",
  "depth": -2500,
  "stddev": 123.45
}
```

⚠️ **WICHTIG:** 
- Feld heißt `"stddev"` (nicht `"deviation"`!)
- `depth` ist negativ bei Wasser (Tiefe unter Meeresspiegel)
- Server sendet KEINEN `sector` Parameter (Sektor aus aktuellem Kontext)

---

#### 4. MOVE (Schiff bewegen)
```json
{
  "cmd": "navigate",
  "rudder": "Center",
  "course": "Forward"
}
```

**Rudder:** `Left`, `Center`, `Right`  
**Course:** `Forward`, `Backward`

**Antwort:**
```json
{
  "cmd": "move2d",
  "id": "#1#Explorer-1",
  "sector": {"vec2": [50, 51]},
  "dir": {"vec2": [0, 1]},
  "abspos": {"vec2": [5051, 5050]}
}
```

⚠️ **WICHTIG:** Feld heißt `"dir"` (nicht `"direction"`!)

---

#### 5. EXIT (Verbindung trennen)
```json
{"cmd": "exit"}
```

**Antwort:** Keine (Server schließt Verbindung)

---

## 📥 ANTWORTEN EMPFANGEN

Nach **JEDEM Befehl** sendet der Server eine Antwort:

```java
String command = "{\"cmd\":\"radar\"}\n";
out.print(command);
out.flush();

String response = in.readLine();  // Blockiert bis Antwort kommt
JSONObject json = new JSONObject(response);
```

⚠️ **WICHTIG:** Immer auf Antwort warten nach Befehl!

---

## 🎯 BEST PRACTICES

### ✅ DO:
- Immer `\n` am Ende der Befehle
- Immer auf Antwort warten nach Befehl
- `status` in Antwort prüfen
- Timeouts setzen (optional, für Robustheit)

### ❌ DON'T:
- Kein `readLine()` nach Connect ohne Befehl
- Keine Befehle ohne Antwort abzuwarten
- Keine fehlenden `\n` am Ende

---

## 🔄 KOMMUNIKATIONS-FLOW

```
Client                          Server
  |                               |
  |--- Socket Connect ----------->|
  |                               |
  |--- {"cmd":"launch",...}\n --->|
  |<-- {"status":"ok"} -----------|
  |                               |
  |--- {"cmd":"radar"}\n -------->|
  |<-- {"echoes":[...]} ----------|
  |                               |
  |--- {"cmd":"scan"}\n --------->|
  |<-- {"sector":...} ------------|
  |                               |
  |--- {"cmd":"move",...}\n ----->|
  |<-- {"status":"ok",...} -------|
  |                               |
  |--- {"cmd":"exit"}\n --------->|
  |    Socket Close               |
```

---

## 🐛 HÄUFIGE FEHLER

### 1. Programm hängt nach Connect
**Ursache:** `readLine()` wartet auf Config-Message, die nie kommt  
**Lösung:** Kein `readLine()` direkt nach Connect!

### 2. "Connection refused"
**Ursache:** OceanServer läuft nicht oder falsche Ports  
**Lösung:** Server starten, "Start" klicken, Ports prüfen

### 3. Keine Antwort auf Befehle
**Ursache:** Fehlendes `\n` am Ende oder falsches JSON  
**Lösung:** JSON validieren, `\n` hinzufügen

### 4. "status": "error"
**Ursache:** Ungültige Parameter (z.B. Sektor belegt, falsche Koordinaten)  
**Lösung:** Parameter prüfen, Fehlermeldung lesen

---

## 📚 VERWENDUNG IM CODE

### OceanServerClient.java
```java
// Connect (OHNE Config-Read!)
socket = new Socket(host, port);
out = new PrintWriter(socket.getOutputStream(), true);
in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

// Befehl senden
String command = CommandFactory.launch(name, type, sector, dir);
out.print(command);
out.flush();

// Antwort empfangen
String response = in.readLine();
JSONObject json = new JSONObject(response);

// Status prüfen
if (json.optString("status").equals("ok")) {
    // Erfolg
}
```

---

## 🎓 SUBMARINE-PROTOKOLL (für später)

Submarines verbinden sich zu Port **8151** und nutzen ein **ähnliches** Protokoll:

### Submarine → Server:
- `ready` - Submarine ist bereit
- `measure` - 3D-Messpunkte
- `picture` - PNG als Hex
- `crash` - Unfall
- `arise` - Auftauchen

### Server → Submarine:
- `pilot` - Route mit Aktionen

**Details folgen in Phase 4!**

---

**Stand:** Phase 1 abgeschlossen ✅  
**Nächste Phase:** Navigation (automatische Schiffssteuerung)
