#!/bin/bash

function printIt() { echo -e ".\n.\n=========\n"; echo -e == $* ; echo =========; }

function checkInstalled() { verify=`which $1`; if [ "$verify" == "" ] ; then printIt error: $1 not found, install using yum -y install; exit; fi   }



printIt "running as `id`"; 

printIt "path is $PATH" ; 

printIt "Java is `java -version 2>&1`" ;

printIt "starting: java with javaOpts: $javaOpts" 

printIt "hsqlConfig:  $hsqlConfig " \
	"\n== NOTE: use of non default port to avoid port conflicts:  hsqldb:hsql://<host_name>:9002/testdb"

printIt "dbDefinition: $dbDefinition"

java $javaOpts $hsqlConfig $dbDefinition