@echo off
REM Builds Grabix APKs on Windows.
REM Needs: Android Studio installed once (it provides Java 17 and the Android SDK).

if "%ANDROID_HOME%"=="" if exist "%LOCALAPPDATA%\Android\Sdk" set "ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk"
if "%JAVA_HOME%"=="" if exist "C:\Program Files\Android\Android Studio\jbr" set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"

if "%ANDROID_HOME%"=="" (
  echo Android SDK not found. Install Android Studio first: https://developer.android.com/studio
  exit /b 1
)
echo sdk.dir=%ANDROID_HOME:\=\\%> local.properties

call gradlew.bat assembleRelease
if errorlevel 1 exit /b 1

echo.
echo Done. APKs are in app\build\outputs\apk\release\
echo Install "app-universal-release.apk" if unsure which one fits your phone.
explorer app\build\outputs\apk\release
