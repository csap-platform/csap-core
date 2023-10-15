#!/bin/bash
#
#
# 
scriptDir=$(dirname $0)
scriptName=$(basename $0)


linuxPackage="csap-package-linux" ;
if [[ ( "$csapName" ==  "$linuxPackage" ) 
		&& ( "$scriptDir" != "/tmp" ) ]]; then
	#
	# self-update: spawning in background
	#
	rebuildFile="/tmp/$scriptName"
	
	if test -e $rebuildFile ; then rm --verbose --force $rebuildFile ; fi
	
	# copying $0 to $rebuildFile to allow script upgrades
	echo "Detected linx package, spawning from /tmp"  ;
 	\cp --force --verbose $0 $rebuildFile 2>&1; 
	chmod 755 $rebuildFile ; 
	
	$rebuildFile $@;
	exit ;
fi ;
	
echo "...."

# Defaults
isNoLogout="0" ;
needClean="-x clean" # we clean out folders on source build only

SCM_BRANCH="HEAD"

mavenBuildCommand="-Dmaven.test.skip=true clean package" ;
SCM_USER="" ;
hosts="" ;

# getopts is great parser, ref: http://wiki.bash-hackers.org/howto/getopts_tutorial
# alternative impl to getopt: isNoLogout=`expr match "$* " '.*-noLogout' != 0`
mavenArtifactPath="" ;

source $CSAP_FOLDER/bin/csap-environment.sh

startTime=$(date +%s%N)

function wait_for_deployment_on_secondary_hosts {
	# deployCompleteFile="$csapPackageFolder/$csapName.$primaryHost";
	print_with_head "Waiting for deployment sync complete file from primary: '$waitForPrimaryFile'"
	# 
	original=$(stat -c %y $waitForPrimaryFile 2>&1);
	while [ ! -f $waitForPrimaryFile ]
	do
		sleep 5 ;
		numSeconds="$((($(date +%s%N) - $startTime)/1000000000))"
		echo "$numSeconds seconds"
		
	done
	
	print_line "deployment complete: '$(cat $waitForPrimaryFile)'"
	
	exit;
}
if [[ "$waitForPrimaryFile" != "none" && "$waitForPrimaryFile" != "" ]] ; then 	
	wait_for_deployment_on_secondary_hosts ;
fi ;

print_if_debug Running $0 : dir is $scriptDir
print_if_debug param count: $#
print_if_debug params: $@

if [ "$SCM_USER" == "" ] ; then
	print_with_head "usage: refer to confluence"
	print_line "exiting"
	exit ;
fi ;


print_if_debug ==  SCM_USER is "$SCM_USER"
mkdir --parents --verbose $CSAP_FOLDER/build


if [ "$csapServer" == "" ] ; then
	print_with_head "Error: missing variable: csapServer"
	exit ;
fi ;

print_if_debug == CHecking for  mavenArtifactPath
mavenWarDeploy=$(echo $mavenBuildCommand | grep -c :war)
mavenZipDeploy=$(echo $mavenBuildCommand | grep -c :zip)
mavenJarDeploy=$(echo $mavenBuildCommand | grep -c :jar)

mavenArtVersion=""

function export_artifact_variables() {
	
	# do not wipe out history for non source deployments
	needClean="" 
	
	local itemToParse=$1
	# build the bash string array
	oldIFS=$IFS
	IFS=":"
	mvnNameArray=( $itemToParse )
	IFS="$oldIFS"
	
	mavenGroupName=${mvnNameArray[0]}
	mavenArtName=${mvnNameArray[1]}
	mavenArtVersion=${mvnNameArray[2]}
	export csapBuildVersion=$mavenArtVersion
	mavenArtPackage=${mvnNameArray[3]}
	
	mavenArtifactPath=$(echo $mavenGroupName|sed 's/\./\//g') ;
	mavenArtifactPath="$csapMavenRepo/$mavenArtifactPath/$mavenArtName/$mavenArtVersion"
}

function buildMavenDependencyCommand() {

	local buildItem=$1 ;
	
	#print_if_debug  == mavenArtifactPath is $mavenArtifactPath
	local dependencyCommand="org.apache.maven.plugins:maven-dependency-plugin:3.1.1:get -DremoteRepositories=1_repo::::file:///$csapMavenRepo,$svcRepo "

	# Note the short form had bugs with snapshot versions, but should now be fixed
	dependencyCommand="$dependencyCommand -Dtransitive=false -Dartifact=$buildItem"
	#dependencyCommand="$dependencyCommand -Dtransitive=false -DgroupId=$mavenGroupName -DartifactId=$mavenArtName -Dversion=$mavenArtVersion -Dpackaging=$mavenArtPackage"
	
	#print_if_debug == mavenBuildCommand: $mavenBuildCommand
	echo $dependencyCommand
}

csapBuildVersion="" ;
csapBuildFull=$mavenBuildCommand
if [  $mavenWarDeploy != 0 ] || [  $mavenZipDeploy != 0 ] || [  $mavenJarDeploy != 0 ] ; then

	mavenBuildCommand=$(buildMavenDependencyCommand $csapBuildFull);
	export_artifact_variables $csapBuildFull;
	print_section "Deployment Artifact Requested: '$csapBuildFull'" ;
	print_if_debug "artifact" "$csapBuildFull" ;
	print_if_debug "location" "$mavenArtifactPath"
	print_if_debug "command" "$mavenBuildCommand"
	
fi ;


#
# SCM integration always uses the following folder for source
#
export SOURCE_CODE_LOCATION="$CSAP_FOLDER/build/$csapName"_"$csapHttpPort$buildSubDir"

print_if_debug "Current directory is $(pwd)"


#Assume a jee war is being built - then override if other is detected
deployItem=$csapName.war
buildItem=target/$deployItem
suffixTestEval="suffixMatch=target/*.war"

earFolder=./$csapName"Ear"
if [ -e $earFolder ]; then
	print_with_head "Found jeeFolder: '$earFolder'"
	deployItem=$csapName"Ear.ear" 
	buildItem=$csapName"Ear/target/"$deployItem
	suffixTestEval="suffixMatch=$csapName\"Ear/target/$csapName\"*.ear"
fi ;

if [ "$csapServer" == "csap-api" ] ; then
	print_if_debug ==
	print_if_debug == Found a csap-api, using zip deploy
	print_if_debug ==
	deployItem=$csapName.zip
	buildItem=target/$deployItem
	suffixTestEval="suffixMatch=target/*.zip"
fi ;

if [ "$csapServer" == "SpringBoot" ] ; then
	print_if_debug ==
	print_if_debug == SpringBoot jar deploy 
	print_if_debug ==
	deployItem=$csapName.jar
	buildItem=target/$deployItem
	suffixTestEval="suffixMatch=target/*.jar"
fi ;

print_if_debug =====================================================================================
print_if_debug deployItem is $deployItem, buildItem is $buildItem, suffixTestEval is $suffixTestEval
print_if_debug =====================================================================================

print_if_debug ====================================================================
print_if_debug Switching back to build dir $SOURCE_CODE_LOCATION
print_if_debug ====================================================================


if [ -e $SOURCE_CODE_LOCATION ] ; then
	cd $SOURCE_CODE_LOCATION
fi ;

function run_the_build() {
	
	print_separator "Starting build in '$(pwd)'"
	
	local isJavaBuildOnly=${javaBuildOnly:-false} ;
	
	if $isJavaBuildOnly ; then 
		print_line "javaBuildOnly is true " ;
		mavenBuildCommand="$mavenBuildCommand install" ; 
	fi ;
	
	local mBuild="$mavenBuildCommand"
		
	if [[ "$mavenBuildCommand" == *deploy* ]] ; then 
		if [ "$csapServer" == "csap-api" ] ||  [ "$csapServer" == "SpringBoot" ] ; then  
			print_if_debug "Stripping off maven deploy param if it is specified, maven deploy will run separately after csapApi deploy"
			mBuild=$(echo $mavenBuildCommand | sed -e "s/deploy/  /g")
		fi
	fi ;
	
	print_if_debug "Build command: $mBuild"
	
	csap_mvn $mBuild
	
	if $isJavaBuildOnly ; then 	
	
		if [ -e "csap-starter-parent" ] ; then
			cd csap-starter-parent ;
			print_line "running parent build" ;
			csap_mvn $mBuild
		fi ;
		
		print_line "javaBuildOnly is true; exiting" ;
		exit 95 ;
	fi ;
}

run_the_build ;
	

function verify_artifact_build() {
	
	print_separator "Verifying artifact build"
	
	if [  "$mavenArtifactPath" != "" ] ; then 
		buildItem="$mavenArtifactPath/$mavenArtName-$mavenArtVersion.war"
		if [  $mavenZipDeploy != 0 ] ; then 
			buildItem="$mavenArtifactPath/$mavenArtName-$mavenArtVersion.zip"
		fi ;
		if [  $mavenJarDeploy != 0 ] ; then 
			buildItem="$mavenArtifactPath/$mavenArtName-$mavenArtVersion.jar"
		fi ;
		
		print_if_debug "== buildItem set to $buildItem" ;
		# echo " $SCM_USER $mavenArtVersion" >| $buildItem.txt ;
		
	else
	
		# echo "Host Build Only: $SCM_USER $2" >| $buildItem.txt
		print_if_debug checking for versioned service
		

		eval $suffixTestEval
		
		print_if_debug buildItem: $buildItem suffixMatch: $suffixMatch
		numMatches=$(ls $suffixMatch | wc -w)
		
		if [ $numMatches != 1 ] ; then
			print_line "\n\nWARNING: csap-deploy.sh requires exactly a single matching artifact to be build, but found: $numMatches"
			print_line "suffixMatch: '$suffixMatch', location: '$(pwd)'"
			print_line "If your maven files is doing dependency copy or other commands that put matching artifacts"
			print_line "into target folder, put them into a subfolder."
			
			
			print_line "Upating suffixmatch to use the file with the largest match" ;
			suffixMatch=$(ls -S $suffixMatch | head -1);
			#print_line "exiting"
			#exit 94; 
		fi ; 

		
		if [ $buildItem != $suffixMatch ]; then
			print_if_debug copying $suffixMatch to $buildItem for deployment
			\cp -f $suffixMatch $buildItem
		fi
	fi ;

}

verify_artifact_build


function post_build() {

	print_if_debug "copying  $buildItem $csapPackageFolder" ;

	
	
	#
	#  Note that csap stores the artifacts using the process name, versus the original artifact name
	# This establishes the relationship between process and specific deployment version.
	#
	
	local serviceDeploymentPath=$csapPackageFolder/$deployItem ;
	local deploymentNotesFile="$serviceDeploymentPath.txt" ;
	
	if test -f "$buildItem"  ; then 
	
		if test -f "$deploymentNotesFile"  ; then  rm --verbose $serviceDeploymentPath.txt ; fi
		print_line "Copying to csap package folder: " $(cp --force --verbose $buildItem $serviceDeploymentPath) ;
		
	else
		print_error "Unable to locate deployment artifact: '$buildItem', current dir: $(pwd)" ;
		exit 99 ;
	fi
	
	
	# echo "$SCM_USER $SCM_BRANCH" > $serviceDeploymentPath.txt
	if [ -f "$serviceDeploymentPath.txt" ] ; then
		print_if_debug "removing previous '$serviceDeploymentPath.txt'";
		\rm --force $serviceDeploymentPath.txt
	fi
	
	#print_line "Creating artifact notes: '$serviceDeploymentPath.txt'" ;
	
	append_file "Deployment Notes" $deploymentNotesFile false
	
	print_if_debug == Getting version info
	
	# Critical Note version string is parsed during deployments to determine extract dir for tomcat
	deployTime=$(date +"%b %d %H:%M")
	if [ "$mavenArtVersion" != "" ] ; then 
		append_line "Maven Deploy of $mavenArtName"
		append_line "<version>$mavenArtVersion</version>"
		append_line "Maven Deploy by $SCM_USER at $deployTime using $mavenArtName"
	else 
		
		if [ -e "pom.xml" ]; then
			append_line "$(grep \<groupId pom.xml | head -1)"
			append_line "$(grep \<artifactId pom.xml | head -1)"
			local pomVersion=$(grep \<version pom.xml | head -1)
			if [[ "$pomVersion" == *{version}* ]] ; then
				append_line "<version>0.0-none</version>" ;
			else 
				append_line "$pomVersion"
			fi
			
		fi
		append_line "Source build by $SCM_USER at $deployTime  on $SCM_BRANCH"
	fi ;

	if  [ "$secondary" != "" ]  ; then
		print_line "found secondary deployment artifacts, creating: '$csapPackageFolder/$csapName.secondary'"

		IFS=","
		\rm -rf $csapPackageFolder/$csapName.secondary
		mkdir $csapPackageFolder/$csapName.secondary
		for artifactId in $secondary ; do
			IFS=" "
			print_line "Running secondary installer on '$artifactId'"
			append_line  "<secondary>$artifactId\</secondary>"
			
			generateMavenCommand $artifactId
			csap_mvn $mavenBuildCommand
			buildItem="$mavenArtifactPath/$mavenArtName-$mavenArtVersion.war"
			
			print_line "copying '$buildItem' to '$csapPackageFolder/$csapName.secondary'"
			cp $buildItem  $csapPackageFolder/$csapName.secondary
			IFS=","
			 
		done;
		IFS=" "
		print_line "completed secondary artifact installation"
	fi ;

	
	#
	# services need to rename their artifact to $csapName in order for installs to work
	#
	if [ "$csapServer" == "SpringBoot" ] && [ "$mavenArtVersion" == "" ] ; then
		print_if_debug ==
		print_if_debug == Found a SpringBoot... Running  commands
		print_if_debug ==
		
		source csap-integration-springboot.sh
		deployBoot
		
		# getting to the correct folder so that remaining commands can be executed.
		cd $SOURCE_CODE_LOCATION
		
	
		# CS-AP console will do the clean package automatically, but not the deploy to allow custom packaging.
		if [[ "$mavenBuildCommand" == *deploy* ]] ; then
	        # An explicity deploy is done here to finalize
			print_section "csap-deploy.sh() maven deployment"
			
			csap_mvn -Dmaven.test.skip=true deploy
			
			if [ $? != "0" ] ; then
				echo
				echo
				echo =========== CSSP Abort ===========================
				echo __ERROR: Maven build exited with none 0 return code
				echo == Build errors need to be fixed
				exit 99 ;
			fi ;
		fi; 
		
		
	fi
	#
	# services need to rename their artifact to $csapName in order for installs to work
	#
	if [ "$csapServer" == "csap-api" ] && [ "$mavenArtVersion" == "" ] ; then
		print_if_debug ==
		print_if_debug == Found a csap-api... Running pre csap-api commands
		print_if_debug ==
		
		rm -rf $CSAP_FOLDER/temp
		mkdir $CSAP_FOLDER/temp
		print_if_debug == /usr/bin/unzip $csapPackageFolder/$csapName.zip -d $CSAP_FOLDER/temp
		/usr/bin/unzip -uq $csapPackageFolder/$csapName.zip -d $CSAP_FOLDER/temp
		print_if_debug == find $CSAP_FOLDER/temp/scripts
		
		if [ -e "$CSAP_FOLDER/temp/scripts" ] ; then
			
			ensure_files_are_unix $CSAP_FOLDER/temp/scripts

		fi ;
		
		print_if_debug == loading custom $CSAP_FOLDER/temp/scripts/consoleCommands.sh
		#source $CSAP_FOLDER/temp/scripts/consoleCommands.sh
		savedWorking="$csapWorkingDir"
		csapWorkingDir="$CSAP_FOLDER/temp" ;
		source csap-integration-api.sh
		csapWorkingDir=$savedWorking
		
		
		print_if_debug "Switch to temp folder so csap-apis can do builds in a temp folder"
		cd $CSAP_FOLDER/temp
		
		print_if_debug "Now invoking provided api_package_build"

		if $( is_function_available api_package_build ) ; then

			api_package_build	
				
		elif $( is_function_available buildAdditionalPackages) ; then 

			buildAdditionalPackages
			
		else 
			
			print_with_head "Warning - did not find api_package_build"
			
		fi
		
		
		
		print_if_debug "csap-api api_package_build completed, cd to $SOURCE_CODE_LOCATION"
		
		# getting to the correct folder so that remaining commands can be executed.
		cd $SOURCE_CODE_LOCATION
		
	
		# CS-AP console will do the clean package automatically, but not the deploy to allow custom packaging.
		if [[ "$mavenBuildCommand" == *deploy* ]] ; then
	        # An explicity deploy is done here to finalize
			print_section "csap-deploy.sh() maven deployment"
			
			csap_mvn -Dmaven.test.skip=true deploy
			
			if [ $? != "0" ] ; then
				print_error "__ERROR: Maven build exited with none 0 return code"
				echo == Build errors need to be fixed
				exit 99 ;
			fi ;
		fi; 
	
	fi ;
}

if  [ "$csapPackageFolder" != "" ]  ; then
	post_build ;
fi ;

if [ "$csapServer" == "csap-api" ]  ; then  
	# set -x
	\rm --recursive --force $CSAP_FOLDER/temp
	mkdir --parents --verbose $CSAP_FOLDER/temp
	
	print_if_debug == /usr/bin/unzip $csapPackageFolder/$csapName.zip -d $CSAP_FOLDER/temp
	/usr/bin/unzip -uq $csapPackageFolder/$csapName.zip -d $CSAP_FOLDER/temp
	
	
	print_if_debug "checking for package scripts: $CSAP_FOLDER/temp/scripts"
	if test -d $CSAP_FOLDER/temp/scripts ; then
		ensure_files_are_unix $CSAP_FOLDER/temp/scripts ;
	fi ;
	
	print_if_debug == loading custom $CSAP_FOLDER/temp/scripts/consoleCommands.sh
	#source $CSAP_FOLDER/temp/scripts/consoleCommands.sh
	savedWorking="$csapWorkingDir"
	csapWorkingDir="$CSAP_FOLDER/temp" ;
	source csap-integration-api.sh
	csapWorkingDir=$savedWorking
		
	print_if_debug "Switch to temp folder so csap-apis can do builds in a temp folder"
	cd $CSAP_FOLDER/temp
	
	print_if_debug ==
	print_if_debug == Now invoking provided api_package_get
	print_if_debug ==
	
	#print_line getPackages found:  `type -t getPackages`
	if $(is_function_available api_package_get) ; then 
		
		api_package_get
		
	elif $(is_function_available getAdditionalBinaryPackages) ; then 
		
		getAdditionalBinaryPackages
		
	else 
		
		print_line Did Not find api_package_get interface
		
	fi
fi

print_if_debug "pwd is $(pwd)"
print_if_debug "Checking for $SOURCE_CODE_LOCATION/buildCommand.sh"

if [ -e "$SOURCE_CODE_LOCATION/buildCommand.sh" ] ; then 
	print_line "buildCommand found"
	cat $SOURCE_CODE_LOCATION/buildCommand.sh;
	ensure_files_are_unix $SOURCE_CODE_LOCATION
	
	chmod 755 $SOURCE_CODE_LOCATION/buildCommand.sh ;
	$SOURCE_CODE_LOCATION/buildCommand.sh
	
	print_line "Assuming $SOURCE_CODE_LOCATION/buildCommand.sh did everything needed - exiting" ;
	exit ;
fi; 

cd $CSAP_FOLDER

print_section "Finished Build:  $(ls -l $csapPackageFolder/$deployItem)"

# The following must be the final line to trigger start to occur in ServiceController
print_line "BUILD__SUCCESS"