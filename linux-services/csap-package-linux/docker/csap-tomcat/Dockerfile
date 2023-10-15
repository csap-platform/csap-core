
FROM docker.io/peterdnight/csap-base
#FROM pnightin/csap-base

ENV testApp="http://maven.yourcompany.com/artifactory/yourartifactory/org/csap/Servlet3Sample/1.0.8/Servlet3Sample-1.0.8.war"

ENV tomcatVersion="apache-tomcat-8.5.16"
ENV CATALINA_BASE="/$tomcatVersion"
ENV CATALINA_HOME="/$tomcatVersion"

WORKDIR "/"
RUN echo installing tomcat

RUN wget -nv --no-cookies --no-check-certificate \
	"http://csaptools.yourcompany.com/tomcat/tom8.5/$tomcatVersion.tar.gz"
RUN tar -xzf *tomcat*.tar.gz 
RUN rm -rf *.gz

RUN echo adding support for jmx access
RUN wget -nv --no-cookies --no-check-certificate \
	"http://csaptools.yourcompany.com/tomcat/tom8.5/catalina-jmx-remote.jar"
RUN mv catalina-jmx-remote.jar $CATALINA_HOME/lib

WORKDIR "$CATALINA_BASE/webapps"
RUN wget -nv --no-cookies --no-check-certificate \
	$testApp \
	-O sample.war
	
RUN chmod -R 755 $CATALINA_HOME


EXPOSE 8080

ENV PATH="$CATALINA_HOME/bin/:${PATH}"

ENTRYPOINT [ \
			"/bin/sh", \
			"-c", \
			"echo running as `id`; echo \"path is $PATH\"; java -version; catalina.sh run" \
]