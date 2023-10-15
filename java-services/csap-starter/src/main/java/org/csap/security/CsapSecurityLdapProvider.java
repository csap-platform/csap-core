package org.csap.security ;

import java.util.ArrayList ;
import java.util.List ;

import org.apache.commons.lang3.StringUtils ;
import org.csap.helpers.CSAP ;
import org.csap.helpers.CsapApplication ;
import org.csap.integations.CsapSecurityConfiguration ;
import org.csap.security.config.CsapSecurityProvider ;
import org.csap.security.config.CsapSecuritySettings ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.beans.factory.annotation.Autowired ;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty ;
import org.springframework.context.annotation.Bean ;
import org.springframework.context.annotation.Configuration ;
import org.springframework.ldap.core.DirContextOperations ;
import org.springframework.ldap.core.LdapTemplate ;
import org.springframework.ldap.core.support.LdapContextSource ;
import org.springframework.security.authentication.RememberMeAuthenticationProvider ;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder ;
import org.springframework.security.core.userdetails.UsernameNotFoundException ;
import org.springframework.security.ldap.authentication.BindAuthenticator ;
import org.springframework.security.ldap.authentication.LdapAuthenticationProvider ;
import org.springframework.security.ldap.search.FilterBasedLdapUserSearch ;
import org.springframework.security.ldap.search.LdapUserSearch ;
import org.springframework.security.ldap.userdetails.LdapAuthoritiesPopulator ;
import org.springframework.security.ldap.userdetails.LdapUserDetailsService ;
import org.springframework.security.web.authentication.RememberMeServices ;

@Configuration ( "CsapSecurityLdapProvider" )
@ConditionalOnProperty ( CsapSecurityConfiguration.PROPERTIES_ENABLED )
public class CsapSecurityLdapProvider {

	final Logger logger = LoggerFactory.getLogger( this.getClass( ) ) ;

	CsapSecuritySettings csapSecuritySettings ;

	@Autowired
	public CsapSecurityLdapProvider ( CsapSecuritySettings settings ) {

		this.csapSecuritySettings = settings ;

	}

	// A straight up custom authentication using builder APIs, but some
	// companies does not use
	// standard LDAP group membership
	// auth.ldapAuthentication()
	// .userDnPatterns(dn, dnGen)
	// .userDetailsContextMapper(customContextMapper)
	// .groupSearchBase(searchGroups)
	// .contextSource(getContextSource())
	// .groupSearchFilter("groupmembership")
	// .rolePrefix("ROLE_");

	public void configureLdapProvider (
										AuthenticationManagerBuilder authenticationBuilder ,
										CsapSecuritySettings csapSecuritySettings ,
										List<String> attributesToGetOnLdapBind ) {

		try {

			//
			// Custom support: authentication and authorization via LDAP
			//
			BindAuthenticator ldapBindAuthenticator = new BindAuthenticator(
					csapSecuritySettings.getContextSource( ) ) ;

			if ( csapSecuritySettings.isAuthenticationUsingLdapSearch( ) ) {

				// LDAP DN uses the user name or other, so we need to search
				ldapBindAuthenticator
						.setUserSearch( csapSecuritySettings.buildLdapSearch( ) ) ;

			} else {

				// LDAP DN uses the login
				ldapBindAuthenticator.setUserDnPatterns(
						new String[] {
								csapSecuritySettings.getProvider( ).getDirectoryDn( ), csapSecuritySettings
										.getProvider( )
										.getDirectoryDnGeneric( )
						} ) ;

			}

			ldapBindAuthenticator.setUserAttributes( attributesToGetOnLdapBind.toArray( String[]::new ) ) ;
			LdapAuthenticationProvider ldapAuthProvider = new LdapAuthenticationProvider(
					ldapBindAuthenticator,
					ldapAuthoritiesPopulator( ) ) ;

			ldapAuthProvider.setAuthoritiesMapper( csapAuthoritiesMapper( ) ) ;

			ldapAuthProvider.setUserDetailsContextMapper( getCustomContextMapper( ) ) ;
			authenticationBuilder.authenticationProvider( ldapAuthProvider ) ;

			//
			// CS-AP SSO Support
			//
			// logger.info("encryptKey" + encryptKey);
			RememberMeAuthenticationProvider csapSSoAuth = new RememberMeAuthenticationProvider( csapSecuritySettings
					.getCookieEncrypt( ) ) ;
			authenticationBuilder.authenticationProvider( csapSSoAuth ) ;

			logger.debug( "Added in Remember me" ) ;

		} catch ( Exception e ) {

			logger.error( CsapApplication.header( "Failed creating CSAP ldap provider: {}" ), CSAP.buildCsapStack(
					e ) ) ;
			throw e ;

		}

	}

	@Bean
	@ConditionalOnProperty ( name = CsapSecurityProvider.TYPE , havingValue = CsapSecurityProvider.LDAP )
	public LdapTemplate ldapTemplate ( ) {

		LdapTemplate template = new LdapTemplate( ) ;

		try {

			LdapContextSource contextSource = new LdapContextSource( ) ;

			if ( csapSecuritySettings.getProvider( ).getDirectoryUser( ).length( ) > 0 ) {

				contextSource.setUserDn( csapSecuritySettings.getProvider( ).getLdapSearchUser( ).toString( ) ) ;

				contextSource.setPassword( csapSecuritySettings.getContextPassword( ) ) ;

			} else {

				logger.debug( "Using anonymous binds for search" ) ;

			}

			contextSource.setUrl( csapSecuritySettings.getProvider( ).getUrl( ) ) ;

			contextSource.afterPropertiesSet( ) ;
			template.setContextSource( contextSource ) ;
			template.afterPropertiesSet( ) ;

		} catch ( Exception e ) {

			logger.error( "Failed LDAP initialization: {}", CSAP.buildCsapStack( e ) ) ;

		}

		StringBuilder builder = new StringBuilder( ) ;

		builder.append( "\n\n ==========================" ) ;
		builder.append( "\n LdapTemplate for accessing Identity" ) ;
		builder.append( "\n Directory url: " + csapSecuritySettings.getProvider( ).getUrl( ) ) ;
		builder.append( "\n Access User: " + csapSecuritySettings.getProvider( ).getDirectoryUser( ) ) ;
		builder.append( "\n Access Tree: " + csapSecuritySettings.getProvider( ).getGenericUseridTree( ) ) ;
		builder.append( "\n Directory user search tree: " + csapSecuritySettings.getProvider( ).getSearchUser( ) ) ;
		builder.append( "\n==========================\n\n" ) ;

		logger.debug( builder.toString( ) ) ;

		return template ;

	}

	/**
	 * Custom does not use groupmembership attribute in activedirectory, but stores
	 * groups in an attribute
	 *
	 * To hook into Spring, a custom populator is necessary. For more insight,
	 * download Softerra ldap browser and explore active directory.
	 *
	 * @return
	 */

	@Bean
	@ConditionalOnProperty ( name = CsapSecurityProvider.TYPE , havingValue = CsapSecurityProvider.LDAP )
	public LdapAuthoritiesPopulator ldapAuthoritiesPopulator ( ) {

//		var ldapAuthPopulator = new DefaultLdapAuthoritiesPopulator(
//				settings.getContextSource( ), settings.getProvider( ).getSearchGroups( ) ) ;

//		ldapAuthPopulator.setGroupSearchFilter("(uniquemember={0})") ;
//		ldapAuthPopulator.setGroupSearchFilter("(member={0})") ;

//		settings.getProvider( ).getSearchGroups( )

		logger.debug( "Creating populator" ) ;

		var ldapAuthPopulator = //
				new CsapAuthoritiesLdapPopulator(
						csapSecuritySettings.getProvider( ).getAdditionalLdapRoles( ),
						csapSecuritySettings.getContextSource( ),
						csapSecuritySettings.getProvider( ).getSearchGroups( ) ) ;

		if ( StringUtils.isNotEmpty( csapSecuritySettings.getProvider( ).getSearchGroupFilter( ) ) ) {

			ldapAuthPopulator.setGroupSearchFilter( csapSecuritySettings.getProvider( ).getSearchGroupFilter( ) ) ;

		}

		return ldapAuthPopulator ;

	}

	@Bean
	@ConditionalOnProperty ( name = CsapSecurityProvider.TYPE , havingValue = CsapSecurityProvider.LDAP )
	public RememberMeServices getCsapSingleSignOn ( CsapSecuritySettings csapSecuritySettings ) {

		LdapUserDetailsService userDetailsService = new LdapUserDetailsService(
				getMultipleLdapTreeSearch( ),
				ldapAuthoritiesPopulator( ) ) ;

		userDetailsService.setUserDetailsMapper( getCustomContextMapper( ) ) ;

		// logger.info("encryptKey" + encryptKey);
		CustomRememberMeService rememberMe = new CustomRememberMeService( csapSecuritySettings.getCookieEncrypt( ),
				userDetailsService ) ;
		rememberMe.setCookieName( csapSecuritySettings.getCookie( ).getName( ) ) ;
		rememberMe.setTokenValiditySeconds( csapSecuritySettings.getCookie( ).getExpireSeconds( ) ) ;
		rememberMe.setAlwaysRemember( true ) ;

		rememberMe.setAuthoritiesMapper( csapAuthoritiesMapper( ) ) ;

		return rememberMe ;

	}

	@Bean
	public CsapAuthoritiesMapper csapAuthoritiesMapper ( ) {

		CsapAuthoritiesMapper mapper = new CsapAuthoritiesMapper( ) ;
		return mapper ;

	}

	/**
	 * Custom specific attributes from LDAP
	 *
	 * @return
	 */
	@Bean
	public CustomContextMapper getCustomContextMapper ( ) {

		CustomContextMapper customContextMapper = new CustomContextMapper( ) ;
		return customContextMapper ;

	}

	@Bean
	@ConditionalOnProperty ( name = CsapSecurityProvider.TYPE , havingValue = CsapSecurityProvider.LDAP )
	public MultipleLdapTreeSearch getMultipleLdapTreeSearch ( ) {
		// FilterBasedLdapUserSearch filterUserSearch = new
		// FilterBasedLdapUserSearch(
		// searchUser, "(uid={0})",
		// getContextSource() );
		//
		// LdapUserDetailsService userDetailsService = new
		// LdapUserDetailsService(
		// filterUserSearch,
		// getActiveDirGroupsProvider() );

		FilterBasedLdapUserSearch primaryUsers = new FilterBasedLdapUserSearch(
				csapSecuritySettings.getProvider( ).getSearchUser( ), "(uid={0})",
				csapSecuritySettings.getContextSource( ) ) ;

		FilterBasedLdapUserSearch genericUsers = new FilterBasedLdapUserSearch(
				csapSecuritySettings.getProvider( ).getGenericUseridTree( ), "(uid={0})",
				csapSecuritySettings.getContextSource( ) ) ;

		MultipleLdapTreeSearch multipleTreeSearch = new MultipleLdapTreeSearch( primaryUsers ) ;
		multipleTreeSearch.addSearch( genericUsers ) ;

		return multipleTreeSearch ;

	}

	/**
	 * 
	 * Helper class to support multiple user contexts in LDAP tree. eg. real versus
	 * generic ids.
	 * 
	 * @author pnightin
	 *
	 */
	public class MultipleLdapTreeSearch implements LdapUserSearch {
		public static final String SAM_FILTER = "(&(sAMAccountName={0})(objectclass=user))" ;

		List<LdapUserSearch> treesToSearch ;

		public MultipleLdapTreeSearch ( LdapUserSearch primarySearch ) {

			treesToSearch = new ArrayList<>( ) ;
			treesToSearch.add( primarySearch ) ;

		}

		public void addSearch ( LdapUserSearch alternateSearch ) {

			logger.info( "Adding: {}", alternateSearch ) ;
			treesToSearch.add( alternateSearch ) ;

		}

		public DirContextOperations searchForUser ( String username ) {

			try {

				return treesToSearch.get( 0 ).searchForUser( username ) ;

			} catch ( UsernameNotFoundException e ) {

				if ( treesToSearch.size( ) > 1 ) {

					return treesToSearch.get( 1 ).searchForUser( username ) ;

				} else {

					throw e ;

				}

			}

		}
	}

}
