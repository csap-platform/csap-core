#!/bin/bash


function copy_remote() {
	local user="$1";
	local password="$2";
	local hosts="$3";
	local fileToCopy="$4";
	local destination=${5:-$(dirname $fileToCopy)};
	
	if $(is_need_package sshpass) ; then
		run_using_root yum --assumeyes install sshpass
	fi ;
	
	exit_if_not_installed sshpass
	
	
	for targetHost in $hosts; do
		
		print_two_columns	"$targetHost" "copying '$fileToCopy' to '$destination'" ;
		
		hostOutput=$(sshpass -p $password scp -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null $fileToCopy $user@$targetHost:$destination 2>&1)
		connection_return_code="$?" ;
		
		print_if_debug $targetHost "$hostOutput"
		
		
		if (( $connection_return_code != 0 )) ; then
			print_two_columns "$targetHost" "FAILED"
			failureCount=$(( $podCount + 1)) ;
		else
			print_if_debug "$targetHost" "PASSED"
		fi ;

	done ;
	
	
}

function run_remote() {
	
	local user="$1";
	local password="$2";
	local hosts="$3";
	
	# handle array
	shift;shift;shift
	local commands=("$@");
	
	if $(is_need_package sshpass) ; then
		run_using_root yum --assumeyes install sshpass
	fi ;
	
	exit_if_not_installed sshpass
	
	tempFolder="$(pwd)/remote-"$(date +"%h-%d-%I-%M-%S")
	
	print_line "Creating folder to avoid wildcard matches: '$tempFolder'"
	mkdir --parents $tempFolder
	cd $tempFolder
	
	
	for targetHost in $hosts; do
	
		for command in "${commands[@]}"; do
			print_with_head	"$targetHost: running '$command'" ;
			sshpass -p $password ssh -o StrictHostKeyChecking=no $user@$targetHost $command
		done
		
	done ;
	
	\rm -rf $tempFolder
	
}


function wait_for_curl_success() {

	local curl_parameters=${1:---request GET http://www.cnn.com} ;
	local use_jq=${2:-true} ;
	local max_poll_result_attempts=${3:-100} ;
	local currentAttempt=1;
	
	local responseBody ;
	local returnCode=99 ;
	
	print_separator "Waiting for a linux return code of 0" ;
	for currentAttempt in $(seq 1 $max_poll_result_attempts); do
    
		responseBody=$(curl --silent --show-error $curl_parameters 2>&1) ;
		returnCode="$?"
		
		print_command \
		  "attempt $currentAttempt of $max_poll_result_attempts: returnCode - '$returnCode'" \
		  "$(echo "$responseBody" )"
    
		if (( $returnCode == 0 )) ; then
			break ;
		fi ;
        sleep 5 ;
	done ;
	
	if (( $returnCode == 0 )) ; then
		print_separator "Waiting for a http return code of 200" ;
		for currentAttempt in $(seq 1 $max_poll_result_attempts); do
	    
			responseBody=$(curl --silent --output /dev/null --write-out "%{http_code}" $curl_parameters 2>&1) ;
			returnCode="$?"
			print_command \
			  "attempt $currentAttempt of $max_poll_result_attempts: returnCode - '$returnCode'" \
			  "$(echo -e "http status code: $responseBody" )"
	    
			if (( $responseBody == 200 )) ; then
				break ;
			fi ;
	        sleep 5 ;
		done ;
	fi ;
	
	
	if (( $returnCode == 0 )) \
		&& (( $responseBody == 200 )) \
		&& [[ "$use_jq" == true ]] ; then
		
		
		print_command \
		  "Successful request" \
		  "$(curl --silent  $curl_parameters | jq )"
		  
	fi

}

#
#  NFS utils
#

function is_nfs_mounted() {
	local mount_source="$1";
	timeout 5s df --human-readable | grep "$mount_source" 2>&1 >/dev/null
	if [ $? == 0 ] ; then
		true ;
	else 
		false ;
	fi ;
}
	
function nfs_add_mount() {
	
	local mount_source="$1";
	local mount_target="$2";
	local mount_options="${3:-'vers=3'}"
	local exitIfPresent="${4:-true}";
	local package=${5:-nfs-utils} ;
	
	#
	# escape when invoked directly without setting parameters
	#
	if [ "$mount_source" == "none" ] \
		|| [[ "$mount_source" == *nfs_server*nfs_path ]] \
		|| [[ "$mount_source" == *nfs-server*nfs-path ]] ; then
		print_two_columns "mount source" "requested is '$mount_source'. Skipping install" ;
	    return ;
	fi;
	
		
	print_with_head "nfs_add_mount() - mount source: '$mount_source',  mount_target: '$mount_target', mount_options: '$mount_options', exitIfPresent: $exitIfPresent" ;
	
	if [ "$mount_target" == "" ] || [ "$mount_source" == "" ] || [ "$mount_source" == ":" ]; then
		print_with_head "Missing required parameters" ;
	    exit;
	fi
	
	
	if $(is_nfs_mounted $mount_source) ; then
		if [ $exitIfPresent == true ] ; then
			print_with_head "INFO: existing mount found '$mount_source', skipping remaining commands."
			return;
		fi
		print_with_head "WARNING: existing mount detected '$mount_source'"
	fi ;

	if is_need_package $package ; then
		run_using_root "yum --assumeyes  install $package"
		print_line "\n\n"
	fi ;
	
	if [ ! -d $mount_target ] ; then
		print_with_head "Creating nfs mount point: '$mount_target'" ;
		run_using_root "mkdir -p $mount_target" ;
	fi
	
	print_line "removing mount target if it already exists"
	run_using_root sed -i "'\|$mount_target|d'" /etc/fstab
	
	fstab_entry="$mount_source     $mount_target     nfs     ${mount_options}  0 0"
	print_with_head "Adding nfs mount to /etc/fstab: '$fstab_entry'"
	
	run_using_root "echo -e '\n'$fstab_entry'\n' >> /etc/fstab"
	run_using_root "timeout 5s mount $mount_target"
	
	if ! $( is_nfs_mounted "$mount_source" ) ; then
		print_with_head "$csapDeployAbort: error in nfs_add_mount(): mount failed: '$mount_source'"
		exit;
	fi ;
}

function nfs_remove_mount() {
	
	local mount_target="$1";
	local uninstallPackage="${2:-false}";
	local package=${3:-nfs-utils} ;
	
	print_with_head "nfs_remove_mount() -  mount_target: '$mount_target', uninstallPackage: '$uninstallPackage' package: '$package'" ;
	
	if ! $( is_nfs_mounted "$mount_target" ) ; then
		print_with_head "WARNING: Did not find: '$mount_target'"
	fi ;
	
	run_using_root "timeout 5s umount $mount_target" ;
	run_using_root "timeout 5s umount --lazy $mount_target" ;
	
	print_line "removing '$mount_target' from /etc/fstab"
	run_using_root sed -i "'\|$mount_target|d'" /etc/fstab
	
	if $(is_package_installed $package) && [ $uninstallPackage == true ] ; then
		print_with_head "INFO: removing '$package'"
		run_using_root yum --assumeyes  remove $package
	fi
	
	print_line "Removing duplicate blank lines from /etc/fstab"
	run_using_root sed --in-place "'$!N;/^\n$/{$q;D;};P;D;'" /etc/fstab
	
}
