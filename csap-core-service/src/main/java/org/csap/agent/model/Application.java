package org.csap.agent.model ;

import static org.csap.agent.integrations.VersionControl.CONFIG_SUFFIX_FOR_UPDATE ;

import java.io.BufferedWriter ;
import java.io.File ;
import java.io.IOException ;
import java.io.StringWriter ;
import java.lang.reflect.Method ;
import java.net.InetAddress ;
import java.net.URI ;
import java.net.URL ;
import java.net.URLClassLoader ;
import java.net.UnknownHostException ;
import java.nio.charset.StandardCharsets ;
import java.nio.file.Files ;
import java.nio.file.Path ;
import java.nio.file.StandardOpenOption ;
import java.text.DateFormat ;
import java.text.SimpleDateFormat ;
import java.time.LocalDateTime ;
import java.time.format.DateTimeFormatter ;
import java.util.ArrayList ;
import java.util.Arrays ;
import java.util.Collection ;
import java.util.Collections ;
import java.util.Comparator ;
import java.util.Date ;
import java.util.HashMap ;
import java.util.HashSet ;
import java.util.Iterator ;
import java.util.LinkedHashMap ;
import java.util.List ;
import java.util.Map ;
import java.util.Optional ;
import java.util.OptionalInt ;
import java.util.TreeMap ;
import java.util.TreeSet ;
import java.util.concurrent.Executors ;
import java.util.concurrent.ScheduledExecutorService ;
import java.util.concurrent.ScheduledFuture ;
import java.util.concurrent.TimeUnit ;
import java.util.regex.Matcher ;
import java.util.regex.Pattern ;
import java.util.stream.Collectors ;
import java.util.stream.IntStream ;
import java.util.stream.Stream ;

import javax.inject.Inject ;
import javax.servlet.http.HttpServletResponse ;

import org.apache.commons.io.FileUtils ;
import org.apache.commons.io.filefilter.FileFilterUtils ;
import org.apache.commons.lang3.StringUtils ;
import org.apache.commons.lang3.concurrent.BasicThreadFactory ;
import org.csap.agent.ApplicationConfiguration ;
import org.csap.agent.CsapApis ;
import org.csap.agent.CsapConstants ;
import org.csap.agent.CsapTemplates ;
import org.csap.agent.container.C7 ;
import org.csap.agent.container.ContainerIntegration ;
import org.csap.agent.container.kubernetes.KubernetesConfiguration ;
import org.csap.agent.container.kubernetes.KubernetesIntegration ;
import org.csap.agent.container.kubernetes.KubernetesSettings ;
import org.csap.agent.integrations.CsapEvents ;
import org.csap.agent.integrations.HttpdIntegration ;
import org.csap.agent.linux.HostInfo ;
import org.csap.agent.linux.HostStatusManager ;
import org.csap.agent.linux.OsCommandRunner ;
import org.csap.agent.linux.OutputFileMgr ;
import org.csap.agent.services.OsCommands ;
import org.csap.agent.services.OsManager ;
import org.csap.agent.services.ServiceOsManager ;
import org.csap.agent.stats.MetricCategory ;
import org.csap.agent.ui.explorer.KubernetesExplorer ;
import org.csap.helpers.CSAP ;
import org.csap.helpers.CsapApplication ;
import org.csap.helpers.CsapRestTemplateFactory ;
import org.csap.integations.CsapInformation ;
import org.csap.integations.CsapWebServerConfig ;
import org.csap.integations.CsapWebSettings ;
import org.csap.integations.micrometer.CsapMeterUtilities ;
import org.csap.integations.micrometer.MeterReport ;
import org.csap.security.CsapSecurityRestFilter ;
import org.csap.security.CsapUser ;
import org.csap.security.config.CsapSecuritySettings ;
import org.jasypt.encryption.pbe.StandardPBEStringEncryptor ;
import org.jasypt.exceptions.EncryptionOperationNotPossibleException ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.beans.factory.annotation.Autowired ;
import org.springframework.beans.factory.annotation.Value ;
import org.springframework.context.annotation.Lazy ;
import org.springframework.context.event.ContextRefreshedEvent ;
import org.springframework.context.event.EventListener ;
import org.springframework.core.annotation.Order ;
import org.springframework.core.env.Environment ;
import org.springframework.stereotype.Service ;
import org.springframework.web.client.RestTemplate ;
import org.springframework.web.servlet.resource.ResourceUrlProvider ;

import com.fasterxml.jackson.core.JsonParseException ;
import com.fasterxml.jackson.core.JsonProcessingException ;
import com.fasterxml.jackson.core.SerializableString ;
import com.fasterxml.jackson.core.io.CharacterEscapes ;
import com.fasterxml.jackson.core.type.TypeReference ;
import com.fasterxml.jackson.databind.JsonNode ;
import com.fasterxml.jackson.databind.ObjectMapper ;
import com.fasterxml.jackson.databind.node.ArrayNode ;
import com.fasterxml.jackson.databind.node.ObjectNode ;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry ;

/**
 *
 * Provide access to the CSAP application definition. Key items in the
 * application definition:
 * <ul>
 * <li>lifecycles - contain clusters, versions, and hosts</li>
 * <li>services - list of services and respective attributes</li>
 * </ul>
 *
 * @see ServiceInstance
 *
 * @see EnvironmentSettings
 *
 * @see <a href="doc-files/csagent.jpg"> Click to enlarge
 *      <IMG width=300 SRC="doc-files/csagent.jpg"></a>
 *
 * @see <a href="doc-files/spring.jpg" > Click to enlarge
 *      <IMG width=300 SRC="doc-files/modelDocs.jpg"></a>
 *
 * @author someDeveloper
 *
 */
@Service
public class Application {

	final Logger logger = LoggerFactory.getLogger( getClass( ) ) ;

	ScoreReport scoreReport ;

	final static String PERFORMANCE_ID = "Application." ;
	final static String CSAP_DEFAULT_DEFINITION_NAME = "scan-for-file" ;
	// final static String CSAP_DEFAULT_DEFINITION_NAME = "Application.json" ;
	final static String SCRIPTS_RUN = "scripts-run" ;
	final static String YAML_LOAD = "kubernetes-yaml" ;

	ProjectLoader projectLoader = null ;
	HealthManager healthManager = null ;
	MetricManager metricManager = null ;

	CsapSecuritySettings securitySettings ;
	StandardPBEStringEncryptor encryptor ;

	public static final String DESKTOP_STUB_HOST = "csap-dev01.***REMOVED***" ;
	public static String DESKTOP_CLUSTER_HOST = "localhost" ; // localhost.fqdn.test

	private String csapHostName = DESKTOP_CLUSTER_HOST ;
	private String agentEndpoint = ":8011" ; // required for junits

	private File csapInstallFolder ;
	private File csapWorkingFolder ;
	private File csapDefinitionFolder ;
	private File rootDefinitionFile ;
	private File csapBuildFolder ;

	private ApplicationConfiguration csapCoreService ;

	private boolean adminMode = false ;

	static private boolean developerMode = false ;

	private boolean autoReload = true ;
	private boolean skipScanAndActivate = false ;

	private ActiveUsers activeUsers ;

	@Lazy
	@Autowired
	private CsapApis csapApis ;

//	public CsapApis apiServices ( ) {
//
//		return csapApis ;
//
//	}

	@Autowired
	public Application (
			CsapSecuritySettings securitySettings,
			ApplicationConfiguration csapCoreService,
			StandardPBEStringEncryptor encryptor ) {

		this.csapCoreService = csapCoreService ;
		this.securitySettings = securitySettings ;
		this.encryptor = encryptor ;

		// crioIntegraton = new CrioIntegraton( jacksonMapper, this ) ;

		logger.info(
				CSAP.buildDescription(
						"StartUp Environment Variables",
						CsapApplication.CSAP_INSTALL_VARIABLE, csapCoreService.getInstallationFolder( ),
						"csap working folder", csapCoreService.getWorkingFolder( ),
						"definition strict mode", Boolean.toString( csapCoreService.isDefinitionStrictMode( ) ),
						"hostFqdn", getHostFqdn( ),
						"csapName", System.getenv( "csapName" ),
						"csapHttpPort", System.getProperty( "csapHttpPort" ),
						// "isCrioInstalledAndActive", isCrioInstalledAndActive( ),
						"csapLife", System.getProperty( "csapLife" ) ) ) ;

		csapInstallFolder = new File( csapCoreService.getInstallationFolder( ) ) ;
		csapWorkingFolder = new File( csapCoreService.getWorkingFolder( ) ) ;

		ObjectNode template = null ;

		try {

			template = (ObjectNode) jacksonMapper.readTree( CsapTemplates.edit_service.getFile( ) ) ;

		} catch ( Exception e ) {

			String reason = CSAP.buildCsapStack( e ) ;
			logger.warn( "Failed parsing {}, {}", CsapTemplates.edit_service.getFile( ), reason ) ;

		}

		serviceTemplates = template ;

	}

	//
	// WARNING: spring injections are STRONGLY ordered: do not reorder without
	// extensive testing
	//

	//
	// WARNING: spring injections are STRONGLY ordered: do not reorder without
	// extensive testing
	//

	@Value ( "${user.home:/dummy}" )
	private String agentRunHome = "willBeReplacedOnSpringInit" ;

	@Value ( "${user.name:dummy}" )
	private String agentRunUser = "willBeReplacedOnSpringInit" ;

	@Value ( "${allowRemoteJmx:false}" )
	private String allowRemoteJmx = "initialized-by-spring" ;

	//
	// CsapBareTest Junits only
	//
	static public Application testBuilder ( ) {

		var csapInfo = new CsapInformation( ) ;

		var webSettings = new CsapWebSettings( null, csapInfo ) ;

		var csapWeb = new CsapWebServerConfig( webSettings ) ;

		var csapCore = new ApplicationConfiguration( csapWeb, null ) ;

		var metricUtilities = new CsapMeterUtilities( new SimpleMeterRegistry( ) ) ;
		metricUtilities.set_self_for_junit( metricUtilities ) ;
		var meterReport = new MeterReport( metricUtilities.getSimpleMeterRegistry( ) ) ;
		metricUtilities.setMeterReport( meterReport ) ;

		var theApp = new Application( null, csapCore, null ) ;

		var osManager = new OsManager( new OsCommands( ) ) ;
		theApp.csapApis = new CsapApis( theApp, osManager, metricUtilities, new CsapEvents( ) ) ;

		theApp.projectLoader = new ProjectLoader( theApp.csapApis ) ;
		theApp.healthManager = new HealthManager( theApp.csapApis, theApp.jacksonMapper ) ;
		theApp.metricManager = new MetricManager( theApp.csapApis, theApp.jacksonMapper ) ;

		theApp.restTemplateFactory = new CsapRestTemplateFactory( null, null ) ;

		var kubConfig = new KubernetesConfiguration( ) ;
		kubConfig.setCsapApi( theApp.csapApis ) ;

		theApp.csapApis.setKubernetesIntegration( new KubernetesIntegration(
				new KubernetesSettings( ),
				kubConfig,
				new ObjectMapper( ) ) ) ;

		var serviceManager = new ServiceOsManager( theApp.csapApis ) ;
		osManager.setServiceManager( serviceManager ) ;

		theApp.setSkipScanAndActivate( true ) ;
		theApp.setAutoReload( false ) ;

		return theApp ;

	}

	//
	// CsapThinTest
	//
	public Application ( OsManager osManager ) {

		this(
				null,
				new ApplicationConfiguration( null, null ),
				null ) ;

		// testBuilder() ;
		logger.info( CsapApplication.testHeader( "Using stubbed OsManager, KubernetesIntegration" ) ) ;

		var testMapper = new ObjectMapper( ) ;

		csapApis = new CsapApis( this, osManager, null, new CsapEvents( ) ) ;

		csapApis.setKubernetesIntegration( new KubernetesIntegration(
				new KubernetesSettings( ),
				new KubernetesConfiguration( ),
				testMapper ) ) ;

		// var dockerConfiguration = new DockerConfiguration() ;
		// dockerConfiguration.setDocker( new DockerSettings() );
		// dockerIntegration = new DockerIntegration( dockerConfiguration , testMapper )
		// ;

	}

	public boolean isAllowRemoteJmx ( ) {

		return allowRemoteJmx.equals( "true" ) ;

	}

	// @PostConstruct
	@EventListener ( {
			ContextRefreshedEvent.class
	} )
	@Order ( CsapConstants.CSAP_MODEL_LOAD_ORDER )
	public void initialize ( ) {

		logger.info( CsapApplication.highlightHeader( "Initializing Application" ) ) ;

		activeUsers = new ActiveUsers( csapApis, securitySettings ) ;

		httpdIntegration = new HttpdIntegration( csapApis ) ;
		scoreReport = new ScoreReport( csapApis, jacksonMapper ) ;
		projectLoader = new ProjectLoader( csapApis ) ;
		healthManager = new HealthManager( csapApis, jacksonMapper ) ;
		metricManager = new MetricManager( csapApis, jacksonMapper ) ;

		rootDefinitionFile = new File(
				csapInstallFolder + "/" + CsapApplication.DEFINITION_FOLDER_NAME + "/"
						+ CSAP_DEFAULT_DEFINITION_NAME ) ;

		if ( isDesktopProfileActiveOrSpringNull( ) || isJunit( ) ) {

			setDeveloperMode( true ) ;

			if ( csapApis.metrics( ) == null ) {

				logger.warn( CsapApplication.testHeader( "Building Meter Utilities" ) ) ;
				var metricUtilities = new CsapMeterUtilities( new SimpleMeterRegistry( ) ) ;
				metricUtilities.set_self_for_junit( metricUtilities ) ;
				// metricUtilities.setSimpleMeterRegistry( new SimpleMeterRegistry( ) ) ;
				var meterReport = new MeterReport( metricUtilities.getSimpleMeterRegistry( ) ) ;
				metricUtilities.setMeterReport( meterReport ) ;

				csapApis.setMetricUtilities( metricUtilities ) ;

			}

		}

		String mode = "Agent" ;

		if ( isAdminProfileActive( ) ) {

			mode = "admin" ;
			setJvmInManagerMode( true ) ;

			// still needed?
			// CsapCore.AGENT_NAME_PORT = "admin_8911" ;
		} else {

			// only override if in agent mode. Otherwise we use csap-core.yaml
			if ( ( csapInfo != null ) &&
					! csapInfo.getHttpPort( ).equals( "-1" ) ) { // junits

				csapCoreService.setAgentPort( Integer.parseInt( csapInfo.getHttpPort( ) ) ) ;
				csapCoreService.setAgentContextPath( csapInfo.getHttpContext( ) ) ;

			}

		}

		setAgentEndpoint( ":" + getAgentPort( ) + getAgentContextPath( ) ) ;

		if ( getAgentContextPath( ).length( ) == 1 ) {

			setAgentEndpoint( ":" + getAgentPort( ) ) ;

		}

		try {

			if ( isDeveloperMode( ) ) {

				developmentModeSetup( ) ;

			}

			if ( ! rootDefinitionFile.exists( ) ) {

				if ( rootDefinitionFile.getName( ).equals( CSAP_DEFAULT_DEFINITION_NAME ) ) {

					var csapDefinitionFolder = rootDefinitionFile.getParentFile( ) ;
					var firstProjectFile = findFirstProjectFile( csapDefinitionFolder ) ;

					if ( firstProjectFile != null ) {

						rootDefinitionFile = firstProjectFile ;

					}

				} else {

					logger.warn( "Did not find: {}", rootDefinitionFile ) ;

				}

			}

			String mailInfo = "not initialized" ;

			if ( springEnvironment != null ) {

				mailInfo = springEnvironment.getProperty( "spring.mail.host" ) + " port "
						+ springEnvironment.getProperty( "spring.mail.port" ) ;

			}

			logger.warn( CSAP.buildDescription(
					"Platform Settings",
					"mode", mode,
					"working folder - agent", processWorkingDirectory,
					"working folder - platform", csapWorkingFolder,
					"agent endpoint", getAgentEndpoint( ),
					"agent url", getAgentUrl( "some-host", "/some-command" ),

					CsapApplication.CSAP_INSTALL_VARIABLE, csapInstallFolder,
					"csapFqdn", System.getenv( "csapFqdn" ),
					"Definition", rootDefinitionFile,
					"Host Url Pattern", getAgentHostUrlPattern( false ),
					"mail", mailInfo ) ) ;

			// final CHECK
			if ( ! rootDefinitionFile.exists( ) ) {

				logger.warn( CsapApplication.header( "Definition file not found: '{}'" ),
						rootDefinitionFile.getAbsolutePath( ) ) ;

			}

			updateApplicationVariables( ) ;

		} catch ( Exception e ) {

			logger.error( "Failed to initialize: {}", CSAP.buildCsapStack( e ) ) ;

		}

		try {

//			if ( isAutoReload( ) && ( csapEventClient != null ) ) {
			if ( isAutoReload( )
					&& csapApis.events( ) != null ) {

				run_application_scan( ) ;

			} else {

				logger.warn( CsapApplication.testHeader( "Skipping Application scan" ) ) ;

			}

		} catch ( Exception e ) {

			logger.error( "Failed to update cache {}", CSAP.buildCsapStack( e ) ) ;

		}

		logger.info( CsapApplication.highlightHeader( "Application Initialization Complete" ) ) ;

	}

	public File findFirstProjectFile ( File csapDefinitionFolder ) {

		File firstProjectFile = null ;

		try (
				Stream<Path> filesystemPaths = Files.list( csapDefinitionFolder.toPath( ) ) ) {

			var templates = filesystemPaths
					.filter( Files::isRegularFile )
					.filter( path -> path.getFileName( ).toString( ).endsWith( "-project.json" ) )
					.sorted( Comparator.comparing( path -> path.getFileName( ).toString( ) ) )
					.collect( Collectors.toList( ) ) ;

			var projectListing = templates.stream( )
					.map( Path::getFileName )
					.map( Path::toString )
					.collect( Collectors.joining( ", " ) ) ;

			logger.info( "*-project.json files - '{}'", projectListing ) ;

			var optionalPath = templates.stream( ).findFirst( ) ;

			if ( optionalPath.isPresent( ) ) {

				firstProjectFile = optionalPath.get( ).toFile( ) ;

			} else {

				logger.warn( "Failed to find a matching *-project.json file" ) ;

			}

		} catch ( Exception e ) {

			logger.info( "failed getting listing: {}", CSAP.buildCsapStack( e ) ) ;

		}

		return firstProjectFile ;

	}

	private void updateApplicationVariables ( )
		throws UnknownHostException {

		httpdIntegration.updateConstants( ) ;

		csapBuildFolder = csapPlatformPath( "build" ) ;
		csapDefinitionFolder = csapPlatformPath( CsapApplication.DEFINITION_FOLDER_NAME ) ;
		//
		// csapDefinitionFolder = getDefinitionFile().getParentFile().getAbsolutePath()
		// ;

		if ( springEnvironment != null
				&& StringUtils.isNotEmpty( springEnvironment.getProperty( "csapFqdn" ) ) ) {

			// default to use env variable in csap-start.sh
			csapHostName = springEnvironment.getProperty( "csapFqdn" ) ;

		} else {

			// fallback use java networking
			InetAddress addr = InetAddress.getLocalHost( ) ;
			csapHostName = addr.getHostName( ) ;

		}

		if ( isRunningOnDesktop( ) ) {

			csapHostName = DESKTOP_CLUSTER_HOST ;
			csapDefinitionFolder = applicationDefinition( ).getParentFile( ).getAbsoluteFile( ) ;
			//
			logger.info( CsapApplication.testHeader(
					"Running on desktop, host set to '{}', csapDefinitionFolder: {}" ),
					csapHostName,
					csapDefinitionFolder ) ;

		}

		// strip off domain for easier management
		csapHostName = hostShortName( csapHostName ) ;
//		if ( csapHostName.indexOf( "." ) != -1 ) {
//			csapHostName = csapHostName.substring( 0, csapHostName.indexOf( "." ) ) ;
//		}

		// hook for testing on eclipse
		// logger.info( "Host name is: {}", HOST_NAME ) ;

		logger.warn( CSAP.buildDescription(
				"Application Settings",
				"active host", csapHostName,
				"csap package folder", getCsapPackageFolder( ),
				"build location", csapBuildFolder ) ) ;

		if ( isAdminProfile( ) && springEnvironment != null ) {

			logger.info( "csap-admin - scheduling application scan every '10 seconds'" ) ;
			applicationScheduler = csapApplicationThreadPool.scheduleWithFixedDelay(
					( ) -> run_application_scan( ),
					10,
					10,
					TimeUnit.SECONDS ) ;

		} else if ( isAgentProfile( ) && springEnvironment != null ) {

			logger.info( "csap-agent - scheduling runaway resources every '60 seconds'" ) ;
			applicationScheduler = csapApplicationThreadPool.scheduleWithFixedDelay(
					( ) -> healthManager( ).killRunaways( ),
					60,
					60,
					TimeUnit.SECONDS ) ;

		}

		// initial schedule at 10s for start up loading
		namespaceScheduler = csapApplicationThreadPool.scheduleWithFixedDelay(
				( ) -> checkNamespaces( ),
				10,
				10,
				TimeUnit.SECONDS ) ;

	}

	ScheduledFuture<?> namespaceScheduler = null ;
	int numRuns = 0 ;

	public void checkNamespaces ( ) {

		if ( isAgentProfile( )
				&& ! csapApis.isKubernetesInstalledAndActive( ) ) {

			return ;

		}

		if ( numRuns++ > 3 ) {

			logger.debug( "Swithing namespace scans to every 30 seconds" ) ;
			namespaceScheduler.cancel( false ) ;
			namespaceScheduler = csapApplicationThreadPool.scheduleWithFixedDelay(
					( ) -> checkNamespaces( ),
					30,
					30,
					TimeUnit.SECONDS ) ;

		}

		// check to force agent reload
		if ( getProjectLoader( ).isNamespacesStableAndModified( ) ) {

			// trigger it
			sumOfDefinitionTimestamps = -1 ;
			updateApplication( ) ;

		}

	}

	public String getCsapHostName ( ) {

		return csapHostName ;

	}

	public String getCsapHostShortname ( ) {

		return hostShortName( getCsapHostName( ) ) ;

	}

	public String hostShortName ( String host ) {

		if ( host.indexOf( "." ) == -1 ) {

			return host ;

		}

		return host.split( Pattern.quote( "." ) )[0] ;

	}

	final static int MAX_RESOLUTION_ATTEMPTS = 8 ;

	public String resolveDefinitionVariables ( String sourceWithVariables , ServiceInstance serviceInstance ) {

		return resolveDefinitionVariables(
				sourceWithVariables,
				serviceInstance, false ) ;

	}

	boolean printedStrictWarning = false ;

	public String resolveDefinitionVariables (
												String sourceWithVariables ,
												ServiceInstance serviceInstance ,
												boolean byPassLegacy ) {

		var resolvedVariables = sourceWithVariables ;

		if ( ! csapCoreService.isDefinitionStrictMode( ) ) {

			if ( ! printedStrictWarning ) {

				logger.warn( CsapApplication.header(
						"Definition is NOT in strict mode: every variable/script/shell will be checked for migration" ) ) ;
				printedStrictWarning = true ;

			}

			if ( resolvedVariables.contains( CsapConstants.CSAP_LEGACY_PREFIX ) ) {

				logger.debug( CsapApplication.header( "Triggering legacy migrations - resolve asap: \n {}" ),
						sourceWithVariables ) ;
				csapApis.metrics( ).incrementCounter(
						"Application.service.legacy-variables."
								+ serviceInstance.getName( ) ) ;

			}

//		if ( ! resolvedVariables.contains( CsapCore.CSAP_VARIABLE_PREFIX ) ) {
//		if ( ! byPassLegacy
//				&& resolvedVariables.contains( CsapCore.CSAP_LEGACY_PREFIX ) ) {
			if ( ! byPassLegacy && ! resolvedVariables.contains( CsapConstants.CSAP_VARIABLE_PREFIX ) ) {

				// RNI: legacy configuration still includes CSAP_LEGACY_PREFIX on generated
				// specs
				resolvedVariables = getProjectLoader( ).getProjectMigrator( ).migrateServiceVariables(
						resolvedVariables ) ;

			}

		}

		for ( var i = 0; i < MAX_RESOLUTION_ATTEMPTS; i++ ) {

			if ( resolvedVariables.contains( CsapConstants.CSAP_VARIABLE_PREFIX ) ) {

				resolvedVariables = resolveOnePassVariables( serviceInstance, resolvedVariables ) ;

			} else {

				break ;

			}

		}

		if ( resolvedVariables.contains( CsapConstants.CSAP_VARIABLE_PREFIX ) ) {

			logger.warn( "Excceeded maximum resolution attempts: {}, {}", MAX_RESOLUTION_ATTEMPTS,
					sourceWithVariables ) ;

		}

		return resolvedVariables ;

	}

	private String resolveOnePassVariables (
												ServiceInstance serviceInstance ,
												String definition ) {

		definition = serviceInstance.resolveRuntimeVariables( definition ) ;

		LinkedHashMap<String, String> environmentVariables = new LinkedHashMap<>( ) ;

		StringWriter sw = new StringWriter( ) ;
		BufferedWriter stringWriter = new BufferedWriter( sw ) ;

		var replaceUserVariables = false ;
		csapApis.osManager( ).getServiceManager( ).addServiceEnvironment( serviceInstance, environmentVariables,
				replaceUserVariables, stringWriter ) ;

		logger.debug( "Checking {} for variables {}", definition, environmentVariables ) ;

		// agentEnvVars.keySet().stream()
		// .filter(key -> key.startsWith( "csap-def" ))
		// .forEach( key -> {
		for ( String variableName : environmentVariables.keySet( ) ) {

			var value = environmentVariables.get( variableName ) ;

			if ( variableName.startsWith( CsapConstants.CSAP_VARIABLE_PREFIX ) && variableName.length( ) > 2 ) {

				definition = definition.replaceAll(
						Matcher.quoteReplacement( variableName ),
						Matcher.quoteReplacement( value ) ) ;

			} else if ( ! csapCoreService.isDefinitionStrictMode( )
					&& variableName.startsWith( CsapConstants.CSAP_USER_PREFIX ) ) {

				var fullKey = "$" + variableName ;

				logger.debug( "Checking: {} for {}, replacing with {}", definition, fullKey, value ) ;

				definition = definition.replaceAll(
						Matcher.quoteReplacement( fullKey ),
						Matcher.quoteReplacement( value ) ) ;

			}

		}

		return definition ;

	}

	static boolean latestKubernetesCredentials = false ;

	private void developmentModeSetup ( ) {

		logger.warn( CsapApplication.testHeader( "Running in Development mode, stubbed OS output will be used" ) ) ;
		// addPathToJVM( "devData/stubResults" );

		if ( isJunit( ) ) {

			// addPathToJVM( "devData/stubResults" );
			// special hook for eclipse JUnit
			JUNIT_CLUSTER_PREFIX = "target/junit/" + "JUNIT_CLUSTER_" ;

		}

		try {

			csapWorkingFolder = csapWorkingFolder.getCanonicalFile( ) ;
			csapInstallFolder = csapInstallFolder.getCanonicalFile( ) ;

		} catch ( Exception e ) {

			logger.error( "Failed: {}", CSAP.buildCsapStack( e ) ) ;

		}

		if ( StringUtils.isEmpty( csapCoreService.getDefinitionFolder( ) ) ) {

			// Junits - spring disabled
			rootDefinitionFile = new File( csapInstallFolder,
					"src/test/resources/definitions/" + CSAP_DEFAULT_DEFINITION_NAME ) ;

		} else {

			// Junits - spring enabled or DESKTOP testing
			logger.debug( "definition folder: {}", csapCoreService.getDefinitionFolder( ) ) ;
			rootDefinitionFile = new File( csapCoreService.getDefinitionFolder( ), CSAP_DEFAULT_DEFINITION_NAME ) ;

			if ( ! latestKubernetesCredentials
					&& isAgentProfile( ) ) {

				File extractDir = new File( System.getProperty( "user.home" ), "agent-desktop-kubernetes-folder" ) ;

				try {

					if ( csapApis.kubernetes( ) != null ) {

						KubernetesIntegration.buildAndCacheDesktopCredentials(
								logger,
								csapApis.kubernetes( ).getSettings( ).getTestCredentialUrl( ),
								extractDir ) ;
						latestKubernetesCredentials = true ;

					} else {

						logger.warn( "Kubernetes configuration not loaded" ) ;

					}

				} catch ( Exception e ) {

					logger.warn( "Failed to get kubernetes: {}", CSAP.buildCsapStack( e ) ) ;

				}

			}

		}

		try {

			rootDefinitionFile = rootDefinitionFile.getCanonicalFile( ) ;

		} catch ( IOException e ) {

			logger.error( "Failed to resolve desktop definition" ) ;

		}

		logger.debug( "Definition file: {}", rootDefinitionFile.getAbsolutePath( ) ) ;

		if ( ! getCsapInstallFolder( ).exists( ) ) {

			logger.warn( CsapApplication.testHeader( "Creating csap-platform folder : {}" ), getCsapInstallFolder( )
					.getAbsolutePath( ) ) ;
			getCsapInstallFolder( ).mkdirs( ) ;

		}

		if ( ! getCsapWorkingFolder( ).exists( ) ) {

			getCsapWorkingFolder( ).mkdirs( ) ;

		}

	}

	/**
	 * TOKEN is used so that path on other VMs is relative to local CSAP install
	 * folder
	 *
	 * @return
	 */

	public enum FileToken {
		PLATFORM( "__platform__" ),
		WORKING( "__working__" ),
		PROPERTY( "__props__" ),
		HOME( "__home__" ),
		ROOT( "__root__" ),
		DOCKER( "__docker__" ),
		JOURNAL( "__journal__" );

		public String value ;

		private FileToken ( String value ) {

			this.value = value ;

		}
	}

	private static final String SAVED = "saved" ;

	private static final String CSAP_SCRIPTS_TOKEN = FileToken.PLATFORM.value + "/" + SAVED + "/" + SCRIPTS_RUN + "/" ;
	public static final String CSAP_SAVED_TOKEN = FileToken.PLATFORM.value + "/" + SAVED + "/" ;

	public static final String CSAP_SERVICE_PACKAGES = "/packages/" ;
	public static final String CSAP_PACKAGES_TOKEN = FileToken.PLATFORM.value + CSAP_SERVICE_PACKAGES ;

	public static final String ALL_PACKAGES = "All Packages" ;

	public File applicationDefinition ( ) {

		return rootDefinitionFile ;

	}

	public static String SKIP_TOMCAT_JAR_SCAN = "skipTomcatJarScan" ;

	public static final String SYS_USER = "System" ;

	public synchronized File getCsapWorkingTempFolder ( ) {

		File processingTemp = new File( csapWorkingFolder, CsapConstants.AGENT_NAME + "-temp" ) ;

		if ( isRunningOnDesktop( ) ) {

			logger.debug( "Skipping creation of {} on desktop", processingTemp.getAbsolutePath( ) ) ;

		} else {

			if ( ! processingTemp.exists( ) ) {

				logger.info( "Creating: {}", processingTemp.getAbsolutePath( ) ) ;

				if ( ! processingTemp.mkdirs( ) ) {

					logger.error( "\n\n\n ********** Failed creating: {}",
							processingTemp.getAbsolutePath( ) ) ;

				}

			}

		}

		return processingTemp ;

	}

	// public static String getCSAP_WORKING_FOLDER () {
	// return CSAP_WORKING_FOLDER ;
	// }

	// public static String stagingPathForParsingOnly () {
	// return CSAP_INSTALLATION_FOLDER ;
	// }

	public String getInstallationFolderAsString ( ) {

		var installationFolder = getCsapInstallFolder( ).getAbsolutePath( ) ;

		if ( installationFolder.contains( ":" ) ) {

			logger.debug( "windows path: {} , converting to linux format", installationFolder ) ;
			return stripWindowsPaths( installationFolder ) ;

		}

		return installationFolder ;

	}

	public String stripWindowsPaths ( String path ) {

		return path.substring( path.lastIndexOf( ":" ) + 1 )
				.replaceAll( Pattern.quote( "\\" ), "/" ) ;

	}

	// private File csapInstallationFolder ;

	public File getCsapInstallFolder ( ) {

		if ( csapInstallFolder == null ) {

			var now = LocalDateTime.now( ).format( DateTimeFormatter.ofPattern( "MMM.d-HH.mm.ss" ) ) ;
			csapInstallFolder = new File( "target/junit/junit-" + now + "/staging" ) ;
			logger.warn( "csapInstallFolder is null, stubbing {}", csapInstallFolder ) ;

		}

		// initStagingFolderIfNeeded() ;
		return csapInstallFolder ;

	}

	public File csapPlatformTemp ( ) {

		return csapPlatformPath( "/temp/" ).getAbsoluteFile( ) ;

	}

	public File csapPlatformPath ( String path ) {

		return new File( getCsapInstallFolder( ), path ) ;

	}

	public File getCsapWorkingFolder ( ) {

		return csapWorkingFolder ;

	}

	public File getCsapWorkingSubFolder ( String serviceName ) {

		if ( isRunningOnDesktop( ) && serviceName.equals( CsapConstants.AGENT_NAME ) ) {

			return getCsapWorkingFolder( ) ;

		}

		return new File( getCsapWorkingFolder( ), serviceName ) ;

	}

	public String check_for_stub (
									String commandOutput ,
									String path ) {

		if ( ! Application.isRunningOnDesktop( ) ) {

			return commandOutput ;

		}

		OsManager.setLinuxLineFormat( ) ;

		File testFile = new File( getCsapInstallFolder( ), "devData/stubResults/" + path ) ;

		if ( ! testFile.exists( ) ) {

			testFile = new File( getCsapInstallFolder( ),
					"../../devData/stubResults/" + path ) ;

		}

		if ( ! testFile.exists( ) ) {

			testFile = new File( "devData/stubResults/" + path ) ;

		}

		if ( isDisplayOnDesktop( ) ) {

			logger.warn( "\n\n Desktop Testing - using stub output for linux {} \n\n", testFile.getAbsolutePath( ) ) ;

		}

		return readFile( testFile ) ;

	}

	public static String readFile (
									File file ) {

		try {

			if ( file == null || ! file.exists( ) ) {

				LoggerFactory
						.getLogger( Application.class )
						.info( "Failed to read null or non-existant file: '{}'", file
								.toURI( )
								.getPath( ) ) ;
				return "File is null or does not exist: " + file ;

			}

			return FileUtils.readFileToString( file ) ;

		} catch ( IOException e ) {

			LoggerFactory
					.getLogger( Application.class )
					.warn( "Failed to read: " + file
							.toURI( )
							.getPath( ),
							e ) ;
			return "Failed to read: " + file
					.toURI( )
					.getPath( )
					+ " due to: " + e.getMessage( ) ;

		}

	}

	private HttpdIntegration httpdIntegration ;

	public HttpdIntegration getHttpdIntegration ( ) {

		return httpdIntegration ;

	}

	public String getFromSpringEnvironment (
												String variableName ,
												String defaultIfNull ) {

		if ( springEnvironment == null )
			return defaultIfNull ;

		return springEnvironment.getProperty( variableName ) ;

	}

	static boolean printPatternWarning = true ;

	private String agentUrl = null ;

	///
	// lots of junit cases - run all
	///

	String junitFqdn ;

	public void setHostNameForDefinitionMapping ( String testHost ) {

		logger.warn( "Definition Host Mapping: {}", testHost ) ;
		csapHostName = testHost ;

		if ( isJunit( ) ) {

			junitFqdn = csapHostName ;
			csapHostName = hostShortName( junitFqdn ) ;

		}

	}

	public String getHostUsingFqdn (
										String host ) {

		String fqdnHost = host ;

		// logger.info( "fqdnHost: {}", fqdnHost );
		if ( fqdnHost.indexOf( "." ) == -1 && getHostFqdn( ).contains( "." ) ) {

			fqdnHost += getHostFqdn( ).substring( getHostFqdn( ).indexOf( "." ) ) ;

		}

		return fqdnHost ;

	}

	public String getHostFqdn ( ) {

		String fqdn = System.getenv( "csapFqdn" ) ;

		if ( isJunit( ) && StringUtils.isNotEmpty( junitFqdn ) ) {

			fqdn = junitFqdn ;

		} else if ( isRunningOnDesktop( ) ) {

			fqdn = DESKTOP_STUB_HOST ;

		}

		if ( fqdn == null ) {

			fqdn = getCsapHostName( ) ;

		}

		return fqdn ;

	}

	public String getDefinitionHostFqdn ( ) {

		if ( isRunningOnDesktop( )
				&& ! isJunit( ) ) {

			return DESKTOP_CLUSTER_HOST ;

		}

		return getHostFqdn( ) ;

	}

	///
	//
	///

	public int getAgentPort ( ) {

		return csapCoreService.getAgentPort( ) ;

	}

	public String getAgentContextPath ( ) {

		return csapCoreService.getAgentContextPath( ) ;

	}

	public String getAgentEndpoint ( ) {

		return this.agentEndpoint ;

	}

	public String getAgentSslPortAndContext ( ) {

		var agentSslPort = getAgentPort( ) + CsapWebSettings.SSL_PORT_OFFSET ;
		var sslEndpoint = getAgentEndpoint( ).replace(
				":" + getCsapCoreService( ).getAgentPort( ),
				":" + agentSslPort ) ;

		return sslEndpoint ;

	}

	public String getAdminSslPortAndContext ( ) {

		var admin = findFirstServiceInstanceInLifecycle( CsapConstants.ADMIN_NAME ) ;

		var adminPort = Integer.parseInt( admin.getPort( ) ) + CsapWebSettings.SSL_PORT_OFFSET ;
		var sslEndpoint = ":" + adminPort + "/" + admin.getContext( ) ;

		return sslEndpoint ;

	}

	private void setAgentEndpoint ( String agentEndpoint ) {

		// Slogger.info( "updating {} to {}", this.agentEndpoint, agentEndpoint );
		this.agentEndpoint = agentEndpoint ;

	}

	public String getAgentHostUrlPattern (
											boolean checkForInternalOverride ) {

		if ( springEnvironment == null ) {

			var defaultPattern = "http://CSAP_HOST." + CsapConstants.DEFAULT_DOMAIN + getAgentEndpoint( ) ;

			if ( printPatternWarning ) {

				logger.warn( "springEnvironment is null, returning junit default: '{}'", defaultPattern ) ;

			}

			printPatternWarning = false ;
			return defaultPattern ;

		}

		if ( agentUrl == null ) {

			agentUrl = csapCoreService.getHostUrlPattern( ) ;

			logger.info( "initial host-url-pattern {}", agentUrl ) ;

			if ( ( StringUtils.isEmpty( agentUrl )
					|| agentUrl.equalsIgnoreCase( "auto" ) )
					&& springEnvironment != null ) {

				// change fqdn into http://CSAP_HOST.yourcompany.com:8011/
				var fqdn = springEnvironment.getProperty( "csapFqdn", getCsapHostName( ) ) ;

				if ( isRunningOnDesktop( ) ) {

					fqdn = DESKTOP_STUB_HOST ;

				}

				if ( fqdn.contains( "." ) ) {

					var scheme = "http://CSAP_HOST" ;
					var context = getAgentEndpoint( ) ;

					if ( getCsapCoreService( ).getCsapWebServer( ) != null
							&& getCsapCoreService( ).getCsapWebServer( ).isSslClient( ) ) {

						scheme = "https://CSAP_HOST" ;
						context = getAgentSslPortAndContext( ) ;

					}

					var newAgentUrl = scheme
							+ fqdn.substring( fqdn.indexOf( "." ) )
							+ context ;

					agentUrl = newAgentUrl ;

				}

			}

			logger.debug( CsapApplication.header( "csap-core.host-url-pattern: '{}', resolved: '{}'" ),
					csapCoreService.getHostUrlPattern( ), agentUrl ) ;

		}

		// only if external (browser) networking different from host network
		var internalAgentUrl = csapCoreService.getHostUrlPatternInternal( ) ;

		if ( ! checkForInternalOverride
				|| StringUtils.isEmpty( internalAgentUrl ) ) {

			// use for all Browser UI requests, and most of the time when
			// internal/external hosts are the same
			return agentUrl ;

		} else {

			// private networking is being used - so local host url is different
			// when
			// agent connectivity is being used.
			return internalAgentUrl ;

		}

	}

	// Used to get launch urls for services, using the suffix in yml
	public String buildJavaLaunchUrl (
										String host ,
										String port ,
										String context ) {

		var appUrl = getAgentHostUrlPattern( true ).replaceAll( "CSAP_HOST", host ) ;

		if ( ! host.equals( hostShortName( host ) ) ) {

			logger.debug( "Using qualified host: {}", host ) ;
			appUrl = "http://" + host + ":" + appUrl.split( ":" )[2] ;

		}

		// logger.debug( "agentPortAndName: {}, appUrl: {} , ", agentPortAndName, appUrl
		// ) ;
		// strip off agent and port context
		if ( appUrl.endsWith( getAgentEndpoint( ) ) ) {

			appUrl = appUrl.substring( 0, appUrl.indexOf( getAgentEndpoint( ) ) ) ;

		} else if ( appUrl.endsWith( getAgentSslPortAndContext( ) ) ) {

			appUrl = appUrl.substring( 0, appUrl.indexOf( getAgentSslPortAndContext( ) ) ) ;

			// always prefer http for cli examples
//			appUrl = appUrl.replace( "https:", "http:" ) ;

			if ( getCsapCoreService( ).getCsapWebServer( ) != null
					&& getCsapCoreService( ).getCsapWebServer( ).isSslClient( ) ) {

				try {

					var sslPort = Integer.parseInt( port ) + CsapWebSettings.SSL_PORT_OFFSET ;
					port = "" + sslPort ;

				} catch ( NumberFormatException e ) {

					logger.warn( "Failed to parse int {}", CSAP.buildCsapStack( e ) ) ;

				}

			}

		}

		appUrl += ":" + port + context ;
		// logger.debug("launch url: {}", appUrl) ;
		// logger.debug("host pattern: {}, appUrl: {}", getAgentHostUrlPattern(
		// true ), appUrl );

		return appUrl ;

	}

	public String getAgentUrl (
								String target ) {

		return getAgentUrl( null, target, false ) ;

	}

	public String getAgentUrl (
								String host ,
								String target ) {

		return getAgentUrl( host, target, false ) ;

	}

	public String getAgentUrl (
								String host ,
								String target ,
								boolean checkForInternalOverride ) {

		if ( host.equals( "$host" )
				|| StringUtils.isEmpty( host ) ) {

			host = getCsapHostName( ) ;

		}

		try {

			var pattern = getAgentHostUrlPattern( checkForInternalOverride ) ;
			var url = pattern.replaceAll( "CSAP_HOST", host ) + target ;

			if ( ! host.equals( hostShortName( host ) ) ) {

				logger.debug( "Using qualified host: {}", host ) ;
				url = "http://" + host + ":" + url.split( ":" )[2] ;

			}

			//
			// legacy
			//
			if ( ! url.startsWith( "https" ) ) {

				var convertToSsl = false ;

				var sslHosts = csapCoreService.getSslForceHosts( ) ;

				if ( sslHosts == null
						&& getCsapCoreService( ).getCsapWebServer( ) != null
						&& getCsapCoreService( ).getCsapWebServer( ).isSslClient( ) ) {

					convertToSsl = true ;

				} else if ( sslHosts != null
						&& sslHosts.contains( host )
						&& getCsapCoreService( ).getCsapWebServer( ) != null
						&& getCsapCoreService( ).getCsapWebServer( ).isSslClient( ) ) {

					convertToSsl = true ;

				}

				if ( convertToSsl ) {

					url = url.replace( "http:", "https:" ) ;

					var agentSslPort = getAgentPort( ) + 2 ;
					url = url.replace(
							":" + getCsapCoreService( ).getAgentPort( ),
							":" + agentSslPort ) ;

				}

			}

//			 logger.info( "pattern: {} resolved: {}", pattern, url );
			return url ;

		} catch ( Exception e ) {

			logger.error( "Error in host name: {} for target: {}, {}", host, target, CSAP.buildCsapStack( e ) ) ;
			var url = getAgentHostUrlPattern( checkForInternalOverride ).replaceAll( "CSAP_HOST",
					"ERROR_IN_HOST_NAME" ) + target ;
			return url ;

		}

	}

	private static String JUNIT_CLUSTER_PREFIX = "JUNIT_CLUSTER_" ;

	@Autowired
	private Environment springEnvironment ;

	public String getCompanyConfiguration (
											String key ,
											String defaultValue ) {

		if ( springEnvironment == null )
			return defaultValue ;
		// logger.info("Getting: {}", key) ;
		return springEnvironment.getProperty( key, defaultValue ) ;

	}

	public boolean isCompanyVariableConfigured (
													String key ) {

		if ( springEnvironment != null && springEnvironment.getProperty( key ) != null ) {

			return true ;

		}

		return false ;

	}

	public List<String> getDockerUiDefaultImages ( ) {

		return csapCoreService.getDockerUiDefaultImages( ) ;

	}

	public boolean isSvnEnabled ( ) {

		return csapCoreService.isSvnEnabled( ) ;

	}

	public boolean isJunit ( ) {

		if ( springEnvironment == null ) {

			return true ;

		}

		return Arrays.asList( springEnvironment.getActiveProfiles( ) ).contains( "junit" ) ;

	}

	public boolean isDesktopProfileActiveOrSpringNull ( ) {

		if ( springEnvironment == null ) {

			return true ;

		}

		return Arrays.asList( springEnvironment.getActiveProfiles( ) ).contains( "desktop" ) ;

	}

	int numDecodeWarnings = 0 ;

	public String decode (
							String input ,
							String description ) {

		String result = input ;

		if ( encryptor == null )
			return result ;

		try {

			result = encryptor.decrypt( input ) ;

		} catch ( EncryptionOperationNotPossibleException e ) {

			if ( logger.isDebugEnabled( ) || ( numDecodeWarnings++ < 10 ) ) {

				logger.warn( "{} is not encrypted.  Use CSAP encrypt to generate", description ) ;

			}

		} catch ( Exception e ) {

			logger.warn( "{} Encryption error: {}",
					description, CSAP.buildCsapStack( e ) ) ;

		}

		return result ;

	}

	private boolean isAdminProfileActive ( ) {

		// JUNITS
		if ( springEnvironment == null )
			return false ;

		return Arrays.asList( springEnvironment.getActiveProfiles( ) ).contains( "admin" ) ;

	}

	static List<String> pathsAdded = new ArrayList<>( ) ;

	public void addPathToJVM (
								String pathToBeAdded ) {

		// junits will repeatedly initialize
		if ( pathsAdded.contains( pathToBeAdded ) )
			return ;

		pathsAdded.add( pathToBeAdded ) ;

		try {

			logger.warn( "Adding new path to JVM resources: {}", pathToBeAdded ) ;
			File pathInFile = new File( pathToBeAdded ) ;

			URI uriWithNewPath = pathInFile.toURI( ) ;
			ClassLoader urlClassLoader = ClassLoader.getSystemClassLoader( ) ;
			Class<URLClassLoader> urlClass = URLClassLoader.class ;

			Method method = urlClass.getDeclaredMethod( "addURL", new Class[] {
					URL.class
			} ) ;
			method.setAccessible( true ) ;
			method.invoke( urlClassLoader, new Object[] {
					uriWithNewPath.toURL( )
			} ) ;

			// URI uriWithNewPath = pathInFile.toURI();
			// URLClassLoader urlClassLoader = (URLClassLoader)
			// ClassLoader.getSystemClassLoader();
			// Class<URLClassLoader> urlClass = URLClassLoader.class;
			//
			// Method method = urlClass.getDeclaredMethod( "addURL", new Class[]
			// { URL.class } );
			// method.setAccessible( true );
			// method.invoke( urlClassLoader, new Object[] {
			// uriWithNewPath.toURL() } );
		} catch ( Exception e ) {

			logger.error( "* Failed to add path: {}", CSAP.buildCsapStack( e ) ) ;

		}

	}

	@Autowired
	ResourceUrlProvider mvcResourceUrlProvider ;

	public String versionedUrl (
									String path ) {

		return mvcResourceUrlProvider.getForLookupPath( path ) ;

	}

	// cache blowout strategy for requires.js: generate a unique path on every start
	public String requiresUrl (
								String path ) {

		String requiresJsName = mvcResourceUrlProvider.getForLookupPath( path ) ;
		return requiresJsName ;

	}

	public Project getRootProject ( ) {

		return projectLoader.getActiveRootProject( ) ;

	}

	public List<String> getPackageNames ( ) {

		return getRootProject( )
				.getReleasePackageNames( )
				.collect( Collectors.toList( ) ) ;

	}

	public Map<String, String> getPackageNameToFileMap ( ) {

		HashMap<String, String> map = new HashMap<>( ) ;

		getReleasePackageStream( ).forEach( releasePackage -> {

			map.put( releasePackage.getName( ), releasePackage.getSourceFileName( ) ) ;

		} ) ;

		return map ;

	}

	public Map<String, String> getHelpMenuMap ( ) {

		return rootProjectEnvSettings( )
				.getHelpMenuUrlMap( ) ;

	}

	public List<Project> getPackageModels ( ) {

		return getRootProject( )
				.getProjects( )
				.collect( Collectors.toList( ) ) ;

	}

	public Stream<Project> getReleasePackageStream ( ) {

		return getRootProject( )
				.getProjects( ) ;

	}

	// public Stream<ArrayList<InstanceConfig>>
	// getServiceConfigStreamCurrentLC() {
	// return _globalModel.getSvcToConfigMapCurrentLC().values().stream();
	// }
	public Project getActiveProject ( ) {

		return getRootProject( ).getActiveModel( ) ;

	}

	public String getActiveProjectName ( ) {

		return getRootProject( )
				.getActiveModel( )
				.getName( ) ;

	}

	public Project getProject ( String projectName ) {

		if ( projectName.equals( "default" ) ) {

			return getRootProject( ) ;

		}

		if ( projectName.equals( ALL_PACKAGES ) ) {

			return getRootProject( )
					.getAllPackagesModel( ) ;

		}

		return getRootProject( )
				.getReleasePackage( projectName ) ;

	}

	/**
	 * Main Data is cached for 5 minutes; war timestamps are cached for 60s
	 */
	public void run_application_scan ( ) {

		if ( isSkipScanAndActivate( ) ) {

			logger.warn( CsapApplication.testHeader( "minimumActive in use" ) ) ;
			return ;

		}

		updateApplication( ) ;

	}

	/**
	 * Timestamps shown on ui , only checked every 60s to avoid overhead
	 */
	public void updateServiceTimeStamps ( ) {

		if ( isAgentProfile( ) && csapApis.osManager( ) != null ) {

			var millisSince = System.currentTimeMillis( ) - lastKubernetesFileSystemScanMillis ;

			if ( millisSince > ( TimeUnit.MINUTES.toMillis( 10 ) ) ) {

				logger.debug( "10 minute rescan interval: rescanning kubernetes clusters" ) ;
				// resetServiceFileSystemScan( true ) ;
				markServicesForFileSystemScan( false ) ;
				lastKubernetesFileSystemScanMillis = System.currentTimeMillis( ) ;

			}

			scanServiceFileSystems( ) ;

		}

	}

	String serviceDebugName = null ;

	public void activateProject ( Project rootProject ) {

		if ( isSkipScanAndActivate( ) ) {

			logger.warn( CsapApplication.testHeader( "minimumActive in use" ) ) ;
			return ;

		}

		csapApis.events( ).initialize( rootProjectEnvSettings( ), getActiveProjectName( ) ) ;

		// Trigger the httpdWorkers for load balancing
		if ( rootProject.getServiceToAllInstancesMap( ).isEmpty( ) ) {

			logger.error( "\n\n ====================== ERROR PARSING capability =====================\n\n" ) ;

		} else if ( ! isAdminProfile( ) ) {

			// generate HTTP mappings
			buildHttpdConfiguration( ) ;

		}

		// Finally update httpd instances with test urls
		rootProject.getHttpdTestUrls( ).clear( ) ;

		if ( getActiveProject( )
				.getServiceInstances( "httpd" ).count( ) == 0 ) {

			rootProject.getHttpdTestUrls( ).add( "http://noHttpdConfigured" ) ;

		} else {

			getActiveProject( )
					.getServiceInstances( "httpd" )
					.forEach(
							serviceInstance -> {

								rootProject.getHttpdTestUrls( ).add(
										serviceInstance.getHostName( ) + ":" + serviceInstance.getPort( ) ) ;

							} ) ;

		}

		// trigger application score calculation
		scoreReport.resetAppScoreCards( ) ;

	}

	public ProjectLoader getProjectLoader ( ) {

		return projectLoader ;

	}

	public StringBuilder getLastTestParseResults ( ) {

		return lastParseResults ;

	}

	private StringBuilder lastParseResults = null ;

	public boolean loadCleanForJunits (
										boolean isTest ,
										File definitionFile ) {

		try {

			loadDefinitionForJunits( isTest, definitionFile ) ;
			return true ;

		} catch ( Exception e ) {

			logger.warn( "Failed loading definition: \n '{}' \n details: {}", definitionFile, CSAP.buildCsapStack(
					e ) ) ;
			return false ;

		}

	}

	// Helper method for testing
	public boolean loadDefinitionForJunits (
												boolean isTest ,
												File definitionFile )
		throws JsonProcessingException ,
		IOException {

		if ( isJunit( ) ) {

			logger.info( "unit tests updating csapWorkingFolder and csapInstallFolder" ) ;
			String now = LocalDateTime.now( ).format( DateTimeFormatter.ofPattern( "MMM.d-HH.mm.ss" ) ) ;
			csapWorkingFolder = new File( "target/junit/junit-" + now + "/processing" ) ;
			csapInstallFolder = new File( "target/junit/junit-" + now + "/staging" ) ;

		}

		if ( springEnvironment != null ) {

			//
			// reset singleton to current application since non-spring junits will be
			// creating new instances
			//
			CsapApis.setInstance( csapApis ) ;

			// setting up for junits running in spring context
			updateApplicationVariables( ) ;

		} else {

			csapBuildFolder = csapPlatformPath( "build" ) ;
			csapDefinitionFolder = definitionFile.getParentFile( ) ;

		}

		var setupMessage = CSAP.buildDescription(
				"",
				"Definition", definitionFile.getParentFile( ).getName( ) + "/" + definitionFile.getName( ),
				"csapWorkingFolder", csapWorkingFolder,
				CsapApplication.CSAP_INSTALL_VARIABLE, csapInstallFolder,
				"csapBuildFolder", csapBuildFolder ) ;

		logger.info( CsapApplication.testHeader( setupMessage ) ) ;

		lastParseResults = projectLoader.process( isTest, definitionFile ) ;
		applicationLoadedAtMillis = System.currentTimeMillis( ) ;
		csapApis.osManager( ).resetAllCaches( ) ;

		int errorIndex = lastParseResults.indexOf( CsapConstants.CONFIG_PARSE_ERROR ) ;

		if ( errorIndex >= 0 ) {

			logger.warn( "Found errors: {}", lastParseResults.substring( errorIndex ) ) ;
			return false ;

		}

		logger.info( "springEnvironment: {}, isJvmInManagerMode: {} ", springEnvironment, isAdminProfile( ) ) ;

		if ( springEnvironment != null && ! isAdminProfile( ) ) {

			csapApis.osManager( ).wait_for_initial_process_status_scan( 30 ) ;
			metricManager( ).startCollectorsForJunit( ) ;

		}

		setBootstrapComplete( ) ;
		int warnIndex = lastParseResults.indexOf( CsapConstants.CONFIG_PARSE_WARN ) ;

		if ( warnIndex >= 0 ) {

			logger.warn( CsapApplication.highlightHeader( "Found application definition warnings" ) ) ;
			logger.warn( "\n\n {} \n\n", lastParseResults.substring( warnIndex ) ) ;

			return false ;

		}

		return true ;

	}

	public JsonNode getDefinitionForActivePackage ( ) {

		return getActiveProject( )
				.getSourceDefinition( ) ;

	}

	/**
	 * Mongo chokes on "." in field names. Arguably not a desirable practice anyway
	 *
	 * @param definitionNode
	 * @param location
	 * @return
	 */
	public ObjectNode getDefinitionForAllPackages ( ) {

		ObjectNode applicationDefinition = jacksonMapper.createObjectNode( ) ;

		logger.debug( "Definition Folder: {}", getDefinitionFolder( ) ) ;

		ArrayNode definitions = applicationDefinition.putArray( "definitions" ) ;

		getReleasePackageStream( ).forEach( releasePackage -> {

			ObjectNode item = definitions.addObject( ) ;
			item.put( "fileName", releasePackage.getSourceFileName( ) ) ;

			try {

				item.put( "content", releasePackage.getSourceDefinition( ).toString( ) ) ;

			} catch ( Exception e ) {

				item.put( "content", "Failed to parse:" + e.getMessage( ) ) ;
				logger.error( "Failed to create definition object for upload: {}", CSAP.buildCsapStack( e ) ) ;

			}

		} ) ;

		return applicationDefinition ;

	}

	public String buildHttpdConfiguration ( ) {

		return httpdIntegration.reload_csap_web_integration( ) ;

	}

	public ObjectNode checkDefinitionForParsingIssues (
														String projectSource ,
														String projectName ) {

		var parsingReport = jacksonMapper.createObjectNode( ) ;
		var parseErrors = parsingReport.putArray( VALIDATION_ERRORS ) ;
		var parseWarnings = parsingReport.putArray( VALIDATION_WARNINGS ) ;

		parsingReport.put( "releasePackage", projectName ) ;

		OutputFileMgr outputManager = null ;

		try {

			outputManager = new OutputFileMgr( getCsapWorkingFolder( ), "application-validation.log" ) ;

			String fileNameForUpdatedPackage = getProject( projectName ).getSourceFileName( ) ;

			// Create a new empty working folder for the uploaded file
			// Working folder is used solely to validate contents, then will be
			// moved to build folder prior to triggering reload
			File workingFolder = new File( getRootModelBuildLocation( ) + CONFIG_SUFFIX_FOR_UPDATE ) ;
			FileUtils.deleteQuietly( workingFolder ) ;

			// createWorkingFolder using existing live files
			FileUtils.copyDirectory( getDefinitionFolder( ), workingFolder ) ;

			outputManager.print( "Created working folder: " + workingFolder.getAbsolutePath( )
					+ "\n initialized from: " + getDefinitionFolder( )
					+ "\n containing: " + Arrays.asList( workingFolder.list( ) ) ) ;

			// First put the uploaded file into working directory
			File fileForUpdatedPackage = new File( workingFolder, fileNameForUpdatedPackage ) ;
			File rootModelFile = new File( workingFolder, getRootProject( ).getSourceFileName( ) ) ;
			FileUtils.writeStringToFile( fileForUpdatedPackage, projectSource.replaceAll( "\r", "\n" ) ) ;
			outputManager.print( "Pushed updated config to : " + fileForUpdatedPackage.getAbsolutePath( ) ) ;

			//
			// RUN the parser in test mode
			//
			StringBuilder parsingResultsBuffer = projectLoader.process( true, rootModelFile ) ;

			logger.debug( "parsing results: \n{}", parsingResultsBuffer.toString( ) ) ;

			if ( ( parsingResultsBuffer != null ) ) {

				if ( parsingResultsBuffer.indexOf( CsapConstants.CONFIG_PARSE_WARN ) != -1 ) {

					updateOutputWithLimitedInfo( CsapConstants.CONFIG_PARSE_WARN, 25, outputManager,
							parsingResultsBuffer, parseWarnings ) ;

				}

				if ( parsingResultsBuffer.indexOf( CsapConstants.CONFIG_PARSE_ERROR ) != -1 ) {

					updateOutputWithLimitedInfo( CsapConstants.CONFIG_PARSE_ERROR, 25, outputManager,
							parsingResultsBuffer, parseErrors ) ;

				}

			}

			if ( ( parsingResultsBuffer != null )
					&& parsingResultsBuffer.indexOf( CsapConstants.CONFIG_PARSE_ERROR ) == -1 ) {

				parsingReport.put( "success", true ) ;

			} else {

				parsingReport.put( "success", false ) ;
				logger.error( "Failed to parse, look for errors in: {}", parsingResultsBuffer ) ;

				if ( ( parsingResultsBuffer != null ) ) {

					outputManager.print( "-" ) ;
					outputManager
							.print( "\n\n============= Found Semantic Errors !! ====================\n"
									+ "Filtered output for :"
									+ CsapConstants.CONFIG_PARSE_ERROR ) ;

				}

			}

		} catch ( JsonParseException jp ) {

			var stackSummary = CSAP.buildCsapStack( jp ) ;
			outputManager.print( "Failed parsing json" ) ;
			outputManager.print( stackSummary ) ;

			parsingReport.put( "success", false ) ;
			// Parsing exceptions come in here
			// logger.error("Failed to parse config", jp);
			var errorItem = parseErrors.addObject( ) ;
			errorItem.put( "type", "json" ) ;
			errorItem.put( "message", jp.getLocalizedMessage( ) ) ;
			errorItem.put( "line", jp
					.getLocation( )
					.getLineNr( ) ) ;
			errorItem.put( "column", jp
					.getLocation( )
					.getColumnNr( ) ) ;
			errorItem.put( "offset", jp
					.getLocation( )
					.getCharOffset( ) ) ;

		} catch ( Exception e ) {

			parsingReport.put( "success", false ) ;

			// Parsing exceptions come in here
			var stackSummary = CSAP.buildCsapStack( e ) ;
			outputManager.print( "Failed processing definition" ) ;
			outputManager.print( stackSummary ) ;
			logger.error( "Failed to parse config: {}", stackSummary ) ;
			var errorItem = parseErrors.addObject( ) ;
			errorItem.put( "type", "semantic" ) ;
			errorItem.put( "message", stackSummary ) ;

			if ( e.getMessage( ).contains( ProjectLoader.UNABLE_TO_ACTIVATE_ENV ) ) {

				errorItem.put( "message", e.getMessage( ) ) ;

			}

			// outputManager.print(Application.CONFIG_PARSE_ERROR
			// + "Failed to do parse config:\n" +
			// Application.getCustomStackTrace( e ));
		} finally {

			if ( outputManager != null ) {

				outputManager.close( ) ;

			}

		}

		return parsingReport ;

	}

	public static final String VALIDATION_WARNINGS = "warnings" ;

	public static final String VALIDATION_ERRORS = "errors" ;

	public void updateOutputWithLimitedInfo (
												String filterString ,
												int maxLines ,
												OutputFileMgr outputManager ,
												StringBuilder outputContents ,
												ArrayNode alertItemsJson )
		throws IOException {

		int found = outputContents.indexOf( filterString ) ;

		int lineCount = 0 ;
		String lastMatch = "" ;

		while ( found != -1 ) {

			if ( ( outputContents.indexOf( "\n", found ) != -1 ) && ( lineCount < maxLines ) ) {

				String newMatch = outputContents.substring( found,
						outputContents.indexOf( "\n", found ) ) ;

				logger.debug( "newMatch: {}", newMatch ) ;

				if ( ! newMatch.equals( lastMatch ) ) {

					lineCount++ ;
					outputManager.print( newMatch ) ;

					if ( alertItemsJson != null ) {

						alertItemsJson.add( newMatch ) ;

					}

					lastMatch = newMatch ;

				}

			}

			found = outputContents.indexOf( filterString, found + 5 ) ;

		}

		if ( lineCount > maxLines ) {

			outputManager.print( "Note: only first " + maxLines + " of " + lineCount
					+ " shown. View output above for others" + "\n" ) ;

		}

	}

	public ObjectNode getServiceCollection (
												String[] hostNames ,
												String service_port ) {

		if ( ! service_port.contains( "_" ) ) {

			// lookup using instance
			var firstInstance = findFirstServiceInstanceInLifecycle( service_port ) ;
			service_port = firstInstance.getServiceName_Port( ) ;

		}

		ObjectNode serviceDataAllHosts = jacksonMapper.createObjectNode( ) ;
		ArrayNode dataArray = serviceDataAllHosts.putArray( "data" ) ;

		try {

			for ( String hostName : hostNames ) {

				ObjectNode serviceHostData = serviceDataAllHosts.putObject( hostName ) ;

				ObjectNode hostCollectedNode = null ;

				if ( ! isAdminProfile( ) ) {

					hostCollectedNode = (ObjectNode) csapApis.osManager( ).getHostRuntime( ) ;

				} else {

					hostCollectedNode = getHostStatusManager( ).getResponseFromHost( hostName ) ;

					if ( hostCollectedNode == null ) {

						serviceHostData.put( "error", "No response found for host" ) ;

					}

				}

				if ( hostCollectedNode != null ) {

					ObjectNode servicesNode = (ObjectNode) hostCollectedNode.path( "services" ) ;

					if ( servicesNode == null ) {

						serviceDataAllHosts.put( "error", "No response found for host" ) ;
						return serviceDataAllHosts ;

					}

					if ( ! servicesNode.has( service_port ) ) {

						serviceDataAllHosts.put( "error", "No response found for service_port" ) ;
						return serviceDataAllHosts ;

					}

					ObjectNode serviceHost = (ObjectNode) servicesNode.get( service_port ) ;
					serviceHost.put( "hostName", hostName ) ;
					dataArray.add( serviceHost ) ;

				}

			}

			// return (ObjectNode) servicesNode.get( service_port );
		} catch ( Exception e ) {

			serviceDataAllHosts.put( "error", "No response found for host" ) ;

		}

		return serviceDataAllHosts ;

	}

	/**
	 * invoke from JSP
	 *
	 * @param service
	 * @return
	 */
	private void scanFileSystemForVersion (
											ServiceInstance service ) {

		// logger.info( CsapApplication.header( "{}" ), instance.toSummaryString() ) ;

		for ( ContainerState container : service.getContainerStatusList( ) ) {

			StringBuilder version = new StringBuilder( ) ;

			if ( service.is_os_process_monitor( ) ) {

				version.append( "monitor" ) ;

			} else if ( service.is_docker_server( ) ) {

				scanDockerContainerVersion( service, container, version ) ;

			} else if ( service.is_csap_api_server( ) || service.is_springboot_server( ) ) {

				// File versionFile = new File( getCsapWorkingFolder(),
				// instance.getServiceName_Port() + "/version" ) ;
				File versionFile = new File( service.getWorkingDirectory( ), "version" ) ;

				if ( ! versionFile.exists( ) ) {

					// fallback to propertyFolder
					versionFile = new File( service.getPropDirectory( ) + "/version" ) ;

				}

				logger.debug( "Scanning processing folder for version info: {} ", versionFile.getAbsolutePath( ) ) ;

				if ( versionFile.exists( ) ) {

					File[] filesInFolder = versionFile.listFiles( ) ;

					if ( filesInFolder.length == 1 ) {

						version.append( filesInFolder[0].getName( ) ) ;

					} else {

						version.append( "*Too Many Versions*" ) ;

					}

				}

				if ( version.toString( ).equals( "none" ) || ! versionFile.exists( ) ) {

					String targetArtifact = service.getMavenId( ) ;
					version.setLength( 0 ) ;

					try {

						Optional<String> match = buildManifest.keySet( ).stream( )
								.filter( key -> targetArtifact.contains( key ) )
								.findFirst( ) ;

						if ( match.isPresent( ) ) {

							version.append( buildManifest.get( match.get( ) ) ) ;

						} else {

							version.append( "*Not Deployed" ) ;

						}

					} catch ( Exception e ) {

						logger.warn( "Failed processing manifest {}", CSAP.buildCsapStack( e ) ) ;

					}

				}

			} else {

				File targetFile = new File( getCsapWorkingFolder( ), service.getName( ) + "_"
						+ service.getPort( ) + "/webapps" ) ;

				logger.debug( "Scanning processing folder for version info: {}", targetFile.getAbsolutePath( ) ) ;

				if ( targetFile.exists( ) ) {

					File[] filesInFolder = targetFile.listFiles( ) ;

					for ( File itemInFolder : filesInFolder ) {

						if ( itemInFolder.isDirectory( ) ) {

							if ( itemInFolder
									.getName( )
									.contains( "##" ) ) {

								if ( version.length( ) > 0 ) {

									version.append( "," ) ;

								}

								version.append( itemInFolder.getName( ) ) ;

							}

						}

					}

				} else {

					version.append( "*Not Deployed" ) ;

				}

			}

			container.setDeployedArtifacts( version.toString( ) ) ;

		}

		return ;

	}

	private void scanDockerContainerVersion (
												ServiceInstance instance ,
												ContainerState container ,
												StringBuilder version ) {

		String dockerVersion = C7.definitionSettings.val( ) ;

		if ( instance.getDockerImageName( ).contains( ":" ) ) {

			String[] imageName = instance.getDockerImageName( ).split( ":" ) ;

			if ( imageName.length >= 1 ) {

				dockerVersion = imageName[1] ;

			} else {

				dockerVersion = "none" ;

			}

		}

		logger.debug( "docker active: {}, {} version command: {}",
				csapApis.isContainerProviderInstalledAndActive( ),
				instance.getName( ),
				instance.getDockerVersionCommand( ) ) ;

		if ( csapApis.isContainerProviderInstalledAndActive( ) &&
				instance.getDockerVersionCommand( ) != null ) {

			if ( container.getContainerName( ).equals( "default" ) ) {

				logger.debug( "{} container name is default, delaying version discovery: {}",
						instance.getName( ), instance.getDockerVersionCommand( ) ) ;

			} else {

				String versionOutput = csapApis.containerIntegration( ).containerCommand(
						container.getContainerName( ),
						"bash", "-c",
						instance.getDockerVersionCommand( ) ) ;

				if ( StringUtils.isNotEmpty( versionOutput )
						&& ! versionOutput.equals( ContainerIntegration.CRIO_COMMAND_NOT_IMPLMENTED ) ) {

					String customVersion = versionOutput.trim( ) ;

					if ( customVersion.endsWith( ".jar" ) ) {

						customVersion = customVersion.substring( 0, customVersion.length( ) - 4 ) ;

					}

					logger.debug( "customVersion: '{}'", customVersion ) ;

					if ( customVersion.length( ) > 8 ) {

						customVersion = customVersion.substring( 0, 8 ) + "..." ;

					}

					dockerVersion += "_" + customVersion ;

				}

			}

		}

		version.append( dockerVersion ) ;

	}

	private boolean isAutoReload ( ) {

		return autoReload ;

	}

	public void setAutoReload (
								boolean autoReload ) {

		this.autoReload = autoReload ;

	}

	@Inject
	CsapInformation csapInfo ;

	public ObjectNode buildSummaryReport ( boolean isIncludeHealth ) {

		ObjectNode summaryJson = jacksonMapper.createObjectNode( ) ;
		summaryJson.put( "name", getName( ) ) ;
		summaryJson.put( "lifecycle", getCsapHostEnvironmentName( ) ) ;

		if ( csapInfo != null ) {

			summaryJson.put( "version", csapInfo.getVersion( ) ) ;

		} else {

			summaryJson.put( "version", "not-loaded" ) ;

		}

		summaryJson.put( "projectUrl", rootProjectEnvSettings( ).getLoadbalancerUrl( ) ) ;

		if ( isIncludeHealth ) {

			try {

				ObjectNode healthJson = summaryJson.putObject( "health" ) ;
				ObjectNode errorNode = healthManager.build_health_report( 1.0, false, null ) ;

				if ( errorNode.size( ) == 0 ) {

					healthJson.put( "Healthy", true ) ;

				} else {

					healthJson.put( "Healthy", false ) ;
					healthJson.set( "issues", errorNode ) ;

				}

			} catch ( Exception e ) {

				logger.warn( "Failed to get health", e ) ;

			}

		}

		// if ( Application.isAdminProfile() ) {
		// ObjectNode packageSummary =
		// summaryJson.putObject("packageSummary");
		ArrayNode arraySummary = summaryJson.putArray( "packages" ) ;

		getRootProject( )
				.getProjects( )
				.forEach( model -> {

					ObjectNode packageItem = arraySummary.addObject( ) ;
					packageItem.put( "package", model.getName( ) ) ;

					if ( model.getInstanceTotalCountInCurrentLC( ) > 0 ) {

						// ObjectNode packageItem =
						// packageSummary.putObject(key);
						packageItem.put( "vms",
								model
										.getEnvironmentNameToHostInfo( )
										.get( getCsapHostEnvironmentName( ) )
										.size( ) ) ;
						packageItem.put( "services", model
								.serviceInstancesInCurrentLifeByName( )
								.size( ) ) ;
						packageItem.set( "instances", model.getInstanceCountInCurrentLC( ) ) ;
						packageItem.set( "clusters", getClusters( model ) ) ;
						packageItem.set( "metrics", getMetricsConfiguriation( model ) ) ;

					}

				} ) ;

		ObjectNode packageItem = arraySummary.addObject( ) ;
		packageItem.put( "package", "all" ) ;
		// ObjectNode packageItem = packageSummary.putObject("all");
		packageItem.put( "vms", getProject( Application.ALL_PACKAGES )
				.getEnvironmentNameToHostInfo( )
				.get( getCsapHostEnvironmentName( ) )
				.size( ) ) ;
		packageItem.put( "services", getProject( Application.ALL_PACKAGES )
				.serviceInstancesInCurrentLifeByName( )
				.size( ) ) ;
		packageItem.put( "instances", getProject( Application.ALL_PACKAGES )
				.getInstanceTotalCountInCurrentLC( ) ) ;

		summaryJson.set( "serviceAttributes", servicePerformanceLabels( ) ) ;
		// } else {
		// summaryJson
		// .put( "error",
		// "Application Summary is only available from Deployment Manager service:
		// admin." );
		// }

		return summaryJson ;

	}

	private ObjectNode getMetricsConfiguriation (
													Project releasePackage ) {

		ObjectNode configJson = jacksonMapper.createObjectNode( ) ;

		// String type = reqType;
		// if (reqType.startsWith("jmx"))
		// type = "jmx";
		for ( String metricType : rootProjectEnvSettings( )
				.getMetricToSecondsMap( )
				.keySet( ) ) {

			ArrayNode samplesArray = configJson.putArray( metricType ) ;

			for ( Integer sampleInterval : rootProjectEnvSettings( )
					.getMetricToSecondsMap( )
					.get( metricType ) ) {

				samplesArray.add( sampleInterval ) ;

			}

		}

		return configJson ;

	}

	public ObjectNode getClusters (
									Project model ) {

		ObjectNode clusterJson = jacksonMapper.createObjectNode( ) ;

		String lifecycle = getCsapHostEnvironmentName( ) ;

		logger.debug( "csapApp.getActiveModel().getLifeCycleToGroupMap(): {} ", getActiveProject( )
				.getLifecycleToClusterMap( ) ) ;

		for ( String clusterName : model.getLifecycleToClusterMap( ).get( lifecycle ) ) {

			List<String> hosts = model.getLifeClusterToHostMap( )
					.get( lifecycle + ProjectLoader.ENVIRONMENT_CLUSTER_DELIMITER + clusterName ) ;
			clusterJson.set( clusterName, jacksonMapper.convertValue( hosts, ArrayNode.class ) ) ;

		}

		ArrayNode allArray = clusterJson.putArray( "all" ) ;

		for ( String host : sortedHostsInLifecycle( model ) ) {

			allArray.add( host ) ;

		}

		return clusterJson ;

	}

	private HostStatusManager _hostStatusManager = null ;

	public void setHostStatusManager (
										HostStatusManager hostStatusManager ) {

		this._hostStatusManager = hostStatusManager ;

	}

	public HostStatusManager getHostStatusManager ( ) {

		return _hostStatusManager ;

	}

	/**
	 *
	 * Note run on a background threads - but updates in response to either UI or
	 * platform updates.
	 *
	 */
	synchronized public void resetTimeStampsToBypassReload ( ) {

		// so long as edit definition occurs on master, reload wll not occur
		sumOfDefinitionTimestamps = addDefinitionFilesLastModified( ) ;

	}

	synchronized private void updateApplication ( ) {

		var currentTimeStampTotals = addDefinitionFilesLastModified( ) ;

		if ( ( sumOfDefinitionTimestamps != currentTimeStampTotals )
				&& isAutoReload( ) ) {

			logger.warn( CSAP.buildDescription(
					"(Re)Loading Application definition",
					"currentTimeStampTotals", currentTimeStampTotals,
					"sumOfDefinitionTimestamps", sumOfDefinitionTimestamps ) ) ;

			sumOfDefinitionTimestamps = currentTimeStampTotals ;

			reloadApplicationDefinition( ) ;

			if ( ! isStatefulRestartNeeded( )
					&& ! isAdminProfile( )
					&& ! metricManager( ).isCollectorsStarted( )
					&& ! isJunit( ) ) {

				metricManager( ).startResourceCollectors( ) ;

			}

			// update jobs based on settings
			if ( csapApis.osManager( ) != null && csapApis.osManager( ).getInfraRunner( ) != null ) {

				csapApis.osManager( ).getInfraRunner( ).scheduleInfrastructure( ) ;

			}

		} else {

			logger.debug( "Timestamp not modified on file: {}", applicationDefinition( ).getAbsolutePath( ) ) ;

		}

	}

	// private DateFormat dateFormatter = DateFormat.getDateInstance(
	// DateFormat.FULL );
	DateFormat dateFormatter = new SimpleDateFormat( "HH:mm:ss MMM-dd-yyy" ) ;

	private void reloadApplicationDefinition ( ) {

		if ( isAdminProfile( ) && _hostStatusManager != null ) {

			_hostStatusManager.wipeList( ) ;

		}

		try {

			var definitionParsingResults = projectLoader.process( false, applicationDefinition( ) ) ;
			logger.debug( "Parse results: {} ", definitionParsingResults.toString( ) ) ;

			configureApiSecurity( ) ;

			refreshAgentHttpConnections( ) ;

			if ( isAdminProfile( ) ) {

				updateManagerAgentInstances( ) ;

			}

			applicationLoadedAtMillis = System.currentTimeMillis( ) ;

			csapApis.osManager( ).resetAllCaches( ) ;

			var reloadSummary = latestReloadInfo( ) ;

			csapApis.events( ).publishEvent(
					CsapEvents.CSAP_SYSTEM_CATEGORY + "/model/reload", "Reloaded Cluster",
					reloadSummary, null ) ;

			logger.info( CSAP.buildDescription( "reloadSummary", reloadSummary ) ) ;

		} catch ( Exception e ) {

			logger.error( CSAP.buildDescription( "Failed to load definition",
					"path", applicationDefinition( ).getAbsolutePath( ),
					"error", CSAP.buildCsapStack( e ) ) ) ;

			csapApis.events( ).publishEvent( CsapConstants.AGENT_NAME,
					" parsing: " + applicationDefinition( ).getAbsolutePath( ), "Parse Failure", e ) ;

		}

	}

	private void configureApiSecurity ( ) {

		if ( csapRestFilter == null || ! rootProjectEnvSettings( ).isAgentLocalAuth( ) ) {

			logger.info( "Agent apis using LDAP" ) ;

		} else {

			logger.debug( "Updated Agent credentials" ) ;

			if ( rootProjectEnvSettings( ).getAgentPass( ) == null ) {

				String hash = rootProjectEnvSettings( ).getApplicationName( ) + getCsapHostEnvironmentName( ) ;
				logger.info( "Generating hash: {}", hash ) ;
				rootProjectEnvSettings( ).setAgentPass( encryptor.encrypt( hash ) ) ;

			}

			String localPass = rootProjectEnvSettings( ).getAgentPass( ) ;

			try {

				localPass = encryptor.decrypt( localPass ) ;

			} catch ( Exception e1 ) {

				logger.warn( "agent api credential should be encrypted using csap encrpter" ) ;

			}

			csapRestFilter.setLocalCredentials(
					rootProjectEnvSettings( ).getAgentUser( ),
					localPass ) ;

		}

	}

	@Autowired ( required = false )
	CsapSecurityRestFilter csapRestFilter ;

	CsapRestTemplateFactory restTemplateFactory ;
	private RestTemplate agentRestTemplate ;

	private int hostRefreshIntervalSeconds = 60 ;
	private int agentConnectinReadTimeoutSeconds = 60 ;
	private int previousHostCount = 0 ;
	private int previousHostTimeoutSeconds = 0 ;

	public void refreshAgentHttpConnections ( ) {

		checkGitSslVerificationSettings( rootProjectEnvSettings( ) ) ;

		int latestHostCount = getAllPackages( ).getLifeCycleToHostMap( ).get( getCsapHostEnvironmentName( ) ).size( ) ;

		if ( restTemplateFactory == null ) {

			logger.info( "cert: {} {}", csapCoreService.getSslCertificateUrl( ), csapCoreService
					.getSslCertificatePass( ) ) ;

			this.restTemplateFactory = new CsapRestTemplateFactory(
					csapCoreService.getSslCertificateUrl( ),
					csapCoreService.getSslCertificatePass( ) ) ;

		}

		var latestTimeoutSeconds = rootProjectEnvSettings( ).getAdminToAgentTimeoutSeconds( ) ;

		var needToReloadAgentConnections = latestHostCount != previousHostCount
				|| previousHostTimeoutSeconds != latestTimeoutSeconds ;

		logger.info( CSAP.buildDescription( "Agent Connection Pool",
				"needToReloadAgentConnections", needToReloadAgentConnections,
				"latestHostCount", latestHostCount,
				"previousHostCount", previousHostCount,
				"latestTimeoutSeconds", latestTimeoutSeconds,
				"previousHostTimeoutSeconds", previousHostTimeoutSeconds ) ) ;

		if ( needToReloadAgentConnections ) {

			// if number of hosts increase - then increase the pool
			restTemplateFactory.closeAllAndReset( ) ;
			previousHostCount = latestHostCount ;
			previousHostTimeoutSeconds = latestTimeoutSeconds ;

		} else {

			return ;

		}

		setAgentConnectinReadTimeoutSeconds( rootProjectEnvSettings( ).getAdminToAgentTimeoutSeconds( ) ) ;

		if ( ! isAdminProfile( ) ) {

			// increasing timeout for script operations
			setAgentConnectinReadTimeoutSeconds( 65 ) ;

		}

		if ( getCsapCoreService( ) != null ) {

			agentRestTemplate = restTemplateFactory.buildDefaultTemplate(
					"csap-agent-http-connections",
					getCsapCoreService( ).isDisableSslValidation( ),
					5, latestHostCount * 2 + 10,
					latestTimeoutSeconds,
					getAgentConnectinReadTimeoutSeconds( ),
					getHostRefreshIntervalSeconds( ) + 30 ) ;

		} else {

			logger.warn( "Skipping template Factory because core services not injected (junit) " ) ;

		}

	}

	public RestTemplate getAgentPooledConnection (
													long fileSize ,
													int timeoutSeconds ) {

		/**
		 * HttpClient will run OOM if filesize is large. Use the
		 *
		 */

		if ( agentRestTemplate == null ) {

			logger.error( "Pool not initialized - verify setup" ) ;

		}

		if ( agentRestTemplate == null
				|| ( fileSize > CHUNKING_SIZE )
				|| ( timeoutSeconds > getAgentConnectinReadTimeoutSeconds( ) ) ) {

			// Pooled connections are NOT used when:
			// Transfer of large objects (100's of mb for large packages)
			// OOM exception will occur
			// Transfer of scripts - possibly long timeouts

			// logger.info( "csapCoreService: {}", csapCoreService );
			var nonPooledFactory = getCsapCoreService( ).csapRestFactory( ).buildNonPooledFactory(
					"csap-agent-not-pooled",
					rootProjectEnvSettings( ).getAdminToAgentTimeoutSeconds( ),
					timeoutSeconds ) ;

			if ( getCsapCoreService( ).isDisableSslValidation( ) ) {

				nonPooledFactory = getCsapCoreService( )
						.csapRestFactory( )
						.buildFactoryDisabledSslChecks(
								"csap-agent-not-pooled-ssl-disabled",
								rootProjectEnvSettings( ).getAdminToAgentTimeoutSeconds( ),
								timeoutSeconds ) ;

			}

			nonPooledFactory.setBufferRequestBody( false ) ;
			return new RestTemplate( nonPooledFactory ) ;

//			SimpleClientHttpRequestFactory simpleFactory = new SimpleClientHttpRequestFactory( ) ;
//			simpleFactory.setReadTimeout( timeoutSeconds * 1000 ) ;
//			simpleFactory.setChunkSize( CHUNKING_SIZE ) ; //
//			simpleFactory.setConnectTimeout( rootProjectEnvSettings( ).getAdminToAgentTimeoutMs( ) ) ;
//			simpleFactory.setBufferRequestBody( false ) ;

//			return new RestTemplate( simpleFactory ) ;

		}

		// return pooled/active connection
		return agentRestTemplate ;

	}

	public List<String> checkGitSslVerificationSettings (
															EnvironmentSettings lifeCycleSettings ) {

		File gitconfig = new File( getAgentRunHome( ), ".gitconfig" ) ;
		String gitConfigContents = readFile( gitconfig ) ;

		// per domain does not seem to be working

		logger.debug( "{} contents: \n{}",
				gitconfig.getAbsolutePath( ),
				gitConfigContents ) ;

		// jgit has issue - ssl can only be disabled globally

		List<String> updatedSection = lifeCycleSettings
				.getGitSslVerificationDisabledUrls( ).stream( )
				.filter( url -> ! gitConfigContents.contains( url ) )
				.map( url -> {

					return "\n[http \"" + url + "\"]\n\tsslVerify = false" ;

				} )
				.collect( Collectors.toList( ) ) ;

		if ( updatedSection.size( ) > 0 ) {

			// bug in jgit requires global disabled as well
			updatedSection.add( "\n[http]\n\tsslVerify = false" ) ;

			logger.warn( "Updating .gitconfig: {} with: \n {}", gitconfig.getAbsolutePath( ), updatedSection ) ;

			try {

				if ( ! gitconfig.getAbsolutePath( ).contains( "willBeReplacedOnSpringInit" ) ) {

					Files.write( gitconfig.toPath( ),
							updatedSection,
							StandardCharsets.UTF_8,
							StandardOpenOption.APPEND,
							StandardOpenOption.CREATE ) ;

				} else {

					logger.info( "Skipping update {}", gitconfig.getAbsolutePath( ) ) ;

				}

			} catch ( Exception e ) {

				logger.error( "Failed updating gitconfig: {}",
						gitconfig.getAbsolutePath( ),
						CSAP.buildCsapStack( e ) ) ;

				e.printStackTrace( ) ;

			}

		} else {

			logger.info( "Did not find any updates" ) ;

		}

		return updatedSection ;

	}

	public boolean isStatefulRestartNeeded ( ) {

		return System.getProperty( "org.csap.needStatefulRestart" ) != null ;

	}

	private void updateManagerAgentInstances ( ) {

		StringBuffer sbuf = new StringBuffer( "Service loaded in lifecycle "
				+ getCsapHostEnvironmentName( ) + ":" ) ;

		if ( _hostStatusManager != null ) {

			_hostStatusManager.shutdown( ) ;
			_hostStatusManager = null ;

		}

		getAllPackages( ).getHostsInActiveLifecycleStream( )
				.forEach( host -> {

					sbuf.append( "\n" + host + " : " ) ;
					var hostInstances = getAllPackages( )
							.getServicesListOnHost( host ) ;

					for ( ServiceInstance instance : hostInstances ) {

						sbuf.append( instance.getName( ) + "(" + instance.getPort( ) + ") " ) ;

					}

				} ) ;

//		for ( var host : getAllPackages( )
//				.getLifeCycleToHostMap( )
//				.get(
//						getCsapHostEnvironmentName( ) ) ) {
//
//
//			sbuf.append( "\n" + host + " : " ) ;
//
//			var hostInstances = getAllPackages( )
//					.getHostToConfigMap( )
//					.get(  host ) ;
//
//
//			for ( ServiceInstance instance : hostInstances ) {
//				sbuf.append( instance.getName( ) + "(" + instance.getPort( ) + ") " ) ;
//			}
//
//		}

		logger.debug( "Services loaded: {}", sbuf.toString( ) ) ;

		var allHostsInAllPackages = new ArrayList<String>( ) ;
		allHostsInAllPackages.addAll( getAllPackages( )
				.getLifeCycleToHostMap( )
				.get(
						getCsapHostEnvironmentName( ) ) ) ;

		_hostStatusManager = new HostStatusManager(
				csapApis,
				rootProjectEnvSettings( ).getNumberWorkerThreads( ),
				allHostsInAllPackages ) ;

	}

	public JsonNode latestReloadInfo ( ) {

		String lastLoad = dateFormatter.format( new Date( getApplicationLoadedAtMillis( ) ) ) ;

		ObjectNode data = jacksonMapper.createObjectNode( ) ;
		data.put( "Path: ", applicationDefinition( ).getAbsolutePath( ) ) ;
		data.put( "Services Parsed", serviceNameToAllInstances( )
				.keySet( )
				.size( ) ) ;
		data.put( "Hosts Parsed", getActiveProject( )
				.getHostsInAllLifecycles( )
				.size( ) ) ;
		data.put( "millis", getApplicationLoadedAtMillis( ) ) ;
		data.put( "formated", lastLoad ) ;
		data.put( "sumOfDefinitionTimestamps", sumOfDefinitionTimestamps ) ;
		return data ;

	}

	private long addDefinitionFilesLastModified ( ) {

		long configFileLastModTime = 0 ;

		if ( applicationDefinition( ) != null
				&& applicationDefinition( ).exists( )
				&& applicationDefinition( ).canRead( ) ) {

			long totalTime = 0 ;

			logger.debug( "Loading definition files in: {}", getDefinitionFolder( )
					.getAbsolutePath( ) ) ;

			Iterator<File> jsFileIterator = FileUtils.iterateFiles( getDefinitionFolder( ),
					new String[] {
							"js", "json"
					}, false ) ;
			StringBuilder builder = null ;

			if ( logger.isDebugEnabled( ) ) {

				builder = new StringBuilder( ) ;

			}

			while ( jsFileIterator.hasNext( ) ) {

				File jsFile = jsFileIterator.next( ) ;

				if ( logger.isDebugEnabled( ) ) {

					builder.append( "\n\t" + jsFile.getAbsolutePath( ) ) ;

				}

				totalTime += jsFile.lastModified( ) ;

			}

			logger.debug( "Checking filestamps for file(s):  {} ", builder ) ;
			configFileLastModTime = totalTime ;

		} else {

			logger.error( "Cannot access definition: '{}'", applicationDefinition( ) ) ;

		}

		return configFileLastModTime ;

	}

	public void markServicesForFileSystemScan (
												boolean kubernetesOnly ) {

		getActiveProject( )
				.getServicesOnHost( getCsapHostName( ) )
				.filter( serviceInstance -> {

					if ( kubernetesOnly )
						return serviceInstance.is_cluster_kubernetes( ) ;
					return true ;

				} )
				.forEach( service -> {

					service.setFileSystemScanRequired( true ) ;

				} ) ;

	}

	private Map<String, String> buildManifest = null ;

	private void scanServiceFileSystems ( ) {

		logger.debug( "starting scan" ) ;

		// only on agents

		if ( buildManifest == null ) {

			try {

				var deployFolder = new File( getCsapInstallFolder( ), CSAP_SERVICE_PACKAGES + "installer-conf.json" ) ;
				String manifest = readFile( deployFolder ) ;
				buildManifest = jacksonMapper.readValue(
						check_for_stub( manifest, "agent/installer-conf.json" ),
						new TypeReference<HashMap<String, String>>( ) {
						} ) ;

				logger.info( CSAP.buildDescription( "Build Manifest", buildManifest ) ) ;

			} catch ( Exception e ) {

				logger.warn( "Unable to load build manifest: ", CSAP.buildCsapStack( e ) ) ;

			}

		}

//		osManager.checkForProcessStatusUpdate( ) ;

		var timer = csapApis.metrics( ).startTimer( ) ;

		getActiveProject( )
				.getServicesOnHost( getCsapHostName( ) )
				.filter( ServiceInstance::isFileSystemScanRequired )
				.forEach( this::scanServiceFolderForArtifacts ) ;

		csapApis.metrics( ).stopTimer( timer, "csap.service.scan" ) ;

	}

	private void scanServiceFolderForArtifacts (
													ServiceInstance instanceConfig ) {

		var timer = csapApis.metrics( ).startTimer( ) ;

		if ( instanceConfig.is_cluster_kubernetes( ) ) {
			// k8s version info is store on master: service is inactive, but info is
			// available.

		} else if ( instanceConfig.is_docker_server( ) &&
				( ( ! csapApis.isContainerProviderInstalledAndActive( ) )
						|| ! instanceConfig.getDefaultContainer( ).isRunning( ) ) ) {

			logger.debug( "{} Delaying docker scan until status is started", instanceConfig.getName( ) ) ;

			return ;

		}

		scanFileSystemForVersion( instanceConfig ) ;
		scanFileSystemForArtifactDate( instanceConfig ) ;
		scanFileSystemForEolJars( instanceConfig ) ;

		instanceConfig.setFileSystemScanRequired( false ) ;

		csapApis.metrics( ).stopTimer( timer, PERFORMANCE_ID + "service.scan." + instanceConfig.getName( ) ) ;

	}

	private void scanFileSystemForEolJars (
											ServiceInstance serviceInstance ) {

		if ( ! serviceInstance.is_springboot_server( ) ) {

			return ;

		}

		var eolSet = new TreeSet<String>( ) ;
		rootProjectEnvSettings( )
				.getEolJarPatterns( )
				.forEach( eolPattern -> {

					String jarPattern = eolPattern.asText( ) ;
					File libDir = getLibraryFolder( serviceInstance ) ;
					logger.debug( "{} looking for {} in {}",
							serviceInstance.getName( ), jarPattern, libDir.getAbsolutePath( ) ) ;

					if ( libDir.exists( ) && libDir.isDirectory( ) ) {

						String[] matches = libDir.list( (
															File dir ,
															String name ) -> {

							return name.matches( jarPattern ) ;

						} ) ;
						logger.debug( "{} looking for {} in {} found: {}",
								serviceInstance.getName( ),
								jarPattern,
								libDir.getAbsolutePath( ),
								Arrays.asList( matches ) ) ;

						if ( matches.length > 0 ) {

							eolSet.add( jarPattern ) ;

						}

					}

				} ) ;

		// Keep unique string for multiple matches
		ArrayNode eolJars = jacksonMapper.createArrayNode( ) ;
		eolSet.forEach( eolJars::add ) ;

		serviceInstance.setEolJars( eolJars ) ;

	}

	public File getAgentWebDir ( ) {

		File libDir = getLibraryFolder( getServiceInstanceCurrentHost( CsapConstants.AGENT_ID ) ) ;

		File agentWebDir = new File( libDir.getParentFile( ), "classes/static/htmlViewer" ) ;

		return agentWebDir ;

	}

	private File getLibraryFolder (
									ServiceInstance serviceInstance ) {

		// default to be tomcat based as context is calculated.
		String svcId = serviceInstance.getName( ) ;
		File libFolder = new File( getCsapWorkingSubFolder( svcId ), serviceInstance.getLibDirectory( ) ) ;

		if ( serviceInstance.getLibDirectory( ).length( ) > 0 && serviceInstance.getLibDirectory( ).startsWith(
				"/" ) ) {

			libFolder = new File( serviceInstance.getLibDirectory( ) ) ;

		}

		if ( serviceInstance.isTomcatPackaging( ) && serviceInstance.getLibDirectory( ).length( ) == 0 ) {

			// tomcat instance have a context folder
			libFolder = new File( getPropertyFolder( svcId ).getParentFile( ), "/lib" ) ;

		} else if ( serviceInstance.is_springboot_server( ) ) {

			if ( ! libFolder.exists( ) && serviceInstance.is_springboot_server( ) ) {

				// legacy boot lib folder
				libFolder = new File( getCsapWorkingSubFolder( svcId ),
						ServiceBase.getBootFolder( true ) + "lib" ) ;

			}

		}

		return libFolder ;

	}

	private void scanFileSystemForArtifactDate (
													ServiceInstance serviceInstance ) {

		serviceInstance.setArtifactDate( "Today" ) ;

		//
		File deployFile = getServiceDeployFile( serviceInstance ) ;
		File versionInfoFile = getDeployVersionFile( serviceInstance ) ;

		if ( ! deployFile.exists( ) ) {

			deployFile = versionInfoFile ;

		}

		logger.debug( "Checking deployment file: {}", deployFile.getAbsolutePath( ) ) ;

		if ( deployFile.exists( ) ) {

			SimpleDateFormat formatter = new SimpleDateFormat( "MMM.d H:mm" ) ;
			Date date = new Date( deployFile.lastModified( ) ) ;
			serviceInstance.setArtifactDate( formatter.format( date ) ) ;

		}

		logger.debug( "Checking versionFile: {}", versionInfoFile.getAbsolutePath( ) ) ;

		if ( versionInfoFile.canRead( ) ) {

			String info = Application.readFile( versionInfoFile ) ;

			if ( info.contains( "version" ) ) {

				info = info.substring( info.indexOf( "version" ) - 1 ) ;

			}

			serviceInstance.setScmVersion( info.replaceAll( "<[^>]*>", " " ) ) ;

			// logger.info("Found Version File" ) ;
		} else if ( ! serviceInstance.is_os_process_monitor( )
				&& ! serviceInstance.is_csap_api_server( ) ) {
			// os versions are loaded from JSON files
			// serviceInstance.setScmVersionNotDeployed() ;

			try {

				Optional<String> match = buildManifest.keySet( ).stream( )
						.filter( key -> serviceInstance.getMavenId( ).contains( key ) )
						.findFirst( ) ;

				if ( match.isPresent( ) ) {

					serviceInstance.setScmVersion( "CSAP Release" ) ;

				} else {

					serviceInstance.setScmVersionNotDeployed( ) ;

				}

			} catch ( Exception e ) {

				logger.warn( "Failed processing manifest {}", CSAP.buildCsapStack( e ) ) ;

			}

		}

	}

	public File getDefinitionFolder ( ) {

		return csapDefinitionFolder ;

	}

	/**
	 * TOKEN is used so that path on other VMs is relative to local CSAP install
	 * folder
	 *
	 * @return
	 */
	public String getDefinitionToken ( ) {

		return FileToken.PLATFORM.value + "/" + CsapApplication.DEFINITION_FOLDER_NAME ;

	}

	/**
	 *
	 * Utility function to enable defintion unit testing to occur without impacting
	 * other tests
	 *
	 * @param definitionFile
	 */
	public void configureForDefinitionOperationsTest (
														File definitionFile ) {

		File globalBuildLoc = new File( getRootModelBuildLocation( ) ) ;

		try {

			FileUtils.deleteQuietly( globalBuildLoc ) ;

			// We are going to work using a test folder to avoid impacting other
			// tests.
			// CLUSTER_DIR += "_TEST";
			csapDefinitionFolder = new File( JUNIT_CLUSTER_PREFIX + System.currentTimeMillis( ) ) ;

			logger.warn( "Test setup using: {} \n Deleting: {} \n Deleting: {} \n Copying: {} to {}",
					definitionFile.getAbsolutePath( ),
					globalBuildLoc.getAbsolutePath( ),
					getDefinitionFolder( ).getAbsolutePath( ),
					definitionFile.getParentFile( ).getAbsolutePath( ),
					getDefinitionFolder( ).getAbsolutePath( ) ) ;

			FileUtils.deleteQuietly( getDefinitionFolder( ) ) ;

			FileUtils.copyDirectory( definitionFile.getParentFile( ), getDefinitionFolder( ),
					FileFilterUtils.suffixFileFilter( ".json" ) ) ;

		} catch ( IOException e ) {

			logger.error( "Failed test setup", e ) ;

		}

	}

	public File configureForDefinitionOverideTest (
													File definitionFile ) {

		File globalBuildLoc = new File( getRootModelBuildLocation( ) ) ;

		try {

			FileUtils.deleteQuietly( globalBuildLoc ) ;

			// We are going to work using a test folder to avoid impacting other
			// tests.
			// CLUSTER_DIR += "_TEST";
			csapDefinitionFolder = new File( JUNIT_CLUSTER_PREFIX + System.currentTimeMillis( ) ) ;

			logger.warn( "Test setup using: {} \n Deleting: {} \n Deleting: {} \n Copying: {} to {}",
					definitionFile.getAbsolutePath( ),
					globalBuildLoc.getAbsolutePath( ),
					getDefinitionFolder( ).getAbsolutePath( ),
					definitionFile.getParentFile( ).getAbsolutePath( ),
					getDefinitionFolder( ).getAbsolutePath( ) ) ;

			FileUtils.deleteQuietly( getDefinitionFolder( ) ) ;

			FileUtils.copyDirectory( definitionFile.getParentFile( ), getDefinitionFolder( ) ) ;

		} catch ( IOException e ) {

			logger.error( "Failed test setup", e ) ;

		}

		return getDefinitionFolder( ) ;

	}

	public boolean isAdminProfile ( ) {

		return adminMode ;

	}

	public boolean isAgentProfile ( ) {

		return ! isAdminProfile( ) ;

	}

	public static boolean isRunningOnDesktop ( ) {

		return isDeveloperMode( ) ;

	}

	public boolean isDesktopHost ( ) {

		if ( isRunningOnDesktop( ) ) {

			return true ;

		}

		return false ;

	}

	public boolean isRunningAsRoot ( ) {

		if ( System.getenv( "CSAP_NO_ROOT" ) == null ) {

			return true ;

		}

		return false ;

	}

	public void setJvmInManagerMode ( boolean adminMode ) {

		logger.info( "adminMode '{}'", adminMode ) ;
		this.adminMode = adminMode ;

	}

	private String agentStatus = "bootStrapInProgress" ;

	private boolean bootstrapComplete = false ;

	private String csapHostEnvironmentName = "" ;

	public String getCsapHostEnvironmentName ( ) {

		return csapHostEnvironmentName ;

	}

	private ObjectMapper jacksonMapper = new ObjectMapper( ) ;

	//
	// Determines when to reload application definition
	//
	private long sumOfDefinitionTimestamps = -1 ;
	volatile private long applicationLoadedAtMillis = 0 ;

	public String lastOp = "0::csap restarted" ;

	private long lastOpMillis = System.currentTimeMillis( ) ;

	private long lastKubernetesFileSystemScanMillis = 0 ;

	private SimpleDateFormat df = new SimpleDateFormat( "E MMM d,  HH:mm" ) ;

	private String motdMessage = "Last restart: "
			+ df.format( new Date( ) ) ;

	OsCommandRunner osCommandRunner = new OsCommandRunner( 90, 3, "CapMgr" ) ; // apachectl

	ScheduledFuture<?> applicationScheduler = null ;

	BasicThreadFactory schedFactory = new BasicThreadFactory.Builder( )
			.namingPattern( "CsapApplicationThreadPool-%d" )
			.daemon( true )
			.priority( Thread.NORM_PRIORITY )
			.build( ) ;
	ScheduledExecutorService csapApplicationThreadPool = Executors.newScheduledThreadPool( 1, schedFactory ) ;

	@Value ( "${user.dir}" )
	private String processWorkingDirectory = ".." ;

	// defaults to above

	public TreeMap<String, HashSet<String>> serviceToArtifactMap ;

	// init for tests from
	// main
	public String getAgentStatus ( ) {

		return agentStatus ;

	}

	/**
	 * Lifecycle Name is embedded to prevent cross-lifecycle calls
	 *
	 * @return
	 */
	public String getTomcatAjpKey ( ) {

		return getCsapHostEnvironmentName( )
				+ rootProjectEnvSettings( )
						.getTomcatAjpSecret( ) ;

	}

	public String getName ( ) {

		return rootProjectEnvSettings( ).getApplicationName( ) ;

		// if (getGlobalModel().getReleaseModelCount() == 1)
		// return getGlobalModel().getName();
		// return getGlobalModel().getName() + " : "
		// + getActiveModel().getReleasePackageName();
	}

	public ArrayList<ServiceInstance> getLifeCycleServicesByMatch (
																	String jvmName ) {

		var allModel = getRootProject( ).getAllPackagesModel( ) ;

		var filterInstances = new ArrayList<ServiceInstance>( ) ;

		for ( String svc : allModel
				.getServiceToAllInstancesMap( )
				.keySet( ) ) {

			if ( ! svc.matches( jvmName ) ) {

				continue ;

			}

			if ( allModel
					.getServiceToAllInstancesMap( )
					.get( svc ) != null ) {

				for ( ServiceInstance instance : allModel
						.getServiceToAllInstancesMap( )
						.get( svc ) ) {

					if ( instance
							.getLifecycle( )
							.startsWith(
									getCsapHostEnvironmentName( ) ) ) {

						filterInstances.add( instance ) ;

					}

				}

			}

		}

		return filterInstances ;

	}

	// this can trivially be set to multiple locations...defer for now and always
	// use root project settings
	public String getRootProjectDefinitionUrl ( ) {

		return rootProjectEnvSettings( )
				.getDefinitionRepoUrl( ) ;

	}

	public String getSourceType ( ) {

		return rootProjectEnvSettings( )
				.getDefinitionRepoType( ) ;

	}

	public String getSourceBranch ( ) {

		return rootProjectEnvSettings( ).getDefinitionRepoBranch( ) ;

	}

	public EnvironmentSettings rootProjectEnvSettings ( ) {

		if ( getRootProject( ) == null || getRootProject( ).getEnvironmentNameToSettings( ) == null ) {

			logger.warn( "Unable to get settings: null model" ) ;
			return null ;

		}

		return getRootProject( )
				.getEnvironmentNameToSettings( )
				.get( getCsapHostEnvironmentName( ) ) ;

	}

	public EnvironmentSettings rootProjectEnvSettings ( String life ) {

		return getRootProject( )
				.getEnvironmentNameToSettings( )
				.get( life ) ;

	}

	public EnvironmentSettings environmentSettings ( ) {

		return getActiveProject( )
				.getEnvironmentNameToSettings( )
				.get( getCsapHostEnvironmentName( ) ) ;

	}

	public File getDeployVersionFile (
										ServiceInstance serviceInstance ) {

		return new File( getCsapPackageFolder( ), serviceInstance.getDeployFileName( ) + ".txt" ) ;

	}

	public File getServiceDeployFile (
										ServiceInstance serviceInstance ) {

		return new File( getCsapPackageFolder( ), serviceInstance.getDeployFileName( ) ) ;

	}

	private File deployFolder = null ;

	public File getCsapPackageFolder ( ) {

		if ( deployFolder == null ) {

			deployFolder = new File( getCsapInstallFolder( ), CSAP_SERVICE_PACKAGES ) ;
			deployFolder.mkdirs( ) ;

		}

		return deployFolder ;

	}

	public void move_to_csap_saved_folder (
											File folder_to_backup ,
											StringBuilder operation_output )
		throws IOException {

		File csapSavedFolder = getCsapSavedFolder( ) ;

		if ( folder_to_backup.getAbsolutePath( ).equals( getDefinitionFolder( ).getAbsolutePath( ) ) ) {

			csapSavedFolder = new File( csapSavedFolder, "definitionBackups" ) ;

			if ( ! csapSavedFolder.exists( ) ) {

				csapSavedFolder.mkdirs( ) ;

			}

		}

		String now = LocalDateTime.now( ).format( DateTimeFormatter.ofPattern( "MMM.d-HH.mm.ss" ) ) ;

		String backUpFileName = folder_to_backup.getName( ) + "." + now ;

		File backUpFolder = new File( csapSavedFolder, backUpFileName ) ;

		if ( backUpFolder.exists( ) ) {

			logger.info( "Warning: agent jobs are not cleaning up: {}", backUpFolder.getAbsolutePath( ) ) ;
			FileUtils.deleteQuietly( backUpFolder ) ;
			operation_output.append( "\n\n Deleting previous backup: " + backUpFolder.getAbsolutePath( ) + "\n" ) ;

		}

		if ( folder_to_backup.exists( ) ) {

			logger.info( "Moving: {} to {}", folder_to_backup.getAbsolutePath( ), backUpFolder.getAbsolutePath( ) ) ;

			operation_output.append( "\n\n Moving : "
					+ folder_to_backup.getAbsolutePath( )
					+ " to: "
					+ backUpFolder.getAbsolutePath( ) + "\n" ) ;

			FileUtils.moveDirectory( folder_to_backup, backUpFolder ) ;

		} else {

			operation_output.append( "Folder does not exist: " + folder_to_backup.getCanonicalPath( ) ) ;
			logger.warn( "Folder does not exist: {}", folder_to_backup.getCanonicalPath( ) ) ;

		}

	}

	public File getCsapSavedFolder ( ) {

		File csapSavedFolder = csapPlatformPath( SAVED ) ;

		if ( ! csapSavedFolder.exists( ) ) {

			logger.info( "creating csap saved folder: {}", csapSavedFolder.getAbsolutePath( ) ) ;
			csapSavedFolder.mkdirs( ) ;

		}

		return csapSavedFolder ;

	}

	public File getFileOnHost (
								String svcName ,
								String logSelect ,
								String propSelect ) {

		if ( propSelect != null ) {

			return new File( getPropertyFolder( svcName ), propSelect ) ;

		}

		File logPath = new File( getLogDir( svcName ), logSelect ) ;

		return logPath ;

	}

	public ArrayList<String> sortedHostsInLifecycle ( Project model ) {

		return getHostsForLifecycle( getCsapHostEnvironmentName( ), model ) ;

	}

	/**
	 * This returns a mutable list - hence copies are made
	 *
	 * @param lc
	 * @return
	 */
	public List<String> getMutableHostsInActivePackage (
															String lc ) {

		logger.debug( "Getting: {}", lc ) ;

		var hostList = new ArrayList<String>( ) ;

		if ( lc != null && lc.equals( ALL_PACKAGES ) ) {

			hostList = new ArrayList<>( getAllHostsInAllPackagesInCurrentLifecycle( ) ) ;

		}

		if ( lc != null ) {

			logger.debug( "Getting: {} from available: {}", lc, getClustersForActivePackage( ).keySet( ) ) ;
			List<String> testHosts = getClustersForActivePackage( ).get( lc ) ;

			if ( testHosts != null ) {

				hostList = new ArrayList<>( testHosts ) ;

			}

		}

		Collections.sort( hostList ) ;
		//
		// return getHostsForLifecycle(lc, getActiveModel());
		// merge all packages together Map<String, List<String>>
		// return getClustersForActiveLifecycle().get( lc );

		return hostList ;

	}

	public Map<String, List<String>> getClustersForActivePackage ( ) {

		Map<String, List<String>> clusterToHostListJson = getActiveProject( )
				.getClustersToHostMapInCurrentLifecycle( ) ;

		// now create the all list
		List<String> allHosts = clusterToHostListJson
				.values( )
				.stream( )
				.flatMap( Collection::stream )
				.distinct( )
				.collect( Collectors.toList( ) ) ;

		clusterToHostListJson.put( activeLifecycleClusterName( ),
				allHosts ) ;

		return clusterToHostListJson ;

	}

	public String activeLifecycleClusterName ( ) {

		return getCsapHostEnvironmentName( )
				+ "(" + getActiveProject( ).getName( ) + ")" ;

	}

	public ObjectNode buildClusterReportForAllPackagesInActiveLifecycle ( ) {

		var allPackagesMap = getActiveProject( ).getAllPackagesModel( ).getClustersToHostMapInCurrentLifecycle( ) ;

		return jacksonMapper.convertValue( allPackagesMap, ObjectNode.class ) ;

	}

	public ObjectNode buildClusterByPackageInActiveLifecycleReport ( ) {

		ObjectNode clusterReport = jacksonMapper.createObjectNode( ) ;

		getReleasePackageStream( ).forEach( model -> {

			String packageName = model.getName( ) ;

			var packageClusters = jacksonMapper.convertValue(
					model.getClustersToHostMapInCurrentLifecycle( ),
					ObjectNode.class ) ;

			clusterReport.set( packageName, packageClusters ) ;

		} ) ;

		return clusterReport ;

	}

	public ArrayList<String> getHostsForLifecycle (
													String environmentName ,
													Project model ) {

		var hostList = new ArrayList<String>( ) ;

		if ( environmentName != null && getLifeCycleToHostMap( )
				.get( environmentName ) != null ) {

			hostList = new ArrayList<>( model
					.getLifeCycleToHostMap( )
					.get( environmentName ) ) ;

			// Collections.sort( hostList ) ;
		}

		return hostList ;

	}

	public boolean is_service_running (
										String serviceName ) {

		ServiceInstance instance = findServiceByNameOnCurrentHost( serviceName ) ;

		// logger.info( "dockerInstance: {}", dockerInstance.toString() );

		return ( instance != null && instance.getDefaultContainer( ).isRunning( ) ) ;

	}

	public String getLastOp ( ) {

		return lastOp ;

	}

	public String getLastOpMessage ( ) {

		if ( lastOp.contains( "::" ) ) {

			return lastOp.substring( lastOp.indexOf( ":" ) + 2 ) ;

		}

		return lastOp ;

	}

	public long getLastOpMillis ( ) {

		return lastOpMillis ;

	}

	public ArrayList<String> getLifeCycleHosts ( ) {

		return getLifeCycleToHostMap( )
				.get( getCsapHostEnvironmentName( ) ) ;

	}

	public ArrayList<String> getLifecycleList ( ) {

		return getRootProject( )
				.getEnvironmentAndClusterNames( ) ;

	}

	public TreeMap<String, ArrayList<HostInfo>> getLifeCycleToHostInfoMap ( ) {

		return getActiveProject( )
				.getEnvironmentNameToHostInfo( ) ;

	}

	public TreeMap<String, ArrayList<String>> getLifeCycleToHostMap ( ) {

		return getActiveProject( )
				.getLifeCycleToHostMap( ) ;

	}

	// public ArrayList<String> getClustersInLifecycle() {
	//
	// ArrayList<String> clustersInCurrentLifeCycle = new ArrayList<String>();
	// for (String lc : getActiveModel().getLifecycleList()) {
	// // logger.debug("Lifecycle: " + lc);
	// ArrayList<String> hostList =
	// getActiveModel().getLifeCycleToHostMap().get(lc);
	// if (hostList == null) {
	// continue;
	// }
	//
	// if (lc.startsWith(getCurrentLifeCycle())) {
	// clustersInCurrentLifeCycle.add(lc);
	// }
	// }
	// return clustersInCurrentLifeCycle;
	// }
	public List<String> getClustersInLifecycle (
													String releasePackage ) {

		var clustersInCurrentLifeCycle = new ArrayList<String>( ) ;

		for ( String environmentName : getProject( releasePackage ).getEnvironmentAndClusterNames( ) ) {

			// logger.debug("Lifecycle: " + lc);
			var hostNames = getProject( releasePackage ).getHostsForEnvironment( environmentName ) ;

			if ( hostNames == null ) {

				continue ;

			}

			if ( environmentName.startsWith( getCsapHostEnvironmentName( )
					+ ProjectLoader.ENVIRONMENT_CLUSTER_DELIMITER ) ) {

				clustersInCurrentLifeCycle.add( environmentName ) ;

			}

		}

		Collections.sort( clustersInCurrentLifeCycle, String.CASE_INSENSITIVE_ORDER ) ;
		return clustersInCurrentLifeCycle ;

	}

	public String getMotdMessage ( ) {

		return motdMessage ;

	}

	public List<String> getAllHostsInAllPackagesInCurrentLifecycle ( ) {

//		Set<String> result = new TreeSet<String>( getRootProject( )
//				.getAllPackagesModel( )
//				.getHostsForEnvironment( getCsapHostEnvironmentName( ) ) ) ;
//
//		logger.debug( "Other hosts: {}", result.toString( ) ) ;

		return getRootProject( ).getAllPackagesModel( ).getHostsForEnvironment( getCsapHostEnvironmentName( ) ) ;

	}

	private File getPropertyFolder ( String serviceFlexId ) {

		var serviceInstance = flexFindFirstInstanceCurrentHost( serviceFlexId ) ;

		var serviceName = serviceInstance.getName( ) ;

		File procDir = new File( getCsapWorkingSubFolder( serviceName ), "/webapps/" ) ;
		File[] filesInFolder = procDir.listFiles( ) ;

		File propFile = new File( getCsapWorkingSubFolder( serviceName ), "/webapps/"
				+ serviceInstance.getContext( ) + "/" + serviceInstance.getPropDirectory( ) ) ;

		File propFileOver = new File( getCsapWorkingSubFolder( serviceName ), "/"
				+ serviceInstance.getPropDirectory( ) ) ;

		if ( serviceInstance.is_springboot_server( ) && ! propFileOver.exists( ) ) {

			// springboot <= 1.3
			logger.debug( "Did not find: {}", propFileOver.getAbsolutePath( ) ) ;
			propFileOver = new File( getCsapWorkingSubFolder( serviceName ),
					ServiceBase.getBootFolder( true ) ) ;

		}

		if ( propFileOver.exists( ) ) {

			propFile = propFileOver ;

		} else if ( filesInFolder != null ) {

			// handle tomcat parallel deployments - may have multiple contexts
			// so only pick the first one
			for ( File itemInFolder : filesInFolder ) {

				if ( itemInFolder
						.getName( )
						.startsWith( serviceInstance.getContext( ) ) ) {

					propFile = itemInFolder ;
					File test = new File( itemInFolder, serviceInstance.getPropDirectory( ) ) ;

					if ( test.exists( ) ) {

						propFile = test ;

					}

					break ;

				}

			}

		}

		if ( serviceInstance
				.getPropDirectory( )
				.startsWith( "/" ) ) {

			propFile = new File( serviceInstance.getPropDirectory( ) ) ;

		}

		if ( isRunningOnDesktop( ) && serviceFlexId.equals( CsapConstants.AGENT_ID ) ) {

			propFile = new File( getCsapWorkingFolder( ) + "/../../src/main/resources" ) ;

		}

		logger.debug( "definition: {}, \n file: {}", serviceInstance.getPropDirectory( ), propFile
				.getAbsolutePath( ) ) ;
		return propFile ;

	}

	// varies based on cvs or svn, but gets the working directory
	public String getRootModelBuildLocation ( ) {

		if ( getRootProjectDefinitionUrl( )
				.startsWith( "http" ) ) {

			File f = new File( getRootProjectDefinitionUrl( )
					.substring( getRootProjectDefinitionUrl( )
							.indexOf( "/" ) ) ) ;
			return csapInstallFolder + "/build/" + f.getName( ) ;

		} else {

			return csapInstallFolder + "/build" + getRootProjectDefinitionUrl( ) ;

		}

	}

	private boolean testModeToSkipActivate = false ;

	public boolean isTestModeToSkipActivate ( ) {

		return testModeToSkipActivate ;

	}

	public void setTestModeToSkipActivate (
											boolean b ) {

		testModeToSkipActivate = b ;

	}

	public Project getAllPackages ( ) {

		return getActiveProject( ).getAllPackagesModel( ) ;

	}

	// @formatter:off

	public int getMaxDeploySecondsForService ( String serviceNamePort ) {

		// in case port is added
		String serviceName = serviceNamePort.split( "_" )[0] ;

		OptionalInt largestTimeout = getAllPackages( ).getServiceInstances( serviceName )
				.mapToInt( ServiceInstance::getDeployTimeOutSeconds )
				.max( ) ;

		if ( largestTimeout.isPresent( ) )
			return largestTimeout.getAsInt( ) ;

		return 30 ;

	}

	// @formatter:on

	/**
	 *
	 * Helper method to get access to instance config data
	 *
	 * @param svcName_port
	 * @return
	 */
	public ServiceInstance getServiceInstanceAnyPackage (
															String svcName_port ) {

		String[] serviceDescription = svcName_port.split( "_", 2 ) ;

		if ( serviceDescription.length != 2 ) {

			return null ;

		}

		String svcName = serviceDescription[0] ;
		String port = serviceDescription[1] ;
		ArrayList<ServiceInstance> instanceList = getRootProject( )
				.getAllPackagesModel( )
				.getServiceToAllInstancesMap( )
				.get( svcName ) ;

		if ( instanceList != null ) {

			for ( ServiceInstance instanceConfig : instanceList ) {

				if ( instanceConfig
						.getName( )
						.equals( svcName )
						&& instanceConfig
								.getPort( )
								.equals( port ) ) {

					return instanceConfig ;

				}

			}

		}

		return null ;

	}

	public ServiceInstance getServiceInstancePackage (
														String svcName_port ,
														String releasePackage ) {

		logger.debug( "svcName_port: {}", svcName_port ) ;

		String[] svc = svcName_port.split( "_" ) ;

		if ( ! svcName_port.contains( "_" ) ) {

			return null ;

		}

		String svcName = svc[0] ;
		String port = svc[1] ;
		ArrayList<ServiceInstance> instanceList = getProject( releasePackage )
				.getServiceToAllInstancesMap( )
				.get(
						svcName ) ;

		if ( instanceList != null ) {

			for ( ServiceInstance instanceConfig : instanceList ) {

				if ( instanceConfig
						.getName( )
						.equals( svcName )
						&& instanceConfig
								.getPort( )
								.equals( port ) ) {

					return instanceConfig ;

				}

			}

		}

		return null ;

	}

	public ServiceInstance getServiceInstanceCurrentHost (
															String svcName_port ) {

		if ( StringUtils.isNotEmpty( svcName_port ) ) {

			return getServiceInstance( svcName_port, getCsapHostName( ), getActiveProjectName( ) ) ;

		}

		return null ;

	}

	//
	// 3 variants:
	// - serviceName_port: legacy
	// - serviceName: non kubernetes
	// - serviceName-integer: kubernetes containers
	//
	public ServiceInstance flexFindFirstInstanceCurrentHost ( String serviceFlexId ) {

		if ( StringUtils.isEmpty( serviceFlexId ) ) {

			throw new IllegalArgumentException( "serviceFlexId cannot be null or empty" ) ;

		}

		ServiceInstance serviceInstance = getServiceInstanceCurrentHost( serviceFlexId ) ;

		if ( serviceInstance == null ) {

			serviceInstance = findServiceByNameOnCurrentHost( serviceFlexId ) ;

		}

		if ( serviceInstance == null && serviceFlexId.contains( "_" ) ) {

			// kubernetes strips off the port and appends an instance number
			String serviceName = serviceFlexId.substring( 0, serviceFlexId.lastIndexOf( "_" ) ) ;
			serviceInstance = findServiceByNameOnCurrentHost( serviceName ) ;

		}

		if ( serviceInstance == null && serviceFlexId.contains( "-" ) ) {

			// kubernetes strips off the port and appends an instance number
			String serviceName = serviceFlexId.substring( 0, serviceFlexId.lastIndexOf( "-" ) ) ;
			serviceInstance = findServiceByNameOnCurrentHost( serviceName ) ;

		}

		return serviceInstance ;

	}

	public ServiceInstance getServiceInstanceAnyPackage (
															String svcName_port ,
															String hostname ) {

		return getServiceInstance( svcName_port, hostname, getAllPackages( ).getName( ) ) ;

	}

	public ServiceInstance getServiceInstance (
												String serviceName ,
												String hostname ,
												String modelName ) {

		Project targetModel = getProject( modelName ) ;

		String[] serviceDescription = serviceName.split( "_", 2 ) ;

		String svcName = serviceDescription[0] ;
		// String port = serviceDescription[1] ;
		var instanceList = targetModel
				.getServicesListOnHost( hostname ) ;

		if ( instanceList != null ) {

			for ( ServiceInstance instanceConfig : instanceList ) {

				if ( instanceConfig
						.getName( )
						.equals( svcName )
				// && instanceConfig
				// .getPort()
				// .equals( port )
				) {

					return instanceConfig ;

				}

			}

		}

		return null ;

	}

	public Stream<ServiceInstance> servicesOnHost ( ) {
		// return hostToSvcPathList.get(getHostDir().getName());

		var hostServices = getActiveProject( )
				.getServicesListOnHost( getCsapHostName( ) ) ;

		if ( hostServices != null ) {

			return hostServices.stream( ) ;

		} else {

			logger.warn( "Did not find services on host: {}", getCsapHostName( ) ) ;
			return new ArrayList<ServiceInstance>( ).stream( ) ;

		}

	}

	public List<ServiceInstance> getServicesOnHost ( ) {

		// return hostToSvcPathList.get(getHostDir().getName());
		return getActiveProject( )
				.getServicesListOnHost( getCsapHostName( ) ) ;

	}

	public ServiceInstance getLocalAgent ( ) {

		return findServiceByNameOnCurrentHost( CsapConstants.AGENT_NAME ) ;

	}

	public ServiceInstance findServiceByNameOnCurrentHost (
															String name ) {

		Optional<ServiceInstance> optionalInstance = servicesOnHost( )
				.filter( instance -> instance.getName( ).equals( name ) )
				.findFirst( ) ;

		if ( optionalInstance.isPresent( ) ) {

			return optionalInstance.get( ) ;

		}

		logger.debug( "Failed to locate: {}", name ) ;

		return null ;

	}

	public List<ServiceInstance> getServicesOnTargetHost (
															String host ) {

		// return hostToSvcPathList.get(getHostDir().getName());
		logger.debug( "Host: {}, Model: {} ", host, getModelForHost( host ) ) ;

		try {

			// if ( host.length() > 2 ) throw new Exception("Peter Testing");
			return getModelForHost( host )
					.getServicesListOnHost( host ) ;

		} catch ( Exception e ) {

			logger.warn( "Failed finding host: {} ", host, e ) ;

		}

		return new ArrayList<>( ) ;

	}

	public String findHostWithLowestAttribute (
												List<String> hostNames ,
												String attribute ) {

		return getHostStatusManager( )
				.getHostWithLowestAttribute( hostNames, "/hostStats/cpuLoad" ) ;

	}

	public TreeMap<String, HashSet<String>> getServiceToArtifactMap (
																		Project model ) {

		var timer = csapApis.metrics( ).startTimer( ) ;

		if ( getHostStatusManager( ) == null ) {

			return null ;

		}

		var lifeCycleHostList = model
				.getLifeCycleToHostMap( )
				.get(
						getCsapHostEnvironmentName( ) ) ;

		var working_serviceToVersionMap = new TreeMap<String, HashSet<String>>( ) ;

		try {

			for ( var host : lifeCycleHostList ) {

				ObjectNode hostRuntime = getHostStatusManager( ).getResponseFromHost( host ) ;

				if ( hostRuntime != null ) {

					Iterator<String> serviceIter = hostRuntime
							.path( "services" )
							.fieldNames( ) ;

					while ( serviceIter.hasNext( ) ) {

						String service_port = serviceIter.next( ) ;

						String serviceName = hostRuntime
								.path( "services" )
								.path( service_port )
								.path( "serviceName" )
								.asText( ) ;

						String artifacts = hostRuntime
								.path( "services" )
								.path( service_port )
								.path( ContainerState.DEPLOYED_ARTIFACTS )
								.asText( ) ;

						if ( artifacts.contains( "##" ) ) {

							artifacts = artifacts.substring( artifacts.indexOf( "##" ) + 2 ) ;

						}

						if ( ! working_serviceToVersionMap.containsKey( serviceName ) ) {

							working_serviceToVersionMap.put( serviceName, new HashSet<String>( ) ) ;

						}

						HashSet<String> versionList = working_serviceToVersionMap.get( serviceName ) ;

						if ( artifacts
								.trim( )
								.length( ) != 0 ) {

							versionList.add( artifacts.trim( ) ) ;

						}

					}

				}

			}

		} catch ( Exception e ) {

			logger.error( "Failed parsing runtime", e ) ;

		}

		serviceToArtifactMap = working_serviceToVersionMap ;

		csapApis.metrics( ).stopTimer( timer, PERFORMANCE_ID + "service.artifactMap" ) ;
		return serviceToArtifactMap ;

	}

	public static String filePathAllOs (
											File path ) {

		String fpath = path.getAbsolutePath( ) ;

		try {

			if ( Application.isRunningOnDesktop( ) ) {

				fpath = path.getCanonicalPath( ).replaceAll( "\\\\", "/" ) ;

			} else {

				fpath = path.getCanonicalPath( ) ;

			}

		} catch ( Exception ex ) {

			CsapApis.getInstance( ).application( ).logger.warn( "Failed when checking {}", path.getAbsolutePath( ),
					ex ) ;

		}

		return fpath ;

	}

	public String getScriptToken ( ) {

		return CSAP_SCRIPTS_TOKEN ;

	}

	public File getScriptDir ( ) {

		File scriptDir = new File( getCsapSavedFolder( ), SCRIPTS_RUN ) ;

		if ( testModeToSkipActivate ) {

			scriptDir = new File( csapWorkingFolder, SCRIPTS_RUN ) ;

		}

		if ( ! scriptDir.exists( ) ) {

			logger.info( "Did not find {}, so creating", scriptDir.getAbsolutePath( ) ) ;
			scriptDir.mkdirs( ) ;

		}

		return scriptDir ;

	}

	public File createYamlFile (
									String desc ,
									String yaml ,
									String prefix )
		throws IOException {

		String suffix = desc + LocalDateTime.now( ).format( DateTimeFormatter.ofPattern( "MMM.d-HH.mm.ss" ) )
				+ ".yaml" ;
		String name = prefix + suffix ;

		File yamlFile = new File( getYamlDir( ), name ) ;

		logger.info( "Creating: {}", yamlFile.getAbsolutePath( ) ) ;

		FileUtils.writeStringToFile( yamlFile, yaml ) ;
		return yamlFile ;

	}

	public File getYamlDir ( ) {

		File yamlDir = new File( getCsapSavedFolder( ), YAML_LOAD ) ;

		if ( testModeToSkipActivate ) {

			yamlDir = new File( csapWorkingFolder, YAML_LOAD ) ;

		}

		if ( ! yamlDir.exists( ) ) {

			logger.info( "Did not find {}, so creating", yamlDir.getAbsolutePath( ) ) ;
			yamlDir.mkdirs( ) ;

		}

		return yamlDir ;

	}

	public Map<String, ArrayList<ServiceInstance>> serviceNameToAllInstances ( ) {

		return getActiveProject( )
				.getServiceToAllInstancesMap( ) ;

	}

	/**
	 * Current all service instances matching service name
	 *
	 * @param releasePackage
	 * @param serviceName
	 * @return
	 */
	public Stream<ServiceInstance> getServiceInstances (
															String releasePackage ,
															String serviceName ) {

		return getProject( releasePackage )
				.getServiceInstances( serviceName ) ;

	}

	public List<ServiceInstance> serviceInstancesByName (
															String releasePackage ,
															String serviceName_noPort ) {

		return getProject( releasePackage )
				.serviceInstancesInCurrentLifeByName( )
				.get( serviceName_noPort ) ;

	}

	/**
	 * use the raw definitions because lookup is done in every lifecycle
	 *
	 * @return
	 */
	public ObjectNode servicePerformanceLabels ( ) {

		var timer = csapApis.metrics( ).startTimer( ) ;

		ObjectNode serviceAttributesMap = jacksonMapper.createObjectNode( ) ;

		Project allModel = getAllPackages( ) ;

		allModel.getServiceNameStream( ).forEach( serviceName -> {

			ArrayNode meters = rootProjectEnvSettings( ).getRealTimeMeters( ) ;

			final String packageFilter = IntStream
					.range( 0, meters.size( ) )
					.mapToObj( meters::get )
					.filter( meter -> meter.has( "id" ) )
					.filter( meter -> {

						MetricCategory performanceCategory = MetricCategory.parse( meter ) ;

						if ( performanceCategory == MetricCategory.application ) {

							if ( serviceName
									.equals( performanceCategory.serviceName( meter ) ) ) {

								return true ;

							}

						}

						// logger.info(serviceName) ;
						return false ;

					} )
					.filter( meter -> meter.has( "optionSource" ) )
					.findFirst( )
					.map( meter -> meter.get( "optionSource" ).asText( ) )
					.orElse( ".*" ) ;

			logger.debug( "{} packageFilter: {}", serviceName, packageFilter ) ;

			Optional<JsonNode> serviceDefinitionOptional = allModel
					.getProjects( )
					.filter( pkg -> pkg.getName( ).matches( packageFilter ) )
					.filter( pkg -> ! pkg.findAndCloneServiceDefinition( serviceName ).isMissingNode( ) )
					.map( pkg -> pkg.findAndCloneServiceDefinition( serviceName ) )
					.findFirst( ) ;
			//

			if ( serviceDefinitionOptional.isPresent( ) ) {

				JsonNode perf = serviceDefinitionOptional.get( ).path( ServiceAttributes.performanceApplication
						.json( ) ) ;
				ObjectNode idToNameReport = jacksonMapper.createObjectNode( ) ;
				perf.fieldNames( ).forEachRemaining( id -> {

					try {

						if ( perf.get( id ).has( "title" ) ) {

							idToNameReport.put( id, perf.get( id ).get( "title" ).asText( "MissingTitle" ) ) ;

						} else {

							idToNameReport.put( id, id + "*" ) ;

						}

					} catch ( Exception e ) {

						logger.warn( "{} Missing {}, {}", serviceName, ServiceAttributes.performanceApplication.json( ),
								id, e ) ;

					}

				} ) ;

				// hook for kubernetes
				if ( serviceName.startsWith( "kubelet" ) ) {

					var settings = rootProjectEnvSettings( ) ;

					if ( settings.getKubernetesMeters( ).isArray( ) ) {

						CSAP.jsonStream( settings.getKubernetesMeters( ) )
								.filter( JsonNode::isTextual )
								.map( JsonNode::asText )
								.forEach( podName -> {

									idToNameReport.put( getProjectLoader( ).podHostCountName( podName ),
											getProjectLoader( ).podHostTotalTitle( podName ) ) ;
									idToNameReport.put( getProjectLoader( ).podTotalCountName( podName ),
											getProjectLoader( ).podTotalCountTitle( podName ) ) ;
									idToNameReport.put( getProjectLoader( ).podCoreName( podName ), getProjectLoader( )
											.podCoreTotalTitle( podName ) ) ;
									idToNameReport.put( getProjectLoader( ).podMemoryName( podName ),
											getProjectLoader( ).podMemoryTotalTitle( podName ) ) ;

								} ) ;

					}

				}

				serviceAttributesMap.set( serviceName, idToNameReport ) ;

			} else {

				logger.warn( " Did not find service definition: {} ", serviceName ) ;

			}

			// getServiceDefinition( serviceName );
		} ) ;

		csapApis.metrics( ).stopTimer( timer, PERFORMANCE_ID + "service.performanceLabels" ) ;

		return serviceAttributesMap ;

	}

	public ServiceInstance findFirstServiceInstanceInLifecycle (
																	String serviceName ) {

		Optional<ServiceInstance> def = getActiveProject( )
				.getAllPackagesModel( )
				.getServiceInstances( serviceName )
				.findFirst( ) ;

		if ( def.isPresent( ) ) {

			return def.get( ) ;

		}

		return null ;

	}

	public Stream<ServiceInstance> getActiveServiceInstances (
																String releasePackage ,
																String serviceName ) {

		return getProject( releasePackage )
				.getServiceInstances( serviceName )
				.filter( this::isRunningInLatestCollection ) ;

	}

	public boolean isRunningInLatestCollection (
													ServiceInstance serviceInstance ) {

		// Filter out the instances that are not running.
		ObjectNode collectedServiceStatus = getHostStatusManager( ).getServiceRuntime(
				serviceInstance.getHostName( ),
				serviceInstance.getServiceName_Port( ) ) ;

		ServiceInstance runtimeService ;

		try {

			runtimeService = ServiceInstance.buildInstance( jacksonMapper, collectedServiceStatus ) ;

		} catch ( Exception e ) {

			logger.warn( CSAP.buildCsapStack( e ) ) ;
			return false ;

		}

		return runtimeService.getContainerStatusList( ).get( 0 ).isActive( ) ;

	}

	public ArrayList<String> getVersionList ( ) {

		return getActiveProject( )
				.getVersionList( ) ;

	}

	public boolean isBootstrapComplete ( ) {

		return bootstrapComplete ;

	}

	public void setAgentStatus (
									String agentStatus ) {

		this.agentStatus = agentStatus ;

	}

	public void setBootstrapComplete ( ) {

		agentStatus = "admin" ; //
		bootstrapComplete = true ;

	}

	public void setCsapHostEnvironmentName (
												String hostEnvironmentName ) {

		csapHostEnvironmentName = hostEnvironmentName ;

	}

	public void setLastOp (
							String lastOp ) {

		lastOpMillis = System.currentTimeMillis( ) ;
		this.lastOp = lastOp ;

	}

	public void setLastOpMillis (
									long lastOpMillis ) {

		this.lastOpMillis = lastOpMillis ;

	}

	public void setMotdMessage (
									String motdMessage ) {

		this.motdMessage = motdMessage ;

	}

	public void shutdown ( ) {

		logger.info( CsapApplication.header( "shutting down application" ) ) ;

		if ( metricManager( ) != null ) {

			metricManager( ).shutdown( ) ;

		}

		if ( csapApplicationThreadPool != null ) {

			csapApplicationThreadPool.shutdownNow( ) ;

		}

		if ( _hostStatusManager != null ) {

			logger.info( CsapApplication.header( "shuttting down host status manager" ) ) ;
			// only admins
			_hostStatusManager.shutdown( ) ;
			_hostStatusManager = null ;

		}

	}

	@Override
	public String toString ( ) {

		StringBuilder sbuf = new StringBuilder( "\n\n ============== Active Model: "
				+ getActiveProject( )
						.getName( ) ) ;

		getRootProject( )
				.getProjects( )
				.forEach( project -> {

					sbuf.append( "\n Host Map for " + project.getName( ) ) ;

					for ( String host : project
							.getHostToServicesMap( )
							.keySet( ) ) {

						sbuf.append( "\n\t" + host + "\n\t\t" ) ;
						var svcList = project
								.getServicesListOnHost( host ) ;

						if ( svcList != null ) {

							int num = 0 ;

							for ( ServiceInstance serviceDefinition : svcList ) {

								sbuf.append( "     " + serviceDefinition.toSummaryString( ) ) ;

								if ( ++num % 5 == 0 ) {

									sbuf.append( "\n\t\t" ) ;

								}

							}

						}

					}

					sbuf.append( "\n Service Map for " + project.getName( ) ) ;

					for ( String service : project
							.getServiceToAllInstancesMap( )
							.keySet( ) ) {

						sbuf.append( "\n\t" + service + "\n\t\t" ) ;
						ArrayList<ServiceInstance> svcList = project
								.getServiceToAllInstancesMap( )
								.get( service ) ;

						if ( svcList != null ) {

							for ( ServiceInstance serviceDefinition : svcList ) {

								sbuf.append( "\t" + serviceDefinition.toSummaryString( ) ) ;

							}

						}

					}

				} ) ;

		return sbuf.toString( ) ;

	}

	public class HTMLCharacterEscapes extends CharacterEscapes {

		/**
		 *
		 */
		private static final long serialVersionUID = 1L ;
		private final int[] asciiEscapes ;

		public HTMLCharacterEscapes ( ) {

			// start with set of characters known to require escaping
			// (double-quote, backslash etc)
			int[] esc = CharacterEscapes.standardAsciiEscapesForJSON( ) ;
			// and force escaping of a few others:
			esc['<'] = CharacterEscapes.ESCAPE_STANDARD ;
			esc['>'] = CharacterEscapes.ESCAPE_STANDARD ;
			esc['&'] = CharacterEscapes.ESCAPE_STANDARD ;
			esc['\''] = CharacterEscapes.ESCAPE_STANDARD ;
			asciiEscapes = esc ;

		}

		// this method gets called for character codes 0 - 127
		@Override
		public int[] getEscapeCodesForAscii ( ) {

			return asciiEscapes ;

		}

		// and this for others; we don't need anything special here
		@Override
		public SerializableString getEscapeSequence (
														int ch ) {

			// no further escaping (beyond ASCII chars) needed:
			return null ;

		}
	}

	public Project getModel (
								String hostName ,
								String serviceName ) {

		Project hostModel ;

		if ( hostName.equals( "*" ) ) {

			hostModel = getRootProject( ).getModelForService( serviceName ) ;

		} else {

			hostModel = getModelForHost( hostName ) ;

		}

		if ( serviceName.equals( CsapConstants.AGENT_NAME ) ) {

			hostModel = getRootProject( ) ;

		}

		return hostModel ;

	}

	public Project getModelForHost (
										String hostName ) {

		if ( ! isAdminProfile( ) ) {

			return getActiveProject( ) ;

		}

		Project releasePackage = getRootProject( )
				.getReleasePackageForHost( hostName ) ;

		return releasePackage ;

	}

	public File getLogDir (
							String serviceName_port ) {

		ServiceInstance instance = getServiceInstanceCurrentHost( serviceName_port ) ;

		if ( instance == null ) {

			logger.warn( "Did not location instance for: () ", serviceName_port ) ;
			return null ;

		}

		return instance.getLogWorkingDirectory( ) ;

	}

	public File getWorkingLogDir (
									String serviceName_port ) {

		ServiceInstance instance = getServiceInstanceCurrentHost( serviceName_port ) ;

		if ( instance == null ) {

			logger.warn( "Did not location instance for: () ", serviceName_port ) ;
			return null ;

		}

		return new File( getCsapWorkingSubFolder( instance.getName( ) ), "logs" ) ;

	}

	public String getDefaultLogFileName (
											String svcName ) {

		ServiceInstance instance = getServiceInstanceCurrentHost( svcName ) ;
		return instance.getDefaultLogToShow( ) ;

	}

	public String getDefaultLogFile (
										String svcName ) {

		File logDir = getLogDir( svcName ) ;

		String logFileName = getDefaultLogFileName( svcName ) ;
		File defaultFile = new File( logDir, logFileName ) ;

		// needs to be remote
		if ( ! defaultFile.exists( ) && logDir.exists( ) ) {

			logger.debug( "Searching now: {} ", logDir.getAbsolutePath( ) ) ;

			try {

				for ( String name : logDir.list( ) ) {

					logger.debug( "found: {} ", name ) ;
					logFileName = name ;

					if ( name.endsWith( ".log" ) ) {

						break ;

					}

				}

			} catch ( Exception e ) {

				logger.debug( "Error getting files", e ) ;

			}

		}

		return logDir.getName( ) + "/" + logFileName ;

	}

	public File getRequestedFile (
									String fromFolder ,
									String serviceFlexId ,
									boolean isLogFile ) {

		File targetFile = new File( getCsapWorkingFolder( ), fromFolder ) ;

		if ( isLogFile ) {

			targetFile = new File(
					getLogDir( serviceFlexId ),
					fromFolder ) ;

		} else if ( StringUtils.isNotEmpty( serviceFlexId ) && fromFolder.startsWith( FileToken.PROPERTY.value ) ) {

			targetFile = new File( getPropertyFolder( serviceFlexId ),
					fromFolder.substring( 9 ) ) ;

			logger.info( "fromFolder: {} \n propFolder: {} \n targetFile: {}", fromFolder, getPropertyFolder(
					serviceFlexId ), targetFile.getAbsolutePath( ) ) ;

		} else if ( fromFolder.startsWith( FileToken.WORKING.value ) ) {

			targetFile = new File( getCsapWorkingFolder( ),
					fromFolder.substring( FileToken.WORKING.value.length( ) ) ) ;

		} else if ( fromFolder.startsWith( FileToken.PLATFORM.value ) ) {

			if ( fromFolder.startsWith( getDefinitionToken( ) + "/" ) && isRunningOnDesktop( ) ) {

				// targetFile = new File( getDefinitionFolder(),
				// fromFolder.substring( 16 ) ) ;
				targetFile = new File( getCsapInstallFolder( ),
						fromFolder.substring( FileToken.PLATFORM.value.length( ) ) ) ;
				logger.info( " Desktop definition: {}", targetFile.getAbsolutePath( ) ) ;

			} else {

				targetFile = new File( getCsapInstallFolder( ),
						fromFolder.substring( FileToken.PLATFORM.value.length( ) ) ) ;

			}

		} else if ( fromFolder.startsWith( FileToken.HOME.value ) ) {

			targetFile = new File(
					System.getProperty( "user.home" ),
					fromFolder.substring( FileToken.HOME.value.length( ) ) ) ;

		} else if ( fromFolder.startsWith( FileToken.ROOT.value ) ) {

			targetFile = new File(
					"/",
					fromFolder.substring( FileToken.ROOT.value.length( ) ) ) ;

			if ( fromFolder.contains( ":" ) ) {

				logger.info( "Suspected windows path: {} . Will skip past the : for dev purposes.", fromFolder ) ;
				targetFile = new File( "/", fromFolder.substring( fromFolder.lastIndexOf( ":" ) + 2 ) ) ;

			}

		}

		try {

			targetFile = targetFile.getAbsoluteFile( ) ;

		} catch ( Exception e ) {

			logger.error( "Failed to resolve file: {}", targetFile ) ;

		}

		if ( isRunningOnDesktop( )
				&& ! targetFile.exists( )
				&& StringUtils.isNotEmpty( serviceFlexId )
				&& serviceFlexId.equals( CsapConstants.AGENT_NAME )
				&& fromFolder.contains( serviceFlexId ) ) {

			var nameOffset = fromFolder.indexOf( serviceFlexId ) + serviceFlexId.length( ) + 1 ;

			var message = "Desktop Detected, targetFile does not exist folder: fromFolder: {}   targetFile: {}" ;

			if ( nameOffset < fromFolder.length( ) ) {

				targetFile = new File(
						getCsapInstallFolder( ),
						fromFolder.substring( nameOffset ) ) ;

				message = "Desktop Detected, updating: fromFolder: {}   targetFile: {}" ;

			}

//			if ( isDisplayOnDesktop( ) ) {

			logger.warn( "Desktop Detected, targetFile does not exist folder: fromFolder: {}   targetFile: {}",
					fromFolder, targetFile ) ;

//			}

		}

		logger.debug( "fromFolder: {}   targetFile: {}", fromFolder, targetFile ) ;

		return targetFile ;

	}

	static int desktopDisplayCount = 0 ;
	static private int MAX_DESKTOP_COUNT = 5 ;

	static public boolean isDisplayOnDesktop ( ) {

		if ( isRunningOnDesktop( ) ) {

			if ( desktopDisplayCount++ < MAX_DESKTOP_COUNT ) {

				return true ;

			}

		}

		return false ;

	}

	public File getCsapBuildFolder ( ) {

		return csapBuildFolder ;

	}

	public File getCsapBuildFolder ( String path ) {

		return new File( getCsapBuildFolder( ), path ) ;

	}

	public ApplicationConfiguration getCsapCoreService ( ) {

		return csapCoreService ;

	}

	public void setCsapCoreService (
										ApplicationConfiguration agentService ) {

		this.csapCoreService = agentService ;

	}

	public void flushCollectionCacheToDisk (
												ArrayNode cache ,
												String cacheName ) {

		try {

			File cacheFile = getCollectionCacheLocation( cacheName ) ;

			if ( cacheFile.exists( ) ) {

				logger.error( "Existing file found, should not happen: {}", cacheFile.getCanonicalPath( ) ) ;
				cacheFile.delete( ) ;

			}

			// show during shutdowns - log4j may not be output
			System.out.println( "\n *** Writing cache to disk: " + cacheFile.getAbsolutePath( ) ) ;

			FileUtils.writeStringToFile( cacheFile, jacksonMapper.writeValueAsString( cache ) ) ;

		} catch ( Exception e ) {

			logger.error( "Failed to store cache {}", CSAP.buildCsapStack( e ) ) ;

		}

	}

	private File getCollectionCacheLocation (
												String cacheName ) {

		var cacheFile = new File( getCsapSavedFolder( ), "collection-cache/" + cacheName + ".json" ) ;
		cacheFile.getParentFile( ).mkdirs( ) ;

		return cacheFile ;

	}

	public void loadCollectionCacheFromDisk (
												ArrayNode cache ,
												String cacheName ) {

		try {

			var cacheFile = getCollectionCacheLocation( cacheName ) ;

			if ( cacheFile.exists( ) ) {

				logger.info( "Reading cache disk: {}", cacheFile.getAbsolutePath( ) ) ;

				String cacheData = FileUtils.readFileToString( cacheFile ) ;
				JsonNode loadedCache = jacksonMapper.readTree( cacheData ) ;

				if ( loadedCache.isArray( ) ) {

					logger.info( "Loading disk entries: {}", loadedCache.size( ) ) ;
					cache.addAll( (ArrayNode) loadedCache ) ;

				}

				logger.info( "in memory cache size: {}", cache.size( ) ) ;

				if ( ! cacheFile.delete( ) ) {

					logger.warn( "Cache read in - but cannot delete" ) ;

				}

			} else {

				logger.warn( "Did not find cache file: '{}' \n\t note: should only happen on a fresh install", cacheFile
						.getCanonicalPath( ) ) ;

			}

		} catch ( Exception e ) {

			logger.error( "Failed to load cache {}", CSAP.buildCsapStack( e ) ) ;

		}

	}

	final static int CHUNKING_SIZE = 100 * 1024 ;

	public boolean isHostAuthenticatedMember (
												String ipAddress ) {

		try {

			InetAddress addr = InetAddress.getByName( ipAddress ) ;
			String remoteServerName = addr.getHostName( ) ;

			if ( remoteServerName.equals( "127.0.0.1" ) ) {

				remoteServerName = DESKTOP_CLUSTER_HOST ;

			}

			if ( remoteServerName.contains( "." ) ) {

				remoteServerName = remoteServerName.substring( 0, remoteServerName.indexOf( "." ) ) ;

			}

			if ( remoteServerName.equals( "rtp-someDeveloper-8811" ) && getCsapHostEnvironmentName( ).equals(
					"dev" ) ) {

				logger.warn( "DEVELOPER TESTING: Resolved: {} to host: {}", ipAddress, remoteServerName ) ;
				return true ;

			}

			logger.debug( "Resolved: {} to host: {}", ipAddress, remoteServerName ) ;

			return getAllHostsInAllPackagesInCurrentLifecycle( ).contains( remoteServerName ) ;

		} catch ( Exception e ) {

			logger.error( "Failed to get hostname" ) ;

		}

		return false ;

	}

	public int getHostRefreshIntervalSeconds ( ) {

		return hostRefreshIntervalSeconds ;

	}

	public void setHostRefreshIntervalSeconds (
												int hostRefreshIntervalSeconds ) {

		this.hostRefreshIntervalSeconds = hostRefreshIntervalSeconds ;

	}

	public String getAgentRunHome ( ) {

		return agentRunHome ;

	}

	public File getAutoStartDisabledFile ( ) {

		return new File( getAgentRunHome( ) + "/" + CsapConstants.AUTO_START_DISABLED_FILE ) ;

	}

	public File getAutoPlayFile ( ) {

		return new File( getAgentRunHome( ) + "/" + CsapConstants.AUTO_PLAY_FILE ) ;

	}

	public void setAgentRunHome (
									String agentRunHome ) {

		this.agentRunHome = agentRunHome ;

	}

	public String getAgentRunUser ( ) {

		return agentRunUser ;

	}

	public void setAgentRunUser (
									String agentRunUser ) {

		this.agentRunUser = agentRunUser ;

	}

	public ArrayList<String> buildOsUsersList ( ) {

		var osUsers = new ArrayList<String>( ) ;
		osUsers.add( getAgentRunUser( ) ) ;

		if ( Application.isRunningOnDesktop( ) ) {

			osUsers.add( "csap" ) ;

		}

		osUsers.add( "root" ) ;
		return osUsers ;

	}

	int getAgentConnectinReadTimeoutSeconds ( ) {

		return agentConnectinReadTimeoutSeconds ;

	}

	void setAgentConnectinReadTimeoutSeconds (
												int agentConnectinReadTimeout ) {

		this.agentConnectinReadTimeoutSeconds = agentConnectinReadTimeout ;

	}

	public CsapSecuritySettings getSecuritySettings ( ) {

		return securitySettings ;

	}

	public void setSecuritySettings (
										CsapSecuritySettings securitySettings ) {

		this.securitySettings = securitySettings ;

	}

	public void setSpringEnvironment (
										Environment springEnvironment ) {

		this.springEnvironment = springEnvironment ;

	}

	public ServiceInstance kubeletInstance ( ) {

		return findServiceByNameOnCurrentHost( csapApis.kubernetes( ).getDiscoveredServiceName( ) ) ;

	}

	public static boolean isDeveloperMode ( ) {

		return developerMode ;

	}

	public static void setDeveloperMode (
											boolean developerMode ) {

		Application.developerMode = developerMode ;

	}

	public void create_browseable_file_and_redirect_to_it (
															HttpServletResponse response ,
															File targetFile )
		throws IOException {

		// adding content link under agent to allow browsing
		File agentWebDir = getAgentWebDir( ) ;

		agentWebDir.mkdirs( ) ;

		String[] lines = {
				"#!/bin/bash",
				"set -x",
				"cd " + agentWebDir,
				"echo current folder is: $(pwd)",
				// "\rm -rf *",
				"ln -s  " + targetFile.getParentFile( ).getAbsolutePath( ),
				""
		} ;

		String agentWebDirSetup = osCommandRunner.runUsingRootUser( "agentWebDir", Arrays.asList( lines ) ) ;

		if ( isDesktopHost( ) ) {

			var desktopTestFile = new File( "target/classes/static/htmlViewer", targetFile.getName( ) ) ;
			desktopTestFile.getParentFile( ).mkdirs( ) ;
			logger.warn( CsapApplication.testHeader( "java copy for desktop: {}" ), desktopTestFile ) ;
			FileUtils.copyFile( targetFile, desktopTestFile ) ;

		}

		var parentPath = "" ;
		var parentName = targetFile.getParentFile( ).getName( ) ;

		if ( ! parentName.equals( "/" ) && ! StringUtils.isEmpty( parentName ) ) {

			parentPath = "/" + parentName ;

		}

		String locationUrl = "/" + agentWebDir.getName( ) + parentPath + "/" + targetFile.getName( ) ;

		logger.debug(
				"html file requested: {}, parentPath: '{}' \n\t created link in : {},  \n\t locationUrl: {},\n output: {}",
				targetFile.getAbsolutePath( ),
				targetFile.getParentFile( ).getName( ),
				agentWebDir,
				locationUrl,
				agentWebDirSetup ) ;

		csapApis.events( ).publishUserEvent( CsapEvents.CSAP_OS_CATEGORY + "/file/htmlViewer",
				CsapUser.currentUsersID( ),
				csapApis.events( ).fileName( targetFile, 100 ),
				targetFile.getAbsolutePath( ) ) ;

		String linkedContent = "**CSAP INJECTED: Use browser:" +
				"<a href='" + locationUrl
				+ "' target='_blank'>" +
				targetFile.getName( ) + "</a>" ;

		response.setHeader( "Location", locationUrl ) ;
		response.setStatus( 302 ) ;
		response.getWriter( ).println( linkedContent.getBytes( ) ) ;

	}

	public boolean isApplicationLoaded ( ) {

		return getApplicationLoadedAtMillis( ) > 0 ;

	}

	public long getApplicationLoadedAtMillis ( ) {

		return applicationLoadedAtMillis ;

	}

	private boolean isSkipScanAndActivate ( ) {

		return skipScanAndActivate ;

	}

	public void setSkipScanAndActivate ( boolean minimumMode ) {

		this.skipScanAndActivate = minimumMode ;

	}

	public ScoreReport getScoreReport ( ) {

		return scoreReport ;

	}

	public HealthManager healthManager ( ) {

		return healthManager ;

	}

	public MetricManager metricManager ( ) {

		return metricManager ;

	}

	public ActiveUsers getActiveUsers ( ) {

		return activeUsers ;

	}

	public final ObjectNode serviceTemplates ;

	public ObjectNode getServiceTemplates ( ) {

		return serviceTemplates ;

	}

	public ObjectNode getTemplateAndUpdateVariables ( String templateName , String agentSourceDir ) {

		ObjectNode serviceTemplate = (ObjectNode) getServiceTemplates( ).path( templateName ) ;

		if ( serviceTemplate.has( ServiceAttributes.deployFromSource.json( ) ) ) {

			ObjectNode sourcePath = (ObjectNode) serviceTemplate.path( ServiceAttributes.deployFromSource.json( ) ) ;

			if ( ! sourcePath.isMissingNode( ) && sourcePath.has( "path" )
					&& sourcePath.get( "path" ).asText( ).equals(
							"https://github.com/csap-platform/csap-starter.git" ) ) {

				// use agent start
				sourcePath.put( "path", agentSourceDir.replace( "csap-core", "csap-starter" ) ) ;

			}

		}

		var templateRepo = "docker.io" ;

		if ( csapApis.isContainerProviderInstalledAndActive( ) ) {

			templateRepo = csapApis.containerIntegration( ).getDockerConfig( ).getSettings( )
					.getTemplateRepository( ) ;

		}

		if ( serviceTemplate.has( ServiceAttributes.dockerSettings.json( ) ) ) {

			ObjectNode dockerSettings = (ObjectNode) serviceTemplate.path( ServiceAttributes.dockerSettings.json( ) ) ;
			String imageName = dockerSettings.path( "image" ).asText( "" ) ;

			if ( StringUtils.isNotEmpty( imageName ) ) {

				imageName = dockerSettings.path( "image" ).asText( ).replaceAll(
						Matcher.quoteReplacement( CsapConstants.DOCKER_REPOSITORY ),
						Matcher.quoteReplacement( templateRepo ) ) ;

				dockerSettings.put( "image", imageName ) ;

			}

		} else {

			String imageName = serviceTemplate.path( "image" ).asText( "" ) ;

			if ( StringUtils.isNoneEmpty( imageName ) ) {

				imageName = serviceTemplate.path( "image" ).asText( ).replaceAll(
						Matcher.quoteReplacement( CsapConstants.DOCKER_REPOSITORY ),
						Matcher.quoteReplacement( templateRepo ) ) ;

				serviceTemplate.put( "image", imageName ) ;

			}

		}

		return serviceTemplate ;

	}

	public String getResourcePath ( String key ) {

		String urlPath ;

		switch ( key ) {

		case "DEFINITION_URL":
			urlPath = CsapConstants.DEFINITION_URL ;
			break ;

		case "EXPLORER_URL":
			urlPath = KubernetesExplorer.EXPLORER_URL ;
			break ;

		case "FILE_URL":
			urlPath = CsapConstants.FILE_URL ;
			break ;

		case "OS_URL":
			urlPath = CsapConstants.OS_URL ;
			break ;

		default:
			urlPath = "/" ;

		}

		return urlPath ;

	}

	public String getServiceDebugName ( ) {

		return serviceDebugName ;

	}

	public void setServiceDebugName ( String serviceDebugName ) {

		this.serviceDebugName = serviceDebugName ;

	}

}
