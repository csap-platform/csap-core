package org.csap.agent.ui.explorer ;

import java.util.Optional ;

import javax.inject.Inject ;
import javax.servlet.http.HttpServletResponse ;

import org.csap.agent.container.ContainerIntegration ;
import org.csap.agent.container.DockerJson ;
import org.csap.agent.integrations.CsapEvents ;
import org.csap.agent.model.Application ;
import org.csap.agent.services.OsManager ;
import org.csap.helpers.CSAP ;
import org.csap.security.config.CsapSecuritySettings ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.beans.factory.annotation.Autowired ;
import org.springframework.http.MediaType ;
import org.springframework.web.bind.annotation.DeleteMapping ;
import org.springframework.web.bind.annotation.GetMapping ;
import org.springframework.web.bind.annotation.PathVariable ;
import org.springframework.web.bind.annotation.PostMapping ;
import org.springframework.web.bind.annotation.RequestMapping ;
import org.springframework.web.bind.annotation.RequestParam ;
import org.springframework.web.bind.annotation.RestController ;

import com.fasterxml.jackson.annotation.JsonInclude.Include ;
import com.fasterxml.jackson.databind.JsonNode ;
import com.fasterxml.jackson.databind.ObjectMapper ;
import com.fasterxml.jackson.databind.node.ArrayNode ;
import com.fasterxml.jackson.databind.node.ObjectNode ;
import com.github.dockerjava.api.DockerClient ;
import com.github.dockerjava.api.command.InspectContainerResponse ;
import com.github.dockerjava.api.model.Container ;
import com.github.dockerjava.api.model.Info ;

@RestController
@RequestMapping ( ContainerExplorer.EXPLORER_URL + "/docker" )
public class ContainerExplorer {

	final Logger logger = LoggerFactory.getLogger( this.getClass( ) ) ;
	ObjectMapper jacksonMapper = new ObjectMapper( ) ;

	public final static String EXPLORER_URL = "/explorer" ;

	@Inject
	public ContainerExplorer (
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

	// https://github.com/docker-java/docker-java
	@Autowired ( required = false )
	DockerClient dockerClient ;

	@Autowired ( required = false )
	ContainerIntegration dockerIntegration ;

	@GetMapping ( "/configuration" )
	public JsonNode dockerConfiguration ( ) {

		if ( ! csapApp.isDockerInstalledAndActive( ) )
			return build_not_configured_listing( ) ;

		Info info = dockerClient.infoCmd( ).exec( ) ;
		ObjectNode result = jacksonMapper.convertValue( info, ObjectNode.class ) ;

		return result ;

	}

	private ArrayNode build_not_configured_listing ( ) {

		ArrayNode listing = jacksonMapper.createArrayNode( ) ;
		ObjectNode item = listing.addObject( ) ;
		item.put( DockerJson.list_label.json( ), DockerJson.error.json( ) + "Docker not configured" ) ;
		item.put( "folder", false ) ;
		item.put( DockerJson.error.json( ), "Docker not configured" ) ;
		return listing ;

	}

	@GetMapping ( "/networks" )
	public JsonNode networks ( ) {

		if ( ! csapApp.isDockerInstalledAndActive( ) )
			return build_not_configured_listing( ) ;

		ArrayNode listing = dockerIntegration.networkList( ) ;

		if ( listing.size( ) == 0 ) {

			ObjectNode msg = listing.addObject( ) ;
			msg.put( DockerJson.error.json( ), "No networks defined" ) ;

		}

		return listing ;

	}

	@GetMapping ( "/volumes" )
	public JsonNode volumes ( ) {

		if ( ! csapApp.isDockerInstalledAndActive( ) )
			return build_not_configured_listing( ) ;

		ArrayNode listing = dockerIntegration.volumeList( ) ;

		if ( listing.size( ) == 0 ) {

			ObjectNode msg = listing.addObject( ) ;
			msg.put( DockerJson.error.json( ), "No volumes defined" ) ;

		}

		return listing ;

	}

	@GetMapping ( "/images" )
	public JsonNode imagesList (
									boolean showFilteredItems ) {

		if ( ! csapApp.isDockerInstalledAndActive( ) )
			return build_not_configured_listing( ) ;

		return dockerIntegration.imageListWithDetails( showFilteredItems ) ;

	}

	@GetMapping ( "/image/info" )
	public ObjectNode imageInfo (
									String id ,
									String name ) {

		return dockerIntegration.imageInfo( name ) ;

	}

	@GetMapping ( "/image/pull/progress" )
	public String imagePullProgress (
										@RequestParam ( defaultValue = "0" ) int offset ) {

		return dockerIntegration.getLastResults( offset ) ;

	}

	@PostMapping ( "/image/pull" )
	public ObjectNode imagePull (
									String id ,
									String name )
		throws Exception {

		issueAudit( "Pulling Image: " + name, null ) ;

		return dockerIntegration.imagePull( name, null, 600, 1 ) ;

	}

	@PostMapping ( "/image/remove" )
	public ObjectNode imageRemove (
									boolean force ,
									String id ,
									String name )
		throws Exception {

		logger.info( "force: {} ,id: {}, name: {}", force, id, name ) ;

		issueAudit( "Removing Image: " + name, id ) ;

		return dockerIntegration.imageRemove( force, id, name ) ;

	}

	@DeleteMapping ( "/image/clean/{days}/{minutes}" )
	public ObjectNode imageClean (
									@PathVariable int days ,
									@PathVariable int minutes )
		throws Exception {

		logger.info( "Cleaning images older then days: {}, minutes: {}", days, minutes ) ;

		issueAudit( "Cleaning Images older then: " + days + " Days and " + minutes + " minutes", null ) ;

		return osManager.cleanImages( days, minutes ) ;

	}

	@GetMapping ( "/containers" )
	public ArrayNode containersListing ( boolean showFilteredItems )
		throws Exception {

		if ( ! csapApp.isDockerInstalledAndActive( ) ) {

			return build_not_configured_listing( ) ;

		}

		return dockerIntegration.containerListing( showFilteredItems ) ;

	}

	static ObjectMapper nonNullMapper ;
	static {

		nonNullMapper = new ObjectMapper( ) ;
		nonNullMapper.setSerializationInclusion( Include.NON_NULL ) ;

	}

	@GetMapping ( "/container/info" )
	public ObjectNode containerInfo (
										String id ,
										String name )
		throws Exception {

		ObjectNode result = jacksonMapper.createObjectNode( ) ;
		result.put( "csapNoSort", true ) ;

		try {

			InspectContainerResponse info = dockerIntegration.containerConfiguration( name ) ;
			return nonNullMapper.convertValue( info, ObjectNode.class ) ;

		} catch ( Exception e ) {

			String reason = CSAP.buildCsapStack( e ) ;
			logger.warn( "Failed getting info {}, {}", name, reason ) ;
			result.put( DockerJson.error.json( ), "Docker API Failure: " + e.getClass( ).getName( ) ) ;
			// result.put( DockerJson.errorReason.json(), reason ) ;

			var inspectDetails = dockerIntegration.inspectByCli( name ) ;

			result.setAll( inspectDetails ) ;

		}

		return result ;

	}

	@GetMapping ( "/container/sockets" )
	public ObjectNode containerSockets (
											String id ,
											String name )
		throws Exception {

		ObjectNode result = jacksonMapper.createObjectNode( ) ;

		try {

			InspectContainerResponse info = dockerIntegration.containerConfiguration( name ) ;

			String socketInfo = dockerIntegration.dockerOpenSockets( info.getState( ).getPid( ) + "" ) ;
			result.put( "result", "socket info for pid: " + info.getState( ).getPid( ) ) ;
			result.put( DockerJson.response_plain_text.json( ), socketInfo ) ;

		} catch ( Exception e ) {

			String reason = CSAP.buildCsapStack( e ) ;
			logger.warn( "Failed starting {}, {}", name, reason ) ;
			result.put( DockerJson.error.json( ), "Failed starting: " + name ) ;
			result.put( DockerJson.errorReason.json( ), reason ) ;

		}

		return result ;

	}

	@GetMapping ( "/container/processTree" )
	public ObjectNode containerProcessTree (
												String id ,
												String name )
		throws Exception {

		ObjectNode result = jacksonMapper.createObjectNode( ) ;

		try {

			InspectContainerResponse info = dockerIntegration.containerConfiguration( name ) ;

			result = osManager.buildProcessReport( info.getState( ).getPid( ) + "", name ) ;
			result.put( "result", "process tree for pid: " + info.getState( ).getPid( ) ) ;

		} catch ( Exception e ) {

			String reason = CSAP.buildCsapStack( e ) ;
			logger.warn( "Failed starting {}, {}", name, reason ) ;
			result.put( DockerJson.error.json( ), "Failed starting: " + name ) ;
			result.put( DockerJson.errorReason.json( ), reason ) ;

		}

		return result ;

	}

	@GetMapping ( value = "/container/tail" , produces = MediaType.TEXT_HTML_VALUE )
	public void containerTailStream (
										String id ,
										String name ,
										HttpServletResponse response ,
										@RequestParam ( defaultValue = "500" ) int numberOfLines )
		throws Exception {

		dockerIntegration.containerTailStream( id, name, response, numberOfLines ) ;

	}

	@GetMapping ( "/container/tail" )
	public ObjectNode containerTail (
										String id ,
										String name ,
										@RequestParam ( defaultValue = "500" ) int numberOfLines ,
										@RequestParam ( defaultValue = "0" ) int since )
		throws Exception {

		return dockerIntegration.containerTail( id, name, numberOfLines, since ) ;

	}

	private void issueAudit ( String commandDesc , String details ) {

		csapEventClient.publishUserEvent( "docker",
				securitySettings.getRoles( ).getUserIdFromContext( ),
				commandDesc, details ) ;

	}

	@PostMapping ( "/container/cpuQuota" )
	public ObjectNode containerCpuQuota (
											String name ,
											Integer periodMs ,
											Integer quotaMs )
		throws Exception {

		issueAudit( name + ": Updating CPU quota: " + quotaMs + "ms, period: " + periodMs, null ) ;

		ObjectNode result = jacksonMapper.createObjectNode( ) ;

		try {

			result.put( DockerJson.response_plain_text.json( ),
					dockerIntegration.updateContainerCpuAllow( periodMs, quotaMs,
							dockerIntegration.findContainerByName( name ).get( ).getId( ) ) ) ;

		} catch ( Exception e ) {

			String reason = CSAP.buildCsapStack( e ) ;
			logger.warn( "Failed updating {}, {}", name, reason ) ;

			result.put( DockerJson.error.json( ), "Failed updaing: " + name ) ;
			result.put( DockerJson.errorReason.json( ), reason ) ;

		}

		return result ;

	}

	@PostMapping ( "/container/create" )
	public ObjectNode containerCreate (
										boolean start ,
										String image ,
										String name ,
										String command ,
										String entry ,
										String workingDirectory ,
										String network ,
										String restartPolicy ,
										String runUser ,
										String ports ,
										String volumes ,
										String environmentVariables ,
										String limits )
		throws Exception {

		issueAudit( "creating container: " + name + " from image: " + image, null ) ;

		return dockerIntegration.containerCreate(
				null, start, image, name,
				command, entry, workingDirectory,
				network, restartPolicy, runUser,
				ports, volumes, environmentVariables,
				limits ) ;

	}

	@PostMapping ( "/container/start" )
	public ObjectNode containerStart (
										String id ,
										String name )
		throws Exception {

		issueAudit( "starting container: " + name + "id: " + id, null ) ;
		ObjectNode result = jacksonMapper.createObjectNode( ) ;

		try {

			String targetId = getContainerId( id, name ) ;

			if ( targetId != null && ! targetId.isEmpty( ) ) {

				dockerClient.startContainerCmd( targetId ).exec( ) ;
				result.put( "result", "Started container: " + name + " id:" + targetId ) ;
				InspectContainerResponse info = dockerClient.inspectContainerCmd( targetId ).exec( ) ;

				result.set( "state", jacksonMapper.convertValue( info.getState( ), ObjectNode.class ) ) ;

			} else {

				result.put( DockerJson.error.json( ), "Container not found: " + name ) ;

			}

		} catch ( Exception e ) {

			String reason = CSAP.buildCsapStack( e ) ;
			logger.warn( "Failed starting {}, {}", name, reason ) ;

			if ( e.getClass( ).getSimpleName( ).toLowerCase( ).contains( "notmodified" ) ) {

				result.put( "result", "Container was already running: " + name ) ;

			} else {

				result.put( DockerJson.error.json( ), "Failed starting: " + name ) ;
				result.put( DockerJson.errorReason.json( ), reason ) ;

			}

		}

		return result ;

	}

	private String getContainerId ( String id , String name ) {

		String targetId = id ;

		if ( id == null || id.isEmpty( ) ) {

			Optional<Container> matchContainer = dockerIntegration.findContainerByName( name ) ;
			targetId = matchContainer.get( ).getId( ) ;

		}

		return targetId ;

	}

	@PostMapping ( "/container/stop" )
	public ObjectNode containerStop (
										String id ,
										String name ,
										boolean kill ,
										int stopSeconds )
		throws Exception {

		issueAudit( "starting container: " + name, null ) ;

		return dockerIntegration.containerStop( id, name, kill, stopSeconds ) ;

	}

	@PostMapping ( "/container/remove" )
	public ObjectNode containerRemove (
										String id ,
										String name ,
										boolean force )
		throws Exception {

		issueAudit( "removeing container: " + name, null ) ;
		return dockerIntegration.containerRemove( id, name, force ) ;

	}
}
