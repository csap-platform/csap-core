package org.csap.agent.full.ui ;

import static org.assertj.core.api.Assertions.assertThat ;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get ;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content ;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status ;

import java.io.File ;
import java.util.ArrayList ;
import java.util.Arrays ;
import java.util.List ;
import java.util.stream.Collectors ;

import javax.inject.Inject ;

import org.csap.agent.CsapBareTest ;
import org.csap.agent.CsapCore ;
import org.csap.agent.api.ApplicationApi ;
import org.csap.agent.linux.HostStatusManager ;
import org.csap.agent.model.Application ;
import org.csap.agent.model.ServiceAlertsEnum ;
import org.csap.agent.model.ServiceInstance ;
import org.csap.agent.stats.OsProcessEnum ;
import org.csap.agent.ui.rest.ApplicationBrowser ;
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
import org.springframework.test.web.servlet.MockMvc ;
import org.springframework.test.web.servlet.ResultActions ;

import com.fasterxml.jackson.databind.ObjectMapper ;
import com.fasterxml.jackson.databind.node.ObjectNode ;

@Tag ( "core" )

@AutoConfigureMockMvc
@CsapBareTest.Admin_Full
class Admin_ApplicationBrowser {

	static {

		CsapApplication.initialize( "Test Setup Complete" ) ;

	}

	Logger logger = LoggerFactory.getLogger( getClass( ) ) ;

	@Inject
	ApplicationBrowser appBrowser ;

	@Inject
	Application csapApp ;

	@Inject
	ApplicationApi applicationApi ;

	@Inject
	ServiceRequests serviceRequests ;

	@BeforeAll
	void beforeAll ( )
		throws Exception {

		File csapApplicationDefinition = new File(
				Admin_ApplicationBrowser.class.getResource(
						"services-definition.json" )
						.getPath( ) ) ;

		csapApp.setHostNameForDefinitionMapping( "localhost.local.test" ) ;

		logger.info( CsapApplication.testHeader( "loading: {}" ), csapApplicationDefinition ) ;

		csapApp.getProjectLoader( ).setAllowLegacyNames( true ) ;

		HostStatusManager testHostStatusManager = new HostStatusManager( "agent/runtime-host-k8.json", csapApp ) ;
		csapApp.setHostStatusManager( testHostStatusManager ) ;

		csapApp.setJvmInManagerMode( true ) ;
		csapApp.setAutoReload( false ) ;

		logger.info( CsapApplication.testHeader( "Loading using: {} " ), csapApplicationDefinition
				.getAbsolutePath( ) ) ;

		assertThat( csapApp.loadDefinitionForJunits( false, csapApplicationDefinition ) ).as( "No Errors or warnings" )
				.isFalse( ) ;
		StringBuilder parsingResults = csapApp.getLastTestParseResults( ) ;
		logger.debug( "parsing results: {}", parsingResults.toString( ) ) ;

		csapApp.getOsManager( ).wait_for_initial_process_status_scan( 3 ) ;

		csapApp.getOsManager( ).resetAllCaches( ) ;
		csapApp.getOsManager( ).checkForProcessStatusUpdate( ) ;

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

		var adminLandingPage = CsapBareTest.getPage( result ) ;
		assertThat( adminLandingPage ).isNotNull( ) ;

		var environmentHeader = adminLandingPage.getElementById( "environment-name" ) ;

		assertThat( environmentHeader ).isNotNull( ) ;
		logger.info( CsapApplication.header( "environmentHeader: {}" ), environmentHeader ) ;

		assertThat( environmentHeader.asXml( ) )
				.contains( "dev" )
				.contains( "environment-select" )
				.contains( "definition-group" )
				.contains( "discovery-group" ) ;

		//
		// summary checks to ensure no thymeleaf template errors
		//
		var keyFields = List.of(
				"manager",
				"environment-name",
				"application-name",
				"bar",
				"tabs",
//				"agent-tab",
				"hosts-tab-content",
				"projects-tab-content",
				"editor-templates" ) ;

		for ( var field : keyFields ) {

			assertThat( adminLandingPage.getElementById( field ) )
					.as( "agent landing page: " + field )
					.isNotNull( ) ;

		}

	}

	@Test
	@Tag ( "containers" )
	public void verify_namespace_monitors ( @Autowired MockMvc mockMvc )
		throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		var systemNamespaceMonitor = csapApp.findServiceByNameOnCurrentHost(
				csapApp.getProjectLoader( ).getNsMonitorName( "kube-system" ) ) ;

		assertThat( systemNamespaceMonitor ).isNotNull( ) ;

		logger.info( "{} resources: {}", systemNamespaceMonitor.toSummaryString( ), systemNamespaceMonitor
				.getContainerStatusList( ) ) ;

		var namespaces = csapApp.getProjectLoader( ).namespaceMonitors( csapApp.getActiveProjectName( ) ) ;

		logger.info( "namespaces: {}", namespaces ) ;
		assertThat( namespaces ).contains( "default", "kube-system", "csap-test" ) ;

	}

	@Test
	void verify_kubernetes_services ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		List<String> kubernetesServices = csapApp
				.getActiveProject( ).getAllPackagesModel( )
				.getServiceConfigStreamInCurrentLC( )
				.flatMap( serviceInstancesEntry -> serviceInstancesEntry.getValue( ).stream( ) )
				.filter( ServiceInstance::is_cluster_kubernetes )
				.map( ServiceInstance::getName )
				.distinct( )
				.collect( Collectors.toList( ) ) ;

		assertThat( kubernetesServices ).hasSizeGreaterThanOrEqualTo( 9 ).contains( "csap-test-k8s-service",
				"k8-crashed-service" ) ;

		logger.info( "kubernetesServices: {}", kubernetesServices ) ;

	}

	@Test
	void verify_api_health_with_details ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		ObjectNode healthReport = applicationApi.health( 1.0, true, Application.ALL_PACKAGES ) ;

		logger.info( "localhost error count: {}, healthReport: {} ", healthReport.at( "/details/localhost" ).size( ),
				CSAP.jsonPrint( healthReport ) ) ;

		assertThat( healthReport.at( "/details" ).toString( ) )
				.doesNotContain(
						ServiceAlertsEnum.TYPE_KUBERNETES_PROCESS_CHECK )
				.contains(
						ServiceAlertsEnum.TYPE_PROCESS_CRASH_OR_KILL ) ;

	}

	@Test
	public void verify_api_health ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		ObjectNode healthReport100 = applicationApi.health( 1.0, false, Application.ALL_PACKAGES ) ;

		logger.info( "localhost error count: {}, healthReport: {} ", healthReport100.at( "/summary/localhost" ).size( ),
				CSAP.jsonPrint( healthReport100 ) ) ;

		assertThat( healthReport100.at( "/summary" ).toString( ) )
				.contains(
						"localhost: k8-crashed-service-1: no active OS process found, and no user stop was issued. Probable crash or runaway resource kill: review logs." ) ;

		ObjectNode healthReport200 = applicationApi.health( 2.0, false, Application.ALL_PACKAGES ) ;

		logger.info( "localhost 100% error count: {}, 200% error count: {} ",
				healthReport100.at( "/summary/localhost" ).size( ),
				healthReport200.at( "/summary/localhost" ).size( ) ) ;

		assertThat( healthReport100.at( "/summary/localhost" ).size( ) )
				.isGreaterThan( healthReport200.at( "/summary/localhost" ).size( ) ) ;

	}

	@Test
	void verify_health_report ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		var healthReport = csapApp.healthManager( ).build_health_report( 1.0, true, null ) ;

		logger.info( "healthReport: {} ", CSAP.jsonPrint( healthReport ) ) ;

		assertThat( healthReport.at( "/summary/localhost" ).toString( ) )
				.contains( "k8-crashed-service-1: no active OS process found" ) ;

	}

	@Test
	void verify_service_summary_errors ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		var summaryReport = appBrowser.service_summary( true, null, null, null ) ;

		logger.info( "serviceSummary: {} ", CSAP.jsonPrint( summaryReport ) ) ;

		assertThat( summaryReport.at( "/errorsByService/k8-crashed-service" ).asInt( ) )
				.isEqualTo( 6 ) ;

		var crashServiceAlerts = serviceRequests.alertsFilter( null, "k8-crashed-service" ) ;

		logger.info( "crashServiceAlerts: {} ", CSAP.jsonPrint( crashServiceAlerts ) ) ;

		assertThat( crashServiceAlerts.size( ) )
				.isEqualTo( 6 ) ;

	}

	@Test
	void verify_host_report ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		var hostReport = appBrowser.hostsReport( null, false, null ) ;

		logger.info( "hostReport: {} ", CSAP.jsonPrint( hostReport ) ) ;

		assertThat( hostReport.size( ) )
				.as( "host services active" )
				.isEqualTo( 9 ) ;

		assertThat( hostReport.at( "/0/hostStats/processCount" ).asInt( ) )
				.as( "host services active" )
				.isEqualTo( 671 ) ;

	}

	@Test
	void verify_service_summary_for_k8_monitor ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		var summaryReport = appBrowser.service_summary( true, null, null, null ) ;

		logger.info( "serviceSummary: {} ", CSAP.jsonPrint( summaryReport ) ) ;

		assertThat( summaryReport.at( "/servicesActive/k8s-api-server" ).asInt( ) )
				.as( "kubernetes services that are not running are not reported in stats" )
				.isEqualTo( 3 ) ;

		assertThat( summaryReport.at( "/servicesTotal/k8s-api-server" ).asInt( ) )
				.as( "kubernetes total services" )
				.isEqualTo( 1 ) ;

	}

	@Test
	public void verify_service_summary_for_k8_deploy ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		var testService = csapApp.getServiceInstanceCurrentHost( "csap-test-k8s-service_6090" ) ;

		logger.info( CsapApplication.testHeader( "verify_service_summary_for_k8_deploy: replicaCount: {}" ),
				testService.getKubernetesReplicaCount( ) ) ;

		logger.debug( CSAP.jsonPrint( testService.getDockerSettings( ) ) ) ;

		var summaryReport = appBrowser.service_summary( true, null, null, null ) ;

		logger.info( "summaryReport: {} ", CSAP.jsonPrint( summaryReport ) ) ;

		// in cluster
		assertThat( summaryReport.at( "/servicesTotal/k8-crashed-service" ).isMissingNode( ) )
				.isFalse( ) ;

		// is in cluster
		assertThat( summaryReport.at( "/servicesTotal/" + testService.getName( ) ).isMissingNode( ) )
				.isFalse( ) ;

		assertThat( summaryReport.at( "/servicesTotal/" + testService.getName( ) ).asInt( ) )
				.isEqualTo( 3 ) ;

		assertThat( summaryReport.at( "/servicesActive/" + testService.getName( ) ).asInt( ) )
				.isEqualTo( 9 ) ;

		assertThat( summaryReport.at( "/errorsByService/k8-crashed-service" ).asInt( ) )
				.isEqualTo( 6 ) ;

		assertThat( summaryReport.at( "/errorsByService/csap-test-k8s-service" ).asInt( ) )
				.isEqualTo( 1 ) ;

		var k8CrashServiceAlertReport = serviceRequests.alertsFilter( null, "k8-crashed-service" ) ;
		logger.info( "k8CrashServiceAlertReport: {}", CSAP.jsonPrint( k8CrashServiceAlertReport ) ) ;

		assertThat( k8CrashServiceAlertReport.toString( ) )
				.contains( "os-process-resource" )
				.contains( "disk-used-mb" )
				.contains( "150" )
				.contains( "1000" )
				.contains( "process-crash-or-kill" ) ;

		var csapTestAlertReport = serviceRequests.alertsFilter( null, "csap-test-k8s-service" ) ;
		logger.info( "csapTestAlertReport: {}", CSAP.jsonPrint( csapTestAlertReport ) ) ;

		assertThat( csapTestAlertReport.toString( ) )
				.contains( "os-process-resource" )
				.contains( "files-open" )
				.contains( "5051" )
				.contains( "500" ) ;

//		assertThat( summaryReport.at( "/errors" ).toString( ) )
//				.contains( //
//						"worker-host-2: csap-test-k8s-service-1: OS File Handles: 5051 open, Alert threshold: 500",
//						"localhost: k8-crashed-service-1: no active OS process found, and no user stop was issued. Probable crash or runaway resource kill: review logs." ) ;

	}

	@Test
	public void verify_service_summary_for_k8_deploy_by_cluster ( )
		throws Exception {

		String clusterName = "k8-services" ;
		logger.info( CsapApplication.testHeader( "Filtering using: {} " ), clusterName ) ;

		ServiceInstance testService = csapApp.getServiceInstanceCurrentHost( "csap-test-k8s-service_6090" ) ;

		logger.info( "'{}' replicaCount: {} cluster: {}",
				testService.getName( ),
				testService.getKubernetesReplicaCount( ),
				testService.getLifecycle( ) ) ;

		logger.debug( CSAP.jsonPrint( testService.getDockerSettings( ) ) ) ;

		MockHttpSession session = new MockHttpSession( ) ;
		var summaryReport = appBrowser.service_summary( true, null, clusterName, session ) ;

		logger.info( "serviceSummary: {} ", CSAP.jsonPrint( summaryReport ) ) ;

		// filtered from cluster
		assertThat( summaryReport.at( "/servicesTotal/k8-crashed-service" ).isMissingNode( ) )
				.isTrue( ) ;

		// is in cluster
		assertThat( summaryReport.at( "/servicesTotal/" + testService.getName( ) ).isMissingNode( ) )
				.isFalse( ) ;

		assertThat( summaryReport.at( "/servicesTotal/" + testService.getName( ) ).asInt( ) )
				.isEqualTo( 3 ) ;

		assertThat( summaryReport.at( "/servicesActive/" + testService.getName( ) ).asInt( ) )
				.isEqualTo( 9 ) ;

		assertThat( summaryReport.at( "/errorsByService/k8-crashed-service" ).asInt( ) )
				.isEqualTo( 6 ) ;

		assertThat( summaryReport.at( "/errorsByService/csap-test-k8s-service" ).asInt( ) )
				.isEqualTo( 1 ) ;

//		assertThat( summaryReport.at( "/errors" ).toString( ) )
//				.contains( //
//						"worker-host-2: csap-test-k8s-service-1: OS File Handles: 5051 open, Alert threshold: 500",
//						"localhost: k8-crashed-service-1: no active OS process found, and no user stop was issued. Probable crash or runaway resource kill: review logs." ) ;

	}

	@Test
	public void verify_service_instances_for_agent ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		String servicesOnHost = csapApp.getServicesOnHost( ).stream( )
				.map( ServiceInstance::toSummaryString )
				.collect( Collectors.joining( "\n" ) ) ;

		logger.info( "Host: {}, servicesOnHost: {}", csapApp.getCsapHostName( ), servicesOnHost ) ;

		ServiceInstance agentInstance = csapApp.getServiceInstanceCurrentHost( CsapCore.AGENT_ID ) ;

		logger.info( "agentInstance status: {}", agentInstance.getContainerStatusList( ) ) ;

		assertThat( agentInstance.getContainerStatusList( ).size( ) )
				.isEqualTo( 1 ) ;

		// List<ServiceInstance> agentInstances = csapApp.serviceInstancesByName(
		// "default", agentInstance.getServiceName() ) ;
		ArrayList<ServiceInstance> agentInstances = csapApp.serviceNameToAllInstances( ).get( agentInstance
				.getName( ) ) ;
		assertThat( agentInstances.size( ) )
				.as( "agent instance count" )
				.isEqualTo( 9 ) ;

		var instanceReport = appBrowser.service_instances( true, agentInstance.getName( ), null ) ;

		logger.info( "instanceReport: {} ", CSAP.jsonPrint( instanceReport ) ) ;

		assertThat( instanceReport.path( "instances" ).size( ) )
				.as( "agent instance status count" )
				.isEqualTo( 9 ) ;

		assertThat( instanceReport.at( "/instances/0/" + OsProcessEnum.THREAD_COUNT ).asInt( ) )
				.as( "agent thread count" )
				.isEqualTo( 107 ) ;

		assertThat( instanceReport.at( "/instances/0/serviceName" ).asText( ) )
				.isEqualTo( CsapCore.AGENT_NAME ) ;

		assertThat( instanceReport.at( "/instances/0/deployedArtifacts" ).asText( ) )
				.isEqualTo( "testing.1.2.3" ) ;

	}

	@Test
	public void verify_service_instances_for_multiple_k8_containers ( )
		throws Exception {

		ServiceInstance csapTestService = csapApp.getServiceInstanceCurrentHost( "k8s-csap-test_0" ) ;

		logger.info( "csapTestService resources: {}", csapTestService.getContainerStatusList( ) ) ;
		assertThat( csapTestService.getContainerStatusList( ).size( ) )
				.isEqualTo( 2 ) ;

		ArrayList<ServiceInstance> allTestInstances = csapApp.serviceNameToAllInstances( ).get( csapTestService
				.getName( ) ) ;

		String testServicesDesc = allTestInstances.stream( )
				.map( ServiceInstance::toString )
				.collect( Collectors.joining( "\n\n\t" ) ) ;

		logger.info( "allTestInstances: {}", testServicesDesc ) ;
		assertThat( allTestInstances.size( ) )
				.isEqualTo( 3 ) ;

		var instanceReport = appBrowser.service_instances( true, csapTestService.getName( ), null ) ;

		logger.info( "serviceInstances: {} ", CSAP.jsonPrint( instanceReport ) ) ;

		assertThat( instanceReport.at( "/instances" ).size( ) )
				.as( "multiple pods matched" )
				.isEqualTo( 6 ) ;

		assertThat( instanceReport.at( "/instances/1/containerIndex" ).asInt( ) )
				.isEqualTo( 1 ) ;

		assertThat( instanceReport.at( "/instances/1/containerName" ).asText( ) )
				.contains( "csap-test-container" ) ;

	}

	@Test
	public void verify_service_instances_when_no_kubernetes_running ( )
		throws Exception {

		ServiceInstance inactiveService = csapApp.getServiceInstanceAnyPackage( "inactive-k8s-service_0" ) ;

		logger.info( "csapTestService instance: {}", inactiveService ) ;

		// initial status
		assertThat( inactiveService )
				.isNotNull( ) ;

		ArrayList<ServiceInstance> allTestInstances = csapApp.serviceNameToAllInstances( ).get( inactiveService
				.getName( ) ) ;

		String testServicesDesc = allTestInstances.stream( )
				.map( ServiceInstance::toString )
				.collect( Collectors.joining( "\n\n\t" ) ) ;

		logger.info( "all instances: {}", testServicesDesc ) ;
		assertThat( allTestInstances.size( ) )
				.isEqualTo( 3 ) ;

		HostStatusManager.testHostNameFilters = Arrays.asList( ) ;

//		ObjectNode status_with_all_stopped = serviceRequests.service_instances(
//				inactiveService.getName( ), false, "default", null, null ) ;

		var status_with_all_stopped = appBrowser.service_instances( true, inactiveService.getName( ), null ) ;

		logger.info( "status_with_all_stopped: {} ", CSAP.jsonPrint( status_with_all_stopped ) ) ;

		// when all inactive: only master is displayed - for deployment selection
		assertThat( status_with_all_stopped.at( "/instances" ).size( ) )
				.isEqualTo( 1 ) ;

		assertThat( status_with_all_stopped.at( "/instances/0/" + "kubernetes-master" ).asBoolean( ) )
				.isTrue( ) ;

		assertThat( status_with_all_stopped.at( "/instances/0/containerIndex" ).asInt( ) )
				.isEqualTo( 0 ) ;

		assertThat( status_with_all_stopped.at( "/instances/0/containerName" ).asText( ) )
				.contains( "default" ) ;

	}

	@Inject
	ObjectMapper jsonMapper ;

	@Test
	public void verify_service_summary_when_k8_worker_running ( )
		throws Exception {

		ServiceInstance inactiveService = csapApp.getServiceInstanceAnyPackage( "inactive-k8s-service_0" ) ;

		logger.info( CsapApplication.header( "verify_service_summary_when_k8_worker_running: " + inactiveService ) ) ;

		HostStatusManager.testHostNameFilters = Arrays.asList( "filter-for-master2-host",
				"filter-for-worker2-host-1" ) ;

		var summaryReport = appBrowser.service_summary( true, null, null, null ) ;

		logger.info( "status_with_worker_started: {} ", CSAP.jsonPrint( summaryReport ) ) ;

		assertThat( summaryReport.path( "errorsByService" ).path( inactiveService.getName( ) ).asInt( ) )
				.isEqualTo( 2 ) ;

		var alertReport = serviceRequests.alertsFilter( null, inactiveService.getName( ) ) ;
		logger.info( "alertReport: {}", CSAP.jsonPrint( alertReport ) ) ;
		assertThat( alertReport.toString( ) )
				.doesNotContain( ServiceAlertsEnum.KUBERNETES_PROCESS_CHECKS ) ;

//		List<String> errors_all_hosts = jsonMapper.readValue( summaryReport.get( "errors" ).toString( ),
//				new TypeReference<List<String>>( ) {
//				} ) ;
//
//		String desc = errors_all_hosts.stream( ).collect( Collectors.joining( "\n\t" ) ) ;
//		logger.info( "summary errors: {} ", desc ) ;
//
//		assertThat( errors_all_hosts )
//				.doesNotContain( "master2-host: inactive-k8s-service" + ServiceAlertsEnum.KUBERNETES_PROCESS_CHECKS ) ;

	}

	@Test
	public void verify_service_instances_when_k8_worker_running ( )
		throws Exception {

		var inactiveService = csapApp.getServiceInstanceAnyPackage( "inactive-k8s-service_0" ) ;

		logger.info( "csapTestService instance: {}", inactiveService ) ;

		HostStatusManager.testHostNameFilters = Arrays.asList( "filter-for-master2-host", "worker2-host-1" ) ;

		var status_with_worker_started = appBrowser.service_instances( true, inactiveService.getName( ), null ) ;

		logger.info( "status_with_worker_started: {} ", CSAP.jsonPrint( status_with_worker_started ) ) ;

		assertThat( status_with_worker_started.at( "/instances" ).size( ) )
				.isEqualTo( 1 ) ;

	}

}
