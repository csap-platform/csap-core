package org.csap.security ;

import java.util.Collection ;
import java.util.HashSet ;
import java.util.List ;
import java.util.Map ;

import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.ldap.core.DirContextOperations ;
import org.springframework.security.core.GrantedAuthority ;
import org.springframework.security.core.authority.SimpleGrantedAuthority ;
import org.springframework.security.core.userdetails.UserDetails ;
import org.springframework.security.ldap.userdetails.LdapUserDetailsMapper ;

public class ActiveDirectoryUserMapper extends LdapUserDetailsMapper {

	final static Logger logger = LoggerFactory.getLogger( ActiveDirectoryUserMapper.class ) ;
	Map<String, List<String>> additionalUserRoles ;

	public ActiveDirectoryUserMapper ( Map<String, List<String>> additionalUserRoles ) {

		this.additionalUserRoles = additionalUserRoles ;

	}

	@Override
	public UserDetails mapUserFromContext (
											DirContextOperations ctx ,
											String username ,
											Collection<? extends GrantedAuthority> authorities ) {

		ActiveDirectoryUserDetails.Essence adEssence = new ActiveDirectoryUserDetails.Essence( ctx ) ;
		adEssence.setUsername( username ) ;

		adEssence.setAuthorities( authorities ) ;
		getAdditionalRoles( additionalUserRoles, username ).forEach( role -> {

			adEssence.addAuthority( role ) ;

		} ) ;

		return adEssence.createUserDetails( ) ;

	}

	protected Collection<GrantedAuthority> getAdditionalRoles (
																Map<String, List<String>> additionalUserRoles ,
																String username ) {

		logger.debug( "Checking '{}' for additional roles: '{}'", username, additionalUserRoles ) ;

		Collection<GrantedAuthority> ga = new HashSet<>( ) ;

		if ( additionalUserRoles != null && additionalUserRoles.containsKey( username ) ) {

			additionalUserRoles.get( username ).stream( ).forEach( additionalRole -> {

				ga.add( new SimpleGrantedAuthority( "ROLE_" + additionalRole ) ) ;

			} ) ;

			logger.info( "Added Roles for '{}' : {}", username, ga ) ;

		}

		return ga ;

	}

}
