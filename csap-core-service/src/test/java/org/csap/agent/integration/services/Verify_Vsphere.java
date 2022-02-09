package org.csap.agent.integration.services ;

import static org.assertj.core.api.Assertions.assertThat ;

import java.io.IOException ;

import org.csap.agent.CsapThinTests ;
import org.csap.agent.container.C7 ;
import org.csap.agent.integrations.Vsphere ;
import org.csap.agent.ui.explorer.VsphereExplorer ;
import org.csap.helpers.CSAP ;
import org.csap.helpers.CsapApplication ;
import org.junit.jupiter.api.BeforeAll ;
import org.junit.jupiter.api.DisplayName ;
import org.junit.jupiter.api.Test ;

@DisplayName ( "Integration: VSphere" )
class Verify_Vsphere extends CsapThinTests {

	Vsphere vsphere ;

	VsphereExplorer vsphereExplorer ;

	@BeforeAll
	void beforeAll ( ) {

		logger.info( CsapApplication.testHeader( ) ) ;

		vsphere = new Vsphere( getJsonMapper( ), getCsapApis( ) ) ;

		vsphereExplorer = new VsphereExplorer( vsphere, getCsapApis( ), getJsonMapper( ) ) ;

		loadDefaultCsapApplicationDefinition( ) ;

	}

	@Test
	void verify_govc_commands ( ) {

		logger.info( CsapApplication.testHeader( ) ) ;

		var datastoreListCommand = getCsapApis( ).osManager( ).getOsCommands( ).getGovcDatastoreList( ) ;
		logger.info( "listCommand: {}", datastoreListCommand ) ;

		assertThat( datastoreListCommand.size( ) )
				.isEqualTo( 2 ) ;

		var vmListCommand = getCsapApis( ).osManager( ).getOsCommands( ).getGovcVmList( "vm/RNIs/CSAP-DEV_p" ) ;
		logger.info( "vmListCommand: {}", vmListCommand ) ;

		assertThat( vmListCommand.size( ) )
				.isEqualTo( 4 ) ;

		assertThat( vmListCommand.toString( ) )
				.contains( "CSAP-DEV_p" ) ;

		var commandResults = vsphere.govcRun( vmListCommand ) ;
		logger.info( "govcRun results: {}", commandResults ) ;

		assertThat( commandResults )
				.contains( "GOVC_DATACENTER=***REMOVED***" ) ;

	}

	@Test
	void verify_datastore_listing ( ) {

		logger.info( CsapApplication.testHeader( ) ) ;

		var datastores = vsphere.dataStores( false ) ;
		logger.info( "datastores: {}", CSAP.jsonPrint( datastores ) ) ;
		logger.info( "datastore 0 : {}", CSAP.jsonPrint( datastores.get( 0 ) ) ) ;

		assertThat( datastores.size( ) )
				.isEqualTo( 115 ) ;

		datastores = vsphere.dataStores( true ) ;
		logger.info( "filtered datastores: {}", CSAP.jsonPrint( datastores ) ) ;

		assertThat( datastores.size( ) )
				.isEqualTo( 1 ) ;

	}

	@Test
	void verify_datastore_browsing ( ) {

		logger.info( CsapApplication.testHeader( ) ) ;

		var datastores = vsphereExplorer.datastores_listing( false ) ;
		logger.debug( "datastores: {}", CSAP.jsonPrint( datastores ) ) ;
		logger.info( "datastore 0 : {}", CSAP.jsonPrint( datastores.get( 0 ) ) ) ;

		assertThat( datastores.size( ) )
				.isEqualTo( 115 ) ;

	}

	@Test
	void verify_datastore_info ( ) {

		logger.info( CsapApplication.testHeader( ) ) ;

		var datastore = vsphere.dataStore_info( "CSAP_DS1_NFS" ) ;

		logger.info( "datastore: {}", CSAP.jsonPrint( datastore ).substring( 0, 200 ) ) ;

		assertThat( datastore.at( "/details/command" ).asText( ) )
				.isEqualTo( "[govc, datastore.info, -json=true, CSAP_DS1_NFS]" ) ;

		assertThat( datastore.at( "/details/OverallStatus" ).asText( ) ).isEqualTo( "green" ) ;

	}

	@Test
	void verify_datastore_files ( ) {

		logger.info( CsapApplication.testHeader( ) ) ;

		var datastoreFiles = vsphere.datastore_files( "CSAP_DS1_NFS", "" ) ;

		logger.info( "datastore: {}", CSAP.jsonPrint( datastoreFiles ).substring( 0, 600 ) ) ;

		assertThat( datastoreFiles.size( ) )
				.isEqualTo( 12 ) ;

		assertThat( datastoreFiles.at( "/0/Path" ).asText( ) )
				.isEqualTo( "peter.vmdk" ) ;

		var datastoreListing = vsphereExplorer.datastore_files( "CSAP_DS1_NFS:" ) ;
		logger.info( "datastoreListing: {}", CSAP.jsonPrint( datastoreListing ) ) ;

	}

	@Test
	void verify_datastore_file_add ( ) {

		logger.info( CsapApplication.testHeader( ) ) ;

		var addResults = vsphere.datastore_file_add( "CSAP_DS1_NFS", "demo-folder/demo.vmdk", "9M", "thin" ) ;

		logger.info( "addResults: {}", addResults.path( C7.response_plain_text.val( ) ).asText( ) ) ;

	}

	@Test
	void verify_datastore_file_find ( )
		throws IOException {

		logger.info( CsapApplication.testHeader( ) ) ;

		var findResults = vsphere.datastore_file_find( "demo-folder/demo.vmdk" ) ;

		logger.info( "findResults: {}", findResults.path( C7.response_plain_text.val( ) ).asText( ) ) ;

	}

	@Test
	void verify_datastore_file_delete ( ) {

		logger.info( CsapApplication.testHeader( ) ) ;

		var deleteResults = vsphere.datastore_file_delete( "CSAP_DS1_NFS", "demo-folder/demo.vmdk" ) ;

		logger.info( "deleteResults: {}", deleteResults.path( C7.response_plain_text.val( ) ).asText( ) ) ;

	}

	@Test
	void verify_datastore_files_recurse ( ) {

		logger.info( CsapApplication.testHeader( ) ) ;

		var datastoreFiles = vsphere.datastore_files_recurse( "CSAP_DS1_NFS" ) ;

		logger.info( "datastore: {}", CSAP.jsonPrint( datastoreFiles ).substring( 0, 200 ) ) ;

		assertThat( datastoreFiles.at( "/command" ).asText( ) )
				.isEqualTo( "[govc, datastore.ls, -ds, CSAP_DS1_NFS, -R=true, -json=true, -l=true]" ) ;

		logger.info( "kubevols: {}", CSAP.jsonPrint( datastoreFiles.at( "/kubevols_" ) ) ) ;

		assertThat( datastoreFiles.at( "/kubevols_" ).size( ) )
				.isEqualTo( 1 ) ;

		logger.info( "vmdks: {}", CSAP.jsonPrint( datastoreFiles.at( "/vmdks" ) ) ) ;

		assertThat( datastoreFiles.at( "/vmdks" ).size( ) )
				.isEqualTo( 14 ) ;

	}

	@Test
	void verify_vm_listing ( ) {

		logger.info( CsapApplication.testHeader( ) ) ;

		var vms = vsphere.vm_find( "/***REMOVED***/vm" ) ;
		logger.debug( "vm: {}", CSAP.jsonPrint( vms ) ) ;
		logger.info( "vm 0 : {}", CSAP.jsonPrint( vms.get( 0 ) ) ) ;
		logger.info( "vm 1 : {}", CSAP.jsonPrint( vms.get( 1 ) ) ) ;

		assertThat( vms.size( ) )
				.isEqualTo( 14 ) ;

		assertThat( vms.get( 0 ).asText( ) )
				.isEqualTo( "[govc, find, /***REMOVED***/vm, -type, m]" ) ;

		vms = vsphere.vm_find( "***REMOVED***/CSAP-DEV_p" ) ;
		logger.debug( "vm: {}", CSAP.jsonPrint( vms ) ) ;
		logger.info( "vm 0 : {}", CSAP.jsonPrint( vms.get( 0 ) ) ) ;
		logger.info( "vm 1 : {}", CSAP.jsonPrint( vms.get( 1 ) ) ) ;

		assertThat( vms.size( ) )
				.isEqualTo( 14 ) ;

		assertThat( vms.get( 0 ).asText( ) )
				.isEqualTo( "[govc, find, ***REMOVED***/CSAP-DEV_p, -type, m]" ) ;

	}

	@Test
	void verify_vm_browsing ( ) {

		logger.info( CsapApplication.testHeader( ) ) ;

		var vms_default_filter = vsphereExplorer.vm_listing( true, "" ) ;
		logger.info( "vms_default_filter : {}", CSAP.jsonPrint( vms_default_filter ) ) ;

		assertThat( vms_default_filter.size( ) )
				.isEqualTo( 14 ) ;

		assertThat( vms_default_filter.at( "/1/label" ).asText( ) )
				.isEqualTo( "csap-dev11" ) ;

	}

	@Test
	void verify_vm_folder_list ( ) {

		logger.info( CsapApplication.testHeader( ) ) ;

		var vms_folder_list = vsphereExplorer.vm_listing( false, "" ) ;
		logger.info( "vms_folder_list : {}", CSAP.jsonPrint( vms_folder_list ) ) ;

		assertThat( vms_folder_list.size( ) )
				.isEqualTo( 40 ) ;

		// VM

		assertThat( vms_folder_list.at( "/0/attributes/path" ).asText( ) )
				.isEqualTo( "[govc, ls, -l=true, /***REMOVED***/vm]" ) ;

		assertThat( vms_folder_list.at( "/1/attributes/folderUrl" ).asText( ) )
				.isEqualTo( "vsphere/vm" ) ;

		assertThat( vms_folder_list.at( "/1/label" ).asText( ) )
				.isEqualTo( "ws-JeffW-WS" ) ;

		assertThat( vms_folder_list.at( "/39/attributes/folderUrl" ).asText( ) )
				.isEqualTo( "vsphere/vms/browse" ) ;

		assertThat( vms_folder_list.at( "/39/attributes/path" ).asText( ) )
				.isEqualTo( "/***REMOVED***/vm/Flex Net Sim/" ) ;

		var vms_folder_path_find = vsphereExplorer.vm_listing( false, "/***REMOVED***/vm/SecureCommandServer/" ) ;
		logger.debug( "vms_folder_list : {}", CSAP.jsonPrint( vms_folder_path_find ) ) ;

		assertThat( vms_folder_path_find.size( ) )
				.isEqualTo( 14 ) ;

	}

	@Test
	void verify_vm_info ( ) {

		logger.info( CsapApplication.testHeader( ) ) ;

		var vm = vsphere.vmInfo( "***REMOVED***/CSAP-DEV_p/csap-dev2/csap-dev07" ) ;

		logger.info( "vm: {}", CSAP.jsonPrint( vm ).substring( 0, 400 ) ) ;

		assertThat( vm.at( "/details/command" ).asText( ) )
				.isEqualTo( "[govc, vm.info, -json=true, ***REMOVED***/CSAP-DEV_p/csap-dev2/csap-dev07]" ) ;

		assertThat( vm.at( "/details/ConfigStatus" ).asText( ) ).isEqualTo( "green" ) ;

	}
}
