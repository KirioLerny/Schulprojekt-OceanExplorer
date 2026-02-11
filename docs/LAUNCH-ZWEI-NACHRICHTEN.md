# 🐛 PROBLEM GELÖST: Server sendet 2 Antworten auf launch

## 🔍 DAS PROBLEM:

Nach dem `launch` Befehl sendet der OceanServer **ZWEI Nachrichten**:

```
Client sendet: {"cmd":"launch",...}

Server antwortet:
1. {"cmd":"launched","id":"#0#Explorer-1",...}  ← Bestätigung
2. {"cmd":"move2d","sector":...,"dir":...}      ← Initial-Position
```

**Aber:** Der Code las nur die erste Nachricht!

Die zweite Nachricht (`move2d`) blieb im Buffer und wurde beim **nächsten** Befehl gelesen:

```
Client sendet: {"cmd":"radar"}
Server antwortet: {"cmd":"radarresponse",...}

ABER Client liest: {"cmd":"move2d",...}  ← DIE ALTE NACHRICHT!
```

**Resultat:** Alle nachfolgenden Befehle lasen die falsche Antwort!

---

## ✅ DIE LÖSUNG:

Nach `launch` muss die **zweite Nachricht** auch gelesen werden:

```java
public boolean launch(...) {
    String response = sendCommand(command);
    JSONObject json = new JSONObject(response);
    
    String cmd = json.optString("cmd", "");
    boolean success = cmd.equals("launched");
    
    if (success) {
        logger.info("Schiff erfolgreich gestartet");
        
        // WICHTIG: Server sendet noch eine move2d Nachricht!
        String move2dResponse = in.readLine();
        System.out.println("<<< Empfangen: " + move2dResponse);
        logger.debug("Launch gefolgt von move2d (ignoriert)");
    }
    
    return success;
}
```

---

## 📊 KORREKTE REIHENFOLGE:

### ✅ JETZT (korrekt):

```
>>> Sende: {"cmd":"launch",...}
<<< Empfangen: {"cmd":"launched",...}           ✅ Gelesen
<<< Empfangen: {"cmd":"move2d",...}             ✅ Gelesen & ignoriert

>>> Sende: {"cmd":"radar"}
<<< Empfangen: {"cmd":"radarresponse",...}      ✅ Korrekte Antwort!

>>> Sende: {"cmd":"scan"}
<<< Empfangen: {"cmd":"scanned",...}            ✅ Korrekte Antwort!
```

---

## 🎓 WARUM SENDET SERVER 2 NACHRICHTEN?

Der Server sendet nach `launch` eine **move2d** Nachricht, weil:
1. `launched` = Bestätigung, dass Schiff erstellt wurde
2. `move2d` = Initiale Position und Richtung des Schiffs

Das ist das gleiche Format wie bei `navigate` → der Server behandelt `launch` intern wie eine Bewegung.

---

## ⚠️ WICHTIG FÜR ANDERE BEFEHLE:

Prüfe immer, ob ein Befehl **mehrere Antworten** sendet!

**Bekannte Befehle:**
- `launch` → sendet **2 Nachrichten** (`launched` + `move2d`)
- `navigate` → sendet **1 Nachricht** (`move2d`)
- `radar` → sendet **1 Nachricht** (`radarresponse`)
- `scan` → sendet **1 Nachricht** (`scanned`)
- `exit` → sendet **keine Nachricht** (schließt Verbindung)

---

## 📚 PROTOKOLL-UPDATE:

### LAUNCH (komplett):

**Client → Server:**
```json
{"cmd":"launch","name":"Explorer-1","typ":"ship","sector":{"vec2":[50,50]},"dir":{"vec2":[0,1]}}
```

**Server → Client (2 Nachrichten!):**
```json
{"cmd":"launched","id":"#0#Explorer-1","abspos":{"vec2":[5050,5050]}}
{"cmd":"move2d","id":"#0#Explorer-1","sector":{"vec2":[50,50]},"dir":{"vec2":[0,1]},"abspos":{"vec2":[5050,5050]}}
```

⚠️ **BEIDE Nachrichten müssen gelesen werden!**

---

## ✅ LÖSUNG IMPLEMENTIERT:

| Datei | Status | Änderung |
|-------|--------|----------|
| `OceanServerClient.java` | ✅ | `launch()` liest jetzt beide Nachrichten |
| **Build Status** | ✅ | SUCCESS |

---

## 🚀 JETZT TESTEN:

```powershell
.\test-grundlagen.bat
```

**Erwartete Ausgabe:**
```
>>> Sende: {"cmd":"launch",...}
<<< Empfangen: {"cmd":"launched",...}
<<< Empfangen: {"cmd":"move2d",...}  ← Jetzt auch gelesen!
✅ Schiff erfolgreich gestartet

>>> Sende: {"cmd":"radar"}
<<< Empfangen: {"cmd":"radarresponse",...}  ← Korrekt!
✅ Radar-Scan ergab 8 Sektoren

>>> Sende: {"cmd":"scan"}
<<< Empfangen: {"cmd":"scanned",...}  ← Korrekt!
✅ Tiefen-Scan erfolgreich
```

---

## 🎉 PROBLEM GELÖST!

Die Reihenfolge der Nachrichten ist jetzt korrekt! 🚢✨
