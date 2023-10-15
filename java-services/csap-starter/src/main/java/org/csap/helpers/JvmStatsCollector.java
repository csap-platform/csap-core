package org.csap.helpers ;

import java.io.IOException ;
import java.lang.management.ManagementFactory ;
import java.util.HashMap ;
import java.util.Map ;
import java.util.Set ;

import javax.management.AttributeNotFoundException ;
import javax.management.InstanceNotFoundException ;
import javax.management.MBeanException ;
import javax.management.MBeanServer ;
import javax.management.MBeanServerConnection ;
import javax.management.MalformedObjectNameException ;
import javax.management.ObjectInstance ;
import javax.management.ObjectName ;
import javax.management.ReflectionException ;
import javax.management.openmbean.CompositeData ;

import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;

import com.fasterxml.jackson.databind.ObjectMapper ;
import com.fasterxml.jackson.databind.node.ObjectNode ;

/**
 * Class to collect core JVM metrics
 * 
 * @author pnightin
 *
 */
public class JvmStatsCollector {

	final static Logger logger = LoggerFactory.getLogger( JvmStatsCollector.class ) ;

	public JvmStatsCollector ( ) {

	}

	ObjectMapper jacksonMapper = new ObjectMapper( ) ;

	public String TOMCAT_JMX_NAME = "Tomcat" ; // SpringBoot, wars are Catalina
	public String CACHE_KEY = "JavaPerformance" ;
	public static final long SECOND_IN_MS = 1000 ;
	public static final long MINUTE_IN_MS = 60 * 1000 ;

	public CollectionResults performCollection ( ) {

		MBeanServerConnection mbeanConn = (MBeanServer) ManagementFactory
				.getPlatformMBeanServer( ) ;

		CollectionResults results = new CollectionResults( ) ;

		try {

			collectJavaCoreMetrics( mbeanConn, results ) ;
			collectJavaHeapMetrics( mbeanConn, results ) ;
			collectTomcatConnections( mbeanConn, results ) ;
			collectTomcatRequestData( mbeanConn, results ) ;

		} catch ( Exception e ) {

			logger.error( "Failed to collect data", e ) ;

		}

		return results ;

	}

	static Map<String, Long> lastMinorGcMap = new HashMap<String, Long>( ) ;
	static Map<String, Long> lastMajorGcMap = new HashMap<String, Long>( ) ;

	private void collectJavaHeapMetrics ( MBeanServerConnection mbeanConn , CollectionResults results )
		throws Exception {

		// **************** Memory
		String mbeanName = "java.lang:type=Memory" ;
		String attributeName = "HeapMemoryUsage" ;

		CompositeData resultData = (CompositeData) mbeanConn
				.getAttribute( new ObjectName( mbeanName ),
						attributeName ) ;

		results.setHeapUsed( Long
				.parseLong( resultData
						.get( "used" )
						.toString( ) )
				/ 1024 / 1024 ) ;

		results.setHeapMax( Long.parseLong( resultData
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
				logger.debug( "Failed to get jmx data for service: {}, Reason: {}", e.getMessage( ) ) ;

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

			logger.debug( " gcBean: {} , gcCount: {}, isMajor: {}, gcCollectionTime: {} ",
					gcBeanName, gcCount, isMajor, gcCollectionTime ) ;

			// We show incremental times on UI - making any activity show up as
			// greater then 0
			long lastTime = gcCollectionTime ;
			String collectionKey = "keyForDelta" ;

			if ( isMajor ) {

				if ( lastMajorGcMap.containsKey( collectionKey ) ) {

					lastTime = lastMajorGcMap.get( collectionKey ) ;

				}

				long delta = gcCollectionTime - lastTime ;

				if ( delta < 0 ) {

					delta = -1 ;

				}

				results.setMajorGcInMs( delta ) ;
				lastMajorGcMap.put( collectionKey, gcCollectionTime ) ;

			} else {

				if ( lastMinorGcMap.containsKey( collectionKey ) ) {

					lastTime = lastMinorGcMap.get( collectionKey ) ;

				}

				long delta = gcCollectionTime - lastTime ;

				if ( delta < 0 ) {

					delta = -1 ;

				}

				results.setMinorGcInMs( delta ) ;

				lastMinorGcMap.put( collectionKey, gcCollectionTime ) ;

			}

		}

	}

	private void collectJavaCoreMetrics ( MBeanServerConnection mbeanConn , CollectionResults results )
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

			results.setCpuPercent( Math.round( cpuDouble * 100 ) ) ;

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
			results.setOpenFiles( (Long) mbeanConn.getAttribute(
					new ObjectName( mbeanName ), attributeName ) ) ;

		} catch ( Exception e ) {

			logger.debug( "When run on Windows - this does not exist." ) ;

		}

		// **************** JVM threads
		mbeanName = "java.lang:type=Threading" ;
		attributeName = "ThreadCount" ;
		results.setJvmThreadCount( (int) mbeanConn.getAttribute(
				new ObjectName( mbeanName ), attributeName ) ) ;

		mbeanName = "java.lang:type=Threading" ;
		attributeName = "PeakThreadCount" ;
		results.setJvmThreadMax( (int) mbeanConn.getAttribute( new ObjectName(
				mbeanName ), attributeName ) ) ;

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

	private void collectTomcatRequestData ( MBeanServerConnection mbeanConn , CollectionResults results )
		throws IOException ,
		MalformedObjectNameException ,
		MBeanException ,
		AttributeNotFoundException ,
		InstanceNotFoundException ,
		ReflectionException {

		// **************** Tomcat Global processor: collect http stats
		// Multiple connections ajp and http, add all together for graphs

		String mbeanName = TOMCAT_JMX_NAME + ":type=GlobalRequestProcessor,name=*" ;

		Set<ObjectInstance> tomcatGlobalRequestBeans = mbeanConn.queryMBeans(
				new ObjectName( mbeanName ), null ) ;

		for ( ObjectInstance tomcatConnectionInstance : tomcatGlobalRequestBeans ) {

			logger.debug( "Service: {} ObjectName: {}", "JavaPerformance", tomcatConnectionInstance.getObjectName( ) ) ;

			String frontKey = CACHE_KEY + tomcatConnectionInstance.getObjectName( ) ;

			long deltaCollected = jmxDelta( frontKey + "requestCount",
					(int) mbeanConn.getAttribute( tomcatConnectionInstance.getObjectName( ), "requestCount" ) ) ;

			results.setHttpRequestCount( results.getHttpRequestCount( ) + deltaCollected ) ;

			deltaCollected = jmxDelta( frontKey + "processingTime",
					(long) mbeanConn.getAttribute( tomcatConnectionInstance.getObjectName( ), "processingTime" ) ) ;

			results.setHttpProcessingTime( results.getHttpProcessingTime( ) + deltaCollected ) ;

			deltaCollected = jmxDelta( frontKey + "bytesReceived",
					(long) mbeanConn.getAttribute( tomcatConnectionInstance.getObjectName( ), "bytesReceived" ) ) ;
			results.setHttpBytesReceived( results.getHttpBytesReceived( ) + ( deltaCollected / 1024 ) ) ;

			deltaCollected = jmxDelta( frontKey + "bytesSent",
					(long) mbeanConn.getAttribute( tomcatConnectionInstance.getObjectName( ), "bytesSent" ) ) ;
			results.setHttpBytesSent( results.getHttpBytesSent( ) + ( deltaCollected / 1024 ) ) ;

		}

		// There may be multiple wars deployed...so add them all together
		String sessionMbeanName = TOMCAT_JMX_NAME + ":type=Manager,host=localhost,context=*" ;

		Set<ObjectInstance> tomcatManagerBeans = mbeanConn.queryMBeans(
				new ObjectName( sessionMbeanName ), null ) ;

		for ( ObjectInstance warDeployedInstance : tomcatManagerBeans ) {

			logger.debug( "Service: {} ObjectName: {}", CACHE_KEY, warDeployedInstance.getObjectName( ) ) ;

			try {

				long sessionsActive = (int) mbeanConn
						.getAttribute( warDeployedInstance.getObjectName( ), "activeSessions" ) ;

				// active http sessions
				results.setSessionsActive( sessionsActive + results.getSessionsActive( ) ) ;

				// Use deltas, then we can track sessions per day
				String frontKey = CACHE_KEY + warDeployedInstance.getObjectName( ) ;

				long sessionCount = (long) mbeanConn.getAttribute( warDeployedInstance.getObjectName( ),
						"sessionCounter" ) ;
				long deltaCollected = jmxDelta( frontKey + "sessionCounter", sessionCount ) ;

				logger
						.debug( "{}  sessionsActive: {} sessionCount: {} delta: {}", frontKey, sessionsActive,
								sessionCount, deltaCollected ) ;

				results.setSessionsCount( results.getSessionsCount( ) + deltaCollected ) ;

			} catch ( Exception e ) {

				logger.error( "Failed to collect session", e ) ;

			}

		}

	}

	private void collectTomcatConnections ( MBeanServerConnection mbeanConn , CollectionResults results )
		throws IOException ,
		MalformedObjectNameException ,
		MBeanException ,
		AttributeNotFoundException ,
		InstanceNotFoundException ,
		ReflectionException {

		String mbeanName ;

		String tomcatJmxRoot = TOMCAT_JMX_NAME ;

		// **************** Tomcat connections
		mbeanName = tomcatJmxRoot + ":type=ThreadPool,name=*" ;
		Set<ObjectInstance> tomcatThreadPoolBeans = mbeanConn.queryMBeans(
				new ObjectName( mbeanName ), null ) ;

		for ( ObjectInstance objectInstance : tomcatThreadPoolBeans ) {

			logger.debug( "Service: {} ObjectName: {}", CACHE_KEY, objectInstance.getObjectName( ) ) ;

			if ( results.getHttpConn( ) < 0 ) {

				// get rid of -10 init
				results.setHttpConn( 0 ) ;
				results.setThreadsBusy( 0 ) ;
				results.setThreadCount( 0 ) ;

			}

			try {

				results.setHttpConn( results.getHttpConn( ) + (Long) mbeanConn.getAttribute(
						objectInstance.getObjectName( ),
						"connectionCount" ) ) ;

			} catch ( Exception e ) {

				// tomcat 6 might not have.
				logger.debug( "Failed to get jmx data for service: {} Reason: {}", CACHE_KEY, e.getMessage( ) ) ;

			}

			results.setThreadsBusy( results.getThreadsBusy( ) + (int) mbeanConn.getAttribute(
					objectInstance.getObjectName( ),
					"currentThreadsBusy" ) ) ;

			results.setThreadCount( results.getThreadCount( ) + (int) mbeanConn.getAttribute(
					objectInstance.getObjectName( ),
					"currentThreadCount" ) ) ;

		}

	}

	public class CollectionResults {
		private long minorGcInMs = 0 ;
		private long majorGcInMs = 0 ;
		private long httpConn = 0 ;
		private long threadsBusy = 0 ;
		private long threadCount = 0 ; // threadCount = 0 means JMX is not
										// responding

		private long httpRequestCount = 0 ;
		private long httpProcessingTime = 0 ;
		private long httpBytesReceived = 0 ;
		private long httpBytesSent = 0 ;

		private long cpuPercent = 0 ;
		private long jvmThreadCount = 0 ;
		private long jvmThreadMax = 0 ;
		private long openFiles = 0 ;
		private long heapUsed = 0 ;
		private long heapMax = 0 ;

		// JEE Sessions
		private long sessionsCount = 0 ;
		private long sessionsActive = 0 ;

		public long getMinorGcInMs ( ) {

			return minorGcInMs ;

		}

		public void setMinorGcInMs ( long minorGcInMs ) {

			this.minorGcInMs = minorGcInMs ;

		}

		public long getMajorGcInMs ( ) {

			return majorGcInMs ;

		}

		public void setMajorGcInMs ( long majorGcInMs ) {

			this.majorGcInMs = majorGcInMs ;

		}

		public long getHttpConn ( ) {

			return httpConn ;

		}

		public void setHttpConn ( long httpConn ) {

			this.httpConn = httpConn ;

		}

		public long getThreadsBusy ( ) {

			return threadsBusy ;

		}

		public void setThreadsBusy ( long threadsBusy ) {

			this.threadsBusy = threadsBusy ;

		}

		public long getThreadCount ( ) {

			return threadCount ;

		}

		public void setThreadCount ( long threadCount ) {

			this.threadCount = threadCount ;

		}

		public long getHttpRequestCount ( ) {

			return httpRequestCount ;

		}

		public void setHttpRequestCount ( long httpRequestCount ) {

			this.httpRequestCount = httpRequestCount ;

		}

		public long getHttpProcessingTime ( ) {

			return httpProcessingTime ;

		}

		public void setHttpProcessingTime ( long httpProcessingTime ) {

			this.httpProcessingTime = httpProcessingTime ;

		}

		public long getHttpBytesReceived ( ) {

			return httpBytesReceived ;

		}

		public void setHttpBytesReceived ( long httpBytesReceived ) {

			this.httpBytesReceived = httpBytesReceived ;

		}

		public long getHttpBytesSent ( ) {

			return httpBytesSent ;

		}

		public void setHttpBytesSent ( long httpBytesSent ) {

			this.httpBytesSent = httpBytesSent ;

		}

		public long getCpuPercent ( ) {

			return cpuPercent ;

		}

		public void setCpuPercent ( long cpuPercent ) {

			this.cpuPercent = cpuPercent ;

		}

		public long getJvmThreadCount ( ) {

			return jvmThreadCount ;

		}

		public void setJvmThreadCount ( long jvmThreadCount ) {

			this.jvmThreadCount = jvmThreadCount ;

		}

		public long getJvmThreadMax ( ) {

			return jvmThreadMax ;

		}

		public void setJvmThreadMax ( long jvmThreadMax ) {

			this.jvmThreadMax = jvmThreadMax ;

		}

		public long getOpenFiles ( ) {

			return openFiles ;

		}

		public void setOpenFiles ( long openFiles ) {

			this.openFiles = openFiles ;

		}

		public long getHeapUsed ( ) {

			return heapUsed ;

		}

		public void setHeapUsed ( long heapUsed ) {

			this.heapUsed = heapUsed ;

		}

		public long getHeapMax ( ) {

			return heapMax ;

		}

		public void setHeapMax ( long heapMax ) {

			this.heapMax = heapMax ;

		}

		@Override
		public String toString ( ) {

			return "\n\t JavaPerformance [minorGcInMs=" + minorGcInMs + ", majorGcInMs=" + majorGcInMs + ", heapUsed="
					+ heapUsed
					+ ", heapMax=" + heapMax +
					",\n\t sessionsActive=" + sessionsActive + ", sessionsCount=" + sessionsCount +
					",\n\t httpConn=" + httpConn + ", httpRequestCount=" + httpRequestCount +
					", httpProcessingTime=" + httpProcessingTime + ", httpBytesReceived=" + httpBytesReceived
					+ ", httpBytesSent="
					+ httpBytesSent +
					",\n\t threadsBusy=" + threadsBusy + ", threadCount=" + threadCount +
					", cpuPercent=" + cpuPercent + ", jvmThreadCount=" + jvmThreadCount + ", jvmThreadMax="
					+ jvmThreadMax + ", openFiles="
					+ openFiles +
					"]" ;

		}

		public long getSessionsCount ( ) {

			return sessionsCount ;

		}

		public void setSessionsCount ( long sessionsCount ) {

			this.sessionsCount = sessionsCount ;

		}

		public long getSessionsActive ( ) {

			return sessionsActive ;

		}

		public void setSessionsActive ( long sessionsActive ) {

			this.sessionsActive = sessionsActive ;

		}

	}
}
