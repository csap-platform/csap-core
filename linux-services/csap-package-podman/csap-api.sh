#!/bin/bash


function installation_settings() {
	

	dockerStorage=${dockerStorage:-/var/lib/docker} ;
	podmanStorage=${podmanStorage:-/var/lib/containers} ;
	exposeService=${exposeService:-no} ;
	
	# requires: csap-core.docker.url: "unix:///var/run/podman/podman.sock"
	podmanSocketFolder=${podmanSocketFolder:-/var/run/podman} ; 
	csapUser=${csapUser:-$(whoami)} ;
	

	print_section "CSAP podman package" ;
	print_two_columns "podmanStorage" "$podmanStorage (if not a fs, /etc/containers/storage.conf will be updated to dockerStorage)" ;
	print_two_columns "dockerStorage" "$dockerStorage" ;
	print_two_columns "csapUser" "$csapUser" ;
	print_two_columns "podmanSocketFolder" "$podmanSocketFolder" ;
	print_two_columns "exposeService" "$exposeService (if yes, will be on port $csapPrimaryPort)" ;

}

installation_settings


function api_package_build() { print_line "api_package_build not used" ; }

function api_package_get() { print_line "api_package_get not used" ; }

function api_service_kill() {

	print_with_head "api_service_kill()"

	api_service_stop ;

}

#
# CSAP agent will always kill -9 after this command. For data sources - it is recommended to use the 
# shutdown command provided by the stack to ensure caches, etc. are flushed to disk.
#
function api_service_stop() {

	print_with_head "api_service_stop" 

	
	run_using_root "setfacl --remove user:$csapUser $podmanSocketFolder"
	
	run_using_root "getfacl --absolute-names $podmanSocketFolder"
		
	run_using_root "kill -9 $csapPids"
	
	
	if [ "$isClean" == "1" ] ||  [ "$isSuperClean" == "1"  ] ; then
#		run_using_root dnf remove --assumeyes podman  # optional podman-docker 

		root_csap_command clean_podman_and_crio
		
		# invokes restore-port-tunnels.sh in httpd
		check_for_webserver_port_restore ;
		
		
		root_csap_command umount_containers /run/containers/storage
		
		if test -d $podmanStorage  ; then
			root_csap_command umount_containers $podmanStorage
			
			
			
			if ! df  | grep $podmanStorage  ; then
			
				root_csap_command rm --recursive --force $podmanStorage
				
			else
				
				root_csap_command rm --recursive --force $podmanStorage/*
			  
		  	fi ;
		fi ;
		
		
		if test -d $dockerStorage  ; then
			root_csap_command umount_containers $dockerStorage
			root_csap_command rm --recursive --force $dockerStorage/*
		fi ;
		
		
		
	fi ;
	
}

#
# startWrapper should always check if $csapWorkingDir exists, if not then create it using $packageDir
# 
function api_service_start() {
	
	print_with_head "api_service_start"
	
	
	if ! is_package_installed podman ; then 
	
		# load any customizations
		copy_csap_service_resources ;
		
		
		run_using_root yum install --assumeyes podman  # optional podman-docker
		
		
		
			
		if ! df  | grep $podmanStorage  \
		  && df  | grep $dockerStorage ; then
		  
			print_section "legacy storage support: recommend replacing $dockerStorage with $podmanStorage"
#			run_using_root ln --verbose --symbolic $dockerStorage $podmanStorage
			root_csap_command replace_all_in_file "$podmanStorage" "$dockerStorage" /etc/containers/storage.conf ;
				
		fi ;
		
		
		
		local podmanReferences="$csapWorkingDir/podman-os-references" ;
	
		if [ ! -e $podmanReferences ] ; then 
			
			print_with_head "Creating configuration shortcuts in $podmanReferences"
			mkdir -p $podmanReferences ;
			cd $podmanReferences ;
			
			add_link_in_pwd "/etc/docker"
			add_link_in_pwd "$dockerStorage"
			add_link_in_pwd "$podmanStorage"
			add_link_in_pwd "/etc/containers"
			add_link_in_pwd "/etc/crio"
			add_link_in_pwd "/etc/cni"
			
			createVersion
			
		fi ;
		
		
		cd $csapWorkingDir ;
		
				
	fi

	
	cd $csapWorkingDir ;
	
	if test -d $csapWorkingDir/configuration/etc-containers ; then
		print_section "Copying configuration $csapWorkingDir/configuration/etc-containers to /etc/containers"
		run_using_root "cp --verbose --force $csapWorkingDir/configuration/etc-containers/* /etc/containers"
	fi ;
	
	if ! test -d $csapLogDir ; then
	
		mkdir --parents --verbose $csapLogDir ; 
	
	fi ;
    
	post_start_status_check ;
	
	
	local listener="tcp:$(hostname --long):4243"  # set to empty to use

#	podman system service $listener --log-level=info --time=0 2>&1 

	local appendlogs="no";
	local useRoot="yes" ;
	launch_background "podman" "system service --log-level=info --time=0" "$csapLogDir/$csapName.log" $appendlogs  $useRoot
	
	
	delay_with_message 3 "updating permissions on $podmanSocketFolder for docker api access"
		
	run_using_root "setfacl --recursive --modify user:$csapUser:rwx $podmanSocketFolder"
	
	run_using_root "getfacl --absolute-names $podmanSocketFolder" ;
	
	if [ "$exposeService" == "yes" ] ; then
		local listener="tcp:$(hostname --long):$csapPrimaryPort"  # set to empty to use $podmanSocketFolder
		launch_background "podman" "system service $listener --log-level=info --time=0" "$csapLogDir/$csapName-public.log" $appendlogs  $useRoot
		
	fi ;

	
}

function post_start_status_check() {

	
	$csapWorkingDir/scripts/sanity-tests.sh status_tests
	
	$csapWorkingDir/scripts/sanity-tests.sh verify_podman_run
	
	
	if $(is_process_running podman) ; then
		print_two_columns "pkill podman" "$(root_csap_command pkill -SIGKILL --full podman)"
	fi ;
	
	
}




function createVersion() {
	
	local packageVersion=$(ls $csapWorkingDir/version | head -n 1)
	
	print_line "Prepending podman version to package version"
	
	local podmanVersion=$(podman --version  | awk '{ print $3 }' | tr -d ,)
	
	
	myVersion="$podmanVersion--$packageVersion"
	
	print_line "Renaming version folder: $csapWorkingDir/version/$packageVersion to $myVersion"
	
	mv --verbose "$csapWorkingDir/version/$packageVersion" "$csapWorkingDir/version/$myVersion" 

	
}


