package org.csap.agent.ui.windows ;

import java.io.File ;
import java.io.IOException ;
import java.text.SimpleDateFormat ;
import java.time.LocalDateTime ;
import java.time.format.DateTimeFormatter ;
import java.util.Collections ;
import java.util.Date ;
import java.util.HashMap ;
import java.util.Map ;
import java.util.Random ;
import java.util.TreeMap ;
import java.util.concurrent.TimeUnit ;
import java.util.regex.Matcher ;
import java.util.stream.Collectors ;

import javax.inject.Inject ;
import javax.servlet.http.HttpServletRequest ;
import javax.servlet.http.HttpServletResponse ;
import javax.servlet.http.HttpSession ;

import org.apache.commons.lang3.StringUtils ;
import org.csap.agent.ApplicationConfiguration ;
import org.csap.agent.CsapApis ;
import org.csap.agent.CsapConstants ;
import org.csap.agent.container.kubernetes.KubernetesIntegration ;
import org.csap.agent.container.kubernetes.K8 ;
import org.csap.agent.integrations.CsapEvents ;
import org.csap.agent.linux.OsCommandRunner ;
import org.csap.agent.model.Application ;
import org.csap.agent.model.EnvironmentSettings ;
import org.csap.agent.model.ProcessRuntime ;
import org.csap.agent.model.Project ;
import org.csap.agent.model.ServiceAlertsEnum ;
import org.csap.agent.model.ServiceInstance ;
import org.csap.agent.services.OsManager ;
import org.csap.agent.services.ServiceOsManager ;
import org.csap.agent.stats.OsProcessEnum ;
import org.csap.agent.stats.OsSharedEnum ;
import org.csap.agent.stats.service.JmxCommonEnum ;
import org.csap.agent.ui.rest.ApplicationBrowser ;
import org.csap.agent.ui.rest.HostRequests ;
import org.csap.alerts.AlertSettings ;
import org.csap.docs.CsapDoc ;
import org.csap.helpers.CSAP ;
import org.csap.helpers.CsapApplication ;
import org.csap.helpers.CsapRestTemplateFactory ;
import org.csap.integations.CsapInformation ;
import org.csap.security.CsapUser ;
import org.csap.security.config.CsapSecurityRoles ;
import org.csap.security.config.CsapSecuritySettings ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.beans.factory.annotation.Autowired ;
import org.springframework.beans.factory.annotation.Qualifier ;
import org.springframework.context.annotation.Lazy ;
import org.springframework.http.HttpStatus ;
import org.springframework.http.MediaType ;
import org.springframework.http.ResponseEntity ;
import org.springframework.stereotype.Controller ;
import org.springframework.ui.ModelMap ;
import org.springframework.web.bind.annotation.GetMapping ;
import org.springframework.web.bind.annotation.PathVariable ;
import org.springframework.web.bind.annotation.RequestMapping ;
import org.springframework.web.bind.annotation.RequestParam ;
import org.springframework.web.bind.annotation.ResponseBody ;
import org.springframework.web.client.RestTemplate ;
import org.springframework.web.servlet.ModelAndView ;
import org.springframework.web.servlet.view.RedirectView ;

import com.fasterxml.jackson.core.JsonProcessingException ;
import com.fasterxml.jackson.databind.ObjectMapper ;
import com.fasterxml.jackson.databind.node.ArrayNode ;
import com.fasterxml.jackson.databind.node.ObjectNode ;

@Controller
@RequestMapping ( value = CsapConstants.BASE_URL )
@CsapDoc ( title = "CSAP Application Portal" , notes = {
		"CSAP Application Portal provides core application management capabilities, including "
				+ "starting/stoping/deploying services, viewing log files, and much more.",
		"<a class='csap-link' target='_blank' href='https://github.com/csap-platform/csap-core/wiki'>learn more</a>",
		"<img class='csapDocImage' src='CSAP_BASE/images/csapboot.png' />"
				+ "<img class='csapDocImage' src='CSAP_BASE/images/portals.png' />"
} )
public class CorePortals {

	final Logger logger = LoggerFactory.getLogger( getClass( ) ) ;

	@Inject
	public CorePortals (
			CsapApis csapApis,
			HostRequests hostController,
			CsapInformation globalContext,
			CsapEvents csapEventClient ) {

		this.csapApis = csapApis ;
		this.csapInformation = globalContext ;
		this.csapEventClient = csapEventClient ;

		var restTemplateFactory = new CsapRestTemplateFactory(
				csapApis.application( ).getCsapCoreService( ).getSslCertificateUrl( ),
				csapApis.application( ).getCsapCoreService( ).getSslCertificatePass( ) ) ;

		podProxyRestTemplate = restTemplateFactory.buildDefaultTemplate(
				"PodProxyConnections",
				false,
				1, 20, // max per route, max total
				5, 5, // timeouts
				30 + 30 ) ; // idle time

	}

	CsapApis csapApis ;

	CsapInformation csapInformation ;

	@Lazy
	@Autowired ( required = false )
	ApplicationBrowser appBrowser ;

	@Autowired ( required = false )
	CsapSecuritySettings securitySettings ;

	CsapEvents csapEventClient ;

	@GetMapping
	@CsapDoc ( notes = {
			"Default page load for Application. It redirects to the services portal"
	} )
	public String applicationDefault_loadServicesPortal ( HttpServletRequest request ) {

		return "redirect:/app-browser" ;

	}

	@GetMapping ( CsapConstants.TEST_URL )
	public String integrationTestsPage ( ModelMap springViewModelMap , HttpSession session ) {

		appBrowser.setCommonAttributes( springViewModelMap, session ) ;

		String pageName = "agent@" + csapApis.application( ).getCsapHostName( ) ;

		if ( csapApis.application( ).isAdminProfile( ) ) {

			pageName = "admin@" + csapApis.application( ).getCsapHostName( ) ;

		}

		springViewModelMap.addAttribute( "pageName", pageName ) ;
		springViewModelMap.addAttribute( "host", csapApis.application( ).getCsapHostName( ) ) ;

		return "IntegrationTests" ;

	}

	@GetMapping ( "/health" )
	@CsapDoc ( notes = {
			"The real time meters dashboard enables teams to quickly view the last collected operational "
					+ "metrics for each host. This enables teams to quickly identify if one or more service instances "
					+ "has encounterd a performance issue or functional escape."
	} )
	public String health ( ModelMap modelMap , HttpSession session ) {

		if ( logger.isDebugEnabled( ) ) {

			logger.debug( " Entered" ) ;

		}

		AlertSettings alertSettings = csapApis.application( ).getCsapCoreService( ).getAlerts( ) ;
		HashMap<String, String> settings = new HashMap<>( ) ;
		settings.put( "Health Report Interval", alertSettings.getReport( ).getIntervalSeconds( ) + " seconds" ) ;
		settings.put( "Maximum items to store", alertSettings.getRememberCount( ) + "" ) ;
		settings.put( "Email Notifications", alertSettings.getNotify( ).toString( ) ) ;
		settings.put( "Alert Throttles", alertSettings.getThrottle( ).toString( ) ) ;

		modelMap.addAttribute( "csapPageLabel", "Service Health Reports" ) ;
		modelMap.addAttribute( "settings", settings ) ;

		Map<String, Map<String, String>> healthUrlsByService = csapApis.application( ).healthManager( )
				.buildHealthUrls( ) ;

		modelMap.addAttribute( "healthUrlsByServiceByInstance", healthUrlsByService ) ;

		setCommonAttributes( modelMap, session ) ;

		return "health/alertsPortal" ;

	}

	@CsapDoc ( notes = "Health data showing alerts. Default hours is 4 - and testing is 0" , baseUrl = "/csap" )
	@GetMapping ( value = "/health/alerts" , produces = MediaType.APPLICATION_JSON_VALUE )
	@ResponseBody
	public ObjectNode report (
								@RequestParam ( value = "hours" , required = false , defaultValue = "4" ) int hours ,
								@RequestParam ( value = "testCount" , required = false , defaultValue = "0" ) int testCount ) {

		ObjectNode results = jacksonMapper.createObjectNode( ) ;

		ArrayNode alertsTriggered ;

		if ( ! csapApis.application( ).isAdminProfile( ) ) {

			alertsTriggered = jacksonMapper.createArrayNode( ) ;
			ObjectNode t = alertsTriggered.addObject( ) ;
			t.put( "ts", System.currentTimeMillis( ) ) ;

			// String foundTime = LocalDateTime.now().format(
			// DateTimeFormatter.ofPattern( "HH:mm:ss , MMM d" ) ) ;
			t.put( "id", "Agent mode" ) ;
			t.put( "type", "." ) ;
			t.put( "time", "." ) ;
			t.put( "host", "." ) ;
			t.put( "service", "." ) ;
			t.put( "description", "Switch to admin to view aggregated" ) ;

		} else if ( testCount == 0 ) {

			alertsTriggered = csapApis.application( ).getHostStatusManager( ).getAllAlerts( ) ;

		} else {

			results.put( "testCount", testCount ) ;
			alertsTriggered = jacksonMapper.createArrayNode( ) ;
			Random rg = new Random( ) ;

			for ( int i = 0; i < testCount; i++ ) {

				ObjectNode t = alertsTriggered.addObject( ) ;
				long now = System.currentTimeMillis( ) ;
				long itemTimeGenerated = now - rg.nextInt( (int) TimeUnit.DAYS.toMillis( 1 ) ) ;
				t.put( "ts", itemTimeGenerated ) ;

				// String foundTime = LocalDateTime.now().format(
				// DateTimeFormatter.ofPattern( "HH:mm:ss , MMM d" ) ) ;
				t.put( "id", "test.simon." + rg.nextInt( 20 ) ) ;
				t.put( "type", "type" + rg.nextInt( 20 ) ) ;
				t.put( "time", getFormatedTime( itemTimeGenerated ) ) ;
				t.put( "host", "testHost" + rg.nextInt( 10 ) ) ;
				t.put( "service", "testService" + rg.nextInt( 10 ) ) ;
				t.put( "description", "description" + rg.nextInt( 20 ) ) ;

			}

		}

		ArrayNode filteredByHoursShow = alertsTriggered ;

		if ( hours > 0 ) {

			ArrayNode filteredByHours = jacksonMapper.createArrayNode( ) ;

			long now = System.currentTimeMillis( ) ;
			alertsTriggered.forEach( item -> {

				if ( item.has( "ts" ) ) {

					long itemTime = item.get( "ts" ).asLong( ) ;

					if ( ( now - itemTime ) < TimeUnit.HOURS.toMillis( hours ) ) {

						filteredByHours.add( item ) ;

					}

				}

			} ) ;

			filteredByHoursShow = filteredByHours ;

		}

		results.put( "storeTotal", alertsTriggered.size( ) ) ;
		results.put( "filterTotal", filteredByHoursShow.size( ) ) ;
		results.set( "triggered", filteredByHoursShow ) ;

		return results ;

	}

	SimpleDateFormat timeDayFormat = new SimpleDateFormat( "HH:mm:ss , MMM d" ) ;

	private String getFormatedTime ( long tstamp ) {

		Date d = new Date( tstamp ) ;
		return timeDayFormat.format( d ) ;

	}

	@RequestMapping ( "/batchDialog" )
	public String applicationBatchDeploymentDashboard (
														@RequestParam ( value = CsapConstants.PROJECT_PARAMETER , required = false ) String projectName ,
														ModelMap modelMap ,
														HttpSession session ) {

		if ( logger.isDebugEnabled( ) ) {

			logger.debug( " Entered" ) ;

		}

		if ( StringUtils.isEmpty( projectName ) ) {

			projectName = csapApis.application( ).getActiveProjectName( ) ;

		}

		setCommonAttributes( modelMap, session ) ;

		// addSelectedReleasePackage( session, modelMap, projectName ) ;

		Project project = csapApis.application( ).getProject( projectName ) ;

		Map<String, String> servicesToType = project
				.serviceInstancesInCurrentLifeByName( ).values( )
				.stream( )
				.filter( listOfInstances -> listOfInstances.size( ) > 0 )

				.filter( listOfInstances -> {

					ServiceInstance service = listOfInstances.get( 0 ) ;

					if ( service.is_os_process_monitor( ) )
						return false ;

					return true ;

				} )
				.collect( Collectors.toMap(
						listOfInstances -> listOfInstances.get( 0 ).getName( ),
						listOfInstances -> listOfInstances.get( 0 ).getClusterType( ).getJson( ),
						( a , b ) -> a,
						( ) -> new TreeMap<String, String>( String.CASE_INSENSITIVE_ORDER ) ) ) ;

		modelMap.addAttribute( "serviceNames", servicesToType ) ;

		modelMap.addAttribute( "hostNames", csapApis.application( ).sortedHostsInLifecycle( project ) ) ;

		if ( ! csapApis.application( ).isAdminProfile( ) ) {

			servicesToType = csapApis.application( )
					.servicesOnHost( )
					.filter( service -> {

						if ( service.is_os_process_monitor( ) )
							return false ;

						return true ;

					} )
					.collect( Collectors.toMap(
							ServiceInstance::getName,
							instance -> instance.getClusterType( ).getJson( ),
							( a , b ) -> a, // merge function should never be used
							( ) -> new TreeMap<String, String>( String.CASE_INSENSITIVE_ORDER ) ) ) ; // want them
																										// sorted

			modelMap.addAttribute( "serviceNames", servicesToType ) ;
			modelMap.addAttribute( "hostNames", csapApis.application( ).getCsapHostName( ) ) ;

		}

		var clusterToHostsWithKubernetesAdded = project.getClustersToHostMapInCurrentLifecycle( ) ;
		var clusterToServices = project.getClustersToServicesMapInCurrentLifecycle( ) ;
		logger.debug( "clusterToServices: {} ", clusterToServices ) ;

		clusterToHostsWithKubernetesAdded.entrySet( ).stream( )
				.filter( clusterHostsEntry -> clusterHostsEntry.getValue( ).isEmpty( ) )
				.filter( clusterHostsEntry -> clusterToServices.containsKey( clusterHostsEntry.getKey( ) ) )
				.forEach( clusterHostsEntry -> {

					var services = clusterToServices.get( clusterHostsEntry.getKey( ) ) ;

					var serviceInstance = csapApis.application( ).findFirstServiceInstanceInLifecycle( services.get(
							0 ) ) ;
					var optionalInstance = project.getServiceInstances( services.get( 0 ) ).findFirst( ) ;

					if ( optionalInstance.isPresent( ) ) {

						serviceInstance = optionalInstance.get( ) ;

					}

					if ( serviceInstance.is_cluster_kubernetes( ) ) {

						clusterHostsEntry.getValue( ).add( serviceInstance.getKubernetesPrimaryMaster( ) ) ;

					}

					// clusterHostsEntry.getValue().add( "csap-dev01" ) ;
				} ) ;

		modelMap.addAttribute( "clusters", clusterToHostsWithKubernetesAdded ) ;
		modelMap.addAttribute( "clustersToType", project.getClustersToTypeInCurrentLifecycle( ) ) ;

		// "/CsAgent/file/FileMonitor?isLogFile=true&" );
		ServiceInstance adminInstance = csapApis.application( ).findServiceByNameOnCurrentHost(
				CsapConstants.ADMIN_NAME ) ;

		if ( adminInstance == null ) {

			adminInstance = csapApis.application( ).getLocalAgent( ) ;

		}

		try {

			modelMap.addAttribute( "clusterHostJson",
					jacksonMapper.writeValueAsString( clusterToHostsWithKubernetesAdded ) ) ;

		} catch ( JsonProcessingException e ) {

			logger.error( "Failed to generate host mapping", CSAP.buildCsapStack( e ) ) ;

		}

		try {

			logger.debug( "clusterServiceJson: {}", project.getClustersToServicesMapInCurrentLifecycle( ) ) ;
			modelMap.addAttribute( "clusterServiceJson",
					jacksonMapper.writeValueAsString( project.getClustersToServicesMapInCurrentLifecycle( ) ) ) ;

		} catch ( JsonProcessingException e ) {

			logger.error( "Failed to generate host mapping", e ) ;

		}

		return "/app-browser/service-dialog-batch" ;

	}

//	void addSelectedReleasePackage ( HttpSession session , ModelMap modelMap , String releasePackage ) {
//		modelMap.addAttribute( "selectedRelease",  csapApis.application().getActiveProjectName( ) ) ;
//
//		Project csapProject =  csapApis.application().getActiveProject( ) ;
//		if ( releasePackage != null ) {
//			modelMap.addAttribute( "selectedRelease", releasePackage ) ;
//			csapProject =  csapApis.application().getProject( releasePackage ) ;
//		}
//
//		modelMap.addAttribute( "hosts", csapProject.getHostsCurrentLc( ) ) ;
//
//		String userView = (String) session.getAttribute( releasePackage + DEFAULT_VIEW_ATTRIBUTE ) ;
//		if ( userView == null ) {
//
//			String defaultView =  csapApis.application().getCsapHostEnvironmentName( ) ;
//			String uiView = csapProject.getEnvironmentNameToSettings( )
//					.get(  csapApis.application().getCsapHostEnvironmentName( ) )
//					.getUiDefaultView( ) ;
//
//			// if ( !uiView.equalsIgnoreCase( LifeCycleSettings.ALL_IN_LIFECYCLE ) ) {
//			// defaultView += "-" + uiView ;
//			// }
//
//			userView = defaultView ;
//
//		}
//
//		modelMap.addAttribute( DEFAULT_VIEW_ATTRIBUTE, userView.trim( ) ) ;
//
//		modelMap.addAttribute( "currentLifecycle",  csapApis.application().getCsapHostEnvironmentName( ) ) ;
//	}

	@Autowired
	OsManager osManager ;

	@GetMapping ( "/location/dashboard" )
	public ModelAndView dashboard_secret (
											@RequestParam ( defaultValue = "kubernetes-dashboard" ) String serviceName ,
											@RequestParam ( defaultValue = "" ) String path ,
											@RequestParam ( defaultValue = "true" ) boolean ssl ,
											ModelMap modelMap ,
											HttpSession session ) {

		// issueAudit( "User accessing kubernetes dashboard", "" ) ;
		logger.info( "serviceName: '{}', path: '{}'", serviceName, path ) ;

		String results = "Permission Denied" ;
		String url = "" ;

		if ( securitySettings.getRoles( ).getAndStoreUserRoles( session )
				.contains( CsapSecurityRoles.ADMIN_ROLE ) ) {

			ServiceInstance kublet = csapApis.application( ).kubeletInstance( ) ;

			if ( kublet != null ) {

				File kubeletWorking = kublet.getWorkingDirectory( ) ;
				File tokenScript = new File( kubeletWorking, "scripts/dash-token.sh" ) ;

				results = OsCommandRunner.trimHeader( osManager.runFile( tokenScript ) ) ;

				url = csapApis.kubernetes( )
						.nodePortUrl(
								csapApis.application( ),
								serviceName,
								csapApis.application( ).getCsapHostName( ),
								path,
								ssl ) ;

			}

		}

		modelMap.addAttribute( "token", results ) ;
		modelMap.addAttribute( "dashUrl", url ) ;

		return new ModelAndView( CsapConstants.OS_URL + "/launch-dashboard" ) ;

	}

	private RestTemplate podProxyRestTemplate ;

	@GetMapping ( "/podProxy/{serviceid}" )
	@ResponseBody
	public String podIpProxy (
								@PathVariable String serviceid ,
								HttpServletResponse response ,
								HttpServletRequest request )
		throws Exception {

		// ObjectNode collection = jacksonMapper.createObjectNode() ;
		ObjectNode collection = jacksonMapper.createObjectNode( ) ;

		String results = "" ;

		var debugDetails = "" ;

		try {

			var nonK8Service = csapApis.application( ).findServiceByNameOnCurrentHost( serviceid ) ;
			String configuredUrl ;

			if ( nonK8Service != null ) {

//				configuredUrl = nonK8Service.getUrl() + "/csap/metrics/micrometers" ;
				configuredUrl = nonK8Service.getHttpCollectionSettings( ).path( "httpCollectionUrl" ).asText( ) ;
				configuredUrl = nonK8Service.resolveRuntimeVariables( configuredUrl ) ;
//				if (  Application.isRunningOnDesktop() && serviceid.equals( CsapCore.AGENT_NAME )) {
//					configuredUrl = "http://csap-dev01.***REMOVED***:8011/csap/metrics/micrometers" ;
//				}

			} else {

				logger.debug( "serviceid: {}", serviceid ) ;
				var podDelim = serviceid.lastIndexOf( "-" ) ;
				var serviceName = serviceid.substring( 0, podDelim ) ;
				var podIndex = serviceid.substring( podDelim + 1 ) ;

				logger.debug( "serviceid: {} serviceName: {} podIndex: {}", serviceid, serviceName, podIndex ) ;

				var service = csapApis.application( ).findServiceByNameOnCurrentHost( serviceName ) ;

				var containerStatusReport = service.getContainerStatusList( ).get( Integer.parseInt( podIndex ) - 1 ) ;

//				configuredUrl = service.getHttpCollectionSettings( ).path( "javaCollectionUrl" ).asText( "java-collection-url-not-found" ) ;
				configuredUrl = service.getHttpCollectionSettings( ).path( "httpCollectionUrl" ).asText(
						"http-collection-not-found" ) ;

				configuredUrl = service.resolveRuntimeVariables( configuredUrl ) ;

				configuredUrl = configuredUrl.replaceAll(
						Matcher.quoteReplacement( CsapConstants.K8_POD_IP ),
						containerStatusReport.getPodIp( ) ) ;

			}

			var collectionUrl = configuredUrl ;

			if ( collectionUrl.contains( "?" ) ) {

				collectionUrl = collectionUrl.substring( 0, collectionUrl.indexOf( "?" ) ) ;

			}

			collectionUrl += "?" ;

			for ( String name : Collections.list( request.getParameterNames( ) ) ) {

				collectionUrl += name + "=" + request.getParameter( name ) + "&" ;

			}

			logger.debug( "configuredUrl: {}, collectionUrl: {}", configuredUrl, collectionUrl ) ;

			collection.put( "source", collectionUrl ) ;

			ResponseEntity<String> collectionResponse ;

			if ( Application.isRunningOnDesktop( ) && configuredUrl.startsWith( "classpath" ) ) {
				// File stubResults = new File( getClass()
				// .getResource( httpCollectionUrl.substring(
				// httpCollectionUrl.indexOf( ":" ) + 1 ) )
				// .getFile() );

				String target = configuredUrl.substring( configuredUrl.indexOf( ":" ) + 1 ) ;
				String stubResults = csapApis.application( ).check_for_stub( "", target ) ;

				collectionResponse = new ResponseEntity<String>( stubResults,
						HttpStatus.OK ) ;

			} else {

				debugDetails = "configuredUrl: " + configuredUrl + " collectionUrl: " + collectionUrl ;

				collectionResponse = podProxyRestTemplate.getForEntity( collectionUrl, String.class ) ;

			}

			// collection = (ObjectNode) jacksonMapper.readTree(
			// collectionResponse.getBody() ) ;
			results = collectionResponse.getBody( ) ;

		} catch ( Exception e ) {

			var reason = CSAP.buildCsapStack( e ) ;
			collection.put( "error", "Failed to collect service live report." ) ;
			collection.put( "details", reason ) ;
			results = collection.toString( ) ;

			logger.warn( "{} Failed proxy request: {}  {}", serviceid, debugDetails, reason ) ;

		}

		return results ;

	}

	@GetMapping ( "/location/nodeport" )
	public void nodePortRedirect (
									@RequestParam String serviceName ,
									@RequestParam ( defaultValue = "" ) String path ,
									@RequestParam ( defaultValue = "false" ) boolean ssl ,
									boolean debug ,
									HttpServletResponse response )
		throws Exception {

		String location = csapApis.kubernetes( ).nodePortUrl(
				csapApis.application( ),
				serviceName,
				"$host",
				path,
				ssl ) ;
		logger.info( "Path location: '{}'", location ) ;
		response.setHeader( "Location", location ) ;

		if ( debug ) {

			response.setStatus( 200 ) ;
			response.getWriter( ).println( "debug mode" ) ;

		} else {

			response.setStatus( 302 ) ;

		}

		response.getWriter( ).println( "node port lookup, location: " + location ) ;

	}

	@Inject
	ServiceOsManager serviceOsManager ;

	@GetMapping ( "/location/ingress" )
	public void ingressRedirect (
									@RequestParam String path ,
									@RequestParam ( defaultValue = "false" ) boolean ssl ,
									String serviceName ,
									boolean debug ,
									HttpServletResponse response )
		throws Exception {

		if ( StringUtils.isEmpty( serviceName ) ) {

			serviceName = path.substring( 1 ) ;

		}

		var ingressHost = "" ; // can be optionally defined as env var; or if not found the first will be used

		try {

			var serviceInstance = csapApis.application( ).flexFindFirstInstanceCurrentHost( serviceName ) ;

			if ( serviceInstance == null ) {

				// use kubelet instance for variables
				serviceInstance = csapApis.application( ).kubeletInstance( ) ;

			}

			var serviceVariables = serviceOsManager.buildServiceEnvironmentVariables(
					serviceInstance ) ;

			logger.debug( "{} variables: {}", serviceVariables ) ;
			ingressHost = serviceVariables.get( KubernetesIntegration.CSAP_DEF_INGRESS_HOST ) ;

			if ( ingressHost != null
					&& ingressHost.contains( "*" ) ) {

				ingressHost = null ; // wildcard rule: just use the host of the first running instance located

			}

		} catch ( Exception e ) {

			logger.warn( "serviceName: {} path: '{}' Failed to find ingress host variable: {}", serviceName, path,
					CSAP.buildCsapStack( e ) ) ;

		}

		String location = csapApis.kubernetes( ).ingressUrl( csapApis.application( ), path, ingressHost, ssl ) ;

		logger.info( "Path location: '{}'", location ) ;
		response.setHeader( "Location", location ) ;

		if ( debug ) {

			response.setStatus( 200 ) ;
			response.getWriter( ).println( "debug mode" ) ;

		} else {

			response.setStatus( 302 ) ;

		}

		response.getWriter( ).println( "ingress lookup, location: " + location ) ;

	}

	@RequestMapping ( "/location/service/file/{serviceName}" )
	public void serviceBrowser (
									@PathVariable String serviceName ,
									@RequestParam String filePath ,
									HttpServletResponse response )
		throws Exception {

		File path = csapApis.application( ).getCsapWorkingSubFolder(
				csapApis.application( ).findServiceByNameOnCurrentHost( serviceName )
						.getName( ) ) ;

		csapApis.application( ).create_browseable_file_and_redirect_to_it( response, new File( path, filePath ) ) ;

	}

	@RequestMapping ( "/find-service/{releasePackage}/{serviceName}" )
	public ModelAndView servicePortalFind (
											@PathVariable String serviceName ,
											@PathVariable String releasePackage ) {

		ModelAndView mav = new ModelAndView( ) ;

		var targetView = CsapConstants.ADMIN_URL ;

		if ( csapApis.application( ).isAgentProfile( ) ) {

			targetView = CsapConstants.BASE_URL ;

		}

		var redirectUrl = targetView + "app-browser#services-tab,instances," + serviceName ;

		mav.setView( new RedirectView( redirectUrl, true, false, true ) ) ;

		logger.info( "Redirecting based on package {}  and service {}", releasePackage, serviceName ) ;

		return mav ;

	}

	ObjectMapper jacksonMapper = new ObjectMapper( ) ;

	@Inject
	@Qualifier ( "csapEventsService" )
	private RestTemplate csapEventsService ;

	@Inject
	ApplicationConfiguration csapCore ;

	@RequestMapping ( CsapConstants.SCREEN_URL )
	public String csapScreenCastViewer (
											ModelMap modelMap ,
											@RequestParam ( "item" ) String item ,
											@RequestParam ( value = "wiki" , required = false , defaultValue = "https://github.com/csap-platform/csap-core/wiki" ) String wiki ,
											HttpServletRequest request ,
											HttpSession session )
		throws IOException {

		String user = CsapUser.currentUsersID( ) ;

		String mp4 = csapCore.getScreenCastServerUrl( ) + item + ".mp4" ;

		csapEventClient.publishUserEvent( CsapEvents.CSAP_UI_CATEGORY + "/screencast", user, mp4, wiki ) ;

		if ( ! wiki.startsWith( "http" ) ) {

			wiki = "https://github.com/csap-platform/csap-core/wiki#updateRef" + wiki ;

		}

		modelMap.addAttribute( "filename", item + ".mp4" ) ;
		modelMap.addAttribute( "mp4", mp4 ) ;
		modelMap.addAttribute( "wiki", wiki ) ;

		return "misc/screencast" ;

	}

	final static String TESTHOST = "testHost" ;

	public void addTestHost ( HttpServletRequest request , HttpSession session ) {

		var testHost = request.getParameter( TESTHOST ) ;

		if ( StringUtils.isNotEmpty( testHost ) ) {

			session.setAttribute( TESTHOST, testHost ) ;
			logger.info( CsapApplication.testHeader( "Updated testHost: {}" ), testHost ) ;

		}

	}

	public void setViewConstants ( ModelMap modelMap ) {

		modelMap.addAttribute( "csapApp", csapApis.application( ) ) ;

		modelMap.addAttribute( "csapHostAgentPattern", csapApis.application( ).getAgentHostUrlPattern( false ) ) ;
		modelMap.addAttribute( "csapHostName", csapApis.application( ).getCsapHostName( ) ) ;
		modelMap.addAttribute( "csapHostEnvironmentName", csapApis.application( ).getCsapHostEnvironmentName( ) ) ;

		modelMap.addAttribute( "collectionHost", CsapApplication.COLLECTION_HOST ) ;
		modelMap.addAttribute( "collectionOsProcess", CsapApplication.COLLECTION_OS_PROCESS ) ;
		modelMap.addAttribute( "collectionJava", CsapApplication.COLLECTION_JAVA ) ;
		modelMap.addAttribute( "collectionApplication", CsapApplication.COLLECTION_APPLICATION ) ;

		modelMap.addAttribute( "viewConstants", CsapConstants.viewConstants ) ;

		modelMap.addAttribute( "csapHelper", CSAP.class ) ;

		modelMap.addAttribute( "csapAlerts", ServiceAlertsEnum.values( ) ) ;

	}

	public void setCommonAttributes ( ModelMap modelMap , HttpSession session ) {

		setViewConstants( modelMap ) ;

		if ( session != null ) {

			addSecurityAttributes( modelMap, session ) ;
			//
			// Used for ui development on desktop - get more graph data on hostdashboard,
			// host.html, java.html,...
			//
			var isSimulateLiveEnv = true ;
			modelMap.addAttribute( "isSimulateLiveEnv", isSimulateLiveEnv ) ;

			if ( isSimulateLiveEnv && csapApis.application( ).isDesktopHost( ) ) {

				if ( csapApis.application( ).isAgentProfile( ) ) {

					var testHost = (String) session.getAttribute( TESTHOST ) ;

					if ( StringUtils.isEmpty( testHost ) ) {

						testHost = "csap-dev04" ;

					}

					modelMap.addAttribute( "testHostOnDesktop", testHost ) ;
					modelMap.addAttribute( "testHostForceLocalHost", true ) ;

				} else {

//					modelMap.addAttribute( "analyticsUrl",
//							"http://localhost.***REMOVED***:8021/csap-admin/os/performance"
//									+ "?life=" +  csapApis.application().getCsapHostEnvironmentName( ) ) ;
					modelMap.addAttribute( "analyticsUrl",
							"http://localhost.***REMOVED***:8021/csap-admin/os/performance" ) ;

				}

				modelMap.addAttribute( "eventUser", "SensusCsap" ) ;
				modelMap.addAttribute( "graphReleasePackage", "CSAP Platform" ) ;

			}

		}

		modelMap.addAttribute( "applicationBranch", csapApis.application( ).getSourceBranch( ) ) ;

		modelMap.addAttribute( "agentHostUrlPattern", csapApis.application( ).getAgentHostUrlPattern( false ) ) ;

		modelMap.addAttribute( "host", csapApis.application( ).getCsapHostName( ) ) ;
		modelMap.addAttribute( "javaServers", ProcessRuntime.javaServers( ) ) ;

		try {

			modelMap.addAttribute( "userid", CsapUser.currentUsersID( ) ) ;

		} catch ( Exception e ) {

			logger.error( "Failed to get security principle", e ) ;
			modelMap.addAttribute( "userid", "UnknownUserid" ) ;

		}

		EnvironmentSettings rootEnvSettings = csapApis.application( ).rootProjectEnvSettings( ) ;
		modelMap.addAttribute( "lifeCycleSettings", rootEnvSettings ) ;
		modelMap.addAttribute( "environmentSettings", rootEnvSettings ) ;

		modelMap.addAttribute( "lifecycle", csapApis.application( ).getCsapHostEnvironmentName( ) ) ;

		modelMap.addAttribute( "name", csapApis.application( ).getName( ) ) ;
		modelMap.addAttribute( "packageNames", csapApis.application( ).getPackageNames( ) ) ;
		modelMap.addAttribute( "packageMap", csapApis.application( ).getPackageNameToFileMap( ) ) ;

		ArrayNode testUrls = jacksonMapper.createArrayNode( ) ;

		for ( String url : csapApis.application( ).getRootProject( ).getHttpdTestUrls( ) ) {

			testUrls.add( url ) ;

		}

		modelMap.addAttribute( "testUrls", testUrls ) ;
		logger.debug( "testUrls: {} ", testUrls.toString( ) ) ;

		logger.debug( "Root Model: {} ", csapApis.application( ).getRootProject( ) ) ;

		modelMap.addAttribute( "version", csapInformation.getVersion( ) ) ;

		modelMap.addAttribute( "kubernetesServiceTypes", K8.k8TypeList( ) ) ;

		modelMap.addAttribute( "dateTime",
				LocalDateTime.now( ).format( DateTimeFormatter.ofPattern( "HH:mm:ss,   MMM d  uuuu " ) ) ) ;

//		modelMap.addAttribute( "analyticsUrl",  csapApis.application().rootProjectEnvSettings( ).getAnalyticsUiUrl( )
//				+ "?life=" +  csapApis.application().getCsapHostEnvironmentName( ) ) ;
		modelMap.addAttribute( "analyticsUrl", csapApis.application( ).rootProjectEnvSettings( )
				.getAnalyticsUiUrl( ) ) ;
		//
		modelMap.addAttribute( "prodDataUrl", csapApis.application( ).rootProjectEnvSettings( ).getAnalyticsUiUrl( )
				+ "?life=prod&report=service/detail&appId=" + csapApis.application( ).rootProjectEnvSettings( )
						.getEventDataUser( )
				+ "&service=" ) ;

		modelMap.addAttribute( "applicationId", csapApis.application( ).rootProjectEnvSettings( )
				.getEventDataUser( ) ) ;
		modelMap.addAttribute( "eventUser", csapApis.application( ).rootProjectEnvSettings( ).getEventDataUser( ) ) ;
		modelMap.addAttribute( "eventApiUrl", csapApis.application( ).rootProjectEnvSettings( ).getEventApiUrl( ) ) ;
		modelMap.addAttribute( "eventMetricsUrl", csapApis.application( ).rootProjectEnvSettings( )
				.getEventMetricsUrl( ) ) ;

	}

	public void addSecurityAttributes ( ModelMap modelMap , HttpSession session ) {

		session.setAttribute( CsapConstants.ROLES, securitySettings.getRoles( ).getAndStoreUserRoles( session ) ) ;

		if ( securitySettings.getRoles( ).getAndStoreUserRoles( session )
				.contains( CsapSecurityRoles.ADMIN_ROLE ) ) {

			modelMap.addAttribute( "adminRole", CsapSecurityRoles.ADMIN_ROLE ) ;

		}

		if ( securitySettings.getRoles( ).getAndStoreUserRoles( session )
				.contains( CsapSecurityRoles.BUILD_ROLE ) ) {

			modelMap.addAttribute( "scmRole", CsapSecurityRoles.BUILD_ROLE ) ;

		}

		if ( securitySettings.getRoles( ).getAndStoreUserRoles( session )
				.contains( CsapSecurityRoles.INFRA_ROLE ) ) {

			modelMap.addAttribute( "infraRole", CsapSecurityRoles.INFRA_ROLE ) ;

		}

		for ( String role : securitySettings.getRoles( ).getAndStoreUserRoles( session ) ) {

			if ( role.equals( CsapSecurityRoles.ADMIN_ROLE ) ) {

				modelMap.addAttribute( "admin", "admin" ) ;

			}

		}

		modelMap.addAttribute( "adminGroup", securitySettings.getRoles( ).getAdminGroup( ) ) ;
		modelMap.addAttribute( "buildGroup", securitySettings.getRoles( ).getBuildGroup( ) ) ;

	}

	@GetMapping ( "csap-analytics" )
	public ModelAndView csapAnalyticsPortal (
												ModelMap modelMap ,
												HttpSession session ,
												@RequestParam ( value = "serviceName" , required = false ) String serviceNamePort ,
												@RequestParam ( value = "project" , required = false ) String project ,
												@RequestParam ( value = "life" , required = false ) String life ,
												@RequestParam ( value = CsapConstants.HOST_PARAM , required = false ) String hostName )
		throws IOException {

		if ( csapApis.application( ).getActiveUsers( ).addTrail( "/csap-analytics" ) ) {

			csapEventClient.publishUserEvent( CsapEvents.CSAP_OS_CATEGORY + "/accessed", CsapUser.currentUsersID( ),
					"User interface: csap-analytics", "" ) ;

		}

		setCommonAttributes( modelMap, session ) ;

		modelMap.put( "project", project ) ;
		modelMap.put( "life", life ) ;

		modelMap.addAttribute( "metricLabels", buildMetricLabels( ) ) ;

		return new ModelAndView( "analytics/analytics-main" ) ;

	}

	private ObjectNode buildMetricLabels ( ) {

		ObjectNode labelMapping = JmxCommonEnum.graphLabels( ) ;
		labelMapping.setAll( OsSharedEnum.hostReportLabels( ) ) ;
		labelMapping.setAll( OsProcessEnum.graphLabels( ) ) ;
		return labelMapping ;

	}
}
