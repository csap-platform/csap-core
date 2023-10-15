#!/bin/bash


function run_python() {

	local scriptPath=${1:---version} ;
	local version=${2:-python:2} ;
	local containerName=${3:-python-script} ;
	local interactive=${4:-} # use -it
	
	local mountFolder=$(pwd) ;
	if [[ "$scriptPath" == *\/* ]]  ; then
		mountFolder=$(dirname $scriptPath) ;
		scriptPath=$(basename $scriptPath) ;
	fi ;
	
	
	print_separator "docker" ;
	print_two_columns "image" "$version"
	print_two_columns "mountFolder" "$mountFolder"
	
	print_separator "output: $scriptPath"
	
	docker run $interactive --rm \
		--name $containerName \
		-e "HOSTNAME=$(hostname --long)" \
		-v "$mountFolder":/usr/src/myapp \
		-w /usr/src/myapp \
		$version python \
		$scriptPath
}

function run_perl() {
	
	# docker run -it --rm --name my-running-script -v "$PWD":/usr/src/myapp -w /usr/src/myapp perl:5.20 perl sample.pl

	local scriptPath=${1:---version} ;
	local version=${2:-perl:5.20} ;
	local containerName=${3:-perl-script} ;
	local interactive=${4:-} # use -it
	
	local mountFolder=$(pwd) ;
	if [[ "$scriptPath" == *\/* ]]  ; then
		mountFolder=$(dirname $scriptPath) ;
		scriptPath=$(basename $scriptPath) ;
	fi ;
	
	
	print_separator "docker" ;
	print_two_columns "image" "$version"
	print_two_columns "mountFolder" "$mountFolder"
	
	print_separator "output: $scriptPath"
	
	docker run $interactive --rm \
		--name $containerName \
		-e "HOSTNAME=$(hostname --long)" \
		-v "$mountFolder":/usr/src/myapp \
		-w /usr/src/myapp \
		$version perl \
		$scriptPath
}

function calicoctl() {
	
	run_using_root DATASTORE_TYPE=kubernetes KUBECONFIG=~/.kube/config $CSAP_FOLDER/saved/calicoctl $*
}

#
# https://docs.projectcalico.org/getting-started/clis/calicoctl/configure/kdd
#
function calico() {
	
	
	if ! test -e $CSAP_FOLDER/saved/calicoctl ; then
	
		print_separator "Getting calico" ;
		cd $CSAP_FOLDER/saved;
		curl -O -L  https://github.com/projectcalico/calicoctl/releases/download/v3.21.0/calicoctl ;
		chmod 755 $CSAP_FOLDER/saved/calicoctl ;
		cd - ;
		
		
		print_with_head "calicoctl is installed in $CSAP_FOLDER/saved/calicoctl"
		
	fi ;
	
	local numArgs="$#" ;
	
	if (( numArgs == 0 )) ; then
		print_with_head "Reference: https://docs.projectcalico.org/maintenance/troubleshoot/troubleshooting"
		
		
		print_command \
			"calicoctl get nodes -o wide" \
			"$(calicoctl get nodes -o wide)"
			
		print_command \
			"calicoctl get ipPool -o wide" \
			"$(calicoctl get ipPool -o wide)"
			
		print_command \
			"calicoctl node status" \
			"$(calicoctl node status)"
			
		# -n kube-system or --all-namespaces
		print_command \
			"calicoctl get workloadendpoints --all-namespaces" \
			"$(calicoctl get workloadendpoints --all-namespaces)"
		
		
	else
		calicoctl $*
	fi ;
	
}

function verify_helm() {
	
	if ! $(is_command_available helm) ; then
	
		print_error "helm is not installed; it is include in csap-package-linux";
		exit ;

	fi
	
	print_command \
		"helm repo list" \
		"$( helm version; helm repo list )"
	
}


function create_namespace_if_needed() {

	local namespaceName=${1:-none} ;
	
	if [ "$namespaceName" != "none" ] ; then
		kubectl create namespace $namespaceName --dry-run=client -o yaml | kubectl apply -f -
	fi ;

}

function is_helm_release_deployed() {

	local releaseName=${1:-release-not-specified} ;
	local namespaceName=${2:-namespace-not-printspecified} ;
	
	print_two_columns2 "is_helm_release_deployed" "releaseName: $releaseName, namespaceName: $namespaceName" ;

	helm status $releaseName --namespace $namespaceName &> /dev/null
	
	local returnCode="$?" ;
	
	if (( $returnCode == 0 )) ; then 
		true ;
	else  
		false ;
	fi ;   
	
}


function add_repo_with_setup_checks() {

	
	local reposToAdd=${1:-repoNotSpecified}
	local numArgs="$#" ;
	
	if is_package_installed dnf ; then 
	
		if (( $numArgs == 1 )) ; then
			print_two_columns "adding repo" "$reposToAdd" ;
			commandResults=$(dnf config-manager --add-repo=$reposToAdd 2>&1) ;
			exit_on_failure $? "$commandResults";
		fi ;
		
		repoInfo=$(dnf --assumeyes repoinfo 2>&1) ;
		exit_on_failure $? "$repoInfo";
			    
		cleanOutput=$(dnf clean all 2>&1) ;
		exit_on_failure $? "$cleanOutput";
		
		print_command \
			"dnf enabled repositories" \
			"$(dnf repolist all | grep enabled)"
		
	
	else
	
		if (( $numArgs == 1 )) ; then
			print_two_columns "adding repo" "$reposToAdd" ;
			commandResults=$(yum-config-manager --add-repo $reposToAdd 2>&1) ;
			
		
		
			if  [[ "$reposToAdd" =~ (.*docker-ce.*) ]] && grep "Red Hat.* 7.*" /etc/redhat-release ; then
				print_with_head "WARNING: docker-ce repo bug: RH7 requires patching REPO: https://github.com/docker/for-linux/issues/1111"
				replace_all_in_file '$releasever' "7" /etc/yum.repos.d/docker-ce.repo;
#				for channel in "stable" "test" "nightly"; do
#					yum-config-manager --setopt="docker-ce-$channel.baseurl=https://download.docker.com/linux/centos/7/x86_64/stable" --save
#				done ;
			fi;
			
			exit_on_failure $? "$commandResults";
		fi ;
		
		repoInfo=$(yum --assumeyes repoinfo) ;
		exit_on_failure $? "$repoInfo";
			    
		cleanOutput=$(yum clean all 2>&1) ;
		exit_on_failure $? "$cleanOutput";
		
		print_command \
			"yum enabled repositories" \
			"$(yum repolist all | grep enabled)"
		
	fi ;
		
}


function delay_message_seconds() {
	
	local delay_in_seconds=${1:-200} ;
	local max_poll_result_attempts=$(( $delay_in_seconds / 2 )) ;
	local message=${2:-Delaying execution}
	print_with_head "$message"
	local currentAttempt=1;
	for i in $(seq $currentAttempt $max_poll_result_attempts); do
	   	print_line "Delay: $(( i * 2)) of $delay_in_seconds (seconds)"
        sleep 2;
    done ;
}

function wait_for_port_free() {

	local portFilter=${1:-8011}
	local max_poll_result_attempts=${2:-10} ;
	local message="$3"
	
	# use --numeric instead of --resolve 
	print_with_head2 "wait_for_port_free $message - waiting for filter: '$portFilter' to not be found in 'ss --numeric --processes'  (--listen)"
	
	local currentAttempt=1;
	
	
	for i in $(seq $currentAttempt $max_poll_result_attempts); do
		nonListeningCount=$(ss --numeric | awk '{print $5}' | grep --word-regexp $portFilter | wc -l)
		listeningCount=$(ss --numeric  --listening  | grep --invert-match UNCONN | awk '{print $5}' | grep --word-regexp $portFilter | wc -l)
		containerMatches=$(( nonListeningCount + listeningCount ))
		print_line2 "attempt $i of $max_poll_result_attempts: found: '$containerMatches'\n"
		if (( $containerMatches == 0 )) ; then
			break ;
		fi ;
		sleep 5;
	done
	
	if (( $containerMatches == 0 )) ; then
		print_line2 "No remaining instances of '$portFilter'" ;
		echo true
	else
		print_with_head2 "WARNING: $containerMatches still remaining" \
			 "\n $(ss --numeric  --processes  | grep --word-regexp $portFilter)"\
			 "\n $(ss --numeric  --processes --listening | grep --word-regexp $portFilter)"
		echo false
	fi ;
	
	
}



#
#  Docker Helpers
#

function wait_for_docker_log() {
	
	local containerName="$1"
	local logPattern=${2:-1}
	local max_poll_result_attempts=${3:-30} ;
	local since=${4:-300m}
	
	print_with_head "Waiting for logs pattern: '$logPattern',  in containerName: '$containerName' since: '$since'"
	
	local currentAttempt=1;
	local containerMatches=0;
	
	for i in $(seq $currentAttempt $max_poll_result_attempts); do
	        
	        sleep 2;
	        
	        logOutput=$(docker logs $containerName --since $since 2>&1) ;
	        containerMatches=$(echo $logOutput | grep "$logPattern" | wc -l)
	        
			print_line "attempt $i of $max_poll_result_attempts:  waiting for log pattern: '$logPattern'\n"
	    
			if (( $containerMatches > 0 )) ; then
				break ;
			fi ;
	done
	
	print_with_head "Matches Found: '$containerMatches' for logs pattern: '$logPattern',  in containerName: '$containerName' "
	
}



#
#   k8s helpers
#

#
#  create_storage_folder <type> <folder/disk> <nfsmount> <vsphereDatastore> <vsphere size>
#  create_storage_folder $csap_def_storage_type $csap_def_storage_folder/$csap_def_name-disk $csap_def_nfs_mount $csap_def_vsphere_datastore  $10M
#
function create_storage_folder() {

	if (( $# < 5 )) ; then 
		print_with_head "Error: invalid argument count '$#'" ;
		print_line "create_storage_folder <type> <folder/disk> <nfsmount> <vsphereDatastore> <vsphere size>" 
		return ;
	fi ; 
	
	local folderType=${1:-type-not-specified}
	
	local folderPath=${2:-path-not-specified}
	
	local nfsMountPath=${3:-path-not-specified}
	
	local vsphereDataStore=${4:-path-not-specified}
	
	local vsphereSize=${5:-1G}
	
	if [ "$folderType" == "nfs" ] ; then 
	
		local nfsFolderPath="$nfsMountPath/$folderPath" ;
		
		print_command \
			"Create a folder on nfs path: $folderPath" \
			"$( run_using_root mkdir --parents --verbose $nfsFolderPath )"
		
		
	elif [ "$folderType" == "vsphere" ] ; then 
	
		local vsphereDisk="$folderPath.vmdk" ;
		local vsphereFolder="$(dirname $folderPath)" ;
		
		print_separator "Verifying $vsphereDisk is removed from any host"
		find_kubernetes_vcenter_device $vsphereDisk true
	
		print_separator "Create a vsphere folder: $vsphereFolder" 
		govc datastore.mkdir -ds=$vsphereDataStore $vsphereFolder
		
		print_separator "Create a vsphere disk: $vsphereDisk size $vsphereSize" 
		govc datastore.disk.create -ds=$vsphereDataStore -size $vsphereSize $vsphereDisk
		
		print_separator "$vsphereFolder listing"
		govc datastore.ls -R=true $vsphereFolder
		
	else 
	
		print_with_head "Error: Unexpected storage type: '$folderType'" ;
		
	fi ;
	
}


function find_pod_name() {
	
	podTarget=$1
	podFullName=$(kubectl get pod --all-namespaces | grep "$podTarget" | tail -1 | awk '{print $2}')
	
	#
	# this function returns the name of the pod
	#
	echo "$podFullName"
}

function find_pod_names() {
	
	podTarget=$1
	podNames=$(kubectl get pod --all-namespaces | grep "$podTarget" | awk '{print $2}')
	
	#
	# this function returns the name of the pod
	#
	echo "$podNames"
}


function find_pod_namespace() {
	local targetPod=$1;
	kubectl get pods --all-namespaces | grep $targetPod | tail -1 | awk '{print $1}'
}

function wait_for_pod_log() {
	
	podPattern="$1"
	podLogPattern=${2:-1}
	number_of_pods=${3:-1}
	logNamespace=${4:-all}
	max_poll_result_attempts=${5:-200} ;
	#tail_filter=${6:---tail=200} ;
	tail_filter=${6:---since=1h} ;
	
	wait_for_pod_running $podPattern $number_of_pods $logNamespace $max_poll_result_attempts
	
	targetPods=$(find_pod_names $podPattern) ;
	print_with_head "Waiting for logs pattern: '$podLogPattern', pod_tail: '$tail_filter', in pods: '$targetPods' "
	
	local podCount=0 ;
	local currentAttempt=1;
	
	for targetPod in $targetPods; do
	
		podCount=$(( $podCount + 1)) ;
		if [ $logNamespace == "all" ] ; then
			detectedNamespace=$(find_pod_namespace $targetPod)
			namespace="--namespace=$detectedNamespace" ;
		else
			namespace="--namespace=$logNamespace";
		fi ;
		
		for i in $(seq $currentAttempt $max_poll_result_attempts); do
	        
	        sleep 2;
	        
	        local podNames=$(find_pod_names)
	        if [[ ${podNames} != *"$targetPod"* ]];then
			    print_line "$targetPod pod is not found, exiting ";
			    break ;
			fi
	        
	        # preserve attempt total across all pods
	        currentAttempt=$(( $currentAttempt + 1 )); 
	        
	        containerNames=$(kubectl get pods $targetPod $namespace -o jsonpath='{.spec.containers[*].name}') ;
	        
	        containerMatches=0 ;
	        logsFound="" ;
	        for containerName in $containerNames; do
	        	
	        	print_if_debug "Logs container: '$containerName',  attempt $i of $max_poll_result_attempts: \n"
	        	print_if_debug "$(kubectl logs $targetPod $namespace --container=$containerName --tail=20)\n"
	        	numMatchedLogs=$(kubectl logs $targetPod $namespace --container=$containerName $tail_filter | grep "$podLogPattern" | wc -l) ;
	        	if (( $numMatchedLogs > $containerMatches )) ; then
					containerMatches=$numMatchedLogs;
				fi ;
				
	    	done
	
			print_line "attempt $i of $max_poll_result_attempts: pod $podCount: '$targetPod',  waiting for log pattern: '$podLogPattern', matches found: '$containerMatches'\n"
	    
			if (( $containerMatches > 0 )) ; then
				break ;
			fi ;
	    	
		done ;
	done ;
	
	failed_to_find_logs=false;
	if (( $currentAttempt >= $max_poll_result_attempts )) ; then
		failed_to_find_logs=true;	
	fi
    
    print_with_head "Pod pattern: '$podPattern', log pattern: '$podLogPattern', matches: '$containerMatches' "
}


function wait_for_pod_conditions() {
	
	local podPattern=${1:-test-k8s-by-spec}
	local number_of_pods=${2:-1}
	local namespace=${3:-all}
	local max_poll_result_attempts=${4:-200} ;
	
	#wait_for_pod_running $podPattern $number_of_pods $namespace $max_poll_result_attempts
	
	print_with_head "Waiting for: '$number_of_pods' pods with conditions passed: '$podPattern' in '$namespace'."
	
	
	local currentAttempt=1;
	
	for i in $(seq $currentAttempt $max_poll_result_attempts); do
	
		sleep 2;
		local podNames=$(find_pod_names $podPattern)
		local podsAllPassed=0;
		for podName in $podNames; do
			if [ $namespace == "all" ] ; then
				namespace="--namespace=$(find_pod_namespace $podName)" ;
			fi ;
			
			
			local podDescribe=$(kubectl describe pod $podName $namespace) ;
			
			local podConditionsWithHeaders=$(echo "$podDescribe" | sed -n '\|^Conditions:$|,\|^Volumes:$|p' ) ;
			
			local podConditions=$( echo "$podConditionsWithHeaders" | grep -v -e "Conditions" -e "Volumes" -e "Status") ;
			
			
			local podConditionTotal=$( echo "$podConditions" | wc -l) ;
			local podConditionPassed=$( echo "$podConditions" | grep -i true | wc -l) ;
			local podConditionFailed=$( echo "$podConditions" | grep -iv true | tail -1 ) ;
			
			print_two_columns "$podConditionPassed of $podConditionTotal conditions" "$podName: $podConditionFailed"
			
			print_if_debug \
				"pod conditions:  $podConditionPassed of $podConditionTotal" \
				"$podConditions"
				
			if (( $podConditionPassed == $podConditionTotal)) ; then
				podsAllPassed=$(( $podsAllPassed + 1)) ;
			fi ;
		
		done ;
		
		print_line "attempt $i of $max_poll_result_attempts: '$podPattern',  podsAllPassed: $podsAllPassed. Minimum: $number_of_pods'\n"
    
		if (( $podsAllPassed >= $number_of_pods )) ; then
			break ;
		fi ;
	done ;

    
    print_line "Pod pattern: '$podPattern', '$numPods' in running state"
}

function wait_for_pod_running() {
	
	local podPattern="$1"
	local number_of_pods=${2:-1}
	local namespace=${3:-all}
	local max_poll_result_attempts=${4:-200} ;
	
	if [ $namespace == "all" ] ; then
		namespace="--all-namespaces" ;
	else
		namespace="--namespace=$namespace" ;
	fi ;
	
	print_with_head "Waiting for: '$number_of_pods' pods in run state: '$podPattern' in '$namespace'."
	for i in $(seq 1 $max_poll_result_attempts); do
        sleep 2;

		print_separator "attempt $i of $max_poll_result_attempts"
		kubectl get pods $namespace | grep $podPattern
    
    	numPods=$(kubectl get pods $namespace | grep $podPattern | grep " Running" | wc -l)
		if (( $numPods >= $number_of_pods )) ; then
			break;
		fi ;
    	
    done
    
    print_line "Pod pattern: '$podPattern', '$numPods' in running state"
}

function is_pod_running() {
	
	podPattern="$1"
	namespace=${2:-all}
	
	if [ $namespace == "all" ] ; then
		namespace="--all-namespaces" ;
	else
		namespace="--namespace=$namespace";
	fi;
	
	numPods=$(kubectl get pods $namespace | grep $podPattern | wc -l) ;
		
	if (( $numPods == 0 )) ; then
		>&2 print_line "Not Found: '$podPattern' in '$namespace'"
		echo false ;
	else
		>&2 print_line "Found $numPods pods: '$podPattern' in '$namespace'"
		echo true ;
	fi ;
	
}

function wait_for_pod_removed() {
	
	podPattern="$1"
	namespace=${2:-all}
	max_poll_result_attempts=${3:-50} ;
	
	if [ $namespace == "all" ] ; then
		namespace="--all-namespaces" ;
	else
		namespace="--namespace=$namespace";
	fi;
	
	print_with_head "Waiting for all pods to be removed, pattern: '$podPattern' in '$namespace'."
	
	local numPods=99
	for i in $(seq 1 $max_poll_result_attempts); do
        sleep 2;

		print_line "attempt $i of $max_poll_result_attempts: \n$(kubectl get pods $namespace | grep $podPattern) \n"
		numPods=$(kubectl get pods $namespace | grep $podPattern | wc -l) ;
		
		if (( $numPods == 0 )) ; then
			break;
		fi ;
    	
    done
    
	pod_still_found=true;
	if (( $numPods == 0 )) ; then
		pod_still_found=false;	
	fi
	
    print_line "Pod pattern: '$podPattern', '$numPods' found"
}


function addIptableRedirect() {
	
	local sourcePort=$1 ;
	local destPort=$2 ;
	local primaryNetworkDevice=$(route | grep default | awk '{ print $8}') ;
	
	print_separator "adding new rule mapping $sourcePort to $destPort on $primaryNetworkDevice"
	iptables --table nat --append PREROUTING --in-interface $primaryNetworkDevice --protocol tcp --dport $sourcePort --jump REDIRECT --to-port $destPort
	
	if (( $sourcePort == 443 )) ; then
		iptables --table nat --append OUTPUT --protocol tcp --dport $sourcePort --out-interface lo --jump REDIRECT --to-port $destPort
	fi
	
}

function cleanIpTableChain() {
	
	local table=$1 ;
	local chain=$2 ;
	local ruleFilter=$3 ;
	
	local itemsToBeDeleted="$(iptables --table $table --list $chain --line-numbers | grep --word-regexp $ruleFilter | wc -w)";
	
	if (( $itemsToBeDeleted == 0 )); then
		return ;
	fi ;
	
	
	print_command \
	  "FOUND  - table $table ruleFilter $ruleFilter chain $chain" \
	  "$(iptables --table $table --list $chain --line-numbers | grep --word-regexp $ruleFilter)" 
	
	#
	# note line numbers are reversed so that deletes using number are consistent
	#
	local lineNumbers=$(iptables --table $table --list $chain --line-number | grep --word-regexp $ruleFilter | awk '{ print $1}' | tac)
	
	# for i in $( rulesWith$servicePort | tac ); do
	for lineNumber in $lineNumbers ; do 
#		print_command \
#			"Deleting rule with line number: $lineNumber" \
#			"$(iptables --table $table --delete $chain $lineNumber)"; 

		print_two_columns "Deleteing" "lineNumber $lineNumber"
		iptables --table $table --delete $chain $lineNumber
	done


}

#
# restore functions for wipe
#
function check_for_webserver_port_restore() {
	
	local restoreScript="$CSAP_FOLDER/working/httpd/restore-port-tunnels.sh";
	print_separator "Checking for '$restoreScript'"
	if [ -f "$restoreScript" ] ; then  
		print_two_columns "invoking" "$restoreScript"
		chmod 755 $restoreScript
		run_using_root $restoreScript 
	fi ; 
		
}


#
# cleanup functions for kubernetes
#

function iptable_wipe() {

	if [ "$USER" != "root" ] ; then
		print_with_head "Script must be run as root, switch user."
		exit ;
	fi ;

	
	local primaryNetworkDevice=$(route | grep default | awk '{ print $8}') ;
	local numberRedirectsToRestore=$(iptables --table nat --list | grep REDIRECT | wc -l) ;
	
	
	#tableTypes="filter nat mangle"
	local tableTypes="filter nat mangle raw" ;
#	print_command \
#		"iptables --flush ; iptables --zero ;iptables --delete-chain" \
#		"$(iptables --flush ; iptables --zero ;iptables --delete-chain)" ;
	
	print_separator "cleaning up iptables"
	for tableType in $tableTypes ; do
		numberOfRoutes=$(iptables --list-rules --table $tableType | wc -l);
		if (( $numberOfRoutes > 0 )) ; then 
			print_two_columns "table $tableType" "found '$numberOfRoutes' routes"
			iptables --table $tableType --flush ;
			iptables --table $tableType --delete-chain ;
			if (( $? != 0 )) ; then
				print_error "non 0 return code when cleaning up iptables"
			fi ;
			
			numberOfRoutes=$(iptables --list-rules --table $tableType | wc -l) ;
			print_two_columns "post flush" "'$numberOfRoutes' remain."	
		fi ;
	done ;
	
	

	
	print_two_columns "Note" "view details using 'iptables --list-rules'"
	
}


function perform_kubeadm_reset() {
	
	if is_package_installed kubeadm ; then

		print_separator "perform_kubeadm_reset() cleaning up previous installs"
		kubeadm reset --force
		# echo y | kubeadm --v 8 reset
		
		clean_kubernetes_systemd
		
		print_line "Running mount in case kubeadm umounts kubelet due to bug"
#		mount --all --verbose
		mount --verbose /var/lib/kubelet
		
	else 
		print_two_columns "skipping kubeadm reset" "reason: kubeadm not installed"  ;
	fi ;
	
	# cleanup support for local test nfsserver
	local demoNfsServer="/mnt/nfsshare" ;
	print_two_columns "Checking test nfs" "check if exists: $demoNfsServer"
	if test -d $demoNfsServer ; then
		nfs_remove_mount $demoNfsServer
	fi ;
}


function clean_kubernetes_systemd() {

	#
	# kubelet fails failed to create top level Burstable QOS cgroup : Unit kubepods-burstable.slice already exists
	#
	# kubeadm not cleaning this up
	print_separator "kubernetes systemd cleanup start"; 
	
	
	print_command \
		"systemctl listing filtered by pod: pre cleanup" \
		"$(systemctl list-units | grep pod)"
		
	systemctl stop  kubepods-besteffort.slice ;
	systemctl stop  kubepods-burstable.slice ;
	systemctl stop  kubepods.slice ;
	
	local leftOverPods=$(systemctl list-unit-files --no-legend --no-pager -l | awk '{print $1}' | grep kubepod) ;
	leftOverPods="$leftOverPods $( systemctl list-units --no-legend --no-pager -l | awk '{print $1}' | grep 'var-lib-kubelet-pods' )" ;
	
	
	for podService in $leftOverPods; do 

	  print_command \
	  	"systemctl stop $podService" \
	  	"$(systemctl stop $podService)" ;
	
	done
	
	systemctl daemon-reload
	systemctl reset-failed
	
	print_command \
		"systemctl list-units filtered by pod: post cleanup" \
		"$(systemctl list-units | grep pod)"
		

}

function umount_containers() {

	local folderPaths=${1:-not-specified};
	local max_umount_attempts=${2:-10} ;

	print_two_columns "Unmounting" "mounts underneath: '$folderPaths'"
	
	local fsRc=$(are_file_systems_readable; echo $?) ;
	if (( $fsRc != 0 )) ; then
		print_with_head "Unable to list filesystems, exiting. Try running hard_umount_all" ;
		exit 999 ;
	fi ;
	
	
	local vcenterEnv="$HOME/vcenter/vcenter-env.sh" ;
	
	local isVcenterIntegration=$(if test -d $vcenterEnv ; then echo true ; else echo false ; fi) ;
	local vsphereVmPath="" ;
	
	
	local currentAttempt=1;
	
	if $isVcenterIntegration ; then
		print_line "vcenter integration detected: '$HOME/vcenter', loading 'vcenter-env.sh'" ;
		source $vcenterEnv ;
		vsphereVmPath="/$GOVC_DATACENTER/$( govc find vm -name $(hostname --short) )" ;
	fi ;
	
	
	for folderPath in $folderPaths ; do
		
		# numberOfContainerMounts=$(cat /proc/mounts | grep "$folderPath/" | wc -l) ;
		containerMounts=$(timeout 5s df --output=target | grep "$folderPath/" ) ;
		
		numberOfContainerMounts=$(echo -n "$containerMounts" | wc -w ) ;
		
		if (( $numberOfContainerMounts > 0 )) ; then
		
			currentAttempt=1;
			for i in $(seq $currentAttempt $max_umount_attempts); do
				print_line "($currentAttempt of $max_umount_attempts) removing '$numberOfContainerMounts' container mounts in '$folderPath'" ;
				#cat /proc/mounts | grep "$folderPath/" | awk '{print $2}' | xargs --no-run-if-empty --verbose umount
				echo "$containerMounts" | while read mountpoint ; do
					print_line "unmounting: $mountpoint"
				 	umount "$mountpoint" ; 
				 	
				 	
				 	mountPointCount=$(timeout 5s df --output=target | grep "$mountpoint" | wc -w) ;
				 	
				 	if $isVcenterIntegration && (( $mountPointCount == 0 )) ; then
				 		diskName=$(basename "$mountpoint");
				 		vcenter_remove_device "$vsphereVmPath" $diskName
				 	fi ;
				 	
				done ;
				 
		 		containerMounts=$(timeout 5s df --output=target | grep "$folderPath/" ) ;
				numberOfContainerMounts=$(echo -n "$containerMounts" | wc -w ) ;
		 		
		 		if (( $numberOfContainerMounts == 0 )) ; then
					break ;
				fi ;
				sleep 5 ;
			 done ;
			 
			 if (( $numberOfContainerMounts == 0 )) ; then
		 		print_line "Successfully umounted all items" ;

			 else
			 	print_command \
			 		"Warning: failed to umount: '$numberOfContainerMounts' items" \
			 		"$containerMounts" ;
			 fi ;
		fi ;
		
		print_two_columns "removing" "'$folderPath/*'" ;
		#\rm --recursive --force $folderPath/*
		removeLocalItemsOnly $folderPath
		
	done

}

function vcenter_remove_device() {

	local vsphereVmPath="$1" ;
	local diskName="$2" ;
	
	print_line "looking for diskName: '$diskName' on host '$vsphereVmPath'"
	
	local deviceInfo=$(govc device.info  \
		-vm "$vsphereVmPath"  \
		| tr '\n' ' ' | sed 's/Name:/\nName:/g' | sed 's/  */ /g' | grep "$diskName") ;
	
	print_two_columns  "deviceInfo" "$deviceInfo"
	
	local vcenterDeviceName=$(echo $deviceInfo | awk '{ print $2 }')
	print_two_columns  "vcenterDeviceName" "$vcenterDeviceName"
	
	if [[ "$vcenterDeviceName" != "" ]] ; then
		print_command \
			"govc device.remove  -vm '$vsphereVmPath' -keep=true  $vcenterDeviceName" \
			$(govc device.remove  -vm "$vsphereVmPath" -keep=true  $vcenterDeviceName)
	fi ;
}


function clean_podman_and_crio() {
	print_separator "clean_podman_and_crio"
	
	clean_oci_artifacts ;
	
	if $(is_process_running crio) ; then
		print_two_columns "systemctl stop crio.service" "$(systemctl stop crio.service  2>&1)"
		wait_for_terminated "/usr/bin/crio" 3 root
	fi ;
	
	
	if $(is_process_running podman) ; then
		print_two_columns "pkill podman" "$(pkill -SIGKILL --full podman 2>&1)"
	fi ;
	
	print_separator "removing packages podman cri-o cri-tools"
	
	#
	if is_package_installed podman ; then
		print_command "podman system reset --force" "$(podman system reset --force)" ;
	fi ;
	
	
	if is_package_installed dnf ; then
			
		dnf --assumeyes autoremove podman cri-o cri-tools
	
	else 
			
		yum --assumeyes --remove-leaves erase podman cri-o cri-tools
	
	fi ;
	
	exit_on_failure $? "failed to remove podman cri-o cri-tools"
	
	print_separator "removing /etc/crio /etc/containers"
	rm --verbose --recursive --force /etc/crio /etc/containers
	
	
	if $(is_process_running conmon) ; then
		delay_with_message 2 "killing all conmon"
		killall conmon ;
	fi ;
	
	wait_for_terminated "/usr/bin/conmon" 3 root
	
	if $(is_process_running conmon) ; then
	
		print_command "ps -ef | grep conmon" "$(ps -ef | grep conmon)"
	
	fi ;
	
	remove_kubernetes_networking
	ip link delete cni0;
	ip -o link show | awk -F': ' '{print $2}' | grep docker | xargs --no-run-if-empty -L 1 ip link delete
	#  modprobe -r ipip  # can hang OS
	
	remove_kubernetes_routing
	
	
	print_separator "Purging iptable"
	iptable_wipe
	
}



function clean_crio_containers() {
	
	if ! is_package_installed cri-o ; then
		print_two_columns "clean_crio_containers" "skipping - crio is not installed" ;
		return ;
	fi ;
	
	

	local numberOfContainers=$(crictl ps --all --quiet 2>&1 | wc -l);
	print_two_columns "crio containers" "$numberOfContainers"

	if (( $numberOfContainers > 0 )) ; then
		print_command "crio containers" "$(crictl ps 2>&1)"
	
		print_two_columns "crictl" "stopping all running containers"
		crictl stop $(crictl ps --quiet) ; 
		
		print_command "crio containers" "$(crictl ps 2>&1)"
		
		print_command "ALL crio containers" "$(crictl ps --all 2>&1)"
		
		print_two_columns "crictl" "removing all containers"
		crictl rm $(crictl ps --all --quiet) ;
	fi ;
	
	
	print_command "NOT removing images (delegated to podman uninstall)" "$(crictl image 2>&1)"
	
	
}


function clean_oci_artifacts() {
	
	clean_crio_containers ;
	
	if ! is_package_installed podman ; then
		print_two_columns "clean_oci_artifacts" "skipping - podman is not installed" ;
		return ;
	fi ;

	local numberOfContainers=$(podman ps --all --quiet 2>&1 | wc -l);
	print_two_columns "podman containers" "$numberOfContainers"

	if (( $numberOfContainers > 0 )) ; then
		print_command "Running containers" "$(podman ps --all --quiet 2>&1)"
	
		print_two_columns "podman" "stopping all running containers"
		podman stop $(podman ps --all --quiet) ; 
		
		print_two_columns "podman" "removing all containers"
		docker rm $(podman ps --all --quiet) ;
	fi ;
	
	local imageCount=$(podman image ls --all 2>&1 | wc -l);
	print_two_columns "podman imageCount" "$imageCount"
	if (( $imageCount > 1 )) && ! test -f /root/keep-images ; then
		print_two_columns "podman" "removing all images"
		podman rmi --force $(podman images -a -q) ;
	else
		print_two_columns "podman" "skipping image clean up: /root/keep-images" ;
	fi ;
	
	
}

function clean_docker_artifacts() {
	
	
	if ! is_package_installed docker-ce ; then
		print_two_columns "clean_docker_artifacts" "skipping - docker is not installed" ;
		return ;
	fi ;

	local numberOfContainers=$(docker ps --all --quiet 2>&1 | wc -l);
	print_two_columns "containers" "$numberOfContainers"

	if (( $numberOfContainers > 0 )) ; then
		print_command "Running containers" "$(docker ps --all 2>&1)"
	
		print_two_columns "docker" "stopping all running containers"
		docker stop $(docker ps --quiet) ; 
		
		print_two_columns "docker" "removing all containers"
		docker rm $(docker ps --all --quiet) ;
	fi ;
	
	local imageCount=$(docker image ls --all 2>&1 | wc -l);
	print_two_columns "imageCount" "$imageCount"
	if (( $imageCount > 1 )) && ! test -f /root/keep-images ; then
		print_two_columns "docker" "removing all images"
		docker rmi --force $(docker images -a -q) ;
	else
		print_two_columns "docker" "skipping image clean up: /root/keep-images" ;
	fi ;
}

function clean_docker() {
	print_separator "Docker cleaner"
	
	clean_docker_artifacts ;
	
	if $(is_process_running docker) ; then
		systemctl stop docker.service ; 
		wait_for_terminated "/usr/bin/dockerd" 3 root
	fi ;
	
	print_separator "removing docker rpms from system"
	
	if is_package_installed dnf ; then
		print_command \
			"dnf version unlock:  docker-ce docker-ce-cli" \
			"$(dnf versionlock delete docker-ce docker-ce-cli)" 
			
		dnf --assumeyes autoremove docker-ce
	
	else 
		print_command \
			"yum version unlock:  lvm2* docker-ce docker-ce-cli" \
			"$(yum versionlock delete 'lvm2*' docker-ce docker-ce-cli)" 
			
		yum --assumeyes --remove-leaves erase docker-ce docker-ce-cli
	
	fi ;
	
	exit_on_failure $? "failed to remove docker packages"
	
	
	print_separator "removing /etc/yum.repos.d/docker*"
	rm --force --verbose /etc/yum.repos.d/docker*
	
	print_two_columns "systemctl" "Removing start up configuration in /etc/systemd/system/docker.service.d"
	\rm --recursive --force --verbose /etc/systemd/system/docker.service.d
	
	print_two_columns "skipping" "device-mapper-persistent-data - clean manually if required"
	
	
	remove_kubernetes_networking
	ip -o link show | awk -F': ' '{print $2}' | grep docker | xargs --no-run-if-empty -L 1 ip link delete
	#  modprobe -r ipip  # can hang OS
	
	remove_kubernetes_routing

	print_separator "Purging iptable"
	iptable_wipe

}

function remove_kubernetes_networking() {
	
	print_separator "removing all virtual interfaces:  starting with br, cali, and tunl"
	ip -o link show | awk -F': ' '{print $2}' | grep br | xargs --no-run-if-empty -L 1 ip link delete
	ip -o link show | awk -F': ' '{print $2}' | grep cali | xargs --no-run-if-empty -L 1 ip link delete
	ip link delete tunl0;
	
	print_separator "Remaining Network Interfaces:"
	ip a
	
}


function remove_kubernetes_routing() {
	
	print_separator "Removing routes containing either 'bird|cali'"
	ip route show | grep -E "bird|cali" | sed -e 's/[[:space:]]*$//' | xargs --no-run-if-empty --max-lines=1 ip route del

	
	print_separator "Remaining routes, to clean up: 'ip route del <line-from-list>'"
	ip route list
	
	
}

function removeLocalItemsOnly() {

	local targetPath=${1:-/does/not/exist} ;
	
	local allItems=$(find $targetPath | wc -l) ;
	local localItems=$(find $targetPath -mount | wc -l) ;
	
	print_two_columns "cleanLocalItemsOnly()" "folder: $targetPath contains: '$allItems' items, and '$localItems' are local filesystem" ;

	if (( $allItems > $localItems )) ; then 
		print_line \
			"Warning: found mount points, using find to selectively remove items"
			
		find /var/lib/kubelet -mount -type f -exec rm --force {} \;
		find /var/lib/kubelet -mount -type d -exec rmdir {} \;
	else
		print_two_columns "note" "no mount points detected - recusively removing contents of $targetPath"
		\rm --recursive --force $targetPath/*
	fi ;
	
}

function kubernetes_manual_clean() {
	

	print_separator "kubernetes_manual_clean"
	
	print_two_columns "systemctl" "stop kubelet"
	#
	# stop kubelet & docker to enab
	#
	systemctl stop kubelet ;
	wait_for_terminated "/usr/bin/kubelet" 3 root
	
	#systemctl stop docker ;
	
	clean_docker_artifacts
	
	clean_oci_artifacts
	
	print_separator "kubernetes vsphere workaround - use CSAP umount to handle paths" ;
	umount_containers $kubernetesStorage ;
}

function clean_kubernetes() {
	
	local kubernetesStorage=${1:-/var/lib/kubelet} ;

	print_separator "Kubernetes Cleaner"

	# kubernetes_manual_clean $kubernetesStorage ;


	perform_kubeadm_reset ;
	wait_for_terminated "/usr/bin/kubelet" 3 root ;
	
	if $(is_process_running kubelet) ; then
		kubernetes_manual_clean $kubernetesStorage ;
	fi ;
	
	print_separator "removing packages: kubelet kubeadm kubectl from system"
	
	if is_package_installed dnf ; then
		print_command \
			"dnf version unlock:  kubelet kubeadm kubectl" \
			"$(dnf versionlock delete kubelet kubeadm kubectl)" 
			
		dnf --assumeyes autoremove kubelet kubeadm kubectl
	
	else 
		print_command \
			"yum version unlock:  kubelet kubeadm kubectl" \
			"$(yum versionlock delete kubelet kubeadm kubectl)" 
			
		# yum -y erase kubelet kubeadm kubectl
		yum --assumeyes --remove-leaves erase kubelet kubeadm kubectl
	
	fi ;
	
	exit_on_failure $? "failed to remove kubelet packages"
	

	
	print_two_columns "yum" "removing /etc/yum.repos.d/kubernetes*"
	rm --force --verbose /etc/yum.repos.d/kubernetes*
	
	
	print_separator "Cleaning file system"
	folderPaths="$HOME/.kube /etc/systemd/system/kubelet /etc/kubernetes /var/lib/etcd  /var/etcd/calico-data /etc/cni /var/run/calico /var/lib/calico";
	for folderPath in $folderPaths ; do
		if [ -d $folderPath ] ; then 
			print_line "Removing '$folderPath' ..."
			\rm --recursive --force  $folderPath;
		fi ;
	done
	
	# removeLocalItemsOnly /var/lib/kubelet
	
	
#	print_command \
#		"Running mount in case some resources were unmounted" \
#		"$( mount --all --verbose )"
	
	print_line "Waiting for api server port to be free"
	
	if ! $(wait_for_port_free 10250 10 "kubernetes api server") ; then
		print_with_head "Warning: kubernetes apiserver port still running";
		print_line "CSAP host dashboard port explorer can be used to identify process holding port"
		exit 90 ;
	fi ;
	
}



function find_kubernetes_vcenter_device() {

	local searchTarget="${1-kubevols}"
	
	local releaseTarget="${2-false}"
	
	local kubernetesHosts=$(kubectl get nodes | grep Ready | awk '{print $1}') ;
	
	local deviceMatches="" ;
	
	print_line "Searching for '$searchTarget' on kubernetes hosts. Release: '$releaseTarget'" 
	
	local printWarningOnce=true ;

	for khost in $kubernetesHosts ; do 
		
		simpleHost="${khost%%.*}" ; # stripped off domain 
		searchVmPath="/$GOVC_DATACENTER/$( govc find vm -name $simpleHost )"
		# print_line "searchVmPath $searchVmPath"
 		deviceMatches=$(govc device.info  \
 			-vm "$searchVmPath"  \
 			| tr '\n' ' ' \
 			| sed 's/Name:/\nName:/g' \
 			| sed 's/  */ /g' \
 			| grep $searchTarget ) ;
 			#| grep --invert-match --regexp $simpleHost ) ;
 		# print_line "$simpleHost deviceInfo $deviceInfo"	
 		
 		if [ "$deviceMatches" != "" ] ; then
 		
 			numberOfMatches=$(echo "$deviceMatches" | wc -l) ;
 			#
 			#fileDescription="$fileDescription host: $simpleHost, deviceName: $deviceName"
 			print_separator "$simpleHost - $numberOfMatches matches"
 			echo "$deviceMatches"
 			
 			if $releaseTarget ; then
 				
 				if $printWarningOnce ; then
 					print_with_head "Warning: if device is still mounted - VM may hang when delete is issued"
 				fi ;
 			
				echo "$deviceMatches" | while read deviceInfoLine ; do
				 
					vcenterDeviceName=$(echo $deviceInfoLine | awk '{print $2}') ;
					print_command \
						"govc device.remove  -vm '$searchVmPath' -keep=true  $vcenterDeviceName" \
						$(govc device.remove  -vm "$searchVmPath" -keep=true  $vcenterDeviceName)
						
				done ;
 				
		 			
 			fi ;
 			
 		fi ;
 		
	done 
}
