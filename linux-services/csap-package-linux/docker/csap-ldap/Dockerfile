FROM bitnami/openldap:2.5.6-debian-10-r16

#FROM bitnami/openldap:2.5.7-debian-10-r5


#
#  ref https://hub.docker.com/r/bitnami/openldap/
#


COPY csap-demo.ldif /csap-init/csap-demo.ldif


ENV LDAP_ADMIN_PASSWORD="admin" \
	LDAP_ADMIN_USERNAME="admin" \
	LDAP_CUSTOM_LDIF_DIR="/csap-init" 
	
#ENV LDAP_ADMIN_PASSWORD="admin" \
#	LDAP_ADMIN_USERNAME="admin" \
#	LDAP_ROOT="dc=example,dc=org" \
#	LDAP_CONFIG_ADMIN_ENABLED="yes"  \
#	LDAP_CONFIG_ADMIN_USERNAME="cadmin"  \
#	LDAP_CONFIG_ADMIN_PASSWORD="admin"  \
#	LDAP_GROUP="demo-group" \
#	LDAP_USERS="admin,csap" \
#	LDAP_PASSWORDS="admin,csap" 


#
# https://github.com/osixia/docker-phpLDAPadmin 	
#	PHPLDAPADMIN_LDAP_HOSTS=yourhost
#   PHPLDAPADMIN_LDAP_PORT=1389
#	
#
	