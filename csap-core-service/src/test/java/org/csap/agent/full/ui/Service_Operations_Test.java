package org.csap.agent.full.ui ;

import static org.assertj.core.api.Assertions.assertThat ;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post ;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status ;

import java.io.File ;

import javax.inject.Inject ;

import org.apache.commons.io.FileUtils ;
import org.csap.agent.Agent_context_loaded ;
import org.csap.agent.ApplicationConfiguration ;
import org.csap.agent.CsapApis ;
import org.csap.agent.CsapBareTest ;
import org.csap.agent.CsapConstants ;
import org.csap.agent.integrations.VersionControl ;
import org.csap.agent.linux.OutputFileMgr ;
import org.csap.agent.model.Application ;
import org.csap.agent.model.ServiceInstance ;
import org.csap.agent.services.ServiceOsManager ;
import org.csap.helpers.CsapApplication ;
import org.jasypt.encryption.pbe.StandardPBEStringEncryptor ;
import org.junit.jupiter.api.Assertions ;
import org.junit.jupiter.api.Assumptions ;
import org.junit.jupiter.api.BeforeAll ;
import org.junit.jupiter.api.BeforeEach ;
import org.junit.jupiter.api.Disabled ;
import org.junit.jupiter.api.Tag ;
import org.junit.jupiter.api.Test ;
import org.junit.jupiter.api.TestInstance ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.beans.factory.annotation.Autowired ;
import org.springframework.boot.test.context.SpringBootTest ;
import org.springframework.http.MediaType ;
import org.springframework.test.context.ActiveProfiles ;
import org.springframework.test.web.servlet.MockMvc ;
import org.springframework.test.web.servlet.ResultActions ;
import org.springframework.test.web.servlet.setup.MockMvcBuilders ;
import org.springframework.web.context.WebApplicationContext ;

import com.fasterxml.jackson.databind.ObjectMapper ;
import com.fasterxml.jackson.databind.node.ObjectNode ;

@Tag ( "core" )

@SpringBootTest ( classes = ApplicationConfiguration.class )
@ActiveProfiles ( {
		CsapBareTest.PROFILE_JUNIT, "Service_Operations_Test"
} )

@TestInstance ( TestInstance.Lifecycle.PER_CLASS )

public class Service_Operations_Test {

	static {

		CsapApplication.initialize( "" ) ;

	}

	@Inject
	Application csapApplication ;
	@Inject
	CsapApis csapApis ;

	Logger logger = LoggerFactory.getLogger( getClass( ) ) ;

	String SUPPORTING_SAMPLE_A_PACKAGE = "Supporting Sample A" ;

	@Autowired
	WebApplicationContext wac ;

	MockMvc mockMvc ;

	@BeforeAll
	void beforeAll ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( "loading: mock" ) ) ;

		csapApplication.getProjectLoader( ).setAllowLegacyNames( true ) ;

		assertThat( csapApp.loadDefinitionForJunits( false, Agent_context_loaded.SIMPLE_TEST_DEFINITION ) ).as(
				"No Errors or warnings" )
				.isTrue( ) ;

		this.mockMvc = MockMvcBuilders.webAppContextSetup( this.wac ).build( ) ;

	}

	@BeforeEach
	void beforeEach ( ) {

		csapApp.setJvmInManagerMode( false ) ;
		csapApp.setAutoReload( false ) ;

		File buildFolder = csapApp.getCsapBuildFolder( ) ;
		logger.debug( "Deleting: {}", buildFolder.getAbsolutePath( ) ) ;
		FileUtils.deleteQuietly( buildFolder ) ;

		csapApis.osManager( ).wait_for_initial_process_status_scan( 5 ) ;

	}

	ObjectMapper jacksonMapper = new ObjectMapper( ) ;

	@Test
	public void activity_count_from_event_service ( )
		throws Exception {

		// First load a config with supporting services
		File csapApplicationDefinition = new File(
				getClass( ).getResource( Agent_context_loaded.TEST_DEFINITIONS
						+ "sub-project-localhost/clusterConfigMultiple.json" )
						.getPath( ) ) ;
		String message = "Loading a working definition with multiple supporting services: "
				+ csapApplicationDefinition.getAbsolutePath( ) ;

		logger.info( CsapApplication.TC_HEAD + message ) ;

		if ( ! csapApp.isCompanyVariableConfigured( "test.variables.user" ) ) {

			logger.info( "company variable not set - skipping test" ) ;
			return ;

		}

		assertThat( csapApp.loadDefinitionForJunits( false, csapApplicationDefinition ) ).as( "No Errors or warnings" )
				.isTrue( ) ;

		message = "Hitting /getServicesInLifeCycleSummary "
				+ "releasePackage: SampleDefaultPackage" ;
		logger.info( CsapApplication.TC_HEAD + message ) ;

		// mock does much validation.....
		ResultActions resultActions = mockMvc.perform( post( CsapConstants.SERVICE_URL + "/activityCount" )
				.accept( MediaType.APPLICATION_JSON ) ) ;

		// But you could do full parsing of the Json result if needed
		String responseText = resultActions
				.andExpect( status( ).isOk( ) )
				.andReturn( )
				.getResponse( )
				.getContentAsString( ) ;

		ObjectNode responseJson = (ObjectNode) jacksonMapper.readTree( responseText ) ;

		logger.info( jacksonMapper.writerWithDefaultPrettyPrinter( )
				.writeValueAsString( responseJson ) ) ;

		assertThat( responseJson.at( "/count" ).asInt( ) )
				.as( "activityCount" )
				.isGreaterThanOrEqualTo( 0 ) ;

	}

	@Test
	public void get_service_report_from_analytics_service ( )
		throws Exception {

		Assumptions.assumeTrue( csapApp.isCompanyVariableConfigured( "test.variables.testAppId" ) ) ;

		// First load a config with supporting services
		File csapApplicationDefinition = new File(
				getClass( ).getResource( Agent_context_loaded.TEST_DEFINITIONS
						+ "sub-project-localhost/clusterConfigMultiple.json" )
						.getPath( ) ) ;
		String message = "Loading a working definition with multiple supporting services: "
				+ csapApplicationDefinition.getAbsolutePath( ) ;
		logger.info( CsapApplication.TC_HEAD + message ) ;

		assertThat( csapApp.loadDefinitionForJunits( false, csapApplicationDefinition ) ).as( "No Errors or warnings" )
				.isTrue( ) ;

		message = "Hitting /report " ;
		logger.info( CsapApplication.TC_HEAD + message ) ;

		ResultActions resultActions = mockMvc.perform( post( CsapConstants.SERVICE_URL + "/report" )
				.param( "appId", csapApp.getCompanyConfiguration( "test.variables.testAppId", "yourAppid" ) )
				.param( "project", csapApp.getCompanyConfiguration( "test.variables.testProject", "CSAP Platform" ) )
				.param( "report", "service" )
				.param( "life", "dev" )
				.param( "numDays", "1" )
				.accept( MediaType.APPLICATION_JSON ) ) ;

		// But you could do full parsing of the Json result if needed
		String responseText = resultActions
				.andExpect( status( ).isOk( ) )
				.andReturn( )
				.getResponse( )
				.getContentAsString( ) ;

		ObjectNode responseJson = (ObjectNode) jacksonMapper.readTree( responseText ) ;

		logger.info( "report: {}", CsapConstants.jsonPrint( jacksonMapper, responseJson ) ) ;

		assertThat( responseJson.at( "/numDaysAvailable" ).asInt( ) )
				.as( "Number of days available" )
				.isGreaterThan( 1 ) ;

	}
//
//	/**
//	 *
//	 * Scenario: load multiple package cluster. Active model will be child....
//	 *
//	 * @throws Exception
//	 */
//	@Disabled
//	@Test
//	public void verify_service_summary_for_package_agent ( )
//			throws Exception {
//
//		File csapApplicationDefinition = new File(
//				getClass( ).getResource( Agent_context_loaded.TEST_DEFINITIONS
//						+ "sub-project-localhost/clusterConfigMultiple.json" )
//						.getPath( ) ) ;
//
//		String message = "Loading a working definition with multiple supporting services: "
//				+ csapApplicationDefinition.getAbsolutePath( ) ;
//
//		logger.info( CsapApplication.TC_HEAD + message ) ;
//
//		assertThat( csapApp.loadDefinitionForJunits( false, csapApplicationDefinition ) )
//				.as( "No Errors or warnings" )
//				.isTrue( ) ;
//
//		ServiceInstance agentService = csapApp.getServiceInstancePackage( CsapCore.AGENT_ID,
//				SUPPORTING_SAMPLE_A_PACKAGE ) ;
//
//		Map<String, String> limits = ServiceAlertsEnum.limitsForService( agentService, csapApp
//				.rootProjectEnvSettings( ) ) ;
//		logger.info( "agent limits: {}", limits ) ;
//
//		assertThat( limits.get( ServiceAlertsEnum.diskSpace.value ) )
//				.as( "No Errors or warnings" )
//				.isEqualTo( "50" ) ;
//
//		logger.info( "mockMvc.perform getServicesInLifeCycleSummary" ) ;
//
//		// mock does much validation.....
//		ResultActions resultActions = mockMvc.perform(
//				get( CsapCoreService.SERVICE_URL + ServiceRequests.SERVICE_SUMMARY_URI )
//						.param( "blocking", "true" )
//						.param( CsapCore.PROJECT_PARAMETER, SUPPORTING_SAMPLE_A_PACKAGE )
//						.accept( MediaType.APPLICATION_JSON ) ) ;
//
//		// But you could do full parsing of the Json result if needed
//		String responseText = resultActions
//				.andExpect( status( ).isOk( ) )
//				.andReturn( )
//				.getResponse( )
//				.getContentAsString( ) ;
//
//		ObjectNode serviceSummaryJson = (ObjectNode) jacksonMapper.readTree( responseText ) ;
//
//		logger.info( CSAP.jsonPrint( serviceSummaryJson ) ) ;
//
//		assertThat( serviceSummaryJson.at( "/totalServices" ).asInt( ) )
//				.as( "Service in definition" )
//				.isEqualTo( 3 ) ;
//
//		assertThat( serviceSummaryJson.at( "/hostStatus/localhost/cpuCount" ).asInt( ) )
//				.as( "Cpu count" )
//				.isGreaterThanOrEqualTo( 2 ) ;
//
//		assertThat( serviceSummaryJson.at( "/hostStatus/localhost/vmLoggedIn/0" ).asText( ) )
//				.as( "rtp found in vm messages" )
//				.contains( "rtp" ) ;
//
//		List<String> agentErrors = jacksonMapper.readValue( serviceSummaryJson.get( "errors" ).toString( ),
//				new TypeReference<List<String>>( ) {
//				} ) ;
//
//		assertThat( Arrays.asList( agentErrors ).toString( ) )
//				.as( "agentErrors contains: Running in single node mode" )
//				.contains( "Running in single node mode" ) ;
//		//
//		logger.info( "Errors: {}", agentErrors.toString( ).replaceAll( ",", ",\n" ) ) ;
//		// errer
//
//		assertThat( agentErrors )
//				.contains( "localhost: CsAgent: Disk Space: 72Mb, Alert threshold: 50Mb" ) ;
//
//		List<String> clusters = jacksonMapper.readValue(
//				serviceSummaryJson.get( "clusters" ).toString( ),
//				new TypeReference<List<String>>( ) {
//				} ) ;
//
//		assertThat( clusters )
//				.as( "Clusters in definition" )
//				.hasSize( 3 ) ;
//
//	}
//
//	@Test
//	public void getServicesInLifeCycleSummaryAllPackages ( )
//			throws Exception {
//
//		// First load a config with supporting services
//
//		File csapApplicationDefinition = new File(
//				getClass( ).getResource( Agent_context_loaded.TEST_DEFINITIONS
//						+ "sub-project-localhost/clusterConfigMultiple.json" )
//						.getPath( ) ) ;
//
//		String message = "Loading a working definition with multiple supporting services: "
//				+ csapApplicationDefinition.getAbsolutePath( ) ;
//		logger.info( CsapApplication.TC_HEAD + message ) ;
//
//		assertThat( csapApp.loadDefinitionForJunits( false, csapApplicationDefinition ) ).as( "No Errors or warnings" )
//				.isTrue( ) ;
//
//		message = "Hitting /getServicesInLifeCycleSummary "
//				+ "releasePackage: ALL_PACKAGES" ;
//		logger.info( CsapApplication.TC_HEAD + message ) ;
//
//		// mock does much validation.....
//		ResultActions resultActions = mockMvc.perform(
//				get( CsapCoreService.SERVICE_URL + ServiceRequests.SERVICE_SUMMARY_URI )
//						.param( "block", "false" )
//						.param( CsapCore.PROJECT_PARAMETER, Application.ALL_PACKAGES )
//						.accept( MediaType.APPLICATION_JSON ) ) ;
//
//		// But you could do full parsing of the Json result if needed
//		String responseText = resultActions
//				.andExpect( status( ).isOk( ) )
//				.andReturn( )
//				.getResponse( )
//				.getContentAsString( ) ;
//
//		ObjectNode summaryReport = (ObjectNode) jacksonMapper.readTree( responseText ) ;
//
//		logger.info( jacksonMapper.writerWithDefaultPrettyPrinter( )
//				.writeValueAsString( summaryReport ) ) ;
//
//		assertThat( summaryReport.get( "totalServices" ).asInt( ) ).isEqualTo( 3 ) ;
//
//		assertThat( summaryReport.at( "/hostStatus/localhost/cpuCount" ).asInt( ) ).isGreaterThanOrEqualTo( 2 ) ;
//
//		JsonNode errors = summaryReport.path( "errors" ) ;
//		logger.info( "Errors: {}", CSAP.jsonPrint( errors ) ) ;
//
//		assertThat( errors.isArray( ) ) ;
//		assertThat( errors.size( ) ).isGreaterThan( 1 ) ;
//
//		JsonNode clusters = summaryReport.path( "clusters" ) ;
//
//		assertThat( clusters.isArray( ) ) ;
//		assertThat( clusters.size( ) ).isEqualTo( 4 ) ;
//
//	}

	/**
	 *
	 * Scenario: Load a multi file package, and get service summary using sub
	 * package filter and cluster filter
	 *
	 * @throws Exception
	 */
//	@Test
//	public void getServicesInLifeCycleSummaryMultiple ( )
//			throws Exception {
//
//		// First load a config with supporting services
//
//		File csapApplicationDefinition = new File(
//				getClass( ).getResource( Agent_context_loaded.TEST_DEFINITIONS
//						+ "sub-project-localhost/clusterConfigMultiple.json" )
//						.getPath( ) ) ;
//
//		String message = "Loading a working definition with multiple supporting services: "
//				+ csapApplicationDefinition.getAbsolutePath( ) ;
//		logger.info( CsapApplication.testHeader( message ) ) ;
//
//		assertThat( csapApp.loadDefinitionForJunits( false, csapApplicationDefinition ) ).as( "No Errors or warnings" )
//				.isTrue( ) ;
//
//		message = "Invoking getServicesInLifeCycleSummary..... returns JSON" ;
//		logger.info( message ) ;
//
//		//
//		// First get the default list
//		//
//		ResultActions resultActions = mockMvc.perform(
//				get( CsapCoreService.SERVICE_URL + ServiceRequests.SERVICE_SUMMARY_URI )
//						.param( "block", "false" )
//						.param( CsapCore.PROJECT_PARAMETER, "SampleDefaultPackage" )
//						.accept( MediaType.APPLICATION_JSON ) ) ;
//
//		// But you could do full parsing of the Json result if needed
//		JsonNode responseJsonNode = jacksonMapper.readTree( resultActions.andReturn( ).getResponse( )
//				.getContentAsString( ) ) ;
//
//		logger.info( "result:\n"
//				+ CSAP.jsonPrint(
//						responseJsonNode ) ) ;
//
//		MvcResult mvcResult = resultActions.andExpect( status( ).isOk( ) )
//				.andExpect( content( ).contentTypeCompatibleWith( MediaType.APPLICATION_JSON ) ).andReturn( ) ;
//
//		Assertions.assertTrue(
//				responseJsonNode.get( "hostStatus" ).get( "localhost" ).get( "cpuCount" ) != null ) ;
//		Assertions.assertTrue( responseJsonNode.get( "hostStatus" ).get( "localhost" ).get( "cpuCount" )
//				.asInt( ) >= 2 ) ;
//
//		//
//		// Now get the release package list
//		//
//		message = "Invoking getServicesInLifeCycleSummary with SSO Filter..... returns JSON" ;
//		resultActions = mockMvc.perform(
//				get( CsapCoreService.SERVICE_URL + ServiceRequests.SERVICE_SUMMARY_URI )
//						.param( "block", "false" )
//						.param( CsapCore.PROJECT_PARAMETER, SUPPORTING_SAMPLE_A_PACKAGE )
//						.accept( MediaType.APPLICATION_JSON ) ) ;
//
//		// But you could do full parsing of the Json result if needed
//		responseJsonNode = jacksonMapper.readTree( resultActions.andReturn( ).getResponse( )
//				.getContentAsString( ) ) ;
//		logger.info( "result:\n"
//				+ CSAP.jsonPrint(
//						responseJsonNode ) ) ;
//
//		mvcResult = resultActions.andExpect( status( ).isOk( ) )
//				.andExpect( content( ).contentTypeCompatibleWith( MediaType.APPLICATION_JSON ) ).andReturn( ) ;
//
//		Assertions.assertTrue(
//				responseJsonNode.get( "servicesTotal" ).get( "SampleJvmInA" ) != null ) ;
//
//		assertThat( responseJsonNode.get( "servicesTotal" ).get( "SampleJvmInA" ).asInt( ) ).isEqualTo( 1 ) ;
//
//		//
//		// Now get the release package list using cluster filter. Since we are
//		// running in standalone - it should be empty
//		//
//		message = "Invoking getServicesInLifeCycleSummary with SSO Filter..... returns JSON" ;
//		resultActions = mockMvc.perform(
//				get( CsapCoreService.SERVICE_URL + ServiceRequests.SERVICE_SUMMARY_URI )
//						.param( "block", "false" )
//						.param( CsapCore.PROJECT_PARAMETER, SUPPORTING_SAMPLE_A_PACKAGE )
//						.param( "cluster", "dev:middlewareA2" )
//						.accept( MediaType.APPLICATION_JSON ) ) ;
//
//		// But you could do full parsing of the Json result if needed
//		responseJsonNode = jacksonMapper.readTree( resultActions.andReturn( ).getResponse( )
//				.getContentAsString( ) ) ;
//
//		logger.info( "result:\n", CSAP.jsonPrint( responseJsonNode ) ) ;
//
//		mvcResult = resultActions
//				.andExpect( status( ).isOk( ) )
//				.andExpect( content( ).contentTypeCompatibleWith( MediaType.APPLICATION_JSON ) ).andReturn( ) ;
//
//		assertThat( responseJsonNode.get( "totalHostsActive" ).asInt( ) )
//				.as( "Verifying standalone mode that sup package services are not active" )
//				.isEqualTo( 0 ) ;
//
//	}
//
//	@Disabled
//	@Test
//	public void getServicesMultipleManager ( )
//			throws Exception {
//
//		File csapApplicationDefinition = new File(
//				getClass( ).getResource( Agent_context_loaded.TEST_DEFINITIONS
//						+ "sub-project-release-file/clusterConfigManager.json" )
//						.getPath( ) ) ;
//
//		String message = "Loading a working definition with multiple supporting services: "
//				+ csapApplicationDefinition.getAbsolutePath( ) ;
//
//		logger.info( CsapApplication.TC_HEAD + message ) ;
//
//		// Configure the manager...
//		csapApp.setJvmInManagerMode( true ) ;
//		HostStatusManager testHostStatusManager = new HostStatusManager( "CsAgent_Host_Response.json", csapApp ) ;
//
//		csapApp.setHostStatusManager( testHostStatusManager ) ;
//
//		assertThat( csapApp.loadDefinitionForJunits( false, csapApplicationDefinition ) ).as( "No Errors or warnings" )
//				.isTrue( ) ;
//
//		message = "Invoking getServicesInLifeCycleSummary..... returns JSON" ;
//		logger.info( CsapApplication.TC_HEAD + message ) ;
//
//		//
//		// First get the default list
//		//
//		ResultActions resultActions = mockMvc.perform(
//				get( CsapCoreService.SERVICE_URL + ServiceRequests.SERVICE_SUMMARY_URI )
//						.param( "blocking", "true" )
//						.param( CsapCore.PROJECT_PARAMETER, "SampleDefaultPackage" )
//						.accept( MediaType.APPLICATION_JSON ) ) ;
//
//		// But you could do full parsing of the Json result if needed
//		JsonNode service_summary_ui = jacksonMapper.readTree(
//				resultActions.andReturn( ).getResponse( )
//						.getContentAsString( ) ) ;
//
//		logger.info( "service_summary_ui: {}", CSAP.jsonPrint( service_summary_ui ) ) ;
//
//		MvcResult mvcResult = resultActions.andExpect( status( ).isOk( ) )
//				.andExpect( content( ).contentTypeCompatibleWith( MediaType.APPLICATION_JSON ) ).andReturn( ) ;
//		//
//		Assertions.assertTrue(
//				service_summary_ui.get( "hostStatus" ).get( "localhost" ).get( "cpuCount" ) != null ) ;
//		Assertions.assertTrue( service_summary_ui.get( "hostStatus" ).get( "localhost" ).get( "cpuCount" )
//				.asInt( ) >= 2 ) ;
//
//		Assertions.assertTrue(
//				service_summary_ui.get( "hostStatus" ).get( "csap-dev01" ).get( "cpuCount" ) != null ) ;
//		Assertions.assertTrue( service_summary_ui.get( "hostStatus" ).get( "csap-dev01" ).get( "cpuCount" )
//				.asInt( ) >= 2 ) ;
//
//		assertThat( service_summary_ui.get( "servicesTotal" ).get( "Factory2Sample" ).asInt( ) ).isEqualTo( 2 ) ;
//
//		//
//		// Now get the service instance filtered by default release package
//		//
//		List<ServiceInstance> factoryInstances = csapApp.serviceInstancesByName( "SampleDefaultPackage",
//				"Factory2Sample" ) ;
//		logger.info( "factoryInstances: {}", factoryInstances ) ;
//		assertThat( factoryInstances.size( ) )
//				.as( "factoryInstances  count" )
//				.isEqualTo( 2 ) ;
//
//		message = "Invoking getServiceInstances with releaseFilter ..... returns JSON" ;
//		logger.info( CsapApplication.TC_HEAD + message ) ;
//
//		resultActions = mockMvc.perform(
//				get( CsapCoreService.SERVICE_URL + ServiceRequests.INSTANCES_URI ).param( "blocking", "false" )
//						.param( "serviceName", "Factory2Sample" )
//						.param( CsapCore.PROJECT_PARAMETER, "SampleDefaultPackage" )
//						.accept( MediaType.APPLICATION_JSON ) ) ;
//
//		// But you could do full parsing of the Json result if needed
//		JsonNode service_instances = jacksonMapper.readTree( resultActions.andReturn( ).getResponse( )
//				.getContentAsString( ) ) ;
//		logger.info( "service_instances:  {}", CSAP.jsonPrint( service_instances ) ) ;
//
//		mvcResult = resultActions
//				.andExpect( status( ).isOk( ) )
//				.andExpect( content( ).contentTypeCompatibleWith( MediaType.APPLICATION_JSON ) )
//				.andReturn( ) ;
//
//		// assertEquals( "Found at least 1 service status", 2, responseJsonNode.get(
//		// "serviceStatus" )
//		// .size() );
//		assertThat( service_instances.get( "serviceStatus" ).size( ) )
//				.as( "service instance count" )
//				.isEqualTo( 2 ) ;
//
//		assertThat(
//				service_instances.get( "serviceStatus" ).get( 0 ).get( "host" ).asText( ) )
//						.isEqualTo( "localhost" ) ;
//
//		//
//		// Now get the service summary filtered by release package
//		//
//		message = "Invoking getServicesInLifeCycleSummary with releaseFilter ..... returns JSON" ;
//		logger.info( CsapApplication.TC_HEAD + message ) ;
//
//		resultActions = mockMvc.perform(
//				get( CsapCoreService.SERVICE_URL + ServiceRequests.SERVICE_SUMMARY_URI )
//						.param( "blocking", "false" )
//						.param( CsapCore.PROJECT_PARAMETER, SUPPORTING_SAMPLE_A_PACKAGE )
//						.accept( MediaType.APPLICATION_JSON ) ) ;
//
//		// But you could do full parsing of the Json result if needed
//		service_summary_ui = jacksonMapper.readTree(
//				resultActions.andReturn( ).getResponse( ).getContentAsString( ) ) ;
//
//		logger.info( "/getServicesInLifeCycleSummary result:\n"
//				+ CSAP.jsonPrint(
//						service_summary_ui ) ) ;
//
//		mvcResult = resultActions
//				.andExpect( status( ).isOk( ) )
//				.andExpect( content( ).contentTypeCompatibleWith( MediaType.APPLICATION_JSON ) ).andReturn( ) ;
//
//		Assertions.assertTrue(
//				service_summary_ui.get( "hostStatus" ).get( "sampleHostA-dev01" ).get( "cpuCount" ) != null ) ;
//		Assertions.assertTrue(
//				service_summary_ui.get( "hostStatus" ).get( "sampleHostA-dev01" ).get( "cpuCount" ).asInt( ) >= 2 ) ;
//
//		assertThat( service_summary_ui.get( "servicesTotal" ).get( "SampleJvmInA" ).asInt( ) )
//				.isEqualTo( 1 ) ;
//
//		//
//		// Now get the service summary filtered by release package and cluster
//		// name
//		//
//		message = "Invoking getServicesInLifeCycleSummary with releaseFilter ..... returns JSON" ;
//		logger.info( CsapApplication.TC_HEAD + message ) ;
//
//		resultActions = mockMvc.perform(
//				get( CsapCoreService.SERVICE_URL + ServiceRequests.SERVICE_SUMMARY_URI )
//						.param( "blocking", "false" )
//						.param( CsapCore.PROJECT_PARAMETER, SUPPORTING_SAMPLE_A_PACKAGE )
//						.param( "cluster", "dev-middlewareA2" )
//						.accept( MediaType.APPLICATION_JSON ) ) ;
//
//		// But you could do full parsing of the Json result if needed
//		JsonNode summaryResponseWithClusterFiltering = //
//				jacksonMapper.readTree( resultActions.andReturn( ).getResponse( ).getContentAsString( ) ) ;
//
//		logger.info( "/getServicesInLifeCycleSummary result: {}", CSAP.jsonPrint(
//				summaryResponseWithClusterFiltering ) ) ;
//
//		mvcResult = resultActions
//				.andExpect( status( ).isOk( ) )
//				.andExpect( content( ).contentTypeCompatibleWith( MediaType.APPLICATION_JSON ) ).andReturn( ) ;
//
//		assertThat( summaryResponseWithClusterFiltering.get( "hostStatus" ).get( "sampleHostA-dev01" ) == null )
//				.as( "No status for filtered  host: sampleHostA-dev01 " )
//				.isTrue( ) ;
//
//		// Assertions.assertTrue( message, responseJsonNode.get( "hostStatus" ).get(
//		// "sampleHostA-dev01" ) == null );
//
//		// Assertions.assertTrue( message,
//		// responseJsonNode.get( "hostStatus" ).get( "sampleHostA2-dev02" ).get(
//		// "cpuCount" )
//		// .asInt() >= 2 );
//
//		assertThat( summaryResponseWithClusterFiltering.get( "hostStatus" ).get( "sampleHostA2-dev02" ).get(
//				"cpuCount" ).asInt( ) )
//						.as( "found cpu count" )
//						.isGreaterThanOrEqualTo( 2 ) ;
//
//		assertThat(
//				summaryResponseWithClusterFiltering.get( "servicesTotal" ).get( "SampleJvmInA2" ).asInt( ) )
//						.isEqualTo( 1 ) ;
//
//		//
//		// Now get the service instance filtered by release package
//		//
//		message = "Invoking getServiceInstances with releaseFilter ..... returns JSON" ;
//		logger.info( CsapApplication.TC_HEAD + message ) ;
//
//		resultActions = mockMvc.perform(
//				get( CsapCoreService.SERVICE_URL + ServiceRequests.INSTANCES_URI )
//						.param( "blocking", "false" )
//						.param( "serviceName", "SampleJvmInA" )
//						.param( CsapCore.PROJECT_PARAMETER, SUPPORTING_SAMPLE_A_PACKAGE ).accept(
//								MediaType.APPLICATION_JSON ) ) ;
//
//		// But you could do full parsing of the Json result if needed
//		service_summary_ui = jacksonMapper.readTree( resultActions.andReturn( ).getResponse( )
//				.getContentAsString( ) ) ;
//
//		logger.info( "/getServiceInstances result:\n"
//				+ CSAP.jsonPrint(
//						service_summary_ui ) ) ;
//
//		mvcResult = resultActions
//				.andExpect( status( ).isOk( ) )
//				.andExpect( content( ).contentTypeCompatibleWith( MediaType.APPLICATION_JSON ) ).andReturn( ) ;
//
//		Assertions.assertTrue( service_summary_ui.get( "serviceStatus" ).size( ) >= 1 ) ;
//
//		assertThat(
//				service_summary_ui.get( "serviceStatus" ).get( 0 ).get( "host" ).asText( ) )
//						.isEqualTo( "sampleHostA-dev01" ) ;
//
//		//
//		// Now get the service instance using all package
//		//
//		message = "Invoking getServicesInLifeCycleSummary with releaseFilter ..... returns JSON" ;
//		logger.info( CsapApplication.TC_HEAD + message ) ;
//
//		resultActions = mockMvc.perform(
//				get( CsapCoreService.SERVICE_URL + ServiceRequests.SERVICE_SUMMARY_URI )
//						.param( "blocking", "false" )
//						.param( CsapCore.PROJECT_PARAMETER, Application.ALL_PACKAGES )
//						.accept( MediaType.APPLICATION_JSON ) ) ;
//
//		// But you could do full parsing of the Json result if needed
//		service_summary_ui = jacksonMapper.readTree(
//				resultActions.andReturn( ).getResponse( )
//						.getContentAsString( ) ) ;
//
//		logger.info( "ALL /getServicesInLifeCycleSummary result:\n"
//				+ CSAP.jsonPrint( service_summary_ui ) ) ;
//
//		mvcResult = resultActions
//				.andExpect( status( ).isOk( ) )
//				.andExpect( content( ).contentTypeCompatibleWith( MediaType.APPLICATION_JSON ) ).andReturn( ) ;
//
//		logger.info( "All Services :\n"
//				+ CSAP.jsonPrint(
//						service_summary_ui.get( "servicesTotal" ) ) ) ;
//
//		assertThat( service_summary_ui.get( "servicesTotal" ).size( ) ).isEqualTo( 18 ) ;
//		assertThat( service_summary_ui.get( "servicesTotal" ).get( CsapCore.AGENT_NAME ).asInt( ) ).isEqualTo( 7 ) ;
//		assertThat( service_summary_ui.get( "hostStatus" ).size( ) ).isEqualTo( 7 ) ;
//
//		assertThat( service_summary_ui.get( "hostStatus" ).get( "sampleHostA-dev01" ) ).isNotNull( ) ;
//
//		Assertions.assertTrue(
//				service_summary_ui.get( "hostStatus" ).get( "sampleHostA2-dev02" ).get( "cpuCount" )
//						.asInt( ) >= 2 ) ;
//
//		assertThat(
//				service_summary_ui.get( "servicesTotal" ).get( "SampleJvmInA2" ).asInt( ) )
//						.isEqualTo( 1 ) ;
//
//	}

	@Inject
	Application csapApp ;

	@Inject
	ServiceOsManager serviceManager ;
	// ServiceRequests serviceController;

	@Inject
	private StandardPBEStringEncryptor encryptor ;

	@Test
	public void build_service_using_git_no_auth_required ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( "loading: {}" ), Agent_context_loaded.SIMPLE_TEST_DEFINITION ) ;

		assertThat( csapApp.loadDefinitionForJunits( false, Agent_context_loaded.SIMPLE_TEST_DEFINITION ) ).as(
				"No Errors or warnings" )
				.isTrue( ) ;

		String svcName = "simple-service_8241" ;
		logger.debug( "git Service: {}", csapApp.getServiceInstanceCurrentHost( svcName ) ) ;
		assertThat( csapApp.getServiceInstanceCurrentHost( svcName ) ).isNotNull( ) ;

		String scmCommand = "-Dmaven.test.skip=true clean package deploy" ;
		String javaOpts = "-Xms128M -Xmx128M -XX:MaxPermSize=128m" ;
		String scmUserid = "" ; // general service - use empty userid to
								// bypass
								// authentication checks
		String scmPass = "" ;
		String mavenDeployArtifact = null ;
		String targetScpHosts = "" ;
		String hotDeploy = null ;
		String scmBranch = VersionControl.GIT_NO_BRANCH ;
		OutputFileMgr outputFm = new OutputFileMgr( csapApp.getCsapWorkingFolder( ), "/"
				+ svcName + "_testDeploy" ) ;

		ServiceInstance serviceInstance = csapApp.getServiceInstanceCurrentHost( svcName ) ;

		boolean sourceCheckoutOk = serviceManager.deployService(
				serviceInstance, null, "ms" + System.currentTimeMillis( ), "junit",
				scmUserid, encryptor.encrypt( scmPass ),
				scmBranch, mavenDeployArtifact,
				scmCommand, targetScpHosts, hotDeploy, javaOpts, outputFm ) ;

		assertThat( sourceCheckoutOk )
				.as( "Source Checked out ok" )
				.isTrue( ) ;

		logger.info( "serviceManager.deployService output: {}", outputFm.getOutputFile( ).getAbsolutePath( ) ) ;

		assertThat( outputFm.getOutputFile( ) )
				.as( "buildOutput file found" )
				.exists( )
				.isFile( ) ;

		String buildOutput = FileUtils.readFileToString( outputFm.getOutputFile( ) ) ;
		assertThat( buildOutput )
				.as( "Not updating an existing branch" )
				.doesNotContain( "Updating existing branch on git repository" ) ;

		File serviceBuildFolder = csapApp.getCsapBuildFolder( svcName ) ;
		File buildPom = new File( serviceBuildFolder, serviceInstance.getScmBuildLocation( ) + "/pom.xml" ) ;

		logger.info( "Verifying maven build file: {}", buildPom.getAbsolutePath( ) ) ;

		assertThat( buildPom )
				.as( "Pom file found" )
				.exists( ).isFile( ) ;

		logger.info( "Deleting: {}", serviceBuildFolder.getAbsolutePath( ) ) ;
		FileUtils.deleteQuietly( serviceBuildFolder ) ;

	}

	/**
	 *
	 * Scenario: Issue rebuild, ensure password is encrypted
	 *
	 * @throws Exception
	 */
	@Disabled
	public void build_service_using_svn ( )
		throws Exception {

		File csapApplicationDefinition = new File(
				getClass( ).getResource( Agent_context_loaded.TEST_DEFINITIONS
						+ "sub-project-localhost/clusterConfigMultiple.json" )
						.getPath( ) ) ;

		String message = "Loading a working definition with multiple supporting services: "
				+ csapApplicationDefinition.getAbsolutePath( ) ;
		logger.info( CsapApplication.TC_HEAD + message ) ;

		assertThat( csapApp.loadDefinitionForJunits( false, csapApplicationDefinition ) ).as( "No Errors or warnings" )
				.isTrue( ) ;

		//
		ServiceInstance testInstance = csapApp.getServiceInstanceCurrentHost( "SampleJvmInA_8041" ) ;

		Assertions.assertTrue( testInstance != null ) ;
		File deployLogFile = new File( csapApp.getCsapWorkingFolder( ), testInstance.getServiceName_Port( )
				+ ServiceOsManager.DEPLOY_OP + ".log" ) ;
		FileUtils.deleteQuietly( deployLogFile ) ;

		Assertions.assertTrue( deployLogFile.exists( ) == false ) ;

		message = "Hitting /rebuildServer "
				+ "releasePackage: SampleDefaultPackage. service: " + testInstance.getServiceName_Port( ) ;

		logger.info( CsapApplication.TC_HEAD + message ) ;

		String testPassword = "shouldNeverBeInLogs" ;

		// mock does much validation.....
		ResultActions resultActions = mockMvc.perform(
				post( CsapConstants.SERVICE_URL + "/rebuildServer" )
						.param( "scmUserid", "testUser" )
						.param( "scmPass", testPassword )
						.param( "scmBranch", "false" )
						.param( "hostName", "localhost" )
						.param( "serviceName", "SampleJvmInA_8041" )
						.accept( MediaType.APPLICATION_JSON ) ) ;

		// But you could do full parsing of the Json result if needed
		String buildResponse = resultActions
				.andExpect( status( ).isOk( ) )
				.andReturn( ).getResponse( ).getContentAsString( ) ;

		logger.info( "result:\n" + buildResponse + "\n deployLogFile: " + deployLogFile.getAbsolutePath( ) ) ;

		// RaceCondition on async build
		// Assertions.assertTrue("Assert Deploy Log file created: ",
		// deployLogFile.exists());
		// junitLogs will be relative to run dir
		File jvmLogFile = new File( "target/logs/junit-logs.txt" ) ;
		String jvmLogs = FileUtils.readFileToString( jvmLogFile ) ;
		// We removed the password from the build output

		Assertions.assertTrue( ! jvmLogs.contains( testPassword ) ) ;

		// Builds are done on background thread because they can take a while.
		// Junit needs to poll waiting for completion
		//
		String deployLogs = FileUtils.readFileToString( deployLogFile ) ;
		int numAttempts = 0 ;

		while ( numAttempts++ < 20 ) {

			deployLogs = FileUtils.readFileToString( deployLogFile ) ;

			if ( deployLogs.contains( OutputFileMgr.OUTPUT_COMPLETE_TOKEN ) ) {

				break ;

			}

			logger.info( "Sleeping for build attempt to complete" ) ;
			Thread.sleep( 1000 ) ;

		}

		Assertions.assertTrue( deployLogs.contains( "__ERROR: SVN Failure" ) ) ;

	}

}
