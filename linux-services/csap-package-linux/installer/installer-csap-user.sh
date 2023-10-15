#!/bin/bash


scriptDir=$(realpath $(dirname $0))
scriptName=$(basename $0)
source $scriptDir/installer-common-functions.sh


function setup_environment() {
	csapEnvironmentFile="$HOME/.csapEnvironment" ;
	if test -f  $csapEnvironmentFile ; then 
		
		print_with_head "Using existing csapEnvironmentFile: '$csapEnvironmentFile'" ;
	
	else
	
		print_two_columns "environment" "creating csapEnvironmentFile: '$csapEnvironmentFile'" ;
		append_file "# generated by installer-csap-user.sh" $csapEnvironmentFile false
		
		# defined in installer-common-functions.sh
		append_line ""
		append_line "export CSAP_FOLDER=$CSAP_FOLDER"
		append_line ""
	fi ;
	
	# load variables for access to staging and processing contents
	source $csapEnvironmentFile
	
	if test -d $CSAP_FOLDER  ; then
		print_section "WARNING: Found existing csap-platform installation: $CSAP_FOLDER" ;
		# "Confirm disks have been wiped or previous version removed, and all previous processes killed, and retry. "
		print_line "OK if containers not removed, otherwise verify clean up output above"
		delay_with_message $userDelaySeconds "Continuing installation" ;
	fi ;
}

setup_environment

#
# Variables used during installation
#
applicationFolder="$CSAP_FOLDER/definition";
csapPackageUrl="http://$packageServer/csap/csap-host-2.0.0$allInOnePackage.zip"

print_if_debug "Starting '$0' : params are '$*'"

function extract_csap_platform() {

	if [ -z "$localPackages" ] ; then 
	
		print_separator "Running normal install in current directory: '$(pwd)'"
		numberPackagesLocal=$(ls -l /root/csap-host*.zip | wc -l)
		if (( $numberPackagesLocal == 1 )) ; then
			
			local csapZip="$scriptDir/../csap-host*.zip" ;
			print_two_columns "Unzipping" "$csapZip to '$(pwd)'"
			unzip -q $csapZip
			exit_on_failure $?
			
		else
			print_error "Expected a single csap-host.zip, but found: '$numberPackagesLocal'" ;
			exit 99 ;
		fi ;
		
	else 

		print_separator "running non root install '$localPackages'"
		
		# extract csap linux commands
		mkdir --parents $CSAP_FOLDER
		\cp -r $localPackages/platform-bin $CSAP_FOLDER/bin
		
		PACKAGES=$CSAP_FOLDER/packages
		mkdir --parents $PACKAGES
		
		print_with_head "copying $localPackages/csap-core-service-*.jar $PACKAGES/CsAgent.jar"
		cp --verbose $localPackages/csap-core-service-*.jar $PACKAGES/CsAgent.jar
		cp --verbose $localPackages/csap-core-service-*.jar $PACKAGES/admin.jar
		
		
		print_with_head "copying $localPackages/csap-package-linux-*.zip $PACKAGES/csap-package-linux.zip"
		cp --verbose $localPackages/csap-package-linux-*.zip $PACKAGES/csap-package-linux.zip
		
		# getting linux dependencies (maven)
		mkdir --parents $PACKAGES/linux.secondary
		print_with_head "copying $localPackages/apache-maven-*-bin.zip $PACKAGES/csap-package-linux.secondary"
		cp --verbose $localPackages/apache-maven-*-bin.zip $PACKAGES/linux.secondary

		print_with_head "copying $localPackages/csap-package-java-*.zip $PACKAGES/csap-package-java.zip"
		cp --verbose $localPackages/csap-package-java-*.zip $PACKAGES/csap-package-java.zip
		
		# getting linux dependencies (maven)
		mkdir --parents $PACKAGES/csap-package-java.secondary
		print_with_head "copying $localPackages/jdk-*-linux-x64.tar.gz $PACKAGES/csap-package-java.secondary"
		cp --verbose $localPackages/jdk-*-linux-x64.tar.gz $PACKAGES/csap-package-java.secondary
		
	fi ;
	
	print_two_columns "$HOME/.bashrc" "copied from $CSAP_FOLDER/bin/admin.bashrc"
	echo  source $CSAP_FOLDER/bin/admin.bashrc >> $HOME/.bashrc
	source ~/.bashrc
	
	# only needed on windows build
	chmod --recursive 755 $CSAP_FOLDER/bin
}

extract_csap_platform


# Placed at top for ez updating of package
function install_java() {
	
	print_separator "installing java"
	#
	# Setting VARIABLES needed by package installer - do not make local
	#
	csapName="csap-package-java";
	javaInstallLocation=$csapPlatformWorking/$csapName
	
	print_two_columns "creating"  "'$javaInstallLocation'"
	mkdir --parents --verbose $javaInstallLocation
	
	cd $javaInstallLocation
	csapWorkingDir=$( pwd );
	
	print_two_columns "loading" "$CSAP_FOLDER/bin/csap-environment.sh, with messages hidden" ;
	source $CSAP_FOLDER/bin/csap-environment.sh >/dev/null

	print_two_columns "extracting" "$csapPackageFolder/csap-package-java.zip to $csapWorkingDir" ;
	unzip -qo $csapPackageFolder/csap-package-java.zip
	exit_on_failure $?
	
	print_two_columns "loading" "$csapWorkingDir/csap-api.sh"
	source $csapWorkingDir/csap-api.sh
	
	print_two_columns "invoking" "api_service_start, current folder: '$(pwd)'"
	api_service_start
	
	#print_with_head Exiting ; exit
}

install_java

cd $HOME



function modify_domain_references () {
	
	print_separator "Checking for generic references in definition"
	
	company=$(dnsdomainname)
	print_two_columns "Replacing" "yourcompany.com with: '$company'" ;
	
	DEFAULT_COMPANY="yourcompany.com"
	
	if [ "$company" == "" ] ; then
		print_error "Warning: dnsdomainname did not resolve host, stripping fqdn from host. This is ok for test vms, but avoid on clustered systems"
		DEFAULT_COMPANY=".yourcompany.com"
	fi
	
	replace_all_in_file "$DEFAULT_COMPANY" "$company" $applicationFolder/*-project.json "true"
	
}

function setup_default_application () {
	
	
	print_two_columns "Extracting" "using csap agent as default source"
	\rm -rf $applicationFolder;
	unzip -o -d $csapSavedFolder $csapPackageFolder/csap-agent.jar BOOT-INF/classes/csap-templates/application-definition/*
	exit_on_failure $?
	
 	\cp -rv $csapSavedFolder/BOOT-INF/classes/csap-templates/application-definition $applicationFolder;
 	
 	if [ $mavenSettingsUrl != "default" ] ; then
		print_two_columns "downloading" "$mavenSettingsUrl"
		wgetWrapper $mavenSettingsUrl
		\mv settings.xml $applicationFolder/resources
	else 
		print_two_columns "mavenSettingsUrl" "not specified in installer, public spring repo is the default"
	fi
	
	if [ $mavenRepoUrl != "default" ] ; then
		print_two_columns "mavenRepoUrl" "updating $applicationFolder/*-project.json with $mavenRepoUrl"
		sed -i "s=http://repo.spring.io/libs-release=$mavenRepoUrl=g" $applicationFolder/*-project.json
	else 
		print_two_columns "mavenRepoUrl" "not specified in installer, public spring repo is the default"
	fi	
	
	modify_domain_references
	
	memoryOnHostInKb=$(free|awk '/^Mem:/{print $2}');
	memoryOnHostInMb=$((memoryOnHostInKb / 1024 ))
	print_two_columns "memoryOnHostInMb" "$memoryOnHostInMb"
	
	if [[ "$memoryOnHostInMb" -lt 1000 ]] ; then 
		
		print_with_head "Host has less then 1GB configured memory: $memoryOnHostInMb Mb. Removing non-essential services from *-project.json...."
		sed -i '/"admin": \[/{N;N;d}' $applicationFolder/*-project.json
		sed -i '/"CsapTest": \[/{N;N;d}' $applicationFolder/*-project.json
		sed -i '/"SimpleServlet": \[/{N;N;d}' $applicationFolder/*-project.json
	fi ;
	
	if [ "$csapDefinition" == "defaultMinimal" ]  || [ "$csapDefinition" == "defaultAgent" ] ; then
		print_with_head "Using minimal install - only CsAgent, docker will be configured in clusters"
		sed -i '/"admin": \[/{N;N;d}' $applicationFolder/*-project.json
		sed -i '/"CsapTest": \[/{N;N;d}' $applicationFolder/*-project.json
		sed -i '/"SimpleServlet": \[/{N;N;d}' $applicationFolder/*-project.json
		sed -i '/"CsapStarter",/ d' $applicationFolder/*-project.json
		sed -i '/"nginx",/ d' $applicationFolder/*-project.json
		sed -i '/"mpstatMonitor",/ d' $applicationFolder/*-project.json
		sed -i '/"Java",/ d' $applicationFolder/*-project.json
		# make linux final item in array
		sed -i '/"tomcat"/ d' $applicationFolder/*-project.json
		sed -i 's="linux",="linux"=g' $applicationFolder/*-project.json
	fi ;
	
	if  [ "$csapDefinition" == "defaultAgent" ] ; then
		print_with_head "Agent only - removing docker"
		sed -i '/"docker",/ d' $applicationFolder/*-project.json
	fi ;
	
}

function setup_definition() {
	print_separator "setup_definition(): '$csapDefinition'"
	
	if [[ "$csapDefinition" == http* ]] ; then
	
	
	 	\rm --recursive --force $applicationFolder application.zip
	
		print_separator "retrieving from remote"
	 	wget --output-document application.zip $csapDefinition
	 	
	 	unzip  -q -o -d $applicationFolder application.zip
	 	exit_on_failure $?
	 	
	 	modify_domain_references
	
	 	
	elif [[ "$csapDefinition" == default* ]] ; then
	
		setup_default_application ;
		
	elif [[ "$csapDefinition" == *.zip ]] ; then
		
		# copied in place by root install 
		homeDirDef=$scriptDir/../$(basename $csapDefinition)
		print_separator "Using local definition file '$homeDirDef'"
	 	unzip  -q -o -d $applicationFolder $homeDirDef
	 	exit_on_failure $?
	 	
	else
		print_separator "Downloading definition from host"
	 	\rm -rf $applicationFolder
	 	
	 	print_two_columns "wget" "http://$csapDefinition:8011/CsAgent/os/definitionZip"
	 	wget http://$csapDefinition:8011/CsAgent/os/definitionZip
	 	unzip  -q -o -d $applicationFolder definitionZip
	 	exit_on_failure $?
	fi ;
	
	#
	# if being run using docker image - ignore
	#
	if [ $isDocker != true ] ; then
		
		if [[ $(hostname --short) == "csap-01" ]] ; then 
			print_two_columns "template" "csap_def_template_host ignored, csap-01 is excluded as it is the lab install verification host";
		else
			print_two_columns "template"  "replacing with $(hostname --short)";
			replace_all_in_file "csap_def_template_host" "$(hostname --short)" $applicationFolder/*-project.json "true"
		fi ;
	fi

	# && ( ($csapDefinition == default ) || ( $csapDefinition == *csap-dev01.davis.yourcompany.lab* )  )
#	if [[ ( "$csapDefinition" != "defaultPublic" ) 
#			&& ( ($csapDefinition == default ) || ( $csapDefinition == *csap-dev01.davis.yourcompany.lab* )  )
#			 ]] ; then 
#	
#		print_two_columns "yourtown" "using yourtown repos: removing 'mco-' references"
#		replace_all_in_file "mco-" "" $applicationFolder/*-project.json "true"
#		
#	fi ;
}

setup_definition

print_two_columns "csap-admin.jar" "Copying csap-agent.jar to csap-admin.jar as they use the same binary"
cp --verbose $csapPackageFolder/csap-agent.jar $csapPackageFolder/csap-admin.jar

print_separator "$scriptName completed"

