# ipcs, semaphore listing

cd _file_

print_with_head "kernel semaphores, current limits:  `cat /proc/sys/kernel/sem`"
print_with_head "kernel semaphores, used should always be less then allocation: `ipcs -us`"
print_with_head "kernel semaphores, current settings:  `ipcs -ls`"
print_with_head "Last access with pid: `ipcs -p`" 

print_with_head "To find allocation: cat /proc/YOURPID/cmdline"

function clean_semaphores() {
	for semid in `ipcs -s | grep -v -e - -e key  -e "^$" | cut -f2 -d" "`; do ipcrm -s $semid; done
	for semid in `ipcs -m | grep -v -e - -e key  -e "^$" | cut -f2 -d" "`; do ipcrm -m $semid; done
	
	print_with_head "ipcs output, note that docker,httpd install may fail if  semaphores exceed available"
	ipcs -a
	
	numLeft=`ipcs | grep -v -e - -e key -e root -e "^$" | wc -l`
	
	if [ $numLeft != "0" ] ; then
		print_with_head WARNING : only limited semaphores should be allocated: httpd
		sleep 10 ;
	fi
        
}

# clean_semaphores

