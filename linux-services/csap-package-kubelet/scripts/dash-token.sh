#!/bin/bash

# helper functions 
source /opt/csap/csap-platform/bin/csap-environment.sh ; 


print_section "Generating token for accessing kubernetes dashboards"

dashUsers="$(kubectl --namespace=kubernetes-dashboard get secret)";

print_command "kubectl --namespace=kubernetes-dashboard get secret" "$(echo "$dashUsers")"

adminUser="$(echo "$dashUsers" | grep csap-dash-user-token | awk '{print $1}')";

if [ "$adminUser" == "" ] ; then 
	adminUser="$(echo "$dashUsers" | grep kubernetes-dashboard-token | awk '{print $1}')";
fi ;

print_two_columns "admin" "$adminUser" ;


token=$(kubectl --namespace=kubernetes-dashboard describe secret $adminUser | grep "token:" | awk '{print $2}');

# DO NOT change this line - it is parsed in launch ui
echo "_CSAP_SCRIPT_OUTPUT_"
echo "$token"
