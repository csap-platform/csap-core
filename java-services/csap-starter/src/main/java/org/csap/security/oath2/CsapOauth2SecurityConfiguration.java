package org.csap.security.oath2 ;

import java.net.URI ;
import java.time.Clock ;
import java.time.Duration ;
import java.time.Instant ;
import java.util.ArrayList ;
import java.util.Collection ;
import java.util.Collections ;
import java.util.HashSet ;
import java.util.List ;
import java.util.Map ;
import java.util.Optional ;
import java.util.Set ;
import java.util.stream.Collectors ;

import javax.annotation.PostConstruct ;
import javax.inject.Inject ;
import javax.servlet.http.HttpServletRequest ;
import javax.servlet.http.HttpServletResponse ;

import org.csap.helpers.CSAP ;
import org.csap.integations.CsapSecurityConfiguration ;
import org.csap.security.config.CsapSecurityProvider ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.beans.factory.annotation.Autowired ;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression ;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty ;
import org.springframework.boot.autoconfigure.security.oauth2.client.OAuth2ClientProperties ;
import org.springframework.boot.autoconfigure.security.oauth2.client.OAuth2ClientProperties.Provider ;
import org.springframework.boot.autoconfigure.security.oauth2.client.OAuth2ClientProperties.Registration ;
import org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration ;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration ;
import org.springframework.context.annotation.Bean ;
import org.springframework.context.annotation.ComponentScan ;
import org.springframework.context.annotation.Configuration ;
import org.springframework.context.annotation.Import ;
import org.springframework.core.convert.converter.Converter ;
import org.springframework.http.HttpEntity ;
import org.springframework.http.HttpHeaders ;
import org.springframework.http.MediaType ;
import org.springframework.http.ResponseEntity ;
import org.springframework.http.converter.FormHttpMessageConverter ;
import org.springframework.security.authentication.AbstractAuthenticationToken ;
import org.springframework.security.core.Authentication ;
import org.springframework.security.core.GrantedAuthority ;
import org.springframework.security.core.authority.SimpleGrantedAuthority ;
import org.springframework.security.core.context.SecurityContextHolder ;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient ;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService ;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken ;
import org.springframework.security.oauth2.client.http.OAuth2ErrorResponseErrorHandler ;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository ;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService ;
import org.springframework.security.oauth2.core.OAuth2AccessToken ;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse ;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames ;
import org.springframework.security.oauth2.core.http.converter.OAuth2AccessTokenResponseHttpMessageConverter ;
import org.springframework.security.oauth2.core.oidc.OidcIdToken ;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo ;
import org.springframework.security.oauth2.core.oidc.user.OidcUser ;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority ;
import org.springframework.security.oauth2.core.user.OAuth2UserAuthority ;
import org.springframework.security.oauth2.jwt.Jwt ;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter ;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler ;
import org.springframework.util.LinkedMultiValueMap ;
import org.springframework.util.MultiValueMap ;
import org.springframework.web.client.RestTemplate ;
import org.springframework.web.util.UriComponentsBuilder ;

import com.fasterxml.jackson.databind.JsonNode ;
import com.fasterxml.jackson.databind.ObjectMapper ;

@Configuration

@ConditionalOnProperty ( name = CsapSecurityProvider.TYPE , havingValue = CsapSecurityProvider.OAUTH2 )
@ConditionalOnExpression ( "${" + CsapSecurityConfiguration.PROPERTIES_ENABLED + "}" )

// alternate:
// @ConditionalOnExpression ( "${" + CsapSecurityConfiguration.PROPERTIES_ENABLED
// + "} and '${" + CsapSecurityProvider.TYPE + "}' == '" + CsapSecurityProvider.OAUTH2 + "'" )

@Import ( {
		OAuth2ClientAutoConfiguration.class,
		OAuth2ResourceServerAutoConfiguration.class
} )

@ComponentScan

// @Order ( 101 ) // oauth overrides direct security

public class CsapOauth2SecurityConfiguration {

	private static final String ROLE = "ROLE_" ;

	Logger logger = LoggerFactory.getLogger( getClass( ) ) ;

	// https://www.keycloak.org/docs/latest/securing_apps/index.html

	public static final String CSAP_VIEW = "ViewRole" ;
	public static final String AUTHENTICATED = "AUTHENTICATED" ;

	public static final String ADMIN = "admin" ;

	public static final String USER = "user" ;

	boolean enabled = false ;
	String oathUserTokenName = "not-specified" ;
	String oathServiceClaimName = "not-specified" ;
	String oathClientServiceName = "not-specified" ;

	@Inject
	ObjectMapper jsonMapper ;

	@Inject
	OAuth2ClientProperties oathProps ;

	@PostConstruct
	public void showConfiguration ( ) {

		StringBuilder builder = buildConfigurationMessage( ) ;

		logger.debug( builder.toString( ) ) ;

	}

	// @Component
	// @Order(Ordered.HIGHEST_PRECEDENCE)
	// public class SimpleCorsFilter implements Filter {
	//
	// public SimpleCorsFilter() {
	// }
	//
	// @Override
	// public void doFilter(ServletRequest req, ServletResponse res, FilterChain
	// chain) throws IOException, ServletException {
	// HttpServletResponse response = (HttpServletResponse) res;
	// HttpServletRequest request = (HttpServletRequest) req;
	// response.setHeader("Access-Control-Allow-Origin", "*");
	// response.setHeader("Access-Control-Allow-Methods", "POST, GET, OPTIONS,
	// DELETE");
	// response.setHeader("Access-Control-Max-Age", "3600");
	// response.setHeader("Access-Control-Allow-Headers", "x-requested-with,
	// authorization");
	//
	// if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
	// response.setStatus(HttpServletResponse.SC_OK);
	// } else {
	// chain.doFilter(req, res);
	// }
	// }
	//
	// @Override
	// public void init(FilterConfig filterConfig) {
	// }
	//
	// @Override
	// public void destroy() {
	// }
	// }

	public StringBuilder buildConfigurationMessage ( ) {

		StringBuilder builder = new StringBuilder( ) ;

		builder.append( CSAP.padLine( "user authorities token" ) + getOathUserTokenName( )
				+ CSAP.padLine( "oath service claim" ) + getOathServiceClaimName( )
				+ CSAP.padLine( "oath service client name" ) + getOathClientServiceName( ) ) ;

		String regInfo = oathProps.getRegistration( ).keySet( ).stream( )
				.map( key -> {

					Registration registration = oathProps.getRegistration( ).get( key ) ;
					StringBuilder p = new StringBuilder( "\n" + CSAP.padLine( "Registration" ) + key ) ;
					p.append( CSAP.padLine( "Client ID" ) + registration.getClientId( ) ) ;
					p.append( CSAP.padLine( "Client Name" ) + registration.getClientName( ) ) ;
					p.append( CSAP.padLine( "Grant Type" ) + registration.getAuthorizationGrantType( ) ) ;

					if ( registration.getScope( ) != null ) {

						p.append( CSAP.padLine( "Scopes" ) + registration.getScope( ).toString( ) ) ;

					}

					return p.toString( ) ;

				} )
				.collect( Collectors.joining( "\n" ) ) ;
		builder.append( regInfo ) ;

		String propInfo = oathProps.getProvider( ).keySet( ).stream( )
				.map( key -> {

					Provider provider = oathProps.getProvider( ).get( key ) ;
					StringBuilder p = new StringBuilder( "\n" + CSAP.padLine( "Provider" ) + key ) ;
					p.append( CSAP.padLine( "Auth uri" ) + provider.getAuthorizationUri( ) ) ;
					p.append( CSAP.padLine( "Issuer uri" ) + provider.getIssuerUri( ) ) ;
					return p.toString( ) ;

				} )
				.collect( Collectors.joining( "\n" ) ) ;

		builder.append( propInfo ) ;

		return builder ;

	}

//	@Inject
//	OpenIdLogoutHandler openIdLogoutHandler ;

	public Converter<Jwt, ? extends AbstractAuthenticationToken> oathServiceAuthoritiesMapper ( ) {

		JwtAuthenticationConverter jwtAuthenticationConverter = new JwtAuthenticationConverter( ) {
			protected Collection<GrantedAuthority> extractAuthorities ( Jwt jwt ) {

				Collection<GrantedAuthority> myAuthorities = null ;

				logger.info( "jwt: audience: {}, \n claims: {}", jwt.getAudience( ), jwt.getClaims( ) ) ;

				try {

					Map<String, Object> resourceClaims = jwt.getClaimAsMap( "resource_access" ) ;

					JsonNode csapServiceClaim = jsonMapper.readTree( resourceClaims.get( "csap-service" )
							.toString( ) ) ;
					logger.info( "csapServiceClaim: {}", CSAP.jsonPrint( csapServiceClaim ) ) ;

					myAuthorities = CSAP.jsonStream( csapServiceClaim.path( "roles" ) )
							.map( JsonNode::asText )
							.map( csapServiceRole -> {

								return new SimpleGrantedAuthority( ROLE + csapServiceRole ) ;

							} )
							.collect( Collectors.toList( ) ) ;

					SimpleGrantedAuthority csapAuthenticatedAuthority = new SimpleGrantedAuthority( ROLE
							+ AUTHENTICATED ) ;
					myAuthorities.add( csapAuthenticatedAuthority ) ;

				} catch ( Exception e ) {

					logger.warn( "Failed to to find service claim: {}", jwt.getClaims( ).toString( ) ) ;

				}

				// String types = resourceClaims.values().stream()
				// .map( Object::getClass )
				// .map( Class::getName )
				// .collect( Collectors.joining("\n\t") );
				// logger.info( "types: {}", types );

				return myAuthorities ;

			}
		} ;

		return jwtAuthenticationConverter ;

	}

	public boolean isEnabled ( ) {

		return enabled ;

	}

	public void setEnabled (
								boolean disabled ) {

		this.enabled = disabled ;

	}

	@Bean
	public OpenIdLogoutHandler getOpenIdLogoutHandler ( ) {

		return new OpenIdLogoutHandler( ) ;

	}

	public class OpenIdLogoutHandler extends SecurityContextLogoutHandler {
		Logger logger = LoggerFactory.getLogger( getClass( ) ) ;

		private RestTemplate restTemplate = new RestTemplate( ) ;

		@Override
		public void logout (
								HttpServletRequest request ,
								HttpServletResponse response ,
								Authentication authentication ) {

			super.logout( request, response, authentication ) ;

			if ( authentication != null && ( authentication instanceof OAuth2AuthenticationToken ) ) {

				logger.debug( "Authentication type: {}", authentication.getClass( ).getName( ) ) ;
				propagateLogoutToOauth2Provider( (OidcUser) authentication.getPrincipal( ) ) ;

			} else {

				logger.warn( "Ignoring logout attempt - authentication principle is null" ) ;

			}

		}

		private void propagateLogoutToOauth2Provider (
														OidcUser user ) {

			String endSessionEndpoint = user.getIssuer( ) + "/protocol/openid-connect/logout" ;

			UriComponentsBuilder builder = UriComponentsBuilder //
					.fromUriString( endSessionEndpoint ) //
					.queryParam( "id_token_hint", user.getIdToken( ).getTokenValue( ) ) ;

			logger.debug( "Logging out: {}", builder.toUriString( ) ) ;

			ResponseEntity<String> logoutResponse = restTemplate.getForEntity( builder.toUriString( ), String.class ) ;

			if ( logoutResponse.getStatusCode( ).is2xxSuccessful( ) ) {

				logger.debug( "Successfulley logged out in Keycloak" ) ;

			} else {

				logger.warn( "Could not propagate logout to Keycloak" ) ;

			}

		}
	}

	public String getOathUserTokenName ( ) {

		return oathUserTokenName ;

	}

	public void setOathUserTokenName (
										String claimName ) {

		this.oathUserTokenName = claimName ;

	}

	/**
	 * 
	 * default role mapping in spring security is USER_ROLE and is hardcoded
	 * 
	 * - couple of options to set roles, but note it ALWAYS involves custom mappings
	 * 
	 * 
	 * @Link https://docs.spring.io/spring-security/site/docs/current/reference/htmlsingle/#oauth2login-advanced-map-authorities-grantedauthoritiesmapper
	 * 
	 * 
	 * @see DefaultOAuth2UserService#loadUser(org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest)
	 * 
	 * @param authorities
	 * @return
	 */
	public Collection<? extends GrantedAuthority> oathUserAuthoritiesMapper (
																				Collection<? extends GrantedAuthority> authorities ) {

		Set<GrantedAuthority> mappedAuthorities = new HashSet<>( ) ;

		authorities.forEach( authority -> {

			logger.debug( "authority: {}", authority ) ;

			// SimpleGrantedAuthority grantedAuthority = new SimpleGrantedAuthority(
			// authority.getAuthority() ) ;
			// mappedAuthorities.add( grantedAuthority ) ;

			if ( OidcUserAuthority.class.isInstance( authority ) ) {

				OidcUserAuthority oidcUserAuthority = (OidcUserAuthority) authority ;

				OidcIdToken idToken = oidcUserAuthority.getIdToken( ) ;
				OidcUserInfo userInfo = oidcUserAuthority.getUserInfo( ) ;

				logger.debug( "claims: {}", userInfo.getClaims( ) ) ;
				// Map the claims found in idToken and/or userInfo
				// to one or more GrantedAuthority's and add it to mappedAuthorities
				Optional<String> csapClaims = userInfo.getClaims( ).keySet( ).stream( )
						.filter( claimKey -> claimKey.equals( getOathUserTokenName( ) ) )
						.findFirst( ) ;

				if ( csapClaims.isPresent( ) ) {

					logger.debug( "csapClaims: {}", csapClaims.get( ) ) ;

					try {

						logger.debug( "type: {} ", userInfo.getClaims( ).get( getOathUserTokenName( ) ).getClass( )
								.getName( ) ) ;
						ArrayList<String> claimRoles = (ArrayList<String>) userInfo.getClaims( ).get(
								getOathUserTokenName( ) ) ;
						claimRoles.stream( )
								.forEach( role -> {

									logger.debug( "role: {}", role ) ;
									mappedAuthorities.add( new SimpleGrantedAuthority( ROLE + role ) ) ;

								} ) ;

					} catch ( Exception e ) {

						logger.warn( "Failed to read roles: {}", CSAP.buildCsapStack( e ) ) ;

					}

				} else {

					logger.warn( "No csap claims found" ) ;

				}

				SimpleGrantedAuthority csapAuthenticatedAuthority = new SimpleGrantedAuthority( ROLE + AUTHENTICATED ) ;
				mappedAuthorities.add( csapAuthenticatedAuthority ) ;

			} else if ( OAuth2UserAuthority.class.isInstance( authority ) ) {

				OAuth2UserAuthority oauth2UserAuthority = (OAuth2UserAuthority) authority ;

				Map<String, Object> userAttributes = oauth2UserAuthority.getAttributes( ) ;
				logger.info( "userAttributes: {}", userAttributes ) ;

				// Map the attributes found in userAttributes
				// to one or more GrantedAuthority's and add it to mappedAuthorities

			}

		} ) ;

		return mappedAuthorities ;

	}

	@Autowired ( required = false )
	private OAuth2AuthorizedClientService authorizedClientService ;

	@Inject
	ClientRegistrationRepository clientRegistrationRepository ;

	public void addWebSso ( RestTemplate restTemplate ) {

		logger.debug( "adding oauth sso" ) ;

		restTemplate.setInterceptors(
				Collections.singletonList( (
												request ,
												body ,
												execution ) -> {

					request
							.getHeaders( )
							.add( "Authorization", getAuthorizationHeader( ) ) ;

					return execution.execute( request, body ) ;

				} ) ) ;

	}

	public String getAuthorizationHeader ( ) {

		return "Bearer " + getToken( ).getTokenValue( ) ;

	}

	private OAuth2AccessToken getToken ( ) {

		Authentication authentication = SecurityContextHolder
				.getContext( )
				.getAuthentication( ) ;

		OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) authentication ;
		OAuth2AuthorizedClient authorizedClient = this.authorizedClientService.loadAuthorizedClient(
				oauthToken.getAuthorizedClientRegistrationId( ),
				oauthToken.getName( ) ) ;

		OAuth2AccessToken accessToken = authorizedClient.getAccessToken( ) ;

		if ( shouldRefresh( authorizedClient ) ) {

			try {

				OAuth2AuthorizedClient updatedClient = refreshOAuth2AccessToken( authorizedClient, oauthToken ) ;
				accessToken = updatedClient.getAccessToken( ) ;

			} catch ( Exception ex ) {

				logger.error( "refreshOAuth2AccessToken error, Exception: {}", CSAP.buildCsapStack( ex ) ) ;

			}

		}

		return accessToken ;

	}

	public String getOathServiceClaimName ( ) {

		return oathServiceClaimName ;

	}

	public void setOathServiceClaimName ( String oathServiceClaimName ) {

		this.oathServiceClaimName = oathServiceClaimName ;

	}

	public String getOathClientServiceName ( ) {

		return oathClientServiceName ;

	}

	public void setOathClientServiceName ( String oathClientServiceName ) {

		this.oathClientServiceName = oathClientServiceName ;

	}

	private OAuth2AuthorizedClient refreshOAuth2AccessToken (
																OAuth2AuthorizedClient authorizedClient ,
																OAuth2AuthenticationToken oauthToken ) {

		if ( authorizedClientService == null ) {

			return authorizedClient ;

		}

		RestTemplate restTemplate = new RestTemplate( List.of(
				new FormHttpMessageConverter( ),
				new OAuth2AccessTokenResponseHttpMessageConverter( ) ) ) ;

		restTemplate.setErrorHandler( new OAuth2ErrorResponseErrorHandler( ) ) ;
		HttpHeaders headers = new HttpHeaders( ) ;
		String tokenUri = authorizedClient.getClientRegistration( ).getProviderDetails( ).getTokenUri( ) ;

		headers.setBasicAuth( authorizedClient.getClientRegistration( ).getClientId( ),
				authorizedClient.getClientRegistration( ).getClientSecret( ) ) ;

		headers.add( HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE ) ;
		headers.setContentType( MediaType.APPLICATION_FORM_URLENCODED ) ;
		MultiValueMap<String, String> map = new LinkedMultiValueMap<>( ) ;
		map.add( OAuth2ParameterNames.GRANT_TYPE, OAuth2ParameterNames.REFRESH_TOKEN ) ;
		map.add( OAuth2ParameterNames.REFRESH_TOKEN, authorizedClient.getRefreshToken( ).getTokenValue( ) ) ;
		HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>( map, headers ) ;
		OAuth2AuthorizedClient refreshedClient = null ;

		try {

			logger.debug( "refreshOAuth2AccessToken, preparing to call {} to get AccessTokenResponse.", tokenUri ) ;
			ResponseEntity<OAuth2AccessTokenResponse> response = restTemplate.postForEntity(
					URI.create( tokenUri ), request,
					OAuth2AccessTokenResponse.class ) ;
			OAuth2AccessTokenResponse tokenResponse = response.getBody( ) ;

			logger.debug( "refreshOAuth2AccessToken, tokenResponse={}", tokenResponse.toString( ) ) ;
			refreshedClient = new OAuth2AuthorizedClient( authorizedClient.getClientRegistration( ),
					authorizedClient.getPrincipalName( ), tokenResponse.getAccessToken( ), tokenResponse
							.getRefreshToken( ) ) ;
			authorizedClientService.saveAuthorizedClient( refreshedClient, oauthToken ) ;

		} catch ( Exception e ) {

			logger.warn( "Failed to refresh token: {}", CSAP.buildCsapStack( e ) ) ;

		}

		// } catch (OAuth2AuthorizationException ex) {
		// OAuth2Error oauth2Error = ex.getError();
		// throw new OAuth2AuthenticationException(oauth2Error, ex.toString(), ex);
		// } catch (RestClientException ex) {
		// OAuth2Error oauth2Error = new OAuth2Error(INVALID_TOKEN_RESPONSE_ERROR_CODE,
		// "An error occurred while attempting to retrieve the OAuth 2.0 Access Token
		// Response: " + ex.getMessage(), null);
		// throw new OAuth2AuthorizationException(oauth2Error, ex);
		// }
		return refreshedClient ;

	}

	private boolean shouldRefresh ( OAuth2AuthorizedClient authorizedClient ) {

		if ( authorizedClientService == null || authorizedClient == null ) {

			logger.debug( "Client not refreshable" ) ;
			return false ;

		}

		Instant now = Clock.systemUTC( ).instant( ) ;
		Instant expiresAt = authorizedClient.getAccessToken( ).getExpiresAt( ) ;
		logger.debug( "shouldRefresh, now: {} expiresAt: {}", now, expiresAt ) ;

		// Default to one minute. Make a configurable parameter.
		if ( now.isAfter( expiresAt.minus( Duration.ofMinutes( 1 ) ) ) ) {

			logger.debug( "shouldRefresh returning true" ) ;
			return true ;

		}

		logger.debug( "shouldRefresh returning false" ) ;
		return false ;

	}

}
