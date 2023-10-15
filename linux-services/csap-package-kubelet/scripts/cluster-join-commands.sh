#!/bin/bash

source $CSAP_FOLDER/bin/csap-environment.sh

# 1.15 and later: --upload-certs replaces --experimental-upload-certs
resetControlCerts="kubeadm init phase upload-certs --upload-certs"
print_with_head "Running: '$resetControlCerts'"
resetOutput=$(run_using_root $resetControlCerts)
certKey=$(echo $resetOutput| awk '{print $NF}')

printJoin="kubeadm token create --print-join-command"
print_with_head "Running: '$printJoin'"
joinWorkerCommand=$(eval $printJoin) ;

# 1.15 and later: --control-plane replaces --experimental-control-plane
joinControlCommand="$joinWorkerCommand --control-plane --certificate-key $certKey"

#
# DO NOT MODIFY BELOW: it is parsed in OsManager.java
#
print_with_head "joinWorkerCommand: $joinWorkerCommand"
print_with_head "joinControlCommand: $joinControlCommand"
