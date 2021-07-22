# iostat , linux IO statistics

# Modify CSAP timeouts if you change
sampleIntervalSeconds=4
numberOfSamples=6
deviceFilter="-d"  # all devices

#deviceFilter="-d dm-2" # usually app mounts
# ls -l /dev/mapper* will show volume groups to dm mapping. 

#
# local disk
#

iostat -m $deviceFilter  $sampleIntervalSeconds $numberOfSamples

#
#  for NFS
#
# nfsiostat $sampleIntervalSeconds $numberOfSamples