#!/bin/bash

# output is used in CSAP docker container collections. Update dockerHelper.java with changes

dockerIds=$(docker ps -q)
volumeIncludes=${volumeIncludes:-/var/lib}

for dockerContainerId in $dockerIds; do 	
					
	containerName=$(docker inspect -f {{.Name}} $dockerContainerId | tail -c +2) ;
	containerDiskUsage="none"
	  
	containerMounts=$(docker inspect -f '{{range .Mounts}}{{.Source}} {{end}}' $dockerContainerId) ; 
	
	filteredMounts=""
	for containerMount in $containerMounts ; do
		# trim whitespace
		containerMount=$(echo $containerMount | xargs) ; 
		if [[ $containerMount =~ $volumeIncludes.* ]] ; then
			filteredMounts="$filteredMounts $containerMount"
		fi ;
	done ;
	
	containerTotalMb="-1"
	if [ "$filteredMounts" != "" ] ; then
	
		containerDiskUsage=$(timeout 5s du --summarize --block-size=1M --total $filteredMounts)
		if [ "$containerDiskUsage" != "" ] ; then
			containerTotalMb=$(echo $containerDiskUsage | awk '{print $(NF-1)}') ;
		fi ;
		
	fi ;

	
	#echo -e "\n\n =====\nvolumeIncludes: $volumeIncludes, containerMounts:$containerMounts\n$containerDiskUsage\n====" 
	echo $containerTotalMb $containerName
	
done ;