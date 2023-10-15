package org.csap.security ;

import java.io.IOException ;
import java.util.Iterator ;
import java.util.Map ;
import java.util.concurrent.ConcurrentHashMap ;
import java.util.concurrent.locks.Lock ;
import java.util.concurrent.locks.ReentrantLock ;

import javax.servlet.Filter ;
import javax.servlet.FilterChain ;
import javax.servlet.FilterConfig ;
import javax.servlet.ServletException ;
import javax.servlet.ServletRequest ;
import javax.servlet.ServletResponse ;
import javax.servlet.http.HttpServletResponse ;

import org.jasypt.encryption.pbe.StandardPBEStringEncryptor ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.context.ApplicationContext ;
import org.springframework.ldap.core.LdapTemplate ;
import org.springframework.web.context.support.WebApplicationContextUtils ;

import com.fasterxml.jackson.databind.ObjectMapper ;
import com.fasterxml.jackson.databind.node.ObjectNode ;

/**
 * 
 * HelperMethod for securing rest apis. Extend this class, and ensure that
 * spring context includes beans of types
 * 
 * @see StandardPBEStringEncryptor
 * @see LdapTemplate
 * 
 * 
 * @author pnightin
 *
 */

// @WebFilter(urlPatterns = { "/api/*" }, description = "Api Security Filter",
// initParams = {
// @WebInitParam(name = "placeHolderForFuture", value = "whenNeeded") })
abstract public class LdapAuthCachingFilter implements Filter {
	final Logger logger = LoggerFactory.getLogger( this.getClass( ) ) ;

	private ApplicationContext springAppContext ;

	public ApplicationContext getSpringAppContext ( ) {

		return springAppContext ;

	}

	ObjectMapper jacksonMapper = new ObjectMapper( ) ;

	/**
	 * return a group to validate role
	 * 
	 * @return
	 */
	abstract public String getGroup ( ) ;

	/**
	 * return 0 to disable
	 * 
	 * @return
	 */
	abstract public int getCacheSeconds ( ) ;

	abstract public String getLdapSearchTree ( ) ;

	@Override
	public void init ( FilterConfig filterConfig ) throws ServletException {

		springAppContext = WebApplicationContextUtils.getWebApplicationContext( filterConfig
				.getServletContext( ) ) ;

		logger.warn( "Security will be applied when either userid or password is found in request params: "
				+ springAppContext ) ;

	}

	final public static String USERID = "userid" ;
	final public static String PASSWORD = "pass" ;

	final static public String SEC_RESPONSE_ATTRIBUTE = "securityResponse" ;

	@Override
	public void doFilter ( ServletRequest request , ServletResponse resp , FilterChain filterChain )
		throws IOException ,
		ServletException {

		logger.debug( "Intercepted Request" ) ;

		HttpServletResponse response = (HttpServletResponse) resp ;
		int numPurged = purgeStaleAuth( ) ;
		logger.debug( "Entries being purged from cache:  " + numPurged ) ;

		if ( request.getParameter( USERID ) != null && request.getParameter( PASSWORD ) != null ) {

			ObjectNode resultJson = jacksonMapper.createObjectNode( ) ;

			resultJson.put( "userid", request.getParameter( USERID ) ) ;

			request.setAttribute( SEC_RESPONSE_ATTRIBUTE, resultJson ) ;
			String userid = request.getParameter( USERID ) ;
			String inputPass = request.getParameter( PASSWORD ) ;

			if ( isAuthorizationCached( userid, inputPass ) ) {

				if ( logger.isDebugEnabled( ) )
					logger.debug( "Found Cached user " ) ;

			} else {

				StandardPBEStringEncryptor encryptor = springAppContext
						.getBean( StandardPBEStringEncryptor.class ) ;

				String pass = inputPass ;

				try {

					pass = encryptor.decrypt( inputPass ) ;

				} catch ( Exception e1 ) {

					resultJson.put( "passwordWarning", "Use of encrypted passwords is recommended." ) ;
					resultJson.put( "passwordEncrypted", encryptor.encrypt( pass ) ) ;

				}

				LdapTemplate ldapTemplate = springAppContext.getBean( LdapTemplate.class ) ;
				;
				CsapUserContextCallback csapUserContextCallback = new CsapUserContextCallback(
						ldapTemplate ) ;

				logger.debug( "Authenticating: {}", userid ) ;

				boolean authenticated = ldapTemplate.authenticate( getLdapSearchTree( ), "(uid="
						+ userid
						+ ")", pass, csapUserContextCallback ) ;

				boolean authorized = false ;

				if ( authenticated ) {

					logger.debug( "Authorizing" ) ;
					authorized = csapUserContextCallback.getCsapUser( ).isAuthorized(
							getGroup( ) ) ;

				}

				if ( ! authenticated ) {

					resultJson.put( "error", "Failed to authenticate user: " + userid ) ;
					response.getWriter( ).println( jacksonMapper.writeValueAsString( resultJson ) ) ;
					response.setStatus( 403 ) ;
					return ;

				} else if ( ! authorized ) {

					resultJson.put( "error", "Failed to  authorized user: " + userid
							+ " Must be a member of: " + getGroup( ) ) ;
					response.getWriter( ).println( jacksonMapper.writeValueAsString( resultJson ) ) ;
					response.setStatus( 401 ) ;
					return ;

				} else {

					long currentTimeInMillis = System.currentTimeMillis( ) ;
					Long result = authenticatedUsersCache.putIfAbsent( userid + "~" + inputPass, currentTimeInMillis ) ;
					logger.debug( "Cached entry: " + result ) ;

				}

			}

		}

		filterChain.doFilter( request, response ) ;

	}

	long lastPurgeTime = 0 ;
	private Lock cacheLock = new ReentrantLock( ) ;

	private int purgeStaleAuth ( ) {

		int numStaleEntries = 0 ;

		// Caching disabled
		if ( getCacheSeconds( ) == 0 )
			return numStaleEntries ;

		long maxAge = getCacheSeconds( ) * 1000 ;

		long now = System.currentTimeMillis( ) ;

		// Only execute intermittenly as purges slow down performance
		if ( now - lastPurgeTime > maxAge ) {

			return numStaleEntries ;

		}

		lastPurgeTime = now ;

		// no need to block multiple threads behind the load
		if ( cacheLock.tryLock( ) ) {

			try {

				cacheLock.lock( ) ;

				for ( Iterator<Map.Entry<String, Long>> authIter = authenticatedUsersCache.entrySet( )
						.iterator( ); authIter.hasNext( ); ) {

					Map.Entry<String, Long> authEntry = authIter.next( ) ;

					if ( now - authEntry.getValue( ).longValue( ) > maxAge ) {

						authIter.remove( ) ;
						numStaleEntries++ ;

					}

				}

			} catch ( Exception e ) {

				logger.error( "Failed to prune auth cache" ) ;
				;

			} finally {

				cacheLock.unlock( ) ;

			}

		}

		return numStaleEntries ;

	}

	// http://javarevisited.blogspot.com/2013/02/concurrenthashmap-in-java-example-tutorial-working.html
	private ConcurrentHashMap<String, Long> authenticatedUsersCache = new ConcurrentHashMap<>( ) ;

	private boolean isAuthorizationCached ( String userId , String inputPasswd ) {

		// Caching disabled
		if ( getCacheSeconds( ) == 0 )
			return false ;

		logger.debug( "authCache: " + authenticatedUsersCache ) ;
		return authenticatedUsersCache.containsKey( userId + "~" + inputPasswd ) ;

	}

	@Override
	public void destroy ( ) {
		// TODO Auto-generated method stub

	}

}
