package org.sample.input.http.ui.windows ;

import java.io.IOException ;

import javax.servlet.http.HttpServletRequest ;
import javax.servlet.http.HttpServletResponse ;

import org.apache.commons.lang3.exception.ExceptionUtils ;
import org.csap.alerts.CsapGlobalId ;
import org.csap.helpers.CSAP ;
import org.csap.integations.micrometer.CsapMeterUtilities ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.beans.factory.annotation.Autowired ;
import org.springframework.http.HttpStatus ;
import org.springframework.web.bind.annotation.ControllerAdvice ;
import org.springframework.web.bind.annotation.ExceptionHandler ;
import org.springframework.web.bind.annotation.ResponseStatus ;

import com.fasterxml.jackson.core.JsonParseException ;

@ControllerAdvice
public class ErrorHandling {

	final Logger logger = LoggerFactory.getLogger( getClass( ) ) ;

	@Autowired
	CsapMeterUtilities microMeterHelper ;

	/**
	 * Default handler. Note the Springs error handling does not extend into they
	 * will fall through to Servlet container. eg. OutOfMemoryError - will not
	 * invoke any of the handlers below.
	 * 
	 * So - you still MUST define a error page in web.xml
	 * 
	 * @param e
	 */
	@ResponseStatus ( value = HttpStatus.INTERNAL_SERVER_ERROR , reason = "Exception during processing, examine server Logs" )
	@ExceptionHandler ( Exception.class )
	public void defaultHandler ( HttpServletRequest request , Exception e ) {

		logger.warn( "{}: {}", request.getRequestURI( ), CSAP.buildFilteredStack( e, "org.sample" ) ) ;
		logger.debug( "Full exception", e ) ;
		microMeterHelper.incrementCounter( CsapGlobalId.EXCEPTION.id ) ;

	}

	@ResponseStatus ( value = HttpStatus.INTERNAL_SERVER_ERROR , reason = "Exception during processing, examine server Logs" )
	@ExceptionHandler ( NullPointerException.class )
	public void handleNullPointer ( HttpServletRequest request , Exception e ) {

		logger.warn( "{}: {}", request.getRequestURI( ), CSAP.buildFilteredStack( e, "org.sample" ) ) ;
		logger.debug( "Full exception", e ) ;
		microMeterHelper.incrementCounter( CsapGlobalId.EXCEPTION.id ) ;

	}

	@ResponseStatus ( value = HttpStatus.INTERNAL_SERVER_ERROR , reason = "Exception during processing, examine server Logs" )
	@ExceptionHandler ( JsonParseException.class )
	public void handleJsonParsing ( HttpServletRequest request , Exception e ) {

		logger.warn( "{}: {}", request.getRequestURI( ), CSAP.buildFilteredStack( e, "org.sample" ) ) ;
		logger.debug( "Full exception", e ) ;

	}

	// ClientAbort which extends ioexception cannot have response written cannot
	// have a response written
	@ExceptionHandler ( IOException.class )
	public void handleIOException ( HttpServletRequest request , Exception e , HttpServletResponse response ) {

		String stackFrames = ExceptionUtils.getStackTrace( e ) ;

		if ( stackFrames.contains( "ClientAbortException" ) ) {

			logger.info( "ClientAbortException found: " + e.getMessage( ) ) ;

		} else {

			logger.warn( "{}: {}", request.getRequestURI( ), CSAP.buildFilteredStack( e, "org.sample" ) ) ;

			microMeterHelper.incrementCounter( CsapGlobalId.EXCEPTION.id ) ;

			try {

				response.setStatus( HttpStatus.INTERNAL_SERVER_ERROR.value( ) ) ;
				response.getWriter( )
						.print( HttpStatus.INTERNAL_SERVER_ERROR.value( )
								+ " : Exception during processing, examine server Logs" ) ;

			} catch ( IOException e1 ) {

				// TODO Auto-generated catch block
				e1.printStackTrace( ) ;

			}

		}

	}

}
