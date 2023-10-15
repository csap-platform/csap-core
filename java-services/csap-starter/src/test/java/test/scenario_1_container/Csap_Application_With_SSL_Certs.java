package test.scenario_1_container ;

import static org.assertj.core.api.Assertions.assertThat ;

import java.net.URL ;
import java.time.LocalDateTime ;
import java.time.format.DateTimeFormatter ;

import javax.inject.Inject ;

import org.csap.CsapBootApplication ;
import org.csap.helpers.CsapApplication ;
import org.csap.helpers.CsapRestTemplateFactory ;
import org.csap.integations.CsapEncryptionConfiguration ;
import org.csap.integations.CsapWebServerConfig ;
import org.csap.integations.CsapWebSettings ;
import org.junit.jupiter.api.Test ;
import org.junit.jupiter.api.TestInstance ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.beans.factory.annotation.Autowired ;
import org.springframework.boot.test.context.SpringBootTest ;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment ;
import org.springframework.boot.test.web.client.TestRestTemplate ;
import org.springframework.boot.web.client.RestTemplateBuilder ;
import org.springframework.boot.web.server.LocalServerPort ;
import org.springframework.context.ApplicationContext ;
import org.springframework.test.annotation.DirtiesContext ;
import org.springframework.test.context.ActiveProfiles ;
import org.springframework.web.bind.annotation.GetMapping ;
import org.springframework.web.bind.annotation.RestController ;

import com.fasterxml.jackson.databind.ObjectMapper ;

@SpringBootTest ( //
		classes = Csap_Application_With_SSL_Certs.SSL_Tester_Application.class , //
		webEnvironment = WebEnvironment.RANDOM_PORT )

@ActiveProfiles ( {
		"test",
		"junit-ssl"
} )

@DirtiesContext
@TestInstance ( TestInstance.Lifecycle.PER_CLASS )
public class Csap_Application_With_SSL_Certs {
	final static private Logger logger = LoggerFactory.getLogger( Csap_Application_With_SSL_Certs.class ) ;

	static {

		CsapApplication.initialize( "Test Setup Complete" ) ;

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
//	( scanBasePackages = {
//			"org.none"
//	} )
	public static class SSL_Tester_Application {

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

	}

	@Inject
	CsapWebServerConfig csapWebServer ;

	@Test
	public void load_context ( ) {

		logger.info( CsapApplication.testHeader( "loaded beans: {}" ), applicationContext.getBeanDefinitionCount( ) ) ;

		assertThat( applicationContext.getBeanDefinitionCount( ) )
				.as( "Spring Bean count" )
				.isGreaterThan( 200 ) ;

		assertThat( csapWebServer ).isNotNull( ) ;

		var connectorReport = csapWebServer.getConnectorReport( ).toString( ) ;

		logger.info( "csapWebServer: {}", connectorReport ) ;

		assertThat( csapWebServer.isSslServerStarted( ) ).isTrue( ) ;

		// Assert.assertFalse( true);

	}

	@LocalServerPort
	private int testPort ;

	@Inject
	RestTemplateBuilder restTemplateBuilder ;

	@Inject
	CsapWebSettings webServerSettings ;

	@Inject
	CsapEncryptionConfiguration csapEncrypt ;

	@Test
	public void verify_self_signed_ssl_http_get ( )
		throws Exception {

		var httpUrl = "http://localhost:" + testPort + HI_NO_SECURITY ;

		logger.info( CsapApplication.testHeader( "hitting: {}" ), httpUrl ) ;

		var httpRestTemplate = new TestRestTemplate( restTemplateBuilder ) ;

		var httpRestResponse = httpRestTemplate.getForEntity( httpUrl, String.class ) ;

		logger.info( "htttpRestResponse:\n" + httpRestResponse ) ;

		assertThat( httpRestResponse.getBody( ) ).startsWith( "Hello" ) ;

		var sslUrl = "https://localhost:" + webServerSettings.getSsl( ).getPort( ) + HI_NO_SECURITY ;

		logger.info( CsapApplication.testHeader( "hitting: {}" ), sslUrl ) ;

		var decoded = csapEncrypt.decodeIfPossible( webServerSettings.getSsl( ).getKeystorePassword( ), logger ) ;
		var restSslFactory = new CsapRestTemplateFactory(
				new URL( webServerSettings.getSsl( ).getKeystoreFile( ) ),
				decoded ) ;

		var restSslTemplate = restSslFactory.buildDefaultRestTemplate( "verify_self_signed_ssl_http_get" ) ;

		var sslRestResponse = restSslTemplate.getForEntity( httpUrl, String.class ) ;

		logger.info( "sslRestResponse:\n" + sslRestResponse ) ;

		assertThat( sslRestResponse.getBody( ) ).startsWith( "Hello" ) ;

	}

}
