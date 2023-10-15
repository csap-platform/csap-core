package test.scenario_2_http ;

import static org.assertj.core.api.Assertions.assertThat ;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user ;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get ;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content ;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status ;

import org.csap.helpers.CsapApplication ;
import org.csap.integations.CsapSecurityConfiguration ;
import org.csap.security.config.CsapSecurityRoles ;
import org.junit.jupiter.api.Disabled ;
import org.junit.jupiter.api.Test ;
import org.sample.Csap_Simple_Application ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.beans.factory.annotation.Autowired ;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc ;
import org.springframework.boot.test.context.SpringBootTest ;
import org.springframework.context.annotation.Bean ;
import org.springframework.http.MediaType ;
import org.springframework.test.context.ActiveProfiles ;
import org.springframework.test.web.servlet.MockMvc ;
import org.springframework.test.web.servlet.ResultActions ;

import com.fasterxml.jackson.databind.ObjectMapper ;
import com.gargoylesoftware.htmlunit.WebClient ;
import com.gargoylesoftware.htmlunit.html.HtmlPage ;

//@WebAppConfiguration

@SpringBootTest ( classes = {
		
		Spring_Http_Endpoints.SwitchSecurityToBasicForTesting.class, 
		Csap_Simple_Application.class

} )
@AutoConfigureMockMvc
@ActiveProfiles ( {
		"junit",
		"junit-security"
} )
public class Spring_Http_Endpoints {
	final static private Logger logger = LoggerFactory.getLogger( Spring_Http_Endpoints.class ) ;

	static {

		CsapApplication.initialize( "" ) ;

	}

	public static class SwitchSecurityToBasicForTesting {

		@Bean
		public CsapSecurityConfiguration.CustomHttpSecurity myDemSecurityPolicy ( ) {

			CsapSecurityConfiguration.CustomHttpSecurity mySecurity = ( httpSecurity -> {

				logger.info( CsapApplication.testHeader( "Enabling basic auth for mock testing" ) ) ;
				httpSecurity.httpBasic( ) ;

			} ) ;

			return mySecurity ;

		}
	}

	ObjectMapper jacksonMapper = new ObjectMapper( ) ;

	@Test
	public void http_get_landing_page ( @Autowired MockMvc mockMvc )
		throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		// mock does much validation.....
		ResultActions resultActions = mockMvc.perform(
				get( "/" )

						.with(
								user( "some-random-username" )
										.roles(
												"AUTHENTICATED",
												CsapSecurityRoles.ADMIN_ROLE,
												CsapSecurityRoles.VIEW_ROLE ) )

						.param( "sampleParam1", "sampleValue1" )
						.param( "sampleParam2", "sampleValue2" )
						.accept( MediaType.TEXT_PLAIN ) ) ;

		//
		String result = resultActions
				.andExpect( status( ).isOk( ) )
				.andExpect( content( ).contentType( "text/html;charset=UTF-8" ) )
				.andReturn( ).getResponse( ).getContentAsString( ) ;

		logger.debug( "result:\n {}", result ) ;

		var pageTitle = "" ;

		try ( WebClient webClient = new WebClient( ) ) {

			webClient.getOptions( ).setThrowExceptionOnScriptError( false ) ;
			webClient.getOptions( ).setJavaScriptEnabled( false ) ;
			webClient.getOptions( ).setCssEnabled( false ) ;
			webClient.getOptions( ).setThrowExceptionOnFailingStatusCode( false ) ;

			final HtmlPage page = webClient.loadHtmlCodeIntoCurrentWindow( result ) ;
			// work with the html page
			var body = page.querySelector( "body" ) ;

			logger.debug( CsapApplication.header( "Body: {}" ), body.asXml( ) ) ;

			// pageHeader = body.querySelector( "header" ).asXml( ) ;
			pageTitle = page.getElementById( "csap-page-title" ).asXml( ) ;

			// page.getElementById( "" ) ;
		}

		logger.info( CsapApplication.header( "header: {}" ), pageTitle ) ;

		assertThat( pageTitle )
				.contains( "span" )
				.contains( "csapPageVersion" )
				.contains( "MyDesktop" ) ;

	}

	@Test
	public void http_get_hello_endpoint ( @Autowired MockMvc mockMvc )
		throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		// mock does much validation.....
		ResultActions resultActions = mockMvc.perform(
				get( "/hello" )

						.with(
								user( "some-random-username" )
										.roles(
												"AUTHENTICATED",
												CsapSecurityRoles.ADMIN_ROLE,
												CsapSecurityRoles.VIEW_ROLE ) )

						.accept( MediaType.TEXT_PLAIN ) ) ;

		//
		String result = resultActions
				.andExpect( status( ).isOk( ) )
				.andExpect( content( ).contentType( "text/plain;charset=UTF-8" ) )
				.andReturn( ).getResponse( ).getContentAsString( ) ;
		logger.info( "result:\n" + result ) ;

		assertThat( result )
				.startsWith( "Hello" ) ;

	}

	@Disabled // need to move to webtests
	@Test
	public void http_get_hello_round_robin ( @Autowired MockMvc mockMvc )
		throws Exception {

		logger.info( CsapApplication.TC_HEAD + "simple mvc test" ) ;
		// mock does much validation.....
		ResultActions resultActions = mockMvc.perform(
				get( "/helloRoundRobin" )

						.with(
								user( "some-random-username" )
										.roles(
												"AUTHENTICATED",
												CsapSecurityRoles.ADMIN_ROLE,
												CsapSecurityRoles.VIEW_ROLE ) )

						.accept( MediaType.TEXT_PLAIN ) ) ;

		//
		String result = resultActions
				.andExpect( status( ).isOk( ) )
				.andExpect( content( ).contentType( "text/plain;charset=UTF-8" ) )
				.andReturn( ).getResponse( ).getContentAsString( ) ;
		logger.info( "result:\n" + result ) ;

		assertThat( result )
				.startsWith( "Response: Hello" ) ;

	}

}
