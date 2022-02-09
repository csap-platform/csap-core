package org.csap.agent.project.loader ;

import static org.assertj.core.api.Assertions.assertThat ;

import java.io.File ;
import java.net.URI ;
import java.util.stream.Collectors ;

import org.csap.agent.CsapBareTest ;
import org.csap.agent.CsapConstants ;
import org.csap.agent.container.C7 ;
import org.csap.agent.model.Application ;
import org.csap.agent.model.DefinitionConstants ;
import org.csap.agent.model.Project ;
import org.csap.agent.model.ServiceAttributes ;
import org.csap.agent.services.ServiceOsManager ;
import org.csap.helpers.CSAP ;
import org.csap.helpers.CsapApplication ;
import org.junit.jupiter.api.BeforeAll ;
import org.junit.jupiter.api.Test ;

class Service_Templates extends CsapBareTest {

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
				.findAndCloneServiceDefinition( C7.dockerService.val( ) ) ;
		logger.debug( "csapTemplateDefinition: {}", csapTemplateDefinition ) ;
		assertThat( csapTemplateDefinition.path( Project.DEFINITION_SOURCE ).asText( ) ).isEqualTo( "csap-templates" ) ;
		assertThat( csapTemplateDefinition.path( Project.DEFINITION_COPY ).isMissingNode( ) ).isTrue( ) ;

		var csapTemplateFromCopyDefinition = getApplication( ).getProject( "Supporting Sample A" )
				.findAndCloneServiceDefinition( C7.dockerService.val( ) ) ;
		logger.debug( "csapTemplateFromCopyDefinition: {}", csapTemplateFromCopyDefinition ) ;
		assertThat( csapTemplateFromCopyDefinition.path( Project.DEFINITION_SOURCE ).asText( ) ).isEqualTo(
				"copySource" ) ;
		assertThat( csapTemplateFromCopyDefinition.path( Project.DEFINITION_COPY ).asText( ) ).isEqualTo(
				C7.dockerService.val( ) ) ;

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

		assertThat( verifyService.getRawAutoStart( ) ).isEqualTo( 9999 ) ;

		// ensure release package has all names
		assertThat( getApplication( ).getActiveProject( ).getAllPackagesModel( ).getServiceNamesInLifecycle( ) )
				.contains( verifyService.getName( ) ) ;

		var definition = getApplication( ).getRootProject( ).findAndCloneServiceDefinition( verifyService.getName( ) ) ;

		logger.debug( "definition: {}", CSAP.jsonPrint( definition ) ) ;

		assertThat( definition.path( ServiceAttributes.port.json( ) ).asInt( ) ).isEqualTo( 7011 ) ;

	}

	@Test
	void verify_import_overrides ( ) {

		logger.info( CsapApplication.testHeader( ) ) ;

		var demoWithOverridesService = getApplication( )
				.findFirstServiceInstanceInLifecycle( "demo-import-overrides" ) ;

		logger.info( "demoWithOverridesService: {}", demoWithOverridesService ) ;

		assertThat( demoWithOverridesService ).isNotNull( ) ;

		assertThat( demoWithOverridesService.getParameters( ) ).isEqualTo( "base params" ) ;

		assertThat( demoWithOverridesService.getRawAutoStart( ) ).isEqualTo( 9001 ) ;

		assertThat( demoWithOverridesService.getPort( ) ).isEqualTo( "8001" ) ;

		assertThat( demoWithOverridesService.getDescription( ) ).isEqualTo( "dev description" ) ;

		assertThat( demoWithOverridesService.getDeploymentNotes( ) ).isEqualTo( "test-base-env notes" ) ;

		assertThat( demoWithOverridesService.getKubernetesReplicaCount( ).asInt( ) ).isEqualTo( 9 ) ;

		// ensure release package has all names
		assertThat( getApplication( ).getActiveProject( ).getAllPackagesModel( ).getServiceNamesInLifecycle( ) )
				.contains( demoWithOverridesService.getName( ) ) ;

		//
		// verify kubernetes yaml specs
		//
		var specUriStream = getServiceOsManager( ).buildSpecificationFileArray(
				demoWithOverridesService,
				demoWithOverridesService.getKubernetesDeploymentSpecifications( ) ) ;

		var specPathList = specUriStream.collect( Collectors.toList( ) ) ;
		logger.info( "specFiles: {}", specPathList ) ;

		assertThat( specPathList.size( ) ).isEqualTo( 3 ) ;
		assertThat( specPathList.toString( ) )
				.doesNotContain( CsapConstants.SEARCH_RESOURCES )
				.contains( "definitions/simple-packages/resources/demo-import-overrides/common/k8-import-sample.yaml" )
				.contains( "definitions/simple-packages/resources/demo-import-overrides/dev/k8-import-over.yaml" )
				.contains(
						"definitions/simple-packages/resources/demo-import-overrides/test-base-env/k8-import-base-env.yaml" ) ;

		var sampleDeployYamlFileNames = specPathList.stream( )
				.map( specUri -> new File( specUri ) )
				.collect( Collectors.toList( ) ) ;

		var notOverridenDeploySpec = sampleDeployYamlFileNames.get( 0 ) ;

		// getServiceOsManager().buildYamlTemplate( simpleService, sourceFile ) ;
		var sampleDeployFileContents = Application.readFile( notOverridenDeploySpec ) ;
		logger.info( "Original yaml: {} ", sampleDeployFileContents ) ;

		assertThat( sampleDeployFileContents ).contains( "$$service-name" ) ;

		var deploymentFile = getServiceOsManager( )
				.buildDeplomentFile( demoWithOverridesService, notOverridenDeploySpec, getJsonMapper( )
						.createObjectNode( ) ) ;

		var yaml_with_vars_updated = Application.readFile( deploymentFile ) ;

		logger.info( "deploymentFile: {} yaml_with_vars_updated: {} ", deploymentFile, yaml_with_vars_updated ) ;

		assertThat( yaml_with_vars_updated )
				.doesNotContain( CsapConstants.CSAP_VARIABLE_PREFIX )
				.doesNotContain( "$$service-name" )
				.doesNotContain( "$$service-namespace" )
				.contains( "demo-import-overrides-id" ) ;

		//
		// OverRidden current environment
		//
		var devDeploySpec = sampleDeployYamlFileNames.get( 1 ) ;

		var devDeploymentFile = getServiceOsManager( )
				.buildDeplomentFile( demoWithOverridesService, devDeploySpec, getJsonMapper( ).createObjectNode( ) ) ;

		yaml_with_vars_updated = Application.readFile( devDeploymentFile ) ;

		logger.info( "devDeploySpec:{} yaml_with_vars_updated: {} ", devDeploySpec, yaml_with_vars_updated ) ;

		assertThat( yaml_with_vars_updated )
				.doesNotContain( CsapConstants.CSAP_VARIABLE_PREFIX )
				.doesNotContain( "$$service-name" )
				.doesNotContain( "$$service-namespace" )
				.doesNotContain( "import-over-base" )
				.contains( "import-over-dev" ) ;

		//
		// OverRidden - imports from base
		//
		var baseDeploySpec = sampleDeployYamlFileNames.get( 2 ) ;

		var baseDeploymentFile = getServiceOsManager( )
				.buildDeplomentFile( demoWithOverridesService, baseDeploySpec, getJsonMapper( ).createObjectNode( ) ) ;

		yaml_with_vars_updated = Application.readFile( baseDeploymentFile ) ;

		logger.info( "baseDeploymentFile:{} yaml_with_vars_updated: {} ", baseDeploymentFile, yaml_with_vars_updated ) ;

		assertThat( yaml_with_vars_updated )
				.doesNotContain( CsapConstants.CSAP_VARIABLE_PREFIX )
				.doesNotContain( "$$service-name" )
				.doesNotContain( "$$service-namespace" )
				.doesNotContain( "import-should-not-be-used" )
				.contains( "import-over-base-env" ) ;

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

		ServiceOsManager serviceOsManager = new ServiceOsManager( getCsapApis( ) ) ;

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
