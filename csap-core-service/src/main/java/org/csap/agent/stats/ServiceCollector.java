package org.csap.agent.stats ;

import java.lang.management.ManagementFactory ;
import java.util.ArrayList ;
import java.util.Arrays ;
import java.util.HashMap ;
import java.util.HashSet ;
import java.util.Iterator ;
import java.util.List ;
import java.util.Map ;
import java.util.Optional ;
import java.util.Set ;
import java.util.stream.Collectors ;

import org.csap.agent.CsapApis ;
import org.csap.agent.model.ContainerState ;
import org.csap.agent.model.ServiceInstance ;
import org.csap.agent.stats.service.HttpCollector ;
import org.csap.agent.stats.service.JmxCollector ;
import org.csap.agent.stats.service.JmxCommonEnum ;
import org.csap.agent.stats.service.ServiceCollectionResults ;
import org.csap.agent.stats.service.ServiceMeter ;
import org.csap.helpers.CSAP ;
import org.csap.helpers.CsapApplication ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;

import com.fasterxml.jackson.core.JsonProcessingException ;
import com.fasterxml.jackson.databind.ObjectMapper ;
import com.fasterxml.jackson.databind.node.ArrayNode ;
import com.fasterxml.jackson.databind.node.ObjectNode ;
import com.sun.management.OperatingSystemMXBean ;

public class ServiceCollector extends HostCollector implements Runnable {

	final Logger logger = LoggerFactory.getLogger( getClass( ) ) ;

	// String metricsArray[] = { "cpuPercent", "heapUsed", "heapMax",
	// "httpConnections", "ajpConnections" };
	private long maxCollectionAllowedInMs = 2000 ;

	private JmxCollector jmxCollector ;
	private HttpCollector httpCollector ;

	private HashMap<String, ServiceCollectionResults> lastCollectedResults = new HashMap<String, ServiceCollectionResults>( ) ;

	ObjectMapper jacksonMapper = new ObjectMapper( ) ;

	private ObjectNode serviceAttributeCache = jacksonMapper.createObjectNode( ) ;

	private ObjectNode javaAttributeCache = null ;
	private long serviceCacheHash = 0 ;
	private boolean isCacheNeedsPublishing = true ;

	public HashMap<String, ServiceCollectionResults> getLastCollectedResults ( ) {

		return lastCollectedResults ;

	}

	public ServiceCollector ( CsapApis csapApis,
			int intervalSeconds, boolean publishSummary ) {

		super( csapApis, intervalSeconds, publishSummary ) ;

		httpCollector = new HttpCollector( csapApis, this ) ;
		jmxCollector = new JmxCollector( csapApis, this ) ;

		// if ( Application.isRunningOnDesktop() && !csapApis.application().isJunit() )
		// {
		// System.err.println( "\n ============= DESKTOP detected - setting logs to
		// ERROR " ) ;
		// Configurator.setLevel( ServiceCollector.class.getName(), Level.ERROR ) ;
		// }

		collected_timestamps = jacksonMapper.createArrayNode( ) ;
		collected_HostCpu = jacksonMapper.createArrayNode( ) ;

		setMaxCollectionAllowedInMs( csapApis.application( )
				.rootProjectEnvSettings( )
				.getMaxJmxCollectionMs( ) ) ;

		scheduleCollection( this ) ;

	}

	//
	// final OperatingSystemMXBean osStats = ManagementFactory
	// .getOperatingSystemMXBean();
	// Sun mbean has more features then found in jdk
	final OperatingSystemMXBean osStats = ManagementFactory
			.getPlatformMXBean( OperatingSystemMXBean.class ) ;

	// ArrayList<Long> timestamps = new ArrayList<Long>() ;

	private Map<String, String> serviceRemoteHost = new HashMap<String, String>( ) ;

	private Map<String, ObjectNode> service_to_javaMetrics = new HashMap<String, ObjectNode>( ) ;

	public Map<String, ObjectNode> getServiceToJavaMetrics ( ) {

		return service_to_javaMetrics ;

	}

	private Map<String, ObjectNode> service_to_appMetrics = new HashMap<String, ObjectNode>( ) ;

	public Map<String, ObjectNode> getServiceToAppMetrics ( ) {

		return service_to_appMetrics ;

	}

	ArrayNode collected_timestamps ;
	ArrayNode collected_HostCpu ;

	// Hook to avoid flooding buffer
	private boolean isShowWarnings = true ;

	long lastMessagesDisplayed = 0 ;
	static long DAY_IN_MS = 24 * 60 * 60 * 1000 ;

	@Override
	public void run ( ) {

		// initCollectorThread(); Switching to workers

		try {

			if ( csapApis.application( ).metricManager( ).isSuspendCollection( ) ) {

				logger.info( "collection suspended - testing" ) ;
				return ;

			}

			// logger.warn( "\n\n *********************** Collecting Service
			// Data *********************\n\n" );
			logger.debug( "Collecting Service Data" ) ;

			if ( logger.isDebugEnabled( )
					|| ( isPublishSummaryAndPerformHeartBeat( ) && System.currentTimeMillis( )
							- lastMessagesDisplayed > DAY_IN_MS ) ) {

				// only shown one collection per day
				logger.debug( "Collection Failures will be displayed" ) ;
				lastMessagesDisplayed = System.currentTimeMillis( ) ;
				isShowWarnings = true ;

			} else {

				isShowWarnings = false ;

			}

			updateTimeStampAndCpu( ) ;

			jmxCollector.collectServicesOnHost( ) ;

			httpCollector.collectServicesOnHost( ) ;

			cleanUpServiceCache( ) ;

			// Use fixed iterations??
			peformUploadIfNeeded( ) ;

		} catch ( Exception e ) {

			logger.error( "Exception in processing", e ) ;

			try {

				// add a lag. This should never happen, but if it does we
				// will not hammer on resources.
				Thread.sleep( 5000 ) ;

			} catch ( InterruptedException e1 ) {

				// TODO Auto-generated catch block
				e1.printStackTrace( ) ;

			}

		}

		logger.debug( "ServiceMetricsRunnable thread exiting: " + collectionIntervalSeconds ) ;

	}

	private void updateTimeStampAndCpu ( ) {

		int totalCpuFromSunMbean = -1 ;

		collected_timestamps.insert( 0, Long.toString( System.currentTimeMillis( ) ) ) ;

		try {

			var cpu = Long.valueOf( Math.round( osStats.getSystemCpuLoad( ) * 100.0 ) ) ;
			totalCpuFromSunMbean = cpu.intValue( ) ;

		} catch ( Exception e ) {

			logger.warn( "Failed to get cpu" ) ;

		}

		collected_HostCpu.insert( 0, totalCpuFromSunMbean ) ;

		// ObjectNode hostRuntimeNode = (ObjectNode)
		// osManager.buildServiceStatsReportAndUpdateTopCpu( true ) ;
		// logger.info( "csapApis.application().isBootstrapComplete {}",
		// csapApis.application().isBootstrapComplete() );

//		if ( hostRuntimeNode == null || ! hostRuntimeNode.has( "ps" ) || ! csapApis.application().isBootstrapComplete( ) ) {
//			logger.warn( "Failed to get valid data " ) ;
//			collected_HostCpu.insert( 0, -1 ) ;
//
//		} else {
//
//			collected_timestamps.insert( 0,
//					Long.toString( System.currentTimeMillis( ) ) ) ;
//			try {
//				// int mpTotal = (hostRuntimeNode.get("mp").get("all")
//				// .get("puser").asInt() + hostRuntimeNode.get("mp")
//				// .get("all").get("psys").asInt())
//				// * osStats.getAvailableProcessors();
//
//				totalCpuFromSunMbean = ( (int) ( osStats.getSystemCpuLoad( ) * 100 ) ) ;
//				// logger.debug("Parsing: " +
//				// hostRuntimeNode.get("mp").get("all").get("puser") );
//				collected_HostCpu.insert( 0, totalCpuFromSunMbean ) ;
//			} catch ( Exception e ) {
//				collected_HostCpu.insert( 0, -1 ) ;
//			}
//		}

		if ( collected_timestamps.size( ) > getInMemoryCacheSize( ) ) {

			collected_timestamps.remove( collected_timestamps.size( ) - 1 ) ;
			collected_HostCpu.remove( collected_timestamps.size( ) - 1 ) ;

		}

	}

	// this is total time for all services on VM being collected.
	// Typical collection only take ~30ms per services ; retries are reserved in
	// case a major gc occurs
	private int maxCollectionMillis = 10 * 1000 ;

	// private static String TEST_HOST="csapdb-dev01" ;
	public static String TEST_HOST = "csap-dev01.yourcompany.org" ;

	public void testJmxCollection ( String testHost ) {

		TEST_HOST = testHost ;
		logger.info( "\n\n ==================== Test using host: {} =======================", TEST_HOST ) ;
		updateTimeStampAndCpu( ) ;
		jmxCollector.collectServicesOnHost( ) ;
		updateTimeStampAndCpu( ) ;
		jmxCollector.collectServicesOnHost( ) ;

	}

	public void testJmxCollection ( ServiceInstance serviceInstance , String testHost ) {

		setTestHeartBeat( true ) ;
		maxCollectionMillis = 30 * 1000 ;
		TEST_HOST = testHost ;
		logger.info(
				"\n\n ==================== Testing: {} on  host: {}, serviceMeters: {}  =======================\n\n\n",
				serviceInstance.getName( ), TEST_HOST, serviceInstance.hasServiceMeters( ) ) ;
		updateTimeStampAndCpu( ) ;
		// performJmxCollection();
		jmxCollector.collectJmxDataForService( serviceInstance ) ;
		updateTimeStampAndCpu( ) ;
		jmxCollector.collectJmxDataForService( serviceInstance ) ;

	}

	public void testHttpCollection ( long maxConnectionInMs ) {
		// set timeoutout on desktop

		setMaxCollectionAllowedInMs( maxConnectionInMs ) ;
		logger.warn( CsapApplication.testHeader( " test collection with timeout: {}" ), maxConnectionInMs ) ;

		updateTimeStampAndCpu( ) ;
		httpCollector.collectServicesOnHost( ) ;
		updateTimeStampAndCpu( ) ;
		httpCollector.collectServicesOnHost( ) ;

	}

	public int getTestNumRetries ( ) {

		return testNumRetries ;

	}

	private int testNumRetries = 999 ;
	private long testServiceTimeout = -1 ;
	private String testServiceTimeoutName = null ;

	public String getTestServiceTimeoutName ( ) {

		return testServiceTimeoutName ;

	}

	public long getTestServiceTimeout ( ) {

		return testServiceTimeout ;

	}

	/**
	 * Junit testing only
	 *
	 * @param testServiceTimeout
	 * @param serviceName
	 * @param numRetries
	 */
	public void setTestServiceTimeout ( long testServiceTimeout , String serviceName , int numRetries ) {

		this.testServiceTimeout = testServiceTimeout ;
		this.testServiceTimeoutName = serviceName ;
		this.testNumRetries = numRetries ;

	}

	/**
	 * Helper method to remove services when ever instances are removed from manager
	 */
	private void cleanUpServiceCache ( ) {

		for ( Iterator<String> javaIterator = getServiceToJavaMetrics( ).keySet( ).iterator( ); javaIterator
				.hasNext( ); ) {

			String serviceName_port = javaIterator.next( ) ;

			if ( csapApis.application( ).flexFindFirstInstanceCurrentHost( serviceName_port ) == null ) {

				logger.info( "{}  - removing from java monitoring cache", serviceName_port ) ;
				javaIterator.remove( ) ;

			}

		}

		for ( Iterator<String> appIterator = getServiceToAppMetrics( ).keySet( ).iterator( ); appIterator
				.hasNext( ); ) {

			String serviceId = appIterator.next( ) ;

			if ( csapApis.application( ).flexFindFirstInstanceCurrentHost( serviceId ) == null ) {

				logger.info( "{}  - removing from app  monitoring cache", serviceId ) ;
				appIterator.remove( ) ;

			}

		}

	}

	public final static String JAVA_METRICS_EVENT = METRICS_EVENT + "/" + CsapApplication.COLLECTION_JAVA + "/" ;
	public final static String APPLICATION_METRICS_EVENT = METRICS_EVENT + "/" + CsapApplication.COLLECTION_APPLICATION
			+ "/" ;

	// need to refactor to app/custom but this involves db
	public String uploadMetrics ( int iterationsBetweenUploads ) {

		String result = "PASSED" ;

		try {

			// this is one upload
			result = upload_java_collection( iterationsBetweenUploads ) ;

			// this is one per service with custom metrics
			result = upload_application_collections(
					iterationsBetweenUploads,
					getServiceToAppMetrics( ).keySet( ) ) ;

		} catch ( Exception e ) {

			logger.error( "Failed to upload metrics: {}", CSAP.buildCsapStack( e ) ) ;
			result = "Failed, Exception:" + CSAP.buildCsapStack( e ) ;

		}

		return result ;

	}

	public String uploadLastApplicationCollection ( Set<String> serviceIds )
		throws JsonProcessingException {

		return upload_application_collections( 1, serviceIds ) ;

	}

	private String upload_application_collections (
													int iterationsBetweenAuditUploads ,
													Set<String> serviceIds )
		throws JsonProcessingException {
		// Now do custom metrics - each service uploaded independently

		_lastCustomServiceSummary = jacksonMapper.createObjectNode( ) ;

		csapApis.application( ).servicesOnHost( )
				.filter( service -> service.hasServiceMeters( ) )
				.forEach( service -> {

					List<String> ids = serviceIds.stream( )
							.filter( id -> {

								ServiceInstance idInstance = csapApis.application( ).flexFindFirstInstanceCurrentHost(
										id ) ;

								if ( idInstance != null && idInstance.getName( ).equals( service.getName( ) ) ) {

									return true ;

								}

								return false ;

							} )
							.collect( Collectors.toList( ) ) ;

					if ( ! ids.isEmpty( ) ) {

						publishApplicationMetrics( iterationsBetweenAuditUploads, ids.toArray( new String[0] ),
								service ) ;

					}

					// return false;
				} ) ;

		logger.debug( "Adding summary: {}", _lastCustomServiceSummary ) ;

		addApplicationSummary( _lastCustomServiceSummary, true ) ;

		publishSummaryReport( CsapApplication.COLLECTION_APPLICATION, true ) ;
		return "PASSED" ;

	}

	ObjectNode _latestApplicationMetricsPublished ;

	private void publishApplicationMetrics (
												int iterationsBetweenAuditUploads ,
												String[] serviceIds ,
												ServiceInstance serviceInstance ) {

		_latestApplicationMetricsPublished = jacksonMapper.createObjectNode( ) ;

		// String[] idsForCustomServices = { serviceId } ;

		String reportId = serviceInstance.getName( ) ;

		var applicationMetrics = buildCollectionReport(
				true,
				serviceIds,
				iterationsBetweenAuditUploads, 0, "isCustom" ) ;

		var customBaseCategory = APPLICATION_METRICS_EVENT + reportId + "/" + collectionIntervalSeconds + "/" ;

		//
		// Updated attributes pushed....should only be on change....
		//
		if ( serviceAttributeCache.has( serviceInstance.getName( ) )
				&& ! serviceAttributeCache.path( serviceInstance.getName( ) ).has( "published" ) ) {

			ObjectNode attributeDefinition = (ObjectNode) serviceAttributeCache.get( serviceInstance.getName( ) ) ;
			attributeDefinition.put( "published", "true" ) ;
			// full upload. We could make call to event service to see if
			// they match...for now we do on restarts

			logger.debug( "event: {}, {}", customBaseCategory + "attributes",
					CSAP.jsonPrint( attributeDefinition.get( "attributes" ) ) ) ;

			_latestApplicationMetricsPublished.put( "attributes.location", customBaseCategory + "attributes" ) ;
			_latestApplicationMetricsPublished.set( "attributes", attributeDefinition.get( "attributes" ) ) ;

			csapApis.events( )
					.publishEvent( customBaseCategory + "attributes", "Modified", null,
							attributeDefinition.get( "attributes" ) ) ;

		}

		ObjectNode correlationAttributes = jacksonMapper.createObjectNode( ) ;

		correlationAttributes.put( "id", CsapApplication.COLLECTION_APPLICATION + "-" + reportId + "_"
				+ collectionIntervalSeconds ) ;
		// correlationAttributes.put( "id", "jmx" + reportId + "_" +
		// collectionIntervalSeconds ) ;
		correlationAttributes.put( "hostName", csapApis.application( ).getCsapHostShortname( ) ) ;

		// Send normalized data
		applicationMetrics.set( "attributes", correlationAttributes ) ;

		logger.debug( "event: {}, {}", customBaseCategory + "data", CSAP.jsonPrint( applicationMetrics ) ) ;
		_latestApplicationMetricsPublished.put( "data.location", customBaseCategory + "data" ) ;
		_latestApplicationMetricsPublished.set( "data", applicationMetrics ) ;
		csapApis.events( )
				.publishEvent( customBaseCategory + "data",
						"Upload", null, applicationMetrics ) ;

		// }
	}

	public String[] getJavaServiceNames ( ) {

		return getServiceToJavaMetrics( )
				.keySet( )
				.toArray( new String[0] ) ;

	}

	public ObjectNode buildServicesAvailableReport ( ) {

		var nameReport = jacksonMapper.createObjectNode( ) ;
		nameReport.set( "java", jacksonMapper.convertValue( getServiceToJavaMetrics( ).keySet( ), ArrayNode.class ) ) ;
		nameReport.set( "application", jacksonMapper.convertValue( getServiceToAppMetrics( ).keySet( ),
				ArrayNode.class ) ) ;
		return nameReport ;

	}

	private String upload_java_collection ( int iterationsBetweenAuditUploads )
		throws JsonProcessingException {

		// MultiValueMap<String, String> map = new
		// LinkedMultiValueMap<String, String>();
		ObjectNode jmxSamplesToUploadNode = buildCollectionReport( true,
				getJavaServiceNames( ),
				iterationsBetweenAuditUploads, 0 ) ;

		// new Event publisher - it checks if publish is enabled. If services
		// have been updated, then attributes are uploaded again
		if ( isCacheNeedsPublishing ) {

			// full upload. We could make call to event service to see if they
			// match...for now we do on restarts
			csapApis.events( )
					.publishEvent( JAVA_METRICS_EVENT + collectionIntervalSeconds + "/attributes", "Modified", null,
							javaAttributeCache ) ;
			isCacheNeedsPublishing = false ;

		}

		if ( jmxCorrelationAttributes == null ) {

			jmxCorrelationAttributes = jacksonMapper.createObjectNode( ) ;
			jmxCorrelationAttributes.put( "id", CsapApplication.COLLECTION_JAVA + "_" + collectionIntervalSeconds ) ;
			jmxCorrelationAttributes.put( "hostName", csapApis.application( ).getCsapHostShortname( ) ) ;

		}

		// Send normalized data
		jmxSamplesToUploadNode.set( "attributes", jmxCorrelationAttributes ) ;
		csapApis.events( )
				.publishEvent( JAVA_METRICS_EVENT + collectionIntervalSeconds + "/data",
						"Upload", null, jmxSamplesToUploadNode ) ;

		publishSummaryReport( CsapApplication.COLLECTION_JAVA ) ;

		return "PASSED" ;

	}

	private ObjectNode jmxCorrelationAttributes = null ;

	final static int DEFAULT_SERVICES = 4 ;

	public ObjectNode getJavaCollection ( int requestedSampleSize , int skipFirstItems , String... services ) {

		if ( services[0].toLowerCase( ).equals( ALL_SERVICES ) ) {

			services = getJavaServiceNames( ) ;

		}

		return buildCollectionReport( false, services, requestedSampleSize, skipFirstItems ) ;

	}

	public ObjectNode getApplicationCollection ( int requestedSampleSize , int skipFirstItems , String... services ) {

		return buildCollectionReport( false,
				services,
				requestedSampleSize, 0, "isCustom" ) ;

	}

	private ObjectNode _lastCustomServiceSummary = jacksonMapper.createObjectNode( ) ;

	public ObjectNode buildCollectionReport (
												boolean isUpdateSummary ,
												String[] serviceNameArray ,
												int requestedSampleSize ,
												int skipFirstItems ,
												String... customArgs ) {

		logger.debug(
				" serviceNameArray: {} , customArgs: {}, intervalSeconds: {}, jmxCacheResults: {}, timeStamps: {} ",
				Arrays.toString( serviceNameArray ),
				Arrays.toString( customArgs ),
				collectionIntervalSeconds,
				getServiceToJavaMetrics( ).size( ),
				collected_timestamps.size( ) ) ;

		boolean isCustom = customArgs.length > 0 ;

		ObjectNode collectionReport = jacksonMapper.createObjectNode( ) ;

		List<String> requestedServices = new ArrayList<String>( ) ;
		Set<String> availableServices = new HashSet<String>( ) ;

		if ( isCustom ) {

			availableServices = getServiceToAppMetrics( ).keySet( ) ;
			requestedServices = Arrays.asList( serviceNameArray ) ;

			if ( requestedServices.isEmpty( ) ) {

				collectionReport.put( "error", "no services found, application:" + isCustom ) ;
				return collectionReport ;

			}

			if ( requestedServices.size( ) == 1 ) {

				ServiceInstance matchedInstance = csapApis.application( ).flexFindFirstInstanceCurrentHost(
						requestedServices
								.get( 0 ) ) ;

				if ( matchedInstance != null
						&& matchedInstance.is_cluster_kubernetes( )
						&& matchedInstance.getName( ).equals( requestedServices.get( 0 ) ) ) {

					requestedServices = matchedInstance.getCsapKubePodNames( ) ;

				}

			}

			if ( ! getServiceToAppMetrics( ).keySet( ).contains( requestedServices.get( 0 ) ) ) {

				collectionReport.put( "error", "Did no find application data for service: " + requestedServices.get( 0 )
						+ ". Available: " + getServiceToAppMetrics( ).keySet( ).toString( ) ) ;
				return collectionReport ;

			}

		} else {

			availableServices = getServiceToJavaMetrics( ).keySet( ) ;

			if ( serviceNameArray == null ) {

				// default for java - show the first n services
				serviceNameArray = new String[DEFAULT_SERVICES] ;
				int i = 0 ;

				for ( String serviceName : getServiceToJavaMetrics( ).keySet( ) ) {

					requestedServices.add( serviceName ) ;

					if ( ++i >= DEFAULT_SERVICES ) {

						break ;

					}

				}

			} else {

				requestedServices = Arrays.asList( serviceNameArray ) ;

			}

		}

		// check for missing services
		Set<String> testkeySet = availableServices ;
		Optional<String> service = requestedServices.stream( )
				.filter( serviceId -> testkeySet.contains( serviceId ) )
				.findAny( ) ;

		if ( service.isEmpty( ) ) {

			collectionReport.put( "error", "Did not find  data for service: " + requestedServices
					+ ". Available: " + testkeySet ) ;

		}

		logger.debug( "requestedSampleSize: {}, skipFirstItems: {}", requestedSampleSize, skipFirstItems ) ;

		ObjectNode attributeNode = collectionReport.putObject( ATTRIBUTES_JSON ) ;
		ObjectNode dataNode = collectionReport.putObject( DATA_JSON ) ;
		ArrayNode tsArray = dataNode.putArray( "timeStamp" ) ;
		ArrayNode totalArray = dataNode.putArray( "totalCpu" ) ;

		int numSamples = 0 ;

		if ( requestedSampleSize == -1 ) {

			tsArray.addAll( collected_timestamps ) ;
			totalArray.addAll( collected_HostCpu ) ;

		} else {

			for ( int i = 0; i < requestedSampleSize
					&& i < collected_timestamps.size( ); i++ ) {

				tsArray.add( collected_timestamps.get( i ) ) ;
				totalArray.add( collected_HostCpu.get( i ) ) ;
				numSamples++ ;

			}

		}

		logger.debug( "Number of timestamps: {} , tsArray: {}", collected_timestamps.size( ), tsArray.size( ) ) ;

		if ( isCustom ) {

			dataNode.setAll(
					build_application_collection( isUpdateSummary, requestedSampleSize, requestedServices,
							numSamples ) ) ;

			attributeNode.setAll(
					build_application_attributes( requestedServices, requestedSampleSize, skipFirstItems ) ) ;

		} else {

			dataNode.setAll( build_java_collection( isUpdateSummary, requestedSampleSize, requestedServices,
					numSamples ) ) ;
			attributeNode.setAll( build_java_attributes(
					requestedServices,
					requestedSampleSize,
					skipFirstItems ) ) ;

		}

		return collectionReport ;

	}

	private ObjectNode build_java_collection (
												boolean isUpdateSummary ,
												int requestedSampleSize ,
												List<String> servicesFilter ,
												int numSamples ) {

		ObjectNode javaMetricsReport = jacksonMapper.createObjectNode( ) ;
		ObjectNode javaSummaryReport = jacksonMapper.createObjectNode( ) ;

		// standard JMX collections
		for ( String serviceName : servicesFilter ) {

			ObjectNode serviceCacheNode = getServiceToJavaMetrics( ).get( serviceName ) ;

			if ( serviceCacheNode != null ) {

				String summaryName = serviceName ;
				// strip off port or pod suffix
				ServiceInstance serviceInstance = csapApis.application( ).flexFindFirstInstanceCurrentHost(
						serviceName ) ;
				summaryName = serviceInstance.getName( ) ;

				ObjectNode serviceSummary ;

				if ( javaSummaryReport.has( summaryName ) ) {

					serviceSummary = (ObjectNode) javaSummaryReport.path( summaryName ) ;

				} else {

					serviceSummary = javaSummaryReport.putObject( summaryName ) ;

				}

				serviceSummary.put( "numberOfSamples", numSamples + serviceSummary.path( summaryName ).asInt( 0 ) ) ;

				for ( JmxCommonEnum jmxMetric : JmxCommonEnum.values( ) ) {

					String metricFullName = jmxMetric.value + "_"
							+ serviceName ;
					ArrayNode cacheArray = (ArrayNode) serviceCacheNode
							.get( metricFullName ) ;

					if ( cacheArray == null ) {

						continue ;

					}

					ArrayNode metricArray = javaMetricsReport
							.putArray( metricFullName ) ;

					if ( requestedSampleSize == -1 ) {

						metricArray.addAll( cacheArray ) ;

					} else {

						long metricTotal = 0 ;

						for ( int i = 0; i < requestedSampleSize
								&& i < cacheArray.size( ); i++ ) {

							metricArray.add( cacheArray.get( i ) ) ;
							metricTotal += cacheArray
									.get( i )
									.asLong( ) ;

						}

						serviceSummary.put( jmxMetric.value, metricTotal + serviceSummary.path( jmxMetric.value ).asInt(
								0 ) ) ;

					}

				}

			}

		}

		logger.debug( "javaSummaryReport: {}", javaSummaryReport ) ;

		addSummary( javaSummaryReport, isUpdateSummary ) ;
		return javaMetricsReport ;

	}

	private ObjectNode build_application_collection (
														boolean isUpdateSummary ,
														int requestedSampleSize ,
														List<String> servicesFilter ,
														int numSamples ) {

		ObjectNode dataNode = jacksonMapper.createObjectNode( ) ;

		// custom Collections
		for ( String serviceId : servicesFilter ) {

			// String serviceId = servicesFilter.get( 0 ) ;
			ObjectNode application_metrics_cache = getServiceToAppMetrics( ).get( serviceId ) ;

			String summaryServiceName = serviceId ;

			// Strip off ports for summary reports
			// Handle k8s instance selection: uses pod it
			ServiceInstance serviceInstance = csapApis.application( ).flexFindFirstInstanceCurrentHost( serviceId ) ;
			summaryServiceName = serviceInstance.getName( ) ;

			ObjectNode summaryReport = jacksonMapper.createObjectNode( ) ;

			if ( isUpdateSummary ) {

				// Done on a single thread
				if ( _lastCustomServiceSummary.has( summaryServiceName ) ) {

					summaryReport = (ObjectNode) _lastCustomServiceSummary.path( summaryServiceName ) ;

				} else {

					summaryReport = _lastCustomServiceSummary.putObject( summaryServiceName ) ;

				}

			}

			summaryReport.put( "numberOfSamples", numSamples + summaryReport.path( "numberOfSamples" ).asInt( 0 ) ) ;

			if ( application_metrics_cache == null ) {

				logger.debug( "Warning: serviceCacheNode is null" ) ;

			} else {

				// logger.info("**** serviceName " + serviceName);
				for ( Iterator<String> metricNames = application_metrics_cache.fieldNames( ); metricNames
						.hasNext( ); ) {

					String metricName = metricNames.next( ).trim( ) ;

					String metricDataSet = metricName ;

					if ( serviceInstance.is_cluster_kubernetes( ) ) {

						metricDataSet = metricName + "_" + serviceId ;

					}

					logger.debug( "metricId: {} , dataKey: {}", metricName, metricDataSet ) ;

					ArrayNode metricCacheArray = (ArrayNode) application_metrics_cache
							.get( metricName ) ;

					ArrayNode graphDataArray = dataNode.putArray( metricDataSet ) ;

					//
					if ( requestedSampleSize == -1 ) {

						graphDataArray.addAll( metricCacheArray ) ;

					} else {

						// summary reports as ints? maybe switch to doubles
						//
						// logger.info( "metricCacheArray size: " +
						// metricCacheArray.size() ) ;
						long metricTotal = 0 ;

						for ( int i = 0; i < requestedSampleSize
								&& i < metricCacheArray.size( ); i++ ) {

							graphDataArray.add( metricCacheArray.get( i ) ) ;
							long current = metricCacheArray
									.get( i )
									.asLong( ) ;
							metricTotal += current ;

						}

						summaryReport.put( metricName, metricTotal + summaryReport.path( metricName ).asInt( 0 ) ) ;
						logger.debug( "{}  total: {} ", metricName, metricTotal ) ;

					}

				}

			}

		}

		return dataNode ;

	}

	private ObjectNode build_java_attributes (
												List<String> servicesFilter ,
												int requestedSampleSize ,
												int skipFirstItems ) {

		long requestHashValue = csapApis.application( ).getApplicationLoadedAtMillis( ) + servicesFilter
				.toString( )
				.hashCode( ) ;

		boolean isFullServicesRequest = servicesFilter.size( ) == getServiceToJavaMetrics( )
				.keySet( )
				.size( ) ;

		if ( javaAttributeCache != null && isFullServicesRequest ) {

			if ( requestHashValue == serviceCacheHash ) {

				logger.debug( "Using Cached attributes" ) ;
				return javaAttributeCache ;

			}

		}

		var timer = csapApis.metrics( ).startTimer( ) ;

		ObjectNode attributeJson = jacksonMapper.createObjectNode( ) ;

		attributeJson.put( "id", CsapApplication.COLLECTION_JAVA + "_" + collectionIntervalSeconds ) ;
		attributeJson.put( "metricName", "Java Collection" ) ;
		attributeJson.put( "description", "Contains service metrics" ) ;
		attributeJson.put( "timezone", TIME_ZONE_OFFSET ) ;
		attributeJson.put( "hostName", csapApis.application( ).getCsapHostShortname( ) ) ;
		attributeJson.put( "sampleInterval", collectionIntervalSeconds ) ;
		attributeJson.put( "samplesRequested", requestedSampleSize ) ;
		attributeJson.put( "samplesOffset", skipFirstItems ) ;
		attributeJson.put( "currentTimeMillis", System.currentTimeMillis( ) ) ;
		attributeJson.put( "cpuCount", osStats.getAvailableProcessors( ) ) ;

		ObjectNode remoteCollect = attributeJson
				.putObject( "remoteCollect" ) ;

		for ( String serviceName : serviceRemoteHost.keySet( ) ) {

			remoteCollect.put( serviceName, serviceRemoteHost.get( serviceName ) ) ;

		}

		ArrayNode servicesAvailArray = attributeJson
				.putArray( "servicesAvailable" ) ;

		for ( String serviceName : getServiceToJavaMetrics( ).keySet( ) ) {

			servicesAvailArray.add( serviceName ) ;

		}

		ArrayNode servicesReqArray = attributeJson
				.putArray( "servicesRequested" ) ;

		for ( String serviceName : servicesFilter ) {

			servicesReqArray.add( serviceName ) ;

		}

		ObjectNode graphsObject = attributeJson.putObject( "graphs" ) ;
		attributeJson.set( "titles", JmxCommonEnum.graphLabels( ) ) ;

		for ( JmxCommonEnum metric : JmxCommonEnum.values( ) ) {

			String metricNameInAttributes = metric.value ;

			ObjectNode resourceGraph = graphsObject.putObject( metricNameInAttributes ) ;

			for ( String serviceId : servicesFilter ) {

				String metricFullName = metric.value + "_" + serviceId ;

				// filter tomcat params from non tomcat services.
				// ServiceInstance testInstance =
				// getCsapApplication().getServiceInstanceAnyPackage(
				// serviceName );
				ServiceInstance testInstance = csapApis.application( ).flexFindFirstInstanceCurrentHost( serviceId ) ;

				if ( testInstance != null && metric.isTomcatOnly( ) ) {

					if ( testInstance.is_tomcat_collect( ) ) {

						resourceGraph.put( metricFullName, serviceId ) ;

					}

				} else {

					resourceGraph.put( metricFullName, serviceId ) ;

				}

			}

			if ( ! resourceGraph
					.fieldNames( )
					.hasNext( ) ) {

				// some jmx attributes are for tomcat only; filter them if empty
				graphsObject.remove( metricNameInAttributes ) ;

			}

			// Added at the bottom so colors match across graphs
			if ( metric == JmxCommonEnum.cpuPercent ) {

				resourceGraph.put( "totalCpu", "VM Total" ) ;

			}

		}

		csapApis.metrics( ).stopTimer( timer, "service-collector.attributes-java" ) ;

		if ( isFullServicesRequest ) {

			javaAttributeCache = attributeJson ;
			serviceCacheHash = requestHashValue ;
			isCacheNeedsPublishing = true ;

			logger.debug( "Updated attributes cache \n {}", javaAttributeCache ) ;

		}

		return attributeJson ;

	}

	// only cache for all services. sevice -> service.hash, service.publish,
	// service.attributes

	private ObjectNode build_application_attributes (
														List<String> requestedServiceIds ,
														int requestedSampleSize ,
														int skipFirstItems ) {

		String primaryFilter = requestedServiceIds.get( 0 ) ;
		ServiceInstance serviceInstance = csapApis.application( ).flexFindFirstInstanceCurrentHost( primaryFilter ) ;

		logger.debug( "{} build attributes for id: {}, all ids: {}",
				serviceInstance.getName( ),
				primaryFilter,
				requestedServiceIds ) ;

		ObjectNode latestServiceCollection = getServiceToAppMetrics( ).get( primaryFilter ) ;

		if ( latestServiceCollection == null ) {

			logger.warn( "no data for: {}", serviceInstance.toSummaryString( ) ) ;
			return null ;

		}

		StringBuilder serviceAttributeCacheCalculator = new StringBuilder( "" ) ;

		// sync names and metrics
		serviceInstance.getContainerStatusList( ).forEach( container -> {

			serviceAttributeCacheCalculator.append( container.getContainerName( ) ) ;

		} ) ;

		serviceAttributeCacheCalculator.append( requestedServiceIds.toString( ) ) ;

		long requestHashValue = csapApis.application( ).getApplicationLoadedAtMillis( )
				+ serviceAttributeCacheCalculator
						.toString( ).hashCode( ) ;

		logger.debug( "{} at {} ms requestHashValue: {}, attributeCacheDetails: {}",
				serviceInstance.getName( ), csapApis.application( ).getApplicationLoadedAtMillis( ), requestHashValue,
				serviceAttributeCacheCalculator ) ;

		if ( serviceAttributeCache.has( serviceInstance.getName( ) ) ) {

			ObjectNode cachedItem = (ObjectNode) serviceAttributeCache.path( serviceInstance.getName( ) ) ;

			var currentCacheHash = cachedItem.path( "hash" ).asLong( -1 ) ;
			logger.debug( "Checking request: {} against cache hash: {}", requestHashValue, currentCacheHash ) ;

			if ( requestHashValue == currentCacheHash ) {

				logger.debug( "Using Cached attributes" ) ;
				return (ObjectNode) cachedItem.get( "attributes" ) ;

			}

		}

		var timer = csapApis.metrics( ).startTimer( ) ;
		ObjectNode updatedAttributesJson = jacksonMapper.createObjectNode( ) ;
		String serviceId = serviceInstance.getName( ) ;

		updatedAttributesJson.put( "id", CsapApplication.COLLECTION_APPLICATION + "-" + serviceId + "_"
				+ collectionIntervalSeconds ) ;

		updatedAttributesJson.put( "metricName", "Service Collection" ) ;
		updatedAttributesJson.put( "description", "Contains service metrics" ) ;
		updatedAttributesJson.put( "timezone", TIME_ZONE_OFFSET ) ;
		updatedAttributesJson.put( "hostName", csapApis.application( ).getCsapHostShortname( ) ) ;
		updatedAttributesJson.put( "sampleInterval", collectionIntervalSeconds ) ;
		updatedAttributesJson.put( "samplesRequested", requestedSampleSize ) ;
		updatedAttributesJson.put( "samplesOffset", skipFirstItems ) ;
		updatedAttributesJson.put( "currentTimeMillis", System.currentTimeMillis( ) ) ;
		updatedAttributesJson.put( "cpuCount", osStats.getAvailableProcessors( ) ) ;

		ObjectNode remoteCollect = updatedAttributesJson.putObject( "remoteCollect" ) ;

		if ( serviceRemoteHost.containsKey( primaryFilter ) ) {

			remoteCollect.put( "default", serviceRemoteHost.get( primaryFilter ) ) ;

		}

		ArrayNode servicesAvailArray = updatedAttributesJson.putArray( "servicesAvailable" ) ;

		if ( ! serviceInstance.getCsapKubePodNames( ).isEmpty( ) ) {

			serviceInstance.getCsapKubePodNames( ).stream( )
					.forEach( containerName -> {

						servicesAvailArray.add( containerName ) ;

					} ) ;

		} else {

			servicesAvailArray.add( serviceInstance.getName( ) ) ;

		}

		ArrayNode servicesRequested = updatedAttributesJson.putArray( "servicesRequested" ) ;

		for ( String serviceFilter : requestedServiceIds ) {

			if ( getServiceToAppMetrics( ).keySet( ).contains( serviceFilter ) ) {

				servicesRequested.add( serviceFilter ) ;

			}

		}

		ObjectNode graphs = updatedAttributesJson.putObject( "graphs" ) ;
		ObjectNode collectedFrom = updatedAttributesJson.putObject( "collectedFrom" ) ;
		ObjectNode seriesInfo = updatedAttributesJson.putObject( "seriesInfo" ) ;
		updatedAttributesJson.set( "titles", serviceInstance.getServiceMeterTitles( ) ) ;

		for ( ServiceMeter meter : serviceInstance.getServiceMeters( ) ) {

			ObjectNode graph = graphs.putObject( meter.getCollectionId( ) ) ;
			String uiLegend = meter.toSummary( ) ;
			uiLegend.replace( '"', '/' ) ;
			collectedFrom.put( meter.getCollectionId( ), uiLegend ) ;

			for ( int container = 1; container <= serviceInstance.getContainerStatusList( ).size( ); container++ ) {

				ContainerState containerState = serviceInstance.getContainerStatusList( ).get( container - 1 ) ;

				String graphKey = meter.getCollectionId( ) ;

				if ( serviceInstance.is_cluster_kubernetes( ) ) {

					String podName = serviceInstance.getName( ) + "-" + container ;

					if ( requestedServiceIds.contains( podName ) ) {

						graphKey = meter.getCollectionId( ) + "_" + podName ;
						uiLegend = "pod-" + container ;
						seriesInfo.put( uiLegend, containerState.getPodNamespace( ) + "," + containerState
								.getContainerName( ) ) ;
						graph.put( graphKey, uiLegend ) ;

					}

				} else {

					graph.put( graphKey, uiLegend ) ;
					seriesInfo.put( meter.getCollectionId( ), "source:" + "," + uiLegend ) ;

				}

			}

		}

		csapApis.metrics( ).stopTimer( timer, "service-collector.attributes-application" ) ;

		ObjectNode cachedAttributes = jacksonMapper.createObjectNode( ) ;
		cachedAttributes.set( "attributes", updatedAttributesJson ) ;
		cachedAttributes.put( "hash", requestHashValue ) ;
		serviceAttributeCache.set( serviceInstance.getName( ), cachedAttributes ) ;

		return updatedAttributesJson ;

	}

	private boolean testHeartBeat = false ;

	public boolean isTestHeartBeat ( ) {

		return testHeartBeat ;

	}

	public void setTestHeartBeat ( boolean testHeartBeat ) {

		this.testHeartBeat = testHeartBeat ;

	}

	public long getMaxCollectionAllowedInMs ( ) {

		return maxCollectionAllowedInMs ;

	}

	public void setMaxCollectionAllowedInMs ( long maxCollectionAllowedInMs ) {

		this.maxCollectionAllowedInMs = maxCollectionAllowedInMs ;

	}

	public boolean isShowWarnings ( ) {

		return isShowWarnings ;

	}

	public void setShowWarnings ( boolean isShowWarnings ) {

		this.isShowWarnings = isShowWarnings ;

	}

	public ArrayNode getCollected_HostCpu ( ) {

		return collected_HostCpu ;

	}

	public ObjectNode get_lastCustomServiceSummary ( ) {

		return _lastCustomServiceSummary ;

	}

	public ObjectNode getLatestApplicationMetricsPublished ( ) {

		return _latestApplicationMetricsPublished ;

	}

	public int getMaxCollectionMillis ( ) {

		return maxCollectionMillis ;

	}

	public void setMaxCollectionMillis ( int maxCollectionTime ) {

		this.maxCollectionMillis = maxCollectionTime ;

	}

	public Map<String, String> getServiceRemoteHost ( ) {

		return serviceRemoteHost ;

	}

	public JmxCollector getJmxCollector ( ) {

		return jmxCollector ;

	}

	public HttpCollector getHttpCollector ( ) {

		return httpCollector ;

	}

}
