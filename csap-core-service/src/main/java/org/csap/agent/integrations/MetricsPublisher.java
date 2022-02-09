package org.csap.agent.integrations ;

import java.util.Random ;
import java.util.concurrent.Executors ;
import java.util.concurrent.ScheduledExecutorService ;
import java.util.concurrent.ScheduledFuture ;
import java.util.concurrent.TimeUnit ;

import org.apache.commons.lang3.concurrent.BasicThreadFactory ;
import org.csap.agent.CsapApis ;
import org.csap.agent.model.ServiceAlertsEnum ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;

import com.fasterxml.jackson.databind.ObjectMapper ;
import com.fasterxml.jackson.databind.node.ObjectNode ;

public class MetricsPublisher {

	final Logger logger = LoggerFactory.getLogger( getClass( ) ) ;

	BasicThreadFactory publishFactory = new BasicThreadFactory.Builder( )
			.namingPattern( "CsapPublish-%d" )
			.daemon( true )
			.priority( Thread.NORM_PRIORITY )
			.build( ) ;
	ScheduledExecutorService publishExecutorService = Executors
			.newScheduledThreadPool( 1, publishFactory ) ;

	ScheduledFuture<?> publishServiceHandle ;

	private CsapApis csapApis ;
	private ObjectNode metricsPublicationSettings ;

	// ClassPathResource templateLocation = new
	// ClassPathResource("nagiosResultTemplate.xml");
	// String statusTemplate = "";

	public MetricsPublisher ( CsapApis csapApis, ObjectNode metricsPublicationSettings ) {

		this.csapApis = csapApis ;
		this.metricsPublicationSettings = metricsPublicationSettings ;

		// seed defaults to System.currentTimeMillis(), which is generally good
		// enough to spread upload requests
		Random rg = new Random( ) ;
		// wait until AFTER initial collection have occurred, and host has had time to
		// quiesce
		var initialDelaySeconds = 180 + rg.nextInt( 60 ) ;
		var intervalSeconds = metricsPublicationSettings.path( "intervalInSeconds" ).asInt( ) ;

		if ( intervalSeconds > 0 ) {

			publishServiceHandle = publishExecutorService
					.scheduleWithFixedDelay( ( ) -> publishHealthReport( ),
							initialDelaySeconds, intervalSeconds,
							TimeUnit.SECONDS ) ;

			logger.warn( "Health Publishing \n\t interval: {} seconds \n\t starting in: {} \n\t settings: {} ",
					intervalSeconds,
					initialDelaySeconds,
					metricsPublicationSettings.toString( ) ) ;

		}

	}

	String lastResults = "" ;

	public String getLastResults ( ) {

		return lastResults ;

	}

	public void publishHealthReport ( ) {

		Thread.currentThread( ).setName( "csap-metrics-publisher" ) ;
		logger.debug( "Invoking:  " + metricsPublicationSettings.toString( ) ) ;

		if ( metricsPublicationSettings.path( "type" ).asText( ).equals( "nagios" ) ) {

			lastResults = NagiosIntegration.publishHealthReport( metricsPublicationSettings, csapApis,
					isIntegrationEnabled( ) ) ;

		} else if ( metricsPublicationSettings.path( "type" ).asText( ).equals( "csap-health-report" )
				|| metricsPublicationSettings.path( "type" ).asText( ).equals( "csapCallHome" ) ) {

			lastResults = publishHealthReportToCsapEvents( ) ;

		} else {

			logger.warn( "Unknown publish type: " + metricsPublicationSettings.toString( ) ) ;

		}

	}

	public void stop ( ) {

		logger.debug( "*************** Shutting down  **********************" ) ;

		if ( publishServiceHandle != null ) {

			publishExecutorService.shutdownNow( ) ;

		}

	}

	boolean integrationEnabled = false ;

	public boolean isIntegrationEnabled ( ) {

		return integrationEnabled ;

	}

	public void setIntegrationEnabled ( boolean integrationEnabled ) {

		this.integrationEnabled = integrationEnabled ;

	}

	ObjectMapper jacksonMapper = new ObjectMapper( ) ;

	final public static String CSAP_HEALTH = "/csap/health" ;

	private String publishHealthReportToCsapEvents ( ) {

		StringBuilder uploadResults = new StringBuilder( "**Uploaded CSAP health: " ) ;

		try {

			ObjectNode applicationStatus = csapApis.application( ).healthManager( ).statusForAdminOrAgent(
					ServiceAlertsEnum.ALERT_LEVEL, false ) ;
			csapApis.events( ).publishEvent( CSAP_HEALTH,
					"Health Data", null, applicationStatus ) ;

		} catch ( Throwable e ) {

			uploadResults.append( "  -  Warning Failed response: " + e.getMessage( ) ) ;

		}

		return uploadResults.toString( ) ;

	}

	/**
	 * 
	 * Nagios requires 3 hooks - disable SSL checks - auth headers - form params
	 * 
	 */

	// MUST update nagiosTemplat.cfg
	private static String[] nagiosServices = {
			"cpuLoad", "memory", "disk", "processes"
	} ;

	public static final String NAGIOS_WARN = "1" ;

	public static String[] getNagiosServices ( ) {

		return nagiosServices ;

	}

}
