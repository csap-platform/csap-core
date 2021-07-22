package org.csap.agent.full.project ;

import static org.assertj.core.api.Assertions.assertThat ;
import static org.assertj.core.api.Assertions.contentOf ;

import java.io.File ;
import java.io.IOException ;
import java.util.ArrayList ;
import java.util.Arrays ;
import java.util.stream.Collectors ;

import javax.inject.Inject ;

import org.apache.commons.io.FileUtils ;
import org.csap.agent.Agent_context_loaded ;
import org.csap.agent.CsapBareTest ;
import org.csap.agent.CsapCore ;
import org.csap.agent.CsapCoreService ;
import org.csap.agent.integrations.HttpdIntegration ;
import org.csap.agent.linux.HostStatusManager ;
import org.csap.agent.model.Application ;
import org.csap.agent.model.ClusterType ;
import org.csap.agent.model.EnvironmentSettings ;
import org.csap.agent.model.ServiceInstance ;
import org.csap.helpers.CSAP ;
import org.csap.helpers.CsapApplication ;
import org.junit.jupiter.api.BeforeAll ;
import org.junit.jupiter.api.BeforeEach ;
import org.junit.jupiter.api.Disabled ;
import org.junit.jupiter.api.Tag ;
import org.junit.jupiter.api.Test ;
import org.junit.jupiter.api.TestInstance ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.boot.test.context.SpringBootTest ;
import org.springframework.test.context.ActiveProfiles ;

import com.fasterxml.jackson.core.JsonProcessingException ;
import com.fasterxml.jackson.databind.ObjectMapper ;

@Tag ( "full" )
@SpringBootTest ( classes = CsapCoreService.class )
@ActiveProfiles ( {
		CsapBareTest.PROFILE_JUNIT, "Load_Working_Models_Test"
} )

@TestInstance ( TestInstance.Lifecycle.PER_CLASS )
class Load_Working_Models_Test {

	Logger logger = LoggerFactory.getLogger( getClass( ) ) ;

	static {

		CsapApplication.initialize( "" ) ;

	}

	@BeforeAll
	void beforeAll ( )
		throws Exception {

		CsapApplication.initialize( logger.getName( ) ) ;

	}

	@BeforeEach
	public void beforeEach ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		csapApplication.setJvmInManagerMode( false ) ;
		csapApplication.setAutoReload( false ) ;
		csapApplication.getProjectLoader( ).setAllowLegacyNames( true ) ;

		logger.info( "Deleting: {}", getHttpdConfig( ).getHttpdWorkersFile( ).getParentFile( ).getAbsolutePath( ) ) ;
		FileUtils.deleteQuietly( getHttpdConfig( ).getHttpdWorkersFile( ).getParentFile( ) ) ;

	}

	@Inject
	Application csapApplication ;

	@Test
	public void get_latest_csap_version_number_from_csaptools ( )
		throws JsonProcessingException ,
		IOException {

		logger.info( CsapApplication.testHeader( ) ) ;

		CSAP.setLogToDebug( Application.class.getName( ) ) ;
		assertThat( csapApplication.getScoreReport( ).updatePlatformVersionsFromCsapTools( true ) )
				.as( "Get Latest version for csap scorecards on landing page" )
				.matches( "2.*6.*6.*" ) ;
		CSAP.setLogToInfo( Application.class.getName( ) ) ;

	}

	@Test
	public void load_application_with_hosts_excluded_from_httpd ( )
		throws JsonProcessingException ,
		IOException {

		logger.info( CsapApplication.testHeader( ) ) ;

		File csapApplicationDefinition = new File(
				getClass( ).getResource( "application-hosts-filtered.json" ).getPath( ) ) ;

		assertThat( csapApplication.loadDefinitionForJunits( false, csapApplicationDefinition ) )
				.as( "No Errors or warnings" )
				.isTrue( ) ;

		assertThat( csapApplication.getName( ) )
				.as( "Capability Name parsed" )
				.isEqualTo( "DEFAULT APPLICATION FOR JUNITS" ) ;

		assertThat( csapApplication.getLifecycleList( ) )
				.as( "Lifecycles found" )
				.hasSize( 3 )
				.contains( "dev", "dev:simple-cluster", "defaults" ) ;

		assertThat( csapApplication.serviceNameToAllInstances( ).get( CsapCore.AGENT_NAME ) )
				.as( "CsAgents instances found" )
				.hasSize( 2 ) ;

		assertThat( csapApplication.getServiceInstanceAnyPackage( CsapCore.AGENT_ID ).getOsProcessPriority( ) )
				.as( "CsAgent OS Priority" )
				.isEqualTo(
						-12 ) ;

		assertThat( csapApplication.getServiceInstanceCurrentHost( CsapCore.AGENT_ID ).getMavenRepo( ) )
				.as( "CsAgent Maven Repo" )
				.isEqualTo(
						"https://repo.maven.apache.org/maven2/" ) ;

		assertThat( getHttpdConfig( ).getHttpdWorkersFile( ) )
				.as( "Web Servers Worker file created" )
				.exists( ) ;

		assertThat( contentOf( getHttpdConfig( ).getHttpdWorkersFile( ) ) )
				.as( "Ensure filtered VMs are not in web server config files" )
				.doesNotContain(
						"balance_workers=simple-tomcat_8241localhost,simple-tomcat_8241host2" )
				.contains(
						"worker.simple-tomcat_8241localhost.type=ajp13" )
				.contains(
						"balance_workers=simple-tomcat_8241localhost" ) ;

		assertThat( getHttpdConfig( ).getHttpdModjkFile( ) )
				.as( "Web Servers ModJK file created" )
				.exists( ) ;

		assertThat( contentOf( getHttpdConfig( ).getHttpdModjkFile( ) ) )
				.contains( "simple-tomcat" ) ;

	}

	/**
	 *
	 * Scenario: - load a config file without package definition and 1 jvm and 1 os
	 *
	 */
	@Test
	public void load_simple_application_as_agent ( )
		throws JsonProcessingException ,
		IOException {

		logger.info( CsapApplication.testHeader( ) ) ;

		File csapApplicationDefinition = new File(
				getClass( ).getResource( "application-simple.json" ).getPath( ) ) ;

		assertThat( csapApplication.loadDefinitionForJunits( false, csapApplicationDefinition ) )
				.as( "No Errors or warnings" )
				.isTrue( ) ;

		assertThat( csapApplication.getName( ) )
				.as( "Capability Name parsed" )
				.isEqualTo( "DEFAULT APPLICATION FOR JUNITS" ) ;

		assertThat( csapApplication.getLifecycleList( ) )
				.as( "Lifecycles found" )
				.hasSize( 5 )
				.contains( "dev", "stage" ) ;

		assertThat( csapApplication.serviceNameToAllInstances( ).get( CsapCore.AGENT_NAME ) )
				.as( "CsAgent Instances found" )
				.hasSize( 3 ) ;

		ServiceInstance agentInstance = csapApplication.getServiceInstanceCurrentHost( CsapCore.AGENT_ID ) ;
		csapApplication.getOsManager( ).checkForProcessStatusUpdate( ) ;

		assertThat( agentInstance.isAutoStart( ) )
				.as( "CsAgent autostart" )
				.isTrue( ) ;

		assertThat( ! agentInstance.getDefaultContainer( ).isInactive( ) )
				.as( "CsAgent Started" )
				.isTrue( ) ;

		assertThat( agentInstance.getDefaultContainer( ).getPid( ) )
				.as( "CsAgent pid" )
				.contains( "10941" ) ;

		assertThat( agentInstance.getDefaultContainer( ).getPidsAsString( ) )
				.as( "CsAgent pid string" )
				.isEqualTo( "10941" ) ;

		assertThat( agentInstance.getOsProcessPriority( ) )
				.as( "CsAgent process priority" )
				.isEqualTo( -10 ) ;

		assertThat( agentInstance.getDefaultLogToShow( ) )
				.as( "Default log file for service" )
				.isEqualTo( "console.log" ) ;

		assertThat( agentInstance.getMavenRepo( ) )
				.as( "CsAgent Maven Repo" )
				.isEqualTo(
						"https://repo.maven.apache.org/maven2/" ) ;

		ServiceInstance jdkInstance = csapApplication.getServiceInstanceCurrentHost( "jdk_0" ) ;
		assertThat( jdkInstance.getDefaultContainer( ).getPidsAsString( ) )
				.as( "jdk pid string" )
				.isEqualTo( "" ) ;

		ServiceInstance refService = csapApplication.serviceNameToAllInstances( ).get( "Cssp3ReferenceMq" ).get( 0 ) ;
		assertThat( refService.isTomcatPackaging( ) )
				.as( "server override" )
				.isTrue( ) ;

		ServiceInstance httpdService = csapApplication.serviceNameToAllInstances( ).get( "httpd" ).get( 0 ) ;

		assertThat( httpdService.isAutoStart( ) )
				.as( "httpdService autostart" )
				.isTrue( ) ;

		assertThat( httpdService.is_csap_api_server( ) )
				.as( "httpdService autostart" )
				.isTrue( ) ;

		assertThat( httpdService.getDefaultLogToShow( ) )
				.as( "Default log file for service" )
				.isEqualTo( "access.log" ) ;

		logger.info( "Verifying Apache Workers file: {}", getHttpdConfig( ).getHttpdWorkersFile( )
				.getAbsolutePath( ) ) ;
		assertThat( getHttpdConfig( ).getHttpdWorkersFile( ) )
				.as( "Web Servers Worker file created" )
				.exists( ) ;

		assertThat( contentOf( getHttpdConfig( ).getHttpdWorkersFile( ) ) )
				.contains(
						"worker.Cssp3ReferenceMq_0localhost.type=ajp13" ) ;

		assertThat( getHttpdConfig( ).getHttpdModjkFile( ) )
				.as( "Web Servers ModJK file created" )
				.exists( ) ;

		assertThat( contentOf( getHttpdConfig( ).getHttpdModjkFile( ) ) )
				.contains( "Cssp3ReferenceMq" ) ;

	}

	/**
	 *
	 * Scenario: - load a config file with multiple services, some shared nothing,
	 * some standard - httpd instance has generateWorkerProperties metadata in
	 * cluster.js - verify that mount points get generated
	 *
	 */
	@Test
	public void load_application_with_cluster_types ( )
		throws JsonProcessingException ,
		IOException {

		File csapApplicationDefinition = new File(
				getClass( ).getResource( Agent_context_loaded.TEST_DEFINITIONS + "test_application_model.json" )
						.getPath( ) ) ;

		assertThat( csapApplication.loadDefinitionForJunits( false, csapApplicationDefinition ) )
				.as( "No Errors or warnings" )
				.isTrue( ) ;

		assertThat( csapApplication.getName( ) )
				.as( "Capability Name parsed" )
				.isEqualTo( "TestDefinitionForAutomatedTesting" ) ;

		assertThat( csapApplication.getRootProject( ).getHostToServicesMap( ).keySet( ) )
				.as( "All Hosts found" )
				.hasSize( 9 )
				.contains( "csap-dev01",
						"csap-dev02",
						"csapdb-dev01", "localhost",
						"peter-dev01", "xcssp-qa01",
						"xcssp-qa02",
						"xfactory-qa01",
						"xfactory-qa02" ) ;

		assertThat( csapApplication.getAllHostsInAllPackagesInCurrentLifecycle( ) )
				.as( "Lifecycles Hosts found" )
				.hasSize( 5 )
				.contains( "localhost",
						"csapdb-dev01" ) ;

		assertThat( csapApplication.getLifecycleList( ) )
				.as( "Lifecycles found" )
				.hasSize( 9 )
				.contains( "dev", "dev:WebServer", "stage" ) ;

		assertThat( csapApplication.serviceNameToAllInstances( ).get( CsapCore.AGENT_NAME ) )
				.as( "CsAgents instances found" )
				.hasSize( 9 ) ;

		assertThat( csapApplication.serviceNameToAllInstances( ).keySet( ) )
				.as( "Service instances found" )
				.hasSize( 15 )
				.contains( "AuditService", CsapCore.AGENT_NAME, "CsspSample",
						"Factory2Sample",
						"FactorySample", "SampleDataLoader",
						"ServletSample", "activemq",
						CsapCore.ADMIN_NAME, "denodo", "httpd", "oracle",
						"sampleOsWrapper",
						"springmvc-showcase", "vmmemctl" ) ;

		// New instance meta data
		ServiceInstance csAgentInstance = csapApplication
				.getServiceInstanceAnyPackage( CsapCore.AGENT_ID ) ;

		assertThat( csAgentInstance.getOsProcessPriority( ) )
				.as( "CsAgent OS Priority" )
				.isEqualTo( -12 ) ;

		// logger.info("Workers file: " +
		// getHttpdConfig().getHttpdWorkersFile().getAbsolutePath());
		assertThat( getHttpdConfig( ).getHttpdWorkersFile( ) )
				.as( "Web Servers Worker file created" )
				.exists( ) ;

		assertThat( contentOf( getHttpdConfig( ).getHttpdWorkersFile( ) ) )
				.contains( "worker.FactorySample-dev01_LB" )
				.contains( "worker.AuditService_LB" ) ;

		assertThat( getHttpdConfig( ).getHttpdModjkFile( ) )
				.as( "Web Servers ModJK file created" )
				.exists( ) ;

		assertThat( contentOf( getHttpdConfig( ).getHttpdModjkFile( ) ) )
				.as( "Mounts include both singleVm partion suffix and non suffix for enterpise" )
				.contains( "springmvc-showcase" )
				.contains( "Factory2Sample-dev01" ) ;

	}

	/**
	 * 
	 * Quick way to validate applications reporting issues
	 * 
	 */
	@Test
	public void validate_application_in_home_folder ( )
		throws JsonProcessingException ,
		IOException {

		if ( ! csapApplication.isCompanyVariableConfigured( "test.variables.test-external-application" ) ) {

			logger.info( "company variable not set - skipping test" ) ;
			return ;

		}

		File csapApplicationDefinition = new File(
				csapApplication.getCompanyConfiguration( "test.variables.test-external-application", "none" ) ) ;

		assertThat( csapApplication.loadDefinitionForJunits( false, csapApplicationDefinition ) )
				.as( "No Errors or warnings" )
				.isFalse( ) ;

		StringBuilder parsingResults = csapApplication.getLastTestParseResults( ) ;
		logger.info( "Sntc Parsing Results:\n {}", parsingResults ) ;
		assertThat( parsingResults.toString( ) )
				.as( "SNTC 3 loads with warnings" )
				.doesNotContain( CsapCore.CONFIG_PARSE_ERROR )
				.contains( CsapCore.CONFIG_PARSE_WARN ) ;

		assertThat( csapApplication.getName( ) )
				.as( "Capability Name parsed" )
				.isEqualTo( "Smart Services Platform" ) ;

		assertThat( csapApplication.getRootProject( )
				.getReleasePackageNames( )
				.collect( Collectors.toList( ) ) )
						.as( "Release Package names" )
						.containsExactly( "IBM",
								"Jolt",
								"Net Authenticate",
								"SC2SNTC Convergence",
								"SFC",
								"SNAS Dev",
								"SNTC and PSS",
								"SSP Shared",
								"Titanium" ) ;

		assertThat( csapApplication.getAllHostsInAllPackagesInCurrentLifecycle( ) )
				.as( "All Hosts in All Packages in Current Lifecycle" )
				.hasSize( 97 )
				.contains( "localhost", "v01app-dev801" ) ;

		assertThat( csapApplication.getRootProject( ).getAllPackagesModel( )
				.getServiceNameStream( )
				.collect( Collectors.toList( ) ) )
						.as( "All Services in all packages in Current Lifecycle" )
						.hasSize( 120 )
						.contains(
								"SparkMaster01",
								"SparkMaster02",
								"SparkSlave",
								"SparkSlave01",
								"SparkSlave02",
								"SsueMetaSvc",
								"SsueService" ) ;

		assertThat( getHttpdConfig( ).getHttpdWorkersFile( ) )
				.as( "Web Servers Worker file created" )
				.exists( ) ;

		assertThat( contentOf( getHttpdConfig( ).getHttpdWorkersFile( ) ) )
				.contains( "DataReaderSvc_LB", "admin_LB" ) ;

		assertThat( getHttpdConfig( ).getHttpdModjkFile( ) )
				.as( "Web Servers ModJK file created" )
				.exists( ) ;

		assertThat( contentOf( getHttpdConfig( ).getHttpdModjkFile( ) ) )
				.contains(
						"JkMount /DataReaderSvc* DataReaderSvc_LB" ) ;

	}

	/**
	 *
	 * Scenario loads a definition with a non-existant package file. File should be
	 * created using release template
	 *
	 * @throws JsonProcessingException
	 * @throws IOException
	 */
	@Test
	public void load_definition_with_new_package_creates_new_one ( )
		throws JsonProcessingException ,
		IOException {

		File csapApplicationDefinition = new File(
				getClass( ).getResource( "application-with-new-package.json" ).getPath( ) ) ;

		logger.info( CsapApplication.testHeader( "loading: {}" ), csapApplicationDefinition.getAbsolutePath( ) ) ;

		/*
		 * Delete previously generated files if they exist
		 */
		File generatedPackageFromTemplate = new File(
				csapApplicationDefinition.getParentFile( ),
				"junit-new-package.json" ) ;

		logger.info( "Deleting the autogenerated runs as it might exist from previous runs: '{}'",
				generatedPackageFromTemplate.getAbsolutePath( ) ) ;
		FileUtils.deleteQuietly( generatedPackageFromTemplate ) ;

		assertThat( csapApplication.loadDefinitionForJunits( false, csapApplicationDefinition ) )
				.as( "No Errors or warnings" )
				.isTrue( ) ;

		assertThat( csapApplication.getName( ) )
				.as( "Capability Name present" )
				.isEqualTo( "CSAP Default" ) ;

		assertThat( generatedPackageFromTemplate.exists( ) )
				.as( "New package was created from template" )
				.isTrue( ) ;

		String testInstances = csapApplication.getServiceInstances( "changeToYourPackageName", "csap-verify-service" )
				.map( ServiceInstance::toSummaryString )
				.collect( Collectors.joining( "\n" ) ) ;
		logger.info( "testInstances: {}", testInstances ) ;

		ServiceInstance newService = csapApplication.getServiceInstance( "csap-verify-service_7011",
				"change-me-1", "changeToYourPackageName" ) ;

		assertThat( newService )
				.as( "Manager was able to load services in new package" )
				.isNotNull( ) ;

		assertThat( newService.getServiceName_Port( ) )
				.as( "Manager was able to load services in new package" )
				.isEqualTo( "csap-verify-service_7011" ) ;

		generatedPackageFromTemplate.delete( ) ;

		logger.info( "Deleted: " + generatedPackageFromTemplate.getAbsolutePath( ) ) ;

	}

	private ObjectMapper jacksonMapper = new ObjectMapper( ) ;

	@Test
	void validate_all_packages_api_call ( )
		throws Exception {

		File csapApplicationDefinition = new File(
				getClass( ).getResource( Agent_context_loaded.TEST_DEFINITIONS
						+ "sub-project-release-file/clusterConfigManager.json" )
						.getPath( ) ) ;

		logger.info( CsapApplication.testHeader( "loading: {}" ), csapApplicationDefinition.getAbsolutePath( ) ) ;

		assertThat( csapApplication.loadDefinitionForJunits( false, csapApplicationDefinition ) )
				.as( "No Errors or warnings" )
				.isTrue( ) ;

		logger.info( "parse results: {}", csapApplication.getLastTestParseResults( ) ) ;

		assertThat( csapApplication.getName( ) )
				.as( "Capability Name parsed" )
				.isEqualTo( "Desktop Dev2" ) ;

		csapApplication.setTestModeToSkipActivate( true ) ;

		logger.info( "Application Definition\n {}",
				jacksonMapper.writerWithDefaultPrettyPrinter( ).writeValueAsString( csapApplication
						.getDefinitionForAllPackages( ) ) ) ;

		// Note that JUNITs use a different default name and location
		assertThat( csapApplication.getDefinitionForAllPackages( ).at( "/definitions/0/fileName" ).asText( ) )
				.as( "verify fileName" )
				.isEqualTo( "clusterConfigManager.json" ) ;

		assertThat( csapApplication.getDefinitionForAllPackages( ).at( "/definitions/0/content" ).asText( ) )
				.as( "verify content" )
				.contains( "clusterConfigManagerB.json" ) ;

	}

	@Test
	public void load_application_with_release_packages ( )
		throws JsonProcessingException ,
		IOException {

		logger.info( CsapApplication.testHeader( ) ) ;

		File csapApplicationDefinition = new File(
				getClass( ).getResource( Agent_context_loaded.TEST_DEFINITIONS
						+ "sub-project-localhost/clusterConfigMultiple.json" )
						.getPath( ) ) ;

		assertThat( csapApplication.loadDefinitionForJunits( false, csapApplicationDefinition ) )
				.as( "No Errors or warnings" )
				.isTrue( ) ;

		assertThat( csapApplication.getName( ) )
				.as( "Capability Name parsed" )
				.isEqualTo( "TestDefinitionWithMultipleServices" ) ;

		assertThat( csapApplication.getRootProject( )
				.getReleasePackageNames( )
				.collect( Collectors.toList( ) ) )
						.as( "Release Package names" )
						.containsExactly( "SampleDefaultPackage",
								"Supporting Sample A",
								"Supporting Sample B" ) ;

		assertThat( csapApplication.getModelForHost( "sampleHostA-dev01" ).getName( ) )
				.as( "Release Packag for host" )
				.isEqualTo(
						"Supporting Sample A" ) ;

		String activeModelAgents = csapApplication.getActiveProject( ).getServiceInstances( CsapCore.AGENT_NAME )
				.map( ServiceInstance::toString )
				.collect( Collectors.joining( "\n" ) ) ;
		logger.info( "All packages: {}, \n Active model: {}",
				csapApplication.serviceNameToAllInstances( ).get( CsapCore.AGENT_NAME ),
				activeModelAgents ) ;

		assertThat( csapApplication.serviceNameToAllInstances( ).get( CsapCore.AGENT_NAME ) )
				.as( "CsAgents instances found" )
				.hasSize( 3 ) ;

		assertThat( csapApplication.getActiveProject( ).getServiceInstances( CsapCore.AGENT_NAME ).count( ) )
				.as( "CsAgents instances in active model and lifecycle" )
				.isEqualTo( 2 ) ;

		assertThat( csapApplication.getMutableHostsInActivePackage( csapApplication.activeLifecycleClusterName( ) ) )
				.as( "All Hosts in Current Lifecycle" )
				.hasSize( 2 )
				.contains(
						"localhost",
						"middlewareA2Host-dev98" ) ;

		assertThat( csapApplication.getActiveProject( ).getLifecycleToClusterMap( ) )
				.as( "Active model Lifecycle to Groups" )
				.hasSize( 3 )
				.containsKeys( "dev", "stage" )
				.containsEntry( "dev",
						new ArrayList<String>(
								Arrays.asList(
										"middlewareA",
										"middlewareA2" ) ) ) ;

		assertThat( csapApplication.getActiveProject( ).getName( ) )
				.as( "Release Packag for active model" )
				.isEqualTo( "Supporting Sample A" ) ;

		assertThat( csapApplication.getProject( Application.ALL_PACKAGES ).getLifecycleToClusterMap( ).get( "dev" ) )
				.as( "Groups for dev" )
				.hasSize(
						4 )
				.contains(
						"cssp",
						"middlewareA",
						"middlewareA2",
						"middlewareB" ) ;

		assertThat( csapApplication.getServiceInstanceCurrentHost( CsapCore.AGENT_ID ).getMavenRepo( ) )
				.as( "CsAgent Maven Repo" )
				.isEqualTo(
						"https://repo.maven.apache.org/maven2/" ) ;

		assertThat( csapApplication.getServiceInstanceCurrentHost( "SampleJvmInA_8041" ).getName( ) )
				.as( "Sample service loaded" )
				.isEqualTo(
						"SampleJvmInA" ) ;

		assertThat( csapApplication.getRootProject( ).getAllPackagesModel( )
				.serviceInstancesInCurrentLifeByName( ).get( "CsspSample" ) )
						.as( "Service count in all packages" )
						.hasSize( 1 ) ;

		assertThat( csapApplication.getRootProject( ).getLifecycleToClusterMap( ) )
				.as( "Lifecycles to group in root model" )
				.hasSize( 3 )
				.containsKey( "dev" ) ;

		assertThat( csapApplication.getRootProject( ).getServiceToAllInstancesMap( ).keySet( ) )
				.as( "Services in root model" )
				.hasSize( 8 )
				.contains( CsapCore.AGENT_NAME,
						"CsspSample",
						"FactorySample",
						"RedHatLinux",
						"ServletSample",
						"httpd",
						"oracleDriver",
						"springmvc-showcase" ) ;

		assertThat( csapApplication.getProject( Application.ALL_PACKAGES )
				.getHostsForEnvironment( csapApplication.getCsapHostEnvironmentName( ) ) )
						.as( "Hosts in current lifecycle" )
						.hasSize(
								4 )
						.contains(
								"mainHostA",
								"localhost",
								"middlewareA2Host-dev98",
								"SampleHostB-dev99" ) ;

		assertThat( getHttpdConfig( ).getHttpdWorkersFile( ) )
				.as( "Ensure web server files are not created as they are not in active model" )
				.doesNotExist( ) ;

	}

	/**
	 *
	 * Scenario: Load definition with sub-packages, on a NON-manger jvm
	 *
	 * Verify: http config files INCLUDE the sub package mount points
	 *
	 * @throws JsonProcessingException
	 * @throws IOException
	 */
	@Disabled
	@Test
	public void load_application_with_web_server_enabled ( )
		throws JsonProcessingException ,
		IOException {

		File csapApplicationDefinition = new File(
				getClass( ).getResource( Agent_context_loaded.TEST_DEFINITIONS + "clusterConfigManager.json" )
						.getPath( ) ) ;

		assertThat( csapApplication.loadDefinitionForJunits( false, csapApplicationDefinition ) )
				.as( "No Errors or warnings" )
				.isTrue( ) ;

		assertThat( csapApplication.getName( ) )
				.as( "Capability Name parsed" )
				.isEqualTo( "Desktop Dev2" ) ;

		assertThat( csapApplication.getActiveProject( ).getName( ) )
				.as( "Release Package" )
				.isEqualTo( "SampleDefaultPackage" ) ;

		assertThat( csapApplication.serviceNameToAllInstances( ).get( CsapCore.AGENT_NAME ) )
				.as( "CsAgents instances found" )
				.hasSize( 9 ) ;

		assertThat( csapApplication.getActiveProject( ).getLifecycleToClusterMap( ) )
				.as( "Active model Lifecycle to Groups" )
				.hasSize( 2 )
				.containsKeys( "dev", "stage" )
				.containsEntry( "dev",
						new ArrayList<String>(
								Arrays.asList(
										"WebServer",
										"csspLocal",
										"cssp",
										"middleware" ) ) ) ;

		assertThat( csapApplication.getRootProject( ).getLifecycleToClusterMap( ) )
				.as( "Lifecycles to group in root model" )
				.hasSize( 2 )
				.containsKey( "dev" ) ;

		assertThat( getHttpdConfig( ).getHttpdWorkersFile( ) )
				.as( "Web Servers Worker file created" )
				.exists( ) ;

		assertThat( contentOf( getHttpdConfig( ).getHttpdWorkersFile( ) ) )
				.contains(
						"worker.ServletSample_8041csap-dev01.port=8042" ) ;

		assertThat( contentOf( getHttpdConfig( ).getHttpdWorkersFile( ) ) )
				.as( "SpringBoot is excluded unless it has metadata tag" )
				.contains( "worker.ServletSample_8041" )
				.doesNotContain( "worker.SpringBootRest" ) ;

		assertThat( contentOf( getHttpdConfig( ).getHttpdWorkersFile( ) ) )
				.as( "SpringBoot will not generate worker._LB" )
				.doesNotContain( "worker._LB" ) ;

		assertThat( getHttpdConfig( ).getHttpdModjkFile( ) )
				.as( "Web Servers ModJK file created" )
				.exists( ) ;

		assertThat( contentOf( getHttpdConfig( ).getHttpdModjkFile( ) ) )
				.contains( "springmvc-showcase",
						"SampleJvmInA" ) ;

	}

	/**
	 *
	 * Application has split personality: Node Manager and Node Agent
	 *
	 * Node Manager mode will not change active lifecycle
	 *
	 * @throws JsonProcessingException
	 * @throws IOException
	 */
	@Disabled
	@Test
	public void load_application_using_manager_mode ( )
		throws JsonProcessingException ,
		IOException {

		File csapApplicationDefinition = new File(
				getClass( ).getResource( Agent_context_loaded.TEST_DEFINITIONS + "clusterConfigManager.json" )
						.getPath( ) ) ;

		// Configure the manager...
		csapApplication.setJvmInManagerMode( true ) ;
		HostStatusManager testStatus = new HostStatusManager( "CsAgent_Host_Response.json", csapApplication ) ;

		csapApplication.setHostStatusManager( testStatus ) ;

		assertThat( csapApplication.loadDefinitionForJunits( false, csapApplicationDefinition ) )
				.as( "No Errors or warnings" )
				.isTrue( ) ;

		assertThat( csapApplication.getName( ) )
				.as( "Capability Name parsed" )
				.isEqualTo( "Desktop Dev2" ) ;

		assertThat( csapApplication.getModelForHost( "sampleHostA-dev01" ).getName( ) )
				.as( "Release Packag for host" )
				.isEqualTo(
						"Supporting Sample A" ) ;

		assertThat( csapApplication.serviceNameToAllInstances( ).get( CsapCore.AGENT_NAME ) )
				.as( "CsAgents instances found" )
				.hasSize( 9 ) ;

		assertThat( csapApplication.getLifecycleList( ) )
				.as( "Lifecycles found" )
				.hasSize( 8 )
				.contains( "dev",
						"dev-WebServer",
						"dev-csspLocal",
						"dev-cssp",
						"dev-middleware",
						"stage",
						"stage-cssp",
						"stage-factory" ) ;

		assertThat( csapApplication.getProject( "Supporting Sample A" ).getEnvironmentAndClusterNames( ) )
				.as( "Lifecycles for model" )
				.hasSize( 4 )
				.contains( "dev", "dev-middlewareA", "dev-middlewareA2", "stage" ) ;

		assertThat( csapApplication.getProject( Application.ALL_PACKAGES )
				.getServiceToAllInstancesMap( ).keySet( ) )
						.as( "All service instanaces in all models" )
						.hasSize( 18 )
						.contains( "SpringBootRest",
								CsapCore.AGENT_NAME, "CsspSample",
								"Factory2Sample",
								"FactorySample",
								"SampleDataLoader",
								"SampleJvmInA",
								"SampleJvmInA2",
								"SampleJvmInB" ) ;

		assertThat( csapApplication.getProject( Application.ALL_PACKAGES ).getHostToServicesMap( ).keySet( ) )
				.as( "All Hosts found" )
				.hasSize( 12 )
				.contains(
						"SampleHostB-dev03",
						"csap-dev01",
						"csap-dev02",
						"csapdb-dev01",
						"localhost",
						"peter-dev01",
						"sampleHostA-dev01",
						"sampleHostA2-dev02",
						"xcssp-qa01",
						"xcssp-qa02" ) ;

	}

	/**
	 *
	 * Verify presence of lifecycle meta data
	 *
	 * @throws JsonProcessingException
	 * @throws IOException
	 */
	@Test
	public void load_application_and_verify_lifecycle_settings ( )
		throws JsonProcessingException ,
		IOException {

		File csapApplicationDefinition = new File(
				getClass( ).getResource( Agent_context_loaded.TEST_DEFINITIONS
						+ "sub-project-release-file/clusterConfigManager.json" )
						.getPath( ) ) ;

		assertThat( csapApplication.loadDefinitionForJunits( false, csapApplicationDefinition ) )
				.as( "No Errors or warnings" )
				.isTrue( ) ;

		assertThat( csapApplication.getName( ) )
				.as( "Capability Name parsed" )
				.isEqualTo( "Desktop Dev2" ) ;

		EnvironmentSettings lifeSettings = csapApplication.rootProjectEnvSettings( ) ;

		assertThat( lifeSettings.getNumberWorkerThreads( ) )
				.as( "Worker Thread Count" )
				.isEqualTo( 4 ) ;

		assertThat( lifeSettings.getNewsJsonArray( ) )
				.as( "News Items" )
				.hasSize( 1 ) ;

		assertThat( lifeSettings.isMetricsPublication( ) )
				.as( "metricsPublication enabled" )
				.isTrue( ) ;

		assertThat( lifeSettings.getMaxHostCpuLoad( "localhost" ) )
				.as( "Max Host Cpu Load" )
				.isEqualTo( 2 ) ;

		assertThat( lifeSettings.getMaxHostCpu( "localhost" ) )
				.as( "Max Host Cpu " )
				.isEqualTo( 80 ) ;

		assertThat( lifeSettings.getMaxHostCpuIoWait( "localhost" ) )
				.as( "Max Host CPU IO Wait " )
				.isEqualTo( 11 ) ;

		assertThat( lifeSettings.getMetricsPublicationNode( ) )
				.as( "Publication Endpoints" )
				.hasSize( 2 ) ;

		assertThat( lifeSettings.getAutoStopServiceThreshold( "localhost" ) )
				.as( "autoStopServiceThreshold" )
				.isEqualTo( 1.2 ) ;

		// assertThat( lifeCycleConfig.isJmxHeatbeat( "localhost" ) )
		// .as( "Jmx Heartbeat enabled localhost" )
		// .isTrue() ;
		//
		// assertThat( lifeCycleConfig.isJmxHeatbeat( "csapdb-dev01" ) )
		// .as( "Jmx Heartbeat enabled csapdb-dev01" )
		// .isFalse() ;

	}

	@Test
	public void load_application_with_warnings ( )
		throws JsonProcessingException ,
		IOException {

		File csapApplicationDefinition = new File(
				getClass( ).getResource( Agent_context_loaded.TEST_DEFINITIONS + "application_with_warnings.json" )
						.getPath( ) ) ;

		assertThat( csapApplication.loadDefinitionForJunits( false, csapApplicationDefinition ) )
				.as( "No Errors or warnings" )
				.isFalse( ) ;

		logger.info( "Definition results: \n {}", csapApplication.getLastTestParseResults( ).toString( ) ) ;

	}

	/**
	 *
	 * Scenario: - load a config file with multiple services using multiVmPartition
	 * cluster type g, some standard - httpd instance has generateWorkerProperties
	 * metadata in cluster.js - verify that mount points get generated
	 *
	 */
	@Test
	public void load_application_with_multiple_host_partition ( )
		throws JsonProcessingException ,
		IOException {

		File csapApplicationDefinition = new File(
				getClass( ).getResource( "application-multi-host-partition.json" ).getPath( ) ) ;

		assertThat( csapApplication.loadDefinitionForJunits( false, csapApplicationDefinition ) )
				.as( "No Errors or warnings" )
				.isTrue( ) ;

		assertThat( csapApplication.getName( ) )
				.as( "Capability Name parsed" )
				.isEqualTo( "ParitionExample" ) ;

		assertThat( csapApplication.getLifecycleList( ) )
				.as( "Lifecycles found" )
				.hasSize( 9 )
				.containsExactly( "defaults", "dev",
						"dev:WebServer",
						"dev:csspLocal",
						"dev:cssp",
						"dev:middleware",
						"stage",
						"stage:cssp",
						"stage:factory" ) ;

		assertThat( csapApplication.serviceNameToAllInstances( ).get( CsapCore.AGENT_NAME ) )
				.as( "CsAgent Instances found" )
				.hasSize( 8 ) ;

		assertThat( csapApplication.getRootProject( ).getHostToServicesMap( ).keySet( ) )
				.as( "All Hosts found" )
				.hasSize( 8 )
				.contains( "csap-dev01",
						"csap-dev02",
						"csapdb-dev01",
						"localhost",
						"peterDummyVmA",
						"xcssp-qa01",
						"xcssp-qa02",
						"xfactory-qa01" ) ;

		assertThat( csapApplication.getAllHostsInAllPackagesInCurrentLifecycle( ) )
				.as( "All Hosts in current lifecycle" )
				.hasSize( 5 ) ;

		assertThat( csapApplication.getLifecycleList( ) )
				.as( "Lifecycles found" )
				.hasSize( 9 )
				.contains( "dev",
						"dev:WebServer",
						"dev:csspLocal",
						"dev:cssp",
						"dev:middleware",
						"stage",
						"stage:cssp",
						"stage:factory" ) ;

		assertThat( csapApplication.getServiceInstanceAnyPackage( CsapCore.AGENT_ID ).getOsProcessPriority( ) )
				.as( "CsAgent OS Priority" )
				.isEqualTo(
						-10 ) ;

		assertThat( getHttpdConfig( ).getHttpdWorkersFile( ) )
				.as( "Web Servers Worker file created" )
				.exists( ) ;

		assertThat( contentOf( getHttpdConfig( ).getHttpdWorkersFile( ) ) )
				.as( "Ensure filtered VMs are not in web server config files" )
				.contains( "worker.FactorySample-dev01_LB" )
				.contains(
						"worker.Factory2Sample-dev01_LB.balance_workers=Factory2Sample-dev01_LB0" ) ;

		logger.info( "multivm clusters eol, {}", ClusterType.MULTI_SHARED_NOTHING ) ;
		// assertThat( contentOf( getHttpdConfig().getHttpdWorkersFile() ) )
		// .as( "Ensure filtered VMs are not in web server config files" )
		// .contains( "worker.FactorySample-dev01_LB" )
		// .contains( "worker.AuditService-1_LB" )
		// .contains( "worker.AuditService-2_LB" )
		// .contains(
		// "worker.AuditService-1_LB.balance_workers=AuditService-1_8191localhost" );

		assertThat( getHttpdConfig( ).getHttpdModjkFile( ) )
				.as( "Web Servers ModJK file created" )
				.exists( ) ;

		assertThat( contentOf( getHttpdConfig( ).getHttpdModjkFile( ) ) )
				.contains( "Factory2Sample-dev01" ) ;

		// assertEquals(
		// "Worker properties should contain only 1 StringUtils", 1,
		// StringUtils.countMatches(workerContents,
		// "worker.AuditService-1_LB.type"));
		//
	}

	private HttpdIntegration getHttpdConfig ( ) {

		return csapApplication.getHttpdIntegration( ) ;

	}
}
