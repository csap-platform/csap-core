#!/bin/bash

source /opt/csap/csap-platform/bin/csap-environment.sh ; 


print_section "Volume Totals by container                 Note this can take several minutes to compute"

function print_volume() { printf "%15s     %-20s %-20s \n" "$@" ; }

print_volume 'Total(mb)' "Container Name"
print_volume "---------" "------------------------------------"


containerIds=$(docker ps --quiet);

for containerId in $containerIds; do 
  	
  	name=$(docker inspect -f {{.Name}} $containerId | tail -c +2) ;
  
  	allMounts=$(docker inspect -f '{{range .Mounts}}{{.Source}} {{end}}' $containerId); 

	totalAll="0"
	if [ "$allMounts" != "" ] ; then
  		totalAll=$(run_using_root du -sm --total $allMounts | tail -1 | awk '{print $1}')
	fi ;

	print_volume $totalAll $name
	
done | sort --reverse --human-numeric-sort



print_section "Volume Report per container"

for containerId in $containerIds; do 

  	name=$(docker inspect -f {{.Name}} $containerId | tail -c +2) ;
  	
  	print_line "\n *$name: $containerId"
	
	mountArray=( $(docker inspect -f '{{range .Mounts}}{{.Source}} {{end}}' "$containerId") ); 

	if (( ${#mountArray[@]} == 0 )) ; then 
		print_volume '-' '-'
	else
		for mount in ${mountArray[@]}; do 
			print_volume $(run_using_root du -hLs "$mount" 2>/dev/null | tail -n 1); 
		done | sort --reverse --human-numeric-sort
	fi

done

if [ "$USER" != "root" ]; then
	print_line "Warning: Docker volume reports require root level access"
fi;

print_section "docker system df -v"
docker system df -v


exit