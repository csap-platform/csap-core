# yum and rpm, examples highlighting query, update, delete of linux packages


packageCommand="yum" ;
if is_package_installed dnf ; then 
	osPackages="$osPackages dnf-plugins-core python3-dnf-plugins-core python3-dnf-plugin-versionlock" ; # use dnf autoremove
	packageCommand="dnf";
fi ;
#
#  updated yum repositories		
#
function maintain_repositories() {


	print_command \
		"$packageCommand clean all " \
		"$(run_using_root $packageCommand clean all  2>&1)" ;
		
	print_command \
		"$packageCommand makecache fast " \
		"$(run_using_root $packageCommand makecache fast 2>&1)" ;
			


}
maintain_repositories



#
# List out updates and optionally apply
#
function maintain_os() {
	
	local doUpdate=${1:-false} ;
	
	print_command \
		"$packageCommand check-update " \
		"$($packageCommand check-update 2>&1)" ;
	
	print_command \
		"$packageCommand versionlock status " \
		"$($packageCommand versionlock status 2>&1)" ;
		
		
	
	print_command \
		"cat /etc/yum/pluginconf.d/versionlock.list " \
		"$(cat /etc/yum/pluginconf.d/versionlock.list 2>&1)" ;
		
	if $doUpdate ; then
		
		delay_with_message 10 "System is being updated" ;
		run_using_root $packageCommand --assumeyes update ;
		
	fi ;

}
maintain_os


function view_repositorys() {
	
	print_command \
		"$packageCommand repoinfo " \
		"$($packageCommand --assumeyes repoinfo )" ;
		
	print_command \
		"$packageCommand repolist " \
		"$($packageCommand repolist )" ;

		
	print_command \
		"$packageCommand repolist all " \
		"$($packageCommand repolist all )" ;

}

view_repositorys

function find_package() {
	local command_to_find_package=${1:-bash} ;
	print_separator "Locating the package: '$command_to_find_package'"
	
	local full_path_to_command ;
	full_path_to_command=$(which $command_to_find_package 2>&1) ;
	local return_code=$? ;
	
	if (( $return_code == 0 )) ; then
		
		print_two_columns "package" "$(rpm -qf $full_path_to_command)" ;
		
	else
		print_two_columns "package" "not found" ;
	fi ;
	
}

find_package "bash"


function dnf_fast_mirror() {
		# https://darryldias.me/2020/how-to-setup-fastest-mirror-in-dnf/
		local dnfConfFile="/etc/dnf/dnf.conf" ;
		
		if ! test -r $dnfConfFile.orig ; then
			backup_original $dnfConfFile ;
			rm $dnfConfFile
			append_file "[main]" "$dnfConfFile" true
			append_line gpgcheck=1
			append_line installonly_limit=3
			append_line clean_requirements_on_remove=True
			append_line best=False
			append_line skip_if_unavailable=True
			append_line fastestmirror=1
			
			dnf clean all
		fi ;
}


exit





# examples are below


print_with_head "install configure and start ntpd service for syncing time"
$packageCommand --assumeyes install ntp ; systemctl start ntpd ; systemctl enable ntpd




print_with_head "completing any existing work when $packageCommand in a pending state"
yum-complete-transaction

print_with_head "install package without prompts using default repo"
yum --assumeyes install  traceroute

yum --assumeyes install dos2unix*

##
## Sample with rh5 and rh6
## 


echo;echo; echo ============== Sample os specific install used for bash ssl patch
numMatches=`grep  "6." /etc/redhat-release | wc -l`

if [ $numMatches == 0 ] ; then 
echo patching rh5	
cd /var/tmp


else	

echo updating  redhat 6

fi



rpm -qa | grep bash
# Note this may take 90 seconds to run, extend the timeout on console
echo == Current glibc version is `rpm -q bash`

echo == remove this line to proceed with install ; exit




echo == Post install
rpm -q bash


