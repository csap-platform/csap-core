package org.csap.agent.integration.linux ;

import static org.assertj.core.api.Assertions.assertThat ;

import java.io.File ;
import java.util.Arrays ;
import java.util.List ;
import java.util.Map ;
import java.util.stream.Collectors ;

import org.csap.agent.Agent_context_loaded ;
import org.csap.agent.CsapThinTests ;
import org.csap.agent.model.HealthManager ;
import org.csap.agent.model.ServiceAlertsEnum ;
import org.csap.agent.model.ServiceInstance ;
import org.csap.agent.stats.OsProcessEnum ;
import org.csap.helpers.CSAP ;
import org.csap.helpers.CsapApplication ;
import org.junit.jupiter.api.BeforeAll ;
import org.junit.jupiter.api.Test ;
import org.junit.jupiter.api.TestInstance ;

import com.fasterxml.jackson.core.type.TypeReference ;
import com.fasterxml.jackson.databind.JsonNode ;
import com.fasterxml.jackson.databind.node.ObjectNode ;
import com.google.gson.Gson ;

@TestInstance ( TestInstance.Lifecycle.PER_CLASS )
public class Host_Alert_Processing extends CsapThinTests {

	String TEST_SERVICE_NAME = "CsapTestDocker" ;
	String TEST_SERVICE_PORT = TEST_SERVICE_NAME + "_8261" ;
	String TEST_HOST_IN_DEFINTION = "csapTest-six" ;

	HealthManager healthManager ;

	File csapApplicationDefinition = new File(
			Host_Status_Manager_Test.class.getResource( "host-status-test-app.json" ).getPath( ) ) ;

	@BeforeAll
	void beforeAll ( )
		throws Exception {

		getApplication( ).setHostNameForDefinitionMapping( TEST_HOST_IN_DEFINTION ) ;

		// hostAlertProcessor = new HostAlertProcessor( getApplication(),
		// getApplication().getEventClient(), null ) ;
		healthManager = new HealthManager( getApplication( ), getJsonMapper( ) ) ;

		getApplication( ).getProjectLoader( ).setAllowLegacyNames( true ) ;

		assertThat( getApplication( ).loadDefinitionForJunits( false, csapApplicationDefinition ) )
				.as( "No Errors or warnings" )
				.isTrue( ) ;

		logger.info( CsapApplication.SETUP_HEAD + "Using: {}",
				Agent_context_loaded.SIMPLE_TEST_DEFINITION.getAbsolutePath( ) ) ;

	}

	@Test
	public void verify_definition ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		logger.debug( "{}", getApplication( ).getServicesOnHost( ).toString( ) ) ;

		assertThat( getApplication( ).getServicesOnHost( ).size( ) )
				.as( "Number of services on host" )
				.isEqualTo( 15 ) ;

		List<String> serviceNames = getApplication( )
				.getActiveProject( )
				.getServicesOnHost( TEST_HOST_IN_DEFINTION )
				.map( ServiceInstance::getName )
				.collect( Collectors.toList( ) ) ;

		logger.info( "serviceNames: {}", serviceNames.toString( ) ) ;

		assertThat( serviceNames.size( ) )
				.as( "Number of services on host" )
				.isEqualTo( 15 ) ;

		assertThat( getApplication( ).getServiceInstanceCurrentHost( "TestService_8241" ) )
				.as( "TestService_8241 found" )
				.isNotNull( ) ;

		assertThat( getApplication( ).getServiceInstanceCurrentHost( TEST_SERVICE_PORT ) )
				.as( "CsapTestDocker_8261 found" )
				.isNotNull( ) ;

		assertThat( getApplication( )
				.getServiceInstanceCurrentHost( TEST_SERVICE_PORT )
				.getMonitors( ).get( ServiceAlertsEnum.diskSpace.maxId( ) ).asText( ) )
						.as( "CsapTestDocker_8261 found" )
						.isEqualTo( "1g" ) ;

	}

	@Test
	void verify_service_alert_limits_overrides ( ) {

		logger.info( CsapApplication.testHeader( ) ) ;

		var hsqlService = getApplication( ).findFirstServiceInstanceInLifecycle( "HsqlDatabase" ) ;

		logger.info( "hsql monitors: {}", CSAP.jsonPrint( hsqlService.getMonitors( ) ) ) ;

		Map<String, String> hsqlLimits = ServiceAlertsEnum.limitsForService( hsqlService, getApplication( )
				.rootProjectEnvSettings( ) ) ;
		logger.info( "hsql limits: {}", hsqlLimits ) ;

		assertThat( hsqlLimits.get( OsProcessEnum.FILE_COUNT ) )
				.as( "global default from settings" )
				.isEqualTo( "350" ) ;

		assertThat( hsqlLimits.get( OsProcessEnum.SOCKET_COUNT ) )
				.as( "global default from code" )
				.isEqualTo( "30" ) ;

		assertThat( hsqlLimits.get( OsProcessEnum.DISK_USED_IN_MB ) )
				.as( "service definition limit" )
				.isEqualTo( "800m" ) ;

		assertThat( hsqlLimits.get( OsProcessEnum.RSS_MEMORY ) )
				.as( "cluster definition default" )
				.isEqualTo( "1" ) ;

		var nginxService = getApplication( ).findFirstServiceInstanceInLifecycle( "nginx" ) ;

		Map<String, String> nginxLimts = ServiceAlertsEnum.limitsForService( nginxService, getApplication( )
				.rootProjectEnvSettings( ) ) ;
		logger.info( "nginxLimts limits: {}", nginxLimts ) ;

		assertThat( nginxLimts.get( OsProcessEnum.RSS_MEMORY ) )
				.as( "cluster definition default" )
				.isEqualTo( "512000" ) ;

	}

	@Test
	void verify_kill_runaways ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		ServiceInstance csapTest = getApplication( )
				.getServiceInstanceCurrentHost( TEST_SERVICE_PORT ) ;

		// This gets fields that are normalized
		ObjectNode response_test_data = (ObjectNode) getJsonMapper( )
				.readTree( new File( getClass( )
						.getResource( "host-status-response.json" )
						.toURI( ) ) ) ;

		JsonNode serviceData = response_test_data.at( "/" + TEST_HOST_IN_DEFINTION + "/services/"
				+ TEST_SERVICE_PORT ) ;
		logger.info( "service runtime data from file: {}", CSAP.jsonPrint( serviceData ) ) ;

		ServiceInstance csapTest_loaded = ServiceInstance.buildInstance( getJsonMapper( ), serviceData ) ;
		csapTest.mergeContainerData( csapTest_loaded.getContainerStatusList( ) ) ;
		logger.info( "testService: {}", csapTest ) ;
		logger.info( "containers: {}", csapTest.getContainerStatusList( ) ) ;

		logger.info( "Updated '{}', runtime data: \t active: '{}' \t matcher: '{}' \t path: '{}' \t diskUsage: '{}' ",
				csapTest.getServiceName_Port( ),
				csapTest.getDefaultContainer( ).isInactive( ),
				csapTest.getDiskUsageMatcher( null ),
				csapTest.getDiskUsagePath( ),
				csapTest.getDefaultContainer( ).getDiskUsageInMb( ) ) ;

		assertThat( csapTest_loaded.getDefaultContainer( ).getDiskUsageInMb( ) )
				.as( "Test service disk usage" )
				.isEqualTo( 2799 ) ;

		List<String> servicesNeedingToBeKilled = healthManager
				.findServicesWithResourceRunaway( )
				.map( ServiceInstance::getServiceName_Port )
				.collect( Collectors.toList( ) ) ;

		logger.info( "servicesNeedingToBeKilled: {}", servicesNeedingToBeKilled.toString( ) ) ;

		assertThat( servicesNeedingToBeKilled )
				.as( "Test service disk usage" )
				.contains( TEST_SERVICE_PORT ) ;

		healthManager.killRunaways( ) ;

	}

	@Test
	public void verify_traffic_stop_no_alert ( )
		throws Exception {

		logger.info( CsapApplication.header( "verify_traffic_stop_no_alert" ) ) ;

		ObjectNode response_test_data = (ObjectNode) getJsonMapper( )
				.readTree( new File( getClass( )
						.getResource( "host-status-response.json" )
						.toURI( ) ) ) ;

		ObjectNode alerts = healthManager.alertsBuilder(
				1.0, true,
				TEST_HOST_IN_DEFINTION,
				(ObjectNode) response_test_data.get( TEST_HOST_IN_DEFINTION ), null ) ;

		logger.info( CSAP.jsonPrint( alerts ) ) ;

		List<String> errorList = getJsonMapper( ).readValue(
				alerts.get( HealthManager.HEALTH_SUMMARY ).toString( ),
				new TypeReference<List<String>>( ) {
				} ) ;

		// Gson gson = new Gson()
		String[] errorArray = new Gson( ).fromJson( alerts.get( HealthManager.HEALTH_SUMMARY ).toString( ),
				String[].class ) ;

		// logger.info( "errorList: {}, \n errorArray: {}", errorList, Arrays.asList(
		// errorArray ) ) ;

		assertThat( errorArray )
				.as( "disk space" )
				.contains( "csapTest-six: csap-test-docker-traffic: Disk Space: 97.66 gb, Alert threshold: 2.0 gb" ) ;

		assertThat( errorArray )
				.as( "abort message" )
				.doesNotContain(
						"csapTest-six: csap-test-docker-traffic: no active OS process found, and no user stop was issued. Probable crash or runaway resource kill: review logs." ) ;

	}

	@Test
	public void verify_process_crash_alert ( )
		throws Exception {

		logger.info( CsapApplication.header( "verify_process_crash_alert" ) ) ;

		ObjectNode response_test_data = (ObjectNode) getJsonMapper( )
				.readTree( new File( getClass( )
						.getResource( "host-status-response.json" )
						.toURI( ) ) ) ;

		ObjectNode alerts = healthManager.alertsBuilder(
				1.0, true,
				TEST_HOST_IN_DEFINTION,
				(ObjectNode) response_test_data.get( TEST_HOST_IN_DEFINTION ),
				null ) ;

		logger.info( CSAP.jsonPrint( alerts ) ) ;

		List<String> errorList = getJsonMapper( ).readValue(
				alerts.get( HealthManager.HEALTH_SUMMARY ).toString( ),
				new TypeReference<List<String>>( ) {
				} ) ;

		// Gson gson = new Gson()
		String[] errorArray = new Gson( ).fromJson( alerts.get( HealthManager.HEALTH_SUMMARY ).toString( ),
				String[].class ) ;

		logger.info( "errorList: {}, \n errorArray: {}", errorList, Arrays.asList( errorArray ) ) ;

		assertThat( errorArray )
				.as( "disk space" )
				.contains( "csapTest-six: TestService: Disk Space: 97.66 gb, Alert threshold: 100.0 mb" ) ;

		assertThat( errorArray )
				.as( "abort message" )
				.contains(
						"csapTest-six: TestService: no active OS process found, and no user stop was issued. Probable crash or runaway resource kill: review logs." ) ;

	}

	@Test
	public void verify_health_report ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		ObjectNode response_test_data = (ObjectNode) getJsonMapper( )
				.readTree( new File( getClass( )
						.getResource( "host-status-response.json" )
						.toURI( ) ) ) ;

		// CSAP.setLogToDebug( HealthManager.class.getName() ) ;
		// CSAP.setLogToDebug( ServiceAlertsEnum.class.getName() ) ;

		ObjectNode alerts = healthManager.alertsBuilder(
				1.0, true,
				TEST_HOST_IN_DEFINTION,
				(ObjectNode) response_test_data.get( TEST_HOST_IN_DEFINTION ),
				null ) ;

		CSAP.setLogToInfo( ServiceAlertsEnum.class.getName( ) ) ;
		CSAP.setLogToInfo( HealthManager.class.getName( ) ) ;

		logger.info( "alerts: {}", CSAP.jsonPrint( alerts ) ) ;

		List<String> errorList = getJsonMapper( ).readValue(
				alerts.get( HealthManager.HEALTH_SUMMARY ).toString( ),
				new TypeReference<List<String>>( ) {
				} ) ;

		// Gson gson = new Gson()
		String[] errorArray = new Gson( ).fromJson( alerts.get( HealthManager.HEALTH_SUMMARY ).toString( ),
				String[].class ) ;

		logger.info( "errorList: {}, \n errorArray: {}", errorList, Arrays.asList( errorArray ) ) ;

		// assertThat( alerts.get( HealthManager.HEALTH_SUMMARY ).toString() )
		// .as( "Application Heartbeat failure" )
		// .contains(
		// "csapTest-six: CsAgent: failure: (1 of 1) , view: service live:
		// http://csapTest-six.yourorg.org:8011/CsAgent/csap/health" ) ;

		assertThat( errorArray )
				.as( "service: CsapTestDocker disk alert" )
				.contains( "csapTest-six: CsapTestDocker: Disk Space: 2.73 gb, Alert threshold: 1024.0 mb" ) ;

		assertThat( errorArray )
				.as( "host: csapTest-six disk alert" )
				.contains( "csapTest-six:  Disk usage on /junit is: 91%, max: 85%" ) ;

		assertThat( errorArray )
				.as( "host: csapTest-six ignored disk alert" )
				.doesNotContain( "csapTest-six:  Disk usage on /ignored-junit is: 92%, max: 85%" ) ;

	}

	@Test
	public void verify_disk_alerts_for_service_and_host ( )
		throws Exception {

		logger.info( CsapApplication.header( "verify_disk_alerts_for_service_and_host" ) ) ;

		ObjectNode response_test_data = (ObjectNode) getJsonMapper( )
				.readTree( new File( getClass( )
						.getResource( "host-status-response.json" )
						.toURI( ) ) ) ;

		ObjectNode alerts = healthManager.alertsBuilder(
				1.0, true,
				TEST_HOST_IN_DEFINTION,
				(ObjectNode) response_test_data.get( TEST_HOST_IN_DEFINTION ),
				null ) ;

		logger.info( CSAP.jsonPrint( alerts ) ) ;

		List<String> errorList = getJsonMapper( ).readValue(
				alerts.get( HealthManager.HEALTH_SUMMARY ).toString( ),
				new TypeReference<List<String>>( ) {
				} ) ;

		// Gson gson = new Gson()
		String[] errorArray = new Gson( ).fromJson( alerts.get( HealthManager.HEALTH_SUMMARY ).toString( ),
				String[].class ) ;

		logger.info( "errorList: {}, \n errorArray: {}", errorList, Arrays.asList( errorArray ) ) ;

		assertThat( errorArray )
				.as( "service: CsapTestDocker disk alert" )
				.contains( "csapTest-six: CsapTestDocker: Disk Space: 2.73 gb, Alert threshold: 1024.0 mb" ) ;

		assertThat( errorArray )
				.as( "host: csapTest-six disk alert" )
				.contains( "csapTest-six:  Disk usage on /junit is: 91%, max: 85%" ) ;

		assertThat( errorArray )
				.as( "host: csapTest-six ignored disk alert" )
				.doesNotContain( "csapTest-six:  Disk usage on /ignored-junit is: 92%, max: 85%" ) ;

	}

	@Test
	public void verify_alerts_builder ( )
		throws Exception {

		logger.info( CsapApplication.header( "verify_alerts_builder" ) ) ;

		ObjectNode response_test_data = (ObjectNode) getJsonMapper( )
				.readTree( new File( getClass( )
						.getResource( "host-status-response.json" )
						.toURI( ) ) ) ;

		ObjectNode alerts = healthManager.alertsBuilder(
				0.5, true,
				TEST_HOST_IN_DEFINTION,
				(ObjectNode) response_test_data.get( TEST_HOST_IN_DEFINTION ),
				null ) ;

		logger.info( CSAP.jsonPrint( alerts ) ) ;

		assertThat( alerts.path( HealthManager.HEALTH_SUMMARY ).toString( ) )
				.as( "ActiveMq thread error" )
				.contains( "csapTest-six: ActiveMq: OS Threads: 69, Alert threshold: 60",
						"csapTest-six: csap-agent: Memory (RSS): 912.0 mb, Alert threshold: 400.0 mb" ) ;

	}

}
