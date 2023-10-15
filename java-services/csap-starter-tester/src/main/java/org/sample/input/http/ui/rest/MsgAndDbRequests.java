package org.sample.input.http.ui.rest ;

import java.io.File ;
import java.io.FileWriter ;
import java.io.IOException ;
import java.net.URL ;
import java.nio.file.Files ;
import java.sql.Connection ;
import java.sql.DriverManager ;
import java.sql.ResultSet ;
import java.sql.SQLException ;
import java.util.ArrayList ;
import java.util.Arrays ;
import java.util.Collections ;
import java.util.List ;
import java.util.Map ;
import java.util.Random ;
import java.util.concurrent.Executors ;
import java.util.concurrent.ScheduledExecutorService ;

import javax.inject.Inject ;
import javax.servlet.http.HttpServletRequest ;
import javax.servlet.http.HttpServletResponse ;
import javax.servlet.http.HttpSession ;
import javax.ws.rs.core.MediaType ;

import org.apache.commons.io.FileUtils ;
import org.csap.docs.CsapDoc ;
import org.csap.helpers.CSAP ;
import org.csap.helpers.CsapApplication ;
import org.csap.integations.micrometer.CsapMeterUtilities ;
import org.csap.security.config.CsapSecurityRoles ;
import org.sample.Csap_Tester_Application ;
import org.sample.JmsConfig ;
import org.sample.jpa.DemoEvent ;
import org.sample.jpa.DemoManager ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.beans.factory.InitializingBean ;
import org.springframework.beans.factory.annotation.Autowired ;
import org.springframework.beans.factory.annotation.Qualifier ;
import org.springframework.beans.factory.annotation.Value ;
import org.springframework.http.HttpEntity ;
import org.springframework.http.HttpHeaders ;
import org.springframework.http.HttpStatus ;
import org.springframework.http.ResponseEntity ;
import org.springframework.http.client.SimpleClientHttpRequestFactory ;
import org.springframework.jms.core.JmsTemplate ;
import org.springframework.jms.listener.DefaultMessageListenerContainer ;
import org.springframework.util.LinkedMultiValueMap ;
import org.springframework.util.MultiValueMap ;
import org.springframework.web.bind.annotation.GetMapping ;
import org.springframework.web.bind.annotation.PostMapping ;
import org.springframework.web.bind.annotation.RequestMapping ;
import org.springframework.web.bind.annotation.RequestParam ;
import org.springframework.web.bind.annotation.ResponseBody ;
import org.springframework.web.bind.annotation.ResponseStatus ;
import org.springframework.web.bind.annotation.RestController ;
import org.springframework.web.client.RestTemplate ;

import com.fasterxml.jackson.core.JsonGenerationException ;
import com.fasterxml.jackson.databind.JsonMappingException ;
import com.fasterxml.jackson.databind.JsonNode ;
import com.fasterxml.jackson.databind.ObjectMapper ;
import com.fasterxml.jackson.databind.node.ArrayNode ;
import com.fasterxml.jackson.databind.node.ObjectNode ;

/**
 * 
 * Spring MVC "Controller" class using Annotations that are discovered when the
 * spring config file is loaded.
 * 
 * @link
 * @author pnightin
 * 
 * @see <a href=
 *      "http://static.springsource.org/spring/docs/3.2.x/spring-framework-reference/html/mvc.html#mvc-introduction">
 *      Spring Mvc </a>
 * 
 * 
 * @see <a href=
 *      "http://download.oracle.com/javase/tutorial/jmx/remote/custom.html"> JDK
 *      JMX docs </a>
 * 
 * 
 * @see <a href=
 *      "http://static.springsource.org/spring/docs/3.2.x/spring-framework-reference/html/jmx.html">
 *      Spring JMX docs </a>
 * 
 *      Container Initialization:
 *      <p>
 *      <IMG SRC="doc-files/mvc.jpg">
 *      <p>
 *      <IMG SRC="doc-files/spring.jpg">
 */
@RestController
@RequestMapping ( Csap_Tester_Application.SPRINGREST_URL )
@CsapDoc ( title = "Messaging and DB demos and tests" , notes = {
		"Many JPA and JMS examples are included to demonstrate both code and performance",
		"<a class='csap-link' target='_blank' href='https://github.com/csap-platform/csap-core/wiki'>learn more</a>",
		"<img class='csapDocImage' src='CSAP_BASE/images/csapboot.png' />"
} )
public class MsgAndDbRequests implements InitializingBean {
	final Logger logger = LoggerFactory.getLogger( getClass( ) ) ;

	/**
	 * 
	 * Note: constructor injection is preferred programming model for dependency
	 * injection
	 * 
	 */

	@Inject
	public MsgAndDbRequests ( DemoManager dao ) {

		this.demoDataService = dao ;

	}

	@Autowired ( required = false )
	JmsTemplate jmsTemplate ;

	DemoManager demoDataService ;

	/**
	 * As simple as it gets
	 * 
	 * @return
	 */

	// @RequestMapping("/hello")
	@RequestMapping ( value = "/hello" , produces = MediaType.TEXT_PLAIN )
	public String helloWorld ( ) {

		return "hello" ;

	}

	public static String JAVA8_MESSAGE = "helloJava8UsingLambdasAndStreams" ;

	// http://spring.io/blog/2014/11/17/springone2gx-2014-replay-java-8-language-capabilities-what-s-in-it-for-you
	@RequestMapping ( value = "/helloJava8" , produces = MediaType.TEXT_PLAIN )
	public String helloJava8 ( ) {

		StringBuilder result = new StringBuilder( JAVA8_MESSAGE ) ;
		List<Integer> values = Arrays.asList( 1, 2, 3, 4, 5, 6 ) ;

		// java 7 generics
		// for (int e: values) {
		// result.append(e) ;
		// }

		// java 8 with consumer
		// values.forEach( new Consumer<Integer>() {
		// @Override
		// public void accept(Integer t) {
		// result.append( t) ;
		// }
		// });

		// java 8 with lambda
		// values.forEach( (Integer value) -> result.append(value) ) ;

		// java 8 with type inference
		// values.forEach( ( value) -> result.append(value) ) ;

		// java 8 with cast replace
		// values.forEach( value -> result.append(value) ) ;

		// java 8 with method reference
		values.forEach( result::append ) ;

		int sumOfList = values.stream( )
				.map( e -> e * 2 )
				.reduce( 0,
						( c , e ) -> c + e ) ;

		result.append( "Sum of List: " + sumOfList ) ;

		return result.toString( ) ;

	}

	@RequestMapping ( "/testNullPointer" )
	public String testNullPointer ( ) {

		if ( System.currentTimeMillis( ) > 1 )
			throw new NullPointerException( "For testing only" ) ;

		return "hello" ;

	}

	ObjectMapper jsonMapper = new ObjectMapper( ) ;

	@Autowired ( required = false )
	JmsConfig jmsConfig ;

	@RequestMapping ( "/sendNewJms" )
	public ObjectNode sendNewJms ( ) {

		ObjectNode resultJson = jsonMapper.createObjectNode( ) ;

		if ( jmsConfig == null ) {

			resultJson.put( "messageSent", false ) ;
			resultJson.put( "note", "verify that mq configuration is configured" ) ;
			return resultJson ;

		}

		try {

			logger.info( "Sending Message to: " + jmsConfig.getSimpleQueueName( ) ) ;
			jmsTemplate.convertAndSend( jmsConfig.getSimpleQueueName( ), "Hello" ) ;
			resultJson.put( "Sent: ", jmsConfig.getSimpleQueueName( ) ) ;

		} catch ( Exception e ) {

			logger.error( "Failed sending message", e ) ;

			resultJson.put( "messageSent", false ) ;
			resultJson.put( "note", "verify that mq configuration is configured" ) ;
			resultJson.put( "reason", CSAP.buildCsapStack( e ) ) ;

		}

		return resultJson ;

	}

	public static final String DATA_200 = "PADDED7890PADDED7890PADDED7890PADDED7890PADDED7890PADDED7890PADDED7890PADDED7890PADDED7890PADDED7890"
			+
			"PADDED7890PADDED7890PADDED7890PADDED7890PADDED7890PADDED7890PADDED7890PADDED7890PADDED7890PADDED7890" ;

	/**
	 * 
	 * Simple method for doing bulk JPA inserts. Convenient for test impacts of db
	 * colocation, etc.
	 * 
	 * @param filter
	 * @param message
	 * @param count
	 * @param payloadPadding
	 * @param request
	 * @param response
	 * @throws JsonGenerationException
	 * @throws JsonMappingException
	 * @throws IOException
	 */
	@PostMapping ( value = "/addBulkData" , produces = MediaType.APPLICATION_JSON )
	public void addBulkData (
								@RequestParam ( defaultValue = DemoManager.TEST_TOKEN ) String filter ,
								@RequestParam String message ,
								@RequestParam ( value = "count" , defaultValue = "1" , required = false ) int count ,
								@RequestParam ( value = "payloadPadding" , required = false ) String payloadPadding ,
								HttpServletRequest request ,
								HttpServletResponse response )
		throws JsonGenerationException ,
		JsonMappingException ,
		IOException {

		logger.info( "Loading Bulk data, message: " + message + " numMessages:" + count
				+ " payloadPadding: " + payloadPadding ) ;

		response.setContentType( MediaType.APPLICATION_JSON ) ;

		// ObjectNode resultNode = testDao.showScheduleItemsWithFilter(filter,
		// 20);

		ObjectNode resultNode = jsonMapper.createObjectNode( ) ;

		ArrayNode recordsAdded = resultNode.arrayNode( ) ;
		long totalStart = System.currentTimeMillis( ) ;

		for ( int i = 0; i < count; i++ ) {

			DemoEvent jobScheduleInput = new DemoEvent( ) ;
			jobScheduleInput.setDemoField( "test Jndi name" ) ;
			// jobScheduleInput.setScheduleObjid(System.currentTimeMillis()); //
			// Never provide this as it is generated
			jobScheduleInput.setCategory( filter ) ;
			jobScheduleInput.setDescription( "Spring Consumer ======> "
					+ filter + " String: " + message ) ;

			if ( payloadPadding != null ) {

				jobScheduleInput.setDemoField( DATA_200 ) ;

			}

			try {

				jobScheduleInput = demoDataService.addSchedule( jobScheduleInput ) ;
				recordsAdded.add( jobScheduleInput.toString( ) ) ;

			} catch ( Exception e ) {

				recordsAdded.add( jobScheduleInput + "Failed due to: " + e.getMessage( ) ) ;

			}

			logger.debug( "Added with ID " + jobScheduleInput ) ;

		}

		resultNode.put( "totalTimeInSeconds", ( System.currentTimeMillis( ) - totalStart ) / 1000 ) ;
		resultNode.put( "averageTimeInMilliSeconds",
				( ( System.currentTimeMillis( ) - totalStart ) / count ) ) ;
		resultNode.set( "recordsAdded", recordsAdded ) ;

		response.getWriter( ).println(
				jsonMapper.writeValueAsString( resultNode ) ) ;

	}

	@Value ( "$secure{factorySample.madeup.password}" )
	private String samplePass ;
	@Value ( "$secure{factorySample.madeup.user}" )
	private String sampleUser ;

	@RequestMapping ( value = "/showSecureConfiguration" , produces = MediaType.APPLICATION_JSON )
	public ObjectNode showSecureConfiguration (
												@RequestParam ( defaultValue = DemoManager.TEST_TOKEN ) String filter ,
												HttpServletRequest request ,
												HttpServletResponse response )
		throws JsonGenerationException ,
		JsonMappingException ,
		IOException {

		logger.info( "Getting Test data" ) ;

		ObjectNode resultNode = jsonMapper.createObjectNode( ) ;
		resultNode.put( "factorySample.madeup.password", samplePass ) ;
		resultNode.put( "factorySample.madeup.user", sampleUser ) ;

		return resultNode ;

	}

	@RequestMapping ( value = "/sampleProtectedMethod" )
	public ObjectNode sampleProtectedMethod (
												@RequestParam ( defaultValue = DemoManager.TEST_TOKEN ) String filter ,
												HttpServletRequest request ,
												HttpServletResponse response )
		throws JsonGenerationException ,
		JsonMappingException ,
		IOException {

		logger.info( "url is protected in security config with a dummy group for demo purposes." ) ;

		response.setContentType( MediaType.APPLICATION_JSON ) ;

		ObjectNode resultNode = jsonMapper.createObjectNode( ) ;
		resultNode.put( "count", demoDataService.countRecordsUsingCriteriaWrapper( filter ) ) ;
		resultNode.put( "CsapSecurityConfiguration.PROTECTED_BY", CsapSecurityRoles.ADMIN_ROLE ) ;

		return resultNode ;

	}

	@GetMapping ( value = "/getRecordCountEz" , produces = MediaType.APPLICATION_JSON )
	public void record_count_using_CriteriaWrapper (
														@RequestParam ( defaultValue = DemoManager.TEST_TOKEN ) String filter ,
														HttpServletRequest request ,
														HttpServletResponse response )
		throws JsonGenerationException ,
		JsonMappingException ,
		IOException {

		logger.debug( "Getting Test data" ) ;

		response.setContentType( MediaType.APPLICATION_JSON ) ;

		ObjectNode resultNode = jsonMapper.createObjectNode( ) ;
		resultNode.put( "count", demoDataService.countRecordsUsingCriteriaWrapper( filter ) ) ;

		response.getWriter( ).println(
				jsonMapper.writeValueAsString( resultNode ) ) ;

	}

	@GetMapping ( value = "/recordQueryWithUai" , produces = MediaType.APPLICATION_JSON )
	public void record_query_using_CriteriaWrapper (
														@RequestParam ( defaultValue = DemoManager.TEST_TOKEN ) String filter ,
														@RequestParam ( defaultValue = "10" ) int showMax ,
														HttpServletRequest request ,
														HttpServletResponse response )
		throws JsonGenerationException ,
		JsonMappingException ,
		IOException {

		logger.debug( "Getting Test data" ) ;

		response.setContentType( MediaType.APPLICATION_JSON ) ;

		ObjectNode resultNode = jsonMapper.createObjectNode( ) ;
		resultNode.set( "results", demoDataService.findScheduleItemsUsingCriteriaWrapper( filter, showMax ) ) ;

		response.getWriter( ).println(
				jsonMapper.writeValueAsString( resultNode ) ) ;

	}

	/**
	 * 
	 * Simple Code sample demonstrating Spring resttemplate with a Jackson converter
	 * wired in. Since only a single attribute of the JSON object is of interest,
	 * the generic JsonNode is used rather then a pojo.
	 * 
	 * @param request
	 * @return
	 * 
	 * @see <a href=
	 *      "http://static.springsource.org/spring/docs/3.1.x/spring-framework-reference/html/remoting.html#rest-resttemplate">
	 *      Spring Docs </a>
	 * 
	 * @see <a href="http://wiki.fasterxml.com/JacksonInFiveMinutes"> Jackson
	 *      Interpreter </a>
	 * 
	 */

	@Autowired
	@Qualifier ( "csAgentRestTemplate" )
	private RestTemplate csAgentRestTemplate = null ;

	public static boolean isRunningOnDesktop ( ) {

		if ( ! CsapApplication.isCsapFolderSet( ) ) {

			return true ;

		}

		return false ;

	}

	@RequestMapping ( "/csAgentSampleRest" )
	@ResponseBody
	public ObjectNode csAgentSampleRest (
											HttpServletRequest request ,
											HttpServletResponse response )
		throws Exception {

		ObjectNode resultNode = jsonMapper.createObjectNode( ) ;
		resultNode.put( "success", false ) ;

		String targetJvm = "notFound" ;

		if ( request.getContextPath( ).length( ) > 1 ) {

			targetJvm = request.getContextPath( ).substring( 1 ) ;

		}

		String csAgentApiUrl = "http://localhost:8011/CsAgent/api/agent/runtime" ;

		// hook for desktop testing.
		if ( isRunningOnDesktop( ) ) {

			csAgentApiUrl = "http://csap-dev01.yourcompany.org:8011/api/agent/runtime" ;
			targetJvm = "csap-agent" ;

		}

		resultNode.put( "csAgentApiUrl", csAgentApiUrl ) ;
		resultNode.put( "targetJvm", targetJvm ) ;

		try {

			// Short Version:
			String urlForJvm = csAgentRestTemplate
					.getForObject(
							csAgentApiUrl,
							JsonNode.class, // only need 1 attribute, no need
											// for a pojo
							targetJvm// the rest parameter value
					).path( "serviceOpsQueue" ) // the JSON map key
					.asText( ) ;

			resultNode.put( "shortResult", urlForJvm ) ;
			logger.info( "Condensed result:" + urlForJvm ) ;

			// Long Version:

			// Template based param substitution for rest.
			Map<String, String> templateParams = Collections.singletonMap(
					"jvmName", targetJvm ) ;

			JsonNode jsonNode = csAgentRestTemplate.getForObject( csAgentApiUrl, JsonNode.class,
					templateParams ) ;

			resultNode.set( "longResult", jsonNode ) ;

			resultNode.put( "success", true ) ;

		} catch ( Exception e ) {

			var reason = CSAP.buildCsapStack( e ) ;
			logger.error( "Failed to get response {}", reason ) ;
			resultNode.put( "reason", reason ) ;

		}

		return resultNode ;

	}

	@RequestMapping ( Csap_Tester_Application.LARGE_PARAM_URL )
	public String largePayload (
									@RequestParam ( required = false ) String doc ,
									HttpServletRequest request ,
									HttpServletResponse response )
		throws IOException {

		logger.info( "received request" ) ;

		if ( doc == null ) {

			return "largePayload , doc request parameter is null" ;

		} else {

			return "largePayload , Size of doc request parameter: " + doc.length( ) ;

		}

	}

	@Autowired
	CsapMeterUtilities microMeterHelper ;

	@RequestMapping ( "/restParamPost" )
	public void restParamPost (
								@RequestParam String doc ,
								@RequestParam ( required = false , defaultValue = "1" ) int count ,
								HttpServletRequest request ,
								HttpServletResponse response )
		throws IOException {

		logger.info( "Build test data: count: {}, doc: {}", count, doc ) ;
		response.setContentType( "text/plain" ) ;

		StringBuilder testPostContent = new StringBuilder( ) ;

		for ( int i = 0; i < count; i++ ) {

			testPostContent.append( doc ) ;

		}

		response.getWriter( ).println( "Size of content: " + testPostContent.length( )
				+ " === By default posts larger then 2MB will fail" ) ;

		String restUrl = "http://localhost:"
				+ request.getServerPort( ) + request.getContextPath( )
				+ Csap_Tester_Application.SPRINGREST_URL + Csap_Tester_Application.LARGE_PARAM_URL ;

		SimpleClientHttpRequestFactory simpleClientRequestFactory = new SimpleClientHttpRequestFactory( ) ;
		simpleClientRequestFactory.setReadTimeout( 5000 ) ;
		simpleClientRequestFactory.setConnectTimeout( 5000 ) ;

		RestTemplate rest = new RestTemplate( simpleClientRequestFactory ) ;

		MultiValueMap<String, String> formParams = new LinkedMultiValueMap<String, String>( ) ;
		formParams.add( "doc", testPostContent.toString( ) ) ;

		var timer = microMeterHelper.startTimer( ) ;

		try {

			logger.info( "Hitting: " + restUrl ) ;
			// String result = rest.postForObject( restUrl, restReq,
			// String.class );

			ResponseEntity<String> restResponse = rest.postForEntity( restUrl,
					formParams, String.class ) ;
			// String result = rest.getForObject(restUrl, String.class);

			response.getWriter( ).println( "Response: " + restResponse.getBody( ) ) ;

		} catch ( Exception e ) {

			logger.error( "Failed sending " + doc, e ) ;
			response.getWriter( )
					.println( "Exception Sending: " + e.getMessage( ) ) ;

		}

		microMeterHelper.stopTimer( timer, "csap.payload.parameter.post" ) ;

	}

	@RequestMapping ( "/restBodyPost" )
	public void restBodyPost (
								@RequestParam String doc ,
								@RequestParam ( required = false , defaultValue = "1" ) int count ,
								HttpServletRequest request ,
								HttpServletResponse response )
		throws IOException {

		logger.info( "Build test data: count: {}, doc: {}", count, doc ) ;
		response.setContentType( "text/plain" ) ;

		StringBuilder testPostContent = new StringBuilder( ) ;

		for ( int i = 0; i < count; i++ ) {

			testPostContent.append( doc ) ;

		}

		response.getWriter( ).println( "Size of content: " + testPostContent.length( ) ) ;

		String restUrl = "http://localhost:"
				+ request.getServerPort( ) + request.getContextPath( )
				+ Csap_Tester_Application.JERSEY_URL + "/simpleSpringRest/dummyHost" ;

		SimpleClientHttpRequestFactory simpleClientRequestFactory = new SimpleClientHttpRequestFactory( ) ;
		simpleClientRequestFactory.setReadTimeout( 5000 ) ;
		simpleClientRequestFactory.setConnectTimeout( 5000 ) ;

		RestTemplate rest = new RestTemplate( simpleClientRequestFactory ) ;

		HttpHeaders headers = new HttpHeaders( ) ;
		headers.setContentType( org.springframework.http.MediaType.APPLICATION_JSON ) ;
		HttpEntity<String> restReq = new HttpEntity<String>( testPostContent.toString( ), headers ) ;

		var timer = microMeterHelper.startTimer( ) ;

		try {

			logger.info( "Hitting: " + restUrl ) ;
			String result = rest.postForObject( restUrl, restReq, String.class ) ;
			// String result = rest.getForObject(restUrl, String.class);

			response.getWriter( ).println( "Response: " + result ) ;

		} catch ( Exception e ) {

			logger.error( "Failed sending " + doc, e ) ;
			response.getWriter( )
					.println( "Exception Sending: " + e.getMessage( ) ) ;

		}

		microMeterHelper.stopTimer( timer, "csap.payload.body.post" ) ;

	}

	public final static String TEST_DATA = "0123456789" ;

	@RequestMapping ( value = "/diskTest" , produces = MediaType.TEXT_PLAIN )
	@ResponseBody
	public String diskTest (
								int numberOfIterations ,
								int numberOfKb )
		throws IOException {

		StringBuilder result = new StringBuilder( ) ;

		logger.warn( "running test: {} iterations, with {} kb ", numberOfIterations, numberOfKb ) ;

		int rwIterations = 0 ;
		long totalBytesWritten = 0 ;
		long totalBytesRead = 0 ;

		for ( rwIterations = 0; rwIterations < numberOfIterations; rwIterations++ ) {

			File testFile = new File( folderToCreateFilesIn, "rwTest.txt" ) ;

			try ( FileWriter writer = new FileWriter( testFile ) ) {

				int bytesWritten = 0 ;

				for ( bytesWritten = 0; bytesWritten < numberOfKb * 1024; bytesWritten = bytesWritten + TEST_DATA
						.length( ) ) {

					writer.write( TEST_DATA ) ;
					totalBytesWritten += TEST_DATA.length( ) ;

				}

			}

			for ( int i = 0; i < 5; i++ ) {

				try {

					String content = new String( Files.readAllBytes( testFile.toPath( ) ) ) ;
					totalBytesRead += content.length( ) ;

				} catch ( IOException e ) {

					// TODO Auto-generated catch block
					e.printStackTrace( ) ;

				}

			}

			;
			testFile.delete( ) ;

		}

		result.append(
				"Files created and deleted: " + rwIterations
						+ "\n Total Data Written: " + totalBytesWritten / 1024 / 1024 + "Mb"
						+ "\n Total Data Read: " + totalBytesRead / 1024 / 1024 + "Mb" ) ;

		result.append( "\n===================\n\n" ) ;

		return result.toString( ) ;

	}

	List<FileWriter> leakFiles = new ArrayList<FileWriter>( ) ;

	@Value ( "${user.dir:current}" )
	private String folderToCreateFilesIn = "" ;

	@RequestMapping ( value = "/leakFileDescriptors" , produces = MediaType.TEXT_PLAIN )
	public String leakFileDescriptors (
										@RequestParam ( value = "numberToLeak" , required = true ) int numberFilesToTryToOpen )
		throws Exception {

		StringBuilder result = new StringBuilder( ) ;
		logger.warn( "Leaking VM descriptors: " + numberFilesToTryToOpen + " Creating in location:"
				+ folderToCreateFilesIn ) ;

		File leakContainer = new File( folderToCreateFilesIn, "leakContainer" ) ;

		result.append(
				"\n\n ========== File Leak Test: container: " + leakContainer.getAbsolutePath( ) + " number:"
						+ numberFilesToTryToOpen + "\n\n" ) ;

		leakContainer.mkdirs( ) ;

		int fileOpenCount = 0 ;

		for ( fileOpenCount = 0; fileOpenCount < numberFilesToTryToOpen; fileOpenCount++ ) {

			File leakFile = new File( leakContainer, "leak_" + fileOpenCount + "_" + System.currentTimeMillis( ) ) ;

			FileWriter writer ;

			try {

				writer = new FileWriter( leakFile ) ;

			} catch ( Exception e ) {

				result.append( "Failed to open at: " + fileOpenCount ) ;
				break ;

			}

			writer.write( "Leaking File" ) ;

			// not closing

			leakFiles.add( writer ) ;

		}

		result.append( "Number of files opened: " + fileOpenCount ) ;

		result.append(
				"Remember to clean up when done. Note CSAP polls files every 5 minutes to avoid performance impacts." ) ;

		result.append( "\n===================\n\n" ) ;

		return result.toString( ) ;

	}

	@RequestMapping ( value = "/cleanFileDescriptors" , produces = MediaType.TEXT_PLAIN )
	@ResponseBody
	public String cleanFileDescriptors ( )
		throws IOException {

		StringBuilder result = new StringBuilder( ) ;

		logger.warn( "Removing VM descriptors: " + folderToCreateFilesIn ) ;

		File leakContainer = new File( folderToCreateFilesIn, "leakContainer" ) ;

		int numClosed = 0 ;

		for ( FileWriter writer : leakFiles ) {

			writer.flush( ) ;
			writer.close( ) ;
			numClosed++ ;

		}

		leakFiles.clear( ) ;
		result.append( "Files Closed: " + numClosed ) ;

		FileUtils.deleteDirectory( leakContainer ) ;
		result.append(
				"\n\n ========== Deleted Folder: " + leakContainer.getAbsolutePath( ) + "\n\n" ) ;

		result.append( "\n===================\n\n" ) ;

		return result.toString( ) ;

	}

	List<Thread> threadList = new ArrayList<Thread>( ) ;

	@RequestMapping ( value = "/startThreads" , produces = MediaType.TEXT_PLAIN )
	@ResponseBody
	public String startThreads (
									@RequestParam ( value = "numberToLeak" , required = true ) int numberFilesToTryToOpen )
		throws Exception {

		StringBuilder result = new StringBuilder( ) ;
		logger.warn( "startThreads: " + numberFilesToTryToOpen ) ;

		int fileOpenCount = 0 ;
		Runnable r = new Runnable( ) {

			@Override
			public void run ( ) {

				while ( true ) {

					try {

						Thread.sleep( 500 ) ;

					} catch ( InterruptedException e ) {

						// TODO Auto-generated catch block
						e.printStackTrace( ) ;

					}

				}

			}
		} ;

		for ( fileOpenCount = 0; fileOpenCount < numberFilesToTryToOpen; fileOpenCount++ ) {

			Thread t = new Thread( r ) ;
			t.setName( "CsapThreadLeakTest-" + fileOpenCount ) ;
			t.setDaemon( true ) ;
			t.start( ) ;
			threadList.add( t ) ;

		}

		result.append( "Number of threads  Started: " + fileOpenCount ) ;

		result.append( "Remember to clean up when done" ) ;

		result.append( "\n===================\n\n" ) ;

		return result.toString( ) ;

	}

	@RequestMapping ( value = "/cleanThreads" , produces = MediaType.TEXT_PLAIN )
	@ResponseBody
	public String cleanThreads ( )
		throws IOException {

		StringBuilder result = new StringBuilder( ) ;

		logger.warn( "Closing threads:  " + threadList.size( ) ) ;

		int numClosed = 0 ;

		for ( Thread thread : threadList ) {

			try {

				thread.stop( ) ;

			} catch ( Exception e ) {

				logger.error( "Failed to stop", e ) ;

			}

			numClosed++ ;

		}

		threadList.clear( ) ;
		result.append( "Threads Stopped: " + numClosed ) ;

		return result.toString( ) ;

	}

	// static List<String> leakStringList = new ArrayList<String>();
	Random randomGenerator = new Random( ) ;

	@RequestMapping ( value = "/leakMemory" , produces = MediaType.TEXT_PLAIN )
	@ResponseBody
	public String leakMemory (
								HttpSession session ,
								@RequestParam ( value = "mbToLeak" , required = true ) int mbToLeak ,
								@RequestParam ( value = "kbToLeak" , required = true ) int kbToLeak )
		throws Exception {

		StringBuilder result = new StringBuilder( "\n\nLeaked: " + mbToLeak + "Mb, " + kbToLeak + " Kb\n" ) ;
		logger.warn( "Leaking: {} mb {} Kb", mbToLeak, mbToLeak ) ;

		for ( int j = 0; j < mbToLeak; j++ ) {

			StringBuffer buffer = new StringBuffer( ) ;

			for ( int i = 0; i < 1024 * 1024; i++ ) {

				buffer.append( randomGenerator.nextInt( 9 ) ) ;

			}

			String t = buffer.toString( ) ;
			leakStringList.add( t ) ;

			// logger.info( "Size of string added: " + t); ;
		}

		for ( int j = 0; j < kbToLeak; j++ ) {

			StringBuffer buffer = new StringBuffer( ) ;

			for ( int i = 0; i < 1024; i++ ) {

				buffer.append( randomGenerator.nextInt( 9 ) ) ;

			}

			String t = buffer.toString( ) ;
			leakStringList.add( t ) ;

			// logger.info( "Size of string added: " + t); ;
		}

		result.append( "Remember to clean up when done, current Items in List: " + leakStringList.size( ) ) ;

		result.append( "\n===================\n\n" ) ;

		return result.toString( ) ;

	}

	private static volatile ArrayList<String> leakStringList = new ArrayList<String>( ) ;

	@RequestMapping ( value = "/freeMemory" , produces = MediaType.TEXT_PLAIN )
	@ResponseBody
	public String freeMemory ( HttpSession session )
		throws Exception {

		StringBuilder result = new StringBuilder(
				"\n\n ===========================\n\n current Mbs in List: " + leakStringList.size( ) ) ;

		leakStringList.clear( ) ;
		result.append( "\nList now contains: " + leakStringList.size( ) ) ;
		result.append( "\n===================\n\n" ) ;

		return result.toString( ) ;

	}

	@RequestMapping ( "/testOracleHangConnection" )
	public void testOracleHangConnection (
											@RequestParam ( value = "url" , required = true ) String url ,
											@RequestParam ( value = "query" , required = true ) String query ,
											@RequestParam ( value = "user" , required = true ) String user ,
											@RequestParam ( value = "pass" , required = true ) String pass ,
											HttpServletRequest request ,
											HttpServletResponse response )
		throws IOException {

		if ( logger.isDebugEnabled( ) )
			logger.debug( " url:" + url + " query" + query ) ;

		StringBuilder resultsBuff = new StringBuilder(
				"\n\nTesting connection: " ) ;
		Connection jdbcConnection = null ;
		ResultSet rs = null ;

		try {

			Class.forName( "oracle.jdbc.driver.OracleDriver" ) ;
			jdbcConnection = DriverManager.getConnection( url, user, pass ) ;

			// resultsBuff.append(jdbcConnection.createStatement().executeQuery("select
			// count(*) from job_schedule").getString(1))
			// ;
			rs = jdbcConnection.createStatement( ).executeQuery( query ) ;

			while ( rs.next( ) ) {

				resultsBuff.append( rs.getString( 1 ) ) ;

			}

		} catch ( ClassNotFoundException e ) {

			resultsBuff.append( CSAP.buildCsapStack( e ) ) ;

		} catch ( SQLException e ) {

			resultsBuff.append( CSAP.buildCsapStack( e ) ) ;

		} finally {

			try {

				resultsBuff
						.append( "\n\n NOTE: This is a destructive test used to demonstrate open ports and failing to close connections." ) ;
				resultsBuff
						.append( "\n ================= RESTART THIS JVM WHEN DEMO COMPLETED ===================" ) ;

				// rs.close() ;
				// jdbcConnection.close() ;
			} catch ( Exception e ) {

				logger.error( "Failed to close:", e ) ;

			}

		}

		response.setContentType( "text/plain" ) ;
		response.getWriter( ).print(
				"\n\n ========== Results from: " + " url:" + url + " query"
						+ query + "\n\n" ) ;

		response.getWriter( ).println( resultsBuff ) ;

		response.getWriter( ).println( "\n===================\n\n" ) ;

	}

	@RequestMapping ( "/showJmeterResults" )
	public void showJmeterResults (
									HttpServletRequest request ,
									HttpServletResponse response )
		throws Exception {

		if ( logger.isDebugEnabled( ) )
			logger.debug( " entered" ) ;

		response.setContentType( MediaType.TEXT_HTML ) ;

		// String path = request.getServletContext()
		// .getRealPath("/jmeter-reports");
		URL path = getClass( ).getResource( "/static/jmeter-reports" ) ;
		// response.getWriter().print(
		// "\n\n ========== Results from: " + " url:" + url + " query"
		// + query + "\n\n");
		//
		response.getWriter( ).println( "<br/><br/>Files in " + path ) ;

		File resultDir = new File( path.toURI( ) ) ;

		response.getWriter( ).println(
				"<br/><br/><a href=\"showJmeterResults\">refresh</a><br><br>" ) ;
		response.getWriter( )
				.println(
						"<br/><a href=\"clearJmeterResults\">clearJmeterResults</a><br/><br/>" ) ;

		File resultFiles[] = resultDir.listFiles( ) ;

		if ( resultFiles != null ) {

			Arrays.sort( resultFiles ) ;

			for ( File fileName : resultFiles ) {

				if ( ! fileName.getName( ).startsWith( "." ) )
					response.getWriter( ).println(
							"<br><a href=\"../jmeter-reports/"
									+ fileName.getName( ) + "\">"
									+ fileName.getName( ) + "</a>" ) ;

			}

		}

		response.getWriter( ).println( "<br><br>" ) ;

	}

	@RequestMapping ( "/clearJmeterResults" )
	public void clearJmeterResults (
										HttpServletRequest request ,
										HttpServletResponse response )
		throws Exception {

		if ( logger.isDebugEnabled( ) )
			logger.debug( " entered" ) ;

		response.setContentType( MediaType.TEXT_HTML ) ;

		URL path = getClass( ).getResource( "/static/jmeter-reports" ) ;
		// response.getWriter().print(
		// "\n\n ========== Results from: " + " url:" + url + " query"
		// + query + "\n\n");
		//
		response.getWriter( ).println( "Deleted Files in " + path ) ;

		File resultFiles = new File( path.toURI( ) ) ;

		for ( File fileName : resultFiles.listFiles( ) ) {

			if ( ! fileName.getName( ).startsWith( "." ) )
				fileName.delete( ) ;

		}

		response.getWriter( )
				.println(
						"<br><a href=\"showJmeterResults\">showJmeterResults</a><br><br>" ) ;
		response.getWriter( ).println( "<br>===================<br>" ) ;

	}

	@RequestMapping ( "/testOracle" )
	public void testOci (
							@RequestParam ( value = "url" , required = true ) String reqUrl ,
							@RequestParam ( value = "query" , required = true ) String query ,
							@RequestParam ( value = "user" , required = true ) String user ,
							@RequestParam ( value = "pass" , required = true ) String pass ,
							HttpServletRequest request ,
							HttpServletResponse response )
		throws IOException {

		String url = reqUrl ;

		if ( reqUrl.indexOf( "SIMON_REAL_DRV" ) != -1 ) {

			// Stripping Simon from path since raw jdbc does not support
			url = url.replace( "simon:", "" ) ;
			url = url.substring( 0, url.indexOf( ";SIMON_REAL_DRV" ) ) ;

		}

		if ( logger.isDebugEnabled( ) )
			logger.debug( " url:" + url + " query" + query ) ;

		StringBuilder resultsBuff = new StringBuilder(
				"\nResults: " ) ;
		Connection jdbcConnection = null ;
		ResultSet rs = null ;

		try {

			// Class.forName( "oracle.jdbc.driver.OracleDriver" );
			jdbcConnection = DriverManager.getConnection( url, user, pass ) ;

			// resultsBuff.append(jdbcConnection.createStatement().executeQuery("select
			// count(*) from job_schedule").getString(1))
			// ;
			rs = jdbcConnection.createStatement( ).executeQuery( query ) ;

			while ( rs.next( ) ) {

				resultsBuff.append( rs.getString( 1 ) ) ;

			}

		} catch ( Exception e ) {

			String message = "Failed to direct test: " + CSAP.buildFilteredStack( e, "org.sample" ) ;
			logger.error( message ) ;
			resultsBuff.append( message ) ;

		} finally {

			try {

				rs.close( ) ;
				jdbcConnection.close( ) ;

			} catch ( Exception e ) {

				logger.error( "Failed to close: {}", CSAP.buildFilteredStack( e, "org.sample" ) ) ;

			}

		}

		response.setContentType( "text/plain" ) ;
		response.getWriter( ).print(
				"\n\n Testing Connection using: \n\t url: " + url + "\n\t query"
						+ query + "\n" ) ;

		response.getWriter( ).println( resultsBuff ) ;

	}

	/**
	 * 
	 * Mostly for Demo/POC!
	 * 
	 * Simple hook for running with: -XX:MaxHeapFreeRatio=20 -XX:MinHeapFreeRatio=10
	 * 
	 * On linux - heap does not appear to be reclaimed until after a couple of major
	 * gc's
	 * 
	 * @param event
	 */
	public void afterPropertiesSet ( ) {

		// Runnable recalimRunnable = new Runnable() {
		//
		// @Override
		// public void run() {
		//
		// logger.warn("Post startup trigger to do a system.gc. On linux this is
		// to reclaim heap used by tomcat7 jar scanning on boot");
		//
		// System.gc();
		//
		// }
		// };
		// reclaimHeapExecutor.schedule(recalimRunnable, 10, TimeUnit.SECONDS);
		// reclaimHeapExecutor.schedule(recalimRunnable, 20, TimeUnit.SECONDS);
		//
		// reclaimHeapExecutor.schedule(recalimRunnable, 40, TimeUnit.SECONDS);

	}

	ScheduledExecutorService reclaimHeapExecutor = Executors
			.newScheduledThreadPool( 1 ) ;

	@Autowired ( required = false )
	private DefaultMessageListenerContainer sfMessageListener ;

	@RequestMapping ( "/stopJmsListener" )
	public void stopJmsListener ( HttpServletResponse response )
		throws IOException {

		sfMessageListener.stop( ) ;
		response.setContentType( "text/plain" ) ;
		response.getWriter( ).println( "jms listeners stopped" ) ;

	}

	@RequestMapping ( "/startJmsListener" )
	public void startJmsListener ( HttpServletResponse response )
		throws IOException {

		sfMessageListener.start( ) ;
		response.setContentType( "text/plain" ) ;
		response.getWriter( ).println( "jms listeners started" ) ;

	}

	@RequestMapping ( "/testPostParams" )
	public void dataFromModel (
								HttpServletRequest request ,
								HttpServletResponse response )
		throws Exception {

		Long dataSetViewId ;
		String dsvIdStr = request.getParameter( "data_set_view_id" ) ;
		String objectType = request.getParameter( "objectType" ) ;
		String dataSetType = request.getParameter( "dataSetType" ) ;

		response.setHeader( "Cache-Control", "no-cache" ) ;
		response.setContentType( MediaType.TEXT_HTML ) ;

		if ( dataSetType == null ) {

			logger.error( "\n\n\n dataSetType is null \n\n\n" ) ;

			response.getWriter( ).println( "fail" ) ;

			throw new InternalServerError( "dataset is null" ) ;

		} else {

			logger.debug( "dataSetType: " + dataSetType ) ;
			response.getWriter( ).println( "pass" ) ;

		}

	}

	@ResponseStatus ( value = HttpStatus.INTERNAL_SERVER_ERROR )
	public class InternalServerError extends RuntimeException {

		public InternalServerError ( String message ) {

			super( message ) ;

		}

		/**
		 * 
		 */
		private static final long serialVersionUID = 1L ;

	}

}
