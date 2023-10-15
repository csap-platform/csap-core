package org.csap.integations ;

import java.io.BufferedReader ;
import java.io.IOException ;
import java.io.InputStream ;
import java.io.InputStreamReader ;
import java.net.InetAddress ;
import java.util.List ;
import java.util.stream.Collectors ;

import javax.inject.Inject ;
import javax.servlet.http.HttpServletRequest ;

import org.apache.catalina.Context ;
import org.apache.catalina.connector.Connector ;
import org.apache.catalina.core.StandardHost ;
import org.apache.catalina.startup.Tomcat ;
import org.apache.catalina.valves.ErrorReportValve ;
import org.apache.commons.lang3.StringUtils ;
import org.apache.coyote.AbstractProtocol ;
import org.apache.coyote.ProtocolHandler ;
import org.apache.coyote.ajp.AbstractAjpProtocol ;
import org.apache.coyote.http11.AbstractHttp11JsseProtocol ;
import org.apache.tomcat.util.http.Rfc6265CookieProcessor ;
import org.apache.tomcat.util.net.SSLHostConfig ;
import org.csap.helpers.CSAP ;
import org.csap.helpers.CsapApplication ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.beans.factory.annotation.Autowired ;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty ;
import org.springframework.boot.autoconfigure.web.servlet.ServletWebServerFactoryAutoConfiguration ;
import org.springframework.boot.context.properties.EnableConfigurationProperties ;
import org.springframework.boot.web.context.WebServerInitializedEvent ;
import org.springframework.boot.web.embedded.tomcat.TomcatContextCustomizer ;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory ;
import org.springframework.boot.web.embedded.tomcat.TomcatWebServer ;
import org.springframework.boot.web.server.WebServerFactoryCustomizer ;
import org.springframework.boot.web.servlet.server.ServletWebServerFactory ;
import org.springframework.context.annotation.Bean ;
import org.springframework.context.annotation.Configuration ;
import org.springframework.context.event.EventListener ;
import org.springframework.core.io.ClassPathResource ;

/**
 * Optional integration with Apache Web Server; you can also use client side
 * loadbalancing as an alternative
 *
 * @author pnightin
 * 
 * @see ServletWebServerFactoryAutoConfiguration
 *
 *      =============== WebServer integration provides LoadBalancing and Sticky
 *      sessions
 *
 */
@Configuration ( "CsapWebServer" )
@ConditionalOnProperty ( "csap.web-server.enabled" )
@EnableConfigurationProperties ( CsapWebSettings.class )
public class CsapWebServerConfig {

	private static final String TOMCAT_ERROR_TEMPLATE = "tomcat-error.html" ;

	final static Logger logger = LoggerFactory.getLogger( CsapWebServerConfig.class ) ;

	CsapWebSettings settings ;

	static int maxPostInBytes = 2 * 1024 * 1024 ;

	String ajpConfiguration = "" ;
	StringBuilder connectorReport = new StringBuilder( ) ;

	@Autowired ( required = false )
	CsapEncryptionConfiguration csapEncrypt ;

	@Autowired
	public CsapWebServerConfig ( CsapWebSettings settings ) {

		this.settings = settings ;
		logger.debug( CsapApplication.header( "Constructing bean: \n {} ," ),
				settings ) ;

	}

	@EventListener
	public void handleContextRefresh ( WebServerInitializedEvent event ) {

		connectorReport.append( CSAP.buildDescription( "loaded using 'csap.web-server.*' and 'server.*'",
				"server.port", settings.getHttpPort( ),
				CsapInformation.SERVER_CONTEXT_PATH, settings.getContextPath( ),
				"ssl server", getSettings( ).getSsl( ).isEnabled( ),
				"ssl client", getSettings( ).getSsl( ).isClient( ),
				"ssl keystore", getSettings( ).getSsl( ).getKeystoreFile( ),
				"ssl self signed", getSettings( ).getSsl( ).isSelfSigned( ) ) ) ;

		try {

			buildServerReport( event ) ;

		} catch ( Exception e ) {

			connectorReport.append( "\n\t Warning CSAP tomcat initialization failed" ) ;
			logger.error( "Failed to get tomcat information {}", CSAP.buildCsapStack( e ) ) ;

		}

		logger.info( connectorReport.toString( ) ) ;

	}

	@Bean
	WebServerFactoryCustomizer<TomcatServletWebServerFactory> cookieProcessorCustomizer ( ) {

		return new WebServerFactoryCustomizer<TomcatServletWebServerFactory>( ) {

			@Override
			public void customize ( TomcatServletWebServerFactory tomcatServletWebServerFactory ) {

				tomcatServletWebServerFactory.addContextCustomizers( new TomcatContextCustomizer( ) {
					@Override
					public void customize ( Context context ) {

						Rfc6265CookieProcessor processor = new Rfc6265CookieProcessor( ) ;
						processor.setSameSiteCookies( settings.getSameSiteCookie( ) ) ;
						context.setCookieProcessor( processor ) ;

					}
				} ) ;

			}
		} ;

	}

	@Bean
	public ServletWebServerFactory servletContainer ( ) {

		TomcatServletWebServerFactory tomcatFactory = new TomcatServletWebServerFactory( ) ;

		try {

			logger.debug( "Building a custom tomcat factory" ) ;

			if ( settings.getSsl( ).isEnabled( ) ) {

				tomcatFactory.addAdditionalTomcatConnectors( createSslConnector( ) ) ;

			} else {

				logger.warn( "SSL Disabled: keystore not located" ) ;

			}

			// https://tomcat.apache.org/tomcat-8.0-doc/config/http.html
			if ( settings.isAjpDisabled( ) ) {

				logger.warn( "Disabled AJP connection due to port < 1000 : '{}'", settings.getAjpConnectionPort( ) ) ;

			} else {

				// var tomcatErrorPages = tomcatFactory.getErrorPages() ;
				// tomcatErrorPages.add( new ErrorPage("/error/404.html") ) ;
				// logger.info( "tomcatErrorPages: {}", tomcatErrorPages );

				tomcatFactory.addAdditionalTomcatConnectors( createAjpConnector( ) ) ;

				tomcatFactory.addConnectorCustomizers( ( connector ) -> {

					ProtocolHandler handler = connector.getProtocolHandler( ) ;

					// connector.setProperty( "acceptCount", "0" );
					// connector.setProperty("maxThreads", "1");
					// connector.
					// h.setA
					if ( handler instanceof AbstractProtocol ) {

						AbstractProtocol<?> protocol = (AbstractProtocol<?>) handler ;

						// set and overridden by spring boot server.max-thread
						// protocol.setMaxThreads(1);
						protocol.setMaxConnections( settings.getMaxConnectionsHttp( ) ) ;
						protocol.setAcceptCount( settings.getBacklog( ) ) ;
						// protocol.setConnectionTimeout( 1000 );

					}

				} ) ;

			}

		} catch ( Exception e ) {

			logger.warn( "Failed configuration: {}", CSAP.buildCsapStack( e ) ) ;

		}

		webServerInfo.append( settings.toString( ) ) ;

		logger.debug( "Factory created {}", webServerInfo.toString( ) ) ;
		// tomcatFactory.

		// tunnels through with no JSESSIONID
		// tomcat.addContextValves( createRemoteIpValves() );
		return tomcatFactory ;

	}

	private Connector createSslConnector ( ) {

		var sslPort = settings.getSsl( ).getPort( ) ;

		logger.info( "creating ssl connector on port: {}", sslPort ) ;

		// Http11NioProtocol, older - Http11Nio2Protocol exception on shutdown
		var theConnector = new Connector( "org.apache.coyote.http11.Http11Nio2Protocol" ) ;

		theConnector.setPort( sslPort ) ;
		theConnector.setScheme( "https" ) ;

		// https://tomcat.apache.org/tomcat-8.5-doc/config/http.html#SSL_Support_-_Certificate
		theConnector.setProperty( "SSLEnabled", "true" ) ;

		var sslHostConfig = new SSLHostConfig( ) ;
		sslHostConfig.setSslProtocol( "TLS" ) ;
		sslHostConfig.setCertificateKeystoreFile( settings.getSsl( ).getKeystoreFile( ) ) ;
		sslHostConfig.setCertificateKeystoreType( settings.getSsl( ).getKeystoreType( ) ) ;

		var decoded = csapEncrypt.decodeIfPossible( settings.getSsl( ).getKeystorePassword( ), logger ) ;

		sslHostConfig.setCertificateKeystorePassword( decoded ) ;
		sslHostConfig.setCertificateKeyAlias( settings.getSsl( ).getKeyAlias( ) ) ;

		theConnector.addSslHostConfig( sslHostConfig ) ;

		return theConnector ;

	}

	// private RemoteIpValve createRemoteIpValves() {
	// RemoteIpValve remoteIpValve = new RemoteIpValve();
	// remoteIpValve.setRemoteIpHeader( "x-forwarded-for" );
	// remoteIpValve.setProtocolHeader( "x-forwarded-protocol" );
	// return remoteIpValve;
	// }

	private Connector createAjpConnector ( ) {

		//
		// refer to: https://tomcat.apache.org/tomcat-9.0-doc/config/ajp.html
		//

		// AjpNioProtocol vs AjpNio2Protocol
		Connector connector = new Connector( "org.apache.coyote.ajp.AjpNio2Protocol" ) ;

		// relying on tomcat system property - look for param in the future
		System.setProperty( "jvmRoute", settings.getJvmAjpRoute( ) ) ;

		String ajpSecret = settings.getAjpSecret( ) ;

		if ( connector.getProtocolHandler( ) instanceof AbstractAjpProtocol<?> ) {

			settings.setAjpConfigurationSource( "via protocol handler" ) ;
			var ajpHandler = ( (AbstractAjpProtocol<?>) connector.getProtocolHandler( ) ) ;

			ajpHandler.setMaxThreads( settings.getMaxThreads( ) ) ;
			ajpHandler.setMaxConnections( settings.getMaxConnectionsAjp( ) ) ;
			ajpHandler.setAcceptCount( settings.getBacklog( ) ) ;

			try {

				InetAddress localhost = InetAddress.getLocalHost( ) ;
//				ajpHandler.setAddress( localhost ) ;
				connector.setProperty( "address", "0.0.0.0" ) ;

			} catch ( Exception e ) {

				logger.warn( "Failed setting ajp address; routing will likely fail {}", CSAP.buildCsapStack( e ) ) ;

			}

			if ( StringUtils.isNotEmpty( ajpSecret ) ) {

				ajpHandler.setSecret( ajpSecret ) ;
				ajpHandler.setSecretRequired( true ) ;

			} else {

				logger.warn( CsapApplication.testHeader( "secret ignored" ) ) ;
				ajpHandler.setSecretRequired( false ) ;

			}

		} else {

			settings.setAjpConfigurationSource( "via attribute" ) ;
			connector.setProperty( "secret", ajpSecret ) ;
			connector.setProperty( "secretRequired", "true" ) ;
			connector.setProperty( "maxThreads", Integer.toString( settings.getMaxThreads( ) ) ) ;
			connector.setProperty( "maxConnections", Integer.toString( settings.getMaxConnectionsAjp( ) ) ) ;
			connector.setProperty( "acceptCount", Integer.toString( settings.getBacklog( ) ) ) ;

		}

		connector.setPort( settings.getAjpConnectionPort( ) ) ;
		connector.setScheme( settings.getAjpScheme( ) ) ;
		connector.setRedirectPort( settings.getAjpRedirectPort( ) ) ;
		connector.setProxyPort( 0 ) ;

		connector.setSecure( settings.isAjpSecure( ) ) ;

		// Using attributes, versus explicity setting in the override
		connector.setProperty( "SSLEnabled", "false" ) ;
		// connector.setRedirectPort(8443);
		// connector.setSecure(true);

		return connector ;

	}

	StringBuilder webServerInfo = new StringBuilder( ) ;

	public String toString ( ) {

		return webServerInfo.toString( ) ;

	}

	public CsapWebSettings getSettings ( ) {

		return settings ;

	}

	public void setSettings ( CsapWebSettings settings ) {

		this.settings = settings ;

	}

	public static int getMaxPostInBytes ( ) {

		logger.debug( "maxPostInBytes: {}", maxPostInBytes ) ;
		return maxPostInBytes ;

	}

	public static void setMaxPostInBytes ( int maxPostInBytes ) {

		CsapWebServerConfig.maxPostInBytes = maxPostInBytes ;

	}

	public StringBuilder getConnectorReport ( ) {

		return connectorReport ;

	}

	public boolean isSslClient ( ) {

		return settings.getSsl( ).isEnabled( )
				&& settings.getSsl( ).isClient( ) ;

	}

	public boolean isSsl_and_client_and_self_signed ( ) {

		var started = isSslClient( )
				&& settings.getSsl( ).isSelfSigned( ) ;

//		var started = connectorReport.contains( "https" )
//				&& connectorReport.contains( "TLS" )
//				&& settings.getSsl( ).isSelfSigned( ) ;

		logger.debug( "isSslServerStarted: {}", started ) ;

		return started ;

	}

	public boolean isSslServerStarted ( ) {

		var connectorReport = getConnectorReport( ).toString( ) ;

		var started = connectorReport.contains( "https" )
				&& connectorReport.contains( "TLS" ) ;

//		logger.info( "isSslServerStarted: {}, {}", started, connectorReport ) ;

		return started ;

	}

	public void setConnectorReport ( StringBuilder connectorReport ) {

		this.connectorReport = connectorReport ;

	}

	private void buildServerReport ( WebServerInitializedEvent event ) throws IOException , Exception {

		TomcatWebServer tomcatContainer = (TomcatWebServer) event.getWebServer( ) ;

		logger.debug( CSAP.padLine( "tomcatContainer port" ) + tomcatContainer.getPort( ) ) ;
		Tomcat tomcat = tomcatContainer.getTomcat( ) ;

		var tomcatHost = tomcat.getHost( ) ;

		// swap out error valve on default host to mask content
		if ( tomcatHost instanceof StandardHost ) {

			var templateResource = new ClassPathResource( TOMCAT_ERROR_TEMPLATE ) ;
			var errorTemplate = "not-found" ;

			// read from a local file
			// var templateFile = templateResource.getFile() ;
			// var errorTemplate = FileUtils.readFileToString( templateFile ) ;

			// read from a jar
			InputStream resource = templateResource.getInputStream( ) ;

			try ( BufferedReader reader = new BufferedReader(
					new InputStreamReader( resource ) ) ) {

				errorTemplate = reader.lines( )
						.collect( Collectors.joining( "\n" ) ) ;

			} catch ( Exception e ) {

				logger.warn( "Failed to load tomcat template {}", CSAP.buildCsapStack( e ) ) ;

			}

			var tomcatStandardHost = (StandardHost) tomcatHost ;
			var tomcatValveNames = List.of( tomcatStandardHost.getValveNames( ) ) ;
			logger.debug( "tomcatValveNames: {}", tomcatValveNames ) ;
			var valves = tomcatStandardHost.getPipeline( ).getValves( ) ;

			for ( var valve : valves ) {

				if ( valve instanceof ErrorReportValve ) {

					var errorValve = ( (ErrorReportValve) valve ) ;

					tomcatStandardHost.getPipeline( ).removeValve( errorValve ) ;
					var csapErrorValve = new CsapErrorReportValve( errorTemplate ) ;
					tomcatStandardHost.getPipeline( ).addValve( csapErrorValve ) ;

					connectorReport.append( CSAP.padLine( "tomcat error template" ) + TOMCAT_ERROR_TEMPLATE ) ;

					// ErrorReportValve 360 use absolute path on windows, server path on linux
					// var errorPagePath = new File(System.getProperty("user.dir"),
					// "tomcat-error.html" ) ;
					// ((ErrorReportValve) valve).setProperty( "errorCode.404",
					// errorPagePath.getAbsolutePath()) ;
					// logger.info( "errorPagePath: {} errorValves: {}",
					// errorPagePath,
					// errorValve.getProperty( "errorCode.404" ) ) ;
					break ;

				}

			}

		}

		maxPostInBytes = 0 ;

		for ( var tomcatConnector : tomcat.getService( ).findConnectors( ) ) {

			var protocolHandler = (AbstractProtocol<?>) tomcatConnector.getProtocolHandler( ) ;
			var protocolName = protocolHandler.getClass( ).getSimpleName( ) ;

			connectorReport.append( "\n\n\n" ) ;

			connectorReport.append( tomcatConnector.getProtocolHandlerClassName( ) + ":" ) ;

			connectorReport.append( CSAP.padLine( "scheme" ) + tomcatConnector.getScheme( ) ) ;
			connectorReport.append( CSAP.padLine( "port" ) + CSAP.pad( tomcatConnector.getPort( ) ) ) ;

			if ( protocolName.contains( "Ajp" ) ) {

				connectorReport.append( "*Derived from http +1 " ) ;

				connectorReport.append( CSAP.padLine( "modjk route id" )
						+ settings.getJvmAjpRoute( ) ) ;

			} else {

				// used for calculating http post limits
				if ( tomcatConnector.getMaxPostSize( ) > maxPostInBytes ) {

					// set by spring application.yml
					maxPostInBytes = tomcatConnector.getMaxPostSize( ) ;

				}

			}

			connectorReport.append( CSAP.padLine( "maxThreads" ) + CSAP.pad( protocolHandler.getMaxThreads( ) )
					+ "server.tomcat.max-threads" ) ;

			connectorReport.append( CSAP.padLine( "max-connections" )
					+ CSAP.pad( protocolHandler.getMaxConnections( ) ) + "csap.web-server.max-connections" ) ;

			connectorReport.append( CSAP.padLine( "keep Alive timeout" )
					+ CSAP.timeUnitPresent( protocolHandler.getKeepAliveTimeout( ) ) ) ;

			connectorReport.append( CSAP.padLine( "acceptCount" )
					+ CSAP.pad( protocolHandler.getAcceptCount( ) ) + "csap.web-server.backlog" ) ;

			connectorReport.append( CSAP.padLine( "address" )
					+ protocolHandler.getAddress( ) ) ;

			connectorReport.append( CSAP.padLine( "protocol port" )
					+ protocolHandler.getPort( ) ) ;

			connectorReport.append( CSAP.padLine( "connection maxPost" )
					+ ( CSAP.printBytesWithUnits( tomcatConnector.getMaxPostSize( ) ) ) ) ;

			if ( protocolHandler instanceof AbstractHttp11JsseProtocol ) {

				connectorReport.append( "\n" ) ;
				var sslProtocol = (AbstractHttp11JsseProtocol<?>) protocolHandler ;
				connectorReport.append( CSAP.padLine( "getSslProtocol" ) + sslProtocol.getSslProtocol( ) ) ;
				connectorReport.append( CSAP.padLine( "getKeyAlias" ) + sslProtocol.getKeyAlias( ) ) ;
				connectorReport.append( CSAP.padLine( "getKeystoreFile" ) + sslProtocol.getKeystoreFile( ) ) ;
				connectorReport.append( CSAP.padLine( "getKeystoreType" ) + sslProtocol.getKeystoreType( ) ) ;

				if ( logger.isDebugEnabled( ) ) {

					connectorReport.append( CSAP.padLine( "getKeystorePass" ) + sslProtocol.getKeystorePass( ) ) ;

					connectorReport.append( CSAP.padLine( "sslProtocol dump" )
							+ CSAP.jsonPrint(
									CSAP.buildGenericObjectReport( sslProtocol ) ) ) ;

				}

			}

		}

	}

	@Inject
	CsapInformation csapInformation ;

	public String getSecureUrl ( HttpServletRequest request ) {

		String secureUrl = null ;

		if ( isSslServerStarted( ) ) {

			var sslPort = request.getServerPort( ) + CsapWebSettings.SSL_PORT_OFFSET ;

			if ( request.getServerPort( ) == 80 ) {

				// assume csap web server
				sslPort = 443 ;

			}

			secureUrl = "https://" + request.getServerName( ) + ":" + sslPort +
					csapInformation.getHttpContext( ) ;

		}

		return secureUrl ;

	}

}
