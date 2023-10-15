package org.csap.agent ;

import static org.ehcache.config.builders.CacheManagerBuilder.newCacheManagerBuilder ;

import java.io.File ;
import java.io.IOException ;
import java.io.OutputStream ;
import java.lang.management.ManagementFactory ;
import java.lang.management.MemoryMXBean ;
import java.lang.management.OperatingSystemMXBean ;
import java.net.InetAddress ;
import java.net.NetworkInterface ;
import java.net.URI ;
import java.nio.ByteBuffer ;
import java.nio.charset.Charset ;
import java.nio.file.Files ;
import java.nio.file.Path ;
import java.nio.file.Paths ;
import java.nio.file.StandardCopyOption ;
import java.text.DecimalFormat ;
import java.time.Duration ;
import java.time.LocalDate ;
import java.time.LocalDateTime ;
import java.time.OffsetDateTime ;
import java.time.ZoneId ;
import java.time.format.DateTimeFormatter ;
import java.util.ArrayList ;
import java.util.Arrays ;
import java.util.Collections ;
import java.util.List ;
import java.util.Spliterator ;
import java.util.Spliterators ;
import java.util.concurrent.TimeUnit ;
import java.util.regex.Matcher ;
import java.util.regex.Pattern ;
import java.util.stream.Collectors ;
import java.util.stream.LongStream ;
import java.util.stream.Stream ;
import java.util.stream.StreamSupport ;

import org.apache.commons.io.FileUtils ;
import org.apache.commons.io.FilenameUtils ;
import org.csap.agent.model.ProjectLoader ;
import org.csap.agent.project.loader.Rest_Service_Tests ;
import org.csap.helpers.CSAP ;
import org.csap.helpers.CsapApplication ;
import org.csap.helpers.CsapRestTemplateFactory ;
import org.ehcache.Cache ;
import org.ehcache.CacheManager ;
import org.ehcache.config.CacheConfiguration ;
import org.ehcache.config.builders.CacheConfigurationBuilder ;
import org.ehcache.config.builders.ExpiryPolicyBuilder ;
import org.ehcache.config.builders.ResourcePoolsBuilder ;
import org.jasypt.encryption.pbe.StandardPBEStringEncryptor ;
import org.junit.jupiter.api.Disabled ;
import org.junit.jupiter.api.Test ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.core.io.DefaultResourceLoader ;
import org.springframework.http.HttpEntity ;
import org.springframework.http.HttpHeaders ;
import org.springframework.http.HttpMethod ;
import org.springframework.http.HttpStatus ;
import org.springframework.http.MediaType ;
import org.springframework.http.ResponseEntity ;
import org.springframework.http.converter.FormHttpMessageConverter ;
import org.springframework.util.LinkedMultiValueMap ;
import org.springframework.util.MultiValueMap ;
import org.springframework.web.client.RestTemplate ;

import com.fasterxml.jackson.core.SerializableString ;
import com.fasterxml.jackson.core.io.CharacterEscapes ;
import com.fasterxml.jackson.core.type.TypeReference ;
import com.fasterxml.jackson.databind.ObjectMapper ;
import com.fasterxml.jackson.databind.node.ArrayNode ;
import com.fasterxml.jackson.databind.node.MissingNode ;
import com.fasterxml.jackson.databind.node.ObjectNode ;

import io.micrometer.core.instrument.Counter ;
import io.micrometer.core.instrument.Metrics ;
import io.micrometer.core.instrument.Timer ;
import io.micrometer.core.instrument.distribution.HistogramSnapshot ;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry ;

/**
 * 
 * Simple tests to validate specific configuration of Spring LDAP Template.
 * 
 * Similar to sql - LDAP has a DSL for interacting with provider, which in turn
 * is abstracted somewhat by Java nameing apis. Spring Ldap makes this much more
 * developer friendly.
 * 
 * Prior to jumping to code, it is highly recommended to make use of a desktop
 * LDAP browser to browse LDAP tree to familiarize your self with syntax and
 * available attributes.
 * 
 * Softerra ldap browser is nice way to approach
 * 
 * 
 * @author someDeveloper
 *
 * 
 * @see <a href=
 *      "http://docs.spring.io/spring-ldap/docs/1.3.2.RELEASE/reference/htmlsingle/#introduction-overview">
 *      Spring LDAP lookup </a>
 *
 */

// TO RUN - update UID and password
//@Disabled
public class NonCsapTests {

	final static private Logger logger = LoggerFactory.getLogger( NonCsapTests.class ) ;

	static {

		CsapApplication.initialize( "Test Setup Complete" ) ;

	}

	ObjectMapper jsonMapper = new ObjectMapper( ) ;

	@Disabled
	@Test
	void writeResource ( ) throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		// var cp = new ClassPathResource("/info.png" ) ;
		var resourceLoader = new DefaultResourceLoader( ) ;
		var cp = resourceLoader.getResource( "/info.png" ) ;

		var src = cp.getInputStream( ) ;

//		try ( var reader = new BufferedReader(
//				new InputStreamReader( resource ) ) ) {
//			
//
//			String springContent = reader.lines( )
//					.collect( Collectors.joining( "\n" ) ) ;
//			
//			
//			logger.info( "springContent: {}", springContent ) ;
//
//		}

		// InputStream src = c.getResourceAsStream(res);
		Files.copy( src, Paths.get( "/dev/peter.png" ), StandardCopyOption.REPLACE_EXISTING ) ;

		// logger.info( "MissingNode.getInstance int value: {}", misValue );

	}

	@Test
	void missingNodeToInt ( ) {

		logger.info( CsapApplication.testHeader( ) ) ;

		int misValue = MissingNode.getInstance( ).asInt( ) ;

		logger.info( "MissingNode.getInstance int value: {}", misValue ) ;

	}

	@Test
	void java_regex_keyword_value ( ) throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		var jobYamlFile = new File(
				Rest_Service_Tests.class.getResource(
						"create-yourapp-job.yaml" )
						.getPath( ) ) ;
		var jobYamlContent = FileUtils.readFileToString( jobYamlFile ) ;

		// https://www.oreilly.com/library/view/java-cookbook-3rd/9781449338794/ch04.html#javacook-regex-SECT-10
		// non whitespace characters
//		var yamlPattern= Pattern.compile( "(?<=(command|name): )(\\S+)" ) ;
		var yamlPattern = Pattern.compile( "(?<=(command|name): )(.*)" ) ;

		var matches = new ArrayList<String>( ) ;
		Matcher yamlKeywordMatcher = yamlPattern.matcher( jobYamlContent ) ;

		while ( yamlKeywordMatcher.find( ) ) {

			matches.add( CSAP.padNoLine( yamlKeywordMatcher.group( 1 ) ) + yamlKeywordMatcher.group( 2 ) ) ;

		}

		var matchesDesc = matches.stream( )
				.collect( Collectors.joining( "\n" ) ) ;

		logger.info( "matches: \n{}", matchesDesc ) ;

	}

	@Test
	void listHashes ( ) {

		var empty = List.of( ) ;

		var apples = List.of( "a" ) ;

		var ab = List.of( "ab" ) ;

		var a_b = List.of( "a", "b" ) ;

		var z = List.of( "z" ) ;

		logger.info( CSAP.buildDescription(
				"hashes",
				empty, empty.hashCode( ),
				ab, ab.hashCode( ),
				a_b, a_b.hashCode( ),
				apples, apples.hashCode( ),
				z, z.hashCode( ) ) ) ;

	}

	@Test
	void offsetTimes ( ) {

		logger.info( CsapApplication.testHeader( ) ) ;

		var gmtTime = OffsetDateTime.now( ZoneId.of( "Z" ) ) ;

		var localTime = gmtTime.toZonedDateTime( ).withZoneSameInstant( ZoneId.systemDefault( ) ) ;

		logger.info( "nowOffset: {},\n local: {},\n formatted: {}", gmtTime,
				localTime,
				localTime.format( DateTimeFormatter.ofPattern( "MM/dd/yyyy - HH:mm:ss z" ) ) ) ;

	}

	@Test
	void valueSet ( ) {

		logger.info( CsapApplication.testHeader( ) ) ;

		var source = jsonMapper.createObjectNode( ) ;
		source.put( "hi", "there" ) ;

		var dest = jsonMapper.createObjectNode( ) ;
		dest.set( "hey", source.path( "hi" ) ) ;

		logger.info( "source: {}, dest: {}", source, dest ) ;

	}

	@Test
	void pathTest ( ) {

		logger.info( CsapApplication.testHeader( ) ) ;

		var myPath = Paths.get( "/some/linux/path" ) ;

		logger.info( "source: {}, dest: {}", myPath, FilenameUtils.separatorsToUnix( myPath.getParent( )
				.toString( ) ) ) ;

	}

	@Test
	void filePath ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		var currentFolder = new File( "." ) ;

		var winpath = new File( "C:\\Users\\peter.nightingale" ) ;

		logger.info( CSAP.buildDescription(
				"Conversions",
				"winpath", winpath,
				"exists", winpath.exists( ),
				"currentFolder", currentFolder,
				".getAbsolutePath( )", currentFolder.getAbsolutePath( ),
				".toURI( )", currentFolder.toURI( ),
				".toURI().getPath()", currentFolder.toURI( ).getPath( ) ) ) ;

		var convertedBackString = new URI( currentFolder.toURI( ).toString( ) ) ;
		var convertedBackFolder = new File( convertedBackString ) ;

		logger.info( CSAP.buildDescription(
				"convertedBackString",
				"convertedBackString", convertedBackString,
				".getAbsolutePath( )", convertedBackFolder.getAbsolutePath( ) ) ) ;

	}

	@Test
	void streamTest ( ) {

		logger.info( CsapApplication.testHeader( ) ) ;

		var testObject = jsonMapper.createObjectNode( ) ;
		var hiPath = testObject.remove( "hi" ) ;

		CSAP.asStreamHandleNulls( hiPath ).forEach( item -> {

			logger.info( "got: '{}'", item ) ;

		} ) ;

	}

	@Test
	void timeCheck ( ) {

		logger.info( CsapApplication.testHeader( ) ) ;

		LocalDateTime now = LocalDateTime.now( ) ;

		LocalDateTime anotherTime = LocalDateTime.now( ).minusSeconds( 10 ) ;

		logger.info( "now: '{}' oneSecondPast: {} , difference: {}",
				now,
				anotherTime,
				Duration.between( anotherTime, now ).getSeconds( ) ) ;

	}

	@Test
	void jsonPath ( ) {

		logger.info( CsapApplication.testHeader( ) ) ;

		var testObject = jsonMapper.createObjectNode( ) ;
		var hiPath = testObject.path( "hi" ) ;
		var hiTherePath = testObject.path( ProjectLoader.SERVICE_TEMPLATES ) ;

		logger.info( "hiPath: '{}' \t\t hiTherePath: '{}'", hiPath.getClass( ), hiTherePath.getClass( ) ) ;

	}

	@Test
	void millisCheck ( ) {

		logger.info( CsapApplication.testHeader( ) ) ;

		var millisInAMinute = TimeUnit.MINUTES.toMillis( 2 ) ;
		logger.info( "Millis in a 2 minute: {} current millis: {}", millisInAMinute, System.currentTimeMillis( ) ) ;

	}

	@Test
	void processHandle ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		var processListing = ProcessHandle.allProcesses( )
				.map( process -> {

					var info = process.info( ) ;

					var args = info.arguments( ).orElse( new String[] {} ) ;

					return CSAP.pad( "" + process.pid( ) )
							+ CSAP.pad( "" + info.totalCpuDuration( ).orElse( Duration.ofMillis( 0 ) ).toMinutes( ) )
							+ CSAP.pad( info.command( ).orElse( "-" ) )
							+ Arrays.asList( args ) ;

				} )
				.collect( Collectors.joining( "\n" ) ) ;

		logger.info( CsapApplication.header( "\n" + processListing ) ) ;

		// ProcessHandle.allProcesses().filter(p - >
		// p.info().command().isPresent()).limit(4).forEach((process) - > {
		//
		// System.out.println("Process id : " + process.pid());
		//
		// ProcessHandle.Info info = process.info();
		//
		// System.out.println("Command: " + info.command().orElse(""));
		//
		// String[] arguments = info.arguments().orElse(new String[] {});
		//
		// System.out.println("Arguments:");
		//
		// for (String arg: arguments)
		//
		// System.out.println(" arguement :" + arg);
		//
		// System.out.println("Command line: " + info.commandLine().orElse(""));
		//
		// System.out.println("Start time: " +
		// info.startInstant().orElse(Instant.now()).toString());
		//
		// System.out.println("Run time duration: " +
		// info.totalCpuDuration().orElse(Duration.ofMillis(0)).toMillis());
		//
		// System.out.println("User :" + info.user().orElse(""));
		//
		// System.out.println("===================");
		//
		// });
	}

	@Test
	public void verify_ehcache_pojo ( )
		throws InterruptedException {

		logger.info( CsapApplication.testHeader( ) ) ;

		CacheConfiguration<Long, String> cacheConfiguration = CacheConfigurationBuilder
				.newCacheConfigurationBuilder( Long.class, String.class,
						ResourcePoolsBuilder.heap( 3 ) )
				.withExpiry( ExpiryPolicyBuilder.timeToLiveExpiration( Duration.ofSeconds( 2 ) ) )
				.build( ) ;

		try ( CacheManager cacheManager = newCacheManagerBuilder( )
				.withCache( "basicCache",
						cacheConfiguration )
				.build( true ) ) {

			Cache<Long, String> basicCache = cacheManager.getCache( "basicCache", Long.class, String.class ) ;

			logger.info( "Putting to cache" ) ;
			basicCache.put( 1L, "da one!" ) ;
			basicCache.put( 2L, "da 2!" ) ;
			basicCache.put( 3L, "da 3!" ) ;
			basicCache.put( 4L, "da 4!" ) ;

			var val1 = basicCache.get( 1L ) ;
			logger.info( "Retrieved '{}'", val1 ) ;

			var val2 = basicCache.get( 2L ) ;
			logger.info( "Retrieved '{}'", val2 ) ;

			TimeUnit.SECONDS.sleep( 3 ) ;
			val2 = basicCache.get( 2L ) ;
			logger.info( "Retrieved '{}'", val2 ) ;
			logger.info( "Closing cache manager" ) ;

		}

	}

	@Test
	public void jasypt_encode_example ( ) {

		logger.info( CsapApplication.testHeader( ) ) ;

		StandardPBEStringEncryptor encryptor = new StandardPBEStringEncryptor( ) ;
		encryptor.setAlgorithm( "PBEWITHMD5ANDTRIPLEDES" ) ;
		encryptor.setPassword( "willBeOverwritten" ) ;

		var testVal = encryptor.decrypt( "k16ljSCT5UnF8o1fCyshcD3+VZtrWm2c" ) ;

		logger.info( "Decoded: {}", testVal ) ;

	}

	@Test
	public void simpleReplace ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		var string = "peter $was+1 here" ;

		logger.info( "original: {}, replaced: {}",
				string,
				string.replaceAll( Pattern.quote( "$was+1" ), "is" ) ) ;

		var test = ".csap.com" ;

		logger.info( "found * : '{}'", test.contains( "*" ) ) ;

	}

	@Test
	public void verifyStreamObject ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		var s = jsonMapper.createObjectNode( ) ;

		s.put( "a", "1" ) ;
		s.put( "b", "2" ) ;

		CSAP.jsonStream( s ).forEach( System.out::println ) ;
		;

	}

	@Test
	public void verifyArrayAdd ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		List<String> list = new ArrayList<String>( Arrays.asList( "item 1", "item 2" ) ) ;

		list.remove( "item 1" ) ;
		list.add( "peter" ) ;

		logger.info( "list: {}", list ) ;

	}

	//

	@Test
	public void microMeterCounter ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		Metrics.addRegistry( new SimpleMeterRegistry( ) ) ;
		Metrics.counter( "junit-counter" ).increment( ) ;

		Counter testCounter = Metrics.globalRegistry.find( "junit-counter" ).counter( ) ;

		logger.info( "testCounter: {}", testCounter.count( ) ) ;

	}

	@Test
	public void microMeterTimer ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		Metrics.addRegistry( new SimpleMeterRegistry( ) ) ;
		Timer testTimer = Metrics.timer( "junit-timer" ) ;

		testTimer.record( ( ) -> {

			try {

				TimeUnit.MILLISECONDS.sleep( 55 ) ;

			} catch ( InterruptedException ignored ) {

			}

		} ) ;

		Timer searchTimer = Metrics.globalRegistry.find( "junit-timer" ).timer( ) ;

		HistogramSnapshot snap = searchTimer.takeSnapshot( ) ;

		logger.info( "testCounter: {} ms,   {} seconds, snap: {} ms",
				searchTimer.mean( TimeUnit.MILLISECONDS ),
				searchTimer.mean( TimeUnit.SECONDS ),
				snap.mean( TimeUnit.MILLISECONDS ) ) ;

	}

	@Test
	public void verifyDiskParsing ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		String disk = "7942M" ;

		logger.info( "disk: {}, parsed: {}", disk, Integer.parseInt( disk.replaceAll( "[\\D]", "" ) ) ) ;

		double memUsed = 1.21790464E8 ;
		logger.info( "memUsed: {}, in mb: {}", memUsed, memUsed / CSAP.MB_FROM_BYTES ) ;

		double cpu = 5.771106343341624E-1 ;
		logger.info( "cpu: {}, in cpu rounded: {}", cpu, Math.round( cpu * 100 ) ) ;

	}

	@Test
	public void endsWith ( ) {

		logger.info( CsapApplication.testHeader( ) ) ;

		String name = "perter.gz" ;

		logger.info( "name: '{}', endsWith .gz{}", name, name.endsWith( ".gz" ) ) ;

	}

	@Test
	public void json_default ( ) {

		logger.info( CsapApplication.testHeader( ) ) ;

		ObjectNode testObject = jsonMapper.createObjectNode( ) ;

		int testInt = testObject.path( "doesNotExist" ).asInt( -1 ) ;

		logger.info( "testInt: {}, parsed: {}", testInt, Math.round( Double.parseDouble( "03.5" ) ) ) ;

		logger.info( "list: {}", Arrays.asList( " a    b c d ".trim( ).split( "\\s+ ", 9 ) ) ) ; // 8th field is the
																									// args

		testObject.put( "testDouble", 1 ) ;
		logger.info( "testDouble: {}", testObject.path( "testDouble" ).asDouble( -0.01 ) ) ;

	}

	@Test
	public void verifyJsonClone ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		ArrayNode testArray = jsonMapper.createArrayNode( ) ;

		testArray.add( "a" ) ;
		testArray.add( "b" ) ;
		testArray.add( "c" ) ;

		ArrayNode cloneArray = testArray.deepCopy( ) ;

		cloneArray.remove( 1 ) ;

		logger.info( "testArray: {}, cloneArray{}", testArray, cloneArray ) ;

		// logger.info( "Missing item: {}",
		// jacksonMapper.createObjectNode().get( "missing" ).asText("") );
	}

	@Test
	public void linuxFile ( ) {

		logger.info( CsapApplication.testHeader( ) ) ;

		File f = new File( System.getProperty( "user.dir" ) ) ;
		logger.info( "file: {}, {}", f.getPath( ) ) ;

	}

	@Test
	public void verifyJsonFileName ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		ObjectNode fileNode = jsonMapper.createObjectNode( ) ;

		Files.newDirectoryStream(

				Paths.get(
						System.getProperty( "user.dir" ) + "/src/main/resources" ),

				path -> path.toString( )
						.endsWith( ".yml" ) )

				.forEach( path -> {

					logger.info( "path: {}", path ) ;
					String content ;

					try {

						content = new String(
								Files.readAllBytes( path ),
								Charset.forName( "UTF-8" ) ) ;
						fileNode.put( path.toFile( ).getName( ), content ) ;

					} catch ( IOException e ) {

						// TODO Auto-generated catch block
						e.printStackTrace( ) ;

					}

				} ) ;

		logger.info( "fileNode: {}", CsapConstants.jsonPrint( jsonMapper, fileNode ) ) ;

		// logger.info( "Missing item: {}",
		// jacksonMapper.createObjectNode().get( "missing" ).asText("") );
	}

	// @Test
	// public void verify_metric_parsing ()
	// throws Exception {
	//
	// RestTemplate restTemplate = new RestTemplate();
	//
	// String url =
	// "http://csap-dev04:8014/api/v1/namespaces/default/services/csap-test-k8s-service-6090-service/proxy/csap-test-k8s-service/csap/metrics/micrometers"
	// ;
	//
	// ObjectNode apiResponse = restTemplate.getForObject( url, ObjectNode.class ) ;
	//
	// logger.info( "url: {} \n\t response: {}", url, CSAP.jsonPrint( apiResponse ))
	// ;
	//
	// String path="/http.server.requests[~1**~1*.css]/COUNT" ;
	// logger.info( "path: {} \n\t value: {}", path, apiResponse.at( path
	// ).toString()) ;
	//
	//
	// }

	// @Test
	public void verify_pod_status ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		RestTemplate restTemplate = new RestTemplate( ) ;

		String url = "http://saasyourapp-csap01.yourcompany.org:8011/CsAgent/api/agent/kubernetes/pods?namespace=all" ;

		ArrayNode status_of_all_pods = restTemplate.getForObject( url, ArrayNode.class ) ;

		logger.debug( "url: {} \n\t response: {}", url, CSAP.jsonPrint( status_of_all_pods ) ) ;

		String status_text = CSAP.jsonStream( status_of_all_pods )

				.map( pod_status -> {

					var name = pod_status.at( "/metadata/name" ).asText( "name-not-found" ) ;
					var statusNode = pod_status.at( "/status/containerStatuses/0/state" ) ;
					var status = "not-found" ;

					if ( ! statusNode.isMissingNode( ) ) {

						status = statusNode.toString( ) ;

					}

					return "Name: " + name + " 1st container: " + status ;

				} )

				.collect( Collectors.joining( "\n\t" ) ) ;

		logger.info( "Results from {} {}", url, status_text ) ;

	}

	public static String NAMESPACE_YAML = "apiVersion: v1\n" +
			"kind: Namespace\n" +
			"metadata:\n" +
			"  name: demo-dummy-namespace\n" +
			"  labels:\n" +
			"    name: test" ;

	@Disabled
	@Test
	public void verify_kubernetes_post ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		RestTemplate restTemplate = new RestTemplate( ) ;

		String url = "http://csap-dev04.yourcompany.org:8011/api/agent/kubernetes/specification" ;
		// url =
		// "http://saasyourapp-csap01.yourcompany.org:8011/CsAgent/api/agent/kubernetes/specification"
		// ;
		// url =
		// "http://localhost.yourcompany.org:8011/CsAgent/api/agent/kubernetes/specification"
		// ;

		//
		// Create Namespace
		//

		MultiValueMap<String, String> urlVariables = new LinkedMultiValueMap<String, String>( ) ;
		urlVariables.add( "userid", "csap-unit-test" ) ;
		urlVariables.add( "pass", "RqayZbbIpHdVVkdkbpsY3gu8t1ozIN/+cAfW3lAGaKk=" ) ;
		urlVariables.add( "yaml", NAMESPACE_YAML ) ;

		ResponseEntity<ObjectNode> postResponse = restTemplate.postForEntity( url, urlVariables,
				ObjectNode.class ) ;

		logger.info( "postResponse: {}", postResponse ) ;

		logger.info( "Passed: {}          post url: {} \n\t create response: {}",
				postResponse.getStatusCode( ).equals( HttpStatus.OK ),
				url,
				postResponse.getBody( ).at( "/response-sh" ).asText( "no-response-found" ) ) ;

		//
		// Common configuration for restTemplate PUT and DELETE support
		//

		HttpHeaders headers = new HttpHeaders( ) ;
		headers.setContentType( MediaType.APPLICATION_FORM_URLENCODED ) ;
		restTemplate.getMessageConverters( ).add( new FormHttpMessageConverter( ) ) ;

		//
		// Update Namespace
		//
		// urlVariables.replace( "pass", List.of( "broken password") ) ;

		HttpEntity<MultiValueMap<String, String>> putRequestSettings = new HttpEntity<>( urlVariables, headers ) ;

		ResponseEntity<ObjectNode> putResponse = //
				restTemplate.exchange( url, HttpMethod.PUT, putRequestSettings, ObjectNode.class ) ;

		logger.info( "Passed: {}          put url: {} \n\t update response: {}",
				putResponse.getStatusCode( ).equals( HttpStatus.OK ),
				url,
				putResponse.getBody( ).at( "/response-sh" ).asText( "no-response-found" ) ) ;

		//
		// Delete Namespace
		//

		HttpEntity<MultiValueMap<String, String>> deleteRequestSettings = new HttpEntity<>( urlVariables, headers ) ;
		ResponseEntity<ObjectNode> deleteResponse = //
				restTemplate.exchange( url, HttpMethod.DELETE, deleteRequestSettings, ObjectNode.class ) ;

		logger.info( "Passed: {}          delete url: {} \n\t delete response: {}",
				deleteResponse.getStatusCode( ).equals( HttpStatus.OK ),
				url,
				deleteResponse.getBody( ).at( "/response-sh" ).asText( "no-response-found" ) ) ;

	}

	@Disabled
	@Test
	public void verifyInsecureRest ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		CsapRestTemplateFactory factory = new CsapRestTemplateFactory( null, null ) ;

		RestTemplate sslTestTemplate = factory.buildDefaultTemplate( "junit", false, 10, 10, 5, 60, 300 ) ;

		String sslUrl = "https://csap-secure.yourcompany.com/admin/api/application/health" ;

		String sslResponse = sslTestTemplate.getForObject( sslUrl, String.class ) ;

		logger.info( "url: {} \n\t response: {}", sslUrl, sslResponse ) ;

		// RestTemplate restTemplate = factory.buildDefaultTemplate( "junit",
		// false, 10, 10, 5, 60, 300 );
		//
		// String url =
		// "http://testhost.yourcompany.com:8011/CsAgent/api/agent/health" ;
		// String response = restTemplate.getForObject( url , String.class ) ;
		//
		// logger.info( "url: {} \n\t response: {}", url,response );

		// RestTemplate sslErrorsIgnoreTemplate = new RestTemplate(
		// factory.buildFactoryDisabledSslChecks( "junit", 10, 10 )) ;
		// url = "https://10.127.41.116:8011/CsAgent/api/agent/health" ;
		// response = sslErrorsIgnoreTemplate.getForObject( url , String.class )
		// ;
		//
		// logger.info( "url: {} \n\t response: {}", url,response );

	}

	@Test
	public void nio_filesystem_traversal ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		try (
				Stream<Path> filesystemPaths = Files.list( new File( "/" ).toPath( ) ) ) {

			var folderListing = filesystemPaths
					.map( Path::getFileName )
					.map( Path::toString )
					.collect( Collectors.joining( "\n\t" ) ) ;

			logger.info( "folderListing: {}", folderListing ) ;

		} catch ( Exception e ) {

			logger.info( "failed getting listing: {}", CSAP.buildCsapStack( e ) ) ;

		}

		try (
				Stream<Path> filesystemPaths = Files.list( new File( "/" ).toPath( ) ) ) {

			var folderListing = filesystemPaths
					.map( Path::getFileName )
					.map( Path::toString )
					.sorted( )
					.collect( Collectors.joining( "\n\t" ) ) ;

			logger.info( "folderListing sorted: {}", folderListing ) ;

		} catch ( Exception e ) {

			logger.info( "failed getting listing: {}", CSAP.buildCsapStack( e ) ) ;

		}

		// try (
		// Stream<Path> filesystemPaths = Files.list( new File( "/" ).toPath() )) {
		//
		// var folderListing = filesystemPaths
		// .filter( Files::isRegularFile )
		// .map( Path::getFileName )
		// .map( Path::toString )
		// .collect( Collectors.joining( "\n\t" ) ) ;
		//
		// logger.info( "regular files: {}", folderListing ) ;
		//
		// } catch ( Exception e) {
		// logger.info( "failed getting listing: {}", CSAP.buildCsapStack( e ) );
		// }
		//
		// try (
		// Stream<Path> filesystemPaths = Files.list( new File( "/ss" ).toPath() )) {
		//
		// var folderListing = filesystemPaths
		// .filter( Files::isRegularFile )
		// .filter( path -> path.getFileName().toString().endsWith( ".zip" ) )
		// .map( Path::getFileName )
		// .map( Path::toString )
		// .collect( Collectors.joining( "\n\t" ) ) ;
		//
		// logger.info( "regular files ending in txt:\n {}", folderListing ) ;
		//
		// } catch ( Exception e) {
		// logger.info( "failed getting listing: {}", CSAP.buildCsapStack( e ) );
		// }

	}

	Double byteToMb = 1024 * 1024D ;
	DecimalFormat gbFormat = new DecimalFormat( "#.#Gb" ) ;

	String getMb ( long num ) {

		logger.info( "num: {}", num ) ;
		return gbFormat.format( num / byteToMb ) ;

	}

	DecimalFormat percentFormat = new DecimalFormat( "#.#%" ) ;

	String getPercent ( double num ) {

		logger.info( "num: {}", num ) ;
		return percentFormat.format( num ) ;

	}

	@Test
	public void verifyGB ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		OperatingSystemMXBean s = ManagementFactory.getOperatingSystemMXBean( ) ;
		var osStats = (com.sun.management.OperatingSystemMXBean) s ;

		logger.info( "com.sun.management.OperatingSystemMXBean memory: {} , sys: {}, process: {}",
				getMb( osStats.getTotalPhysicalMemorySize( ) ),
				getPercent( osStats.getSystemCpuLoad( ) ),
				getPercent( osStats.getProcessCpuLoad( ) ) ) ;

		MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean( ) ;
		logger.info( "memoryMXBean heap committed: {} , heap used: {}, non-heap commited: {}",
				getMb( memoryMXBean.getHeapMemoryUsage( ).getCommitted( ) ),
				getMb( memoryMXBean.getHeapMemoryUsage( ).getUsed( ) ),
				getMb( memoryMXBean.getNonHeapMemoryUsage( ).getCommitted( ) ) ) ;

	}

	@Test
	public void verifyDoubleParse ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		ObjectNode test = jsonMapper.createObjectNode( ) ;
		ObjectNode nested = test.putObject( "test" ) ;

		nested.put( "$numberLong", "17" ) ;

		logger.info( "object: {} : value: {}", test.toString( ), test.asDouble( ) ) ;

		double d = 5500 / 1000d ;

		logger.info( "d: {} ", d ) ;

	}

	@Test
	public void verifyJsonParsing ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		ObjectNode test = jsonMapper.createObjectNode( ) ;
		test.put( "hi-there", "from me" ) ;

		logger.info( "object: {} : value: {}", test.toString( ), test.at( "/hi-there" ).asText( ) ) ;

	}

	@Test
	public void verifyStringRegex ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		String params = "serviceName=[ServletSample_8091], javaOpts=[-DcsapJava8 -Xms128M -Xmx128M ], runtime=[tomcat8.x], scmUserid=[someDeveloper],"
				+ " scmPass=[ws0zIy4CVuhWgkjKwebi0/y69yQMiJTr], scmBranch=[trunk], hotDeploy=[null], targetScpHosts=[], scmCommand=[-X -Dmaven.test.skip=true clean package, "
				+ "-X -Dmaven.test.skip=true clean package], mavenDeployArtifact=[null]" ;

		logger.info( "params: \n{}\n\n{}", params, params.replaceAll( "\\bscmPass[^\\s]*", "scmPass[*MASKED*]," ) ) ;

		logger.info( "alphanumeric only: \n{}\n\n{}", params, params.replaceAll( "[^A-Za-z0-9]", "_" ) ) ;

	}

	@Test
	public void lifeReplace ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		String params = "JAVA_OPTS=$life -Djava.rmi.server.hostname=localhost -Dcom.sun.management.jmxremote.port=8046 -Dcom.sun.management.jmxremote.rmi.port=8046 -Dcom.sun.management.jmxremote.local.only=false -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false  -DcsapProcessId=ServletSample_8041  -Djava.security.egd=file:/dev/./urandom -DcsapDockerJava" ;

		logger.info( "params: \n{}\n\n{}\n{}", params,
				params.replaceAll( Matcher.quoteReplacement( "$life" ), Matcher.quoteReplacement( "$peter" ) ) ) ;

		// String result = params.trim().replaceAll( "\\" +
		// CSAP.SERVICE_PARAMETERS, "-D test.life=$dev".replaceAll( "$", "\\$" )
		// );
		//
		// logger.info( "CSAP.SERVICE_PARAMETERS: {}", result);

	}

	@Test
	public void simpleJson ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		ObjectMapper jsonMapper = new ObjectMapper( ) ;

		ObjectNode full = jsonMapper.createObjectNode( ) ;

		ArrayNode hostsDef = full.putArray( "hosts" ) ;
		hostsDef.add( "csap-dev01" ) ;
		hostsDef.add( "csap-dev02" ) ;

		List<String> hosts = jsonMapper.readValue( full.path( "hosts" ).traverse( ),
				new TypeReference<List<String>>( ) {
				} ) ;

		logger.info( "hosts: {}", hosts ) ;

	}

	@Test
	public void verifyStringExtract ( ) {

		logger.info( CsapApplication.testHeader( ) ) ;

		String descLine = "users:((\"nginx\",pid=17497,fd=39),(\"nginx\",pid=17493,fd=39)," ;
		var users = descLine.split( "\"", 3 ) ;

		if ( users.length == 3 ) {

			descLine = users[1] ;

		}

		logger.info( "descLine: {}", descLine ) ;
		var testSplit = "::1:6443" ;// "::ffff:127.0.0.1:7016" ; //kube-apiserver: ::1:6443,::1:9885

		logger.info( "testSplit: size: {} , {}", testSplit.split( ":" ).length, Arrays.asList( testSplit.split(
				":" ) ) ) ;

	}

	@Test
	public void verifyMatches ( ) {

		logger.info( CsapApplication.testHeader( ) ) ;

		String prod = "prod" ;

		logger.info( "Matched: {}", prod.matches( "prod" ) ) ;
		logger.info( "Matched: {}", prod.matches( "p(?!rod)" ) ) ;

		String dev = "dev" ;

		logger.info( "Matched: {}", dev.matches( "p(?!rod)" ) ) ;
		logger.info( "Matched: {}", dev.matches( "p(?!rod)" ) ) ;
		logger.info( "^d.* Matched: {}", dev.matches( "^sd.*" ) ) ;

		String buildResponse = "peter \nDiffEntry[MODIFY Services/testFileForGitJunits.txt]" ;

		Pattern searchWithNewLinesPattern = Pattern.compile(
				".*" + Pattern.quote( "DiffEntry[MODIFY" ) + ".*testFileForGitJunits.txt.*", Pattern.DOTALL ) ;

		logger.info( "buildResponse: {} contains",
				searchWithNewLinesPattern.matcher( buildResponse ).find( ) ) ;
		// buildResponse.matches(
		// ".*" + Pattern.quote( "DiffEntry[MODIFY" ) +
		// ".*testFileForGitJunits.txt.*") );

		var orMatch = "(calico-etcd|csap-test-k8s-service-container|nginx-container|heapster|grafana)" ;
		var source = "csap-test-k8s-service-container" ;
		logger.info( "source {} pattern:{} matches: {}", source, orMatch, source.matches( orMatch ) ) ;

		// logger.info( "source {} pattern:{} matches: {}", "peter", "peter123",
		// "peter".matches( "peter123") ) ;
	}

	public class SlashEscapes extends CharacterEscapes {

		/**
		 *
		 */
		private static final long serialVersionUID = 1L ;
		private final int[] asciiEscapes ;

		public SlashEscapes ( ) {

			// start with set of characters known to require escaping
			// (double-quote, backslash etc)
			int[] esc = CharacterEscapes.standardAsciiEscapesForJSON( ) ;
			// esc['/'] = CharacterEscapes.ESCAPE_STANDARD ;
			esc['/'] = CharacterEscapes.ESCAPE_CUSTOM ;
			asciiEscapes = esc ;

		}

		// this method gets called for character codes 0 - 127
		@Override
		public int[] getEscapeCodesForAscii ( ) {

			return asciiEscapes ;

		}

		// and this for others; we don't need anything special here
		@Override
		public SerializableString getEscapeSequence ( int i ) {

			if ( i == '/' ) {

				return new SerializableString( ) {

					@Override
					public String getValue ( ) {

						return "\\/" ;

					}

					@Override
					public int charLength ( ) {

						return 2 ;

					}

					@Override
					public char[] asQuotedChars ( ) {

						return new char[] {
								'\\', '/'
						} ;

					}

					@Override
					public byte[] asUnquotedUTF8 ( ) {

						return new byte[] {
								'\\', '/'
						} ;

					}

					@Override
					public byte[] asQuotedUTF8 ( ) {

						return new byte[] {
								'\\', '/'
						} ;

					}

					@Override
					public int writeUnquotedUTF8 ( OutputStream out )
						throws IOException {

						return 0 ;

					}

					@Override
					public int writeQuotedUTF8 ( OutputStream out )
						throws IOException {

						return 0 ;

					}

					@Override
					public int putUnquotedUTF8 ( ByteBuffer out )
						throws IOException {

						return 0 ;

					}

					@Override
					public int putQuotedUTF8 ( ByteBuffer buffer )
						throws IOException {

						return 0 ;

					}

					@Override
					public int appendUnquotedUTF8 ( byte[] buffer , int offset ) {

						return 0 ;

					}

					@Override
					public int appendUnquoted ( char[] buffer , int offset ) {

						return 0 ;

					}

					@Override
					public int appendQuotedUTF8 ( byte[] buffer , int offset ) {

						return 0 ;

					}

					@Override
					public int appendQuoted ( char[] buffer , int offset ) {

						return 0 ;

					}
				} ;

			} else {

				return null ;

			}

		}
	}

	@Test
	public void verify_big_decimal_rounding ( )
		throws IOException {

		logger.info( CsapApplication.testHeader( ) ) ;

		double memory = 0.15732585356684092 ;
		logger.info( "Simple : {}, full: {}", CSAP.roundIt( 1.23456, 2 ), CSAP.roundIt( memory, 2 ) ) ;

		ObjectMapper mapper = new ObjectMapper( ) ;
		mapper.getFactory( ).setCharacterEscapes( new SlashEscapes( ) ) ;
		ObjectNode o = mapper.createObjectNode( ) ;
		o.put( "/demo/here", "/some/location" ) ;
		String encodedValue = mapper.writeValueAsString( o ) ;
		logger.info( "object: {}, encodedValue: {}", o.toString( ), encodedValue ) ;

		logger.info( "encodedValue read: {}", mapper.readTree( encodedValue ).toString( ) ) ;

	}

	@Test
	public void verifyFileRegex ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		File f = new File( "/aTemp/alertify.js" ) ;

		logger.info( "Absolute: {}, cannonical: {}", f.getAbsolutePath( ), f.getCanonicalPath( ) ) ;

		Pattern p = Pattern.compile( ".*aTemp.*ale.*" ) ;
		Matcher m = p.matcher( Pattern.quote( f.getCanonicalPath( ) ) ) ;

		logger.info( "Pattern quoted: {} Matches: {}", p.toString( ), m.matches( ) ) ;

		p = Pattern.compile( ".*aTemp.*ale.*" ) ;
		m = p.matcher( f.getCanonicalPath( ) ) ;

		logger.info( "Pattern raw: {} Matches: {}", p.toString( ), m.matches( ) ) ;

		p = Pattern.compile( ".*\\\\aTemp.*ale.*" ) ;
		m = p.matcher( Pattern.quote( f.getCanonicalPath( ) ) ) ;
		logger.info( "Pattern: {} Matches: {}", p.toString( ), m.matches( ) ) ;

		p = Pattern.compile( ".*\\\\aTemp.*ale.*" ) ;
		m = p.matcher( f.getCanonicalPath( ) ) ;
		logger.info( "Pattern Raw: {} Matches: {}", p.toString( ), m.matches( ) ) ;

		String ctl = "├─puppet.service" ;

		p = Pattern.compile( "[^\\-].*\\.service" ) ;
		m = p.matcher( ctl ) ;
		logger.info( "Pattern Raw: {} Matches: {}, then {}", p.toString( ), m.matches( ), m.group( ) ) ;

	}

	@Test
	public void verifyStringList ( ) {

		logger.info( CsapApplication.testHeader( ) ) ;

		List<String> list = Arrays.asList( "Bob", "Steve", "Jim", "Arbby" ) ;

		List<String> upperCaseList = list.stream( )
				.map( String::toUpperCase )
				.collect( Collectors.toList( ) ) ;

		Collections.reverse( list ) ;
		List<String> upperCaseListReverse = list.stream( )
				.map( String::toUpperCase )
				.collect( Collectors.toList( ) ) ;

		logger.info( "list: {} \n upper: {} \n upperCaseListReverse: {} ", list, upperCaseList, upperCaseListReverse ) ;

		for ( int i = 0; i < list.size( ); i++ ) {

			list.set( i, list.get( i ).toUpperCase( ) ) ;

		}

		logger.info( "list: {}", list ) ;

	}

	@Test
	public void buildDateList ( ) {

		logger.info( CsapApplication.testHeader( ) ) ;

		LocalDate today = LocalDate.now( ) ;
		logger.info( "now: {} ", today.format( DateTimeFormatter.ofPattern( "yyyy-MM-dd" ) ) ) ;

		List<String> past10days = LongStream
				.rangeClosed( 1, 10 )
				.mapToObj( day -> today.minusDays( day ) )
				.map( offsetDate -> offsetDate.format( DateTimeFormatter.ofPattern( "yyyy-MM-dd" ) ) )
				.collect( Collectors.toList( ) ) ;

		List<String> past10daysReverse = LongStream.iterate( 10, e -> e - 1 )
				.limit( 10 )
				.mapToObj( day -> today.minusDays( day ) )
				.map( offsetDate -> offsetDate.format( DateTimeFormatter.ofPattern( "yyyy-MM-dd" ) ) )
				.collect( Collectors.toList( ) ) ;

		logger.info( "past10days: {} \n reverse: {}", past10days, past10daysReverse ) ;

	}

	@Disabled
	@Test
	public void ip ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		System.out.println( "localhost: " + InetAddress.getLocalHost( ).getHostAddress( ) ) ;

		System.out.println( "dev1 lookup: " + InetAddress.getByName( "10.22.14.153" ).getCanonicalHostName( ) ) ;
		System.out.println( "dev2 lookup: " + InetAddress.getByName( "10.22.13.240" ).getCanonicalHostName( ) ) ;

		NetworkInterface
				.getNetworkInterfaces( ).asIterator( ).forEachRemaining( ni -> {

					String niAddresses = ni.inetAddresses( )
							.map( address -> {

								return address.getHostName( ) + " - " + address.getHostAddress( ) ;

							} )
							.filter( interfaceLine -> interfaceLine.contains( "10.22.14.153" ) )
							.collect( Collectors.joining( "\n\t" ) ) ;
					System.out.println( "addresses: " + niAddresses ) ;

				} ) ;

		Stream<NetworkInterface> niStream = StreamSupport.stream(
				Spliterators.spliteratorUnknownSize( NetworkInterface
						.getNetworkInterfaces( ).asIterator( ), Spliterator.ORDERED ),
				false ) ;

		String niAddresses = niStream.flatMap( ni -> ni.getInterfaceAddresses( ).stream( ) )
				.map( address -> address.getAddress( ).getCanonicalHostName( ) )
				.collect( Collectors.joining( "\n\t" ) ) ;

		System.out.println( "niAddresses: " + niAddresses ) ;

	}

}
