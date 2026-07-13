@echo off
rem Avvia l'emulatore Android "Guardians_Phone" (il telefono virtuale sul PC).
rem Lascialo aperto: poi usa 2-compila-e-installa.bat per mettere l'app aggiornata.
set ANDROID_HOME=C:\AndroidSdk
set ANDROID_SDK_ROOT=C:\AndroidSdk
start "Emulatore Android" "C:\AndroidSdk\emulator\emulator.exe" -avd Guardians_Phone
echo Emulatore in avvio... la prima volta puo' impiegare qualche minuto.
