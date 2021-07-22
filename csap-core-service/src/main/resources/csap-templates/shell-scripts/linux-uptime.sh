# uptime and df, Summary of host availability

cd _file_

print_two_columns "host" "$(hostname --long)"

print_two_columns "user" "$(whoami)"

print_two_columns "linux uptime" "$(uptime | xargs)"


#
# csap cli examples
# 

#print_command \
#	"csap-agent host information" \
#	"$(agent agent/runtime)"
	
# print_two_columns "agent services" "$(agent model/services/name?reverse=true --parse | wc -w )"
# print_two_columns "csap services (all)" "$(csap model/services/name?reverse=true --parse | wc -w )"

# print_two_columns "agents up" "$(services_running csap-agent)"

# preflight, csap preflight function to asses state of system: source: click on files: bin/functions/misc.sh
# run_preflight  ;
