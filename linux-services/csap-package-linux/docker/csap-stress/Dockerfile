FROM centos:centos7

#
# yourcompany-docker.yourcompany.org/stress:latest
# 

RUN printf "\n\n ________ installing core os tools ________\n\n" && \
	yum -y install epel-release && \
	yum clean all && \
	yum -y install wget which unzip && \
	yum -y update bind-license

#RUN printf "\n\n ***** installing core os tools ***\n\n" && \
#	yum -y install epel-release && \ 
#	yum -y update && \ 
#	yum clean all && \
#	yum -y install wget which unzip && \
#	yum -y update bind-license

RUN yum -y install stress

#ENTRYPOINT [ \
#			"/bin/bash", \
#			"-c", \
#			"echo running as `id`; echo \"path is $PATH\"; stress --verbose" \
#]

CMD /usr/bin/stress --cpu 1