# processMemory, show either java memory (jmap) or os memory(pmap) information 


# updated automatically when launched from host dashboard 
pidsCommaSeparated="_pid_" ;
serviceName="_serviceName_" ;

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

	jmapType="-histo:live" ; # also -finalizerinfo , -clstats
	print_command \
		"Java jmap '$jmapType' for: '$pidsCommaSeparated'" \
		"$( $JAVA_HOME/bin/jmap $jmapType $pidsCommaSeparated )"


else
	
	# ref. http://linoxide.com/linux-command/linux-memory-analysis-with-free-and-pmap-command/
	

	print_command \
		"Linux pmap for $pidsCommaSeparated. Change -d to -x to view rss" \
		"$( run_using_root pmap -x $pidsCommaSeparated )"
		
	

fi ;


 