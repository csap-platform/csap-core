package org.sample.input.http.api ;

import java.text.SimpleDateFormat ;
import java.util.Date ;

import javax.inject.Inject ;
import javax.servlet.http.HttpServletRequest ;
import javax.servlet.http.HttpServletResponse ;
import javax.ws.rs.core.MediaType ;

import org.apache.commons.logging.Log ;
import org.apache.commons.logging.LogFactory ;
import org.sample.Csap_Tester_Application ;
import org.sample.jpa.DemoManager ;
import org.springframework.cache.annotation.Cacheable ;
import org.springframework.web.bind.annotation.PathVariable ;
import org.springframework.web.bind.annotation.RequestMapping ;
import org.springframework.web.bind.annotation.RequestMethod ;
import org.springframework.web.bind.annotation.RequestParam ;
import org.springframework.web.bind.annotation.RestController ;
import org.springframework.web.servlet.ModelAndView ;

import com.fasterxml.jackson.databind.ObjectMapper ;
import com.fasterxml.jackson.databind.node.ArrayNode ;
import com.fasterxml.jackson.databind.node.ObjectNode ;

@RestController
@RequestMapping ( Csap_Tester_Application.API_URL )
public class ApiRoot {

	protected final Log logger = LogFactory.getLog( getClass( ) ) ;

	DemoManager demoDataService ;

	@Inject
	public ApiRoot ( DemoManager demoDataService ) {

		logger.info( " ===== Best Practice: use constructor injection for dependencies" ) ;
		this.demoDataService = demoDataService ;

	}

	@RequestMapping ( method = RequestMethod.GET )
	public ModelAndView get ( ) {

		logger.info( "Got help" ) ;
		return new ModelAndView( "redirect:/api/help" ) ;

	}

	@RequestMapping ( "/help" )
	public ModelAndView help ( ) {

		logger.info( "Got help" ) ;
		return new ModelAndView( "api/help" ) ;

	}

	private ObjectMapper jacksonMapper = new ObjectMapper( ) ;

	@RequestMapping ( value = "/helloJson" , produces = MediaType.APPLICATION_JSON )
	public ObjectNode helloJson ( ) {

		ObjectNode resultNode = jacksonMapper.createObjectNode( ) ;
		resultNode.put( "message", "Hello" ) ;
		return resultNode ;

	}

	@Cacheable ( Csap_Tester_Application.SIMPLE_CACHE_EXAMPLE )
	@RequestMapping ( value = "/simpleCacheExample" , produces = MediaType.APPLICATION_JSON )
	public ObjectNode simpleCached ( @RequestParam ( value = "key" , defaultValue = "peter" ) String key ) {

		ObjectNode resultNode = jacksonMapper.createObjectNode( ) ;
		resultNode.put( "message",
				"Sample has max entries 3. Change request param a few times to observer cache eviction" ) ;
		resultNode.put( "key", key ) ;

		SimpleDateFormat formatter = new SimpleDateFormat( "MMM.d H:mm:ss" ) ;
		resultNode.put( "timestamp", formatter.format( new Date( ) ) ) ;
		return resultNode ;

	}

	/**
	 * More complex ehcache example - uses custom key - based on path and request
	 * params, and excludes request param
	 * 
	 * @param key
	 * @param request
	 * @return
	 */

	@Cacheable ( value = Csap_Tester_Application.SIMPLE_CACHE_EXAMPLE , key = "{#path1, #path2, #key}" )
	@RequestMapping ( value = "/customKeyExample/{path1}/{path2}" , produces = MediaType.APPLICATION_JSON )
	public ObjectNode customKeyExample (
											@PathVariable ( "path1" ) String path1 ,
											@PathVariable ( "path2" ) String path2 ,
											@RequestParam ( value = "key" , defaultValue = "peter" ) String key ,
											HttpServletRequest request ) {

		logger.info( "Got here" ) ;
		ObjectNode resultNode = jacksonMapper.createObjectNode( ) ;
		resultNode.put( "message",
				"Sample has max entries 3. Change request param a few times to observer cache eviction" ) ;
		resultNode.put( "key", key ) ;
		resultNode.put( "path1", path1 ) ;
		resultNode.put( "path2", path2 ) ;

		SimpleDateFormat formatter = new SimpleDateFormat( "MMM.d H:mm:ss" ) ;
		resultNode.put( "timestamp", formatter.format( new Date( ) ) ) ;
		return resultNode ;

	}

	@Cacheable ( Csap_Tester_Application.TIMEOUT_CACHE_EXAMPLE )
	@RequestMapping ( value = "/cacheWithTimeout" , produces = MediaType.APPLICATION_JSON )
	public ObjectNode cacheWithTimeout ( ) {

		ObjectNode resultNode = jacksonMapper.createObjectNode( ) ;
		resultNode.put( "message", "Cache has timeout eviction of ten seconds. Wait a few minutes and try again" ) ;

		SimpleDateFormat formatter = new SimpleDateFormat( "MMM.d H:mm:ss" ) ;
		resultNode.put( "timestamp", formatter.format( new Date( ) ) ) ;
		return resultNode ;

	}

	@RequestMapping ( value = "/showTestDataJson" , produces = MediaType.APPLICATION_JSON )
	public ObjectNode showTestDataJson (
											@RequestParam ( value = "filter" , defaultValue = DemoManager.TEST_TOKEN ) String filter ,
											@RequestParam ( value = "pageSize" , defaultValue = "20" , required = false ) int pageSize ,
											@RequestParam ( value = "count" , defaultValue = "1" , required = false ) int count ,
											HttpServletRequest request ,
											HttpServletResponse response )
		throws Exception {

		logger.debug( "Getting Test data" ) ;

		ObjectNode resultNode = null ;

		if ( count == 1 ) {

			try {

				resultNode = demoDataService.showScheduleItemsWithFilter( filter, pageSize ) ;

			} catch ( Exception e ) {

				logger.error( "Failed getting data: ", e ) ;

			}

		} else {

			resultNode = jacksonMapper.createObjectNode( ) ;
			int recordsFound = 0 ;
			ArrayNode timesNode = resultNode.arrayNode( ) ;
			long totalStart = System.currentTimeMillis( ) ;

			for ( int i = 0; i < count; i++ ) {

				long start = System.currentTimeMillis( ) ;
				ObjectNode items = demoDataService.showScheduleItemsWithFilter( filter, pageSize ) ;
				recordsFound += items.path( "count" ).asInt( ) ;
				timesNode.add( System.currentTimeMillis( ) - start ) ;

			}

			resultNode.put( "totalTimeInSeconds", ( System.currentTimeMillis( ) - totalStart ) / 1000 ) ;
			resultNode.put( "averageTimeInMillSeconds",
					( ( System.currentTimeMillis( ) - totalStart ) / count ) ) ;

			resultNode.put( "recordsFound",
					recordsFound ) ;
			resultNode.set( "iterationInMs", timesNode ) ;

		}

		return resultNode ;

	}
}
