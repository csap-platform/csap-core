#!/bin/sh
#
#
#

scriptDir=`dirname $0`
source $STAGING/bin/csap-shell-utilities.sh


print_line "Running $0 $*"
print_line "add param \"includePackages\" to build a standalone. By default not done due to size"

if [ $# -eq 0 ] ; then
	print_line "param 1 is release and is required param 2 is optional: includePackages : use to indicate if binaries are to be retrieved"
	print_line "Exiting Script. Run again with params"
	exit
fi

relNumber="$1"
includePackages="$2"
includeMavenRepo="$3"
targetHost="$4"
releaseFolder=$5

csapPackageFolder="$STAGING/csap-packages"

releaseZipFile="csap-host-$relNumber.zip"
csapClient="csap-client-$relNumber.zip"

print_with_head "Starting build..."

#echo == removing old stuff from repo

buildDir="$HOME/temp";

print_line "Build is being performed in $buildDir"

[ -e $buildDir ] && print_line "removing existing $buildDir..." && rm -r $buildDir ; # delete if exists



mkdir -p $buildDir/staging
cd $buildDir

mkdir staging/build
mkdir staging/csap-packages


function addBasePackages() {
	
	basePackages="$@" ;
	print_with_head "basePackages: $basePackages"
	for package in $basePackages ; do
		print_two_columns "Including" "$csapPackageFolder/$package"
		cp -rp $csapPackageFolder/$package* staging/csap-packages
	done ;
}

# note jdk and linkux also wildcards to match jdk.secondary
addBasePackages csap-package-linux csap-package-java csap-package-tomcat CsAgent.jar events-service.jar csap-verify-service.jar httpd docker kubelet csap-demo-tomcat


if  [ "$includeMavenRepo" == "yes" ] ; then
	print_with_head "removing $STAGING/mavenRepo files to slim down distribution"
	\rm -rf $STAGING/mavenRepo/*
fi ;




function doPackageBuild() {
	print_with_head "includePackages requested: '$includePackages'. Running maven dependencies to transfer into maven repo"
	
	mavenBuildCommand=""
	function generateMavenCommand() {
		itemToParse=$1
		
		# do not wipe out history for non source deployments
		needClean="" 
		# set uses the IFS var to split
		oldIFS=$IFS
		IFS=":"
		mvnNameArray=( $itemToParse )
		IFS="$oldIFS"
		mavenGroupName=${mvnNameArray[0]}
		mavenArtName=${mvnNameArray[1]}
		mavenArtVersion=${mvnNameArray[2]}
		mavenArtPackage=${mvnNameArray[3]}
		
		# filter packages
		mavenBuildCommand="skipped" ;
		if [[ "$mavenArtName" =~ ^($includePackages.*)$ ]]; then
			mavenWarPath=$(echo $mavenGroupName|sed 's/\./\//g') ;
			mavenWarPath="$STAGING/mavenRepo/$mavenWarPath/$mavenArtName/$mavenArtVersion"
		
			#echo  == mavenWarPath is $mavenWarPath
			# Note the short form has bugs with snapshot versions. here is the long form for get
			mavenBuildCommand="-B org.apache.maven.plugins:maven-dependency-plugin:3.0.1:get  -Dtransitive=false -DremoteRepositories=1myrepo::default::file:///$STAGING/mavenRepo,http://maven.yourcompany.com/artifactory/cstg-smartservices-group "
			mavenBuildCommand="$mavenBuildCommand -DgroupId=$mavenGroupName -DartifactId=$mavenArtName -Dversion=$mavenArtVersion -Dpackaging=$mavenArtPackage"
		fi
		#echo "== mavenBuildCommand: $mavenBuildCommand"
		
	}
	
	#developmentPackages=`csap.sh -lab http://localhost:8911/admin -api model/mavenArtifacts -script` ;
	developmentPackages=`csap.sh -lab http://localhost:8011/CsAgent -api model/mavenArtifacts -script` ;
	print_line "Development Packages: $developmentPackages"
	for package in $developmentPackages ; do
		# echo == found package: $package
		
		generateMavenCommand $package
		print_two_columns "$mavenBuildCommand" "$package"
		if [[ "$mavenBuildCommand" != "skipped" ]] ; then
			mvn -s $STAGING/conf/propertyOverride/settings.xml $mavenBuildCommand ;
		fi;
	done	
}

if  [ "$includePackages" != "no" ] ; then
	doPackageBuild
fi ;
#set -o verbose #echo on

function mySync() {
	# rsync --recursive --perms $1 $2
	print_line "rsync not supported on windows, using cp -r"
	cp -rf $1 $2
}

print_with_head "copying $STAGING/bin"
mySync $STAGING/bin $buildDir/staging

print_line "copying $STAGING/apache-maven"
mySync $STAGING/apache-maven* $buildDir/staging

if  [ "$includeMavenRepo" == "yes" ] ; then
	print_line "Including maven repo"
	mySync $STAGING/mavenRepo  $buildDir/staging
	
	print_line 'Removing maven _remote* files from repo - otherwise they are ignored'
	find $buildDir/staging/mavenRepo/ -name _remote* -exec rm -f {} \;
else 
	print_line "Skipping maven repo"
	mkdir -p  $buildDir/staging/mavenRepo
fi;

print_line Build item sizes
du -sh $buildDir/staging/*

print_line "Building `pwd` $releaseZipFile ..."
zip -qr $releaseZipFile staging

print_line "Completed, size: `ls -lh $releaseZipFile |  awk '{print $5}'`"

if [ "$targetHost" != "do-not-copy" ] ; then
	
	print_line "Transferring $releaseZipFile $targetHost:web/csap size `ls -lh $releaseZipFile |  awk '{print $5}'`"
	scp -o BatchMode=yes -o StrictHostKeyChecking=no $releaseZipFile $targetHost:web/csap
	print_line "Go to $targetHost to sync upload to other hosts"
else
	
	print_line "Copying to $releaseFolder/csap"
	cp $releaseZipFile $releaseFolder/csap
	
fi ;


function build_csap_web() {

	folderName=$(basename $releaseFolder) ;

	print_with_head "Building: $releaseFolder"
	
	if [ -f $releaseFolder/web.zip ] ; then 
		print_line "Removing previous $releaseFolder/$folderName.zip" ;
		rm -f $releaseFolder/web.zip
	fi ;
	
	print_line "switching to $releaseFolder/.. so zip is relative"
	cd $releaseFolder/..
	zip -qr $releaseFolder/$folderName.zip $folderName/tomcat $folderName/httpd $folderName/mongo
	
	print_with_head "Completed '$releaseFolder/$folderName.zip', size: $(ls -lh $releaseFolder/$folderName.zip |  awk '{print $5}')"
	
	cd $buildDir
}


function build_csap_client() {
	
	cd $buildDir
	print_with_head "Building in: $buildDir/csapClient"
	
	if [ -e $buildDir/csapClient ] ; then 
		print_line "Removing previous $buildDir/csapClient" ;
		\rm -rf $buildDir/csapClient
	fi ;
	
	mkdir -p $HOME/csapClient ;
	cd $HOME/csapClient ;
		
	print_with_head "Adding csap.sh and _sample to client folder"
	cp $STAGING/bin/csap.sh .
	cp $STAGING/bin/csap-cli-samples.sh .
	
	print_with_head "unzipping '$STAGING/csap-packages/CsAgent.jar' to lib"
	unzip -qj -o $STAGING/csap-packages/CsAgent.jar -d lib  'BOOT-INF/lib/*'
	
	print_line "creating zip in $(pwd)"
	zip -qr csapClient.zip *
	
	if [ -f $releaseFolder/csap/$csapClient ] ; then 
		print_line "Removing previous $releaseFolder/csap/$csapClient" ;
		rm -f $releaseFolder/csap/$csapClient
	fi ;
	
	mv csapClient.zip $releaseFolder/csap/$csapClient
	print_with_head "Completed '$releaseFolder/csap/$csapClient', size: $(ls -lh $releaseFolder/csap/$csapClient |  awk '{print $5}')"
}

if [ -e $releaseFolder ] ; then
	build_csap_web
	build_csap_client ;
fi ;



