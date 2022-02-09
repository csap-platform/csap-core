package org.csap.agent.collection ;

import static org.assertj.core.api.Assertions.assertThat ;

import java.io.File ;

import org.csap.agent.CsapThinTests ;
import org.csap.agent.stats.ServiceCollector ;
import org.csap.agent.stats.service.HttpCollector ;
import org.csap.helpers.CSAP ;
import org.csap.helpers.CsapApplication ;
import org.junit.jupiter.api.BeforeAll ;
import org.junit.jupiter.api.Test ;
import org.springframework.boot.context.properties.ConfigurationProperties ;

import com.fasterxml.jackson.databind.node.ObjectNode ;

@ConfigurationProperties ( prefix = "test.variables" )
public class Mongo_Collection extends CsapThinTests {

	File csapApplicationDefinition = new File( Service_Collection_Test.class.getResource( "application-mongo.json" )
			.getPath( ) ) ;

	@BeforeAll
	void beforeAll ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( "loading: {}" ), csapApplicationDefinition ) ;

		getApplication( ).getProjectLoader( ).setAllowLegacyNames( true ) ;

		assertThat( getApplication( ).loadDefinitionForJunits( false, csapApplicationDefinition ) )
				.as( "No Errors or warnings" )
				.isTrue( ) ;

		getApplication( ).metricManager( ).startCollectorsForJunit( ) ;
		getOsManager( ).checkForProcessStatusUpdate( ) ;
		getOsManager( ).wait_for_initial_process_status_scan( 3 ) ;

	}

	@Test
	void mongo_collection_remote ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		// csapApp.shutdown();
		ServiceCollector serviceCollector = new ServiceCollector( getCsapApis( ), 30, false ) ;

		serviceCollector.shutdown( ) ;

		// This will trigger a remote procedure call with sufficient time to
		// process
		CSAP.setLogToDebug( ServiceCollector.class.getName( ) ) ;
		CSAP.setLogToDebug( HttpCollector.class.getName( ) ) ;
		serviceCollector.testHttpCollection( 5000 ) ;
		CSAP.setLogToInfo( HttpCollector.class.getName( ) ) ;
		CSAP.setLogToInfo( ServiceCollector.class.getName( ) ) ;

		String[] mongoServices = {
				"mongoDb"
		} ; // "Cssp3ReferenceMq_8241"
		// CsapCore.AGENT_ID
		ObjectNode mongoStatistics = serviceCollector.buildCollectionReport( false, mongoServices, 999, 0,
				"ApplicationCollection" ) ;
		logger.info( "Results: {}", CSAP.jsonPrint( mongoStatistics ) ) ;

		assertThat(
				mongoStatistics.at( "/data/MongoActiveConnections/0" ).isMissingNode( ) ).isFalse( ) ;

		assertThat(
				mongoStatistics.at( "/data/MongoActiveConnections/0" ).asInt( ) )
						.as( "Verifying active connections" )
						.isGreaterThan( 2 ) ;

	}

	@Test
	void mongo_collection_remote_with_http_timeout ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		// csapApp.shutdown();
		ServiceCollector applicationCollector = new ServiceCollector( getCsapApis( ), 30, false ) ;

		applicationCollector.shutdown( ) ;

		// This will trigger a remote procedure call with very short wait time
		applicationCollector.testHttpCollection( 1 ) ;

		String[] mongoServices = {
				"mongoDb"
		} ; // "Cssp3ReferenceMq_8241"
		// CsapCore.AGENT_ID
		ObjectNode mongoStatistics = applicationCollector.buildCollectionReport( false, mongoServices, 999, 0,
				"CustomJmxIsBeingUsed" ) ;
		logger.info( "Results: {}", CSAP.jsonPrint( mongoStatistics ) ) ;

		assertThat(
				mongoStatistics.at( "/data/MongoActiveConnections/0" ).isMissingNode( ) ).isFalse( ) ;

		assertThat(
				mongoStatistics.at( "/data/MongoActiveConnections/0" ).asInt( ) )
						.as( "Verifying active connections" )
						.isEqualTo( 0 ) ;

	}

}
