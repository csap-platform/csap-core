package test.scenario_3_performance ;

import static org.assertj.core.api.Assertions.assertThat ;

import java.util.Map ;

import javax.inject.Inject ;

import org.csap.helpers.CsapApplication ;
import org.csap.integations.CsapInformation ;
import org.junit.jupiter.api.BeforeAll ;
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
import org.springframework.http.HttpEntity ;
import org.springframework.http.HttpMethod ;
import org.springframework.http.ResponseEntity ;
import org.springframework.test.context.ActiveProfiles ;

import com.fasterxml.jackson.databind.ObjectMapper ;

@SpringBootTest ( classes = Csap_Simple_Application.class , webEnvironment = WebEnvironment.RANDOM_PORT )
@ActiveProfiles ( {
		"junit", "mockJunit"
} )
public class Csap_Health_Page_Using_Web_Environment {
	final static private Logger logger = LoggerFactory.getLogger( Csap_Health_Page_Using_Web_Environment.class ) ;

	@BeforeAll
	public static void setUpBeforeClass ( )
			throws Exception {

		CsapApplication.initialize( logger.getName( ) ) ;

	}

	ObjectMapper jacksonMapper = new ObjectMapper( ) ;

	@Inject
	RestTemplateBuilder restTemplateBuilder ;

	@Autowired
	private CsapInformation csapInfo ;

	@LocalServerPort
	private int testPort ;

	@Test
	public void validate_csap_health_using_rest_template ( )
			throws Exception {

		String healthUrl = "http://localhost:" + testPort + csapInfo.getCsapHealthUrl( ) ;

		logger.info( CsapApplication.testHeader( "Invoking: " + healthUrl ) ) ;
		// mock does much validation.....

		TestRestTemplate restTemplate = new TestRestTemplate( restTemplateBuilder ) ;

		ResponseEntity<String> response = restTemplate.getForEntity( healthUrl, String.class ) ;

		logger.info( "response: {} ", response ) ;

		assertThat( response.getBody( ) )
				.contains( "id=\"metricTable\"" ) ;
	}

	@Test
	public void validate_current_time_using_rest_template ( )
			throws Exception {

		var timeUrl = "http://localhost:" + testPort + "/currentTime" ;

		logger.info( CsapApplication.testHeader( "Invoking: " + timeUrl ) ) ;
		// mock does much validation.....

		TestRestTemplate restTemplate = new TestRestTemplate( restTemplateBuilder ) ;

		ResponseEntity<String> response = restTemplate.getForEntity( timeUrl, String.class ) ;

		logger.info( "response: {} ", response ) ;

		assertThat( response.getBody( ) )
				.contains( "currentTime:" ) ;

	}

	@Test
	public void validate_current_time_delete_using_rest_template ( )
			throws Exception {

		var timeUrl = "http://localhost:" + testPort + "/currentTime" ;

		logger.info( CsapApplication.testHeader( "Invoking: " + timeUrl ) ) ;
		// mock does much validation.....

		TestRestTemplate restTemplate = new TestRestTemplate( restTemplateBuilder ) ;

		// no response
		restTemplate.delete( timeUrl ) ;

		var timeParamUrl = "http://localhost:" + testPort + "/currentTimeParam" ;
		Map<String, String> params = Map.of( "hi", "there" ) ;
		HttpEntity<Map<String, String>> entity = new HttpEntity<Map<String, String>>( params ) ;

		ResponseEntity<String> deleteResponse = restTemplate.exchange( timeParamUrl, HttpMethod.DELETE, entity,
				String.class ) ;

		logger.info( "url: {} \n\t deleteResponse: {}", timeParamUrl, deleteResponse ) ;

	}

}
