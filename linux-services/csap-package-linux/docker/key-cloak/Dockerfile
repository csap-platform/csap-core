FROM jboss/keycloak:4.6.0.Final

WORKDIR /app

#COPY 2018-11-28-yourcompany-sso-realm-export.json /app
#COPY export /app

#USER root
#WORKDIR "/java-local"
COPY keycloak-start.sh .
RUN chmod +x keycloak-start.sh

#ENTRYPOINT /app/keycloak-start.sh 