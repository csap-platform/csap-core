#!/bin/bash

#
#  NOTE WHEN UPDATING: version is ALSO in csap-package-linux-secondary/pom.xml for host installation
#
helmArtifact="bin:helm:3.7.0:gz" ;
govcArtifact="bin:govc:0.21.0:gz" ;
mavenArtifact="bin:maven:3.6.1:zip" ;
mavenHome="$CSAP_FOLDER/apache-maven-3.6.1" ;

print_separator "CSAP Linux Package"

	
function api_package_build() { 
	print_section "api_package_build not used" ; 
}




function api_package_get() {
	
	print_section "api_package_get() removing $csapPackageDependencies"
	\rm -rf $csapPackageDependencies
	
	mkdir --parents $csapPackageDependencies
	cd $csapPackageDependencies
	
	csap_mvn dependency:copy -Dtransitive=false -Dartifact=$mavenArtifact -DoutputDirectory=$(pwd)
	
	csap_mvn dependency:copy -Dtransitive=false -Dartifact=$govcArtifact -DoutputDirectory=$(pwd)
	
	csap_mvn dependency:copy -Dtransitive=false -Dartifact=$helmArtifact -DoutputDirectory=$(pwd)
	
}




#
# CSAP agent will always kill -9 after this command
#
function api_service_kill() { print_section "api_service_kill not used" ; }

#
# CSAP agent will always kill -9 after this command. For data sources - it is recommended to use the 
# shutdown command provided by the stack to ensure caches, etc. are flushed to disk.
#
function api_service_stop() { print_section "STOP" ; }


#
# startWrapper should always check if $csapWorkingDir exists, if not then create it using $csapPackageDependencies
# 
#
function api_service_start() {

	print_section "Starting linux package installation"
	
	createLogs ;
	
	update_platform_bin ;
	
	updateSudo ;
		
	install_dependencies ;
	
	install_auto_plays ;
		

}

function install_auto_plays() {

	print_separator "installing auto-plays"
	if test -d $CSAP_FOLDER/auto-plays ; then
		rm --recursive --force --verbose $CSAP_FOLDER/auto-plays ;
	fi ;
	cp --recursive --verbose $csapWorkingDir/auto-plays $CSAP_FOLDER

}

function install_dependencies() {
	
	print_separator  "Installing $csapPackageDependencies in $CSAP_FOLDER"
	ls -l $csapPackageDependencies
	
	
	print_separator  "Installing maven in $CSAP_FOLDER"
	if [ ! -e $csapPackageDependencies/maven*.zip ] ; then 	
		print_line "Error: did not find $csapPackageDependencies/maven*.zip  in $csapPackageDependencies "
		return;	
	fi ;
	
	\rm --recursive --force $CSAP_FOLDER/apache-maven*
	
	#unzip -qq $csapPackageDependencies/apache-maven*.zip -d $CSAP_FOLDER
	unzip -qq $csapPackageDependencies/maven*.zip -d $CSAP_FOLDER
	
	local csapEnvironmentFile="$HOME/.csapEnvironment" ;
	delete_all_in_file "M2_HOME" $csapEnvironmentFile ;
	delete_all_in_file "MAVEN_OPTS" $csapEnvironmentFile ;
	
	append_file "M2_HOME=$mavenHome" $csapEnvironmentFile ;
	append_file 'MAVEN_OPTS="-Djava.awt.headless=true -Xms1g -Xmx2g -XX:MaxMetaspaceSize=512m -Dmaven.wagon.http.ssl.insecure=true -Dmaven.wagon.http.ssl.allowall=true -Dmaven.wagon.http.ssl.ignore.validity.dates=true"' $csapEnvironmentFile ;
	
	print_command \
		"maven version" \
		"mvn --version"
	
	
	print_separator "Installing govc in $CSAP_FOLDER/bin"

	if [ ! -e $csapPackageDependencies/govc*.gz ] ; then 	
		print_line "Error: did not find $csapPackageDependencies/govc*.gz  in $csapPackageDependencies "
		return;	
	fi ;
	
	rm --recursive --force $CSAP_FOLDER/bin/govc
	gunzip --stdout $csapPackageDependencies/govc*.gz > $CSAP_FOLDER/bin/govc
	chmod 755 $CSAP_FOLDER/bin/govc
	
	print_command \
		"govc version" \
		"govc version"
		
	
	print_separator "Installing helm in $CSAP_FOLDER/bin"
	
	if [ ! -e $csapPackageDependencies/helm*.gz ] ; then 	
		print_line "Error: did not find $csapPackageDependencies/helm*.gz  in $csapPackageDependencies "
		return;	
	fi ;
	
	rm --recursive --force $CSAP_FOLDER/bin/helm
	gunzip --stdout $csapPackageDependencies/helm*.gz > $CSAP_FOLDER/bin/helm
	chmod 755 $CSAP_FOLDER/bin/helm
	
	print_command \
		"helm version" \
		"helm version"
	

}



function updateSudo() {
	
	print_section "Updating sudo"
	
	# update hosts to latest set of sudo commands
	if  [ "$CSAP_NO_ROOT" != "yes" ]; then
		
		sudoScript="$csapWorkingDir/installer/install-csap-sudo.sh" ;
		print_two_columns "Updating sudo" "using $sudoScript, updating CSAP_USER with $USER"
			
		# rootDeploy is configured by the host installer
		rm --recursive --force $CSAP_FOLDER/bin/csap-deploy-as-root.sh ;
		cat $sudoScript > $CSAP_FOLDER/bin/csap-deploy-as-root.sh ;
		chmod 755 $CSAP_FOLDER/bin/csap-deploy-as-root.sh ;
		
		sudo $CSAP_FOLDER/bin/csap-deploy-as-root.sh $USER "$CSAP_FOLDER/bin"
		
	fi ;	
}

function createVersion() {
	
	print_separator "Creating version"
	
	packageVersion=$(ls $csapWorkingDir/version | head -n 1)
	
	print_two_columns "Appending" "linux version to package version"
	
	local linuxVersion=$(uname -r)
	local linuxShortVersion=${linuxVersion:0:8}
	
	print_command \
		"cat /etc/redhat-release" \
		"$(cat /etc/redhat-release)" ;
		
	if [ -e /etc/os-release ] ; then
		
		print_command \
			"cat /etc/os-release" \
			"$(cat /etc/os-release)" ;
		source /etc/os-release
		myVersion="$ID-$VERSION_ID"
		
	elif [ -e /etc/redhat-release ] ; then 
		myVersion=$(cat /etc/redhat-release | awk '{ print "rh-"$7}') ;
			
	else
		myVersion="no-etc-os-release"
		
	fi;
	
	myVersion="$myVersion--$packageVersion"
	myVersion=$(echo $myVersion | tr -d ' ') ;
	
	print_two_columns "Renaming" "version folder: $csapWorkingDir/version/$packageVersion to $myVersion"
	
	mv --verbose "$csapWorkingDir/version/$packageVersion" "$csapWorkingDir/version/$myVersion" 

	
}


function createLogs() {
	
	if [ ! -d "$csapLogDir" ] ; then
		print_two_columns "creating logs" "in $csapLogDir, and linking /var/log/messages"
		mkdir -p $csapLogDir
		cd $csapLogDir
		ln -s /var/log/messages var-log-messages
		
		createVersion
		
	else
		print_line "Exit: log directory already present. kill/clean $csapName package before deploying a different version"
		exit;
	fi ;
	
	cd $csapWorkingDir
		
}

function update_platform_bin() {
	
	print_section "Updating '$CSAP_FOLDER'"
	
	local currentBin="$CSAP_FOLDER/bin"
	local previousBin="$csapSavedFolder/bin.old"

	if [ -e $previousBin ] ; then
		
		print_two_columns "Removing previous backup: $previousBin" "$( \rm --recursive --force $previousBin 2>&1 )"
			
	fi
	
	print_two_columns "Backing up current platform" "$( \mv --verbose $currentBin $previousBin 2>&1)"
	
	ensure_files_are_unix "$csapWorkingDir/platform-bin"
	
	print_two_columns "copying $csapWorkingDir/platform-bin $currentBin " "$( \cp --recursive --preserve $csapWorkingDir/platform-bin $currentBin )" ;

	cd $csapWorkingDir
	
}







