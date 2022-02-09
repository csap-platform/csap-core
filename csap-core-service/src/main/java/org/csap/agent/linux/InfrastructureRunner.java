package org.csap.agent.linux ;

import java.util.Arrays ;
import java.util.List ;
import java.util.Random ;
import java.util.concurrent.Executors ;
import java.util.concurrent.ScheduledExecutorService ;
import java.util.concurrent.ScheduledFuture ;
import java.util.concurrent.TimeUnit ;

import org.apache.commons.lang3.concurrent.BasicThreadFactory ;
import org.csap.agent.CsapApis ;
import org.csap.agent.model.Application ;
import org.csap.agent.model.EnvironmentSettings ;
import org.csap.alerts.AlertFields ;
import org.csap.helpers.CSAP ;
import org.csap.helpers.CsapApplication ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;

public class InfrastructureRunner {

	final private Logger logger = LoggerFactory.getLogger( getClass( ) ) ;

	private static final String INFRASTRUCTURE_TEST_SIMON_ID = "csap.infrastructure-" ;
	public static final String INFRASTRUCTURE_TEST_DISK = INFRASTRUCTURE_TEST_SIMON_ID + "disk" ;
	public static final String INFRASTRUCTURE_TEST_CPU = INFRASTRUCTURE_TEST_SIMON_ID + "cpu" ;

	BasicThreadFactory schedFactory = new BasicThreadFactory.Builder( )

			.namingPattern( "CsapLogRotation-%d" )
			.daemon( true )
			.priority( Thread.NORM_PRIORITY )
			.build( ) ;
	// limit Log Rolling to a single thread.
	ScheduledExecutorService scheduledExecutorService = Executors
			.newScheduledThreadPool( 1, schedFactory ) ;

	CsapApis csapApis ;

	public InfrastructureRunner ( CsapApis csapApis ) {

		this.csapApis = csapApis ;

	}

	private ScheduledFuture<?> diskTestJob = null ;
	private ScheduledFuture<?> cpuTestJob = null ;

	boolean showDiskOutputOnce = false ;
	boolean showCpuOutputOnce = false ;

	public void scheduleInfrastructure ( ) {

		showDiskOutputOnce = true ;
		showCpuOutputOnce = true ;
		EnvironmentSettings.InfraTests infraTestSettings = csapApis.application( ).rootProjectEnvSettings( )
				.getInfraTests( ) ;

		if ( diskTestJob != null ) {

			logger.info( "Cancelling previous test schedule" ) ;
			diskTestJob.cancel( false ) ;
			cpuTestJob.cancel( false ) ;

		}

		// avoid hitting infra from large number of hosts simultaniously. Spread
		// it out
		Random initializeRandom = new Random( ) ;
		long initialDelaySeconds = 60 + initializeRandom.nextInt( 60 * 4 ) ;
		long diskIntervalSeconds = infraTestSettings.getDiskIntervalMinutes( ) * 60 ;
		long cpuIntervalSeconds = infraTestSettings.getCpuIntervalMinutes( ) * 60 ;

		// TimeUnit testTimeUnit = TimeUnit.MINUTES;

		if ( Application.isRunningOnDesktop( ) ) {

			logger.warn( "Setting DESKTOP to run in seconds" ) ;
			diskIntervalSeconds = diskIntervalSeconds / 60 ;
			cpuIntervalSeconds = cpuIntervalSeconds / 60 ;
			initialDelaySeconds = initializeRandom.nextInt( 5 ) ;

		}

		logger.warn(
				"Scheduling tests\n\t disk interval: {} {} \n\t cpu interval: {} {}.\n\t Random Initial Delay: {} {}",
				diskIntervalSeconds, TimeUnit.SECONDS,
				cpuIntervalSeconds, TimeUnit.SECONDS,
				initialDelaySeconds, TimeUnit.SECONDS ) ;

		diskTestJob = scheduledExecutorService
				.scheduleAtFixedRate(
						( ) -> runDiskTest( infraTestSettings.getDiskWriteMb( ) ),
						initialDelaySeconds,
						diskIntervalSeconds,
						TimeUnit.SECONDS ) ;

		cpuTestJob = scheduledExecutorService
				.scheduleAtFixedRate(
						( ) -> runCpuTest( infraTestSettings.getCpuLoopsMillions( ) ),
						initialDelaySeconds,
						cpuIntervalSeconds,
						TimeUnit.SECONDS ) ;

	}

	OsCommandRunner osCommandRunner = new OsCommandRunner( 60, 1, getClass( ).getName( ) ) ;

	public double getLastDiskTimeInSeconds ( ) {

		double collectionMs = 0.0 ;

		var meter = csapApis.metrics( ).find( INFRASTRUCTURE_TEST_DISK ) ;

		if ( meter != null ) {

			var report = csapApis.metrics( ).getMeterReport( ).buildMeterReport( meter, 0, false ) ;
			collectionMs = report.path( AlertFields.meanMs.json ).asLong( ) ;

		}

		return collectionMs / 1000d ;

	}

	int WRITE_BLOCK_SIZE_1MB = 1000 * 1000 ;

	private void runDiskTest ( int diskInMb ) {

		var timer = csapApis.metrics( ).startTimer( ) ;

		try {

			// String[] diskTestScript = buildDiskScript(
			// csapApis.application().getOsManager().getOsCommands(), diskInMb ) ;

			List<String> diskTestScript = csapApis.osManager( ).getOsCommands( )
					.getInfraTestDisk( Integer.toString( WRITE_BLOCK_SIZE_1MB ), Long.toString( diskInMb ) ) ;

			String testResults = osCommandRunner.runUsingDefaultUser( "diskTest", diskTestScript ) ;

			if ( showDiskOutputOnce ) {

				logger.info( "One time display: {}" + CsapApplication.header( "{}" ),
						Arrays.asList( diskTestScript ),
						osCommandRunner.trimHeader( testResults ) ) ;
				showDiskOutputOnce = false ;

			}

		} catch ( Exception e ) {

			logger.warn( "Failed disk test execution: {}", CSAP.buildCsapStack( e ) ) ;

		}

		csapApis.metrics( ).stopTimer( timer, INFRASTRUCTURE_TEST_DISK ) ;

	}

	public double getLastCpuTimeInSeconds ( ) {

		double collectionMs = 0.0 ;

		var meter = csapApis.metrics( ).find( INFRASTRUCTURE_TEST_CPU ) ;

		if ( meter != null ) {

			var report = csapApis.metrics( ).getMeterReport( ).buildMeterReport( meter, 0, false ) ;
			collectionMs = report.path( AlertFields.meanMs.json ).asLong( ) ; // TimeUnit.NANOSECONDS.toMillis(
																				// lastResult ) / 1000d ;

		}

		return collectionMs / 1000d ;

	}

	private void runCpuTest ( int cpuLoopsMillions ) {

		var timer = csapApis.metrics( ).startTimer( ) ;

		try {

			long numLoops = cpuLoopsMillions * 1000000 ;
			List<String> cpuTestScript = csapApis.osManager( ).getOsCommands( )
					.getInfraTestCpu( Long.toString( numLoops ) ) ;

			String testResults = osCommandRunner.runUsingDefaultUser( "cpuTest", cpuTestScript ) ;

			if ( showCpuOutputOnce ) {

				logger.info( "One time display: {}" + CsapApplication.header( "{}" ),
						Arrays.asList( cpuTestScript ),
						osCommandRunner.trimHeader( testResults ) ) ;
				showCpuOutputOnce = false ;

			}

		} catch ( Exception e ) {

			logger.warn( "Failed cpu test execution: {}", CSAP.buildCsapStack( e ) ) ;

		}

		csapApis.metrics( ).stopTimer( timer, INFRASTRUCTURE_TEST_CPU ) ;

	}
}
