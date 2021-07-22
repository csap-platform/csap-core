# network socket wait , use expression to find a socket count by process name 
#
#  RUN AS ROOT: to view user mappings
#


portFilter="" ; # update this if you want to locate a specific port


networkConns=$(ss | grep -iv wait | wc -l)	
networkWait=$(ss | grep -i wait | wc -l)

print_with_head "Number of active sockets: $networkConns , Wait state: $networkWait"


if [ "$portFilter" == "" ] ; then
	print_with_head "socket status with process info "
	ss --resolve --processes
	
else 
	print_with_head "socket status filtered by '$portFilter' "
	ss --resolve --processes | grep $portFilter
fi

