package org.sample ;

import java.io.PrintWriter ;
import java.security.Principal ;
import java.time.LocalDateTime ;
import java.time.format.DateTimeFormatter ;
import java.util.ArrayList ;
import java.util.Map ;
import java.util.Random ;
import java.util.concurrent.TimeUnit ;

import javax.inject.Inject ;
import javax.servlet.http.HttpServletRequest ;

import org.csap.CsapMonitor ;
import org.csap.docs.CsapDoc ;
import org.csap.helpers.CsapApplication ;
import org.csap.integations.CsapInformation ;
import org.csap.integations.micrometer.CsapMicroMeter ;
import org.csap.security.CsapUser ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.beans.factory.annotation.Autowired ;
import org.springframework.http.MediaType ;
import org.springframework.stereotype.Controller ;
import org.springframework.ui.Model ;
import org.springframework.web.bind.annotation.CrossOrigin ;
import org.springframework.web.bind.annotation.DeleteMapping ;
import org.springframework.web.bind.annotation.GetMapping ;
import org.springframework.web.bind.annotation.PostMapping ;
import org.springframework.web.bind.annotation.PutMapping ;
import org.springframework.web.bind.annotation.RequestBody ;
import org.springframework.web.bind.annotation.RequestMapping ;
import org.springframework.web.bind.annotation.RequestParam ;
import org.springframework.web.bind.annotation.ResponseBody ;

import com.fasterxml.jackson.databind.JsonNode ;
import com.fasterxml.jackson.databind.ObjectMapper ;
import com.fasterxml.jackson.databind.node.ObjectNode ;

import io.micrometer.core.annotation.Timed ;

@Controller
@CsapMonitor ( prefix = "LandPage" )
@CsapDoc ( title = "CSAP Landing Page Controller" , type = CsapDoc.PUBLIC , notes = {
		"Landing page provides simple technology demonstrations. Refer to @CsapDoc java doc for more usage examples.",
		"<a class='csap-link' target='_blank' href='https://github.com/csap-platform/csap-core/wiki'>learn more</a>",
		"<img class='csapDocImage' src='CSAP_BASE/images/csapboot.png' />"
} )
public class SimpleLandingPage {

	public static final String SIMPLE_GET = "/simple-get" ;

	final Logger logger = LoggerFactory.getLogger( getClass( ) ) ;

	@Inject
	CsapInformation csapInfo ;

	@Timed ( value = "csap.ui.page.landing" , description = "Root landing page" )
	@GetMapping ( value = "/" )
	public String get ( Model springViewModel )
		throws Exception {

		springViewModel.addAttribute( "helpPageExample", SimpleLandingPage.class.getCanonicalName( ) ) ;
		springViewModel.addAttribute( "docsController", csapInfo.getDocUrl( ) + "/class" ) ;

		TimeUnit.MILLISECONDS.sleep( ( new Random( ) ).nextInt( 50 ) ) ;

		return "simple-landing" ;

	}

	@GetMapping ( value = "/styles-only" )
	public String getSimple ( Model springViewModel )
		throws Exception {

		var formatedTime = LocalDateTime.now( ).format( DateTimeFormatter.ofPattern( "HH:mm:ss,   MMMM d  uuuu " ) ) ;

		logger.warn( CsapApplication.testHeader( formatedTime ) ) ;

		springViewModel.addAttribute( "simpleTime", formatedTime ) ;

		return "styles-only" ;

	}

	@Autowired
	CsapMicroMeter.HealthReport healthReport ;

	@CsapDoc ( notes = "test method for add error" )
	@GetMapping ( "/errors/add" )
	@ResponseBody
	public JsonNode errorAdd ( HttpServletRequest request ) {

		return healthReport.addTestError( request ) ;

	}

	@GetMapping ( "/maxConnections" )
	public String maxConnections ( Model springViewModel ) {

		return "maxConnections" ;

	}

	@Timed ( value = "csap.ui.current time" , description = "Root landing page" )
	@CrossOrigin ( )
	@CsapDoc ( linkTests = {
			"Test1", "TestWithOptionalParam"
	} , linkGetParams = {
			"param=value_1,anotherParam=Peter",
			"sampleOptionalList=test1"
	} , notes = {
			"Show the current time", "This is for demo only"
	} )
	// @RequestMapping("/currentTime")
	@GetMapping ( path = {
			"/currentTime", "/cors/currentTime"
	} )
	public void currentTimeGet (
									@RequestParam ( value = "mySampleParam" , required = false , defaultValue = "1.0" ) double sampleForTesting ,
									@RequestParam ( required = false ) ArrayList<String> sampleOptionalList ,
									String a ,
									PrintWriter writer ) {

		var formatedTime = LocalDateTime.now( ).format( DateTimeFormatter.ofPattern( "HH:mm:ss,   MMMM d  uuuu " ) ) ;

		logger.info( "Time now is: {}, sampleOptionalList: {}, a: {}", formatedTime, sampleOptionalList, a ) ;

		writer.println( "currentTime: " + formatedTime ) ;

		return ;

	}

	@CsapDoc ( linkTests = {
			"simple time with param"
	} , linkGetParams = {
			"demo=this demo",
	} , notes = {
			"Show the current time"
	} )
	// @RequestMapping("/currentTime")
	@GetMapping ( "/simpleTime" )
	public void simpleTime (
								String demo ,
								PrintWriter writer ) {

		var formatedTime = LocalDateTime.now( ).format( DateTimeFormatter.ofPattern( "HH:mm:ss,   MMMM d  uuuu " ) ) ;

		logger.info( "Time now is: {}, demo: {}", formatedTime, demo ) ;

		writer.println( "currentTime: " + formatedTime + " message: " + demo ) ;

		return ;

	}

	@DeleteMapping ( path = {
			"/currentTime"
	} )
	@ResponseBody
	public String currentTimeDelete ( ) {

		logger.info( "Deleted content" ) ;
		return "delete time response" ;

	}

	@DeleteMapping ( path = {
			"/currentTimeParam"
	} , consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE )
	@ResponseBody
	public String currentTimeDeleteParam ( @RequestBody Map<String, String> myParamMap ) {

		logger.info( "Deleted content parameters{}", myParamMap ) ;
		return "currentTimeDeleteParam time response" ;

	}

	@CsapDoc ( linkTests = {
			"Test1", "Test2"
	} , linkPostParams = {
			"param=value_1,anotherParam=Peter", "param=value_2"
	} , notes = {
			"Get the logged in user if security is enabled"
	} )
	@RequestMapping ( "/currentUser" )
	public void currentUser ( PrintWriter writer , Principal principle ) {

		logger.info( "SpringMvc writer" ) ;

		if ( principle != null ) {

			writer.println( "logged in user: " + principle.getName( ) ) ;

		} else {

			writer.println( "logged in user: principle is null - verify security is configured" ) ;

		}

		return ;

	}

	@GetMapping ( "/currentUserDetails" )
	@ResponseBody
	public JsonNode currentUserDetails ( ) {

		logger.info( "SpringMvc writer" ) ;
		// CustomUserDetails userDetails = (CustomUserDetails)
		// SecurityContextHolder.getContext().getAuthentication().getPrincipal() ;
		JsonNode info = CsapUser.getPrincipleInfo( ) ;

		// writer.println( "logged in user email: " + info.path( "mail" ).asText("not
		// found") ) ;
		// writer.println( "\n\n user information: \n" + CSAP.jsonPrint( info ) ) ;

		return info ;

	}

	@Autowired ( required = false )
	ObjectMapper jsonMapper ;

	@GetMapping ( SIMPLE_GET )
	@ResponseBody
	public JsonNode simpleGet ( ) {

		logger.info( CsapApplication.testHeader( ) ) ;

		ObjectNode simple = jsonMapper.createObjectNode( ) ;
		simple.put( "hi", "there" ) ;

		return simple ;

	}

	@PostMapping ( "/simple-post" )
	@ResponseBody
	public JsonNode simplePost ( String message ) {

		ObjectNode simple = jsonMapper.createObjectNode( ) ;
		simple.put( "hi", message ) ;

		return simple ;

	}

	@PutMapping ( "/simple-put" )
	@ResponseBody
	public JsonNode simplePut ( String message ) {

		ObjectNode simple = jsonMapper.createObjectNode( ) ;
		simple.put( "hi", message ) ;

		return simple ;

	}

	@DeleteMapping ( "/simple-delete" )
	@ResponseBody
	public JsonNode simpleDelete ( String message ) {

		ObjectNode simple = jsonMapper.createObjectNode( ) ;
		simple.put( "hi", message ) ;

		return simple ;

	}

	@Inject
	HelloService helloService ;

	@GetMapping ( "/testAsync" )
	@ResponseBody
	public String testAsync (
								@RequestParam ( value = "delaySeconds" , required = false , defaultValue = "5" ) int delaySeconds )
		throws Exception {

		String message = "Hello from " + this.getClass( ).getSimpleName( )
				+ " at " + LocalDateTime.now( ).format( DateTimeFormatter.ofPattern( "hh:mm:ss" ) ) ;
		helloService.printMessage( message, delaySeconds ) ;
		return "Look in logs for async to complete in: " + delaySeconds + " seconds" ;

	}

	@GetMapping ( "/testNullPointer" )
	public String testNullPointer ( ) {

		if ( System.currentTimeMillis( ) > 1 ) {

			throw new NullPointerException( "For testing only" ) ;

		}

		return "hello" ;

	}

	@GetMapping ( "/missingTemplate" )
	public String missingTempate ( Model springViewModel ) {

		logger.info( "Sample thymeleaf controller" ) ;

		springViewModel.addAttribute( "dateTime",
				LocalDateTime.now( ).format( DateTimeFormatter.ofPattern( "HH:mm:ss,   MMMM d  uuuu " ) ) ) ;

		// templates are in: resources/templates/*.html
		// leading "/" is critical when running in a jar
		return "/missingTemplate" ;

	}

	@GetMapping ( "/malformedTemplate" )
	public String malformedTemplate ( Model springViewModel ) {

		logger.info( "Sample thymeleaf controller" ) ;

		springViewModel.addAttribute( "dateTime",
				LocalDateTime.now( ).format( DateTimeFormatter.ofPattern( "HH:mm:ss,   MMMM d  uuuu " ) ) ) ;

		// templates are in: resources/templates/*.html
		// leading "/" is critical when running in a jar
		return "/MalformedExample" ;

	}

}
