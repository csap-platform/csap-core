package test.scenario_3_performance ;

import static org.assertj.core.api.Assertions.assertThat ;

import java.util.Arrays ;
import java.util.List ;
import java.util.stream.Collectors ;

import org.csap.alerts.AlertsController ;
import org.csap.helpers.CsapApplication ;
import org.csap.integations.CsapInformation ;
import org.junit.jupiter.api.BeforeAll ;
import org.junit.jupiter.api.Test ;
import org.sample.Csap_Simple_Application ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.beans.factory.annotation.Autowired ;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc ;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc ;
import org.springframework.boot.test.context.SpringBootTest ;
import org.springframework.context.ApplicationContext ;
import org.springframework.test.context.ActiveProfiles ;

import com.gargoylesoftware.htmlunit.BrowserVersion ;
import com.gargoylesoftware.htmlunit.WebClient ;
import com.gargoylesoftware.htmlunit.html.HtmlPage ;

// @WebMvcTest(includeFilters={"org.sample"}) // health is a condition bean so
// this will not work. See landing page example
@SpringBootTest ( classes = Csap_Simple_Application.class )
@AutoConfigureWebMvc
@AutoConfigureMockMvc ( )
@ActiveProfiles ( "junit" ) // disables performance filters which do not work
public class Csap_Health_Page_Using_Html_Unit {

	final static private Logger logger = LoggerFactory.getLogger( Csap_Health_Page_Using_Html_Unit.class ) ;

	@BeforeAll
	public static void setUpBeforeClass ( )
		throws Exception {

		CsapApplication.initialize( logger.getName( ) ) ;
		BrowserVersion.setDefault( BrowserVersion.FIREFOX ) ;

	}

	@Autowired
	private WebClient webClient ;

	@Autowired
	private CsapInformation csapInfo ;

	@Autowired
	private ApplicationContext applicationContext ;

	@Test
	public void validate_csap_health ( ) throws Exception {

		// work around default parameters in java script
		webClient.getOptions( ).setJavaScriptEnabled( false ) ;

		logger.info( CsapApplication.testHeader( "browser: {}" ), webClient.getBrowserVersion( ) ) ;

		String healthUrl = csapInfo.getCsapBaseContext( ) + AlertsController.HEALTH_URL ;
		logger.info( CsapApplication.testHeader( " using webClient to hit health url: {}, bean count: {}" ),
				healthUrl,
				applicationContext.getBeanDefinitionCount( ) ) ;

		// logger.info( "beans loaded: {}", Arrays.asList(
		// applicationContext.getBeanDefinitionNames() ) );

		String beanFilter = ".SecurityAutoConfiguration" ;
		List<String> matchedBeans = Arrays.asList( applicationContext.getBeanDefinitionNames( ) )
				.stream( )
				.filter( beanName -> beanName.contains( beanFilter ) )
				.collect( Collectors.toList( ) ) ;

		logger.info( "Filtered beans with '{}',  matches: {}", beanFilter, matchedBeans ) ;

		assertThat( matchedBeans.size( ) ).isEqualTo( 0 ) ;

		// https://docs.spring.io/spring-boot/docs/current/reference/html/boot-features-testing.html
		// http://docs.spring.io/spring/docs/current/spring-framework-reference/htmlsingle/#spring-mvc-test-server-htmlunit-mah

		HtmlPage createMsgFormPage = webClient.getPage( healthUrl ) ;

		String metricsTableText = createMsgFormPage
				.getElementById( "metricTable" )
				.getTextContent( ) ;

		logger.debug( "Full page: {}", createMsgFormPage ) ;
		logger.info( "Metric Table: {}", metricsTableText ) ;

		assertThat( metricsTableText )
				.contains( "Name", "Alerts" ) ;

	}
}
