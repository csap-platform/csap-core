#
#  kuberntes verification tests
#
#  https://hub.docker.com/r/fabric8/java-centos-openjdk8-jdk
#
FROM fabric8/java-centos-openjdk8-jdk

#FROM yourcompany/openjdk8-jdk

#ADD http://csap-dev01.yourcompany.org/activemq/apache-activemq-5.15.6-bin.tar.gz /opt

ADD http://csap-dev01.yourcompany.org/activemq/apache-activemq-5.15.6-bin.tar.gz /opt/activemq.tar.gz

WORKDIR /opt

RUN tar -xzf /opt/activemq.tar.gz
RUN ln -s apache-activemq-5.15.6 activemq
ENV JAVA_OPTIONS="-Xms64M -Xmx1G -Djava.util.logging.config.file=logging.properties -Djava.security.auth.login.config=/opt/activemq/conf.tmp/login.config -Djava.awt.headless=true -Djava.io.tmpdir=/opt/activemq//tmp -Dactivemq.classpath=/opt/activemq/conf.tmp:/opt/activemq//../lib/: -Dactivemq.home=/opt/activemq/ -Dactivemq.base=/opt/activemq/ -Dactivemq.conf=/opt/activemq/conf.tmp -Dactivemq.data=/data/activemq"
ENV JAVA_APP_JAR="/opt/apache-activemq-5.15.6/activemq-all-5.15.6.jar"

EXPOSE 1883
EXPOSE 5672
EXPOSE 8161
EXPOSE 61613
EXPOSE 61614
EXPOSE 61616

VOLUME /data
WORKDIR /opt/apache-activemq-5.15.6/bin/linux-x86-64
ENTRYPOINT [ \
	"./activemq" , \
	"console" \
]

# http://csap-dev03.yourcompany.org:8161/api/jolokia/read/org.apache.activemq:type=Broker,brokerName=localhost,destinationType=Queue,destinationName=csap-test-csap-dev03

