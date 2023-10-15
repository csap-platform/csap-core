#!/bin/bash

scriptDir=$(realpath $(dirname -- $0))
scriptName=$(basename $0)

# "loading $scriptDir/installer-common-functions.sh"
source $scriptDir/installer-common-functions.sh

if $isRunUninstall ; then	
	uninstallCsap ;
	
else

	if [ "$targetFs" == "0" ] ; then
	      
		#print_two_columns "filesystem" "default"
		coreInstall
		
	else 
		#print_two_columns "filesystem" "target"
		# echo == Click enter to continue
		#read progress
		targetInstall $origParams
	fi ;
	
	
	
	if [ "$csapFs" != "0" ] ; then
	     
		csap_user_install $origParams
		
	fi ;
fi ;


