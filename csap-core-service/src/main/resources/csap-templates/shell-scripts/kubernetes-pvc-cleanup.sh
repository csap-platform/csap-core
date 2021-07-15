# pvc cleanup , Used by CSAP kubernetes integration to remove dead claims

# 
# Notes:
#	- supports nfs (default) and vsphere (set isVsphere=true)
#   - default: lists out inactive pvcs. set isTest=false to actually delete
#


requestedFolder="_file_"
cd $requestedFolder

# print_command "Vsphere vcgo environment" "$(env | grep -i govc)"

isTest=true
isVsphere=false ;

print_with_head "Kubernetes active volume claims"
kubectl get PersistentVolumeClaim --all-namespaces

kubernetesPvcFiles=$(run_using_root ls -d $requestedFolder/*pvc* 2>/dev/null) ;

if $isVsphere ; then 
	requestedFolder="kubevols" ;
	kubernetesPvcFiles=$(govc datastore.ls kubevols) ;
fi ;

print_with_head "Checking for inactive pvcs in: '$requestedFolder'"
activeClaims=$(kubectl get PersistentVolumeClaim --all-namespaces | awk '{print $4}')

for filename in $kubernetesPvcFiles; do

	isActive=false ;
    
	for activeClaim in $activeClaims; do
		if [[ $filename == *$activeClaim* ]] ; then
			print_two_columns "active" $filename
			isActive=true
		fi
	done
    
	if ! $isActive ; then
		
		fileDescription="$(du --summarize --human-readable $filename 2>/dev/null)" ;
		
		if $isVsphere ; then
			fileDescription="Vsphere datastore: $filename" ;
		fi ;
    
		print_two_columns "NOT ACTIVE" "$fileDescription"
		
	 	if ! $isTest ; then 
	 	
			print_with_head "Deleting $filename"
			if $isVsphere ; then
				if [[ "$filename" == *vmdk ]] ;
			 		print_with_head "datastore.rm $requestedFolder/$filename"
					govc datastore.rm $requestedFolder/$filename ;
				else
					print_line "Ignoring file - filename does not end in vmdk"
				fi ;
		 		
			else
				run_using_root rm --recursive --force $filename
			fi ;
	  		
		fi
    fi
done