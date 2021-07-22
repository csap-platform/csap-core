package org.csap.agent.api ;

import static java.util.Comparator.comparing ;

import java.io.IOException ;
import java.util.ArrayList ;
import java.util.Collections ;
import java.util.List ;
import java.util.Map ;
import java.util.Optional ;
import java.util.TreeMap ;
import java.util.stream.Collectors ;
import java.util.stream.Stream ;

import org.csap.CsapMonitor ;
import org.csap.agent.CsapCore ;
import org.csap.agent.CsapCoreService ;
import org.csap.agent.integrations.NagiosIntegration ;
import org.csap.agent.model.Application ;
import org.csap.agent.model.Project ;
import org.csap.agent.model.ProjectLoader ;
import org.csap.agent.model.ServiceInstance ;
import org.csap.docs.CsapDoc ;
import org.csap.helpers.CSAP ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.http.MediaType ;
import org.springframework.web.bind.annotation.GetMapping ;
import org.springframework.web.bind.annotation.PathVariable ;
import org.springframework.web.bind.annotation.RequestMapping ;
import org.springframework.web.bind.annotation.RequestParam ;
import org.springframework.web.bind.annotation.RestController ;

import com.fasterxml.jackson.core.JsonGenerationException ;
import com.fasterxml.jackson.databind.JsonMappingException ;
import com.fasterxml.jackson.databind.JsonNode ;
import com.fasterxml.jackson.databind.ObjectMapper ;
import com.fasterxml.jackson.databind.node.ArrayNode ;
import com.fasterxml.jackson.databind.node.ObjectNode ;

/**
 *
 * CSAP APIs
 *
 * @author someDeveloper
 *
 */
@RestController
@CsapMonitor ( prefix = "api.model" )
@RequestMapping ( {
		CsapCoreService.API_MODEL_URL
} )
@CsapDoc ( title = "/api/model/*: apis for querying CSAP definition (Application.json)" , type = CsapDoc.PUBLIC , notes = {
		"CSAP Application Apis provide access to the application definition and instances",
		"<a class='csap-link' target='_blank' href='https://github.com/csap-platform/csap-core/wiki'>learn more</a>",
		"<img class='csapDocImage' src='CSAP_BASE/images/csapboot.png' />"
				+ "<img class='csapDocImage' src='CSAP_BASE/images/event.png' />"
				+ "<img class='csapDocImage' src='CSAP_BASE/images/editOverview.png' />"
} )
public class ModelApi {

	final Logger logger = LoggerFactory.getLogger( this.getClass( ) ) ;

	ObjectMapper jacksonMapper = new ObjectMapper( ) ;

	private Application application ;

	public ModelApi ( Application application ) {

		this.application = application ;

	}

	@CsapDoc ( notes = "Summary information for application. On Admin nodes, it will include packages, clusters, trending configuration and more." )
	@GetMapping ( "/summary" )
	public ObjectNode applicationSummary ( )
		throws JsonGenerationException ,
		JsonMappingException ,
		IOException {

		return application.buildSummaryReport( true ) ;

	}

	@CsapDoc ( notes = {
			"Gets the application definition for specified release package ",
			"Optional: project - if not specified, Application.json (root package) will be returned",
			"Optional: path - json path in definition. If not specified - the entire definition will be returned"
	} , linkTests = {
			"Application.json",
			"CsAgent definition",
			"Release Package",
	} , linkGetParams = {
			"params=none", "path='/jvms/CsAgent'", "project=changeMe"
	} )
	@GetMapping ( "/application" )
	public JsonNode applicationDefinition (
											String project ,
											String path ) {

		if ( project == null ) {

			project = application.getRootProject( ).getName( ) ;

		}

		Project model = application.getProject( project ) ;

		if ( model == null ) {

			ObjectNode managerError = jacksonMapper.createObjectNode( ) ;
			managerError.put( "error", "Unrecognized package name: " + project ) ;
			managerError.set( "available", packagesWithCluster( ) ) ;
			return managerError ;

		}

		JsonNode results = model.getSourceDefinition( ) ;

		if ( path != null ) {

			results = results.at( path ) ;

		}

		return results ;

	}

	@CsapDoc ( notes = "Gets the last load time of the cluster. Usefull for binding to application changes" )
	@GetMapping ( "/application/latestReload" )
	public JsonNode applicationLatestReloadInfo ( ) {

		return application.latestReloadInfo( ) ;

	}

	@CsapDoc ( notes = "Gets the application lifecycle, based on how the current host is configured in the application definition" )
	@GetMapping ( "/host/lifecycle" )
	public JsonNode hostLifecycle ( ) {

		return jacksonMapper.createObjectNode( ).put( "lifecycle", application.getCsapHostEnvironmentName( ) ) ;

	}

	@CsapDoc ( notes = "Gets all hosts in lifecycle" )
	@GetMapping ( "/hosts" )
	public JsonNode hostsInLifecycle ( ) {

		logger.debug( "csapApp lifecycle is {}", application.getCsapHostEnvironmentName( ) ) ;

		Project allModel = application.getRootProject( ).getAllPackagesModel( ) ;

		return jacksonMapper.convertValue( allModel.getEnvironmentNameToHostInfo( )
				.get( application.getCsapHostEnvironmentName( ) ), ArrayNode.class ) ;

	}

	@CsapDoc ( notes = "Gets  hosts for specified {cluster} in current lifecycle. eg. webServer ." )
	@GetMapping ( "/hosts/{cluster}" )
	public JsonNode hostsForCluster (
										@PathVariable ( "cluster" ) String cluster ) {

		return hostsForClusterInPackage( Application.ALL_PACKAGES, cluster ) ;

	}

	@CsapDoc ( notes = "Gets hosts for specified {project}{cluster} in current lifecycle."
			+ "eg. webServer-1 (current lc), or dev-webServer-1, stage-webServer-1, etc."
			+ "For other lifecycles, prod-webServer-1, etc. To select all packages, pass\"{project}\"" )
	@GetMapping ( "/hosts/{project}/{cluster}" )
	public JsonNode hostsForClusterInPackage (
												@PathVariable ( "project" ) String project ,
												@PathVariable ( "cluster" ) String cluster ) {

		logger.debug( "csapApp lifecycle is {}", application.getCsapHostEnvironmentName( ) ) ;

		if ( project.equals( "{project}" ) ) {

			project = Application.ALL_PACKAGES ;

		}

		Project requestedModel = application.getProject( project ) ;

		ObjectNode hostNode = jacksonMapper.createObjectNode( ) ;

		logger.debug( "project: {} : {}", project, requestedModel.getLifeClusterToHostMap( ) ) ;

		String shortName = cluster ;

		if ( shortName.equals( "{cluster}" ) ) {

			shortName = requestedModel.getLifeClusterToHostMap( ).firstKey( ) ;

		}

		ArrayList<String> hosts = requestedModel.getLifeClusterToHostMap( ).get( shortName ) ;

		if ( hosts == null && ! shortName.startsWith( application.getCsapHostEnvironmentName( ) ) ) {

			shortName = application.getCsapHostEnvironmentName( ) + ProjectLoader.ENVIRONMENT_CLUSTER_DELIMITER
					+ shortName ;
			hosts = requestedModel.getLifeClusterToHostMap( ).get( shortName ) ;

		}

		hostNode.put( "project", project ) ;
		hostNode.put( "cluster", shortName ) ;
		hostNode.set( "hosts", jacksonMapper.convertValue( hosts, ArrayNode.class ) ) ;

		return hostNode ;

	}

	@CsapDoc ( notes = {
			"Cluster Report - release packages merged. Clusters with same names will be merged"
	} )
	@GetMapping ( "/clusters" )
	public ObjectNode clusters ( ) {

		return application.buildClusterReportForAllPackagesInActiveLifecycle( ) ;

	}

	@CsapDoc ( notes = {
			"Cluster Report - release packages separated"
	} )
	@GetMapping ( "/clusters/package" )
	public ObjectNode clustersByPackage ( ) {

		return application.buildClusterByPackageInActiveLifecycleReport( ) ;

	}

	@CsapDoc ( notes = "Gets packages, by cluster and hosts" )
	@GetMapping ( "/packages" )
	public ArrayNode packagesWithCluster ( ) {

		List<ObjectNode> nodeList = application
				.getReleasePackageStream( )
				.map( model -> {

					ObjectNode packageJson = jacksonMapper.createObjectNode( ) ;
					packageJson.put( "packageName", model.getName( ) ) ;
					packageJson.put( "packageFile", model.getSourceFileName( ) ) ;
					packageJson.set( "clusters", application.getClusters( model ) ) ;
					return packageJson ;

				} )
				.collect( Collectors.toList( ) ) ;

		return jacksonMapper.convertValue( nodeList, ArrayNode.class ) ;

	}

	@CsapDoc ( notes = {
			"Gets the artifacts configured in application definition",
			"Optional: project - if not specified, all artifacts in all packages will be returned"
	} , linkTests = {
			"All Packages",
			"changeMe package",
	} , linkGetParams = {
			"params=none", "project=changeMe"
	} )
	@GetMapping ( value = {
			"/artifacts", "/mavenArtifacts"
	} )
	public JsonNode artifacts ( String project ) {

		if ( project == null ) {

			project = application.getAllPackages( ).getName( ) ;

		}

		if ( application.getProject( project ) == null ) {

			ObjectNode managerError = jacksonMapper.createObjectNode( ) ;
			managerError.put( "error", "Unrecognized package name: " + project ) ;
			managerError.set( "available", packagesWithCluster( ) ) ;
			return managerError ;

		}

		List<String> mavenIds = application.getProject( project )
				.getServiceConfigStreamInCurrentLC( )
				.flatMap( serviceInstancesEntry -> serviceInstancesEntry.getValue( ).stream( ) )
				.map( serviceInstance -> serviceInstance.getMavenId( ) )
				.distinct( )
				.filter( version -> version.length( ) != 0 )
				.collect( Collectors.toList( ) ) ;

		return jacksonMapper.convertValue( mavenIds, ArrayNode.class ) ;

	}

	@CsapDoc ( notes = "All hosts in all lifecycles, organized by lifecycle" )
	@GetMapping ( "/hosts/allLifeCycles" )
	public JsonNode hostAllLifecycles ( ) {

		Project allModel = application.getRootProject( ).getAllPackagesModel( ) ;

		return jacksonMapper.convertValue( allModel.getEnvironmentNameToHostInfo( ), ObjectNode.class ) ;

	}

	@CsapDoc ( notes = "Nagios definition" )
	@GetMapping ( value = "/nagios/definition" , produces = MediaType.TEXT_PLAIN_VALUE )
	public String nagiosDefinition ( )
		throws IOException {

		String nagiosDefinition = NagiosIntegration.getNagiosDefinition( application.getReleasePackageStream( ) ) ;

		return nagiosDefinition ;

	}

	@CsapDoc ( notes = "Get all services" )
	@GetMapping ( value = "/services" )
	public ArrayNode services ( String project ) {

		if ( project == null ) {

			project = application.getRootProject( ).getName( ) ;

		}

		Project model = application.getProject( project ) ;

		// List<ServiceInstance> filterInstances = application.getActiveModel()
		// .getServicesOnHost( application.getCsapHostName() )
		// .collect( Collectors.toList() ) ;
		// return jacksonMapper.convertValue( filterInstances, ArrayNode.class ) ;

		return model.getServiceDefinitions( ) ;

	}

	@CsapDoc ( notes = "Get all services on host" )
	@GetMapping ( value = "/supportEmail" )
	public ArrayNode supportEmail ( ) {

		logger.debug( "Entered" ) ;

		return application
				.rootProjectEnvSettings( ).getEmailJsonArray( ) ;

	}

	@CsapDoc ( notes = {
			"Returns service names sorted by start order, optionally filtered by 1 or more cluster names",
			"Optional: reverse default:false;  shutdown order.",
			"Optional: appendInfo default:false; include cluster name and start order",
			"Optional: cluster default:all; filter by 1 or more cluster names",
			"Optional: project default:all; filter by project",
			""
	} , linkTests = {
			"No Parameters",
			"base-os and kubernetes clusters",
			"show order and cluster",
	} , linkGetParams = {
			"params=none", "filterCluster='base-os',filterCluster='kubernetes-1'", "appendInfo=true"
	} )
	@GetMapping ( value = "/services/name" )
	public JsonNode serviceNames (
									@RequestParam ( defaultValue = Application.ALL_PACKAGES ) String project ,
									@RequestParam ( required = false ) List<String> cluster ,
									@RequestParam ( defaultValue = "false" ) boolean appendInfo ,
									@RequestParam ( defaultValue = "false" ) boolean reverse ) {

		return getIdOrNames( true, false, project, cluster, appendInfo, reverse ) ;

	}

	@CsapDoc ( notes = {
			"Returns service names sorted by start order, optionally filtered by 1 or more cluster names",
			"Optional: reverse default:false;  shutdown order.",
			"Optional: appendInfo default:false; include cluster name and start order",
			"Optional: cluster default:all; filter by 1 or more cluster names",
			"Optional: project default:all; filter by project",
			""
	} , linkTests = {
			"No Parameters",
			"base-os and kubernetes clusters",
			"show order and cluster",
	} , linkGetParams = {
			"params=none", "filterCluster='base-os',filterCluster='kubernetes-1'", "appendInfo=true"
	} )
	@GetMapping ( value = "/services/names/all" )
	public JsonNode serviceNamesAll (
										@RequestParam ( defaultValue = Application.ALL_PACKAGES ) String project ,
										@RequestParam ( required = false ) List<String> cluster ,
										@RequestParam ( defaultValue = "false" ) boolean appendInfo ,
										@RequestParam ( defaultValue = "false" ) boolean reverse ) {

		return getIdOrNames( false, false, project, cluster, appendInfo, reverse ) ;

	}

	@CsapDoc ( notes = {
			"Returns service ids (used for start/stop) sorted by start order, optionally filtered by 1 or more cluster names",
			"Optional: reverse default:false;  shutdown order.",
			"Optional: appendInfo default:false; include cluster name and start order",
			"Optional: cluster default:all; filter by 1 or more cluster names",
			"Optional: project default:all; filter by project",
			""
	} , linkTests = {
			"No Parameters",
			"base-os and kubernetes clusters",
			"show order and cluster",
	} , linkGetParams = {
			"params=none", "filterCluster='base-os',filterCluster='kubernetes-1'", "appendInfo=true"
	} )
	@GetMapping ( value = "/services/id" )
	public JsonNode serviceIds (
									@RequestParam ( defaultValue = Application.ALL_PACKAGES ) String project ,
									@RequestParam ( required = false ) List<String> cluster ,
									@RequestParam ( defaultValue = "false" ) boolean appendInfo ,
									@RequestParam ( defaultValue = "false" ) boolean reverse ) {

		return getIdOrNames( true, true, project, cluster, appendInfo, reverse ) ;

	}

	private JsonNode getIdOrNames (
									boolean includeOnlyAutostart ,
									boolean isId ,
									String projectName ,
									List<String> clusterFilter ,
									boolean appendInfo ,
									boolean reverse ) {

		if ( projectName == null ) {

			projectName = application.getRootProject( ).getName( ) ;

		}

		Project project = application.getProject( projectName ) ;

		var services = project.getServiceConfigStreamInCurrentLC( ).flatMap(
				serviceInstancesEntry -> serviceInstancesEntry.getValue( ).stream( ) ) ;

		if ( application.isAgentProfile( ) ) {

			services = application.getServicesOnHost( ).stream( ) ;

		}

		Stream<ServiceInstance> instances = services
				.filter( CSAP.distinctByKey( ServiceInstance::getName ) )
				.filter( instance -> {

					return ! includeOnlyAutostart || instance.isAutoStart( ) ;

				} )
				.filter( service -> {

					if ( clusterFilter == null ) {

						return true ;

					}

					Optional<String> matchedCluster = clusterFilter.stream( )
							.filter( name -> name.equals( service.getCluster( ) ) )
							.findFirst( ) ;

					return matchedCluster.isPresent( ) ;

				} ) ;

		if ( includeOnlyAutostart ) {

			instances = instances.sorted( comparing( ServiceInstance::startOrder ) ) ;

		} else {

			instances = instances.sorted( comparing( ServiceInstance::getName ) ) ;

		}

		List<String> serviceNames = instances

				.map( service -> {

					String result = service.getName( ) ;

					if ( isId ) {

						result = service.getServiceName_Port( ) ;

					}

					if ( appendInfo ) {

						result = CSAP.pad( service.getName( ) ) ;
						result += CSAP.pad( service.getPort( ) + "" ) ;
						result += CSAP.pad( service.startOrder( ) + "" ) ;
						result += service.getCluster( ) ;

					}

					return result ;

				} )
				.collect( Collectors.toList( ) ) ;

		if ( reverse ) {

			Collections.reverse( serviceNames ) ;

		}

		return jacksonMapper.convertValue( serviceNames, ArrayNode.class ) ;

	}

	@CsapDoc ( notes = "get service url for services on current host" )
	@GetMapping ( value = "/service/urls" )
	public ObjectNode serviceUrls ( ) {

		Map<String, List<String>> serviceMappings = application.getActiveProject( )
				.getServicesOnHost( application.getCsapHostName( ) )
				.collect( Collectors.groupingBy( ServiceInstance::getName,
						Collectors.mapping( ServiceInstance::getUrl,
								Collectors.toList( ) ) ) ) ;

		return jacksonMapper.convertValue( serviceMappings, ObjectNode.class ) ;

	}

	@CsapDoc ( notes = "get service url for services on current host" )
	@GetMapping ( value = "/service/http/ports" )
	public ObjectNode serviceHttpPorts ( ) {

		Map<String, List<String>> serviceMappings = application.getActiveProject( )
				.getServicesOnHost( application.getCsapHostName( ) )
				.collect( Collectors.groupingBy( ServiceInstance::getName,
						Collectors.mapping( ServiceInstance::getPort,
								Collectors.toList( ) ) ) ) ;

		return jacksonMapper.convertValue( serviceMappings, ObjectNode.class ) ;

	}

	@CsapDoc ( notes = "get service url for services on current host" )
	@GetMapping ( value = "/service/jmx/ports" )
	public ObjectNode serviceJmxPorts ( ) {

		Map<String, List<String>> serviceMappings = application.getActiveProject( )
				.getServicesOnHost( application.getCsapHostName( ) )
				.collect( Collectors.groupingBy( ServiceInstance::getName,
						Collectors.mapping( ServiceInstance::getJmxPort,
								Collectors.toList( ) ) ) ) ;

		return jacksonMapper.convertValue( serviceMappings, ObjectNode.class ) ;

	}

	@CsapDoc ( notes = "get allservice urls for the specified service. Only valid on admins" )
	@GetMapping ( value = "/service/urls/{serviceName}" )
	public JsonNode serviceUrlsForAllInstances (
													@PathVariable ( "serviceName" ) String serviceName ) {

		if ( serviceName.equals( "{serviceName}" ) ) {

			return getAllServices( ) ;

		}

		List<String> urls = application
				.getServiceInstances( Application.ALL_PACKAGES, serviceName )
				.map( serviceInstance -> serviceInstance.getUrl( ) )
				.collect( Collectors.toList( ) ) ;

		return jacksonMapper.convertValue( urls, ArrayNode.class ) ;

	}

	private ObjectNode managerError ;

	{

		managerError = jacksonMapper.createObjectNode( ) ;
		managerError.put( "error", "Only permitted on admin nodes" ) ;

	}

	private ObjectNode getAllServices ( ) {

		ObjectNode response = jacksonMapper.createObjectNode( ) ;

		response.put( "info", "add service name to url" ) ;
		response.set( "availableServices", jacksonMapper.convertValue(
				application.getActiveProject( ).getAllPackagesModel( ).getServiceNamesInLifecycle( ),
				ArrayNode.class ) ) ;
		return response ;

	}

	@CsapDoc ( notes = {
			"Service Definitions on host that match name specified regular expression filters",
	} , linkTests = {
			CsapCore.AGENT_NAME,
			"List"
	} , linkGetParams = {
			"serviceName=CsAgent"
	} )
	@GetMapping ( "/services/byName/{serviceName:.+}" )
	public JsonNode serviceDefinitionsFilteredByName (
														@PathVariable ( "serviceName" ) String serviceName ) {

		if ( serviceName.equals( "{serviceName}" ) ) {

			return getAllServices( ) ;

		}

		List<ServiceInstance> filterInstances = application.getActiveProject( )
				.getServicesOnHost( application.getCsapHostName( ) )
				.filter( serviceInstance -> serviceInstance.getName( ).matches( serviceName ) )
				.collect( Collectors.toList( ) ) ;

		return jacksonMapper.convertValue( filterInstances, ArrayNode.class ) ;

	}

	@CsapDoc ( notes = {
			"Service Definitions on host that match regular expression context specified regular expression filters"
	} , linkTests = {
			CsapCore.AGENT_NAME,
			"List"
	} , linkGetParams = {
			"contextName=CsAgent"
	} )

	@GetMapping ( "/services/byContext/{contextName:.+}" )
	public JsonNode serviceDefinitionsFilterdByContext (
															@PathVariable ( "contextName" ) String contextName ) {

		if ( contextName.equals( "{contextName}" ) ) {

			return getAllServices( ) ;

		}

		List<ServiceInstance> filterInstances = application.getActiveProject( )
				.getServicesOnHost( application.getCsapHostName( ) )
				.filter( serviceInstance -> serviceInstance.getContext( ).matches( contextName ) )
				.collect( Collectors.toList( ) ) ;

		return jacksonMapper.convertValue( filterInstances, ArrayNode.class ) ;

	}

	@CsapDoc ( notes = "Gets all service definitions grouped by host" )
	@GetMapping ( value = "/services/currentLifecycle" )
	public JsonNode serviceDefinitionsInLifecycleGroupedByHost ( ) {

		Project allModel = application.getRootProject( ).getAllPackagesModel( ) ;

		var hostToConfigMap = new TreeMap<String, List<ServiceInstance>>( ) ;

		allModel.getHostsCurrentLc( )
				.forEach( host -> hostToConfigMap.put( host, allModel.getServicesListOnHost( host ) ) ) ;

		return jacksonMapper.convertValue( hostToConfigMap, ObjectNode.class ) ;

	}

	@CsapDoc ( notes = "Gets ports used by services" )
	@GetMapping ( value = "/services/currentLifecycle/ports" )
	public JsonNode portsUsedByServices ( ) {

		Project allModel = application.getRootProject( ).getAllPackagesModel( ) ;

		TreeMap<String, ArrayList<Integer>> hostToConfigMap = new TreeMap<String, ArrayList<Integer>>( ) ;

		allModel.getHostsCurrentLc( )
				.forEach( host -> {

					ArrayList<Integer> portList = new ArrayList<Integer>( ) ;
					allModel.getServicesListOnHost( host ).forEach( serviceInstance -> {

						try {

							int httpPort = Integer.parseInt( serviceInstance.getPort( ) ) ;

							if ( httpPort > 0 ) {

								portList.add( httpPort ) ;

							}

							int jmxPort = Integer.parseInt( serviceInstance.getJmxPort( ) ) ;

							if ( jmxPort > 0 ) {

								portList.add( jmxPort ) ;

							}

						} catch ( NumberFormatException e ) {

							logger.debug( "Failed to parse port for service: {}", serviceInstance.getName( ), e ) ;

						}

					} ) ;
					hostToConfigMap.put( host, portList ) ;

				} ) ;

		return jacksonMapper.convertValue( hostToConfigMap, ObjectNode.class ) ;

	}

	@CsapDoc ( notes = "Service Definitions on all hosts that match name specified regular expression filters" , linkTests = {
			CsapCore.AGENT_NAME,
			"List"
	} , linkGetParams = {
			"serviceName=CsAgent"
	} )
	@GetMapping ( value = {
			"/services/currentLifecycle/name/{serviceName}", "/services/currentLifecycle/jvm/{serviceName}"
	} )
	public JsonNode serviceDefinitionsInLifecycleFilteredByName (
																	@PathVariable ( "serviceName" ) String serviceName ) {

		if ( serviceName.equals( "{serviceName}" ) ) {

			return getAllServices( ) ;

		}

		return jacksonMapper.convertValue( application.getLifeCycleServicesByMatch( serviceName ), ArrayNode.class ) ;

	}

	@CsapDoc ( notes = "Service Definitions on all hosts that matches regular expression context specified regular expression filters" , linkTests = {
			CsapCore.AGENT_NAME, "List"
	} , linkGetParams = {
			"serviceContext=CsAgent"
	} )
	@GetMapping ( "/services/currentLifecycle/context/{serviceContext}" )
	public JsonNode serviceDefinitionsInLifecycleFilteredByContext (
																		@PathVariable ( "serviceContext" ) String serviceContext ) {

		logger.info( "got filter: {}", serviceContext ) ;

		if ( serviceContext.equals( "{serviceContext}" ) ) {

			return getAllServices( ) ;

		}

		ArrayList<ServiceInstance> filterInstances = new ArrayList<ServiceInstance>( ) ;

		for ( String svcName : application.serviceNameToAllInstances( ).keySet( ) ) {

			if ( ! application.serviceNameToAllInstances( ).get( svcName ).get( 0 ).getContext( )
					.matches( serviceContext ) ) {

				continue ;

			}

			for ( ServiceInstance instance : application.serviceNameToAllInstances( ).get( svcName ) ) {

				if ( instance.getLifecycle( ).startsWith( application.getCsapHostEnvironmentName( ) ) ) {

					filterInstances.add( instance ) ;

				}

			}

		}

		return jacksonMapper.convertValue( filterInstances, ArrayNode.class ) ;

	}

}
