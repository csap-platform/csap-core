#!/bin/bash
#
#
#

scriptDir=$(dirname $0)

if [ -z "$CSAP_FOLDER" ] ; then 
	echo Warning - Did not find CSAP_FOLDER env variable. Resourcing $HOME/.bashrc
	source $HOME/.bashrc
fi




#echo == Syntax:  optional wipe out runtime dir: -clean: $isClean
#echo == $1 $2 $3


source $CSAP_FOLDER/bin/csap-environment.sh

print_if_debug Running $0 : dir is $scriptDir
print_if_debug param count: $#
print_if_debug params: $@



csapEvent=${csapEvent:none} ;
csapJobBackground=${csapJobBackground:false} ;

print_section "Service Job: $csapName" ;
print_two_columns "job" "$csapJob" ;
print_two_columns "background" "$csapJobBackground" ;
print_two_columns "event" "$csapEvent" ;

firstCommand=$(cut --delimiter=' ' --field=1 <<< $csapJob) ;
firstCommandType=$(type -t $firstCommand) ;
firstCommandTypeRc=$? ;
if (( $firstCommandTypeRc == 0 )) ; then  firstCommandFound=true; else firstCommandFound=false ; fi

print_two_columns "firstCommand" "'$firstCommand'" ;
print_two_columns "type" "'$firstCommandType'" ;

#
# Ensure working folder created for log output and working folder
#
mkdir --parents --verbose $csapWorkingDir
cd $csapWorkingDir

#
# Update permissions; eg. if definition/scripts, etc
#


if ! $firstCommandFound ; then
	if test -f $firstCommand ; then
		print_two_columns "permissions" "chmod 755 $firstCommand" ;
		chmod 755 $firstCommand ;
		firstCommandType=$(type -t $firstCommand) ;
		print_two_columns "type new" "'$firstCommandType'" ;
	fi ;
fi ;

commandCount=$(wc -w <<< $csapJob) ; 

# automatically prune job log files
maxJobLogFiles=${maxJobLogFiles:-10} ;
jobLogFolder="$csapLogDir/serviceJobs"

pidFilesCount=$(find $jobLogFolder -maxdepth 1 -name "*.pid" | wc -l)
if (( $pidFilesCount > 0 )) ; then
	\rm --force $jobLogFolder/*.pid
fi ;

if [[ $csapJobBackground == "true" ]] ; then 

	mkdir --parents --verbose $jobLogFolder ;
	
	firstCommand=$(echo "$csapJob" | awk '{print $1}') ;
	numberOfExistingJobs=$(ps -ef | grep $firstCommand | grep --invert-match grep| wc -l) ;
	if (( numberOfExistingJobs == 0 )) ; then 
		launch_background "$csapJob" "" "$jobLogFolder/$outputFile" ;
	else
		print_error "Found jobs already running" ;
		print_command \
			"Matches from ps -ef | grep $firstCommand " \
			"$(ps -ef | grep $firstCommand)"
	fi;
	
else 
	
	if (( $commandCount == 1 )) && [[ "$firstCommandType" == "file" ]] ; then
		print_separator "event script output"
		source $csapJob ;
	else 
		print_separator "command output"
		eval $csapJob
	fi ;
	
fi ;

numFiles=$(ls $jobLogFolder/* | wc -w) ;
if (( $numFiles > $maxJobLogFiles )) ; then
	print_with_head "$jobLogFolder - Running clean up: $numFiles found, maxJobLogFiles: $maxJobLogFiles (update service env vars to modify)"
	numToDelete=$(( $numFiles - $maxJobLogFiles))
	ls -t $jobLogFolder/* | tail -$numToDelete | xargs \rm  --verbose --recursive --force ;
	#ls -t $jobLogFolder/* | tail -$numToDelete | xargs ls -l
fi ;





