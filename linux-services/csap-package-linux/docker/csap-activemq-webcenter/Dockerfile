#
#  docker tests
#

FROM webcenter/activemq


#add wrapper.conf /opt/activemq/bin/linux-x86-64/wrapper.conf
ADD env /opt/activemq/bin/env

#env csapJmxOpts="-Dcsap -Djava.rmi.server.hostname=centos1 -Dcom.sun.management.jmxremote.port=8162 -Dcom.sun.management.jmxremote.rmi.port=8162 -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false -Dcom.sun.management.jmxremote.local.only=false"
#expose 8163

# csap docker launcher will fully configure jmx and export port if it finds -DcsapJmxPort
ENV csapJmxOpts="-DcsapJmxPort=8162"

