package org.sample.locator ;

import java.io.PrintWriter ;
import java.time.LocalDateTime ;
import java.time.format.DateTimeFormatter ;

import org.csap.integations.CsapInformation ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.beans.factory.annotation.Autowired ;
import org.springframework.cache.annotation.Cacheable ;
import org.springframework.ui.Model ;
import org.springframework.web.bind.annotation.GetMapping ;
import org.springframework.web.bind.annotation.RestController ;

@RestController
public class SimpleUI {

	final Logger logger = LoggerFactory.getLogger( SimpleUI.class ) ;

	/**
	 * 
	 * Simple example swapping out jsp for thymeleaf
	 * 
	 * @see http://www.thymeleaf.org/doc/articles/thvsjsp.html
	 * 
	 * @param springViewModel
	 * @return
	 */
	@GetMapping ( "/missingTemplate" )
	public String missingTempate ( Model springViewModel ) {

		logger.info( "Sample thymeleaf controller" ) ;

		springViewModel.addAttribute( "dateTime",
				LocalDateTime.now( ).format( DateTimeFormatter.ofPattern( "HH:mm:ss,   MMMM d  uuuu " ) ) ) ;

		// templates are in: resources/templates/*.html
		// leading "/" is critical when running in a jar

		return "/missingTempate" ;

	}

	@Autowired
	CsapInformation csapInformation ;

	@GetMapping ( "/secure/hello" )
	public String helloWithRestAcl ( ) {

		logger.info( "simple log" ) ;
		return "helloWithRestAcl - Hello from " + csapInformation.getHostName( ) + " at "
				+ LocalDateTime.now( ).format( DateTimeFormatter.ofPattern( "HH:mm:ss,   MMMM d  uuuu " ) ) ;

	}

	@GetMapping ( "/currentTime" )
	public void viewHello ( PrintWriter writer ) {

		logger.info( "SpringMvc writer" ) ;

		writer.println( "currentTime: " + LocalDateTime.now( ).format( DateTimeFormatter.ofPattern(
				"HH:mm:ss,   MMMM d  uuuu " ) ) ) ;

		// templates are in: resources/templates/*.html
		// leading "/" is critical when running in a jar

		return ;

	}

	@Cacheable ( value = "sampleCacheWithNoExpirations" , key = "{'timeUsingCache-' + #key }" )
	@GetMapping ( "/time-using-cache" )
	public String timeUsingCache ( String key ) {

		return "key: " + key + " cached time: " +
				LocalDateTime.now( )
						.format( DateTimeFormatter
								.ofPattern( "HH:mm:ss:nnnn,   MMMM d  uuuu " ) ) ;

	}

}
