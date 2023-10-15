#!/bin/bash

#
# Run by OsManager.java as part of shared collections
#




kubectl describe nodes | \
  grep --extended-regexp \
    --regexp='^Name:[ ]*[(a-z)|(0-9)|.|-]*$'  \
    --regexp '^Capacity:$'  \
    --regexp '^Allocatable:$'  \
    --regexp "^[ ]*cpu:[ ]*[(a-z)|(0-9)|.|-]*$"  \
    --regexp "^[ ]*memory:[ ]*[(0-9)]*[(a-z)|(A-Z)]*$"  \
    --regexp '^Allocated resources:$'  \
    --regexp '^[ ]*cpu[ ]*[^\s]*[ ]*[^\s]*[ ]*[^\s]*[ ]*[^\s]*[ ]*$'  \
    --regexp '^[ ]*memory[ ]*[^\s]*[ ]*[^\s]*[ ]*[^\s]*[ ]*[^\s]*[ ]*$' 
    
    
# match
#Name:               yourapp-mgmt-app-sandbox4a.yourcompany.org
#Capacity:
#  cpu:                16
#  memory:             32778740Ki
#Allocatable:
#  cpu:                16
#  memory:             32676340Ki
#Allocated resources:
#  cpu                1700m (10%)  4 (25%)
#  memory             600Mi (1%)   2112Mi (6%)