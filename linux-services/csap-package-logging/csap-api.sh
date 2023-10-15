#!/bin/bash

#
#  
#


function verify_settings() {
	volumeBasePath=${volume_os:-not-specified} ;
	loggingServices=${loggingServices:-elastic-search elastic-hq fluentd kibana} ;
	
	print_separator "$csapName Package: $volumeBasePath"
	
	if [ "$volumeBasePath" == "not-specified" ] ; then
		print_error "volumeBasePath not set" ;
		exit 99;
	fi ;
}
verify_settings
	
function api_package_build() { 
	print_with_head "api_package_build not used" ; 
}




function api_package_get() {
	
	print_with_head "api_package_get() not used"
	
}


skipBacklogWaits=true ; # items get added to queue

function api_service_kill() { 

	api_service_stop
	
}


function api_service_stop() { 

	print_with_head "removing log services" ; 
	
	print_separator "Checking for running log services"
	count_running_services $loggingServices ;
	local numRunning=$?;
	
	if (( $numRunning > 0 )) ; then
		print_separator "Scheduling stops - try again once backlog is eliminated" ;
		stop_services "$loggingServices" clean;
		
		print_error "Failed to remove $csapName - logging services were running and now have been scheduled for removal"
		
		print_section "Wait for log service shutdown to complete and try again"
		exit 99;
	fi ;
	
	local clean="no" ; # or clean="clean", or no
	if [ "$isClean" == "1" ] ||  [ "$isSuperClean" == "1"  ] ; then
		local isApply="true"
		envsubst '$csapLife' <$csapWorkingDir/configuration/remove-logging.yaml >$csapWorkingDir/remove-logging.yaml
		update_application $csapWorkingDir/remove-logging.yaml $isApply ;
		
		print_separator "removing $volumeBasePath"
		run_using_root rm --recursive --force $volumeBasePath* ;
		
		print_separator "removing $csapDefinitionResources/_logservices_"
		for logService in $loggingServices ; do
			local resourceFolder=$csapDefinitionResources/$logService ;
			local output=$(rm --force $resourceFolder/*) ;
			if (( $? == 0 )) ; then
				output=$(rmdir $csapDefinitionResources/$logService 2>&1) ;
				if (( $? != 0 )) ; then
					print_two_columns "service-folder" "custom resources exist: $csapDefinitionResources/$logService" ;
				else 
					print_two_columns "service-folder" "removed: $csapDefinitionResources/$logService" ;
				fi ;
			fi ;
		done
		
	fi ;
}


function api_service_start() {

	print_section "api_service_start $csapName"
	
	#
	# load any application customizations
	#
	copy_csap_service_resources ;
	
	#
	# minor configuration changs for crio support
	#
	if is_package_installed cri-o ; then
	
		print_separator "configuring services with CRIO support" ;
		
		local verbose="true"
		
		local dashLoaderFiler="$csapWorkingDir/configuration/resources/kibana/load-sample-dashboards.sh"
		replace_all_in_file '"log"' '"message"' $dashLoaderFiler $verbose;
		
		local ndJsonFiles="$csapWorkingDir/configuration/resources/kibana/*.ndjson" ;
		for ndJsonFile in $ndJsonFiles ; do
			replace_all_in_file '"log"' '"message"' $ndJsonFile $verbose;
		done;
		
		/csap-package-logging/configuration/resources/kibana/calico-dashboard.ndjson
		
		
		
		local addLoggingFile="$csapWorkingDir/configuration/add-logging.yaml"
		replace_all_in_file 'fluentd-docker-config-map.yaml' 'fluentd-crio-config-map.yaml' $addLoggingFile $verbose;
		
		
		local fluentdFile="$csapWorkingDir/configuration/resources/fluentd/fluentd.yaml"
		replace_all_in_file '/var/lib/docker/containers' '/var/log/pods' $fluentdFile $verbose;
		
	else
		print_with_head "Default configuration: kubernetes docker container"
	fi ;
	
	#
	#  Update application with logging services only if not already present
	#
	print_separator "Checking for running log services"
	count_running_services $loggingServices ;
	local numRunning=$?;
	
	if (( $numRunning == 0 )) ; then
	
		#
		# Copy resources
		#
		print_separator "copying logging resources to application folder"
		cp --recursive --force --verbose $csapWorkingDir/configuration/resources/* $csapDefinitionResources
		
		
		local isApply="true"
		envsubst '$csapLife' <$csapWorkingDir/configuration/add-logging.yaml >$csapWorkingDir/add-logging.yaml
		update_application $csapWorkingDir/add-logging.yaml $isApply ;
		
		print_separator "adding deployment requests"
		delay_with_message 10 "Delaying for application reloads on hosts" ;
		deploy_services "$loggingServices" ;
			
	fi ;

	
	print_with_head "Deployment progress and operations are available using CSAP console"
	

}





