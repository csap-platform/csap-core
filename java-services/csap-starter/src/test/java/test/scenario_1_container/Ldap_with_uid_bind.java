package test.scenario_1_container ;

import static org.assertj.core.api.Assertions.assertThat ;

import java.io.IOException ;

import javax.annotation.PostConstruct ;
import javax.naming.ldap.LdapName ;

import org.apache.commons.lang3.StringUtils ;
import org.csap.helpers.CSAP ;
import org.csap.helpers.CsapApplication ;
import org.csap.helpers.CsapRestTemplateFactory ;
import org.csap.security.CsapUser ;
import org.csap.security.CsapUserContextCallback ;
import org.junit.jupiter.api.BeforeAll ;
import org.junit.jupiter.api.Disabled ;
import org.junit.jupiter.api.Test ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.beans.factory.annotation.Value ;
import org.springframework.boot.test.context.TestConfiguration ;
import org.springframework.ldap.core.LdapTemplate ;
import org.springframework.ldap.core.support.LdapContextSource ;
import org.springframework.ldap.filter.AndFilter ;
import org.springframework.ldap.filter.EqualsFilter ;
import org.springframework.ldap.support.LdapNameBuilder ;
import org.springframework.test.context.TestPropertySource ;

import com.fasterxml.jackson.core.JsonProcessingException ;
import com.fasterxml.jackson.databind.ObjectMapper ;

/**
 * 
 * Simple tests to validate Spring LDAP Template.
 * 
 * Similar to sql - LDAP has a DSL for interacting with provider, which in turn
 * is abstracted somewhat by Java nameing apis. Spring Ldap makes this much more
 * developer friendly.
 * 
 * Prior to jumping to code, it is highly recommended to make use of a desktop
 * LDAP browser to browse LDAP tree to familiarize your self with syntax and
 * available attributes.
 * 
 * Softerra ldap browser is nice way to approach
 * 
 * 
 */

@TestPropertySource ( locations = "file:${user.home}/csap/csapSecurity.properties" )
// ignoring because we don't want jenkins to have a file in /home/csap/*
@Disabled
public class Ldap_with_uid_bind {
	final static private Logger logger = LoggerFactory.getLogger( Ldap_with_uid_bind.class ) ;

	// @Configuration
	// @ComponentScan(basePackageClasses = CsapSecurityConfiguration.class,
	// useDefaultFilters = false,
	// includeFilters = {
	// @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
	// value = CsapSecurityConfiguration.class)
	// } )
	// public static class SimpleConfig {
	//
	// }

	@Value ( "${security.dir.url:overWritten}" )
	private String ldapUrl = "example: ldap://your.ldap.com:389/" ;

	@Value ( "${security.dir.password:overWritten}" )
	private String ldapPass = "overWritten" ;

	@Value ( "${security.dir.user:overWritten}" )
	private String ldapUser = "overWritten" ;

	@Value ( "${security.dir.search.user:overWritten}" )
	private String ldapSearchUser = "overWritten" ;

	//
	@Value ( "${security.dir.tree:overWritten}" )
	private String genericTree = "overWritten" ;

	@Value ( "${security.test.user.id:overWritten}" )
	private String theTestUserId = "overWritten" ;

	@Value ( "${security.test.user.pass:overWritten}" )
	private String theTestPass = "overWritten" ;

	@TestConfiguration
	static class SimpletConfiguration {

	}

	@PostConstruct
	void printVals ( ) {
		// ldapUserFull = environment.getProperty( "security.dir.user" );
		// ldapPass = environment.getProperty( "security.dir.password" );
		// ldapUrl = environment.getProperty( "security.dir.url" );

		// currentUser = springEnvironment.getProperty( "user.name" );

		logger.info( CSAP.padLine( "ldapUser" ) + "'{}'"
				+ CSAP.padLine( "ldapPass" ) + "'{}'"
				+ CSAP.padLine( "ldapUrl" ) + "'{}'"
				+ CSAP.padLine( "ldapSearchUser" ) + "'{}'"
				+ CSAP.padLine( "theTestUserId" ) + "'{}'",
				ldapUser, ldapPass, ldapUrl, ldapSearchUser,
				theTestUserId ) ;

		assertThat( ldapUrl ).isNotNull( ) ;
		assertThat( ldapUser ).isNotNull( ) ;
		assertThat( ldapUser ).isNotEqualTo( "overWritten" ) ;
		assertThat( ldapPass ).isNotNull( ) ;

	}

	@BeforeAll
	public static void setUpBeforeClass ( )
		throws Exception {

		CsapApplication.initialize( logger.getName( ) ) ;

	}

	ObjectMapper jacksonMapper = new ObjectMapper( ) ;

	/**
	 * 
	 * Scenario: - retrieve user attributes using a generic search id/password
	 * 
	 * @throws IOException
	 * @throws JsonProcessingException
	 * 
	 * 
	 * @see <a href=
	 *      "http://docs.spring.io/spring-ldap/docs/1.3.2.RELEASE/reference/htmlsingle/#introduction-overview">
	 *      Spring LDAP lookup </a>
	 * 
	 */
	@Test
	public void validate_user_lookup ( )
		throws Exception {

		int maxAttempts = 1 ;
		int numFailures = 0 ;

		for ( int i = 0; i < maxAttempts; i++ ) {

			logger.info( "\n\n *****************   Attempt: " + i ) ;

			LdapTemplate template = buildContextTemplate( ) ;

			StringBuilder builder = new StringBuilder( ) ;
			builder.append( "\n\n ==========================" ) ;
			builder.append( "\n LdapTemplate:" ) ;

			logger.info( builder.toString( ) ) ;

			LdapName ldapName = LdapNameBuilder.newInstance( ldapSearchUser )
					.add( "uid", theTestUserId )
					.build( ) ;

			logger.info( "Looking up: {}",
					ldapName ) ;

			CsapUser csapUser = null ;

			try {

				csapUser = (CsapUser) template.lookup( ldapName, new CsapUser( ) ) ;
				logger.info( "CsapUser Raw Attributes: \n\t"
						+ jacksonMapper.writerWithDefaultPrettyPrinter( ).writeValueAsString(
								csapUser.getAttibutesJson( ) ) ) ;

				assertThat( csapUser.getMail( ) ).startsWith( theTestUserId + "@" ) ;

				csapUser = (CsapUser) template.lookup( ldapName, CsapUser.PRIMARY_ATTRIBUTES, new CsapUser( ) ) ;
				logger.info( "CsapUser.PRIMARY_ATTRIBUTES  filter: \n\t"
						+ jacksonMapper.writerWithDefaultPrettyPrinter( ).writeValueAsString(
								csapUser.getAttibutesJson( ) ) ) ;

				assertThat( csapUser.getMail( ) ).startsWith( theTestUserId + "@" ) ;

			} catch ( Exception e ) {

				logger.error( "Failed lookup {}",
						CsapRestTemplateFactory.getFilteredStackTrace( e, "csap" ) ) ;

				numFailures++ ;

			}

			assertThat( csapUser ).isNotNull( ) ;

		}

		logger.info( "maxAttempts :" + maxAttempts + " numFailures: " + numFailures ) ;

	}

	@Test
	public void validate_login_using_context ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		LdapTemplate ldapSpringTemplate = buildContextTemplate( ) ;

		try {

			boolean authenticated = ldapSpringTemplate.authenticate(
					genericTree,
					"(uid=" + ldapUser + ")",
					ldapPass ) ;

			assertThat( authenticated ).as( "Using configured tree" ).isTrue( ) ;

			authenticated = ldapSpringTemplate.authenticate(
					genericTree,
					"(uid=" + ldapUser + ")",
					"Wrong Password" ) ;

			assertThat( authenticated ).as( "Incorrect password attempt" ).isFalse( ) ;

			String rootTree = genericTree.split( "," )[1] ;
			authenticated = ldapSpringTemplate
					.authenticate(
							rootTree,
							"(uid=" + ldapUser + ")",
							ldapPass ) ;

			assertThat( authenticated ).as( "Using root tree" ).isTrue( ) ;

			assertThat(

					ldapSpringTemplate.authenticate(
							ldapSearchUser,
							"(uid=" + theTestUserId + ")",
							"Wrong Password" ) )

									.as( "Current User attempt" )

									.isFalse( ) ;

			logger.info( "All Scenarios passed" ) ;

		} catch ( Exception e ) {

			logger.error( "Failed to init LDAP", e ) ;

		}

	}

	@Test
	public void validate_login_using_bind ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		LdapTemplate ldapSpringTemplate = new LdapTemplate( ) ;
		LdapContextSource contextSource = new LdapContextSource( ) ;
		contextSource.setUrl( ldapUrl ) ;

		if ( StringUtils.isNotEmpty( ldapUser ) ) {

			contextSource.setUserDn( ldapUser ) ;
			contextSource.setPassword( ldapPass ) ;

		}

		// Critical: Spring beans must be initialized, and JavaConfig requires
		// explicity calls

		contextSource.afterPropertiesSet( ) ;
		ldapSpringTemplate.setContextSource( contextSource ) ;
		ldapSpringTemplate.afterPropertiesSet( ) ;

		try {

			AndFilter filter = new AndFilter( ) ;

			filter
					.and( new EqualsFilter( "uid", theTestUserId ) )
					.and( new EqualsFilter( "objectclass", "person" ) ) ;

			logger.info( "Filter: " + filter.toString( ) ) ;

			assertThat(

					ldapSpringTemplate.authenticate(
							ldapSearchUser,
							filter.toString( ),
							theTestPass ) )

									.as( "Current User bind attempt" )

									.isTrue( ) ;

			assertThat(

					ldapSpringTemplate.authenticate(
							ldapSearchUser,
							filter.toString( ),
							"Wrong Password" ) )

									.as( "Current User bind attempt" )

									.isFalse( ) ;

		} catch ( Exception e ) {

			logger.error( "Failed to init LDAP {}", CSAP.buildFilteredStack( e, "test.scenario" ) ) ;

			assertThat( false ).isTrue( ) ;

		}

	}

	// @Inject
	// CsapSecurityConfiguration csapSecurityConfiguration ;
	//
	// @Test
	// public void verify_lookup_using_getRealUserDn ()
	// throws Exception {
	//
	// LdapTemplate ldapSpringTemplate = buildContextTemplate();
	//
	// CsapSecurityConfiguration csapSecurityConfiguration = new
	// CsapSecurityConfiguration();
	//
	// CsapUser csapUser;
	// try {
	// csapUser = (CsapUser) ldapSpringTemplate.lookup(
	// csapSecurityConfiguration.getRealUserDn( theTestUserId ),
	// CsapUser.PRIMARY_ATTRIBUTES,
	// new CsapUser() );
	//
	// } catch (Exception e) {
	// }
	//
	// }
	/**
	 * 
	 * refer to notes at your directory searches
	 * 
	 * @throws Exception
	 */
	@Test
	public void validate_generic_login_using_context ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		LdapTemplate ldapSpringTemplate = buildContextTemplate( ) ;

		try {

			AndFilter filter = new AndFilter( ) ;
			filter.and( new EqualsFilter( "uid", ldapUser ) )
					.and( new EqualsFilter( "objectclass", "person" ) ) ;

			assertThat(

					ldapSpringTemplate.authenticate(
							genericTree,
							filter.toString( ),
							ldapPass ) )

									.as( "generic login" )

									.isTrue( ) ;

			logger.info( "validated authentication" ) ;

		} catch ( Exception e ) {

			logger.error( "Failed to init LDAP", e ) ;
			assertThat( false ).isTrue( ) ;

		}

	}

	private LdapTemplate buildContextTemplate ( )
		throws Exception {

		LdapTemplate ldapSpringTemplate = new LdapTemplate( ) ;
		LdapContextSource contextSource = new LdapContextSource( ) ;

		contextSource.setUserDn( ldapUser ) ;
		contextSource.setPassword( ldapPass ) ;
		contextSource.setUrl( ldapUrl ) ;

		// Critical: Spring beans must be initialized, and JavaConfig requires
		// explicity calls
		contextSource.afterPropertiesSet( ) ;
		ldapSpringTemplate.setContextSource( contextSource ) ;
		ldapSpringTemplate.afterPropertiesSet( ) ;
		return ldapSpringTemplate ;

	}

	@Test
	public void validate_generic_login_with_csap_user_context_created ( )
		throws Exception {

		LdapTemplate ldapTemplate = buildContextTemplate( ) ;

		CsapUserContextCallback csapUserContextCallback = new CsapUserContextCallback( ldapTemplate ) ;

		assertThat(
				ldapTemplate
						.authenticate(
								genericTree,
								new EqualsFilter( "uid", ldapUser ).toString( ),
								ldapPass,
								csapUserContextCallback ) )

										.as( "generic login with csap user context created" )

										.isTrue( ) ;

		CsapUser csapUser = csapUserContextCallback.getCsapUser( ) ;
		logger.info( "User from context: {}", csapUser.toString( ) ) ;

		assertThat( csapUser.getFullName( ) )
				.as( "name from context" )
				.isEqualTo( ldapUser ) ;

	}

	@Test
	public void validate_user_login_with_csap_user_context_created ( )
		throws Exception {

		LdapTemplate ldapTemplate = buildContextTemplate( ) ;

		CsapUserContextCallback csapUserContextCallback = new CsapUserContextCallback( ldapTemplate ) ;

		// Validate a wrong password
		boolean authenticated = ldapTemplate.authenticate(
				ldapSearchUser,
				new EqualsFilter( "uid", theTestUserId ).toString( ),
				"Wrong Password",
				csapUserContextCallback ) ;

		if ( authenticated ) {

			CsapUser csapUser = csapUserContextCallback.getCsapUser( ) ;
			logger.info( "User from context: " + csapUser.toString( ) ) ;

		}

		// update with actual password to validate
		assertThat( authenticated ).isFalse( ) ;

	}
}
