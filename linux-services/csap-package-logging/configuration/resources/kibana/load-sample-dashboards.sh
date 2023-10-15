#!/bin/bash

#
#  refer to: https://www.elastic.co/guide/en/kibana/master/saved-objects-api-import.html
#

source $CSAP_FOLDER/bin/csap-environment.sh


csapName=${csapName:-kibana};
csapNameSpace=${csapNameSpace:-csap-logging};

function set_up_filters() {
	
	print_with_head "Initializing: '$csapName' in namespace: '$csapNameSpace'" ;
	
	local ip=$(kubectl --namespace=$csapNameSpace get service $csapName-service -o jsonpath="{.spec.clusterIP}") ;
	local url="http://$ip:5601/$csapName"
	local index_pattern="logstash-*"
	local id="logstash-*"
	local time_field="@timestamp"
	
	print_two_columns "url" "$url"
	print_two_columns "index_pattern" "$index_pattern"
	
	
	#
	#  Do not invoke apis until rest service are availble
	#
	wait_for_curl_success "--request GET $url/api/settings"
	
	

	#
	#  create default index
	#  - "timeFieldName": "@timestamp" ommitted due to different date/times
	#
	print_separator "creating index '$index_pattern'"
	curl --silent --request POST \
		--write-out "\n\tHttpStatus: %{http_code}" \
		--header "Content-Type: application/json" \
		--header "kbn-xsrf: anything" \
		--data '{
			  "override": true,
			  "refresh_fields": true,
			  "index_pattern": {
			     "title": "logstash-*",
			     "timeFieldName": "@timestamp"
			  }
			}' \
		"$url/api/index_patterns/index_pattern"
		
	exit_on_failure $?;

	#
	# Set default columns for discover
	#
	print_separator "Existing Configuration "
	curl --silent --request GET \
		--header "Content-Type: application/json" \
		--header "kbn-xsrf: anything" \
		"$url/api/saved_objects/_find?type=config&per_page=1" \
		| jq
		
	configId=$(curl --silent --request GET \
		--header "Content-Type: application/json" \
		--header "kbn-xsrf: anything" \
		"$url/api/saved_objects/_find?type=config&per_page=1" \
		| jq --raw-output '.saved_objects[0].id')
#
	print_separator "updating config: $configId "
	curl --silent --request PUT \
		--write-out "\n\tHttpStatus: %{http_code}" \
		--header "Content-Type: application/json" \
		--header "kbn-xsrf: anything" \
		--data '{
		  "attributes": {
			"defaultIndex": "logstash-*", 
            "defaultColumns": [
	          "@timestamp",
	          "kubernetes.namespace_name",
	          "kubernetes.pod_name",
	          "log"
	        ]
		  }
		}' \
		"$url/api/saved_objects/config/$configId"

	
	
	#
	#  The ndjson format for export and import
	#  - create a dashboard using kibana ui -> export it
	#
	local ndJsonFiles="$(dirname $csapJob)/*.ndjson" ;
	print_separator "loading ndjson files: $ndJsonFiles"
	for ndJsonFile in $ndJsonFiles ; do
		print_separator "adding dashboard from $ndJsonFile"
		curl --silent --request POST \
			--write-out "\n\tHttpStatus: %{http_code}" \
			--header "kbn-xsrf: true" \
			--form "file=@$ndJsonFile" \
			"$url/api/saved_objects/_import?overwrite=true"
	done ;
		
		
	#
	# show status	
	#
	curl --silent --request GET $url/api/status | jq

}

set_up_filters


exit ;

#
#  updated just index
#
	print_separator "setting default index"
	curl --silent --request POST \
		--write-out "\n\tHttpStatus: %{http_code}" \
		--header "Content-Type: application/json" \
		--header "kbn-xsrf: anything" \
		--data '{
			  "value": "logstash-*"
			}' \
		"$url/api/kibana/settings/defaultIndex" 
		
	exit_on_failure $?;

#
# legacy
#
	print_separator "creating index '$index_pattern'"
	curl --silent --request POST \
		--write-out "\n\tHttpStatus: %{http_code}" \
		--header "Content-Type: application/json" \
		--header "kbn-xsrf: anything" \
		--data "{\"attributes\":{\"title\":\"$index_pattern\",\"timeFieldName\":\"$time_field\"}}" \
		"$url/api/saved_objects/index-pattern/$id?overwrite=true"
		
	exit_on_failure $?;

	print_separator "setting default index"
	curl --silent --request POST \
		--write-out "\n\tHttpStatus: %{http_code}" \
		--header "Content-Type: application/json" \
		--header "kbn-xsrf: anything" \
		--data "{\"value\":\"$id\"}" \
		"$url/api/kibana/settings/defaultIndex" 
		
	exit_on_failure $?;

	
	print_separator "setting default columns"
	curl --silent --request POST \
		--write-out "\n\tHttpStatus: %{http_code}" \
		--header "Content-Type: application/json" \
		--header "kbn-xsrf: anything" \
		--data  '{"changes":{"defaultColumns":["kubernetes.pod_name","log"]}}' \
		"$url/api/kibana/settings"
		
	exit_on_failure $?;

	
	
	local errorFile="$(dirname $csapJob)/error-filter.json" ;
	print_separator "adding error filter from $errorFile"
	curl --silent --request POST \
		--write-out "\n\tHttpStatus: %{http_code}" \
		--header "Content-Type: application/json" \
		--header "kbn-xsrf: anything" \
		--data  "@$errorFile" \
		"$url/api/saved_objects/search/errorFilter?overwrite=true"
		
	exit_on_failure $?;
