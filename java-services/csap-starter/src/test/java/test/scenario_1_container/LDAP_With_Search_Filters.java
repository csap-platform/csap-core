package test.scenario_1_container ;

import static org.assertj.core.api.Assertions.assertThat ;

import java.util.Arrays ;

import javax.inject.Inject ;

import org.csap.CsapBootApplication ;
import org.csap.helpers.CSAP ;
import org.csap.helpers.CsapApplication ;
import org.csap.security.CsapUser ;
import org.csap.security.config.CsapSecuritySettings ;
import org.junit.jupiter.api.Assumptions ;
import org.junit.jupiter.api.BeforeAll ;
import org.junit.jupiter.api.BeforeEach ;
import org.junit.jupiter.api.Test ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.beans.factory.annotation.Autowired ;
import org.springframework.boot.test.context.SpringBootTest ;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment ;
import org.springframework.context.ApplicationContext ;
import org.springframework.ldap.core.LdapTemplate ;
import org.springframework.ldap.core.support.LdapContextSource ;
import org.springframework.test.annotation.DirtiesContext ;
import org.springframework.test.context.ActiveProfiles ;

@SpringBootTest ( //
		classes = LDAP_With_Search_Filters.Bare_Application.class , //
		webEnvironment = WebEnvironment.RANDOM_PORT )

@ActiveProfiles ( {
		"test", Csap_Application_With_LDAP.PROFILE_NAME
} )
@DirtiesContext
public class LDAP_With_Search_Filters {
	final static private Logger logger = LoggerFactory.getLogger( LDAP_With_Search_Filters.class ) ;

	@BeforeEach
	public void beforeMethod ( ) {

		// Guard for setup

		Assumptions.assumeTrue( ldapSettings.isConfigured( ) ) ;

	}

	// @SpringBootConfiguration
	// @ImportAutoConfiguration ( classes = {
	// PropertyPlaceholderAutoConfiguration.class,
	// ConfigurationPropertiesAutoConfiguration.class } )
	@CsapBootApplication
	public static class Bare_Application {
	}

	@Autowired ( required = false )
	public Settings_ldap ldapSettings = new Settings_ldap( ) ;

	@BeforeAll
	static void setUpBeforeClass ( )
		throws Exception {

		CsapApplication.initialize( logger.getName( ) ) ;

	}

	@Autowired
	private ApplicationContext applicationContext ;

	@Test
	public void spring_context_loaded ( ) {

		logger.info( CsapApplication.testHeader( "beans loaded: {} " ), applicationContext.getBeanDefinitionCount( ) ) ;

		logger.debug( "beans loaded: {}\n\t {}",
				applicationContext.getBeanDefinitionCount( ),
				Arrays.asList( applicationContext.getBeanDefinitionNames( ) ) ) ;

		assertThat( applicationContext.getBeanDefinitionCount( ) )
				.as( "Spring Bean count" )
				.isGreaterThan( 9 ) ;

		logger.info( "ldap settings: {}, \n\t CsapSecuritySettings: {}", ldapSettings, settings ) ;

	}

	@Inject
	CsapSecuritySettings settings ;

	@Test
	public void verify_real_user_dn ( ) {

		logger.info( CsapApplication.testHeader( ) ) ;

		CsapUser csapUser = null ;

		try {

			csapUser = (CsapUser) buildLdapTemplate( ).lookup(
					settings.getRealUserDn( ldapSettings.getUser( ) ), CsapUser.PRIMARY_ATTRIBUTES,
					new CsapUser( ) ) ;

		} catch ( Exception e ) {

			logger.error( "Failed lookup {}",
					CSAP.buildCsapStack( e ) ) ;

		}

		logger.info( "csapUser: {}", csapUser ) ;

		assertThat( csapUser.getMail( ) )
				.as( "userid lookup mail" )
				.isEqualTo( ldapSettings.getEmail( ) ) ;

	}

	private LdapTemplate buildLdapTemplate ( )
		throws Exception {

		LdapTemplate ldapSpringTemplate = new LdapTemplate( ) ;
		LdapContextSource contextSource = new LdapContextSource( ) ;

		// contextSource.setUserDn( ldapSettings.getUser() );
		// contextSource.setPassword( ldapSettings.getPass() );
		contextSource.setUrl( ldapSettings.getUrls( ) ) ;

		// Critical: Spring beans must be initialized, and JavaConfig requires
		// explicity calls
		contextSource.afterPropertiesSet( ) ;
		ldapSpringTemplate.setContextSource( contextSource ) ;
		ldapSpringTemplate.afterPropertiesSet( ) ;
		return ldapSpringTemplate ;

	}

}
