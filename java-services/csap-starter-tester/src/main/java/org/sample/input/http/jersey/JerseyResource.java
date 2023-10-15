package org.sample.input.http.jersey ;

import javax.inject.Inject ;
import javax.ws.rs.Consumes ;
import javax.ws.rs.GET ;
import javax.ws.rs.POST ;
import javax.ws.rs.Path ;
import javax.ws.rs.PathParam ;
import javax.ws.rs.Produces ;
import javax.ws.rs.core.MediaType ;

import org.sample.jpa.DemoManager ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.stereotype.Component ;

/**
 * Simple SPring wired Jersey Class
 * 
 * @author pnightin
 * 
 * @see <a href=
 *      "http://static.springsource.org/spring/docs/3.1.0.M1/spring-framework-reference/html/remoting.html#rest-resttemplate">
 *      Spring REST Template </a>
 */

@Component
@Path ( "/simpleSpringRest" )
public class JerseyResource {

	final static Logger logger = LoggerFactory.getLogger( JerseyResource.class ) ;

	@Inject
	private DemoManager testDao ;

	// The Java method will process HTTP GET requests
	@GET
	// The Java method will produce content identified by the MIME Media
	// type "text/plain"
	@Produces ( "text/plain" )
	public String getJpaData ( ) {

		// Return some cliched textual content
		return testDao.findUsingJpql( DemoManager.TEST_TOKEN, 20 ) ;

	}

	@POST
	@Path ( "/{hostName}" )
	@Consumes ( MediaType.APPLICATION_JSON )
	@Produces ( "text/plain" )
	public String samplePostDoc (
									String doc ,
									@PathParam ( "hostName" ) String hostName ) {

		logger.info( "Received  doc of length: {} ", doc.length( ) ) ;
		String results = "Received: " + hostName + " Document: " + doc ;

		if ( doc.length( ) > 100 ) {

			results = "Received: " + hostName + " Document: " + doc.substring( doc.length( ) - 100 ) ;

		} else {

			logger.info( "Received  " + doc ) ;

		}

		return results ;

	}

	@GET
	@Path ( "/peter/{hostName}" )
	@Produces ( "text/plain" )
	public String addMetricsRecordByIdAndJson ( @PathParam ( "hostName" ) String hostName ) {

		logger.info( "Received  " ) ;

		String results = "Received: " + hostName ;

		return results ;

	}

	@GET
	@Path ( "/testJerseyException" )
	@Produces ( "text/plain" )
	public String testJerseyException ( )
		throws Exception {

		logger.info( "Received  " ) ;

		String results = "Received: " ;

		throw new Exception( "Text from exception" ) ;

		// return results;
	}

}
