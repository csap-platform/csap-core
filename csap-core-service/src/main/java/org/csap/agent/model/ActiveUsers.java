package org.csap.agent.model ;

import java.util.HashMap ;
import java.util.HashSet ;
import java.util.Iterator ;
import java.util.concurrent.TimeUnit ;
import java.util.stream.Collectors ;

import javax.inject.Inject ;

import org.csap.agent.CsapApis ;
import org.csap.agent.CsapConstants ;
import org.csap.agent.api.AgentApi ;
import org.csap.agent.api.ApplicationApi ;
import org.csap.agent.integrations.CsapEvents ;
import org.csap.helpers.CSAP ;
import org.csap.helpers.CsapSimpleCache ;
import org.csap.security.CsapUser ;
import org.csap.security.CustomUserDetails ;
import org.csap.security.config.CsapSecuritySettings ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.beans.factory.annotation.Qualifier ;
import org.springframework.cache.annotation.Cacheable ;
import org.springframework.scheduling.annotation.Scheduled ;
import org.springframework.web.client.RestTemplate ;

import com.fasterxml.jackson.databind.JsonNode ;
import com.fasterxml.jackson.databind.ObjectMapper ;
import com.fasterxml.jackson.databind.node.ArrayNode ;
import com.fasterxml.jackson.databind.node.ObjectNode ;

//@Service
public class ActiveUsers {

	final Logger logger = LoggerFactory.getLogger( getClass( ) ) ;

	public ActiveUsers ( CsapApis csapApis, CsapSecuritySettings securitySettings ) {

		this.csapApis = csapApis ;

		this.securitySettings = securitySettings ;

	}

	private CsapSecuritySettings securitySettings ;
	private CsapApis csapApis ;

	private ObjectMapper jacksonMapper = new ObjectMapper( ) ;

	private volatile HashMap<String, CsapSimpleCache> activeUsersMap = new HashMap<String, CsapSimpleCache>( ) ;

	final static int ACTIVE_USER_MINUTES = 60 ;

	public static final String BEGIN = CsapEvents.CSAP_ACCESS_CATEGORY + "/begin/" ;
	public static final String END = CsapEvents.CSAP_ACCESS_CATEGORY + "/end/" ;

	public static String accessBegin ( String user ) {

		return BEGIN + user ;

	}

	public static String accessEnd ( String user ) {

		return END + user ;

	}

	public synchronized boolean isUserSessionLogged ( String userid ) {

		return activeUsersMap.containsKey( userid ) ;

	}

	public synchronized boolean addTrail ( String item ) {

		return addTrail( CsapUser.currentUsersID( ), item ) ;

	}

	public synchronized boolean addTrail ( String userid , String item ) {

		// log user access
		updateUserAccessAndReturnAllActive( userid, true ) ;

		// add item
		var trailCache = activeUsersMap.get( userid ) ;
		var trailReport = (ObjectNode) trailCache.getCachedObject( ) ;

		// logger.debug( "{} trail: {}", userid, trailReport ) ;

		if ( trailReport.has( item ) ) {

			return false ;

		}

		trailReport.put( item, true ) ;

		return true ;

	}

	public synchronized ArrayNode updateUserAccessAndReturnAllActive ( String userid , boolean logAccess ) {

		//
		if ( ! isUserSessionLogged( userid ) ) {

			var trailCache = CsapSimpleCache.builder( ACTIVE_USER_MINUTES, TimeUnit.MINUTES, getClass( ),
					"Active Portal Users" ) ;
			trailCache.setCachedObject( jacksonMapper.createObjectNode( ) ) ;
			activeUsersMap.put( userid, trailCache ) ;

			if ( logAccess && csapApis.application( ).isAdminProfile( ) ) {

				// agents on local host will get their access removed by admin.
				// need to throttle.

				JsonNode userDetails = CsapUser.getPrincipleInfo( ) ;

				if ( userDetails.has( "error" ) ) {

					logger.info( "Skipping user registration" ) ;

				} else {

					publishAccessForUiReports( userid, userDetails ) ;

				}

			}

			logSessionStart( userid, "" ) ;

		}

		activeUsersMap.get( userid ).reset( ) ; // extend timer

		logger.trace( "Reset user: {} in  Active Users: {}", userid, activeUsersMap.keySet( ) ) ;
		return getActive( ) ;

	}

	public void logSessionStart ( String userid , String info ) {

		csapApis.events( ).publishUserEvent( accessBegin( userid ), userid,
				"session start", info ) ;

	}

	/**
	 * 
	 * 
	 * @see ApplicationApi#userAccess(String, String)
	 * 
	 *      publish latest data into events service for referral in info popups in
	 *      ui
	 */
	private void publishAccessForUiReports ( String userid , JsonNode userDetails ) {

		ObjectNode userReport = jacksonMapper.createObjectNode( ) ;
		userReport.put( "userid", userid ) ;

		userReport.set( "csap-roles",
				jacksonMapper.convertValue( securitySettings.getRoles( ).getUserRolesFromContext( ),
						ArrayNode.class ) ) ;

		var name = userDetails.findPath( "displayname" ).asText( "-" ) ; // multiple keys
		userReport.put( "name", userDetails.findPath( "name" ).asText( name ) ) ;

		var mail = userDetails.findPath( "mail" ).asText( "-" ) ; // multiple keys
		userReport.put( "email", userDetails.findPath( "email" ).asText( mail ) ) ;

		userReport.put( "manager", CustomUserDetails.handleOptionalCn( userDetails.findPath( "manager" ).asText(
				"-" ) ) ) ;
		userReport.put( "title", userDetails.findPath( "title" ).asText( "-" ) ) ;

		var addressNode = userDetails.findPath( "address" ) ;

		if ( ! addressNode.isMissingNode( ) ) {

			userReport.set( "address", addressNode ) ;

		} else {

			userReport.set( "address", jacksonMapper.createObjectNode( ) ) ;

		}

		userReport.set( "groups", userDetails.findPath( "groups" ) ) ;
		userReport.set( "authorities", userDetails.findPath( "authorities" ) ) ;
		userReport.set( "csap-service-claim", userDetails.findPath( "csap-service-claim" ) ) ;

		//
		// pushed for ui queries
		//
		csapApis.events( ).publishUserEvent( CsapEvents.CSAP_ACCESS_CATEGORY + "/" + userid, userid,
				"user details", userReport ) ;

	}

	@Cacheable ( cacheNames = CsapConstants.TIMEOUT_CACHE_60s , key = "{'all-admin-users'}" )
	synchronized public ArrayNode allAdminUsers ( ) {

		ArrayNode users = jacksonMapper.createArrayNode( ) ;

		// remove calls for other hosts
		csapApis.application( ).getAllPackages( )
				.getServiceInstances( "admin" )
				.filter( instance -> ! instance.getHostName( ).equals( csapApis.application( ).getCsapHostName( ) ) )
				.map( this::getUsersOnRemoteAdmins )
				.forEach( users::addAll ) ;

		// add the local host entries
		users.addAll( getActive( ) ) ;

		// now make them distinct
		HashSet<String> uniqueUsers = new HashSet<>( ) ;
		users.forEach( userJson -> uniqueUsers.add( userJson.asText( ) ) ) ;

		// Now transform
		users.removeAll( ) ;
		uniqueUsers.forEach( users::add ) ;

		return users ;

	}

	@Inject
	@Qualifier ( "adminConnection" )
	private RestTemplate adminTemplate ;

	private ArrayNode getUsersOnRemoteAdmins ( ServiceInstance service ) {

		String adminUrl = service.getUrl( ) + CsapConstants.API_AGENT_URL + AgentApi.USERS_URL ;

		ArrayNode remoteUsers ;

		try {

			remoteUsers = adminTemplate.getForObject( adminUrl, ArrayNode.class ) ;

		} catch ( Exception e ) {

			logger.warn( "Failed getting admin users: {}", CSAP.buildCsapStack( e ) ) ;
			remoteUsers = jacksonMapper.createArrayNode( ) ;
			remoteUsers.add( "Failed " + service.getHostName( ) ) ;

		}

		logger.debug( "Remote users: {}  url: {}", remoteUsers, adminUrl ) ;

		return remoteUsers ;

	}

	public synchronized ArrayNode getActive ( ) {

		ArrayNode users = jacksonMapper.createArrayNode( ) ;
		activeUsersMap.keySet( )
				.stream( )
				.forEach( users::add ) ;

		return users ;

	}

	final long run_interval_ms = ACTIVE_USER_MINUTES / 6 * CsapConstants.ONE_MINUTE_MS ;
	// final long run_interval_ms = 5000 ;

	@Scheduled ( initialDelay = run_interval_ms , fixedDelay = run_interval_ms )
	public synchronized void review_active_users_and_purge_expired ( )
		throws InterruptedException {

		// logger.info( "CHecking for expired users" );
		// Thread.sleep( 5000 );
		// activeUsersMap
		// .entrySet()
		// .removeIf( userCacheEntry -> userCacheEntry.getValue().isExpired() ) ;

		for ( Iterator<String> iterator = activeUsersMap.keySet( ).iterator( ); iterator.hasNext( ); ) {

			String userid = iterator.next( ) ;
			CsapSimpleCache usersCache = activeUsersMap.get( userid ) ;

			if ( usersCache.isExpired( ) ) {

				logSessionEnd( userid, "" ) ;

				iterator.remove( ) ;

			}

		}

		if ( logger.isDebugEnabled( ) ) {

			var userReport = activeUsersMap.entrySet( ).stream( )
					.map( entry -> CSAP.padLine( entry.getKey( ) ) + entry.getValue( ).getCachedObject( ) )
					.collect( Collectors.joining( ) ) ;

			logger.debug( "userReport: {}", userReport ) ;

		}

	}

	public void logSessionEnd ( String userid , String info ) {

		csapApis.events( ).publishUserEvent( accessEnd( userid ), userid,
				"session end", info ) ;

	}
}
