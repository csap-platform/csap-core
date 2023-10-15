package org.sample.input.http.jersey ;

import java.io.IOException ;

import org.apache.commons.logging.Log ;
import org.apache.commons.logging.LogFactory ;
import org.springframework.http.HttpInputMessage ;
import org.springframework.http.converter.StringHttpMessageConverter ;

/**
 * Simple converter example
 * 
 * @author pnightin
 * 
 * @see <a href=
 *      "http://static.springsource.org/spring/docs/3.1.0.M1/spring-framework-reference/html/remoting.html#rest-resttemplate">
 *      Spring REST Template </a>
 */
public class SimpleConverter extends StringHttpMessageConverter {
	protected final Log logger = LogFactory.getLog( getClass( ) ) ;

	@Override
	protected String readInternal ( Class clazz , HttpInputMessage inputMessage )
		throws IOException {

		// TODO Auto-generated method stub
		logger.info( "Very simple wrapper for spring Jersey message converter" ) ;
		return super.readInternal( clazz, inputMessage ) ;

	}

}
