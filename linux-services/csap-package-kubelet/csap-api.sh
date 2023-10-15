#!/bin/bash


function installation_settings() {
	
	configurationFolder=${configurationFolder:-$(pwd)/configuration}
	
	# optional nfs configuration
	
	csapUser=$(whoami) ;
	nfs_server=${nfs_server:-none} ;
	nfs_path=${nfs_path:-none} ;
	nfs_mount=${nfs_mount:-none} ;
	nfs_options=${nfs_options:-'vers=3'} ;
	
	masterBackupFolder=${masterBackupFolder:-/root/kubernetes-backups} ;
	# handle path references
	masterBackupFolder=$(eval echo $masterBackupFolder)
	
	veth_mtu=${veth_mtu:-1440};
	
	# wildcard support, but if name preferred interface=ens192.* or eth0.*
	calico_ip_method=${calico_ip_method:-auto};
	if [ "$calico_ip_method" == "auto" ] ; then
		calico_ip_method='interface=eth0' ; # default to centos primary interface
		if (( $(ip a | grep ens192: | wc -l) > 0 )) ; then
			# print_line "calico auto discovery: ip a matched ens192:" ; 
			calico_ip_method='interface=ens192'
			
		elif (( $(ip a | grep enp0s3: | wc -l) > 0 )) ; then
			# print_line "calico auto discovery: ip a matched enp0s3:" ; 
			calico_ip_method='interface=enp0s3'
		else
			print_with_head "WARNING: calico auto discovery: ip a did NOT match ens192, or enp0s3" ; 
		fi
	fi
	

	etcdFolder=${etcdFolder:-/var/lib/etcd}
	
	kubeadmParameters="not-initialized" ;
	
	
	singleMasterUntaint=${singleMasterUntaint:-yes}
	
	imageRepository=${imageRepository:-none}
	cipherSuites=${cipherSuites:-none}
	if [[ $cipherSuites == "default-secure" ]] ; then
		cipherSuites="TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305,TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA,TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA,TLS_RSA_WITH_AES_128_GCM_SHA256,TLS_RSA_WITH_AES_256_GCM_SHA384,TLS_RSA_WITH_AES_128_CBC_SHA,TLS_RSA_WITH_AES_256_CBC_SHA"
	fi ;
	
	strictDirectives=${strictDirectives:-none}
	if [[ $strictDirectives == "default-strict" ]] ; then
		strictDirectives="max-age=31536000,includeSubDomains,preload" ;
	fi ;

	kubeCommandsFile=${commandsFile:-$csapWorkingDir/scripts/kubeadm-commands.sh}
	
	kubernetesMasters=${kubernetesMasters:-notSpecified};
	
	kubernetesMasterDns=${kubernetesMasterDns:-not-specified};
	
	kubernetesStorage=${kubernetesStorage:-/var/lib/kubelet};
	kubeletExtraArgs=${kubeletExtraArgs:-};
	
	
	kubernetesAllInOne=${kubernetesAllInOne:-false}
	
	if [ "$kubernetesMasters" == "notSpecified" ] ; then 
		print_with_head "kubernetesMasters is a required environment variable. Add it to service parameters"
		exit ;
	fi
	
	kubernetesRepo=${kubernetesRepo:-https://packages.cloud.google.com/yum}
	
	clusterToken=${clusterToken:-token-not-set}
	
	if [[ "$clusterToken" == "token-not-set" ]] ; then
		print_with_head "clusterToken variable not found - verify kubelet config maps"
		exit 77 ;
	fi ;
	
	

	# note the semantic differences in version string: 1.21.4-0 vs. 1.21.4
	local kubernetesVersion=${kubernetesVersion:-1.23.3} ;
	k8Version=${k8Version:-${kubernetesVersion}-0}
	k8ImageVersion=${k8ImageVersion:-v$kubernetesVersion} 
	
	# alternate: 10.112.0.0/12
	k8PodSubnet=${k8PodSubnet:-192.168.0.0/16} 
	
	isForceIpForApiServer=${isForceIpForApiServer:-false} ;
	
	print_section "CSAP kubelet package" ;
	print_two_columns "kubernetesVersion" "$kubernetesVersion" ;
	print_two_columns "rpm version" "$k8Version" ;
	print_two_columns "image version" "$k8ImageVersion" ;
	print_two_columns "k8PodSubnet" "$k8PodSubnet" ;
	
	print_two_columns "singleMasterUntaint" "$singleMasterUntaint" ;
	print_two_columns "imageRepository" "$imageRepository" ;
	print_two_columns "api server cipherSuites" "$cipherSuites" ;
	print_two_columns "api server strictDirectives" "$strictDirectives" ;
	
	
	
	print_two_columns "kubernetesRepo" "$kubernetesRepo" ;
	
	print_two_columns "calico_ip_method" "$calico_ip_method" ;
	print_two_columns "veth_mtu" "$veth_mtu" ;
	
	print_two_columns "kubernetesMasters" "$kubernetesMasters" ;
	print_two_columns "kubernetesMasterDns" "$kubernetesMasterDns" ;
	print_two_columns "kubernetesAllInOne" "$kubernetesAllInOne" ;
	print_two_columns "kubernetesStorage" "$kubernetesStorage" ;
	print_two_columns "isForceIpForApiServer" "$isForceIpForApiServer" ;
	print_two_columns "etcdFolder" "$etcdFolder" ;
	print_two_columns "masterBackupFolder" "$masterBackupFolder" ;
	print_two_columns "kubernetesAllInOne" "$kubernetesAllInOne" ;
	

}

installation_settings ;

function api_package_build() { 
	print_line "api_package_build not used" ;
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
function is_worker() {
	
	if [ "$kubernetesAllInOne" == "true" ] ; then
		true;
		
	elif [[ $kubernetesMasters == *$(hostname --short)* ]] ; then 
		false ;
		
	else
		true ;
	fi ;
}

function remove_kube_credential() {

	
	if test -d $HOME/.kube ; then
		print_line "Found an existing credential folder, deleting: '$HOME/.kube'"
		rm --recursive --force --verbose $HOME/.kube
	fi ;
	
}

function api_package_get() { 
	print_line "csap api_package_get in kubelet package"
	remove_kube_credential
}



function stop_kubectl_proxy() {
	existingProxyPid=$(pgrep -f "kubectl proxy")
	
	if [[ $existingProxyPid ]] ; then 
		print_with_head "Found an existing pid '$existingProxyPid', running kill";
		# pkill
		/usr/bin/kill --signal SIGTERM $existingProxyPid
	fi	
}


function api_service_kill() {
	
	api_service_stop
	
	if [ "$isClean" == "1" ] ||  [ "$isSuperClean" == "1"  ] ; then
		run_command clean ;
		remove_nfs
		remove_kube_credential
	fi ;
}

function api_service_stop() {
	
	stop_kubectl_proxy ;
	
	print_with_head "Draining all pods from $(hostname --long)" 
	kubectl drain --ignore-daemonsets --delete-local-data $(hostname --long)  ;
	
	run_command stop ;

}

#
# startWrapper should always check if $csapWorkingDir exists, if not then create it using $packageDir
# 
#
function api_service_start() {
	
	print_with_head "Starting kubelet"
	
	if $(is_process_running /sbin/mount) ; then
		print_error "Found /sbin/mount running: file system should be quiesced prior to installing kubernetes"
		wait_for_terminated /sbin/mount 200 "root"
		if $(is_process_running /sbin/mount) ; then
			print_error "sbin/mount is still running. Possible solution: reboot host"
			exit 99 ;
		fi ;
	fi ;
	
	cd $csapWorkingDir
	
	mkdir --parents --verbose $csapLogDir
	
	stop_kubectl_proxy
	
    copy_csap_service_resources
    
    configure_vcenter_integation
    
    configure_cloud_provider
    
    configure_kubeadm
    
    
	# nfs is added early as it may be used for recovery install
    add_nfs

	# install only occurs if not already present 
	run_command install
	
	install_return_code="$?" ;
	if (( $install_return_code != 0 )) ; then
		print_line "Install aborted."
		exit $install_return_code ;
	fi ;
	
	cd $csapLogDir
	if [ ! -e link-var-log-messages ] ; then 
		add_link_in_pwd /var/log/messages
	fi ;
	
	cd $csapWorkingDir ;
	
	run_command start
	
	osConfiguration="$csapWorkingDir/os-configuration-folders" ;
	
	if [ ! -e $osConfiguration ] ; then 
		
		print_with_head "api_service_start(): Creating configuration shortcuts in $osConfiguration"
		mkdir -p $osConfiguration ;
		cd $osConfiguration ;
		
		add_link_in_pwd "/var/lib/kubelet"
		add_link_in_pwd "/var/lib/docker"
		add_link_in_pwd "/etc/kubernetes"
		add_link_in_pwd "/etc/docker"
		add_link_in_pwd "/etc/sysctl.d/k8s.conf"
		add_link_in_pwd "/etc/systemd/system/kubelet.service.d"
		
		add_link_in_pwd "/var/log/calico/cni/cni.log"
		
		
		createVersion
		
	fi ;
	
	check_to_untaint ;
		
	if $( is_primary_master ) ; then
		local nodeCount=$(( $( wc -w <<< $csapPeers ) + 1 ));
		print_with_head "Waiting for '$nodeCount' calico-nodes to be started, prior to deployments on primary master.";
		print_line "NOTE: host dashboard may be used to terminate:  csap-start.sh if desired".
		#'--tail=2000'
		# wait_for_pod_log calico-node 'HealthReport{Live:true, Ready:true}' "$nodeCount" 'kube-system' 500 '--since=1h'
		wait_for_pod_running calico-node "$nodeCount" 'kube-system' 500 ;
		
		
		if $(is_process_running crio) ; then
		
			print_section "triggering dns restart for crio"
			kubectl rollout restart deployment coredns --namespace=kube-system ;
			
		fi ;
		
		$csapWorkingDir/scripts/sanity-tests.sh dns_tests 
		
	fi ;

	print_command \
		"Renable scheduling on $(hostname --long)" \
		$(kubectl uncordon $(hostname --long) )  ;
	
	
	  
	post_start_status_check ;

}

function configure_vcenter_integation() {
	
	if [ "$GOVC_DATACENTER" != "" ]  ; then
	
    	# local originalConfiguration="$csapWorkingDir/configuration.original"
    	
		cd $csapWorkingDir ;
		vcenterEnvFile="$csapWorkingDir/vcenter-env.sh" ;
		
		print_with_head "Building: $vcenterEnvFile"
		
		\rm --recursive --force --verbose $vcenterEnvFile ;
		\cp --force --verbose  $csapWorkingDir/configuration/os/vcenter-env.sh $vcenterEnvFile
		
		local escapedUser=$(echo $GOVC_USERNAME | sed 's|\\|\\\\\\\\|g')
	
		replace_all_in_file '$GOVC_INSECURE' "$GOVC_INSECURE" $vcenterEnvFile ;
		replace_all_in_file '$GOVC_URL' "$GOVC_URL" 
		replace_all_in_file '$GOVC_DATACENTER' "$GOVC_DATACENTER" 
		replace_all_in_file '$GOVC_DATASTORE' "$GOVC_DATASTORE" 
		replace_all_in_file '$GOVC_USERNAME' "$escapedUser"
		replace_all_in_file '$GOVC_PASSWORD' "$GOVC_PASSWORD"
#		replace_all_in_file "resource_pool_path" "$resource_pool_path"
#		replace_all_in_file "vm_path" "$vm_path"

		local rootVcenter="/root/vcenter" ;
		
		run_using_root "rm --recursive --force --verbose $rootVcenter ; \
			mkdir --parents --verbose $rootVcenter ; \
			cp $vcenterEnvFile $rootVcenter ; \
			cp $CSAP_FOLDER/bin/csap-environment.sh $rootVcenter; \
			cp --recursive $CSAP_FOLDER/bin/functions $rootVcenter; \
			cp $CSAP_FOLDER/bin/govc $rootVcenter"
	else
	
		print_with_head "vcenter integration skipped. To enable: ensure vcenter-env.sh is created"
		
	fi ;
}

function configure_cloud_provider() {

	local cloudProvider="$csapWorkingDir/configuration/cloud-provider" ;
	if test -d $cloudProvider ; then 
		
		print_with_head "Found: '$cloudProvider', updating template file(s)"
		
		if test -f $cloudProvider/vsphere.conf ; then
		
			# vsphere.conf requires escaped windows domains: lab\csapuser becomes lab\\\\csapuser ; then sed reduces to lab\\csapuser
			local escapedUser=$(echo $GOVC_USERNAME | sed 's|\\|\\\\\\\\|g')
		
			replace_all_in_file "GOVC_INSECURE" "$GOVC_INSECURE" $cloudProvider/vsphere.conf
			replace_all_in_file "GOVC_URL" "$GOVC_URL" 
			replace_all_in_file "GOVC_DATACENTER" "$GOVC_DATACENTER" 
			replace_all_in_file "GOVC_DATASTORE" "$GOVC_DATASTORE" 
			replace_all_in_file "GOVC_USERNAME" "$escapedUser"
			replace_all_in_file "GOVC_PASSWORD" "$GOVC_PASSWORD"
			replace_all_in_file "resource_pool_path" "$resource_pool_path"
			replace_all_in_file "vm_path" "$vm_path"
			
			local currentVm="/$GOVC_DATACENTER/$(govc find vm -name $(hostname --short))"
			vmDiskEnableUuid=$(govc vm.info -e=true "$currentVm" | grep disk.enableUUID | grep -i true | wc -l )
			
			if (( $vmDiskEnableUuid == 0 )) ; then 
				print_with_head "Info: disk.enableUUID not enabled on vm: '$currentVm'" ;
				print_line "Attempting to enable using govc..."
				$(govc vm.change -e='disk.enableUUID=true' -vm="$currentVm")
				
				
				vmDiskEnableUuid=$(govc vm.info -e=true "$currentVm" | grep disk.enableUUID | grep -i true | wc -l )	
				if (( $vmDiskEnableUuid == 0 )) ; then 
					print_line "Error: failed to enable uuid, exiting" ;
					exit 99 ;
				else
					print_line "Successfully enabled uuid" ;
				fi
			
			else 
				print_line "\n\t vsphere vm disk.enableUUID is enabled on vm: '$currentVm'"
 			fi
		fi
		
	fi ;
	
}


function configure_kubeadm() {

	local NOW=$(date +"%h-%d-%I-%M") ;
	
	local kubeadmConfigFolder="$csapWorkingDir/configuration/kubeadm" ;
	local kubeadmConfigurationFile="$kubeadmConfigFolder/kubeadm-$NOW.yaml"
	
	print_with_head "Creating kubeadm configuration: '$kubeadmConfigurationFile'"
	
	if test -f $kubeadmConfigurationFile ; then
		rm --recursive --force --verbose $kubeadmConfigurationFile
	fi ;
	
	
	# note: verbose and $kubeadmConfigurationFile will default for remaining commands
	append_file $kubeadmConfigFolder/cluster-configuration.yaml $kubeadmConfigurationFile true
	
	replace_all_in_file "ETCD_FOLDER" "$etcdFolder"
	
	local description="Type: " ;
	local kubeadmType="init" ;
	local kubeadmIgnore="--ignore-preflight-errors=SystemVerification,DirAvailable--var-lib-etcd" ;
	
	if [ "$kubernetesAllInOne" == "true" ] ; then
	
		description="$description All In One with swap disabled"
		kubeadmIgnore="--ignore-preflight-errors=SystemVerification,DirAvailable--var-lib-etcd,Swap" ;
		
		append_line "\n---\n" ;
		append_file $kubeadmConfigFolder/init-configuration.yaml
		
		replace_all_in_file "#__failSwapOn: updatedByInstaller" "failSwapOn: false"
		#append_line "\n---\n" ;
		#append_file $kubeadmConfigFolder/ignore-swap.yaml
		
	else
		
		if $( is_primary_master ) ; then
			append_line "\n---\n" ;
			append_file $kubeadmConfigFolder/init-configuration.yaml
			
			local numMasters=$( wc -w <<< $kubernetesMasters) ;
			description="$description Master (number: $numMasters)"
			if (( $numMasters > 1 )) ; then
			
				kubeadmType="$kubeadmType --upload-certs" ;
				replace_all_in_file "#__controlPlaneEndpoint" "controlPlaneEndpoint"
				replace_all_in_file "MASTER_DNS" "$kubernetesMasterDns"
				
			fi ;
			
		elif $( is_master ) ; then
		
			description="$description Secondary Master"
			kubeadmType="join" ;
			
			replace_all_in_file "#__controlPlaneEndpoint" "controlPlaneEndpoint"
			replace_all_in_file "MASTER_DNS" "$kubernetesMasterDns"
			
			print_line "Updating config file with '$kubeadmConfigFolder/join.yaml'"
			append_line "\n---\n" ;
			append_file $kubeadmConfigFolder/join.yaml
			
			append_line "\n\n" ;
			append_line "controlPlane:" ;
			append_line '  certificateKey: "CONTROL_CERT_KEY"' ;
			
		else
		
			description="$description Worker"
			kubeadmType="join" ;
			
			print_line "Updating config file with '$kubeadmConfigFolder/join.yaml'"
			append_line "\n---\n" ;
			append_file $kubeadmConfigFolder/join.yaml
			
		fi ;  
		
	fi
	
	replace_all_in_file "KUBERNETES_STORAGE" "$kubernetesStorage"
	
	replace_all_in_file "MASTER_HOST" "$(getPrimaryMaster)"
	
	replace_all_in_file "JOIN_TOKEN" "$clusterToken"
	
	# 1.12.2-0 converted to "v1.12.2"
	replace_all_in_file "K8_IMAGE_VERSION" "$k8ImageVersion"  
	replace_all_in_file "K8_POD_SUBNET" "$k8PodSubnet"  
	
	
	if [[ "$imageRepository" != "none" ]] ; then
		replace_all_in_file "#__imageRepository: updatedByInstaller" "imageRepository: '$imageRepository'"
		replace_all_in_file "#__dns:" "dns:"
	fi ;
	
	#
	# kube api server switches
	#  https://kubernetes.io/docs/reference/command-line-tools-reference/kube-apiserver/
	#
	if [[ "$cipherSuites" != "none" || "$strictDirectives" != "none" ]] ; then
		print_separator "kube api server updates"
		replace_all_in_file "#__apiServer:" "apiServer:"
		replace_all_in_file "#__extraArgs2:" "extraArgs:"
	fi ;
	
	#
	# kube api server: cipherSuites
	#
	if [[ "$cipherSuites" != "none" ]] ; then
		replace_all_in_file "#__extraArgs1:" "extraArgs:"
		replace_all_in_file "#__cipher-suites: updatedByInstaller" "cipher-suites: '$cipherSuites'"
		replace_all_in_file "#__tls-cipher-suites: updatedByInstaller" "tls-cipher-suites: '$cipherSuites'"
		#replace_all_in_file "#__tlsCipherSuites: updatedByInstaller" "tlsCipherSuites: '$cipherSuites'"
		local altSuites=""
		for suite in $(echo $cipherSuites | sed "s|,| |g") ; do
			altSuites="$altSuites\n  - \"$suite\"";
		done ;
		print_line $altSuites
		replace_all_in_file "#__tlsCipherSuites: updatedByInstaller" "tlsCipherSuites: $altSuites"
	fi ;
	
	#
	# kube api server: strictDirectives
	#
	if [[ "$strictDirectives" != "none" ]] ; then
		replace_all_in_file "#__strict-transport-security-directives: updatedByInstaller" "strict-transport-security-directives: '$strictDirectives'"
	fi ;
	
	
	kubeadmParameters="$kubeadmType --config $kubeadmConfigurationFile  $kubeadmIgnore"
	print_command \
		"$description, kubeadm parameters" \
		"$kubeadmParameters"
	
	print_command \
		"kubeadm configuration file: '$kubeadmConfigurationFile'" \
		"$(cat $kubeadmConfigurationFile)" 
	
}


function post_start_status_check() {
	
	$csapWorkingDir/scripts/sanity-tests.sh status_tests 
	
	if $(is_worker) ; then
		$csapWorkingDir/scripts/sanity-tests.sh deployment_tests
	fi ;
	
}

function check_to_untaint() {

	#if [ "$kubernetesAllInOne" == "true" ] ; then
		
	local numMasters=$( wc -w <<< $kubernetesMasters) ;
	
	if $(is_master)  ; then
		if (( $numMasters == 1 )) ; then
		
			if [[ $singleMasterUntaint == "yes" ]] ; then
				currentNodeName=$(kubectl get nodes | grep $(hostname --short) | cut -d' ' -f1); 
				print_with_head "check_to_untaint(): single master detected - removing master taints from: '$currentNodeName'"
				
				kubectl patch node $currentNodeName -p '{"spec":{"taints":[]}}' ;
			else
				print_with_head "Warning: found singleMasterUntaint, single master is being left tainted" ;
			fi ;
		else
			print_line "check_to_untaint(): numMasters: $numMasters > 1 , skipping"
		fi ; 
	fi
		
}

function dashboard_setup() {
#	source $csapWorkingDir/configuration/dashboard/dashboard-functions.sh
#	
#	dashboard_installer $1
#	generate_dashboard_launch_page
	
	print_with_head "For dashboard support, add a kubernetes spec service referencing: '$csapWorkingDir/configuration/dashboard'"

}

function ingress_setup() {
	
	#source $csapWorkingDir/configuration/ingress/ingress-functions.sh
	#ingress_installer $1
	
	print_with_head "For ingress support, add a kubernetes spec service referencing: '$csapWorkingDir/configuration/ingress'"
	
}



function add_nfs() {
	
	print_with_head "nfs configuration:  nfs_server: '$nfs_server' , nfs_path :'$nfs_path', nfs_mount:'$nfs_mount'" ;
	
	if [ $nfs_server != "none" ] \
		&& [ $nfs_mount != "none" ] \
		&& [ $nfs_path != "none" ]  ; then
		
		print_command \
			"Attempting to mount nfs" \
			"$(nfs_add_mount $nfs_server:$nfs_path $nfs_mount $nfs_options)" ;
		
	fi
	
	
}

function remove_nfs() {
	
	print_with_head "nfs remove:  nfs_server: '$nfs_server'" ;
	
	if [ $nfs_server != "none" ] ; then
	
		nfs_remove_mount $nfs_mount;
	
	fi ;
	
}



variablesFile="$csapWorkingDir/install-variables.sh"
function build_variables_file() {

	print_with_head "Creating environment file for kubeadm commands" ;
	rm --recursive --force --verbose $variablesFile
	
	
	append_file "# generated file" $variablesFile true
	
	# set verbose to false
	append_file "#" $variablesFile false
	
	append_line  export csapUser=$csapUser

	append_line export  kubernetesAllInOne=$kubernetesAllInOne
	append_line export  kubernetesStorage=$kubernetesStorage
	append_line export  kubeletExtraArgs=\"$kubeletExtraArgs\"
	append_line export  calico_ip_method=\"$calico_ip_method\"
	append_line export  veth_mtu=\"$veth_mtu\"
	
	
	append_line export  CSAP_FOLDER=$CSAP_FOLDER
	append_line export  AGENT_ENDPOINT=\"$AGENT_ENDPOINT\"
	append_line export  csapName=$csapName
	append_line export  csapProcessId=$csapProcessId
	append_line export  csapWorkingDir=$csapWorkingDir
	append_line export  csapPrimaryPort=$csapPrimaryPort
	
	append_line export  masterBackupFolder=$masterBackupFolder
	
	append_line export  imageRepository=\"$imageRepository\"
	append_line export  kubeadmParameters=\"$kubeadmParameters\"
	append_line export  kubernetesMasters=\"$kubernetesMasters\"
	append_line export  kubernetesMasterDns=$kubernetesMasterDns
	
	append_line export  k8Version=$k8Version
	append_line export  k8ImageVersion=$k8ImageVersion
	
	append_line export  isForceIpForApiServer=$isForceIpForApiServer
	
	append_line export  kubernetesRepo=$kubernetesRepo
	append_line export  clusterToken=\"$clusterToken\"
	
}


function run_command() {
	command=$1
	#run_using_csap_root "$kubeCommandsFile" "$command" "$kubernetesMasters" "$kubernetesRepo" "$clusterToken"
	build_variables_file ;
	print_with_head "'$variablesFile': \n$(cat $variablesFile)"
	run_using_csap_root_file "$command" "$kubeCommandsFile" "$variablesFile"  
	
}

function createVersion() {
	
	packageVersion=`ls $csapWorkingDir/version | head -n 1`
	
	print_with_head "Prepending kubeadm version to package version"
	
	#k8Version="1.10"   #`docker --version | awk '{ print $3 }' | tr -d ,`
	
	k8Version=`kubeadm version | awk '{ print $5 }' | cut -f2 -dv | cut -f1 -d\"`
	myVersion="$k8Version--$packageVersion"
	
	print_line "Renaming version folder: $csapWorkingDir/version/$packageVersion to $myVersion"
	
	\mv -v "$csapWorkingDir/version/$packageVersion" "$csapWorkingDir/version/$myVersion" 

	
}


