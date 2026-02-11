# 🚀 QUICK-START: Grundlagen testen

## ✅ PORTS KONFIGURIERT: 8150/8151

---

## 📋 ABLAUF (3 Schritte):

### **1. OceanServer starten**
Öffne ein **PowerShell-Fenster**:
```powershell
cd C:\Users\DOPAMINKISTE-v2\IdeaProjects\Schulprojekt-OceanExplorer\external
java -jar oceanserver.jar
```

Im GUI:
- **WICHTIG:** Ports sind jetzt in `oceanserver.conf` fest auf **8150/8151** gesetzt
- Einfach auf **"Start"** klicken
- Status sollte zeigen: "Ship Server running on port 8150"

---

### **2. ShipApp starten**
In einem **neuen PowerShell-Fenster**:
```powershell
cd C:\Users\DOPAMINKISTE-v2\IdeaProjects\Schulprojekt-OceanExplorer
mvn exec:java -Dexec.mainClass="ocean.Main"
```

Oder verwende das Batch-Skript:
```powershell
.\test-grundlagen.bat
```

---

### **3. Erwartete Ausgabe**
```
14:04:27 INFO Main - === Ocean Explorer - ShipApp gestartet ===
14:04:27 INFO Main - Verbinde zu OceanServer:
14:04:27 INFO Main -   - Ship Port: 8150
14:04:27 INFO Main -   - Submarine Port: 8151
[DEBUG] Verbinde mit OceanServer localhost:8150
[INFO] Verbunden mit OceanServer: localhost:8150
[INFO] Schiff gestartet: Ship{name='Explorer-1', position=(50,50), direction=(0,1)}
[INFO] Radar-Scan ergab 8 Sektoren:
[INFO]   - (49,51,Water,0)
[INFO]   - (50,51,Water,0)
[INFO]   - ...
[INFO] Tiefen-Scan: ScanResult{...}
[INFO] === Phase 1 Test erfolgreich! ===
[INFO] === ShipApp beendet ===
```

---

## ❌ FEHLERSUCHE

### Problem: "Connection refused: connect"
**Ursache:** OceanServer läuft nicht oder falsche Ports

**Lösung:**
1. Prüfe ob OceanServer-GUI offen ist
2. Klicke auf **"Start"** im GUI
3. Prüfe Status-Meldung: "Ship Server running on port 8150"
4. Wenn andere Ports angezeigt werden:
   - Starte OceanServer NEU (schließen und neu starten)
   - Die `oceanserver.conf` wird beim Start gelesen

### Problem: OceanServer zeigt andere Ports
**Lösung:**
```powershell
# OceanServer schließen, dann:
cd external
notepad oceanserver.conf
# Prüfe: shipport = 8150, submarineport = 8151
# Speichern, dann OceanServer neu starten
```

### Problem: Ports bereits belegt
**Lösung:**
```powershell
# Prüfe welcher Prozess die Ports nutzt:
netstat -ano | findstr "8150"
netstat -ano | findstr "8151"
# Falls belegt → anderen Port in Config wählen
```

---

## 🔍 NETZWERK-TEST

Prüfe ob Port 8150 erreichbar ist:
```powershell
Test-NetConnection -ComputerName localhost -Port 8150
```

Sollte zeigen: `TcpTestSucceeded : True`

---

## 📊 PORTS-ÜBERSICHT

| Port | Service | Verwendung |
|------|---------|------------|
| **8150** | Ship Server | ShipApp → OceanServer (TCP/JSON) |
| **8151** | Submarine Server | Submarine → OceanServer (TCP/JSON) |

---

## ✅ ERFOLG = PHASE 1 ABGESCHLOSSEN!

Wenn du die Meldung siehst:
```
[INFO] === Phase 1 Test erfolgreich! ===
```

Dann ist **Phase 1 (Grundlagen) komplett** und du bist bereit für:
→ **Phase 2: Navigation** (autonome Schiffssteuerung)

---

**LOS GEHT'S! 🚢**
