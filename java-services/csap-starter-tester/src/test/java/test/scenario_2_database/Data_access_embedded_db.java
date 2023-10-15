package test.scenario_2_database ;

import static org.assertj.core.api.Assertions.assertThat ;

import java.time.LocalDateTime ;
import java.time.format.DateTimeFormatter ;

import org.apache.commons.lang3.StringUtils ;
import org.csap.helpers.CsapApplication ;
import org.junit.jupiter.api.Assertions ;
import org.junit.jupiter.api.BeforeAll ;
import org.junit.jupiter.api.BeforeEach ;
import org.junit.jupiter.api.Test ;
import org.sample.Csap_Tester_Application ;
import org.sample.jpa.DemoEvent ;
import org.sample.jpa.DemoManager ;
import org.slf4j.LoggerFactory ;
import org.springframework.beans.factory.annotation.Autowired ;
import org.springframework.boot.test.context.SpringBootTest ;
import org.springframework.test.context.ActiveProfiles ;
import org.springframework.transaction.annotation.Transactional ;

import com.fasterxml.jackson.databind.ObjectMapper ;
import com.fasterxml.jackson.databind.node.ObjectNode ;

/**
 * 
 * Tests using an embedded DB (HSQLDB), note JPA context requires -javaagent see
 * ECLIPSE_SETUP
 * 
 * NOte the use of @Transactional which wraps every test to rollback db commits.
 * Note this includes the setup invoked for each
 * 
 * @TransactionConfiguration(defaultRollback=true) (no need to specify, test
 *                                                 context provides by default)
 * 
 * @author pnightin
 * 
 * 
 * @see <a href=
 *      "http://static.springsource.org/spring/docs/3.2.x/spring-framework-reference/html/jdbc.html#jdbc-embedded-database-support">
 *      Spring Test: Embedding a DB</a>
 * 
 * @see <a href=
 *      "http://static.springsource.org/spring/docs/3.2.x/spring-framework-reference/html/testing.html#testing-introduction">
 *      Spring Test Reference Guide</a>
 * 
 * @see <a href=
 *      "http://static.springsource.org/spring/docs/3.2.x/spring-framework-reference/html/new-in-3.2.html#new-in-3.2-testing">
 *      SpringMvc Test </a>
 * 
 * 
 * @see <a href=
 *      "http://static.springsource.org/spring/docs/3.2.x/spring-framework-reference/html/new-in-3.1.html#new-in-3.1-test-context-profiles">
 *      TestContext </a>
 * 
 * 
 */

@SpringBootTest ( classes = Csap_Tester_Application.class )
@ActiveProfiles ( "junit" )
@Transactional
public class Data_access_embedded_db {

	final static private org.slf4j.Logger logger = LoggerFactory.getLogger( Data_access_embedded_db.class ) ;

	private static String TEST_TOKEN = Data_access_embedded_db.class.getSimpleName( ) ;

	@Autowired
	private DemoManager demoManager ;

	// static private EmbeddedDatabase db;

	@BeforeAll
	public static void setUpBeforeClass ( )
		throws Exception {

		CsapApplication.initialize( logger.getName( ) ) ;

		logger.info( "Initializing in memory db" ) ;

		// creates an HSQL in-memory database populated from default scripts
		// classpath:schema.sql and classpath:data.sql
		// db = new EmbeddedDatabaseBuilder().addDefaultScripts().build();

		// db = new EmbeddedDatabaseBuilder().build();
	}

	public static boolean init = true ;

	@BeforeEach
	public void setUp ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( "Initializing db" ) ) ;

		for ( int i = 0; i < 10; i++ ) {

			DemoEvent event = new DemoEvent( ) ;
			event.setDemoField( "demoData" ) ;

			event.setCategory( TEST_TOKEN ) ;
			event.setDescription(
					"Time: " + LocalDateTime.now( ).format( DateTimeFormatter.ofPattern(
							"HH:mm:ss(SSS), MMMM d uuuu" ) ) ) ;
			event = demoManager.addSchedule( event ) ;

		}

	}

	@Test
	public void removeBulkData ( ) {

		logger.info( CsapApplication.testHeader( ) ) ;

		var deleteResults = demoManager.removeBulkDataJpql( TEST_TOKEN ) ;
		logger.info( "Result: " + deleteResults ) ;

		assertThat( deleteResults )
				.contains( "Total Items deleted: 10" ) ;

		assertThat( demoManager.getCountJpql( TEST_TOKEN ) ).isEqualTo( 0 ) ;

	}

	@Test
	public void removeBulkDataWithNamedQuery ( ) {

		String message = "Verifying no data is present after a bulk delete using named query" ;
		logger.info( CsapApplication.TC_HEAD + message ) ;

		String results = demoManager.removeBulkDataJpqlNamed( TEST_TOKEN ) ;
		logger.info( "Result: " + results ) ;

	}

	@Test
	public void removeBulkDataWithNewJpa2_1Criteria ( ) {

		String message = "Verifying no data is present after a bulk delete using named query" ;
		logger.info( CsapApplication.TC_HEAD + message ) ;

		String results = demoManager.removeBulkDataWithCriteria( TEST_TOKEN ) ;
		logger.info( "Result: " + results ) ;

		assertThat( demoManager.getCountJpql( TEST_TOKEN ) ).isEqualTo( 0 ) ;

	}

	@Test
	public void add_item_to_database ( ) {

		DemoEvent jobScheduleInput = new DemoEvent( ) ;
		jobScheduleInput.setDemoField( "test Jndi name" ) ;
		// jobScheduleInput.setScheduleObjid(System.currentTimeMillis()); //
		// Never provide this as it is generated
		jobScheduleInput.setCategory( "My test" ) ;
		jobScheduleInput
				.setDescription( "Spring Consumer ======> My test String: "
						+ System.currentTimeMillis( ) ) ;

		String message = "Inserting: " + jobScheduleInput ;
		logger.info( CsapApplication.TC_HEAD + message ) ;

		jobScheduleInput = demoManager.addSchedule( jobScheduleInput ) ;
		logger.info( "Result: " + jobScheduleInput ) ;
		Assertions.assertTrue( jobScheduleInput.getId( ) >= 0 ) ;

	}

	@Test
	public void testCriteriaCount ( ) {

		String message = "Test testCriteriaCount: " ;
		logger.info( CsapApplication.TC_HEAD + message ) ;

		// Thread.sleep(5000);
		long count = demoManager.getCountCriteria( TEST_TOKEN ) ;
		logger.info( "Result: " + count ) ;
		Assertions.assertTrue( count == 10 ) ;

	}

	@Test
	public void showTestDataWithJpql ( ) {

		String message = "Test show data via Jpql" ;
		logger.info( CsapApplication.TC_HEAD + message ) ;

		String result = demoManager.findUsingJpql( TEST_TOKEN, 10 ) ;
		logger.info( "Result: " + result ) ;

		assertThat( StringUtils.countMatches( result, "category=" + TEST_TOKEN ) )
				.as( message )
				.isEqualTo( 10 ) ;

	}

	ObjectMapper jacksonMapper = new ObjectMapper( ) ;

	@Test
	public void showTestDataWithCriteria ( )
		throws Exception {

		String message = "Test show data via criteria" ;
		logger.info( CsapApplication.TC_HEAD + message ) ;

		ObjectNode resultNode = demoManager.showScheduleItemsWithFilter(
				TEST_TOKEN, 10 ) ;
		logger.info( "Result: "
				+ jacksonMapper.writerWithDefaultPrettyPrinter( )
						.writeValueAsString( resultNode ) ) ;
		Assertions.assertTrue( resultNode.get( "count" ).asInt( ) == 10 ) ;

	}

	@Test
	public void showTestDataWithEz ( )
		throws Exception {

		String message = "Test show data via ez criteria API" ;
		logger.info( CsapApplication.TC_HEAD + message ) ;

		ObjectNode resultNode = demoManager.findScheduleItemsUsingCriteriaWrapper(
				TEST_TOKEN, 10 ) ;
		logger.info( "Result: "
				+ jacksonMapper.writerWithDefaultPrettyPrinter( )
						.writeValueAsString( resultNode ) ) ;
		Assertions.assertTrue( resultNode.get( "availableInDb" ).asInt( ) == 10 ) ;

	}

	@Test
	public void testEzCriteriaCount ( ) {

		String message = "Test testCriteriaCount via Ez Api: " ;
		logger.info( CsapApplication.TC_HEAD + message ) ;

		// Thread.sleep(5000);
		long count = demoManager.countRecordsUsingCriteriaWrapper( TEST_TOKEN ) ;
		logger.info( "Result: " + count ) ;
		Assertions.assertTrue( count == 10 ) ;

	}

}
