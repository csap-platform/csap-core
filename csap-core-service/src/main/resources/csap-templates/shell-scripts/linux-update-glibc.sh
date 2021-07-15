# patch glibc, update glibc version based on target os
			
function glibcPatch() {
	print_with_head current glibc
	rpm -qa | grep glibc
	print_with_head run as root and extend timeout to 5 minutes then remove the exit
	exit

	print_with_head updating
	yum -y update glibc


	print_with_head new glibc
	rpm -qa | grep glibc
}

function rh5() {
	print_with_head rh5 patch
	glibcPatch
}

function rh6() {
	print_with_head rh6 patch
	glibcPatch

}

function rh7() {
	print_with_head rh7 patch
	glibcPatch

}

function runBasedOnOs() {
	versionDescription=`cat /etc/redhat-release` ;

	if [[ $versionDescription == *"release 5"* ]] ; then
		rh5

	elif [[ $versionDescription == *"release 6"* ]] ; then
		rh6

	elif [[ $versionDescription == *"release 7"* ]] ; then
		rh7
	fi
}

runBasedOnOs ;
