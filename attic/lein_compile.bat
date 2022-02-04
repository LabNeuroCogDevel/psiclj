Rem from
Rem https://github.com/clj-kondo/clj-kondo/blob/fde1e542320d9c45c6a213dc1133b35d23b697ae/script/compile.bat
@echo off

set ROOTPATH=L:\Applications\JavaDevelGraalVM\
set GRAALVM_HOME=%ROOTPATH%\graalvm-ce-java17-22.0.0.2
set PATH=%PATH%;%ROOTPATH%
Rem set PATH=%PATH%;C:\Users\IEUser\bin

if "%GRAALVM_HOME%"=="" ( 
    echo Please set GRAALVM_HOME
    exit /b
)
set JAVA_HOME=%GRAALVM_HOME%\bin
set PATH=%PATH%;%GRAALVM_HOME%\bin

set /P VERSION=1.0.0
echo Building %VERSION%

call lein.bat do clean, uberjar
if %errorlevel% neq 0 exit /b %errorlevel%

Rem the --no-server option is not supported in GraalVM Windows.
Rem -H:Name=psiclj ^   ... is optional?
Rem added no-fallback
call %GRAALVM_HOME%\bin\native-image.cmd ^
  -jar target/psiclj-%VERSION%-standalone.jar ^
  -H:+ReportExceptionStackTraces ^
  -J-Dclojure.spec.skip-macros=true ^
  -J-Dclojure.compiler.direct-linking=true ^
  "-H:IncludeResources=.*html$" ^
  --no-fallback ^
  --allow-incomplete-classpath ^
  -H:ReflectionConfigurationFiles=reflection.json ^
  --initialize-at-build-time  ^
  -H:Log=registerResource: ^
  --verbose ^
  "-J-Xmx3g"
if %errorlevel% neq 0 exit /b %errorlevel%

echo Creating zip archive
jar -cMf psiclj-%VERSION%-windows-amd64.zip psiclj.exe
