#!/bin/bash

source $CSAP_FOLDER/bin/csap-environment.sh

#
#  Reference: https://www.elastic.co/guide/en/elasticsearch/reference/current/cat.html
#


command=${1:-showIndexes};

function setup_environment() {
	
	local elasticIp=$(kubectl --namespace=csap-logging get pod elastic-search-cluster-0 -o yaml | grep " podIP:" | awk '{ print $2 }')
	elasticUrl="$elasticIp:9200"
	
	print_two_columns "elasticUrl" "$elasticUrl"
	print_two_columns "command" "$command"
}
setup_environment





function showIndexes() {

	print_separator "showIndexes" ;
	
	curl --silent --request GET \
		"$elasticUrl/_cat/indices?bytes=b&s=store.size:desc&v"

}




function wipeKibana() {

	print_separator "removing indexes starting with kibana" ;
	
	curl --silent --request DELETE \
		"$elasticUrl/.kibana*"
		
	print_header "Note - kibana will need to be removed and redeployed"

}




function wipeAllData() {

	print_separator "wipeKibana" ;
	
	curl --silent --request DELETE \
		"$elasticUrl/_all"

}



eval $command ;