#
# Notes: 
#	- used with kubernetes template: storage-local.yaml
#	- https://kubernetes.io/blog/2019/04/04/kubernetes-1.14-local-persistent-volumes-ga/
#


function install_required_tools() {

	if is_need_command helm ; then
	
		print_with_head "Installing helm client" ; 
		run_using_root 'curl -LO https://git.io/get_helm.sh ; chmod 700 get_helm.sh ; ./get_helm.sh' ; 
		
		print_with_head "Installing helm server (tiller) into kubernetes" ; 
		helm init ;
		
	fi ;
	
	if is_need_command git ; then
	
		print_with_head "getting git" ;
		run_using_root "yum -y install git" ;
	fi ;
}

install_required_tools


git clone --depth=1 https://github.com/kubernetes-sigs/sig-storage-local-static-provisioner.git
helm template ./helm/provisioner -f <path-to-your-values-file> > local-volume-provisioner.generated.yaml
# edit local-volume-provisioner.generated.yaml if necessary
kubectl create -f local-volume-provisioner.generated.yaml