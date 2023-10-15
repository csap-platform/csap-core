#!/bin/sh



function setupEnv() {
	
	echo "Working Directory: '$(pwd)'"

	if [ -e installer/csap-environment.sh ] ; then
		source installer/csap-environment.sh
	
	elif [ -e ../environment/csap-environment.sh ] ; then
	
		cd ..
		scriptDir=$(pwd) ;
		
		echo -e "\n\nDesktop development using windows subsystem for linux: \n\t '$scriptDir'"
		ENV_FUNCTIONS=$scriptDir/environment/functions ;
		source $scriptDir/environment/csap-environment.sh ;
		
	else
	
		echo "Desktop development"
		source $scriptDir/platform-bin/csap-environment.sh
		
	fi
	
	
	#debug=true;
	print_debug_command \
		"Environment variables" \
		"$(env)"

}



function checkOutRepos() {
	
	local repositorys="$1" ;
	local gitCheckoutFolder="$2" ;
	
	print_section "checkOutRepos to folder: $gitCheckoutFolder"
	
	print_two_columns "if this hangs" "ssl key probably needs to be added, try git clone --depth 1 git@bitbucket.org:yourcompanyinc/yourcompany-integration-definition "
	
	if test -e $gitCheckoutFolder ; then
	
#		prompt_to_continue "delete gitCheckoutFolder '$gitCheckoutFolder'"
		print_two_columns "Deleting" "$gitCheckoutFolder"
		rm --recursive --force $gitCheckoutFolder
		
	fi ;
	
	print_two_columns "creating" "$(mkdir --parents --verbose $gitCheckoutFolder)";
	 
	cd $gitCheckoutFolder 
	
	print_if_debug "repositorys: $repositorys"
	
	for repository in $repositorys ; do
	
		print_line "";
		print_two_columns "repository" "$repository"
		git clone --depth 1 $repository
		
	done ;
	
}

function move_with_retries() {
	
	
	local sourceFolder=${1:-not-specified} ;
	local destinationFolder=${2:-not-specified} ;
	
	local max_poll_result_attempts=${3:-10} ;
	
	local currentAttempt=1;
	
	for i in $(seq $currentAttempt $max_poll_result_attempts); do
	
		print_two_columns "moving" "'$sourceFolder' to '$destinationFolder'"
		sleep 1;  # always need a little delay for git commands to complete

		mv --force --verbose $sourceFolder $destinationFolder ;
		
		local returnCode="$?" ;
		
		if (( $returnCode == 0 )) ; then 
			break ;
		fi ;  
		
		
		currentAttempt=$(( $currentAttempt + 1 )); 
		print_two_columns "attempt" "$currentAttempt of $max_poll_result_attempts"
		
	done
	
	if (( $currentAttempt >= $max_poll_result_attempts )) ; then
		exit_on_failure $currentAttempt "failed to move";	
	fi
	
}

function publishToOtherProvider() {
	
	
	
	local repositorys="$1" ;
	local gitCheckoutFolder="$2" ;
	local publishLocation=${3:-skip} ;
	local publishTag=${4:-skip};
	
	
	if [[ $publishLocation == "skip" ]] ; then
	
		print_two_columns "publishLocation" "$publishLocation"
		return ;
	
	fi ;
	
	print_section "publishToOtherProvider: $publishLocation"
	print_two_columns "if this hangs" "ssl key probably needs to be added, git clone --depth 1 git@github.com:csap-platform/csap-bin"
	
	if ! test -e $gitCheckoutFolder ; then
	
#		prompt_to_continue "delete gitCheckoutFolder '$gitCheckoutFolder'"
		print_two_columns "Missing" "$gitCheckoutFolder"
		return ;
		
	fi ;
	 
	cd $gitCheckoutFolder 
	
	print_line "repositorys: $repositorys"
	
	
	
	for repository in $repositorys ; do
	
		print_line "";
		print_section "repository" "$repository"
		
	
		
		local projFolder=$(basename $repository);
		local gitFolder="$projFolder/.git" ;
		print_two_columns "removing" "$gitFolder"
		rm --recursive --force $gitFolder
		exit_on_failure $? "failed to remove";
		
		
		
		if [[ "$projFolder" == oss* ]]; then
		
			local newName=${projFolder:4} ;
			print_two_columns "newName" "$newName (was $projFolder)"
			move_with_retries $projFolder $newName.legacy ;
			
			projFolder=$newName ;
			
		elif [[ "$projFolder" == yourcompany* ]]; then
		
			local newName=${projFolder:7} ;
			print_two_columns "newName" "$newName (was $projFolder)"
			move_with_retries $projFolder $newName.legacy ;
			projFolder=$newName ;
		fi ;
		
		
		
		local gitHubLocation="$publishLocation/$projFolder.git";
		
		print_two_columns "cloning target" "$gitHubLocation"
		git clone $gitHubLocation
		local returnCode=$?
		
		
		if (( $returnCode == 0 )) ;  then
		
			print_line "\n * step 1 completed: cloned target source \n\n" 
			
			
			move_with_retries $projFolder $projFolder.latest
			print_line "\n * step 2 completed: updated source renamed to .latest \n\n"
			
			
			move_with_retries  $projFolder.legacy $projFolder
			print_line "\n * step 3 completed: source renamed to .latest \n\n"
			

			move_with_retries  $projFolder.latest/.git $projFolder
			print_line "\n * step 4 completed: moved git folder \n\n"
			
			
			
			# print_two_columns "copying" "$projFolder.legacy to $projFolder"
			# cp --recursive --force --no-target-directory --preserve=mode,ownership,timestamps $projFolder.legacy $projFolder
			
			cd $projFolder ;
			
			print_separator "switched to $projFolder for commit"
			
			# test only: echo "$publishTag" >> test-file.txt
			
			if ! test -d .git ; then
			
				#
				# github will create an empty repo
				#
			
				print_two_columns "init" "running git init"
			
				git init
				git add .
				git commit -m "csap-master-create $publishTag"
				git branch -M master
				
			else
				
				git add --all
				print_two_columns "merge" "$publishTag"
				git commit -m "csap-master-merge $publishTag"
			fi ;
			
			sleep 1;
			print_two_columns "pushing" "$gitHubLocation"
			git push -u origin master ;
			
			local returnCode=$?
			print_two_columns "returnCode" "$returnCode"
			
			if (( $returnCode != 0 )) ;  then
				delay_with_message 10 "WARNING: Failed to push" ;
			fi ;
			
			
			
			if [[ "$publishTag" != merge* ]] ; then
				print_two_columns "tagging" "$publishTag"
				git tag $publishTag
				
				git push --tags
				
			else 
				print_two_columns "tagging" "skipped $publishTag"
			fi ;
			
		fi ;
		

		cd $gitCheckoutFolder 
		

	done ;
	
}

function performBuild() {
	
	local buildFolders="$1" ;
	local m2="$2" ;
	local mavenCommand="$3" ;
	
	
	print_section "performBuild: $buildFolders"
	
	
	if [[ $buildFolders == "skip" ]] ; then
	
		print_two_columns "buildFolders" "$buildFolders"
		return ;
	
	fi ;
	
	
	print_if_debug "runBuilds  folder: $buildFolder" ;
	
	local mavenSettings="$m2/settings.xml" ;
	if [ ! -e "$mavenSettings" ] ; then
		prompt_to_continue "Warning: $mavenSettings not found"
	fi
	
	for buildFolder in $buildFolders ; do

		cd $buildFolder ;
		print_section "Building $buildFolder" ;
		
		print_two_columns "mvn" "--batch-mode --settings $mavenSettings" 
		print_two_columns "command" "$mavenCommand" 
		print_two_columns "MAVEN_OPTS" "$MAVEN_OPTS" 
		
		print_separator "maven output start"
	
		#
		# indenting output so that content can be folded in most editors
		#
		mvn --batch-mode --settings $mavenSettings $* 2>&1 | sed 's/^/  /'
	
		
		#
		# bash pipestatus gets the rc when commands are piped
		#
		local buildReturnCode="${PIPESTATUS[0]}" ;
		if [ $buildReturnCode != "0" ] ; then
			print_line "Found Error RC from build: $buildReturnCode"
			echo __ERROR: Maven build exited with none 0 return code
			exit 99 ;
		fi ;
		
		print_separator "maven output end"
	
	done ;
	
}
