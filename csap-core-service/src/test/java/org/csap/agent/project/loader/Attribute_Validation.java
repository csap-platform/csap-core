package org.csap.agent.project.loader ;

import static org.assertj.core.api.Assertions.assertThat ;

import org.csap.agent.container.C7 ;
import org.csap.agent.model.ProcessRuntime ;
import org.csap.helpers.CsapApplication ;
import org.junit.jupiter.api.Test ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;

public class Attribute_Validation {

	final static private Logger logger = LoggerFactory.getLogger( Attribute_Validation.class ) ;

	@Test
	public void validate_java_server ( ) {

		logger.info( CsapApplication.TC_HEAD ) ;

		boolean isJavaServer = ProcessRuntime.isJavaServer( "SpringBoot" ) ;

		logger.info( "isJavaServer: {}", isJavaServer ) ;

		assertThat( isJavaServer )
				.as( "cpu loops" )
				.isTrue( ) ;

		isJavaServer = ProcessRuntime.isJavaServer( "springboot" ) ;

		logger.info( "isJavaServer: {}", isJavaServer ) ;

		assertThat( isJavaServer )
				.as( "cpu loops" )
				.isFalse( ) ;

		isJavaServer = ProcessRuntime.isJavaServer( C7.definitionSettings.val( ) ) ;

		logger.info( "isJavaServer: {}", isJavaServer ) ;

		assertThat( isJavaServer )
				.as( "cpu loops" )
				.isFalse( ) ;

	}

}
