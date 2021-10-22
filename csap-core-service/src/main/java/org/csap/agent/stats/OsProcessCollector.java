package org.csap.agent.stats ;

import java.lang.management.ManagementFactory ;
import java.lang.management.OperatingSystemMXBean ;
import java.util.ArrayList ;
import java.util.Arrays ;
import java.util.Collections ;
import java.util.Comparator ;
import java.util.HashMap ;
import java.util.LinkedHashMap ;
import java.util.LinkedList ;
import java.util.List ;
import java.util.Map ;
import java.util.concurrent.TimeUnit ;
import java.util.stream.Collectors ;

import org.csap.agent.model.Application ;
import org.csap.agent.model.ClusterType ;
import org.csap.agent.model.ContainerState ;
import org.csap.agent.model.ProcessRuntime ;
import org.csap.agent.model.ServiceInstance ;
import org.csap.agent.services.OsManager ;
import org.csap.helpers.CSAP ;
import org.csap.helpers.CsapApplication ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;

import com.fasterxml.jackson.databind.JsonNode ;
import com.fasterxml.jackson.databind.ObjectMapper ;
import com.fasterxml.jackson.databind.node.ArrayNode ;
import com.fasterxml.jackson.databind.node.ObjectNode ;

public class OsProcessCollector extends HostCollector implements Runnable {

	final Logger logger = LoggerFactory.getLogger( OsProcessCollector.class ) ;

	public OsProcessCollector ( Application csapApplication,
			OsManager osManager, int intervalSeconds, boolean publishSummary ) {

		super( csapApplication, osManager, intervalSeconds, publishSummary ) ;

		timeStampArray = jacksonMapper.createArrayNode( ) ;
		totalCpuArray = jacksonMapper.createArrayNode( ) ;

		scheduleCollection( this ) ;

		if ( csapApplication.isDesktopHost( ) ) {

			cleanStaleServicesMs = TimeUnit.MINUTES.toMillis( 5 ) ;

		}

		logger.info( "stale services will be removed after: {}", CSAP.autoFormatMillis( cleanStaleServicesMs ) ) ;

	}

	public void testCollection ( ) {

		logger.warn( "{} \n Test: OS Processes {}",
				CsapApplication.LINE,
				CsapApplication.LINE ) ;

		add_latest_statistics_to_cache( ) ;

	}

	ArrayList<Long> timestamps = new ArrayList<Long>( ) ;

	Map<String, ObjectNode> service_to_metricsArrays = new HashMap<>( ) ;
	Map<String, String> kubernetes_services = new HashMap<>( ) ;
	Map<String, Long> stale_services = new HashMap<>( ) ;
	long cleanStaleServicesMs = TimeUnit.HOURS.toMillis( 24 ) ;

	ArrayNode timeStampArray ;
	ArrayNode totalCpuArray ;

	ObjectMapper jacksonMapper = new ObjectMapper( ) ;

	@Override
	public void run ( ) {

		try {

			// Step 1 - iteratate over latest metrics from top thread
			try {

				add_latest_statistics_to_cache( ) ;

			} catch ( Exception e1 ) {

				logger.error( "Failed processing latest metrics: {}", CSAP.buildCsapStack( e1 ) ) ;

			}

			peformUploadIfNeeded( ) ;

		} catch ( Exception e ) {

			logger.error( "Exception in processing", e ) ;

		}

		logger.debug( "Completed collection" ) ;

	}

	public ArrayNode buildServicesAvailableReport ( ) {

		return jacksonMapper.convertValue( service_to_metricsArrays.keySet( ), ArrayNode.class ) ;

	}

	private void add_latest_statistics_to_cache ( ) {

		logger.debug( "Getting updated service metrics" ) ;

		var os_metrics = osManager.buildServiceStatsReportAndUpdateTopCpu( true ) ;

		if ( os_metrics != null && os_metrics.has( "ps" ) ) {

			var latestServiceStatistics = os_metrics.path( "ps" ) ;

			timeStampArray.insert( 0, Long.toString( System.currentTimeMillis( ) ) ) ;

			try {

				logger.debug( "servicesNode status: \n {}", CSAP.jsonPrint( latestServiceStatistics ) ) ;
				// totalCpuArray.insert( 0, osManager.getVmTotal() *
				// osStats.getAvailableProcessors() );
				totalCpuArray.insert( 0, osManager.getHostTotalTopCpu( ) ) ;

			} catch ( Exception e ) {

				logger.error( "Failed getting total cpu {}", CSAP.buildCsapStack( e ) ) ;
				totalCpuArray.insert( 0, 0 ) ;

			}

			if ( timeStampArray.size( ) > getInMemoryCacheSize( ) ) {

				timeStampArray.remove( timeStampArray.size( ) - 1 ) ;
				totalCpuArray.remove( timeStampArray.size( ) - 1 ) ;

			}

			latestServiceStatistics.fieldNames( ).forEachRemaining( serviceName -> {

				add_latest_statistics_for_service( latestServiceStatistics, serviceName ) ;

			} ) ;

			// after adding all the latest services remove any old ones
			cleanUpServiceCache( latestServiceStatistics ) ;

		} else {

			logger.warn( "Runtime not availbable" ) ;

		}

	}

	private void add_latest_statistics_for_service (
														JsonNode allServiceState ,
														String serviceName ) {

		var serviceStatusReport = allServiceState.path( serviceName ) ;
		var serverType = serviceStatusReport.path( "serverType" ).asText( ProcessRuntime.unknown.getId( ) ) ;
		var cpuUtil = serviceStatusReport
				.at( "/" + ContainerState.JSON_KEY + "/0/" + ContainerState.PS_CPU )
				.asText( ContainerState.INACTIVE ) ;

		// Skip scripts in collections
		if ( serverType == ProcessRuntime.unknown.getId( ) ) {

			logger.warn( "Server Type not found for service: '{}', collection aborted", serviceName ) ;
			return ;

		}

		if ( serverType.equals( ProcessRuntime.script.getId( ) )
				|| cpuUtil.equals( ContainerState.REMOTE_UTIL ) ) {

			logger.debug( "service: '{}', is a script or remote collection - skipping", serviceName ) ;
			return ;

		}

		int containerCount = serviceStatusReport.path( ContainerState.JSON_KEY ).size( ) ;
		boolean isKubernetes = serviceStatusReport.path( ClusterType.CLUSTER_TYPE ).asText( "not" )
				.equals( ClusterType.KUBERNETES.getJson( ) ) ;

		boolean isAggregateContainers = false ;

		var activeService = csapApplication.findServiceByNameOnCurrentHost( serviceName ) ;

		if ( activeService != null ) {

			isAggregateContainers = activeService.isAggregateContainerMetrics( ) ;

		}

		for ( var containerIndex = 0; containerIndex < containerCount; containerIndex++ ) {

			// Initialize the metric array if needed
			var serviceKey = serviceName ;

			if ( isAggregateContainers ) {

				serviceKey += "-1" ; // everything is pushed into a single container
				kubernetes_services.put( serviceKey, serviceName ) ;

			} else if ( isKubernetes ) {

				serviceKey += "-" + ( containerIndex + 1 ) ;
				kubernetes_services.put( serviceKey, serviceName ) ; // stored for cleanup

			}

			if ( ! service_to_metricsArrays.containsKey( serviceKey ) ) {

				logger.debug( "Adding cache for service: {}  containerCount: {} ", CSAP.pad( serviceKey ),
						containerCount ) ;

				ObjectNode serviceMetricNode = jacksonMapper.createObjectNode( ) ;
				serviceMetricNode.putArray( "timeStamp" ) ;

				for ( OsProcessEnum os : OsProcessEnum.values( ) ) {

					String metricFullName = os.value + "_" + serviceKey ;
					serviceMetricNode.putArray( metricFullName ) ;

				}

				service_to_metricsArrays.put( serviceKey, serviceMetricNode ) ;

			}

			var serviceCollectionCache = service_to_metricsArrays.get( serviceKey ) ;

			var statusContainerPath = "/" + ContainerState.JSON_KEY + "/" + containerIndex + "/" ;

			for ( OsProcessEnum os : OsProcessEnum.values( ) ) {

				var metric = os.value ;

				// source
				var metricStatusReportPath = statusContainerPath + metric ;
				var metricCollected = serviceStatusReport.at( metricStatusReportPath ) ;

				// destination
				var metricFullName = metric + "_" + serviceKey ;
				var serviceMetricCollection = ( (ArrayNode) serviceCollectionCache.get( metricFullName ) ) ;

				if ( isAggregateContainers && containerIndex > 0 ) {

					// aggregate all containers into first instance: eg namespace collection
					var current = serviceMetricCollection.remove( 0 ).asInt( ) ;

					if ( metric.equals( OsProcessEnum.RSS_MEMORY ) ) {

						serviceMetricCollection.insert( 0, current + metricCollected.asInt( ) / 1024 ) ;

					} else {

						serviceMetricCollection.insert( 0, current + metricCollected.asInt( ) ) ;

					}

				} else {

					if ( metricCollected.isMissingNode( ) ) {

						serviceMetricCollection.insert( 0, -1 ) ;
						logger.warn( "Did not find {} in {}", metricStatusReportPath, serviceStatusReport ) ;
						continue ;

					}

					if ( metric.equals( OsProcessEnum.RSS_MEMORY ) ) {

						serviceMetricCollection.insert( 0, metricCollected.asInt( ) / 1024 ) ;

					} else {

						serviceMetricCollection.insert( 0, metricCollected.asInt( ) ) ;

					}

					if ( serviceMetricCollection.size( ) > getInMemoryCacheSize( ) ) {

						serviceMetricCollection.remove( serviceMetricCollection.size( ) - 1 ) ;

					}

				}

			}

		}

	}

	/**
	 *
	 *
	 */
	private void cleanUpServiceCache ( JsonNode latestServiceStatistics ) {

		// Iterator<String> service_names = servicesMapNodes.keySet().iterator();

		// List<String> services_to_remove = Stream.of(
		// service_to_metricsArrays.keySet().toArray( new String[ 0 ] ) )
		var collectionStaleServices = service_to_metricsArrays.keySet( ).stream( )
				.filter( serviceNameInCache -> {

					var serviceStatus = latestServiceStatistics.path( serviceNameInCache ) ;

					if ( kubernetes_services.containsKey( serviceNameInCache ) ) {

						String kubernetesServiceName = kubernetes_services.get( serviceNameInCache ) ;
						JsonNode kubernetesParent = latestServiceStatistics.path( kubernetesServiceName ) ;

						if ( ! kubernetesParent.isMissingNode( ) ) {

							int containerCount = kubernetesParent.path( ContainerState.JSON_KEY ).size( ) ;

							for ( int i = 0; i < containerCount; i++ ) {

								String kubernetesFullName = kubernetesServiceName + "-" + ( i + 1 ) ;

								if ( kubernetesFullName.equals( serviceNameInCache ) ) {

									serviceStatus = kubernetesParent ;
									break ;

								}

							}

						}

					}

					if ( ! serviceStatus.isMissingNode( ) ) {

						if ( stale_services.containsKey( serviceNameInCache ) ) {

							logger.info( "found {} - removing from stale_services list", serviceNameInCache ) ;
							stale_services.remove( serviceNameInCache ) ;

						}

					}

					return serviceStatus.isMissingNode( ) ;

				} )
				.collect( Collectors.toList( ) ) ;

		if ( collectionStaleServices.size( ) > 0 ) {

			for ( var serviceName : collectionStaleServices ) {

				if ( stale_services.containsKey( serviceName ) ) {

					var staleDurationMs = System.currentTimeMillis( ) - stale_services.get( serviceName ) ;

					if ( staleDurationMs > cleanStaleServicesMs ) {

						logger.warn(
								"Did not find the following service in latest collection, removing from collection cache: \n\t {}",
								serviceName ) ;

						service_to_metricsArrays.remove( serviceName ) ;
						kubernetes_services.remove( serviceName ) ;
						stale_services.remove( serviceName ) ;

					} else {

						logger.debug( "{} was last found: {}; it will be removed from host reports in {}",
								serviceName,
								CSAP.autoFormatMillis( staleDurationMs ),
								CSAP.autoFormatMillis( cleanStaleServicesMs ) ) ;

						addNullCollectionData( serviceName ) ;

					}

				} else {

					stale_services.put( serviceName, System.currentTimeMillis( ) ) ;

					addNullCollectionData( serviceName ) ;

				}

			}

		}

	}

	private void addNullCollectionData ( String serviceName ) {

		var serviceCollectionCache = service_to_metricsArrays.get( serviceName ) ;

		for ( OsProcessEnum os : OsProcessEnum.values( ) ) {

			var metric = os.value ;
			var metricFullName = metric + "_" + serviceName ;
			var serviceMetricCollection = ( (ArrayNode) serviceCollectionCache.get( metricFullName ) ) ;
			serviceMetricCollection.insert( 0, 0 ) ;

			if ( serviceMetricCollection.size( ) > getInMemoryCacheSize( ) ) {

				serviceMetricCollection.remove( serviceMetricCollection.size( ) - 1 ) ;

			}

		}

	}

	public final static String PROCESS_METRICS_EVENT = METRICS_EVENT + "/" + CsapApplication.COLLECTION_OS_PROCESS
			+ "/" ;;

	protected String uploadMetrics ( int iterationsBetweenAuditUploads ) {

		String result = "FAILED" ;

		try {

			String[] servicesArray = getAllCollectedServiceNames( ) ;
			ObjectNode metricSample = buildCollectionReport( true, servicesArray, iterationsBetweenAuditUploads, 0 ) ;

			// new Event publisher - it checks if publish is enabled. If
			// services have been updated, then attributes are uploaded again
			if ( isCacheNeedsPublishing ) {

				// full upload. We could make call to event service to see if
				// they match...for now we do on restarts
				csapApplication.getEventClient( ).publishEvent( PROCESS_METRICS_EVENT + collectionIntervalSeconds
						+ "/attributes",
						"Modified", null,
						attributeCacheJson ) ;
				isCacheNeedsPublishing = false ;

			}

			if ( processCorrellationAttributes == null ) {

				processCorrellationAttributes = jacksonMapper.createObjectNode( ) ;
				processCorrellationAttributes.put( "id", CsapApplication.COLLECTION_OS_PROCESS + "_"
						+ collectionIntervalSeconds ) ;
				processCorrellationAttributes.put( "hostName", csapApplication.getCsapHostShortname( ) ) ;

			}

			// Send normalized data
			metricSample.set( "attributes", processCorrellationAttributes ) ;
			csapApplication.getEventClient( ).publishEvent( PROCESS_METRICS_EVENT + collectionIntervalSeconds + "/data",
					"Upload", null,
					metricSample ) ;

			publishSummaryReport( CsapApplication.COLLECTION_OS_PROCESS ) ;

		} catch ( Exception e ) {

			logger.error( "Failed upload", e ) ;
			result = "Failed, Exception:"
					+ CSAP.buildCsapStack( e ) ;

		}

		return result ;

	}

	public String[] getAllCollectedServiceNames ( ) {

		String[] servicesArray = service_to_metricsArrays.keySet( ).toArray( new String[0] ) ;
		return servicesArray ;

	}

	private ObjectNode processCorrellationAttributes = null ;

	final OperatingSystemMXBean osStats = ManagementFactory
			.getOperatingSystemMXBean( ) ;

	final static int DEFAULT_SERVICES = 5 ;

	public ObjectNode getCollection (
										int requestedSampleSize ,
										int skipFirstItems ,
										String... services ) {

		if ( services[ 0 ].toLowerCase( ).equals( ALL_SERVICES ) ) {

			services = service_to_metricsArrays.keySet( ).toArray( new String[0] ) ;

		}

		return buildCollectionReport(
				false, services,
				requestedSampleSize,
				skipFirstItems ) ;

	}

	public static <K, V extends Comparable<? super V>> Map<K, V> sortByValue ( Map<K, V> map ) {

		List<Map.Entry<K, V>> list = new LinkedList<Map.Entry<K, V>>( map.entrySet( ) ) ;
		Collections.sort( list, new Comparator<Map.Entry<K, V>>( ) {
			public int compare ( Map.Entry<K, V> o1 , Map.Entry<K, V> o2 ) {

				return ( o2.getValue( ) ).compareTo( o1.getValue( ) ) ;

			}
		} ) ;

		Map<K, V> result = new LinkedHashMap<K, V>( ) ;

		for ( Map.Entry<K, V> entry : list ) {

			result.put( entry.getKey( ), entry.getValue( ) ) ;

		}

		return result ;

	}

	public ObjectNode buildCollectionReport (
												boolean isUpdateSummary ,
												String[] serviceNamesRequested ,
												int requestedSampleSize ,
												int skipFirstItems ,
												String... customArgs ) {

		ObjectNode serviceMetricsReport = jacksonMapper.createObjectNode( ) ;

		var servicesFilter = mapRequestedToServiceName( serviceNamesRequested ) ;

		ObjectNode attributes = generateAttributes( servicesFilter, requestedSampleSize, skipFirstItems ) ;
		serviceMetricsReport.set( "attributes", attributes ) ;

		ObjectNode dataSection = serviceMetricsReport.putObject( DATA_JSON ) ;
		ArrayNode timestamps = dataSection.putArray( "timeStamp" ) ;
		ArrayNode cpuTotals = dataSection.putArray( "totalCpu" ) ;

		int numSamples = 0 ;

		if ( requestedSampleSize == -1 ) {

			timestamps.addAll( timeStampArray ) ;
			cpuTotals.addAll( totalCpuArray ) ;

		} else {

			for ( int i = 0; i < requestedSampleSize
					&& i < timeStampArray.size( ); i++ ) {

				timestamps.add( timeStampArray.path( i ) ) ;
				cpuTotals.add( totalCpuArray.path( i ) ) ;
				numSamples++ ;

			}

		}

		addSummary(
				updateMetricsDataAndbuildSummaryReport(
						numSamples, requestedSampleSize, servicesFilter,
						attributes,
						dataSection ),
				isUpdateSummary ) ;

		return serviceMetricsReport ;

	}

	private List<String> mapRequestedToServiceName ( String[] serviceNamesRequested ) {

		List<String> serviceNames = new ArrayList<String>( ) ;

		if ( serviceNamesRequested == null ) {

			// if no services are specified - then ONLY the top 5 will be
			// returned

			Map<String, Integer> sumMap = new HashMap<String, Integer>( ) ;

			for ( String serviceName : service_to_metricsArrays.keySet( ) ) {

				ObjectNode serviceCacheNode = service_to_metricsArrays.get( serviceName ) ;
				String metricFullName = "topCpu" + "_" + serviceName ;
				ArrayNode cacheArray = (ArrayNode) serviceCacheNode
						.get( metricFullName ) ;

				int summ = 0 ;

				for ( JsonNode node : cacheArray ) {

					summ += node.asInt( ) ;

				}

				sumMap.put( serviceName, summ ) ;

			}

			Map<String, Integer> sumByValue = sortByValue( sumMap ) ;

			logger.debug( "Sorted by value services: {}", sumByValue ) ;

			int i = 0 ;

			for ( Map.Entry<String, Integer> entry : sumByValue.entrySet( ) ) {

				serviceNames.add( entry.getKey( ) ) ;

				if ( ++i >= DEFAULT_SERVICES ) {

					break ;

				}

			}

		} else {

			serviceNames = Arrays.asList( serviceNamesRequested ) ;

		}

		var servicesFilter = serviceNames ;
		logger.debug( "serviceNames: {}", serviceNames ) ;
		// Support for autostripping of port when not found
		// logger.info( "{}", servicesMapNodes );
//		ArrayList<String> servicesFilter = new ArrayList<>( ) ;
//		for ( var serviceName : serviceNames ) {
//			if ( serviceName.contains( "_" ) ) {
//				ObjectNode serviceCacheNode = service_to_metricsArrays.get( serviceName ) ;
//				// logger.info( "serviceCacheNode: {}", serviceCacheNode );
//				if ( serviceCacheNode == null ) {
//					String nameOnly = serviceName.split( "_" )[0] ;
//					// logger.info( "checking for {} in {}", nameOnly,
//					// servicesMapNodes.get( nameOnly ) );
//					servicesFilter.add( nameOnly ) ;
//
//				}
//			}
//		}
//		servicesFilter.addAll( serviceNames ) ;
		// logger.info("numSamples: " + numSamples + " skipFirstItems: " +
		// skipFirstItems ) ;
		return servicesFilter ;

	}

	private ObjectNode updateMetricsDataAndbuildSummaryReport (
																int numSamples ,
																int requestedSampleSize ,
																List<String> serviceIds ,
																ObjectNode metricsAttributeSection ,
																ObjectNode metricsDataSection ) {

		logger.debug( "numSamples: {}, requestedSampleSize: {}, serviceNames: {}",
				numSamples, requestedSampleSize, serviceIds ) ;

		ObjectNode allServicesSummaryReport = jacksonMapper.createObjectNode( ) ;

		for ( var serviceId : serviceIds ) {

			//
			// summary is per HOST: if service is kubernetes - strip of container suffix
			ServiceInstance serviceInstance = csapApplication.flexFindFirstInstanceCurrentHost( serviceId ) ;

			if ( serviceInstance == null ) {

				logger.debug( "Did not locate: {}", serviceId ) ;
				continue ;

			}

			var serviceName = serviceInstance.getName( ) ;

			ObjectNode serviceSummaryReport ;

			if ( allServicesSummaryReport.has( serviceName ) ) {

				serviceSummaryReport = (ObjectNode) allServicesSummaryReport.path( serviceName ) ;

			} else {

				serviceSummaryReport = allServicesSummaryReport.putObject( serviceName ) ;

			}

			ObjectNode serviceOsMetrics = service_to_metricsArrays.get( serviceId ) ;

			if ( serviceOsMetrics != null ) {
				// logger.info( "serviceName: {}, summaryName: {}, serviceCacheNode {}",
				// serviceName, summaryName, CSAP.jsonPrint( serviceCacheNode ) ) ;

				serviceSummaryReport.put( "numberOfSamples",
						numSamples + serviceSummaryReport.path( "numberOfSamples" ).asInt( 0 ) ) ;

				// add container counts for calculating total - unless - it is in aggregation
				// mode
				serviceSummaryReport.put( "countCsapMean",
						1 + serviceSummaryReport.path( "countCsapMean" ).asInt( 0 ) ) ;

//				if ( serviceName.equals( "csap-test-k8s-service"  )) {
//					serviceSummaryReport.put( "countCsapMean", rg.nextInt( 30 )  ) ;
//				}

				for ( OsProcessEnum os : OsProcessEnum.values( ) ) {

					String metric = os.value ;
					String metricFullName = metric + "_" + serviceId ;
					ArrayNode metricArray = metricsDataSection.putArray( metricFullName ) ;
					ArrayNode cacheArray = (ArrayNode) serviceOsMetrics.get( metricFullName ) ;

					if ( requestedSampleSize == -1 ) {

						metricArray.addAll( cacheArray ) ;

					} else {

						int metricTotal = 0 ;

						for ( int i = 0; i < requestedSampleSize
								&& i < cacheArray.size( ); i++ ) {

							metricArray.add( cacheArray.path( i ) ) ;
							metricTotal += cacheArray.path( i ).asInt( ) ;

						}

						serviceSummaryReport.put( metric, metricTotal + serviceSummaryReport.path( metric ).asInt(
								0 ) ) ;

					}

				}

			} else {

				( (ArrayNode) metricsAttributeSection.get( "servicesNotFound" ) ).add( serviceId ) ;

			}

		}

		// logger.info( "allServicesSummaryReport {}", CSAP.jsonPrint(
		// allServicesSummaryReport ) ) ;
		return allServicesSummaryReport ;

	}

	// only cache for all services.
	private ObjectNode attributeCacheJson = null ;
	private int serviceCacheHash = 0 ;

	private boolean isCacheNeedsPublishing = true ;

	private ObjectNode generateAttributes (
											List<String> servicesFilter ,
											int requestedSampleSize ,
											int skipFirstItems ) {

		// hash codes are used to identify when cluster definition has been
		// updated with new services
		int requestHashValue = servicesFilter.toString( ).hashCode( ) ;

		boolean isFullServicesRequest = servicesFilter.size( ) == service_to_metricsArrays.keySet( ).size( ) ;

		if ( attributeCacheJson != null && isFullServicesRequest ) {

			if ( requestHashValue == serviceCacheHash ) {

				logger.debug( "Using Cached attributes" ) ;
				return attributeCacheJson ;

			}

		}

		ObjectNode attributeJson = jacksonMapper.createObjectNode( ) ;

		attributeJson.put( "id", CsapApplication.COLLECTION_OS_PROCESS + "_" + collectionIntervalSeconds ) ;
		attributeJson.put( "metricName", "Service Resources" ) ;
		attributeJson.put( "description", "Contains service metrics" ) ;
		attributeJson.put( "timezone", TIME_ZONE_OFFSET ) ;
		attributeJson.put( "hostName", csapApplication.getCsapHostShortname( ) ) ;
		attributeJson.put( "sampleInterval", collectionIntervalSeconds ) ;
		attributeJson.put( "samplesRequested", requestedSampleSize ) ;
		attributeJson.put( "samplesOffset", skipFirstItems ) ;
		attributeJson.put( "currentTimeMillis", System.currentTimeMillis( ) ) ;
		attributeJson.put( "cpuCount", osStats.getAvailableProcessors( ) ) ;

		ArrayNode servicesAvailArray = attributeJson
				.putArray( "servicesAvailable" ) ;

		for ( String serviceName : service_to_metricsArrays.keySet( ) ) {

			servicesAvailArray.add( serviceName ) ;

		}

		ArrayNode servicesReqArray = attributeJson
				.putArray( "servicesRequested" ) ;

		for ( String serviceName : servicesFilter ) {

			servicesReqArray.add( serviceName ) ;

		}

		attributeJson
				.putArray( "servicesNotFound" ) ;

		ObjectNode graphsObject = attributeJson.putObject( "graphs" ) ;
		// ObjectNode titlesObject = attributeJson.putObject( "titles" );
		attributeJson.set( "titles", OsProcessEnum.graphLabels( ) ) ;

		for ( OsProcessEnum os : OsProcessEnum.values( ) ) {

			String metric = os.value ;
			String desc = metric ;
			// if ( desc.equals( "rssMemory" ) || desc.equals( "diskUtil" ) ) {
			// desc += "InMB";
			// }
			//
			// if ( desc.equals( "topCpu" ) ) {
			// desc = "Cpu_" + csapApplication.getTopInterval() + "s";
			// }

			ObjectNode resourceGraph = graphsObject.putObject( desc ) ;

			for ( String serviceName : servicesFilter ) {

				String metricFullName = metric + "_" + serviceName ;
				resourceGraph.put( metricFullName, serviceName ) ;

			}

			// Added at the bottom so colors match across graphs
			if ( metric.equals( "topCpu" ) ) {

				resourceGraph.put( "totalCpu",
						"VM (OS+App)" ) ;

				// resourceGraph.put("totalCpu",
				// "VM (Max: " + (osStats.getAvailableProcessors() * 100)
				// + ")");
			}

		}

		if ( isFullServicesRequest ) {

			attributeCacheJson = attributeJson ;
			serviceCacheHash = requestHashValue ;
			isCacheNeedsPublishing = true ;
			logger.debug( "Updated attributes cache: \n {}", attributeCacheJson ) ;

		}

		return attributeJson ;

	}
}
