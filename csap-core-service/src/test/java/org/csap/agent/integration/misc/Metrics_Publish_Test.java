package org.csap.agent.integration.misc ;

import static org.assertj.core.api.Assertions.assertThat ;

import java.io.File ;
import java.io.IOException ;

import javax.inject.Inject ;

import org.apache.commons.io.FileUtils ;
import org.csap.agent.Agent_context_loaded ;
import org.csap.agent.CsapBareTest ;
import org.csap.agent.CsapCoreService ;
import org.csap.agent.integrations.MetricsPublisher ;
import org.csap.agent.integrations.NagiosIntegration ;
import org.csap.agent.model.Application ;
import org.csap.helpers.CSAP ;
import org.csap.helpers.CsapApplication ;
import org.junit.jupiter.api.Assertions ;
import org.junit.jupiter.api.Assumptions ;
import org.junit.jupiter.api.BeforeAll ;
import org.junit.jupiter.api.Test ;
import org.junit.jupiter.api.TestInstance ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.boot.test.context.SpringBootTest ;
import org.springframework.test.context.ActiveProfiles ;

import com.fasterxml.jackson.core.JsonProcessingException ;
import com.fasterxml.jackson.databind.ObjectMapper ;
import com.fasterxml.jackson.databind.node.ObjectNode ;

@SpringBootTest ( classes = CsapCoreService.class )
@ActiveProfiles ( {
		CsapBareTest.PROFILE_JUNIT, "EclipseJGitTests"
} )

@TestInstance ( TestInstance.Lifecycle.PER_CLASS )

public class Metrics_Publish_Test {

	Logger logger = LoggerFactory.getLogger( getClass( ) ) ;

	static {

		CsapApplication.initialize( "" ) ;

	}

	@Inject
	Application csapApp ;

	@Inject
	ObjectMapper jacksonMapper ;

	@BeforeAll
	void beforeAll ( ) {

		logger.info( CsapApplication.testHeader( ) ) ;

		csapApp.setAutoReload( false ) ;
		csapApp.getProjectLoader( ).setAllowLegacyNames( true ) ;
		FileUtils.deleteQuietly( csapApp.getHttpdIntegration( ).getHttpdWorkersFile( ).getParentFile( ) ) ;

	}

	/**
	 *
	 * Scenario: - validate publishing of CSAP data
	 *
	 */
	@Test
	public void verify_publish_to_csaptools ( )
		throws JsonProcessingException ,
		IOException {

		File csapApplicationDefinition = new File(
				getClass( ).getResource( Agent_context_loaded.TEST_DEFINITIONS + "test_application_model.json" )
						.getPath( ) ) ;

		logger.info( CsapApplication.testHeader( "loading: {}" ), csapApplicationDefinition.getAbsolutePath( ) ) ;

		assertThat( csapApp.loadDefinitionForJunits( false, csapApplicationDefinition ) ).as( "No Errors or warnings" )
				.isTrue( ) ;

		assertThat( csapApp.loadDefinitionForJunits( false, csapApplicationDefinition ) ).as( "No Errors or warnings" )
				.isTrue( ) ;

		assertThat( csapApp.getName( ) ).isEqualTo( "TestDefinitionForAutomatedTesting" ) ;

		// Test MetricsPublishing
		Assertions.assertTrue( csapApp.rootProjectEnvSettings( ).isMetricsPublication( ) ) ;

		ObjectNode pubInfoJson = jacksonMapper.createObjectNode( ) ;
		pubInfoJson.put( "type", "csapCallHome" ) ;
		pubInfoJson.put( "intervalInSeconds", -1 ) ;
		pubInfoJson.put( "url", "http://csaptools.yourcompany.com/CsapGlobalAnalytics/rest/vm/healthInfo2" ) ;
		pubInfoJson.put( "token", "notUsed" ) ;

		// { "type" : "csapCallHome", "intervalInSeconds" : 300 , "url":
		// "http://csaptools.yourcompany.com/CsapGlobalAnalytics/api/vm/health"
		// ,
		// "token": "notUsed"}
		CSAP.setLogToInfo( MetricsPublisher.class.getName( ) ) ;
		MetricsPublisher publisher = new MetricsPublisher( csapApp, pubInfoJson ) ;
		publisher.setIntegrationEnabled( true ) ; // Uncomment to hit
		publisher.publishHealthReport( ) ;

		assertThat( publisher.getLastResults( ).contains( "Failed" ) ).isFalse( ) ;

	}

	@Test
	public void verify_publish_to_nagios ( )
		throws JsonProcessingException ,
		IOException {

		File csapApplicationDefinition = new File(
				getClass( ).getResource( Agent_context_loaded.TEST_DEFINITIONS + "test_application_model.json" )
						.getPath( ) ) ;

		logger.info( CsapApplication.testHeader( "loading: {}" ), csapApplicationDefinition.getAbsolutePath( ) ) ;

		assertThat( csapApp.loadDefinitionForJunits( false, csapApplicationDefinition ) ).as( "No Errors or warnings" )
				.isTrue( ) ;

		assertThat( csapApp.getName( ) ).isEqualTo( "TestDefinitionForAutomatedTesting" ) ;

		// Test MetricsPublishing
		assertThat( csapApp.rootProjectEnvSettings( ).isMetricsPublication( ) ).isTrue( ) ;

		Assumptions.assumeTrue( csapApp.isCompanyVariableConfigured( "test.variables.nagios-url" ) ) ;

		String nagiosUrl = csapApp.getCompanyConfiguration( "test.variables.nagios-url", "" ) ;
		logger.info( "nagiosUrl: {}", nagiosUrl ) ;

		ObjectNode nagiosDefinition = jacksonMapper.createObjectNode( ) ;
		nagiosDefinition.put( "type", "nagios" ) ;
		nagiosDefinition.put( "intervalInSeconds", -1 ) ;
		nagiosDefinition.put( "url", nagiosUrl ) ;
		nagiosDefinition.put( "token", csapApp.getCompanyConfiguration( "test.variables.nagios-token", "" ) ) ;
		nagiosDefinition.put( "user", csapApp.getCompanyConfiguration( "test.variables.nagios-user", "" ) ) ;
		nagiosDefinition.put( "pass", csapApp.getCompanyConfiguration( "test.variables.nagios-pass", "" ) ) ;

		CSAP.setLogToInfo( MetricsPublisher.class.getName( ) ) ;
		CSAP.setLogToDebug( NagiosIntegration.class.getName( ) ) ;
		MetricsPublisher publisher = new MetricsPublisher( csapApp, nagiosDefinition ) ;
		publisher.setIntegrationEnabled( true ) ;
		publisher.publishHealthReport( ) ;
		;

		CSAP.setLogToInfo( NagiosIntegration.class.getName( ) ) ;
		logger.info( "Results from publish: {}", publisher.getLastResults( ) ) ;

		assertThat( publisher.getLastResults( ) )
				.as( "pubish success" )
				.contains( "<message>OK</message>" ) ;

		assertThat( publisher.getLastResults( ).contains( "<output>4 checks processed.</output>" ) ).isTrue( ) ;

		assertThat( publisher.getLastResults( ).contains( "Failed" ) ).isFalse( ) ;
		assertThat( publisher.getLastResults( ).contains( "BAD TOKEN" ) ).isFalse( ) ;
		assertThat( publisher.getLastResults( ).contains( "BAD XML" ) ).isFalse( ) ;

	}

}
