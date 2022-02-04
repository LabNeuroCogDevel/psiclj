@echo off

Rem graalvm: https://www.graalvm.org/downloads/
Rem deps.exe: https://github.com/borkdude/deps.clj

Rem root path has deps.exe and graalvm folder
Rem hosting remotely, on network share outside of VM
set ROOTPATH=L:\Applications\JavaDevelGraalVM\
set GRAALVM_HOME=%ROOTPATH%\graalvm-ce-java17-22.0.0.2
set JAVA_HOME=%GRAALVM_HOME%\bin
set PATH=%PATH%;%GRAALVM_HOME%\bin;%ROOTPATH%
deps.exe -A:native-image
