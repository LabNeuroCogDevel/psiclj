Rem root path should have deps.exe and graalvm folder
Rem hosting remotely, on network share outside of VM
Rem 
Rem deps.exe: https://github.com/borkdude/deps.clj
Rem graalvm: https://www.graalvm.org/downloads/; consider 'choco install graalvm'
Rem need git(forwindows) to fetch dep.exe native-image plugin. but not used
Rem
Rem
set ROOTPATH=C:\Users\IEUser\Desktop\JavaDevelGraalVM\
set GRAALVM_HOME=%ROOTPATH%\graalvm-ce-java17-22.0.0.2
set JAVA_HOME=%GRAALVM_HOME%\bin
set PATH=%PATH%;%GRAALVM_HOME%\bin;%ROOTPATH%
SET "PATH=%PATH%;%ALLUSERSPROFILE%\chocolatey\bin"



WHERE cl.exe
IF %ERRORLEVEL% NEQ 0 (
Rem === install compiler tools
Rem install package manage(chocolatey) to get SDK (MSVC2017) and setup. otherwise get "cl.exe" not found
Rem need admin shell?
"%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -NoProfile -InputFormat None -ExecutionPolicy Bypass -Command "[System.Net.ServicePointManager]::SecurityProtocol = 3072; iex ((New-Object System.Net.WebClient).DownloadString('https://community.chocolatey.org/install.ps1'))" && SET "PATH=%PATH%;%ALLUSERSPROFILE%\chocolatey\bin"
  choco install visualstudio2017community --version 15.9.17.0 --no-progress --package-parameters "--add Microsoft.VisualStudio.Component.VC.Tools.ARM64 --add Microsoft.VisualStudio.Component.VC.CMake.Project" -y
 "C:\Program Files (x86)\Microsoft Visual Studio\2017\Community\VC\Auxiliary\Build\vcvarsx86_amd64.bat"
)

Rem without native-image deps.exe -A:native-image gives: 'null' error
WHERE native-image 
IF %ERRORLEVEL% NEQ 0 nu install native-image

Rem deps.exe -A:uberjar
Rem last took 537258 ms
Rem error that lien uberjar doesnt have
Rem .NativeImage$NativeImageError: No main manifest attribute, in psiclj.jar


Rem is lein aviable? needed b/c haven't figured out how to configure deps.edn abov

WHERE lein.bat 
IF %ERRORLEVEL% NEQ 0 (
Rem INSTALL LEININGEN
Rem https://raw.githubusercontent.com/technomancy/leiningen/stable/bin/lein.bat
Rem lein.bat self-install
exit /b 1
)

Rem Make eg target/psiclj-0.1.0-standalone.jar
Rem this standalone works with native-image (or at least did once)
lein uberjar

Rem deps.exe -A:native-image
Rem "The command line is too long"

Rem Using native-image with uberjar by hand instead
Rem Would "lien native-image" work? dont remember trying (20220205WF)
native-image -jar target/psiclj-0.2.3-standalone.jar ^
  -H:Name=psiclj -H:-CheckToolchain -H:+ReportExceptionStackTraces ^
  -H:"IncludeResources=.*html$" ^
  --verbose --allow-incomplete-classpath --no-fallback ^
  --initialize-at-build-time ^
  --initialize-at-build-time=org.sqlite.JDBC ^
  -J-Dclojure.compiler.direct-linking=true -J-Dclojure.spec.skip-macros=true 
