/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.csap.debug ;

import java.util.Arrays ;

import org.csap.helpers.CSAP ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty ;
import org.springframework.boot.context.properties.ConfigurationProperties ;
import org.springframework.boot.web.servlet.FilterRegistrationBean ;
import org.springframework.context.annotation.Bean ;
import org.springframework.context.annotation.Configuration ;

/**
 *
 * @author pnightin
 */
@Configuration ( CsapDebug.BEAN_NAME )
@ConditionalOnProperty ( prefix = CsapDebug.PROPERTIES_NAME , name = "enabled" )
@ConfigurationProperties ( prefix = CsapDebug.PROPERTIES_NAME )
public class CsapDebug {

	final static Logger logger = LoggerFactory.getLogger( CsapDebug.class ) ;

	public final static String BEAN_NAME = "CsapDebug" ;
	public final static String PROPERTIES_NAME = "csap.debug.show-web-params" ;

	private String[] urls = {
			"/add/some/urls/*"
	} ;
	private boolean includeHeaders = false ;

	public String toString ( ) {

		StringBuilder infoBuilder = new StringBuilder( ) ;
		infoBuilder.append( "\n " + CsapDebug.PROPERTIES_NAME ) ;
		infoBuilder.append( CSAP.padLine( "showHeaders" ) + isIncludeHeaders( ) ) ;
		infoBuilder.append( CSAP.padLine( "urls" ) ) ;

		for ( String url : getUrls( ) ) {

			infoBuilder.append( " " + url ) ;

		}

		infoBuilder.append( "\n" ) ;
		return infoBuilder.toString( ) ;

	}

	/**
	 * @return the monitorUrls
	 */
	public String[] getUrls ( ) {

		return urls ;

	}

	/**
	 * @param urls the monitorUrls to set
	 */
	public void setUrls ( String[] urls ) {

		this.urls = urls ;

	}

	@Bean
	public FilterRegistrationBean showParametersRegistration ( ) {

		logger.debug( "Collecting metrics for: {}", Arrays.asList( urls ).toString( ) ) ;
		FilterRegistrationBean showParametersFilter = new FilterRegistrationBean(
				new ShowParamsFilter( ) ) ;
		showParametersFilter.addUrlPatterns( urls ) ;

		showParametersFilter.addInitParameter( ShowParamsFilter.SHOW_HEADERS_PARAM,
				Boolean.toString( isIncludeHeaders( ) ) ) ;

		return showParametersFilter ;

	}

	/**
	 * @return the includeHeaders
	 */
	public boolean isIncludeHeaders ( ) {

		return includeHeaders ;

	}

	/**
	 * @param includeHeaders the includeHeaders to set
	 */
	public void setIncludeHeaders ( boolean includeHeaders ) {

		this.includeHeaders = includeHeaders ;

	}

}
