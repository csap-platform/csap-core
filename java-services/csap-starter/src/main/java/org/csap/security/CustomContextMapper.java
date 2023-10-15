package org.csap.security ;

import java.util.Collection ;

import org.springframework.ldap.core.DirContextAdapter ;
import org.springframework.ldap.core.DirContextOperations ;
import org.springframework.security.core.GrantedAuthority ;
import org.springframework.security.core.userdetails.UserDetails ;
import org.springframework.security.ldap.userdetails.UserDetailsContextMapper ;
import org.springframework.util.Assert ;

public class CustomContextMapper implements UserDetailsContextMapper {

	@Override
	public UserDetails mapUserFromContext (
											DirContextOperations ctx ,
											String username ,
											Collection<? extends GrantedAuthority> authorities ) {

		CustomUserDetails.Essence p = new CustomUserDetails.Essence( ctx ) ;

		p.setUsername( username ) ;
		p.setAuthorities( authorities ) ;

		return p.createUserDetails( ) ;

	}

	public void mapUserToContext ( UserDetails user , DirContextAdapter ctx ) {

		Assert.isInstanceOf( CustomUserDetails.class, user, "UserDetails must be a CustomUserDetails instance" ) ;

		CustomUserDetails p = (CustomUserDetails) user ;
		p.populateContext( ctx ) ;

	}

}
