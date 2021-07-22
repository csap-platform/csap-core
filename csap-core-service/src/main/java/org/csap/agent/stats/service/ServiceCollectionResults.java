package org.csap.agent.stats.service ;

import java.util.Iterator ;
import java.util.Map ;

import org.csap.agent.model.ServiceInstance ;
import org.csap.helpers.CSAP ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;

import com.fasterxml.jackson.databind.ObjectMapper ;
import com.fasterxml.jackson.databind.node.ArrayNode ;
import com.fasterxml.jackson.databind.node.ObjectNode ;

/**
 *
 * Results pojo for storing and updating application results - typically used to
 * store JMX values, but also http collected results as well
 *
 * @author someDeveloper
 *
 */
public class ServiceCollectionResults {

	private int inMemoryCacheSize ;

	public ServiceCollectionResults ( ServiceInstance serviceInstance, int inMemoryCacheSize ) {

		this.serviceInstance = serviceInstance ;
		this.inMemoryCacheSize = inMemoryCacheSize ;
		logger.debug( "Custom meters: {}", serviceInstance.hasServiceMeters( ) ) ;

	}

	@Override
	public String toString ( ) {

		return "ApplicationResults [cpuPercent=" + cpuPercent + ", jvmThreadCount=" + jvmThreadCount + ", jvmThreadMax="
				+ jvmThreadMax + ", openFiles=" + openFiles + ", heapUsed=" + heapUsed + ", heapMax=" + heapMax
				+ ", minorGcInMs=" + minorGcInMs + ", majorGcInMs=" + majorGcInMs + ", httpConn=" + httpConn
				+ ", threadsBusy=" + threadsBusy + ", threadCount=" + threadCount + ", httpRequestCount="
				+ httpRequestCount + ", httpProcessingTime=" + httpProcessingTime + ", httpBytesReceived="
				+ httpBytesReceived + ", httpBytesSent=" + httpBytesSent + ", sessionsCount=" + sessionsCount
				+ ", sessionsActive=" + sessionsActive + "\nCustomCollection="
				+ customMap.toString( ) + "]" ;

	}

	final Logger logger = LoggerFactory.getLogger( ServiceCollectionResults.class ) ;

	private long cpuPercent = 0 ;
	private long jvmThreadCount = 0 ;
	private long jvmThreadMax = 0 ;
	private long openFiles = 0 ;
	private long heapUsed = 0 ;
	private long heapMax = 0 ;

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

	private long minorGcInMs = 0 ;
	private long majorGcInMs = 0 ;
	private long httpConn = 0 ;
	private long threadsBusy = 0 ;
	private long threadCount = 0 ; // threadCount = 0 means JMX is not responding

	private long httpRequestCount = 0 ;
	private long httpProcessingTime = 0 ;
	private long httpBytesReceived = 0 ;
	private long httpBytesSent = 0 ;

	// JEE Sessions
	private long sessionsCount = 0 ;
	private long sessionsActive = 0 ;

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

	public long getHttpRequestCount ( ) {

		return httpRequestCount ;

	}

	private ServiceInstance serviceInstance ;

	public ServiceInstance getServiceInstance ( ) {

		return serviceInstance ;

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

	public void setHttpRequestCount ( long requestCount ) {

		this.httpRequestCount = requestCount ;

	}

	public void add_results_to_application_collection (
														Map<String, ObjectNode> latest_application_collection ,
														String serviceId ) {

		if ( ! serviceInstance.hasServiceMeters( ) ) {

			logger.debug( "No Meters found" ) ;
			return ;

		}

		ensure_application_cache_initialized( serviceId, latest_application_collection ) ;

		ObjectNode serviceCustomMetricNode = latest_application_collection.get( serviceId ) ;

		StringBuilder customStorage = new StringBuilder( serviceId + ": \t" ) ;

		CSAP.asStreamHandleNulls( customMap )
				.forEach( metricId -> {

					// logger.info("Adding Custom Results" + metricId) ;
					ArrayNode metricArray = (ArrayNode) serviceCustomMetricNode.get( metricId ) ;

					if ( metricArray == null ) {

						metricArray = serviceCustomMetricNode.putArray( metricId ) ;
						customStorage.append( metricId + ", " ) ;

						// isSomeNewItems = true ;
						// logger.warn(serviceNamePort + " metricArray not
						// initialized for custom attribute not found in result set:
						// " + metricId ) ;
						// continue ;
					}

					if ( ! customMap.has( metricId ) ) {

						logger.warn( "{} custom attribute not found in result set: {}", serviceId, metricId ) ;
						// continue ;
						metricArray.insert( 0, 0 ) ;

					} else if ( metricArray.size( ) == 0 ) {

						// initialize to 0 to support sampling deltas in simon and
						// jmx delta in csap
						metricArray.insert( 0, 0 ) ;

					} else {

						metricArray.insert( 0, customMap.get( metricId ) ) ;

					}

					if ( metricArray.size( ) > inMemoryCacheSize ) {

						metricArray.remove( metricArray.size( ) - 1 ) ;

					}

				} ) ;

		Iterator<String> keyIter = serviceCustomMetricNode.fieldNames( ) ;

		while ( keyIter.hasNext( ) ) {

			String metricName = keyIter.next( ) ;

			if ( ! serviceInstance.hasMeter( metricName ) ) {

				logger.warn( "{} :  Removing metricName: {} - assumed due to definition update.", serviceId,
						metricName ) ;
				keyIter.remove( ) ;

			}

		}

		// if ( isSomeNewItems )
		// logger.info("Custom storage allocated: " + customStorage);
	}

	private long collectedValue ( JmxCommonEnum metric ) {

		if ( metric == JmxCommonEnum.cpuPercent ) {

			return getCpuPercent( ) ;

		}

		if ( metric == JmxCommonEnum.heapMax ) {

			return getHeapMax( ) ;

		}

		if ( metric == JmxCommonEnum.heapUsed ) {

			return getHeapUsed( ) ;

		}

		if ( metric == JmxCommonEnum.httpConnections ) {

			return getHttpConn( ) ;

		}

		if ( metric == JmxCommonEnum.httpKbytesReceived ) {

			return getHttpBytesReceived( ) ;

		}

		if ( metric == JmxCommonEnum.httpKbytesSent ) {

			return getHttpBytesSent( ) ;

		}

		if ( metric == JmxCommonEnum.httpProcessingTime ) {

			return getHttpProcessingTime( ) ;

		}

		if ( metric == JmxCommonEnum.httpRequestCount ) {

			return getHttpRequestCount( ) ;

		}

		if ( metric == JmxCommonEnum.jvmThreadCount ) {

			return getJvmThreadCount( ) ;

		}

		if ( metric == JmxCommonEnum.jvmThreadsMax ) {

			return getJvmThreadMax( ) ;

		}

		if ( metric == JmxCommonEnum.majorGcInMs ) {

			return getMajorGcInMs( ) ;

		}

		if ( metric == JmxCommonEnum.minorGcInMs ) {

			return getMinorGcInMs( ) ;

		}

		if ( metric == JmxCommonEnum.openFiles ) {

			return getOpenFiles( ) ;

		}

		if ( metric == JmxCommonEnum.sessionsActive ) {

			return getSessionsActive( ) ;

		}

		if ( metric == JmxCommonEnum.sessionsCount ) {

			return getSessionsCount( ) ;

		}

		if ( metric == JmxCommonEnum.threadCount ) {

			return getThreadCount( ) ;

		}

		if ( metric == JmxCommonEnum.threadsBusy ) {

			return getThreadsBusy( ) ;

		}

		return 0 ;

	}

	public void add_results_to_java_collection ( Map<String, ObjectNode> latestCollection , String serviceId ) {

		ensure_java_collection_initialized( latestCollection, serviceId ) ;

		ObjectNode serviceCacheNode = latestCollection.get( serviceId ) ;

		for ( JmxCommonEnum metric : JmxCommonEnum.values( ) ) {

			// some jvms are not tomcat, so skip tomcat specific metrics
			if ( metric.isTomcatOnly( ) && ! serviceInstance.is_tomcat_collect( ) ) {

				continue ;

			}

			ArrayNode metricResultsArray = ( (ArrayNode) serviceCacheNode.path( metric.value + "_" + serviceId ) ) ;

			metricResultsArray.insert( 0, collectedValue( metric ) ) ;

			if ( metricResultsArray.size( ) > inMemoryCacheSize ) {

				metricResultsArray.remove( metricResultsArray.size( ) - 1 ) ;

			}

		}

	}

	ObjectMapper jacksonMapper = new ObjectMapper( ) ;

	private void ensure_java_collection_initialized ( Map<String, ObjectNode> latestCollection , String serviceId ) {

		if ( ! latestCollection.containsKey( serviceId ) ) {

			logger.debug( "Creating jmx results storage for: {}", serviceId ) ;

			ObjectNode serviceMetricNode = jacksonMapper.createObjectNode( ) ;
			serviceMetricNode.putArray( "timeStamp" ) ;

			for ( JmxCommonEnum jmxMetric : JmxCommonEnum.values( ) ) {

				// some jvms are not tomcat, so skip tomcat specific metrics
				if ( jmxMetric.isTomcatOnly( ) && ! serviceInstance.is_tomcat_collect( ) ) {

					continue ;

				}

				String metricFullName = jmxMetric.value + "_"
						+ serviceId ;
				serviceMetricNode.putArray( metricFullName ) ;

			}

			latestCollection.put( serviceId,
					serviceMetricNode ) ;

		}

	}

	/**
	 * Used to store application/service specific data. Mostly via JMX, but http as
	 * well
	 * 
	 * @param serviceId
	 *
	 * @param latest_application_collection
	 */
	private void ensure_application_cache_initialized (
														String serviceId ,
														Map<String, ObjectNode> latest_application_collection ) {
		// Check if custom metrics will be collected, and not needing init
		// This needs to by dynamic - based on any changes to service

		// logger.info("Config String" + metricsConfig.toString());
		if ( ! latest_application_collection.containsKey( serviceId )
				|| latest_application_collection.get( serviceId )
						.size( ) != serviceInstance.getServiceMeters( ).size( ) ) {

			ObjectNode serviceCustomMetricNode = latest_application_collection.get( serviceId ) ;

			if ( serviceCustomMetricNode == null ) {

				serviceCustomMetricNode = jacksonMapper.createObjectNode( ) ;

			}

			latest_application_collection.put( serviceId,
					serviceCustomMetricNode ) ;

		}

	}

	// private HashMap<String, Long> customMap = new HashMap<String, Long>();
	private ObjectNode customMap = jacksonMapper.createObjectNode( ) ;

	public void addCustomResultLong ( String metricId , long resultLong ) {

		customMap.put( metricId, resultLong ) ;

	}

	public void addCustomResultDouble ( String metricId , double resultDouble ) {

		customMap.put( metricId, resultDouble ) ;

	}

	public long getCustomResult ( String metricId ) {

		return customMap.get( metricId )
				.asLong( ) ;

	}

}
