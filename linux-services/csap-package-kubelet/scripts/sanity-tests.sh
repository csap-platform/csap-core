#!/bin/bash

source $CSAP_FOLDER/bin/csap-environment.sh
# used when polling for completion of last kubectl command
max_poll_result_attempts=100


requestedCommand=${1:-none} ;

sanityNginxImage=${sanityNginxImage:-nginx:1.21.3} ;
sanityDnsImage=${sanityDnsImage:-gcr.io/kubernetes-e2e-test-images/dnsutils:1.3} ;

function status_tests() {
	
	print_section "status_tests"
	
	print_with_head "Querying nodes - summary"
	kubectl get nodes --output=wide
	
	print_with_head "Querying nodes - taints"
	#kubectl get no -o json | jq -r '.items[] | select(.spec.unschedulable!=true) | [.metadata.name,.spec.taints] '
	kubectl get nodes -o custom-columns=NAME:.metadata.name,TAINTS:.spec.taints
	
	print_with_head "Querying pods: 'kubectl get pods --all-namespaces'"
	kubectl get pods --all-namespaces
	
	print_with_head "Querying services: 'kubectl -n kube-system get service'"
	kubectl -n kube-system get service
}

function show_pods_and_containers() {
	
	namespaces=$(kubectl get namespaces --output jsonpath={.items[*].metadata.name})
	
	for namespace in $namespaces ; do
	
		print_with_head "Namespace: '$namespace'"
		
		# use kubectl get pods --output json to view raw data
		podNames=$(kubectl get pods --namespace=$namespace -o jsonpath={.items[*].metadata.name})
		
		for podName in $podNames ; do
			podContainers=$(kubectl get pods $podName --namespace=$namespace -o jsonpath="{.spec.containers[*].name}")
			
			print_line "Pod: $podName"
			print_line "\t\t Containers: $podContainers\n"
		done
		
	done
		
}

function show_network() {
	
	print_with_head "calico pods"
	kubectl get pods -o wide -l k8s-app="calico-node" --namespace=kube-system
	
	local calicoNodeOnCurrentHost=$(kubectl get pods -o wide --namespace=kube-system | grep 'calico-node' | grep $(hostname --short) | awk '{print $1}')
	local calicoKubeOnCurrentHost=$(kubectl get pods -o wide --namespace=kube-system | grep 'calico-kube' | grep $(hostname --short) | awk '{print $1}')
	
	print_with_head "calico-node pod on host: '$calicoNodeOnCurrentHost'"
	kubectl logs $calicoNodeOnCurrentHost --namespace=kube-system  --since=1h
	
	print_with_head "calico-node pod on host: '$calicoKubeOnCurrentHost'"
	kubectl logs $calicoKubeOnCurrentHost --namespace=kube-system  --since=1h --previous
	
}



function wait_for_calico_node_running_and_ready() {
	
	currentHostName=$(hostname | cut -f1 -d.)
	
	print_with_head "Wait for calico ready on host: '$currentHostName'"
	
	namespace="--namespace=kube-system"
	
	for i in `seq 1 $max_poll_result_attempts`;
	do
		sleep 3;
		print_line "\n---------- attempt $i:"
		calicoPodOnCurrentHost=$(kubectl get pods -o wide $namespace | grep "$currentHostName" | grep calico-node | awk '{print $1}') ;

		if [ "$calicoPodOnCurrentHost" == ""  ] ; then
			continue ;
		fi; 

		print_line "calicoPodOnCurrentHost: '$calicoPodOnCurrentHost'"
		kubectl get pods $calicoPodOnCurrentHost $namespace

		numberReady=$(kubectl get pods $calicoPodOnCurrentHost $namespace | grep " Running" | wc -l)
		if (( "$numberReady" >= 1 )) ; then
			break;
		fi ;
	done

	print_with_head "Polling for 5 success logs with no bind errors"

	noBindInLogs=0
	for i in `seq 1 60`; do
		
		sleep 2;
		print_line "\n--------------- attempt $i "

		kubectl logs $calicoPodOnCurrentHost $namespace --container=calico-node --tail=10

		numLinesWithReady=$(kubectl logs $calicoPodOnCurrentHost $namespace --container=calico-node --tail=10 | grep "Ready:true" | wc -l)
		numLinesWithBind=$(kubectl logs $calicoPodOnCurrentHost $namespace --container=calico-node --tail=10 | grep bind | wc -l)
		
		if (( $numLinesWithBind == 0 && numLinesWithReady > 0)) ; then
			((noBindInLogs++))
			if (( $noBindInLogs > 5 )) ; then
				print_with_head "Assuming calico is initialized successfully - no bind errors in logs "
				break;
			fi ;
		fi ;
	done
}

function dashboard_tests() {
	grafanaName=$(find_pod_name grafana)

	print_with_head "grafana status for '$grafanaName'"
	kubectl -n kube-system exec $grafanaName -- netstat -ntlp
	
	heapsterName=$(find_pod_name heapster)
	kubectl -n kube-system exec $heapsterName -- nslookup kubernetes.default
	kubectl -n kube-system exec $heapsterName -- wget https://kubernetes.default/api/v1/namespaces?resourceVersion=0
}



function deployment_tests() {
	
	print_section "deployment_tests"
	
	local podShortName="my-test-$(hostname --short)"
	
	local nodeSelector=''
	
	master=$(kubectl get nodes | grep $(hostname) | grep master);
	if [ "$master" == "" ] ; then 	
	    print_with_head "Current host is a worker - adding a node selector to force deployment on host";	
		nodeSelector='{ "spec": { "template": { "spec": { "nodeSelector": { "kubernetes.io/hostname": "'$(hostname)'" } } } } }'
  	fi ;
	
	#
	#  Create a deployment using nginx
	#
	print_section "deployment_tests: '$podShortName' using docker image: '$sanityNginxImage' and nodeSelector: '$nodeSelector'"
	kubectl run $podShortName --image $sanityNginxImage --overrides="$nodeSelector"
	
	wait_for_pod_running $podShortName
	
	testPod=$(find_pod_name $podShortName)
	
	#
	#  Query deployment state
	#
	print_command \
		"Deployment details" \
		"$(kubectl get pods -o wide)"
		
	
	print_command \
		"Pod Details" \
		"$(kubectl describe pods $testPod)"	
	
	 
	
	#
	#  Expose deployment: enables outside pod access to pod network
	#
	
	
	print_command \
		"Exposing deployment as a service" \
		"$(kubectl expose pod $testPod --name=$podShortName --port=80 --type=NodePort)"	
		
	
	#local patchSpec='[{"op": "replace", "path": "/spec/ports/0/nodePort", "value":'$targetPort'}]'
	#kubectl patch service $podShortName --type='json' --patch="$patchSpec"

	print_command \
		"Service status" \
		"$(kubectl get service $podShortName)"	
	
	#
	# Using hostname --long to ping service. Note that host name MUST be resolvable by dns 
	#
	local targetHost=$(hostname --long) ;
	local nslookupOutput=$(nslookup $targetHost 2>&1) ;
	local nslookupReturnCode=$? ;
	if (( $nslookupReturnCode != 0 )) ; then 
		print_with_head "Warning: $targetHost not found using nslookup - kubectl node interalip will be used" ; 
		targetHost=$(kubectl get nodes -o wide | grep -v INTERNAL | awk '{ print $6}')
		print_with_head "targetHost set to: $targetHost"
	fi
	
	local targetPort=$(echo $(kubectl get service $podShortName -o custom-columns=:.spec.ports[0].nodePort) | xargs)
	
	local sanityResult;
	for i in $(seq 1 3); do
		serviceUrl="$targetHost:$targetPort/"
#		print_with_head "Service response with most html stripped:  'curl $serviceUrl'"
		nginxResponse=$(curl --max-time 3 --silent $serviceUrl | sed -e 's/<[a-zA-Z\/][^>]*>//g' | tail -15)
#		print_with_head "$nginxResponse"


		print_command \
			"Pod http get response via 'curl $serviceUrl' | strip html-chars" \
			"$(echo -e "$nginxResponse")"	
		
		
		local trimmedResponse=$(echo $nginxResponse | tr '\n' ' ')
		local expectedLog="Welcome to nginx!";
		
		if [[ "$trimmedResponse" =~ $expectedLog ]] ; then
			sanityResult="SUCCESS: Found expected log message: $expectedLog"
			print_line "$sanityResult"  ;
			break ;
		else
			sanityResult="ERROR: Found expected log message: $expectedLog"
			print_line "$sanityResult"  ;
			sleep 2;
			print_line "\n--------------- attempt $i of 3" ;
		fi ;
	
	done ;
	
	
	
	print_command \
		"Pod Logs" \
		"$(kubectl logs $testPod)"	
		
	
	
	#
	#  Cleanup - remove the exposed port (service) , and remove the deployment
	#
	
	print_command \
		"Removing service" \
		"$(kubectl delete service $podShortName)"	
	
	print_command \
		"Removing test pod" \
		"$(kubectl delete pod $podShortName)"	
	
	print_section "$sanityResult"
	
}

function deploy_network_troubleshooter() {
	
	deploymentName="csaptools"
	
	print_with_head "deploying csap-tools as '$deploymentName' into default namespace"
	kubectl run $deploymentName --image docker.yourcompany.org/csap/csap-tools -- /bin/sleep 5000
	
	toolsPod=$(find_pod_name $deploymentName)
	print_with_head "to attach: 'kubectl exec -it $toolsPod bash'"
	
	
}

function deploy_csap_test_app() {
	
	deploymentName="csap-test-app"
	imageName="docker.yourcompany.org/csap/csap-test-app"
	
	
	#
	#  Create a deployment using nginx
	#
	print_with_head "deployment_tests: '$deploymentName' using docker image: '$imageName'"
	kubectl run $deploymentName --image $imageName --replicas=1
	
	
	
	wait_for_pod_running $deploymentName
	testPod=$(find_pod_name $deploymentName)
	
	#
	#  Query deployment state
	#
	print_with_head "Deployment details"
	kubectl get deployment -o wide
	
	print_with_head "Pod Details"
	kubectl describe pods $testPod
	
	#
	#  Expose deployment: enables outside pod access to pod network
	#
	print_with_head "Exposing deployment as a services"
	kubectl expose deployment $deploymentName --port=7080 --type=NodePort
	
	print_with_head "Service status"
	kubectl get service $deploymentName
	

	print_with_head "Waiting for csap-test-app to initialize:"
	for i in `seq 1 $max_poll_result_attempts`; do
		sleep 2;
		
		print_line "attempt $i start >>>>"
		kubectl logs $testPod --tail=10
		print_line "<<< attempt $i end"
		currentLogs=$(kubectl logs $testPod)
		
		if [[ $currentLogs =~ .*logStarted.* ]] ; then
			break;
		fi ;
	done
    
    
    #
	#  Use api server to perform service lookup - and proxy the service call
	#
	serviceUrl="$(hostname):8014/api/v1/namespaces/default/services/$deploymentName/proxy/"
	print_with_head "Service response with most html stripped:  'curl $serviceUrl'"
	curl --max-time 1 -s $serviceUrl | sed -e 's/<[a-zA-Z\/][^>]*>//g' | tail -15
	
	#
	#  Cleanup - remove the exposed port (service) , and remove the deployment
	#
	#print_with_head "Removing service"
	#kubectl delete service $deploymentName
	
	#print_with_head "Removing deployment"
	#kubectl delete deployment $deploymentName
	
}


dnsPodName="dnsutils"
dnsPodNamespace="default"
function deploy_dnsUtils() {
	
	local dnsYmlFile="$(pwd)/dnsutils.yml"
	
	if $(is_pod_running $dnsPodName $dnsPodNamespace) ; then 
	
		print_with_head "$dnsPodName is running"
		return ;
		
	fi ;
	
	cat >$dnsYmlFile<<EOF
---
apiVersion: v1
kind: Pod
metadata:
  name: $dnsPodName
  namespace: $dnsPodNamespace
spec:
  containers:
  - name: dnsutils
    image: $sanityDnsImage
    command:
      - sleep
      - "3600"
    imagePullPolicy: IfNotPresent
  restartPolicy: Always

---
EOF

	print_command  \
		"kubectl create --filename=$dnsYmlFile" \
		"$( kubectl create --filename=$dnsYmlFile )"
		

  wait_for_pod_conditions $dnsPodName
}


function dns_tests() {
	
	print_section "dns_tests"
	
	deploy_dnsUtils  
	  
	#
	# simple dns tests
	#
	
	
	
	print_command  \
		"kubectl exec -it dnsutils --namespace=default -- nslookup kubernetes.default" \
		"$( kubectl exec -it dnsutils --namespace=default -- nslookup kubernetes.default 2>&1)"
	
	print_command  \
		"kubectl exec -it dnsutils --namespace=default -- nslookup google.com" \
		"$( kubectl exec -it dnsutils --namespace=default -- nslookup google.com 2>&1)"
		
	kubectl delete pod $dnsPodName  --namespace=$dnsPodNamespace

}



function perform_operation() {

	case "$requestedCommand" in
		
		"status_tests")
			status_tests
			;;
		
		"dns_tests")
			dns_tests
			;;
		
		"deployment_tests")
			deployment_tests
			;;
		
		 *)
	            echo "Usage: $0 {verify_podman_run|status_tests}"
	            exit 1
	esac

}

# uncomment to invoke specific command interactively; note no CSAP variables will be set 
# requestedCommand="verify_podman_run"
perform_operation














