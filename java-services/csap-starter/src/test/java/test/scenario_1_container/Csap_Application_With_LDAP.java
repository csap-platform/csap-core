package test.scenario_1_container ;

import static org.assertj.core.api.Assertions.assertThat ;

import java.time.LocalDateTime ;
import java.time.format.DateTimeFormatter ;
import java.util.Arrays ;
import java.util.List ;

import javax.annotation.PostConstruct ;
import javax.inject.Inject ;
import javax.naming.ldap.LdapName ;

import org.csap.CsapBootApplication ;
import org.csap.helpers.CSAP ;
import org.csap.helpers.CsapApplication ;
import org.csap.integations.CsapEncryptionConfiguration ;
import org.csap.integations.CsapSecurityConfiguration ;
import org.csap.security.CsapSecurityLdapProvider ;
import org.csap.security.CsapUser ;
import org.csap.security.config.CsapSecurityRoles ;
import org.csap.security.config.CsapSecuritySettings ;
import org.junit.jupiter.api.Assumptions ;
import org.junit.jupiter.api.BeforeEach ;
import org.junit.jupiter.api.DisplayName ;
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
import org.springframework.http.HttpEntity ;
import org.springframework.http.HttpHeaders ;
import org.springframework.http.HttpMethod ;
import org.springframework.http.HttpStatus ;
import org.springframework.http.ResponseEntity ;
import org.springframework.ldap.support.LdapNameBuilder ;
import org.springframework.security.core.Authentication ;
import org.springframework.security.core.context.SecurityContextHolder ;
import org.springframework.security.ldap.search.FilterBasedLdapUserSearch ;
import org.springframework.security.ldap.userdetails.LdapUserDetails ;
import org.springframework.security.ldap.userdetails.LdapUserDetailsService ;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter ;
import org.springframework.test.annotation.DirtiesContext ;
import org.springframework.test.context.ActiveProfiles ;
import org.springframework.util.LinkedMultiValueMap ;
import org.springframework.util.MultiValueMap ;
import org.springframework.web.bind.annotation.GetMapping ;
import org.springframework.web.bind.annotation.RestController ;

import com.fasterxml.jackson.databind.JsonNode ;
import com.fasterxml.jackson.databind.ObjectMapper ;

@SpringBootTest ( //
		classes = Csap_Application_With_LDAP.Simple_Ldap_App.class , //
		webEnvironment = WebEnvironment.RANDOM_PORT )

@ActiveProfiles ( {
		"test", Csap_Application_With_LDAP.PROFILE_NAME, "ldap-test"
} )
@DirtiesContext

@TestInstance ( TestInstance.Lifecycle.PER_CLASS )
@DisplayName ( "CSAP Application: LDAP Enabled" )

class Csap_Application_With_LDAP {

	Logger logger = LoggerFactory.getLogger( getClass( ) ) ;

	static {

		CsapApplication.initialize( "Test Setup Complete" ) ;

	}

	final static String PROFILE_NAME = "localhost" ;

	@BeforeEach
	void beforeEach ( ) {

		// Guard for setup
		Assumptions.assumeTrue( ldapSettings.isConfigured( ) ) ;

	}

	@Test
	void load_context ( ) {

		logger.info( CsapApplication.TC_HEAD ) ;

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

	@Autowired ( required = false )
	public Settings_ldap ldapSettings = new Settings_ldap( ) ;

	@Autowired
	private ApplicationContext applicationContext ;

	@Autowired
	private ObjectMapper jsonMapper ;

	@LocalServerPort
	private int testPort ;

	@Autowired
	RestTemplateBuilder restTemplateBuilder ;

	@Autowired
	CsapSecuritySettings securitySettings ;

	@Autowired
	CsapSecurityConfiguration csapSecurityConfiguration ;

	@Autowired
	CsapSecurityLdapProvider csapSecurityLdapProvider ;

	@PostConstruct
	public void show_settings ( ) {

		logger.info( CsapApplication.testHeader( "{}" ), ldapSettings ) ;

		if ( ! ldapSettings.isConfigured( ) ) {

			Arrays.asList( Settings_ldap.SETUP_NOTES )
					.stream( )
					.forEach( System.out::println ) ;

		}

	}

	private static final String HI_SECURED_URL = "/hiWithLdapSecurity" ;
	private static final String HI_NO_SECURITY = "/hiNoLdapSecurity" ;
	private static final String SEC_USER = "/securedUser" ;

	/**
	 * 
	 * Simple test app that excludes security autoconfiguration
	 *
	 */
	@CsapBootApplication
	public static class Simple_Ldap_App {

		Logger logger = LoggerFactory.getLogger( getClass( ) ) ;

		@RestController
		static public class SimpleHello {

			@GetMapping ( {
					HI_SECURED_URL, HI_NO_SECURITY
			} )
			public String simple_http_test_endpoint ( ) {

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

				return results.toString( ) ;

			}

			@GetMapping ( {
					SEC_USER,
			} )
			public JsonNode userReport ( ) {

				return CsapUser.getPrincipleInfo( ) ;

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
//					.csrf()
//						.requireCsrfProtectionMatcher( CsapSecurityConfiguration.buildRequestMatcher( "/login*" ) )
//						.and()

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
						.antMatchers( HI_SECURED_URL, SEC_USER, "/someUrlNeedingAdmin1", "/anotherUrlNeedingAdmin" )
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
	public void lookup_user_in_ldap ( )
		throws Exception {

		logger.info( CsapApplication.TC_HEAD + "Looking up: {}", ldapSettings.getUser( ) ) ;

		// CsapUser csapUser = (CsapUser) ldapTemplate
		// .lookup( ldapEntryIdentification.getRelativeDn(),
		// new CsapUser() );
		String dnSearchAttribute = securitySettings.getProvider( ).getDirectoryDn( ).split( ":" )[1] ;

		FilterBasedLdapUserSearch searchFilter = new FilterBasedLdapUserSearch(
				securitySettings.getProvider( ).getSearchUser( ),
				dnSearchAttribute,
				securitySettings.getContextSource( ) ) ;

		LdapUserDetailsService ldapUserSearch = new LdapUserDetailsService( searchFilter ) ;

		LdapUserDetails ldapUserDetails = (LdapUserDetails) ldapUserSearch.loadUserByUsername( ldapSettings
				.getUser( ) ) ;

		logger.info( "LdapUserDetails: {}", ldapUserDetails ) ;

		assertThat( ldapUserDetails.getDn( ) )
				.as( "ldap user dn" )
				.contains( securitySettings.getProvider( ).getSearchUser( ) ) ;

		LdapName ldapName = LdapNameBuilder.newInstance( ldapUserDetails.getDn( ) ).build( ) ;
		logger.info( "ldapName: {}", ldapName ) ;

		CsapUser csapUser = (CsapUser) csapSecurityLdapProvider.ldapTemplate( ).lookup(
				ldapName,
				CsapUser.PRIMARY_ATTRIBUTES,
				new CsapUser( ) ) ;

		logger.info( "csapUser: {}", csapUser ) ;

		assertThat( csapUser.getUserid( ) )
				.as( "csapUser userid" )
				.contains( ldapUserDetails.getUsername( ) ) ;

	}

	@Test
	public void verify_user_info ( )
		throws Exception {

		String simpleUrl = "http://localhost:" + testPort + SEC_USER ;

		logger.info( CsapApplication.testHeader( "hitting url: {}" ), simpleUrl ) ;

		// NOW use secured
		TestRestTemplate restTemplateWithAuth = new TestRestTemplate(
				ldapSettings.getUser( ),
				csapEncrypt.decodeIfPossible( ldapSettings.getPass( ), logger ) ) ;
		ResponseEntity<String> responseFromCredQuery = restTemplateWithAuth.getForEntity( simpleUrl, String.class ) ;

		logger.info( "user info:\n {}", responseFromCredQuery ) ;

		assertThat( responseFromCredQuery.getStatusCode( ) ).isEqualTo( HttpStatus.OK ) ;

		var userReport = jsonMapper.readTree( responseFromCredQuery.getBody( ) ) ;

		logger.info( "userReport: {}", CSAP.jsonPrint( userReport ) ) ;

		assertThat( userReport.path( "dn" ).asText( ) ).contains( "Peter Nightingale,ou=People,dc=flexnet,dc=net" ) ;

	}

	@Test
	public void http_get_hi_secure_from_simple_app ( )
		throws Exception {

		String simpleUrl = "http://localhost:" + testPort + HI_SECURED_URL ;

		logger.info( CsapApplication.testHeader( "hitting url: {}" ), simpleUrl ) ;

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
		TestRestTemplate restTemplateWithAuth = new TestRestTemplate(
				ldapSettings.getUser( ),
				csapEncrypt.decodeIfPossible( ldapSettings.getPass( ), logger ) ) ;
		ResponseEntity<String> responseFromCredQuery = restTemplateWithAuth.getForEntity( simpleUrl, String.class ) ;

		logger.info( "responseFromCredQuery:\n {}", responseFromCredQuery ) ;

		assertThat( responseFromCredQuery.getStatusCode( ) )
				.as( "Simple get on hello method" )
				.isEqualTo( HttpStatus.OK ) ;

		assertThat( responseFromCredQuery.getBody( ) )
				.startsWith( "Hello" ) ;

	}

	@Test
	public void http_get_secured_resource_using_login_cookies ( )
		throws Exception {

		logger.info( CsapApplication.TC_HEAD + "Logging in to server" ) ;

		ResponseEntity<String> loginResponse = performLogin( ) ;
		logger.info( "login response:\n {}", loginResponse ) ;

		String simpleUrl = "http://localhost:" + testPort + HI_SECURED_URL ;

		HttpHeaders loginResponseHeaders = loginResponse.getHeaders( ) ;

		HttpHeaders requestHeaders = new HttpHeaders( ) ;
		List<String> cookieList = loginResponseHeaders.get( HttpHeaders.SET_COOKIE ) ;
		assertThat( cookieList ).hasSize( 2 ) ;

		cookieList.forEach( cookie -> {

			requestHeaders.add( "Cookie", cookie ) ;

		} ) ;
		HttpEntity<String> requestEntity = new HttpEntity<String>( null, requestHeaders ) ;

		logger.info( "hitting url: {} , requestEntity: {}", simpleUrl, requestEntity ) ;
		// NOW use secured
		TestRestTemplate restTemplateWithAuth = new TestRestTemplate( ) ;
		// restTemplateWithAuth.

		ResponseEntity<String> response_from_secure_url = restTemplateWithAuth.exchange(
				simpleUrl,
				HttpMethod.GET,
				requestEntity,
				String.class ) ;

		logger.info( "response_from_secure_url:\n {}", response_from_secure_url ) ;

		assertThat( response_from_secure_url.getStatusCode( ) )
				.as( "Simple get on hello method" )
				.isEqualTo( HttpStatus.OK ) ;

		assertThat( response_from_secure_url.getBody( ) )
				.startsWith( "Hello" ) ;

	}

	@Test
	public void http_get_secured_resource_using_remember_me_cookie ( )
		throws Exception {

		logger.info( CsapApplication.TC_HEAD + "Logging in to server" ) ;

		ResponseEntity<String> loginResponse = performLogin( ) ;
		logger.info( "login response:\n {}", loginResponse ) ;

		String simpleUrl = "http://localhost:" + testPort + HI_SECURED_URL ;

		HttpHeaders loginResponseHeaders = loginResponse.getHeaders( ) ;

		HttpHeaders requestHeaders = new HttpHeaders( ) ;
		List<String> cookieList = loginResponseHeaders.get( HttpHeaders.SET_COOKIE ) ;
		assertThat( cookieList ).hasSize( 2 ) ;

		cookieList.forEach( cookie -> {

			if ( cookie.matches( securitySettings.getCookie( ).getName( ) + ".*" ) ) {

				requestHeaders.add( "Cookie", cookie ) ;

			}

		} ) ;
		HttpEntity<String> requestEntity = new HttpEntity<String>( null, requestHeaders ) ;

		logger.info( "hitting url: {} , requestEntity: \n\t {}", simpleUrl, requestEntity ) ;
		// NOW use secured
		TestRestTemplate restTemplateWithAuth = new TestRestTemplate( ) ;
		// restTemplateWithAuth.

		ResponseEntity<String> response_from_secure_url = restTemplateWithAuth.exchange(
				simpleUrl,
				HttpMethod.GET,
				requestEntity,
				String.class ) ;

		logger.info( "response_from_secure_url:\n {}", response_from_secure_url ) ;

		assertThat( response_from_secure_url.getStatusCode( ) )
				.as( "Simple get on hello method" )
				.isEqualTo( HttpStatus.OK ) ;

		assertThat( response_from_secure_url.getBody( ) )
				.startsWith( "Hello" ) ;

	}

	@Test
	public void http_get_secured_resource_using_BAD_remember_me_cookie ( )
		throws Exception {

		logger.info( CsapApplication.TC_HEAD + "Logging in to server" ) ;

		ResponseEntity<String> loginResponse = performLogin( ) ;
		logger.info( "login response:\n {}", loginResponse ) ;

		String simpleUrl = "http://localhost:" + testPort + HI_SECURED_URL ;

		HttpHeaders loginResponseHeaders = loginResponse.getHeaders( ) ;

		HttpHeaders requestHeaders = new HttpHeaders( ) ;
		List<String> cookieList = loginResponseHeaders.get( HttpHeaders.SET_COOKIE ) ;
		assertThat( cookieList ).hasSize( 2 ) ;

		cookieList.forEach( cookie -> {

			if ( cookie.matches( securitySettings.getCookie( ).getName( ) + ".*" ) ) {

				requestHeaders.add( "Cookie", securitySettings.getCookie( ).getName( ) + "=BAD" ) ;

			}

		} ) ;
		HttpEntity<String> requestEntity = new HttpEntity<String>( null, requestHeaders ) ;

		logger.info( "hitting url: {} , requestEntity: \n\t {}", simpleUrl, requestEntity ) ;
		// NOW use secured
		TestRestTemplate restTemplateWithAuth = new TestRestTemplate( ) ;
		// restTemplateWithAuth.

		ResponseEntity<String> response_from_secure_url = restTemplateWithAuth.exchange(
				simpleUrl,
				HttpMethod.GET,
				requestEntity,
				String.class ) ;

		logger.info( "response_from_secure_url:\n {}", response_from_secure_url ) ;

		assertThat( response_from_secure_url.getStatusCode( ) )
				.as( "Simple get on hello method" )
				.isEqualTo( HttpStatus.FOUND ) ;

		HttpHeaders response_from_secure_url_headers = response_from_secure_url.getHeaders( ) ;

		assertThat( response_from_secure_url_headers.getFirst( "Location" ) )
				.as( "redirected to login due to invalid credential" )
				.endsWith( "login" ) ;

	}

	@Test
	void verify_login_sets_cookies ( ) {

		logger.info( CsapApplication.testHeader( ) ) ;

		ResponseEntity<String> loginResponse = performLogin( ) ;

		logger.info( "login response:\n {}", loginResponse ) ;

		assertThat( loginResponse.getStatusCode( ) )
				.as( "Simple get on hello method" )
				.isEqualTo( HttpStatus.FOUND ) ;

		HttpHeaders loginResponseHeaders = loginResponse.getHeaders( ) ;

		assertThat( loginResponseHeaders.getFirst( "Location" ) )
				.as( "not redirected to login" )
				.doesNotContain( "login" ) ;

		List<String> cookieList = loginResponseHeaders.get( HttpHeaders.SET_COOKIE ) ;
		assertThat( cookieList ).hasSize( 2 ) ;

		cookieList.forEach( cookie -> {

			boolean validCookie = cookie.matches( "JSESSION.*" +
					"|" + securitySettings.getCookie( ).getName( ) + ".*" ) ;

			assertThat( validCookie )
					.as( "cookie list of valid enties: " + cookie )
					.isTrue( ) ;

		} ) ;
		logger.info( "cookies: {}", cookieList ) ;

	}

	@Inject
	CsapEncryptionConfiguration csapEncrypt ;

	private ResponseEntity<String> performLogin ( ) {

		String simpleUrl = "http://localhost:" + testPort + "/login" ;
		logger.info( "simple login test" ) ;

		/**
		 * 
		 * STEP 1 login
		 * 
		 */
		TestRestTemplate restTemplateWithAuth = new TestRestTemplate( ) ;
		MultiValueMap<String, String> formParams = new LinkedMultiValueMap<String, String>( ) ;

		formParams.set(
				UsernamePasswordAuthenticationFilter.SPRING_SECURITY_FORM_USERNAME_KEY,
				ldapSettings.getUser( ) ) ;
		formParams.set(
				UsernamePasswordAuthenticationFilter.SPRING_SECURITY_FORM_PASSWORD_KEY,
				csapEncrypt.decodeIfPossible( ldapSettings.getPass( ), logger ) ) ;

		ResponseEntity<String> loginResponse = restTemplateWithAuth
				.postForEntity( simpleUrl,
						formParams,
						String.class ) ;
		return loginResponse ;

	}

	// private String extractSsoCookie ( HttpServletRequest request ) {
	// String ssoCookieStringForHeader =
	// securityConfig.getRememberMeCookieName() + "=NotUsed";
	//
	// if ( request != null ) {
	// ssoCookieStringForHeader = securityConfig.getRememberMeCookieName() + "="
	// + WebUtils.getCookie( request, securityConfig.getRememberMeCookieName()
	// ).getValue();
	// }
	//
	// return ssoCookieStringForHeader;
	// }

}
