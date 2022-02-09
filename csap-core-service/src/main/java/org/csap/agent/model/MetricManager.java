package org.csap.agent.model ;

import java.util.ArrayList ;
import java.util.Collections ;
import java.util.HashMap ;
import java.util.List ;
import java.util.Map ;

import org.csap.agent.CsapApis ;
import org.csap.agent.integrations.MetricsPublisher ;
import org.csap.agent.stats.OsProcessCollector ;
import org.csap.agent.stats.OsSharedResourcesCollector ;
import org.csap.agent.stats.ServiceCollector ;
import org.csap.helpers.CSAP ;
import org.csap.helpers.CsapApplication ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;

import com.fasterxml.jackson.databind.JsonNode ;
import com.fasterxml.jackson.databind.ObjectMapper ;
import com.fasterxml.jackson.databind.node.ObjectNode ;

public class MetricManager {

	final Logger logger = LoggerFactory.getLogger( getClass( ) ) ;

	CsapApis csapApis ;
	ObjectMapper jsonMapper ;

	List<MetricsPublisher> publishers = new ArrayList<MetricsPublisher>( ) ;
	private Map<Integer, ServiceCollector> serviceCollectorMap = new HashMap<Integer, ServiceCollector>( ) ;
	private Map<Integer, OsSharedResourcesCollector> osSharedResourceCollectorMap = new HashMap<Integer, OsSharedResourcesCollector>( ) ;
	private Map<Integer, OsProcessCollector> osProcessCollectorMap = new HashMap<Integer, OsProcessCollector>( ) ;

	public MetricManager ( CsapApis csapApis, ObjectMapper jsonMapper ) {

		this.csapApis = csapApis ;
		this.jsonMapper = jsonMapper ;

	}

	boolean collectorsStarted = false ;

	public boolean isCollectorsStarted ( ) {

		return collectorsStarted ;

	}

	boolean suspendCollection = false ;

	void startResourceCollectors ( ) {

		var message = "Kicking off background threads for jobs, logs, metricsToCsap and metricsPublish: \n {}" ;
		logger.warn( message,
				csapApis.application( ).rootProjectEnvSettings( ).getMetricToSecondsMap( ) ) ;

		if ( csapApis.osManager( ) == null ) {

			logger.warn( CsapApplication.testHeader( "os manager is null - skipping " ) ) ;
			return ;

		}

		if ( csapApis.application( ).rootProjectEnvSettings( )
				.getMetricToSecondsMap( )
				.get(
						CsapApplication.COLLECTION_HOST ) != null ) {

			collectorsStarted = true ;
			Collections.sort( csapApis.application( ).rootProjectEnvSettings( )
					.getMetricToSecondsMap( )
					.get(
							CsapApplication.COLLECTION_HOST ) ) ;

			boolean publishSummary = true ;

			for ( Integer time : csapApis.application( ).rootProjectEnvSettings( ).getMetricToSecondsMap( ).get(
					CsapApplication.COLLECTION_HOST ) ) {

				getOsSharedResourceCollectorMap( ).put( time,
						new OsSharedResourcesCollector( csapApis, time, publishSummary ) ) ;
				publishSummary = false ;

			}

		}

		if ( ! collectorsStarted ) {

			logger.info( "No resource collectors configured - Resource collectors are disabled" ) ;
			return ;

		}

		if ( csapApis.application( ).rootProjectEnvSettings( )
				.getMetricToSecondsMap( )
				.get(
						CsapApplication.COLLECTION_OS_PROCESS ) != null ) {

			Collections.sort( csapApis.application( ).rootProjectEnvSettings( )
					.getMetricToSecondsMap( )
					.get(
							CsapApplication.COLLECTION_OS_PROCESS ) ) ;

			boolean publishSummary = true ;

			for ( Integer time : csapApis.application( ).rootProjectEnvSettings( )
					.getMetricToSecondsMap( )
					.get( CsapApplication.COLLECTION_OS_PROCESS ) ) {

				getOsProcessCollectorMap( )
						.put( time, new OsProcessCollector(
								csapApis, time,
								publishSummary ) ) ;
				publishSummary = false ;

			}

		}

		// Sorting JMX because ONLY the lowest interval will do
		// collections. Other intervals simply use the last
		// collected
		// result to avoid overwhelming connections.
		if ( csapApis.application( ).rootProjectEnvSettings( )
				.getMetricToSecondsMap( )
				.get(
						CsapApplication.COLLECTION_APPLICATION ) != null ) {

			Collections.sort( csapApis.application( ).rootProjectEnvSettings( )
					.getMetricToSecondsMap( )
					.get(
							CsapApplication.COLLECTION_APPLICATION ) ) ;

			boolean publishSummary = true ;

			for ( Integer time : csapApis.application( ).rootProjectEnvSettings( )
					.getMetricToSecondsMap( )
					.get( CsapApplication.COLLECTION_APPLICATION ) ) {

				logger.debug( "Jmx Sorted items: {}", time ) ;

				ServiceCollector applicationCollector = new ServiceCollector( csapApis, time, publishSummary ) ;

				publishSummary = false ;
				getApplicationCollectors( ).put( time, applicationCollector ) ;

			}

		}

		if ( csapApis.application( ).rootProjectEnvSettings( ).isMetricsPublication( ) ) {

			for ( JsonNode item : csapApis.application( ).rootProjectEnvSettings( )
					.getMetricsPublicationNode( ) ) {

				publishers.add( new MetricsPublisher( csapApis, (ObjectNode) item ) ) ;

			}

		}

		// Finally - Kick Off the top thread
		csapApis.osManager( ).startAgentResourceCollectors( ) ;

	}

	public Map<Integer, OsSharedResourcesCollector> getOsSharedResourceCollectorMap ( ) {

		return osSharedResourceCollectorMap ;

	}

	public void clearResourceStats ( ) {

		for ( OsSharedResourcesCollector vmStatsRunnable : osSharedResourceCollectorMap.values( ) ) {

			vmStatsRunnable.clear( ) ;

		}

	}

	public OsSharedResourcesCollector getOsSharedCollector ( ) {

		return ( getOsSharedCollector( -1 ) ) ;

	}

	public OsSharedResourcesCollector getOsSharedCollector (
																Integer collectionInterval ) {

		logger.debug( "collectionInterval: {}", collectionInterval ) ;

		if ( collectionInterval.intValue( ) < 0 ) {

			collectionInterval = firstHostCollectionInterval( ) ;

		}

		if ( getOsSharedResourceCollectorMap( ).containsKey( collectionInterval ) ) {

			return getOsSharedResourceCollectorMap( ).get( collectionInterval ) ;

		} else {

			if ( collectionInterval != -1 ) {

				logger.warn( "Requested collectionInterval not found: {}", collectionInterval ) ;

			}

			return getOsSharedResourceCollectorMap( ).get( firstHostCollectionInterval( ) ) ;

		}

	}

	public Map<Integer, OsProcessCollector> getOsProcessCollectorMap ( ) {

		return osProcessCollectorMap ;

	}

	public OsProcessCollector getOsProcessCollector (
														Integer time ) {

		try {

			if ( time.intValue( ) < 0 ) {

				time = firstServiceCollectionInterval( ) ;

			}

			if ( osProcessCollectorMap.containsKey( time ) ) {

				return osProcessCollectorMap.get( time ) ;

			} else {

				logger.warn( "Requested key not found, using first" ) ;
				return osProcessCollectorMap.get( firstServiceCollectionInterval( ) ) ;

			}

		} catch ( Exception e ) {

			logger.warn( "Failed to find a collector" ) ;
			logger.debug( "Failed to find a collector: {}", CSAP.buildCsapStack( e ) ) ;
			return null ;

		}

	}

	public Map<Integer, ServiceCollector> getApplicationCollectors ( ) {

		return serviceCollectorMap ;

	}

	public ServiceCollector getServiceCollector (
													Integer time ) {

		if ( time.intValue( ) < 0 ) {

			time = firstJavaCollectionInterval( ) ;

		}

		if ( serviceCollectorMap.containsKey( time ) ) {

			return serviceCollectorMap.get( time ) ;

		} else {

			// common query is to specify non existant or 0
			logger.debug( "Requested collector for interval: {} not found, using first", time ) ;
			return serviceCollectorMap.get( firstJavaCollectionInterval( ) ) ;

		}

	}

	public void setFirstServiceCollector (
											ServiceCollector updatedCollector ) {

		Integer firstPeriod = firstJavaCollectionInterval( ) ;
		serviceCollectorMap.put( firstPeriod, updatedCollector ) ;

	}

	public Integer firstJavaCollectionInterval ( ) {

		return csapApis.application( ).rootProjectEnvSettings( )
				.getMetricToSecondsMap( )
				.get( CsapApplication.COLLECTION_APPLICATION )
				.get( 0 ) ;

	}

	public Integer lastJavaCollectionInterval ( ) {

		return csapApis.application( ).rootProjectEnvSettings( )
				.getMetricToSecondsMap( )
				.get( CsapApplication.COLLECTION_APPLICATION )
				.get( csapApis.application( ).rootProjectEnvSettings( )
						.getMetricToSecondsMap( )
						.get( CsapApplication.COLLECTION_APPLICATION )
						.size( )
						- 1 ) ;

	}

	public Integer lastHostCollectionInterval ( ) {

		return csapApis.application( ).rootProjectEnvSettings( )
				.getMetricToSecondsMap( )
				.get( CsapApplication.COLLECTION_HOST )
				.get( csapApis.application( ).rootProjectEnvSettings( )
						.getMetricToSecondsMap( )
						.get( CsapApplication.COLLECTION_HOST )
						.size( )
						- 1 ) ;

	}

	public Integer firstServiceCollectionInterval ( ) {

		return csapApis.application( ).rootProjectEnvSettings( )
				.getMetricToSecondsMap( )
				.get( CsapApplication.COLLECTION_OS_PROCESS )
				.get( 0 ) ;

	}

	public Integer lastServiceCollectionInterval ( ) {

		return csapApis.application( ).rootProjectEnvSettings( )
				.getMetricToSecondsMap( )
				.get( CsapApplication.COLLECTION_OS_PROCESS )
				.get( csapApis.application( ).rootProjectEnvSettings( )
						.getMetricToSecondsMap( )
						.get( CsapApplication.COLLECTION_OS_PROCESS )
						.size( )
						- 1 ) ;

	}

	public void setFirstOsProcessCollector (
												OsProcessCollector updatedCollector ) {

		Integer firstPeriod = firstHostCollectionInterval( ) ;
		osProcessCollectorMap.put( firstPeriod, updatedCollector ) ;

	}

	public Integer firstHostCollectionInterval ( ) {

		try {

			return csapApis.application( ).rootProjectEnvSettings( )
					.getMetricToSecondsMap( )
					.get( CsapApplication.COLLECTION_HOST )
					.get( 0 ) ;

		} catch ( Exception e ) {

			logger.warn( "Failed to find a collector: {}", CSAP.buildCsapStack( e ) ) ;
			return 30 ;

		}

	}

	public String getSysCpuLevel ( ) {

		if ( csapApis.application( ).isJunit( ) ) {

			return "-1" ;

		}

		try {

			return osSharedResourceCollectorMap
					.get( firstHostCollectionInterval( ) )
					.getSysCpuLevel( ) ;

		} catch ( Exception e ) {

			logger.warn( "Failed to find a collector: {}", CSAP.buildCsapStack( e ) ) ;
			return "1" ;

		}

	}

	public String getUsrCpuLevel ( ) {

		if ( csapApis.application( ).isJunit( ) ) {

			return "-1" ;

		}

		try {

			return osSharedResourceCollectorMap
					.get( firstHostCollectionInterval( ) )
					.getUsrCpuLevel( ) ;

		} catch ( Exception e ) {

			logger.warn( "Failed to find a collector: {}", CSAP.buildCsapStack( e ) ) ;
			return "1" ;

		}

	}

	/**
	 * mxbean can false report
	 * 
	 * @return
	 */
	public int getLatestCpuUsage ( ) {

		if ( csapApis.application( ).isJunit( ) ) {

			return -1 ;

		}

		try {

			return osSharedResourceCollectorMap
					.get( firstHostCollectionInterval( ) )
					.getLatestCpu( ) ;

		} catch ( Exception e ) {

			logger.warn( "Failed to find a collector: {}", CSAP.buildCsapStack( e ) ) ;
			return -1 ;

		}

	}

	public double getLatestCpuLoad ( ) {

		if ( csapApis.application( ).isJunit( ) ) {

			return -1 ;

		}

		try {

			return osSharedResourceCollectorMap
					.get( firstHostCollectionInterval( ) )
					.getLatestLoad( ) ;

		} catch ( Exception e ) {

			logger.warn( "Failed to find a collector: {}", CSAP.buildCsapStack( e ) ) ;
			return -1 ;

		}

	}

	public int getLatestIoWait ( ) {

		if ( csapApis.application( ).isJunit( ) ) {

			return -1 ;

		}

		try {

			return osSharedResourceCollectorMap
					.get( firstHostCollectionInterval( ) )
					.getLatestIoWait( ) ;

		} catch ( Exception e ) {

			logger.warn( "Failed to find a collector: {}", CSAP.buildCsapStack( e ) ) ;
			return -1 ;

		}

	}

	public void startCollectorsForJunit ( ) {

		if ( ! collectorsStarted ) {

			collectorsStarted = true ;
			startResourceCollectors( ) ;

		}

	}

	public boolean isSuspendCollection ( ) {

		return suspendCollection ;

	}

	public void setSuspendCollection ( boolean suspendCollection ) {

		logger.info( CsapApplication.testHeader( "suspendCollection: {}" ), suspendCollection ) ;

		this.suspendCollection = suspendCollection ;

	}

	public void shutdown ( ) {

		logger.info( CsapApplication.header( "Shutting down collectors" ) ) ;

		try {

			for ( MetricsPublisher publisher : publishers ) {

				publisher.stop( ) ;

			}

			for ( OsSharedResourcesCollector collector : getOsSharedResourceCollectorMap( ).values( ) ) {

				collector.shutdown( ) ;

			}

			for ( OsProcessCollector collector : getOsProcessCollectorMap( ).values( ) ) {

				collector.shutdown( ) ;

			}

			for ( ServiceCollector collector : getApplicationCollectors( ).values( ) ) {

				collector.shutdown( ) ;

			}


		} catch ( Exception e1 ) {

			logger.error( "Errors on shutdown", e1 ) ;

		}

		logger.info( CsapApplication.highlightHeader( "Triggering final uploads" ) ) ;

		try {

			for ( OsSharedResourcesCollector collector : getOsSharedResourceCollectorMap( ).values( ) ) {

				collector.uploadMetricsNow( ) ;

			}

			for ( OsProcessCollector collector : getOsProcessCollectorMap( ).values( ) ) {

				collector.uploadMetricsNow( ) ;

			}

			for ( ServiceCollector collector : getApplicationCollectors( ).values( ) ) {

				collector.uploadMetricsNow( ) ;

			}

		} catch ( Exception e1 ) {

			logger.error( "Error on shutdown: {}", CSAP.buildCsapStack( e1 ) ) ;

		}

	}
}
