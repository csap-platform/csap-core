#!/bin/bash

#
#  NOTE WHEN UPDATING: version is ALSO in csap-package-java-secondary/pom.xml for host installation
#

jdkDistribution=${jdkDistribution:-openjdk-17.0.2_linux-x64_bin.tar.gz} ;

# strip off for a shorter name for folder
versionLessSuffix=${jdkDistribution%_linux-x64_bin.tar.gz} ;
versionLessPrefix=${versionLessSuffix#*openjdk-}
shortVersion=${shortVersion:-$versionLessPrefix}


variablesFile="$csapWorkingDir/install-variables.sh"

print_separator "CSAP Java Package"
 
print_two_columns "jdkDistribution" "'$jdkDistribution'"
print_two_columns "version" "'$shortVersion'"



#
# Use this for any software "build" operations. Typically used for building c/c++ code
# -  eg. apache httpd, redis
# -  This method should also upload into a repository (usually maven)
# - Note: most software packages are available as prebuilt distributions, in which case no implementation is required
#
function api_package_build() { print_with_head "api_package_build not used" ; }


#
# Use this for getting binary packages - either prebuilt by distributions (tomcat, mongodb, cassandra,etc.)
#   or built using buildAdditionalPackages() above
#   Note that CSAP deploy will invoke this on the primary host selected during deployment, and then automatically
#   - synchronize the csapPackageDependencies to all the other hosts (via local network copy) for much faster distribution
#
function api_package_get() {

	print_with_head "retrieving jdk"
	
	\rm --recursive --force $csapPackageDependencies
	
	mkdir --parents --verbose $csapPackageDependencies
	cd $csapPackageDependencies
	
	# support local vms
	localDir="$HOME/opensource/"
	if [ -e $localDir/$jdkDistribution ] ; then 
		print_line using local copies from $localDir
		cp $localDir/$jdkDistribution .
	else		
		
		#print_line "Downloading from toolsServer: http://$toolsServer/java"
		#wget -nv "http://$toolsServer/java/$jdkDistribution"
		
		csap_mvn dependency:copy -Dtransitive=false -Dartifact=bin:jdk:$shortVersion:tar.gz -DoutputDirectory=$(pwd)
		
		buildReturnCode="$?" ;
		if [ $buildReturnCode != "0" ] ; then
			print_line "Found Error RC from build: $buildReturnCode"
			echo __ERROR: Maven build exited with invalid return code
			exit 99 ;
		fi ;

	fi ;
	
}




#
# CSAP agent will always kill -9 after this command
#
#
function api_service_kill() { print_with_head "api_service_kill not used" ; }

#
# CSAP agent will always kill -9 after this command. For data sources - it is recommended to use the 
# shutdown command provided by the stack to ensure caches, etc. are flushed to disk.
#
function api_service_stop() { print_with_head "STOP" ; }

 
#
# startWrapper should always check if $csapWorkingDir exists, if not then create it using $csapPackageDependencies
# 
#
function api_service_start() {
	
	
	
	#
	# We add serviceName to params so that process state is reflected in UI

	versionFolder="$csapWorkingDir/version/$shortVersion" ;
	
	print_two_columns "creating" "$versionFolder"
	mkdir --parents --verbose $versionFolder
	touch "$versionFolder/empty.txt" 
	
	# always install under csap account
	
	
	install_java ;
	
	
     
#	local javaInstallFile="$csapWorkingDir/scripts/rootInstall.sh"	
#    if [ "$CSAP_NO_ROOT" == "yes" ]; then 	
#		# hook for running on non root systems
#		$javaInstallFile "$csapWorkingDir" "$csapPackageDependencies" "$jdkDistribution" "$shortVersion"
#		
#	else
#		build_variables_file ;
#
#		print_command \
#			"$csapName configuration file: '$variablesFile'" \
#			"$(cat $variablesFile)" 
#		
#		run_using_csap_root_file "install" "$javaInstallFile" "$variablesFile"  
#
#	fi ;
	  

    
	cd $csapWorkingDir ;
    

	
}

function install_java() {
	
	local javaFolderName="openjdk-$shortVersion" ;
	
	print_two_columns "source" "$csapWorkingDir" ;
	print_two_columns "javaFolderName" "$javaFolderName" ;
	
	local javaVersionsDir="$(dirname $CSAP_FOLDER)/java" ;
	local installPath="$javaVersionsDir/$javaFolderName" ;
	
	local testOnly=false;
	
	if [ -d  $installPath ] ; then
	
		testOnly=true;
		
		installPath="$installPath"-test ;
		print_with_head "found existing '$installPath', testonly install in $installPath"
		chmod --recursive 755 $installPath 
		\rm --recursive --force $installPath
		
	fi
	
	
	print_two_columns "installPath" "$installPath" ;
	
	mkdir --parents --verbose $javaVersionsDir ;
	print_two_columns "java base" "$javaVersionsDir"
	cd $javaVersionsDir
	
	
	local csapEnvFile=$HOME/.csapEnvironment ;
	if $testOnly ; then
		print_two_columns "test run" "skipping update of $csapEnvFile"
	else
		JAVA17_HOME=$installPath
		
		print_two_columns "deleting" "JAVA17_HOME from $csapEnvFile"
		delete_all_in_file "JAVA17_HOME" $csapEnvFile ; 
		
		append_line  export JAVA17_HOME=$installPath
		local isDefaultJava=$(if [[ $(basename "$csapWorkingDir") == "csap-package-java" ]]; then echo true; else echo false; fi) ;
		
		if $isDefaultJava ; then 
			print_two_columns "default java" "detected because $csapWorkingDir matches csap-package-java"
			delete_all_in_file "JAVA_HOME"  ;
			
			append_line  export JAVA_HOME=$installPath ;
			append_line  export PATH=\$JAVA_HOME/bin:\$PATH ;
			
		else
			print_two_columns "Not default" "detected because csapWorkingDir does not matche csap-package-java"
		fi ;
	fi ;
	
	

	
	
	\rm --recursive --force temp
	mkdir --parents --verbose temp
	cd temp
	
	print_two_columns "extracting"  "$csapPackageDependencies/*jdk*.tar.gz"
	print_two_columns "destination"  "$(pwd)"
	tar --preserve-permissions --extract --gzip --file $csapPackageDependencies/*jdk*.tar.gz
	
	print_two_columns "moving" "$(pwd) to $installPath" ;
	mv --force * $installPath
	
	print_two_columns "permissions" "running chmod --recursive 555 $installPath" ;
	chmod --recursive 555 $installPath 
	
	
}




function build_variables_file() {

	rm --recursive --force --verbose $variablesFile
	append_file "# generated file" $variablesFile true
	
	# set verbose to false
	append_file "#" $variablesFile false
	
	append_line  export csapUser="$csapUser"
	
	append_line  export csapPackageDependencies="$csapPackageDependencies"
	append_line  export jdkDistribution="$jdkDistribution"
	append_line  export shortVersion="$shortVersion"
	
	append_line  export CSAP_FOLDER="$CSAP_FOLDER"
	append_line  export csapName="$csapName"
	append_line  export csapProcessId="$csapProcessId"
	append_line  export csapWorkingDir="$csapWorkingDir"
	append_line  export csapPrimaryPort="$csapPrimaryPort"
	append_line  export csapLogDir="$csapLogDir"
	
}
