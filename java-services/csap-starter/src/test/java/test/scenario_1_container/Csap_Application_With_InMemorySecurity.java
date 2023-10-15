package test.scenario_1_container ;

import static org.assertj.core.api.Assertions.assertThat ;

import java.time.LocalDateTime ;
import java.time.format.DateTimeFormatter ;

import javax.inject.Inject ;

import org.csap.CsapBootApplication ;
import org.csap.helpers.CsapApplication ;
import org.csap.integations.CsapSecurityConfiguration ;
import org.csap.security.config.CsapSecurityRoles ;
import org.csap.security.config.CsapSecuritySettings ;
import org.junit.jupiter.api.Test ;
import org.junit.jupiter.api.TestInstance ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
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

@SpringBootTest ( //
		classes = Csap_Application_With_InMemorySecurity.Simple_In_Memory_App.class , //
		webEnvironment = WebEnvironment.RANDOM_PORT )

@ActiveProfiles ( {
		"test",
		"in-memory"
} )

@DirtiesContext
@TestInstance ( TestInstance.Lifecycle.PER_CLASS )
public class Csap_Application_With_InMemorySecurity {

	final static private Logger logger = LoggerFactory.getLogger( Csap_Application_With_InMemorySecurity.class ) ;

	static {

		CsapApplication.initialize( "Test Setup Complete" ) ;

	}

	@Autowired
	private ApplicationContext applicationContext ;

	@Inject
	CsapSecuritySettings securitySettings ;

	private static final String HI_SECURED_URL = "/hiWithSecurity" ;
	private static final String HI_NO_SECURITY = "/hiNoSecurity" ;

	/**
	 * 
	 * Simple test app that excludes security autoconfiguration
	 *
	 */
	@CsapBootApplication
//	( scanBasePackages = {
//			"org.none"
//	} )
	public static class Simple_In_Memory_App {

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

		logger.info( CsapApplication.TC_HEAD ) ;

		logger.info( "beans loaded: {}", applicationContext.getBeanDefinitionCount( ) ) ;

		assertThat( applicationContext.getBeanDefinitionCount( ) )
				.as( "Spring Bean count" )
				.isGreaterThan( 200 ) ;

		assertThat( applicationContext.getBean( SecurityAutoConfiguration.class ) )
				.as( "CSAP element present if enabled: CsapInformation" )
				.isNotNull( ) ;

		Csap_Application_No_Security.verify_csap_components_loaded( applicationContext ) ;

		// Assert.assertFalse( true);

	}

	@LocalServerPort
	private int testPort ;

	@Inject
	RestTemplateBuilder restTemplateBuilder ;

	@Test
	public void http_get_hi_no_secure_from_simple_app ( )
		throws Exception {

		String simpleUrl = "http://localhost:" + testPort + HI_NO_SECURITY ;

		logger.info( CsapApplication.TC_HEAD + "hitting url: {}", simpleUrl ) ;
		// mock does much validation.....

		TestRestTemplate restTemplate = new TestRestTemplate( restTemplateBuilder ) ;

		ResponseEntity<String> response = restTemplate.getForEntity( simpleUrl, String.class ) ;

		logger.info( "result:\n" + response ) ;

		assertThat( response.getBody( ) )
				.startsWith( "Hello" ) ;

	}

	@Test
	public void http_get_hi_secure_from_simple_app ( )
		throws Exception {

		String simpleUrl = "http://localhost:" + testPort + HI_SECURED_URL ;

		logger.info( CsapApplication.TC_HEAD + "hitting url: {}", simpleUrl ) ;
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

		assertThat( securitySettings.getProvider( ).getMemoryUsers( ) ).contains(
				"junitadmin,adminpass,AUTHENTICATED,dummy1,dummy2" ) ;

		// NOW use secured with plaintext password
		TestRestTemplate restTemplateWithAuth = new TestRestTemplate( "junitadmin", "adminpass" ) ;
		ResponseEntity<String> responseFromCredQuery = restTemplateWithAuth.getForEntity( simpleUrl, String.class ) ;

		logger.info( "responseFromCredQuery:\n {}", responseFromCredQuery ) ;

		assertThat( responseFromCredQuery.getStatusCode( ) )
				.as( "Simple get on hello method" )
				.isEqualTo( HttpStatus.OK ) ;

		assertThat( responseFromCredQuery.getBody( ) )
				.startsWith( "Hello" ) ;

		// NOW use secured with encrypted password in file
		TestRestTemplate restForEncyrptedPass = new TestRestTemplate( "junituser", "userpass" ) ;
		ResponseEntity<String> encryptedResponse = restForEncyrptedPass.getForEntity( simpleUrl, String.class ) ;

		logger.info( "encryptedResponse:\n {}", encryptedResponse ) ;

		assertThat( encryptedResponse.getStatusCode( ) )
				.isEqualTo( HttpStatus.OK ) ;

	}

}
