#!/bin/bash

source $STAGING/bin/csap-shell-utilities.sh

print_with_head "Exposing kubernetes api on port: '$csapPrimaryPort'"

existingProxyPid=$(pgrep -f "kubectl proxy")

if [[ $existingProxyPid ]] ; then 
	print_with_head "Found an existing pid '$existingProxyPid', running kill";
	# pkill
	/usr/bin/kill --signal SIGTERM $existingProxyPid
fi

args="proxy --port $csapPrimaryPort --address 0.0.0.0 --accept-hosts .*"

launch_background "kubectl" "$args" "$csapLogDir/kubectl_proxy.log"
