package org.csap.agent ;

import static org.assertj.core.api.Assertions.assertThat ;

import org.csap.helpers.CsapApplication ;
import org.junit.jupiter.api.Test ;
import org.junit.jupiter.api.TestInstance ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;

@TestInstance ( TestInstance.Lifecycle.PER_CLASS )
public class HelloWorld {

	static {

		CsapApplication.initialize( "Test Setup Complete" ) ;

	}

	Logger logger = LoggerFactory.getLogger( getClass( ) ) ;

	@Test
	public void verify_test_dependencies ( ) {

		logger.info( CsapApplication.testHeader( ) ) ;

		assertThat( true ).as( "simple test" ).isTrue( ) ;

	}

}
