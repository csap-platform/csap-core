# pod commands, pod command templates

podName='_serviceName_' ;
podName=$(find_pod_name $podName) ; print
namespace=$(find_pod_namespace $podName) ; 

print_with_head "podName: $podName, namespace: $namespace"


#
# etcd remove failed node
#	- must be run on a host with a working
#

currentHostIp=$(host $(hostname --long) | awk '{ print $NF }');
etcdWorkingMember="$currentHostIp" ;  
etcdCli="etcdctl -C https://$etcdWorkingMember:2379"
certs="--ca-file=/etc/kubernetes/pki/etcd/ca.crt --cert-file=/etc/kubernetes/pki/etcd/peer.crt --key-file=/etc/kubernetes/pki/etcd/peer.key"
	
print_command \
	"$etcdCli cluster-health" \
	"$(kubectl exec $podName --namespace=$namespace -- $etcdCli $certs cluster-health)"  ;
	
exit ;
	
# etcd member remove, assumes a single failed 
failedMemberToRemoveId="$(kubectl exec $podName --namespace=$namespace -- $etcdCli $certs cluster-health | grep unreachable | awk '{ print $2 }')"
print_with_head "failedMemberToRemoveId: '$failedMemberToRemoveId'"

do_the_remove=true ;
if [ $do_the_remove ]  && [ "$failedMemberToRemoveId" != "" ] ; then 
	print_command \
		"$etcdCli cluster-health" \
		"$(kubectl exec $podName --namespace=$namespace -- $etcdCli $certs member remove $failedMemberToRemoveId)"  ;
fi ;
	
