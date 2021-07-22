package org.csap.agent.stats.service ;

import com.fasterxml.jackson.databind.ObjectMapper ;
import com.fasterxml.jackson.databind.node.ObjectNode ;

public enum JmxCommonEnum {

	cpuPercent("cpuPercent"), httpConnections(JmxCommonEnum.TOMCAT_CONNECTIONS),
	httpRequestCount("httpRequestCount"), httpProcessingTime("httpProcessingTime"),
	httpKbytesReceived("httpKbytesReceived"), httpKbytesSent("httpKbytesSent"),
	sessionsCount("sessionsCount"), sessionsActive("sessionsActive"),
	openFiles("openFiles"),

	minorGcInMs("minorGcInMs"), majorGcInMs("majorGcInMs"),

	heapUsed("heapUsed"), heapMax("heapMax"),

	threadsBusy("tomcatThreadsBusy"), threadCount("tomcatThreadCount"),
	jvmThreadCount(JmxCommonEnum.JVM_THREAD_COUNT),
	jvmThreadsMax("jvmThreadsMax");

	public final static String TOMCAT_CONNECTIONS = "tomcatConnections" ;
	public final static String JVM_THREAD_COUNT = "jvmThreadCount" ;

	public String value ;

	public boolean isTomcatOnly ( ) {

		if ( this == httpConnections ||
				this == httpRequestCount ||
				this == httpProcessingTime ||
				this == httpKbytesReceived ||
				this == httpKbytesSent ||
				this == sessionsCount ||
				this == sessionsActive ||
				this == threadCount ||
				this == threadsBusy )
			return true ;

		return false ;

	}

	static ObjectMapper jacksonMapper = new ObjectMapper( ) ;

	static public ObjectNode graphLabels ( ) {

		ObjectNode labels = jacksonMapper.createObjectNode( ) ;

		for ( JmxCommonEnum jmx : JmxCommonEnum.values( ) ) {

			switch ( jmx ) {

			case cpuPercent:
				labels.put( jmx.value, "Cpu %" ) ;
				break ;
			case httpConnections:
				labels.put( jmx.value, "Tomcat Connections" ) ;
				break ;
			case httpRequestCount:
				labels.put( jmx.value, "Http Requests" ) ;
				break ;
			case httpProcessingTime:
				labels.put( jmx.value, "Http Processing Time (ms)" ) ;
				break ;
			case httpKbytesReceived:
				labels.put( jmx.value, "Http Bytes Received (KB)" ) ;
				break ;
			case httpKbytesSent:
				labels.put( jmx.value, "Http Bytes Sent (KB)" ) ;
				break ;
			case sessionsCount:
				labels.put( jmx.value, "New User Sessions" ) ;
				break ;
			case sessionsActive:
				labels.put( jmx.value, "Users Active" ) ;
				break ;
			case openFiles:
				labels.put( jmx.value, "Open Files" ) ;
				break ;
			case minorGcInMs:
				labels.put( jmx.value, "Minor Garbage Col (ms)" ) ;
				break ;
			case majorGcInMs:
				labels.put( jmx.value, "Major Garbage Col (ms)" ) ;
				break ;
			case heapUsed:
				labels.put( jmx.value, "Heap Used (MB)" ) ;
				break ;
			case heapMax:
				labels.put( jmx.value, "Heap Max (MB)" ) ;
				break ;
			case threadsBusy:
				labels.put( jmx.value, "Tomcat Threads Busy" ) ;
				break ;
			case threadCount:
				labels.put( jmx.value, "Tomcat Thread Count" ) ;
				break ;
			case jvmThreadCount:
				labels.put( jmx.value, "Java Threads" ) ;
				break ;
			case jvmThreadsMax:
				labels.put( jmx.value, "Java Max Threads" ) ;
				break ;
			default:
				throw new AssertionError( jmx.name( ) ) ;

			}

		}

		return labels ;

	}

	private JmxCommonEnum ( String value ) {

		this.value = value ;

	}
};
