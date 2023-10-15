#!/bin/bash

info="Learn more about docker-ce: https://docs.docker.com/install/linux/docker-ce/centos/" ;
scriptName="docker-commands-ce.sh"

function command_settings() {
	
	helperFunctions=${1:-/opt/csap/csap-platform/bin/csap-environment.sh} ;
	source $helperFunctions;
	command=$2
	
	dockerStorage=${dockerStorage:-/var/lib/docker}
	dockerStorageDriver=${dockerStorageDriver:-overlay2}
	docker_preserve_storage=$dockerStorage/preserve-over-reinstalls
	allowRemote=${allowRemote}
	dockerPackage=${dockerPackage:-docker-ce}
	dockerPort=${csapPrimaryPort:-4243}
	dockerRepo=${dockerRepo:-https://download.docker.com/linux/centos/docker-ce.repo}
	
	print_with_head "user: '$csapUser',  script: '$scriptName': '$command',\t dockerStorage: '$dockerStorage',\t  allowRemote: '$allowRemote'" \
		"\n\t dockerPackage: '$dockerPackage',\t dockerRepo: '$dockerRepo'" \
		"\n\t $info"
}

command_settings $* ;

function install() {
	
	rpm -q $dockerPackage ;
	if [ "$?" == 0 ] ; then 
		print_line "$dockerPackage was found, skipping install"
		return ;
	fi;
	
	print_with_head "Starting install of $dockerPackage, ref: 'https://docs.docker.com/install/linux/docker-ce/centos/'"
		
	if [ "$USER" == "root" ] ; then

		# https://docs.docker.com/install/linux/docker-ce/centos/#install-using-the-repository
		
		add_repo_with_setup_checks $dockerRepo
		
		if is_package_installed dnf ; then 
			print_two_columns "centos8 notes" "https://www.linuxtechi.com/install-docker-ce-centos-8-rhel-8/"
			print_separator "dnf installing $dockerPackage"
			dnf --assumeyes  install \
				$dockerPackage
				
			exit_on_failure $? "Failed to install docker";
				
			
			print_command \
				"dnf version lock: '$dockerPackage $dockerCliPackage'" \
				"$(dnf versionlock $dockerPackage $dockerCliPackage)" 
		
		else
			print_separator "yum installing $dockerPackage $dockerCliPackage"
			yum --assumeyes install \
				$dockerPackage $dockerCliPackage
				
				
			exit_on_failure $? "Failed to install docker";
				
			
			print_command \
				"yum version lock: '$dockerPackage $dockerCliPackage'" \
				"$(yum versionlock $dockerPackage $dockerCliPackage)" 
		fi
		
		# print_with_head "downgrading package  'containerd.io-1.2.2-3.el7.x86_64' due to selinux bug"
		# yum --assumeyes downgrade \
		#	containerd.io-1.2.2-3.el7.x86_64
		
		local dockerConfigurationFile="/etc/docker/daemon.json";
		print_separator "Updating docker settings:'$dockerConfigurationFile'"
		
		local csapDockerTemplate="$csapWorkingDir/configuration/daemon.json"
		
		backup_and_replace $dockerConfigurationFile $csapDockerTemplate  ;

		remoteAllowParam="" ;
		if [ "$allowRemote" == "true" ] ; then 
			remoteAllowParam="tcp://0.0.0.0:$dockerPort" ;
			print_line "WARNING: Exposing host to remote connections: '$remoteAllowParam'"
		fi
		
		replace_all_in_file "_CSAP_ALLOW_REMOTE_" "$remoteAllowParam" $dockerConfigurationFile
		replace_all_in_file "_CSAP_STORAGE_" "$dockerStorage" $dockerConfigurationFile
		replace_all_in_file "_CSAP_DRIVER_" "$dockerStorageDriver" $dockerConfigurationFile
		
		print_command \
			"configuration file: '$dockerConfigurationFile'" \
			"$(cat $dockerConfigurationFile)" 
		
		
		if [ -e $dockerStorage ] ; then
			print_line "Found existing dockerStorage '$dockerStorage'."
		else
			print_line "Creating dockerStorage: '$dockerStorage'"
			mkdir --parents --verbose $dockerStorage
		fi
		
		local systemdStartFile="/etc/systemd/system/docker.service.d/docker.conf"
		if [[ ! -e $systemdStartFile ]]; then
			print_two_columns "configuration" " creating file: '$systemdStartFile'" ;
    		mkdir --parents --verbose $(dirname ${systemdStartFile})

			local disableIpTables=""; 
#			if is_package_installed dnf ; then
#				print_two_columns "found dnf" "disabling iptables integration"
#				disableIpTables="--iptables=false";
#			fi ;
    		cat >$systemdStartFile <<EOF
[Service]
ExecStart=
ExecStart=/usr/bin/dockerd --containerd=/run/containerd/containerd.sock $disableIpTables
EOF
            systemctl daemon-reload
        else
			print_two_columns "configuration" " found existing file: '$systemdStartFile'" ;
        fi


        print_command \
			"configuration: '$systemdStartFile'" \
			"$(cat $systemdStartFile)"
		
		print_line "Creating preserve file: '$docker_preserve_storage'. Use file manager or run clean to remove"
		touch "$docker_preserve_storage"

	else
		print_with_head ROOT access is require to install. contact sysadmin
	fi ;
	

}


function start() {
	
	print_with_head "starting docker and enabling via systemctl"
	
	systemctl daemon-reload
	systemctl start docker
	systemctl enable docker
	
	sleep 3

	print_with_head "systemctl status docker.service"
	systemctl status docker
		
}

function clean() {
	
	print_with_head "Clean was specified: all docker containers will be stopped and all images removed"
	
	clean_docker

	if [ -f $docker_preserve_storage ] ; then
		print_with_head "Found '$docker_preserve_storage' file - contents will be preserved. To delete, use the CSAP FileManger."
		
	else
		print_with_head "No '$docker_preserve_storage' file - contents will be DELETED."
		umount_containers $dockerStorage ;
		\rm -rf $dockerStorage/*
	fi

	# /opt/csap/csap-platform/working/httpd/restore-port-tunnels.sh
	
	check_for_webserver_port_restore ;

	
	# print_with_head "NOT Removing routes with 192.168"
	# ip route show | sed -e 's/[[:space:]]*$//' | grep 192.168 | xargs --max-lines=1 ip route del
	
	print_with_head "ip route list. To clean up: 'ip route del <line-from-list>'"
	ip route list
	
}

function stop() {



#	local numberOfRunningContainers=$(docker ps -a -q | wc -l) ;
#	if (( $numberOfRunningContainers != 0 )) ; then
#		print_with_head "Stopping docker containers"
#		docker ps -a -q
#		docker stop $(docker ps -a -q) ;
#	fi ;

	print_with_head "Stopping docker using systemctl: docker.service"
	
	print_command \
		"docker containers" \
		"$(docker ps)"
		
	systemctl stop docker
	systemctl disable docker


}

#print_with_head "Running $command"

case "$command" in
	
	install)
		install
		;;
	
	clean)
		clean
		;;
	
	stop)
		stop
		;;
	
	start)
		start
		;;
	
	 *)
            echo $"Usage: $0 {install|start|stop|restart|clean}"
            exit 1
esac
