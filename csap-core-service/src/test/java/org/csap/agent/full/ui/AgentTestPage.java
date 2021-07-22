package org.csap.agent.full.ui ;

import static org.assertj.core.api.Assertions.assertThat ;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get ;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content ;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status ;

import org.csap.agent.CsapBareTest ;
import org.csap.helpers.CsapApplication ;
import org.junit.jupiter.api.Tag ;
import org.junit.jupiter.api.Test ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.beans.factory.annotation.Autowired ;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc ;
import org.springframework.http.MediaType ;
import org.springframework.test.context.web.WebAppConfiguration ;
import org.springframework.test.web.servlet.MockMvc ;
import org.springframework.test.web.servlet.ResultActions ;

import com.fasterxml.jackson.databind.ObjectMapper ;

@WebAppConfiguration

@Tag ( "full" )
@CsapBareTest.Agent_Full
@AutoConfigureMockMvc

public class AgentTestPage {

	static Logger logger = LoggerFactory.getLogger( AgentTestPage.class ) ;

	static {

		CsapApplication.initialize( "Test Setup Complete" ) ;

	}

	ObjectMapper jacksonMapper = new ObjectMapper( ) ;

	@Test
	public void http_get_landing_page ( @Autowired MockMvc mockMvc )
		throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;
		// mock does much validation.....
		ResultActions resultActions = mockMvc.perform(
				get( "/test" )

						.with( CsapBareTest.csapMockUser( ) )

						.param( "sampleParam1", "sampleValue1" )
						.param( "sampleParam2", "sampleValue2" )
						.accept( MediaType.TEXT_PLAIN ) ) ;

		//
		String result = resultActions
				.andExpect( status( ).isOk( ) )
				.andExpect( content( ).contentType( "text/html;charset=UTF-8" ) )
				.andReturn( ).getResponse( ).getContentAsString( ) ;
		logger.debug( "landing page result: {}", result ) ;

		var landingPage = CsapBareTest.getPage( result ) ;

		assertThat( landingPage ).isNotNull( ) ;

		assertThat( landingPage.getElementById( "core" ) ).isNotNull( ) ;

		assertThat( landingPage.getElementById( "core" ).asXml( ) ).contains( "health reports" ) ;

	}

}
