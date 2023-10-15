#!/bin/bash 


#
#  install bash on windows: lxrun /install
#

#
#  Parameters:
#  	<host>  : default is -help, which will output setup steps. csap-01.root is vm testing
#			- NOTE: per help, ~/.ssh/config and ssh keys should be setup first.
#

# Example1: uninstall
# "csap-01.root" "-" "-noPrompt -ignorePreflight -uninstall"
#
#
# Example 2: default definition
# "csap-01.root" "-" "-noPrompt -skipOs -ignorePreflight -runCleanUp -deleteContainers -installDisk default -installCsap default -csapDefinition default"
#
#
# Example 3: default definition, all-in-one csapAutoPlay, skip kernel and host transfer file 
# "csap-01.root" "-" "-noPrompt -ignorePreflight -csapAutoPlay -runCleanUp -deleteContainers -installDisk default -installCsap default -csapDefinition default"
# 
#
# Example 4: default definition, yourtown os repos
# "csap-01.root" "-" "-noPrompt -ignorePreflight -overwriteOsRepos -runCleanUp -deleteContainers -installDisk default -installCsap default -csapDefinition default"
# 
#
# Example 5: default definition, yourtown os repos, all-in-one csapAutoPlay (yourtown ldap and kubernetes)
# "csap-01.root" "-" "-noPrompt -ignorePreflight -overwriteOsRepos -csapAutoPlay -runCleanUp -deleteContainers -installDisk default -installCsap default -csapDefinition default"
# 
#
# 
#
#
# Example 6: default definition, public-all-in-one csapAutoPlay, skip kernel and host transfer file 
# "csap-01.root" "-" "-noPrompt -ignorePreflight -csapAutoPlay -runCleanUp -deleteContainers -installDisk default -installCsap default -csapDefinition default"
# 
#
# EOL Example: subpathed definition 
# yourcompany starter: "csap-01.root" "-" "-noPrompt -ignorePreflight -installDisk default -installCsap default -runCleanUp -deleteContainers -csapDefinition 'http://csap-dev01.davis.yourcompany.lab/admin/os/definitionZip?path=yourcompanyStarter'"
#
#   -runCleanUp -deleteContainers will blow away docker and kubernetes
#
#  Configuration options:
#  	-skipKernel : bypasses OS patches and kernel settings
#	-csapDefinition : default | defaultMinimal | defaultAgent 
#   yourcompany:  "csap-01.root" "-" "-noPrompt -ignorePreflight -installDisk default -installCsap default -runCleanUp -deleteContainers -csapDefinition 'http://csap-dev01.davis.yourcompany.lab/admin/os/definitionZip?path=yourcompanyStarter'"
#            -packageServer csap-dev01.yourcompany.org
#   
defaultCsapInstall="-noPrompt -installDisk default -installCsap default -csapDefinition 'default'"

installHost=${1:--help} ;
installAws=${2:-} ;
installOptions=${3:-$defaultCsapInstall} ;


skipBaseOperations=false ;
if [[ "$installOptions" == *skipOs* ]] ; then
	skipBaseOperations=true ;
	skipBaseOperations=false ; # uncomment to force transfer of host.zip
fi ;

isDoBuild=false ; # enable to use localhost artifacts

if [[ $2 == "skipBuild" ]]; then isDoBuild=false;  fi ;

#scriptDir=`dirname $0`
scriptDir=$(pwd)
scriptName=`basename $0`
echo "Working Directory: '$(pwd)'"

if [ -e installer/csap-environment.sh ] ; then
	source installer/csap-environment.sh

elif [ -e ../environment/csap-environment.sh ] ; then

	cd ..
	scriptDir=$(pwd) ;
	
	echo "Desktop development using windows subsystem for linux: '$scriptDir'"
	ENV_FUNCTIONS=$scriptDir/environment/functions ;
	source $scriptDir/environment/csap-environment.sh ;
	
else
	echo "Desktop development"
	source $scriptDir/platform-bin/csap-environment.sh
fi

# change timer to 300 seconds or more
release="2.0.0";

includePackages="no" ; # set to yes to include dev lab artifacts
includeMavenRepo="no" ; # set to yes to include maven Repo
scpCopyHost="do-not-copy"




gitFolder="$HOME/git";
openSourceFolder="$HOME/opensource2";
buildDir="$HOME/localbuild"

function ensureToolsInstalled() {
	# gcc  gcc-c++ openssl097a nethogs iftop
	#local osPackages="tar zip unzip nmap-ncat dos2unix psmisc net-tools wget dos2unix sysstat lsof yum-utils bind-utils" ;
	local osPackages="wget" ;
	print_line "Verifying required packages are installed: $osPackages"
	
	for package in $osPackages ; do
		
		if is_need_package $package ; then
			yum -y  install $package
			print_line "\n\n"
		fi ;
		
	done
}

#ensureToolsInstalled ;

function getLatestInstaller() {

	mkdir --parents --verbose $openSourceFolder
	rm --verbose --recursive $openSourceFolder/*
	
	#wget http://my-server-01.yourcompany.org:8081/artifactory/csap-release/org/csap/csap-host/2.0.9/csap-host-2.0.9.zip
	wget -nv --no-cookies --no-check-certificate --directory-prefix $openSourceFolder\
		http://my-server-01.yourcompany.org:8081/artifactory/csap-snapshots/org/csap/csap-host/2-SNAPSHOT/csap-host-2-SNAPSHOT.zip
}


#getLatestInstaller ;
#exit;

function build_notes() {
	add_note "start"
	add_note "Notes:"
	
	add_note "# remote-install-sh <hostName with certificate trust to root>"
	add_note ""
	
	add_note "Configure ssl certs:"
	add_note "$note_indent optional: use of ~/.ssh/config enables aliasing and certs to be stored. eg:"
	add_note "\nHost csap-01.root\n\tHostName centos1\n\tUser root"
	add_note ""
	
	add_note "1. Configure local host ssl:"
	add_note " # ssh-keygen -t rsa"
	add_note " # chmod 700 .ssh ; chmod 600 ~/.ssh/id_rsa"
	add_note ""
	
	add_note "2. Configure remote host ssl:"
	add_note " # scp ~/.ssh/id_rsa.pub root@csap-01:"
	add_note " # ssh root@centos1"
	add_note " # mkdir .ssh ; chmod 700 .ssh ; cat id_rsa.pub >> ~/.ssh/authorized_keys ;chmod 600 ~/.ssh/authorized_keys"
	add_note " # verify: should NOT prompt for password:"
	add_note " # 	ssh root@centos1"
	add_note " # 	ssh csap-01.root"
	add_note " # Critical: ssh will pompt the first time: you MUST ssh using the alias to set: ssh csap-01.root"
	add_note ""
	
	add_note "For more information on ssl configuration: https://wiki.centos.org/HowTos/Network/SecuringSSH"
	
	add_note ""
	
	add_note "windows users: use of choco package management and install of git includes bash as part of install"
	add_note "refer to: https://chocolatey.org/"
	
	add_note "end"
}

if [ "$installHost" == "-help" ] ; then 
	build_notes
	print_line "$add_note_contents"
	exit ;
fi ;

print_separator "remote install parameters"
print_two_columns "installHost" "'$installHost'"
print_two_columns "installAws" "'$installAws'"
print_two_columns "skipBaseOperations" "$skipBaseOperations"
print_two_columns "csapInstall" "$installOptions"


print_separator "script hardcoded settings"
print_two_columns "scriptDir" "$scriptDir"
print_two_columns "isDoBuild" "$isDoBuild"
print_two_columns "~/.ssh/config" "$(echo $(grep -A 3 $installHost ~/.ssh/config))"


function copy_remote() {

	local sourceItems="$1" ;
	local destinationFolder="$2" ;

	for sourceItem in $sourceItems ; do
		print_info "copy $installHost:$destinationFolder"  "$sourceItem"
		scp -r $sourceItem $installHost:$destinationFolder
	done ;
}

function run_remote() {
	
	print_separator "$installHost: $*"
	ssh $installHost $*
	if [ $? != 0 ] ; then 
		print_with_head "Error running remote, has ssh credential been installed: scp .ssh/id_rsa.pub root@<yourhost>:" 
		print_line "Refer to https://wiki.centos.org/HowTos/Network/SecuringSSH, resuming in 5 seconds"
		sleep 5;  
	fi ;
	
}


function set_up_remote_host_for_install() {

	print_separator "cleaning up previous installs" ;
	
	run_remote "ls -l"
	run_remote 'rm -rf /root/installer /root/index.html* /opt/java/*'
	if ! $skipBaseOperations ; then
		run_remote 'rm -rf /root/csap*.zip'
	fi ;
	
	print_separator "Copying latest installer files"
	copy_remote "$scriptDir/installer"
	copy_remote "$scriptDir/environment/*" installer
	
	
	if [[ "$installOptions" == *csapAutoPlay* ]] ; then
	
		print_separator "csap-auto-play" ;
		local sourceFile="$scriptDir/auto-plays/all-in-one-auto-play.yaml";
		
#		if [[ "$installOptions" == *overwriteOsRepos* ]] ; then
#			sourceFile="$scriptDir/auto-plays/yourtown/all-in-one-auto-play.yaml";
#		fi ;
		
		local installerFile="$HOME/remote-play.yaml"
		
		cp --force --verbose $sourceFile $installerFile
 	
#		replace_all_in_file "xxxHost" "csap-01" $installerFile

	
		local appName="all-in-one-remote"
		replace_all_in_file '$appName' "$appName" $installerFile
		replace_all_in_file '$appId' $(to_lower_hyphen $appName) $installerFile
		replace_all_in_file '$apiToken' $(to_lower_hyphen $appName) $installerFile

		replace_all_in_file '$managerHost' "csap-01" $installerFile
		replace_all_in_file '$hostDomain' "csap.org" $installerFile
		
		copy_remote $installerFile "csap-auto-play.yaml" ;
		
	fi ;
	
	
	if ! $skipBaseOperations ; then
		csapZip="$openSourceFolder/csap*.zip" ;
		if $isDoBuild ; then
			csapZip="$HOME/temp/*.zip";
		fi ;
		
		copy_remote $csapZip ;
	fi ;
	
	run_remote ls -l
}


function root_user_setup() {
	
	
	checkHostName=$(run_remote hostname) ;
	if [[ $checkHostName == *.amazonaws.com ]] ; then
		print_with_head "Found amazonaws.com , root setup is already completed" ;
		return ;
	fi ;
	
	print_separator  "host setup"
	
	stripRoot="-root"
	
	originalAlias="$installHost" ;
	installHost=${installHost%$stripRoot} ;
	#if [ "$installHost" != "$originalAlias" ] ; then
	
	if [ "$installAws" == "installAws" ] ; then
		print_with_head "Setting up root user using alias: $installHost derived from $originalAlias" ;
		run_remote sudo cp .ssh/authorized_keys /root/.ssh
		run_remote sudo chown root /root/.ssh/authorized_keys 
		installHost="$originalAlias" ;
		
		# update redhat config
		run_remote sed -i "/preserve_hostname/d" /etc/cloud/cloud.cfg
		run_remote 'echo -e "\npreserve_hostname: true\n" >> /etc/cloud/cloud.cfg'
		
		# update hostname
		run_remote 'external_host=$(curl -s http://169.254.169.254/latest/meta-data/public-hostname) ; hostnamectl set-hostname --static $external_host'
		
		# reboot
		
		run_remote hostname
		
	
	else
		print_line "Skipping aws certificate setup" ;
	fi ;
	
	print_with_head "Installing unzip and wget, the remaining packages and kernel configuration will be installed by csap installer"
	#run_remote yum --assumeyes install wget unzip 
	run_remote systemctl restart chronyd.service
}


function remote_csap_install() {
	
	run_remote installer/install.sh $installOptions ;
	# -skipKernel
}


#exit ;

function add_local_packages() {
	
	sourceFolder="$gitFolder/$1" ; destination="$STAGING/packages/$2"
	
	[ ! -e $sourceFolder ] && print_with_head "skipping: $sourceFolder..." && return ; # delete if exists
	
	print_with_head "overwriting $destination  with contents from $sourceFolder"
	
	ls -l $destination
	\cp -vf $sourceFolder $destination
	
	sed -i "" 's=.*version.*=<version>6.desktop</version>=' "$destination.txt"
	
	ls -l $destination
	
}


function build_csap_using_local_packages() {

	
	print_with_head "Building $release in  $buildDir - port to latest"
	exit 99
	
	if [ ! -e "$openSourceFolder" ] ; then
		print_with_head "Did not find openSourceFolder: $openSourceFolder. This needs to contain a base csap.zip" 
		exit
	fi ;
	
	
	[ -e $buildDir ] && print_with_head "removing existing $buildDir..." && rm -r $buildDir ; # delete if exists
	
	export STAGING="$buildDir/csap-platform" ;
	
	print_with_head "Extracting contents of base release $openSourceFolder/csap-host-*.zip to $buildDir ..."
	unzip -qq -o "$openSourceFolder/csap-host-*.zip" -d "$buildDir"
	
	print_with_head "Replacing $STAGING/bin with contents from $gitFolder/packages/csap-package-linux/platform-bin"
	cp -f $gitFolder/packages/csap-package-linux/platform-bin/* $STAGING/bin
	
	#print_with_head "Replacing $STAGING/mavenRepo with contents from $HOME/.m2"
	#cp -rvf $HOME/.m2/* $STAGING/mavenRepo
	
	add_local_packages packages/csap-package-java/target/*.zip Java.zip
	add_local_packages packages/csap-package-linux/target/*.zip linux.zip
	add_local_packages csap-core/csap-core-service/target/*.jar CsAgent.jar
	#exit;
	
	$scriptDir/build-csap.sh $release $includePackages $includeMavenRepo $scpCopyHost
	
	
	
	#$STAGING/bin/mkcsap.sh $release $includePackages $includeMavenRepo $scpCopyHost
	
	#includePackages="yes" ; # set to yes to include dev lab artifacts
	#includeMavenRepo="yes" ; # set to yes to include maven Repo
	#release="$release-full"
	
	#print_with_head Building $release , rember to use ui on csaptools to sync release file to other vm
	#$STAGING/bin/mkcsap.sh $release $includePackages $includeMavenRepo $scpCopyHost

}

if [ $release != "updateThis" ] ; then
	
	if $isDoBuild ; then
		build_csap_using_local_packages ;
	fi ;
	#exit ;
	
	if ! $skipBaseOperations; then
		root_user_setup ;
	fi ;
	
	set_up_remote_host_for_install 
	
	remote_csap_install
	
else
	print_with_head update release variable and timer
fi

