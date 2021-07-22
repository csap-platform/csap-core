# vsphere vcgo, Provides cli to vcenter to create/update/delete vms, datastores, ...
#
# Reference: https://github.com/vmware/govmomi/blob/master/govc/USAGE.md
#
cd _file_


# print_command "env" "$(env | grep GOVC)" 


print_command \
	"About $(echo $GOVC_USERNAME at $GOVC_URL | tr '\\' '/' )" \
	"$(govc about)" 
	

vsphereVmPath="/$GOVC_DATACENTER/$(govc find vm -name $(hostname --short))"
vmDiskEnableUuid=$(govc vm.info -e=true "$vsphereVmPath" | grep disk.enableUUID | grep -i true | wc -l )

print_command \
	"vm info (10 lines) '$vsphereVmPath'" \
	"k8s disk: \t" \
	"$( if (( $vmDiskEnableUuid == 0 )) ; then echo FAILED: disk.enableUUID must be enabled ; else echo PASSED ; fi)\n" \
	"$(govc vm.info -e=true "$vsphereVmPath" | head -10)" 
	
print_command \
	"device info (10 lines) '$vsphereVmPath'" \
	"$(govc device.info -vm "$vsphereVmPath" | head -10 )"
	


exit

#
#  General Notes
#  - most commands support -json=true
#

print_command \
	"Api Root" \
	"$(govc ls)"


print_command \
	"vm info:  '$vsphereVmPath'" \
	"$(govc vm.info -e=true "$vsphereVmPath" )"


print_command \
	"find vms matching 'csap-dev*'" \
	"$(govc find vm -name 'csap-dev*' )"


print_command \
	"Enable uuid for vsphere cloud integration" \
	"$(govc vm.change -e='disk.enableUUID=true' -vm="$vsphereVmPath")" \
	"\n query: $(govc vm.info -e=true "$vsphereVmPath" | grep disk.enableUUID )"

#
#	DataStore
#		- default datastore is $GOVC_DATASTORE; use -ds CSAP_DS1_NFS
#


print_command \
	"find nfs datastores" \
	"$(govc find . -type s -summary.type NFS )"

	
folderName="demo-folder"
print_command \
	"Create a disk on datastore '$GOVC_DATASTORE'" \
	"$(govc datastore.mkdir $folderName )" \
	"\n $(govc datastore.disk.create -size 10M $folderName/demo-disk.vmdk)" \
	"\n $folderName contents: \n $(govc datastore.ls -R=true $folderName)" 
	

print_command \
	"Remove folder and disks in $folderName on datastore '$GOVC_DATASTORE'" \
	"$(govc datastore.rm $folderName)" \
	"\n listing: \n$(govc datastore.ls)"
	

#
# Devices
#

print_command \
	"attach disk to a vm - creating a clone but not a link" \
	$(govc vm.disk.attach -vm "$vsphereVmPath" -disk $folderName/demo-disk.vmdk)

print_command \
	"Remove a device from vm - keep files on datastore,  eg. disk-1000-1 usually is /dev/sdb " \
	$(govc device.remove -keep=true -vm "$vsphereVmPath" $deviceName)
	

print_command \
	"VCenter Devices - kubernetes hosts with dynamic PVCs" \
	"$(find_kubernetes_vcenter_device kubevols)"

print_command \
	"VCenter Devices - kubernetes hosts with disks" \
	"$(find_kubernetes_vcenter_device vmdk)"	

print_command \
	"VCenter Cleanup - change false to true" \
	"$(find_kubernetes_vcenter_device demo-disk.vmdk false)"

print_command \
	"VCenter Devices - kubernetes hosts with volumes" \
	"$(find_kubernetes_vcenter_device $GOVC_DATASTORE)"
