package org.csap.agent.collection ;

import static org.assertj.core.api.Assertions.assertThat ;

import java.io.File ;
import java.util.ArrayList ;
import java.util.List ;
import java.util.Set ;

import org.csap.agent.CsapConstants ;
import org.csap.agent.CsapThinTests ;
import org.csap.agent.container.C7 ;
import org.csap.agent.model.ModelJson ;
import org.csap.agent.model.ServiceInstance ;
import org.csap.agent.services.HostKeys ;
import org.csap.agent.services.OsManager ;
import org.csap.agent.stats.HostCollector ;
import org.csap.agent.stats.MetricCategory ;
import org.csap.agent.stats.OsProcessCollector ;
import org.csap.agent.stats.OsProcessEnum ;
import org.csap.agent.stats.ServiceCollector ;
import org.csap.agent.stats.service.HttpCollector ;
import org.csap.agent.stats.service.JmxCollector ;
import org.csap.agent.stats.service.JmxCustomCollector ;
import org.csap.helpers.CSAP ;
import org.csap.helpers.CsapApplication ;
import org.csap.integations.micrometer.CsapMicroMeter.HealthReport.Report ;
import org.junit.jupiter.api.Assumptions ;
import org.junit.jupiter.api.BeforeAll ;
import org.junit.jupiter.api.BeforeEach ;
import org.junit.jupiter.api.Disabled ;
import org.junit.jupiter.api.DisplayName ;
import org.junit.jupiter.api.Nested ;
import org.junit.jupiter.api.Test ;
import org.junit.jupiter.api.TestInstance ;
import org.springframework.boot.context.properties.ConfigurationProperties ;

import com.fasterxml.jackson.core.type.TypeReference ;
import com.fasterxml.jackson.databind.JsonNode ;
import com.fasterxml.jackson.databind.node.ObjectNode ;

@ConfigurationProperties ( prefix = "test.variables" )
public class Service_Collection_Test extends CsapThinTests {

	File collectionDefinition = new File( Service_Collection_Test.class.getResource( "application-collection.json" )
			.getPath( ) ) ;

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
		getOsManager( ).checkForProcessStatusUpdate( ) ;
		getOsManager( ).wait_for_initial_process_status_scan( 3 ) ;

		assertThat( getApplication( ).isBootstrapComplete( ) ).isTrue( ) ;

		assertThat( getApplication( ).getServiceInstanceCurrentHost( testService ) ).isNotNull( ) ;

		logger.debug( "latest discovery: {}",
				getOsManager( ).latest_process_discovery_results( ).path(
						C7.response_plain_text.val( ) ).asText( ) ) ;

	}

	// @ConfigurationProperties
	private String testService ;

	private String verifyService ;

	final static private String testK8Monitor = "k8s-csap-test-1" ;
	final static private String testK8DockerName = "csap-test-k8s-service" ;
	final static private String testK8Docker_CollectionId1 = testK8DockerName + "-1" ;
	final static private String testK8Docker_CollectionId2 = testK8DockerName + "-2" ;
	private String testDbService ;
	private String testDbHost ;
	private String testAdminHost1 ;

	@BeforeEach
	void beforeMethod ( ) {

		// Guard for setup

		Assumptions.assumeTrue( isSetupOk( ) && getApplication( ).metricManager( ).isCollectorsStarted( ) ) ;

	}

	boolean isSetupOk ( ) {

		logger.info( "testService: {}, {},  testDbHost: {}, testAdminHost1: {}",
				testService, getApplication( ).getServiceInstanceCurrentHost( testService ).toSummaryString( ),
				testDbHost, testAdminHost1 ) ;

		if ( testService == null || testDbHost == null || testAdminHost1 == null )
			return false ;

		return true ;

	}

	@Nested
	@DisplayName ( "Host Collections" )
	@TestInstance ( TestInstance.Lifecycle.PER_CLASS )
	class Host {

		@Test
		public void process_collection_for_host ( )
			throws Exception {

			ServiceInstance targetService = getApplication( ).getServiceInstanceCurrentHost( testService ) ;

			String[] targetServices = {
					CsapConstants.AGENT_NAME, testK8Monitor, testK8Docker_CollectionId1, testK8Docker_CollectionId2,
					targetService.getName( )
			} ;

			logger.info( CsapApplication.testHeader( "OS Collection for: {}" ), List.of( targetServices ) ) ;

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
			ObjectNode os_collection = osProcessCollector.buildCollectionReport( true, targetServices, 999, 0 ) ;

			logger.debug( "os_collection (attributes): \n {} ", CSAP.jsonPrint( os_collection ) ) ;
			logger.info( "servicesNotFound: \n {} ", CSAP.jsonPrint( os_collection.at(
					"/attributes/servicesNotFound" ) ) ) ;

			logger.info( "os_collection (data only): \n {} ", CSAP.jsonPrint( os_collection.path( "data" ) ) ) ;

			assertThat( os_collection.at( "/data/timeStamp/0" ).asLong( ) )
					.as( "/data/timeStamp/0" )
					.isGreaterThanOrEqualTo( now ) ;

			assertThat( os_collection.at( "/data/threadCount_" + CsapConstants.AGENT_NAME + "/0" ).asInt( ) )
					.as( "threadCount_CsAgent" )
					.isGreaterThan( 90 ) ;

//			assertThat( os_collection.at( "/data/fileCount_" + CsapCore.AGENT_NAME + "/0" ).asInt( ) )
//					.isGreaterThan( 99999 ) ;

			assertThat( os_collection.at( "/data/threadCount_" + targetService.getName( ) + "/0" ).asInt( ) )
					.as( "/data/threadCount_" + testService + "/0" )
					.isGreaterThan( 10 ) ;

			assertThat( os_collection.at( "/data/threadCount_" + testK8Monitor + "/0" ).asInt( ) )
					.as( "k8s monitor  service threads" )
					.isEqualTo( 104 ) ;

			assertThat( os_collection.at( "/data/threadCount_" + testK8Docker_CollectionId1 + "/0" ).asInt( ) )
					.as( "k8s docker  service threads" )
					.isEqualTo( 76 ) ;

			//
			// Verify osManger runtime
			//
			JsonNode runtimeReport = getOsManager( ).getHostRuntime( ) ;
			logger.info( "agent status: {}",
					CSAP.jsonPrint( runtimeReport.at( HostKeys.servicesJsonPath( CsapConstants.AGENT_ID ) ) ) ) ;

			var agentThreadPath = HostKeys.serviceMetricJsonPath( CsapConstants.AGENT_ID, 0,
					OsProcessEnum.THREAD_COUNT ) ;
			assertThat( runtimeReport.at( agentThreadPath ).intValue( ) )
					.as( agentThreadPath )
					.isEqualTo( 107 ) ;

			var agentNumSamplesPath = HostKeys.serviceMetricJsonPath(
					CsapConstants.AGENT_ID, 0,
					HostKeys.numberSamplesAveraged.jsonId ) ;
			assertThat( runtimeReport.at( agentNumSamplesPath ).intValue( ) )
					.as( agentNumSamplesPath )
					.isGreaterThanOrEqualTo( 1 ) ;

			//
			// Summary Report
			//
			osProcessCollector.buildCollectionReport( true, targetServices, 999, 0 ) ;
			osProcessCollector.buildCollectionReport( true, targetServices, 999, 0 ) ;
			logger.info( CsapApplication.testHeader( "testSummaryReport" ) ) ;
			JsonNode processSummaryReport = osProcessCollector.testSummaryReport( false ) ;
			logger.info( "processSummaryReport: {}", CSAP.jsonPrint( processSummaryReport ) ) ;

			var k8sInfo = CSAP.jsonStream( processSummaryReport )
					.filter( processCollected -> {

						return processCollected.path( "serviceName" ).asText( ).equals( testK8DockerName ) ;

					} )
					.findFirst( ) ;

			logger.info( "k8sInfo: {}", CSAP.jsonPrint( k8sInfo.get( ) ) ) ;
			assertThat( k8sInfo.get( ).path( OsProcessEnum.threadCount.json( ) ).asInt( ) )
					.as( "aggregate threads" )
					.isGreaterThanOrEqualTo( 77 ) ;

		}

	}

	//@Disabled
	@Nested
	@TestInstance ( TestInstance.Lifecycle.PER_CLASS )
	class JMX_Collection {

		@Test
		void verify_java_collection_for_SpringBoot ( )
			throws Exception {

			ServiceInstance targetService = getApplication( ).getServiceInstanceCurrentHost( testService ) ;
			logger.info( CsapApplication.testHeader( "service: {}" ), testService ) ;

			String[] services = {
					targetService.getName( )
			} ;
			ObjectNode javaGraphReport = performJavaCommonCollection( services, testDbHost ) ;

			logger.info( "Service Collection: \n {}", CSAP.jsonPrint( javaGraphReport.path( "data" ) ) ) ;

			var jpath = "/data/openFiles_" + targetService.getName( ) + "/0" ;
			assertThat( javaGraphReport.at( jpath ).asInt( ) )
					.as( jpath + " found" )
					.isGreaterThan( 0 ) ;

		}

		@Test
		void verify_java_and_application_collection_for_SpringBoot ( )
			throws Exception {

			ServiceInstance targetService = getApplication( ).getServiceInstanceCurrentHost( testService ) ;

			logger.info( CsapApplication.testHeader( "Collecting: {}" ), targetService.toSummaryString( ) ) ;

			String[] services = {
					targetService.getName( )
			} ;
			ObjectNode javaGraphReportWithCustomAdded = performJavaCommonAndCustomCollection( services, testDbHost ) ;

			var javaReportData = javaGraphReportWithCustomAdded.path( "data" ) ;

			var applicationGraphReport = javaGraphReportWithCustomAdded.path( "custom" + services[0] ) ;
			var applicationData = applicationGraphReport.path( "data" ) ;
			;

			logger.info( "javaReportData: \n {} \n\n applicationData: \n{}",
					CSAP.jsonPrint( javaReportData ),
					CSAP.jsonPrint( applicationData ) ) ;

			var openFilesPath = "/openFiles_" + targetService.getName( ) + "/0" ;
			assertThat( javaReportData.at( openFilesPath ).asInt( ) )
					.as( openFilesPath + " found" )
					.isGreaterThan( 0 ) ;

			var maxInstancePath = "/springDispatcherMaxInstances/0" ;
			assertThat( applicationData.at( maxInstancePath ).asInt( ) )
					.as( maxInstancePath + " found" )
					.isEqualTo( 20 ) ;

		}

		@Test
		public void verify_activemq_collection ( )
			throws Exception {

			ServiceInstance targetService = getApplication( ).getServiceInstanceCurrentHost( "activemq" ) ;

			logger.info( CsapApplication.testHeader( "Collecting: {}" ), targetService.toSummaryString( ) ) ;

			String[] services = {
					targetService.getName( )
			} ;
			ObjectNode javaAndApplicationReport = performJavaCommonAndCustomCollection( services, testDbHost ) ;

			var applicationGraphReport = (ObjectNode) javaAndApplicationReport.get( "custom" + services[0] ) ;

			logger.info( "collected: \n {}", CsapConstants.jsonPrint( getJsonMapper( ), javaAndApplicationReport ) ) ;

			// ensure non tomcat jvms skip jmx reporting of tomcat only attributes.
			assertThat( javaAndApplicationReport.at( "/attributes/graphs/httpKbytesReceived" ).fieldNames( )
					.hasNext( ) )
							.as( "/attributes/graphs/httpKbytesReceived" )
							.isFalse( ) ;
			assertThat( javaAndApplicationReport.at( "/attributes/graphs/httpKbytesSent" ).fieldNames( ).hasNext( ) )
					.as( "/attributes/graphs/httpKbytesSent" )
					.isFalse( ) ;

			assertThat( javaAndApplicationReport.at( "/data/openFiles_activemq/0" ).asInt( ) )
					.as( "/data/openFiles_activemq/0" )
					.isGreaterThan( 100 ) ;

			assertThat( javaAndApplicationReport.at( "/data" ).has( "sessionsActive_activemq" ) )
					.as( "/data should not have sessionsActive_activemq" )
					.isFalse( ) ;

			assertThat( applicationGraphReport.at( "/data/JvmThreadCount/0" ).asInt( ) )
					.as( "JvmThreadCount" )
					.isGreaterThan( 40 ) ;

			assertThat( applicationGraphReport.at( "/data/TotalConsumerCount/0" ).asInt( ) )
					.as( "TotalConsumerCount" )
					.isGreaterThan( 0 ) ;

		}

		@Test
		public void verify_java_service_upload_and_summary_report ( )
			throws Exception {

			logger.info( CsapApplication.testHeader( ) ) ;

			// csapApp.shutdown();
			ServiceCollector serviceCollector = new ServiceCollector(
					getCsapApis( ), 30, true ) ;

			// This will trigger a remote procedure call
			serviceCollector.shutdown( ) ;

			ServiceInstance targetService = getApplication( ).getServiceInstanceCurrentHost( testService ) ;

			serviceCollector.testJmxCollection( targetService, testDbHost ) ;

			String[] services = {
					targetService.getName( )
			} ;

			ObjectNode collectionReport = serviceCollector.buildCollectionReport( true, services, 999, 0 ) ;

			logger.info( "collectionReport: {} ", CSAP.jsonPrint( collectionReport ) ) ;

			assertThat( collectionReport.at( "/data/openFiles_" + targetService.getName( ) + "/0" ).asInt( ) )
					.as( "open files" )
					.isGreaterThan( 90 ) ;

			logger.info( "{}", CsapApplication.header( "invoking serviceCollector.uploadMetrics" ) ) ;

			String uploadResult = serviceCollector.uploadMetrics( 1 ) ;
			logger.info( "uploadResult: {}", uploadResult ) ;

			assertThat( uploadResult.contains( "Exception" ) ).isFalse( ) ;

			ObjectNode application_summary_data = serviceCollector.get_lastCustomServiceSummary( ) ;
			logger.info( "lastServiceSummary: \n {}", CSAP.jsonPrint( application_summary_data ) ) ;

			assertThat( application_summary_data.at( "/test-docker-csap-reference/springDispatcherMaxInstances" )
					.asInt( ) )
							.isEqualTo( 20 ) ;

		}

		private ObjectNode performJavaCommonCollection ( String[] services , String host ) {

			return performJavaCollection( services, host, false ) ;

		}

		private ObjectNode performJavaCommonAndCustomCollection ( String[] services , String host ) {

			return performJavaCollection( services, host, true ) ;

		}

		private ObjectNode performJavaCollection ( String[] services , String host , boolean isCustom ) {

			// csapApp.shutdown();
			ServiceCollector serviceCollector = new ServiceCollector( getCsapApis( ), 30, false ) ;

			getApplication( ).metricManager( ).setFirstServiceCollector( serviceCollector ) ;

			// shutdown to keep logs reasonable to trace during test.
			serviceCollector.shutdown( ) ;

			// runs collection based on application
			CSAP.setLogToDebug( ServiceCollector.class.getName( ) ) ;
			CSAP.setLogToDebug( JmxCollector.class.getName( ) ) ;
			CSAP.setLogToDebug( JmxCustomCollector.class.getName( ) ) ;

			for ( String serviceName : services ) {

				ServiceInstance svc = getApplication( ).getServiceInstanceCurrentHost( serviceName ) ;
				assertThat( svc ).as( "Found service in " + collectionDefinition ).isNotNull( ) ;
				serviceCollector.testJmxCollection( svc, host ) ;

			}
			//

			ObjectNode standardJavaData = serviceCollector.buildCollectionReport( false, services, 999, 0 ) ;
			CSAP.setLogToInfo( JmxCollector.class.getName( ) ) ;
			CSAP.setLogToInfo( ServiceCollector.class.getName( ) ) ;
			CSAP.setLogToInfo( JmxCustomCollector.class.getName( ) ) ;

			if ( isCustom ) {

				// add custom data into standard result for testing ease.
				for ( String serviceName : services ) {

					String[] customServices = {
							serviceName
					} ;
					ObjectNode customJavaData = serviceCollector.buildCollectionReport( false, customServices, 999, 0,
							"CustomJmxIsBeingUsed" ) ;
					standardJavaData.set( "custom" + serviceName, customJavaData ) ;

				}

			}

			return standardJavaData ;

		}

		@Disabled
		@Test
		public void jmx_collection_with_retries_all_failures ( )
			throws Exception {

			logger.info( CsapApplication.testHeader( ) ) ;

			// csapApp.shutdown();
			ServiceCollector serviceCollector = new ServiceCollector( getCsapApis( ), 30, false ) ;

			// shutdown to keep logs reasonable to trace during test.
			serviceCollector.shutdown( ) ;

			// runs collection based on application
			serviceCollector.setTestServiceTimeout( 75, testService, 999 ) ;
			serviceCollector.testJmxCollection( testDbHost ) ;
			String[] services = {
					CsapConstants.AGENT_ID, testService
			} ;
			ObjectNode results = serviceCollector.buildCollectionReport( false, services, 999, 0 ) ;

			logger.info( "Results: \n"
					+ getJsonMapper( ).writerWithDefaultPrettyPrinter( ).writeValueAsString( results ) ) ;

			assertThat( serviceCollector.getJmxCollector( ).getNumberOfCollectionAttempts( ) )
					.as( "getNumberOfCollectionAttempts" )
					.isGreaterThan( 1 ) ;

			assertThat( results.at( "/data/openFiles_CsAgent_8011/0" ).asInt( ) )
					.as( "/data/openFiles_CsAgent_8011/0" )
					.isGreaterThan( 100 ) ;

			assertThat( results.at( "/data/heapUsed_CsAgent_8011/0" ).asInt( ) )
					.as( "/data/heapUsed_CsAgent_8011/0" )
					.isGreaterThan( 10 ) ;

			assertThat( results.at( "/data/jvmThreadCount_" + testService + "/0" ).asInt( ) )
					.as( "threadCOunt" )
					.isEqualTo( 0 ) ;

		}

		@Disabled
		@Test
		public void jmx_collection_with_3_retries_and_success ( )
			throws Exception {

			logger.info( CsapApplication.testHeader( ) ) ;

			// csapApp.shutdown();
			ServiceCollector jmxRunnable = new ServiceCollector( getCsapApis( ), 30, false ) ;

			// shutdown to keep logs reasonable to trace during test.
			jmxRunnable.shutdown( ) ;

			// runs collection based on application
			jmxRunnable.setTestServiceTimeout( 75, testService, 3 ) ;
			jmxRunnable.testJmxCollection( testDbHost ) ;
			String[] services = {
					CsapConstants.AGENT_ID, testService
			} ;
			ObjectNode results = jmxRunnable.buildCollectionReport( false, services, 999, 0 ) ;

			logger.info( "Results: \n"
					+ getJsonMapper( ).writerWithDefaultPrettyPrinter( ).writeValueAsString( results ) ) ;

			assertThat( jmxRunnable.getJmxCollector( ).getNumberOfCollectionAttempts( ) )
					.as( "getNumberOfCollectionAttempts" )
					.isGreaterThan( 2 ) ;

			assertThat( results.at( "/data/openFiles_CsAgent_8011/0" ).asInt( ) )
					.as( "/data/openFiles_CsAgent_8011/0" )
					.isGreaterThan( 100 ) ;

			assertThat( results.at( "/data/heapUsed_CsAgent_8011/0" ).asInt( ) )
					.as( "/data/heapUsed_CsAgent_8011/0" )
					.isGreaterThan( 10 ) ;

			List<String> servicesAvailable = getJsonMapper( ).readValue(
					results.at( "/attributes/servicesAvailable" ).traverse( ), new TypeReference<List<String>>( ) {
					} ) ;
			assertThat( servicesAvailable )
					.as( "servicesAvailable" )
					.contains( "activemq", CsapConstants.AGENT_ID, testService ) ;

		}

		@Test
		public void verify_java_collection_for_multiple_services ( )
			throws Exception {

			logger.info( CsapApplication.testHeader( ) ) ;

			var activeMqId = "activemq" ;

			ServiceInstance testServiceInstance = getApplication( ).getServiceInstanceCurrentHost( testService ) ;

			String[] services = {
					testServiceInstance.getName( ), activeMqId
			} ;
			ObjectNode commonAndCustomResults = performJavaCommonAndCustomCollection( services, testDbHost ) ;

			logger.debug( "java and custom report: \n {}", CSAP.jsonPrint( commonAndCustomResults ) ) ;

			// var agentOpenFiles = commonAndCustomResults.at(
			// "/data/openFiles_CsAgent_8011" ) ;
			var serviceOpenFiles = commonAndCustomResults.at( "/data/openFiles_" + testServiceInstance.getName( ) ) ;
			logger.info( "targetService openFiles: {}", serviceOpenFiles ) ;

			ArrayList<String> servicesAvailable = getJsonMapper( ).readValue(
					commonAndCustomResults.at( "/attributes/servicesAvailable" ).traverse( ),
					new TypeReference<ArrayList<String>>( ) {
					} ) ;

			assertThat( servicesAvailable )
					.as( "servicesAvailable" )
					.contains( activeMqId ) ;

			assertThat( serviceOpenFiles.path( 0 ).asInt( ) )
					.as( "open files" )
					.isGreaterThan( 90 ) ;

			assertThat( commonAndCustomResults.at( "/data/heapUsed_" + activeMqId + "/0" ).asInt( ) )
					.as( "heap used" )
					.isGreaterThan( 10 ) ;

			assertThat( commonAndCustomResults.at( "/data/openFiles_" + testServiceInstance.getName( ) + "/0" )
					.asInt( ) )
							.as( "/data/openFiles_" + testServiceInstance.getName( ) + "/0" )
							.isGreaterThan( 88 ) ;

			// ObjectNode mqCustomResults = (ObjectNode) commonAndCustomResults.get(
			// "custom" + activeMqId ) ;
			// logger.debug( "agentCustomResults: \n {}", CSAP.jsonPrint( mqCustomResults )
			// ) ;
			//
			// var agentApplicationData=mqCustomResults.path( "data" ) ;
			// logger.info( "agentApplicationData: \n {}", CSAP.jsonPrint(
			// agentApplicationData ) ) ;

			// assertThat( agentApplicationData.at( "/SpringMvcRequests/0" ).asInt() )
			// .as( "SpringMvcRequests is in delta mode - so unless we collect twice, it
			// will be 0" )
			// .isLessThan( 10 ) ;
			//
			// assertThat( agentApplicationData.at( "/OsCommandsCounter/0" ).asInt() )
			// .as( "/data/UptimeInSeconds/0" )
			// .isGreaterThan( 1 ) ;

			ObjectNode mqCustomResults = (ObjectNode) commonAndCustomResults.get( "custom" + activeMqId ) ;

			logger.info( "mqCustomResults: \n {}", CSAP.jsonPrint( mqCustomResults ) ) ;

			assertThat( mqCustomResults.at( "/data/TotalConsumerCount/0" ).asInt( ) )
					.as( "TotalConsumerCount" )
					.isGreaterThan( 0 ) ;

		}

	}

	@Nested
	@DisplayName ( "Http Collections" )
	@TestInstance ( TestInstance.Lifecycle.PER_CLASS )
	class Http_Collection {

		@Test
		void verify_agent_collection ( )
			throws Exception {

			logger.info( CsapApplication.testHeader( ) ) ;

			var agentInstance = getApplication( ).flexFindFirstInstanceCurrentHost( CsapConstants.AGENT_NAME ) ;

			var agentUrl = agentInstance.resolveRuntimeVariables(
					agentInstance.getHttpCollectionSettings( ).path( ModelJson.httpCollectionUrl.jpath( ) )
							.asText( ) ) ;
			logger.info( "agent collection url: {}", agentUrl ) ;

			assertThat( agentUrl )
					.contains( CsapConstants.DEFAULT_DOMAIN ) ;

			CSAP.setLogToInfo( HttpCollector.class.getName( ) ) ;
			// CSAP.setLogToDebug( ServiceCollector.class.getName() ) ;
			var serviceCollector = new ServiceCollector( getCsapApis( ), 30,
					false ) ;
			serviceCollector.shutdown( ) ;
			serviceCollector.testHttpCollection( 10000 ) ;

			// CSAP.setLogToInfo( ServiceCollector.class.getName() ) ;
			CSAP.setLogToInfo( HttpCollector.class.getName( ) ) ;

			String[] services = {
					agentInstance.getName( )
			} ;

			//
			// Health collection
			//
			var latestHealthReport = agentInstance.getContainerStatusList( ).get( 0 ).getHealthReportCollected( ) ;
			logger.info( "{} \n\t latestHealthReport: {}", agentInstance, CSAP.jsonPrint( latestHealthReport ) ) ;

			assertThat(
					latestHealthReport.path( Report.healthy.json ).isMissingNode( ) )
							.as( "found health" )
							.isFalse( ) ;

			assertThat(
					latestHealthReport.path( Report.collectionCount.json ).asLong( ) )
							.as( "Report.collectionCount" )
							.isGreaterThan( 1 ) ;

			//
			// JAVA collection
			//

			var agentJavaReport = serviceCollector.buildCollectionReport( false, services, 999, 0 ) ;
			var agentJavaData = agentJavaReport.path( "data" ) ;

			logger.info( "agentJavaData: \n {}", CSAP.jsonPrint( agentJavaData ) ) ;

			assertThat(
					agentJavaData.at( "/jvmThreadCount_" + CsapConstants.AGENT_NAME + "/0" ).asLong( ) )
							.as( "jvm thread count" )
							.isGreaterThan( 10 ) ;

			//
			// Application collection
			//
			var agentApplicationReport = serviceCollector.buildCollectionReport(
					false, services, 999, 0,
					"CustomJmxIsBeingUsed" ) ;

			var agentApplicationData = agentApplicationReport.path( "data" ) ;
			logger.info( "agentApplicationData: \n {}", CSAP.jsonPrint( agentApplicationData ) ) ;

			assertThat(
					agentApplicationData.at( "/CommandsSinceRestart/0" ).asLong( ) )
							.as( "Commands Run" )
							.isGreaterThan( 10 ) ;

			logger.info( "{}", CsapApplication.header( "invoking serviceCollector.uploadMetrics" ) ) ;

			CSAP.setLogToInfo( ServiceCollector.class.getName( ) ) ;
			String appUploadResult = serviceCollector
					.uploadLastApplicationCollection( Set.of( agentInstance.getName( ) ) ) ;
			CSAP.setLogToInfo( ServiceCollector.class.getName( ) ) ;

			ObjectNode latestPublishReport = serviceCollector.getLatestApplicationMetricsPublished( ) ;
			logger.info( CsapApplication.testHeader( "latestPublishReport: \n {}" ), CsapConstants.jsonPrint(
					latestPublishReport ) ) ;

		}

		@Test
		public void verify_kubernetes_http_collection ( )
			throws Exception {

			logger.info( CsapApplication.testHeader( ) ) ;

//			CSAP.setLogToDebug( HttpCollector.class.getName( ) ) ;
//			CSAP.setLogToDebug( ServiceCollector.class.getName( ) ) ;

			var serviceCollector = new ServiceCollector(
					getCsapApis( ),
					30,
					false ) ;

			serviceCollector.shutdown( ) ;
			serviceCollector.testHttpCollection( 10000 ) ;

			String[] services = {
					testK8Docker_CollectionId1, testK8Docker_CollectionId2
			} ;
			var serviceGraphs = serviceCollector.buildCollectionReport( false, services, 999, 0,
					"CustomJmxIsBeingUsed" ) ;

			logger.info( "serviceGraphs: \n {}", CsapConstants.jsonPrint( getJsonMapper( ), serviceGraphs ) ) ;

			assertThat(
					serviceGraphs.at( "/attributes/id" ).asText( ) )
							.as( "collection id" )
							.isEqualTo( CsapApplication.COLLECTION_APPLICATION + "-" + testK8DockerName + "_30" ) ;

			assertThat(
					serviceGraphs.at( "/data/HttpRequests_" + testK8Docker_CollectionId1 + "/0" ).asInt( ) )
							.as( "HttpRequests" )
							.isEqualTo( 99 ) ;

			assertThat(
					serviceGraphs.at( "/attributes/servicesRequested" ).toString( ) )
							.as( "servicesRequested" )
							.contains( testK8Docker_CollectionId1, testK8Docker_CollectionId2 ) ;

			List<String> servicesAvailable = getJsonMapper( ).readValue(
					serviceGraphs.at( "/attributes/servicesAvailable" ).traverse( ),
					new TypeReference<List<String>>( ) {
					} ) ;

			assertThat( servicesAvailable )
					.as( "servicesAvailable" )
					.contains( testK8Docker_CollectionId1, testK8Docker_CollectionId2 ) ;

			assertThat(
					serviceGraphs.at( "/attributes/graphs/HttpRequestsMs/HttpRequestsMs_" + testK8Docker_CollectionId2 )
							.isMissingNode( ) )
									.as( "titles" )
									.isFalse( ) ;

			List<String> servicesRequested = getJsonMapper( ).readValue(
					serviceGraphs.at( "/attributes/servicesRequested" ).traverse( ),
					new TypeReference<List<String>>( ) {
					} ) ;

			assertThat( servicesRequested )
					.as( "servicesRequested" )
					.contains( testK8Docker_CollectionId1, testK8Docker_CollectionId2 ) ;

			String[] servicesSingle = {
					testK8Docker_CollectionId1
			} ;
			var serviceGraphsSingle = serviceCollector.buildCollectionReport( false, servicesSingle, 999, 0,
					"CustomJmxIsBeingUsed" ) ;

			logger.info( "serviceGraphsSingle: \n {}", CSAP.jsonPrint( serviceGraphsSingle ) ) ;
			assertThat(
					serviceGraphsSingle.at( "/attributes/servicesRequested" ).toString( ) )
							.as( "titles" )
							.doesNotContain( testK8Docker_CollectionId2 ) ;

			ObjectNode standardJavaData = serviceCollector.buildCollectionReport( false, services, 999, 0 ) ;
			logger.info( CsapApplication.header( "standardJavaData: \n {}" ), CSAP.jsonPrint( standardJavaData ) ) ;

			assertThat(
					standardJavaData.at( "/attributes/servicesRequested" ).toString( ) )
							.as( "titles" )
							.contains( testK8Docker_CollectionId2 ) ;

			assertThat(
					standardJavaData.at( "/data/jvmThreadCount_" + testK8Docker_CollectionId1 + "/0" ).asInt( ) )
							.as( "jvmThreadCount_csap" )
							.isEqualTo( 71 ) ;

			assertThat(
					standardJavaData.at( "/data/cpuPercent_" + testK8Docker_CollectionId1 + "/0" ).asInt( ) )
							.as( "cpuPercent_" )
							.isEqualTo( 2 ) ;

			assertThat(
					standardJavaData.at( "/data/sessionsActive_" + testK8Docker_CollectionId1 + "/0" ).asInt( ) )
							.as( "sessionsActive_" )
							.isEqualTo( 3 ) ;

			assertThat(
					standardJavaData.at( "/data/sessionsCount_" + testK8Docker_CollectionId1 + "/0" ).asInt( ) )
							.as( "sessionsCount_" )
							.isEqualTo( 0 ) ;

			// logger.info( k8TestService.getH );

			assertThat(
					standardJavaData.at( "/data/minorGcInMs_" + testK8Docker_CollectionId1 + "/0" ).asInt( ) )
							.as( "minorGcInMs" )
							.isEqualTo( 17240 ) ;

			CSAP.setLogToInfo( ServiceCollector.class.getName( ) ) ;
			CSAP.setLogToInfo( HttpCollector.class.getName( ) ) ;

		}

		@Test
		public void verify_real_time_report ( )
			throws Exception {

			logger.info( CsapApplication.testHeader( ) ) ;

			// var osProcessCollector = new OsProcessCollector( getApplication(),
			// getOsManager(), 30, false ) ;
			// osProcessCollector.shutdown();
			var osProcessCollector = getApplication( ).metricManager( ).getOsProcessCollector( -1 ) ;
			CSAP.setLogToInfo( OsProcessCollector.class.getName( ) ) ;
			osProcessCollector.testCollection( ) ;
			CSAP.setLogToInfo( OsProcessCollector.class.getName( ) ) ;

			var serviceCollector = new ServiceCollector( getCsapApis( ), 30,
					false ) ;
			serviceCollector.shutdown( ) ;
			serviceCollector.testHttpCollection( 10000 ) ;

			getApplication( ).metricManager( ).setFirstServiceCollector( serviceCollector ) ;
			getApplication( ).metricManager( ).setFirstOsProcessCollector( osProcessCollector ) ;

			CSAP.setLogToInfo( OsManager.class.getName( ) ) ;
			ObjectNode realTimeReport = getOsManager( ).buildRealTimeCollectionReport( ) ;
			CSAP.setLogToInfo( OsManager.class.getName( ) ) ;

			logger.info( "realTimeReport: {}", CSAP.jsonPrint( realTimeReport ) ) ;

			assertThat(
					realTimeReport
							.path( MetricCategory.osShared.json( ) )
							.path( "coresActive" )
							.asInt( -1 ) )
									.isGreaterThanOrEqualTo( 0 ) ;

			assertThat(
					realTimeReport
							.path( MetricCategory.osProcess.json( ) )
							.path( OsProcessEnum.topCpu + "_" + CsapConstants.AGENT_NAME )
							.asInt( ) )
									.isEqualTo( 9 ) ;

			assertThat(
					realTimeReport
							.path( MetricCategory.application.json( ) )
							.path( CsapConstants.AGENT_NAME )
							.path( "AdminPingsMeanMs" )
							.asInt( -1 ) )
									.isGreaterThanOrEqualTo( 0 ) ;

			assertThat(
					realTimeReport
							.path( MetricCategory.application.json( ) )
							.path( testK8DockerName )
							.path( "DbQueryWithFilter" )
							.asInt( ) )
									.isEqualTo( 622 ) ;

		}

		@Test
		public void verify_http_service_upload ( )
			throws Exception {

			logger.info( CsapApplication.testHeader( ) ) ;

			//
			// Perform a collection
			//
			ServiceCollector serviceCollector = new ServiceCollector( getCsapApis( ), 30, true ) ;
			serviceCollector.shutdown( ) ;
			serviceCollector.testHttpCollection( 500 ) ;

			String[] services = {
					testK8Docker_CollectionId1
			} ;
			ObjectNode collectionReport = serviceCollector.buildCollectionReport( true, services, 999, 0,
					"CustomJmxIsBeingUsed" ) ;

			logger.info( "collectionReport: {} ", CsapApplication.header( CSAP.jsonPrint( collectionReport ) ) ) ;

			assertThat(
					collectionReport.at( "/data/HttpRequests_" + testK8Docker_CollectionId1 + "/0" ).asInt( ) )
							.as( "HttpRequests" )
							.isEqualTo( 99 ) ;

			//
			// SUMMARY report
			//

			logger.info( "{}", CsapApplication.header( "invoking serviceCollector.uploadMetrics" ) ) ;

			CSAP.setLogToInfo( ServiceCollector.class.getName( ) ) ;
			String appUploadResult = serviceCollector
					.uploadLastApplicationCollection( Set.of( testK8Docker_CollectionId1,
							testK8Docker_CollectionId2 ) ) ;
			CSAP.setLogToInfo( ServiceCollector.class.getName( ) ) ;

			assertThat( appUploadResult.contains( "Exception" ) ).isFalse( ) ;

			ObjectNode lastSummaryReport = serviceCollector.get_lastCustomServiceSummary( ) ;
			logger.info( "lastServiceSummary: \n {}", CsapConstants.jsonPrint( lastSummaryReport ) ) ;

			assertThat(
					lastSummaryReport.at( "/" + testK8DockerName + "/HttpRequests" ).asInt( ) )
							.as( "HttpRequests" )
							.isEqualTo( 198 ) ;

			//
			// Java report
			//

			logger.info( "{}", CsapApplication.header( "building Java report" ) ) ;

			String[] javaServices = {
					testService, testK8Docker_CollectionId1, testK8Docker_CollectionId2
			} ;
			CSAP.setLogToInfo( ServiceCollector.class.getName( ) ) ;
			// ObjectNode javaCollection = serviceCollector.getJavaCollection( 1, 0,
			// testK8Docker_CollectionId1, testK8Docker_CollectionId2
			// ) ;
			ObjectNode javaCollection = serviceCollector.buildCollectionReport( true, javaServices, 999, 0 ) ;
			CSAP.setLogToInfo( ServiceCollector.class.getName( ) ) ;
			logger.debug( "javaCollection: {}", CSAP.jsonPrint( javaCollection ) ) ;

			CSAP.setLogToInfo( HostCollector.class.getName( ) ) ;
			JsonNode javaSummaryReport = serviceCollector.testSummaryReport( false ) ;
			CSAP.setLogToInfo( HostCollector.class.getName( ) ) ;
			logger.info( "javaSummaryReport: {}", CSAP.jsonPrint( javaSummaryReport ) ) ;

			assertThat(
					javaSummaryReport.at( "/0/httpRequestCount" ).asInt( ) )
							.as( "httpRequestCount" )
							.isEqualTo( 6030 ) ;

			//
			// LATEST app metrics published
			//

			ObjectNode latestPublishReport = serviceCollector.getLatestApplicationMetricsPublished( ) ;
			logger.info( CsapApplication.testHeader( "latestPublishReport: \n {}" ), CsapConstants.jsonPrint(
					latestPublishReport ) ) ;

			assertThat(
					latestPublishReport.at( "/data/data/HttpRequests_" + testK8Docker_CollectionId1 + "/0" ).asInt( ) )
							.as( "HttpRequests" )
							.isEqualTo( 99 ) ;

			logger.info( CsapApplication.header( "Verifying kubernetes service id" ) ) ;

			assertThat(
					latestPublishReport.at( "/attributes/id" ).asText( ) )
							.as( "/attributes/id" )
							.contains( testK8DockerName ) ;

			assertThat(
					latestPublishReport.at( "/attributes/graphs/HttpRequestsMs/HttpRequestsMs_"
							+ testK8Docker_CollectionId1 ).isMissingNode( ) )
									.as( "titles" )
									.isFalse( ) ;

			assertThat(
					latestPublishReport.at( "/attributes/graphs/HttpRequestsMs/HttpRequestsMs_" + testK8DockerName )
							.isMissingNode( ) )
									.as( "titles" )
									.isTrue( ) ;

			assertThat(
					latestPublishReport.at( "/data.location" ).asText( ).contains( testK8DockerName ) )
							.as( "data.location" )
							.isTrue( ) ;

			assertThat(
					latestPublishReport.at( "/attributes/servicesRequested" ).toString( ) )
							.as( "servicesRequested" )
							.contains( testK8Docker_CollectionId1, testK8Docker_CollectionId2 ) ;

			assertThat(
					latestPublishReport.at( "/attributes/servicesAvailable" ).toString( ).contains(
							testK8Docker_CollectionId1 ) )
									.as( "servicesAvailable" )
									.isTrue( ) ;

		}

		@Test
		public void verify_nginx_collection ( )
			throws Exception {

			logger.info( CsapApplication.testHeader( ) ) ;

			// csapApp.shutdown();
			ServiceCollector applicationCollector = new ServiceCollector( getCsapApis( ), 30, false ) ;

			// This will trigger a remote procedure call
			applicationCollector.shutdown( ) ;

			CSAP.setLogToDebug( HttpCollector.class.getName( ) ) ;
			// CSAP.setLogToInfo( OsManager.class.getName(), Level.DEBUG );
			applicationCollector.testHttpCollection( 10000 ) ;
			CSAP.setLogToInfo( HttpCollector.class.getName( ) ) ;

			String[] services = {
					"nginx"
			} ; // "Cssp3ReferenceMq_8241"
			// CsapCore.AGENT_ID
			ObjectNode httpStatistics = applicationCollector.buildCollectionReport( false, services, 999, 0,
					"CustomJmxIsBeingUsed" ) ;

			logger.info( "Results: \n {}",
					CsapConstants.jsonPrint( getJsonMapper( ), httpStatistics ) ) ;

			assertThat(
					httpStatistics.at( "/data/activeConnections/0" ).asInt( ) )
							.isEqualTo( 3 ) ;

			assertThat(
					httpStatistics.at( "/data/mathVerify/0" ).asDouble( ) )
							.isEqualTo( 1.13 ) ;

		}

		@Test
		public void verify_apache_web_server_collection ( )
			throws Exception {

			logger.info( CsapApplication.testHeader( ) ) ;

			// csapApp.shutdown();
			ServiceCollector applicationCollector = new ServiceCollector( getCsapApis( ), 30, false ) ;

			// This will trigger a remote procedure call
			applicationCollector.shutdown( ) ;

			CSAP.setLogToDebug( HttpCollector.class.getName( ) ) ;
			// CSAP.setLogToInfo( OsManager.class.getName(), Level.DEBUG );
			applicationCollector.testHttpCollection( 10000 ) ;
			CSAP.setLogToInfo( HttpCollector.class.getName( ) ) ;

			String[] services = {
					"httpd"
			} ;

			ObjectNode httpStatistics = applicationCollector.buildCollectionReport( false, services, 999, 0,
					"CustomJmxIsBeingUsed" ) ;

			logger.info( "Results: \n {}",
					CsapConstants.jsonPrint( getJsonMapper( ), httpStatistics ) ) ;

			assertThat(
					httpStatistics.at( "/data/IdleWorkers/0" ).asInt( ) )
							.as( "Verifying Idle Workers" )
							.isEqualTo( 99 ) ;

			assertThat(
					httpStatistics.at( "/data/BrokenConfg/0" ).asInt( ) )
							.as( "Verifying Broken Config" )
							.isEqualTo( 0 ) ;

			List<String> servicesAvailable = getJsonMapper( ).readValue(
					httpStatistics.at( "/attributes/servicesAvailable" ).traverse( ),
					new TypeReference<List<String>>( ) {
					} ) ;
			assertThat( servicesAvailable )
					.as( "servicesAvailable" )
					.contains( "httpd" ) ;

		}

		@Test
		public void verify_mongo_collection ( )
			throws Exception {

			logger.info( CsapApplication.testHeader( ) ) ;

			// csapApp.shutdown();
			ServiceCollector applicationCollector = new ServiceCollector( getCsapApis( ), 30, false ) ;

			// This will trigger a remote procedure call
			applicationCollector.shutdown( ) ;

			applicationCollector.testHttpCollection( 10000 ) ;

			String[] mongoServices = {
					"mongoDb"
			} ; // "Cssp3ReferenceMq_8241"
			// CsapCore.AGENT_ID
			ObjectNode mongoStatistics = applicationCollector
					.buildCollectionReport( false, mongoServices, 999, 0,
							"CustomJmxIsBeingUsed" ) ;

			logger.info( "mongoStatistics collected: \n {}", CSAP.jsonPrint( mongoStatistics ) ) ;

			assertThat(
					mongoStatistics.at( "/data/MongoActiveConnections/0" ).asInt( ) )
							.as( "Verifying active connections" )
							.isEqualTo( 7 ) ;

			assertThat(
					mongoStatistics.at( "/data/MongoMbTotalIn/0" ).asLong( ) )
							.as( "Verifying Total network bytes in" )
							.isEqualTo( 48973 ) ;

			assertThat(
					mongoStatistics.at( "/data/DemoMultiplyByInteger/0" ).asLong( ) )
							.as( "Verifying multiply int" )
							.isEqualTo( 5194837000L ) ;

			assertThat(
					mongoStatistics.at( "/data/DemoMultiplyByDecimal/0" ).asDouble( ) )
							.as( "Verifying multiply decimal" )
							.isEqualTo( 97947.3 ) ;

			assertThat(
					mongoStatistics.at( "/data/testRound/0" ).asDouble( ) )
							.as( "Verifying multiply decimal" )
							.isEqualTo( 99.9995 ) ;

		}

	}

	public void setTestAdminHost1 ( String testAdminHost1 ) {

		this.testAdminHost1 = testAdminHost1 ;

	}

	public void setTestDbHost ( String testDbHost ) {

		this.testDbHost = testDbHost ;

	}

	public String getTestService ( ) {

		return testService ;

	}

	public void setTestService ( String testService ) {

		this.testService = testService ;

	}

	public String getTestDbService ( ) {

		return testDbService ;

	}

	public void setTestDbService ( String testDbService ) {

		this.testDbService = testDbService ;

	}

	public String getVerifyService ( ) {

		return verifyService ;

	}

	public void setVerifyService ( String verifyService ) {

		this.verifyService = verifyService ;

	}

}
