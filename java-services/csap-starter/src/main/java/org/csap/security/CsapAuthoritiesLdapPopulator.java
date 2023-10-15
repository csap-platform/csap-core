package org.csap.security ;

import java.util.HashSet ;
import java.util.List ;
import java.util.Map ;
import java.util.Set ;

import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.ldap.core.ContextSource ;
import org.springframework.ldap.core.DirContextOperations ;
import org.springframework.security.core.GrantedAuthority ;
import org.springframework.security.core.authority.SimpleGrantedAuthority ;
import org.springframework.security.ldap.userdetails.DefaultLdapAuthoritiesPopulator ;

public class CsapAuthoritiesLdapPopulator extends DefaultLdapAuthoritiesPopulator {

	final Logger logger = LoggerFactory.getLogger( this.getClass( ) ) ;

	Map<String, List<String>> additionalUserRoles ;

	public CsapAuthoritiesLdapPopulator ( Map<String, List<String>> additionalUserRoles, ContextSource contextSource,
			String groupSearchBase ) {

		super( contextSource, groupSearchBase ) ;

		this.additionalUserRoles = additionalUserRoles ;

	}

	protected Set<GrantedAuthority> getAdditionalRoles (
															DirContextOperations user ,
															String username ) {

		logger.debug( "Checking '{}' for additional roles: '{}'", username, additionalUserRoles ) ;

		if ( additionalUserRoles != null && additionalUserRoles.containsKey( username ) ) {

			Set<GrantedAuthority> ga = new HashSet<>( ) ;

			additionalUserRoles.get( username ).stream( ).forEach( additionalRole -> {

				ga.add( new SimpleGrantedAuthority( getRolePrefix( ) + additionalRole ) ) ;

			} ) ;

			logger.info( "Added Roles for '{}' : {}", username, ga ) ;

			return ga ;

		} else {

			return null ;

		}

	}

}
