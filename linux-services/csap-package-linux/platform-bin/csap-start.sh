#!/bin/bash
#
# Script uses convention of containing folder name to generate default for war name and env and JMX_PORT
# You can override if you want, but sticking to convention is easier
#

scriptDir=`dirname $0`

if [ "$CSAP_FOLDER" == "" ] ; then
	# csap-start.sh is registered with systemctl. core env needs to be loaded
	echo "loading '$HOME/.bashrc'. Assumed: systemctl start csap in progress"
	source $HOME/.bashrc
fi

# loads params
source $CSAP_FOLDER/bin/csap-environment.sh

print_if_debug Running $0 : dir is $scriptDir
print_if_debug param count: $#
print_if_debug params: $@

if [ ! -e "$csapDefinitionFolder" ] ; then
	print_with_head "Did not find Application definition. Use restartAdmin.sh clone on existing clusters or add -clone to install"
	print_with_head "Sleeping for 5 seconds in case of file lag"
	sleep 5
	if [ ! -e "$csapDefinitionFolder" ] ; then
		print_with_head "WARNING Sleeping for 10 seconds in case of file lag"
		sleep 10
	fi ;
fi ;

 
isClean=`expr match "$svcClean" 'clean' != 0`
isSuperClean=`expr match "$svcClean" 'super' != 0`
isSpawn=`expr match "$svcSpawn" 'yes' != 0`
isSkip=`expr match "$svcSkipDeployment" 'yes' != 0`
isHotDeploy=`expr match "$hotDeploy" 'yes' != 0`

if [ "$csapName" == "" ]; then
	echo usage $0 -d starts cs agent with default settings - everything else should use ui
	# echo usage $0 -i \<service_port\> -u \<scmUser\> optional: -b \<scmBranch\> -w \<warDir\> 
	echo exiting
	exit ;
fi ;



if [ "$csapServer" == "" ] ; then
	print_with_head "Error: unable to find required parameters, use -d for service agent"
	exit ;
fi ;

# push into background on 8011 because we are killing the admin jvm
overRideClean="" ;
if [ "$csapName" == "$csapAgentName" ] ; then

	print_with_head "$csapAgentName svcClean is '$svcClean'"
	if [ "$svcClean" != "" ] && [ "$svcClean" != "no" ] ; then
		echo svcClean set so overriding so that super clean does not occur
		overRideClean="-cleanType clean" ;
	fi ;
	
	# isSpawn prevents circular calls with killInstance
	if [ $isSpawn == "0" ] ; then
	  # The "-q" option to grep suppresses output.
	  print_with_head "$csapName matches $csapAgentName so running in background since admin process kills itself "
	  print_line "look for content in $CSAP_FOLDER/nohup.txt file. Wait a few minutes and reload the browser."
	  
	  $scriptDir/csap-kill.sh $args $overRideClean 
	  exit ;
	fi
	#sleep 5 ;
else

	if [ $isHotDeploy != "1" ] ; then
	
		print_section "csap-start.sh: ensuring processes are stopped"
		killOutput=$($scriptDir/csap-kill.sh $args 2>&1 ) ;
		print_if_debug "$killOutput"
		if [[ "$killOutput" =~ "Skipping kill" ]] ; then
			print_two_columns "process check" "No existing processes found" ;
		else
			print_command "killOutput" "$(echo -e "$killOutput" | grep kill)"
		fi ;
		
	fi ; 
	
fi ;

if [ "$csapHttpPort" != 0 ] ; then 
	if ! $(wait_for_port_free "$csapHttpPort" 2 "csap-start.sh: pre start check") ; then
		print_with_head "WARNING: proceeding with start, but port conflict may occur"
		print_line "CSAP host dashboard port explorer can be used to identify process holding port"
	fi
fi
 


if [ ! -e "$csapPackageFolder/$csapName.war" ] \
	&& [   ! -e "$csapPackageFolder/$csapName.zip" ] \
	&& [   ! -e "$csapPackageFolder/$csapName.jar" ] ; then
	
	 print_with_head "Did not find $csapPackageFolder/$csapName., build must be done"
	
	exit ;
fi ;

#
# Ensure working folder created
#
print_two_columns "working folder" "$(mkdir --parents --verbose $csapWorkingDir)"

isJmxEnabled=false;

function configure_java_options() {

	local javaOptions="$csapParams" ;
	local flagForJavaKill="csapProcessId=$csapProcessId" ;
	
	javaOptions="$javaOptions  -D$flagForJavaKill -DCSAP_FOLDER=$CSAP_FOLDER" ;
	
	if [ "$csapJmxPort" != "" ] ; then
	
		isJmxEnabled=true;
	
		javaOptions="$javaOptions -Dcom.sun.management.jmxremote.port=$csapJmxPort -Dcom.sun.management.jmxremote.rmi.port=$csapJmxPort" ;
	
		local allowRemoteJmx=${allowRemoteJmx:-false} ;
		if [[ "$allowRemoteJmx" == "true" ]] ; then
			print_with_head "Remote JMX Access Enabled" ;
			javaOptions="$javaOptions -Djava.rmi.server.hostname=$(hostname --long)" ;
		else
			javaOptions="$javaOptions -Djava.rmi.server.hostname=localhost -Dcom.sun.management.jmxremote.host=localhost" ;
		fi ;
		
		local isJmxAuthentication=${isJmxAuthentication:-true} ;
		local jmxUser=${jmxUser:-csap} ;
		local jmxPassword=${jmxUser:-csap} ;
		
		#javaOptions="$javaOptions -Dcom.sun.management.jmxremote.port=$JMX_PORT"
		javaOptions="$javaOptions -Dcom.sun.management.jmxremote.authenticate=$isJmxAuthentication"
		javaOptions="$javaOptions  -Dcom.sun.management.jmxremote.ssl=false"
		
		if [[ $isJmxAuthentication == "true" ]] ; then 
			# JMX Firewall Users
			javaOptions="$javaOptions  -Dcom.sun.management.jmxremote.password.file=$jmxPassFile"
			javaOptions="$javaOptions  -Dcom.sun.management.jmxremote.access.file=$jmxAccessFile"
			
			# generate access files roles are: readonly or readwrite: javasimon requires readwrite
			rm --recursive --force  $jmxAccessFile $jmxPassFile
			append_file "$jmxUser readwrite" $jmxAccessFile false
			append_file "$jmxUser $jmxPassword" $jmxPassFile false
			chmod 700 $jmxPassFile
			
		fi ;
	
	fi ;
	
	JAVA_OPTS="$javaOptions" ;
	print_command "java options variable JAVA_OPTS" "$JAVA_OPTS" ;
}

if [[ "$csapServer" == "SpringBoot" ]] || [[ "$csapTomcat" == "true" ]] ; then 
	configure_java_options
fi ;


#customPeerId=$csapName"_peer_"
# echo customPeerId $customPeerId
# env | grep "peer"
#peers=`env | grep $customPeerId`

function updateServiceOsPriority {
	servicePattern="$1"
	if [ -e $CSAP_FOLDER/bin/csap-renice.sh ]  \
			&& [ "$CSAP_NO_ROOT" != "yes" ] \
			&& [ "$osProcessPriority" != 0 ] ; then
		
		print_with_head Service has set a custom os priority
		sleep 5 ;
		
		sudo $CSAP_FOLDER/bin/csap-renice.sh $servicePattern $osProcessPriority
	fi
}

	
startOverrideFile="$csapDefinitionResources/serviceStartOverride.sh"
if [ -e "$startOverrideFile" ] ; then

	print_with_head  "Warning: $startOverrideFile  found  in '$csapDefinitionResources' folder" 
	print_line "This can corrupt the FS - project team must carefully test"
	
	if `is_command_installed dos2unix` ; then
		print_with_head "Found scripts in package, running dos2unix"
		dos2unix  -n $startOverrideFile $CSAP_FOLDER/temp/serviceStartOverride.sh 
	else
		print_with_head "Warning: did not find  dos2unix. Ensure files are linux line endings"
		cp $startOverrideFile $CSAP_FOLDER/temp/serviceStartOverride.sh ;
	fi ;
	chmod 755 $CSAP_FOLDER/temp/serviceStartOverride.sh
	$CSAP_FOLDER/temp/serviceStartOverride.sh $csapName $csapServiceLife $csapLife
	
else
	print_if_debug "optional: starts may be extended using: '$csapDefinitionResources/serviceStartOverride.sh'"  
fi


reservedPorts="9099 10250 10251 10252 10253 10254 10255 10256 10257 10258 10259 30080"
function reserve_kubernetes_ports() {

	# (java grabs random port for attach and optionally jmx)
	print_with_head "Reserving ports: $reservedPorts" ;
	for port in $reservedPorts ; do
		nc --listen $port --exec csap_reserve_kubernetes_ports &
	done ;
	
	sleep 1;
	
	ps -ef | grep csap_reserve_kubernetes_ports
}

function release_kubernetes_ports() {
	
	print_separator "Releasing kubernetes port reservations" 
	pkill --full csap_reserve_kubernetes_ports 
	
	sleep 1;
	
}

if [ "$csapServer" == "SpringBoot" ] ; then
	
	cd $csapWorkingDir ;
	
	export csapFqdn="$(hostname --long)";
	if [ -n "$dockerHostFqdn" ] && [ "$dockerHostFqdn" != "container" ] ; then 
		print_line "dockerHostFqdn is set '$dockerHostFqdn', it will be used instead of hostname: '$csapFqdn' " ;
		export csapFqdn="$dockerHostFqdn" ;
	fi;
	print_line "Adding csapFqdn: '$csapFqdn' for agent host name"
	
	export csapPrimaryInterface='eth0' ; # default to centos primary interface
	if (( $(ip a | grep ens192: | wc -l) > 0 )) ; then
		print_line "csapPrimaryInterface auto discovery: ip a matched ens192:" ; 
		export csapPrimaryInterface='ens192'
		
	elif (( $(ip a | grep enp0s3: | wc -l) > 0 )) ; then
		print_line "csapPrimaryInterface auto discovery: ip a matched enp0s3:" ; 
		export csapPrimaryInterface='enp0s3'
	else
		print_line "csapPrimaryInterface auto discovery: ip a did NOT match ens192:" ; 
	fi
	print_line "Adding csapPrimaryInterface: '$csapPrimaryInterface' for agent collection"
	
	
#	if $isJmxEnabled ; then reserve_kubernetes_ports ; fi ;
	#reserve_kubernetes_ports
	source csap-integration-springboot.sh
	startBoot
	if [ "$csapName" == "$csapAgentName" ] ; then
		agentPid="$!"
		print_with_head "Updating $CSAP_FOLDER/agent.pid with $agentPid"
		set +o noclobber
		echo $agentPid > $CSAP_FOLDER/agent.pid
	fi ;
	#release_kubernetes_ports
#	if $isJmxEnabled ; then release_kubernetes_ports ; fi ;
	
	

	servicePattern='.*java.*csapProcessId='$csapName'.*'
	updateServiceOsPriority $servicePattern

	exit ;
	
fi ;

if [ "$csapTomcat" == "true" ] ; then 
	if [ -e "$tomcat_wrapper" ]  ; then
	
		#if $isJmxEnabled ; then reserve_kubernetes_ports ; fi ;
		source $tomcat_wrapper
		tomcatStart
		#if $isJmxEnabled ; then release_kubernetes_ports ; fi ;
		print_with_head "tomcat start has completed. Use csap application portal and logs to verify service is active."
		exit ;
	else
		print_with_head "Did not find csap tomcat package installed: '$tomcat_wrapper'. Update application definition and deploy it."
	fi ;
fi ;



if [ "$csapServer" == "csap-api" ] ; then
	
	cd $csapWorkingDir ;
	
	source csap-integration-api.sh
	
	if `is_function_available api_service_start` ; then
		api_service_start
	else
		startWrapper
	fi
	
	print_line "..."
 
	if [ -e $CSAP_FOLDER/bin/csap-renice.sh ]  ; then
		servicePattern='.*processing.*'$csapName'.*'
		
		if [ "$osProcessPriority" != "0" ] && [ "$CSAP_NO_ROOT" != "yes" ]; then
			sudo $CSAP_FOLDER/bin/csap-renice.sh $servicePattern $osProcessPriority
		else
			print_if_debug Skipping priority since it is 0
	 fi
	fi
	
	print_line "Token to exit read loop in OsCommandRunner.java -csap-abort-script-io-" ;
	exit ;
fi ;



print_with_head "Unhandled csapServer: $csapServer . Contact your Application manager for support"


