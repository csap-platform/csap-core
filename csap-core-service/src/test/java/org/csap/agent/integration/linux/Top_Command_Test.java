package org.csap.agent.integration.linux ;

import static org.assertj.core.api.Assertions.assertThat ;

import java.util.Arrays ;

import org.csap.agent.linux.TopRunnable ;
import org.csap.agent.model.Application ;
import org.csap.helpers.CsapApplication ;
import org.junit.jupiter.api.BeforeAll ;
import org.junit.jupiter.api.Test ;
import org.junit.jupiter.api.TestInstance ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;

@TestInstance ( TestInstance.Lifecycle.PER_CLASS )
public class Top_Command_Test {

	private Logger logger = LoggerFactory.getLogger( getClass( ) ) ;

	static {

		CsapApplication.initialize( "Test Setup Complete" ) ;

	}

	@BeforeAll
	public void setUpBeforeAll ( ) {

		logger.info( CsapApplication.testHeader( ) ) ;

		Application.setDeveloperMode( true ) ;

	}

	@Test
	public void verify_top_collection ( ) {

		logger.info( CsapApplication.testHeader( ) ) ;

		Application.testBuilder( ) ;

		String[] topCommands = {
				"bash", "-c", "top"
		} ;
		TopRunnable topRunnable = new TopRunnable( 1, Arrays.asList( topCommands ) ) ;

		int attempts = 0 ;
		var cpu = 0.0f ;

		while ( attempts++ < 5 ) {

			String[] pids = {
					TopRunnable.VM_TOTAL
			} ;
			cpu = topRunnable.getCpuForPid( Arrays.asList( pids ) ) ;
			logger.info( "pid list: '{}' cpu: '{}'", pids, cpu ) ;

			if ( cpu != 0.0f )
				break ;

			try {

				// Only do work if needed
				logger.info( "Waiting for data to be processed" ) ;
				Thread.sleep( 1000 ) ;

			} catch ( InterruptedException e1 ) {

				// TODO Auto-generated catch block
				e1.printStackTrace( ) ;

			}

		}

		assertThat( attempts ).isLessThan( 4 ) ;

		assertThat( cpu ).isEqualTo( 31.4f ) ;

	}

	@Test
	public void simpleString ( ) {

		String message = "3.0.26 Maven Deploy by someDeveloper using Cssp3Reference on csap-dev01" ;
		logger.info( "Contains space: " + message.contains( " " ) + "Index of space: " + message.indexOf( " " ) ) ;

	}

}
