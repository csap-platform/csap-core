package test.scenario_3_performance ;

import static org.assertj.core.api.Assertions.assertThat ;

import org.csap.helpers.CsapApplication ;
import org.junit.jupiter.api.BeforeAll ;
import org.junit.jupiter.api.Test ;
import org.sample.Csap_Simple_Application ;
import org.sample.HelloService ;
import org.sample.SimpleLandingPage ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.beans.factory.annotation.Autowired ;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest ;
import org.springframework.context.ApplicationContext ;
import org.springframework.security.test.context.support.WithMockUser ;
import org.springframework.test.context.ActiveProfiles ;
import org.springframework.test.context.ContextConfiguration ;

import com.gargoylesoftware.htmlunit.WebClient ;
import com.gargoylesoftware.htmlunit.html.HtmlPage ;

@WebMvcTest ( controllers = {
		SimpleLandingPage.class
} )
@ContextConfiguration ( classes = {
		Csap_Simple_Application.class, HelloService.class
} )
@ActiveProfiles ( {
		"junit", "htmlUnit"
} )
public class Landing_Page_Using_Web_Client {
	static final private Logger logger = LoggerFactory.getLogger( Landing_Page_Using_Web_Client.class ) ;

	@Autowired
	private WebClient webClient ;

	@BeforeAll
	public static void setUpBeforeClass ( )
			throws Exception {

		CsapApplication.initialize( logger.getName( ) ) ;

	}

	@Autowired
	private ApplicationContext applicationContext ;

	@Test
	@WithMockUser ( username = "dummy" )
	public void validate_landing_page_with_test_web_client ( )
			throws Exception {

		// work around default parameters in java script
		webClient.getOptions( ).setJavaScriptEnabled( false ) ;

		logger.info( "browser: {}", webClient.getBrowserVersion( ) ) ;

		String landingPageUrl = "/" ;

		logger.info( CsapApplication.TC_HEAD + "simple mvc test url: {}, beans loaded: {}",
				landingPageUrl,
				applicationContext.getBeanDefinitionCount( ) ) ;
		// https://docs.spring.io/spring-boot/docs/current/reference/html/boot-features-testing.html
		// http://docs.spring.io/spring/docs/current/spring-framework-reference/htmlsingle/#spring-mvc-test-server-htmlunit-mah

		HtmlPage landingPage = webClient.getPage( landingPageUrl ) ;

		logger.debug( "Full page: {}", landingPage ) ;
		logger.info( "csapPageVersion: {}", landingPage.getElementById( "csapPageVersion" ).getTextContent( ) ) ;

		assertThat( landingPage.getElementById( "csapPageVersion" ).getTextContent( ) )
				.contains( "1.0-Desktop" ) ;
	}
}
