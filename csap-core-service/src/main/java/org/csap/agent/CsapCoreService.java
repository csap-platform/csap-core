package org.csap.agent ;

import java.io.IOException ;
import java.lang.reflect.Method ;
import java.net.MalformedURLException ;
import java.net.URL ;
import java.util.Arrays ;
import java.util.List ;
import java.util.Map ;

import org.aspectj.lang.ProceedingJoinPoint ;
import org.aspectj.lang.annotation.Around ;
import org.aspectj.lang.annotation.Aspect ;
import org.aspectj.lang.annotation.Pointcut ;
import org.csap.CsapBootApplication ;
import org.csap.agent.ui.explorer.KubernetesExplorer ;
import org.csap.agent.ui.explorer.OsExplorer ;
import org.csap.agent.ui.rest.ApplicationBrowser ;
import org.csap.agent.ui.rest.FileRequests ;
import org.csap.agent.ui.rest.HostRequests ;
import org.csap.agent.ui.rest.ServiceRequests ;
import org.csap.agent.ui.rest.TrendCache ;
import org.csap.alerts.AlertSettings ;
import org.csap.helpers.CSAP ;
import org.csap.helpers.CsapApplication ;
import org.csap.helpers.CsapRestTemplateFactory ;
import org.csap.integations.CsapEncryptionConfiguration ;
import org.csap.integations.CsapInformation ;
import org.csap.integations.CsapMicroMeter ;
import org.csap.integations.CsapSecurityConfiguration ;
import org.csap.integations.CsapWebServerConfig ;
import org.csap.security.config.CsapSecurityRoles ;
import org.csap.security.config.CsapSecuritySettings ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.beans.factory.annotation.Autowired ;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty ;
import org.springframework.boot.context.properties.ConfigurationProperties ;
import org.springframework.cache.interceptor.KeyGenerator ;
import org.springframework.context.annotation.Bean ;
import org.springframework.context.annotation.Import ;
import org.springframework.core.io.Resource ;
import org.springframework.core.task.TaskExecutor ;
import org.springframework.http.HttpMethod ;
import org.springframework.http.converter.FormHttpMessageConverter ;
import org.springframework.http.converter.StringHttpMessageConverter ;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter ;
import org.springframework.scheduling.TaskScheduler ;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor ;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler ;
import org.springframework.web.client.RestTemplate ;
import org.springframework.web.servlet.config.annotation.CorsRegistry ;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry ;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer ;
import org.springframework.web.servlet.resource.VersionResourceResolver ;

@CsapBootApplication
@ConfigurationProperties ( prefix = "csap-core" )
@Import ( CsapSecuritySettings.class )
@Aspect
public class CsapCoreService implements WebMvcConfigurer {

	final Logger logger = LoggerFactory.getLogger( getClass( ) ) ;

	final static public String CONFIGURATION_PREFIX = "csap-core" ;

	//
	// Primary configuration attributes
	//

	private boolean definitionStrictMode = true ;

	private String installationFolder = "" ;

	private String minVersionCsap = "2.0.9" ;
	private String minVersionDocker = "19.03." ;
	private String minVersionKubelet = "1.16." ;

	private int agentPort = 8011 ;
	private String agentContextPath = "/" ;

	private boolean jmxAuthentication = true ;
	private String jmxUser = "csap" ;
	private String jmxPass = "csap" ;

	private boolean svnEnabled = false ;
	private String definitionFolder = "" ;

	private String screenCastServerUrl = "http://not.configured" ;

	// Used extensively for intra node communication, and UIrarely used - when DNS
	// of v
	private String hostUrlPattern = "" ;
	private String hostUrlPatternInternal = null ;

	//
	//  ssl settings: map to CsapRestTemplate
	//
	private List<String> sslForceHosts ;

	private List<String> dockerUiDefaultImages ;

	private Map<String, String> helpUrls ;
	


	@Autowired
	CsapInformation csapInformation ;
	
	@Autowired
	CsapMicroMeter.Utilities meterUtilities ;

	CsapWebServerConfig csapWebServer ;
	CsapEncryptionConfiguration csapEncrypt ;
	
	public CsapCoreService( CsapWebServerConfig csapWebServer, CsapEncryptionConfiguration csapEncrypt ) {
		
		this.csapWebServer = csapWebServer ;
		this.csapEncrypt = csapEncrypt ;
		
	}

	public static boolean isRunningOnDesktop ( ) {

		return ! CsapApplication.isCsapFolderSet( ) ;

	}
	

	// Security
	// must be synced with ehcache.xml
	public final static String TIMEOUT_CACHE_60s = "CacheWith60SecondEviction" ;

	// URLs
	public final static String BASE_URL = "/" ;
	public final static String TEST_URL = BASE_URL + "test" ;
	public final static String METER_URL = BASE_URL + "MeterActivity" ;
	public final static String APP_BROWSER_URL = BASE_URL + "app-browser" ;
	public final static String MAINHOSTS_URL = BASE_URL + "hosts" ;
	public final static String CLUSTERBROWSER_URL = BASE_URL + "clusterDialog" ;
	public final static String ADMIN_URL = BASE_URL + "admin" ;
	public final static String EDIT_URL = BASE_URL + "edit" ;
	public final static String SCREEN_URL = BASE_URL + "viewScreencast" ;

	public final static String DEFINITION_URL = BASE_URL + "definition" ;
	public final static String ENCODE_URL = "/properties/encode" ;
	public final static String ENCODE_FULL_URL = DEFINITION_URL + ENCODE_URL ;
	public final static String NOTIFY_URL = "/notify" ;
	public final static String NOTIFY_FULL_URL = DEFINITION_URL + NOTIFY_URL ;
	public final static String DECODE_URL = "/properties/decode" ;

	public final static String SERVICE_URL = BASE_URL + "service" ;
	public final static String FILE_URL = BASE_URL + "file" ;
	public final static String FILE_MANAGER_URL = FILE_URL + FileRequests.FILE_MANAGER ;
	public final static String OS_URL = BASE_URL + "os" ;
	public final static String API_URL = BASE_URL + "api" ;
	public final static String API_AGENT_URL = API_URL + "/agent" ;
	public final static String API_MODEL_URL = API_URL + "/model" ;
	public final static String API_CONTAINER_URL = API_URL + "/container" ;
	public final static String API_APPLICATION_URL = API_URL + "/application" ;
	public final static String JSP_VIEW = "/view/" ;

	public static void main ( String[] args ) {

		// CSAP sets up shared profiles
		CsapApplication.run( CsapCoreService.class, args ) ;

		// SpringApplication.run( CsapCoreService.class, args );..
	}

	@Bean
	public WebMvcConfigurer corsConfigurer ( ) {

		return new WebMvcConfigurer( ) {
			@Override
			public void addCorsMappings ( CorsRegistry registry ) {

				// registry
				// .addMapping( API_URL + "/**" )
				// .allowedMethods( HttpMethod.GET.name() )
				// .allowCredentials( true );
				//
				// registry
				// .addMapping( CsapCoreService.SERVICE_URL + "/**" )
				// .allowedMethods( HttpMethod.GET.name() )
				// .allowCredentials( true );
				//
				// registry
				// .addMapping( CsapCoreService.FILE_URL + "/**" )
				// .allowedHeaders( "*" )
				// .allowedMethods( "*" )
				// .allowedOrigins( "*" )
				// .allowCredentials( true );

				registry
						.addMapping( "/**" )
						.allowedHeaders( "*" )
						.allowedMethods( "*" )
						.allowedOrigins( "*" ) ;

//						.allowCredentials( true ) ;
			}
		} ;

	}

	@Bean ( "analyticsKeyGenerator" )
	public KeyGenerator analyticsKeyGenerator ( ) {

		return new KeyGenerator( ) {

			@Override
			public Object generate ( Object target , Method method , Object... params ) {

				String key ;

				if ( params.length == 2 ) {

					key = params[1].toString( ) + TrendCache.buildReportHash( params[0].toString( ) ) ;

				} else {

					logger.warn( "Unexpected params: {}", Arrays.asList( params ) ) ;
					key = "unexpected-params" ;

				}

				logger.debug( "key: {}, params: {}", key, Arrays.asList( params ) ) ;
				return key ;

			}
		} ;

	}

	@Bean ( "compareKeyGenerator" )
	public KeyGenerator compareKeyGenerator ( ) {

		return new KeyGenerator( ) {

			@Override
			public Object generate ( Object target , Method method , Object... params ) {

				return "analyticsCompareReport-" + TrendCache.buildReportHash( Arrays.asList( params ).toString( ) ) ;

			}
		} ;

	}

	// configure @Scheduled thread pool
	@Bean
	public TaskScheduler taskScheduler ( ) {

		ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler( ) ;
		scheduler.setThreadNamePrefix( CsapCoreService.class.getSimpleName( ) + "Scheduler" ) ;
		scheduler.setPoolSize( 2 ) ;
		return scheduler ;

	}

	final public static String HEALTH_EXECUTOR = "CsapHealthExecutor" ;

	@Bean ( HEALTH_EXECUTOR )
	public TaskExecutor taskExecutor ( ) {

		ThreadPoolTaskExecutor taskExecutor = new ThreadPoolTaskExecutor( ) ;
		taskExecutor.setThreadNamePrefix( CsapCoreService.class.getSimpleName( ) + "@Async" ) ;
		taskExecutor.setMaxPoolSize( 5 ) ;
		taskExecutor.setQueueCapacity( 300 ) ;
		taskExecutor.afterPropertiesSet( ) ;
		return taskExecutor ;

	}

	private int ONE_YEAR_SECONDS = 60 * 60 * 24 * 365 ;

	// https://spring.io/blog/2014/07/24/spring-framework-4-1-handling-static-web-resources
	// http://www.mscharhag.com/spring/resource-versioning-with-spring-mvc
	@Override
	public void addResourceHandlers ( ResourceHandlerRegistry registry ) {

		// if ( Application.isRunningOnDesktop() ) { // NOT initialized prior to
		// start
		if ( isRunningOnDesktop( ) ) {

			logger.warn( CsapApplication.testHeader( "Desktop detected: Caching DISABLED" ) ) ;
			return ; // when disabled in yaml
			// ONE_YEAR_SECONDS = 0;
			// return;

		} else {

			logger.info( "Web caching enabled" ) ;

		}

		// String version = csapInformation.getVersion(); // this is fixed
		// version from definition
		// // find actual version? or use snap?
		// if ( version.toLowerCase().contains( "snapshot" ) ) {
		// version = "snap" + System.currentTimeMillis();
		// }
		String version = "start" + System.currentTimeMillis( ) ;
		VersionResourceResolver versionResolver = new VersionResourceResolver( )
				.addFixedVersionStrategy( version,
						"/**/modules/**/*.js" ) // requriesjs uses relative paths
				.addContentVersionStrategy( "/**" ) ;

		// A Handler With Versioning - note images in css files need to be
		// resolved.
		registry.addResourceHandler( "/**/*.js", "/**/*.css", "/**/*.png", "/**/*.gif", "/**/*.jpg" )
				.addResourceLocations( "classpath:/static/", "classpath:/public/" )
				.setCachePeriod( ONE_YEAR_SECONDS )
				.resourceChain( true )
				.addResolver( versionResolver ) ;

	}

	@Bean
	@ConditionalOnProperty ( "csap.security.enabled" )
	public CsapSecurityConfiguration.CustomHttpSecurity mySecurityPolicy ( ) {

		// spring.resources.cache-period should almost always be set as well
		// @formatter:off
		CsapSecurityConfiguration.CustomHttpSecurity mySecurity = ( httpSecurity -> {

			httpSecurity

					// CSRF adds complexity - refer to
					// https://docs.spring.io/spring-security/site/docs/current/reference/htmlsingle/#csrf
					// csap.security.csrf also needs to be enabled or this will be ignored
					.csrf( )
					.requireCsrfProtectionMatcher(
							CsapSecurityConfiguration.buildRequestMatcher(
									"/login*",
									FILE_URL + FileRequests.SAVE_URL,
									OS_URL + HostRequests.EXECUTE_URL ) )
					.and( )

					.authorizeRequests( )

					// CORS OATH Bearer support
					.antMatchers( HttpMethod.OPTIONS ).permitAll( )

					// Public assets for UI
					// Spring boot allows: "/webjars/**", "/js/**", "/css/**", "/images/**"

					.antMatchers( "/login", "/noAuth/**", "/swagger-resources/**", "/v2/**" )
					.permitAll( )

					//
					// Api access is protected at application level if needed
					.antMatchers( API_URL + "/**" )
					.permitAll( ) // Disable security on public assets

					//
					// App Browser get requests
					//
					.antMatchers( HttpMethod.GET,
							FILE_URL + FileRequests.FILE_REMOTE_MONITOR,
							APP_BROWSER_URL + ApplicationBrowser.POD_RESOURCE_URL,
							APP_BROWSER_URL + ApplicationBrowser.POD_LOG_URL,
							APP_BROWSER_URL + ApplicationBrowser.POD_REPORT_URL,
							APP_BROWSER_URL + ApplicationBrowser.NODE_REPORT_URL,
							APP_BROWSER_URL + ApplicationBrowser.REALTIME_REPORT_URL,
							APP_BROWSER_URL + ApplicationBrowser.VOLUME_REPORT_URL,
							APP_BROWSER_URL + ApplicationBrowser.CSAP_EVENT_REPORT_URL,
							APP_BROWSER_URL + ApplicationBrowser.KUBERNETES_EVENT_REPORT_URL,
							KubernetesExplorer.EXPLORER_URL + "/kubernetes/cli/info/**" )
					.permitAll( ) // Disable security on public listing

					//
					// Service Deployment
					//
					.antMatchers( SERVICE_URL + ServiceRequests.REBUILD_URL )
					.access( CsapSecurityRoles.hasAny( CsapSecurityRoles.Access.build ) )

					//
					// admin actions
					//
					.antMatchers( SERVICE_URL + "/reImage",
							SERVICE_URL + ServiceRequests.START_URL,
							SERVICE_URL + "/stopServer",
							SERVICE_URL + ServiceRequests.KILL_URL,
							SERVICE_URL + ServiceRequests.GENERATE_APACHE_MAPPINGS,
							SERVICE_URL + "/httpd",
							SERVICE_URL + "/modjk",
							SERVICE_URL + "/status",
							SERVICE_URL + "/uploadArtifact",
							SERVICE_URL + "/jmeter" )
					.access( CsapSecurityRoles.hasAny( CsapSecurityRoles.Access.admin ) )

					//
					// Definition Management
					//
					.antMatchers( HttpMethod.GET, DEFINITION_URL + "/**" )
					.authenticated( )

					//
					// Explorer operations
					.antMatchers( HttpMethod.POST, OsExplorer.EXPLORER_URL + "/**" )
					.access( CsapSecurityRoles.hasAny( CsapSecurityRoles.Access.admin )
							+ " OR "
							+ CsapSecurityRoles.hasAny( CsapSecurityRoles.Access.infra ) )
					//
					// encoding properties
					.antMatchers( HttpMethod.POST, ENCODE_FULL_URL )
					.access( CsapSecurityRoles.hasAny( CsapSecurityRoles.Access.admin )
							+ " OR "
							+ CsapSecurityRoles.hasAny( CsapSecurityRoles.Access.infra ) )

					//
					// notify when non admin
					.antMatchers( HttpMethod.POST, NOTIFY_FULL_URL )
					.access( CsapSecurityRoles.hasAny( CsapSecurityRoles.Access.admin ) )

					//
					// Updating Application Definition
					.antMatchers( HttpMethod.POST, DEFINITION_URL + "/**" )
					.access( CsapSecurityRoles.hasAny( CsapSecurityRoles.Access.infra ) )

					//
					// Agent operations protected at app level using host membership
					//
					.antMatchers(
							CsapCoreService.SERVICE_URL + "/query/**",
							OS_URL + HostRequests.HOST_INFO_URL,
							OS_URL + HostRequests.DEFINITION_ZIP_URL,
							OS_URL + HostRequests.JOIN_URL,
							OS_URL + HostRequests.MASTERS_READY_URL,
							OS_URL + HostRequests.FOLDER_ZIP_URL )
					.permitAll( ) // Disable security on public assets

					//
					//
					// VM actions OS_URL + "/command", EDIT_URL + "/**",
					.antMatchers(
							FILE_MANAGER_URL,
							FILE_URL + FileRequests.EDIT_URL,
							FILE_URL + FileRequests.SAVE_URL,
							FILE_URL + FileRequests.FILE_SYSTEM_URL,
							OS_URL + "/syncFiles",
							OS_URL + "/hostAdmin",
							OS_URL + "/delete",
							OS_URL + "/checkFsThroughput",
							OS_URL + "/killPid",
							OS_URL + HostRequests.EXECUTE_URL )
					.access( CsapSecurityRoles.hasAny( CsapSecurityRoles.Access.admin ) )

					// anything else
					.anyRequest( )
					.access( CsapSecurityRoles.hasAny( CsapSecurityRoles.Access.view )
							+ " OR "
							+ CsapSecurityRoles.hasAny( CsapSecurityRoles.Access.admin ) ) ;

		} ) ;

		// @formatter:on
		return mySecurity ;

	}

	@Bean
	public CsapRestTemplateFactory csapRestFactory ( ) {
		
		return new CsapRestTemplateFactory( getSslCertificateUrl( ) , getSslCertificatePass( ) ) ;

	}

	// UI sessions are associated with these calls
	@Bean ( name = "adminConnection" )
	public RestTemplate getAdminConnection ( CsapRestTemplateFactory factory ) {

		// 1 1 for testing, 20, 20
		return factory.buildDefaultTemplate( "csap-admin", isDisableSslValidation( ), 10, 10, 2, 4, 300 ) ;

	}

	// UI sessions are associated with these calls
	@Bean ( name = "analyticsRest" )
	public RestTemplate getCsapAnalyticsService ( CsapRestTemplateFactory factory ) {

		// 1 1 for testing, 20, 20
		return factory.buildDefaultTemplate( "csap-analytics", isDisableSslValidation( ), 10, 10, 5, 60, 300 ) ;

	}

	// These are background UI initiated
	@Bean ( name = "trendRestTemplate" )
	public RestTemplate getTrendAnalyticsService ( CsapRestTemplateFactory factory ) {

		return factory.buildDefaultTemplate( "csap-event-trends", isDisableSslValidation( ), 10, 10, 5, 60, 300 ) ;

	}

	private boolean disableSslValidation = false ;

	public void setDisableSslValidation ( boolean disableSslValidation ) {

		this.disableSslValidation = disableSslValidation ;

	}

	public boolean isDisableSslValidation ( ) {

		return disableSslValidation ;

	}

	public String getDefinitionFolder ( ) {

		return definitionFolder ;

	}

	public void setDefinitionFolder ( String definitionFolder ) {

		this.definitionFolder = definitionFolder ;

	}

	//
	@Bean ( name = "csapEventsService" )
	public RestTemplate csapEventsService ( CsapRestTemplateFactory factory ) {

		RestTemplate restTemplate = factory.buildDefaultTemplate( "csapEvents", false, 10, 10, 5, 5, 300 ) ;

		restTemplate.getMessageConverters( ).clear( ) ;
		restTemplate.getMessageConverters( ).add( new FormHttpMessageConverter( ) ) ;
		restTemplate.getMessageConverters( ).add( new StringHttpMessageConverter( ) ) ;
		restTemplate.getMessageConverters( ).add( new MappingJackson2HttpMessageConverter( ) ) ;

		return restTemplate ;

	}

	//
	@Bean ( name = "apiTester" )
	public RestTemplate getApiTemplate ( CsapRestTemplateFactory factory ) {

		return factory.buildDefaultTemplate( "ApiTester", 1, 1, 10, 10, 10 ) ;

	}

	@Pointcut ( "within(org.csap.agent.model.Application)" )
	private void csapModelPC ( ) {

	}



	@Pointcut ( "within(org.csap.agent.integrations..*)" )
	private void integrationsPC ( ) {

	}

	@Around ( "integrationsPC()" )
	public Object integrationTimers ( ProceedingJoinPoint pjp )
		throws Throwable {

		return meterUtilities.timedExecution( pjp, "xtimer-remote." ) ;

	}

	@Pointcut ( "within(org.csap.agent.api..*)" )
	private void apiPC ( ) {

	}

	@Around ( "apiPC()" )
	public Object apiTimers ( ProceedingJoinPoint pjp )
		throws Throwable {

		return meterUtilities.timedExecution( pjp, "xtimer-api." ) ;

	}

	@Pointcut ( "within(org.csap.agent.ui..*)" )
	private void uiPC ( ) {

	}

	@Around ( "uiPC()" )
	public Object uiTimers ( ProceedingJoinPoint pjp )
		throws Throwable {

		return meterUtilities.timedExecution( pjp, "xtimer-ui." ) ;

	}

	@Pointcut ( "within(org.csap.agent.linux..*)" )
	private void linuxPC ( ) {

	}

	@Pointcut ( "within(org.csap.agent.linux.ServiceJobRunner)" )
	private void isServiceJobRunner ( ) {

	}

	@Around ( "linuxPC() && !isServiceJobRunner()" )
	public Object linuxAdvice ( ProceedingJoinPoint pjp )
		throws Throwable {

		return meterUtilities.timedExecution( pjp, "xtimer-linux." ) ;

	}


	@Around ( "within(org.csap.agent.services..*)  && !linuxPC()  && !csapModelPC() " )
	public Object servicesSimonAdvice ( ProceedingJoinPoint pjp )
		throws Throwable {

		return meterUtilities.timedExecution( pjp, "xtimer-services." ) ;

	}

	// Use for admin Health summary dashboard
	public AlertSettings getAlerts ( ) {

		return alerts ;

	}

	public void setAlerts ( AlertSettings alerts ) {

		this.alerts = alerts ;

	}

	private AlertSettings alerts = new AlertSettings( ) ;

	public List<String> getDockerUiDefaultImages ( ) {

		return dockerUiDefaultImages ;

	}

	public void setDockerUiDefaultImages ( List<String> dockerImages ) {

		this.dockerUiDefaultImages = dockerImages ;

	}

	public String getHostUrlPattern ( ) {

		return hostUrlPattern ;

	}

	public void setHostUrlPattern ( String hostUrlPattern ) {

		//
		this.hostUrlPattern = hostUrlPattern ;

	}

	public String getHostUrlPatternInternal ( ) {

		return hostUrlPatternInternal ;

	}

	public void setHostUrlPatternInternal ( String hostUrlPatternInternal ) {

		this.hostUrlPatternInternal = hostUrlPatternInternal ;

	}

	public boolean isSvnEnabled ( ) {

		return svnEnabled ;

	}

	public void setSvnEnabled ( boolean svnSupport ) {

		this.svnEnabled = svnSupport ;

	}

	public boolean isJmxAuthentication ( ) {

		return jmxAuthentication ;

	}

	public void setJmxAuthentication ( boolean jmxEnabled ) {

		this.jmxAuthentication = jmxEnabled ;

	}

	public String getJmxUser ( ) {

		return jmxUser ;

	}

	public void setJmxUser ( String jmxUser ) {

		this.jmxUser = jmxUser ;

	}

	public String getJmxPass ( ) {

		return jmxPass ;

	}

	public void setJmxPass ( String jmxPass ) {

		this.jmxPass = jmxPass ;

	}

	public String getScreenCastServerUrl ( ) {

		return screenCastServerUrl ;

	}

	public void setScreenCastServerUrl ( String screenCastServerUrl ) {

		this.screenCastServerUrl = screenCastServerUrl ;

	}

	public String getMinVersionCsap ( ) {

		return minVersionCsap ;

	}

	public void setMinVersionCsap ( String minVersionCsap ) {

		this.minVersionCsap = minVersionCsap ;

	}

	public String getMinVersionDocker ( ) {

		return minVersionDocker ;

	}

	public void setMinVersionDocker ( String minVersionDocker ) {

		this.minVersionDocker = minVersionDocker ;

	}

	public String getMinVersionKubelet ( ) {

		return minVersionKubelet ;

	}

	public void setMinVersionKubelet ( String minVersionKubelet ) {

		this.minVersionKubelet = minVersionKubelet ;

	}

	// legacy
	public String getInstallationFolder ( ) {

		return installationFolder ;

	}

	public String getWorkingFolder ( ) {

		return getInstallationFolder( ) + "/working" ;

	}

	public void setInstallationFolder ( String csapFolder ) {

		this.installationFolder = csapFolder ;

	}

	public int getAgentPort ( ) {

		return agentPort ;

	}

	public void setAgentPort ( int agentPort ) {

		// logger.info( "agentPort: {}", agentPort );
		this.agentPort = agentPort ;

	}

	public String getAgentContextPath ( ) {

		return agentContextPath ;

	}

	public void setAgentContextPath ( String agentContext ) {

		this.agentContextPath = agentContext ;

	}

	public boolean isDefinitionStrictMode ( ) {

		return definitionStrictMode ;

	}

	public void setDefinitionStrictMode ( boolean definitionStrictMode ) {

		this.definitionStrictMode = definitionStrictMode ;

	}

	public String getHelpUrl ( String key ) {

		return helpUrls.get( key ) ;

	}

	public Map<String, String> getHelpUrls ( ) {

		return helpUrls ;

	}

	public void setHelpUrls ( Map<String, String> helpUrls ) {

		this.helpUrls = helpUrls ;

	}

	public List<String> getSslForceHosts ( ) {
	
		return sslForceHosts ;
	
	}

	public void setSslForceHosts ( List<String> sslHosts ) {
		
		logger.info( CsapApplication.testHeader( "{}" ), sslHosts);
	
		this.sslForceHosts = sslHosts ;
	
	}


	public String getSslCertificatePass ( ) {
	
		if ( csapWebServer != null
				&& csapWebServer.isSsl_and_client_and_self_signed( ) ) {
			
			return csapWebServer.getSettings( ).getSsl( ).getKeystorePassword( ) ;
			
		}
	
		return null ;
	
	}




	public URL getSslCertificateUrl ( ) {
		
		if (  csapWebServer != null
				&& csapWebServer.isSsl_and_client_and_self_signed( ) ) {
			
			try {

				return new URL(csapWebServer.getSettings( ).getSsl( ).getKeystoreFile( )) ;

			} catch ( Exception e ) {

				logger.info( "Failed creating url for {} {}", csapWebServer.getSettings( ).getSsl( ), CSAP.buildCsapStack( e ) );

			}
		}
	
		return null ;
	
	}

	public CsapWebServerConfig getCsapWebServer ( ) {
	
		return csapWebServer ;
	
	}

	public void setCsapWebServer ( CsapWebServerConfig csapWebServer ) {
	
		this.csapWebServer = csapWebServer ;
	
	}

}
