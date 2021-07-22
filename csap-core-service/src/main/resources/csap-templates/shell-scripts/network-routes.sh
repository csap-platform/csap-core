# Network: routes, Show network routes



print_two_columns "hostname" "$(hostname --short), $(hostname --long)"
print_two_columns "address" "$(hostname --all-ip-addresses)"


#
# DNS troubleshooting
#


if  is_process_running kubelet ; then

	networkTestPod="dnsutils"
	service="kubernetes-dashboard.kubernetes-dashboard" ; servicePort="443"

	if ! $(is_pod_running $networkTestPod) ; then 
		print_with_head "Deploying $networkTestPod"; 
		kubectl create -f $csapPlatformWorking/csap-agent/jarExtract/BOOT-INF/classes/csap-templates/kubernetes-yaml/dns-utils.yaml
		wait_for_pod_conditions $networkTestPod
	fi

	
#	networkTestPod="test-k8s-by-spec-ha-0 --namespace=csap-test-ha"
#	service="test-k8s-postgres-service"; servicePort="5432"

	print_command \
		"Kubernetes DNS lookup source: '$networkTestPod':  target: '$service'" \
		"$(kubectl exec -i $networkTestPod -- nslookup $service)"

	print_command \
		"Kubernetes DNS dig  source: '$networkTestPod':  target: '$service'" \
		"$(kubectl exec -i $networkTestPod -- dig +search +short $service)"

	print_command \
		"Kubernetes nc (netcat):  source: '$networkTestPod':  target: '$service' port: '$servicePort'" \
		"$(kubectl exec -i $networkTestPod -- nc -w 2 -zv $service $servicePort 2>&1)"
		
#	print_command \
#		"docker nslookup" \
#		"$(docker run csap/csap-tools nslookup ldap.davis.***REMOVED***.lab)"
fi


#
# Network setup information
#
print_command \
	"ip addr: show interface status" \
	"$(ip addr)"


print_command \
	"ip route list: show interface status" \
	"$(ip route list; echo -e "\n* To clean up: 'ip route del <line-from-list>'" )"
	
	


print_command \
	"route: Kernel IP routing table" \
	"$(route)"
	
if [[ $(whoami) == root ]] ; then


	print_command \
		"iptables --table nat --list KUBE-SERVICES" \
		"$(iptables --table nat --list KUBE-SERVICES)" ;
				
	
	tableTypes="filter nat mangle raw" ;
	for tableType in $tableTypes ; do
		print_command \
			"iptables --table $tableType --list-rules " \
			"$(iptables --table $tableType --list-rules )" ;
			
	done ;
	
else 

	print_error "Must be root in order to run iptable commands"

fi ;
	

