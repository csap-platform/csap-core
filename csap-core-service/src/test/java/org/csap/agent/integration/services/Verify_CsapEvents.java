package org.csap.agent.integration.services ;

import static org.assertj.core.api.Assertions.assertThat ;

import java.io.File ;
import java.io.IOException ;
import java.text.DateFormat ;
import java.text.SimpleDateFormat ;
import java.util.Date ;

import javax.inject.Inject ;

import org.apache.commons.io.FileUtils ;
import org.csap.agent.Agent_context_loaded ;
import org.csap.agent.CsapBareTest ;
import org.csap.agent.api.AgentApi ;
import org.csap.agent.integrations.CsapEvents ;
import org.csap.agent.model.Application ;
import org.csap.helpers.CSAP ;
import org.csap.helpers.CsapApplication ;
import org.junit.jupiter.api.Assertions ;
import org.junit.jupiter.api.Assumptions ;
import org.junit.jupiter.api.BeforeAll ;
import org.junit.jupiter.api.Disabled ;
import org.junit.jupiter.api.DisplayName ;
import org.junit.jupiter.api.Nested ;
import org.junit.jupiter.api.Test ;
import org.junit.jupiter.api.TestInstance ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;

import com.fasterxml.jackson.databind.ObjectMapper ;
import com.fasterxml.jackson.databind.node.ObjectNode ;

@CsapBareTest.Agent_Full
class Verify_CsapEvents {

	Logger logger = LoggerFactory.getLogger( getClass( ) ) ;

	static {

		CsapApplication.initialize( "Test Setup Complete" ) ;

	}

	@Inject
	Application csapApplication ;

	@Inject
	AgentApi agentApi ;

	@Inject
	CsapEvents csapEventClient ;

	DateFormat shortFormatter = new SimpleDateFormat( "HH:mm:ss MMM-dd" ) ;

	@BeforeAll
	void beforeAll ( ) {

		logger.info( CsapApplication.testHeader( ) ) ;

		csapApplication.setAutoReload( false ) ;

		csapApplication.getProjectLoader( ).setAllowLegacyNames( true ) ;

	}

	public void verify_remote_test_data_configured ( )
		throws Exception {

		Assumptions.assumeTrue( csapApplication.rootProjectEnvSettings( ).isEventPublishEnabled( ) ) ;

	}

	@Nested
	@TestInstance ( TestInstance.Lifecycle.PER_CLASS )
	@DisplayName ( "Events Publishing Disabled" )
	class EventDisabled {

		File publishDisabledDefinition = new File(
				getClass( ).getResource( Agent_context_loaded.TEST_DEFINITIONS + "test_application_model.json" )
						.getPath( ) ) ;

		@BeforeAll
		public void beforeAll ( )
			throws Exception {

			logger.info( CsapApplication.testHeader( "{}" ), publishDisabledDefinition.getAbsolutePath( ) ) ;
			assertThat( csapApplication.loadDefinitionForJunits( false, publishDisabledDefinition ) ).as(
					"No Errors or warnings" )
					.isTrue( ) ;

		}

		@Test
		public void publish_event_with_remote_disabled ( )
			throws Exception {

			logger.info( CsapApplication.testHeader( ) ) ;

			csapEventClient.publishEvent(
					"/junit/summaryTest",
					shortFormatter.format( new Date( ) ), null,
					csapApplication.buildSummaryReport( false ) ) ;

			assertThat( csapEventClient.waitForFlushOfAllEvents( 5 ) ).isTrue( ) ;

		}

		ObjectMapper jacksonMapper = new ObjectMapper( ) ;

		@Test
		public void publish_jmx_metrics_with_remote_disabled ( )
			throws InterruptedException ,
			IOException {

			logger.info( CsapApplication.testHeader( ) ) ;

			File sampleMetricsData = new File(
					getClass( ).getResource( "csap-event-sample-data.json" ).getPath( ) ) ;

			ObjectNode attJson = (ObjectNode) jacksonMapper.readTree( FileUtils.readFileToString(
					sampleMetricsData ) ) ;

			csapEventClient.publishEvent(
					"/junit/jmx30Post",
					shortFormatter.format( new Date( ) ), null, attJson ) ;

			Assertions.assertTrue( csapEventClient.waitForFlushOfAllEvents( 5 ) ) ;

			// todo : add checks for event
		}
	}

	@Nested
	@TestInstance ( TestInstance.Lifecycle.PER_CLASS )
	@DisplayName ( "Events Publishing Enabled" )
	class EventEnabled {

		@BeforeAll
		public void beforeAll ( )
			throws Exception {

			File configFile = new File( this.getClass( ).getResource( "appWithEventPublishEnabled.json" ).getPath( ) ) ;
			logger.info( CsapApplication.testHeader( "{}" ), configFile ) ;

			assertThat( csapApplication.loadDefinitionForJunits( false, configFile ) )
					.isTrue( )
					.as( "Definition parsed cleanly" ) ;

			verify_remote_test_data_configured( ) ;

			assertThat( csapApplication.rootProjectEnvSettings( ).isEventPublishEnabled( ) )
					.isTrue( ) ;

			csapEventClient.setMaxTextSize( 50 * 1024 ) ;

		}

		@Test
		public void agent_latest_event_time ( )
			throws Exception {

			logger.info( CsapApplication.testHeader( ) ) ;

			var lastStartCompleteMs = agentApi.latestEventTime( "csap-dev01", CsapEvents.CSAP_SYSTEM_CATEGORY
					+ "/agent-start-up" ) ;

			logger.info( "{} lastStartCompleteMs: {}", CsapEvents.CSAP_SYSTEM_CATEGORY + "/agent-start-up",
					lastStartCompleteMs ) ;

			assertThat( lastStartCompleteMs )
					.isGreaterThan( 0 ) ;

			var lastjobStart = agentApi.latestEventTime( "csap-dev01", CsapEvents.CSAP_SYSTEM_SERVICE_CATEGORY
					+ "/csap-agent/job" ) ;

			logger.info( "{} lastjobStart: {}", CsapEvents.CSAP_SYSTEM_SERVICE_CATEGORY + "/csap-agent/job",
					lastjobStart ) ;

			assertThat( lastjobStart )
					.isGreaterThan( 0 ) ;

		}

		@Test
		public void publish_simple_json_event ( )
			throws Exception {

			logger.info( CsapApplication.testHeader( ) ) ;

			assertThat( csapApplication.rootProjectEnvSettings( ).isSecondaryEventPublishEnabled( ) )
					.isTrue( ) ;

			CSAP.setLogToDebug( csapEventClient.getClass( ).getName( ) ) ;
			// fail("Not yet implemented");
			csapEventClient.getNumberOfPostedEventsAndReset( ) ; // resets to 0
			csapEventClient.publishEvent(
					"/junit/summaryTest",
					shortFormatter.format( new Date( ) ), null,
					csapApplication.buildSummaryReport( false ) ) ;

			boolean flushedEvents = csapEventClient.waitForFlushOfAllEvents( 5 ) ;

			CSAP.setLogToInfo( csapEventClient.getClass( ).getName( ) ) ;
			// csapEventClient.shutdown();

			assertThat( csapEventClient.getBacklogCount( ) )
					.isEqualTo( 0 )
					.as( "All messages posted" ) ;

			assertThat( csapEventClient.getNumberOfPostedEventsAndReset( ) )
					.isGreaterThanOrEqualTo( 1 )
					.as( "message posted" ) ;

			Assertions.assertTrue( flushedEvents ) ;

			// todo : add checks for event
		}

		final static String TEST_CONTENT = "0123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789" ;

		@Test
		public void publish_event_50kb_payload ( )
			throws Exception {

			logger.info( CsapApplication.testHeader( ) ) ;

			StringBuilder testPostContent = new StringBuilder( ) ;

			for ( int i = 0; i < 500; i++ ) {

				testPostContent.append( TEST_CONTENT ) ;

			}

			logger.info( "Posting event with size: {}", testPostContent.length( ) ) ;
			csapEventClient.getNumberOfPostedEventsAndReset( ) ;
			csapEventClient.publishEvent(
					"/junit/summaryTest",
					shortFormatter.format( new Date( ) ),
					testPostContent.toString( ) ) ;

			boolean flushedEvents = csapEventClient.waitForFlushOfAllEvents( 5 ) ;
			// csapEventClient.shutdown();

			assertThat( csapEventClient.getBacklogCount( ) )
					.isEqualTo( 0 )
					.as( "All messages posted" ) ;

			assertThat( csapEventClient.getNumberOfPostedEventsAndReset( ) )
					.isGreaterThanOrEqualTo( 1 )
					.as( "StartUp messages plus the above" ) ;

			Assertions.assertTrue( flushedEvents ) ;

			// todo : add checks for event
		}

		@Test
		public void publish_event_truncate_payload ( )
			throws Exception {

			logger.info( CsapApplication.testHeader( ) ) ;

			csapEventClient.setMaxTextSize( 5000 ) ;
			int overflow = ( csapEventClient.getMaxTextSize( ) * 2 ) / TEST_CONTENT.length( ) ;
			StringBuilder testPostContent = new StringBuilder( ) ;

			for ( int i = 0; i < overflow; i++ ) {

				testPostContent.append( TEST_CONTENT ) ;

			}

			logger.info( "Posting event with size: {}", testPostContent.length( ) ) ;
			// fail("Not yet implemented");

			csapEventClient.getNumberOfPostedEventsAndReset( ) ;
			csapEventClient.publishEvent(
					"/junit/summaryTest",
					shortFormatter.format( new Date( ) ),
					testPostContent.toString( ) ) ;

			boolean flushedEvents = csapEventClient.waitForFlushOfAllEvents( 10 ) ;
			// csapEventClient.shutdown();

			assertThat( csapEventClient.getBacklogCount( ) )
					.isEqualTo( 0 )
					.as( "All messages posted" ) ;

			assertThat( csapEventClient.getNumberOfPostedEventsAndReset( ) )
					.isGreaterThanOrEqualTo( 1 )
					.as( "StartUp messages plus the above" ) ;

			Assertions.assertTrue( flushedEvents ) ;

			// todo : add checks for event
		}

		@Disabled
		@Test
		public void publish_event_overflow_payload ( )
			throws Exception {

			logger.info( CsapApplication.testHeader( ) ) ;

			csapEventClient.setMaxTextSize( 1024 * 1024 * 3 ) ;

			int overflow = ( csapEventClient.getMaxTextSize( ) * 2 ) / TEST_CONTENT.length( ) ; // 2MB
																								// is
																								// max
																								// size

			StringBuilder testPostContent = new StringBuilder( ) ;

			for ( int i = 0; i < overflow; i++ ) {

				testPostContent.append( TEST_CONTENT ) ;

			}

			logger.info( "Posting event with size: {}", testPostContent.length( ) ) ;
			// fail("Not yet implemented");
			csapEventClient.publishEvent(
					"/junit/summaryTest",
					shortFormatter.format( new Date( ) ),
					testPostContent.toString( ) ) ;

			boolean flushedEvents = csapEventClient.waitForFlushOfAllEvents( 20 ) ;
			// csapEventClient.shutdown();

			assertThat( csapEventClient.getBacklogCount( ) )
					.isEqualTo( 0 )
					.as( "All messages posted" ) ;

			assertThat( csapEventClient.getEventPostFailures( ) )
					.isEqualTo( 1 )
					.as( "Large payload will fail to post" ) ;

			assertThat( csapEventClient.getNumberOfPostedEventsAndReset( ) )
					.isGreaterThanOrEqualTo( 1 )
					.as( "StartUp messages plus the above" ) ;

			Assertions.assertTrue( flushedEvents ) ;

			// todo : add checks for event
		}
	}

}
