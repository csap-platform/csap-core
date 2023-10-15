#!/bin/sh
#
# used to kill all running services
#


scriptDir=$(dirname $0)
source $scriptDir/csap-environment.sh


#processFilter="server.hostname=`hostname`";
processFilter=$csapPlatformWorking
print_with_head "Killing All listed process, and their children using pattern '$processFilter'"

parentPid=$(ps -ef | grep $processFilter | grep -v -e grep -e $0 | awk '{ printf "%s ", $2 }')

print_with_head "parent Pids are $parentPid"

trim () {
    read -rd '' $1 <<<"${!1}"
}

trim parentPid

svcPids=$(ps -ef | grep -e ${parentPid// / -e }  | grep -v -e grep -e $0 | awk '{ print $2 }')

print_with_head "pids to be killed: $svcPids"

/bin/kill -9 $svcPids

isClean=$(expr match "$*" 'clean' != 0)

if [ $isClean == "1" ]; then

	print_with_head "clean was passed in, doing a rm -rf $csapPlatformWorking"
	
	if [ -d "$csapPlatformWorking" ]; then
		\rm --recursive --force $csapPlatformWorking ;
	fi ;
	mkdir --parents --verbose $csapPlatformWorking
fi ;