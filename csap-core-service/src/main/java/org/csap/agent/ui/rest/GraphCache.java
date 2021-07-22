/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.csap.agent.ui.rest ;

import javax.inject.Inject ;

import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.beans.factory.annotation.Qualifier ;
import org.springframework.stereotype.Component ;
import org.springframework.web.client.RestTemplate ;

/**
 *
 * @author someDeveloper
 */
@Component
public class GraphCache {

	final static Logger logger = LoggerFactory.getLogger( GraphCache.class ) ;

	@Inject
	@Qualifier ( "analyticsRest" )
	private RestTemplate analyticsTemplate ;

	public String getGraphData ( String restUrl ) {

		String restResponse = "{ error: \"No response\"}" ;

		try {

			logger.debug( "getting report from: {} ", restUrl ) ;
			restResponse = analyticsTemplate.getForObject( restUrl, String.class ) ;

		} catch ( Exception e ) {

			logger.error( "Failed getting report from url: {}, Reason: ", restUrl, e.getMessage( ) ) ;
			logger.debug( "Stack Trace ", e ) ;

			// resultsJson.put( "url", restUrl );
			// resultsJson.put( "message", "Error during Access: " +
			// e.getMessage() );
		}

		return restResponse ;

	}

}
