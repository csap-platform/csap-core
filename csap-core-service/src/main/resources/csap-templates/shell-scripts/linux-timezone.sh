# timezone modification, view / update the timezone settings
# Notes:
# 1. Set the timeZone as needed

curZone=`cat /etc/sysconfig/clock`
tzLink=`readlink /etc/localtime`
print_with_head == date is `date` ,  zone is $curZone, /etc/localtime is $tzLink


#
# comment this out to update
#
exit ;

# update to desired zone
vmZone="America/Chicago"

echo ZONE="$vmZone" > /etc/sysconfig/clock
\rm -rf /etc/localtime
ln -s /usr/share/zoneinfo/$vmZone /etc/localtime
