#!/bin/bash

#
#  install bash on windows: lxrun /install
#


scriptName=$(basename $0) ;

opensourceLocation="/mnt/c/dev/opensource" ;

echo "HOME: '$HOME', Working Directory: '$(pwd)' release: '$(cat /etc/redhat-release)'"

cat $HOME/.ssh/known_hosts

if [ -e installer/csap-environment.sh ] ; then

	scriptDir=$(pwd)
	echo "installer/csap-environment.sh" ;
	
elif [ -e ../environment/csap-environment.sh ] ; then

	cd ..
	scriptDir=$(pwd) ;
	
	echo "Desktop development using windows subsystem for linux, scriptDir: '$scriptDir'"
	
	opensourceLocation="/mnt/c/dev/opensource" ;
	windowsHome="/mnt/c/Users/peter.nightingale"
	
	if test -d $windowsHome/.ssh ; then
		if ! test -d $HOME/.ssh ; then
			cp --verbose --recursive --force $windowsHome/.ssh $HOME;
			chmod 700 $HOME/.ssh ; chmod 644 $HOME/.ssh/config ; chmod 600 ~/.ssh/id_rsa
		fi ;
		
		if ! test -d $HOME/opensource2 ; then
			ln -s  $windowsHome/opensource2 $HOME/opensource2
		fi
		
		if ! test -d $HOME/git ; then
			ln -s  $windowsHome/git $HOME/git 
		fi
	else
		echo "\n\n WARNING: create '$windowsHome/.ssh', '$windowsHome/opensource2', and '$windowsHome/git'"
	fi ;
	 
	
#	if [ -f installer/ssh-config ] ; then
#		mkdir --parents --verbose $HOME/.ssh ;
#		cp --verbose --force installer/ssh-config $HOME/.ssh/config;
#		chmod 700 $HOME/.ssh ; chmod 644 $HOME/.ssh/config
#		ln -s  $opensourceLocation $HOME/opensource 
#	fi ;
	
else
	echo "Desktop development using git bash: '$scriptDir'"
	source $scriptDir/platform-bin/csap-environment.sh
fi

ENV_FUNCTIONS=$scriptDir/environment/functions ;
source $scriptDir/environment/csap-environment.sh ;

testTarget="csap-01.root"
#testTarget="centos1.root"
#testTarget="csap-dev20.root"
#testTarget=${1:-centos1.root} ;

print_with_head "current directory: '$(pwd)'"

print_with_head "listing of installer folder"
ls installer


#ssh nightingale-one.root ls

print_with_head "remote listing '$testTarget' . Ensure ~/.ssh/config has alias added. Run remote-install.sh to see setup"
ssh $testTarget 'ls *'

ssh $testTarget 'ls * 2>&1'

exit

print_with_head "Cleaning up previous installs"
ssh $testTarget rm -rvf installer opensource platform-bin

print_with_head "Copying latest installer"
scp -r installer $testTarget:

scp -r $HOME/opensource/*.zip $testTarget:

ssh $testTarget ls -l 