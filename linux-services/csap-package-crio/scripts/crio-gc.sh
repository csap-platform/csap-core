source $CSAP_FOLDER/bin/csap-environment.sh


stoppedContainerIds=$(root_command crictl ps -a \
  | grep --invert-match --regexp "Running" --regexp "CONTAIN" \
  | awk '{print $1}') ;
  
stoppedContainerNames=$(root_command crictl ps -a \
  | grep --invert-match --regexp "Running" --regexp "CONTAIN" \
  | awk -F '[[:space:]][[:space:]]+' '{print $5}') ;
  
print_command \
	"Removing stopped containers: $stoppedContainerNames" \
	"$(root_command crictl --debug rm $stoppedContainerIds)"


print_command \
	"Removing unused images" \
 	"$( root_command crictl rmi --prune )"

 	

run_using_root "crictl ps"

run_using_root "crictl images"


