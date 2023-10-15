package org.csap.alerts ;

import java.security.Principal ;
import java.text.SimpleDateFormat ;
import java.util.Date ;
import java.util.HashMap ;
import java.util.Random ;
import java.util.concurrent.TimeUnit ;
import java.util.concurrent.atomic.AtomicInteger ;

import javax.annotation.PostConstruct ;
import javax.servlet.http.HttpServletRequest ;

import org.csap.docs.CsapDoc ;
import org.csap.integations.CsapInformation ;
import org.csap.integations.micrometer.CsapMeterUtilities ;
import org.csap.integations.micrometer.CsapMicroRegistryConfiguration ;
import org.csap.integations.micrometer.MeterReport ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.beans.factory.annotation.Autowired ;
import org.springframework.http.MediaType ;
import org.springframework.stereotype.Controller ;
import org.springframework.ui.ModelMap ;
import org.springframework.web.bind.annotation.GetMapping ;
import org.springframework.web.bind.annotation.RequestMapping ;
import org.springframework.web.bind.annotation.RequestParam ;
import org.springframework.web.bind.annotation.ResponseBody ;
import org.springframework.web.servlet.resource.ResourceUrlProvider ;

import com.fasterxml.jackson.databind.ObjectMapper ;
import com.fasterxml.jackson.databind.node.ArrayNode ;
import com.fasterxml.jackson.databind.node.ObjectNode ;

@Controller
@RequestMapping ( "${csap.baseContext:/csap}" )
@CsapDoc ( title = "CSAP Performance Alerts" , type = CsapDoc.OTHER , notes = "Provides both dashboard and rest API for alerts" )
public class AlertsController {

	final Logger logger = LoggerFactory.getLogger( getClass( ) ) ;

	public AlertsController ( AlertSettings alertSettings ) {

		logger.debug( "\n\n\n\n Created Alerts Controller \n\n\n\n" ) ;
		this.alertSettings = alertSettings ;

	}

	@Autowired
	MeterReport meterReport ;

	@Autowired
	CsapMeterUtilities metricUtilities ;

	@Autowired
	ResourceUrlProvider mvcResourceUrlProvider ;

	@Autowired
	private CsapInformation csapInfo ;

	@PostConstruct
	public void add_references ( ) {

		// work around cyclic dependency
		meterReport.setAlertController( this ) ;

	}

	// cache blowout strategy for requires.js: generate a unique path on every start
	public String requiresUrl ( String path ) {

		String requiresJsName = mvcResourceUrlProvider.getForLookupPath( path ) ;
		return requiresJsName ;

	}

	@Autowired ( required = false )
	AlertProcessor alertProcessor ;

	AlertSettings alertSettings ;

	public final static String HEALTH_URL = "/health" ;

	@CsapDoc ( notes = "Health Dashboard for viewing alerts" , baseUrl = "/csap" )
	@GetMapping ( HEALTH_URL )
	public String healthDashboard ( ModelMap springViewModel , HttpServletRequest request ) {

		springViewModel.addAttribute( "cacheSafeUrl", requiresUrl( "/js/modules/alerts/alerts-main.js" ) ) ;

		HashMap<String, String> settings = new HashMap<>( ) ;
		settings.put( "Health Report Interval", alertSettings.getReport( ).getIntervalSeconds( ) + " seconds" ) ;
		settings.put( "Maximum items to store", alertSettings.getRememberCount( ) + "" ) ;
		settings.put( "Email Notifications", alertSettings.getNotify( ).toString( ) ) ;
		settings.put( "Alert Throttles", alertSettings.getThrottle( ).toString( ) ) ;

		springViewModel.addAttribute( "defaultTagFilter", CsapMicroRegistryConfiguration.CSAP_COLLECTION_TAG ) ;
		springViewModel.addAttribute( "csapPageLabel", "Service Live" ) ;

		var browserTitle = csapInfo.getHostName( ) + "-" + csapInfo.getName( ) + "-live" ;
		var pod = request.getParameter( "pod" ) ;

		if ( pod != null ) {

			browserTitle = csapInfo.getHostName( ) + "-" + pod + "-live" ;

		}

		springViewModel.addAttribute( "browserTitle", browserTitle ) ;
		springViewModel.addAttribute( "settings", settings ) ;
		springViewModel.addAttribute( "definitions", alertSettings.getAllAlertDefinitions( ) ) ;

		springViewModel.addAttribute( "customCollect",
				alertSettings.getReport( ).getFrequency( ) + " " + alertSettings.getReport( ).getTimeUnit( ) ) ;
		springViewModel.addAttribute( "maxBacklog", alertSettings.getRememberCount( ) ) ;
		return "csap/alerts/health" ;

	}

	private ObjectMapper jacksonMapper = new ObjectMapper( ) ;

//	@GetMapping ( value = "/definitions" , produces = MediaType.APPLICATION_JSON_VALUE )
//	@ResponseBody
	public ArrayNode definitions ( ) {

		var defs = jacksonMapper.createArrayNode( ) ;

		alertSettings.getAllAlertDefinitions( ).stream( ).forEach( defs::add ) ;
		;

		return defs ;

	}

	@CsapDoc ( notes = "Get metric details" , baseUrl = "/csap" )
	@GetMapping ( value = "/metric" , produces = MediaType.APPLICATION_JSON_VALUE )
	@ResponseBody
	public ObjectNode metric (
								@RequestParam ( "name" ) String name ,
								@RequestParam ( defaultValue = "Simon" ) String meterView ,
								@RequestParam ( defaultValue = SAMPLE_UI ) String sampleName ) {

		logger.debug( "name: {} sample: {} ", name, sampleName ) ;
		ObjectNode results = jacksonMapper.createObjectNode( ) ;

		metricUtilities.getSimpleMeterRegistry( )
				.getMeters( )
				.stream( )
				.filter( meter -> {

					boolean hideCsapTag = ! name.contains( CsapMicroRegistryConfiguration.CSAP_COLLECTION_TAG ) ;

					String id = CsapMeterUtilities.buildMicroMeterId( meter, hideCsapTag, false ) ;
					logger.info( "id: {}", id ) ;

					if ( id.equals( name ) ) {

						return true ;

					}

					return false ;

				} )
				.findFirst( )
				.ifPresent( meter -> {

					meter.measure( ).forEach( measure -> {

						// measure.
					} ) ;

					results.put( "firstUsage", "-" ) ;
					results.put( "lastUsage", "-" ) ;
					results.put( "maxTimeStamp", "-" ) ;
					StringBuilder details = new StringBuilder( ) ;
					details.append( "Micrometer Name=" + meter.getId( ).getName( ) ) ;
					details.append( ",Description=" + meter.getId( ).getDescription( ) ) ;
					meter.getId( ).getTags( ).stream( )
							.forEach( tag -> {

								details.append( ",tag." + tag.getKey( ) + "=" + tag.getValue( ) ) ;

							} ) ;
					;
					results.put( "details", details.toString( ) ) ;

				} ) ;

		// }

		return results ;

	}

	@CsapDoc ( notes = "Enable/disable the meter from ui" , baseUrl = "/csap" )
	@GetMapping ( value = "/toggleMeter" , produces = MediaType.APPLICATION_JSON_VALUE )
	@ResponseBody
	public ObjectNode toggleMeter (
									@RequestParam ( "id" ) String id ,
									@RequestParam ( "enabled" ) boolean isEnabled ,
									Principal user ) {

		ObjectNode results = jacksonMapper.createObjectNode( ) ;

		String userid = "SecurityDisabled" ;

		if ( user != null ) {

			userid = user.getName( ) ;

		}

		logger.info( "User: {}  setting: {} to: {}", userid, id, isEnabled ) ;
		results.put( "id", id ) ;
		results.put( "enabled", isEnabled ) ;
		results.put( "result", alertSettings.updateLimitEnabled( id, isEnabled, userid ) ) ;

		return results ;

	}

	@CsapDoc ( notes = "Clear the data" , baseUrl = "/csap" )
	@GetMapping ( value = "/clearMetrics" , produces = MediaType.APPLICATION_JSON_VALUE )
	@ResponseBody
	public ObjectNode clearMetrics (
										@RequestParam ( value = "unit" , required = false , defaultValue = "MILLISECONDS" ) String units ,
										@RequestParam ( value = "filters" , required = false , defaultValue = "" ) String filters ) {

		ObjectNode results = jacksonMapper.createObjectNode( ) ;

		AtomicInteger meterCount = new AtomicInteger( 0 ) ;
		AtomicInteger skipped = new AtomicInteger( 0 ) ;

		metricUtilities.getSimpleMeterRegistry( ).getMeters( )
				.forEach( ( meter ) -> {

					var isGauge = meter.getClass( ).getName( ).contains( "auge" ) ;
					logger.debug( "isGauge: {}", isGauge ) ;

					if ( isGauge
							|| meter.getId( ).getName( ).startsWith( "tomcat" )
							|| meter.getId( ).getName( ).startsWith( "system" )
							|| meter.getId( ).getName( ).startsWith( "cache" )
							|| meter.getId( ).getName( ).startsWith( "process" ) ) {

						skipped.incrementAndGet( ) ;

					} else {

						metricUtilities.getSimpleMeterRegistry( ).remove( meter ) ;
						meterCount.incrementAndGet( ) ;

					}

				} ) ;

		results.put( "cleared", meterCount.get( ) ) ;
		results.put( "skipped", skipped.get( ) ) ;

		return results ;

	}

	public final String SAMPLE_UI = "csapUI" ;

	@CsapDoc ( notes = "Get the metrics" , baseUrl = "/csap" )
	@GetMapping ( value = "/metrics" , produces = MediaType.APPLICATION_JSON_VALUE )
	@ResponseBody
	public ObjectNode metrics (
								@RequestParam ( defaultValue = SAMPLE_UI ) String sampleName ,
								@RequestParam ( defaultValue = "" ) String filters ) {

		logger.debug( "sampleName: {}, filters: {}", sampleName, filters ) ;
		ObjectNode results = jacksonMapper.createObjectNode( ) ;

		results.set( "healthReport", alertProcessor.getHealthReport( ) ) ;

		ArrayNode rows = results.putArray( "rows" ) ;

		results = meterReport.buildSimple( null, null, 3, true ) ;

		return results ;

	}

	@CsapDoc ( notes = "Health data showing alerts. Default hours is 4 - and testing is 0" , baseUrl = "/csap" )
	@GetMapping ( value = "/health/alerts" , produces = MediaType.APPLICATION_JSON_VALUE )
	@ResponseBody
	public ObjectNode report (
								@RequestParam ( value = "hours" , required = false , defaultValue = "4" ) int hours ,
								@RequestParam ( value = "testCount" , required = false , defaultValue = "0" ) int testCount ) {

		ObjectNode results = jacksonMapper.createObjectNode( ) ;

		ArrayNode alertsTriggered ;

		if ( testCount == 0 ) {

			alertsTriggered = alertProcessor.getAllAlerts( ) ;

		} else {

			results.put( "testCount", testCount ) ;
			alertsTriggered = jacksonMapper.createArrayNode( ) ;
			Random rg = new Random( ) ;

			for ( int i = 0; i < testCount; i++ ) {

				ObjectNode t = alertsTriggered.addObject( ) ;
				long now = System.currentTimeMillis( ) ;
				long itemTimeGenerated = now - rg.nextInt( (int) TimeUnit.DAYS.toMillis( 1 ) ) ;
				t.put( "ts", itemTimeGenerated ) ;

				// String foundTime = LocalDateTime.now().format(
				// DateTimeFormatter.ofPattern( "HH:mm:ss , MMM d" ) ) ;
				t.put( "id", "test.simon." + rg.nextInt( 20 ) ) ;
				t.put( "type", "type" + rg.nextInt( 20 ) ) ;
				t.put( "time", getFormatedTime( itemTimeGenerated ) ) ;
				t.put( "host", "testHost" + rg.nextInt( 10 ) ) ;
				t.put( "service", "testService" + rg.nextInt( 10 ) ) ;
				t.put( "description", "description" + rg.nextInt( 20 ) ) ;

			}

		}

		if ( hours > 0 ) {

			ArrayNode filteredByHours = jacksonMapper.createArrayNode( ) ;

			long now = System.currentTimeMillis( ) ;
			alertsTriggered.forEach( item -> {

				if ( item.has( "ts" ) ) {

					long itemTime = item.get( "ts" ).asLong( ) ;

					if ( ( now - itemTime ) < TimeUnit.HOURS.toMillis( hours ) ) {

						filteredByHours.add( item ) ;

					}

				}

			} ) ;

			alertsTriggered = filteredByHours ;

		}

		results.set( "triggered", alertsTriggered ) ;

		return results ;

	}

	SimpleDateFormat timeDayFormat = new SimpleDateFormat( "HH:mm:ss , MMM d" ) ;

	private String getFormatedTime ( long tstamp ) {

		Date d = new Date( tstamp ) ;
		return timeDayFormat.format( d ) ;

	}

}
