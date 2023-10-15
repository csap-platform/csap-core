#!/bin/bash

function printIt() { echo; echo; echo =========; echo == $* ; echo =========; }

function checkInstalled() { verify=`which $1`; if [ "$verify" == "" ] ; then printIt error: $1 not found, install using yum -y install; exit; fi   }

checkInstalled zgrep

# Notes:
# 1. You may need to extend time for command to complete if VM is very busy
# 2. stdbuf is used to prevent grep from buffering output. ref. http://www.pixelbeat.org/programming/stdio_buffering/
# 3. find -mmin +5 will find files older then 5 minutes
# 4. find -mtime +5 will find files older then 5 days


# update this to select directory 
folderToBeSearched="/data/loadbalancer/t800_COMPLETE" ;
newerThenDays="-10" ; # files newer then 60 days and older then 30 days Sets too large will cause performance issues
olderThenDays="+5"
dirDepth="5" ;

if [ ! -d "$folderToBeSearched" ]; then
	printIt "Folder does not exist: $folderToBeSearched";
	exit
fi

cd $folderToBeSearched 

printIt "looking for files newer then $newerThenDays days and older then $olderThenDays "

numMatches=`find . -name "*.zip" -maxdepth $dirDepth -mtime $newerThenDays -mtime $olderThenDays -type f | wc -l`

printIt "Found $numMatches files inside $folderToBeSearched."

if [ "$numMatches" == "0" ]; then
	printIt "No matches in date range. Modify and try again";
	exit
fi

#uncomment to show matching file listing
# find . -name "*.zip" -maxdepth $dirDepth  -type f  -mtime $newerThenDays -mtime $olderThenDays | xargs ls -l


printIt "looking for files containing $stringToLocate"
#numFilesWithVersion=`find . -name "*.zip" -maxdepth $dirDepth -mtime $newerThenDays -type f | xargs zgrep -oP $stringToLocate | wc -l`

#printIt "Found $numFilesWithVersion files containing $stringToLocate"


printIt "Items Located:"


function showMatches() {

	fileNamesInZip="download.hdr out/download.hdr Collector_CSPC.xml"
	stringToLocate='<Version>.*</Version>'
	vsemMatch='CDATA.*</Version>'
	zipName=$1
   
	#default search
	matches=`zipgrep -oh $stringToLocate $zipName $fileNamesInZip  2>&1 | grep -v grep | grep -v matched | sed 's/\(<Version>\|<\/Version>\)//g' | xargs echo -n`

   if [ "$matches" == "" ] ; then 
		# VSEM scenario
		matches=`zipgrep -oh $vsemMatch $zipName $fileNamesInZip  2>&1 | grep -v grep | grep -v matched |  sed 's/\(CDATA\[\|\]\]><\/Version>\)//g' | xargs echo -n`
   fi ;

   echo "$zipName : versions: $matches"

   #zipgrep -oh $stringToLocate $zipName $fileNamesInZip
}

export -f showMatches
find . -name "*.zip" -maxdepth $dirDepth -mtime $newerThenDays  -mtime $olderThenDays  -type f  -exec bash -c 'showMatches "$0"' {}  \;


# find . -name "*.zip" -maxdepth $dirDepth -mtime $newerThenDays  -mtime $olderThenDays  -type f | xargs zgrep -oP $stringToLocate | sed 's/\(<Version>\|<\/Version>\)//g'  



# uncomment to see the <version> tags
# find . -name "*.zip" -maxdepth $dirDepth -mtime $newerThenDays -type f | xargs zgrep -oP $stringToLocate


# alternate with newerct
# numMatchesDays=`find . -name "*.zip" -maxdepth $dirDepth  -newerct "1 Jan 2016" -type f | wc -l`