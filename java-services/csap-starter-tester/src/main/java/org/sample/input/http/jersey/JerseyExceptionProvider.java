package org.sample.input.http.jersey ;

import javax.ws.rs.core.Response ;
import javax.ws.rs.ext.ExceptionMapper ;
import javax.ws.rs.ext.Provider ;

import org.apache.commons.logging.Log ;
import org.apache.commons.logging.LogFactory ;

/**
 * 
 * Jersey helper class that catches any exceptions thrown from any method, logs
 * it in logs, and generates a error page.
 * 
 * @author pnightin
 *
 */
@Provider
public class JerseyExceptionProvider implements
		ExceptionMapper<Exception> {

	protected final Log logger = LogFactory.getLog( getClass( ) ) ;

	@Override
	public Response toResponse ( Exception ex ) {

		logger.error( "Got an exception during Jersey Processing" + ex.getMessage( ) ) ;

		if ( logger.isDebugEnabled( ) )
			logger.error( "Stack ", ex ) ;

		return Response.status( 404 ).entity( ex.getMessage( ) ).type( "text/plain" )
				.build( ) ;

	}
}
