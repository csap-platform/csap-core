#!/bin/bash

#
# Run by OsManager.java as part of shared collections
#

#source $CSAP_FOLDER/bin/csap-environment.sh


openFiles=$(cat /proc/sys/fs/file-nr | awk '{print $1}')
totalThreads=$(ps -e --no-heading --sort -pcpu -o pcpu,rss,nlwp,ruser,pid  | awk '{ SUM += $3} END { print SUM }')
csapThreads=$(ps -u$USER --no-heading --sort -pcpu -o pcpu,rss,nlwp,ruser,pid  | awk '{ SUM += $3} END { print SUM }')

networkConns=$(ss | grep -v WAIT | wc -l)	
networkWait=$(ss | grep WAIT | wc -l)	
networkTimeWait=$(ss -a | grep TIME-WAIT | wc -l)
totalFileDescriptors=-1 ;	
csapFileDescriptors=-1 ;	
	
# takes a long time
# totalFileDescriptors=$(lsof  | wc -l)

#csapFileDescriptors=$(lsof -u $USER  | wc -l)
csapFileDescriptors=$(timeout 5s lsof -u $USER  | wc -l 2>&1) ;

totalFileDescriptors=0;

# default to only using the current user.
allUsers=$USER ;

if [ "$USER" == "root" ] ; then	
	# if running as root - add all together
	usersWithBash=$(cat /etc/passwd | grep bash) ;
	allUsers=$(echo -e "$usersWithBash" | sed 's/:.*$//g');
fi ;

for userid in $allUsers; do 
   currentUserCount=$(timeout 5s lsof -u $userid  2>/dev/null | wc -l)
   totalFileDescriptors=$((totalFileDescriptors+ currentUserCount))
done


##
## Do not modify without updating parsing in OsManager 
##
echo openFiles: $openFiles totalThreads: $totalThreads csapThreads $csapThreads \
totalFileDescriptors: $totalFileDescriptors csapFileDescriptors: $csapFileDescriptors \
networkConns: $networkConns networkWait: $networkWait networkTimeWait: $networkTimeWait