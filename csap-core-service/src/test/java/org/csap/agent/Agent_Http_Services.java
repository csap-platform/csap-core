package org.csap.agent ;

import static org.assertj.core.api.Assertions.assertThat ;

import java.io.File ;
import java.util.List ;

import javax.inject.Inject ;

import org.apache.commons.io.FileUtils ;
import org.csap.agent.linux.OutputFileMgr ;
import org.csap.agent.linux.TransferManager ;
import org.csap.agent.model.Application ;
import org.csap.helpers.CSAP ;
import org.csap.helpers.CsapApplication ;
import org.junit.jupiter.api.BeforeAll ;
import org.junit.jupiter.api.Tag ;
import org.junit.jupiter.api.Test ;
import org.junit.jupiter.api.TestInstance ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.boot.test.context.SpringBootTest ;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment ;
import org.springframework.boot.test.web.client.TestRestTemplate ;
import org.springframework.boot.web.client.RestTemplateBuilder ;
import org.springframework.boot.web.server.LocalServerPort ;
import org.springframework.http.HttpStatus ;
import org.springframework.http.ResponseEntity ;
import org.springframework.test.annotation.DirtiesContext ;
import org.springframework.test.context.ActiveProfiles ;
import org.springframework.web.util.UriComponentsBuilder ;

import com.fasterxml.jackson.databind.JsonNode ;
import com.fasterxml.jackson.databind.ObjectMapper ;

@Tag ( "full" )

@SpringBootTest ( classes = CsapCoreService.class , webEnvironment = WebEnvironment.RANDOM_PORT )

@ActiveProfiles ( {
		"agent", CsapBareTest.PROFILE_JUNIT, "security-disabled"
} )

@DirtiesContext

@TestInstance ( TestInstance.Lifecycle.PER_CLASS )
public class Agent_Http_Services {

	Logger logger = LoggerFactory.getLogger( getClass( ) ) ;

	static {

		CsapApplication.initialize( CSAP.padNoLine( "Loading" ) + Agent_Http_Services.class.getName( ) ) ;

	}

	@LocalServerPort
	private int testPort ;

	@Inject
	Application csapApp ;

	@Inject
	ObjectMapper jacksonMapper ;

	@Inject
	RestTemplateBuilder restTemplateBuilder ;

	@BeforeAll
	public void beforeAll ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( "Started on port {}" ), testPort ) ;

		csapApp.setAutoReload( false ) ;

		assertThat( csapApp.loadDefinitionForJunits( false, Agent_context_loaded.SIMPLE_TEST_DEFINITION ) )
				.as( "No Errors or warnings" )
				.isTrue( ) ;

		var agent = csapApp.flexFindFirstInstanceCurrentHost( CsapCore.AGENT_NAME ) ;
		agent.setPort( Integer.toString( testPort ) ) ;

	}

	@Test
	void verify_agent_loaded ( ) {

		var agent = csapApp.flexFindFirstInstanceCurrentHost( CsapCore.AGENT_NAME ) ;

		logger.info( CsapApplication.testHeader( "Agent {}" ), agent.toSummaryString( ) ) ;

		var restTemplate = new TestRestTemplate( restTemplateBuilder ) ;

		var uiLandingUrl = "http://localhost:" + testPort + CsapCoreService.APP_BROWSER_URL ;

		ResponseEntity<String> landingResponse = restTemplate.getForEntity( uiLandingUrl, String.class ) ;

		assertThat( landingResponse.getStatusCode( ) ).isEqualTo( HttpStatus.OK ) ;

		logger.debug( "landingResponse: {} ", landingResponse ) ;

		var agentBrowserPage = CsapBareTest.getPage( landingResponse.getBody( ) ) ;
		assertThat( agentBrowserPage ).isNotNull( ) ;

		var environmentHeader = agentBrowserPage.getElementById( "environment-name" ) ;

		assertThat( environmentHeader ).isNotNull( ) ;
		logger.info( CsapApplication.header( "environmentHeader: {}" ), environmentHeader ) ;

		assertThat( environmentHeader.asXml( ) )
				.contains( "dev" )
				.contains( "environment-select" )
				.contains( "definition-group" )
				.contains( "discovery-group" ) ;

	}

	@Test
	void verify_transfer_manager ( ) throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		var simpleService = csapApp.flexFindFirstInstanceCurrentHost( "simple-service" ) ;

		var outputManager = new OutputFileMgr(
				csapApp.getCsapWorkingFolder( ), "/"
						+ simpleService.getName( ) + "-junit-test" ) ;

		TransferManager transferManager = new TransferManager( csapApp, 3, outputManager.getBufferedWriter( ) ) ;

		var sourceFile = new File( csapApp.getCsapInstallFolder( ), "junit-transfer-demo" ) ;

		var sourceText = "junit test of file transfer" ;
		FileUtils.writeStringToFile( sourceFile, sourceText ) ;

		transferManager.httpCopyViaCsAgent( "junit-user", sourceFile, Application.FileToken.PLATFORM.value, List.of(
				simpleService.getHostName( ) ) ) ;

		var transferReports = transferManager.waitForCompleteJson( ) ;
		logger.info( "transferReports: {}", CSAP.jsonPrint( transferReports ) ) ;

		assertThat( transferReports.path( 0 ).has( "error" ) ).isFalse( ) ;

		var destFile = new File( csapApp.getCsapInstallFolder( ).getAbsoluteFile( ), "junit-transfer-demo" ) ;

		logger.info( "destFile: {}", destFile ) ;

		assertThat( destFile ).exists( ) ;

		var destText = FileUtils.readFileToString( destFile ) ;
		assertThat( destText ).isEqualTo( sourceText ) ;

	}

	StringBuilder sampleScript = new StringBuilder( ) ;

	@Test
	public void executeScript ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		// create scriptDir
		if ( ! csapApp.getScriptDir( ).exists( ) ) {

			logger.info( "Creating scriptDir: " + csapApp.getScriptDir( ).getCanonicalPath( ) ) ;
			csapApp.getScriptDir( ).mkdirs( ) ;

		}

		sampleScript.append( "#!/bin/bash\n" ) ;
		sampleScript.append( "ls\n" ) ;

		var restTemplate = new TestRestTemplate( restTemplateBuilder ) ;
		// var executeScriptUrl = "http://localhost:" + testPort ;

		var executeScriptUrl = UriComponentsBuilder
				.fromHttpUrl( "http://localhost:" + testPort )
				.path( CsapCoreService.OS_URL + "/executeScript" )
				.queryParam( "chownUserid", "csap" )
				.queryParam( "hosts", "localhost" )
				.queryParam( "scriptName", "junit-ls.sh" )
				.queryParam( "jobId", Long.toString( System.currentTimeMillis( ) ) )
				.queryParam( "timeoutSeconds", "10" )
				.queryParam( "contents", "ls -l" )
				.build( ).toUri( ) ;

		ResponseEntity<String> executeScriptResponse = restTemplate.getForEntity( executeScriptUrl, String.class ) ;

		assertThat( executeScriptResponse.getStatusCode( ) ).isEqualTo( HttpStatus.OK ) ;

		logger.debug( "landingResponse: {} ", executeScriptResponse ) ;

		// mock does much validation.....
//		ResultActions resultActions = mockMvc.perform(
//				get( CsapCoreService.OS_URL + "/executeScript" )
//						.param( "contents", sampleScript.toString( ) )
//						.param( "chownUserid", "csap" )
//						.param( "hosts", "localhost" )
//						.param( "jobId", Long.toString( System.currentTimeMillis( ) ) )
//						.param( "scriptName", "junitTest.sh" )
//						.param( "timeoutSeconds", "30" )
//						.accept( MediaType.APPLICATION_JSON ) ) ;

		// But you could do full parsing of the Json result if needed
//		String responseJson = resultActions
//				.andExpect( status( ).isOk( ) )
//				.andExpect( content( ).contentTypeCompatibleWith( MediaType.APPLICATION_JSON ) )
//				.andReturn( )
//				.getResponse( )
//				.getContentAsString( ) ;

		// Validate response
		JsonNode executeReport = jacksonMapper.readTree( executeScriptResponse.getBody( ) ) ;

		// Print it out for generating tests using jackson
		logger.info( CSAP.jsonPrint( executeReport ) ) ;

		var scriptResults = executeReport.path( "otherHosts" ).path( 0 )
				.path( "transferResults" ).path( "scriptResults" ).path( 0 )
				.asText( ) ;

		assertThat( scriptResults ).startsWith( "junit - skipping script execute" ) ;

	}

	//
	//
	//
	//
	//
	//
	//
	//
	//
	//
	//
	//
	//
	//
	//
	//
	//
	//
	//
	//
	//
	//
	//
	//
	//
	//
	//
	//
	//
	//
	//
	//
	//
	//
	//
	//

}
