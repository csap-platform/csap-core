package org.sample.locator ;

import org.csap.integations.CsapServiceLocator ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.beans.factory.annotation.Autowired ;
import org.springframework.boot.context.properties.ConfigurationProperties ;
import org.springframework.context.annotation.Configuration ;
import org.springframework.retry.annotation.Retryable ;
import org.springframework.web.bind.annotation.RequestMapping ;
import org.springframework.web.bind.annotation.RestController ;
import org.springframework.web.client.RestClientException ;
import org.springframework.web.client.RestTemplate ;

/**
 * http://stackoverflow.com/questions/27468697/spring-cloud-with-resttemplate-ribbon-eureka-retry-when-server-not-available
 * 
 * @author pnightin
 *
 */
@Configuration
@RestController
@ConfigurationProperties ( prefix = "my-service-configuration.locator" )
public class HelloClient {

	final Logger logger = LoggerFactory.getLogger( HelloClient.class ) ;

	@Autowired
	CsapServiceLocator csapLocator ;

	RestTemplate restTemplate = new RestTemplate( ) ; // lots of options for pools, etc

	@Retryable ( maxAttempts = 2 )
	@RequestMapping ( "/helloRemoteRandom" )
	public String helloRandom ( ) {

		String serviceUrl = csapLocator.getRandomInstance( csapLocator.getServiceUrls( getHelloProvider( ) ) ) ;
		logger.info( "Using binding: {} ", serviceUrl ) ;

		String result = "none" ;

		try {

			result = restTemplate.getForObject( serviceUrl + getHelloApi( ), String.class ) ;

		} catch ( RestClientException e ) {

			logger.warn( "Failed invoking url, will retry" ) ;
			throw e ;

		}

		return "Response: " + result ;

	}

	@Retryable ( maxAttempts = 2 )
	@RequestMapping ( "/helloRoundRobin" )
	public String helloRoundRobin ( ) {

		String serviceUrl = csapLocator.getRoundRobinInstance( csapLocator.getServiceUrls( getHelloProvider( ) ) ) ;
		logger.info( "Using binding: {} ", serviceUrl ) ;

		String result = "none" ;

		try {

			result = restTemplate.getForObject( serviceUrl + getHelloApi( ), String.class ) ;

		} catch ( RestClientException e ) {

			logger.warn( "Failed invoking url, will retry" ) ;
			throw e ;

		}

		return "Response: " + result ;

	}

	@Retryable ( maxAttempts = 5 )
	@RequestMapping ( value = {
			"/hello/lowCpu", "/hello/lowLoad"
	} )
	public String helloLowResource ( ) {

		String serviceUrl = csapLocator.getLowestCpuInstance( getHelloProvider( ) ) ;
		logger.info( "Using binding: {} ", serviceUrl ) ;

		String result = "none" ;

		try {

			result = restTemplate.getForObject( serviceUrl + getHelloApi( ), String.class ) ;

		} catch ( RestClientException e ) {

			logger.warn( "Failed invoking url, will retry" ) ;
			throw e ;

		}

		return "Response: " + result ;

	}

	public String getHelloApi ( ) {

		return helloApi ;

	}

	public void setHelloApi ( String helloApi ) {

		this.helloApi = helloApi ;

	}

	public String getHelloProvider ( ) {

		return helloProvider ;

	}

	public void setHelloProvider ( String helloProvider ) {

		this.helloProvider = helloProvider ;

	}

	public String helloApi = "" ;
	public String helloProvider = "" ;

}
