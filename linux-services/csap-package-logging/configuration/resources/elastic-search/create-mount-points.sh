#!/bin/bash


print_separator "Creating logging mountpoints"

volumeBasePath=${volume_os:-not-specified} ;
if [ "$volumeBasePath" == "not-specified" ] ; then
	print_error "volumeBasePath not set" ;
	exit 99;
fi ;

mountCreateScript="$(pwd)/create-logging-mounts.sh"

cat >$mountCreateScript<<EOF
#!/bin/bash
source $CSAP_FOLDER/bin/csap-environment.sh
EOF

cat >>$mountCreateScript<<'EOF'
function makeIfNeeded() {
	
	local folder=${1:-not-named} ;
	
	if ! test -d folder ; then
		
		print_two_columns "creating folder" $(mkdir --parents --verbose $folder 2>&1) ;
		
	else 
	
		
		print_two_columns "existing" "$folder" ;
	
	fi ;
	
}
EOF

cat >>$mountCreateScript<<EOF
makeIfNeeded $volumeBasePath-0 ;
makeIfNeeded $volumeBasePath-1 ;
makeIfNeeded $volumeBasePath-2 ;

EOF

chmod 755 $mountCreateScript ;
run_using_root $mountCreateScript ;
	