package org.csap.integations.micrometer ;

import java.util.List ;
import java.util.concurrent.TimeUnit ;
import java.util.function.ToDoubleFunction ;
import java.util.regex.Pattern ;
import java.util.stream.Collectors ;

import javax.annotation.PostConstruct ;

import org.apache.commons.lang3.StringUtils ;
import org.aspectj.lang.ProceedingJoinPoint ;
import org.csap.helpers.CSAP ;
import org.csap.helpers.CsapApplication ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.beans.factory.annotation.Autowired ;

import io.micrometer.core.instrument.Counter ;
import io.micrometer.core.instrument.Gauge ;
import io.micrometer.core.instrument.Meter ;
import io.micrometer.core.instrument.Tag ;
import io.micrometer.core.instrument.Timer ;
import io.micrometer.core.instrument.config.MeterFilter ;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry ;
import io.micrometer.core.lang.Nullable ;

public class CsapMeterUtilities {

	final Logger logger = LoggerFactory.getLogger( getClass( ) ) ;

	static final double BYTES_IN_MB = 1024 * 1024 ;

	static CsapMeterUtilities _self ; // non spring support -- favor @Inject

	@PostConstruct
	public void supportForNonSpring ( ) {

		logger.debug( CsapApplication.testHeader( ) ) ;

		_self = this ;

	}

	SimpleMeterRegistry simpleMeterRegistry ;

	@Autowired
	MeterReport meterReport ;

	public MeterReport getMeterReport ( ) {

		return meterReport ;

	}

	public void setMeterReport ( MeterReport meterReport ) {

		this.meterReport = meterReport ;

	}

	public CsapMeterUtilities ( SimpleMeterRegistry simpleMeterRegistry ) {

		this.simpleMeterRegistry = simpleMeterRegistry ;

		logger.debug( CsapApplication.testHeader( ) ) ;

	}

	public <T> Gauge addGauge ( String name , @Nullable T obj , ToDoubleFunction<T> toDoubleFunction ) {

		Gauge gauge = Gauge
				.builder( name, obj, toDoubleFunction )
				.register( simpleMeterRegistry ) ;

		return gauge ;

	}

	public Meter find ( String name ) {

		// return getMeterRegistry().find( name ).timer() ;
		return getSimpleMeterRegistry( ).find( name ).meter( ) ;

	}

	public Timer.Sample record ( String name , Runnable runnable ) {

		simpleMeterRegistry.timer( name ).record( runnable ) ;
		return Timer.start( simpleMeterRegistry ) ;

	}

	public Timer.Sample record ( String name , long amount , TimeUnit unit ) {

		simpleMeterRegistry.timer( name ).record( amount, unit ) ;
		return Timer.start( simpleMeterRegistry ) ;

	}

	public Timer.Sample startTimer ( ) {

		return Timer.start( simpleMeterRegistry ) ;

	}

	public long stopTimer (
							Timer.Sample sample ,
							String name ) {

		return sample.stop(
				Timer
						.builder( name )
						.register( simpleMeterRegistry ) ) ;

	}

	public void incrementCounter ( String name ) {

		Counter.builder( name ).register( simpleMeterRegistry ).increment( ) ;

	}

	public Object timedExecution ( ProceedingJoinPoint pjp , String desc )
		throws Throwable {

		var className = pjp.getTarget( ).getClass( ).getSimpleName( ) ;

		if ( StringUtils.isEmpty( className ) ) {

			// anonymous class support
			try {

				className = pjp.getTarget( ).getClass( ).getName( ) ;

				if ( className.contains( "." ) ) {

					className = className.substring( className.lastIndexOf( "." ) + 1 ).replaceAll( Pattern.quote(
							"$" ), "-anon-" ) ;
					;

				}

				logger.debug( "className: {}", className ) ;

			} catch ( Exception e ) {

				logger.info( "Failed building  anon name: {}", CSAP.buildCsapStack( e ) ) ;

			}

		}

		var metricName = desc
				+ CSAP.camelToSnake( className )
				+ "." + CSAP.camelToSnake( pjp.getSignature( ).getName( ) ) ;

		// Timer timer = Metrics.globalRegistry.timer( timerId, new ArrayList<>() ) ;
		Timer.Sample sample = Timer.start( simpleMeterRegistry ) ;

		try {

			return pjp.proceed( ) ;

		} catch ( Exception ex ) {

			// exceptionClass = ex.getClass().getSimpleName();
			throw ex ;

		} finally {

			try {

				sample.stop( Timer.builder( metricName )
						.description( "executeMicroMeter timed" )
						.register( simpleMeterRegistry ) ) ;

			} catch ( Exception e ) {

				// ignoring on purpose
			}

		}

	}

	public MeterFilter addCsapCollectionTag ( String meterNamePattern ) {

		var filter = new MeterFilter( ) {
			@Override
			public Meter.Id map ( Meter.Id id ) {

				if ( id.getName( ).matches( meterNamePattern ) ) {

					return id.withTag( Tag.of( CsapMicroRegistryConfiguration.CSAP_COLLECTION_TAG, "true" ) ) ;

				}

				return id ;

			}
		} ;

		simpleMeterRegistry.config( ).meterFilter( filter ) ;

		return filter ;

	}

	static public String buildMicroMeterId ( Meter meter , boolean isHideCsapTag , boolean encode ) {

		StringBuilder id = new StringBuilder( meter.getId( ).getName( ) ) ;

		if ( meter.getId( ).getName( ).matches( MeterReport.CONVERT_TO_MB ) ) {

			id.append( ".mb" ) ;

		}

		List<Tag> tags = meter.getId( ).getTags( ) ;
		String tagInfo = "" ;

		if ( ! tags.isEmpty( ) ) {

			tagInfo = tags.stream( )
					.filter( tag -> {

						// exception=None,method=GET,outcome=SUCCESS,status=200,uri=/**/*.css
						if ( tag.getKey( ).equals( "exception" ) && tag.getValue( ).equals( "None" ) )
							return false ;
						if ( isHideCsapTag && tag.getKey( ).equals(
								CsapMicroRegistryConfiguration.CSAP_COLLECTION_TAG ) )
							return false ;
						if ( tag.getKey( ).equals( "method" ) && tag.getValue( ).equals( "GET" ) )
							return false ;
						if ( tag.getKey( ).equals( "outcome" ) && tag.getValue( ).equals( "SUCCESS" ) )
							return false ;
						if ( tag.getKey( ).equals( "outcome" ) && tag.getValue( ).equals( "REDIRECTION" ) )
							return false ;
						if ( tag.getKey( ).equals( "status" ) && tag.getValue( ).equals( "200" ) )
							return false ;
						return true ;

					} )
					.map( tag -> {

						if ( tag.getKey( ).equals( "uri" ) || tag.getKey( ).equals( "name" ) )
							return tag.getValue( ) ;
						return tag.getKey( ) + "=" + tag.getValue( ) ;

					} )
					.collect( Collectors.joining( "," ) ) ;

		}

		if ( tagInfo.length( ) > 0 ) {

			id.append( "[" ).append( tagInfo ).append( "]" ) ;// + tagInfo + "]" ;

		}

		String result = id.toString( ) ;

		if ( encode ) {

			result = result.replaceAll( "/", "_" ) ;

		}

		return result ;

	}

	public SimpleMeterRegistry getSimpleMeterRegistry ( ) {

		return simpleMeterRegistry ;

	}

	public void setSimpleMeterRegistry ( SimpleMeterRegistry simpleMeterRegistry ) {

		this.simpleMeterRegistry = simpleMeterRegistry ;

	}

	public static CsapMeterUtilities supportForNonSpringConsumers ( ) {

		return _self ;

	}

	public void set_self_for_junit ( CsapMeterUtilities _self ) {

		CsapMeterUtilities._self = _self ;

	}

}
