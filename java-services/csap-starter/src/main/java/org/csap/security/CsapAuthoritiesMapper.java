package org.csap.security ;

import java.util.ArrayList ;
import java.util.Collection ;

import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.security.core.GrantedAuthority ;
import org.springframework.security.core.authority.SimpleGrantedAuthority ;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper ;

public class CsapAuthoritiesMapper implements GrantedAuthoritiesMapper {
	final Logger logger = LoggerFactory.getLogger( this.getClass( ) ) ;

	@Override
	public Collection<? extends GrantedAuthority> mapAuthorities (
																	Collection<? extends GrantedAuthority> authorities ) {

		// TODO Auto-generated method stub
		StringBuilder builder = new StringBuilder( "Authorities mapped: " ) ;

		Collection<GrantedAuthority> ga = new ArrayList<GrantedAuthority>( ) ;
		int i = 0 ;

		for ( GrantedAuthority grantedAuthority : authorities ) {

			builder.append( grantedAuthority.toString( ) ) ;
			builder.append( ", \t" ) ;

			if ( i++ > 6 ) {

				builder.append( "\n" ) ;
				i = 0 ;

			}

			ga.add( grantedAuthority ) ;

		}

		// used for provisioning roles in application.yml
		SimpleGrantedAuthority csapAuthenticatedAuthority = new SimpleGrantedAuthority( "ROLE_AUTHENTICATED" ) ;
		ga.add( csapAuthenticatedAuthority ) ;
		builder.append( "\n" + csapAuthenticatedAuthority.toString( ) ) ;

		logger.debug( builder.toString( ) ) ;

		return ga ;

	}

}
