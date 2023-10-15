package org.sample.locator ;

import org.apache.commons.logging.Log ;
import org.apache.commons.logging.LogFactory ;
import org.springframework.http.HttpStatus ;
import org.springframework.web.bind.annotation.ExceptionHandler ;
import org.springframework.web.bind.annotation.ResponseStatus ;

import com.fasterxml.jackson.core.JsonParseException ;

//@ControllerAdvice("org.csap")
public class MvcExeptionAdvisor {

	protected final Log logger = LogFactory.getLog( getClass( ) ) ;

	/**
	 * Default handler. Note the Springs error handling does not extend into - they
	 * will fall through to Servlet container. eg. OutOfMemoryError - will not
	 * invoke any of the handlers below.
	 * 
	 * So - you still MUST define a error page in web.xml
	 * 
	 * @param e
	 */
	@ResponseStatus ( value = HttpStatus.INTERNAL_SERVER_ERROR , reason = "Exception during processing, examine server Logs" )
	@ExceptionHandler ( Exception.class )
	public void defaultHandler ( Exception e ) {

		logger.warn( "Controller  exception: ", e ) ;

	}

	@ResponseStatus ( value = HttpStatus.INTERNAL_SERVER_ERROR , reason = "Exception during processing, examine server Logs" )
	@ExceptionHandler ( NullPointerException.class )
	public void handleNullPointer ( Exception e ) {

		logger.warn( "Controller null pointer: ", e ) ;

	}

	@ResponseStatus ( value = HttpStatus.INTERNAL_SERVER_ERROR , reason = "Exception during processing, examine server Logs" )
	@ExceptionHandler ( JsonParseException.class )
	public void handleJsonParsing ( Exception e ) {

		logger.warn( "Controller json parsing: ", e ) ;

	}

}
