@echo off
setlocal EnableExtensions
set MASTER=%~dp0
if exist "%JAVA_HOME%\bin\java.exe" if not exist "%JAVA_HOME%\include\jni.h" (
  for %%I in ("%JAVA_HOME%\..") do set "JAVA_HOME=%%~fI"
)
if not exist "%JAVA_HOME%\include\jni.h" (
  for /d %%D in ("C:\Program Files\Java\jdk-*") do (
    if exist "%%D\include\jni.h" set "JAVA_HOME=%%D"
  )
)
if not exist "%JAVA_HOME%\include\jni.h" (
  echo JAVA_HOME does not contain include\jni.h
  exit /b 1
)
if exist "C:\Users\Scott\Qt\Tools\mingw810_64\bin\g++.exe" (
  set "PATH=C:\Users\Scott\Qt\Tools\mingw810_64\bin;%PATH%"
) else if exist "C:\msys64\ucrt64\bin\g++.exe" (
  set "PATH=C:\msys64\ucrt64\bin;%PATH%"
)
set OUT=%MASTER%native\jni\build\native
set RF=%MASTER%..\native

mkdir "%OUT%" 2>nul
mkdir "%RF%\windows" 2>nul
cd /d "%MASTER%"

set INC=%JAVA_HOME%\include
set INCW=%JAVA_HOME%\include\win32
set CXXDEF=-DNDEBUG -D_WIN32 -O2 -std=c++17 -ffp-contract=off -I. -Igeode -I"%INC%" -I"%INCW%"

del /q *.o bridge.o geode_selftest.exe 2>nul
g++ %CXXDEF% -c geode/Search.cpp -o Search.o
if errorlevel 1 exit /b 1
g++ %CXXDEF% -c geode/selftest.cpp -o selftest.o
if errorlevel 1 exit /b 1
g++ -O2 -o geode_selftest.exe selftest.o Search.o -static -static-libgcc -static-libstdc++ -pthread
if errorlevel 1 exit /b 1

echo Running geode selftest...
geode_selftest.exe
if errorlevel 1 exit /b 1

g++ %CXXDEF% -c native/jni/sunnyslopes_geodefinder_GeodeFinderBridge.cpp -o bridge.o
if errorlevel 1 exit /b 1
g++ -shared -O2 -o "%OUT%\libGeodeFinderLibJ.dll" Search.o bridge.o -static -static-libgcc -static-libstdc++ -pthread
if errorlevel 1 exit /b 1

copy /y "%OUT%\libGeodeFinderLibJ.dll" "%RF%\windows\libGeodeFinderLibJ.dll"
echo Built: %OUT%\libGeodeFinderLibJ.dll
echo Copied to: %RF%\windows\libGeodeFinderLibJ.dll
