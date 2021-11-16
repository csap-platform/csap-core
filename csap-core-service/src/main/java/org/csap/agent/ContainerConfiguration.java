package org.csap.agent ;

import java.time.Duration ;

import javax.cache.CacheManager ;
import javax.inject.Inject ;

import org.csap.agent.container.ContainerIntegration ;
import org.csap.agent.model.Application ;
import org.csap.agent.services.OsCommands ;
import org.csap.helpers.CSAP ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.beans.factory.annotation.Autowired ;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty ;
import org.springframework.boot.context.properties.EnableConfigurationProperties ;
import org.springframework.context.annotation.Bean ;
import org.springframework.context.annotation.Configuration ;
import org.springframework.context.annotation.Profile ;

import com.fasterxml.jackson.databind.ObjectMapper ;
import com.github.dockerjava.api.DockerClient ;
import com.github.dockerjava.core.DefaultDockerClientConfig ;
import com.github.dockerjava.core.DockerClientBuilder ;
import com.github.dockerjava.core.DockerClientConfig ;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient ;

@Configuration
@ConditionalOnProperty ( "csap-core.docker.enabled" )
@Profile ( "agent" )
@EnableConfigurationProperties ( ContainerSettings.class )
public class ContainerConfiguration {

	final Logger logger = LoggerFactory.getLogger( getClass( ) ) ;

	@Inject
	private ContainerSettings docker ; // binds to yml property

	@Autowired ( required = false )
	private CacheManager cacheManager = null ;

	@Inject
	private ObjectMapper jsonMapper ;

	@Inject
	private OsCommands osCommands ;

	@Inject
	private Application csapApp ;

	private DockerClient testClient = null ;

	@Bean
	public ContainerIntegration dockerHelper ( ) {

		ContainerIntegration helper = new ContainerIntegration( this, jsonMapper ) ;
		return helper ;

	}

	// https://github.com/spotify/docker-client/blob/master/docs/user_manual.md#creating-a-docker-client
	@Bean
	public DockerClient dockerClient ( ) {

		if ( testClient != null ) {

			return testClient ;

		}

		logger.info( docker.toString( ).replaceAll( ",", ",\n" ) ) ;

		DockerClient client = null ;

		try {

			//
			// Latest Docker Client uses apache client, but without timeouts. Class is
			// wrapped to expose
			//

			var containerUrl = docker.getUrl( ) ;

			DockerClientConfig dockerConfiguration = DefaultDockerClientConfig.createDefaultConfigBuilder( )
					.withDockerHost( containerUrl )
					.build( ) ;

			var csapApacheClient = new ApacheDockerHttpClient.Builder( )
					.dockerHost( dockerConfiguration.getDockerHost( ) )
					.sslConfig( dockerConfiguration.getSSLConfig( ) )
					.maxConnections( 100 )
					.connectionTimeout( Duration.ofSeconds( docker.getConnectionTimeoutSeconds( ) ) )
					.responseTimeout( Duration.ofSeconds( docker.getReadTimeoutSeconds( ) ) )
					.build( ) ;

//			WrapperApacheDockerHttpClientImpl csapApacheClient = new WrapperApacheDockerHttpClientImpl(
//					dockerConfiguration.getDockerHost( ),
//					dockerConfiguration.getSSLConfig( ),
//					10,
//					Timeout.ofSeconds( docker.getConnectionTimeoutSeconds( ) ),
//					Timeout.ofSeconds( docker.getReadTimeoutSeconds( ) ) ) ;

			client = DockerClientBuilder
					.getInstance( dockerConfiguration )
					.withDockerHttpClient( csapApacheClient )
					.build( ) ;

//			DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder( )
//					.withDockerHost( docker.getUrl( ) )
//					.build( ) ;
//
//			DockerCmdExecFactory dockerCmdExecFactory = new JerseyDockerCmdExecFactory( )
//					.withReadTimeout( docker.getReadTimeoutSeconds( ) * 1000 )
//					.withConnectTimeout( docker.getConnectionTimeoutSeconds( ) * 1000 )
//					;
//					//.withMaxTotalConnections( docker.getConnectionPool( ) )
//					//.withMaxPerRouteConnections( 3 ) ;
//			
//
//
//			client = DockerClientBuilder
//					.getInstance( config )
//					.withDockerCmdExecFactory( dockerCmdExecFactory )
//					.build( ) ;

			// new ApacheDockerHttpClient.Builder().

			// DockerCmdExecFactory dockerCmdExecFactory = new
			// NettyDockerCmdExecFactory()
			// .withConnectTimeout( docker.getReadTimeoutSeconds() *1000 ) ;

			// client = DefaultDockerClient.builder()
			// .uri( docker.getUrl() )
			// .connectionPoolSize( docker.getConnectionPool() )
			// .build();
		} catch ( Throwable t ) {

			logger.warn( "Failed initializing dockerClient service: {}", CSAP.buildCsapStack( t ) ) ;

		}

		return client ;

	}

	public Application getCsapApp ( ) {

		return csapApp ;

	}

	public void setCsapApp ( Application csapApp ) {

		this.csapApp = csapApp ;

	}

	public ContainerSettings getDocker ( ) {

		return docker ;

	}

	public void setDocker ( ContainerSettings docker ) {

		this.docker = docker ;

	}

	public OsCommands getOsCommands ( ) {

		return osCommands ;

	}

	public void setOsCommands ( OsCommands osCommands ) {

		this.osCommands = osCommands ;

	}

	public ObjectMapper getJsonMapper ( ) {

		return jsonMapper ;

	}

	public void setJsonMapper ( ObjectMapper jsonMapper ) {

		this.jsonMapper = jsonMapper ;

	}

	public DockerClient getTestClient ( ) {

		return testClient ;

	}

	public void setTestClient ( DockerClient testClient ) {

		this.testClient = testClient ;

	}

	public CacheManager getCacheManager ( ) {

		return cacheManager ;

	}

	public void setCacheManager ( CacheManager cacheManager ) {

		this.cacheManager = cacheManager ;

	}
}
