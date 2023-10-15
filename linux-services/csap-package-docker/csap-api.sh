#!/bin/bash


function installation_settings() {
	
	if is_package_installed dnf ; then 
#		dockerPackage=${dockerPackage:-docker-ce-3:20.10.6-3.el8} ;
#		dockerCliPackage=${dockerCliPackage:-docker-ce-cli-1:20.10.6-3.el8} ;
		dockerPackage=${dockerPackage:-docker-ce-3:20.10.12-3.el8} ;
		dockerCliPackage=${dockerCliPackage:-docker-ce-cli-1:20.10.12-3.el8} ;
	else
#		dockerPackage=${dockerPackage:-docker-ce-20.10.6-3.el7} ;
#		dockerCliPackage=${dockerCliPackage:-docker-ce-cli-1:20.10.6-3.el7} ;
		dockerPackage=${dockerPackage:-docker-ce-20.10.12-3.el7} ;
		dockerCliPackage=${dockerCliPackage:-docker-ce-cli-1:20.10.12-3.el7} ;
	fi

	dockerStorage=${dockerStorage:-/var/lib/docker} ;
	dockerStorageDriver=${dockerStorageDriver:-overlay2} ;
	dockerRepo=${dockerRepo:-https://download.docker.com/linux/centos/docker-ce.repo} ;
	csapUser=$(whoami) ;
	
	allowRemote=${allowRemote:-false} ;
	
	# disable remote accesson specific hosts
	neverRemoteHosts=${neverRemoteHosts:-csap-dev01};
	local currentHostName=$(hostname --short)
	
	if [ $allowRemote == true ] ; then 
		if [[  "$neverRemoteHosts" == *$currentHostName* ]] ; then 
			allowRemote=false ;
			print_with_head "Found: '$currentHostName' in neverRemoteHosts: '$neverRemoteHosts'"
		else
			print_with_head "No match '$currentHostName' in neverRemoteHosts: '$neverRemoteHosts'"
		fi
	fi
	
	#
	# 
	#
	#if [[ $dockerPackage == docker-ce* ][ ; then
	dockerCommandsScript="root-commands.sh" ;
	#else
	#	dockerCommandsScript="docker-commands-rh.sh" ;
	#fi
	dockerCommandsFile=${commandsFile:-$csapWorkingDir/scripts/$dockerCommandsScript}
	
	print_section "CSAP docker package" ;
	print_two_columns "dockerPackage" "$dockerPackage" ;
	print_two_columns "dockerCliPackage" "$dockerCliPackage" ;
	print_two_columns "dockerRepo" "$dockerRepo" ;
	print_two_columns "dockerStorage" "$dockerStorage" ;
	print_two_columns "dockerStorageDriver" "$dockerStorageDriver" ;
	print_two_columns "allowRemote" "$allowRemote" ;
	print_two_columns "neverRemoteHosts" "$neverRemoteHosts" ;
	print_two_columns "dockerCommandsScript" "$dockerCommandsScript" ;

}

installation_settings


function api_package_build() { print_line "api_package_build not used" ; }

function api_package_get() { print_line "api_package_get not used" ; }

function api_service_kill() {

	print_with_head "api_service_kill()"

	if [ $isClean == "1" ] ||  [ $isSuperClean == "1"  ] ; then
		run_command clean ;
	else 
		run_command stop
	fi ;
	
	
}

#
# CSAP agent will always kill -9 after this command. For data sources - it is recommended to use the 
# shutdown command provided by the stack to ensure caches, etc. are flushed to disk.
#
function api_service_stop() {

	print_with_head "api_service_stop" 

	run_command stop
	
}

#
# startWrapper should always check if $csapWorkingDir exists, if not then create it using $packageDir
# 
function api_service_start() {
	
	# run before installation, so exit can be delayed if needed
	dockerMembershipFound=$(id $csapUser|grep docker|wc -l) ;
	
	print_with_head "api_service_start"
	
	# load any customizations
	copy_csap_service_resources ;
	
	# install only occurs if not already present 
	run_command install
	
	dockerMembershipFound=$(id $csapUser|grep docker|wc -l) ;
	
	run_command start
	
	dockerConfig="$csapWorkingDir/docker-os-references" ;
	
	if [ ! -e $dockerConfig ] ; then 
		
		print_line "Creating configuration shortcuts in $dockerConfig"
		mkdir -p $dockerConfig ;
		cd $dockerConfig ;
		
		add_link_in_pwd "/etc/docker"
		# add_link_in_pwd "/usr/lib/docker-storage-setup"
		add_link_in_pwd "$dockerStorage"
		#add_link_in_pwd "/etc/sysconfig/docker"
		#add_link_in_pwd "/var/lib/docker"
		
		createVersion
		
	fi ;
	
	cd $csapWorkingDir ;
    
	post_start_status_check ;
	
	dockerMembershipFound=$(id $csapUser|grep docker|wc -l) ;
	if [[ "$dockerMembershipFound" == 0 ]]; then
		run_using_root groupadd docker ;
		run_using_root gpasswd --add $csapUser docker;	
		# gpasswd --delete $csapUser docker
		
		touch $csapWorkingDir/restart-agent-for-docker-group
		print_with_head "'$csapUser' group membership updated - created file: '$csapWorkingDir/restart-agent-for-docker-group'"
		# run_using_root "systemctl restart csap"
		
	else
		print_with_head "'$csapUser' is a member of docker group":
	fi
	
}

function post_start_status_check() {

	print_with_head "post_start_status_check - id $(id)"
	
	source $csapWorkingDir/scripts/sanity-tests.sh
	
	status_tests
	
	verify_docker_run
	
}

variablesFile="$csapWorkingDir/install-variables.sh"
function build_variables_file() {

	rm --recursive --force --verbose $variablesFile
	
	append_file "# generated file" $variablesFile true
	
	# set verbose to false
	append_file "#" $variablesFile false
	
	append_line  export csapUser=$csapUser
	
	append_line  export csapWorkingDir="$csapWorkingDir"
	append_line  export csapPrimaryPort="$csapPrimaryPort"
	
	
	append_line  export dockerStorage="$dockerStorage" 
	append_line  export dockerStorageDriver="$dockerStorageDriver" 
	append_line  export allowRemote="$allowRemote" 
	append_line  export dockerPackage="$dockerPackage" 
	append_line  export dockerCliPackage="$dockerCliPackage" 
	
	append_line  export dockerRepo="$dockerRepo" 
}


function run_command() {
	command=$1
	#run_using_csap_root "$dockerCommandsFile" "$command" "$dockerStorage" "$allowRemote" "$dockerPackage" "$csapPrimaryPort" "$dockerRepo"
	
	build_variables_file ;
	print_with_head "'$variablesFile': \n$(cat $variablesFile)"
	
	run_using_csap_root_file "$command" "$dockerCommandsFile" "$variablesFile"
}

function createVersion() {
	
	packageVersion=`ls $csapWorkingDir/version | head -n 1`
	
	print_line "Prepending docker version to package version"
	
	dockerShortVersion=`docker --version | awk '{ print $3 }' | tr -d ,`
	
	
	myVersion="$dockerShortVersion--$packageVersion"
	
	print_line "Renaming version folder: $csapWorkingDir/version/$packageVersion to $myVersion"
	
	\mv -v "$csapWorkingDir/version/$packageVersion" "$csapWorkingDir/version/$myVersion" 

	
}


