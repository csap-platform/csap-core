FROM csapplatform/csap-java:latest

# FROM csap/csap-java
# 11.0.4

ARG jarVersion="2-SNAPSHOT"
ARG jarType="csap-snapshots"

#ARG jarVersion="22.01"
#ARG jarType="csap-release"


ARG jarName="csap-starter-tester-$jarVersion.jar"
ARG jarSource="http://my-server-01.yourcompany.org:8081/artifactory/$jarType/org/csap/csap-starter-tester/$jarVersion/$jarName"


ADD $jarSource $jarName

VOLUME /tmp

# http 
EXPOSE 7080

#
# -DcsapJmxPort=8086 will auto inject all the required java jmx settings, and expose the port when launched using CSAP dashboards
#

ENV jarVersion="$jarVersion"
ENV javaOptions="-Dcsap.security.enabled=false -Dserver.servlet.context-path=/ -Xms256M -Xmx256M -Dspring.profiles.active=CSAP_LIFE,services-embedded,company -DcsapJmxPort=7086 -Dserver.port=7080"
ENV javaTarget="-jar $jarName"

# docker run -it -u 0 --entrypoint="bash" daa27344c067

#ENTRYPOINT [ \
#	"java", "-Xms256M", "-Xmx256M", \
#	"-DcsapDockerJava", \
#	"-Dspring.profiles.active=embedded", \
#	"-DcsapJmxPort=8086", \
#	"-Dserver.port=8080", \
#	"-jar", \
#	"/csapTest.jar" \
#]




# for local and fast builds
# for debugging builds and multiple tries - copy jdk and others into local build context target/docker/pnightin/csap-tester/build/
#RUN echo adding jdk
#ADD jdk-8u141-linux-x64.tar.gz /java-local

#RUN echo adding secure java 
#ADD jce_policy-8.zip /java-local/jce_policy-8.zip
#RUN unzip -qq -o /java-local/jce_policy-8.zip -d /java-local/jdk1.8.0_141/jre/lib/security

#RUN sh -c 'touch /csapTest.jar'



#jmx 
#EXPOSE 8086

#
# Java RMI requires explicit host name - Recommend a string substition at lauch  
#
#ENTRYPOINT [ \
#	"java", "-Xms256M", "-Xmx256M", \
#	"-Dspring.profiles.active=embedded", \
#	"-Djava.security.egd=file:/dev/./urandom", \
#	"-Djava.rmi.server.hostname=_HOST_NAME_",  "-Dcom.sun.management.jmxremote.port=8086", "-Dcom.sun.management.jmxremote.rmi.port=8086", \
#	"-Dcom.sun.management.jmxremote.local.only=false", "-Dcom.sun.management.jmxremote.authenticate=false", "-Dcom.sun.management.jmxremote.ssl=false", \
#	"-jar","/csapTest.jar" \
# ]


