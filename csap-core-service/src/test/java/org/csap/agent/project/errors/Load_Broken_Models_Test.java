package org.csap.agent.project.errors ;

import static org.assertj.core.api.Assertions.assertThat ;
import static org.assertj.core.api.Assertions.assertThatThrownBy ;

import java.io.File ;
import java.util.regex.Pattern ;

import org.csap.agent.Agent_context_loaded ;
import org.csap.agent.CsapBareTest ;
import org.csap.agent.CsapConstants ;
import org.csap.agent.model.ProjectLoader ;
import org.csap.helpers.CsapApplication ;
import org.junit.jupiter.api.BeforeAll ;
import org.junit.jupiter.api.Test ;

import com.fasterxml.jackson.core.JsonProcessingException ;

class Load_Broken_Models_Test extends CsapBareTest {

	@BeforeAll
	void beforeAll ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

	}

	// @Inject
	// Application csapApp ;

	@Test
	public void application_definition_with_same_jvm_fails_to_load ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		File csapApplicationDefinition = new File(
				getClass( ).getResource( "application-jvm-multiple-packages.json" ).getPath( ) ) ;

		Pattern multiple_releases_pattern = Pattern.compile(
				".*Service: Factory2Sample.*found in multiple release packages.*",
				Pattern.DOTALL ) ;

		assertThat(
				getApplication( ).getProjectLoader( ).process( true, csapApplicationDefinition ).toString( ) )
						.as( "Warning messages" )
						.contains( CsapConstants.CONFIG_PARSE_WARN )
						.doesNotContain( CsapConstants.CONFIG_PARSE_ERROR )
						.matches( multiple_releases_pattern )
						.contains( "it must be unique" ) ;

	}

	@Test
	public void load_application_with_invalid_json_throws_exception ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		File csapApplicationDefinition = new File(
				getClass( ).getResource( Agent_context_loaded.TEST_DEFINITIONS
						+ "application_with_invalid_format.json" ).getPath( ) ) ;

		assertThatThrownBy( ( ) -> {

			getApplication( ).getProjectLoader( ).process( true, csapApplicationDefinition ) ;

		} )
				.as( "JsonProcessingException is thrown when malformed mark up" )
				.isInstanceOf( JsonProcessingException.class )
				.hasMessageContaining(
						"Unexpected character (':' (code 58)): was expecting comma to separate" ) ;

	}

	@Test
	public void load_application_with_missing_jvm_throws_exception ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		File csapApplicationDefinition = new File(
				getClass( ).getResource( Agent_context_loaded.TEST_DEFINITIONS
						+ "application_with_jvm_missing_error.json" ).getPath( ) ) ;

		var parseResults = getApplication( ).getProjectLoader( ).process( true, csapApplicationDefinition ) ;

		logger.info( "parseResults: {}", parseResults.toString( ) ) ;

		assertThat( parseResults.toString( ) )
				.as( "MISSING_SERVICE_MESSAGE" )
				.contains( CsapConstants.CONFIG_PARSE_WARN )
				.contains( CsapConstants.MISSING_SERVICE_MESSAGE ) ;

		// assertThatThrownBy( () -> {
		// getApplication().getParser().load( true, csapApplicationDefinition ) ;
		// } )
		// .as( "IOException is thrown if a service referred to in cluster is not
		// present" )
		// .isInstanceOf( IOException.class )
		// .hasMessageContaining( Application.MISSING_SERVICE_MESSAGE ) ;

	}

	@Test
	public void load_application_with_duplicate_port ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		File csapApplicationDefinition = new File(
				getClass( ).getResource( "application-duplicate-port.json" ).getPath( ) ) ;

		StringBuilder parsingResults = getApplication( ).getProjectLoader( ).process( true,
				csapApplicationDefinition ) ;
		logger.info( parsingResults.toString( ) ) ;

		assertThat( parsingResults.toString( ) )
				.as( "Duplicate ports parse message" )
				.contains( CsapConstants.CONFIG_PARSE_WARN )
				.contains( ProjectLoader.WARNING_DUPLICATE_HOST_PORT ) ;

	}

	@Test
	public void load_application_service_with_missing_server ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		File csapApplicationDefinition = new File(
				getClass( ).getResource( "application-missing-server.json" ).getPath( ) ) ;

		StringBuilder parsingResults = getApplication( ).getProjectLoader( ).process( false,
				csapApplicationDefinition ) ;

		assertThat( getApplication( ).getLocalAgent( ) ).isNotNull( ) ;

		logger.info( parsingResults.toString( ) ) ;

		assertThat( parsingResults.toString( ) )
				.contains( CsapConstants.CONFIG_PARSE_WARN )
				.contains( "Missing required attribute" ) ;

		assertThat( getApplication( ).findServiceByNameOnCurrentHost( "csap-simple-service" ) ).isNull( ) ;

	}

	@Test
	public void load_application_with_invalid_name_not_loaded ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		File csapApplicationDefinition = new File(
				getClass( ).getResource( "application-bad-service-name.json" ).getPath( ) ) ;

		StringBuilder parsingResults = getApplication( ).getProjectLoader( ).process( true,
				csapApplicationDefinition ) ;
		logger.info( parsingResults.toString( ) ) ;

		assertThat( parsingResults.toString( ) )
				.as( "Duplicate ports parse message" )
				.contains( CsapConstants.CONFIG_PARSE_WARN )
				.contains( ProjectLoader.ERROR_INVALID_CHARACTERS ) ;

		// assertThatThrownBy( () -> {
		// getApplication().getParser().load( true, csapApplicationDefinition ) ;
		// } )
		// .as( "IOException is thrown when service name contains invalid characters" )
		// .isInstanceOf( IOException.class )
		// .hasMessageContaining( DefinitionParser.ERROR_INVALID_CHARACTERS ) ;

	}

}
