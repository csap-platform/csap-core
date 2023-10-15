FROM csap/csap-java9

RUN yum -y install net-tools sysstat openssh-clients

ENV csapInstaller="2.0.0"

ENV USER="csapUser"
ENV HOME="/home/$USER"
WORKDIR $HOME

ADD http://nightingale-one.davis.yourcompany.lab/csap/csap2.0.0.zip $HOME
ADD http://nightingale-one.davis.yourcompany.lab/web.zip $HOME

RUN printf "\n\n ***** Extracting linux package ***\n\n" && \
	unzip  -j csap2.0.0.zip staging/csap-packages/linux.zip && \
	printf "\n\n ***** Extracting installer scripts ***\n\n" && \ 
	unzip -qq linux.zip installer/* && \
	printf "\n\n ***** Creating $USER ***\n\n" && \ 
	groupadd -r $USER && \
	useradd -r -g $USER -d $HOME -s /sbin/nologin -c "Docker image user" $USER && \
	chown -R $USER $HOME


USER $USER

RUN printf "\n\n ***** kicking off csap install into $HOME/csap-base ***\n\n" && \ 
	mkdir $HOME/csap-base && \
	./installer/install.sh -targetFs $HOME/csap-base -noPrompt -csapDefinition defaultAgent

RUN  printf "\n\n ***** setting up user to do installs ***\n\n" && \
	ssh-keygen -t rsa -N "" -f ~/.ssh/id_rsa && \
	chmod 700 .ssh && chmod 600 ~/.ssh/id_rsa && \
	mkdir opensource && mv csap2.0.0.zip opensource
	
# http 
EXPOSE 8011


# adding -DcsapJava8 will trigger csap to  mount java
# -DcsapJmxPort triggers jmx configuration WHEN started from CSAP
#  refer to https://wiki.yourcompany.com/display/SFAECSAP/Csap+Docker
ENV PATH="$HOME/csap-base/csap/staging/bin/:${PATH}"
ENV agentLogs="$HOME/csap-base/csap/processing/CsAgent_8011/logs/consoleLogs.txt"

ENV javaOptions=""
ENV startCommand="admin-restart.sh; sleep 2; tail -f $agentLogs"

