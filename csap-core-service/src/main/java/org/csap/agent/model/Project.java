package org.csap.agent.model ;

import java.io.File ;
import java.util.ArrayList ;
import java.util.Collections ;
import java.util.Comparator ;
import java.util.HashSet ;
import java.util.List ;
import java.util.Map ;
import java.util.Map.Entry ;
import java.util.Optional ;
import java.util.Set ;
import java.util.TreeMap ;
import java.util.stream.Collectors ;
import java.util.stream.Stream ;

import org.apache.commons.lang3.StringUtils ;
import org.csap.agent.CsapApis ;
import org.csap.agent.CsapTemplates ;
import org.csap.agent.linux.HostInfo ;
import org.csap.agent.ui.editor.ServiceResources ;
import org.csap.helpers.CSAP ;
import org.csap.security.CsapUser ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;

import com.fasterxml.jackson.databind.JsonNode ;
import com.fasterxml.jackson.databind.ObjectMapper ;
import com.fasterxml.jackson.databind.node.ArrayNode ;
import com.fasterxml.jackson.databind.node.MissingNode ;
import com.fasterxml.jackson.databind.node.ObjectNode ;

/**
 *
 * In Memory Model for parsed capability definitions
 *
 *
 * @author someDeveloper
 *
 *
 * @see <a href=
 *      "https://github.com/csap-platform/csap-core/wiki#updateRefDomain+Model">
 *      CS -AP Definition Model in reference guide </a>
 *
 *
 * @see <a href="doc-files/csagent.jpg"> Click to enlarge
 *      <IMG width=300 SRC="doc-files/csagent.jpg"></a>
 *
 * @see <a href="doc-files/spring.jpg" > Click to enlarge
 *      <IMG width=300 SRC="doc-files/modelDocs.jpg"></a>
 *
 */
public class Project {

	public static final String DEFINITION_COPY = "copySource" ;

	public static final String DEFINITION_FILES = "definition-files" ;

	public static final String DEFINITION_SOURCE = "definitionSource" ;
	public static final String DEFINITION_TEMPLATE = "csap-templates" ;
	public final static String GLOBAL_PACKAGE = "global" ;
	public static final String PARSER_RELEASE_PACKAGE_NAME = "name" ;

	private String hostEnvironmentName = "" ;

	private Map<String, String> kubernetesServiceNames = new TreeMap<String, String>( ) ;
	private Map<String, String> kubernetesMasterHosts = new TreeMap<String, String>( ) ;

	private Project activeModel = this ;

	private Project allPackagesModel = this ;

	private String architect = "UpdateArchitectName" ;

	private String description = "Update the package description." ;

	private String emailNotifications = "support@notConfigured.com" ;
	private ArrayList<String> environmentAndClusterNames = new ArrayList<String>( ) ;

	private TreeMap<String, EnvironmentSettings> environmentNameToSettings = new TreeMap<String, EnvironmentSettings>( ) ;

	private ArrayList<String> hostsInAllLifecycles = new ArrayList<String>( ) ;

	private TreeMap<String, ArrayList<ServiceInstance>> hostToConfigMap = new TreeMap<String, ArrayList<ServiceInstance>>( ) ;

	private List<String> httpdTestUrls = new ArrayList<String>( ) ;

	private ObjectNode infrastructure = null ;

	boolean isRootPackage = false ;

	private ObjectMapper jsonMapper = new ObjectMapper( ) ;

	private TreeMap<String, ArrayList<String>> envToServiceMap = new TreeMap<String, ArrayList<String>>( ) ;

	private TreeMap<String, ArrayList<String>> lifeClusterToHostMap = new TreeMap<String, ArrayList<String>>( ) ;

	private TreeMap<String, ArrayList<String>> lifeClusterToOsServices = new TreeMap<String, ArrayList<String>>( ) ;

	private TreeMap<String, String> lifeClusterToTypeMap = new TreeMap<String, String>( ) ;

	private TreeMap<String, ArrayList<String>> lifecycleToClusterMap = new TreeMap<String, ArrayList<String>>( ) ;
	private TreeMap<String, ArrayList<HostInfo>> environmentNameToHostInfo = new TreeMap<String, ArrayList<HostInfo>>( ) ;

	private TreeMap<String, ArrayList<String>> lifeCycleToHostMap = new TreeMap<String, ArrayList<String>>( ) ;

	final Logger logger = LoggerFactory.getLogger( getClass( ) ) ;

	private ObjectNode sourceDefinition = null ;
	private String editUserid = "" ;

	private TreeMap<String, Project> projectNameToProject = new TreeMap<String, Project>( ) ;

	private File sourceFile = null ;

	private String name = GLOBAL_PACKAGE ;

	// All Service instances, All lifecycles
	private TreeMap<String, ArrayList<ServiceInstance>> serviceNameToAllInstancesMap = new TreeMap<String, ArrayList<ServiceInstance>>( ) ;

	// Service Instances for Current Lifecycles
	private TreeMap<String, List<ServiceInstance>> serviceNameToLifeInstancesMap = new TreeMap<String, List<ServiceInstance>>( ) ;

	ArrayNode serviceTemplates ;

	private ArrayList<String> versionList = new ArrayList<String>( ) ;

	public Project ( ObjectNode projectDefinition ) {

		this.sourceDefinition = projectDefinition ;
		infrastructure = jsonMapper.createObjectNode( ) ;

	}

	public Project ( ObjectNode jsonModelDefinition, Project rootProject ) {

		this.sourceDefinition = jsonModelDefinition ;
		this.setProjectNameToProject( rootProject.getProjectNameToProject( ) ) ;
		infrastructure = jsonMapper.createObjectNode( ) ;

	}

	private JsonNode find_service_definition (
												String serviceName ) {

		logger.debug( "locating definition for {}", serviceName ) ;

		var extendedService = jsonMapper.createObjectNode( ) ;

		JsonNode serviceDefinition = getSourceDefinition( ).path( ProjectLoader.SERVICE_TEMPLATES ).path(
				serviceName ) ;
		var definitionSource = getSourceFileName( ) ;
		var templatePath = "" ; // defaults to root

		//
		// use copy name if specified
		//
		var copySource = serviceDefinition.path( DEFINITION_COPY ) ;

		if ( copySource.isTextual( ) ) {

			var sourceServiceName = copySource.asText( ) ;
			extendedService.put( DEFINITION_COPY, sourceServiceName ) ;

			logger.debug( "{} copySource: {}", serviceName, sourceServiceName ) ;

			// 1 - check current model
			definitionSource = DEFINITION_COPY ;
			serviceDefinition = getSourceDefinition( )
					.path( ProjectLoader.SERVICE_TEMPLATES )
					.path( sourceServiceName ) ;

			if ( serviceDefinition.isMissingNode( ) || serviceDefinition.has( DEFINITION_COPY ) ) {

				logger.debug( "2. check templates" ) ;

				for ( JsonNode template : getCsapServiceTemplates( ) ) {

					var definition = template.path( DefinitionConstants.definition.key( ) ) ;

					if ( definition.path( ProjectLoader.SERVICE_TEMPLATES ).path( sourceServiceName ).isObject( ) ) {

						serviceDefinition = definition.path( ProjectLoader.SERVICE_TEMPLATES ).path(
								sourceServiceName ) ;
						templatePath = template.path( DefinitionConstants.pathToTemplate.key( ) ).asText( ) ;

					}

				}

			}

			if ( serviceDefinition.isMissingNode( ) || serviceDefinition.has( DEFINITION_COPY ) ) {

				serviceDefinition = getRootPackage( )
						.getSourceDefinition( )
						.path( ProjectLoader.SERVICE_TEMPLATES )
						.path( sourceServiceName ) ;
				logger.debug( " 3. check root package: {}", getRootPackage( ).getSourceFileName( ) ) ;

			}

		} else if ( serviceDefinition.isMissingNode( ) ) {

			definitionSource = DEFINITION_TEMPLATE ;

			for ( JsonNode template : getCsapServiceTemplates( ) ) {

				var definition = template.path( DefinitionConstants.definition.key( ) ) ;

				if ( definition.path( ProjectLoader.SERVICE_TEMPLATES ).path( serviceName ).isObject( ) ) {

					serviceDefinition = definition.path( ProjectLoader.SERVICE_TEMPLATES ).path( serviceName ) ;
					templatePath = template.path( DefinitionConstants.pathToTemplate.key( ) ).asText( ) ;

				}

			}

			if ( serviceDefinition.isMissingNode( ) ) {

				//
				// Use externalized definition: csap-service.json if found
				//
				logger.debug( "checking for definition on filesystem: {}", serviceName ) ;

				var externalizedDefinition = loadExternalizedServiceDefinition( serviceName ) ;

				if ( externalizedDefinition.isObject( ) ) {

					// definitionSource = DEFINITION_TEMPLATE ;
					serviceDefinition = externalizedDefinition ;

				}

			}

		}

		// legacy - copy from default package
		if ( serviceDefinition.isMissingNode( ) && ! isRootPackage ) {

			if ( numMissingMessages++ < 5 ) {

				logger.warn( "Service {} not found in {}"
						+ CSAP.padLine( "searching" ) + "root package, recommended to add: {}",
						serviceName,
						getSourceFileName( ),
						DEFINITION_COPY ) ;

			} else {

				logger.debug( "Service {} not found in {}"
						+ CSAP.padLine( "searching" ) + "root package, recommended to add: {}",
						serviceName,
						getSourceFileName( ),
						DEFINITION_COPY ) ;

			}

			definitionSource = DEFINITION_COPY ;
			// definitionSource = DEFINITION_COPY + "_" +
			// getRootPackage().getReleasePackageName() ;
			serviceDefinition = getRootPackage( )
					.getSourceDefinition( )
					.path( ProjectLoader.SERVICE_TEMPLATES )
					.path( serviceName ) ;

		}

		extendedService.set( DefinitionConstants.definition.key( ), serviceDefinition ) ;
		extendedService.put( DEFINITION_SOURCE, definitionSource ) ;
		extendedService.put( DefinitionConstants.pathToTemplate.key( ), templatePath ) ;

		logger.debug( "{} extendedService: {}", serviceName, extendedService.toString( ) ) ;

		return extendedService ;

	}

	int numMissingMessages = 0 ;

	public String find_service_template_path ( String serviceName ) {

		var extendedDefinition = find_service_definition( serviceName ) ;

		var source = extendedDefinition.path( DefinitionConstants.pathToTemplate.key( ) ).asText( ) ;
		logger.debug( "template path: {}", source ) ;

		return source ;

	}

	public JsonNode findAndCloneServiceDefinition ( String serviceName ) {

		JsonNode clonedServiceDefinition = MissingNode.getInstance( ) ;

		var extendedDefinition = find_service_definition( serviceName ) ;

		//
		// We might be overriding definitions partially - and they might be templates in
		// jvm
		// - check is on serviceType - which should never be overridden
		//

		if ( extendedDefinition.isObject( ) ) {

			var sourceDefinition = extendedDefinition.path( DefinitionConstants.definition.key( ) ) ;

			if ( sourceDefinition.isObject( ) ) {

				// creating a cloned instance, for both ordering of attributes, and insertion
				var serviceClone = jsonMapper.createObjectNode( ) ;
				serviceClone.put( DEFINITION_SOURCE, extendedDefinition.path( DEFINITION_SOURCE ).asText( ) ) ;

				if ( extendedDefinition.has( DEFINITION_COPY ) ) {

					serviceClone.put( DEFINITION_COPY, extendedDefinition.path( DEFINITION_COPY ).asText( ) ) ;

				}

				serviceClone.put( DEFINITION_SOURCE, extendedDefinition.path( DEFINITION_SOURCE ).asText( ) ) ;

				if ( extendedDefinition.path( DEFINITION_SOURCE ).asText( ).equals( DEFINITION_TEMPLATE ) ) {

					var pathToTemplate = extendedDefinition.path( DefinitionConstants.pathToTemplate.key( ) )
							.asText( ) ;

					if ( StringUtils.isEmpty( pathToTemplate ) ) {

						pathToTemplate = CsapTemplates.default_service_definitions.getKey( ) ;

					}

					serviceClone.put( DefinitionConstants.pathToTemplate.key( ),
							pathToTemplate ) ;

				}

				ObjectNode deepCopyClone = sourceDefinition.deepCopy( ) ;

				for ( String item : ServiceAttributes.preferredOrder( ) ) {

					if ( deepCopyClone.has( item ) ) {

						serviceClone.put( item, "-" ) ;

					}

				}

				serviceClone.setAll( deepCopyClone ) ;

				if ( ! serviceClone.has( ServiceAttributes.description.json( ) ) ) {

					serviceClone.put( ServiceAttributes.description.json( ),
							"added by " + CsapUser.currentUsersID( ) ) ;

				}

				clonedServiceDefinition = serviceClone ;

			}

		}

		return clonedServiceDefinition ;

	}

	/**
	 * gets hosts in current life cycle
	 *
	 * @param serviceName
	 * @return
	 */
	public List<String> findHostsForService ( String serviceName ) {

		logger.debug( " looking for: {} in: {} ", serviceName, serviceInstancesInCurrentLifeByName( ).keySet( ) ) ;

		if ( ! serviceInstancesInCurrentLifeByName( ).containsKey( serviceName ) ) {

			return new ArrayList<>( ) ;

		}

		return serviceInstancesInCurrentLifeByName( )
				.get( serviceName ).stream( )
				.map( ServiceInstance::getHostName )
				.distinct( )
				.collect( Collectors.toList( ) ) ;

	}

	public List<String> findOtherHostsForService ( String serviceName ) {

		logger.debug( "{} instances: {}", serviceName,
				getServiceInstances( serviceName )
						.filter( instance -> ! instance.getHostName( ).equals( CsapApis.getInstance( ).application( )
								.getCsapHostName( ) ) )
						.collect( Collectors.toList( ) ).size( ) ) ;

		return getServiceInstances( serviceName )
				.filter( instance -> ! instance.getHostName( ).equals( CsapApis.getInstance( ).application( )
						.getCsapHostName( ) ) )
				.map( ServiceInstance::getHostName )
				.distinct( )
				.collect( Collectors.toList( ) ) ;

	}

	public Set<String> findServiceNamesInLifecycle ( String life ) {

		Set<String> serviceNames = new HashSet<>( ) ;

		getLifeClusterToOsServices( ).keySet( ).forEach( lc -> {

			if ( lc.startsWith( life ) ) {

				serviceNames.addAll( getLifeClusterToOsServices( ).get( lc ) ) ;

			}

		} ) ;

		return serviceNames ;

	}

	/**
	 * Updates the globalModel with the contents of all the sub packages
	 *
	 * @param testGlobalModel
	 */
	protected void buildAllProjectsModel ( File rootApplicationDefinition ) {

		logger.info( "Merging all projects into '{}' for ui", Application.ALL_PACKAGES ) ;

		Project mergedPackage = new Project( getSourceDefinition( ) ) ;

		mergedPackage.setReleaseInfo( Application.ALL_PACKAGES, rootApplicationDefinition ) ;

		mergedPackage.setProjectNameToProject( getProjectNameToProject( ) ) ;

		mergedPackage.getEnvironmentNameToSettings( ).putAll( getEnvironmentNameToSettings( ) ) ;

		getProjects( )
				.forEach( model -> loadModelIntoAllModel( model, mergedPackage ) ) ;
		// Finally add the all model
		// testGlobalModel.putReleasePackage(allPackageDataModel.getReleasePackageName(),
		// allPackageDataModel);
		// setAllPackagesModel( mergedPackage ) ;

		getProjects( )
				.forEach( model -> model.setAllPackagesModel( mergedPackage ) ) ;

	}

	public Project getActiveModel ( ) {

		return activeModel ;

	}

	public Project getAllPackagesModel ( ) {

		return allPackagesModel ;

	}

	public String getArchitect ( ) {

		return architect ;

	}

	public List<String> getClusterHosts ( String clusterName ) {

		var hosts = getHostsForEnvironment( CsapApis.getInstance( ).application( ).getCsapHostEnvironmentName( ) ) ;

		if ( StringUtils.isNotEmpty( clusterName ) ) {

			hosts = getLifeClusterToHostMap( )
					.get( CsapApis.getInstance( ).application( ).getCsapHostEnvironmentName( )
							+ ProjectLoader.ENVIRONMENT_CLUSTER_DELIMITER + clusterName ) ;

		}

		return hosts ;

	}

	public Map<String, String> getClustersToTypeInCurrentLifecycle ( ) {

		var modelClustersMap = new TreeMap<String, String>( ) ;

		// critical or other lifecycles with same prefix are picked up
		var currentLifecycleName = getRootPackage( ).getHostEnvironmentName( )
				+ ProjectLoader.ENVIRONMENT_CLUSTER_DELIMITER ;

		logger.debug( "Package: {} , currentLifecycleName: {} getLifeClusterToHostMap(): {}",
				getName( ), currentLifecycleName, getLifeClusterToHostMap( ) ) ;

		// for ( String clusterName : getLifecycleToClusterMap().get( lifecycle ) ) {
		for ( String envName_clusterName : getLifeClusterToHostMap( ).keySet( ) ) {

			if ( envName_clusterName.startsWith( currentLifecycleName ) ) {

				var clusterName = envName_clusterName.substring( currentLifecycleName.length( ) ) ;
				modelClustersMap.put(
						clusterName,
						getClusterType( envName_clusterName ) ) ;

			}

		}

		return modelClustersMap ;

	}

	public Map<String, List<String>> getClustersToHostMapInCurrentLifecycle ( ) {

		var modelClustersMap = new TreeMap<String, List<String>>( ) ;

		// critical or other lifecycles with same prefix are picked up
		var currentLifecycleName = getRootPackage( ).getHostEnvironmentName( )
				+ ProjectLoader.ENVIRONMENT_CLUSTER_DELIMITER ;

		logger.debug( "Package: {} , currentLifecycleName: {} getLifeClusterToHostMap(): {}",
				getName( ), currentLifecycleName, getLifeClusterToHostMap( ) ) ;

		// for ( String clusterName : getLifecycleToClusterMap().get( lifecycle ) ) {
		for ( String lifeClusterName : getLifeClusterToHostMap( ).keySet( ) ) {

			if ( lifeClusterName.startsWith( currentLifecycleName ) ) {

				List<String> hosts = getLifeClusterToHostMap( ).get( lifeClusterName ) ;
				modelClustersMap.put( lifeClusterName.substring( currentLifecycleName.length( ) ), new ArrayList<>(
						hosts ) ) ;

			}

		}

		return modelClustersMap ;

	}

	public Map<String, List<String>> getClustersToServicesMapInCurrentLifecycle ( ) {

		Map<String, List<String>> modelClustersMap = new TreeMap<String, List<String>>( ) ;

		String lifecycle = getRootPackage( ).getHostEnvironmentName( ) ;

		logger.debug( "csapApp.getActiveModel().getLifeCycleToGroupMap(): {}",
				getLifecycleToClusterMap( ) ) ;

		for ( String clusterName : getLifecycleToClusterMap( ).get( lifecycle ) ) {

			ArrayList<String> services = new ArrayList<String>( ) ;

			// OS processes
			ArrayList<String> clusterOs = getLifeClusterToOsServices( ).get( lifecycle + clusterName ) ;

			if ( clusterOs != null ) {

				services.addAll( clusterOs ) ;

			}

			if ( services.size( ) > 0 ) {

				modelClustersMap.put( clusterName, services ) ;

			}

		}

		return modelClustersMap ;

	}

	public String getClusterType ( String envName_clusterName ) {

		return getLifeClusterToTypeMap( ).get( envName_clusterName ) ;

	}

	public String getClusterType ( String envName , String clusterName ) {

		return getLifeClusterToTypeMap( ).get( envName + ProjectLoader.ENVIRONMENT_CLUSTER_DELIMITER + clusterName ) ;

	}

	private ArrayNode getCsapServiceTemplates ( ) {

		if ( isRootPackage( ) )
			return serviceTemplates ;
		else
			return getRootPackage( ).getCsapServiceTemplates( ) ;

	}

	public String getDescription ( ) {

		return description ;

	}

	public String getEmailNotifications ( ) {

		if ( emailNotifications.equals( "support@notConfigured.com" ) || emailNotifications.length( ) == 0 ) {

			return null ;

		}

		return emailNotifications ;

	}

	public ArrayList<String> getEnvironmentAndClusterNames ( ) {

		return environmentAndClusterNames ;

	}

	public TreeMap<String, EnvironmentSettings> getEnvironmentNameToSettings ( ) {

		return environmentNameToSettings ;

	}

	public List<String> getHostsCurrentLc ( ) {

		var currentHosts = lifeCycleToHostMap.get( getRootPackage( ).getHostEnvironmentName( ) ) ;

		return currentHosts ;

	}

	public Stream<String> getHostsInActiveLifecycleStream ( ) {

		if ( lifeCycleToHostMap.get( getRootPackage( ).getHostEnvironmentName( ) ) == null ) {

			return ( new ArrayList<String>( ) ).stream( ) ;

		}

		return lifeCycleToHostMap
				.get( getRootPackage( ).getHostEnvironmentName( ) )
				.stream( ) ;

//				.map( Application::hostShortName ) ;
	}

	public ArrayList<String> getHostsInAllLifecycles ( ) {

		return hostsInAllLifecycles ;

	}

	public TreeMap<String, ArrayList<ServiceInstance>> getHostToServicesMap ( ) {

		return hostToConfigMap ;

	}

	public List<String> getHttpdTestUrls ( ) {

		return httpdTestUrls ;

	}

	public String getInfraAddHost ( ) {

		JsonNode item = getInfrastructure( ).get( "addHost" ) ;

		if ( item != null && item.isTextual( ) ) {

			return item.asText( ) ;

		}

		return "Not Configured" ;

	}

	public String getInfraCatalog ( ) {

		JsonNode item = getInfrastructure( ).get( "catalog" ) ;

		if ( item != null && item.isTextual( ) ) {

			return item.asText( ) ;

		}

		return "Not Configured" ;

	}

	public String getInfraProvider ( ) {

		JsonNode item = getInfrastructure( ).get( "provider" ) ;

		if ( item != null && item.isTextual( ) ) {

			return item.asText( ) ;

		}

		return "Not Configured" ;

	}

	/**
	 * @return the infrastructure
	 */
	public ObjectNode getInfrastructure ( ) {

		return infrastructure ;

	}

	public ObjectNode getInstanceCountInCurrentLC ( ) {

		ObjectNode resultNode = jsonMapper.createObjectNode( ) ;

		ArrayNode instanceCount = jsonMapper.createArrayNode( ) ;

		serviceNameToLifeInstancesMap
				.entrySet( )
				.stream( )
				.map( serviceListEntry -> {

					String service = serviceListEntry.getKey( ) ;
					ObjectNode serviceNode = jsonMapper.createObjectNode( ) ;
					serviceNode.put( "serviceName", service ) ;
					serviceNode.put( "count", serviceListEntry.getValue( ).size( ) ) ;

					boolean hasCustomJmx = false ;

					if ( serviceListEntry.getValue( ).size( ) >= 1 ) {

						ServiceInstance firstInstance = serviceListEntry.getValue( ).get( 0 ) ;

						serviceNode.put( "type", firstInstance.getRuntime( ) ) ;
						serviceNode.put( "script", firstInstance.is_files_only_package( ) ) ;
						serviceNode.put( "cluster", firstInstance.getCluster( ) ) ;

						if ( firstInstance.getPerformanceConfiguration( ) != null ) {

							hasCustomJmx = true ;

						}

						if ( firstInstance.is_cluster_kubernetes( ) ) {

							serviceNode.put( "kubernetes", firstInstance.is_cluster_kubernetes( ) ) ;
							serviceNode.put( "replicaCount", firstInstance.getKubernetesReplicaCount( ).asInt( 1 ) ) ;

						}

					}

					serviceNode.put( "hasCustom", hasCustomJmx ) ;

					return serviceNode ;

				} )
				.forEach( serviceNode -> instanceCount.add( serviceNode ) ) ;

		resultNode.put( "total", getInstanceTotalCountInCurrentLC( ) ) ;
		resultNode.set( "instanceCount", instanceCount ) ;

		return resultNode ;

	}

	public int getInstanceTotalCountInCurrentLC ( ) {

		int instances = (int) getServiceConfigStreamInCurrentLC( )
				.flatMap( serviceEntry -> serviceEntry.getValue( ).stream( ) )
				.count( ) ;

		return instances ;

	}

	public TreeMap<String, ArrayList<String>> getLifeClusterToHostMap ( ) {

		return lifeClusterToHostMap ;

	}

	public TreeMap<String, ArrayList<String>> getLifeClusterToOsServices ( ) {

		return lifeClusterToOsServices ;

	}

	public TreeMap<String, String> getLifeClusterToTypeMap ( ) {

		return lifeClusterToTypeMap ;

	}

	public TreeMap<String, ArrayList<String>> getLifecycleToClusterMap ( ) {

		return lifecycleToClusterMap ;

	}

	public TreeMap<String, ArrayList<HostInfo>> getEnvironmentNameToHostInfo ( ) {

		return environmentNameToHostInfo ;

	}

	public TreeMap<String, ArrayList<String>> getLifeCycleToHostMap ( ) {

		return lifeCycleToHostMap ;

	}

	public List<String> getHostsForEnvironment ( String environmentName ) {

		return Collections.unmodifiableList( getLifeCycleToHostMap( ).get( environmentName ) ) ;

	}

	public ObjectNode getSourceDefinition ( ) {

		return sourceDefinition ;

	}

	public JsonNode getImports ( String environmentName ) {

		var envPath = "/" + ProjectLoader.ENVIRONMENTS + "/" + environmentName ;
		var envSettingsPath = envPath + "/" + ProjectLoader.SETTINGS ;
		var importEnvPath = envSettingsPath + "/" + ProjectLoader.ENVIRONMENT_IMPORTS ;

		var imports = getSourceDefinition( ).at( importEnvPath ) ;

		logger.trace( "path '{}; : {}", importEnvPath, imports ) ;

		return imports ;

	}

	public ObjectNode getSource ( ) {

		ObjectNode editInProgressReport = jsonMapper.createObjectNode( ) ;
		editInProgressReport.put( "user", editUserid ) ;
		File definitionSource = new File( CsapApis.getInstance( ).application( ).getRootProjectDefinitionUrl( ) ) ;
		editInProgressReport.put( "name", definitionSource.getName( ) ) ;

		editInProgressReport.set( "source", getSourceDefinition( ) ) ;

		return editInProgressReport ;

	}

	public boolean isModified ( ) {

		return StringUtils.isNotEmpty( editUserid ) ;

	}

	// public void setEditInProgressDefinition ( JsonNode definition, String userid
	// ) {
	// editInProgressDefinition = definition ;
	// editUserid = userid ;
	//
	// }

	public ObjectNode editSource ( String userid , JsonNode definition ) {

		var changeReport = jsonMapper.createObjectNode( ) ;

		logger.debug( "updated source: {}", userid ) ;

		if ( ! definition.isNull( ) && definition.isObject( ) ) {

			editUserid = userid ;
			changeReport.put( "user", editUserid ) ;
			setSourceDefinition( (ObjectNode) definition ) ;
			changeReport.put( "errors", false ) ;

		} else {

			changeReport.put( "errors", true ) ;
			changeReport.put( "reason", "unable to parse report" ) ;

		}

		return changeReport ;

	}

	public File getSourceFile ( ) {

		return sourceFile ;

	}

	public Project getModelForService ( String serviceName ) {

		if ( logger.isDebugEnabled( ) ) {

			getProjects( ).forEach( model -> {

				logger.info( "Model: {}, services: {}", model.getName( ), model.getServiceNamesInLifecycle( ) ) ;

			} ) ;

		}

		Optional<Project> matchingPackage = getProjects( )
				.filter( model -> model.getServiceNamesInLifecycle( ).contains( serviceName ) )
				.findFirst( ) ;

		return matchingPackage.orElse( this ) ;

	}

	public Stream<Entry<String, Project>> getReleaseEntryStream ( ) {

		return projectNameToProject.entrySet( ).stream( ) ;

	}

	public File getReleaseFile ( File projectDefinitionFile ) {

		StringBuilder releaseFile = new StringBuilder( getSourceFileName( ) ) ;
		releaseFile.insert( releaseFile.indexOf( "." ), "-release" ) ;
		// Use path relative to parent to determin child
		File projectReleaseFile = new File( projectDefinitionFile.getAbsoluteFile( ).getParent( ), releaseFile
				.toString( ) ) ;
		return projectReleaseFile ;

	}

	public int getReleaseModelCount ( ) {

		return projectNameToProject.size( ) ;

	}

	private TreeMap<String, Project> getProjectNameToProject ( ) {

		return projectNameToProject ;

	}

	public Project getReleasePackage ( String packageName ) {

		return projectNameToProject.get( packageName ) ;

	}

	public String getSourceFileName ( ) {

		if ( sourceFile == null )
			return "package-file-name-not-found" ;

		return sourceFile.getName( ) ;

	}

	public Project getReleasePackageForHost ( String hostName ) {

		if ( logger.isDebugEnabled( ) ) {

			StringBuilder modelInfo = new StringBuilder( "\n\n Models for hosts:" ) ;

			getProjects( )
					.forEach( model -> {

						modelInfo.append( "\n\t model: \t" + model.getName( ) + " hosts: " ) ;
						modelInfo.append( "\t\t" + " hosts: " + model.getHostsInActiveLifecycleStream( ) ) ;

					} ) ;

			logger.debug( modelInfo.toString( ) ) ;

		}

		return getProjects( )
				.filter( model -> model.getHostsInAllLifecycles( ).contains( hostName ) )
				.findFirst( )
				.get( ) ;

	}

	// public TreeMap<String, ServiceInstance> getHostToAdminMap () {
	// return hostToAdminMap ;
	// }
	//
	// public Stream<String> getAdminHostNameStream () {
	// return hostToAdminMap.keySet().stream() ;
	// }

	public String getName ( ) {

		return name ;

	}

	public Stream<String> getReleasePackageNames ( ) {

		return releasePackagesRootFirst( )
				.map( Project::getName ) ;

	}

	public Stream<Project> getProjects ( ) {

		return projectNameToProject.values( ).stream( ) ;

	}

	public Project getRootPackage ( ) {

		return getProjectNameToProject( ).values( ).stream( )
				.filter( Project::isRootPackage )
				.findFirst( ).get( ) ;

	}

	public Stream<Entry<String, List<ServiceInstance>>> getServiceConfigStreamInCurrentLC ( ) {

		return serviceInstancesInCurrentLifeByName( ).entrySet( ).stream( ) ;

	}

	private JsonNode getServiceDefinitionInCurrentPackage ( String serviceName ) {

		var definition = getSourceDefinition( ).at( ProjectLoader.buildServicePtr( serviceName, true ) ) ;

		if ( definition.isMissingNode( ) ) {

			definition = sourceDefinition.at( ProjectLoader.buildServicePtr( serviceName, false ) ) ;

		}

		return definition ;

	}

	public ArrayNode getServiceDefinitions ( ) {

		return getServiceToAllInstancesMap( ).entrySet( ).stream( )
				.map( nameToList -> nameToList.getValue( ).get( 0 ) )
				.map( ServiceInstance::buildDefinition )
				.collect( CSAP.Collectors.toArrayNode( ) ) ;

	}

	/*
	 * gets instances in current lifecycle. Note that empty stream can be returned.
	 */
	public Stream<ServiceInstance> getServiceInstances ( String serviceName ) {

		// Return an EmptyStream.
		if ( serviceInstancesInCurrentLifeByName( ).get( serviceName ) == null ) {

			return ( new ArrayList<ServiceInstance>( ) ).stream( ) ;

		}

		return serviceInstancesInCurrentLifeByName( ).get( serviceName ).stream( ) ;

	}

	public Stream<ServiceInstance> getServiceInstancesInAllLifecycles ( String serviceName ) {

		// Return an EmptyStream.
		if ( getServiceToAllInstancesMap( ).get( serviceName ) == null ) {

			return ( new ArrayList<ServiceInstance>( ) ).stream( ) ;

		}

		return getServiceToAllInstancesMap( ).get( serviceName ).stream( ) ;

	}

	public Set<String> getServiceNamesInLifecycle ( ) {

		return serviceNameToLifeInstancesMap.keySet( ) ;

	}

	public List<String> getServiceNamesInModel ( boolean includeTemplates ) {

		List<String> serviceNames = serviceNamesInModel( includeTemplates, ProjectLoader.SERVICE_TEMPLATES ) ;

		Collections.sort( serviceNames, String.CASE_INSENSITIVE_ORDER ) ;

		return serviceNames ;

	}

	public Stream<String> getServiceNameStream ( ) {

		return serviceNameToAllInstancesMap.keySet( ).stream( ) ;

	}

	/**
	 * Get peer instances of the specified service in current lifecycle
	 *
	 * @param serviceName
	 * @return
	 */
	public List<ServiceInstance> getServicePeers ( String serviceName ) {

		logger.debug( "{} instances: {}", serviceName,
				getServiceInstances( serviceName )
						.filter( instance -> ! instance.getHostName( ).equals( CsapApis.getInstance( ).application( )
								.getCsapHostName( ) ) )
						.collect( Collectors.toList( ) ).size( ) ) ;

		return getServiceInstances( serviceName )
				.filter( instance -> ! instance.getHostName( ).equals( CsapApis.getInstance( ).application( )
						.getCsapHostName( ) ) )
				.collect( Collectors.toList( ) ) ;

	}

	public Stream<ServiceInstance> getServicesOnHost ( String hostName ) {

//		var servicesOnHost = getHostToServicesMap( ).get( Application.hostShortName( hostName ) ) ;
		var servicesOnHost = getHostToServicesMap( ).get( hostName ) ;

		if ( servicesOnHost == null ) {

			// hook for fqdn on host
			logger.warn( "No hosts found matching: {}", hostName ) ;

		}

		return servicesOnHost.stream( ) ;

	}

	public List<ServiceInstance> getServicesListOnHost ( String hostName ) {

//		return getHostToServicesMap( ).get( Application.hostShortName( hostName ) ) ;
		return getHostToServicesMap( ).get( hostName ) ;

	}

	public Stream<ServiceInstance> getServicesWithKubernetesFiltering ( String hostName ) {

//		return getHostToServicesMap( ).get( Application.hostShortName( hostName ) )
		return getHostToServicesMap( ).get( hostName )
				.stream( )
				.filter( ServiceInstance::filterInactiveKubernetesWorker ) ;

	}

	public Map<String, ArrayList<ServiceInstance>> getServiceToAllInstancesMap ( ) {

		return serviceNameToAllInstancesMap ;

	}

	public ArrayList<String> getVersionList ( ) {

		return versionList ;

	}

	public void initializeRoot ( File definitionFile , ArrayNode csapServiceTemplates ) {

		this.isRootPackage = true ;
		this.serviceTemplates = csapServiceTemplates ;

		updatePackageInformation( definitionFile ) ;

		addProject( getName( ), this ) ;

	}

	public boolean isDefinitionFromFile ( JsonNode extendedDefinition ) {

		return extendedDefinition.path( DEFINITION_SOURCE ).asText( ).equals( DEFINITION_FILES ) ;

	}

	public boolean isNameInPackageDefinitions ( String attributeName ) {

		// service check: we resolve using BOTH templates and package services - hence
		// the check
		var definition = getServiceDefinitionInCurrentPackage( attributeName ) ;

		if ( definition.isObject( ) ) {

			return true ;

		}

		// check ALL lifecycles, all attributes
		for ( var attributeDefinition : getSourceDefinition( ).findValues( attributeName ) ) {

			logger.debug( "attributeName: {} : {}", attributeName, attributeDefinition ) ;

			if ( attributeDefinition.isContainerNode( ) || attributeDefinition.isValueNode( ) ) {

				return true ;

			}

		}

		return false ;

	}

	public boolean isRootPackage ( ) {

		return isRootPackage ;

	}

	public JsonNode load_service_definition ( String serviceName ) {

		var definition = find_service_definition( serviceName ).path( DefinitionConstants.definition.key( ) ) ;

		if ( definition.isObject( ) ) {

			// legacy cleanup
			( (ObjectNode) definition ).remove( DEFINITION_SOURCE ) ;

		}

		return definition ;

	}

	private JsonNode loadExternalizedServiceDefinition ( String serviceName ) {

		JsonNode serviceDefinition = MissingNode.getInstance( ) ;
		var resourceFolder = ServiceResources.serviceResourceFolder( serviceName ) ;
		var commonDefinition = new File( resourceFolder, "/common/" + ProjectLoader.CSAP_SERVICE_FILE ) ;
		logger.debug( "Checking for definition file: {}", commonDefinition.getAbsolutePath( ) ) ;

		if ( commonDefinition.exists( ) ) {

			try {

				var processMessages = jsonMapper.createObjectNode( ) ;
				serviceDefinition = CsapApis.getInstance( ).application( ).getProjectLoader( )
						.getProjectOperators( )
						.loadYaml( commonDefinition, processMessages )
						.get( 0 ) ;

				// serviceDefinition = jacksonMapper.readTree( commonDefinition ) ;

			} catch ( Exception e ) {

				logger.warn( "Failed reading {} : {}", commonDefinition.getAbsolutePath( ), CSAP.buildCsapStack( e ) ) ;

			}

		}

		return serviceDefinition ;

	}

	private void loadModelIntoAllModel ( Project sourcePackage , Project allPackage ) {

		logger.debug( "Adding hosts {}", sourcePackage.getHostsInAllLifecycles( ) ) ;

		// allPackageDataModel.getHostToAdminMap().putAll( model.getHostToAdminMap() ) ;
		allPackage.getHostsInAllLifecycles( ).addAll( sourcePackage.getHostsInAllLifecycles( ) ) ;

		allPackage.getHostToServicesMap( ).putAll( sourcePackage.getHostToServicesMap( ) ) ;

		// For all model: common cluster host names will be merged for ui navigation
		for ( var lifeClusterName : sourcePackage.getLifeClusterToHostMap( ).keySet( ) ) {

			var allClusterMap = allPackage.getLifeClusterToHostMap( ) ;
			var sourceClusterMap = sourcePackage.getLifeClusterToHostMap( ) ;

			if ( ! allClusterMap.containsKey( lifeClusterName ) ) {

				var allhosts = new ArrayList<String>( ) ;
				allClusterMap.put( lifeClusterName, allhosts ) ;

			}

			var hostNames = sourceClusterMap.get( lifeClusterName ) ;
			Collections.sort( hostNames ) ;
			allClusterMap.get( lifeClusterName ).addAll( hostNames ) ;

		}

		for ( var hostNames : allPackage.getLifeClusterToHostMap( ).values( ) ) {

			Collections.sort( hostNames ) ;

		}

		allPackage.getLifeClusterToOsServices( ).putAll( sourcePackage.getLifeClusterToOsServices( ) ) ;

		allPackage.getEnvironmentAndClusterNames( ).addAll( sourcePackage.getEnvironmentAndClusterNames( ) ) ;
		// allPackageDataModel.getLifeCycleToGroupMap().putAll(model.getLifeCycleToGroupMap());

		for ( String lc : sourcePackage.getLifecycleToClusterMap( ).keySet( ) ) {

			logger.debug( "Adding to global groups: {}", sourcePackage.getLifecycleToClusterMap( ).get( lc ) ) ;

			if ( ! allPackage.getLifecycleToClusterMap( ).containsKey( lc ) ) {

				allPackage.getLifecycleToClusterMap( ).put( lc,
						new ArrayList<String>( ) ) ;

			}

			allPackage.getLifecycleToClusterMap( ).get( lc )
					.addAll( sourcePackage.getLifecycleToClusterMap( ).get( lc ) ) ;

		}

		logger.debug( "All Model groups: {}", allPackage.getLifecycleToClusterMap( ) ) ;
		// allPackageDataModel.getLifeCycleToHostInfoMap().putAll(
		// model.getLifeCycleToHostInfoMap());

		for ( String lc : sourcePackage.getEnvironmentNameToHostInfo( ).keySet( ) ) {

			if ( ! allPackage.getEnvironmentNameToHostInfo( ).containsKey( lc ) ) {

				allPackage.getEnvironmentNameToHostInfo( ).put( lc,
						new ArrayList<HostInfo>( ) ) ;

			}

			allPackage.getEnvironmentNameToHostInfo( ).get( lc )
					.addAll( sourcePackage.getEnvironmentNameToHostInfo( ).get( lc ) ) ;

		}

		// allPackageDataModel.getLifeCycleToHostMap().putAll(
		// model.getLifeCycleToHostMap() );
		for ( String lc : sourcePackage.getLifeCycleToHostMap( ).keySet( ) ) {

			if ( ! allPackage.getLifeCycleToHostMap( ).containsKey( lc ) ) {

				allPackage.getLifeCycleToHostMap( ).put( lc, new ArrayList<String>( ) ) ;

			}

			var hostNames = sourcePackage.getLifeCycleToHostMap( ).get( lc ) ;
			Collections.sort( hostNames ) ;
			allPackage.getLifeCycleToHostMap( ).get( lc ).addAll( hostNames ) ;

		}

		for ( var hostNames : allPackage.getLifeCycleToHostMap( ).values( ) ) {

			Collections.sort( hostNames ) ;

		}

		// allPackageDataModel.getSvcToConfigMap().putAll(
		// model.getSvcToConfigMap() );
		for ( String serviceName : sourcePackage.getServiceToAllInstancesMap( ).keySet( ) ) {

			if ( ! allPackage.getServiceToAllInstancesMap( ).containsKey( serviceName ) ) {

				allPackage.getServiceToAllInstancesMap( ).put( serviceName,
						new ArrayList<ServiceInstance>( ) ) ;

			}

			allPackage.getServiceToAllInstancesMap( ).get( serviceName )
					.addAll( sourcePackage.getServiceToAllInstancesMap( ).get( serviceName ) ) ;

		}

		// allPackageDataModel.getSvcToConfigMapCurrentLC().putAll(
		// model.getSvcToConfigMapCurrentLC() ); fff
		for ( String serviceName : sourcePackage.serviceInstancesInCurrentLifeByName( ).keySet( ) ) {

			if ( ! allPackage.serviceInstancesInCurrentLifeByName( ).containsKey( serviceName ) ) {

				allPackage.serviceInstancesInCurrentLifeByName( ).put( serviceName,
						new ArrayList<ServiceInstance>( ) ) ;

			}

			allPackage.serviceInstancesInCurrentLifeByName( ).get( serviceName )
					.addAll( sourcePackage.serviceInstancesInCurrentLifeByName( ).get( serviceName ) ) ;

		}

	}

	public void addProject ( String packageName , Project model ) {

		projectNameToProject.put( packageName, model ) ;

	}

	public Stream<Project> releasePackagesRootFirst ( ) {

		return getProjects( )
				.sorted(
						Comparator.comparing( Project::isRootPackage ).reversed( )
								.thenComparing( Project::getName, String.CASE_INSENSITIVE_ORDER ) ) ;

	}

	public TreeMap<String, List<ServiceInstance>> serviceInstancesInCurrentLifeByName ( ) {

		return serviceNameToLifeInstancesMap ;

	}

	public List<String> serviceNamesInModel ( boolean includeTemplates , String serviceType ) {

		List<String> allServiceNames = new ArrayList<>( ) ;

		if ( includeTemplates ) {

			allServiceNames = CSAP.jsonStream( getCsapServiceTemplates( ) )
					.map( template -> template.path( DefinitionConstants.definition.key( ) ) )
					.map( definition -> definition.path( serviceType ) )
					.flatMap( osDefinition -> CSAP.asStreamHandleNulls( osDefinition ) )
					.collect( Collectors.toList( ) ) ;

		}

		List<String> modelOsServices = CSAP.asStreamHandleNulls( getSourceDefinition( ).path( serviceType ) )
				.collect( Collectors.toList( ) ) ;
		allServiceNames.addAll( modelOsServices ) ;

		List<String> uniqueNames = allServiceNames.stream( ).distinct( ).collect( Collectors.toList( ) ) ;

		return uniqueNames ;

	}

	public void setActiveModel ( Project activeModel ) {

		this.activeModel = activeModel ;

	}

	public void setAllPackagesModel ( Project allPackagesModel ) {

		this.allPackagesModel = allPackagesModel ;

	}

	public void setArchitect ( String architect ) {

		this.architect = architect ;

	}

	public void setDescription ( String description ) {

		this.description = description ;

	}

	public void setEmailNotifications ( String emailNotifications ) {

		this.emailNotifications = emailNotifications ;

	}

	public void setHostsInAllLifecycles ( ArrayList<String> hostsInActiveLifecycle ) {

		this.hostsInAllLifecycles = hostsInActiveLifecycle ;

	}

	/**
	 * @param infrastructure the infrastructure to set
	 */
	public void setInfrastructure ( ObjectNode infrastructure ) {

		this.infrastructure = infrastructure ;

	}

	public void setSourceDefinition ( ObjectNode jsonModelDefinition ) {

		this.sourceDefinition = jsonModelDefinition ;

	}

	private void setReleaseInfo (
									String releasePackageName ,
									File releasePackageFile ) {

		this.name = releasePackageName ;
		this.sourceFile = releasePackageFile ;

	}

	// Children packages will get this from the parent
	// public void setReleaseModelMap(TreeMap<String, CapabilityDataModel>
	// releaseModelMap) {
	// this.releaseModelMap = releaseModelMap;
	// }
	private void setProjectNameToProject ( TreeMap<String, Project> releasePackageMap ) {

		this.projectNameToProject = releasePackageMap ;

	}

	public String summary ( ) {

		return CSAP.pad( getName( ) )
				+ CSAP.pad( getSourceFileName( ) )
				+ CSAP.pad( "services: " + getInstanceTotalCountInCurrentLC( ) ) ;

	}

	@Override
	public String toString ( ) {

		return "ReleasePackage [ httpdTestUrls="
				+ httpdTestUrls + ", architect=" + architect + ", description=" + description + ", releasePackageName="
				+ name + ", releasePackageFileName=" + getSourceFileName( )
				+ ", clusterVersionToTypeMap=" + lifeClusterToTypeMap
				+ ", environmentNames=" + environmentAndClusterNames
				+ ", versionList="
				+ versionList + "]" ;

	}

	public boolean updatePackageInformation ( File packageFile ) {

		JsonNode csapPackageSection = getSourceDefinition( ).path( ProjectLoader.PROJECT ) ;

		if ( csapPackageSection.isMissingNode( ) ) {

			logger.warn( "Did not find package definition" ) ;
			return false ;

		}

		setReleaseInfo(
				csapPackageSection.path( PARSER_RELEASE_PACKAGE_NAME )
						.asText( "missing-package-name" ),
				packageFile ) ;

		if ( csapPackageSection.has( "architect" ) ) {

			setArchitect( csapPackageSection.path( "architect" ).asText( ) ) ;

		}

		if ( csapPackageSection.has( "emailNotifications" ) ) {

			setEmailNotifications( csapPackageSection.path( "emailNotifications" ).asText( ) ) ;

		}

		if ( csapPackageSection.has( "description" ) ) {

			setDescription( csapPackageSection.path( "description" ).asText( ) ) ;

		}

		return true ;

	}

	public ObjectNode buildLbReport ( ) {

		var report = jsonMapper.createObjectNode( ) ;
		var envNameToSettings = getEnvironmentNameToSettings( ) ;

		for ( String environmentName : envNameToSettings.keySet( ) ) {

			EnvironmentSettings settings = envNameToSettings.get( environmentName ) ;

			if ( ! settings.isBaseEnvOnly( ) ) {

				report.put( environmentName, settings.getLoadbalancerUrl( ) ) ;

			}

		}

		return report ;

	}

	public JsonNode getCluster ( String environmentName , String clusterName ) {

		return getSourceDefinition( )
				.path( ProjectLoader.ENVIRONMENTS )
				.path( environmentName )
				.path( clusterName ) ;

	}

	public String getHostEnvironmentName ( ) {

		return hostEnvironmentName ;

	}

	public void setHostEnvironmentName ( String hostEnvironmentName ) {

		this.hostEnvironmentName = hostEnvironmentName ;

	}

	public String getEditUserid ( ) {

		return editUserid ;

	}

	public void setEditUserid ( String editUserid ) {

		this.editUserid = editUserid ;

	}

	public TreeMap<String, ArrayList<String>> getEnvToServiceMap ( ) {

		return envToServiceMap ;

	}

	public void setEnvToServiceMap ( TreeMap<String, ArrayList<String>> envToServiceMap ) {

		this.envToServiceMap = envToServiceMap ;

	}

	public void setDefaultKubernetes ( String serviceName , String hostName , String environmentName ) {

		// first host in provider is the global master
		if ( ! kubernetesServiceNames.containsKey( environmentName ) ) {

			this.kubernetesServiceNames.put( environmentName, serviceName ) ;
			this.kubernetesMasterHosts.put( environmentName, hostName ) ;

		}

	}

	public String getKubernetesServiceName ( String environmentName ) {

		return kubernetesServiceNames.get( environmentName ) ;

	}

	public String getKubernetesMasterHost ( String environmentName ) {

		return kubernetesMasterHosts.get( environmentName ) ;

	}

}
