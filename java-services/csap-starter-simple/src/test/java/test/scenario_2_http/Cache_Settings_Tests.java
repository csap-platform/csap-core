package test.scenario_2_http ;

import static org.assertj.core.api.Assertions.assertThat ;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get ;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status ;

import java.util.stream.Collectors ;

import org.csap.helpers.CSAP ;
import org.csap.helpers.CsapApplication ;
import org.csap.integations.CsapInformation ;
import org.junit.jupiter.api.BeforeAll ;
import org.junit.jupiter.api.Test ;
import org.junit.jupiter.api.TestInstance ;
import org.sample.Csap_Simple_Application ;
import org.sample.HelloService ;
import org.sample.SimpleLandingPage ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.beans.factory.annotation.Autowired ;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest ;
import org.springframework.boot.test.mock.mockito.MockBean ;
import org.springframework.http.HttpHeaders ;
import org.springframework.security.test.context.support.WithMockUser ;
import org.springframework.test.context.ActiveProfiles ;
import org.springframework.test.context.ContextConfiguration ;
import org.springframework.test.web.servlet.MockMvc ;
import org.springframework.web.servlet.resource.ResourceUrlProvider ;

import com.fasterxml.jackson.databind.ObjectMapper ;

@TestInstance ( TestInstance.Lifecycle.PER_CLASS )

@WebMvcTest ( controllers = SimpleLandingPage.class )

@ContextConfiguration ( classes = Csap_Simple_Application.class )

@ActiveProfiles ( "junit" )

class Cache_Settings_Tests {

	Logger logger = LoggerFactory.getLogger( getClass( ) ) ;

	static {

		CsapApplication.initialize( "" ) ;

	}

	@Autowired
	ResourceUrlProvider mvcResourceUrlProvider ;

	@MockBean
	CsapInformation csapInfo ;

	@MockBean
	HelloService helloService ;

	@Autowired
	ObjectMapper jsonMapper ;

	@BeforeAll
	void beforeAll ( ) {

		logger.info( CsapApplication.testHeader( "loading test data" ) ) ;

	}

	@Test
	@WithMockUser
	void verify_cache_for_controller ( @Autowired MockMvc mockMvc ) throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		var urlPath = SimpleLandingPage.SIMPLE_GET ;

		var resultActions = mockMvc.perform( get( urlPath )
				.contentType( "application/json" ) )
				.andExpect( status( ).isOk( ) ) ;

		var response = resultActions.andReturn( ).getResponse( ) ;

		//
		// verify content
		//
		var content = response.getContentAsString( ) ;

		logger.info( CSAP.buildDescription( "landing page",
				"urlPath", urlPath,
				"content", content ) ) ;

		var pageReport = jsonMapper.readTree( content ) ;

		assertThat( pageReport.path( "hi" ).asText( ) )
				.isEqualTo( "there" ) ;

		//
		// verify headers
		//
		var headerListing = response.getHeaderNames( ).stream( )
				.map( headerName -> CSAP.pad( headerName ) + response.getHeader( headerName ) )
				.collect( Collectors.joining( "\n\t" ) ) ;

		logger.info( "headerListing: \n\t{} ", headerListing ) ;

		assertThat( response.getHeader( HttpHeaders.CACHE_CONTROL ) )
				.isEqualTo( "no-cache, no-store, max-age=0, must-revalidate" ) ;

	}

	@Test
	@WithMockUser
	void verify_cache_for_js_modules ( @Autowired MockMvc mockMvc ) throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		var urlPath = "/js/modules/simple/_simple-main.js" ;

		var cacheBustUrl = mvcResourceUrlProvider.getForLookupPath( urlPath ) ;

		assertThat( cacheBustUrl ).matches( "/js/.*/modules/simple/_simple-main.js" ) ;

		var resultActions = mockMvc.perform( get( cacheBustUrl )
				.contentType( "application/json" ) )
				.andExpect( status( ).isOk( ) ) ;

		var response = resultActions.andReturn( ).getResponse( ) ;

		//
		// verify headers
		//
		var headerListing = response.getHeaderNames( ).stream( )
				.map( headerName -> CSAP.pad( headerName ) + response.getHeader( headerName ) )
				.collect( Collectors.joining( "\n\t" ) ) ;

		logger.info( "headerListing: \n\t{} ", headerListing ) ;

		assertThat( response.getHeader( HttpHeaders.CACHE_CONTROL ) )
				.isEqualTo( "max-age=31536000" ) ;

		//
		// verify content
		//
		var content = response.getContentAsString( ) ;

		logger.info( CSAP.buildDescription( "js module",
				"urlPath", urlPath,
				"cacheBustUrl", cacheBustUrl,
				"content", String.format( "%.100s", content ) ) ) ;

		assertThat( content )
				.contains( "_dom.onReady" ) ;

	}

	@Test
	@WithMockUser
	void verify_cache_for_js_non_modules ( @Autowired MockMvc mockMvc ) throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		var urlPath = "/js/junit-cache-test.js" ;

		var cacheBustUrl = mvcResourceUrlProvider.getForLookupPath( urlPath ) ;

		assertThat( cacheBustUrl ).matches( "/js/junit-cache-test-.*.js" ) ;

		var resultActions = mockMvc.perform( get( cacheBustUrl )
				.contentType( "application/json" ) )
				.andExpect( status( ).isOk( ) ) ;

		var response = resultActions.andReturn( ).getResponse( ) ;

		//
		// verify headers
		//
		var headerListing = response.getHeaderNames( ).stream( )
				.map( headerName -> CSAP.pad( headerName ) + response.getHeader( headerName ) )
				.collect( Collectors.joining( "\n\t" ) ) ;

		logger.info( "headerListing: \n\t{} ", headerListing ) ;

		assertThat( response.getHeader( HttpHeaders.CACHE_CONTROL ) )
				.isEqualTo( "max-age=31536000" ) ;

		//
		// verify content
		//
		var content = response.getContentAsString( ) ;

		logger.info( CSAP.buildDescription( "js module",
				"urlPath", urlPath,
				"cacheBustUrl", cacheBustUrl,
				"content", String.format( "%.100s", content ) ) ) ;

		assertThat( content )
				.contains( "console.log" ) ;

	}

	// http://localhost.yourcompany.org:8080/images/16x16/help-white.svg

	@Test
	@WithMockUser
	void verify_cache_for_images ( @Autowired MockMvc mockMvc ) throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		var urlPath = "/images/16x16/help-white.svg" ;

		//
		//  cache busting NOT enabled for images
		//
		var cacheBustUrl = mvcResourceUrlProvider.getForLookupPath( urlPath ) ;
		assertThat( urlPath )
				.isEqualTo( cacheBustUrl ) ;

		var resultActions = mockMvc.perform( get( urlPath )
				.contentType( "application/json" ) )
				.andExpect( status( ).isOk( ) ) ;

		var response = resultActions.andReturn( ).getResponse( ) ;

		//
		// verify headers
		//
		var headerListing = response.getHeaderNames( ).stream( )
				.map( headerName -> CSAP.pad( headerName ) + response.getHeader( headerName ) )
				.collect( Collectors.joining( "\n\t" ) ) ;

		logger.info( "headerListing: \n\t{} ", headerListing ) ;

		assertThat( response.getHeader( HttpHeaders.CACHE_CONTROL ) )
				.isEqualTo( "max-age=31536000" ) ;

		//
		// verify content
		//
		var content = response.getContentAsString( ) ;

		logger.info( CSAP.buildDescription( "image",
				"urlPath", urlPath,
				"cacheBustUrl", cacheBustUrl,
				"content", content ) ) ;

		assertThat( content )
				.contains( "svg xmlns" ) ;

	}

	@Test
	@WithMockUser
	void verify_cache_for_css ( @Autowired MockMvc mockMvc ) throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		var urlPath = "/css/csap.css" ;

		var cacheBustUrl = mvcResourceUrlProvider.getForLookupPath( urlPath ) ;

		assertThat( cacheBustUrl ).matches( "/css/csap-.*.css" ) ;

		var resultActions = mockMvc.perform( get( cacheBustUrl )
				.contentType( "application/json" ) )
				.andExpect( status( ).isOk( ) ) ;

		var response = resultActions.andReturn( ).getResponse( ) ;

		//
		// verify headers
		//
		var headerListing = response.getHeaderNames( ).stream( )
				.map( headerName -> CSAP.pad( headerName ) + response.getHeader( headerName ) )
				.collect( Collectors.joining( "\n\t" ) ) ;

		logger.info( "headerListing: \n\t{} ", headerListing ) ;

		assertThat( response.getHeader( HttpHeaders.CACHE_CONTROL ) )
				.isEqualTo( "max-age=31536000" ) ;

		//
		// verify content
		//
		var content = response.getContentAsString( ) ;

		logger.info( CSAP.buildDescription( "js module",
				"urlPath", urlPath,
				"cacheBustUrl", cacheBustUrl,
				"content", String.format( "%.100s", content ) ) ) ;

		assertThat( content )
				.contains( "csap-scrollable" ) ;

	}

}
