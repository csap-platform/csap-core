#!/bin/bash
#
#
#


scriptDir=$(dirname $0)

source $scriptDir/csap-environment.sh

# make sure we are in a stable directory. Running this from processing can cause handles to go stale.

cd $CSAP_FOLDER;

numberOfArguments=$# ;
if (( $numberOfArguments == 0 )) ; then
	
	print_with_head "WARNING: you should aways specify a host to clone configuration from. This host may be out of sync with cluster which can lead to problems"

elif (( $numberOfArguments == 1 )) ; then
	
	# this will exit if host does not exist
	set -e
	cloneHost="$1"
	print_with_head "cloneHost specified: $cloneHost , using wget http://$cloneHost:8011/$csapAgentName/os/definitionZip"

 	rm -rf definitionZip*
 	wget http://$cloneHost:8011/os/definitionZip
 	
 	print_with_head "removing existing definition: $CSAP_FOLDER/definition"
 	rm -rf $CSAP_FOLDER/definition
 	unzip -o -d $CSAP_FOLDER/definition definitionZip
fi ;

# set -x verbose

csap-kill.sh -d

