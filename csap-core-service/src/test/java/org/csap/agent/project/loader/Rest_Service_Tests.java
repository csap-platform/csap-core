/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.csap.agent.project.loader ;

import static org.assertj.core.api.Assertions.assertThat ;

import java.io.File ;
import java.util.List ;
import java.util.stream.Collectors ;

import org.apache.commons.io.FileUtils ;
import org.csap.agent.CsapBareTest ;
import org.csap.agent.api.ApplicationApi ;
import org.csap.agent.api.ContainerApi ;
import org.csap.agent.api.ModelApi ;
import org.csap.agent.integration.container.KubernetesCsapTests ;
import org.csap.agent.integrations.CsapEvents ;
import org.csap.agent.model.Application ;
import org.csap.agent.model.ProjectLoader ;
import org.csap.agent.model.ServiceInstance ;
import org.csap.agent.services.ServiceOsManager ;
import org.csap.helpers.CSAP ;
import org.csap.helpers.CsapApplication ;
import org.junit.jupiter.api.BeforeAll ;
import org.junit.jupiter.api.Test ;

import com.fasterxml.jackson.databind.JsonNode ;
import com.fasterxml.jackson.databind.node.ObjectNode ;

/**
 *
 * @author someDeveloper
 */

public class Rest_Service_Tests extends CsapBareTest {

	File testDefinition = new File(
			Rest_Service_Tests.class.getResource( "application-agent.json" ).getPath( ) ) ;

	ServiceOsManager serviceOsManager ;

	ModelApi modelApi ;
	ApplicationApi applicationApi ;
	ContainerApi containerApi ;

	@BeforeAll
	void beforeAll ( )
		throws Exception {

		modelApi = new ModelApi( getApplication( ) ) ;
		applicationApi = new ApplicationApi( getCsapApis( ), new CsapEvents( ), null ) ;
		containerApi = new ContainerApi( getCsapApis( ), getJsonMapper( ) ) ;

		getApplication( ).setAgentRunHome( System.getProperty( "user.home" ) ) ;
		getApplication( ).getProjectLoader( ).setAllowLegacyNames( true ) ;

		// CSAP.setLogToDebug( DefinitionParser.class.getName() );
		assertThat( getApplication( ).loadDefinitionForJunits( false, testDefinition ) )
				.as( "No Errors or warnings" )
				.isTrue( ) ;

		serviceOsManager = new ServiceOsManager( getCsapApis( ) ) ;

		CSAP.setLogToInfo( ProjectLoader.class.getName( ) ) ;

		logger.info( CsapApplication.SETUP_HEAD + "Using: " + testDefinition.getAbsolutePath( ) ) ;

	}

	@Test
	void yamlSummary_tests ( ) throws Exception {

		var testYaml = new File(
				KubernetesCsapTests.class.getResource(
						"k8-nginx-multiple.yaml" )
						.getPath( ) ) ;

		var yamlContent = FileUtils.readFileToString( testYaml ) ;
		var yamlSummary = containerApi.buildSummary( yamlContent ) ;
		logger.info( "yamlSummary: {}", yamlSummary ) ;

		containerApi.issueAudit( "aaa", "bbb", yamlContent ) ;

		var jobYamlFile = new File(
				getClass( ).getResource(
						"create-rni-job.yaml" )
						.getPath( ) ) ;
		var jobYamlContent = FileUtils.readFileToString( jobYamlFile ) ;

		var jobSummary = containerApi.buildJobYamlSumary( jobYamlContent ) ;
		assertThat( jobSummary )
				.contains( "name: alarm-punisher-1620224171504" )
				.contains( "command: [\"/opt/flexnet/dk/dataextraction/de_commands/alarm.sh\"]" ) ;
		// containerApi.issueAudit( "ccc", "ddd", jobYamlContent );

	}

	@Test
	void verify_deploy_kubernetes ( ) {

		logger.info( CsapApplication.testHeader( ) ) ;

		ServiceInstance k8Service = getApplication( ).serviceNameToAllInstances( ).get( "k8s-spec-deploy-service" ).get(
				0 ) ;

		String k8sServices = getApplication( ).getServiceInstances( Application.ALL_PACKAGES, k8Service.getName( ) )
				.map( instance -> {

					return instance.toSummaryString( ) + ", cluster: " + instance.getCluster( ) + " is k8: "
							+ instance.is_cluster_kubernetes( ) ;

				} )
				.collect( Collectors.joining( "\n" ) ) ;

		logger.info( "k8sServices: {}", k8sServices ) ;

		try {

			getApplication( ).setJvmInManagerMode( true ) ;
			ObjectNode deployStatus = applicationApi.serviceDeploy( k8Service.getName( ), k8Service.getCluster( ), null,
					null, false,
					"peter", "pass", null ) ;
			logger.info( "Deploy Results: {}", CSAP.jsonPrint( deployStatus ) ) ;

			assertThat( deployStatus.path( "hosts" ).get( 0 ).asText( ) )
					.isEqualTo( "master-host" ) ;

		} catch ( Exception e ) {

			logger.info( "Failed deployment: {}", CSAP.buildCsapStack( e ) ) ;

		}

	}

	@Test
	public void verify_modelapi_serviceIds ( ) {

		logger.info( CsapApplication.testHeader( ) ) ;

		JsonNode ids = modelApi.serviceIds( null, null, false, false ) ;

		logger.info( "ids: {}", CSAP.jsonPrint( ids ) ) ;

		assertThat( ids.path( 0 ).asText( ) )
				.isEqualTo( "jdk_0" ) ;

	}

	@Test
	public void verify_modelapi_serviceOrder ( ) {

		logger.info( CsapApplication.testHeader( ) ) ;

		JsonNode allServicesInOrder = modelApi.serviceNames( null, null, true, false ) ;

		logger.info( "order: {}", CSAP.jsonPrint( allServicesInOrder ) ) ;

		assertThat( allServicesInOrder.get( 0 ).asText( ) )
				.isEqualTo(
						"jdk                      0                        2                        test-cluster" ) ;

		JsonNode filteredIds = modelApi.serviceNames( null, List.of( "simple-cluster", "base-os" ), true, false ) ;

		logger.info( "filteredIds: {}", CSAP.jsonPrint( filteredIds ) ) ;

		assertThat( filteredIds.size( ) )
				.isEqualTo( 2 ) ;

		JsonNode reversed = modelApi.serviceNames( null, List.of( "simple-cluster", "base-os" ), true, true ) ;

		logger.info( "reversed: {}", CSAP.jsonPrint( reversed ) ) ;

	}

}
