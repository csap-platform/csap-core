#
#
#
FROM rockylinux/rockylinux:8

ARG repoVersion=22.03
ARG repoPath=artifactory/csap-release

#ARG repoVersion=2-SNAPSHOT
#ARG repoPath=artifactory/csap-snapshots

ARG csapZip="csap-host-${repoVersion}.zip"
ARG packageServer="http://my-server-01.yourcompany.org:8081/${repoPath}/org/csap/csap-host/${repoVersion}"


ENV SPACER="\n_________________________________________________________________________________________________________\n\n" \
	csapZip=$csapZip

WORKDIR /root

VOLUME /transfer


RUN echo -e "$SPACER \
	adding:  $packageServer/$csapZip \n \
	$SPACER"
	


ADD $packageServer/$csapZip $csapZip


ENTRYPOINT [ \
    "sh",  \
    "-c", \
    "echo -e $SPACER copying $csapZip to volume /transfer $SPACER && \
    cp --verbose /root/$csapZip /transfer" \
]


