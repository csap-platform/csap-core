/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.csap.agent.api ;

import java.io.File ;
import java.io.IOException ;
import java.time.LocalDate ;
import java.time.LocalDateTime ;
import java.time.format.DateTimeFormatter ;
import java.util.ArrayList ;
import java.util.Arrays ;
import java.util.List ;
import java.util.Map ;

import javax.inject.Inject ;
import javax.servlet.http.HttpServletRequest ;
import javax.servlet.http.HttpServletResponse ;

import org.apache.commons.lang3.StringUtils ;
import org.csap.CsapMonitor ;
import org.csap.agent.CsapCore ;
import org.csap.agent.CsapCoreService ;
import org.csap.agent.container.DockerIntegration ;
import org.csap.agent.container.kubernetes.KubernetesIntegration ;
import org.csap.agent.container.kubernetes.KubernetesJson ;
import org.csap.agent.container.kubernetes.SpecBuilder ;
import org.csap.agent.integrations.CsapEvents ;
import org.csap.agent.linux.OsCommandRunner ;
import org.csap.agent.linux.OutputFileMgr ;
import org.csap.agent.model.ActiveUsers ;
import org.csap.agent.model.Application ;
import org.csap.agent.model.HealthManager ;
import org.csap.agent.model.ServiceAlertsEnum ;
import org.csap.agent.model.ServiceInstance ;
import org.csap.agent.services.OsManager ;
import org.csap.agent.services.ServiceCommands ;
import org.csap.agent.services.ServiceOsManager ;
import org.csap.agent.stats.OsProcessCollector ;
import org.csap.agent.stats.OsSharedResourcesCollector ;
import org.csap.agent.stats.ServiceCollector ;
import org.csap.agent.ui.rest.FileRequests ;
import org.csap.docs.CsapDoc ;
import org.csap.helpers.CSAP ;
import org.csap.helpers.CsapApplication ;
import org.csap.integations.CsapMicroMeter ;
import org.csap.security.SpringAuthCachingFilter ;
import org.jasypt.encryption.pbe.StandardPBEStringEncryptor ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.beans.factory.annotation.Autowired ;
import org.springframework.beans.factory.annotation.Qualifier ;
import org.springframework.context.annotation.Profile ;
import org.springframework.core.io.FileSystemResource ;
import org.springframework.http.MediaType ;
import org.springframework.web.bind.annotation.GetMapping ;
import org.springframework.web.bind.annotation.PathVariable ;
import org.springframework.web.bind.annotation.PostMapping ;
import org.springframework.web.bind.annotation.RequestMapping ;
import org.springframework.web.bind.annotation.RequestParam ;
import org.springframework.web.bind.annotation.RestController ;
import org.springframework.web.client.RestTemplate ;
import org.springframework.web.multipart.MultipartFile ;

import com.fasterxml.jackson.core.JsonGenerationException ;
import com.fasterxml.jackson.databind.JsonMappingException ;
import com.fasterxml.jackson.databind.JsonNode ;
import com.fasterxml.jackson.databind.ObjectMapper ;
import com.fasterxml.jackson.databind.node.ArrayNode ;
import com.fasterxml.jackson.databind.node.ObjectNode ;

/**
 *
 * @author user-id
 */

@Profile ( "agent" )
@RestController
@CsapMonitor ( prefix = "api.agent" )
@RequestMapping ( CsapCoreService.API_AGENT_URL )
@CsapDoc ( title = "/api/agent/*: apis for querying data collected by management agent." , type = CsapDoc.PUBLIC , notes = {
		"CSAP Performance APis provide access to the runtime data. This includes everything from the state "
				+ "of the host resources (disk/cpu/memory), java (heap, threads), and service custom metrics",
		"For  access to aggregated performance collections - refer to <a class='simple' href='class?clazz=org.csap.agent.input.http.api.Runtime_Application'>Application Apis</a> ",
		"<a class='csap-link' target='_blank' href='https://github.com/csap-platform/csap-core/wiki'>learn more</a>",
		"<img class='csapDocImage' src='CSAP_BASE/images/csapboot.png' />"
				+ "<img class='csapDocImage' src='CSAP_BASE/images/event.png' />"
				+ "<img class='csapDocImage' src='CSAP_BASE/images/editOverview.png' />",
		"Note: unless otherwise stated - these apis can only be executed on CSAP Admin Service instances. Typically: https://yourApp/admin"
} )
public class AgentApi {

	public static final String CORE_RESULTS = "coreResults" ;

	final Logger logger = LoggerFactory.getLogger( this.getClass( ) ) ;

	ObjectMapper jacksonMapper = new ObjectMapper( ) ;

	// Spring Injected

	private Application csapApp ;

	@Autowired
	public AgentApi ( Application csapApp ) {

		this.csapApp = csapApp ;

	}

	private ObjectNode managerError ;

	{

		managerError = jacksonMapper.createObjectNode( ) ;
		managerError.put( "error", "Only permitted on admin nodes" ) ;

	}

	@CsapDoc ( notes = "Health of host, return a complete JSON status object" )
	@GetMapping ( "/health" )
	public ObjectNode health (
								@RequestParam ( defaultValue = "1.0" ) double alertLevel ,
								@RequestParam ( value = CsapCore.PROJECT_PARAMETER , defaultValue = Application.ALL_PACKAGES ) String csapProject )
		throws Exception {

		ObjectNode healthJson = jacksonMapper.createObjectNode( ) ;

		ObjectNode healthReport = csapApp.healthManager( ).build_health_report( ServiceAlertsEnum.ALERT_LEVEL, false,
				csapApp.getProject( csapProject ) ) ;

		if ( healthReport.path( HealthManager.HEALTH_SUMMARY ).size( ) == 0 ) {

			healthJson.put( "Healthy", true ) ;

		} else {

			healthJson.put( "Healthy", false ) ;
			healthJson.setAll( healthReport ) ;

		}

		return healthJson ;

	}

	int lastKubernetesMasterCertCheckDay = 0 ;

	CsapMicroMeter.HealthReport kubernetesHealthReport = new CsapMicroMeter.HealthReport( null, jacksonMapper ) ;

	@Inject
	CsapMicroMeter.MeterReport meterReport ;

	@CsapDoc ( notes = {
			"Health of kubernetes, return a complete JSON status object",
			"optional parameter: store - used to store the event keep for subsequent comparisons"
	} , linkGetParams = {
			"csapui=true"
	} )
	@GetMapping ( "/health/kubernetes" )
	public JsonNode healthKubernetes (
										@RequestParam ( defaultValue = "false" ) boolean csapui ,
										HttpServletRequest request )
		throws Exception {

		logger.debug( "csapui: {}", csapui ) ;

		if ( ! csapApp.isKubernetesInstalledAndActive( ) ) {

			var report = jacksonMapper.createObjectNode( ) ;
			report.put( "installed-and-active", false ) ;
			return report ;

		}

		var kubernetesPerformanceReport = jacksonMapper.createObjectNode( ) ;
		var healthReport = kubernetesPerformanceReport.putObject( CsapMicroMeter.HealthReport.Report.name.json ) ;

		var reportKey = KubernetesJson.report_metrics.json( ) ;

		if ( csapApp.kubeletInstance( ).isKubernetesMaster( ) ) {

			reportKey = KubernetesJson.report_namespace_all.json( ) ;

		}

		if ( csapui ) {

			logger.info( "csapui set - refreshing report data" ) ;
			csapApp.getKubernetesIntegration( ).buildKubernetesHealthReport( ) ;

		}

		var nodeReport = csapApp.getKubernetesIntegration( ).getCachedReport( reportKey ) ;

		logger.debug( "type: {} nodeReport {}", reportKey, CSAP.jsonPrint( nodeReport ) ) ;

		kubernetesPerformanceReport.set( "report", nodeReport ) ;

		// kubernetesPerformanceReport.put( "csapui", csapui +"-" + lastEventCount) ;

		ObjectNode timers = kubernetesPerformanceReport.putObject( "timers" ) ;
		meterReport.addMicroMeter(
				timers,
				csapApp.metrics( ).find( KubernetesIntegration.SUMMARY_TIMER ),
				"summary",
				1,
				false ) ;

		meterReport.addMicroMeter(
				timers,
				csapApp.metrics( ).find( SpecBuilder.DEPLOY_TIMER ),
				"deploy",
				1,
				false ) ;

		ArrayNode httpHealthErrors = jacksonMapper.createArrayNode( ) ;

		if ( nodeReport.has( "error" ) ) {

			httpHealthErrors.add( kubernetesHealthReport.buildError(
					"connection-failure",
					"kube-connection",
					"connection failure" ) ) ;

		} else {

			if ( nodeReport.has( KubernetesJson.heartbeat.json( ) )
					&& ! nodeReport.path( KubernetesJson.heartbeat.json( ) ).asBoolean( ) ) {

				httpHealthErrors.add( kubernetesHealthReport.buildError(
						"health",
						"kube-node",
						"Node Report failed to complete" ) ) ;

			}

			if ( ! nodeReport.path( "metrics" ).path( KubernetesJson.heartbeat.json( ) ).asBoolean( ) ) {

				httpHealthErrors.add( kubernetesHealthReport.buildError(
						"health",
						"kube-node",
						"metric Report failed to complete" ) ) ;

			}

			if ( ! nodeReport.at( "/metrics/current/healthy" ).asBoolean( ) ) {

				httpHealthErrors.add( kubernetesHealthReport.buildError(
						"health",
						"kube-node",
						"Node conditions failure" ) ) ;

			}

			if ( csapApp.kubeletInstance( ).isKubernetesMaster( ) ) {

				// clear cache every night
				int nowDayOfYear = LocalDate.now( ).getDayOfYear( ) ;

				if ( nowDayOfYear != lastKubernetesMasterCertCheckDay ) {

					logger.info( "Running master certificate checks" ) ;
					var maxDaysBeforeAlerting = csapApp.environmentSettings( ).getKubernetesCertMinimumDays( ) ;
					var fewestDaysToExpiration = csapApp.getOsManager( ).kubernetes_certs_expiration_days(
							maxDaysBeforeAlerting ) ;

					if ( fewestDaysToExpiration < maxDaysBeforeAlerting ) {

						var error = kubernetesHealthReport.buildError( "certs-nearing-expiration", "application",
								"certificate expiration pending: " + fewestDaysToExpiration
										+ " days. Reset all masters using kubelet cert reset job" ) ;

						error.put( "current", fewestDaysToExpiration ) ;
						httpHealthErrors.add( error ) ;

					} else {

						lastKubernetesMasterCertCheckDay = nowDayOfYear ;

					}

				}

			}

			if ( csapApp.kubeletInstance( ).isKubernetesPrimaryMaster( ) ) {

				// extended health checks ONLY on primary master
				var eventCountReports = csapApp.getKubernetesIntegration( ).getCachedReport(
						KubernetesJson.report_events
								.json( ) ) ;

				if ( eventCountReports != null
						&& ! eventCountReports.isEmpty( ) ) {

					var totalEventsFound = CSAP.jsonStream( eventCountReports )
							.mapToInt( JsonNode::asInt )
							.sum( ) ;

					var error = kubernetesHealthReport.buildError( "event-increased", "application",
							"Event count changed, count: " + totalEventsFound ) ;
					error.put( "current", totalEventsFound ) ;
					httpHealthErrors.add( error ) ;

					CSAP.asStreamHandleNulls( eventCountReports )
							.limit( 3 )
							.forEach( eventName -> {

								var count = eventCountReports.path( eventName ).asInt( ) ;

								var eventError = kubernetesHealthReport.buildError( "event-increased", "application",
										eventName + " found : " + count ) ;
								eventError.put( "current", count ) ;
								httpHealthErrors.add( eventError ) ;

							} ) ;

				}

			}

		}

		// kubernetesPerformanceReport.put( "lastEventCounts",
		// lastEventCounts.toString()) ;
		kubernetesHealthReport.setErrors( httpHealthErrors ) ;

		ObjectNode currentHealth = (ObjectNode) kubernetesHealthReport
				.buildEmbeddedReport( request )
				.path( CsapMicroMeter.HealthReport.Report.name.json ) ;
		currentHealth.remove( "source" ) ;
		healthReport.setAll( currentHealth ) ;

		logger.debug( "csapui: {}", csapui ) ;

		return kubernetesPerformanceReport ;

	}

	CsapMicroMeter.HealthReport dockerHealthReport = new CsapMicroMeter.HealthReport( null, jacksonMapper ) ;

	@CsapDoc ( notes = "Health of docker, return a complete JSON status object" )
	@GetMapping ( "/health/docker" )
	public JsonNode healthDocker ( HttpServletRequest request )
		throws Exception {

		ObjectNode dockerReport = jacksonMapper.createObjectNode( ) ;
		JsonNode dockerSummary = csapApp.getDockerIntegration( ).buildSummary( ) ;
		dockerReport.set( "report", dockerSummary ) ;

		ObjectNode timers = dockerReport.putObject( "timers" ) ;
		meterReport.addMicroMeter(
				timers,
				csapApp.metrics( ).find( DockerIntegration.SUMMARY_TIMER ),
				"summary",
				1,
				false ) ;

		meterReport.addMicroMeter(
				timers,
				csapApp.metrics( ).find( DockerIntegration.DEPLOY_TIMER ),
				"deploy",
				1,
				false ) ;

		ArrayNode httpHealthErrors = jacksonMapper.createArrayNode( ) ;

		if ( dockerSummary.has( "error" ) ) {

			httpHealthErrors.add( dockerHealthReport.buildError( "connection-failure", "docker-connection",
					"connection failure" ) ) ;

		} else {

			var collectedImageCount = dockerSummary.path( "imageCount" ).asInt( 9999 ) ;
			var collectedContainerCount = dockerSummary.path( "containerCount" ).asInt( 9999 ) ;
			var collectedVolumeCount = dockerSummary.path( "volumeCount" ).asInt( 9999 ) ;

			if ( collectedImageCount > csapApp.environmentSettings( ).getDockerMaxImages( ) ) {

				var maxImageAlert = dockerHealthReport.buildError( "docker-max-images", "application",
						"max images exceeded" ) ;
				maxImageAlert.put( "current", collectedImageCount ) ;
				maxImageAlert.put( "max", csapApp.environmentSettings( ).getDockerMaxImages( ) ) ;
				httpHealthErrors.add( maxImageAlert ) ;

			}

			if ( collectedContainerCount > csapApp.environmentSettings( ).getDockerMaxContainers( ) ) {

				var maxContainerAlert = dockerHealthReport.buildError( "docker-max-containers", "application",
						"max containers exceeded" ) ;
				maxContainerAlert.put( "current", collectedContainerCount ) ;
				maxContainerAlert.put( "max", csapApp.environmentSettings( ).getDockerMaxContainers( ) ) ;
				httpHealthErrors.add( maxContainerAlert ) ;

			}

			if ( collectedVolumeCount > csapApp.environmentSettings( ).getDockerMaxVolumes( ) ) {

				var maxVolumeAlert = dockerHealthReport.buildError( "docker-max-volumes", "application",
						"max volumes exceeded" ) ;
				maxVolumeAlert.put( "current", collectedVolumeCount ) ;
				maxVolumeAlert.put( "max", csapApp.environmentSettings( ).getDockerMaxVolumes( ) ) ;
				httpHealthErrors.add( maxVolumeAlert ) ;

			}

		}

		dockerHealthReport.setErrors( httpHealthErrors ) ;

		dockerReport.set(
				CsapMicroMeter.HealthReport.Report.name.json,
				dockerHealthReport
						.buildEmbeddedReport( request )
						.path( CsapMicroMeter.HealthReport.Report.name.json ) ) ;

		return dockerReport ;

	}

	public final static String RUNTIME_URL = "/runtime" ;

	@CsapDoc ( notes = {
			"Runtime status of host, including cpu, disk, services, etc"
	} , linkTests = "default" )
	@GetMapping ( RUNTIME_URL )
	public JsonNode runtime ( )
		throws JsonGenerationException ,
		JsonMappingException ,
		IOException {

		return osManager.getHostRuntime( ) ;

	}

	@CsapDoc ( notes = {
			"Get the number of jobs (start, stop, deploy) queued for execution."
	} , linkTests = {
			"Show Jobs"
	} )
	@GetMapping ( "/service/jobs" )
	public ObjectNode serviceJobStatus ( )
		throws Exception {

		return serviceManager.getJobStatus( ) ;

	}

	public final static String USERS_URL = "/users/active" ;

	@CsapDoc ( notes = {
			"Uses active in past 60 minutes"
	} , linkTests = "default" )
	@GetMapping ( USERS_URL )
	public ArrayNode usersActive ( )
		throws JsonGenerationException ,
		JsonMappingException ,
		IOException {

		return activeUsers.getActive( ) ;

	}

	@Inject
	ActiveUsers activeUsers ;

	@CsapDoc ( notes = {
			"Summary status of host"
	} , linkTests = "default" )
	@GetMapping ( {
			"/status"
	} )
	public ObjectNode status ( )
		throws JsonGenerationException ,
		JsonMappingException ,
		IOException {

		ObjectNode healthJson = jacksonMapper.createObjectNode( ) ;

		if ( csapApp.isAdminProfile( ) ) {

			healthJson.put( "error", "vmHealth is only enabled on csap-agent urls." ) ;

		} else {

			healthJson = csapApp.healthManager( ).statusForAdminOrAgent( ServiceAlertsEnum.ALERT_LEVEL, true ) ;

		}

		return healthJson ;

	}

	@CsapDoc ( notes = {
			"Host disk usage", "* Agent service only"
	} )
	@GetMapping ( "/diskUsage" )
	public ObjectNode diskUsage ( )
		throws JsonGenerationException ,
		JsonMappingException ,
		IOException {

		ObjectNode diskInfo = jacksonMapper.createObjectNode( ) ;

		if ( csapApp.isAdminProfile( ) ) {

			diskInfo.put( "error",
					"Disk Usage is only enabled on csap-agent urls. Use /admin/api/hosts, then /csap-agent/api/diskUsage on host.  CSAP Command Runner UI can be used to run on all VMS at same time." ) ;

		} else {

			diskInfo.set( csapApp.getCsapHostName( ), osManager.getCachedFileSystemInfo( ) ) ;

		}

		return diskInfo ;

	}

	@CsapDoc ( notes = {
			"Service Runtime on host",
	} , linkTests = {
			CsapCore.AGENT_NAME,
			"List"
	} , linkGetParams = {
			"serviceName=csap-agent"
	} )
	@GetMapping ( "/service/runtime/{serviceName:.+}" )
	public JsonNode serviceStatus (
									@PathVariable ( "serviceName" ) String serviceName ) {

		ObjectNode serviceInfo = jacksonMapper.createObjectNode( ) ;

		ServiceInstance service = csapApp.findServiceByNameOnCurrentHost( serviceName ) ;

		if ( csapApp.isAdminProfile( ) || service == null ) {

			serviceInfo.put( "error",
					"Disk Usage is only enabled on csap-agent urls. Use /admin/api/hosts, then /csap-agent/api/diskUsage on host.  CSAP Command Runner UI can be used to run on all VMS at same time." ) ;

		} else {

			serviceInfo = service.getCoreRuntime( ) ;

			serviceInfo.put( "processFilter", service.getProcessFilter( ) ) ;
			serviceInfo.set( "containers", jacksonMapper.convertValue( service.getContainerStatusList( ),
					ArrayNode.class ) ) ;

		}

		return serviceInfo ;

	}

	@CsapDoc ( notes = {
			"Service definition on hosts",
	} , linkTests = {
			CsapCore.AGENT_NAME,
			"List"
	} , linkGetParams = {
			"serviceName=csap-agent"
	} )
	@GetMapping ( "/service/definition/{serviceName:.+}" )
	public JsonNode serviceDefinition (
										@PathVariable ( "serviceName" ) String serviceName ) {

		ObjectNode serviceInfo = jacksonMapper.createObjectNode( ) ;

		ServiceInstance service = csapApp.findServiceByNameOnCurrentHost( serviceName ) ;

		if ( csapApp.isAdminProfile( ) || service == null ) {

			serviceInfo.put( "error",
					"Disk Usage is only enabled on csap-agent urls. Use /admin/api/hosts, then /csap-agent/api/diskUsage on host.  CSAP Command Runner UI can be used to run on all VMS at same time." ) ;

		} else {

			serviceInfo = jacksonMapper.convertValue( service, ObjectNode.class ) ;

		}

		return serviceInfo ;

	}

	@Inject
	private OsManager osManager ;

	@CsapDoc ( notes = {
			"Gets Host summary information: uptime, os version, df. Optional support for JSONP"
					+ " is provided if callback parameter is included",
			"* Agent service only"
	} , linkTests = {
			"json", "jsonp"
	} , linkGetParams = {
			"a=b",
			"callback=myFunctionCall"
	} , produces = {
			MediaType.APPLICATION_JSON_VALUE, "application/javascript"
	} )
	@GetMapping ( {
			"/hostSummary",
			"/vmSummary"
	} )
	public JsonNode hostSummary (
									@RequestParam ( value = "callback" , required = false , defaultValue = "false" ) String callback )
		throws Exception {

		return osManager.getHostSummary( ) ;

	}

	// @Autowired ( required = false )
	// KubernetesIntegration kubernetesIntegration ;

	@CsapDoc ( notes = {
			"Node port url for specified kubernetes service name.",
			"Note: kubernetesService parameter may or may not be the CSAP service name, based on the deployment specification used"
	} , linkTests = {
			"ingress-nginx service"
	} , linkGetParams = {
			"kubernetesService=ingress-nginx"
	} )
	@GetMapping ( {
			"/kubernetes/{kubernetesService}/nodeport"
	} )
	public ObjectNode nodePort (
									@PathVariable String kubernetesService ,
									@RequestParam ( defaultValue = "false" ) boolean ssl )
		throws Exception {

		String location = csapApp.getKubernetesIntegration( ).nodePortUrl( csapApp, kubernetesService, "$host", "",
				ssl ) ;
		ObjectNode result = jacksonMapper.createObjectNode( ) ;
		result.put( "url", location ) ;
		return result ;

	}

	@CsapDoc ( notes = {
			"OS data collect by agent that is shared across all processes",
			"This includes host cpu (mpstat), open files(/proc/sys, lsof), sockets and many others",
			"param collectionInterval:  specifying '0' (or non existing) will return data for the shortest interval",
			"param numberOfDataPoints:  the amount of collected data points to return"
	} , linkTests = {
			"Host data"
	} , linkGetParams = {
			"collectionInterval=30,numberOfDataPoints=1"
	} )
	@GetMapping ( {
			"/collection/osShared/{collectionInterval}/{numberOfDataPoints}"
	} )
	public JsonNode collectionOsShared (
											@PathVariable ( "collectionInterval" ) String collectionInterval ,
											@PathVariable ( "numberOfDataPoints" ) int numberOfDataPoints )
		throws Exception {

		return getPerformanceData( "resource", collectionInterval, numberOfDataPoints ) ;

	}

	@CsapDoc ( notes = {
			"Default os data collected for service by agent.",
			"param serviceName:  specifying 'all' will return data for all services.",
			"param collectionInterval:  specifying '0' (or non existing) will return data for the shortest interval",
			"param numberOfDataPoints:  the amount of collected data points to return",
			"This includes top cpu, thread counts, disk usage, etc.",
			"Note: "
	} , linkTests = {
			"csap-agent OS Data",
			"csap-agent OS Data",
			"All Services OS Data"
	} , linkGetParams = {
			"serviceName=csap-agent,collectionInterval=30,numberOfDataPoints=1",
			"serviceName=csap-agent,collectionInterval=30,numberOfDataPoints=1",
			"serviceName=all,collectionInterval=30,numberOfDataPoints=1"
	} )
	@GetMapping ( {
			"/collection/os/{serviceName}/{collectionInterval}/{numberOfDataPoints}",
			"/collection/os/{serviceName}/{collectionInterval}/{numberOfDataPoints}"
	} )
	public JsonNode collectionOsProcess (
											@PathVariable ( "serviceName" ) String serviceName ,
											@PathVariable ( "collectionInterval" ) String collectionInterval ,
											@PathVariable ( "numberOfDataPoints" ) int numberOfDataPoints )
		throws Exception {

		return getPerformanceData( "service", collectionInterval, numberOfDataPoints, serviceName ) ;

	}

	@CsapDoc ( notes = {
			"Application data collected for service instance by agent.",
			"param serviceName:  specifying 'all' will return data for all services. port is required.",
			"param collectionInterval:  specifying '0' (or non existing) will return data for the shortest interval",
			"param numberOfDataPoints:  the amount of collected data points to return"
	} , linkTests = {
			"csap-agent Custom Data"
	} , linkGetParams = {
			"serviceName=csap-agent,collectionInterval=30,numberOfDataPoints=1",
	} )
	@GetMapping ( {
			"/collection/application/{serviceName}/{collectionInterval}/{numberOfDataPoints}"
	} )
	public JsonNode collectionApplication (
											@PathVariable ( "serviceName" ) String serviceNamePort ,
											@PathVariable ( "collectionInterval" ) String collectionInterval ,
											@PathVariable ( "numberOfDataPoints" ) int numberOfDataPoints ) {

		// return appData( serviceName, "app", interval, number );
		return getPerformanceData( "app", collectionInterval, numberOfDataPoints, serviceNamePort ) ;

	}

	@GetMapping ( {
			"/collection/application/raw"
	} )
	public JsonNode collectionAppRaw ( ) {

		var rawData = csapApp.metricManager( ).getServiceCollector( -1 ).getServiceToAppMetrics( ) ;

		return jacksonMapper.convertValue(
				rawData,
				ObjectNode.class ) ;

	}

	@CsapDoc ( notes = {
			"Default java data collected for service by agent. Specifying all will return data for all services.",
			"This includes heap, gc, threads, etc. If tomcat based, http sessions, requests, etc. are also included",
			"param serviceName:  specifying 'all' will return data for all services. port is required.",
			"param collectionInterval:  specifying '0' (or non existing) will return data for the shortest interval",
			"param numberOfDataPoints:  the amount of collected data points to return"
	} , linkTests = {
			"csap-agent Java Data",
			"All Java Data"
	} , linkGetParams = {
			"serviceName=csap-agent,collectionInterval=30,numberOfDataPoints=1",
			"serviceName=all,collectionInterval=30,numberOfDataPoints=1",
	} )
	@GetMapping ( {
			"/collection/java/{serviceName}/{collectionInterval}/{numberOfDataPoints}",
			"/collection/java/{serviceName}/{collectionInterval}/{numberOfDataPoints}"
	} )
	public JsonNode collectionJava (
										@PathVariable ( "serviceName" ) String serviceNamePort ,
										@PathVariable ( "collectionInterval" ) String collectionInterval ,
										@PathVariable ( "numberOfDataPoints" ) int numberOfDataPoints ) {

		return getPerformanceData( "jmx", collectionInterval, numberOfDataPoints, serviceNamePort ) ;

	}

	public JsonNode getPerformanceData ( String type , String interval , int number , String... services ) {

		ObjectNode metricsJson = jacksonMapper.createObjectNode( ) ;

		if ( csapApp.isAdminProfile( ) ) {

			metricsJson.put( "error",
					"Metrics api is only available from Node Agent instances. Vm Name: csap-agent" ) ;

		} else {

			switch ( type ) {

			case "resource":

				OsSharedResourcesCollector vmStatsRunnable = csapApp.metricManager( ).getOsSharedCollector( Integer
						.parseInt( interval ) ) ;
				metricsJson = vmStatsRunnable.buildCollectionReport( false, null, number, 0 ) ;
				break ;

			case "service":
				OsProcessCollector svcStats = csapApp.metricManager( ).getOsProcessCollector( Integer
						.parseInt( interval ) ) ;
				// metricsJson = svcStats.getAllCSVdata( number, 0 );
				metricsJson = svcStats.getCollection( number, 0, services ) ;
				break ;

			case "jmx":
				ServiceCollector serviceCollector = csapApp.metricManager( ).getServiceCollector( Integer.parseInt(
						interval ) ) ;
				metricsJson = serviceCollector.getJavaCollection( number, 0, services ) ;
				break ;

			case "app":

				// not working?
				ServiceCollector appCollector = csapApp.metricManager( ).getServiceCollector( Integer
						.parseInt( interval ) ) ;
				// metricsJson = appCollector.getAllCSVdata( number, 0 );
				metricsJson = appCollector.getApplicationCollection( number, 0, services ) ;
				break ;

			default:
				metricsJson.put( "error", "Unknown metric selection: " + type ) ;
				logger.error( "Unknown metric selection: " + type ) ;
				break ;

			}

		}

		return metricsJson ;

	}

	public static final String RUN_SCRIPT_URL = "/script" ;

	@CsapDoc ( notes = {
			RUN_SCRIPT_URL + ": api for running script",
			"scriptName:  name of script to be run.",
			"scriptUserid: OS user used for script execution",
			"timeoutSeconds: amount of time before script will be aborted/killed",
			"scriptContents: actual script to be run"
	} , linkTests = {
			"Run shell script"
	} , linkPaths = {
			RUN_SCRIPT_URL
	} , linkPostParams = {
			USERID_PASS_PARAMS
					+ "scriptUserid=csapUser,timeoutSeconds=30,scriptName=MyTest.sh,scriptContents=#!/bin/bash\nls -l"
	} )
	@PostMapping ( RUN_SCRIPT_URL )
	public ObjectNode scriptRun (
									@RequestParam String scriptName ,
									@RequestParam int timeoutSeconds ,
									@RequestParam String scriptUserid ,
									@RequestParam String scriptContents ,
									@RequestParam ( SpringAuthCachingFilter.USERID ) String apiUserid ,
									@RequestParam ( SpringAuthCachingFilter.PASSWORD ) String apiPass ,
									HttpServletRequest request )
		throws JsonGenerationException ,
		JsonMappingException ,
		IOException {

		logger.info( "apiUserid: {}, scriptName: {},  scriptUserid: {} ",
				apiUserid, scriptName, scriptUserid ) ;

		String[] hosts = {
				csapApp.getCsapHostName( )
		} ;
		String fullName = scriptName + LocalDateTime.now( ).format( DateTimeFormatter.ofPattern( "MMM.d-HH.mm" ) ) ;
		OutputFileMgr outputFm = new OutputFileMgr( csapApp.getScriptDir( ), fullName ) ;

		ObjectNode apiResponse = jacksonMapper.createObjectNode( ) ;
		apiResponse.put( "scriptOutput", csapApp.getScriptDir( ).getAbsolutePath( ) +
				"/xfer_" + scriptName + ".log" ) ;

		ObjectNode runResponse = osManager.executeShellScriptClustered(
				apiUserid,
				timeoutSeconds, scriptContents, scriptUserid,
				hosts,
				scriptName, outputFm ) ;
		outputFm.opCompleted( ) ;

		apiResponse.set( "result", runResponse ) ;

		ObjectNode securityResponse = (ObjectNode) request
				.getAttribute( SpringAuthCachingFilter.SEC_RESPONSE_ATTRIBUTE ) ;
		apiResponse.set( "security", securityResponse ) ;

		return apiResponse ;

	}

	@Inject
	ServiceCommands serviceCommands ;

	public static final String USERID_PASS_PARAMS = "userid=user-id,pass=CHANGEME," ;

	public static final String KILL_SERVICES_URL = "/service/kill" ;

	@CsapDoc ( notes = {
			KILL_SERVICES_URL + ": api for stoping specified service",
			"param services:  1 or more service port is required.",
			"Parameter: clean - optional - omit or leave blank to not delete files"
	} , linkTests = {
			"csap-verify-service"
	} , linkPaths = {
			KILL_SERVICES_URL
	} , linkPostParams = {
			USERID_PASS_PARAMS
					+ "clean=clean,keepLogs=keepLogs,services=csap-verify-service_7011,auditUserid=blank"
	} )
	@PostMapping ( KILL_SERVICES_URL )
	public ObjectNode serviceKill (
									@RequestParam ArrayList<String> services ,
									@RequestParam ( required = false ) String clean ,
									@RequestParam ( required = false ) String keepLogs ,
									@RequestParam ( SpringAuthCachingFilter.USERID ) String apiUserid ,
									@RequestParam ( defaultValue = "" ) String auditUserid ,
									HttpServletRequest request )
		throws JsonGenerationException ,
		JsonMappingException ,
		IOException {

		logger.info( "auditUserid: {},  apiUserid: {}, services: {} clean: {}, keepLogs: {} ",
				auditUserid, apiUserid, services, clean, keepLogs ) ;

		ObjectNode apiResponse = serviceCommands.killRequest( apiUserid, services, clean, keepLogs, auditUserid ) ;

		ObjectNode securityResponse = (ObjectNode) request
				.getAttribute( SpringAuthCachingFilter.SEC_RESPONSE_ATTRIBUTE ) ;
		apiResponse.set( "security", securityResponse ) ;

		return apiResponse ;

	}

	public static final String RUN_SERVICE_JOB = "/service/job" ;

	@CsapDoc ( notes = {
			RUN_SERVICE_JOB + ": api for running service job"
	} , linkTests = {
			"run job"
	} , linkPaths = {
			RUN_SERVICE_JOB
	} , linkPostParams = {
			USERID_PASS_PARAMS
					+ "auditUserid=blank"
	} )
	@PostMapping ( RUN_SERVICE_JOB )
	public ObjectNode runServiceJob (
										@RequestParam String service ,
										@RequestParam String job ,
										@RequestParam String jobParameters ,
										@RequestParam ( SpringAuthCachingFilter.USERID ) String apiUserid ,
										@RequestParam ( defaultValue = "" ) String auditUserid ,
										HttpServletRequest request )
		throws JsonGenerationException ,
		JsonMappingException ,
		IOException {

		logger.info( "auditUserid: {},  apiUserid:{} " ) ;

		return serviceCommands.runJob( service, job, jobParameters, auditUserid, apiUserid ) ;

	}

	public static final String CLEAR_DEPLOYMENTS_URL = "/deployments/clear" ;

	@CsapDoc ( notes = {
			CLEAR_DEPLOYMENTS_URL + ": api for clearing deployments queued"
	} , linkTests = {
			"clear"
	} , linkPaths = {
			PAUSE_DEPLOYMENTS_URL
	} , linkPostParams = {
			USERID_PASS_PARAMS
					+ "auditUserid=blank"
	} )
	@PostMapping ( CLEAR_DEPLOYMENTS_URL )
	public ObjectNode deploymentsClear (
											@RequestParam ( SpringAuthCachingFilter.USERID ) String apiUserid ,
											@RequestParam ( defaultValue = "" ) String auditUserid ,
											HttpServletRequest request )
		throws JsonGenerationException ,
		JsonMappingException ,
		IOException {

		logger.info( "auditUserid: {},  apiUserid:{} " ) ;

		return serviceCommands.deleteDeployments( auditUserid, apiUserid ) ;

	}

	public static final String PAUSE_DEPLOYMENTS_URL = "/deployments/pause" ;

	@CsapDoc ( notes = {
			PAUSE_DEPLOYMENTS_URL + ": api for pausing deployments"
	} , linkTests = {
			"pause"
	} , linkPaths = {
			PAUSE_DEPLOYMENTS_URL
	} , linkPostParams = {
			USERID_PASS_PARAMS
					+ "auditUserid=blank"
	} )
	@PostMapping ( PAUSE_DEPLOYMENTS_URL )
	public ObjectNode deploymentsPause (
											@RequestParam ( SpringAuthCachingFilter.USERID ) String apiUserid ,
											@RequestParam ( defaultValue = "" ) String auditUserid ,
											HttpServletRequest request )
		throws JsonGenerationException ,
		JsonMappingException ,
		IOException {

		logger.info( "auditUserid: {},  apiUserid:{} " ) ;

		return serviceCommands.toggleDeploymentProcessing( auditUserid, apiUserid ) ;

	}

	public static final String START_SERVICES_URL = "/service/start" ;

	@CsapDoc ( notes = {
			START_SERVICES_URL + ": api for starting specified service",
			"parameter:  services:  1 or more service port is required.",
			"optional: deployId -  start will only be issued IF deployment id specified is successful. If not specified start will be issued.",
			"optional: clean  - omit or leave blank to not delete files"
	} , linkTests = {
			"csap-verify-service"
	} , linkPaths = {
			START_SERVICES_URL
	} , linkPostParams = {
			USERID_PASS_PARAMS
					+ "commandArguments=blank,services=csap-verify-service_7011,auditUserid=blank,startClean=blank,startNoDeploy=blank,hotDeploy=blank,deployId=blank"
	} )
	@PostMapping ( START_SERVICES_URL )
	public ObjectNode serviceStart (
										@RequestParam ArrayList<String> services ,

										@RequestParam ( required = false ) String commandArguments ,
										@RequestParam ( required = false ) String hotDeploy ,
										@RequestParam ( required = false ) String startClean ,
										@RequestParam ( required = false ) String noDeploy ,
										String deployId ,

										@RequestParam ( SpringAuthCachingFilter.USERID ) String apiUserid ,
										@RequestParam ( defaultValue = "" ) String auditUserid ,
										HttpServletRequest request )
		throws JsonGenerationException ,
		JsonMappingException ,
		IOException {

		logger.info(
				"auditUserid: {},  apiUserid:{} , services: {} commandArguments: {}, hotDeploy: {}, startClean: {}, startNoDeploy: {} ",
				auditUserid, apiUserid, services, commandArguments, hotDeploy, startClean, noDeploy ) ;

		return serviceCommands.startRequest( apiUserid, services, commandArguments, hotDeploy, startClean, noDeploy,
				auditUserid, deployId ) ;

	}

	public static final String STOP_SERVICES_URL = "/service/stop" ;

	@CsapDoc ( notes = {
			STOP_SERVICES_URL + ": api for synchronous stop of specified service",
			"Note: unlike other operations, stop is synchronous and only valid for csap-api and spring boot services"
	} , linkTests = {
			"csap-verify-service"
	} , linkPaths = {
			STOP_SERVICES_URL
	} , linkPostParams = {
			USERID_PASS_PARAMS
					+ "commandArguments=blank,services=csap-verify-service_7011,auditUserid=blank,startClean=blank,startNoDeploy=blank,hotDeploy=blank,deployId=blank"
	} )
	@PostMapping ( STOP_SERVICES_URL )
	public ObjectNode serviceStop (
									@RequestParam ArrayList<String> services ,
									String requestUser ,

									@RequestParam ( SpringAuthCachingFilter.USERID ) String apiUserid ,
									@RequestParam ( defaultValue = "" ) String auditUserid ,
									HttpServletRequest request )
		throws JsonGenerationException ,
		JsonMappingException ,
		IOException {

		logger.info(
				"auditUserid: {},  apiUserid:{} , services: {} ",
				auditUserid, apiUserid, services ) ;

		return serviceCommands.stopRequest( apiUserid, services, auditUserid ) ;

//		var stopResults =  serviceManager.stopService( serviceName, requestUser ) ;
//		
//		var results = jacksonMapper.createObjectNode() ;
//		
//		results.put( "results", stopResults) ;
//		
//		return results; 

	}

	public static final String DEPLOY_SERVICES_URL = "/service/deploy" ;

	@CsapDoc ( notes = {
			DEPLOY_SERVICES_URL + ": api for deploying specified services",
			"param: services - 1 or more service port is required.",
			"optional: mavenDeployArtifact:  the artifact to deploy from maven - if ommited then source build is done."
					+ " specify 'default' to use, the version in definition file",
			"optional: targetScpHosts - after deploying artifact, it will be copied to specified hosts",
			"optional: commandArguments - overide the definition parameters for heap size, etc.",
			"optional: deployId - unique ID to identify results of deploy."
	} , linkTests = {
			"csap-verify-service"
	} , linkPaths = {
			DEPLOY_SERVICES_URL
	} , linkPostParams = {
			USERID_PASS_PARAMS
					+ "commandArguments=blank,services=csap-verify-service_7011,auditUserid=blank,hotDeploy=blank,"
					+ "scmUserid=user-ideloper,scmBranch=trunk,scmPass=CHANGEME,"
					+ "mavenDeployArtifact=default,deployId=blank"
	} )
	@PostMapping ( DEPLOY_SERVICES_URL )
	public ObjectNode serviceDeploy (
										@RequestParam ArrayList<String> services ,

										// required parameters
										@RequestParam ( defaultValue = "dummy" ) String scmUserid ,
										@RequestParam ( defaultValue = "dummy" ) String scmPass ,
										@RequestParam ( defaultValue = "dummy" ) String scmBranch ,

										String primaryHost ,
										String deployId ,
										String commandArguments ,
										String targetScpHosts ,
										String hotDeploy ,
										String mavenDeployArtifact , // if null -
																		// then
																		// source
																		// build
										String scmCommand ,
										@RequestParam ( required = false , defaultValue = "true" ) boolean doEncrypt ,

										@RequestParam ( SpringAuthCachingFilter.USERID ) String apiUserid ,
										@RequestParam ( defaultValue = "" ) String auditUserid ,
										HttpServletRequest request )
		throws JsonGenerationException ,
		JsonMappingException ,
		IOException {

		logger.info(
				"auditUserid: {},  apiUserid:{} , primaryHost:{} , services: {} javaOpts: {},  hotDeploy: {},"
						+ " scmUserid: {}, scmBranch: {}, mavenDeployArtifact: {}, deployId: {} "
						+ "\n targetScpHosts: {} ",
				auditUserid, apiUserid, primaryHost, services, commandArguments, hotDeploy,
				scmUserid, scmBranch, mavenDeployArtifact, deployId,
				targetScpHosts ) ;
		String sourcePassword = encryptor.encrypt( scmPass ) ; // immediately
		// encrypt
		// pass

		if ( ! doEncrypt ||
				( csapApp.isRunningOnDesktop( ) && csapApp.isAdminProfile( ) ) ) {

			sourcePassword = scmPass ;

		}

		return serviceCommands.deployRequest(
				apiUserid, primaryHost, deployId,
				services, scmUserid, sourcePassword, scmBranch,
				commandArguments, hotDeploy,
				mavenDeployArtifact, scmCommand, targetScpHosts, auditUserid ) ;

	}

	@Inject
	private StandardPBEStringEncryptor encryptor ;

	/**
	 * 
	 * 
	 * 
	 * 
	 * 
	 * 
	 * 
	 * 
	 * 
	 * 
	 * LOGS
	 * 
	 * 
	 * 
	 * 
	 * 
	 * 
	 * 
	 * 
	 */

	private ObjectNode getAllServices ( ) {

		ObjectNode response = jacksonMapper.createObjectNode( ) ;

		response.put( "info", "add service name to url" ) ;
		response.set( "availableServices", jacksonMapper.convertValue(
				csapApp.getAllPackages( ).getServiceNamesInLifecycle( ),
				ArrayNode.class ) ) ;
		return response ;

	}

	@CsapDoc ( notes = {
			"List of log files",
			"Note: agent api only."
	} , linkTests = {
			"csap-agent Listing"
	} , linkPostParams = {
			USERID_PASS_PARAMS
					+ "serviceName_port=csap-agent"
	} )
	@PostMapping ( "/service/log/list" )
	public JsonNode service_log_list (
										@RequestParam ( "serviceName_port" ) String serviceName_port ,
										@RequestParam ( SpringAuthCachingFilter.USERID ) String userid ,
										@RequestParam ( SpringAuthCachingFilter.PASSWORD ) String inputPass ) {

		if ( serviceName_port.equals( "{serviceName_port}" ) ) {

			return getAllServices( ) ;

		}

		logger.info( "{} listing files on: ", userid, serviceName_port ) ;

		ArrayList<String> names = new ArrayList<String>( ) ;

		File working = csapApp.getLogDir( serviceName_port ) ;

		if ( working != null && working.exists( ) ) {

			File[] logsFiles = working.listFiles( ) ;

			for ( File logItem : logsFiles ) {

				if ( logItem.isDirectory( ) ) {

					File[] subFiles = logItem.listFiles( ) ;

					for ( File subFile : subFiles ) {

						if ( subFile.isFile( ) ) {

							// Hook since tomcat chokes on urlencoded /
							String path = logItem.getName( ) + "_slash_" + subFile.getName( ) ;
							names.add( path ) ;

						}

					}

				} else {

					names.add( logItem.getName( ) ) ;

				}

			}

		}

		return jacksonMapper.convertValue( names, ArrayNode.class ) ;

	}

	final public static String LOG_CHANGES = "/service/log/changes" ;

	@CsapDoc ( notes = {
			"Download service log file changes"
	} , linkTests = {
			"csap-agent Warnings"
	} , linkPostParams = {
			USERID_PASS_PARAMS
					+ "serviceName_port=csap-agent,fileName=warnings.log"
	} )
	@PostMapping ( LOG_CHANGES )
	public JsonNode logFileChanges (
										@RequestParam ( value = "fromFolder" , required = false ) String fromFolder ,
										@RequestParam ( defaultValue = "0" ) int dockerLineCount ,
										@RequestParam ( defaultValue = "0" ) String dockerSince ,
										@RequestParam ( value = "bufferSize" , required = true ) long bufferSize ,
										@RequestParam ( value = CsapCore.HOST_PARAM , required = false ) String hostName ,
										@RequestParam ( value = CsapCore.SERVICE_NOPORT_PARAM , required = false ) String serviceName ,
										@RequestParam ( value = "isLogFile" , required = false , defaultValue = "false" ) boolean isLogFile ,
										@RequestParam ( value = FileRequests.LOG_FILE_OFFSET_PARAM , required = false , defaultValue = "-1" ) long offsetLong ,
										String apiUser ,
										@RequestParam ( SpringAuthCachingFilter.USERID ) String userid ,
										@RequestParam ( SpringAuthCachingFilter.PASSWORD ) String inputPass ,
										HttpServletResponse response )
		throws Exception {

		logger.debug( "{} Downloading {} file: {}", userid, serviceName, fromFolder ) ;

		if ( serviceName.indexOf( "_" ) != -1 ) {

			serviceName = serviceName.substring( 0, serviceName.indexOf( "_" ) ) ;

		}

		// long bufferSize = 100 * 1024 ;

		var changes = fileRequests.getFileChanges(
				fromFolder, dockerLineCount, dockerSince,
				bufferSize, hostName, serviceName,
				isLogFile, offsetLong,
				false, apiUser,
				null, null ) ;

		return changes ;

	}

	@Autowired ( required = false )
	FileRequests fileRequests ;

	@CsapDoc ( notes = {
			"Download service log file.",
			"Note: agent api only."
	} , linkTests = {
			"csap-agent Warnings"
	} , linkPostParams = {
			USERID_PASS_PARAMS
					+ "serviceName_port=csap-agent,fileName=warnings.log"
	} )
	@PostMapping ( value = "/service/log/download" , produces = MediaType.APPLICATION_OCTET_STREAM_VALUE )
	public FileSystemResource downloadLogFile (
												@RequestParam ( "serviceName" ) String serviceName ,
												@RequestParam ( "fileName" ) String fileName ,
												@RequestParam ( SpringAuthCachingFilter.USERID ) String userid ,
												@RequestParam ( SpringAuthCachingFilter.PASSWORD ) String inputPass ,
												HttpServletResponse response ) {

		logger.info( "{} Downloading {} file: {}", userid, serviceName, fileName ) ;

		var serviceInstance = csapApp.flexFindFirstInstanceCurrentHost( serviceName ) ;
		FileSystemResource theFile ;

		if ( serviceInstance != null ) {

			// Hook since tomcat chokes on urlencoded /
			File logFileRequested = new File( serviceInstance.getLogWorkingDirectory( ), fileName.replaceAll( "_slash_",
					"/" ) ) ;

			if ( ! logFileRequested.exists( ) ) {

				throw new RuntimeException( "File not found" + logFileRequested.getAbsolutePath( ) ) ;

			}
			// HttpServletResponse response
			// String mt = new
			// MimetypesFileTypeMap().getContentType(logFileRequested);

			// logger.info("File type: {}" , mt) ;
			response.setHeader( "Content-Disposition", "attachment;filename=" + fileName ) ;
			theFile = new FileSystemResource( logFileRequested ) ;

		} else {

			logger.warn( "Failed to find service: {}", serviceName ) ;
			throw new RuntimeException( "serviceName not found" + serviceName ) ;

		}

		return theFile ;

	}

	@CsapDoc ( notes = {
			"Log File output filtered by specified filter, processed using grep command",
			"Note: agent api only"
	} , linkTests = {
			CsapCore.AGENT_ID
	} , linkPostParams = {
			USERID_PASS_PARAMS
					+ "serviceName_port=csap-agent,fileName=warnings.log,filter=Error"
	} )
	@PostMapping ( value = "/service/log/filter" , produces = MediaType.TEXT_PLAIN_VALUE )
	public String service_log_filter (
										@RequestParam ( "serviceName_port" ) String serviceName_port ,
										@RequestParam ( "fileName" ) String fileName ,
										@RequestParam ( value = "filter" ) String filter ,
										@RequestParam ( SpringAuthCachingFilter.USERID ) String userid ,
										@RequestParam ( SpringAuthCachingFilter.PASSWORD ) String inputPass )
		throws IOException {

		logger.info( "{} Filtering {} file: {} with: {}", userid, serviceName_port, fileName, filter ) ;

		StringBuilder results = new StringBuilder( "Filter: " + filter + "\n\n" ) ;
		// Hook since tomcat chokes on urlencoded /
		File logFileRequested = new File( csapApp.getLogDir( serviceName_port ) + "/"
				+ fileName.replaceAll( "_slash_", "/" ) ) ;

		if ( ! logFileRequested.exists( ) ) {

			throw new RuntimeException( "File not found" + logFileRequested.getAbsolutePath( ) ) ;

		}

		List<String> parmList = Arrays.asList( "bash", "-c",
				"grep -i '" + filter + "' " + logFileRequested.getAbsolutePath( ) ) ;
		// osCommandRunner.executeString(parmList);

		OsCommandRunner osCommandRunner = new OsCommandRunner( 10, 3, "Api" ) ;

		results.append( osCommandRunner
				.executeString( parmList, csapApp.getCsapInstallFolder( ),
						null, null, 20, 2, null ) ) ;

		logger.debug( "Result: {}", results ) ;

		return results.toString( ) ;

	}

	@CsapDoc ( notes = {
			"/service/file/download - download files from the service folder in $PROCESSING",
			"Note: agent api only."
	} , linkTests = {
			"csap-agent start file"
	} , linkPaths = {
			"/service/file/download"
	} , linkPostParams = {
			USERID_PASS_PARAMS
					+ "serviceName_port=csap-agent,fileName=csap-agent_start.log"
	} )
	@PostMapping ( value = "/service/file/download" , produces = MediaType.APPLICATION_OCTET_STREAM_VALUE )
	public FileSystemResource downloadServiceFile (
													@RequestParam ( "serviceName" ) String serviceName ,
													@RequestParam ( "fileName" ) String fileName ,
													@RequestParam ( SpringAuthCachingFilter.USERID ) String userid ,
													@RequestParam ( SpringAuthCachingFilter.PASSWORD ) String inputPass ,
													HttpServletResponse response ) {

		logger.info( "{} Downloading {} file: {}", userid, serviceName, fileName ) ;

		// Hook since tomcat chokes on urlencoded /
		File serviceFile = new File( csapApp.getCsapWorkingSubFolder( serviceName ) + "/"
				+ fileName.replaceAll( "_slash_", "/" ) ) ;

		if ( ! serviceFile.exists( ) ) {

			throw new RuntimeException( "File not found: " + serviceFile.getAbsolutePath( ) ) ;

		}

		response.setHeader( "Content-Disposition", "attachment;filename=" + fileName ) ;
		FileSystemResource theFile = new FileSystemResource( serviceFile ) ;

		return theFile ;

	}

	@Inject
	OsManager osManger ;

	public static final String PROGRESS_PREFIX = "xfer_" ;
	public static final String PLATFORM_UPDATE = "/platformUpdate" ;

	@CsapDoc ( notes = {
			PLATFORM_UPDATE
					+ ": upload a .tgz to the file system, with support for extraction, and optional running scripts",
			"extractDir: location to place uploaded archive, ",
			"distFile: multi-part attachment containing a *.tgz archive file (tar gzipped) ",
			"Optional deleteExisting: defaults false. If true - existing file will be removed",
	} , linkTests = {
			"platform update"
	} , linkPostParams = {
			USERID_PASS_PARAMS
					+ "extractDir=$STAGING/temp,deleteExisting=false,timeoutSeconds=120"
					+ "chownUserid=csapUser,"
	} , fileParams = {
			"distFile"
	} )

	@PostMapping ( PLATFORM_UPDATE )
	public ObjectNode platformUpdate (
										@RequestParam ( SpringAuthCachingFilter.USERID ) String userid ,
										@RequestParam ( SpringAuthCachingFilter.PASSWORD ) String inputPass ,

										@RequestParam ( value = "extractDir" , required = true ) String extractToken ,
										@RequestParam ( value = "chownUserid" , required = false ) String chownUserid ,
										@RequestParam ( value = "timeoutSeconds" , required = false , defaultValue = "120" ) int timeoutSeconds ,
										@RequestParam ( value = "deleteExisting" , required = false ) String deleteExisting ,
										@RequestParam ( value = "auditUser" , required = false ) String auditUser ,
										@RequestParam ( value = "distFile" , required = true ) MultipartFile multiPartFile ,
										HttpServletRequest request ) {

		if ( auditUser == null ) {

			auditUser = userid ;

		}

		String extractDir = extractToken ;

		if ( extractDir.startsWith( Application.FileToken.PLATFORM.value ) ) {

			// STAGING_TOKEN is used as csap install folder might be different;
			// full paths are not passed when syncing elements between hosts.
			extractDir = extractDir.replaceAll(
					Application.FileToken.PLATFORM.value,
					csapApp.getInstallationFolderAsString( ) ) ;

		}

		String desc = multiPartFile.toString( ) ;

		if ( multiPartFile != null ) {

			desc = multiPartFile.getOriginalFilename( ) + " size: " + multiPartFile.getSize( ) ;

		}

		var details = "File System being updated"
				+ CSAP.padLine( "file" ) + desc
				+ CSAP.padLine( "extractToken" ) + extractToken
				+ CSAP.padLine( "extractDir" ) + extractDir
				+ CSAP.padLine( "chownUserid" ) + chownUserid
				+ CSAP.padLine( "timeoutSeconds" ) + timeoutSeconds
				+ CSAP.padLine( "deleteExisting" ) + deleteExisting ;
		logger.info( details ) ;

		csapEventClient.publishUserEvent(
				CsapEvents.CSAP_SYSTEM_CATEGORY + PLATFORM_UPDATE, auditUser,
				"File System Update", details ) ;

		ObjectNode jsonObjectResponse = jacksonMapper.createObjectNode( ) ;
		jsonObjectResponse.put( "host", csapApp.getCsapHostName( ) ) ;

		ArrayNode coreArray = jsonObjectResponse.putArray( CORE_RESULTS ) ;

		String servletRemoteHost = request.getRemoteHost( ) ;

		String coreResults = "Error" ;

		try {

			var outputFileManager = new OutputFileMgr( csapApp.getScriptDir( ), userid + "_platformUpdate" ) ;
			coreResults = osManger.updatePlatformCore( multiPartFile, extractDir, false,
					servletRemoteHost,
					chownUserid,
					auditUser,
					deleteExisting,
					outputFileManager ) ;
			outputFileManager.close( ) ;

		} catch ( Exception e1 ) {

			logger.error( "Failed updating Platform core", e1 ) ;
			coreArray.add( "**" + CsapCore.CONFIG_PARSE_ERROR + " Host "
					+ csapApp.getCsapHostName( )
					+ " failed updating core: "
					+ e1.getMessage( ) ) ;

			return jsonObjectResponse ;

		}

		coreArray.add( coreResults ) ;

		var scriptPathAllOs = csapApp.stripWindowsPaths( csapApp.getScriptDir( ).getAbsolutePath( ) ) ;

		logger.debug( "extractDir: {} \n scriptPathAllOs: {}", extractDir, scriptPathAllOs ) ;

		if ( extractDir.equals( csapApp.getDefinitionFolder( )
				.getAbsolutePath( ) ) ) {

			coreArray.add( "Triggering Reload" ) ;
			logger.info( " Extract to cluster definition detected,  triggering a reload " ) ;
			csapApp.run_application_scan( ) ;

		} else if ( extractDir.startsWith( scriptPathAllOs ) ) {

			// Special hook for execution of scripts
			String scriptName = multiPartFile.getOriginalFilename( ) ;

			if ( scriptName.endsWith( ".zip" ) || scriptName.endsWith( ".tgz" ) ) {

				scriptName = scriptName.substring( 0, scriptName.length( ) - 4 ) ;

			}

			String scriptResults = "" ;

			if ( scriptName.endsWith( OsCommandRunner.CANCEL_SUFFIX ) ) {

				scriptResults = CsapApplication.header( "Cancel Received: " + scriptName
						+ "\n\t Output will be displayed after all VMs have cancelled the command." ) ;

				logger.warn( CsapApplication.header( "results: {}" ), scriptResults ) ;

			} else {

				try {

					var scriptToRun = new File( extractDir, scriptName ) ;
					logger.info( "Executabled script: {}", scriptToRun.getAbsolutePath( ) ) ;

					Map<String, String> environmentVariables = null ;

					if ( csapApp.rootProjectEnvSettings( ).isVsphereConfigured( ) ) {

						environmentVariables = csapApp.environmentSettings( ).getVsphereEnv( ) ;

					}

					var scriptOutput = new OutputFileMgr( csapApp.getScriptDir( ), PROGRESS_PREFIX
							+ scriptName ) ;

					if ( csapApp.isJunit( ) ) {

						logger.info( CsapApplication.testHeader( "junit: skipping FS script run" ) ) ;
						scriptResults = "junit - skipping script execute " + scriptToRun ;

					} else {

						scriptResults = OsCommandRunner.runCancellable(
								timeoutSeconds,
								chownUserid,
								scriptToRun,
								scriptOutput,
								csapApp.getAgentRunUser( ),
								environmentVariables ) ;

					}

					scriptOutput.close( ) ;

					if ( csapApp.isDesktopHost( ) ) {

						scriptResults += "\n\n sample-script-output.txt \n\n" + csapApp.check_for_stub( "",
								"linux/sample-script-output.txt" ) ;

					}

				} catch ( IOException e ) {

					logger.error( "Failed script run {}", CSAP.buildCsapStack( e ) ) ;

				}

				int maxReturned = 1024 * 1024 * 3 ; // max 3mb

				if ( scriptResults.length( ) > maxReturned ) {

					String header = CsapCore.CONFIG_PARSE_WARN
							+ " Output was truncated; click on download button to view full output\n\n" ;

					scriptResults = header + scriptResults.substring( scriptResults.length( ) - maxReturned ) ;

				}

			}

			ArrayNode res = jsonObjectResponse.putArray( "scriptResults" ) ;
			res.add( scriptResults ) ;

		}

		return jsonObjectResponse ;

	}

	@Inject
	ServiceOsManager serviceManager ;

	@Inject
	CsapEvents csapEventClient ;

	@CsapDoc ( notes = {
			"/upload/file: upload a file to the file system",
			"Note: agent api only, password should always be encypted using CSAP encoder UI.",
			"Optional Param: otherHosts - defaults to none. If specified file will be synched to other hosts (along with version if specified)",
			"Optional Param: uploadLocation - defaults to $STAGING/temp. $DEPLOY_FOLDER, $PROCESSING and $STAGING variables supported",
			"Optional Param: overwrite - defaults false. If true - existing file will be overwritten",
	} , linkTests = {
			"Sample Upload"
	} , linkPostParams = {
			USERID_PASS_PARAMS
					+ "uploadLocation=$STAGING/temp,overWrite=false,"
					+ "otherHosts=blank,"
	} , fileParams = {
			"uploadFile"
	} )

	@PostMapping ( value = "/upload/file" )
	public ObjectNode uploadFileToFileSystem (
												@RequestParam ( "uploadFile" ) MultipartFile multiPartFile ,
												@RequestParam ( value = "uploadLocation" , required = false , defaultValue = "$DEPLOY_FOLDER" ) String uploadLocation ,
												@RequestParam ( value = "overWrite" , required = false , defaultValue = "false" ) boolean overWrite ,
												@RequestParam ( value = "otherHosts" , required = false ) String[] otherHosts ,
												@RequestParam ( SpringAuthCachingFilter.USERID ) String userid ,
												@RequestParam ( SpringAuthCachingFilter.PASSWORD ) String inputPass ,
												HttpServletRequest request )
		throws IOException {

		ObjectNode resultJson = (ObjectNode) request
				.getAttribute( SpringAuthCachingFilter.SEC_RESPONSE_ATTRIBUTE ) ;

		resultJson.put( "uploadFile", multiPartFile.getOriginalFilename( ) ) ;
		resultJson.put( "uploadFileSize", multiPartFile.getSize( ) ) ;
		resultJson.put( "uploadLocation", uploadLocation ) ;
		resultJson.put( "overWrite", overWrite ) ;

		uploadLocation = replaceVariables( uploadLocation ) ;
		String artifactName = multiPartFile.getOriginalFilename( ) ;
		csapEventClient.publishUserEvent( CsapEvents.CSAP_SYSTEM_CATEGORY + "/fileUpload",
				userid, "API: file upload", "location: " + uploadLocation ) ;

		resultJson.put( "uploadLocationResolved", uploadLocation ) ;

		try {

			if ( multiPartFile.getSize( ) == 0 || multiPartFile.getOriginalFilename( ).length( ) == 0 ) {

				throw new IOException( "Invalid parameters. Verify name and size of attachment" ) ;

			}

			File targetDir = new File( uploadLocation ) ;

			if ( ! targetDir.exists( ) ) {

				targetDir.mkdirs( ) ;

			}

			File uploadFile = new File( targetDir, artifactName ) ;

			if ( uploadFile.exists( ) ) {

				if ( ! overWrite ) {

					throw new IOException(
							"target location contains an existing file of the same name. Rename or set overwrite to true" ) ;

				} else {

					resultJson.put( "warning", "Existing file is being overwritten" ) ;

				}

			}

			multiPartFile.transferTo( uploadFile ) ;

			resultJson.put( "savedTo", uploadFile.getAbsolutePath( ) ) ;
			resultJson.put( "uploadResult", true ) ;

			if ( otherHosts != null && otherHosts.length > 0 ) {

				List<String> hostsToCopyTo = new ArrayList<String>( Arrays.asList( otherHosts ) ) ;
				String syncResult = serviceManager.syncToOtherHosts(
						userid,
						hostsToCopyTo,
						uploadFile.getAbsolutePath( ),
						uploadFile.getParentFile( ).getAbsolutePath( ),
						csapApp.getAgentRunUser( ), userid,
						null ) ;
				resultJson.put( "syncResult", syncResult ) ;

			}

			resultJson.put( "success", true ) ;

		} catch ( Exception e ) {

			resultJson.put( "success", false ) ;
			resultJson.put( "errorMessage", e.getMessage( ) ) ;

			logger.warn( CSAP.buildCsapStack( e ) ) ;
			logger.debug( "Full exception", e ) ;

		}

		return resultJson ;

	}

	private String replaceVariables ( String uploadLocation )
		throws IOException {

		if ( uploadLocation.contains( "$STAGING" ) ) {

			uploadLocation = uploadLocation.replace( "$STAGING", csapApp.getCsapInstallFolder( ).getCanonicalPath( ) ) ;

		}

		if ( uploadLocation.contains( "$PROCESSING" ) ) {

			uploadLocation = uploadLocation.replace( "$PROCESSING", csapApp.getCsapWorkingFolder( )
					.getCanonicalPath( ) ) ;

		}

		if ( uploadLocation.contains( "$DEPLOY_FOLDER" ) ) {

			uploadLocation = uploadLocation.replace( "$DEPLOY_FOLDER", csapApp.getCsapPackageFolder( )
					.getCanonicalPath( ) ) ;

		}

		return uploadLocation ;

	}

	@CsapDoc ( notes = {
			"/upload/service: upload a service artifact to the file system, and start.",
			"Note: agent api only, password should always be encypted using CSAP encoder UI.",
			"Required Param: serviceName - uploadFile will be renamed to be serviceName, location set to $DEPLOY_FOLDER",
			"Required Param: version - if specified,  csap version file will be created in the upload location",
			"Optional Param: serviceStart - defaults true. Set to false to skip service start. Service folder is deleted"
					+ " - use other apis if needed to backup logs or skip working folder clean"
	} , linkTests = {
			"Sample Upload"
	} , linkPostParams = {
			USERID_PASS_PARAMS
					+ "serviceName=csap-verify-service_7011,version=1.2.3-test,serviceStart=true"
	} , fileParams = {
			"uploadFile"
	} )
	@PostMapping ( value = "/upload/service" )
	public ObjectNode uploadServiceToFileSystem (
													@RequestParam ( "uploadFile" ) MultipartFile multiPartFile ,
													@RequestParam ( "serviceName" ) String serviceName_port ,
													@RequestParam ( "version" ) String version ,
													@RequestParam ( value = "serviceStart" , required = false , defaultValue = "true" ) boolean serviceStart ,
													@RequestParam ( SpringAuthCachingFilter.USERID ) String userid ,
													@RequestParam ( SpringAuthCachingFilter.PASSWORD ) String inputPass ,
													HttpServletRequest request )
		throws IOException {

		ObjectNode resultJson = (ObjectNode) request
				.getAttribute( SpringAuthCachingFilter.SEC_RESPONSE_ATTRIBUTE ) ;

		resultJson.put( "uploadFile", multiPartFile.getOriginalFilename( ) ) ;
		resultJson.put( "uploadFileSize", multiPartFile.getSize( ) ) ;
		resultJson.put( "version", version ) ;

		try {

			String uploadLocation = replaceVariables( "$DEPLOY_FOLDER" ) ;

			ServiceInstance serviceInstance = findServiceOnCurrentHost( serviceName_port ) ;

			String checkVersion = version.replaceAll( "-", "1" ).replaceAll( ".", "1" ) ;

			if ( ! StringUtils.isAlphanumeric( checkVersion ) ) {

				throw new IOException( "Invalid version specified: " + checkVersion ) ;

			}

			uploadLocation = uploadLocation.replace( "$DEPLOY_FOLDER", csapApp.getCsapPackageFolder( )
					.getCanonicalPath( ) ) ;

			csapEventClient.publishUserEvent( CsapEvents.CSAP_USER_SERVICE_CATEGORY + "/" + serviceName_port,
					userid, "API: upload, start", "location: " + uploadLocation ) ;

			if ( multiPartFile.getSize( ) == 0 ) {

				throw new IOException( "Invalid parameters. Verify name and size of attachment" ) ;

			}

			File targetDir = new File( uploadLocation ) ;

			if ( ! targetDir.exists( ) ) {

				targetDir.mkdirs( ) ;

			}

			File uploadFile = new File( targetDir, serviceInstance.getDeployFileName( ) ) ;

			resultJson.put( "destination", uploadFile.getAbsolutePath( ) ) ;

			multiPartFile.transferTo( uploadFile ) ;

			resultJson.put( "success", true ) ;

			File versionFile = serviceManager.createVersionFile(
					csapApp.getDeployVersionFile( serviceInstance ),
					version, userid, "API Upload" ) ;

			resultJson.put( "versionFileCreated", versionFile.getAbsolutePath( ) ) ;

			if ( serviceName_port != null && serviceName_port.length( ) > 0 && serviceStart ) {

				resultJson.put( "serviceStartRequested", true ) ;
				// startServiceAndWaitABit( serviceName_port, userid, resultJson
				// );
				ArrayList<String> services = new ArrayList<>( ) ;
				services.add( serviceName_port ) ;
				serviceCommands.startRequest(
						userid, services, null, null,
						"clean", null, userid, null ) ;

			} else {

				resultJson.put( "serviceStartRequested", false ) ;

			}

		} catch ( Exception e ) {

			resultJson.put( "success", false ) ;
			resultJson.put( "errorMessage", e.getMessage( ) ) ;

			logger.warn( CSAP.buildCsapStack( e ) ) ;
			logger.debug( "Full exception", e ) ;

		}

		return resultJson ;

	}

	private ServiceInstance findServiceOnCurrentHost ( String serviceName_port )
		throws IOException {

		ServiceInstance instance = csapApp.getServiceInstanceCurrentHost( serviceName_port ) ;

		if ( instance == null ) {

			throw new IOException( "Requested service not found in model: " + serviceName_port ) ;

		}

		return instance ;

	}

	@Inject
	@Qualifier ( "analyticsRest" )
	private RestTemplate analyticsTemplate ;

	@CsapDoc ( notes = {
			"Retrieve last start up time via events service. Returns 0 if events is not enabled",
			"param hostname:  specifying 'all' will return data for all services. port is required."
	} , linkTests = {
			"csap-agent agent-start-up"
	} , linkGetParams = {
			"hostname=csap-dev01,category=" + CsapEvents.CSAP_SYSTEM_CATEGORY + "/agent-start-up"
	} )
	@GetMapping ( {
			"/event/latestTime"
	} )
	public long latestEventTime (
									@RequestParam ( "hostname" ) String hostname ,
									@RequestParam ( "category" ) String category )
		throws Exception {

		var startEventTime = 0l ;

		String restUrl = csapApp.rootProjectEnvSettings( ).getEventApiUrl( ) + "/latest"
				+ "?life=" + csapApp.getCsapHostEnvironmentName( )
				+ "&host=" + hostname
				+ "&appId=" + csapApp.rootProjectEnvSettings( ).getEventDataUser( )
				+ "&category=" + category ;

		if ( ! csapApp.rootProjectEnvSettings( ).isEventPublishEnabled( ) ) {

			return 0l ;

		}

		logger.debug( "getting report from: {} ", restUrl ) ;

		try {

			ObjectNode startReport = analyticsTemplate.getForObject( restUrl, ObjectNode.class ) ;
			logger.debug( "startReport: {} ", startReport ) ;
			startEventTime = startReport.path( "createdOn" ).path( "unixMs" ).asLong( startEventTime ) ;

		} catch ( Exception e ) {

			logger.warn( "Failed to hit: {}  {}", restUrl, CSAP.buildCsapStack( e ) ) ;

		}

		if ( startEventTime == 0l ) {

			logger.warn( "Did not find a result for {}", restUrl ) ;

		}

		return startEventTime ;

	}

	public static final String SERVICE_EVENT = "/service/event" ;

	@CsapDoc ( notes = {
			SERVICE_EVENT + ": api for generating a service event store in events service",
			"param: service : service name for event"
	} , linkTests = {
			"csap-verify-service"
	} , linkPaths = {
			SERVICE_EVENT
	} , linkPostParams = {
			USERID_PASS_PARAMS
					+ "service=csap-agent"
					+ ",summary=Demo Message"
	} )
	@PostMapping ( SERVICE_EVENT )
	public String serviceEvent (
									String service ,
									String summary )
		throws Exception {

		var result = "Event Posted to events services" ;

		var instance = csapApp.findServiceByNameOnCurrentHost( service ) ;

		if ( instance != null && csapApp.rootProjectEnvSettings( ).isEventPublishEnabled( ) ) {

			csapEventClient.publishEvent( CsapEvents.CSAP_USER_SERVICE_CATEGORY + "/" + instance.getName( ) + "/api",
					summary, "generate via agentapi" ) ;

		} else {

			result = "Failed to publish Event - check logs." ;

		}

		return result ;

	}

}
