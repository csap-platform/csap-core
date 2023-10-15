#!/bin/bash
#
#  Used when OsCommandRunner times out running a script, or script is cancelled.
#

source $CSAP_FOLDER/bin/csap-environment.sh

print_with_head "Running '$0' \t\t current dir: '$(pwd)'"


function killPattern() {

	killFilter=$1;
	print_line "Searching for processes matching: '$killFilter'"
	ps -ef | grep $killFilter | grep -v grep| grep -v csap-run-as-root | grep -v admin-kill-scripts | grep -v DcsapProcessId

	# exclude running services and kill scripts
	processToKillCount=$(ps -ef | grep $killFilter | grep -v grep| grep -v csap-run-as-root | grep -v admin-kill-scripts| grep -v DcsapProcessId|wc -l)

	if (( $processToKillCount != 0 )) ; then

		print_line "Found '$processToKillCount' matches"

		parentPid=$(ps -ef | grep $killFilter | grep -v grep | grep -v csap-run-as-root | grep -v admin-kill-scripts | grep -v DcsapProcessId | awk '{ print $2 }')

		childPids="none"
		#print_line "parentPid is $parentPid"
		if [ "$parentPid" != "" ] ; then 
			# -w to force words, and -e to specify matching pids to ensure pid subsets do not match
			# eg. pid 612 and 13612 would both match unless this is added
			# since only spaces are replaced, the first entry is explicity added
			pidFilterWithRegExpFilter=" -we $(echo $parentPid | sed 's/ / -we /g')"
			childPids=$(ps -ef | grep $pidFilterWithRegExpFilter  | grep -v -e grep -e $0 | awk '{ print $2 }')
			print_line "Processes to be killed with children added using $pidFilterWithRegExpFilter"
			ps -ef | grep $pidFilterWithRegExpFilter  | grep -v -e grep -e $0 
		fi ;

		print_with_head "child pids of '$parentPid' will be killed: '$childPids'"

		/bin/kill -9 $childPids
		if [  "$CSAP_NO_ROOT" == "" ] ; then 
			print_line "Triggering a root kill in case process was run as root"
			sleep 2
			/usr/bin/sudo /bin/kill -9  $childPids
		fi ;
	else 
		print_line "\t\t No processes found"
	fi ;
	sleep 2
}

killPattern "/saved/scripts-run"
killPattern "csapDeployOp"
#killPattern "rootDeploy.sh"

