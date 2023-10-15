package test.scenario_1_container ;

import static org.assertj.core.api.Assertions.assertThat ;

import java.util.ArrayList ;

import javax.inject.Inject ;

import org.csap.helpers.CsapApplication ;
import org.jasypt.encryption.pbe.StandardPBEStringEncryptor ;
import org.junit.jupiter.api.Disabled ;
import org.junit.jupiter.api.Test ;
import org.junit.jupiter.api.TestInstance ;
import org.sample.Csap_Tester_Application ;
import org.sample.input.http.ui.rest.MsgAndDbRequests ;
import org.sample.jpa.DemoManager ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.beans.factory.annotation.Autowired ;
import org.springframework.boot.test.context.SpringBootTest ;
import org.springframework.context.ApplicationContext ;
import org.springframework.core.env.Environment ;
import org.springframework.expression.EvaluationContext ;
import org.springframework.expression.Expression ;
import org.springframework.expression.ExpressionParser ;
import org.springframework.expression.spel.standard.SpelExpressionParser ;
import org.springframework.expression.spel.support.StandardEvaluationContext ;
import org.springframework.test.context.ActiveProfiles ;
import org.springframework.web.client.RestTemplate ;

import com.fasterxml.jackson.core.type.TypeReference ;
import com.fasterxml.jackson.databind.ObjectMapper ;
import com.fasterxml.jackson.databind.node.ArrayNode ;
import com.fasterxml.jackson.databind.node.ObjectNode ;

@SpringBootTest ( classes = Csap_Tester_Application.class )
@ActiveProfiles ( "junit" )

@TestInstance ( TestInstance.Lifecycle.PER_CLASS )
public class Boot_Container {

	static {

		CsapApplication.initialize( "Test Setup Complete" ) ;

	}

	Logger logger = LoggerFactory.getLogger( getClass( ) ) ;

	@Autowired
	private ApplicationContext applicationContext ;

	@Test
	public void load_context ( ) {

		logger.info( CsapApplication.TC_HEAD ) ;

		assertThat( applicationContext.getBeanDefinitionCount( ) )
				.as( "Spring Bean count" )
				.isGreaterThan( 200 ) ;

		assertThat( applicationContext.getBean( MsgAndDbRequests.class ) )
				.as( "SpringRequests controller loaded" )
				.isNotNull( ) ;

		assertThat( applicationContext.getBean( DemoManager.class ) )
				.as( "Demo_DataAccessObject  loaded" )
				.isNotNull( ) ;

		// using MQ embedded
		// assertThatThrownBy( () -> {
		// applicationContext.getBean( JmsConfig.class );
		// } )
		// .as( "Jms is disabled in junits by application.yml configuration
		// override" )
		// .isInstanceOf( NoSuchBeanDefinitionException.class )
		// .hasMessageContaining( "No qualifying bean of type" );
	}

	@Inject
	private StandardPBEStringEncryptor encryptor ;

	@Test
	public void testEncryption ( ) {

		String testSample = "Testing encyrpt" ;
		String encSample = encryptor.encrypt( testSample ) ;

		String message = "Encoding of  " + testSample + " is " + encSample ;
		logger.info( CsapApplication.TC_HEAD + message ) ;

		assertThat( testSample ).isNotEqualTo( encSample ) ;

		assertThat( testSample ).isEqualTo( encryptor.decrypt( encSample ) ) ;
		// assertTrue( encryptor.decrypt( encSample).equals( testSample) ) ;

	}

	@Inject
	private Environment springEnv ;

	@Inject
	private RestTemplate csAgentRestTemplate ;

	@Disabled
	public void get_hostUrls_for_service ( ) {

		ArrayNode urlArrayNode = csAgentRestTemplate.getForObject(
				springEnv.getProperty( "csap.info.load-balancer-url" )
						+ "/admin/api/service/urls/all/BootReference",
				ArrayNode.class ) ;

		assertThat( urlArrayNode.size( ) )
				.as( "Number of Urls" )
				.isEqualTo( 4 ) ;

		assertThat( urlArrayNode.get( 1 ).toString( ) )
				.as( "url contents" )
				.contains( "csap-dev" ) ;

	}

	ObjectMapper jacksonMapper = new ObjectMapper( ) ;

	@Disabled
	public void validate_spring_expression ( )
		throws Exception {

		String url = "http://csap-dev01.yourcompany.com:8011/CsAgent/api/agent/collection/application/CsAgent_8011/30/10" ;
		ObjectNode restResponse = csAgentRestTemplate.getForObject( url, ObjectNode.class ) ;

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
