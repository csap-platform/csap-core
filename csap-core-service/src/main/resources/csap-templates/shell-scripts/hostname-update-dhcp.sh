# hostname , update hostname and set domain

simpleHost=$(hostname | cut -d"." -f1) ;
dhcpDomain="DHCP_HOSTNAME=$simpleHost.yourdomain.net"
networkConfigFile="/etc/sysconfig/network-scripts/ifcfg-eth0"

print_with_head "host: $simpleHost dhcpDomain: $dhcpDomain"
print_line "remove the exit to apply changes"
exit

hostnamectl set-hostname $simpleHost
print_with_head "Updated '$(hostname)'"

backup_file $networkConfigFile
cp $networkConfigFile.last $networkConfigFile

sed -i '/DHCP_HOSTNAME/d' $networkConfigFile # uncomment if previously commented
echo $dhcpDomain >> $networkConfigFile

print_with_head "Updated $networkConfigFile"
cat $networkConfigFile

exit
print_with_head "Restarting csap and network"	
systemctl restart csap
systemctl restart network

exit
