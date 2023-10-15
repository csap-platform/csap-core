#!/bin/bash

function ingress_installer() {
	
	operation="$1";
	
	print_with_date "ingress setup: '$operation'"
	
	numInstalled=$(kubectl get pods --all-namespaces | grep ingress-nginx | wc -l) ;
	
	if (( $numInstalled == 0 )) || [ "$operation" == "delete" ]; then
		# info: https://kubernetes.github.io/ingress-nginx
		# 	- https://raw.githubusercontent.com/kubernetes/ingress-nginx/master/deploy/mandatory.yaml
		#	- https://raw.githubusercontent.com/kubernetes/ingress-nginx/master/deploy/provider/cloud-generic.yaml
		print_with_head "Installing kubernetes ingress components: refer to https://kubernetes.github.io/ingress-nginx/deploy/"
		
		run_cli \
			$operation \
			"ingress - nginx" \
			"$csapWorkingDir/configuration/ingress/ingress-nginx.yaml"
			
		
	else
		 print_with_head "ingress already installed"
	fi ;
		
}

function run_cli() {
	
	operation="$1"
	description="$2"
	source="$3"
	
	print_with_head "Operation: '$operation': $description \n\t '$source'"
	#kubectl apply -f configuration/calico.yaml
	kubectl $operation --insecure-skip-tls-verify=true \
		-f $source
}
