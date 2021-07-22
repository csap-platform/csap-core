package org.csap.agent.linux ;

import java.io.File ;
import java.nio.charset.Charset ;
import java.nio.file.Files ;
import java.util.ArrayList ;
import java.util.Arrays ;
import java.util.Calendar ;
import java.util.HashMap ;
import java.util.List ;
import java.util.Map ;
import java.util.concurrent.Executors ;
import java.util.concurrent.ScheduledExecutorService ;
import java.util.concurrent.ScheduledFuture ;
import java.util.concurrent.TimeUnit ;

import org.apache.commons.lang3.concurrent.BasicThreadFactory ;
import org.csap.agent.CsapCore ;
import org.csap.agent.integrations.CsapEvents ;
import org.csap.agent.model.Application ;
import org.csap.agent.model.ServiceBaseParser ;
import org.csap.agent.model.ServiceInstance ;
import org.csap.alerts.AlertFields ;
import org.csap.helpers.CSAP ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;

import com.fasterxml.jackson.databind.ObjectMapper ;
import com.fasterxml.jackson.databind.node.ArrayNode ;
import com.fasterxml.jackson.databind.node.ObjectNode ;

/**
 * Helper thread to trigger log rolling on Service Instances.
 * 
 * 
 * @author someDeveloper
 * 
 */
public class LogRollerRunnable {

	Logger logger = LoggerFactory.getLogger( this.getClass( ) ) ;

	static final int MINIMUM_LOG_ROTATE_SECONDS_TO_RECORD = 2 ;
	static final int initialDelayMinute = 3 ;
	static final TimeUnit logRotationTimeUnit = TimeUnit.MINUTES ;
	static final String LOG_ROLLER_SIMON_ID = ServiceJobRunner.SERVICE_JOB_ID + "logs-" ;
	static final String LOG_ROLLER_ALL = "csap." + ServiceJobRunner.SERVICE_JOB_ID + "logs" ;
	static final String LOG_ROLLER_DAILY = "csap." + ServiceJobRunner.SERVICE_JOB_ID + "logs-daily" ;

	BasicThreadFactory schedFactory = new BasicThreadFactory.Builder( )
			.namingPattern( "CsapLogRotation-%d" )
			.daemon( true )
			.priority( Thread.NORM_PRIORITY )
			.build( ) ;

	ScheduledExecutorService scheduledExecutorService = Executors
			.newScheduledThreadPool( 1, schedFactory ) ;

	public LogRollerRunnable ( Application csapApp ) {

		this.csapApp = csapApp ;
		long intervalMinutes = csapApp.rootProjectEnvSettings( ).getLogRotationMinutes( ) ;

		// if ( Application.isRunningOnDesktop() ) {
		// logger.warn( "Setting DESKTOP to seconds" );
		// logRotationTimeUnit = TimeUnit.SECONDS;
		// }

		logger.warn(
				"Scheduling logrotates to be triggered every {} {}. Logs only rotated if size exceeds threshold (default is 10mb)",
				intervalMinutes, logRotationTimeUnit ) ;

		ScheduledFuture<?> jobHandle = scheduledExecutorService
				.scheduleAtFixedRate(
						( ) -> executeLogRotateForAllServices( ),
						initialDelayMinute,
						intervalMinutes,
						logRotationTimeUnit ) ;

	}

	OsCommandRunner osCommandRunner = new OsCommandRunner( 60, 1, LogRollerRunnable.class.getName( ) ) ;
	Application csapApp ;

	/**
	 * Kill off the spawned threads - triggered from ServiceRequests
	 */
	public void shutdown ( ) {

		logger.warn( "Shutting down all jobs" ) ;

		try {

			scheduledExecutorService.shutdown( ) ;

		} catch ( Exception e ) {

			logger.error( "Shutting down error {}", CSAP.buildCsapStack( e ) ) ;

		}

	}

	Process logRotateLinuxProcess = null ;

	ObjectMapper jacksonMapper = new ObjectMapper( ) ;

	int _last_rotation_day = -1 ;
	static long NANOS_IN_SECOND = 1000 * 1000000 ;

	private int previousLogConfigurationLength = 0 ;
	private int previousLogRotationLength = 0 ;

	public void executeLogRotateForAllServices ( ) {
		// TODO Auto-generated method stub

		StringBuilder servicesWithLongRotations = new StringBuilder( ) ;
		StringBuilder configurationResults = new StringBuilder( ) ;
		StringBuilder rotationResults = new StringBuilder( ) ;

		ObjectNode dailyReport = jacksonMapper.createObjectNode( ) ;

		ArrayNode serviceArray = dailyReport.putArray( "summary" ) ;

		var allServiceTimer = csapApp.metrics( ).startTimer( ) ;
		logger.info( "Starting service log rotations" ) ;

		// Generate the log configuration file
		csapApp.getActiveProject( )
				.getServicesOnHost( csapApp.getCsapHostName( ) )
				.filter( CsapCore.not( ServiceInstance::is_files_only_package ) )
				.filter( CsapCore.not( ServiceInstance::isRemoteCollection ) )
				.map( this::generateDefaultRotateConfig )
				.forEach( configurationResults::append ) ;

		if ( configurationResults.length( ) != previousLogConfigurationLength ) {

			logger.info( "Configuration Update - Old '{}' New '{}' \n {}",
					previousLogConfigurationLength, configurationResults.length( ),
					configurationResults.toString( ) ) ;

		}

		previousLogConfigurationLength = configurationResults.length( ) ;

		// Run the log rotation for each service
		csapApp.getActiveProject( )
				.getServicesOnHost( csapApp.getCsapHostName( ) )
				.filter( CsapCore.not( ServiceInstance::is_files_only_package ) )
				.filter( CsapCore.not( ServiceInstance::isRemoteCollection ) )
				.map( service -> logRotateService( service, serviceArray, servicesWithLongRotations ) )
				.forEach( rotationResults::append ) ;

		if ( rotationResults.length( ) != previousLogRotationLength ) {

			logger.info( "Log Rotation Report {}", rotationResults.toString( ) ) ;

		}

		previousLogRotationLength = rotationResults.length( ) ;

		var nanos = csapApp.metrics( ).stopTimer( allServiceTimer, LOG_ROLLER_ALL ) ;
		csapApp.metrics( ).record( LOG_ROLLER_DAILY, nanos, TimeUnit.NANOSECONDS ) ;

		int nowDay = ( Calendar.getInstance( ) ).get( Calendar.DAY_OF_WEEK ) ;

		if ( _last_rotation_day != nowDay ) {
			// Publish summary to event service

			var dailyMeter = csapApp.metrics( ).find( LOG_ROLLER_DAILY ) ;
			var report = csapApp.metrics( ).getMeterReport( ).buildMeterReport( dailyMeter, 0, false ) ;

			csapApp.metrics( ).getSimpleMeterRegistry( ).remove( dailyMeter ) ;
			csapApp.metrics( ).record( LOG_ROLLER_DAILY, 1, TimeUnit.MILLISECONDS ) ;

			ObjectNode dailyServiceJson = dailyReport.putObject( "Total" ) ;
			dailyServiceJson.put( "Count", report.path( AlertFields.count.json ).asLong( ) ) ;
			dailyServiceJson.put( "MeanSeconds",
					Math.round( TimeUnit.MILLISECONDS.toSeconds( report.path( AlertFields.meanMs.json )
							.asLong( ) ) ) ) ;
			dailyServiceJson.put( "TotalSeconds",
					Math.round( TimeUnit.MILLISECONDS.toSeconds( report.path( AlertFields.totalMs.json )
							.asLong( ) ) ) ) ;
			csapApp.getEventClient( ).publishEvent( CsapEvents.CSAP_REPORTS_CATEGORY + "/logRotate",
					"Daily Summary", null, dailyReport ) ;

		}

		_last_rotation_day = nowDay ;

		if ( servicesWithLongRotations.length( ) > 0 ) {

			logger.warn( "\n *** Services with rotations taking more then 3 seconds:\n {}",
					servicesWithLongRotations ) ;

			csapApp.getEventClient( ).publishEvent(
					CsapEvents.CSAP_SYSTEM_CATEGORY + "/logrotate", "Service with rotations",
					servicesWithLongRotations.toString( ) ) ;

		}

	}

	public String rotate ( ServiceInstance serviceInstance ) {

		StringBuilder results = new StringBuilder( "UI Request\n" ) ;
		results.append( generateDefaultRotateConfig( serviceInstance ) ) ;
		results.append( logRotateService( serviceInstance, null, null ) ) ;
		return results.toString( ) ;

	}

	private String logRotateService (
										ServiceInstance serviceInstance ,
										ArrayNode serviceArray ,
										StringBuilder servicesWithLongRotations ) {

		StringBuilder informationOutput = new StringBuilder( "\n\t" + serviceInstance.paddedId( ) + ": " ) ;

		try {

			var serviceLogRotateTimerMs = System.currentTimeMillis( ) ;

			File serviceLogDirectory = serviceInstance.getLogWorkingDirectory( ) ;

			boolean isRotated = false ;

			// Check for config file

			File logRotateConfigFile = getLogRotateConfigurationFile( serviceInstance ) ;

			if ( ! logRotateConfigFile.exists( ) ) {

				logger.debug( "Skipping: {},  log rotation configuration does not exist: {}",
						serviceInstance.getName( ), logRotateConfigFile.toPath( ) ) ;

				return informationOutput.append( " log rotation configuration does not exist: " + logRotateConfigFile
						.toPath( ) )
						.toString( ) ;

			}

			String[] logRotateLines = {
					"#!/bin/bash",
					"set -x",
					"/usr/sbin/logrotate -v -s " + serviceLogDirectory.getAbsolutePath( )
							+ "/logRotate.state "
							+ serviceLogDirectory.getAbsolutePath( )
							+ "/logRotate.config",
					""
			} ;

			String rotateResults = "notRun" ;

			if ( serviceInstance.isRunningAsRoot( ) ) {

				String[] permissionsLines = {
						"#!/bin/bash",
						"set -x",
						"chown root " + serviceLogDirectory.getAbsolutePath( ) + "/logRotate.config",
						"chmod 644  " + serviceLogDirectory.getAbsolutePath( ) + "/logRotate.config",
						// "chmod 755 " +
						// serviceLogDirectory.getAbsolutePath() + "/*",
						""
				} ;
				String results = osCommandRunner.runUsingRootUser( "backup" + serviceInstance.getServiceName_Port( ),
						Arrays.asList( permissionsLines ) ) ;
				logger.debug( "Root Results from {}, \n {}", Arrays.asList( permissionsLines ), results ) ;

				rotateResults = osCommandRunner.runUsingRootUser( "backup" + serviceInstance.getServiceName_Port( ),
						Arrays.asList( logRotateLines ) ) ;

			} else {

				// String results = executeString( parmList ).toString();
				rotateResults = osCommandRunner.runUsingDefaultUser(
						"backup" + serviceInstance.getServiceName_Port( ),
						Arrays.asList( logRotateLines ) ) ;

			}

			logger.debug( "Results from {}, \n {}", Arrays.asList( logRotateLines ), rotateResults ) ;

			try {

				// Leave some space between rotates to not overwhelm the
				// system
				Thread.sleep( 1000 ) ;

			} catch ( InterruptedException e ) {

				logger.error( "Log rotation error", e ) ;
				;

			}

			serviceLogRotateTimerMs = System.currentTimeMillis( ) - serviceLogRotateTimerMs ;

			// var nanos = csapApp.metrics().stopTimer( serviceLogRotateTimerMs,
			// LOG_ROLLER_SIMON_ID + serviceInstance.getServiceName() ) ;
			var numSeconds = TimeUnit.MILLISECONDS.toSeconds( serviceLogRotateTimerMs ) ;

			if ( rotateResults.contains( "compressing" ) ) {

				isRotated = true ;

			}

			if ( serviceArray == null || servicesWithLongRotations == null ) {

				logger.info( "Bypassing instrumentation calls for manual invokation" ) ;
				// add in debug output
				informationOutput.append( "Results from: " + Arrays.asList( logRotateLines ) + "\n Results: \n"
						+ rotateResults ) ;

			} else {

				if ( isRotated ) {

					if ( numSeconds > MINIMUM_LOG_ROTATE_SECONDS_TO_RECORD ) {

						csapApp.metrics( ).record(
								LOG_ROLLER_SIMON_ID + serviceInstance.getName( ),
								numSeconds,
								TimeUnit.SECONDS ) ;

						servicesWithLongRotations.append( "\t" + serviceInstance.getName( )
								+ " logrotate duration: " + numSeconds + "\n" ) ;

					}

				}

				int nowDay = ( Calendar.getInstance( ) ).get( Calendar.DAY_OF_WEEK ) ;

				if ( _last_rotation_day != nowDay ) {

					var meter = csapApp.metrics( ).find( LOG_ROLLER_SIMON_ID + serviceInstance.getName( ) ) ;

					if ( meter != null ) {

						var report = csapApp.metrics( ).getMeterReport( ).buildMeterReport( meter, 0, false ) ;

						csapApp.metrics( ).getSimpleMeterRegistry( ).remove( meter ) ;
						csapApp.metrics( ).record( LOG_ROLLER_SIMON_ID + serviceInstance.getName( ), 1,
								TimeUnit.MILLISECONDS ) ;

						ObjectNode dailyServiceJson = serviceArray.addObject( ) ;
						dailyServiceJson.put( "serviceName", serviceInstance.getName( ) ) ;
						dailyServiceJson.put( "Count", report.path( AlertFields.count.json ).asLong( ) ) ;
						dailyServiceJson.put( "MeanSeconds",
								Math.round( TimeUnit.MILLISECONDS.toSeconds( report.path( AlertFields.meanMs.json )
										.asLong( ) ) ) ) ;
						dailyServiceJson.put( "TotalSeconds",
								Math.round( TimeUnit.MILLISECONDS.toSeconds( report.path( AlertFields.totalMs.json )
										.asLong( ) ) ) ) ;

					}

				}

			}

		} catch ( Exception e ) {

			logger.error( "{} Failed logrotate: {}", serviceInstance.getName( ),
					CSAP.buildCsapStack( e ) ) ;

		}

		return informationOutput.toString( ) ;

	}

	private File getLogRotateConfigurationFile ( ServiceInstance service ) {

		return new File( service.getLogWorkingDirectory( ), "logRotate.config" ) ;

	}

	public Map<String, Long> logFileLastModified = new HashMap<>( ) ;

	private String generateDefaultRotateConfig ( ServiceInstance serviceInstance ) {

		StringBuilder progress = new StringBuilder( "\n\t" + serviceInstance.paddedId( ) + ": " ) ;
		File logRotateConfigFile = getLogRotateConfigurationFile( serviceInstance ) ;

		if ( ! logRotateConfigFile.getParentFile( ).exists( ) ) {

			// logger.info( "{} log folder does not exist: {}",
			// serviceInstance.getServiceName(),
			// logRotateConfigFile.getParentFile().toPath() );
			return progress.append( " log folder does not exist: " + logRotateConfigFile.getParentFile( ).toPath( ) )
					.toString( ) ;

		}

		List<String> configurationLines = new ArrayList<>( ) ;

		if ( ! serviceInstance.getLogsToRotate( ).isEmpty( ) ) {

			if ( logRotateConfigFile.exists( ) ) {

				progress.append( "\n Deleting existing policy and recreating: " + logRotateConfigFile.getParentFile( )
						.toPath( ) ) ;
				logRotateConfigFile.delete( ) ;

			}

			// if ( logRotateConfigFile.exists() && logFileLastModified.containsKey(
			// serviceInstance.getServiceName_Port() )
			// && logRotateConfigFile.lastModified() > logFileLastModified.get(
			// serviceInstance.getServiceName_Port() ) ) {
			// // logger.info( "Skipping logRotateConfigFile not modified: {}",
			// // logRotateConfigFile.getAbsolutePath() );
			// return progress.append( " settings not modified: " +
			// logRotateConfigFile.getParentFile().toPath() ).toString();
			// }
			// logFileLastModified.put( serviceInstance.getServiceName_Port(),
			// logRotateConfigFile.lastModified() );
			serviceInstance
					.getLogsToRotate( )
					.stream( )
					.filter( ServiceBaseParser.LogRotation::isActive )
					.forEach( logRotation -> {

						if ( configurationLines.isEmpty( ) ) {

							configurationLines
									.add( "# created using csap application definition for service " + serviceInstance
											.getName( ) ) ;
							configurationLines.add( "# DO NOT MODIFY" ) ;
							configurationLines.add( "# This will be regenerated using CSAP on every rotation" ) ;
							configurationLines.add( "" ) ;

						}

						configurationLines.add( logRotation.getPath( ) + " {" ) ;

						String[] settings = logRotation.getSettings( ).split( "," ) ;

						for ( String setting : settings ) {

							configurationLines.add( setting ) ;

						}

						configurationLines.add( "}" ) ;
						configurationLines.add( "" ) ;

					} ) ;

		} else if ( ! logRotateConfigFile.exists( ) && ( serviceInstance.getProcessRuntime( ).isJava( ) ) ) {

			progress.append( "\n Generating default log policy for java " ) ;

			configurationLines.addAll(
					buildDefaultPolicy(
							logRotateConfigFile.getParent( ) + "/" + serviceInstance.getDefaultLogToShow( ) ) ) ;

		}

		if ( ! configurationLines.isEmpty( ) ) {

			try {

				logger.debug( "{} Creating: {}",
						serviceInstance.getName( ),
						logRotateConfigFile.toPath( ) ) ;

				StringBuilder details = new StringBuilder( "Updated configurationfile: " + logRotateConfigFile.toPath( )
						+ "\n\n" ) ;
				configurationLines.stream( ).forEach( line -> {

					details.append( line ) ;
					details.append( "\n" ) ;

				} ) ;

				// csapApp.getEventClient()
				// .generateEvent( CsapEventClient.CSAP_SVC_CATEGORY + "/" +
				// serviceInstance.getServiceName(),
				// csapApp.lifeCycleSettings().getAgentUser(), "Updated log configuration file",
				// details.toString() );

				progress.append( " updating configuration: " + logRotateConfigFile.getParentFile( ).toPath( ) ) ;
				Files.write( logRotateConfigFile.toPath( ), configurationLines, Charset.forName( "UTF-8" ) ) ;

			} catch ( Exception e ) {

				logger.warn( "Failed creating configuration file: {} reason: {}",
						logRotateConfigFile.getAbsolutePath( ),
						CSAP.buildCsapStack( e ) ) ;

			}

		} else {

			progress.append( " - " ) ;

		}

		return progress.toString( ) ;

	}

	final static List<String> LOG_DEFAULT_ROTATE_CONFIGURATION = Arrays
			.asList( "copytruncate",
					"weekly",
					"rotate 3",
					"compress",
					"missingok",
					"size 10M",
					"}",
					"" ) ;

	private List<String> buildDefaultPolicy ( String logPath ) {

		List<String> rotatePolicy = new ArrayList<>( ) ;

		rotatePolicy.add( "# DEFAULT LOG Policy" ) ;
		rotatePolicy.add( "# RECOMMENDED: add the rotation settings to the CSAP service definition jobs" ) ;

		rotatePolicy.add( logPath + " {" ) ;
		rotatePolicy.addAll( LOG_DEFAULT_ROTATE_CONFIGURATION ) ;

		return rotatePolicy ;

	}

}
