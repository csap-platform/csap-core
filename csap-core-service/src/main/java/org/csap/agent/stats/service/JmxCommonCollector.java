package org.csap.agent.stats.service ;

import java.io.IOException ;
import java.util.HashMap ;
import java.util.Map ;
import java.util.Set ;

import javax.management.AttributeNotFoundException ;
import javax.management.InstanceNotFoundException ;
import javax.management.MBeanException ;
import javax.management.MBeanServerConnection ;
import javax.management.MalformedObjectNameException ;
import javax.management.ObjectInstance ;
import javax.management.ObjectName ;
import javax.management.ReflectionException ;
import javax.management.openmbean.CompositeData ;

import org.csap.helpers.CSAP ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;

import com.fasterxml.jackson.databind.ObjectMapper ;
import com.fasterxml.jackson.databind.node.ObjectNode ;

public class JmxCommonCollector {

	final Logger logger = LoggerFactory.getLogger( JmxCommonCollector.class ) ;

	ObjectMapper jacksonMapper = new ObjectMapper( ) ;

	/**
	 *
	 * Key Attributes are collected from every JVM unless configuration explicity
	 * disables for service
	 *
	 *
	 */
	public void collect ( MBeanServerConnection mbeanConn , ServiceCollectionResults collectionResults )
		throws Exception {

		String serviceId = collectionResults
				.getServiceInstance( )
				.getName( ) ;
		// .getServiceName_Port() ;

		collectJavaCoreMetrics( mbeanConn, collectionResults ) ;

		collectJavaHeapMetrics( mbeanConn, collectionResults, serviceId ) ;

		if ( collectionResults
				.getServiceInstance( )
				.is_java_application_server( ) ) {

			collectTomcatConnections( mbeanConn, collectionResults, serviceId ) ;

			collectTomcatRequestData( mbeanConn, collectionResults, serviceId ) ;

		}

	}

	private void collectJavaCoreMetrics ( MBeanServerConnection mbeanConn , ServiceCollectionResults jmxResults )
		throws Exception {

		// cpu
		// http://docs.oracle.com/javase/7/docs/jre/api/management/extension/com/sun/management/OperatingSystemMXBean.html
		String mbeanName = "java.lang:type=OperatingSystem" ;

		//
		// Seems to be race condition when same ProcessCpuLoad is
		// pulled from the same connection.
		String attributeName = "ProcessCpuLoad" ;
		Double cpuDouble ;

		try {

			cpuDouble = (Double) mbeanConn.getAttribute( new ObjectName( mbeanName ), attributeName ) ;

			jmxResults.setCpuPercent( Math.round( cpuDouble * 100 ) ) ;

			logger.debug( "cpuDouble: {}", cpuDouble ) ;

		} catch ( Exception e ) {

			logger.debug( "Failed to get ProcessCpuLoad", e ) ;

		}

		// **************** Open Files
		// logger.error("\n\n\t ************** Sleeping for testing JMX timeouts
		// ***********");
		// Thread.sleep(5000); // For testing timeouts only
		try {

			mbeanName = "java.lang:type=OperatingSystem" ;
			attributeName = "OpenFileDescriptorCount" ;
			jmxResults.setOpenFiles( (Long) mbeanConn.getAttribute(
					new ObjectName( mbeanName ), attributeName ) ) ;

		} catch ( Exception e ) {

			logger.debug( "When run on Windows - this does not exist." ) ;

		}

		// **************** JVM threads
		mbeanName = "java.lang:type=Threading" ;
		attributeName = "ThreadCount" ;
		jmxResults.setJvmThreadCount( (int) mbeanConn.getAttribute(
				new ObjectName( mbeanName ), attributeName ) ) ;

		mbeanName = "java.lang:type=Threading" ;
		attributeName = "PeakThreadCount" ;
		jmxResults.setJvmThreadMax( (int) mbeanConn.getAttribute( new ObjectName(
				mbeanName ), attributeName ) ) ;

	}

	private void collectTomcatRequestData (
											MBeanServerConnection mbeanConn ,
											ServiceCollectionResults jmxResults ,
											String serviceNamePort )
		throws IOException ,
		MalformedObjectNameException ,
		MBeanException ,
		AttributeNotFoundException ,
		InstanceNotFoundException ,
		ReflectionException {

		// **************** Tomcat Global processor: collect http stats
		// Multiple connections ajp and http, add all together for graphs
		String tomcatJmxRoot = jmxResults.getServiceInstance( ).getTomcatJmxName( ) ;

		String mbeanName = tomcatJmxRoot + ":type=GlobalRequestProcessor,name=*" ;

		Set<ObjectInstance> tomcatGlobalRequestBeans = mbeanConn.queryMBeans(
				new ObjectName( mbeanName ), null ) ;

		for ( ObjectInstance tomcatConnectionInstance : tomcatGlobalRequestBeans ) {

			logger.debug( "Service: {} ObjectName: {}", serviceNamePort, tomcatConnectionInstance.getObjectName( ) ) ;

			String frontKey = serviceNamePort + tomcatConnectionInstance.getObjectName( ) ;

			long deltaCollected = jmxDelta( frontKey + "requestCount",
					(int) mbeanConn.getAttribute( tomcatConnectionInstance.getObjectName( ), "requestCount" ) ) ;

			jmxResults.setHttpRequestCount( jmxResults.getHttpRequestCount( ) + deltaCollected ) ;

			deltaCollected = jmxDelta( frontKey + "processingTime",
					(long) mbeanConn.getAttribute( tomcatConnectionInstance.getObjectName( ), "processingTime" ) ) ;

			jmxResults.setHttpProcessingTime( jmxResults.getHttpProcessingTime( ) + deltaCollected ) ;

			deltaCollected = jmxDelta( frontKey + "bytesReceived",
					(long) mbeanConn.getAttribute( tomcatConnectionInstance.getObjectName( ), "bytesReceived" ) ) ;
			jmxResults.setHttpBytesReceived( jmxResults.getHttpBytesReceived( ) + ( deltaCollected / 1024 ) ) ;

			deltaCollected = jmxDelta( frontKey + "bytesSent",
					(long) mbeanConn.getAttribute( tomcatConnectionInstance.getObjectName( ), "bytesSent" ) ) ;
			jmxResults.setHttpBytesSent( jmxResults.getHttpBytesSent( ) + ( deltaCollected / 1024 ) ) ;

		}

		// There may be multiple wars deployed...so add them all together
		String sessionMbeanName = tomcatJmxRoot + ":type=Manager,host=localhost,context=*" ;

		Set<ObjectInstance> tomcatManagerBeans = mbeanConn.queryMBeans(
				new ObjectName( sessionMbeanName ), null ) ;

		for ( ObjectInstance warDeployedInstance : tomcatManagerBeans ) {

			logger.debug( "Service: {} ObjectName: {}", serviceNamePort, warDeployedInstance.getObjectName( ) ) ;

			try {

				long sessionsActive = (int) mbeanConn
						.getAttribute( warDeployedInstance.getObjectName( ), "activeSessions" ) ;

				// active http sessions
				jmxResults.setSessionsActive( sessionsActive + jmxResults.getSessionsActive( ) ) ;

				// Use deltas, then we can track sessions per day
				String frontKey = serviceNamePort + warDeployedInstance.getObjectName( ) ;

				long sessionCount = (long) mbeanConn.getAttribute( warDeployedInstance.getObjectName( ),
						"sessionCounter" ) ;
				long deltaCollected = jmxDelta( frontKey + "sessionCounter", sessionCount ) ;

				logger
						.debug( "{}  sessionsActive: {} sessionCount: {} delta: {}", frontKey, sessionsActive,
								sessionCount, deltaCollected ) ;

				jmxResults.setSessionsCount( jmxResults.getSessionsCount( ) + deltaCollected ) ;

			} catch ( Exception e ) {

				logger.error( "Failed to collect session data for service: {}, reason: {}",
						serviceNamePort, CSAP.buildCsapStack( e ) ) ;

			}

		}

	}

	private void collectTomcatConnections (
											MBeanServerConnection mbeanConn ,
											ServiceCollectionResults jmxResults ,
											String serviceNamePort )
		throws IOException ,
		MalformedObjectNameException ,
		MBeanException ,
		AttributeNotFoundException ,
		InstanceNotFoundException ,
		ReflectionException {

		String mbeanName ;

		String tomcatJmxRoot = jmxResults.getServiceInstance( ).getTomcatJmxName( ) ;

		// **************** Tomcat connections
		mbeanName = tomcatJmxRoot + ":type=ThreadPool,name=*" ;
		Set<ObjectInstance> tomcatThreadPoolBeans = mbeanConn.queryMBeans(
				new ObjectName( mbeanName ), null ) ;

		for ( ObjectInstance objectInstance : tomcatThreadPoolBeans ) {

			logger.debug( "Service: {} ObjectName: {}", serviceNamePort, objectInstance.getObjectName( ) ) ;

			if ( jmxResults.getHttpConn( ) < 0 ) {

				// get rid of -10 init
				jmxResults.setHttpConn( 0 ) ;
				jmxResults.setThreadsBusy( 0 ) ;
				jmxResults.setThreadCount( 0 ) ;

			}

			try {

				jmxResults.setHttpConn( jmxResults.getHttpConn( ) + (Long) mbeanConn.getAttribute(
						objectInstance.getObjectName( ),
						"connectionCount" ) ) ;

			} catch ( Exception e ) {

				// tomcat 6 might not have.
				logger.debug( "Failed to get jmx data for service: {} Reason: {}", serviceNamePort, e.getMessage( ) ) ;

			}

			jmxResults.setThreadsBusy( jmxResults.getThreadsBusy( ) + (int) mbeanConn.getAttribute(
					objectInstance.getObjectName( ),
					"currentThreadsBusy" ) ) ;

			jmxResults.setThreadCount( jmxResults.getThreadCount( ) + (int) mbeanConn.getAttribute(
					objectInstance.getObjectName( ),
					"currentThreadCount" ) ) ;

		}

	}

	private ObjectNode deltaLastCollected = jacksonMapper.createObjectNode( ) ;

	private long jmxDelta ( String key , long collectedMetricAsLong ) {

		logger.debug( "Service: {} , collectedMetricAsLong: {}", key, collectedMetricAsLong ) ;

		long last = collectedMetricAsLong ;

		if ( deltaLastCollected.has( key ) ) {

			collectedMetricAsLong = collectedMetricAsLong - deltaLastCollected
					.get( key )
					.asLong( ) ;

			if ( collectedMetricAsLong < 0 ) {

				collectedMetricAsLong = 0 ;

			}

		} else {

			collectedMetricAsLong = 0 ;

		}

		deltaLastCollected.put( key, last ) ;

		return collectedMetricAsLong ;

	}

	Map<String, Long> lastMinorGcMap = new HashMap<String, Long>( ) ;
	Map<String, Long> lastMajorGcMap = new HashMap<String, Long>( ) ;

	private void collectJavaHeapMetrics (
											MBeanServerConnection mbeanConn ,
											ServiceCollectionResults jmxResults ,
											String serviceNamePort )
		throws Exception {

		// **************** Memory
		String mbeanName = "java.lang:type=Memory" ;
		String attributeName = "HeapMemoryUsage" ;

		CompositeData resultData = (CompositeData) mbeanConn
				.getAttribute( new ObjectName( mbeanName ),
						attributeName ) ;

		jmxResults.setHeapUsed( Long
				.parseLong( resultData
						.get( "used" )
						.toString( ) )
				/ 1024 / 1024 ) ;

		jmxResults.setHeapMax( Long.parseLong( resultData
				.get( "max" )
				.toString( ) )
				/ 1024 / 1024 ) ;

		// **************** GarbageCollection
		Set<ObjectInstance> gcBeans = mbeanConn.queryMBeans(
				new ObjectName( "java.lang:type=GarbageCollector,name=*" ), null ) ;

		for ( ObjectInstance objectInstance : gcBeans ) {

			long gcCount = -1 ;
			long gcCollectionTime = -1 ;

			try {

				gcCount = (Long) mbeanConn.getAttribute(
						objectInstance.getObjectName( ),
						"CollectionCount" ) ;
				gcCollectionTime = (Long) mbeanConn.getAttribute(
						objectInstance.getObjectName( ),
						"CollectionTime" ) ;

				// jmxResults.setHttpConn();
			} catch ( Exception e ) {

				// tomcat 6 might not have.
				logger.debug( "Failed to get jmx data for service: {}, Reason: {}", serviceNamePort, e.getMessage( ) ) ;

			}

			//
			// There are several different GC algorithms, the name is used to ID
			// if current object is major or minor
			//
			boolean isMajor = false ;
			String gcBeanName = objectInstance
					.getObjectName( )
					.toString( ) ;

			if ( gcBeanName.contains( "Mark" ) || gcBeanName.contains( "Old" ) ) {

				isMajor = true ;

			}

			logger.debug( "Service: {} , gcBean: {} , gcCount: {}, isMajor: {}, gcCollectionTime: {} ",
					serviceNamePort, gcBeanName, gcCount, isMajor, gcCollectionTime ) ;

			// We show incremental times on UI - making any activity show up as
			// greater then 0
			long lastTime = gcCollectionTime ;

			if ( isMajor ) {

				if ( lastMajorGcMap.containsKey( serviceNamePort ) ) {

					lastTime = lastMajorGcMap.get( serviceNamePort ) ;

				}

				long delta = gcCollectionTime - lastTime ;

				if ( delta < 0 ) {

					delta = -1 ;

				}

				jmxResults.setMajorGcInMs( delta ) ;
				lastMajorGcMap.put( serviceNamePort, gcCollectionTime ) ;

			} else {

				if ( lastMinorGcMap.containsKey( serviceNamePort ) ) {

					lastTime = lastMinorGcMap.get( serviceNamePort ) ;

				}

				long delta = gcCollectionTime - lastTime ;

				if ( delta < 0 ) {

					delta = -1 ;

				}

				jmxResults.setMinorGcInMs( delta ) ;

				lastMinorGcMap.put( serviceNamePort, gcCollectionTime ) ;

			}

		}

	}

}
