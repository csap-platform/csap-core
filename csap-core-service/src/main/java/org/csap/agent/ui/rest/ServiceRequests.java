package org.csap.agent.ui.rest ;

import static java.util.Comparator.comparing ;

import java.io.File ;
import java.io.IOException ;
import java.io.PrintWriter ;
import java.text.DateFormat ;
import java.text.SimpleDateFormat ;
import java.util.ArrayList ;
import java.util.Arrays ;
import java.util.Collections ;
import java.util.Date ;
import java.util.List ;
import java.util.Map.Entry ;
import java.util.Random ;
import java.util.concurrent.ArrayBlockingQueue ;
import java.util.concurrent.BlockingQueue ;
import java.util.concurrent.ExecutorService ;
import java.util.concurrent.ThreadPoolExecutor ;
import java.util.concurrent.TimeUnit ;
import java.util.regex.Matcher ;
import java.util.regex.Pattern ;
import java.util.stream.Collectors ;

import javax.inject.Inject ;
import javax.servlet.http.HttpServletRequest ;
import javax.servlet.http.HttpServletResponse ;
import javax.servlet.http.HttpSession ;

import org.apache.commons.lang3.concurrent.BasicThreadFactory ;
import org.csap.agent.CsapCore ;
import org.csap.agent.CsapCoreService ;
import org.csap.agent.api.AgentApi ;
import org.csap.agent.container.DockerJson ;
import org.csap.agent.integrations.CsapEvents ;
import org.csap.agent.linux.OsCommandRunner ;
import org.csap.agent.linux.OutputFileMgr ;
import org.csap.agent.model.ActiveUsers ;
import org.csap.agent.model.Application ;
import org.csap.agent.model.HealthManager ;
import org.csap.agent.model.Project ;
import org.csap.agent.model.ServiceAlertsEnum ;
import org.csap.agent.model.ServiceInstance ;
import org.csap.agent.services.HostKeys ;
import org.csap.agent.services.OsManager ;
import org.csap.agent.services.ServiceCommands ;
import org.csap.agent.services.ServiceOsManager ;
import org.csap.agent.stats.MetricCategory ;
import org.csap.docs.CsapDoc ;
import org.csap.helpers.CSAP ;
import org.csap.helpers.CsapApplication ;
import org.csap.security.config.CsapSecuritySettings ;
import org.jasypt.encryption.pbe.StandardPBEStringEncryptor ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.beans.factory.annotation.Autowired ;
import org.springframework.beans.factory.annotation.Qualifier ;
import org.springframework.cache.annotation.Cacheable ;
import org.springframework.core.io.ClassPathResource ;
import org.springframework.http.HttpHeaders ;
import org.springframework.http.MediaType ;
import org.springframework.http.ResponseEntity ;
import org.springframework.util.LinkedMultiValueMap ;
import org.springframework.util.MultiValueMap ;
import org.springframework.web.bind.annotation.DeleteMapping ;
import org.springframework.web.bind.annotation.GetMapping ;
import org.springframework.web.bind.annotation.PathVariable ;
import org.springframework.web.bind.annotation.PostMapping ;
import org.springframework.web.bind.annotation.RequestMapping ;
import org.springframework.web.bind.annotation.RequestParam ;
import org.springframework.web.bind.annotation.ResponseBody ;
import org.springframework.web.bind.annotation.RestController ;
import org.springframework.web.client.RestTemplate ;
import org.springframework.web.multipart.MultipartFile ;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody ;

import com.fasterxml.jackson.core.JsonParser ;
import com.fasterxml.jackson.databind.JsonNode ;
import com.fasterxml.jackson.databind.ObjectMapper ;
import com.fasterxml.jackson.databind.node.ArrayNode ;
import com.fasterxml.jackson.databind.node.ObjectNode ;

import io.micrometer.core.instrument.util.StringUtils ;

/**
 *
 * ServiceRequests is a container for MVC actions primarily targetting the main
 * UI for services.
 *
 * @author someDeveloper
 *
 * @see <a href=
 *      "http://static.springsource.org/spring/docs/current/spring-framework-reference/html/mvc.html">
 *      SpringMvc Docs </a>
 *
 */
@RestController
@RequestMapping ( CsapCoreService.SERVICE_URL )
@CsapDoc ( title = "Service Operations" , notes = {
		"Update, Reload and similar operations to manage the running application",
		"<a class='csap-link' target='_blank' href='https://github.com/csap-platform/csap-core/wiki'>learn more</a>",
		"<img class='csapDocImage' src='CSAP_BASE/images/portals.png' />"
} )
public class ServiceRequests {

	final Logger logger = LoggerFactory.getLogger( getClass( ) ) ;

	public static final String SSO_PARAM_FOR_BATCH = "ssoCookie" ;

	@Inject
	public ServiceRequests (
			Application csapApp,
			ServiceOsManager serviceOsManager,
			OsManager osManager,
			CsapEvents csapEventClient,
			ServiceCommands serviceCommands ) {

		this.serviceOsManager = serviceOsManager ;
		this.csapApp = csapApp ;
		this.osManager = osManager ;
		this.csapEventClient = csapEventClient ;
		this.serviceCommands = serviceCommands ;

	}

	Application csapApp ;

	@Autowired ( required = false )
	AgentApi agentApi ;

	@Inject
	ServiceCommands serviceCommands ;

	@Autowired ( required = false )
	CsapSecuritySettings securitySettings ;

	@Inject
	@Qualifier ( "analyticsRest" )
	private RestTemplate analyticsTemplate ;

	CsapEvents csapEventClient ;
	ServiceOsManager serviceOsManager ;
	OsManager osManager ;

	OsCommandRunner osCommandRunner = new OsCommandRunner( 60, 2, "ServiceRequests" ) ;

	ObjectMapper jacksonMapper = new ObjectMapper( ) ;

	public ServiceRequests ( ) {

		jacksonMapper.getFactory( ).enable( JsonParser.Feature.ALLOW_UNQUOTED_CONTROL_CHARS ) ;

	}

	public static boolean isRoleInGroup ( List<String> roles , String role ) {

		for ( String curRole : roles ) {

			if ( curRole.equalsIgnoreCase( role ) ) {

				return true ;

			}

		}

		return false ;

	}

	static public String MAVEN_VERIFY_FILE = "admin-run-load-test.sh" ;
	static public String JMX_AUTH_FILE = "admin-reset-jmx-auth.sh" ;
	static private String CLEAN_UP_SCRIPT = "admin-clean-deploy.sh" ;

	public final static String SKIP_HEADERS = "skipHeaders" ;

	@Inject
	@Qualifier ( "csapEventsService" )
	private RestTemplate csapEventsService ;

	@Cacheable ( value = CsapCoreService.TIMEOUT_CACHE_60s , key = "{'eventCount-' + #project }" )
	@RequestMapping ( value = "/activityCount" )
	public ObjectNode eventCount (
									@RequestParam ( value = CsapCore.PROJECT_PARAMETER , required = false ) String csapProject ) {

		ObjectNode eventReport = jacksonMapper.createObjectNode( ) ;

		String eventCountUrl = csapApp.rootProjectEnvSettings( ).getEventUiCountUrl( ) ;

		if ( csapProject != null ) {

			eventCountUrl += "&project=" + csapProject ;

		}

		eventReport.put( "eventsIn24hours", "-1" ) ;
		eventReport.put( "url", eventCountUrl ) ;
		eventReport.set( "environment-urls", csapApp.getRootProject( ).buildLbReport( ) ) ;
		logger.debug( "restUrl: {}", eventCountUrl ) ;

		eventReport.set( "packageMap", jacksonMapper.convertValue( csapApp.getPackageNameToFileMap( ),
				ObjectNode.class ) ) ;

		if ( ! eventCountUrl.startsWith( "http" ) ) {

			logger.debug( "====> \n\n Cache Reuse or disabled" ) ;

			eventReport.put( "message", "cache disabled: " + eventCountUrl ) ;

		}

		if ( ! csapApp.rootProjectEnvSettings( ).isEventPublishEnabled( ) ) {

			logger.info( "Stubbing out data for trends - add csap events services" ) ;
			eventReport.put( "eventsIn24hours", "disabled" ) ;
			eventReport.put( "message", "csap-event-service disabled - using stub data" ) ;
			return eventReport ;

		}

		try {

			ObjectNode countReport = csapEventsService.getForObject( eventCountUrl, ObjectNode.class ) ;

			if ( countReport != null ) {

				eventReport.put( "eventsIn24hours", countReport.path( "count" ).asInt( -1 ) ) ;

				var discoveryUrl = csapApp.rootProjectEnvSettings( ).getEnvDiscoveryUrl( ) ;
				var discoveryReport = csapEventsService.getForObject( discoveryUrl, ObjectNode.class ) ;

				if ( discoveryReport != null ) {

					eventReport.set( "discovery", discoveryReport ) ;

				}

			} else {

				eventReport.put( "message", "Got a null response from url: " + eventCountUrl ) ;

			}

		} catch ( Exception e ) {

			logger.error( "Failed getting activity count from url: {}, reason: {}", eventCountUrl, CSAP.buildCsapStack(
					e ) ) ;
			eventReport.put( "url", eventCountUrl ) ;
			eventReport.put( "message", "Error during Access: " + e.getMessage( ) ) ;

		}

		return eventReport ;

	}

	@RequestMapping ( value = "/httpd" , produces = MediaType.TEXT_HTML_VALUE )
	@ResponseBody
	public String getHttpdStatus ( HttpServletRequest request )
		throws IOException {

		String statusUrl = "http://localhost:8080/server-status?" + request.getQueryString( ) ;

		if ( Application.isRunningOnDesktop( ) ) {

			statusUrl = "http://csap-dev02:8080/server-status?" + request.getQueryString( ) ;

		}

		csapEventClient.publishUserEvent( "httpd", securitySettings.getRoles( ).getUserIdFromContext( ),
				"apache httpd status", "privleged information via: " + statusUrl ) ;

		String restResponse = analyticsTemplate.getForObject( statusUrl, String.class ) ;

		return restResponse ;

	}

	@RequestMapping ( value = {
			"/modjk", "/status"
	} , produces = MediaType.TEXT_HTML_VALUE )
	@ResponseBody
	public String getModjkStatus ( HttpServletRequest request )
		throws IOException {

		logger.debug( "queryString: {} ", request.getQueryString( ) ) ;
		String statusUrl = "http://localhost:8080/status?" + request.getQueryString( ) ;

		if ( Application.isRunningOnDesktop( ) ) {

			statusUrl = "http://csap-dev02:8080/status?" + request.getQueryString( ) ;

		}

		csapEventClient.publishUserEvent( "httpd", securitySettings.getRoles( ).getUserIdFromContext( ),
				"apache modjk status", "privleged information via: " + statusUrl ) ;

		String restResponse = analyticsTemplate.getForObject( statusUrl, String.class ) ;
		// restResponse.replaceAll("/status", "./status")
		// response.getWriter().print( restResponse );
		return restResponse.replaceAll( "/status", "./status" ) ;

	}

	@RequestMapping ( value = "/resources" )
	public ObjectNode service_resources (
											@RequestParam ( value = CsapCore.HOST_PARAM ) String[] hostNameArray ,
											@RequestParam ( value = CsapCore.SERVICE_PORT_PARAM ) String serviceName ) {

		return csapApp.getServiceCollection( hostNameArray, serviceName ) ;

	}

	static public final String LATEST_APP_STATS_URL = "/query/getLatestAppStats" ;

	@GetAndPostMapping ( LATEST_APP_STATS_URL )
	public JsonNode getLatestAppStats (
										@RequestParam ( value = CsapCore.HOST_PARAM , required = false ) ArrayList<String> hosts ,
										@RequestParam ( value = CsapCore.SERVICE_PORT_PARAM ) String serviceName ,
										String type ,
										String interval ,
										int number ,
										HttpServletRequest request ) {

		ObjectNode resultsJson = jacksonMapper.createObjectNode( ) ;

		if ( csapApp.isAdminProfile( ) ) {

			MultiValueMap<String, String> urlVariables = new LinkedMultiValueMap<String, String>( ) ;

			String url = CsapCoreService.SERVICE_URL + LATEST_APP_STATS_URL ;

			urlVariables.add( CsapCore.SERVICE_PORT_PARAM, serviceName ) ;

			urlVariables.add( "type", type ) ;
			urlVariables.add( "interval", interval ) ;
			urlVariables.add( "number", Integer.toString( number ) ) ;

			if ( hosts != null ) {

				resultsJson = serviceOsManager.remoteAgentsStateless( hosts, url, urlVariables ) ;

			} else {

				resultsJson.put( CsapCore.CONFIG_PARSE_ERROR, " - Failed to find hostName: "
						+ hosts ) ;

			}

			return resultsJson ;

		}

		if ( type.equals( "app" ) ) {

			return agentApi.collectionApplication( serviceName, interval, number ) ;

		} else {

			return agentApi.collectionJava( serviceName, interval, number ) ;

		}

	}

	@Cacheable ( value = CsapCoreService.TIMEOUT_CACHE_60s , key = "{'realTimeMeters-' + #projectName + #detailMeters  }" )
	@RequestMapping ( value = "/realTimeMeters" )
	public ArrayNode getRealTimeMeters (
											@RequestParam ( value = CsapCore.PROJECT_PARAMETER ) String projectName ,
											@RequestParam ( value = "meterId" , required = false , defaultValue = "" ) ArrayList<String> detailMeters ) {

		// copy the definitions - then added results
		ArrayNode realTimeMeterReport = csapApp.rootProjectEnvSettings( ).getRealTimeMeters( ).deepCopy( ) ;

		if ( csapApp.isAdminProfile( ) ) {

			logger.debug( "Updating Manager meters" ) ;

			Project model = csapApp.getProject( projectName ) ;

			if ( model == null ) {

				model = csapApp.getActiveProject( ).getAllPackagesModel( ) ;

			}

			csapApp.getHostStatusManager( ).updateRealTimeMeters( realTimeMeterReport, model.getHostsCurrentLc( ),
					detailMeters ) ;

		} else {

			for ( JsonNode item : realTimeMeterReport ) {

				ObjectNode meterJson = (ObjectNode) item ;

				String id = meterJson.get( "id" ).asText( ) ;
				String[] jsonPath = id.split( Pattern.quote( "." ) ) ;
				String collector = jsonPath[ 0 ] ;
				String attribute = jsonPath[ 1 ] ;
				// logger.info("collector: " + collector + " attribute: " +
				// attribute);
				// vm. process. jmxCommon. jmxCustom.Service.var

				ObjectNode latestCollection = null ;
				ObjectNode collectedJson = null ;

				try {

					latestCollection = (ObjectNode) osManager.buildRealTimeCollectionReport( ) ;
					collectedJson = (ObjectNode) latestCollection.get( collector ) ;

					if ( collector.equals( MetricCategory.application.json( ) ) ) {

						String serviceName = jsonPath[ 1 ] ;
						attribute = jsonPath[ 2 ] ;

						if ( ! collectedJson.has( serviceName ) ) {

							continue ;

						}

						collectedJson = (ObjectNode) collectedJson.get( serviceName ) ;

					}

					if ( ! collectedJson.has( attribute ) ) {

						continue ;

					}

					// logger.info(" collectedJson : " + collectedJson); ;
					meterJson.put( "value", collectedJson.get( attribute ).asInt( ) ) ;
					meterJson.put( MetricCategory.hostCount.json( ), 1 ) ;

					// to get values
					if ( detailMeters == null || detailMeters.contains( id ) ) {

						ArrayNode hosts = meterJson.putArray( "hostNames" ) ;
						hosts.add( csapApp.getCsapHostName( ) ) ;
						ArrayNode hostValues = meterJson.putArray( "hostValues" ) ;
						hostValues.add( collectedJson.get( attribute ).asInt( ) ) ;

					}

				} catch ( Exception e ) {

					logger.error( "Failed runtime: {} latestCollection: {} {}",
							collectedJson, latestCollection, CSAP.buildCsapStack( e ) ) ;

					meterJson.put( "value", 0 ) ;

				}

			}

		}

		return realTimeMeterReport ;

	}

	// #path1.concat('peter')

	/**
	 * Wrapper for events service: only used on application/agent portals which may
	 * have different ssl contexts - specifically: model.js makes calls to retrieve
	 * /csap/system/model/summary when displaying graphs - also used to get last
	 * boot time
	 * 
	 * @param apiName
	 * @param appId
	 * @param life
	 * @param category
	 * @param request
	 * @return
	 * @throws Exception
	 */
	public static final String EVENT_API_WRAPPER = "/eventApi" ;

	@Cacheable ( value = CsapCoreService.TIMEOUT_CACHE_60s , key = "{'latestEvent-' + #apiName + #appId + #life + #category }" )
	@RequestMapping ( value = EVENT_API_WRAPPER + "/{apiName:.+}" )
	public ObjectNode latestEvent (
									@PathVariable ( value = "apiName" ) String apiName ,
									@RequestParam ( value = "appId" , required = true ) String appId ,
									@RequestParam ( value = "life" , required = true ) String life ,
									@RequestParam ( value = "category" , required = false ) String category ,
									HttpServletRequest request )
		throws Exception {

		ObjectNode resultsJson = jacksonMapper.createObjectNode( ) ;

		String restUrl = csapApp.rootProjectEnvSettings( ).getEventApiUrl( ) + "/" + apiName
				+ "?life=" + life
				+ "&appId=" + appId
				+ "&category=" + category ;

		if ( ! csapApp.rootProjectEnvSettings( ).isEventPublishEnabled( ) ) {

			resultsJson.put( "error", "CSAP Event API is  disabled - update definition to enable" ) ;
			resultsJson.put( "url", restUrl ) ;
			resultsJson.put( "stub", true ) ;
			return resultsJson ;

		}

		logger.debug( "getting report from: {} ", restUrl ) ;

		try {

			ObjectNode restResponse = analyticsTemplate.getForObject( restUrl, ObjectNode.class ) ;

			resultsJson = restResponse ;

			if ( resultsJson != null ) {

				resultsJson.put( "source", restUrl ) ;

				resultsJson.put( "updated", shortFormatter.format( new Date( ) ) ) ;

			}

		} catch ( Exception e ) {

			resultsJson.put( "error", "CSAP Event API Failed - update definition to enable" ) ;
			resultsJson.put( "url", restUrl ) ;

			logger.error( "Event Api failure: {}", CSAP.jsonPrint( resultsJson ) ) ;

			logger.debug( "Stack Trace {}", CSAP.buildCsapStack( e ) ) ;

		}

		return resultsJson ;

	}

	@Inject
	GraphCache graphsCache ;

	/**
	 * loads historical data in context; wraps remote calls so that ssl & non ssl
	 * applications can still get data
	 *
	 * @param callback
	 * @param host
	 * @param graph
	 * @param appId
	 * @param life
	 * @param numberOfDays
	 * @param dateOffSet
	 * @param serviceName
	 * @param padLatest
	 * @param searchFromBegining
	 * @param response
	 * @throws Exception
	 */
	@RequestMapping ( value = "/metricsApi/{host}/{graph}" , produces = {
			"application/javascript"
	} )
	public void metricsApi (
								@RequestParam ( value = "callback" , defaultValue = "false" ) String callback ,
								@PathVariable ( value = "host" ) String host ,
								@PathVariable ( value = "graph" ) String graph ,
								@RequestParam ( value = "appId" , required = true ) String appId ,
								@RequestParam ( value = "life" , required = true ) String life ,
								@RequestParam ( value = "numberOfDays" , required = false , defaultValue = "1" ) int numberOfDays ,
								@RequestParam ( value = "dateOffSet" , required = false , defaultValue = "0" ) int dateOffSet ,
								@RequestParam ( value = CsapCore.SERVICE_PORT_PARAM , required = false ) String serviceName ,
								@RequestParam ( value = "padLatest" , required = false ) String padLatest ,
								@RequestParam ( value = "searchFromBegining" , required = false ) String searchFromBegining ,
								HttpServletResponse response )
		throws Exception {

		String restResponse = "{failed}" ;
		// ObjectNode resultsJson = jacksonMapper.createObjectNode();
		String restUrl = csapApp.rootProjectEnvSettings( ).getEventMetricsUrl( ) + host + "/" + graph
				+ "?life=" + life
				+ "&appId=" + appId
				+ "&numberOfDays=" + numberOfDays
				+ "&dateOffSet=" + dateOffSet
				+ "&serviceName=" + serviceName
				+ "&padLatest=" + padLatest
				+ "&searchFromBegining=" + searchFromBegining ;

		if ( callback.equals( "false" ) ) {

			response.setContentType( MediaType.APPLICATION_JSON_VALUE ) ;

		} else {

			response.setContentType( "application/javascript" ) ;

		}

		PrintWriter writer = response.getWriter( ) ;

		if ( ! callback.equals( "false" ) ) {

			writer.print( callback + "(" ) ;

		}

		String cachedResponse = graphsCache.getGraphData( restUrl ) ;

		if ( cachedResponse.length( ) < 100 && cachedResponse.contains( "Error getting data" ) ) {

			ObjectNode resultsJson = jacksonMapper.createObjectNode( ) ;
			resultsJson.put( "error", "Invalid Response from " + restUrl ) ;
			resultsJson.put( "host", host ) ;
			cachedResponse = resultsJson.toString( ) ;

		}

		writer.print( cachedResponse ) ;

		if ( ! callback.equals( "false" ) ) {

			writer.print( ")" ) ;

		}

		return ;

	}

	DateFormat shortFormatter = new SimpleDateFormat( "HH:mm:ss MMM-dd" ) ;

	// #path1.concat('peter')
	// @Cacheable ( CsapCoreService.TIMEOUT_CACHE_60s )
	@Cacheable ( cacheNames = CsapCoreService.TIMEOUT_CACHE_60s , keyGenerator = "compareKeyGenerator" )

	@RequestMapping ( value = "/report" )
	public ObjectNode analyticsCompareReport (
												@RequestParam ( value = "report" , required = false ) String report ,
												@RequestParam ( value = "appId" , required = true ) String appId ,
												@RequestParam ( value = "project" , required = true ) String project ,
												@RequestParam ( value = CsapCore.SERVICE_PORT_PARAM , required = false ) String serviceName ,
												@RequestParam ( value = "host" , required = false ) String host ,
												@RequestParam ( value = "life" , required = false ) String life ,
												@RequestParam ( value = "numDays" , required = false , defaultValue = "1" ) int numDays ,
												@RequestParam ( value = "dateOffSet" , required = false , defaultValue = "0" ) int dateOffSet ,
												@RequestParam ( value = "trending" , required = false , defaultValue = "0" ) int trending ,
												@RequestParam ( value = "trendDivide" , required = false , defaultValue = "0" ) String trendDivide ,
												@RequestParam ( value = "allVmTotal" , required = false , defaultValue = "" ) String allVmTotal ,
												@RequestParam ( value = "metricsId" , required = false , defaultValue = "topCpu" ) String metricsId ,
												@RequestParam ( value = "resource" , required = false , defaultValue = "resource_30" ) String resource )
		throws Exception {

		ObjectNode resultsJson = jacksonMapper.createObjectNode( ) ;

		// Support for converting to Analytics
		String analyticsReportType = report ;

		if ( report.equals( "app" ) ) {

			analyticsReportType = CsapApplication.COLLECTION_APPLICATION ;

		}

		if ( report.equals( "java" ) ) {

			analyticsReportType = CsapApplication.COLLECTION_JAVA ;

		}

		String restUrl = csapApp.rootProjectEnvSettings( ).getReportUrl( ) + analyticsReportType
				+ "?life=" + life
				+ "&appId=" + appId
				+ "&numDays=" + numDays
				+ "&dateOffSet=" + dateOffSet ;

		if ( ! project.contains( CsapCore.ALL_PACKAGES ) ) {

			restUrl += "&project=" + project ;

		}

		logger.debug( "getting report from: {} ", restUrl ) ;

		if ( trending != 0 ) {

			restUrl += "&trending=true&metricsId=" + metricsId ;

			if ( trendDivide.length( ) > 1 ) {

				String[] divides = trendDivide.split( "," ) ;

				for ( String div : divides ) {

					restUrl += "&divideBy=" + div ;

				}

			}

			if ( allVmTotal.length( ) > 0 ) {

				restUrl += "&allVmTotal=" + allVmTotal ;

			}

		}

		if ( serviceName != null ) {

			restUrl += "&serviceName=" + serviceName ;

		}

		if ( host != null ) {

			restUrl += "&host=" + host ;

		}

		try {

			ObjectNode restResponse ;

			if ( ! csapApp.rootProjectEnvSettings( ).isEventPublishEnabled( ) ) {

				ClassPathResource reportStub = new ClassPathResource( CsapCore.EVENTS_STUB_FOLDER + "report-" + report
						+ ".json" ) ;
				logger.info( "Stubbing out report data using: {},  add csap events services", reportStub ) ;
				restResponse = (ObjectNode) jacksonMapper.readTree( reportStub.getFile( ) ) ;

				resultsJson.put( "message", "csap-event-service disabled - using stub data" ) ;

			} else {

				restResponse = analyticsTemplate.getForObject( restUrl, ObjectNode.class ) ;

			}

			resultsJson = restResponse ;

			if ( resultsJson != null ) {

				resultsJson.put( "source", restUrl ) ;

				resultsJson.put( "updated", shortFormatter.format( new Date( ) ) ) ;

			}

		} catch ( Exception e ) {

			String reason = CSAP.buildCsapStack( e ) ;
			logger.error( "Failed getting report from url: {}, Reason: {}", restUrl, reason ) ;
			logger.debug( "Stack Trace {}", reason ) ;
			resultsJson.put( "url", restUrl ) ;
			resultsJson.put( "message", "Error during Access: " + reason ) ;

		}

		return resultsJson ;

	}

	@Autowired
	TrendCache trendCache ;

	@Autowired
	TrendCacheManager trendCacheManager ;

	@RequestMapping ( value = "/trend" )
	@CsapDoc ( notes = "Get last cached data, and trigger a refresh if needed" , linkTests = {
			"Vm Threads",
			"NullException"
	} , linkGetParams = {
			"report=vm,appId=csapssp.gen,metricsId=threadsTotal,project='SNTC and PSS',life=dev,trending=1",
			"report=testNull,appId=csapssp.gen,project='SNTC and PSS',life=dev"
	} )
	public ObjectNode analyticsTrendReport (
												@RequestParam ( value = "report" , required = false ) String report ,
												@RequestParam ( value = "appId" , required = true ) String appId ,
												@RequestParam ( value = "project" , required = true ) String project ,
												@RequestParam ( value = CsapCore.SERVICE_PORT_PARAM , required = false ) String serviceName ,
												@RequestParam ( value = "host" , required = false ) String host ,
												@RequestParam ( value = "life" , required = false ) String life ,
												@RequestParam ( value = "numDays" , required = false , defaultValue = "1" ) int numDays ,
												@RequestParam ( value = "top" , required = false , defaultValue = "0" ) int top ,
												@RequestParam ( value = "low" , required = false , defaultValue = "0" ) int low ,
												@RequestParam ( value = "dateOffSet" , required = false , defaultValue = "0" ) int dateOffSet ,
												@RequestParam ( value = "trending" , required = false , defaultValue = "0" ) int trending ,
												String trendDivide ,
												String allVmTotal ,
												String perVm ,
												@RequestParam ( value = "metricsId" , required = false , defaultValue = "topCpu" ) String metricsId ,
												@RequestParam ( value = "resource" , required = false , defaultValue = "resource_30" ) String resource )
		throws Exception {

		if ( Application.isRunningOnDesktop( ) && appId.equals( "DesktopCsap" ) ) {

			appId = "SensusCsap" ;

		}

		String restUrl = csapApp.rootProjectEnvSettings( ).getReportUrl( ) + report
				+ "?life=" + life
				+ "&appId=" + appId
				+ "&dateOffSet=" + dateOffSet ;

		if ( ! project.contains( CsapCore.ALL_PACKAGES ) ) {

			restUrl += "&project=" + project ;

		}

		// logger.warn( "SLEEPING 10000 for testing" );
		// Thread.sleep( 10000 );
		if ( trending != 0 ) {

			restUrl += "&trending=true&metricsId=" + metricsId ;
			restUrl += "&top=" + top ;
			restUrl += "&low=" + low ;

			if ( StringUtils.isNotEmpty( trendDivide ) ) {

				String[] divides = trendDivide.split( "," ) ;

				for ( String div : divides ) {

					restUrl += "&divideBy=" + div ;

				}

			}

			if ( StringUtils.isNotEmpty( allVmTotal ) ) {

				restUrl += "&allVmTotal=" + allVmTotal ;

			}

			if ( StringUtils.isNotEmpty( perVm ) ) {

				restUrl += "&perVm=" + perVm ;

			}

		}

		if ( serviceName != null && ! MetricCategory.isAllServices( serviceName ) ) {

			restUrl += "&serviceName=" + serviceName ;

		}

		if ( host != null ) {

			restUrl += "&host=" + host ;

		}

		String timerName = project + "." + report + ".days." + numDays ;
		ObjectNode resultsJson = trendCache.get( restUrl + "&numDays=" + numDays, timerName ) ;

		if ( trendCacheManager.isRefreshNeeded( resultsJson, numDays ) ) {

			logger.debug( "Updating cache on background thread to not impact UI" ) ;
			trendCacheManager.updateInBackground( restUrl + "&numDays=" + numDays, numDays, timerName ) ;
			// if ( numDays == 14 || numDays == 16 ) {
			//
			// if ( trendCacheManager.isInitialLoad( resultsJson ) ) {
			// // if we have results for 2weeks - and no other results - lets
			// lazy load for enhanced UI
			// logger.info( "{} - Refreshing {}", project, report );
			// for ( int reportDay : reportDays ) {
			// trendCacheManager.updateInBackground( restUrl + "&numDays=" +
			// reportDay,
			// reportDay, timerName + reportDay );
			// }
			// }
			//
			// }

		}

		return resultsJson ;

	}

	private Random batchRandom = new Random( ) ;

	@RequestMapping ( value = "/batchStart" , produces = MediaType.APPLICATION_JSON_VALUE )
	public ObjectNode startBatch (
									@RequestParam ( CsapCore.SERVICE_PORT_PARAM ) ArrayList<String> services ,
									@RequestParam ( CsapCore.HOST_PARAM ) ArrayList<String> hosts ,
									HttpServletRequest request ) {

		String uiUser = securitySettings.getRoles( ).getUserIdFromContext( ) ;

		logger.info( CSAP.buildDescription(
				"Batch Start",
				"uiUser", uiUser,
				"services", services,
				"hosts", hosts ) ) ;

		ObjectNode resultsJson = jacksonMapper.createObjectNode( ) ;

		List<String> requestedNames = services ;

		createBatchThreadPool( ) ;
		resultsJson.put( "result", "Batch start has been scheduled." ) ;
		resultsJson.put( "hosts", hosts.size( ) ) ;
		resultsJson.put( HostKeys.services.json( ), services.size( ) ) ;
		resultsJson.put( "parallelRequests",
				csapApp.rootProjectEnvSettings( ).getNumberWorkerThreads( ) ) ;

		ObjectNode hostMap = resultsJson.putObject( "hostInfo" ) ;

		int operationCount = 0 ;
		int jobCount = 0 ;

		for ( String batchHostTarget : hosts ) {

			ObjectNode hostInfo = hostMap.putObject( batchHostTarget ) ;

			List<String> batchServices = csapApp
					.getServicesOnTargetHost( batchHostTarget )
					.stream( )
					.filter( instance -> requestedNames.contains( instance.getName( ) ) )
					.sorted( comparing( ServiceInstance::startOrder ) )
					.map( ServiceInstance::getServiceName_Port )
					.collect( Collectors.toList( ) ) ;

			operationCount += batchServices.size( ) ;

			if ( batchServices.size( ) == 0 ) {

				hostInfo.put( "info", "Skipping - no services on host" ) ;
				continue ;

			} else {

				if ( batchServices.size( ) != requestedNames.size( ) ) {

					hostInfo.put( "info",
							"Scheduling services (host filtered)" ) ;

				} else {

					hostInfo.put( "info", "Scheduling services" ) ;

				}

				ArrayNode servicesNode = hostInfo.putArray( HostKeys.services.json( ) ) ;

				for ( String svc : batchServices ) {

					servicesNode.add( svc ) ;

				}

			}

			jobCount++ ;
			Runnable startServicesJob = ( ) -> {

				logger.debug( "Batch Start: {}", batchHostTarget ) ;

				try {

					ObjectNode results ;

					if ( csapApp.isAdminProfile( ) ) {

						results = serviceCommands.startRemoteRequests(
								uiUser,
								batchServices,
								Arrays.asList( batchHostTarget ),
								null,
								null, null, null,
								csapApp.rootProjectEnvSettings( ).getAgentUser( ),
								csapApp.rootProjectEnvSettings( ).getAgentPass( ),
								null ) ;

					} else {

						results = serviceCommands.startRequest(
								uiUser, batchServices,
								null, null, null, null, null, null ) ;

					}

					Thread.sleep( 1000 * batchRandom.nextInt( 5 ) ) ;
					logger.debug( "{} Completed ", batchHostTarget ) ;
					logger.info( "Results: \n {}",
							jacksonMapper.writerWithDefaultPrettyPrinter( ).writeValueAsString( results ) ) ;

				} catch ( Throwable t ) {

					logger.error( "{} start failed: \n", batchHostTarget, t ) ;

				}

			} ;
			batchExecutor.submit( startServicesJob ) ;

		}

		resultsJson.put( "jobsOperations", operationCount ) ;
		resultsJson.put( "jobsCount", jobCount ) ;
		resultsJson.put( "jobsRemaining", jobCount ) ;

		return resultsJson ;

	}

	@RequestMapping ( value = "/batchDeploy" , produces = MediaType.APPLICATION_JSON_VALUE )
	public ObjectNode deployBatch (
									@RequestParam ( value = CsapCore.PROJECT_PARAMETER , required = true ) String csapProject ,
									@RequestParam ( CsapCore.SERVICE_PORT_PARAM ) ArrayList<String> services ,
									@RequestParam ( CsapCore.HOST_PARAM ) ArrayList<String> hosts ,
									HttpServletRequest request ) {

		String uiUser = securitySettings.getRoles( ).getUserIdFromContext( ) ;

		logger.info( CSAP.buildDescription(
				"Batch Deploy",
				"uiUser", uiUser,
				"services", services,
				"hosts", hosts ) ) ;

		ObjectNode resultsJson = jacksonMapper.createObjectNode( ) ;

		createBatchThreadPool( ) ;
		resultsJson.put( "result", "Batch deploy has been scheduled." ) ;
		resultsJson.put( "hosts", hosts.size( ) ) ;
		resultsJson.put( HostKeys.services.json( ), services.size( ) ) ;
		resultsJson.put( "parallelRequests",
				csapApp.rootProjectEnvSettings( ).getNumberWorkerThreads( ) ) ;

		ObjectNode hostInfo = resultsJson.putObject( "hostInfo" ) ;

		int operationsCount = 0 ;
		int jobCount = 0 ;

		// deploy service on all hosts - in order.
		List<String> serviceNamesSortedByStartOrder = csapApp
				.getProject( csapProject )
				.serviceInstancesInCurrentLifeByName( )
				.entrySet( )
				.stream( )
				.filter( allInstances -> services.contains( allInstances.getKey( ) ) )
				.map( Entry::getValue )
				.map( instanceList -> instanceList.get( 0 ) )
				.sorted( comparing( ServiceInstance::startOrder ) )
				.map( ServiceInstance::getName )
				.collect( Collectors.toList( ) ) ;

		logger.info( "Submitted order: {} \n\t Sorted by start: {}",
				services,
				serviceNamesSortedByStartOrder ) ;

		for ( String serviceName : serviceNamesSortedByStartOrder ) {

			List<ServiceInstance> instances = csapApp.getProject( csapProject )
					.getAllPackagesModel( )
					.serviceInstancesInCurrentLifeByName( )
					.get( serviceName ) ;

			List<String> batchServices = instances
					.stream( )
					.map( ServiceInstance::getServiceName_Port )
					.distinct( )
					.collect( Collectors.toList( ) ) ;

			// String serviceNamePort = instances.get( 0
			// ).getServiceName_Port();

			List<String> batchHosts = instances.stream( )
					.filter( instance -> hosts.contains( instance.getHostName( ) ) )
					.map( instance -> instance.getHostName( ) )
					.collect( Collectors.toList( ) ) ;

			operationsCount += batchHosts.size( ) ;

			if ( batchHosts.size( ) == 0 ) {

				ObjectNode hostNode = (ObjectNode) hostInfo.putObject( serviceName ) ;
				hostNode.put( "info", "No Services found that match: " + batchServices ) ;
				logger.debug( "Skipping {} - no services on any selected hosts", batchServices ) ;
				continue ;

			} else {

				for ( String host : batchHosts ) {

					ObjectNode hostNode = (ObjectNode) hostInfo.get( host ) ;

					if ( hostNode == null ) {

						hostNode = hostInfo.putObject( host ) ;
						hostNode.put( "info", "Deploying Services" ) ;

					}

					ArrayNode hostServiceNode = (ArrayNode) hostNode.get( HostKeys.services.json( ) ) ;

					if ( hostServiceNode == null ) {

						hostServiceNode = hostNode.putArray( HostKeys.services.json( ) ) ;

					}

					batchServices.stream( ).forEach( hostServiceNode::add ) ;

				}

			}

			jobCount++ ;
			Runnable deployServiceJobs = ( ) -> {

				logger.debug( "Batch Start: {}", batchServices ) ;

				try {

					ObjectNode results ;

					if ( csapApp.isAdminProfile( ) ) {

						results = serviceCommands.deployRemoteRequests(
								"ms" + System.currentTimeMillis( ),
								uiUser,
								batchServices, batchHosts,
								"dummy", "dummy", "dummy",
								null, null,
								ServiceOsManager.MAVEN_DEFAULT_BUILD, null,
								null,
								csapApp.rootProjectEnvSettings( ).getAgentUser( ),
								csapApp.rootProjectEnvSettings( ).getAgentPass( ) ) ;

					} else {

						results = serviceCommands.deployRequest(
								uiUser, null,
								"ms" + System.currentTimeMillis( ),
								batchServices,
								"dummy", "dummy", "dummy",
								null,
								null, ServiceOsManager.MAVEN_DEFAULT_BUILD, null,
								null,
								null ) ;

					}

					// ObjectNode results = rebuildServer( firstHost,
					// "dummy", "dummy", "dummy", serviceNamePort,
					// null, null, scpHosts, null,
					// ServiceOsManager.MAVEN_DEFAULT_BUILD, true, null, user,
					// null );
					// stagger the jobs to distribute compute a bit
					Thread.sleep( 1000 * batchRandom.nextInt( 5 ) ) ;
					logger.debug( "{} Completed ", batchServices ) ;
					logger.debug( "Results: \n {}",
							jacksonMapper.writerWithDefaultPrettyPrinter( ).writeValueAsString( results ) ) ;

				} catch ( Throwable e ) {

					logger.error( "{} deploy failed", batchServices, e ) ;

				}

			} ;
			batchExecutor.submit( deployServiceJobs ) ;

		}

		resultsJson.put( "jobsOperations", operationsCount ) ;
		resultsJson.put( "jobsCount", jobCount ) ;
		resultsJson.put( "jobsRemaining", jobCount ) ;

		return resultsJson ;

	}

	@Inject
	FileRequests fileRequests ;
	public final static String DEPLOY_PROGRESS_URL = "/query/deployProgress" ;

	@RequestMapping ( value = {
			DEPLOY_PROGRESS_URL
	} , produces = MediaType.APPLICATION_JSON_VALUE )
	@ResponseBody
	public JsonNode deployProgress (
										@RequestParam ( CsapCore.HOST_PARAM ) String hostName ,
										@RequestParam ( CsapCore.SERVICE_PORT_PARAM ) String serviceName_port ,
										@RequestParam ( FileRequests.LOG_FILE_OFFSET_PARAM ) long offsetLong )
		throws IOException {

		var serviceName = serviceName_port ;

		if ( serviceName.indexOf( "_" ) != -1 ) {

			serviceName = serviceName.substring( 0, serviceName.indexOf( "_" ) ) ;

		}

		String fromFolder = "//" + serviceName + ServiceOsManager.DEPLOY_OP + ".log" ;
		JsonNode progress = null ;

		long bufferSize = 100 * 1024 ;

		if ( csapApp.isAdminProfile( ) ) {

			MultiValueMap<String, String> urlVariables = new LinkedMultiValueMap<String, String>( ) ;

			urlVariables.set( CsapCore.SERVICE_PORT_PARAM, serviceName_port ) ;
			urlVariables.set( FileRequests.LOG_FILE_OFFSET_PARAM, Long.toString( offsetLong ) ) ;
			String url = CsapCoreService.SERVICE_URL + DEPLOY_PROGRESS_URL ;
			List<String> hosts = new ArrayList<>( ) ;
			hosts.add( hostName ) ;
			progress = serviceOsManager
					.remoteAgentsStateless( hosts, url, urlVariables )
					.get( hostName ) ;

		} else {

			File targetFile = csapApp.getRequestedFile( fromFolder, serviceName_port, false ) ;
			logger.debug( "Getting progress from: {}", targetFile.getAbsolutePath( ) ) ;
			progress = fileRequests.readFileChanges( bufferSize, offsetLong, targetFile ) ;

		}

		return progress ;

	}

	@Inject
	private StandardPBEStringEncryptor encryptor ;

	public static final String REBUILD_URL = "/rebuildServer" ;

	@RequestMapping ( {
			REBUILD_URL
	} )
	public ObjectNode deployService (
										@RequestParam ( CsapCore.SERVICE_PORT_PARAM ) ArrayList<String> services ,
										@RequestParam ( CsapCore.HOST_PARAM ) ArrayList<String> primaryHost ,

										// required parameters
										String scmUserid ,
										String scmPass ,
										String scmBranch ,

										@RequestParam ( required = false ) String commandArguments ,
										@RequestParam ( required = false ) ArrayList<String> targetScpHosts ,
										@RequestParam ( required = false ) String hotDeploy ,
										@RequestParam ( required = false ) String mavenDeployArtifact ,
										@RequestParam ( required = false , defaultValue = "true" ) boolean doEncrypt ,
										@RequestParam ( required = false ) String scmCommand ,
										HttpServletRequest request )
		throws IOException {

		String uiUser = securitySettings.getRoles( ).getUserIdFromContext( ) ;

		logger.info( CSAP.buildDescription(
				"Deploy Artifact",
				"uiUser", uiUser,
				"services", services,
				"mavenDeployArtifact", mavenDeployArtifact,
				"primaryHost", primaryHost,
				"targetScpHosts", targetScpHosts,
				"commandArguments", commandArguments,
				"hotDeploy", hotDeploy,
				"scmUserid", scmUserid,
				"scmBranch", scmBranch ) ) ;

		ObjectNode resultsJson ;
		String sourcePassword = encryptor.encrypt( scmPass ) ; // immediately
		// encrypt
		// pass

		if ( ! doEncrypt ||
				( Application.isRunningOnDesktop( ) && csapApp.isAdminProfile( ) ) ) {

			sourcePassword = scmPass ;

		}

		if ( csapApp.isAdminProfile( ) ) {

			List<String> allHostsPrimaryFirst = new ArrayList<>( ) ;
			allHostsPrimaryFirst.addAll( primaryHost ) ;

			if ( targetScpHosts != null ) {

				allHostsPrimaryFirst.addAll( targetScpHosts ) ;

			}

			resultsJson = serviceCommands.deployRemoteRequests(
					"ms" + System.currentTimeMillis( ),
					uiUser, services, allHostsPrimaryFirst,
					scmUserid, scmPass, scmBranch,
					commandArguments, hotDeploy,
					mavenDeployArtifact, scmCommand,
					null,
					csapApp.rootProjectEnvSettings( ).getAgentUser( ),
					csapApp.rootProjectEnvSettings( ).getAgentPass( ) ) ;

		} else {

			resultsJson = serviceCommands.deployRequest(
					uiUser, null,
					"ms" + System.currentTimeMillis( ),
					services,
					scmUserid, sourcePassword, scmBranch,
					commandArguments,
					hotDeploy, mavenDeployArtifact, scmCommand,
					null,
					null ) ;

		}

		return resultsJson ;

	}

	@CsapDoc ( notes = {
			"toggles processing - the current is kept running"
	} , linkTests = {
			"toggle processing"
	} )
	@PostMapping ( "/deployments/pause" )
	public ObjectNode deploymentsPause (
											@RequestParam ( CsapCore.HOST_PARAM ) ArrayList<String> hosts ) {

		String uiUser = securitySettings.getRoles( ).getUserIdFromContext( ) ;

		ObjectNode result ;

		if ( csapApp.isAdminProfile( ) ) {

			result = serviceCommands.toggleRemoteDeploymentProcessing(
					uiUser, hosts,
					csapApp.rootProjectEnvSettings( ).getAgentUser( ),
					csapApp.rootProjectEnvSettings( ).getAgentPass( ) ) ;

		} else {

			result = serviceCommands.toggleDeploymentProcessing( uiUser, null ) ;

		}

		return result ;

	}

	// , consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE

	@CsapDoc ( notes = {
			"Cancel all queued jobs; current job completes"
	} , linkTests = {
			"cancel jobs"
	} )
	@DeleteMapping ( value = "/deployments" , consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE )
	public ObjectNode deploymentsClear (
											@RequestParam ( CsapCore.HOST_PARAM ) ArrayList<String> hosts ) {

		String uiUser = securitySettings.getRoles( ).getUserIdFromContext( ) ;

		ObjectNode result ;

		if ( csapApp.isAdminProfile( ) ) {

			result = serviceCommands.deleteRemoteDeployments(
					uiUser, hosts,
					csapApp.rootProjectEnvSettings( ).getAgentUser( ),
					csapApp.rootProjectEnvSettings( ).getAgentPass( ) ) ;

		} else {

			result = serviceCommands.deleteDeployments( uiUser, null ) ;

		}

		return result ;

	}

	@PostMapping ( "/runServiceJob" )
	public ObjectNode runJob (
								@RequestParam ( value = CsapCore.HOST_PARAM , required = false ) ArrayList<String> hosts ,
								@RequestParam ( CsapCore.SERVICE_PORT_PARAM ) ArrayList<String> services ,
								@RequestParam ( "jobToRun" ) String jobToRun ,
								@RequestParam ( "jobParameters" ) String jobParameters ,
								HttpServletRequest request )
		throws Exception {

		logger.info( "User: {}, Service(s): {}, Host(s): {}, \n\t job: {}, \n\t jobParameters: {}",
				getUser( null ), services, hosts, jobToRun, jobParameters ) ;

		String uiUser = securitySettings.getRoles( ).getUserIdFromContext( ) ;

		ObjectNode result ;

		if ( csapApp.isAdminProfile( ) ) {

			result = serviceCommands.runRemoteJob(
					services.get( 0 ), jobToRun, jobParameters,
					uiUser, hosts,
					csapApp.rootProjectEnvSettings( ).getAgentUser( ),
					csapApp.rootProjectEnvSettings( ).getAgentPass( ) ) ;

		} else {

			result = serviceCommands.runJob( services.get( 0 ), jobToRun, jobParameters, uiUser, null ) ;

		}

		return result ;

	}

	public static final String START_URL = "/startServer" ;

	@RequestMapping ( START_URL )
	public ObjectNode startServer (
									@RequestParam ( CsapCore.SERVICE_PORT_PARAM ) ArrayList<String> services ,
									@RequestParam ( CsapCore.HOST_PARAM ) ArrayList<String> hosts ,
									@RequestParam ( required = false ) String commandArguments ,
									@RequestParam ( required = false ) String hotDeploy ,
									@RequestParam ( required = false ) String startClean ,
									@RequestParam ( required = false ) String noDeploy ,
									HttpServletRequest request )
		throws IOException {

		String uiUser = securitySettings.getRoles( ).getUserIdFromContext( ) ;

		logger.info( CSAP.buildDescription(
				"Start Service",
				"uiUser", uiUser,
				"services", services,
				"hosts", hosts,
				"commandArguments", commandArguments,
				"hotDeploy", hotDeploy,
				"startClean", startClean,
				"noDeploy", noDeploy ) ) ;

		ObjectNode resultsJson ;

		if ( csapApp.isAdminProfile( ) ) {

			resultsJson = serviceCommands.startRemoteRequests(
					uiUser, services, hosts,
					commandArguments,
					hotDeploy, startClean, noDeploy,
					csapApp.rootProjectEnvSettings( ).getAgentUser( ),
					csapApp.rootProjectEnvSettings( ).getAgentPass( ),
					null ) ;

		} else {

			resultsJson = serviceCommands.startRequest(
					uiUser, services,
					commandArguments,
					hotDeploy, startClean, noDeploy,
					null,
					null ) ;

		}

		return resultsJson ;

	}

	@PostMapping ( value = "/stopServer" , produces = MediaType.APPLICATION_JSON_VALUE )
	public JsonNode stopServer (
									@RequestParam ( CsapCore.SERVICE_PORT_PARAM ) ArrayList<String> services ,
									@RequestParam ( value = CsapCore.HOST_PARAM , required = false ) ArrayList<String> hosts ,
									HttpServletRequest request ) {

		String uiUser = securitySettings.getRoles( ).getUserIdFromContext( ) ;

		logger.info(
				"User: {},  hosts: {}, services: {}",
				uiUser, hosts, services ) ;

		ObjectNode stopResultReport ;

		if ( csapApp.isAdminProfile( ) ) {

			stopResultReport = serviceCommands.stopRemoteRequests(
					uiUser, services, hosts,
					csapApp.rootProjectEnvSettings( ).getAgentUser( ),
					csapApp.rootProjectEnvSettings( ).getAgentPass( ) ) ;

		} else {

			stopResultReport = serviceCommands.stopRequest(
					uiUser, services,
					null ) ;

		}

		// logger.info( "User: " + securitySettings.getRoles().getUserIdFromContext() +
		// " hostName : "
		// + hosts + " Services: " + Arrays.toString( svcNameArray ) ) ;
		//
		// ObjectNode resultsJson = jacksonMapper.createObjectNode() ;
		// ObjectNode stopResults = jacksonMapper.createObjectNode() ;
		//
		// if ( csapApp.isAdminProfile() ) {
		//
		// MultiValueMap<String, String> urlVariables = new LinkedMultiValueMap<String,
		// String>() ;
		//
		// urlVariables.set( "serviceName", svcNameArray[0] ) ;
		// urlVariables.set( "requestUser", CsapUser.currentUsersID() ) ;
		//
		// String url = CsapCoreService.API_AGENT_URL + AgentApi.STOP_SERVICES_URL ;
		//
		// stopResults = serviceOsManager.remoteAgentsApi(
		// csapApp.rootProjectEnvSettings().getAgentUser(),
		// csapApp.rootProjectEnvSettings().getAgentPass(),
		// hosts,
		// url,
		// urlVariables ) ;
		//
		// } else {
		//
		// for ( int i = 0; i < svcNameArray.length; i++ ) {
		//
		// var serviceName = svcNameArray[i] ;
		// var results = serviceOsManager.stopService( serviceName,
		// CsapUser.currentUsersID() ) ;
		// stopResults.put( hosts.get( 0 ), results ) ;
		//
		// }
		//
		// }
		//
		// resultsJson.set( "clusteredResults", stopResults ) ;
		//
		// logger.info( "Completed" ) ;

		return stopResultReport ;

	}

	@PostMapping ( value = "/reImage" , produces = MediaType.APPLICATION_JSON_VALUE )
	public ObjectNode reImage (
								@RequestParam ( value = CsapCore.HOST_PARAM , required = false ) ArrayList<String> hosts ,
								@RequestParam ( value = CsapCore.SERVICE_PORT_PARAM , required = false ) String[] svcNameArray ,
								HttpServletRequest request )
		throws IOException {

		logger.info( "\n\t hosts: {}, \n\t services: {}", hosts, Arrays.toString( svcNameArray ) ) ;

		ObjectNode resultsJson = jacksonMapper.createObjectNode( ) ;

		if ( csapApp.isAdminProfile( ) ) {

			MultiValueMap<String, String> urlVariables = new LinkedMultiValueMap<String, String>( ) ;

			if ( svcNameArray != null && svcNameArray.length > 0 ) {

				for ( String service : svcNameArray ) {

					if ( service.trim( ).length( ) != 0 ) {

						urlVariables.add( CsapCore.SERVICE_PORT_PARAM, service ) ;

					}

				}

			}

			String url = CsapCoreService.SERVICE_URL + "/reImage" ;

			if ( hosts != null ) {

				resultsJson = serviceOsManager.remoteAgentsUsingUserCredentials( hosts, url, urlVariables, request ) ;

			} else {

				resultsJson.put( CsapCore.CONFIG_PARSE_ERROR, " - Failed to find hostName: " + hosts ) ;

			}

			return resultsJson ;

		}

		// Use file to trigger reImage process
		serviceOsManager.getReImageFile( ).createNewFile( ) ;

		// pkill any middleware instances specified
		if ( svcNameArray != null && svcNameArray.length > 0 ) {

			for ( ServiceInstance instance : csapApp.getServicesOnHost( ) ) {

				if ( instance.getUser( ) != null && ! instance.getUser( ).equals( csapApp.getAgentRunUser( ) )
						&& csapApp.isRunningAsRoot( ) ) {

					for ( String sevicePort : svcNameArray ) {

						if ( sevicePort.equals( instance.getServiceName_Port( ) ) ) {

							logger.info( "pkill on user: " + instance.getUser( ) ) ;

							List<String> parmList = Arrays.asList( "bash", "-c",
									"sudo /usr/bin/pkill -9 -u " + instance.getUser( ) ) ;
							// osCommandRunner.executeString(parmList);
							resultsJson.put( "pkill", osCommandRunner
									.executeString( parmList, csapApp.getCsapInstallFolder( ),
											null, null, 600, 1, null ) ) ;
							break ;

						}

					}

				}

			}

		}

		// Now clean up the deploy artifacts, copying back in CsAgent
		List<String> parmList = Arrays.asList(
				"bash",
				"-c",
				"mv CsAgent* .. ; "
						+ "rm -rf * ; "
						+ "mv ../CsAgent* . ;" ) ;

		sendCsapEvent( "Host ReImage", parmList.toString( ) ) ;
		// osCommandRunner.executeString(parmList);
		osCommandRunner.executeString( parmList,
				csapApp.getCsapPackageFolder( ), null, null, 600, 1, null ) ;

		String svcName = CsapCore.AGENT_ID ;
		ArrayList<String> params = new ArrayList<String>( ) ;

		params.add( "-cleanType" ) ;
		params.add( "super" ) ;

		resultsJson.put( svcName,
				serviceOsManager.run_service_script( securitySettings.getRoles( ).getUserIdFromContext( ),
						ServiceOsManager.KILL_FILE, svcName,
						params,
						null,
						null ) ) ;

		return resultsJson ;

	}

	@RequestMapping ( value = "/batchKill" , produces = MediaType.APPLICATION_JSON_VALUE )
	public ObjectNode killBatch (
									@RequestParam ( CsapCore.SERVICE_PORT_PARAM ) ArrayList<String> services ,
									@RequestParam ( CsapCore.HOST_PARAM ) ArrayList<String> hosts ,
									@RequestParam ( defaultValue = "" ) String clean ,
									HttpServletRequest request ) {

		String uiUser = securitySettings.getRoles( ).getUserIdFromContext( ) ;

		logger.info( CSAP.buildDescription(
				"Batch Kill",
				"uiUser", uiUser,
				"services", services,
				"hosts", hosts,
				"clean", clean ) ) ;

		ObjectNode resultsJson = jacksonMapper.createObjectNode( ) ;

		List<String> requestedNames = services ;

		createBatchThreadPool( ) ;
		resultsJson.put( "result", "Batch kill has been scheduled." ) ;
		resultsJson.put( "hosts", hosts.size( ) ) ;
		resultsJson.put( HostKeys.services.json( ), services.size( ) ) ;
		resultsJson.put( "parallelRequests",
				csapApp.rootProjectEnvSettings( ).getNumberWorkerThreads( ) ) ;

		ObjectNode hostMap = resultsJson.putObject( "hostInfo" ) ;
		int operationCount = 0 ;
		int jobCount = 0 ;

		for ( String batchHostTarget : hosts ) {

			ObjectNode hostInfo = hostMap.putObject( batchHostTarget ) ;

			List<String> batchServices = csapApp
					.getServicesOnTargetHost( batchHostTarget )
					.stream( )
					.filter( instance -> requestedNames.contains( instance.getName( ) ) )
					.sorted( comparing( ServiceInstance::startOrder ).reversed( ) )
					.map( ServiceInstance::getServiceName_Port )
					.collect( Collectors.toList( ) ) ;

			logger.info( "Services original order: {} \n\t sorted: {}", services, batchServices ) ;
			operationCount += batchServices.size( ) ;

			if ( batchServices.size( ) == 0 ) {

				hostInfo.put( "info", "Skipping - no services on host" ) ;
				continue ;

			} else {

				if ( batchServices.size( ) != requestedNames.size( ) ) {

					hostInfo.put( "info",
							"Scheduling services (host filtered)" ) ;

				} else {

					hostInfo.put( "info", "Scheduling services" ) ;

				}

				ArrayNode responseServices = hostInfo.putArray( HostKeys.services.json( ) ) ;

				for ( String svc : batchServices ) {

					responseServices.add( svc ) ;

				}

			}

			jobCount++ ;
			Runnable killTask = ( ) -> {

				logger.debug( "Batch Delete: {}", batchHostTarget ) ;

				try {

					ObjectNode clusterResponse ;

					if ( csapApp.isAdminProfile( ) ) {

						clusterResponse = serviceCommands.killRemoteRequests(
								uiUser,
								batchServices,
								Arrays.asList( batchHostTarget ), clean, "keepLogs",
								csapApp.rootProjectEnvSettings( ).getAgentUser( ),
								csapApp.rootProjectEnvSettings( ).getAgentPass( ) ) ;

					} else {

						clusterResponse = serviceCommands.killRequest(
								uiUser, batchServices, clean, "keepLogs", null ) ;

					}

					logger.debug( "{} Completed ", batchHostTarget ) ;
					logger.debug( "Results: \n {}",
							jacksonMapper.writerWithDefaultPrettyPrinter( ).writeValueAsString( clusterResponse ) ) ;

				} catch ( Throwable t ) {

					logger.error( "{} kill failed: \n", batchHostTarget, t ) ;

				}

			} ;
			batchExecutor.submit( killTask ) ;

		}

		resultsJson.put( "jobsOperations", operationCount ) ;
		resultsJson.put( "jobsCount", jobCount ) ;
		resultsJson.put( "jobsRemaining", jobCount ) ;

		return resultsJson ;

	}

	BasicThreadFactory batchFactory = new BasicThreadFactory.Builder( )
			.namingPattern( "CsapBatchThread-%d" )
			.daemon( true )
			.priority( Thread.MAX_PRIORITY )
			.build( ) ;

	final BlockingQueue<Runnable> batchQueue = new ArrayBlockingQueue<>( 1000 ) ;
	private ExecutorService batchExecutor = null ;

	private void createBatchThreadPool ( ) {

		if ( batchExecutor == null ) {

			logger.info( "Creating batch deployment thread" ) ;

			// int numThreads = csapApp.lifeCycleSettings().getNumberWorkerThreads() ;
			// only doing 1 at a time - this is a submit job, not the actual execution.
			int numThreads = 1 ;
			batchExecutor = new ThreadPoolExecutor( numThreads, numThreads,
					0L, TimeUnit.MILLISECONDS,
					batchQueue, batchFactory ) ;

			// batchExecutor = Executors.newFixedThreadPool(
			// 1, batchFactory );
		}

	}

	@RequestMapping ( value = "/batchJobs" , produces = MediaType.APPLICATION_JSON_VALUE )
	public ObjectNode batchJobs ( ) {

		// logger.info("got request");
		ObjectNode jobsNode = jacksonMapper.createObjectNode( ) ;

		jobsNode.put( "jobsRemaining", batchQueue.size( ) ) ;

		jobsNode.put( "tasksRemaining", opsQueued( ) ) ;

		return jobsNode ;

	}

	private int opsQueued ( ) {

		if ( csapApp.isAdminProfile( ) ) {

			return csapApp.getHostStatusManager( ).totalOpsQueued( ) ;

		} else {

			return serviceOsManager.getOpsQueued( ) ;

		}

	}

	public static final String KILL_URL = "/killServer" ;

	@RequestMapping ( KILL_URL )
	public ObjectNode killServer (
									@RequestParam ( CsapCore.SERVICE_PORT_PARAM ) ArrayList<String> services ,
									@RequestParam ( CsapCore.HOST_PARAM ) ArrayList<String> hosts ,
									@RequestParam ( required = false ) String clean ,
									@RequestParam ( required = false ) String keepLogs ,
									HttpServletRequest request )
		throws IOException {

		String uiUser = securitySettings.getRoles( ).getUserIdFromContext( ) ;

		logger.info( CSAP.buildDescription(
				"Kill Service",
				"uiUser", uiUser,
				"services", services,
				"hosts", hosts,
				"clean", clean,
				"keepLogs", keepLogs ) ) ;

		ObjectNode resultsJson ;

		if ( csapApp.isAdminProfile( ) ) {

			resultsJson = serviceCommands.killRemoteRequests(
					uiUser,
					services, hosts,
					clean, keepLogs,
					csapApp.rootProjectEnvSettings( ).getAgentUser( ),
					csapApp.rootProjectEnvSettings( ).getAgentPass( ) ) ;

		} else {

			resultsJson = serviceCommands.killRequest( uiUser, services, clean, keepLogs, null ) ;

		}

		return resultsJson ;

	}

	private String getUser ( String ssoCookie ) {

		String user = "batch" ;

		if ( ssoCookie == null ) {

			user = securitySettings.getRoles( ).getUserIdFromContext( ) ;

		}

		return user ;

	}

	/**
	 *
	 *
	 * trigger a mvn verify in the background via nohup. Should be configured to run
	 * jmeter via pom.xml plgun
	 *
	 * @param hostNameArray
	 * @param svcNameArray
	 * @param requireXml
	 * @param request
	 * @param response
	 * @throws IOException
	 */
	@PostMapping ( value = "/jmeter" , produces = MediaType.APPLICATION_JSON_VALUE )
	public ObjectNode jmeter (
								@RequestParam ( value = CsapCore.HOST_PARAM , required = false ) ArrayList<String> hosts ,
								@RequestParam ( CsapCore.SERVICE_PORT_PARAM ) String[] svcNameArray ,
								HttpServletRequest request ) {

		logger.info( "hostName : " + hosts + " svcName count: "
				+ svcNameArray.length ) ;

		ObjectNode resultsJson = jacksonMapper.createObjectNode( ) ;

		if ( csapApp.isAdminProfile( ) ) {

			MultiValueMap<String, String> urlVariables = new LinkedMultiValueMap<String, String>( ) ;

			for ( String service : svcNameArray ) {

				urlVariables.add( CsapCore.SERVICE_PORT_PARAM, service ) ;

			}

			String url = CsapCoreService.SERVICE_URL + "/jmeter" ;

			if ( hosts != null ) {

				resultsJson = serviceOsManager.remoteAgentsUsingUserCredentials( hosts, url, urlVariables, request ) ;

			} else {

				resultsJson.put( CsapCore.CONFIG_PARSE_ERROR, " - Failed to find hostName: " + hosts ) ;

			}

			return resultsJson ;

		}

		for ( int i = 0; i < svcNameArray.length; i++ ) {

			String svcName = svcNameArray[ i ] ;
			ArrayList<String> params = new ArrayList<String>( ) ;

			// check for host
			resultsJson.put( svcName,
					serviceOsManager.run_service_script( securitySettings.getRoles( ).getUserIdFromContext( ),
							MAVEN_VERIFY_FILE,
							svcName, params, null, null ) ) ;

		}

		return resultsJson ;

	}

	@PostMapping ( value = "/purgeDeployCache" , produces = MediaType.APPLICATION_JSON_VALUE )
	public ObjectNode purgeDeployCaches (

											@RequestParam ( CsapCore.SERVICE_PORT_PARAM ) ArrayList<String> services ,
											@RequestParam ( CsapCore.HOST_PARAM ) ArrayList<String> hosts ,
											@RequestParam ( value = "global" , required = false ) String global ,
											HttpServletRequest request ) {

		String uiUser = securitySettings.getRoles( ).getUserIdFromContext( ) ;
		logger.info( "User: {}, services: {}, hosts:{}, global: {}",
				uiUser, services, hosts, global ) ;

		ObjectNode resultsJson = jacksonMapper.createObjectNode( ) ;

		if ( csapApp.isAdminProfile( ) ) {

			MultiValueMap<String, String> urlVariables = new LinkedMultiValueMap<String, String>( ) ;
			urlVariables.put( CsapCore.SERVICE_PORT_PARAM, services ) ;
			// }
			urlVariables.add( "global", global ) ;

			String url = CsapCoreService.SERVICE_URL + "/purgeDeployCache" ;

			resultsJson = serviceOsManager.remoteAgentsUsingUserCredentials(
					hosts, url, urlVariables,
					request ) ;

			return resultsJson ;

		}

		for ( String service : services ) {

			ArrayList<String> params = new ArrayList<String>( ) ;

			if ( global != null ) {

				params.add( "deleteMavenRepo" ) ; // hardcoded in purge script

			}

			resultsJson.put( service,
					serviceOsManager.run_service_script(
							uiUser,
							CLEAN_UP_SCRIPT, service,
							params, null, null ) ) ;

		}

		return resultsJson ;

	}

	@GetMapping ( "/alerts" )
	public ArrayNode alertsFilter (
									String project ,
									String filter ) {

		if ( project == null ) {

			project = csapApp.getActiveProjectName( ) ;

		}
		// ArrayList<String> lifeCycleHostList = csapApp
		// .getLifeCycleToHostMap().get(clusterFilter);

		Project requestedPackage = csapApp.getProject( project ) ;

		ArrayNode serviceReport = jacksonMapper.createArrayNode( ) ;

		var includeKubernetesCheck = false ;

		if ( csapApp.isAdminProfile( ) ) {

			includeKubernetesCheck = true ; // detects k8s crashed processes

		}

		var healthReport = csapApp.healthManager( ).build_health_report(
				ServiceAlertsEnum.ALERT_LEVEL, includeKubernetesCheck,
				requestedPackage ) ;

		JsonNode detailByHost = healthReport.path( HealthManager.HEALTH_DETAILS ) ;

		CSAP.asStream( detailByHost.fieldNames( ) ).forEach( hostName -> {

			CSAP.jsonStream( detailByHost.path( hostName ) )
					.filter( JsonNode::isObject )
					.map( alert -> (ObjectNode) alert )
					.filter( alert -> alert.has( "source" ) )
					.filter( alert -> alert.path( "source" ).asText( "-" ).matches( Matcher.quoteReplacement(
							filter ) ) )
					.filter( alert -> alert.path( "category" ).asText( "none" ).startsWith( "os-process" ) )
					.forEach( alert -> {

						alert.put( "host", hostName ) ;
						serviceReport.add( alert ) ;

					} ) ;

		} ) ;

		return serviceReport ;

	}

	@Inject
	ActiveUsers activeUsers ;

	private String SESSION_EXPIRED = "SessionExpired" ;

	public void isSessionExpired ( HttpSession session ) {

		if ( session == null ) {

			logger.debug( "Bypassing checks because session is null" ) ;
			return ;

		}

		// Hook for checking for expired SSO cookie.
		if ( session.getAttribute( "renew" ) == null ) {

			session.setAttribute( "renew", System.currentTimeMillis( ) ) ;

			// logger.info("session.getAttribute(ServiceRequests.PROGRESS_BUFF"
			// + session.getAttribute(ServiceRequests.PROGRESS_BUFF) ) ;
			if ( session.getAttribute( SESSION_EXPIRED ) == null ) {

				session.setAttribute( SESSION_EXPIRED, new StringBuffer( ) ) ;

			}

		}

		// logger.debug("\n\n ******** session.getAttribute(renew)" +
		// session.getAttribute("renew") + " current: " +
		// System.currentTimeMillis() ) ;
		// hook for expiring sessions. We force SSO validation every hour,
		// but never interupting t
		if ( ( (StringBuffer) session.getAttribute( SESSION_EXPIRED ) ).length( ) == 0
				&& System.currentTimeMillis( ) - ( (long) session.getAttribute( "renew" ) ) > 60 * 60 * 1000 ) {

			// 60*60*1000
			// logger.warn("\n\n **************** Forcing session renew
			// *****************")
			// ;
			session.invalidate( ) ;

		}

	}

	@PostMapping ( "/uploadArtifact" )
	public JsonNode uploadArtifact (
										@RequestParam ( value = "distFile" , required = false ) MultipartFile multiPartFile ,
										@RequestParam ( value = CsapCore.SERVICE_PORT_PARAM , required = false ) String serviceName ,
										@RequestParam ( value = CsapCore.HOST_PARAM , required = true ) String[] hostNameArray ,
										HttpServletRequest request )
		throws IOException {

		logger.debug( "{}: service: {}, multiPartFile: ", Arrays.toString( hostNameArray ), serviceName,
				multiPartFile ) ;

		var resultReport = jacksonMapper.createObjectNode( ) ;
		resultReport.put( "multiPartFile", multiPartFile.getName( ) ) ;
		resultReport.put( "serviceName", serviceName ) ;

		if ( multiPartFile == null
				|| serviceName == null ) {

			resultReport.put( CsapCore.CONFIG_PARSE_ERROR, "Failed to process request" ) ;

			return resultReport ;

		}

		// ServiceInstance serviceInstance = csapApp.getServiceInstanceAnyPackage(
		// serviceNamePort ) ;
		ServiceInstance serviceInstance = csapApp.findFirstServiceInstanceInLifecycle( serviceName ) ;

		StringBuilder results = new StringBuilder( "Received upload for jvm: " + serviceName ) ;

		if ( multiPartFile != null && multiPartFile.getSize( ) != 0 ) {

			File deployFolder = csapApp.getCsapPackageFolder( ) ;

			if ( ! deployFolder.exists( ) ) {

				deployFolder.mkdirs( ) ;

			}

			File deployFile = new File( deployFolder.getCanonicalPath( ), serviceInstance.getDeployFileName( ) ) ;

			results.append( ", uploaded file name: " + multiPartFile.getOriginalFilename( ) ) ;
			results.append( " Size: " + multiPartFile.getSize( ) ) ;

			try {

				multiPartFile.transferTo( deployFile ) ;
				results.append( "\n- File saved to: " + deployFile.getAbsolutePath( ) ) ;

			} catch ( Exception e ) {

				results.append( "\n== " + CsapCore.CONFIG_PARSE_ERROR + " Host "
						+ csapApp.getCsapHostName( ) + ":" + e.getMessage( ) ) ;

			}

			File versionFile = serviceOsManager.createVersionFile(
					csapApp.getDeployVersionFile( serviceInstance ),
					null,
					securitySettings.getRoles( ).getUserIdFromContext( ),
					"User Upload" ) ;

			OutputFileMgr outputFm = new OutputFileMgr( csapApp.getCsapWorkingFolder( ),
					"/" + serviceInstance.getName( ) + ServiceOsManager.DEPLOY_OP ) ;

			List<String> hostsToCopyTo = new ArrayList<String>( Arrays.asList( hostNameArray ) ) ;
			results.append( "\n" ) ;
			results.append(
					serviceOsManager.syncToOtherHosts(
							securitySettings.getRoles( ).getUserIdFromContext( ),
							hostsToCopyTo,
							deployFile.getAbsolutePath( ),
							Application.CSAP_PACKAGES_TOKEN,
							csapApp.getAgentRunUser( ),
							securitySettings.getRoles( ).getUserIdFromContext( ),
							outputFm.getBufferedWriter( ) ) ) ;

			results.append( "\n" ) ;
			results.append(
					serviceOsManager.syncToOtherHosts(
							securitySettings.getRoles( ).getUserIdFromContext( ),
							hostsToCopyTo,
							versionFile.getAbsolutePath( ),
							Application.CSAP_PACKAGES_TOKEN,
							csapApp.getAgentRunUser( ),
							securitySettings.getRoles( ).getUserIdFromContext( ),
							outputFm.getBufferedWriter( ) ) ) ;

			csapEventClient.publishUserEvent(
					serviceInstance.getName( ), securitySettings.getRoles( ).getUserIdFromContext( ),
					"Uploaded deployment", results.toString( ) ) ;

			outputFm.opCompleted( ) ;

		} else {

			logger.error( "Empty File received" ) ;

			if ( multiPartFile != null ) {

				results.append( "<br>ERROR: File  received was size 0" ) ;

			}

		}

		if ( Application.isRunningOnDesktop( ) ) {

			logger.warn( "Sleeping on desktop to similate remote connection speed" ) ;

			try {

				Thread.sleep( 10000 ) ;

			} catch ( InterruptedException e ) {

				// TODO Auto-generated catch block
				e.printStackTrace( ) ;

			}

		}

		results.append( "Completed upload. Click Start to use the new file." ) ;

		resultReport.put( DockerJson.response_plain_text.json( ), results.toString( ) ) ;
		return resultReport ;

	}

	@GetMapping ( value = "/profilerLauncher" )
	public ResponseEntity<StreamingResponseBody> profilerLauncher (
																	@RequestParam ( "jmxPorts" ) String jmxHostPortsSpaceDelimArray ,
																	@RequestParam ( value = "jvisualvm" , required = false ) String jvisualvm ,
																	@RequestParam ( value = CsapCore.SERVICE_PORT_PARAM ) String serviceName_port )
		throws Exception {

		StringBuilder batContents = new StringBuilder( ) ;
		String[] jmxHostPortArray = jmxHostPortsSpaceDelimArray.trim( ).split( " " ) ;

		var firstHost = "nohost" ;

		logger.debug( "{} : jmxHostPortsSpaceDelimArray: {}, length: {}, jvisualvm: {}",
				serviceName_port, jmxHostPortsSpaceDelimArray, jmxHostPortArray.length, jvisualvm ) ;

		// boolean isJmxUsingRmi = true;
		if ( ! serviceName_port.equals( "no" ) ) {

			ServiceInstance serviceDefinition = csapApp
					.getServiceInstanceAnyPackage( serviceName_port ) ;

			if ( serviceDefinition == null ) {

				// isJmxUsingRmi = serviceDefinition.isJmxRmi();
			} else {

				logger.warn( "Did not find a service instance for: " + serviceName_port ) ;

			}

		}

		csapEventClient.publishUserEvent( CsapCore.AGENT_ID, securitySettings.getRoles( ).getUserIdFromContext( ),
				" Java Profiler Launch: " + csapApp.getCsapHostName( ), "no details" ) ;

		batContents.append( "echo %JAVA_HOME% \r\n" ) ;
		batContents
				.append( "echo jdk version is old, download https://visualvm.github.io/ \r\n" ) ;
		batContents
				.append( "echo Update your path variable in windows and mac to include folder containing visualvm \r\n" ) ;
		batContents
				.append( "echo Optional Download topthreads plugin from http://lsd.luminis.nl/top-threads-plugin-for-jconsole \r\n" ) ;
		batContents.append( "echo Tomcat Firewalled ports connection \r\n" ) ;

		if ( jvisualvm != null ) {

			batContents.append( "start visualvm --openjmx  " ) ;

		} else {

			batContents.append( "start jmc --openjmx   " ) ;

			// batContents.append("start jconsole -pluginpath
			// /java/topthreads.jar ");
		}

		for ( int i = 0; i < jmxHostPortArray.length; i++ ) {

			// from csap-eng01:8026 to
			// service:jmx:rmi://csap-eng01:8028/jndi/rmi://csap-eng01:8027/jmxrmi
			String jndiDest = jmxHostPortArray[ i ]
					.substring( 0, jmxHostPortArray[ i ].length( ) - 1 ) + "7" ;
			String firewallDest = jmxHostPortArray[ i ].substring( 0,
					jmxHostPortArray[ i ].length( ) - 1 ) + "8" ;

			// if ( isJmxUsingRmi ) {
			// batContents.append( "service:jmx:rmi://" + firewallDest );
			// batContents.append( "/jndi/rmi://" + jndiDest + "/jmxrmi " );
			// } else {
			String hostPort[] = jmxHostPortArray[ i ].split( ":" ) ;
			firstHost = hostPort[ 0 ] ;
			batContents.append( csapApp.getHostUsingFqdn( firstHost ) + ":" + hostPort[ 1 ] ) ;
			// }

			if ( jvisualvm != null ) {

				// jvisualvm only can launch with 1 url. put the remaining
				// on the comment
				batContents.append( "\r\n REM " ) ;

			}

		}

		batContents.append( "\r\n echo Direct connect port connection\r\n" ) ;
		batContents.append( "REM start jconsole -pluginpath /java/topthreads.jar " ) ;

		for ( int i = 0; i < jmxHostPortArray.length; i++ ) {

			batContents.append( jmxHostPortArray[ i ] ) ;
			batContents.append( " " ) ;

		}

		// HttpHeaders headers = new HttpHeaders();

		// headers.setContentDisposition( ContentDisposition.builder("inline")
		// .filename("peter.bat")
		// .build() );

		StreamingResponseBody stream = out -> {

			out.write( batContents.toString( ).getBytes( ) ) ;

		} ;

		return ResponseEntity
				.ok( )
				.header( HttpHeaders.CONTENT_DISPOSITION, "attachment;filename=" + "jmx" + serviceName_port + "-"
						+ firstHost + ".bat" )
				.contentType( MediaType.APPLICATION_OCTET_STREAM )
				.body( stream ) ;

	}

	final public static String GENERATE_APACHE_MAPPINGS = "/genHttpdWorkers" ;

	@RequestMapping ( value = GENERATE_APACHE_MAPPINGS , produces = MediaType.TEXT_PLAIN_VALUE )
	public String genHttpdWorkers ( HttpServletRequest request , HttpServletResponse response ) {

		logger.debug( " Entering" ) ;
		StringBuilder results = new StringBuilder( ) ;
		results.append( "\n\n ========== Restart httpd process to pick up update ========\n\n" ) ;
		results.append( csapApp.buildHttpdConfiguration( ) ) ;
		results.append( "\n\n ========== Restart httpd process to pick up update ========\n\n" ) ;

		sendCsapEvent( "Httpd config updated", "" ) ;
		return results.toString( ) ;

	}

	@PostMapping ( value = "/undeploy" , produces = MediaType.APPLICATION_JSON_VALUE )
	public ObjectNode undeploy (
									@RequestParam ( value = CsapCore.HOST_PARAM , required = false ) ArrayList<String> hosts ,
									@RequestParam ( CsapCore.SERVICE_PORT_PARAM ) String serviceName ,
									@RequestParam ( "warSelect" ) String warSelect ,
									HttpServletRequest request ,
									HttpServletResponse response ) {

		logger.info( "User: " + securitySettings.getRoles( ).getUserIdFromContext( ) + " hostName : "
				+ hosts + " Services: " + serviceName
				+ " warSelect: " + warSelect ) ;

		ObjectNode resultsJson = jacksonMapper.createObjectNode( ) ;

		if ( serviceName.startsWith( CsapCore.AGENT_NAME ) ) {

			resultsJson.put( serviceName, "Agent does not support undeploys" ) ;
			return resultsJson ;

		}

		if ( csapApp.isAdminProfile( ) ) {

			MultiValueMap<String, String> urlVariables = new LinkedMultiValueMap<String, String>( ) ;
			urlVariables.add( CsapCore.SERVICE_PORT_PARAM, serviceName ) ;

			urlVariables.add( "warSelect", warSelect ) ;

			String url = CsapCoreService.SERVICE_URL + "/undeploy" ;

			if ( hosts != null ) {

				resultsJson = serviceOsManager.remoteAgentsUsingUserCredentials( hosts, url, urlVariables, request ) ;

			} else {

				resultsJson.put( CsapCore.CONFIG_PARSE_ERROR, " - Failed to find hostName: " + hosts ) ;

			}

			return resultsJson ;

		}

		File targetFile = new File( csapApp.getCsapWorkingFolder( ), serviceName
				+ "/webapps/" + warSelect ) ;

		if ( targetFile.exists( ) ) {

			List<String> parmList = new ArrayList<String>( ) ;
			Collections
					.addAll( parmList, "bash", "-c", "rm -rf " + targetFile.getAbsolutePath( ) ) ;

			File workingDir2 = new File( csapApp.getCsapInstallFolder( )
					.getAbsolutePath( ) ) ;
			osCommandRunner.executeString( parmList, workingDir2,
					null, null, 60, 1, null ) ;
			// response.getWriter().print(sourceControlManager.executeShell(parmList,
			// null));

			csapEventClient.publishUserEvent( CsapCore.AGENT_ID,
					securitySettings.getRoles( ).getUserIdFromContext( ),
					" Undeploying: " + targetFile.getAbsolutePath( )
							+ csapApp.getCsapHostName( ),
					warSelect ) ;
			resultsJson.put( serviceName, "removed: " + targetFile.getAbsolutePath( ) ) ;

		} else {

			resultsJson.put( serviceName,
					"ERROR: Did not find deployment file: " + targetFile.getAbsolutePath( ) ) ;

		}

		return resultsJson ;

	}

	@PostMapping ( value = "/updateMotd" , produces = MediaType.APPLICATION_JSON_VALUE )
	public ObjectNode updateMotd (
									@RequestParam ( "motd" ) String motdMessage ,
									@RequestParam ( value = CsapCore.HOST_PARAM , required = false ) ArrayList<String> hosts ,
									HttpServletRequest request ) {

		logger.debug( " motdMessage: {}", motdMessage ) ;

		ObjectNode resultsJson = jacksonMapper.createObjectNode( ) ;

		if ( csapApp.isAdminProfile( ) ) {

			MultiValueMap<String, String> urlVariables = new LinkedMultiValueMap<String, String>( ) ;
			urlVariables.add( "motd", motdMessage ) ;

			String url = CsapCoreService.SERVICE_URL + "/updateMotd" ;

			if ( hosts != null ) {

				resultsJson = serviceOsManager.remoteAgentsUsingUserCredentials( hosts, url, urlVariables, request ) ;

			} else {

				resultsJson.put( CsapCore.CONFIG_PARSE_ERROR, " - Failed to find hostName: " + hosts ) ;

			}

			return resultsJson ;

		}

		csapApp.setMotdMessage( securitySettings.getRoles( ).getUserIdFromContext( ) + ": " + motdMessage ) ;
		csapEventClient.publishUserEvent( CsapCore.AGENT_ID, securitySettings.getRoles( ).getUserIdFromContext( ),
				" updated motd on Host: " + csapApp.getCsapHostName( ), motdMessage ) ;

		resultsJson.put( "results", " updated motd on Host: " + csapApp.getCsapHostName( ) ) ;

		return resultsJson ;

	}

	private void sendCsapEvent ( String commandDesc , String details ) {

		csapEventClient.publishUserEvent( CsapCore.AGENT_ID, securitySettings.getRoles( ).getUserIdFromContext( ),
				commandDesc, details ) ;

	}

}
