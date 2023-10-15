#
#
#
FROM rockylinux/rockylinux:8
#FROM centos:8


ARG jdkDistribution="openjdk-17.0.2_linux-x64_bin.tar.gz"
ARG packageServer="http://csap-dev01.yourcompany.org"
ARG javaVersion="openjdk-17.0.2"
ARG javaHome="/java-local/$javaVersion"

#
# note: reference ${PATH} from env will cause windows maven builds to fail
#
ENV JAVA_HOME="$javaHome" \
	PATH="$javaHome/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin" \
	startCommand="java" \
	javaOptions="-version" \
	javaTarget="" \
	SPACER="\n___________________________________\n\n"

SHELL ["/bin/bash", "-c"]

RUN echo -e "$SPACER \
	shell:  $SHELL \n \
	path: $PATH \n \
	$SPACER"

RUN echo -e "$SPACER \
	installing:  core os tools $SPACER" && \
	yum -y install wget which unzip && \
	echo -e "$SPACER"


	
#	&& yum -y update bind-license


WORKDIR "/java-local"	
ADD $packageServer/java/$jdkDistribution .

RUN echo -e "$SPACER \
	installing:  java $SPACER" && \
	tar -xzf openjdk*.tar.gz && \
	rm -rf *.tar.gz && \
	mv jdk* $javaVersion


ADD runJava.sh .
RUN chmod +x runJava.sh

ENTRYPOINT [ \
	"/bin/sh",  \
	"-c", \
	"/java-local/runJava.sh" \
]

	