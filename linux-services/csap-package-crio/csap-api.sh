#!/bin/bash


function installation_settings() {
	
	dockerStorage=${dockerStorage:-/var/lib/docker} ;
	podmanStorage=${podmanStorage:-/var/lib/containers} ;
	csapUser=${csapUser:-$(whoami)} ;
	
	
	installOs=${installOs:-CentOS_8} ; 
	installVersion=${installVersion:-1.21} ;
	

	print_section "CSAP crio package" ;
	print_two_columns "installOs" "$installOs" ;
	print_two_columns "installVersion" "$installVersion" ;
	print_two_columns "csapUser" "$csapUser" ;
	print_two_columns "podmanStorage" "$podmanStorage (if not a fs, will be linked to dockerStorage location)" ;
	print_two_columns "dockerStorage" "$dockerStorage" ;

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
	
	
	
	if [ $isClean == "1" ] ||  [ $isSuperClean == "1"  ] ; then
#		run_using_root dnf remove --assumeyes podman  # optional podman-docker 

		# full clean is done via podman
		root_csap_command clean_crio_containers
		
		run_using_root systemctl stop crio.service ;
		
		# root_csap_command clean_podman_and_crio
		
		run_using_root systemctl disable crio.service ;
		
		
		run_using_root dnf --assumeyes autoremove cri-o cri-tools
		
		run_using_root 'rm --verbose --force /etc/yum.repos.d/devel*'
		
		
		if test -L $podmanStorage  ; then
		
			print_command \
				"not deleting $podmanStorage link" \
				"$( ls -l $podmanStorage )"
				
		fi ;
		
		if test -d $podmanStorage  ; then
		
			print_command \
				"not deleting $podmanStorage folder" \
				"$( ls -l $podmanStorage )"
				
		fi ;
		
		if test -d $dockerStorage  ; then
		
			print_command \
				"not deleting $dockerStorage folder" \
				"$( ls -l $dockerStorage )"
		fi ;
		
	else 
	
		run_using_root systemctl stop crio.service ;
	
	fi ;
	
}

#
# startWrapper should always check if $csapWorkingDir exists, if not then create it using $packageDir
# 
function api_service_start() {
	
	print_with_head "api_service_start"
	
	
	if ! is_package_installed crio ; then 
	
		# load any customizations
		copy_csap_service_resources ;
		
			
		run_using_root curl -L -o /etc/yum.repos.d/devel:kubic:libcontainers:stable.repo https://download.opensuse.org/repositories/devel:/kubic:/libcontainers:/stable/$installOs/devel:kubic:libcontainers:stable.repo
		run_using_root curl -L -o /etc/yum.repos.d/devel:kubic:libcontainers:stable:cri-o:$installVersion.repo https://download.opensuse.org/repositories/devel:kubic:libcontainers:stable:cri-o:$installVersion/$installOs/devel:kubic:libcontainers:stable:cri-o:$installVersion.repo
		run_using_root yum install --assumeyes cri-o cri-tools
	
		run_using_root systemctl daemon-reload
		run_using_root systemctl enable crio --now
		
		#
		# post install of kubernetes ref: https://github.com/cri-o/cri-o/issues/4276
		# systemctl restart crio
		#
		#
		# or add to /etc/crio/crio.conf
		# Path to the directory where CNI configuration files are located.
		# network_dir = "/etc/cni/net.d/"
		#
		local crioConf="/etc/crio/crio.conf"
		
		if ! does_file_contain_word $crioConf "/etc/cni/net.d/" ; then 
			print_line "updating $crioConf"
			run_using_root sed --in-place "'/\[crio.network\]/a network_dir = \"/etc/cni/net.d/\"'" $crioConf
			
		else 
			print_line "already updated $crioConf"
			
		fi
		
		print_command "$crioConf" "$(cat $crioConf)"
	
		local crioReferences="$csapWorkingDir/crio-os-references" ;
	
		if [ ! -e $crioReferences ] ; then 
			
			print_line "Creating configuration shortcuts in $crioReferences"
			mkdir -p $crioReferences ;
			cd $crioReferences ;
			
			add_link_in_pwd "/etc/docker"
			add_link_in_pwd "$dockerStorage"
			add_link_in_pwd "$podmanStorage"
			add_link_in_pwd "/etc/containers"
			add_link_in_pwd "/etc/crio"
			add_link_in_pwd "/etc/cni"
			add_link_in_pwd "/var/run/containers"
			
			
			createVersion
		fi ;
		
		cd $csapWorkingDir ;
		
				
	fi
	
	run_using_root systemctl start crio.service

	delay_with_message 3 "delay for service start"
		

		
	print_command \
		"systemctl status crio" \
		"$( systemctl status crio )"
		
	post_start_status_check

	
}



function post_start_status_check() {
	
	$csapWorkingDir/scripts/sanity-tests.sh status_tests
	
	$csapWorkingDir/scripts/sanity-tests.sh run_tests
	
	
}


function createVersion() {
	
	local packageVersion=$(ls $csapWorkingDir/version | head -n 1)
	
	print_line "Prepending podman version to package version"
	
	local podmanVersion=$(podman --version  | awk '{ print $3 }' | tr -d ,)
	
	
	myVersion="$podmanVersion--$packageVersion"
	
	print_line "Renaming version folder: $csapWorkingDir/version/$packageVersion to $myVersion"
	
	mv --verbose "$csapWorkingDir/version/$packageVersion" "$csapWorkingDir/version/$myVersion" 

	
}


