#!/bin/bash

source $CSAP_FOLDER/bin/csap-environment.sh


print_with_date "Running clean up"

print_with_head "running docker system prune"
docker system prune --force 


numHours=${dockerGcHours:-24}
export PID_DIR="$csapWorkingDir/scripts"; 
export STATE_DIR="$csapWorkingDir/docker-gc-state"
hoursInSeconds=$((60*60)) ; export GRACE_PERIOD_SECONDS=$(($numHours*$hoursInSeconds))

print_with_head "running docker-gc: images older then '$numHours' hours will be removed"
$csapWorkingDir/scripts/docker-gc.sh
