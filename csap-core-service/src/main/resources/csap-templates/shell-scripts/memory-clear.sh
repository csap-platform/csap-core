# clearBuffers, show and clear linux memory buffers. du commands can cause buffers to fill up.


# Notes:
# 1. You will need to run as root

print_with_head Before:
free && sync && echo 3 > /proc/sys/vm/drop_caches

print_with_head After:
free
