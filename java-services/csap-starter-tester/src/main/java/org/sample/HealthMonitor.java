package org.sample ;

import java.util.ArrayList ;
import java.util.List ;

import javax.annotation.PostConstruct ;
import javax.inject.Inject ;
import javax.servlet.http.HttpServletRequest ;

import org.csap.alerts.AlertProcessor ;
import org.csap.helpers.CSAP ;
import org.csap.integations.CsapPerformance ;
import org.csap.integations.micrometer.CsapMeterUtilities ;
import org.csap.integations.micrometer.CsapMicroMeter ;
import org.csap.security.config.CsapSecuritySettings ;
import org.sample.Csap_Tester_Application.HealthSettings ;
import org.sample.input.http.ui.windows.JmsController ;
import org.sample.input.jms.SimpleJms ;
import org.sample.jpa.DemoManager ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.beans.factory.annotation.Autowired ;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean ;
import org.springframework.context.annotation.Bean ;
import org.springframework.context.annotation.Configuration ;
import org.springframework.jms.config.JmsListenerEndpointRegistry ;
import org.springframework.jms.listener.DefaultMessageListenerContainer ;
import org.springframework.scheduling.annotation.Scheduled ;
import org.springframework.stereotype.Component ;
import org.springframework.web.bind.annotation.GetMapping ;
import org.springframework.web.bind.annotation.RequestMapping ;
import org.springframework.web.bind.annotation.RestController ;

import com.fasterxml.jackson.databind.ObjectMapper ;
import com.fasterxml.jackson.databind.node.ArrayNode ;
import com.fasterxml.jackson.databind.node.ObjectNode ;

@Configuration
public class HealthMonitor {

	private Logger logger = LoggerFactory.getLogger( getClass( ) ) ;

	ObjectMapper jacksonMapper = new ObjectMapper( ) ;

	@Inject
	private DemoManager testDao ;

	@Inject
	private Csap_Tester_Application myApp ;

	@PostConstruct
	public void showConfiguration ( ) {

		logger.info( "checking health every minute using user: {} at: {}", myApp.getDb( ).getUsername( ), myApp.getDb( )
				.getUrl( ) ) ;

	}

	private final long SECONDS = 1000 ;

	@Scheduled ( initialDelay = 10 * SECONDS , fixedDelay = 60 * SECONDS )
	public void databaseMonitor ( ) {

		// Split monitorTimer = SimonManager.getStopwatch(
		// "HealthMonitor.databaseMonitor" ).start();

		List<String> latestIssues = new ArrayList<>( ) ;

		try {

			long count = testDao.getCountCriteria( DemoManager.TEST_TOKEN ) ;
			ObjectNode result = testDao.showScheduleItemsWithFilter( DemoManager.TEST_TOKEN, 10 ) ;
			logger.debug( "Count: {} Records:\n {}", count, jacksonMapper.writerWithDefaultPrettyPrinter( )
					.writeValueAsString( result ) ) ;

		} catch ( Throwable e ) {

			logger.error( "Failed HealthStatis on DB queries", e ) ;
			latestIssues.add( "Exception QueryingDB: " + myApp.getDb( ).getUrl( ) + " Exception: " + e.getClass( )
					.getSimpleName( ) ) ;

		}

		dbFailures = latestIssues ;

		// monitorTimer.stop();
	}

	@Inject
	private JmsController jmsController ;

	@Scheduled ( initialDelay = 10 * SECONDS , fixedDelay = 60 * SECONDS )
	public void jmsProcessingMonitor ( ) {

		HealthSettings settings = myApp.getJmsBacklogHealth( ) ;

		if ( settings.getHost( ) == null ) {

			return ;

		}

		// Split monitorTimer = SimonManager.getStopwatch(
		// "HealthMonitor.jmsProcessingMonitor" ).start();
		List<String> latestIssues = new ArrayList<>( ) ;

		try {

			ObjectNode result = jmsController.buildHungReport(
					settings.getExpression( ),
					settings.getBacklogQ( ),
					settings.getProcessedQ( ),
					settings.getSampleCount( ),
					settings.getBaseUrl( ),
					settings.getHost( ) ) ;

			logger.debug( "hung: {} Report: {}",
					result.at( "/hungReports/0/isHung" ).asBoolean( ),
					jacksonMapper.writerWithDefaultPrettyPrinter( ).writeValueAsString( result ) ) ;

			if ( result.at( "/hungReports/0/isHung" ).isMissingNode( ) ) {

				latestIssues.add( "Unable to query JMS: " + settings.getHost( ) ) ;

			} else if ( result.at( "/hungReports/0/isHung" ).asBoolean( ) ) {

				latestIssues.add( "Processing Q is not showing activity" ) ;

			}

		} catch ( Throwable e ) {

			logger.error( "Failed HealthStatis on DB queries", e ) ;
			latestIssues.add( "Exception QueryingJms: " + " Exception: " + e.getClass( ).getSimpleName( ) ) ;

		}

		// monitorTimer.stop();
		setJmsFailures( latestIssues ) ;

	}

	@Autowired
	CsapMicroMeter.HealthReport httpHealthReport ;

	private volatile List<String> dbFailures = new ArrayList<>( ) ;
	private volatile List<String> jmsFailures = new ArrayList<>( ) ;
	static private volatile List<String> testFailures = new ArrayList<>( ) ;

	@RestController
	@RequestMapping ( "/health/test" )
	static public class HealthTester {
		@Autowired
		CsapMicroMeter.HealthReport healthReport ;

		@Autowired ( required = false )
		CsapSecuritySettings securitySettings ;

		@GetMapping ( "/add" )
		public ObjectNode addTestError ( HttpServletRequest request ) {

			String errMessage = healthReport.getTimestamp( ) ;

			if ( securitySettings != null ) {

				errMessage += " user: " + securitySettings.getRoles( ).getUserIdFromContext( ) ;

			}

			errMessage += " Added demo issue" ;
			testFailures.add( errMessage ) ;
			healthReport.getOffLineErrors( ).add( healthReport.buildError( "test-message-id", "dev-only",
					errMessage ) ) ;
			healthReport.update_health_status( ) ;
			return healthReport.buildEmbeddedReport( request ) ;

		}

		@GetMapping ( "/clear" )
		public ObjectNode clear ( HttpServletRequest request ) {

			testFailures.clear( ) ;
			healthReport.getOffLineErrors( ).removeAll( ) ;
			healthReport.update_health_status( ) ;
			return healthReport.buildEmbeddedReport( request ) ;

		}
	}

	public List<String> getDbFailures ( ) {

		return dbFailures ;

	}

	@Bean
	@ConditionalOnBean ( AlertProcessor.class )
	public CsapPerformance.CustomHealth myHealth ( ) {

		// Push any work into background thread to avoid blocking collection

		CsapPerformance.CustomHealth health = new CsapPerformance.CustomHealth( ) {

			@Autowired
			AlertProcessor alertProcessor ;

			@Override
			public boolean isHealthy ( ObjectNode mbeanHealthReport )
				throws Exception {

				logger.debug( "Invoking custom health" ) ;

				List<String> offlineErrors = new ArrayList<>( ) ;
				ArrayNode httpHealthErrors = jacksonMapper.createArrayNode( ) ;

				offlineErrors.addAll( getDbFailures( ) ) ;
				offlineErrors.addAll( getJmsFailures( ) ) ;

				if ( ! testFailures.isEmpty( ) ) {

					offlineErrors.addAll( testFailures ) ;

				}

				offlineErrors.forEach( reason -> {

					// @Csap reports
					alertProcessor.addFailure( this, mbeanHealthReport, reason ) ;

					// http reports micrometer
					httpHealthErrors.add( httpHealthReport.buildError( "app-health", "db-or-jms", reason ) ) ;

				} ) ;

				// http reports
				httpHealthReport.setOffLineErrors( httpHealthErrors ) ;

				if ( getDbFailures( ).size( ) > 0 || getJmsFailures( ).size( ) > 0 )
					return false ;
				return true ;

			}

			@Override
			public String getComponentName ( ) {

				return HealthMonitor.class.getName( ) ;

			}
		} ;

		return health ;

	}

	public List<String> getJmsFailures ( ) {

		return jmsFailures ;

	}

	public void setJmsFailures ( List<String> jmsFailures ) {

		this.jmsFailures = jmsFailures ;

	}

	/**
	 * 
	 * JMX endpoint for collecting JMS active listenr
	 * 
	 * @author pnightin
	 *
	 */

	@Component
	public class JmsGauges {

		@Inject
		CsapMeterUtilities microMeterHelper ;

		@Autowired ( required = false )
		JmsListenerEndpointRegistry jmsRegistry ;

		@Autowired ( required = false )
		SimpleJms simpleJms ;

		@PostConstruct
		public void registerMetrics ( ) {

			microMeterHelper.addGauge( "csap.jms.listeners-active", jmsRegistry, registry -> {

				int count = -1 ; // indicates jms is disabled

				if ( simpleJms != null ) {

					try {

						count = ( (DefaultMessageListenerContainer) jmsRegistry
								.getListenerContainer( SimpleJms.TEST_JMS_LISTENER_ID ) )
										.getActiveConsumerCount( ) ;

					} catch ( Exception e ) {

						logger.error( "Failed to get count {}", CSAP.buildCsapStack( e ) ) ;

					}

				}

				return count ;

			} ) ;

		}
	}

	// @Service
	// @ManagedResource ( objectName =
	// "org.csap:application=sample,name=PerformanceMonitor" , description =
	// "Exports performance data to
	// external systems" )
	// public class JmsHealth {
	// // Jms is optionally disabled, so optional injection
	// @Autowired ( required = false )
	// JmsListenerEndpointRegistry jmsRegistry;
	//
	// @Autowired ( required = false )
	// SimpleJms simpleJms;
	//
	// @ManagedMetric ( category = "UTILIZATION" , displayName = "Number of JMS
	// listeners running" , description = "Some time dependent
	// value" , metricType = MetricType.COUNTER )
	// synchronized public int getJmsActive () {
	//
	// int count = -1; // indicates jms is disabled
	// if ( simpleJms != null ) {
	// try {
	// count = ((DefaultMessageListenerContainer) jmsRegistry
	// .getListenerContainer( SimpleJms.TEST_JMS_LISTENER_ID
	// )).getActiveConsumerCount();
	// } catch (Exception e) {
	// logger.error( "Failed to get count", e );
	// }
	// }
	//
	// return count; // Number of messages processed
	// }
	// }

}
