package org.csap.agent.integration.linux ;

import static org.assertj.core.api.Assertions.assertThat ;

import java.io.File ;
import java.text.SimpleDateFormat ;
import java.util.ArrayList ;
import java.util.Arrays ;
import java.util.Date ;
import java.util.List ;
import java.util.concurrent.TimeUnit ;
import java.util.concurrent.atomic.AtomicInteger ;
import java.util.stream.Collectors ;

import javax.inject.Inject ;

import org.apache.commons.lang3.StringUtils ;
import org.csap.agent.CsapCore ;
import org.csap.agent.CsapThinTests ;
import org.csap.agent.container.ContainerIntegration ;
import org.csap.agent.container.ContainerProcess ;
import org.csap.agent.container.DockerJson ;
import org.csap.agent.model.ClusterType ;
import org.csap.agent.model.ContainerState ;
import org.csap.agent.model.ProcessRuntime ;
import org.csap.agent.model.ServiceAttributes ;
import org.csap.agent.model.ServiceInstance ;
import org.csap.agent.services.HostKeys ;
import org.csap.agent.services.OsCommands ;
import org.csap.agent.services.OsManager ;
import org.csap.agent.services.OsProcessMapper ;
import org.csap.agent.stats.OsProcessEnum ;
import org.csap.alerts.AlertFields ;
import org.csap.helpers.CSAP ;
import org.csap.helpers.CsapApplication ;
import org.junit.jupiter.api.BeforeAll ;
import org.junit.jupiter.api.DisplayName ;
import org.junit.jupiter.api.Nested ;
import org.junit.jupiter.api.Test ;
import org.springframework.boot.context.properties.ConfigurationProperties ;
import org.springframework.context.ApplicationContext ;

import com.fasterxml.jackson.databind.JsonNode ;
import com.fasterxml.jackson.databind.node.ArrayNode ;
import com.fasterxml.jackson.databind.node.ObjectNode ;

@ConfigurationProperties ( prefix = "test.junit" )
@DisplayName ( "Os Manager: Linux integration tests" )
class OsManager_Stubbed extends CsapThinTests {

	OsProcessMapper processMapper ;

	@Inject
	ApplicationContext springContext ;

	@Inject
	OsCommands osCommands ;

	File applicationDefinition = new File(
			OsManager_Agent_Context.class.getResource( "full_app.json" ).getPath( ) ) ;

	StringBuilder diskCollectionResults = new StringBuilder( ) ;

	@BeforeAll
	void beforeAll ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		processMapper = new OsProcessMapper( getApplication( ).getCsapWorkingFolder( ), getApplication( ).metrics( ) ) ;

		getApplication( ).getProjectLoader( ).setAllowLegacyNames( true ) ;

		assertThat( getApplication( ).loadDefinitionForJunits( false, applicationDefinition ) )
				.as( "No Errors or warnings" )
				.isTrue( ) ;

		diskCollectionResults.append( getApplication( ).check_for_stub( "", "linux/ps-service-disk.txt" ) ) ;
		diskCollectionResults.append( getApplication( ).check_for_stub( "", "linux/ps-docker-volumes.txt" ) ) ;

		getApplication( ).getOsManager( ).wait_for_initial_process_status_scan( 3 ) ;

		logger.info( CsapApplication.testHeader( "completed setup: stubbed results for diskCollect will be used" ) ) ;

	}

	@Test
	void verify_minimal_test_context ( ) {

		logger.info( CsapApplication.testHeader( "Number of Beans loaded: {}" ), springContext
				.getBeanDefinitionCount( ) ) ;

		logger.debug( "beans loaded: {}", Arrays.asList( springContext.getBeanDefinitionNames( ) ) ) ;

		assertThat( springContext.getBeanDefinitionCount( ) )
				.as( "Spring Bean count" )
				.isLessThan( 20 ) ;

		logger.info( "All commands: {}", osCommands ) ;

		assertThat( getApplication( ).getOsManager( ).getOsCommands( ).getSystemProcessMetrics( 10 ) )
				.contains( "top -b -d 10" ) ;

		assertThat( getApplication( ).getOsManager( ).getOsCommands( ).getSystemDiskWithUtilization( ) )
				.contains( "iostat -dx | awk '{ print $1 \" \" $NF}' | grep --invert-match 'util\\|Linux'" ) ;

		assertThat( getApplication( ).getOsManager( ).getOsCommands( ).getSystemDiskWithRateOnly( ) )
				.contains( "iostat -dm" ) ;

		logger.info( "network stats command: {}", getApplication( ).getOsManager( ).getOsCommands( )
				.getSystemNetworkStats( "eth0" ) ) ;
		assertThat( getApplication( ).getOsManager( ).getOsCommands( ).getSystemNetworkStats( "eth0" ) )
				.contains( "cat /proc/net/dev |  grep 'eth.*:' | sed 's/  */ /g'" ) ;

		assertThat( getApplication( ).getOsManager( ).getOsCommands( ).getSystemNetworkDevices( ) )
				.contains( "ip addr" ) ;

		assertThat( getApplication( ).getOsManager( ).getOsCommands( ).getSystemPackages( ) )
				.contains( "rpm -qa" ) ;

		assertThat( getApplication( ).getOsManager( ).getOsCommands( ).getSystemPackageDetails( "testpackage" ) )
				.contains( "rpm -qi testpackage" ) ;

		assertThat( getApplication( ).getOsManager( ).getOsCommands( ).getSystemServices( ) )
				.contains( "systemctl --no-pager list-unit-files --state=enabled | grep enabled | cut -f1 -d' '" ) ;

		assertThat( getApplication( ).getOsManager( ).getOsCommands( ).getSystemServiceDetails( "testservice" ) )
				.contains( "systemctl --no-pager status -l testservice" ) ;

		assertThat( getApplication( ).getOsManager( ).getOsCommands( ).getServiceDiskUsage( "/home/t1 /home/t2" ) )
				.contains(
						"for servicePath in /home/t1 /home/t2; do" ) ;

		assertThat( getApplication( ).getOsManager( ).getOsCommands( ).getServiceDiskUsageDf( ) )
				.contains(
						"timeout 2s df --portability --block-size=M --print-type | sed 's/  */ /g' |  awk '{print $4 \"/\" $3 \" \" $7 \" \" $6 \" \" $1}'" ) ;

		// infra
		assertThat( getApplication( ).getOsManager( ).getOsCommands( ).getInfraTestCpu( "55000000" ) )
				.contains( "time $(i=55000000; while (( i > 0 )); do (( i=i-1 )); done)" ) ;

		assertThat( getApplication( ).getOsManager( ).getOsCommands( ).getInfraTestDisk( "1024", "1000" ) )
				.contains( "time dd oflag=nocache,sync if=/dev/zero of=csap_test_file bs=1024 count=1000" ) ;

		// docker

		assertThat( getApplication( ).getOsManager( ).getOsCommands( ).getDockerImageExport( "myDest", "nginx" ) )
				.contains( "docker save --output myDest nginx" ) ;

		assertThat( getApplication( ).getOsManager( ).getOsCommands( ).getDockerImageLoad( "peter.tar" ) )
				.contains( "docker load --input peter.tar" ) ;

		assertThat( getApplication( ).getOsManager( ).getOsCommands( ).getDockerSocketStats( "12345" ) )
				.contains( "nsenter -t 12345 -n ss -pr" ) ;

		var diskCleanLines = getApplication( ).getOsManager( ).getOsCommands( ).getServiceJobsDiskClean(
				"/path/to/target", 2, 7, true,
				false ) ;
		logger.info( "diskCleanLines: {}", diskCleanLines.size( ) ) ;
		logger.info( CsapApplication.header( "disk clean script: \n{}" ), OsCommands.asScript( diskCleanLines ) ) ;
		assertThat( OsCommands.asScript( diskCleanLines ) )
				.contains( "find /path/to/target -maxdepth 2 -mtime +7 -type d | xargs \\rm  --recursive --force" ) ;

	}

	@Nested
	@DisplayName ( "resource collection tests" )
	class ResourceCollection {

		@Test
		public void verify_network_connections ( ) {

			logger.info( CsapApplication.testHeader( ) ) ;

			// CSAP.setLogToDebug( OsManager.class.getName() );
			ArrayNode socketConnections = getApplication( ).getOsManager( ).socketConnections( false ) ;
			// CSAP.setLogToInfo( OsManager.class.getName() );
			logger.info( CSAP.jsonPrint( socketConnections.get( 0 ) ) ) ;

			ArrayNode socketSummaryConnections = getApplication( ).getOsManager( ).socketConnections( true ) ;
			// CSAP.setLogToInfo( OsManager.class.getName() );
			logger.info( "socketSummaryConnections: {}", CSAP.jsonPrint( socketSummaryConnections ) ) ;

		}

		@Test
		public void verify_network_listeners ( ) {

			logger.info( CsapApplication.testHeader( ) ) ;

			ArrayNode socketListeners = getApplication( ).getOsManager( ).socketListeners( false ) ;
			// CSAP.setLogToInfo( OsManager.class.getName() );
			logger.info( CSAP.jsonPrint( socketListeners.get( 0 ) ) ) ;

			ArrayNode socketSummaryListeners = getApplication( ).getOsManager( ).socketListeners( true ) ;
			// CSAP.setLogToInfo( OsManager.class.getName() );
			logger.info( "socketListeners Summary: {}", CSAP.jsonPrint( socketSummaryListeners ) ) ;

			// assertThat( portSummary.get( OsManager.RECEIVE_MB ).asDouble() )
			// .as( OsManager.RECEIVE_MB )
			// .isEqualTo( 1445196.64 ) ;

		}

		@Test
		public void verify_network_devices ( ) {

			logger.info( CsapApplication.testHeader( "verify_network_devices()" ) ) ;

			// CSAP.setLogToDebug( OsManager.class.getName() );
			ObjectNode hostResourceSummary = getApplication( ).getOsManager( ).networkReceiveAndTransmit( ) ;
			// CSAP.setLogToInfo( OsManager.class.getName() );
			logger.info( CSAP.jsonPrint( hostResourceSummary ) ) ;

			assertThat( hostResourceSummary.get( OsManager.RECEIVE_MB ).asDouble( ) )
					.as( OsManager.RECEIVE_MB )
					.isEqualTo( 1445196.64 ) ;

		}

		@Test
		public void verify_network_io ( ) {

			logger.info( CsapApplication.testHeader( ) ) ;

			// CSAP.setLogToDebug( OsManager.class.getName() );
			ObjectNode hostResourceSummary = getApplication( ).getOsManager( ).networkReceiveAndTransmit( ) ;
			// CSAP.setLogToInfo( OsManager.class.getName() );
			logger.info( CSAP.jsonPrint( hostResourceSummary ) ) ;

			assertThat( hostResourceSummary.get( OsManager.RECEIVE_MB ).asDouble( ) )
					.as( OsManager.RECEIVE_MB )
					.isEqualTo( 1445196.64 ) ;

		}

		@Test
		public void verify_network_activity ( ) {

			logger.info( CsapApplication.testHeader( ) ) ;

			ObjectNode hostResourceSummary = getApplication( ).getOsManager( ).getHostResourceSummary( ) ;

			logger.info( CSAP.jsonPrint( hostResourceSummary ) ) ;

			assertThat( hostResourceSummary.at( "/networkWait" ).asInt( ) )
					.as( "/networkWait" )
					.isEqualTo( 5 ) ;

			assertThat( hostResourceSummary.at( "/networkTimeWait" ).asInt( ) )
					.as( "/networkTimeWait" )
					.isEqualTo( 300 ) ;

		}

		@Test
		public void verify_disk_reads_and_writes ( ) {

			logger.info( CsapApplication.testHeader( ) ) ;

			ObjectNode diskActivity = getApplication( ).getOsManager( ).disk_reads_and_writes( ) ;

			logger.info( CSAP.jsonPrint( diskActivity ) ) ;

			assertThat( diskActivity.at( "/totalInMB/reads" ).asInt( ) )
					.as( "/totalInMB/reads" )
					.isEqualTo( 22925 ) ;

			assertThat( getApplication( ).getOsManager( ).diskReport( ).at( "/reads" ).asInt( ) )
					.as( "/reads" )
					.isEqualTo( 22925 ) ;

		}

		@Test
		public void verify_disk_utilization_parsing ( ) {

			logger.info( CsapApplication.testHeader( ) ) ;

			ObjectNode diskUtilization = getApplication( ).getOsManager( ).disk_io_utilization( ) ;

			logger.info( CSAP.jsonPrint( diskUtilization ) ) ;

			assertThat( diskUtilization.at( "/devices/2/name" ).asText( ) )
					.as( "device name" )
					.isEqualTo( "sdc" ) ;

			assertThat( diskUtilization.at( "/devices/2/percentCapacity" ).asInt( ) )
					.as( "sdc utilization" )
					.isEqualTo( 48 ) ;

		}

		@Test
		public void verify_kubernetes_join ( ) {

			logger.info( CsapApplication.testHeader( ) ) ;

			ObjectNode kubernetesJoin = getApplication( ).getOsManager( ).getCachedKubernetesJoin( ) ;

			logger.info( CSAP.jsonPrint( kubernetesJoin ) ) ;

			assertThat( kubernetesJoin.path( "master" ).asText( ) )
					.as( "master join" )
					.contains( "--experimental-control-plane" ) ;

		}

		@Test
		public void verify_file_system_parsing ( ) {

			logger.info( CsapApplication.testHeader( ) ) ;

			ObjectNode fileSystemInfo = getApplication( ).getOsManager( ).getCachedFileSystemInfo( ) ;

			logger.debug( "fileSystemInfo: {}", CSAP.jsonPrint( fileSystemInfo ) ) ;

			assertThat( fileSystemInfo.path( "/opt" ).isObject( ) ).isTrue( ) ;

			logger.info( "opt fileSystemInfo: {}", CSAP.jsonPrint( fileSystemInfo.path( "/opt" ) ) ) ;

			assertThat( fileSystemInfo.path( "/opt" ).path( "sized" ).asText( ) )
					.as( "/opt device sized attribute" )
					.isEqualTo( "45G" ) ;

			logger.info( "fileSystemInfo: {}", CSAP.jsonPrint( fileSystemInfo ) ) ;
			assertThat(
					fileSystemInfo.path(
							"/var/lib/kubelet/plugins/kubernetes.io/vsphere-volume/mounts/[CSAP_DS1_NFS] peter" )
							.isObject( ) )
									.isTrue( ) ;

			// logger.info( "opt fileSystemInfo: {}",
			// CSAP.jsonPrint( fileSystemInfo.path(
			// "/var/lib/kubelet/plugins/kubernetes.io/vsphere-volume/mounts/[CSAP_DS1_NFS]"
			// ) ) ) ;

		}

		@Test
		public void verify_host_status_for_multiple_containers ( )
			throws Exception {

			logger.info( CsapApplication.testHeader( ) ) ;

			//
			// Ensure all pids are loaded: above tcs alter
			//
			var servicesOnHost = getApplication( ).getServicesOnHost( ) ;
			getApplication( ).getOsManager( ).buildDockerPidMapping( ) ;
			processMapper.process_find_all_service_matches(
					servicesOnHost,
					getApplication( ).check_for_stub( "", "linux/ps-service-matching.txt" ),
					diskCollectionResults.toString( ),
					getApplication( ).getOsManager( ).getDockerContainerProcesses( ) ) ;

			ServiceInstance csapTestService = getApplication( ).getServiceInstanceCurrentHost( "k8s-csap-test_0" ) ;

			JsonNode hostStatus = getApplication( ).getOsManager( ).getHostRuntime( ) ;
			logger.debug( CSAP.jsonPrint( hostStatus ) ) ;

			JsonNode k8_test_status = hostStatus.at( "/services/" + csapTestService.getServiceName_Port( ) ) ;
			logger.info( "k8_test_status: {}", CSAP.jsonPrint( k8_test_status ) ) ;

			assertThat( csapTestService.getContainerStatusList( ).size( ) )
					.as( "container count" )
					.isEqualTo( 2 ) ;

			assertThat( k8_test_status.at( "/containers/0/cpuUtil" ).asDouble( ) )
					.as( "agent cpu" )
					.isEqualTo( 0.1 ) ;

			// verify that it can be loaded back into a service.

			ServiceInstance testService = ServiceInstance.buildInstance( getJsonMapper( ), k8_test_status ) ;
			logger.info( "testService: {}", testService.details( ) ) ;

			assertThat( testService.getDefaultContainer( ).getCpuUtil( ) )
					.as( "agent cpu" )
					.isEqualTo( "0.1" ) ;

		}

		@Test
		public void verify_host_status_for_agent ( )
			throws Exception {

			logger.info( CsapApplication.testHeader( ) ) ;

			ServiceInstance agentService = getApplication( ).getServiceInstanceCurrentHost( CsapCore.AGENT_ID ) ;

			ObjectNode healthReportCollected = getJsonMapper( ).createObjectNode( ) ;
			SimpleDateFormat timeDayFormat = new SimpleDateFormat( "HH:mm:ss , MMM d" ) ;
			healthReportCollected.put( AlertFields.lastCollected.json, timeDayFormat.format( new Date( ) ) ) ;
			agentService.getDefaultContainer( ).setHealthReportCollected( healthReportCollected ) ;

			assertThat( agentService.getContainerStatusList( ).size( ) )
					.as( "container count" )
					.isEqualTo( 1 ) ;

			JsonNode hostStatus = getApplication( ).getOsManager( ).getHostRuntime( ) ;
			logger.debug( CSAP.jsonPrint( hostStatus ) ) ;

			JsonNode agentStatus = hostStatus.at( HostKeys.servicesJsonPath( agentService.getServiceName_Port( ) ) ) ;

			logger.info( "agentStatus: {}", CSAP.jsonPrint( agentStatus ) ) ;

			assertThat( agentStatus.at( "/containers/0/cpuUtil" ).asDouble( ) )
					.as( "agent cpu" )
					.isEqualTo( 3.1 ) ;

			ServiceInstance testService = ServiceInstance.buildInstance( getJsonMapper( ), agentStatus ) ;
			logger.info( "testService: {}", testService.details( ) ) ;

			assertThat( testService.getDefaultContainer( ).getCpuUtil( ) )
					.as( "agent cpu" )
					.isEqualTo( "3.1" ) ;

			assertThat( testService.getDefaultContainer( ).getHealthReportCollected( ) )
					.as( "health report" )
					.isNotNull( ) ;

		}

		@Test
		public void verify_host_status_for_host ( )
			throws Exception {

			logger.info( CsapApplication.testHeader( ) ) ;

			JsonNode runtimeInformation = getApplication( ).getOsManager( ).getHostRuntime( ) ;

			logger.info( CSAP.jsonPrint( runtimeInformation ) ) ;

			assertThat( runtimeInformation.at( "/hostStats/cpuCount" ).asText( ) )
					.as( "cpuCount" )
					.isEqualTo( "8" ) ;

			assertThat( runtimeInformation.at( "/hostStats/" + OsManager.IO_UTIL_IN_PERCENT + "/0/name" ).asText( ) )
					.as( "/hostStats/io_utilization/0/name" )
					.isEqualTo( "sda" ) ;

		}

	}

	@Nested
	@DisplayName ( "process matching tests" )
	class ProcessMatching {

		@Test
		void verify_processes_running ( ) {

			logger.info( CsapApplication.testHeader( ) ) ;

			logger.info( "processes: {}", CSAP.jsonPrint( getApplication( ).getOsManager( ).processStatus( ) ) ) ;

			assertThat( getApplication( ).getOsManager( ).isProcessRunning( ".*wewenot.*" ) )
					.isFalse( ) ;

			assertThat( getApplication( ).getOsManager( ).isProcessRunning( ".*dockerd.*" ) )
					.isTrue( ) ;

		}

		@Test
		void verify_time_taken_for_matching ( ) {

			logger.info( CsapApplication.testHeader( ) ) ;

			var servicesOnHost = getApplication( ).getServicesOnHost( ) ;
			var processTimer = getApplication( ).metrics( ).startTimer( ) ;

			for ( var i = 1; i < 10; i++ ) {

				processMapper.process_find_all_service_matches(
						servicesOnHost,
						getApplication( ).check_for_stub( "", "linux/ps-service-matching.txt" ),
						diskCollectionResults.toString( ),
						getApplication( ).getOsManager( ).getDockerContainerProcesses( ) ) ;

			}

			var psProcessNanos = getApplication( ).metrics( ).stopTimer( processTimer, "junitPs" ) ;

			logger.debug( "process summary: {}", processMapper.getLatestProcessSummary( ) ) ;

			logger.info( "PS Time Taken {}",
					CSAP.timeUnitPresent( TimeUnit.NANOSECONDS.toMillis( psProcessNanos ) ) ) ;

		}

		@Test
		public void verify_k8_multi_process_match ( )
			throws Exception {

			logger.info( CsapApplication.testHeader( ) ) ;

			var k8TestService = buildKubernetesMonitor( "k8s-csap-test", "csap-test-container" ) ;
			var modelServiceInstances = new ArrayList<ServiceInstance>( ) ;
			modelServiceInstances.add( k8TestService ) ;

			getApplication( ).getOsManager( ).buildDockerPidMapping( ) ;
			// CSAP.setLogToDebug( OsProcessMapper.class.getName() );

			var processTimer = getApplication( ).metrics( ).startTimer( ) ;
			processMapper.process_find_all_service_matches(
					modelServiceInstances,
					getApplication( ).check_for_stub( "", "linux/ps-service-matching.txt" ),
					diskCollectionResults.toString( ),
					getApplication( ).getOsManager( ).getDockerContainerProcesses( ) ) ;
			var psProcessNanos = getApplication( ).metrics( ).stopTimer( processTimer, "junitPs" ) ;
			logger.info( "PS Time Taken {}",
					CSAP.timeUnitPresent( TimeUnit.NANOSECONDS.toMillis( psProcessNanos ) ) ) ;

			String processMatchResults = processMapper.getLatestProcessSummary( ) ;

			logger.info( "{}  using pattern: {}, processMatchResults: {}",
					k8TestService.getName( ),
					ContainerIntegration.getProcessMatch( k8TestService ),
					processMatchResults ) ;

			assertThat( k8TestService.getContainerStatusList( ).size( ) )
					.as( "found multiple containers" )
					.isEqualTo( 2 ) ;

			assertThat( processMatchResults )
					.as( " csapTest pids" )
					.contains( "k8s-csap-test - pid(s): [9367, 9573]" ) ;

			// logger.info( "csapTestService.getRuntime: {}", CSAP.jsonPrint(
			// csapTestService.getRuntime() ) );
			AtomicInteger containerIndex = new AtomicInteger( 0 ) ;

			//
			String containerRuntimeInfo = //
					k8TestService.getContainerStatusList( ).stream( )
							.map( container -> {

								return container.getContainerName( ) + ": " +
										CSAP.jsonPrint( k8TestService.build_ui_service_instance( container,
												containerIndex.getAndIncrement( ) ) ) ;

							} )
							.collect( Collectors.joining( "\n" + CsapApplication.LINE + "\n" ) ) ;

			assertThat( k8TestService.getContainerStatusList( ).get( 0 ).getDiskUsageInMb( ) )
					.isEqualTo( 99 ) ;

			assertThat( k8TestService.getContainerStatusList( ).get( 1 ).getDiskUsageInMb( ) )
					.isEqualTo( 7 ) ;

			logger.info( "containerRuntimeInfo: {}", containerRuntimeInfo ) ;

		};

		@Test
		public void verify_k8_namespace_wild_card_containers ( )
			throws Exception {

			logger.info( CsapApplication.testHeader( ) ) ;

			var testService = buildKubernetesMonitor( "test-k8s-csap-reference", DockerJson.containerWildCard
					.json( ) ) ;
			var modelServiceInstances = new ArrayList<ServiceInstance>( ) ;
			modelServiceInstances.add( testService ) ;

			var locator = (ObjectNode) testService.getDockerLocator( ) ;
			locator.put( DockerJson.podNamespace.json( ), "csap-test" ) ;
			testService.resetLocatorAndMatching( ) ;
			logger.info( "locator: {}", locator ) ;

			getApplication( ).getOsManager( ).buildDockerPidMapping( ) ;

			// CSAP.setLogToDebug( OsProcessMapper.class.getName() );
			processMapper.process_find_all_service_matches(
					modelServiceInstances,
					getApplication( ).check_for_stub( "", "linux/ps-service-matching.txt" ),
					diskCollectionResults.toString( ),
					getApplication( ).getOsManager( ).getDockerContainerProcesses( ) ) ;

			var processMatchResults = processMapper.getLatestProcessSummary( ) ;
			logger.debug( "processMatchResults: {}", processMatchResults ) ;

			var matchedPids = getContainerPids( testService ) ;

			var matchedContainerLabels = getContainerLabels( testService ) ;

			logger.info( "matchedContainerNames: {}, \n matchedPids: {}", matchedContainerLabels, matchedPids ) ;

			assertThat( matchedContainerLabels )
					.hasSize( 6 )
					.contains( "test-k8s-by-spec-container",
							"test-k8s-postgres-container",
							"test-k8s-csap-reference-container",
							"test-k8s-csap-reference-container",
							"csap-test-container",
							"csap-test-k8s-service-container" ) ;

			assertThat( matchedPids )
					.hasSize( 20 )
					.contains( "31662",
							"31698",
							"17405",
							"29837",
							"29655",
							"29464",
							"29090",
							"17577",
							"17576",
							"17575",
							"17574",
							"17573",
							"17572",
							"11265",
							"11331",
							"11259",
							"11333",
							"9264",
							"9502",
							"1692" ) ;

		}

		private List<String> getContainerLabels ( ServiceInstance testService ) {

			var matchedContainerLabels = testService.getContainerStatusList( ).stream( )
					.map( ContainerState::getContainerLabel )
					.collect( Collectors.toList( ) ) ;
			return matchedContainerLabels ;

		}

		private List<String> getContainerPids ( ServiceInstance testService ) {

			var matchedPids = testService.getContainerStatusList( ).stream( )
					.map( ContainerState::getPid )
					.flatMap( List::stream )
					.collect( Collectors.toList( ) ) ;
			return matchedPids ;

		}

		@Test
		public void verify_k8_pod_container_match ( )
			throws Exception {

			logger.info( CsapApplication.testHeader( ) ) ;

			var podProcessMatchService = buildKubernetesMonitor( "test-k8s-csap-reference", null ) ;
			var modelServiceInstances = new ArrayList<ServiceInstance>( ) ;
			modelServiceInstances.add( podProcessMatchService ) ;

			var locator = (ObjectNode) podProcessMatchService.getDockerLocator( ) ;
			locator.put( DockerJson.podName.json( ), "$$service-name-.*" ) ;
			podProcessMatchService.resetLocatorAndMatching( ) ;
			logger.info( "locator: {}", locator ) ;

			getApplication( ).getOsManager( ).buildDockerPidMapping( ) ;

			// CSAP.setLogToDebug( OsProcessMapper.class.getName() );
			processMapper.process_find_all_service_matches(
					modelServiceInstances,
					getApplication( ).check_for_stub( "", "linux/ps-service-matching.txt" ),
					diskCollectionResults.toString( ),
					getApplication( ).getOsManager( ).getDockerContainerProcesses( ) ) ;

			var processMatchResults = processMapper.getLatestProcessSummary( ) ;
			logger.debug( "processMatchResults: {}", processMatchResults ) ;

			var matchedPids = getContainerPids( podProcessMatchService ) ;
			var matchedContainerLabels = getContainerLabels( podProcessMatchService ) ;
			logger.info( "matchedContainerNames: {}, \n matchedPids: {}", matchedContainerLabels, matchedPids ) ;

			assertThat( matchedContainerLabels )
					.hasSize( 2 )
					.contains( "test-k8s-csap-reference-container" ) ;
			assertThat( matchedPids )
					.hasSize( 4 )
					.contains( "11265",
							"11331",
							"11259",
							"11333" ) ;

			//
			// Namespace MISSING
			//

			locator.put( DockerJson.podNamespace.json( ), "missing-namespace" ) ;

			// reload docker pids
			podProcessMatchService.resetLocatorAndMatching( ) ;
			getApplication( ).getOsManager( ).buildDockerPidMapping( ) ;

			processMapper.process_find_all_service_matches(
					modelServiceInstances,
					getApplication( ).check_for_stub( "", "linux/ps-service-matching.txt" ),
					diskCollectionResults.toString( ),
					getApplication( ).getOsManager( ).getDockerContainerProcesses( ) ) ;

			matchedPids = getContainerPids( podProcessMatchService ) ;
			matchedContainerLabels = getContainerLabels( podProcessMatchService ) ;
			logger.info( "matchedContainerNames: {}, \n matchedPids: {}", matchedContainerLabels, matchedPids ) ;

			assertThat( matchedContainerLabels )
					.hasSize( 1 ) ;
			assertThat( matchedContainerLabels.get( 0 ) ).isNull( ) ;

			assertThat( matchedPids )
					.hasSize( 1 )
					.contains( ContainerState.NO_PIDS ) ;

			//
			// Namespace WITH matching
			//

			locator.put( DockerJson.podNamespace.json( ), "csap-test" ) ;

			// reload docker pids
			podProcessMatchService.resetLocatorAndMatching( ) ;
			getApplication( ).getOsManager( ).buildDockerPidMapping( ) ;

			processMapper.process_find_all_service_matches(
					modelServiceInstances,
					getApplication( ).check_for_stub( "", "linux/ps-service-matching.txt" ),
					diskCollectionResults.toString( ),
					getApplication( ).getOsManager( ).getDockerContainerProcesses( ) ) ;

			matchedPids = getContainerPids( podProcessMatchService ) ;
			matchedContainerLabels = getContainerLabels( podProcessMatchService ) ;
			logger.info( "matchedContainerNames: {}, \n matchedPids: {}", matchedContainerLabels, matchedPids ) ;

			assertThat( matchedContainerLabels )
					.hasSize( 2 )
					.contains( "test-k8s-csap-reference-container" ) ;
			assertThat( matchedPids )
					.hasSize( 4 )
					.contains( "11265",
							"11331",
							"11259",
							"11333" ) ;

		}

		@Test
		//
		// locators like: (kube-apiserver|etcd)
		//
		public void verify_k8_container_multi_match ( )
			throws Exception {

			logger.info( CsapApplication.testHeader( ) ) ;

			var serviceWithMultiContainers = buildKubernetesMonitor(
					"demo-multi-container-match",
					"(kube-apiserver|etcd)" ) ;

			var modelServiceInstances = new ArrayList<ServiceInstance>( ) ;
			modelServiceInstances.add( serviceWithMultiContainers ) ;

			getApplication( ).getOsManager( ).buildDockerPidMapping( ) ;

			// CSAP.setLogToDebug( OsProcessMapper.class.getName() );
			processMapper.process_find_all_service_matches(
					modelServiceInstances,
					getApplication( ).check_for_stub( "", "linux/ps-service-matching.txt" ),
					diskCollectionResults.toString( ),
					getApplication( ).getOsManager( ).getDockerContainerProcesses( ) ) ;

			var processMatchResults = processMapper.getLatestProcessSummary( ) ;
			logger.debug( "processMatchResults: {}", processMatchResults ) ;

			var matchedPids = getContainerPids( serviceWithMultiContainers ) ;
			var matchedContainerLabels = getContainerLabels( serviceWithMultiContainers ) ;
			logger.info( "matchedContainerNames: {}, \n matchedPids: {}", matchedContainerLabels, matchedPids ) ;

			assertThat( matchedContainerLabels )
					.hasSize( 2 )
					.contains( "kube-apiserver", "etcd" ) ;

			assertThat( matchedPids )
					.hasSize( 2 )
					.contains( "13729", "13624" ) ;

		}

		@Test
		public void verify_k8_container_matching ( )
			throws Exception {

			logger.info( CsapApplication.testHeader( ) ) ;

			var k8ApiService = buildKubernetesMonitor( "kube-apiserver", "kube-apiserver" ) ;
			var modelServiceInstances = new ArrayList<ServiceInstance>( ) ;
			modelServiceInstances.add( k8ApiService ) ;

			getApplication( ).getOsManager( ).buildDockerPidMapping( ) ;

			// CSAP.setLogToDebug( OsProcessMapper.class.getName() );
			processMapper.process_find_all_service_matches(
					modelServiceInstances,
					getApplication( ).check_for_stub( "", "linux/ps-service-matching.txt" ),
					diskCollectionResults.toString( ),
					getApplication( ).getOsManager( ).getDockerContainerProcesses( ) ) ;

			CSAP.setLogToInfo( OsProcessMapper.class.getName( ) ) ;

			var processMatchResults = processMapper.getLatestProcessSummary( ) ;
			logger.debug( "processMatchResults: {}", processMatchResults ) ;

			var matchedPids = getContainerPids( k8ApiService ) ;
			var matchedContainerLabels = getContainerLabels( k8ApiService ) ;
			logger.info( "matchedContainerNames: {}, \n matchedPids: {}", matchedContainerLabels, matchedPids ) ;

			assertThat( matchedContainerLabels )
					.hasSize( 1 )
					.containsExactly( "kube-apiserver" ) ;

			assertThat( matchedPids )
					.hasSize( 1 )
					.containsExactly( "13729" ) ;

			assertThat( k8ApiService.getDefaultContainer( ).getDiskUsageInMb( ) )
					.isEqualTo( 2 ) ;

			//
			// Examine latest containers - find new services
			//
			var containersFoundInDef = processMapper
					.getLatestDiscoveredContainers( ).stream( )
					.filter( ContainerProcess::isInDefinition )
					.map( ContainerProcess::getContainerName )
					.collect( Collectors.joining( " " ) ) ;

			assertThat( containersFoundInDef )
					.as( " k8sApiServer pids" )
					.contains( "kube-apiserver" ) ;

			String newServicesFound = processMapper
					.getLatestDiscoveredContainers( ).stream( )
					.filter( ContainerProcess::isNotInDefinition )
					.map( ContainerProcess::getContainerName )
					.collect( Collectors.joining( " " ) ) ;

			assertThat( newServicesFound )
					.as( " k8sApiServer pids" )
					.contains( "csaptestdocker", "nginx-container" ) ;

		}

		private ServiceInstance buildKubernetesMonitor (
															String serviceName ,
															String dockerLocator )
			throws Exception {

			var csapService = new ServiceInstance( ) ;
			csapService.setName( serviceName ) ;
			csapService.setProcessRuntime( ProcessRuntime.os.getId( ) ) ;
			csapService.setRunUsingDocker( true ) ;

			// determines matching algorithm
			csapService.setClusterType( ClusterType.KUBERNETES ) ;

			//
			// Create locator for test case
			//
			var serviceAttributes = getJsonMapper( ).createObjectNode( ) ;
			var docker = serviceAttributes.putObject( ServiceAttributes.dockerSettings.json( ) ) ;
			var locator = docker.putObject( DockerJson.locator.json( ) ) ;

			if ( StringUtils.isNotEmpty( dockerLocator ) ) {

				locator.put( DockerJson.value.json( ), dockerLocator ) ;

			}

			logger.info( "serviceAttributes: {}", CSAP.jsonPrint( serviceAttributes ) ) ;

			csapService.attributeLoad( ServiceAttributes.dockerSettings,
					serviceAttributes,
					new StringBuilder( ) ) ;

			return csapService ;

		}

		@Test
		public void verify_k8_default_locator_process_match ( )
			throws Exception {

			logger.info( CsapApplication.testHeader( ) ) ;

			var k8DeployService = buildKubernetesMonitor( "csap-test-k8s-service",
					"removing-locator-for-auto-generation-below" ) ;
			var modelServiceInstances = new ArrayList<ServiceInstance>( ) ;
			modelServiceInstances.add( k8DeployService ) ;

			k8DeployService.setProcessRuntime( ProcessRuntime.docker.getId( ) ) ;
			k8DeployService.setRunUsingDocker( false ) ;

			//
			// Generate a pod spec - which will add -container to the default name
			// triggered by removing the locator attribute
			//
			var dockerSettings = k8DeployService.getDockerSettings( ) ;
			dockerSettings.remove( DockerJson.locator.json( ) ) ;

			getApplication( ).getOsManager( ).buildDockerPidMapping( ) ;

			// CSAP.setLogToDebug( OsProcessMapper.class.getName() );
			processMapper.process_find_all_service_matches(
					modelServiceInstances,
					getApplication( ).check_for_stub( "", "linux/ps-service-matching.txt" ),
					diskCollectionResults.toString( ),
					getApplication( ).getOsManager( ).getDockerContainerProcesses( ) ) ;

			var processMatchResults = processMapper.getLatestProcessSummary( ) ;
			logger.info( "processMatchResults: {}", processMatchResults ) ;

			var matchedPids = getContainerPids( k8DeployService ) ;
			var matchedContainerLabels = getContainerLabels( k8DeployService ) ;
			logger.info( "matchedContainerNames: {}, \n matchedPids: {}", matchedContainerLabels, matchedPids ) ;

			assertThat( matchedContainerLabels )
					.hasSize( 2 )
					.containsExactly( "csap-test-k8s-service-container", "csap-test-k8s-service-container" ) ;

			assertThat( matchedPids )
					.hasSize( 3 )
					.containsExactly( "19128", "19329", "1692" ) ;

			var discoveredContainers = processMapper.getLatestDiscoveredContainers( ) ;

			logger.info( "discoveredContainers: {}", discoveredContainers ) ;

			String containersFoundInDef = discoveredContainers.stream( )
					.filter( ContainerProcess::isInDefinition )
					.map( ContainerProcess::getContainerName )
					.collect( Collectors.joining( " " ) ) ;

			assertThat( containersFoundInDef )
					.contains( k8DeployService.getName( ) ) ;

			assertThat( k8DeployService.getDefaultContainer( ).getCpuUtil( ) )
					.isEqualTo( "2.7" ) ;

			assertThat( k8DeployService.getDefaultContainer( ).getDiskUsageInMb( ) )
					.isEqualTo( 1 ) ;

		}

		@Test
		public void verify_activemq_docker_ALL_PID_process_match ( )
			throws Exception {

			ArrayList<ServiceInstance> modelServiceInstances = new ArrayList<>( ) ;

			ServiceInstance activemqService = new ServiceInstance( ) ;
			activemqService.setName( "csap-test-activemq" ) ;
			activemqService.setPort( "8161" ) ;
			activemqService.setProcessRuntime( ProcessRuntime.docker.getId( ) ) ;
			modelServiceInstances.add( activemqService ) ;

			getApplication( ).getOsManager( ).buildDockerPidMapping( ) ;
			processMapper.process_find_all_service_matches(
					modelServiceInstances,
					getApplication( ).check_for_stub( "", "linux/ps-service-matching.txt" ),
					diskCollectionResults.toString( ),
					getApplication( ).getOsManager( ).getDockerContainerProcesses( ) ) ;

			String processMatchResults = processMapper.getLatestProcessSummary( ) ;

			logger.info( "processMatchResults: {}", processMatchResults ) ;

			assertThat( processMatchResults )
					.as( OsManager.RECEIVE_MB )
					.contains( "csap-test-activemq - pid(s): [11690, 12211, 12209, 12281]" ) ;

		}

		@Test
		public void verify_activemq_docker_sub_process_match ( )
			throws Exception {

			ArrayList<ServiceInstance> modelServiceInstances = new ArrayList<>( ) ;

			ServiceInstance activemqService = new ServiceInstance( ) ;
			activemqService.setName( "csap-test-activemq" ) ;
			activemqService.setPort( "8161" ) ;
			activemqService.setProcessRuntime( ProcessRuntime.docker.getId( ) ) ;
			activemqService.setProcessFilter( "/usr/bin/java.*" ) ;

			modelServiceInstances.add( activemqService ) ;

			getApplication( ).getOsManager( ).buildDockerPidMapping( ) ;
			processMapper.process_find_all_service_matches(
					modelServiceInstances,
					getApplication( ).check_for_stub( "", "linux/ps-service-matching.txt" ),
					diskCollectionResults.toString( ),
					getApplication( ).getOsManager( ).getDockerContainerProcesses( ) ) ;

			String processMatchResults = processMapper.getLatestProcessSummary( ) ;

			logger.info( "processMatchResults: {}", processMatchResults ) ;

			assertThat( processMatchResults )
					.as( "activemq process match results" )
					.contains( "csap-test-activemq - pid(s): [12281]" ) ;

		}

		@Test
		public void verify_csaptestdocker_process_match ( )
			throws Exception {

			ArrayList<ServiceInstance> modelServiceInstances = new ArrayList<>( ) ;

			ServiceInstance csapTestDockerService = new ServiceInstance( ) ;
			csapTestDockerService.setName( "CsapTestDocker" ) ;
			csapTestDockerService.setPort( "8261" ) ;
			csapTestDockerService.setProcessRuntime( ProcessRuntime.docker.getId( ) ) ;
			modelServiceInstances.add( csapTestDockerService ) ;

			getApplication( ).getOsManager( ).buildDockerPidMapping( ) ;
			processMapper.process_find_all_service_matches(
					modelServiceInstances,
					getApplication( ).check_for_stub( "", "linux/ps-service-matching.txt" ),
					diskCollectionResults.toString( ),
					getApplication( ).getOsManager( ).getDockerContainerProcesses( ) ) ;

			String processMatchResults = processMapper.getLatestProcessSummary( ) ;

			logger.info( "processMatchResults: {}", processMatchResults ) ;

			assertThat( processMatchResults )
					.as( OsManager.RECEIVE_MB )
					.contains( "CsapTestDocker - pid(s): [6081, 6565]" ) ;

		}

		@Test
		public void verify_kubelet_process_match ( )
			throws Exception {

			logger.info( CsapApplication.testHeader( ) ) ;
			ArrayList<ServiceInstance> modelServiceInstances = new ArrayList<>( ) ;

			ServiceInstance kubeletService = new ServiceInstance( ) ;

			kubeletService.setName( "kubelet" ) ;
			kubeletService.setPort( "0" ) ;
			kubeletService.setProcessRuntime( ProcessRuntime.csap_api.getId( ) ) ;
			kubeletService.setProcessFilter( ".*kubelet --bootstrap.*" ) ;
			modelServiceInstances.add( kubeletService ) ;

			// CSAP.setLogToDebug( OsProcessMapper.class.getName() );
			processMapper.process_find_all_service_matches(
					modelServiceInstances,
					getApplication( ).check_for_stub( "", "linux/ps-service-matching.txt" ),
					diskCollectionResults.toString( ),
					getApplication( ).getOsManager( ).getDockerContainerProcesses( ) ) ;
			// CSAP.setLogToInfo( OsProcessMapper.class.getName() );

			String processMatchResults = processMapper.getLatestProcessSummary( ) ;

			logger.info( "processMatchResults: {}", processMatchResults ) ;

			assertThat( processMatchResults )
					.as( OsManager.RECEIVE_MB )
					.contains( "kubelet - pid(s): [4325]" ) ;

		}

		@Test
		public void verify_nginx_process_match ( )
			throws Exception {

			ArrayList<ServiceInstance> modelServiceInstances = new ArrayList<>( ) ;

			ServiceInstance nginxService = new ServiceInstance( ) ;
			nginxService.setName( "nginx" ) ;
			nginxService.setPort( "7080" ) ;
			nginxService.setProcessRuntime( ProcessRuntime.docker.getId( ) ) ;
			modelServiceInstances.add( nginxService ) ;

			getApplication( ).getOsManager( ).buildDockerPidMapping( ) ;

			processMapper.process_find_all_service_matches(
					modelServiceInstances,
					getApplication( ).check_for_stub( "", "linux/ps-service-matching.txt" ),
					diskCollectionResults.toString( ),
					getApplication( ).getOsManager( ).getDockerContainerProcesses( ) ) ;

			String processMatchResults = processMapper.getLatestProcessSummary( ) ;

			logger.info( "processMatchResults: {}", processMatchResults ) ;

			assertThat( processMatchResults )
					.as( OsManager.RECEIVE_MB )
					.contains( "nginx - pid(s): [19247, 17404, 17403, 17402, 17401]" ) ;

			logger.info( "nginxService: {}", nginxService.details( ) ) ;

		}

		@Test
		public void verify_cs_api_process_match ( )
			throws Exception {

			logger.info( CsapApplication.testHeader( ) ) ;
			ArrayList<ServiceInstance> modelServiceInstances = new ArrayList<>( ) ;

			ServiceInstance javaService = new ServiceInstance( ) ;

			javaService.setName( "Java" ) ;
			javaService.setPort( "0" ) ;
			javaService.setProcessRuntime( ProcessRuntime.csap_api.getId( ) ) ;
			// javaService.setProcessFilter( ".*CsAgent.*" ) ;
			modelServiceInstances.add( javaService ) ;

			// CSAP.setLogToDebug( OsProcessMapper.class.getName() );
			processMapper.process_find_all_service_matches(
					modelServiceInstances,
					getApplication( ).check_for_stub( "", "linux/ps-service-matching.txt" ),
					diskCollectionResults.toString( ),
					getApplication( ).getOsManager( ).getDockerContainerProcesses( ) ) ;
			// CSAP.setLogToInfo( OsProcessMapper.class.getName() );

			String processMatchResults = processMapper.getLatestProcessSummary( ) ;

			logger.info( "processMatchResults: {}", processMatchResults ) ;

			logger.info( "javaService state: {}", CSAP.jsonPrint( javaService.buildRuntimeState( ) ) ) ;

			assertThat( javaService.getDefaultContainer( ).getPid( ) )
					.as( "pids found" )
					.contains( "-" ) ;

			assertThat( javaService.getDefaultContainer( ).getDiskUsageInMb( ) )
					.as( "disk space" )
					.isEqualTo( 919 ) ;

		}

		@Test
		public void verify_agent_process_match ( )
			throws Exception {

			logger.info( CsapApplication.testHeader( ) ) ;
			ArrayList<ServiceInstance> modelServiceInstances = new ArrayList<>( ) ;

			ServiceInstance agentService = new ServiceInstance( ) ;

			agentService.setName( CsapCore.AGENT_NAME ) ;
			agentService.setPort( "8011" ) ;
			agentService.setProcessRuntime( ProcessRuntime.springboot.getId( ) ) ;
			agentService.setProcessFilter( ".*" + CsapCore.AGENT_NAME + ".*" ) ;
			agentService.getDefaultContainer( ).setSocketCount( 99 ) ;
			modelServiceInstances.add( agentService ) ;

			// CSAP.setLogToDebug( OsProcessMapper.class.getName() );
			processMapper.process_find_all_service_matches(
					modelServiceInstances,
					getApplication( ).check_for_stub( "", "linux/ps-service-matching.txt" ),
					diskCollectionResults.toString( ),
					getApplication( ).getOsManager( ).getDockerContainerProcesses( ) ) ;
			// CSAP.setLogToInfo( OsProcessMapper.class.getName() );

			String processMatchResults = processMapper.getLatestProcessSummary( ) ;

			logger.info( "processMatchResults: {}", processMatchResults ) ;

			assertThat( processMatchResults )
					.as( OsManager.RECEIVE_MB )
					.contains( CsapCore.AGENT_NAME + " - pid(s): [10941]" ) ;

			logger.info( "agentService.getRuntime: {}", CSAP.jsonPrint( agentService.buildRuntimeState( ) ) ) ;

			assertThat( agentService.buildRuntimeState( ).at( "/containers/0/" + OsProcessEnum.threadCount.json( ) )
					.asInt( ) )
							.as( "agent thread count" )
							.isEqualTo( 107 ) ;

			assertThat( agentService.getDefaultContainer( ).getPid( ) )
					.as( "pids found" )
					.contains( "10941" ) ;

			assertThat( agentService.getDefaultContainer( ).getSocketCount( ) )
					.as( "socket count preserved" )
					.isEqualTo( 99 ) ;

			assertThat( agentService.getDefaultContainer( ).getDiskUsageInMb( ) )
					.as( "disk space" )
					.isEqualTo( 72 ) ;

		}
	}

}
