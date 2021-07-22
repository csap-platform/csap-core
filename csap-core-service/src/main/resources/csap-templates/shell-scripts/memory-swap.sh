# swap disk, create addition swap space on host


# Notes:
# https://access.redhat.com/documentation/en-US/Red_Hat_Enterprise_Linux/5/html/Deployment_Guide/s2-swap-creating-file.html

print_with_head creating swap - run as root
swapDestination="/data/swapfile"

swapoff $swapDestination ; \rm -rf $swapDestination
swapInGb=1
swapSize=$((swapInGb*1024*1024))
dd if=/dev/zero of=$swapDestination bs=1024 count=$swapSize
chmod 0600 $swapDestination
mkswap $swapDestination
swapon $swapDestination
				
