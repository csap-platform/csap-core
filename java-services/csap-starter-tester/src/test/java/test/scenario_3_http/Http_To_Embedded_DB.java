package test.scenario_3_http ;

import static org.assertj.core.api.Assertions.assertThat ;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get ;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post ;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content ;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model ;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status ;

import java.util.List ;

import org.apache.commons.lang3.StringUtils ;
import org.csap.helpers.CSAP ;
import org.csap.helpers.CsapApplication ;
import org.junit.jupiter.api.Assertions ;
import org.junit.jupiter.api.BeforeAll ;
import org.junit.jupiter.api.BeforeEach ;
import org.junit.jupiter.api.Test ;
import org.sample.Csap_Tester_Application ;
import org.sample.jpa.DemoEvent ;
import org.sample.jpa.DemoManager ;
import org.slf4j.LoggerFactory ;
import org.springframework.beans.factory.annotation.Autowired ;
import org.springframework.boot.test.context.SpringBootTest ;
import org.springframework.http.MediaType ;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase ;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder ;
import org.springframework.test.context.ActiveProfiles ;
import org.springframework.test.context.web.WebAppConfiguration ;
import org.springframework.test.web.servlet.MockMvc ;
import org.springframework.test.web.servlet.MvcResult ;
import org.springframework.test.web.servlet.ResultActions ;
import org.springframework.test.web.servlet.setup.MockMvcBuilders ;
import org.springframework.transaction.annotation.Transactional ;
import org.springframework.web.context.WebApplicationContext ;

import com.fasterxml.jackson.core.type.TypeReference ;
import com.fasterxml.jackson.databind.JsonNode ;
import com.fasterxml.jackson.databind.ObjectMapper ;

/**
 * 
 * Tests using an external DB, note JPA context requires -javaagent see
 * ECLIPSE_SETUP. Note also use of @Transactional to roll back commits
 * 
 * @author pnightin
 * 
 * @see <a href=
 *      "http://static.springsource.org/spring/docs/3.2.x/spring-framework-reference/html/testing.html#spring-mvc-test-framework">
 *      SpringMvc Test </a>
 * 
 * @see <a href=
 *      "http://static.springsource.org/spring/docs/3.2.x/spring-framework-reference/html/testing.html#testing-introduction">
 *      Spring Test Reference Guide</a>
 * 
 * @see <a href=
 *      "http://static.springsource.org/spring/docs/3.2.x/spring-framework-reference/html/new-in-3.2.html#new-in-3.2-testing">
 *      SpringMvc Test </a>
 * 
 * 
 * @see <a href=
 *      "http://static.springsource.org/spring/docs/3.2.x/spring-framework-reference/html/new-in-3.1.html#new-in-3.1-test-context-profiles">
 *      TestContext </a>
 * 
 * 
 */

@WebAppConfiguration
@SpringBootTest ( classes = Csap_Tester_Application.class )
@ActiveProfiles ( "junit" )
@Transactional
public class Http_To_Embedded_DB {

	final static private org.slf4j.Logger logger = LoggerFactory.getLogger( CSAP_Test_Http.class ) ;

	@Autowired
	private WebApplicationContext wac ;

	private MockMvc mockMvc ;

	static private EmbeddedDatabase db ;

	@BeforeAll
	public static void setUpBeforeClass ( )
		throws Exception {

		CsapApplication.initialize( logger.getName( ) ) ;

		logger.info( "Initializing in memory db" ) ;

		// creates an HSQL in-memory database populated from default scripts
		// classpath:schema.sql and classpath:data.sql
		// db = new EmbeddedDatabaseBuilder().addDefaultScripts().build();
		db = new EmbeddedDatabaseBuilder( ).build( ) ;

	}

	@BeforeEach
	public void setUp ( )
		throws Exception {

		this.mockMvc = MockMvcBuilders.webAppContextSetup( this.wac ).build( ) ;

		logger.info( "Adding test data" ) ;

		for ( int i = 0; i < 10; i++ ) {

			DemoEvent jobScheduleInput = new DemoEvent( ) ;
			jobScheduleInput.setDemoField( "test Jndi name" ) ;
			// jobScheduleInput.setScheduleObjid(System.currentTimeMillis()); //
			// Never provide this as it is generated
			jobScheduleInput.setCategory( TEST_TOKEN ) ;
			jobScheduleInput
					.setDescription( TEST_TOKEN
							+ System.currentTimeMillis( ) ) ;

			// String message = "Inserting: " + jobScheduleInput;
			// logger.info(SpringJavaConfig_GlobalContextTest.TC_HEAD +
			// message);

			jobScheduleInput = demo_db_manager.addSchedule( jobScheduleInput ) ;
			// logger.info("Result: " + jobScheduleInput);

			// Progress indicator

			Assertions.assertTrue( jobScheduleInput.getId( ) >= 0 ) ;

		}

		logger.info( "Test Data populated with 10 records\n\n" ) ;

	}

	private static String TEST_TOKEN = "MvcTestToken" ;

	ObjectMapper jacksonMapper = new ObjectMapper( ) ;

	@Autowired
	private DemoManager demo_db_manager ;

	/**
	 * 
	 * Painful - but there is no simple way to validate unit tests that contain JSP.
	 * Here we have added a JSON generator to the controller.
	 * 
	 * Most apps now use javascript with JSON eg. a jquery expression:
	 * $.getJSON("someUrl", "urlParams")
	 * 
	 * @throws Exception
	 * 
	 */
	@Test
	public void get_test_data ( )
		throws Exception {

		String message = "Hitting controller to hit JPA to get data..... returns JSON" ;
		logger.info( CsapApplication.TC_HEAD + message ) ;

		// mock does much validation.....
		ResultActions resultActions = mockMvc.perform(
				get( Csap_Tester_Application.API_URL + "/showTestDataJson" )
						.param( "filter", TEST_TOKEN )
						.accept( MediaType.APPLICATION_JSON ) ) ;

		String responseText = resultActions.andReturn( ).getResponse( ).getContentAsString( ) ;
		logger.info( "responseText: {}", responseText ) ;

		// But you could do full parsing of the Json result if needed
		JsonNode responseJsonNode = jacksonMapper
				.readTree( responseText ) ;
		logger.info(
				"result:\n" + jacksonMapper.writerWithDefaultPrettyPrinter( ).writeValueAsString( responseJsonNode ) ) ;

		assertThat( responseJsonNode.get( "count" ).asInt( ) )
				.isEqualTo( 10 ) ;
		// MvcResult mvcResult = resultActions
		// .andExpect( status().isOk() )
		// .andExpect( content().contentType( MediaType.APPLICATION_JSON
		// ) )
		// .andExpect( jsonPath( "$.count" ).exists() )
		// .andExpect( jsonPath( "$.data" ).exists() ).andReturn();

		// Mock validates the existence. But we can get very explicit using the
		// result

	}

	@Test
	public void http_get_and_validate_html ( )
		throws Exception {

		String message = "Hitting controller to hit JPA to get data.....uses html and returns Spring Model" ;
		logger.info( CsapApplication.TC_HEAD + message ) ;

		// mock validates http response code and that mvc attribute was created
		MvcResult mvcResult = mockMvc.perform(
				get( Csap_Tester_Application.SPRINGAPP_URL + "/showTestData" )
						.param( "filter", TEST_TOKEN ).accept( MediaType.TEXT_HTML ) )
				.andExpect( status( ).isOk( ) )
				.andExpect( model( ).attributeExists( "result" ) )
				.andReturn( ) ;

		String result = mvcResult.getModelAndView( ).getModel( ).get( "result" ).toString( ) ;
		logger.info( "result:\n" + result ) ;

		assertThat( StringUtils.countMatches( result, "description=" + TEST_TOKEN ) )
				.isEqualTo( 10 ) ;

	}

	@Test
	public void http_get_count_using_ezJpa ( )
		throws Exception {

		String message = "Hitting controller to hit JPA to get data..... returns JSON" ;
		logger.info( CsapApplication.TC_HEAD + message ) ;

		// mock does much validation.....
		ResultActions resultActions = mockMvc.perform(
				get( Csap_Tester_Application.SPRINGREST_URL + "/getRecordCountEz" )
						.param( "filter", TEST_TOKEN )
						.accept( MediaType.APPLICATION_JSON ) ) ;

		// But you could do full parsing of the Json result if needed
		JsonNode responseJsonNode = jacksonMapper
				.readTree( resultActions.andReturn( ).getResponse( ).getContentAsString( ) ) ;
		logger.info(
				"result:\n" + jacksonMapper.writerWithDefaultPrettyPrinter( ).writeValueAsString( responseJsonNode ) ) ;

		// MvcResult mvcResult = resultActions
		// .andExpect( status().isOk() )
		// .andExpect( content().contentType( MediaType.APPLICATION_JSON ) )
		// .andExpect( jsonPath( "$.count" ).exists() ).andReturn();

		// Mock validates the existence. But we can get very explicit using the
		// result

		assertThat( responseJsonNode.get( "count" ).asInt( ) )
				.isEqualTo( 10 ) ;

	}

	@Test
	public void http_get_secure_property ( )
		throws Exception {

		String message = "Hitting controller to get decrypted property..... returns JSON" ;
		logger.info( CsapApplication.TC_HEAD + message ) ;

		// mock does much validation.....
		ResultActions resultActions = mockMvc.perform(
				get( Csap_Tester_Application.SPRINGREST_URL + "/showSecureConfiguration" )
						.accept( MediaType.APPLICATION_JSON ) ) ;

		// But you could do full parsing of the Json result if needed
		JsonNode responseJsonNode = jacksonMapper
				.readTree( resultActions.andReturn( ).getResponse( ).getContentAsString( ) ) ;
		logger.info(
				"result:\n" + jacksonMapper.writerWithDefaultPrettyPrinter( ).writeValueAsString( responseJsonNode ) ) ;

		resultActions
				.andExpect( status( ).isOk( ) )
				.andExpect( content( ).contentType( MediaType.APPLICATION_JSON ) ).andReturn( ) ;
		// .andExpect(jsonPath("$.count").exists()).andReturn() ;

		// Mock validates the existence. But we can get very explicit using the
		// result

		// Assert properties read from secureSample.properties file. password is
		// scrambled enc(....) in file, but decrypted by property loader in
		// SecureProperties.java
		assertThat( responseJsonNode.get( "factorySample.madeup.password" ).asText( ) )
				.as( "Stubbed out as it is very atypical" )
				.isEqualTo( "$secure{factorySample.madeup.password}" ) ;

		// Assert clear text for user
		assertThat( responseJsonNode.get( "factorySample.madeup.user" ).asText( ) )
				.as( "Stubbed out as it is very atypical" )
				.isEqualTo( "$secure{factorySample.madeup.user}" ) ;

	}

	@SuppressWarnings ( "unchecked" )
	@Test
	public void add_bulk_data ( )
		throws Exception {

		String message = "Hitting controller to hit JPA to add data..... returns JSON" ;
		logger.info( CsapApplication.TC_HEAD + message ) ;

		// mock does much validation.....
		ResultActions resultActions = mockMvc.perform(
				post( Csap_Tester_Application.SPRINGREST_URL + "/addBulkData" )
						.param( "filter", TEST_TOKEN )
						.param( "message", "Junittest" )
						.param( "count", "5" )
						.param( "payloadPadding", "yes" )
						.accept( MediaType.APPLICATION_JSON ) ) ;

		// MvcResult mvcResult = resultActions
		// .andExpect( status().isOk() )
		// .andExpect( content().contentType( MediaType.APPLICATION_JSON ) )
		// .andExpect( jsonPath( "$.recordsAdded" ).exists() ).andReturn();

		// But you could do full parsing of the Json result if needed
		// String responseString = mvcResult.getResponse().getContentAsString();
		JsonNode responseJsonNode = jacksonMapper
				.readTree( resultActions.andReturn( ).getResponse( ).getContentAsString( ) ) ;

		logger.info( "result:\n {}",
				CSAP.jsonPrint( responseJsonNode ) ) ;

		assertThat(
				(List<String>) jacksonMapper.readValue(
						responseJsonNode.at( "/recordsAdded" )
								.traverse( ),
						new TypeReference<List<String>>( ) {
						} ) )
								.hasSize( 5 ) ;

		// List<String> records = JsonPath.read( responseString,
		// "$.recordsAdded" );
		// assertEquals( "Insert Count", 5, records.size() );
		// assertTrue( "Contains padded fields", records.get( 0 ).contains(
		// SpringRequests.DATA_200 ) );

	}

	@Test
	public void http_get_test_data_as_json ( )
		throws Exception {

		String message = "Hitting controller to hit JPA to get data..... returns JSON" ;
		logger.info( CsapApplication.TC_HEAD + message ) ;

		// mock does much validation.....
		ResultActions resultActions = mockMvc.perform(
				get( Csap_Tester_Application.API_URL + "/showTestDataJson" )
						.param( "filter", TEST_TOKEN )
						.param( "pageSize", "50" )
						.param( "count", "5" )
						.accept( MediaType.APPLICATION_JSON ) ) ;

		MvcResult mvcResult = resultActions
				.andExpect( status( ).isOk( ) )
				.andExpect( content( ).contentType( MediaType.APPLICATION_JSON ) )
				.andReturn( ) ;
		// .andExpect( jsonPath( "$.iterationInMs" ).exists()
		// ).andReturn();

		// But you could do full parsing of the Json result if needed
		String responseString = mvcResult.getResponse( ).getContentAsString( ) ;
		JsonNode responseJsonNode = jacksonMapper.readTree( responseString ) ;
		logger.info( "result:\n"
				+ jacksonMapper.writerWithDefaultPrettyPrinter( ).writeValueAsString(
						responseJsonNode ) ) ;

		assertThat(
				(List<String>) jacksonMapper.readValue(
						responseJsonNode.at( "/iterationInMs" )
								.traverse( ),
						new TypeReference<List<String>>( ) {
						} ) )
								.hasSize( 5 ) ;

	}
}
