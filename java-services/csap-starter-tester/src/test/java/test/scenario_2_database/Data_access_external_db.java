package test.scenario_2_database ;

import static org.assertj.core.api.Assertions.assertThat ;

import java.time.LocalDateTime ;
import java.time.format.DateTimeFormatter ;

import org.apache.commons.lang3.StringUtils ;
import org.csap.helpers.CsapApplication ;
import org.junit.jupiter.api.BeforeAll ;
import org.junit.jupiter.api.BeforeEach ;
import org.junit.jupiter.api.Test ;
import org.sample.Csap_Tester_Application ;
import org.sample.jpa.DemoEvent ;
import org.sample.jpa.DemoManager ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.beans.factory.annotation.Autowired ;
import org.springframework.boot.test.context.SpringBootTest ;
import org.springframework.test.context.ActiveProfiles ;
import org.springframework.transaction.annotation.Transactional ;

/**
 * 
 * Tests using an external DB (Oracle), note JPA context requires -javaagent see
 * ECLIPSE_SETUP.
 * 
 * NOte the use of @Transactional which wraps every test to rollback DB commits.
 * Note this includes the setup invoked for each
 * 
 * @TransactionConfiguration(defaultRollback=true) (no need to specify, test
 *                                                 context provides by default)
 * 
 * @author pnightin
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
@ActiveProfiles ( {
		"junit", "company"
} )
@Transactional
public class Data_access_external_db {

	final static private Logger logger = LoggerFactory.getLogger( Data_access_external_db.class ) ;

	private static String TEST_TOKEN = Data_access_external_db.class.getSimpleName( ) ;

	@Autowired
	private DemoManager demoManager ;

	@BeforeAll
	public static void setUpBeforeClass ( )
		throws Exception {

		CsapApplication.initialize( logger.getName( ) ) ;

	}

	@BeforeEach
	public void setUp ( )
		throws Exception {

		logger.info( "Loading Test Data" ) ;

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

	/**
	 * Note the use of @Transactional and @Rollback to avoid impacting Oracle DB
	 */
	@Test
	public void delete_all_data_matching_filter ( ) {

		String message = "Verifying no data is present after a bulk delete" ;
		logger.info( CsapApplication.TC_HEAD + message ) ;

		String results = demoManager
				.removeBulkDataJpql( TEST_TOKEN ) ;

		logger.info( "Result: {} ", results ) ;

		assertThat( demoManager.getCountJpql( TEST_TOKEN ) )
				.as( "Inserted a recpred" )
				.isEqualTo( 0 ) ;

	}

	@Test
	public void insert_record_into_db ( ) {

		DemoEvent jobScheduleInput = new DemoEvent( ) ;
		jobScheduleInput.setDemoField( "test Jndi name" ) ;
		// jobScheduleInput.setScheduleObjid(System.currentTimeMillis()); //
		// Never provide this as it is generated
		jobScheduleInput.setCategory( "My test" ) ;
		jobScheduleInput
				.setDescription( TEST_TOKEN
						+ System.currentTimeMillis( ) ) ;

		String message = "Inserting: " + jobScheduleInput ;
		logger.info( CsapApplication.TC_HEAD + message ) ;

		jobScheduleInput = demoManager.addSchedule( jobScheduleInput ) ;
		logger.info( "Result: " + jobScheduleInput ) ;

		assertThat( jobScheduleInput.getId( ) )
				.as( "Inserted a record" )
				.isGreaterThanOrEqualTo( 0 ) ;

	}

	@Test
	public void show_data_using_jpql ( ) {

		String message = "Querying using Jpql" ;
		logger.info( CsapApplication.TC_HEAD + message ) ;

		String result = demoManager.findUsingJpql( TEST_TOKEN, 10 ) ;
		logger.info( "Result: {}", result ) ;

		assertThat( StringUtils.countMatches( result, TEST_TOKEN ) )
				.as( message )
				.isEqualTo( 11 ) ;

	}
}
