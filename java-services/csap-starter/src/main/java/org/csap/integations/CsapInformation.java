package org.csap.integations ;

import java.io.PrintWriter ;
import java.net.InetAddress ;
import java.net.URI ;
import java.net.URISyntaxException ;
import java.time.LocalDateTime ;
import java.time.format.DateTimeFormatter ;
import java.util.Enumeration ;
import java.util.LinkedHashMap ;
import java.util.Map ;
import java.util.Objects ;
import java.util.regex.Pattern ;
import java.util.stream.Collectors ;
import java.util.stream.StreamSupport ;

import javax.inject.Inject ;
import javax.servlet.http.Cookie ;
import javax.servlet.http.HttpServletRequest ;
import javax.servlet.http.HttpSession ;

import org.apache.commons.lang3.StringUtils ;
import org.csap.alerts.AlertsController ;
import org.csap.docs.CsapDoc ;
import org.csap.docs.DocumentController ;
import org.csap.helpers.CSAP ;
import org.csap.helpers.CsapApplication ;
import org.csap.helpers.CsapSimpleCache ;
import org.csap.security.config.CsapSecurityRoles ;
import org.csap.security.config.CsapSecuritySettings ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.beans.factory.annotation.Autowired ;
import org.springframework.beans.factory.annotation.Value ;
import org.springframework.boot.context.properties.ConfigurationProperties ;
import org.springframework.cache.CacheManager ;
import org.springframework.cache.jcache.JCacheCache ;
import org.springframework.context.ApplicationContext ;
import org.springframework.context.annotation.Bean ;
import org.springframework.core.env.Environment ;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter ;
import org.springframework.security.core.GrantedAuthority ;
import org.springframework.security.core.context.SecurityContext ;
import org.springframework.security.core.context.SecurityContextHolder ;
import org.springframework.web.bind.annotation.GetMapping ;
import org.springframework.web.bind.annotation.PathVariable ;
import org.springframework.web.bind.annotation.RequestMapping ;
import org.springframework.web.bind.annotation.RestController ;
import org.springframework.web.servlet.ModelAndView ;

import com.fasterxml.jackson.databind.ObjectMapper ;
import com.fasterxml.jackson.databind.node.ArrayNode ;
import com.fasterxml.jackson.databind.node.ObjectNode ;

@RestController ( "csapInformation" )
@ConfigurationProperties ( prefix = "csap.info" )
@RequestMapping ( "${csap.baseContext:/csap}" )
@CsapDoc ( //
		title = "CSAP Information Controller" , //
		type = CsapDoc.OTHER , //
		notes = {
				"@Inject CsapInformation into your spring beans for convenient access to CSAP variables",
				" and configuration settings (workingDir, etc.)"
		} )
public class CsapInformation {

	final Logger logger = LoggerFactory.getLogger( this.getClass( ) ) ;

	public static final String SERVER_CONTEXT_PATH = "server.servlet.context-path" ;

	public CsapInformation ( ) {

	}

	@Bean
	/**
	 * @see org.springframework.boot.autoconfigure.security.servlet.SpringBootWebSecurityConfiguration#
	 */
	public WebSecurityConfigurerAdapter earlyInjectionOfWebSecurityConfigurerAdapter ( ) {

		logger.warn(
				"Spring Boot 2.4 Early Injection of null WebSecurityConfigurerAdapter\n reason: disable SpringBootWebSecurityConfiguration injection of SecurityFilterChain " ) ;
		return null ;

	}

	@Value ( "${csap.baseContext:/csap}" )
	public String csapBaseContext ;

	@Autowired
	private Environment springEnv ;
	@Autowired
	private ApplicationContext springContext ;

	@Autowired
	CsapBootConfig config ;

	@Autowired ( required = false )
	CsapSecuritySettings securitySettings ;

	ObjectMapper jacksonMapper = new ObjectMapper( ) ;

	@RequestMapping ( value = "/identity" )
	@CsapDoc ( notes = "Shows User Identity from security information" , baseUrl = "/csap" )
	public ObjectNode identity ( )
		throws Exception {

		ObjectNode userIdentity = jacksonMapper.createObjectNode( ) ;
		userIdentity.put( "security-provider", securitySettings.getProvider( ).getType( ) ) ;

		SecurityContext springSecurityContext = SecurityContextHolder.getContext( ) ;

		if ( springSecurityContext.getAuthentication( ) == null ) {

			userIdentity.put( "authentication-source", "disabled" ) ;
			return userIdentity ;

		}

		ObjectNode authenticationSummary = userIdentity.putObject( "summary" ) ;
		authenticationSummary.put( "name", springSecurityContext.getAuthentication( ).getName( ) ) ;

		String authorities = springSecurityContext.getAuthentication( ).getAuthorities( ).stream( )
				.map( GrantedAuthority::getAuthority )
				.collect( Collectors.joining( "," ) ) ;
		authenticationSummary.put( "authorities", authorities ) ;

		userIdentity.set( "details", (ObjectNode) CSAP.buildGenericObjectReport( springSecurityContext
				.getAuthentication( ) ) ) ;

		// if ( principle instanceof DefaultOidcUser ) {
		//
		// idNode.set( "oauth details", CSAP.getDetails( principle )) ;
		//
		// } else {
		// UserDetails person =(UserDetails) principle ;
		//
		// CustomUserDetails customUser = null ;
		// idNode.put( "userid", person.getUsername() ) ;
		//
		// logger.debug( "Security Principle: {}", person ) ;
		// if ( person instanceof CustomUserDetails ) {
		//
		// idNode.put( "url", securitySettings.getProvider().getUrl() ) ;
		//
		// customUser = (CustomUserDetails) person ;
		// idNode.put( "name-cn", customUser.getCn()[0] ) ;
		// idNode.put( "mail", customUser.getMail() ) ;
		// idNode.put( "mail-extended", customUser.getAllAttributesInConfigFile().get(
		// "mail" ).toString() ) ;
		//
		// ObjectNode customNode = idNode.putObject( "custom-user-attributes" ) ;
		// NamingEnumeration<String> idEnum =
		// customUser.getAllAttributesInConfigFile().getIDs() ;
		//
		// while ( idEnum.hasMore() ) {
		//
		// String id = idEnum.next() ;
		// String attributeValues = customUser.getAllAttributesInConfigFile().get( id
		// ).get().toString() ;
		// customNode.put( id, attributeValues ) ;
		// }
		//
		// } else if ( person instanceof ActiveDirectoryUserDetails ) {
		//
		// idNode.put( "authentication-source", "Active Directory" ) ;
		// idNode.put( "url", securitySettings.getProvider().getUrl() ) ;
		//
		// ActiveDirectoryUserDetails adUser = (ActiveDirectoryUserDetails) person ;
		// idNode.put( "name-cn", adUser.getCn()[0] ) ;
		// idNode.put( "description", adUser.getDescription() ) ;
		// idNode.put( "department", adUser.getDepartment() ) ;
		// idNode.put( "mail", adUser.getMail() ) ;
		// // ArrayNode rolesNode = idNode.putArray( "user-roles" );
		// // idNode.put( "mail-extended",
		// // adUser.getAllAttributesInConfigFile().get( "mail" ).toString() );
		//
		// ObjectNode customNode = idNode.putObject( "custom-user-attributes" ) ;
		// NamingEnumeration<String> idEnum =
		// adUser.getAllAttributesInConfigFile().getIDs() ;
		//
		// while ( idEnum.hasMore() ) {
		//
		// String id = idEnum.next() ;
		// String attributeValues = adUser.getAllAttributesInConfigFile().get( id
		// ).get().toString() ;
		// customNode.put( id, attributeValues ) ;
		// }
		//
		// } else {
		// idNode.put( "authentication-source", "In Memory" ) ;
		// }
		// }
		// }

		return userIdentity ;

	}

	@GetMapping ( value = "/" )
	@CsapDoc ( notes = {
			"Default csap management console"
	} , baseUrl = "/csap" )
	public ModelAndView defaultConsole ( HttpServletRequest request ) {

		ModelAndView mav = new ModelAndView( "csap/console" ) ;

		return mav ;

	}

	ObjectNode customLogs = jacksonMapper.createObjectNode( ) ;

	@GetMapping ( value = "/loggers" )
	@CsapDoc ( notes = {
			"show defined loggers"
	} , baseUrl = "/csap" )
	synchronized public ObjectNode loggers (
												String logName ,
												String logValue ,
												HttpServletRequest request ,
												HttpSession session ) {

		var loggerReport = jacksonMapper.createObjectNode( ) ;

		if ( securitySettings != null
				&& ! securitySettings.getRoles( ).getAndStoreUserRoles( session )
						.contains( CsapSecurityRoles.ADMIN_ROLE ) ) {

			loggerReport.put( "error", "user is not an admin" ) ;
			return loggerReport ;

		}

		try {

			URI uri = new URI( request.getRequestURL( ).toString( ) ) ;

			loggerReport.put( "refresh", uri.resolve( "loggers" ).toString( ) ) ;

			if ( customLogs.isEmpty( ) ) {

				addLogEntry( "org.sample", "default", uri ) ;
				addLogEntry( "org.csap.agent.ui.rest", "default", uri ) ;
				addLogEntry( "org.csap.agent.stats", "default", uri ) ;
				addLogEntry( "org.csap", "default", uri ) ;
				addLogEntry( "org.csap.security", "default", uri ) ;
				addLogEntry( "org.springframework.boot", "default", uri ) ;
				addLogEntry( "org.springframework.security", "default", uri ) ;
				addLogEntry( "org.springframework", "default", uri ) ;

			}

			if ( ! StringUtils.isEmpty( logName ) ) {

				logger.warn( CsapApplication.highlightHeader( "logger updated: {} level: {}" ), logName, logValue ) ;

				if ( StringUtils.isNotEmpty( logValue )
						&& logValue.equals( "debug" ) ) {

					CSAP.setLogToDebug( logName ) ;

					addLogEntry( logName, "debug", uri ) ;

				} else {

					CSAP.setLogToInfo( logName ) ;
					addLogEntry( logName, "info", uri ) ;

				}

			}

		} catch ( URISyntaxException e ) {

			logger.warn( "Failed to generated url {}", CSAP.buildCsapStack( e ) ) ;

		}

		loggerReport.set( "current-settings", customLogs ) ;

		return loggerReport ;

	}

	private void addLogEntry (
								String logName ,
								String logValue ,
								URI uri ) {

		var logActions = customLogs.putObject( logName ) ;
		logActions.put( "current", logValue ) ;

		var switchValue = "debug" ;

		if ( logValue.equals( "debug" ) ) {

			switchValue = "info" ;

		}

		logActions.put( "update", uri.resolve( "loggers?logName=" + logName + "&logValue=" + switchValue )
				.toString( ) ) ;

	}

	@GetMapping ( value = "/csapInfo" )
	@CsapDoc ( notes = {
			"Primary configuration data, including csap environment variabale and Servlet information"
	} , baseUrl = "/csap" )
	public ObjectNode showSecureConfiguration (
												HttpServletRequest request )
		throws Exception {

		logger.debug( "Getting data" ) ;

		var performanceConfig = springContext.getBean( CsapPerformance.class ) ;

		var documentController = springContext.getBean( DocumentController.class ) ;

		var infoNode = jacksonMapper.createObjectNode( ) ;

		ObjectNode commonNode = infoNode.putObject( "csap-configuration" ) ;

		commonNode.put( "csap.security.enabled", securitySettings != null ) ;

		commonNode.put( "csap.document.enabled", documentController != null ) ;
		commonNode.put( "csap.performance.enabled", performanceConfig != null ) ;

		ObjectNode csapNode = infoNode.putObject( "csap-info" ) ;

		csapNode.put( "getName", getName( ) ) ;
		csapNode.put( "getVersion", getVersion( ) ) ;
		csapNode.put( "getLoadBalancerUrl", getLoadBalancerUrl( ) ) ;
		csapNode.put( "getLifecycle", getLifecycle( ) ) ;
		csapNode.put( "getPort", getHttpPort( ) ) ;
		csapNode.put( "getCluster", getCluster( ) ) ;

		if ( performanceConfig != null ) {

			ArrayNode monitorUrls = commonNode.putArray( "monitorUrls" ) ;

			for ( String url : performanceConfig.getMonitorUrls( ) ) {

				monitorUrls.add( url ) ;

			}

		}

		ObjectNode servlet = infoNode.putObject( "servlet" ) ;

		ObjectNode core = servlet.putObject( "core" ) ;
		core.put( "getRemoteUser", request.getRemoteUser( ) ) ;
		core.put( "getRemoteAddr", request.getRemoteAddr( ) ) ;
		core.put( "getRemoteHost", request.getRemoteHost( ) ) ;
		core.put( "getRemotePort", request.getRemotePort( ) ) ;
		core.put( "getServerName", request.getServerName( ) ) ;
		core.put( "getServletPath", request.getServletPath( ) ) ;
		core.put( "getRequestURI", request.getRequestURI( ) ) ;

		ObjectNode requestHeaders = servlet.putObject( "requestHeaders" ) ;
		Enumeration<String> names = request.getHeaderNames( ) ;

		while ( names.hasMoreElements( ) ) {

			String name = (String) names.nextElement( ) ;

			if ( name.startsWith( "cookie" ) ) {

				continue ;

			}

			String value = request.getHeader( name ) ;

			requestHeaders.put( name, value ) ;

		}

		ObjectNode cookieNode = servlet.putObject( "cookies" ) ;

		if ( request.getCookies( ) != null ) {

			for ( Cookie cookie : request.getCookies( ) ) {

				cookieNode.put( cookie.getName( ), cookie.getPath( ) + " , " + cookie.getDomain( ) + " ,"
						+ cookie.getSecure( ) + " ," + cookie.getValue( ) ) ;

			}

		}

		ObjectNode requestAtt = servlet.putObject( "attributes" ) ;
		names = request.getAttributeNames( ) ;

		while ( names.hasMoreElements( ) ) {

			String name = (String) names.nextElement( ) ;

			String value = request.getHeader( name ) ;

			requestAtt.put( name, value ) ;

		}

		return infoNode ;

	}

	@Inject
	private CacheManager cacheManager ;

	@CsapDoc ( notes = "Show and clear ehcache entires, show CSAP simple cache entries" , baseUrl = "/csap" )
	@RequestMapping ( value = "/cache/show" )
	public ObjectNode cacheShow (
									HttpServletRequest request ,
									HttpSession session ) {

		ObjectNode cacheEntries = jacksonMapper.createObjectNode( ) ;

		if ( securitySettings != null
				&& session != null
				&& ! securitySettings.getRoles( ).getAndStoreUserRoles( session )
						.contains( CsapSecurityRoles.ADMIN_ROLE ) ) {

			cacheEntries.put( "error", "user is not an admin" ) ;
			return cacheEntries ;

		}

		try {

			URI uri = new URI( request.getRequestURL( ).toString( ) ) ;
			cacheEntries.put( "clear-all-caches", uri.resolve( "clear/all" ).toString( ) ) ;

		} catch ( URISyntaxException e ) {

			logger.warn( "Failed to generated url {}", CSAP.buildCsapStack( e ) ) ;

		}

		cacheManager.getCacheNames( )
				.stream( )
				.map( cacheManager::getCache )
				.filter( JCacheCache.class::isInstance )
				.map( JCacheCache.class::cast )
				// .filter( Eh107Cache.class::isInstance )
				.forEach( jCache -> {

					String cacheName = jCache.getName( ) ;
					logger.debug( "name: {} type: {}", cacheName, jCache.getNativeCache( ).getClass( ).getName( ) ) ;
					ObjectNode cacheContainer = cacheEntries.putObject( cacheName ) ;

					try {

						URI uri = new URI( request.getRequestURL( ).toString( ) ) ;
						cacheContainer.put( "clear", uri.resolve( "clear/" + cacheName ).toString( ) ) ;

					} catch ( URISyntaxException e ) {

						logger.warn( "Failed to generated url {}", CSAP.buildCsapStack( e ) ) ;

					}

					ObjectNode cacheEntry = cacheContainer.putObject( "entries" ) ;

					StreamSupport.stream( jCache.getNativeCache( ).spliterator( ), false )
							.filter( Objects::nonNull )
							.forEach( item -> {

								Object key = item.getKey( ) ;
								String value = item.getValue( ).toString( ) ;
								cacheEntry.put( key.toString( ), value ) ;

								try {

									cacheEntry.set( key.toString( ), jacksonMapper.readTree( value ) ) ;

								} catch ( Exception e ) {

									logger.debug( "Not json: {}", CSAP.buildCsapStack( e ) ) ;

								}

							} ) ;
					;

				} ) ;

		ArrayNode simpleCacheArray = cacheEntries.putArray( "CsapSimpleCache" ) ;

		CsapSimpleCache.getCacheReferences( ).forEach( cache -> {

			ObjectNode simple = simpleCacheArray.addObject( ) ;
			simple.put( "class", cache.getClassName( ) ) ;
			simple.put( "description", cache.getDescription( ) ) ;
			simple.put( "maxAge", cache.getMaxAgeFormatted( ) ) ;
			simple.put( "currentAge", cache.getCurrentAgeFormatted( ) ) ;

		} ) ;

		return cacheEntries ;

	}

	@CsapDoc ( notes = "Empty cache on the server " )
	@GetMapping ( value = "/cache/clear/{cacheToClear}" )
	public ObjectNode cacheClear (
									@PathVariable String cacheToClear ,
									HttpServletRequest request ) {

		ObjectNode resultNode = jacksonMapper.createObjectNode( ) ;

		resultNode.put( "clearing", cacheToClear ) ;

		try {

			URI uri = new URI( request.getRequestURL( ).toString( ) ) ;
			resultNode.put( "show", uri.resolve( "../show" ).toString( ) ) ;

		} catch ( URISyntaxException e ) {

			logger.warn( "Failed to generated url {}", CSAP.buildCsapStack( e ) ) ;

		}

		logger.warn( "Clearing caches: {} ", cacheToClear ) ;

		cacheManager.getCacheNames( )
				.stream( )
				.filter( ( cacheName ) -> cacheName.equals( cacheToClear )
						|| cacheToClear.equals( "all" ) )
				.map( cacheManager::getCache )
				.filter( JCacheCache.class::isInstance )
				.map( JCacheCache.class::cast )
				.forEach( JCacheCache::clear ) ;

		resultNode.put( "completed", LocalDateTime.now( ).format( DateTimeFormatter.ofPattern(
				"HH:mm:ss,   MMMM d  uuuu " ) ) ) ;
		return resultNode ;

	}

	public Map<String, String> buildToolsMap ( ) {

		Map<String, String> tools = new LinkedHashMap<>( ) ;

		tools.put( "Logout", "/logout" ) ;
		tools.put( "csap: performance", getCsapHealthUrl( ) ) ;
		tools.put( "csap: performance MM", getCsapHealthUrl( ) + "?pod=default" ) ;
		tools.put( "csap: api navigator", getDocUrl( ) + "/nav" ) ;
		tools.put( "csap: identity", getCsapBaseContext( ) + "/identity" ) ;
		tools.put( "csap: information", getCsapBaseContext( ) + "/csapInfo" ) ;
		tools.put( "csap: cache", getCsapBaseContext( ) + "/cache/show" ) ;
		tools.put( "csap: loggers", getCsapBaseContext( ) + "/loggers" ) ;
		// tools.put( "Cache - clear", "/cache/clear" );

		String bootActuatorContext = springEnv.getProperty( "management.endpoints.web.base-path" ) ;

		if ( bootActuatorContext == null ) {

			bootActuatorContext = "" ;

		}

		logger.debug( "bootActuatorContext: {}", bootActuatorContext ) ;

		tools.put( "boot: actuator list", bootActuatorContext + "/" ) ;

		tools.put( "boot: environment", bootActuatorContext + "/env" ) ;
		tools.put( "boot: metrics", bootActuatorContext + "/metrics" ) ;
		tools.put( "boot: prometheus", bootActuatorContext + "/prometheus" ) ;
		tools.put( "boot: metrics/threads", bootActuatorContext + "/metrics/jvm.threads.live" ) ;
		tools.put( "boot: health", bootActuatorContext + "/health" ) ;
		tools.put( "boot: thread dump", bootActuatorContext + "/threaddump" ) ;
		tools.put( "boot: heap dump", bootActuatorContext + "/heapdump" ) ;
		tools.put( "boot: loggers", bootActuatorContext + "/loggers" ) ;

		if ( CsapBootConfig.isHttpTraceEnabled( ) ) {

			tools.put( "boot: http trace", bootActuatorContext + "/httptrace" ) ;

		} else {

			tools.put( "boot: http trace", getCsapBaseContext( ) + "/httpTraceDisabled" ) ;

		}

		tools.put( "boot: http urls", bootActuatorContext + "/mappings" ) ;

		tools.put( "boot: info", bootActuatorContext + "/info" ) ;
		tools.put( "boot: beans", bootActuatorContext + "/beans" ) ;
		tools.put( "boot: conditions", bootActuatorContext + "/conditions" ) ;
		tools.put( "boot: caches", bootActuatorContext + "/caches" ) ;
		tools.put( "boot: configuration", bootActuatorContext + "/configprops" ) ;

		// logger.info( "logout: {}" , getHttpContext() + "/logout" );

		return tools ;

	}

	@GetMapping ( value = "/httpTraceDisabled" )
	public void showHttpTraceMessage ( PrintWriter writer ) {

		writer.print(
				"HttpTrace is disabled - it is enabled if /opt/csap/enable-http-trace or /java-local/runJava.sh is present. (jvm must be restarted after creating)" ) ;

	}

	public String getCsapHealthUrl ( ) {

		return getCsapBaseContext( ) + AlertsController.HEALTH_URL ;

	}

	private String name = null ;
	private String loadBalancerUrl ;
	private String lifecycle = "dev" ;
	private String version ;
	private String httpPort ;
	private String workingDir ;
	private String cluster ;
	private String domain = "yourcompany.com" ;

	public String getTime ( ) {

//		return LocalDateTime.now().format( DateTimeFormatter.ofPattern( "HH:mm:ss,   MMM d  uuuu " ) ) ;
		return LocalDateTime.now( ).format( DateTimeFormatter.ofPattern( "MMM.d-HH.mm" ) ) ;

	}

	public String getName ( ) {

		if ( name == null ) {

			return "default_" + LocalDateTime.now( ).format( DateTimeFormatter.ofPattern( "MMM.d-HH.mm" ) ) ;

		}

		return name ;

	}

	public void setName ( String name ) {

		this.name = name ;

	}

	public String getLoadBalancerUrl ( ) {

		return loadBalancerUrl ;

	}

	public void setLoadBalancerUrl ( String loadBalancerUrl ) {

		this.loadBalancerUrl = loadBalancerUrl ;

	}

	public String getVersion ( ) {

		return version ;

	}

	public void setVersion ( String version ) {

		this.version = version ;

	}

	/**
	 * @see #getHttpPort()
	 * @return
	 */
	@Deprecated
	public String getCsapEnvHttpPort ( ) {

		logger.warn( "Use getHttpPort" ) ;
		return httpPort ;

	}

	public String getHttpPort ( ) {

		// return httpPort; not a good idea this is the env variable. Instead -
		// leverage the server port
		String serverPort = "8080" ;

		if ( springEnv != null && springEnv.getProperty( "server.port" ) != null ) {

			serverPort = springEnv.getProperty( "server.port" ) ;

		}

		return serverPort ;

	}

	public void setHttpPort ( String port ) {

		this.httpPort = port ;

	}

	public String getFullServiceUrl ( ) {

		return "http://" + getHostName( ) + "." + getDomain( ) + ":" + getHttpPort( ) + getHttpContext( ) ;

	}

	public String getFullServiceCsapUrl ( ) {

		return getFullServiceUrl( ) + getCsapBaseContext( ) ;

	}

	public String getHttpContext ( ) {

		String context = "" ;
		if ( springEnv != null )
			context = springEnv.getProperty( SERVER_CONTEXT_PATH ) ;

		if ( context == null ||
				( context.length( ) == 1 && context.equals( "/" ) ) ) {

			context = "" ;

		}

		return context ;

	}

	public boolean isAgentContext ( ) {

		if ( ! CsapApplication.isCsapFolderSet( ) ) {

			if ( getHttpPort( ).equals( "7011" ) ) {

				return true ;

			}

		}

		return getHttpPort( ).equals( "8011" ) ;

	}

	public String getCluster ( ) {

		return cluster ;

	}

	public void setCluster ( String cluster ) {

		this.cluster = cluster ;

	}

	public String getLifecycle ( ) {

		return lifecycle ;

	}

	public void setLifecycle ( String lifecycle ) {

		this.lifecycle = lifecycle ;

	}

	public String getHostName ( ) {

		return HOST_NAME ;

	}

	public String getHostShortName ( ) {

		return HOST_NAME.split( Pattern.quote( "." ) )[0] ;

	}

	static String HOST_NAME = "notFound" ;

	static {

		try {

			HOST_NAME = InetAddress.getLocalHost( ).getHostName( ) ;

		} catch ( Exception e ) {

			HOST_NAME = "HOST_LOOKUP_ERROR" ;
			System.out.println( "\n\n CsapInformation.java: Failed getting host name: " + e.getMessage( ) + "\n\n" ) ;
			e.printStackTrace( ) ;

		}

	}

	public String toString ( ) {

		StringBuilder infoBuilder = new StringBuilder( ) ;
		infoBuilder.append( "csap.information:" ) ;
		infoBuilder.append( CSAP.padLine( "name" ) + getName( ) ) ;
		infoBuilder.append( CSAP.padLine( "http context" ) + getHttpContext( ) + ", httpPort:  " + getHttpPort( ) ) ;
		infoBuilder.append( CSAP.padLine( "lb url" ) + getLoadBalancerUrl( ) ) ;
		infoBuilder.append( CSAP.padLine( "direct url" ) + getFullServiceUrl( ) ) ;
		infoBuilder.append( CSAP.padLine( "version" ) + getVersion( ) ) ;
		infoBuilder.append( CSAP.padLine( "workingDir" ) + getWorkingDir( ) ) ;
		infoBuilder.append( CSAP.padLine( "csapBaseContext" ) + getCsapBaseContext( ) ) ;
		String ehcacheLocation = springEnv.getProperty( "spring.cache.jcache.config" ) ;
		// new ClassPathResource( ehcacheLocation )).getURI()
		infoBuilder.append( CSAP.padLine( "spring.cache.jcache.config" ) + ehcacheLocation ) ;
		infoBuilder.append( CSAP.padLine( "spring cache manager" ) + getCacheManager( ).getClass( ).getName( ) ) ;
		infoBuilder.append( "\n" ) ;

		return infoBuilder.toString( ) ;

	}

	public String getWorkingDir ( ) {

		return workingDir ;

	}

	public void setWorkingDir ( String workingDir ) {

		this.workingDir = workingDir ;

	}

	public String getDomain ( ) {

		return domain ;

	}

	public void setDomain ( String domain ) {

		this.domain = domain ;

	}

	public String getCsapBaseContext ( ) {

		return csapBaseContext ;

	}

	public String getFullHealthUrl ( ) {

		return getFullServiceCsapUrl( ) + AlertsController.HEALTH_URL ;

	}

	public String getDocUrl ( ) {

		return getCsapBaseContext( ) + "/docs" ;

	}

	public CacheManager getCacheManager ( ) {

		return cacheManager ;

	}
}
