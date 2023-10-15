#!/bin/bash

extractAsNeeded=${extractAsNeeded:-false} ;
tomcat_run_folder=$csapPlatformWorking/csap-package-tomcat-runtime
tomcatMavenArtifact="bin:tomcat:9.0.20:tar.gz" ;
tomcatJmxArtifact="bin:catalina-jmx-remote:9.0.20:jar" ;

print_with_date "CSAP Tomcat Package: extractAsNeeded: '$extractAsNeeded' "

function api_package_build() { print_line "api_package_build not used" ; }

function api_package_get() {

	print_with_head "api_package_get() removing $csapPackageDependencies"
	\rm -rf $csapPackageDependencies
	
	mkdir --parents $csapPackageDependencies/tom9
	cd $csapPackageDependencies
	
	csap_mvn dependency:copy -Dtransitive=false -Dartifact=$tomcatMavenArtifact -DoutputDirectory=$csapPackageDependencies/tom9
	csap_mvn dependency:copy -Dtransitive=false -Dartifact=$tomcatJmxArtifact -DoutputDirectory=$csapPackageDependencies/tom9
	
}


function api_service_kill() { print_with_head "KILL" ; }

function api_service_stop() { print_with_head "STOP" ; }

function setTarAndCpParams() {
	numSkipInDocs=`man tar | grep "silently skip" | wc -l`
	if [ $numSkipInDocs == 1 ] ; then 
		tarParams="--skip-old-files"
		cpParams="-n";
	else
		print_line WARNING - clobbering old files. EOL OS detected
		uname -a
		tarParams="";
		cpParams="-u";
	fi ;
}


function api_service_start() {
	
	
	
	print_with_head "Starting tomcat installation. Binaries will be extracted to '$tomcat_run_folder'"
	
	
	if [ "$extractAsNeeded" != "true" ] ; then 
		
		print_line "Extracting runtimes to  '$tomcat_run_folder'. Note by default existing files are NOT overwritten"

		if [ ! -d $tomcat_run_folder ] ; then 
			mkdir -p  $tomcat_run_folder
		fi ;
		
		cd $tomcat_run_folder
		
		print_line "copying tomcat plugin: '$csapWorkingDir/scripts'" 
		cp -rf $csapWorkingDir/scripts/* $tomcat_run_folder
		

		# handle old OSs
		setTarAndCpParams
		
#		print_with_head Extracting tom7 into $tomcat_run_folder 
#		# set -x
#		tar $tarParams -xzf $csapPackageDependencies/tom7/*.gz
#		addTomcatCustomizations tom7
#		#set +x
#		
#		print_with_head Extracting tom8 into $tomcat_run_folder 
#		tar $tarParams -xzf $csapPackageDependencies/tom8/*.gz
#		addTomcatCustomizations tom8
#		
#		print_with_head Extracting tom8.5 into $tomcat_run_folder 
#		tar $tarParams -xzf $csapPackageDependencies/tom8.5/*.gz
#		addTomcatCustomizations tom8.5
		
		
		print_with_head "Extracting tom9 into $tomcat_run_folder"
		tar $tarParams -xzf $csapPackageDependencies/tom9/*.gz
		addTomcatCustomizations tom9
		
		
		print_line  chmod 755 $tomcat_run_folder
		chmod --quiet -R 755 $tomcat_run_folder 
		
	else
		print_line Only configured runtimes will be extracted	
	fi ;

}


	
function addTomcatCustomizations() {
	
	#tomcatRuntimeSetup
	if [ "$csapVanilla" != "true" ] ; then
		
		runtime="$1"
		customSource="unknownSrc"
		customDest="unknownDest/lib"
		case $runtime in
			tom7 ) 
				customSource="$csapPackageDependencies/tom7"
				customDest=`ls -td $tomcat_run_folder/apache-tomcat-7* | head -1`
				;;

				
			tom8 ) 
				customSource="$csapPackageDependencies/tom8"
				customDest=`ls -td $tomcat_run_folder/apache-tomcat-8.0* | head -1`
				;;
				
			tom8.5 ) 
				customSource="$csapPackageDependencies/tom8.5"
				customDest=`ls -td $tomcat_run_folder/apache-tomcat-8.5* | head -1`
				;;
				
				
			tom9 ) 
				customSource="$csapPackageDependencies/tom9"
				customDest=`ls -td $tomcat_run_folder/apache-tomcat-9* | head -1`
				;;
				
			* ) echo "unknown runtime: " $runtime
				;;
		esac;
		
	
		print_line 'customizing '$customDest', add csapVanilla environment variable to use vanilla tomcat'
		
		addTomcatJmx customSource customDest
		
		addTomcatOracle
		
		if [ -e "$serviceConfig/$csapName/$runtime/lib" ]; then
			print_line "Found custom: $serviceConfig/$csapName/$runtime/lib $customDest/lib"
			\cp $cpParams  $serviceConfig/$csapName/$runtime/lib/* $customDest/lib
		fi ;
		
		addTomcatInstanceCustom
	fi ;
	
}

function addTomcatInstanceCustom() {
	
	
	if [ -e $csapWorkingDir/custom/$runtime ] ; then 
		
		print_line "Adding Tomcat Instance overrides to ensure security from $csapWorkingDir/custom/$runtime to  $customDest/custom"
		
		if [ ! -e $customDest/custom ] ; then
			mkdir -p $customDest/custom ;
		fi ;
		
		cp -r $cpParams $csapWorkingDir/custom/$runtime/* $customDest
	else 
		print_line "No additional customizations found: $csapWorkingDir/custom/$runtime"
	fi ;
}


function addTomcatJmx() {
	
	print_line "Adding Tomcat JMX Firewall support jars from $customSource to  $customDest/lib"
	
	cp $cpParams $customSource/*.jar $customDest/lib
	
}

	
function addTomcatOracle() {
	
	jdbcJar=ojdbc6_g.jar ;
	jdbcSource="$ORACLE_HOME/jdbc/lib/$jdbcJar" ;
	
	# override if we find other versions
	if [ -e $ORACLE_HOME/ojdbc7.jar ] ; then 
		jdbcJar=ojdbc7.jar ;
		jdbcSource="$ORACLE_HOME/$jdbcJar" ;
	elif [ -e $ORACLE_HOME/jdbc/lib/ojdbc6.jar ] ; then 
		jdbcJar=ojdbc6.jar ;
		jdbcSource="$ORACLE_HOME/jdbc/lib/$jdbcJar" ;
	elif [ -e $ORACLE_HOME/ojdbc6.jar ] ; then 
		jdbcJar=ojdbc6.jar ;
		jdbcSource="$ORACLE_HOME/$jdbcJar" ;
	elif [ -e $ORACLE_HOME/jdbc/lib/ojdbc14_g.jar ] ; then 
		jdbcJar=ojdbc14_g.jar ;
		jdbcSource="$ORACLE_HOME/jdbc/lib/$jdbcJar" ;
	elif [ -e $ORACLE_HOME/jdbc/lib/ojdbc14.jar ] ; then
		jdbcJar=ojdbc14_g.jar ;
		jdbcSource="$ORACLE_HOME/jdbc/lib/$jdbcJar" ;
	elif [ -e $ORACLE_HOME/ojdbc14.jar ] ; then
		jdbcJar=ojdbc14.jar ;
		jdbcSource="$ORACLE_HOME/$jdbcJar" ;
	else
		print_if_debug "Note:  Did not locate oracle driver in  $ORACLE_HOME, confirm $HOME/.csapEnvironmentOverride. If not using ORACLE OCI this is ok."

	fi ; 
	
	if [ -e "$jdbcSource" ] && [ ! -e "$customDest/lib/$jdbcJar" ] ; then 	
		print_line INFO: Adding $jdbcSource to $customDest/lib ============
		#echo  Did not find $CATALINA_HOME/lib/$jdbcJar
		# echo getting rid of any previous versions
		# \rm -rf ojdbc?.jar
		\cp $cpParams $jdbcSource  $customDest/lib
		
		
	else 
		print_if_debug not install oracle
	fi
	
}

