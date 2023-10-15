#!/bin/bash

source $STAGING/bin/csap-shell-utilities.sh

#
#  1. CHANGE the timer on CSAP ui to avoid timeout
#  2. Update the variables below
#  3. Run using the test commands (default)
#  4. set isTest=false;
#  5. run install
#

isTest=true; 
remoteUser="root"
remotePassword="YOUR_PASS" ;

csapDefinitionHost="YOUR_PRIMARY_HOST.yourcompany.org"
csapClusterName2="/YOUR\ CAPABILITY\ NAME/base-os" ;

csapApiUrl="" ; #defaults to localhost, or: csapApiUrl="http://your-host:8011/CsAgent"

snapshot="http://my-server-01.yourcompany.org:8081/artifactory/csap-snapshots/org/csap/csap-host/2-SNAPSHOT/csap-host-2-SNAPSHOT.zip"
release="http://my-server-01.yourcompany.org:8081/artifactory/csap-release/org/csap/csap-host/2.0.4/csap-host-2.0.4.zip"

csapZipUrl=$snapshot
csapZipName=$(basename $csapZipUrl)

# or: remoteHosts="my-host-1 my-host-3 ..."
remoteHosts=$(csap.sh -lab $csapApiUrl -parseOutput -api model/hosts/$csapClusterName1 -script);

print_with_head "Hosts: '$remoteHosts'"

#
#  get the zip local - so we can do in place update of entire cluster
#

if [ -f definitionZip ] ; then
	\rm -rf definitionZip
fi ;

definitionUrl="http://$csapDefinitionHost:8011/CsAgent/os/definitionZip"
print_with_head "Getting definition: '$definitionUrl'"
wget $definitionUrl ;
copy_remote $remoteUser $remotePassword "$remoteHosts" definitionZip /root/application.zip



testCommands=( "hostname --short" ) # testCommands=( "nohup ls &> ls.out &")

# legacy: http://csap-dev01.yourcompany.org/csap/csap-host-latest.zip

installCommands=(
     'ls -l csap*.zip* *linux.zip installer 2>/dev/null'
     'rm --recursive --force --verbose csap*.zip* *linux.zip installer'
     'yum --assumeyes install wget unzip ; systemctl restart chronyd.service'
     "wget -nv $csapZipUrl"
     "unzip  -j $csapZipName staging/csap-packages/csap-package-linux.zip"
     'unzip -qq csap-package-linux.zip installer/*'
     "nohup installer/install.sh -noPrompt  -runCleanUp -deleteContainers \
    -installDisk default  \
    -installCsap default \
    -packageServer csap-dev01.yourcompany.org:8080 \
    -csapDefinition /root/application.zip &> csap-install.txt &"
   )
   
# "nohup ... &> csap-install.txt &"

if $isTest ; then
	
	run_remote $remoteUser $remotePassword "$remoteHosts" "${testCommands[@]}" ;
   	
else
	
	run_remote $remoteUser $remotePassword "$remoteHosts" "${installCommands[@]}";
	
fi


