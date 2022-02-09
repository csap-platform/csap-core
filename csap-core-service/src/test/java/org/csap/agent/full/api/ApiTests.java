package org.csap.agent.full.api ;

import static org.assertj.core.api.Assertions.assertThat ;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get ;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status ;

import java.io.File ;
import java.util.ArrayList ;
import java.util.Arrays ;
import java.util.List ;
import java.util.concurrent.TimeUnit ;

import javax.inject.Inject ;

import org.apache.commons.io.FileUtils ;
import org.csap.agent.Agent_context_loaded ;
import org.csap.agent.CsapApis ;
import org.csap.agent.CsapBareTest ;
import org.csap.agent.CsapConstants ;
import org.csap.agent.api.AgentApi ;
import org.csap.agent.api.ApplicationApi ;
import org.csap.agent.container.kubernetes.K8 ;
import org.csap.agent.model.Application ;
import org.csap.agent.model.HealthManager ;
import org.csap.helpers.CSAP ;
import org.csap.helpers.CsapApplication ;
import org.csap.integations.micrometer.CsapMicroMeter ;
import org.csap.security.config.CsapSecuritySettings ;
import org.junit.jupiter.api.Assumptions ;
import org.junit.jupiter.api.BeforeAll ;
import org.junit.jupiter.api.Disabled ;
import org.junit.jupiter.api.DisplayName ;
import org.junit.jupiter.api.Nested ;
import org.junit.jupiter.api.Tag ;
import org.junit.jupiter.api.Test ;
import org.junit.jupiter.api.TestInstance ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.beans.factory.annotation.Autowired ;
import org.springframework.http.MediaType ;
import org.springframework.test.web.servlet.MockMvc ;
import org.springframework.test.web.servlet.ResultActions ;
import org.springframework.test.web.servlet.setup.MockMvcBuilders ;
import org.springframework.web.context.WebApplicationContext ;

import com.fasterxml.jackson.databind.JsonNode ;
import com.fasterxml.jackson.databind.ObjectMapper ;
import com.fasterxml.jackson.databind.node.ObjectNode ;

@Tag ( "full" )
@Tag ( "my-state" )
@CsapBareTest.Agent_Full
class ApiTests {

	static {

		CsapApplication.initialize( "Test Setup Complete" ) ;

	}

	Logger logger = LoggerFactory.getLogger( getClass( ) ) ;

	@Autowired
	WebApplicationContext wac ;
	MockMvc mockMvc ;

	@Inject
	Application csapApp ;

	@Inject
	CsapApis csapApis ;

	@Inject
	AgentApi agentApi ;

	@Inject
	ApplicationApi applicationApi ;

	@Inject
	ObjectMapper jacksonMapper ;

	@Inject
	CsapSecuritySettings security ;

	@BeforeAll
	public void beforeAll ( )
		throws Exception {

		this.mockMvc = MockMvcBuilders.webAppContextSetup( this.wac ).build( ) ;

		logger.info( "Deleting: {}",
				csapApp.getHttpdIntegration( ).getHttpdWorkersFile( ).getParentFile( ).getAbsolutePath( ) ) ;

		csapApp.setAutoReload( false ) ;

		csapApp.getProjectLoader( ).setAllowLegacyNames( true ) ;

		FileUtils.deleteQuietly( csapApp.getHttpdIntegration( ).getHttpdWorkersFile( ).getParentFile( ) ) ;

		csapApp.setTestModeToSkipActivate( true ) ;
		csapApp.metricManager( ).setSuspendCollection( true ) ;

	}

	@Nested
	@TestInstance ( TestInstance.Lifecycle.PER_CLASS )
	@DisplayName ( "common global model" )
	class GlobalModel {

		@BeforeAll
		public void beforeAll ( )
			throws Exception {

			File csapApplicationDefinition = new File(
					getClass( ).getResource( Agent_context_loaded.TEST_DEFINITIONS + "test_application_model.json" )
							.getPath( ) ) ;

			logger.info( "Loading application: {}" + csapApplicationDefinition ) ;

			assertThat( csapApp.loadDefinitionForJunits( false, csapApplicationDefinition ) ).as(
					"No Errors or warnings" ).isTrue( ) ;

		}

		@Nested
		@DisplayName ( "Application Api" )
		class ApplicationApiTests {

			@Test
			void verify_userNames_api ( )
				throws Exception {

				Assumptions.assumeTrue( ( security != null ) && security.getProvider( ).isLdapEnabled( ),
						"Only ldap supported" ) ;
				logger.info( CsapApplication.testHeader( ) ) ;
				// Application.setJvmInManagerMode( true ) ;
				List<String> userids = new ArrayList<String>(
						Arrays.asList( "pnightingale", "nonExist" ) ) ;
				JsonNode userInfoJson = applicationApi.userNames( userids, "false" ) ;

				// Print it out for generating tests using jackson
				logger.info( jacksonMapper.writerWithDefaultPrettyPrinter( )
						.writeValueAsString( userInfoJson ) ) ;

				// Use jsonPath apis for quick processing for tests
				assertThat( userInfoJson.get( "pnightingale" ).asText( ) )
						.isEqualTo( "Peter Nightingale" ) ;

			}

		}

		@Nested
		@DisplayName ( "Model" )
		class ModelApiTests {
			@Test
			void validate_api_model_clusters ( )
				throws Exception {

				logger.info( CsapApplication.testHeader( ) ) ;

				// mock does much validation.....
				ResultActions resultActions = mockMvc.perform(
						get( CsapConstants.API_MODEL_URL + "/clusters" )
								.accept( MediaType.APPLICATION_JSON ) ) ;

				// But you could do full parsing of the Json result if needed
				String responseText = resultActions
						.andExpect( status( ).isOk( ) )
						.andReturn( )
						.getResponse( )
						.getContentAsString( ) ;

				ObjectNode clusterJson = (ObjectNode) jacksonMapper.readTree( responseText ) ;

				logger.info( "cluster report: {}", CSAP.jsonPrint( clusterJson ) ) ;

				List<String> clusters = jacksonMapper.readValue(
						clusterJson.path( "WebServer" ).toString( ), List.class ) ;

				logger.info( "base-os: {}", clusters ) ;
				assertThat( clusters )
						.as( "clusters" )
						.hasSize( 4 )
						.contains( "csap-dev01", "csap-dev02", "peter-dev01", "localhost" ) ;

			}

		}

	}

	@Nested
	@DisplayName ( "Agent" )
	@TestInstance ( TestInstance.Lifecycle.PER_CLASS )
	class AgentApiTests {
		File simpleProject = new File( getClass( ).getResource( "api-project.json" ).getPath( ) ) ;

		@BeforeAll
		public void beforeAll ( )
			throws Exception {

			logger.info( CsapApplication.testHeader( ) ) ;

			assertThat( csapApp.loadDefinitionForJunits( false, simpleProject ) )
					.as( "No Errors or warnings" )
					.isTrue( ) ;

			assertThat( csapApp.getName( ) )
					.as( "Capability Name parsed" )
					.isEqualTo( "DefaultName" ) ;

			assertThat( csapApp.metricManager( ).isCollectorsStarted( ) )
					.as( "collectors started" )
					.isTrue( ) ;

			logger.info( "Waiting for processStatusUpdate to complete" ) ;
			// First update with process data
			csapApis.osManager( ).checkForProcessStatusUpdate( ) ;

		}

		@Test
		public void agent_docker_health ( )
			throws Exception {

			logger.info( CsapApplication.testHeader( ) ) ;

			var dockerHealth = agentApi.healthDocker( null ) ;

			logger.info( "dockerHealth: {}", CSAP.jsonPrint( dockerHealth ) ) ;

			assertThat( //
					dockerHealth.path( "report" )
							.path( "version" ).asText( ) )
									.startsWith( "20." ) ;

			assertThat( //
					dockerHealth.path( CsapMicroMeter.HealthReport.Report.name.json ).isObject( ) )
							.isTrue( ) ;

			// assertThat( //
			// dockerHealth.path( CsapMicroMeter.HealthReport.Report.name.json )
			// .path( CsapMicroMeter.HealthReport.Report.healthy.json ).asBoolean() )
			// .isTrue() ;
		}

		@Test
		public void agent_summary ( )
			throws Exception {

			logger.info( CsapApplication.testHeader( ) ) ;

			JsonNode agentStatus = agentApi.status( ) ;

			logger.info( "agentStatus: {}", CSAP.jsonPrint( agentStatus ) ) ;

			assertThat( agentStatus.has( "vm" ) ).isTrue( ) ;
			assertThat( agentStatus.has( "services" ) ).isTrue( ) ;
			assertThat( agentStatus.has( "errors" ) ).isTrue( ) ;
			assertThat( agentStatus.at( "/errors/localhost" ).size( ) ).isGreaterThanOrEqualTo( 3 ) ;

		}

		@Test
		@Tag ( "containers" )
		public void agent_kubernetes_health ( ) throws Exception {

			logger.info( CsapApplication.testHeader( ) ) ;

			JsonNode healthAndSummaryReport = agentApi.healthKubernetes( false, null ) ;

			logger.info( "kubernetesHealth: {}", CSAP.jsonPrint( healthAndSummaryReport ) ) ;

			var summaryReport = healthAndSummaryReport.path( "report" ) ;

			assertThat( summaryReport.path( K8.heartbeat.val( ) ).asBoolean( ) ).isTrue( ) ;

			var nodeMetrics = summaryReport.path( "metrics" ).path( "current" ) ;
			var foundVersion = nodeMetrics.path( "version" ).asText( ) ;

			logger.info( "foundVersion: {}", foundVersion ) ;
			assertThat( foundVersion.startsWith( "v1.23." )
					|| foundVersion.startsWith( "v1.21." )
					|| foundVersion.startsWith( "v1.20." )
					|| foundVersion.startsWith( "v1.19." )
					|| foundVersion.startsWith( "v1.18." )
					|| foundVersion.startsWith( "v1.16." ) )
							.isTrue( ) ;

			assertThat( nodeMetrics.path( K8.memoryGb.val( ) ).asDouble( ) ).isGreaterThan( 7 ) ;

			// assertThat( //
			// kubernetesHealth.path( CsapMicroMeter.HealthReport.Report.name.json )
			// .path( CsapMicroMeter.HealthReport.Report.healthy.json ).asBoolean() )
			// .isTrue() ;

		}

		@Disabled
		@Test
		public void agent_kubernetes_health_changes ( ) {

			logger.info( CsapApplication.testHeader( ) ) ;

			try {

				for ( var i = 0; i <= 100; i++ ) {

					JsonNode kubernetesHealth = agentApi.healthKubernetes( false, null ) ;

					logger.debug( "kubernetesHealth: {}", CSAP.jsonPrint( kubernetesHealth ) ) ;

					var healthy = kubernetesHealth.path( CsapMicroMeter.HealthReport.Report.name.json )
							.path( CsapMicroMeter.HealthReport.Report.healthy.json ).asBoolean( ) ;

					var errors = kubernetesHealth.path( CsapMicroMeter.HealthReport.Report.name.json )
							.path( CsapMicroMeter.HealthReport.Report.errors.json ).toString( ) ;

					var version = kubernetesHealth.path( "report" )
							.path( "version" ).asText( ) ;

					var eventCount = kubernetesHealth.path( "report" )
							.path( "eventCount" ).asInt( ) ;

					logger.info( CsapApplication.header(
							"kubernetes version: {}, healthy: {}, eventCount: {}, errors: {}" ),
							version, healthy, eventCount, errors ) ;

					TimeUnit.SECONDS.sleep( 5 ) ;

				}

			} catch ( Exception e ) {

				logger.warn( "Need to enable test: {}", CSAP.buildCsapStack( e ) ) ;

			}

		}

		@Test
		void agent_health_api ( )
			throws Exception {

			logger.info( CsapApplication.testHeader( ) ) ;

			// update with JMX data
			// ServiceCollector serviceCollection = new ServiceCollector( csapApp,
			// csapApis.osManager(), 30, false );
			// serviceCollection.shutdown();
			// serviceCollection.testJmxCollection( "csap-dev01" );

			var agentHealth = agentApi.health( 1.0, Application.ALL_PACKAGES ) ;

			// Print it out for generating tests using jackson
			logger.info( "Health: {}", CsapConstants.jsonPrint( jacksonMapper, agentHealth ) ) ;

			assertThat( agentHealth.at( "/Healthy" ).asBoolean( ) )
					.isFalse( ) ;

			assertThat( agentHealth.at( "/" + HealthManager.HEALTH_SUMMARY + "/localhost" ).size( ) )
					.as( " capabilityHealth: /errors/localhost" )
					.isGreaterThanOrEqualTo( 3 ) ;

			// assertThat( responseJson.at( "/errors/localhost" ).toString() )
			// .as( " capabilityHealth: /errors/localhost" )
			// .contains( "CsAgent: diskUtil Value", "Exceeds configured maximum:
			// 200" );

		}

	}

}
