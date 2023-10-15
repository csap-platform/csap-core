#!/bin/bash

#
#  
#

specPackageName="csap-monitoring-specs" ;
#kubePrometheseus="kube-prometheus@release-0.8" ;
kubePrometheseus="kube-prometheus@release-0.9" ;

#specVersion=${specVersion:-2-SNAPSHOT} ;
specVersion=${specVersion:-21.10} ;
ui_anonymous=${ui_anonymous:-false} ;

templateVariables="data_retention data_volume_size data_storage_class" ;
data_retention=${data_retention:-3d} ;
data_volume_size=${data_volume_size:-5Gi} ;
data_storage_class=${data_storage_class:-$storage_class} ;

#debug="true" ;


print_separator "CSAP Monitoring Package"

print_two_columns "specVersion" "$specVersion"
print_two_columns "kubePrometheseus" "$kubePrometheseus"
print_two_columns "ui_anonymous" "$ui_anonymous"

#
# csap passes in many env variables; only some are used during deployment
#
for name in $templateVariables ; do
	print_two_columns "$name" "${!name}" ;
done

print_separator "yaml substituions"
for (( counter=1; counter < (10) ; counter++ )) ; do
  current="yamlCurrent$counter" ;
  new="yamlNew$counter" ;
  if [ -z "${!current}" ] || [ -z "${!new}" ]; then
    break;
  fi ;
  
  
  print_two_columns "$current" "${!current}"
  print_two_columns "$new" "${!new}"
done


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


function verify_settings() {
	
	kubernetesMasters=${kubernetesMasters:-notSpecified};
	if [ "$kubernetesMasters" == "notSpecified" ] ; then 
		print_error "kubernetesMasters is a required environment variable. Add it to service parameters" ;
		exit ;
	fi
	
	if ! $( is_primary_master ) ; then
		print_with_head "Exiting: $(hostname --long) is not the primary kubernertes master"
		exit ;
	fi ;
	
	print_separator "$csapName Package: $kubernetesMasters"
}

#
#  only run on primary master
#
verify_settings


function api_package_build() {
	
	print_section "api_package_build: $(pwd)"
	
	if [[ "$mavenBuildCommand" != *package* ]] ; then
		print_line "Skipping source build" ;
		return ;
	fi ;
	
	print_two_columns "Current directory" "$(pwd)" 
	print_two_columns "built wrapper" "$csapPackageFolder/$csapName.zip"
	#print_line "httpd source code build will be bypassed if '$HOME/opensource/httpd.zip' exists"
	if [ -r "$HOME/opensource/$specPackageName.zip" ] ; then 
	
		print_with_head "NOTE: Source build skipped, copying $HOME/opensource/$specPackageName.zip to '$(pwd)'"
		cp --force --verbose $HOME/opensource/$specPackageName.zip .
		
	else
		generate_deployment_specs 
	fi ;
	
	\rm --recursive --force $csapPackageDependencies
	mkdir --parents --verbose $csapPackageDependencies
	cp --force --verbose $specPackageName.zip $csapPackageDependencies
	
	print_two_columns "mavenBuildCommand" "$mavenBuildCommand"
	
	if [[ "$mavenBuildCommand" == *deploy* ]] ; then
		deploy_specs
	fi ;
	
	
	print_separator "api_package_build() completed"
	
}


function update_repo_variables() {
	REPO_ID="csap-release-repo"
	REPO_URL="$svcRepo"
	
	
	if [[ "$specVersion" == *SNAPSHOT* ]] ; then
		REPO_ID="csap-snapshot-repo" ;
		REPO_URL="$(dirname $svcRepo)/csap-snapshots/" ;
	fi 
	
	FILE=$specPackageName.zip
	GROUP_ID="bin"
	TYPE="zip"
	
	print_two_columns "FILE" "$FILE"
	print_two_columns "Version" "$specVersion"
	print_two_columns "REPO_ID" "$REPO_ID"
	print_two_columns "REPO_URL" "$REPO_URL"
}

function deploy_specs() {

	update_repo_variables
	print_with_head "Deploying $specPackageName to repository using maven: '$(pwd)'"
	
	
	local deployCommand="deploy:deploy-file -DgroupId=$GROUP_ID -DartifactId=$specPackageName -Dversion=$specVersion -Dpackaging=$TYPE -Dfile=$FILE"
	deployCommand="$deployCommand -DrepositoryId=$REPO_ID -Durl=$REPO_URL"
	
	csap_mvn $deployCommand
}

	
function generate_deployment_specs() { 
	
	print_separator "generate_deployment_specs" ;
	
	print_with_head "generate_deployment_specs: $specPackageName.zip"
	print_two_columns "build in" "$(pwd)"
	print_two_columns "install in" "$csapWorkingDir"
	
	
	sourceFolder=$(pwd) ;
	buildFile="$sourceFolder/configuration/csap-monitoring.jsonnet" ;
	buildFolder="$sourceFolder/build-tools" ;
	projectFolder="$sourceFolder/csap-kube-prometheus" ;
	
	#
	# get jsonnet tools
	#
	#print_two_columns "reload env" "reloading $HOME/.bashrc as build tools get inserted during initial build"
	#source $HOME/.bashrc
	
	build_jsonnet ;
	
	build_kube_promethesius ;
	
	build_csap_monitoring $buildFile ;
	 
	
	print_separator "zipping deployment manifests" ;
	print_line "switching to $projectFolder" ;
	cd $projectFolder ; # need to zip in a relative folder
	zip -q -r $specPackageName manifests
	cp $specPackageName*.zip $sourceFolder;
	
	cd $sourceFolder ;
	
}



function build_jsonnet() {
	
	if is_need_command jsonnet ; then
	
		print_separator "build_jsonnet() - installing dependencies"
	
		osPackages="gcc gcc-c++ git make wget"
		for package in $osPackages ; do
			install_if_needed $package
		done ;
		
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
		print_two_columns "jsonnet" "using existing jsonnet"
	fi ;
	
	if is_need_command go ; then
		export PATH="$PATH:/usr/local/go/bin" ;
	fi ;
	
}

function build_kube_promethesius() {
	
	local cachedLocation="$HOME/$kubePrometheseus" ;
	
	if test -d $cachedLocation ; then
		print_two_columns "cachedLocation" "using $cachedLocation";
		cp --force --recursive $cachedLocation $projectFolder ;
	fi ;
	
	if ! test -d $projectFolder ; then 
		
		mkdir --parents --verbose $projectFolder; 
		cd $projectFolder ;
		
		print_separator "running jsonnet-bundler to initialize jsonnetfile.json"
		jb init;
		
		# Creates `vendor/` & `jsonnetfile.lock.json`, and fills in `jsonnetfile.json`
		print_separator "checking out $kubePrometheseus to $projectFolder"
		jb install github.com/prometheus-operator/kube-prometheus/jsonnet/$kubePrometheseus 
		
		cp --force --recursive $projectFolder $cachedLocation ;
	else
		print_two_columns "kube_promethesius" "using existing $projectFolder"
	fi;
	

}

function build_csap_monitoring() {
	
	local sourceFile="${1-csap.jsonnet}" ;
	print_separator "generating csap-monitoring"
	print_two_columns "sourceFile" "$sourceFile"
	
	cd $projectFolder ;
	
	# https://www.gnu.org/software/bash/manual/html_node/The-Set-Builtin.html
	set -e
	# set -x
	# only exit with zero if all commands of the pipeline exit successfully
	set -o pipefail
	
	# Make sure to use project tooling
	PATH="$(pwd)/tmp/bin:${PATH}"
	
	# Make sure to start with a clean 'manifests' dir
	
	print_two_columns "clean up" " removing manifests"
	rm -rf manifests 
	mkdir -p manifests/setup
	
	#delay_with_message 3 "starting build" ;
	
	# Calling gojsontoyaml is optional, but we would like to generate yaml, not json
	print_two_columns "compiling jsonnet" "this will take several minutes..."
	jsonnet --jpath vendor --multi manifests $sourceFile | xargs -I{} sh -c 'cat {} | /usr/local/go/bin/gojsontoyaml > {}.yaml' -- {}
	
	set +e
	set +o pipefail
	
	# Make sure to remove json files
	print_two_columns "kubernetes prep" "removing non yaml from manifests folder"
	find manifests -type f ! -name '*.yaml' -delete
	rm -f kustomization
	
#	print_with_head "to deploy rto kubenetes:";
#	print_line "cd $projectFolder ;" ;
#	print_line "kubectl delete --ignore-not-found=true -f manifests/ -f manifests/setup ;" ;
#	print_line 'kubectl create -f manifests/setup ; until kubectl get servicemonitors --all-namespaces ; do date; sleep 1; echo ""; done ;'
#	print_line 'kubectl create -f manifests/'


}


function api_package_get() {
	
	print_with_head "api_package_get(): csapBuildVersion: '$csapBuildVersion'"

	if [[ "$csapBuildVersion" == "" ]] ; then
		print_line "api_package_get() skipping binary retrieval, using binary just built"
		return ;
	fi ;
	
	print_line "api_package_get(): removing previous files in $csapPackageDependencies"
	update_repo_variables
	
	\rm --recursive --force $csapPackageDependencies
	
	mkdir -p $csapPackageDependencies
	cd $csapPackageDependencies
	
	csap_mvn dependency:copy -Dtransitive=false -Dartifact=bin:$specPackageName:$specVersion:zip -DoutputDirectory=$(pwd)
}


skipBacklogWaits=true ; # items get added to queue

function api_service_kill() { 

	api_service_stop
	
}


function api_service_stop() { 

	print_with_head "removing $csapName, dir: $(pwd)" ;
	
	# set -x
	# --grace-period=3 --timeout=10s 
#	print_separator "Deleting $csapWorkingDir/manifests"
#	kubectl delete --ignore-not-found=true --recursive=false --filename=$csapWorkingDir/manifests
	
	print_separator "Deleting $csapWorkingDir/manifests"
	find $csapWorkingDir/manifests -type f -name '*.yaml' -print0 \
		| sort --zero-terminated --reverse \
		| xargs --null --replace=theYamlFile \
		kubectl delete --ignore-not-found=true --filename=theYamlFile
	
	local numInstances=$(count_services_in_definition monitoring-tools) ;
	if (( $numInstances > 0 )) ; then 
	
		print_separator "removing $csapName monitoring services from application" ;
		
		envsubst '$csapLife' <$csapWorkingDir/configuration/remove-monitoring.yaml >$csapWorkingDir/remove-monitoring.yaml
		
		local isApply="true"
		update_application $csapWorkingDir/remove-monitoring.yaml $isApply ;
		
	else 
		print_two_columns "monitoring services" "already removed from application" ;
	fi ;

}


function api_service_start() {

	print_with_head "Starting $csapName package installation"
	
	#
	# load any application customizations
	#
	copy_csap_service_resources ;
	
	if [ ! -e  "$csapWorkingDir/manifests" ] ; then
		local specZip=$csapPackageDependencies/$specPackageName*.zip ;
	
		print_with_head "extracting '$specZip' to '$(pwd)'"
		
		if $( ! test -e $specZip ) ; then 	
			print_line "Error: did not find $csapPackageDependencies/specZip.zip  in $csapPackageDependencies "
			exit;	
		fi ;
		
		cp   --force --verbose $specZip .
		unzip -qq -o $specZip ;
		
	fi ; 
	
	
	print_separator "Updating yaml manifests"
	
	for templateVariable in $templateVariables ; do
		key="__"$templateVariable"__";
		value=${!templateVariable} ;
		print_two_columns "manifest update" "replacing: $key with $value" ;
		find $csapWorkingDir/manifests -type f -name '*.yaml' | xargs sed -i "s/$key/$value/g"
	done
	
	
	#
	#  YAML swaps: Defined in Env settings/csap integrations
	#
	for (( counter=1; counter < (100) ; counter++ )) ; do
	  current="yamlCurrent$counter" ;
	  new="yamlNew$counter" ;
	  if [ -z "${!current}" ] || [ -z "${!new}" ]; then
	    break;
	  fi ;

	  currentVal=${!current}
      newVal=${!new}
	  
	  print_two_columns "$current" "$currentVal"
	  print_two_columns "$new" "$newVal"

	  find $csapWorkingDir/manifests -type f -name '*.yaml' | xargs sed -i "s|$currentVal|$newVal|g"
	done


	if $ui_anonymous ; then 
		print_with_head "ui_anonymous was set to true: overwriting packaged manifest that builds grafana.ini" ;
		if test -f $csapWorkingDir/manifests/grafana-config.yaml ; then
			mv --verbose $csapWorkingDir/manifests/grafana-config.yaml $csapWorkingDir/manifests/grafana-config.yaml.auth-enabled-orig ;
		fi ;
		cp --verbose --force $csapWorkingDir/configuration/grafana-config-no-auth.yaml $csapWorkingDir/manifests/grafana-config.yaml ;
		
		local grafanaDeploymentFile="$csapWorkingDir/manifests/grafana-deployment.yaml"
		cp --verbose $grafanaDeploymentFile $grafanaDeploymentFile.orig ;
		
#		print_two_columns "comment out" "mountPath: /etc/grafana" ;
#		sed --in-place --expression='/mountPath: \/etc\/grafana/,+2 s/^/#/' $grafanaDeploymentFile 
#		
#		print_two_columns "comment out" "grafana-config" ;
#		sed --in-place --expression='/\- name: grafana-config/,+2 s/^/#/' $grafanaDeploymentFile 
		
		
		print_two_columns "adding volumeMounts" "$grafanaDeploymentFile" ;
		local volumeMountsDefinition=$(cat <<'EOF'
        - mountPath: /etc/grafana
          name: grafana-no-auth-ini
          readOnly: false
EOF
);
		sed --in-place '/volumeMounts:/r'<(
		    echo -e "$volumeMountsDefinition"
		) -- $grafanaDeploymentFile 
		
		
		print_two_columns "adding volumes" "$grafanaDeploymentFile" ;
		local volumesDefinition=$(cat <<'EOF'
      - configMap:
          name: grafana-no-auth-ini
        name: grafana-no-auth-ini
EOF
);
		sed --in-place '/volumes:/r'<(
		    echo -e "$volumesDefinition"
		) -- $grafanaDeploymentFile 
 
		
	fi ;
	
#	print_separator "Deploying setup specs"
#	kubectl create --filename=$csapWorkingDir/manifests/setup ; 
	
	print_separator "creating manifests/0*"
	find $csapWorkingDir/manifests -type f -name '0*.yaml' -print0 \
		| sort --zero-terminated  \
		| xargs --null --replace=theYamlFile \
		kubectl create --filename=theYamlFile
	
	print_separator "Waiting for service monitor creation"
	until kubectl get servicemonitors --all-namespaces ; do echo -e "\nWaiting for sevicemonitors to be created" ; sleep 5; echo ""; done ;
	
	print_separator "Deploying core specs"
#	kubectl create --filename=$csapWorkingDir/manifests/ ;
	find $csapWorkingDir/manifests -type f -and -name '*.yaml' -and -not -name '0*.yaml' -print0 \
		| sort --zero-terminated  \
		| xargs --null --replace=theYamlFile \
		kubectl create --filename=theYamlFile
	
	local numInstances=$(count_services_in_definition monitoring-tools) ;
	if (( $numInstances == 0 )) ; then 
		print_separator "Adding $csapName monitoring services into application environment '$csapLife'" ;
		
		envsubst '$csapLife' <$csapWorkingDir/configuration/add-monitoring.yaml >$csapWorkingDir/add-monitoring.yaml
		
		local isApply="true"
		update_application $csapWorkingDir/add-monitoring.yaml $isApply ;
		
	else 
		print_two_columns "monitoring services" "already installed in application" ;
	fi ;

}





