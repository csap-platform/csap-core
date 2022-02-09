package org.csap.agent.integrations ;

import java.io.File ;
import java.text.DateFormat ;
import java.text.SimpleDateFormat ;
import java.util.Arrays ;
import java.util.Date ;
import java.util.Iterator ;
import java.util.List ;
import java.util.Random ;
import java.util.concurrent.ArrayBlockingQueue ;
import java.util.concurrent.BlockingQueue ;
import java.util.concurrent.Callable ;
import java.util.concurrent.ExecutorCompletionService ;
import java.util.concurrent.ExecutorService ;
import java.util.concurrent.Future ;
import java.util.concurrent.ThreadPoolExecutor ;
import java.util.concurrent.TimeUnit ;

import javax.annotation.PreDestroy ;
import javax.inject.Inject ;

import org.apache.commons.lang3.StringUtils ;
import org.apache.commons.lang3.concurrent.BasicThreadFactory ;
import org.apache.commons.lang3.text.WordUtils ;
import org.csap.agent.CsapApis ;
import org.csap.agent.model.EnvironmentSettings ;
import org.csap.helpers.CSAP ;
import org.csap.helpers.CsapApplication ;
import org.csap.integations.micrometer.CsapMeterUtilities ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.beans.factory.annotation.Autowired ;
import org.springframework.beans.factory.annotation.Qualifier ;
import org.springframework.http.ResponseEntity ;
import org.springframework.stereotype.Service ;
import org.springframework.util.LinkedMultiValueMap ;
import org.springframework.util.MultiValueMap ;
import org.springframework.web.client.RestTemplate ;

import com.fasterxml.jackson.databind.JsonNode ;
import com.fasterxml.jackson.databind.ObjectMapper ;
import com.fasterxml.jackson.databind.node.ObjectNode ;

/**
 * sends messages to csap event service
 */
@Service
public class CsapEvents {

	final Logger logger = LoggerFactory.getLogger( this.getClass( ) ) ;

	EnvironmentSettings lifecycleSettings = new EnvironmentSettings( ) ;

	private static int MAX_EVENT_BACKLOG = 2048 ;

	@Autowired
	CsapMeterUtilities metricUtilities ;

	volatile BlockingQueue<Runnable> eventPostQueue ;

	@Inject
	@Qualifier ( "csapEventsService" )
	private RestTemplate csapEventsService ;

	public int getBacklogCount ( ) {

		return eventPostQueue.size( ) ;

	}

	public CsapEvents ( ) {

		BasicThreadFactory eventThreadFactory = new BasicThreadFactory.Builder( )
				.namingPattern( "CsapEventPost-%d" )
				.daemon( true )
				.priority( Thread.NORM_PRIORITY + 1 )
				.build( ) ;

		eventPostQueue = new ArrayBlockingQueue<>( MAX_EVENT_BACKLOG ) ;
		// Use a single thread to sequence and post
		// eventPostPool = Executors.newFixedThreadPool(1, schedFactory, queue);
		// really only needs to be 1 - adding the others for lt scenario
		eventPostPool = new ThreadPoolExecutor( 1, 1,
				30, TimeUnit.SECONDS,
				eventPostQueue, eventThreadFactory ) ;

		eventPostCompletionService = new ExecutorCompletionService<String>(
				eventPostPool ) ;

	}

	public EnvironmentSettings getLifecycleSettings ( ) {

		return lifecycleSettings ;

	}

	private String projectName = "notInit" ;

	/**
	 * Injected by Capability Manager after loading cluster
	 *
	 * @param lifecycleSettings
	 */
	public void initialize ( EnvironmentSettings lifecycleSettings , String project ) {

		this.lifecycleSettings = lifecycleSettings ;
		this.projectName = project ;

		if ( lifecycleSettings.isEventPublishEnabled( ) && csapEventsService == null ) {

			logger.warn(
					"Event publishing enabled and injected events REST client is null - creating new one - assuming integration test" ) ;

		}

		logger.info( CSAP.buildDescription(
				"Event Publishing Settings",
				"primary enabled", lifecycleSettings.isEventPublishEnabled( ),
				"user", lifecycleSettings.getEventDataUser( ),
				"url", lifecycleSettings.getEventUrl( ),
				"secondary enabled", lifecycleSettings.isSecondaryEventPublishEnabled( ),
				"user", lifecycleSettings.getSecondaryEventUser( ),
				"url", lifecycleSettings.getSecondaryEventUrl( ) ) ) ;

	}

	public String fileName ( File targetFile , int size ) {

		String name = targetFile.getAbsolutePath( ) ;
		String finalName = targetFile.getAbsolutePath( ) ;

		if ( name.length( ) > size && name.length( ) >= 50 ) {

			finalName = name.substring( 0, 29 ) ;
			finalName += "/.../" + name.substring( name.length( ) - ( size - 31 ) ) ;

		}

		return finalName ;

	}

//	@EventListener
//	public void onSpringContextRefreshedEvent ( ContextRefreshedEvent event ) {
//
//		logger.warn( "Receive Spring ContextRefreshedEvent" ) ;
//
//	}

	public void flushEvents ( ) {

		logger.info( CsapApplication.header( "Flushing Event Cache: all collections will be uploaded to server" ) ) ;
		var attempts = 0 ;
		var maxAttempts = 40 ;

		while ( getBacklogCount( ) > 0
				&& attempts++ < maxAttempts ) {

			try {

				logger.info( CsapApplication.highlightHeader( "attempt: " + attempts
						+ " of " + maxAttempts
						+ " to flush event backlog, remaining: " + getBacklogCount( ) ) ) ;

				Thread.sleep( 1000 ) ;

			} catch ( InterruptedException e ) {

				logger.error( "{}", CSAP.buildCsapStack( e ) ) ;

			}

		}

		if ( getBacklogCount( ) > 0 ) {

			logger.info( CsapApplication.header( "Max Attempts reached: " + attempts + " items will be lost: "
					+ getBacklogCount( ) ) ) ;

		} else {

			logger.info( CsapApplication.header( "All events published successfully" ) ) ;

		}

	}

	@PreDestroy
	public void shutdown ( ) {

		logger.info( "Standard out is used because log4j thread will be shutdown" ) ;
		var attempts = 0 ;
		var maxAttempts = 40 ;

		while ( ! CsapApis.getInstance( ).isShutdown( )
				&& ! CsapApis.getInstance( ).application( ).isJunit( )
				&& ( ++attempts <= 40 ) ) {

			try {

				System.out.println( CsapApplication.header( "attempt" + attempts
						+ " of " + maxAttempts
						+ " waiting for agent shutdown to complete" ) ) ;
				Thread.sleep( 1000 ) ;

			} catch ( InterruptedException e ) {

				logger.error( "{}", CSAP.buildCsapStack( e ) ) ;

			}

		}

		flushEvents( ) ;

		eventPostQueue.clear( ) ;
		eventPostPool.shutdown( ) ;

	}

	/**
	 * Helper method that constructs event JSON
	 *
	 * @param service
	 * @param userid
	 * @param summary
	 * @param details
	 */
	public void publishUserEvent (
									String category_or_serviceName ,
									String userId ,
									String summary ,
									String details ) {

		if ( ! category_or_serviceName.startsWith( "/csap" ) ) {

			category_or_serviceName = CSAP_USER_SERVICE_CATEGORY + "/" + category_or_serviceName ;

		}

		ObjectNode data = jacksonMapper.createObjectNode( ) ;
		data.put( "csapText", details ) ;

		ObjectNode metaData = jacksonMapper.createObjectNode( ) ;

		if ( category_or_serviceName.startsWith( "/csap/ui" ) ) {

			metaData.put( "uiUser", userId ) ;

		}

		publishEvent( category_or_serviceName, summary, metaData, data ) ;

		return ;

	}

	public void publishUserEvent (
									String category_or_serviceName ,
									String userId ,
									String summary ,
									JsonNode details ) {

		if ( ! category_or_serviceName.startsWith( "/csap" ) ) {

			category_or_serviceName = CSAP_USER_SERVICE_CATEGORY + "/" + category_or_serviceName ;

		}

		ObjectNode metaData = jacksonMapper.createObjectNode( ) ;

		if ( category_or_serviceName.startsWith( "/csap/ui" ) ) {

			metaData.put( "uiUser", userId ) ;

		}

		publishEvent( category_or_serviceName, summary, metaData, details ) ;

		return ;

	}

	ObjectMapper jacksonMapper = new ObjectMapper( ) ;

	public void publishEvent (
								String category ,
								String summary ,
								String details ) {

		ObjectNode data = jacksonMapper.createObjectNode( ) ;
		data.put( "csapText", details ) ;
		publishEvent( category, summary, null, data ) ;

		return ;

	}

	public void publishEvent (
								String category ,
								String summary ,
								String details ,
								Throwable t ) {

		ObjectNode data = jacksonMapper.createObjectNode( ) ;
		data.put( "csapText", details + "\n Exception " + getCustomStackTrace( t ) ) ;
		publishEvent( category, summary, null, data ) ;

		return ;

	}

	public static final String TIMER_PREFIX = "event-publish" ;
	public static final String CSAP_CATEGORY = "/csap" ;
	public static final String CSAP_USER_SETTINGS_CATEGORY = "/csap/settings/user/" ;
	public static final String CSAP_UI_CATEGORY = "/csap/ui" ;
	public static final String CSAP_ACCESS_CATEGORY = CSAP_UI_CATEGORY + "/access" ;
	public static final String CSAP_OS_CATEGORY = CSAP_UI_CATEGORY + "/os" ;
	public static final String CSAP_USER_SERVICE_CATEGORY = CSAP_UI_CATEGORY + "/service" ;
	public static final String CSAP_SYSTEM_CATEGORY = "/csap/system" ;
	public static final String CSAP_SYSTEM_SERVICE_CATEGORY = CSAP_SYSTEM_CATEGORY + "/service" ;
	public static final String CSAP_REPORTS_CATEGORY = "/csap/reports" ;

	// http://logging.apache.org/log4j/2.x/manual/customloglevels.html#CustomLoggers
	private int maxTextSize = 50 * 1024 ; // 50kb

	public int getMaxTextSize ( ) {

		return maxTextSize ;

	}

	public void setMaxTextSize ( int maxTextSize ) {

		logger.warn( "Current: {}, New: {} ", this.maxTextSize, maxTextSize ) ;
		this.maxTextSize = maxTextSize ;

	}

	public void publishEvent (
								String category ,
								String summary ,
								JsonNode metaData ,
								JsonNode data ) {

		logger.debug( "category: {},  queue length {}", category, getBacklogCount( ) ) ;

		try {

			if ( category.equals( MetricsPublisher.CSAP_HEALTH ) ) {

				// When events are being queued, skip health publis
				if ( getBacklogCount( ) >= 10 ) {

					logger.info( "Skipping health publish due to queue length {}", getBacklogCount( ) ) ;

				}

			} else {

				CsapEventsUnwind eventLogger = CsapEventsUnwind.create( ) ;
				var newline = "\n" ;
				if ( summary.length( ) < 30 )
					newline = "" ;
				eventLogger.info( "{} {} Category '{}'",
						StringUtils.rightPad( summary, 30 ),
						newline,
						category ) ;

				// logger.info( "Category: {} \t\t {}", category, summary );
			}

		} catch ( Throwable t ) {

			logger.error( "Failed to parse data", t ) ;

		}

		if ( lifecycleSettings == null || ! lifecycleSettings.isEventPublishEnabled( ) ) {

			return ;

		}

		MultiValueMap<String, String> formParams = new LinkedMultiValueMap<String, String>( ) ;
		// Build jsDoc parameter
		// Map<String, String> map = new HashMap<String, String>();

		if ( summary.length( ) > 60 ) {

			summary = summary.substring( 0, 60 ) + "..." ;

		}

		ObjectNode eventJson = jacksonMapper.createObjectNode( ) ;
		eventJson.put( "category", category ) ;
		eventJson.put( "summary", summary ) ;
		eventJson.put( "lifecycle", CsapApis.getInstance( ).application( ).getCsapHostEnvironmentName( ) ) ;
		eventJson.put( "project", this.projectName ) ;
		eventJson.put( "host", CsapApis.getInstance( ).application( ).getCsapHostName( ) ) ;

		ObjectNode createdOn = eventJson.putObject( "createdOn" ) ;

		createdOn.put( "unixMs", System.currentTimeMillis( ) ) ;

		DateFormat df2 = new SimpleDateFormat( "yyyy-MM-dd" ) ;
		Date now = new Date( ) ;
		createdOn.put( "date", df2.format( now ) ) ;

		DateFormat df1 = new SimpleDateFormat( "HH:mm:ss" ) ;
		createdOn.put( "time", df1.format( now ) ) ;

		if ( metaData != null ) {

			eventJson.set( "metaData", metaData ) ;

		}

		if ( data != null ) {

			if ( data.isObject( ) && data.has( "csapText" ) ) {

				ObjectNode dataNode = jacksonMapper.createObjectNode( ) ;
				String text = data.get( "csapText" ).asText( ) ;

				if ( text.length( ) > maxTextSize ) {

					logger.warn( "Warning: truncating details of item: {}. Found: {}, max: {} ",
							category, text.length( ), maxTextSize ) ;
					text = text.substring( text.length( ) - maxTextSize ) ;

				}

				dataNode.put( "csapText", text ) ;
				eventJson.set( "data", dataNode ) ;

			} else {

				eventJson.set( "data", data ) ;

			}

		}

		try {

			formParams.add( "eventJson",
					jacksonMapper.writerWithDefaultPrettyPrinter( ).writeValueAsString( eventJson ) ) ;

			// logger.info("Message Converters loaded: " +
			// auditPostTemplate.getMessageConverters());
			logger.debug( "Submitting event to queue: url: {}, params:\n {} ", lifecycleSettings.getEventUrl( ),
					WordUtils.wrap( formParams.toString( ), 140 ) ) ;

			// logger.debug( "eventPostPool terminated: {}", eventPostPool.isTerminated() );
			eventPostPool.submit(
					new EventPostRunnable(
							formParams,
							category,
							summary ) ) ;

			logger.debug( " post size: {}", getBacklogCount( ) ) ;

		} catch ( Exception e ) {

			metricUtilities.incrementCounter( TIMER_PREFIX + ".queue-failure" ) ;

			logger.error( "Failed submitting to job queue: {} ,\n params: {}, \n {}",
					lifecycleSettings.getEventUrl( ), WordUtils.wrap( formParams.toString( ), 140 ),
					CSAP.buildCsapStack( e ) ) ;

		}

		return ;

	}

	/**
	 * For testing
	 *
	 * @param formParams
	 */
	transient int numSent = 0 ;

	public void testingOnlyPostEvent ( MultiValueMap<String, String> formParams )
		throws Exception {

		// Support for LT. do not blow through queue
		if ( numSent++ % 1000 == 0 ) {

			logger.info( "Messages sent: " + numSent + " backlog: " + eventPostQueue.size( ) ) ;

		}

		ThreadPoolExecutor pool = (ThreadPoolExecutor) eventPostPool ;

		if ( eventPostQueue.size( ) > 100 && pool.getCorePoolSize( ) == 1 ) {

			pool.setCorePoolSize( 6 ) ;
			pool.setMaximumPoolSize( 6 ) ;

		}

		// blocking for testing
		logger.info( "eventPostPool terminated: {}", eventPostPool.isTerminated( ) ) ;
		Future<String> futureResult = eventPostPool.submit( new EventPostRunnable( formParams, "test", "test" ) ) ;

		// Non Blocking to test event caching/pooling
		// futureResult.get() ;
	}

	ExecutorService eventPostPool ;
	ExecutorCompletionService<String> eventPostCompletionService ;

	/**
	 * Making Http requests survive a minor service outage for migrations, etc.
	 *
	 * @author someDeveloper
	 *
	 */
	private int numberPublished = 0 ;

	public int getNumberPublished ( ) {

		return numberPublished ;

	}

	// only intended for junits
	public boolean waitForFlushOfAllEvents ( int maxAttempts )
		throws InterruptedException {

		int attempts = 0 ;

		logger.info( "Sleeping for 3 seconds for in process queries" ) ;
		Thread.sleep( 3000 ) ;

		while ( eventPostQueue.size( ) > 0
				&& attempts++ < maxAttempts ) {

			Thread.sleep( 500 ) ;
			logger.info( "Sleeping on response for items: " + eventPostQueue.size( ) + " \t\t Attempt: " + attempts ) ;

		}

		if ( eventPostQueue.size( ) == 0 ) {

			return true ;

		}

		return false ;

	}

	private int consecutiveFailedEventPostAttempts = 0 ;

	public int getConsecutiveFailedEventPostAttempts ( ) {

		return consecutiveFailedEventPostAttempts ;

	}

	private int eventPostFailures = 0 ;

	public int getEventPostFailures ( ) {

		return eventPostFailures ;

	}

	private int numberPostedEvents = 0 ;

	public int getNumberOfPostedEventsAndReset ( ) {

		int current = numberPostedEvents ;
		numberPostedEvents = 0 ;
		return current ;

	}

	// This prevents an infinite loop occurring when an event is needed to be
	// purged.
	// An extended outage may result in intermittent events not being posted,
	// but logs will contain records
	private final int MAX_EVENT_RETRIES = 500 ;
	private final int EVENT_SIZE_WARNING = 1024 * 1024 * 1 ; // default tomcat
																// post limit is
																// 2MB, warning
																// output at 1

	private int secondaryFailFilter = 0 ;

	public class EventPostRunnable implements Callable<String> {

		private MultiValueMap<String, String> formParams ;
		private String timerId ;
		private String eventDescription ;

		public EventPostRunnable ( MultiValueMap<String, String> formParams, String category,
				String infoMessage ) {

			this.formParams = formParams ;
			this.timerId = category.substring( 1 ).replaceAll( "/", "-" ) ;
			if ( this.timerId.startsWith( "csap-metrics" ) )
				this.timerId = "csap-metrics" ;
			if ( this.timerId.startsWith( "csap-reports" ) )
				this.timerId = "csap-reports" ;
			if ( this.timerId.startsWith( "csap-ui" ) )
				this.timerId = "csap-ui" ;
			if ( this.timerId.startsWith( "csap-system" ) )
				this.timerId = "csap-system" ;
			this.eventDescription = category + ":" + infoMessage ;

		}

		@Override
		public String call ( )
			throws Exception {

			if ( ( eventPostQueue.size( ) > 10 ) && ( eventPostQueue.size( ) % 10 == 0 ) ) {

				logger.warn(
						" Events in backlog: " + eventPostQueue.size( ) + ", maximum allowed: " + MAX_EVENT_BACKLOG ) ;

			}

			int payloadSize = formParams.toString( ).length( ) ;

			if ( payloadSize > EVENT_SIZE_WARNING ) {

				logger.warn( "Event: {}, size: {} is larger then max allowed: {}", eventDescription, payloadSize,
						EVENT_SIZE_WARNING ) ;

			}

			if ( this.timerId.equals( "csap-ui" ) ) {

				numberPublished++ ;

			}

			boolean isRemoveEventFromQueue = false ;
			int numAttempts = 0 ;
			String httpResponseCode = "not-used" ;

			while ( ! isRemoveEventFromQueue ) {

				numAttempts++ ;

				var idTimer = metricUtilities.startTimer( ) ;

				try {

					// set password immediately before sending - in case it has
					// been updated in definition.
					formParams.set( "userid", lifecycleSettings.getEventDataUser( ) ) ;
					formParams.set( "pass", lifecycleSettings.getEventDataPass( ) ) ;

					logger.debug( "posting to event service: {}, parameters: \n{}", lifecycleSettings.getEventUrl( ),
							WordUtils.wrap( formParams.toString( ), 140 ) ) ;

					if ( csapEventsService == null ) {

						logger.warn( "Client connection not initialized, data not being sent: \n{}",
								eventDescription ) ;
						isRemoveEventFromQueue = true ;

					} else {

						ResponseEntity<String> response = csapEventsService.postForEntity(
								lifecycleSettings.getEventUrl( ),
								formParams, String.class ) ;

						logger.debug( "Event service http response: {}, body: {} ", response.getStatusCode( ), response
								.getBody( ) ) ;

						if ( response.getStatusCode( ).is2xxSuccessful( ) ) {

							httpResponseCode = response.getStatusCode( ).toString( ) ;
							isRemoveEventFromQueue = true ;
							numberPostedEvents++ ;

							if ( consecutiveFailedEventPostAttempts > 0 ) {

								consecutiveFailedEventPostAttempts-- ;

							}

							if ( lifecycleSettings.isSecondaryEventPublishEnabled( ) ) {

								// Support for multiple analytics targets
								try {

									formParams.set( "userid", lifecycleSettings.getSecondaryEventUser( ) ) ;
									formParams.set( "pass", lifecycleSettings.getSecondaryEventPass( ) ) ;

									ResponseEntity<String> responseSecondary = csapEventsService.postForEntity(
											lifecycleSettings.getSecondaryEventUrl( ),
											formParams, String.class ) ;

									logger.debug( "Secondary event service http response: {}, body: {} ",
											responseSecondary.getStatusCode( ),
											responseSecondary.getBody( ) ) ;

								} catch ( Exception e ) {

									if ( ( ( secondaryFailFilter++ ) % 10 == 0 ) ) {

										logger.warn( "Failed secondary post: {} to {}",
												lifecycleSettings.getSecondaryEventUser( ),
												lifecycleSettings.getSecondaryEventUrl( ) ) ;

									}

									logger.debug( "Failed secondary post: {}", CSAP.buildCsapStack( e ) ) ;

								}

							}

						} else {

							logger.warn( "Event publish failed, attempt: {} \t\t Status: {} \t\t Body: {}",
									numAttempts, response.getStatusCode( ), response.getBody( ) ) ;

						}

					}

				} catch ( Exception e ) {

					metricUtilities.incrementCounter( "csap." + TIMER_PREFIX + ".failures" ) ;

					consecutiveFailedEventPostAttempts++ ;
					eventPostFailures++ ;
					logger.warn(
							"Event post failure: service url: '{}' \n\t category: '{}' \n\t attempt: {}, events in backlog: {}, max backlog size: {}, Reason: {}, Length: {}",
							lifecycleSettings.getEventUrl( ),
							eventDescription, numAttempts, eventPostQueue.size( ), MAX_EVENT_BACKLOG, e.getMessage( ),
							formParams.toString( ).length( ) ) ;

					logger.debug( "Failed to post: \n {} \n payload {}", CSAP.buildCsapStack( e ),
							WordUtils.wrap( formParams.toString( ), 140 ) ) ;

					if ( e.getMessage( ).equals( "403 Forbidden" ) ) {

						isRemoveEventFromQueue = true ;
						logger.warn( "Invalid user: {} or password(xxx) - Audits and Analytics will not be published.",
								formParams.get( "userid" ) ) ;

					} else if ( e.getMessage( ).startsWith( "400 Bad Request" ) ) {

						isRemoveEventFromQueue = true ;
						logger
								.warn(
										"Failed to post event, response: 400 Bad Request. This is usually due to exceeding max event size (2MB default). Size posted: {}",
										formParams.toString( ).length( ) ) ;

					} else if ( e.getMessage( ).startsWith( "400" ) ) {

						// Latest Boot may be not setting the request correctly.
						isRemoveEventFromQueue = true ;
						logger
								.warn(
										"Failed to post event, response: 400. This is usually due to exceeding max event size (2MB default). Size posted: {}",
										formParams.toString( ).length( ) ) ;

					} else {

						// 503 Service Unavailable: due to service migration. set to debug to avoid
						// filling logs
						logger.debug( "Failure posting to events service, retry is 30 to 60 seconds {}", CSAP
								.buildCsapStack( e ) ) ;

					}

				} finally {

					try {

						var nanos = metricUtilities.stopTimer( idTimer, TIMER_PREFIX + "." + timerId ) ;
						metricUtilities.record( "csap." + TIMER_PREFIX, nanos, TimeUnit.NANOSECONDS ) ;

					} catch ( Exception e ) {

						logger.error( "Failed to stop", e ) ;

					}

					long spaceRequestsMs = 1 ; // avoid hammering event service

					if ( getConsecutiveFailedEventPostAttempts( ) > 10 ) {

						spaceRequestsMs = 200 ;

					}

					// During migrations extended service delays could occur.
					// if ( numAttempts > MAX_EVENT_RETRIES) {
					// logger.warn( "Purging Event as attempts is greater then
					// MAX_EVENT_RETRIES: {}", MAX_EVENT_RETRIES );
					// isRemoveEventFromQueue = true;
					// }
					if ( ! isRemoveEventFromQueue ) {

						// Adding spread to retry requests with variance
						spaceRequestsMs = ( 30 + javaRandom.nextInt( 60 ) ) * 1000 ;

					}

					try {

						TimeUnit.MILLISECONDS.sleep( spaceRequestsMs ) ;

					} catch ( InterruptedException e1 ) {

						logger.info( "Sleep exception" ) ;

					}

				}

			}

			return httpResponseCode ;

		}

	}

	private Random javaRandom = new Random( ) ;

	/**
	 * Shorten the summary field so that it does not scroll to far
	 *
	 * @param summary
	 * @return
	 */
	private static String shortenSummary ( String summary ) {

		if ( summary.length( ) > 60 ) {

			return summary.substring( 0, 60 ) ;

		} else {

			return summary ;

		}

	}

	@SuppressWarnings ( "unchecked" )
	public static String getCustomStackTrace ( Throwable possibleNestedThrowable ) {

		// add the class name and any message passed to constructor
		final StringBuffer result = new StringBuffer( ) ;

		Throwable currentThrowable = possibleNestedThrowable ;

		int nestedCount = 1 ;

		while ( currentThrowable != null ) {
			// if (log.isDebugEnabled()) {
			// log.debug("currentThrowable: " + currentThrowable.getMessage()
			// + " nestedCount: " + nestedCount + " resultBuf size: "
			// + result.length());
			// }

			if ( nestedCount == 1 ) {

				result.append( "\n__========== TOP Exception ================================__" ) ;

			} else {

				result.append( "\n========== Nested Count: " ) ;
				result.append( nestedCount ) ;
				result.append( " ===============================__" ) ;

			}

			result.append( "\n\n Exception: __"
					+ currentThrowable.getClass( ).getName( ) ) ;
			result.append( "\n Message: " + currentThrowable.getMessage( ) ) ;
			result.append( "__\n\n StackTrace: __\n" ) ;

			// add each element of the stack trace
			List traceElements = Arrays
					.asList( currentThrowable.getStackTrace( ) ) ;

			Iterator traceIt = traceElements.iterator( ) ;

			while ( traceIt.hasNext( ) ) {

				StackTraceElement element = (StackTraceElement) traceIt.next( ) ;
				result.append( element ) ;
				result.append( "__\n" ) ;

			}

			result.append( "\n========================================================__" ) ;
			currentThrowable = currentThrowable.getCause( ) ;
			nestedCount++ ;

		}

		return result.toString( ) ;

	}

}
