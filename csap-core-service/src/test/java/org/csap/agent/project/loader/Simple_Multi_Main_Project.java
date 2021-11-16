/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.csap.agent.project.loader ;

import static java.util.Comparator.comparing ;
import static org.assertj.core.api.Assertions.assertThat ;
import static org.assertj.core.api.Assertions.contentOf ;

import java.io.File ;
import java.util.stream.Collectors ;

import org.csap.agent.CsapBareTest ;
import org.csap.agent.CsapCore ;
import org.csap.agent.container.DockerJson ;
import org.csap.agent.linux.ServiceJobRunner ;
import org.csap.agent.model.Application ;
import org.csap.agent.model.ClusterType ;
import org.csap.agent.model.EnvironmentSettings ;
import org.csap.agent.model.ProjectLoader ;
import org.csap.agent.model.ProjectOperators ;
import org.csap.agent.model.ServiceAlertsEnum ;
import org.csap.agent.model.ServiceAttributes ;
import org.csap.agent.model.ServiceInstance ;
import org.csap.helpers.CSAP ;
import org.csap.helpers.CsapApplication ;
import org.junit.jupiter.api.BeforeAll ;
import org.junit.jupiter.api.Test ;

import com.fasterxml.jackson.databind.node.ObjectNode ;

/**
 *
 * @author someDeveloper
 */

public class Simple_Multi_Main_Project extends CsapBareTest {

	String definitionBase = "/definitions/simple-multi-project" ;
	File definitionFolder = new File( getClass( ).getResource( definitionBase ).getPath( ) ) ;

	// File csapApplicationDefinition = new File( definitionFolder,
	// "root-project.json" ) ;
	File csapApplicationDefinition = new File( definitionFolder, "root-project.json" ) ;
	File complexAutoPlayFile = new File( definitionFolder, "complex-auto-play.yaml" ) ;

	ServiceInstance simpleService ;

	@BeforeAll
	void beforeAll ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( "loading: {}" ), csapApplicationDefinition.getAbsolutePath( ) ) ;

		CSAP.setLogToInfo( ProjectLoader.class.getName( ) ) ;
		assertThat( getApplication( ).loadDefinitionForJunits( false, csapApplicationDefinition ) )
				.as( "No Errors or warnings" )
				.isTrue( ) ;
		CSAP.setLogToInfo( ProjectLoader.class.getName( ) ) ;

		simpleService = getApplication( ).flexFindFirstInstanceCurrentHost( "simple-service" ) ;
		assertThat( simpleService ).isNotNull( ) ;

	}

	@Test
	void main_project_loaded ( ) {

		logger.info( CsapApplication.testHeader( ) ) ;

		var clusters = getApplication( ).getClustersInLifecycle( getApplication( ).getActiveProjectName( ) ) ;
		logger.info( "clusters: {}", clusters ) ;

		assertThat( clusters ).contains( "dev:test-cluster2" ) ;

		logger.info( "cluster type map: {}", getApplication( ).getRootProject( ).getLifeClusterToTypeMap( ) ) ;

		assertThat( getApplication( ).getRootProject( ).getLifeClusterToTypeMap( ).get( "dev:test-cluster2" ) )
				.isEqualTo( ClusterType.SIMPLE.getJson( ) ) ;

		var verifyService = getApplication( ).findServiceByNameOnCurrentHost( "csap-verify-service" ) ;
		assertThat( verifyService ).isNotNull( ) ;
		assertThat( verifyService.getPort( ) ).isEqualTo( "7011" ) ;

	}

	@Test
	void csap_event_settings ( ) {

		logger.info( CsapApplication.testHeader( ) ) ;

		var eventUrl = getActiveProjectSettings( ).getEventApiUrl( ) ;
		logger.info( "eventUrl: {}", eventUrl ) ;

		assertThat( eventUrl ).isEqualTo( "http://event-dev-url/api/event" ) ;

	}

	@Test
	void cluster_default_inheritance ( ) {

		logger.info( CsapApplication.testHeader( ) ) ;

		var clusters = getApplication( ).getClustersInLifecycle( getApplication( ).getActiveProjectName( ) ) ;
		logger.info( "clusters: {}", clusters ) ;

		assertThat( clusters ).contains( "dev:test-cluster1" ) ;

	}

	@Test
	void cluster_inheritance_push_down ( ) {

		logger.info( CsapApplication.testHeader( ) ) ;

		var testDefinition = getApplication( ).getActiveProject( ).getSourceDefinition( ).deepCopy( ) ;
		var clusterToPush = "test-cluster2" ;
		var sourceEnvName = "dev" ;
		var targetEnvName = "" ;

		var results = getApplication( ).getProjectLoader( ).getProjectOperators( ).pushDown( clusterToPush,
				sourceEnvName, targetEnvName,
				testDefinition ) ;

		logger.info( "push results: {}", CSAP.jsonPrint( results ) ) ;

		var updatedTestDefinition = testDefinition.path( ProjectLoader.ENVIRONMENTS ).path( "test" ) ;
		var updatedClusterDefinition = updatedTestDefinition.path( clusterToPush ) ;
		logger.info( "testDefinition: test: {}", CSAP.jsonPrint( updatedTestDefinition ) ) ;

		assertThat( results.at( "/request/targetEnvironmentName" ).asText( ) ).isEqualTo(
				ProjectLoader.ENVIRONMENT_DEFAULTS ) ;
		assertThat( results.at( "/updates/operation" ).asText( ) ).isEqualTo( "added test-cluster2 to defaults" ) ;
		assertThat( results.at( "/updates/dev" ).asText( ) ).isEqualTo( "removed type and templates" ) ;
		assertThat( results.at( "/updates/test" ).asText( ) ).isEqualTo( "removed type and templates" ) ;
		assertThat( updatedClusterDefinition.has( ProjectLoader.CLUSTER_TEMPLATE_REFERENCES ) ).isFalse( ) ;
		assertThat( updatedClusterDefinition.path( ProjectLoader.CLUSTER_HOSTS ).path( 0 ).asText( ) ).isEqualTo(
				"stage-host-1" ) ;

	}

	@Test
	void csap_auto_play_full ( ) {

		logger.info( CsapApplication.testHeader( ) ) ;

		var modifyProject = getApplication( ).getActiveProject( ).getSourceDefinition( ).deepCopy( ) ;

		var autoplayResults = getApplication( ).getProjectLoader( ).getProjectOperators( ).processAutoPlay(
				complexAutoPlayFile,
				getApplication( ).getActiveProject( ).getSourceFile( ),
				modifyProject ) ;

		logger.info( "autoplayResults: {}", CSAP.jsonPrint( autoplayResults ) ) ;

		assertThat( autoplayResults.path( "renamed" ).asText( ) ).contains( "csap-auto-play-completed.yaml" ) ;

		//
		// target-modify
		//
		assertThat( autoplayResults.at( "/modify/environments/deleted/0" ).asText( ) ).isEqualTo( "dev" ) ;

		//
		// target-delete
		//
		assertThat( autoplayResults.at( "/delete/" + ProjectOperators.PROCESSED ).asText( ) ).contains(
				"test-delete.yaml" ) ;

		//
		// target-create
		//
		assertThat( autoplayResults.at( "/create/" + ProjectOperators.PROCESSED ).asText( ) ).contains(
				"application-company.yml" ) ;
		var generatedCompanyFile = new File( definitionFolder, "application-company.yml" ) ;

		assertThat( generatedCompanyFile ).exists( ) ;
		assertThat( contentOf( generatedCompanyFile ) )
				.contains( "rest-api-filter.token" )
				.contains( "k16ljSCT5UnF8o1fCyshcD3+VZtrWm2c" )
				.doesNotContain( "operator" )
				.doesNotContain( "target" ) ;

	}

	@Test
	void csap_auto_play_simple_autoplay ( ) {

		logger.info( CsapApplication.testHeader( ) ) ;

		assertThat( simpleService.getDockerImageName( ) )
				.isEqualTo( "nginx:1.2.3" ) ;

		assertThat( simpleService.getDockerSettings( ).path( DockerJson.network.json( ) ).path( DockerJson.network_name
				.json( ) ).asText( ) )
						.isEqualTo( "nginx-network-over" ) ;

		//
		// autoplay changes
		//

		var modifyOperations = loadAutoPlay( "simple-auto-play.yaml" ) ;
		logger.info( "modifyOperations: {}", CSAP.jsonPrint( modifyOperations ) ) ;

		assertThat( modifyOperations.at( "/service-templates/simple-service.docker.image" ).asText( ) )
				.isEqualTo( "nginx:17.17" ) ;

		var modifyProject = getApplication( ).getActiveProject( ).getSourceDefinition( ).deepCopy( ) ;

		assertThat( modifyProject.at( "/service-templates/simple-service/docker/image" ).asText( ) )
				.isEqualTo( "nginx:1.2.3" ) ;

		//
		// Update the project and verify fields updated
		//
		var modifyResults = getApplication( ).getProjectLoader( ).getProjectOperators( ).performModifyOperations(
				modifyOperations,
				modifyProject ) ;

		logger.info( "modifyResults: {} \n\n updated project: \n {}",
				CSAP.jsonPrint( modifyResults ),
				CSAP.jsonPrint( modifyProject ) ) ;

		var simpleServiceUpdated = modifyProject.at( "/service-templates/simple-service" ) ;
		var simpleServiceDockerSetting = simpleServiceUpdated.path( "docker" ) ;

		assertThat( simpleServiceDockerSetting.path( "image" ).asText( ) )
				.isEqualTo( "nginx:17.17" ) ;

		// autoplay env change
		assertThat( simpleServiceUpdated.path( ServiceAttributes.environmentOverload.json( ) ).has( "simple" ) )
				.isFalse( ) ;
		assertThat( simpleServiceUpdated.path( ServiceAttributes.environmentOverload.json( ) ).has( "xxx" ) )
				.isTrue( ) ;
		ServiceInstance newSimple = new ServiceInstance( ) ;
		newSimple.parseDefinition( "junit-package", simpleServiceUpdated, new StringBuilder( ) ) ;
		var envDef = simpleServiceUpdated.path( ServiceAttributes.environmentOverload.json( ) ).path( "xxx" ) ;
		newSimple.parseDefinition( "junit-package", envDef, new StringBuilder( ) ) ;

		assertThat( newSimple.getDockerSettings( ).path( DockerJson.network.json( ) ).path( DockerJson.network_name
				.json( ) ).asText( ) )
						.isEqualTo( "nginx-network-over" ) ;

		var testSettingsPath = "/environments/xxx/settings" ;

		assertThat( modifyProject.at( testSettingsPath + "/application" ).isMissingNode( ) )
				.isTrue( ) ;

		assertThat( modifyProject.at( testSettingsPath + "/configuration-maps/global/test1" ).asText( ) )
				.isEqualTo( "simple-overridden" ) ;

	}

	@Test
	void csap_auto_play_service_updates ( ) {

		logger.info( CsapApplication.testHeader( ) ) ;

		var modifyOperations = loadAutoPlay( complexAutoPlayFile.getName( ) ) ;
		logger.info( "modifyOperations: {}", CSAP.jsonPrint( modifyOperations ) ) ;

		assertThat( modifyOperations.at( "/service-templates/simple-service/docker.image" ).asText( ) )
				.isEqualTo( "nginx:99" ) ;

		var modifyProject = getApplication( ).getActiveProject( ).getSourceDefinition( ).deepCopy( ) ;

		assertThat( modifyProject.at( "/service-templates/simple-service/docker/image" ).asText( ) )
				.isEqualTo( "nginx:1.2.3" ) ;

		assertThat( modifyProject.at( "/service-templates/simple-service/scheduledJobs/scripts/0/description" )
				.asText( ) )
						.isEqualTo( "wait for pod startup" ) ;

		assertThat( modifyProject.at( "/service-templates/simple-service/scheduledJobs/scripts/0/frequency" )
				.asText( ) )
						.isEqualTo( "event-post-deploy" ) ;

		//
		// Update the project and verify fields updated
		//

		var modifyResults = getApplication( ).getProjectLoader( ).getProjectOperators( ).performModifyOperations(
				modifyOperations,
				modifyProject ) ;

		assertThat( modifyProject.at(
				"/service-templates/simple-service/port" ).asInt( ) )
						.isEqualTo( 9998 ) ;

		assertThat( modifyProject.at( "/service-templates/simple-service/docker/deployment-file-names/0" ).asText( ) )
				.isEqualTo( "SEARCH_FOR_RESOURCE:sample-deploy.yaml" ) ;

		assertThat( modifyProject.at( "/service-templates/simple-service/docker/image" ).asText( ) )
				.isEqualTo( "nginx:99" ) ;

		assertThat( modifyProject.at( "/service-templates/simple-service/scheduledJobs/scripts/0/description" )
				.asText( ) )
						.isEqualTo( "updated description" ) ;

		assertThat( modifyProject.at( "/service-templates/simple-service/scheduledJobs/scripts/0/frequency" )
				.asText( ) )
						.isEqualTo( "onDemand" ) ;

	}

	@Test
	void csap_auto_play_modifys ( ) {

		logger.info( CsapApplication.testHeader( ) ) ;

		var modifyOperations = loadAutoPlay( complexAutoPlayFile.getName( ) ) ;

		//
		// target-modify
		//
		logger.info( "projectModifyDoc: {}", CSAP.jsonPrint( modifyOperations ) ) ;

		assertThat( modifyOperations.path( "target" ).asText( ) ).isEqualTo( "default" ) ;
		assertThat( modifyOperations.path( "operator" ).asText( ) ).isEqualTo( "modify" ) ;

		var generatedProject = getApplication( ).getActiveProject( ).getSourceDefinition( ).deepCopy( ) ;

		var defaultSettingsPath = "/environments/defaults/settings" ;

		assertThat( getActiveProjectSettings( ).getDefinitionRepoUrl( ) ).isEqualTo( "https://path/to/your.git" ) ;

		assertThat( generatedProject.at( defaultSettingsPath + "/application/definition-repo-url" ).asText( ) )
				.isEqualTo( "https://path/to/your.git" ) ;

		//
		// Apply changes to project
		//

		var modifyResults = getApplication( ).getProjectLoader( ).getProjectOperators( ).performModifyOperations(
				modifyOperations,
				generatedProject ) ;

		logger.info( "modifyResults: {} \n\n updated project: \n {}",
				CSAP.jsonPrint( modifyResults ),
				CSAP.jsonPrint( generatedProject ) ) ;

		//
		// project settings updated
		//

		assertThat( generatedProject.at( "/project/name" ).asText( ) ).isEqualTo( "XXX Project" ) ;
		assertThat( generatedProject.at( "/project/architect" ).asText( ) ).isEqualTo( "xxx.xxx@***REMOVED***.com" ) ;

		//
		// environments - defaults
		//
		var defaultsEnv = "/environments/defaults/" ;
		assertThat( generatedProject.at( defaultsEnv + "settings/configuration-maps/global/testDefaultAutoplay" )
				.asText( ) )
						.isEqualTo( "yyyy" ) ;
		assertThat( generatedProject.at( defaultsEnv + "settings/csap-data/user" ).asText( ) )
				.isEqualTo( "xxx" ) ;

		assertThat( generatedProject.at( defaultsEnv + "base-os/template-references" ).toString( ) )
				.contains( "csap-agent" )
				.contains( "new-service-to-cluster" ) ;

		//
		// environments - newly created env
		//
		var myNewEnv = "/environments/my-new-environment/" ;

		assertThat( modifyResults.at( "/environments/deleted/0" ).asText( ) ).isEqualTo( "dev" ) ;
		assertThat( generatedProject.at( "/environments/dev" ).isMissingNode( ) ).isTrue( ) ;

		assertThat( generatedProject.at( myNewEnv + "base-os/hosts" ).toString( ) )
				.contains( "auto-1" )
				.contains( "auto-2" ) ;

		assertThat( generatedProject.at( myNewEnv + "test-cluster2/hosts" ).toString( ) )
				.contains( "auto-3" )
				.contains( "auto-4" ) ;

		assertThat( generatedProject.at( myNewEnv + "test-cluster2/masters" ).toString( ) )
				.contains( "auto-3" ) ;

		assertThat( generatedProject.at( defaultSettingsPath + "/application/name" ).asText( ) )
				.isEqualTo( "multi-default" ) ;

		assertThat( generatedProject.at( myNewEnv + "settings/application/name" ).asText( ) )
				.isEqualTo( "auto-play-demo-application" ) ;

		assertThat( generatedProject.at( myNewEnv + "settings/application/definition-repo-url" ).asText( ) )
				.isEqualTo( "https://moc-bb.***REMOVED***/bitbucket/update-with-your-repo" ) ;

		assertThat( generatedProject.at( myNewEnv + "settings/application/definition-repo-branch" ).asText( ) )
				.isEqualTo( "my-branch-name" ) ;

		assertThat( generatedProject.at( myNewEnv + "settings/loadbalancer-url" ).asText( ) )
				.isEqualTo( "http://my.loadbalancer.com" ) ;

		assertThat( generatedProject.at( myNewEnv + "settings/monitorDefaults/maxDiskPercent" ).asInt( ) )
				.isEqualTo( 99 ) ;

		var newGlobalConfig = myNewEnv + "settings/configuration-maps/global" ;
		assertThat( generatedProject.at( newGlobalConfig + "/test-not-replaced" ).asText( ) )
				.isEqualTo( "value-not-replaced" ) ;

		assertThat( generatedProject.at( newGlobalConfig + "/test1" ).asText( ) )
				.isEqualTo( "test1-overridden" ) ;

		//
		// project cluster inserted
		//
		assertThat( modifyResults.at( "/environments/clusters/csap-logging-cluster" ).asText( ) ).isEqualTo(
				"added new cluster" ) ;
		assertThat( generatedProject.at( myNewEnv + "csap-logging-cluster/type" ).asText( ) ).isEqualTo( "simple" ) ;
		assertThat( generatedProject.at( myNewEnv + "csap-logging-cluster/hosts/0" ).asText( ) ).isEqualTo( "auto-4" ) ;
		assertThat( generatedProject.at( myNewEnv + "csap-logging-cluster/template-references/0" ).asText( ) )
				.isEqualTo( "elastic-search" ) ;

		assertThat( generatedProject.at( myNewEnv + "cluster-to-be-deleted" ).isMissingNode( ) ).isTrue( ) ;

		assertThat( generatedProject.at( "/service-templates/elastic-search/server" ).asText( ) ).isEqualTo(
				"docker" ) ;

		//
		// deletes
		//
		logger.info( CsapApplication.testHeader( "deletes" ) ) ;
		assertThat( modifyResults.at( "/operations/0/deletes/processed/0" ).asText( ) )
				.isEqualTo( "/environments/defaults/settings/application/sub-projects" ) ;
		assertThat( generatedProject.at( "/environments/defaults/settings/application/sub-projects" ).isMissingNode( ) )
				.isTrue( ) ;

		//
		// updates
		//
		logger.info( CsapApplication.testHeader( "updates" ) ) ;
		assertThat( modifyResults.at( "/operations/1/updates/autoplay-errors" ).size( ) ).isEqualTo( 1 ) ;
		assertThat( modifyResults.at( "/operations/1/updates/autoplay-errors/0" ).asText( ) ).isEqualTo(
				"/missing/path" ) ;
		assertThat( generatedProject.at( myNewEnv + "settings/csap-collection/host" )
				.toString( ) ).isEqualTo( "[1,2]" ) ;

		//
		// inserts
		//
		logger.info( CsapApplication.testHeader( "inserts" ) ) ;
		assertThat( modifyResults.at( "/operations/2/inserts/autoplay-errors" ).size( ) ).isEqualTo( 1 ) ;

		assertThat( modifyResults.at( "/operations/2/inserts/processed/0" ).asText( ) )
				.isEqualTo( myNewEnv + "settings/configuration-maps: added to parent generated-map" ) ;

		assertThat( modifyResults.at( "/operations/2/inserts/processed/1" ).asText( ) )
				.isEqualTo( myNewEnv + "settings/configuration-maps/global: added to existing map" ) ;

		assertThat( generatedProject.at( newGlobalConfig + "/STAGING" ).asText( ) )
				.isEqualTo( "$$csap-base" ) ;

		assertThat( generatedProject.at( newGlobalConfig + "/$$test-template-variable" ).asText( ) )
				.isEqualTo( "$$csap-base" ) ;

		assertThat( generatedProject.at( newGlobalConfig + "/some-new-variable" ).asText( ) )
				.isEqualTo( "$$some-value" ) ;

		assertThat( generatedProject.at( myNewEnv + "settings/configuration-maps/generated-map" )
				.toString( ) )
						.contains( "value-1" )
						.contains( "value-2" ) ;

	}

	private ObjectNode loadAutoPlay ( String fileName ) {

		File csapAutoPlayFile = new File( definitionFolder, fileName ) ;

		var results = getJsonMapper( ).createObjectNode( ) ;

		var docs = getApplication( ).getProjectLoader( ).getProjectOperators( ).loadYaml( csapAutoPlayFile, results ) ;

		logger.debug( "docs: {}", docs ) ;

		var modifyOperations = docs.get( 0 ) ;
		return modifyOperations ;

	}

	@Test
	void project_variables ( ) {

		logger.info( CsapApplication.testHeader( ) ) ;

		var globalMap = getApplication( ).environmentSettings( ).getConfigurationMap(
				EnvironmentSettings.GLOBAL_CONFIG_MAP_NAME ) ;
		logger.info( "configuration maps: {}", CSAP.jsonPrint( globalMap ) ) ;

		assertThat( globalMap.path( "test1" ).asText( ) ).isEqualTo( "test1-overridden" ) ;
		assertThat( globalMap.path( "test2" ).asText( ) ).isEqualTo( "test2" ) ;

	}

	@Test
	void application_monitors ( ) {

		logger.info( CsapApplication.testHeader( ) ) ;

		assertThat( getRootProjectSettings( ).getAutoStopServiceThreshold( "cluster1-host" ) )
				.isEqualTo( 5 ) ;

		assertThat( getRootProjectSettings( ).getMaxDiskPercent( "no-default-host" ) )
				.isEqualTo( 79 ) ;

		assertThat( getRootProjectSettings( ).getMaxDiskPercent( "cluster1-host" ) )
				.isEqualTo( 79 ) ;

		assertThat( getRootProjectSettings( ).getMonitorForCluster( "cluster1-host", ServiceAlertsEnum.threads ) )
				.isEqualTo( 999 ) ;

	}

	@Test
	void application_settings ( ) {

		logger.info( CsapApplication.testHeader( ) ) ;

		assertThat( getApplication( ).rootProjectEnvSettings( ).getApplicationName( ) ).isEqualTo( "multi-default" ) ;
		assertThat( getApplication( ).environmentSettings( ).getApplicationName( ) ).isEqualTo( "multi-default" ) ;
		assertThat( getApplication( ).rootProjectEnvSettings( "test" ).getApplicationName( ) ).isEqualTo(
				"multi-test" ) ;

		assertThat( getApplication( ).rootProjectEnvSettings( ).getAdminToAgentTimeoutSeconds( ) )
				.as( "agent time out" )
				.isEqualTo( 5 ) ;

		assertThat( getApplication( ).rootProjectEnvSettings( ).isEventPublishEnabled( ) )
				.as( "isEventPublishEnabled" )
				.isTrue( ) ;

		logger.info( "infra settings: {}", getApplication( ).rootProjectEnvSettings( ).getInfraTests( ) ) ;

		assertThat( getApplication( ).rootProjectEnvSettings( ).getInfraTests( ).getCpuLoopsMillions( ) )
				.as( "cpu loops" )
				.isEqualTo( 1 ) ;

		assertThat( getApplication( ).rootProjectEnvSettings( ).getLabelToServiceUrlLaunchMap( ).size( ) )
				.isEqualTo( 2 ) ;

		logger.info( "Event Api Url: {}", getApplication( ).rootProjectEnvSettings( ).getEventApiUrl( ) ) ;

		assertThat( getApplication( ).rootProjectEnvSettings( ).getEventApiUrl( ) )
				.isEqualTo( "http://event-dev-url/api/event" ) ;

	}

	@Test
	public void verify_model_names ( ) {

		logger.info( CsapApplication.testHeader( ) ) ;

		String servicesOnHost = getApplication( )
				.getServicesOnHost( )
				.stream( )
				.map( ServiceInstance::getServiceName_Port )
				.collect( Collectors.joining( "\t" ) ) ;

		logger.info( "servicesOnHost: {} , CsapSimple present: {}, xxx present: {}",
				servicesOnHost,
				getApplication( ).getActiveProject( ).isNameInPackageDefinitions( "CsapSimple" ),
				getApplication( ).getActiveProject( ).isNameInPackageDefinitions( "xxx" ) ) ;

		assertThat( getApplication( ).getActiveProject( ).isNameInPackageDefinitions( "simple-service" ) ).isTrue( ) ;
		assertThat( getApplication( ).getActiveProject( ).isNameInPackageDefinitions( "xxx" ) ).isFalse( ) ;
		assertThat( getApplication( ).getActiveProject( ).isNameInPackageDefinitions( "modjk" ) ).isFalse( ) ;
		assertThat( getApplication( ).getActiveProject( ).isNameInPackageDefinitions( "dev" ) ).isTrue( ) ;
		assertThat( getApplication( ).getActiveProject( ).isNameInPackageDefinitions( "type" ) ).isTrue( ) ;
		assertThat( getApplication( ).getActiveProject( ).isNameInPackageDefinitions( ServiceAttributes.parameters
				.json( ) ) ).isTrue( ) ;
		assertThat( getApplication( ).getActiveProject( ).isNameInPackageDefinitions( "kubelet" ) ).isFalse( ) ;

	}

	@Test
	public void verify_simple_service ( ) {

		logger.info( CsapApplication.testHeader( ) ) ;

		assertThat( simpleService.getDockerImageName( ) )
				.isEqualTo( "nginx:1.2.3" ) ;

		//
		// Env merge overrides
		//

		assertThat( simpleService.getDockerSettings( ).path( DockerJson.network.json( ) ).path( DockerJson.network_name
				.json( ) ).asText( ) )
						.isEqualTo( "nginx-network-over" ) ;

		assertThat( simpleService.getDockerSettings( ).path( DockerJson.network.json( ) ).path( "test-new" ).asText( ) )
				.isEqualTo( "new-value" ) ;

		var networkSettings = simpleService
				.getDockerSettings( )
				.path( DockerJson.network.json( ) )
				.path( DockerJson.create_persistent.json( ) ) ;

		assertThat( networkSettings.path( "enabled" ).asBoolean( true ) )
				.isFalse( ) ;

		assertThat( networkSettings.path( DockerJson.network_driver.json( ) ).asText( ) )
				.isEqualTo( "bridge" ) ;

	}

	@Test
	public void verify_job_variables ( ) {

		logger.info( CsapApplication.testHeader( ) ) ;

		var jobs = simpleService.getJobs( ) ;

		logger.info( "jobs: {}", jobs ) ;

		assertThat( jobs.toString( ) )
				.doesNotContain( CsapCore.CSAP_DEF_NAME ) ;

		ServiceJobRunner jobRunner = new ServiceJobRunner( getApplication( ) ) ;

		var jobResults = jobRunner.runJobUsingDescription( simpleService, jobs.get( 0 ).getDescription( ), null ) ;
		logger.info( "jobResults: {}", jobResults ) ;

		assertThat( jobResults )
				.contains( "test1=test1-overridden" )
				.contains( "test2=test2" )
				.contains( "test-simple=test-val" )
				.doesNotContain( CsapCore.CSAP_VARIABLE_PREFIX )
				.contains( "test_csap_name=test-csap-name-value" )
				.contains( "ingress_host=the-ingress-host" )
				.contains( "test_inherit=test-base-resolution" )
				.contains( "test-base-script" )
				.doesNotContain( CsapCore.CSAP_DEF_REPLICA )
				.doesNotContain( CsapCore.CSAP_DEF_NAME ) ;

	}

	@Test
	public void verify_yaml_variables ( ) {

		logger.info( CsapApplication.testHeader( ) ) ;

		var specUriStream = getServiceOsManager( ).buildSpecificationFileArray(
				simpleService,
				simpleService.getKubernetesDeploymentSpecifications( ) ) ;

		var specPathList = specUriStream.collect( Collectors.toList( ) ) ;
		logger.info( "specFiles: {}", specPathList ) ;

		assertThat( specPathList.size( ) ).isEqualTo( 1 ) ;
		assertThat( specPathList.toString( ) )
				.doesNotContain( CsapCore.SEARCH_RESOURCES )
				.contains( "definitions/simple-multi-project/resources/simple-service/sample-deploy.yaml" ) ;

		var simpleYamlDeployFile = specPathList.stream( )
				.map( specUri -> new File( specUri ) )
				.findFirst( )
				.get( ) ;

		// getServiceOsManager().buildYamlTemplate( simpleService, sourceFile ) ;
		var rawContents = Application.readFile( simpleYamlDeployFile ) ;
		logger.info( "Original yaml: {} ", rawContents ) ;

		assertThat( rawContents ).contains( "$$service-name" ) ;

		var envSettings = getApplication( ).environmentSettings( ).getKubernetesYamlReplacements( ) ;
		var currentImage = "image: nginx:1.2.3" ;
		var newImage = "image: nginx:1.2.4" ;
		envSettings.put( currentImage, newImage ) ;

		var deploymentFile = getServiceOsManager( )
				.buildDeplomentFile( simpleService, simpleYamlDeployFile, getJsonMapper( ).createObjectNode( ) ) ;

		var yaml_with_vars_updated = Application.readFile( deploymentFile ) ;

		logger.info( "yaml_with_vars_updated: {} ", yaml_with_vars_updated ) ;

		assertThat( yaml_with_vars_updated )
				.doesNotContain( CsapCore.CSAP_VARIABLE_PREFIX )
				.doesNotContain( "$$service-name" )
				.doesNotContain( "$$test-csap-name" )
				.contains( "test-csap-name-value" )
				.doesNotContain( "$$ingress-host" )
				.contains( "the-ingress-host" )
				.doesNotContain( "$$test-csap-nested" )
				.contains( "base-test-csap-name-value" ) ;

		assertThat( yaml_with_vars_updated )
				.doesNotContain( currentImage ) ;

		//
		// logger.info( "yaml_with_vars_updated: {}", yaml_with_vars_updated ) ;
		//
		// assertThat( yaml_with_vars_updated )
		// .contains( "context-path=/k8s-spec-deploy-service" ) ;

	}

	@Test
	public void verify_autostart_order ( ) {

		logger.info( CsapApplication.testHeader( ) ) ;

		String defaultServiceList = getApplication( )
				.getServicesOnHost( )
				.stream( )
				.map( ServiceInstance::getName )
				.collect( Collectors.joining( ", " ) ) ;

		String sortedServiceList = getApplication( )
				.getServicesOnHost( )
				.stream( )
				.sorted( comparing( ServiceInstance::getAutoStart ) )
				.map( ServiceInstance::getName )
				.collect( Collectors.joining( ", " ) ) ;

		String reversed = getApplication( )
				.getServicesOnHost( )
				.stream( )
				.sorted( comparing( ServiceInstance::getAutoStart ).reversed( ) )
				.map( ServiceInstance::getName )
				.collect( Collectors.joining( ", " ) ) ;

		logger.info( "default: {} \n\t sorted: {} \n\t reversed: {}",
				defaultServiceList, sortedServiceList, reversed ) ;

		assertThat( defaultServiceList ).isEqualTo( "docker, csap-verify-service, simple-service, csap-agent" ) ;
		assertThat( sortedServiceList ).isEqualTo( "csap-agent, simple-service, docker, csap-verify-service" ) ;

	}

}
