/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.csap.helpers ;

import java.util.ArrayList ;
import java.util.HashMap ;
import java.util.List ;
import java.util.Map ;

import javax.inject.Inject ;

import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.context.annotation.Bean ;
import org.springframework.context.annotation.DependsOn ;
import org.springframework.core.env.Environment ;
import org.springframework.http.ResponseEntity ;
import org.springframework.http.client.ClientHttpRequestFactory ;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory ;
import org.springframework.http.converter.FormHttpMessageConverter ;
import org.springframework.http.converter.HttpMessageConverter ;
import org.springframework.http.converter.StringHttpMessageConverter ;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter ;
import org.springframework.util.LinkedMultiValueMap ;
import org.springframework.util.MultiValueMap ;
import org.springframework.web.client.RestTemplate ;

import com.fasterxml.jackson.databind.JsonNode ;
import com.fasterxml.jackson.databind.ObjectMapper ;

//@Configuration
public class CsapShellConfiguration {

	final static Logger logger = LoggerFactory.getLogger( CsapShellConfiguration.class ) ;

	@Inject
	Environment env ;

	ObjectMapper jacksonMapper = new ObjectMapper( ) ;

	public void execute ( ) {

		var api = env.getProperty( "api" ) ;
		var params = env.getProperty( "params" ) ;
		var textResponse = env.getProperty( "textResponse" ) ;

		var isTextResponse = textResponse != null ;
		var jpath = env.getProperty( "jpath" ) ;

		var isParseResponse = false ;

		if ( env.getProperty( "parse" ) != null
				|| env.getProperty( "jpath" ) != null ) {

			isParseResponse = true ;

		}

		String apiUrl = env.getProperty( "lab" ) + "/api/" + api ;

		StringBuilder info = new StringBuilder( ) ;
		info.append( CSAP.padLine( "api" ) + api ) ;
		info.append( CSAP.padLine( "apiUrl" ) + apiUrl ) ;
		info.append( CSAP.padLine( "api timeout" ) + env.getProperty( "timeOutSeconds", Integer.class ) ) ;
		info.append( CSAP.padLine( "params" ) + params ) ;
		info.append( CSAP.padLine( "isParseResponse" ) + isParseResponse ) ;

		StringBuilder result = new StringBuilder( ) ;
		String textResult = "" ;

		try {

			JsonNode apiResponseInJsonFormat = null ;

			if ( isTextResponse && params.equals( "none" ) ) {

				Map<String, String> urlVariables = new HashMap<>( ) ;

				if ( apiUrl.contains( "{" ) ) {

					logger.info( "apiUrl contains get params needing encoding" ) ;

					String[] getParams = apiUrl.split( "}" ) ;

					for ( String currParam : getParams ) {

						String strippedParam = currParam.substring( currParam.indexOf( "{" ) + 1 ) ;
						logger.info( "strippedParam: " + strippedParam ) ;
						urlVariables.put( strippedParam, strippedParam ) ;

					}

				}

				info.append( CSAP.padLine( "method" ) + "http GET" + urlVariables ) ;
				logger.info( CsapApplication.header( info.toString( ) ) ) ;

				ResponseEntity<String> response = getRestTemplate( ).getForEntity( apiUrl,
						String.class, urlVariables ) ;

				logger.info( "Results from post: " + apiUrl + ""
						+ ", HttpResponse: \n" + response.getStatusCode( ) + " body: \n" + response.getBody( ) ) ;

				textResult = response.getBody( ) ;

			} else if ( isTextResponse && ! params.equals( "none" ) ) {

				MultiValueMap<String, String> formVariables = buildParams( params ) ;

				info.append( CSAP.padLine( "method" ) + "http POST" + formVariables ) ;
				logger.info( CsapApplication.header( info.toString( ) ) ) ;

				textResult = getRestTemplate( ).postForObject( apiUrl, formVariables, String.class ) ;

			} else if ( params.equals( "none" ) ) {

				info.append( CSAP.padLine( "method" ) + "http GET" ) ;
				logger.info( CsapApplication.header( info.toString( ) ) ) ;

//				logger.info( "GET to api: " + api + " with lab: " + env.getProperty( "lab" ) + " script: " + isParseResponse ) ;
				apiResponseInJsonFormat = getRestTemplate( ).getForObject( apiUrl, JsonNode.class ) ;

			} else {

				MultiValueMap<String, String> formVariables = buildParams( params ) ;

				info.append( CSAP.padLine( "method" ) + "http POST" + formVariables ) ;
				logger.info( CsapApplication.header( info.toString( ) ) ) ;

				apiResponseInJsonFormat = getRestTemplate( ).postForObject( apiUrl, formVariables, JsonNode.class ) ;

			}

			if ( isTextResponse ) {

				result.append( textResult ) ;

			} else {

				if ( isParseResponse ) {

					var parsedResponse = apiResponseInJsonFormat ;

					if ( jpath != null ) {

						// result.append(apiResponseInJsonFormat.at(jpath).asText());
						parsedResponse = apiResponseInJsonFormat.at( jpath ) ;

					}

					logger.info( CsapApplication.header( "response type: {}, contents: \n {}" ),
							parsedResponse.getClass( ),
							parsedResponse ) ;

					if ( parsedResponse.isArray( ) ) {

						CSAP.jsonStream( parsedResponse ).forEach( item -> result.append( item.asText( ) + " " ) ) ;

					} else if ( parsedResponse.isTextual( ) ) {

						result.append( parsedResponse.asText( ) ) ;

					} else if ( parsedResponse.isInt( ) ) {

						result.append( parsedResponse.asInt( ) ) ;

					} else if ( parsedResponse.isDouble( ) ) {

						result.append( parsedResponse.asDouble( ) ) ;

					} else {

						// result.append( resultObject.at("/0/port").asText() ) ;
						result.append( "* Script handling not supported. Raw results will be shown.\n" ) ;
						result.append( "* Api: " + apiUrl + "\n" ) ;
						result.append( jacksonMapper.writerWithDefaultPrettyPrinter( ).writeValueAsString(
								parsedResponse ) ) ;

					}

				} else {

//				logger.info( "Raw output for command: " + apiUrl + "\n" ) ;
					result.append( jacksonMapper.writerWithDefaultPrettyPrinter( ).writeValueAsString(
							apiResponseInJsonFormat ) ) ;

				}

			}

		} catch ( Exception e ) {

			logger.error( "Failed command {}", CSAP.buildCsapStack( e ) ) ;

			// resultObject = jacksonMapper.createObjectNode() ;
			// resultObject.put("url", apiUrl ) ;
			// resultObject.put("Failed Command", e.getMessage() ) ;
			// ArrayNode stackNode = resultObject.putArray("Stack Trace") ;

			result.append( "Failed invoking: " + apiUrl + "\n" ) ;
			result.append( "error: " + e.getMessage( ) + "\n" ) ;

			result.append( CSAP.buildCsapStack( e ) ) ;

		}

		System.out.println( result ) ;
		logger.info( result.toString( ) ) ;

	}

	private MultiValueMap<String, String> buildParams ( String params ) {

		MultiValueMap<String, String> formVariables = new LinkedMultiValueMap<String, String>( ) ;

		if ( params.trim( ).length( ) != 0 ) {

			String[] paramArray = params.split( "," ) ;

			for ( String param : paramArray ) {

				int index = param.indexOf( "=" ) ;

				if ( index == -1 ) {

					System.out.println( "Params must be in form of name1=value1,name2=value2, ... , but found: "
							+ params ) ;
					System.exit( 99 ) ;
					;

				}

				String name = param.substring( 0, index ) ;
				String val = param.substring( index + 1 ) ;
				formVariables.add( name, val ) ;

			}

		}

		return formVariables ;

	}

	/**
	 * 
	 * restService gets injected throughout code base via @Inject or @Autowired
	 * 
	 * @return
	 */
	@Bean ( name = "agentRestService" )
	public RestTemplate getRestTemplate ( ) {

		RestTemplate restTemplate = new RestTemplate( getHttpFactory( ) ) ;

		// This is stricly JSON client
		List<HttpMessageConverter<?>> messageConverters = new ArrayList<HttpMessageConverter<?>>( ) ;

		messageConverters.add( new StringHttpMessageConverter( ) ) ;
		// Used for posts
		messageConverters.add( new FormHttpMessageConverter( ) ) ;

		// used for parsing json response
		messageConverters.add( new MappingJackson2HttpMessageConverter( ) ) ;
		restTemplate.setMessageConverters( messageConverters ) ;

		return restTemplate ;

	}

	/**
	 * Default timeouts block indefinitely - rarely/never acceptable in production
	 * systems
	 * 
	 * @return
	 */
	@Bean ( name = "httpFactory" )
	@DependsOn ( {
			"environment"
	} )
	public ClientHttpRequestFactory getHttpFactory ( ) {

		int timeout = env.getProperty( "timeOutSeconds", Integer.class ) * 1000 ;
		HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory( ) ;
		factory.setReadTimeout( timeout ) ;
		factory.setConnectTimeout( timeout ) ;
		return factory ;

	}

}
