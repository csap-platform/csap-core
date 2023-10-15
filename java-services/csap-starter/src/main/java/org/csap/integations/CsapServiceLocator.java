package org.csap.integations ;

import java.util.Random ;
import java.util.concurrent.atomic.AtomicInteger ;

import org.csap.helpers.CSAP ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.beans.factory.annotation.Autowired ;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty ;
import org.springframework.boot.context.properties.ConfigurationProperties ;
import org.springframework.cache.CacheManager ;
import org.springframework.cache.annotation.Cacheable ;
import org.springframework.context.annotation.Configuration ;
import org.springframework.core.env.Environment ;
import org.springframework.stereotype.Service ;
import org.springframework.web.client.RestTemplate ;

import com.fasterxml.jackson.databind.node.ArrayNode ;
import com.fasterxml.jackson.databind.node.ObjectNode ;

@Configuration ( "CsapServiceLocator" )
@ConditionalOnProperty ( prefix = "csap.locator" , name = "enabled" )
@ConfigurationProperties ( prefix = "csap.locator" )
@Service
public class CsapServiceLocator {

	final Logger logger = LoggerFactory.getLogger( CsapServiceLocator.class ) ;

	protected final static String ROUND_ROBIN_CACHE = "roundRobinUrlCache" ;
	protected final static String LOW_RESOURCE_CACHE = "lowResourceUrlCache" ;

	public CsapServiceLocator ( Environment springEnv ) {

		locatorInfo.append( "\n csap.locator: " ) ;

		try {

			String ehcacheLocation = springEnv.getProperty( "spring.cache.jcache.config" ) ;
			// new ClassPathResource( ehcacheLocation )).getURI()
			locatorInfo.append( CSAP.padLine( "Cache Policies" ) + "spring.cache.jcache.config - " + ehcacheLocation ) ;

		} catch ( Exception e ) {

			locatorInfo.append( CSAP.padLine( "Cache Policies" ) + "ERROR - NOT FOUND" ) ;
			logger.error( "Cache Policies: ERROR - NOT FOUND", e ) ;

		}

		locatorInfo.append( CSAP.padLine( "ROUND_ROBIN_CACHE  id" ) + ROUND_ROBIN_CACHE ) ;
		locatorInfo.append( CSAP.padLine( "LOW_RESOURCE_CACHE id" ) + LOW_RESOURCE_CACHE ) ;

		locatorInfo.append( "\n " ) ;
		logger.debug( locatorInfo.toString( ) ) ;

	}

	StringBuilder locatorInfo = new StringBuilder( ) ;

	public String toString ( ) {

		return locatorInfo.toString( ) ;

	}

	@Autowired
	public CacheManager cacheManager ;
	RestTemplate restTemplate = new RestTemplate( ) ; // lots of options for
														// pools, etc

	// @Cacheable: must be on a public method invoked externally; or you must
	// you aspectj

	@Cacheable ( ROUND_ROBIN_CACHE )
	public ArrayNode getServiceUrls ( String serviceName ) {

		String locatorUrl = getActiveUrlsLocator( ) + serviceName ;

		logger.info(
				"\n\n ********** Service lookup: {} ,  using: {}\n\t ** Will be cached based on ehcache.xml settings. (default 30s) \n",
				locatorUrl,
				cacheManager.getClass( ).getName( ) ) ;

		ArrayNode servicesArray = restTemplate
				.getForObject( locatorUrl, ArrayNode.class ) ;
		logger.debug( "Urls available: {}", servicesArray.toString( ) ) ;

		// return "http://csap-dev02.yourcompany.com:8301/hello" ;
		return servicesArray ;

	}

	public String getRandomInstance ( ArrayNode serviceUrls ) {

		return serviceUrls.get( randomService.nextInt( serviceUrls.size( ) ) ).asText( ) ;

	}

	AtomicInteger roundRobinIndex = new AtomicInteger( ) ;

	public String getRoundRobinInstance ( ArrayNode serviceUrls ) {

		String url = serviceUrls.get( roundRobinIndex.getAndIncrement( ) ).asText( ) ;

		// reset to 0 at the end of list
		roundRobinIndex.compareAndSet( serviceUrls.size( ), 0 ) ;
		return url ;

	}

	@Cacheable ( LOW_RESOURCE_CACHE )
	public String getLowestCpuInstance ( String serviceName ) {

		String locatorUrl = getLowResourcesUrlLocator( ) + serviceName ;

		logger.info(
				"\n\n ********** Service lookup: {} ,  caching using: {}\n\t ** Will be cached based on ehcache.xml settings(default 30s) \n",
				locatorUrl,
				cacheManager.getClass( ).getName( ) ) ;

		ObjectNode servicesNode = restTemplate
				.getForObject( locatorUrl, ObjectNode.class ) ;

		logger.debug( "Urls available: {}", servicesNode.toString( ) ) ;
		return servicesNode.get( getLowResource( ) ).asText( ) ;

	}

	Random randomService = new Random( ) ;

	public String getActiveUrlsLocator ( ) {

		return activeUrlsLocator ;

	}

	public void setActiveUrlsLocator ( String activeUrlsLocator ) {

		this.activeUrlsLocator = activeUrlsLocator ;

	}

	public String getLowResourcesUrlLocator ( ) {

		return lowResourcesUrlLocator ;

	}

	public void setLowResourcesUrlLocator ( String lowResourcesUrlLocator ) {

		this.lowResourcesUrlLocator = lowResourcesUrlLocator ;

	}

	private String activeUrlsLocator ;
	private String lowResourcesUrlLocator ;
	private String lowResource = "lowCpu" ;

	public String getLowResource ( ) {

		return lowResource ;

	}

	public void setLowResource ( String lowResource ) {

		this.lowResource = lowResource ;

	}

}
