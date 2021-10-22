# pod dns, pod dns templates



function deploy_dnsUtils() {
	
	local dnsYmlFile="$(pwd)/dnsutils.yml"
	local dnsPodName="dnsutils"
	local dnsPodNamespace="default"
	
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
    image: gcr.io/kubernetes-e2e-test-images/dnsutils:1.3
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











