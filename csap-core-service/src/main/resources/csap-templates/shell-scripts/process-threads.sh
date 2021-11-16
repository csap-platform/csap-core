# processThreads, linux pstree output highlights the process child/parent relationships

pidsCommaSeparated="_pid_"
serviceName='_serviceName_' ;

# uncomment to run on multiple hosts
# pidsCommaSeparated=$(ps -eo pid,args | grep $serviceName | grep --invert-match --regexp grep | awk '{print $1}' | paste -d, -s -)


pidsSpaceSeparated="${pidsCommaSeparated//,/ }";
firstPid=${pidsSpaceSeparated%% *}
#print_line "parentPid: $parentPid pidsSpaceSeparated: $pidsSpaceSeparated firstPid: $firstPid"

print_command \
	"process parents" \
	"$(pstree -slp $firstPid | head -1)"


print_command \
	"process arguments" \
	"$(pstree -sla $firstPid )"
		
		
javaMatches=$(ps -ef | grep $pidsCommaSeparated | grep java | wc -l);


if (( $javaMatches >= 1 )) ; then
	
	
	print_command \
		"java jstack" \
		"$($JAVA_HOME/bin/jstack -l $pidsCommaSeparated)"
	
	
else 


	print_command \
		"ps threads $(ps -Lf -p $pidsCommaSeparated | wc -l) total" \
		"$(ps -Lf -p $pidsCommaSeparated)"

	print_command \
		"ps threads -mo" \
		"$(ps -mo THREAD -p $pidsCommaSeparated)"
		

fi ;
