@echo off
setlocal
rem Reuse this PC's existing Android Studio and SDK; do not change global variables.
if defined BEAD_JAVA_HOME (set "JAVA_HOME=%BEAD_JAVA_HOME%") else (set "JAVA_HOME=D:\soft\Android_Studio\jbr")
if defined BEAD_ANDROID_HOME (set "ANDROID_HOME=%BEAD_ANDROID_HOME%") else (set "ANDROID_HOME=D:\soft\Android_Sdk")
if not defined GRADLE_USER_HOME set "GRADLE_USER_HOME=D:\soft\Android_Sdk\Cache\Gradle"
if not exist "%JAVA_HOME%\bin\java.exe" (
  echo Set BEAD_JAVA_HOME to an existing JDK 17 or newer compatible with Gradle 9.4.1.
  exit /b 1
)
cd /d "%~dp0"
set "BEAD_SDK_ARG="
if not exist "%ANDROID_HOME%\platforms\android-36\android.jar" (
  if exist "%ANDROID_HOME%\platforms\android-37.0\android.jar" set "BEAD_SDK_ARG=-Pbead.compileSdk=37"
)
if not "%BEAD_SDK_ARG%"=="" echo DEBUG VALIDATION: reusing installed API 37.0; targetSdk stays 36 and minSdk stays 31.
call gradlew.bat %BEAD_SDK_ARG% testDebugUnitTest lintDebug stageDebugArtifacts stageVerificationReports --console=plain --continue
exit /b %ERRORLEVEL%
