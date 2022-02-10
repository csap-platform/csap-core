# csap cli examples, examples of querying application definition, host collections, etc

cd _file_

# Usage:
#	csap-cli.sh <args>  - connects to either csap-agent or csap-admin, local or remote
#   agent <args>        - (uses cli) connects to localhost csap-agent
#   csap <args>         - (uses cli) connects to localhosts agent admin csap-admin
#

print_command "Number of csap-agent services running" \
	$(services_running csap-agent)

print_command "services in stop order" \
	$(csap model/services/name?reverse=true --parse)

print_command "hosts: for specified cluster" \
	"$(csap model/hosts/base-os --jpath /hosts)"

print_command "cli help" \
	"$(csap-cli.sh)"
	


exit ;

#
#  CSAP CLI Orchestrations: refer to service.sh for details
#
wait_for_csap_backlog ;
update_application "/full/path/to/my-auto-play.yaml"
count_running service-name

targetServices="space separated list of services - they will be done in autostart order"

stop_services "$targetServices" clean;
deploy_services "$targetServices" ;
start_services "$targetServices" ;

# displays list of service and running counts - return code ($?) will contain total of all requested services
count_running_services $targetServices 

pause_all_deployments ;



	
print_command "clusters: using remote sytax" \
	"$(csap-cli.sh --lab http://$(hostname --long):8011 --api model/clusters)"

print_command "clusters: via localhost agent" \
	"$(agent model/clusters)"
	
print_command "clusters: via agents admin" \
	"$(csap model/clusters)"
	
print_command "clusters: extract cluster hosts" \
	"$(csap model/clusters --jpath /base-os)"
	
print_command "csap-admin service first url" \
	$(agent model/service/urls/csap-admin --jpath /0)


#
# generate api event
#
print_command \
	"pushing csap-agent event: agent agent/service/event " \
	"$(agent agent/service/event --textResponse --params "$(csap_credentials),service=csap-agent,summary='demo cli event'")" ;


#
#  Simple start and stop
#
csap application/service/stop \
	--params "$(csap_credentials),serviceName=csap-verify-service"
	
	
csap application/service/deploy \
	--params "$(csap_credentials),serviceName=csap-verify-service"
	
csap application/service/start \
	--params "$(csap_credentials),serviceName=csap-verify-service"
	
#
#  On agent: STOP is NOT the same as stop on admin (kills with options)
#

print_command \
	"agent agent/service/stop " \
	"$(agent agent/service/stop  --params "$(csap_credentials),services=csap-verify-service")" ;
wait_for_csap_backlog
	
print_command \
	"agent agent/service/start " \
	"$(agent agent/service/start  --params "$(csap_credentials),services=csap-verify-service")" ;
wait_for_csap_backlog



	
# with orchestration
excludeServices="none" ;
clean="no" ; # or clean="clean"
stop_services csap-verify-service $clean $excludeServices;
start_services csap-verify-service $excludeServices ;



#
# bypass cliu
#
curl  \
	--silent \
	--request GET \
	http://localhost:8011/api/model/services/name



#
# patching autoplay
#

update_application "path-to-file"
isApply="false"
update_application demo $isApply

#
#  Long form: Sample stop using curl 
#
credContent=$(cat $HOME/.csap-config) 
creds=(${credContent//,/ })
user=${creds[0]} ;
pass=${creds[1]} ;

curl  \
	--silent \
	--data-urlencode "$pass" \
	--data "$user" \
	--data "serviceName=csap-verify-service" \
	--request POST \
	http://localhost:8021/csap-admin/api/application/service/stop


#
# patch
#
	

curl  \
	--silent \
	--data-urlencode "$pass" \
	--data "$user" \
	--data "patchfile=simple-auto-play.yaml" \
	--request POST \
	http://localhost:8021/csap-admin/api/application/patch

#
#  download logs
#
#agent service/log/download \
#	--params "$(csap_credentials),serviceName=csap-verify-service"
curl -d "$(echo $(csap_credentials) | tr ',' '&')&serviceName=csap-verify-service&fileName=console.log"  http://centos1.***REMOVED***:8011/api/agent/service/log/download


testCluster="base-os"
testMaven="org.csap:csap-starter-tester:2.0.0-SNAPSHOT:jar"
testService="csap-verify-service_7011"


#
# Parsing output: 
#	-script will return raw text.
#	-jpath will allow parsing of json to specific fields
#
# jpath syntax is at: http://tools.ietf.org/html/draft-ietf-appsawg-json-pointer-03



#
#	Sample: find kubernetes nodeport for a service
#
testK8Service="ingress-nginx"
print_with_head "$testK8Service port number: \
	'$(csap-cli.sh --lab $agent_api --api agent/kubernetes/$testK8Service/nodeport --parse -jpath /url)'"
	


#
#	Sample: Get service definition, parsing response for port
#
print_with_head "$testService port number: \
	'$(csap-cli.sh --lab $admin_api --api model/services/byName/$testService \
	--parse -jpath /0/port)'"

print_with_head "$testService port number: \
	'$(csap-cli.sh --lab $admin_api --api model/services/byName/$testService \
	--parse -jpath /0/port)'"

#
#  Sample: Get log files for service: print out the 2nd file name
#
logFileName="$(csap-cli.sh --lab $agent_api --api agent/service/log/list \
	-params \"userid=$apiUser,pass=$apiPass,serviceName_port=$testService\" \
	--parse -jpath /1)"
print_with_head "$testService 2nd log file: '$logFileName'"




#
#   Sample: Search LOG file for service for pattern
#			Note the escape quotes are needed to handle greps with spaces.
#
searchPattern="OsManager.java"
print_with_head Sample: grep log file content for $testService
csap-cli.sh --parse -textResponse --lab $agent_api \
	--api 'agent/service/log/filter' \
	-params \"userid=$apiUser,pass=$apiPass,fileName=console.log,filter=\"$searchPattern\",serviceName_port=$testService\" \
	


#
#   Sample: Show hosts in specified cluster
#

print_with_head "Hosts in cluster: '$testCluster'"
hosts=$(csap-cli.sh --parse --lab $admin_api --api model/hosts/$testCluster -jpath /hosts) ;
for hostName in $hosts ; do
	print_line "found host: $hostName"
done


print_with_head "Services on all hosts sorted by start order"
services=$(csap-cli.sh --parse --lab $admin_api --api model/services/id?reverse=true) ;
for service in $services ; do
	print_line "found service: $service"
done





#
#   Sample: service deployment AND start
#
print_with_head "Deploying: '$testService'"

csap-cli.sh --lab  $admin_api \
	--api application/service/deploy \
	-timeOutSeconds 30 \
	-params "userid=$apiUser,pass=$apiPass,serviceName=$testService,performStart=true,mavenId=$testMaven,cluster=$testCluster"






#
#   Sample: service start (or restart)
#
print_with_head "Starting: '$testService'"

csap-cli.sh --lab  $admin_api \
	--api application/service/start \
	-timeOutSeconds 11 \
	-params "userid=$apiUser,pass=$apiPass,serviceName=$testService,startClean=true,cluster=$testCluster"

print_command \
	"HTTP Get with multiple parameters and debug enabled" \
	"$( agent "agent/event/latestTime?hostname=$(hostname --short)\&category=/csap/system/agent-start-up" -debug ) "

	
# csap-cli.sh --lab  https://csap-secure.yourcompany.com/admin --api serviceDeploy/ServletSample_8041 -timeOutSeconds 11 -params "mavenId=com.your.group:Servlet3Sample:1.0.2:war,userid=$apiUser,pass=$apiPass,cluster=dev-AuditCluster-1"

# service start/stop - update password
#csap-cli.sh  -timeOutSeconds 11 --lab  http://localhost:8011/$csapAgentName --api serviceStart/ServletSample_8041 -params "userid=$apiUser,pass=$apiPass"
#csap-cli.sh  -timeOutSeconds 11 --lab  http://localhost:8011/$csapAgentName --api serviceStop/ServletSample_8041 -params "userid=$apiUser,pass=$apiPass"




#echo; echo == Sample invocation: get log file content for $csapAgentName
#p=`csap-cli.sh --parse -textResponse --lab http://testhost.yourcompany.com:8080/admin --api service/$csapAgentId/logs/$logFileName  `
#echo  log file content $p


# print_with_head json format with optional timeout
# csap-cli.sh --api help -timeOutSeconds 14

# Service Deploy - update password
# csap-cli.sh --lab  http://localhost:8011/$csapAgentName --api serviceDeploy/ServletSample_8041 -timeOutSeconds 11 -params "mavenId=com.your.group:Servlet3Sample:1.0.2:war,userid=$apiUser,pass=$apiPass,cluster=dev-AuditCluster-1"
# csap-cli.sh --lab  https://csap-secure.yourcompany.com/admin --api serviceDeploy/ServletSample_8041 -timeOutSeconds 11 -params "mavenId=com.your.group:Servlet3Sample:1.0.2:war,userid=$apiUser,pass=$apiPass,cluster=dev-AuditCluster-1"

# service start/stop - update password
#csap-cli.sh  -timeOutSeconds 11 --lab  http://localhost:8011/$csapAgentName --api serviceStart/ServletSample_8041 -params "userid=$apiUser,pass=$apiPass"
#csap-cli.sh  -timeOutSeconds 11 --lab  http://localhost:8011/$csapAgentName --api serviceStop/ServletSample_8041 -params "userid=$apiUser,pass=$apiPass"


#
#csap-cli.sh  -timeOutSeconds 11 --lab  http://localhost:8011/$csapAgentName --api mavenArtifacts

# Hitting manager of entire lifecycle. These are HA calls
# csap-cli.sh --lab https://csap-secure.yourcompany.com/admin summary

# echo == hosts
# csap-cli.sh --api hosts -script


# echo == hosts with JSON output
# csap-cli.sh --lab https://csap-secure.yourcompany.com/admin --api hosts/dev-AuditCluster-1 

# echo == hosts with script output
# csap-cli.sh --lab https://csap-secure.yourcompany.com/admin --api hosts/dev-AuditCluster-1 -script



