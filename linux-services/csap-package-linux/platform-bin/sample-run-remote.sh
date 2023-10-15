#!/bin/bash

source $CSAP_FOLDER/bin/csap-environment.sh

remoteUser="csapUser"
remotePassword="changeMe" ;
csapClusterName="base-os"

# or: remoteHosts="my-host-1 my-host-3 ..."
remoteHosts=$(csap.sh -parseOutput -api model/hosts/$csapClusterName -script);
remoteCommands=(
     'ls'
     'ls -a'
     'ls \
     -a \
     -l'
   )
   
run_remote $remoteUser $remotePassword "$remoteHosts" "${remoteCommands[@]}"
