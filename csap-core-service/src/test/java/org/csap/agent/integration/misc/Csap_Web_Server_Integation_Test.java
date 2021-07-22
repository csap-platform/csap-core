package org.csap.agent.integration.misc ;

import static org.assertj.core.api.Assertions.assertThat ;
import static org.assertj.core.api.Assertions.contentOf ;

import java.io.File ;
import java.io.IOException ;

import org.apache.commons.io.FileUtils ;
import org.csap.agent.CsapCore ;
import org.csap.agent.CsapThinTests ;
import org.csap.agent.integrations.HttpdIntegration ;
import org.csap.agent.model.ServiceInstance ;
import org.csap.helpers.CsapApplication ;
import org.junit.jupiter.api.BeforeAll ;
import org.junit.jupiter.api.Test ;
import org.junit.jupiter.api.TestInstance ;

import com.fasterxml.jackson.core.JsonProcessingException ;

@TestInstance ( TestInstance.Lifecycle.PER_CLASS )

class Csap_Web_Server_Integation_Test extends CsapThinTests {
	HttpdIntegration getHttpdConfig ( ) {

		return getApplication( ).getHttpdIntegration( ) ;

	}

	@BeforeAll
	void setUpBeforeAll ( ) {

		logger.info( CsapApplication.testHeader( ) ) ;

		getApplication( ).getProjectLoader( ).setAllowLegacyNames( true ) ;

		FileUtils.deleteQuietly( getHttpdConfig( ).getHttpdWorkersFile( ).getParentFile( ) ) ;

	}

	/**
	 *
	 * Scenario: - load a config file without package definition and 1 jvm and 1 os
	 *
	 */
	@Test
	void verify_web_server_files_updated_from_model ( )
		throws JsonProcessingException ,
		IOException {

		logger.info( CsapApplication.testHeader( ) ) ;

		File csapApplicationDefinition = new File(
				getClass( ).getResource( "web_server_model.json" ).getPath( ) ) ;

		logger.info( "Loading: {}", csapApplicationDefinition.getAbsolutePath( ) ) ;

		assertThat( getApplication( ).loadDefinitionForJunits( false, csapApplicationDefinition ) )
				.as( "No Errors or warnings" )
				.isTrue( ) ;

		assertThat( getApplication( ).getName( ) )
				.as( "Name" )
				.isEqualTo( "DEFAULT APPLICATION FOR JUNITS" ) ;

		assertThat( getApplication( ).getLifecycleList( ).size( ) )
				.as( "lifecycles" )
				.isEqualTo( 6 ) ;

		assertThat( getApplication( ).serviceNameToAllInstances( ).get( CsapCore.AGENT_NAME ).size( ) )
				.as( "agents allocated" )
				.isEqualTo( 4 ) ;

		// New instance meta data
		ServiceInstance csAgentInstance = getApplication( ).getServiceInstanceAnyPackage( CsapCore.AGENT_ID ) ;

		assertThat( csAgentInstance.getOsProcessPriority( ) )
				.as( "OS priority" )
				.isEqualTo( -12 ) ;

		logger.info( "Workers file: " + getHttpdConfig( ).getHttpdWorkersFile( ).getAbsolutePath( ) ) ;
		assertThat( getHttpdConfig( ).getHttpdWorkersFile( ) )
				.as( "Web Servers Worker file created" )
				.exists( ) ;

		assertThat( contentOf( getHttpdConfig( ).getHttpdWorkersFile( ) ) )
				.as( "Both factory and enterprise workers" )
				.contains(
						"worker.simple-hyphen-service_0localhost.port=0" )
				.contains(
						"worker.Cssp3ReferenceMq_0localhost.port=0" )
				.doesNotContain(
						"worker.Cssp3ReferenceMq_0peter.port=0" )
				.contains(
						"worker.Cssp3ReferenceMq-dev01_LB0.port=0" )
				.contains(
						"worker.Cssp3ReferenceMq-dev002_LB0.port=0" )
				.contains(
						"balance_workers=Cssp3ReferenceMq_0localhost" ) ;

		assertThat( contentOf( getHttpdConfig( ).getHttpdWorkersFile( ) ) )
				.as( "Custom Routing Rules" )
				.contains(
						"worker.ServiceWithCustomRouting_LB.method=Next" )
				.contains(
						"worker.ServiceWithCustomRouting_0localhost.reply_timeout=10000" )
				.contains(
						"worker.ServiceWithCustomRouting_LB.sticky_session=1" ) ;

		assertThat( getHttpdConfig( ).getHttpdModjkFile( ) )
				.as( "Web Servers ModJK file created" )
				.exists( ) ;

		assertThat( contentOf( getHttpdConfig( ).getHttpdModjkFile( ) ) )
				.contains( "JkMount /Cssp3ReferenceMq* Cssp3ReferenceMq_LB" )
				.contains( "JkMount /ServiceWithCustomRouting* ServiceWithCustomRouting_LB" )
				.contains( "JkMount /Cssp3ReferenceMq-dev01* Cssp3ReferenceMq-dev01_LB" )
				.contains( "JkMount /Cssp3ReferenceMq-dev002* Cssp3ReferenceMq-dev002_LB" ) ;

		assertThat( getHttpdConfig( ).getHttpdModReWriteFile( ) )
				.as( "Web Servers mod rewrite file created" )
				.exists( ) ;

		assertThat( contentOf( getHttpdConfig( ).getHttpdModReWriteFile( ) ) )
				.contains( "RewriteRule ^/test1/(.*)$  /ServiceWithCustomRouting/$1 [PT]" ) ;

	}

}
