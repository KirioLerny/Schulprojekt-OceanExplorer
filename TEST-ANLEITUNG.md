# 🧪 TEST-ANLEITUNG: Grundlagen testen

## ✅ Status: Projekt kompiliert erfolgreich!

## 🔌 WICHTIG: FESTE PORTS

Der OceanServer verwendet **feste Ports**:
- **Ship Port:** `8150`
- **Submarine Port:** `8151`

Diese sind jetzt in `oceanserver.conf` und `Main.java` fest eingestellt!

---

## 📋 SCHRITT-FÜR-SCHRITT ANLEITUNG

### **Schritt 1: OceanServer starten**

1. Öffne ein **neues Terminal/PowerShell-Fenster**
2. Navigiere zum Projekt:
   ```powershell
   cd C:\Users\DOPAMINKISTE-v2\IdeaProjects\Schulprojekt-OceanExplorer\external
   ```
3. Starte den OceanServer:
   ```powershell
   java -jar oceanserver.jar
   ```
4. Ein GUI-Fenster öffnet sich mit dem Titel "Ocean Server"

---

### **Schritt 2: OceanServer konfigurieren**

Im OceanServer GUI:
1. Die Ports sind **bereits in oceanserver.conf gesetzt:**
   - Ship Port: **8150** (fest)
   - Submarine Port: **8151** (fest)
2. Klicke einfach auf **"Start"**
3. Status sollte zeigen:
   - "Ship Server running on port 8150"
   - "Submarine Server running on port 8151"

---

### **Schritt 3: ShipApp testen**

**Option A: Mit dem Batch-Skript (EINFACH) ⭐**
```powershell
cd C:\Users\DOPAMINKISTE-v2\IdeaProjects\Schulprojekt-OceanExplorer
.\test-grundlagen.bat
```
**Keine Port-Eingabe nötig!** Die App verbindet sich automatisch zu Port 8050.

**Option B: Direkt mit Maven**
```powershell
cd C:\Users\DOPAMINKISTE-v2\IdeaProjects\Schulprojekt-OceanExplorer
mvn exec:java -Dexec.mainClass="ocean.Main"
```

**Option C: Kompiliertes JAR ausführen**
```powershell
cd C:\Users\DOPAMINKISTE-v2\IdeaProjects\Schulprojekt-OceanExplorer
mvn package
java -jar target\OceanExplorer-1.0-SNAPSHOT.jar
```

---

### **Schritt 4: Was sollte passieren**

Die ShipApp sollte folgende Ausgabe zeigen:

```
=== Ocean Explorer - ShipApp gestartet ===
OceanServer Port eingeben: 8150
[INFO] Verbunden mit OceanServer: localhost:8150
[INFO] Schiff gestartet: Ship{name='Explorer-1', position=(50,50), direction=(0,1)}
[INFO] Radar-Scan ergab 8 Sektoren:
[INFO]   - (50,51,Water,0)
[INFO]   - (51,51,Water,0)
[INFO]   - ...
[INFO] Tiefen-Scan: ScanResult{sector=(50,50), avgDepth=-2500, stdDev=123.45}
[INFO] === Phase 1 Test erfolgreich! ===
[INFO] === ShipApp beendet ===
```

---

### **Schritt 5: Im OceanServer GUI prüfen**

Im OceanServer-Fenster solltest du sehen:
- ✅ Ein Schiff namens "Explorer-1" auf Position (50, 50)
- ✅ Das Schiff ist auf der Karte sichtbar
- ✅ Log-Einträge über empfangene Befehle (launch, radar, scan)

---

## 🔍 WAS WIRD GETESTET?

### ✅ Test 1: Verbindung
- TCP-Verbindung zum OceanServer aufbauen
- JSON-Protokoll verwenden

### ✅ Test 2: Schiff starten (launch)
- Schiff "Explorer-1" erstellen
- Position: (50, 50) - Mitte des Ozeans
- Richtung: (0, 1) - Nord

### ✅ Test 3: Radar-Scan
- 8 umliegende Sektoren scannen
- Ground-Typ erkennen (Water/Land)
- Höhen auswerten

### ✅ Test 4: Tiefen-Scan
- Meerestiefe messen
- Durchschnittswert erhalten
- Standardabweichung erhalten

---

## ❌ FEHLERSUCHE

### Problem: "Connection refused"
**Lösung:**
- Prüfe, ob OceanServer läuft
- Prüfe, ob "Start" geklickt wurde
- Prüfe, ob der Port korrekt ist (3000)

### Problem: "Schiff konnte nicht gestartet werden"
**Lösung:**
- Prüfe, ob die Position (50, 50) frei ist
- Starte OceanServer neu
- Prüfe, ob ein anderes Schiff bereits läuft

### Problem: "Compilation error"
**Lösung:**
```powershell
mvn clean compile
```

### Problem: Java nicht gefunden
**Lösung:**
```powershell
java -version
```
Sollte "java version 21" oder höher anzeigen

---

## 📊 ERWARTETES ERGEBNIS

### ✅ ERFOLGREICH wenn:
- [x] ShipApp verbindet sich zum OceanServer
- [x] Schiff wird gestartet
- [x] Radar liefert 8 Sektoren
- [x] Scan liefert Tiefendaten
- [x] Keine Exceptions
- [x] Sauberes Beenden

### 🎉 BEDEUTUNG:
**Phase 1 (Grundlagen) ist KOMPLETT!**
- OceanServerClient funktioniert
- Alle Model-Klassen funktionieren
- Kommunikation stabil
- Bereit für Phase 2: Navigation

---

## 🚀 NÄCHSTE SCHRITTE

Nach erfolgreichem Test:
1. ✅ Grundlagen bestätigt
2. → Phase 2: Navigation implementieren
3. → Schiff automatisch bewegen
4. → Mehrere Sektoren erkunden

---

## 💡 TIPPS

- **Port merken:** Der Ship-Port (z.B. 3000) wird immer wieder gebraucht
- **OceanServer laufen lassen:** Für weitere Tests nicht schließen
- **Logs lesen:** Die Log-Ausgaben helfen bei der Fehlersuche
- **Config-Datei:** `external/oceanserver.conf` kann Ports fest einstellen

---

**Bereit? Los geht's! 🚢**
