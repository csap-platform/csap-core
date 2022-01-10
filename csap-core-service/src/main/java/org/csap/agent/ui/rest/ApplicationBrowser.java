package org.csap.agent.ui.rest ;

import java.io.File ;
import java.net.URLDecoder ;
import java.net.URLEncoder ;
import java.nio.file.Files ;
import java.time.LocalDate ;
import java.time.LocalDateTime ;
import java.time.format.DateTimeFormatter ;
import java.util.ArrayList ;
import java.util.Arrays ;
import java.util.Collections ;
import java.util.HashMap ;
import java.util.List ;
import java.util.Map ;
import java.util.Optional ;
import java.util.TreeMap ;
import java.util.regex.Matcher ;
import java.util.stream.Collectors ;
import java.util.stream.Stream ;

import javax.inject.Inject ;
import javax.servlet.http.HttpServletRequest ;
import javax.servlet.http.HttpServletResponse ;
import javax.servlet.http.HttpSession ;

import org.apache.commons.io.FileUtils ;
import org.apache.commons.lang3.StringEscapeUtils ;
import org.apache.commons.lang3.StringUtils ;
import org.commonmark.ext.gfm.tables.TablesExtension ;
import org.commonmark.parser.Parser ;
import org.commonmark.renderer.html.HtmlRenderer ;
import org.csap.agent.CsapCore ;
import org.csap.agent.CsapCoreService ;
import org.csap.agent.CsapTemplate ;
import org.csap.agent.container.DockerJson ;
import org.csap.agent.container.kubernetes.KubernetesJson ;
import org.csap.agent.integrations.CsapEvents ;
import org.csap.agent.linux.OsCommandRunner ;
import org.csap.agent.model.Application ;
import org.csap.agent.model.HealthManager ;
import org.csap.agent.model.ProcessRuntime ;
import org.csap.agent.model.Project ;
import org.csap.agent.model.ProjectLoader ;
import org.csap.agent.model.ServiceAlertsEnum ;
import org.csap.agent.model.ServiceAttributes ;
import org.csap.agent.model.ServiceInstance ;
import org.csap.agent.services.HostKeys ;
import org.csap.agent.stats.service.JmxCommonEnum ;
import org.csap.agent.ui.editor.ServiceResources ;
import org.csap.agent.ui.explorer.OsExplorer ;
import org.csap.agent.ui.windows.CorePortals ;
import org.csap.alerts.AlertSettings ;
import org.csap.docs.CsapDoc ;
import org.csap.helpers.CSAP ;
import org.csap.helpers.CsapApplication ;
import org.csap.integations.CsapInformation ;
import org.csap.security.CsapUser ;
import org.csap.security.CustomRememberMeService ;
import org.csap.security.config.CsapSecuritySettings ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.beans.factory.annotation.Autowired ;
import org.springframework.beans.factory.annotation.Qualifier ;
import org.springframework.http.MediaType ;
import org.springframework.http.ResponseCookie ;
import org.springframework.ui.ModelMap ;
import org.springframework.util.LinkedMultiValueMap ;
import org.springframework.util.MultiValueMap ;
import org.springframework.web.bind.annotation.CookieValue ;
import org.springframework.web.bind.annotation.GetMapping ;
import org.springframework.web.bind.annotation.PathVariable ;
import org.springframework.web.bind.annotation.PostMapping ;
import org.springframework.web.bind.annotation.RequestMapping ;
import org.springframework.web.bind.annotation.RequestParam ;
import org.springframework.web.bind.annotation.RestController ;
import org.springframework.web.client.RestTemplate ;
import org.springframework.web.servlet.ModelAndView ;

import com.fasterxml.jackson.databind.JsonNode ;
import com.fasterxml.jackson.databind.ObjectMapper ;
import com.fasterxml.jackson.databind.node.ArrayNode ;
import com.fasterxml.jackson.databind.node.ObjectNode ;

@RestController
@RequestMapping ( CsapCoreService.APP_BROWSER_URL )
public class ApplicationBrowser {

	final Logger logger = LoggerFactory.getLogger( getClass( ) ) ;

	Application application ;
	ObjectMapper jsonMapper ;
	CsapInformation csapInformation ;
	CorePortals corePortals ;

	public ApplicationBrowser ( Application application, ObjectMapper jsonMapper, CsapInformation csapInformation,
			CorePortals corePortals ) {

		this.application = application ;
		this.jsonMapper = jsonMapper ;
		this.csapInformation = csapInformation ;
		this.corePortals = corePortals ;

	}

	//
	// Top level browsers for services
	//
	final static String PREFERENCES_COOKIE = "csap-preferences" ;

	String adminVersion ;

	@GetMapping
	public ModelAndView applicationBrowser (
												HttpServletRequest request ,
												HttpSession session ,
												String layout ,
												@RequestParam ( value = CsapCore.PROJECT_PARAMETER , required = false ) String csapProjectName ,
												@CookieValue ( value = PREFERENCES_COOKIE , required = false ) String preferences ) {

		ModelAndView mav = new ModelAndView( "app-browser/browser-main" ) ;

		var activeProject = csapProjectName ;

		if ( StringUtils.isEmpty( csapProjectName ) ) {

			activeProject = application.getActiveProjectName( ) ;

		}

		JsonNode userPrefs = jsonMapper.createObjectNode( ) ;
		var theme = "auto" ;

		if ( StringUtils.isNotEmpty( preferences ) ) {

			try {

				logger.debug( "preferences: {}", preferences ) ;
				userPrefs = jsonMapper.readTree( URLDecoder.decode( preferences, "UTF-8" ) ) ;
				theme = userPrefs.path( "csap-theme" ).asText( theme ) ;

			} catch ( Exception e ) {

				logger.warn( "Failed parsing preferences: {}", CSAP.buildCsapStack( e ) ) ;

			}

		}

		mav.getModelMap( ).addAttribute( "agentProfile", application.isAgentProfile( ) ) ;

		if ( application.isAgentProfile( ) ) {

			// only used while browser is loading - then preferences.js sets based on
			// criteria
			theme = "theme-dark agent" ;

			if ( System.getenv( "dockerHostFqdn" ) != null ) {

				theme = "theme-dark agent theme-forest" ;

			}

			if ( application.isCrioInstalledAndActive( ) ) {

				mav.getModelMap( ).addAttribute( "crio", true ) ;

			}

		}

		var newSession = application.getActiveUsers( ).addTrail( "ApplicationBrowser" ) ;

		if ( newSession ) {

			application.getEventClient( ).publishUserEvent(
					CsapEvents.CSAP_UI_CATEGORY + CsapCoreService.APP_BROWSER_URL,
					CsapUser.currentUsersID( ), "portal accessed", "" ) ;

		}

//		mav.getModelMap( ).addAttribute( "isFirstAccess", newSession ) ;
		// bypasss login location
		mav.getModelMap( ).addAttribute( "isFirstAccess", false ) ;

		mav.getModelMap( ).addAttribute( "theme", theme ) ;
		mav.getModelMap( ).addAttribute( "preferences", userPrefs ) ;

		var deployedArtifact = application.getLocalAgent( ).getDefaultContainer( )
				.getDeployedArtifacts( ) ;

		if ( application.isAdminProfile( ) ) {

			if ( StringUtils.isEmpty( adminVersion ) ) {

				// need to use version for host
				var adminOnHost = application.findServiceByNameOnCurrentHost( CsapCore.ADMIN_NAME ) ;

				if ( adminOnHost == null ) {

					logger.warn( "Did not find {} on host: {}", CsapCore.ADMIN_NAME, application
							.getServicesOnHost( ) ) ;

				} else {

					var adminReport = application.getHostStatusManager( ).serviceCollectionReport(
							List.of( adminOnHost.getHostName( ) ),
							adminOnHost.getServiceName_Port( ),
							null ) ;
					logger.debug( "adminReport: {}", CSAP.jsonPrint( adminReport ) ) ;

					if ( adminReport != null ) {

						var adminDeployed = adminReport.findValue( "deployedArtifacts" ) ;

						if ( adminDeployed != null ) {

							adminVersion = adminDeployed.asText( "" ) ;

						}

					}

				}

			}

			deployedArtifact = adminVersion ;

		}

		if ( StringUtils.isEmpty( deployedArtifact ) ) {

			deployedArtifact = "csap" ;

			if ( Application.isRunningOnDesktop( ) ) {

				// deployedArtifact = "desktop-9901" ;

			}

		}

		mav.getModelMap( ).addAttribute( "deployedArtifact", deployedArtifact ) ;

		String secureUrl = null ;

		if ( request.getScheme( ).equals( "http" ) ) {

			secureUrl = application.getCsapCoreService( ).getCsapWebServer( ).getSecureUrl( request ) ;

		}

		mav.getModelMap( ).addAttribute( "secureUrl", secureUrl ) ;

		mav.getModelMap( ).addAttribute( "activeProject", activeProject ) ;
		mav.getModelMap( ).addAttribute( "applicationName", application.getName( ) ) ;
		mav.getModelMap( ).addAttribute( "projectNames", application.getPackageNames( ) ) ;

		mav.getModelMap( ).addAttribute( "analyticsUrl", application.rootProjectEnvSettings( ).getAnalyticsUiUrl( )
				+ "?life=" + application.getCsapHostEnvironmentName( ) ) ;

		var sortedPackages = application.getRootProject( ).releasePackagesRootFirst( )
				.collect( Collectors.toList( ) ) ;

		mav.getModelMap( ).addAttribute( "sortedPackages", sortedPackages ) ;

		mav.getModelMap( ).addAttribute( "applicationId", application.rootProjectEnvSettings( ).getEventDataUser( ) ) ;

		setCommonAttributes( mav.getModelMap( ), session ) ;

		//
		// Used for ui development on desktop - get more graph data on hostdashboard,
		// host.html, java.html,...
		//
		var isSimulateLiveEnv = true ;
		mav.getModelMap( ).addAttribute( "isSimulateLiveEnv", isSimulateLiveEnv ) ;

		if ( isSimulateLiveEnv && application.isDesktopHost( ) ) {

			if ( application.isAgentProfile( ) ) {

				var testHost = (String) session.getAttribute( TESTHOST ) ;

				if ( StringUtils.isEmpty( testHost ) ) {

					testHost = "csap-dev04" ;

				}

				mav.getModelMap( ).addAttribute( "testHostOnDesktop", testHost ) ;
				mav.getModelMap( ).addAttribute( "testHostForceLocalHost", true ) ;
				mav.getModelMap( ).addAttribute( "kubernetesApiUrl", "http://" + testHost + ".***REMOVED***:8014" ) ;

			} else {

				mav.getModelMap( ).addAttribute( "analyticsUrl",
						"http://localhost.***REMOVED***:8021/csap-admin/os/performance"
								+ "?life=" + application.getCsapHostEnvironmentName( ) ) ;

			}

			mav.getModelMap( ).addAttribute( "applicationId", "SensusCsap" ) ;
			mav.getModelMap( ).addAttribute( "graphReleasePackage", "CSAP Platform" ) ;

			logger.info( CsapApplication.testHeader( "desktop simulate active" ) ) ;

		}

		if ( StringUtils.isNotEmpty( layout ) ) {

			mav.setViewName( "app-browser/layout" ) ;

		}

		return mav ;

	}

	final static String TESTHOST = "testHost" ;

	String title ;

	public void setCommonAttributes ( ModelMap modelMap , HttpSession session ) {

		corePortals.addSecurityAttributes( modelMap, session ) ;

		corePortals.setViewConstants( modelMap ) ;

		if ( title == null ) {

			title = application.getName( ) ;

			if ( application.isAgentProfile( ) ) {

				title = application.getCsapHostName( ) + "-" + title ;

			}

		}

		modelMap.addAttribute( "pageTitle", title ) ;

		modelMap.addAttribute( "toolsMap", csapInformation.buildToolsMap( ) ) ;
		modelMap.addAttribute( "helpMap", application.getHelpMenuMap( ) ) ;

		try {

			var loggedInId = CsapUser.currentUsersID( ) ;
			modelMap.addAttribute( "csapUser", loggedInId ) ;
			modelMap.addAttribute( "userid", loggedInId ) ;
			modelMap.addAttribute( "scmUser", loggedInId ) ;

			var principleReport = CsapUser.getPrincipleInfo( ) ;
			// logger.info( "logged in: {} ", CSAP.jsonPrint( principleReport ) ) ;

			var scmUser = principleReport.at( "/cn/0" ).asText( loggedInId ) ;

			// oath given
			if ( principleReport.has( "givenName" ) ) {

				scmUser = principleReport.path( "givenName" ).asText( loggedInId ) ;

			}

			modelMap.addAttribute( "scmUser", scmUser
					.toLowerCase( )
					.replaceAll( " ", "" )
					.replaceAll( Matcher.quoteReplacement( "'" ), "" ) ) ;

		} catch ( Exception e ) {

			logger.error( "Failed to get security principle {}", CSAP.buildCsapStack( e ) ) ;

		}

		modelMap.addAttribute( "environmentSettings", application.environmentSettings( ) ) ;
		modelMap.addAttribute( "HISTORY_URL", application.environmentSettings( ).getHistoryUiUrl( ) ) ;
		modelMap.addAttribute( "METRICS_URL", application.rootProjectEnvSettings( ).getEventMetricsUrl( ) ) ;

		// editor
		modelMap.addAttribute( "applicationBranch", application.getSourceBranch( ) ) ;
		modelMap.addAttribute( "addHostUrl", application.getRootProject( ).getInfraAddHost( ) ) ;

		// Host Dashboard
		modelMap.addAttribute( "explorerUrl", OsExplorer.EXPLORER_URL ) ;

		modelMap.addAttribute( "activityUrl", application.rootProjectEnvSettings( ).getHostActivityUrl( ) ) ;
		modelMap.addAttribute( "healthUrl", application.rootProjectEnvSettings( ).getHostHealthUrl( ) ) ;

		modelMap.addAttribute( "vsphereEnabled", application.rootProjectEnvSettings( ).isVsphereConfigured( ) ) ;

		if ( application.isDockerInstalledAndActive( ) ) {

			modelMap.addAttribute( "containerUrl", application.getDockerIntegration( ).getSettings( ).getUrl( ) ) ;
			modelMap.addAttribute( "dockerRepository", application.getDockerIntegration( ).getSettings( )
					.getTemplateRepository( ) ) ;
			modelMap.addAttribute( "referenceImages", application.getDockerUiDefaultImages( ) ) ;

		}

		if ( application.isKubernetesInstalledAndActive( ) ) {

			ServiceInstance kubernetesInstance = application.kubeletInstance( ) ;

			var urls = kubernetesInstance.getUrl( ).split( "," ) ;
			var apiUrl = urls[0] ;

			if ( urls.length >= 2 ) {

				apiUrl = urls[1] ;

			}

			modelMap.addAttribute( "kubernetesApiUrl", apiUrl ) ;
			modelMap.addAttribute( "kubernetesNamespaces", application.getKubernetesIntegration( ).nameSpaces( ) ) ;
			modelMap.addAttribute( "kubernetesServiceTypes", KubernetesJson.k8TypeList( ) ) ;

		}

	}

	@PostMapping ( "/preferences" )
	public String preferences ( String preferences , HttpServletRequest request , HttpServletResponse response )
		throws Exception {

		ResponseCookie cookie = ResponseCookie
				.from( PREFERENCES_COOKIE,
						URLEncoder.encode( preferences, "UTF-8" ) )
				.maxAge( 60 * 60 * 24 * 365 * 10 )
				.domain( CustomRememberMeService.getSingleSignOnDomain( request ) )
				.sameSite( "Lax" )
				.path( "/" )
				.build( ) ;

		response.addHeader( "Set-Cookie", cookie.toString( ) ) ;

		// Cookie cookie = new Cookie(
		// PREFERENCES_COOKIE,
		// URLEncoder.encode( preferences, "UTF-8" ) ) ;
		//
		// logger.info( "preferences: {}", preferences );
		// cookie.setHttpOnly( true ) ;
		// cookie.setDomain( CustomRememberMeService.getSingleSignOnDomain( request ) )
		// ;
		// cookie.setPath( "/" ) ;
		// cookie.setMaxAge( 60 * 60 * 24 * 365 * 10 ) ;
		// response.addCookie( cookie ) ;

		return "cookie stored" ;

	}

	@GetMapping ( "/host-configuration" )
	public JsonNode hostConfiguration (
										String fromFolder ,
										String location ,
										String extractDir ,
										@RequestParam ( value = "serviceName" , required = false , defaultValue = "" ) String serviceName_port ) {

		var configReport = jsonMapper.createObjectNode( ) ;

		if ( fromFolder == null ) {

			fromFolder = Application.FileToken.HOME.value ;

		}

		File requestedFileOrFolder = application.getRequestedFile( fromFolder, serviceName_port, false ) ;
		String defaultLocation = requestedFileOrFolder.getAbsolutePath( ) ;

		if ( location != null ) {

			defaultLocation = location ;

			if ( Application.isRunningOnDesktop( ) ) {

				defaultLocation.replaceAll( StringEscapeUtils.escapeJava( "\\" ),
						StringEscapeUtils.escapeJava( "\\\\" ) ) ;

			}

		}

		if ( extractDir != null ) {

			defaultLocation = extractDir ;

		}

		configReport.put( "defaultLocation", defaultLocation ) ;

		if ( requestedFileOrFolder != null ) {

			configReport.put( "locationName", requestedFileOrFolder.getName( ) ) ;

		}

		//
		// CSAP command runner
		//
		configReport.put( "csapEnvFile", application.csapPlatformPath( "bin/csap-environment.sh" )
				.getAbsolutePath( ) ) ;
		configReport.put( "userid", CsapUser.currentUsersID( ) ) ;
		configReport.put( "scriptBase", application.getScriptToken( ) ) ;
		configReport.set( "clusterHostsMap", application.buildClusterByPackageInActiveLifecycleReport( ) ) ;
		configReport.set( "allHosts",
				jsonMapper.convertValue( application.getAllHostsInAllPackagesInCurrentLifecycle( ),
						ArrayNode.class ) ) ;
		configReport.set( "allHosts",
				jsonMapper.convertValue( application.getAllHostsInAllPackagesInCurrentLifecycle( ),
						ArrayNode.class ) ) ;

		var serviceOnHost = application.findServiceByNameOnCurrentHost( serviceName_port ) ;

		if ( serviceOnHost != null ) {

			configReport.set( "serviceHosts",
					jsonMapper.convertValue(
							application.getActiveProject( ).findHostsForService( serviceOnHost.getName( ) ),
							ArrayNode.class ) ) ;

		} else {

			configReport.set( "serviceHosts", null ) ;

		}

		if ( requestedFileOrFolder.isFile( )
				&& commandRunnerSupportedFiles( requestedFileOrFolder.getName( ) ) ) {

			try {

				var contents = FileUtils.readFileToString( requestedFileOrFolder ) ;
				configReport.put( "fileContents", contents ) ;

			} catch ( Exception e ) {

				logger.warn( "Failed loading contents", CSAP.buildCsapStack( e ) ) ;

			}

		}

		configReport.set( "templateArray", buildTemplates( ) ) ;

		return configReport ;

	}

	/**
	 * @see OsCommandRunner#runCancellable(int, String, File,
	 *      org.csap.agent.linux.OutputFileMgr, String, Map)
	 */
	private boolean commandRunnerSupportedFiles ( String fileName ) {

		return fileName.endsWith( ".sh" )
				|| fileName.endsWith( ".ksh" )
				|| fileName.endsWith( ".py" )
				|| fileName.endsWith( ".pl" ) ;

	}

	private ArrayNode buildTemplates ( ) {

		ArrayNode templates = jsonMapper.createArrayNode( ) ;

		File scriptsFolder = CsapTemplate.shell_scripts.getFile( ) ;

		if ( scriptsFolder.exists( ) && scriptsFolder.isDirectory( ) ) {

			List<File> templateFiles = Arrays.asList( scriptsFolder.listFiles( ) ) ;

			Collections.sort( templateFiles ) ;

			templateFiles.stream( )
					.filter( file -> {

						return file.length( ) < 10000 ;

					} )
					.forEach( scriptFile -> {

						try ( Stream<String> lines = Files.lines( scriptFile.toPath( ) ) ) {

							logger.debug( "Reading: {}", scriptFile.getAbsolutePath( ) ) ;
							ObjectNode template = templates.addObject( ) ;

							template.put( "source", scriptFile.getName( ) ) ;
							template.put( "command", scriptFile.getName( ) ) ;
							template.put( "description", scriptFile.getName( ) ) ;

							// List<String> lines = Files.readAllLines(
							// scriptFile.toPath() );
							Optional<String> firstLine = lines.limit( 1 ).findFirst( ) ;

							if ( firstLine.isPresent( ) ) {

								String[] desc = firstLine.get( ).substring( 1 ).split( ",", 2 ) ;

								if ( desc.length == 2 ) {

									template.put( "command", desc[0].trim( ) ) ;
									template.put( "description", desc[1].trim( ) ) ;

								}

								// template.set( "lines", jacksonMapper.convertValue( lines, ArrayNode.class )
								// );
							}

						} catch ( Exception e ) {

							logger.warn( "Failed to get first line from: {} {}",
									scriptFile,
									CSAP.buildCsapStack( e ) ) ;

						}

					} ) ;

		}

		File scriptFolder = new File( application.getDefinitionFolder( ), "scripts" ) ;

		/*
		 * @see #OsCommandRunner for wrapper invokation
		 */
		String[] scriptNames = scriptFolder.list(
				( File dir , String name ) -> commandRunnerSupportedFiles( name ) ) ;

		if ( scriptNames != null ) {

			for ( var scriptName : scriptNames ) {

				ObjectNode template = templates.addObject( ) ;

				template.put( "source", scriptName ) ;
				template.put( "command", scriptName ) ;
				template.put( "description", "project script" ) ;

			}

		}

		return templates ;

	}

	@GetMapping ( "/trend/definition" )
	public JsonNode trendingDefinition ( ) {

		return application.environmentSettings( ).getTrendingConfig( ) ;

	}

	public final static String HELM_INFO_URL = "/helm/info" ;

	@GetMapping ( HELM_INFO_URL )
	public JsonNode helmInfoReport (
										String project ,
										String command ,
										String chart ,
										boolean showAll ) {

		var infoReport = jsonMapper.createObjectNode( ) ;

		if ( application.isAdminProfile( ) ) {

			var allHostReport = application.healthManager( ).build_host_report( project ) ;

			var kubernetesHostOptional = CSAP.jsonStream( allHostReport )
					.filter( hostReport -> hostReport.findParent( "kubernetes" ) != null )
					.map( hostReport -> hostReport.path( "name" ).asText( ) )
					.findFirst( ) ;

			if ( kubernetesHostOptional.isPresent( ) ) {

				var hostName = kubernetesHostOptional.get( ) ;
				MultiValueMap<String, String> urlVariables = new LinkedMultiValueMap<String, String>( ) ;

				urlVariables.set( "apiUser", CsapUser.currentUsersID( ) ) ;
				urlVariables.set( "project", project ) ;
				urlVariables.set( "command", command ) ;
				urlVariables.set( "chart", chart ) ;
				urlVariables.set( "showAll", Boolean.toString( showAll ) ) ;

				String url = CsapCoreService.APP_BROWSER_URL + HELM_INFO_URL ;
				List<String> hosts = new ArrayList<>( ) ;
				hosts.add( hostName ) ;

				logger.debug( "hitting: {}, hosts: {}, urlVariables: {} ", url, hosts, urlVariables ) ;

				JsonNode remoteCall = application.getOsManager( ).getServiceManager( ).remoteAgentsGet(
						hosts,
						url,
						urlVariables ) ;

				return remoteCall.path( hostName ) ;

			} else {

				logger.warn( "Failed to locate kubernetes host" ) ;
				infoReport.put( "error", "kubernetes not available" ) ;
				return infoReport ;

			}

		}

		var serviceInstance = application.findServiceByNameOnCurrentHost( chart ) ;

		if ( serviceInstance != null
				&& serviceInstance.isHelmConfigured( ) ) {

			chart = serviceInstance.getHelmChartName( ) ;

		}

		var helmCommand = "helm show values " + chart ;

		if ( showAll ) {

			helmCommand = "helm show all " + chart ;

		}

		if ( command.equals( "helm-readme" ) ) {

			helmCommand = "helm show readme " + chart ;

		}

		logger.info( helmCommand ) ;

		var cliResults = application.getOsManager( ).cli( helmCommand ) ;

		if ( command.equals( "helm-readme" ) ) {

			cliResults = convertMarkdownToHtml( cliResults ) ;

			infoReport.put( DockerJson.response_html.json( ), cliResults ) ;
			infoReport.put( "source", "helm show readme" ) ;

		} else {

			infoReport.put( DockerJson.response_yaml.json( ), cliResults ) ;

		}

		return infoReport ;

	}

	private String convertMarkdownToHtml ( String cliResults ) {

		var extensions = Arrays.asList( TablesExtension.create( ) ) ;
		var parser = Parser.builder( ).extensions( extensions ).build( ) ;
		var document = parser.parse( cliResults ) ;
		var renderer = HtmlRenderer.builder( ).extensions( extensions ).build( ) ;
		cliResults = renderer.render( document ) ;

		cliResults = cliResults.replaceAll( "a href", "a class=csap-link target=_blank href" ) ;
		cliResults = cliResults.replaceAll( "<table>", "<table class=csap>" ) ;
		return cliResults ;

	}

	@GetMapping ( "/readme" )
	public JsonNode readme ( String name ) {

		var readMeReport = jsonMapper.createObjectNode( ) ;

		var readMeMarkDown = "#Failed to retrieve report " ;

		var readMeSource = "Application Definition" ;

		try {

			var restTemplate = new RestTemplate( ) ;

			var readme = application.findFirstServiceInstanceInLifecycle( name ).getReadme( ) ;

			if ( readme.startsWith( "http" ) ) {

				readMeMarkDown = restTemplate.getForObject( readme, String.class ) ;
				readMeSource = readme ;

			} else {

				readMeMarkDown = readme ;

			}

		} catch ( Exception e ) {

			logger.warn( "Failed to get readme: {}", CSAP.buildCsapStack( e ) ) ;

		}

		readMeReport.put( DockerJson.response_html.json( ), convertMarkdownToHtml( readMeMarkDown ) ) ;
		readMeReport.put( "source", readMeSource ) ;

		return readMeReport ;

	}

	public final static String REALTIME_REPORT_URL = "/kubernetes/realtime" ;

	@GetMapping ( REALTIME_REPORT_URL )
	public JsonNode kubernetesRealtimeReport (
												String project ,
												boolean blocking ) {

		var kubernetesMetricsReport = jsonMapper.createObjectNode( ) ;
		kubernetesMetricsReport.put( "error", "kubernetes not available" ) ;

		if ( StringUtils.isEmpty( project ) ) {

			project = application.getActiveProjectName( ) ;

		}

		if ( application.isAdminProfile( ) ) {

			if ( blocking ) {

				application.getHostStatusManager( ).refreshAndWaitForComplete( null ) ;

			}

			var allHostReport = application.healthManager( ).build_host_report( project ) ;

			var kubernetesHostOptional = CSAP.jsonStream( allHostReport )
					.filter( hostReport -> hostReport.findParent( "kubernetes" ) != null )
					.map( hostReport -> hostReport.path( "name" ).asText( ) )
					.findFirst( ) ;

			if ( kubernetesHostOptional.isPresent( ) ) {

				var hostName = kubernetesHostOptional.get( ) ;
				MultiValueMap<String, String> urlVariables = new LinkedMultiValueMap<String, String>( ) ;

				urlVariables.set( "apiUser", CsapUser.currentUsersID( ) ) ;
				urlVariables.set( "project", project ) ;

				String url = CsapCoreService.APP_BROWSER_URL + REALTIME_REPORT_URL ;
				List<String> hosts = new ArrayList<>( ) ;
				hosts.add( hostName ) ;

				logger.debug( "hitting: {}, hosts: {}, urlVariables: {} ", url, hosts, urlVariables ) ;

				JsonNode remoteCall = application.getOsManager( ).getServiceManager( ).remoteAgentsGet(
						hosts,
						url,
						urlVariables ) ;

				return remoteCall.path( hostName ) ;

			} else {

				logger.warn( "Failed to locate kubernetes host" ) ;
				return kubernetesMetricsReport ;

			}

		}

		if ( application.isKubernetesInstalledAndActive( ) ) {

			kubernetesMetricsReport = application.getKubernetesIntegration( ).metricsBuilder( ).cachedKubeletReport( ) ;

		}

		return kubernetesMetricsReport ;

	}

	public final static String VOLUME_REPORT_URL = "/kubernetes/volumes" ;

	@GetMapping ( VOLUME_REPORT_URL )
	public JsonNode kubernetesVolumeReport (
												String project ,
												boolean blocking ,
												String filter ,
												String apiUser ) {

		var volumeReport = jsonMapper.createArrayNode( ) ;

		if ( StringUtils.isEmpty( project ) ) {

			project = application.getActiveProjectName( ) ;

		}

		if ( application.isAdminProfile( ) ) {

			if ( blocking ) {

				application.getHostStatusManager( ).refreshAndWaitForComplete( null ) ;

			}

			var allHostReport = application.healthManager( ).build_host_report( project ) ;

			var kubernetesHostOptional = CSAP.jsonStream( allHostReport )
					.filter( hostReport -> hostReport.findParent( "kubernetes" ) != null )
					.map( hostReport -> hostReport.path( "name" ).asText( ) )
					.findFirst( ) ;

			if ( kubernetesHostOptional.isPresent( ) ) {

				var hostName = kubernetesHostOptional.get( ) ;
				MultiValueMap<String, String> urlVariables = new LinkedMultiValueMap<String, String>( ) ;

				urlVariables.set( "apiUser", CsapUser.currentUsersID( ) ) ;

				String url = CsapCoreService.APP_BROWSER_URL + VOLUME_REPORT_URL ;
				List<String> hosts = new ArrayList<>( ) ;
				hosts.add( hostName ) ;

				logger.info( "hitting: {}, hosts: {}, urlVariables: {} ", url, hosts, urlVariables ) ;

				JsonNode remoteCall = application.getOsManager( ).getServiceManager( ).remoteAgentsGet(
						hosts,
						url,
						urlVariables ) ;

				return remoteCall.path( hostName ) ;

			} else {

				logger.warn( "Failed to locate kubernetes host" ) ;
				return volumeReport ;

			}

		}

		if ( application.isKubernetesInstalledAndActive( ) ) {

			var userName = CsapUser.currentUsersID( ) ;

			if ( StringUtils.isNotEmpty( apiUser ) ) {

				userName = apiUser ;

			}

			if ( application.getActiveUsers( ).addTrail( userName, "browser-volume-report" ) ) {

				application.getEventClient( ).publishUserEvent(
						CsapEvents.CSAP_OS_CATEGORY + "/accessed",
						userName,
						"browser-volume-report", "" ) ;

			}

			volumeReport = application.getKubernetesIntegration( ).reportsBuilder( ).volumeReport( ) ;

		} else {

			// not installed
		}

		return volumeReport ;

	}

	public final static String NODE_REPORT_URL = "/kubernetes/nodes" ;

	@GetMapping ( NODE_REPORT_URL )
	public JsonNode kubernetesNodeReport (
											String project ,
											boolean blocking ,
											String filter ,
											String apiUser ) {

		var nodeReport = jsonMapper.createArrayNode( ) ;

		if ( StringUtils.isEmpty( project ) ) {

			project = application.getActiveProjectName( ) ;

		}

		if ( application.isAdminProfile( ) ) {

			if ( blocking ) {

				application.getHostStatusManager( ).refreshAndWaitForComplete( null ) ;

			}

			var allHostReport = application.healthManager( ).build_host_report( project ) ;

			var kubernetesHostOptional = CSAP.jsonStream( allHostReport )
					.filter( hostReport -> hostReport.findParent( "kubernetes" ) != null )
					.map( hostReport -> hostReport.path( "name" ).asText( ) )
					.findFirst( ) ;

			if ( kubernetesHostOptional.isPresent( ) ) {

				var hostName = kubernetesHostOptional.get( ) ;
				MultiValueMap<String, String> urlVariables = new LinkedMultiValueMap<String, String>( ) ;

				urlVariables.set( "apiUser", CsapUser.currentUsersID( ) ) ;

				String url = CsapCoreService.APP_BROWSER_URL + NODE_REPORT_URL ;
				List<String> hosts = new ArrayList<>( ) ;
				hosts.add( hostName ) ;

				logger.info( "hitting: {}, hosts: {}, urlVariables: {} ", url, hosts, urlVariables ) ;

				JsonNode remoteCall = application.getOsManager( ).getServiceManager( ).remoteAgentsGet(
						hosts,
						url,
						urlVariables ) ;

				return remoteCall.path( hostName ) ;

			} else {

				logger.warn( "Failed to locate kubernetes host" ) ;
				return nodeReport ;

			}

		}

		if ( application.isKubernetesInstalledAndActive( ) ) {

			var userName = CsapUser.currentUsersID( ) ;

			if ( StringUtils.isNotEmpty( apiUser ) ) {

				userName = apiUser ;

			}

			if ( application.getActiveUsers( ).addTrail( userName, "browser-pod-report" ) ) {

				application.getEventClient( ).publishUserEvent(
						CsapEvents.CSAP_OS_CATEGORY + "/accessed",
						userName,
						"browser-node-report", "" ) ;

			}

			nodeReport = application.getKubernetesIntegration( ).reportsBuilder( ).nodeReports( ) ;

		} else {

			// not installed
		}

		return nodeReport ;

	}

	public final static String POD_LOG_URL = "/kubernetes/pod/logs" ;

	@GetMapping ( POD_LOG_URL )
	public JsonNode kubernetesPodLogs (
										String namespace ,
										String podName ,
										String containerName ,
										boolean previousTerminated ,
										@RequestParam ( defaultValue = "500" ) int numberOfLines ,
										@RequestParam ( defaultValue = "0" ) int since ,
										boolean blocking ,
										String project ,
										String apiUser ) {

		var podLogReport = jsonMapper.createObjectNode( ) ;

		if ( StringUtils.isEmpty( project ) ) {

			project = application.getActiveProjectName( ) ;

		}

		if ( application.isAdminProfile( ) ) {

			if ( blocking ) {

				application.getHostStatusManager( ).refreshAndWaitForComplete( null ) ;

			}

			var allHostReport = application.healthManager( ).build_host_report( project ) ;

			var kubernetesHostOptional = CSAP.jsonStream( allHostReport )
					.filter( hostReport -> hostReport.findParent( "kubernetes" ) != null )
					.map( hostReport -> hostReport.path( "name" ).asText( ) )
					.findFirst( ) ;

			if ( kubernetesHostOptional.isPresent( ) ) {

				var hostName = kubernetesHostOptional.get( ) ;
				MultiValueMap<String, String> urlVariables = new LinkedMultiValueMap<String, String>( ) ;

				urlVariables.set( "namespace", namespace ) ;
				urlVariables.set( "podName", podName ) ;
				urlVariables.set( "containerName", containerName ) ;
				urlVariables.set( "previousTerminated", Boolean.toString( previousTerminated ) ) ;
				urlVariables.set( "numberOfLines", Integer.toString( numberOfLines ) ) ;
				urlVariables.set( "since", Integer.toString( since ) ) ;
				urlVariables.set( "apiUser", CsapUser.currentUsersID( ) ) ;

				String url = CsapCoreService.APP_BROWSER_URL + POD_LOG_URL ;
				List<String> hosts = new ArrayList<>( ) ;
				hosts.add( hostName ) ;

				logger.debug( "hitting: {}, hosts: {}, urlVariables: {} ", url, hosts, urlVariables ) ;

				JsonNode remoteCall = application.getOsManager( ).getServiceManager( ).remoteAgentsGet(
						hosts,
						url,
						urlVariables ) ;

				return remoteCall.path( hostName ) ;

			} else {

				logger.warn( "Failed to locate kubernetes host" ) ;
				return podLogReport ;

			}

		}

		if ( application.isKubernetesInstalledAndActive( ) ) {

			var userName = CsapUser.currentUsersID( ) ;

			if ( StringUtils.isNotEmpty( apiUser ) ) {

				userName = apiUser ;

			}

			if ( application.getActiveUsers( ).addTrail( userName, "browser-pod-report" ) ) {

				application.getEventClient( ).publishUserEvent(
						CsapEvents.CSAP_OS_CATEGORY + "/accessed",
						userName,
						"browser-pod-report", "" ) ;

			}

			podLogReport = application.getKubernetesIntegration( ).podContainerTail(
					namespace, podName, containerName,
					previousTerminated, numberOfLines, since ) ;

		} else {

			// not installed
		}

		return podLogReport ;

	}

	public final static String POD_REPORT_URL = "/kubernetes/pods" ;

	@GetMapping ( POD_REPORT_URL )
	public JsonNode kubernetesPodReport (
											String project ,
											String namespace ,
											boolean blocking ,
											String podName ,
											String apiUser ) {

		JsonNode podNamespaceReport = jsonMapper.createArrayNode( ) ;

		if ( StringUtils.isEmpty( project ) ) {

			project = application.getActiveProjectName( ) ;

		}

		if ( application.isAdminProfile( ) ) {

			if ( blocking ) {

				application.getHostStatusManager( ).refreshAndWaitForComplete( null ) ;

			}

			var allHostReport = application.healthManager( ).build_host_report( project ) ;

			var kubernetesHostOptional = CSAP.jsonStream( allHostReport )
					.filter( hostReport -> hostReport.findParent( "kubernetes" ) != null )
					.map( hostReport -> hostReport.path( "name" ).asText( ) )
					.findFirst( ) ;

			if ( kubernetesHostOptional.isPresent( ) ) {

				var hostName = kubernetesHostOptional.get( ) ;
				MultiValueMap<String, String> urlVariables = new LinkedMultiValueMap<String, String>( ) ;

				urlVariables.set( "namespace", namespace ) ;
				urlVariables.set( "apiUser", CsapUser.currentUsersID( ) ) ;
				urlVariables.set( "podName", podName ) ;

				String url = CsapCoreService.APP_BROWSER_URL + POD_REPORT_URL ;
				List<String> hosts = new ArrayList<>( ) ;
				hosts.add( hostName ) ;

				logger.info( "hitting: {}, hosts: {}, urlVariables: {} ", url, hosts, urlVariables ) ;

				JsonNode remoteCall = application.getOsManager( ).getServiceManager( ).remoteAgentsGet(
						hosts,
						url,
						urlVariables ) ;

				return remoteCall.path( hostName ) ;

			} else {

				logger.warn( "Failed to locate kubernetes host" ) ;
				return podNamespaceReport ;

			}

		}

		if ( application.isKubernetesInstalledAndActive( ) ) {

			var userName = CsapUser.currentUsersID( ) ;

			if ( StringUtils.isNotEmpty( apiUser ) ) {

				userName = apiUser ;

			}

			if ( application.getActiveUsers( ).addTrail( userName, "browser-pod-report" ) ) {

				application.getEventClient( ).publishUserEvent(
						CsapEvents.CSAP_OS_CATEGORY + "/accessed",
						userName,
						"browser-pod-report", "" ) ;

			}

			if ( StringUtils.isEmpty( podName ) ) {

				podNamespaceReport = application.getKubernetesIntegration( ).reportsBuilder( ).podSummaryReport(
						namespace, null ) ;

			} else {

				//
				// Used on kubernetes summary view to view pod container information
				//
				podNamespaceReport = application.getKubernetesIntegration( ).podContainerMetricsReport( namespace,
						podName ) ;

			}

		} else {

			logger.info( "Kubernetes not running" ) ;

		}

		return podNamespaceReport ;

	}

	public final static String POD_RESOURCE_URL = "/kubernetes/namespace/pods" ;

	@GetMapping ( POD_RESOURCE_URL )
	public JsonNode kubernetesResourceReport (
												String project ,
												String kubernetesNamespace ,
												boolean blocking ,
												String filter ,
												String apiUser ) {

		logger.info( "running report namespace: {}, podName: {}", kubernetesNamespace ) ;

		var namespaceReport = jsonMapper.createArrayNode( ) ;

		if ( StringUtils.isEmpty( project ) ) {

			project = application.getActiveProjectName( ) ;

		}

		if ( blocking && application.isAdminProfile( ) ) {

			application.getHostStatusManager( ).refreshAndWaitForComplete( null ) ;

		}

		if ( application.isAdminProfile( ) ) {

			var allHostReport = application.healthManager( ).build_host_report( project ) ;

			// logger.info( "hostReport: {}", CSAP.jsonPrint( allHostReport ) );

			var kubernetesHostOptional = CSAP.jsonStream( allHostReport )
					.filter( hostReport -> hostReport.findParent( "kubernetes" ) != null )
					.map( hostReport -> hostReport.path( "name" ).asText( ) )
					.findFirst( ) ;

			if ( kubernetesHostOptional.isPresent( ) ) {

				var hostName = kubernetesHostOptional.get( ) ;
				MultiValueMap<String, String> urlVariables = new LinkedMultiValueMap<String, String>( ) ;

				urlVariables.set( "project", project ) ;
				urlVariables.set( "namespace", kubernetesNamespace ) ;
				urlVariables.set( "apiUser", CsapUser.currentUsersID( ) ) ;

				String url = CsapCoreService.APP_BROWSER_URL + POD_RESOURCE_URL ;
				List<String> hosts = new ArrayList<>( ) ;
				hosts.add( hostName ) ;

				logger.info( "hitting: {}, hosts: {}, urlVariables: {} ", url, hosts, urlVariables ) ;

				JsonNode remoteCall = application.getOsManager( ).getServiceManager( ).remoteAgentsGet(
						hosts,
						url,
						urlVariables ) ;

				return remoteCall.path( hostName ) ;

			} else {

				logger.warn( "Failed to locate kubernetes host" ) ;
				return namespaceReport ;

			}

		}

		if ( application.isKubernetesInstalledAndActive( ) ) {

			namespaceReport = application.getKubernetesIntegration( ).reportsBuilder( ).podNamespaceSummaryReport(
					kubernetesNamespace ) ;

		} else {

			logger.debug( "Kubernetes not running service: {} current host: {}, mapping: {}",
					application.getKubernetesIntegration( ).getDiscoveredServiceName( ),
					application.getCsapHostName( ),
					application.getActiveProject( ).getHostToServicesMap( ) ) ;

		}

		return namespaceReport ;

	}

	@GetMapping ( "/hosts" )
	public JsonNode hostsReport (
									String project ,
									boolean blocking ,
									String filter ) {

		if ( StringUtils.isEmpty( project ) ) {

			project = application.getActiveProjectName( ) ;

		}

		if ( blocking && application.isAdminProfile( ) ) {

			application.getHostStatusManager( ).refreshAndWaitForComplete( null ) ;

		}

		return application.healthManager( ).build_host_report( project ) ;

	}

	@GetMapping ( "/launchers" )
	public ArrayNode launchers ( ) throws Exception {

		ArrayNode launchers = jsonMapper.createArrayNode( ) ;

		var demoLauncher = launchers.addObject( ) ;
		demoLauncher.put( "service", "csap-analytics" ) ;
		demoLauncher.put( "label", "CSAP Analytics" ) ;
		demoLauncher.put( "description", "Launches CSAP Performance Portal" ) ;

		launchers.addAll( application.environmentSettings( ).getQuickLaunchers( ) ) ;

		return launchers ;

	}

	@GetMapping ( "/logParsers" )
	public ArrayNode logParsers ( ) throws Exception {

		return application.environmentSettings( ).getLogParsers( ) ;

	}

	@GetMapping ( "/launch/{serviceName}" )
	public ObjectNode serviceLauncher (
										@PathVariable String serviceName )
		throws Exception {

		ObjectNode launchReport = jsonMapper.createObjectNode( ) ;

		String location = null ;

		if ( serviceName.equals( "csap-analytics" ) ) {

			location = application.rootProjectEnvSettings( ).getAnalyticsUiUrl( ) + "?life=" + application
					.getCsapHostEnvironmentName( ) ;

		}

		var instances = service_instances( false, serviceName, null ).at( "/instances" ) ;

		logger.info( "serviceName {}, location: {},  instances: {}", serviceName, location, instances ) ;

		if ( instances.isArray( )
				&& instances.size( ) > 0 ) {

			location = instances.at( "/0/launchUrl" ).asText( ) ;

			if ( application.isDesktopHost( ) ) {

				var optionalLaunch = CSAP.jsonStream( instances )
						.filter( instance -> ! instance.path( "host" ).asText( ).equals( "localhost" ) )
						.filter( instance -> instance.path( "running" ).asBoolean( ) )
						.map( instance -> instance.path( "launchUrl" ).asText( ) )
						.findFirst( ) ;

				if ( optionalLaunch.isPresent( ) ) {

					location = optionalLaunch.get( ) ;

				}

			}

		}

		if ( location == null ) {

			var firstInstance = application.findFirstServiceInstanceInLifecycle( serviceName ) ;

			if ( firstInstance == null ) {

				launchReport.put( "reason", "Service was not found in lifecycle" ) ;

			} else {

				launchReport.put( "reason", "unable to map to location" ) ;

			}

		} else {

			// handle multiple urls
			launchReport.put( "location", location.split( "," )[0] ) ;

		}

		return launchReport ;

	}

	@GetMapping ( "/service/instances" )

	@CsapDoc ( notes = "Summary information for lifecycle, including services, hosts, scoreCard, errors " , //
			linkTests = {
					"default package", "releasePackageExample"
			} , //
			linkGetParams = {
					"blocking=true", CsapCore.PROJECT_PARAMETER + "=projectName,blocking=true"
			} , //
			produces = {
					MediaType.APPLICATION_JSON_VALUE
			} )
	public ObjectNode service_instances (
											boolean blocking ,
											String name ,
											@RequestParam ( value = CsapCore.PROJECT_PARAMETER , required = false ) String csapProject )
		throws Exception {

		if ( blocking && application.isAdminProfile( ) ) {

			application.getHostStatusManager( ).refreshAndWaitForComplete( null ) ;

		}

		if ( csapProject == null ) {

			csapProject = application.getActiveProjectName( ) ;

		}

		var servicesReport = jsonMapper.createObjectNode( ) ;
		servicesReport.put( "host-time",
				LocalDateTime.now( ).format( DateTimeFormatter.ofPattern( "MMM.d-HH.mm.ss" ) ) ) ;

		var serviceInstances = application.serviceInstancesByName(
				csapProject,
				name ) ;

		if ( serviceInstances == null
				&& name.equals( ProcessRuntime.unregistered.getId( ) ) ) {

			// handle unregistered services

			if ( application.isAdminProfile( ) ) {

				var hostsInProject = application
						.getProject( csapProject )
						.getHostsInActiveLifecycleStream( )
						.collect( Collectors.toList( ) ) ;

				logger.debug( "csapProject: {} hostsInProject: {}", csapProject, hostsInProject ) ;

				serviceInstances = application
						.getProject( csapProject )
						.getHostsInActiveLifecycleStream( )
						.map( application.getHostStatusManager( )::findUnregisteredServices )
						.flatMap( List::stream )
						.collect( Collectors.toList( ) ) ;

			} else {

				var unregisteredContainers = application.getOsManager( ).findUnregisteredContainerNames( ) ;

				serviceInstances = unregisteredContainers.stream( )
						.map( serviceName -> {

							return ServiceInstance.buildUnregistered(
									application.getCsapHostName( ),
									serviceName ) ;

						} )
						.collect( Collectors.toList( ) ) ;

			}

		}

		if ( serviceInstances != null
				&& serviceInstances.size( ) >= 1 ) {

			var envHosts = serviceInstances.stream( )
					.map( ServiceInstance::getHostName )
					.collect( Collectors.joining( "," ) ) ;
			servicesReport.put( "envHosts", envHosts ) ;

			var firstInstance = serviceInstances.get( 0 ) ;

			servicesReport.put( "docUrl", firstInstance.getDocUrl( ) ) ;
			servicesReport.put( ServiceAttributes.description.json( ), firstInstance.getDescription( ) ) ;
			servicesReport.put( ServiceAttributes.deploymentNotes.json( ), firstInstance.getDeploymentNotes( ) ) ;
			servicesReport.put( "csapApi", firstInstance.is_csap_api_server( ) ) ;
			servicesReport.put( "csapApi", firstInstance.is_csap_api_server( ) ) ;
			servicesReport.put( "tomcat", firstInstance.isTomcatPackaging( ) ) ;
			servicesReport.put( "javaJmx", firstInstance.isJavaJmxCollectionEnabled( ) ) ;
			servicesReport.put( "datastore", firstInstance.isDataStore( ) ) ;
			servicesReport.put( "killWarnings", firstInstance.isKillWarnings( ) ) ;
			servicesReport.put( "filesOnly", firstInstance.is_files_only_package( ) ) ;
			servicesReport.put( "javaJmxCollection", firstInstance.isJavaJmxCollectionEnabled( ) ) ;
			servicesReport.put( "mavenId", firstInstance.getMavenId( ) ) ;
			servicesReport.put( "scmLocation", firstInstance.getScmLocation( ) ) ;
			servicesReport.put( "scmFolder", firstInstance.getScmBuildLocation( ) ) ;
			servicesReport.put( "scmBranch", firstInstance.getDefaultBranch( ) ) ;
			servicesReport.put( "kubernetes", firstInstance.is_cluster_kubernetes( ) ) ;
			servicesReport.put( "helm", firstInstance.isHelmConfigured( ) ) ;
			servicesReport.put( "readme", firstInstance.isReadmeConfigured( ) ) ;
			servicesReport.put( "javaCollection", firstInstance.isJavaCollectionEnabled( ) ) ;

			var alertReport = application.healthManager( ).buildServiceAlertReport( csapProject, name ) ;
			servicesReport.set( "alertReport", alertReport ) ;

			servicesReport.set( "performanceConfiguration", firstInstance.getPerformanceConfiguration( ) ) ;
			servicesReport.set( "javaLabels", JmxCommonEnum.graphLabels( ) ) ;
			servicesReport.set( "jobs", firstInstance.getJobsDefinition( ) ) ;
			servicesReport.set( "serviceLimits",
					ServiceAlertsEnum.getAdminUiLimits( firstInstance, application.rootProjectEnvSettings( ) ) ) ;
			servicesReport.put( "parameters", firstInstance.getParameters( ) ) ;
			servicesReport.set( "dockerSettings", firstInstance.getDockerSettings( ) ) ;

			long count = 0 ;

			try {

				count = fileCount( ServiceResources.serviceResourceFolder(
						firstInstance.getName( ) ).getAbsoluteFile( ) ) ;

			} catch ( Exception e ) {

				logger.warn( "Failed to count files: {}", CSAP.buildCsapStack( e ) ) ;

			}

			servicesReport.put( "resourceCount", count ) ;

			var allServiceContainers = servicesReport.putArray( "instances" ) ;

			for ( var serviceInstance : serviceInstances ) {

				if ( application.isAgentProfile( )
						&& ! serviceInstance.getHostName( ).equals( application.getCsapHostName( ) ) ) {

					continue ; // only show current host service instances

				}

				var serviceContainers = application.healthManager( ).buildServiceRuntimes( serviceInstance ) ;
				allServiceContainers.addAll( serviceContainers ) ;
				logger.debug( "allServiceContainers: {}, serviceContainers: {}", allServiceContainers.size( ),
						serviceContainers.size( ) ) ;

			}

		}

		return servicesReport ;

	}

	long fileCount ( File folderToCount ) throws Exception {

		if ( ! folderToCount.exists( ) ) {

			return 0 ;

		}

		return Files.walk( folderToCount.toPath( ) )
				.parallel( )
				.filter( p -> ! p.toFile( ).isDirectory( ) )
				.count( ) ;

	}

	@GetMapping ( "/health/settings" )
	public ObjectNode healthSettings ( )
		throws Exception {

		ObjectNode healthSettings = jsonMapper.createObjectNode( ) ;

		AlertSettings alertSettings = application.getCsapCoreService( ).getAlerts( ) ;
		HashMap<String, String> settings = new HashMap<>( ) ;
		settings.put( "Health Report Interval", alertSettings.getReport( ).getIntervalSeconds( ) + " seconds" ) ;
		settings.put( "Maximum items to store", alertSettings.getRememberCount( ) + "" ) ;
		settings.put( "Email Notifications", alertSettings.getNotify( ).toString( ) ) ;
		settings.put( "Alert Throttles", alertSettings.getThrottle( ).toString( ) ) ;

		healthSettings.set( "settings", jsonMapper.convertValue( settings, ObjectNode.class ) ) ;

		Map<String, Map<String, String>> healthUrlsByService = application.healthManager( ).buildHealthUrls( ) ;

		healthSettings.set( "healthUrlsByServiceByInstance", jsonMapper.convertValue( healthUrlsByService,
				ObjectNode.class ) ) ;

		return healthSettings ;

	}

	@GetMapping ( "/service/summary" )

	@CsapDoc ( notes = "Summary information for lifecycle, including services, hosts, scoreCard, errors " , //
			linkTests = {
					"default package", "releasePackageExample"
			} , //
			linkGetParams = {
					"blocking=true", CsapCore.PROJECT_PARAMETER + "=projectName,blocking=true"
			} , //
			produces = {
					MediaType.APPLICATION_JSON_VALUE
			} )
	public ObjectNode service_summary (
										boolean blocking ,
										@RequestParam ( value = CsapCore.PROJECT_PARAMETER , required = false ) String csapProject ,
										String cluster ,
										HttpSession session )
		throws Exception {

		ObjectNode servicesReport = jsonMapper.createObjectNode( ) ;

		String now = LocalDateTime.now( ).format( DateTimeFormatter.ofPattern( "MMM.d-HH.mm.ss" ) ) ;
		servicesReport.put( "host-time", now ) ;

		if ( StringUtils.isEmpty( csapProject ) ) {

			csapProject = application.getActiveProjectName( ) ;

		}

		Project requestedProject = application.getProject( csapProject ) ;

		var healthReport = application.healthManager( ).build_health_report(
				ServiceAlertsEnum.ALERT_LEVEL, true,
				requestedProject ) ;

		ObjectNode errorsByService = servicesReport.putObject( "errorsByService" ) ;
		JsonNode detailByHost = healthReport.path( HealthManager.HEALTH_DETAILS ) ;
		CSAP.jsonStream( detailByHost )
				.filter( JsonNode::isArray )
				.flatMap( CSAP::jsonStream )
				.filter( alert -> alert.has( "source" ) )
				.filter( alert -> alert.path( "category" ).asText( "none" ).startsWith( "os-process" ) )
				.forEach( alert -> {

					var source = alert.path( "source" ).asText( "-" ) ;
					var count = 1 + errorsByService.path( source ).asInt( 0 ) ;
					errorsByService.put( source, count ) ;

				} ) ;

		// legacy serviceRequest support
		var legacyFilter = application.getCsapHostEnvironmentName( ) ;

		if ( StringUtils.isNotEmpty( cluster ) ) {

			legacyFilter = application.getCsapHostEnvironmentName( ) + ProjectLoader.ENVIRONMENT_CLUSTER_DELIMITER
					+ cluster ;

		}

		List<String> environmentHosts = new ArrayList<>( ) ;

		try {

			environmentHosts = requestedProject.getHostsForEnvironment( legacyFilter ) ;

		} catch ( Exception e ) {

			logger.warn( "Failed to find hosts for: {}", CSAP.buildCsapStack( e ) ) ;

		}

		logger.debug( "cluster: {}, lifeCycleHostList: {}, getLifeCycleToHostMap: {} ", cluster, environmentHosts,
				requestedProject.getLifeCycleToHostMap( ) ) ;

//		Collections.sort( environmentHosts ) ;

		logger.debug( "Sorted host map to ensure kubernetes master status processing: {}", environmentHosts ) ;

		var activeServicesReport = servicesReport.putObject( "servicesActive" ) ;
		var serviceTotalCountMap = new TreeMap<String, Integer>( ) ;
		var serviceTypeMap = new TreeMap<String, String>( ) ;
		var serviceRuntimeMap = new TreeMap<String, String>( ) ;

		if ( application.isAgentProfile( ) ) {

			application.getOsManager( ).checkForProcessStatusUpdate( ) ;

			ObjectNode hostMapNode = servicesReport.putObject( HostKeys.host_status.json( ) ) ;

			application.healthManager( ).agent( ).service_summary(
					servicesReport, blocking, legacyFilter, activeServicesReport,
					serviceTotalCountMap,
					serviceTypeMap, serviceRuntimeMap,
					hostMapNode ) ;

			servicesReport.set( "users",
					application.getActiveUsers( ).updateUserAccessAndReturnAllActive(
							securitySettings.getRoles( ).getUserIdFromContext( ),
							true ) ) ;

			servicesReport.put( "lastOp", application.getLastOpMessage( ) ) ;

		} else {

			application.healthManager( ).admin( ).service_summary(
					servicesReport, blocking,
					csapProject, legacyFilter,
					environmentHosts, activeServicesReport, serviceTotalCountMap,
					serviceTypeMap, serviceRuntimeMap,
					null ) ;

			application.getActiveUsers( ).updateUserAccessAndReturnAllActive(
					securitySettings.getRoles( ).getUserIdFromContext( ),
					true ) ;

		}

		servicesReport.set( "servicesTotal", jsonMapper.valueToTree( serviceTotalCountMap ) ) ;
		servicesReport.set( "servicesType", jsonMapper.valueToTree( serviceTypeMap ) ) ;
		servicesReport.set( "servicesRuntime", jsonMapper.valueToTree( serviceRuntimeMap ) ) ;

		var serviceStartOrder = jsonMapper.createObjectNode( ) ;
		serviceTotalCountMap.keySet( ).stream( )
				.forEach( serviceName -> {

					var serviceInstance = application.findFirstServiceInstanceInLifecycle( serviceName ) ;

					if ( serviceInstance != null ) {

						serviceStartOrder.put( serviceName, serviceInstance.startOrder( ) ) ;

					} else {

						serviceStartOrder.put( serviceName, -1 ) ;

					}

				} ) ;
		;

		servicesReport.set( "startOrder", serviceStartOrder ) ;

		var clusters = requestedProject.getClustersToServicesMapInCurrentLifecycle( ) ;
		servicesReport.set( "clusters", jsonMapper.convertValue( clusters, ObjectNode.class ) ) ;

		return servicesReport ;

	}

	@Autowired ( required = false )
	CsapSecuritySettings securitySettings ;

	@GetMapping ( "/status-report" )
	public JsonNode statusReport (
									boolean blocking ,
									String project ,
									String filter ) {

		if ( StringUtils.isEmpty( project ) ) {

			project = application.getActiveProjectName( ) ;

		}

		if ( StringUtils.isEmpty( filter ) ) {

			filter = ".*" ;

		}
		// ArrayList<String> lifeCycleHostList = csapApp
		// .getLifeCycleToHostMap().get(clusterFilter);

		Project requestedProject = application.getProject( project ) ;

		if ( blocking && application.isAdminProfile( ) ) {

			application.getHostStatusManager( ).refreshAndWaitForComplete( null ) ;

		}

		var statusReport = application.healthManager( ).build_host_summary_report( project ) ;

		var includeKubernetesCheck = false ;

		if ( application.isAdminProfile( ) ) {

			includeKubernetesCheck = true ; // detects k8s crashed processes

		}

		var healthReport = application.healthManager( ).build_health_report(
				ServiceAlertsEnum.ALERT_LEVEL, includeKubernetesCheck,
				requestedProject ) ;

		logger.debug( "healthReport: {}", healthReport ) ;

		var alerts = statusReport.putArray( "alerts" ) ;
		// JsonNode detailByHost = healthReport.path( HealthManager.HEALTH_DETAILS ) ;
		JsonNode summaryByHost = healthReport.path( HealthManager.HEALTH_SUMMARY ) ;
		CSAP.jsonStream( summaryByHost )
				.filter( JsonNode::isArray )
				.flatMap( CSAP::jsonStream )
				.map( JsonNode::asText )
				.forEach( desc -> {

					alerts.add( desc ) ;

				} ) ;

		if ( application.isAgentProfile( ) ) {

			try {

				application.healthManager( ).addCpuMetrics( statusReport ) ;
				var interval = application.metricManager( ).firstHostCollectionInterval( ) ;
				var servicesOnHost = application.metricManager( ).getOsProcessCollector( interval )
						.buildServicesAvailableReport( ) ;
				// statusReport.set( "serviceNames", servicesOnHost ) ;

				// map kubernetes services to serviceIds
				var serviceIdMapping = CSAP.jsonStream( servicesOnHost )

						.map( JsonNode::asText )

						.collect( Collectors.toMap(
								serviceOsCollectId -> serviceOsCollectId,
								serviceOsCollectId -> {

									var service = application.flexFindFirstInstanceCurrentHost( serviceOsCollectId ) ;

									if ( service != null ) {

										return service.getName( ) ;

									}

									return "service-not-found" ;

								},
								( a , b ) -> a, // merge function should never be used
								( ) -> new TreeMap<String, String>( String.CASE_INSENSITIVE_ORDER ) ) ) ;

				statusReport.set( "serviceIdMapping", jsonMapper.convertValue( serviceIdMapping, ObjectNode.class ) ) ;
				// application.flexFindFirstInstance( svcName_port_or_name ) ;

				statusReport.set( "servicesWithHealth",
						jsonMapper.convertValue( application.healthManager( ).getHealthServiceIds( ),
								ArrayNode.class ) ) ;

			} catch ( Exception e ) {

				logger.warn( "Failed to build agent services: {}", CSAP.buildCsapStack( e ) ) ;

			}

		}

		statusReport.set( "users",
				application.getActiveUsers( ).updateUserAccessAndReturnAllActive(
						securitySettings.getRoles( ).getUserIdFromContext( ),
						true ) ) ;

		statusReport.put( "kubernetes-master", requestedProject.getKubernetesMasterHost( application
				.getCsapHostEnvironmentName( ) ) ) ;

		statusReport.put( "kubernetes-service",
				requestedProject.getKubernetesServiceName(
						application.getCsapHostEnvironmentName( ) ) ) ;

		var containerService = "docker" ;

		if ( application.findFirstServiceInstanceInLifecycle( "podman-system-service" ) != null ) {

			containerService = "podman-system-service" ;

		}

		if ( application.findFirstServiceInstanceInLifecycle( containerService ) == null
				&& application.findFirstServiceInstanceInLifecycle( "docker-monitor" ) != null ) {

			containerService = "docker-monitor" ;

		}

		statusReport.put( "container-service", containerService ) ;

		return statusReport ;

	}

	@Inject
	@Qualifier ( "csapEventsService" )
	private RestTemplate csapEventsService ;

	public final static String CSAP_EVENT_REPORT_URL = "/events/csap" ;

	@GetMapping ( CSAP_EVENT_REPORT_URL )
	public JsonNode csapEvents (
									@RequestParam ( value = CsapCore.PROJECT_PARAMETER , required = false ) String csapProject ,
									@RequestParam ( defaultValue = "100" ) int count ,
									@RequestParam ( defaultValue = CsapEvents.CSAP_UI_CATEGORY
											+ "/*" ) String category ,
									String from ,
									String to ) {

		logger.info( "category: '{}' from: '{}', to: '{}'", category, from, to ) ;

		if ( StringUtils.isEmpty( csapProject ) ) {

			csapProject = application.getActiveProjectName( ) ;

		}

		var projectParam = ",project=" + csapProject ;

		if ( csapProject.equals( CsapCore.ALL_PACKAGES ) ) {

			projectParam = "" ;

		}

		var fromParam = "" ;

		if ( StringUtils.isNotEmpty( from ) ) {

			fromParam = ",from=" + from ;

		}

		var toParam = "" ;

		if ( StringUtils.isNotEmpty( from ) ) {

			toParam = ",to=" + to ;

		}

		ObjectNode eventReport = jsonMapper.createObjectNode( ) ;

		var projectSettings = application.rootProjectEnvSettings( ) ;

		if ( count > 2000 ) {

			count = 2000 ;

		}

		var filteredEvents = eventReport.putArray( "events" ) ;

		if ( ! application.rootProjectEnvSettings( ).isEventPublishEnabled( ) ) {

			logger.info( "Stubbing out data for trends - add csap events services" ) ;
			eventReport.put( "count", "disabled" ) ;
			eventReport.put( "message", "csap-event-service disabled - using stub data" ) ;
			return eventReport ;

		}

		String eventUrl = projectSettings.getEventQueryUrl( )
				+ "?start=0&length=" + count + "&searchText=" ;

		try {

			var appId = projectSettings.getEventDataUser( ) ;

			if ( application.isRunningOnDesktop( ) ) {

				appId = "SensusCsap" ;

			}

			var searchTextParam = "appId=" + appId
					+ projectParam
					+ ",lifecycle=" + application.getCsapHostEnvironmentName( )
					+ ",simpleSearchText=" + category
					+ ",isDataRequired=false"
					+ fromParam
					+ toParam ;

			eventUrl += searchTextParam ;

			eventReport.put( "source", eventUrl ) ;

			buildCsapEvents( eventUrl, filteredEvents ) ;

			// eventReport.setAll( eventData ) ;
		} catch ( Exception e ) {

			logger.error( "Failed getting activity count from url: {}, reason: {}", eventUrl, CSAP.buildCsapStack(
					e ) ) ;
			eventReport.put( "url", eventUrl ) ;
			eventReport.put( "message", "Error during Access: " + e.getMessage( ) ) ;

		}

		return eventReport ;

	}

	private void buildCsapEvents ( String eventUrl , ArrayNode filteredEvents ) {

		String today = LocalDateTime.now( ).format( DateTimeFormatter.ofPattern( "yyyy-MM-dd" ) ) ;
		String this_year = LocalDateTime.now( ).format( DateTimeFormatter.ofPattern( "yyyy" ) ) ;

		logger.info( "restUrl: {}", eventUrl ) ;

		ObjectNode eventResponse = csapEventsService.getForObject( eventUrl, ObjectNode.class ) ;
		var csapEvents = eventResponse.path( "data" ) ;

		if ( csapEvents.isArray( ) ) {

			CSAP.jsonStream( csapEvents )
					.filter( JsonNode::isObject )
					.map( event -> (ObjectNode) event )
					.forEach( rawEvent -> {

						logger.debug( "rawEvent: {}", rawEvent ) ;
						var event = filteredEvents.addObject( ) ;
						var date = rawEvent.path( "createdOn" ).path( "date" ).asText( ) ;

						if ( date.equals( today ) ) {

							date = "" ;

						} else if ( date.startsWith( this_year ) ) {

							try {

								LocalDate dateParsed = LocalDate.parse( date ) ;
								date = dateParsed.format( DateTimeFormatter.ofPattern( "MMM dd" ) ) ;

							} catch ( Exception e ) {

								logger.warn( "Failed parsing event date: {}, {}", date, CSAP.buildCsapStack( e ) ) ;
								date = "" ;

							}

						}

						event.put( "date", date ) ;
						event.set( "time", rawEvent.path( "createdOn" ).path( "time" ) ) ;
						event.set( "host", rawEvent.path( "host" ) ) ;
						event.set( "summary", rawEvent.path( "summary" ) ) ;
						event.set( "category", rawEvent.path( "category" ) ) ;
						event.set( "user", rawEvent.path( "metaData" ).path( "uiUser" ) ) ;
						event.set( "id", rawEvent.path( "_id" ).path( "$oid" ) ) ;

					} ) ;
			;

		}

	}

	public final static String KUBERNETES_EVENT_REPORT_URL = "/events/kubernetes" ;

	@GetMapping ( KUBERNETES_EVENT_REPORT_URL )
	public JsonNode kubernetesEvents (
										@RequestParam ( value = CsapCore.PROJECT_PARAMETER , required = false ) String csapProject ,
										@RequestParam ( defaultValue = "100" ) int count ) {

		if ( StringUtils.isEmpty( csapProject ) ) {

			csapProject = application.getActiveProjectName( ) ;

		}

		ObjectNode eventReport = jsonMapper.createObjectNode( ) ;

		if ( count > 2000 ) {

			count = 2000 ;

		}

		var filteredEvents = eventReport.putArray( "events" ) ;

		if ( application.isAdminProfile( ) ) {
			// if ( blocking ) {
			// application.getHostStatusManager().refreshAndWaitForComplete( null ) ;
			// }

			var allHostReport = application.healthManager( ).build_host_report( csapProject ) ;

			var kubernetesHostOptional = CSAP.jsonStream( allHostReport )
					.filter( hostReport -> hostReport.findParent( "kubernetes" ) != null )
					.map( hostReport -> hostReport.path( "name" ).asText( ) )
					.findFirst( ) ;

			if ( kubernetesHostOptional.isPresent( ) ) {

				var hostName = kubernetesHostOptional.get( ) ;
				MultiValueMap<String, String> urlVariables = new LinkedMultiValueMap<String, String>( ) ;

				urlVariables.set( "apiUser", CsapUser.currentUsersID( ) ) ;
				urlVariables.set( "count", Integer.toString( count ) ) ;

				String url = CsapCoreService.APP_BROWSER_URL + KUBERNETES_EVENT_REPORT_URL ;
				List<String> hosts = new ArrayList<>( ) ;
				hosts.add( hostName ) ;
				eventReport.put( "source", url ) ;
				logger.debug( "hitting: {}, hosts: {}, urlVariables: {} ", url, hosts, urlVariables ) ;

				JsonNode remoteCall = application.getOsManager( ).getServiceManager( ).remoteAgentsGet(
						hosts,
						url,
						urlVariables ) ;

				return remoteCall.path( hostName ) ;

			} else {

				logger.warn( "Failed to locate kubernetes host" ) ;
				return eventReport ;

			}

		}

		if ( application.isKubernetesInstalledAndActive( ) ) {

			buildKubernetesEvents( filteredEvents, count ) ;

		}

		return eventReport ;

	}

	private void buildKubernetesEvents ( ArrayNode filteredEvents , int maxEvents ) {

		var eventReport = application.getKubernetesIntegration( ).reportsBuilder( ).eventReport( null, maxEvents ) ;
		String today = LocalDateTime.now( ).format( DateTimeFormatter.ofPattern( "MM/dd" ) ) ;
		logger.debug( "today: {}", today ) ;
		// String this_year = LocalDateTime.now().format( DateTimeFormatter.ofPattern(
		// "yyyy" ) ) ;

		if ( eventReport.isArray( ) ) {

			CSAP.jsonStream( eventReport )
					.filter( JsonNode::isObject )
					.map( event -> (ObjectNode) event )
					.forEach( rawEvent -> {

						logger.debug( "rawEvent: {}", rawEvent ) ;
						var event = filteredEvents.addObject( ) ;
						var dateTime = rawEvent.path( "dateTime" ).asText( ).split( " ", 2 ) ;
						var date = "" ;
						var time = "" ;

						if ( dateTime.length == 2 ) {

							date = dateTime[0] ;
							time = dateTime[1] ;

							if ( date.equals( today ) ) {

								date = "" ;

							} else {

								try {

									LocalDate dateParsed = LocalDate.parse( date ) ;
									date = dateParsed.format( DateTimeFormatter.ofPattern( "MMM dd" ) ) ;

								} catch ( Exception e ) {

									logger.warn( "Failed parsing event date: {}, {}", date, CSAP.buildCsapStack( e ) ) ;
									date = "" ;

								}

							}

						}

						event.put( "date", date ) ;
						event.put( "time", time ) ;
						event.put( "host", rawEvent.path( "host" ).asText( ) ) ;
						event.put( "reason", rawEvent.path( "reason" ).asText( ) ) ;
						event.put( "simpleName", rawEvent.path( "simpleName" ).asText( ) ) ;
						event.put( "component", rawEvent.path( "component" ).asText( ) ) ;

						var summary = rawEvent.path( "message" ).asText( ) ;
						var count = rawEvent.path( "count" ).asInt( ) ;
						event.put( "summary", summary ) ;
						event.put( "count", count ) ;
						event.set( "namespace", rawEvent.path( "namespace" ) ) ;
						event.set( "kind", rawEvent.path( "kind" ) ) ;
						event.set( "type", rawEvent.path( "type" ) ) ;
						event.set( KubernetesJson.apiPath.json( ), rawEvent.path( KubernetesJson.apiPath.json( ) ) ) ;

					} ) ;
			;

		}

	}

	@GetMapping ( value = "/event" )
	public ObjectNode get_event ( String id ) {

		var projectSettings = application.rootProjectEnvSettings( ) ;

		String eventUrl = projectSettings.getEventQueryUrl( )
				+ "/getById?id=" + id ;

		logger.info( "restUrl: {}", eventUrl ) ;

		ObjectNode eventResponse = csapEventsService.getForObject( eventUrl, ObjectNode.class ) ;

		return eventResponse ;

	}

}
