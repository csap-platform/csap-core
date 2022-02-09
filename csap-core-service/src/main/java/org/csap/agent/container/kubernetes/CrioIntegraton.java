package org.csap.agent.container.kubernetes ;

import java.io.File ;
import java.net.URISyntaxException ;
import java.util.List ;
import java.util.Map ;
import java.util.Objects ;
import java.util.stream.Collectors ;

import javax.servlet.http.HttpServletResponse ;

import org.csap.agent.CsapApis ;
import org.csap.agent.container.C7 ;
import org.csap.agent.container.ContainerIntegration ;
import org.csap.agent.container.ContainerProcess ;
import org.csap.helpers.CSAP ;
import org.csap.helpers.CsapApplication ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.web.client.RestTemplate ;

import com.fasterxml.jackson.databind.JsonNode ;
import com.fasterxml.jackson.databind.ObjectMapper ;
import com.fasterxml.jackson.databind.node.ArrayNode ;
import com.fasterxml.jackson.databind.node.ObjectNode ;

public class CrioIntegraton {

	final Logger logger = LoggerFactory.getLogger( this.getClass( ) ) ;

	ObjectMapper jsonMapper ;
	CsapApis csapApis ;

	public CrioIntegraton ( CsapApis csapApis, ObjectMapper jsonMapper ) {

		this.jsonMapper = jsonMapper ;
		this.csapApis = csapApis ;

	}

	File crioBinary = new File( "/usr/bin/crio" ) ;

	public List<String> containerNames ( ) {

		var pidReport = csapApis.osManager( ).getCachedCrioPidReport( ) ;

		var names = CSAP.asStreamHandleNulls( pidReport ).collect( Collectors.toList( ) ) ;

//		var containers = containers( ) ;
//		var names = CSAP.jsonStream( containers )
//				.map( container -> {
//
//					return "/" + container.at( "/metadata/name" ).asText( "notFound" ) ;
//
//				} )
//				.collect( Collectors.toList( ) ) ;
//
//		if ( csapApp.isDesktopHost( ) ) {
//
//			logger.info( CsapApplication.testHeader( "attempting to pull using remote" ) ) ;
//
//		}

		return names ;

	}

	public String listFiles ( String containerName , String path ) {

		var pidReport = csapApis.osManager( ).getCachedCrioPidReport( ) ;

		logger.debug( "looking up {} in {} ", containerName, pidReport ) ;

		return csapApis.osManager( )
				.buildCrioFileListing(
						pidReport.path( containerName ).path( "id" ).asText( "missing-id" ),
						path ) ;

	}

	public void writeContainerFileToHttpResponse (
													boolean isBinary ,
													String containerName ,
													String path ,
													HttpServletResponse servletResponse ,
													long maxEditSize ,
													int chunkSize ) {

		logger.info( "Container: {}, path: {}, maxEditSize: {}", containerName, path, maxEditSize ) ;

		var pidReport = csapApis.osManager( ).getCachedCrioPidReport( ) ;

		logger.debug( "looking up {} in {} ", containerName, pidReport ) ;

		var fileContents = csapApis.osManager( )
				.getCrioFileContents(
						pidReport.path( containerName ).path( "id" ).asText( "missing-id" ),
						path,
						maxEditSize ) ;

		if ( isBinary ) {

			servletResponse.setContentLength( Math.toIntExact( fileContents.length( ) ) ) ;

		}

		try {

			if ( fileContents.length( ) > maxEditSize - 100 ) {

				servletResponse.getWriter( ).print(
						"**** Warning: output truncated as max size reached: " + maxEditSize / 1024 +
								"Kb. \n\tView or download can be used to access entire file.\n=====================================" ) ;

			}

			servletResponse.getWriter( ).print( fileContents ) ;

		} catch ( Exception e ) {

			logger.warn( "Failed writing conttents {} ", CSAP.buildCsapStack( e ) ) ;

		}

	}

	public ObjectNode writeFileToContainer (
												String contents ,
												String containerName ,
												String pathToFile ,
												long maxEditSize ,
												int chunkSize ) {

		ObjectNode resultReport = jsonMapper.createObjectNode( ) ;

		logger.info( "Container: {}, path: {}, maxEditSize: {}", containerName, pathToFile, maxEditSize ) ;

		var pidReport = csapApis.osManager( ).getCachedCrioPidReport( ) ;

		logger.debug( "looking up {} in {} ", containerName, pidReport ) ;

		return resultReport ;

	}

	public int containerCount ( ) {

		return containers( ).size( ) ;

	}

	public ArrayNode containers ( ) {

		var containers = jsonMapper.createArrayNode( ) ;

		var psCliReport = csapApis.osManager( ).getCachedCrioPs( ) ;

		try {

			containers = (ArrayNode) psCliReport.path( "containers" ) ;

		} catch ( Exception e ) {

			logger.warn( "Failed to get ps listing: {}", CSAP.buildCsapStack( e ) ) ;

		}

		return containers ;

	}

	public ArrayNode containerListing ( ) {

		var containerListing = jsonMapper.createArrayNode( ) ;

		try {

			var containers = containers( ) ;

			CSAP.jsonStream( containers ).forEach( container -> {

				ObjectNode item = containerListing.addObject( ) ;

				var podName = container.path( "labels" ).path( "io.kubernetes.pod.name" ).asText( "notFound" ) ;

				var containerName = container.path( "metadata" ).path( "name" ).asText( "notFound" ) ;

				var label = podName + "," + containerName ;

//				long disk = 0 ;

				item.put( "label", label ) ;

				var attributes = (ObjectNode) container.deepCopy( ) ;
//				attributes.put( "allVolumesInMb", disk ) ;
				item.set( "attributes", attributes ) ;
//				attributes.put( "Create Date", getContainerCreationTime( container ) ) ;
				item.put( "folder", true ) ;
				item.put( "lazy", true ) ;

			} ) ;

			if ( containers.size( ) == 0 ) {

				ObjectNode item = containerListing.addObject( ) ;
				item.put( C7.error.val( ), "No containers defined" ) ;

			}

		} catch ( Exception e ) {

			containerListing.add( buildErrorResponse( "VolumeList ", e ) ) ;

		}

		return containerListing ;

	}

	public List<ContainerProcess> buildContainerProcesses ( ) {

		List<ContainerProcess> containerProcesses ;
		var pidReport = csapApis.osManager( ).getCachedCrioPidReport( ) ;

		logger.debug( "pidReport: {}", pidReport ) ;

//		var crioContainers = csapApis.application( ).crio( ).containers( ) ;
//		containerProcesses = CSAP.jsonStream( crioContainers )
		containerProcesses = CSAP.asStreamHandleNulls( pidReport )
				.map( pidReportId -> {

					var podReport = pidReport.path( pidReportId ) ;

					ContainerProcess process = new ContainerProcess( ) ;

					// need the uniqu name
					process.setContainerName( pidReportId ) ;

					logger.debug( "podReport: \n {}", podReport ) ;

					String containerMatchName = podReport
							.path( ContainerIntegration.IO_KUBERNETES_CONTAINER_NAME )
							.asText( "notFound" ) ;

					process.setMatchName( containerMatchName ) ;

					process.setPodName( podReport.path( "io.kubernetes.pod.name" ).asText( "pod-not-found" ) ) ;
					process.setPid( podReport.path( "pid" ).asText( ) ) ;
					process.setPodNamespace( podReport.path( "io.kubernetes.pod.namespace" ).asText(
							"namespace-not-found" ) ) ;

					var podTimer = csapApis.metrics( ).startTimer( ) ;

					// Very slow - instead use pidListing
					// var details = csapApis.application( ).crio( ).containerInspect(
					// container.path( "id").asText( ) ) ;

					csapApis.metrics( ).stopTimer( podTimer, "collect-container.pids."
							+ process.getPodName( ) ) ;

					return process ;

				} )
				.filter( Objects::nonNull )
				.collect( Collectors.toList( ) ) ;

		return containerProcesses ;

	}

	public ObjectNode containerInspect ( String id ) {

		var containerReport = csapApis.osManager( )
				.getCrioInspect( id ) ;

		return containerReport ;

	}

	private ObjectNode buildErrorResponse ( String description , Exception failureExeption ) {

		String reason = CSAP.buildCsapStack( failureExeption ) ;

		ObjectNode result = jsonMapper.createObjectNode( ) ;

		logger.warn( "Failure: {} {}", description, reason ) ;
		logger.debug( "detailed stack", failureExeption ) ;

		result.put( C7.error.val( ), description ) ;

		String uiMessage = failureExeption.getMessage( ) ;

		try {

			String testParse = jsonMapper.readTree( uiMessage ).path( "message" ).asText( "" ) ;

			if ( testParse.length( ) > 5 ) {

				uiMessage = testParse ;

			}

		} catch ( Exception e1 ) {

			logger.error( "Failed to parse uiMessage message", CSAP.buildCsapStack( e1 ) ) ;

		}

		result.put( C7.errorReason.val( ), uiMessage + ". Reference: " + failureExeption.getClass( )
				.getSimpleName( ) ) ;
		return result ;

	}

	public JsonNode buildRemoteListing ( String resource , Map<String, String> parameters )
		throws URISyntaxException {

		logger.info( CsapApplication.testHeader( resource ) ) ;

		var params = "" ;

		if ( parameters != null ) {

			params = parameters.entrySet( ).stream( )
					.map( entry -> {

						return entry.getKey( ) + "=" + entry.getValue( ) ;

					} )
					.collect( Collectors.joining( "&" ) ) ;

		}

		var dockerHost = csapApis.containerHostConfigured( ) ;
		var url = "http://" + dockerHost + ":8011" + resource + "?" + params ;

		logger.debug( " hitting: {} ", url ) ;

		var restTemplate = new RestTemplate( ) ;

		JsonNode remoteListing = null ;

		try {

			var remoteResponse = restTemplate.getForObject( url, String.class ) ;

			logger.debug( "remoteResponse: {} : {}", url, remoteResponse ) ;
			remoteListing = jsonMapper.readTree( remoteResponse ) ;

		} catch ( Exception e ) {

			logger.warn( "failed {} \n\t {}", url, CSAP.buildCsapStack( e ) ) ;
			remoteListing = build_not_configured_listing( ) ;

		}

		return remoteListing ;

	}

	public ArrayNode build_not_configured_listing ( ) {

		ArrayNode listing = jsonMapper.createArrayNode( ) ;
		ObjectNode item = listing.addObject( ) ;
		item.put( C7.list_label.val( ), C7.error.val( ) + "CRIO not configured" ) ;
		item.put( "folder", false ) ;
		item.put( C7.error.val( ), "CRIO not configured" ) ;
		return listing ;

	}
}
