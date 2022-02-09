package org.csap.agent.full.ui ;

import static org.assertj.core.api.Assertions.assertThat ;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get ;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content ;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status ;

import java.io.File ;
import java.util.List ;
import java.util.stream.Collectors ;

import javax.inject.Inject ;

import org.csap.agent.CsapApis ;
import org.csap.agent.CsapBareTest ;
import org.csap.agent.CsapConstants ;
import org.csap.agent.container.kubernetes.K8 ;
import org.csap.agent.model.Application ;
import org.csap.agent.model.HealthForAgent ;
import org.csap.agent.model.ProcessRuntime ;
import org.csap.agent.model.ProjectLoader ;
import org.csap.agent.model.ServiceInstance ;
import org.csap.agent.stats.OsProcessEnum ;
import org.csap.agent.ui.rest.ApplicationBrowser ;
import org.csap.agent.ui.rest.FileRequests ;
import org.csap.agent.ui.rest.ServiceRequests ;
import org.csap.helpers.CSAP ;
import org.csap.helpers.CsapApplication ;
import org.junit.jupiter.api.BeforeAll ;
import org.junit.jupiter.api.Tag ;
import org.junit.jupiter.api.Test ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.beans.factory.annotation.Autowired ;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc ;
import org.springframework.http.MediaType ;
import org.springframework.mock.web.MockHttpSession ;
import org.springframework.test.annotation.DirtiesContext ;
import org.springframework.test.web.servlet.MockMvc ;
import org.springframework.test.web.servlet.ResultActions ;

import com.fasterxml.jackson.databind.node.ObjectNode ;
import com.gargoylesoftware.htmlunit.html.HtmlInput ;

@Tag ( "full" )
@CsapBareTest.Agent_Full
@DirtiesContext
@AutoConfigureMockMvc
class Agent_ApplicationBrowser {

	Logger logger = LoggerFactory.getLogger( getClass( ) ) ;

	static {

		CsapApplication.initialize( "Test Setup Complete" ) ;

	}

	File csapApplicationDefinition = new File(
			Agent_ApplicationBrowser.class.getResource( "services-definition.json" ).getPath( ) ) ;

	@Inject
	Application csapApp ;

	@Inject
	CsapApis csapApis ;

	@Inject
	ServiceRequests serviceRequests ;

	@Inject
	ApplicationBrowser appBrowser ;

	@BeforeAll
	public void beforeAll ( )
		throws Exception {

		csapApp.setHostNameForDefinitionMapping( "localhost.local.test" ) ;

		csapApp.setAutoReload( false ) ;
		csapApp.getProjectLoader( ).setAllowLegacyNames( true ) ;

		logger.info( CsapApplication.testHeader( "Loading: {}" ), csapApplicationDefinition.getAbsolutePath( ) ) ;

		assertThat( csapApp.loadDefinitionForJunits( false, csapApplicationDefinition ) ).as( "No Errors or warnings" )
				.isFalse( ) ;
		StringBuilder parsingResults = csapApp.getLastTestParseResults( ) ;
		logger.debug( "parsing results: {}", parsingResults.toString( ) ) ;

		csapApis.osManager( ).wait_for_initial_process_status_scan( 3 ) ;
		csapApis.osManager( ).resetAllCaches( ) ;
		csapApis.osManager( ).checkForProcessStatusUpdate( ) ;

	}

	@Test
	public void applicationBrowser ( @Autowired MockMvc mockMvc )
		throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		// mock does much validation.....
		ResultActions resultActions = mockMvc.perform(

				get( "/app-browser" )

						.with( CsapBareTest.csapMockUser( ) )

						.accept( MediaType.TEXT_PLAIN ) ) ;

		//
		String result = resultActions
				.andExpect( status( ).isOk( ) )
				.andExpect( content( ).contentType( "text/html;charset=UTF-8" ) )
				.andReturn( ).getResponse( ).getContentAsString( ) ;

		logger.debug( "result:\n {}", result ) ;

		var agentLandingPage = CsapBareTest.getPage( result ) ;
		assertThat( agentLandingPage ).isNotNull( ) ;

		var environmentHeader = agentLandingPage.getElementById( "environment-name" ) ;

		assertThat( environmentHeader ).isNotNull( ) ;
		logger.info( CSAP.buildDescription(
				"environmentHeader",
				"toString", environmentHeader,
				"normalized", environmentHeader.asNormalizedText( ),
				"xml", environmentHeader.asXml( ) ) ) ;
//				environmentHeader, environmentHeader.asNormalizedText( ),  environmentHeader.asXml( ) ) ;

		assertThat( environmentHeader.asXml( ) )
				.contains( "dev" )
				.contains( "environment-select" )
				.contains( "definition-group" )
				.contains( "discovery-group" ) ;

		//
		// summary checks to ensure no thymeleaf template errors
		//
		CsapBareTest.assertAllElementsFound( agentLandingPage, List.of(
				"manager",
				"environment-name",
				"application-name",
				"bar",
				"tabs",
				"agent-tab",
				"hosts-tab-content",
				"projects-tab-content",
				"editor-templates" ) ) ;

	}

	@Test
	public void fileManager ( @Autowired MockMvc mockMvc )
		throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		// mock does much validation.....
		ResultActions resultActions = mockMvc.perform(

				get( CsapConstants.FILE_MANAGER_URL )

						.with( CsapBareTest.csapMockUser( ) )

						.param( "serviceName", CsapConstants.AGENT_NAME )

						.accept( MediaType.TEXT_PLAIN ) ) ;

		//
		String result = resultActions
				.andExpect( status( ).isOk( ) )
				.andExpect( content( ).contentType( "text/html;charset=UTF-8" ) )
				.andReturn( ).getResponse( ).getContentAsString( ) ;

		logger.debug( "result:\n {}", result ) ;

		var fileMonitorPage = CsapBareTest.getPage( result ) ;
		assertThat( fileMonitorPage ).isNotNull( ) ;

		logger.debug( "fileMonitorPage: {}", fileMonitorPage.querySelector( "body" ).asXml( ) ) ;

		//
		// summary checks to ensure no thymeleaf template errors
		//
		CsapBareTest.assertAllElementsFound( fileMonitorPage, List.of(
				"files-in-editor",
				"file-size" ) ) ;

		//
		// key element: browser ui derives disk shortcuts from html elements
		//

		var diskpaths = fileMonitorPage.querySelector( ".disk-paths" ) ;

		logger.info( CSAP.buildDescription(
				"diskpaths",
				"toString", diskpaths,
				"normalized", diskpaths.asNormalizedText( ),
				"xml", diskpaths.asXml( ) ) ) ;

		var homeInput = (HtmlInput) diskpaths.querySelector( ".homeDisk" ) ;
		logger.info( "homeInput: {}", homeInput.getDefaultValue( ) ) ;

	}

	@Test
	public void fileMonitor ( @Autowired MockMvc mockMvc )
		throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		// mock does much validation.....
		ResultActions resultActions = mockMvc.perform(

				get( CsapConstants.FILE_URL + FileRequests.FILE_MONITOR )

						.with( CsapBareTest.csapMockUser( ) )

						.param( "serviceName", CsapConstants.AGENT_NAME )

						.accept( MediaType.TEXT_PLAIN ) ) ;

		//
		String result = resultActions
				.andExpect( status( ).isOk( ) )
				.andExpect( content( ).contentType( "text/html;charset=UTF-8" ) )
				.andReturn( ).getResponse( ).getContentAsString( ) ;

		logger.debug( "result:\n {}", result ) ;

		var fileMonitorPage = CsapBareTest.getPage( result ) ;
		assertThat( fileMonitorPage ).isNotNull( ) ;

		var selectBar = fileMonitorPage.getElementById( "select-bar" ) ;
		logger.info( CSAP.buildDescription(
				"selectBar",
				"toString", selectBar,
				"normalized", selectBar.asNormalizedText( ),
				"xml", selectBar.asXml( ) ) ) ;

		logger.debug( "fileMonitorPage: {}", fileMonitorPage.querySelector( "body" ).asXml( ) ) ;

		//
		// summary checks to ensure no thymeleaf template errors
		//
		CsapBareTest.assertAllElementsFound( fileMonitorPage, List.of(
				"header",
				"select-bar",
				"logFileSelect",
				"action-bar" ) ) ;

	}

	@Test
	public void verify_service_summary_for_k8_deploy ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		ServiceInstance testService = csapApp.getServiceInstanceCurrentHost( "csap-test-k8s-service_6090" ) ;
		logger.info( "k8DeployService: {}", testService.details( ) ) ;

		var serviceSummary = appBrowser.service_summary( true, null, null, null ) ;

		logger.info( "serviceSummary: {} ", CSAP.jsonPrint( serviceSummary ) ) ;

		// filtered from cluster
		assertThat( serviceSummary.at( "/servicesTotal/k8-crashed-service" ).isMissingNode( ) )
				.isFalse( ) ;

		assertThat( serviceSummary.at( "/servicesActive/" + testService.getName( ) ).asInt( ) )
				.isEqualTo( 2 ) ;

		assertThat( serviceSummary.at( "/errorsByService/k8-crashed-service" ).asInt( ) )
				.isEqualTo( 1 ) ;

		assertThat( serviceSummary.at( "/servicesTotal/" + testService.getName( ) ).asInt( ) )
				.as( "kubernetes total services" )
				.isEqualTo( 3 ) ;

		assertThat( serviceSummary.at( "/errorsByService" ).toString( ) )
				.contains( "k8-crashed-service" ) ;

		var alertReport = serviceRequests.alertsFilter( null, "k8-crashed-service" ) ;
		logger.info( "alertReport: {}", alertReport ) ;
		assertThat( alertReport.toString( ) )
				.contains( "current", ":998,", "max", ":150,", "localhost" ) ;

//		assertThat( serviceSummary.at( "/errors" ).toString( ) )
//				.contains( "localhost: k8-crashed-service-1: Disk Space: 998Mb, Alert threshold: 150Mb" ) ;

	}

	@Test
	public void verify_service_summary_for_k8_deploy_filtered_by_cluster ( )
		throws Exception {

		// String clusterName = "dev:k8-services" ;
		String clusterName = "k8-services" ;
		logger.info( CsapApplication.testHeader( "Filtering using: {} " ), clusterName ) ;

		ServiceInstance testService = csapApp.getServiceInstanceCurrentHost( "csap-test-k8s-service_6090" ) ;
		logger.info( "testService: {}", testService.details( ) ) ;

		MockHttpSession session = new MockHttpSession( ) ;
		ObjectNode serviceSummary = appBrowser.service_summary( true, "default", clusterName, session ) ;

		logger.info( "serviceSummary: {} ", CSAP.jsonPrint( serviceSummary ) ) ;

		// filtered from cluster
		assertThat( serviceSummary.at( "/servicesTotal/k8-crashed-service" ).isMissingNode( ) )
				.isTrue( ) ;

		assertThat( serviceSummary.at( "/servicesActive/" + testService.getName( ) ).asInt( ) )
				.isEqualTo( 2 ) ;

		assertThat( serviceSummary.at( "/servicesTotal/" + testService.getName( ) ).asInt( ) )
				.as( "kubernetes total services" )
				.isEqualTo( 3 ) ;

		assertThat( serviceSummary.at( "/errorsByService" ).toString( ) )
				.contains( "k8-crashed-service" ) ;

//		assertThat( serviceSummary.at( "/errors" ).toString( ) )
//				.contains( "localhost: k8-crashed-service-1: Disk Space: 998Mb, Alert threshold: 150Mb" ) ;
		// .doesNotContain( "localhost: k8-crashed-service-1: Disk Space: 998Mb, Alert
		// threshold: 150Mb" ) ;

	}

	@Test
	public void verify_service_summary_for_multiple_k8_containers_on_master ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		ServiceInstance k8TestService = csapApp.getServiceInstanceCurrentHost( "k8s-csap-test_0" ) ;
		logger.info( "k8TestService containers: {}", k8TestService.getContainerStatusList( ) ) ;

		assertThat( k8TestService.getContainerStatusList( ).size( ) )
				.as( "both pod instances found" )
				.isEqualTo( 2 ) ;

		ObjectNode serviceSummary = appBrowser.service_summary( true, null, null, null ) ;

		logger.info( "serviceSummary: {} ", CSAP.jsonPrint( serviceSummary ) ) ;

		assertThat( serviceSummary.at( "/servicesActive/" + k8TestService.getName( ) ).asInt( ) )
				.as( "multiple pods matched" )
				.isEqualTo( 2 ) ;

		assertThat( serviceSummary.at( "/servicesTotal/" + k8TestService.getName( ) ).asInt( ) )
				.as( "multiple pods matched" )
				.isEqualTo( 8 ) ;

		assertThat( serviceSummary.at( "/servicesTotal/" + ProcessRuntime.unregistered.getId( ) ).asInt( ) )
				.as( "unregisterd service count" )
				.isGreaterThanOrEqualTo( 20 ) ;

	}

	@Test
	public void verify_namespace_monitors ( @Autowired MockMvc mockMvc )
		throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		var systemNamespaceMonitor = csapApp.findServiceByNameOnCurrentHost(
				csapApp.getProjectLoader( ).getNsMonitorName( K8.systemNamespace.val( ) ) ) ;
		logger.info( "{} resources: {}", systemNamespaceMonitor.toSummaryString( ), systemNamespaceMonitor
				.getContainerStatusList( ) ) ;

		assertThat( systemNamespaceMonitor.getContainerStatusList( ).size( ) )
				.as( "process mapped to server" )
				.isGreaterThanOrEqualTo( 1 ) ;

		assertThat( systemNamespaceMonitor.getContainerStatusList( ).get( 0 ).isActive( ) )
				.as( "container is running" )
				.isTrue( ) ;

		var systemNsReport = appBrowser.service_instances( true, systemNamespaceMonitor.getName( ), null ) ;

		logger.info( "systemNsReport: {} ", CSAP.jsonPrint( systemNsReport ) ) ;

		CSAP.setLogToDebug( HealthForAgent.class.getName( ) ) ;

		var services = csapApp
				.getActiveProject( )
				.getServicesWithKubernetesFiltering( csapApp.getCsapHostName( ) ) ;
		logger.info( "services: {}", services.map( ServiceInstance::toSummaryString ).collect( Collectors.joining(
				"\n" ) ) ) ;

		var summaryReport = appBrowser.service_summary( true, null, null, null ) ;

		logger.info( "summaryReport: {} ", CSAP.jsonPrint( summaryReport ) ) ;

		assertThat( summaryReport.path( "servicesTotal" ).path( systemNamespaceMonitor.getName( ) ).asInt( ) )
				.isGreaterThanOrEqualTo( 1 ) ;

		assertThat( summaryReport.path( "servicesActive" ).path( systemNamespaceMonitor.getName( ) ).asInt( ) )
				.isGreaterThanOrEqualTo( 1 ) ;

		var clusters = CSAP.asStreamHandleNulls( summaryReport.path( "clusters" ) ) ;
		assertThat( clusters )
				.contains( "base-os" )
				.contains( ProjectLoader.NAMESPACE_MONITORS ) ;

	}

	@Test
	public void verify_service_instances_for_multiple_k8_containers_on_master ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		ServiceInstance k8TestInstance = csapApp.findServiceByNameOnCurrentHost( "k8s-csap-test" ) ;
		logger.info( "csapTestService resources: {}", k8TestInstance.getContainerStatusList( ) ) ;

		assertThat( k8TestInstance.getContainerStatusList( ).size( ) )
				.as( "both pod instances found" )
				.isEqualTo( 2 ) ;

		var csapTestInstanceReport = appBrowser.service_instances( true, k8TestInstance.getName( ), null ) ;

		logger.info( "csapTestService status: {} ", CSAP.jsonPrint( csapTestInstanceReport ) ) ;

		assertThat( csapTestInstanceReport.path( "kubernetes" ).asBoolean( ) ).isTrue( ) ;
		assertThat( csapTestInstanceReport.path( "csapApi" ).asBoolean( ) ).isFalse( ) ;
		assertThat( csapTestInstanceReport.path( "envHosts" ).asText( ) )
				.isEqualTo( "localhost,worker-host-1,worker-host-2" ) ;

		assertThat( csapTestInstanceReport.at( "/instances" ).size( ) )
				.as( "multiple pods matched" )
				.isEqualTo( 2 ) ;

		assertThat( CSAP.jsonList( csapTestInstanceReport.at( "/instances/0/kubernetes-masters" ) ) )
				.as( "master list" )
				.containsExactly( "localhost" ) ;

		assertThat( csapTestInstanceReport.at( "/instances/0/serverType" ).asText( ) ).isEqualTo( ProcessRuntime.os
				.getId( ) ) ;

		var unregisteredServiceStatus = appBrowser.service_instances( true, ProcessRuntime.unregistered.getId( ),
				null ) ;
		logger.debug( "unregisteredServiceStatus status: {} ", CSAP.jsonPrint( unregisteredServiceStatus ) ) ;

		assertThat( unregisteredServiceStatus.path( "instances" ).size( ) ).isGreaterThanOrEqualTo( 20 ) ;
		assertThat( unregisteredServiceStatus.at( "/instances/0/serverType" ).asText( ) ).isEqualTo(
				ProcessRuntime.unregistered.getId( ) ) ;

	}

	@Test
	public void verify_service_summary_for_multiple_k8_containers_on_worker ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		ServiceInstance k8TestService = csapApp.getServiceInstanceCurrentHost( "inactive-worker-service_0" ) ;
		logger.info( "csapTestService resources: {}", k8TestService.getContainerStatusList( ) ) ;

		assertThat( k8TestService.isKubernetesMaster( ) ).isFalse( ) ;

		assertThat( k8TestService.getContainerStatusList( ).size( ) )
				.isEqualTo( 1 ) ;

		var summaryReport = appBrowser.service_summary( true, null, null, null ) ;

		logger.info( "serviceSummary: {} ", CSAP.jsonPrint( summaryReport ) ) ;

		assertThat( summaryReport.at( "/servicesTotal/" + k8TestService.getName( ) ).asInt( ) )
				.as( "Filtered total on non worker" )
				.isEqualTo( 0 ) ;

	}

	@Test
	public void verify_service_instances_for_multiple_k8_containers_on_worker ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		ServiceInstance k8TestService = csapApp.getServiceInstanceCurrentHost( "inactive-worker-service_0" ) ;
		logger.info( "csapTestService resources: {}", k8TestService.getContainerStatusList( ) ) ;

		assertThat( k8TestService.getContainerStatusList( ).size( ) )
				.as( "both pod instances found" )
				.isEqualTo( 1 ) ;

		var serviceInstances = appBrowser.service_instances( true, k8TestService.getName( ), null ) ;

		logger.info( "serviceInstances: {} ", CSAP.jsonPrint( serviceInstances ) ) ;

		assertThat( serviceInstances.at( "/instances" ).size( ) ).isEqualTo( 0 ) ;

	}

	@Test
	public void verify_service_instances_for_agent ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		var agentInstance = csapApp.getServiceInstanceCurrentHost( CsapConstants.AGENT_ID ) ;
		logger.info( "csapTestService resources: {}", agentInstance.getContainerStatusList( ) ) ;

		assertThat( agentInstance.getContainerStatusList( ).size( ) )
				.isEqualTo( 1 ) ;

		var serviceInstances = appBrowser.service_instances( true, agentInstance.getName( ), null ) ;

		logger.info( "serviceInstances: {} ", CSAP.jsonPrint( serviceInstances ) ) ;

		assertThat( serviceInstances.at( "/instances/0/" + OsProcessEnum.THREAD_COUNT ).asInt( ) )
				.as( "agent thread count" )
				.isEqualTo( 107 ) ;

	}

	@Test
	public void verify_service_instances_for_docker_service ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		ServiceInstance nginxInstance = csapApp.getServiceInstanceCurrentHost( "nginx_7080" ) ;
		logger.info( "nginxInstance containers: {}", nginxInstance.getContainerStatusList( ) ) ;

		assertThat( nginxInstance.getContainerStatusList( ).size( ) )
				.isEqualTo( 1 ) ;

		var serviceInstances = appBrowser.service_instances( true, nginxInstance.getName( ), null ) ;

		logger.info( "serviceInstances: {} ", CSAP.jsonPrint( serviceInstances ) ) ;

		assertThat( serviceInstances.at( "/instances/0/" + OsProcessEnum.THREAD_COUNT ).asInt( ) )
				.as( "agent thread count" )
				.isEqualTo( 133 ) ;

	}

	@Test
	public void verify_service_summary_for_single_k8_containers ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		var serviceSummaryReport = appBrowser.service_summary( true, null, null, null ) ;

		logger.debug( "serviceSummary: {} ", CSAP.jsonPrint( serviceSummaryReport ) ) ;

		var k8ApiService = csapApp.getServiceInstanceCurrentHost( "k8s-api-server_0" ) ;
		logger.debug( "{}", k8ApiService.details( ) ) ;

		assertThat( serviceSummaryReport.at( "/servicesActive/k8s-api-server" ).asInt( ) )
				.as( "kubernetes services that are not running are not reported in stats" )
				.isEqualTo( 1 ) ;

		assertThat( serviceSummaryReport.at( "/servicesTotal/k8s-api-server" ).asInt( ) )
				.as( "kubernetes total services" )
				.isEqualTo( 1 ) ;

		var k8NotRunningService = csapApp.getServiceInstanceCurrentHost( "k8s-not-running-service_0" ) ;
		logger.debug( "{}", k8NotRunningService.details( ) ) ;

		assertThat( serviceSummaryReport.at( "/servicesActive/k8s-not-running-service" ).isMissingNode( ) )
				.as( "kubernetes services that are not running are not reported in stats" )
				.isFalse( ) ;

		assertThat( serviceSummaryReport.at( "/servicesActive/k8s-not-running-service" ).asInt( ) )
				.as( "kubernetes services that are not running are not reported in stats" )
				.isEqualTo( 0 ) ;

		// agent mode - not running instances get set to 0
		assertThat( serviceSummaryReport.at( "/servicesTotal/k8s-not-running-service" ).asInt( ) )
				.as( "kubernetes services that are not running are not reported in stats" )
				.isEqualTo( 99 ) ;

		// handle filtering on non master - uncomment to test
		// k8NotRunningService.setKubernetesMaster( false );
		// serviceSummary = serviceRequests.service_summary( true, null, null, null );
		// logger.info( "serviceSummary: {} ", CSAP.jsonPrint( serviceSummary ) );
		// assertThat( serviceSummary.at( "/servicesTotal/k8s-not-running-service"
		// ).isMissingNode() )
		// .as( "kubernetes services that are not running are not reported in stats" )
		// .isTrue();

	}

	@Test
	public void verify_service_instances_for_k8_not_running_on_master ( ) throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		var k8NotRunningService = csapApp.getServiceInstanceCurrentHost( "k8s-not-running-service_0" ) ;
		logger.info( "k8NotRunningService: {}", k8NotRunningService.getContainerStatusList( ) ) ;

		assertThat( k8NotRunningService.getContainerStatusList( ).size( ) )
				.as( "found 1 instance defined" )
				.isEqualTo( 1 ) ;

		var serviceInstances = appBrowser.service_instances( true, k8NotRunningService.getName( ), null ) ;

		logger.info( "serviceInstances: {} ", CSAP.jsonPrint( serviceInstances ) ) ;

		assertThat( serviceInstances.at( "/instances" ).size( ) )
				.as( "multiple pods matched" )
				.isEqualTo( 1 ) ;

		// handle filtering on non master
		// k8NotRunningService.setKubernetesMaster( false );
		// serviceInstances = serviceRequests.service_instances(
		// k8NotRunningService.getServiceName(), false, "default", null, null );
		//
		// logger.info( "serviceInstances: {}, \n\n service: {} ", CSAP.jsonPrint(
		// serviceInstances ), k8NotRunningService.details() );
		//
		// assertThat( serviceInstances.at( "/serviceStatus" ).size() )
		// .isEqualTo( 0 );
	}

}
