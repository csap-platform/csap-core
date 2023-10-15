#
# ref https://springframework.guru/running-spring-boot-in-a-docker-container/
# 


FROM centos

# used in dockerbuild
ENV javaVersion="144"

ENV JAVA_HOME="/java-local/jdk1.8.0_$javaVersion"
ENV PATH="${JAVA_HOME}/bin:${PATH}"

ENV PARENT_HOST="$(hostname)"

RUN echo installing core os tools; \
	yum -y install wget which unzip; \
	yum -y update bind-license

RUN echo installing java \
	mkdir -p /java-local ;
WORKDIR "/java-local"
  
RUN wget -nv --no-cookies --no-check-certificate "http://nightingale-one.davis.yourcompany.lab/java/jdk-8u$javaVersion-linux-x64.tar.gz"; \
	tar -xzf jdk*.tar.gz ;\
	rm -rf jdk*.tar.gz


RUN echo installing java strong encyption ;\
	wget -nv --no-cookies --no-check-certificate "http://nightingale-one.davis.yourcompany.lab/java/jce_policy-8.zip";\
	unzip -qq -o jce*.zip -d jdk*/jre/lib/security; \
	rm -rf jce*.zip; \
	mv -f  jdk*/jre/lib/security/UnlimitedJCEPolicyJDK8/*.jar  jdk*/jre/lib/security



WORKDIR "/"
RUN echo simple java test2

ENTRYPOINT [ \
	"java",  \
	"-version" \
]
