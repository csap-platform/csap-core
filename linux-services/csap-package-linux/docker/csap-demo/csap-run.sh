#!/bin/bash


source $HOME/.bashrc

envFile=${1:-/root/csap-run-env.sh};

print_two_columns "loading" "$envFile"
source $envFile


print_section "Configuring default application definition with docker settings"
#
# set the host name in application definition
#
defHost="$(hostname --short)";
if [ "$dockerHostFqdn" != "container" ] ; then 
	shortHost=$(echo $dockerHostFqdn | cut -d"." -f1) ;
	
	print_two_columns "dockerHostFqdn" "$dockerHostFqdn, '$shortHost' will be used instead of hostname: '$defHost'"
	defHost="$shortHost" ;
fi;


if ! grep -q "docker_install" $csapDefinitionFolder/application-company.yml ; then

	sed -i '1i# docker_install updated' $csapDefinitionFolder/application-company.yml

	print_two_columns "updating credentials" "application-company.yml" ;
	replace_all_in_file "admin,password" "$webUser,$webPass" $csapDefinitionFolder/application-company.yml true 

#	print_two_columns "disabling ssl" "application-company.yml" ;
#	append_file 'csap.web-server.ssl.keystore-file: ""' $csapDefinitionFolder/application-company.yml true 
#	append_file '' $csapDefinitionFolder/application-company.yml true 
	
	print_two_columns "updating" "default-project.json" ;
	replace_all_in_file "csap_def_template_host" "$defHost"  $csapDefinitionFolder/default-project.json true 


	agentJsonFile="$csapDefinitionResources/$csapName/$csapLife/csap-service.json" ;
	print_two_columns "overriding" "csap-agent port using $agentJsonFile" ;
	mkdir --parents $csapDefinitionResources/$csapName/$csapLife

	cat >>$agentJsonFile <<EOF
{
  "port": "$agentPort"
}
EOF


	testService="csap-verify-service" ;
	testJsonFile=$csapDefinitionResources/$testService/$csapLife/csap-service.json
	print_two_columns "overriding" "$testService port using EtestJsonFile" ;
	mkdir --parents $csapDefinitionResources/$testService/$csapLife

	cat >>$testJsonFile <<EOF
{
  "port": "9021"
}
EOF

else
	print_with_header "Skipping container configuration" ;
fi




if test -e /var/run/docker.sock ; then 
	print_section "found /var/run/docker.sock : updating permissions " ; 
	run_using_root chmod 777 /var/run/docker.sock
fi ;



#
# csap-start.sh default is to launch in the background for csap-agent. Show launch output in docker container logs
#
svcSpawn="yes" ; csap-start.sh

#
# core log file tailed in foreground to keep container running
#
sleep 2 ;



tail --follow=name --retry $csapLogDir/console.log