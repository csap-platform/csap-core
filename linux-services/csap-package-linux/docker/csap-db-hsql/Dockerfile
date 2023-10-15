FROM csap/csap-java9

#add hsqldb-2.4.0.zip /

RUN printf "\n\n ***** installing hsql db ***\n\n" && \
	wget -nv --no-cookies --no-check-certificate "http://nightingale-one.davis.yourcompany.lab/hsqldb/hsqldb-2.4.0.zip" && \
	unzip -qq -o hsqldb-*.zip -d / && \
	rm *.zip && \
	mv /hsqldb-2.4.0/hsqldb /hsqldb

ADD runHsql.sh /hsqldb
RUN chmod -R 755 /hsqldb

WORKDIR "/hsqldb"

EXPOSE 9002


# csap docker launcher will fully configure jmx and export port if it finds -DcsapJmxPort
ENV javaOpts="-Xmx500M -Xms500M -DcsapJmxPort=9003" \
	hsqlConfig="-classpath lib/hsqldb.jar org.hsqldb.server.Server -port 9002" \
	dbDefinition="-database.0 file:data/testdb -dbname.0 testdb"

#env csapJmxOpts="-Dcsap -Djava.rmi.server.hostname=centos1 -Dcom.sun.management.jmxremote.port=8162 -Dcom.sun.management.jmxremote.rmi.port=8162 -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false -Dcom.sun.management.jmxremote.local.only=false"
#expose 8163





ENTRYPOINT [ "/hsqldb/runHsql.sh" ]


#ENTRYPOINT [ \
#	"/bin/sh", \
#	"-c", \
#	"echo running as `id`; echo path is $PATH ; java $javaOpts $hsqlConfig $dbDefinition" \
#]

#ENTRYPOINT [ \
#	"java",  \
#	"-classpath" ,  \
#	"lib/hsqldb.jar" ,  \
#	"org.hsqldb.server.Server" ,  \
#	"-database.0" ,  \
#	"file:/testdb", \
#	"-dbname.0" ,  \
#	"testdb" \
#]
