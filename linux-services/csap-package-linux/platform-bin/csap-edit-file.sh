#!/bin/bash
#
#
#

if (( $# < 6 )) ; then 
	echo  "params: csapPlatformFolder updatedFile targetLocation linuxUserid csapUserid keepPermissions"
	exit ;
fi ;


csapPlatformFolder="$1";
updatedFile="$2" ;
targetLocation="$3"
linuxUserid="$4" ;
csapUserid="$5"
isKeepPermissions="$6"

csapPlatformEnvironment="$csapPlatformFolder/bin/csap-environment.sh" ;
csapPlatformDefinition="$csapPlatformFolder/definition" ;

if [ -e  $csapPlatformEnvironment ] ; then 
	source $csapPlatformEnvironment ;
else
	echo "Exiting - unable to locate csapPlatformEnvironment: '$csapPlatformEnvironment'" ;
	exit ;
fi

isUpdateToDefinitionFolder=false;
if [[ "$targetLocation" == $csapPlatformDefinition/* ]] ; then
	isUpdateToDefinitionFolder=true;
fi ;

# params: tempLocation, targetLocation, targetUnixOwner, linuxUserid


print_separator "Updating $targetLocation"
print_two_columns "user" "$csapUserid"
print_two_columns "source" "$updatedFile"
print_two_columns "chown" "$linuxUserid"

if [[ $isKeepPermissions != "true" ]] ; then
	print_two_columns "dos2unix" "ensuring uploaded file is using linux line format"
	convertOutput=$(dos2unix $updatedFile 2>&1)
fi ;

if  [ "$USER" == "root" ] && [ "$linuxUserid" != "root" ] ; then
	chown -R $linuxUserid $updatedFile
	chgrp -R $linuxUserid $updatedFile
else 
	print_two_columns "mode" "running in non root mode"
fi ; 
	
chmod 755 $updatedFile
NOW=$(date +"%h-%d-%I-%M-%S")
backup="$targetLocation-$csapUserid-$NOW"

 if [ -e "$targetLocation" ] ; then 
 	
 	if $isUpdateToDefinitionFolder ; then
 		print_two_columns "definition" "use csap editor to check in changes" ;
	else
		print_two_columns "backup" "original file copied to: '$backup'" ;
		\cp --force "$targetLocation" "$backup" ;
		
		if  [ "$USER" == "root" ] && [ "$linuxUserid" != "root" ] ; then
			chown -R $linuxUserid "$backup"
			chgrp -R $linuxUserid "$backup"
		fi ;
	fi
	
fi 

if [[ $isKeepPermissions == "true" ]] ; then
	print_two_columns "permissions" "keep permissions is true, using 'cat > existing file'" ;
	cat "$updatedFile" > "$targetLocation" ;
else
	print_two_columns "permissions" "keep permissions is false, using 'cp --force' to overwrite" ;
	\cp --force "$updatedFile" "$targetLocation" 
	if  [ "$USER" == "root" ] && [ "$linuxUserid" != "root" ] ; then
		print_two_columns "chown" "chown -R $linuxUserid" ;
		chown -R $linuxUserid "$targetLocation"
	fi ;
	
fi ;

\rm --recursive --force "$updatedFile" ;



