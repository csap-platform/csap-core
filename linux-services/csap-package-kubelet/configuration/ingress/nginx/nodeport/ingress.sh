#!/bin/bash

source $STAGING/bin/csap-shell-utilities.sh

scriptDir=$(dirname $0)

csapWorkingDir=$csapPlatformWorking/kubelet_8014
functions="$csapWorkingDir/configuration/ingress/ingress-functions.sh"
print_with_head "loading functions: '$functions'"
source $functions

# apply or create or delete
ingress_installer create

