package org.sample.input.http.jersey ;

import javax.ws.rs.ext.Provider ;

import org.apache.commons.logging.Log ;
import org.apache.commons.logging.LogFactory ;
import org.glassfish.jersey.server.monitoring.ApplicationEvent ;
import org.glassfish.jersey.server.monitoring.ApplicationEventListener ;
import org.glassfish.jersey.server.monitoring.RequestEvent ;
import org.glassfish.jersey.server.monitoring.RequestEventListener ;

@Provider
public class JerseyEventListener implements ApplicationEventListener {

	protected final Log logger = LogFactory.getLog( getClass( ) ) ;

	public void onEvent ( ApplicationEvent event ) {

		switch ( event.getType( ) ) {

		case INITIALIZATION_FINISHED:
			logger.info( "Application  was initialized." ) ;
			break ;
		case DESTROY_FINISHED:
			logger.info( "Application  destroyed." ) ;
			break ;
		default:
			logger.debug( "Application event:  " + event.getType( ) ) ;

		}

	}

	public RequestEventListener onRequest ( RequestEvent requestEvent ) {

		// requestCnt++;
		// System.out.println("Request " + requestCnt + " started.");
		// return the listener instance that will handle this request.
		return new RequestEventListener( ) {

			@Override
			public void onEvent ( RequestEvent event ) {

				if ( logger.isDebugEnabled( ) ) {

					logger.debug( "Event type: " + event.getType( ) + " Event path " + event.getUriInfo( )
							.getPath( ) ) ;

				}

				// logger.info("Event type: " + event.getType() + " Event path "
				// + event.getUriInfo().getPath());
				if ( event.getType( ) == RequestEvent.Type.ON_EXCEPTION ) {

					logger.error( "Exception found on path: " + event.getUriInfo( ).getPath( ) ) ;

				}

			}
		} ;

	}

}
