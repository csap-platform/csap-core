# pod commands, pod command templates


podName='_serviceName_' ; # podName=$(find_pod_name $podName) 
namespace=$(find_pod_namespace $podName) ; 

print_with_head "podName: $podName, namespace: $namespace"


print_command \
	"status" \
	"$(kubectl describe pods $podName --namespace=$namespace | grep --ignore-case --extended-regexp 'Status:|IP:|Port:|Ready' )"  ;

exit ;

#
#  Port Forwarding: defaults to first port; alternately set
#
firstPort=$(kubectl get pod $podName --namespace=$namespace -o json | \
	jq '.spec.containers[].ports[].containerPort' | grep -v null | head -1) ;
hostPort=$firstPort

print_separator "Port forwarding: exposing $podName as $(hostname --long):$hostPort"
kubectl port-forward $podName --namespace=$namespace --address 0.0.0.0 $hostPort:$firstPort  



#
#  Sample: run a command in a pod with a single container. If multiple: --container <name>
#
print_command \
	"pod working directory" \
	"$(kubectl exec $podName --namespace=$namespace -- bash -c 'pwd')"  ;


print_command \
	"pod resource summary" \
	"$(kubectl top pods $podName --namespace=$namespace)"  ;
	

	
print_command \
	"Logs for $podName in $namespace (add --previous for crashed)" \
	"$(kubectl logs $podName --all-containers=true --namespace=$namespace --tail=10)"  ;


print_command \
	"Drain all pods from $(hostname --long)" \
	$(kubectl drain --ignore-daemonsets --delete-local-data $(hostname --long) )  ;

print_command \
	"Renable scheduling on $(hostname --long)" \
	$(kubectl uncordon $(hostname --long) )  ;

#
# kubectl verbose to show api calls 
#
kubectl --v=8 get pods

#
# calico logs
#

kubectl logs $(find_pod_name calico-node)  --container=calico-node --namespace=kube-system --tail=100;



#
# kuberntes resources - requires metrics-server installation
#

print_with_head "node resource summary"
kubectl top nodes

print_with_head "pod resource summary"
kubectl top pods


#
#  Reference commands
#

#
#  Sample: run a command in a pod with a single container. If multiple: --container <name>
#
print_with_head "pod files at root"
kubectl exec $podName --namespace=$namespace -- bash -c 'ls /*'  ;




# typically pods are deleted via associated resources
kubectl delete statefulsets $yourSetName
kubectl delete deployments $yourDeploymentName
kubectl delete replicasets $yourSetName



#
#  Inspect
#

print_with_head "pod details"
kubectl describe pods $podName --namespace=$namespace;

#
# Logs
#

print_with_head "Logs for $podName in $namespace"
kubectl logs $podName --namespace=$namespace --tail=20;
kubectl logs $podName --since=10h;
kubectl logs $podName --follow=true;

#
# Pod storage
#

print_with_head "Pods on '$(hostname --short)' with storage location"
function print_formated() { 	printf "%30s \t %-15s \t %-65s\n" "$@"; } ; export -f print_formated
print_formated "/var/lib/docker/kubernetes/*uid*" "Namespace" "PodName";
print_formated "____________________________________" "______________" "_______";
kubectl get pods \
	--all-namespaces \
	--output=jsonpath='{range .items[*]}{.spec.nodeName}{" "}{.metadata.uid}{" "}{.metadata.namespace}{" "}{.metadata.name}{"\n"}{end}' \
	| grep  $(hostname --short) | cut -f2- -d' ' \
	| xargs -n 1 -I {} bash -c 'print_formated {}'



#
#  csap-tools-container demo:  inject via csap service definition checkbox
#  - tools container includes dns tools deployed in a sidecar image. 
#
k8Service="test-k8s-activemq-service"

print_with_head "nslookup for '$k8Service'"
kubectl exec $podName --namespace=$namespace --container csap-tools-container -- nslookup $k8Service ;

print_with_head "dig for '$k8Service'"
kubectl exec $podName --namespace=$namespace --container csap-tools-container -- dig $k8Service a +search +noall +answer ;


kubectl get events

#
# interactive demo - ssh host, su - csap
#
source $HOME/csap-platform/bin/csap-environment.sh
podName=$(find_pod_name test-k8s-csap-reference);
kubectl exec -it $podName --namespace=$(find_pod_namespace $podName) --container csap-tools-container -- bash




#
# List pods Sorted by Restart Count
#
kubectl get pods --all-namespaces --sort-by='.status.containerStatuses[0].restartCount'



kubectl logs $podName                                 # dump pod logs (stdout)
kubectl logs $podName -c my-container                 # dump pod container logs (stdout, multi-container case)
kubectl logs -f $podName                              # stream pod logs (stdout)
kubectl logs -f $podName -c my-container              # stream pod container logs (stdout, multi-container case)
kubectl run -i --tty busybox --image=busybox -- sh  # Run pod as interactive shell
kubectl attach $podName -i                            # Attach to Running Container

kubectl exec $podName -- ls /                         # Run command in existing pod (1 container case)
kubectl exec $podName -c my-container -- ls /         # Run command in existing pod (multi-container case)
kubectl top pod $podName --containers               # Show metrics for a given pod and its containers



kubectl run $deploymentName --image nginx --replicas=1