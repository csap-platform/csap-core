/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.csap.agent.ui.rest ;

import java.text.DateFormat ;
import java.text.SimpleDateFormat ;
import java.time.LocalDate ;
import java.time.format.DateTimeFormatter ;
import java.util.Date ;
import java.util.List ;
import java.util.stream.Collectors ;
import java.util.stream.LongStream ;

import javax.inject.Inject ;

import org.csap.agent.CsapConstants ;
import org.csap.agent.model.Application ;
import org.csap.helpers.CSAP ;
import org.csap.integations.micrometer.CsapMeterUtilities ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.beans.factory.annotation.Autowired ;
import org.springframework.beans.factory.annotation.Qualifier ;
import org.springframework.cache.CacheManager ;
import org.springframework.cache.annotation.CacheConfig ;
import org.springframework.cache.annotation.CachePut ;
import org.springframework.cache.annotation.Cacheable ;
import org.springframework.cache.support.NoOpCacheManager ;
import org.springframework.core.io.ClassPathResource ;
import org.springframework.stereotype.Component ;
import org.springframework.web.client.RestTemplate ;

import com.fasterxml.jackson.databind.ObjectMapper ;
import com.fasterxml.jackson.databind.node.ArrayNode ;
import com.fasterxml.jackson.databind.node.ObjectNode ;

/**
 *
 * @author someDeveloper
 */
@Component
@CacheConfig ( cacheNames = "AnalyticsTrendingCache" )
public class TrendCache {

	final Logger logger = LoggerFactory.getLogger( this.getClass( ) ) ;

	ObjectMapper jacksonMapper = new ObjectMapper( ) ;

	@Autowired
	Application csapApp ;

	@Autowired
	CsapMeterUtilities microMeterHelper ;
	@Inject
	private CacheManager cacheManager ;

	@Autowired
	@Qualifier ( "trendRestTemplate" )
	private RestTemplate trendRestTemplate ;

	DateFormat shortFormatter = new SimpleDateFormat( "HH:mm:ss MMM-dd" ) ;

	final static String KEY_IS_RESTURL = "{#restUrl}" ;

	boolean printOnce = true ;

	// @Cacheable ( key = KEY_IS_RESTURL )
	@Cacheable ( keyGenerator = "analyticsKeyGenerator" )
	public ObjectNode get ( String restUrl , String timerName )
		throws Exception {

		logger.debug( "restUrl: {}", restUrl ) ;
		ObjectNode resultsJson = refreshRequiredResponse( ) ;

		resultsJson.put( TrendCacheManager.HASH,
				buildReportHash( restUrl ) ) ;

		resultsJson.put( TrendCacheManager.NEEDS_LOAD, "initialLoadInProgress" ) ;

		if ( cacheManager == null || cacheManager instanceof NoOpCacheManager ) {

			if ( printOnce ) {

				printOnce = false ;
				logger.warn( "Cache disabled - blocking on response" ) ;

			}

			resultsJson = update( restUrl, timerName ) ;
			resultsJson.put( "WARNING", "cache is disabled - queries running in blocking mode" ) ;

		}

		return resultsJson ;

	}

	private ObjectNode refreshRequiredResponse ( ) {

		ObjectNode resultsJson = jacksonMapper.createObjectNode( ) ;

		resultsJson.put( TrendCacheManager.LAST_UPDATE_TOKEN, 0 ) ;
		return resultsJson ;

	}

	// Report Hashes used to prevent multiple request for the same report being
	// scheduled
	// Large number of users could hit..
	static public Integer buildReportHash ( String restUrl ) {

		return restUrl.hashCode( ) ;

	}

	private static final ClassPathResource trendStub = new ClassPathResource( CsapConstants.EVENTS_STUB_FOLDER
			+ "trendingReport.json" ) ;

	private ObjectNode loadStubDataAndUpdateDateRange ( )
		throws Exception {

		ObjectNode stubResponse = (ObjectNode) jacksonMapper.readTree( trendStub.getFile( ) ) ;

		LocalDate today = LocalDate.now( ) ;

		List<String> pastDays = LongStream
				.iterate( 15, e -> e - 1 )
				.limit( 16 )
				.mapToObj( day -> today.minusDays( day ) )
				.map( offsetDate -> offsetDate.format( DateTimeFormatter.ofPattern( "yyyy-MM-dd" ) ) )
				.collect( Collectors.toList( ) ) ;

		( (ObjectNode) stubResponse.get( "data" ).get( 0 ) )
				.set( "date", jacksonMapper.convertValue(
						pastDays,
						ArrayNode.class ) ) ;

		return stubResponse ;

	}

	// @CachePut ( key = KEY_IS_RESTURL )
	@CachePut ( keyGenerator = "analyticsKeyGenerator" )
	public ObjectNode update ( String restUrl , String timerName )
		throws Exception {

		ObjectNode resultsJson = refreshRequiredResponse( ) ;

		int reportHash = buildReportHash( restUrl ) ;
		var allTimer = microMeterHelper.startTimer( ) ;
		timerName = "trendCache.reload." + timerName.replaceAll( "/", "-" ).replaceAll( " ", "-" ).replaceAll( "=",
				"-" )
				.replaceAll( "\\?", "." ).replaceAll( "&", "." ) ;
		var trendTimer = microMeterHelper.startTimer( ) ;

		try {

			ObjectNode restResponse = null ;

			if ( ! csapApp.rootProjectEnvSettings( ).isEventPublishEnabled( ) ) {

				logger.info( "Stubbing out data for trends - add csap events services" ) ;

				restResponse = loadStubDataAndUpdateDateRange( ) ;
				resultsJson.put( "message", "csap-event-service disabled - using stub data" ) ;

			} else {

				restResponse = trendRestTemplate.getForObject( restUrl, ObjectNode.class ) ;

			}

			resultsJson = restResponse ;

			if ( resultsJson != null ) {

				resultsJson.put( "source", restUrl ) ;
				resultsJson.put( "updated", shortFormatter.format( new Date( ) ) ) ;
				resultsJson.put( TrendCacheManager.LAST_UPDATE_TOKEN, System.currentTimeMillis( ) ) ;

			}

		} catch ( Exception e ) {

			String reason = CSAP.buildCsapStack( e ) ;
			logger.error( "Failed getting report from url: {}, Reason: {}", restUrl, reason ) ;
			logger.debug( "Stack Trace ", e ) ;
			resultsJson.put( "url", restUrl ) ;
			resultsJson.put( "message", "Error during Access: " + reason ) ;

		}

		microMeterHelper.stopTimer( trendTimer, timerName ) ;
		microMeterHelper.stopTimer( allTimer, "trendCache.reload" ) ;
		resultsJson.put( TrendCacheManager.HASH, reportHash ) ;

		return resultsJson ;

	}

}
