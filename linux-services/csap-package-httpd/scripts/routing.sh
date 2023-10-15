#!/bin/bash

CSAP_FOLDER=${CSAP_FOLDER:-/opt/csap/csap-platform}

source $CSAP_FOLDER/bin/csap-environment.sh

servicePort=${1:-8080} ;
sourcePort=${2:-80} ;
createPort80Tunnel=${3:-false} ;

#print_separator "CSAP Httpd Routing"
#print_two_columns "servicePort" "$servicePort"
#print_two_columns "sourcePort" "$sourcePort"
#print_two_columns "createPort80Tunnel" "$createPort80Tunnel"

#
#  clean up any previous routes associated with port
#
cleanIpTableChain "nat" "PREROUTING" "$servicePort" ;

cleanIpTableChain "nat" "OUTPUT" "$servicePort" ;

#print_command \
#  "iptables --table nat --list" \
#  "$(iptables --table nat --list --line-numbers | grep REDIRECT)" 



primaryNetworkDevice=$(route | grep default | awk '{ print $8}') ;

if [[ $createPort80Tunnel == true ]] ; then

	addIptableRedirect $sourcePort $servicePort ;

	print_command \
	  "iptables --table nat --list | grep REDIRECT" \
	  "$(iptables --table nat --list --line-numbers | grep REDIRECT)" 
	
	
else
	
	print_with_head "createPort80Tunnel is not true - skipping"
	
fi ;
