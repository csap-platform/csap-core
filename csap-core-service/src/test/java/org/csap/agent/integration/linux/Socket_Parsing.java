/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.csap.agent.integration.linux ;

import static org.assertj.core.api.Assertions.assertThat ;

import java.io.File ;
import java.util.Arrays ;

import org.apache.commons.io.FileUtils ;
import org.csap.agent.Agent_context_loaded ;
import org.csap.agent.CsapConstants ;
import org.csap.agent.CsapThinTests ;
import org.csap.agent.container.C7 ;
import org.csap.agent.linux.OsCommandRunner ;
import org.csap.agent.linux.ResourceCollector ;
import org.csap.agent.model.ServiceInstance ;
import org.csap.helpers.CSAP ;
import org.csap.helpers.CsapApplication ;
import org.junit.jupiter.api.BeforeAll ;
import org.junit.jupiter.api.Disabled ;
import org.junit.jupiter.api.Test ;

/**
 *
 * @author someDeveloper
 */

public class Socket_Parsing extends CsapThinTests {

	ResourceCollector osCollector ;

	@BeforeAll
	public void beforeAll ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;
		// Application.setJvmInManagerMode( false ) ;
		//
		// OsManager osManager = new OsManager( null, new OsCommands() ) ;
		// csapApplication = new Application( osManager ) ;
		// osManager.setTestApp( csapApplication ) ;
		//
		// csapApplication.setEventClient( new CsapEvents() ) ;
		//
		// osCollector = new ServiceResourceRunnable( csapApplication, new
		// OsCommandRunner( 2, 2, "dummy" ) ) ;
		//
		// csapApplication.setAutoReload( false ) ;
		//
		// csapApplication.initialize() ;
		osCollector = new ResourceCollector( getCsapApis( ), new OsCommandRunner( 2, 2, "dummy" ) ) ;

		assertThat( getApplication( ).loadDefinitionForJunits( false, Agent_context_loaded.SIMPLE_TEST_DEFINITION ) )
				.as( "No Errors or warnings" )
				.isTrue( ) ;

	}

	@Disabled
	@Test
	public void verify_agent_sockets_redhat6 ( ) {

		logger.info( CsapApplication.testHeader( ) ) ;

		ServiceInstance agentInstance = getApplication( ).getServiceInstanceCurrentHost( CsapConstants.AGENT_ID ) ;
		assertThat( agentInstance.is_springboot_server( ) )
				.as( "server override" )
				.isTrue( ) ;

		agentInstance.getDefaultContainer( ).setPid( Arrays.asList( "13303" ) ) ;

		osCollector.testSocketParsing( agentInstance ) ;
		// errer
		assertThat( agentInstance.getDefaultContainer( ).getSocketCount( ) )
				.as( "socket count" )
				.isEqualTo( 15 ) ;

	}

	@Test
	void verify_ingress_sockets ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		File simpleDefinitionFile = new File( getClass( ).getResource( "nginx-ingress.json" ).getPath( ) ) ;

		var nginxExport = getJsonMapper( ).readTree( FileUtils.readFileToString( simpleDefinitionFile ) ) ;

		ServiceInstance ingressService = ServiceInstance.buildInstance( getJsonMapper( ), nginxExport ) ;

		var customDef = getJsonMapper( ).createObjectNode( ) ;
		customDef.set( C7.definitionSettings.val( ), nginxExport.path( "dockerSettings" ) ) ;
		ingressService.parseDefinition( "default", customDef, new StringBuilder( ) ) ;

		assertThat( ingressService.is_docker_server( ) )
				.as( "server override" )
				.isTrue( ) ;

		CSAP.setLogToDebug( ResourceCollector.class.getName( ) ) ;
		osCollector.testSocketParsing( ingressService ) ;
		CSAP.setLogToInfo( ResourceCollector.class.getName( ) ) ;

		assertThat( ingressService.getDefaultContainer( ).getSocketCount( ) )
				.as( "socket count" )
				.isEqualTo( 36 ) ;

	}

	@Test
	public void verify_agent_sockets_redhat7 ( ) {

		logger.info( CsapApplication.testHeader( ) ) ;

		ServiceInstance agentInstance = getApplication( ).getServiceInstanceCurrentHost( CsapConstants.AGENT_ID ) ;
		assertThat( agentInstance.is_springboot_server( ) )
				.as( "server override" )
				.isTrue( ) ;

		agentInstance.getDefaultContainer( ).setPid( Arrays.asList( "10941" ) ) ;
		CSAP.setLogToDebug( ResourceCollector.class.getName( ) ) ;
		osCollector.testSocketParsing( agentInstance ) ;
		CSAP.setLogToInfo( ResourceCollector.class.getName( ) ) ;

		assertThat( agentInstance.getDefaultContainer( ).getSocketCount( ) )
				.as( "socket count" )
				.isEqualTo( 12 ) ;

	}
}
