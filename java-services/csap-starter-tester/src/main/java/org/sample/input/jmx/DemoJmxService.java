package org.sample.input.jmx ;

import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.jmx.export.annotation.ManagedMetric ;
import org.springframework.jmx.export.annotation.ManagedOperation ;
import org.springframework.jmx.export.annotation.ManagedResource ;
import org.springframework.stereotype.Service ;

/**
 * Trivial example show mbean definition. Spring config is shown below, refer to
 * links for references.
 * 
 * 
 */

@Service
@ManagedResource ( objectName = "org.csap:application=sample,name=DemoJmxService" , description = "Use to show client code samples" )
public class DemoJmxService {

	final private Logger logger = LoggerFactory.getLogger( getClass( ) ) ;

	@ManagedOperation ( description = "translates input to upper case, for demo " )
	public String toUpperCase ( String config ) {

		logger.info( "Received Request, translating to uppercase" ) ;

		return " UpperCase" + config.toUpperCase( ) ;

	}

	@ManagedMetric ( description = "Gets current mills" )
	public long getCurrentMillis ( ) {

		return System.currentTimeMillis( ) ;

	}
}
