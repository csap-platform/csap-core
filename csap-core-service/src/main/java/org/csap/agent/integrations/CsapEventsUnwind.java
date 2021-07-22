package org.csap.agent.integrations ;

import java.util.Arrays ;
import java.util.Iterator ;
import java.util.List ;

import org.apache.logging.log4j.Level ;
import org.apache.logging.log4j.LogManager ;
import org.apache.logging.log4j.Logger ;
import org.apache.logging.log4j.spi.AbstractLogger ;
import org.apache.logging.log4j.spi.ExtendedLoggerWrapper ;
import org.csap.agent.CsapCoreService ;
import org.csap.integations.CsapMicroMeter ;
import org.slf4j.LoggerFactory ;

/**
 * Very simple class for unwinding CsapEvent client calls to source lines
 */
public final class CsapEventsUnwind extends ExtendedLoggerWrapper {

	static org.slf4j.Logger slfLogger = LoggerFactory.getLogger( CsapEventsUnwind.class ) ;

	private static final long serialVersionUID = 1612276765616375L ;
	private final ExtendedLoggerWrapper logger ;

	private static final String FQCN = CsapEvents.class.getName( ) ;

	private CsapEventsUnwind ( final Logger logger ) {

		super( (AbstractLogger) logger, logger.getName( ), logger.getMessageFactory( ) ) ;
		this.logger = this ;

	}

	@Override
	public void info ( final String message , final Object... params ) {

		logIfEnabled( FQCN, Level.INFO, null, message, params ) ;

	}

	/**
	 * Returns a custom Logger with the name of the calling class.
	 * 
	 * @return The custom Logger for the calling class.
	 */
	public static CsapEventsUnwind create ( ) {

		Class wrappedClass = CsapEventsUnwind.class ;

		List<StackTraceElement> traceElements = Arrays.asList( new Exception( ).getStackTrace( ) ) ;

		Iterator<StackTraceElement> traceIt = traceElements.iterator( ) ;

		while ( traceIt.hasNext( ) ) {

			StackTraceElement element = traceIt.next( ) ;
			String stackDesc = element.toString( ) ;

			if ( stackDesc.contains( "org.csap" )
					&& ! stackDesc.contains( CsapEvents.class.getSimpleName( ) )
					&& ! stackDesc.contains( CsapMicroMeter.class.getSimpleName( ) )
					&& ! stackDesc.contains( CsapCoreService.class.getSimpleName( ) ) ) {

				try {

					wrappedClass = Class.forName( element.getClassName( ) ) ;
					break ;

				} catch ( Exception e ) {

					slfLogger.warn( "Failed to find {}", stackDesc ) ;

				}

			}

		}

		// slfLogger.info( "wrapped: {}", wrappedClass.getName() ) ;
		final Logger wrapped = LogManager.getLogger( wrappedClass ) ;
		return new CsapEventsUnwind( wrapped ) ;

	}

}
