package org.csap.agent.ui.windows ;

import java.io.File ;
import java.io.IOException ;
import java.text.DateFormat ;
import java.text.SimpleDateFormat ;
import java.util.Date ;
import java.util.Random ;

import javax.inject.Inject ;
import javax.servlet.http.HttpServletResponse ;

import org.apache.commons.lang3.text.WordUtils ;
import org.csap.agent.CsapCoreService ;
import org.csap.agent.CsapTemplate ;
import org.csap.agent.integrations.CsapEvents ;
import org.csap.agent.integrations.MetricsPublisher ;
import org.csap.agent.model.Application ;
import org.csap.agent.stats.OsProcessCollector ;
import org.csap.agent.stats.OsSharedResourcesCollector ;
import org.csap.agent.stats.ServiceCollector ;
import org.csap.helpers.CSAP ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty ;
import org.springframework.http.MediaType ;
import org.springframework.stereotype.Controller ;
import org.springframework.util.LinkedMultiValueMap ;
import org.springframework.util.MultiValueMap ;
import org.springframework.web.bind.annotation.RequestMapping ;
import org.springframework.web.bind.annotation.RequestParam ;

import com.fasterxml.jackson.databind.ObjectMapper ;
import com.fasterxml.jackson.databind.node.ArrayNode ;
import com.fasterxml.jackson.databind.node.LongNode ;
import com.fasterxml.jackson.databind.node.ObjectNode ;

/**
 * 
 * Helper class for wiring into Jmeter tests
 * 
 * @author someDeveloper
 *
 */
@Controller
@ConditionalOnProperty ( "myServiceConfiguration.test.enabled" )
@RequestMapping ( CsapCoreService.TEST_URL )
public class Agent_Capacity_Tests {

	final Logger logger = LoggerFactory.getLogger( getClass( ) ) ;

	private ObjectMapper jacksonMapper = new ObjectMapper( ) ;

	@Inject
	CsapEvents csapEventClient ;

	@RequestMapping ( "/nullPointer" )
	public void nullPointer ( ) {

		throw new NullPointerException( "Testing Spring MVC advice handler" ) ;

	}

	@Inject
	private Application csapApp ;

	final static String PAYLOAD = "Each line is 100 bytes ===============================================================\n"
			+
			"Each line is 100 bytes ===============================================================\n" +
			"Each line is 100 bytes ===============================================================\n" ;

	/**
	 * 
	 * use a random event generator if both random hosts and days are non-zero
	 * 
	 * @param numRandomDays
	 * @param numRandomHosts
	 * @param numberEvents
	 * @param category
	 * @param summary
	 * @param response
	 * @throws IOException
	 */
	@RequestMapping ( "/testEvent" )
	public void testEvent (
							@RequestParam ( value = "numRandomDays" , defaultValue = "0" , required = false ) int numRandomDays ,
							@RequestParam ( value = "numRandomHosts" , defaultValue = "0" , required = false ) int numRandomHosts ,
							@RequestParam ( value = "numberEvents" , defaultValue = "1" , required = false ) int numberEvents ,
							@RequestParam ( value = "category" , defaultValue = CsapEvents.CSAP_USER_SERVICE_CATEGORY
									+ "/testCsAgent" , required = false ) String category ,
							@RequestParam ( value = "summary" , defaultValue = "Test Message" , required = false ) String summary ,
							HttpServletResponse response )
		throws IOException {

		if ( logger.isDebugEnabled( ) ) {

			logger.debug( "getting CPU" ) ;

		}

		response.setContentType( MediaType.TEXT_PLAIN_VALUE ) ;

		ObjectNode data = jacksonMapper.createObjectNode( ) ;

		if ( category.startsWith( CsapEvents.CSAP_USER_SERVICE_CATEGORY ) ) {

			data.put( "text", PAYLOAD ) ;
			data.put( "uiUser", "testId" ) ;

		}

		String result = "posted event" ;

		try {

			for ( int i = 1; i <= numberEvents; i++ ) {

				if ( numRandomDays == 0 || numRandomHosts == 0 ) {

					csapEventClient.publishEvent(
							category,
							summary + " - Batch: " + i,
							data, null ) ;

				} else {

					publishRandomEvent( numRandomDays, numRandomHosts,
							summary + " - Batch: " + i ) ;

				}

			}

		} catch ( Exception e ) {

			// just keep the test rolling, but print warnings in logs
			// result = "Failed to post event: " + e.getMessage();
			logger.warn( "Exception posting event: " + e.getMessage( ) ) ;

			try {

				Thread.sleep( 5000 ) ;

			} catch ( InterruptedException e1 ) {

				// TODO Auto-generated catch block
				e1.printStackTrace( ) ;

			}

		}

		response.getWriter( ).println( result ) ;

	}

	private String[] lifecycles = {
			"dev", "stage", "lt", "prod"
	} ;
	private String[] appIds = {
			"csapir.gen", "csappss.gen", "csapssp.gen", "csapcset.gen", "system"
	} ;
	private String[] categories = {
			CsapEvents.CSAP_USER_SERVICE_CATEGORY, MetricsPublisher.CSAP_HEALTH,
			OsSharedResourcesCollector.VM_METRICS_EVENT
	} ;
	private Random javaRandom = new Random( ) ;

	static final long MINUTES_15_MS = 15 * 60 * 1000 ;
	static final long HOUR_MS = 60 * 60 * 1000 ;
	static final long DAY_MS = 24 * HOUR_MS ;

	ObjectNode vmTestData = null ;
	ObjectNode vmStatusData = null ;
	ObjectNode serviceTestData = null ;
	ObjectNode jmxTestData = null ;

	public void publishRandomEvent ( int numRandomDays , int numRandomHosts , String summary )
		throws Exception {

		String life = lifecycles[ javaRandom.nextInt( lifecycles.length ) ] ;
		// seed defaults to System.currentTimeMillis(), which is generally good
		// enough to spread upload requests

		String appId = appIds[ javaRandom.nextInt( appIds.length ) ] ;
		String pass = "5Rzsdzt5dHARBQjZlcmgLiIpwFhY+37C" ;

		// random day/hour/15 minute interval
		long nowMs = System.currentTimeMillis( )
				- ( javaRandom.nextInt( numRandomDays ) * DAY_MS )
				- ( javaRandom.nextInt( 24 ) * HOUR_MS )
				- ( javaRandom.nextInt( 4 ) * MINUTES_15_MS ) ;

		if ( logger.isDebugEnabled( ) )
			logger.debug( "appId: " + appId ) ;

		if ( appId.equals( "system" ) ) {

			appId = csapApp.rootProjectEnvSettings( ).getEventDataUser( ) ;
			pass = csapApp.rootProjectEnvSettings( ).getEventDataPass( ) ;

		}

		String hostName = appId + "_" + life + javaRandom.nextInt( numRandomHosts ) ;

		String category = categories[ javaRandom.nextInt( categories.length ) ] ;
		ObjectNode meta = null ;
		ObjectNode data = null ;

		if ( category.startsWith( CsapEvents.CSAP_USER_SERVICE_CATEGORY ) ) {

			category += "/testService" + javaRandom.nextInt( 10 ) ; // 10 test
																	// Services
			meta = jacksonMapper.createObjectNode( ) ;
			meta.put( "text", PAYLOAD ) ;
			meta.put( "uiUser", "testId" ) ;

		} else if ( category.startsWith( MetricsPublisher.CSAP_HEALTH ) ) {

			// Using live data will overwhelm process
			// data = csapApp.getVmStatus();
			if ( vmStatusData == null ) {

				try {

					File testFile = CsapTemplate.test_host_status_collection.getFile( ) ;
					logger.info( "loading  testFile: " + testFile.getAbsolutePath( ) ) ;
					vmStatusData = (ObjectNode) jacksonMapper.readTree( testFile ) ;

				} catch ( Exception e ) {

					logger.error( "Failed reading data file: {}", CSAP.buildCsapStack( e ) ) ;

				}

			}

			data = vmStatusData ;

		} else if ( category.startsWith( OsSharedResourcesCollector.VM_METRICS_EVENT ) ) {

			category += "30/data" ;

			if ( vmTestData == null ) {

				try {

					File testFile = CsapTemplate.test_host_collection.getFile( ) ;
					logger.info( "loading  testFile: " + testFile.getAbsolutePath( ) ) ;
					vmTestData = (ObjectNode) jacksonMapper.readTree( testFile ) ;

					testFile = CsapTemplate.test_service_os_collection.getFile( ) ;
					logger.info( "loading  testFile: " + testFile.getAbsolutePath( ) ) ;
					serviceTestData = (ObjectNode) jacksonMapper.readTree( testFile ) ;

					testFile = CsapTemplate.test_service_jmx_collection.getFile( ) ;
					logger.info( "loading  testFile: " + testFile.getAbsolutePath( ) ) ;
					jmxTestData = (ObjectNode) jacksonMapper.readTree( testFile ) ;

				} catch ( Exception e ) {

					logger.error( "Failed reading data file: {}", CSAP.buildCsapStack( e ) ) ;

				}

			}

			data = updateMetricData( nowMs, hostName, vmTestData ) ;

		}

		if ( logger.isDebugEnabled( ) )
			logger.debug( "data: \n" + jacksonMapper.writerWithDefaultPrettyPrinter( ).writeValueAsString( data ) ) ;

		httpSendEvent( summary, life, appId, pass, nowMs, hostName, category, meta, data ) ;

		if ( category.startsWith( OsSharedResourcesCollector.VM_METRICS_EVENT ) ) {

			httpSendEvent( summary, life, appId, pass, nowMs, hostName,
					OsProcessCollector.PROCESS_METRICS_EVENT + "30/data", meta,
					updateMetricData( nowMs, hostName, serviceTestData ) ) ;
			httpSendEvent( summary, life, appId, pass, nowMs, hostName,
					ServiceCollector.JAVA_METRICS_EVENT + "30/data", meta,
					updateMetricData( nowMs, hostName, jmxTestData ) ) ;

		}

		return ;

	}

	private void httpSendEvent (
									String summary ,
									String life ,
									String appId ,
									String pass ,
									long nowMs ,
									String hostName ,
									String category ,
									ObjectNode meta ,
									ObjectNode data )
		throws Exception {

		MultiValueMap<String, String> formParams = new LinkedMultiValueMap<String, String>( ) ;

		// Build jsDoc parameter
		// Map<String, String> map = new HashMap<String, String>();

		ObjectNode eventJson = jacksonMapper.createObjectNode( ) ;
		eventJson.put( "category", category ) ;
		eventJson.put( "summary", summary ) ;
		eventJson.put( "lifecycle", life ) ;
		eventJson.put( "project", appId + "Test Project" ) ;
		eventJson.put( "host", hostName ) ;

		ObjectNode createdOn = eventJson.putObject( "createdOn" ) ;

		createdOn.put( "unixMs", nowMs ) ;

		DateFormat df2 = new SimpleDateFormat( "yyyy-MM-dd" ) ;
		Date now = new Date( nowMs ) ;
		createdOn.put( "date", df2.format( now ) ) ;

		DateFormat df1 = new SimpleDateFormat( "HH:mm:ss" ) ;
		createdOn.put( "time", df1.format( now ) ) ;

		if ( meta != null )
			eventJson.set( "metaData", meta ) ;

		if ( data != null )
			eventJson.set( "data", data ) ;

		formParams.add( "eventJson", jacksonMapper.writerWithDefaultPrettyPrinter( ).writeValueAsString( eventJson ) ) ;
		formParams.add( "userid", appId ) ;
		formParams.add( "pass", pass ) ;

		// logger.info("Message Converters loaded: " +
		// auditPostTemplate.getMessageConverters());

		if ( logger.isDebugEnabled( ) )
			logger.debug( "Posting url " + csapApp.rootProjectEnvSettings( ).getEventUrl( )
					+ " params: "
					+ WordUtils.wrap( formParams.toString( ), 140 ) ) ;

		csapEventClient.testingOnlyPostEvent( formParams ) ;

	}

	private ObjectNode updateMetricData ( long nowMs , String hostName , ObjectNode testData ) {

		ObjectNode data ;
		data = testData.deepCopy( ) ; // copy test data, modify it

		ObjectNode attNode = (ObjectNode) data.path( "attributes" ) ;
		attNode.put( "hostName", hostName ) ;
		attNode.put( "currentTimeMillis", nowMs ) ;

		ArrayNode timeStamps = (ArrayNode) data.path( "data" ).path( "timeStamp" ) ;

		for ( int i = 0; i < timeStamps.size( ); i++ ) {

			long offset = i * 30000 ; // timeStamps in reverse order LIFO. latest
										// time is first
			timeStamps.set( i, LongNode.valueOf( nowMs - offset ) ) ;

		}

		return data ;

	}
}
