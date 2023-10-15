#!/bin/bash

source $CSAP_FOLDER/bin/csap-environment.sh

requestedCommand=${1:-none} ;

sanityImage=${sanityImage:-nginx:latest} ;

function status_tests() {
	
	print_section "status_tests"
	
	print_command \
		"podman info" \
		"$( podman info )" 
	
	print_command \
		"podman ps" \
		"$( podman ps )" 

}


function verify_podman_image_pulls() {

	print_with_big_head "verify_podman_image_pulls"
	
	local testImage="$sanityImage" # jboss/keycloak:8.0.1 is large
	
	local maxRuns=2 ;
	for (( runCount=1; runCount<=$maxRuns; runCount++ )) ; do
	
		print_command \
			"Run $runCount: podman rmi $testImage" \
			"$( podman rmi $testImage )" 
		
		print_separator "podman pull docker.io/$testImage" 
		podman pull docker.io/$testImage
		
	done
	
}

function verify_podman_run() {
	
	print_section "verify_podman_run"
	
	print_command \
		"podman network create --driver bridge verify_bridge" \
		"$( podman network create --driver bridge verify_bridge )" 
	
	
	print_command \
		"podman run --detach --name verify-nginx --publish=6080:80 --network="verify_bridge" $sanityImage" \
		"$( podman run --detach --name verify-nginx --publish=6080:80 --network="verify_bridge" $sanityImage )" 
		
	
	print_command \
		"podman ps" \
		"$( podman ps )" 
	
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
		"podman logs verify-nginx" \
		"$( podman logs verify-nginx )" 
	
	#
	#  Cleanup - remove the exposed port (service) , and remove the deployment
	#
	print_command \
		"podman rm --force verify-nginx" \
		"$( podman rm --force verify-nginx )" 
		
	
	print_command \
		"podman network rm  verify_bridge" \
		"$( podman network rm  verify_bridge )" 
		
	
}

function perform_operation() {

	case "$requestedCommand" in
		
		"verify_podman_run")
			verify_podman_run
			;;
		
		"status_tests")
			status_tests
			;;
		
		 *)
	            echo "Usage: $0 {verify_podman_run|status_tests}"
	            exit 1
	esac

}

# uncomment to invoke specific command interactively; note no CSAP variables will be set 
# requestedCommand="verify_podman_run"
perform_operation

