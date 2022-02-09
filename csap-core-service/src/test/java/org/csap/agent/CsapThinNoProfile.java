package org.csap.agent ;

import static org.assertj.core.api.Assertions.assertThat ;
import static org.assertj.core.api.Assertions.assertThatCode ;

import javax.inject.Inject ;

import org.csap.agent.model.Application ;
import org.csap.agent.model.EnvironmentSettings ;
import org.csap.agent.services.OsCommands ;
import org.csap.helpers.CSAP ;
import org.csap.helpers.CsapApplication ;
import org.jasypt.encryption.pbe.StandardPBEStringEncryptor ;
import org.junit.jupiter.api.AfterAll ;
import org.junit.jupiter.api.BeforeAll ;
import org.junit.jupiter.api.TestInstance ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.boot.SpringBootConfiguration ;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration ;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration ;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration ;
import org.springframework.boot.context.properties.ConfigurationProperties ;
import org.springframework.boot.test.context.SpringBootTest ;
import org.springframework.boot.test.context.TestConfiguration ;
import org.springframework.test.annotation.DirtiesContext ;

@SpringBootTest ( classes = CsapThinNoProfile.BareBoot.class )
@TestConfiguration
@DirtiesContext
@ConfigurationProperties ( prefix = "test.variables" )

@TestInstance ( TestInstance.Lifecycle.PER_CLASS )
public abstract class CsapThinNoProfile extends CsapBareTest {

	static {

		// includes logging initialization - so must occur very early
		CsapApplication.initialize( CSAP.padNoLine( "Loading" ) + CsapThinNoProfile.class.getName( ) ) ;

	}

	public Logger logger = LoggerFactory.getLogger( getClass( ) ) ;

	@Inject
	OsCommands osCommands ;

	StandardPBEStringEncryptor encryptor ;

	@SpringBootConfiguration
	@ImportAutoConfiguration ( classes = {
			PropertyPlaceholderAutoConfiguration.class,
			ConfigurationPropertiesAutoConfiguration.class,
			OsCommands.class
	} )
	public static class BareBoot {
	}

	@BeforeAll
	void thinBeforeAll ( )
		throws Exception {

		super.thinBeforeAll( ) ;
		logger.info( CsapApplication.testHeader( "loading OsCommands" ) ) ;

		getOsManager( ).setOsCommands( osCommands ) ;
		getApplication( ).setSkipScanAndActivate( false ) ;
		getApplication( ).initialize( ) ;
		getApplication( ).setAutoReload( false ) ;

		encryptor = new StandardPBEStringEncryptor( ) ;
		encryptor.setPassword( "junittest" ) ;

		assertThat( getJsonMapper( ).createObjectNode( ).isObject( ) ).isTrue( ) ;

	}

	@AfterAll
	void afterAll ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( "Cleaning up thin tests" ) ) ;

		getApplication( ).metricManager( ).shutdown( ) ;

	}

	public void loadDefaultCsapApplicationDefinition ( ) {

		assertThatCode( ( ) -> {

			assertThat( getApplication( ).loadDefinitionForJunits( false,
					Agent_context_loaded.SIMPLE_TEST_DEFINITION ) )
							.as( "No Errors or warnings" )
							.isTrue( ) ;

		} ).doesNotThrowAnyException( ) ;

	}

	// public Application getApplication () {
	// return application ;
	// }

	public EnvironmentSettings getRootProjectSettings ( ) {

		return getApplication( ).rootProjectEnvSettings( ) ;

	}

	public EnvironmentSettings getActiveProjectSettings ( ) {

		return getApplication( ).environmentSettings( ) ;

	}

	public void setApplication ( Application application ) {

		this.application = application ;

	}

	public StandardPBEStringEncryptor getEncryptor ( ) {

		return encryptor ;

	}

	public void setEncryptor ( StandardPBEStringEncryptor encryptor ) {

		this.encryptor = encryptor ;

	}

	// public ObjectMapper getJsonMapper () {
	// return jsonMapper ;
	// }

}
