# network socket query , use expression to find a socket count by process name 
# Notes:
# 1. You may need to extend time for command to complete if VM is very busy

processFilter="someFilter"

print_with_head Matching processes
ps -ef | grep $processFilter | grep -v -e grep

parentPid=`ps -ef | grep $processFilter   | grep -v -e grep -e $0 | awk '{ print $2 }'`

for pid in $parentPid; do
print_with_head looking for ports for pid $pid

# ss -r will resolve ip addresses
ss -p | grep -w $pid
done ;