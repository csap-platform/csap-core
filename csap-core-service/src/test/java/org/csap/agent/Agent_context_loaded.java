package org.csap.agent ;

import static org.assertj.core.api.Assertions.assertThat ;

import java.io.File ;
import java.util.ArrayList ;

import javax.cache.Cache ;
import javax.cache.CacheManager ;
import javax.inject.Inject ;

import org.csap.agent.container.ContainerIntegration ;
import org.csap.agent.model.Application ;
import org.csap.agent.ui.windows.CorePortals ;
import org.csap.helpers.CSAP ;
import org.csap.helpers.CsapApplication ;
import org.jasypt.encryption.pbe.StandardPBEStringEncryptor ;
import org.junit.jupiter.api.BeforeAll ;
import org.junit.jupiter.api.Disabled ;
import org.junit.jupiter.api.DisplayName ;
import org.junit.jupiter.api.Tag ;
import org.junit.jupiter.api.Test ;
import org.junit.jupiter.api.TestInstance ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.beans.factory.annotation.Autowired ;
import org.springframework.beans.factory.annotation.Qualifier ;
import org.springframework.boot.test.context.SpringBootTest ;
import org.springframework.context.ApplicationContext ;
import org.springframework.expression.EvaluationContext ;
import org.springframework.expression.Expression ;
import org.springframework.expression.ExpressionParser ;
import org.springframework.expression.spel.standard.SpelExpressionParser ;
import org.springframework.expression.spel.support.StandardEvaluationContext ;
import org.springframework.web.client.RestTemplate ;

import com.fasterxml.jackson.core.JsonProcessingException ;
import com.fasterxml.jackson.core.type.TypeReference ;
import com.fasterxml.jackson.databind.ObjectMapper ;
import com.fasterxml.jackson.databind.node.ObjectNode ;

@Tag ( "core" )
@Tag ( "full" )
@SpringBootTest ( classes = ApplicationConfiguration.class )

@CsapBareTest.ActiveProfiles_JunitOverRides

@TestInstance ( TestInstance.Lifecycle.PER_CLASS )
public class Agent_context_loaded {

	Logger logger = LoggerFactory.getLogger( getClass( ) ) ;

	static {

		CsapApplication.initialize( CSAP.padNoLine( "Loading" ) + Agent_context_loaded.class.getName( ) ) ;

	}

	public static String TC_HEAD = CsapApplication.TC_HEAD ;
	public static String SETUP_HEAD = CsapApplication.SETUP_HEAD ;

	public static String TEST_DEFINITIONS = "/definitions/" ;

	public static File SIMPLE_TEST_DEFINITION = new File(
			CsapApplication.class.getResource( "/definitions/test-default-project.json" ).getPath( ) ) ;

	@BeforeAll
	public void beforeAll ( )
		throws Exception {

		csapApplication.setAutoReload( false ) ;

		assertThat( csapApplication.loadDefinitionForJunits( false, Agent_context_loaded.SIMPLE_TEST_DEFINITION ) )
				.as( "No Errors or warnings" )
				.isTrue( ) ;

	}

	@Autowired
	private ApplicationContext applicationContext ;

	@Autowired
	Application csapApplication ;

	@Autowired ( required = false )
	private CacheManager cacheManager = null ;

	@Test
	void verify_ehcache ( ) {

		Cache<String, String> pidCache = cacheManager.getCache( ContainerIntegration.PID_CACHE, String.class,
				String.class ) ;

		pidCache.put( "hi", "there" ) ;

		logger.info( "hi: {}", pidCache.get( "hi" ) ) ;

	}

	@Test
	@DisplayName ( "spring context loaded" )
	public void verify_spring_context_loaded ( ) {

		logger.info( CsapApplication.testHeader( ) ) ;

		assertThat( csapApplication.isAdminProfile( ) ).isFalse( ) ;

		assertThat( applicationContext.getBeanDefinitionCount( ) )
				.as( "Spring Bean count" )
				.isGreaterThan( 100 ) ;

		assertThat( applicationContext.getBean( CorePortals.class ) )
				.as( "SpringRequests controller loaded" )
				.isNotNull( ) ;

		assertThat( csapApplication.getName( ) )
				.as( "csapApplication.getActiveModelName" )
				.isEqualTo( "DEFAULT APPLICATION FOR JUNITS" ) ;

		// assertThat( csapApplication.lifeCycleSettings().getEventDataUser() )
		// .as( "event user" )
		// .isEqualTo( "$user" );

	}

	@Inject
	private StandardPBEStringEncryptor encryptor ;

	@Test
	@DisplayName ( "csap encryption enabled" )
	public void testEncryption ( ) {

		logger.info( CsapApplication.testHeader( ) ) ;

		String testSample = "Testing encyrpt" ;
		String encSample = encryptor.encrypt( testSample ) ;

		String message = "Encoding of  " + testSample + " is " + encSample ;
		logger.info( TC_HEAD + message ) ;

		assertThat( testSample ).isNotEqualTo( encSample ) ;

		assertThat( testSample ).isEqualTo( encryptor.decrypt( encSample ) ) ;
		// Assertions.assertTrue( encryptor.decrypt( encSample).equals( testSample) ) ;

	}

	ObjectMapper jacksonMapper = new ObjectMapper( ) ;

	@Inject
	@Qualifier ( "analyticsRest" )
	private RestTemplate analyticsTemplate ;

	@Disabled
	@Test
	public void testRest ( )
		throws JsonProcessingException {

		logger.info( CsapApplication.testHeader( ) ) ;

		String url = "http://csaptools.yourcompany.com/admin/api/model/clusters" ;
		ObjectNode restResponse = analyticsTemplate.getForObject( url, ObjectNode.class ) ;

		logger.info( "Url: {} response: {}", url, jacksonMapper.writerWithDefaultPrettyPrinter( )
				.writeValueAsString( restResponse ) ) ;

	}

	@Disabled
	@Test
	public void validate_spring_expression ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		// String url = "http://csaptools.yourcompany.com/admin/api/clusters";
		String url = "http://testhost.yourcompany.com:8011/CsAgent/api/collection/application/CsAgent_8011/30/10" ;
		ObjectNode restResponse = analyticsTemplate.getForObject( url, ObjectNode.class ) ;

		logger.info( "Url: {} response: {}", url, jacksonMapper.writerWithDefaultPrettyPrinter( )
				.writeValueAsString( restResponse ) ) ;

		ArrayList<Integer> publishvals = jacksonMapper.readValue(
				restResponse.at( "/data/publishEvents" )
						.traverse( ),
				new TypeReference<ArrayList<Integer>>( ) {
				} ) ;

		int total = publishvals.stream( ).mapToInt( Integer::intValue ).sum( ) ;

		logger.info( "Total: {} publishvals: {}", total, publishvals ) ;

		EvaluationContext context = new StandardEvaluationContext( ) ;
		context.setVariable( "total", total ) ;

		ExpressionParser parser = new SpelExpressionParser( ) ;
		Expression exp = parser.parseExpression( "#total.toString()" ) ;
		logger.info( "SPEL evalutation: {}", (String) exp.getValue( context ) ) ;

		exp = parser.parseExpression( "#total > 99" ) ;
		logger.info( "#total > 99 SPEL evalutation: {}", (Boolean) exp.getValue( context ) ) ;

		exp = parser.parseExpression( "#total > 3" ) ;
		logger.info( "#total > 3 SPEL evalutation: {}", (Boolean) exp.getValue( context ) ) ;

	}

}
