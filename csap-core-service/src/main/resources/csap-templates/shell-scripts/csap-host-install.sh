# csap host install, runs install on 1 or more hosts using a specified definition



#
#     To run:
#		- update configure function 
#		- Sample:  definitionProvider="default" ; hostsToInstall="csap-dev20" ; hostsRootPassword=xxxx ; 
# 

function configure() {

	definitionProvider="${definitionProvider:-default}";			# a running host, or default for a new application
	
	hostsToInstall="${hostsToInstall:-my-host-1 my-host-2}" ; 		# one or more hosts space delimited
	
	hostsRootPassword="${hostsRootPassword:-yourRootPassword}" ;
	
	runInForeground=false ;				# install output placed in /root/csap-install.txt
	isKillContainers=true ;				# if true all docker,kubernetes, and containers are reinstalled
	isCleanJournal=true ;				# cleaning up system logs makes troubleshooting much easier
	remoteUser="root" ;
	
	#
	# install using the csap-host.zip used to install current host
	#
	hostInstallerZip="http://${csapFqdn:-$(hostname --long)}:${agentPort:-8011}/api/agent/installer"
	
	installerFile="disabled" ;         # sample function: kubernetes_autoplay_singlenode
	
	
	# -skipOs : bypass repo checks, kernel parameters, and security limits
	# -ignorePreflight : bypass setup tests
	# -uninstall: remove csap
	extraOptions="" ;
	
	
	#
	# alternatly - use a artifactory instance available
	#
	# hostInstallerZip="http://my-artifactory:8081/artifactory/csap-snapshots/org/csap/csap-host/2-SNAPSHOT/csap-host-2-SNAPSHOT.zip"
	# hostInstallerZip="http://my-artifactory:8081/artifactory/csap-release/org/csap/csap-host/21.08/csap-host-21.08.zip"
	

}

#
#  Usually just edit the file. This is for demo only
#
function kubernetes_autoplay_singlenode() {
	
	
	local numHosts=$(echo "$hostsToInstall" | wc -w) ;
	if (( $numHosts > 1 )) ; then
		print_error "Aborting: this demo is for a single host. Configure a differnent autoplay file" ;
		exit 99;
	fi ;
	
	
 	installerFile="$CSAP_FOLDER/auto-plays/demo.yaml"
	
	print_section "kubernetes_autoplay_singlenode creating: $installerFile" ;
	
	cp --force --verbose $CSAP_FOLDER/auto-plays/all-in-one-auto-play.yaml $installerFile
 	
	replace_all_in_file "xxxHost" "$hostsToInstall" $installerFile
	
	myDomain=$(expr "$(hostname --long)" : '[^.][^.]*\.\(.*\)')
	replace_all_in_file "yyyDomain" "$myDomain" $installerFile
	
	print_command "$installerFile" "$(cat $installerFile)" ;
	
}

function verify_host_access() {

	print_separator "connection tests"

	if $(is_need_package sshpass) ; then
		run_using_root yum --assumeyes install sshpass openssh-clients;
	fi ;
	
	if $(is_need_package sshpass) ; then
		# ensure epel is enabled
		run_using_root 'yum search epel-release; yum info epel-release; yum --assumeyes install epel-release'
		run_using_root yum --assumeyes install sshpass openssh-clients;
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
	
	delay_with_message 10 "Installation resuming" ; 
	
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
	     "wget --no-verbose --content-disposition $hostInstallerZip"
	     'unzip  -j csap-host-*.zip csap-platform/packages/csap-package-linux.zip'
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





