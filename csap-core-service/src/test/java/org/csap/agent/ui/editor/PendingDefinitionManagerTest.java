package org.csap.agent.ui.editor ;

import static org.assertj.core.api.Assertions.assertThat ;

import java.io.BufferedWriter ;
import java.io.File ;
import java.io.IOException ;
import java.io.StringWriter ;
import java.util.List ;

import org.apache.commons.io.FileUtils ;
import org.csap.agent.CsapThinTests ;
import org.csap.agent.model.ServiceAttributes ;
import org.csap.helpers.CSAP ;
import org.csap.helpers.CsapApplication ;
import org.junit.jupiter.api.BeforeEach ;
import org.junit.jupiter.api.DisplayName ;
import org.junit.jupiter.api.Nested ;
import org.junit.jupiter.api.Tag ;
import org.junit.jupiter.api.Test ;
import org.junit.jupiter.api.TestInstance ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;

import com.fasterxml.jackson.databind.node.ArrayNode ;
import com.fasterxml.jackson.databind.node.ObjectNode ;

@Tag ( "core" )

@TestInstance ( TestInstance.Lifecycle.PER_CLASS )
@DisplayName ( "PendingDefinitionManagerTest: pending file operations in editor" )

class PendingDefinitionManagerTest extends CsapThinTests {
	Logger logger = LoggerFactory.getLogger( getClass( ) ) ;

	ServiceResources servicesResources ;
	PendingDefinitionManager pendingOpsManager ;
	int testCase = 0 ;

	@BeforeEach
	void beforeEach ( ) {

		File testLocation = new File( CsapApplication.getJunitFolder( ), "testCase-" + ( testCase++ ) ) ;

		if ( testLocation.exists( ) ) {

			try {

				FileUtils.forceDelete( testLocation ) ;

			} catch ( Exception e ) {

				logger.warn( "cleanup: {}", CSAP.buildCsapStack( e ) ) ;

			}

		}

		if ( testLocation.mkdirs( ) ) {

			logger.info( CsapApplication.testHeader( "Created: {}" ), testLocation ) ;

		}

		;
		servicesResources = new ServiceResources( "junit-definition", testLocation, getJsonMapper( ) ) ;
		servicesResources.setJunitMode( true ) ;
		pendingOpsManager = new PendingDefinitionManager( servicesResources, getJsonMapper( ) ) ;

	}

	@Nested
	@DisplayName ( "Service " )
	class Service {

		@Test
		@DisplayName ( "Delete" )
		void verify_service_delete ( )
			throws Exception {

			logger.info( CsapApplication.testHeader( ) ) ;

			File current_service_checked_out = servicesResources.checkedOutFolderService( "current-name" ) ;

			FileUtils.writeStringToFile( new File( current_service_checked_out, "test-file" ), "dummy content" ) ;
			logger.info( "added test content: '{}'", current_service_checked_out.getCanonicalPath( ) ) ;

			assertThat( current_service_checked_out ).exists( ) ;

			pendingOpsManager.addServiceDelete( "current-name" ) ;

			String operationResults = apply_pending_changes( ) ;

			assertThat( operationResults.toString( ) ).doesNotContain( "no resources found" ) ;
			assertThat( current_service_checked_out ).doesNotExist( ) ;

		}

		@Test
		@DisplayName ( "Rename" )
		void verify_service_rename ( )
			throws Exception {

			logger.info( CsapApplication.testHeader( ) ) ;

			File current_service_checked_out = servicesResources.checkedOutFolderService( "current-name" ) ;
			File renamed_service_checked_out = servicesResources.checkedOutFolderService( "new-name" ) ;

			assertThat( renamed_service_checked_out ).doesNotExist( ) ;

			FileUtils.writeStringToFile( new File( current_service_checked_out, "test-file" ), "dummy content" ) ;
			logger.info( "added test content: '{}'", current_service_checked_out.getCanonicalPath( ) ) ;

			pendingOpsManager.addServiceRename( "current-name", "new-name" ) ;

			String operationResults = apply_pending_changes( ) ;

			assertThat( operationResults.toString( ) ).doesNotContain( "no resources found" ) ;
			assertThat( renamed_service_checked_out ).exists( ) ;

		}

		@Test
		@DisplayName ( "Multiple Adds" )
		void verify_service_multiple_resource_added ( )
			throws Exception {

			logger.info( CsapApplication.testHeader( ) ) ;

			String serviceName = "junit-add-resource" ;

			ObjectNode serviceDefinition = getJsonMapper( ).createObjectNode( ) ;
			ArrayNode serviceFiles = serviceDefinition.putArray( DefinitionRequests.DEFINITION_FILES ) ;

			String fileName1 = serviceName + "-file-1" ;
			String fileName2 = serviceName + "-file-2" ;
			serviceFiles.add( buildResourceFile( fileName1, true ) ) ;
			serviceFiles.add( buildResourceFile( fileName2, true ) ) ; // adds ONLY if not there

			pendingOpsManager.processServiceResourceEdits( serviceDefinition, serviceName ) ;
			assertThat( servicesResources.workingFolder( serviceName ) ).exists( ) ;

			String operationResults = apply_pending_changes( ) ;
			assertThat( operationResults.toString( ) ).doesNotContain( "no resources found" ) ;
			assertThat( operationResults.toString( ) ).contains( "File Added" ) ;
			assertThat( servicesResources.workingFolder( serviceName ) ).doesNotExist( ) ;

			File resourceFile = new File( servicesResources.checkedOutFolderService( serviceName ), "common/"
					+ fileName1 ) ;
			assertThat( resourceFile ).exists( ) ;
			File resourceFile2 = new File( servicesResources.checkedOutFolderService( serviceName ), "common/"
					+ fileName2 ) ;
			assertThat( resourceFile2 ).exists( ) ;

		}

		@Test
		@DisplayName ( "copy service with files" )
		void verify_copy_service_with_resource_files ( )
			throws Exception {

			logger.info( CsapApplication.testHeader( "New Files" ) ) ;

			String serviceName = "junit-copy-service" ;

			ObjectNode serviceDefinition = getJsonMapper( ).createObjectNode( ) ;
			ArrayNode serviceFiles = serviceDefinition.putArray( DefinitionRequests.DEFINITION_FILES ) ;

			String fileName1 = serviceName + "-file-1" ;
			String fileName2 = serviceName + "-file-2" ;
			serviceFiles.add( buildResourceFile( fileName1, false ) ) ;
			serviceFiles.add( buildResourceFile( fileName2, false ) ) ;

			String fileNameDeep1 = "deep/the-file" ;
			serviceFiles.add( buildResourceFile( fileNameDeep1, false ) ) ;

			List<String> ignoredFiles = pendingOpsManager.processServiceResourceEdits( serviceDefinition,
					serviceName ) ;
			assertThat( servicesResources.workingFolder( serviceName ) ).exists( ) ;
			assertThat( ignoredFiles ).isEmpty( ) ;

			String operationResults = apply_pending_changes( ) ;
			assertThat( operationResults.toString( ) ).doesNotContain( "no resources found" ) ;
			assertThat( operationResults.toString( ) ).contains( "File Added" ) ;
			assertThat( servicesResources.workingFolder( serviceName ) ).doesNotExist( ) ;

			File resourceFile = new File( servicesResources.checkedOutFolderService( serviceName ), "common/"
					+ fileName1 ) ;
			logger.info( "Verifying fileName1 exists in service resources : {}", resourceFile.getAbsolutePath( ) ) ;
			assertThat( resourceFile ).exists( ) ;
			File resourceFile2 = new File( servicesResources.checkedOutFolderService( serviceName ), "common/"
					+ fileName2 ) ;
			assertThat( resourceFile2 ).exists( ) ;

			logger.info( CsapApplication.header(
					"copying build resources to active definition for test \n\t active: {} \n\t build: {} " ),
					ServiceResources.getActiveProperyOverrideFolder( ).getAbsolutePath( ),
					servicesResources.getBuildPropertyOverrideFolder( ).getAbsolutePath( ) ) ;

			File activePropertyOverrideFolder = ServiceResources.getActiveProperyOverrideFolder( ) ;
			activePropertyOverrideFolder.mkdirs( ) ;
			FileUtils.copyDirectory( servicesResources.getBuildPropertyOverrideFolder( ),
					ServiceResources.getActiveProperyOverrideFolder( ) ) ;

			ObjectNode serviceDefinition2 = getJsonMapper( ).createObjectNode( ) ;
			ArrayNode existingFileDefs = serviceDefinition2.putArray( DefinitionRequests.DEFINITION_FILES ) ;

			String fileName3 = serviceName + "-file-2" ;
			existingFileDefs.add( buildResourceFile( fileName3, false ) ) ;
			// logger.info( "definition: {}", CSAP.jsonPrint( serviceDefinition ) ) ;
			List<String> ignoredFiles2 = pendingOpsManager.processServiceResourceEdits( serviceDefinition2,
					serviceName ) ;
			logger.info( "ignoredFiles2: {}", ignoredFiles2 ) ;
			// String operationResults2 = apply_pending_changes() ;
			assertThat( ignoredFiles2 )
					.as( "Adding service files already added" )
					.isNotEmpty( )
					.contains( "junit-copy-service/common/junit-copy-service-file-2" ) ;

			logger.info( CsapApplication.header( "Nested resources" ) ) ;
			String deepApplyResults = apply_pending_changes( ) ;

			logger.info( CsapApplication.header( "deepApplyResults: \n {}" ), deepApplyResults ) ;
			ObjectNode testDefinition = getJsonMapper( ).createObjectNode( ) ;

			servicesResources.addResourceFilesToDefinition( testDefinition, serviceName, ".*", ".*" ) ;
			logger.info( "addResourceFilesToDefinition: {}", CSAP.jsonPrint( testDefinition ) ) ;

			assertThat( testDefinition.at( "/files/0/name" ).asText( ) )
					.isEqualTo( "the-file" ) ;

			assertThat( testDefinition.at( "/files/0/lifecycle" ).asText( ) )
					.isEqualTo( "common/deep" ) ;

			assertThat( testDefinition.at( "/files/0/content/0" ).asText( ) )
					.isEqualTo( "test content from junit" ) ;

		}

	}

	@Nested
	@DisplayName ( "Service Resource Folder" )
	class Service_Resource {

		@Test
		@DisplayName ( "Path" )
		void verify_service_resource_folder ( )
			throws Exception {

			logger.info( CsapApplication.testHeader( ) ) ;

			logger.info( "active service: '{}'", ServiceResources.serviceResourceFolder( "test-service" ) ) ;

			assertThat( ServiceResources.serviceResourceFolder( "test-service" ).getCanonicalPath( ) )
					.contains( ( new File( "resources/test-service" ) ).getPath( ) ) ;

			logger.info( "working service: '{}'", servicesResources.workingFolder( "test-service" )
					.getCanonicalPath( ) ) ;
			assertThat( servicesResources.workingFolder( "test-service" ).getCanonicalPath( ) )
					.contains( ( new File( "build/resources/test-service" ) ).getPath( ) ) ;

			logger.info( "checked out service: '{}'", servicesResources.checkedOutFolderService( "test-service" )
					.getCanonicalPath( ) ) ;
			// assertThat(
			// servicesResources.checkedOutFolderService( "test-service"
			// ).getCanonicalPath() )
			// .contains( (new File(
			// "build/junit-definition/propertyOverride/test-service/resources" )).getPath()
			// ) ;
			assertThat(
					servicesResources.checkedOutFolderService( "test-service" ).getCanonicalPath( ) )
							.contains( ( new File( "/resources/test-service" ) ).getPath( ) ) ;

		}

		@Test
		@DisplayName ( "Add" )
		void verify_service_resource_added ( )
			throws Exception {

			logger.info( CsapApplication.testHeader( ) ) ;

			String serviceName = "junit-add-resource" ;

			addResourceFile( serviceName ) ;

		}

		@Test
		@DisplayName ( "Delete" )
		void verify_service_resource_deleted ( )
			throws Exception {

			logger.info( CsapApplication.testHeader( ) ) ;

			String serviceName = "junit-remove-resource" ;

			File new_resource_file = addResourceFile( serviceName ) ;

			assertThat( new_resource_file ).exists( ) ;

			pendingOpsManager.clearAll( ) ;

			ObjectNode serviceDefinition = getJsonMapper( ).createObjectNode( ) ;
			ArrayNode serviceFiles = serviceDefinition.putArray( DefinitionRequests.DEFINITION_FILES ) ;

			ObjectNode serviceFile = serviceFiles.addObject( ) ;
			serviceFile.put( ServiceAttributes.FileAttributes.name.json, serviceName + "-file" ) ;
			serviceFile.put( ServiceAttributes.FileAttributes.deleteFile.json, true ) ;

			pendingOpsManager.processServiceResourceEdits( serviceDefinition, serviceName ) ;
			String operationResults = apply_pending_changes( ) ;

			assertThat( operationResults.toString( ) ).contains( "File Deleted" ) ;
			assertThat( new_resource_file ).doesNotExist( ) ;

		}

		private File addResourceFile ( String serviceName )
			throws IOException {

			ObjectNode serviceDefinition = getJsonMapper( ).createObjectNode( ) ;
			ArrayNode serviceFiles = serviceDefinition.putArray( DefinitionRequests.DEFINITION_FILES ) ;

			String fileName = serviceName + "-file" ;
			ObjectNode serviceFile = buildResourceFile( fileName, true ) ;
			serviceFiles.add( serviceFile ) ;

			pendingOpsManager.processServiceResourceEdits( serviceDefinition, serviceName ) ;
			assertThat( servicesResources.workingFolder( serviceName ) ).exists( ) ;

			String operationResults = apply_pending_changes( ) ;
			assertThat( operationResults.toString( ) ).doesNotContain( "no resources found" ) ;
			assertThat( operationResults.toString( ) ).contains( "File Added" ) ;
			assertThat( servicesResources.workingFolder( serviceName ) ).doesNotExist( ) ;

			File resourceFile = new File( servicesResources.checkedOutFolderService( serviceName ), "common/"
					+ fileName ) ;
			assertThat( resourceFile ).exists( ) ;

			return resourceFile ;

		}

	}

	private String apply_pending_changes ( )
		throws IOException {

		StringWriter operationResults = new StringWriter( ) ;
		BufferedWriter bufferedWriter = new BufferedWriter( operationResults ) ;

		pendingOpsManager.apply_to_checked_out_folder( bufferedWriter, servicesResources.checkedOutFolderRoot( ) ) ;

		bufferedWriter.flush( ) ;

		logger.info( "Results: {}", operationResults.toString( ) ) ;

		return operationResults.toString( ) ;

	}

	private ObjectNode buildResourceFile ( String fileName , boolean isAddNew ) {

		ObjectNode serviceFile = getJsonMapper( ).createObjectNode( ) ;
		serviceFile.put( ServiceAttributes.FileAttributes.name.json, fileName ) ;

		if ( isAddNew ) {

			serviceFile.put( ServiceAttributes.FileAttributes.newFile.json, true ) ;

		}

		ArrayNode content = serviceFile.putArray( ServiceAttributes.FileAttributes.content.json ) ;
		content.add( "test content from junit" ) ;
		return serviceFile ;

	}

}
