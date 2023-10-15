#!/bin/bash

function print_two_columns() { 
	printf "%25s: %-20s\n" "$@"; 
}


print_two_columns "os.date" "$(date +"%h-%d-%I-%M-%S")" ;
print_two_columns "os.host" "$(hostname --long)"
print_two_columns "os.user" "$(id)"
print_two_columns "os.path" "$PATH"
print_two_columns "os.version" "$(cat /etc/redhat-release)"


javaVersion=$(java -version 2>&1 | tail -1)

print_two_columns "java.home" "$JAVA_HOME" ;
print_two_columns "java.version" "$javaVersion"


#startCommand=${startCommand:-java} ;
print_two_columns "csap.startCommand" "$startCommand"
print_two_columns "csap.javaOptions" "$javaOptions"
print_two_columns "csap.javaTarget" "$javaTarget"

echo -e "\n\n ------------- starting service -------------  \n\n"

eval $startCommand $javaOptions $javaTarget

#bash -c "$startCommand"
#java $JAVA_OPTIONS