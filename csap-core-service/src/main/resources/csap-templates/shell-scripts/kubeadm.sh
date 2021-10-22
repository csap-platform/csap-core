# kubeadm commands, troubleshoot install issues




print_section "kubeadm-config"
kubectl get cm -o yaml -n kube-system kubeadm-config


print_section "kubectl: configmap kubeadm-config"
kubectl describe configmap kubeadm-config --namespace="kube-system"


print_section "kubeadm: init-defaults"
kubeadm config print init-defaults 



print_section "kubeadm: join-defaults"
kubeadm config print join-defaults 


exit;


#
#  troubleshooting commands - run as root
#


function cleanUp() {
	
	print_separator "cleaning previous install"
	
	systemctl stop kubelet
	
	print_command \
		"removing journals" \
		"$(rm --recursive --force /run/log/journal/*)"
	
	
	print_separator "perform_kubeadm_reset() cleaning up previous installs"
	echo y | kubeadm --v 8 reset
	
	iptable_wipe
	
	
	print_separator "legacy cleanup"; 
	rm  --verbose  --recursive --force $HOME/.kube/config
	rm  --verbose  --recursive --force /etc/cni/net.d
	rm  --verbose  --recursive --force /run/log/journal/*
	
	# kubeadm not cleaning this up
	systemctl stop  kubepods-besteffort.slice
	systemctl stop  kubepods-burstable.slice
	systemctl stop  kubepods.slice
	
	
	systemctl daemon-reload
	systemctl reset-failed
	print_command \
		"systemctl listing filtered by pod" \
		"$(systemctl list-units | grep pod)"

	
}

function installAgain() {
	
	configFile="/opt/csap/csap-platform/working/kubelet/configuration/kubeadm/kubeadm-Jul-08-04-31.yaml" ;
	print_separator "running installer using config $configFile"

	# paste from your kubelet install.sh....
	kubeadm init --v=5 --config $configFile --ignore-preflight-errors=SystemVerification,DirAvailable--var-lib-etcd,Swap
}

cleanUp ;


installAgain ;

exit

print_section "list cgroups"
systemctl list-units | grep pod



