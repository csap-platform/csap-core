#!/bin/bash

testServices=${testServices:-services-not-specified};
print_with_head "CSAP Traffic Package"

print_two_columns "testFolder" "'$testFolder'"
print_two_columns "testServices" "'$testServices'"
print_two_columns "time_in_minutes" "'$time_in_minutes'"

env  >> $csapPlatformWorking/$csapName-environment.log ;


function api_package_build() { print_line "api_package_build not used" ; }

function api_package_get() { print_line "api_package_build not used" ; }


function api_service_kill() { 

	print_if_debug "KILL" 
	export killOnly=true;
	$CSAP_FOLDER/bin/admin-run-load-test.sh
}

function api_service_stop() {

	print_if_debug "STOP" ; 
	export killOnly=true;
	$CSAP_FOLDER/bin/admin-run-load-test.sh	
	
}

function api_service_start() {

	print_if_debug START
	
	print_separator "start request: checking for running services '$testServices'"
	count_running_services $testServices ;
	local numRunning=$?;
	
	if (( $numRunning == 0 )) ; then
		print_error "Exiting: Did not find tests services running. Ensure 'testServices' environment variable is set"
		exit 99 ;
	fi ;
	
	print_two_columns "Note" "Test will end automatically: create stop file to prevent alerts: '$csapStopFile'";
    touch $csapStopFile
    
    if [ -d "$csapResourceFolder/$csapLife" ] ; then
    	print_two_columns "configuration folder" "'$testFolder', using: '$csapResourceFolder/$csapLife'" ;
    	cp -rf $csapResourceFolder/$csapLife $testFolder ;
	else
		print_two_columns "Default" "service test profile being used: '$testFolder'. For custom runs,  add files using csap application editor."
	fi ;
	
	print_separator "Test Configuration"
	cat $testFolder/test-settings.txt
    
	$CSAP_FOLDER/bin/admin-run-load-test.sh
	
}