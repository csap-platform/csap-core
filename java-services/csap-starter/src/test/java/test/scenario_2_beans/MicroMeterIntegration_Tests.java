package test.scenario_2_beans ;

import static org.assertj.core.api.Assertions.assertThat ;

import java.util.concurrent.TimeUnit ;

import org.csap.helpers.CsapApplication ;
import org.csap.integations.micrometer.CsapMicroMeter ;
import org.csap.integations.micrometer.MeterReport ;
import org.junit.jupiter.api.BeforeAll ;
import org.junit.jupiter.api.Test ;
import org.junit.jupiter.api.TestInstance ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;

import io.micrometer.core.instrument.Counter ;
import io.micrometer.core.instrument.Timer ;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry ;

@TestInstance ( TestInstance.Lifecycle.PER_CLASS )
public class MicroMeterIntegration_Tests {

	Logger logger = LoggerFactory.getLogger( getClass( ) ) ;

	SimpleMeterRegistry registry ;

	@BeforeAll
	void beforeAll ( )
		throws Exception {

		CsapApplication.initialize( "Micrometer Integration Tests" ) ;

		registry = new SimpleMeterRegistry( ) ;

	}

	@Test
	void registryPresent ( ) {

		CsapApplication.testHeader( ) ;

		assertThat( registry.getMeters( ).size( ) )
				.as( "Spring Bean count" )
				.isGreaterThanOrEqualTo( 0 ) ;

	}

	@Test
	void simpleTimer ( )
		throws Exception {

		CsapApplication.testHeader( ) ;

		var millis = 10 ;

		Timer.Sample sample = Timer.start( registry ) ;

		TimeUnit.MILLISECONDS.sleep( millis ) ;

		var timer = Timer.builder( "test.simple" ).register( registry ) ;

		long timeTaken = sample.stop( timer ) ;

		logger.info( "timeTaken: {}, count: {}, mean: {}", timeTaken, timer.count( ), timer.mean(
				TimeUnit.MILLISECONDS ) ) ;

		assertThat( timer.mean( TimeUnit.MILLISECONDS ) )
				.as( "timer duration" )
				.isGreaterThanOrEqualTo( millis - 1 ) ; // allow for variable sleep

		MeterReport report = new MeterReport( registry ) ;

		var meterReport = report.buildMeterReport( timer, 2, true ) ;
		logger.info( "meterReport: {}", meterReport ) ;

	}

	@Test
	void simpleCounter ( )
		throws Exception {

		CsapApplication.testHeader( ) ;

		var millis = 10 ;

		Counter counter = Counter.builder( "test.counter.simple" ).register( registry ) ;

		counter.increment( ) ;

		logger.info( "count: {}", counter.count( ) ) ;

		assertThat( counter.count( ) )
				.as( "counter" )
				.isEqualTo( 1 ) ;

	}

}
