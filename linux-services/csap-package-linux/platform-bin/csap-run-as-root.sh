#!/bin/bash

scriptDir=$(dirname $0)
source $scriptDir/csap-environment.sh

print_if_debug "Running $*"

numberOfArguments=$# ;

if (( $numberOfArguments != 2 )) ; then
	print_with_head "params: scriptname targetUnixOwner"
	exit ;
fi ;

scriptToRun="$1" ;
targetUser="$2" ;

print_two_columns "script" "$scriptToRun"
print_two_columns "user" "$targetUser"

currentUser="$USER" ;

# ensuring script is correct linefeeds
coversionResults=$(dos2unix $scriptToRun 2>&1) ;
print_if_debug "coversionResults" ;

print_if_debug "chmod 755 $scriptToRun" ;
chmod 755 $scriptToRun ;

if  [ "$currentUser" == "root" ]; then

	# making sure agent has read access. Note agent can be ANY user, so retrieve from ps output
	agentUser=$(ps -ef | grep $csapAgentId | grep -v grep | awk '{ print $1 }');
	chown -R $agentUser $scriptToRun
	chgrp -R $agentUser $scriptToRun
	
	print_if_debug "Running as $targetUser" ;
else
	print_if_debug "Running as non root user" ;
fi ;

# token to strip header
echo _CSAP_OUTPUT_

# support for running scripts as other users
if [ $targetUser != "root" ] && [ "$currentUser" == "root" ]; then 
	sudo su - $targetUser -c $scriptToRun
else
	TERM="dumb"
	
	if ! test -e $scriptToRun ; then
		print_section "Failed to find executable file '$scriptToRun'"
	fi ;
	stdbuf -o0 $scriptToRun
fi ;
