# file find, locate files based on name, age, etc

# Notes:
# 1. You may need to extend time for command to complete if VM is very busy
# 2. stdbuf is used to prevent grep from buffering output. ref. http://www.pixelbeat.org/programming/stdio_buffering/
# 3. find -mmin +5 will find files older then 5 minutes
# 4. find -mtime +5 will find files older then 5 days


# update this to select directory 
folderToBeSearched="_file_" ;
numDays="+2" ;
dirDepth="1" ;

if [ ! -d "$folderToBeSearched" ]; then
	print_with_head "Folder does not exist: $folderToBeSearched";
	exit
fi

cd $folderToBeSearched 

print_with_head "looking for files "

numMatches=$(find . -maxdepth $dirDepth -mtime $numDays -type f | wc -l)


print_with_head "Found $numMatches files older then $numDays days inside $folderToBeSearched . Uncomment the next line to perform"

#uncomment to remove
#find . -maxdepth $dirDepth -mtime $numDays -type f  | xargs \rm -vrf 

# to review
#find . -maxdepth $dirDepth -mtime $numDays -type f  | xargs ls -l

# find * -mtime +5 -exec rm {} \;
# find . -mtime +30 | xargs rm 
# find . -maxdepth 1 -mtime $numDays | xargs ls -l