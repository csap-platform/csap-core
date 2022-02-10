#!/bin/bash



function print_section() { 
	echo -e  "\n* \n**\n*** \n**** \n*****  $* \n**** \n*** \n** \n*"
}


print_section "api/model/services/name"

curl  \
	--silent \
	--request GET \
	http://localhost:8011/api/model/services/name
	
	
	
	
print_section "model/services/byName/csap-agent"

curl  \
	--silent \
	--request GET \
	http://localhost:8011/api/model/services/byName/csap-agent
	
	