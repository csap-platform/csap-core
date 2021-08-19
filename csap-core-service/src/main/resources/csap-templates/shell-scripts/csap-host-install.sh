# csap host install, runs install on 1 or more hosts using a specified definition



#
#     To run:
#		- update configure function 
# 

function configure() {

	definitionProvider="${definitionProvider:-default}";			# a running host, or default for a new application
	
	hostsToInstall="${hostsToInstall:-my-host-1 my-host-2}" ; 		# one or more hosts space delimited
	
	hostsRootPassword="${hostsRootPassword:-yourRootPassword}" ;
	
	installerFile="disabled" ;										# $CSAP_FOLDER/auto-plays/***REMOVED***/kubernetes-cluster-auto-play.yaml
	
	# Sample settings
	#definitionProvider="default" ; hostsToInstall="csap-dev20" ; hostsRootPassword=xxxx ; installerFile="$csapDefinitionFolder/scripts/***REMOVED***-auto-play.yaml"
	
	runInForeground=false ;				# install output placed in /root/csap-install.txt
	isKillContainers=true ;				# if true all docker,kubernetes, and containers are reinstalled
	isCleanJournal=true ;				# cleaning up system logs makes troubleshooting much easier
	remoteUser="root" ;
	
	# -skipOs : bypass repo checks, kernel parameters, and security limits
	# -ignorePreflight : bypass setup tests
	# -overwriteOsRepos : epel should be configured; set both following variables csapBaseRepo, csapEpelRepo
	#   eg csapEpelRepo="http://media.***REMOVED***/media/third_party/linux/CentOS/Sensus-epel-7.repo""
	extraOptions="" ;   # extraOptions="-ignorePreflight"
	
	#csapZipUrl="http://***REMOVED***.***REMOVED***:8081/artifactory/csap-snapshots/org/csap/csap-host/2-SNAPSHOT/csap-host-2-SNAPSHOT.zip"
	csapZipUrl="http://***REMOVED***.***REMOVED***:8081/artifactory/csap-release/org/csap/csap-host/21.08/csap-host-21.08.zip"

}

function verify_host_access() {

	print_separator "connection tests"

	if $(is_need_package sshpass) ; then
		run_using_root yum --assumeyes install sshpass ;
	fi ;
	
	exit_if_not_installed sshpass ;
	
	local failureCount=0;
	
	for targetHost in $hostsToInstall; do
	
		hostOutput=$(sshpass -p $hostsRootPassword ssh -o StrictHostKeyChecking=no $remoteUser@$targetHost ls -ld /etc 2>&1) ;
		connection_return_code="$?" ;
		
		print_if_debug $targetHost "$hostOutput"
		
		
		if (( $connection_return_code != 0 )) ; then
			print_two_columns "$targetHost" "FAILED"
			failureCount=$(( $podCount + 1)) ;
		else
			print_two_columns "$targetHost" "PASSED"
		fi ;
		
	done ;
	
	if (( $failureCount > 0 )) ; then
	
		print_error "Aborting installation - correct connection errors"
		exit $install_return_code ;
		
	fi

}

function remote_installer () {

	configure
	
	verify_host_access
	
	csapZipName=$(basename $csapZipUrl)
	
	cleanupParameters="" ;
	if $isKillContainers ; then
		cleanupParameters="-deleteContainers"
	fi ;
	
	if [[ $hostsToInstall == *$(hostname --short)* ]] ; then 
		print_with_head "Aborting installation: this script must not be run on a host being installed" ; 
	fi ;
	
	
	local targetDefinition="default" ;
	
	if [[ $definitionProvider != default* ]] ; then
	
		#
		#  get the zip local - so we can do in place update of entire cluster
		#
		
		print_separator "Copying application definition to hosts"
	
		targetDefinition="/root/application.zip" ;
	
		if [ -f definitionZip ] ; then
			print_line ""
			rm --force definitionZip
		fi ;
	
		definitionUrl="http://$definitionProvider:8011/os/definitionZip"
		
		if [[ $definitionProvider =~ http.* ]] ; then
			definitionUrl=$definitionProvider ;
		fi ;
		
		print_two_columns "source" "$definitionUrl copied to $(pwd)"
		print_two_columns "local" "$(hostname --short):$(pwd)"
		
		local definitionOutput=$(wget --no-verbose --output-document definitionZip $definitionUrl 2>&1) ;
		print_two_columns "result" "$definitionOutput"
		
		definition_return_code="$?" ;
		if (( $definition_return_code != 0 )) ; then
			print_error "Failed to retrieve definition - verify definitionProvider: $definitionProvider"
			exit $definition_return_code ;
		fi ;
		copy_remote $remoteUser $hostsRootPassword "$hostsToInstall" definitionZip /root/application.zip
		
	fi ;
	
	
	print_separator "Starting installation ..."
	sleep 5 ;
	
	
	testCommands=( "hostname --short" ) # testCommands=( "nohup ls &> ls.out &")
	
	local cleanJournalCommand="echo skipping journal cleanup" ;
	if $isCleanJournal ; then
		cleanJournalCommand="journalctl --flush; journalctl --rotate; journalctl --vacuum-time=1s;" ;
	fi ;
	
	
	local backgroundCommand="&> csap-install.txt &";
	if $runInForeground ; then
		backgroundCommand="" ;
	fi ;
	
	local autoPlayParam=""
	if [[ "$installerFile" != "disabled"  ]] ; then 
	
		if ! test -f $installerFile ; then
			print_error "Specifed installer file not found: '$installerFile'" ;
			exit 99 ;
		fi ;
	
		print_separator "Copying $installerFile to remote hosts"
		autoPlayParam=" -csapAutoPlay "
		copy_remote $remoteUser $hostsRootPassword "$hostsToInstall" $installerFile /root/csap-auto-play.yaml

	fi ;
	
	local installerOsCommands="yum --assumeyes install wget unzip ; systemctl restart chronyd.service" ;
	if [[ "$extraOptions" == *skipOs* ]] ; then
		installerOsCommands="echo assuming wget and unzip installed" ;
	fi ;
	
	installCommands=(
	     'echo $(hostname --long) ;'
	     "$cleanJournalCommand"
	     'rm --recursive --force --verbose csap*.zip* *linux.zip installer'
	     "$installerOsCommands"
	     "wget -nv $csapZipUrl"
	     "unzip  -j $csapZipName csap-platform/packages/csap-package-linux.zip"
	     'unzip -qq csap-package-linux.zip installer/*'
	     "nohup installer/install.sh -noPrompt $autoPlayParam -runCleanUp $cleanupParameters \
	    -installDisk default  \
	    -installCsap default $extraOptions \
	    -csapDefinition $targetDefinition $backgroundCommand"
	   )
	   
	run_remote $remoteUser $hostsRootPassword "$hostsToInstall" "${installCommands[@]}";
	
	
	
	print_separator "Installation Complete" ;
	print_line "agent dashboard will be available in 60-90 seconds";

}


remote_installer





