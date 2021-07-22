# CSAP process count, shows counts of services
cd _file_

print_with_head "DcsapProcessId matches: " `ps -ef | grep DcsapProcessId | wc -l`

print_with_head "java matches: " `ps -ef | grep java| wc -l`

print_with_head "Uncomment the last line to see the deltas"
#ps -ef | grep java | grep -v DcsapProcessId  | grep -v activemq