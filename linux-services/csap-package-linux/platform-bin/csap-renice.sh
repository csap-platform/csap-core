#!/bin/bash

processPattern=$1
priority=$2
optionalPid=$3

if [ "$optionalPid" != "" ] ; then
	delimPids=$(echo $optionalPid | sed -e "s/,/ -p /g")
	echo "== Pid passed in, reniceing $optionalPid to $priority"
	renice $priority -p $delimPids
	
	# echo  Confirm output below
	# ps l -p $optionalPid
	
else

	echo "== process pattern passed in , reniceing $processPattern to $priority"
	
	pidMatches=$(ps -ef  | grep $processPattern | grep -v -e grep -e csap-renice | awk '{ print $2 }')
	
	for pid in $pidMatches ; do 
		echo Found $pid 
		renice $priority -p $pid
		echo  Confirm output below
		ps l -p $pid
	done
fi