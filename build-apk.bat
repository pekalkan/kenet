@echo off
setlocal EnableExtensions EnableDelayedExpansion
cd /d "%~dp0"
title Kenet - APK derleme
chcp 65001 >nul

echo.
echo  ================================================
echo   KENET  -  APK derleme
echo  ================================================
echo.

if not exist "%~dp0gradlew.bat" (
  echo  HATA: Bu klasorde proje dosyalari eksik ^(gradlew.bat yok^).
  echo  Kenet.zip'i bir klasore cikar ve build-apk.bat'i o klasorun icinden calistir.
  pause & exit /b 1
)

REM JDK ve SDK tum klasorler icin ortak bir yere kurulur; klasoru tasisan da tekrar indirilmez
set "TOOLS=%LOCALAPPDATA%\KenetBuild"
if not exist "%TOOLS%" mkdir "%TOOLS%"

REM ---------- 1) JDK 17-21 (Gradle 8.7 daha yeni Java'yi desteklemez) ----------
set "JDK="
if defined JAVA_HOME call :tryjdk "%JAVA_HOME%"
if not defined JDK call :tryjdk "%ProgramFiles%\Android\Android Studio\jbr"
if not defined JDK for /d %%D in ("%TOOLS%\jdk\jdk-*") do if not defined JDK call :tryjdk "%%~D"
if not defined JDK for /d %%D in ("%~dp0.tools\jdk\jdk-*") do if not defined JDK call :tryjdk "%%~D"
if not defined JDK for /d %%D in ("%LOCALAPPDATA%\TetraminosBuild\jdk\jdk-*") do if not defined JDK call :tryjdk "%%~D"
if not defined JDK for /d %%D in ("%ProgramFiles%\Eclipse Adoptium\jdk-*") do if not defined JDK call :tryjdk "%%~D"
if not defined JDK for /d %%D in ("%ProgramFiles%\Java\jdk-*") do if not defined JDK call :tryjdk "%%~D"
if not defined JDK for /d %%D in ("%ProgramFiles%\Microsoft\jdk-*") do if not defined JDK call :tryjdk "%%~D"
if not defined JDK (
  echo  [1/5] Uygun JDK ^(17-21^) bulunamadi, Temurin JDK 17 indiriliyor ^(~190 MB^)...
  mkdir "%TOOLS%\jdk" 2>nul
  curl -L -# -o "%TOOLS%\jdk.zip" "https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jdk/hotspot/normal/eclipse?project=jdk"
  if errorlevel 1 goto :fail_dl
  tar -xf "%TOOLS%\jdk.zip" -C "%TOOLS%\jdk"
  del "%TOOLS%\jdk.zip"
  for /d %%D in ("%TOOLS%\jdk\jdk-*") do if not defined JDK call :tryjdk "%%~D"
)
if not defined JDK goto :fail_jdk
set "JAVA_HOME=%JDK%"
set "PATH=%JDK%\bin;%PATH%"
echo  [1/5] JDK: %JDK%

REM ---------- 2) Android SDK ----------
set "SDK="
if defined ANDROID_HOME if exist "%ANDROID_HOME%\platforms" set "SDK=%ANDROID_HOME%"
if not defined SDK if defined ANDROID_SDK_ROOT if exist "%ANDROID_SDK_ROOT%\platforms" set "SDK=%ANDROID_SDK_ROOT%"
if not defined SDK if exist "%LOCALAPPDATA%\Android\Sdk\platforms" set "SDK=%LOCALAPPDATA%\Android\Sdk"
if not defined SDK if exist "%TOOLS%\sdk\platforms" set "SDK=%TOOLS%\sdk"
if not defined SDK if exist "%~dp0.tools\sdk\platforms" set "SDK=%~dp0.tools\sdk"
if not defined SDK if exist "%LOCALAPPDATA%\TetraminosBuild\sdk\platforms" set "SDK=%LOCALAPPDATA%\TetraminosBuild\sdk"
if not defined SDK (
  echo  [2/5] Android SDK bulunamadi, komut satiri araclari indiriliyor ^(~150 MB + paketler^)...
  set "SDK=%TOOLS%\sdk"
  mkdir "!SDK!\cmdline-tools" 2>nul
  curl -L -# -o "%TOOLS%\cmdtools.zip" "https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip"
  if errorlevel 1 goto :fail_dl
  tar -xf "%TOOLS%\cmdtools.zip" -C "!SDK!\cmdline-tools"
  del "%TOOLS%\cmdtools.zip"
  if exist "!SDK!\cmdline-tools\cmdline-tools" move /y "!SDK!\cmdline-tools\cmdline-tools" "!SDK!\cmdline-tools\latest" >nul
  echo  Lisanslar kabul ediliyor ve paketler kuruluyor ^(bir sure surebilir^)...
  (for /l %%i in (1,1,30) do @echo y) | call "!SDK!\cmdline-tools\latest\bin\sdkmanager.bat" --sdk_root="!SDK!" --licenses >nul
  call "!SDK!\cmdline-tools\latest\bin\sdkmanager.bat" --sdk_root="!SDK!" "platform-tools" "platforms;android-34" "build-tools;34.0.0"
  if errorlevel 1 goto :fail_sdk
)
echo  [2/5] Android SDK: %SDK%
set "SDKP=%SDK:\=/%"
> local.properties echo sdk.dir=%SDKP%

REM ---------- 3) Muzik ----------
if not exist "muzik" mkdir "muzik"
if exist "app\src\main\assets\music" rmdir /s /q "app\src\main\assets\music"
mkdir "app\src\main\assets\music"
robocopy "muzik" "app\src\main\assets\music" *.mp3 *.m4a *.ogg *.opus *.wav *.aac *.flac /NFL /NDL /NJH /NJS /NP /R:1 /W:1 >nul
set "NSONG=0"
for %%F in ("app\src\main\assets\music\*") do set /a NSONG+=1
if "!NSONG!"=="0" (echo  [3/5] Muzik: muzik klasoru bos, oyunun kendi muzigi kullanilacak) else (echo  [3/5] Muzik: !NSONG! sarki eklendi)

REM ---------- 4) Derleme ----------
echo  [4/5] Gradle ile derleniyor... ^(ilk seferde Gradle ve eklentiler indirilir, 5-10 dk surebilir^)
echo.
call gradlew.bat assembleDebug --no-daemon --console=plain "-Dorg.gradle.java.home=%JDK%"
if errorlevel 1 goto :fail_build

REM ---------- 5) Sonuc ----------
copy /y "app\build\outputs\apk\debug\app-debug.apk" "Kenet.apk" >nul
echo.
echo  ================================================
echo   [5/5] HAZIR:  %~dp0Kenet.apk
echo  ================================================
echo.
echo  Kurulum: Kenet.apk dosyasini telefona kopyala ^(WhatsApp/USB/Drive^),
echo  telefonda dosyaya dokun, "bilinmeyen kaynaklara izin ver" de, kur.
echo.
echo  Telefon USB ile bagli ve USB hata ayiklama acik ise simdi kurmak icin bir tusa bas,
echo  istemiyorsan pencereyi kapat.
pause >nul
"%SDK%\platform-tools\adb.exe" install -r "Kenet.apk"
pause
exit /b 0

:tryjdk
REM Aday klasordeki java surumunu kontrol eder; 17-21 ise JDK degiskenini ayarlar
if not exist "%~1\bin\java.exe" exit /b 0
"%~1\bin\java.exe" -version 2>&1 | findstr /r /c:"version .1[7-9]\." /c:"version .2[01]\." >nul
if not errorlevel 1 set "JDK=%~1"
exit /b 0

:fail_dl
echo.
echo  HATA: Indirme basarisiz oldu. Internet baglantisini kontrol edip tekrar dene.
pause & exit /b 1
:fail_jdk
echo.
echo  HATA: JDK kurulamadi. https://adoptium.net adresinden JDK 17 kurup tekrar dene.
pause & exit /b 1
:fail_sdk
echo.
echo  HATA: Android SDK paketleri kurulamadi. %LOCALAPPDATA%\KenetBuild\sdk klasorunu silip tekrar dene.
pause & exit /b 1
:fail_build
echo.
echo  HATA: Derleme basarisiz. Yukaridaki hata mesajini Claude'a yapistir, birlikte cozelim.
pause & exit /b 1
