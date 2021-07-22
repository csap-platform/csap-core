# wget and curl, http access using wget and curl

exit_if_not_installed wget ;

githubUrl="https://github.com/csap-platform/csap-core" ;
webUrl="http://$toolsServer/admin/api/model/host/lifecycle" ; 
toolsUrl="http://$toolsServer/admin/api/model/host/lifecycle" ; 
agentUrl="http://localhost:8011/CsAgent/api/model/host/lifecycle" ; 

testUrl="$agentUrl" ;

numRuns=10;
success=0 ;
showOutput="y" ;
intervalSeconds=0 ; # 0.5,1, ...

# wget option for getting out shown: -q0-


for (( run=0; run < numRuns ; run++ )) ; do  

	# test both status and content - rare conditions cause either to fail
	#resultsText=`wget -nv --spider $testUrl 2>&1`
	startTime=$(date +%s%N)
	#output=$(wget -qO- $testUrl 2>&1)
	output=$(curl --max-time 1 --silent $testUrl)
	resultsCode=$?  ; # bash exit code returned from wget, 0 indicates success
	
	#if [[ "$results" == *200* ]] ; then success=$((success+1)) ; fi
	if [[ "$resultsCode" == 0  && output != "" ]] ; then 
		success=$((success+1)) ; 
		output=$(echo $output | sed -e 's/<[a-zA-Z\/][^>]*>//g' | tail -15)
	fi
	
	if [ $showOutput != "n" ] ; then 

		numMillis=$((($(date +%s%N) - $startTime)/1000000))
		fullCode=$resultsCode;
		if (( "$resultsCode" == 28 )) ; then
			fullCode="$resultsCode (Request Timeout)";
		fi ;
		
		print_with_head "Run $run \t\t Return Code: $fullCode \t time: $numMillis ms, $testUrl"
		
		if (( "$resultsCode" != 28 )) ; then
			print_two_columns "HTTP Body" "$output" ; 
		fi ;
		
	fi
	
	sleep $intervalSeconds ;
	
done

errors="" ;
if [ $success != $numRuns ] ; then errors="Warning errors found " ; fi;

print_with_head "==== Results "$errors $success of $numRuns passed

# ssl certificate validation
# curl -vvI https://github.com/csap-platform/csap-core

# uncomment to get http content
# curl http://$toolsServer/admin/api/CapabilityHealth
# curl -v [[ ${lifeCycleSettings.getLbUrl()} ]]
	#nslookup $toolsServer
	# results=`nc -w 2 -z $toolsServer 80`