package org.csap.agent.full.ui ;

import static org.assertj.core.api.Assertions.assertThat ;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get ;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content ;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status ;

import java.io.File ;
import java.io.FileOutputStream ;
import java.io.OutputStream ;

import javax.inject.Inject ;

import org.apache.commons.io.FileUtils ;
import org.csap.agent.Agent_context_loaded ;
import org.csap.agent.CsapBareTest ;
import org.csap.agent.CsapCore ;
import org.csap.agent.CsapCoreService ;
import org.csap.agent.api.AgentApi ;
import org.csap.agent.linux.ZipUtility ;
import org.csap.agent.model.Application ;
import org.csap.agent.ui.rest.HostRequests ;
import org.csap.helpers.CSAP ;
import org.csap.helpers.CsapApplication ;
import org.junit.jupiter.api.Assertions ;
import org.junit.jupiter.api.BeforeAll ;
import org.junit.jupiter.api.DisplayName ;
import org.junit.jupiter.api.Tag ;
import org.junit.jupiter.api.Test ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.beans.factory.annotation.Autowired ;
import org.springframework.http.MediaType ;
import org.springframework.test.web.servlet.MockMvc ;
import org.springframework.test.web.servlet.ResultActions ;
import org.springframework.test.web.servlet.setup.MockMvcBuilders ;
import org.springframework.web.context.WebApplicationContext ;

import com.fasterxml.jackson.databind.JsonNode ;
import com.fasterxml.jackson.databind.ObjectMapper ;

@Tag ( "full" )
@CsapBareTest.Agent_Full
class Host_Operations_Test {

	Logger logger = LoggerFactory.getLogger( getClass( ) ) ;

	static {

		CsapApplication.initialize( "" ) ;

	}

	@Autowired
	private WebApplicationContext wac ;

	private MockMvc mockMvc ;

	@Inject
	Application csapApp ;

	@BeforeAll
	void beforeAll ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		csapApp.getProjectLoader( ).setAllowLegacyNames( true ) ;

		this.mockMvc = MockMvcBuilders.webAppContextSetup( this.wac ).build( ) ;
		csapApp.setTestModeToSkipActivate( true ) ;
		csapApp.setAutoReload( false ) ;

		logger.info( "Deleting: {}", csapApp.getHttpdIntegration( ).getHttpdWorkersFile( ).getParentFile( )
				.getAbsolutePath( ) ) ;

		FileUtils.deleteQuietly( csapApp.getHttpdIntegration( ).getHttpdWorkersFile( ).getParentFile( ) ) ;

		File csapApplicationDefinition = new File(
				getClass( ).getResource( Agent_context_loaded.TEST_DEFINITIONS + "test_application_model.json" )
						.getPath( ) ) ;

		logger.info( "Loading test configuration: {}", csapApplicationDefinition ) ;

		assertThat( csapApp.loadDefinitionForJunits( false, csapApplicationDefinition ) ).as( "No Errors or warnings" )
				.isTrue( ) ;

		csapApp.getOsManager( ).wait_for_initial_process_status_scan( 3 ) ;

	}

	ObjectMapper jacksonMapper = new ObjectMapper( ) ;

	@Test
	public void validate_get_cpu ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		assertThat( csapApp.metricManager( ).isCollectorsStarted( ) ).isTrue( ) ;

		// mock does much validation.....
		ResultActions resultActions = mockMvc.perform(
				get( CsapCoreService.OS_URL + "/processes/csap" )
						.accept( MediaType.APPLICATION_JSON ) ) ;

		String responseText = resultActions
				.andExpect( status( ).isOk( ) )
				.andExpect( content( ).contentTypeCompatibleWith( MediaType.APPLICATION_JSON ) )
				.andReturn( )
				.getResponse( )
				.getContentAsString( ) ;

		// Validate response
		JsonNode responseJson = jacksonMapper.readTree( responseText ) ;

		logger.debug( "result:\n" + CSAP.jsonPrint( responseJson ) ) ;

		assertThat( responseJson.at( "/mp/all/intr" ).asDouble( ) )
				.isEqualTo( 1005.50 ) ;

		assertThat( responseJson.at( "/ps/" + CsapCore.AGENT_NAME + "/containers/0/currentProcessPriority" ).asInt( ) )
				.isEqualTo( -12 ) ;

	}

	@Test
	@DisplayName ( "zip a folder" )
	public void verifyFolderZip ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		String testPath = "junit-test.txt" ;
		String testContents = "hi" ;
		File testFile = new File( System.getProperty( "user.home" ), testPath ) ;
		FileUtils.writeStringToFile( testFile, testContents, false ) ;

		// path=.kube&token=584t76.b0b7c7r75rbc0ml0&service=kubelet_8014
		ResultActions resultActions = mockMvc.perform(
				get( CsapCoreService.OS_URL + HostRequests.FOLDER_ZIP_URL )
						.param( "path", testPath )
						.param( "token", "junit-desktop" )
						.param( "service", CsapCore.AGENT_ID )
						.accept( MediaType.APPLICATION_OCTET_STREAM ) ) ;

		byte[] zipBytes = resultActions
				.andExpect( status( ).isOk( ) )
				.andExpect( content( ).contentTypeCompatibleWith( MediaType.APPLICATION_OCTET_STREAM ) )
				.andReturn( )
				.getResponse( )
				.getContentAsByteArray( ) ;

		File testZipFile = new File( System.getProperty( "user.home" ), testPath + ".zip" ) ;

		try ( OutputStream opStream = new FileOutputStream( testZipFile ) ) {

			opStream.write( zipBytes ) ;
			opStream.flush( ) ;

		}

		File extractDir = new File( System.getProperty( "user.home" ), testPath + "-folder" ) ;
		ZipUtility.unzip( testZipFile, extractDir ) ;

		File extractFile = new File( extractDir, testPath ) ;
		String stringFromZipUnzippedAndRead = FileUtils.readFileToString( extractFile ) ;

		logger.info( "response: {}", stringFromZipUnzippedAndRead ) ;

		assertThat( stringFromZipUnzippedAndRead ).isEqualTo( testContents ) ;

	}

	@Test
	public void agent_api_runtime ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		// mock does much validation.....
		ResultActions resultActions = mockMvc.perform(
				get( CsapCoreService.API_AGENT_URL + AgentApi.RUNTIME_URL )
						.param( "resetCache", "no" )
						.accept( MediaType.APPLICATION_JSON ) ) ;

		// But you could do full parsing of the Json result if needed
		String responseJson = resultActions
				.andExpect( status( ).isOk( ) )
				.andExpect( content( ).contentTypeCompatibleWith( MediaType.APPLICATION_JSON ) )
				.andReturn( )
				.getResponse( )
				.getContentAsString( ) ;

		// Validate response
		JsonNode jsonResults = jacksonMapper.readTree( responseJson ) ;

		// Print it out for generating tests using jackson
		logger.info( CSAP.jsonPrint( jsonResults ) ) ;

		Assertions.assertTrue( jsonResults.at( "/hostStats" ) != null ) ;

		assertThat( jsonResults.at( "/hostStats/df/" + SLASH_JSON_POINTER_ESCAPE ).asText( ) )
				.as( "df output" )
				.isEqualTo( "36%" ) ;

	}

	// @see JsonPointer
	final public static String SLASH_JSON_POINTER_ESCAPE = "~1" ;

}
