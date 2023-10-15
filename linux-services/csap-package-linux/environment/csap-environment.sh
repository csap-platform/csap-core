#!/bin/sh

csapDeployAbort="CSAP_DEPLOY_ABORT"



if [ -z "$CSAP_FOLDER" ] ; then 

	export CSAP_FOLDER="/opt/csap/csap-platform" ;
	
	if [ -z "$ENV_FUNCTIONS" ] ; then
		echo "Warning - Did not find CSAP_FOLDER env variable, exported as: '$CSAP_FOLDER'";
	fi ;
	
fi

#
# Note: csap-install and remote-install both customize
#
ENV_FUNCTIONS=${ENV_FUNCTIONS:-$CSAP_FOLDER/bin/functions} ;
source $ENV_FUNCTIONS/misc.sh
source $ENV_FUNCTIONS/print.sh
source $ENV_FUNCTIONS/container.sh
source $ENV_FUNCTIONS/network.sh
source $ENV_FUNCTIONS/service.sh


print_if_debug "Reloading '$HOME/.bashrc'"
#source $HOME/.bashrc

if test -f /etc/bashrc ; then
	source /etc/bashrc ;
fi;

if test -f  $HOME/.csapEnvironment ; then

	print_if_debug "sourcing $HOME/.csapEnvironment"
	source "$HOME/.csapEnvironment"
fi

print_if_debug  "CSAP_FOLDER: '$CSAP_FOLDER'"


scriptName=$(basename -- $0) ;

if [[ ( "$scriptName" != "-bash" ) 
	&& ( "$scriptName" != "bash" ) 
	&& ( $0 != $CSAP_FOLDER/saved/* ) ]] ; then
	# hook to avoid sftp error sessions ; any output will kill them
	# hook to avoid outputing when running scripts
	print_with_date "Running: $scriptName"
else

	if [ "$TERM" == "dumb" ]; then
		return ;
	else
		print_separator "csap environment loaded - type help for commands"
		print_line ""
	fi ;
	
fi ;




args=$*


# put anything here to see output
debug="";


# echo == parsing arguments on `hostname` : $*
if [ "$toolsServer" == "" ] ; then
	toolsServer="csap-dev01.yourcompany.org"
fi ;

#
# platform variables
#
export csapPlatformWorking="$CSAP_FOLDER/working" ;
export csapPackageFolder="$CSAP_FOLDER/packages" ; 
export csapSavedFolder="$CSAP_FOLDER/saved" ; 
export csapDefinitionFolder="$CSAP_FOLDER/definition"
export csapDefinitionResources="$csapDefinitionFolder/resources"
export csapDefinitionProjects="$csapDefinitionFolder/projects"
export tomcat_wrapper="$csapPlatformWorking/csap-package-tomcat-runtime/TomcatWrapper.sh" ;
export csapAgentName=${AGENT_NAME:-csap-agent} ;
export csapAgentId=${AGENT_NAME:-csap-agent} ;
export csapAgentPortAndPath=${AGENT_ENDPOINT:-:8011} ;



#
#  Also Defined in settings.xml
#
csapMavenRepo="$CSAP_FOLDER/maven-repository" ; 

#
# variables
#
export TIMEFORMAT=$'\nreal %3R\tuser %3U\tsys %3S\tpcpu %P\n'
export HISTIGNORE="&:bg:fg:ll:h"
export HOSTFILE=$HOME/.hosts	# Put a list of remote hosts in ~/.hosts

export APACHE_HOME=$csapPlatformWorking/httpd

#export PATH=.:/usr/sbin/:$JAVA_HOME/bin:$APACHE_HOME/bin:$PATH
PATH=/usr/local/bin:/bin:/usr/bin:/usr/local/sbin:/usr/sbin:/sbin
PATH=$JAVA_HOME/bin:$APACHE_HOME/bin:$M2_HOME/bin:$PATH
export PATH=.:$CSAP_FOLDER/bin:$PATH

print_if_debug "path is '$PATH'"
# hook for duplicate path. Long paths can kill OS
#PATH=$(echo -n $PATH | awk -v RS=: '{ if (!arr[$0]++) {printf("%s%s",!ln++?"":":",$0)}}')



if [ "$USER" == "" ] ; then 
	print_with_head "Warning: USER environment variable not set. This will cause scripts to fail - setting to '$(whoami)'" ;
	USER=$(whoami)
	sleep 3
fi ;

if [[ "$csapName" != "" ]] ; then
	#
	#  Service variables
	#
	csapProcessId="$csapName"
	csapStopFile="$csapWorkingDir.stopped" ;
	csapResourceFolder="$csapDefinitionResources/$csapName"
	csapPackageDependencies="$csapPackageFolder/$csapName.secondary" ;
	
fi ;

isCsapDeployScript=false ;
if  [[ ( "$scriptName" == "csap-start.sh" ) \
	|| ( "$scriptName" == "csap-stop.sh" ) \
	|| ( "$scriptName" == "csap-deploy.sh" ) \
	|| ( "$scriptName" == "csap-kill.sh" ) \
	|| ( "$scriptName" == "admin-restart.sh" ) ]] ; then
	isCsapDeployScript=true ;
fi ;

if $isCsapDeployScript ; then
	
	osProcessPriority="0" ;
	isKeepLogs="${isKeepLogs:-no}" ;

	if [ "$csapServer" == "os" ] ; then
		print_with_head "Note: service is in monitored only mode. Refer to documentation for management."
		exit ;
	fi ;
	
	migrate_legacy_resources ;
	process_csap_cli_args $*;
	

	if [[ "$csapName" != "" ]] ; then
		log_environment_variables ;
	fi ;
	

fi ;


if [[ "$csapServer" == "SpringBoot" ]] || [[ "$csapTomcat" == "true" ]] ; then
	jmxPassFile="$csapWorkingDir/jmxremote.password"
	jmxAccessFile="$csapWorkingDir/jmxremote.access"
fi ;

configure_java_environment

if $isCsapDeployScript ; then
	javaVersion=$(java -version 2>&1 | tail -1)
	print_two_columns "JAVA_HOME" "'$JAVA_HOME'"
	print_two_columns "java -version" "'$javaVersion'"
fi ;

if test -f  $HOME/.csap-override ; then

	# enable applications to override anything
	print_if_debug "sourcing $HOME/.csap-override"
	source "$HOME/.csap-override"
fi
