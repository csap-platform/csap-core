#!/bin/bash

source $CSAP_FOLDER/bin/csap-environment.sh

#
# Run on the master
#

# this expires?
# kubeadm init phase upload-certs --upload-certs

print_with_head "Current kubeadm tokens"
kubeadm token list

if [ "$clusterToken" == "" ] ; then 
	print_with_head "Warning: clusterToken  being reset is the last in the list"
	clusterToken=$(kubeadm token list|tail -1 | awk '{print $1}');
fi;

print_with_head "Deleteing existing token: '$clusterToken'"
kubeadm token delete $clusterToken

ttl="1h"
print_with_head "Creating token: '$clusterToken' with time to live '$ttl'"

kubeadm token create $clusterToken --ttl $ttl

print_with_head "New expiration"
kubeadm token list