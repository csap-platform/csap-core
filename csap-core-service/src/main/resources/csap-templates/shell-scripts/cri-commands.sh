# podman and crictl, troubleshoot kubernetes cri-o provider

#
#  Notes
#	- podman and CRIO  should run as root
#	- csap podman and crio packages include sanity-tests.sh with extensive examples
#  
#


run_using_root "crictl ps"


exit ;


#
#  prune containers and images post clean up
#

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



#
# json listing samples
#
run_using_root "podman ps --all --external --format json"


run_using_root "crictl ps --output=json"



#
# cri inspect to find runtime 
#
containerIds=$(root_command crictl ps --quiet);
firstId=$(echo $containerIds | awk '{print $1;}') ;
run_using_root "crictl inspect --output=json $firstId"
	


containerIds=$(crictl ps --quiet);

for containerId in $containerIds; do 
  	
  	pid=$(crictl inspect --output go-template --template '{{.info.pid}}' $containerId ) ;
	
	echo "$containerId,$pid"
	
done




#
# start podman rest api
#

function add_podman() {
	
	yum install --assumeyes podman  # optional podman-docker 
	
	local listener="tcp:$(hostname --long):4243"  # set to empty to use

	podman system service $listener --log-level=info --time=0

	# podman system service --log-level=info --time=0
	# setfacl --recursive --modify user:csap:rwx /var/run/podman
	
}




function add_crio() {
	
	print_separator "adding podman"
	yum  install -y podman
	
	#
	#  crio required - otherwise kubernetes preflight fails
	#
	# sysctl -w net.ipv4.ip_forward=1 # set by podman install?
	
	local OS=CentOS_8 ;
	local VERSION=1.21 ;
	
	
	
	curl -L -o /etc/yum.repos.d/devel:kubic:libcontainers:stable.repo https://download.opensuse.org/repositories/devel:/kubic:/libcontainers:/stable/$OS/devel:kubic:libcontainers:stable.repo
	curl -L -o /etc/yum.repos.d/devel:kubic:libcontainers:stable:cri-o:$VERSION.repo https://download.opensuse.org/repositories/devel:kubic:libcontainers:stable:cri-o:$VERSION/$OS/devel:kubic:libcontainers:stable:cri-o:$VERSION.repo
	yum install --assumeyes cri-o cri-tools
	
	systemctl daemon-reload
	systemctl enable crio --now
	
	#
	# post install of kubernetes ref: https://github.com/cri-o/cri-o/issues/4276
	# systemctl restart crio
	#
	#
	# or add to /etc/crio/crio.conf
	# Path to the directory where CNI configuration files are located.
	# network_dir = "/etc/cni/net.d/"
	#
	local crioConf="/etc/crio/crio.conf"
	
	if ! does_file_contain_word $crioConf "/etc/cni/net.d/" ; then 
		print_line "updating $crioConf"
		sed --in-place '/\[crio.network\]/a network_dir = "/etc/cni/net.d/"' $crioConf
		
	else 
		print_line "already updated $crioConf"
		
	fi
	
	print_command "$crioConf" "$(cat $crioConf)"
	
	
	
}

add_crio


