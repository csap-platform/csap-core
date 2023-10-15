FROM centos:centos7

RUN printf "\n\n ***** Installing core CSAP OS dependencies ***\n\n" && \
	yum -y install \
	nc \
	openssh-clients \
	bash-completion \
	ca-certificates \
	curl \
	procps-ng \
	rsync \
	socat \
	wget \
	telnet \
	bind-utils \
	net-tools \
	traceroute \
	nmap \
	bind-utils


WORKDIR /root

ADD simple.bashrc .bashrc

ENTRYPOINT []

CMD ["/bin/bash", "-c", "echo To access network tools: 'docker run -it csap/csap-tools bash'"]