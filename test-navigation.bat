@echo off
REM ====================================
REM Ocean Explorer - Phase 2 Navigation Test
REM ====================================

echo.
echo ====================================
echo Ocean Explorer - Navigation Test
echo ====================================
echo.
echo OceanServer muss laufen auf:
echo   - Ship Port: 8150
echo   - Submarine Port: 8151
echo.
echo Starte ShipApp mit Navigation...
echo.

REM ShipApp starten (Maven)
call mvn exec:java -Dexec.mainClass="ocean.Main"

echo.
echo ====================================
echo Test abgeschlossen
echo ====================================
pause
