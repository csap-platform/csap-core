/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.csap.agent.project.loader ;

import static org.assertj.core.api.Assertions.assertThat ;

import java.io.File ;

import org.csap.agent.CsapBareTest ;
import org.csap.agent.CsapConstants ;
import org.csap.agent.linux.ServiceJobRunner ;
import org.csap.agent.model.EnvironmentSettings ;
import org.csap.agent.model.Project ;
import org.csap.agent.model.ProjectLoader ;
import org.csap.agent.model.ServiceInstance ;
import org.csap.helpers.CSAP ;
import org.csap.helpers.CsapApplication ;
import org.junit.jupiter.api.BeforeAll ;
import org.junit.jupiter.api.Test ;

/**
 *
 * @author someDeveloper
 */

public class Simple_Multi_Sub_Project extends CsapBareTest {

	String definitionPath = "/definitions/simple-multi-project/root-project.json" ;
	File csapApplicationDefinition = new File( getClass( ).getResource( definitionPath ).getPath( ) ) ;

	ServiceInstance simpleService2 ;

	@BeforeAll
	void beforeAll ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( "loading: {}" ), csapApplicationDefinition.getAbsolutePath( ) ) ;

		getApplication( ).setHostNameForDefinitionMapping( "sub-host-1" ) ;

		CSAP.setLogToInfo( ProjectLoader.class.getName( ) ) ;
		assertThat( getApplication( ).loadDefinitionForJunits( false, csapApplicationDefinition ) )
				.as( "No Errors or warnings" )
				.isTrue( ) ;
		CSAP.setLogToInfo( ProjectLoader.class.getName( ) ) ;

		simpleService2 = getApplication( ).flexFindFirstInstanceCurrentHost( "simple-2" ) ;
		assertThat( simpleService2 ).isNotNull( ) ;

	}

	@Test
	void sub_project_loaded ( ) {

		logger.info( CsapApplication.testHeader( ) ) ;

		var subProject = getApplication( ).getProject( "sub-project-a" ) ;

		assertThat( subProject ).isNotNull( ) ;

		var clustersToHosts = subProject.getClustersToHostMapInCurrentLifecycle( ) ;
		assertThat( clustersToHosts ).isNotNull( ) ;

		assertThat( clustersToHosts.keySet( ) )
				.hasSize( 1 )
				.contains( "test-cluster2" ) ;

		assertThat( clustersToHosts.get( "test-cluster2" ) )
				.hasSize( 1 )
				.contains( "sub-host-1" ) ;

		var subProjectSettings = subProject.getEnvironmentNameToSettings( ).get( "dev" ) ;

		assertThat( subProjectSettings ).isNotNull( ) ;

		assertThat( subProjectSettings.getApplicationName( ) ).isEqualTo( "multi-test-sub" ) ;

		var globalEnvVars = subProjectSettings.getConfigurationMap( Project.GLOBAL_PACKAGE ) ;
		logger.info( "globalEnvVars: {}", globalEnvVars ) ;

		// var clusters = getApplication().getClustersInLifecycle(
		// getApplication().getActiveProjectName() ) ;
		// logger.info( "clusters: {}", clusters ) ;
		//
		// assertThat( clusters ).contains( "dev:test-cluster2" ) ;
		//
		// var verifyService = getApplication().findServiceByNameOnCurrentHost(
		// "csap-verify-service" ) ;
		// assertThat( verifyService ).isNotNull() ;
		// assertThat( verifyService.getPort() ).isEqualTo( "7011" ) ;

	}

	@Test
	public void verify_job_variables ( ) {

		logger.info( CsapApplication.testHeader( ) ) ;

		var jobs = simpleService2.getJobs( ) ;

		logger.info( "jobs: {}", jobs ) ;

		assertThat( jobs.toString( ) )
				.doesNotContain( CsapConstants.CSAP_DEF_NAME ) ;

		ServiceJobRunner jobRunner = new ServiceJobRunner( getCsapApis( ) ) ;

		var jobResults = jobRunner.runJobUsingDescription( simpleService2, jobs.get( 0 ).getDescription( ), null ) ;
		logger.info( "jobResults: {}", jobResults ) ;

		assertThat( jobResults )
				.doesNotContain( "test1" )
				.contains( "test4=test4" )
				.contains( "test5=test5-overidden" )
				.doesNotContain( CsapConstants.CSAP_DEF_REPLICA )
				.doesNotContain( CsapConstants.CSAP_DEF_NAME ) ;

	}

	@Test
	void project_variables ( ) {

		logger.info( CsapApplication.testHeader( ) ) ;

		var globalMap = getApplication( ).environmentSettings( ).getConfigurationMap(
				EnvironmentSettings.GLOBAL_CONFIG_MAP_NAME ) ;
		logger.info( "configuration maps: {}", CSAP.jsonPrint( globalMap ) ) ;

		assertThat( globalMap.path( "test1" ).asText( ) ).isEqualTo( "" ) ;
		assertThat( globalMap.path( "test4" ).asText( ) ).isEqualTo( "test4" ) ;

	}

}
