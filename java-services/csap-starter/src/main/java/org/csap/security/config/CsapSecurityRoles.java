package org.csap.security.config ;

import java.util.ArrayList ;
import java.util.Collection ;
import java.util.List ;

import javax.servlet.http.HttpSession ;

import org.csap.helpers.CSAP ;
import org.csap.security.CsapUser ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.security.core.GrantedAuthority ;
import org.springframework.security.core.context.SecurityContextHolder ;
import org.springframework.security.core.userdetails.UserDetails ;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser ;

public class CsapSecurityRoles {

	public static final String ROLE_AUTHENTICATED = "ROLE_AUTHENTICATED" ;

	boolean enabled = true ;

	final Logger logger = LoggerFactory.getLogger( this.getClass( ) ) ;

	public static final String SPEL_ROLE = "@" + CsapSecuritySettings.BEAN_NAME + ".roles." ;
	public static final String DEFAULT_TO_BUILD = "defaultToBuild" ;

	private String infraGroup = DEFAULT_TO_BUILD ;
	private String adminGroup = ROLE_AUTHENTICATED ;
	private String superUsers = "noSuperUsers" ;
	private String buildGroup = ROLE_AUTHENTICATED ;
	private String viewGroup = ROLE_AUTHENTICATED ;

	public void checkForInfraDefault ( ) {

		if ( getInfraGroup( ).equals( DEFAULT_TO_BUILD ) ) {

			logger.warn(
					"security.role.infra not found in csapSecurity.properties. Default to be the same as buildGroup" ) ;
			infraGroup = buildGroup ;

		}

		;

	}

	private final static String CSAP_SUPER_USER = "(isAuthenticated() and " + SPEL_ROLE
			+ "superUsers.contains(principal.username))" ;

	public static String hasAny ( Access role ) {

		return "hasAnyRole(" + role.value + ") or " + CSAP_SUPER_USER ;

	}

	public boolean isViewGroupAuthenticateOnly ( ) {

		logger.info( "getViewGroups: ", getViewGroup( ) ) ;

		if ( getViewGroup( ).equals( ROLE_AUTHENTICATED ) ) {

			return true ;

		}

		return false ;

	}

	public static final String VIEW_ROLE = "ViewRole" ;
	public static final String BUILD_ROLE = "BuildRole" ;
	public static final String ADMIN_ROLE = "AdminRole" ;
	public static final String INFRA_ROLE = "InfraRole" ;

	public enum Access {
		admin( SPEL_ROLE + "adminGroup" ),
		infra( SPEL_ROLE + "infraGroup" ),
		build( SPEL_ROLE + "buildGroup" ),
		view( SPEL_ROLE + "viewGroup" ),;

		public String value ;

		private Access ( String value ) {

			this.value = value ;

		}
	}

	/**
	 *
	 * CSAP only roles: view, scm, admin. Use all user roles for generic role
	 * support
	 *
	 * @return
	 */
	public List<String> getUserRolesFromContext ( ) {

		List<String> roles = new ArrayList<String>( ) ;

		if ( ! isEnabled( ) ) {

			roles.add( INFRA_ROLE ) ;
			roles.add( ADMIN_ROLE ) ;
			roles.add( BUILD_ROLE ) ;
			roles.add( VIEW_ROLE ) ;

		} else if ( SecurityContextHolder.getContext( ).getAuthentication( ) != null ) {

			Collection<? extends GrantedAuthority> authorities = SecurityContextHolder.getContext( )
					.getAuthentication( ).getAuthorities( ) ;

			for ( GrantedAuthority grantedAuthority : authorities ) {

				if ( grantedAuthority.getAuthority( ).equals( infraGroup ) ) {

					roles.add( INFRA_ROLE ) ;

				}

				if ( grantedAuthority.getAuthority( ).equals( adminGroup ) ) {

					roles.add( ADMIN_ROLE ) ;

				}

				if ( grantedAuthority.getAuthority( ).equals( buildGroup ) ) {

					roles.add( BUILD_ROLE ) ;

				}

				if ( grantedAuthority.getAuthority( ).equals( viewGroup ) ) {

					roles.add( VIEW_ROLE ) ;

				}

			}

			Object principle = SecurityContextHolder.getContext( ).getAuthentication( ) ;
			String userName = "not-found" ;

			try {

				if ( principle instanceof UserDetails ) {

					UserDetails person = (UserDetails) principle ;
					userName = person.getUsername( ) ;

				} else if ( principle instanceof DefaultOidcUser ) {

					DefaultOidcUser person = (DefaultOidcUser) principle ;
					userName = person.getPreferredUsername( ) ;

				}

			} catch ( Exception e ) {

				logger.warn( "Falling back to default user: {}", CSAP.buildCsapStack( e ) ) ;

				// User p = (User) principle ;
				// userName = SecurityContextHolder.getContext(). ;
			}

			if ( superUsers.contains( userName ) ) {

				roles.add( ADMIN_ROLE ) ;
				roles.add( BUILD_ROLE ) ;
				roles.add( VIEW_ROLE ) ;
				roles.add( INFRA_ROLE ) ;

			}

		} else {

			roles.add( "no-security-context" ) ;

		}

		return roles ;

	}

	// Helper class for ui
	@SuppressWarnings ( "unchecked" )
	public List<String> getAndStoreUserRoles ( HttpSession session ) {

		// Cache in session for performance
		if ( session.getAttribute( "USER_ROLES" ) == null ) {

			session.setAttribute( "USER_ROLES", getUserRolesFromContext( ) ) ;

		}

		return (List<String>) session.getAttribute( "USER_ROLES" ) ;

	}

	public String getUserIdFromContext ( ) {

		String userName = "NotFoundUser" ;

		if ( ! isEnabled( ) ) {

			userName = "securityDisabled" ;

		} else {

			try {

				userName = CsapUser.getContextUser( ) ;

			} catch ( Exception e ) {

				userName = "securityFailed" ;
				// if (capabilityManager.isBootstrapComplete())
				logger.warn( "Failed to get user from security context" ) ;

			}

		}

		return userName ;

	}

	public String getInfraGroup ( ) {

		return infraGroup ;

	}

	public void setInfraGroup ( String infraGroup ) {

		this.infraGroup = infraGroup ;

	}

	public String getAdminGroup ( ) {

		return adminGroup ;

	}

	public void setAdminGroup ( String adminGroup ) {

		this.adminGroup = adminGroup ;

	}

	public String getSuperUsers ( ) {

		return superUsers ;

	}

	public void setSuperUsers ( String superUsers ) {

		this.superUsers = superUsers ;

	}

	public String getBuildGroup ( ) {

		return buildGroup ;

	}

	public void setBuildGroup ( String buildGroup ) {

		this.buildGroup = buildGroup ;

	}

	public String getViewGroup ( ) {

		return viewGroup ;

	}

	public void setViewGroup ( String viewGroup ) {

		this.viewGroup = viewGroup ;

	}

	public List<String> getAllUserRoles ( ) {

		List<String> roles = new ArrayList<String>( ) ;

		Collection<? extends GrantedAuthority> authorities = SecurityContextHolder.getContext( )
				.getAuthentication( ).getAuthorities( ) ;

		for ( GrantedAuthority grantedAuthority : authorities ) {

			roles.add( grantedAuthority.getAuthority( ) ) ;

		}

		return roles ;

	}

	public void addRoleIfUserHasAccess ( HttpSession session , String customRole ) {

		session.removeAttribute( customRole ) ;
		String springRoleMapp = "ROLE_" + customRole.toUpperCase( ) ;

		Collection<? extends GrantedAuthority> authorities = SecurityContextHolder.getContext( )
				.getAuthentication( ).getAuthorities( ) ;

		for ( GrantedAuthority grantedAuthority : authorities ) {

			if ( grantedAuthority.getAuthority( ).equals( springRoleMapp ) ) {

				session.setAttribute( customRole, customRole ) ;

			}

		}

	}

	public boolean hasCustomRole ( HttpSession session , String customRole ) {

		return session.getAttribute( customRole ) != null ;

	}

	@Override
	public String toString ( ) {

		return "RoleSettings [infraGroup=" + infraGroup + ", adminGroup=" + adminGroup + ", superUsers=" + superUsers
				+ ", buildGroup="
				+ buildGroup + ", viewGroup=" + viewGroup + "]" ;

	}

	public boolean isEnabled ( ) {

		return enabled ;

	}

	public void setEnabled ( boolean enabled ) {

		this.enabled = enabled ;

	}

}
