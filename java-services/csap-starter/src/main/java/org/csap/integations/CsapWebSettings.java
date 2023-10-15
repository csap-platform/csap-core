package org.csap.integations ;

import java.net.InetAddress ;

import org.apache.commons.lang3.StringUtils ;
import org.csap.helpers.CSAP ;
import org.csap.helpers.CsapApplication ;
import org.csap.helpers.CsapRestTemplateFactory ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.beans.factory.annotation.Autowired ;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty ;
import org.springframework.boot.context.properties.ConfigurationProperties ;
import org.springframework.core.env.Environment ;
import org.springframework.core.io.Resource ;

@ConditionalOnProperty ( "csap.web-server.enabled" )
@ConfigurationProperties ( prefix = CsapWebSettings.PROPERTY_BASE )
public class CsapWebSettings {

	final static Logger logger = LoggerFactory.getLogger( CsapWebSettings.class ) ;

	@Autowired
	public CsapWebSettings ( Environment springEnvironment, CsapInformation csapInformation ) {

		this.springEnvironment = springEnvironment ;
		this.csapInformation = csapInformation ;

	}

	private Environment springEnvironment ;
	private CsapInformation csapInformation ;

	public final static String PROPERTY_BASE = "csap.web-server" ;
	public final static String DEFAULT_AJP_VARIABLE_IN_YAML = "csapAjp" ;

	private String sameSiteCookie = "lax" ;

	private int maxConnectionsHttp = 1000 ;
	private int maxConnectionsAjp = 50 ;
	private int backlog = 1 ;

	private int ajpProxyPort = 0 ;
	private int ajpRedirectPort = 443 ;
	private int ajpConnectionPort = -1 ;
	private String ajpHost = null ;
	private String ajpScheme = "http" ;
	private String ajpSecret = "http" ;
	boolean ajpSecure = false ;
	String ajpConfigurationSource = "" ;

	private SslSettings ssl = new SslSettings( ) ;

	static String HOST_NAME = "notFound" ;

	public final static int SSL_PORT_OFFSET = 2 ;

	static {

		try {

			HOST_NAME = InetAddress.getLocalHost( ).getHostName( ) ;
			HOST_NAME = HOST_NAME.split( "\\." )[0] ;

		} catch ( Exception e ) {

			logger.error( "Failed getting host name {}", CsapRestTemplateFactory.getFilteredStackTrace( e, "csap" ) ) ;

		}

	}

	public int getMaxThreads ( ) {

		String threadConfig = springEnvironment.getProperty( "server.tomcat.max-threads" ) ;
		logger.debug( "server.tomcat.max-threads: {} ", threadConfig ) ;

		int maxThreads = 50 ;

		try {

			maxThreads = Integer.parseInt( threadConfig ) ;

		} catch ( NumberFormatException e ) {

			logger.warn( "ignoring server.tomcat.max-threads as non integer was found: {}", threadConfig ) ;

		}

		return maxThreads ;

	}

	public int getMaxConnectionsHttp ( ) {

		return maxConnectionsHttp ;

	}

	public void setMaxConnectionsHttp ( int maxConnectionsHttp ) {

		this.maxConnectionsHttp = maxConnectionsHttp ;

	}

	public int getMaxConnectionsAjp ( ) {

		return maxConnectionsAjp ;

	}

	public void setMaxConnectionsAjp ( int maxConnectionsAjp ) {

		this.maxConnectionsAjp = maxConnectionsAjp ;

	}

	public int getBacklog ( ) {

		return backlog ;

	}

	public void setBacklog ( int backlog ) {

		this.backlog = backlog ;

	}

	public int getAjpProxyPort ( ) {

		return ajpProxyPort ;

	}

	public void setAjpProxyPort ( int ajpProxyPort ) {

		this.ajpProxyPort = ajpProxyPort ;

	}

	public int getAjpRedirectPort ( ) {

		return ajpRedirectPort ;

	}

	public void setAjpRedirectPort ( int ajpRedirectPort ) {

		this.ajpRedirectPort = ajpRedirectPort ;

	}

	public int getAjpConnectionPort ( ) {

		if ( ajpConnectionPort == -1 ) {

			return getHttpPort( ) + 1 ;

		}

		return ajpConnectionPort ;

	}

	int parseInteger ( String value , int defaultValue ) {

		try {

			return Integer.parseInt( value ) ;

		} catch ( NumberFormatException nfe ) {

			// Log exception.
			return defaultValue ;

		}

	}

	public void setAjpConnectionPort ( int ajpConnectionPort ) {

		this.ajpConnectionPort = ajpConnectionPort ;

	}

	public String getAjpHost ( ) {

		if ( StringUtils.isEmpty( ajpHost ) )
			return HOST_NAME ;
		return ajpHost ;

	}

	public void setAjpHost ( String ajpHost ) {

		this.ajpHost = ajpHost ;

	}

	public String getAjpScheme ( ) {

		return ajpScheme ;

	}

	public void setAjpScheme ( String ajpScheme ) {

		this.ajpScheme = ajpScheme ;

	}

	public String getAjpSecret ( ) {

		return ajpSecret ;

	}

	public void setAjpSecret ( String ajpSecret ) {

		this.ajpSecret = ajpSecret ;

	}

	public boolean isAjpSecure ( ) {

		return ajpSecure ;

	}

	public void setAjpSecure ( boolean ajpSecure ) {

		this.ajpSecure = ajpSecure ;

	}

	public boolean isAjpDisabled ( ) {

		return getAjpConnectionPort( ) < 1000 ;

	}

	@Override
	public String toString ( ) {

		StringBuilder webServerInfo = new StringBuilder( ) ;

		webServerInfo.append( "\n " + CsapWebSettings.PROPERTY_BASE + ":" ) ;

		webServerInfo.append( "\n " + CSAP.padLine( "same-site-cookie" ) + getSameSiteCookie( )
				+ "   ( https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Set-Cookie/SameSite ) \n" ) ;

		if ( StringUtils.isEmpty( getSsl( ).getKeystoreFile( ) ) ) {

			webServerInfo.append( CSAP.padLine( "ssl" ) + "Disabled: keystore file not specified" ) ;

		} else {

			webServerInfo.append( CSAP.padLine( "ssl keystore file" ) + getSsl( ).getKeystoreFile( ) ) ;
			webServerInfo.append( CSAP.padLine( "ssl key alias" ) + getSsl( ).getKeyAlias( ) ) ;
			webServerInfo.append( CSAP.padLine( "ssl port" ) + getSsl( ).getPort( ) ) ;
			webServerInfo.append( CSAP.padLine( "ssl self signed" ) + getSsl( ).isSelfSigned( ) ) ;

		}

		webServerInfo.append( "\n " ) ;

		if ( isAjpDisabled( ) ) {

			webServerInfo.append( CSAP.padLine( "AJP" ) + "Disabled due to port < 1000 ***" ) ;

		} else {

			webServerInfo.append(
					CSAP.padLine( "host" ) + getAjpHost( ) + ":" + getAjpConnectionPort( )
							+ " jvmRoute(modjk): "
							+ getJvmAjpRoute( ) + " secret: "
							+ getAjpSecret( ) ) ;

			webServerInfo.append( CSAP.padLine( "secure" ) + isAjpSecure( ) + " scheme: " + getAjpScheme( ) ) ;
			webServerInfo.append(
					CSAP.padLine( "redirect-port" ) + getAjpRedirectPort( ) + " proxy-port: "
							+ getAjpProxyPort( ) ) ;

			webServerInfo
					.append( CSAP.padLine( "ajp configuration" ) + getAjpConfigurationSource( ) ) ;

		}

		webServerInfo.append( "\n " ) ;

		return webServerInfo.toString( ) ;

	}

	public String getContextPath ( ) {

		String contextPath = springEnvironment.getProperty( CsapInformation.SERVER_CONTEXT_PATH ) ;

		if ( contextPath != null && contextPath.length( ) > 1 ) {

			return contextPath.substring( 1 ) ;

		}

		return "DEFAULT_ROOT_CONTEXT" ;

	}

	public int getHttpPort ( ) {

		return parseInteger( csapInformation.getHttpPort( ), 8080 ) ;

	}

	public String getJvmAjpRoute ( ) {

		return getContextPath( ) + "_" + getHttpPort( ) + getAjpHost( ) ;

	}

	public String getAjpConfigurationSource ( ) {

		return ajpConfigurationSource ;

	}

	public void setAjpConfigurationSource ( String ajpConfigurationSource ) {

		this.ajpConfigurationSource = ajpConfigurationSource ;

	}

	public SslSettings getSsl ( ) {

		return ssl ;

	}

	public void setSsl ( SslSettings ssl ) {

		this.ssl = ssl ;

	}

	public class SslSettings {

		private int port = -1 ;

		private String keystoreType ;

		private String keystoreFile = "" ;

		private String keystorePassword ;

		private String keyAlias ;

		private String testDomain ;

		private boolean selfSigned = false ;

		private boolean client = true ;

		public int getPort ( ) {

			if ( port == -1 ) {

				return getHttpPort( ) + SSL_PORT_OFFSET ;

			}

			return port ;

		}

		public void setPort ( int port ) {

			this.port = port ;

		}

		public String getKeystoreType ( ) {

			return keystoreType ;

		}

		public void setKeystoreType ( String keystoreType ) {

			this.keystoreType = keystoreType ;

		}

		public String getKeystoreFile ( ) {

			return keystoreFile ;

		}

		int maxPrints = 0 ;

		public boolean isEnabled ( ) {

			var sslEnabled = StringUtils.isNotEmpty( getKeystoreFile( ) ) ;

			if ( sslEnabled
					&& CsapApplication.isCsapFolderSet( )
					&& isCsapLabCertificate( ) ) {

				sslEnabled = false ;
				var csapFqdn = System.getenv( "csapFqdn" ) ;

				if ( StringUtils.isNotEmpty( csapFqdn )
						&& csapFqdn.contains( "yourcompany.org" ) ) {

					sslEnabled = true ;

				} else {

					if ( maxPrints++ == 0 ) {

						logger.warn( CsapApplication.testHeader(
								"host: {} keystore file {} only valid for domain yourcompany.org" ),
								csapFqdn,
								getKeystoreFile( ) ) ;

					}

				}

			}

			if ( getPort( ) < 1000 ) {

				return false ;

			}

			return sslEnabled ;

		}

		public void setKeystoreFile ( Resource keystoreFile ) {

			var theFile = "" ;

			try {

				theFile = keystoreFile.getURL( ).toString( ) ;

			} catch ( Exception e ) {

				logger.info( "Failed to resolve {} {}", keystoreFile, CSAP.buildCsapStack( e ) ) ;

			}

			this.keystoreFile = theFile ;

		}

		public String getKeystorePassword ( ) {

			return keystorePassword ;

		}

		public void setKeystorePassword ( String keystorePassword ) {

			this.keystorePassword = keystorePassword ;

		}

		public String getKeyAlias ( ) {

			return keyAlias ;

		}

		public void setKeyAlias ( String keyAlias ) {

			this.keyAlias = keyAlias ;

		}

		public String getTestDomain ( ) {

			return testDomain ;

		}

		public void setTestDomain ( String testDomain ) {

			this.testDomain = testDomain ;

		}

		public boolean isSelfSigned ( ) {

			return selfSigned ;

		}

		public void setSelfSigned ( boolean selfSigned ) {

			this.selfSigned = selfSigned ;

		}

		public boolean isCsapLabCertificate ( ) {

			if ( StringUtils.isEmpty( getKeystoreFile( ) ) ) {

				return false ;

			}

			var isLabCert = getKeystoreFile( ).endsWith( "csap-lab.p12" ) ;

			return isLabCert ;

		}

		public boolean isClient ( ) {

			return client ;

		}

		public void setClient ( boolean client ) {

			this.client = client ;

		}

	}

	public String getSameSiteCookie ( ) {

		return sameSiteCookie ;

	}

	public void setSameSiteCookie ( String sameSite ) {

		this.sameSiteCookie = sameSite ;

	}

}
