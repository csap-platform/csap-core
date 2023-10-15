package test.scenario_1_container ;

import static org.assertj.core.api.Assertions.assertThat ;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user ;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity ;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get ;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content ;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status ;

import java.time.LocalDateTime ;
import java.time.format.DateTimeFormatter ;

import javax.inject.Inject ;

import org.csap.CsapBootApplication ;
import org.csap.helpers.CSAP ;
import org.csap.helpers.CsapApplication ;
import org.csap.integations.CsapSecurityConfiguration ;
import org.csap.security.config.CsapSecurityRoles ;
import org.junit.jupiter.api.BeforeAll ;
import org.junit.jupiter.api.DisplayName ;
import org.junit.jupiter.api.Test ;
import org.junit.jupiter.api.TestInstance ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.beans.factory.annotation.Autowired ;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty ;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration ;
import org.springframework.boot.test.context.SpringBootTest ;
import org.springframework.boot.web.client.RestTemplateBuilder ;
import org.springframework.context.ApplicationContext ;
import org.springframework.context.annotation.Bean ;
import org.springframework.http.MediaType ;
import org.springframework.mock.web.MockHttpServletResponse ;
import org.springframework.test.annotation.DirtiesContext ;
import org.springframework.test.context.ActiveProfiles ;
import org.springframework.test.web.servlet.MockMvc ;
import org.springframework.test.web.servlet.ResultActions ;
import org.springframework.test.web.servlet.setup.MockMvcBuilders ;
import org.springframework.web.bind.annotation.GetMapping ;
import org.springframework.web.bind.annotation.RestController ;
import org.springframework.web.context.WebApplicationContext ;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer ;

import com.fasterxml.jackson.databind.ObjectMapper ;

@SpringBootTest ( //
		classes = Csap_Application_With_MockSecurity.Simple_In_Memory_App.class )

@ActiveProfiles ( "test" )

@DirtiesContext
@TestInstance ( TestInstance.Lifecycle.PER_CLASS )

@DisplayName ( "CSAP Application - verify mocked security and roles" )
public class Csap_Application_With_MockSecurity {

	final static private Logger logger = LoggerFactory.getLogger( Csap_Application_With_MockSecurity.class ) ;

	static {

		CsapApplication.initialize( "Test Setup Complete" ) ;

	}

	@Inject
	WebApplicationContext wac ;
	MockMvc mockMvc ;

	@BeforeAll
	void beforeAll ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( "Wiring spring security mock" ) ) ;

		this.mockMvc = MockMvcBuilders.webAppContextSetup( this.wac ).apply( springSecurity( ) ).build( ) ;

	}

	@Autowired
	private ApplicationContext applicationContext ;

	private static final String HI_SECURED_URL = "/hiWithSecurity" ;
	private static final String HI_NO_SECURITY = "/hiNoSecurity" ;

	/**
	 * 
	 * Simple test app that excludes security autoconfiguration
	 *
	 */
	@CsapBootApplication
	public static class Simple_In_Memory_App implements WebMvcConfigurer {

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

						.antMatchers( HI_SECURED_URL )
						.hasRole( CsapSecurityRoles.ADMIN_ROLE )

						.antMatchers( HI_NO_SECURITY )
						.permitAll( )

//							.access( CsapSecurityRoles.hasAny( CsapSecurityRoles.Access.admin ) )
						//
						//
						.antMatchers( "/testAclFailure" )
						.hasRole( "NonExistGroupToTriggerAuthFailure" )
//						//
//						//
//						.antMatchers("/someUrlNeedingAdmin1", "/anotherUrlNeedingAdmin" )
//							.access( CsapSecurityRoles.hasAny( CsapSecurityRoles.Access.view )
//								+ " OR "
//								+ CsapSecurityRoles.hasAny( CsapSecurityRoles.Access.admin ) )
						//
						//
						.anyRequest( )
						.authenticated( ) ;
//				.anyRequest()
//				.access( CsapSecurityRoles.hasAny( CsapSecurityRoles.Access.view ) );

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

		// Assert.assertFalse( true);

	}

	@Inject
	RestTemplateBuilder restTemplateBuilder ;

	@Test
	@DisplayName ( "Spring In Memory Security: mocked with permitall" )
	public void verifyPermitAll ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( "Wiring spring security mock" ) ) ;

		// mock does much validation.....
		ResultActions resultActions = mockMvc.perform(
				get( HI_NO_SECURITY )
						.accept( MediaType.TEXT_PLAIN ) ) ;

		//
		String result = resultActions
				.andExpect( status( ).isOk( ) )
				.andExpect( content( ).contentTypeCompatibleWith( MediaType.TEXT_PLAIN ) )
				.andReturn( ).getResponse( ).getContentAsString( ) ;

		logger.info( "result: {}", result ) ;

		assertThat( result ).startsWith( "Hello" ) ;

	}

	@Test
	@DisplayName ( "Spring In Memory Security: mocked requires login" )
	public void verifyHttpGetUsingMockSecurityLogin ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;
		// mock does much validation.....
		ResultActions resultActions = mockMvc.perform(
				get( HI_SECURED_URL )
						.accept( MediaType.TEXT_PLAIN ) ) ;

		//
		MockHttpServletResponse response = resultActions
				.andExpect( status( ).isFound( ) )
				.andReturn( ).getResponse( ) ;

		logger.info( "result: {}", CSAP.jsonPrint( CSAP.buildGenericObjectReport( response ) ) ) ;

		assertThat( response.getRedirectedUrl( ) ).isEqualTo( "http://localhost/login" ) ;

	}

	@Test
	@DisplayName ( "Spring In Memory Security: mocked user" )
	public void verifyHttpGetUsingMockSecurityUser ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( "Wiring spring security mock" ) ) ;

		// mock does much validation.....
		ResultActions resultActions = mockMvc.perform(
				get( HI_SECURED_URL )
						.with( user( "some-random-username" ).roles( CsapSecurityRoles.ADMIN_ROLE,
								CsapSecurityRoles.VIEW_ROLE ) )
						.accept( MediaType.TEXT_PLAIN ) ) ;

		//
		String result = resultActions
				.andExpect( status( ).isOk( ) )
				.andExpect( content( ).contentTypeCompatibleWith( MediaType.TEXT_PLAIN ) )
				.andReturn( ).getResponse( ).getContentAsString( ) ;

		logger.info( "result: {}", result ) ;

		assertThat( result ).startsWith( "Hello" ) ;

	}

}
