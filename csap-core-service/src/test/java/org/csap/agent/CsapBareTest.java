package org.csap.agent ;

import static org.assertj.core.api.Assertions.assertThat ;
import static org.mockito.Mockito.doReturn ;
import static org.mockito.Mockito.mock ;
import static org.mockito.Mockito.when ;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user ;

import java.lang.annotation.ElementType ;
import java.lang.annotation.Retention ;
import java.lang.annotation.RetentionPolicy ;
import java.lang.annotation.Target ;
import java.util.List ;

import org.csap.agent.model.Application ;
import org.csap.agent.model.EnvironmentSettings ;
import org.csap.agent.model.ProjectLoader ;
import org.csap.agent.services.OsManager ;
import org.csap.agent.services.ServiceOsManager ;
import org.csap.helpers.CSAP ;
import org.csap.helpers.CsapApplication ;
import org.csap.integations.CsapWebServerConfig ;
import org.csap.integations.CsapWebSettings ;
import org.csap.security.config.CsapSecurityRoles ;
import org.junit.jupiter.api.BeforeAll ;
import org.junit.jupiter.api.TestInstance ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.boot.test.context.SpringBootTest ;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor ;
import org.springframework.test.context.ActiveProfiles ;

import com.fasterxml.jackson.databind.ObjectMapper ;
import com.fasterxml.jackson.databind.SerializationFeature ;
import com.gargoylesoftware.htmlunit.WebClient ;
import com.gargoylesoftware.htmlunit.html.HtmlPage ;

@TestInstance ( TestInstance.Lifecycle.PER_CLASS )
public abstract class CsapBareTest {

	static {

		// includes logging initialization - so must occur very early
		CsapApplication.initialize( CSAP.padNoLine( "Loading" ) + CsapThinNoProfile.class.getName( ) ) ;

	}

	public static Logger logger = LoggerFactory.getLogger( CsapBareTest.class ) ;

	//
	// Shared annotations for testing
	//
	final static public String PROFILE_JUNIT = "junit" ;

	@Target ( ElementType.TYPE )
	@Retention ( RetentionPolicy.RUNTIME )
	@ActiveProfiles ( {
			PROFILE_JUNIT, "localhost"
	} )
	public @interface ActiveProfiles_JunitOverRides {
	}

	@Target ( ElementType.TYPE )
	@Retention ( RetentionPolicy.RUNTIME )
	@SpringBootTest ( classes = CsapCoreService.class )
	@TestInstance ( TestInstance.Lifecycle.PER_CLASS )
	@ActiveProfiles ( {
			"agent", PROFILE_JUNIT
	} )
	public @interface Agent_Full {
	}

	@Target ( ElementType.TYPE )
	@Retention ( RetentionPolicy.RUNTIME )
	@SpringBootTest ( classes = CsapCoreService.class )
	@TestInstance ( TestInstance.Lifecycle.PER_CLASS )
	@ActiveProfiles ( {
			"admin", PROFILE_JUNIT
	} )
	public @interface Admin_Full {
	}

	Application application ;
	ObjectMapper jsonMapper ;

	@BeforeAll
	void thinBeforeAll ( )
		throws Exception {

		Application.DESKTOP_CLUSTER_HOST = "localhost" ;
		logger.info( CsapApplication.testHeader( "initializing {}" ), this.getClass( ).getSimpleName( ) ) ;

		jsonMapper = new ObjectMapper( ) ;

		ProjectLoader.addCsapJsonConfiguration( jsonMapper ) ;
		jsonMapper.configure( SerializationFeature.FAIL_ON_EMPTY_BEANS, false ) ;

		Application.setDeveloperMode( true ) ;

		application = Application.testBuilder( ) ;
		application.setJvmInManagerMode( false ) ;
		application.setAgentRunHome( System.getProperty( "user.home" ) ) ;

		//
		// Mock disable the ssl configuration
		//
		CsapWebServerConfig webServer = mock( CsapWebServerConfig.class ) ;
		CsapWebSettings webSettings = mock( CsapWebSettings.class ) ;
		CsapWebSettings.SslSettings sslSettings = mock( CsapWebSettings.SslSettings.class ) ;

		application.getCsapCoreService( ).setCsapWebServer( webServer ) ;

		doReturn( webSettings ).when( webServer ).getSettings( ) ;
		doReturn( false ).when( webServer ).isSslClient( ) ;

		doReturn( sslSettings ).when( webSettings ).getSsl( ) ;

		doReturn( false ).when( sslSettings ).isEnabled( ) ;

		doReturn( false ).when( sslSettings ).isSelfSigned( ) ;

		// when( webServer.getSettings( ) ).thenReturn( webSettings ) ;
//		when( webServer.isSslClient( ) ).thenReturn( false ) ;

//		when( webSettings.getSsl( ) ).thenReturn( sslSettings ) ;

//		when( sslSettings.isEnabled( ) ).thenReturn( false ) ;
//		when( sslSettings.isSelfSigned( ) ).thenReturn( false ) ;

	}

	public Application getApplication ( ) {

		return application ;

	}

	public OsManager getOsManager ( ) {

		return getApplication( ).getOsManager( ) ;

	}

	public ServiceOsManager getServiceOsManager ( ) {

		return getApplication( ).getOsManager( ).getServiceManager( ) ;

	}

	public EnvironmentSettings getRootProjectSettings ( ) {

		return getApplication( ).rootProjectEnvSettings( ) ;

	}

	public EnvironmentSettings getActiveProjectSettings ( ) {

		return getApplication( ).environmentSettings( ) ;

	}

	public ObjectMapper getJsonMapper ( ) {

		return jsonMapper ;

	}

	public static UserRequestPostProcessor csapMockUser ( ) {

		return user( "some-random-username" )
				.roles(
						"AUTHENTICATED",
						CsapSecurityRoles.ADMIN_ROLE,
						CsapSecurityRoles.VIEW_ROLE ) ;

	}

	public static HtmlPage getPage ( String htmlText ) {

		HtmlPage htmlPage = null ;

		try ( WebClient webClient = new WebClient( ) ) {

			webClient.getOptions( ).setThrowExceptionOnScriptError( false ) ;
			webClient.getOptions( ).setJavaScriptEnabled( false ) ;
			webClient.getOptions( ).setCssEnabled( false ) ;
			webClient.getOptions( ).setThrowExceptionOnFailingStatusCode( false ) ;

			htmlPage = webClient.loadHtmlCodeIntoCurrentWindow( htmlText ) ;
			// work with the html page
			var body = htmlPage.querySelector( "body" ) ;

			logger.debug( CsapApplication.header( "Body: {}" ), body.asXml( ) ) ;

		} catch ( Exception e ) {

			logger.warn( "Failed loading page: {}", CSAP.buildCsapStack( e ) ) ;

		}

		return htmlPage ;

	}

	public static void assertAllElementsFound ( HtmlPage page , List<String> domIds ) {

		//
		// Note - Any missing elements - and the mock fields will be dumped
		//
		for ( var field : domIds ) {

			var element = page.getElementById( field ) ;

			if ( element == null ) {

				logger.error( "Field '{}' not found. =================== mockmvc will dump context", field ) ;

			}

			assertThat( element ).as( "domId: " + field ).isNotNull( ) ;

		}

	}

}
