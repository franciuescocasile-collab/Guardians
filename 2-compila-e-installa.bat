@echo off
rem Compila l'app e la installa sul dispositivo collegato
rem (emulatore avviato con 1-avvia-emulatore.bat, oppure telefono via USB).
setlocal
cd /d "%~dp0"
set JAVA_HOME=C:\AndroidSdk\jdk
set ANDROID_HOME=C:\AndroidSdk
set ADB=C:\AndroidSdk\platform-tools\adb.exe

echo ============================================================
echo  [1/4] COMPILAZIONE APK  (puo' richiedere 1-2 minuti)
echo ============================================================
call gradlew.bat assembleDebug
if errorlevel 1 (
    echo.
    echo ^>^> ERRORE durante la compilazione. Leggi i messaggi qui sopra.
    pause
    exit /b 1
)

echo.
echo ============================================================
echo  [2/4] CERCO UN DISPOSITIVO  (emulatore o telefono USB)
echo ============================================================
"%ADB%" start-server >nul 2>&1

rem Conta i dispositivi realmente pronti (righe che finiscono con "device").
set DEVCOUNT=0
for /f "skip=1 tokens=1,2" %%a in ('"%ADB%" devices') do (
    if "%%b"=="device" set /a DEVCOUNT+=1
)

if "%DEVCOUNT%"=="0" (
    echo.
    echo ^>^> NESSUN DISPOSITIVO TROVATO. L'installazione NON puo' partire.
    echo.
    echo    Controlla UNA di queste cose:
    echo    - EMULATORE: avvialo prima con  1-avvia-emulatore.bat  e aspetta
    echo      che il telefono virtuale sia acceso del tutto.
    echo    - TELEFONO VERO: collega il cavo USB, attiva il "Debug USB" nelle
    echo      Opzioni sviluppatore, e sul telefono tocca "Consenti" quando
    echo      chiede di autorizzare questo computer.
    echo.
    echo    Dispositivi visti da adb in questo momento:
    "%ADB%" devices
    echo.
    pause
    exit /b 1
)

echo    Dispositivo trovato. Procedo.
"%ADB%" devices

echo.
echo ============================================================
echo  [3/4] INSTALLAZIONE  (attendi: "Success" = riuscita)
echo ============================================================
"%ADB%" install -r "app\build\outputs\apk\debug\app-debug.apk"
if errorlevel 1 (
    echo.
    echo ^>^> ERRORE durante l'installazione.
    echo    Se leggi "signatures do not match" disinstalla prima la vecchia
    echo    app dal telefono e rilancia questo file.
    pause
    exit /b 1
)

echo.
echo ============================================================
echo  [4/4] AVVIO DELL'APP
echo ============================================================
"%ADB%" shell am start -n com.guardians.app/.MainActivity >nul 2>&1

echo.
echo ^>^> FATTO! App aggiornata e avviata.
echo    (L'APK si trova anche in: app\build\outputs\apk\debug\app-debug.apk)
echo.
pause
