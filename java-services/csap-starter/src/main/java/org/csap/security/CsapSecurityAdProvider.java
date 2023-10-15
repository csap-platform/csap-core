package org.csap.security ;

import org.csap.integations.CsapSecurityConfiguration ;
import org.csap.security.config.CsapSecurityProvider ;
import org.csap.security.config.CsapSecuritySettings ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.beans.factory.annotation.Autowired ;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty ;
import org.springframework.context.annotation.Bean ;
import org.springframework.context.annotation.Configuration ;
import org.springframework.security.ldap.authentication.ad.ActiveDirectoryLdapAuthenticationProvider ;

@Configuration ( "CsapSecurityAdProvider" )
@ConditionalOnProperty ( CsapSecurityConfiguration.PROPERTIES_ENABLED )
public class CsapSecurityAdProvider {

	final Logger logger = LoggerFactory.getLogger( this.getClass( ) ) ;

	CsapSecuritySettings csapSecuritySettings ;
	CsapSecurityLdapProvider csapSecurityLdapProvider ;

	@Autowired
	public CsapSecurityAdProvider ( CsapSecuritySettings settings, CsapSecurityLdapProvider csapSecurityLdapProvider ) {

		this.csapSecuritySettings = settings ;
		this.csapSecurityLdapProvider = csapSecurityLdapProvider ;

	}

	@Bean
	@ConditionalOnProperty ( name = CsapSecurityProvider.TYPE , havingValue = CsapSecurityProvider.ACTIVE_DIRECTORY )
	public ActiveDirectoryLdapAuthenticationProvider activeDirectoryLdapAuthenticationProvider ( ) {

		ActiveDirectoryLdapAuthenticationProvider provider = new ActiveDirectoryLdapAuthenticationProvider(
				csapSecuritySettings.getProvider( ).getDomain( ),
				csapSecuritySettings.getProvider( ).getUrl( ) ) ;

		provider.setAuthoritiesMapper( csapSecurityLdapProvider.csapAuthoritiesMapper( ) ) ;

		provider.setUserDetailsContextMapper( activeDirectoryUserMapper( ) ) ;

		provider.setConvertSubErrorCodesToExceptions( true ) ;

		return provider ;

	}

	@Bean
	ActiveDirectoryUserMapper activeDirectoryUserMapper ( ) {

		return new ActiveDirectoryUserMapper( csapSecuritySettings.getProvider( ).getAdditionalLdapRoles( ) ) ;

	}

}
