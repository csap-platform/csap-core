/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.csap.agent.project.loader ;

import static org.assertj.core.api.Assertions.assertThat ;

import java.io.File ;

import org.csap.agent.CsapConstants ;
import org.csap.agent.model.Application ;
import org.csap.agent.model.ServiceAttributes ;
import org.csap.agent.model.ServiceBaseParser ;
import org.csap.agent.model.ServiceInstance ;
import org.csap.helpers.CsapApplication ;
import org.junit.jupiter.api.BeforeAll ;
import org.junit.jupiter.api.Test ;
import org.junit.jupiter.api.TestInstance ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;

/**
 *
 * @author someDeveloper
 */

@TestInstance ( TestInstance.Lifecycle.PER_CLASS )
public class Service_Parsing_Warnings {

	Logger logger = LoggerFactory.getLogger( getClass( ) ) ;

	@BeforeAll
	public void beforeAll ( )
		throws Exception {

		CsapApplication.initialize( "Test Setup Complete" ) ;

		csapApp = Application.testBuilder( ) ;
		csapApp.setAutoReload( false ) ;

		File csapApplicationDefinition = new File(
				Service_Parsing_Warnings.class.getResource( "application-warnings.json" ).getPath( ) ) ;

		StringBuilder parseResults = csapApp.getProjectLoader( ).process( false, csapApplicationDefinition ) ;

		// assertThat( parseResults.toString() )
		// .as( "Found Warnings" )
		// .contains( CsapCore.CONFIG_PARSE_WARN ) ;
	}

	// @Inject
	static Application csapApp ;

	@Test
	public void verify_httpd_attributes ( ) {

		ServiceInstance httpdInstance = csapApp.serviceNameToAllInstances( ).get( "httpd" ).get( 0 ) ;

		assertThat( httpdInstance.is_csap_api_server( ) )
				.as( "is wrapper" )
				.isTrue( ) ;

		assertThat( httpdInstance.getProcessFilter( ) )
				.as( "process" )
				.isEqualTo( "httpdFilter" ) ;

	}

	@Test
	public void verify_vmware_attributes ( ) {

		ServiceInstance wmwareService = csapApp.serviceNameToAllInstances( ).get( "vmtoolsd" ).get( 0 ) ;

		assertThat( wmwareService.is_os_process_monitor( ) )
				.as( "is script" )
				.isTrue( ) ;

	}

	@Test
	public void verify_jdk_attributes ( ) {

		ServiceInstance jdkService = csapApp.serviceNameToAllInstances( ).get( "jdk" ).get( 0 ) ;

		assertThat( jdkService.is_files_only_package( ) )
				.as( "is script" )
				.isTrue( ) ;

		assertThat( jdkService.getScmLocation( ) )
				.as( "source location" )
				.contains( "JavaDevKitPackage8" ) ;

		assertThat( jdkService.getScm( ) )
				.as( "scm type" )
				.isEqualTo( "svn" ) ;

	}

	@Test
	public void verify_reference_attributes ( ) {

		ServiceInstance refService = csapApp.serviceNameToAllInstances( ).get( "Cssp3ReferenceMq" ).get( 0 ) ;

		assertThat( refService.is_springboot_server( ) )
				.as( "server override" )
				.isTrue( ) ;

		assertThat( refService.isTomcatPackaging( ) )
				.as( "server override" )
				.isFalse( ) ;

	}

	@Test
	public void verify_serviceWithWarning_attributes ( ) {

		ServiceInstance refService = csapApp.serviceNameToAllInstances( ).get( "ServiceWithWarnings" ).get( 0 ) ;

		assertThat( refService.is_springboot_server( ) )
				.as( "server override" )
				.isTrue( ) ;

		assertThat( refService.isTomcatPackaging( ) )
				.as( "server override" )
				.isFalse( ) ;

		// WARNINGS

		assertThat( refService.getPerformanceConfiguration( ).toString( ) )
				.as( "performance items" )
				.contains( "ProcessCpu", "jmxHeartbeatMs" ) ;

		assertThat( refService.getPerformanceConfiguration( ).get( "Total VmCpu" ).get( ServiceBaseParser.ERRORS )
				.asBoolean( ) )
						.as( "performance items space is ignored" )
						.isTrue( ) ;

		// NO Warning

		assertThat( refService.getParameters( ) )
				.as( "params" )
				.isEqualTo( "-Xms16M -Xmx256M paramsOveride" ) ;

		assertThat( refService.getScm( ) )
				.as( "scm type" )
				.isEqualTo( "svn" ) ;

		assertThat( refService.getAttributeAsObject( ServiceAttributes.webServerTomcat ) )
				.as( "webServer" )
				.isNotNull( ) ;

		assertThat( refService.getAttributeAsObject( ServiceAttributes.webServerTomcat ).get( "loadBalance" )
				.toString( ) )
						.as( "webServer load balance" )
						.contains( "method=Next", "sticky_session=1" ) ;

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
				.isEqualTo( "ServiceWithWarnings" ) ;

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

		ServiceInstance agentInstance = csapApp.serviceNameToAllInstances( ).get( CsapConstants.AGENT_NAME ).get( 0 ) ;

		assertThat( agentInstance.is_springboot_server( ) )
				.as( "server override" )
				.isTrue( ) ;

		assertThat( agentInstance.getOsProcessPriority( ) )
				.as( "process override" )
				.isEqualTo( -99 ) ;

		assertThat( agentInstance.getAttribute( ServiceAttributes.parameters ) )
				.as( "process override" )
				.contains( "-Doverride=true" ) ;

		assertThat( agentInstance.getMavenId( ) )
				.as( "maven override" )
				.contains( "9.9.9" ) ;

		assertThat( agentInstance.getScm( ) )
				.as( "scm type" )
				.isEqualTo( "svn" ) ;

	}

}
