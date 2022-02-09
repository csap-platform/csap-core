package org.csap.agent.integration.linux ;

import static org.assertj.core.api.Assertions.assertThat ;
import static org.assertj.core.api.Assertions.assertThatCode ;

import java.io.File ;
import java.io.IOException ;
import java.util.ArrayList ;
import java.util.Arrays ;
import java.util.List ;

import org.csap.agent.CsapConstants ;
import org.csap.agent.CsapThinTests ;
import org.csap.agent.linux.HostStatusManager ;
import org.csap.agent.model.ContainerState ;
import org.csap.helpers.CSAP ;
import org.csap.helpers.CsapApplication ;
import org.junit.jupiter.api.Assertions ;
import org.junit.jupiter.api.Assumptions ;
import org.junit.jupiter.api.BeforeAll ;
import org.junit.jupiter.api.BeforeEach ;
import org.junit.jupiter.api.Tag ;
import org.junit.jupiter.api.Test ;
import org.junit.jupiter.api.TestInstance ;
import org.springframework.boot.context.properties.ConfigurationProperties ;

import com.fasterxml.jackson.core.JsonProcessingException ;
import com.fasterxml.jackson.databind.node.ArrayNode ;
import com.fasterxml.jackson.databind.node.ObjectNode ;

@Tag ( "core" )
@ConfigurationProperties ( prefix = "test.variables" )
@TestInstance ( TestInstance.Lifecycle.PER_CLASS )
public class Host_Status_Manager_Test extends CsapThinTests {

	final static public File csapApplicationDefinition = new File(
			Host_Status_Manager_Test.class.getResource( "simpleApp.json" ).getPath( ) ) ;

	public void setTestAdminHost1 ( String testAdminHost1 ) {

		this.testAdminHost1 = testAdminHost1 ;

	}

	public void setTestAdminHost2 ( String testAdminHost2 ) {

		this.testAdminHost2 = testAdminHost2 ;

	}

	private String testAdminHost1 = null ;
	private String testAdminHost2 = null ;

	@BeforeEach
	public void setUp ( )
		throws Exception {

	}

	@BeforeAll
	public void beforeAll ( ) {

		// Guard for setup
		logger.info( CsapApplication.testHeader( ) ) ;

		Assumptions.assumeTrue( isSetupOk( ) ) ;

		getApplication( ).setJvmInManagerMode( true ) ;

		assertThatCode( ( ) -> {

			assertThat(
					getApplication( ).loadDefinitionForJunits( false, csapApplicationDefinition ) )
							.as( "No Errors or warnings" )
							.isTrue( ) ;

			getApplication( ).refreshAgentHttpConnections( ) ;

		} ).doesNotThrowAnyException( ) ;

	}

	private boolean isSetupOk ( ) {

		if ( testAdminHost2 == null || testAdminHost1 == null )
			return false ;

		logger.info( "testAdminHost2: {}, testAdminHost1: {}", testAdminHost2, testAdminHost1 ) ;

		return true ;

	}

	@Test
	public void verify_service_report ( ) {

		logger.info( CsapApplication.testHeader( ) ) ;

		HostStatusManager testHostStatusManager = new HostStatusManager(
				"admin/host-status-scorecard-test.json",
				getCsapApis( ) ) ;
		testHostStatusManager.loadStubbedHostResponses( ) ;
		getApplication( ).setHostStatusManager( testHostStatusManager ) ;

		ObjectNode agentFirstReport = testHostStatusManager.serviceCollectionReport( List.of( "csap-dev04" ),
				CsapConstants.AGENT_ID, null ) ;

		logger.info( "agentFirstReport: {}", CSAP.jsonPrint( agentFirstReport ) ) ;

		assertThat( agentFirstReport.at( "/csap-dev04/services/" + CsapConstants.AGENT_ID + "/serverType" ).asText( ) )
				.isEqualTo( "SpringBoot" ) ;

		ObjectNode agentAttributeReport = testHostStatusManager.serviceCollectionReport( null, CsapConstants.AGENT_ID,
				ContainerState.DEPLOYED_ARTIFACTS ) ;

		logger.info( "agentAttributeReport: {}", CSAP.jsonPrint( agentAttributeReport ) ) ;

		assertThat( agentAttributeReport.at( "/csap-dev09/services/" + CsapConstants.AGENT_ID + "/"
				+ ContainerState.DEPLOYED_ARTIFACTS + "/0" )
				.asText( ) )
						.isEqualTo( "2.0.9" ) ;

		ObjectNode globalReport = testHostStatusManager.serviceCollectionReport( null, null, null ) ;
		logger.info( "globalReport: {}", CSAP.jsonPrint( globalReport.at( "/csap-dev09/services/"
				+ CsapConstants.AGENT_ID ) ) ) ;

		assertThat(
				globalReport.at( "/csap-dev09/services/" + CsapConstants.AGENT_ID + "/containers/0/"
						+ ContainerState.DEPLOYED_ARTIFACTS ).asText( ) )
								.isEqualTo( "2.0.9" ) ;

	}

	@Test
	public void verify_platform_score ( )
		throws JsonProcessingException ,
		IOException {

		logger.info( CsapApplication.testHeader( ) ) ;

		HostStatusManager testHostStatusManager = new HostStatusManager( "admin/host-status-scorecard-test.json",
				getCsapApis( ) ) ;
		testHostStatusManager.loadStubbedHostResponses( ) ;
		getApplication( ).setHostStatusManager( testHostStatusManager ) ;

		// CSAP.setLogToDebug( Application.class.getName() ) ;
		ObjectNode score = getApplication( ).getScoreReport( ).buildAgentScoreReport( true ) ;

		logger.info( "Score: {}", CSAP.jsonPrint( score ) ) ;

		assertThat( score.path( "csap" ).asText( ) )
				.as( "Platform scorecards on landing page" )
				.isEqualTo( "9 of 13" ) ;

		// CSAP.setLogToInfo( Application.class.getName() ) ;
	}

	@Test
	public void verify_container_version_score ( )
		throws JsonProcessingException ,
		IOException {

		logger.info( CsapApplication.testHeader( ) ) ;

		HostStatusManager testHostStatusManager = new HostStatusManager( "admin/host-status-scorecard-test.json",
				getCsapApis( ) ) ;
		getApplication( ).setHostStatusManager( testHostStatusManager ) ;
		testHostStatusManager.loadStubbedHostResponses( ) ;

		ObjectNode score = getApplication( ).getScoreReport( ).buildContainerScoreReport( ) ;

		logger.info( "Score: {} ", CSAP.jsonPrint( score ) ) ;

		assertThat( score.path( "total" ).asInt( ) )
				.as( "Application  scorecards on landing page" )
				.isEqualTo( 22 ) ;

		assertThat( score.path( "kubeletTotal" ).asInt( ) )
				.isEqualTo( 9 ) ;

	}

	@Test
	public void collect_status_from_remote_hosts ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		if ( ! isSetupOk( ) )
			return ;

		// ArrayList<String> hostList = new ArrayList<>( Arrays.asList( testAdminHost1,
		// testAdminHost1 ) ) ;
		var testHosts = new ArrayList<>( Arrays.asList( "csap-dev01.***REMOVED***" ) ) ;

		HostStatusManager hostStatusManager = new HostStatusManager( getCsapApis( ), 2, testHosts ) ;

		CSAP.setLogToDebug( HostStatusManager.class.getName( ) ) ;

		hostStatusManager.refreshAndWaitForComplete( null ) ;

		CSAP.setLogToInfo( HostStatusManager.class.getName( ) ) ;
		ObjectNode hostReport = hostStatusManager.getHostAsJson( testHosts.get( 0 ) ) ;

		logger.debug( "Host Status: {}", CSAP.jsonPrint( hostReport ) ) ;

		assertThat( hostReport.at( "/hostStats/cpuCount" ).asInt( ) )
				.as( "cpuCount" )
				.isGreaterThanOrEqualTo( 1 ) ;

		hostStatusManager.updateServiceHealth( testHosts.get( 0 ), hostReport ) ;

		CSAP.setLogToInfo( HostStatusManager.class.getName( ) ) ;
		ArrayNode alerts = hostStatusManager.getAllAlerts( ) ;
		logger.info( "alerts: {}", CSAP.jsonPrint( alerts ) ) ;

		var backlogReport = hostStatusManager.hostsBacklog( testHosts, "/junit/api/" ) ;

		logger.info( "backlogReport: {} ",
				CSAP.jsonPrint( backlogReport ) ) ;

	}

	@Test
	public void find_host_with_lowest_load ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		if ( ! isSetupOk( ) )
			return ;

		ArrayList<String> hostList = new ArrayList<>( Arrays.asList( testAdminHost1, testAdminHost1 ) ) ;

		HostStatusManager testStatus = new HostStatusManager( getCsapApis( ), 2, hostList ) ;

		testStatus.refreshAndWaitForComplete( null ) ;
		CSAP.setLogToDebug( HostStatusManager.class.getName( ) ) ;
		String vmWithLowestCpu = testStatus.getHostWithLowestAttribute( hostList, "/hostStats/cpuLoad" ) ;

		CSAP.setLogToInfo( HostStatusManager.class.getName( ) ) ;

		logger.info( "vmWithLowestCpu: " + vmWithLowestCpu ) ;

		Assertions.assertTrue( hostList.contains( vmWithLowestCpu ) ) ;

	}

	@Test
	public void find_host_with_lowest_cpu ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		if ( ! isSetupOk( ) )
			return ;

		ArrayList<String> hostList = new ArrayList<>( Arrays.asList( testAdminHost1, testAdminHost1 ) ) ;

		HostStatusManager hostStatusManager = new HostStatusManager( getCsapApis( ), 2, hostList ) ;

		CSAP.setLogToDebug( HostStatusManager.class.getName( ) ) ;
		hostStatusManager.refreshAndWaitForComplete( null ) ;
		CSAP.setLogToInfo( HostStatusManager.class.getName( ) ) ;

		var host1Report = hostStatusManager.getHostAsJson( testAdminHost1 ) ;
		logger.info( "host1Report: {}", CSAP.jsonPrint( host1Report ) ) ;
		assertThat( host1Report.at( "/hostStats/processCount" ).asInt( ) ).isGreaterThan( 10 ) ;

		CSAP.setLogToDebug( HostStatusManager.class.getName( ) ) ;
		String hostWithLowestCpu = hostStatusManager.getHostWithLowestAttribute( hostList, "/hostStats/cpu" ) ;
		CSAP.setLogToInfo( HostStatusManager.class.getName( ) ) ;

		logger.info( "vmWithLowestCpu: " + hostWithLowestCpu ) ;

		assertThat( hostList.contains( hostWithLowestCpu ) ).isTrue( ) ;

	}

}
