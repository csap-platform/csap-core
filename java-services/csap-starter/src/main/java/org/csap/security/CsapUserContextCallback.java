package org.csap.security ;

import javax.naming.directory.DirContext ;

import org.springframework.ldap.core.AuthenticatedLdapEntryContextCallback ;
import org.springframework.ldap.core.LdapEntryIdentification ;
import org.springframework.ldap.core.LdapTemplate ;

public class CsapUserContextCallback implements AuthenticatedLdapEntryContextCallback {

	private LdapTemplate ldapTemplate ;

	public CsapUserContextCallback ( LdapTemplate ldapTemplate ) {

		this.ldapTemplate = ldapTemplate ;

	}

	private CsapUser csapUser ;

	public CsapUser getCsapUser ( ) {

		return csapUser ;

	}

	@Override
	public void executeWithContext (
										DirContext ctx ,
										LdapEntryIdentification ldapEntryIdentification ) {
		// TODO Auto-generated method stub

		// logger.info("ldapEntryIdentification.getRelativeDn(): " +
		// ldapEntryIdentification.getRelativeDn()) ;

		csapUser = (CsapUser) ldapTemplate.lookup( ldapEntryIdentification.getRelativeDn( ),
				new CsapUser( ) ) ;

		// ctx.lookup(ldapEntryIdentification.getRelativeDn());
	}

}
