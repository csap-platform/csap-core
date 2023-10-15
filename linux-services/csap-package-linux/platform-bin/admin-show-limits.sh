#!/bin/bash

source $CSAP_FOLDER/bin/csap-environment.sh

print_two_columns "kernel semaphores" "current limits:  $(cat /proc/sys/kernel/sem)"
print_command \
	"kernel semaphores, used:  ipcs -us " \
	"$(ipcs -us  2>&1)" ;
	
print_command \
	"kernel semaphores, current settings:  ipcs -ls " \
	"$(ipcs -ls  2>&1)" ;


print_with_head "Reference: http://www.itworld.com/operating-systems/317369/setting-limits-ulimit"

print_command \
	"limit from cat /proc/sys/fs/file-nr" \
	"$(cat /proc/sys/fs/file-nr  2>&1)" ;



print_command \
	"Total Threads using ps output" \
	"$(ps -e --no-heading --sort -pcpu -o pcpu,rss,nlwp,ruser,pid  | awk '{ SUM += $3} END { print SUM }')" ;



print_command \
	"User $USER Threads using ps output" \
	"$(ps -u$USER --no-heading --sort -pcpu -o pcpu,rss,nlwp,ruser,pid  | awk '{ SUM += $3} END { print SUM }')" ;

# really slow	
#numberFiles=`/usr/sbin/lsof 2>/dev/null | wc -l`

# litle faster
#numberFiles=$(/usr/sbin/lsof -nOlP 2>/dev/null | wc -l` lsof -nOlP | wc -l)
#print_line "Open files using lsof: $numberFiles"

# faster - but shows open fd versus files
openFileStats=$(cat /proc/sys/fs/file-nr)
print_line "File Descriptors: \n\t total allocated - $(echo $openFileStats | awk '{print $1}')" \
	"\n\t total free - $(echo $openFileStats | awk '{print $2}') "		\
	"\n\t max  - $(echo $openFileStats | awk '{print $3}') "		
	
#numUserFiles=$(/usr/sbin/lsof 2>/dev/null | grep $USER  | wc -l)
numUserFiles=$(/usr/sbin/lsof -u $USER 2>/dev/null | wc -l)
print_line "$USER Open Files using lsof: $numUserFiles"	


numUserFiltered=$(/usr/sbin/lsof -u $USER |grep /|sort  -k9 -u |wc -l)
print_line "$USER Open Files filtered by file  using lsof: $numUserFiltered"	

#print_line "All kernel limits from 'ulimit -a' `ulimit -a`"

print_command \
	"All kernel limits from ulimit -a" \
	"$(ulimit -a)" ;



print_command \
	"cat on /etc/security/limits.conf configured by csap installer" \
	"$(cat /etc/security/limits.conf)" ;
	

print_command \
	"sysctl -a output. To update: /usr/sbin/sysctl -w net.ipv6.conf.all.forwarding=1 " \
	"$(sysctl -a)" ;
	
	
