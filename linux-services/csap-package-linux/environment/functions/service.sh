
#
#  csap-*.sh functions
#

function csap_credentials() {
	echo $(cat $HOME/.csap-config) ;
}

function agent () {

	local agent_api="http://$(hostname --long):8011" ;
	local numArgs="$#" ;
	if (( $numArgs > 0 )) ; then
		csap-cli.sh --lab $agent_api --api $* ;
	else
		csap-cli.sh --help ;
		print_with_head "try: csap model/hosts/base-os --jpath /hosts"
	fi ;
}

function csap () {
	
#	local admin_api="http://$(hostname --long):8021/csap-admin" ;
#	
#	if ! $(is_process_running csap-admin) ; then
#		# look up url if not running on current host
#		admin_api=$(agent /model/service/urls/csap-admin --jpath /0)
#	fi ;
	
	local numArgs="$#" ;
	if (( $numArgs > 0 )) ; then
		csap-cli.sh --lab $csapAdminUrl --api $* ;
	else
		csap-cli.sh --help ;
		print_with_head "try: csap model/hosts/base-os --jpath /hosts"
	fi ;
	
}

function is_admin_on_host() {
	
	if [ -n "$adminRc" ] ; then 
		return $adminRc ;  # cache results on expensive call
	fi ;
	print_two_columns "is_admin_on_host()" "checking if csap-admin is on host $(hostname --long)";
	adminRc=0; 
	if (( $(count_services_on_host csap-admin) == 0 )) ; then
		adminRc=99;	
	fi ;
	
	return $adminRc ;
	
}

skipBacklogWaits=false ;
function wait_for_csap_backlog() {

	local max_poll_result_attempts=${1:-100} ;
	local currentAttempt=1;
	
	if $skipBacklogWaits ; then
		print_two_columns "wait_for_csap_backlog" "skip configured: $skipBacklogWaits"
		sleep 2;
		return ;
	fi ;
	
	for i in $(seq $currentAttempt $max_poll_result_attempts); do
    
		local backLogCount;
		
		if [ -z "$csapAdminUrl" ] ; then
			if is_admin_on_host ; then
				backLogCount=$(csap application/deployment/backlog?isTotal=true --jpath /backlog) ;
			else 
				backLogCount=$(agent application/deployment/backlog?isTotal=true --jpath /backlog) ;
			fi;
		else
			backLogCount=$(csap application/deployment/backlog?isTotal=true --jpath /backlog) ;
		fi ;
	
		
		containerMatches=$(echo $logOutput | grep "$logPattern" | wc -l)
        
		print_line "wait_for_csap_backlog() attempt $i of $max_poll_result_attempts: '$backLogCount' items remaining\n"
    
		if (( $backLogCount == 0 )) ; then
			break ;
		fi ;
		
		
        sleep 5 ;
	done

}


function update_application() {

	local pathToAutoPlayFile=${1:-demo} ;
	local isApply=${2:-false} ;
	local formatter=${3:-jq .} ;
	
	local credContent=$(cat $HOME/.csap-config) 
	local creds=(${credContent//,/ })
	local user=${creds[0]} ;
	local pass=${creds[1]} ;
	
	

	# jq provides formatting functions
	if [[ "$formatter" == jq*  ]] ; then
		install_if_needed epel-release ;  # contains jq 
		install_if_needed jq ; 
	fi ;
	
	local apply="isApply=$isApply"
	
	if ! $isApply ; then
		print_with_head "Test Mode Only: parameter 2 either defaulted or set to false"
	fi; 
	
	local content="content@$pathToAutoPlayFile"
	if ! test -f $pathToAutoPlayFile ; then
		print_line "Warning: file not found, assuming autoplay is: '$pathToAutoPlayFile'" ;
		content="content=$pathToAutoPlayFile" ;
	fi
	
	wait_for_csap_backlog
	
	print_separator "using curl to hit $csapAdminUrl/api/application/autoplay"
	local apiOutput=$( \
		curl  \
			--silent \
			--data-urlencode "$pass" \
			--data "$user" \
			--data "$apply" \
			--data-urlencode "$content" \
			--request POST \
			$csapAdminUrl/api/application/autoplay \
			)
		
	
	if [[ "$formatter" == jq*  ]] ; then
		print_separator "summary"
		echo $apiOutput | jq --raw-output '.autoplayFile'
		echo $apiOutput | jq --raw-output '.creating'
		echo $apiOutput | jq '."autoplay-results"'
		echo $apiOutput | jq --raw-output '."parsing-summary"'
		
		print_separator "details"
		echo $apiOutput | jq --raw-output '."parsing-results"'
	else
		echo $apiOutput | $formatter
	fi ;
	
	wait_for_csap_backlog
	
	sleep 3;
	
}


function count_running() {

	local serviceName=${1:-csap-verify-service} ;
	local blocking=${2:-false}
	local project=${3:-$csapPackage}

	# Note: & requires escaping and quoting
	# local numberServices=$(csap "application/services/running/$serviceName?blocking=$blocking\&project=$project")  ;
	
	# Better?: use curl escaping
	local numberServices=$( \
		curl  \
			--silent \
			--get \
			--data "blocking=$blocking" \
			--data-urlencode "project=$project" \
			$csapAdminUrl/api/application/services/running/$serviceName \
			)
	
	echo $numberServices ;

}

function count_services_on_host() {
	local serviceName=${1:-some-service-name} ;
	local numFound=$(agent model/services/name --parse | grep --only-matching --word-regexp $serviceName | wc -w) ;
	echo $numFound;
}


function count_services_in_definition() {
	local serviceName=${1:-some-service-name} ;
	local numFound=$(agent model/services/names/all --parse | grep --only-matching --word-regexp $serviceName | wc -w) ;
	echo $numFound;
}

function count_running_services() {

	local serviceNames=${*:-csap-verify-service} ;
	
	local numRunning=0;
	local doRefresh=true ;
	local serviceName ;
	for serviceName in $serviceNames ; do
		local serviceRunning=$(count_running $serviceName $doRefresh) ;
		print_two_columns "$serviceName" "$serviceRunning"
		
		numRunning=$(( $numRunning + $serviceRunning ))  ;
		doRefresh=false;
	done
	
	print_two_columns "total" "$numRunning"  ;
	
	return $numRunning ;

}

function stop_services() {

	local includePatterns=${1:-test} ;
	local clean=${2:-no} ;
	local excludePatterns=${3:-csap-agent} ;
	local waitForBacklog=${4:-true} ;
	local printDetails=${5:-false} ;
	local project=${6:-none} ;
	
	local projectParam="";
	if [ "$project" != "none" ] ; then
		projectParam="project=$project," ;
	fi
	
	local serviceNamesInStopOrder=$(csap model/services/name?"$projectParam"reverse=true --parse) ;
	
	if $printDetails ; then 
		print_two_columns "serviceNamesInStopOrder" "$serviceNamesInStopOrder" ;
	fi ;
	
	print_separator "stopping services: include: $includePatterns, exclude: $excludePatterns"
	
	local serviceName ;
	for serviceName in $serviceNamesInStopOrder ; do
	
		# grep misses hyphens in word matches
		#local isMatched=$(echo $includePatterns | grep --only-matching --word-regexp $serviceName | wc -w)
		local isMatched=false ;
		local includePattern ;
		for includePattern in $includePatterns ; do
			if [ $includePattern ==  $serviceName ] ; then
				isMatched=true ;
				break;
			fi ;
		done ;
	
		if $isMatched && [[ $serviceName != $excludePatterns* ]]  ; then
			print_two_columns "stopping" "$serviceName" ;
			
			local stopParams="$projectParam""serviceName=$serviceName";
			if [[ "$clean" != "no" ]] ; then
				stopParams="$stopParams,clean=$clean";
			fi ;
			print_command "output" "$( \
				 csap application/service/stop \
				-params "$(csap_credentials),$stopParams" \
				)"
	
			if $waitForBacklog ; then
				wait_for_csap_backlog
			fi ;
		else
			if $printDetails ; then 
				print_two_columns "skipping" "$serviceName" ;
			fi ;
		fi ;
		
	done ;
}

function deploy_services() {

	local includePatterns=${1:-test} ;
	local excludePatterns=${2:-csap-agent} ;
	local waitForBacklog=${3:-true} ;
	local printDetails=${4:-false} ;
	
	local serviceNamesInStartOrder=$(csap model/services/name --parse) ;
	
	print_separator "deploying services: include: $includePatterns, exclude: $excludePatterns"
	
	local serviceName ;
	for serviceName in $serviceNamesInStartOrder ; do
	
		local isMatched=false ;
		local includePattern ;
		for includePattern in $includePatterns ; do
			if [ $includePattern ==  $serviceName ] ; then
				isMatched=true ;
				break;
			fi ;
		done ;
	
		if $isMatched && [[ $serviceName != $excludePatterns* ]]  ; then
			print_two_columns "deploying" "$serviceName" ;
			
			print_command "output" "$( \
				 csap application/service/deploy \
				-params "$(csap_credentials),serviceName=$serviceName" \
				)"
	
			if $waitForBacklog ; then
				wait_for_csap_backlog
			fi ;
				
		else
			if $printDetails ; then
				print_two_columns "skipping" "$serviceName" ;
			fi ;
		fi ;
		
	done ;
}

function start_services() {

	local includePatterns=${1:-test} ;
	local excludePatterns=${2:-csap-agent} ;
	local waitForBacklog=${3:-true} ;
	local printDetails=${4:-false} ;
	
	local serviceNamesInStartOrder=$(csap model/services/name --parse) ;
	
	print_separator "starting services: include: $includePatterns, exclude: $excludePatterns"
	
	local serviceName ;
	for serviceName in $serviceNamesInStartOrder ; do
	
		local isMatched=false ;
		local includePattern ;
		for includePattern in $includePatterns ; do
			if [ $includePattern ==  $serviceName ] ; then
				isMatched=true ;
				break;
			fi ;
		done ;
	
		if $isMatched && [[ $serviceName != $excludePatterns* ]]  ; then
			print_two_columns "starting" "$serviceName" ;
			
			print_command "output" "$( \
				 csap application/service/start \
				-params "$(csap_credentials),serviceName=$serviceName" \
				)"
	
			if $waitForBacklog ; then
				wait_for_csap_backlog
			fi ;
				
		else
			if $printDetails ; then
				print_two_columns "skipping" "$serviceName" ;
			fi ;
		fi ;
		
	done ;
}



function pause_all_deployments() {
	local pauseFile="$csapPlatformWorking/_pause-all-deployments" ;
	print_with_head "All deployments suspended: use the csap deployment monitor to resume"
	touch $pauseFile ;
}


function process_csap_cli_args() {

	print_two_columns "cli" "process_csap_cli_args() parsing parameters"
	
	mkdir --parents --verbose $csapPlatformWorking
	mkdir --parents --verbose $csapPackageFolder
	mkdir --parents --verbose $csapSavedFolder
	mkdir --parents --verbose $csapMavenRepo

	print_if_debug  "arguments: $args"
	print_if_debug  "csap-environment.sh\t:" "=================== Parsing Command Line Start ================"

	commandArgs="";
	
	while [ $# -gt 0 ] ; do
	
		case $1 in
		
		    -csapDeployOp )
		      print_if_debug  "csap-environment.sh\t:" "-csapDeployOp used to tag process in case of long deployment times needing to be aborted/killed"   ;
		      shift 1
		    ;;
		    
		    #
		    #  Agent CLI bootstrap environment
		    #
		    
		    -d | -default )
		    
		      print_if_debug  "csap-environment.sh\t:" "-d used for cli starts. Agent will restart after loading parameters from definition" 
		      print_with_head "Setting Agent CLI variables"
			  csapName="$csapAgentName"; csapHttpPort=8011; csapProcessId="$csapName"
			  csapLife="dev"; csapServer="SpringBoot";
		      svcSpawn="yes"; csapWorkingDir="$csapPlatformWorking/$csapName" csapLogDir="$csapWorkingDir/logs"
			  csapParams="-Dspring.profiles.active=dev,agent,company -Dsun.rmi.transport.tcp.responseTimeout=3000 -XX:MaxMetaspaceSize=144M -Xms512M -Xmx512M"
			  
				if [ -z "$dockerHostFqdn" ] ; then 
					print_line "dockerHostFqdn is NOT set; adding restart flag" ;
					csapParams="$csapParams -Dorg.csap.needStatefulRestart=yes" ;
				fi;
		
		      shift 1
		      ;;
		           
		    -s | -spawn )
		      print_if_debug  "csap-environment.sh\t:" "-s spawn was triggered"  
		      svcSpawn="yes";
		      shift 1
		      ;;
		      
		    
		    #
		    # Core switches
		    #
		    
		    -osProcessPriority )
		      print_if_debug  "csap-environment.sh\t:" "-osProcessPriority was specified,  Parameter: $2"   ;
		      osProcessPriority="$2" ;
		      shift 2
		    ;;
		    
		    -keepLogs )
		      print_if_debug  "csap-environment.sh\t:" "-keepLogs was triggered "  
		      isKeepLogs="yes";
		      shift 1
		      ;;
		      
		    -v | -skipDeployment )
		      print_if_debug  "csap-environment.sh\t:" "-v skipDeployment was triggered"  
		      svcSkipDeployment="yes";
		      shift 1
		      ;;
		      
			-x | -cleanType )
		      print_if_debug  "csap-environment.sh\t:" "-x clean, Parameter: $2"  
		      svcClean="$2" ;
		      shift 2
		      ;;
	
		    #
		    #  SCM / Build
		    #      
		    -r | -repo )
		      print_if_debug  "csap-environment.sh\t:" "-r repo was triggered, Parameter: $2"  
		      svcRepo="$2" ;
		      shift 2
		      ;;
		      
		
		    -b | -scmBranch )
				print_if_debug  "csap-environment.sh\t:" "-b branch was triggered, Parameter: $2"  
				SCM_BRANCH="$2"
		    	shift 2
		      ;;
		    
		    
		    -scmLocation )
				print_if_debug  "csap-environment.sh\t:" "scmLocation was passed but no longer needed Parameter: $2"  
		    	shift 2
		      ;;
		    
		    -m | -mavenCommand )
		      print_if_debug  "csap-environment.sh\t:" "-m was triggered, Parameter: $2, doing a global replace on _"  
		      mavenBuildCommand=$(echo $2|sed 's/__/ /g') ;
		      shift 2
		     
		      ;;
		      
		    -u | -scmUser )
		      print_if_debug  "csap-environment.sh\t:" "-u was triggered, Parameter: $2"  
		      SCM_USER="$2" ;
		      shift 2
		      ;;
		
	
		 
		    *)
		      print_with_head "Adding argument to commandArgs: $1"
		      commandArgs="$commandArgs $1"
		      shift 1
		    ;;
		esac
	done
	
	print_if_debug "csapName: '$csapName' csapAgentName: '$csapAgentName'"

	print_if_debug  "csap-environment.sh\t:" "=================== Parsing Command Line End ================"
}

function migrate_legacy_resources() {
	local legacyApplicationFile="$csapDefinitionFolder/Application.json" ; 
	
	
	local newApplicationFile="$csapDefinitionFolder/CHANGEME-project.json" ; 
	
	local hostNameShort=$(hostname --short) ;
	if [[  $hostNameShort == centos1 ]]  ; then
		newApplicationFile="$csapDefinitionFolder/sample-project.json" ;
	elif [[  $hostNameShort == *csap-dev* ]]  ; then
		newApplicationFile="$csapDefinitionFolder/csap-dev-project.json" ;
	elif [[  $hostNameShort == *netmet* ]]  ; then
		newApplicationFile="$csapDefinitionFolder/netmet-project.json" ;
	elif [[  $hostNameShort == *rni* ]]  ; then
		newApplicationFile="$csapDefinitionFolder/yourapp-project.json" ;
	fi ;
	
	if test -f $legacyApplicationFile ; then
	
		print_with_head "migrating $legacyApplicationFile to $newApplicationFile";
		
		if ! test -f $newApplicationFile ; then
			mv --verbose --force $legacyApplicationFile $newApplicationFile ;
		else
			print_with_head "warning - $newApplicationFile already exists"
		fi ;
		
	fi ;
	
	if ! test -d $csapDefinitionProjects; then
		mkdir --parents --verbose $csapDefinitionProjects
		append_file "add templates and projects" $csapDefinitionProjects/add-projects-here.txt false
	fi ;
	
	
	#
	#  2.0.9 and later no longer uses propertyOverride
	#
#	local legacyResourceFolder="$CSAP_FOLDER/conf/propertyOverride" ;
	
#	if test -d $legacyResourceFolder; then
#	
#		print_with_head "migrating $legacyResources to $csapDefinitionResources"
#		
#		mkdir --parents --verbose $csapDefinitionResources
#		
#		legacyResources="$legacyResourceFolder/*"
#		for legacyResource in $legacyResources ; do
#		
#			if test -f $legacyResource ; then
#				\cp --verbose $legacyResource $csapDefinitionResources
#			else
#				legacyItem=$(basename $legacyResource)
#				\cp --recursive --verbose --force $legacyResource/resources $csapDefinitionResources/$legacyItem
#			fi
#		done ;
#		
#		mv --verbose $legacyResourceFolder $CSAP_FOLDER/saved
#		
#	fi ; 
}

function log_environment_variables() {

	local variablesFile="$csapPlatformWorking/$csapName-environment.log" ;

	if test -f $variablesFile ; then
		\rm --force $variablesFile
	fi ;
	
	append_file "\n" $variablesFile false
	append_line "$(print_separator "generated by $CSAP_FOLDER/bin/csap-environment.sh") "
 
	append_line "$( print_columns csapName			"$csapName"			csapPrimaryPort	"$csapPrimaryPort" )"
	append_line "$( print_columns csapWorkingDir	"$csapWorkingDir" "csapLogDir" $csapLogDir )"
	append_line "$( print_columns csapResourceFolder "$csapResourceFolder" "set" "Application over ride files" )"
	append_line "$( print_columns csapHttpPort		"$csapHttpPort"		csapJmxPort		"$csapJmxPort" )"
	append_line "$( print_columns csapServer   		"$csapServer"		csapTomcat      "$csapTomcat"		csapHttpPerHost "$csapHttpPerHost" )"
	append_line "$( print_columns csapPackage		"$csapPackage"		csapLife		"$csapLife"			csapLbUrl "$csapLbUrl" )"
	append_line "$( print_columns csapArtifact		"$csapArtifact"		csapRelicaCount	"$csapRelicaCount"  )"
	append_line "$( print_columns csapVersion		"$csapVersion"		csapServiceLife "$csapServiceLife"  )"
	
	append_line "$( print_columns csapProcessId		"$csapProcessId"	csapPids		"$csapPids"  )"
	
	append_line "$( print_columns csapAjp        "MASKED" "Refer to"  "https://github.com/csap-platform/csap-core/wiki#updateRefCSAP+Loadbalancing" )"
	append_line "$( print_columns csapPeers      "$csapPeers" )"
	# print_line peers with explicit assignment are available as:  "$csapName_peer_1"
	
	append_line "$( print_columns "Csap Encryption" ""  "CSAP_ALGORITHM" $CSAP_ALGORITHM CSAP_ID "Encryption token masked" )"
	#append_line "$( print_columns redisSentinels $redisSentinels  )"
	append_line "$( print_columns notifications "-"  csapAddresses $csapAddresses csapFrequency "$csapFrequency $csapTimeUnit" csapMaxBacklog $csapMaxBacklog  )"
	append_line "$( print_columns CSAP_FOLDER "$CSAP_FOLDER" csapPlatformWorking "$csapPlatformWorking"  )"
	append_line "$( print_columns csapAdminUrl "$csapAdminUrl" )"
	
	append_line "$( print_columns hostUrlPattern "$hostUrlPattern" mailServer "$mailServer" csapDockerRepository "$csapDockerRepository" )"
	
	append_line "$( print_command csapParams "$csapParams" )"
	append_line "$( print_command customAttributes	"$customAttributes"  )"
	
	append_line "\n\n ------------ full env ----------------"
	
	
	append_line "$(env)"

}

function configure_java_environment() {
	if [[  $csapParams == *csapJava8* ]]  ; then
		
		if [[ "$JAVA8_HOME" != "" && -e "$JAVA8_HOME" ]] ; then
			export JAVA_HOME=$JAVA8_HOME
			export PATH=$JAVA8_HOME/bin:$PATH
		else
			print_with_head "warning: JAVA8_HOME variable is not set. reverting to vm default"
		fi
		
	elif [[  $csapParams == *csapJava17* && -e "$JAVA17_HOME" ]]  ; then
		if [ "$JAVA17_HOME" != "" ] ; then
			export JAVA_HOME=$JAVA17_HOME
			export PATH=$JAVA17_HOME/bin:$PATH
		else
			print_with_head "warning: JAVA17_HOME variable is not set. reverting to vm default"
		fi
	
	elif [[  $csapParams == *csapJava9* && -e "$JAVA9_HOME"  ]]  ; then
		if [ "$JAVA9_HOME" != "" ] ; then
			export JAVA_HOME=$JAVA9_HOME
			export PATH=$JAVA9_HOME/bin:$PATH
		else
			print_with_head "warning: JAVA9_HOME variable is not set. reverting to vm default"
		fi
		
	elif [[  $csapParams == *csapJava11* && -e "$JAVA11_HOME"  ]]  ; then
		if [ "$JAVA11_HOME" != "" ] ; then
			export JAVA_HOME=$JAVA11_HOME
			export PATH=$JAVA11_HOME/bin:$PATH
		else
			print_with_head "warning: JAVA11_HOME variable is not set. reverting to vm default"
		fi
	fi ;
	
	#print_line "JAVA_HOME: '$JAVA_HOME' , java -version: '$javaVersion'"
	
}

#
# maven
#

function csap_mvn() {
	
	local mavenSettings="$CSAP_FOLDER/bin/settings.xml" ;
	if [ -e "$csapDefinitionResources/settings.xml" ] ; then
		mavenSettings="$csapDefinitionResources/settings.xml"
	else
		print_line "Warning: $csapDefinitionResources/settings.xml not found - best practice is to include one in definition/resources folder"
	fi

	print_line ""
	print_two_columns "mvn" "--batch-mode --settings $mavenSettings" 
	print_two_columns "command" "$*" 
	print_two_columns "MAVEN_OPTS" "$MAVEN_OPTS" 
	
	print_separator "maven output start"
	
	#
	# indenting output so that content can be folded in most editors
	#
	mvn --batch-mode --settings $mavenSettings $* 2>&1 | sed 's/^/  /'
	
	# buildReturnCode="$?" ;
	#
	# bash pipestatus gets the rc when commands are piped
	#
	local buildReturnCode="${PIPESTATUS[0]}" ;
	if (( $buildReturnCode != 0 )) ; then
		print_error "Found Error RC from maven build: '$buildReturnCode'" ;
		# __ERROR token aborts deployments in ServiceOsManager.java
		print_line "__ERROR: $(pwd)" ;
		exit 99 ;
	fi ;
	
	print_separator "maven output end"
}


function copy_csap_service_resources() {
    if [ -d "$csapResourceFolder" ] ; then
    
    	if ! test -r "$csapWorkingDir/configuration.original" ; then
			backup_original "$csapWorkingDir/configuration"
		else
			print_line "package configuration already backed up $csapWorkingDir/configuration.original" ;
		fi ;
    	
    	if [ -d "$csapResourceFolder/common" ] ; then
	    	print_two_columns "service resources" "copying '$csapResourceFolder/common'" ;
	    	\cp --recursive --verbose --force $csapResourceFolder/common/* $csapWorkingDir ;
    	fi
    
    	if [ -d "$csapResourceFolder/$csapLife" ] ; then
	    	print_two_columns "service resources" "copying '$csapResourceFolder/$csapLife'" ;
	    	\cp --recursive --verbose --force $csapResourceFolder/$csapLife/* $csapWorkingDir ;
    	fi
    	
	else
	
		print_two_columns "service resources" "No custom settings found, if desired add files to $csapResourceFolder using csap editor."
		
	fi ;
	
}


#
# Deprecated: multiple jobs may collide on the file.
#
function run_using_root_eol() {
	
	command_to_run="$*" ;
	
	print_dashed "run_using_root: '$command_to_run'"
	
	wait_for_terminated csap-deploy-as-root.sh 10 root
	
	rm -rf $CSAP_FOLDER/bin/csap-deploy-as-root.sh
	
	echo $command_to_run > $CSAP_FOLDER/bin/csap-deploy-as-root.sh
	
	chmod 755 $CSAP_FOLDER/bin/csap-deploy-as-root.sh
	sudo $CSAP_FOLDER/bin/csap-deploy-as-root.sh
			
}

#
# enables root sessions 
#

function root_command() {
	# remove the header line
	run_using_root "$*" | sed 3d
}

addCsapEnvironment="no" ;
function root_csap_command() {
	# load a working env
	addCsapEnvironment="yes" ;
	run_using_root "$*" ;
	addCsapEnvironment="no" ;
}

function run_using_root() {
	
	command_to_run="$*" ;
	
	print_separator "run_using_root: '$command_to_run'"
	
	NOW=$(date +"%h-%d-%I-%M-%S") ;
	mkdir --parents $CSAP_FOLDER/saved/scripts-run
	local tempScript=$CSAP_FOLDER/saved/scripts-run/csap-run-as-root-$NOW.sh ;
	
	local index=1;
	while test -f $tempScript ; do
	
		index=$(( $index + 1 )) ;
		tempScript=$CSAP_FOLDER/saved/scripts-run/csap-run-as-root-$NOW-$index.sh ;
		
		#print_two_columns "testing" "$tempScript"
		
		if (( $index > 50 )) ; then
			print_error "failed to get unique name" ;
			exit 99 ;
		fi ;
	done ;
	
	
	
	if [ "$addCsapEnvironment" == "yes" ] ; then
		echo "# generate from service.sh" > $tempScript ;
		echo "source $CSAP_FOLDER/bin/csap-environment.sh" >> $tempScript ;
		echo $command_to_run >> $tempScript ;
	else 
		echo $command_to_run > $tempScript ;
	fi ;
	
	
	chmod 755 $tempScript
	
	# set terminal to dumb so that env messages are suppressed
	export TERM="dumb"
	sudo $CSAP_FOLDER/bin/csap-run-as-root.sh $tempScript "root" 2>&1 \
	  | sed '0,/_CSAP_OUTPUT_/d'
	  
	 # | sed 6d
		
	if [[ "$deleteCsapRootFile" == true ]] ; then
		# scheduled jobs should not fill up saved folder - so delete
		\rm -f $tempScript ;
	fi ;
			
}

function run_using_csap_root_file() {
	
	command_to_run="$1" ;
	command_script="$2" ;
	variable_script="$3" ;
	
	
	
	# remove first argument from $*, it will be replaced with helperFile
	shift 1 ;
	
	rm -rf $CSAP_FOLDER/bin/csap-deploy-as-root.sh
	
	if [ ! -f "$variable_script" ] ; then 
		print_line "Did not find variable_script: '$variable_script'" ;
		return ;
	fi;
	
	cat $variable_script > $CSAP_FOLDER/bin/csap-deploy-as-root.sh
	
	if [ ! -f "$command_script" ] ; then 
		print_line "Did not find command_script file: '$command_script'" ;
		return ;
	fi;
	
	cat $command_script >> $CSAP_FOLDER/bin/csap-deploy-as-root.sh

	
	chmod 755 $CSAP_FOLDER/bin/csap-deploy-as-root.sh
	sudo $CSAP_FOLDER/bin/csap-deploy-as-root.sh $CSAP_FOLDER/bin/csap-environment.sh $command_to_run
			
}

function run_using_csap_root() {
	
	rootInstallScript="$1" ;
	
	# remove first argument from $*, it will be replaced with helperFile
	shift 1 ;
	
	rm -rf $CSAP_FOLDER/bin/csap-deploy-as-root.sh
	
	if [ ! -f "$rootInstallScript" ] ; then 
		print_line "Did not find root install script file: '$rootInstallScript'" ;
		return ;
	fi ;
	
	cat $rootInstallScript > $CSAP_FOLDER/bin/csap-deploy-as-root.sh
	
	chmod 755 $CSAP_FOLDER/bin/csap-deploy-as-root.sh
	sudo $CSAP_FOLDER/bin/csap-deploy-as-root.sh $CSAP_FOLDER/bin/csap-environment.sh $*
			
}
