#!/bin/bash

source $CSAP_FOLDER/bin/csap-environment.sh

#
# use specific version to hit caches
#
sanityImage=${sanityImage:-nginx:1.21.3} ;

function status_tests() {
	
	print_with_big_head "status_tests"
	
	print_command \
		"docker info" \
		"$( docker info )" 
	
	print_command \
		"docker ps" \
		"$( docker ps )" 

}


function verify_docker_image_pulls() {

	print_with_big_head "verify_docker_image_pulls"
	
	local testImage="$sanityImage" # jboss/keycloak:8.0.1 is large
	
	local maxRuns=2 ;
	for (( runCount=1; runCount<=$maxRuns; runCount++ )) ; do
	
		print_command \
			"Run $runCount: docker rmi $testImage" \
			"$( docker rmi $testImage )" 
		
		print_separator "docker pull $testImage" 
		docker pull $testImage
		
	done
	
}

function verify_docker_run() {
	
	print_with_big_head "runtime tests"
	
	print_command \
		"docker network create --driver bridge verify_bridge" \
		"$( docker network create --driver bridge verify_bridge )" 
	
	
	print_command \
		"docker run --detach --name verify-nginx --publish=6080:80 --network="verify_bridge" $sanityImage" \
		"$( docker run --detach --name verify-nginx --publish=6080:80 --network="verify_bridge" $sanityImage )" 
		
	
	print_command \
		"docker ps" \
		"$( docker ps )" 
	
	serviceUrl="$(hostname):6080/"
	local nginxResponse=$(curl --max-time 3 --silent $serviceUrl | sed -e 's/<[a-zA-Z\/][^>]*>//g' | tail -15)
	print_command \
		"nginx response with most html stripped:  'curl $serviceUrl'" \
		"$nginxResponse" 
	
	local trimmedResponse=$(echo $nginxResponse | tr '\n' ' ')
	local expectedLog="Welcome to nginx!";
	
	if [[ "$trimmedResponse" =~ $expectedLog ]] ; then
		print_with_head "SUCCESS: Found expected log message: $expectedLog" ;
	else
		print_with_head "ERROR: Did not find message: $expectedLog" ;
	fi ;
	
	
	print_command \
		"docker logs verify-nginx" \
		"$( docker logs verify-nginx )" 
	
	#
	#  Cleanup - remove the exposed port (service) , and remove the deployment
	#
	print_command \
		"docker rm --force verify-nginx" \
		"$( docker rm --force verify-nginx )" 
		
	
	print_command \
		"docker network rm  verify_bridge" \
		"$( docker network rm  verify_bridge )" 
		
	
}

print_with_head "uncomment the tests to run"
# verify_docker_run