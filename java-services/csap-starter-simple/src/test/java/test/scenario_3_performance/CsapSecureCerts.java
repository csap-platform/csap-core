package test.scenario_3_performance ;

import static org.assertj.core.api.Assertions.assertThat ;

import java.net.URL ;

import javax.inject.Inject ;

import org.csap.helpers.CsapApplication ;
import org.csap.helpers.CsapRestTemplateFactory ;
import org.csap.integations.CsapEncryptionConfiguration ;
import org.csap.integations.CsapWebSettings ;
import org.junit.jupiter.api.Assumptions ;
import org.junit.jupiter.api.BeforeAll ;
import org.junit.jupiter.api.Disabled ;
import org.junit.jupiter.api.Test ;
import org.sample.Csap_Simple_Application ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.beans.factory.annotation.Autowired ;
import org.springframework.boot.test.context.SpringBootTest ;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment ;
import org.springframework.boot.test.web.client.TestRestTemplate ;
import org.springframework.boot.web.client.RestTemplateBuilder ;
import org.springframework.boot.web.server.LocalServerPort ;
import org.springframework.http.ResponseEntity ;
import org.springframework.test.context.ActiveProfiles ;

import com.fasterxml.jackson.databind.ObjectMapper ;

@SpringBootTest ( //
		classes = Csap_Simple_Application.class , //
		webEnvironment = WebEnvironment.RANDOM_PORT )

@ActiveProfiles ( {
		"junit",
		"junit-ssl"
} )

public class CsapSecureCerts {
	final static private Logger logger = LoggerFactory.getLogger( CsapSecureCerts.class ) ;

	@BeforeAll
	public static void setUpBeforeClass ( )
		throws Exception {

		CsapApplication.initialize( logger.getName( ) ) ;

	}

	ObjectMapper jacksonMapper = new ObjectMapper( ) ;

	@Inject
	RestTemplateBuilder restTemplateBuilder ;

	@Inject
	CsapWebSettings webServerSettings ;

	@LocalServerPort
	private int testPort ;

	@Autowired ( required = false )
	CsapEncryptionConfiguration csapEncrypt ;

	@Test
	public void verify_self_signed_ssl_http_get ( )
		throws Exception {
		
		

		var httpUrl = "http://localhost:" + testPort + "/currentTime" ;
//		var httpsUrl = "https://localhost" + "." + trustStoreDomain + ":" + testPort
//			+ "/currentTime" ;

		logger.info( CsapApplication.testHeader( "os {} url: {}" ),
				System.getProperty("os.name"), 
				httpUrl ) ;
		

		TestRestTemplate httpRestTemplate = new TestRestTemplate( restTemplateBuilder ) ;
		ResponseEntity<String> httpResponse = httpRestTemplate.getForEntity( httpUrl, String.class ) ;

		logger.info( "httpResponse: {} ", httpResponse ) ;

		var sslUrl = "https://localhost:" + webServerSettings.getSsl( ).getPort( )
				+ "/currentTime" ;
		

		var decoded = csapEncrypt.decodeIfPossible( webServerSettings.getSsl( ).getKeystorePassword( ), logger ) ;

		logger.info( CsapApplication.testHeader( "url: {}, trustStore: {}, trustStorePassword: {}" ),
				sslUrl, 
				webServerSettings.getSsl( ).getKeystoreFile( ), 
				webServerSettings.getSsl( ).getKeystorePassword( ) ) ;


		var restSslFactory = new CsapRestTemplateFactory(
				new URL( webServerSettings.getSsl( ).getKeystoreFile( ) ),
				decoded ) ;

		var restSslTemplate = restSslFactory.buildDefaultRestTemplate( "verify_self_signed_ssl_http_get" ) ;

		ResponseEntity<String> customSslResponse = restSslTemplate.getForEntity( sslUrl, String.class ) ;
		logger.info( "customSslResponse: {} ", customSslResponse ) ;
		assertThat( customSslResponse.getBody( ) ).contains( "currentTime:" ) ;

	}
	
	
	
	//
	//  This requires csap-lab.p12 and /etc/hosts updated
	//
	@Disabled
	@Test
	public void verify_self_signed_ssl_yourcompany_lab_http_get ( )
		throws Exception {
		
		

		var httpUrl = "http://localhost:" + testPort + "/currentTime" ;
//		var httpsUrl = "https://localhost" + "." + trustStoreDomain + ":" + testPort
//			+ "/currentTime" ;

		logger.info( CsapApplication.testHeader( "os {} url: {}" ),
				System.getProperty("os.name"), 
				httpUrl ) ;
		

		Assumptions.assumeTrue( System.getProperty("os.name").contains( "Windows" ) ) ;

		TestRestTemplate httpRestTemplate = new TestRestTemplate( restTemplateBuilder ) ;
		ResponseEntity<String> httpResponse = httpRestTemplate.getForEntity( httpUrl, String.class ) ;

		logger.info( "httpResponse: {} ", httpResponse ) ;

		var sslUrl = "https://localhost" + "." + webServerSettings.getSsl( ).getTestDomain( ) 
				+ ":" + webServerSettings.getSsl( ).getPort( )
				+ "/currentTime" ;
		
		
//		timeUrl = "https://csap-dev20" + "." + trustStoreDomain + ":8011/api/model/services/name" ;

		var decoded = csapEncrypt.decodeIfPossible( webServerSettings.getSsl( ).getKeystorePassword( ), logger ) ;

		logger.info( CsapApplication.testHeader( "url: {}, trustStore: {}, trustStorePassword: {}" ),
				sslUrl, webServerSettings.getSsl( ).getKeystoreFile( ), webServerSettings.getSsl( )
						.getKeystorePassword( ) ) ;

		// var restTemplate = restTemplateWithTrustStore( restTemplateBuilder ) ;

		var restSslFactory = new CsapRestTemplateFactory(
				new URL( webServerSettings.getSsl( ).getKeystoreFile( ) ),
				decoded ) ;

		var restSslTemplate = restSslFactory.buildDefaultRestTemplate( "verify_self_signed_ssl_http_get" ) ;

//		ResponseEntity<String> sslResponse = restSslTemplate.getForEntity( sslUrl, String.class ) ;
//		ResponseEntity<String> springSslResponse = restTemplate.getForEntity( httpsUrl, String.class ) ;
//		logger.info( "springSslResponse: {} ", springSslResponse ) ;
//		assertThat( sslResponse.getBody( ) ).contains( "currentTime:" ) ;

		ResponseEntity<String> customSslResponse = restSslTemplate.getForEntity( sslUrl, String.class ) ;
		logger.info( "customSslResponse: {} ", customSslResponse ) ;
		assertThat( customSslResponse.getBody( ) ).contains( "currentTime:" ) ;

	}

//	public RestTemplate restTemplateWithTrustStore ( RestTemplateBuilder builder ) throws Exception {
//
//		var sslContext = new SSLContextBuilder( )
//				
//				.loadTrustMaterial( 
//						trustStore.getURL( ), 
//						trustStorePassword.toCharArray( ) )
//				
//				.build( ) ;
//		
//		SSLConnectionSocketFactory socketFactory = new SSLConnectionSocketFactory( sslContext ) ;
//
//		HttpClient httpClient = HttpClients.custom( )
//				.setSSLSocketFactory( socketFactory )
//				.build( ) ;
//
//		return builder
//				.requestFactory( ( ) -> new HttpComponentsClientHttpRequestFactory( httpClient ) )
//				.build( ) ;
//
//	}

}
