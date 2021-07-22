package org.csap.agent.linux ;

import java.io.BufferedWriter ;
import java.util.HashMap ;
import java.util.List ;
import java.util.Map ;
import java.util.Optional ;
import java.util.concurrent.ArrayBlockingQueue ;
import java.util.concurrent.BlockingQueue ;
import java.util.concurrent.ExecutorService ;
import java.util.concurrent.Executors ;
import java.util.concurrent.ScheduledExecutorService ;
import java.util.concurrent.ScheduledFuture ;
import java.util.concurrent.ThreadPoolExecutor ;
import java.util.concurrent.TimeUnit ;
import java.util.stream.Collectors ;
import java.util.stream.Stream ;

import org.apache.commons.lang3.concurrent.BasicThreadFactory ;
import org.csap.agent.integrations.CsapEvents ;
import org.csap.agent.model.Application ;
import org.csap.agent.model.ServiceBaseParser ;
import org.csap.agent.model.ServiceBaseParser.ServiceJob ;
import org.csap.agent.model.ServiceInstance ;
import org.csap.agent.services.OsCommands ;
import org.csap.helpers.CSAP ;
import org.csap.helpers.CsapApplication ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;

import com.fasterxml.jackson.databind.ObjectMapper ;

/**
 * 
 * 
 * Applications -> ReleasePackages -> Services -> Jobs
 * 
 * JobRunner looks for services on host with jobs scheduled at defined
 * intervals. - jobs are pushed onto another thread pool so as to not block
 * timer executions - max time jobs are allowed to run is controlled by service
 * instance timeout - jobs can be invoked on demand from UI for jobs with and
 * without scheduled invocation.
 * 
 * @author Peter Nightingale
 *
 */

public class ServiceJobRunner {

	final private Logger logger = LoggerFactory.getLogger( getClass( ) ) ;

	Application csapApplication ;

	// wakes up and checks for jobs that are scheduled to be run
	private ScheduledExecutorService jobTimerService ;

	long jobInitialDelay = 5 ;
	long jobInterval = 60 ;
	TimeUnit jobTimeUnit = TimeUnit.MINUTES ;

	// jobs invoked on separate thread pool
	private ExecutorService jobRunnerService ;
	private static final int MAX_JOBS_QUEUED = 60 ;
	private static final int MAX_JOBS_CONCURRENT = 2 ;
	volatile BlockingQueue<Runnable> jobRunnerQueue ;
	public static final String SERVICE_JOB_ID = "service-jobs." ;

	public enum Event {

		daily("daily"), hourly("hourly"), onDemand("on-demand"),

		preStart("event-pre-start"), postStart("event-post-start"),
		preDeploy("event-pre-deploy"), postDeploy("event-post-deploy"),
		preStop("event-pre-stop"), postStop("event-post-stop"),;

		public String json ( ) {

			return key ;

		}

		private String key ;

		private Event ( String key ) {

			this.key = key ;

		}

	}

	public ServiceJobRunner ( Application csapApplication ) {

		this.csapApplication = csapApplication ;

		if ( Application.isRunningOnDesktop( ) ) {

			// logger.warn( "Setting DESKTOP to seconds" );
			// logRotationTimeUnit = TimeUnit.SECONDS;
		}

		logger.warn( "Creating job runner thread - jobs will be checked for running every: {} {}.",
				jobInterval, jobTimeUnit ) ;

		BasicThreadFactory schedFactory = new BasicThreadFactory.Builder( )
				.namingPattern( "CsapLogRotation-%d" )
				.daemon( true )
				.priority( Thread.NORM_PRIORITY )
				.build( ) ;

		jobTimerService = Executors
				.newScheduledThreadPool( 1, schedFactory ) ;

		ScheduledFuture<?> jobHandle = jobTimerService
				.scheduleAtFixedRate(
						( ) -> findAndRunScheduledJobs( ),
						jobInitialDelay,
						jobInterval,
						jobTimeUnit ) ;

		logger.warn(
				"Creating job runner thread pool: {} threads.  Maximum jobs queued: {}",
				MAX_JOBS_CONCURRENT, MAX_JOBS_QUEUED ) ;

		BasicThreadFactory jobRunnerThreadFactory = new BasicThreadFactory.Builder( )
				.namingPattern( "CsapServiceJob-%d" )
				.daemon( true )
				.priority( Thread.NORM_PRIORITY + 1 )
				.build( ) ;
		//
		jobRunnerQueue = new ArrayBlockingQueue<>( MAX_JOBS_QUEUED ) ;

		jobRunnerService = new ThreadPoolExecutor(
				MAX_JOBS_CONCURRENT, MAX_JOBS_CONCURRENT,
				30, TimeUnit.SECONDS,
				jobRunnerQueue,
				jobRunnerThreadFactory ) ;

	}

	// @Scheduled(initialDelay = 10 * CSAP.ONE_SECOND_MS, fixedRate = 60 *
	// CSAP.ONE_SECOND_MS)
	public void findAndRunScheduledJobs ( ) {

		var timer = csapApplication.metrics( ).startTimer( ) ;

		try {

			csapApplication.getActiveProject( )
					.getServicesOnHost( csapApplication.getCsapHostName( ) )
					.filter( ServiceInstance::hasJobs )
					.flatMap( this::activeServiceJobEntries )
					.forEach( jobEntry -> {

						jobRunnerService.submit( ( ) -> runJob( jobEntry, null, null ) ) ;

					} ) ;

		} catch ( Exception e ) {

			logger.error( "Failed to schedule job {}", CSAP.buildCsapStack( e ) ) ;

		}

		csapApplication.metrics( ).stopTimer( timer, SERVICE_JOB_ID + "all.script.checkForActive" ) ;

	}

	/**
	 * 
	 */
	public void shutdown ( ) {

		logger.warn( "Shutting down all jobs" ) ;

		try {

			jobTimerService.shutdown( ) ;
			jobRunnerService.shutdown( ) ;

		} catch ( Exception e ) {

			logger.error( "Shutting down error {}", CSAP.buildCsapStack( e ) ) ;

		}

	}

	ObjectMapper jacksonMapper = new ObjectMapper( ) ;

	int lastDay = -1 ;
	static long NANOS_IN_SECOND = 1000 * 1000000 ;

	public Stream<Map.Entry<ServiceBaseParser.ServiceJob, ServiceInstance>> activeServiceJobEntries (
																										ServiceInstance instance ) {

		HashMap<ServiceBaseParser.ServiceJob, ServiceInstance> map = new HashMap<>( ) ;

		instance.getJobs( ).forEach( job -> {

			if ( job.isTimeToRun( ) ) {

				if ( isEnvironmentMatch( job ) && isHostMatch( job ) ) {

					map.put( job, instance ) ;

				}

			} else {

				logger.debug( "Skipping {} ", job ) ;

			}

		} ) ;

		logger.debug( "Jobs: {} ", map ) ;

		return map.entrySet( ).stream( ) ;

	}

	private boolean isEnvironmentMatch ( ServiceJob job ) {

		var environmentMatch = job.getEnvironmentFilters( ).stream( ).filter( envNameFilter -> {

			return csapApplication.getCsapHostEnvironmentName( ).matches( envNameFilter ) ;

		} ).findFirst( ) ;
		return environmentMatch.isPresent( ) ;

	}

	private boolean isHostMatch ( ServiceJob job ) {

		var hostFilterMatch = job.getHostFilters( ).stream( ).filter( hostFilter -> {

			return csapApplication.getCsapHostName( ).matches( hostFilter ) ;

		} ).findFirst( ) ;
		return hostFilterMatch.isPresent( ) ;

	}

	OsCommandRunner cleanOsCommandRunner = new OsCommandRunner( 60, 1, ServiceJobRunner.class.getName( ) ) ;

	/**
	 * 
	 * Manually triggering a job from UI
	 * 
	 */
	public String runJobUsingDescription (
											ServiceInstance serviceInstance ,
											String uiDescription ,
											String jobParameters ) {

		var description = serviceInstance.resolveRuntimeVariables( uiDescription ) ;
		String jobResult = "Did not find matching job: " + description ;

		if ( description.equals( "Log Rotation" ) ) {

			jobResult = "Log Rotation: " + csapApplication.getOsManager( ).getLogRoller( ).rotate( serviceInstance ) ;

		} else {

			Optional<ServiceBaseParser.ServiceJob> matchedJob = serviceInstance.getJobs( ).stream( )
					// always allow when manually run
					// .filter( this::isLifecycleMatch )
					.filter( job -> job.isMatchingJob( description ) )
					.findFirst( ) ;

			if ( matchedJob.isPresent( ) ) {

				var jobToRun = matchedJob.get( ) ;
				HashMap<ServiceBaseParser.ServiceJob, ServiceInstance> map = new HashMap<>( ) ;
				map.put( jobToRun, serviceInstance ) ;
				jobResult = runJob( map.entrySet( ).iterator( ).next( ), jobParameters, null ) ;

				if ( ! isEnvironmentMatch( jobToRun ) ||
						! isHostMatch( jobToRun ) ) {

					jobResult += CsapApplication
							.header( "Warning: job has filters to exclude the current lifecycle, but was triggered via ui" ) ;

				}

			}

		}

		return jobResult ;

	}

	public String runJobUsingEvent ( ServiceInstance serviceInstance , Event event , BufferedWriter outputWriter ) {

		// String jobResult //= "Did not find matching job: " + interval.json() ;

		String allJobResults = serviceInstance.getJobs( ).stream( )
				.filter( job -> job.getFrequency( ).equals( event.json( ) ) )
				.filter( this::isEnvironmentMatch )
				.filter( this::isHostMatch )
				.map( job -> {

					HashMap<ServiceBaseParser.ServiceJob, ServiceInstance> map = new HashMap<>( ) ;
					map.put( job, serviceInstance ) ;
					String jobResult = runJob( map.entrySet( ).iterator( ).next( ), null, outputWriter ) ;
					return jobResult ;

				} )
				.collect( Collectors.joining( "\n\n" ) ) ;

		if ( allJobResults == null ) {

			allJobResults = "Did not find matching job: " + event.json( ) ;

		}

		logger.debug( "results: '{}'", allJobResults ) ;

		return allJobResults ;

	}

	private String runJob (
							Map.Entry<ServiceBaseParser.ServiceJob, ServiceInstance> jobEntry ,
							String jobParameters ,
							BufferedWriter outputWriter ) {

		var allTimer = csapApplication.metrics( ).startTimer( ) ;

		var serviceTimer = csapApplication.metrics( ).startTimer( ) ;

		ServiceInstance serviceInstance = jobEntry.getValue( ) ;
		ServiceBaseParser.ServiceJob job = jobEntry.getKey( ) ;

		logger.debug( "running : {} - {}", serviceInstance.getName( ), job.getDescription( ) ) ;
		String jobResult = "not able to run" ;

		try {

			if ( job.isDiskCleanJob( ) ) {

				List<String> cleanLines = csapApplication.getOsManager( ).getOsCommands( )
						.getServiceJobsDiskClean(
								job.getPath( ),
								job.getMaxDepth( ),
								job.getOlderThenDays( ),
								job.isPruneByFolder( ),
								job.isPruneEmptyFolders( ) ) ;

				var timer = csapApplication.metrics( ).startTimer( ) ;

				jobResult = OsCommandRunner.trimHeader( cleanOsCommandRunner.runUsingDefaultUser(
						"cleanServiceDisks" + serviceInstance.getServiceName_Port( ),
						cleanLines ) ) ;

				csapApplication.metrics( ).stopTimer( timer, SERVICE_JOB_ID + "script." + serviceInstance.getName( )
						+ ".diskClean" ) ;

				jobResult = "\n Clean script: \n" + OsCommands.asScript( cleanLines ) + "\n\n Result:\n" + jobResult ;

				logger.debug( "Results from {}, \n {}", cleanLines, jobResult ) ;

			} else {

				jobResult = csapApplication
						.getOsManager( )
						.getServiceManager( )
						.runServiceJob( serviceInstance, job.getDescription( ), jobParameters, outputWriter ) ;

			}

			csapApplication.getEventClient( )
					.publishEvent( CsapEvents.CSAP_SYSTEM_SERVICE_CATEGORY + "/" + serviceInstance.getName( ) + "/job",
							"Service Job: " + job.getDescription( ), jobResult ) ;

		} catch ( Exception e ) {

			logger.error( "Failed running jobs for {} job: {}, {}",
					serviceInstance.getName( ),
					job,
					CSAP.buildCsapStack( e ) ) ;

		}

		csapApplication.metrics( ).stopTimer( allTimer, SERVICE_JOB_ID + "all.script.run" ) ;

		csapApplication.metrics( ).stopTimer( serviceTimer, SERVICE_JOB_ID + "script." + serviceInstance.getName( ) ) ;

		return jobResult ;

	}

	public static final String SIMON_ID = "java.script.scheduled." ;

}
