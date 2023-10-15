package org.csap.security ;

import java.util.Arrays ;

import org.csap.helpers.CSAP ;
import org.csap.integations.micrometer.CsapMeterUtilities ;
import org.csap.security.config.CsapSecuritySettings ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.beans.factory.annotation.Autowired ;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty ;
import org.springframework.boot.context.properties.ConfigurationProperties ;
import org.springframework.boot.web.servlet.FilterRegistrationBean ;
import org.springframework.context.annotation.Bean ;
import org.springframework.context.annotation.Configuration ;

@Configuration ( "CsapSecurityRestFilter" )
@ConditionalOnProperty ( "csap.security.rest-api-filter.enabled" )
@ConfigurationProperties ( prefix = "csap.security.rest-api-filter" )
public class CsapSecurityRestFilter {

	final Logger logger = LoggerFactory.getLogger( this.getClass( ) ) ;

	private String[] urls ;

	private String group ;

	private String token ;

	private int cacheSeconds = 60 ;

	@Autowired
	private CsapSecuritySettings settings ;

	@Bean
	public FilterRegistrationBean<SpringAuthCachingFilter> restApiFilterRegistration (
																						CsapMeterUtilities csapMicroUtilites ) {

		logger.debug( "group: {} , cacheSeconds: {}", getGroup( ), getCacheSeconds( ) ) ;
		FilterRegistrationBean<SpringAuthCachingFilter> restSecurityFilterRegistration = null ;

		try {

			restFilter = new SpringAuthCachingFilter( csapMicroUtilites, token, group, cacheSeconds ) ;
			restSecurityFilterRegistration = new FilterRegistrationBean<SpringAuthCachingFilter>(
					restFilter ) ;
			restSecurityFilterRegistration.addUrlPatterns( urls ) ;

		} catch ( Exception e ) {

			logger.warn( "Failed creating rest authentiction filter: {}", CSAP.buildCsapStack( e ) ) ;

		}

		return restSecurityFilterRegistration ;

	}

	SpringAuthCachingFilter restFilter = null ;

	public void setLocalCredentials ( String user , String pass ) {

		logger.debug( "***** Adding local credential: {}", user ) ;
		restFilter.setLocalCredentials( user, pass ) ;

	}

	public String toString ( ) {

		StringBuilder builder = new StringBuilder( ) ;
		builder.append( "\n csap.security.rest-api-filter: " ) ;
		builder.append( "\n\t urls inspected for user and password params: " + Arrays.asList( urls ).toString( ) ) ;
		builder.append( "\n\t auth token: " + getToken( ) ) ;
		builder.append( "\n\t group used to verify access: " + group ) ;
		builder.append( "\n\t seconds to cache before re-athentication: " + cacheSeconds ) ;
		builder.append( "\n " ) ;

		return builder.toString( ) ;

	}

	public String[] getUrls ( ) {

		return urls ;

	}

	public void setUrls ( String[] urls ) {

		// logger.debug( "Injecting by reflection?" );
		this.urls = urls ;

	}

	public String getGroup ( ) {

		return group ;

	}

	public void setGroup ( String group ) {

		this.group = group ;
		if ( group.equals( "$CSAP_ADMIN_GROUP" ) )
			this.group = settings.getRoles( ).getAdminGroup( ) ;

	}

	public int getCacheSeconds ( ) {

		return cacheSeconds ;

	}

	public void setCacheSeconds ( int cacheSeconds ) {

		this.cacheSeconds = cacheSeconds ;

	}

	public String getToken ( ) {

		return token ;

	}

	public void setToken ( String token ) {

		this.token = token ;

	}

}
