package org.csap.agent.integration.misc ;
// package test.scenarios_7.service_integrations;

//
// import java.io.File;
// import java.nio.file.Files;
// import java.nio.file.Path;
// import java.util.List;
// import java.util.Optional;
// import java.util.regex.Matcher;
// import java.util.regex.Pattern;
//
// import javax.annotation.PostConstruct;
//
// import org.apache.logging.log4j.LogManager;
// import org.apache.logging.log4j.core.LoggerContext;
// import org.csap.agent.model.Application;
// import org.junit.After;
// import org.junit.AfterClass;
// import org.junit.Before;
// import org.junit.BeforeClass;
// import org.junit.Test;
// import org.slf4j.Logger;
// import org.slf4j.LoggerFactory;
// import org.springframework.beans.factory.annotation.Autowired;
// import org.springframework.boot.autoconfigure.SpringBootApplication;
// import org.springframework.core.env.Environment;
//
// import com.fasterxml.jackson.databind.ObjectMapper;
// import com.fasterxml.jackson.databind.node.ObjectNode;
// import com.spotify.docker.client.DefaultDockerClient;
// import com.spotify.docker.client.DockerClient;
// import com.spotify.docker.client.DockerClient.ListContainersParam;
// import com.spotify.docker.client.DockerClient.ListImagesParam;
// import com.spotify.docker.client.DockerClient.LogsParam;
// import com.spotify.docker.client.LogStream;
// import com.spotify.docker.client.exceptions.DockerException;
// import com.spotify.docker.client.messages.Container;
// import com.spotify.docker.client.messages.ContainerConfig;
// import com.spotify.docker.client.messages.ContainerCreation;
// import com.spotify.docker.client.messages.ContainerInfo;
// import com.spotify.docker.client.messages.Image;
//
/// **
// *
// * Simple tests to validate specific configuration of Spring LDAP
// * Template.
// *
// * Similar to sql - LDAP has a DSL for interacting with provider, which in
// turn
// * is abstracted somewhat by Java nameing apis. Spring Ldap makes this much
// more
// * developer friendly.
// *
// * Prior to jumping to code, it is highly recommended to make use of a desktop
// * LDAP browser to browse LDAP tree to familiarize your self with syntax
// * and available attributes.
// *
// * Softerra ldap browser is nice way to approach
// *
// *
// * @author someDeveloper
// *

// *
// * @see <a href=
// *
// "http://docs.spring.io/spring-ldap/docs/1.3.2.RELEASE/reference/htmlsingle/#introduction-overview">
// * Spring LDAP lookup </a>
// *
// */
//
//// @Disabled
// public class Docker_Spotify {
//
// final static private Logger logger = LoggerFactory.getLogger(
// Docker_Spotify.class );
//
// @BeforeAll
// public static void setUpBeforeClass ()
// throws Exception {
// // Boot_Container_Test.printTestHeader( logger.getName() );
// LoggerContext context = (org.apache.logging.log4j.core.LoggerContext)
// LogManager.getContext( false );
// String logConfig = System.getProperty( "user.dir" ) +
// "/devData/log4j2-junit.yml";
// File file = new File( logConfig );
//
// // this will force a reconfiguration
//
// context.setConfigLocation( file.toURI() );
//
// logger.info( "\n\n *** log4j initialized using: {} *** \n\n", file );
//
// // java.util.logging.Logger jerseylogs =
// // java.util.logging.Logger.getLogger( "JerseyLogger" );
// // Feature feature = new LoggingFeature( jerseylogs, Level.INFO, null,
// // null );
// //
// // Client client = ClientBuilder.newBuilder()
// // .register( feature )
// // .build();
// //
// // Response response = client.target( "https://www.google.com" )
// // .queryParam( "q", "Hello, World!" )
// // .request().get();
// // logger.info( "\n\n *** jersey debug configured *** \n\n", file );
// }
//
// @AfterClass
// public static void tearDownAfterClass ()
// throws Exception {
// }
//
// @Before
// public void setUp ()
// throws Exception {
// }
//
// @After
// public void tearDown ()
// throws Exception {
// }
//
// // needed for property placedholder context
// @SpringBootApplication
// static class TestConfiguration {
// }
//
// @PostConstruct
// void printVals () {
// }
//
// ObjectMapper jacksonMapper = new ObjectMapper();
//
// // https://github.com/spotify/docker-client/blob/master/docs/user_manual.md
// // DockerClient docker = new
// // DefaultDockerClient("unix:///var/run/docker.sock");
// final DockerClient docker = DefaultDockerClient.builder()
// .uri( "http://csap-dev04.yourcompany.com:4243" )
// .connectionPoolSize( 5 )
// .build();
//
// @Test
// public void verify_docker_api ()
// throws Exception {
//
// List<Container> containers = docker.listContainers(
// ListContainersParam.allContainers() );
//
// containers.forEach( container -> {
// logger.info( "container id: {}, \tnames: {} , \t\t {}", container.id(),
// container.names(), container.toString() );
// } );
// // logger.info( "Containers: {}", containers );
//
// List<Image> images = docker.listImages( ListImagesParam.allImages() );
//
// // logger.info( "images: {}",images );
//
// images.forEach( image -> {
// logger.info( "image id: {}, \t labels: {} , repo: {} \t\t {}", image.id(),
// image.labels(), image.repoTags(), image.toString() );
// } );
//
// }
//
// private Container getContainerByName ( String containerName )
// throws Exception {
//
// List<Container> containers = docker.listContainers(
// ListContainersParam.filter( "name", containerName ),
// ListContainersParam.allContainers( true ) );
//
// if ( containers.size() != 1 ) {
// throw new Exception( "Failed to locate unique instance of: " + containerName
// );
// }
//
// return containers.get( 0 );
// }
//
// @Test
// public void create_docker_container_by_name ()
// throws Exception {
// List<Container> containers = docker.listContainers(
// ListContainersParam.allContainers() );
//
// int originalCount = containers.size() ;
// containers.forEach( container -> {
// logger.debug( "container id: {}, \tnames: {} , \t\t {}", container.id(),
// container.names(), container.toString() );
// } );
// ContainerCreation container = docker.createContainer(
// ContainerConfig
// .builder()
// .image( "hello-world" )
// .build() );
//
//
// containers = docker.listContainers( ListContainersParam.allContainers() );
//
// containers.forEach( afterContainers -> {
// logger.debug( "container id: {}, \tnames: {} , \t\t {}",
// afterContainers.id(), afterContainers.names(), afterContainers.toString() );
// } );
//
// logger.info( "Originally: {}, now: {}" , originalCount, containers.size() );
//
// }
//
// @Test
// public void start_docker_container_by_name ()
// throws Exception {
//
// Container container = getContainerByName( "/peter" );
//
// logger.info( "Before State: {} status: {}", container.state(),
// container.status() );
//
// docker.startContainer( container.id() );
//
// while (true) {
// Thread.sleep( 1000 );
//
// container = getContainerByName( "/peter" );
// logger.info( "After State: {} status: {}", container.state(),
// container.status() );
//
// if ( !container.state().equals( "exited" ) ) {
// break;
// }
// }
//
// }
//
// @Test
// public void stop_docker_container_by_name ()
// throws Exception {
//
// Container container = getContainerByName( "/peter" );
// logger.info( "Before State: {} status: {}", container.state(),
// container.status() );
//
// docker.stopContainer( container.id(), 0 );
//
// while (true) {
// Thread.sleep( 1000 );
// container = getContainerByName( "/peter" );
// logger.info( "After State: {} status: {}", container.state(),
// container.status() );
//
// if ( container.state().equals( "exited" ) ) {
// break;
// }
// }
//
// }
//
// @Test
// public void start_docker_container () {
//
// String startName = "/peter";
// try {
// // docker.startContainer( startName);
// // List<Container> containers =
// // docker.listContainers(ListContainersParam.allContainers() ) ;
// List<Container> containers = docker.listContainers(
// ListContainersParam.filter( "name", startName ),
// ListContainersParam.allContainers( true ) );
//
// logger.info( "Containers found: {}", containers.size() );
// containers.forEach( container -> {
// logger.debug( "container id: {}, \tnames: {} , \t\t {}", container.id(),
// container.names(), container.toString() );
//
// if ( container.names().contains( startName ) ) {
// logger.info( " STARTING: container id: {}, \t names: {} , \t\t {}",
// container.id(), container.names(),
// container.toString() );
// try {
// // docker.stopContainer( container.id(), 0 );
// docker.startContainer( container.id() );
// } catch (Exception e) {
//
// logger.error( "Failed to start: {}", Application.getCsapFilteredStackTrace(
// e, "csap" ) );
// }
// }
//
// } );
// } catch (Exception e) {
//
// logger.error( "Failed to start: {}", Application.getCsapFilteredStackTrace(
// e, "csap" ) );
//
// }
// }
//
// @Test
// public void logs_docker_container () {
//
// String startName = "/peter";
// try {
// // docker.startContainer( startName);
// List<Container> containers = docker.listContainers(
// ListContainersParam.allContainers() );
//
// containers.forEach( container -> {
// logger.debug( "container id: {}, \tnames: {} , \t\t {}", container.id(),
// container.names(), container.toString() );
//
// if ( container.names().contains( startName ) ) {
// logger.info( " LOGGINS: container id: {}, \t names: {} , \t\t {}",
// container.id(), container.names(),
// container.toString() );
// try {
// ContainerInfo info = docker.inspectContainer( container.id() );
// logger.info( "Inspect: {}", info.toString() );
//
// // docker.logs( container.id(), LogsParam.tail( 100 ),
// // LogsParam.stdout() ) ;
// String logs;
// try (LogStream stream = docker.logs( container.id(), LogsParam.tail( 100 ),
// LogsParam.stdout(),
// LogsParam.stderr() )) {
// logs = stream.readFully();
// }
//
// logger.info( "logs: {}", logs );
// // docker.logs( containerId, params )( container.id(), 0
// // );
// } catch (Exception e) {
//
// logger.error( "Failed to start: {}", Application.getCsapFilteredStackTrace(
// e, "csap" ) );
// }
// }
//
// } );
// } catch (Exception e) {
//
// logger.error( "Failed to start: {}", Application.getCsapFilteredStackTrace(
// e, "csap" ) );
//
// }
// }
//
// }
