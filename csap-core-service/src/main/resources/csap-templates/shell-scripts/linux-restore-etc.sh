# linux permissions, restores permissions on etc file system


print_with_head "Restoring /etc permissions"

set -x
chown -R root:root /etc
find /etc -type f -exec chmod 644 {} +
find /etc -type d -exec chmod 755 {} +
chmod 755 /etc/init.d/* /etc/rc.local /etc/cron.*/*
chmod 400 /etc/ssh/ssh*key