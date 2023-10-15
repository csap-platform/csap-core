#!/bin/sh
#
#
#

scriptDir=$(dirname $0)
echo ====================================================================
echo == Running $0 $*
echo == script dir is $scriptDir 
echo == param count: $#


# svcInstance=$2 ;
source $scriptDir/csap-environment.sh
#set -o verbose #echo on


echo "admin $jmxPassword" >| $jmxPassFile
if [ "$jmxPassword" == "wipeit" ] ; then 
	rm -rf $jmxPassFile
fi ;
touch $jmxPassFile
chmod 700 $jmxPassFile


#set +o verbose #echo off