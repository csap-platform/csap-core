/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.csap.agent.project.loader ;

import static org.assertj.core.api.Assertions.assertThat ;

import java.io.File ;
import java.util.List ;
import java.util.stream.Collectors ;

import org.csap.agent.Agent_context_loaded ;
import org.csap.agent.CsapBareTest ;
import org.csap.agent.CsapCore ;
import org.csap.agent.linux.HostStatusManager ;
import org.csap.agent.model.Application ;
import org.csap.agent.model.EnvironmentSettings ;
import org.csap.agent.model.Project ;
import org.csap.agent.model.ServiceAttributes ;
import org.csap.agent.model.ServiceInstance ;
import org.csap.helpers.CsapApplication ;
import org.junit.jupiter.api.BeforeAll ;
import org.junit.jupiter.api.DisplayName ;
import org.junit.jupiter.api.Test ;
import org.junit.jupiter.api.TestInstance ;

/**
 *
 * @author someDeveloper
 */

@TestInstance ( TestInstance.Lifecycle.PER_CLASS )
@DisplayName ( "Model Validation: running as admin" )

public class Model_As_Manager extends CsapBareTest {

	String SUPPORTING_SAMPLE_A = "Supporting Sample A" ;

	@BeforeAll
	void beforeAll ( )
		throws Exception {

		CsapApplication.initialize( getClass( ).getSimpleName( ) ) ;

		File csapApplicationDefinition = new File(
				Model_As_Manager.class
						.getResource( Agent_context_loaded.TEST_DEFINITIONS
								+ "sub-project-release-file/clusterConfigManager.json" )
						.getPath( ) ) ;

		getApplication( ).setJvmInManagerMode( true ) ;
		HostStatusManager testStatus = new HostStatusManager( "CsAgent_Host_Response.json", getApplication( ) ) ;
		getApplication( ).setHostStatusManager( testStatus ) ;

		assertThat( getApplication( ).loadCleanForJunits( false, csapApplicationDefinition ) )
				.as( "No Errors or warnings" )
				.isTrue( ) ;

		logger.info( CsapApplication.header( "loaded: {} \n\n\n {}" ), csapApplicationDefinition.getAbsolutePath( ),
				getApplication( ).getLastTestParseResults( ).toString( ) ) ;

	}

	@Test
	@DisplayName ( "verify streaming packages" )
	void verify_streaming_packages ( ) {

		logger.info( CsapApplication.testHeader( ) ) ;

		var notes = getApplication( ).getReleasePackageStream( )
				.filter( releasePackage -> releasePackage.getHostsForEnvironment( getApplication( )
						.getCsapHostEnvironmentName( ) ) != null )
				// .map( ReleasePackage::getLifeCycleToHostMap )
				.map( Object::toString )
				.collect( Collectors.joining( "\n\n" ) ) ;

		logger.info( "notes: {}", notes ) ;

	}

	@Test
	@DisplayName ( "verify kubernetes services loaded" )
	public void verify_kubernetes_services ( ) {

		logger.info( CsapApplication.testHeader( ) ) ;

		List<String> kubernetesServices = getApplication( )
				.getActiveProject( ).getAllPackagesModel( )
				.getServiceConfigStreamInCurrentLC( )
				.flatMap( serviceInstancesEntry -> serviceInstancesEntry.getValue( ).stream( ) )
				.filter( ServiceInstance::is_cluster_kubernetes )
				.map( ServiceInstance::getName )
				.distinct( )
				.collect( Collectors.toList( ) ) ;

		logger.info( "kubernetesServices: {}", kubernetesServices ) ;

		assertThat(
				getApplication( ).getProject( SUPPORTING_SAMPLE_A ).findAndCloneServiceDefinition( "docker_0" ) ) ;

	}

	@Test
	public void verify_application_packages_load_with_no_errors ( ) {

		logger.info( CsapApplication.testHeader( ) ) ;

		assertThat( getApplication( ).getName( ) )
				.as( "Capability Name parsed" )
				.isEqualTo( "Desktop Dev2" ) ;

		assertThat( getApplication( ).getModelForHost( "sampleHostA-dev01" ).getName( ) )
				.as( "Release Packag for host" )
				.isEqualTo(
						SUPPORTING_SAMPLE_A ) ;

		List<String> fileNames = getApplication( ).getPackageModels( ).stream( )
				.filter( model -> ! model.getName( ).equals( Project.GLOBAL_PACKAGE ) )
				.map( Project::getSourceFileName )
				.collect( Collectors.toList( ) ) ;

		assertThat( fileNames )
				.as( "Files loaded" )
				.contains( "clusterConfigManager.json",
						"clusterConfigManagerA.json",
						"clusterConfigManagerB.json" ) ;

		assertThat( getApplication( ).serviceNameToAllInstances( ).get( CsapCore.AGENT_NAME ) )
				.as( "CsAgents instances found" )
				.hasSize( 7 ) ;

		assertThat( getApplication( ).getLifecycleList( ) )
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

		assertThat( getApplication( ).getProject( SUPPORTING_SAMPLE_A ).getEnvironmentAndClusterNames( ) )
				.as( "Lifecycles for model" )
				.hasSize( 5 )
				.contains( "dev", "dev:middlewareA", "dev:middlewareA2", "stage" ) ;

		assertThat( getApplication( ).getProject( Application.ALL_PACKAGES )
				.getServiceToAllInstancesMap( ).keySet( ) )
						.as( "All service instanaces in all models" )
						.hasSize( 19 )
						.contains( "SpringBootRest",
								CsapCore.AGENT_NAME, "CsspSample",
								"Factory2Sample",
								"FactorySample",
								"SampleDataLoader",
								"SampleJvmInA",
								"SampleJvmInA2",
								"SampleJvmInB" ) ;

		assertThat( getApplication( ).getProject( Application.ALL_PACKAGES ).getHostToServicesMap( ).keySet( ) )
				.as( "All Hosts found" )
				.hasSize( 10 )
				.contains(
						"SampleHostB-dev03",
						"csap-dev01",
						"csap-dev02",
						"csapdb-dev01",
						"localhost",
						"sampleHostA-dev01",
						"sampleHostA2-dev02",
						"xcssp-qa01",
						"xcssp-qa02" ) ;

		List<ServiceInstance> services = getApplication( ).getProject( Application.ALL_PACKAGES )
				.getServiceToAllInstancesMap( )
				.get( CsapCore.AGENT_NAME ) ;
		// logger.info( "services: {}" , services);
		assertThat( services )
				.as( "All Hosts found" )
				.hasSize( 10 ) ;

		assertThat( getApplication( ).getMaxDeploySecondsForService( CsapCore.AGENT_NAME ) )
				.as( "Maximum Deploy for Agent" )
				.isEqualTo( 300 ) ;

		assertThat( getApplication( ).getMaxDeploySecondsForService( "Factory2Sample" ) )
				.as( "Maximum Deploy for Factory2Sample" )
				.isEqualTo( 900 ) ;

	}

	@Test
	public void verify_root_package_settings ( ) {

		assertThat( getApplication( ).rootProjectEnvSettings( ).getConfigurationMap(
				EnvironmentSettings.GLOBAL_CONFIG_MAP_NAME )
				.path( "DEMO_VARIABLE" ).asText( ) )
						.as( "UI settings" )
						.isEqualTo( "my-demo-value-in-root-package" ) ;

	}

	@Test
	public void verify_release_package_settings ( ) {

		EnvironmentSettings settings = getApplication( )
				.getProject( SUPPORTING_SAMPLE_A )
				.getEnvironmentNameToSettings( )
				.get( "dev" ) ;

		logger.info( "Settings map: {}", settings ) ;

		assertThat( settings.getUiDefaultView( ) )
				.as( "UI settings" )
				.isEqualTo( "middlewareA" ) ;

		assertThat( settings.getConfigurationMap( EnvironmentSettings.GLOBAL_CONFIG_MAP_NAME ).path( "DEMO_VARIABLE" )
				.asText( ) )
						.as( "UI settings" )
						.isEqualTo( "my-demo-value" ) ;

		assertThat( settings.getRealTimeMeters( ).at( "/0/label" ).asText( ) )
				.as( "UI settings" )
				.isEqualTo( "Cpu Cores Active" ) ;

	}

	@Test
	public void verify_os_packages_inherited_by_release_packages ( ) {

		logger.info( "All: {}",
				getApplication( )
						.getProject( SUPPORTING_SAMPLE_A )
						.getServiceToAllInstancesMap( ) ) ;

		logger.info( "docker: {}",
				getApplication( )
						.getProject( SUPPORTING_SAMPLE_A )
						.findHostsForService( "docker_0" ) ) ;
		assertThat(
				getApplication( ).getProject( SUPPORTING_SAMPLE_A )
						.findAndCloneServiceDefinition( "docker_0" ) ) ;

	}

	@Test
	public void verify_agent_attributes ( ) {

		logger.info( CsapApplication.testHeader( ) ) ;
		ServiceInstance agentInstance = getApplication( ).serviceNameToAllInstances( ).get( CsapCore.AGENT_NAME ).get(
				0 ) ;

		assertThat( agentInstance.is_springboot_server( ) )
				.as( "server override" )
				.isTrue( ) ;

		assertThat( agentInstance.getProcessFilter( ) )
				.as( "process filter" )
				.isEqualTo( ".*java.*csapProcessId=" + CsapCore.AGENT_NAME + ".*" ) ;

		assertThat( agentInstance.getOsProcessPriority( ) )
				.as( "os priority" )
				.isEqualTo( -99 ) ;

		assertThat( agentInstance.getAttribute( ServiceAttributes.parameters ) )
				.as( "parameters" )
				.contains( "-Doverride=true" ) ;

		assertThat( agentInstance.getMavenId( ) )
				.as( "maven override" )
				.contains( "9.9.9" ) ;

		assertThat( agentInstance.getScm( ) )
				.as( "scm type" )
				.isEqualTo( "svn" ) ;

		assertThat( agentInstance.getPerformanceConfiguration( ).at( "/SpringMvcRequests/mbean" ).asText( ) )
				.as( "performance mbean" )
				.isEqualTo(
						"Tomcat:j2eeType=Servlet,WebModule=__CONTEXT__,name=dispatcherServlet,J2EEApplication=none,J2EEServer=none" ) ;

	}

	@Test
	public void verify_release_files_found ( ) {

		logger.info( CsapApplication.testHeader( ) ) ;
		assertThat( getApplication( ).getLastTestParseResults( ).toString( ) )
				.as( "release files found" )
				.doesNotContain( "No release files found" )
				.contains( "clusterConfigManagerA-release" ) ;

		var sampleProjectService = getApplication( ).findFirstServiceInstanceInLifecycle( "SampleJvmInA" ) ;
		// getSvcToConfigMap().get( "SampleJvmInA" ).get( 0 );

		assertThat( sampleProjectService.getMavenId( ) )
				.as( "mavenId" )
				.isEqualTo( "org.demo:TestForVersionReleaseFile:9.9.9-SNAPSHOT:jar" ) ;

		var dockerService = getApplication( ).findFirstServiceInstanceInLifecycle( "csap-demo-nginx" ) ;
		assertThat( dockerService.getDockerImageName( ) )
				.as( "dockerImage" )
				.isEqualTo( "nginx:1.16.999" ) ;

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
				.isEqualTo( "httpd_8080" ) ;

		assertThat( httpdInstance.getMonitors( ) )
				.as( "disk limit" )
				.isNull( ) ;

		assertThat( httpdInstance.getPerformanceConfiguration( ) )
				.as( "colleciton url" )
				.isNull( ) ;

		assertThat( httpdInstance.getLifeEnvironmentVariables( ).fieldNames( ).hasNext( ) )
				.as( "environmentVariabls" )
				.isFalse( ) ;

		assertThat( httpdInstance.getDefaultLogToShow( ) )
				.as( "log to show" )
				.isEqualTo( "catalina.out" ) ;

		assertThat( httpdInstance.getPropDirectory( ) )
				.as( "propery folder" )
				.isEqualTo( "/opt/csap/staging/httpdConf" ) ;

	}

}
