
package org.csap.security.oath2 ;

import static org.springframework.security.oauth2.client.web.reactive.function.client.ServletOAuth2AuthorizedClientExchangeFilterFunction.clientRegistrationId ;

import javax.inject.Inject ;

import org.csap.security.config.CsapSecurityProvider ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty ;
import org.springframework.stereotype.Component ;
import org.springframework.web.reactive.function.client.WebClient ;

import com.fasterxml.jackson.databind.JsonNode ;
import com.fasterxml.jackson.databind.ObjectMapper ;

@Component
//@ConditionalOnBean(CsapSecuritySettings.class)
@ConditionalOnProperty ( name = CsapSecurityProvider.TYPE , havingValue = CsapSecurityProvider.OAUTH2 )

public class CsapWebClients {

	Logger logger = LoggerFactory.getLogger( getClass( ) ) ;

	@Inject
	CsapOauth2SecurityConfiguration securityConfig ;

	private final static String URI_WEBCLIENT = "/webclient" ;
	private final static String URI_HI = "/hi" ;
	public final static String URI_CLIENT_SELECTION_HI = URI_WEBCLIENT + URI_HI + "/client" ;
	public final static String URI_AUTO_SELECTION_HI = URI_WEBCLIENT + URI_HI + "/auto" ;

	private final WebClient webClientUser ;
	private final WebClient webClientService ;

	private final ObjectMapper jsonMapper ;

	public CsapWebClients ( WebClient webClientUser, WebClient webClientService, ObjectMapper jsonMapper ) {

		this.webClientUser = webClientUser ;
		this.webClientService = webClientService ;
		this.jsonMapper = jsonMapper ;

	}

	public JsonNode getContentUsingWebClientFromUserContext ( String targetUrl )
		throws Exception {

		logger.info( "Connection using client: {}", securityConfig.getOathClientServiceName( ) ) ;

		String body = this.webClientUser
				.get( )
				.uri( targetUrl )
				.attributes( clientRegistrationId( securityConfig.getOathClientServiceName( ) ) )
				.retrieve( )
				.bodyToMono( String.class )
				.block( ) ;

		return jsonMapper.readTree( body ) ;

	}

	public JsonNode getContentUsingWebClientFromAnonymousContext ( String targetUrl )
		throws Exception {

		logger.info( "Connection using client: {}", securityConfig.getOathClientServiceName( ) ) ;

		String body = this.webClientService
				.get( )
				.uri( targetUrl )
				.attributes( clientRegistrationId( securityConfig.getOathClientServiceName( ) ) )
				.retrieve( )
				.bodyToMono( String.class )
				.block( ) ;

		return jsonMapper.readTree( body ) ;

	}

}
