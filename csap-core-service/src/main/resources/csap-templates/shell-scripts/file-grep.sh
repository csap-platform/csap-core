# grep , grep a file for a pattern

# Notes:
# 1. Set the filter as needed
# 2. Tail/Grep commands can put significant load on VM, use CSAP vm monitor to observe impact

location="_file_"
filter="ReplaceThisWithYourString" ;

searchCommand="--fixed-strings" # --ignore-case

maxMatches=10
linesBefore=2
linesAfter=0

print_with_head "searching file for $filter, ignoring case , maxMatches: $maxMatches"

grep -A $linesAfter -B $linesBefore -m $maxMatches $searchCommand "$filter" $location

# 
# zgrep -A $linesAfter -B $linesBefore -m $maxMatches -i "$filter" $location