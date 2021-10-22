package org.csap.agent.ui.explorer ;

import java.util.List ;

import javax.inject.Inject ;
import javax.servlet.http.HttpSession ;

import org.apache.commons.lang3.StringUtils ;
import org.csap.agent.container.DockerJson ;
import org.csap.agent.integrations.CsapEvents ;
import org.csap.agent.model.Application ;
import org.csap.agent.services.OsManager ;
import org.csap.helpers.CSAP ;
import org.csap.security.config.CsapSecurityRoles ;
import org.csap.security.config.CsapSecuritySettings ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.beans.factory.annotation.Autowired ;
import org.springframework.ui.ModelMap ;
import org.springframework.web.bind.annotation.DeleteMapping ;
import org.springframework.web.bind.annotation.GetMapping ;
import org.springframework.web.bind.annotation.PathVariable ;
import org.springframework.web.bind.annotation.PostMapping ;
import org.springframework.web.bind.annotation.RequestMapping ;
import org.springframework.web.bind.annotation.RequestParam ;
import org.springframework.web.bind.annotation.RestController ;

import com.fasterxml.jackson.databind.JsonNode ;
import com.fasterxml.jackson.databind.ObjectMapper ;
import com.fasterxml.jackson.databind.node.ArrayNode ;
import com.fasterxml.jackson.databind.node.ObjectNode ;

@RestController
@RequestMapping ( OsExplorer.EXPLORER_URL + "/os" )
public class OsExplorer {

	final Logger logger = LoggerFactory.getLogger( this.getClass( ) ) ;
	ObjectMapper jacksonMapper = new ObjectMapper( ) ;

	public final static String EXPLORER_URL = "/explorer" ;

	@Inject
	public OsExplorer (
			Application csapApp,
			OsManager osManager,
			CsapEvents csapEventClient ) {

		this.csapApp = csapApp ;
		this.osManager = osManager ;
		this.csapEventClient = csapEventClient ;

	}

	Application csapApp ;
	OsManager osManager ;

	@Autowired ( required = false )
	CsapSecuritySettings securitySettings ;

	CsapEvents csapEventClient ;

	@GetMapping ( "/memory" )
	public JsonNode memory ( )
		throws Exception {

		return osManager.getCachedMemoryMetrics( ) ;

	}

	@GetMapping ( "/network/devices" )
	public JsonNode networkDevices ( )
		throws Exception {

		ArrayNode networkListing = jacksonMapper.createArrayNode( ) ;

		osManager
				.networkInterfaces( ).stream( )
				.forEach( interfaceLine -> {

					String[] interfaceFields = interfaceLine.split( " ", 3 ) ;
					ObjectNode item = networkListing.addObject( ) ;
					var state = "" ;
					var stateSplit = interfaceFields[ 2 ].split( "state", 2 ) ;

					if ( stateSplit.length == 2 ) {

						state = stateSplit[ 1 ].trim( ).split( " ", 2 )[ 0 ] ;

					}

					var inetSplit = interfaceFields[ 2 ].split( "inet", 2 ) ;

					if ( inetSplit.length == 2 ) {

						state = inetSplit[ 1 ].trim( ).split( " ", 2 )[ 0 ] + ":" + state ;

					}

					var index = interfaceFields[ 0 ] ;

					if ( index.length( ) == 2 ) {

						index = "0" + index ;

					}

					item.put( "label", index + " " + interfaceFields[ 1 ] + " (" + state + ")" ) ;
					item.put( "description", interfaceFields[ 2 ] ) ;
					item.put( "folder", false ) ;
					item.put( "lazy", false ) ;

				} ) ;

		return networkListing ;

	}

	@GetMapping ( "/systemctl" )
	public ObjectNode systemctl (
									ModelMap modelMap ,
									HttpSession session ) {

		var report = jacksonMapper.createObjectNode( ) ;

		String commandOutput = osManager.systemStatus( ) ;

		report.put( DockerJson.response_plain_text.json( ), commandOutput ) ;
		return report ;

	}

	@PostMapping ( "/cli" )
	public ObjectNode cli ( String parameters , HttpSession session ) {

		var report = jacksonMapper.createObjectNode( ) ;

		if ( StringUtils.isEmpty( parameters ) ) {

			parameters = "print_section 'no command specified'" ;

		}

		parameters = parameters.replaceAll( "calicoctl", "calico" ) ;

		String commandOutput ;

		if ( ! securitySettings.getRoles( ).getAndStoreUserRoles( session )
				.contains( CsapSecurityRoles.ADMIN_ROLE ) ) {

			commandOutput = "*Permission denied: only admins may access journal entries" ;

		} else {

			issueAudit( "running cli: " + parameters, null ) ;
			commandOutput = osManager.cli( parameters ) ;

		}

		report.put( DockerJson.response_yaml.json( ), commandOutput ) ;
		return report ;

	}

	@GetMapping ( "/disk" )
	public JsonNode disk ( )
		throws Exception {

		return osManager.getCachedFileSystemInfo( ) ;

	}

	@GetMapping ( "/socket/connections" )
	public JsonNode socketConnections ( @RequestParam ( defaultValue = "true" ) boolean summaryReport )
		throws Exception {

		ArrayNode portListing = jacksonMapper.createArrayNode( ) ;

		CSAP.jsonStream( osManager.socketConnections( summaryReport ) )
				.forEach( portAttributes -> {

					// //String[] interfaceFields = interfaceLine.split( " ", 4 ) ;
					ObjectNode item = portListing.addObject( ) ;

					var label = portAttributes.path( "processName" ).asText( ) + ": " + portAttributes.path( "port" )
							.asText( ) ;

					var description = portAttributes.path( "peer" ).asText( ) ;
					var relatedItems = portAttributes.path( "related" ) ;

					if ( relatedItems.isArray( ) ) {

						description += "<span class='more-items'>" + relatedItems.size( ) + "</span> related items" ;

					}

					item.put( "label", label ) ;
					item.put( "description", description ) ;
					item.put( "folder", false ) ;
					item.put( "lazy", false ) ;
					item.set( "attributes", portAttributes ) ;

				} ) ;

		return portListing ;

	}

	@GetMapping ( "/socket/listeners" )
	public JsonNode socketListeners ( @RequestParam ( defaultValue = "true" ) boolean summaryReport )
		throws Exception {

		ArrayNode portListing = jacksonMapper.createArrayNode( ) ;

		CSAP.jsonStream( osManager.socketListeners( summaryReport ) )
				.forEach( portAttributes -> {

					// //String[] interfaceFields = interfaceLine.split( " ", 4 ) ;
					ObjectNode item = portListing.addObject( ) ;
					var portId = portAttributes.path( "port" ).asText( ) ;

					var processInfo = portAttributes.path( "details" ).asText( ) ;
					var users = processInfo.split( "\"", 3 ) ;

					if ( users.length == 3 ) {

						processInfo = users[ 1 ] ;

					}

					var relatedItems = portAttributes.path( "related" ) ;
					var desc = "" ;

					if ( relatedItems.isArray( ) ) {

						desc = "<span class='more-items'>" + relatedItems.size( ) + "</span> related items" ;

					}

					item.put( "label", processInfo + ": " + portId ) ;
					item.put( "description", desc ) ;
					item.put( "folder", false ) ;
					item.put( "lazy", false ) ;
					item.set( "attributes", portAttributes ) ;

				} ) ;

		return portListing ;

	}

	@GetMapping ( "/cpu" )
	public JsonNode cpu ( )
		throws Exception {

		return osManager.buildServiceStatsReportAndUpdateTopCpu( true ).get( "mp" ) ;

	}

	@GetMapping ( "/csap/services" )
	public ArrayNode servicesCsap ( )
		throws Exception {

		ArrayNode serviceListing = jacksonMapper.createArrayNode( ) ;

		// do a listing
		ObjectNode serviceMetricsJson = osManager.buildServiceStatsReportAndUpdateTopCpu( true ) ;

		JsonNode processItems = serviceMetricsJson.get( "ps" ) ;

		processItems.fieldNames( ).forEachRemaining( name -> {

			JsonNode processAttributes = processItems.get( name ) ;
			ObjectNode item = serviceListing.addObject( ) ;
			item.put( "label", name ) ;
			item.set( "attributes", processAttributes ) ;
			item.put( "folder", true ) ;
			item.put( "lazy", true ) ;

		} ) ;

		return serviceListing ;

	}

	@GetMapping ( "/csap/definition" )
	public ArrayNode csapDefinition ( )
		throws Exception {

		ArrayNode result = jacksonMapper.createArrayNode( ) ;

		// do a listing
		JsonNode activeDefinition = csapApp.getActiveProject( ).getSourceDefinition( ) ;

		activeDefinition.fieldNames( ).forEachRemaining( name -> {

			JsonNode processAttributes = activeDefinition.get( name ) ;
			ObjectNode item = result.addObject( ) ;
			item.put( "label", name ) ;
			item.set( "attributes", processAttributes ) ;
			item.put( "folder", true ) ;
			item.put( "lazy", true ) ;

		} ) ;

		return result ;

	}

	@GetMapping ( "/packages/linux" )
	public ArrayNode packagesLinux ( )
		throws Exception {

		ArrayNode result = jacksonMapper.createArrayNode( ) ;

		List<String> packages = osManager.getLinuxPackages( ) ;

		packages.stream( ).forEach( name -> {

			// JsonNode processAttributes = processItems.get( name );
			ObjectNode item = result.addObject( ) ;
			item.put( "label", name ) ;

			// item.set( "attributes", processAttributes );

			item.put( "folder", false ) ;
			item.put( "lazy", false ) ;

		} ) ;

		return result ;

	}

	final static String REPLACE_SPACES = "\\s+" ;

	@RequestMapping ( "/packages/linux/info" )
	public ObjectNode packagesLinuxInfo ( String name )
		throws Exception {

		String info = osManager.getLinuxPackageInfo( name ) ;

		String url = "" ;
		StringBuilder description = new StringBuilder( ) ;
		boolean isDescription = false ;

		for ( String line : info.split( "\n" ) ) {

			if ( isDescription ) {

				description.append( line ) ;
				description.append( "\n" ) ;

			} else {

				String[] words = line
						.replaceAll( REPLACE_SPACES, " " )
						.split( " " ) ;

				switch ( words[ 0 ] ) {

				case "URL":
					url = words[ 2 ] ;
					break ;

				case "Description":
					isDescription = true ;
					break ;

				}

			}

		}

		ObjectNode result = jacksonMapper.createObjectNode( ) ;

		result.put( "result", "name: " + name ) ;
		result.put( "url", url ) ;
		result.put( "description", description.toString( ) ) ;
		result.put( "details", info ) ;

		return result ;

	}

	@RequestMapping ( "/services/linux" )
	public ArrayNode servicesLinux ( )
		throws Exception {

		ArrayNode result = jacksonMapper.createArrayNode( ) ;

		List<String> services = osManager.getLinuxServices( ) ;

		services.stream( ).forEach( name -> {

			// JsonNode processAttributes = processItems.get( name );
			ObjectNode item = result.addObject( ) ;
			item.put( "label", name ) ;

			// item.set( "attributes", processAttributes );

			item.put( "folder", false ) ;
			item.put( "lazy", false ) ;

		} ) ;

		return result ;

	}

	@GetMapping ( "/services/linux/info" )
	public JsonNode servicesLinuxStatus ( String name )
		throws Exception {

		ObjectNode result = jacksonMapper.createObjectNode( ) ;

		String info = osManager.getLinuxServiceStatus( name ) ;
		result.put( "result", "name: " + name ) ;
		result.put( "description", info ) ;
		result.put( "details", info ) ;

		return result ;

	}

	@GetMapping ( "/services/linux/logs" )
	public JsonNode servicesLinuxLogs ( String name )
		throws Exception {

		ObjectNode result = jacksonMapper.createObjectNode( ) ;

		result.put( "result", "name: " + name ) ;
		result.put( "plainText", osManager.getJournal( name, "", "500", false, false ) ) ;

		return result ;

	}

	@DeleteMapping ( "/processes/{pid}/{signal}" )
	public ObjectNode processKill (
									@PathVariable int pid ,
									@PathVariable String signal )
		throws Exception {

		issueAudit( "Killing pid: " + pid + " signal: " + signal, null ) ;

		return osManager.killProcess( pid, signal ) ;

	}

	private void issueAudit ( String commandDesc , String details ) {

		csapEventClient.publishUserEvent( "osExplorer",
				securitySettings.getRoles( ).getUserIdFromContext( ),
				commandDesc, details ) ;

	}

	@GetMapping ( "/processes" )
	public ArrayNode processesList ( )
		throws Exception {

		ArrayNode processesGroupedByCommandPath = jacksonMapper.createArrayNode( ) ;

		osManager.checkForProcessStatusUpdate( ) ;

		ArrayNode processStatusItems = osManager.processStatus( ) ;
		ObjectNode processKeys = jacksonMapper.createObjectNode( ) ;

		processStatusItems.forEach( processAttributes -> {

			String processCommandPath = "" ;
			String[] params = processAttributes.get( "parameters" ).asText( ).split( " " ) ;

			if ( params.length >= 0 ) {

				processCommandPath = params[ 0 ] ;

				if ( processCommandPath.startsWith( "[kworker" ) ) {

					processCommandPath = "system: kernel workers" ;

				} else if ( processCommandPath.startsWith( "[scsi" ) ) {

					processCommandPath = "system: scsi" ;

				} else if ( processCommandPath.startsWith( "[watchdog" ) ) {

					processCommandPath = "system: watchdogs" ;

				} else if ( processCommandPath.startsWith( "[xfs" ) ) {

					processCommandPath = "system: xfs" ;

				} else if ( processCommandPath.startsWith( "[" ) ) {

					processCommandPath = "system: miscellaneous" ;

				}

			}

			if ( processCommandPath.trim( ).length( ) == 0 ) {

				// every process should have a command path - but just in case a
				// parising error.
				processCommandPath += "Pid: " + processAttributes.get( "pid" ).asText( ) ;

			}

			if ( ! processKeys.has( processCommandPath ) ) {

				ObjectNode keyItem = processesGroupedByCommandPath.addObject( ) ;

				processKeys.set( processCommandPath, keyItem ) ;
				keyItem.put( "label", processCommandPath ) ;

				keyItem.putArray( "attributes" ) ;
				keyItem.put( "folder", true ) ;
				keyItem.put( "lazy", true ) ;

			}

			ArrayNode keyList = (ArrayNode) processKeys.get( processCommandPath ).get( "attributes" ) ;
			keyList.add( processAttributes ) ;

		} ) ;

		return processesGroupedByCommandPath ;

	}
}
