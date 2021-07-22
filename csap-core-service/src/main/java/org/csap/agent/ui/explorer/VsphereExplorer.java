package org.csap.agent.ui.explorer ;

import java.io.File ;
import java.util.Map ;

import javax.inject.Inject ;

import org.apache.commons.lang3.StringUtils ;
import org.csap.agent.CsapCore ;
import org.csap.agent.container.DockerJson ;
import org.csap.agent.integrations.CsapEvents ;
import org.csap.agent.integrations.Vsphere ;
import org.csap.agent.model.Application ;
import org.csap.helpers.CSAP ;
import org.csap.security.config.CsapSecuritySettings ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.beans.factory.annotation.Autowired ;
import org.springframework.web.bind.annotation.GetMapping ;
import org.springframework.web.bind.annotation.PostMapping ;
import org.springframework.web.bind.annotation.RequestMapping ;
import org.springframework.web.bind.annotation.RequestParam ;
import org.springframework.web.bind.annotation.RestController ;

import com.fasterxml.jackson.databind.JsonNode ;
import com.fasterxml.jackson.databind.ObjectMapper ;
import com.fasterxml.jackson.databind.node.ArrayNode ;
import com.fasterxml.jackson.databind.node.ObjectNode ;

@RestController
@RequestMapping ( DockerExplorer.EXPLORER_URL + "/vsphere" )
public class VsphereExplorer {

	final Logger logger = LoggerFactory.getLogger( this.getClass( ) ) ;

	private static final String VIRTUAL_MACHINE_TOKEN = " (VirtualMachine)" ;
	private Application csapApp ;
	private CsapEvents csapEvents ;
	private ObjectMapper jsonMapper ;
	private Vsphere vsphere ;

	@Autowired ( required = false )
	CsapSecuritySettings securitySettings ;

	@Inject
	public VsphereExplorer (
			Vsphere vsphere,
			Application csapApp,
			CsapEvents csapEventClient,
			ObjectMapper jsonMapper ) {

		this.vsphere = vsphere ;
		this.csapApp = csapApp ;
		this.csapEvents = csapEventClient ;
		this.jsonMapper = jsonMapper ;

	}

	Map<String, String> categories = Map.of(
			"datastores", "vsphere datastores",
			"vms", "vsphere virtual machines" ) ;

	@GetMapping
	public JsonNode vsphere_listing ( ) {

		if ( ! csapApp.rootProjectEnvSettings( ).isVsphereConfigured( ) )
			return build_not_configured_listing( ) ;

		ArrayNode dataStoreListing = jsonMapper.createArrayNode( ) ;

		categories.entrySet( ).stream( )
				.forEach( categoryEntry -> {

					ObjectNode item = dataStoreListing.addObject( ) ;
					item.put( "label", categoryEntry.getKey( ) ) ;
					item.put( "description", categoryEntry.getValue( ) ) ;
					item.put( "folder", true ) ;
					item.put( "lazy", true ) ;

					// triggers cache invalidation
					var attributes = item.putObject( "attributes" ) ;
					attributes.put( DockerJson.list_folderUrl.json( ), "vsphere/" + categoryEntry.getKey( ) ) ;

				} ) ;

		return dataStoreListing ;

	}

	@GetMapping ( "/datastores" )
	public JsonNode datastores_listing ( @RequestParam ( defaultValue = "false" ) boolean configurationFilter ) {

		if ( ! csapApp.rootProjectEnvSettings( ).isVsphereConfigured( ) )
			return build_not_configured_listing( ) ;

		ArrayNode dataStoreListing = jsonMapper.createArrayNode( ) ;

		ArrayNode datastores = vsphere.dataStores( configurationFilter ) ;

		if ( datastores.size( ) == 0 ) {

			ObjectNode msg = dataStoreListing.addObject( ) ;
			msg.put( DockerJson.error.json( ), "No vsphere datastores found" ) ;

		} else {

			CSAP.jsonStream( datastores )
					.forEach( datastore -> {

						ObjectNode item = dataStoreListing.addObject( ) ;
						item.put( "label", datastore.path( "name" ).asText( ) + " (" + datastore.path( "name" )
								.asText( ) + ")" ) ;
						item.put( "description",
								"available: " + datastore.path( "free" ).asText( "-" ) + " of " + datastore.path(
										"capacity" ).asText( "-" ) ) ;
						item.put( "folder", true ) ;
						item.put( "lazy", true ) ;

						var attributes = item.putObject( "attributes" ) ;
						attributes.put( DockerJson.list_folderUrl.json( ), "vsphere/datastore" ) ;

						var navPath = datastore.path( "path" ).asText( "-" ) ;
						attributes.put( "path", navPath ) ;

					} ) ;

		}

		return dataStoreListing ;

	}

	@GetMapping ( "/datastore" )
	public ObjectNode datastore_info ( String path ) {

		ObjectNode datastore = vsphere.dataStore_info( path ) ;

		if ( datastore == null ) {

			ObjectNode msg = jsonMapper.createObjectNode( ) ;
			msg.put( DockerJson.error.json( ), "Not found: " + path ) ;

		}

		return datastore ;

	}

	@GetMapping ( "/datastore/files" )
	public ArrayNode datastore_files ( String path ) {

		var paths = path.split( ":", 2 ) ;

		var datastoreName = paths[0] ;
		var datastorePath = "" ;

		if ( paths.length == 2 ) {

			datastorePath = paths[1] ;

		}

		ArrayNode datastore_files = vsphere.datastore_files( datastoreName, datastorePath ) ;
		ArrayNode datastoreListing = jsonMapper.createArrayNode( ) ;

		if ( datastore_files.size( ) == 0 ) {

			ObjectNode msg = datastoreListing.addObject( ) ;
			msg.put( DockerJson.error.json( ), "Empty folder" ) ;

		} else {

			CSAP.jsonStream( datastore_files )
					.forEach( datastoreFile -> {

						ObjectNode item = datastoreListing.addObject( ) ;

						var itemPath = datastoreFile.path( "Path" ).asText( ) ;

						if ( datastoreFile.path( "FileSize" ).asLong( ) == 4096 ) {

							item.put( "label", itemPath ) ;
							item.put( "description", "folder" ) ;
							item.put( "folder", true ) ;
							item.put( "lazy", true ) ;

							var attributes = item.putObject( "attributes" ) ;

							var sourceUri = "vsphere/datastore/files" ;
							attributes.put( DockerJson.list_folderUrl.json( ), sourceUri ) ;
							attributes.put( "path", path + itemPath + "/" ) ;

						} else {

							( (ObjectNode) datastoreFile ).put( "isFile", true ) ;

							var size = CsapCore.getDiskWithUnit( datastoreFile.path( "FileSize" ).asLong( ) ) ;
							item.put( "label", itemPath + ", Size: " + size ) ;
							item.put( "description", "" ) ;
							item.put( "folder", true ) ;
							item.put( "lazy", true ) ;
							item.set( "attributes", datastoreFile ) ;

						}

					} ) ;

		}

		return datastoreListing ;

	}

	private ArrayNode build_not_configured_listing ( ) {

		ArrayNode listing = jsonMapper.createArrayNode( ) ;
		ObjectNode item = listing.addObject( ) ;
		item.put( DockerJson.list_label.json( ), DockerJson.error.json( ) + "Vsphere integration is not enabled" ) ;
		item.put( "folder", false ) ;
		item.put( DockerJson.error.json( ), "Vsphere integration is not enabled" ) ;
		return listing ;

	}

	@PostMapping ( "/datastore/files" )
	public ObjectNode datastore_files_add (
											String datastoreName ,
											String diskPath ,
											String diskSize ,
											String diskType ,
											String operation )
		throws Exception {

		issueAudit( "Adding Disk: " + datastoreName + ":" + diskPath, " diskSize: " + diskSize + " diskType"
				+ diskType ) ;

		if ( operation.equals( "add" ) ) {

			return vsphere.datastore_file_add( datastoreName, diskPath, diskSize, diskType ) ;

		} else if ( operation.equals( "delete" ) ) {

			return vsphere.datastore_file_delete( datastoreName, diskPath ) ;

		}

		return vsphere.datastore_file_find( diskPath ) ;

	}

	@GetMapping ( "/vms" )
	public JsonNode vm_listing (
									@RequestParam ( defaultValue = "false" ) boolean configurationFilter ,
									@RequestParam ( defaultValue = "" ) String path ) {

		if ( ! csapApp.environmentSettings( ).isVsphereConfigured( ) )
			return build_not_configured_listing( ) ;

		// do a find using either specified path or defaults
		var vsphereConfiguration = csapApp.environmentSettings( ).getVsphereConfiguration( ) ;
		var environmentVariables = csapApp.environmentSettings( ).getVsphereEnv( ) ;
		var findExpression = vsphereConfiguration.at( "/filters/vm-path" ) ;

		var baseVmPath = "/" + environmentVariables.get( "GOVC_DATACENTER" ) + "/vm" ;

		// find will take too long if no filter is specified
		if ( ( configurationFilter && ! findExpression.isMissingNode( ) )
				|| StringUtils.isNotEmpty( path ) ) {

			var targetPath = path ;

			if ( StringUtils.isEmpty( targetPath ) ) {

				targetPath = baseVmPath + "/" + findExpression.asText( "" ) ;

			}

			return findVms( targetPath, findExpression ) ;

		} else {

			return vms_browse( baseVmPath ) ;

		}

	}

	@GetMapping ( "/vms/browse" )
	public JsonNode vms_browse ( @RequestParam ( defaultValue = "" ) String path ) {

		// return null ;
		// }
		//
		// private JsonNode listVmFolders ( String path ) {
		ArrayNode vmFolders = vsphere.vm_list( path ) ;

		ArrayNode vmListing = jsonMapper.createArrayNode( ) ;

		if ( vmFolders.size( ) == 0 ) {

			ObjectNode msg = vmListing.addObject( ) ;
			msg.put( DockerJson.error.json( ), "No vsphere vms found" ) ;

		} else {

			CSAP.jsonStream( vmFolders )
					.forEach( vmPath -> {

						ObjectNode item = vmListing.addObject( ) ;

						var listingPath = vmPath.asText( ) ;

						var sourceUri = "vsphere/vms/browse" ;
						var type = "folder" ;

						if ( listingPath.endsWith( VIRTUAL_MACHINE_TOKEN ) ) {

							sourceUri = "vsphere/vm" ;
							type = "Virtual Machine" ;
							listingPath = listingPath.substring( 0, listingPath.indexOf( VIRTUAL_MACHINE_TOKEN ) ) ;

						}

						var vmFile = new File( listingPath ) ;

						item.put( "label", vmFile.getName( ) ) ;
						item.put( "description", type ) ;
						item.put( "folder", true ) ;
						item.put( "lazy", true ) ;

						var attributes = item.putObject( "attributes" ) ;
						attributes.put( DockerJson.list_folderUrl.json( ), sourceUri ) ;
						attributes.put( "path", listingPath ) ;

					} ) ;

		}

		return vmListing ;

	}

	private JsonNode findVms ( String filterPath , JsonNode findExpression ) {

		ArrayNode vms = vsphere.vm_find( filterPath ) ;

		ArrayNode vmListing = jsonMapper.createArrayNode( ) ;

		if ( vms.size( ) == 0 ) {

			ObjectNode msg = vmListing.addObject( ) ;
			msg.put( DockerJson.error.json( ), "No vsphere vms found" ) ;

		} else {

			CSAP.jsonStream( vms )
					.forEach( vmPath -> {

						ObjectNode item = vmListing.addObject( ) ;

						var vmFile = new File( vmPath.asText( ) ) ;

						item.put( "label", vmFile.getName( ) ) ;
						item.put( "description", "" ) ;
						item.put( "folder", true ) ;
						item.put( "lazy", true ) ;

						var attributes = item.putObject( "attributes" ) ;
						attributes.put( DockerJson.list_folderUrl.json( ), "vsphere/vm" ) ;
						attributes.put( "path", vmPath.asText( ) ) ;

					} ) ;

		}

		return vmListing ;

	}

	@GetMapping ( "/vm" )
	public ObjectNode vm_info ( String path ) {

		ObjectNode vm = vsphere.vmInfo( path ) ;

		if ( vm == null ) {

			ObjectNode msg = jsonMapper.createObjectNode( ) ;
			msg.put( DockerJson.error.json( ), "Not found: " + path ) ;

		}

		return vm ;

	}

	private void issueAudit ( String commandDesc , String details ) {

		csapEvents.publishUserEvent( "vsphere",
				securitySettings.getRoles( ).getUserIdFromContext( ),
				commandDesc, details ) ;

	}
}
