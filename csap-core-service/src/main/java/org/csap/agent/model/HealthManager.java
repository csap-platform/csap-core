package org.csap.agent.model ;

import java.lang.management.ManagementFactory ;
import java.text.DecimalFormat ;
import java.text.Format ;
import java.text.SimpleDateFormat ;
import java.util.ArrayList ;
import java.util.Arrays ;
import java.util.Date ;
import java.util.Iterator ;
import java.util.List ;
import java.util.Map ;
import java.util.Optional ;
import java.util.Set ;
import java.util.TreeMap ;
import java.util.stream.Collectors ;
import java.util.stream.Stream ;

import org.apache.commons.lang3.StringUtils ;
import org.csap.agent.CsapCore ;
import org.csap.agent.integrations.CsapEvents ;
import org.csap.agent.integrations.MetricsPublisher ;
import org.csap.agent.linux.OutputFileMgr ;
import org.csap.agent.services.HostKeys ;
import org.csap.agent.services.OsManager ;
import org.csap.agent.services.ServiceOsManager ;
import org.csap.helpers.CSAP ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;

import com.fasterxml.jackson.databind.JsonNode ;
import com.fasterxml.jackson.databind.ObjectMapper ;
import com.fasterxml.jackson.databind.node.ArrayNode ;
import com.fasterxml.jackson.databind.node.ObjectNode ;

public class HealthManager {

	final Logger logger = LoggerFactory.getLogger( getClass( ) ) ;

	public static final String HEALTH_SUMMARY = "summary" ;
	public static final String HEALTH_DETAILS = "details" ;
	public static final String RUNAWAY_KILL = "/runaway/kill/" ;

	Application application ;
	ObjectMapper jsonMapper ;
	HealthForAdmin healthForAdmin ;
	HealthForAgent healthForAgent ;

	final com.sun.management.OperatingSystemMXBean osStats = (com.sun.management.OperatingSystemMXBean) ManagementFactory
			.getOperatingSystemMXBean( ) ;

	public HealthManager ( Application application, ObjectMapper jsonMapper ) {

		this.application = application ;
		this.jsonMapper = jsonMapper ;

		healthForAdmin = new HealthForAdmin( application, jsonMapper ) ;
		healthForAgent = new HealthForAgent( application, jsonMapper ) ;

	}

	public HealthForAdmin admin ( ) {

		return healthForAdmin ;

	}

	public HealthForAgent agent ( ) {

		return healthForAgent ;

	}

	public ObjectNode build_health_report (
											double alertLevel ,
											boolean includeKubernetesWarnings ,
											Project project ) {

		logger.debug( "alertLevel: {}, includeKubernetesWarnings: {}", alertLevel, includeKubernetesWarnings,
				project ) ;

		var timer = application.metrics( ).startTimer( ) ;

		ObjectNode healthReport = jsonMapper.createObjectNode( ) ;

		ObjectNode summaryReport = healthReport.putObject( "summary" ) ;
		ObjectNode detailReport = healthReport.putObject( "details" ) ;

		if ( application.isAdminProfile( ) ) {

			buildErrorReportsForAdmin( summaryReport, detailReport, alertLevel, includeKubernetesWarnings, project ) ;

		} else {

			ObjectNode agentHealthReport = build_health_report_for_agent( alertLevel, includeKubernetesWarnings,
					null ) ;
			ArrayNode errors = (ArrayNode) agentHealthReport.get( HEALTH_SUMMARY ) ;
			// ObjectNode states = (ObjectNode) agentHealthReport.get( "states" ) ;

			if ( errors.size( ) > 0 ) {

				summaryReport.set( application.getCsapHostName( ), agentHealthReport.get( HEALTH_SUMMARY ) ) ;
				detailReport.set( application.getCsapHostName( ), agentHealthReport.get( HEALTH_DETAILS ) ) ;

				// healthReport.set( "states", states ) ;
			}

		}

		logger.debug( "healthReport: {}", CSAP.jsonPrint( healthReport ) ) ;

		application.metrics( ).stopTimer( timer, application.PERFORMANCE_ID + "build.errors" ) ;
		return healthReport ;

	}

	private void buildErrorReportsForAdmin (
												ObjectNode summaryReport ,
												ObjectNode detailReport ,
												double alertLevel ,
												boolean includeKubernetesWarnings ,
												Project requestedProject ) {

		// var notes = getReleasePackageStream()
		//// .filter( releasePackage -> releasePackage.getLifeCycleToHostMap().get(
		// getCurrentLifeCycle() ) != null )
		// .map( ReleasePackage::getLifeCycleToHostMap )
		// .map( Object::toString )
		// .collect( Collectors.joining( "\n\n" ) ) ;
		//
		// logger.info( "notes: {}", notes ) ;

		application.getReleasePackageStream( )

				.filter( project -> project.getHostsForEnvironment( application
						.getCsapHostEnvironmentName( ) ) != null )

				.filter( project -> {

					if ( ( requestedProject == null ) || // default to all packages
							( requestedProject == application.getRootProject( ).getAllPackagesModel( ) ) ||
							( project.getName( ).equals( requestedProject.getName( ) ) ) ) {

						return true ;

					}

					return false ;

				} )

				.forEach( matchedProject -> {

					logger.debug( "using hosts in: {}", matchedProject.getName( ) ) ;
					var lifecycleHosts = matchedProject.getHostsForEnvironment( application
							.getCsapHostEnvironmentName( ) ) ;

					Set<String> kubernetesRunningServices = application.getHostStatusManager( )
							.findRunningKubernetesServices( lifecycleHosts ) ;

					for ( String host : lifecycleHosts ) {
						// logger.info("Health check on: " + host) ;

						ObjectNode hostStatusJson = null ;

						if ( application.getHostStatusManager( ) != null ) {

							hostStatusJson = application.getHostStatusManager( ).getResponseFromHost( host ) ;

						}

						ObjectNode hostErrorReport = getAlertsOnRemoteAgent( alertLevel,
								includeKubernetesWarnings, host,
								hostStatusJson ) ;

						JsonNode summaryErrors = hostErrorReport.path( "summary" ) ;
						JsonNode detailErrors = hostErrorReport.path( "details" ) ;

						summaryReport.set( host, summaryErrors ) ;
						detailReport.set( host, detailErrors ) ;

						if ( includeKubernetesWarnings &&
								( kubernetesRunningServices.size( ) > 0 )
								&& summaryErrors.isArray( )
								&& ( summaryErrors.size( ) > 0 )
								&& summaryErrors.toString( ).contains( ServiceAlertsEnum.KUBERNETES_PROCESS_CHECKS ) ) {

							summaryReport.set( host,
									filter_errors_for_kubernetes_summary_active_or_crashed(
											summaryErrors,
											kubernetesRunningServices,
											host ) ) ;

							detailReport.set( host,
									filter_errors_for_kubernetes_details_active_or_crashed(
											detailErrors,
											kubernetesRunningServices,
											host ) ) ;

						}

						// filter empty hosts
						if ( summaryReport.get( host ).size( ) == 0 ) {

							summaryReport.remove( host ) ;

						}

					}

					logger.debug( "kubernetes services: {},\n errorsByHost: {}",
							kubernetesRunningServices, CSAP.jsonPrint( summaryReport ) ) ;

				} ) ;

		return ;

	}

	private JsonNode filter_errors_for_kubernetes_summary_active_or_crashed (
																				JsonNode summaryErrors ,
																				Set<String> kubernetesRunningServices ,
																				String host ) {

		ArrayNode translatedErrors = jsonMapper.createArrayNode( ) ;

		CSAP.jsonStream( summaryErrors ).forEach( error -> {

			String errorMessage = error.asText( ) ;

			if ( errorMessage.contains( ServiceAlertsEnum.KUBERNETES_PROCESS_CHECKS ) ) {

				Optional<String> foundRunningInstance = kubernetesRunningServices
						.stream( )
						.filter( runningService -> errorMessage.contains( runningService ) )
						.findFirst( ) ;

				if ( foundRunningInstance.isEmpty( ) ) {

					String convertedText = ServiceAlertsEnum.fromK8sToCrashOrKill( errorMessage ) ;
					translatedErrors.add( convertedText ) ;

				}

			} else {

				translatedErrors.add( errorMessage ) ;

			}

		} ) ;

		return translatedErrors ;

	}

	private JsonNode filter_errors_for_kubernetes_details_active_or_crashed (
																				JsonNode detailErrors ,
																				Set<String> kubernetesRunningServices ,
																				String host ) {

		ArrayNode filteredErrors = jsonMapper.createArrayNode( ) ;

		CSAP.jsonStream( detailErrors ).forEach( error -> {

			String type = error.path( ServiceAlertsEnum.ALERT_TYPE ).asText( ) ;
			String serviceName = error.path( ServiceAlertsEnum.ALERT_SOURCE ).asText( ) ;

			if ( type.equals( ServiceAlertsEnum.TYPE_KUBERNETES_PROCESS_CHECK ) ) {

				Optional<String> foundRunningInstance = kubernetesRunningServices
						.stream( )
						.filter( serviceName::startsWith )
						.findFirst( ) ;

				if ( foundRunningInstance.isEmpty( ) ) {

					( (ObjectNode) error ).put( ServiceAlertsEnum.ALERT_TYPE,
							ServiceAlertsEnum.TYPE_PROCESS_CRASH_OR_KILL ) ;
					filteredErrors.add( error ) ;

				} else {

					// found a running instance on another host - drop this alert
				}

			} else {

				filteredErrors.add( error ) ;

			}

		} ) ;

		return filteredErrors ;

	}

	private ObjectNode build_health_report_for_agent (
														double alertLevel ,
														boolean includeKubernetesWarnings ,
														String clusterFilter ) {

		logger.debug( "Health check" ) ;

		ObjectNode alertMessages = jsonMapper.createObjectNode( ) ;
		ArrayNode errorArray = alertMessages.putArray( HEALTH_SUMMARY ) ;

		try {

			alertMessages = alertsBuilder(
					alertLevel,
					includeKubernetesWarnings,
					application.getCsapHostName( ),
					(ObjectNode) application.getOsManager( ).getHostRuntime( ),
					clusterFilter ) ;

		} catch ( Exception e ) {

			if ( logger.isDebugEnabled( ) ) {

				logger.error( "Failed checking VM heath", e ) ;

			}

			errorArray.add( application.getCsapHostName( ) + ": " + "Failed HealthCheck reason: " + e.getMessage( ) ) ;

		}

		return alertMessages ;

	}

	public ObjectNode getAlertsOnRemoteAgent (
												double alertLevel ,
												boolean includeKubernetesWarnings ,
												String hostName ,
												ObjectNode hostStatusJson ) {

		logger.debug( "Health check on: {}", hostName ) ;

		ObjectNode alertReport = jsonMapper.createObjectNode( ) ;
		ArrayNode summaryErrors = alertReport.putArray( HEALTH_SUMMARY ) ;
		ArrayNode detailErrors = alertReport.putArray( HEALTH_DETAILS ) ;

		if ( hostStatusJson == null ) {

			// errorArray.add( "No response found" ) ;
			summaryErrors.add( hostName + " - Agent response does not contain hostStats." ) ;
			detailErrors.add( hostName + " - Agent response does not contain hostStats." ) ;
			return alertReport ;

		} else {

			try {

				alertReport = alertsBuilder( alertLevel, includeKubernetesWarnings, hostName, hostStatusJson, null ) ;

			} catch ( Exception e ) {

				if ( logger.isDebugEnabled( ) ) {

					logger.error( "failed health check evalutation: {}", CSAP.buildCsapStack( e ) ) ;

				}

				summaryErrors.add( hostName + ": " + "Failed HealthCheck reason: " + e.getMessage( ) ) ;
				detailErrors.add( hostName + ": " + "Failed HealthCheck reason: " + e.getMessage( ) ) ;
				return alertReport ;

			}

		}

		return alertReport ;

	}

	/**
	 *
	 * Add errors to errorList.
	 *
	 * @param hostName
	 * @param errorList
	 * @param responseFromHostStatusJson
	 * @param clusterFilter
	 */
	public ObjectNode alertsBuilder (
										double alertLevel ,
										boolean includeKubernetesWarnings ,
										String hostName ,
										ObjectNode responseFromHostStatusJson ,
										String clusterFilter ) {

		ObjectNode alertReport = jsonMapper.createObjectNode( ) ;
		ArrayNode summaryReport = alertReport.putArray( HEALTH_SUMMARY ) ;
		ArrayNode detailReport = alertReport.putArray( HEALTH_DETAILS ) ;

		if ( responseFromHostStatusJson.has( "error" ) ) {

			summaryReport.add( hostName + ": " + responseFromHostStatusJson
					.path( "error" )
					.textValue( ) ) ;
			ServiceAlertsEnum.addHostSourceDetailAlert( alertReport, hostName, "response-error",
					responseFromHostStatusJson
							.path( "error" )
							.textValue( ), -1, -1 ) ;
			// return result;
			return alertReport ;

		}

		ObjectNode hostStatsNode = (ObjectNode) responseFromHostStatusJson.path( HostKeys.hostStats.jsonId ) ;

		if ( hostStatsNode == null ) {

			summaryReport.add( hostName + ": " + "Host response missing attribute: hostStats" ) ;
			ServiceAlertsEnum.addHostSourceDetailAlert( alertReport, hostName, "response-error",
					"missing-host", -1, -1 ) ;
			// return result;
			return alertReport ;

		}

		alertsForHostCpu( hostStatsNode, hostName, alertLevel, summaryReport, alertReport ) ;

		alertsForHostMemory( hostStatsNode, hostName, summaryReport, alertReport ) ;

		alertsForHostFileSystems( hostStatsNode, hostName, alertLevel, summaryReport, alertReport ) ;

		ObjectNode serviceHealthCollected = (ObjectNode) responseFromHostStatusJson.path( "services" ) ;

		if ( serviceHealthCollected == null ) {

			summaryReport.add( hostName + ": " + "Host response missing attribute: services" ) ;
			detailReport.add( hostName + ": " + "Host response missing attribute: services" ) ;
			// return result;
			return alertReport ;

		}

		alertsForServices( hostName, clusterFilter, serviceHealthCollected, summaryReport,
				alertLevel, includeKubernetesWarnings, alertReport ) ;

		return alertReport ;

	}

	private void alertsForServices (
										String hostName ,
										String clusterFilter ,
										ObjectNode serviceHealthCollected ,
										ArrayNode summaryErrors ,
										double alertLevel ,
										boolean includeKubernetesWarnings ,
										ObjectNode healthReport ) {

		StringBuilder serviceMessages = new StringBuilder( ) ;

		logger.debug( "hostName: {}, clusterFilter: {}, application.isAgentProfile: {} ", hostName, clusterFilter,
				application.isAgentProfile( ) ) ;

		application
				.getAllPackages( )
				// .getServicesWithKubernetesFiltering( hostName )
				.getServicesOnHost( hostName )
				.filter( serviceInstance -> {

					if ( StringUtils.isNotEmpty( clusterFilter ) ) {

						return serviceInstance.getLifecycle( ).startsWith( clusterFilter ) ;

					} else {

						// logger.debug( serviceInstance.toSummaryString() ) ;
						return true ;

					}

				} )
				.filter( serviceInstance -> {

					if ( application.isAgentProfile( ) ) {

						return serviceInstance.filterInactiveKubernetesWorker( ) ;

					} else {

						return serviceHealthCollected.has( serviceInstance.getServiceName_Port( ) ) ;

					}

				} )
				.forEach( serviceInstance -> {

					if ( ! serviceHealthCollected.has( serviceInstance.getServiceName_Port( ) ) ) {

						String message = hostName + ": " + serviceInstance.getServiceName_Port( )
								+ " No status found" ;
						serviceMessages.append( message + "\n" ) ;
						summaryErrors.add( message ) ;

					} else {

						try {

							List<String> serviceAlerts = ServiceAlertsEnum.getServiceAlertsAndUpdateCpu(
									serviceInstance, alertLevel,
									includeKubernetesWarnings,
									serviceHealthCollected.get( serviceInstance.getServiceName_Port( ) ),
									application.rootProjectEnvSettings( ),
									healthReport ) ;

							// if ( serviceInstance.getServiceName().contains( "crashed" )) {
							logger.debug( "{} : serviceAlerts: {}, health: {} ",
									serviceInstance.toSummaryString( ),
									serviceAlerts,
									CSAP.jsonPrint( serviceHealthCollected.get( serviceInstance
											.getServiceName_Port( ) ) ) ) ;

							for ( String alertDescription : serviceAlerts ) {

								summaryErrors.add( alertDescription ) ;
								serviceMessages.append( alertDescription + "\n" ) ;

							}

						} catch ( Exception e ) {

							logger.error( "Failed parsing messages{}", CSAP.buildCsapStack( e ) ) ;

						}

					}

				} ) ;

		if ( serviceMessages.length( ) > 0 ) {

			addNagiosStateMessage( healthReport, "processes", MetricsPublisher.NAGIOS_WARN, serviceMessages
					.toString( ) ) ;

		}

	}

	private void alertsForHostFileSystems (
											ObjectNode hostStatsNode ,
											String hostName ,
											double alertLevel ,
											ArrayNode summaryAlerts ,
											ObjectNode healthReport )
		throws NumberFormatException {

		logger.debug( "hostName: {}, alertLevel: {}", hostName, alertLevel ) ;

		StringBuilder diskMessages = new StringBuilder( ) ;

		if ( hostStatsNode.has( "df" ) ) {

			ObjectNode dfJson = (ObjectNode) hostStatsNode.path( "df" ) ;
			Iterator<String> keyIter = dfJson.fieldNames( ) ;

			while ( keyIter.hasNext( ) ) {

				String mount = keyIter.next( ) ;
				String perCentString = dfJson
						.path( mount )
						.asText( ) ;
				String perCent = perCentString.substring( 0, perCentString.length( ) - 1 ) ;
				int perCentInt = Integer.parseInt( perCent ) ;
				int diskMax = (int) Math.round( application.rootProjectEnvSettings( )
						.getMaxDiskPercent( hostName ) * alertLevel ) ;

				if ( ( application.rootProjectEnvSettings( ).is_disk_monitored( hostName, mount ) )
						&& perCentInt > diskMax ) {

					// states.put("disk", MetricsPublisher.NAGIOS_WARN) ;
					String message = hostName + ": " + " Disk usage on " + mount + " is: "
							+ perCentString + ", max: " + diskMax + "%" ;
					summaryAlerts.add( message ) ;

					ServiceAlertsEnum.addHostSourceDetailAlert( healthReport, hostName, "disk-percent", mount,
							perCentInt, diskMax ) ;
					diskMessages.append( message + "\n" ) ;

				}

			}

		} else {

			summaryAlerts.add( hostName + ": " + "Host response missing attribute: hostStats.df" ) ;

		}

		if ( hostStatsNode.has( OsManager.IO_UTIL_IN_PERCENT ) ) {

			int ioUsagePercentMax = (int) Math.round( application.rootProjectEnvSettings( )
					.getMaxDeviceIoPercent( hostName ) * alertLevel ) ;

			CsapCore.jsonStream( hostStatsNode.get( OsManager.IO_UTIL_IN_PERCENT ) )
					.forEach( device -> {

						int deviceUsage = device.get( "percentCapacity" ).asInt( ) ;

						if ( deviceUsage > ioUsagePercentMax ) {

							String message = hostName + ": " + " IO usage on " + device.get( "name" ) + " is: "
									+ deviceUsage + "%, max: " + ioUsagePercentMax
									+ "%. Run iostat -dx to investigate" ;
							summaryAlerts.add( message ) ;
							diskMessages.append( message + "\n" ) ;
							ServiceAlertsEnum.addHostSourceDetailAlert( healthReport, hostName, "ioutil-percent",
									device.path( "name" ).asText( ), deviceUsage, deviceUsage ) ;

						}

					} ) ;

		}

		if ( hostStatsNode.has( "readOnlyFS" ) ) {

			ArrayNode readOnlyFS = (ArrayNode) hostStatsNode.path( "readOnlyFS" ) ;

			for ( JsonNode item : readOnlyFS ) {

				String message = hostName + ": Read Only Filesystem: " + item.asText( ) ;
				summaryAlerts.add( message ) ;
				diskMessages.append( message + "\n" ) ;
				ServiceAlertsEnum.addHostSourceDetailAlert( healthReport, hostName, "fs-read-only", item.asText( ), -1,
						-1 ) ;

			}

		} else {

			summaryAlerts
					.add( hostName + ": " + "Host response missing attribute: hostStats.readOnlyFS" ) ;

		}

		if ( diskMessages.length( ) > 0 ) {

			addNagiosStateMessage( healthReport, "disk", MetricsPublisher.NAGIOS_WARN, diskMessages.toString( ) ) ;

		}

	}

	private void alertsForHostMemory (
										ObjectNode hostStatsNode ,
										String hostName ,
										ArrayNode errorArray ,
										ObjectNode alertReport ) {

		if ( hostStatsNode.has( "memoryAggregateFreeMb" ) ) {

			int minFree = application.rootProjectEnvSettings( )
					.getMinFreeMemoryMb( hostName ) ;
			int freeMem = hostStatsNode
					.path( "memoryAggregateFreeMb" )
					.asInt( ) ;

			logger.debug( "freeMem: {} , minFree: {}", freeMem, minFree ) ;

			if ( freeMem < minFree ) {

				String message = hostName + ": " + " available memory " + freeMem
						+ " < min configured: " + minFree ;
				errorArray.add( message ) ;

				ServiceAlertsEnum.addHostDetailAlert( alertReport, hostName, "cpu-memory-free-min", freeMem, minFree ) ;
				addNagiosStateMessage( alertReport, "memory", MetricsPublisher.NAGIOS_WARN, message ) ;

			}

		} else {

			errorArray.add( hostName + ": "
					+ "Host response missing attribute: hostStats.memoryAggregateFreeMb" ) ;

		}

	}

	private void alertsForHostCpu (
									ObjectNode hostStatusResponse ,
									String hostName ,
									double alertLevel ,
									ArrayNode errorArray ,
									ObjectNode alertReport ) {

		int hostLoad = ( (Double) hostStatusResponse
				.path( "cpuLoad" )
				.asDouble( ) ).intValue( ) ;

		int maxLoad = (int) Math.round( application.rootProjectEnvSettings( )
				.getMaxHostCpuLoad( hostName ) * alertLevel ) ;

		if ( hostLoad >= maxLoad ) {

			String message = hostName + ": " + "current load " + hostLoad + " >= max configured: "
					+ maxLoad ;
			errorArray.add( message ) ;
			ServiceAlertsEnum.addHostDetailAlert( alertReport, hostName, "cpu-load", hostLoad, maxLoad ) ;
			addNagiosStateMessage( alertReport, "cpuLoad", MetricsPublisher.NAGIOS_WARN, message ) ;

		}

		int hostCpu = ( (Double) hostStatusResponse
				.path( "cpu" )
				.asDouble( ) ).intValue( ) ;
		int maxCpu = (int) Math.round( application.rootProjectEnvSettings( )
				.getMaxHostCpu( hostName ) * alertLevel ) ;

		if ( hostCpu >= maxCpu ) {

			String message = hostName + ": " + "current mpstat cpu " + hostCpu + " >= max configured: "
					+ maxCpu ;
			errorArray.add( message ) ;
			ServiceAlertsEnum.addHostDetailAlert( alertReport, hostName, "cpu-percent", hostCpu, maxCpu ) ;
			addNagiosStateMessage( alertReport, "hostCpu", MetricsPublisher.NAGIOS_WARN, message ) ;

		}

		int hostCpuIoWait = ( (Double) hostStatusResponse
				.path( "cpuIoWait" )
				.asDouble( ) ).intValue( ) ;
		int maxCpuIoWait = (int) Math.round( application.rootProjectEnvSettings( )
				.getMaxHostCpuIoWait( hostName ) * alertLevel ) ;

		if ( hostCpuIoWait >= maxCpuIoWait ) {

			String message = hostName + ": " + "current mpstat cpu IoWait " + hostCpuIoWait + " >= max configured: "
					+ maxCpuIoWait ;
			errorArray.add( message ) ;
			ServiceAlertsEnum.addHostDetailAlert( alertReport, hostName, "cpu-iowait", hostCpuIoWait, maxCpuIoWait ) ;
			addNagiosStateMessage( alertReport, "hostCpuIoWait", MetricsPublisher.NAGIOS_WARN, message ) ;

		}

	}

	/**
	 *
	 * Only agents use state
	 *
	 * @param stateNode
	 * @param State
	 * @param message
	 */
	private void addNagiosStateMessage ( ObjectNode errorNode , String monitor , String state , String message ) {

		ObjectNode stateNode = (ObjectNode) errorNode.get( "states" ) ;

		if ( stateNode != null ) {

			ObjectNode item = stateNode.putObject( monitor ) ;
			item.put( "status", state ) ;
			item.put( "message", message ) ;

		}

	}

	static final List<String> csapAdminServices = Arrays.asList( CsapCore.AGENT_NAME, CsapCore.ADMIN_NAME ) ;

	public void killRunaways ( ) {

		if ( application.isAdminProfile( ) ) {

			return ;

		}

		logger.debug( "Checking services on host to see if we need to be killed" ) ;

		findServicesWithResourceRunaway( ).forEach( this::killServiceRunaway ) ;

	}

	public Stream<ServiceInstance> findServicesWithResourceRunaway ( ) {

		// @formatter:off
		Stream<ServiceInstance> run_away_stream = application.getActiveProject( )
				.getServicesOnHost( application.getCsapHostName( ) )
				.filter( service -> {

					return service.getDefaultContainer( ).isActive( ) ;

				} )
				// .filter( ServiceInstance::isActive )
				.filter( ServiceInstance::is_not_os_process_monitor )
				.filter( serviceInstance -> ! csapAdminServices.contains( serviceInstance.getName( ) ) )
				// .filter( serviceInstance -> serviceInstance.getDisk() != null )
				.filter( this::isServiceResourceRunawayAndTimeToKill ) ;
		// @formatter:on

		return run_away_stream ;

	}

	private boolean isServiceResourceRunawayAndTimeToKill ( ServiceInstance serviceDefinition ) {

		var isTimeToKill = false ;
		var reasons = new ArrayList<String>( ) ;

//		long maxAllowedDisk = ServiceAlertsEnum.getMaxDiskInMb( serviceDefinition, application
//				.rootProjectEnvSettings( ) ) ;

		var maxAllowedDisk = ServiceAlertsEnum.getEffectiveLimit( serviceDefinition, application
				.rootProjectEnvSettings( ), ServiceAlertsEnum.diskSpace ) ;

		double resourceThresholdMultiplier = application.rootProjectEnvSettings( )
				.getAutoStopServiceThreshold( serviceDefinition.getHostName( ) ) ;

		long diskKillThreshold = Math.round( maxAllowedDisk * resourceThresholdMultiplier ) ;
		int currentDisk = 0 ;

		try {

			for ( ContainerState csapServiceResults : serviceDefinition.getContainerStatusList( ) ) {

				currentDisk = csapServiceResults.getDiskUsageInMb( ) ;

				logger.debug( "{} : currentDisk: {}, threshold: {}, maxAllowed: {}, maxThresh: {}",
						serviceDefinition.getName( ), currentDisk,
						resourceThresholdMultiplier, maxAllowedDisk, diskKillThreshold ) ;

				if ( currentDisk > diskKillThreshold ) {

					isTimeToKill = true ;
					reasons.add( "currentDisk: " + " Collected: " + currentDisk + ",  kill limit: "
							+ diskKillThreshold ) ;

				}

				for ( ServiceAlertsEnum alert : ServiceAlertsEnum.values( ) ) {

					int lastCollectedValue = 0 ;

					switch ( alert ) {

					case threads:
						lastCollectedValue = csapServiceResults.getThreadCount( ) ;
						break ;

					case sockets:
						lastCollectedValue = csapServiceResults.getSocketCount( ) ;
						break ;

					case openFileHandles:
						lastCollectedValue = csapServiceResults.getFileCount( ) ;
						break ;

					default:
						continue ;

					}

					long maxAllowed = ServiceAlertsEnum.getEffectiveLimit(
							serviceDefinition,
							application.rootProjectEnvSettings( ),
							alert ) ;

					long killThreshold = Math.round( maxAllowed * resourceThresholdMultiplier ) ;

					logger.debug( "{} : Item: {} lastCollectedValue: {}, threshold: {}, maxAllowed: {}, maxThresh: {}",
							serviceDefinition.getName( ), alert, lastCollectedValue, resourceThresholdMultiplier,
							maxAllowed,
							killThreshold ) ;

					if ( lastCollectedValue > killThreshold ) {

						isTimeToKill = true ;
						reasons.add(
								alert.value + " Collected: " + lastCollectedValue + ",  kill limit: "
										+ killThreshold ) ;

					}

				}

				csapServiceResults.setResourceViolations( reasons ) ;

			}

		} catch ( Exception e ) {

			logger.warn( "{} Failed runaway processing: {}",
					serviceDefinition.getName( ),
					CSAP.buildCsapStack( e ) ) ;

		}

		return isTimeToKill ;

	}

	private void killServiceRunaway ( ServiceInstance serviceInstance ) {

		logger.debug( "Killing service as Service limits exceeded for "
				+ serviceInstance.getServiceName_Port( ) ) ;

		ArrayList<String> params = new ArrayList<String>( ) ;

		StringBuilder reasons = new StringBuilder( ) ;

		for ( ContainerState csapServiceResults : serviceInstance.getContainerStatusList( ) ) {

			if ( csapServiceResults.getResourceViolations( ).size( ) > 1 ) {

				reasons.append( "1 of " + csapServiceResults.getResourceViolations( ).size( ) + ": " ) ;

			}

			if ( csapServiceResults.getResourceViolations( ).size( ) > 0 ) {

				reasons.append( csapServiceResults.getResourceViolations( ).get( 0 ) ) ;
				break ;

			}

		}

		// trigger a system event as well.
		application.getEventClient( ).publishEvent( CsapEvents.CSAP_SYSTEM_CATEGORY + RUNAWAY_KILL
				+ serviceInstance.getName( ),
				reasons.toString( ), null,
				serviceInstance.buildRuntimeState( ) ) ;

		// log a user event so it is found easier
		application.getEventClient( ).publishEvent( CsapEvents.CSAP_USER_SERVICE_CATEGORY + "/"
				+ serviceInstance.getName( ),
				RUNAWAY_KILL + ": " + reasons,
				null,
				serviceInstance.buildRuntimeState( ) ) ;

		serviceInstance.getDefaultContainer( ).setAutoKillInProgress( true ) ;

		try {

			OutputFileMgr outputFileMgr = new OutputFileMgr( application.getCsapWorkingFolder( ),
					"/" + serviceInstance.getName( ) + "_runaway" ) ;

			if ( serviceInstance.is_docker_server( ) ) {

				application.getOsManager( ).getServiceManager( ).killServiceUsingDocker(
						serviceInstance,
						outputFileMgr,
						params,
						Application.SYS_USER ) ;

			} else {

				application.getOsManager( ).getServiceManager( ).run_service_script(
						Application.SYS_USER,
						ServiceOsManager.KILL_FILE,
						serviceInstance.getServiceName_Port( ), params,
						null,
						outputFileMgr.getBufferedWriter( ) ) ;

			}

		} catch ( Exception e ) {

			logger.error( "Failed to kill: {}", CSAP.buildCsapStack( e ) ) ;

		}

	}

	public ObjectNode statusForAdminOrAgent (
												double alertLevel ,
												boolean includeKubernetesWarnings ) {

		var timer = application.metrics( ).startTimer( ) ;

		ObjectNode statusReport = jsonMapper.createObjectNode( ) ;
		ObjectNode healthReport = build_health_report( alertLevel, includeKubernetesWarnings, null ) ;
		JsonNode summaryHostToErrors = healthReport.path( HealthManager.HEALTH_SUMMARY ) ;

		ObjectNode hostSection = statusReport.putObject( "vm" ) ;

		if ( summaryHostToErrors.size( ) == 0 ) {

			statusReport.put( "Healthy", true ) ;

		} else {

			statusReport.put( "Healthy", false ) ;
			statusReport.set( application.VALIDATION_ERRORS, summaryHostToErrors ) ;

		}

		ObjectNode serviceToRuntimeNode = build_host_status_using_cached_data( ) ;

		hostSection.put( "cpuCount", Integer.parseInt(
				serviceToRuntimeNode
						.path( "cpuCount" )
						.asText( ) ) ) ;

		double newKB = Math.round( Double.parseDouble(
				serviceToRuntimeNode
						.path( "cpuLoad" )
						.asText( ) )
				* 10.0 )
				/ 10.0 ;

		hostSection.put( "cpuLoad", newKB ) ;
		hostSection.put( "host", application.getCsapHostName( ) ) ;
		hostSection.put( "project", application.getActiveProject( ).getName( ) ) ;
		hostSection.put( "application", application.getName( ) ) ;
		hostSection.put( "environment", application.getCsapHostEnvironmentName( ) ) ;

		// used for host discovery
		hostSection.put( EnvironmentSettings.LOADBALANCER_URL, application.rootProjectEnvSettings( )
				.getLoadbalancerUrl( ) ) ;

		int totalServicesActive = 0 ;
		int totalServices = 0 ;

		for ( ServiceInstance instance : application.getServicesOnHost( ) ) {

			if ( ! instance.is_files_only_package( ) ) { // Scripts should be
															// ignored

				totalServices++ ;

				if ( instance.getDefaultContainer( ).isRunning( ) ) {

					totalServicesActive++ ;

				}

			}

		}

		ObjectNode serviceNode = statusReport.putObject( "services" ) ;
		serviceNode.put( "total", totalServices ) ;
		serviceNode.put( "active", totalServicesActive ) ;

		application.metrics( ).stopTimer( timer, application.PERFORMANCE_ID + "status" ) ;
		return statusReport ;

	}

	public ObjectNode buildCollectionSummaryReport ( Map<String, ObjectNode> hostToAgentReport , String packageName ) {

		logger.debug( "hostToAgentReport {}", hostToAgentReport ) ;

		var filteredReport = jsonMapper.createObjectNode( ) ;
		var hostFilterReport = filteredReport.putArray( "hosts" ) ;
		var serviceFilterReport = filteredReport.putObject( "services" ) ;

		for ( String hostName : hostToAgentReport.keySet( ) ) {

			ObjectNode agentCollection = hostToAgentReport.get( hostName ) ;

			var hostSummary = hostFilterReport.addObject( ) ;
			hostSummary.put( "name", hostName ) ;
			var cpuStats = hostSummary.putObject( "cpu" ) ;
			cpuStats.put( "cores", agentCollection.at( "/hostStats/cpuCount" ).asInt( ) ) ;
			cpuStats.put( "load", agentCollection.at( "/hostStats/cpuLoad" ).asDouble( ) ) ;
			cpuStats.put( "usage", agentCollection.at( "/hostStats/cpu" ).asInt( ) ) ;
			cpuStats.put( "io-wait", agentCollection.at( "/hostStats/cpuIoWait" ).asInt( ) ) ;
			hostSummary.set( "memory", agentCollection.at( "/hostStats/memory" ) ) ;

			// CSAP.jsonStream( agentCollection.path( HostKeys.services.json() ) )
			// // .filter( serviceReport ->
			// serviceReport.at("/containers/0/healthReportCollected/isHealthy").asBoolean(true)
			// ==
			// // false )
			// .forEach( serviceReport -> {

			agentCollection.path( HostKeys.services.json( ) ).fieldNames( ).forEachRemaining( serviceInstanceName -> {

				logger.debug( "processing: {}", serviceInstanceName ) ;
				var serviceReport = agentCollection.path( HostKeys.services.json( ) ).path( serviceInstanceName ) ;

				CSAP.jsonStream( serviceReport.path( ContainerState.JSON_KEY ) )

						// only aggregate containers with missing items
						// .filter( containerReport -> containerReport.at( healthPath )
						// .asBoolean( true ) == false )

						.forEach( containerReport -> {

							var serviceName = serviceReport.path( "serviceName" ).asText( ) ;

							ServiceInstance serviceWithConfiguration = //
									application.getServiceInstance( serviceInstanceName, hostName, packageName ) ;

							var clusterType = serviceReport.path( ClusterType.CLUSTER_TYPE ).asText( ) ;

							var testReport = serviceFilterReport.path( serviceName ) ;
							ObjectNode aggregatedServiceReport ;

							if ( testReport.isMissingNode( ) ) {

								aggregatedServiceReport = serviceFilterReport.putObject( serviceName ) ;

								if ( serviceWithConfiguration != null ) {

									aggregatedServiceReport.put( "runtime", serviceWithConfiguration.getRuntime( ) ) ;
									aggregatedServiceReport.put( "cluster", serviceWithConfiguration.getCluster( ) ) ;

									if ( serviceWithConfiguration.getClusterType( ) == ClusterType.KUBERNETES ) {

										aggregatedServiceReport.put( "runtime", ClusterType.KUBERNETES.getJson( ) ) ;

									}

								}

								aggregatedServiceReport.putArray( "instances" ) ;

							} else {

								aggregatedServiceReport = (ObjectNode) testReport ;

							}

							var serviceInstances = (ArrayNode) aggregatedServiceReport.path( "instances" ) ;

							var instanceReport = serviceInstances.addObject( ) ;
							instanceReport.put( "host", hostName ) ;

							// aggregatedServiceReport.put( ClusterType.CLUSTER_TYPE, clusterType ) ;
							if ( clusterType.equals( ClusterType.KUBERNETES.getJson( ) ) ) {

								instanceReport.put( "podIp", containerReport.path( "podIp" ).asText( ) ) ;
								instanceReport.put( "podNamespace", containerReport.path( "podNamespace" ).asText( ) ) ;

							}

							for ( ServiceAlertsEnum alert : ServiceAlertsEnum.values( ) ) {

								instanceReport.put( alert.getName( ), containerReport.path( alert.value ).asInt(
										-1 ) ) ;

							}

						} ) ;

			} ) ;

		}

		return filteredReport ;

	}

	public ArrayNode build_host_report ( String projectName ) {

		var host_report = jsonMapper.createArrayNode( ) ;

		if ( application.isAgentProfile( ) ) {

			application.getOsManager( ).checkForProcessStatusUpdate( ) ;

			var hostDetailReport = host_report.addObject( ) ;
			hostDetailReport.put( "name", application.getCsapHostName( ) ) ;

			// hostDetailReport.setAll( agentReport ) ;
			try {

				hostDetailReport.setAll( (ObjectNode) application.getOsManager( ).getHostRuntime( ) ) ;

			} catch ( Exception e ) {

				logger.warn( "Failed getting agent {}", CSAP.buildCsapStack( e ) ) ;

			}

		} else {

			Project project = application.getProject( projectName ) ;

			var environmentHostnames = project.getHostsForEnvironment( application.getCsapHostEnvironmentName( ) ) ;

			// logger.info( "lifeCycleHostList: {}", lifeCycleHostList );

			for ( var host : environmentHostnames ) {

				var hostDetailReport = host_report.addObject( ) ;
				hostDetailReport.put( "name", host ) ;

				ObjectNode agentReport = application.getHostStatusManager( ).getHostAsJson( host ) ;

				if ( agentReport != null ) {

					hostDetailReport.setAll( agentReport ) ;

				} else {

					hostDetailReport.put( "errors", "status-not-found" ) ;

				}

			}

		}

		return host_report ;

	}

	public ObjectNode build_host_summary_report ( String projectName ) {

		var summaryReport = jsonMapper.createObjectNode( ) ;

		var serviceCount = 0 ;
		var hostCount = 1 ;
		var hostAllProjectCount = 1 ;
		var kubernetesEventCount = 0 ;
		var podCount = 0 ;
		var podRestartCount = 0 ;
		var volumeCount = 0 ;
		var kubernetesNodeCount = 0 ;
		var kubernetesMetrics = false ;

		var hostSessions = summaryReport.putArray( "host-sessions" ) ;

		long lastOpMills = -1 ;
		summaryReport.put( "lastOp", application.getLastOpMessage( ) ) ;

		if ( application.isAgentProfile( ) ) {

			summaryReport.put( "deploymentBacklog", application.getOsManager( ).getServiceManager( ).getOpsQueued( ) ) ;

			application.getOsManager( ).checkForProcessStatusUpdate( ) ;

			var hostLogin = hostSessions.addObject( ) ;
			hostLogin.put( "name", application.getCsapHostName( ) ) ;

			var agentHostReport = build_host_status_using_cached_data( ) ;
			hostLogin.set( "sessions", agentHostReport.path( "vmLoggedIn" ) ) ;

			if ( application.isKubernetesInstalledAndActive( ) ) {

				kubernetesEventCount = (int) application.getKubernetesIntegration( ).eventCount( null ) ;

				kubernetesMetrics = ! agentHostReport.path( "kubernetes" ).path( "metrics" ).path( "current" ).path(
						"cores" ).isMissingNode( ) ;

				kubernetesNodeCount = (int) application.getKubernetesIntegration( ).nodeCount( ) ;
				var podCountsReport = application.getKubernetesIntegration( ).podCountsReport( null ) ;
				podCount = podCountsReport.path( "count" ).asInt( ) ;
				podRestartCount = podCountsReport.path( "restarts" ).asInt( ) ;
				volumeCount = (int) application.getKubernetesIntegration( ).listingsBuilder( ).persistentVolumeCount(
						null ) ;

				serviceCount = application.getServicesOnHost( ).size( ) ;

			}

		} else {

			hostAllProjectCount = application.getAllHostsInAllPackagesInCurrentLifecycle( ).size( ) ;

			summaryReport.put( "deploymentBacklog", application.getHostStatusManager( ).totalOpsQueued( ) ) ;

			Project project = application.getProject( projectName ) ;

			project.getAllPackagesModel( ).getHostsCurrentLc( ) ;

			var environmentHostNames = project.getHostsForEnvironment( application.getCsapHostEnvironmentName( ) ) ;

			hostCount = environmentHostNames.size( ) ;

			for ( var host : environmentHostNames ) {

				ObjectNode agent_status = application.getHostStatusManager( ).getHostAsJson( host ) ;

				if ( agent_status != null ) {

					var hostStatus = agent_status.path( HostKeys.hostStats.jsonId ) ;
					var loggedIn = hostStatus.path( "vmLoggedIn" ) ;

					if ( loggedIn.size( ) > 0 ) {

						var hostLogin = hostSessions.addObject( ) ;
						hostLogin.put( "name", host ) ;
						hostLogin.set( "sessions", hostStatus.get( "vmLoggedIn" ) ) ;

					}

					serviceCount += agent_status.path( "services" ).size( ) ;
					// logger.info("hostStatus: {}", CSAP.jsonPrint( hostStatus ) ) ;
					long hostOpMillis = hostStatus.path( "lastOpMillis" ).longValue( ) ;
					logger.debug( "{} hostOpMillis: {} , {}", host, hostOpMillis, hostStatus.path( "lastOp" ) ) ;

					if ( hostOpMillis > lastOpMills ) {

						lastOpMills = hostOpMillis ;
						summaryReport.put( "lastOp", host + ":" + hostStatus.path( "lastOp" ).asText( "-" ) ) ;

					}

					var hostEventCount = hostStatus.path( "kubernetes" ).path( "eventCount" ).asInt( ) ;

					if ( hostEventCount > 0 ) {

						kubernetesEventCount = hostEventCount ;

					}

					var hostPodCount = hostStatus.path( "kubernetes" ).path( "podReport" ).path( "count" ).asInt( ) ;

					if ( hostPodCount > 0 ) {

						podCount = hostPodCount ;

					}

					var hostPodRestartCount = hostStatus.path( "kubernetes" )
							.path( "podReport" ).path( "restarts" ).asInt( ) ;

					if ( hostPodRestartCount > 0 ) {

						podRestartCount = hostPodRestartCount ;

					}

					var hostNodeCount = hostStatus.path( "kubernetes" ).path( "nodeCount" ).asInt( ) ;

					if ( hostNodeCount > 0 ) {

						kubernetesNodeCount = hostNodeCount ;

					}

					var hostKubMetrics = hostStatus.path( "kubernetes" ).path( "metrics" ).path( "current" ).path(
							"cores" ) ;

					if ( ! hostKubMetrics.isMissingNode( ) ) {

						kubernetesMetrics = true ;

					}

					var hostVolumeCount = hostStatus.path( "kubernetes" ).path( "volumeClaimCount" ).asInt( ) ;

					if ( hostVolumeCount > 0 ) {

						volumeCount = hostVolumeCount ;

					}

				} else {

					logger.debug( "No status found for host" ) ;

				}

			}

		}

		summaryReport.put( "services", serviceCount ) ;
		summaryReport.put( "hosts", hostCount ) ;
		summaryReport.put( "hosts-all-projects", hostAllProjectCount ) ;

		summaryReport.put( "kubernetesNodes", kubernetesNodeCount ) ;
		summaryReport.put( "kubernetesMetrics", kubernetesMetrics ) ;
		summaryReport.put( "volumeCount", volumeCount ) ;
		summaryReport.put( "kubernetesEvents", kubernetesEventCount ) ;
		summaryReport.put( "podCount", podCount ) ;
		summaryReport.put( "podRestartCount", podRestartCount ) ;

		return summaryReport ;

	}

	public ObjectNode build_host_status_using_cached_data ( ) {

		return build_host_status_using_cached_data( null ) ;

	}

	public ObjectNode build_host_status_using_cached_data (
															String kubernetesNamespace ) {

		var timer = application.metrics( ).startTimer( ) ;

		ObjectNode hostStatus = jsonMapper.createObjectNode( ) ;

		try {

			// NEEDS TO BE FAST : < 1ms. Use background caching as needed
			addCpuMetrics( hostStatus ) ;

			try {

				var version = "2.1" ;
				var linux = application.findServiceByNameOnCurrentHost( "csap-package-linux" ) ;

				if ( linux != null ) {

					version = linux.getDefaultContainer( ).getDeployedArtifacts( ) ;

				}

				hostStatus.put( "osVersion", version ) ;

			} catch ( Exception e ) {

				logger.warn( "Failed to get os version {}", CSAP.buildCsapStack( e ) ) ;

			}

			hostStatus.put( "processCount", application.getOsManager( ).numberOfProcesses( ) ) ;
			hostStatus.put( "csapCount", application.getServicesOnHost( ).size( ) ) ;
			hostStatus.put( "linuxInterfaceCount", application.getOsManager( ).getCachedNetworkInterfaceCount( ) ) ;
			hostStatus.put( "linuxServiceCount", application.getOsManager( ).getCachedLinuxServiceCount( ) ) ;
			hostStatus.put( "linuxPackageCount", application.getOsManager( ).getCachedLinuxPackageCount( ) ) ;
			hostStatus.put( "diskCount", application.getOsManager( ).getDiskCount( ) ) ;

			if ( application.isDockerInstalledAndActive( ) ) {

				var dockerSummaryReport = application.getDockerIntegration( ).buildSummary( ) ;
				var dockerInstance = application.findServiceByNameOnCurrentHost( "docker" ) ;

				if ( dockerInstance != null ) {

					dockerSummaryReport.put( "dockerStorage", dockerInstance.getDefaultContainer( ).getDiskUsageInMb( )
							/ 1024 ) ;

				} else {

					dockerSummaryReport.put( "dockerStorage", -1 ) ;

				}

				hostStatus.set( "docker", dockerSummaryReport ) ;

			}

			if ( application.isKubernetesInstalledAndActive( ) ) {
				// very costly - slim down to minimum
				// hostStatus.set( "kubernetes", application.getKubernetesIntegration(
				// ).buildSummary( kubernetesNamespace ) ) ;

				// hmmmm - this is triggering work -- remote apis could hang --- causing
				// disastter

				JsonNode summaryReport = jsonMapper.createObjectNode( ) ;

				if ( application.kubeletInstance( ).isKubernetesMaster( )
						|| kubernetesNamespace != null ) {

					// full report ONLY on masters, or on specific UI requests
					summaryReport = application
							.getKubernetesIntegration( )
							.buildSummaryReport( kubernetesNamespace ) ;

				} else {

					// run metrics report only - typically agent heartbeats on non masters
					summaryReport = application.getKubernetesIntegration( ).getCachedNodeHealthMetrics( ) ;

				}

				hostStatus.set( "kubernetes", summaryReport ) ;

			}

			ObjectNode memory = hostStatus.putObject( "memory" ) ;
			memory.put( "total", getGb( osStats.getTotalPhysicalMemorySize( ) ) ) ;
			memory.put( "free", getGb( osStats.getFreePhysicalMemorySize( ) ) ) ;
			memory.put( "swapTotal", getGb( osStats.getTotalSwapSpaceSize( ) ) ) ;
			memory.put( "swapFree", getGb( osStats.getFreeSwapSpaceSize( ) ) ) ;

			var availableBytes = application.getOsManager( ).getMemoryAvailbleLessCache( ) * 1024L * 1024L ;
			memory.put( "available", getGb( availableBytes ) ) ;

			hostStatus.set( "users", application.getActiveUsers( ).getActive( ) ) ;

			hostStatus.put( "lastOp", application.getLastOpMessage( ) ) ;

			hostStatus.set( "vmLoggedIn", application.getOsManager( ).getVmLoggedIn( ) ) ;

			hostStatus.put( "motd", application.getMotdMessage( ) ) ;

			Format tsFormater = new SimpleDateFormat( "HH:mm:ss" ) ;

			hostStatus.put( "timeStamp", tsFormater.format( new Date( ) ) ) ;

			hostStatus.put( "lastOpMillis", application.getLastOpMillis( ) ) ;

		} catch ( Exception e ) {

			hostStatus.put( "error", "Reason: " + CSAP.buildCsapStack( e ) ) ;
			logger.info( "Failed build health: {}", CSAP.buildCsapStack( e ) ) ;

		}

		application.metrics( ).stopTimer( timer, application.PERFORMANCE_ID + "status.host" ) ;
		return hostStatus ;

	}

	public void addCpuMetrics ( ObjectNode hostStatus ) {

		var currentLoad = osStats.getSystemLoadAverage( ) ;

		if ( application.isDesktopHost( ) && ( currentLoad == -1.0 ) ) {

			currentLoad = 9.3 ;

		}

		hostStatus.put( "cpuLoad", currentLoad ) ;

		hostStatus.put( "cpuCount", osStats.getAvailableProcessors( ) ) ;

		if ( application.rootProjectEnvSettings( ).getMetricToSecondsMap( ).size( ) > 0 ) {

			hostStatus.put( "cpu", application.metricManager( ).getLatestCpuUsage( ) ) ;
			hostStatus.put( "cpuIoWait", application.metricManager( ).getLatestIoWait( ) ) ;

		}

	}

	Double byteToGb = 1024 * 1024 * 1024D ;
	DecimalFormat gbFormat = new DecimalFormat( "#.#GB" ) ;

	String getGb ( long num ) {

		// logger.info( "num: {}" , num );
		return gbFormat.format( num / byteToGb ) ;

	}

	public int getCpuCount ( ) {

		return osStats.getAvailableProcessors( ) ;

	}

	public ArrayNode buildServiceRuntimes ( ServiceInstance definitionService ) {

		logger.debug( definitionService.toString( ) ) ;

		boolean addKubernetesMaster = false ;

		ArrayNode serviceRuntimes = jsonMapper.createArrayNode( ) ;

		ServiceInstance runtimeService = definitionService ;
		ObjectNode hostStatus ;

		if ( application.isAgentProfile( ) ) {

			hostStatus = build_host_status_using_cached_data( ) ;

		} else {

			hostStatus = jsonMapper.createObjectNode( ) ;

			ObjectNode hostAgentStatus = application
					.getHostStatusManager( )
					.getHostAsJson( definitionService.getHostName( ) ) ;

			if ( hostAgentStatus != null ) {

				hostStatus = (ObjectNode) hostAgentStatus.path( HostKeys.hostStats.jsonId ) ;

				ObjectNode collectedServiceStatus = application
						.getHostStatusManager( )
						.getServiceRuntime(
								definitionService.getHostName( ),
								definitionService.getServiceName_Port( ) ) ;

				if ( collectedServiceStatus != null ) {

					try {

						// rebuild service, using collected data
						runtimeService = ServiceInstance.buildInstance( jsonMapper, collectedServiceStatus ) ;

						// add in any required fields
						runtimeService.setProcessFilter( definitionService.getProcessFilter( ) ) ;

					} catch ( Exception e ) {

						logger.warn( "Failed building service, {}", CSAP.buildCsapStack( e ) ) ;

					}

				} else {

					logger.debug( "Did not find any results for service: {}, empty data will be used",
							definitionService.getServiceName_Port( ) ) ;
					definitionService.getDefaultContainer( ) ;

				}

			} else {

				definitionService.getDefaultContainer( ) ; // initialize with default container
				runtimeService = definitionService ;

			}

		}

		logger.debug( "runtimeService: {}", runtimeService ) ;

		int containerIndex = 0 ;

		for ( ContainerState container : runtimeService.getContainerStatusList( ) ) {

			// kubernetes filtering
			boolean inactiveKubernetesWorkerService = //
					runtimeService.is_cluster_kubernetes( )
							&& ( ! definitionService.isKubernetesMaster( ) )
							&& ( ! container.isRunning( ) ) ;

			if ( addKubernetesMaster ) {

				// when no active instances on any host - include master
				inactiveKubernetesWorkerService = runtimeService.is_cluster_kubernetes( ) && ! definitionService
						.isKubernetesMaster( ) ;

			}

			logger.debug( "filterInactiveKubernetes: {}", inactiveKubernetesWorkerService ) ;

			if ( ! inactiveKubernetesWorkerService ) {

				ObjectNode uiInstanceData = runtimeService.build_ui_service_instance( container, containerIndex++ ) ; // gets
																														// runtime
																														// fields

				if ( hostStatus.path( "cpuCount" ).asInt( ) == 0 ) {

					uiInstanceData.put( "deployedArtifacts", "host-not-connected" ) ;

				}

				add_definition_values_used_by_ui( definitionService, uiInstanceData, hostStatus ) ;

				if ( definitionService.isKubernetesMaster( ) ) {

					uiInstanceData.put( "kubernetes-master", true ) ;

				}

				uiInstanceData.put( "replicaCount", runtimeService.getKubernetesReplicaCount( ).asInt( -1 ) ) ;

				serviceRuntimes.add( uiInstanceData ) ;

			}

		}

		return serviceRuntimes ;

	}

	private void add_definition_values_used_by_ui (
													ServiceInstance definition ,
													ObjectNode uiInstanceData ,
													ObjectNode hostStatus ) {

		logger.debug( "{} : {}", definition.toSummaryString( ), CSAP.jsonPrint( uiInstanceData ) ) ;

		if ( application.isRunningOnDesktop( ) ) {

			// using definition host for long name support
			uiInstanceData.put( ServiceBase.HOSTNAME_JSON, definition.getHostName( ) ) ;

		}

		uiInstanceData.put( "mavenId", definition.getMavenId( ) ) ;
		// uiInstanceData.put( ServiceAttributes.servletContext.json(),
		// definition.getContext() ) ;
		uiInstanceData.put( "launchUrl", definition.getUrl( ) ) ;
		uiInstanceData.put( "javaHttp", definition.isJavaOverHttpCollectionEnabled( ) ) ;

		String serviceId = definition.getName( ) + "-" + ( uiInstanceData.path( "containerIndex" ).asInt( 0 ) + 1 ) ;
		uiInstanceData.put( "serviceHealth", definition.getHealthUrl( serviceId ) ) ;

		uiInstanceData.put( "jmx", definition.getJmxPort( ) ) ;
		uiInstanceData.put( "jmxrmi", definition.isJmxRmi( ) ) ;

		if ( definition.isRemoteCollection( ) ) {

			uiInstanceData.put( "jmx", definition.getCollectHost( ) + ":" + definition.getCollectPort( ) ) ;

		}

		uiInstanceData.put( ServiceAttributes.startOrder.json( ), definition.getRawAutoStart( ) ) ;
		uiInstanceData.put( "lc", definition.getLifecycle( ) ) ;

		// host stats for instance
		uiInstanceData.put( "cpuCount", hostStatus.path( "cpuCount" ).asText( "-" ) ) ;
		uiInstanceData.put( "cpuLoad", hostStatus.path( "cpuLoad" ).asText( "-" ) ) ;

		double newKB = Math.round( hostStatus.path( "cpuLoad" ).asDouble( 0 ) * 10.0 ) / 10.0 ;
		uiInstanceData.put( "cpuLoad", Double.toString( newKB ) ) ;

		uiInstanceData.put( "motd", hostStatus.path( "motd" ).asText( "-" ) ) ;
		uiInstanceData.put( "users", hostStatus.path( "users" ).asText( "-" ) ) ;
		uiInstanceData.put( "lastOp", hostStatus.path( "lastOp" ).asText( "-" ) ) ;

		if ( hostStatus.has( "du" ) ) {

			uiInstanceData.put( "du", hostStatus.path( "du" ).asInt( ) ) ;

		}

	}

	public List<String> getHealthServiceIds ( ) {

		var healthIds = application
				.getAllPackages( )
				.getServicesOnHost( application.getCsapHostName( ) )
				.filter( ServiceInstance::isApplicationHealthEnabled )
				.flatMap( ServiceInstance::getIds )
				.collect( Collectors.toList( ) ) ;

		return healthIds ;

	}

	public Map<String, Map<String, String>> buildHealthUrls ( ) {

		Map<String, Map<String, String>> healthUrlsByService ;

		if ( application.isAdminProfile( ) ) {

			//
			healthUrlsByService = application.getAllPackages( )
					.getServiceConfigStreamInCurrentLC( )
					.flatMap( serviceInstancesEntry -> serviceInstancesEntry.getValue( ).stream( ) )
					.filter( ServiceInstance::isApplicationHealthEnabled )
					.filter( service -> discoveredHealthUrls( service ) != null )
					.collect( Collectors.toMap(
							ServiceInstance::getName,
							this::discoveredHealthUrls,
							( a , b ) -> {

								a.putAll( b ) ;
								return a ;

							}, // merge all hosts
							( ) -> new TreeMap<String, Map<String, String>>( String.CASE_INSENSITIVE_ORDER ) ) ) ;

		} else {

			healthUrlsByService = application
					.getAllPackages( )
					.getServicesOnHost( application.getCsapHostName( ) )
					.filter( ServiceInstance::isApplicationHealthEnabled )
					.collect( Collectors.toMap(
							ServiceInstance::getName,
							ServiceInstance::getHealthUrls,
							( a , b ) -> a, // merge function should never be used on a single host
							( ) -> new TreeMap<String, Map<String, String>>( String.CASE_INSENSITIVE_ORDER ) ) ) ; // want
																													// them
																													// sorted

		}

		return healthUrlsByService ;

	}

	private Map<String, String> discoveredHealthUrls ( ServiceInstance serviceInstance ) {

		ObjectNode collectedRuntime = application.getHostStatusManager( ).getHostAsJson( serviceInstance
				.getHostName( ) ) ;

		if ( collectedRuntime == null ) {

			return null ;

		}

		JsonNode serviceCollected = collectedRuntime
				.at( "/" + HostKeys.services.json( ) + "/" + serviceInstance.getServiceName_Port( ) ) ;

		return serviceInstance.getHealthUrls( serviceCollected ) ;

	}

}
