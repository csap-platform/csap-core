/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.csap.agent.project.loader ;

import static java.util.Comparator.comparing ;
import static org.assertj.core.api.Assertions.assertThat ;

import java.io.File ;
import java.util.stream.Collectors ;

import org.apache.commons.io.FileUtils ;
import org.csap.agent.Agent_context_loaded ;
import org.csap.agent.CsapBareTest ;
import org.csap.agent.CsapCore ;
import org.csap.agent.CsapTemplate ;
import org.csap.agent.model.ProjectLoader ;
import org.csap.agent.model.ProjectMigrator ;
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

public class Default_Project extends CsapBareTest {

	@BeforeAll
	public void beforeAll ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		// CSAP.setLogToInfo( ProjectLoader.class.getName( ) ) ;
		assertThat( getApplication( ).loadDefinitionForJunits( false, Agent_context_loaded.SIMPLE_TEST_DEFINITION ) )
				.as( "No Errors or warnings" )
				.isTrue( ) ;
		// CSAP.setLogToInfo( ProjectLoader.class.getName( ) ) ;

		logger.info( CsapApplication.SETUP_HEAD + "Using: " + Agent_context_loaded.SIMPLE_TEST_DEFINITION
				.getAbsolutePath( ) ) ;

	}

	@Test
	public void default_services_loaded ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		var defaultsFile = CsapTemplate.default_service_definitions.getFile( ) ;

		var services = getApplication( ).getProjectLoader( ).definition_file_reader( defaultsFile, false ) ;

		var serviceTemplates = (ObjectNode) services.path( ProjectLoader.SERVICE_TEMPLATES ) ;

		ProjectMigrator migrator = new ProjectMigrator( false ) ;
		migrator.migrateServiceTemplates( serviceTemplates ) ;

		var updateTemplateFile = new File( defaultsFile.getParentFile( ), "update-templates.json" ) ;
		logger.info( "Creating: updateTemplateFile {}", updateTemplateFile.getAbsolutePath( ) ) ;
		FileUtils.write( updateTemplateFile, services.toString( ) ) ;

		var defaultAgent = serviceTemplates.path( CsapCore.AGENT_NAME ) ;

		logger.info( "csap-agent: {}", CSAP.jsonPrint( defaultAgent ) ) ;

		assertThat( defaultAgent.isObject( ) ).isTrue( ) ;

	}

	@Test
	public void default_clusters_loaded ( ) {

		logger.info( CsapApplication.testHeader( ) ) ;

		var clusters = getApplication( ).getClustersInLifecycle( getApplication( ).getActiveProjectName( ) ) ;
		logger.info( "clusters: {}", clusters ) ;

		assertThat( clusters ).contains( "dev:csap-simple" ) ;

		assertThat( getApplication( ).rootProjectEnvSettings( ).getApplicationName( ) ).contains(
				"DEFAULT APPLICATION FOR JUNITS" ) ;

	}

	@Test
	public void default_application_loaded ( ) {

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

		assertThat( getApplication( ).rootProjectEnvSettings( ).getLabelToServiceUrlLaunchMap( ).size( ) )
				.isEqualTo( 2 ) ;

		logger.info( "Event Api Url: {}", getApplication( ).rootProjectEnvSettings( ).getEventApiUrl( ) ) ;

		assertThat( getApplication( ).rootProjectEnvSettings( ).getEventApiUrl( ) )
				.isEqualTo( "$eventServiceUrl/api/event" ) ;

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
		assertThat( getApplication( ).getActiveProject( ).isNameInPackageDefinitions( "csap-simple" ) ).isTrue( ) ;
		assertThat( getApplication( ).getActiveProject( ).isNameInPackageDefinitions( "type" ) ).isTrue( ) ;
		assertThat( getApplication( ).getActiveProject( ).isNameInPackageDefinitions( ServiceAttributes.parameters
				.json( ) ) ).isTrue( ) ;

		assertThat( getApplication( ).getActiveProject( ).isNameInPackageDefinitions( "kubelet" ) ).isFalse( ) ;

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

		logger.info( "default: {} \n\t sorted: {} \n\t reversed: {}",
				defaultServiceList, sortedServiceList, reversed ) ;

	}

}
