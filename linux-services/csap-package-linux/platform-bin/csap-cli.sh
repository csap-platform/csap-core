#!/bin/bash

params=$@

# echo params is: $params
# Supress any info messages
source $CSAP_FOLDER/bin/csap-environment.sh >/dev/null

csapShellJars=${csapJars:-$csapPlatformWorking/$csapAgentId/jarExtract/BOOT-INF/lib}/*
# echo == cp is $cp
# A little tricky but we run eval to allow caller to quote params
eval -- java -classpath \"$csapShellJars\" CsapShell  $params


# sapShell  $params