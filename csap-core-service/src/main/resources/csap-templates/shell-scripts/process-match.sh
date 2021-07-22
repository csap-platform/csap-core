# csap process matching, command run by management agent and used by service process filters

print_with_head "command used by csap to map service names to pids for deploy commands and resource consumption"

ps -e --no-heading --sort -rss -o pcpu,rss,vsz,nlwp,ruser,pid,nice,args

# ps -e --no-heading --sort -rss -o pcpu,rss,vsz,nlwp,ruser,pid,nice,args | sed 's/  */ /g'