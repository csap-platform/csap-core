package org.csap.agent.model ;

import java.io.File ;
import java.io.IOException ;
import java.util.ArrayList ;
import java.util.List ;
import java.util.Map ;
import java.util.Optional ;
import java.util.TreeMap ;
import java.util.concurrent.atomic.AtomicInteger ;
import java.util.regex.Matcher ;
import java.util.regex.Pattern ;
import java.util.stream.Stream ;

import org.apache.commons.lang3.StringUtils ;
import org.apache.commons.lang3.text.WordUtils ;
import org.csap.agent.container.DockerJson ;
import org.csap.agent.container.kubernetes.KubernetesIntegration ;
import org.csap.agent.container.kubernetes.KubernetesJson ;
import org.csap.agent.services.HostKeys ;
import org.csap.agent.stats.OsProcessEnum ;
import org.csap.helpers.CSAP ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;

import com.fasterxml.jackson.annotation.JsonIgnore ;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties ;
import com.fasterxml.jackson.annotation.JsonProperty ;
import com.fasterxml.jackson.core.JsonParseException ;
import com.fasterxml.jackson.core.type.TypeReference ;
import com.fasterxml.jackson.databind.JsonMappingException ;
import com.fasterxml.jackson.databind.JsonNode ;
import com.fasterxml.jackson.databind.ObjectMapper ;
import com.fasterxml.jackson.databind.node.ArrayNode ;
import com.fasterxml.jackson.databind.node.ObjectNode ;

/**
 *
 * metadata associated with each service
 *
 *
 *
 *
 * @author someDeveloper
 *
 *         JsonIgnoreProperties: provides backwards compatability when instance
 *         metrics are updated
 *
 */
@JsonIgnoreProperties ( ignoreUnknown = true )
public class ServiceInstance extends ServiceBaseParser {

	final static Logger logger = LoggerFactory.getLogger( ServiceInstance.class ) ;

	private List<ContainerState> containers = new ArrayList<>( ) ;

	// service stopped by a user - avoids alerts
	private boolean userStopped = false ;

	// Scans by Application services
	private String artifactDate = "" ;
	private ArrayNode eolJars = jacksonMapper.createArrayNode( ) ;
	private boolean fileSystemScanRequired = true ;

	public ContainerState getDefaultContainer ( ) {

		if ( containers.size( ) == 0 ) {

			containers.add( new ContainerState( "default" ) ) ;

		}

		return getContainerStatusList( ).get( 0 ) ;

	}

	public Map<String, String> getHealthUrls ( ) {

		Map<String, String> urls = new TreeMap<>( String.CASE_INSENSITIVE_ORDER ) ;

		AtomicInteger containerIndex = new AtomicInteger( 0 ) ;

		if ( is_cluster_kubernetes( ) ) {

			getContainerStatusList( ).stream( ).forEach( container -> {

				containerIndex.addAndGet( 1 ) ;
				urls.put( getHostName( ) + "-" + containerIndex.get( ),
						getHealthUrl( getName( ) + "-" + containerIndex.get( ) ) ) ;

			} ) ;

		} else {

			urls.put( getHostName( ) + ":" + getPort( ), getHealthUrl( null ) ) ;

		}

		return urls ;

	}

	public Stream<String> getIds ( ) {

		List<String> serviceIds = new ArrayList<>( ) ;

		AtomicInteger containerIndex = new AtomicInteger( 0 ) ;

		if ( is_cluster_kubernetes( ) ) {

			getContainerStatusList( ).stream( ).forEach( container -> {

				containerIndex.addAndGet( 1 ) ;
				serviceIds.add( getName( ) + "-" + containerIndex.get( ) ) ;

			} ) ;

		} else {

			serviceIds.add( getName( ) ) ;

		}

		return serviceIds.stream( ) ;

	}

	public Map<String, String> getHealthUrls ( JsonNode serviceCollected ) {

		if ( isApplicationHealthEnabled( ) ) {

			return getHealthUrls( ) ;

		} else {

			logger.debug( "{} : {}", toSummaryString( ), CSAP.jsonPrint( serviceCollected ) ) ;
			// kubernetes health is dynamic based on deployment host
			Map<String, String> healthUrls = null ;

			if ( serviceCollected.isObject( ) ) {

				try {

					ServiceInstance serviceWithRuntimeStats = ServiceInstance.buildInstance( jacksonMapper,
							serviceCollected ) ;
					var containerIndex = 0 ;

					for ( ContainerState containerState : serviceWithRuntimeStats.getContainerStatusList( ) ) {

						if ( containerState.isActive( ) ) {

							containerIndex++ ;

							if ( healthUrls == null ) {

								healthUrls = new TreeMap<>( String.CASE_INSENSITIVE_ORDER ) ;

							}

							healthUrls.put( getHostName( ) + "-" + containerIndex,
									getHealthUrl( getName( ) + "-" + containerIndex ) ) ;

						}

					}

				} catch ( IOException e ) {

					logger.warn( "Failed to parse service runtime: {}", CSAP.buildCsapStack( e ) ) ;

				}

			}

			return healthUrls ;

		}

	}

	public void append_summary_information ( StringBuilder matchSummary ) {

		if ( is_cluster_kubernetes( ) ) {

			getContainerStatusList( ).stream( ).forEach( resource -> {

				matchSummary.append( "\n" ) ;
				matchSummary.append( StringUtils.leftPad( getName( ), 20 ) ) ;
				matchSummary.append( StringUtils.rightPad( " - pid(s): " + resource.getPid( ), 40 ) ) ;
				matchSummary.append( "filter: " ) ;
				matchSummary.append( getProcessFilter( ) ) ;
				matchSummary.append( "\t container: " ) ;
				matchSummary.append( resource.getContainerName( ) ) ;

			} ) ;

		} else {

			matchSummary.append( "\n" ) ;
			matchSummary.append( StringUtils.leftPad( getName( ), 20 ) ) ;
			matchSummary.append( StringUtils.rightPad( " - pid(s): " + getDefaultContainer( ).getPid( ), 40 ) ) ;
			matchSummary.append( "filter: " + getProcessFilter( ) ) ;

		}

	}

	public boolean filterInactiveKubernetesWorker ( ) {

		boolean filterInstance = false ;

		if ( is_cluster_kubernetes( )
				&& ! getDefaultContainer( ).isRunning( )
				&& ! isKubernetesMaster( ) ) {

			filterInstance = true ;

		}

		return ! filterInstance ;

	}

	/**
	 * used by deploy manager to poll service state from hosts
	 *
	 * reloaded in admin ServiceInstance serviceInstance = jacksonMapper.readValue(
	 * serviceNode.get( serviceInstanceName ).traverse(), ServiceInstance.class );
	 *
	 * @return
	 * @throws IOException
	 * @throws JsonMappingException
	 * @throws JsonParseException
	 */

	public static ServiceInstance buildInstance ( ObjectMapper jsonMapper , JsonNode runtimeStatus )
		throws IOException {

		logger.debug( "Loading: {}", CSAP.jsonPrint( runtimeStatus ) ) ;

		ServiceInstance instance = jsonMapper.readValue(
				runtimeStatus.traverse( ),
				ServiceInstance.class ) ;

		var runtimeClusterType = runtimeStatus.path( ClusterType.CLUSTER_TYPE ).asText( ) ;

		if ( StringUtils.isNotEmpty( runtimeClusterType ) ) {

			ObjectNode wrapperForLookup = jsonMapper.createObjectNode( ) ;
			wrapperForLookup.put( "type", runtimeClusterType ) ;
			instance.setClusterType( ClusterType.getPartitionType( wrapperForLookup ) ) ;

		}

		var kubernetesMasterHostNames = runtimeStatus.path( KubernetesJson.kubernetesMasterHostNames.json( ) ) ;

		if ( kubernetesMasterHostNames.isArray( ) ) {

			instance.setKubernetesMasterHostNames( (ArrayNode) kubernetesMasterHostNames ) ;

		}

		JsonNode runContainers = runtimeStatus.path( HostKeys.containers.jsonId ) ;

		if ( runContainers.isArray( ) ) {

			CSAP.jsonStream( runContainers ).forEach( container -> {

				JsonNode resourceViolations = container.path( "resourceViolations" ) ;

				if ( ! resourceViolations.isMissingNode( ) && ! resourceViolations.isArray( ) ) {

					// legacy handling
					( (ObjectNode) container ).set( "resourceViolations", jsonMapper.createArrayNode( ) ) ;

				}

			} ) ;
			List<ContainerState> containers = jsonMapper.readValue(
					runContainers.toString( ), new TypeReference<List<ContainerState>>( ) {
					} ) ;

			instance.mergeContainerData( containers ) ;

		} else {

			logger.warn( "{}: No container state found", instance.getName( ) ) ;

		}

		return instance ;

	}

	@JsonIgnore
	public ObjectNode build_ui_service_instance ( ContainerState container , int containerIndex ) {

		ObjectNode runStatus = getCoreRuntime( ) ;

		ObjectNode serviceResources = jacksonMapper.convertValue( container, ObjectNode.class ) ;

		if ( Application.getInstance( ).isAgentProfile( ) ) {

			// handle rssMemory mapping to MB
			serviceResources.put( OsProcessEnum.rssMemory.value,
					serviceResources.path( OsProcessEnum.rssMemory.value ).asLong( ) / 1024 ) ;

		}

		logger.debug( "serviceResources: {}", CSAP.jsonPrint( serviceResources ) ) ;
		runStatus.put( "containerIndex", containerIndex ) ;
		runStatus.setAll( serviceResources ) ;

		return runStatus ;

	}

	JsonNode resolvedContainerLocator = null ;

	public void resetLocatorAndMatching ( ) {

		resolvedContainerLocator = null ;
		containers = new ArrayList<>( ) ;

	}

	@JsonIgnore
	public JsonNode getResolvedLocators ( ) {

		if ( resolvedContainerLocator == null ) {

			var settings = getDockerSettingsOrMissing( ).path( DockerJson.locator.json( ) ) ;
			resolvedContainerLocator = settings ;

			if ( settings.isObject( ) ) {

				var rawLocator = Application.getInstance( ).resolveDefinitionVariables(
						settings.toString( ), this ) ;

				try {

					resolvedContainerLocator = jacksonMapper.readTree( rawLocator ) ;
					logger.debug( "{} Lazy locator init: {} ", getName( ), resolvedContainerLocator.toString( ) ) ;

				} catch ( Exception e ) {

					logger.warn( "Failed parsing", rawLocator ) ;

				}

			}

		}

		return resolvedContainerLocator ;

	}

	@JsonIgnore
	public ObjectNode buildRuntimeState ( ) {

		ObjectNode runStatus = getCoreRuntime( ) ;

		ArrayNode containers = jacksonMapper.convertValue( getContainerStatusList( ), ArrayNode.class ) ;
		runStatus.set( ContainerState.JSON_KEY, containers ) ;

		return runStatus ;

	}

	public ObjectNode buildDefinition ( ) {

		ObjectNode runStatus = jacksonMapper.createObjectNode( ) ;

		runStatus.put( "serviceName", getName( ) ) ;
		runStatus.put( "serverType", getServerQualifedType( ) ) ;
		runStatus.put( ClusterType.CLUSTER_TYPE, getClusterType( ).getJson( ) ) ;

		runStatus.put( ServiceBase.HOSTNAME_JSON, getHostName( ) ) ;
		runStatus.put( "port", getPort( ) ) ;

		if ( isJavaJmxCollectionEnabled( ) ) {

			runStatus.put( "jmxPort", getJmxPort( ) ) ;

		}

		// if ( isTomcatPackaging() ) {
		// runStatus.put( "servletThreadCount", getServletThreadCount() ) ;
		// runStatus.put( "servletAccept", getServletAccept() ) ;
		// runStatus.put( "servletMaxConnections", getServletMaxConnections() ) ;
		// runStatus.put( "servletTimeoutMs", getServletTimeoutMs() ) ;
		// }

		return runStatus ;

	}

	@JsonIgnore
	public ObjectNode getCoreRuntime ( ) {

		ObjectNode runStatus = buildDefinition( ) ;

		runStatus.put( USER_STOP, isUserStopped( ) ) ;
		runStatus.put( "scmVersion", getScmVersion( ) ) ;
		runStatus.put( "artifactDate", getArtifactDate( ) ) ;

		if ( getKubernetesMasterHostNames( ) != null ) {

			runStatus.set( KubernetesJson.kubernetesMasterHostNames.json( ), getKubernetesMasterHostNames( ) ) ;

		}

		runStatus.set( "eolJars", getEolJars( ) ) ;
		runStatus.put( "context", getContext( ) ) ;

		runStatus.put( "iconType", getServerUiIconType( ) ) ;
		runStatus.put( "user", getUser( ) ) ;

		if ( isRemoteCollection( ) ) {

			runStatus.put( "deployedArtifacts", "Remote Managed" ) ;

		}

		return runStatus ;

	}

	public String getArtifactDate ( ) {

		return artifactDate ;

	}

	public void setArtifactDate ( String warDate ) {

		this.artifactDate = resolveTemplateVariables( warDate ) ;

	}

	/**
	 * @return the userStopped
	 */
	public boolean isUserStopped ( ) {

		return userStopped ;

	}

	/**
	 * @param userStopped the userStopped to set
	 */

	@JsonProperty ( USER_STOP )
	public void setUserStopped ( boolean userStopped ) {

		this.userStopped = userStopped ;

	}

	public void updateServiceManagementState ( File csapProcessingDirectory ) {

		File serviceUserStopFile = new File( csapProcessingDirectory, getStoppedFileName( ) ) ;
		File serviceFolder = new File( csapProcessingDirectory, getName( ) ) ;

		File serviceStartLog = new File( serviceFolder.getAbsolutePath( ) + "_start.log" ) ;

		// setUserStopped( false ) ;
		if ( ! getDefaultContainer( ).isRunning( )
				&& ( serviceFolder.exists( ) || serviceStartLog.exists( ) ) //
				&& ! serviceUserStopFile.exists( ) ) {

			setUserStopped( false ) ;

		} else {

			setUserStopped( true ) ;

		}

		logger.debug( "running: {} serviceUserStopFile: {}, userStopped: {}, serviceStartLog: {}, exists: {}",
				getDefaultContainer( ).isRunning( ),
				serviceUserStopFile.getAbsolutePath( ), isUserStopped( ),
				serviceStartLog.getAbsolutePath( ), serviceStartLog.exists( ) ) ;

	}

	/**
	 * @return the eolJars
	 */
	public ArrayNode getEolJars ( ) {

		return eolJars ;

	}

	/**
	 * @param eolJars the eolJars to set
	 */
	public void setEolJars ( ArrayNode eolJars ) {

		this.eolJars = eolJars ;

	}

	/**
	 * @return the fileSystemScanRequired
	 */
	public boolean isFileSystemScanRequired ( ) {

		String threadName = Thread.currentThread( ).getName( ).toLowerCase( ) ;

		if ( threadName.contains( "main" ) ) {

			logger.debug( "Skipping scan on main thread to improve startup: {}, {}",
					getName( ),
					CSAP.buildCsapStack( new Exception( "stack" ) ) ) ;
			return false ;

		}

		return fileSystemScanRequired ;

	}

	/**
	 * @param fileSystemScanRequired the fileSystemScanRequired to set
	 */
	public void setFileSystemScanRequired ( boolean fileSystemScanRequired ) {

		this.fileSystemScanRequired = fileSystemScanRequired ;

	}

	static public ServiceInstance buildUnregistered ( String host , String discoveryReportCsv ) {

		String containerOrPodMatchName = discoveryReportCsv ;
		String containerName = null ;
		String podName = null ;
		String podNameSpace = null ;

		String[] s = discoveryReportCsv.split( Pattern.quote( "," ) ) ;

		if ( s.length == 4 ) {

			containerOrPodMatchName = s[0] ;
			if ( StringUtils.isNotEmpty( s[1] ) )
				containerName = s[1] ;
			if ( StringUtils.isNotEmpty( s[2] ) )
				podName = s[2] ;
			if ( StringUtils.isNotEmpty( s[3] ) )
				podNameSpace = s[3] ;

		}

		ServiceInstance unregisteredInstance = new ServiceInstance( ) ;
		String csapServiceName = containerOrPodMatchName.replaceAll( Matcher.quoteReplacement( "/" ), "" ) ;

		if ( StringUtils.isNotEmpty( podName ) ) {

			csapServiceName = podName ; // services passed as a map - key must be unique

		}

		unregisteredInstance.setName( csapServiceName ) ;
		unregisteredInstance.setLifecycle( Application.getInstance( ).getCsapHostEnvironmentName( ) ) ;
		unregisteredInstance.setHostName( host ) ;
		unregisteredInstance.setProcessRuntime( ProcessRuntime.unregistered.getId( ) ) ;
		unregisteredInstance.setProcessFilter( "dummy" ) ;
		var containerStatus = unregisteredInstance.addContainerStatus( containerName ) ; // initializes container to be
																							// inactive

		if ( StringUtils.isNotEmpty( podName ) ) {

			containerStatus.setPodNamespace( podNameSpace ) ;
			containerStatus.setPodName( podName ) ;
			containerStatus.setContainerLabel( containerOrPodMatchName ) ;

		}

		unregisteredInstance.setUrl( KubernetesIntegration.getDefaultUrl( host ) ) ;
		return unregisteredInstance ;

	}

	public List<ContainerState> getContainerStatusList ( ) {

		return containers ;

	}

	public List<String> getCsapKubePodNames ( ) {

		List<String> containerNames = new ArrayList<>( ) ;

		if ( is_cluster_kubernetes( ) ) {

			for ( int container = 1; container <= getContainerStatusList( ).size( ); container++ ) {

				containerNames.add( getName( ) + "-" + container ) ;

			}

		}

		return containerNames ;

	}

	public String findContainerName ( String csapKubePodName ) {

		var containerName = "" ;

		for ( int container = 1; container <= getContainerStatusList( ).size( ); container++ ) {

			var currentName = getName( ) + "-" + container ;

			if ( currentName.equals( csapKubePodName ) ) {

				containerName = getContainerStatusList( ).get( container - 1 ).getContainerName( ) ;
				break ;

			}

		}

		return containerName ;

	}

	public Optional<ContainerState> findPodContainer ( String csapKubePodName ) {

		Optional<ContainerState> containerState = Optional.empty( ) ;
		;

		for ( int container = 1; container <= getContainerStatusList( ).size( ); container++ ) {

			var currentName = getName( ) + "-" + container ;

			if ( currentName.equals( csapKubePodName ) ) {

				containerState = Optional.of( getContainerStatusList( ).get( container - 1 ) ) ;
				break ;

			}

		}

		return containerState ;

	}

	@JsonIgnore
	public void mergeContainerData ( List<ContainerState> updatedContainers ) {
		// preserve collected stats.

		getContainerStatusList( ).stream( ).forEach( existingContainer -> {

			updatedContainers.stream( )
					.filter( updatedContainer -> {

						return updatedContainer.getContainerName( ).equals( existingContainer.getContainerName( ) ) ;

					} )
					.findFirst( )
					.ifPresent( updatedContainer -> {

						// preserving collect stats
						updatedContainer.setTopCpu( existingContainer.getTopCpu( ) ) ;
						updatedContainer.setHealthReportCollected( existingContainer.getHealthReportCollected( ) ) ;
						updatedContainer.setDiskReadKb( existingContainer.getDiskReadKb( ) ) ;
						updatedContainer.setDiskWriteKb( existingContainer.getDiskWriteKb( ) ) ;
						updatedContainer.setNumTomcatConns( Long.valueOf( existingContainer.getNumTomcatConns( ) ) ) ;
						updatedContainer.setJvmThreadCount( Long.valueOf( existingContainer.getJvmThreadCount( ) ) ) ;
						updatedContainer.setJmxHeartbeatMs( existingContainer.getJmxHeartbeatMs( ) ) ;
						updatedContainer.setSocketCount( existingContainer.getSocketCount( ) ) ;
						updatedContainer.setFileCount( existingContainer.getFileCount( ) ) ;
						updatedContainer.setResourceViolations( existingContainer.getResourceViolations( ) ) ;
						updatedContainer.setDeployedArtifacts( existingContainer.getDeployedArtifacts( ) ) ;
						updatedContainer.setPodNamespace( existingContainer.getPodNamespace( ) ) ;
						updatedContainer.setContainerLabel( existingContainer.getContainerLabel( ) ) ;
						updatedContainer.setPodIp( existingContainer.getPodIp( ) ) ;

					} ) ;

		} ) ;

		this.containers = updatedContainers ;

	}

	public ContainerState addContainerStatus ( String containerName ) {

		ContainerState container = new ContainerState( containerName ) ;
		getContainerStatusList( ).add( container ) ;
		return container ;

	}

	public String details ( ) {

		String details = getServiceName_Port( )
				+ " ServiceInstance "
				+ "\n\t\t user stopped: " + isUserStopped( )
				+ "\n\t\t containers: " + containers + "]" ;

		return WordUtils.wrap( details, 140, "\n\t\t", false ) + "\n\t" + super.toString( ) ;

	}

	@Override
	public String toString ( ) {

		// used in junits - do not modify
		return getServiceName_Port( ) + " on host: " + getHostName( ) + " cluster: " + getCluster( ) + " containers: "
				+ getContainerStatusList( ).size( ) ;

	}

}
