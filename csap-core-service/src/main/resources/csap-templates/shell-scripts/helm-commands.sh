# helm examples, run helm commands


#
#  ref. https://helm.sh/docs/intro/install/
#



print_command \
	"installed repositories" \
	"$(helm repo list)"
	



print_command \
	"installed releases" \
	"$(helm list --all-namespaces)"	


print_command \
	"Show release versions" \
	"$(helm search repo bitnami/nginx --versions)"
	
	

exit ;


demoName="demo-nginx" ;
demoNamespace="helm-demo" ;


print_command \
	"Install a release" \
	"$(helm install --namespace $demoNamespace  $demoName bitnami/nginx --version 9.5.4)"


print_command \
	"upgrade a release" \
	"$(helm upgrade --namespace $demoNamespace  $demoName bitnami/nginx --version 9.5.3)"



print_command \
	"release status" \
	"$(helm status --namespace $demoNamespace  $demoName)"



	
	


function install_helm() {

	
	wget --no-verbose --output-document=install-helm.sh https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3
	
	exit_on_failure $? "failed to download new helm installer"
	
	chmod 755 install-helm.sh
	
	
	if $(is_command_available helm) ; then
		run_using_root rm --verbose $(which helm);
	fi ;
	
	export HELM_INSTALL_DIR="$CSAP_FOLDER/bin";
	export USE_SUDO="false";
	
	$(pwd)/install-helm.sh
	
	print_command \
		"helm environment" \
		"$(helm version; helm env)"

	
	#run_using_root $(pwd)/install-helm.sh
	
}

function install_package() {

	helm repo add bitnami https://charts.bitnami.com/bitnami
	helm repo add jenkins https://charts.jenkins.io
	
	helm repo update
	
	helm search repo nginx
	
	
	helm search repo nginx
	
	local demoName="demo-nginx" ;
	
	helm install $demoName bitnami/nginx 
	
	print_command \
		"helm status $demoName" \
		"$( helm status $demoName )"
	
	
	
	print_command \
		"helm list" \
		"$( helm list )"
	
	
	
	print_command \
		"helm uninstall $demoName" \
		"$( helm uninstall $demoName )"
	
	

#	kubectl -n kube-system create sa tiller
#	kubectl create clusterrolebinding tiller --clusterrole cluster-admin --serviceaccount=kube-system:tiller
#	helm init --service-account tiller
#	kubectl create namespace kubeapps
#	helm install --name kubeapps --namespace kubeapps bitnami/kubeapps
	
}