#!/bin/bash
#
# This does both zip and unzips of files , Triggered by TransferManager.java
#

scriptDir=$(dirname $0)
source $scriptDir/csap-environment.sh

print_separator "$0"

csapFolder=$(dirname $scriptDir) ;
print_if_debug "csapFolder: '$csapFolder'"

numberOfArguments=$# ;
if (( $numberOfArguments == 2 )) ; then

	#
	#  TransferManager - used to compress items before sending to agents on other hosts
	#
	
	tarBaseDir=$1
	tarFileName=$2
	tarTargetDir="."
	
	
	if test -f $tarBaseDir ; then  
		# hook for files
		tarBaseDir=$(dirname $1) ;
		tarTargetDir=$(basename $1) ;
	fi ;
	
	print_two_columns "building" "$tarFileName"
	print_two_columns "source" "$tarBaseDir"
	print_two_columns "working" "$(pwd)"
	
	
	#tar Pcvzf $2 --directory $tarBaseDir $tarTargetDir
	tar --preserve-permissions --create --verbose --gzip --file $tarFileName --directory $tarBaseDir $tarTargetDir
	
	if  [ "$USER" == "root" ]; then
		targetUser=$(ps -ef | grep $csapAgentId | grep -v grep | tail -1 | awk '{ print $1 }');
		chown $targetUser $2 ;
	fi ;
	
	
elif (( $numberOfArguments == 3 )) ; then

	#
	#  OsManger - used to decompress items compressed
	#

	compressedFile="$1"  ;
	extractLocation="$2" ;
	ownedByUser="$3" ;
	
	print_two_columns "decompressing" "$compressedFile"
	print_two_columns "extractLocation" "$extractLocation"
	print_two_columns "ownedByUser" "$ownedByUser"
	
	
	if [[ $extractLocation == $csapFolder/*.secondary ]] ; then
	
		print_two_columns "csap-packages secondary" "$(\rm --recursive --verbose --force $extractLocation/*  2>&1)"
		
	fi ;
	
	mkdir --parents $extractLocation
	
	if [[ $compressedFile == *.tgz ]] ; then 
		#tar Pxvzf $compressedFile --directory $extractLocation
		tar --preserve-permissions --extract --verbose --gzip --file $compressedFile --directory $extractLocation
	else 
		unzip -o $compressedFile -d $extractLocation 
	fi ;

	if  [ "$USER" == "root" ]; then
		print_two_columns "root" "updating permissions to $ownedByUser"
		chown -R $ownedByUser $extractLocation 
		chgrp -R $ownedByUser $extractLocation
	else
		print_two_columns "user" "$(whoami)"
	fi ;
	
	print_two_columns "Removing" "$(\rm --recursive --verbose --force $compressedFile 2>&1)"
	
elif (( $numberOfArguments == 4 )) ; then


	#
	#  Unused ?
	#
	
	source="$1" ;
	destination="$2"
	ownedByUser="$3" ;
	print_two_columns "source" "$source"
	print_two_columns "destination" "$destination"
	print_two_columns "ownedByUser" "$ownedByUser"
	
	mkdir --parents $destination
	\cp -f $source $destination 
	
	if [[ $destination == $csapFolder/* ]] ; then
		# Changing root file permissions can get very dicey. we only do on $CSAP_FOLDER Files
		print_line "Detected csap install folder performing chmod 755 on $destination"
		chmod -R 755 $destination 
	fi ;
	
	if  [ "$USER" == "root" ]; then
		print_two_columns "root" "updating permissions to $ownedByUser"
		chown -R $ownedByUser $destination 
		chgrp -R $ownedByUser $destination
	else
		print_two_columns "user" "$(whoami)"
	fi ;
	
	\rm -rf $source
	
else 

	add_note start
	
	add_note "zip params: sourceLocation, zipLocation"
	add_note "unzip params: tempLocation, targetLocation, targetUnixOwner"
	add_note "copy params: tempLocation, targetLocation, targetUnixOwner , isCopy"
	
	add_note end
	
	print_line "$add_note_contents" ;
	
fi ;

