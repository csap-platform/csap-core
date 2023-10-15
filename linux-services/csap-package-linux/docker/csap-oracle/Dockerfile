#
# ref https://springframework.guru/running-spring-boot-in-a-docker-container/
# 


FROM docker.io/peterdnight/csap-base

RUN yum -y install libaio telnet

# used in dockerbuild
ENV oracleVersion="linux.x64-12.1.0.2.0"
ENV oracleInstall="/oracle"
ENV ORACLE_HOME="$oracleInstall/instantclient_12_1"

ENV LD_LIBRARY_PATH="$ORACLE_HOME"
ENV TNS_ADMIN="$ORACLE_HOME"
ENV CLASSPATH=".:$ORACLE_HOME/ojdbc7.jar"

ENV PATH="$ORACLE_HOME:${PATH}"


ENV PARENT_HOST="$(hostname)"

RUN echo installing $ORACLE_HOME
RUN mkdir -p $oracleInstall ;

WORKDIR $oracleInstall


RUN wget -nv --no-cookies --no-check-certificate "http://csaptools.yourcompany.com/oracleClient/instantclient-basic-$oracleVersion.zip"
RUN unzip -qq -o *basic*.zip 
RUN wget -nv --no-cookies --no-check-certificate "http://csaptools.yourcompany.com/oracleClient/instantclient-jdbc-$oracleVersion.zip"
RUN unzip -qq -o *jdbc*.zip 
RUN wget -nv --no-cookies --no-check-certificate "http://csaptools.yourcompany.com/oracleClient/instantclient-sqlplus-$oracleVersion.zip"
RUN unzip -qq -o *sqlplus*.zip 

RUN rm -rf *.zip


WORKDIR $ORACLE_HOME
RUN wget -nv --no-cookies --no-check-certificate "http://csaptools.yourcompany.com/oracleClient/ldap.ora"
RUN wget -nv --no-cookies --no-check-certificate "http://csaptools.yourcompany.com/oracleClient/sqlnet.ora"
#ADD ldap.ora $ORACLE_HOME
#ADD sqlnet.ora $ORACLE_HOME


WORKDIR "/"

# "echo running csap-oracle tests; echo \"path is $PATH\"; java -version; echo 'select count(*) from job_schedule' | sqlplus " "echo running csap-oracle tests; echo \"path is $PATH\"; java -version; echo 'select count(*) from job_schedule' | sqlplus " 
ENV connection="USER/PASS@cafi7dev"
ENTRYPOINT [ \
			"/bin/sh", \
			"-c", \
			"echo running csap-oracle tests; echo \"path is $PATH\"; java -version; echo \"testing oracle connection: $connection\" ; echo 'select 1 from dual' | sqlplus $connection" \
]

