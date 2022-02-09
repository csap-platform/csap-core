package org.csap.agent.integrations ;

import java.io.BufferedWriter ;
import java.io.File ;
import java.io.IOException ;
import java.util.Arrays ;
import java.util.List ;

import org.apache.commons.lang3.StringUtils ;
import org.csap.agent.CsapApis ;
import org.csap.agent.CsapConstants ;
import org.csap.agent.container.C7 ;
import org.csap.agent.linux.OsCommandRunner ;
import org.csap.agent.services.OsManager ;
import org.csap.helpers.CSAP ;
import org.csap.helpers.CsapApplication ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.stereotype.Service ;

import com.fasterxml.jackson.databind.JsonNode ;
import com.fasterxml.jackson.databind.ObjectMapper ;
import com.fasterxml.jackson.databind.node.ArrayNode ;
import com.fasterxml.jackson.databind.node.ObjectNode ;

@Service
public class Vsphere {

	final Logger logger = LoggerFactory.getLogger( this.getClass( ) ) ;

	public static final String VSPHERE_SETTINGS_NAME = "vsphere" ;

	ObjectMapper jsonMapper ;

	CsapApis csapApis ;

	OsCommandRunner govcRunner = new OsCommandRunner( 30, 1, "govRunner" ) ;

	public Vsphere ( ObjectMapper jsonMapper, CsapApis csapApis ) {

		this.jsonMapper = jsonMapper ;
		this.csapApis = csapApis ;

	}

	public String govcRun ( List<String> command ) {

		File workingDir = csapApis.application( ).getCsapInstallFolder( ) ;

		var hungProcessIntervalInSeconds = 5 ;
		var hungProcessMaxIterations = 5 ;
		BufferedWriter outputWriter = null ;

		if ( ! csapApis.application( ).environmentSettings( ).isVsphereConfigured( ) ) {

			logger.warn( "Missing vsphere configuration" ) ;
			return "Not configured" ;

		}

		var environmentVariables = csapApis.application( ).environmentSettings( ).getVsphereEnv( ) ;
		var commandResults = govcRunner.executeString( command, environmentVariables, workingDir, null, null,
				hungProcessIntervalInSeconds,
				hungProcessMaxIterations, outputWriter ) ;

		return OsCommandRunner.trimHeader( commandResults ) ;

	}

	public ArrayNode dataStores ( boolean filterUsingConfiguration ) {

		var allDataStores = jsonMapper.createArrayNode( ) ;

		List<String> collectionScripts = csapApis.osManager( ).getOsCommands( ).getGovcDatastoreList( ) ;

		String scriptOutput = govcRun( collectionScripts ) ;

		logger.debug( "scriptOutput: {}", scriptOutput ) ;

		scriptOutput = csapApis.application( ).check_for_stub( scriptOutput, "govc/datastore-all.txt" ) ;
		String[] serviceLines = scriptOutput.split( OsManager.LINE_SEPARATOR ) ;

		Arrays.stream( serviceLines )
				.filter( StringUtils::isNotEmpty )
				.map( CsapConstants::singleSpace )
				.filter( line -> ! line.startsWith( "#" ) )
				.map( line -> line.split( ":", 2 ) )
				.filter( keyValueArray -> keyValueArray.length == 2 )
				.forEach( keyValueArray -> {

					var key = keyValueArray[0].trim( ).toLowerCase( ) ;
					var value = keyValueArray[1].trim( ) ;

					ObjectNode datastore = null ;

					if ( key.equals( "name" ) ) {

						datastore = allDataStores.addObject( ) ;

					} else {

						datastore = (ObjectNode) allDataStores.get( allDataStores.size( ) - 1 ) ;

					}

					if ( datastore != null ) {

						datastore.put( key, value ) ;

					} else {

						logger.warn( "failed inserting: '{}' - '{}'", key, value ) ;

					}

					// portDetails.put( "line", portLine ) ;
				} ) ;

		var dataStores = allDataStores ;

		if ( filterUsingConfiguration ) {

			var datastoreFilter = csapApis.application( ).environmentSettings( ).getVsphereConfiguration( ).at(
					"/filters/datastore-regex" )
					.asText( ) ;
			logger.info( "datastoreFilter: {}", datastoreFilter ) ;

			if ( StringUtils.isNotEmpty( datastoreFilter ) ) {

				var filteredDataStores = jsonMapper.createArrayNode( ) ;
				CSAP.jsonStream( allDataStores )
						.filter( datastore -> datastore.path( "name" ).asText( ).matches( datastoreFilter ) )
						.forEach( filteredDataStores::add ) ;

				dataStores = filteredDataStores ;

			}

		}

		return dataStores ;

	}

	public ObjectNode dataStore_info ( String datastoreName ) {

		ObjectNode datastore = jsonMapper.createObjectNode( ) ;
		datastore.put( "csapNoSort", true ) ;

		List<String> infoCommand = csapApis.osManager( ).getOsCommands( ).getGovcDatastoreInfo( datastoreName ) ;

		String infoOutput = govcRun( infoCommand ) ;

		infoOutput = csapApis.application( ).check_for_stub( infoOutput, "govc/datastore-info.json" ) ;

		logger.debug( "command: {}, \n\t output: \n {}", infoCommand, infoOutput ) ;

		try {

			var govcInfo = jsonMapper.readTree( infoOutput ) ;

			var dataStoreFull = govcInfo.at( "/Datastores/0" ) ;

			datastore.put( "status", "Type: " + dataStoreFull.at( "/Summary/Type" ).asText( )
					+ ",    status: " + dataStoreFull.at( "/OverallStatus" ).asText( ) ) ;

			datastore.put( "FreeSpace", CsapConstants.getDiskWithUnit( dataStoreFull.at( "/Summary/FreeSpace" )
					.asLong( ) ) ) ;
			datastore.put( "Capacity", CsapConstants.getDiskWithUnit( dataStoreFull.at( "/Summary/Capacity" )
					.asLong( ) ) ) ;

			var listing = datastore.putObject( "datastore files" ) ;
			listing.put( C7.list_folderUrl.val( ), "vsphere/datastore/files" ) ;
			listing.put( "path", datastoreName + ":/" ) ;

			( (ObjectNode) dataStoreFull ).put( "command", infoCommand.toString( ) ) ;
			datastore.set( "details", dataStoreFull ) ;

		} catch ( Exception e ) {

			var reason = CSAP.buildCsapStack( e ) ;
			logger.warn( "Failed reading {} {}", datastoreName, reason ) ;
			datastore.put( C7.error.val( ), "failed api request" ) ;
			datastore.put( C7.errorReason.val( ), reason ) ;

		}

		return datastore ;

	}

	public ArrayNode datastore_files ( String datastoreName , String path ) {

		List<String> lsCommand = csapApis.osManager( ).getOsCommands( ).getGovcDatastoreLs( datastoreName, path ) ;

		String lsOutput = govcRun( lsCommand ) ;

		lsOutput = csapApis.application( ).check_for_stub( lsOutput, "govc/list-datastore.json" ) ;
		logger.debug( "command: {}, \n\t output: \n {}", lsCommand, lsOutput ) ;

		var fileListing = jsonMapper.createArrayNode( ) ;

		JsonNode govcListing = jsonMapper.createObjectNode( ) ;

		try {

			govcListing = jsonMapper.readTree( lsOutput ) ;
			govcListing = govcListing.at( "/0/File" ) ;

			if ( govcListing.isArray( ) ) {

				fileListing = (ArrayNode) govcListing ;

			}

		} catch ( IOException e ) {

			var reason = CSAP.buildCsapStack( e ) ;
			logger.warn( "Failed reading {} {}", datastoreName, reason ) ;
			var error = fileListing.addObject( ) ;
			error.put( C7.error.val( ), "failed api request" ) ;
			error.put( C7.errorReason.val( ), reason ) ;

		}

		return fileListing ;

	}

	synchronized public ObjectNode datastore_file_add (
														String datastoreName ,
														String diskPath ,
														String diskSize ,
														String diskType ) {

		logger.info( "datastoreName: {}, diskPath: {}, diskSize: {}, diskType:{} ",
				datastoreName, diskPath,
				diskSize, diskType ) ;
		ObjectNode result = jsonMapper.createObjectNode( ) ;

		var createFolderOutput = "" ;

		if ( diskPath.contains( "/" ) ) {

			List<String> createFolder = List.of(
					"govc",
					"datastore.mkdir",
					"-ds", datastoreName,
					diskPath.substring( 0, diskPath.lastIndexOf( "/" ) ) ) ;

			createFolderOutput = govcRun( createFolder ) ;

		}

		List<String> createDisk = List.of(
				"govc",
				"datastore.disk.create",
				"-ds", datastoreName,
				"-size", diskSize,
				"-d", diskType,
				diskPath ) ;

		String createDiskOutput = govcRun( createDisk ) ;

		String resultMessage = "" ;

		if ( StringUtils.isNotEmpty( createFolderOutput ) ) {

			resultMessage = CsapApplication.header( "Create Folder: " ) + createFolderOutput ;

		}

		resultMessage += CsapApplication.header( "Create Disk: " ) + createDiskOutput ;

		result.put( C7.response_plain_text.val( ), resultMessage ) ;

		return result ;

	}

	synchronized public ObjectNode datastore_file_find ( String diskPath ) {

		logger.info( "diskPath: {}",
				diskPath ) ;

		ObjectNode result = jsonMapper.createObjectNode( ) ;

		String findOutput = runFindCommand( diskPath ) ;

		var resultMessage = CsapApplication.header( "Find Disk: " ) + OsCommandRunner.trimHeader( findOutput ) ;

		result.put( C7.response_plain_text.val( ), resultMessage ) ;

		return result ;

	}

	private String runFindCommand ( String diskPath ) {

		var csapBashFunctions = csapApis.osManager( ).sourceCommonFunctions( ) ;

		var findCommand = List.of(
				"#!/bin/bash",
				csapBashFunctions,
				"find_kubernetes_vcenter_device " + diskPath ) ;

		// String findOutput = govcRun( findCommand ) ;
		var environmentVariables = csapApis.application( ).environmentSettings( ).getVsphereEnv( ) ;
		String findOutput = "Failed to run command" ;

		try {

			findOutput = govcRunner.runUsingDefaultUser( "find-vsphere-disk", findCommand, null,
					environmentVariables, true ) ;

		} catch ( IOException e ) {

			var message = "Failed to perform find for " + diskPath + CSAP.buildCsapStack( e ) ;
			logger.warn( "{} ", message ) ;
			findOutput += message ;

		}

		return findOutput ;

	}

	synchronized public ObjectNode datastore_file_delete ( String datastoreName , String diskPath ) {

		logger.info( "datastoreName: {}, diskPath: {}, diskSize: {}, diskType:{} ",
				datastoreName, diskPath ) ;
		ObjectNode result = jsonMapper.createObjectNode( ) ;

		String findOutput = runFindCommand( diskPath ) ;

		if ( findOutput.contains( "matches" ) ) {

			var resultMessage = CsapApplication.header( "Delete Disk Aborted, found device mounted on hosts: " )
					+ findOutput ;

			result.put( C7.response_plain_text.val( ), resultMessage ) ;

			return result ;

		}

		List<String> deleteCommands = List.of(
				"govc",
				"datastore.rm",
				"-ds", datastoreName,
				diskPath ) ;

		String deleteDiskOutput = govcRun( deleteCommands ) ;

		var resultMessage = CsapApplication.header( "Delete Disk: " ) + deleteDiskOutput ;

		result.put( C7.response_plain_text.val( ), resultMessage ) ;

		return result ;

	}

	public ObjectNode datastore_files_recurse ( String datastoreName ) {

		List<String> lsCommand = csapApis.osManager( ).getOsCommands( ).getGovcDatastoreRecurse( datastoreName ) ;

		String lsOutput = govcRun( lsCommand ) ;

		lsOutput = csapApis.application( ).check_for_stub( lsOutput, "govc/list-datastore-recursive.json" ) ;
		logger.debug( "command: {}, \n\t output: \n {}", lsCommand, lsOutput ) ;

		var fileListing = jsonMapper.createObjectNode( ) ;
		fileListing.put( "csapNoSort", true ) ;
		JsonNode dataListing = jsonMapper.createObjectNode( ) ;

		try {

			dataListing = jsonMapper.readTree( lsOutput ) ;

		} catch ( IOException e ) {

			var reason = CSAP.buildCsapStack( e ) ;
			logger.warn( "Failed reading {} {}", datastoreName, reason ) ;
			fileListing.put( C7.error.val( ), "failed api request" ) ;
			fileListing.put( C7.errorReason.val( ), reason ) ;

		}

		if ( dataListing.isArray( ) ) {

			// var pvcs = fileListing.putArray( "kubernetes" ) ;
			CSAP.jsonStream( dataListing )
					.filter( dsFolder -> dsFolder.path( "FolderPath" ).asText( ).contains( "kube" ) )
					.filter( dsFolder -> dsFolder.path( "File" ).isArray( ) )
					.forEach( dsFolder -> {

						// pvcs.add( ) ;
						var folderNames = dsFolder.path( "FolderPath" ).asText( ).split( " " ) ;
						var folderName = folderNames[folderNames.length - 1].replaceAll( "/", "_" ) ;
						var kubeFolder = fileListing.putArray( folderName ) ;

						CSAP.jsonStream( dsFolder.path( "File" ) )
								.forEach( dsFile -> {

									var pvc = kubeFolder.addObject( ) ;
									var size = CsapConstants.getDiskWithUnit( dsFile.path( "FileSize" ).asLong( ) ) ;
									pvc.put( "name",
											dsFile.path( "Path" ).asText( )
													+ ", " + size ) ;

									pvc.put( "size", size ) ;

								} ) ;

					} ) ;
			// CSAP.jsonStream( dataListing )
			// .filter( dsFolder -> dsFolder.path( "FolderPath" ).asText().contains( "kube"
			// ) )
			// .filter( dsFolder -> dsFolder.has( "File" ) )
			// .flatMap( dsFolder -> CSAP.jsonStream( dsFolder.path( "File" ) ) )
			// .forEach( dsFile -> {
			// // pvcs.add( ) ;
			// var pvc = pvcs.addObject() ;
			// var size = CsapCore.getDiskWithUnit( dsFile.path( "FileSize" ).asLong() ) ;
			// pvc.put( "name",
			// dsFile.path( "Path" ).asText()
			// + ", " + size ) ;
			//
			// pvc.put( "size", size ) ;
			//
			// } ) ;

			var vmdks = fileListing.putArray( "vmdks" ) ;
			CSAP.jsonStream( dataListing )
					.filter( dsFolder -> dsFolder.has( "File" ) )
					.flatMap( dsFolder -> CSAP.jsonStream( dsFolder.path( "File" ) ) )
					.filter( dsFile -> dsFile.has( "Path" ) && dsFile.path( "Path" ).asText( ).endsWith( ".vmdk" ) )
					.forEach( dsFile -> {

						var vmdk = vmdks.addObject( ) ;
						var size = CsapConstants.getDiskWithUnit( dsFile.path( "FileSize" ).asLong( ) ) ;
						vmdk.put( "name",
								dsFile.path( "Path" ).asText( )
										+ ", " + size ) ;

						vmdk.put( "size", size ) ;

					} ) ;

			// add name for ui browser
			CSAP.jsonStream( dataListing )
					.filter( dsFolder -> dsFolder.has( "FolderPath" ) )
					.forEach( dsFolder -> {

						var names = dsFolder.path( "FolderPath" ).asText( ).split( " " ) ;
						( (ObjectNode) dsFolder ).put( "name", names[names.length - 1] ) ;

					} ) ;

			CSAP.jsonStream( dataListing )
					.filter( dsFolder -> dsFolder.has( "File" ) )
					.flatMap( dsFolder -> CSAP.jsonStream( dsFolder.path( "File" ) ) )
					.filter( dsFile -> dsFile.has( "Path" ) )
					.forEach( dsFile -> {

						var size = CsapConstants.getDiskWithUnit( dsFile.path( "FileSize" ).asLong( ) ) ;
						( (ObjectNode) dsFile ).put( "name",
								dsFile.path( "Path" ).asText( )
										+ ", " + size ) ;

					} ) ;

			( (ObjectNode) fileListing ).put( "command", lsCommand.toString( ) ) ;
			fileListing.set( "details", dataListing ) ;

		}

		return fileListing ;

	}

	public ArrayNode vm_find ( String vmFilter ) {

		ArrayNode vms = jsonMapper.createArrayNode( ) ;

		logger.debug( "Entered " ) ;

		List<String> govcFindVms = csapApis.osManager( ).getOsCommands( ).getGovcVmFind( vmFilter ) ;

		if ( csapApis.application( ).isJunit( ) ) {

			vms.add( govcFindVms.toString( ) ) ;

		}

		String scriptOutput = "Failed to run" ;

		scriptOutput = govcRun( govcFindVms ) ;

		logger.debug( "scriptOutput: {}", scriptOutput ) ;

		scriptOutput = csapApis.application( ).check_for_stub( scriptOutput, "govc/vm-find.txt" ) ;
		String[] serviceLines = scriptOutput.split( OsManager.LINE_SEPARATOR ) ;

		Arrays.stream( serviceLines )
				.filter( StringUtils::isNotEmpty )
				.map( CsapConstants::singleSpace )
				.filter( line -> ! line.startsWith( "#" ) )
				.forEach( line -> {

					vms.add( line ) ;

				} ) ;

		return vms ;

	}

	public ArrayNode vm_list ( String path ) {

		ArrayNode vmListing = jsonMapper.createArrayNode( ) ;

		List<String> govcListVmFolders = csapApis.osManager( ).getOsCommands( ).getGovcVmList( path ) ;

		if ( csapApis.application( ).isJunit( ) ) {

			vmListing.add( govcListVmFolders.toString( ) ) ;

		}

		String govcListOutput = govcRun( govcListVmFolders ) ;
		govcListOutput = csapApis.application( ).check_for_stub( govcListOutput, "govc/vm-list.txt" ) ;

		logger.debug( "govcListVmFolders: {}, \n\t govcListOutput: \n {}", govcListVmFolders, govcListOutput ) ;

		String[] serviceLines = govcListOutput.split( OsManager.LINE_SEPARATOR ) ;

		Arrays.stream( serviceLines )
				.filter( StringUtils::isNotEmpty )
				.map( CsapConstants::singleSpace )
				.filter( line -> ! line.startsWith( "#" ) )
				.forEach( line -> {

					vmListing.add( line ) ;

				} ) ;

		return vmListing ;

	}

	public ObjectNode vmInfo ( String vmPath ) {

		ObjectNode vm = jsonMapper.createObjectNode( ) ;
		vm.put( "csapNoSort", true ) ;

		List<String> vmInfoCommand = csapApis.osManager( ).getOsCommands( ).getGovcVmInfo( vmPath ) ;

		String vmInfoOutput = govcRun( vmInfoCommand ) ;

		logger.debug( "vmInfoCommand: {}, \n\t vmInfoOutput: \n {}", vmInfoCommand, vmInfoOutput ) ;

		vmInfoOutput = csapApis.application( ).check_for_stub( vmInfoOutput, "govc/vm-info.json" ) ;

		try {

			var govcInfo = jsonMapper.readTree( vmInfoOutput ) ;
			var vmDetails = govcInfo.at( "/VirtualMachines/0" ) ;

			vm.put( "status", vmDetails.at( "/Guest/GuestState" ).asText( "-" )
					+ ",   status: "
					+ vmDetails.at( "/OverallStatus" ).asText( "-" ) ) ;

			vm.put( "resources", "cores: "
					+ vmDetails.at( "/Config/Hardware/NumCPU" ).asText( "-" )
					+ ",   memory: "
					+ vmDetails.at( "/Config/Hardware/MemoryMB" ).asLong( ) / 1024 + "Gb" ) ;

			vm.put( "Ip", vmDetails.at( "/Guest/IpAddress" ).asText( "-" ) ) ;
			vm.put( "OS", vmDetails.at( "/Guest/GuestFullName" ).asText( "-" ) ) ;

			var extraConfig = vmDetails.at( "/Config/ExtraConfig" ) ;
			var uuid = false ;

			if ( extraConfig.isArray( ) ) {

				var uidSetting = CSAP.jsonStream( extraConfig )
						.filter( config -> config.path( "Key" ).asText( ).equals( "disk.enableUUID" ) )
						.map( config -> config.path( "Value" ).asText( ).equalsIgnoreCase( "true" ) )
						.findFirst( ) ;

				if ( uidSetting.isPresent( ) ) {

					uuid = uidSetting.get( ) ;

				}

			}

			vm.put( "disk-uuid-enabled", uuid ) ;

			var diskInfo = vmDetails.at( "/LayoutEx/File" ) ;

			if ( diskInfo.isArray( ) ) {

				var diskArray = vm.putArray( "disks" ) ;
				CSAP.jsonStream( diskInfo ).forEach( diskDetails -> {

					var detail = diskArray.addObject( ) ;

					long size = diskDetails.path( "Size" ).asLong( ) ;
					var sizeWithUnits = CsapConstants.getDiskWithUnit( size ) ;

					var fullName = diskDetails.path( "Name" ).asText( ) ;
					var shorterName = fullName.split( " " ) ;
					detail.put( "name", ( new File( shorterName[shorterName.length - 1] ) ).getName( ) + ","
							+ sizeWithUnits ) ;
					detail.put( "path", fullName ) ;
					detail.put( "size", sizeWithUnits ) ;
					detail.put( "type", diskDetails.path( "Type" ).asText( ) ) ;

				} ) ;
				;

			}

			( (ObjectNode) vmDetails ).put( "command", vmInfoCommand.toString( ) ) ;
			vm.set( "details", vmDetails ) ;

		} catch ( Exception e ) {

			var reason = CSAP.buildCsapStack( e ) ;
			logger.warn( "Failed reading {} {}", vmPath, reason ) ;
			vm.put( C7.error.val( ), "failed api request" ) ;
			vm.put( C7.errorReason.val( ), reason ) ;

		}

		return vm ;

	}

}
