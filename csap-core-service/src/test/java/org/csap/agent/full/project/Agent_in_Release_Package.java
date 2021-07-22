package org.csap.agent.full.project ;

import static org.assertj.core.api.Assertions.assertThat ;

import java.io.File ;
import java.io.IOException ;
import java.util.ArrayList ;
import java.util.Arrays ;
import java.util.List ;
import java.util.stream.Collectors ;

import javax.inject.Inject ;

import org.apache.commons.io.FileUtils ;
import org.csap.agent.CsapBareTest ;
import org.csap.agent.CsapCore ;
import org.csap.agent.integrations.HttpdIntegration ;
import org.csap.agent.model.Application ;
import org.csap.agent.model.ServiceAlertsEnum ;
import org.csap.helpers.CsapApplication ;
import org.junit.jupiter.api.BeforeAll ;
import org.junit.jupiter.api.BeforeEach ;
import org.junit.jupiter.api.Tag ;
import org.junit.jupiter.api.Test ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;

import com.fasterxml.jackson.core.JsonProcessingException ;
import com.fasterxml.jackson.databind.node.ObjectNode ;

@Tag ( "full" )
@CsapBareTest.Agent_Full
public class Agent_in_Release_Package {

	Logger logger = LoggerFactory.getLogger( getClass( ) ) ;

	static {

		CsapApplication.initialize( "" ) ;

	}

	String definitionPath = "/definitions/simple-packages/main-project.json" ;
	File csapApplicationDefinition = new File( getClass( ).getResource( definitionPath ).getPath( ) ) ;

	@BeforeAll
	void beforeAll ( )
		throws Exception {

		csapApp.setAutoReload( false ) ;

		assertThat( csapApp.loadCleanForJunits( false, csapApplicationDefinition ) )
				.as( "No Errors or warnings" )
				.isTrue( ) ;

	}

	@BeforeEach
	void beforeEach ( )
		throws Exception {

		logger.info( "Deleting: {}", getHttpdConfig( ).getHttpdWorkersFile( ).getParentFile( ).getAbsolutePath( ) ) ;
		FileUtils.deleteQuietly( getHttpdConfig( ).getHttpdWorkersFile( ).getParentFile( ) ) ;

	}

	@Inject
	Application csapApp ;

	@Test
	void verify_alert_thresholds_for_service ( )
		throws JsonProcessingException ,
		IOException {

		logger.info( CsapApplication.testHeader( ) ) ;

		String targetService = "SampleJvmInA" ;
		ObjectNode limitsJson = ServiceAlertsEnum.getAdminUiLimits(
				csapApp.findFirstServiceInstanceInLifecycle( targetService ),
				csapApp.rootProjectEnvSettings( ) ) ;

		logger.info( "{} serviceLimits: {}", targetService, limitsJson ) ;

		assertThat( limitsJson.get( ServiceAlertsEnum.threads.value ).asInt( ) )
				.as( "Service thread limit" )
				.isEqualTo( 100 ) ;

		assertThat( limitsJson.get( ServiceAlertsEnum.httpConnections.value ).asInt( ) )
				.as( "Service Tomcat limit" )
				.isEqualTo( 40 ) ;

		assertThat( limitsJson.get( ServiceAlertsEnum.diskWriteRate.value ).asInt( ) )
				.as( "Cluster Disk default limit" )
				.isEqualTo( 15 ) ;

	}

	@Test
	public void verify_alert_thresholds_with_cluster_override ( )
		throws JsonProcessingException ,
		IOException {

		logger.info( CsapApplication.testHeader( ) ) ;

		var postgresInstance = csapApp.findFirstServiceInstanceInLifecycle( "postgresLocal" ) ;

		ObjectNode limitsJson = ServiceAlertsEnum.getAdminUiLimits(
				postgresInstance,
				csapApp.rootProjectEnvSettings( ) ) ;

		logger.info( "{} serviceLimits: {}", postgresInstance.toSummaryString( ), limitsJson ) ;

		assertThat( limitsJson.get( ServiceAlertsEnum.threads.value ).asInt( ) )
				.as( "Service thread limit" )
				.isEqualTo( 55 ) ; // 55

		assertThat( limitsJson.get( ServiceAlertsEnum.httpConnections.value ).asInt( ) )
				.as( "Service Tomcat limit" )
				.isEqualTo( 40 ) ;

		assertThat( limitsJson.get( ServiceAlertsEnum.diskWriteRate.value ).asInt( ) )
				.as( "Cluster Disk write default limit" )
				.isEqualTo( 15 ) ;

		assertThat( limitsJson.get( ServiceAlertsEnum.diskSpace.value ).asInt( ) )
				.as( "Cluster Disk space default limit" )
				.isEqualTo( 15360 ) ; // 15360

	}

	@Test
	public void core_settings_in_release_package ( )
		throws JsonProcessingException ,
		IOException {

		logger.info( CsapApplication.testHeader( ) ) ;

		assertThat( csapApp.getName( ) )
				.as( "Capability Name parsed" )
				.isEqualTo( "Junit Simple Packages" ) ;

		assertThat( csapApp.getActiveProjectName( ) )
				.as( "Active model name" )
				.isEqualTo( "Supporting Sample A" ) ;

		assertThat(
				csapApp.getRootProject( )
						.getReleasePackageNames( )
						.collect( Collectors.toList( ) ) )
								.as( "Release Package names" )
								.containsExactly( "SampleDefaultPackage",
										"Supporting Sample A",
										"Supporting Sample B" ) ;

		assertThat( csapApp.getModelForHost( "sampleHostA-dev01" ).getName( ) )
				.as( "Release Packag for host" )
				.isEqualTo(
						"Supporting Sample A" ) ;

		assertThat(
				csapApp.serviceNameToAllInstances( ).get( CsapCore.AGENT_NAME ) )
						.as( "CsAgents instances found" )
						.hasSize( 3 ) ;

		assertThat(
				csapApp.getActiveProject( ).getServiceInstances( CsapCore.AGENT_NAME ).count( ) )
						.as( "CsAgents instances in active model and lifecycle" )
						.isEqualTo( 3 ) ;

		assertThat(
				csapApp.getMutableHostsInActivePackage( csapApp.activeLifecycleClusterName( ) ) )
						.as( "All Hosts in Current Lifecycle" )
						.hasSize( 3 )
						.contains(
								"localhost",
								"middlewareA2Host-dev98" ) ;

		logger.info( "getLifecycleToClusterMap: {}", csapApp.getActiveProject( ).getLifecycleToClusterMap( ) ) ;
		assertThat(
				csapApp.getActiveProject( ).getLifecycleToClusterMap( ) )
						.as( "Active model Lifecycle to Groups" )
						.hasSize( 2 )
						.containsKeys( "dev" )
						.containsEntry( "dev",
								new ArrayList<String>(
										Arrays.asList(
												"kubernetes-provider",
												"partition-cluster-A1",
												"partition-cluster-A4",
												"partition-cluster-A2",
												"partition-cluster-A3",
												"kubernetes-cluster",
												"namespace-monitors" ) ) ) ;

		assertThat( csapApp.getActiveProject( ).getName( ) )
				.as( "Release Packag for active model" )
				.isEqualTo( "Supporting Sample A" ) ;

		assertThat( csapApp.getProject( Application.ALL_PACKAGES ).getLifecycleToClusterMap( ).get( "dev" ) )
				.as( "Groups for dev" )
				.hasSize(
						9 )
				.contains(
						"simple-cluster",
						"kubernetes-provider",
						"partition-cluster-A1",
						"partition-cluster-A4",
						"partition-cluster-A2",
						"partition-cluster-A3",
						"kubernetes-cluster",
						"namespace-monitors",
						"partition-cluster-B" ) ;

		assertThat(
				csapApp.getServiceInstanceCurrentHost( CsapCore.AGENT_ID ).getMavenRepo( ) )
						.as( "CsAgent Maven Repo" )
						.isEqualTo(
								"https://repo.maven.apache.org/maven2/" ) ;

	}

	@Test
	void agent_in_release_package_inherits_os_definitions ( )
		throws JsonProcessingException ,
		IOException {

		logger.info( CsapApplication.testHeader( ) ) ;

		// no docker definition in release package - it is inherited from parent
		logger.info( "All docker instances: {}",
				csapApp.serviceNameToAllInstances( ).get( "docker" ) ) ;

		assertThat(
				csapApp.serviceNameToAllInstances( ).get( "docker" ) )
						.as( "docker instances found" )
						.hasSize( 1 ) ;

		assertThat(
				csapApp.getServiceInstanceCurrentHost( "docker_4243" ).getHostName( ) )
						.as( "docker instances found" )
						.isEqualTo( "localhost" ) ;

	}

	@Test
	void agent_in_release_package ( )
		throws JsonProcessingException ,
		IOException {

		logger.info( CsapApplication.testHeader( ) ) ;

		assertThat(
				csapApp.serviceNameToAllInstances( ).get( "docker" ) )
						.as( "docker instances found" )
						.hasSize( 1 ) ;

		assertThat(
				csapApp.getServiceInstanceCurrentHost( "SampleJvmInA_8041" ).getName( ) )
						.as( "Sample service loaded" )
						.isEqualTo(
								"SampleJvmInA" ) ;

		assertThat(
				csapApp.getRootProject( ).getAllPackagesModel( )
						.serviceInstancesInCurrentLifeByName( ).get( "CsspSample" ) )
								.as( "CsspSample count in all packages" )
								.hasSize( 3 ) ;

		assertThat(
				csapApp.getRootProject( ).getAllPackagesModel( )
						.serviceInstancesInCurrentLifeByName( ).get( "docker" ) )
								.as( "Service count in all packages" )
								.hasSize( 4 ) ;

		// resolve all instances in all packages.
		List<String> dockerHosts = csapApp
				.getRootProject( ).getAllPackagesModel( )
				.getServiceInstances( "docker" )
				.map( serviceInstance -> serviceInstance.getHostName( ) )
				.collect( Collectors.toList( ) ) ;

		assertThat( dockerHosts )
				.as( "Service count in all packages" )
				.hasSize( 4 )
				.contains( "mainHostA", "mainHostB", "mainHostC" ) ;

		assertThat(
				csapApp.getRootProject( ).getLifecycleToClusterMap( ) )
						.as( "Lifecycles to group in root model" )
						.hasSize( 2 )
						.containsKey( "dev" ) ;

		assertThat(
				csapApp.getRootProject( ).getServiceToAllInstancesMap( ).keySet( ) )
						.as( "Services in root model" )
						.hasSize( 9 )
						.contains( CsapCore.AGENT_NAME,
								"postgres",
								"CsspSample",
								"docker",
								"FactorySample",
								"ServletSample",
								"httpd",
								"springmvc-showcase" ) ;

		assertThat( csapApp.getProject( Application.ALL_PACKAGES )
				.getHostsForEnvironment( csapApp.getCsapHostEnvironmentName( ) ) )
						.as( "Hosts in current lifecycle" )
						.hasSize(
								7 )
						.contains(
								"mainHostA",
								"localhost",
								"middlewareA2Host-dev98",
								"SampleHostB-dev99" ) ;

		assertThat( getHttpdConfig( ).getHttpdWorkersFile( ) )
				.as( "Ensure web server files are not created as they are not in active model" )
				.doesNotExist( ) ;

	}

	private HttpdIntegration getHttpdConfig ( ) {

		return csapApp.getHttpdIntegration( ) ;

	}

}
