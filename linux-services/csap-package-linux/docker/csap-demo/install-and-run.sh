#!/bin/sh




osPackages="wget which unzip procps-ng sysstat sudo net-tools dos2unix"


function print() { 
	echo -e "\n\n\n---------------   $*  ------------------" ;
}


function print_section() { 
	echo -e  "\n* \n**\n*** \n**** \n*****  $* \n**** \n*** \n** \n*"
}


webUrl="http://$dockerHostFqdn:9011";
if [ "$dockerHostFqdn" == "container" ] ; then
	print_section "It is recommended to set the dockerHostFqdn variable to the fqdn of the host running dockerd. (output of hostname --long); console can still be hit though)" ;
	webUrl="http://<docker-host>:9011";
fi ;


print_section "In ~2 minutes - console will be available via browser $webUrl : user: $webUser pass: $webPass" ;




print "installing minimum os packages (60s)" ;
# yum eol
dnf --assumeyes install $osPackages



print "extracting linux package from $csapZip" ;
unzip  -j $csapZip csap-platform/packages/csap-package-linux.zip



print "extracting installer from csap-package-linux.zip installer/*" ;
unzip -qq csap-package-linux.zip installer/*



if ! test -d /opt/csap/csap-platform ; then 

	print "running installer/install.sh" ;
	# -skipAutoStart is a little quicker but then requires manuall starts of demo services
	./installer/install.sh -noPrompt -ignorePreflight -skipOs  -dockerContainer  -installDisk default  -installCsap default -csapDefinition default
	
fi

#
# run using the csap login environment
#

#
#  Variable substituion
#
cat >/root/csap-run-env <<EOF
export agentPort=${agentPort:-9011};
export dockerHostFqdn=${dockerHostFqdn:-container};
export webUser=${webUser:-csapx};
export webPass=${webPass:-csapx};


EOF


#
#  NO Variable substituion
#
cat >>/root/csap-run-env <<'EOF'
export csapCookieSSO="csapDockerSSO" ;
export csapCookieSession="csapDockerSession" ;
export csapName="csap-agent" ;
export csapLife="dev" ;
export csapHttpPort=${agentPort} ;
export csapServer="SpringBoot" ;
export csapWorkingDir="$csapPlatformWorking/$csapName" ;
export csapLogDir="$csapWorkingDir/logs" ;
export csapParams="-Dspring.profiles.active=dev,agent,company -Xmx512M" ;
EOF


print "invoking csap-run.sh" ;
su --login csap --command="/root/csap-run.sh /root/csap-run-env"



