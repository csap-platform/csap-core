FROM csap/csap-java9

RUN printf "\n\n ________ Installing core CSAP OS dependencies ________\n\n" && \
	yum -y install net-tools sysstat openssh-clients sudo


ARG csapZip="csap2.0.0.zip"
ARG csapUser="csapUser"
ARG packageServer="http://csap-dev01.davis.yourcompany.lab"

WORKDIR /root

ADD $packageServer/csap/$csapZip .
ADD $packageServer/web.zip .

RUN printf "\n\n ________ Extracting linux package ________\n\n" && \
	unzip  -j $csapZip staging/csap-packages/linux.zip && \
	\
	printf "\n\n ________ Extracting installer scripts ________\n\n" && \ 
	unzip -qq linux.zip installer/* 

RUN printf "\n\n ________ kicking off csap install into $HOME/csap-base ________\n\n" && \ 
	./installer/install.sh -noPrompt -dockerContainer  -installDisk default  -installCsap default -csapDefinition defaultAgent



USER $csapUser

ENV HOME="/opt/$csapUser" \
	hostUrlPattern="http://docker_host:docker_public_port/CsAgent" \
	csapCookieSSO="csapDockerSSO" \
	csapCookieSession="csapDockerSession"

WORKDIR $HOME

RUN printf "\n\n ________ setting up user to do installs ________\n\n" && \
	ssh-keygen -t rsa -N "" -f ~/.ssh/id_rsa && \
	chmod 700 .ssh && chmod 600 ~/.ssh/id_rsa && \
	\
	\
	printf "\n\n ________ creating opensource folder ________\n\n" && \
	mkdir opensource && mv $csapZip opensource && \
	\
	\
	printf "\n\n ________ Disabling hostUrlPattern in defintion ________\n\n" && \
	sed -i 's/hostUrlPattern/hostUrlPatternNotUsed/g' ~/staging/conf/Application.json && \
	sed -i 's/Your Application/Csap In Docker/g' ~/staging/conf/Application.json
 

EXPOSE 8011

#
#	csap-java uses javaOptions and startCommand to configure and run the jvm
# 
ARG agentLogs="$HOME/processing/CsAgent_8011/logs/console.log"
ENV javaOptions="" \
	startCommand="source $HOME/.bashrc && admin-restart.sh && sleep 5 && ls -l $HOME/processing/CsAgent_8011/logs && tail --follow=name $agentLogs"

