/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.csap.agent.model ;

import java.io.IOException ;
import java.util.ArrayList ;
import java.util.Arrays ;
import java.util.Iterator ;
import java.util.Map ;
import java.util.concurrent.atomic.AtomicInteger ;
import java.util.stream.Collectors ;

import org.csap.agent.CsapCore ;
import org.csap.agent.services.HostKeys ;
import org.csap.agent.stats.OsProcessEnum ;
import org.csap.agent.stats.service.JmxCommonEnum ;
import org.csap.alerts.AlertFields ;
import org.csap.alerts.AlertInstance ;
import org.csap.helpers.CSAP ;
import org.csap.integations.CsapMicroMeter ;
import org.csap.integations.CsapMicroMeter.HealthReport.Report ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;

import com.fasterxml.jackson.core.JsonProcessingException ;
import com.fasterxml.jackson.databind.JsonNode ;
import com.fasterxml.jackson.databind.ObjectMapper ;
import com.fasterxml.jackson.databind.node.ArrayNode ;
import com.fasterxml.jackson.databind.node.ObjectNode ;

/**
 *
 * @author someDeveloper
 */
public enum ServiceAlertsEnum {

	threads(OsProcessEnum.threadCount.value, "thread-count"), diskSpace(OsProcessEnum.diskUsedInMb.value,
			"disk-used-mb"),
	diskWriteRate(OsProcessEnum.diskWriteKb.value, "disk-write-kb"),
	memory(OsProcessEnum.rssMemory.value, "memory-rss"), sockets(OsProcessEnum.socketCount.value, "sockets-open"),
	openFileHandles(OsProcessEnum.fileCount.value, "files-open"),
	cpu(OsProcessEnum.topCpu.value, "cpu-top"), httpConnections(JmxCommonEnum.httpConnections.value,
			"http-connections");

	public static final String ALERT_TYPE = "type" ;
	public static final String ALERT_SOURCE = "source" ;
	public static final String TYPE_PROCESS_CRASH_OR_KILL = "process-crash-or-kill" ;
	public static final String TYPE_KUBERNETES_PROCESS_CHECK = "kubernetes-process-check" ;
	public static final String PROCESS_CRASH_OR_KILL = ": no active OS process found, and no user stop was issued. Probable crash or runaway resource kill: review logs." ;
	public static final String KUBERNETES_PROCESS_CHECKS = ": warning - kubernetes process checks not supported on master" ;
	static final Logger logger = LoggerFactory.getLogger( ServiceAlertsEnum.class ) ;
	static ObjectMapper jacksonMapper = new ObjectMapper( ) ;

	public String value ;
	private String name ;

	public String getName ( ) {

		return name ;

	}

	public String maxId ( ) {

		return "max_" + value ;

	}

	public static double ALERT_LEVEL = 1.0 ;
	public static final String JAVA_HEARTBEAT = "jmxHeartbeatMs" ;

	private ServiceAlertsEnum ( String value, String name ) {

		this.value = value ;
		this.name = name ;

	}

	public String buildMessage (
									String serviceId ,
									ServiceInstance serviceDefinition ,
									long collected ,
									long maxAllowed ) {

		StringBuilder alertMessage = new StringBuilder(
				serviceDefinition.getHostName( ) + ": " + serviceId + ": " ) ;

		switch ( this ) {

		case threads:
			alertMessage.append( "OS Threads: "
					+ collected
					+ ", Alert threshold: " + maxAllowed ) ;
			break ;

		case diskSpace:
			alertMessage.append( "Disk Space: "
					+ CSAP.printBytesWithUnits( collected * 1024 * 1024 )
					+ ", Alert threshold: " + CSAP.printBytesWithUnits( maxAllowed * 1024 * 1024 ) ) ;
			break ;

		case diskWriteRate:
			alertMessage.append( "Disk Writes: "
					+ collected + "Kb/s, Alert threshold: " + maxAllowed ) ;

			if ( serviceDefinition.getName( )
					.equals( CsapCore.AGENT_NAME ) ) {

				alertMessage.append( " (log rotation, deployment)" ) ;

			}
			break ;

		case memory:
			alertMessage.append( "Memory (RSS): "
					+ CSAP.printBytesWithUnits( collected * 1024 * 1024 )
					+ ", Alert threshold: " + CSAP.printBytesWithUnits( maxAllowed * 1024 * 1024 ) ) ;
			break ;

		case sockets:
			alertMessage.append( "Network Connections: "
					+ collected
					+ " sockets, Alert threshold: " + maxAllowed ) ;
			break ;

		case openFileHandles:
			alertMessage.append( "OS File Handles: "
					+ collected
					+ " open, Alert threshold: " + maxAllowed ) ;
			break ;

		case cpu:
			alertMessage.append( "Process CPU: "
					+ collected
					+ "%, Alert threshold: " + maxAllowed ) ;
			break ;

		case httpConnections:
			alertMessage.append( "Http Connections: "
					+ collected
					+ " Open, Alert threshold: " + maxAllowed ) ;
			break ;

		default:
			throw new AssertionError( this.name( ) ) ;

		}

		return alertMessage.toString( ) ;

	}

	/**
	 *
	 * Supports 2 distinct flows: 1) Node Agent 2) Node Manager
	 *
	 * - we are NOT verifying InstanceConfig data, but can use the non-runtime
	 * configuration
	 *
	 * In Node Manager scenario, runtime data is passed in after being loaded from
	 * remote host. For implementation, we always pass in runtime data versus using
	 * local configuration.
	 *
	 * @return
	 */
	static public ArrayList<String> getServiceAlertsAndUpdateCpu (
																	ServiceInstance serviceDefinition ,
																	double alertLevel ,
																	boolean includeKubernetesWarnings ,
																	JsonNode collectedServiceState ,
																	EnvironmentSettings lifeCycleMetaData ,
																	ObjectNode healthReport ) {

		ArrayList<String> summaryErrors = new ArrayList<String>( ) ;

		logger.debug( "collectedServiceData: {} \n{}", serviceDefinition.toSummaryString( ), CSAP.jsonPrint(
				collectedServiceState ) ) ;

		JsonNode containers = collectedServiceState.path( HostKeys.containers.jsonId ) ;

		ServiceInstance buildService ;

		try {

			buildService = ServiceInstance.buildInstance( jacksonMapper, collectedServiceState ) ;

		} catch ( IOException e1 ) {

			buildService = serviceDefinition ;
			logger.warn( "Failed to parse service: {}", CSAP.jsonPrint( collectedServiceState ) ) ;

		}

		ServiceInstance collectedService = buildService ;

		AtomicInteger containerIndex = new AtomicInteger( 0 ) ;

		// AND refer to HostStatusManager
		CSAP.jsonStream( containers ).forEach( collectedContainerState -> {

			String serviceId = serviceDefinition.getName( ) ;

			if ( serviceDefinition.is_cluster_kubernetes( ) ) {

				serviceId += "-" + containerIndex.addAndGet( 1 ) ;

			}

			// JsonNode collectedServiceData = service_runtime_state.at( "/containers/0" ) ;

			logger.debug( "collectedService: {}", collectedService.details( ) ) ;

			//
			// Note: enums are put in a loop, versus explicit availability via the
			// collectedService
			//
			for ( ServiceAlertsEnum alert : ServiceAlertsEnum.values( ) ) {

				try {

					var maxAllowed = Math.round(
							alertLevel *
									getEffectiveLimit(
											serviceDefinition,
											lifeCycleMetaData,
											alert ) ) ;
					var collected = -1 ;

					if ( collectedContainerState.has( alert.value ) ) {

						collected = collectedContainerState.get( alert.value ).asInt( -1 ) ;

					}

					logger.debug( "{} alert: {}, maxAllowed: {} collected: {}}",
							serviceId, alert, maxAllowed, collected ) ;

					// maxValues.get(attributeName)
					if ( collected > maxAllowed ) {

						var alertMessage = alert.buildMessage( serviceId, serviceDefinition,
								collected,
								maxAllowed ) ;

						summaryErrors.add( alertMessage.toString( ) ) ;

						ServiceAlertsEnum.addServiceResourceAlerts(
								healthReport, serviceDefinition.getHostName( ),
								collectedContainerState,
								serviceDefinition,
								alert, collected, maxAllowed ) ;

					}

				} catch ( Exception e ) {

					summaryErrors.add( serviceDefinition.getHostName( ) + ": " + serviceId + ": " + alert.value
							+ " Value: " + collectedContainerState.get( alert.value )
							+ " Failed evaluation: " + e.getMessage( ) ) ;

				}

			}

			logger.debug( "{} inactive: {}, userStopped: {}, files package: {}, monitor: {}",
					serviceId,
					isCollectedServiceInactive( collectedContainerState ),
					collectedService.isUserStopped( ),
					serviceDefinition.is_files_only_package( ),
					serviceDefinition.is_os_process_monitor( ) ) ;

			if ( ! serviceDefinition.is_files_only_package( )
					&& ! serviceDefinition.is_os_process_monitor( )
					&& isCollectedServiceInactive( collectedContainerState )
					&& ! collectedService.isUserStopped( ) ) {

				if ( serviceDefinition.is_cluster_kubernetes( ) ) {

					if ( serviceDefinition.isKubernetesSchemaDeployment( ) ) {
						// settting container or replica count to 0 will disable process checks for
						// kubernetes services

					} else if ( includeKubernetesWarnings ) {

						summaryErrors.add( buildKubernetesWarning( serviceDefinition.getHostName( ), serviceId ) ) ;
						ObjectNode details = jacksonMapper.createObjectNode( ) ;
						details.put( ALERT_TYPE, TYPE_KUBERNETES_PROCESS_CHECK ) ;
						ServiceAlertsEnum.addServiceApplicationAlerts(
								healthReport, collectedContainerState,
								serviceDefinition.getHostName( ), serviceDefinition.getName( ),
								serviceDefinition,
								details ) ;

					}

				} else {

					summaryErrors.add( serviceDefinition.getHostName( ) + ": " + serviceId
							+ PROCESS_CRASH_OR_KILL ) ;

					ObjectNode details = jacksonMapper.createObjectNode( ) ;
					details.put( ALERT_TYPE, TYPE_PROCESS_CRASH_OR_KILL ) ;
					ServiceAlertsEnum.addServiceApplicationAlerts(
							healthReport, collectedContainerState,
							serviceDefinition.getHostName( ), serviceDefinition.getName( ),
							serviceDefinition,
							details ) ;

				}

			} else if ( serviceDefinition.isApplicationHealthEnabled( ) ) {

				applicationHealthReport( serviceId, serviceDefinition, summaryErrors, collectedContainerState,
						healthReport ) ;

			}

			logger.debug( "{}   isInactive:{}, result: {}",
					serviceDefinition.getName( ), serviceDefinition.getDefaultContainer( ).isInactive( ),
					summaryErrors ) ;

		} ) ;

		return summaryErrors ;

	}

	public static void addHostDetailAlert (
											ObjectNode alertReport ,
											String host ,
											String type ,
											int current ,
											int max ) {

		JsonNode detailsNode = alertReport.path( HealthManager.HEALTH_DETAILS ) ;

		if ( detailsNode.isArray( ) ) {

			ArrayNode detailReport = (ArrayNode) detailsNode ;

			ObjectNode item = detailReport.addObject( ) ;
			// item.put( "host", host ) ;
			item.put( "category", "os-host" ) ;
			item.put( ALERT_TYPE, type ) ;
			item.put( "current", current ) ;
			item.put( "max", max ) ;

		}

	}

	public static void addHostSourceDetailAlert (
													ObjectNode alertReport ,
													String host ,
													String type ,
													String source ,
													int current ,
													int max ) {

		JsonNode detailsNode = alertReport.path( HealthManager.HEALTH_DETAILS ) ;

		if ( detailsNode.isArray( ) ) {

			ArrayNode detailReport = (ArrayNode) detailsNode ;

			ObjectNode item = detailReport.addObject( ) ;
			// item.put( "host", host ) ;
			item.put( "category", "os-host" ) ;
			item.put( ALERT_TYPE, type ) ;
			item.put( ALERT_SOURCE, source ) ;
			item.put( "current", current ) ;
			item.put( "max", max ) ;

		}

	}

	public static void addServiceResourceAlerts (
													ObjectNode alertReport ,
													String host ,
													JsonNode collectedContainerState ,
													ServiceInstance instance ,
													ServiceAlertsEnum alert ,
													long current ,
													long max ) {

		JsonNode detailsNode = alertReport.path( HealthManager.HEALTH_DETAILS ) ;

		if ( detailsNode.isArray( ) ) {

			ArrayNode detailReport = (ArrayNode) detailsNode ;

			ObjectNode item = detailReport.addObject( ) ;
			// item.put( "host", host ) ;
			item.put( "category", "os-process-resource" ) ;
			item.put( ALERT_TYPE, alert.getName( ) ) ;
			item.put( ALERT_SOURCE, instance.getName( ) ) ;
			JsonNode pids = collectedContainerState.path( "pid" ) ;

			if ( pids.isArray( ) ) {

				item.set( "pids", pids ) ;

			}

			item.put( "current", current ) ;
			item.put( "max", max ) ;
			item.put( "runtime", instance.getRuntime( ) ) ;
			item.put( "cluster", instance.getCluster( ) ) ;

			// aggregatedServiceReport.put( ClusterType.CLUSTER_TYPE, clusterType ) ;
			if ( instance.getClusterType( ) == ClusterType.KUBERNETES ) {

				item.put( "runtime", ClusterType.KUBERNETES.getJson( ) ) ;
				item.put( "podNamespace", collectedContainerState.path( "podNamespace" ).asText( "-" ) ) ;
				item.put( "podIp", collectedContainerState.path( "podIp" ).asText( "-" ) ) ;

			}

		}

	}

	public static void addServiceApplicationAlerts (
														ObjectNode alertReport ,
														JsonNode collectedContainerState ,
														String host ,
														String serviceName ,
														ServiceInstance instance ,
														JsonNode alertDef ) {

		JsonNode detailsNode = alertReport.path( HealthManager.HEALTH_DETAILS ) ;

		if ( detailsNode.isArray( ) ) {

			ArrayNode detailReport = (ArrayNode) detailsNode ;

			ObjectNode item = detailReport.addObject( ) ;
			// item.put( "host", host ) ;
			item.put( "category", "os-process-application" ) ;
			item.put( ALERT_SOURCE, serviceName ) ;

			JsonNode pids = collectedContainerState.path( "pid" ) ;

			if ( pids.isArray( ) ) {

				item.set( "pids", pids ) ;

			}

			if ( alertDef.isObject( ) ) {

				item.setAll( (ObjectNode) alertDef ) ;

			} else {

				logger.warn( "{} on {} unexpected report format: {}", serviceName, host, alertDef ) ;

			}

			item.put( "runtime", instance.getRuntime( ) ) ;
			item.put( "cluster", instance.getCluster( ) ) ;
			// item.put( "id", alertDef.path("id").asText("not-specified") ) ;
			// item.put( "type", alertDef.path("type").asText("not-specified") ) ;
			// item.put( "description", alertDef.path("description").asText("not-specified")
			// ) ;

			// aggregatedServiceReport.put( ClusterType.CLUSTER_TYPE, clusterType ) ;
			if ( instance.getClusterType( ) == ClusterType.KUBERNETES ) {

				item.put( "runtime", ClusterType.KUBERNETES.getJson( ) ) ;
				item.put( "podNamespace", collectedContainerState.path( "podNamespace" ).asText( "-" ) ) ;
				item.put( "podIp", collectedContainerState.path( "podIp" ).asText( "-" ) ) ;

			}

		}

	}

	private static void applicationHealthReport (
													String serviceId ,
													ServiceInstance serviceDefinition ,
													ArrayList<String> summaryErrors ,
													JsonNode collectedContainerState ,
													ObjectNode healthReportFull ) {

		if ( isCollectedServiceInactive( collectedContainerState ) ) {

			return ;

		}

		JsonNode collectedHealthReport = collectedContainerState.path( HostKeys.healthReportCollected.jsonId ) ;

		if ( collectedHealthReport.isObject( ) ) {

			boolean isHealthy = collectedHealthReport.path( Report.healthy.json ).asBoolean( false ) ;

			if ( ! isHealthy ) {

				String summary = "(Review logs for more information)" ;

				JsonNode microErrors = collectedHealthReport.path( Report.errors.json ) ;
				JsonNode starterErrors = collectedHealthReport.path( AlertFields.limitsExceeded.json ) ;

				if ( microErrors.isArray( ) && microErrors.size( ) > 0 ) {

					summary = " (1 of " + microErrors.size( ) + ") "
							+ microErrors.get( 0 ).path( Report.id.json ).asText( ) ;
					// + microErrors.get( 0 ).path( Report.description.json ).asText() ;

					CSAP.jsonStream( microErrors ).forEach( microError -> {

						ServiceAlertsEnum.addServiceApplicationAlerts(
								healthReportFull, collectedContainerState,
								serviceDefinition.getHostName( ), serviceDefinition.getName( ),
								serviceDefinition,
								microError ) ;

					} ) ;

				} else if ( starterErrors.isArray( ) && starterErrors.size( ) > 0 ) {

					summary = " (1 of " + starterErrors.size( ) + ") "
							+ starterErrors.get( 0 ).path( AlertInstance.AlertItem.id.json ).asText( ) ;
					CSAP.jsonStream( starterErrors ).forEach( starterError -> {

						ServiceAlertsEnum.addServiceApplicationAlerts(
								healthReportFull, collectedContainerState,
								serviceDefinition.getHostName( ), serviceDefinition.getName( ),
								serviceDefinition,
								starterError ) ;

					} ) ;

				}

				summaryErrors.add( buildFailedHealthMessage( serviceId, summary, serviceDefinition ) ) ;

			}

		} else {

			summaryErrors.add( buildFailedHealthMessage( serviceId, "missing health report", serviceDefinition ) ) ;
			var alertDef = jacksonMapper.createObjectNode( ) ;
			alertDef.put( CsapMicroMeter.HealthReport.Report.description.json, "missing health" ) ;

			ServiceAlertsEnum.addServiceApplicationAlerts(
					healthReportFull, collectedContainerState,
					serviceDefinition.getHostName( ), serviceDefinition.getName( ),
					serviceDefinition,
					alertDef ) ;

		}

	}

	public static String buildKubernetesWarning ( String host , String service ) {

		return host + ": " + service + KUBERNETES_PROCESS_CHECKS ;

	}

	static boolean isCollectedServiceInactive ( JsonNode collectedServiceData ) {

		return collectedServiceData.has( "cpuUtil" )
				&& collectedServiceData.get( "cpuUtil" ).asText( ).equals( ContainerState.INACTIVE ) ;

	}

	// Used in app-portal-main.js as well
	public static final String healthStatusToken = "service live:" ;

	static String buildFailedHealthMessage ( String serviceId , String message , ServiceInstance serviceDefinition ) {

		String errorMsg = serviceDefinition.getHostName( ) + ": " + serviceId
				+ ": failure: " + message ;

		// if ( serviceDefinition.isHealthStatusConfigured() ) {
		if ( serviceDefinition.isApplicationHealthEnabled( ) ) {

			errorMsg += ", view: " + healthStatusToken + " " + serviceDefinition.getHealthUrl( serviceId ) ;

		} else {

			errorMsg += ", review logs." ;

		}

		return errorMsg ;

	}

	//
	// get csap default
	// -> override with environment default if exists
	// -> override with cluster default if exists
	// -> override with service limit if exists
	//
	static public long getEffectiveLimit (
											ServiceInstance serviceDefinition ,
											EnvironmentSettings environmentSettings ,
											ServiceAlertsEnum alert ) {

		// default to the value in environment
		var maxAllowedEnv = environmentSettings.getMonitorForCluster(
				// serviceDefinition.getHostName(),
				serviceDefinition.getCluster( ),
				alert ) ;

		logger.debug( "{} maxAllowedEnv: {}", alert, maxAllowedEnv ) ;

//		if ( alert == ServiceAlertsEnum.diskSpace ) {
//
//			return getMaxDiskInMb( serviceDefinition, environmentSettings ) ;
//
//		}

		var maxAllowed = maxAllowedEnv ;

		// check for local
		if ( serviceDefinition.getMonitors( ) != null
				&& serviceDefinition.getMonitors( ).has( alert.maxId( ) ) ) {

			var alertSetting = serviceDefinition.getMonitors( )
					.path( alert.maxId( ) ).asText( )
					.toLowerCase( ) ;

			//
			// any attribute can use m or g
			//
			maxAllowed = EnvironmentSettings.convertUnitToKb( alertSetting, alert ) ;

			if ( alert == ServiceAlertsEnum.memory ) {

				// stored as mb
				maxAllowed = maxAllowed / 1024 ;

				// handle default byte scenario

			}

//			logger.info( "{} alertSetting: {} , maxAllowed: {}",
//					serviceDefinition.getName( ),
//					alertSetting,
//					maxAllowed ) ;

		}

		logger.debug( "{} limit: {}", alert, maxAllowed ) ;

		return maxAllowed ;

	}

	static public String getMaxAllowedSummary (
												ServiceInstance serviceDefinition ,
												EnvironmentSettings lifecycleSettings ,
												ServiceAlertsEnum alert ) {

		String maxString = "" + getEffectiveLimit( serviceDefinition, lifecycleSettings, alert ) ;

		if ( serviceDefinition.getMonitors( ) != null && serviceDefinition.getMonitors( ).has( alert.maxId( ) ) ) {

			maxString = serviceDefinition.getMonitors( ).get( alert.maxId( ) ).asText( ) ;

		}

		return maxString ;

	}

	static public Map<String, String> limitDefinitionsForService (
																	ServiceInstance serviceDefinition ,
																	EnvironmentSettings lifeCycleSettings ) {

		Map<String, String> limits = Arrays.stream( ServiceAlertsEnum.values( ) )
				.collect( Collectors.toMap(
						alert -> alert.value,
						alert -> {

							if ( serviceDefinition == null ) {

								return "-" ;

							}

							return getMaxAllowedSummary(
									serviceDefinition,
									lifeCycleSettings,
									alert ) ;

						} ) ) ;
		return limits ;

	}

	static public ObjectNode getAdminUiLimits (
												ServiceInstance serviceDefinition ,
												EnvironmentSettings lifecycleSettings )
		throws JsonProcessingException {

		ObjectNode limitsNode = jacksonMapper.createObjectNode( ) ;

		for ( ServiceAlertsEnum alert : ServiceAlertsEnum.values( ) ) {

			String maxAllowed = "" + getEffectiveLimit( serviceDefinition, lifecycleSettings, alert ) ;

			if ( alert == ServiceAlertsEnum.memory ) {

				// special handling to deal with units in UI: must be "g" or "m"
				// maxAllowed = lifeCycleMetaData.getMax_rssMemoryRaw();
				if ( serviceDefinition.getMonitors( ) != null && serviceDefinition.getMonitors( ).has(
						ServiceAlertsEnum.memory.maxId( ) ) ) {

					maxAllowed = serviceDefinition.getMonitors( ).get( ServiceAlertsEnum.memory.maxId( ) ).asText( ) ;

				}

				// force into MB; use when no units are specified as limits
				if ( maxAllowed.matches( "^[0-9]+$" ) ) {

					maxAllowed = ( getEffectiveLimit( serviceDefinition, lifecycleSettings, alert ) ) + "m" ;

				}

			}

			limitsNode.put( alert.value, maxAllowed ) ;

		}
		// resultsJson.put("socketCount", getMaxAllowed(lifeCycleMetaData,
		// "socketCount"));

		// rarely used .. but allows ui warnings
		ObjectNode customJavaSettings = serviceDefinition.getPerformanceConfiguration( ) ;

		if ( customJavaSettings != null ) {

			String metricId = null ;

			for ( Iterator<String> metricIdIterator = customJavaSettings
					.fieldNames( ); metricIdIterator.hasNext( ); ) {

				metricId = metricIdIterator.next( ).trim( ) ;
				ObjectNode metricConfigNode = (ObjectNode) customJavaSettings
						.get( metricId ) ;

				if ( metricConfigNode.has( "max" ) ) {

					String maxValue = metricConfigNode
							.path( "max" ).asText( ) ;

					try {

						limitsNode.put( metricId, Integer.parseInt( maxValue ) ) ;

					} catch ( NumberFormatException e ) {

						logger.error( "node :" + metricConfigNode, e ) ;
						;

					}

				}

			}

		}

		// rarely used .. but allows ui warnings
		ObjectNode javaStandardSettings = serviceDefinition.getAttributeAsObject(
				ServiceAttributes.javaAlertWarnings ) ;

		if ( javaStandardSettings != null ) {

			String metricId = null ;

			for ( Iterator<String> metricIdIterator = javaStandardSettings
					.fieldNames( ); metricIdIterator.hasNext( ); ) {

				metricId = metricIdIterator.next( ).trim( ) ;
				ObjectNode metricConfigNode = (ObjectNode) javaStandardSettings
						.get( metricId ) ;

				if ( metricConfigNode.has( "max" ) ) {

					String maxValue = metricConfigNode
							.path( "max" ).asText( ) ;

					try {

						limitsNode.put( metricId, Integer.parseInt( maxValue ) ) ;

					} catch ( NumberFormatException e ) {

						logger.error( "node :" + metricConfigNode, e ) ;
						;

					}

				}

			}

		}

		return limitsNode ;

	}

//	static public long getMaxDiskInMb (
//										ServiceInstance serviceDefinition ,
//										EnvironmentSettings environmentSettings ) {
//
//		long maxAllowed = environmentSettings.getMonitorForCluster(
//				serviceDefinition.getCluster( ),
//				diskSpace ) ;
//
//		if ( serviceDefinition.getMonitors( ) != null && serviceDefinition.getMonitors( ).has( diskSpace.maxId( ) ) ) {
//
//			// maxAllowed = serviceDefinition.getMonitors().get(
//			// diskSpace.maxId() ).asInt();
//			String alertSetting = serviceDefinition.getMonitors( ).get( diskSpace.maxId( ) ).asText( ).toLowerCase( ) ;
//
//			maxAllowed = EnvironmentSettings.convertUnitToKb( alertSetting, diskSpace ) ;
//
//			// in case user specified limit with m or g - the above switches to
//			// kb
//			// but we default to MB for disk
//			maxAllowed = maxAllowed / 1024 ;
//
//		}
//
//		return maxAllowed ;
//
//	}

	public static String fromK8sToCrashOrKill ( String errorText ) {

		return errorText.replaceAll( ServiceAlertsEnum.KUBERNETES_PROCESS_CHECKS,
				ServiceAlertsEnum.PROCESS_CRASH_OR_KILL ) ;

	}
}
