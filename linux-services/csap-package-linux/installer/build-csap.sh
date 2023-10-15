#!/bin/bash


scriptDir=$(pwd)
scriptName=$(basename $0)


buildFunctions="$scriptDir/build-csap-functions.sh" ;

#echo "loading" "$buildFunctions"
source $buildFunctions ;

#
# Note: ssh credentials pushed to both source and target repositories
#
#
# Build options
#	- check out only (git clone)
#	- optional: publish to github
#	- optional: perform a build of all repos
#
releaseFolder=${releaseFolder:-/mnt/CSAP_DEV01_NFS/csap-web-server} ;
gitCheckoutFolder="/mnt/c/Users/peter.nightingale/csap-gits" ;
m2="/mnt/c/Users/peter.nightingale/.m2" ;


publishLocation="git@github.com:csap-platform" ;
publishTag="22.03";

#
# comment out for releases
#
#publishLocation="skip" ;
#publishTag="merge-$(date +"%h-%d-%I-%M-%S")" ;


#gitProjects=$( echo git@bitbucket.org:yourcompanyinc/oss-csap-event-services ) ;

gitProjects=$(echo \
	git@bitbucket.org:yourcompanyinc/yourcompany-integration-definition \
	git@bitbucket.org:yourcompanyinc/yourcompany-desktop-definition \
	git@bitbucket.org:yourcompanyinc/oss-csap-bin \
	git@bitbucket.org:yourcompanyinc/oss-csap-java \
	git@bitbucket.org:yourcompanyinc/oss-csap-build \
	git@bitbucket.org:yourcompanyinc/oss-csap-images \
	git@bitbucket.org:yourcompanyinc/oss-csap-installer \
	git@bitbucket.org:yourcompanyinc/oss-csap-event-services \
	git@bitbucket.org:yourcompanyinc/oss-csap-packages \
	git@bitbucket.org:yourcompanyinc/oss-csap-starter \
	git@bitbucket.org:yourcompanyinc/oss-csap-core )


#	git@bitbucket.org:yourcompanyinc/oss-csap-java \

#buildFolders="$gitCheckoutFolder/oss-sample-project";
buildFolders="skip";



#
##
###  load csap helper functions
## 
#
setupEnv


#
##
###  Start the process
## 
#

checkOutRepos "$gitProjects" $gitCheckoutFolder

publishToOtherProvider "$gitProjects" $gitCheckoutFolder $publishLocation $publishTag

performBuild "$buildFolders" "$m2" "-Dmaven.repo.local=$m2/repository clean package install" 
