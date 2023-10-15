package test.scenario_3_http ;

import static org.assertj.core.api.Assertions.assertThat ;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get ;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content ;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status ;

import java.util.concurrent.TimeUnit ;

import javax.inject.Inject ;

import org.csap.helpers.CSAP ;
import org.csap.helpers.CsapApplication ;
import org.junit.jupiter.api.Assertions ;
import org.junit.jupiter.api.BeforeEach ;
import org.junit.jupiter.api.Test ;
import org.junit.jupiter.api.TestInstance ;
import org.sample.Csap_Tester_Application ;
import org.sample.DatabaseSettings ;
import org.sample.input.http.ui.rest.MsgAndDbRequests ;
import org.sample.input.http.ui.rest.PostgressMonitoring ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.beans.factory.annotation.Autowired ;
import org.springframework.boot.test.context.SpringBootTest ;
import org.springframework.http.MediaType ;
import org.springframework.test.context.ActiveProfiles ;
import org.springframework.test.context.web.WebAppConfiguration ;
import org.springframework.test.web.servlet.MockMvc ;
import org.springframework.test.web.servlet.ResultActions ;
import org.springframework.test.web.servlet.setup.MockMvcBuilders ;
import org.springframework.web.context.WebApplicationContext ;

import com.fasterxml.jackson.databind.JsonNode ;
import com.fasterxml.jackson.databind.ObjectMapper ;
import com.fasterxml.jackson.databind.node.ObjectNode ;

@SpringBootTest ( classes = Csap_Tester_Application.class )
@WebAppConfiguration
@ActiveProfiles ( "junit" )

@TestInstance ( TestInstance.Lifecycle.PER_CLASS )
public class CSAP_Test_Http {

	Logger logger = LoggerFactory.getLogger( getClass( ) ) ;

	static {

		CsapApplication.initialize( "Test Setup Complete" ) ;

	}

	@Autowired
	private WebApplicationContext wac ;

	private MockMvc mockMvc ;

	@BeforeEach
	public void setUp ( )
		throws Exception {

		this.mockMvc = MockMvcBuilders.webAppContextSetup( this.wac ).build( ) ;

	}

	private static String TEST_TOKEN = "MvcTestToken" ;

	@Test
	public void http_get_landing_page ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		// mock does much validation.....
		ResultActions resultActions = mockMvc.perform(
				get( "/" )
						.param( "sampleParam1", "sampleValue1" )
						.param( "sampleParam2", "sampleValue2" )
						.accept( MediaType.TEXT_PLAIN ) ) ;

		//
		String result = resultActions
				.andExpect( status( ).isOk( ) )
				.andExpect( content( ).contentType( "text/html;charset=UTF-8" ) )
				.andReturn( ).getResponse( ).getContentAsString( ) ;
		logger.info( "First 100 characters:\n {} ", result.substring( 0, 100 ) ) ;

		assertThat( result )
				.contains( "hello" ) ;

	}

	ObjectMapper jacksonMapper = new ObjectMapper( ) ;

	@Test
	public void http_get_cached_endpoint ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		// mock does much validation.....
		ResultActions resultActions = mockMvc.perform(
				get( Csap_Tester_Application.API_URL + "/simpleCacheExample" )
						.param( "key", TEST_TOKEN )
						.accept( MediaType.APPLICATION_JSON ) ) ;

		// But you could do full parsing of the Json result if needed
		ObjectNode simpleCacheResponse1 = (ObjectNode) jacksonMapper
				.readTree( resultActions.andReturn( ).getResponse( ).getContentAsString( ) ) ;

		logger.info( "simpleCacheResponse1: {}", CSAP.jsonPrint( simpleCacheResponse1 ) ) ;

		// MvcResult mvcResult = resultActions
		// .andExpect( status().isOk() )
		// .andExpect( content().contentType( MediaType.APPLICATION_JSON ) )
		// .andExpect( jsonPath( "$.key" ).exists() )
		// .andExpect( jsonPath( "$.message" ).exists() ).andReturn();

		// Mock validates the existence. But we can get very explicit using the
		// result

		TimeUnit.SECONDS.sleep( 2 ) ;
		Assertions.assertTrue( simpleCacheResponse1.get( "key" ).asText( ).equals( TEST_TOKEN ) ) ;

		resultActions = mockMvc.perform(
				get( Csap_Tester_Application.API_URL + "/simpleCacheExample" )
						.param( "key", TEST_TOKEN )
						.accept( MediaType.APPLICATION_JSON ) ) ;

		// But you could do full parsing of the Json result if needed
		ObjectNode simpleCacheResponse2 = (ObjectNode) jacksonMapper
				.readTree( resultActions.andReturn( ).getResponse( ).getContentAsString( ) ) ;
		logger.info( "simpleCacheResponse2: {}", CSAP.jsonPrint( simpleCacheResponse2 ) ) ;

		// mvcResult = resultActions
		// .andExpect( status().isOk() )
		// .andExpect( content().contentType( MediaType.APPLICATION_JSON ) )
		// .andExpect( jsonPath( "$.key" ).exists() )
		// .andExpect( jsonPath( "$.message" ).exists() ).andReturn();

		// ensure cached entry returned

		assertThat( simpleCacheResponse1.get( "timestamp" ).asText( ) )
				.isEqualTo( simpleCacheResponse2.get( "timestamp" ).asText( ) ) ;

	}

	@Test
	public void http_get_hello_endpoint ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		// mock does much validation.....
		ResultActions resultActions = mockMvc.perform(
				get( Csap_Tester_Application.SPRINGREST_URL + "/hello" )
						.param( "sampleParam1", "sampleValue1" )
						.param( "sampleParam2", "sampleValue2" )
						.accept( MediaType.TEXT_PLAIN ) ) ;

		//
		String result = resultActions
				.andExpect( status( ).isOk( ) )
				.andExpect( content( ).contentType( "text/plain;charset=UTF-8" ) )
				.andReturn( ).getResponse( ).getContentAsString( ) ;
		logger.info( "result:\n" + result ) ;

		assertThat( result )
				.contains( "hello" ) ;

	}

	@Test
	public void http_get_endpoint_using_java_8_lamdas ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		// mock does much validation.....
		ResultActions resultActions = mockMvc.perform(
				get( Csap_Tester_Application.SPRINGREST_URL + "/helloJava8" )
						.param( "sampleParam1", "sampleValue1" )
						.param( "sampleParam2", "sampleValue2" )
						.accept( MediaType.TEXT_PLAIN ) ) ;

		//
		String result = resultActions
				.andExpect( status( ).isOk( ) )
				.andExpect( content( ).contentType( "text/plain;charset=UTF-8" ) )
				.andReturn( ).getResponse( ).getContentAsString( ) ;
		logger.info( "result:\n" + result ) ;

		assertThat( result )
				.contains( MsgAndDbRequests.JAVA8_MESSAGE + "123" ) ;

	}

	@Inject
	DatabaseSettings dbSettings ;

	@Test
	public void verify_db_performance ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		// mock does much validation.....
		ResultActions resultActions = mockMvc.perform(
				get( Csap_Tester_Application.DB_MONITOR_URL + PostgressMonitoring.DB_STATS_URL )
						.accept( MediaType.APPLICATION_JSON ) ) ;

		//
		String responseString = resultActions
				.andExpect( status( ).isOk( ) )
				.andExpect( content( ).contentType( MediaType.APPLICATION_JSON ) )
				.andReturn( ).getResponse( ).getContentAsString( ) ;

		JsonNode responseJsonNode = jacksonMapper.readTree( responseString ) ;

		logger.info( "result: {}", CSAP.jsonPrint( responseJsonNode ) ) ;

		assertThat( responseJsonNode.at( "/settings/password" ).asText( ) )
				.as( "Verifying password masked" )
				.isEqualTo( "masked" ) ;

	}
}
