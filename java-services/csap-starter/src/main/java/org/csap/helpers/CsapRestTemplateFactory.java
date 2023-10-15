package org.csap.helpers ;

import java.net.URL ;
import java.security.KeyManagementException ;
import java.security.KeyStoreException ;
import java.security.NoSuchAlgorithmException ;
import java.security.cert.X509Certificate ;
import java.util.ArrayList ;
import java.util.Arrays ;
import java.util.Iterator ;
import java.util.List ;
import java.util.concurrent.Executors ;
import java.util.concurrent.ScheduledExecutorService ;
import java.util.concurrent.TimeUnit ;

import org.apache.commons.lang3.concurrent.BasicThreadFactory ;
import org.apache.http.config.RegistryBuilder ;
import org.apache.http.conn.socket.ConnectionSocketFactory ;
import org.apache.http.conn.socket.PlainConnectionSocketFactory ;
import org.apache.http.conn.ssl.NoopHostnameVerifier ;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory ;
import org.apache.http.impl.client.CloseableHttpClient ;
import org.apache.http.impl.client.HttpClients ;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager ;
import org.apache.http.ssl.SSLContextBuilder ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.http.MediaType ;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory ;
import org.springframework.http.converter.HttpMessageConverter ;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter ;
import org.springframework.web.client.RestTemplate ;

/**
 * 
 * Helper class for RestTemplate: it requires explicit setting of typical params
 * it assumes json by default
 * 
 * @author pnightin
 *
 */
public class CsapRestTemplateFactory {

	final Logger logger = LoggerFactory.getLogger( CsapRestTemplateFactory.class ) ;

	URL certPath = null ;
	String certPass = null ;

	public CsapRestTemplateFactory ( URL certPath, String certPass ) {

		this.certPass = certPass ;
		this.certPath = certPath ;

	}

	public RestTemplate buildDefaultRestTemplate ( String description ) {

		HttpComponentsClientHttpRequestFactory httpConnectionFactory = getHttpFactory(
				description,
				1, 3,
				10, 10, 10 ) ;

		RestTemplate restTemplate = new RestTemplate( httpConnectionFactory ) ;

		return restTemplate ;

	}

	public RestTemplate buildDefaultTemplate (
												String description ,
												int maxConnectionsPerRoute ,
												int maxConnectionsTotal ,
												int connectTimeOutSeconds ,
												int readTimeOutSeconds ,
												long idleCloseSeconds ) {

		HttpComponentsClientHttpRequestFactory httpConnectionFactory = getHttpFactory(
				description,
				maxConnectionsPerRoute, maxConnectionsTotal,
				connectTimeOutSeconds, readTimeOutSeconds, idleCloseSeconds ) ;

		RestTemplate restTemplate = new RestTemplate( httpConnectionFactory ) ;

		return restTemplate ;

	}

	/**
	 * 
	 * helper if endpoint does not have a valid ssl certificate
	 * 
	 * @param isSslVerificationDisabled
	 * @param maxConnectionsPerRoute
	 * @param connectTimeOutSeconds
	 * @param readTimeOutSeconds
	 * @return
	 */
	public RestTemplate buildDefaultTemplate (
												String description ,
												boolean isSslVerificationDisabled ,
												int maxConnectionsPerRoute ,
												int maxConnectionsTotal ,
												int connectTimeOutSeconds ,
												int readTimeOutSeconds ,
												long idleCloseSeconds ) {

		if ( ! isSslVerificationDisabled ) {

			HttpComponentsClientHttpRequestFactory httpConnectionFactory = getHttpFactory(
					description,
					maxConnectionsPerRoute, maxConnectionsTotal,
					connectTimeOutSeconds, readTimeOutSeconds, idleCloseSeconds ) ;

			return new RestTemplate( httpConnectionFactory ) ;

		} else {

			return new RestTemplate( buildFactoryDisabledSslChecks( description, connectTimeOutSeconds,
					readTimeOutSeconds ) ) ;

		}

	}

	public RestTemplate buildDefaultJsonTemplate ( String description ) {

		HttpComponentsClientHttpRequestFactory httpConnectionFactory = getHttpFactory(
				description,
				1, 10, 10, 10, 10 ) ;

		RestTemplate restTemplate = new RestTemplate( httpConnectionFactory ) ;

		List<HttpMessageConverter<?>> messageConverters = new ArrayList<HttpMessageConverter<?>>( ) ;

		MappingJackson2HttpMessageConverter jacksonConverter = new MappingJackson2HttpMessageConverter( ) ;
		jacksonConverter.setPrefixJson( false ) ;
		jacksonConverter.setSupportedMediaTypes( new ArrayList<MediaType>( Arrays
				.asList( MediaType.APPLICATION_JSON ) ) ) ;

		messageConverters.add( jacksonConverter ) ;

		restTemplate.setMessageConverters( messageConverters ) ;

		return restTemplate ;

	}

	public RestTemplate buildJsonTemplate (
											String description ,
											int maxConnectionsPerRoute ,
											int maxConnectionsTotal ,
											int connectTimeOutSeconds ,
											int readTimeOutSeconds ,
											long idleCloseSeconds ) {

		HttpComponentsClientHttpRequestFactory httpConnectionFactory = getHttpFactory(
				description,
				maxConnectionsPerRoute, maxConnectionsTotal,
				connectTimeOutSeconds, readTimeOutSeconds, idleCloseSeconds ) ;

		RestTemplate restTemplate = new RestTemplate( httpConnectionFactory ) ;
		// RestTemplate restTemplate = new RestTemplate( );

		List<HttpMessageConverter<?>> messageConverters = new ArrayList<HttpMessageConverter<?>>( ) ;

		MappingJackson2HttpMessageConverter jacksonConverter = new MappingJackson2HttpMessageConverter( ) ;
		jacksonConverter.setPrefixJson( false ) ;
		jacksonConverter.setSupportedMediaTypes( new ArrayList<MediaType>( Arrays
				.asList( MediaType.APPLICATION_JSON ) ) ) ;

		messageConverters.add( jacksonConverter ) ;

		restTemplate.setMessageConverters( messageConverters ) ;

		return restTemplate ;

	}

	private ArrayList<PoolingHttpClientConnectionManager> allocatedPools = new ArrayList<>( ) ;

	private HttpComponentsClientHttpRequestFactory getHttpFactory (
																	String description ,
																	int maxConnectionsPerRoute ,
																	int maxTotalConnections ,
																	int connectTimeOutSeconds ,
																	int readTimeOutSeconds ,
																	long idleCloseSeconds ) {

		var sslConnectionFactory = SSLConnectionSocketFactory.getSocketFactory( ) ;

		if ( certPath != null ) {

			try {

				var sslContext = new SSLContextBuilder( )

						.loadTrustMaterial(
								certPath,
								certPass.toCharArray( ) )

						.build( ) ;

				sslConnectionFactory = new SSLConnectionSocketFactory( sslContext ) ;

			} catch ( Exception e ) {

				logger.warn( "Failed accessing trust store {}", CSAP.buildCsapStack( e ) ) ;

			}

		}

		logger.info( CSAP.buildDescription(
				"Creating Pool",
				"description", description,
				"source", getRequestSource( ),
				"maxTotalConnections", maxTotalConnections,
				"maxConnectionsPerRoute", maxConnectionsPerRoute,
				"connectTimeOutSeconds", connectTimeOutSeconds,
				"readTimeOutSeconds", readTimeOutSeconds,
				"idleCloseSeconds", idleCloseSeconds,
				"certPath", certPath ) ) ;

		//
		// Support self signed certs using a custom ssl registry
		//
		var socketFactoryRegistry = RegistryBuilder.<ConnectionSocketFactory>create( )
				.register( "https", sslConnectionFactory )
				.register( "http", PlainConnectionSocketFactory.getSocketFactory( ) )
				.build( ) ;
		//
		// Pool Manager requires custom socket registry for self signed
		//
		var httpPoolManager = new PoolingHttpClientConnectionManager( socketFactoryRegistry ) ;
		allocatedPools.add( httpPoolManager ) ;
		httpPoolManager.setDefaultMaxPerRoute( maxConnectionsPerRoute ) ;
		httpPoolManager.setMaxTotal( maxTotalConnections ) ;

		// httpPoolManager.setValidateAfterInactivity( 1000 );
		var closeableHttpClient = HttpClients.custom( )
				// .setKeepAliveStrategy( buildKeepAliveStrategy() )
				.setConnectionManager( httpPoolManager )
				.build( ) ;

		// Thread

		if ( idleCloseSeconds > 0 ) {

			httpPoolMonitor.scheduleWithFixedDelay(
					( ) -> {

						logger.debug( "releasing idle connections" ) ;
						httpPoolManager.closeExpiredConnections( ) ;
						httpPoolManager.closeIdleConnections( idleCloseSeconds, TimeUnit.SECONDS ) ;

					}, idleCloseSeconds, idleCloseSeconds, TimeUnit.SECONDS ) ;

		}

		var httpConnectionFactory = new HttpComponentsClientHttpRequestFactory( ) ;
		httpConnectionFactory.setHttpClient( closeableHttpClient ) ;

		httpConnectionFactory.setConnectTimeout( connectTimeOutSeconds * SECOND_IN_MS ) ;
		httpConnectionFactory.setReadTimeout( readTimeOutSeconds * SECOND_IN_MS ) ;

		return httpConnectionFactory ;

	}

	BasicThreadFactory httpPoolFactory = new BasicThreadFactory.Builder( )
			.namingPattern( "CsapRestTemplateFactory-%d" )
			.daemon( true )
			.priority( Thread.NORM_PRIORITY )
			.build( ) ;

	ScheduledExecutorService httpPoolMonitor = Executors.newScheduledThreadPool( 1, httpPoolFactory ) ;

	public synchronized void closeAllAndReset ( ) {

		logger.info( " === Shutting down monitors and open connections" ) ;

		if ( httpPoolMonitor != null ) {

			httpPoolMonitor.shutdownNow( ) ;
			httpPoolMonitor = Executors.newScheduledThreadPool( 1, httpPoolFactory ) ;

		}

		allocatedPools.stream( ).forEach( PoolingHttpClientConnectionManager::close ) ;
		allocatedPools.clear( ) ;

	}

	public static final int SECOND_IN_MS = 1000 ;

	// http://www.baeldung.com/httpclient-connection-management
	// static private ConnectionKeepAliveStrategy buildKeepAliveStrategy () {
	// ConnectionKeepAliveStrategy myStrategy = ( response, context ) -> {
	// HeaderElementIterator it = new BasicHeaderElementIterator(
	// response.headerIterator( HTTP.CONN_KEEP_ALIVE ) );
	// while (it.hasNext()) {
	// HeaderElement he = it.nextElement();
	// String param = he.getName();
	// String value = he.getValue();
	// if ( value != null && param.equalsIgnoreCase( "timeout" ) ) {
	// //logger.info( "keepAlive: {}", Long.parseLong( value ) );
	// return Long.parseLong( value ) * SECOND_IN_MS;
	// }
	// }
	// return 10 * SECOND_IN_MS;
	// };
	//
	// return myStrategy;
	// }

	public HttpComponentsClientHttpRequestFactory buildFactoryDisabledSslChecks (
																					String description ,
																					int connectTimeoutSeconds ,
																					int readTimeoutSeconds ) {

		logger.warn( CsapApplication.testHeader( "ssl verification disabled" ) ) ;

		logger.warn( CSAP.buildDescription( description,
				"source", getRequestSource( ) ) ) ;

		SSLContextBuilder builder = new SSLContextBuilder( ) ;
		// builder.loadTrustMaterial(null, new TrustSelfSignedStrategy());
		SSLConnectionSocketFactory sslsf = null ;

		try {

			builder.loadTrustMaterial( null,
					( X509Certificate[] chain , String authType ) -> {

						return true ;

					} ) ;

			sslsf = new SSLConnectionSocketFactory(
					builder.build( ), new NoopHostnameVerifier( ) ) ;

		} catch ( KeyManagementException | NoSuchAlgorithmException | KeyStoreException e ) {

			logger.error( "Failed to build factory with ssl verification disabled" ) ;

		}

		CloseableHttpClient httpClient = HttpClients.custom( ).setSSLSocketFactory(
				sslsf ).build( ) ;

		HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory( ) ;
		factory.setHttpClient( httpClient ) ;

		factory.setConnectTimeout( connectTimeoutSeconds * 1000 ) ;
		factory.setReadTimeout( readTimeoutSeconds * 1000 ) ;

		return factory ;

	}

	public HttpComponentsClientHttpRequestFactory buildNonPooledFactory (
																			String description ,
																			int connectTimeoutSeconds ,
																			int readTimeoutSeconds ) {

		logger.debug( CSAP.buildDescription( description,
				"source", getRequestSource( ) ) ) ;

		var sslConnectionFactory = SSLConnectionSocketFactory.getSocketFactory( ) ;

		if ( certPath != null ) {

			try {

				var sslContext = new SSLContextBuilder( )

						.loadTrustMaterial(
								certPath,
								certPass.toCharArray( ) )

						.build( ) ;

				sslConnectionFactory = new SSLConnectionSocketFactory( sslContext ) ;

			} catch ( Exception e ) {

				logger.warn( "Failed accessing trust store {}", CSAP.buildCsapStack( e ) ) ;

			}

		}

		CloseableHttpClient httpClient = HttpClients.custom( ).setSSLSocketFactory(
				sslConnectionFactory ).build( ) ;

		HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory( ) ;
		factory.setHttpClient( httpClient ) ;

		factory.setConnectTimeout( connectTimeoutSeconds * 1000 ) ;
		factory.setReadTimeout( readTimeoutSeconds * 1000 ) ;

		return factory ;

	}

	private String getRequestSource ( ) {

		String stack = Arrays.asList( Thread.currentThread( ).getStackTrace( ) ).stream( )
				.filter( stackElement -> {

					return ( ! stackElement.getClassName( ).equals( getClass( ).getName( ) ) )
							&& ( ! stackElement.getClassName( ).startsWith( "java." ) ) ;

				} )
				.map( StackTraceElement::toString )
				.findFirst( )
				.orElse( "Stack not found" ) ;
		return stack ;

	}

	public static String getFilteredStackTrace ( Throwable possibleNestedThrowable , String pattern ) {

		// add the class name and any message passed to constructor
		final StringBuffer result = new StringBuffer( ) ;

		Throwable currentThrowable = possibleNestedThrowable ;

		int nestedCount = 1 ;

		while ( currentThrowable != null ) {

			if ( nestedCount == 1 ) {

				result.append( "\n========== CSAP Exception, Filter:  " + pattern ) ;

			} else {

				result.append( "\n========== Nested Count: " ) ;
				result.append( nestedCount ) ;
				result.append( " ===============================" ) ;

			}

			result.append( "\n\n Exception: " + currentThrowable
					.getClass( )
					.getName( ) ) ;
			result.append( "\n Message: " + currentThrowable.getMessage( ) ) ;
			result.append( "\n\n StackTrace: \n" ) ;

			// add each element of the stack trace
			List<StackTraceElement> traceElements = Arrays.asList( currentThrowable.getStackTrace( ) ) ;

			Iterator<StackTraceElement> traceIt = traceElements.iterator( ) ;

			while ( traceIt.hasNext( ) ) {

				StackTraceElement element = traceIt.next( ) ;
				String stackDesc = element.toString( ) ;

				if ( pattern == null || stackDesc.contains( pattern ) ) {

					result.append( stackDesc ) ;
					result.append( "\n" ) ;

				}

			}

			result.append( "\n========================================================" ) ;
			currentThrowable = currentThrowable.getCause( ) ;
			nestedCount++ ;

		}

		return result.toString( ) ;

	}

	public ArrayList<PoolingHttpClientConnectionManager> getAllocatedPools ( ) {

		return allocatedPools ;

	}

	public void setAllocatedPools ( ArrayList<PoolingHttpClientConnectionManager> allocatedPools ) {

		this.allocatedPools = allocatedPools ;

	}

}
