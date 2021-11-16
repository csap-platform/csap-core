package org.csap.agent.integration.container ;

import static org.assertj.core.api.Assertions.assertThat ;
import static org.assertj.core.api.Assertions.assertThatThrownBy ;

import java.io.ByteArrayOutputStream ;
import java.io.File ;
import java.io.IOException ;
import java.io.InputStream ;
import java.io.StringWriter ;
import java.io.Writer ;
import java.nio.charset.StandardCharsets ;
import java.nio.file.Files ;
import java.nio.file.StandardCopyOption ;
import java.time.Duration ;
import java.time.Instant ;
import java.time.LocalDateTime ;
import java.time.ZoneId ;
import java.time.format.DateTimeFormatter ;
import java.util.ArrayList ;
import java.util.Arrays ;
import java.util.HashMap ;
import java.util.List ;
import java.util.Map ;
import java.util.Objects ;
import java.util.Optional ;
import java.util.concurrent.TimeUnit ;
import java.util.stream.Collectors ;

import javax.annotation.PostConstruct ;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry ;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream ;
import org.apache.commons.io.IOUtils ;
import org.apache.commons.io.LineIterator ;
import org.apache.commons.lang3.StringUtils ;
import org.csap.agent.CsapThinTests ;
import org.csap.agent.ContainerSettings ;
import org.csap.agent.container.ContainerIntegration ;
import org.csap.helpers.CSAP ;
import org.csap.helpers.CsapApplication ;
import org.junit.jupiter.api.Assumptions ;
import org.junit.jupiter.api.BeforeAll ;
import org.junit.jupiter.api.Disabled ;
import org.junit.jupiter.api.DisplayName ;
import org.junit.jupiter.api.Tag ;
import org.junit.jupiter.api.Test ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.beans.factory.annotation.Autowired ;
import org.springframework.boot.context.properties.ConfigurationProperties ;
import org.springframework.context.ApplicationContext ;
import org.springframework.web.client.RestTemplate ;

import com.fasterxml.jackson.core.JsonProcessingException ;
import com.fasterxml.jackson.databind.node.ArrayNode ;
import com.fasterxml.jackson.databind.node.ObjectNode ;
import com.github.dockerjava.api.DockerClient ;
import com.github.dockerjava.api.async.ResultCallback ;
import com.github.dockerjava.api.async.ResultCallback.Adapter ;
import com.github.dockerjava.api.command.CreateContainerResponse ;
import com.github.dockerjava.api.command.ExecCreateCmdResponse ;
import com.github.dockerjava.api.command.InspectContainerResponse ;
import com.github.dockerjava.api.command.InspectImageResponse ;
import com.github.dockerjava.api.command.InspectVolumeResponse ;
import com.github.dockerjava.api.command.ListVolumesResponse ;
import com.github.dockerjava.api.command.PullImageResultCallback ;
import com.github.dockerjava.api.command.WaitContainerResultCallback ;
import com.github.dockerjava.api.model.AccessMode ;
import com.github.dockerjava.api.model.Bind ;
import com.github.dockerjava.api.model.Container ;
import com.github.dockerjava.api.model.ExposedPort ;
import com.github.dockerjava.api.model.Frame ;
import com.github.dockerjava.api.model.HostConfig ;
import com.github.dockerjava.api.model.Image ;
import com.github.dockerjava.api.model.Info ;
import com.github.dockerjava.api.model.LogConfig ;
import com.github.dockerjava.api.model.LogConfig.LoggingType ;
import com.github.dockerjava.api.model.Network ;
import com.github.dockerjava.api.model.Ports ;
import com.github.dockerjava.api.model.PullResponseItem ;
import com.github.dockerjava.api.model.RestartPolicy ;
import com.github.dockerjava.api.model.SELContext ;
import com.github.dockerjava.api.model.Ulimit ;
import com.github.dockerjava.api.model.Volume ;
import com.github.dockerjava.api.model.Volumes ;
import com.github.dockerjava.core.DefaultDockerClientConfig ;
import com.github.dockerjava.core.DockerClientBuilder ;
import com.github.dockerjava.core.DockerClientConfig ;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient ;

/**
 * Ensure docker is started, with port open
 *
 */
@Tag ( "containers" )
@ConfigurationProperties ( prefix = "test.junit" )
@DisplayName ( "Docker Api: github.dockerjava tests" )
public class ContainerApiTests extends CsapThinTests {

	// auto configured application-localhost.yml
	public ContainerSettings docker ;

	static DockerClient junit_docker_client = null ;
	static boolean workingDockerConnection = false ;

	final static String CONTAINER_TEST_PREFIX = "/junit-" ;

	@BeforeAll
	public void beforeAll ( )
		throws Exception {

		// starts logger, and localhost context
		CsapApplication.initialize( logger.getName( ) ) ;

		junit_docker_client = buildDockerClientWithShutdown( ) ;
		logger.info( "junit_docker_client: {}", junit_docker_client ) ;
		workingDockerConnection = runInfoCommands( "connectionTest" ) != null ;

		if ( ! workingDockerConnection ) {

			logger.warn( "Aborting docker test: verify connection information" ) ;

		}

		Assumptions.assumeTrue( workingDockerConnection ) ;

		delete_containers( CONTAINER_TEST_PREFIX ) ;

	}

	@Autowired
	private ApplicationContext spring ;

	@Test
	public void load_context ( ) {

		logger.info( "Number of Beans loaded: {}", spring.getBeanDefinitionCount( ) ) ;

		logger.debug( "beans loaded: {}", Arrays.asList( spring.getBeanDefinitionNames( ) ) ) ;

		assertThat( spring.getBeanDefinitionCount( ) )
				.as( "Spring Bean count" )
				.isLessThan( 20 ) ;

	}

	public final int dockerPoolSize = 2 ;
	// DockerCmdExecFactory dockerCmdExecFactory = buildDockerCommandFactory() ;

	public final int MAX_WAIT_TIME_MS = 60000 ;

	private DockerClient buildDockerClientWithShutdown ( ) {

		DockerClientConfig dockerConfiguration = DefaultDockerClientConfig.createDefaultConfigBuilder( )
				.withDockerHost( docker.getUrl( ) )
				.build( ) ;

		logger.warn( CSAP.buildDescription( "CUSTOM docker client",
				"host", dockerConfiguration.getDockerHost( ),
				"type", "WrapperApacheDockerHttpClientImpl",
				"poolsize", dockerPoolSize,
				"timeout", TimeUnit.MILLISECONDS.toSeconds( MAX_WAIT_TIME_MS ) + " seconds" ) ) ;

//		DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder( )
//				.dockerHost( dockerConfiguration.getDockerHost( ) )
//				.maxConnections( 5 )
//				.sslConfig( dockerConfiguration.getSSLConfig( ) )
//				.build( ) ;

//		WrapperApacheDockerHttpClientImpl csapApacheClient = new WrapperApacheDockerHttpClientImpl(
//				dockerConfiguration.getDockerHost( ),
//				dockerConfiguration.getSSLConfig( ),
//				5,
//				Timeout.ofSeconds( 10 ),
//				Timeout.ofSeconds( 10 ) ) ;
		
		var csapApacheClient = new ApacheDockerHttpClient.Builder( )
				.dockerHost( dockerConfiguration.getDockerHost( ) )
				.sslConfig( dockerConfiguration.getSSLConfig( ) )
				.maxConnections( 100 )
				.connectionTimeout( Duration.ofSeconds( docker.getConnectionTimeoutSeconds( ) ) )
				.responseTimeout( Duration.ofSeconds( docker.getReadTimeoutSeconds( ) ) )
				.build( ) ;


		DockerClient localClient = null ;

		try {

			logger.info( "Building client" ) ;
			localClient = DockerClientBuilder
					.getInstance( dockerConfiguration )
					.withDockerHttpClient( csapApacheClient )
					.build( ) ;

		} catch ( Exception e ) {

			logger.error( "Failed building client", e ) ;

		}

		return localClient ;

	}

	@Test
	public void verify_docker_info ( ) {

		junit_docker_client.pingCmd( ).exec( ) ;

		Info info = runInfoCommands( "test" ) ;
		assertThat( info.getLoggingDriver( ) )
				.as( "logging driver" )
				.isEqualTo( "json-file" ) ;

	}

	public Info runInfoCommands ( String desc ) {

		Info info = null ;

		try {

			info = junit_docker_client.infoCmd( ).exec( ) ;
			logger.info( "info {}: {}",
					desc,
					CSAP.jsonPrint( CSAP.buildGenericObjectReport( info ) ) ) ;

		} catch ( Exception e ) {

			logger.warn( "Failed info command: {}", CSAP.buildCsapStack( e ) ) ;

			try {

				junit_docker_client.close( ) ;

			} catch ( Exception e1 ) {

				logger.warn( "Failed close command: {}", CSAP.buildCsapStack( e1 ) ) ;

			}

		}

		return info ;

	}

	@Disabled
	@Test
	public void ERROR_CASE_docker_load_leaks_connections ( )
		throws Exception {

		MyPullImageHandler handler = new MyPullImageHandler( ) ;

		String imageName = BUSY_BOX ;

		/**
		 * Image pull
		 */
		junit_docker_client
				.pullImageCmd( imageName )
				.withTag( "latest" )
				// .withAuthConfig( authConfig )
				.exec( handler ).awaitCompletion( ) ;

		logger.info( "\n\n - pulled image" ) ;

		/**
		 * image save to file
		 */

		File targetFile = new File( "target/peter.tar" ) ;

		try (
				InputStream is = junit_docker_client.saveImageCmd( imageName ).exec( ); ) {

			java.nio.file.Files.copy(
					is,
					targetFile.toPath( ),
					StandardCopyOption.REPLACE_EXISTING ) ;

		} catch ( Exception e ) {

		}

		logger.info( "create targetFile: {}", targetFile.getCanonicalPath( ) ) ;
		assertThat( targetFile )
				.as( "exported image" )
				.exists( ) ;

		/**
		 * Image remove
		 */
		removeImageIfPresent( imageName ) ;

		logger.info( "Removed image: {}", imageName ) ;

		try (
				InputStream uploadStream = Files.newInputStream( targetFile.toPath( ) ) ) {

			junit_docker_client.loadImageCmd( uploadStream ).exec( ) ;

		}

		logger.info( "loaded image targetFile: {}", targetFile.getCanonicalPath( ) ) ;

		logger.warn( "loadImage leaks connections. If bypassed - this will hang on the image list" ) ;

		// assertThat( false )
		// .as( "docker java load leak" )
		// .isTrue();

		logger.info( "\n\n - loaded image" ) ;

		// lastFactory.close();
		// dockerClient = buildDockerClientWithShutdown() ;

		/**
		 * Image list
		 */
		verify_images_list( ) ;

		logger.info( "\n\n - listed image" ) ;
		// Thread.sleep( 60000 );

	}

	private void removeImageIfPresent ( String imageName ) {

		logger.info( "Removing: {}", imageName ) ;

		try {

			junit_docker_client
					.removeImageCmd( imageName )
					.withForce( true )
					.exec( ) ;

		} catch ( Exception e ) {

			logger.info( "Failed to remove: {}", CSAP.buildCsapStack( e ) ) ;

		}

		assertThat( listImages( ).toString( ) ).doesNotContain( imageName ) ;

	}

	@Test
	public void verify_container_command ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		CreateContainerResponse createResponse = startNginx( ) ;

		logger.info( "Started" ) ;

		String commandResult = containerCommand( createResponse.getId( ), "nginx", "-v" ) ;
		// commandResult = containerCommand( "/nginx", "ls", "-l" ) ;

		logger.info( "Command output: {}", commandResult ) ;
		assertThat( commandResult )
				.as( "version message" )
				.contains( "nginx version" ) ;

		logger.info( "Started success, removing: {}", createResponse.getId( ) ) ;

		removeContainer( createResponse ) ;

	}

	private String containerCommand ( String containerId , String... command ) {

		String commandOutput = "" ;

		try {

			ExecCreateCmdResponse execCreateCmdResponse = junit_docker_client
					.execCreateCmd( containerId )
					.withAttachStdout( true )
					.withAttachStderr( true )
					.withCmd( command )
					.withUser( "root" )
					.exec( ) ;

			StringBuffer lsOutputStream = new StringBuffer( ) ;
			junit_docker_client
					.execStartCmd( execCreateCmdResponse.getId( ) )
					.exec( new ResultCallback.Adapter<Frame>( ) {
						@Override
						public void onNext ( Frame item ) {

							logger.info( "frame: {}", item.toString( ) ) ;
							lsOutputStream.append( item.toString( ) ) ;

						}
					} ).awaitCompletion( 3, TimeUnit.SECONDS ) ;

			commandOutput = lsOutputStream.toString( ) ;

		} catch ( Exception e ) {

			String reason = CSAP.buildCsapStack( e ) ;
			logger.warn( "Failed listing files in {}, {}", containerId, reason ) ;

		}

		logger.debug( "Container: {}, Command: {}, output: {}", containerId, Arrays.asList( command ), commandOutput ) ;

		return commandOutput ;

	}

	final public static String TEST_CONTAINER_NAME = "/csap-java-simple" ;
	final public static String BUSY_BOX = ContainerCsapTests.BUSY_BOX ;

	@Test
	public void verify_sleep_in_container ( ) throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		var nginxCreateRespone = startNginx( ) ;

		logger.info( "Started nginx container, id: {}", nginxCreateRespone.getId( ) ) ;

		ExecCreateCmdResponse execCreateCmdResponse = junit_docker_client
				.execCreateCmd( nginxCreateRespone.getId( ) )
				.withAttachStdout( true )
				.withCmd( "ls", "/etc" )
				.withCmd( "sleep", "500" )
				.exec( ) ;

		StringBuffer sleepOutput = new StringBuffer( ) ;

		assertThatThrownBy( ( ) -> {

			junit_docker_client
					.execStartCmd( execCreateCmdResponse.getId( ) )
					.exec( new ResultCallback.Adapter<Frame>( ) {
						@Override
						public void onNext ( Frame item ) {

							logger.info( "frame: {}", item.toString( ) ) ;
							sleepOutput.append( item.toString( ) ) ;

						}
					} ).awaitCompletion( 30, TimeUnit.SECONDS ) ;

		} ).isInstanceOf( RuntimeException.class )
				.hasMessageContaining( "Read timed out" ) ;

		logger.info( "Sleep output: {}", sleepOutput ) ;

		removeContainer( nginxCreateRespone ) ;

	}

	@Test
	public void verify_docker_file_list_and_copy ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		var nginxCreateRespone = startNginx( ) ;

		logger.info( "Started nginx container, id: {}", nginxCreateRespone.getId( ) ) ;

		ExecCreateCmdResponse execCreateCmdResponse = junit_docker_client
				.execCreateCmd( nginxCreateRespone.getId( ) )
				.withAttachStdout( true )
//				.withCmd( "ls", "-al", "/etc" )
				.withCmd( "bash", "-c",
						"echo; echo root; echo ;  ls /;  echo ; echo etc; echo; echo ; ls -al /etc; sleep 20" )
				.exec( ) ;

		// StringBuffer fileListingOutput = new StringBuffer( ) ;

		ByteArrayOutputStream lsOutputStream = new ByteArrayOutputStream( ) ;

		var commandCompleted = junit_docker_client
				.execStartCmd( execCreateCmdResponse.getId( ) )
				.exec( new ResultCallback.Adapter<Frame>( ) {
					@Override
					public void onNext ( Frame item ) {

						logger.debug( "frame: {}", item.toString( ) ) ;

						try {

							lsOutputStream.write( item.getPayload( ) ) ;

						} catch ( IOException e ) {

							logger.warn( "failed writing output" ) ;

						}

					}
				} ).awaitCompletion( 3, TimeUnit.SECONDS ) ;

		String fileListingOutput = new String( lsOutputStream.toByteArray( ), StandardCharsets.UTF_8 ) ;

		logger.info( "Command completed: {} File listing: {}", commandCompleted, fileListingOutput ) ;

		assertThat( commandCompleted )
				.as( "sleep command running" )
				.isFalse( ) ;

//		ByteArrayOutputStream lsOutputStream = new ByteArrayOutputStream( ) ;
//
//		junit_docker_client
//				.execStartCmd( execCreateCmdResponse.getId( ) )
//				.exec(
//						new ExecStartResultCallback( lsOutputStream, lsOutputStream ) )
//				.awaitCompletion( ) ;
//
//		String fileListingOutput = new String( lsOutputStream.toByteArray( ), StandardCharsets.UTF_8 ) ;

		String targetFile = "/etc/bash.bashrc" ;

		assertThat( fileListingOutput )
				.as( "found:" + targetFile )
				.contains( "bash.bashrc" ) ;

		// targetFile = "/etc/fstab";
		logger.info( "Reading file {} from container", targetFile ) ;

		InputStream dockerTarStream = junit_docker_client
				.copyArchiveFromContainerCmd(
						nginxCreateRespone.getId( ),
						targetFile )
				.exec( ) ;

		// read the stream fully. Otherwise, the underlying stream will not be
		// closed.
		// String responseAsString = consumeAsString( response, 10 );
		int limit = 10 * 1024 ;
		String responseAsString = consumeAsTar( dockerTarStream, 512, limit ) ;
		// String responseAsString = consumeChunksAsString( response, 1000, 100
		// );

		logger.info( "First {} chars from {} : \n {}", limit, targetFile, responseAsString ) ;

		assertThat( responseAsString )
				.as( "found first line in " + targetFile )
				.contains( "System-wide .bashrc file" ) ;

		removeContainer( nginxCreateRespone ) ;

	}

	private void removeContainer ( CreateContainerResponse createResponse ) {

		logger.info( "removing: {}", createResponse.getId( ) ) ;

		junit_docker_client
				.removeContainerCmd( createResponse.getId( ) )
				.withForce( true )
				.exec( ) ;

		try {

			InspectContainerResponse containerStatus = junit_docker_client.inspectContainerCmd( createResponse
					.getId( ) ).exec( ) ;
			logger.warn( " container is still running: state: {}", containerStatus.getState( ) ) ;

			assertThat( true )
					.as( "container failed to be removed" )
					.isFalse( ) ;

		} catch ( Exception e ) {

			logger.info( "Container is no longer running" ) ;
			logger.debug( "Post remove Container: {}", CSAP.buildCsapStack( e ) ) ;

		}

	}

	public String consumeAsTar ( InputStream dockerTarStream , int chunkSize , int maxSize )
		throws Exception {

		StringWriter logwriter = new StringWriter( ) ;

		try ( TarArchiveInputStream tarInputStream = new TarArchiveInputStream( dockerTarStream ) ) {

			TarArchiveEntry tarEntry = tarInputStream.getNextTarEntry( ) ;

			logger.info( "tar name: {}", tarEntry.getName( ) ) ;
			byte[] bufferAsByteArray = new byte[chunkSize] ;

			long tarEntrySize = tarEntry.getSize( ) ;

			int numReadIn = 0 ;

			while ( tarInputStream.available( ) > 0 && numReadIn < maxSize && numReadIn <= tarEntrySize ) {

				int numRead = IOUtils.read( tarInputStream, bufferAsByteArray ) ;

				numReadIn += numRead ;

				String stringReadIn = new String( bufferAsByteArray, 0, numRead ) ;
				logwriter.write( stringReadIn ) ;
				logger.debug( "numRead: {}, chunk: {}", numRead, stringReadIn ) ;

			}

			while ( IOUtils.read( dockerTarStream, bufferAsByteArray ) > 0 ) {

				// need to read fully or stream will leak
			}
			// response.close();

		}

		return logwriter.toString( ) ;

	}

	public String consumeAsString ( InputStream response , int maxLines ) {

		StringWriter logwriter = new StringWriter( ) ;

		try {

			LineIterator itr = IOUtils.lineIterator( response, "UTF-8" ) ;

			int numLines = 0 ;

			while ( itr.hasNext( ) && numLines < maxLines ) {

				String line = itr.next( ) ;
				logwriter.write( line + ( itr.hasNext( ) ? "\n" : "" ) ) ;
				numLines++ ;
				System.out.println( line ) ;

				// logger.info( line);
				// LOG.info("line: " + line);
			}

			while ( itr.hasNext( ) )
				itr.next( ) ;
			// response.close();

		} catch ( Exception e ) {

			logger.error( "Failed to close: {}",
					CSAP.buildCsapStack( e ) ) ;
			return "Failed" ;

		} finally {

			IOUtils.closeQuietly( response ) ;

		}

		return logwriter.toString( ) ;

	}

	public String consumeChunksAsString ( InputStream response , int maxChars , int chunkSize ) {

		StringWriter logwriter = new StringWriter( ) ;

		byte[] bufferAsByteArray = new byte[chunkSize] ;

		try {

			int numReadIn = 0 ;

			while ( response.available( ) > 0 && numReadIn < maxChars ) {

				int numRead = IOUtils.read( response, bufferAsByteArray ) ;

				numReadIn += numRead ;

				String stringReadIn = new String( bufferAsByteArray, 0, numRead ) ;
				logwriter.write( stringReadIn ) ;
				logger.debug( "numRead: {}, chunk: {}", numRead, stringReadIn ) ;

				// LOG.info("line: " + line);
			}

			while ( IOUtils.read( response, bufferAsByteArray ) > 0 )
				;
			;
			// response.close();

		} catch ( Exception e ) {

			logger.error( "Failed to close: {}",
					CSAP.buildCsapStack( e ) ) ;
			return "Failed" ;

		} finally {

			IOUtils.closeQuietly( response ) ;

		}

		return logwriter.toString( ) ;

	}

	private String pp ( ObjectNode json ) {

		String result = json.toString( ) ;

		try {

			result = getJsonMapper( ).writerWithDefaultPrettyPrinter( ).writeValueAsString( json ) ;

		} catch ( JsonProcessingException e ) {

			// TODO Auto-generated catch block
			e.printStackTrace( ) ;

		}

		return result ;

	}

	@Test
	public void verify_volume_list ( )
		throws Exception {

		ListVolumesResponse volumeResponse = junit_docker_client.listVolumesCmd( ).exec( ) ;

		if ( volumeResponse.getVolumes( ) == null ) {

			logger.info( "No Volumes" ) ;
			return ;

		}

		volumeResponse.getVolumes( ).forEach( volume -> {

			logger.info( "volume name: {}, \t string: {} , \t\t {}",
					volume.getName( ), volume.toString( ) ) ;

			ObjectNode volumeJson = getJsonMapper( ).convertValue( volume, ObjectNode.class ) ;

			logger.info( "Json: \n {}", pp( volumeJson ) ) ;

			InspectVolumeResponse volumeInpect = junit_docker_client.inspectVolumeCmd( volume.getName( ) ).exec( ) ;
			ObjectNode inspectJson = getJsonMapper( ).convertValue( volumeInpect, ObjectNode.class ) ;

			logger.info( "inspectJson: \n {}", pp( inspectJson ) ) ;

		} ) ;

	}

	@Test
	public void verify_network_list ( )
		throws Exception {

		List<Network> networkList = junit_docker_client.listNetworksCmd( ).exec( ) ;

		if ( networkList.size( ) == 0 ) {

			logger.info( "No Networks" ) ;
			return ;

		}

		String summary = networkList
				.stream( )
				.map( network -> getJsonMapper( ).convertValue( network, ObjectNode.class ) )
				.map( networkInJson -> CSAP.jsonPrint( networkInJson ) )
				.collect( Collectors.joining( "\n" ) ) ;

		logger.info( "Network: {}", summary ) ;

		String details = networkList
				.stream( )
				.map( network -> getJsonMapper( ).convertValue( network, ObjectNode.class ) )
				.map( networkInJson -> {

					Network ni = junit_docker_client.inspectNetworkCmd( ).withNetworkId( networkInJson.get( "Id" )
							.asText( ) ).exec( ) ;
					networkInJson.set( "inspect", getJsonMapper( ).convertValue( ni, ObjectNode.class ) ) ;
					return networkInJson ;

				} )
				.map( networkInJson -> CSAP.jsonPrint( networkInJson ) )
				.collect( Collectors.joining( "\n" ) ) ;

		logger.info( "Network details: {}", details ) ;

	}

	@Test
	public void verify_network_list_using_custom_collector ( )
		throws Exception {

		ArrayNode summary = junit_docker_client
				.listNetworksCmd( ).exec( )
				.stream( )
				.map( network -> getJsonMapper( ).convertValue( network, ObjectNode.class ) )
				.collect( CSAP.Collectors.toArrayNode( ) ) ;

		logger.info( "Network: {}", CSAP.jsonPrint( summary ) ) ;

	}

	@Test
	public void verify_images_list ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		var images = junit_docker_client.listImagesCmd( ).withShowAll( true ).exec( ) ;

		var labelReport = images.stream( )
				.map( image -> {

					logger.debug( "container id: {}, \t labels: {}",
							image.getId( ), image.getLabels( ) ) ;

					ObjectNode imageJson = getJsonMapper( ).convertValue( image, ObjectNode.class ) ;

					logger.debug( "Json: \n {}", pp( imageJson ) ) ;

					InspectImageResponse imageResponse = junit_docker_client.inspectImageCmd( image.getId( ) ).exec( ) ;

					ObjectNode inspectJson = getJsonMapper( ).convertValue( imageResponse, ObjectNode.class ) ;

					logger.debug( "inspectJson: \n {}", pp( inspectJson ) ) ;

					var desc = image.getId( ) ;

					if ( image.getRepoTags( ) != null ) {

						desc = Arrays.asList( image.getRepoTags( ) ).toString( ) ;

					}

					if ( image.getRepoTags( ) != null ) {

						desc = Arrays.asList( image.getRepoTags( ) ).toString( ) ;

					} else {

						desc = "no-tags-found" ;

					}

					return CSAP.padNoLine( desc ) + image.getId( ) ;

				} )
				.sorted( )
				.collect( Collectors.joining( "\n" ) ) ;

		logger.info( "Total number of images: {}, {}", images.size( ), labelReport ) ;

	}

	@Test
	public void verify_image_pull_and_tag ( )
		throws Exception {

		// String registryName = "index.docker.io/v1";
		String imageName = BUSY_BOX ;
		// imageName = "peterdnight/demo"; // docker.io

		// imageName = CSAP_DOCKER_REPOSITORY +"/csap-simple"; //

		try {

			removeImageIfPresent( imageName ) ;

			logger.info( "Removed image: {}", imageName ) ;

		} catch ( Exception e ) {

			logger.error( "Failed to remove: {}, {}",
					imageName,
					CSAP.buildCsapStack( e ) ) ;

		}

		String imageList = listImages( ) ;
		assertThat( imageList )
				.as( imageName + "not present" )
				.doesNotContain( imageName ) ;

		// imageName = CSAP_DOCKER_REPOSITORY +"/csap-simple" ;

		// Info info = dockerClient.infoCmd().exec();
		// logger.info( "Docker Config: {}", info );

		loadImage( imageName ) ;

		// handler.awaitCompletion() ;
		listImages( ) ;
		Optional<Image> busyImage = findImageByName( imageName ) ;
		assertThat( busyImage.isPresent( ) )
				.as( imageName + "is present" )
				.isTrue( ) ;

		String now = LocalDateTime.now( ).format( DateTimeFormatter.ofPattern( "MMM.d-HH.mm.ss" ) ) ;
		String tagName = "Copy" + now ;

		junit_docker_client.tagImageCmd( busyImage.get( ).getId( ), "junit", tagName ).exec( ) ;
		listImages( ) ;

		logger.info( "Removing using id: {}", busyImage.get( ).getId( ) ) ;
		junit_docker_client
				.removeImageCmd( busyImage.get( ).getId( ) )
				.withForce( true )
				.exec( ) ;

		listImages( ) ;

	}

	private Optional<Image> findImageByName ( String imageName ) {

		List<Image> images = junit_docker_client.listImagesCmd( ).exec( ) ;

		Optional<Image> matchImage = images
				.stream( )
				.filter( image -> {

					boolean foundMatch = false ;

					if ( image.getRepoTags( ) != null ) {

						foundMatch = //
								Arrays.asList(
										image.getRepoTags( ) )
										.contains(
												ContainerIntegration.simpleImageName( imageName ) ) ;

					}

					// logger.info( "match: {}, name: {} tags: {}", foundMatch,
					// name, Arrays.asList( image.getRepoTags() ) );
					// Arrays.asList( image.getRepoTags() ).contains( name )
					return foundMatch ;

				} )
				.findFirst( ) ;
		return matchImage ;

	}

	@Test
	public void verify_image_pull ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		// String registryName = "index.docker.io/v1";
		String imageName = BUSY_BOX ;
		// imageName = "peterdnight/demo"; // docker.io

		// imageName = CSAP_DOCKER_REPOSITORY +"/csap-simple"; //

		try {

			removeImageIfPresent( imageName ) ;

			logger.info( "Removed image: {}", imageName ) ;

		} catch ( Exception e ) {

			logger.error( "Failed to remove: {}, {}",
					imageName,
					CSAP.buildCsapStack( e ) ) ;

		}

		String imageList = listImages( ) ;
		assertThat( imageList )
				.as( imageName + "not present" )
				.doesNotContain( imageName ) ;

		// imageName = CSAP_DOCKER_REPOSITORY +"/csap-simple" ;

		// Info info = dockerClient.infoCmd().exec();
		// logger.info( "Docker Config: {}", info );

		loadImage( imageName ) ;

		// handler.awaitCompletion() ;
		imageList = listImages( ) ;
		assertThat( imageList )
				.as( imageName + "is present" )
				.contains( ContainerIntegration.simpleImageName( imageName ) ) ;

	}

	private void loadImage ( String imageName ) {

		MyPullImageHandler handler = new MyPullImageHandler( ) ;

		if ( ! imageName.contains( ":" ) ) {

			logger.warn( "Appending latest to {}", imageName ) ;
			imageName += ":latest" ;

		}

		// AuthConfig authConfig = new AuthConfig()
		// .withUsername( "peterdnight" )
		// .withPassword( "xxxxx" )
		// .withEmail( "ben@me.com" )
		// .withRegistryAddress( registryName );
		// authConfig.wi

		var startTime = System.currentTimeMillis( ) ;

		try {

			boolean pullSuccess = junit_docker_client
					.pullImageCmd( imageName )
					// .withTag( "latest" )
					// .withAuthConfig( authConfig )
					.exec( handler )
					.awaitCompletion( 55, TimeUnit.SECONDS ) ;

			logger.info( "Pull Success: {}", pullSuccess ) ;

		} catch ( Exception e ) {

			logger.info( "pull status: {}", handler.getLastStatus( ) ) ;

			if ( StringUtils.isNotEmpty( handler.getLastStatus( ) ) &&
					( handler.getLastStatus( ).contains( "Image is up to date" )
							|| handler.getLastStatus( ).contains( "Downloaded newer image" ) ) ) {

				logger.info( "image is already up to date" ) ;

			} else {

				assertThat( false ).isTrue( ) ;
				logger.warn( "Failed pulling image: {}", CSAP.buildCsapStack( e ) ) ;
				logger.debug( "Full stack", e ) ;

			}

		}

		var pullSeconds = TimeUnit.MILLISECONDS.toSeconds( System.currentTimeMillis( ) - startTime ) ;
		logger.info( "Time to pull: {} ", pullSeconds ) ;
		assertThat( pullSeconds ).as( "Time to pull image: " + imageName )
				.isLessThan( TimeUnit.MILLISECONDS.toSeconds( MAX_WAIT_TIME_MS ) ) ;

	}

	public class MyPullImageHandler extends PullImageResultCallback {

		String lastStatus = null ;

		@Override
		public void onNext ( PullResponseItem item ) {

			logger.debug( "response: {} ", item ) ;

			String progress = item.getStatus( ) ;
			lastStatus = progress ;

			if ( item.getProgressDetail( ) != null && item.getProgressDetail( ).getCurrent( ) != null ) {

				progress += "..." + Math.round( item.getProgressDetail( ).getCurrent( ) * 100 / item
						.getProgressDetail( ).getTotal( ) ) + "%" ;

			}

			if ( item.getErrorDetail( ) != null ) {

				logger.debug( "Error: {}", item.getErrorDetail( ).getMessage( ) ) ;

			} else {

				logger.debug( "Progress: {}", progress ) ;

			}

		}

		public String getLastStatus ( ) {

			return lastStatus ;

		}

	}

	private void loadImageIfNeeded ( String imageName )
		throws InterruptedException {

		String currentImages = listImages( ) ;

		loadImage( imageName ) ;

		// if ( !currentImages.contains( imageName ) ) {
		// logger.info( "loading: {}", imageName );
		// loadImage( imageName );
		// }

	}

	private String listImages ( ) {

		// list them
		List<Image> images = junit_docker_client.listImagesCmd( ).exec( ) ;

		if ( images == null ) {

			logger.warn( "No images found" ) ;

		}

		String imagesMissingTags = images.stream( )
				.filter( image -> {

					if ( image.getRepoTags( ) == null ) {

						return true ;

					}

					return false ;

				} )
				.map( Image::getId )
				.collect( Collectors.joining( "\n" ) ) ;

		if ( StringUtils.isNotEmpty( imagesMissingTags ) ) {

			logger.info( "imagesMissingTags: {}", imagesMissingTags ) ;

		}

		String imageLines = images.stream( )
				.map( Image::getRepoTags )
				.filter( Objects::nonNull )
				.map( Arrays::asList )
				.map( List::toString )
				.collect( Collectors.joining( "\n" ) ) ;

		logger.info( "Current Images: {}", imageLines ) ;

		return imageLines ;

	}

//	static public class LogContainerTestCallback extends Adapter<Frame>{
//        @Override
//        public void onNext(Frame item) {
//            //sb.append(item.toString());
//        }
//		
//	}

	static public class LogContainerTestCallback extends Adapter<Frame> {
		Logger logger = LoggerFactory.getLogger( getClass( ) ) ;
		protected final StringBuffer log = new StringBuffer( ) ;

		List<Frame> collectedFrames = new ArrayList<Frame>( ) ;

		boolean collectFrames = false ;
		boolean collectLog = true ;

		Writer writer = null ;

		public LogContainerTestCallback ( ) {

			this( false ) ;

		}

		public LogContainerTestCallback ( boolean collectFrames ) {

			this.collectFrames = collectFrames ;

		}

		public LogContainerTestCallback ( Writer writer ) {

			this.writer = writer ;
			this.collectLog = false ;

		}

		@Override
		public void onNext ( Frame frame ) {

			logger.debug( "Got frame: {}", frame ) ;
			if ( collectFrames )
				collectedFrames.add( frame ) ;

			if ( collectLog ) {

				String lastLog = new String( frame.getPayload( ) ) ;
				// logger.info( "lastLog: {}", lastLog );
				log.append( lastLog ) ;

			}

			if ( writer != null ) {

				try {

					writer.write( new String( frame.getPayload( ) ) ) ;

				} catch ( IOException e ) {

					// TODO Auto-generated catch block
					e.printStackTrace( ) ;

				}

			}

			//
		}

		@Override
		public String toString ( ) {

			return log.toString( ) ;

		}

		public List<Frame> getCollectedFrames ( ) {

			return collectedFrames ;

		}
	}

	@Test
	public void verify_simple_container_create_and_start ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		String imageName = BUSY_BOX ;

		removeImageIfPresent( BUSY_BOX ) ;

		loadImageIfNeeded( imageName ) ;

		String containerName = CONTAINER_TEST_PREFIX + "busybox-"
				+ LocalDateTime.now( ).format( DateTimeFormatter.ofPattern( "MMM.d-HH.mm.ss" ) ) ;

		// ref. https://github.com/docker-java/docker-java/wiki
		ExposedPort tcp80 = ExposedPort.tcp( 80 ) ;
		List<ExposedPort> exposedList = new ArrayList<>( ) ;
		// exposedList.add( tcp80 ) ;
		// ExposedPort tcp23 = ExposedPort.tcp(23);

		Ports portBindings = new Ports( ) ;
		portBindings.bind( tcp80, Ports.Binding.bindPort( 90 ) ) ;
		// portBindings.bind( tcp80, Ports.Binding("") );

		List<String> environmentVariables = new ArrayList<>( ) ;
		environmentVariables.add( "JAVA_HOME=/opt/java" ) ;
		environmentVariables.add( "WORKING_DIR=/working" ) ;
		environmentVariables.add( "JAVA_OPTS=some path" ) ;

		List<Ulimit> ulimits = new ArrayList<>( ) ;
		ulimits.add( new Ulimit( "nofile", 1000, 1000 ) ) ;
		ulimits.add( new Ulimit( "nproc", 10, 10 ) ) ;

		Map<String, String> jsonLogConfig = new HashMap<>( ) ;
		jsonLogConfig.put( "max-size", "10m" ) ;
		jsonLogConfig.put( "max-file", "2" ) ;

		LogConfig logConfig = new LogConfig( LoggingType.JSON_FILE, jsonLogConfig ) ;

		HostConfig hostConfig = HostConfig.newHostConfig( )
				.withPortBindings( portBindings )
				.withMemory( 20 * CSAP.MB_FROM_BYTES )
				.withCpusetCpus( "0-1" )
				.withCpuPeriod( 400000L )
				.withUlimits( ulimits )
				.withLogConfig( logConfig ) ;

		CreateContainerResponse container = junit_docker_client
				.createContainerCmd( imageName )
				.withName( containerName )
				.withCmd( Arrays.asList( "ls", "-al" ) )
				// .withEntrypoint( entryParameters )

				.withHostConfig( hostConfig )
				.withExposedPorts( exposedList )

				.withHostName( "peter" )
				// .withNetworkMode( "host" )
				.withEnv( environmentVariables )
				.exec( ) ;

		InspectContainerResponse preStartInspection = getContainerStatus( containerName ) ;

		ObjectNode containerInfoJson = getJsonMapper( ).convertValue( preStartInspection, ObjectNode.class ) ;
		logger.debug( "Name: {} ,  InfoJson: \n {}", preStartInspection.getName( ), pp( containerInfoJson ) ) ;

		junit_docker_client.startContainerCmd( container.getId( ) ).exec( ) ;

		InspectContainerResponse postStartInspection = getContainerStatus( containerName ) ;

		assertThat( postStartInspection.getHostConfig( ).getCpuPeriod( ) )
				.isEqualTo( 400000L ) ;

		junit_docker_client
				.removeContainerCmd( container.getId( ) )
				.withRemoveVolumes( true )
				.withForce( true )
				.exec( ) ;

	}

	@Test
	public void verify_nginx_with_port_mappings_and_get_with_force ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		CreateContainerResponse createResponse = startNginx( ) ;

		logger.info( "Started success {}", createResponse.getId( ) ) ;

		removeContainer( createResponse ) ;

	}

	@Test
	public void verify_nginx_with_port_mappings_and_get_with_stop ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;
		CreateContainerResponse createResponse = startNginx( ) ;

		InspectContainerResponse containerState = junit_docker_client.inspectContainerCmd( createResponse.getId( ) )
				.exec( ) ;
		logger.info( "Status: '{}', id: {}", containerState.getState( ).getStatus( ), createResponse.getId( ) ) ;

		assertThat( containerState.getState( ).getStatus( ) )
				.isEqualTo( "running" ) ;

		Integer timeoutInSeconds = 20 ;
		junit_docker_client
				.stopContainerCmd( createResponse.getId( ) )
				.withTimeout( timeoutInSeconds )
				.exec( ) ;

		containerState = junit_docker_client.inspectContainerCmd( createResponse.getId( ) ).exec( ) ;
		logger.info( "Status: '{}'", containerState.getState( ).getStatus( ) ) ;
		assertThat( containerState.getState( ).getStatus( ) )
				.isEqualTo( "exited" ) ;

		junit_docker_client
				.removeContainerCmd( createResponse.getId( ) )
				.withForce( false )
				.exec( ) ;

	}

	private CreateContainerResponse startNginx ( )
		throws Exception {

		TimeUnit.SECONDS.sleep( 1 ) ;
		// String imageName = "nginx:latest" ;
		String imageName = "nginx:1.16.1" ;
		loadImageIfNeeded( imageName ) ;
		String containerName = "junit-nginx-"
				+ LocalDateTime.now( ).format( DateTimeFormatter.ofPattern( "MMM.d-HH.mm.ss" ) ) ;

		List<String> entryParameters = Arrays.asList( "nginx", "-g", "daemon off;" ) ;
		List<String> cmdParameters = Arrays.asList( "nginx", "-v" ) ;

		// ExposedPort publicPort = ExposedPort.tcp( 7079 );
		ExposedPort exposedPort = ExposedPort.tcp( 80 ) ;

		// int publicPort = SocketUtils.findAvailableTcpPort() ;
		int publicPort = 8009 ;
		logger.info( "publicPort: {}", publicPort ) ;
		Ports.Binding boundPort = Ports.Binding.bindPort( publicPort ) ;

		List<ExposedPort> exposedList = new ArrayList<>( ) ;
		exposedList.add( exposedPort ) ;

		Ports portBindings = new Ports( ) ;
		portBindings.bind(
				exposedPort,
				boundPort ) ;

		List<Volume> volumes = new ArrayList<>( ) ;
		volumes.add( new Volume( "/peter" ) ) ;

		HostConfig hostConfig = HostConfig.newHostConfig( ).withPortBindings( portBindings ) ;

		hostConfig.withPrivileged( true ) ;

		CreateContainerResponse createResponse = junit_docker_client
				.createContainerCmd( imageName )
				.withName( containerName )
				// .withCmd( cmdParameters )
				.withEntrypoint( entryParameters )
				.withExposedPorts( exposedList )
				// .withPortBindings( portBindings )
				.withHostConfig( hostConfig )
				.withVolumes( volumes )
				.exec( ) ;

		junit_docker_client
				.startContainerCmd( createResponse.getId( ) )
				.exec( ) ;

		Thread.sleep( 500 ) ;
		RestTemplate springTemplate = new RestTemplate( ) ;

		String testHost = docker.getUrl( ) ;
		testHost = testHost.substring( testHost.lastIndexOf( "/" ) + 1 ) ;
		testHost = testHost.substring( 0, testHost.indexOf( ":" ) ) ;

		String testUrl = "http://" + testHost + ":" + boundPort.getHostPortSpec( ) ;
		logger.info( "Hitting: {}", testUrl ) ;

		String response = CSAP.stripHtmlTags( springTemplate.getForObject( testUrl, String.class ) ) ;

		logger.info( "Testing url: {} \n\t response: {}", testUrl, response ) ;

		assertThat( response )
				.as( "welcome message" )
				.contains( "Welcome to nginx!" ) ;

		String nginxLogsFilter = "GET" ;

		boolean foundStartMessage = waitForMessageInLogs(
				createResponse.getId( ), 10, nginxLogsFilter ) ;

		assertThat( foundStartMessage )
				.as( "found in logs:" + nginxLogsFilter )
				.isTrue( ) ;
		return createResponse ;

	}

	private static String CSAP_DOCKER_REPOSITORY = "updateThis" ;

	@Disabled
	@Test
	public void verify_csap_base ( )
		throws Exception {

		// ref. https://github.com/docker-java/docker-java/wiki

		String containerName = "/junit-csapbase-"
				+ LocalDateTime.now( ).format( DateTimeFormatter.ofPattern( "MMM.d-HH.mm.ss" ) ) ;

		String imageName = CSAP_DOCKER_REPOSITORY + "/csap-base" ;
		// imageName = "centos";
		loadImageIfNeeded( imageName ) ;

		CreateContainerResponse containerResponse = junit_docker_client.createContainerCmd( imageName )
				.withName( containerName )
				.withWorkingDir( "/" )
				.exec( ) ;

		junit_docker_client
				.startContainerCmd( containerResponse.getId( ) )
				.exec( ) ;

		// Thread.sleep( 3000 );
		int exitCode = junit_docker_client.waitContainerCmd( containerResponse.getId( ) )
				.exec( new WaitContainerResultCallback( ) )
				.awaitStatusCode( 5, TimeUnit.SECONDS ) ;

		logger.info( "Container exited with: {}", exitCode ) ;

		LogContainerTestCallback loggingCallback = new LogContainerTestCallback( false ) ;
		junit_docker_client
				.logContainerCmd( containerResponse.getId( ) )
				.withStdErr( true )
				.withStdOut( true )
				.withTailAll( )
				.exec( loggingCallback ) ;

		loggingCallback.awaitCompletion( 3, TimeUnit.SECONDS ) ;

		logger.info( "logs: \n {} ", loggingCallback.toString( ) ) ;

		loggingCallback.close( ) ;

		assertThat( loggingCallback.toString( ) )
				.as( "csap-base logs" )
				.contains( "Java HotSpot(TM) 64-Bit Server VM" ) ;

		removeContainer( containerResponse ) ;

	}

	@Disabled
	@Test
	public void verify_csap_test_app_default ( )
		throws Exception {

		// ref. https://github.com/docker-java/docker-java/wiki

		String containerName = "/junit-csap-test-app-default"
				+ LocalDateTime.now( ).format( DateTimeFormatter.ofPattern( "MMM.d-HH.mm.ss" ) ) ;

		String imageName = CSAP_DOCKER_REPOSITORY + "/csap-test-app" ;
		// imageName = "centos";
		loadImageIfNeeded( imageName ) ;

		CreateContainerResponse containerResponse = junit_docker_client.createContainerCmd( imageName )
				.withName( containerName )
				.withWorkingDir( "/" )
				.exec( ) ;

		junit_docker_client
				.startContainerCmd( containerResponse.getId( ) )
				.exec( ) ;

		// Thread.sleep( 3000 );

		int maxAttempts = 20 ;
		String startUpMessage = "Started BootEnterpriseApplication" ;

		boolean foundStartMessage = waitForMessageInLogs(
				containerResponse.getId( ), maxAttempts, startUpMessage ) ;

		assertThat( foundStartMessage )
				.as( "found in logs:" + startUpMessage )
				.isTrue( ) ;

		logger.info( "Started success, removing: {}", containerResponse.getId( ) ) ;

		removeContainer( containerResponse ) ;

	}

	public final static int LOG_SECONDS = 5 ;

	private boolean waitForMessageInLogs ( String containerId , int maxAttempts , String startUpMessage )
		throws InterruptedException ,
		IOException {

		boolean foundStartMessage = false ;
		logger.info( "Waiting for message: '{}' in logs for container: '{}'  - up to {}  seconds...",
				startUpMessage, containerId, maxAttempts ) ;

		for ( int attempt = 1; attempt < maxAttempts; attempt++ ) {
			// completed = dockerClient.waitContainerCmd(
			// containerResponse.getId() )
			// .exec( new WaitContainerResultCallback() )
			// .awaitCompletion( 1, TimeUnit.SECONDS ) ;
			//
			// logger.info( "Container completed: {}", completed );

			Thread.sleep( 1000 ) ;

			LogContainerTestCallback loggingCallback = new LogContainerTestCallback( false ) ;

			junit_docker_client
					.logContainerCmd( containerId )
					.withStdErr( true )
					.withStdOut( true )
					.withTail( 100 )
					// Watch machine time differential
					// .withSince( DockerIntegration.logSinceSeconds( LOG_SECONDS ) )
					.exec( loggingCallback ) ;

			loggingCallback.awaitCompletion( 3, TimeUnit.SECONDS ) ;
			loggingCallback.close( ) ;

			logger.info( "attempt: {} checking last '{}' seconds :\n looking for: {} \n content:'{}'  ",
					attempt, LOG_SECONDS, startUpMessage, loggingCallback.toString( ) ) ;

			if ( loggingCallback.toString( ).contains( startUpMessage ) ) {

				foundStartMessage = true ;
				loggingCallback.close( ) ;
				break ;

			}

		}

		return foundStartMessage ;

	}

	@Disabled
	@Test
	public void verify_csap_test_app_with_limits_logs ( )
		throws Exception {

		// ref. https://github.com/docker-java/docker-java/wiki

		String containerName = "/junit-csap-test-app-custom"
				+ LocalDateTime.now( ).format( DateTimeFormatter.ofPattern( "MMM.d-HH.mm.ss" ) ) ;

		String imageName = CSAP_DOCKER_REPOSITORY + "/csap-test-app" ;
		// imageName = "centos";
		loadImageIfNeeded( imageName ) ;

		int serverPort = 8080 ;
		int publicPort = 7080 ;

		List<String> entryParameters = Arrays.asList(
				"java", "-Xms256M", "-Xmx256M",
				"-Dspring.profiles.active=embedded",
				"-DcsapJmxPort=8086",
				"-Dserver.port=" + serverPort,
				"-jar",
				"/csapTest.jar" ) ;

		List<ExposedPort> exposedList = new ArrayList<>( ) ;
		exposedList.add( ExposedPort.tcp( publicPort ) ) ;
		// ExposedPort tcp23 = ExposedPort.tcp(23);

		Ports portBindings = new Ports( ) ;
		portBindings.bind(
				ExposedPort.tcp( serverPort ),
				Ports.Binding.bindPort( publicPort ) ) ;
		// portBindings.bind( tcp80, Ports.Binding("") ); // exports as same
		// port

		List<Volumes> volumes = new ArrayList<>( ) ;
		Bind javaVolumeBind = new Bind( "/opt/test", new Volume( "/testHostVolume" ), AccessMode.ro,
				SELContext.shared ) ;

		List<String> environmentVariables = new ArrayList<>( ) ;
		environmentVariables.add( "testVar=some Var" ) ;

		List<Ulimit> ulimits = new ArrayList<>( ) ;
		ulimits.add( new Ulimit( "nofile", 1000, 1000 ) ) ;
		ulimits.add( new Ulimit( "nproc", 200, 200 ) ) ;

		Map<String, String> jsonLogConfig = new HashMap<>( ) ;
		jsonLogConfig.put( "max-size", "10m" ) ;
		jsonLogConfig.put( "max-file", "2" ) ;

		LogConfig logConfig = new LogConfig( LoggingType.JSON_FILE, jsonLogConfig ) ;

		HostConfig hostConfig = HostConfig.newHostConfig( )
				.withPortBindings( portBindings )
				.withBinds( javaVolumeBind )
				.withCpusetCpus( "0-1" )
				.withLogConfig( logConfig )
				// .withCpuPeriod( 400000 )
				.withMemory( 500 * CSAP.MB_FROM_BYTES )
				.withUlimits( ulimits ) ;

		CreateContainerResponse containerResponse = junit_docker_client
				.createContainerCmd( imageName )
				.withName( containerName )

				// .withCmd( cmdParameters )
				.withEntrypoint( entryParameters )
				.withEnv( environmentVariables )

				// resources
				.withHostConfig( hostConfig )
				// network
				.withExposedPorts( exposedList )
				// .withNetworkMode( "host" )
				.exec( ) ;

		junit_docker_client
				.startContainerCmd( containerResponse.getId( ) )
				.exec( ) ;

		// Thread.sleep( 3000 );

		int maxAttempts = 20 ;
		String startUpMessage = "Started BootEnterpriseApplication" ;

		boolean foundStartMessage = waitForMessageInLogs(
				containerResponse.getId( ), maxAttempts, startUpMessage ) ;

		assertThat( foundStartMessage )
				.as( "found in logs:" + startUpMessage )
				.isTrue( ) ;

		logger.info( "Started success, removing: {}", containerResponse.getId( ) ) ;

		removeContainer( containerResponse ) ;

	}

	@Test
	public void verify_container_with_user ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		String imageName = BUSY_BOX ;
		loadImageIfNeeded( imageName ) ;

		String containerName = CONTAINER_TEST_PREFIX + "container-with-user-"
				+ LocalDateTime.now( ).format( DateTimeFormatter.ofPattern( "MMM.d-HH.mm.ss" ) ) ;

		HostConfig hostConfig = HostConfig.newHostConfig( )
				.withRestartPolicy( RestartPolicy.onFailureRestart( 3 ) ) ;

		CreateContainerResponse createResponse = junit_docker_client.createContainerCmd( imageName )
				.withName( containerName )
				.withWorkingDir( "/" )
				.withHostConfig( hostConfig )
				.withUser( "1001:1001" ) // uid:gid
				// .withCmd( "ls", "-l" )
				.withCmd( "id" )
				.exec( ) ;

		logger.info( "Create: {}", createResponse ) ;

		junit_docker_client
				.startContainerCmd( createResponse.getId( ) )
				.exec( ) ;

		LogContainerTestCallback loggingCallback = new LogContainerTestCallback( false ) ;

		junit_docker_client
				.logContainerCmd( createResponse.getId( ) )
				.withStdErr( true )
				.withStdOut( true )
				.withTailAll( )
				.exec( loggingCallback ) ;
		//
		loggingCallback.awaitCompletion( 3, TimeUnit.SECONDS ) ;

		logger.info( "{} logs: \n {} ", containerName, loggingCallback.toString( ) ) ;

		loggingCallback.close( ) ;

		assertThat( loggingCallback.toString( ) )
				.as( "id command output" )
				.contains( "uid=1001 gid=1001" ) ;

		InspectContainerResponse containerInfo = junit_docker_client.inspectContainerCmd( createResponse.getId( ) )
				.exec( ) ;

		assertThat( containerInfo.getHostConfig( ).getRestartPolicy( ).getName( ) )
				.as( "restart policy" )
				.isEqualTo( "on-failure" ) ;

		junit_docker_client
				.removeContainerCmd( createResponse.getId( ) )
				.withRemoveVolumes( true )
				.withForce( true )
				.exec( ) ;

		logger.info( "Container removed: {}", createResponse.getId( ) ) ;

	}

	@Test
	public void verify_container_logs ( )
		throws Exception {

		String imageName = "hello-world" ;
		loadImageIfNeeded( imageName ) ;
		String containerName = "/junit-logtest-"
				+ LocalDateTime.now( ).format( DateTimeFormatter.ofPattern( "MMM.d-HH.mm.ss" ) ) ;

		CreateContainerResponse createResponse = junit_docker_client
				.createContainerCmd( imageName )
				.withName( containerName )
				.withCmd( "/hello" )
				.withTty( true )
				.exec( ) ;

		// CreateContainerResponse createResponse =
		// dockerClient.createContainerCmd( "busybox" )
		// .withCmd( "/bin/ls" )
		// .withName( containerName )
		// .exec();

		logger.info( "Create: {}", createResponse ) ;

		junit_docker_client
				.startContainerCmd( createResponse.getId( ) )
				.exec( ) ;

		int exitCode = junit_docker_client.waitContainerCmd( createResponse.getId( ) )
				.exec( new WaitContainerResultCallback( ) )
				.awaitStatusCode( 5, TimeUnit.SECONDS ) ;

		LogContainerTestCallback loggingCallback = new LogContainerTestCallback( false ) ;

		junit_docker_client
				.logContainerCmd( createResponse.getId( ) )
				.withStdErr( true )
				.withStdOut( true )
				.withTailAll( )
				.exec( loggingCallback ) ;
		//
		loggingCallback.awaitCompletion( 3, TimeUnit.SECONDS ) ;

		logger.info( "{} logs: \n {} ", containerName, loggingCallback.toString( ) ) ;

		loggingCallback.close( ) ;

		assertThat( loggingCallback.toString( ) )
				.as( "HelloWorld logs" )
				.contains( "Hello from Docker!" ) ;

		junit_docker_client
				.removeContainerCmd( createResponse.getId( ) )
				.withRemoveVolumes( true )
				.withForce( true )
				.exec( ) ;

		logger.info( "Container removed: {}", createResponse.getId( ) ) ;

	}

	@Test
	public void verify_container_logs_stream ( )
		throws Exception {

		String imageName = "hello-world" ;
		loadImageIfNeeded( imageName ) ;
		String containerName = "/junit-logtest-"
				+ LocalDateTime.now( ).format( DateTimeFormatter.ofPattern( "MMM.d-HH.mm.ss" ) ) ;

		CreateContainerResponse createResponse = junit_docker_client
				.createContainerCmd( imageName )
				.withName( containerName )
				.withCmd( "/hello" )
				.withTty( true )
				.exec( ) ;

		// CreateContainerResponse createResponse =
		// dockerClient.createContainerCmd( "busybox" )
		// .withCmd( "/bin/ls" )
		// .withName( containerName )
		// .exec();

		logger.info( "Create: {}", createResponse ) ;

		junit_docker_client
				.startContainerCmd( createResponse.getId( ) )
				.exec( ) ;

		int exitCode = junit_docker_client.waitContainerCmd( createResponse.getId( ) )
				.exec( new WaitContainerResultCallback( ) )
				.awaitStatusCode( 5, TimeUnit.SECONDS ) ;

		StringWriter sw = new StringWriter( ) ;
		LogContainerTestCallback loggingCallback = new LogContainerTestCallback( sw ) ;

		junit_docker_client
				.logContainerCmd( createResponse.getId( ) )
				.withStdErr( true )
				.withStdOut( true )
				.withTailAll( )
				.exec( loggingCallback ) ;
		//
		loggingCallback.awaitCompletion( 3, TimeUnit.SECONDS ) ;

		sw.flush( ) ;
		logger.info( "{} logs from stream: \n {} ", containerName, sw.toString( ) ) ;

		loggingCallback.close( ) ;

		assertThat( sw.toString( ) )
				.as( "HelloWorld logs" )
				.contains( "Hello from Docker!" ) ;

		junit_docker_client
				.removeContainerCmd( createResponse.getId( ) )
				.withRemoveVolumes( true )
				.withForce( true )
				.exec( ) ;

		logger.info( "Container removed: {}", createResponse.getId( ) ) ;

		loggingCallback.close( ) ;

	}

	@Test
	public void verify_containers_list ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		var containers = junit_docker_client
				.listContainersCmd( )
				.withShowAll( true )
				.exec( ) ;

//		assertThat( containers.size( ) )
//				.as( "FOund at least one container" )
//				.isGreaterThan( 0 ) ;

		containers.forEach( container -> {

			logger.info( "container id: {}, \tnames: {} , \t\t {}",
					container.getId( ), container.getNames( ), container.toString( ) ) ;

			ObjectNode containerJson = getJsonMapper( ).convertValue( container, ObjectNode.class ) ;

			logger.debug( "Json: \n {}", pp( containerJson ) ) ;

		} ) ;

	}

	@Test
	public void verify_containers_created_date ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		Optional<Container> matchContainer = findContainerByName( "/peter" ) ;

		if ( matchContainer.isPresent( ) ) {

			Container c = matchContainer.get( ) ;
			LocalDateTime date = LocalDateTime.ofInstant( Instant.ofEpochMilli( c.getCreated( ) * 1000 ), ZoneId
					.systemDefault( ) ) ;

			logger.info( "name: {}, created: {} , text: {}",
					Arrays.asList( c.getNames( ) ), c.getCreated( ),
					date.format( DateTimeFormatter.ofPattern( "HH:mm:ss MMM d, yyyy" ) ) ) ;

		}

	}

	@Test
	public void verify_containers_info ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		String imageName = BUSY_BOX ;
		loadImageIfNeeded( imageName ) ;
		String containerName = "/junit-info-"
				+ LocalDateTime.now( ).format( DateTimeFormatter.ofPattern( "MMM.d-HH.mm.ss" ) ) ;

		CreateContainerResponse createResponse = junit_docker_client.createContainerCmd( imageName )
				.withCmd( "/bin/ls" )
				.withName( containerName )
				.exec( ) ;

		logger.info( "Create: {}", createResponse ) ;

		junit_docker_client
				.startContainerCmd( createResponse.getId( ) )
				.exec( ) ;

		Optional<Container> matchContainer = findContainerByName( containerName ) ;

		assertThat( matchContainer.isPresent( ) )
				.as( "found container" )
				.isTrue( ) ;

		ObjectNode containerJson = getJsonMapper( ).convertValue( matchContainer.get( ), ObjectNode.class ) ;
		logger.info( " Json: \n {}", pp( containerJson ) ) ;

		InspectContainerResponse containerInfo = getContainerStatus( containerName ) ;

		ObjectNode containerInfoJson = getJsonMapper( ).convertValue( containerInfo, ObjectNode.class ) ;
		logger.info( "Name: {} ,  InfoJson: \n {}", containerInfo.getName( ), pp( containerInfoJson ) ) ;

		junit_docker_client
				.removeContainerCmd( createResponse.getId( ) )
				.withRemoveVolumes( true )
				.withForce( true )
				.exec( ) ;

		logger.info( "Container removed: {}", createResponse.getId( ) ) ;

	}

	private Optional<Container> findContainerByName ( String name ) {

		List<Container> containers = junit_docker_client.listContainersCmd( ).withShowAll( true ).exec( ) ;

		Optional<Container> matchContainer = containers
				.stream( )
				.filter( container -> Arrays.asList( container.getNames( ) ).contains( name ) )
				.findFirst( ) ;
		return matchContainer ;

	}

	private InspectContainerResponse getContainerStatus ( String name ) {

		Optional<Container> matchContainer = findContainerByName( name ) ;

		if ( matchContainer.isPresent( ) ) {

			return junit_docker_client.inspectContainerCmd( matchContainer.get( ).getId( ) ).exec( ) ;

		}

		return null ;

	}

	private InspectContainerResponse waitForRunning ( String containerName , boolean isRunning ) {

		InspectContainerResponse containerInfo ;

		while ( true ) {

			containerInfo = getContainerStatus( containerName ) ;
			logger.info( "Waiting for: {} current: {}", isRunning, containerInfo.getState( ).getRunning( ) ) ;

			if ( isRunning == containerInfo.getState( ).getRunning( ) ) {

				break ;

			}

			try {

				Thread.sleep( 10 ) ;

			} catch ( InterruptedException e ) {

				// TODO Auto-generated catch block
				e.printStackTrace( ) ;

			}

		}

		return containerInfo ;

	}

	@Disabled
	@Test
	public void verify_containers_stop ( )
		throws Exception {

		String containerName = "/peter" ;
		Optional<Container> matchContainer = findContainerByName( containerName ) ;

		if ( matchContainer.isPresent( ) ) {

			Container container = matchContainer.get( ) ;

			InspectContainerResponse containerInfo = getContainerStatus( containerName ) ;

			// containerInfo.

			logger.info( "Running: {} status: {}", containerInfo.getState( ).getRunning( ), container.getStatus( ) ) ;

			junit_docker_client.stopContainerCmd( matchContainer.get( ).getId( ) ).exec( ) ;
			containerInfo = waitForRunning( containerName, false ) ;

			logger.info( "Running: {} status: {}", containerInfo.getState( ).getRunning( ), container.getStatus( ) ) ;

		}

		;

	}

	@Disabled
	@Test
	public void verify_containers_start ( )
		throws Exception {

		String containerName = "/peter" ;
		Optional<Container> matchContainer = findContainerByName( containerName ) ;

		if ( matchContainer.isPresent( ) ) {

			Container container = matchContainer.get( ) ;

			InspectContainerResponse containerInfo = getContainerStatus( containerName ) ;

			// containerInfo.

			logger.info( "Running: {} status: {}", containerInfo.getState( ).getRunning( ), container.getStatus( ) ) ;

			junit_docker_client.startContainerCmd( matchContainer.get( ).getId( ) ).exec( ) ;

			containerInfo = waitForRunning( containerName, true ) ;

			logger.info( "Running: {} status: {}",
					containerInfo.getState( ).getRunning( ),
					findContainerByName( containerName ).get( ).getStatus( ) ) ;

		}

		;

	}

	@Disabled
	@Test
	public void verify_containers_stop_and_start ( )
		throws Exception {

		verify_containers_stop( ) ;
		verify_containers_start( ) ;

	}

	public ContainerSettings getDocker ( ) {

		return docker ;

	}

	public void setDocker ( ContainerSettings docker ) {

		this.docker = docker ;

	}

	public void delete_containers ( String prefix )
		throws Exception {

		List<Container> containers = junit_docker_client.listContainersCmd( ).withShowAll( true ).exec( ) ;

		// containers.stream().filter( container -> container.getNames() ) ;

		containers.forEach( container -> {

			List<String> names = Arrays.asList( container.getNames( ) ) ;
			Optional<String> matchedPrefix = names.stream( ).filter( name -> name.startsWith( prefix ) ).findFirst( ) ;

			if ( matchedPrefix.isPresent( ) ) {

				logger.info( "container {} did  match: {} === DELETING", names, prefix ) ;
				junit_docker_client
						.removeContainerCmd( container.getId( ) )
						.withRemoveVolumes( true )
						.withForce( true )
						.exec( ) ;

			} else {

				logger.info( "container {} did not match: {} ", names, prefix ) ;

			}

		} ) ;

	}

	@PostConstruct
	void printVals ( ) {

	}

}
