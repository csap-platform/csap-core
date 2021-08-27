# uptime and df, Summary of host availability



print_section "Starting open ldap server on host port 389"

if ! $(wait_for_port_free 389 5 "ldap server") ; then
	print_separator "ldap server port still running";
	print_line "Exiting: CSAP host dashboard port explorer can be used to identify process holding port"
	exit 90 ;
fi ;

docker run \
	--rm --detach \
	--name ldap-server \
	--publish 389:1389 \
	csapplatform/ldap-server


print_section "Starting ldap admin web app on host port 8080"


if ! $(wait_for_port_free 8080 5 "ldap server web app") ; then
	print_separator "Warning: ldap server port still running";
	print_line "Exiting: CSAP host dashboard port explorer can be used to identify process holding port"
	exit 90 ;
fi ;

docker run \
	--rm --detach \
	--name ldap-ui \
	--publish 8080:80 \
	--env PHPLDAPADMIN_LDAP_HOSTS=$(hostname --long)  \
	--env PHPLDAPADMIN_HTTPS=false \
	osixia/phpldapadmin:latest
	
print_with_head "Use csap host dashboard to view and launch, or open http://$(hostname --long):8080"	

delay_with_message 5 "csap login test credentials: admin/admin; LDAP test credentials:  cn=admin,dc=example,dc=org, pass: admin" ; 

