# proc-pid-fd, show open files by specified pid
 
# Notes:
# 1. lsof will include shared libs, and possibly and count both file handle and memory handle
# 2. /proc/sys/fs is probably most useful when troubleshooting as it contains handles specific to process


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
	
	

openFiles=$(cat /proc/sys/fs/file-nr | awk '{print $1}')
print_two_columns  "host total" "$openFiles source: /proc/sys/fs/file-nr"


# uncomment to run on multiple hosts
# pidsCommaSeparated=`ps -eo pid,args | grep $serviceName | grep -v -e grep | awk '{print $1}' | paste -d, -s -`

pidPathList=""
for pid in $pidsSpaceSeparated ; do
	pidPathList+=" /proc/$pid/fd "
done; 


print_two_columns  "process total" "$(root_command ls $pidPathList | wc -l) source: $pidPathList"


for pid in $pidsSpaceSeparated ; do
	
	print_with_head "Files for: '$(ps -o pid,args -p $pid --no-heading)'"

	print_two_columns  "procfd - total" "$(root_command ls /proc/$pid/fd | wc -l)"
	print_two_columns  "procfd - jar" "$(root_command ls -l  /proc/$pid/fd | grep jar | wc -l)"
	print_two_columns  "procfd - socket" "$(root_command ls -l  /proc/$pid/fd | grep socket | wc -l)"
	
	print_two_columns  "lsof total" "$(root_command lsof -p $pid   2>/dev/null | wc -l)"
	
	print_separator "ls -l  /proc/$pid/fd"
	run_using_root ls -l  /proc/$pid/fd
	
#	print_separator "https://www.howtogeek.com/426031/how-to-use-the-linux-lsof-command/"
#	run_using_root lsof -P -p $pid 
	
	
done ;

exit ;

#
# helpers...
#

# dumb terminal
options="-P -p $pid" ; # -i -a will restrict to interfaces
old="$( run_using_root lsof $options )";
sleep 5
new="" ;
while true ; do
	new="$( run_using_root lsof $options )";
	newCount=$(echo -e "$new" | wc -l ) ;
	
	print_command \
		"Time: $(date +"%H:%M:%S") Open files: $newCount \n\n COMMAND  PID USER   FD      TYPE             DEVICE  SIZE/OFF       NODE NAME" \
		"$(diff <(echo "$old") <(echo "$new") )" ;
		
	old="$new" ;
	sleep 5 ;
done ;


# command line
watch --interval 5 \
	'ls -l  /proc/$pid/fd'



print_separator "lsof is slower, but also shows sockets, etc."

print_two_columns  "host lsof count" "$(root_command lsof 2>/dev/null | wc -l)"
print_two_columns  "$USER lsof count" "$(root_command lsof 2>/dev/null | grep $USER | wc -l)"










