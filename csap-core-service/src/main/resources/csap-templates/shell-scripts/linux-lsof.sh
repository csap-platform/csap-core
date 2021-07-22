# host resources, list linux openfiles using process ids

# Notes:
# 1. You may need to extend time for command to complete if VM is very busy

openFiles=`cat /proc/sys/fs/file-nr | awk '{print $1}'`
totalThreads=`ps -e --no-heading --sort -pcpu -o pcpu,rss,nlwp,ruser,pid  | awk '{ SUM += $3} END { print SUM }'`
csapThreads=`ps -u$USER --no-heading --sort -pcpu -o pcpu,rss,nlwp,ruser,pid  | awk '{ SUM += $3} END { print SUM }'`

print_with_head "openFiles: $openFiles totalThreads: $totalThreads csapThreads $csapThreads "


networkConns=`ss | grep -iv wait | wc -l`	
networkWait=`ss | grep -i wait | wc -l`	


print_with_head networkConns: $networkConns networkWait: $networkWait

# totalFileDescriptors=`/usr/sbin/lsof  | wc -l`
csapFileDescriptors=`/usr/sbin/lsof -u $USER  | wc -l`

totalFileDescriptors=0;

for userid in $(cat /etc/passwd | sed 's/:.*$//g'); do 
   echo -n $userid  ' '; lsof -u $userid  2>/dev/null | wc -l  
   currentUserCount=`lsof -u $userid  2>/dev/null | wc -l`
   totalFileDescriptors=$((totalFileDescriptors+ currentUserCount))
done

print_with_head "totalFileDescriptors: $totalFileDescriptors csapFileDescriptors: $csapFileDescriptors"	

exit



print_with_head global totals
openFiles=`cat /proc/sys/fs/file-nr | awk '{print $1}'`
totalFileDescriptors=`/usr/sbin/lsof  | wc -l`
csapFileDescriptors=`/usr/sbin/lsof | grep $USER  | wc -l`
print_with_head openFiles in /proc/sys/fs/file-nr: $openFiles vm: $totalFileDescriptors $USER lsof: $csapFileDescriptors



# update this with your process identifier from ps output 
processFilter="someFilter"

print_with_head Matching processes
ps -ef | grep $processFilter  | grep -v -e grep

parentPid=`ps -ef | grep $processFilter   | grep -v -e grep -e $0 | awk '{ print $2 }'`

for pid in $parentPid; do
	print_with_head looking for open files for pid $pid

	print_with_head Number of open files: `/usr/sbin/lsof -p $pid | wc -l`
	print_with_head Number of procfd files: `ls -l /proc/$pid/fd | wc -l`

	print_with_head == open files:
	#/usr/sbin/lsof -p $pid 
	ls -l  /proc/$pid/fd
done ;