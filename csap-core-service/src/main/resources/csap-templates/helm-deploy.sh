#!/bin/bash


source $CSAP_FOLDER/bin/csap-environment.sh


requestedCommand=${1:-none} ;


helmReleaseName="$$service-name" ;
helmReleaseNamespace="$$service-namespace" ;
helmChartName="$$service-chart-name" ;
helmChartVersion="$$service-chart-version" ;
helmChartRepo="$$service-chart-repo" ;


helmValuesFile="$$service-working/helm-values.yaml" ;



print_section "CSAP Helm Helper" ;
print_two_columns "requestedCommand" "$requestedCommand" ;
print_two_columns "helmReleaseName" "$helmReleaseName" ;
print_two_columns "helmReleaseNamespace" "$helmReleaseNamespace" ;
print_two_columns "helmChartName" "$helmChartName" ;
print_two_columns "helmChartVersion" "$helmChartVersion" ;
print_two_columns "helmValuesFile" "$helmValuesFile" ;
	

# ensures install is present, and lists available repos
verify_helm



function deploy() {
	
	create_namespace_if_needed $helmReleaseNamespace ;
	
	
	if [ "$helmChartRepo" != "none" ] ; then
		helm repo add $helmChartRepo
	fi ;
	
	
	local versionCommand="";
	if [ "$helmChartVersion" != "latest" ] ; then
		versionCommand="--version $helmChartVersion"
	fi ;
	
	local valuesCommand="" ;
	if test -f "$helmValuesFile" ; then
		valuesCommand="--values $helmValuesFile" ;
	fi ;

	
	local operation="install" ;
	if $( is_helm_release_deployed $helmReleaseName $helmReleaseNamespace ) ; then
		operation="upgrade" ;
	fi ;
	
	print_with_head "helm $operation  $valuesCommand --namespace $helmReleaseNamespace  $helmReleaseName  $helmChartName $versionCommand"
	helm $operation  $valuesCommand --namespace $helmReleaseNamespace  $helmReleaseName  $helmChartName $versionCommand;
	
	delay_with_message 3 "Waiting for "
	
	print_command \
		"helm status $helmReleaseName" \
		"$( helm status --namespace $helmReleaseNamespace $helmReleaseName )"

}



function remove() {

	helm uninstall --namespace $helmReleaseNamespace $helmReleaseName

}





function perform_operation() {

	case "$requestedCommand" in
		
		"deploy")
			deploy
			;;
		
		"remove")
			remove
			;;
		
		 *)
	            echo "Usage: $0 {remove|deploy}"
	            exit 1
	esac

}

# uncomment to invoke specific command interactively; note no CSAP variables will be set 
# requestedCommand="verify_podman_run"
perform_operation