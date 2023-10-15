
FROM docker.io/peterdnight/csap-oracle
#FROM pnightin/csap-base

ENV csapTestVersion="1.0.33"

ENV dbPass="CHANGE_ME"

WORKDIR "/"
RUN echo installing CSAP sample code

RUN wget -nv --no-cookies --no-check-certificate \
	"http://maven.yourcompany.com/artifactory/yourgroup/org/csap/BootEnterprise/$csapTestVersion/BootEnterprise-$csapTestVersion.jar" \
	-O csapTest.jar
#ADD BootEnterprise-1.0.32-SNAPSHOT.jar /csapTest.jar
RUN sh -c 'touch csapTest_version_$csapTestVersion'
RUN sh -c 'touch csapTest.jar'

VOLUME /tmp

# http 
EXPOSE 8080

ENTRYPOINT [ \
			"/bin/sh", \
			"-c", \
			"java -version; java -Xms256M -Xmx256M -Dloader.path=lib,$ORACLE_HOME/ojdbc7.jar -Dspring.profiles.active=ociOracle -Dserver.port=8080 -DcsapJmxPort=8086 -jar /csapTest.jar" \
]
#ENTRYPOINT [ \
#	"java", "-Xms256M", "-Xmx256M", \
#	"-Dloader.path=lib,/oracle/instantclient_12_1/ojdbc7.jar", \
#	"-Dspring.profiles.active=ociOracle", \
#	"-DcsapJmxPort=8086", \
#	"-jar", \
#	"/csapTest.jar" \
#]
