FROM csapplatform/installer:latest


ARG agentPort="9011"

#
# --chmod=755  requires buildkit installed
#
COPY csap-run.sh /root
COPY install-and-run.sh /root

RUN chmod +x /root/*.sh

ENV agentPort=${agentPort} \
	dockerHostFqdn="container" \
	webUser="csap" \
	webPass="csap"
	

#
# agent http, and csap-verify-service
#
EXPOSE ${agentPort} 9021


VOLUME /opt/csap


ENTRYPOINT ./install-and-run.sh


#
#ARG osPackages="wget unzip  sysstat sudo net-tools"
#
#RUN echo -e "$SPACER \
#	shell:  $SHELL \n \
#	path: $PATH \n \
#	installing: $osPackages \n \
#	$SPACER" && \
#	yum --assumeyes install $osPackages
#
#
#ARG csapZip="csap-host-2-SNAPSHOT.zip"
#ARG csapUser="csap"
#ARG agentPort="9011"
#
#
#
#ARG packageServer="http://my-server-01.yourcompany.org:8081/artifactory/csap-snapshots/org/csap/csap-host/2-SNAPSHOT"
##ARG packageServer="http://my-server-01.yourcompany.org:8081/artifactory/csap-release/org/csap/csap-host/21.08/csap-host-21.08.zip"
#
#
#
#
#WORKDIR /root
#
#
#
#RUN echo -e "$SPACER \
#	adding:  $packageServer/$csapZip \n \
#	$SPACER"
#	
#ADD $packageServer/$csapZip $csapZip
#
#
#
#
#
#RUN echo -e "$SPACER \
#	Extracting linux package \n \
#	$SPACER" && \
#	unzip  -j $csapZip csap-platform/packages/csap-package-linux.zip && \
#	echo -e "$SPACER \
#	Extracting installer folder \n \
#	$SPACER" && \
#	unzip -qq csap-package-linux.zip installer/*
#	
#
#
#RUN echo -e "$SPACER \
#	Running installer \n \
#	$SPACER" && \
#	./installer/install.sh -noPrompt -ignorePreflight -skipOs -dockerContainer  -installDisk default  -installCsap default -csapDefinition default
#
#
#USER $csapUser
#
##
## normally set in ServiceOsManager.java
##
#ENV HOME="/opt/$csapUser" \
#	USER="csap" \
#	hostUrlPattern="http://docker_host:docker_public_port/" \
#	csapCookieSSO="csapDockerSSO" \
#	csapCookieSession="csapDockerSession" \
#	csapName="csap-agent" \
#	csapLife="dev" \
#	csapHttpPort=${agentPort} \
#	csapServer="SpringBoot" \
#	csapPlatformWorking="/opt/csap/csap-platform" \
#	csapWorkingDir="/opt/csap/csap-platform/csap-agent" \
#	csapLogDir="/opt/csap/csap-platform/csap-agent/logs" \
#	svcSpawn="yes" \
#	csapParams="-Dspring.profiles.active=dev,agent,company -Xmx512M"
#
#WORKDIR $HOME
#
#
#EXPOSE ${agentPort}
#
## Debuging:
## 	alias de='docker exec -e COLUMNS="`tput cols`" -e LINES="`tput lines`" -it '
## 	de <containerName> bash 
##
#ENTRYPOINT [ \
#	"/bin/sh",  \
#	"-c", \
#	"echo $HOME && \
#         source $HOME/.bashrc && \
#         replace_all_in_file csap_def_template_host $(hostname --short) $csapDefinitionFolder/*-project.json true  && \
#         csap-start.sh && \
#         tail --follow=name --retry $csapLogDir/console.log"  \
#]
#

