#!/bin/bash

#
# set this to false to perform the updates
#
showUpdatesOnly=${showUpdatesOnly:-true};
hostsToApply=${hostsToApply:-$(hostname --short)};
maxMinutesPerHost=${maxMinutesPerHost:-30};

#
# Optional
#
doParallel=${doParallel:-false};
yumUpdateOptions=${yumUpdateOptions:- };
rebootHostAfterUpdates=${rebootHostAfterUpdates:-true};
performRepoMaintenance=${performRepoMaintenance:-true};
kubernetesServiceName=${kubernetesServiceName:-kubelet};
debugRun=${debugRun:-false};

# echo params is: $params
# Supress any info messages
source $CSAP_FOLDER/bin/csap-environment.sh >/dev/null

# %s seconds, millis with %3N
millisNow=$(date +%s%3N);

# yum, performs orchestrated os upgrade of csap host

packageCommand="yum" ;
if is_package_installed dnf ; then 
	packageCommand="dnf";
fi


#
#  Comment this out if you are certain timeout is sufficient to perform the updgrade
#
#delay_with_message 10 "csap command timeout defaults to 5 minutes: increase if needed before running this script" ;

#
#  update yum repositories		
#
function maintain_repositories() {
	
	print_command \
		"$packageCommand clean all " \
		"$(run_using_root $packageCommand clean all  2>&1)" ;
		
	print_command \
		"$packageCommand makecache " \
		"$(run_using_root $packageCommand makecache 2>&1)" ;
		
	print_command \
		"$packageCommand repolist " \
		"$($packageCommand repolist )" ;


}
if [ "$performRepoMaintenance" == true ] ; then
	maintain_repositories ;
fi ;




#
#  stop kubelet gracefully (does eviction) and docker, leaves $packageCommand packages in place	
#
function shutdown_containers() {
	
	
	if (( $(count_services_on_host $kubernetesServiceName) > 0 )) ; then
		print_command \
			"agent agent/service/stop " \
			"$(agent agent/service/stop  --params "$(csap_credentials),services=$kubernetesServiceName")" ;
			
		sleep 5 ;
		wait_for_csap_backlog
		
		# rebooting after updates are done; removing the stopped file ensures agent will restart
		rm --verbose $csapPlatformWorking/$kubernetesServiceName.stopped
	fi;
	
	
	if (( $(count_services_on_host docker) > 0 )) ; then
		print_command \
			"agent agent/service/stop " \
			"$(agent agent/service/stop  --params "$(csap_credentials),services=docker")" ;
		sleep 5 ;
		wait_for_csap_backlog
		
		# rebooting after updates are done; removing the stopped file ensures agent will restart
		rm --verbose $csapPlatformWorking/docker.stopped
	fi;
	
}


#
#  Optional test logic only: containers will be restarted when agent restarts.
#
function start_containers() {
	
	if (( $(count_services_on_host docker) > 0 )) ; then
		print_command \
			"agent agent/service/start " \
			"$(agent agent/service/start  --params "$(csap_credentials),services=docker")" ;
		wait_for_csap_backlog
	fi;
	

	if (( $(count_services_on_host $kubernetesServiceName) > 0 )) ; then
		print_command \
			"agent agent/service/start " \
			"$(agent agent/service/start  --params "$(csap_credentials),services=$kubernetesServiceName")" ;
		wait_for_csap_backlog
	fi;
	

}
# start_containers


function wait_for_my_turn() {
	
	if [ $doParallel == true ] ; then
		print_line "doParallel - skipping delay"
		return 0 ;
	fi ;
	
	
	local hostName ;
	for hostName in $hostsToApply ; do
		
		if [ "$hostName" == "$(hostname --short)"  ] ; then
			# my turn to run
			return 0 ;
		fi ;
		
		delay_with_message 10 "Starting polling $hostName" ;
		
		local currentAttempt;
		for currentAttempt in $(seq 1 $maxMinutesPerHost); do
			
			local lastAgentJobMillis=$(agent "agent/event/latestTime?hostname=$hostName\&category=/csap/system/service/csap-agent/job");
			local lastAgentBootMillis=$(agent "agent/event/latestTime?hostname=$hostName\&category=/csap/system/agent-start-up");
			
			
			print_line2 "attempt $currentAttempt of $maxMinutesPerHost: waiting for $hostName agent boot ms: '$lastAgentBootMillis' to be greater then last agent job ms: '$lastAgentJobMillis'\n"
	
			if (( $lastAgentBootMillis > $lastAgentJobMillis )) ; then 
	
				print_with_head "$hostName Boot completed: '$lastAgentBootMillis'";
				break ;
			  
			fi ;
			sleep 60 ;
		done
		
		if (( $currentAttempt >= $maxMinutesPerHost )) ; then 
			print_error "Maximum attempts exceeded for '$hostName', " ;
			break ;
		fi ;
		
		
	done
	
	
	return 99 ;
	
	
}


#
# List out updates and optionally apply
#
function maintain_os() {
	
	print_command \
		"$packageCommand check-update " \
		"$($packageCommand check-update 2>&1)" ;
	
	print_command \
		"$packageCommand versionlock " \
		"$($packageCommand versionlock 2>&1)" ;
		
	
	print_command \
		"cat /etc/yum/pluginconf.d/versionlock.list " \
		"$(cat /etc/yum/pluginconf.d/versionlock.list 2>&1)" ;
		
	if [ $showUpdatesOnly == false ] ; then
	
		if wait_for_my_turn ; then
	

			
			if [ $debugRun == true ] ; then
				print_with_head "debug run enabled - skipping update"
				
				delay_with_message 60 "Restart pending" ;
				
				print_command \
					"restarting csap-agent: agent agent/service/start " \
					"$(agent agent/service/kill  --params "$(csap_credentials),services=csap-agent")" ;
				
			else
				delay_with_message 10 "Shutting down containers" ;
				shutdown_containers
				
				delay_with_message 10 "System is being updated" ;
				run_using_root $packageCommand --assumeyes $yumUpdateOptions update ;
				
				local returnCode=$? ;
				
			    if (( $returnCode == 0 )) ;  then
			        print_with_head "$packageCommand exited with 0 return code - but does not guarantee success. Review logs. ";
					print_command \
						"pushing csap-agent event: agent agent/service/event " \
						"$(agent agent/service/event --textResponse --params "$(csap_credentials),service=csap-agent,summary='os update completed'")" ;
			    else
			        print_error "$packageCommand error code: $returnCode, review logs";
					print_command \
						"pushing csap-agent event: agent agent/service/event " \
						"$(agent agent/service/event --textResponse --params "$(csap_credentials),service=csap-agent,summary='os update failed: $returnCode'")" ;
			    fi
				
				delay_with_message 10 "System is being restarted" ;
			
				if [ "$rebootHostAfterUpdates" == true ] ; then
					run_using_root reboot now ;
				else
					print_command \
						"restarting csap-agent: agent agent/service/start " \
						"$(agent agent/service/kill  --params "$(csap_credentials),services=csap-agent")" ;
				fi ;
			fi
		else
			print_error "Aborting Update: previous host failed to complete upgrade" ;
		fi ;
		
	fi ;

}
maintain_os ;

exit ;












