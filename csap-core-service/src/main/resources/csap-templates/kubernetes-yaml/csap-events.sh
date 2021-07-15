#!/bin/bash

#source $STAGING/bin/csap-env.sh

function pre_deploy() {
	
	print_with_head "Running pre_deploy" ;
	
}

function post_deploy() {
	
	print_with_head "Running post_deploy" ;
	
}


function pre_start() {
	
	print_with_head "Running pre_start" ;
	
} 

function post_start() {
	
	print_with_head "Running post_start" ;
	
}

function pre_stop() {
	
	print_with_head "Running pre_stop" ;
	
}

function post_stop() {
	
	print_with_head "post_stop" ;
	
}

function samples() {
	
	# Sample pulling external dependencies onto service csapPackageFolder.
	print_line "removing '$csapPackageDependencies'"
	\rm -rf $csapPackageDependencies
	
	mkdir -p $csapPackageDependencies
	cd $csapPackageDependencies
 	wget -nv "http://$toolsServer/java/"$jdkVersion"_linux-x64_bin.tar.gz"
 	print_line extracting  $packageDir/jdk-8u$minorVersion-linux-x64.tar.gz to `pwd`
 	tar -xzf $packageDir/jdk-8u$minorVersion-linux-x64.tar.gz
	
	# Sample: running command using root
	run_using_root 'pwd;ls -al'
	
	# Sample: Add nfs mount
	mount_target=${mount_target:-/mnt/CSAP_DEV01_NFS}
	mount_source=${mount_source:-10.22.10.59:/CSAP_DEV01_NFS}
	package="nfs-utils" ;
	
	if is_need_package $package ; then
		run_using_root "yum -y  install $package"
		print_line "\n\n"
	fi ;
	
	if [ ! -d $mount_target ] ; then
		print_with_head "Creating nfs mount point: '$mount_target'" ;
		run_using_root "mkdir -p $mount_target" ;
	fi
	
	print_line "removing mount target if it already exists"
	run_using_root sed -i "'\|$mount_target|d'" /etc/fstab
	
	fstab_entry="$mount_source     $mount_target     nfs     vers=3  0 0"
	
	fstab_entry="$mount_source     $mount_target     nfs     vers=3  0 0"
	print_with_head "Adding nfs mount to /etc/fstab: '$fstab_entry'"
	
	run_using_root "echo $fstab_entry >> /etc/fstab"
	run_using_root "mount $mount_target"
	
	# to uninstall
	run_using_root umount $mount_target
	run_using_root sed -i "'\|$mount_target|d'" /etc/fstab
	
}

function perform_operation() {

	case "$csapEvent" in
		
		"event-pre-deploy")
			pre_deploy
			;;
		
		"event-post-deploy")
			post_deploy
			;;
		
		"event-pre-start")
			pre_start
			;;
		
		
		"event-post-start")
			post_start
			;;
		
	
		"event-pre-stop")
			pre_stop
			;;
		
		"event-post-stop")
			post_stop
			;;
		
		 *)
	            echo "Usage: $0 {start|stop|restart|clean}"
	            exit 1
	esac

}

# uncomment to invoke specific command interactively; note no CSAP variables will be set 
# csapEvent="pre_start"
perform_operation

