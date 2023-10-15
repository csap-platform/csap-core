#!/bin/bash

#
# file system
#

function restore_filesystem_acls() {

	print_with_head "Stripping ACLs from $(pwd) auto-added when using csap file browser. Ref https://www.computerhope.com/unix/usetfacl.htm"
	setfacl --remove-all --recursive $(pwd)
	
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

function test_ciphers() {

	local SERVER=${1:-$(hostname --long):8082}
	
	print_section "testing ciphers on $SERVER using $(openssl version)" ;
	
	local DELAY=0.1
	local ciphers=$(openssl ciphers 'ALL:eNULL' | sed -e 's/:/ /g')
	
	local supportCount=0 ;
	
	for cipher in ${ciphers[@]} ; do
	
		echo -n "    $(printf '%-40s' "$cipher")"
		result=$(echo -n | openssl s_client -cipher "$cipher" -connect $SERVER 2>&1)
		if [[ "$result" =~ ":error:" ]] ; then
		
		  error=$(echo -n $result | cut -d':' -f6)
		  echo NO \($error\)
		  
		else
		
			if [[ "$result" =~ "Cipher is ${cipher}" || "$result" =~ "Cipher    :" ]] ; then
				echo YES
				supportCount=$(( supportCount + 1 )) ;
			else
				echo UNKNOWN RESPONSE
				echo $result
			fi
		
		fi
		
		sleep $DELAY
		
	done

  	print_two_columns "total supported"  "$supportCount"
}


function wait_for_terminated() {
	
	local processName=${1} ;
	
	local seconds=${2:-10} ;
	local processUser=${3:-csap};
	local message=${4:-continuing in};
	local dots;
	local iteration;
	local pidValue;
	
	#print_separator "Waiting for $processName to not be running"
	
	for (( iteration=$seconds; iteration > 0; iteration--)) ; do 
	
		pidValue=$(ps -u $processUser -f| grep $processName  | grep -v -e grep -e $0 | awk '{ print $2 }' | tr '\n' ' ')
		message="Pid(s): '$pidValue', " ;
		dots=$(printf "%0.s-" $( seq 1 1 $iteration ));
		
		
			
		if [ "$pidValue" != "" ] ; then
			print_two_columns "found process" "$message $(printf "%3s" $iteration) seconds  $dots"  ; 
		else
			print_two_columns "no process running" "$processName"  ; 
			break ;
		fi ;
	
		sleep 1; 
	done
	
	print_line "" ;
	
}


function make_keystore() {
	
	local domain=${1:-yourcompany.org}
	local cred=${2:-csap1234}
	
	#
	#
	#
	
	keytool -genkeypair -noprompt \
		-alias csap \
		-dname "CN=$domain, OU=ID, O=csap, L=csap, S=platform, C=US" \
		-storetype PKCS12 \
		-keystore csap.p12 \
		-keyalg RSA \
		-keysize 2048 \
		-storepass $cred \
		-keypass $cred \
		-validity 3650
		
#	keytool -import -keystore csap.p12 -alias csap -file csap.pem -trustcacerts
}



#
# Installer
#
csapProcessingFolder=${csapProcessingFolder:-/opt/csap};
processesThatMightBeRunning="/opt/csap/java docker containerd /usr/bin/conmon podman kubelet httpd mpstat $csapProcessingFolder" ;

function clean_up_process_count() {

	local doPrint=${1:-true} ;
	
	if $doPrint ; then
		print_line2 "\n\nclean_up_process_count:"
		print_two_columns2 "Process Pattern" "count" ;
	fi ;
	totalMatches=0 ;
	for processName in $processesThatMightBeRunning ; do
		
		matchCount=$(ps -ef | grep -v grep | grep $processName | wc -l) ;
		totalMatches=$(( $totalMatches + $matchCount )) ;
		# print to stderr so we can leverage return value
		if $doPrint ; then
			print_two_columns2 $processName $matchCount ;
		fi ;
	done ;	
	
	if $doPrint ; then
		>&2 print_two_columns "Total" "$totalMatches" ;
		>&2 print_line "\n"
	fi ;
	sleep 1 ; #allow for stderr to be flushed
	
	echo $totalMatches
}

function run_preflight() {

	local isIgnorePreflight=${1:-false}
	local label=${2:-csap preflight}

	print_section "$label"

	local networkRc=$(run_preflight_network; echo $?) ;
	local osVersionRc=$(run_preflight_osVersion; echo $?) ;
	local filesystemReadabeRc=$(are_file_systems_readable; echo $?) ;
	local filesystemRc=$(run_preflight_filesystem; echo $?) ;
	local processRc=$(run_preflight_processes; echo $?) ;
	
	
	if [[ $label == *uninstall* ]] ; then
		return 0 ;
	fi ;
	
	
	if (( $networkRc > 0 )) \
		|| (( $osVersionRc > 0 )) \
		|| (( $osVersionRc > 0 )) \
		|| (( $processRc > 0 )) \
		|| (( $filesystemRc > 0 )) ; then
		
		print_with_head "One or more systems failed $label" ;
		
		if $isIgnorePreflight ; then
			print_two_columns "$label" "-ignorePreflight is set"
		else
			print_two_columns "$label" "failed, exiting. To ignore, add -ignorePreflight"
			exit 90 ;
		fi ;
	fi ;
	
	sleep 1 ; # allow stderr to flush
	
}

function run_preflight_processes() {
	local processMatches=$(clean_up_process_count false) ;
	
	local returnCode=0 ;
	
	if (( $processMatches > 0 )) ; then
		print_preflight false "process" "found $processMatches csap processes";
		local numWithPrintsEnabled=$(clean_up_process_count true);
		returnCode=92 ;
	fi
	
	
	local processCount=$(ps -ef | grep -v "\[" | wc -l) ;
	local maxLimit=55
	if (( $processCount > $maxLimit )) ; then
		print_preflight false "process" "found $processCount processes, maximum is $maxLimit ";
		returnCode=94 ;
	else 
		print_preflight true "process" "found $processCount processes, maximum is $maxLimit ";
	fi
	
	print_preflight true "process" "no csap processes found"
	return $returnCode ;

}

function print_preflight() { 

	local result=$1 ; shift 1 ;
	local category=$1 ; shift 1 ;
	local details="$*"
	
	if [ "$result" == "true" ] ; then result="Passed" ; else  result="Failed" ; fi

	>&2 printf "%20s   [%s]   %-s\n" $category $result "$details"; 
}

function run_preflight_network() {

	local networkOutput=$(ip a | grep enp0s9) ;
	
	if (( $(ip a | grep ens192: | wc -l) > 0 )) ; then
		print_preflight true "network" "detected ens192"
		
	elif (( $(ip a | grep enp0s3: | wc -l) > 0 )) ; then
		print_preflight true "network" "detected enp0s3"
		
	elif (( $(ip a | grep eth0: | wc -l) > 0 )) ; then
		print_preflight true "network" "detected eth0"
		
	else
		print_preflight false "network" "unexpected interface. Run: ip a, and ensure kubernetes calico_ip_method is set with interface=<name>"
		return 91 ;
	fi
	
	return 0 ;
}

function run_preflight_osVersion() {
	local releaseInfo=$(cat /etc/redhat-release 2>&1) ;
	local releaseInfoWords=(${releaseInfo})
	local releaseVersion="not-detected";
	
	local returnCode=0;
	
	if (( $(echo $releaseInfo | grep CentOS | wc -l) > 0 )) ; then
		releaseVersion="${releaseInfoWords[3]}" ;
		print_preflight true "distribution" "discovered CentOS"
		
	elif (( $(echo $releaseInfo | grep "Red" | wc -l) > 0 )) ; then
		releaseVersion="${releaseInfoWords[6]}" ;
		print_preflight true "distribution" "discovered Redhat"
		
	elif (( $(echo $releaseInfo | grep "Rocky" | wc -l) > 0 )) ; then
		releaseVersion="${releaseInfoWords[3]}" ;
		print_preflight true "distribution" "discovered Rocky"

	else
		print_preflight false "distribution" "unexpect os distribtion: '$releaseInfo'. Recommended is CentOs 7.6"
		returnCode=92 ;
	fi
	
	if [[ "$releaseVersion" == 7.* ]] || [[ "$releaseVersion" == 8.* ]] ; then
		print_preflight true "version" "discovered $releaseVersion"
	else
		print_preflight false "version" "unexpected os version: '$releaseVersion'. Expected 7.*"
		returnCode=92 ;
	fi ;
	
	local packageCount=$(rpm -qa | wc -l) ;
	local maxLimit=600
	if (( $packageCount > $maxLimit )) ; then
		print_preflight false "packages" "found $packageCount packages, maximum is $maxLimit. Recommended: CentOS Minimal ";
		returnCode=94 ;
	else 
		print_preflight true "packages" "found $packageCount packages, maximum is $maxLimit ";
	fi
	
	return $returnCode ;
}

function run_preflight_filesystem() {

	local fileSystemInfo=$(timeout 5s df --block-size=G --print-type | sed 's/  */ /g' |  awk '{print $3 " " $7}') ;
		
	local allPassed=true ;
	
	verify_filesystem "/run" "5" "$fileSystemInfo" ;
	if (( $? != 0 )) ; then allPassed=false; fi ;
		
	verify_filesystem "/var/lib/docker" "50" "$fileSystemInfo" ;
	if (( $? != 0 )) ; then allPassed=false; fi ;
	
	verify_filesystem "/var/lib/kubelet" "25" "$fileSystemInfo" ;
	if (( $? != 0 )) ; then allPassed=false; fi ;
	
	verify_filesystem "/opt" "20" "$fileSystemInfo" ;
	if (( $? != 0 )) ; then allPassed=false; fi ;
	
	if ! $allPassed ; then
		return 93 ;
	fi

	return 0 ;
}

function verify_filesystem() {

	local mountPoint="$1";
	local minimumSize="$2";
	local fullInfo="$3";
	
	local filesystem=$(echo -e "$fullInfo" | grep --word-regexp "$mountPoint$" ) ;
	
	if [ "$filesystem" != "" ] ; then
		local fsWords=(${filesystem}) ;
		local size=${fsWords[0]::-1} ;
		local name=${fsWords[1]} ;
		if (( $size < $minimumSize )) ; then
			print_preflight false "filesystem" "$name size: '$size' is less then $minimumSize" ;
			return 94 ;
		else
			print_preflight true "filesystem" " verified $filesystem, size: $size" ;
		fi ; 
	
	else
		print_preflight false "filesystem" "missing mountpoint '$mountPoint'" ;
		return 95 ;
	fi ;
	
	return 0 ;
}

function are_file_systems_readable() {

	local dfResponseCheck=$(timeout 2s df --print-type --portability --human-readable | wc -l);
	if (( $dfResponseCheck == 0 )) ; then
		print_preflight false "mounts" "unable to list mounted filesystems" ;
		return 95 ;
	fi ;
	
	print_preflight true "mounts" "found: $dfResponseCheck filesystems" ;
	return 0 ;
	
}

function hard_umount_all() {

	local procMounts="/proc/mounts" ;

	print_separator "hard_umount_all() examining $procMounts"
	
	local podMounts=$(cat $procMounts | grep pod | awk '{print $2}');
	
	for podMount in $podMounts; do
		print_two_columns "podMount" "attempting umount of $podMount"
		print_command "output" "$(umount $podMount 2>&1)"
		
		# deleted pods end with \040(deleted), so removing the last 13 characters 
		local podLess8=${podMount::-13} ;
		print_two_columns "podMount" "attempting umount of $podLess8"
		print_command "output" "$(umount $podLess8 2>&1)"
	done ;
	
	
	
	local mntMounts=$(cat $procMounts | grep /mnt | awk '{print $2}');
	
	for mntMount in $mntMounts; do
		print_preflight "mntMount" "attempting umount of $mntMount"
		print_command "output" "$(umount -l $mntMount 2>&1)"
	done ;
}


#
# Packaging
#

# ref https://stackoverflow.com/questions/592620/how-can-i-check-if-a-program-exists-from-a-bash-script
function exit_if_not_installed() { 
	
	local command="${1:-missing-arg}"
	
	if ! hash $command &> /dev/null ; then 
		print_with_head "error: '$command' not found, install and try again.";
		exit; 
	fi
}

function is_command_available() { 
	
	local command="${1:-missing-arg}"
	
	if hash $command &> /dev/null ; then 
		true ;
	else  
		false ;
	fi ;   
}

function is_process_running() { 
	
	command="$1"
	
	if (( $(ps -ef | grep -v grep | grep $command | wc -l )  > 0 ));  then 
		true ;
	else  
		false ;
	fi ;   
}

function is_function_available() { 
	
	functionName="$1"
	
	if [ -n "$(type -t $functionName)" ] && [ "$(type -t $functionName)" == "function" ];  then 
		true ;
	else  
		false ;
	fi ;   
}

function is_need_package() { 
	
	! is_package_installed $1
	
}

function install_if_needed() {

	local packageName=${1} ;

	if $(is_need_package $packageName) ; then
		run_using_root yum --assumeyes install $packageName
		local returnCode=$? ;
		if (( $returnCode != 0 )) ;  then
			print_error "Warning: failed to install jq"
		fi
	fi ;
	

}

#
#  Common pitfall: bash local declaration sweep return codes: https://google.github.io/styleguide/shellguide.html#s7.6-use-local-variables
#
function exit_on_failure() {
	local returnCode=${1:-999} ; 
	print_if_debug "returnCode: '$returnCode'"
	local message=${2:-no-reason-specified} ; 
	
	
	if (( "$returnCode" == 0 )) && [ "$returnCode" != 0  ] ;  then
		print_error "Error: invalid return code: '$returnCode'. Reason: $message" ;
		exit $returnCode ;
	fi ;
	
	if (( "$returnCode" != 0 )) ;  then
		print_error "Error: return code: '$returnCode'. Reason: $message" ;
		exit $returnCode ;
	fi ;
}

function is_package_installed () { 
	
	rpm -q $1 2>&1 >/dev/null
	
}

function is_need_command() { 
	
	! is_command_installed $1 ;
	
}

function is_command_installed() { 
	
	verify=`which $1 2>/dev/null`; 
	
	if [ "$verify" != "" ] ; then 
		#echo true
		true ;
		
	else  
		#echo false
		false ; 
		
	fi;   
	
}

function ensure_files_are_unix() {
	
	updatePath=$1
	
	if [ -f "$updatePath" ] ; then
		updatePath="$1/*" ;
	fi ;
	
	if $( is_command_installed dos2unix ) ; then
		print_line "Found scripts in package, running dos2unix"
		find $updatePath -name "*.*" -exec dos2unix --quiet -n '{}' '{}' \;
		
	else
	
		print_line "Warning: did not find  dos2unix. Ensure files are linux line endings"
		
	fi ;
	
}


#
# file functions
#

function does_file_contain_word() {
	
	local returnCode=0;
	
	local targetFile="$1";
	local targetWord="$2";
	
	if ! grep --quiet --word-regexp "$targetWord" "$targetFile" ; then returnCode=99 ; fi

	
	return $returnCode ;
}

function build_auto_play_file() {
	
	local targetFolder=${1:-$(pwd)};
	
	append_file "# generated " "csap-auto-play.yaml"
	
}

function append_yaml_comment() {
	local comment=${1:-} ;
	append_line "# $comment" ;
}

function append_yaml() {
	local numIndents=${1:-} ;
	local line=${2:-} ;
	
	local spaces='' ;
	if (( $numIndents > 0 )) ; then
		spaces=$(printf '  %.0s' {1..$numIndents}) ;
	fi ;
	
	append_line "$spaces$line" ;
}


function append_line() {
	append_file "$*"
}

lastTargetFile="no-file-specified-yet"
lastVerbose=true ;

function append_file() {

	local source="$1" ;
	local targetFile="${2:-$lastTargetFile}" ;
	local verbose="${3:-$lastVerbose}" ;
	
	if [ "$targetFile" == "" ] || [ "$targetFile" == "no-file-specified-yet" ] ; then
		print_error "Invalid target file '$targetFile' "
		return 99;
	fi ;
	
	if [ "$verbose" == "" ] ; then verbose=true ; fi ;
	lastTargetFile="$targetFile" ;
	lastVerbose=$verbose ;
	
	if ! test -f $targetFile ; then
		print_if_verbose $verbose "append_file" "Note: specified targetFile '$targetFile', does not exist, creating" ;
	fi ;
	
	if test -f "$source" ; then
	
		print_if_verbose $verbose "append_file()" "file '$source' to file: '$targetFile'" ; 
		cat $source >> $targetFile ;
		
	else
	
		print_if_verbose $verbose "append_file() " "line: '$source' to file: '$targetFile'" ; 
		echo -e "$source" >> $targetFile
	fi ;
}


function delete_all_in_file() {

	
	local searchString="${1:-no_string_specified}" ;
	local targetFile="${2:-$lastTargetFile}" ;
	local verbose="${3:-$lastVerbose}" ;
	
	if [ "$targetFile" == "" ] || [ "$targetFile" == "no-file-specified-yet" ] ; then
		print_error "Invalid target file '$targetFile' "
		return 99;
	fi ;
	
	if  ! test -f "$targetFile" ; then
		print_error "delete_all_in_file: specified file does not exist: '$targetFile'" ;
		return 99 ;
	fi ;
	
	lastTargetFile=$targetFile ;
	
	local numOccurences=$(grep -o $searchString $targetFile | wc -l)
	
	if (( $numOccurences > 0 )) ; then
	
		print_if_verbose $verbose "delete_all_in_file" "Deleting $numOccurences lines containing '$searchString' in '$targetFile'" ;
		sed --in-place "/$searchString/d" $targetFile ;
	
	else 
		print_if_verbose $verbose "delete_all_in_file" "WARNING: no occurences of '$searchString' in '$targetFile'" ;
	fi ;
	
}



function comma_separate() {
  
	local original="$*";

	echo ${original// /,} ;
}



function to_lower_hyphen() {
  
	local original="$*";

	to_lower_case ${original//[^a-z]/-} ;
}


function to_lower_case() {
	echo "$1" | tr '[:upper:]' '[:lower:]'
}

function replace_all_in_file() {

	local searchString="$1" ;
	local replaceString="$2" ;
	local targetFile="${3:-$lastTargetFile}" ;
	local verbose="${4:-$lastVerbose}" ;
	
	if [ "$verbose" == "" ] ; then verbose=true ; fi ;
	lastTargetFile=$targetFile ;
	lastVerbose=$verbose ;
	
	
	if [[ "$searchString" =~ "|" ]] || [[ "$replaceString" =~ "|" ]] ; then 
		print_with_head "ERROR: replace_all_in_file: specified strings contain '|' which is used as a delimiter" ;
		return 99 ; 
	fi
	
	if ! test -f $targetFile ; then
		print_with_head "ERROR: replace_all_in_file: specified file does not exist: '$targetFile'" ;
		return 99 ;
	fi ;
	
	local numOccurences=$(grep -o $searchString $targetFile | wc -l)
	
	if (( $numOccurences > 0 )) ; then
	
		local replaceSafeForOutput=$replaceString ;
		if [[ $(to_lower_case $searchString) == *"pass"* ]] ; then replaceSafeForOutput='*MASKED*' ; fi;
		print_if_verbose $verbose "replace_all_in_file" "Replacing $numOccurences occurences of '$searchString' with '$replaceSafeForOutput' in '$targetFile'" ;
		sed --in-place "s|$searchString|$replaceString|g" $targetFile ;
	
	else 
		print_if_verbose $verbose "replace_all_in_file" "WARNING: no occurences of '$searchString' in '$targetFile'" ;
	fi ;

}
function backup_file() {
	
	local originalFile="$1" ;
	
	local backupFile="$originalFile.last" ;
	if [ "$2" != "" ] ; then
		backupFile="$2/`basename $backupFile`" ;
	fi ; 
	
	if test -r "$originalFile"  ; then
		print_if_verbose  true "backup_file" "Backing up $originalFile to $backupFile"
		
		if test -r "$backupFile" ; then
			print_if_verbose  true "backup_file" "Removing previous backup" ;
			rm --recursive --force $backupFile
		fi ;
		
		mv --force	--verbose $originalFile $backupFile;
		
	fi ;

}

function backup_original() {
	
	local originalFileOrFolder="$1" ;
	local verbose="${2:-true}" ;
	
	local newLocation="$originalFileOrFolder.original" ;
	
	if test -r "$originalFileOrFolder"  ; then
		print_if_verbose  true "backup_file" "Backing up $originalFileOrFolder to $newLocation"
		
		if test -r "$newLocation" ; then
			print_if_verbose  true "backup_file" "Removing previous backup" ;
			rm --recursive --force $newLocation
		fi ;
		
		cp --force --recursive $originalFileOrFolder $newLocation;
		
	fi ;

}

function backup_and_replace() {
	
	originalFile="$1" ;
	updatedFile="$2" ;
	
	print_line "Updating $originalFile with $updatedFile. $originalFile is being backed up"
	
	if [ -f $originalFile ] ; then 
		if [ ! -f $originalFile.orig ] ; then
			mv 	$originalFile $originalFile.orig;
		else
			mv 	$originalFile $originalFile.last;
		fi ; 
	else
		originalFolder=`dirname $originalFile`
		if [ ! -e "$originalFolder" ] ; then
			print_line "Did not find $originalFolder, creating."
			mkdir -p "$originalFolder"
		fi ;
	fi
	
	\cp -f $updatedFile $originalFile
}

function launch_background () {
	
	command="$1" ;
	arguments="$2" ;
	logFile="$3" ;
	appendLog="${4:-no}" ;
	useRoot="${5:-no}" ;
	
	
	print_section "Launch Background: '$command'"
	print_two_columns "pwd" "$(pwd)"
	print_two_columns "useRoot" "$useRoot"
	print_two_columns "logs" "location '$logFile', append: '$appendLog'"
	print_two_columns "Arguments" "$arguments"

	# First spawn a background process to do the agent kill
	# redirect error: 2>&1  replace file if exists and noclobber set: >|
	
	local runRoot="" ;
	if [ "$useRoot" == "yes" ] ; then runRoot="run_using_root" ; fi
	
	if [ "$appendLog" == "appendLogs" ] ; then
	
		$runRoot nohup $command $arguments >> $logFile 2>&1 &
		
	else
	
		backup_file $logFile $csapSavedFolder
		if test -f $logFile ; then
			rm --force $logFile ;
		fi
		$runRoot nohup $command $arguments > $logFile 2>&1 &
		
	fi	
	
	thePid="$!" ; theReturnCode=$? ;
	thePidFile="${thePidFile%.log}.pid"
	if test -f $thePidFile ; then
		rm --force $thePidFile ;
	fi
	
	echo $thePid > $thePidFile
	print_two_columns "return code" "$theReturnCode"
	print_two_columns "pidFile" "$thePidFile"
	
	# sleep is need to making sure any errors output by the nohup itself are captured in output
	sleep 1 ;

}

function add_link_in_pwd() {
	
	pathOnOs="$1"
	
	linkedPath="link"${pathOnOs////-} ;
	
	print_two_columns "$pathOnOs" "linked: $linkedPath"
	
	ln -s $pathOnOs $linkedPath
}


note_indent="   ";
function add_note() {
	
	currentNote="$*" ;
	if [ "$currentNote" == "start" ] ; then 
		add_note_contents="\n$LINE\n" ;
		
	elif [ "$currentNote" == "end" ] ; then 
		add_note_contents+="\n$LINE\n" ;
		
	else
		add_note_contents+="$currentNote\n$my_indent"
	fi ;
}

