package org.csap.agent.full.project ;

import static org.assertj.core.api.Assertions.assertThat ;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post ;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content ;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status ;

import java.io.File ;

import javax.inject.Inject ;

import org.apache.commons.io.FileUtils ;
import org.csap.agent.Agent_context_loaded ;
import org.csap.agent.CsapBareTest ;
import org.csap.agent.CsapConstants ;
import org.csap.agent.container.C7 ;
import org.csap.agent.integrations.VersionControl ;
import org.csap.agent.model.Application ;
import org.csap.agent.ui.editor.DefinitionRequests ;
import org.csap.helpers.CSAP ;
import org.csap.helpers.CsapApplication ;
import org.junit.jupiter.api.Assertions ;
import org.junit.jupiter.api.BeforeAll ;
import org.junit.jupiter.api.DisplayName ;
import org.junit.jupiter.api.Nested ;
import org.junit.jupiter.api.Tag ;
import org.junit.jupiter.api.Test ;
import org.junit.jupiter.api.TestInstance ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.core.io.ClassPathResource ;
import org.springframework.http.MediaType ;
import org.springframework.test.web.servlet.MockMvc ;
import org.springframework.test.web.servlet.ResultActions ;
import org.springframework.test.web.servlet.setup.MockMvcBuilders ;
import org.springframework.web.context.WebApplicationContext ;

import com.fasterxml.jackson.databind.JsonNode ;
import com.fasterxml.jackson.databind.ObjectMapper ;
import com.fasterxml.jackson.databind.node.ArrayNode ;

@Tag ( "full" )
@CsapBareTest.Agent_Full
class Application_Editing {

	Logger logger = LoggerFactory.getLogger( getClass( ) ) ;

	static {

		CsapApplication.initialize( CSAP.padNoLine( "Loading" ) + Agent_context_loaded.class.getName( ) ) ;

	}

	@Inject
	Application csapApp ;

	@Inject
	ObjectMapper jacksonMapper ;

	@Inject
	WebApplicationContext wac ;
	MockMvc mockMvc ;

	@BeforeAll
	void beforeAll ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		this.mockMvc = MockMvcBuilders.webAppContextSetup( this.wac ).build( ) ;

		csapApp.setAutoReload( false ) ;
		csapApp.setTestModeToSkipActivate( true ) ;
		csapApp.getProjectLoader( ).setAllowLegacyNames( true ) ;
		logger.info( CsapApplication.testHeader( ) ) ;

		logger.info( "Deleting: {}", csapApp.getHttpdIntegration( ).getHttpdWorkersFile( ).getParentFile( )
				.getAbsolutePath( ) ) ;
		FileUtils.deleteQuietly( csapApp.getHttpdIntegration( ).getHttpdWorkersFile( ).getParentFile( ) ) ;

	}

	@Nested
	@DisplayName ( "root model" )
	@TestInstance ( TestInstance.Lifecycle.PER_CLASS )
	class Main_Project {

		@BeforeAll
		void beforeAll ( )
			throws Exception {

			File csapApplicationDefinition = new File(
					getClass( ).getResource( Agent_context_loaded.TEST_DEFINITIONS + "test_application_model.json" )
							.getPath( ) ) ;
//			csapApplicationDefinition = new File(
//					getClass( ).getResource( Agent_context_loaded.TEST_DEFINITIONS + "application_with_warnings.json" )
//							.getPath( ) ) ;

			logger.info( "Loading test configuration: {}", csapApplicationDefinition ) ;

			assertThat( csapApp.loadDefinitionForJunits( false, csapApplicationDefinition ) ).as(
					"No Errors or warnings" ).isTrue( ) ;

		}

		@Test
		public void getSecurePropertiesFile ( )
			throws Exception {

			String message = "Hitting controller to get convert property..... returns JSON" ;
			logger.info( CsapApplication.TC_HEAD + message ) ;

			String contents = FileUtils.readFileToString( ( new ClassPathResource(
					"csapSecurity.properties" ) ).getFile( ) ) ;
			logger.info( contents ) ;
			// mock does much validation.....
			ResultActions resultActions = mockMvc.perform(
					post( CsapConstants.ENCODE_FULL_URL )
							.param( "propertyFileContents", contents )
							.accept( MediaType.APPLICATION_JSON ) ) ;

			//
			JsonNode responseJsonNode = jacksonMapper.readTree(
					resultActions
							.andExpect( status( ).isOk( ) )
							.andExpect( content( ).contentTypeCompatibleWith( MediaType.APPLICATION_JSON ) )
							.andReturn( )
							.getResponse( )
							.getContentAsString( ) ) ;

			logger.info( "result:\n"
					+ CSAP.jsonPrint(
							responseJsonNode ) ) ;

			// Mock validates the existence. But we can get very explicit using the
			// result
			// Assert properties read from secureSample.properties file. password is
			// scrambled enc(....) in file, but decrypted by property loader in
			// SecureProperties.java
			Assertions.assertTrue( responseJsonNode.get( "converted" ) != null ) ;

			ArrayNode convertedArray = (ArrayNode) responseJsonNode.get( "converted" ) ;
			Assertions.assertTrue( convertedArray.size( ) > 1 ) ;

			Assertions.assertTrue( ! convertedArray.get( 0 ).get( "key" ).asText( ).equals( "" ) ) ;

		}

		/**
		 *
		 * Scenario: CS_AP UI supports encrypting a single value
		 *
		 * Verify: REST API gets values
		 *
		 * @throws Exception
		 */
		@Test
		public void getSecureSingleValue ( )
			throws Exception {

			String message = "Hitting controller to get convert property..... returns JSON" ;
			logger.info( CsapApplication.TC_HEAD + message ) ;

			String contents = "testValue" ;
			logger.info( contents ) ;
			// mock does much validation.....
			ResultActions resultActions = mockMvc.perform(
					post( CsapConstants.ENCODE_FULL_URL )
							.param( "propertyFileContents", contents )
							.accept( MediaType.APPLICATION_JSON ) ) ;

			JsonNode responseJsonNode = jacksonMapper.readTree(
					resultActions
							.andExpect( status( ).isOk( ) )
							.andExpect( content( ).contentTypeCompatibleWith( MediaType.APPLICATION_JSON ) )
							.andReturn( )
							.getResponse( )
							.getContentAsString( ) ) ;

			logger.info( "result:\n"
					+ CSAP.jsonPrint(
							responseJsonNode ) ) ;

			// Assert properties read from secureSample.properties file. password is
			// scrambled enc(....) in file, but decrypted by property loader in
			// SecureProperties.java
			Assertions.assertTrue( responseJsonNode.get( "converted" ) != null ) ;

			ArrayNode convertedArray = (ArrayNode) responseJsonNode.get( "converted" ) ;
			Assertions.assertTrue( convertedArray.size( ) == 1 ) ;

			Assertions.assertTrue( ! convertedArray.get( 0 ).get( "key" ).asText( ).equals( "" ) ) ;

		}

		@Test
		public void validate_application_model ( )
			throws Exception {

			File csapApplicationDefinition = new File(
					getClass( ).getResource( Agent_context_loaded.TEST_DEFINITIONS + "test_application_model.json" )
							.getPath( ) ) ;

			csapApp.configureForDefinitionOperationsTest( csapApplicationDefinition ) ;

			String message = CsapApplication.TC_HEAD + "Validating a  config : \n" + csapApplicationDefinition ;
			logger.info( message ) ;

			// mock does much validation.....
			ResultActions resultActions = mockMvc.perform(
					post( CsapConstants.DEFINITION_URL + "/validateDefinition" )
							.param( CsapConstants.PROJECT_PARAMETER, "SampleDefaultPackage" )
							.param( "updatedConfig",
									FileUtils.readFileToString( csapApplicationDefinition ) )
							.accept( MediaType.APPLICATION_JSON ) ) ;

			JsonNode responseJsonNode = jacksonMapper.readTree(
					resultActions
							.andExpect( status( ).isOk( ) )
							.andExpect( content( ).contentTypeCompatibleWith( MediaType.APPLICATION_JSON ) )
							.andReturn( )
							.getResponse( )
							.getContentAsString( ) ) ;

			logger.info( "result:\n"
					+ CSAP.jsonPrint(
							responseJsonNode ) ) ;

			Assertions.assertTrue( responseJsonNode.get( "success" ).asBoolean( ) ) ;
			Assertions.assertTrue( 0 == responseJsonNode.get( "errors" ).size( ) ) ;
			Assertions.assertTrue( 0 == responseJsonNode.get( "warnings" ).size( ) ) ;

			//
			//
			File workingFolder = new File( csapApp.getRootModelBuildLocation( )
					+ VersionControl.CONFIG_SUFFIX_FOR_UPDATE ) ;

			File copiedPackage = new File( workingFolder, csapApplicationDefinition.getName( ) ) ;

			logger.info( "original path: " + csapApplicationDefinition.getAbsolutePath( )
					+ "\n cluster File path: " + copiedPackage.getAbsolutePath( ) ) ;
			Assertions.assertTrue(
					csapApplicationDefinition.length( ) == copiedPackage.length( ) ) ;

		}

		/**
		 *
		 * Most basic operation: apply a basic definition file
		 *
		 * @throws Exception
		 */
		@Test
		public void validateDefinitionWithWarnings ( )
			throws Exception {

			File csapApplicationDefinition = new File(
					getClass( ).getResource( Agent_context_loaded.TEST_DEFINITIONS + "application_with_warnings.json" )
							.getPath( ) ) ;

			csapApp.configureForDefinitionOperationsTest( csapApplicationDefinition ) ;

			String message = CsapApplication.TC_HEAD + "Validating a  config : \n" + csapApplicationDefinition ;
			logger.info( message ) ;

			// mock does much validation.....
			ResultActions resultActions = mockMvc.perform(
					post( CsapConstants.DEFINITION_URL + "/validateDefinition" )
							.param( CsapConstants.PROJECT_PARAMETER, "SampleDefaultPackage" )
							.param( "updatedConfig",
									FileUtils.readFileToString( csapApplicationDefinition ) )
							.accept( MediaType.APPLICATION_JSON ) ) ;

			JsonNode responseJsonNode = jacksonMapper.readTree(
					resultActions
							.andExpect( status( ).isOk( ) )
							.andExpect( content( ).contentTypeCompatibleWith( MediaType.APPLICATION_JSON ) )
							.andReturn( )
							.getResponse( )
							.getContentAsString( ) ) ;

			logger.info( "result:\n"
					+ CSAP.jsonPrint(
							responseJsonNode ) ) ;

			assertThat( responseJsonNode.get( "success" ).asBoolean( ) )
					.as( "Validatation Success" )
					.isTrue( ) ;

			Assertions.assertTrue( 0 == responseJsonNode.get( "errors" ).size( ) ) ;
			Assertions.assertTrue( 1 == responseJsonNode.get( "warnings" ).size( ) ) ;

			File workingFolder = new File( csapApp.getRootModelBuildLocation( )
					+ VersionControl.CONFIG_SUFFIX_FOR_UPDATE ) ;

			File copiedPackage = new File( workingFolder, "test_application_model.json" ) ;

			logger.info( "original path: " + csapApplicationDefinition.getAbsolutePath( )
					+ "\n cluster File path: " + copiedPackage.getAbsolutePath( ) ) ;
			Assertions.assertTrue(
					csapApplicationDefinition.length( ) == copiedPackage.length( ) ) ;

		}

		/**
		 *
		 * Run Validator with a broken file
		 *
		 * @throws Exception
		 */
		@Test
		public void validateDefinitionWithJsonErrors ( )
			throws Exception {

			File csapApplicationDefinition = new File(
					getClass( ).getResource( Agent_context_loaded.TEST_DEFINITIONS
							+ "application_with_invalid_format.json" ).getPath( ) ) ;

			csapApp.configureForDefinitionOperationsTest( csapApplicationDefinition ) ;
			String message = CsapApplication.TC_HEAD + "Validating a  config : \n" + csapApplicationDefinition ;
			logger.info( message ) ;

			// mock does much validation.....
			ResultActions resultActions = mockMvc.perform(
					post( CsapConstants.DEFINITION_URL + "/validateDefinition" )
							.param( CsapConstants.PROJECT_PARAMETER, "SampleDefaultPackage" )
							.param( "updatedConfig",
									FileUtils.readFileToString( csapApplicationDefinition ) )
							.accept( MediaType.APPLICATION_JSON ) ) ;

			JsonNode responseJsonNode = jacksonMapper.readTree(
					resultActions
							.andExpect( status( ).isOk( ) )
							.andExpect( content( ).contentTypeCompatibleWith( MediaType.APPLICATION_JSON ) )
							.andReturn( )
							.getResponse( )
							.getContentAsString( ) ) ;

			logger.info( "result:\n"
					+ CSAP.jsonPrint(
							responseJsonNode ) ) ;

			Assertions.assertTrue( false == responseJsonNode.get( "success" ).asBoolean( ) ) ;
			Assertions.assertTrue( 1 == responseJsonNode.get( "errors" ).size( ) ) ;

			File workingFolder = new File( csapApp.getRootModelBuildLocation( )
					+ VersionControl.CONFIG_SUFFIX_FOR_UPDATE ) ;

			File copiedPackage = new File( workingFolder, "test_application_model.json" ) ;

			logger.info( "original path: " + csapApplicationDefinition.getAbsolutePath( ) + " length: "
					+ csapApplicationDefinition.length( )
					+ "\n cluster File path: " + copiedPackage.getAbsolutePath( ) + " length: " + copiedPackage
							.length( ) ) ;

			Assertions.assertTrue(
					csapApplicationDefinition.length( ) == copiedPackage.length( ) ) ;

		}

		/**
		 *
		 * Run Validator with a broken file
		 *
		 * @throws Exception
		 */
		@Test
		public void validate_definition_with_missing_jvm_fails_to_load ( )
			throws Exception {

			logger.info( CsapApplication.testHeader( ) ) ;

			File missingJvmProjectFile = new File(
					getClass( ).getResource( Agent_context_loaded.TEST_DEFINITIONS
							+ "application_with_jvm_missing_error.json" ).getPath( ) ) ;

			csapApp.configureForDefinitionOperationsTest( missingJvmProjectFile ) ;

			logger.info( CsapApplication.header( "http path: {}  file: {}" ), CsapConstants.DEFINITION_URL
					+ "/validateDefinition",
					missingJvmProjectFile.getAbsolutePath( ) ) ;

			// mock does much validation.....
			ResultActions resultActions = mockMvc.perform(
					post( CsapConstants.DEFINITION_URL + "/validateDefinition" )
							.param( CsapConstants.PROJECT_PARAMETER, "SampleDefaultPackage" )
							.param( "updatedConfig",
									FileUtils.readFileToString( missingJvmProjectFile ) )
							.accept( MediaType.APPLICATION_JSON ) ) ;

			var validationResults = jacksonMapper.readTree(
					resultActions
							.andExpect( status( ).isOk( ) )
							.andExpect( content( ).contentTypeCompatibleWith( MediaType.APPLICATION_JSON ) )
							.andReturn( )
							.getResponse( )
							.getContentAsString( ) ) ;

			logger.info( "result: {}", CSAP.jsonPrint( validationResults ) ) ;

			assertThat( validationResults.path( "success" ).asBoolean( ) ).isTrue( ) ;
			assertThat( validationResults.path( "warnings" ).size( ) ).isEqualTo( 1 ) ;
			assertThat( validationResults.at( "/warnings/0" ).asText( ) )
					.contains(
							"__WARN:  Service: Cssp3ReferenceMq(test_application_model.json) - Did not find service" ) ;

			File workingFolder = new File( csapApp.getRootModelBuildLocation( )
					+ VersionControl.CONFIG_SUFFIX_FOR_UPDATE ) ;

			File workingDefinitionFile = new File( workingFolder, "test_application_model.json" ) ;

			logger.info( CsapApplication.header( "Missing Jvm: size: {} path: {}, \n\t original: size: {}, Path: {} " ),
					missingJvmProjectFile.length( ),
					missingJvmProjectFile.getAbsolutePath( ),
					workingDefinitionFile.length( ),
					workingDefinitionFile.getAbsolutePath( ) ) ;

			assertThat( missingJvmProjectFile.length( ) ).isEqualTo( workingDefinitionFile.length( ) ) ;

		}

		@Test
		public void applySimpleDefinition ( )
			throws Exception {

			File csapApplicationDefinition = new File(
					getClass( ).getResource( Agent_context_loaded.TEST_DEFINITIONS + "test_application_model.json" )
							.getPath( ) ) ;

			csapApp.configureForDefinitionOperationsTest( csapApplicationDefinition ) ;

			String applyUrl = CsapConstants.DEFINITION_URL + DefinitionRequests.APPICATION_APPLY ;

			logger.info( CsapApplication.TC_HEAD + "ui test: {} : \n {}", applyUrl, csapApplicationDefinition ) ;

			// mock does much validation.....
			ResultActions resultActions = mockMvc.perform(
					post( applyUrl )
							.param( "applyButNoCheckin", "true" )
							.param( "hostName", "localhost" )
							.param( "scmBranch", "trunk" )
							.param( "scmPass", "" )
							.param( "scmUserid", "peterUser" )
							.param( "serviceName", "HostCommand" )
							.param( CsapConstants.PROJECT_PARAMETER, "SampleDefaultPackage" )
							.param( "updatedConfig",
									FileUtils.readFileToString( csapApplicationDefinition ) )
							.accept( MediaType.APPLICATION_JSON ) ) ;

			JsonNode responseJsonNode = jacksonMapper.readTree(
					resultActions
							.andExpect( status( ).isOk( ) )
							.andExpect( content( )
									.contentTypeCompatibleWith( MediaType.APPLICATION_JSON ) )
							.andReturn( )
							.getResponse( )
							.getContentAsString( ) ) ;

			String applyResult = responseJsonNode.get( C7.response_plain_text.val( ) ).asText( ) ;

			logger.info( "UI Response: \n {}", applyResult ) ;

			assertThat( applyResult )
					.as( "Response has no errors" )
					.doesNotContain( CsapConstants.CONFIG_PARSE_ERROR )
					.contains( DefinitionRequests.EMAIL_DISABLED ) ;

			File copiedPackage = new File( csapApp.getDefinitionFolder( ), "test_application_model.json" ) ;

			logger.info( "original path: " + csapApplicationDefinition.getAbsolutePath( )
					+ "\n cluster File path: " + copiedPackage.getAbsolutePath( ) ) ;
			Assertions.assertTrue(
					csapApplicationDefinition.length( ) == copiedPackage.length( ) ) ;

		}

		@Test
		public void applyDefinitionWithErrors ( )
			throws Exception {

			File csapApplicationDefinition = new File(
					getClass( ).getResource( Agent_context_loaded.TEST_DEFINITIONS
							+ "application_with_invalid_format.json" ).getPath( ) ) ;

			String message = CsapApplication.TC_HEAD
					+ "Applying a definition with a new package : \n" + csapApplicationDefinition ;

			logger.info( message ) ;

			csapApp.configureForDefinitionOperationsTest( csapApplicationDefinition ) ;

			// Hit the endpoint
			ResultActions resultActions = mockMvc.perform(
					post( CsapConstants.DEFINITION_URL + DefinitionRequests.APPICATION_APPLY )
							.param( "applyButNoCheckin", "true" )
							.param( "hostName", "localhost" )
							.param( "scmBranch", "trunk" )
							.param( "scmPass", "" )
							.param( "scmUserid", "peterUser" )
							.param( "serviceName", "HostCommand" )
							.param( CsapConstants.PROJECT_PARAMETER, "SampleDefaultPackage" )
							.param( "updatedConfig",
									FileUtils.readFileToString( csapApplicationDefinition ) )
							.accept( MediaType.APPLICATION_JSON ) ) ;

			JsonNode responseJsonNode = jacksonMapper.readTree(
					resultActions
							.andExpect( status( ).isOk( ) )
							.andExpect( content( ).contentTypeCompatibleWith( MediaType.APPLICATION_JSON ) )
							.andReturn( )
							.getResponse( )
							.getContentAsString( ) ) ;

			String opResult = responseJsonNode.get( C7.response_plain_text.val( ) ).asText( ) ;

			logger.info( "result: {}", opResult ) ;

			Assertions.assertTrue( opResult.indexOf( CsapConstants.CONFIG_PARSE_ERROR ) >= 0 ) ;

			// On junits, cluster reloads are placed in test folder, and packages
			// are
			// not reloaded.
			// We just confirm files exist
			File copiedPackage = new File( csapApp.getDefinitionFolder( ), "test_application_model.json" ) ;

			logger.info( "original path: " + csapApplicationDefinition.getAbsolutePath( )
					+ "\n cluster File path: " + copiedPackage.getAbsolutePath( ) ) ;
			Assertions.assertTrue(
					csapApplicationDefinition.length( ) != copiedPackage.length( ) ) ;

		}

		/**
		 *
		 * Scenario - load a definition with multiple sub packages
		 *
		 * Verify - that sub package definitions are loaded
		 *
		 * @throws Exception
		 */
		@Test
		public void applyDefinitionWithSubPackages ( )
			throws Exception {

			File configFile = new File(
					getClass( ).getResource( Agent_context_loaded.TEST_DEFINITIONS
							+ "sub-project-localhost/clusterConfigMultiple.json" )
							.getPath( ) ) ;

			String message = CsapApplication.TC_HEAD
					+ "Applying a definition with a new package : \n" + configFile.getAbsolutePath( ) ;
			logger.info( message ) ;

			csapApp.configureForDefinitionOperationsTest( configFile ) ;

			// mock does much validation.....
			ResultActions resultActions = mockMvc.perform(
					post( CsapConstants.DEFINITION_URL + DefinitionRequests.APPICATION_APPLY )
							.param( "applyButNoCheckin", "true" )
							.param( "hostName", "localhost" )
							.param( "scmBranch", "trunk" )
							.param( "scmPass", "" )
							.param( "scmUserid", "peterUser" )
							.param( "serviceName", "HostCommand" )
							.param( CsapConstants.PROJECT_PARAMETER, "SampleDefaultPackage" )
							.param( "updatedConfig", FileUtils.readFileToString( configFile ) )
							.accept( MediaType.APPLICATION_JSON ) ) ;

			JsonNode responseJsonNode = jacksonMapper.readTree(
					resultActions
							.andExpect( status( ).isOk( ) )
							.andExpect( content( ).contentTypeCompatibleWith( MediaType.APPLICATION_JSON ) )
							.andReturn( )
							.getResponse( )
							.getContentAsString( ) ) ;

			String opResult = responseJsonNode.get( C7.response_plain_text.val( ) ).asText( ) ;

			logger.info( "result:\n {}", opResult ) ;

			Assertions.assertTrue(
					opResult.indexOf( CsapConstants.CONFIG_PARSE_ERROR ) == -1 ) ;

			// On junits, cluster reloads are placed in test folder, and packages
			// are
			// not reloaded.
			// We just confirm files exist

			File originalPackage = new File(
					getClass( ).getResource( Agent_context_loaded.TEST_DEFINITIONS + "test_application_model.json" )
							.getPath( ) ) ;

			File copiedPackage = new File( csapApp.getDefinitionFolder( ),
					"clusterConfigMultipleA.json" ) ;

			logger.info( "original path: " + originalPackage.getAbsolutePath( )
					+ "\n cluster File path: " + configFile.getAbsolutePath( ) ) ;

			Assertions.assertTrue( originalPackage.length( ) != configFile.length( ) ) ;

			copiedPackage = new File( csapApp.getDefinitionFolder( ), "test_application_model.json" ) ;

			logger.info( "original path: " + configFile.getAbsolutePath( )
					+ "\n cluster File path: " + copiedPackage.getAbsolutePath( ) ) ;

			Assertions.assertTrue( configFile.length( ) == copiedPackage.length( ) ) ;

		}

		private String user = "someDeveloper" ;
		private String pass = "FIXME" ;

		@Test
		public void checkin_simple_definition ( )
			throws Exception {

			if ( pass.equals( "FIXME" ) ) {

				logger.warn( "Skipping Test as password is not set" ) ;
				Thread.sleep( 2000 ) ;
				return ;

			}

			File csapApplicationDefinition = new File(
					getClass( ).getResource( Agent_context_loaded.TEST_DEFINITIONS + "test_application_model.json" )
							.getPath( ) ) ;

			String message = CsapApplication.TC_HEAD + "Validating a  config : \n" + csapApplicationDefinition ;
			logger.info( message ) ;

			// mock does much validation.....
			ResultActions resultActions = mockMvc.perform(
					post( CsapConstants.DEFINITION_URL + DefinitionRequests.APPICATION_CHECKIN )
							.param( "applyButNoCheckin", "false" )
							.param( "hostName", "localhost" )
							.param( "scmBranch", "trunk" )
							.param( "scmPass", pass )
							.param( "scmUserid", user )
							.param( "serviceName", "HostCommand" )
							.param( CsapConstants.PROJECT_PARAMETER, "SampleDefaultPackage" )
							.param( "updatedConfig",
									FileUtils.readFileToString( csapApplicationDefinition ) )
							.accept( MediaType.APPLICATION_JSON ) ) ;

			JsonNode responseJsonNode = jacksonMapper.readTree(
					resultActions
							.andExpect( status( ).isOk( ) )
							.andExpect( content( ).contentTypeCompatibleWith( MediaType.APPLICATION_JSON ) )
							.andReturn( )
							.getResponse( )
							.getContentAsString( ) ) ;

			String opResult = responseJsonNode.get( C7.response_plain_text.val( ) ).asText( ) ;

			logger.info( "result: {} ", opResult ) ;

			assertThat( opResult )
					.as( "Result Message has no errors" )
					.doesNotContain( CsapConstants.CONFIG_PARSE_ERROR ) ;

			assertThat( opResult )
					.as( "Ensure skip message is present" )
					.contains( "Skipping checkin on Desktop" ) ;

			// Assertions.assertTrue(message,
			// responseJsonNode.get("result").asText().indexOf(Application.CONFIG_PARSE_ERROR)
			// == -1);

			File copiedPackage = new File( csapApp.getDefinitionFolder( ), "test_application_model.json" ) ;

			logger.info( "original path: " + csapApplicationDefinition.getAbsolutePath( )
					+ "\n cluster File path: " + copiedPackage.getAbsolutePath( ) ) ;
			Assertions.assertTrue(
					csapApplicationDefinition.length( ) == copiedPackage.length( ) ) ;

		}

		@Test
		public void reload_simple_definition ( )
			throws Exception {

			if ( pass.equals( "FIXME" ) ) {

				logger.warn( "Skipping Test as password is not set" ) ;
				Thread.sleep( 2000 ) ;
				return ;

			}

			assertThat( csapApp.loadDefinitionForJunits( false, Agent_context_loaded.SIMPLE_TEST_DEFINITION ) )
					.as( "No Errors or warnings" )
					.isTrue( ) ;

			logger.info( "{} \n\n source location: {}", CsapApplication.TC_HEAD, csapApp
					.getRootProjectDefinitionUrl( ) ) ;

			assertThat( csapApp.getRootProjectDefinitionUrl( ) )
					.as( "shared definition location set correctly" )
					.contains( "csap_shared_definitions" ) ;

			// mock does much validation.....
			ResultActions resultActions = mockMvc.perform(
					post( CsapConstants.DEFINITION_URL + DefinitionRequests.APPLICATION_RELOAD )
							.param( "applyButNoCheckin", "false" )
							.param( "hostName", "localhost" )
							.param( "scmBranch", "trunk" )
							.param( "scmPass", pass )
							.param( "scmUserid", user )
							.param( "serviceName", "HostCommand" )
							.param( CsapConstants.PROJECT_PARAMETER, "SampleDefaultPackage" )
							.accept( MediaType.TEXT_PLAIN_VALUE ) ) ;

			String result = resultActions
					.andExpect( status( ).isOk( ) )
					.andExpect( content( ).contentTypeCompatibleWith( MediaType.TEXT_PLAIN_VALUE ) )
					.andReturn( )
					.getResponse( )
					.getContentAsString( ) ;

			logger.info( "result:\n {}", result ) ;

			assertThat( result )
					.as( "Result Message has no errors" )
					.doesNotContain( CsapConstants.CONFIG_PARSE_ERROR ) ;

			assertThat( result )
					.as( "Reload success messages" )
					.contains( "Copying build location", "to live location",
							"definitions updated, reloads will occur within 60 seconds" ) ;

			File clusterFileName = new File( csapApp.getRootModelBuildLocation( ) ) ;
			logger.info( "clusterFileName: {}", clusterFileName.getCanonicalPath( ) ) ;
			assertThat( clusterFileName )
					.as( "clusterFile exists" )
					.exists( ) ;

			File clusterBackupFileName = new File( clusterFileName.getCanonicalPath( ) + ".old" ) ;
			logger.info( "clusterBackupFileName: {}", clusterBackupFileName.getCanonicalPath( ) ) ;
			assertThat( clusterBackupFileName )
					.as( "clusterBackupFileName exists" )
					.exists( ) ;

		}

		/**
		 *
		 * Scenario - load a definition with release package that does not exist
		 *
		 * Verify - that sub package definitions are loaded using the new package
		 * template
		 *
		 * @throws Exception
		 */
		@Test
		public void definition_with_new_package_creates_an_empty_one ( )
			throws Exception {

			//
			File csapApplicationDefinition = new File( getClass( ).getResource(
					"application-with-new-package.json" ).getPath( ) ) ;

			String message = CsapApplication.TC_HEAD
					+ "Applying a definition with a new package : \n {}" ;
			logger.info( message, csapApplicationDefinition ) ;

			csapApp.configureForDefinitionOperationsTest( csapApplicationDefinition ) ;

			/**
			 * test defintion will be store in a temp directory
			 */
			File releaseFileGenerated = new File(
					csapApp.getDefinitionFolder( ),
					"junit-new-package.json" ) ;

			// mock does much validation.....
			ResultActions resultActions = mockMvc.perform(
					post( CsapConstants.DEFINITION_URL + DefinitionRequests.APPICATION_APPLY )
							.param( "applyButNoCheckin", "true" )
							.param( "hostName", "localhost" )
							.param( "scmBranch", "trunk" )
							.param( "scmPass", "" )
							.param( "scmUserid", "peterUser" )
							.param( "serviceName", "HostCommand" )
							.param( CsapConstants.PROJECT_PARAMETER, "SampleDefaultPackage" )
							.param( "updatedConfig",
									FileUtils.readFileToString( csapApplicationDefinition ) )
							.accept( MediaType.APPLICATION_JSON ) ) ;

			JsonNode responseJsonNode = jacksonMapper.readTree(
					resultActions
							.andExpect( status( ).isOk( ) )
							.andExpect( content( ).contentTypeCompatibleWith( MediaType.APPLICATION_JSON ) )
							.andReturn( )
							.getResponse( )
							.getContentAsString( ) ) ;

			String opResult = responseJsonNode.get( C7.response_plain_text.val( ) ).asText( ) ;

			logger.info( "apply result text:\n {}", opResult ) ;

			assertThat( opResult )
					.as( "Definition does not contain errors" )
					.doesNotContain( CsapConstants.CONFIG_PARSE_ERROR ) ;

			assertThat( releaseFileGenerated.exists( ) )
					.as( "new release file created from template" )
					.isTrue( ) ;

			var defaultPojectDefinition = jacksonMapper.readTree( releaseFileGenerated ) ;

			assertThat( defaultPojectDefinition.path( "project" ).path( "name" ).asText( ) )
					.isEqualTo( "changeToYourPackageName" ) ;

			assertThat( defaultPojectDefinition.path( "environments" ).path( "dev" ).path( "base-os" ).path(
					"template-references" ).toString( ) )
							.contains( "csap-package-linux" ) ;

//			logger.info( "templateFile path: '{}', \n\t File: '{}' ",
//					CsapTemplate.project_template.getFile( ).getAbsolutePath( ),
//					releaseFileGenerated.getAbsolutePath( ) ) ;

//			assertThat( CsapTemplate.project_template.getFile( ).length( ) )
//					.as( "package file size '{}',  matches new '{}'" )
//					.isEqualTo( releaseFileGenerated.length( ) ) ;

		}

		@Test
		public void applyDefinitionWithParsingErrors ( )
			throws Exception {

			File csapApplicationDefinition = new File(
					getClass( ).getResource( Agent_context_loaded.TEST_DEFINITIONS
							+ "application_with_invalid_format.json" ).getPath( ) ) ;

			String message = CsapApplication.TC_HEAD + "Validating a  config with errors: \n"
					+ csapApplicationDefinition ;
			logger.info( message ) ;

			// mock does much validation.....
			ResultActions resultActions = mockMvc.perform(
					post( CsapConstants.DEFINITION_URL + DefinitionRequests.APPICATION_APPLY )
							.param( "applyButNoCheckin", "true" )
							.param( "hostName", "localhost" )
							.param( "scmBranch", "trunk" )
							.param( "scmPass", "" )
							.param( "scmUserid", "peterUser" )
							.param( "serviceName", "HostCommand" )
							.param( CsapConstants.PROJECT_PARAMETER, "SampleDefaultPackage" )
							.param( "updatedConfig",
									FileUtils.readFileToString( csapApplicationDefinition ) )
							.accept( MediaType.APPLICATION_JSON ) ) ;

			JsonNode responseJsonNode = jacksonMapper.readTree(
					resultActions
							.andExpect( status( ).isOk( ) )
							.andExpect( content( ).contentTypeCompatibleWith( MediaType.APPLICATION_JSON ) )
							.andReturn( )
							.getResponse( )
							.getContentAsString( ) ) ;

			String opResult = responseJsonNode.get( C7.response_plain_text.val( ) ).asText( ) ;

			logger.info( "result: {}", opResult ) ;

			Assertions.assertTrue(
					opResult.indexOf( CsapConstants.CONFIG_PARSE_ERROR ) != -1 ) ;

		}

		@Test
		public void apply_definition_with_missing_jvm_has_error_message ( )
			throws Exception {

			File csapApplicationDefinition = new File(
					getClass( ).getResource( Agent_context_loaded.TEST_DEFINITIONS
							+ "application_with_jvm_missing_error.json" ).getPath( ) ) ;

			csapApp.configureForDefinitionOperationsTest( csapApplicationDefinition ) ;

			String message = CsapApplication.TC_HEAD + "Validating a  config with errors: \n"
					+ csapApplicationDefinition ;
			logger.info( message ) ;

			// mock does much validation.....
			ResultActions resultActions = mockMvc.perform(
					post( CsapConstants.DEFINITION_URL + DefinitionRequests.APPICATION_APPLY )
							.param( "applyButNoCheckin", "true" )
							.param( "hostName", "localhost" )
							.param( "scmBranch", "trunk" )
							.param( "scmPass", "" )
							.param( "scmUserid", "peterUser" )
							.param( "serviceName", "HostCommand" )
							.param( CsapConstants.PROJECT_PARAMETER, "SampleDefaultPackage" )
							.param( "updatedConfig",
									FileUtils.readFileToString( csapApplicationDefinition ) )
							.accept( MediaType.APPLICATION_JSON ) ) ;

			JsonNode responseJsonNode = jacksonMapper.readTree(
					resultActions
							.andExpect( status( ).isOk( ) )
							.andExpect( content( ).contentTypeCompatibleWith( MediaType.APPLICATION_JSON ) )
							.andReturn( )
							.getResponse( )
							.getContentAsString( ) ) ;

			String opResult = responseJsonNode.get( C7.response_plain_text.val( ) ).asText( ) ;

			logger.info( "result: {}", opResult ) ;

			Assertions.assertTrue(
					opResult.indexOf( CsapConstants.MISSING_SERVICE_MESSAGE ) != -1 ) ;

		}
	}

	@Nested
	@DisplayName ( "SubProject" )
	class SubProject {
		@Test
		public void modify_child_release_package_and_apply ( )
			throws Exception {

			logger.info( CsapApplication.testHeader( ) ) ;

			File csapApplicationDefinition = new File(
					getClass( ).getResource( Agent_context_loaded.TEST_DEFINITIONS
							+ "sub-project-localhost/clusterConfigMultiple.json" )
							.getPath( ) ) ;

			assertThat( csapApp.loadDefinitionForJunits( false, csapApplicationDefinition ) ).as(
					"No Errors or warnings" ).isTrue( ) ;

			// Now we update a sub package

			File updatedReleasePackage = new File( getClass( ).getResource( "application-test-modify.json" )
					.getPath( ) ) ;
			logger.info( "Applying a definition with a new sub package: {}", updatedReleasePackage
					.getAbsolutePath( ) ) ;
			csapApp.configureForDefinitionOperationsTest( csapApplicationDefinition ) ;

			// mock does much validation.....
			ResultActions resultActions = mockMvc.perform(
					post( CsapConstants.DEFINITION_URL + DefinitionRequests.APPICATION_APPLY )
							.param( "applyButNoCheckin", "true" )
							.param( "hostName", "localhost" )
							.param( "scmBranch", "trunk" )
							.param( "scmPass", "" )
							.param( "scmUserid", "peterUser" )
							.param( "serviceName", "HostCommand" )
							.param( CsapConstants.PROJECT_PARAMETER, "Supporting Sample A" )
							.param( "updatedConfig",
									FileUtils.readFileToString( updatedReleasePackage ) )
							.accept( MediaType.APPLICATION_JSON ) ) ;

			JsonNode responseJsonNode = jacksonMapper.readTree(
					resultActions
							.andExpect( status( ).isOk( ) )
							.andExpect( content( ).contentTypeCompatibleWith( MediaType.APPLICATION_JSON ) )
							.andReturn( )
							.getResponse( )
							.getContentAsString( ) ) ;

			String opResult = responseJsonNode.get( C7.response_plain_text.val( ) ).asText( ) ;

			logger.info( "result:\n {}", opResult ) ;

			assertThat( opResult )
					.as( "no errors found" )
					.doesNotContain( CsapConstants.CONFIG_PARSE_ERROR ) ;

			// On junits, cluster reloads are placed in test folder, and packages
			// are
			// not reloaded.
			// We just confirm files exist
			File originalPackage = new File(
					getClass( ).getResource( Agent_context_loaded.TEST_DEFINITIONS
							+ "sub-project-localhost/clusterConfigMultipleA.json" )
							.getPath( ) ) ;

			File copiedPackage = new File( csapApp.getDefinitionFolder( ),
					"clusterConfigMultipleA.json" ) ;

			logger.info( "original path: " + originalPackage.getAbsolutePath( )
					+ "\n cluster File path: " + copiedPackage.getAbsolutePath( ) ) ;

			assertThat( originalPackage.length( ) )
					.as( "package size is different after test" )
					.isNotEqualTo( copiedPackage.length( ) ) ;

			copiedPackage = new File( csapApp.getDefinitionFolder( ),
					"clusterConfigMultiple.json" ) ;

			logger.info( "original path: {}, copied path: {}",
					csapApplicationDefinition.getAbsolutePath( ), copiedPackage.getAbsolutePath( ) ) ;

			assertThat( csapApplicationDefinition.length( ) )
					.as( "package size is different after test" )
					.isEqualTo( copiedPackage.length( ) ) ;

		}
	}
}
