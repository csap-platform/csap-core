# testDisk, perform disk IO tests

fileSystemToTest="_file_"

sizeOfTestFileInMbs="500"
blockSize=$((1000*1000));


cd "$fileSystemToTest"
testFile="$fileSystemToTest/csap-disk-test"
\rm --recursive --force $testFile

print_with_head "Testing '$fileSystemToTest' by creating and deleting $sizeOfTestFileInMbs MB file: 'csap-disk-test'"

currAvail=$(df --portability --block-size=1M . | tail -1 | awk '{print $4}')


if [[ $sizeOfTestFileInMbs -gt  $currAvail ]] ; then

	print_with_head Insufficient space available for test, must be numGb+1 available
	print_two_columns "available:" "$currAvail MB"
	print_two_columns "test size:" "$sizeOfTestFileInMbs MB"
	exit ;

fi ;



print_with_head "Running: time dd if=/dev/zero of=$testFile bs=$blockSize count=$sizeOfTestFileInMbs" 
time dd  if=/dev/zero of=$testFile bs=$blockSize count=$sizeOfTestFileInMbs
rm --recursive --force --verbose $testFile




print_with_head "Running: time dd oflag=nocache,sync if=/dev/zero of=$testFile bs=$blockSize  count=$sizeOfTestFileInMbs" 
time dd  oflag=nocache,sync if=/dev/zero of=$testFile bs=$blockSize count=$sizeOfTestFileInMbs
rm --recursive --force --verbose $testFile


print_with_head "Run completed: $(date)" ; 




#print_command \
#	"running: sync --file-system $testFile " \
#	"$( ls -l $testFile )\n" \
#	"$( sync  )" 

