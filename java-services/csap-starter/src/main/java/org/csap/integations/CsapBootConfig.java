package org.csap.integations ;

import java.io.File ;
import java.net.InetAddress ;
import java.net.UnknownHostException ;
import java.util.List ;

import javax.annotation.PostConstruct ;

import org.csap.debug.CsapDebug ;
import org.csap.docs.DocumentController ;
import org.csap.helpers.CSAP ;
import org.csap.helpers.CsapApplication ;
import org.csap.integations.micrometer.CsapMicroMeter ;
import org.csap.security.oath2.CsapOauth2SecurityConfiguration ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.boot.actuate.trace.http.HttpTrace ;
import org.springframework.boot.actuate.trace.http.HttpTraceRepository ;
import org.springframework.boot.actuate.trace.http.InMemoryHttpTraceRepository ;
import org.springframework.cache.annotation.EnableCaching ;
import org.springframework.context.ApplicationContext ;
import org.springframework.context.ApplicationListener ;
import org.springframework.context.annotation.Bean ;
import org.springframework.context.annotation.Configuration ;
import org.springframework.context.annotation.Import ;
import org.springframework.context.event.ContextRefreshedEvent ;
import org.springframework.retry.annotation.EnableRetry ;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder ;
import org.springframework.security.crypto.password.PasswordEncoder ;

@Configuration
@EnableCaching
@EnableRetry
@Import ( {
		CsapInformation.class,
		CsapMicroMeter.class,
		CsapEncryptionConfiguration.class,
		CsapServiceLocator.class,
		CsapWebServerConfig.class,
		CsapPerformance.class,
		CsapDebug.class,
		CsapSecurityConfiguration.class,
		CsapOauth2SecurityConfiguration.class,
		DocumentController.class
} )
public class CsapBootConfig implements ApplicationListener<ContextRefreshedEvent> {

	final static Logger logger = LoggerFactory.getLogger( CsapBootConfig.class ) ;

	@PostConstruct
	public void showInitMessage ( ) {

	}

	@Bean
	public PasswordEncoder passwordEncoder ( ) {

		return new BCryptPasswordEncoder( ) ;

	}

	// enable http trace
	@Bean
	public HttpTraceRepository httpTraceRepository ( ) {

		if ( isHttpTraceEnabled( ) ) {

			logger.warn( CsapApplication.highlightHeader( "HttpTrace is enabled" ) ) ;

			return new InMemoryHttpTraceRepository( ) ;

		} else {

			return new HttpTraceRepository( ) {

				@Override
				public List<HttpTrace> findAll ( ) {

					// TODO Auto-generated method stub
					return null ;

				}

				@Override
				public void add ( HttpTrace trace ) {

					// TODO Auto-generated method stub

				}
			} ;

		}

	}

	public static boolean isHttpTraceEnabled ( ) {

		var runJava = new File( "/java-local/runJava.sh" ) ;
		var enableHttpTrace = new File( "/opt/csap/enable-http-trace" ) ;

		return runJava.exists( ) || enableHttpTrace.exists( ) || ! CsapApplication.isCsapFolderSet( ) ;

	}

	private StringBuffer csapInitializationMessage ;

	@Override
	public void onApplicationEvent ( ContextRefreshedEvent contextRefreshedEvent ) {

		ApplicationContext ctx = contextRefreshedEvent.getApplicationContext( ) ;
		csapInitializationMessage = new StringBuffer( ) ;

		if ( ctx.containsBean( "csapInformation" ) ) {

			csapInitializationMessage.append( ctx.getBean( CsapInformation.class ).toString( ) ) ;

		}

		if ( ctx.containsBean( "CsapDocController" ) ) {

			csapInitializationMessage.append( ctx.getBean( DocumentController.class ).toString( ) ) ;

		}

		if ( ctx.containsBean( "CsapWebServer" ) ) {

			csapInitializationMessage.append( ctx.getBean( CsapWebServerConfig.class ).toString( ) ) ;

		}

		if ( ctx.containsBean( "CsapServiceLocator" ) ) {

			csapInitializationMessage.append( ctx.getBean( CsapServiceLocator.class ).toString( ) ) ;

		}

		if ( ctx.containsBean( "CsapPerformance" ) ) {

			csapInitializationMessage.append( ctx.getBean( CsapPerformance.class ).toString( ) ) ;

		}

		if ( ctx.containsBean( CsapDebug.BEAN_NAME ) ) {

			csapInitializationMessage.append( ctx.getBean( CsapDebug.class ).toString( ) ) ;

		}

		if ( ctx.containsBean( "CsapEncryptableProperties" ) ) {

			csapInitializationMessage.append( ctx.getBean( "CsapEncryptableProperties" ).toString( ) ) ;

		}

		if ( ctx.containsBean( "CsapSecurityConfiguration" ) ) {

			csapInitializationMessage.append( ctx.getBean( "CsapSecurityConfiguration".toString( ) ) ) ;

			if ( ctx.containsBean( "CsapSecurityLoginController" ) ) {

				csapInitializationMessage.append( ctx.getBean( "CsapSecurityLoginController" ).toString( ) ) ;

			}

			if ( ctx.containsBean( "CsapSecurityRestFilter" ) ) {

				csapInitializationMessage.append( ctx.getBean( "CsapSecurityRestFilter" ).toString( ) ) ;

			}

		}

		csapInitializationMessage.append( "\n" ) ;
		logger.info( "'@CsapBootApplication settings'" + CSAP.note( csapInitializationMessage.toString( ) ) ) ;

	}

	public String getHostName ( ) {

		return HOST_NAME ;

	}

	static String HOST_NAME = "notFound" ;

	static {

		try {

			HOST_NAME = InetAddress.getLocalHost( ).getHostName( ) ;

		} catch ( UnknownHostException e ) {

			HOST_NAME = "HOST_LOOKUP_ERROR" ;

		}

	}

	public StringBuffer getCsapInitializationMessage ( ) {

		return csapInitializationMessage ;

	}

}
