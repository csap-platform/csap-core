package test.scenario_1_container ;

import static org.assertj.core.api.Assertions.assertThat ;
import static org.assertj.core.api.Assertions.assertThatThrownBy ;

import java.time.LocalDateTime ;
import java.time.format.DateTimeFormatter ;
import java.util.Arrays ;

import javax.annotation.PostConstruct ;
import javax.inject.Inject ;

import org.csap.CsapBootApplication ;
import org.csap.helpers.CSAP ;
import org.csap.helpers.CsapApplication ;
import org.csap.integations.CsapEncryptionConfiguration ;
import org.csap.integations.CsapSecurityConfiguration ;
import org.csap.security.config.CsapSecurityRoles ;
import org.junit.jupiter.api.Assumptions ;
import org.junit.jupiter.api.BeforeAll ;
import org.junit.jupiter.api.BeforeEach ;
import org.junit.jupiter.api.Test ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.beans.factory.NoSuchBeanDefinitionException ;
import org.springframework.beans.factory.annotation.Autowired ;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty ;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration ;
import org.springframework.boot.test.context.SpringBootTest ;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment ;
import org.springframework.boot.test.web.client.TestRestTemplate ;
import org.springframework.boot.web.client.RestTemplateBuilder ;
import org.springframework.boot.web.server.LocalServerPort ;
import org.springframework.context.ApplicationContext ;
import org.springframework.context.annotation.Bean ;
import org.springframework.http.HttpStatus ;
import org.springframework.http.ResponseEntity ;
import org.springframework.test.annotation.DirtiesContext ;
import org.springframework.test.context.ActiveProfiles ;
import org.springframework.web.bind.annotation.GetMapping ;
import org.springframework.web.bind.annotation.RestController ;

import com.fasterxml.jackson.databind.ObjectMapper ;

@SpringBootTest ( classes = Csap_Application_With_AD_Security.Simple_AD_App.class , webEnvironment = WebEnvironment.RANDOM_PORT )
@ActiveProfiles ( {
		"test", "localhost", Csap_Application_With_AD_Security.AD_TEST_PROFILE
} )
@DirtiesContext
public class Csap_Application_With_AD_Security {

	@Autowired
	private ApplicationContext applicationContext ;

	@BeforeEach
	public void beforeMethod ( ) {

		// Guard for setup

		Assumptions.assumeTrue( settings.isConfigured( ) ) ;

	}

	private static final String HI_SECURED_URL = "/hiWithADSecurity" ;
	private static final String HI_NO_SECURITY = "/hiNoADSecurity" ;

	final static String AD_TEST_PROFILE = "adTestProfile" ;

	final static private Logger logger = LoggerFactory.getLogger( Csap_Application_With_AD_Security.class ) ;

	@BeforeAll
	// @Before
	static public void setUpBeforeClass ( )
		throws Exception {

		System.out.println( "Starting logging" ) ;

		CsapApplication.initialize( logger.getName( ) ) ;

	}

	@Inject
	public Settings_active_dir settings ;

	@PostConstruct
	public void show_settings ( ) {

		logger.info( CsapApplication.testHeader( "active dir settings {}" ), settings ) ;

		if ( ! settings.isConfigured( ) ) {

			Arrays.asList( Settings_active_dir.SETUP_NOTES )
					.stream( )
					.forEach( System.out::println ) ;

			// System.exit( 99 );
		}

	}

	/**
	 * 
	 * Simple test app that excludes security autoconfiguration
	 *
	 */
	@CsapBootApplication
	public static class Simple_AD_App {

		@RestController
		static public class SimpleHello {

			@GetMapping ( {
					HI_SECURED_URL, HI_NO_SECURITY
			} )
			public String hi ( ) {

				return "Hello" +
						LocalDateTime.now( )
								.format( DateTimeFormatter
										.ofPattern( "HH:mm:ss,   MMMM d  uuuu " ) ) ;

			}

			@Inject
			ObjectMapper jsonMapper ;

		}

		@Bean
		@ConditionalOnProperty ( "csap.security.enabled" )
		public CsapSecurityConfiguration.CustomHttpSecurity mySecurityPolicy ( ) {

			// http://blog.netgloo.com/2014/09/28/spring-boot-enable-the-csrf-check-selectively-only-for-some-requests/

			// @formatter:off
			CsapSecurityConfiguration.CustomHttpSecurity mySecurity = ( httpSecurity -> {

				httpSecurity

						// CSRF adds complexity - refer to
						// https://docs.spring.io/spring-security/site/docs/current/reference/htmlsingle/#csrf
						// csap.security.csrf also needs to be enabled or this will be ignored
						.csrf( )
						.requireCsrfProtectionMatcher( CsapSecurityConfiguration.buildRequestMatcher( "/login*" ) )
						.and( )

						// do not run security against opensource jars: "/webjars/**", "/css/**" ,
						// "/js/**", "/images/**"
						.authorizeRequests( )
						.antMatchers( HI_NO_SECURITY )
						.permitAll( )

						//
						//
						.antMatchers( "/testAclFailure" )
						.hasRole( "NonExistGroupToTriggerAuthFailure" )
						//
						//
						.antMatchers( "/someUrlNeedingAdmin1", "/anotherUrlNeedingAdmin" )
						.access( CsapSecurityRoles.hasAny( CsapSecurityRoles.Access.view )
								+ " OR "
								+ CsapSecurityRoles.hasAny( CsapSecurityRoles.Access.admin ) )
						//
						//
						.anyRequest( )
						.access( CsapSecurityRoles.hasAny( CsapSecurityRoles.Access.view ) ) ;

				logger.info( "Enabling basic auth for testing only" ) ;
				httpSecurity.httpBasic( ) ;

			} ) ;

			// @formatter:on

			return mySecurity ;

		}

	}

	@Test
	public void load_context ( ) {

		logger.info( CsapApplication.testHeader( ) ) ;

		logger.info( "beans loaded: {}", applicationContext.getBeanDefinitionCount( ) ) ;

		assertThat( applicationContext.getBeanDefinitionCount( ) )
				.as( "Spring Bean count" )
				.isGreaterThan( 200 ) ;

		logger.debug( "beans loaded: {}\n\t {}",
				applicationContext.getBeanDefinitionCount( ),
				Arrays.asList( applicationContext.getBeanDefinitionNames( ) ) ) ;

		assertThat( applicationContext.getBean( SecurityAutoConfiguration.class ) )
				.as( "CSAP element present if enabled: CsapInformation" )
				.isNotNull( ) ;

		Csap_Application_No_Security.verify_csap_components_loaded( applicationContext ) ;

		assertThatThrownBy( ( ) -> {

			applicationContext.getBean( Settings_ldap.class ) ;

		} )
				.as( "LDAP should be filtered out" )
				.isInstanceOf( NoSuchBeanDefinitionException.class )
				.hasMessageContaining( "No qualifying bean of type" ) ;
		// Assert.assertFalse( true);

	}

	@LocalServerPort
	private int testPort ;

	@Inject
	RestTemplateBuilder restTemplateBuilder ;

	@Test
	public void http_get_hi_no_secure_from_simple_app ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		String simpleUrl = "http://localhost:" + testPort + HI_NO_SECURITY ;

		logger.info( "hitting url: {}", simpleUrl ) ;
		// mock does much validation.....

		TestRestTemplate restTemplate = new TestRestTemplate( restTemplateBuilder ) ;

		ResponseEntity<String> response = restTemplate.getForEntity( simpleUrl, String.class ) ;

		logger.info( "result:\n" + response ) ;

		assertThat( response.getBody( ) )
				.startsWith( "Hello" ) ;

	}

	@Test
	public void verify_login_page_on_secured_url ( )
		throws Exception {

		String simpleUrl = "http://localhost:" + testPort + HI_SECURED_URL ;

		logger.info( CsapApplication.testHeader( "{}" ), simpleUrl ) ;
		// mock does much validation.....

		TestRestTemplate restTemplate = new TestRestTemplate( restTemplateBuilder ) ;

		ResponseEntity<String> noCredResponse = restTemplate.getForEntity( simpleUrl, String.class ) ;

		logger.info( "missing password response:\n {}", noCredResponse ) ;

		assertThat( noCredResponse.getStatusCode( ) )
				.as( "Simple get on hello method" )
				.isEqualTo( HttpStatus.FOUND ) ;

		assertThat( noCredResponse.getHeaders( ).getFirst( "Location" ) )
				.as( "Simple get on hello method" )
				.endsWith( "/login" ) ;

	}

	@Test
	public void verify_login_success ( )
		throws Exception {

		Assumptions.assumeTrue( ! settings.getUser( ).equals( settings.DEFAULT ) ) ;

		String simpleUrl = "http://localhost:" + testPort + HI_SECURED_URL ;

		logger.info( CsapApplication.testHeader( "{}" ), simpleUrl ) ;
		// mock does much validation.....

		TestRestTemplate restTemplate = new TestRestTemplate( restTemplateBuilder ) ;

		ResponseEntity<String> noCredResponse = restTemplate.getForEntity( simpleUrl, String.class ) ;

		logger.info( "missing password response:\n {}", noCredResponse ) ;

		assertThat( noCredResponse.getStatusCode( ) )
				.as( "Simple get on hello method" )
				.isEqualTo( HttpStatus.FOUND ) ;

		assertThat( noCredResponse.getHeaders( ).getFirst( "Location" ) )
				.as( "Simple get on hello method" )
				.endsWith( "/login" ) ;

		// NOW use secured

		CSAP.setLogToDebug( "org.springframework.security.ldap" ) ;

		var pass = csapEncrypt.decodeIfPossible( settings.getPass( ), logger ) ;
		logger.info( CsapApplication.testHeader( "secured url, user: {}, pass: {}" ), settings.getUser( ), pass ) ;

		TestRestTemplate restTemplateWithAuth = new TestRestTemplate(
				settings.getUser( ),
				pass ) ;

		ResponseEntity<String> responseFromCredQuery = restTemplateWithAuth
				.getForEntity(
						simpleUrl,
						String.class ) ;

		CSAP.setLogToInfo( "org.springframework.security.ldap" ) ;

		logger.info( "responseFromCredQuery:\n {}", responseFromCredQuery ) ;

		// if this fails - verify that the userid and password in ~/.csap/ad-profile is
		// correct
		assertThat( responseFromCredQuery.getStatusCode( ) )
				.as( "Active Directory authentication" )
				.isEqualTo( HttpStatus.OK ) ;

		assertThat( responseFromCredQuery.getBody( ) )
				.startsWith( "Hello" ) ;

	}

	@Inject
	CsapEncryptionConfiguration csapEncrypt ;

}
