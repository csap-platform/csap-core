package org.csap.agent.integration.container ;

import static org.assertj.core.api.Assertions.assertThat ;

import org.csap.agent.CsapBareTest ;
import org.csap.agent.container.kubernetes.CrioIntegraton ;
import org.csap.helpers.CSAP ;
import org.csap.helpers.CsapApplication ;
import org.junit.jupiter.api.BeforeAll ;
import org.junit.jupiter.api.Test ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;

public class CrioTests extends CsapBareTest {

	Logger logger = LoggerFactory.getLogger( getClass( ) ) ;

	CrioIntegraton crio ;

	@BeforeAll
	void beforeAll ( ) {

		crio = new CrioIntegraton( getJsonMapper( ), getApplication( ) ) ;
		getApplication( ).initialize( ) ;

	}

	@Test
	public void verify_containers ( ) {

		logger.info( CsapApplication.testHeader( ) ) ;

		var containers = crio.containers( ) ;

		logger.info( CSAP.buildDescription(
				"crio.containers ",
				"containers", CSAP.jsonPrint( containers ) ) ) ;

		assertThat( containers.size( ) ).isGreaterThan( 5 ) ;

	}

	@Test
	public void verify_containerInspect ( ) {

		logger.info( CsapApplication.testHeader( ) ) ;

		var containerInspectReport = crio.containerInspect( "dummy_id" ) ;

		logger.info( CSAP.buildDescription(
				"crio.containers ",
				"containerInspectReport", CSAP.jsonPrint( containerInspectReport ) ) ) ;

	}

	@Test
	public void verify_processes ( ) {

		logger.info( CsapApplication.testHeader( ) ) ;

		var containerProcesses = crio.buildContainerProcesses( ) ;

		logger.info( CSAP.buildDescription(
				"containerProcesses ",
				"containerProcesses", containerProcesses ) ) ;

		assertThat( containerProcesses.size( ) ).isGreaterThan( 5 ) ;
		assertThat( containerProcesses.get( 0 ).getPid( ) ).isNotEmpty( ) ;

	}

	@Test
	public void verify_container_listing ( ) {

		logger.info( CsapApplication.testHeader( ) ) ;
		var containerListing = crio.listFiles( crio.containerNames( ).get( 0 ), "/some-path" ) ;

		logger.info( CSAP.buildDescription(
				"containerProcesses ",
				"containerProcesses", containerListing ) ) ;

//		assertThat( containerListing.size( ) ).isGreaterThan( 5 ) ;

	}

}
