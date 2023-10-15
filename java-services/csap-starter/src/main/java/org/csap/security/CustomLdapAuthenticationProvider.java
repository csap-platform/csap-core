package org.csap.security ;

import org.csap.integations.micrometer.CsapMeterUtilities ;
import org.springframework.security.core.Authentication ;
import org.springframework.security.core.AuthenticationException ;
import org.springframework.security.ldap.authentication.LdapAuthenticationProvider ;
import org.springframework.security.ldap.authentication.LdapAuthenticator ;
import org.springframework.security.ldap.authentication.NullLdapAuthoritiesPopulator ;
import org.springframework.security.ldap.userdetails.LdapAuthoritiesPopulator ;

import io.micrometer.core.instrument.Timer ;

public class CustomLdapAuthenticationProvider extends LdapAuthenticationProvider {

	public CustomLdapAuthenticationProvider ( LdapAuthenticator authenticator,
			LdapAuthoritiesPopulator authoritiesPopulator ) {

		super( authenticator, authoritiesPopulator ) ;

	}

	/**
	 * Creates an instance with the supplied authenticator and a null authorities
	 * populator. In this case, the authorities must be mapped from the user
	 * context.
	 *
	 * @param authenticator the authenticator strategy.
	 */
	public CustomLdapAuthenticationProvider ( LdapAuthenticator authenticator ) {

		super( authenticator, new NullLdapAuthoritiesPopulator( ) ) ;

	}

	// Slim wrapper to capture LDAP timings
	public Authentication authenticate ( Authentication authentication )
		throws AuthenticationException {

		logger.debug( "New authentication" ) ;
		// Split split =
		// SimonManager.getStopwatch("csap.security.authenticate").start();
		Timer.Sample ssoTimer = CsapMeterUtilities.supportForNonSpringConsumers( ).startTimer( ) ;
		Authentication result = super.authenticate( authentication ) ;
		CsapMeterUtilities.supportForNonSpringConsumers( ).stopTimer( ssoTimer, "csap.security.authenticate" ) ;
		return result ;

	}
}
