# pkill , kill process using patterns

# Notes:
# 1. Set the filter as needed: csap-start.sh can be used to kill hung deployments




processFilter="someString" ;

processMatches=$(pgrep --full --list-full $processFilter) ;

print_with_head "Processes to be killed using: '$processFilter'\n$processMatches"





print_line "Exiting - delete this line to run the kill" ; exit ;

pkill -SIGKILL  --full $processFilter
print_line "Status: $?"







# -SIGTERM = -15 SIGKILL = -9