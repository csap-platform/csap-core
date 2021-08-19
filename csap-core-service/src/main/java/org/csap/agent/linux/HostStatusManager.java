package org.csap.agent.linux ;

import java.io.IOException ;
import java.util.ArrayList ;
import java.util.Comparator ;
import java.util.HashMap ;
import java.util.HashSet ;
import java.util.List ;
import java.util.Map ;
import java.util.Map.Entry ;
import java.util.Optional ;
import java.util.Set ;
import java.util.concurrent.Callable ;
import java.util.concurrent.ConcurrentSkipListMap ;
import java.util.concurrent.CopyOnWriteArrayList ;
import java.util.concurrent.ExecutorCompletionService ;
import java.util.concurrent.ExecutorService ;
import java.util.concurrent.Executors ;
import java.util.concurrent.Future ;
import java.util.concurrent.ScheduledExecutorService ;
import java.util.concurrent.ScheduledFuture ;
import java.util.concurrent.TimeUnit ;
import java.util.concurrent.atomic.AtomicBoolean ;
import java.util.concurrent.atomic.AtomicInteger ;
import java.util.concurrent.atomic.AtomicLong ;
import java.util.concurrent.locks.ReentrantLock ;
import java.util.regex.Pattern ;
import java.util.stream.Collectors ;

import javax.net.ssl.SSLHandshakeException ;

import org.apache.commons.lang3.concurrent.BasicThreadFactory ;
import org.apache.commons.lang3.exception.ExceptionUtils ;
import org.csap.agent.CsapCoreService ;
import org.csap.agent.api.AgentApi ;
import org.csap.agent.model.Application ;
import org.csap.agent.model.ContainerState ;
import org.csap.agent.model.Project ;
import org.csap.agent.model.ServiceInstance ;
import org.csap.agent.services.HostKeys ;
import org.csap.agent.stats.MetricCategory ;
import org.csap.agent.stats.OsProcessEnum ;
import org.csap.agent.ui.rest.HostRequests ;
import org.csap.alerts.AlertFields ;
import org.csap.alerts.AlertInstance ;
import org.csap.alerts.AlertInstance.AlertItem ;
import org.csap.helpers.CSAP ;
import org.csap.helpers.CsapSimpleCache ;
import org.csap.integations.CsapMicroMeter ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.web.client.RestTemplate ;

import com.fasterxml.jackson.core.JsonParseException ;
import com.fasterxml.jackson.core.type.TypeReference ;
import com.fasterxml.jackson.databind.JsonMappingException ;
import com.fasterxml.jackson.databind.JsonNode ;
import com.fasterxml.jackson.databind.ObjectMapper ;
import com.fasterxml.jackson.databind.node.ArrayNode ;
import com.fasterxml.jackson.databind.node.ObjectNode ;

/**
 *
 * Context: Csap Application contains 1-n Hosts, with 1-M services,
 * 
 * HostStatusManager Provides: - access to host and service status, via
 * scheduled retrieval and on demand from UI or REST APIs - rest api call to
 * agents retrieve agent runtime data - UI/API calls can interrupt scheduled
 * updates, and then reschedule
 *
 * @author Peter Nightingale
 *
 */
public class HostStatusManager {

	private static final String ERROR_KEY = "error" ;
	final Logger logger = LoggerFactory.getLogger( HostStatusManager.class ) ;

	Application csapApp = null ;
	private ObjectMapper jsonMapper = new ObjectMapper( ) ;

	/**
	 * Agent Health collection Scheduling
	 */
	private ScheduledExecutorService hostStatusScheduler ;
	private ScheduledFuture<?> hostStatusSchedulerJob = null ;
	private ReentrantLock host_queries_in_progress_lock = new ReentrantLock( ) ;

	/**
	 * Agent Health collection threads
	 */
	private ExecutorService hostStatusWorkers ;
	private ExecutorCompletionService<AgentStatus> hostStatusService ;
	private CopyOnWriteArrayList<String> hostList ;
	private ConcurrentSkipListMap<String, ObjectNode> hostResponseMap = new ConcurrentSkipListMap<String, ObjectNode>( ) ;

	/**
	 * Service Health Reporting
	 */
	private ConcurrentSkipListMap<String, String> lastCollectedServiceHeathReportTimes = new ConcurrentSkipListMap<String, String>( ) ;
	private ArrayNode alertHistory = jsonMapper.createArrayNode( ) ;
	private ArrayNode alertsThrottled = jsonMapper.createArrayNode( ) ;
	private CsapSimpleCache alertThrottleTimer ;

	public HostStatusManager (
			Application csapApplication,
			int numberOfThreads,
			ArrayList<String> hostsToQuery ) {

		this.csapApp = csapApplication ;

		csapApp.loadCollectionCacheFromDisk( getAlertHistory( ), this.getClass( ).getSimpleName( ) ) ;

		alertThrottleTimer = CsapSimpleCache.builder(
				csapApplication.getCsapCoreService( ).getAlerts( ).getThrottle( ).getFrequency( ),
				CSAP.parseTimeUnit(
						csapApplication.getCsapCoreService( ).getAlerts( ).getThrottle( ).getTimeUnit( ),
						TimeUnit.HOURS ),
				HostStatusManager.class,
				"Global Alert Throttle" ) ;

		logger.warn(
				"csap-agent monitoring \n\t thread count: {} \n\t connectionTimeout: {} \n\t Host Count: {} \n\t Hosts: {} \n\n\t alert definitions: {}",
				numberOfThreads, csapApplication.rootProjectEnvSettings( ).getAdminToAgentTimeoutSeconds( ),
				hostsToQuery.size( ), hostsToQuery,
				csapApplication.getCsapCoreService( ).getAlerts( ) ) ;

		BasicThreadFactory statusFactory = new BasicThreadFactory.Builder( )
				.namingPattern( "CsapHostStatus-%d" )
				.daemon( true )
				.priority( Thread.NORM_PRIORITY )
				.build( ) ;

		hostStatusWorkers = Executors.newFixedThreadPool( numberOfThreads, statusFactory ) ;

		hostStatusService = new ExecutorCompletionService<AgentStatus>( hostStatusWorkers ) ;

		hostList = new CopyOnWriteArrayList<String>( hostsToQuery ) ;

		initialize_refresh_worker( ) ;
		restartHostRefreshTimer( 3 ) ;

	}

	private void initialize_refresh_worker ( ) {

		BasicThreadFactory schedFactory = new BasicThreadFactory.Builder( )
				.namingPattern( "CsapHostJobsScheduler-%d" )
				.daemon( true )
				.priority( Thread.NORM_PRIORITY )
				.build( ) ;

		hostStatusScheduler = Executors.newScheduledThreadPool( 1, schedFactory ) ;

	}

	private void restartHostRefreshTimer ( int initialDelaySeconds ) {

		hostStatusSchedulerJob = hostStatusScheduler
				.scheduleWithFixedDelay(
						( ) -> runScheduledRefreshes( ),
						initialDelaySeconds,
						csapApp.getHostRefreshIntervalSeconds( ),
						TimeUnit.SECONDS ) ;

	}

	/**
	 * For testing only
	 *
	 * @param testHostResponse
	 */
	public HostStatusManager ( String stubResponseFilePath, Application csapApplication ) {

		logger.warn( "\n ************** Running in Stub Mode: {} \n", stubResponseFilePath ) ;
		this.isTest = true ;
		this.stubResponseFilePath = stubResponseFilePath ;
		this.csapApp = csapApplication ;

		// initRestTemplate( 1 );
	}

	private boolean isTest = false ;
	private String stubResponseFilePath ;

	public void loadStubbedHostResponses ( ) {

		try {

			var hostReports = jsonMapper.readValue(
					csapApp.check_for_stub( "", stubResponseFilePath ),
					ObjectNode.class ) ;

			CSAP.jsonStream( hostReports )
					.forEach( hostReport -> {

						hostResponseMap.put( hostReport.path( "collectedHost" ).asText( ), (ObjectNode) hostReport ) ;

					} ) ;
			;

		} catch ( Exception e ) {

			logger.warn( CSAP.buildCsapStack( e ) ) ;

		}

	}

	/**
	 * Kill off the spawned threads - triggered from ServiceRequests
	 */
	public void shutdown ( ) {

		System.out.println( "\n Shutting down jobs in " + HostStatusManager.class.getName( ) ) ;

		// restFactory.shutdown();

		if ( hostStatusSchedulerJob != null ) {

			hostStatusScheduler.shutdownNow( ) ;

		}

		if ( hostStatusWorkers != null ) {

			hostStatusWorkers.shutdownNow( ) ;

		}

		csapApp.flushCollectionCacheToDisk( getAllAlerts( ), this.getClass( ).getSimpleName( ) ) ;

	}

	public int totalOpsQueued ( ) {

		int totalOpsQueued = hostResponseMap
				.values( )
				.stream( )
				.mapToInt( hostRuntime -> {

					if ( hostRuntime != null && hostRuntime.has( "serviceOpsQueue" ) ) {

						return hostRuntime.get( "serviceOpsQueue" ).asInt( 0 ) ;

					}

					return 0 ;

				} )
				.sum( ) ;

		return totalOpsQueued ;

	}

	public String getHostWithLowestAttribute ( List<String> hostNames , String attributeName ) {

		String result = hostNames.get( 0 ) ;

		//
		// hostRuntimeInJsonMap
		// .values()
		// .forEach(System.out::println);
		Comparator<Entry<String, ObjectNode>> compareAttribute = //
				( host1Entry , host2Entry ) -> {

					ObjectNode vm1Json = host1Entry.getValue( ) ;
					ObjectNode vm2Json = host2Entry.getValue( ) ;

					logger.debug(
							"Entry1: {}, attributeName: {}, value: {}, Entry2: {}, attributeName: {}, value: {}",
							host1Entry.getKey( ), attributeName,
							vm1Json.at( attributeName ).asDouble( ),
							host2Entry.getKey( ), attributeName,
							vm2Json.at( attributeName ).asDouble( ) ) ;

					return Double.compare( vm1Json.at( attributeName ).asDouble( ),
							vm2Json.at( attributeName ).asDouble( ) ) ;

					// return Integer.compare( entry1.getValue().length(),
					// entry2.getValue().length() );
				} ;

		Optional<Entry<String, ObjectNode>> hostWithLowest = hostResponseMap
				.entrySet( )
				.stream( )
				.filter( hostEntry -> hostNames.contains( hostEntry.getKey( ) ) )
				.min( compareAttribute ) ;

		if ( hostWithLowest.isPresent( ) ) {

			result = hostWithLowest.get( ).getKey( ) ;

		}

		return result ;

	}

	public Map<String, ObjectNode> hostsInfo ( List<String> hosts ) {

		return hosts.stream( )
				.map( host -> {

					ObjectNode hostRuntime = hostResponseMap.get( host ) ;
					// logger.info(hostRuntime.toString()) ;
					ObjectNode hostConfiguration = jsonMapper.createObjectNode( ) ;
					hostConfiguration.put( "collectedHost", host ) ;

					if ( hostRuntime == null ||
							( hostRuntime != null && hostRuntime.has( ERROR_KEY ) ) ) {

						hostConfiguration.put( ERROR_KEY, "Collection failed" ) ;

					} else {

						hostConfiguration.put( "timeStamp", hostRuntime.get( "timeStamp" ).asText( ) ) ;
						JsonNode hostStats = hostRuntime.get( "hostStats" ) ;
						hostConfiguration.set( "hostStats", hostStats ) ;

					}

					return hostConfiguration ;

				} )
				.collect( Collectors.toMap(
						hostStatus -> hostStatus.get( "collectedHost" ).asText( ),
						hostStatus -> hostStatus ) ) ;

	}

	public Set<String> findRunningKubernetesServices ( List<String> lifecycleHosts ) {

		Set<String> kubernetesRunningServices = new HashSet<>( ) ;

		List<String> kubernetesAllServices = csapApp.getActiveProject( ).getAllPackagesModel( )
				.getServiceConfigStreamInCurrentLC( )
				.flatMap( serviceInstancesEntry -> serviceInstancesEntry.getValue( ).stream( ) )
				.filter( ServiceInstance::is_cluster_kubernetes )
				.map( ServiceInstance::getServiceName_Port )
				.distinct( )
				.collect( Collectors.toList( ) ) ;

		for ( String host : lifecycleHosts ) {

			JsonNode hostReport = getHostAsJson( host ) ;

			if ( hostReport != null ) {

				JsonNode collected_services = hostReport.path( "services" ) ;
				kubernetesAllServices.stream( ).forEach( serviceName -> {

					try {

						if ( collected_services.has( serviceName ) ) {

							ServiceInstance serviceWithRuntimeStats = //
									ServiceInstance.buildInstance( jsonMapper,
											collected_services.get( serviceName ) ) ;

							for ( ContainerState containerState : serviceWithRuntimeStats.getContainerStatusList( ) ) {

								if ( containerState.isActive( ) ) {

									kubernetesRunningServices.add( serviceWithRuntimeStats.getName( ) ) ;

								}

							}

						}

					} catch ( Exception e ) {

						logger.warn( "{}", CSAP.buildCsapStack( e ) ) ;

					}

				} ) ;

			} else {

				logger.debug( "No response from {}", host ) ;

				// errorList.add( host + " - Agent response does not contain hostStats." ) ;
			}

		}

		return kubernetesRunningServices ;

	}

	public ArrayNode serviceReports ( String serviceName ) {

		var services = jsonMapper.createArrayNode( ) ;

		hostResponseMap.values( ).stream( )
				.map( hostReport -> hostReport.at( "/services/" + serviceName ) )
				.filter( JsonNode::isObject ) ;

		return services ;

	}

	public ObjectNode serviceCollectionReport ( List<String> hosts , String serviceFilter , String attributeFilter ) {

		if ( hosts == null ) {

			hosts = hostResponseMap.keySet( ).stream( ).collect( Collectors.toList( ) ) ;

		}

		logger.debug( "serviceFilter: {}, attributeFilter:{}, hosts: {}", serviceFilter, attributeFilter, hosts ) ;

		Map<String, ObjectNode> hostToReport = hosts.stream( )
				.map( host -> {

					ObjectNode hostRuntime = hostResponseMap.get( host ) ;
					// logger.info(hostRuntime.toString()) ;
					ObjectNode hostFilteredRuntime = jsonMapper.createObjectNode( ) ;
					hostFilteredRuntime.put( "collectedHost", host ) ;

					if ( hostRuntime == null ||
							( hostRuntime != null && hostRuntime.has( ERROR_KEY ) ) ) {

						hostFilteredRuntime.put( ERROR_KEY, "Collection failed" ) ;

					} else {

						hostFilteredRuntime.put( "timeStamp", hostRuntime.get( "timeStamp" ).asText( ) ) ;
						JsonNode servicesCollected = hostRuntime.get( "services" ) ;

						if ( serviceFilter == null ) {

							hostFilteredRuntime.set( "services", servicesCollected ) ;

						} else if ( serviceFilter.equals( HostKeys.hostStats.json( ) ) ) {

							hostFilteredRuntime.set( HostKeys.hostStats.json( ), hostRuntime.path( HostKeys.hostStats
									.json( ) ) ) ;

						} else {

							ObjectNode servicesFiltered = jsonMapper.createObjectNode( ) ;
							hostFilteredRuntime.set( "services", servicesFiltered ) ;
							servicesCollected.fieldNames( ).forEachRemaining( servicePortName -> {

								var filterInstance = csapApp.findFirstServiceInstanceInLifecycle( serviceFilter ) ;
								var filterMatchByName = false ;

								if ( filterInstance != null ) {

									filterMatchByName = servicePortName.equals( filterInstance
											.getServiceName_Port( ) ) ;

								}

								if ( filterMatchByName || servicePortName.matches( serviceFilter ) ) {

									if ( attributeFilter == null ) {

										servicesFiltered.set( servicePortName, servicesCollected.get(
												servicePortName ) ) ;

									} else {

										ObjectNode attributeFiltered = jsonMapper.createObjectNode( ) ;
										JsonNode allAttributes = servicesCollected.get( servicePortName ) ;
										var matches = allAttributes.findValues( attributeFilter ) ;

										if ( matches.size( ) > 0 ) {

											var matchValues = attributeFiltered.putArray( attributeFilter ) ;
											matches.stream( )
													.map( JsonNode::asText )
													.forEach( matchValues::add ) ;

										}

										servicesFiltered.set( servicePortName, attributeFiltered ) ;

									}

								}

							} ) ;

						}

					}

					return hostFilteredRuntime ;

				} )
				.collect( Collectors.toMap(
						hostStatus -> hostStatus.get( "collectedHost" ).asText( ),
						hostStatus -> hostStatus ) ) ;

		return jsonMapper.convertValue( hostToReport, ObjectNode.class ) ;

	}

	public Map<String, ObjectNode> hostsCpuInfo ( List<String> hosts ) {

		return hosts.stream( )
				.map( host -> {

					ObjectNode hostRuntime = hostResponseMap.get( host ) ;
					// logger.info(hostRuntime.toString()) ;
					ObjectNode hostConfiguration = jsonMapper.createObjectNode( ) ;
					hostConfiguration.put( "collectedHost", host ) ;

					if ( hostRuntime == null ||
							( hostRuntime != null && hostRuntime.has( ERROR_KEY ) ) ) {

						hostConfiguration.put( ERROR_KEY, "Collection failed" ) ;

					} else {

						hostConfiguration.put( "timeStamp", hostRuntime.get( "timeStamp" ).asText( ) ) ;
						JsonNode hostStats = hostRuntime.get( "hostStats" ) ;
						hostConfiguration.put( "cpuLoad", hostStats.get( "cpuLoad" ).asText( ) ) ;
						hostConfiguration.put( "cpuCount", hostStats.get( "cpuCount" ).asText( ) ) ;
						hostConfiguration.put( "cpu", hostStats.get( "cpu" ).asText( ) ) ;
						hostConfiguration.put( "cpuIoWait", hostStats.get( "cpuIoWait" ).asText( ) ) ;

					}

					return hostConfiguration ;

				} )
				.collect( Collectors.toMap(
						hostStatus -> hostStatus.get( "collectedHost" ).asText( ),
						hostStatus -> hostStatus ) ) ;

	}

	public Map<String, ObjectNode> hostsRuntime ( List<String> hosts ) {

		return hosts.stream( )
				.map( host -> {

					ObjectNode hostRuntime = getResponseFromHost( host ) ;

					if ( hostRuntime == null ) {

						hostRuntime = jsonMapper.createObjectNode( ) ;
						hostRuntime.put( ERROR_KEY, "No response found" ) ;

					}

					hostRuntime.put( "collectedHost", host ) ;
					return hostRuntime ;

				} )
				.collect( Collectors.toMap(
						hostStatus -> hostStatus.get( "collectedHost" ).asText( ),
						hostStatus -> hostStatus ) ) ;

	}

	public ArrayNode hostsBacklog ( List<String> hosts ) {

		ArrayNode backlog = jsonMapper.createArrayNode( ) ;

		hosts.stream( )
				.forEach( host -> {

					ObjectNode hostBacklog = backlog.addObject( ) ;
					hostBacklog.put( "host", host ) ;
					ObjectNode hostRuntime = hostResponseMap.get( host ) ;
					int totalBacklog = -1 ;

					if ( hostRuntime != null ) {

						totalBacklog = hostRuntime.path( "serviceOpsQueue" ).asInt( 0 ) ;

					}

					hostBacklog.put( "total-backlog", totalBacklog ) ;
					hostBacklog.put( "host-details", csapApp.getAgentUrl( host, "/api/agent/service/jobs" ) ) ;

				} ) ;

		return backlog ;

	}

	/**
	 * 
	 * real time meters appear on application landing page
	 * 
	 */
	public void updateRealTimeMeters (
										ArrayNode meterReports ,
										List<String> environmentHosts ,
										List<String> detailMeters ) {

		if ( logger.isDebugEnabled( ) ) { // isDebugEnabled isInfoEnabled

			List<ObjectNode> hostJsons = environmentHosts.stream( )
					.map( host -> getHostAsJson( host ) )
					.filter( java.util.Objects::nonNull )
					.filter( hostJson -> hostJson.has( HostKeys.lastCollected.jsonId ) )
					.filter( hostJson -> ! hostJson.get( HostKeys.lastCollected.jsonId ).has( "warning" ) )
					.map( hostJson -> (ObjectNode) hostJson.get( HostKeys.lastCollected.jsonId ) )
					.collect( Collectors.toList( ) ) ;

			logger.debug( "lastCollected: {}", hostJsons ) ;

		}

		for ( var meterReportDefinition : meterReports ) {

			var meterReport = (ObjectNode) meterReportDefinition ;
			String id = meterReport.path( "id" ).asText( ) ;
			String[] idParts = id.split( Pattern.quote( "." ) ) ;
			String collector = idParts[0] ;
			String attribute = idParts[1] ;
			double hostTotal = 0 ;
			meterReport.put( MetricCategory.hostCount.json( ), 0 ) ;

			logger.debug( "id: {} reportHosts: {}", id, environmentHosts ) ;

			try {

				if ( ! collector.equals( MetricCategory.application.json( ) ) ) {

					hostTotal = environmentHosts
							.stream( )
							.map( host -> getHostAsJson( host ) )
							.filter( java.util.Objects::nonNull )
							.filter( hostJson -> hostJson.has( HostKeys.lastCollected.jsonId ) )
							.filter( hostJson -> hostJson.get( HostKeys.lastCollected.jsonId ).has( collector ) )
							.map( hostJson -> (ObjectNode) hostJson.get( HostKeys.lastCollected.jsonId ).get(
									collector ) )
							.filter( collectionJson -> collectionJson.has( attribute ) )
							.mapToInt(
									collectionJson -> getCollectedMetric( meterReport, attribute, collector,
											collectionJson ) )
							.sum( ) ;

					if ( detailMeters == null || detailMeters.contains( id ) ) {

						addMetricHostValues( environmentHosts, meterReport, collector, attribute, null, null ) ;

					}

				} else {

					// jmx custom / application metrics have nested data.
					String serviceName = idParts[1] ;
					final String serviceAttribute = idParts[2] ;

					var appHosts = new ArrayList<>( environmentHosts ) ;

					if ( id.startsWith( "application.kubelet" ) ) {

						var podReportnames = csapApp.rootProjectEnvSettings( ).getKubernetesMeters( ) ;

						if ( podReportnames.isArray( ) ) {

							logger.debug( "Filtering kubernetes collection meters {}", podReportnames ) ;
							var podMatch = CSAP.jsonStream( podReportnames )
									.filter( JsonNode::isTextual )
									.map( JsonNode::asText )
									.filter( podName -> {

										if ( id.endsWith( csapApp.getProjectLoader( ).podTotalCountName( podName ) ) ) {

											return true ;

										} else if ( id.endsWith( csapApp.getProjectLoader( ).podCoreName(
												podName ) ) ) {

											return true ;

										} else if ( id.endsWith( csapApp.getProjectLoader( ).podMemoryName(
												podName ) ) ) {

											return true ;

										}

										return false ;

									} )
									.findFirst( ) ;

							if ( podMatch.isPresent( ) ) {

								for ( var host : environmentHosts ) {

									var hostCollection = getHostAsJson( host ) ;

									if ( hostCollection != null ) {

										var isPresent = hostCollection.path( HostKeys.lastCollected.jsonId ).path(
												collector ).has( attribute ) ;

										if ( isPresent ) {

											appHosts.clear( ) ;
											appHosts.add( host ) ;
											break ;

										}

									}

								}

							}

						}

					}

					logger.debug( "searchin id  {} in {} ", id, appHosts ) ;

					hostTotal = appHosts
							.stream( )
							.map( host -> getHostAsJson( host ) )
							.filter( java.util.Objects::nonNull )
							.filter( hostJson -> hostJson.path( HostKeys.lastCollected.jsonId ).has( collector ) )
							.map( hostJson -> (ObjectNode) hostJson.path( HostKeys.lastCollected.jsonId ).path(
									collector ) )
							.filter( collectionJson -> collectionJson.has( attribute ) )
							.mapToInt(
									collectionJson -> getCollectedMetric( meterReport, serviceAttribute, collector,
											(ObjectNode) collectionJson.get( serviceName ) ) )
							.sum( ) ;

					if ( detailMeters == null || detailMeters.contains( id ) ) {

						addMetricHostValues( appHosts, meterReport, collector, attribute, serviceAttribute,
								serviceName ) ;

					}

				}

				if ( meterReport.path( MetricCategory.hostCount.json( ) ).asInt( ) > 0
						&& meterReport.has( "average" )
						&& meterReport.get( "average" ).asBoolean( ) ) {

					meterReport.put( "collectedTotal", hostTotal ) ;
					hostTotal = hostTotal / meterReport.get( MetricCategory.hostCount.json( ) ).asInt( ) ;

				}

				meterReport.put( "value", hostTotal ) ;

				logger.debug( "collector: {}, attribute: {}, total: {}", collector, attribute, hostTotal ) ;

			} catch ( Exception e ) {

				logger.warn( "Exception while process ing realtime meters: {} {}", meterReport, CSAP.buildCsapStack(
						e ) ) ;

			}

		}

	}

	public void addMetricHostValues (
										List<String> hosts ,
										ObjectNode meterJson ,
										String collector ,
										String attribute ,
										String serviceAttribute ,
										String serviceName ) {

		final String detailAttribute ;

		if ( serviceAttribute != null ) {

			detailAttribute = serviceAttribute ;

		} else {

			detailAttribute = attribute ;

		}

		for ( String detailHost : hosts ) {

			List<String> detailHosts = new ArrayList<String>( ) ;
			detailHosts.add( detailHost ) ;
			detailHosts
					.stream( )
					.map( host -> getHostAsJson( host ) )
					.filter( java.util.Objects::nonNull )
					.filter( hostJson -> hostJson.has( HostKeys.lastCollected.jsonId ) )
					.filter( hostJson -> hostJson.get( HostKeys.lastCollected.jsonId ).has( collector ) )
					.map( hostJson -> (ObjectNode) hostJson.get( HostKeys.lastCollected.jsonId ).get( collector ) )
					.filter( collectionJson -> collectionJson.has( attribute ) )
					.forEach(
							collectionJson -> addDetail( meterJson, detailHost, detailAttribute, collectionJson,
									serviceName ) ) ;

		}

	}

	private void addDetail (
								ObjectNode meterJson ,
								String detailHost ,
								String attribute ,
								ObjectNode collectionJson ,
								String serviceName ) {

		logger.debug( "detailHost: {}, attribute: {}, collectionJson: {}", detailHost, attribute, collectionJson ) ;

		if ( serviceName != null ) {

			collectionJson = (ObjectNode) collectionJson.get( serviceName ) ;

		}

		if ( ! meterJson.has( "hostNames" ) ) {

			meterJson.putArray( "hostNames" ) ;
			meterJson.putArray( "hostValues" ) ;

		}

		ArrayNode hostValues = (ArrayNode) meterJson.get( "hostValues" ) ;
		ArrayNode hostNames = (ArrayNode) meterJson.get( "hostNames" ) ;

		hostNames.add( detailHost ) ;
		hostValues.add( collectionJson.get( attribute ) ) ;

	}

	public int getCollectedMetric (
									ObjectNode meterJson ,
									String attribute ,
									String collector ,
									ObjectNode lastCollected ) {

		if ( lastCollected == null || ! lastCollected.has( attribute ) ) {

			logger.info( " Null attribute: " + attribute + "\n collectionJson: " + lastCollected ) ;
			return 0 ;

		}

		int collected = lastCollected.get( attribute ).asInt( ) ;
		meterJson.put( MetricCategory.hostCount.json( ), meterJson.get( MetricCategory.hostCount.json( ) ).asInt( )
				+ 1 ) ;

		return collected ;

	}

	static public List<String> testHostNameFilters = new ArrayList<>( ) ;

	boolean isPrintOnce = true ;

	public ObjectNode getResponseFromHost ( String hostName ) {

		ObjectNode hostResponse = hostResponseMap.get( hostName ) ;

		if ( isTest ) {

			hostResponse = loadHostStatusForTests( hostName, hostResponse ) ;

		}

		return hostResponse ;

	}

	private ObjectNode loadHostStatusForTests ( String hostName , ObjectNode hostResponse ) {

		try {

			if ( isPrintOnce ) {

				logger.warn( "{} Loading test data from : {}, testHosts: {}",
						hostName, stubResponseFilePath, testHostNameFilters ) ;
				isPrintOnce = false ;

			}

			hostResponse = jsonMapper.readValue(
					csapApp.check_for_stub( "", stubResponseFilePath ),
					ObjectNode.class ) ;

			if ( hostResponse.path( hostName ).isObject( ) ) {

				logger.info( "Consolidated status test file, using host path" ) ;
				hostResponse = (ObjectNode) hostResponse.path( hostName ) ;
				logger.debug( "hostResponse: {}", CSAP.jsonPrint( hostResponse ) ) ;

			}

			if ( hostName.equals( "worker-host-2" ) ) { // worker-host-2

				JsonNode testServiceContainer = hostResponse.at( "/services/csap-test-k8s-service_6090/containers/0" ) ;
				logger.info( "Updating filecount in {}", CSAP.jsonPrint( testServiceContainer ) ) ;
				( (ObjectNode) testServiceContainer ).put( OsProcessEnum.fileCount.value, 5051 ) ;

			}

			for ( String testHostFilter : testHostNameFilters ) {

				String filterPath = "/services/" + testHostFilter ;
				JsonNode service_status = hostResponse.at( filterPath ) ;

				logger.debug( "hostName: {}, filterPath: {} service_status: {}", hostName, filterPath,
						service_status ) ;

				if ( ! service_status.isMissingNode( )
						&& testHostFilter.endsWith( hostName ) ) {

					logger.warn( "updating filtered results for hostName: {}", hostName ) ;
					( (ObjectNode) service_status ).put( "deployedArtifacts", "host filter applied for testing: "
							+ hostName ) ;
					String servicePort = service_status.path( "serviceName" ).asText( ) + "_" + service_status.path(
							"port" ).asText( ) ;
					( (ObjectNode) hostResponse.path( "services" ) ).set( servicePort, service_status ) ;

				}

			}

		} catch ( Exception e ) {

			logger.error( "Error: {}", CSAP.buildCsapStack( e ) ) ;

		}

		return hostResponse ;

	}

	public ObjectNode getHostAsJson ( String hostName ) {

		if ( isTest ) {

			hostResponseMap.put( hostName, getResponseFromHost( hostName ) ) ;

		}

		if ( hostResponseMap.containsKey( hostName ) ) {

			try {

				ObjectNode hostStatus = hostResponseMap.get( hostName ) ;

				if ( hostStatus.has( ERROR_KEY ) ) {

					return null ;

				}

				return hostStatus ;

			} catch ( Exception e ) {

				logger.error( "Error: {}", CSAP.buildCsapStack( e ) ) ;

			}

		}

		return null ;

	}

	public ObjectNode buildCollectionSummaryReport ( Project model ) {

		return csapApp.healthManager( ).buildCollectionSummaryReport( hostsRuntime( model.getHostsCurrentLc( ) ), model
				.getName( ) ) ;

	}

	public ObjectNode getHostServicesReport ( String hostName ) {

		if ( hostResponseMap.containsKey( hostName ) ) {

			ObjectNode hostRuntime = hostResponseMap.get( hostName ) ;

			if ( hostRuntime.has( ERROR_KEY ) ) {

				return null ;

			} else {

				var serviceReport = hostRuntime.path( HostKeys.services.jsonId ) ;

				if ( serviceReport.isObject( ) ) {

					return (ObjectNode) serviceReport ;

				}

			}

		}

		return null ;

	}

	public ObjectNode getServiceRuntime ( String hostName , String serviceName_port ) {

		var hostServicesReport = getHostServicesReport( hostName ) ;

		if ( hostServicesReport != null ) {

			var serviceReport = hostServicesReport.path( serviceName_port ) ;

			if ( serviceReport.isObject( ) ) {

				return (ObjectNode) serviceReport ;

			}

		}

		return null ;

	}

	public void wipeList ( ) {

		logger.warn( "Received clear request" ) ;

		if ( hostList != null ) {

			hostList.clear( ) ;

		}

	}

	public void runScheduledRefreshes ( ) {

		logger.debug( "Checking for hosts to Query" ) ;

		try {

			host_queries_in_progress_lock.lock( ) ;
			executeQueriesInParallel( null ) ;

		} catch ( Throwable t ) {

			logger.error( "Error: {}", CSAP.buildCsapStack( t ) ) ;

		} finally {

			try {

				host_queries_in_progress_lock.unlock( ) ;

			} catch ( Exception e ) {

				logger.error( "Failed releasing lock: {}", CSAP.buildCsapStack( e ) ) ;

			}

		}

	}

	private class AgentStatus {

		public AgentStatus ( String host, String jsonResponse ) {

			this.host = host ;
			this.hostRuntimeJson = jsonResponse ;

		}

		public String getHost ( ) {

			return host ;

		}

		String host ;

		String hostRuntimeJson ;

		public String getHostRuntimeJson ( ) {

			return hostRuntimeJson ;

		}

	}

	final static String PERFORMANCE_ID = "csap.agent-status" ;

	// this should usually be a very fast call.
//	final static int MAX_SECONDS = 2 ;

	private class AgentStatusCallable implements Callable<AgentStatus> {

		private String host ;
		private boolean resetCache ;

		public AgentStatusCallable ( String host, boolean resetCache ) {

			this.host = host ;
			this.resetCache = resetCache ;

		}

		@Override
		public AgentStatus call ( ) {

			var hostTimer = csapApp.metrics( ).startTimer( ) ;

			String jsonResponse = "{\"error\": \"Reason: Initialization in Progress: "
					+ host + "\"}" ;
			// always use pooled connection

			RestTemplate pooledRest = csapApp.getAgentPooledConnection( 1, csapApp.rootProjectEnvSettings( )
					.getAdminToAgentTimeoutSeconds( ) ) ;

			jsonResponse = queryAgentStatus( pooledRest ) ;

			var nanos = csapApp.metrics( ).stopTimer( hostTimer, PERFORMANCE_ID + "." + host ) ;
			csapApp.metrics( ).record( PERFORMANCE_ID, nanos, TimeUnit.NANOSECONDS ) ;

			logger.trace( "{} pool: {} \n response: {}", host, pooledRest, jsonResponse ) ;
			return new AgentStatus( host, jsonResponse ) ;

		}

		/**
		 *
		 * method to perform rest call to agent to retrieve process state
		 *
		 * @see HostRequests#getManagerJson(javax.servlet.http.HttpServletRequest,
		 *      javax.servlet.http.HttpServletResponse)
		 * @param restTemplate
		 * @return
		 */
		private String queryAgentStatus ( RestTemplate restTemplate ) {

			String jsonResponse ;

			String statusUrl = csapApp.getAgentUrl(
					host,
					CsapCoreService.API_AGENT_URL + AgentApi.RUNTIME_URL + "?",
					true ) ;

			if ( resetCache ) {

				statusUrl += "resetCache=true&" ;

				// activeUsersCache.setLastRefreshMs( 0 );
			}

			try {

				Map<String, String> vars = new HashMap<String, String>( ) ;

				logger.debug( "Querying host url: '{}'", statusUrl ) ;

				String restResult = restTemplate.getForObject( statusUrl, String.class, vars ) ;

				// logger.info( "{} \n {} ", statusUrl , restResult);
				if ( restResult.indexOf( "cpuUtil" ) == -1 ) {

					logger.error( "Error: Invalid response from host query: " + statusUrl
							+ "\n==============>\n" + restResult ) ;

					jsonResponse = "{\"error\": \"Failed to parse response from host: "
							+ host + "\"}" ;

				} else {

					jsonResponse = restResult ;

					// if ( host.contains("db")) {
					// logger.info(restResult) ;
					// }
				}

			} catch ( Exception e ) {

				// String message = "{\"error\": \"Invalid response from host: "
				// + host + " Message:"
				// + e.getMessage().replaceAll("\"", "") + "\"}" ;
				jsonResponse = "{\"error\": \"Connection Failure - verify agent is running and accessible on: "
						+ host + "\"}" ;

				var errorDetails = CSAP.buildCsapStack( e ) ;

				if ( logger.isDebugEnabled( ) ) {

					logger.debug( "{} has an invalid response: {}", statusUrl, errorDetails ) ;

				} else {
					
					var nowMillis = System.currentTimeMillis( ) ;
					if ( nowMillis - latestMillis.get() > HOUR_MILLIS ) {

						latestMillis.set( nowMillis ) ;
						numPrinted.set( 0 ) ;

					}

					if ( numPrinted.incrementAndGet( ) < 5 ) {

						logger.warn(
								"{} connection error. url '{}' has an invalid response: {}  \n Note: {} of 5 per hour are printed",
								host, statusUrl, errorDetails, numPrinted.get( ) ) ;

					}

					if ( errorDetails.contains( "SSLHandshakeException" ) ) {

						logger.warn( "Failed SSL from: {} {}", statusUrl, errorDetails ) ;

					}

				}

			}

			return jsonResponse ;

		}

	}

	static final long HOUR_MILLIS = TimeUnit.HOURS.toMillis( 1 ) ; 
//	static final long HOUR_MILLIS = TimeUnit.MINUTES.toMillis( 1 ) ; 

	AtomicInteger numPrinted = new AtomicInteger( ) ;
	AtomicLong latestMillis = new AtomicLong( ) ;

	/**
	 * Invoked in response to UI by a user. If a full refresh is issued, restart the
	 * scheduled event.
	 *
	 * Working with timers can incredibly nuanced due to race conditions.
	 * Test/test/test.
	 *
	 * @param hostNameArray
	 */
	public void refreshAndWaitForComplete ( List<String> hosts ) {

		boolean gotLock = false ;

		try {

			gotLock = host_queries_in_progress_lock.tryLock( 5, TimeUnit.SECONDS ) ;

			if ( gotLock ) {

				List<String> hostToUpdate ;

				if ( hosts == null ) {

					hostToUpdate = hostList ;

					logger.debug( "Cancelling scheduler" ) ;

					if ( hostStatusSchedulerJob != null && ! hostStatusSchedulerJob.isDone( ) ) {

						hostStatusSchedulerJob.cancel( true ) ;

					}

				} else {

					hostToUpdate = hosts ;

				}

				executeQueriesInParallel( hostToUpdate ) ;

			} else {

				logger.warn( "Status refresh requested did NOT get lock in time interval, skipping request." ) ;
				;

			}

		} catch ( Exception e ) {

			logger.warn( "UI triggered refresh interruption, {}", CSAP.buildCsapStack( e ) ) ;

		} finally {

			if ( gotLock ) {

				if ( hosts == null ) {

					logger.debug( "Restarting scheduler" ) ;
					// We just got results, so do a full interval

					if ( ! isTest )
						restartHostRefreshTimer( csapApp.getHostRefreshIntervalSeconds( ) ) ;

				}

				host_queries_in_progress_lock.unlock( ) ;

			}

		}

	}

	private void executeQueriesInParallel ( List<String> hostToUpdate ) {

		logger.debug( "Lock Requests: {}", host_queries_in_progress_lock.getQueueLength( ) ) ;

		if ( isTest ) {

			logger.warn( "Running in test mode, status will be loaded from disk {}", hostToUpdate ) ;
			return ;

		}

		logger.debug( "Received a blocking refresh request, cancel existing job, then refresh on hosts: {}",
				hostToUpdate ) ;

		try {

			List<Future<AgentStatus>> futureResultsList = new ArrayList<>( ) ;

			for ( String host : hostList ) {

				Future<AgentStatus> futureResult = hostStatusService.submit( new AgentStatusCallable( host, false ) ) ;
				futureResultsList.add( futureResult ) ;

			}

			for ( int i = 0; i < futureResultsList.size( ); i++ ) {

				try {

					Future<AgentStatus> agentStatusJob = hostStatusService.take( ) ;

					// Future<QueryResult> finishedJob = hostStatusThreadManager
					// .poll(10, TimeUnit.SECONDS);

					if ( agentStatusJob != null ) {

						AgentStatus agentStatus = agentStatusJob.get( ) ;

						String hostName = agentStatus.getHost( ) ;

						ObjectNode hostRuntimeStatus = (ObjectNode) jsonMapper.readTree( agentStatus
								.getHostRuntimeJson( ) ) ;

						if ( hostRuntimeStatus.has( ERROR_KEY ) ) {

							csapApp.metrics( ).incrementCounter( PERFORMANCE_ID + ".errors" ) ;
							csapApp.metrics( ).incrementCounter( PERFORMANCE_ID + ".errors-" + agentStatus
									.getHost( ) ) ;

						}

						hostResponseMap.put(
								hostName,
								hostRuntimeStatus ) ;

						if ( hostRuntimeStatus.has( HostKeys.services.jsonId ) ) {

							updateServiceHealth( hostName, hostRuntimeStatus ) ;

						}

					} else {

						logger.error( "Got a Null result" ) ;
						break ;

					}

				} catch ( Exception e ) {

					logger.error( "Got an exception while processing task results {}",
							CSAP.buildCsapStack( e ) ) ;

				}

			}

			// cleanUp in case anything hangs.
			for ( Future<AgentStatus> f : futureResultsList ) {

				if ( f.cancel( true ) ) {

					logger.warn( "Task was cancelled: " + f.toString( ) ) ;

				}

			}

		} catch ( Exception e ) {

			logger.error( "Failed waiting", e ) ;

		}

		logger.debug( "blocking update completed" ) ;

	}

	public ArrayNode getAllAlerts ( ) {

		ArrayNode all = jsonMapper.createArrayNode( ) ;
		all.addAll( getAlertHistory( ) ) ;
		all.addAll( getAlertsThrottled( ) ) ;
		return all ;

	}

	private ArrayNode getAlertHistory ( ) {

		return alertHistory ;

	}

	public synchronized void updateServiceHealth ( String hostName , ObjectNode hostReport ) {

		ObjectNode serviceReports = (ObjectNode) hostReport.get( HostKeys.services.jsonId ) ;

		CSAP.asStreamHandleNulls( serviceReports )

				.map( serviceName -> serviceReports.get( serviceName ) )

				.filter(
						serviceStatus -> {

							JsonNode containers = serviceStatus.path( HostKeys.containers.jsonId ) ;
							if ( ! containers.isArray( ) )
								return false ;

							AtomicBoolean moreProcessing = new AtomicBoolean( false ) ;
							CSAP.jsonStream( containers ).forEach( collectedContainerState -> {

								if ( collectedContainerState.has( HostKeys.healthReportCollected.jsonId ) ) {

									boolean isHealthy = collectedContainerState
											.at( "/" + HostKeys.healthReportCollected.jsonId + "/"
													+ AlertFields.healthy.json ).asBoolean( true ) ;
									if ( ! isHealthy )
										moreProcessing.set( true ) ;

								}

							} ) ;

							return moreProcessing.get( ) ; // no more processing

						} )

				.forEach( serviceWithFailedReport -> {

					processReportFailures( hostName, serviceWithFailedReport ) ;

				} ) ;

		logger.debug( "history size: {} throttle size: {} ", getAlertHistory( ).size( ), getAlertsThrottled( )
				.size( ) ) ;

		while ( getAlertHistory( ).size( ) > csapApp.getCsapCoreService( ).getAlerts( ).getRememberCount( ) ) {

			getAlertHistory( ).remove( 0 ) ;

		}

		if ( getAlertsThrottled( ).size( ) > ( csapApp.getCsapCoreService( ).getAlerts( ).getRememberCount( ) * .1 ) ) {

			logger.error( "Excessive alerts happening each hour: {}, allowed is 10% of backlog: {}",
					getAlertsThrottled( ).size( ), csapApp.getCsapCoreService( ).getAlerts( ).getRememberCount( ) ) ;

		}

		while ( getAlertsThrottled( ).size( ) > ( csapApp.getCsapCoreService( ).getAlerts( ).getRememberCount( )
				* .1 ) ) {

			getAlertsThrottled( ).remove( 0 ) ;

		}

	}

	private void processReportFailures ( String hostName , JsonNode serviceWithFailedReport ) {

		try {

			// ServiceInstance runtimeInstance = jsonMapper.readValue(
			// serviceWithFailedReport.traverse(),
			// ServiceInstance.class );
			ServiceInstance runtimeInstance = ServiceInstance.buildInstance( jsonMapper, serviceWithFailedReport ) ;

			runtimeInstance.setHostName( hostName ) ;

			AtomicInteger containerIndex = new AtomicInteger( 0 ) ;
			runtimeInstance.getContainerStatusList( ).stream( ).forEach( container -> {

				String serviceId = runtimeInstance.getName( ) ;

				if ( runtimeInstance.is_cluster_kubernetes( ) ) {

					serviceId = runtimeInstance.getName( ) + "-" + containerIndex.addAndGet( 1 ) ;

				}

				ObjectNode healthReport = container.getHealthReportCollected( ) ;

				if ( healthReport.path( AlertFields.healthy.json ).asBoolean( ) ) {

					return ; // healthy k8 container, but other containers may have issue

				}

				String lastUpdatedTime = healthReport.path( AlertFields.lastCollected.json ).asText( ) ;

				String lastUpdatedKey = hostName + runtimeInstance.getServiceName_Port( ) ;

				logger.debug( "lastUpdatedKey: {}, lastUpdatedTime: {}", lastUpdatedKey, lastUpdatedTime ) ;

				if ( lastCollectedServiceHeathReportTimes.containsKey( lastUpdatedKey ) &&
						lastCollectedServiceHeathReportTimes.get( lastUpdatedKey ).equals( lastUpdatedTime ) ) {

					logger.debug( "skipping as items already added: {},\n {}",
							serviceId,
							runtimeInstance.getDefaultContainer( ).getHealthReportCollected( ) ) ;

				} else {

					logger.debug( "Adding health alerts for {},\n {}",
							serviceId,
							runtimeInstance.getDefaultContainer( ).getHealthReportCollected( ) ) ;

					lastCollectedServiceHeathReportTimes.put( lastUpdatedKey, lastUpdatedTime ) ;

					String healthUrl = "service-not-found-in-definition" ;

					try {

						ServiceInstance serviceInstance = csapApp.getServiceInstanceAnyPackage(
								runtimeInstance.getServiceName_Port( ), runtimeInstance.getHostName( ) ) ;

						if ( serviceInstance != null ) {

							healthUrl = serviceInstance.getHealthUrl( serviceId ) ;

						}

						addUpdatedServiceAlerts( serviceId, runtimeInstance, healthReport, lastUpdatedTime,
								healthUrl ) ;

					} catch ( Exception e ) {

						logger.error( "Did not find service: {}, {}",
								runtimeInstance.getServiceName_Port( ),
								CSAP.buildCsapStack( e ) ) ;

					}

				}

			} ) ;

		} catch ( Exception e ) {

			logger.warn( "Error: {}", CSAP.buildCsapStack( e ) ) ;

		}

	}

	// {"collectionCount":61,"lastCollected":"13:31:51 , Jan
	// 6","isHealthy":false,
	// "undefined":[],"pendingFirstInterval":[],"limitsExceeded":[{"id":"health.exceptions"
	// ,"type":"Occurences -
	// Max","collected":5,"limit":0,"description":"
	// Collected: 5, Limit: 0"}]}

	private void addUpdatedServiceAlerts (
											String serviceId ,
											ServiceInstance runtimeInstance ,
											ObjectNode healthReport ,
											String lastUpdated ,
											String healthUrl )
		throws IOException ,
		JsonParseException ,
		JsonMappingException {

		// increment counters and dates - or add
		List<ObjectNode> activeAlerts = null ;

		if ( healthReport.path( CsapMicroMeter.HealthReport.Report.errors.json ).isArray( ) ) {

			activeAlerts = jsonMapper.readValue(
					healthReport.get( CsapMicroMeter.HealthReport.Report.errors.json ).traverse( ),
					new TypeReference<ArrayList<ObjectNode>>( ) {
					} ) ;

		} else if ( healthReport.path( AlertFields.limitsExceeded.json ).isArray( ) ) {

			activeAlerts = jsonMapper.readValue(
					healthReport.get( AlertFields.limitsExceeded.json ).traverse( ),
					new TypeReference<ArrayList<ObjectNode>>( ) {
					} ) ;

		}

		if ( activeAlerts == null )

		{

			logger.warn( "No errors or limits in health report: {}", healthReport ) ;
			return ;

		}

		;

		long now = System.currentTimeMillis( ) ;
		activeAlerts.forEach( item -> {

			item.put( AlertItem.host.json, runtimeInstance.getHostName( ) ) ;
			item.put( AlertItem.service.json, serviceId ) ;
			item.put( "port", runtimeInstance.getPort( ) ) ;
			item.put( AlertInstance.AlertItem.count.json, 1 ) ;
			item.put( AlertInstance.AlertItem.formatedTime.json, lastUpdated ) ;
			item.put( AlertInstance.AlertItem.timestamp.json, now ) ;
			item.put( "healthUrl", healthUrl ) ;

		} ) ;

		activeAlerts.forEach( activeAlert -> {

			int matchCount = 0 ;
			int lastMatchIndex = 0 ;
			int index = 0 ;

			for ( JsonNode throttledEvent : getAlertsThrottled( ) ) {

				// handles uniqueness of host and service
				if ( AlertInstance.AlertItem.isSameId( activeAlert, throttledEvent ) ) {

					matchCount++ ;
					lastMatchIndex = index ;

				}

				index++ ;

			}

			if ( matchCount >= csapApp.getCsapCoreService( ).getAlerts( ).getThrottle( ).getCount( ) ) {

				// update the count
				int oldCount = getAlertsThrottled( )
						.get( lastMatchIndex )
						.get( AlertInstance.AlertItem.count.json )
						.asInt( ) ;
				activeAlert.put( AlertInstance.AlertItem.count.json, 1 + oldCount ) ;

				// remove the oldest
				getAlertsThrottled( ).remove( lastMatchIndex ) ;

			}

			// add the newest
			getAlertsThrottled( ).add( activeAlert ) ;

		} ) ;

		if (

		getAlertsThrottleTimer( ).isExpired( ) ) {

			// Always add in memory browsing
			getAlertHistory( ).addAll( getAlertsThrottled( ) ) ;
			getAlertsThrottleTimer( ).reset( ) ;
			getAlertsThrottled( ).removeAll( ) ;

		}

	}

	private ArrayNode getAlertsThrottled ( ) {

		return alertsThrottled ;

	}

	private CsapSimpleCache getAlertsThrottleTimer ( ) {

		return alertThrottleTimer ;

	}

	public List<ServiceInstance> findUnregisteredServices ( String host ) {

		List<ServiceInstance> servicesOnHost = new ArrayList<>( ) ;

		ObjectNode agent_status = getHostAsJson( host ) ;

		if ( agent_status != null ) {

			JsonNode unregisteredContainers = agent_status.path( HostKeys.unregisteredServices.jsonId ) ;

			if ( unregisteredContainers.isArray( ) && unregisteredContainers.size( ) > 0 ) {

				servicesOnHost = CSAP.jsonStream( unregisteredContainers )
						.map( JsonNode::asText )
						.map( serviceName -> {

							return ServiceInstance.buildUnregistered(
									host,
									serviceName ) ;

						} )
						.collect( Collectors.toList( ) ) ;

			}

		}

		return servicesOnHost ;

	}

}
