#!/bin/bash 



newApiFile="$csapWorkingDir/csap-api.sh";
print_two_columns "csap api" "checking for $newApiFile"

if [ "$skipApiExtract" == "" ] &&  [ ! -e $newApiFile ] ; then
	
	print_two_columns "extracting" "$csapPackageFolder/$csapName.zip to $csapWorkingDir"
	
	if [ -e $csapWorkingDir/version ] ; then
		print_two_columns "Removing" "$csapWorkingDir/version"
		\rm -rf  $csapWorkingDir/version
	fi;

	/usr/bin/unzip -o -qq $csapPackageFolder/$csapName.zip -d $csapWorkingDir
	
	if test -e $csapWorkingDir/scripts  ; then
		print_two_columns "scripts" "$(ensure_files_are_unix $csapWorkingDir/scripts)"
		
	fi ;
	print_two_columns "permissions" "running chmod -R 755 $csapWorkingDir"
	chmod -R 755 $csapWorkingDir
fi ;

if test -e $newApiFile ; then 
	print_two_columns "Loading" "$(basename $newApiFile)" ;
	source $newApiFile ;
	apiFound="true";
		
else 
	print_two_columns "csap api not found" "this is ok if service has already been deleted"
	apiFound="false";
fi;