package test.scenario_2_beans ;

import static org.assertj.core.api.Assertions.assertThat ;

import java.time.LocalDateTime ;
import java.time.format.DateTimeFormatter ;
import java.util.Collection ;
import java.util.Collections ;
import java.util.List ;
import java.util.Random ;
import java.util.concurrent.TimeUnit ;
import java.util.stream.Collectors ;

import javax.inject.Inject ;

import org.apache.logging.log4j.LogManager ;
import org.apache.logging.log4j.core.LoggerContext ;
import org.csap.CsapBootApplication ;
import org.csap.alerts.AlertsController ;
import org.csap.docs.DocumentController ;
import org.csap.helpers.CSAP ;
import org.csap.helpers.CsapApplication ;
import org.csap.integations.CsapEncryptionConfiguration ;
import org.csap.integations.CsapInformation ;
import org.csap.integations.micrometer.CsapMicroMeter ;
import org.csap.integations.micrometer.CsapMicroRegistryConfiguration ;
import org.csap.integations.micrometer.MeterReport ;
import org.junit.jupiter.api.Disabled ;
import org.junit.jupiter.api.Test ;
import org.junit.jupiter.api.TestInstance ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.beans.factory.annotation.Autowired ;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration ;
import org.springframework.boot.test.context.SpringBootTest ;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment ;
import org.springframework.boot.test.web.client.TestRestTemplate ;
import org.springframework.boot.web.client.RestTemplateBuilder ;
import org.springframework.boot.web.server.LocalServerPort ;
import org.springframework.context.ApplicationContext ;
import org.springframework.http.ResponseEntity ;
import org.springframework.scheduling.annotation.EnableAsync ;
import org.springframework.scheduling.annotation.SchedulingConfiguration ;
import org.springframework.test.annotation.DirtiesContext ;
import org.springframework.test.context.ActiveProfiles ;
import org.springframework.web.bind.annotation.GetMapping ;
import org.springframework.web.bind.annotation.RestController ;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer ;

import com.fasterxml.jackson.databind.JsonNode ;
import com.fasterxml.jackson.databind.ObjectMapper ;
import com.fasterxml.jackson.databind.node.MissingNode ;
import com.fasterxml.jackson.databind.node.ObjectNode ;

import io.micrometer.core.instrument.Counter ;
import io.micrometer.core.instrument.Meter ;
import io.micrometer.core.instrument.MeterRegistry ;
import io.micrometer.core.instrument.Metrics ;
import io.micrometer.core.instrument.Timer ;
import io.micrometer.core.instrument.binder.logging.Log4j2Metrics ;
import io.micrometer.core.instrument.distribution.HistogramSnapshot ;
import io.micrometer.core.instrument.search.MeterNotFoundException ;
import io.micrometer.core.instrument.search.RequiredSearch ;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry ;
import io.micrometer.prometheus.PrometheusMeterRegistry ;

@SpringBootTest ( //
		classes = Csap_Api_Tests.Simple_CSAP.class , //
		webEnvironment = WebEnvironment.RANDOM_PORT )
@ActiveProfiles ( {
		"test", "no-security"
} )
@DirtiesContext
@TestInstance ( TestInstance.Lifecycle.PER_CLASS )
public class Csap_Api_Tests {

	final static private Logger logger = LoggerFactory.getLogger( Csap_Api_Tests.class ) ;

	static {

		CsapApplication.initialize( "Test Setup Complete" ) ;

	}

	@Autowired
	private ApplicationContext applicationContext ;

	@Autowired
	private AlertsController alertsController ;
	@Autowired
	private MeterReport meterReport ;

	static final int MIN_SLEEP = 10 ;

	@CsapBootApplication ( scanBasePackages = {
			"org.none"
	} )
	@EnableAsync
	public static class Simple_CSAP implements WebMvcConfigurer {

		@RestController
		static public class SimpleHello {

			@GetMapping ( value = {
					"/csapHiNoSecurity", "/demo2", "/demo3"
			} )
			public String hi ( ) {

				try {

					Random rg = new Random( ) ;
					TimeUnit.MILLISECONDS.sleep( MIN_SLEEP + rg.nextInt( 50 ) ) ;

					// TimeUnit.MILLISECONDS.sleep( 1500 );
					// logger.info( "sleeping" );
				} catch ( InterruptedException e ) {

					// TODO Auto-generated catch block
					e.printStackTrace( ) ;

				}

				return "Hello" +
						LocalDateTime.now( )
								.format( DateTimeFormatter
										.ofPattern( "HH:mm:ss,   MMMM d  uuuu " ) ) ;

			}

			@Inject
			ObjectMapper jsonMapper ;

		}

	}

	@LocalServerPort
	private int testPort ;

	@Test
	public void load_context ( ) {

		logger.info( CsapApplication.TC_HEAD ) ;

		logger.info( "beans loaded: {}", applicationContext.getBeanDefinitionCount( ) ) ;

		assertThat( applicationContext.getBeanDefinitionCount( ) )
				.as( "Spring Bean count" )
				.isGreaterThan( 200 ) ;

		assertThat( applicationContext.containsBean( SecurityAutoConfiguration.class.getName( ) ) )
				.as( "securityAutoConfiguration is disabled" )
				.isFalse( ) ;

		assertThat( applicationContext.containsBean( SchedulingConfiguration.class.getName( ) ) )
				.as( "SchedulingConfiguration is enabled and used extensively in csap app" )
				.isTrue( ) ;

		verify_csap_components_loaded( applicationContext ) ;

		// Assert.assertFalse( true);

	}

	public static void verify_csap_components_loaded ( ApplicationContext contextLoaded ) {

		assertThat( contextLoaded.getBean( CsapInformation.class ) )
				.as( "CSAP element present if enabled: CsapInformation" )
				.isNotNull( ) ;

		assertThat( contextLoaded.getBean( DocumentController.class ) )
				.as( "CSAP element  present if enabled: DocumentController" )
				.isNotNull( ) ;

		assertThat( contextLoaded.getBean( AlertsController.class ) )
				.as( "CSAP element present if enabled: AlertsController" )
				.isNotNull( ) ;

		assertThat( contextLoaded.getBean( CsapEncryptionConfiguration.class ) )
				.as( "CSAP element present if enabled" )
				.isNotNull( ) ;

		// verify_csap_web_server( contextLoaded );
	}

	@Inject
	RestTemplateBuilder restTemplateBuilder ;

	@Test
	public void verify_http_request_security_disabled ( ) {

		logger.info( CsapApplication.TC_HEAD + " running no sercurity test" ) ;
		ResponseEntity<String> response = hit_test_url( ) ;

		logger.info( "result:\n {}", response ) ;

		assertThat( response.getBody( ) )
				.startsWith( "Hello" ) ;

	}

	private ResponseEntity<String> hit_test_url ( String... urls ) {

		String testUrl = "/csapHiNoSecurity" ;

		if ( urls.length != 0 ) {

			testUrl = urls[0] ;

		}

		String simpleUrl = "http://localhost:" + testPort + testUrl ;

		logger.info( "hitting url: {}", simpleUrl ) ;
		// mock does much validation.....

		TestRestTemplate restTemplate = new TestRestTemplate( restTemplateBuilder ) ;

		ResponseEntity<String> response = restTemplate.getForEntity( simpleUrl, String.class ) ;

		try {

			// Adding in a delay to enable gc and others to run
			TimeUnit.MILLISECONDS.sleep( 3 ) ;

		} catch ( InterruptedException e ) {

			logger.info( "{}", CSAP.buildCsapStack( e ) ) ;

		}

		return response ;

	}

	// @Disabled
	// @Test
	// public void verify_metric_simon () {
	//
	// logger.info( CsapApplication.TC_HEAD + "hitting url: {}", "alerts" ) ;
	//
	// ObjectNode metrics = alertsController.metrics( "junitTest", null ) ;
	//
	// logger.debug( "metrics: {}", CSAP.jsonPrint( metrics ) ) ;
	//
	// String TEST_ID = "http.csapHiNoSecurity.GET" ;
	// // TEST_ID = "jvm.gc.memory.promoted" ;
	//
	// logger.info( "metrics: {}", showAlertData( metrics ) ) ;
	// findAlertData( metrics, TEST_ID ) ;
	//
	// JsonNode collected_data = findAlertData( metrics, TEST_ID ) ;
	//
	// double beforeCount = 0 ; // single versus class run
	// if ( !collected_data.isMissingNode() ) {
	// beforeCount = collected_data.at( "/0" ).asDouble() ;
	// }
	//
	// // assertThat( collected_data.isMissingNode() ).isTrue() ;
	//
	// hit_test_url() ;
	//
	// metrics = alertsController.metrics( "junitTest", null ) ;
	// logger.info( "metrics: {}", showAlertData( metrics ) ) ;
	// collected_data = findAlertData( metrics, TEST_ID ) ;
	//
	// assertThat( collected_data.isMissingNode() ).isFalse() ;
	// assertThat( collected_data.at( "/0" ).asDouble() - beforeCount ).isEqualTo(
	// 1.0 ) ;
	//
	// logger.info( "TEST_ID: {}, data: {}", TEST_ID, CSAP.jsonPrint( collected_data
	// ) ) ;
	//
	// }

	@Test
	public void verify_micrometers_escaped_slashes ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		hit_test_url( ) ;
		hit_test_url( ) ;
		hit_test_url( "/demo2" ) ;

		ObjectNode filteredEscapedMetrics = meterReport.build( "http.server.*", null, 3, false, false, true, false,
				false, 0, null ) ;

		logger.info( "allMetrics: {}", CSAP.jsonPrint( filteredEscapedMetrics ) ) ;

		assertThat(
				filteredEscapedMetrics
						.at( "/http.server.requests[_csapHiNoSecurity]/count" )
						.asInt( 0 ) )
								.isGreaterThanOrEqualTo( 2 ) ;

	}

	@Autowired
	MeterRegistry springMeterRegistry ;

	@Autowired
	SimpleMeterRegistry simpleMeterRegistry ;

	@Autowired ( required = false )
	PrometheusMeterRegistry prometheusMeterRegistry ;

	// https://github.com/micrometer-metrics/micrometer/issues/681
	@Test
	public void microMeterTimer ( )
		throws Exception {

		// SimpleMeterRegistry reg = null ;

		// Metrics.addRegistry(reg);

		Metrics.globalRegistry.getRegistries( ).forEach( registry -> {

			logger.info( " registry: {}", registry ) ;

		} ) ;

		assertThat( Metrics.globalRegistry.getRegistries( ).size( ) )
				.as( "promethesius injected" )
				.isGreaterThanOrEqualTo( 3 ) ; // full tests: 25 - maybe should be disabled?

		Timer testTimer = springMeterRegistry.timer( "junit-timer" ) ;

		var testSleepDurationMs = 55 ;
		testTimer.record( ( ) -> {

			try {

				TimeUnit.MILLISECONDS.sleep( testSleepDurationMs ) ;

			} catch ( InterruptedException ignored ) {

			}

		} ) ;

		var missingSearch = simpleMeterRegistry.find( "missing-timer" ).timer( ) ;
		assertThat( missingSearch ).isNull( ) ;

		var junitTimer = simpleMeterRegistry.find( "junit-timer" ).timer( ) ;
		assertThat( junitTimer ).isNotNull( ) ;

		HistogramSnapshot snap = junitTimer.takeSnapshot( ) ;

		logger.info( "simpleMeterRegistry searchTimer: {} ms,   {} seconds, snap: {} ms  baseTimeUnit: {}",
				junitTimer.mean( TimeUnit.MILLISECONDS ),
				junitTimer.mean( TimeUnit.SECONDS ),
				snap.mean( TimeUnit.MILLISECONDS ),
				junitTimer.baseTimeUnit( ) ) ;

		assertThat( junitTimer.mean( TimeUnit.MILLISECONDS ) ).isGreaterThanOrEqualTo( testSleepDurationMs - 5 ) ;

		Collection<Timer> timers = springMeterRegistry.find( "junit-timer" ).timers( ) ;

		timers.stream( ).forEach( springsearchTimer -> {

			HistogramSnapshot springSnap = springsearchTimer.takeSnapshot( ) ;

			logger.info( "springMeterRegistry springsearchTimer: {} ms,   {} seconds, snap: {} ms baseTimeUnit: {}",
					springsearchTimer.mean( TimeUnit.MILLISECONDS ),
					springsearchTimer.mean( TimeUnit.SECONDS ),
					springSnap.mean( TimeUnit.MILLISECONDS ),
					springsearchTimer.baseTimeUnit( ) ) ;

		} ) ;

		assertThat( prometheusMeterRegistry != null ).as( "promethesius injected" ).isTrue( ) ;
		Timer promTimer = prometheusMeterRegistry.find( "junit-timer" ).timer( ) ;

		HistogramSnapshot promSnap = junitTimer.takeSnapshot( ) ;

		logger.info( "prometheusMeterRegistry searchTimer: {} ms,   {} seconds, snap: {} ms  baseTimeUnit: {}",
				promTimer.mean( TimeUnit.MILLISECONDS ),
				promTimer.mean( TimeUnit.SECONDS ),
				promSnap.mean( TimeUnit.MILLISECONDS ),
				promTimer.baseTimeUnit( ) ) ;

	}

	@Test
	public void verify_micrometers_http_filter ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		ObjectNode tcStart = meterReport.buildSimple( null, List.of( "/csapHiNoSecurity" ), 3, false ) ;

		hit_test_url( ) ;
		hit_test_url( ) ;
		hit_test_url( "/demo2" ) ;

		ObjectNode allMetrics = meterReport.buildSimple( null, null, 3, false ) ;
		logger.info( "allMetrics: {}", CSAP.jsonPrint( allMetrics ) ) ;

		assertThat( allMetrics.path( "http.server.requests[/csapHiNoSecurity]" ).isMissingNode( ) )
				.isFalse( ) ;

		ObjectNode filteredTagMetrics = meterReport.buildSimple( null, List.of( "/csapHiNoSecurity" ), 3, false ) ;
		logger.info( "metrics: {}", CSAP.jsonPrint( filteredTagMetrics ) ) ;

		long numMetrics = CSAP.asStreamHandleNulls( filteredTagMetrics ).count( ) ;
		assertThat( numMetrics ).isEqualTo( 3 ) ;

		assertThat(
				filteredTagMetrics.path( "http.server.requests[/csapHiNoSecurity]" ).path( "count" ).asInt( )
						- tcStart.path( "http.server.requests[/csapHiNoSecurity]" ).path( "count" ).asInt( ) )
								.isEqualTo( 2 ) ;

		assertThat(
				filteredTagMetrics.path( "http.server.requests[/csapHiNoSecurity]" ).path( "total-ms" ).asInt( ) )
						.isGreaterThan( MIN_SLEEP ) ;

		assertThat(
				filteredTagMetrics.path( "http.server.requests[/csapHiNoSecurity]" ).path( "bucket-0.5-ms" ).asInt( ) )
						.isGreaterThan( MIN_SLEEP ) ;

		assertThat(
				filteredTagMetrics.path( "http.server.requests[/csapHiNoSecurity]" ).path( "bucket-max-ms" ).asInt( ) )
						.isGreaterThan( MIN_SLEEP ) ;

		ObjectNode filteredSummaryMetrics = meterReport.buildSimple( null, null, 3, true ) ;
		logger.info( "filteredSummaryMetrics: {}", CSAP.jsonPrint( filteredSummaryMetrics ) ) ;

		numMetrics = CSAP.asStreamHandleNulls( filteredSummaryMetrics ).count( ) ;
		assertThat( numMetrics ).isGreaterThan( 40 ) ;

		ObjectNode httpDetailMetrics = meterReport.buildSimple( "http.server.requests", null, 3, false ) ;
		logger.info( "httpDetailMetrics: {}", CSAP.jsonPrint( httpDetailMetrics ) ) ;

		ObjectNode httpSummaryMetrics = meterReport.buildSimple( "http.server.requests", null, 3, true ) ;
		logger.info( "httpSummaryMetrics: {}", CSAP.jsonPrint( httpSummaryMetrics ) ) ;

		numMetrics = CSAP.asStreamHandleNulls( httpSummaryMetrics ).count( ) ;
		assertThat( numMetrics ).isGreaterThan( 1 ) ;

	}

	@Test
	public void verify_micrometers_jvm_gc_filter ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		hit_test_url( ) ;
		hit_test_url( ) ;
		hit_test_url( "/demo2" ) ;
		hit_test_url( "/demo2" ) ;
		hit_test_url( "/demo2" ) ;

		System.gc( ) ;
		System.gc( ) ;
		System.gc( ) ;
		TimeUnit.SECONDS.sleep( 3 ) ;
		CSAP.setLogToDebug( MeterReport.class.getName( ) ) ;
		var doAggregation = true ;
		ObjectNode csapJvmAggregateReport = meterReport.buildSimple( ".*.pause.*", null, 3, doAggregation ) ;
		CSAP.setLogToInfo( CsapMicroMeter.class.getName( ) ) ;

		logger.info( "csap metrics: {}", CSAP.jsonPrint( csapJvmAggregateReport ) ) ;

//		assertThat( csapJvmAggregateReport.path( "csap.jvm.gc.pause.minor" ).isMissingNode() ).isFalse() ;
//		assertThat( csapJvmAggregateReport.at( "/csap.jvm.gc.pause.minor/count" ).asInt() )
//			.isGreaterThanOrEqualTo( 1 ) ;

	}

	@Test
	public void verify_jvm_csap_collection_tag ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		Metrics.globalRegistry.getRegistries( ).forEach( registry -> {

			// logger.info( " registry: {}", registry ) ;
			Collection<Meter> counters = registry.find( "jvm.threads.daemon" ).meters( ) ;

			counters.stream( ).forEach( counter -> {

				logger.info( "registry {} count {}",
						registry.getClass( ).getSimpleName( ),
						counter.getId( ) ) ;

			} ) ;

		} ) ;

		ObjectNode daemonMetrics = meterReport.buildSimple( "jvm.threads.daemon", null, 3, false ) ;
		logger.info( "daemonMetrics: {}", CSAP.jsonPrint( daemonMetrics ) ) ;

		assertThat( daemonMetrics.toString( ) ).contains( "csap-collection" ) ;

	}

	@Test
	public void verify_micrometers_jvm_filter ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		hit_test_url( ) ;
		hit_test_url( ) ;
		hit_test_url( "/demo2" ) ;

		ObjectNode jvmMetrics = meterReport.buildSimple( "jvm.*", null, 3, false ) ;
		logger.info( "jvmMetrics: {}", CSAP.jsonPrint( jvmMetrics ) ) ;

		assertThat( jvmMetrics.path( "jvm.threads.states[csap-collection=true,state=terminated]" ).isMissingNode( ) )
				.isFalse( ) ;

		jvmMetrics = meterReport.buildSimple( "jvm.*", List.of( CsapMicroRegistryConfiguration.CSAP_COLLECTION_TAG ), 3,
				false ) ;
		logger.info( "allMetrics: {}", CSAP.jsonPrint( jvmMetrics ) ) ;

		assertThat( jvmMetrics.path( "jvm.threads.states[state=terminated]" ).isMissingNode( ) )
				.isFalse( ) ;

		ObjectNode jvmSummaryMetrics = meterReport.buildSimple( "jvm.*", List.of(
				CsapMicroRegistryConfiguration.CSAP_COLLECTION_TAG ),
				3, true ) ;
		logger.info( "jvmSummaryMetrics: {}", CSAP.jsonPrint( jvmSummaryMetrics ) ) ;

		logger.info( "jvm.memory.used: {}", CSAP.jsonPrint( jvmSummaryMetrics.path(
				"csap.heap.jvm.memory.used.mb" ) ) ) ;
		assertThat( jvmSummaryMetrics.path( "csap.heap.jvm.memory.used.mb" ).asInt( ) )
				.isGreaterThan( 40 )
				.isLessThan( 600 ) ;

	}

	@Test
	public void verify_micrometers_summary_filter ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		ObjectNode tcDetailStart = meterReport.buildSimple( "http.server.requests", null, 3, false ) ;
		ObjectNode tcSummaryStart = meterReport.buildSimple( "http.server.requests", null, 3, true ) ;

		hit_test_url( ) ;
		hit_test_url( ) ;
		hit_test_url( "/demo2" ) ;

		ObjectNode httpDetailReport = meterReport.buildSimple( "http.server.requests", null, 3, false ) ;
		logger.info( "httpDetailReport: {}", CSAP.jsonPrint( httpDetailReport ) ) ;

		assertThat(
				httpDetailReport.path( "http.server.requests[/csapHiNoSecurity]" ).path( "count" ).asInt( )
						- tcDetailStart.path( "http.server.requests[/csapHiNoSecurity]" ).path( "count" )
								.asInt( ) )
										.isEqualTo( 2 ) ;

		ObjectNode httpSummaryReport = meterReport.buildSimple( "http.server.requests", null, 3, true ) ;
		logger.info( "httpSummaryReport: {}", CSAP.jsonPrint( httpSummaryReport ) ) ;

		assertThat( httpSummaryReport.path( "http.server.requests" ).path( "count" ).asInt( )
				- tcSummaryStart.path( "http.server.requests" ).path( "count" ).asInt( ) )
						.as( "summarized http requests" )
						.isEqualTo( 3 ) ;

	}

	@Disabled
	@Test
	public void verify_metric_micrometers_log4j_and_clear ( )
		throws Exception {

		String logEvents = "log4j2.events" ;

		logger.info( CsapApplication.testHeader( ) ) ;

		RequiredSearch searchResults = simpleMeterRegistry.get( logEvents ).tag( "level", "info" ) ;
		Counter log4jCounter = searchResults.counter( ) ;

		logger.info( "Original: log4jCounter {} id: {}", log4jCounter.count( ), log4jCounter.getId( ) ) ;
		System.out.println( "Original next: log4jCounter: " + log4jCounter.count( ) ) ;

		meterReport.deleteMeterForTests( logEvents ) ;

		Exception junitE = null ;

		try {

			log4jCounter = simpleMeterRegistry.get( logEvents ).tag( "level", "info" ).counter( ) ;

		} catch ( Exception e ) {

			junitE = e ;

		}

		assertThat( junitE ).isInstanceOf( MeterNotFoundException.class ) ;
		// assertThat( log4jCounter.count() ).isEqualTo( 0 ) ;

		logger.info( CsapApplication.header( "Reregistering log4j events" ) ) ;

		Log4j2Metrics springLogMetrics = new Log4j2Metrics(
				Collections.emptyList( ),
				(LoggerContext) LogManager.getContext( false ) ) ;
		springLogMetrics.bindTo( simpleMeterRegistry ) ;

		logger.info( "Post clear: info log" ) ;
		logger.warn( "Post clear: warn log" ) ;

		assertThat( simpleMeterRegistry.get( logEvents ).tag( "level", "info" ).counter( ).count( ) )
				.isGreaterThan( 0 ) ;
		assertThat( simpleMeterRegistry.get( logEvents ).tag( "level", "warn" ).counter( ).count( ) )
				.isGreaterThan( 0 ) ;

		ObjectNode csapLog4jSummaryReport = meterReport.buildSimple( logEvents, null, 3, true ) ;
		logger.info( "csapLog4jReport {}", CSAP.jsonPrint( csapLog4jSummaryReport ) ) ;
		assertThat( csapLog4jSummaryReport
				.path( logEvents ).asInt( ) )
						.as( "all logs all categories" )
						// .isEqualTo( 2 ) ; jenkinsIssue
						.isGreaterThanOrEqualTo( 2 ) ;

	}

	@Test
	public void verify_micrometers_csap_tag_listing ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		// logger.info( "Waiting for bindings" ) ;
		// TimeUnit.SECONDS.sleep( 5 ) ;

		hit_test_url( ) ;
		hit_test_url( ) ;
		hit_test_url( "/demo2" ) ;

		ObjectNode allMetrics = meterReport.buildSimple( null, null, 3, false ) ;
		// logger.info( "allMetrics: {}", CSAP.jsonPrint( allMetrics ) ) ;

		long numMetrics = CSAP.asStreamHandleNulls( allMetrics ).count( ) ;
		assertThat( numMetrics ).isGreaterThan( 46 ) ;

		ObjectNode csapSummaryMetrics = meterReport.buildSimple(
				null, List.of( CsapMicroRegistryConfiguration.CSAP_COLLECTION_TAG ), 3, true ) ;
		logger.info( "csapMetrics: {}", CSAP.jsonPrint( csapSummaryMetrics ) ) ;

		numMetrics = CSAP.asStreamHandleNulls( csapSummaryMetrics ).count( ) ;
		logger.info( "numMetrics: {}", numMetrics ) ;
		assertThat( numMetrics ).isGreaterThan( 25 ) ;
		assertThat( csapSummaryMetrics.path( "jvm.memory.max" ).asInt( ) ).isNotEqualTo( -1 ) ;

		assertThat( csapSummaryMetrics.path( "process.cpu.usage" ).isMissingNode( ) ).isFalse( ) ;
		assertThat( csapSummaryMetrics.path( "system.cpu.usage" ).isMissingNode( ) ).isFalse( ) ;

	}

	@Test
	public void verify_micrometers_health ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		ObjectNode summaryReport = meterReport.buildSimple( null, null, 3, true ) ;

		JsonNode healthReport = summaryReport.path( CsapMicroMeter.HealthReport.Report.name.json ) ;
		assertThat( healthReport.isObject( ) ).isTrue( ) ;

		logger.info( "{}: {}", CsapMicroMeter.HealthReport.Report.name.json, CSAP.jsonPrint( healthReport ) ) ;

		assertThat( healthReport.path( CsapMicroMeter.HealthReport.Report.healthy.json ).asBoolean( ) ).isTrue( ) ;

	}

	@Test
	public void verify_micrometers_full_listing ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		ObjectNode tcStart = meterReport.buildSimple( null, null, 3, true ) ;

		String TEST_URL_ID = "http.server.requests[/csapHiNoSecurity]" ;
		hit_test_url( ) ;
		hit_test_url( ) ;

		TimeUnit.SECONDS.sleep( 1 ) ;

		ObjectNode initialMetrics = meterReport.buildSimple( null, null, 3, false ) ;
		logger.info( "metrics: {}", CSAP.jsonPrint( initialMetrics ) ) ;
		logger.info( "metrics: {}", CSAP.jsonPrint( initialMetrics.path( TEST_URL_ID ) ) ) ;

		assertThat( initialMetrics.path( TEST_URL_ID ).isMissingNode( ) ).isFalse( ) ;

		TimeUnit.SECONDS.sleep( 1 ) ;
		hit_test_url( ) ;
		hit_test_url( ) ;

		hit_test_url( "/demo2" ) ;
		hit_test_url( "/demo3" ) ;

		hit_test_url( "/demo2" ) ;
		hit_test_url( "/demo3" ) ;
		hit_test_url( ) ;
		hit_test_url( ) ;
		TimeUnit.SECONDS.sleep( 3 ) ;

		ObjectNode allMetrics = meterReport.buildSimple( null, null, 3, false ) ;
		// logger.info( "metrics: {}", CSAP.jsonPrint( allMetrics ) ) ;
		logger.info( "metrics: {}", CSAP.jsonPrint( allMetrics.path( TEST_URL_ID ) ) ) ;
		assertThat( allMetrics.path( TEST_URL_ID ).isMissingNode( ) ).isFalse( ) ;

		ObjectNode summaryMetrics = meterReport.buildSimple( null, null, 3, true ) ;
		logger.info( "summaryMetrics: {}", CSAP.jsonPrint( summaryMetrics ) ) ;
		assertThat( summaryMetrics.path( TEST_URL_ID ).isMissingNode( ) ).isTrue( ) ;

		logger.info( "tomcat.global.request: {}", CSAP.jsonPrint( summaryMetrics.path( "tomcat.global.request" ) ) ) ;
		assertThat( summaryMetrics.path( "tomcat.global.request" ).isMissingNode( ) ).isFalse( ) ;
		assertThat( summaryMetrics.at( "/tomcat.global.request/count" ).asInt( )
				- tcStart.at( "/tomcat.global.request/count" ).asInt( ) ).isEqualTo( 10 ) ;

		assertThat( summaryMetrics.path( "system.cpu.count[csap-collection=true]" ).isMissingNode( ) ).isFalse( ) ;

		// assertThat( collected_data.isMissingNode() ).isFalse() ;
		// assertThat( collected_data.at( "/0" ).asDouble() ).isEqualTo( 1.0 ) ;

		// logger.info( "TEST_ID: {}, data: {}", TEST_ID, CSAP.jsonPrint( collected_data
		// ) ) ;

	}

	// @Test
	// public void verify_metric_simon_collection ()
	// throws Exception {
	//
	// logger.info( CsapApplication.TC_HEAD + "hitting url: {}", "alerts" ) ;
	//
	// String TEST_ID = "/http.csapHiNoSecurity.GET" ;
	// hit_test_url() ;
	// hit_test_url() ;
	//
	// ObjectNode metrics = alertsController.metricsSimon( "junit", null, null ) ;
	// // logger.info( "metrics: {}", CSAP.jsonPrint( metrics ) ) ;
	// logger.info( "metrics: {}", CSAP.jsonPrint( metrics.at( TEST_ID ) ) ) ;
	//
	// hit_test_url() ;
	// hit_test_url() ;
	// hit_test_url() ;
	// hit_test_url() ;
	//
	// metrics = alertsController.metricsSimon( "junit", null, null ) ;
	// // logger.info( "metrics: {}", CSAP.jsonPrint( metrics ) ) ;
	// logger.info( "metrics: {}", CSAP.jsonPrint( metrics.at( TEST_ID ) ) ) ;
	//
	// // assertThat( collected_data.isMissingNode() ).isFalse() ;
	// // assertThat( collected_data.at( "/0" ).asDouble() ).isEqualTo( 1.0 ) ;
	//
	// // logger.info( "TEST_ID: {}, data: {}", TEST_ID, CSAP.jsonPrint(
	// collected_data ) ) ;
	//
	// }

	private String showAlertData ( ObjectNode metrics ) {

		return CSAP.jsonStream( metrics.path( "rows" ) )
				// return CSAP.jsonStream( metrics )
				.map( JsonNode::toString )
				.collect( Collectors.joining( "\n" ) ) ;

	}

	private JsonNode findAlertData ( ObjectNode metrics , String TEST_ID ) {

		return CSAP.jsonStream( metrics.path( "rows" ) )
				.filter( row -> row.path( "name" ).asText( ).equals( TEST_ID ) )
				.findFirst( )
				.map( row -> row.path( "data" ) )
				.orElse( MissingNode.getInstance( ) ) ;

	}

}
