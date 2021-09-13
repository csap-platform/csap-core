/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.csap.agent.ui.editor ;

import java.io.IOException ;
import java.util.ArrayList ;
import java.util.Collections ;
import java.util.HashMap ;
import java.util.Iterator ;
import java.util.List ;
import java.util.Map ;
import java.util.Optional ;
import java.util.concurrent.atomic.AtomicInteger ;
import java.util.function.Predicate ;
import java.util.stream.Collectors ;
import java.util.stream.IntStream ;

import javax.servlet.http.HttpServletRequest ;
import javax.servlet.http.HttpSession ;

import org.apache.commons.lang3.StringUtils ;
import org.csap.agent.CsapCore ;
import org.csap.agent.CsapCoreService ;
import org.csap.agent.container.kubernetes.KubernetesJson ;
import org.csap.agent.model.Application ;
import org.csap.agent.model.ClusterType ;
import org.csap.agent.model.ContainerState ;
import org.csap.agent.model.DefinitionConstants ;
import org.csap.agent.model.ProcessRuntime ;
import org.csap.agent.model.Project ;
import org.csap.agent.model.ProjectLoader ;
import org.csap.agent.model.ServiceAlertsEnum ;
import org.csap.agent.model.ServiceInstance ;
import org.csap.agent.ui.windows.CorePortals ;
import org.csap.docs.CsapDoc ;
import org.csap.helpers.CSAP ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.beans.factory.annotation.Autowired ;
import org.springframework.stereotype.Controller ;
import org.springframework.ui.ModelMap ;
import org.springframework.web.bind.annotation.GetMapping ;
import org.springframework.web.bind.annotation.RequestMapping ;
import org.springframework.web.bind.annotation.RequestMethod ;
import org.springframework.web.bind.annotation.RequestParam ;

import com.fasterxml.jackson.databind.JsonNode ;
import com.fasterxml.jackson.databind.ObjectMapper ;
import com.fasterxml.jackson.databind.node.ArrayNode ;
import com.fasterxml.jackson.databind.node.ObjectNode ;

/**
 *
 * @author someDeveloper
 */
@Controller
@RequestMapping ( value = CsapCoreService.EDIT_URL )
@CsapDoc ( title = "CSAP Application Editor" , notes = {
		"CSAP Application Editor provides ability to update/modify application definition, including "
				+ "viewing/reviewing current configuration, changing service parameters, and more",
		"<a class='csap-link' target='_blank' href='https://github.com/csap-platform/csap-core/wiki'>learn more</a>",
		"<img class='csapDocImage' src='CSAP_BASE/images/csapboot.png' />"
				+ "<img class='csapDocImage' src='CSAP_BASE/images/portals.png' />"
} )
public class ApplicationEditor {

	final Logger logger = LoggerFactory.getLogger( getClass( ) ) ;

	@Autowired
	CorePortals corePortals ;

	@Autowired
	Application csapApp ;

	ObjectMapper jacksonMapper = new ObjectMapper( ) ;

	@RequestMapping ( "/clusterDialog" )
	public String clusterDialog (
									@RequestParam ( value = "clusterName" , required = false ) String clusterName ,
									@RequestParam ( value = "lifeToEdit" , required = false ) String lifeToEdit ,
									@RequestParam ( value = CsapCore.PROJECT_PARAMETER , required = false ) String csapProjectName ,
									@RequestParam ( defaultValue = "false" ) boolean includeTemplates ,
									ModelMap modelMap ,
									HttpServletRequest request ,
									HttpSession session )
		throws IOException {

		if ( clusterName == null ) {

			lifeToEdit = csapApp.getCsapHostEnvironmentName( ) ;

		}

		if ( lifeToEdit == null ) {

			lifeToEdit = csapApp.getCsapHostEnvironmentName( ) ;

		}

		modelMap.addAttribute( "lifeToEdit", lifeToEdit ) ;
		modelMap.addAttribute( "clusterEntries", ClusterType.clusterEntries( ) ) ;

		Project requestedModel = null ;

		if ( csapProjectName == null ) {

			requestedModel = csapApp.getActiveProject( ) ;

		} else {

			requestedModel = csapApp.getProject( csapProjectName ) ;

		}

		modelMap.addAttribute( requestedModel ) ;

		logger.info( "clusterName: {}, lifeToEdit: {}, releasePackage: {}",
				clusterName, lifeToEdit, requestedModel.getName( ) ) ;

		ObjectNode lifecycleJson = (ObjectNode) requestedModel.getSourceDefinition( )
				.at( csapApp.getProjectLoader( ).getEnvPath( lifeToEdit ) ) ;

		ArrayList<String> clusterNames = new ArrayList<>( ) ;
		lifecycleJson.fieldNames( ).forEachRemaining( name -> {

			if ( ! name.equals( "settings" ) ) {

				clusterNames.add( name ) ;

			}

		} ) ;
		// modelMap.addAttribute( "clusterNames",
		// requestedModel.getLifeCycleToGroupMap().get( lifeToEdit ) );
		modelMap.addAttribute( "clusterNames", clusterNames ) ;

		// modelMap.addAttribute( "servicesInPackage", corePortals.getServices(
		// requestedModel ) );
		try {

			// addJeeSeviceAttributes( modelMap, requestedModel, lifeToEdit,
			// includeTemplates ) ;
			modelMap.addAttribute( "osServices",
					requestedModel.serviceNamesInModel( includeTemplates, ProjectLoader.SERVICE_TEMPLATES ) ) ;

			modelMap.addAttribute( "hosts", requestedModel.getHostsForEnvironment( lifeToEdit ) ) ;

		} catch ( Exception e ) {

			logger.error( "Failed configuring dialog", e ) ;

		}

		corePortals.setCommonAttributes( modelMap, session ) ;

		return "/editor/dialog-cluster" ;

	}

	// private void addJeeSeviceAttributes ( ModelMap modelMap, Project appPackage,
	// String lifeToEdit, boolean includeDefaults ) {
	//
	// JsonNode lifeJson = appPackage.getModelDefinition().at(
	// csapApp.getProjectLoader().buildLifePtr( lifeToEdit ) ) ;
	// List<JsonNode> javaServiceNodes = lifeJson.findValues(
	// ProjectLoader.EOL_CLUSTER_JAVA ) ;
	//
	// Map<String, String> jeePortMap = appPackage.serviceNamesInModel(
	// includeDefaults, ProjectLoader.EOL_DEF_JVMS ).stream()
	// // .filter( serviceName -> !serviceName.equals( Application.AGENT_ID
	// // ) )
	// .collect( Collectors.toMap(
	// serviceName -> serviceName,
	// serviceName -> getHttpPortFromCurrentClusters( serviceName, javaServiceNodes
	// ) ) ) ;
	//
	// AtomicInteger startPort = new AtomicInteger(
	// csapApp.rootProjectEnvSettings().getPortStart() + 10 ) ;
	//
	// jeePortMap.entrySet().stream()
	// .filter( jeeport -> jeeport.getValue().length() == 0 ) // we set
	// // unknown
	// // services
	// // to ""
	// .forEach( entry -> {
	// String nextAvailable = getNextAvailableHttpPort( javaServiceNodes, startPort
	// ) ;
	// entry.setValue( nextAvailable ) ;
	// // we need new values.
	// } ) ;
	//
	// modelMap.addAttribute( "jeeServices", jeePortMap ) ;
	//
	// // Get the next 20 free ports for manual assignment
	// List<String> jeeFreePorts = IntStream
	// .iterate( startPort.get(), i -> i + 1 ).limit( 20 )
	// .mapToObj( portNumber -> {
	// return getNextAvailableHttpPort( javaServiceNodes, startPort ) ;
	// } )
	// .collect( Collectors.toList() ) ;
	//
	// modelMap.addAttribute( "jeeFreePorts", jeeFreePorts ) ;
	//
	// }

	private String getHttpPortFromCurrentClusters ( String serviceName , List<JsonNode> javaServiceNodes ) {

		String portAssigned = javaServiceNodes.stream( )
				.filter( javaNode -> javaNode.has( serviceName ) )
				.map( javaNode -> javaNode.get( serviceName ) )
				.filter( JsonNode::isArray )
				.map( arrayNode -> arrayNode.get( 0 ).asText( ) )
				.findFirst( )
				.orElse( "" ) ;

		return portAssigned ;

	}

	private String getNextAvailableHttpPort ( List<JsonNode> javaServiceNodes , AtomicInteger startPort ) {

		logger.debug( " Starting at: {}", startPort.get( ) ) ;
		int pstart = startPort.get( ) + 10 ;

		String nextAvailable = IntStream
				.iterate( pstart, i -> i + 10 ).limit( 500 )
				.mapToObj( portNum -> {

					logger.debug( " Setting at: {}", portNum ) ;
					startPort.set( portNum ) ;
					String testPort = Integer.toString( portNum ) ;
					testPort = testPort.substring( 0, testPort.length( ) - 1 ) + "x" ;
					return testPort ;

				} )
				.filter( portAsString -> isPortAvailable( portAsString, javaServiceNodes ) )
				.findFirst( )
				.orElse( "000x" ) ;

		return nextAvailable ;

	}

	public boolean isPortAvailable ( String testPort , List<JsonNode> javaServiceNodes ) {

		Optional<JsonNode> matchingNodes = javaServiceNodes.stream( )
				.filter( JsonNode::isObject )
				.filter( isPortInCluster( testPort ) )
				.findFirst( ) ;

		return ! matchingNodes.isPresent( ) ;

	}

	private Predicate<? super JsonNode> isPortInCluster ( String testPort ) {

		return javaNode -> {

			logger.debug( "Checking: port: {} in {}", testPort, javaNode ) ;
			Iterator<JsonNode> portIterator = javaNode.elements( ) ;
			boolean isPortFound = false ;

			while ( portIterator.hasNext( ) && ! isPortFound ) {

				ArrayNode portArray = (ArrayNode) portIterator.next( ) ;

				for ( int i = 0; i < portArray.size( ); i++ ) {

					if ( portArray.get( i ).asText( ).equals( testPort ) ) {

						isPortFound = true ;
						break ;

					}

				}

			}

			return isPortFound ;

		} ;

	}

	@RequestMapping ( "/serviceDialog" )
	public String serviceDialog (
									@RequestParam ( "serviceName" ) String serviceName ,
									@RequestParam ( "hostName" ) String hostName ,
									ModelMap modelMap ,
									HttpServletRequest request ,
									@RequestParam ( value = "newService" , required = false ) String newService ,
									@RequestParam ( value = CsapCore.PROJECT_PARAMETER , required = false ) String csapProjectName ,
									@RequestParam ( defaultValue = "false" ) boolean includeTemplates ,
									HttpSession session )
		throws IOException {

		logger.debug( "service: {}", serviceName ) ;

		corePortals.setCommonAttributes( modelMap, session ) ;

		ServiceInstance serviceDefinition = csapApp.findFirstServiceInstanceInLifecycle( serviceName ) ;

		if ( serviceDefinition == null ) {

			modelMap.addAttribute( "unused", "unused" ) ;

		}

		modelMap.addAttribute( "tomcatServers", ProcessRuntime.javaServers( ) ) ;

		Map<String, String> limits = ServiceAlertsEnum.limitDefinitionsForService( serviceDefinition, csapApp
				.rootProjectEnvSettings( ) ) ;

		modelMap.addAttribute( "limits", limits ) ;

		Project serviceModel = null ;

		if ( csapProjectName == null ) {

			serviceModel = csapApp.getModel( hostName, serviceName ) ;

		} else {

			serviceModel = csapApp.getProject( csapProjectName ) ;

		}

		var applicationFileNames = List.of( serviceModel.getSourceFileName( ), Project.DEFINITION_TEMPLATE,
				Project.DEFINITION_COPY ) ;

		modelMap.addAttribute( "applicationFileNames", applicationFileNames ) ;

		var serviceNames = serviceModel.getServiceNamesInModel( includeTemplates ) ;
		// lets ALWAYS move item to top and handle default templates
		serviceNames.remove( serviceName ) ;
		serviceNames.add( 0, serviceName ) ;

		modelMap.addAttribute( "servicesInPackage", serviceNames ) ;
		modelMap.addAttribute( "servicePackage", serviceModel.getName( ) ) ;

		return "/editor/service/dialog-service" ;

	}

	@RequestMapping ( "/settingsDialog" )
	public String settingsDialog (
									@RequestParam ( value = "lifeToEdit" , required = false ) String lifeToEdit ,
									@RequestParam ( value = CsapCore.PROJECT_PARAMETER , required = false ) String csapProjectName ,
									ModelMap modelMap ,
									HttpServletRequest request ,
									HttpSession session )
		throws IOException {

		logger.debug( "lifeToEdit: {}", lifeToEdit ) ;

		if ( lifeToEdit == null ) {

			lifeToEdit = csapApp.getCsapHostEnvironmentName( ) ;

		}

		modelMap.addAttribute( "lifeToEdit", lifeToEdit ) ;

		if ( csapProjectName == null ) {

			csapProjectName = csapApp.getActiveProjectName( ) ;

		}

		Project model = csapApp.getProject( csapProjectName ) ;
		modelMap.addAttribute( model ) ;
		corePortals.setCommonAttributes( modelMap, session ) ;

		return "/editor/dialog-settings" ;

	}

	@RequestMapping ( value = "/lifecycle" , method = RequestMethod.GET )
	public String lifecycle (
								@RequestParam ( value = "lifeToEdit" , required = false ) String lifeToEdit ,
								@RequestParam ( value = CsapCore.PROJECT_PARAMETER , required = false ) String csapProjectName ,
								@RequestParam ( defaultValue = "false" ) boolean includeTemplates ,
								ModelMap modelMap ,
								HttpServletRequest request ,
								HttpSession session )
		throws IOException {

		logger.debug( "lifeToEdit: {}", lifeToEdit ) ;

		if ( lifeToEdit == null ) {

			lifeToEdit = csapApp.getCsapHostEnvironmentName( ) ;

		}

		corePortals.setCommonAttributes( modelMap, session ) ;

		modelMap.addAttribute( "lifeToEdit", lifeToEdit ) ;

		if ( csapProjectName == null ) {

			csapProjectName = csapApp.getActiveProjectName( ) ;

		}

		Project project = csapApp.getProject( csapProjectName ) ;
		modelMap.addAttribute( project ) ;

		var environmentDefinition = project.getSourceDefinition( ).at( csapApp.getProjectLoader( ).getEnvPath(
				lifeToEdit ) ) ;

		if ( environmentDefinition.isMissingNode( ) ) {

			ArrayList<String> clusterNames = new ArrayList<>( ) ;
			modelMap.addAttribute( "clusterNames", clusterNames ) ;
			return "/editor/_tab-environment" ;

		}

		ArrayList<String> environmentNames = new ArrayList<>( ) ;
		project.getSourceDefinition( )
				.at( csapApp.getProjectLoader( ).getEnvPath( ) )
				.fieldNames( )
				.forEachRemaining( lifeName -> environmentNames.add( lifeName ) ) ;

		modelMap.addAttribute( "environmentNames", environmentNames ) ;

		ObjectNode environmentSelected = (ObjectNode) environmentDefinition ;

		List<String> unusedServices = project.getServiceNamesInModel( includeTemplates ) ;
		ArrayList<String> clusterNames = new ArrayList<>( ) ;
		HashMap<String, String> clusterDescriptionMap = new HashMap<>( ) ;
		HashMap<String, String> clusterNotesMap = new HashMap<>( ) ;
		HashMap<String, String> clusterDisplayMap = new HashMap<>( ) ;
		HashMap<String, ArrayList<String>> hostsMap = new HashMap<>( ) ;
		HashMap<String, String> clusterProviderMap = new HashMap<>( ) ;
		HashMap<String, String> clusterToType = new HashMap<>( ) ;

		HashMap<String, ArrayList<String>> servicesMap = new HashMap<>( ) ;

		List<String> masterHostNames = new ArrayList<>( ) ;
		var environmentName = lifeToEdit ;

		CSAP.asStreamHandleNulls( environmentSelected )
				.filter( clusterName -> ! clusterName.equals( ProjectLoader.SETTINGS ) )
				.forEach( clusterName -> {

					clusterNames.add( clusterName ) ;

					var rawDefinition = environmentSelected.path( clusterName ) ;
					//
					//
					var clusterDefinition = csapApp
							.getProjectLoader( )
							.buildMergedClusterDefinition( clusterName, project, environmentName ) ;

					if ( rawDefinition.isObject( ) ) {

						clusterDefinition.setAll( (ObjectNode) rawDefinition ) ;

					}

					;

					if ( clusterDefinition.path( DefinitionConstants.clusterTemplate.key( ) ).asBoolean( false ) ) {

						clusterDisplayMap.put( clusterName, "low" ) ;

					} else if ( clusterDefinition.has( "display" ) ) {

						clusterDisplayMap.put( clusterName, clusterDefinition.get( "display" ).asText( ) ) ;

					} else if ( clusterDefinition.path( "type" ).asText( ).equals( ClusterType.MODJK.getJson( ) )
							|| clusterDefinition.path( "type" ).asText( ).equals( ClusterType.KUBERNETES_PROVIDER
									.getJson( ) ) ) {

						clusterDisplayMap.put( clusterName, "modjk" ) ;

					} else if ( clusterDefinition.path( "type" ).asText( ).equals( ClusterType.SIMPLE.getJson( ) ) ) {

						clusterDisplayMap.put( clusterName, "simple" ) ;

					} else if ( clusterDefinition.path( "type" ).asText( ).equals( ClusterType.KUBERNETES
							.getJson( ) ) ) {

						clusterDisplayMap.put( clusterName, "kubernetes" ) ;

					} else {

						clusterDisplayMap.put( clusterName, "normal" ) ;

					}

					ClusterType clusterType = ClusterType.getPartitionType( clusterDefinition ) ;
					clusterToType.put( clusterName, clusterType.getJson( ) ) ;
					logger.debug( "clusterDefinition {}", clusterDefinition ) ;

					if ( clusterType == ClusterType.KUBERNETES && clusterDefinition.has( ClusterType.KUBERNETES_PROVIDER
							.getJson( ) ) ) {

						clusterProviderMap.put( clusterName, clusterDefinition.path( ClusterType.KUBERNETES_PROVIDER
								.getJson( ) ).asText( ) ) ;

					} else if ( clusterType == ClusterType.KUBERNETES_PROVIDER ) {

						// clusterDefinition.path( DefinitionJson.clusterType.json()
						// clusterProviderMap.put( clusterName, clusterJson.at(
						// ClusterType.KUBERNETES.getJson() ).asText("") );
						JsonNode masters = clusterDefinition.path( KubernetesJson.masters.json( ) ) ;
						logger.debug( "masters {}", masters ) ;

						if ( masters.isArray( ) ) {

							CSAP.jsonStream( masters )
									.map( JsonNode::asText )
									.filter( StringUtils::isNotEmpty )
									.forEach( masterHostNames::add ) ;

						}

					}

					clusterNotesMap.put( clusterName, clusterDefinition.path( DefinitionConstants.clusterNotes.key( ) )
							.asText( "no notes" ) ) ;

					// ClusterType clusterPartition = ClusterType.getPartitionType(
					// clusterDefinition ) ;
					String description = clusterType.getDescription( ) ;

					if ( clusterType == ClusterType.KUBERNETES && clusterDefinition.has( ClusterType.KUBERNETES_PROVIDER
							.getJson( ) ) ) {

						description += " (" + clusterDefinition.path( ClusterType.KUBERNETES_PROVIDER.getJson( ) )
								.asText( ) + ")" ;

					}

					clusterDescriptionMap.put(
							clusterName,
							description ) ;

					ArrayList<String> hostList = new ArrayList<>( ) ;
					clusterDefinition.findValues( "hosts" ).forEach( hostNodeJson -> {

						ArrayNode hostsNode = (ArrayNode) hostNodeJson ;
						hostsNode.forEach( itemJson -> hostList.add( itemJson.asText( ) ) ) ;

					} ) ;
					Collections.sort( hostList, String.CASE_INSENSITIVE_ORDER ) ;
					hostsMap.put( clusterName, hostList ) ;

					ArrayList<String> serviceList = new ArrayList<>( ) ;

					JsonNode clusterTemplates = clusterDefinition.path( ProjectLoader.CLUSTER_TEMPLATE_REFERENCES ) ;

					if ( clusterTemplates.isArray( ) ) {

						clusterTemplates.forEach( templateName -> serviceList.add( templateName.asText( ) ) ) ;

					}

					Collections.sort( serviceList, String.CASE_INSENSITIVE_ORDER ) ;

					serviceList.forEach( service -> {

						if ( unusedServices.contains( service ) ) {

							unusedServices.remove( service ) ;

						}

					} ) ;

					servicesMap.put( clusterName, serviceList ) ;

				} ) ;

		Collections.sort( unusedServices, String.CASE_INSENSITIVE_ORDER ) ;
		modelMap.addAttribute( "unusedServices", unusedServices ) ;

		Collections.sort( clusterNames, String.CASE_INSENSITIVE_ORDER ) ;
		modelMap.addAttribute( "clusterNames", clusterNames ) ;
		modelMap.addAttribute( "clusterToType", clusterToType ) ;
		modelMap.addAttribute( "clusterDisplayMap", clusterDisplayMap ) ;
		modelMap.addAttribute( "clusterNotesMap", clusterNotesMap ) ;
		modelMap.addAttribute( "clusterDescriptionMap", clusterDescriptionMap ) ;
		modelMap.addAttribute( "hostsMap", hostsMap ) ;
		modelMap.addAttribute( "clusterProviderMap", clusterProviderMap ) ;
		modelMap.addAttribute( "masterHostNames", masterHostNames ) ;
		modelMap.addAttribute( "servicesMap", servicesMap ) ;

		return "/editor/_tab-environment" ;

	}

	@GetMapping ( "/summary" )
	public String applicationSummaryReport (
												@RequestParam ( value = "emptyCache" , required = false ) String emptyCache ,
												@RequestParam ( value = CsapCore.PROJECT_PARAMETER , required = false ) String csapProjectName ,
												ModelMap modelMap ,
												HttpSession session ) {

		if ( logger.isDebugEnabled( ) ) {

			logger.debug( "Updating Cache to reload cluster if it has changed" ) ;

		}

		if ( csapProjectName == null ) {

			csapProjectName = csapApp.getActiveProjectName( ) ;

		}

		Project project = csapApp.getProject( csapProjectName ) ;
		modelMap.addAttribute( "project", project ) ;

		csapApp.run_application_scan( ) ;

		corePortals.setCommonAttributes( modelMap, session ) ;

		var serviceToInstances = project.serviceInstancesInCurrentLifeByName( ) ;

		var serviceLimitReports = new ArrayList<List<String>>( ) ;
		var serviceManfestReports = new ArrayList<List<String>>( ) ;

		for ( var serviceEntry : serviceToInstances.entrySet( ) ) {

			var serviceName = serviceEntry.getKey( ) ;

			var firstServiceInstance = project.getServiceToAllInstancesMap( ).get( serviceName ).get( 0 ) ;

			if ( firstServiceInstance != null
					&& ! firstServiceInstance.is_files_only_package( )
					&& null != project.serviceInstancesInCurrentLifeByName( ).get( serviceName ) ) {

				var serviceLimitReport = new ArrayList<String>( ) ;
				serviceLimitReports.add( serviceLimitReport ) ;
				serviceLimitReport.add( serviceName ) ;

				for ( var alert : ServiceAlertsEnum.values( ) ) {

					var maxAllowed = ServiceAlertsEnum.getEffectiveLimit(
							firstServiceInstance,
							csapApp.rootProjectEnvSettings( ),
							alert ) ;

					var alertLimitSummary = ServiceAlertsEnum.getMaxAllowedSummary(
							firstServiceInstance,
							csapApp.rootProjectEnvSettings( ),
							alert ) ;

					if ( alert == ServiceAlertsEnum.memory
							|| alert == ServiceAlertsEnum.diskSpace ) {
						
						// reported in MB - convert to bytes and then print with unit

						serviceLimitReport.add(
								CSAP.printBytesWithUnits( maxAllowed * 1024 * 1024 ) ) ;



					} else if ( alert == ServiceAlertsEnum.diskWriteRate  ) {
						
						// reported in KB - convert to bytes and then print with unit

						serviceLimitReport.add(
								CSAP.printBytesWithUnits( maxAllowed * 1024  ) ) ;



					} else {

						serviceLimitReport.add( alertLimitSummary ) ;

					}

				}

				var serviceManfestReport = new ArrayList<String>( ) ;
				serviceManfestReports.add( serviceManfestReport ) ;
				serviceManfestReport.add( serviceName ) ;

				var defVersion = firstServiceInstance.getMavenId( ) ;

				if ( firstServiceInstance.is_docker_server( ) ) {

					defVersion = firstServiceInstance.getDockerImageName( ) ;

				} else if ( firstServiceInstance.is_os_process_monitor( ) ) {

					defVersion = "OS managed" ;

				}

				serviceManfestReport.add( defVersion ) ;

				// deployed

				var foundVersions = new ArrayList<String>( ) ;

				var lifeserviceInstances = csapApp.serviceInstancesByName(
						csapProjectName,
						serviceName ) ;

				for ( var instance : lifeserviceInstances ) {

					if ( csapApp.isAgentProfile( ) ) {

						var deployedServices = instance.getContainerStatusList( ).stream( ).map(
								ContainerState::getDeployedArtifacts ).collect( Collectors.toList( ) ) ;

						if ( deployedServices != null ) {

							foundVersions.addAll( deployedServices ) ;

						}

					} else {

						var runningServices = csapApp.healthManager( ).buildServiceRuntimes( instance ) ;

						if ( runningServices.isArray( ) ) {

							var deployedServices = CSAP.jsonStream( runningServices )
									.map( serviceStatus -> serviceStatus.path( ContainerState.DEPLOYED_ARTIFACTS )
											.asText( ) )
									.collect( Collectors.toList( ) ) ;

							if ( deployedServices != null ) {

								foundVersions.addAll( deployedServices ) ;

							}

						}

					}

				}

				var versions = "not found" ;

				if ( foundVersions.size( ) > 0 ) {

					versions = foundVersions.stream( )
							.distinct( )
							.collect( Collectors.toList( ) )
							.toString( ) ;

				}

				serviceManfestReport.add( versions ) ;

				serviceManfestReport.add( firstServiceInstance.getDocUrl( ) ) ;

			}

		}

		modelMap.addAttribute( "serviceLimitReports", serviceLimitReports ) ;

		modelMap.addAttribute( "serviceManfestReports", serviceManfestReports ) ;

		return "editor/summary-body" ;

	}

}
