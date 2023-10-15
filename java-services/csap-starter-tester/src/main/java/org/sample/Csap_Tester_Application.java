package org.sample ;

import java.util.ArrayList ;
import java.util.List ;

import javax.inject.Inject ;
import javax.ws.rs.ApplicationPath ;

import org.apache.commons.dbcp2.BasicDataSource ;
import org.aspectj.lang.ProceedingJoinPoint ;
import org.aspectj.lang.annotation.Around ;
import org.aspectj.lang.annotation.Aspect ;
import org.aspectj.lang.annotation.Pointcut ;
import org.csap.CsapBootApplication ;
import org.csap.helpers.CSAP ;
import org.csap.helpers.CsapApplication ;
import org.csap.helpers.CsapMvc ;
import org.csap.helpers.CsapRestTemplateFactory ;
import org.csap.integations.CsapEncryptionConfiguration ;
import org.csap.integations.CsapSecurityConfiguration ;
import org.csap.integations.micrometer.CsapMeterUtilities ;
import org.csap.security.config.CsapSecurityRoles ;
import org.glassfish.jersey.server.ResourceConfig ;
import org.sample.input.http.jersey.HelloJaxrsResource ;
import org.sample.input.http.jersey.JerseyEventListener ;
import org.sample.input.http.jersey.JerseyExceptionProvider ;
import org.sample.input.http.jersey.JerseyResource ;
import org.sample.input.http.jersey.SimpleConverter ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.beans.factory.annotation.Autowired ;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty ;
import org.springframework.boot.context.properties.ConfigurationProperties ;
import org.springframework.boot.context.properties.EnableConfigurationProperties ;
import org.springframework.context.annotation.Bean ;
import org.springframework.context.annotation.Configuration ;
import org.springframework.core.env.Environment ;
import org.springframework.core.env.Profiles ;
import org.springframework.core.task.TaskExecutor ;
import org.springframework.http.converter.HttpMessageConverter ;
import org.springframework.scheduling.TaskScheduler ;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor ;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler ;
import org.springframework.security.config.annotation.web.builders.HttpSecurity ;
import org.springframework.web.client.RestTemplate ;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry ;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer ;

@CsapBootApplication
@ConfigurationProperties ( prefix = "my-service-configuration" )
@EnableConfigurationProperties ( DatabaseSettings.class )
//@EntityScan(basePackageClasses=DemoManager.class)
@Aspect
public class Csap_Tester_Application implements WebMvcConfigurer {

	final static Logger logger = LoggerFactory.getLogger( Csap_Tester_Application.class ) ;

//	@Bean
//	//@Order ( Ordered.HIGHEST_PRECEDENCE )
//	public MyBeanPostProcessor myBeanPostProcessor ( ) {
//
//		return new MyBeanPostProcessor( ) ;
//
//	}

//	static class MyBeanPostProcessor implements BeanPostProcessor {
//
//		@Override
//		public Object postProcessBeforeInitialization ( Object bean , String beanName )
//			throws BeansException {
//
//			System.out.println( "--- before:     " + bean.getClass( ).getName( ) ) ;
//
//			return bean ;
//
//		}
//
//		@Override
//		public Object postProcessAfterInitialization ( Object bean , String beanName )
//			throws BeansException {
//
//			System.out.println( "--- after:     " + bean.getClass( ).getName( ) ) ;
//
//			return bean ;
//
//		}
//
//	}

	@Inject
	DatabaseSettings databaseSettings ;

	public DatabaseSettings getDb ( ) {

		return databaseSettings ;

	}

//	@Autowired
	CsapMeterUtilities metricUtilities ;

	public Csap_Tester_Application ( CsapMeterUtilities metricUtilities ) {

		logger.warn( CsapApplication.testHeader( ) ) ;
		this.metricUtilities = metricUtilities ;
		metricUtilities.addCsapCollectionTag( "x-dbcp.*connection" ) ;

	}

	// triggers cyclic dependency
//	@Bean
//	public MeterFilter add_csap_collection_tags ( CsapMeterUtilities helperToAvoidCyclicInjection ) {
//
//		return helperToAvoidCyclicInjection.addCsapCollectionTag( "dbcp.BasicDataSource.getConnection" ) ;
//
//	}

	public static void main ( String[] args ) {

		// CSAP sets up shared profiles
		CsapApplication.run( Csap_Tester_Application.class, args ) ;

		// SpringApplication.run( Csap_Tester_Application.class, args );
	}

	/**
	 * Statics used through out app
	 */
	public final static String SIMPLE_CACHE_EXAMPLE = "sampleCacheWithNoExpirations" ;
	public final static String TIMEOUT_CACHE_EXAMPLE = "sampleCacheWith10SecondEviction" ;
	public final static String JMS_REPORT_CACHE = "jmsReportCache" ;
	public final static String BASE_URL = "/" ;
	public final static String SPRINGAPP_URL = BASE_URL + "spring-app" ;
	public final static String SPRINGREST_URL = BASE_URL + "spring-rest" ;

	public final static String DB_MONITOR_URL = BASE_URL + "csap/metrics/db" ;
	public final static String LARGE_PARAM_URL = "/largePayload" ;
	public final static String API_URL = BASE_URL + "api" ;
	public final static String JERSEY_URL = BASE_URL + "jersey" ;

	public static boolean isRunningOnDesktop ( ) {

		if ( ! CsapApplication.isCsapFolderSet( ) ) {

			return true ;

		}

		return false ;

	}

	int ONE_YEAR_SECONDS = 60 * 60 * 24 * 365 ;
	final String VERSION = "1.1" ;


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

	@Autowired
	private Environment springEnv ;

	// Default post limit is 2MB
	// @Bean
	// EmbeddedServletContainerCustomizer containerCustomizer() throws Exception
	// {
	// return (ConfigurableEmbeddedServletContainer container) -> {
	// if (container instanceof TomcatEmbeddedServletContainerFactory) {
	// TomcatEmbeddedServletContainerFactory tomcat =
	// (TomcatEmbeddedServletContainerFactory) container;
	// tomcat.addConnectorCustomizers(
	// (connector) -> {
	// connector.setMaxPostSize(4*1024*1024); // 4 MB
	// }
	// );
	// }
	// };
	// }

	/**
	 * 
	 * Component specific security rules
	 * 
	 * @return
	 */
	@Bean
	@ConditionalOnProperty ( "csap.security.enabled" )
	public CsapSecurityConfiguration.CustomHttpSecurity mySecurityPolicy ( ) {

		// http://blog.netgloo.com/2014/09/28/spring-boot-enable-the-csrf-check-selectively-only-for-some-requests/
//		CsapSecurityConfiguration.CustomHttpSecurity mySecurity = ( httpSecurity -> {
//
//		} ) ;

		// return mySecurity ;

		var bootActuatorContext = springEnv.getProperty( "management.endpoints.web.base-path" ) ;

		return new MyCustomSecurityPolicy( bootActuatorContext ) ;

	}

	public static class MyCustomSecurityPolicy implements CsapSecurityConfiguration.CustomHttpSecurity {

		String bootActuatorContext ;

		public MyCustomSecurityPolicy ( String bootActuatorContext ) {

			this.bootActuatorContext = bootActuatorContext ;

		}

		@Override
		public void configure ( HttpSecurity httpSecurity ) throws Exception {

			httpSecurity
					.authorizeRequests( )

					// Public assets
					.antMatchers( bootActuatorContext + "/prometheus" )
					.permitAll( )

					// Public assets
					.antMatchers( "/webjars/**", "/noAuth/**", "/js/**", "/css/**", "/images/**" )
					.permitAll( )

					// jersey api uses caching filter
					.antMatchers( SPRINGREST_URL + LARGE_PARAM_URL + "/**" )
					.permitAll( )

					// jersey api uses caching filter
					.antMatchers( "/jersey/**" )
					.permitAll( )

					// db stats are public
					.antMatchers( "/spring-rest/db/statistics" )
					.permitAll( )

					// simple method to ensure ACL page is displayed
					.antMatchers( "/testAclFailure" )
					.hasRole( "NonExistGroupToTriggerAuthFailure" )

					// authorize using CSAP admin group - this app includes destructive
					.anyRequest( )
					.access( CsapSecurityRoles.hasAny( CsapSecurityRoles.Access.admin ) ) ;

		}

	}

	// configure @Scheduled thread pool;
	@Bean
	public TaskScheduler taskScheduler ( ) {

		ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler( ) ;
		scheduler.setThreadNamePrefix( Csap_Tester_Application.class.getSimpleName( ) + "@Scheduler" ) ;
		scheduler.setPoolSize( 1 ) ;
		return scheduler ;

	}

	// configure @Async thread pool. Use named pools for workload segregation:
	// @Async("CsapAsync")

	final public static String ASYNC_EXECUTOR = "CsapAsynExecutor" ;

	@Bean ( ASYNC_EXECUTOR )
	public TaskExecutor taskExecutor ( ) {

		ThreadPoolTaskExecutor taskExecutor = new ThreadPoolTaskExecutor( ) ;
		taskExecutor.setThreadNamePrefix( Csap_Tester_Application.class.getSimpleName( ) + "@Async" ) ;
		taskExecutor.setMaxPoolSize( 5 ) ;
		taskExecutor.setQueueCapacity( 100 ) ;
		taskExecutor.afterPropertiesSet( ) ;
		return taskExecutor ;

	}

	/**
	 * 
	 * ============== Rest Templates use for demos
	 * 
	 */
	@Bean ( name = "aTrivialRestSampleId" )
	public RestTemplate getRestTemplate ( ) {

		RestTemplate restTemplate = new RestTemplate( ) ;
		List<HttpMessageConverter<?>> messageConverters = new ArrayList<HttpMessageConverter<?>>( ) ;
		messageConverters.add( new SimpleConverter( ) ) ;
		restTemplate.setMessageConverters( messageConverters ) ;
		return restTemplate ;

	}

	@Bean
	public CsapRestTemplateFactory csapRestFactory ( ) {

		return new CsapRestTemplateFactory( null, null ) ;

	}

	@Bean ( name = "csAgentRestTemplate" )
	public RestTemplate csAgentRestTemplate ( CsapRestTemplateFactory factory ) {

		return factory.buildJsonTemplate( "agentApi", 6, 10, 3, 1, 10 ) ;

	}

	@Bean ( name = "jmsQueueQueryTemplate" )
	public RestTemplate jmsQueueQueryTemplate ( CsapRestTemplateFactory factory ) {

		return factory.buildJsonTemplate( "JmsProcessingMonitor", 1, 40, 3, 1, 60 ) ;

	}

	/**
	 * 
	 * =================== Jersey integration
	 */
	@Configuration
	@ApplicationPath ( "/jersey" )
	public static class JerseyConfig extends ResourceConfig {
		public JerseyConfig ( ) {

			// register( new JerseyEventListener( ) ) ;
//			logger.warn( "\n\n =============== Spring Jersey Initialization ============\n"
//					+ "base url: /jersey\t\t Packages Scanned: org.sample.input.http.jersey"
//					+ "jersey debug will appear if you set debug on JerseyEventListener"
//					+ "\n\n" );

			// BUG in jersey prevents resource scanning when running from spring boot jar
			// workaround:
			// https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-1.4-Release-Notes#jersey-classpath-scanning-limitations
			// workaround - CSAP run off extracted jars - so do eclipse and IDES - so this
			// can be ignored.
			//
			// packages( "org.sample.input.http.jersey" );

			// alternately - load explicitly - for use with executable jars and skipping
			// need to extract into tmp.
			logger.warn( "Using jersey explicit registration" ) ;
			register( HelloJaxrsResource.class ) ;
			register( JerseyEventListener.class ) ;
			register( JerseyExceptionProvider.class ) ;
			register( JerseyResource.class ) ;

		}
	}

	// Use CsapPropertyEncrypter for decrypt support
	// @Value("${db.password}")
	// private String password;

	@Inject
	CsapEncryptionConfiguration csapEncrypt ;

	@Bean ( destroyMethod = "close" )
	public BasicDataSource helloDataSource ( ) {

		BasicDataSource helloDataSource = new BasicDataSource( ) ;
		helloDataSource.setDriverClassName( getDb( ).getDriverClassName( ) ) ;
		helloDataSource.setUrl( getDb( ).getUrl( ) ) ;
		helloDataSource.setUsername( getDb( ).getUsername( ) ) ;

		helloDataSource.setPassword(
				csapEncrypt.decodeIfPossible(
						getDb( ).getPassword( ),
						logger ) ) ;

		// helloDataSource.setMaxWait(500);
		helloDataSource.setMaxWaitMillis( 500 ) ;
		helloDataSource.setTestWhileIdle( true ) ;
		helloDataSource.setMinEvictableIdleTimeMillis( getDb( ).getIdleEvictionMs( ) ) ;
		helloDataSource.setTimeBetweenEvictionRunsMillis( getDb( ).getIdleEvictionMs( ) ) ;
		helloDataSource.setMaxIdle( getDb( ).getMaxIdle( ) ) ;
		helloDataSource.setMaxTotal( getDb( ).getMaxActive( ) ) ;

		StringBuilder builder = new StringBuilder( ) ;
		builder.append( "DB Connection Pool Settings" ) ;
		builder.append( CSAP.padLine( "Url" ) + helloDataSource.getUrl( ) ) ;
		builder.append( CSAP.padLine( "getUsername" ) + helloDataSource.getUsername( ) ) ;
		builder.append( CSAP.padLine( "getMaxWait" ) + helloDataSource.getMaxWaitMillis( ) ) ;
		builder.append( CSAP.padLine( "Time before marked idle" ) + helloDataSource.getMinEvictableIdleTimeMillis( ) ) ;
		builder.append( CSAP.padLine( "timeBetweenEvictionRunsMillis" ) + helloDataSource
				.getTimeBetweenEvictionRunsMillis( ) ) ;
		builder.append( CSAP.padLine( "Max Idle Connections" ) + helloDataSource.getMaxIdle( ) ) ;
		builder.append( CSAP.padLine( "Max Total Connections" ) + helloDataSource.getMaxTotal( ) ) ;
		builder.append( CSAP.padLine( "getInitialSize" ) + helloDataSource.getInitialSize( ) ) ;

		logger.warn( builder.toString( ) ) ;

		metricUtilities.addGauge( "csap.db.pool-active", helloDataSource, BasicDataSource::getNumActive ) ;
		metricUtilities.addGauge( "csap.db.pool-idle", helloDataSource, BasicDataSource::getNumIdle ) ;

		return helloDataSource ;

	}

	/**
	 * 
	 * monitoring
	 * 
	 */

	// @Pointcut("within(org.apache.commons.dbcp2.BasicDataSource)")
	@Pointcut ( "execution(* org.apache.commons.dbcp2.BasicDataSource.get*(..))" )
	private void dbcpPC ( ) {

	};

	@Around ( "dbcpPC()" )
	public Object dbcpMicroMeter ( ProceedingJoinPoint pjp )
		throws Throwable {

		return metricUtilities.timedExecution( pjp, "x-dbcp." ) ;

	}

	@Pointcut ( "within(org.sample.jpa.*)" )
	private void jpaPC ( ) {

	};

	@Around ( "jpaPC()" )
	public Object jpaAdvice ( ProceedingJoinPoint pjp )
		throws Throwable {

		return metricUtilities.timedExecution( pjp, "x-db." ) ;

	}

	@Pointcut ( "within(org.sample.input.jms.*)" )
	private void jmsPC ( ) {

	};

	@Around ( "jpaPC()" )
	public Object jmsAdvice ( ProceedingJoinPoint pjp )
		throws Throwable {

		return metricUtilities.timedExecution( pjp, "x-jms." ) ;

	}

	@Pointcut ( "within(org.sample.input.http.*)" )
	private void httpPC ( ) {

	};

	@Around ( "jpaPC()" )
	public Object httpAdvice ( ProceedingJoinPoint pjp )
		throws Throwable {

		return metricUtilities.timedExecution( pjp, "x-http." ) ;

	}

	public HealthSettings getJmsBacklogHealth ( ) {

		return jmsBacklogHealth ;

	}

	public void setJmsBacklogHealth ( HealthSettings health ) {

		this.jmsBacklogHealth = health ;

	}

	private HealthSettings jmsBacklogHealth = new HealthSettings( ) ;

	public class HealthSettings {
		private String baseUrl ;
		private String host ;
		private String backlogQ ;
		private String processedQ ;
		private int sampleCount ;
		private String expression ;

		public String getBaseUrl ( ) {

			return baseUrl ;

		}

		public void setBaseUrl ( String baseUrl ) {

			this.baseUrl = baseUrl ;

		}

		public String getBacklogQ ( ) {

			return backlogQ ;

		}

		public void setBacklogQ ( String backlogQ ) {

			this.backlogQ = backlogQ ;

		}

		public String getProcessedQ ( ) {

			return processedQ ;

		}

		public void setProcessedQ ( String processedQ ) {

			this.processedQ = processedQ ;

		}

		public int getSampleCount ( ) {

			return sampleCount ;

		}

		public void setSampleCount ( int sampleCount ) {

			this.sampleCount = sampleCount ;

		}

		public String getExpression ( ) {

			return expression ;

		}

		public void setExpression ( String expression ) {

			this.expression = expression ;

		}

		public String getHost ( ) {

			return host ;

		}

		public void setHost ( String host ) {

			this.host = host ;

		}

	}

}
