package org.csap.agent.integration.misc ;

import static org.assertj.core.api.Assertions.assertThat ;

import java.io.IOException ;

import javax.naming.directory.DirContext ;
import javax.naming.ldap.LdapName ;

import org.csap.agent.CsapBareTest ;
import org.csap.helpers.CSAP ;
import org.csap.helpers.CsapApplication ;
import org.csap.security.CsapUser ;
import org.csap.security.CsapUserContextCallback ;
import org.junit.jupiter.api.Assertions ;
import org.junit.jupiter.api.Assumptions ;
import org.junit.jupiter.api.BeforeAll ;
import org.junit.jupiter.api.BeforeEach ;
import org.junit.jupiter.api.Disabled ;
import org.junit.jupiter.api.Test ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.boot.SpringBootConfiguration ;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration ;
import org.springframework.boot.autoconfigure.SpringBootApplication ;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration ;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration ;
import org.springframework.boot.context.properties.ConfigurationProperties ;
import org.springframework.boot.test.context.SpringBootTest ;
import org.springframework.ldap.core.LdapTemplate ;
import org.springframework.ldap.core.support.LdapContextSource ;
import org.springframework.ldap.filter.AndFilter ;
import org.springframework.ldap.filter.EqualsFilter ;
import org.springframework.ldap.support.LdapNameBuilder ;

import com.fasterxml.jackson.core.JsonProcessingException ;
import com.fasterxml.jackson.databind.ObjectMapper ;

/**
 * 
 * 
 * Integration examples for use with LDAP directories supporting bind Softerra
 * ldap browser is nice way to approach
 * 
 * 
 */

@Disabled

@SpringBootTest ( classes = Spring_Ldap_Bind.BareBoot.class )
@ConfigurationProperties ( prefix = "test.junit.ldap" )

@CsapBareTest.ActiveProfiles_JunitOverRides
public class Spring_Ldap_Bind {
	final static private Logger logger = LoggerFactory.getLogger( Spring_Ldap_Bind.class ) ;

	static {

		CsapApplication.initialize( "Test Setup Complete" ) ;

	}

	@BeforeEach
	public void setup ( ) {

		verify_setup( ) ;
		Assumptions.assumeTrue( isSetupOk( ) ) ;

	}

	@SpringBootConfiguration
	@ImportAutoConfiguration ( classes = {
			PropertyPlaceholderAutoConfiguration.class,
			ConfigurationPropertiesAutoConfiguration.class
	} )
	public static class BareBoot {
	}

	private String url ;

	private String user ;

	private String password ;

	private String searchUser ;

	private String genericTree ;

	private String currentUser ;

	// needed for property placedholder context
	@SpringBootApplication
	static class LdapTestConfiguration {
	}

	public void verify_setup ( ) {

		logger.info( CsapApplication.TC_HEAD ) ;

		logger.info( "ldapUser: {}, ldapPass: {}, ldapUrl: {}, ldapSearchUser: {}"
				+ "\n current user: {}",
				password, user, url, searchUser, currentUser ) ;

		// assertThat( isSetupOk() ).as( "setup ok,
		// ~home/csap/application-company.yml loaded" ).isTrue();
		if ( ! isSetupOk( ) ) {

			logger.warn( "junits requiring a working ldap to test will be skipped;  update application-company.yml" ) ;

		}

	}

	private boolean isSetupOk ( ) {

		// currentUser = springEnvironment.getProperty( "user.name" );

		if ( getUrl( ) == null )
			return false ;

		return true ;

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

			LdapName ldapName = LdapNameBuilder.newInstance( searchUser )
					.add( "uid", currentUser )
					.build( ) ;

			logger.info( "Looking up: {}", ldapName ) ;

			CsapUser csapUser = null ;

			try {

				csapUser = (CsapUser) template.lookup( ldapName, new CsapUser( ) ) ;
				logger.info( "CsapUser Raw Attributes: \n\t"
						+ jacksonMapper.writerWithDefaultPrettyPrinter( ).writeValueAsString(
								csapUser.getAttibutesJson( ) ) ) ;

				assertThat( csapUser.getMail( ) ).startsWith( currentUser + "@" ) ;

				csapUser = (CsapUser) template.lookup( ldapName, CsapUser.PRIMARY_ATTRIBUTES, new CsapUser( ) ) ;
				logger.info( "CsapUser.PRIMARY_ATTRIBUTES  filter: \n\t"
						+ jacksonMapper.writerWithDefaultPrettyPrinter( ).writeValueAsString(
								csapUser.getAttibutesJson( ) ) ) ;

				assertThat( csapUser.getMail( ) ).startsWith( currentUser + "@" ) ;

			} catch ( Exception e ) {

				logger.error( "Failed lookup {}", CSAP.buildCsapStack( e ) ) ;
				numFailures++ ;

			}

			assertThat( csapUser ).isNotNull( ) ;

		}

		logger.info( "maxAttempts :" + maxAttempts + " numFailures: " + numFailures ) ;

	}

	@Test
	public void validate_login_using_context ( )
		throws Exception {

		if ( ! isSetupOk( ) )
			return ;

		LdapTemplate ldapSpringTemplate = buildContextTemplate( ) ;

		try {

			boolean authenticated = ldapSpringTemplate.authenticate(
					genericTree,
					"(uid=" + password + ")",
					user ) ;

			assertThat( authenticated ).as( "Using configured tree" ).isTrue( ) ;

			authenticated = ldapSpringTemplate.authenticate(
					genericTree,
					"(uid=" + password + ")",
					"Wrong Password" ) ;

			assertThat( authenticated ).as( "Incorrect password attempt" ).isFalse( ) ;

			String rootTree = genericTree.split( "," )[1] ;
			authenticated = ldapSpringTemplate
					.authenticate(
							rootTree,
							"(uid=" + password + ")",
							user ) ;

			assertThat( authenticated ).as( "Using root tree" ).isTrue( ) ;

			assertThat(

					ldapSpringTemplate.authenticate(
							searchUser,
							"(uid=" + currentUser + ")",
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

		if ( ! isSetupOk( ) )
			return ;

		LdapTemplate ldapSpringTemplate = new LdapTemplate( ) ;
		LdapContextSource contextSource = new LdapContextSource( ) ;
		contextSource.setUrl( url ) ;

		// Critical: Spring beans must be initialized, and JavaConfig requires
		// explicity calls

		contextSource.afterPropertiesSet( ) ;
		ldapSpringTemplate.setContextSource( contextSource ) ;
		ldapSpringTemplate.afterPropertiesSet( ) ;

		try {

			AndFilter filter = new AndFilter( ) ;

			filter
					.and( new EqualsFilter( "uid", currentUser ) )
					.and( new EqualsFilter( "objectclass", "person" ) ) ;

			logger.info( "Filter: " + filter.toString( ) ) ;

			assertThat(

					ldapSpringTemplate.authenticate(
							searchUser,
							filter.toString( ),
							"Wrong Password" ) )

									.as( "Current User bind attempt" )

									.isFalse( ) ;

		} catch ( Exception e ) {

			logger.error( "Failed to init LDAP", e ) ;
			Assertions.assertTrue( false ) ;

		}

	}

	/**
	 * 
	 * refer to notes at: your LDAP provideer Searches_against_DSX
	 * 
	 * @throws Exception
	 */
	@Test
	public void validate_generic_login_using_context ( )
		throws Exception {

		if ( ! isSetupOk( ) )
			return ;

		LdapTemplate ldapSpringTemplate = buildContextTemplate( ) ;

		try {

			DirContext ctx = null ;
			// this works
			// ctx =
			// contextSource.getContext(ldapUserFull,
			// ldapPass);

			AndFilter filter = new AndFilter( ) ;
			filter.and( new EqualsFilter( "uid", password ) )
					.and( new EqualsFilter( "objectclass", "person" ) ) ;

			assertThat(

					ldapSpringTemplate.authenticate(
							genericTree,
							filter.toString( ),
							user ) )

									.as( "generic login" )

									.isTrue( ) ;

			logger.info( "validated authentication" ) ;

		} catch ( Exception e ) {

			logger.error( "Failed to init LDAP", e ) ;
			Assertions.assertTrue( false ) ;

		}

	}

	private LdapTemplate buildContextTemplate ( )
		throws Exception {

		LdapTemplate ldapSpringTemplate = new LdapTemplate( ) ;
		LdapContextSource contextSource = new LdapContextSource( ) ;

		contextSource.setUserDn( password ) ;
		contextSource.setPassword( user ) ;
		contextSource.setUrl( url ) ;

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

		if ( ! isSetupOk( ) )
			return ;

		LdapTemplate ldapTemplate = buildContextTemplate( ) ;

		CsapUserContextCallback csapUserContextCallback = new CsapUserContextCallback( ldapTemplate ) ;

		assertThat(
				ldapTemplate
						.authenticate(
								genericTree,
								new EqualsFilter( "uid", password ).toString( ),
								user,
								csapUserContextCallback ) )

										.as( "generic login with csap user context created" )

										.isTrue( ) ;

		CsapUser csapUser = csapUserContextCallback.getCsapUser( ) ;
		logger.info( "User from context: {}", csapUser.toString( ) ) ;

		assertThat( csapUser.getFullName( ) )
				.as( "name from context" )
				.isEqualTo( password ) ;

	}

	@Test
	public void validate_user_login_with_csap_user_context_created ( )
		throws Exception {

		if ( ! isSetupOk( ) )
			return ;

		LdapTemplate ldapTemplate = buildContextTemplate( ) ;

		CsapUserContextCallback csapUserContextCallback = new CsapUserContextCallback( ldapTemplate ) ;

		// Validate a wrong password
		boolean authenticated = ldapTemplate.authenticate(
				searchUser,
				new EqualsFilter( "uid", currentUser ).toString( ),
				"Wrong Password",
				csapUserContextCallback ) ;

		if ( authenticated ) {

			CsapUser csapUser = csapUserContextCallback.getCsapUser( ) ;
			logger.info( "User from context: " + csapUser.toString( ) ) ;

		}

		// update with actual password to validate
		assertThat( authenticated ).isFalse( ) ;

	}

	public String getUrl ( ) {

		return url ;

	}

	public void setUrl ( String ldapUrl ) {

		this.url = ldapUrl ;

	}

	public String getUser ( ) {

		return user ;

	}

	public void setUser ( String ldapPass ) {

		this.user = ldapPass ;

	}

	public String getPassword ( ) {

		return password ;

	}

	public void setPassword ( String ldapUser ) {

		this.password = ldapUser ;

	}

	public String getSearchUser ( ) {

		return searchUser ;

	}

	public void setSearchUser ( String ldapSearchUser ) {

		this.searchUser = ldapSearchUser ;

	}

	public String getGenericTree ( ) {

		return genericTree ;

	}

	public void setGenericTree ( String genericTree ) {

		this.genericTree = genericTree ;

	}

	public String getCurrentUser ( ) {

		return currentUser ;

	}

	public void setCurrentUser ( String currentUser ) {

		this.currentUser = currentUser ;

	}
}
