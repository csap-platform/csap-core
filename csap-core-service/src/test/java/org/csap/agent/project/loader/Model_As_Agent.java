/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.csap.agent.project.loader ;

import static java.util.Comparator.comparing ;
import static org.assertj.core.api.Assertions.assertThat ;

import java.io.File ;
import java.net.URI ;
import java.util.ArrayList ;
import java.util.LinkedHashMap ;
import java.util.List ;
import java.util.TreeMap ;
import java.util.stream.Collectors ;

import org.apache.commons.lang3.StringUtils ;
import org.apache.commons.lang3.text.WordUtils ;
import org.csap.agent.CsapBareTest ;
import org.csap.agent.CsapCore ;
import org.csap.agent.model.Application ;
import org.csap.agent.model.ClusterType ;
import org.csap.agent.model.ContainerState ;
import org.csap.agent.model.EnvironmentSettings ;
import org.csap.agent.model.Project ;
import org.csap.agent.model.ProjectLoader ;
import org.csap.agent.model.ServiceAlertsEnum ;
import org.csap.agent.model.ServiceAttributes ;
import org.csap.agent.model.ServiceInstance ;
import org.csap.agent.services.ServiceOsManager ;
import org.csap.agent.ui.editor.ServiceResources ;
import org.csap.helpers.CSAP ;
import org.csap.helpers.CsapApplication ;
import org.csap.integations.CsapMicroMeter ;
import org.junit.jupiter.api.BeforeAll ;
import org.junit.jupiter.api.DisplayName ;
import org.junit.jupiter.api.Nested ;
import org.junit.jupiter.api.Test ;
import org.junit.jupiter.api.TestInstance ;

import com.fasterxml.jackson.databind.JsonNode ;
import com.fasterxml.jackson.databind.ObjectMapper ;
import com.fasterxml.jackson.databind.node.ArrayNode ;
import com.fasterxml.jackson.databind.node.ObjectNode ;

class Model_As_Agent extends CsapBareTest {

	File testDefinition = new File( getClass( ).getResource( "application-agent.json" ).getPath( ) ) ;;

	@BeforeAll
	void beforeAll ( )
		throws Exception {

		getApplication( ).getProjectLoader( ).setAllowLegacyNames( true ) ;
		// CSAP.setLogToDebug( DefinitionParser.class.getName() );
		assertThat( getApplication( ).loadDefinitionForJunits( false, testDefinition ) )
				.as( "No Errors or warnings" )
				.isTrue( ) ;

		CSAP.setLogToInfo( ProjectLoader.class.getName( ) ) ;

		logger.info( CsapApplication.header( "Using: " + testDefinition.getAbsolutePath( ) ) ) ;

	}

	@Nested
	@DisplayName ( "service instance operations" )
	class ServiceInstanceOps {

		ObjectMapper jsonMapper = new ObjectMapper( ) ;

		@Test
		public void verify_instance_merging ( )
			throws Exception {

			logger.info( CsapApplication.testHeader( ) ) ;

			ServiceInstance agentInstance = getApplication( ).getServiceInstanceCurrentHost( CsapCore.AGENT_ID ) ;

			ObjectNode healthReport = (ObjectNode) jsonMapper
					.readTree( getApplication( ).check_for_stub( "", "httpCollect/csap-reference-health.json" ) ) ;

			agentInstance.getDefaultContainer( ).setHealthReportCollected(
					(ObjectNode) healthReport.path( CsapMicroMeter.HealthReport.Report.name.json ) ) ;

			ServiceInstance agentMergeInstance = ServiceInstance.buildInstance( jsonMapper, agentInstance
					.buildRuntimeState( ) ) ;

			agentMergeInstance.getDefaultContainer( ).setHealthReportCollected( null ) ;

			List<ContainerState> updatedContainers = agentMergeInstance.getContainerStatusList( ) ;
			agentInstance.mergeContainerData( updatedContainers ) ;

			assertThat( agentInstance.getContainerStatusList( ).toString( ).length( ) )
					.isEqualTo( agentMergeInstance.getContainerStatusList( ).toString( ).length( ) ) ;

			assertThat( agentMergeInstance.getDefaultContainer( ).getHealthReportCollected( )
					.path( CsapMicroMeter.HealthReport.Report.healthy.json ).asBoolean( ) )
							.isFalse( ) ;

		}

		@Test
		public void verify_instance_building ( )
			throws Exception {

			logger.info( CsapApplication.testHeader( ) ) ;

			ServiceInstance agentInstance = getApplication( ).getServiceInstanceCurrentHost( CsapCore.AGENT_ID ) ;

			ObjectNode healthReport = (ObjectNode) jsonMapper
					.readTree( getApplication( ).check_for_stub( "", "httpCollect/csap-reference-health.json" ) ) ;

			agentInstance.getDefaultContainer( ).setHealthReportCollected(
					(ObjectNode) healthReport.path( CsapMicroMeter.HealthReport.Report.name.json ) ) ;

			agentInstance.getDefaultContainer( ).setCpuUtil( "9.9" ) ;
			agentInstance.getDefaultContainer( ).setDeployedArtifacts( "peter.1.2.3" ) ;
			ArrayNode kubernetesMasterHostNames = jsonMapper.createArrayNode( ) ;

			var JUNIT_MASTER = "junit-master" ;
			kubernetesMasterHostNames.add( JUNIT_MASTER ) ;
			agentInstance.setKubernetesMasterHostNames( kubernetesMasterHostNames ) ;

			ObjectNode runtime = agentInstance.buildRuntimeState( ) ;
			( (ObjectNode) runtime.at( "/containers/0" ) ).put( "resourceViolations", "" ) ;

			logger.info( "agent runtime: {}", CSAP.jsonPrint( runtime ) ) ;

			ServiceInstance agentCloneInstance = ServiceInstance.buildInstance( jsonMapper, runtime ) ;

			logger.info( "agentCloneInstance: {}", agentCloneInstance.details( ) ) ;

			assertThat( agentCloneInstance.getRuntime( ) )
					.isEqualTo( "SpringBoot" ) ;

			assertThat( agentCloneInstance.getDefaultContainer( ).getDeployedArtifacts( ) )
					.isEqualTo( "peter.1.2.3" ) ;

			assertThat( agentCloneInstance.getDefaultContainer( ).getCpuUtil( ) )
					.isEqualTo( "9.9" ) ;

			assertThat( agentCloneInstance.getHostName( ) )
					.isEqualTo( "localhost" ) ;

			assertThat( agentCloneInstance.getDefaultContainer( ).getHealthReportCollected( )
					.path( CsapMicroMeter.HealthReport.Report.healthy.json ).asBoolean( ) )
							.isFalse( ) ;

			assertThat( CSAP.jsonList( agentCloneInstance.getKubernetesMasterHostNames( ) ) )
					.containsExactly( JUNIT_MASTER ) ;

		}

	}

	@Nested
	@DisplayName ( "general" )
	class General {

		@Test
		public void verify_largest_time ( ) {

			logger.info( CsapApplication.testHeader( ) ) ;

			int maxTime = getApplication( ).getMaxDeploySecondsForService( CsapCore.AGENT_NAME ) ;

			logger.info( "maxTime deploy for agent: {}", maxTime ) ;
			assertThat( maxTime ).isEqualTo( 300 ) ;

			int maxTimeForMissing = getApplication( ).getMaxDeploySecondsForService( "missing-service" ) ;

			logger.info( "maxTime deploy for maxTimeForMissing: {}", maxTimeForMissing ) ;
			assertThat( maxTimeForMissing ).isEqualTo( 30 ) ;

		}

		@Test
		public void verify_all_instances ( ) {

			logger.info( CsapApplication.testHeader( ) ) ;

			ArrayNode all_services = getApplication( ).getActiveProject( ).getServiceDefinitions( ) ;

			logger.info( "all_services: {}", CSAP.jsonPrint( all_services ) ) ;

			assertThat( all_services.toString( ) )
					.contains( CsapCore.AGENT_NAME ) ;

		}

		@Test
		public void verify_all_life_hosts ( ) {

			logger.info( CsapApplication.testHeader( ) ) ;

			var hosts = getApplication( ).getRootProject( ).getHostToServicesMap( ).keySet( ) ;

			logger.info( "all lifecycle hosts: {}", hosts ) ;

			assertThat( hosts )
					.contains( "localhost", "host-test01", "host-test02" ) ;

		}

		@Test
		public void verify_script ( ) {

			logger.info( CsapApplication.testHeader( ) ) ;

			ServiceInstance wmwareService = getApplication( ).serviceNameToAllInstances( ).get( "vmtoolsd" ).get( 0 ) ;

			assertThat( wmwareService.is_os_process_monitor( ) )
					.as( "is script" )
					.isTrue( ) ;

		}

		@Test
		public void verify_cluster_parsing ( ) {

			logger.info( CsapApplication.testHeader( ) ) ;

			logger.info( "cluster map: {}\n\n lifecluster: {}",
					getApplication( ).getRootProject( ).getClustersToServicesMapInCurrentLifecycle( ),
					getApplication( ).getRootProject( ).getLifeClusterToOsServices( ) ) ;

			assertThat( getApplication( ).getRootProject( ).getClustersToServicesMapInCurrentLifecycle( ).get(
					"simple-cluster" ) )
							.contains( CsapCore.AGENT_NAME, "simple-cluster-service" ) ;

		}

		@Test
		public void verify_lifecycle_settings ( ) {

			logger.info( CsapApplication.testHeader( ) ) ;

			assertThat( getApplication( ).rootProjectEnvSettings( ).getAdminToAgentTimeoutSeconds( ) )
					.as( "agent time out" )
					.isEqualTo( 6 ) ;

			assertThat( getApplication( ).rootProjectEnvSettings( ).isEventPublishEnabled( ) )
					.as( "isEventPublishEnabled" )
					.isFalse( ) ;

			logger.info( "infra settings: {}", getApplication( ).rootProjectEnvSettings( ).getInfraTests( ) ) ;
			assertThat( getApplication( ).rootProjectEnvSettings( ).getInfraTests( ).getCpuLoopsMillions( ) )
					.as( "cpu loops" )
					.isEqualTo( 1 ) ;

		}

		@Test
		public void verify_git_ssl_verification_disabled ( ) {

			logger.info( CsapApplication.testHeader( ) ) ;

			logger.info( "git disabled ssl urls: {}", getApplication( ).rootProjectEnvSettings( )
					.getGitSslVerificationDisabledUrls( ) ) ;
			assertThat( getApplication( ).rootProjectEnvSettings( ).getGitSslVerificationDisabledUrls( ) )
					.as( "git urls" )
					.contains( "https://moc-bb.***REMOVED***" ) ;

			// assertThat (getApplication().checkGitSslVerificationSettings() )
			// .contains( "\n[http \"https://sample.com/demo.git\"]\n\tsslVerify =
			// false" );
			// assertThat( getApplication().checkGitSslVerificationSettings() )
			// .contains( "\n[http]\n\tsslVerify = false" );

		}

		@Test
		public void verify_httpd_attributes ( ) {

			logger.info( CsapApplication.testHeader( ) ) ;

			ServiceInstance httpdInstance = getApplication( ).serviceNameToAllInstances( ).get( "httpd" ).get( 0 ) ;

			assertThat( httpdInstance.is_csap_api_server( ) )
					.as( "is wrapper" )
					.isTrue( ) ;

			assertThat( httpdInstance.getProcessFilter( ) )
					.as( "process" )
					.isEqualTo( "httpdFilter" ) ;

			logger.info( httpdInstance.getUrl( ) ) ;
			assertThat( httpdInstance.getUrl( ) )
					.as( "url" )
					.isEqualTo(
							"http://localhost.***REMOVED***:8080/server-status,http://localhost:8080/status,http://localhost:8080" ) ;

			assertThat( httpdInstance.getMonitors( ).get( ServiceAlertsEnum.diskSpace.maxId( ) ).asText( ) )
					.as( "disk limit" )
					.isEqualTo( "1000" ) ;

			assertThat( httpdInstance.getPerformanceConfiguration( ).get( "config" ).get( "httpCollectionUrl" )
					.asText( ) )
							.as( "colleciton url" )
							.isEqualTo( "http://localhost:8080/server-status?auto" ) ;

			assertThat( httpdInstance.getLifeEnvironmentVariables( ).get( "test" ).asText( ) )
					.as( "environmentVariabls" )
					.isEqualTo( "someDevDefault" ) ;

			assertThat( httpdInstance.getJobs( ).size( ) )
					.as( "number of jobs" )
					.isEqualTo( 3 ) ;

			assertThat( httpdInstance.getJobs( ).get( 0 ).getScript( ) )
					.as( "script path" )
					.endsWith( "/httpd_8080/jobs/eventsWarmup.sh" ) ;

			assertThat( httpdInstance.getJobs( ).get( 1 ).getHour( ) )
					.as( "invokation hour" )
					.isEqualTo( "01" ) ;

			assertThat( httpdInstance.getDefaultLogToShow( ) )
					.as( "log to show" )
					.isEqualTo( "access.log" ) ;

			assertThat( httpdInstance.getPropDirectory( ) )
					.as( "propery folder" )
					.isEqualTo( "/opt/csapUser/staging/httpdConf" ) ;
			logger.info( "httpd service meters:\n{}", httpdInstance.getServiceMeters( ) ) ;
			assertThat( httpdInstance.getServiceMeters( ).toString( ) )
					.as( "service meters" )
					.contains( "collectionId: BusyWorkers, type: attribute http" ) ;

		}

		@Test
		public void verify_vmware_attributes ( ) {

			logger.info( CsapApplication.testHeader( ) ) ;

			ServiceInstance wmwareService = getApplication( ).serviceNameToAllInstances( ).get( "vmtoolsd" ).get( 0 ) ;

			assertThat( wmwareService.is_os_process_monitor( ) )
					.as( "is script" )
					.isTrue( ) ;

		}

		@Test
		public void verify_jdk_attributes ( ) {

			logger.info( CsapApplication.testHeader( ) ) ;

			ServiceInstance jdkService = getApplication( ).serviceNameToAllInstances( ).get( "jdk" ).get( 0 ) ;

			assertThat( jdkService.getProcessFilter( ) )
					.as( "processFilter" )
					.isNull( ) ;

			assertThat( jdkService.is_files_only_package( ) )
					.as( "is script" )
					.isTrue( ) ;

			assertThat( jdkService.getScmLocation( ) )
					.as( "source location" )
					.contains( "JavaDevKitPackage8" ) ;

			assertThat( jdkService.getDiskUsageMatcher( null ).length )
					.as( "disk matching patterns" )
					.isEqualTo( 2 ) ;

		}

		@Test
		public void verify_cassandra_attributes ( ) {

			logger.info( CsapApplication.testHeader( ) ) ;

			ServiceInstance cassandraService = getApplication( ).serviceNameToAllInstances( ).get( "cassandra" ).get(
					0 ) ;

			assertThat( cassandraService.is_csap_api_server( ) )
					.as( "is wrapper" )
					.isTrue( ) ;

			assertThat( cassandraService.getScmLocation( ) )
					.as( "source location" )
					.isEqualTo( "https://bitbucket.yourcompany.com/bitbucket/scm/smas/csap-cassandra.git" ) ;

			assertThat( cassandraService.getScm( ) )
					.as( "scm type" )
					.isEqualTo( "git" ) ;

			assertThat( cassandraService.getJmxPort( ) )
					.as( "jmxPort" )
					.isEqualTo( "7199" ) ;

		}

		@Test
		public void verify_start_order ( ) {

			String order = getApplication( ).getServicesOnHost( ).stream( )
					.sorted( comparing( ServiceInstance::startOrder ) )
					.map( service -> {

						return CSAP.pad( service.getName( ) ) + " Order: " + service.startOrder( ) ;

					} )
					.collect( Collectors.joining( "\n\t" ) ) ;

			logger.info( "Start order: {}", order ) ;

		}

		@Test
		public void verify_reference_attributes ( ) {

			logger.info( CsapApplication.testHeader( ) ) ;

			ServiceInstance refService = getApplication( ).serviceNameToAllInstances( ).get( "Cssp3ReferenceMq" ).get(
					0 ) ;

			assertThat( refService.is_springboot_server( ) )
					.as( "server override" )
					.isTrue( ) ;

			assertThat( refService.isTomcatPackaging( ) )
					.as( "server override" )
					.isFalse( ) ;

			assertThat( refService.isRunUsingDocker( ) )
					.as( "useDockerJavaContainer" )
					.isTrue( ) ;

			assertThat( refService.getDockerImageName( ) )
					.as( "docker image name" )
					.isEqualTo( "containers.yourcompany.com/cssp-legacy:latest" ) ;

			assertThat( refService.getJobs( ).size( ) )
					.as( "number of jobs" )
					.isEqualTo( 1 ) ;

			assertThat( refService.getLogsToRotate( ).get( 0 ).getPath( ).contains( "catalina.out" ) )
					.as( "log file to rotate" )
					.isTrue( ) ;

			assertThat( refService.getProcessFilter( ) )
					.as( "process filter" )
					.isEqualTo( ".*java.*csapProcessId=Cssp3ReferenceMq.*0.*" ) ;

			assertThat( refService.getJmxPort( ) )
					.as( "jmx port" )
					.isEqualTo( "8246" ) ;

			assertThat( refService.getAttribute( ServiceAttributes.parameters ) )
					.as( "params" )
					.isEqualTo( "-Xms16M -Xmx256M paramsOveride -Dsimple.life=$$service-environment" ) ;

			assertThat( refService.resolveRuntimeVariables( refService.getParameters( ) ) )
					.as( "params" )
					.isEqualTo( "-Xms16M -Xmx256M paramsOveride -Dsimple.life=dev" ) ;

			assertThat( refService.getAttributeAsObject( ServiceAttributes.webServerTomcat ) )
					.as( "webServerTomcat" )
					.isNotNull( ) ;

			assertThat( refService.getAttributeAsObject( ServiceAttributes.webServerTomcat ).get( "loadBalance" )
					.toString( ) )
							.as( "webServerTomcat load balance" )
							.contains( "method=Next", "sticky_session=1" ) ;

			assertThat( refService.getAttributeAsJson( ServiceAttributes.webServerReWrite ) )
					.as( "webServerReWrite" )
					.isNotNull( ) ;

			assertThat( refService.getAttributeAsJson( ServiceAttributes.webServerReWrite ).toString( ) )
					.as( "webServerReWrite" )
					.contains( "RewriteRule ^/test1/(.*)$  /Cssp3ReferenceMq/$1 [PT]" ) ;

			assertThat( refService.getDefaultBranch( ) )
					.as( "scm version" )
					.isEqualTo( "branchOver" ) ;

			assertThat( refService.getMetaData( ) )
					.as( "metadata" )
					.isEqualTo( "exportWeb, -nio" ) ;

			assertThat( refService.getRawAutoStart( ) )
					.as( "autostart" )
					.isEqualTo( 989 ) ;

			assertThat( refService.getContext( ) )
					.as( "servlet context" )
					.isEqualTo( "Cssp3ReferenceMq" ) ;

			assertThat( refService.getDescription( ) )
					.as( "description" )
					.isEqualTo(
							"Provides tomcat8.x reference implementation for engineering, along with core platform regression tests." ) ;

			assertThat( refService.getDocUrl( ) )
					.as( "docs" )
					.isEqualTo( "https://github.com/csap-platform/csap-core/wiki#updateRefCode+Samples" ) ;

			assertThat( refService.getDeployTimeOutMinutes( ) )
					.as( "deploy timeout" )
					.isEqualTo( "55" ) ;

		}

		@Test
		public void verify_agent_attributes ( ) {

			logger.info( CsapApplication.testHeader( ) ) ;

			ServiceInstance agentInstance = getApplication( ).getServiceInstanceCurrentHost( CsapCore.AGENT_ID ) ;

			assertThat( agentInstance.isKubernetesMaster( ) )
					.as( "k8 master" )
					.isFalse( ) ;

			assertThat( agentInstance.is_springboot_server( ) )
					.as( "server override" )
					.isTrue( ) ;

			assertThat( agentInstance.getProcessFilter( ) )
					.as( "process filter" )
					.isEqualTo( ".*java.*csapProcessId=" + CsapCore.AGENT_NAME + ".*8011.*" ) ;

			assertThat( agentInstance.getOsProcessPriority( ) )
					.as( "process override" )
					.isEqualTo( -99 ) ;

			assertThat( agentInstance.getAttribute( ServiceAttributes.parameters ) )
					.as( "process override" )
					.contains( "-Doverride=true" ) ;

			assertThat( agentInstance.getMavenId( ) )
					.as( "maven override" )
					.contains( "9.9.9" ) ;

			assertThat( agentInstance.getPerformanceConfiguration( ).isMissingNode( ) )
					.as( "missing performance data" )
					.isFalse( ) ;

			/**
			 * 
			 */

			logger.info( "Agent service meters:\n{}", agentInstance.getServiceMeters( ) ) ;

			assertThat( agentInstance.getServiceMeters( ) )
					.as( "service meters" )
					.hasSize( 18 ) ;

			assertThat( agentInstance.getServiceMeterTitles( ) )
					.as( "service meter titles" )
					.hasSize( 18 ) ;

			assertThat( agentInstance.getServiceMeterTitles( ).toString( ) )
					.as( "service title string" )
					.contains( "TotalVmCpu", "Host Cpu" ) ;

		}

		@Test
		void verify_agent_jobs ( ) {

			logger.info( CsapApplication.testHeader( ) ) ;

			ServiceInstance agentInstance = getApplication( ).getServiceInstanceCurrentHost( CsapCore.AGENT_ID ) ;

			assertThat( agentInstance.getJobs( ).size( ) )
					.as( "number of jobs" )
					.isEqualTo( 4 ) ;

			assertThat( agentInstance.getJobs( ).get( 0 ).getEnvironmentFilters( ) )
					.as( "lifecycle filters" )
					.hasSize( 1 )
					.contains( ".*" ) ;

			assertThat( agentInstance.getJobs( ).get( 0 ).getScript( ) )
					.as( "script path" )
					.isEqualTo( getApplication( ).getInstallationFolderAsString( ) + "/bin/checkLimits.sh" ) ;

			assertThat( agentInstance.getJobs( ).get( 0 ).getHour( ) )
					.as( "invokation hour" )
					.isEqualTo( "01" ) ;

			assertThat( agentInstance.getJobs( ).get( 1 ).isDiskCleanJob( ) )
					.as( "is disk clean job" )
					.isTrue( ) ;

			assertThat( agentInstance.getJobs( ).get( 1 ).getMaxDepth( ) )
					.as( "maxDepth to search" )
					.isEqualTo( 5 ) ;

			assertThat( agentInstance.getJobs( ).get( 1 ).isPruneEmptyFolders( ) )
					.as( "prune empty folders" )
					.isTrue( ) ;

			assertThat( agentInstance.getJobs( ).get( 1 ).getPath( ) )
					.as( "disk path" )
					.isEqualTo( getApplication( ).getInstallationFolderAsString( ) + "/scripts" ) ;

			assertThat( agentInstance.getLogsToRotate( ).size( ) )
					.as( "number of logs" )
					.isEqualTo( 3 ) ;

			assertThat( agentInstance.getLogsToRotate( ).get( 0 ).getPath( ) )
					.as( "log file" )
					.endsWith( "logs/consoleLogs.txt" ) ;

			assertThat( agentInstance.getLogsToRotate( ).get( 0 ).isActive( ) )
					.as( "log settings active" )
					.isTrue( ) ;

			assertThat( agentInstance.getLogsToRotate( ).get( 0 ).getLifecycles( ) )
					.as( "log file" )
					.isEqualTo( "all" ) ;

			assertThat( agentInstance.getLogsToRotate( ).get( 1 ).isActive( ) )
					.as( "log settings active" )
					.isTrue( ) ;

			assertThat( agentInstance.getLogsToRotate( ).get( 2 ).isActive( ) )
					.as( "log settings active" )
					.isFalse( ) ;

		}

		@Test
		public void verify_docker_service_overrides ( ) {

			ServiceInstance k8sDemoService = getApplication( ).getServiceInstanceCurrentHost( "k8s-test-deploy_0" ) ;
			logger.info( "k8sDemoService on host: {}", k8sDemoService ) ;

			logger.info( "k8sDemoService on host: {}", k8sDemoService.getDockerImageName( ) ) ;

			assertThat( k8sDemoService.getDockerSettings( ).path( "runUser" ).asText( ) )
					.as( "runUser" )
					.isEqualTo( "$csapUser" ) ;

			assertThat( k8sDemoService.getDockerImageName( ) )
					.as( "image name" )
					.isEqualTo( "junit/overide:latest" ) ;

		}

		@Test
		public void verify_service_names ( ) {

			String serviceNames = getApplication( )
					.getRootProject( )
					.getProjects( )
					.map( Project::serviceInstancesInCurrentLifeByName )
					.map( TreeMap::keySet )
					.flatMap( serviceNamesInPackage -> serviceNamesInPackage.stream( ) )
					.map( name -> StringUtils.rightPad( name, 25 ) )
					.collect( Collectors.joining( "" ) ) ;

			logger.info( "serviceNames\n{}", WordUtils.wrap( serviceNames, 150 ) ) ;

		}

		@Test
		public void verify_simple_clusters ( ) {

			logger.info( CsapApplication.testHeader( ) ) ;

			ArrayList<ServiceInstance> simpleServiceInstances = getApplication( ).serviceNameToAllInstances( )
					.get( "simple-cluster-service" ) ;

			assertThat( simpleServiceInstances ).isNotNull( ) ;

			logger.info( "Found: {} instances", simpleServiceInstances.size( ) ) ;

			assertThat( simpleServiceInstances.size( ) )
					.as( "number of instance" )
					.isEqualTo( 2 ) ;

			assertThat( getApplication( ).getServiceInstanceCurrentHost( "simple-cluster-service_9121" ).getCluster( ) )
					.as( "number of instance" )
					.isEqualTo( "simple-cluster" ) ;

			assertThat( getApplication( ).getServiceInstanceCurrentHost( "simple-cluster-service_9121" )
					.getClusterType( ) )
							.as( "number of instance" )
							.isEqualTo( ClusterType.SIMPLE ) ;

		}

		@Test
		public void verify_boot_attributes ( ) {

			logger.info( CsapApplication.testHeader( ) ) ;

			ServiceInstance bootService = getApplication( ).serviceNameToAllInstances( ).get( "SpringBootRest" ).get(
					0 ) ;

			logger.info( "{}", bootService.toString( ) ) ;

			assertThat( bootService.is_springboot_server( ) )
					.as( "server type" )
					.isTrue( ) ;

			assertThat( bootService.getContext( ) )
					.as( "context" )
					.isEqualTo( "SpringBootRest" ) ;

			assertThat( bootService.getUrl( ) )
					.as( "boot url" )
					.isEqualTo( "http://localhost." + CsapCore.DEFAULT_DOMAIN + ":0/admin/info" ) ;

			assertThat( bootService.getProcessFilter( ) )
					.as( "process filter" )
					.isEqualTo( ".*java.*csapProcessId=SpringBootRest.*0.*" ) ;

			assertThat( bootService.getOsProcessPriority( ) )
					.as( "process override" )
					.isEqualTo( 0 ) ;

			assertThat( bootService.getAttribute( ServiceAttributes.parameters ) )
					.as( "parameters" )
					.contains( "-DcsapJava8  -Xms128M -Xmx133M -XX:MaxMetaspaceSize=96M" ) ;

			assertThat( bootService.getMavenId( ) )
					.as( "maven" )
					.contains( "org.demo:SpringBootRest:0.0.1-SNAPSHOT:jar" ) ;

		}

		@Test
		public void verify_hyphenated_names ( ) {

			logger.info( CsapApplication.testHeader( ) ) ;

			ServiceInstance hyphenService = getApplication( ).serviceNameToAllInstances( ).get( "factory-service-2" )
					.get( 0 ) ;

			assertThat( hyphenService.is_springboot_server( ) )
					.as( "server override" )
					.isTrue( ) ;

			assertThat( hyphenService.getContext( ) )
					.as( "context" )
					.isEqualTo( "factory-service-2-dev001" ) ;

			assertThat( hyphenService.getProcessFilter( ) )
					.as( "process filter" )
					.isEqualTo( ".*java.*csapProcessId=factory-service-2.*0.*" ) ;

			assertThat( hyphenService.getHostName( ) )
					.as( "hostname" )
					.isEqualTo( "factory-dev001" ) ;

			assertThat( hyphenService.getUrl( ) )
					.as( "url" )
					.isEqualTo( "http://factory-dev001." + CsapCore.DEFAULT_DOMAIN + ":0/factory-service-2-dev001" ) ;

		}

		@Test
		public void verify_FactoryService_attributes ( ) {

			logger.info( CsapApplication.testHeader( ) ) ;

			ServiceInstance factoryService = getApplication( ).serviceNameToAllInstances( ).get( "FactoryService" ).get(
					0 ) ;

			assertThat( factoryService.is_springboot_server( ) )
					.as( "server override" )
					.isTrue( ) ;

			assertThat( factoryService.getContext( ) )
					.as( "context" )
					.isEqualTo( "FactoryService-dev001" ) ;

			assertThat( factoryService.getProcessFilter( ) )
					.as( "process filter" )
					.isEqualTo( ".*java.*csapProcessId=FactoryService.*0.*" ) ;

			assertThat( factoryService.getHostName( ) )
					.as( "hostname" )
					.isEqualTo( "factory-dev001" ) ;

			assertThat( factoryService.getUrl( ) )
					.as( "url" )
					.isEqualTo( "http://factory-dev001." + CsapCore.DEFAULT_DOMAIN + ":0/FactoryService-dev001" ) ;

		}

		@Test
		public void verify_JmxRemoteService_attributes ( ) {

			logger.info( CsapApplication.testHeader( ) ) ;
			ServiceInstance jmxRemoteService = getApplication( ).serviceNameToAllInstances( ).get( "JmxRemoteService" )
					.get( 0 ) ;

			assertThat( jmxRemoteService.is_java_application_server( ) )
					.as( "tomcat based service" )
					.isTrue( ) ;

			assertThat( jmxRemoteService.isJavaJmxCollectionEnabled( ) )
					.as( "is jmx collection" )
					.isTrue( ) ;

			assertThat( jmxRemoteService.isRemoteCollection( ) )
					.as( "has collection host" )
					.isTrue( ) ;

			assertThat( jmxRemoteService.getRuntime( ) )
					.as( "server type" )
					.isEqualTo( "tomcat8-5.x" ) ;

			assertThat( jmxRemoteService.getCollectHost( ) )
					.as( "collection host" )
					.isEqualTo( "csap-dev99" ) ;

			assertThat( jmxRemoteService.getCollectPort( ) )
					.as( "collect port" )
					.isEqualTo( "8996" ) ;

			assertThat( jmxRemoteService.getPerformanceConfiguration( ).isMissingNode( ) )
					.as( "missing performance data" )
					.isFalse( ) ;

			assertThat( jmxRemoteService.getPerformanceConfiguration( ).at( "/classesLoaded/mbean" ).asText( ) )
					.as( "mbean setting" )
					.isEqualTo( "java.lang:type=ClassLoading" ) ;

		}

		@Test
		public void verify_release_file ( ) {

			logger.info( CsapApplication.testHeader( ) ) ;

			ServiceInstance releaseService = getApplication( ).serviceNameToAllInstances( ).get( "ReleaseService" ).get(
					0 ) ;

			assertThat( releaseService.is_springboot_server( ) )
					.as( "spring boot server" )
					.isTrue( ) ;

			// assertThat( releaseService.getMavenId() )
			// .as( "mavenId" )
			// .isEqualTo( "org.demo:SpringBootRest:0.0.1-SNAPSHOT:jar" );

			assertThat( releaseService.getMavenId( ) )
					.as( "mavenId" )
					.isEqualTo( "org.demo:SpringBootRest:9.9.9-SNAPSHOT:jar" ) ;

		}

		@Test
		public void show_services ( ) {

			logger.info( CsapApplication.testHeader( ) ) ;

			String defaultServiceList = getApplication( )
					.getServicesOnHost( )
					.stream( )
					.map( ServiceInstance::getServiceName_Port )
					.collect( Collectors.joining( "\t" ) ) ;

			String sortedServiceList = getApplication( )
					.getServicesOnHost( )
					.stream( )
					.sorted( comparing( ServiceInstance::getAutoStart ) )
					.map( ServiceInstance::getServiceName_Port )
					.collect( Collectors.joining( "\t" ) ) ;

			String reversed = getApplication( )
					.getServicesOnHost( )
					.stream( )
					.sorted( comparing( ServiceInstance::getAutoStart ).reversed( ) )
					.map( ServiceInstance::getServiceName_Port )
					.collect( Collectors.joining( "\t" ) ) ;

			List<String> reversedList = getApplication( )
					.getServicesOnHost( )
					.stream( )
					.sorted( comparing( ServiceInstance::getAutoStart ).reversed( ) )
					.map( ServiceInstance::getServiceName_Port )
					.collect( Collectors.toList( ) ) ;

			logger.info( "default: {} \n\t sorted: {} \n\t reversed: {}\n\t list: {}",
					defaultServiceList, sortedServiceList, reversed, reversedList ) ;

		}
	}

	@Nested
	@DisplayName ( "kubernetes" )
	class Kubernetes {

		@Test
		public void verify_kubernetes_cluster ( ) {

			logger.info( CsapApplication.testHeader( ) ) ;

			ArrayList<ServiceInstance> k8ProxyInstances = getApplication( ).serviceNameToAllInstances( ).get(
					"k8s-proxy" ) ;

			assertThat( k8ProxyInstances ).isNotNull( ) ;

			logger.info( "Found: {} instances", k8ProxyInstances.size( ) ) ;

			assertThat( k8ProxyInstances.size( ) )
					.as( "number of instance" )
					.isEqualTo( 2 ) ;

			ServiceInstance proxyInstance = getApplication( ).getServiceInstanceCurrentHost( "k8s-proxy_0" ) ;
			assertThat( proxyInstance.getCluster( ) )
					.as( "number of instance" )
					.isEqualTo( "k8-services" ) ;

			assertThat( proxyInstance.getClusterType( ) )
					.as( "number of instance" )
					.isEqualTo( ClusterType.KUBERNETES ) ;

			assertThat( proxyInstance.getKubernetesMasterHostNames( ).get( 0 ).asText( ) )
					.as( "k8 master host name" )
					.isEqualTo( "localhost" ) ;

			ArrayList<ServiceInstance> k8DemoServices = getApplication( ).serviceNameToAllInstances( ).get(
					"k8s-demo-service" ) ;
			logger.info( "testDeployInstances: {}", k8DemoServices ) ;

			assertThat( k8DemoServices.size( ) )
					.as( "k8s-demo-service count" )
					.isEqualTo( 3 ) ;

			assertThat( k8DemoServices.get( 0 ).getKubernetesNamespace( ) )
					.as( "k8s-demo-service namespace" )
					.isEqualTo( "csap-test" ) ;

			logger.info( "k8DemoServices: masterhostnames: {}", k8DemoServices.get( 0 )
					.getKubernetesMasterHostNames( ) ) ;
			assertThat( k8DemoServices.get( 0 ).getKubernetesMasterHostNames( ).get( 0 ).asText( ) )
					.as( "k8s-demo-service master host name" )
					.isEqualTo( "master-host" ) ;

		}

		@Test
		public void verify_kubernetes_provider ( ) {

			logger.info( CsapApplication.testHeader( ) ) ;

			ArrayList<ServiceInstance> kubeletServices = getApplication( ).serviceNameToAllInstances( ).get(
					"kubelet" ) ;

			assertThat( kubeletServices ).isNotNull( ) ;

			logger.info( "kubeletServices: {}", kubeletServices ) ;

			assertThat( kubeletServices.size( ) )
					.as( "number of instance" )
					.isEqualTo( 5 ) ;

			ServiceInstance kubeletService = getApplication( ).getServiceInstanceCurrentHost( "kubelet_8014" ) ;

			logger.info( "kubeletService: {}", kubeletService.details( ) ) ;

			assertThat( kubeletService.getCluster( ) )
					.as( "number of instance" )
					.isEqualTo( "kubernetes-1" ) ;

			assertThat( kubeletService.getClusterType( ) )
					.as( "number of instance" )
					.isEqualTo( ClusterType.KUBERNETES_PROVIDER ) ;

			assertThat( kubeletService.isKubernetesMaster( ) )
					.as( "k8 master" )
					.isTrue( ) ;

			assertThat( getApplication( ).getServiceInstanceAnyPackage( "kubelet_8014", "k8-worker-host" )
					.getClusterType( ) )
							.as( "k8 worker" )
							.isEqualTo( ClusterType.KUBERNETES_PROVIDER ) ;

			assertThat( getApplication( ).getServiceInstanceAnyPackage( "kubelet_8014", "k8-worker-host" )
					.isKubernetesMaster( ) )
							.as( "k8 master" )
							.isFalse( ) ;

		}

	}

	@Nested
	@TestInstance ( TestInstance.Lifecycle.PER_CLASS )
	@DisplayName ( "configuration maps" )
	class ConfigurationMaps {

		ServiceOsManager serviceOsManager ;

		@BeforeAll
		void beforeAll ( ) {

			serviceOsManager = new ServiceOsManager( getApplication( ) ) ;

		}

		@Test
		void verify_configuration_maps ( ) {

			ServiceInstance agentInstance = getApplication( ).getServiceInstanceCurrentHost( CsapCore.AGENT_ID ) ;

			logger.info( CsapApplication.testHeader( ) + agentInstance.toString( ) ) ;

			var testMap = getApplication( ).rootProjectEnvSettings( ).getConfigurationMap( "map-for-testing" ) ;
			logger.info( "map-for-testing values: {}", testMap ) ;

			CSAP.setLogToInfo( serviceOsManager.getClass( ).getName( ) ) ;
			LinkedHashMap<String, String> agentEnvVars = serviceOsManager.buildServiceEnvironmentVariables(
					agentInstance ) ;
			CSAP.setLogToInfo( serviceOsManager.getClass( ).getName( ) ) ;

			logger.info( "agent environmentVariables: {}, \n\n\t notes: {}", agentEnvVars ) ;

			assertThat( agentEnvVars.get( "test-global-1" ) )
					.isEqualTo( "test-global-value-1" ) ;

			assertThat( agentEnvVars.get( "test-name-2" ) )
					.isEqualTo( "test-value-2" ) ;

			assertThat( agentEnvVars.get( EnvironmentSettings.CONFIGURATION_MAPS ) )
					.isNull( ) ;

			assertThat( agentEnvVars.get( "test-map-name-1" ) )
					.isEqualTo( "test-map-value-1" ) ;

			assertThat( agentEnvVars.get( "test-map-name-2" ) )
					.as( "Cluster name override" )
					.isEqualTo( "test-map-value-2-override" ) ;

		}

		File simpleYaml = new File(
				Model_As_Agent.class.getResource( "csap-simple.yaml" ).getPath( ) ) ;

		@Test
		public void verify_k8_spec_service_environment_variables ( ) {

			logger.info( CsapApplication.testHeader( ) ) ;

			var k8SpecService = getApplication( ).findFirstServiceInstanceInLifecycle( "k8s-spec-deploy-service" ) ;
			JsonNode serviceEnvVarDefinition = k8SpecService.getAttributeOrMissing(
					ServiceAttributes.environmentVariables ) ;

			logger.info( "k8SpecService serviceEnvVarDefinition {}", CSAP.jsonPrint( serviceEnvVarDefinition ) ) ;

			//
			// verify file names are translated
			//
			var specfiles = serviceOsManager.buildSpecificationFileArray(
					k8SpecService,
					k8SpecService.getKubernetesDeploymentSpecifications( ) ) ;

			var specUriPaths = specfiles.map( URI::toString ).collect( Collectors.joining( "," ) ) ;

			logger.info( "fileNames: {}", specUriPaths ) ;

			var partialNameWithVariablesResolved = CsapCore.SEARCH_RESOURCES + "spec-from-resource-life-nfs.yaml" ;

			assertThat( specUriPaths )
					.doesNotContain( "$$storage_type" )
					.contains( "k8s-spec-deploy-service/common/csap-simple.yaml" )
					.contains( partialNameWithVariablesResolved ) ;

			var k8EnvVariables = serviceOsManager.buildServiceEnvironmentVariables( k8SpecService ) ;

			logger.info( "k8SpecService environmentVariables: {}, \n\n\t notes: {}", k8EnvVariables ) ;

			assertThat( k8EnvVariables.get( "test-global-1" ) )
					.isEqualTo( "test-global-value-1" ) ;

			assertThat( k8EnvVariables.get( "variable_1" ) )
					.contains( "context-path=/k8s-spec-deploy-service" ) ;

			logger.debug( "Original yaml: {} ", getApplication( ).readFile( simpleYaml ) ) ;
			var deploymentFile = serviceOsManager.buildDeplomentFile( k8SpecService, simpleYaml,
					getJsonMapper( ).createObjectNode( ) ) ;

			var yaml_with_vars_updated = Application.readFile( deploymentFile ) ;

			logger.debug( "yaml_with_vars_updated: {}", yaml_with_vars_updated ) ;

			assertThat( yaml_with_vars_updated )
					.contains( "context-path=/k8s-spec-deploy-service" ) ;

			logger.info( "resource folder: {}", ServiceResources.serviceResourceFolder( k8SpecService.getName( ) ) ) ;

		}

		@Test
		public void verify_kubernetes_path_variable_updates ( ) {

			logger.info( CsapApplication.testHeader( ) ) ;

		}

	}
}
