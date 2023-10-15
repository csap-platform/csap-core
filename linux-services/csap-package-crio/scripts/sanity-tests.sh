source $CSAP_FOLDER/bin/csap-environment.sh

requestedCommand=${1:-none} ;

sanityImage=${sanityImage:-nginx:latest} ;

function status_tests() {
	
	print_section "status_tests"
	
	run_using_root crictl info ;
	
	run_using_root crictl ps --all;
	
	run_using_root crictl stats ;

}


function run_tests() {
	
	print_section "status_tests"
	
	run_using_root crictl info ;
	
	run_using_root crictl ps --all ;
	
	run_using_root crictl pull busybox ;
	
	local podConfigFile=$(pwd)/podConfig ;
	
	cat >>$podConfigFile<<'EOF'
{
    "metadata": {
        "name": "nginx-sandbox",
        "namespace": "default",
        "attempt": 1,
        "uid": "hdishd83djaidwnduwk28bcsb"
    },
    "log_directory": "/tmp",
    "linux": {
    }
}
EOF

	local podId=$(root_command crictl runp $podConfigFile)
	
	print_with_head "created podId $podId using crictl runp $podConfigFile "
	
	run_using_root crictl pods
	
	local containerConfigFile=$(pwd)/containerConfig ;
	
	cat >>$containerConfigFile<<'EOF'
{
  "metadata": {
      "name": "busybox"
  },
  "image":{
      "image": "busybox"
  },
  "command": [
      "top"
  ],
  "log_path":"busybox.log",
  "linux": {
  }
}
EOF
	
	local containerId=$(root_command crictl create $podId $containerConfigFile $podConfigFile);
	
	print_with_head "created containerId $containerId using crictl runp $podConfigFile "
	
	
	run_using_root crictl ps --all ;
	
	run_using_root crictl start $containerId
	
	delay_with_message 1 "waiting for container to start"
	
	run_using_root crictl ps --all ;
	
	
	run_using_root crictl stop $containerId
	
	
	delay_with_message 1 "waiting for container to stop"
	
	run_using_root crictl ps --all ;
	
	
	
	run_using_root crictl stopp $podId
	
	run_using_root crictl rmp $podId
	
	

}


function perform_operation() {

	case "$requestedCommand" in
		
		"run_tests")
			run_tests
			;;
		
		"status_tests")
			status_tests
			;;
		
		 *)
	            echo "Usage: $0 {verify_podman_run|status_tests}"
	            exit 1
	esac

}

# uncomment to invoke specific command interactively; note no CSAP variables will be set 
# requestedCommand="verify_podman_run"
perform_operation
