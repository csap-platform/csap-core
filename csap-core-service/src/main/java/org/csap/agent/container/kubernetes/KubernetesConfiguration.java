package org.csap.agent.container.kubernetes ;

import java.io.File ;
import java.util.concurrent.TimeUnit ;

import javax.inject.Inject ;

import org.csap.agent.CsapApis ;
import org.csap.helpers.CSAP ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.beans.factory.annotation.Autowired ;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty ;
import org.springframework.boot.context.properties.EnableConfigurationProperties ;
import org.springframework.context.annotation.Bean ;
import org.springframework.context.annotation.Configuration ;
import org.springframework.context.annotation.Lazy ;
import org.springframework.context.annotation.Profile ;

import com.fasterxml.jackson.databind.ObjectMapper ;

import io.kubernetes.client.openapi.ApiClient ;
import io.kubernetes.client.util.Config ;
import okhttp3.ConnectionPool ;
import okhttp3.OkHttpClient ;

@Configuration
@ConditionalOnProperty ( "csap-core.kubernetes.enabled" )
@Profile ( "agent" )
@EnableConfigurationProperties ( KubernetesSettings.class )
public class KubernetesConfiguration {

	final Logger logger = LoggerFactory.getLogger( getClass( ) ) ;

	@Inject
	private KubernetesSettings kubernetesSettings ; // binds to yml property

	@Inject
	private ObjectMapper jsonMapper ;

	// private Application csapApp ;
	@Lazy
	@Autowired ( required = false )
	CsapApis csapApis ;

	@Bean
	public KubernetesIntegration kubernetesIntegration ( ) {

		buildApiClient( ) ;
		KubernetesIntegration integration = new KubernetesIntegration( kubernetesSettings, this, jsonMapper ) ;
		return integration ;

	}

	public ApiClient apiClient ( ) {

		return kubernetesApiPooledClient ;

	}

	// need to dynamically reload when cluster redeployed
	ApiClient kubernetesApiPooledClient = null ;

	int attemptsForTesting = 0 ;

	public ApiClient buildApiClient ( ) {

		if ( kubernetesApiPooledClient != null ) {

//			client.getHttpClient( ).
			try {

				kubernetesApiPooledClient.getHttpClient( ).connectionPool( ).evictAll( ) ;

			} catch ( Exception e ) {

				logger.warn( "Failed evicting connections", CSAP.buildCsapStack( e ) ) ;

			}

		}

		try {

			File configFile = new File( kubernetesSettings.getConfigFile( ) ) ;
			logger.debug("loading configuration: {}", configFile) ;

			if ( ! configFile.exists( ) ) {

				var warningMessage = "skipping kubernetes configuration\n\t settings file does not exist: {}" ;
				logger.warn( warningMessage, kubernetesSettings.getConfigFile( ) ) ;

				return null ;

			}

			logger.info( kubernetesSettings.toString( ) ) ;

			kubernetesApiPooledClient = Config.fromConfig( kubernetesSettings.getConfigFile( ) ) ;

			// Customize timeouts
			OkHttpClient httpClientclient = kubernetesApiPooledClient.getHttpClient( ).newBuilder( )
					.connectTimeout( kubernetesSettings.getConnectionTimeOutInMs( ), TimeUnit.MILLISECONDS )
					.writeTimeout( kubernetesSettings.getMaxSessionSeconds( ), TimeUnit.SECONDS )
					.readTimeout( kubernetesSettings.getMaxSessionSeconds( ), TimeUnit.SECONDS )
					.retryOnConnectionFailure( false )
					.connectionPool( new ConnectionPool(
							kubernetesSettings.getConnectionPoolIdleConnections( ),
							kubernetesSettings.getConnectionPoolIdleMinutes( ),
							TimeUnit.MINUTES ) )
					.build( ) ;
			kubernetesApiPooledClient.setHttpClient( httpClientclient ) ;

//			var timeoutMs = kubernetesSettings.getConnectionTimeOutInMs( ) ;
//			client.setConnectTimeout( timeoutMs ) ;
			// client.setDebugging( true ) ;

			if ( logger.isDebugEnabled( ) ) {

				kubernetesApiPooledClient.setDebugging( true ) ;

			}

		} catch ( Exception e ) {

			logger.error( "Failed to build kubernetes api: {}",
					CSAP.buildCsapStack( e ) ) ;

		}

		return kubernetesApiPooledClient ;

	}

	public KubernetesSettings getKubernetesSettings ( ) {

		return kubernetesSettings ;

	}

	public CsapApis getCsapApis ( ) {

		return csapApis ;

	}

	public void setCsapApi ( CsapApis csapApis ) {

		this.csapApis = csapApis ;

	}

}
