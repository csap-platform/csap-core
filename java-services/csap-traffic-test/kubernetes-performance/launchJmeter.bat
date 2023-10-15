echo %JAVA_HOME% 
echo setting the current dir to target folder, this is the default location for generating reports and errors

echo ==
echo == DO NOT FORGET to update path below for your workspaces, and copy the jmeter csv file to same folder as jmx
echo ==
SET
echo == 
cd C:\dev
echo %CD%
set JVM_ARGS=-Xms1024m -Xmx1024m
echo == JVM_ARGS may be increased decreased based on number of results collected: %JVM_ARGS%

set JMETER_HOME="C:\dev\apache-jmeter-5.4.3"
%JMETER_HOME%\bin\jmeter.bat

 