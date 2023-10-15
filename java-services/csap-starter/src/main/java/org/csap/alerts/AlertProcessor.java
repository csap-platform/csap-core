package org.csap.alerts ;

import java.lang.management.ManagementFactory ;
import java.text.SimpleDateFormat ;
import java.time.LocalDateTime ;
import java.time.format.DateTimeFormatter ;
import java.util.ArrayList ;
import java.util.Date ;
import java.util.List ;
import java.util.Optional ;
import java.util.Properties ;
import java.util.concurrent.Executors ;
import java.util.concurrent.ScheduledExecutorService ;
import java.util.concurrent.TimeUnit ;
import java.util.concurrent.atomic.AtomicInteger ;

import javax.annotation.PostConstruct ;
import javax.annotation.PreDestroy ;

import org.apache.commons.lang3.concurrent.BasicThreadFactory ;
import org.csap.helpers.CSAP ;
import org.csap.helpers.CsapSimpleCache ;
import org.csap.integations.CsapInformation ;
import org.csap.integations.CsapPerformance.CustomHealth ;
import org.csap.integations.micrometer.CsapMeterUtilities ;
import org.csap.integations.micrometer.MeterReport ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.beans.factory.annotation.Autowired ;
import org.springframework.context.annotation.Bean ;
import org.springframework.core.env.Environment ;
import org.springframework.core.io.ByteArrayResource ;
import org.springframework.mail.javamail.JavaMailSender ;
import org.springframework.mail.javamail.JavaMailSenderImpl ;
import org.springframework.mail.javamail.MimeMessageHelper ;
import org.springframework.stereotype.Service ;
import org.thymeleaf.context.Context ;
import org.thymeleaf.spring5.SpringTemplateEngine ;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver ;

import com.fasterxml.jackson.core.type.TypeReference ;
import com.fasterxml.jackson.databind.JsonNode ;
import com.fasterxml.jackson.databind.ObjectMapper ;
import com.fasterxml.jackson.databind.node.ArrayNode ;
import com.fasterxml.jackson.databind.node.ObjectNode ;

import io.micrometer.core.instrument.Gauge ;
import io.micrometer.core.instrument.Meter ;
import io.micrometer.core.instrument.Timer ;

@Service
public class AlertProcessor {

	final static Logger logger = LoggerFactory.getLogger( AlertProcessor.class ) ;

	// @Inject - causes ordering issuess - very tricky to detect and correct
	SpringTemplateEngine springTemplateEngine ;

	AlertSettings settings ;
	CsapInformation csapInformation ;

	MeterReport meterReport ;
	CsapMeterUtilities metricsUtilities ;

	@Autowired
	public AlertProcessor ( AlertSettings settings,
			CsapInformation csapInformation,
			MeterReport csapMeterReport,
			CsapMeterUtilities csapMeterUtilities ) {

		// TODO Auto-generated constructor stub
		this.csapInformation = csapInformation ;
		this.settings = settings ;
		this.meterReport = csapMeterReport ;
		this.metricsUtilities = csapMeterUtilities ;

		csapMeterReport.getHealthReport( ).setAlertProcessor( this ) ;

		springTemplateEngine = new SpringTemplateEngine( ) ;
		springTemplateEngine.addTemplateResolver( new ClassLoaderTemplateResolver( ) ) ;

	}

	@PreDestroy
	public void cleanup ( ) {

		if ( scheduledExecutorService != null ) {

			logger.info( "\n\n ********* Cleaning up jobs \n\n" ) ;
			scheduledExecutorService.shutdownNow( ) ;

		}

	}

	private AlertInstance undefinedAlert = new AlertInstance( CsapGlobalId.UNDEFINED_ALERTS.id, 0, null ) ;
	private AlertInstance failRunAlert = new AlertInstance( CsapGlobalId.HEALTH_REPORT_FAIL.id, 0, null ) ;

	private CsapSimpleCache emailTimer ;
	private CsapSimpleCache throttleTimer ;

	@PostConstruct
	public void initialize ( ) {

		logger.debug( "configuring email and alert throttles" ) ;

		emailTimer = CsapSimpleCache.builder(
				settings.getNotify( ).getFrequency( ),
				CSAP.parseTimeUnit(
						settings.getNotify( ).getTimeUnit( ),
						TimeUnit.HOURS ),
				AlertSettings.class,
				"Email Notifications" ) ;

		throttleTimer = CsapSimpleCache.builder(
				settings.getThrottle( ).getFrequency( ),
				CSAP.parseTimeUnit(
						settings.getThrottle( ).getTimeUnit( ),
						TimeUnit.HOURS ),
				AlertSettings.class,
				"Alert Throttle" ) ;

		initializeHealthJobs( ) ;

		// add health report job
		scheduledExecutorService
				.scheduleAtFixedRate( this::buildHealthReport,
						settings.getReport( ).getIntervalSeconds( ),
						settings.getReport( ).getIntervalSeconds( ),
						TimeUnit.SECONDS ) ;

		// add alert jobs - each configured alert is triggered based on
		// configuration
		for ( AlertInstance alertInstance : settings.getAlertDefinitions( ) ) {

			logger.debug( "Scheduling job every {} seconds : {}", alertInstance.getCollectionSeconds( ),
					alertInstance ) ;

			scheduledExecutorService
					.scheduleAtFixedRate(
							( ) -> collectAlertSample( alertInstance ),
							alertInstance.getCollectionSeconds( ),
							alertInstance.getCollectionSeconds( ),
							TimeUnit.SECONDS ) ;

		}

		// add context alets

		settings.getAllAlertInstances( ).add( getUndefinedAlert( ) ) ;
		settings.getAllAlertInstances( ).add( getFailRunAlert( ) ) ;

	}

	public void collectAlertSample ( AlertInstance alert ) {

		try {

			alert.setPendingFirstCollection( false ) ;
			// Simon s = SimonManager.getSimon( alert.getId() ) ;
			Meter meter = metricsUtilities.find( alert.getId( ) ) ;

			if ( meter == null ) {

				logger.debug( "No meters found: {}", alert.getId( ) ) ;
				return ;

			}

			var report = meterReport.buildMeterReport( meter, 3, false ) ;

			if ( report.has( MeterReport.SIMPLE_VALUE ) ) {

				// many reports are a single value - set as count
				var count = report.path( MeterReport.SIMPLE_VALUE ).asDouble( ) ;
				report = jacksonMapper.createObjectNode( ) ;
				report.put( "count", count ) ;

			}

			logger.debug( "meter type: {} ", meter.getClass( ).getName( ) ) ;

			if ( ! Gauge.class.isInstance( meter ) ) {

				alert.setPreviousSample( alert.getLatestSample( ) ) ;

			}

			alert.setLatestSample( report ) ;

			if ( settings.isDebug( ) ) {

				logger.info( "Collected id: '{}',  meter: {} ", alert.getId( ), meter.getClass( ).getSimpleName( ) ) ;

			}

		} catch ( Exception e ) {

			logger.error( "Failed to collect", e ) ;

		}

	}

	private ScheduledExecutorService scheduledExecutorService = null ;

	AtomicInteger counterCollections = new AtomicInteger( ) ;

	protected void initializeHealthJobs ( ) {

		// Initialize healthReport
		healthReport = jacksonMapper.createObjectNode( ) ;
		healthReport.put( AlertFields.collectionCount.json, counterCollections.get( ) ) ;
		healthReport.put( AlertFields.healthy.json, true ) ;
		healthReport.put( "note", "Pending First Run" ) ;

		String scheduleName = AlertProcessor.class.getSimpleName( ) + "_" + settings.getReport( )
				.getIntervalSeconds( ) ;
		BasicThreadFactory schedFactory = new BasicThreadFactory.Builder( )

				.namingPattern( scheduleName + "-%d" )
				.daemon( true )
				.priority( Thread.NORM_PRIORITY )
				.build( ) ;
		// Single collection thread
		scheduledExecutorService = Executors
				.newScheduledThreadPool( 1, schedFactory ) ;

		logger.info( "Adding Job: {}", scheduleName ) ;

	}

	@Autowired
	private Environment springEnvironment ;

	@Bean
	public JavaMailSender csapMailSender ( ) {

		JavaMailSenderImpl sender = new JavaMailSenderImpl( ) ;

		Properties properties = new Properties( ) ;
		// properties.put("mail.smtp.auth", auth);
		properties.put( "mail.smtp.timeout", settings.getNotify( ).getEmailTimeOutMs( ) ) ;
		properties.put( "mail.smtp.connectiontimeout", settings.getNotify( ).getEmailTimeOutMs( ) ) ;
		// properties.put("mail.smtp.starttls.enable", starttls);

		sender.setJavaMailProperties( properties ) ;

		try {

			sender.setHost( springEnvironment.getProperty( "spring.mail.host" ) ) ;
			sender.setPort( Integer.parseInt( springEnvironment.getProperty( "spring.mail.port" ) ) ) ;

		} catch ( Exception e ) {

			logger.warn(
					"Failed to load mail settings from the springEnvironment, falling back to csap notification host:",
					CSAP.buildCsapStack( e ) ) ;
			sender.setHost( settings.getNotify( ).getEmailHost( ) ) ;
			sender.setPort( settings.getNotify( ).getEmailPort( ) ) ;

		}
		// sender.setProtocol(protocol);
		// sender.setUsername(username);
		// sender.setPassword(password);

		return sender ;

	}

	public static String EMAIL_DISABLED = "Email notifications disabled" ;

	private ObjectMapper jacksonMapper = new ObjectMapper( ) ;

	private ArrayNode alertHistory = jacksonMapper.createArrayNode( ) ;
	private ArrayNode alertsThrottled = jacksonMapper.createArrayNode( ) ;

	/**
	 * Called every 30 seconds: if healthy - then no email
	 * 
	 * @param healthReport
	 */
	public void addReport ( ObjectNode healthReport ) {

		logger.debug( "backlog Size: {} ", alertsForEmail.size( ) ) ;

		while ( alertsForEmail.size( ) > settings.getNotify( ).getEmailMaxAlerts( ) ) {

			alertsForEmail.remove( 0 ) ;

		}

		if ( settings.isDebug( ) && alertHistory.size( ) > settings.getRememberCount( ) ) {

			logger.info( "Current alert count: {} is larger then configured: {} - oldest items are being purged",
					alertHistory.size( ), settings.getRememberCount( ) ) ;

		}

		while ( alertHistory.size( ) > settings.getRememberCount( ) ) {

			alertHistory.remove( 0 ) ;

		}

		try {

			ArrayList<ObjectNode> activeAlerts = jacksonMapper.readValue(
					healthReport.get( AlertFields.limitsExceeded.json ).traverse( ),
					new TypeReference<ArrayList<ObjectNode>>( ) {
					} ) ;

			// String foundTime = LocalDateTime.now().format(
			// DateTimeFormatter.ofPattern( "h:mm:ss a" ) ) ;
			long now = System.currentTimeMillis( ) ;
			String foundTime = LocalDateTime.now( ).format( DateTimeFormatter.ofPattern( "HH:mm:ss , MMM d" ) ) ;
			activeAlerts.forEach( item -> {

				item.put( AlertInstance.AlertItem.formatedTime.json, foundTime ) ;
				item.put( AlertInstance.AlertItem.timestamp.json, now ) ;
				item.put( AlertInstance.AlertItem.count.json, 1 ) ;

			} ) ;

			// increment counters and dates - or add
			activeAlerts.forEach( activeAlert -> {

				int matchCount = 0 ;
				int lastMatchIndex = 0 ;
				int index = 0 ;

				for ( JsonNode throttledEvent : getAlertsThrottled( ) ) {

					if ( AlertInstance.AlertItem.isSameId( activeAlert, throttledEvent ) ) {

						matchCount++ ;
						lastMatchIndex = index ;

					}

					index++ ;

				}

				if ( matchCount >= settings.getThrottle( ).getCount( ) ) {

					// update the count
					int oldCount = getAlertsThrottled( )
							.get( lastMatchIndex )
							.get( AlertInstance.AlertItem.count.json )
							.asInt( ) ;
					activeAlert.put( AlertInstance.AlertItem.count.json, 1 + oldCount ) ;

					// remove the oldest
					getAlertsThrottled( ).remove( lastMatchIndex ) ;

				}

				// add the newest
				getAlertsThrottled( ).add( activeAlert ) ;

			} ) ;

			if ( getThrottleTimer( ).isExpired( ) ) {

				// Always add in memory browsing
				alertHistory.addAll( getAlertsThrottled( ) ) ;
				getThrottleTimer( ).reset( ) ;
				getAlertsThrottled( ).removeAll( ) ;

			}

			// Email support
			if ( settings.getNotify( ).getEmails( ) == null ) {

				logger.debug( "Email notifications are disabled." ) ;

			} else {

				sendAlertEmail( healthReport, activeAlerts ) ;

			}

		} catch ( Exception e ) {

			logger.error( "Failed to send message", e ) ;

		}

		return ;

	}

	private List<JsonNode> alertsForEmail = new ArrayList<>( ) ;

	private void sendAlertEmail ( ObjectNode healthReport , ArrayList<ObjectNode> limits ) {

		if ( csapMailSender( ) == null ) {

			logger.warn( "Java Mail is disabled - update spring.mail in application.yml" ) ;
			return ;

		}

		alertsForEmail.addAll( limits ) ;

		if ( alertsForEmail.size( ) == 0 ) {

			logger.debug( "No items in backlog" ) ;
			return ;

		}

		if ( ! getEmailTimer( ).isExpired( ) ) {

			int collectionCount = healthReport.get( AlertFields.collectionCount.json ).asInt( ) ;

			if ( collectionCount % 120 == 0 ) {

				// do not overload with messages - only output 1 per hour
				logger.info( "Notification not sent because interval is not met: {}. Last notification was sent: {}",
						getEmailTimer( ).getMaxAgeFormatted( ),
						getEmailTimer( ).getCurrentAgeFormatted( ) ) ;

			} else {

				logger.debug( "Notification not sent because interval is not met: {}. Last notification was sent: {}",
						getEmailTimer( ).getMaxAgeFormatted( ),
						getEmailTimer( ).getCurrentAgeFormatted( ) ) ;

			}

			return ;

		}

		// Set up variables for template processing
		String emailText = buildEmailText( ) ;

		logger.info( "{} Type {} : \n\t to: {}\n\t message: {}",
				csapInformation.getName( ), "Health", settings.getNotify( ),
				alertsForEmail ) ;

		csapMailSender( ).send( mimeMessage -> {

			MimeMessageHelper messageHelper = new MimeMessageHelper( mimeMessage, true, "UTF-8" ) ;
			messageHelper.setTo( settings.getNotify( ).getEmails( ) ) ;
			// messageHelper.setCc( CsapUser.currentUsersEmailAddress()
			// );
			messageHelper.setFrom( "csap@yourCompany.com" ) ;
			messageHelper.setSubject( "CSAP Notification: " + csapInformation.getName( ) + " - " + csapInformation
					.getLifecycle( ) ) ;
			messageHelper.setText( emailText, true ) ;

			messageHelper.addAttachment( "report.json",
					new ByteArrayResource( jacksonMapper
							.writerWithDefaultPrettyPrinter( )
							.writeValueAsString( healthReport )
							.getBytes( ) ) ) ;

		} ) ;

		getEmailTimer( ).reset( ) ;
		alertsForEmail.clear( ) ;

	}

	public String buildEmailText ( ) {

		Context context = new Context( ) ;
		context.setVariable( "appUrl", csapInformation.getLoadBalancerUrl( ) ) ;
		context.setVariable( "healthUrl", csapInformation.getFullHealthUrl( ) ) ;
		context.setVariable( "life", csapInformation.getLifecycle( ) ) ;
		context.setVariable( "service", csapInformation.getName( ) ) ;
		context.setVariable( "host", csapInformation.getHostName( ) ) ;
		context.setVariable( "dateTime", LocalDateTime.now( ).format( DateTimeFormatter.ofPattern(
				"h:mm:ss a, MMMM d" ) ) ) ;

		context.setVariable( "limits", alertsForEmail ) ;

		// use a custom engine to avoid race conditions on construction
		return springTemplateEngine.process( "/templates/csap/alerts/email.html", context ) ;

	}

	private ArrayNode getAlertHistory ( ) {

		return alertHistory ;

	}

	public ArrayNode getAllAlerts ( ) {

		ArrayNode all = jacksonMapper.createArrayNode( ) ;
		all.addAll( getAlertHistory( ) ) ;
		all.addAll( getAlertsThrottled( ) ) ;

		return all ;

	}

	public CsapSimpleCache getEmailTimer ( ) {

		return emailTimer ;

	}

	public CsapSimpleCache getThrottleTimer ( ) {

		return throttleTimer ;

	}

	public ArrayNode getAlertsThrottled ( ) {

		return alertsThrottled ;

	}

	volatile ObjectNode healthReport = null ;

	SimpleDateFormat timeDayFormat = new SimpleDateFormat( "HH:mm:ss , MMM d" ) ;

	private void buildHealthReport ( ) {

		ObjectNode latestReport = createEmptyHealthReport( ) ;

		boolean isHealthy = false ;

		try {

			// Split collectionTimer = SimonManager.getStopwatch(
			// CsapGlobalId.HEALTH_REPORT.id ).start() ;
			Timer.Sample ssoTimer = metricsUtilities.startTimer( ) ;

			// 1.n counters and times will determine health
			checkConfiguredLimits( latestReport ) ;

			metricsUtilities.stopTimer( ssoTimer, CsapGlobalId.HEALTH_REPORT.id ) ;

			isHealthy = latestReport.get( AlertFields.healthy.json ).asBoolean( ) ;

		} catch ( Exception e ) {

			addFailure( latestReport, failRunAlert, e.getClass( ).getSimpleName( ), 1, 0, false ) ;
			logger.error( "Failed running health report", e ) ;

		}

		if ( isHealthy ) {

			// SimonManager.getCounter( CsapGlobalId.HEALTH_REPORT_PASS.id ).increase() ;
			metricsUtilities.incrementCounter( CsapGlobalId.HEALTH_REPORT_PASS.id ) ;

			if ( settings.isDebug( ) ) {

				logger.info( "HealthReport {}", CSAP.jsonPrint( latestReport ) ) ;

			}

		} else {

			// SimonManager.getCounter( CsapGlobalId.HEALTH_REPORT_FAIL.id ).increase() ;
			metricsUtilities.incrementCounter( CsapGlobalId.HEALTH_REPORT_FAIL.id ) ;
			logger.warn( "HealthReport {}", CSAP.jsonPrint( latestReport ) ) ;

		}

		addReport( latestReport ) ;
		healthReport = latestReport ;
		return ;

	}

	public ObjectNode createEmptyHealthReport ( ) {

		ObjectNode latestReport = jacksonMapper.createObjectNode( ) ;
		latestReport.put( AlertFields.collectionCount.json, counterCollections.incrementAndGet( ) ) ;
		latestReport.put( AlertFields.uptime.json, TimeUnit.MILLISECONDS.toSeconds( ManagementFactory
				.getRuntimeMXBean( ).getUptime( ) ) ) ;
		latestReport.put( AlertFields.lastCollected.json, timeDayFormat.format( new Date( ) ) ) ;
		latestReport.put( AlertFields.healthy.json, true ) ;
		latestReport.putArray( AlertFields.undefined.json ) ;
		latestReport.putArray( AlertFields.pending.json ) ;
		latestReport.putArray( AlertFields.limitsExceeded.json ) ;
		return latestReport ;

	}

	private void checkConfiguredLimits ( ObjectNode healthReport ) {

		// stopWatches
		for ( AlertInstance alert : settings.getAlertDefinitions( ) ) {

			logger.debug( "Checking: {}", alert ) ;

			if ( alert.isPendingFirstCollection( ) ) {

				// first collection not done yet
				ArrayNode pending = (ArrayNode) healthReport.get( AlertFields.pending.json ) ;
				pending.add( alert.getId( ) ) ;

			}

			// Simon latestSimon = SimonManager.getSimon( alert.getId() ) ;
			// Meter latestMeter = metricsUtilities.findMicroMeter( alert.getId() ) ;

			if ( alert.getLatestSample( ) == null ) {

				if ( ! alert.isPendingFirstCollection( ) && ! alert.isIgnoreNull( ) ) {

					logger.debug( "Did not find: {}", alert.getId( ) ) ;
					ArrayNode missingAlert = (ArrayNode) healthReport.get( AlertFields.undefined.json ) ;
					missingAlert.add( alert.getId( ) ) ;

					addFailure( healthReport, undefinedAlert,
							alert.getId( ), "Verify alert id or add ignore null to configuration" ) ;

					// SimonManager.getCounter( CsapGlobalId.UNDEFINED_ALERTS.id ).increase() ;
					metricsUtilities.incrementCounter( CsapGlobalId.UNDEFINED_ALERTS.id ) ;

				}

			} else {

				checkAlertInstanceForThresholds( healthReport, alert ) ;
				// if ( latestMeter instanceof Timer ) {
				// checkTimerForAlerts( healthReport, alert ) ;
				//
				// } else if ( latestMeter instanceof Counter ) {
				// isCounterHeathy( healthReport, alert ) ;
				// }

			}

		}

		for ( AlertInstance alert : settings.getAllAlertInstances( ) ) {

			if ( alert.getCustomHealth( ) != null ) {

				String alertComponentName = alert.getCustomHealth( ).getComponentName( ) ;
				logger.debug( "Invoking Custom Health API for: {}", alertComponentName, alert.getCustomHealth( )
						.getClass( ).getName( ) ) ;

				try {

					boolean isHealthy = alert.getCustomHealth( ).isHealthy( healthReport ) ;

					if ( ! isHealthy ) {

						// SimonManager.getCounter( alert.getId() ).increase() ;
						metricsUtilities.incrementCounter( alert.getId( ) ) ;

					} else {

						// SimonManager.getCounter( alertComponentName + ".passed" ).increase() ;
						metricsUtilities.incrementCounter( alertComponentName + ".passed" ) ;

					}

				} catch ( Exception e ) {

					addFailure( healthReport, alert, e.getClass( ).getSimpleName( ), 1, 0, false ) ;
					logger.error( "Failed to execute custom health	: {}", alertComponentName, e ) ;

				}

			}

			;

		}

		return ;

	}

	public void checkAlertInstanceForThresholds ( ObjectNode healthReport , AlertInstance alertInstance ) {

		logger.debug( "alertInstance id: {}", alertInstance.getId( ) ) ;

		boolean maxErrorsFound = false ;

		// check the last collection interval
		if ( alertInstance.getLatestSample( ) != null ) {
			// StopwatchSample sample = (StopwatchSample)
			// alertInstance.getLastCollectedSample() ;
			// Timer sampleFromLastInterval = (Timer) alertInstance.getLatestSample() ;

			var latestSample = alertInstance.getLatestSample( ) ;
			var previousSample = alertInstance.getPreviousSample( ) ;

			if ( previousSample == null ) {

				previousSample = jacksonMapper.createObjectNode( ) ;

			}

			logger.debug( "latest: {}, previous: {}",
					CSAP.jsonPrint( latestSample ),
					CSAP.jsonPrint( previousSample ) ) ;

			var countInInterval = latestSample.path( AlertFields.count.jsonKey( ) ).asLong( )
					- previousSample.path( AlertFields.count.jsonKey( ) ).asLong( ) ;

			if ( countInInterval < alertInstance.getOccurencesMin( ) ) {

				addFailure( healthReport, alertInstance,
						"Occurences - Min", countInInterval, alertInstance.getOccurencesMin( ), false ) ;

			}

			if ( countInInterval > alertInstance.getOccurencesMax( ) ) {

				maxErrorsFound = true ;
				addFailure( healthReport, alertInstance,
						"Occurences - Max", countInInterval, alertInstance.getOccurencesMax( ), false ) ;

			}

			var meanInIntervalMs = Long.MIN_VALUE ;

			var timeInInterval = latestSample.path( AlertFields.totalMs.jsonKey( ) ).asLong( )
					- previousSample.path( AlertFields.totalMs.jsonKey( ) ).asLong( ) ;

			if ( countInInterval > 0 ) {

				meanInIntervalMs = timeInInterval / countInInterval ;

			}

			var meanLimit = TimeUnit.NANOSECONDS.toMillis( alertInstance.getMeanTimeNano( ) ) ;

			logger.debug( "mean - current: {}, limit: {}", meanInIntervalMs, meanLimit ) ;

			if ( meanInIntervalMs > meanLimit ) {

				addFailure( healthReport, alertInstance, "Time - Mean", meanInIntervalMs, meanLimit, true ) ;

			}

			var maxInBucketSample = latestSample.path( AlertFields.bucketMaxMs.jsonKey( ) ).asLong( ) ;
			var maxLimit = TimeUnit.NANOSECONDS.toMillis( alertInstance.getMaxTimeNano( ) ) ;

			if ( maxInBucketSample > maxLimit ) {

				maxErrorsFound = true ;
				addFailure( healthReport, alertInstance,
						"Time - Max", maxInBucketSample, maxLimit, true ) ;

			}

		}

		// Also check latest samples for MAX - will report in next health
		// report, versus wating for completion of interval
		// -- do not double report errors
		if ( ! maxErrorsFound ) {
			// StopwatchSample sampleFromCurrentInterval = SimonManager.getStopwatch(
			// alertInstance.getId() )
			// .sampleIncrementNoReset( alertInstance.getSampleName() ) ;

			var targetMeter = metricsUtilities.find( alertInstance.getId( ) ) ;

			if ( targetMeter == null ) {

				logger.warn( "Failed to locate alert: {} - known side effect of using clear.", alertInstance
						.getId( ) ) ;
				return ;

			}

			var nowSample = meterReport.buildMeterReport( targetMeter, 3, false ) ;
			var latestSample = alertInstance.getLatestSample( ) ;

			var count = nowSample.path( AlertFields.count.jsonKey( ) ).asLong( )
					- latestSample.path( AlertFields.count.jsonKey( ) ).asLong( ) ;

			if ( count > alertInstance.getOccurencesMax( ) ) {

				addFailure( healthReport, alertInstance,
						"Occurences - Max", count,
						alertInstance.getOccurencesMax( ), false ) ;

			}

			var max = nowSample.path( AlertFields.bucketMaxMs.jsonKey( ) ).asLong( ) ;
			var maxLimit = TimeUnit.NANOSECONDS.toMillis( alertInstance.getMaxTimeNano( ) ) ;

			if ( max > maxLimit ) {

				addFailure( healthReport, alertInstance,
						"Time - Max*", max, maxLimit, true ) ;

			}

		}

		return ;

	}

	public void addFailure ( CustomHealth health , ObjectNode healthReport , String description ) {

		Optional<AlertInstance> instance = settings.findAlertForComponent( health.getComponentName( ) ) ;

		if ( instance.isPresent( ) ) {

			addFailure( healthReport, instance.get( ), "CustomHealth", description ) ;

		} else {

			logger.error( "Failed to locate component: ", health.getComponentName( ) ) ;

		}

	}

	private void addFailure (
								ObjectNode healthReport ,
								AlertInstance alertInstance ,
								String type ,
								long collected ,
								long limit ,
								boolean isTime ) {

		addFailure( healthReport, alertInstance, type, null, collected, limit, isTime ) ;

	}

	private void addFailure (
								ObjectNode healthReport ,
								AlertInstance alertInstance ,
								String type ,
								String description ) {

		addFailure( healthReport, alertInstance, type, description, -1, -1, false ) ;

	}

	private void addFailure (
								ObjectNode healthReport ,
								AlertInstance alertInstance ,
								String type ,
								String description ,
								long collected ,
								long limit ,
								boolean isTime ) {
		// report.append( "\n Category: " + id
		// + " " + type + ": " + SimonUtils.presentNanoTime( collected )
		// + " Limit: " + SimonUtils.presentNanoTime( limit ) );

		if ( alertInstance.isEnabled( ) ) {

			healthReport.put( AlertFields.healthy.json, false ) ;

		} else {

			logger.debug( "Alert has been suppressed in black box: {} ", alertInstance.getId( ) ) ;

		}

		ArrayNode limitsExceeded = (ArrayNode) healthReport.get( "limitsExceeded" ) ;
		ObjectNode item = limitsExceeded.addObject( ) ;
		item.put( AlertInstance.AlertItem.id.json, alertInstance.getId( ) ) ;
		item.put( AlertInstance.AlertItem.type.json, type ) ;

		if ( description != null ) {

			item.put( AlertInstance.AlertItem.description.json, description ) ;

		} else {

			item.put( AlertInstance.AlertItem.collected.json, collected ) ;
			item.put( AlertInstance.AlertItem.limit.json, limit ) ;

			if ( isTime ) {

				item.put( AlertInstance.AlertItem.description.json, " Collected: " + CSAP.timeUnitPresent( collected ) +
						", Limit: " + CSAP.timeUnitPresent( limit ) ) ;

			} else {

				item.put( AlertInstance.AlertItem.description.json, " Collected: " + collected +
						", Limit: " + limit ) ;

			}

		}

	}

	public ObjectNode getHealthReport ( ) {

		return healthReport ;

	}

	public void setHealthReport ( ObjectNode healthReport ) {

		this.healthReport = healthReport ;

	}

	public AlertInstance getUndefinedAlert ( ) {

		return undefinedAlert ;

	}

	public AlertInstance getFailRunAlert ( ) {

		return failRunAlert ;

	}

	public AlertSettings getSettings ( ) {

		return settings ;

	}

	public void setSettings ( AlertSettings settings ) {

		this.settings = settings ;

	}

}
