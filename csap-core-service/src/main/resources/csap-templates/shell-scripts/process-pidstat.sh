# processDetails, use linux pidstat command to show cpu and disk activity

# Modify CSAP timeouts if you change

sampleSeconds="4";

sampleIterations="6"

parentPid="_pid_" ;
serviceName='_serviceName_' ;

# uncomment to run on multiple hosts
# optionalPid=`ps -eo pid,args | grep $serviceName | grep -v -e grep | awk '{print $1}' | paste -d, -s -`

optionalPid="-p $parentPid" ; if [[ "$optionalPid" == *pid*  ]] ; then optionalPid="-l" ; fi;





print_separator "Pidstat for host: $HOSTNAME optionalPid: $optionalPid  sampleSeconds: $sampleSeconds  sampleIterations: $sampleIterations" 

# -p for pid
# u = cpu , d=IO , r=memory , t = thread, w = task switching

run_using_root pidstat $optionalPid  -hud $sampleSeconds $sampleIterations
