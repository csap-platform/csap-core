#!/bin/bash


print_section "csap-integation-springboot.sh"

function addOracleOci() {
	
	jdbcSource="" ;
	
	# override if we find other versions
	if [ -e $ORACLE_HOME/ojdbc7.jar ] ; then 
		jdbcJar=ojdbc7.jar ;
		jdbcSource="$ORACLE_HOME/$jdbcJar" ;
	elif [ -e $ORACLE_HOME/jdbc/lib/ojdbc6.jar ] ; then 
		jdbcJar=ojdbc6.jar ;
		jdbcSource="$ORACLE_HOME/jdbc/lib/$jdbcJar" ;
	elif [ -e $ORACLE_HOME/jdbc/lib/ojdbc6_g.jar ] ; then 
		jdbcJar=ojdbc6_g.jar ;
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
	fi ; 
	
	if [ "$jdbcSource" != "" ] ; then
		print_line Oracle Configuration $jdbcSource
	fi;
	
}


function startBoot() {
	
	local javaTempFolder="$csapWorkingDir/java-io-tmpdir"
	local jarExtractDir="$csapWorkingDir/jarExtract"	
	local springBootClasses="$jarExtractDir/BOOT-INF/classes"
	
	print_line ""
	print_two_columns "jarExtractDir" "$jarExtractDir" 
	print_two_columns "springBootClasses" "$springBootClasses" 
	print_two_columns "javaTempFolder" "$javaTempFolder" 
	
	
	if  [ "$isSkip" != "1" ]  ; then
		
		\rm -rf $springBootClasses
		
		
		print_line ""
		print_two_columns "extracting" "jar contents to '$jarExtractDir'" 
		/usr/bin/unzip -qq -o $csapPackageFolder/$csapName.jar -d $jarExtractDir
	
		print_line ""
		print_two_columns "property files" "Checking for optional files"
		if [ -e "$springBootClasses/$csapLife" ]; then
			print_line "Found packaged resources: '$springBootClasses/$csapLife', copying to '$springBootClasses'"
			\cp -fr $springBootClasses/$csapLife/* $springBootClasses
		else
			print_line "Did not find packaged resources: '$springBootClasses/$csapLife'"
		fi ;
		
		if [ -e "$csapResourceFolder/common" ]; then
			print_line "Found lifecycle Overide properties: '$csapResourceFolder/common', copying to '$springBootClasses'"
			\cp -fr $csapResourceFolder/common/* $springBootClasses
		else
			print_line "Did not find common override resources: '$csapResourceFolder/common'"
		fi ;
		
		if [ -e "$csapResourceFolder/$csapLife" ]; then
			print_line "Found lifecycle Overide properties: '$csapResourceFolder/$csapLife', copying to  '$springBootClasses'"
			\cp -fr $csapResourceFolder/$csapLife/* $springBootClasses
		else
			print_line "Did not find lifecycle override resources: '$csapResourceFolder/$csapLife'"
		fi ;
		
		if [ -e "$csapResourceFolder/$csapLife" ]; then
			print_line "Found csapLife Overide properties: $csapResourceFolder/$csapLife, copying to  $springBootClasses"
			\cp -fr $csapResourceFolder/$csapLife/* $springBootClasses
		else
			print_line "Did not find csapLife override resources: $csapResourceFolder/$csapLife"
		fi ;
		
		csapExternal=$(eval echo $csapExternalPropertyFolder)
		if [[ "$csapExternal" != "" && -e "$csapExternal/$csapLife" ]]; then
			
			print_line "Found csapExternal: '$csapExternal/$csapLife', copying to '$springBootClasses'"
	
			\cp -rf $csapExternal/$csapLife/* $springBootClasses
				
		else
			print_line "Did not find csapExternal"
		fi
		
		print_line ""
		print_line "Spring boot post extraction configuration..."
		if [ -e "$javaTempFolder" ] ; then
			print_line "Warning '$javaTempFolder' already exists, and may contain state such as tomcat persisted session data"
		else
			print_line "Creating '$javaTempFolder' folder" 
			mkdir $javaTempFolder ;
		fi

		configureLogging
	else
		print_line "skipping Extract"
	fi
	
	
	configureDeployVersion
	
	# add oracle OCI driver if present
	addOracleOci
	
	export CLASSPATH="$jdbcSource:$jarExtractDir"
	print_line "exporting CLASSPATH: '$CLASSPATH'"
	
	springProfiles="--spring.profiles.active=$csapLife"
	if [[ $JAVA_OPTS == *spring.profiles.active* ]] ; then 
		print_line "spring profile specified in parameters: replacing CSAP_LIFE with $csapLife"
		springProfiles="";
		JAVA_OPTS="${JAVA_OPTS/CSAP_LIFE/$csapLife}"
		#print_line "JAVA_OPTS: '$JAVA_OPTS'"
	else
		print_line "Using default spring profile '$springProfiles'"
	fi
	
	if [ "$csapDockerTarget" == "true" ]  ; then
		print_line "Service configured for docker, start will be triggered via docker container apis"
	else
		
		args="$JAVA_OPTS -Djava.io.tmpdir=$javaTempFolder org.springframework.boot.loader.JarLauncher $springProfiles" ;
		args="$args --server.port=$csapHttpPort"
		
		launch_background \
			"$JAVA_HOME/bin/java" \
			"$args" \
			"$csapLogDir/console.log" \
			"appendLogs"
		
#		set -x
#		$JAVA_HOME/bin/java  $JAVA_OPTS -Djava.io.tmpdir="temp" \
#			org.springframework.boot.loader.JarLauncher $springProfiles  \
#			--server.port=$csapHttpPort >> $csapLogDir/consoleLogs.txt 2>&1 &
#		set +x ; sync
	fi ;

	print_section "Service has been started: review logs and application metrics to assess health."

}

function configureLogging() {
	
	print_separator "Log folder: $csapLogDir"
	
	if [ -d $csapLogDir ] ; then
		print_line "Found existing folder - re using"
		return;
	fi ;
	
	if [ -e $csapWorkingDir.logs ] ; then 
		print_line "moving existing Log folder from: $csapWorkingDir.logs"
		mv  $csapWorkingDir.logs $csapLogDir
	else
		mkdir --parents --verbose $csapLogDir
	fi ;

		
}

function configureDeployVersion() {
	bootVersion="none"
	if [ -e $csapPackageFolder/$csapName.jar.txt ] ; then
		bootVersion=$(grep -o '<version>.*<' $csapPackageFolder/$csapName.jar.txt  | cut -d ">" -f 2 | cut -d "<" -f 1)
	fi ;

	print_separator "creating $csapWorkingDir/version : $bootVersion"
	
	\rm -rf $csapWorkingDir/version
	mkdir --parents --verbose $csapWorkingDir/version/$bootVersion
	touch $csapWorkingDir/version/$bootVersion/created_by_SpringBootWrapper

		
}

function stopBoot() {


	local csapProcessFilter="csapProcessId=$csapProcessId"
	local svcPid=$(ps -u $USER -f| grep $csapProcessFilter  | grep -v -e grep -e $0 | awk '{ print $2 }')
	local waitForSeconds=15 ;
	
	print_two_columns "stop" "'$csapName' pid: '$svcPid'"
		
	if [ "$svcPid" != "" ] ; then
		print_two_columns "SpringBoot" "SIGTERM used to shutdown gracefully. Use kill if it does not shutdown."
		kill -SIGTERM $svcPid
		wait_for_terminated $csapProcessFilter $waitForSeconds;
	else
		echo "pid not found" 
	fi ;
	
}


function deployBoot() {
	
	print_if_debug "Spring Boot deploy: No customizations"
	
}


function killBoot() {
	
	if [ "$csapName" == "csap-admin" ] ; then 
		print_two_columns "csap-admin" "Shutting csap admin down gracefully to allow for alert backup and sleeping 5 seconds";
		stopBoot ;
	fi ;
	
	print_if_debug "Spring boot lets OS perform the kill: '$csapName' pid: '$svcPid'"

	
}