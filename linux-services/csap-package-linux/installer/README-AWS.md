
# Considerations:
- The t2.micro is very small (but free ) - note that there may performance challenges running anything other then the most trivial services.
- t2.2xlarge running redhat 7.3 or later [very cheap](https://aws.amazon.com/ec2/pricing/on-demand/) and significantly more responsive
	- shut your host down following testing to avoid unnecessary charges
- before you can access your host by either ssh (or CSAP agent by http) -  aws console must be used to updated the inbound rules to
	allow connections.
- run the following to get core configuration completed
	- this is for redhat - [view others](http://docs.aws.amazon.com/AWSEC2/latest/UserGuide/set-hostname.html)  
	
 ```bash
# ssh to host using the default user, and update the host name (1 time activity)
sudo su -
mv authorized_keys ../oldauth
cp ~ec2-user/.ssh/authorized_keys .
chmod 600 authorized_keys

# set the host name
external_host=$(curl -s http://169.254.169.254/latest/meta-data/public-hostname)
sed -i "/preserve_hostname/d" /etc/cloud/cloud.cfg
echo -e "\npreserve_hostname: true\n" >> /etc/cloud/cloud.cfg
hostnamectl set-hostname --static $external_host
reboot

 ```