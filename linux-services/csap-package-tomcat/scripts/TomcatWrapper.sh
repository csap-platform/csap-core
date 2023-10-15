#!/bin/bash


tomcatRuntimes=$csapPlatformWorking/csap-package-tomcat-runtime

print_separator "TomcatWrapper.sh - run time folder: '$tomcatRuntimes'"

isSecure="${isSecure:-no}" ;
isNio="${isNio:-no}" ;

skipHttpConnector="${skipHttpConnector:-no}" ;
skipTomcatJarScan="${skipTomcatJarScan:-no}" ;
servletThreads="${servletThreads:-50}";
servletConnections="${servletConnections:-50}";
servletAccept="${servletAccept:-0}";
servletTimeout="${servletTimeout:-10000}";

compress="${compress:-off}" ;
mimeType="${mimeType:-text/html,text/xml,text/plain,text/css,text/javascript,application/javascript}" ;
cookieDomain="${cookieDomain:-}" ;
cookiePath="${cookiePath:-}" ;
cookieName="${cookieName:-}" ;
serviceContext="${serviceContext:-$csapName}"
platformVersion="${platformVersion:-not-used}" ;

ajpSecret="${ajpSecret:-dummySecretYouShouldUpdateClusterDef}" ;

function tomcatEnvSetup() {
	print_with_head "Tomcat environment configuration"
	# runtime versions
	tom7=`ls -td $tomcatRuntimes/apache-tomcat-7* | head -1`
	tom8=`ls -td $tomcatRuntimes/apache-tomcat-8.0* | head -1`
	tom85=`ls -td $tomcatRuntimes/apache-tomcat-8.5* | head -1`
	tom9=`ls -td $tomcatRuntimes/apache-tomcat-9* | head -1`

	#set default to be tomcat 7
	TOMCAT_VERSION=$tom7 ; 
	SOURCE_SERVERXML=$tom7/custom/conf/_csapTemplate_server.xml
	
	
	chk=`echo $csapServer | grep -c tomcat8`
	if [  $chk != 0 ] ; then 
		TOMCAT_VERSION=$tom8; 
	 	SOURCE_SERVERXML=$tom8/custom/conf/_csapTemplate_server.xml 
	fi
	
		
	chk=`echo $csapServer | grep -c tomcat8-5`
	if [  $chk != 0 ] ; then 
		TOMCAT_VERSION=$tom85; 
	 	SOURCE_SERVERXML=$tom85/custom/conf/_csapTemplate_server.xml 
	fi
	
	chk=`echo $csapServer | grep -c tomcat9`
	if [  $chk != 0 ] ; then 
		TOMCAT_VERSION=$tom9; 
	 	SOURCE_SERVERXML=$tom9/custom/conf/_csapTemplate_server.xml 
	fi
	
	
	export CATALINA_HOME="$TOMCAT_VERSION"
	export CATALINA_BASE="$csapWorkingDir"
	export warDir=$CATALINA_BASE/webapps
	print_line "CATALINA_HOME: '$CATALINA_HOME' CATALINA_BASE: '$CATALINA_BASE' SOURCE_SERVERXML: '$SOURCE_SERVERXML'"
	
}


# Run only when dirs not exist
function configureTomcatWorkingDir() {
	
	print_with_head "Setting up the tomcat working directory CATALINA_BASE: '$CATALINA_BASE'"

	make_if_needed $CATALINA_BASE/conf
	make_if_needed $CATALINA_BASE/temp
	make_if_needed $CATALINA_BASE/webapps/ROOT
	
	make_if_needed $CATALINA_BASE/logs
	if [ -e $csapWorkingDir.logs ] ; then 
		print_line "Found existing Log folder: '$csapWorkingDir.logs', recovering to $CATALINA_BASE/logs"
		mv  $csapWorkingDir.logs $CATALINA_BASE/logs
	fi

	
	if [[ $csapParams == *DrawTomcat*  ]]; then
		
		print_line "Found -DrawTomcat, copying $CATALINA_HOME/conf to $CATALINA_BASE"
		\cp --recursive --verbose --force $CATALINA_HOME/conf/* $CATALINA_BASE/conf
		
	elif [[ -e $CATALINA_HOME/custom   ]]; then

		print_line "Found $CATALINA_HOME/custom, copying to $CATALINA_BASE"
		\cp --recursive --verbose --force $CATALINA_HOME/custom/* $CATALINA_BASE
		
	else
		print_line "Did not find '-DrawTomcat' or '$CATALINA_HOME/custom'"
	fi
		
	if [ -e $csapDefinitionResources/$csapServer ]; then
		print_line "Found '$csapDefinitionResources/$csapServer', copying to '$CATALINA_BASE'"
		\cp --recursive --verbose --force $csapDefinitionResources/$csapServer/* $CATALINA_BASE
			
	else
		print_line "Skipping custom setup: did not find '$csapDefinitionResources/$csapServer'"
		
	fi

	
	# Enable UI to override class loader order
	if [[ $csapParams = *DparentFirst* ]] ; then 
		print_line "forcing parent classLoader"
		sed -i "s/<Context>/<Context><Loader delegate=\"true\"\\/>/g" $CATALINA_BASE/conf/context.xml
	fi;
			
			
	if [[ $csapParams = *DtomcatManager* ]] ; then 
		# https://tomcat.apache.org/tomcat-9.0-doc/manager-howto.html#Configuring_Manager_Application_Access
		print_with_head "WARNING: Installing tomcat manager. Remove -DtomcatManager to disable."
		print_line "copying '$CATALINA_HOME/webapps' to '$CATALINA_BASE'"
		\cp --recursive --force $CATALINA_HOME/webapps/* $CATALINA_BASE/webapps
		print_line "removing remote access restriction"
		mv --verbose $CATALINA_BASE/webapps/manager/META-INF/context.xml $CATALINA_BASE/webapps/manager/META-INF/original-context.xml
		sed '/RemoteAddrValve/,+1 d' $CATALINA_BASE/webapps/manager/META-INF/original-context.xml > $CATALINA_BASE/webapps/manager/META-INF/context.xml
	else
		print_line "Skipping tomcat manager, add '-DtomcatManager' to enable"
	fi;
	
	# adding in redirect
	if [ -e $CATALINA_BASE/webapps/ROOT/index.jsp ] ; then
		print_line "Adding root redirect"
		sed -i "s/CsAgent/$csapName/g" $CATALINA_BASE/webapps/ROOT/index.jsp
	fi ;
		
}

function configureServerXml() {
	
	print_with_head "Configuring tomcat server.xml"
	
	# you can overwrite the generated one if you want, but not advised
	if [ ! -e "conf/server.xml" ] && [ $isHotDeploy != "1" ] ; then
		portPrefix=${csapHttpPort:0:3}
		modJkRoute=$csapName"_"$csapHttpPort`hostname`
		print_line "Copying $SOURCE_SERVERXML to $CATALINA_BASE"
		print_line "changing _SC_PORT_ to $portPrefix and _SC_ROUTE_ to $modJkRoute"
		
		\cp -f $SOURCE_SERVERXML $CATALINA_BASE/conf/server.xml
		sed -i "s/_SC_ROUTE_/$modJkRoute/g" $CATALINA_BASE/conf/server.xml
		sed -i "s/_SC_PORT_/$portPrefix/g" $CATALINA_BASE/conf/server.xml
		
		# optionall disable 
		if [[  $JAVA_OPTS == *noJmxFirewall*  ]]  ; then 
			print_line "Detected noJmxFirewall skip, deleting JmxRemoteLifecycleListener lines"
			sed -i "/JmxRemoteLifecycleListener/d" $CATALINA_BASE/conf/server.xml
			sed -i "/rmiRegistryPortPlatform/d" $CATALINA_BASE/conf/server.xml
		fi
		
		if [[  $JAVA_OPTS == *noWebSocket*  ]]  ; then 
			print_line "Detected noWebSocket skip, Adding skip"
			sed -i "/JmxRemoteLifecycleListener/d" $CATALINA_BASE/conf/catalina.properties
			sed -i "s/jstl.jar/jstl.jar,tomcat7-websocket.jar/g" $CATALINA_BASE/conf/catalina.properties
		fi
		
		# update compressions settings, default is off 
		sed -i "s/_SC_COMPRESS_/$compress/g" $CATALINA_BASE/conf/server.xml
		sed -i "s=_SC_MIME_=$mimeType=g" $CATALINA_BASE/conf/server.xml
		
		sed -i "s/_SC_THREADS_/$servletThreads/g" $CATALINA_BASE/conf/server.xml
		sed -i "s/_SC_ACCEPT_/$servletAccept/g" $CATALINA_BASE/conf/server.xml
		sed -i "s/_SC_MAX_/$servletConnections/g" $CATALINA_BASE/conf/server.xml
		sed -i "s/_SC_TIME_/$servletTimeout/g" $CATALINA_BASE/conf/server.xml
		
		sed -i "s/_SC_SECRET_/$ajpSecret/g" $CATALINA_BASE/conf/server.xml
		if [  $skipHttpConnector == "no" ] ; then
			print_line "Enabling http Connector in $CATALINA_BASE/conf/server.xml"
			sed -i "s/_SC_SKIP_HTTP1_/-->/g" $CATALINA_BASE/conf/server.xml
			sed -i "s/_SC_SKIP_HTTP2_/<!--/g" $CATALINA_BASE/conf/server.xml
			
		else 
			print_line "Disabling http Connector in $CATALINA_BASE/conf/server.xml"
			sed -i "s/_SC_SKIP_HTTP1_/ /g" $CATALINA_BASE/conf/server.xml
			sed -i "s/_SC_SKIP_HTTP2_/ /g" $CATALINA_BASE/conf/server.xml
			
		fi ;
		
		if [  $isSecure == "yes" ] ; then
			# http://www.unc.edu/~adamc/docs/tomcat/tc-accel.html
			# tomcat running behind SSL Accelerator will get confused. These settings
 
			print_line "Secure flag found in metadata, ajp connector in server.xml is being updated to support SSL acceleration"
			print_line "ref http://www.unc.edu/~adamc/docs/tomcat/tc-accel.html"
		 	ajp="redirectPort=\"443\" proxyPort=\"443\"  secure=\"true\" scheme=\"https\" SSLEnabled=\"false\""
			sed -i "s/_SC_SECURE_AJP/$ajp/g" $CATALINA_BASE/conf/server.xml
			
		 	direct="secure=\"true\" scheme=\"http\" SSLEnabled=\"false\""
			sed -i "s/_SC_SECURE_HTTP/$direct/g" $CATALINA_BASE/conf/server.xml
			
			# this forces connection to be secure
			\cp -f $TOMCAT_VERSION/custom/conf/_csapSecure_web.xml $CATALINA_BASE/conf/web.xml
		else
			print_line "AJP connection is not secure"
			sed -i "s/_SC_SECURE_AJP/ /g" $CATALINA_BASE/conf/server.xml
			sed -i "s/_SC_SECURE_HTTP/ /g" $CATALINA_BASE/conf/server.xml
		fi
		
		if [  $isNio == "yes" ] ; then
			print_line "AJP connection is using NIO"
			sed -i "s/org.apache.coyote.ajp.AjpProtoco/org.apache.coyote.ajp.AjpNioProtoco/g" $CATALINA_BASE/conf/server.xml
		else
			print_line "AJP connection is using BIO. Note that high volumes will benefit from NIO configuration"
			
		fi ;
		
		if [  $skipTomcatJarScan == "yes" ] ; then
			print_line "skipTomcatJarScan is in metadata, overwriting catalina.properties with $TOMCAT_VERSION/custom/conf/_csapJarSkip_catalina.properties"
			\cp -f $TOMCAT_VERSION/custom/conf/_csapJarSkip_catalina.properties  $CATALINA_BASE/conf/catalina.properties
			
		fi ;
		
		namePart=""
		if [ "$cookieName" != "" ] ; then 
			namePart="sessionCookieName=\"$cookieName\""
		fi;

		domainPart=""
		if [ "$cookieDomain" != "" ] ; then 
			domainPart="sessionCookieDomain=\"$cookieDomain\""
		fi;
		
		pathPart=""
		if [ "$cookiePath" != "" ] ; then 
			pathPart="sessionCookiePath=\"$cookiePath\""
		fi;
		
		if [ "$namePart" != "" ] || [ "$pathPart" != ""  ] || [ "$domainPart" != ""  ]  ; then
			print_line "Updating $CATALINA_BASE/conf/context.xml with cookie settings $pathPart , $domainPart, $namePart" ;
			sed -i "s/<Context/<Context $domainPart $pathPart $namePart /g" $CATALINA_BASE/conf/context.xml
		else
			print_line "Default cookie settings being used in $CATALINA_BASE/conf/context.xml"
		fi ;
		
		
		# Enable UI to override class loader order
		if [[ $JAVA_OPTS = *DtomcatReloadable* ]] ; then 
			print_line "Warning: tomcat is autoreloading context and may result in leaks"
			sed -i "s/<Context/<Context reloadable=\"true\" /g" $CATALINA_BASE/conf/context.xml
		else
			print_line "Correct: tomcat is NOT autoreloading context"
			sed -i "s/<Context/<Context reloadable=\"false\" /g" $CATALINA_BASE/conf/context.xml
		fi;
			
		
	else
		if [ $isHotDeploy != "1" ] ; then
			print_line "WARNING : use of server.xml not recommended, replace with tomcatVersion.txt copy from CsAgent"
			sleep 3
		fi ;
	fi ;
}

function getWarAndProperties() {
	# Deploy last deployed instance
	#if [ $isSkip == "0"  ] ; then
			
	print_with_head "Configuring war and properties: '$csapPackageFolder/$csapName.war'"
	
	\cp -p $csapPackageFolder/$csapName.war $CATALINA_BASE
	
	\cp -p $csapPackageFolder/$csapName.war.txt $CATALINA_BASE/release.txt
	\cp -p $csapPackageFolder/$csapName.war.txt $CATALINA_BASE
	
	if [ -e "$csapDefinitionResources/$csapName/resources" ]; then
		
		print_line Found Overide properties: $csapDefinitionResources/$csapName/resources, copying to  $CATALINA_BASE
		\cp -fr $csapDefinitionResources/$csapName/resources $CATALINA_BASE
	fi ;
	
	# csap passes as a string, so eval is needed
#	csapExternal=$(eval echo $csapExternalPropertyFolder)
#	if [[ "$csapExternal" != "" && -e "$csapExternal" ]]; then
#		
#		print_line Found csapExternalPropertyFolder variable, $csapExternal == copying to $CATALINA_BASE/resources
#
#		make_if_needed $CATALINA_BASE/resources
#		
#		#scm dirs will be copied - but they can be ignored by runtime
#		\cp -rf $csapExternal/* $CATALINA_BASE/resources
#			
#	else
#		
#		print_line "Did not find csapExternalPropertyFolder environment: $csapExternal"
#	fi

	

	
# override if you want
	export extractDir=$warDir/$csapName	
	
}

function make_if_needed() {
	
	local folderName=$1 ;
	local output=$(mkdir --parents --verbose $folderName 2>&1 | tr '\n' '  ') ;
	
	if [ -z "$output" ] ; then
		print_line "make_if_needed: folder exists: $folderName" ;
	else
		print_line "make_if_needed: $output" ;
	fi
	
}

function doFullServiceSetup() {
	getWarAndProperties
	
	print_with_head "doFullServiceSetup for CATALINA_BASE: '$CATALINA_BASE'" 
	
	make_if_needed $CATALINA_BASE/conf

	
	export extractDir=$warDir/$serviceContext


	#extractDir="$warDir/$serviceContext""##"`date +%s`
	# backwards compatible

	extractDir="$warDir/$serviceContext"
	
	#print_line "checking for $csapPackageFolder/$csapName.war.txt"
	if [ -e $csapPackageFolder/$csapName.war.txt ] && [[  $JAVA_OPTS != *noTomcatVersion*  ]] ; then
		print_line "tomcat extraction folder:  using version in '$csapPackageFolder/$csapName.war.txt'"
		extractDir="$warDir/$serviceContext""##"`grep -o '<version>.*<' $csapPackageFolder/$csapName.war.txt  | cut -d ">" -f 2 | cut -d "<" -f 1`
	else
		print_line "tomcat extraction folder:  skipping version, remove -DnoTomcatVersion to enable"
	fi;
	
	print_line  "extractDir set to $extractDir"

	if [ $isHotDeploy != "1" ] ; then
		print_line "removing previous $warDir/$serviceContext*"
		rm -rf $warDir/$serviceContext*
	fi ; 
    
    make_if_needed $extractDir
	
	print_line "extracting: $CATALINA_BASE/$csapName.war to '$extractDir'"
	/usr/bin/unzip -qq -o $CATALINA_BASE/$csapName.war -d $extractDir
	
	
	make_if_needed $CATALINA_BASE/temp ;
	
	
	# WARNING: warDist is getting hardcoded here.
	if [  -e "$CATALINA_BASE/resources" ]; then
		print_line "overwriting war resources with those from propertyOveride" 
		print_line "$CATALINA_BASE/resources to $extractDir/WEB-INF/classes"
		\cp -fr $CATALINA_BASE/resources/* $extractDir/WEB-INF/classes
		
	fi
	
	if [  -e $extractDir/WEB-INF/classes/common ]; then
		print_line "copying properties - $extractDir/WEB-INF/classes/common"
		\cp -fr $extractDir/WEB-INF/classes/common/* $extractDir/WEB-INF/classes
	fi
	
	#
	# Hook for sublifecycles
	if [[ $csapServiceLife == $csapLife* ]] ; then
		if [  -e $extractDir/WEB-INF/classes/$csapLife ]; then
			print_line "copying properties - $extractDir/WEB-INF/classes/$csapLife"
			\cp -fr $extractDir/WEB-INF/classes/$csapLife/* $extractDir/WEB-INF/classes
		fi
		
		# hook for multi vm partition property files
		if [  -e $extractDir/WEB-INF/classes/$csapLife$platformVersion ]; then
			print_line "copying override properties - $extractDir/WEB-INF/classes/$csapLife$platformVersion "
			\cp -fr $extractDir/WEB-INF/classes/$csapLife$platformVersion/* $extractDir/WEB-INF/classes
		fi
	fi ;
	

	
	if [  -e $extractDir/WEB-INF/classes/$csapServiceLife ]; then
		print_line "copying properties - $extractDir/WEB-INF/classes/$csapServiceLife"
		\cp -fr $extractDir/WEB-INF/classes/$csapServiceLife/* $extractDir/WEB-INF/classes
	fi
	
	
	if [  -e $extractDir/WEB-INF/classes/$csapServiceLife ]; then
		print_line "copying properties - $extractDir/WEB-INF/classes/$csapServiceLife"
		\cp -fr $extractDir/WEB-INF/classes/$csapServiceLife/* $extractDir/WEB-INF/classes
	fi
	
	if [ -e "$extractDir/WEB-INF/classes/tomcatServer.xml" ] ; then
		SOURCE_SERVERXML=$extractDir/WEB-INF/classes/tomcatServer.xml ;
		print_line "NOTE: Detected override $SOURCE_SERVERXML"
		print_line "in properties. Failure to use latest template can result in"
		print_line "unexpected behaviour. If problems occur, ensure you are packaging the latest template "
		print_line "from $CSAP_FOLDER/bin"

	fi ; 
	
	if [ -e "$extractDir/WEB-INF/classes/logRotate.config" ] ; then
		print_line "NOTE: Detected log rotation policy file"
		print_line "$extractDir/WEB-INF/classes/logRotate.config"
		print_line "incorrect syntax in  config files will prevent rotations from occuring."
		print_line "Logs are examined hourly: ensure rotations are occuring or your service will be shutdown"

		cp -vf $extractDir/WEB-INF/classes/logRotate.config $CATALINA_BASE/logs

		sed -i "s=_LOG_DIR_=$CATALINA_BASE/logs=g" $CATALINA_BASE/logs/logRotate.config

	fi ; 
	
	if [ -e "$extractDir/WEB-INF/simpleUsers.xml" ] && [ $isHotDeploy != "1"  ] ; then
		print_line "NOTE: Detected $extractDir/WEB-INF/simpleUsers.xml"
		cp -vf $extractDir/WEB-INF/simpleUsers.xml $CATALINA_BASE/conf/tomcat-users.xml
	fi ; 
	
	configureServerXml
	
}

function tomcatStart() {
	
	tomcatEnvSetup
	print_with_head "Invoking: tomcat start"
	
	# if [ ! -e "$CATALINA_BASE/webapps" ] || [ "$csapName" == "CsAgent" ]; then
	if  [ "$isSkip" != "1" ]  && [ $isHotDeploy != "1"  ]  ; then
		print_line "find $CATALINA_BASE/webapps, running configureTomcatWorkingDir"
		configureTomcatWorkingDir 
	else
		print_line "Skip service is enabled, tomcat files will not be updated"
	fi
	
	##
	##   This is only run once during initial deployment. Subsequent deployments will re-use configuration UNLESS
	##   Kill/Clean is invoked.
	##
	
	if  [ "$isSkip" != "1" ]  ; then
		doFullServiceSetup ;
	fi ;   
	
	if [ -e  $csapPackageDependencies ] ; then
		
		print_line "Found Secondary deployment files: $csapPackageDependencies, deploying"
		
			
		for file in $csapPackageDependencies/*; do
			plainFile=`basename $file`
			#tomcatName=${plainFile/-/##}
			#echo "$file" is being copied to $CATALINA_BASE/webapps/$tomcatName
			# \cp -p $file $CATALINA_BASE/webapps/$tomcatName
			
				# set uses the IFS var to split
			oldIFS=$IFS
			IFS="-"
			mvnNameArray=( $plainFile )
			IFS="$oldIFS"
			mavenArtName=${mvnNameArray[0]}
			versionAndSuffix=${mvnNameArray[1]}
			version=${versionAndSuffix/.war//}
			extractDir="$warDir/$mavenArtName""##"$version
			print_line "extracting: $file to $extractDir"
			 /usr/bin/unzip -qq -o $file -d $extractDir
			 
			 	if [  -e $extractDir/WEB-INF/classes/common ]; then
					print_line "copying properties - $extractDir/WEB-INF/classes/common"
					\cp -vfr $extractDir/WEB-INF/classes/common/* $extractDir/WEB-INF/classes
				fi
			 	if [  -e $extractDir/WEB-INF/classes/$csapLife ]; then
					print_line "copying properties - $extractDir/WEB-INF/classes/$csapLife"
					\cp -vfr $extractDir/WEB-INF/classes/$csapLife/* $extractDir/WEB-INF/classes
				fi
		done
	# \cp -p $csapPackageDependencies/*.war $CATALINA_BASE/webapps
	fi ;
	
	print_line "updated tomcat"
	if [ "$csapDockerTarget" == "true" ]  ; then
		print_with_head "Service configured for docker, start will be triggered via docker container apis" ;
		exit ;
	fi ;

	# tomcat requires JAVA_OPTS be set as an environment variable 
	export JAVA_OPTS="$JAVA_OPTS" ;		
	if [ $isHotDeploy != "1" ] ; then
	
		print_with_head "Invoking $CATALINA_HOME/bin/startup.sh"
		print_line "CATALINA_BASE: $CATALINA_BASE"
		
		cd $CATALINA_BASE
		$CATALINA_HOME/bin/startup.sh 2>&1
		servicePattern='.*java.*/processing/'$csapName'.*catalina.*'
		updateServiceOsPriority $servicePattern
		
	else
		print_line "Hot Deploy in progress. View logs to confirm startup"
	fi ;
		
}

function tomcatStop() {
	
	print_with_head "Invoking: '$CATALINA_HOME/bin/shutdown.sh'"
	tomcatEnvSetup
	$CATALINA_HOME/bin/shutdown.sh
	
}

	    
	      
	    #
	    #  Tomcat Integration
	    #
	    
	      
#	    -c | -context )
#	      print_if_debug  "csap-env.sh\t:" "-c context was triggered, Parameter: $2"  
#	      serviceContext="$2";
#	      shift 2
#	      ;;
#	
#	    -threads )
#	      print_if_debug  "csap-env.sh\t:" "-threads was specified,  Parameter: $2"   ;
#	      servletThreads="$2" ;
#	      shift 2
#	    ;;
#	    
#	    -secondary )
#	      print_if_debug  "csap-env.sh\t:" "-secondary was specified,  Parameter: $2"   ;
#	      secondary="$2" ;
#	      shift 2
#	    ;;
#	    
#	    
#	    -accept )
#	      print_if_debug  "csap-env.sh\t:" "-accept was specified,  Parameter: $2"   ;
#	      servletAccept="$2" ;
#	      shift 2
#	    ;;
#	    
#	    -timeOut )
#	      print_if_debug  "csap-env.sh\t:" "-timeOut was specified,  Parameter: $2"   ;
#	      servletTimeout="$2" ;
#	      shift 2
#	    ;;
#	    
#	    -maxConn )
#	      print_if_debug  "csap-env.sh\t:" "-maxConn was specified,  Parameter: $2"   ;
#	      servletConnections="$2" ;
#	      shift 2
#	    ;;
#	    
#	    -ajpSecret )
#	      print_if_debug  "csap-env.sh\t:" "-ajpSecret was specified"   ;
#	      ajpSecret="$2" ;
#	      shift 2
#	    ;;
#	    
#	    -compress )
#	      print_if_debug  "csap-env.sh\t:" "-compress was specified"   ;
#	      compress="$2" ;
#	      shift 2
#	    ;;
#	    
#	    -mimeType )
#	      print_if_debug  "csap-env.sh\t:" "-mimeType was specified"   ;
#	      mimeType="$2" ;
#	      shift 2
#	    ;;
#	    
#	    -cookieName )
#	      print_if_debug  "csap-env.sh\t:" "-cookieName was specified"   ;
#	      cookieName="$2" ;
#	      shift 2
#	    ;;
#	    
#	    -cookieDomain )
#	      print_if_debug  "csap-env.sh\t:" "-cookieDomain was specified"   ;
#	      cookieDomain="$2" ;
#	      shift 2
#	    ;;
#	    -cookiePath )
#	      print_if_debug  "csap-env.sh\t:" "-cookiePath was specified"   ;
#	      cookiePath="$2" ;
#	      shift 2
#	    ;;
#	    
#	    -skipHttpConnector )
#	      print_if_debug  "csap-env.sh\t:" "-skipHttpConnector was triggered "  
#	      skipHttpConnector="yes";
#	      shift 1
#	      ;;
#	      
#	      
#	    -secure )
#	      print_if_debug  "csap-env.sh\t:" "-secure was triggered "  
#	      isSecure="yes";
#	      shift 1
#	      ;;
#	    
#	    -nio )
#	      print_if_debug  "csap-env.sh\t:" "-nio was triggered "  
#	     isNio="yes";
#	      shift 1
#	      ;;
#	      
#	    -skipTomcatJarScan )
#	      print_if_debug  "csap-env.sh\t:" "-skipTomcatJarScan was triggered "  
#	      skipTomcatJarScan="yes";
#	      shift 1
#	      ;;
#	      
#	    -t | -hotDeploy )
#	      print_if_debug  "csap-env.sh\t:" "-t hot deployment was triggered"  
#	      hotDeploy="yes";
#	      shift 1
#	      ;;











