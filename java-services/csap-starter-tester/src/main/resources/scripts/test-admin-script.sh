#!/bin/bash

function printIt() { echo -e "\n\n =========\n == $* \n ========="; }


scriptName=`dirname $0`
scriptName=`basename $0`



printIt "Running $0."


printIt "csap variables are available, the current service: $csapName"

printIt "Listing pf `pwd`"

ls -l 
