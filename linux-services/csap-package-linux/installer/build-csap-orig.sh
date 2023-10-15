#!/bin/bash


scriptDir=$(pwd)
scriptName=$(basename $0)
echo "Working Directory: '$(pwd)'"


if [ -e installer/csap-environment.sh ] ; then
	source installer/csap-environment.sh

elif [ -e ../environment/csap-environment.sh ] ; then

	cd ..
	scriptDir=$(pwd) ;
	
	echo "Desktop development using windows subsystem for linux: '$scriptDir'"
	ENV_FUNCTIONS=$scriptDir/environment/functions ;
	source $scriptDir/environment/csap-environment.sh ;
	
else
	echo "Desktop development"
	source $scriptDir/platform-bin/csap-environment.sh
fi




# change timer to 300 seconds or more
release="latest"; # or 2.0.0, etc

includePackages="no" ; # regex: use .* for all, or csap 
includeMavenRepo="no" ; # set to yes to include maven Repo
targetHost="do-not-copy"

releaseFolder="/mnt/CSAP_DEV01_NFS/csap-web-server" ;

buildScript="$csapPlatformWorking/csap-package-linux_0/installer/build-csap-functions.sh" ;


if [ $release != "updateThis" ] ; then
	
	print_with_head "Building '$release' , releaseFolder: '$releaseFolder'"
	$buildScript $release $includePackages $includeMavenRepo $targetHost $releaseFolder
	
	includePackages="csap" ; 
	includeMavenRepo="yes" ; 
	release="$release-full"
	
	#print_with_head Building $release , rember to use ui on csaptools to sync release file to other vm
	#$buildScript $release $includePackages $includeMavenRepo $targetHost
	
else
	
	print_with_head update release variable and timer
	
fi