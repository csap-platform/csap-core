package org.csap.security.config ;

import javax.annotation.PostConstruct ;
import javax.naming.Name ;
import javax.naming.ldap.LdapName ;

import org.jasypt.encryption.pbe.StandardPBEStringEncryptor ;
import org.jasypt.exceptions.EncryptionOperationNotPossibleException ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.beans.factory.annotation.Autowired ;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty ;
import org.springframework.boot.context.properties.ConfigurationProperties ;
import org.springframework.context.annotation.Bean ;
import org.springframework.context.annotation.Configuration ;
import org.springframework.ldap.core.support.BaseLdapPathContextSource ;
import org.springframework.ldap.support.LdapNameBuilder ;
import org.springframework.security.ldap.DefaultSpringSecurityContextSource ;
import org.springframework.security.ldap.search.FilterBasedLdapUserSearch ;
import org.springframework.security.ldap.userdetails.LdapUserDetails ;
import org.springframework.security.ldap.userdetails.LdapUserDetailsService ;

@Configuration ( CsapSecuritySettings.BEAN_NAME )
@ConfigurationProperties ( prefix = "csap.security" )
public class CsapSecuritySettings {

	final Logger logger = LoggerFactory.getLogger( this.getClass( ) ) ;

	public static final String DEFAULT = "default" ;

	public static final String BEAN_NAME = "CsapSecuritySettings" ;

	@PostConstruct
	public void updateSettings ( ) {

		roles.setEnabled( isEnabled( ) ) ;

		if ( ! isEnabled( ) ) {

			logger.warn( "\n\n\n *** SECURITY DISABLED *** \n\n\n" ) ;

		}

	}

	//
	// Setting objects
	//
	private CsapSecurityRoles roles = new CsapSecurityRoles( ) ;
	private CsapSecurityProvider provider = new CsapSecurityProvider( ) ;
	private CookieSettings cookie = new CookieSettings( ) ;

	private boolean enabled = false ;
	private boolean csrfEnabled = false ;
	private int maxConcurrentUserSessions = 0 ;

	private boolean hstsEnabled = false ;

	private String sslLoginUrl = "" ;

	public Name getRealUserDn ( String userid ) {

		LdapName dnForUserid = null ;

		if ( isAuthenticationUsingLdapSearch( ) ) {

			LdapUserDetailsService ldapUserSearch = new LdapUserDetailsService( buildLdapSearch( ) ) ;

			LdapUserDetails ldapUserDetails = (LdapUserDetails) ldapUserSearch.loadUserByUsername( userid ) ;

			dnForUserid = LdapNameBuilder.newInstance( ldapUserDetails.getDn( ) ).build( ) ;

		} else {

			dnForUserid = LdapNameBuilder.newInstance( provider.getSearchUser( ) )
					.add( "uid", userid )
					.build( ) ;

		}

		logger.info( "dnForUserid: {}", dnForUserid ) ;
		return dnForUserid ;

	}

	/**
	 * Filter search is required when the LDAP DN is NOT same as uid
	 * 
	 * @return
	 */
	public FilterBasedLdapUserSearch buildLdapSearch ( ) {

		String dnSearchAttribute = provider.getDirectoryDn( ).split( ":" )[1] ;
		return new FilterBasedLdapUserSearch(
				provider.getSearchUser( ),
				dnSearchAttribute,
				getContextSource( ) ) ;

	}

	/**
	 * @return
	 */
	@Bean
	@ConditionalOnProperty ( name = CsapSecurityProvider.TYPE , havingValue = CsapSecurityProvider.LDAP )
	public BaseLdapPathContextSource getContextSource ( ) {

		logger.debug( "Constructing using url: {} , user: {} ", provider.getUrl( ), getProvider( )
				.getDirectoryUser( ) ) ;
		// + " directoryPass" + directoryPass
		DefaultSpringSecurityContextSource ldapContext = new DefaultSpringSecurityContextSource( provider.getUrl( ) ) ;

		if ( provider.getDirectoryUser( ).length( ) > 0 ) {

			ldapContext.setUserDn( provider.getDirectoryUser( ) ) ;
			ldapContext.setPassword( getContextPassword( ) ) ;

		} else {

			logger.info( "Using anonymous binds to ldap" ) ;

		}

		return ldapContext ;

	}

	@Autowired ( required = false )
	StandardPBEStringEncryptor encryptor = null ;

	// support migration to more secure config
	private String decodeProperty ( String itemWithEncyrptSupport , String description ) {

		logger.debug( "itemWithEncyrptSupport: {} ", itemWithEncyrptSupport ) ;
		String password = itemWithEncyrptSupport ;

		try {

			if ( encryptor != null ) {

				password = encryptor.decrypt( itemWithEncyrptSupport ) ;

			} else {

				logger.error( "Did not get encrypter = password wil not be decrypted" ) ;

			}

		} catch ( EncryptionOperationNotPossibleException e ) {

			logger.warn( "{} is not encrypted. Use CSAP encrypt to generate", description ) ;

		}

		// logger.debug("Raw: {}, post: {}", directoryPass, password) ;
		return password ;

	}

	public String getContextPassword ( ) {

		return decodeProperty(
				provider.getDirectoryPassword( ),
				"csap.security.provider.password" ) ;

	}

	public boolean isAuthenticationUsingLdapSearch ( ) {

		return provider.getDirectoryDn( ).startsWith( "search:" ) ;

	}

	/**
	 * @return the remoteUrl
	 */
	public String getSslLoginUrl ( ) {

		return sslLoginUrl ;

	}

	public boolean isCsrfEnabled ( ) {

		return csrfEnabled ;

	}

	public void setCsrfEnabled ( boolean csrfEnabled ) {

		this.csrfEnabled = csrfEnabled ;

	}

	public int getMaxConcurrentUserSessions ( ) {

		return maxConcurrentUserSessions ;

	}

	public void setMaxConcurrentUserSessions ( int maxConcurrentUserSessions ) {

		this.maxConcurrentUserSessions = maxConcurrentUserSessions ;

	}

	private String token = null ;

	public String getCookieEncrypt ( ) {

		if ( token == null ) {

			// only do once
			token = decodeProperty( getCookie( ).getToken( ), "csap.security.cookie.token" ) ;

		}

		return token ;

	}

	public void setSslLoginUrl ( String sslLoginUrl ) {

		this.sslLoginUrl = sslLoginUrl ;

	}

	public CsapSecurityRoles getRoles ( ) {

		return roles ;

	}

	public void setRoles ( CsapSecurityRoles roles ) {

		this.roles = roles ;

	}

	public CsapSecurityProvider getProvider ( ) {

		return provider ;

	}

	public void setProvider ( CsapSecurityProvider provider ) {

		this.provider = provider ;

	}

	public CookieSettings getCookie ( ) {

		return cookie ;

	}

	public void setCookie ( CookieSettings cookie ) {

		this.cookie = cookie ;

	}

	public boolean isEnabled ( ) {

		return enabled ;

	}

	public void setEnabled ( boolean enabled ) {

		this.enabled = enabled ;

	}

	@Override
	public String toString ( ) {

		return "CsapSecuritySettings [roles=" + roles + ", provider=" + provider + ", cookie=" + cookie + ", enabled="
				+ enabled + ", csrfEnabled=" + csrfEnabled + ", maxConcurrentUserSessions=" + maxConcurrentUserSessions
				+ ", sslLoginUrl=" + sslLoginUrl + "]" ;

	}

	public boolean isHstsEnabled ( ) {

		return hstsEnabled ;

	}

	public void setHstsEnabled ( boolean disableHsts ) {

		this.hstsEnabled = disableHsts ;

	}

}
