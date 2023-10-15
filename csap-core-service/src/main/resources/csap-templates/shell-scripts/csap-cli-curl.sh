#!/bin/bash


curl  \
	--silent \
	--data-urlencode "pass=_yourcompanyIntegration" \
	--data "userid=csapcli" \

	--data-urlencode "content@/Users/peter.nightingale/git/csap-core/csap-core-service/target/csap-platform/temp/my-auto-play.yaml" \
	--request POST \
	http://localhost:8021/csap-admin/api/application/autoplay | jq .

#	--data "isApply=true" \
exit ;


curl  \
	--silent \
	--data-urlencode "pass=_yourcompanyIntegration" \
	--data "userid=csapcli" \
	--data-urlencode "content=demo" \
	--request POST \
	http://localhost:8021/csap-admin/api/application/autoplay | jq .
	
curl  \
	--silent \
	--data-urlencode "pass=_yourcompanyIntegration" \
	--data "userid=csapcli" \
	--data "serviceName=csap-verify-service" \
	--request POST \
	http://localhost:8021/csap-admin/api/application/service/start | jq .
#	http://csap-dev01.yourcompany.org:8021/csap-admin/api/application/service/stop
	