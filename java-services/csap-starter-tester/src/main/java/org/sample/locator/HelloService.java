package org.sample.locator ;

import java.net.InetAddress ;
import java.net.UnknownHostException ;
import java.time.LocalDateTime ;
import java.time.format.DateTimeFormatter ;
import java.util.concurrent.atomic.AtomicInteger ;

import javax.inject.Inject ;
import javax.servlet.http.HttpSession ;

import org.csap.docs.CsapDoc ;
import org.jasypt.encryption.pbe.StandardPBEStringEncryptor ;
import org.sample.Csap_Tester_Application ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.http.MediaType ;
import org.springframework.web.bind.annotation.GetMapping ;
import org.springframework.web.bind.annotation.PathVariable ;
import org.springframework.web.bind.annotation.RequestMapping ;
import org.springframework.web.bind.annotation.RestController ;

import com.fasterxml.jackson.databind.ObjectMapper ;
import com.fasterxml.jackson.databind.node.ObjectNode ;

@RestController
@CsapDoc ( title = "Demo Service Locator" , notes = "A simple service for demoing core spring concepts" )
public class HelloService {

	final Logger logger = LoggerFactory.getLogger( HelloService.class ) ;

	@RequestMapping ( "/hello" )
	public String hello ( ) {

		logger.info( "simple log" ) ;
		return "Hello from " + HOST_NAME + " at "
				+ LocalDateTime.now( ).format( DateTimeFormatter.ofPattern( "HH:mm:ss,   MMMM d  uuuu " ) ) ;

	}

	@Inject
	private StandardPBEStringEncryptor encryptor ;

	@GetMapping ( value = "/encode" , produces = MediaType.TEXT_PLAIN_VALUE )
	public String encode ( String stringToEncode ) {

		logger.info( "stringToEncode: {}", stringToEncode ) ;
		return "stringToEncode: " + stringToEncode + " encoded: \n" + encryptor.encrypt( stringToEncode ) + "\n at "
				+ LocalDateTime.now( ).format( DateTimeFormatter.ofPattern( "HH:mm:ss,   MMMM d  uuuu " ) ) ;

	}

	@GetMapping ( "/decode" )
	public String decode ( String stringToDecode ) {

		logger.info( "decodeString: {}", stringToDecode ) ;

		if ( ! Csap_Tester_Application.isRunningOnDesktop( ) ) {

			return "Security Violoation: Decode only allowed on desktop envs" ;

		} else {

			return "stringToDecode: " + stringToDecode + " decoded: " + encryptor.decrypt( stringToDecode ) + " at "
					+ LocalDateTime.now( ).format( DateTimeFormatter.ofPattern( "HH:mm:ss,   MMMM d  uuuu " ) ) ;

		}

	}

	ObjectMapper jacksonMapper = new ObjectMapper( ) ;

	@RequestMapping ( "/helloJson" )
	@CsapDoc ( notes = "Output hello in json format. Optional support for JSONP if the callback parameter is included" , linkTests = {
			"JSON Example", "JSONP Example"
	} , linkGetParams = {
			"none=none", "callback=myDemoFunctionName"
	} )
	public ObjectNode helloJson ( ) {

		ObjectNode resultNode = jacksonMapper.createObjectNode( ) ;

		logger.info( "simple log" ) ;
		resultNode.put( "response", "Hello from " + HOST_NAME + " at "
				+ LocalDateTime.now( ).format( DateTimeFormatter.ofPattern( "HH:mm:ss,   MMMM d  uuuu " ) ) ) ;

		return resultNode ;

	}

	@RequestMapping ( {
			"sleep/{seconds}"
	} )
	public ObjectNode sleep ( @PathVariable int seconds )
		throws InterruptedException {

		ObjectNode resultJson = jacksonMapper.createObjectNode( ) ;
		logger.info( "sleeping for {} seconds", seconds ) ;

		resultJson.put( "seconds", seconds ) ;

		resultJson.put( "host", HOST_NAME ) ;

		resultJson.put( "start", LocalDateTime.now( ).format( DateTimeFormatter.ofPattern(
				"HH:mm:ss,   MMMM d  uuuu " ) ) ) ;

		Thread.sleep( seconds * 1000 ) ;

		resultJson.put( "done", LocalDateTime.now( ).format( DateTimeFormatter.ofPattern(
				"HH:mm:ss,   MMMM d  uuuu " ) ) ) ;
		return resultJson ;

	}

	@RequestMapping ( "/testException" )
	public String testException ( ) {

		logger.info( "simple log" ) ;
		throw new RuntimeException( "Spring Rest Exception" ) ;

	}

	private static String SAMPLE_SESSION_VAR = "sampleSessionVar" ;

	@RequestMapping ( "/addSessionVar" )
	public String addSessionVar ( HttpSession session ) {

		if ( session.getAttribute( SAMPLE_SESSION_VAR ) == null ) {

			session.setAttribute( SAMPLE_SESSION_VAR, new AtomicInteger( 0 ) ) ;

		}

		AtomicInteger val = (AtomicInteger) session.getAttribute( SAMPLE_SESSION_VAR ) ;
		int curValue = val.incrementAndGet( ) ;

		logger.info( "Updated session variable {} : {}", SAMPLE_SESSION_VAR, curValue ) ;

		// in order for spring session to replicate to redis, you must explicit set the
		// attribute
		// this is a performance optimization to avoid replicating everything every time
		session.setAttribute( SAMPLE_SESSION_VAR, val ) ;

		return HOST_NAME + ": Updated session variable " + SAMPLE_SESSION_VAR + " to: " + curValue ;

	}

	@RequestMapping ( "/testAclFailure" )
	public String testAclFailure ( ) {

		logger.info( "simple log" ) ;
		return "ACL page will be displayed if security is enabled in application.acl" ;

	}

	static String HOST_NAME = "notFound" ;

	static {

		try {

			HOST_NAME = InetAddress.getLocalHost( ).getHostName( ) ;

		} catch ( UnknownHostException e ) {

			HOST_NAME = "HOST_LOOKUP_ERROR" ;

		}

	}

}
