package org.csap.security ;

import java.io.IOException ;
import java.util.Arrays ;
import java.util.Iterator ;
import java.util.Map ;
import java.util.Optional ;
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

import org.apache.commons.lang3.StringUtils ;
import org.csap.integations.CsapSecurityConfiguration ;
import org.csap.integations.micrometer.CsapMeterUtilities ;
import org.jasypt.encryption.pbe.StandardPBEStringEncryptor ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.context.ApplicationContext ;
import org.springframework.http.MediaType ;
import org.springframework.security.authentication.AuthenticationManager ;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken ;
import org.springframework.security.core.Authentication ;
import org.springframework.web.context.support.WebApplicationContextUtils ;

import com.fasterxml.jackson.databind.JsonNode ;
import com.fasterxml.jackson.databind.ObjectMapper ;
import com.fasterxml.jackson.databind.node.ObjectNode ;

import io.micrometer.core.instrument.Timer ;

/**
 * 
 * HelperMethod for securing rest apis. Extend this class, and ensure that
 * spring context includes beans of type
 * 
 * @see AuthenticationManager
 * 
 *      Example
 * @Bean(name = "myAuthenticationManager")
 * @Override public AuthenticationManager authenticationManagerBean() throws
 *           Exception { return super.authenticationManagerBean(); }
 * 
 * @author pnightin
 *
 *         Jersey prefers interceptors, but filters will work in most scenarios:
 *         https://java.net/projects/jax-rs-spec/lists/users/archive/2014-02/
 *         message/0
 *
 */

// @WebFilter(urlPatterns = { "/api/*" }, description = "Api Security Filter",
// initParams = {
// @WebInitParam(name = "placeHolderForFuture", value = "whenNeeded") })
public class SpringAuthCachingFilter implements Filter {

	final static Logger logger = LoggerFactory.getLogger( SpringAuthCachingFilter.class ) ;

	private String token ;
	private String group ;
	private int cacheSeconds ;

	CsapMeterUtilities csapMicroUtilites ;

	public SpringAuthCachingFilter ( CsapMeterUtilities csapMicroUtilites, String token, String group,
			int cacheSeconds ) {

		this.group = group ;
		this.token = token ;
		this.cacheSeconds = cacheSeconds ;
		this.csapMicroUtilites = csapMicroUtilites ;

	}

	private ApplicationContext springContext ;

	public ApplicationContext getSpringContext ( ) {

		return springContext ;

	}

	ObjectMapper jacksonMapper = new ObjectMapper( ) ;

	/**
	 * return a group to validate role
	 * 
	 * @return
	 */
	public String getGroup ( ) {

		return group ;

	};

	/**
	 * return 0 to disable
	 * 
	 * @return
	 */
	public int getCacheSeconds ( ) {

		return cacheSeconds ;

	};

	@Override
	public void init ( FilterConfig filterConfig )
		throws ServletException {

		springContext = WebApplicationContextUtils.getWebApplicationContext( filterConfig
				.getServletContext( ) ) ;
		logger.debug(
				"Security will be applied when either userid or password is found in request params, and cached for: {} seconds.\n springContext: {}",
				getCacheSeconds( ), springContext ) ;

	}

	final public static String USERID = "userid" ;
	final public static String PASSWORD = "pass" ;

	final static public String SEC_RESPONSE_ATTRIBUTE = "securityResponse" ;

	private String localUser = null ;
	private String localPass = null ;

	public void setLocalCredentials ( String user , String pass ) {

		logger.info( "***** Adding local credential: {}", user ) ;
		this.localUser = user ;
		this.localPass = pass ;

	}

	@Override
	public void doFilter ( ServletRequest request , ServletResponse resp , FilterChain filterChain )
		throws IOException ,
		ServletException {

		logger.debug( "Intercepted Request" ) ;
		// SimonManager.getCounter( "csap.security.filter" ).increase();
		csapMicroUtilites.incrementCounter( "csap.security.rest-api" ) ;

		HttpServletResponse response = (HttpServletResponse) resp ;

		int numPurged = purgeStaleAuth( ) ;
		logger.debug( "Entries being purged from cache count:  {}", numPurged ) ;

		if ( request.getParameter( USERID ) != null
				&& request.getParameter( PASSWORD ) != null ) {

			ObjectNode resultJson = jacksonMapper.createObjectNode( ) ;

			resultJson.put( "userid", request.getParameter( USERID ) ) ;

			request.setAttribute( SEC_RESPONSE_ATTRIBUTE, resultJson ) ;
			String userid = request.getParameter( USERID ) ;
			String inputPass = request.getParameter( PASSWORD ) ;

			if ( this.localUser != null
					&& this.localUser.equals( userid )
					&& this.localPass != null ) {

				String pass = decrypt( inputPass, resultJson ) ;

				logger.debug( "Found local user: {} ", userid ) ;

				if ( this.localPass.equals( pass ) ) {

					csapMicroUtilites.incrementCounter( "csap.security.rest-api.auth-local-pass" ) ;

				} else {

					csapMicroUtilites.incrementCounter( "csap.security.rest-api.auth-local-fail" ) ;
					resultJson.put( "error", "Failed to authenticate local user: " + userid ) ;
					response.getWriter( ).println( jacksonMapper.writeValueAsString( resultJson ) ) ;
					response.setStatus( 403 ) ;
					return ;

				}

			} else if ( isAuthorizationCached( userid, inputPass ) ) {

				csapMicroUtilites.incrementCounter( "csap.security.rest-api.auth-cache" ) ;
				logger.debug( "Found Cached user: {} ", userid ) ;

			} else {

				String pass = decrypt( inputPass, resultJson ) ;

				boolean authenticated = false ;
				boolean authorized = false ;

				if ( ! StringUtils.isEmpty( getToken( ) ) ) {

					logger.debug( "Verifying request using token" ) ;

					resultJson.put( "securityTokenConfigured", "true" ) ;
					Timer.Sample filterTimer = csapMicroUtilites.startTimer( ) ;
					String testToken = decrypt( getToken( ), resultJson ) ;

					Optional<String> matched = Arrays.asList( testToken.split( "," ) ).stream( )
							.filter( pass::equals )
							.findFirst( ) ;

					if ( matched.isPresent( ) ) {

						authenticated = true ;
						authorized = true ;

					}

					csapMicroUtilites.stopTimer( filterTimer, "csap.security.rest-api.auth-token" ) ;

				}

				if ( ! authenticated ) {

					Timer.Sample authTimer = csapMicroUtilites.startTimer( ) ;

					try {

						// Use spring inject auth manager, which enables
						// multiple
						// providers
						AuthenticationManager authManager = springContext
								.getBean(
										CsapSecurityConfiguration.AUTH_MANAGER_EXPOSE_FOR_REST,
										AuthenticationManager.class ) ;

						UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
								userid, pass ) ;
						Authentication a = authManager.authenticate( authToken ) ;
						a.getAuthorities( ) ;

						logger.debug( "Authenticated via authmanager, authorities:  {}", a.getAuthorities( )
								.toString( ) ) ;
						authenticated = true ;

						// Spring security uses role prefix
						// if group is not empty - verify role

						if ( StringUtils.isEmpty( getGroup( ) ) ) {

							authorized = true ;

						} else {

							authorized = a.getAuthorities( ).toString( ).contains( getGroup( ) ) ;

						}

					} catch ( Throwable e ) {

						logger.warn( "Failed authenticating userid: {}, keyField: {}, Reason: {}", userid,
								getKeyField( request ), e.getMessage( ) ) ;
						logger.debug( "Reason: ", e ) ;

					}

					csapMicroUtilites.stopTimer( authTimer, "csap.security.rest-api.auth-ldap" ) ;

				}

				if ( ! authenticated ) {

					csapMicroUtilites.incrementCounter( "csap.security.rest-api.authenticate-fail" ) ;
					csapMicroUtilites.incrementCounter( "csap.security.rest-api.authenticate-fail." + userid ) ;
					resultJson.put( "error", "Failed to authenticate user: " + userid ) ;
					response.setContentType( MediaType.APPLICATION_JSON_VALUE ) ;
					response.getWriter( ).println( jacksonMapper.writeValueAsString( resultJson ) ) ;
					response.setStatus( 403 ) ;
					return ;

				} else if ( ! authorized ) {

					response.setContentType( MediaType.APPLICATION_JSON_VALUE ) ;
					csapMicroUtilites.incrementCounter( "csap.security.rest-api.authorize-fail" ) ;
					csapMicroUtilites.incrementCounter( "csap.security.rest-api.authorize-fail." + userid ) ;
					resultJson.put( "error", "Failed to  authorized user: " + userid
							+ " Must be a member of: " + getGroup( ) ) ;
					response.getWriter( ).println( jacksonMapper.writeValueAsString( resultJson ) ) ;
					response.setStatus( 401 ) ;
					return ;

				} else {

					csapMicroUtilites.incrementCounter( "csap.security.rest-api.success" ) ;
					csapMicroUtilites.incrementCounter( "csap.security.rest-api.success." + userid ) ;
					long currentTimeInMillis = System.currentTimeMillis( ) ;
					Long result = authenticatedUsersCache.putIfAbsent( userid + "~" + inputPass,
							currentTimeInMillis ) ;
					logger.debug( "Cached entry: {}", result ) ;

				}

			}

		}

		filterChain.doFilter( request, response ) ;

	}

	public String decrypt ( String inputPass , ObjectNode resultJson ) {

		// need to do a lookup due to spring initialization sequence
		StandardPBEStringEncryptor encryptor = springContext
				.getBean( StandardPBEStringEncryptor.class ) ;
		String result = inputPass ;

		try {

			result = encryptor.decrypt( inputPass ) ;

		} catch ( Exception e1 ) {

			resultJson.put( "passwordWarning", "Use of encrypted passwords is recommended." ) ;
			resultJson.put( "passwordEncrypted", encryptor.encrypt( inputPass ) ) ;

		}

		return result ;

	}

	// Over ride if you want to include an identify string in logs on failures
	// Default implementation looks for eventJson param used by CSAP data
	// services.
	protected String getKeyField ( ServletRequest request ) {

		String result = "none" ;
		String jsonParam = request.getParameter( "eventJson" ) ;
		logger.debug( "Payload: {}", jsonParam ) ;

		if ( jsonParam != null ) {

			try {

				JsonNode node = jacksonMapper.readTree( jsonParam ) ;

				result = node.findValue( "host" ).asText( ) ;

			} catch ( IOException e ) {

				logger.debug( "Failed to parse eventJson parameter" ) ;

			}

		}

		return result ;

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
		if ( now - lastPurgeTime < maxAge ) {

			return numStaleEntries ;

		}

		lastPurgeTime = now ;

		// no need to block multiple threads behind the load
		if ( cacheLock.tryLock( ) ) {

			try {

				logger.debug( "Running Purge Logic" ) ;
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

		logger.debug( "authCache: {}", authenticatedUsersCache ) ;
		return authenticatedUsersCache.containsKey( userId + "~" + inputPasswd ) ;

	}

	@Override
	public void destroy ( ) {
		// TODO Auto-generated method stub

	}

	public String getToken ( ) {

		return token ;

	}

}
