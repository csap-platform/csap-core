#!/bin/bash

#
# learn more:
#	- https://github.com/prometheus-operator/kube-prometheus
#	- 
#

source $HOME/.bashrc
source $CSAP_FOLDER/bin/csap-environment.sh

sourceFolder=$csapPlatformWorking/csap-package-monitoring/scripts ;

buildFile="${1-$sourceFolder/csap-monitoring.jsonnet}" ;
buildFolder="${2-$sourceFolder/build-tools}" ;
projectFolder="${3-$sourceFolder/csap-kube-prometheus}" ;



function deploy_build() {
	
	cd $projectFolder ;
	
	print_separator "Deleting previous manifests in $(pwd)/manifests"
	find manifests -type f -name '*.yaml' -print0 \
		| sort --zero-terminated --reverse \
		| xargs --null --replace=theYamlFile \
		kubectl delete --ignore-not-found=true --filename=theYamlFile
	
	#kubectl delete --ignore-not-found=true --filename=manifests/  ;
	
	sleep 2;
	
	
	print_separator "creating manifests/0*"
	find manifests -type f -name '0*.yaml' -print0 \
		| sort --zero-terminated  \
		| xargs --null --replace=theYamlFile \
		kubectl create --filename=theYamlFile
	#	sh -c '{ kubectl create --filename=theYamlFile; if [[ theYamlFile == *00namespace-namespace.yaml ]] ; then  echo sleeping; sleep 10; fi ;  }'
		
	#kubectl create --filename=manifests/0* ; 
	
	print_separator "Waiting for service monitor creation"
	until kubectl get servicemonitors --all-namespaces ; do echo -e "\nWaiting for sevicemonitors to be created" ; sleep 5; echo ""; done ;
	
	print_separator "Creating failed deployments"
	kubectl create --filename=manifests/00namespace-prometheusRule.yaml
	
	print_separator "Creating  remaining manifests"
	find manifests -type f -not -name '0*.yaml' -print0 \
		| sort --zero-terminated  \
		| xargs --null --replace=theYamlFile \
		kubectl create --filename=theYamlFile
		
	#find manifests -type f ! -name '0*.yaml' -exec kubectl create --filename={} \;
	
	exit ;
	
}


# Uncomment this AFTER performing build
#deploy_build

function build_jsonnet() {
	
	if is_need_command jsonnet ; then
	
		print_separator "installing dependencies"
		run_using_root sudo yum --assumeyes install gcc gcc-c++ git make wget
		
		print_separator "building jsonnet"
		
		rm --recursive --force $buildFolder
		mkdir --parents --verbose $buildFolder
		cd $buildFolder ;
		git clone https://github.com/google/jsonnet.git
		cd jsonnet ;
		make
		
		run_using_root cp jsonnet /usr/local/bin
		run_using_root cp jsonnetfmt /usr/local/bin
		
		
		print_separator "installing go"
		wget --no-verbose https://golang.org/dl/go1.15.6.linux-amd64.tar.gz
		run_using_root tar --extract --gzip --file go1.15.6.linux-amd64.tar.gz --directory /usr/local
		
		print_separator "installing go module: jsonnet-bundler"
		run_using_root 'export PATH="$PATH:/usr/local/go/bin"; export GOPATH="/usr/local/go"; GO111MODULE="on" go get github.com/jsonnet-bundler/jsonnet-bundler/cmd/jb'
		
		
		print_separator "installing go module: gojsontoyaml "
		run_using_root 'export PATH="$PATH:/usr/local/go/bin"; export GOPATH="/usr/local/go"; GO111MODULE="on" go get github.com/brancz/gojsontoyaml'
		
		
	else
		print_two_columns "jsonnet" "using existing jsonnet build on host"
	fi ;
	
	if is_need_command go ; then
		#append_file 'export PATH="$PATH:/usr/local/go/bin"' $HOME/.bashrc true
		#source $HOME/.bashrc
		export PATH="$PATH:/usr/local/go/bin" ;
	fi ;
	
}

build_jsonnet ;

function build_kube_promethesius() {
	
	if ! test -d $projectFolder ; then 
		
		mkdir --parents --verbose $projectFolder; 
		cd $projectFolder ;
		
		print_separator "running jsonnet-bundler to initialize jsonnetfile.json"
		jb init;
		
		# Creates `vendor/` & `jsonnetfile.lock.json`, and fills in `jsonnetfile.json`
		print_separator "checking out kube-prometheus@release-0.8 to $projectFolder"
		jb install github.com/prometheus-operator/kube-prometheus/jsonnet/kube-prometheus@release-0.8 
		
		print_with_head "use 'jb updated' to git update"
	else
		print_two_columns "kube_promethesius" "using existing $projectFolder previously checked out"
	fi;
	

}
build_kube_promethesius ;



function build_csap_monitoring() {
	
	local sourceFile="${1-csap.jsonnet}" ;
	print_separator "generating csap-monitoring using $sourceFile"
	
	cd $projectFolder ;
	
	set -e
	# set -x
	# only exit with zero if all commands of the pipeline exit successfully
	set -o pipefail
	
	# Make sure to use project tooling
	PATH="$(pwd)/tmp/bin:${PATH}"
	
	# Make sure to start with a clean 'manifests' dir
	
	print_two_columns "clean up" " removing manifests"
	rm -rf manifests
	mkdir -p manifests
	
	#delay_with_message 3 "starting build" ;
	
	# Calling gojsontoyaml is optional, but we would like to generate yaml, not json
	print_two_columns "compiling jsonnet" "this will take several minutes..."
	jsonnet --jpath vendor --multi manifests $sourceFile | xargs -I{} sh -c 'cat {} | /usr/local/go/bin/gojsontoyaml > {}.yaml' -- {}
	
	
	
	# Make sure to remove json files
	print_two_columns "kubernetes prep" "removing non yaml from manifests folder"
	find manifests -type f -not -name '*.yaml' -delete
	#rm -f kustomization
	
	print_with_head "build complete: to deploy to kubernetes: uncomment deploy on line 29";

}


build_csap_monitoring $buildFile
