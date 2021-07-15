# df , size of mounted volumes

# Notes:
# 1. Set directory as needed
# 2. If running against ROOT owned filesystem, switch user to root


requestedFolder="_file_"
cd $requestedFolder

print_with_head "Running report for: '$requestedFolder'"

print_with_head "du: estimate file space usage"
du --summarize --human-readable --one-file-system * | sort --reverse --human-numeric-sort

print_with_head "df: inode usage"
df --inodes .

print_with_head "df:  report file system disk space usage"
df --human-readable --print-type 

maxTimeToAvoidOsPainSeconds=300

print_with_head "file count with max timeout: $maxTimeToAvoidOsPainSeconds"
timeout $maxTimeToAvoidOsPainSeconds find . -type f | wc -l

print_with_head "Raw Disk via fdisk: run as root"
fdisk -l

print_with_head "logical volume groups: vgdisplay"
vgdisplay ;

print_with_head "logical volumes: lvs"
lvs ;

print_with_head "logical volumes: lvscan"
lvscan  ;

# note that this can cause a lot of system cpu and memory to be used. Run it on an idle vm and use csap os dashboard
# print_with_head "file counts by folder sorted with max timeout seconds: $maxTimeToAvoidOsPainSeconds"
# timeout $maxTimeToAvoidOsPainSeconds find . -xdev -printf '%h\n' | sort | uniq -c | sort -k 1 -n
