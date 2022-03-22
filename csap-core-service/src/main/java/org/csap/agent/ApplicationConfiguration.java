package org.csap.agent ;

import java.lang.reflect.Method ;
import java.net.URL ;
import java.util.Arrays ;
import java.util.List ;
import java.util.Map ;

import org.aspectj.lang.ProceedingJoinPoint ;
import org.aspectj.lang.annotation.Around ;
import org.aspectj.lang.annotation.Aspect ;
import org.aspectj.lang.annotation.Pointcut ;
import org.csap.CsapBootApplication ;
import org.csap.agent.container.ContainerConfiguration ;
import org.csap.agent.container.kubernetes.KubernetesConfiguration ;
import org.csap.agent.ui.explorer.CrioExplorer ;
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
import org.csap.helpers.CsapMvc ;
import org.csap.helpers.CsapRestTemplateFactory ;
import org.csap.integations.CsapEncryptionConfiguration ;
import org.csap.integations.CsapInformation ;
import org.csap.integations.CsapSecurityConfiguration ;
import org.csap.integations.CsapWebServerConfig ;
import org.csap.integations.micrometer.CsapMeterUtilities ;
import org.csap.security.config.CsapSecurityRoles ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.beans.factory.InitializingBean ;
import org.springframework.beans.factory.annotation.Autowired ;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty ;
import org.springframework.boot.context.properties.ConfigurationProperties ;
import org.springframework.cache.interceptor.KeyGenerator ;
import org.springframework.context.annotation.Bean ;
import org.springframework.context.annotation.Configuration ;
import org.springframework.context.annotation.Import ;
import org.springframework.core.env.Environment ;
import org.springframework.core.env.Profiles ;
import org.springframework.core.task.TaskExecutor ;
import org.springframework.http.HttpMethod ;
import org.springframework.http.converter.FormHttpMessageConverter ;
import org.springframework.http.converter.StringHttpMessageConverter ;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter ;
import org.springframework.scheduling.TaskScheduler ;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor ;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler ;
import org.springframework.security.config.annotation.web.builders.HttpSecurity ;
import org.springframework.stereotype.Component ;
import org.springframework.web.client.RestTemplate ;
import org.springframework.web.servlet.config.annotation.CorsRegistry ;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry ;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer ;

@CsapBootApplication
@ConfigurationProperties ( prefix = "csap-core" )

@Import ( {
		ContainerConfiguration.class,
		KubernetesConfiguration.class
} )

@Aspect
//
// Note: @applicationConfiguration is referenced in *.html
//
@Configuration ( "applicationConfiguration" )
public class ApplicationConfiguration implements WebMvcConfigurer {

	final Logger logger = LoggerFactory.getLogger( getClass( ) ) ;

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
	// ssl settings: map to CsapRestTemplate
	//
	private List<String> sslForceHosts ;

	private List<String> dockerUiDefaultImages ;

	private Map<String, String> helpUrls ;

	private Map<String, String> sourceOverrides ;

	@Autowired
	CsapInformation csapInformation ;

	@Autowired
	CsapMeterUtilities meterUtilities ;

	CsapWebServerConfig csapWebServer ;
	CsapEncryptionConfiguration csapEncrypt ;

	public ApplicationConfiguration ( CsapWebServerConfig csapWebServer, CsapEncryptionConfiguration csapEncrypt ) {

		this.csapWebServer = csapWebServer ;
		this.csapEncrypt = csapEncrypt ;

	}

	@Component
	public class DebugLiveServerChanges implements InitializingBean {

		@Override
		public void afterPropertiesSet ( ) throws Exception {

			logger.info( "Properties changed" ) ;

		}

	}

	public static boolean isRunningOnDesktop ( ) {

		return ! CsapApplication.isCsapFolderSet( ) ;

	}

	public static void main ( String[] args ) {

		// CSAP sets up shared profiles
		CsapApplication.run( ApplicationConfiguration.class, args ) ;

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
		scheduler.setThreadNamePrefix( ApplicationConfiguration.class.getSimpleName( ) + "Scheduler" ) ;
		scheduler.setPoolSize( 2 ) ;
		return scheduler ;

	}

	@Bean ( CsapConstants.HEALTH_EXECUTOR )
	public TaskExecutor taskExecutor ( ) {

		ThreadPoolTaskExecutor taskExecutor = new ThreadPoolTaskExecutor( ) ;
		taskExecutor.setThreadNamePrefix( ApplicationConfiguration.class.getSimpleName( ) + "@Async" ) ;
		taskExecutor.setMaxPoolSize( 5 ) ;
		taskExecutor.setQueueCapacity( 300 ) ;
		taskExecutor.afterPropertiesSet( ) ;
		return taskExecutor ;

	}

	@Autowired
	private Environment environment ;

	@Override
	public void addResourceHandlers ( ResourceHandlerRegistry registry ) {

		var productionCaching = true ;

		if ( environment.acceptsProfiles( Profiles.of( "desktop" ) )
				&& System.getProperty( "testCache" ) == null ) {

			productionCaching = false ;
			logger.warn( CsapApplication.testHeader( "productionCaching set to {}" ), productionCaching ) ;

		}

		logger.info( "Web caching enabled" ) ;
		// add common cache policies
		CsapMvc.addResourceHandlers( registry, productionCaching ) ;

	}

	@Bean
	@ConditionalOnProperty ( "csap.security.enabled" )
	public CsapSecurityConfiguration.CustomHttpSecurity mySecurityPolicy ( ) {

		return new MyCustomSecurityPolicy( ) ;

	}

	public static class MyCustomSecurityPolicy implements CsapSecurityConfiguration.CustomHttpSecurity {

		@Override
		public void configure ( HttpSecurity httpSecurity ) throws Exception {

			httpSecurity

					// CSRF adds complexity - refer to
					// https://docs.spring.io/spring-security/site/docs/current/reference/htmlsingle/#csrf
					// csap.security.csrf also needs to be enabled or this will be ignored
					.csrf( )
					.requireCsrfProtectionMatcher(
							CsapSecurityConfiguration.buildRequestMatcher(
									"/login*",
									CsapConstants.FILE_URL + FileRequests.SAVE_URL,
									CsapConstants.OS_URL + HostRequests.EXECUTE_URL ) )
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
					.antMatchers( CsapConstants.API_URL + "/**" )
					.permitAll( ) // Disable security on public assets

					//
					// App Browser get requests
					//
					.antMatchers( HttpMethod.GET,

							CsapConstants.FILE_URL + FileRequests.FILE_REMOTE_MONITOR,

							CrioExplorer.CRIO_URL + "/**",

							CsapConstants.APP_BROWSER_URL + ApplicationBrowser.POD_RESOURCE_URL,
							CsapConstants.APP_BROWSER_URL + ApplicationBrowser.POD_LOG_URL,
							CsapConstants.APP_BROWSER_URL + ApplicationBrowser.POD_REPORT_URL,
							CsapConstants.APP_BROWSER_URL + ApplicationBrowser.NODE_REPORT_URL,
							CsapConstants.APP_BROWSER_URL + ApplicationBrowser.REALTIME_REPORT_URL,
							CsapConstants.APP_BROWSER_URL + ApplicationBrowser.HELM_INFO_URL,
							CsapConstants.APP_BROWSER_URL + ApplicationBrowser.VOLUME_REPORT_URL,
							CsapConstants.APP_BROWSER_URL + ApplicationBrowser.CSAP_EVENT_REPORT_URL,
							CsapConstants.APP_BROWSER_URL + ApplicationBrowser.KUBERNETES_EVENT_REPORT_URL,
							KubernetesExplorer.EXPLORER_URL + "/kubernetes/cli/info/**" )
					.permitAll( ) // Disable security on public listing

					//
					// Service Deployment
					//
					.antMatchers( CsapConstants.SERVICE_URL + ServiceRequests.REBUILD_URL )
					.access( CsapSecurityRoles.hasAny( CsapSecurityRoles.Access.build ) )

					//
					// admin actions
					//
					.antMatchers( CsapConstants.SERVICE_URL + "/reImage",
							CsapConstants.SERVICE_URL + ServiceRequests.START_URL,
							CsapConstants.SERVICE_URL + "/stopServer",
							CsapConstants.SERVICE_URL + ServiceRequests.KILL_URL,
							CsapConstants.SERVICE_URL + ServiceRequests.GENERATE_APACHE_MAPPINGS,
							CsapConstants.SERVICE_URL + "/httpd",
							CsapConstants.SERVICE_URL + "/modjk",
							CsapConstants.SERVICE_URL + "/status",
							CsapConstants.SERVICE_URL + "/uploadArtifact",
							CsapConstants.SERVICE_URL + "/jmeter" )
					.access( CsapSecurityRoles.hasAny( CsapSecurityRoles.Access.admin ) )

					//
					// Definition Management
					//
					.antMatchers( HttpMethod.GET, CsapConstants.DEFINITION_URL + "/**" )
					.authenticated( )

					//
					// Explorer operations
					.antMatchers( HttpMethod.POST, OsExplorer.EXPLORER_URL + "/**" )
					.access( CsapSecurityRoles.hasAny( CsapSecurityRoles.Access.admin )
							+ " OR "
							+ CsapSecurityRoles.hasAny( CsapSecurityRoles.Access.infra ) )
					//
					// encoding properties
					.antMatchers( HttpMethod.POST, CsapConstants.ENCODE_FULL_URL )
					.access( CsapSecurityRoles.hasAny( CsapSecurityRoles.Access.admin )
							+ " OR "
							+ CsapSecurityRoles.hasAny( CsapSecurityRoles.Access.infra ) )

					//
					// notify when non admin
					.antMatchers( HttpMethod.POST, CsapConstants.NOTIFY_FULL_URL )
					.access( CsapSecurityRoles.hasAny( CsapSecurityRoles.Access.admin ) )

					//
					// Updating Application Definition
					.antMatchers( HttpMethod.POST, CsapConstants.DEFINITION_URL + "/**" )
					.access( CsapSecurityRoles.hasAny( CsapSecurityRoles.Access.infra ) )

					//
					// Agent operations protected at app level using host membership
					//
					.antMatchers(
							CsapConstants.SERVICE_URL + "/query/**",
							CsapConstants.OS_URL + HostRequests.HOST_INFO_URL,
							CsapConstants.OS_URL + HostRequests.DEFINITION_ZIP_URL,
							CsapConstants.OS_URL + HostRequests.JOIN_URL,
							CsapConstants.OS_URL + HostRequests.MASTERS_READY_URL,
							CsapConstants.OS_URL + HostRequests.FOLDER_ZIP_URL )
					.permitAll( ) // Disable security on public assets

					//
					//
					// VM actions OS_URL + "/command", EDIT_URL + "/**",
					.antMatchers(
							CsapConstants.FILE_MANAGER_URL,
							CsapConstants.FILE_URL + FileRequests.EDIT_URL,
							CsapConstants.FILE_URL + FileRequests.SAVE_URL,
							CsapConstants.FILE_URL + FileRequests.FILE_SYSTEM_URL,
							CsapConstants.OS_URL + "/syncFiles",
							CsapConstants.OS_URL + "/hostAdmin",
							CsapConstants.OS_URL + "/delete",
							CsapConstants.OS_URL + "/checkFsThroughput",
							CsapConstants.OS_URL + "/killPid",
							CsapConstants.OS_URL + HostRequests.EXECUTE_URL )
					.access( CsapSecurityRoles.hasAny( CsapSecurityRoles.Access.admin ) )

					// anything else
					.anyRequest( )
					.access( CsapSecurityRoles.hasAny( CsapSecurityRoles.Access.view )
							+ " OR "
							+ CsapSecurityRoles.hasAny( CsapSecurityRoles.Access.admin ) ) ;

		}

	}

	@Bean
	public CsapRestTemplateFactory csapRestFactory ( ) {

		return new CsapRestTemplateFactory( getSslCertificateUrl( ), getSslCertificatePass( ) ) ;

	}

	// UI sessions are associated with these calls
	@Bean ( name = "adminConnection" )
	public RestTemplate getAdminConnection ( CsapRestTemplateFactory factory ) {

		// 1 1 for testing, 20, 20
		return factory.buildDefaultTemplate( "csap-admin-peer-connections", isDisableSslValidation( ), 10, 10, 2, 4,
				300 ) ;

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

		RestTemplate restTemplate = factory.buildDefaultTemplate( "csap-events-connections", false, 10, 10, 5, 5,
				300 ) ;

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

		logger.info( CsapApplication.testHeader( "{}" ), sslHosts ) ;

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

		if ( csapWebServer != null
				&& csapWebServer.isSsl_and_client_and_self_signed( ) ) {

			try {

				return new URL( csapWebServer.getSettings( ).getSsl( ).getKeystoreFile( ) ) ;

			} catch ( Exception e ) {

				logger.info( "Failed creating url for {} {}", csapWebServer.getSettings( ).getSsl( ), CSAP
						.buildCsapStack( e ) ) ;

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

	public Map<String, String> getSourceOverrides ( ) {

		return sourceOverrides ;

	}

	public void setSourceOverrides ( Map<String, String> sourceOverrides ) {

		logger.debug( "sourceOverrides: {}", sourceOverrides ) ;

		this.sourceOverrides = sourceOverrides ;

	}

}
