package test.scenario_1_container ;

import static org.assertj.core.api.Assertions.assertThat ;

import org.csap.helpers.CsapApplication ;
import org.junit.jupiter.api.Test ;
import org.junit.jupiter.api.TestInstance ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;

import com.fasterxml.jackson.core.JsonProcessingException ;
import com.fasterxml.jackson.databind.JsonMappingException ;
import com.fasterxml.jackson.databind.ObjectMapper ;

@TestInstance ( TestInstance.Lifecycle.PER_CLASS )
public class HelloWorld {

	static {

		CsapApplication.initialize( "Test Setup Complete" ) ;

	}

	Logger logger = LoggerFactory.getLogger( getClass( ) ) ;

	@Test
	void verify_test_dependencies ( ) {

		logger.info( CsapApplication.testHeader( ) ) ;

		assertThat( true ).as( "simple test" ).isTrue( ) ;

	}

	@Test
	void verify_math ( ) {

		logger.info( CsapApplication.testHeader( ) ) ;

		assertThat( true ).as( "simple test" ).isTrue( ) ;

	}

	String JSON_TEXT_BLOCK = """
			{
				"simple": "test"
			}

			""" ;

	@Test
	void verify_json_block ( ) throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		var json = new ObjectMapper( ) ;

		var testReport = json.readTree( JSON_TEXT_BLOCK ) ;

		assertThat( testReport.path( "simple" ).asText( ) ).as( "simple test" ).isEqualTo( "test" ) ;

	}

	public record Person (
			String name ,
			int age) {
	};

	@Test
	void verify_records ( ) throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		var demoRecord = new Person( "samm", 99 ) ;

		logger.info( "demoRecord: {}", demoRecord ) ;

		assertThat( demoRecord.name( ) ).isEqualTo( "samm" ) ;
		assertThat( demoRecord.age( ) ).isEqualTo( 99 ) ;

	}

}
