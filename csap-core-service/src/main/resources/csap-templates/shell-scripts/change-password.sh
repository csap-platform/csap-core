# password changer, modify password on one or more hosts

user="csap";
newPassword="changeme" ;


if [ "$USER" != "root" ] ; then
	print_with_head "Script must be run as root, switch user."
	exit ;
fi ;

print_with_head "Changing password for user: $user"

echo -e "$newPassword\n$newPassword" | passwd $user