# linux file permissions, removes facl settings





requestedPath="_file_" ;

requestedItem=$(basename $requestedPath) ;
baseFolder=$(dirname $requestedPath) ;
removeUser="csap" ;
recursive="" ; # --recursive


cd $baseFolder


print_section "Restoring $requestedPath permissions"

#
# note setfacl requires relative folder only; pwd must be set
#

print_command \
	"getfacl $requestedPath" \
	"$(run_using_root "cd $baseFolder; getfacl $requestedItem")"
	


print_command \
	"setfacl --remove user:$removeUser $requestedPath" \
	"$(run_using_root "cd $baseFolder; setfacl $recursive --remove user:$removeUser $requestedItem")"



print_command \
	"setfacl --remove user:$removeUser $requestedPath" \
	"$(run_using_root "cd $baseFolder; setfacl  $recursive --remove-all --no-mask $requestedItem")"




print_command \
	"getfacl $requestedPath" \
	"$(run_using_root "cd $baseFolder; getfacl $requestedItem")"
	

#setfacl --recursive --modify user:$csapUser:rx $requestedFolder


#set -x
#chown -R root:root /etc
#find /etc -type f -exec chmod 644 {} +
#find /etc -type d -exec chmod 755 {} +
#chmod 755 /etc/init.d/* /etc/rc.local /etc/cron.*/*
#chmod 400 /etc/ssh/ssh*key