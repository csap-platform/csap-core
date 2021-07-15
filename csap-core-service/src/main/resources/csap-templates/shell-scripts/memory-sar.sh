# memory: sar, use sar to collect memory statistics

# Notes:
# http://www.thegeekstuff.com/2011/03/sar-examples/

print_with_head memory
sar -r 1 3


print_with_head swap
sar -S 1 3


print_with_head runq and load
sar -q 1 3



print_with_head sar options
sar -h