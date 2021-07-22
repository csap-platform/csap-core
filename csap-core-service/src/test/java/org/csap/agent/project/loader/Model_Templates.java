package org.csap.agent.project.loader ;

import static org.assertj.core.api.Assertions.assertThat ;

import java.io.File ;
import java.net.URI ;
import java.util.stream.Collectors ;

import org.csap.agent.CsapBareTest ;
import org.csap.agent.model.Application ;
import org.csap.agent.model.DefinitionConstants ;
import org.csap.agent.model.Project ;
import org.csap.agent.model.ServiceAttributes ;
import org.csap.agent.services.ServiceOsManager ;
import org.csap.helpers.CSAP ;
import org.csap.helpers.CsapApplication ;
import org.junit.jupiter.api.BeforeAll ;
import org.junit.jupiter.api.Test ;

class Model_Templates extends CsapBareTest {

	String definitionPath = "/definitions/simple-packages/main-project.json" ;
	File csapApplicationDefinition = new File( getClass( ).getResource( definitionPath ).getPath( ) ) ;

	@BeforeAll
	void beforeAll ( )
		throws Exception {

		Application.setDeveloperMode( true ) ;

		getApplication( ).getProjectLoader( ).setAllowLegacyNames( true ) ;

		CSAP.setLogToInfo( Project.class.getName( ) ) ;
		assertThat( getApplication( ).loadDefinitionForJunits( false, csapApplicationDefinition ) )
				.as( "No Errors or warnings" )
				.isTrue( ) ;
		CSAP.setLogToInfo( Project.class.getName( ) ) ;

	}

	@Test
	void verify_release_packages ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		var packages = getApplication( ).getActiveProject( ).releasePackagesRootFirst( )
				.map( Project::summary )
				.collect( Collectors.joining( "\n\t" ) ) ;

		logger.info( "packages: \n\t{} ", packages ) ;

	}

	@Test
	void verify_copy_source_parsing ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		var jvmServiceDefinition = getApplication( ).getActiveProject( ).getRootPackage( )
				.findAndCloneServiceDefinition( "ServletSample" ) ;
		logger.debug( "jvmServiceDefinition: {}", jvmServiceDefinition ) ;
		assertThat( jvmServiceDefinition.path( Project.DEFINITION_SOURCE ).asText( ) )
				.isEqualTo( csapApplicationDefinition.getName( ) ) ;

		// CSAP.setLogToDebug( ReleasePackage.class.getName() );
		var postgresDefinition = getApplication( ).getActiveProject( ).getRootPackage( ).findAndCloneServiceDefinition(
				"postgres" ) ;
		// CSAP.setLogToInfo( ReleasePackage.class.getName() );

		logger.debug( "postgresDefinition: {}", postgresDefinition ) ;
		assertThat( postgresDefinition.path( Project.DEFINITION_SOURCE ).asText( ) )
				.isEqualTo( csapApplicationDefinition.getName( ) ) ;

		var postgresLocalDefinition = getApplication( ).getActiveProject( ).findAndCloneServiceDefinition(
				"postgresLocal" ) ;
		logger.debug( "postgresLocalDefinition: {}", postgresLocalDefinition ) ;

		var postgressLocalInstance = getApplication( ).findFirstServiceInstanceInLifecycle( "postgresLocal" ) ;
		assertThat( postgressLocalInstance.getDescription( ) ).isEqualTo( "junit-docker-description-override" ) ;
		assertThat( postgressLocalInstance.getHostName( ) ).isEqualTo( "host-a4" ) ;

		var copyTemplateDefinition = getApplication( ).getProject( "Supporting Sample A" )
				.findAndCloneServiceDefinition( "ServletSample" ) ;
		logger.debug( "copyTemplateDefinition: {}", copyTemplateDefinition ) ;
		assertThat( copyTemplateDefinition.path( Project.DEFINITION_SOURCE ).asText( ) ).isEqualTo( "copySource" ) ;

		var csapTemplateDefinition = getApplication( ).getProject( "SampleDefaultPackage" )
				.findAndCloneServiceDefinition( "docker" ) ;
		logger.debug( "csapTemplateDefinition: {}", csapTemplateDefinition ) ;
		assertThat( csapTemplateDefinition.path( Project.DEFINITION_SOURCE ).asText( ) ).isEqualTo( "csap-templates" ) ;
		assertThat( csapTemplateDefinition.path( Project.DEFINITION_COPY ).isMissingNode( ) ).isTrue( ) ;

		var csapTemplateFromCopyDefinition = getApplication( ).getProject( "Supporting Sample A" )
				.findAndCloneServiceDefinition( "docker" ) ;
		logger.debug( "csapTemplateFromCopyDefinition: {}", csapTemplateFromCopyDefinition ) ;
		assertThat( csapTemplateFromCopyDefinition.path( Project.DEFINITION_SOURCE ).asText( ) ).isEqualTo(
				"copySource" ) ;
		assertThat( csapTemplateFromCopyDefinition.path( Project.DEFINITION_COPY ).asText( ) ).isEqualTo( "docker" ) ;

	}

	@Test
	void verify_kubernetes_default_provider ( ) {

		logger.info( CsapApplication.testHeader( ) ) ;

		var kubeletInstance = getApplication( ).kubeletInstance( ) ;
		logger.info( "kubeletInstance: {}", kubeletInstance ) ;

		assertThat( kubeletInstance ).isNotNull( ) ;
		assertThat( kubeletInstance.isKubernetesMaster( ) ).isTrue( ) ;

		var metricsServer = getApplication( ).findServiceByNameOnCurrentHost( "metrics-server" ) ;
		logger.info( "metricsServer: {}", metricsServer ) ;

		assertThat( metricsServer ).isNotNull( ) ;

	}

	@Test
	void verify_external_template_with_override ( ) {

		logger.info( CsapApplication.testHeader( ) ) ;

		var verifyService = getApplication( ).findFirstServiceInstanceInLifecycle( "csap-verify-service" ) ;
		logger.info( "serviceFromExternalDef: {}", verifyService ) ;
		assertThat( verifyService ).isNotNull( ) ;

		// ensure release package has all names
		assertThat( getApplication( ).getActiveProject( ).getAllPackagesModel( ).getServiceNamesInLifecycle( ) )
				.contains( verifyService.getName( ) ) ;

		var definition = getApplication( ).getRootProject( ).findAndCloneServiceDefinition( verifyService.getName( ) ) ;

		logger.info( "definition: {}", CSAP.jsonPrint( definition ) ) ;

		assertThat( definition.path( ServiceAttributes.port.json( ) ).asInt( ) ).isEqualTo( 7011 ) ;

	}

	@Test
	void verify_external_service ( ) {

		logger.info( CsapApplication.testHeader( ) ) ;

		var serviceFromExternalDef = getApplication( ).findFirstServiceInstanceInLifecycle( "service-from-file" ) ;
		logger.info( "serviceFromExternalDef: {}", serviceFromExternalDef ) ;
		assertThat( serviceFromExternalDef ).isNotNull( ) ;

		// ensure release package has all names
		assertThat( getApplication( ).getActiveProject( ).getAllPackagesModel( ).getServiceNamesInLifecycle( ) )
				.contains( serviceFromExternalDef.getName( ) ) ;

	}

	@Test
	void verify_template_listing ( ) {

		logger.info( CsapApplication.testHeader( ) ) ;

		var namesInModel = getApplication( ).getActiveProject( ).getServiceNamesInModel( false ) ;
		var allNamesModel = getApplication( ).getActiveProject( ).getServiceNamesInModel( true ) ;

		logger.info( "namesInModel: {}\n\tallNamesModel: {}", namesInModel, allNamesModel ) ;

		assertThat( namesInModel.size( ) ).isNotEqualTo( allNamesModel.size( ) ) ;

		assertThat( namesInModel ).doesNotContain( "events-service", "events-mongo" ) ;
		assertThat( allNamesModel ).contains( "events-service", "events-mongo" ) ;

	}

	@Test
	void verify_package_templates ( ) {

		logger.info( CsapApplication.testHeader( "definition folder: {}" ), getApplication( ).getDefinitionFolder( ) ) ;

		var packageTemplateService = getApplication( ).findServiceByNameOnCurrentHost( "xxx-service" ) ;
		logger.info( "packageTemplateService: {}", packageTemplateService ) ;

		assertThat( packageTemplateService ).isNotNull( ) ;

		ServiceOsManager serviceOsManager = new ServiceOsManager( getApplication( ) ) ;

		var specfiles = serviceOsManager.buildSpecificationFileArray(
				packageTemplateService,
				packageTemplateService.getKubernetesDeploymentSpecifications( ) ) ;

		var fileNames = specfiles.map( URI::toString ).collect( Collectors.joining( "," ) ) ;

		logger.info( "fileNames: {}", fileNames ) ;

		assertThat( fileNames ).contains(
				"simple-packages/" + DefinitionConstants.projectsExtracted.key( )
						+ "/sample-project/resources/xxx-service/common/simple-nginx.yaml" ) ;

	}

}
