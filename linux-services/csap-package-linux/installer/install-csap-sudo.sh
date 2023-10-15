#!/bin/bash

#
# linux package AND host installer both invoke
#

# set -x

csapUser="$1" ;
csapBin="$2" ;
csapEnvironmentFile=${3:-$csapBin/csap-environment.sh} ;

#
# handle installs from both common install and linux package
#
ENV_FUNCTIONS="$(dirname -- $csapEnvironmentFile)/functions"
source $csapEnvironmentFile

sudoFile="/etc/sudoers";

if [[ "$csapBin" != /* ]] ; then
	print_error "install-csap-sudo() argument 2 must be an absolute path, but found: '$csapBin'"
	exit 99 ;
fi ;


print_separator "$0 - csapUser: '$csapUser', csapBin: '$csapBin' "


backupFile="$sudoFile-original-pre-csap" ;
if ! test -f $backupFile  ; then
	print_line "Backing up $sudoFile to '$backupFile'"
	cp $sudoFile $backupFile
fi ; 

append_file "# $csapUser" "$sudoFile" false

delete_all_in_file "$csapUser"

print_line "removing requiretty to enable webapp maintenance"
delete_all_in_file "requiretty"


append_line "$csapUser ALL=NOPASSWD: /usr/bin/pmap"
append_line "$csapUser ALL=NOPASSWD: /sbin/service"
append_line "$csapUser ALL=NOPASSWD: /bin/kill"
append_line "$csapUser ALL=NOPASSWD: /bin/rm"
append_line "$csapUser ALL=NOPASSWD: /bin/nice"
append_line "$csapUser ALL=NOPASSWD: /usr/bin/pkill"
append_line "$csapUser ALL=NOPASSWD: $csapBin/csap-renice.sh"
append_line "$csapUser ALL=NOPASSWD: $csapBin/csap-run-as-root.sh"
append_line "$csapUser ALL=NOPASSWD: $csapBin/csap-deploy-as-root.sh"
append_line "$csapUser ALL=NOPASSWD: $csapBin/csap-unzip-as-root.sh"

#
# no root
# append_line "$csapUser ALL=NOPASSWD: /bin/su"


print_command "$sudoFile" "$( cat $sudoFile | grep $csapUser)"