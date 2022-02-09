/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.csap.agent.full.project ;

import static org.assertj.core.api.Assertions.assertThat ;

import java.io.File ;
import java.io.IOException ;

import org.csap.agent.Agent_context_loaded ;
import org.csap.agent.CsapThinTests ;
import org.csap.agent.linux.HostStatusManager ;
import org.csap.agent.model.Application ;
import org.csap.helpers.CSAP ;
import org.csap.helpers.CsapApplication ;
import org.junit.jupiter.api.BeforeAll ;
import org.junit.jupiter.api.Test ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.boot.context.properties.ConfigurationProperties ;
import org.springframework.test.context.ActiveProfiles ;

import com.fasterxml.jackson.core.JsonProcessingException ;

@ConfigurationProperties ( prefix = "test.variables" )
@ActiveProfiles ( {
		"admin", "junit", "Verify_Remote_Tools"
} )

public class Verify_Remote_Tools extends CsapThinTests {

	final private Logger logger = LoggerFactory.getLogger( getClass( ) ) ;

	static {

		CsapApplication.initialize( "Test Setup Complete" ) ;

	}

	String eventServiceUrl ;

	String user ;
	String pass ;

	@BeforeAll
	public void setUpBeforeClass ( )
		throws Exception {

		File csapApplicationDefinition = new File(
				Verify_Remote_Tools.class
						.getResource( Agent_context_loaded.TEST_DEFINITIONS
								+ "sub-project-release-file/clusterConfigManager.json" )
						.getPath( ) ) ;

		getApplication( ).setJvmInManagerMode( true ) ;
		HostStatusManager testStatus = new HostStatusManager( "CsAgent_Host_Response.json", getCsapApis( ) ) ;
		getApplication( ).setHostStatusManager( testStatus ) ;

		assertThat( getApplication( ).loadCleanForJunits( false, csapApplicationDefinition ) )
				.as( "No Errors or warnings" )
				.isTrue( ) ;

		logger.info( Agent_context_loaded.SETUP_HEAD + "Using: " + csapApplicationDefinition.getAbsolutePath( ) ) ;

	}

	@Test
	public void verify_platform_versions_csaptools ( )
		throws JsonProcessingException ,
		IOException {

		logger.info( CsapApplication.testHeader( "{} @ {}" ), getUser( ), getEventServiceUrl( ) ) ;

		// updating from test variables
		getApplication( ).rootProjectEnvSettings( ).setEventServiceUrl( getEventServiceUrl( ) ) ;
		getApplication( ).rootProjectEnvSettings( ).setEventUser( getUser( ) ) ;
		getApplication( ).rootProjectEnvSettings( ).setEventCredential( getPass( ) ) ;

		// default application sets disabled - revert to default
		getApplication( ).rootProjectEnvSettings( ).setEventUrl( "/api/event" ) ;

		assertThat( getApplication( ).rootProjectEnvSettings( ).getCsapAnalyticsServerRootUrl( ) )
				.startsWith( getEventServiceUrl( ).substring( 0, getEventServiceUrl( ).lastIndexOf( "/" ) ) ) ;

		CSAP.setLogToDebug( Application.class.getName( ) ) ;
		String platformVersions = getApplication( ).getScoreReport( ).updatePlatformVersionsFromCsapTools( true ) ;
		CSAP.setLogToInfo( Application.class.getName( ) ) ;

		logger.info( platformVersions ) ;

		assertThat( platformVersions )
				.as( "platformVersions matches latest" )
				.matches( "2.*, 19.*, 1.*" ) ;
		// .matches( "2.*, jdk-9.*, .*7.*" ) ;

	}

	public String getEventServiceUrl ( ) {

		return eventServiceUrl ;

	}

	public void setEventServiceUrl ( String eventApiUrl ) {

		this.eventServiceUrl = eventApiUrl ;

	}

	public String getUser ( ) {

		return user ;

	}

	public void setUser ( String user ) {

		this.user = user ;

	}

	public String getPass ( ) {

		return pass ;

	}

	public void setPass ( String pass ) {

		this.pass = pass ;

	}

}
