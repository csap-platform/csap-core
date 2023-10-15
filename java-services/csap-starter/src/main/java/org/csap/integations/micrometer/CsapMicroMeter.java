package org.csap.integations.micrometer ;

import java.net.InetAddress ;
import java.time.LocalDateTime ;
import java.time.format.DateTimeFormatter ;

import javax.servlet.http.HttpServletRequest ;

import org.apache.commons.lang3.StringUtils ;
import org.csap.alerts.AlertProcessor ;
import org.csap.helpers.CsapApplication ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.context.annotation.Bean ;
import org.springframework.context.annotation.Configuration ;
import org.springframework.context.annotation.Import ;
import org.springframework.scheduling.annotation.Scheduled ;
import org.springframework.web.bind.annotation.GetMapping ;
import org.springframework.web.bind.annotation.RequestMapping ;
import org.springframework.web.bind.annotation.RestController ;

import com.fasterxml.jackson.databind.ObjectMapper ;
import com.fasterxml.jackson.databind.node.ArrayNode ;
import com.fasterxml.jackson.databind.node.ObjectNode ;

import io.micrometer.core.aop.TimedAspect ;
import io.micrometer.core.instrument.Counter ;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry ;

@Configuration
@Import ( {
		CsapMicroRegistryConfiguration.class,
		MeterReport.class
} )
public class CsapMicroMeter {

	final Logger logger = LoggerFactory.getLogger( getClass( ) ) ;

	public CsapMicroMeter ( ) {

		logger.debug( CsapApplication.testHeader( ) ) ;

	}

	public static final String BASE_URI = "/csap" ;

	//
	// By default, micrometer @Timed is ONLY supported in SpringMvc:
	// @RestController, etc.
	// - TimedAspect enables registration in other classes: @JmsListener, etc.
	//
	@Bean
	public TimedAspect timedAspect ( SimpleMeterRegistry registry ) {

		return new TimedAspect( registry ) ;

	}

	// customize all registries - including promethesius
	// @Bean
	// public MeterRegistryCustomizer<MeterRegistry> microMeterSettings () {
	// }

	@RestController
	@RequestMapping ( BASE_URI )
	static public class HealthReport {

		final Logger logger = LoggerFactory.getLogger( getClass( ) ) ;

		ObjectMapper jacksonMapper = new ObjectMapper( ) ;
		SimpleMeterRegistry microMeterRegistry ;

		AlertProcessor alertProcessor = null ;

		public HealthReport ( SimpleMeterRegistry microMeterRegistry, ObjectMapper jacksonMapper ) {

			this.jacksonMapper = jacksonMapper ;
			this.microMeterRegistry = microMeterRegistry ;

		}

		private volatile ArrayNode errors = jacksonMapper.createArrayNode( ) ;
		private volatile ArrayNode offLineErrors = jacksonMapper.createArrayNode( ) ;

		public enum Report {
			name( "health-report" ),
			source( "source" ),
			undefined( "undefined" ), pending( "pendingFirstInterval" ),
			healthy( "isHealthy" ),
			collectionCount( "collectionCount" ),
			errors( "errors" ), id( "id" ), type( "type" ), description( "description" ),
			lastCollected( "lastCollected" );

			public String json ;

			private Report ( String jsonField ) {

				this.json = jsonField ;

			}
		}

		private final long SECONDS = 1000 ;

		@Scheduled ( initialDelay = 10 * SECONDS , fixedDelay = 30 * SECONDS )
		public void update_health_status ( ) {

			// Create a worker store....
			ArrayNode localErrors = jacksonMapper.createArrayNode( ) ;

			//
			// Put all health monitoring calls here.
			// Note: this is @Scheduled to NEVER block collection thread
			//
			// eg. if (db.runQuery() == null ) localErrors.add("DB Query Failed") ;
			// eg. if ( isMyAppFailingByCallingManyMethods() ) localErrors.add("App Summary
			// check Failed") ;

			// offLineErrors cleared/set by external thread(s)
			if ( getOffLineErrors( ).size( ) > 0 ) {

				localErrors.addAll( getOffLineErrors( ) ) ;

			}

			// Finally update for subsequent reports
			setErrors( localErrors ) ;

		}

		/**
		 * 
		 * @param errorId: used for throttling content
		 * @param type:    general classification (short)
		 * @param message: longer
		 * @return
		 */
		public ObjectNode buildError ( String errorId , String type , String message ) {

			ObjectNode error = jacksonMapper.createObjectNode( ) ;
			error.put( Report.id.json, errorId ) ;
			error.put( Report.type.json, type ) ;
			error.put( Report.description.json, message ) ;
			return error ;

		}

		public ObjectNode buildAlertReport ( HttpServletRequest request ) {

			ObjectNode healthReport = jacksonMapper.createObjectNode( ) ;

			if ( alertProcessor == null ) {

				return buildEmbeddedReport( request ) ;

			}

			var csapAlertHealth = alertProcessor.getHealthReport( ) ;

			if ( csapAlertHealth != null ) {

				healthReport.set( Report.name.json, csapAlertHealth ) ;
				return healthReport ;

			}

			return healthReport ;

		}

		@GetMapping ( "/health/report" )
		public ObjectNode buildEmbeddedReport ( HttpServletRequest request ) {

			ObjectNode healthReport = jacksonMapper.createObjectNode( ) ;
			ObjectNode latestReport = healthReport.putObject( Report.name.json ) ;

			if ( microMeterRegistry != null ) {

				Counter reportCount = microMeterRegistry.counter( getClass( ).getSimpleName( ) + ".count" ) ;
				reportCount.increment( ) ;
				latestReport.put( Report.collectionCount.json, reportCount.count( ) ) ;

			}

			latestReport.put( Report.lastCollected.json, getTimestamp( ) ) ;

			latestReport.put( Report.healthy.json, errors.size( ) == 0 ) ;

			// latestReport.putArray( Report.undefined.json );
			// latestReport.putArray( Report.pending.json );
			latestReport.set( Report.errors.json, errors ) ;
			latestReport.set( Report.source.json, buildSourceReport( request ) ) ;

			return healthReport ;

		}

		public String getTimestamp ( ) {

			return LocalDateTime.now( ).format( DateTimeFormatter.ofPattern( "HH:mm:ss, MMM d" ) ) ;

		}

		@GetMapping ( "/health/test/add" )
		public ObjectNode addTestError ( HttpServletRequest request ) {

			offLineErrors.add( buildError( "test.id", "test-demo", getTimestamp( ) + " Added demo issue" ) ) ;
			update_health_status( ) ;
			return buildEmbeddedReport( request ) ;

		}

		@GetMapping ( "/health/test/clear" )
		public ObjectNode clear ( HttpServletRequest request ) {

			getOffLineErrors( ).removeAll( ) ;
			update_health_status( ) ;
			return buildEmbeddedReport( request ) ;

		}

		public ArrayNode getErrors ( ) {

			return errors ;

		}

		public void setErrors ( ArrayNode errors ) {

			this.errors = errors ;

		}

		private ObjectNode buildSourceReport ( HttpServletRequest request ) {

			ObjectNode source = jacksonMapper.createObjectNode( ) ;
			source.put( "collected-at", LocalDateTime.now( ).format( DateTimeFormatter.ofPattern(
					"HH:mm:ss, MMMM d uuuu" ) ) ) ;

			try {

				source.put( "hostName", InetAddress.getLocalHost( ).getHostName( ) ) ;

			} catch ( Exception e1 ) {

			}

			if ( request != null ) {

				String req = request.getRequestURL( ).toString( ) ;

				if ( request.getQueryString( ) != null ) {

					req = request.getRequestURL( ).toString( ) + "?" + request.getQueryString( ) ;

				}

				if ( StringUtils.isNotEmpty( request.getParameter( "help" ) ) ) {

					source.put( "url-requested", req ) ;
					source.put( "default", request.getRequestURL( ).toString( )
							+ "?help=help&" ) ;
					source.put( "sample-params", request.getRequestURL( ).toString( )
							+ "?aggregate=true&nameFilter=jvm.*&details=false&precision=2&tagFilter=state&tagFilter="
							+ CsapMicroRegistryConfiguration.CSAP_COLLECTION_TAG + "&help=help&" ) ;
					source.put( "sample-http", request.getRequestURL( ).toString( )
							+ "?nameFilter=http.server.*&details=true&encode=true&precision=2" + "&help=help&" ) ;

				}

			}

			return source ;

		}

		public ArrayNode getOffLineErrors ( ) {

			return offLineErrors ;

		}

		public void setOffLineErrors ( ArrayNode offLineErrors ) {

			this.offLineErrors = offLineErrors ;

		}

		public void setAlertProcessor ( AlertProcessor alertProcessor ) {

			this.alertProcessor = alertProcessor ;

		}

	}

}
