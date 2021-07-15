# tail , tail a file with grep support


# Notes:
# 1. Set the filter as needed
# 2. Tail/Grep commands can put significant load on VM, use CSAP vm monitor to observe impact
# 3. stdbuf is used to prevent grep from buffering output. ref. http://www.pixelbeat.org/programming/stdio_buffering/

outputFilter='someString' ;

print_with_head  "tailing file for $outputFilter, ignoring case"

tail -f  _file_ | stdbuf -o0 grep -i $outputFilter


