#!/bin/bash
#
#
#

scriptDir=$(dirname $0)
scriptName=$(basename $0)



#echo == Syntax:  optional wipe out runtime dir: -clean: $isClean
#echo == $1 $2 $3

# csapProcessId=$2 ;
source $CSAP_FOLDER/bin/csap-environment.sh


print_if_debug Running $0 : dir is $scriptDir
print_if_debug param count: $#
print_if_debug params: $@

isClean=$(expr match "$svcClean" 'clean' != 0)
isSuperClean=$(expr match "$svcClean" 'super' != 0)
isSpawn=$(expr match "$svcSpawn" 'yes' != 0)


print_if_debug "isSpawn: '$isSpawn'"

if [ "$csapName" == "$csapAgentName" ] && [ $isSpawn == "0" ] ; then

	print_section "$csapAgentName kill with auto restart"
	
	logForNohup="$csapPlatformWorking/$csapAgentName-$isSpawn.log" ;
	launch_background "$scriptDir/csap-kill.sh" "$args -spawn" "$logForNohup"
	
	print_line "Token to exit read loop in OsCommandRunner.java -csap-abort-script-io-" ;

	exit ;
fi ;


if [ "$csapName" == "$csapAgentName" ] ; then
	print_with_head "csap-kill: Sleeping to give $csapAgentName a chance to update logs"
	sleep 3 
	print_with_head "csap-kill: running pkill on agent linux top instances"
	pkill -9 -f top\ -b
fi ;


print_if_debug searching ps output for csapProcessId=$csapProcessId

csapAllProcessFilter="csapProcessId"
csapProcessFilter="csapProcessId=$csapProcessId"

processList=$(ps -u $USER -f | grep $csapProcessFilter | grep -v grep)

isUsingProcessFilter=true ;

if [ "$processList" != "" ] ; then

	# prefer use of csapAllProcessFilter added to java processes
	parentPid=$(ps -u $USER -f| grep $csapProcessFilter  | grep -v -e grep -e $0 | awk '{ print $2 }') ;
	
else

	print_two_columns "csapPids" "'$csapPids'" ;
	parentPid="$csapPids" ;
	isUsingProcessFilter=false ;
	
fi ;


print_if_debug  "csap-kill.sh: "  processList matching  $csapName is $processList



print_if_debug  "csap-kill.sh: " parent Pids are $parentPid

pidsForServiceAndChildren=""

function findPidsForServiceAndChildren() {

	local message=${1:-Pre-kill scan}
	# some processes, including csagent, spawn some os processes. They need to be killed as well.
	if [[ "$parentPid" != ""  && "$parentPid" != "noMatches" ]] ; then 
		# -w to force words, and -e to specify matching pids to ensure pid subsets do not match
		# eg. pid 612 and 13612 would both match unless this is added
		# since only spaces are replaced, the first entry is explicity added
		pidFilterWithRegExpFilter=" -we $(echo $parentPid | sed 's/ / -we /g')"
		
		# -u $USER
		pidsForServiceAndChildren=$(ps -e --format pid,ppid | grep $pidFilterWithRegExpFilter  | awk '{ print $1 }')
		
		local processListing="$( ps -e --format pid,args | grep $pidFilterWithRegExpFilter | grep -v grep )" ;
		local scanDetails="scan pattern: '$pidFilterWithRegExpFilter', pids: '$pidsForServiceAndChildren'" ;
		
		if [ "$processListing" == "" ] ; then
			print_two_columns "$message" "no matches using $scanDetails"
		else
			print_command \
				 "$message, found $scanDetails" \
				"$processListing"
		fi
			
	
	fi ;
}

findPidsForServiceAndChildren "Current Processes" ;

print_if_debug  "csap-kill.sh\t:" child Pids are $pidsForServiceAndChildren

# 

function invokeApiKill() {
	
	print_if_debug  "csap-kill.sh\t:  == loading csap-api ..."

	pidsForServiceAndChildren="$csapPids" ;

	skipApiExtract="true" ;
	source csap-integration-api.sh ;
	skipApiExtract="" ;
	
	if [ "$apiFound" == "true" ] ; then 
		print_line "csap-kill.sh - invoking api"
		
		if `is_function_available api_service_kill` ; then
			api_service_kill
		else
			killWrapper
		fi
	else 
	
		print_with_head "csap-api.sh not found - skipping kill"
		
	fi ;
}

if [[ "$csapServer" == "csap-api" || "$csapServer" == "script" ]] ; then
	invokeApiKill ;
	
elif [ "$csapServer" == "SpringBoot" ] ; then
	print_if_debug  "csap-kill.sh\t: SpringBoot"
	source csap-integration-springboot.sh
	killBoot ;
	
fi ;

if [ $isSuperClean == "1"  ] ; then
	print_section "superclean specified, killing everything"
	#pidsForServiceAndChildren=`ps -u $USER -f| grep service.name | grep -v -e grep -e $0 | awk '{ print $2 }'`
	parentPid=`ps -u $USER -f| grep -e $csapAllProcessFilter -e httpd | grep -v -e grep -e $0 | awk '{ print $2 }'`
	print_with_head  "parent Pids found: '$parentPid'"
	
	if [ "$parentPid" != "" ] ; then 
		
		pidFilterWithRegExpFilter=" -we "`echo $parentPid | sed 's/ / -we /g'`
		pidsForServiceAndChildren=`ps -u $USER -f| grep $pidFilterWithRegExpFilter  | grep -v -e grep -e $0 | awk '{ print $2 }'`

		print_section "Processes to be killed with children added using $pidFilterWithRegExpFilter, pidsForServiceAndChildren is $pidsForServiceAndChildren"
		ps -u $USER -f| grep $pidFilterWithRegExpFilter  | grep -v -e grep -e $0
	fi ;

fi ;


if [[ "$pidsForServiceAndChildren" != ""  && "$pidsForServiceAndChildren" != "noMatches" ]] ; then

	print_two_columns  "kill -9"  "'$csapName' using pids: '$pidsForServiceAndChildren'"
	
	#echo `date`  $csapProcessId pids $pidsForServiceAndChildren >> $csapPlatformWorking/_killList.txt
	#echo $processList >> $csapPlatformWorking/_killList.txt
	#echo ======= >> $csapPlatformWorking/_killList.txt
	/bin/kill -9 $pidsForServiceAndChildren 2>&1
	
	sleep 3 ;
	
	findPidsForServiceAndChildren "Post kill";

	if [ "$pidsForServiceAndChildren" != "" ] ; then
	
		print_section "Processes still running, sleeping 5s and trying again"
		sleep 5;
		/bin/kill -9 $pidsForServiceAndChildren  2>&1
		
		print_section "Kill retry results ;"
		findPidsForServiceAndChildren "Post kill again";

	fi
	
else 
	print_section "Skipping kill since no processes found for '$csapName'"
fi ;

print_if_debug  "csap-kill.sh\t:" echo processes found post kill
print_if_debug  "csap-kill.sh\t:" `ps -u $USER -f| grep $csapName | grep -v -e grep -e killInstance`


if [ $isClean == "1" ] ||  [ $isSuperClean == "1"  ] ; then

	findPidsForServiceAndChildren "Preclean";
	
	if [ "$pidsForServiceAndChildren" != "" ] ; then
		print_section "Clean specified and processes still found: attempting clean up using root" ;
		run_using_root /bin/kill -9 $pidsForServiceAndChildren  2>&1 ;
	fi ;
	
	if  [ $isKeepLogs == "yes"  ] ; then
		print_section "preserving logs in '$csapWorkingDir.logs'"
		\rm -rf $csapWorkingDir.logs
		mv $csapWorkingDir/logs $csapWorkingDir.logs
	fi ;
	
	if test -d $csapWorkingDir ; then
		print_two_columns "clean up" "removing working folder '$csapWorkingDir'"
		\rm --recursive --force $csapWorkingDir
	fi ;
	
	if [ $isSuperClean == "1"  ] ; then
		
		print_two_columns "clean up" "removing platform folder '$csapPlatformWorking'"
		\rm --recursive --force $csapPlatformWorking ;
		
	fi ;
	
fi ;
	
if [ "$csapName" == "$csapAgentName" ] ; then
	print_section "$csapAgentName auto start"
	
	mkdir --parents --verbose $csapPlatformWorking ; 
	
	launch_background "$scriptDir/csap-start.sh" "$args" "$csapPlatformWorking/$csapAgentName-start-$isSpawn.log"
	
fi ;


