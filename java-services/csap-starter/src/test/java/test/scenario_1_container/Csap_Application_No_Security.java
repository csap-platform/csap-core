package test.scenario_1_container ;

import static org.assertj.core.api.Assertions.assertThat ;

import java.time.LocalDateTime ;
import java.time.format.DateTimeFormatter ;

import javax.inject.Inject ;

import org.csap.CsapBootApplication ;
import org.csap.docs.DocumentController ;
import org.csap.helpers.CsapApplication ;
import org.csap.integations.CsapBootConfig ;
import org.csap.integations.CsapEncryptionConfiguration ;
import org.csap.integations.CsapInformation ;
import org.csap.integations.CsapWebServerConfig ;
import org.jasypt.encryption.pbe.StandardPBEStringEncryptor ;
import org.junit.jupiter.api.BeforeAll ;
import org.junit.jupiter.api.Test ;
import org.junit.jupiter.api.TestInstance ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.beans.factory.annotation.Autowired ;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration ;
import org.springframework.boot.test.context.SpringBootTest ;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment ;
import org.springframework.boot.test.web.client.TestRestTemplate ;
import org.springframework.boot.web.client.RestTemplateBuilder ;
import org.springframework.boot.web.server.LocalServerPort ;
import org.springframework.cache.CacheManager ;
import org.springframework.cache.support.NoOpCacheManager ;
import org.springframework.context.ApplicationContext ;
import org.springframework.http.ResponseEntity ;
import org.springframework.scheduling.annotation.EnableAsync ;
import org.springframework.scheduling.annotation.SchedulingConfiguration ;
import org.springframework.test.annotation.DirtiesContext ;
import org.springframework.test.context.ActiveProfiles ;
import org.springframework.web.bind.annotation.GetMapping ;
import org.springframework.web.bind.annotation.RestController ;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer ;

import com.fasterxml.jackson.databind.ObjectMapper ;

@SpringBootTest ( //
		classes = Csap_Application_No_Security.Simple_CSAP.class , //
		webEnvironment = WebEnvironment.RANDOM_PORT )
@ActiveProfiles ( {
		"test", "no-security"
} )
@DirtiesContext
@TestInstance ( TestInstance.Lifecycle.PER_CLASS )
public class Csap_Application_No_Security {

	final static private Logger logger = LoggerFactory.getLogger( Csap_Application_No_Security.class ) ;

	@BeforeAll
	public void setUpBeforeClass ( )
		throws Exception {

		System.out.println( "Starting logging" ) ;

	}

	static {

		CsapApplication.initialize( "Test Setup Complete" ) ;

	}

	@Autowired
	private ApplicationContext applicationContext ;

	/**
	 * 
	 * Simple test app that excludes security autoconfiguration
	 *
	 */
	@CsapBootApplication ( scanBasePackages = {
			"org.none"
	} )
	@EnableAsync
	public static class Simple_CSAP implements WebMvcConfigurer {

		@RestController
		static public class SimpleHello {

			@GetMapping ( "/csapHiNoSecurity" )
			public String hi ( ) {

				return "Hello" +
						LocalDateTime.now( )
								.format( DateTimeFormatter
										.ofPattern( "HH:mm:ss,   MMMM d  uuuu " ) ) ;

			}

			@Inject
			ObjectMapper jsonMapper ;

		}

	}

	@Test
	public void verify_application_context ( ) {

		logger.info( CsapApplication.testHeader( "beans loaded: {}" ), applicationContext.getBeanDefinitionCount( ) ) ;

		verify_spring_components( applicationContext ) ;

		verify_csap_components_loaded( applicationContext ) ;

		// Assert.assertFalse( true);

	}

	private void verify_spring_components ( ApplicationContext contextLoaded ) {

		assertThat( contextLoaded.getBeanDefinitionCount( ) )
				.as( "Spring Bean count" )
				.isGreaterThan( 200 ) ;

		assertThat( contextLoaded.containsBean( SecurityAutoConfiguration.class.getName( ) ) )
				.as( "securityAutoConfiguration is disabled" )
				.isFalse( ) ;

		assertThat( contextLoaded.containsBean( SchedulingConfiguration.class.getName( ) ) )
				.as( "SchedulingConfiguration is enabled and used extensively in csap app" )
				.isTrue( ) ;

		CacheManager springCacheManager = contextLoaded.getBean( CacheManager.class ) ;
		logger.debug( "springCacheManager: {}, names: {}", springCacheManager, springCacheManager.getCacheNames( ) ) ;
		assertThat( springCacheManager instanceof NoOpCacheManager )
				.as( "junit disable cache manager" )
				.isTrue( ) ;

	}

	public static void verify_csap_components_loaded ( ApplicationContext contextLoaded ) {

		assertThat( contextLoaded.getBean( CsapInformation.class ) )
				.as( "CSAP element present if enabled: CsapInformation" )
				.isNotNull( ) ;

		assertThat( contextLoaded.getBean( DocumentController.class ) )
				.as( "CSAP element  present if enabled: DocumentController" )
				.isNotNull( ) ;

		assertThat( contextLoaded.getBean( CsapEncryptionConfiguration.class ) )
				.as( "CSAP element present if enabled" )
				.isNotNull( ) ;

		verify_csap_web_server( contextLoaded ) ;

		String csapBootMessage = contextLoaded.getBean( CsapBootConfig.class ).getCsapInitializationMessage( )
				.toString( ) ;
		logger.info( csapBootMessage ) ;

		assertThat( csapBootMessage )
				.contains( "csap.documentation" )
				.contains( "spring cache manager" ) ;

	}

	private static void verify_csap_web_server ( ApplicationContext contextLoaded ) {

		CsapWebServerConfig webServer = contextLoaded.getBean( CsapWebServerConfig.class ) ;

		logger.debug( webServer.toString( ) ) ;

		assertThat( webServer ).isNotNull( ) ;

		assertThat( webServer.getSettings( ).getAjpConnectionPort( ) )
				.isEqualTo( 1 ) ;

		assertThat( webServer.getSettings( ).getMaxConnectionsAjp( ) )
				.isEqualTo( 99 ) ;

	}

	@LocalServerPort
	private int testPort ;

	@Inject
	RestTemplateBuilder restTemplateBuilder ;

	@Test
	public void verify_http_request_security_disabled ( )
		throws Exception {

		String simpleUrl = "http://localhost:" + testPort + "/csapHiNoSecurity" ;

		logger.info( CsapApplication.testHeader( "hitting url: {}" ), simpleUrl ) ;

		// mock does much validation.....

		TestRestTemplate restTemplate = new TestRestTemplate( restTemplateBuilder ) ;

		ResponseEntity<String> response = restTemplate.getForEntity( simpleUrl, String.class ) ;

		logger.info( "result:\n {}", response ) ;

		assertThat( response.getBody( ) )
				.startsWith( "Hello" ) ;

	}

	@Inject
	private StandardPBEStringEncryptor encryptor ;

	@Test
	public void verify_encryption_using_jasypt ( ) {

		String testSample = "Testing encyrpt" ;
		String encSample = encryptor.encrypt( testSample ) ;

		String message = "Encoding of  " + testSample + " is " + encSample ;
		logger.info( message ) ;

		assertThat( testSample ).isNotEqualTo( encSample ) ;

		assertThat( testSample ).isEqualTo( encryptor.decrypt( encSample ) ) ;
		// assertTrue( encryptor.decrypt( encSample).equals( testSample) ) ;

	}

	@Autowired
	CsapEncryptionConfiguration csapEnc ;

	@Test
	public void verify_encryption_using_csap ( ) {

		String testSample = "Testing encyrpt" ;
		String encSample = encryptor.encrypt( testSample ) ;

		String message = "Encoding of  " + testSample + " is " + encSample ;
		logger.info( message ) ;

		assertThat( testSample ).isNotEqualTo( encSample ) ;

		assertThat( testSample ).isEqualTo( encryptor.decrypt( encSample ) ) ;

		assertThat( testSample ).isEqualTo( csapEnc.decodeIfPossible( encSample, logger ) ) ;

		assertThat( testSample ).isEqualTo( csapEnc.decodeIfPossible( testSample, logger ) ) ;
		// assertTrue( encryptor.decrypt( encSample).equals( testSample) ) ;

	}

}
