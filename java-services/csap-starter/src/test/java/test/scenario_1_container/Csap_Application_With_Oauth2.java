package test.scenario_1_container ;

import static org.assertj.core.api.Assertions.assertThat ;

import java.time.LocalDateTime ;
import java.time.format.DateTimeFormatter ;
import java.util.Arrays ;

import javax.inject.Inject ;

import org.csap.CsapBootApplication ;
import org.csap.helpers.CSAP ;
import org.csap.helpers.CsapApplication ;
import org.csap.integations.CsapSecurityConfiguration ;
import org.csap.security.CsapSecurityController ;
import org.csap.security.config.CsapSecurityRoles ;
import org.csap.security.config.CsapSecuritySettings ;
import org.csap.security.oath2.CsapOauth2SecurityConfiguration ;
import org.csap.security.oath2.CsapWebClients ;
import org.junit.jupiter.api.Assumptions ;
import org.junit.jupiter.api.BeforeEach ;
import org.junit.jupiter.api.Disabled ;
import org.junit.jupiter.api.DisplayName ;
import org.junit.jupiter.api.Test ;
import org.junit.jupiter.api.TestInstance ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.beans.factory.annotation.Autowired ;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty ;
import org.springframework.boot.autoconfigure.security.oauth2.client.OAuth2ClientProperties ;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration ;
import org.springframework.boot.test.context.SpringBootTest ;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment ;
import org.springframework.boot.test.web.client.TestRestTemplate ;
import org.springframework.boot.web.client.RestTemplateBuilder ;
import org.springframework.boot.web.server.LocalServerPort ;
import org.springframework.context.ApplicationContext ;
import org.springframework.context.annotation.Bean ;
import org.springframework.http.ResponseEntity ;
import org.springframework.security.core.Authentication ;
import org.springframework.security.core.context.SecurityContextHolder ;
import org.springframework.test.annotation.DirtiesContext ;
import org.springframework.test.context.ActiveProfiles ;
import org.springframework.web.bind.annotation.GetMapping ;
import org.springframework.web.bind.annotation.RestController ;

import com.fasterxml.jackson.databind.JsonNode ;
import com.fasterxml.jackson.databind.ObjectMapper ;
import com.fasterxml.jackson.databind.node.ObjectNode ;

@SpringBootTest ( //
		classes = Csap_Application_With_Oauth2.Simple_Oath2_App.class , //
		webEnvironment = WebEnvironment.RANDOM_PORT )

@ActiveProfiles ( {
		"test", "oauth2-test"
} )
@DirtiesContext

@TestInstance ( TestInstance.Lifecycle.PER_CLASS )
@DisplayName ( "CSAP Application: OAUTH2 Enabled" )
@Disabled
class Csap_Application_With_Oauth2 {

	Logger logger = LoggerFactory.getLogger( getClass( ) ) ;

	static {

		CsapApplication.initialize( "Test Setup Complete" ) ;

	}

	// final static String PROFILE_NAME = "localhost,oauth2-test" ;

	@Autowired
	private ApplicationContext applicationContext ;

	@LocalServerPort
	private int testPort ;

	@Autowired
	RestTemplateBuilder restTemplateBuilder ;

	@Autowired
	CsapSecuritySettings securitySettings ;

	@Autowired
	CsapSecurityConfiguration csapSecurityConfiguration ;

	@Autowired ( required = false )
	CsapOauth2SecurityConfiguration csapOauth2SecurityConfiguration ;

	@BeforeEach
	void beforeEach ( ) {

		// Guard for setup
		Assumptions.assumeTrue( csapOauth2SecurityConfiguration != null, "OAuth correctly configured" ) ;

	}

	@Test
	void load_context ( ) {

		logger.info( CsapApplication.testHeader( ) ) ;

		logger.info( "beans loaded: {}", applicationContext.getBeanDefinitionCount( ) ) ;
		logger.debug( "beans loaded: {}", Arrays.asList( applicationContext.getBeanDefinitionNames( ) ) ) ;

		assertThat( applicationContext.getBeanDefinitionCount( ) )
				.as( "Spring Bean count" )
				.isGreaterThan( 200 ) ;

		assertThat( applicationContext.getBean( SecurityAutoConfiguration.class ) )
				.as( "CSAP element present if enabled: CsapInformation" )
				.isNotNull( ) ;

		Csap_Application_No_Security.verify_csap_components_loaded( applicationContext ) ;

		// Assert.assertFalse( true);

	}

	private static final String HI_SECURED_URL = "/hiWithLdapSecurity" ;
	private static final String HI_NO_SECURITY = "/hiNoLdapSecurity" ;

	/**
	 *
	 * Simple test app that excludes security autoconfiguration
	 *
	 */
	@CsapBootApplication
	public static class Simple_Oath2_App {

		Logger logger = LoggerFactory.getLogger( getClass( ) ) ;

		@RestController
		static public class SimpleHello {

			@GetMapping ( {
					HI_SECURED_URL, HI_NO_SECURITY
			} )
			public ObjectNode simple_http_test_endpoint ( ) {

				StringBuilder results = new StringBuilder( "Hello" +
						LocalDateTime.now( )
								.format( DateTimeFormatter
										.ofPattern( "HH:mm:ss,   MMMM d  uuuu " ) ) ) ;

				try {

					Authentication authentication = SecurityContextHolder.getContext( ).getAuthentication( ) ;
					String currentPrincipalName = authentication.getName( ) ;
					results.append( "user " + currentPrincipalName + " groups: " + authentication.getAuthorities( ) ) ;

				} catch ( Exception e ) {

					results.append( "user not authenticated" ) ;

				}

				var result = jsonMapper.createObjectNode( ) ;
				result.put( "message", results.toString( ) ) ;

				return result ;

			}

			@Inject
			ObjectMapper jsonMapper ;

		}

		@Bean
		@ConditionalOnProperty ( CsapSecurityConfiguration.PROPERTIES_ENABLED )
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
						.antMatchers( "/login", HI_NO_SECURITY )
						.permitAll( )

						//
						//
						.antMatchers( "/testAclFailure" )
						.hasRole( "NonExistGroupToTriggerAuthFailure" )
						//
						//
						.antMatchers( HI_SECURED_URL, "/someUrlNeedingAdmin1", "/anotherUrlNeedingAdmin" )
						.access( CsapSecurityRoles.hasAny( CsapSecurityRoles.Access.view )
								+ " OR "
								+ CsapSecurityRoles.hasAny( CsapSecurityRoles.Access.admin ) )
						//
						//
						.anyRequest( )
						.access( CsapSecurityRoles.hasAny( CsapSecurityRoles.Access.view ) ) ;

//				logger.info( "Enabling basic auth for testing only" );
//				 httpSecurity.httpBasic() ;
			} ) ;

			// @formatter:on

			return mySecurity ;

		}

	}

	@Test
	@DisplayName ( "Getting an unsecured url" )
	public void http_get_hi_no_secure_from_simple_app ( ) {

		logger.info( CsapApplication.testHeader( ) ) ;

		String simpleUrl = "http://localhost:" + testPort + HI_NO_SECURITY ;

		logger.info( CsapApplication.testHeader( "hitting url: {}" ), simpleUrl ) ;
		TestRestTemplate restTemplate = new TestRestTemplate( restTemplateBuilder ) ;

		ResponseEntity<ObjectNode> response = restTemplate.getForEntity( simpleUrl, ObjectNode.class ) ;

		logger.info( "response:\n {}", response ) ;

		assertThat( response.getBody( ).path( "message" ).asText( ) )
				.startsWith( "Hello" ) ;

	}

	@Test
	@DisplayName ( "Getting the local login" )
	public void http_get_local_login ( )
		throws Exception {

		String simpleUrl = "http://localhost:" + testPort + "/login?local=true" ;

		logger.info( CsapApplication.testHeader( "hitting url: {}" ), simpleUrl ) ;
		// mock does much validation.....

		TestRestTemplate restTemplate = new TestRestTemplate( restTemplateBuilder ) ;

		CSAP.setLogToDebug( CsapSecurityController.class.getName( ) ) ;
		ResponseEntity<String> localLoginResponse = restTemplate.getForEntity( simpleUrl, String.class ) ;
		CSAP.setLogToInfo( CsapSecurityController.class.getName( ) ) ;

		logger.info( "result:\n {}", localLoginResponse ) ;

		assertThat( localLoginResponse.getBody( ).toString( ) )
				.contains( "href=\"/oauth2/authorization/keycloak-user-auth\">" ) ;

	}

	@Test
	@DisplayName ( "Getting the oauth login" )
	public void http_get_oauth_login ( )
		throws Exception {

		String simpleUrl = "http://localhost:" + testPort + "/login" ;

		logger.info( CsapApplication.testHeader( "hitting url: {}" ), simpleUrl ) ;
		// mock does much validation.....

		TestRestTemplate restTemplate = new TestRestTemplate( restTemplateBuilder ) ;

		CSAP.setLogToDebug( CsapSecurityController.class.getName( ) ) ;
		ResponseEntity<String> loginPageResponse = restTemplate.getForEntity( simpleUrl, String.class ) ;
		CSAP.setLogToInfo( CsapSecurityController.class.getName( ) ) ;

		logger.info( "result:\n {}", loginPageResponse ) ;

		var oauthLocation = loginPageResponse.getHeaders( ).getFirst( "Location" ) ;

		assertThat( oauthLocation )
				.as( "Simple get on hello method" )
				.endsWith( "/oauth2/authorization/keycloak-user-auth" ) ;

		var oauthLocationResponse = restTemplate.getForEntity( oauthLocation, String.class ) ;

		logger.info( "oauthLocationResponse:\n {}", oauthLocationResponse ) ;

		var providerLogin = oauthLocationResponse.getHeaders( ).getFirst( "Location" ) ;

		var serviceClientName = "keycloak-user-auth" ;
		var issuerUri = oathProps.getProvider( ).get( serviceClientName ).getIssuerUri( ) ;

		logger.info( "serviceClientName: {}, issuerUri: {}", serviceClientName, issuerUri ) ;

		assertThat( providerLogin )
				.as( "Simple get on hello method" )
				.contains( issuerUri ) ;

	}

	@Inject
	OAuth2ClientProperties oathProps ;

	@Inject
	CsapWebClients csapWebClients ;

	@Inject
	ObjectMapper jsonMapper ;

	@Disabled
	@Test
	@DisplayName ( "Oauth authorization: protected endpoint via oauth2 client_credentials" )
	public void verifyOathWebClient ( )
		throws Exception {

		String simpleUrl = "http://localhost:" + testPort + HI_SECURED_URL ;

		JsonNode webClientResponse = csapWebClients.getContentUsingWebClientFromAnonymousContext( simpleUrl ) ;

		logger.info( "webClientResponse: {}", CSAP.jsonPrint( webClientResponse ) ) ;
		assertThat( webClientResponse.at( "/message" ).asText( ) ).startsWith( "Hello" ) ;

	}

}
