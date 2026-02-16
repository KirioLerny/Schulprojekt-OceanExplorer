# 🚢 PHASE 2: NAVIGATION - TEST-ANLEITUNG

## ✅ VORAUSSETZUNG
Phase 1 muss erfolgreich funktionieren!

---

## 📋 ABLAUF (2 Schritte):

### **1. OceanServer starten**
Öffne ein **PowerShell-Fenster**:
```powershell
cd C:\Users\DOPAMINKISTE-v2\IdeaProjects\Schulprojekt-OceanExplorer\external
java -jar oceanserver.jar
```

Im GUI:
- Ports sind auf **8150/8151** gesetzt
- Klicke auf **"Start"**
- Status sollte zeigen: "Ship Server running on port 8150"

---

### **2. ShipApp mit Navigation starten**
In einem **neuen PowerShell-Fenster**:
```powershell
cd C:\Users\DOPAMINKISTE-v2\IdeaProjects\Schulprojekt-OceanExplorer
.\test-navigation.bat
```

Oder direkt mit Maven:
```powershell
mvn exec:java -Dexec.mainClass="ocean.Main"
```

---

## 🎯 WAS PASSIERT

Die ShipApp wird jetzt:
1. ✅ Schiff starten (wie Phase 1)
2. ✅ Ersten Sektor scannen
3. ✅ **Radar verwenden** um Kollisionen zu vermeiden
4. ✅ **Automatisch 10 Sektoren abfahren**
5. ✅ Jeden Sektor scannen
6. ✅ Finale Position ausgeben

---

## 📊 ERWARTETE AUSGABE

```
=== Ocean Explorer - ShipApp gestartet ===
[INFO] Verbunden mit OceanServer: localhost:8150
[INFO] Schiff gestartet: Ship{name='Explorer-1', position=(50,50), direction=(0,1)}
[INFO] Radar-Scan ergab 8 Sektoren
[INFO] Tiefen-Scan: depth=-2500, stddev=123.45

=== Phase 1 Test erfolgreich! ===

=== Phase 2: Starte autonome Navigation ===
[INFO] === Starte autonome Navigation ===
[INFO] Ziel: 10 Sektoren erkunden
[INFO] Scanne Sektor (50,50)...
[INFO]   → Tiefe: -2500 m, StdDev: 123.45
[INFO] Fortschritt: 1/10 Sektoren gescannt
[DEBUG] Führe Radar-Scan durch...
[DEBUG] Radar-Analyse: Center=true, Right=true, Left=true
[DEBUG] Wähle: Center (geradeaus)
[DEBUG] Navigiere: rudder=Center, course=Forward
[INFO] Neue Position: (50,51) (Richtung: (0,1))
[INFO] Scanne Sektor (50,51)...
[INFO]   → Tiefe: -2600 m, StdDev: 98.23
[INFO] Fortschritt: 2/10 Sektoren gescannt
...
[INFO] === Navigation abgeschlossen ===
[INFO] Insgesamt 10 Sektoren erforscht
[INFO] Finale Position: Ship{name='Explorer-1', position=(50,60), direction=(0,1)}

=== Phase 2 Test erfolgreich! ===
```

---

## ✅ ERFOLG WENN

- [x] Schiff fährt automatisch (ohne manuelle Steuerung)
- [x] Mindestens 10 Sektoren werden gescannt
- [x] Radar wird vor jeder Bewegung genutzt
- [x] Keine Kollisionen
- [x] Sauberer Ablauf ohne Exceptions
- [x] Meldung "Phase 2 Test erfolgreich!"

---

## 🔍 WAS IST NEU?

### NavigationController
- Autonome Steuerung des Schiffs
- Systematisches Abfahren von Sektoren
- Tracking besuchter Positionen

### CollisionAvoidance
- Radar-Daten analysieren
- Sichere Richtung wählen
- Hindernissen ausweichen

### Navigation-Kommando
- `navigate(Rudder, Course)` implementiert
- Schiff kann sich bewegen
- Position und Richtung werden aktualisiert

---

## 🎉 BEDEUTUNG

**Phase 2 (Navigation) ist KOMPLETT!**

Du hast jetzt:
- ✅ Verbindung zum OceanServer
- ✅ Schiffssteuerung
- ✅ **Autonome Navigation**
- ✅ **Kollisionsvermeidung**
- ✅ Radar-Integration
- ✅ Systematische Exploration

**Nächster Schritt:** Phase 3 - Datenbank (Messdaten speichern)

---

## 💡 TIPPS

- **OceanServer-Karte:** Verfolge die Bewegung des Schiffs im GUI
- **Log-Level:** Ändere in `simplelogger.properties` auf DEBUG für mehr Details
- **Anzahl Sektoren:** In `Main.java` Zeile `navigator.explore(10)` → Zahl ändern

---

**LOS GEHT'S! 🚢**
