package org.csap.agent.collection ;

import static org.assertj.core.api.Assertions.assertThat ;

import org.csap.agent.Agent_context_loaded ;
import org.csap.agent.CsapConstants ;
import org.csap.agent.CsapThinTests ;
import org.csap.agent.services.OsManager ;
import org.csap.agent.stats.HostCollector ;
import org.csap.agent.stats.MetricCategory ;
import org.csap.agent.stats.OsProcessCollector ;
import org.csap.agent.stats.OsSharedResourcesCollector ;
import org.csap.agent.stats.ServiceCollector ;
import org.csap.agent.stats.service.JmxCommonEnum ;
import org.csap.helpers.CSAP ;
import org.csap.helpers.CsapApplication ;
import org.junit.jupiter.api.BeforeAll ;
import org.junit.jupiter.api.Test ;

import com.fasterxml.jackson.databind.node.ObjectNode ;

public class Collection_no_spring extends CsapThinTests {

	@BeforeAll
	void beforeAll ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		assertThat( getApplication( ).loadDefinitionForJunits( false, Agent_context_loaded.SIMPLE_TEST_DEFINITION ) )
				.as( "No Errors or warnings" )
				.isTrue( ) ;

	}

	@Test
	public void verify_real_time_report ( ) {

		logger.info( CsapApplication.testHeader( ) ) ;

		var collectionInterval = 30 ;

		OsSharedResourcesCollector osSharedCollecter = new OsSharedResourcesCollector( getCsapApis( ),
				collectionInterval, false ) ;

		getApplication( ).metricManager( ).getOsSharedResourceCollectorMap( ).put( collectionInterval,
				osSharedCollecter ) ;
		getApplication( ).rootProjectEnvSettings( ).getMetricToSecondsMap( ).add( CsapApplication.COLLECTION_HOST,
				collectionInterval ) ;

		// shutdown to keep logs reasonable to trace during test.
		osSharedCollecter.shutdown( ) ;

		var osProcessCollector = new OsProcessCollector( getCsapApis( ), 30, false ) ;
		osProcessCollector.shutdown( ) ;
		// var osProcessCollector = getApplication().getOsProcessCollector( -1 ) ;
		CSAP.setLogToInfo( OsProcessCollector.class.getName( ) ) ;
		osProcessCollector.testCollection( ) ;
		CSAP.setLogToInfo( OsProcessCollector.class.getName( ) ) ;

		var serviceCollector = new ServiceCollector( getCsapApis( ), 30, false ) ;
		serviceCollector.shutdown( ) ;
		serviceCollector.testHttpCollection( 10000 ) ;

		getApplication( ).metricManager( ).setFirstServiceCollector( serviceCollector ) ;
		getApplication( ).metricManager( ).setFirstOsProcessCollector( osProcessCollector ) ;

		ObjectNode osSharedReport = osSharedCollecter.buildCollectionReport( false, null, 1, 0 ) ;
		logger.info( "osSharedReport disktest: {}", CSAP.jsonPrint( osSharedReport.path( "data" ).path(
				"diskTest" ) ) ) ;

		CSAP.setLogToDebug( OsManager.class.getName( ) ) ;
		var realTimeReport = getOsManager( ).buildRealTimeCollectionReport( ) ;
		CSAP.setLogToInfo( OsManager.class.getName( ) ) ;

		logger.info( "realTimeReport: {}", CSAP.jsonPrint( realTimeReport ) ) ;

		assertThat(
				realTimeReport
						.path( MetricCategory.osShared.json( ) )
						.path( "diskTest" ).asDouble( ) )
								.isGreaterThan( 1.01 ) ;

		assertThat(
				realTimeReport
						.path( MetricCategory.java.json( ) )
						.path( JmxCommonEnum.heapUsed + "_" + CsapConstants.AGENT_NAME ).asInt( ) )
								.isGreaterThan( 10 ) ;

	}

	@Test
	public void verify_os_shared_report ( ) {

		logger.info( CsapApplication.testHeader( ) ) ;

		// csapApp.shutdown();
		OsSharedResourcesCollector osSharedCollecter = new OsSharedResourcesCollector( getCsapApis( ), 30, false ) ;

		// shutdown to keep logs reasonable to trace during test.
		osSharedCollecter.shutdown( ) ;

		ObjectNode osSharedReport = osSharedCollecter.buildCollectionReport( false, null, HostCollector.SHOW_ALL_DATA,
				0 ) ;

		logger.info( "osSharedReport titles: {} ", CsapConstants.jsonPrint( osSharedReport.at(
				"/attributes/titles" ) ) ) ;

		assertThat( osSharedReport.at( "/attributes/titles/OS_MpStat" ).asText( ) )
				.as( "/attributes/titles/OS_MpStat" )
				.isEqualTo( "Linux mpstat" ) ;

		assertThat( osSharedReport.at( "/attributes/graphs/OS_MpStat/usrCpu" ).asText( ) )
				.as( "/attributes/graphs/OS_MpStat/usrCpu" )
				.isEqualTo( "User CPU" ) ;

		assertThat( osSharedReport.at( "/data/ioReads/4" ).asInt( ) )
				.as( "/data/ioReads/4" )
				.isGreaterThanOrEqualTo( 0 )
				.isLessThan( OsSharedResourcesCollector.DISK_TEST_IO_MAX ) ;

		// diskutil
		assertThat( osSharedReport.at( "/attributes/titles/ioPercentUsed" ).asText( ) )
				.as( "/attributes/titles/ioPercentUsed" )
				.isEqualTo( "Disk IO % Busy" ) ;

		assertThat( osSharedReport.at( "/attributes/graphs/ioPercentUsed/sda" ).asText( ) )
				.as( "/attributes/graphs/ioPercentUsed/sda" )
				.isEqualTo( "Device: sda" ) ;

		assertThat( osSharedReport.at( "/data/sdaPercent/4" ).asInt( ) )
				.as( "/data/sdaPercent/4" )
				.isGreaterThanOrEqualTo( 0 )
				.isLessThan( OsSharedResourcesCollector.DISK_TEST_IO_MAX ) ;

	}

}
