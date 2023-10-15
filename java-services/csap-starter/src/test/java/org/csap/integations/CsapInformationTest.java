package org.csap.integations ;

import static org.assertj.core.api.Assertions.assertThat ;

import java.time.LocalDateTime ;
import java.time.format.DateTimeFormatter ;
import java.util.concurrent.TimeUnit ;

import javax.inject.Inject ;

import org.csap.CsapBootApplication ;
import org.csap.helpers.CSAP ;
import org.csap.helpers.CsapApplication ;
import org.junit.jupiter.api.Test ;
import org.junit.jupiter.api.TestInstance ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.beans.factory.annotation.Autowired ;
import org.springframework.boot.test.context.SpringBootTest ;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment ;
import org.springframework.cache.annotation.Cacheable ;
import org.springframework.cache.jcache.JCacheCacheManager ;
import org.springframework.context.ApplicationContext ;
import org.springframework.mock.web.MockHttpServletRequest ;
import org.springframework.scheduling.annotation.EnableAsync ;
import org.springframework.test.annotation.DirtiesContext ;
import org.springframework.test.context.ActiveProfiles ;
import org.springframework.web.bind.annotation.GetMapping ;
import org.springframework.web.bind.annotation.RestController ;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer ;

import com.fasterxml.jackson.databind.ObjectMapper ;
import com.fasterxml.jackson.databind.node.ObjectNode ;

@SpringBootTest ( //
		classes = CsapInformationTest.Simple_CSAP.class , //
		webEnvironment = WebEnvironment.RANDOM_PORT )
@ActiveProfiles ( {
		"test", "no-security", "cache"
} )
@DirtiesContext
@TestInstance ( TestInstance.Lifecycle.PER_CLASS )
public class CsapInformationTest {

	final static private Logger logger = LoggerFactory.getLogger( CsapInformationTest.class ) ;

	@Autowired
	private ApplicationContext applicationContext ;

	static {

		CsapApplication.initialize( "Test Setup Complete" ) ;

	}

	/**
	 * 
	 * Simple test app that excludes security autoconfiguration
	 *
	 */
	@CsapBootApplication ( scanBasePackages = {
			"org.none"
	} )
	@EnableAsync
	public static class Simple_CSAP implements WebMvcConfigurer {

		@RestController
		static public class SimpleHello {

			@GetMapping ( "/csapHiNoSecurity" )
			public String hi ( ) {

				return "Hello" +
						LocalDateTime.now( )
								.format( DateTimeFormatter
										.ofPattern( "HH:mm:ss,   MMMM d  uuuu " ) ) ;

			}

			@Cacheable ( "sampleCacheWithNoExpirations" )
			@GetMapping ( "/junit-cache-demo" )
			public String hi_using_cache ( ) {

				return "Hello " +
						LocalDateTime.now( )
								.format( DateTimeFormatter
										.ofPattern( "HH:mm:ss:nnnn,   MMMM d  uuuu " ) ) ;

			}

			@Inject
			ObjectMapper jsonMapper ;

		}

	}

	@Test
	public void verify_cache_settings ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( "beans loaded: {}" ), applicationContext.getBeanDefinitionCount( ) ) ;

		var csapInfo = applicationContext.getBean( CsapInformation.class ) ;
		var springCacheManager = csapInfo.getCacheManager( ) ;

		logger.info( "springCacheManager: {}, names: {}", springCacheManager, springCacheManager.getCacheNames( ) ) ;
		assertThat( springCacheManager instanceof JCacheCacheManager )
				.isTrue( ) ;

		var simpleHello = applicationContext.getBean( Simple_CSAP.SimpleHello.class ) ;
		var firstResponseCached = simpleHello.hi_using_cache( ) ;

		logger.info( "hi_using_cache: {}", firstResponseCached ) ;
		TimeUnit.MILLISECONDS.sleep( 100 ) ;
		assertThat( firstResponseCached )
				.as( "junit disable cache manager" )
				.isEqualTo( simpleHello.hi_using_cache( ) ) ;

		MockHttpServletRequest request = new MockHttpServletRequest( null, "/junit-test" ) ;
		ObjectNode cacheInfo = csapInfo.cacheShow( request, null ) ;
		logger.info( "cacheInfo: {}", CSAP.jsonPrint( cacheInfo ) ) ;

		assertThat( cacheInfo.toString( ) )
				.as( "junit disable cache manager" )
				.contains( simpleHello.hi_using_cache( ) ) ;

	}

}
