package org.csap.security ;

import java.io.IOException ;

import javax.servlet.http.HttpServletRequest ;
import javax.servlet.http.HttpServletResponse ;

import org.csap.docs.CsapDoc ;
import org.csap.helpers.CSAP ;
import org.csap.integations.CsapInformation ;
import org.csap.integations.CsapWebServerConfig ;
import org.csap.security.config.CsapSecuritySettings ;
import org.csap.security.oath2.CsapOauth2SecurityConfiguration ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.beans.factory.annotation.Autowired ;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty ;
import org.springframework.boot.context.properties.ConfigurationProperties ;
import org.springframework.core.io.ClassPathResource ;
import org.springframework.security.core.Authentication ;
import org.springframework.security.web.csrf.CsrfToken ;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache ;
import org.springframework.security.web.savedrequest.SavedRequest ;
import org.springframework.stereotype.Controller ;
import org.springframework.ui.Model ;
import org.springframework.web.bind.annotation.GetMapping ;
import org.springframework.web.bind.annotation.RequestMapping ;

import io.micrometer.core.instrument.util.StringUtils ;

// Adds controllers for login page
@Controller ( "CsapSecurityLoginController" )
@ConditionalOnProperty ( "csap.security.enabled" )
@ConfigurationProperties ( prefix = "csap.security" )

@CsapDoc ( title = "CSAP Security Requests" , type = CsapDoc.OTHER , notes = "Provides authentication endpoints, including error redirects and pages. "
		+ "Note: /logout handling is provided via CsapSecurityConfiguration" )
public class CsapSecurityController {

	private static final String CSAP_SECURITY_TXT = "application definition" ;
	final Logger logger = LoggerFactory.getLogger( this.getClass( ) ) ;

	private CsapSecuritySettings settings ;

	@Autowired
	public CsapSecurityController ( CsapSecuritySettings settings ) {

		this.settings = settings ;
		logger.debug( toString( ) ) ;

	}

	public String toString ( ) {

		StringBuilder builder = new StringBuilder( ) ;
		builder.append( "\n csap.security login controller: " ) ;

		try {

			builder.append( CSAP.padLine( "Login Page" ) + ( new ClassPathResource(
					"/templates/csap/security/login.html" ) ).getURI( ) ) ;

		} catch ( IOException e ) {

			builder.append( "\n Missing Login Page: ERROR - NOT FOUND" ) ;
			logger.error( "Missing Login Page: ERROR - NOT FOUND", e ) ;

		}

		builder.append( CSAP.padLine( "Registered Urls" ) + "login, loginError, accessError, sessionError" ) ;
		builder.append( "\n " ) ;

		return builder.toString( ) ;

	}

	@CsapDoc ( notes = "Test Page for user component" )
	@RequestMapping ( "/CsapUser" )
	public String csapUser ( Model springViewModel ) {

		return "csap/components/CsapUser" ;

	}

	@Autowired ( required = false )
	CsapOauth2SecurityConfiguration csapOauth ;

	@CsapDoc ( notes = "Performs Login" )
	@GetMapping ( "/logout" )
	public String logout (
							Model springViewModel ,
							HttpServletRequest request ,
							HttpServletResponse response ,
							Authentication authentication ) {

		String viewResult = "csap/security/login" ;
		csapOauth.getOpenIdLogoutHandler( ).logout( request, response, authentication ) ;

		return "redirect:/" ;

	}

	@Autowired ( required = false )
	CsapWebServerConfig csapWebServer ;

	public static final String LOGIN_URL = "/login" ;

	@CsapDoc ( notes = "Performs Login" )
	@GetMapping ( LOGIN_URL )
	public String login (
							CsrfToken token ,
							Model springViewModel ,
							HttpServletRequest request ,
							HttpServletResponse response ) {

		logger.debug( "showing login page" ) ;

		// token not needed as a parameter - but nice to show
		if ( token != null ) {

			logger.debug( "performing login using: {} value: {}", token.getParameterName( ), token.getToken( ) ) ;

		}

		springViewModel.addAttribute( "serviceName", getServiceName( ) ) ;
		springViewModel.addAttribute( "serviceVersion", getServiceVersion( ) ) ;
		springViewModel.addAttribute( "admin", settings.getRoles( ).getAdminGroup( ) ) ;
		springViewModel.addAttribute( "view", settings.getRoles( ).getViewGroup( ) ) ;
		springViewModel.addAttribute( "build", settings.getRoles( ).getBuildGroup( ) ) ;
		springViewModel.addAttribute( "infra", settings.getRoles( ).getInfraGroup( ) ) ;
		springViewModel.addAttribute( "ldap", settings.getProvider( ).getUrl( ) ) ;

		String secureUrl = null ;

		if ( request.getScheme( ).equals( "http" ) ) {

			springViewModel.addAttribute( "nonSsl", "true" ) ;
			String sslLoginUrl = settings.getSslLoginUrl( ) ;

			if ( StringUtils.isEmpty( sslLoginUrl )
					&& request.getServerName( ).contains( ".yourcompany.com" ) ) {

				sslLoginUrl = "https://csap-secure.yourcompany.com/admin/ssoLogin" ;

			}

			if ( StringUtils.isNotEmpty( sslLoginUrl ) ) {

				SavedRequest savedRequest = new HttpSessionRequestCache( ).getRequest( request, response ) ;

				if ( savedRequest != null ) {

					springViewModel.addAttribute( "sslLoginUrl", sslLoginUrl + "?ref=" + savedRequest
							.getRedirectUrl( ) ) ;

				}

			} else if ( csapWebServer != null ) {

				secureUrl = csapWebServer.getSecureUrl( request ) + LOGIN_URL ;

			}

		}

		springViewModel.addAttribute( "secureUrl", secureUrl ) ;

		if ( ! settings.getProvider( ).isLdapEnabled( ) && ! settings.getProvider( ).isAdEnabled( ) ) {

			springViewModel.addAttribute( "ldap", "In Memory Authentication" ) ;
			springViewModel.addAttribute( "admin", CSAP_SECURITY_TXT ) ;
			springViewModel.addAttribute( "view", CSAP_SECURITY_TXT ) ;
			springViewModel.addAttribute( "build", CSAP_SECURITY_TXT ) ;
			springViewModel.addAttribute( "infra", CSAP_SECURITY_TXT ) ;

		}

		if ( settings.getProvider( ).isAdEnabled( ) ) {

			springViewModel.addAttribute( "adDomain", settings.getProvider( ).getDomain( ) ) ;

		}

		String viewResult = "csap/security/login" ;

		if ( settings.getProvider( ).isOathLocalLoginSpecified( ) ) {

			springViewModel.addAttribute( "oauthAlternateLogin", settings.getProvider( ).getOauthLoginPage( ) ) ;

			if ( StringUtils.isEmpty( request.getParameter( "local" ) ) ) {

				viewResult = "redirect:" + settings.getProvider( ).getOauthLoginPage( ) ;

			}

		}

		return viewResult ;

	}

	@RequestMapping ( "/loginError" )
	public String error ( Model springViewModel ) {

		logger.debug( "performing loginError" ) ;

		springViewModel.addAttribute( "serviceName", getServiceName( ) ) ;
		springViewModel.addAttribute( "serviceVersion", getServiceVersion( ) ) ;

		return "csap/security/loginError" ;

	}

	@RequestMapping ( "/accessError" )
	public String accessError ( Model springViewModel ) {

		logger.debug( "performing accessError" ) ;

		springViewModel.addAttribute( "serviceName", getServiceName( ) ) ;
		springViewModel.addAttribute( "serviceVersion", getServiceVersion( ) ) ;

		return "csap/security/accessError" ;

	}

	@RequestMapping ( "/sessionError" )
	public String sessionError ( Model springViewModel ) {

		logger.debug( "performing sessionInvalidation" ) ;

		springViewModel.addAttribute( "serviceName", getServiceName( ) ) ;
		springViewModel.addAttribute( "serviceVersion", getServiceVersion( ) ) ;

		return "csap/security/sessionError" ;

	}

	@Autowired
	CsapInformation csapInformation ;

	public String getServiceName ( ) {

		return csapInformation.getName( ) ;

	}

	public String getServiceVersion ( ) {

		return csapInformation.getVersion( ) ;

	}

}
