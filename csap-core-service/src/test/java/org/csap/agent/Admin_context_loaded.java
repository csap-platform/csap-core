package org.csap.agent ;

import static org.assertj.core.api.Assertions.assertThat ;

import org.csap.agent.model.Application ;
import org.csap.agent.ui.windows.CorePortals ;
import org.csap.helpers.CSAP ;
import org.csap.helpers.CsapApplication ;
import org.junit.jupiter.api.BeforeAll ;
import org.junit.jupiter.api.DisplayName ;
import org.junit.jupiter.api.Tag ;
import org.junit.jupiter.api.Test ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.beans.factory.annotation.Autowired ;
import org.springframework.context.ApplicationContext ;

@Tag ( "core" )
@Tag ( "full" )
@CsapBareTest.Admin_Full
public class Admin_context_loaded {

	Logger logger = LoggerFactory.getLogger( getClass( ) ) ;

	static {

		CsapApplication.initialize( CSAP.padNoLine( "Loading" ) + Admin_context_loaded.class.getName( ) ) ;

	}

	@Autowired
	private ApplicationContext applicationContext ;

	@Autowired
	Application csapApplication ;

	@BeforeAll
	void beforeAll ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		csapApplication.setAutoReload( false ) ;

		assertThat( csapApplication.loadDefinitionForJunits( false, Agent_context_loaded.SIMPLE_TEST_DEFINITION ) )
				.as( "No Errors or warnings" )
				.isTrue( ) ;

	}

	@Test
	@DisplayName ( "spring context loaded" )
	void verify_spring_context_loaded ( ) {

		assertThat( csapApplication.isAdminProfile( ) ).isTrue( ) ;

		assertThat( applicationContext.getBeanDefinitionCount( ) )
				.as( "Spring Bean count" )
				.isGreaterThan( 100 ) ;

		assertThat( applicationContext.getBean( CorePortals.class ) )
				.as( "SpringRequests controller loaded" )
				.isNotNull( ) ;

		assertThat( csapApplication.getName( ) )
				.as( "csapApplication.getActiveModelName" )
				.isEqualTo( "DEFAULT APPLICATION FOR JUNITS" ) ;

	}

}
