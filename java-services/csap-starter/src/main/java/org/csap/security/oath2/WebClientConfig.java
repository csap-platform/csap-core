
package org.csap.security.oath2 ;

import javax.servlet.http.HttpServletRequest ;
import javax.servlet.http.HttpServletResponse ;

import org.csap.security.config.CsapSecurityProvider ;
import org.csap.security.config.CsapSecuritySettings ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty ;
import org.springframework.context.annotation.Bean ;
import org.springframework.context.annotation.Configuration ;
import org.springframework.security.core.Authentication ;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient ;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository ;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository ;
import org.springframework.security.oauth2.client.web.reactive.function.client.ServletOAuth2AuthorizedClientExchangeFilterFunction ;
import org.springframework.web.reactive.function.client.WebClient ;

@Configuration
@ConditionalOnProperty ( name = CsapSecurityProvider.TYPE , havingValue = CsapSecurityProvider.OAUTH2 )
public class WebClientConfig {

	Logger logger = LoggerFactory.getLogger( getClass( ) ) ;

	@Bean
	WebClient webClientUser (
								ClientRegistrationRepository clientRegistrationRepository ,
								OAuth2AuthorizedClientRepository authorizedClientRepository ,
								CsapSecuritySettings securityConfig ) {

		logger.debug( "connecting using: {}", securityConfig.getProvider( ).getOauthClientServiceName( ) ) ;

		ServletOAuth2AuthorizedClientExchangeFilterFunction oauth2 = new ServletOAuth2AuthorizedClientExchangeFilterFunction(
				clientRegistrationRepository, authorizedClientRepository ) ;

		oauth2.setDefaultOAuth2AuthorizedClient( true ) ;
		oauth2.setDefaultClientRegistrationId( securityConfig.getProvider( ).getOauthClientServiceName( ) ) ;

		return WebClient.builder( )
				.apply( oauth2.oauth2Configuration( ) )
				.build( ) ;

	}

	// https://stackoverflow.com/questions/55308918/spring-security-5-calling-oauth2-secured-api-in-application-runner-results-in-il/55454870#55454870
	@Bean
	WebClient webClientService (
									ClientRegistrationRepository clientRegistrationRepository ,
									CsapSecuritySettings securityConfig ) {

		var oauth2Filter = new ServletOAuth2AuthorizedClientExchangeFilterFunction( clientRegistrationRepository,
				new OAuth2AuthorizedClientRepository( ) {
					@Override
					public <T extends OAuth2AuthorizedClient> T loadAuthorizedClient (
																						String s ,
																						Authentication authentication ,
																						HttpServletRequest httpServletRequest ) {

						return null ;

					}

					@Override
					public void saveAuthorizedClient (
														OAuth2AuthorizedClient oAuth2AuthorizedClient ,
														Authentication authentication ,
														HttpServletRequest httpServletRequest ,
														HttpServletResponse httpServletResponse ) {

					}

					@Override
					public void removeAuthorizedClient (
															String s ,
															Authentication authentication ,
															HttpServletRequest httpServletRequest ,
															HttpServletResponse httpServletResponse ) {

					}
				} ) ;

		// oauth2.setDefaultOAuth2AuthorizedClient( true ) ;
		oauth2Filter.setDefaultClientRegistrationId( securityConfig.getProvider( ).getOauthClientServiceName( ) ) ;

		return WebClient.builder( )
				.apply( oauth2Filter.oauth2Configuration( ) )
				.build( ) ;

	}
}
