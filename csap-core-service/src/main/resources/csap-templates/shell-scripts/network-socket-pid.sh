# socketPid, used by csap dashboard to query sockets by selected pid

# updated automatically when launched from host dashboard 
pidsCommaSeparated="_pid_" ; 
serviceName="_serviceName_" ;


#
#  Consider adding / removing: --numeric to skip port translate
#
ssParameters="--numeric --resolve --processes"


# uncomment to run on multiple hosts
# pidsCommaSeparated=$(ps -eo pid,args | grep $serviceName | grep --invert-match --regexp grep | awk '{print $1}' | paste -d, -s -)


pidsSpaceSeparated="${pidsCommaSeparated//,/ }";
firstPid=${pidsSpaceSeparated%% *}
#print_line "parentPid: $parentPid pidsSpaceSeparated: $pidsSpaceSeparated firstPid: $firstPid"



print_section "Process Reports"




print_command \
	"process parents" \
	"$(pstree -slp $firstPid | head -1)"

processArgOutput=$(pstree -sla $firstPid ) ;

print_command \
	"process arguments" \
	"$(echo -e "$processArgOutput")"
	 


#
# build pid regex for finding matches
#
#pidMatchRegex="${pidsCommaSeparated//,/|}|Netid";
pidMatchRegex="";
for pid in $pidsSpaceSeparated ; do
	pidMatchRegex="$pidMatchRegex|pid=$pid,";
done ;
pidMatchRegex="Netid$pidMatchRegex"; # include title for output



print_section "host namespace reports:  pid(s): '$pidsCommaSeparated' using regex: '$pidMatchRegex'"



socketListenOutput="$( run_using_root ss --listen $ssParameters | grep --extended-regexp  $pidMatchRegex )" ;
socketListenCount=$(( $(echo "$socketListenOutput" | wc -l) - 1 ));

print_two_columns "sockets listen" "$socketListenCount" ;

if (( $socketListenCount > 0 )) ; then
	
	print_command \
		"sockets listening: ss --listen $ssParameters" \
		"$( echo -e "$socketListenOutput"  | column --table)"
		
fi ;


socketConnectionOutput="$(run_using_root ss $ssParameters | grep --extended-regexp  $pidMatchRegex | column --table)";
socketConnectionCount=$(( $(echo "$socketConnectionOutput" | wc -l) - 1 ));

print_two_columns "socket connections" "$socketConnectionCount" ;

if (( $socketConnectionCount > 0 )) ; then
	
	print_command \
		"socket connections: ss $ssParameters" \
		"$( echo -e "$socketConnectionOutput"  | column --table)"

fi ;

#if (( socketListenCount > 0 )) || (( socketConnectionCount > 0 )) ; then
#	print_with_head "Skipping namespace resolution" 
#	exit ;
#fi ; 


pidNamespaceReportCount=0 ;
if [[ "$processArgOutput" == *namespace* ]]; then
	pidNamespaceReportCount=1; # or increase to show more pids
fi ;



print_section "pid namespace reports: run against first $pidNamespaceReportCount pids"


numRun=0;

#Docker: each pid will print equivalent sockets
for pid in $pidsSpaceSeparated ; do

	if (( $numRun >= $pidNamespaceReportCount)) ; then
		break ;
	fi ;
	numRun=$(( $numRun + 1)); 

	
	socketListenOutput="$( run_using_root nsenter --net --target $pid  ss --listen $ssParameters )" ;
	
	socketCommand="nsenter --net --target $pid  ss --listen $ssParameters" ;
	
	socketListenCount=$(echo -e "$socketListenOutput" |  sed '3d' | wc -l) ;
	print_two_columns "sockets listen" "$socketListenCount" ;
	
	if (( $socketListenCount > 0 )) ; then
		
		print_command \
			"$socketCommand" \
			"$( echo -e "$socketListenOutput" | sed '3d' | column --table)"
			
	fi ;
	
	
	socketConnectionOutput="$( run_using_root nsenter --net --target $pid  ss  $ssParameters )" ;
	
	socketCommand="nsenter --net --target $pid  ss  $ssParameters" ;
	
	socketConnectionCount=$(echo "$socketConnectionOutput" |  sed '3d' | wc -l) ;
	print_two_columns "socket connections" "$socketConnectionCount" ;
	
	if (( $socketConnectionCount > 0 )) ; then
		
		print_command \
			"$socketCommand" \
			"$( echo -e "$socketConnectionOutput" |  sed '3d' | column --table)"
			
	fi ;
	
done






exit

#Docker: each pid will print equivalent sockets
#for pid in $pidsSpaceSeparated ; do
#	print_with_head ": namespace: '$(ps -o pid,args -p $pid --no-header)'"
	
#	print_command \
#		"socket connections" \
#		"$( run_using_root nsenter --net --target $pid  ss --processes )"
#		
#	print_command \
#		"sockets in listen mode" \
#		"$( run_using_root nsenter --net --target $pid  ss --processes --listen )"
		
#done
#
#  Watch over a restart
#
watch --interval 1 \
	'pidsCommaSeparated=$(ps -eo pid,args --cols=1000 | grep CsAgent | grep --invert-match --regexp grep | xargs | cut -d " " -f1); echo $pidsCommaSeparated && pidMatchRegex=${pidsCommaSeparated//,/|} && ss --listen $ssParameters | grep --extended-regexp  $pidMatchRegex'

#
#  show open sockets over time
#

numRuns=10;
sleepDelay=0.5
for (( run=1; run < (numRuns+1) ; run++ ))
do
	sleep $sleepDelay
	print_with_head "run $run of $numRuns, open Sockets:" `ss -pr | grep --extended-regexp  "$pidMatchRegex" | wc -l`
done

exit



# networkConns=`ss | grep -iv wait | wc -l`	
# networkWait=`ss | grep -i wait | wc -l`	
# echo Number of active sockets: $networkConns , Wait state: $networkWait


exit

# iterate over pids if needed

# set separator for comma
IFS=","
for pid in $pidsCommaSeparated; do
	print_with_head  Number sockets for pid $pid : `ss -pr | grep -w $pid | wc -l`

	print_with_head List
	# use -r to resolve ip addresses
	ss -pr | grep -w $pid
done ;