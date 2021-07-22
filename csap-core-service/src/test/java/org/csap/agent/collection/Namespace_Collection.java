package org.csap.agent.collection ;

import static org.assertj.core.api.Assertions.assertThat ;

import java.io.File ;
import java.util.List ;

import org.csap.agent.CsapCore ;
import org.csap.agent.CsapThinTests ;
import org.csap.agent.container.DockerJson ;
import org.csap.agent.services.HostKeys ;
import org.csap.agent.stats.HostCollector ;
import org.csap.agent.stats.OsProcessCollector ;
import org.csap.agent.stats.OsProcessEnum ;
import org.csap.helpers.CSAP ;
import org.csap.helpers.CsapApplication ;
import org.junit.jupiter.api.BeforeAll ;
import org.junit.jupiter.api.DisplayName ;
import org.junit.jupiter.api.Nested ;
import org.junit.jupiter.api.Test ;
import org.junit.jupiter.api.TestInstance ;
import org.springframework.boot.context.properties.ConfigurationProperties ;

import com.fasterxml.jackson.databind.JsonNode ;

@ConfigurationProperties ( prefix = "test.variables" )
public class Namespace_Collection extends CsapThinTests {

	File collectionDefinition = new File( Service_Collection_Test.class.getResource( "namespace-collection.json" )
			.getPath( ) ) ;

	String testServiceName = "csap-test-k8s-service" ;
	String testServiceCollectionId = testServiceName + "-1" ;

	@BeforeAll
	void beforeAll ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		getApplication( ).getProjectLoader( ).setAllowLegacyNames( true ) ;

		assertThat(
				getApplication( ).loadDefinitionForJunits( false, collectionDefinition ) )
						.as( "No Errors or warnings" )
						.isTrue( ) ;

		getApplication( ).metricManager( ).startCollectorsForJunit( ) ;
		getApplication( ).getOsManager( ).checkForProcessStatusUpdate( ) ;
		getApplication( ).getOsManager( ).wait_for_initial_process_status_scan( 3 ) ;

		assertThat( getApplication( ).isBootstrapComplete( ) ).isTrue( ) ;

		assertThat( getApplication( ).getServiceInstanceCurrentHost( testServiceName ) ).isNotNull( ) ;

		logger.debug( "latest discovery: {}",
				getApplication( ).getOsManager( ).latest_process_discovery_results( ).path(
						DockerJson.response_plain_text.json( ) ).asText( ) ) ;

	}

	@Nested
	@DisplayName ( "Host Collections" )
	@TestInstance ( TestInstance.Lifecycle.PER_CLASS )
	class Host {

		@Test
		public void process_collection_for_host ( )
			throws Exception {

			var testK8Service = getApplication( ).getServiceInstanceCurrentHost( testServiceName ) ;

			String[] requestedServices = {
					CsapCore.AGENT_NAME, testServiceCollectionId
			} ;

			logger.info( CsapApplication.testHeader( "OS Collection for: {}" ), List.of( requestedServices ) ) ;

			//
			// Perform collection
			//

			var osProcessCollector = getApplication( ).metricManager( ).getOsProcessCollector( -1 ) ;
			var now = System.currentTimeMillis( ) ;

			// CSAP.setLogToDebug( OsProcessCollector.class.getName() ) ;
			osProcessCollector.testCollection( ) ;
			CSAP.setLogToInfo( OsProcessCollector.class.getName( ) ) ;

			//
			// Collection Report
			//
			var osCollectionReport = osProcessCollector.buildCollectionReport( true, requestedServices, 999, 0 ) ;
			logger.info( "os_collection: \n {} ", CSAP.jsonPrint( osCollectionReport ) ) ;

			var attributes = osCollectionReport.path( HostCollector.ATTRIBUTES_JSON ) ;

			var servicesAvailable = CSAP.jsonList( attributes.path( "servicesAvailable" ) ) ;
			assertThat( servicesAvailable )
					.hasSize( 4 )
					.containsOnly(
							getApplication( ).getProjectLoader( ).getNsMonitorName( "kube-system" ) + "-1",
							"csap-agent", "kubelet", testServiceCollectionId ) ;

			logger.info( "servicesNotFound: \n {} ", CSAP.jsonPrint( osCollectionReport.at(
					"/attributes/servicesNotFound" ) ) ) ;

			logger.info( "os_collection (data only): \n {} ", CSAP.jsonPrint( osCollectionReport.path( "data" ) ) ) ;

			assertThat( osCollectionReport.at( "/data/timeStamp/0" ).asLong( ) )
					.as( "/data/timeStamp/0" )
					.isGreaterThanOrEqualTo( now ) ;

			assertThat( osCollectionReport.at( "/data/threadCount_" + CsapCore.AGENT_NAME + "/0" ).asInt( ) )
					.as( "threadCount_CsAgent" )
					.isGreaterThan( 90 ) ;

			// Note this is 76 + 1
			assertThat( osCollectionReport.at( "/data/threadCount_" + testServiceCollectionId + "/0" ).asInt( ) )
					.isEqualTo( 77 ) ;

			//
			// Verify osManger runtime
			//
			JsonNode runtimeReport = getApplication( ).getOsManager( ).getHostRuntime( ) ;
			logger.info( "agent status: {}",
					CSAP.jsonPrint( runtimeReport.at( HostKeys.servicesJsonPath( CsapCore.AGENT_ID ) ) ) ) ;

			var agentThreadPath = HostKeys.serviceMetricJsonPath( CsapCore.AGENT_ID, 0, OsProcessEnum.THREAD_COUNT ) ;
			assertThat( runtimeReport.at( agentThreadPath ).intValue( ) )
					.as( agentThreadPath )
					.isEqualTo( 107 ) ;

			var agentNumSamplesPath = HostKeys.serviceMetricJsonPath(
					CsapCore.AGENT_ID, 0,
					HostKeys.numberSamplesAveraged.jsonId ) ;
			assertThat( runtimeReport.at( agentNumSamplesPath ).intValue( ) )
					.as( agentNumSamplesPath )
					.isGreaterThanOrEqualTo( 1 ) ;

			//
			// Summary Report
			//
			osProcessCollector.buildCollectionReport( true, requestedServices, 999, 0 ) ;
			osProcessCollector.buildCollectionReport( true, requestedServices, 999, 0 ) ;
			logger.info( CsapApplication.testHeader( "testSummaryReport" ) ) ;
			JsonNode processSummaryReport = osProcessCollector.testSummaryReport( false ) ;
			logger.info( "processSummaryReport: {}", CSAP.jsonPrint( processSummaryReport ) ) ;

			var k8sInfo = CSAP.jsonStream( processSummaryReport )
					.filter( processCollected -> {

						return processCollected.path( "serviceName" ).asText( ).equals( testServiceName ) ;

					} )
					.findFirst( ) ;

			logger.info( "k8sInfo: {}", CSAP.jsonPrint( k8sInfo.get( ) ) ) ;
			assertThat( k8sInfo.get( ).path( OsProcessEnum.threadCount.json( ) ).asInt( ) )
					.as( "aggregate threads" )
					.isGreaterThanOrEqualTo( 77 ) ;

		}

	}

}
