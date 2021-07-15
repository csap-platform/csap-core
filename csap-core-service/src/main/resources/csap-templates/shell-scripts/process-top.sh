# top, show open files by specified pid
 
# Notes:
# 1. top in batch mode with threads shown
# https://man7.org/linux/man-pages/man1/top.1.html


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
	
numSeconds=2 ;
numRuns=1 ;
threadMode="-H" ;
batchMode="-b -n $numRuns -d $numSeconds";
monitorWidth="-w 200";

top $batchMode $threadMode $monitorWidth -p $pidsCommaSeparated

