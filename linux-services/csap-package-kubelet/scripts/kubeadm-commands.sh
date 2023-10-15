#!/bin/bash

# set -x

function initialize_variables() {


	# used when polling for completion of last kubectl command
	max_poll_result_attempts=100
	
	helperFunctions=${1:-/opt/csap/csap-platform/bin/csap-environment.sh} ;
	
	source $helperFunctions;
	
	command=${2:-install} ;
	
	csapAgentPortAndPath=${AGENT_ENDPOINT:-:8011/CsAgent} ;
	
	masterBackupFolder=${masterBackupFolder:-/root/kubernetes-backups} ;
	
	configurationFolder=${configurationFolder:-$csapWorkingDir/configuration}
	kubeadmConfigFolder="$configurationFolder/kubeadm"
	
	kubernetesMasters=${kubernetesMasters:-false} ;
	kubernetesMasterDns=${kubernetesMasterDns:-not-specified};
	
	kubernetesStorage=${kubernetesStorage:-/var/lib/kubelet};
	kubeletExtraArgs=${kubeletExtraArgs:-};

	kubernetesRepo=${kubernetesRepo}
	clusterToken=${clusterToken:dummy}
	k8Version=${k8Version:-1.16.11-0}
	k8ImageVersion=${k8ImageVersion:-v1.16.11} 
	
	isForceIpForApiServer=${isForceIpForApiServer:-false} ;
	
	k8sConfFile="/etc/sysctl.d/k8s.conf"
	kubeadmPackage="kubeadm";
	k8sLocalRepo="/etc/yum.repos.d/kubernetes.repo"
	
	
	calico_ip_method=${calico_ip_method:-interface=en.*};
	
}


function getPrimaryMaster() {
	
	echo $(awk '{ print $1; }' <<< $kubernetesMasters) ;

}

function is_primary_master() {

	if [ "$kubernetesAllInOne" == "true" ] ; then
		true;
	
	else
		
		# redirect to error to not impact choice
		# >&2 echo $( print_with_head "primaryMaster: $primaryMaster")
		
		if [[ $(getPrimaryMaster)  == $(hostname --short) ]] ; then 
			true ;
			
		else 
			false ;
		fi ;
	
	fi ;
}


function is_master() {
	
	if [ "$kubernetesAllInOne" == "true" ] ; then
		true;
		
	elif [[ $kubernetesMasters == *$(hostname --short)* ]] ; then 
		true ;
		
	else
		false ;
	fi ;
}

function install() {


	print_with_head "install() - kubelet, kubeadm, and dependencies" 
	
	rpm -q $kubeadmPackage &> /dev/null ;
	local kubeAdmQueryCode="$?" ;
	
	if [ "$kubeAdmQueryCode" == 0 ] ; then 
		print_with_head "$kubeadmPackage is already installed, skipping package installation";
		print_line "run uninstaller to remove" ;
		return ;
	fi ;
	
	print_line	"current directory: '$(pwd)'"
	
	kube_port_check "pre-os"
	
	install_os
	
	if $(is_primary_master)  ; then 
		
		install_primary_master ;
		
	elif $( is_master ) ; then
			
		install_other_master ;
		
	else
		
		install_worker
		
	fi ;
	
	wait_for_node_ready
	
	print_with_head "install() - completed"
	
}

function kube_port_check() {

	local description="$1"
	
	local reservedPorts="9099 10250 10251 10252 10253 10254 10255 10256 10257 10258 10259 30080"
	
	for port in $reservedPorts ; do
		if ! $(wait_for_port_free $port 10 "kubernetes ports: $description") ; then
			print_with_head "Failed to get kubernetes apiserver port - aborting install";
			print_line "CSAP host dashboard port explorer can be used to identify process holding port"
			exit 90 ;
		fi ;
	done ;

#	if ! $(wait_for_port_free 10250 10 "kubernetes api server: $description") ; then
#		print_with_head "Failed to get kubernetes apiserver port - aborting install";
#		print_line "CSAP host dashboard port explorer can be used to identify process holding port"
#		exit 90 ;
#	fi ;
#
#	if ! $(wait_for_port_free 10251 10 "kube-scheduler: $description") ; then
#		print_with_head "Failed to get kubernetes kube-scheduler port - aborting install";
#		print_line "CSAP host dashboard port explorer can be used to identify process holding port"
#		exit 90 ;
#	fi ;
#
#	if ! $(wait_for_port_free 10252 10 "kube-controller: $description") ; then
#		print_with_head "Failed to get kubernetes kube-controller port - aborting install";
#		print_line "CSAP host dashboard port explorer can be used to identify process holding port"
#		exit 90 ;
#	fi ;
}

nfsPackage="nfs-utils" ;
function install_os() {
	
	print_with_head "install_os() - configuring kubernetes dependencies"
	
#	print_command \
#		"Running mount in case some resources were unmounted" \
#		"$(mount --all --verbose)"
	
	\rm --force --verbose $k8sLocalRepo ;
	if [[ "$kubernetesRepo" == *packages.cloud.google.com* ]] ; then 
		print_with_head "GOOGLE REPO Detected: Updating '$k8sLocalRepo' with kubernetesRepo: '$kubernetesRepo'"
		
		backup_and_replace  $k8sLocalRepo "$configurationFolder/linux-system/etc-yum.repos.d-kubernetes.repo"  ;
		
		replace_all_in_file "_K8_URL_" "$kubernetesRepo" $k8sLocalRepo
		
		if is_package_installed dnf ; then
			# legacy yum repos required \$ for variables - dnf does no
			print_two_columns "dnf support" "removing backslashes '\' from repo definition "
			replace_all_in_file "\\\\" '' $k8sLocalRepo
		fi;
		
		# this will still output checks
		add_repo_with_setup_checks
		
	else
	
		add_repo_with_setup_checks $kubernetesRepo
			
	fi ;
	

	if [ "$kubernetesAllInOne" == "true" ] ; then
		print_separator "Leaving swap enabled for all in one"	
		
	else
		print_line "Disabling swap (no k8s support) 'swapoff -a'"
		swapoff -a 
		
		sed -i '/swap/s/^#//g' /etc/fstab # uncomment if previously commented
		sed -i '/swap/s/^/#/g' /etc/fstab # comment out line
	fi
	
	print_command \
		"Security-Enhanced Linux" \
		"$(sestatus)"
		
	#seLinuxStatus=$(sestatus | grep -i "selinux status" | awk '{print $3}') ;
	#print_with_head "seLinuxStatus: '$seLinuxStatus'"
	#if [ "$seLinuxStatus" != "disabled" ] ; then
#		print_with_head "disabling selinux: 'setenforce 0'"
#		setenforce 0
#		sed -i --follow-symlinks 's/SELINUX=enforcing/SELINUX=disabled/g' /etc/sysconfig/selinux
#		
#		print_with_head "/etc/sysconfig/selinux contents:"
#		cat /etc/sysconfig/selinux
	#fi ;

	local packageCommand="yum" ;
	if is_package_installed dnf ; then 
		 packageCommand="dnf" ;
	fi ;
	
	local etcKube="/etc/kubernetes" ;
	if test -r $etcKube ; then 
		print_with_head "Warning - found $etcKube - blowing away folder as it will prevent install from proceding" ;
		rm --force --recursive --verbose $etcKube ;
	fi ;
	
	print_separator "Installing packages: '$packageCommand install --assumeyes kubelet-$k8Version $kubeadmPackage-$k8Version kubectl-$k8Version'"
	$packageCommand install --assumeyes kubelet-$k8Version $kubeadmPackage-$k8Version kubectl-$k8Version
	exit_on_failure $? "failed to install kubernetes"
	
	print_command \
		"$packageCommand version lock: 'kubelet-$k8Version $kubeadmPackage-$k8Version kubectl-$k8Version'" \
		"$($packageCommand versionlock kubelet-$k8Version $kubeadmPackage-$k8Version kubectl-$k8Version)" 
	
	
	if [ "$kubeletExtraArgs" != "" ] ; then
		print_with_head "WARNING: found kubeletExtraArgs '$kubeletExtraArgs'. Recommended action: update kublet service kubeadm files in csap definition"
		local kubeletDefaults="/etc/sysconfig/kubelet";
		local kubeletExtraArgsFull="$kubeletExtraArgs" ;
		print_with_head "Setting up '$kubeletDefaults' with parameters: '$kubeletExtraArgsFull'" ;
		append_file "KUBELET_EXTRA_ARGS=\"$kubeletExtraArgsFull\"" $kubeletDefaults
	fi ;
	
	
	print_separator "Verifying docker cgroup"
	local currentCgroupInfo=$(docker info | grep -i cgroup)
	if [[ "$currentCgroupInfo" =~  "systemd"  ]] ; then 
		print_line "Passed: Docker cgroup driver: '$currentCgroupInfo'"
		# cat $admConfFile
	else
		print_with_head "Warning: docker cgroupdriver is not systemd, refer to: https://github.com/kubernetes/kubeadm/issues/1394"
		print_line "current cgroup: '$currentCgroupInfo'"
		
#		local dockerConfigFile="/etc/docker/daemon.json" ;
#		print_line "Updating $dockerConfigFile to use cgroupfs" ;
#		backup_file 
#		local cgroupLine='\\t"exec-opts": ["native.cgroupdriver=systemd"],' ;
#	 	sed -i "0,/{/a $cgroupLine" $dockerConfigFile ;
#	 	cat $dockerConfigFile ;
#	 	
#	 	print_with_head "restarting docker"
#	 	systemctl restart docker ;
#	 	sleep 3
#	 	systemctl status docker ;
	fi ;
	
	
	print_command \
		"ip_config_check" \
		"$(cat /proc/sys/net/bridge/bridge-nf-call-iptables)" ;
	
	backup_and_replace  $k8sConfFile "$configurationFolder/linux-system/etc-sysctl.d-k8s.conf"  ;
	print_separator "Reload kernel settings 'sysctl --system'"
	sysctl --system
	
	
	print_separator "reloading system manager: 'systemctl daemon-reload'"
	systemctl daemon-reload	;
	
	
	print_with_head "Enable transparent masquerading and facilitate Virtual Extensible LAN (VxLAN) traffic for communication between Kubernetes pods across the cluster"
    modprobe br_netfilter
    echo '1' > /proc/sys/net/bridge/bridge-nf-call-iptables	
    echo '1' > /proc/sys/net/bridge/bridge-nf-call-ip6tables	
	
	
	
	print_separator "Pulling kubernetes images using version: '$k8ImageVersion'"
	
	local kubeadmParams="--kubernetes-version $k8ImageVersion"
	if [[ "$imageRepository" != "none" ]] ; then
		kubeadmParams="$kubeadmParams --image-repository $imageRepository" ;
	fi ;
	eval kubeadm $kubeadmParams config images pull
	
	
	configure_cloud_provider ;
	
	configure_firewall ;
}

function configure_cloud_provider() {

	local cloudProvider="$configurationFolder/cloud-provider" ;
	if test -d $cloudProvider ; then 
		
		print_command \
			"Found: '$cloudProvider', copying to /etc/kubernetes" \
			"$( \cp --recursive --verbose --force $cloudProvider/* /etc/kubernetes)"
		
	fi ;
	
}

function configure_firewall() {
	print_with_head "Firewall rules update: only done if firewalld enabled"
    firewall-cmd --permanent --add-port=6443/tcp
    firewall-cmd --permanent --add-port=2379-2380/tcp
    firewall-cmd --permanent --add-port=10250/tcp
    firewall-cmd --permanent --add-port=10251/tcp
    firewall-cmd --permanent --add-port=10252/tcp
    firewall-cmd --permanent --add-port=10255/tcp
    firewall-cmd --reload
}

function install_worker() {
	
	print_with_head "Running worker installation"
	
	local numMasters=$( wc -w <<< $kubernetesMasters) ;
	
	wait_for_masters_ready $numMasters ;
	
	get_and_install_credentials_from_primary
	
	run_kubeadm
	
}


function wait_for_masters_ready() {

	#
	# note std err is used to show progress, and avoid impacting output
	# 
	local numberExpected=$1
	print_line2 "Contacting primary master, waiting for '$numberExpected' masters ready" ;

	
	local readyUrl="http://$(getPrimaryMaster)$csapAgentPortAndPath/os/kubernetes/masters/ready?numberExpected=$numberExpected&"
	print_line2 "readyUrl: '$readyUrl'"
	print_line2 "Note: this may take a few minutes if master installation is in progress."
	
	local max_attempts="200" ;
	local commandOutput ;
	for i in $(seq 1 $max_attempts); do
		print_separator2 "attempt $i of $max_attempts"
		
		commandOutput=$(curl --silent $readyUrl) ;
		
		if [[ $commandOutput == "true" ]] ; then 
			break;
		fi ;
		sleep 5 ;
    done
	
	if [[ $commandOutput == "true" ]] ; then 
		print_line2 "Masters ready"
		
	else
		print_error "failed to retrieve join command: '$joinUrl'"
		exit 96;
	fi ;


}

credentialFile="$HOME/.kube/config" ;
function get_and_install_credentials_from_primary() {


	print_with_head "Retrieving credentials from primary master" ;
	
	if [ -e $HOME/.kube ] ; then
		print_line "Found an existing credential folder, deleting: '$HOME/.kube'"
		\rm -rf $HOME/.kube
	fi ;

	local serviceName="$csapName"_"$csapPrimaryPort"
	
	local credentialUrl="http://$(getPrimaryMaster)$csapAgentPortAndPath/os/folderZip?path=.kube/config&token=$clusterToken&service=$serviceName"
	print_line "credentialUrl: '$credentialUrl'"
	print_line "Note: this may take a few minutes if primary master installation is in progress."
	
	local max_attempts="200" ;
	local returnCode="99" ;
	local commandOutput ;
	for i in $(seq 1 $max_attempts); do
		print_separator "attempt $i of $max_attempts"
		
		commandOutput=$(wget --no-verbose --output-document kubeCred.zip $credentialUrl 2>&1) ;
		returnCode="$?";
		
		if (( $returnCode == 0 )) ; then
			print_line "Credential retrieved: unzipping $(pwd)/kubeCred.zip to '$HOME/.kube'"
			unzip  -q -o -d $HOME/.kube kubeCred.zip
			returnCode="$?";
			if (( $returnCode == 0 )) ; then
			
				chmod 700 -R $HOME/.kube
				
				break;
			else
				print_error "Failed extracting zip"
			fi ;
		else
			sleep 5;
		fi ;
    	
    done
	
	if (( $returnCode != 0 )) ; then
	
		print_error "Failed to retrieve credential: '$credentialUrl'"	
		exit 96;
	fi ;


}

function retrieve_join_from_primary_master() {
	
	#
	# Note print*2 is used to separate output from joinCommand
	#

	local nodeType=$1

	print_with_head2 "Retreiving join from primary master, type: $nodeType" ;
	
	wait_for_masters_ready 1 ;

	local serviceName="$csapName"_"$csapPrimaryPort"
	
	local joinUrl="http://$(getPrimaryMaster)$csapAgentPortAndPath/os/kubernetes/join?type=$nodeType&token=$clusterToken&"
	print_line2 "joinUrl: '$joinUrl'"
	print_line2 "Note: this may take a few minutes if primary master installation is in progress."
	
	local max_attempts="300" ;
	local commandOutput="" ;
	for i in $(seq 1 $max_attempts); do
		print_separator2 "attempt $i of $max_attempts"
		
		commandOutput=$(curl --silent $joinUrl) ;
		
		if [[ "$commandOutput" == kubeadm* ]] ; then 
			break;
		fi ;
		sleep 5 ;
    done
    
    
	
	if [[ $commandOutput == kubeadm* ]] ; then 
		echo $commandOutput ;
		
	else
		print_with_head "ERROR: failed to retrieve join command: '$joinUrl'"
		exit 96;
	fi ;

}

#function join_using_primary_master() {
#
#	local nodeType=$1
#	
#	local commandOutput=$(retrieve_join_from_primary_master $nodeType) ;
#	
#	kube_port_check "pre-secondary-$nodeType"
#	
#	print_with_head "Running: '$commandOutput'"
#	eval $commandOutput
#
#}

function check_for_swap_ignore() {
	
	if [ "$kubernetesAllInOne" == "true" ] ; then
		echo "--ignore-preflight-errors='swap'"
	else
		echo "" 
	fi
}


function install_other_master() {
	
	#
	# ref. https://kubernetes.io/docs/setup/production-environment/tools/kubeadm/high-availability/
	#
	
	print_with_head "Host is a secondary master"
	
	# join_using_primary_master master
	wait_for_masters_ready 1 ;
	
	# kubeadm join csap-dev01.yourcompany.org:6443 --token iesfbd.u41svjpv1j95sfda --discovery-token-ca-cert-hash sha256:5bad093adb7b2c330ad2ae1e7268ea3b5b304d2eb7d8b7efd74e4b830b53cd6a --control-plane --certificate-key a2af30f79db74d69fa604a746a826987d045519ad72e974428fae9f1fc5189c7
	local controlJoinCommand=$( retrieve_join_from_primary_master master) ;
	local certKey=$( echo $controlJoinCommand | awk '{ print $NF; }' );
	print_line "controlJoinCommand: $controlJoinCommand \n\t certKey: '$certKey'" ;
	
	local kubeadmConfigurationFile=$(echo $kubeadmParameters | awk '{print $3}') ;
	replace_all_in_file "CONTROL_CERT_KEY" "$certKey" "$kubeadmConfigurationFile"
			
	run_kubeadm
	
	install_master_credentials
}

function install_primary_master() {
	
	local updatedCusterToken=$(kubeadm token generate)
	print_with_head "Host is the primary master. For security, update clusterToken: '$updatedCusterToken'"

	run_kubeadm
	
	install_master_credentials
	
	
	if [ -f "$masterBackupFolder/load-on-install-yes" ] ; then
	
		print_with_head "Bypassing network install due to backupRestore"
		
	else 
	
		install_networking
		
	fi ;

}



function install_master_credentials() {
		
	print_separator  "Creating master credentials: '$HOME/.kube'"
	\rm -rf $HOME/.kube
	mkdir --parents --verbose $HOME/.kube
	cp -i /etc/kubernetes/admin.conf $credentialFile
	chown $(id -u):$(id -g) $HOME/.kube/config
	
}


function run_kubeadm() {
	
	print_section "$(date) kubeadm reset"
	
	systemctl enable kubelet
	systemctl start kubelet
	systemctl daemon-reload
	sleep 5
	
	perform_kubeadm_reset
	
	
	mkdir --parents --verbose $kubernetesStorage
	
	if $(is_primary_master) ; then
		if [ -f "$masterBackupFolder/load-on-install-yes" ] ; then
			print_with_head "Triggering scripts/backup-restore.sh"
			scripts/backup-restore.sh restore
		else 
			print_with_head "Installing a new cluster. Add the following file to trigger backup recovery:'$masterBackupFolder/load-on-install-yes'"
		fi ;
	fi ; 
	
	kube_port_check "pre-kubeadm"
	
	
	print_section "$(date) kubeadm init"
	
	print_command \
		"kubeadmParameters" \
		"'$kubeadmParameters'"
		
	kubeadm --v=5 $kubeadmParameters
	
	local adm_result_code=$?

	if (( $adm_result_code != 0 )) ; then
		print_with_head "Error - kubeadm error code: '$adm_result_code'. Unable to configure kubelet"	;
		exit 95;
	fi ;
		
}


function add_component() {
	
	local description="$1"
	local source="$2"
	
	print_with_head "Adding: $description \n\t '$source'"
	#kubectl apply -f configuration/calico.yaml
	kubectl create --insecure-skip-tls-verify=true -f $source
}

function install_networking() {
	
	print_with_head "network install: calico"
	
	
	local calicoSpec="$configurationFolder/network/calico.yaml"
	print_line "updating variable: CALICO_IP_METHOD: '$calico_ip_method'"
	replace_all_in_file "\$ip_detection" "$calico_ip_method" $calicoSpec
	
	
	print_line "updating variable: veth_mtu: '$veth_mtu'"
	replace_all_in_file "\$veth_mtu" "$veth_mtu" $calicoSpec
	
	local backendParameter="auto" ;
	if is_package_installed dnf ; then
		print_line "found dnf - iptables-nft is in use"
		# backendParameter="nft" ;
	fi ;
	print_line "updating variable: FELIX_IPTABLESBACKEND: '$backendParameter'"
	replace_all_in_file "\$ipTablesBackend" "$backendParameter" $calicoSpec
	
	add_component \
		"networking, refer to: https://docs.projectcalico.org/archive/v3.18/getting-started/kubernetes/" \
		$calicoSpec

		
	wait_for_calico_node_running_and_ready
    
    kubectl get pods --all-namespaces | grep calico
    
}


function wait_for_calico_node_running_and_ready() {
	
	local currentHostName=$(hostname --short)
	
	print_with_head "Wait for calico ready on host: '$currentHostName'"
	
	local namespace="--namespace=kube-system"
	
	local calicoPodOnCurrentHost="" ;

	for i in $(seq 1 $max_poll_result_attempts); do
		sleep 5;
		print_separator "attempt $i of $max_poll_result_attempts"
		calicoPodOnCurrentHost=$(kubectl get pods -o wide $namespace | grep "$currentHostName" | grep 'calico-node' | awk '{print $1}') ;

		if [ "$calicoPodOnCurrentHost" == ""  ] ; then
			continue ;
		fi; 

		print_line "calicoPodOnCurrentHost: '$calicoPodOnCurrentHost'"
		calicoSummary=$(kubectl get pods $calicoPodOnCurrentHost $namespace)
		print_line "$calicoSummary"
		
		readyCount=$(echo "$calicoSummary"| grep -i running | awk '{ print $2}') 

		#numberReady=$(kubectl get pods $calicoPodOnCurrentHost $namespace | grep " Running" | wc -l)
		#if (( "$numberReady" >= 1 )) ; then
		if [[ "$readyCount" == "1/1" || "$readyCount" == "2/2" || "$readyCount" == "3/3" ]] ; then
			break;
		fi ;
	done
	
	check_for_max_attempts $i $max_poll_result_attempts "wait_for_calico_node_running_and_ready() 'Running' failed - verify host setup and try again."

	print_with_head "Polling for HealthReport{Live:true, Ready:true}"

	local noBindInLogs=0
	for i in $(seq 1 $max_poll_result_attempts); do
		
		sleep 2;
		print_separator "attempt $i of $max_poll_result_attempts, last 10 lines of logs (but checking --since=1h)"

		kubectl logs $calicoPodOnCurrentHost $namespace --container=calico-node --tail=10

		local numLinesWithReady=$(kubectl logs $calicoPodOnCurrentHost $namespace --container=calico-node --since=1h | grep "HealthReport{Live:true, Ready:true}" | wc -l)
		if (( $numLinesWithReady > 0 )) ; then
			print_with_head "Assuming calico is initialized successfully - found 'HealthReport{Live:true, Ready:true}' " ;
			break;
		fi ;
		# numLinesWithBind=$(kubectl logs $calicoPodOnCurrentHost $namespace --container=calico-node --since=1h | grep binderror | wc -l)
		#local numLinesWithBindErrors=0
		#if (( $numLinesWithBind == 0 )) && (( $numLinesWithReady > 0)) ; then
		#	((noBindInLogs++))
		#	if (( $noBindInLogs > 3 )) ; then
		#		print_with_head "Assuming calico is initialized successfully - no bind errors in logs "
		#		break;
		#	fi ;
		#fi ;
	done
	check_for_max_attempts $i $max_poll_result_attempts "wait_for_calico_node_running_and_ready() 'No Bind Errors' failed - verify host setup and try again."
}

function check_for_max_attempts() {
	
	attempts_made=$1;
	attempts_max=$2;
	exit_message="$3"
	
	if (( $attempts_made >= $attempts_max )) ; then
    	print_with_head "Did not find expected result: '$exit_message'"
		exit 99;
	fi ;
	
}

function wait_for_node_ready() {
	
	print_with_head "Waiting for node: '$(hostname)' to be in ready state"
	kubectl get nodes
	for i in $(seq 1 $max_poll_result_attempts);
    do
        sleep 5;
        print_separator "attempt $i of $max_poll_result_attempts: \t $(kubectl get nodes | grep $(hostname))"
        numberReady=$(kubectl get nodes | grep $(hostname) | grep " Ready" | wc -l)
		if (( "$numberReady" > 0 )) ; then
			break;
		fi ;
    done
    print_line ""
    
    kubectl get nodes
    
    check_for_max_attempts $i $max_poll_result_attempts "wait_for_node_ready() failed - verify host setup and try again."
    

    
    wait_for_calico_node_running_and_ready
}

function start() {
	
	if [ ! -f "$credentialFile" ] ; then
		print_with_head "WARNING: Missing credentialFile: '$credentialFile'"
		exit 98;
	fi
	
	print_with_head "start() - Using systemctl to start kubelet"
	
	systemctl enable kubelet
	systemctl start kubelet
	
	sleep 2
	
	print_with_head "systemctl status kubelet.service"
	systemctl status kubelet

	install_csapUser_credentials
	
}

function install_csapUser_credentials() {
	
	#
	# all commands are run using csapUser
	#
	
	csapUserHome="$(eval echo ~$csapUser)"
	print_with_head "Updating '$csapUser' kubernetes credentials: '$csapUserHome/.kube'"
	
	\rm -rf $csapUserHome/.kube
	mkdir --parents --verbose $csapUserHome/.kube
	cp -i $HOME/.kube/config $csapUserHome/.kube/config
	chown -R $(id -u $csapUser):$(id -g $csapUser) $csapUserHome/.kube
	chmod 700 -R $csapUserHome/.kube
	
}

function stop() {
	
	print_with_head "Killing all kubectl processes, stopping and disabling kubelet service"
	
	killall kubectl

	systemctl stop kubelet
	systemctl disable kubelet

}

function clean_up_network() {
	
	remove_kubernetes_networking ;
	
	remove_kubernetes_routing ;
	
	# ref: https://blog.heptio.com/properly-resetting-your-kubeadm-bootstrapped-cluster-nodes-heptioprotip-473bd0b824aa
	#print_with_head "Clean up iptables entries: NOT RUNNING as it will strip docker as well: iptables -F && iptables -t nat -F && iptables -t mangle -F && iptables -X"
	# iptables -F && iptables -t nat -F && iptables -t mangle -F && iptables -X
	
#	print_command "before: network interfaces using ip a" "$(ip a)" ;
#	
#	print_command \
#		"Cleaning up kubernetes using: ip link delete tunl0" \
#		"$(ip link delete tunl0)" ;
#	
#
#	print_with_head "To really clean: run 'modprobe -r ipip' Note: this may hang your host requiring reboot"
#	
#	print_command \
#		"after: network interfaces using ip a" \
#		"$(ip a)" ;
#	
#	local numberOfCaliRoutes=$(iptables --list-rules | grep cali | wc -l) ;
#	if (( $numberOfCaliRoutes > 0 )) ; then
#		print_with_head "WARNING: found '$numberOfCaliRoutes' calici rules remaining. Use 'iptables --list-rules' to review."	
#	fi
#	
#	print_command \
#		"Removing routes containing either 'bird|cali'" \
#		"$(ip route show | grep -E "bird|cali" | sed -e 's/[[:space:]]*$//' | xargs --max-lines=1 ip route del)" ;
#		
#	
#	print_command "after: network interfaces using ip routes" "$(ip route show)" ;
	
	local numberOfRunningContainer=$(docker ps --all  | tail -n +2 | wc -l) ;
		
	if (( $numberOfRunningContainer == 0 )) ; then
		print_with_head "Cleaning ip table rules"
		iptable_wipe
		check_for_webserver_port_restore
	
	else 
		print_with_head "Found running containers: use csap iptable_wipe, or iptables -F && iptables -t nat -F && iptables -t mangle -F && iptables -X"
		 
		print_command "docker ps --all" "$(docker ps --all)" ;
	fi ;
	
}


function clean() {

	stop
	
	clean_kubernetes $kubernetesStorage
	
	clean_up_network
	
	\rm -rf $k8sConfFile
	systemctl daemon-reload
	systemctl reset-failed
	
	

	if [ "$kubernetesAllInOne" == "true" ] ; then
		print_with_head "AllInOne: Skipping swap restore"	
		
	else
		print_with_head "Restoring swap in /etc/fstab if it was commented out during install"	
		sed -i '/swap/s/^#//g' /etc/fstab # uncomment if previously commented
	fi
	
	k8ContainerCount=$(docker ps -a | grep k8s | wc -l)
	if (( $k8ContainerCount != 0 )) ; then 
		print_with_head "Found '$k8ContainerCount' containers in docker. Verify clean up is successfull"	
	fi
	

}


initialize_variables $* ;

case "$command" in
	
	install)
		# print_separator "triggering clean"
		# clean
		install
		;;
	
	clean)
		clean
		;;
	
	stop)
		stop
		;;
	
	start)
		start
		;;
	
	 *)
            echo "Usage: $0 {start|stop|restart|clean}"
            exit 1
esac




