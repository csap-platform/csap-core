#!/bin/bash
#
#
#

source $CSAP_FOLDER/bin/csap-environment.sh

if [[ $* == *deleteMavenRepo* ]]; then  

	print_with_head "Removing $CSAP_FOLDER/maven-repository and $CSAP_FOLDER/build"
	\rm --recursive --force $CSAP_FOLDER/maven-repository/*
	\rm --recursive --force $CSAP_FOLDER/build/*
	
else

	print_with_head "Removing $CSAP_FOLDER/build/$csapName"
	\rm --recursive --force $CSAP_FOLDER/build/$csapName*
	
fi

if [[ $csapName == *httpd* ]] ; then

	print_with_head "removing $CSAP_FOLDER/httpdConf"
	\rm --recursive --force $CSAP_FOLDER/httpdConf;
	
fi