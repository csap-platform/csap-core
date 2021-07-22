# sshutils, tools to configure and set ssh

enable=false;
sshConfigFile="/etc/ssh/sshd_config"



if [ "$USER" != "root" ]; then
	print_line "Warning: Modification requires root access"
	exit ;
fi;

# uncomment line if commented
sed -i 's/#PermitRootLogin/PermitRootLogin/' $sshConfigFile

if  $enable ; then
	print_with_head "Enable remote root access, updating '$sshConfigFile'"
	sed -i 's/PermitRootLogin no/PermitRootLogin yes/' $sshConfigFile
	cat /etc/ssh/sshd_config  | grep PermitRootLogin
else
	print_with_head "Disabling remote root access, updating '$sshConfigFile'"
	sed -i 's/PermitRootLogin yes/PermitRootLogin no/' $sshConfigFile
	cat /etc/ssh/sshd_config  | grep PermitRootLogin
fi

print_with_head "Restarting: systemctl restart sshd "
systemctl restart sshd