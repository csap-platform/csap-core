package org.csap.security.config ;

import java.util.List ;
import java.util.Map ;

import javax.naming.Name ;

import org.springframework.ldap.support.LdapNameBuilder ;

public class CsapSecurityProvider {

	public static final String TYPE = "csap.security.provider.type" ;

	public static final String MEMORY = "memory" ;

	public static final String LDAP = "ldap" ;

	public static final String ACTIVE_DIRECTORY = "active-directory" ;
	public static final String OAUTH2 = "oauth2" ;

	public static final String NOT_USED = "notUsed" ;

	public static final String DEFAULT = "default" ;

	private List<String> memoryUsers = List.of(
			"admin,password,AUTHENTICATED,ViewRole,AdminRole,dummy1,dummy2",
			"user,password,AUTHENTICATED,ViewRole,AdminRole,dummy3,dummy4" ) ;

	private List<String> ldapBindAttributes = List.of(
			"userid", "mail", "usersHost", "cn", "sn", "title", "employeeType", "mobile",
			"givenName", "street", "memberOf", "groupmembership", "mcoreportingchain",
			"manager", "telephoneNumber", "pwdLastSet" ) ;

	private Map<String, List<String>> additionalLdapRoles ;

	private String directoryDn ;
	private String directoryDnGeneric ;

	private String type = MEMORY ;

	private String domain = NOT_USED ;
	private String url = NOT_USED ;

	private String directoryUser = "" ;
	private String directoryPassword = DEFAULT ;

	private String genericUseridTree = NOT_USED ;
	private String searchGroups = NOT_USED ;
	private String searchGroupFilter ;
	private String searchUser = NOT_USED ;

	// oauth2 support
	private String oauthLoginLocal = NOT_USED ;
	private String oauthLoginPage = NOT_USED ;
	private String oauthUserTokenName = NOT_USED ;
	private String oauthServiceClaimName = NOT_USED ;
	private String oauthClientServiceName = NOT_USED ;

	public boolean isOauth2Enabled ( ) {

		return getType( ).toLowerCase( ).equals( OAUTH2 ) ;

	}

	public boolean isAdEnabled ( ) {

		return getType( ).toLowerCase( ).equals( ACTIVE_DIRECTORY ) ;

	}

	public boolean isLdapEnabled ( ) {

		return getType( ).toLowerCase( ).equals( LDAP ) ;

	}

	public boolean isInMemory ( ) {

		return getType( ).toLowerCase( ).equals( MEMORY ) ;

	}

	public Name getGenericUserDn ( String userid ) {

		return LdapNameBuilder.newInstance( genericUseridTree )
				.add( "uid", userid )
				.build( ) ;

	}

	public Name getLdapSearchUser ( ) {

		return LdapNameBuilder.newInstance( genericUseridTree )
				.add( "uid", getDirectoryUser( ) )
				.build( ) ;

	}

	public String getDomain ( ) {

		return domain ;

	}

	public void setDomain ( String domain ) {

		this.domain = domain ;

	}

	public String getUrl ( ) {

		return url ;

	}

	public void setUrl ( String url ) {

		this.url = url ;

	}

	public String getDirectoryUser ( ) {

		return directoryUser ;

	}

	public void setDirectoryUser ( String directoryUser ) {

		this.directoryUser = directoryUser ;

	}

	public String getDirectoryPassword ( ) {

		return directoryPassword ;

	}

	public void setDirectoryPassword ( String directoryPassword ) {

		this.directoryPassword = directoryPassword ;

	}

	public String getGenericUseridTree ( ) {

		return genericUseridTree ;

	}

	public void setGenericUseridTree ( String genericUseridTree ) {

		this.genericUseridTree = genericUseridTree ;

	}

	public String getSearchGroups ( ) {

		return searchGroups ;

	}

	public void setSearchGroups ( String searchGroups ) {

		this.searchGroups = searchGroups ;

	}

	public String getSearchUser ( ) {

		return searchUser ;

	}

	public void setSearchUser ( String searchUser ) {

		this.searchUser = searchUser ;

	}

	public String getType ( ) {

		return type ;

	}

	public void setType ( String type ) {

		this.type = type ;

	}

	public String getDirectoryDn ( ) {

		return directoryDn ;

	}

	public void setDirectoryDn ( String directoryDn ) {

		this.directoryDn = directoryDn ;

	}

	public String getDirectoryDnGeneric ( ) {

		return directoryDnGeneric ;

	}

	public void setDirectoryDnGeneric ( String directoryDnGen ) {

		this.directoryDnGeneric = directoryDnGen ;

	}

	public List<String> getMemoryUsers ( ) {

		return memoryUsers ;

	}

	public void setMemoryUsers ( List<String> memoryUsers ) {

		this.memoryUsers = memoryUsers ;

	}

	public Map<String, List<String>> getAdditionalLdapRoles ( ) {

		return additionalLdapRoles ;

	}

	public void setAdditionalLdapRoles ( Map<String, List<String>> additionalLdapRoles ) {

		this.additionalLdapRoles = additionalLdapRoles ;

	}

	public String getOauthLoginPage ( ) {

		return oauthLoginPage ;

	}

	public void setOauthLoginPage ( String oathLoginPage ) {

		this.oauthLoginPage = oathLoginPage ;

	}

	public String getOauthUserTokenName ( ) {

		return oauthUserTokenName ;

	}

	public void setOauthUserTokenName ( String oathUserTokenName ) {

		this.oauthUserTokenName = oathUserTokenName ;

	}

	public String getOauthServiceClaimName ( ) {

		return oauthServiceClaimName ;

	}

	public void setOauthServiceClaimName ( String oauthServiceClaimName ) {

		this.oauthServiceClaimName = oauthServiceClaimName ;

	}

	public String getOauthClientServiceName ( ) {

		return oauthClientServiceName ;

	}

	public void setOauthClientServiceName ( String oauthClientServiceName ) {

		this.oauthClientServiceName = oauthClientServiceName ;

	}

	public String getOauthLoginLocal ( ) {

		return oauthLoginLocal ;

	}

	public void setOauthLoginLocal ( String oauthLocalLogin ) {

		this.oauthLoginLocal = oauthLocalLogin ;

	}

	public String loginUrl ( ) {

		if ( isOathLocalLoginSpecified( ) ) {

			return getOauthLoginLocal( ) ;

		} else {

			return getOauthLoginPage( ) ;

		}

	}

	public boolean isOathLocalLoginSpecified ( ) {

		return ! getOauthLoginLocal( ).equals( NOT_USED ) ;

	}

	public List<String> getLdapBindAttributes ( ) {

		return ldapBindAttributes ;

	}

	public void setLdapBindAttributes ( List<String> ldapBindAttributes ) {

		this.ldapBindAttributes = ldapBindAttributes ;

	}

	public String getSearchGroupFilter ( ) {

		return searchGroupFilter ;

	}

	public void setSearchGroupFilter ( String searchGroupFilter ) {

		this.searchGroupFilter = searchGroupFilter ;

	}

}
