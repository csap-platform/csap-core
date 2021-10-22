package org.csap.agent.integration.container ;

import static org.assertj.core.api.Assertions.assertThat ;

import java.io.File ;
import java.util.Arrays ;
import java.util.List ;
import java.util.Optional ;
import java.util.concurrent.Executors ;
import java.util.concurrent.TimeUnit ;
import java.util.concurrent.atomic.AtomicInteger ;
import java.util.stream.Collectors ;

import org.apache.hc.core5.util.Timeout ;
import org.csap.agent.CsapThinTests ;
import org.csap.agent.DockerConfiguration ;
import org.csap.agent.DockerSettings ;
import org.csap.agent.container.ContainerIntegration ;
import org.csap.agent.container.ContainerProcess ;
import org.csap.agent.container.DockerJson ;
import org.csap.agent.container.WrapperApacheDockerHttpClientImpl ;
import org.csap.agent.container.kubernetes.KubernetesJson ;
import org.csap.agent.model.ServiceAttributes ;
import org.csap.agent.model.ServiceInstance ;
import org.csap.helpers.CSAP ;
import org.csap.helpers.CsapApplication ;
import org.junit.jupiter.api.Assumptions ;
import org.junit.jupiter.api.BeforeAll ;
import org.junit.jupiter.api.BeforeEach ;
import org.junit.jupiter.api.DisplayName ;
import org.junit.jupiter.api.Tag ;
import org.junit.jupiter.api.Test ;
import org.springframework.beans.factory.annotation.Autowired ;
import org.springframework.boot.context.properties.ConfigurationProperties ;
import org.springframework.context.ApplicationContext ;

import com.fasterxml.jackson.databind.JsonNode ;
import com.fasterxml.jackson.databind.node.ArrayNode ;
import com.fasterxml.jackson.databind.node.ObjectNode ;
import com.github.dockerjava.api.model.Container ;
import com.github.dockerjava.core.DefaultDockerClientConfig ;
import com.github.dockerjava.core.DockerClientBuilder ;

@Tag ( "containers" )
@ConfigurationProperties ( prefix = "test.junit" )
@DisplayName ( "Docker Api: org.csap tests" )
class DockerCsapTests extends CsapThinTests {

	ContainerIntegration csapDocker ;

	DockerSettings docker ; // injected via: application-localhost.yml

	@Autowired
	ApplicationContext springContext ;

	@BeforeEach
	void beforeMethod ( ) {

		// Guard for setup

		Assumptions.assumeTrue( docker.isEnabled( ) ) ;
		Assumptions.assumeTrue( docker.getUrl( ) != null ) ;

	}

	@BeforeAll
	public void beforeAll ( )
		throws Exception {

		Assumptions.assumeTrue( docker.isEnabled( ) ) ;
		create_docker_connection( ) ;

	}

	void create_docker_connection ( ) {

		Assumptions.assumeTrue( docker != null && docker.getUrl( ) != null ) ;

		logger.info( CsapApplication.testHeader( "{}" ), getDocker( ) ) ;

		var dockerConfiguration = DefaultDockerClientConfig.createDefaultConfigBuilder( )
				.withDockerHost( getDocker( ).getUrl( ) )
				.build( ) ;

		//
		// Latest Docker Client uses apache client, but without timeouts. Class is
		// wrapped to expose
		//

		var csapApacheClient = new WrapperApacheDockerHttpClientImpl(
				dockerConfiguration.getDockerHost( ),
				dockerConfiguration.getSSLConfig( ),
				10,
				Timeout.ofSeconds( docker.getConnectionTimeoutSeconds( ) ),
				Timeout.ofSeconds( docker.getReadTimeoutSeconds( ) ) ) ;

		var dockerClient = DockerClientBuilder
				.getInstance( dockerConfiguration )
				.withDockerHttpClient( csapApacheClient )
				.build( ) ;

//		DockerCmdExecFactory dockerCmdExecFactory = new JerseyDockerCmdExecFactory( )
//				.withReadTimeout( docker.getReadTimeoutSeconds( ) * 1000 )
//				.withConnectTimeout( docker.getConnectionTimeoutSeconds( ) * 1000 )
//				.withMaxTotalConnections( docker.getConnectionPool( ) )
//				.withMaxPerRouteConnections( 3 ) ;
//
//		DockerCmdExecFactory dockerCmdExecFactory = new JerseyDockerCmdExecFactory( )
//				.withReadTimeout( docker.getReadTimeoutSeconds( ) * 1000 )
//				.withConnectTimeout( docker.getConnectionTimeoutSeconds( ) * 1000 )
//				.withMaxTotalConnections( docker.getConnectionPool( ) )
//				.withMaxPerRouteConnections( 3 ) ;
//
//		// DockerCmdExecFactory dockerCmdExecFactory = new
//		// NettyDockerCmdExecFactory()
//		// .withConnectTimeout( docker.getReadTimeoutSeconds() *1000 ) ;
//
//		DockerClient dockerClient = DockerClientBuilder
//				.getInstance( config )
//				.withDockerCmdExecFactory( dockerCmdExecFactory )
//				.build( ) ;

		// csapDocker = new DockerIntegration( docker, dockerClient, getJsonMapper(),
		// null ) ;
		DockerConfiguration dockerConfig = new DockerConfiguration( ) ;
		dockerConfig.setDocker( docker ) ;
		dockerConfig.setCsapApp( getApplication( ) ) ;
		dockerConfig.setTestClient( dockerClient ) ;
		csapDocker = new ContainerIntegration( dockerConfig, getJsonMapper( ) ) ;
		Assumptions.assumeTrue( csapDocker.buildSummary( ).path( KubernetesJson.heartbeat.json( ) ).asBoolean( ) ) ;

	}

	@Test
	public void load_context ( ) {

		logger.info( CsapApplication.testHeader( ) ) ;

		logger.info( "Number of Beans loaded: {}", springContext.getBeanDefinitionCount( ) ) ;

		logger.debug( "beans loaded: {}", Arrays.asList( springContext.getBeanDefinitionNames( ) ) ) ;

		assertThat( springContext.getBeanDefinitionCount( ) )
				.as( "Spring Bean count" )
				.isLessThan( 30 ) ;

		logger.info( " docker: {}", docker ) ;

		assertThat( docker ).as( "docker settings loaded" ).isNotNull( ) ;

	}

	@Test
	public void verify_docker_connection ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		ObjectNode dockerSummery = csapDocker.buildSummary( ) ;

		logger.info( "dockerSummery:  \n {}",
				CSAP.jsonPrint( dockerSummery ) ) ;

		assertThat( dockerSummery.get( KubernetesJson.heartbeat.json( ) ).asBoolean( ) )
				.as( "No errors in listing" )
				.isTrue( ) ;

	}

	@Test
	@DisplayName ( "process builder" )
	public void verify_container_process_listing ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		List<ContainerProcess> containerListing = csapDocker.build_process_info_for_containers( ) ;

		logger.info( "containerListing - count: '{}' \n {}",
				containerListing.size( ),
				CSAP.jsonPrint( getJsonMapper( ).convertValue( containerListing, ArrayNode.class ) ) ) ;

		assertThat( containerListing.size( ) )
				.as( "No errors in listing" )
				.isGreaterThanOrEqualTo( 1 ) ;

	}

	@Test
	@DisplayName ( "container listing" )
	public void verify_container_listing ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		ArrayNode containerListing = csapDocker.containerListing( false ) ;

		logger.info( "containerListing - count: '{}' \n {}",
				containerListing.size( ),
				CSAP.jsonPrint( containerListing ) ) ;

		logger.info( "containerListing - count: '{}' ",
				containerListing.size( ) ) ;

		assertThat( containerListing.get( 0 ).has( "error" ) )
				.as( "No errors in listing" )
				.isFalse( ) ;

	}

	final public static String BUSY_BOX = "busybox:1.32.0" ;

	@Test
	public void verify_missing_image_pull ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		String testImageName = "missing/image/test" ;

		// CSAP.setLogToDebug( DockerIntegration.class.getName() ) ;
		ObjectNode pullResults = csapDocker.imagePull( testImageName, null, 60, 3 ) ;
		CSAP.setLogToInfo( ContainerIntegration.class.getName( ) ) ;

		logger.info( "pullResults: {}", CSAP.jsonPrint( pullResults ) ) ;

		assertThat( pullResults.path( "error" ).asText( ) ).isEqualTo( "Failed pulling: missing/image/test:latest" ) ;

	}

	//
	@Test
	public void verify_large_image_pull_with_multiple_attempts ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		var testImageName = "mysql:8.0.22" ;
		// testImageName = "dpage/pgadmin4:4.15" ;

		JsonNode removeResults = csapDocker.imageRemove( true, null, testImageName ) ;
		logger.info( "remove results: \n {}", CSAP.jsonPrint( removeResults ) ) ;

		// CSAP.setLogToDebug( DockerIntegration.class.getName() ) ;
		ObjectNode pullResults = csapDocker.imagePull( testImageName, null, 60, 5 ) ;
		CSAP.setLogToInfo( ContainerIntegration.class.getName( ) ) ;

		logger.info( "pullResults: \n {}", CSAP.jsonPrint( pullResults ) ) ;

		assertThat( pullResults.path( "isComplete" ).asBoolean( ) ).isTrue( ) ;

	}

	@Test
	@DisplayName ( "large image pull with insufficient time" )
	public void verify_large_image_pull_with_insufficient_time ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		String testImageName = "jboss/keycloak:8.0.1" ;

		JsonNode removeResults = csapDocker.imageRemove( true, null, testImageName ) ;
		logger.info( "remove results: \n {}", CSAP.jsonPrint( removeResults ) ) ;

		var pullStatusExecutor = Executors.newScheduledThreadPool( 1 ) ;
		AtomicInteger offset = new AtomicInteger( 0 ) ;
		pullStatusExecutor.scheduleWithFixedDelay( ( ) -> {

			var lastResults = csapDocker.getLastResults( offset.get( ) ) ;
			logger.info( lastResults ) ;
			offset.set( lastResults.length( ) ) ;

		}, 1, 1, TimeUnit.SECONDS ) ;

		// CSAP.setLogToDebug( DockerIntegration.class.getName() ) ;
		ObjectNode pullResults = csapDocker.imagePull( testImageName, null, 5, 2 ) ;
		CSAP.setLogToInfo( ContainerIntegration.class.getName( ) ) ;

		logger.info( "pullResults: \n {}", CSAP.jsonPrint( pullResults ) ) ;

		assertThat( pullResults.path( "isComplete" ).asBoolean( ) ).isFalse( ) ;

		// TimeUnit.SECONDS.sleep( 20 );

//		ObjectNode pullAgainResults = csapDocker.imagePull( testImageName, null, 10, 2 ) ;
//		
//		logger.info( "pullAgainResults: \n {}", CSAP.jsonPrint( pullAgainResults ) ) ;
//
//		assertThat( pullAgainResults.path( "reason" ).asText( ) )
//				.contains( "Docker pull already in progress" ) ;
//
//		csapDocker.imageRemove( true, null, testImageName ) ;

		pullStatusExecutor.shutdown( ) ;

	}

	@Test
	public void verify_image_pull_list_and_remove ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		JsonNode removeResults = csapDocker.imageRemove( true, null, BUSY_BOX ) ;
		logger.info( "busybox: \n {}", CSAP.jsonPrint( removeResults ) ) ;

		ObjectNode pullResults = csapDocker.imagePull( BUSY_BOX, null, 60, 3 ) ;

		logger.info( "busybox pullResults: \n {}", CSAP.jsonPrint( pullResults ) ) ;

		Optional<JsonNode> optionalImage = searchForTestImage( ) ;

		logger.info( "busybox: \n {}",
				CSAP.jsonPrint(
						optionalImage.get( )
								.get(
										DockerJson.list_label.json( ) ) ) ) ;

		assertThat( optionalImage.isPresent( ) )
				.as( "Found busybox" )
				.isTrue( ) ;

		// try again to verify alternate path
		ObjectNode pullAgainResults = csapDocker.imagePull( BUSY_BOX, null, 60, 1 ) ;

		removeResults = csapDocker.imageRemove( true, null, BUSY_BOX ) ;
		logger.info( "busybox: \n {}", CSAP.jsonPrint( removeResults ) ) ;
		assertThat( removeResults.has( DockerJson.error.json( ) ) )
				.as( "Found busybox" )
				.isFalse( ) ;

		optionalImage = searchForTestImage( ) ;

		assertThat( optionalImage.isPresent( ) )
				.as( "Found busybox" )
				.isFalse( ) ;

	}

	private Optional<JsonNode> searchForTestImage ( ) {

		ArrayNode imagesWithDetails = csapDocker.imageListWithDetails( true ) ;

		Optional<JsonNode> bzMatch = CSAP.jsonStream( imagesWithDetails )
				.filter( imageJson -> {

					return imageJson.get(
							DockerJson.list_label.json( ) ).asText( )
							// ignore leading source which may be ommitted
							.contains( ContainerIntegration.simpleImageName( BUSY_BOX ) ) ;

				} )
				.findFirst( ) ;
		return bzMatch ;

	}

	@Test
	public void verify_hello_world_deploy_and_start ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		String testImageName = "hello-world" ;

		CSAP.setLogToDebug( ContainerIntegration.class.getName( ) ) ;
		ObjectNode pullResults = csapDocker.imagePull( testImageName, null, 60, 3 ) ;
		CSAP.setLogToInfo( ContainerIntegration.class.getName( ) ) ;

		logger.info( "Image pulled: {}", CSAP.jsonPrint( pullResults ) ) ;
		assertThat( pullResults.path( DockerJson.error.json( ) ).asBoolean( ) ).as( "no errors during pull" )
				.isFalse( ) ;

		ObjectNode imageInfo = csapDocker.imageInfo( pullResults.path( "image" ).asText( ) ) ;

		logger.info( "Image pulled: {}", CSAP.jsonPrint( imageInfo ) ) ;

		assertThat( imageInfo.has( DockerJson.error.json( ) ) ).as( "no errors during pull" ).isFalse( ) ;
		assertThat( imageInfo.path( "Config" ).isObject( ) ).as( "Found cofiguration" ).isTrue( ) ;

		ServiceInstance service = new ServiceInstance( ) ;

		String serviceName = "hello-world" ;

		service.setName( serviceName ) ;
		service.setPort( "0" ) ;

		JsonNode removeResults = csapDocker.containerRemove(
				null,
				service.getDockerContainerPath( ),
				true ) ;

		logger.info( "Remove results: \n {}",
				CSAP.jsonPrint( removeResults ) ) ;

		ObjectNode createAndStartResults = csapDocker.containerCreateAndStart( service, getJsonMapper( )
				.createObjectNode( ) ) ;

		logger.info( "createAndStartResults: \n {}", CSAP.jsonPrint( createAndStartResults ) ) ;

		assertThat( createAndStartResults.has( DockerJson.error.json( ) ) ).as( "no errors during deploy" ).isFalse( ) ;

		// assertThat( createAndStartResults.at( "/startResults/state/Running"
		// ).asBoolean() )
		// .as( "no errors during deploy" )
		// .isTrue() ;

		ObjectNode tailResults = csapDocker.containerTail( null, service.getDockerContainerPath( ), 500, 1 ) ;

		logger.info( "tailResults: \n {}", CSAP.jsonPrint( tailResults ) ) ;

		assertThat( tailResults.at( "/plainText" ).asText( ) )
				.as( "no errors during deploy" )
				.contains( "Hello from Docker!" ) ;

		JsonNode removeAfterResults = csapDocker.containerRemove(
				null,
				service.getDockerContainerPath( ),
				true ) ;

		logger.info( "Remove results: \n {}",
				CSAP.jsonPrint( removeAfterResults ) ) ;

	}

	@Test
	public void verify_find_using_docker_label ( )
		throws Exception {

		String kubernetes_service = "etcd" ; // kubernetes-dashboard,
												// kubernetes-apiserver,
												// etcd
		String searchLabel = "io.kubernetes.container.name" ;
		Optional<Container> container = csapDocker.findContainerUsingLabel( searchLabel, kubernetes_service ) ;
		Assumptions.assumeTrue( container.isPresent( ) ) ;

		ObjectNode tailResults = csapDocker.containerTail( null, container.get( ).getNames( )[ 0 ], 10, 1 ) ;

		logger.info( "tailResults: \n {}", CSAP.jsonPrint( tailResults ) ) ;

		assertThat( tailResults.at( "/info" ).asText( ) )
				.as( "no errors during deploy" )
				.contains( "View logs:" ) ;

	}

	@Test
	public void verify_timezone ( )
		throws Exception {

		// ,\"TZ=US/Eastern\" will be added by start
		String environmentVariables = "[\"testVar=testVal\"]" ;

		csapDocker.imagePull( ContainerIntegration.DOCKER_DEFAULT_IMAGE, null, 60, 3 ) ;

		ServiceInstance service = new ServiceInstance( ) ;

		String serviceName = "SimpleName" ;

		service.setName( serviceName ) ;

		// Running remove in case of an orphaned instance
		JsonNode removeResults = csapDocker.containerRemove(
				null,
				service.getDockerContainerPath( ),
				true ) ;

		ObjectNode defn = getJsonMapper( ).createObjectNode( ) ;
		defn.set( "environmentVariables", getJsonMapper( ).readTree( environmentVariables ) ) ;

		ObjectNode results = csapDocker.containerCreateAndStart( service, defn ) ;

		logger.info( "results: \n {}", CSAP.jsonPrint( results ) ) ;

		assertThat( results.has( DockerJson.error.json( ) ) ).as( "no errors during deploy" ).isFalse( ) ;

		assertThat( results.at( "/startResults/state/Running" ).asBoolean( ) )
				.as( "no errors during deploy" )
				.isTrue( ) ;

		assertThat( results.at( "/container/Config/Env" ).toString( ) )
				.as( "no errors during deploy" )
				.contains( "TZ" ) ;

		removeResults = csapDocker.containerRemove(
				null,
				service.getDockerContainerPath( ),
				true ) ;

		assertThat( removeResults.has( DockerJson.error.json( ) ) ).as( "no errors during deploy" ).isFalse( ) ;

		logger.info( "Remove results: \n {}",
				CSAP.jsonPrint( removeResults ) ) ;

	}

	@Test
	public void verify_volume_create_with_errors ( )
		throws Exception {

		String volumes = CSAP.jsonStream( csapDocker.volumeList( ) )
				.map( network -> network.path( DockerJson.list_label.json( ) ).asText( ) )
				.collect( Collectors.joining( "\n" ) ) ;

		logger.info( "Current volume list: {}", volumes ) ;
		final String VOLUME_WITH_INVALID_PATH = "/VolumeTest" ;

		ObjectNode errorCreateResponse = csapDocker.volumeCreate(
				VOLUME_WITH_INVALID_PATH,
				"local",
				false ) ;
		logger.info( "Invalid path response: \n {}", CSAP.jsonPrint( errorCreateResponse ) ) ;

		assertThat( errorCreateResponse.has( DockerJson.error.json( ) ) )
				.as( "Failed to add invalid volume" )
				.isTrue( ) ;

		assertThat( errorCreateResponse.get( DockerJson.errorReason.json( ) ).asText( ) )
				.as( "Failed to add invalid volume" )
				.contains( "BadRequestException", "includes invalid characters for a local volume name" ) ;

		// errorCreateResponse = dockerHelper.volumeCreate(
		// VOLUME_WITH_INVALID_PATH,
		// "unknownDriver",
		// false );
		//
		// logger.info( "Unkown driver response: \n {}", CSAP.jsonPrint(
		// errorCreateResponse ) );
	}

	@Test
	public void verify_volume_listing_create_remove ( )
		throws Exception {

		String volumes = CSAP.jsonStream( csapDocker.volumeList( ) )
				.map( network -> network.path( DockerJson.list_label.json( ) ).asText( ) )
				.collect( Collectors.joining( "\n" ) ) ;

		logger.info( "Current volume list: {}", volumes ) ;
		final String VOLUME_TEST = "VolumeTest" ;

		ObjectNode volCreateResponse = csapDocker.volumeCreate( VOLUME_TEST, "local", true ) ;
		logger.info( "volCreateResponse: \n {}", CSAP.jsonPrint( volCreateResponse ) ) ;

		assertThat( volCreateResponse.has( "Mountpoint" ) ).as( "mount point added" ).isTrue( ) ;

		volCreateResponse = csapDocker.volumeCreate( VOLUME_TEST, "local", true ) ;
		logger.info( "volCreateResponse: \n {}", CSAP.jsonPrint( volCreateResponse ) ) ;

		assertThat( volCreateResponse.has( DockerJson.response_info.json( ) ) )
				.as( "Volume skipped" )
				.isTrue( ) ;

		assertThat( volCreateResponse.get( DockerJson.response_info.json( ) ).asText( ) )
				.as( "Volume skipped" )
				.isEqualTo( ContainerIntegration.SKIPPING_VOLUME_CREATE ) ;

		ObjectNode volDeleteResponse = csapDocker.volumeDelete( VOLUME_TEST ) ;
		logger.info( "volDeleteResponse: \n {}", CSAP.jsonPrint( volDeleteResponse ) ) ;

		assertThat( volDeleteResponse.has( DockerJson.response_volume_list.json( ) ) )
				.as( "Volume delete list of remaining volumes" )
				.isTrue( ) ;

		assertThat( volDeleteResponse.get( DockerJson.response_volume_list.json( ) ).toString( ) )
				.as( "Volume  list of remaining volumes" )
				.doesNotContain( VOLUME_TEST ) ;

	}

	@Test
	public void verify_network_list_add_remove ( )
		throws Exception {

		final String NETWORK_TEST = "NetworkTest" ;

		logger.info( "networks: {}", CSAP.jsonPrint( csapDocker.networkList( ) ) ) ;

		logger.info( "network names: {}", csapDocker.networkNames( ) ) ;

		String networks = CSAP.jsonStream( csapDocker.networkList( ) )
				.map( ( network ) -> {

					return network.path( DockerJson.list_label.json( ) ).asText( ) +
							", Scope:" + network.at( "/" + DockerJson.list_attributes.json( ) + "/Scope" ).asText( ) ;

				} )
				.collect( Collectors.joining( "\t" ) ) ;

		logger.info( "networks: {}", networks ) ;

		ObjectNode network_created_response = (ObjectNode) csapDocker.networkCreate( NETWORK_TEST, "bridge", true ) ;
		logger.info( "diskCreateResponse: \n {}", CSAP.jsonPrint( network_created_response ) ) ;

		assertThat( network_created_response.has( "Id" ) ).as( "Id found for new network" ).isTrue( ) ;

		assertThat( csapDocker.networkNames( ) )
				.as( "Id found for new network" )
				.contains( NETWORK_TEST ) ;

		ObjectNode networkCreateSkippedResponse = (ObjectNode) csapDocker.networkCreate( NETWORK_TEST, "bridge",
				true ) ;
		logger.info( "diskCreateResponse: \n {}", CSAP.jsonPrint( network_created_response ) ) ;
		assertThat( networkCreateSkippedResponse.get( DockerJson.response_info.json( ) ).asText( ) )
				.as( "network skipped" )
				.isEqualTo( ContainerIntegration.SKIPPING_NETWORK_CREATE ) ;

		// CSAP.jsonStream( dockerHelper.networkList() )
		// .filter( network -> network.path( DockerJson.list_label.json()
		// ).asText().equals( NETWORK_TEST ) )
		// .map (network -> network.at( "/" + DockerJson.list_attributes.json() +
		// "/Id" ).asText())
		// .forEach( dockerHelper::networkDelete );

		ObjectNode networkDeleteResponse = csapDocker.networkDelete( NETWORK_TEST ) ;
		logger.info( "volDeleteResponse: \n {}", CSAP.jsonPrint( networkDeleteResponse ) ) ;
		assertThat( csapDocker.networkNames( ) )
				.as( "Network not found" )
				.doesNotContain( NETWORK_TEST ) ;

	}

	@Test
	public void verify_deploy_with_auto_creates_works ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		ObjectNode dockerDefinitionWithCreates = (ObjectNode) getJsonMapper( )
				.readTree( new File( getClass( )
						.getResource( "SimpleDocker.json" )
						.toURI( ) ) ) ;

		logger.info( "loaded template: \n {}", CSAP.jsonPrint( dockerDefinitionWithCreates ) ) ;

		csapDocker.imagePull( ContainerIntegration.DOCKER_DEFAULT_IMAGE, null, 60, 3 ) ;

		ServiceInstance service = new ServiceInstance( ) ;

		String serviceName = "SimpleName" ;

		service.setName( serviceName ) ;

		JsonNode removeContainerResults = csapDocker.containerRemove(
				null,
				service.getDockerContainerPath( ),
				true ) ;

		JsonNode removeVolumeResults = csapDocker.volumeDelete( "junit-host-volume" ) ;

		logger.info( "Remove container results: \n {} \n\n Remove Volume results:\n {}",
				CSAP.jsonPrint( removeContainerResults ), CSAP.jsonPrint( removeVolumeResults ) ) ;

		assertThat( removeVolumeResults.has( DockerJson.error.json( ) ) )
				.as( "no errors during volme remove" )
				.isFalse( ) ;

		String networks = CSAP.jsonStream( csapDocker.networkList( ) )
				.map( network -> network.get( DockerJson.list_label.json( ) ).asText( ) )
				.collect( Collectors.joining( "\n" ) ) ;

		logger.info( "networks: {}", networks ) ;

		ObjectNode results = csapDocker.containerCreateAndStart(
				service,
				(ObjectNode) dockerDefinitionWithCreates.get( "docker" ) ) ;

		assertThat( results.has( DockerJson.error.json( ) ) ).as( "no errors during deploy" ).isFalse( ) ;

		logger.info( "startResults: \n {}", CSAP.jsonPrint( results.at( "/startResults/state" ) ) ) ;

		assertThat( results.path( "startResults" ).isObject( ) )
				.as( "no errors during deploy" )
				.isTrue( ) ;

		logger.info( "/volumeResults results: \n {}",
				CSAP.jsonPrint( results.get( DockerJson.response_volume_create.json( ) ) ) ) ;

		assertThat( results.get(
				DockerJson.response_volume_create.json( ) )
				.at( "/" + DockerJson.create_persistent.json( ) + "/1/Mountpoint" )
				.asText( ) )
						.as( "Mountpoint created" )
						.contains( "/volumes/junit-host-volume/_data" ) ;

		logger.info( "/network results: \n {}",
				CSAP.jsonPrint( results.get( DockerJson.response_network_create.json( ) ) ) ) ;

	}

	@Test
	public void verify_nginx_pull_create_remove ( )
		throws Exception {

		ObjectNode nginxDefinition = (ObjectNode) getJsonMapper( )
				.readTree( new File( getClass( )
						.getResource( "nginx-definition.json" )
						.toURI( ) ) ) ;

		ObjectNode nginxDockerDef = (ObjectNode) nginxDefinition.get( ServiceAttributes.dockerSettings.json( ) ) ;

		logger.info( "loaded template: \n {}", CSAP.jsonPrint( nginxDefinition ) ) ;

		csapDocker.imagePull( nginxDockerDef.get( DockerJson.imageName.json( ) ).asText( ), null, 60, 3 ) ;

		ServiceInstance service = new ServiceInstance( ) ;

		String serviceName = "JunitNginx" ;

		service.setName( serviceName ) ;
		service.setPort( nginxDefinition.get( "port" ).asText( ) ) ;

		JsonNode removeContainerResults = csapDocker.containerRemove(
				null,
				service.getDockerContainerPath( ),
				true ) ;

		logger.info( "removeContainerResults: {}", CSAP.jsonPrint( removeContainerResults ) ) ;

		JsonNode removeVolumeResults = csapDocker.volumeDelete(
				nginxDockerDef.at( "/volumes/0/hostPath" ).asText( ) ) ;

		logger.info( "removeVolumeResults: {}", CSAP.jsonPrint( removeVolumeResults ) ) ;

		assertThat( removeVolumeResults.has( DockerJson.error.json( ) ) )
				.as( "no errors during volme remove" )
				.isFalse( ) ;

		String networks = CSAP.jsonStream( csapDocker.networkList( ) )
				.map( network -> network.get( DockerJson.list_label.json( ) ).asText( ) )
				.collect( Collectors.joining( "\t" ) ) ;

		logger.info( "networks: {}", networks ) ;

		ObjectNode container_results = csapDocker.containerCreateAndStart(
				service,
				nginxDockerDef ) ;

		logger.info( "containerCreateAndStart: \n {}",
				CSAP.jsonPrint( container_results.at( "/startResults/state" ) ) ) ;

		assertThat( container_results.has( DockerJson.error.json( ) ) )
				.as( "no errors during deploy" )
				.isFalse( ) ;

		assertThat( container_results.at( "/startResults/state/Running" ).asBoolean( ) )
				.as( "no errors during deploy" )
				.isTrue( ) ;

		JsonNode volumeResults = container_results.get( DockerJson.response_volume_create.json( ) ) ;
		logger.info( "/volumeResults: \n {}",
				CSAP.jsonPrint( volumeResults ) ) ;

		assertThat(
				volumeResults.at( "/" + DockerJson.create_persistent.json( ) + "/0/Mountpoint" ).asText( ) )
						.as( "Mountpoint created" )
						.contains( "/var/lib/docker/volumes/nginx-demo-volume/_data" ) ;

		logger.info( "/network results: \n {}",
				CSAP.jsonPrint( container_results.get( DockerJson.response_network_create.json( ) ) ) ) ;

		JsonNode finalRemoveResults = csapDocker.containerRemove(
				null,
				service.getDockerContainerPath( ),
				true ) ;

		logger.info( "removeContainerResults: {}", CSAP.jsonPrint( finalRemoveResults ) ) ;

	}

	public DockerSettings getDocker ( ) {

		return docker ;

	}

	public void setDocker ( DockerSettings docker ) {

		this.docker = docker ;

	}

}
