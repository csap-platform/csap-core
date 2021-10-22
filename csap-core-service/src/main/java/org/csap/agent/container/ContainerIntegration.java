package org.csap.agent.container ;

import java.io.ByteArrayOutputStream ;
import java.io.File ;
import java.io.FileInputStream ;
import java.io.FileOutputStream ;
import java.io.IOException ;
import java.io.InputStream ;
import java.nio.charset.StandardCharsets ;
import java.nio.file.Files ;
import java.nio.file.Paths ;
import java.nio.file.StandardCopyOption ;
import java.time.Instant ;
import java.time.LocalDateTime ;
import java.time.ZoneId ;
import java.time.format.DateTimeFormatter ;
import java.util.ArrayList ;
import java.util.Arrays ;
import java.util.HashMap ;
import java.util.List ;
import java.util.Map ;
import java.util.Objects ;
import java.util.Optional ;
import java.util.concurrent.CountDownLatch ;
import java.util.concurrent.TimeUnit ;
import java.util.regex.Matcher ;
import java.util.regex.Pattern ;
import java.util.stream.Collectors ;

import javax.cache.Cache ;
import javax.servlet.ServletOutputStream ;
import javax.servlet.http.HttpServletResponse ;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry ;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream ;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream ;
import org.apache.commons.io.FileUtils ;
import org.apache.commons.io.FilenameUtils ;
import org.apache.commons.io.IOUtils ;
import org.apache.commons.lang3.StringUtils ;
import org.csap.agent.CsapCore ;
import org.csap.agent.DockerConfiguration ;
import org.csap.agent.DockerSettings ;
import org.csap.agent.container.kubernetes.KubernetesJson ;
import org.csap.agent.linux.OsCommandRunner ;
import org.csap.agent.linux.OutputFileMgr ;
import org.csap.agent.model.Application ;
import org.csap.agent.model.ProjectLoader ;
import org.csap.agent.model.ServiceInstance ;
import org.csap.agent.services.OsCommands ;
import org.csap.agent.services.ServiceOsManager ;
import org.csap.helpers.CSAP ;
import org.csap.helpers.CsapApplication ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.ui.ModelMap ;

import com.fasterxml.jackson.core.JsonParseException ;
import com.fasterxml.jackson.core.JsonProcessingException ;
import com.fasterxml.jackson.core.type.TypeReference ;
import com.fasterxml.jackson.databind.JsonMappingException ;
import com.fasterxml.jackson.databind.JsonNode ;
import com.fasterxml.jackson.databind.ObjectMapper ;
import com.fasterxml.jackson.databind.SerializationFeature ;
import com.fasterxml.jackson.databind.node.ArrayNode ;
import com.fasterxml.jackson.databind.node.ObjectNode ;
import com.github.dockerjava.api.DockerClient ;
import com.github.dockerjava.api.async.ResultCallback ;
import com.github.dockerjava.api.command.CreateContainerCmd ;
import com.github.dockerjava.api.command.CreateContainerResponse ;
import com.github.dockerjava.api.command.CreateNetworkResponse ;
import com.github.dockerjava.api.command.CreateVolumeResponse ;
import com.github.dockerjava.api.command.ExecCreateCmdResponse ;
import com.github.dockerjava.api.command.InspectContainerResponse ;
import com.github.dockerjava.api.command.InspectImageResponse ;
import com.github.dockerjava.api.command.InspectVolumeResponse ;
import com.github.dockerjava.api.command.ListVolumesResponse ;
import com.github.dockerjava.api.command.LogContainerCmd ;
import com.github.dockerjava.api.command.PullImageResultCallback ;
import com.github.dockerjava.api.exception.NotFoundException ;
import com.github.dockerjava.api.model.AccessMode ;
import com.github.dockerjava.api.model.Bind ;
import com.github.dockerjava.api.model.Container ;
import com.github.dockerjava.api.model.ExposedPort ;
import com.github.dockerjava.api.model.Frame ;
import com.github.dockerjava.api.model.HostConfig ;
import com.github.dockerjava.api.model.Image ;
import com.github.dockerjava.api.model.Info ;
import com.github.dockerjava.api.model.LogConfig ;
import com.github.dockerjava.api.model.LogConfig.LoggingType ;
import com.github.dockerjava.api.model.Network ;
import com.github.dockerjava.api.model.Ports ;
import com.github.dockerjava.api.model.PullResponseItem ;
import com.github.dockerjava.api.model.RestartPolicy ;
import com.github.dockerjava.api.model.SELContext ;
import com.github.dockerjava.api.model.Ulimit ;
import com.github.dockerjava.api.model.Volume ;

import io.micrometer.core.instrument.Timer ;

public class ContainerIntegration {

	public static final String CRIO_COMMAND_NOT_IMPLMENTED = "crio-command-not-implmented" ;

	private static final String CSAP_DOCKER_METER = "csap.container.docker-" ;

	private final static String DOCKER_VOLUMES_SCRIPT = "bin/collect-docker-volumes.sh" ;

	public static final String DEPLOY_TIMER = CSAP_DOCKER_METER + "deploy" ;

	public static final String SUMMARY_TIMER = CSAP_DOCKER_METER + "summary" ;

	public static final String IO_KUBERNETES_CONTAINER_NAME = "io.kubernetes.container.name" ;

	public static final String CLI_COMMAND = "docker run" ;

	public static final String DOCKER_LOG_HOST = "//" ;

	public static final String SYSTEM_DF = "/system/df" ;

	final static Logger logger = LoggerFactory.getLogger( ContainerIntegration.class ) ;

	public static final String SKIPPING_VOLUME_CREATE = "Skipping volume create because volume already exists" ;
	public static final String SKIPPING_NETWORK_CREATE = "Skipping network create because network already exists" ;

	public static final String DOCKER_DEFAULT_IMAGE = "docker.io/hello-world:latest" ;

	public static final String MISSING_FILE_NAME = "ERROR_containerNotFound_" ;
	private static final String MISSING_FILE_LINE = "frwxr-xr-x.  18 root root     4096 Apr 14 17:58 "
			+ MISSING_FILE_NAME ;
	public static final String UNABLE_TO_CONNECT_TO_DOCKER = "Unable to connect to docker - ensure CSAP docker package is installed." ;

	private CountDownLatch pullLatch = new CountDownLatch( 0 ) ;

	private boolean pullSuccess = false ;

	volatile private StringBuilder latest_pull_results = new StringBuilder( ) ;
	private volatile String lastImage = "" ;
	private volatile OutputFileMgr _pullOutputManager ;

	private static long MAX_PROGRESS = 1024 * 500 ;

	private DockerConfiguration dockerConfig ;
	private ObjectMapper jsonMapper ;
	private DockerClient dockerClient ;
	private DockerSettings settings ;
	private OsCommands osCommands ;

	public ContainerIntegration (
			DockerConfiguration dockerConfig,
			ObjectMapper notUsedMapper ) {

		this.dockerConfig = dockerConfig ;
		this.settings = dockerConfig.getDocker( ) ;
		// this.jsonMapper = jsonMapper ;

		this.jsonMapper = new ObjectMapper( ) ;
		ProjectLoader.addCsapJsonConfiguration( jsonMapper ) ;
		// docker 3.2.x marching of image listing requires
		jsonMapper.configure( SerializationFeature.FAIL_ON_EMPTY_BEANS, false ) ;

		this.dockerClient = dockerConfig.dockerClient( ) ;
		this.osCommands = dockerConfig.getOsCommands( ) ;

	}

	volatile private boolean foundPullError = false ;

	public class PullHandler extends PullImageResultCallback {

		String lastStatus = "" ;

		@Override
		public void onNext ( PullResponseItem item ) {

			logger.debug( "response: {} ", item ) ;

			if ( lastStatus.length( ) != item.getStatus( ).length( ) ) {

				lastStatus = item.getStatus( ) ;
				logger.debug( "PullResponseItem - onNext : {} ", item.getStatus( ) ) ;

			}

			String progress = item.getStatus( ) ;

			if ( item.getErrorDetail( ) != null ) {

				progress = item.getErrorDetail( ).getMessage( ) ;
				setFoundPullError( true ) ;

			} else if ( item.getProgressDetail( ) != null && item.getProgressDetail( ).getCurrent( ) != null ) {

				progress += "..." + Math.round( item.getProgressDetail( ).getCurrent( ) * 100 / item
						.getProgressDetail( ).getTotal( ) ) + "%" ;

			}

			if ( get_pullOutputManager( ) != null ) {

				get_pullOutputManager( ).print( progress ) ;

			}

			if ( latest_pull_results.length( ) < MAX_PROGRESS ) {

				// System.out.println( progress );
				latest_pull_results.append( "\n" + progress ) ;

			} else {

				logger.warn( "MAX progress messages exceeded: {}", MAX_PROGRESS ) ;

			}

		}

		@Override
		public void onComplete ( ) {
			// TODO Auto-generated method stub

			logger.info( "lastStatus: {}", lastStatus ) ;

			logger.debug( "onComplete: {} ", Boolean.toString( isFoundPullError( ) ) ) ;

			if ( ! isFoundPullError( ) ) {

				setPullSuccess( true ) ;

				if ( get_pullOutputManager( ) != null ) {

					get_pullOutputManager( ).print( ServiceOsManager.BUILD_SUCCESS ) ;

				}

			}

			super.onComplete( ) ;

			// only a single volatile is needed to set scope
			pullLatch.countDown( ) ;

		}

		@Override
		protected void finalize ( )
			throws Throwable {

			logger.debug( "\n\n\n  ************** JAVA GC ***************** \n\n" ) ;

		}

		public String getLastStatus ( ) {

			return lastStatus ;

		}
	}

	private PullHandler buildPullHandler ( String imageName , OutputFileMgr pullOutput ) {

		PullHandler pullHandler = new PullHandler( ) ;
		pullLatch = new CountDownLatch( 1 ) ;
		setPullSuccess( false ) ;
		setFoundPullError( false ) ;
		setLastImage( imageName ) ;
		set_pullOutputManager( pullOutput ) ;
		latest_pull_results.setLength( 0 ) ;

		return pullHandler ;

	}

	public boolean isPullInProgress ( ) {

		return pullLatch.getCount( ) > 0 ;

	}

	public ObjectNode imageInfo ( String name ) {

		ObjectNode result = jsonMapper.createObjectNode( ) ;

		try {

			Optional<Image> match = findImageByName( name ) ;

			if ( match.isPresent( ) ) {

				InspectImageResponse info = dockerClient.inspectImageCmd( match.get( ).getId( ) ).exec( ) ;
				result = jsonMapper.convertValue( info, ObjectNode.class ) ;

			} else {

				result.put( DockerJson.error.json( ), "Failed to locate image with name: " + name ) ;

			}

		} catch ( Exception e ) {

			result = buildErrorResponse( "Failed tailing: " + name, e ) ;

		}

		return result ;

	}

	private Optional<Image> findImageByName ( String name ) {

		List<Image> images = dockerClient.listImagesCmd( ).exec( ) ;

		Optional<Image> matchImage = images
				.stream( )
				.filter( image -> {

					return image.getRepoTags( ) != null ;

				} )
				.filter( image -> {

					return Arrays.asList( image.getRepoTags( ) ).contains( name ) ;

				} )
				.findFirst( ) ;
		return matchImage ;

	}

	public boolean is_docker_logging ( ServiceInstance service ) {

		// logs starting with // escape the default source
		if ( service.is_docker_server( )
				&& ! service.getLogDirectory( ).startsWith( DOCKER_LOG_HOST ) )
			return true ;

		if ( service.isRunUsingDocker( )
				&& service.is_os_process_monitor( )
				&& ! service.getLogDirectory( ).startsWith( DOCKER_LOG_HOST ) )
			return true ;

		if ( service.isRunUsingDocker( )
				&& service.is_java_application_server( )
				&& service.getLogDirectory( ).startsWith( DOCKER_LOG_HOST ) ) {

			return true ;

		}

		return false ;

	}

	public boolean is_docker_folder ( ServiceInstance service ) {

		if ( service.is_docker_server( ) ) {

			return true ;

		}

		if ( service.isRunUsingDocker( )
				&& service.is_os_process_monitor( ) ) {

			return true ;

		}

		if ( service.isRunUsingDocker( )
				&& service.is_java_application_server( )
				&& service.getAppDirectory( ).startsWith( DOCKER_LOG_HOST ) ) {

			return true ;

		}

		return false ;

	}

	public static String getProcessMatch ( ServiceInstance service ) {

		String processMatch = service.getDockerContainerPath( ) ;

		if ( service.getDockerSettings( ) == null ) {

			return processMatch ;

		}

		JsonNode resolvedLocators = service.getResolvedLocators( ) ;

		if ( resolvedLocators.isObject( ) ) {

			// lazy resolution of variables
			processMatch = resolvedLocators.path( "value" ).asText( "missingLocatorValue" ) ;

		} else if ( service.is_cluster_kubernetes( ) ) {

			processMatch = generatedKubernetesContainerName( service ) ;

		}
//		logger.info( "processMatch: '{}'", processMatch );

		return processMatch ;

	}

	private static String generatedKubernetesContainerName ( ServiceInstance service ) {

		return getNetworkSafeContainerName( service.getDockerContainerName( ) + DockerJson.k8ContainerSuffix.json( ) ) ;

	}

	public String determineDockerContainerName ( ServiceInstance service ) {

		var dockerContainerName = service.getDockerContainerPath( ) ;

		logger.info( "{} default container: {}, isRunUsingDocker: {} kubernetes: {}",
				service.getName( ), dockerContainerName, service.isRunUsingDocker( ), service
						.is_cluster_kubernetes( ) ) ;

		if ( service.isRunUsingDocker( ) || service.is_cluster_kubernetes( ) ) {

			JsonNode locator = service.getDockerSettings( ).path( DockerJson.locator.json( ) ) ;

			if ( locator.isContainerNode( ) ) {

				// String labelType = locator.path( "type" ).asText(
				// IO_KUBERNETES_CONTAINER_NAME ) ;
				String labelType = IO_KUBERNETES_CONTAINER_NAME ;
				String labelValue = Application.getInstance( ).resolveDefinitionVariables(
						locator.path( "value" ).asText( "missingLocatorValue" ), service ) ;

				Optional<Container> container = findContainerUsingLabel( labelType, labelValue ) ;

				if ( container.isPresent( ) ) {

					dockerContainerName = container.get( ).getNames( )[ 0 ] ;

				}

			} else if ( service.is_cluster_kubernetes( ) ) {

				String labelType = IO_KUBERNETES_CONTAINER_NAME ;
				String labelValue = generatedKubernetesContainerName( service ) ;

				Optional<Container> container = findContainerUsingLabel( labelType, labelValue ) ;

				if ( container.isPresent( ) ) {

					dockerContainerName = container.get( ).getNames( )[ 0 ] ;

				}

			}

		}

		return dockerContainerName ;

	}

	public String findDockerContainerId ( String podName , String podContainerLabel ) {

		logger.debug( "podName: '{}',  containerName: '{}'", podName, podContainerLabel ) ;

		String dockerContainerId = null ;

		try {

			List<Container> containers = dockerClient.listContainersCmd( ).withShowAll( true ).exec( ) ;

			if ( logger.isDebugEnabled( ) ) {

				String containerInfo = containers
						.stream( )
						.map( container -> {

							return "Names: " + Arrays.asList( container.getNames( ) ) ;

						} )
						.collect( Collectors.joining( "\n" ) ) ;
				logger.info( "resolved: {}", containerInfo ) ;

			}

			dockerContainerId = containers
					.stream( )
					.filter( container -> container.getLabels( ).containsKey( "io.kubernetes.pod.name" ) )
					.filter( container -> container.getLabels( ).get( "io.kubernetes.pod.name" ).equals( podName ) )
					.filter( container -> container.getLabels( ).containsKey( IO_KUBERNETES_CONTAINER_NAME ) )
					.filter( container -> container.getLabels( ).get( IO_KUBERNETES_CONTAINER_NAME ).equals(
							podContainerLabel ) )
					.findFirst( )
					.map( container -> {

						return container.getNames( )[ 0 ] ;

					} )
					.orElseGet( ( ) -> {

						return null ;

					} ) ;

		} catch ( Exception e ) {

			logger.info( "Failed finding container: {}", CSAP.buildCsapStack( e ) ) ;

		}

		logger.info( "podName: '{}',  containerName: '{}' resolved id: {}", podName, podContainerLabel,
				dockerContainerId ) ;

		return dockerContainerId ;

	}

	public Optional<Container> findContainerUsingLabel ( String labelType , String labelValue ) {

		logger.info( "labelType: '{}',  labelValue: '{}'", labelType, labelValue ) ;

		Optional<Container> matchContainer ;

		try {

			logger.debug( "searching for: {}", labelValue ) ;

			if ( ! labelValue.startsWith( "/" ) ) {

				logger.warn( "Docker names should start with /, but found: {}", labelValue ) ;

			}

			List<Container> containers = dockerClient.listContainersCmd( ).withShowAll( true ).exec( ) ;

			if ( logger.isDebugEnabled( ) ) {

				String containerInfo = containers
						.stream( )
						.map( container -> {

							return "Names: " + Arrays.asList( container.getNames( ) ) ;

						} )
						.collect( Collectors.joining( "\n" ) ) ;
				logger.info( "resolved: {}", containerInfo ) ;

			}

			matchContainer = containers
					.stream( )
					.filter( container -> container.getLabels( ).containsKey( labelType ) )
					.filter( container -> container.getLabels( ).get( labelType ).matches( Pattern.quote(
							labelValue ) ) )
					.findFirst( ) ;

		} catch ( Exception e ) {

			logger.info( "Failed finding container: {}", CSAP.buildCsapStack( e ) ) ;
			matchContainer = Optional.empty( ) ;

		}

		return matchContainer ;

	}

	public Optional<Container> findContainerByName ( String name ) {

		Optional<Container> matchContainer ;

		try {

			logger.debug( "searching for: {}", name ) ;

			if ( ! name.startsWith( "/" ) ) {

				logger.warn( "Docker names should start with '/', but found: {}", name ) ;

			}

			List<Container> containers = dockerClient.listContainersCmd( ).withShowAll( true ).exec( ) ;

			if ( logger.isDebugEnabled( ) ) {

				String containerInfo = containers
						.stream( )
						.map( container -> {

							return "Names: " + Arrays.asList( container.getNames( ) ) ;

						} )
						.collect( Collectors.joining( "\n" ) ) ;
				logger.info( "resolved: {}", containerInfo ) ;

			}

			matchContainer = containers
					.stream( )
					.filter( container -> Arrays.asList( container.getNames( ) ).contains( name ) )
					.findFirst( ) ;

		} catch ( Exception e ) {

			String reason = CSAP.buildCsapStack( e ) ;
			logger.warn( "Failed finding container: {}", reason ) ;
			check_docker_fatal_error( reason ) ;
			matchContainer = Optional.empty( ) ;

		}

		return matchContainer ;

	}

	String dockerDisabledMessage = "Docker Runtime Exception: docker integration is being disabled. Verify docker settings and restart agent" ;

	public void check_docker_fatal_error ( String reason ) {
		// linux: java.lang.UnsatisfiedLinkError
		// windows: junixsocket

		if ( reason.contains( "UnsatisfiedLinkError" ) || reason.contains( "junixsocket" ) ) {

			try {
				// Docker api has buggy runtime handling that leaks connections.
				// this should occure very rarely - so rather then attempting to
				// handle -disable for investigation

				logger.warn( "{} \n {} {} {}",
						CsapApplication.LINE,
						dockerDisabledMessage,
						CsapApplication.LINE,
						reason ) ;

				dockerClient.close( ) ;
				dockerClient = null ;

			} catch ( Exception e1 ) {

				logger.warn( "Failed close command: {}", reason = CSAP.buildCsapStack( e1 ) ) ;

			}

		}

	}

	public ObjectNode buildSummary ( ) {

		Timer.Sample summaryTimer = dockerConfig.getCsapApp( ).metrics( ).startTimer( ) ;

		ObjectNode summary = jsonMapper.createObjectNode( ) ;
		summary.put( "version", "not installed" ) ;
		summary.put( KubernetesJson.heartbeat.json( ), false ) ;

		// summary.put( "rootDirectory", "/not/available" );
		try {

			// logger.debug( "Issueing dockerClient command" ) ;
			Info info = dockerClient.infoCmd( ).exec( ) ;
			// logger.debug( "Completed dockerClient command" ) ;

			summary.put( "imageCount", info.getImages( ) ) ;
			summary.put( "containerCount", info.getContainers( ) ) ;
			summary.put( "containerRunning", info.getContainersRunning( ) ) ;

			//
			var crioContainers = 0 ;

			if ( Application.getInstance( ).isCrioInstalledAndActive( ) ) {

				crioContainers = Application.getInstance( ).crio( ).containerCount( ) ;

			}

			summary.put( "crioContainerCount", crioContainers ) ;

			summary.put( "version", info.getServerVersion( ) ) ;

			summary.put( "rootDirectory", info.getDockerRootDir( ) ) ;

			summary.put( KubernetesJson.heartbeat.json( ), true ) ;

			ListVolumesResponse volumeResponse = dockerClient.listVolumesCmd( ).exec( ) ;

			int volumeCount = 0 ;

			if ( volumeResponse.getVolumes( ) != null ) {

				volumeCount = volumeResponse.getVolumes( ).size( ) ;

			}

			summary.put( "volumeCount", volumeCount ) ;

			int networkCount = 0 ;

			if ( ! Application.getInstance( ).isCrioInstalledAndActive( ) ) {

				List<Network> networks = dockerClient.listNetworksCmd( ).exec( ) ;

				if ( networks != null ) {

					networkCount = networks.size( ) ;

				}

			}

			summary.put( "networkCount", networkCount ) ;

		} catch ( Exception e ) {

			summary.set( "error", buildErrorResponse( "Build docker summary", e ) ) ;
			logger.info( "Docker connection Error: {}", CSAP.buildCsapStack( e ) ) ;

		}

		dockerConfig.getCsapApp( ).metrics( ).stopTimer( summaryTimer, SUMMARY_TIMER ) ;
		return summary ;

	}

	boolean printProcessWarningsOnce = true ;

	//
	//
	//
	// Process Mappings
	//
	//

	public List<ContainerProcess> build_process_info_for_containers ( ) {

		var allTimer = getDockerConfig( ).getCsapApp( ).metrics( ).startTimer( ) ;

		List<ContainerProcess> containerProcesses = new ArrayList<>( ) ;
		StringBuilder pidScanInfo = new StringBuilder( ) ;

		//
		// docker and podman container collection
		//
		try {

			// podman system seems to miss every 15 minutes or so
			containerProcesses.addAll( buildContainerProcesses( pidScanInfo ) ) ;

		} catch ( Exception e ) {

			logger.warn( "Failed connecting to container: {}", CSAP.buildCsapStack( e ) ) ;

			// names.add( "** Unable-to-get-listing" );
		}

		//
		// CRIO containers (Optional)
		//
		try {

			if ( Application.getInstance( ).isCrioInstalledAndActive( ) ) {

				var crioProcesses = Application.getInstance( ).crio( ).buildContainerProcesses( ) ;

				logger.debug( "crioProcesses: {}", crioProcesses ) ;

				containerProcesses.addAll( crioProcesses ) ;

			}

		} catch ( Exception e ) {

			logger.warn( "Failed collecting CRIO containers: {}", CSAP.buildCsapStack( e ) ) ;

			// names.add( "** Unable-to-get-listing" );
		}

		if ( printProcessWarningsOnce ) {

			printProcessWarningsOnce = false ;
			logger.warn( "Docker pid scan exclusion(s) {}",
					pidScanInfo ) ;

		}

		getDockerConfig( ).getCsapApp( ).metrics( ).stopTimer( allTimer, "collect-container.pids" ) ;

		return containerProcesses ;

	}

	private List<ContainerProcess> buildContainerProcesses ( StringBuilder pidScanInfo ) {

		List<ContainerProcess> containerProcesses ;
		List<Container> containers = dockerClient.listContainersCmd( ).withShowAll( false ).exec( ) ;

		containerProcesses = containers.stream( )
				.map( container -> {

					if ( isKubernetesPodWrapper( container ) || ( container.getNames( ) == null ) || ( container
							.getNames( ).length == 0 ) ) {

						// logger.info( "Filtering: {}", container );
						if ( printProcessWarningsOnce ) {

							pidScanInfo.append(
									"\n Filtering:  image: " + container.getImage( ) + " description: "
											+ containerDescription( container ) ) ;

						}

						return null ;

					}

					ContainerProcess process = new ContainerProcess( ) ;
					logger.debug( "container: \n {}", container.toString( ) ) ;

					String name = container.getNames( )[ 0 ] ;

					process.setContainerName( name ) ;
					process.setMatchName( name ) ;

					if ( container.getLabels( ) != null ) {

						String k8Name = container.getLabels( ).get( IO_KUBERNETES_CONTAINER_NAME ) ;

						if ( k8Name != null ) {

							process.setMatchName( k8Name ) ;

							var podName = container.getLabels( ).get( "io.kubernetes.pod.name" ) ;

							if ( podName != null ) {

								process.setPodName( podName ) ;

							}

							process.setPodNamespace( container.getLabels( ).get( "io.kubernetes.pod.namespace" ) ) ;

						}

					}

					var podTimer = getDockerConfig( ).getCsapApp( ).metrics( ).startTimer( ) ;

					try {

						InspectContainerResponse details = dockerClient.inspectContainerCmd( container.getId( ) )
								.exec( ) ;
						process.setPid( Integer.toString( details.getState( ).getPid( ) ) ) ;

					} catch ( Exception e ) {

						if ( printProcessWarningsOnce ) {

							pidScanInfo.append(
									"\n Inspection Error:  image: " + container.getImage( ) + " desc: "
											+ containerDescription( container ) ) ;

							// logger.warn( "Failed to inpect container: {}, {}", container,
							// CSAP.buildCsapStack( e ) );
						}

						// fallback to cli - only known failure is coredns

						var pid = cachedPidForCoreDnsInspectParseException( container.getId( ) ) ;

						if ( StringUtils.isNotEmpty( pid ) ) {

							process.setPid( pid ) ;

						} else {

							// logger.warn( "Failed to inspect pid: {}, {}", container, CSAP.buildCsapStack(
							// e ) );
							return null ;

						}

					}

					getDockerConfig( ).getCsapApp( ).metrics( ).stopTimer( podTimer, "collect-container.pids."
							+ process.getPodName( ) ) ;

					return process ;

				} )
				.filter( Objects::nonNull )
				.collect( Collectors.toList( ) ) ;

		return containerProcesses ;

	}

	static public final String PID_CACHE = "DockerPidExceptionCache" ;

	public String cachedPidForCoreDnsInspectParseException ( String containerName ) {

		var cacheManager = dockerConfig.getCacheManager( ) ;

		if ( cacheManager == null ) {

			logger.warn( "Caching disabled" ) ;
			return containerPidWhenInspectErrorsOut( containerName ) ;

		}

		Cache<String, String> pidCache = cacheManager.getCache( PID_CACHE, String.class, String.class ) ;

		var pid = pidCache.get( containerName ) ;

		if ( StringUtils.isEmpty( pid ) ) {

			logger.debug( "Refreshing cache" ) ;
			pid = containerPidWhenInspectErrorsOut( containerName ) ;

			if ( StringUtils.isNotEmpty( pid ) ) {

				pidCache.put( containerName, pid ) ;

			}

		} else {

			logger.debug( "Using cached value" ) ;

		}

		return pid ;

	}

	public String containerPidWhenInspectErrorsOut ( String containerName ) {

		List<String> commands = List.of(
				"docker", "inspect", "-f", "{{.State.Pid}}",
				containerName ) ;

		String results = "" ;

		try {

			results = osCommandRunner.runCommandAndTrimOutput( commands ).trim( ) ;
			logger.debug( "commands: {}, results: {}", commands, results ) ;

		} catch ( Exception e ) {

			logger.debug( "Failed to run inspect: {} {}", commands, CSAP.buildCsapStack( e ) ) ;

		}

		return results ;

	}

	public ObjectNode inspectByCli ( String containerName ) {

		List<String> commands = List.of( "docker", "inspect",
				containerName ) ;

		ObjectNode results = jsonMapper.createObjectNode( ) ;

		try {

			var commandResults = osCommandRunner.runCommandAndTrimOutput( commands ) ;
			logger.debug( "commands: {}, results: {}", commands, results ) ;
			var tree = jsonMapper.readTree( commandResults ) ;

			if ( tree.isArray( ) ) {

				var firstElement = tree.path( 0 ) ;

				if ( firstElement.isObject( ) ) {

					results = (ObjectNode) firstElement ;

				}

			}

		} catch ( Exception e ) {

			logger.warn( "Failed to run inspect: {} {}", commands, CSAP.buildCsapStack( e ) ) ;
			results.put( "cli-error", "failed to parse result" ) ;

		}

		return results ;

	}

	private String containerDescription ( Container container ) {

		String description = container.getId( ) ;

		if ( ( container.getNames( ) != null ) && ( container.getNames( ).length > 0 ) ) {

			description = Arrays.asList( container.getNames( ) ).toString( ) ;

		}

		return description ;

	}

	public List<String> containerNames ( boolean showAll ) {

		List<String> names = new ArrayList<>( ) ;

		try {

			List<Container> containers = dockerClient.listContainersCmd( ).withShowAll( showAll ).exec( ) ;

			containers.forEach( container -> {

				logger.debug( "container: \n {}", container.toString( ) ) ;
				String name = Arrays.asList( container.getNames( ) ).toString( ) ;

				if ( container.getNames( ).length == 1 ) {

					name = container.getNames( )[ 0 ] ;

				}

				names.add( name ) ;
				
				//logger.info( "labels: {}, names: {}",  container.getLabels( ) ,  name )  ;

			} ) ;

		} catch ( Exception e ) {

			logger.warn( "Failed connecting to docker: {}", CSAP.buildCsapStack( e ) ) ;
			names.add( "** Unable-to-get-listing" ) ;

		}

		return names ;

	}

	public String getLastImage ( ) {

		return lastImage ;

	}

	public void setLastImage ( String lastImage ) {

		this.lastImage = lastImage ;

	}

	public String getLastResults ( int offset ) {

		String results = "" ;

		if ( offset < latest_pull_results.length( ) ) {

			results = latest_pull_results.substring( offset ) ;

		}

		;

		if ( ! isPullInProgress( ) ) {

			results += "\n__Complete__" ;

		}

		return results ;

	}

	public void setLatest_pull_results ( StringBuilder lastResults ) {

		this.latest_pull_results = lastResults ;

	}

	public String tailFile ( String name , String path , int numLines ) {

		String listingOutput = "" ;

		try {

			Optional<Container> matchContainer = findContainerByName( name ) ;

			if ( matchContainer.isPresent( ) ) {

				ExecCreateCmdResponse execCreateCmdResponse = dockerClient
						.execCreateCmd( matchContainer.get( ).getId( ) )
						.withAttachStdout( true )
						.withCmd( "tail", "--lines=" + numLines, path )
						.withUser( "root" )
						.exec( ) ;

				ByteArrayOutputStream lsOutputStream = new ByteArrayOutputStream( ) ;
				dockerClient
						.execStartCmd( execCreateCmdResponse.getId( ) )
						.exec( new ResultCallback.Adapter<Frame>( ) {
							@Override
							public void onNext ( Frame item ) {

								logger.info( "frame: {}", item.toString( ) ) ;

								try {

									lsOutputStream.write( item.getPayload( ) ) ;

								} catch ( IOException e ) {

									logger.warn( "Failed to get response" ) ;

								}

							}
						} ).awaitCompletion( 3, TimeUnit.SECONDS ) ;
//						.exec(
//								new ExecStartResultCallback( lsOutputStream, lsOutputStream ) )
//						.awaitCompletion( ) ;

				listingOutput = new String( lsOutputStream.toByteArray( ), StandardCharsets.UTF_8 ) ;

			} else {

				listingOutput = MISSING_FILE_LINE + name ;
				logger.warn( "Container not found: {} ", name ) ;

			}

		} catch ( Exception e ) {

			String reason = CSAP.buildCsapStack( e ) ;
			logger.warn( "Failed listing files in {}, {}", name, reason ) ;

		}

		logger.debug( "Container: {}, path: {}, listing: {}", name, path, listingOutput ) ;

		return listingOutput ;

	}

	public String listFiles ( String name , String path ) {

		String listingOutput = containerCommand( name, "ls", "-al", path ) ;

		if ( listingOutput.length( ) == 0 ) {

			listingOutput = MISSING_FILE_LINE + name ;

		}

		logger.debug( "Container: {}, path: {}, listing: {}", name, path, listingOutput ) ;

		return listingOutput ;

	}

	public String containerCommand ( String containerName , String... command ) {

		String commandOutput = "" ;

		if ( containerName.startsWith( "crio--" ) ) {

			return CRIO_COMMAND_NOT_IMPLMENTED ;

		}

		try {

			Optional<Container> matchContainer = findContainerByName( containerName ) ;

			if ( matchContainer.isPresent( ) ) {

				ExecCreateCmdResponse execCreateCmdResponse = dockerClient
						.execCreateCmd( matchContainer.get( ).getId( ) )
						.withAttachStdout( true )
						.withAttachStderr( true )
						.withCmd( command )
						.withUser( "root" )
						.exec( ) ;

				ByteArrayOutputStream lsOutputStream = new ByteArrayOutputStream( ) ;
				dockerClient
						.execStartCmd( execCreateCmdResponse.getId( ) )
						.exec( new ResultCallback.Adapter<Frame>( ) {
							@Override
							public void onNext ( Frame item ) {

								logger.debug( "frame: {}", item.toString( ) ) ;

								try {

									lsOutputStream.write( item.getPayload( ) ) ;

								} catch ( Exception e ) {

									logger.warn( "failed writing output" ) ;

								}

							}
						} ).awaitCompletion( 3, TimeUnit.SECONDS ) ;
//						.exec(
//								new ExecStartResultCallback( lsOutputStream, lsOutputStream ) )
//						.awaitCompletion( ) ;

				commandOutput = new String( lsOutputStream.toByteArray( ), StandardCharsets.UTF_8 ) ;

			} else {

				logger.warn( "Container not found: {}, commands: {} ", containerName, Arrays.asList( command ) ) ;

			}

		} catch ( Exception e ) {

			String reason = CSAP.buildCsapStack( e ) ;
			logger.warn( "Container {}: Failed running command {}. Reason: {}",
					containerName, Arrays.asList( command ), reason ) ;

		}

		logger.debug( "Container: {}, Command: {}, output: {}", containerName, Arrays.asList( command ),
				commandOutput ) ;

		return commandOutput ;

	}

	static final int LAST_FIVE_SECONDS_IN_MS = 5000 ;

	public static int logSinceSeconds ( int seconds ) {

		// long sinceSeconds = (System.currentTimeMillis() / 1000) - (seconds *
		// CSAP.ONE_SECOND_MS);
		long ts = ( System.currentTimeMillis( ) ) - ( seconds * CSAP.ONE_SECOND_MS ) ;
		return (int) ts ;

	}

	public static String simpleImageName ( String name ) {

		return name.substring( name.indexOf( "/" ) + 1 ) ;

	}

	public void containerTailStream (
										String id ,
										String name ,
										HttpServletResponse response ,
										int numberOfLines ) {

		try {

			var containerId = getContainerId( id, name ) ;
			// docker.startContainer( container.id());
			var loggingCallbackHandler = new ContainerLogHandler( response.getWriter( ) ) ;
			LogContainerCmd logCommand = dockerClient
					.logContainerCmd( containerId )
					.withStdErr( true )
					.withStdOut( true ) ;

			if ( numberOfLines > 0 ) {

				logCommand.withTail( numberOfLines ) ;

			}

			logCommand.exec( loggingCallbackHandler ) ;

			loggingCallbackHandler.awaitCompletion( 3, TimeUnit.SECONDS ) ;

		} catch ( Exception e ) {

			String reason = CSAP.buildCsapStack( e ) ;
			logger.warn( "Failed starting {}, {}", name, reason ) ;

			try {

				response.getWriter( ).println( "Failed getting file: " + reason ) ;

			} catch ( IOException e1 ) {

				// TODO Auto-generated catch block
				e1.printStackTrace( ) ;

			}

		}

		return ;

	}

	public ObjectNode containerTail (
										String id ,
										String name ,
										int numberOfLines ,
										int since ) {

		ObjectNode result = jsonMapper.createObjectNode( ) ;
		result.put( "since", since ) ;
		result.put( "time", LocalDateTime.now( ).format( DateTimeFormatter.ofPattern( "HH:mm:ss" ) ) ) ;

		try {

			String targetId = getContainerId( id, name ) ;

			// docker.startContainer( container.id());
			if ( targetId == null ) {

				result.put( DockerJson.response_info.json( ), "View logs: " + targetId ) ;
				result.put( "plainText", "Unable to locate container - verify it is created and running" ) ;
				logger.info( "Failed to find logs for: {} - name: {}", id, name ) ;
				return result ;

			}

			ContainerLogHandler loggingCallback = new ContainerLogHandler( true ) ;
			LogContainerCmd logCommand = dockerClient
					.logContainerCmd( targetId )
					.withStdErr( true )
					.withStdOut( true )
					.withTail( numberOfLines ) ;

			int timestamp = (int) ( System.currentTimeMillis( ) / 1000 ) - 1 ;
			result.put( "since", timestamp ) ;

			if ( since > 0 ) {

				logCommand.withSince( since ) ;

			}

			logCommand.exec( loggingCallback ) ;
			loggingCallback.awaitCompletion( 3, TimeUnit.SECONDS ) ;

			result.put( DockerJson.response_info.json( ), "View logs: " + targetId ) ;
			// String logOutput =loggingCallback.toString() ;

			// // Docker returns new empty line
			// if ( logOutput.length() == 1 && logOutput.equals( "\n" ) )
			// logOutput = "" ;

			result.put( "plainText", loggingCallback.toString( ) ) ;
			String containerParam = "&id=" + id ;
			if ( id == null )
				containerParam = "&name=" + name ;
			result.put( "url", "/docker/container/tail" + "?numberOfLines="
					+ numberOfLines + containerParam ) ;

		} catch ( Exception e ) {

			result = buildErrorResponse( "Failed tailing: " + name, e ) ;

		}

		return result ;

	}

	// helper method for accessing files
	public void writeContainerFileToHttpResponse (
													boolean isBinary ,
													String name ,
													String path ,
													HttpServletResponse servletResponse ,
													long maxEditSize ,
													int chunkSize ) {

		logger.info( "Container: {}, path: {}, maxEditSize: {}", name, path, maxEditSize ) ;
		Optional<Container> matchContainer = findContainerByName( name ) ;

		if ( StringUtils.isEmpty( path ) ) {

			containerTailStream( null, name, servletResponse, -1 ) ;
			return ;

		}

		byte[] bufferAsByteArray = new byte[chunkSize] ;

		try (
				InputStream dockerTarStream = dockerClient.copyArchiveFromContainerCmd( matchContainer.get( ).getId( ),
						path ).exec( );
				ServletOutputStream servletOutputStream = servletResponse.getOutputStream( ); ) {

			int numReadIn = 0 ;

			try ( TarArchiveInputStream tarInputStream = new TarArchiveInputStream( dockerTarStream ) ) {

				TarArchiveEntry tarEntry = tarInputStream.getNextTarEntry( ) ;
				long tarEntrySize = tarEntry.getSize( ) ;

				if ( isBinary ) {

					servletResponse.setContentLength( Math.toIntExact( tarEntrySize ) ) ;

				}

				logger.info( "tar entry name: {}", tarEntry.getName( ) ) ;

				if ( tarEntrySize > maxEditSize ) {

					servletOutputStream.println(
							"**** Warning: output truncated as max size reached: " + maxEditSize / 1024 +
									"Kb. \n\tView or download can be used to access entire file.\n=====================================" ) ;

				}

				while ( tarInputStream.available( ) > 0 && numReadIn < maxEditSize && numReadIn < tarEntrySize ) {

					int numBytesRead = IOUtils.read( tarInputStream, bufferAsByteArray ) ;
					numReadIn += numBytesRead ;
					// String stringReadIn = new String( bufferAsByteArray,
					// 0,
					// numBytesRead );
					servletOutputStream.write( bufferAsByteArray, 0, numBytesRead ) ;
					servletOutputStream.flush( ) ;

					// logger.debug( "numRead: {}, chunk: {}", numBytesRead,
					// stringReadIn );
				}

				while ( IOUtils.read( dockerTarStream, bufferAsByteArray ) > 0 ) {

					// need to read fully or stream will leak
				}
				// response.close();

			}

			// response.close();

		} catch ( Exception e ) {

			logger.error( "Failed to close: {}",
					CSAP.buildCsapStack( e ) ) ;

		}

	}

	public StringBuilder writeContainerFileToString (
														ModelMap modelMap ,
														String name ,
														String path ,
														long maxEditSize ,
														int chunkSize ) {

		ObjectNode resultReport = jsonMapper.createObjectNode( ) ;

		logger.info( "Container: {}, path: {}, maxEditSize: {}", name, path, maxEditSize ) ;

		StringBuilder fileContents = new StringBuilder( ) ;
		Optional<Container> matchContainer = findContainerByName( name ) ;

		byte[] bufferAsByteArray = new byte[chunkSize] ;

		try (
				InputStream dockerTarStream = dockerClient.copyArchiveFromContainerCmd( matchContainer.get( ).getId( ),
						path ).exec( ); ) {

			int numReadIn = 0 ;

			try ( TarArchiveInputStream tarInputStream = new TarArchiveInputStream( dockerTarStream ) ) {

				TarArchiveEntry tarEntry = tarInputStream.getNextTarEntry( ) ;
				long tarEntrySize = tarEntry.getSize( ) ;

				logger.info( "tar entry name: {}", tarEntry.getName( ) ) ;

				if ( tarEntrySize > maxEditSize ) {

					fileContents.append(
							"**** Warning: output truncated as max size reached: " + maxEditSize / 1024 +
									"Kb. \n\tView or download can be used to access entire file.\n=====================================" ) ;

				}

				while ( tarInputStream.available( ) > 0 && numReadIn < maxEditSize && numReadIn < tarEntrySize ) {

					int numBytesRead = IOUtils.read( tarInputStream, bufferAsByteArray ) ;
					numReadIn += numBytesRead ;
					String stringReadIn = new String( bufferAsByteArray, 0, numBytesRead ) ;
					fileContents.append( stringReadIn ) ;

					// logger.debug( "numRead: {}, chunk: {}", numBytesRead,
					// stringReadIn );
				}

				while ( IOUtils.read( dockerTarStream, bufferAsByteArray ) > 0 ) {

					// need to read fully or stream will leak
				}
				// response.close();

			}

			// response.close();

		} catch ( Exception e ) {

			logger.error( "Failed to close: {}", CSAP.buildCsapStack( e ) ) ;
			resultReport.put( "error", "Failed writing to container: " + e.getLocalizedMessage( ) ) ;

		}

		resultReport.put( "plain-text", fileContents.toString( ) ) ;
		modelMap.addAttribute( "result", resultReport ) ;

		return fileContents ;

	}

	private static TarArchiveOutputStream getTarArchiveOutputStream ( File tarfile ) throws IOException {

		TarArchiveOutputStream taos = new TarArchiveOutputStream( new FileOutputStream( tarfile ) ) ;
		// TAR has an 8 gig file limit by default, this gets around that
		taos.setBigNumberMode( TarArchiveOutputStream.BIGNUMBER_STAR ) ;
		// TAR originally didn't support long file names, so enable the support for it
		taos.setLongFileMode( TarArchiveOutputStream.LONGFILE_GNU ) ;
		taos.setAddPaxHeadersForNonAsciiNames( true ) ;
		return taos ;

	}

	private static void addToArchiveCompression ( TarArchiveOutputStream out , File file , String dir )
		throws IOException {

		String entry = dir + File.separator + file.getName( ) ;

		if ( file.isFile( ) ) {

			out.putArchiveEntry( new TarArchiveEntry( file, entry ) ) ;

			try ( FileInputStream in = new FileInputStream( file ) ) {

				IOUtils.copy( in, out ) ;

			}

			out.closeArchiveEntry( ) ;

		} else if ( file.isDirectory( ) ) {

			File[] children = file.listFiles( ) ;

			if ( children != null ) {

				for ( File child : children ) {

					addToArchiveCompression( out, child, entry ) ;

				}

			}

		} else {

			System.out.println( file.getName( ) + " is not supported" ) ;

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
		Optional<Container> matchContainer = findContainerByName( containerName ) ;

		if ( matchContainer.isPresent( ) ) {

			try {

				var requestedFilePath = Paths.get( pathToFile ) ;
				var requestedFileParent = requestedFilePath.getParent( ).toString( ) ;

				if ( File.separatorChar == '\\' ) {

					logger.warn( "Windows Conversion: {}", requestedFileParent ) ;
					requestedFileParent = FilenameUtils.separatorsToUnix( requestedFilePath.getParent( ).toString( ) ) ;

				}

				//
				// Create a tar file
				//
				var tarFile = new File( Application.getInstance( ).getScriptDir( ), containerName + ".tar" ) ;
				var sourceFolder = new File( Application.getInstance( ).getScriptDir( ), containerName ) ;
				FileUtils.deleteQuietly( sourceFolder ) ;
				sourceFolder.mkdirs( ) ;

				var tempFileToTarAndTransfer = new File( sourceFolder, requestedFilePath.getFileName( ).toString( ) ) ;
				FileUtils.write( tempFileToTarAndTransfer, contents ) ;

				logger.debug( "Creating: {} for tar: {}", tempFileToTarAndTransfer, tarFile ) ;

				try ( TarArchiveOutputStream tarOuputStream = getTarArchiveOutputStream( tarFile ) ) {

					addToArchiveCompression( tarOuputStream, tempFileToTarAndTransfer, "." ) ;

				}

				//
				// transfer to docker host
				//
				try ( var tarInputStream = //
						new FileInputStream( tarFile ) ) {

					dockerClient.copyArchiveToContainerCmd( matchContainer.get( ).getId( ) )
//						.withHostResource( sourceFile.getAbsolutePath( ) )
							.withTarInputStream( tarInputStream )
							.withRemotePath( requestedFileParent )
							.exec( ) ;

				}

				resultReport.put( "success", "Updated container: " + containerName + " path: " + pathToFile ) ;

			} catch ( Exception e ) {

				logger.warn( "Failed writing file: {}", CSAP.buildCsapStack( e ) ) ;
				resultReport.put( "error", "Failed writing to container: " + e.getLocalizedMessage( ) ) ;

			}

		} else {

			resultReport.put( "error", "Failed to locate container: " + containerName ) ;

		}

		return resultReport ;

	}

	private ArrayNode imageListNames ( ) {

		ArrayNode listArray = jsonMapper.createArrayNode( ) ;

		dockerClient
				.listImagesCmd( ).exec( )
				.stream( )
				.map( Image::getRepoTags )
				.filter( Objects::nonNull )
				.map( Arrays::asList )
				.map( List::toString )
				.forEach( tagListString -> {

					listArray.add( tagListString ) ;

				} ) ;
		logger.info( "Current Images: {}", listArray ) ;
		return listArray ;

	}

	public ArrayNode imageListWithDetails ( boolean showAllImages ) {

		ArrayNode imageListing = jsonMapper.createArrayNode( ) ;

		try {

			List<Image> images = dockerClient.listImagesCmd( ).withShowAll( showAllImages ).exec( ) ;

			// logger.info( "images: {}",images );

			images.forEach( image -> {

				logger.debug( "image: \n {}", image.toString( ) ) ;

				ObjectNode item = imageListing.addObject( ) ;

				String label = "none" ;

				if ( image.getRepoTags( ) != null && image.getRepoTags( ).length > 0 ) {

					label = image.getRepoTags( )[ 0 ] ;

					logger.debug( "tags: {}, length: {} ", image.getRepoTags( ), image.getRepoTags( ).length ) ;

					if ( label.equals( "<none>:<none>" ) ) {

						label = image.getId( ) ;

					}

				} else {

					label = image.getId( ) ;

				}

				item.put( DockerJson.list_label.json( ), label ) ;

				InspectImageResponse imageResponse = dockerClient.inspectImageCmd( image.getId( ) ).exec( ) ;

				ObjectNode inspectJson = jsonMapper.convertValue( imageResponse, ObjectNode.class ) ;

				item.set( DockerJson.list_attributes.json( ), inspectJson ) ;
				item.put( "folder", true ) ;
				item.put( "lazy", true ) ;

			} ) ;

			if ( images.size( ) == 0 ) {

				ObjectNode item = imageListing.addObject( ) ;
				item.put( DockerJson.error.json( ), "No images defined" ) ;

			}

		} catch ( Exception e ) {

			imageListing.add( buildErrorResponse( "Failed listing: " + showAllImages, e ) ) ;

		}

		return imageListing ;

	}

	public ObjectNode imageRemove (
									boolean force ,
									String id ,
									String name )
		throws Exception {

		logger.info( "Removing: {}", name ) ;
		ObjectNode result = jsonMapper.createObjectNode( ) ;

		try {

			String itemToRemove = name ;

			if ( StringUtils.isNotEmpty( id ) ) {

				itemToRemove = id ;

			}

			dockerClient
					.removeImageCmd( itemToRemove )
					.withForce( force ) // force will remove image tags - but
					// leave in place as anonymous
					.exec( ) ;

			result.put( DockerJson.response_info.json( ), "image has been removed: " + name ) ;
			result.set( "listing", imageListNames( ) ) ;

		} catch ( Exception e ) {

			result = buildErrorResponse( "Failed removing image: " + name, e ) ;

		}

		return result ;

	}

	public boolean imageSave ( String name , File destination ) {

		if ( ! Application.isRunningOnDesktop( ) ) {

			return imageSaveOs( name, destination.getAbsolutePath( ) ) ;

		} else {

			if ( Application.isRunningOnDesktop( ) ) {

				logger.warn(
						"\n\n *************** SKIPPING save on desktop as can be slow. Uncomment code to test\n\n" ) ;

				try {

					Thread.sleep( 3000 ) ;

				} catch ( InterruptedException e ) {

					// TODO Auto-generated catch block
					e.printStackTrace( ) ;

				}

				return true ;

			}

			try (
					InputStream is = dockerClient.saveImageCmd( name ).exec( ); ) {

				java.nio.file.Files.copy(
						is,
						destination.toPath( ),
						StandardCopyOption.REPLACE_EXISTING ) ;

			} catch ( Exception e ) {

				logger.error( "Failed to save image {} to local filesystem: {}, reason: {}",
						name, destination,
						CSAP.buildCsapStack( e ) ) ;

				return false ;

			}

		}

		return true ;

	}

	private boolean imageSaveOs ( String imageName , String destTarPath ) {

		logger.info( "saving image using os shell: {}",
				destTarPath ) ;

		// String[] lines = {
		// "#!/bin/bash",
		// "docker save --output " + destTarPath + " " + imageName,
		// "echo ",
		// "" };

		List<String> lines = osCommands.getDockerImageExport( destTarPath, imageName ) ;

		String scriptOutput = "Failed to run" ;

		try {

			scriptOutput = osCommandRunner.runUsingRootUser( "dockerSaveTar", lines ) ;
			logger.info( "results: {}", scriptOutput ) ;

		} catch ( IOException e ) {

			logger.warn( "Failed to save docker image {} tar: {}, reason: {} ",
					imageName, destTarPath, CSAP.buildCsapStack( e ) ) ;
			scriptOutput += ", reason: " + e.getMessage( ) + " type: " + e.getClass( ).getName( ) ;
			return false ;

		}

		return true ;

	}

	public boolean imageLoad ( File sourceTarFile ) {

		if ( ! Application.isRunningOnDesktop( ) ) {

			return imageLoadOs( sourceTarFile.getAbsolutePath( ) ) ;

		} else {

			try ( InputStream uploadStream = Files
					.newInputStream( sourceTarFile.toPath( ) ) ) {

				dockerClient.loadImageCmd( uploadStream ).exec( ) ;

			} catch ( Exception e ) {

				logger.error( "Failed to load image from  local filesystem: {}, reason: {}",
						sourceTarFile,
						CSAP.buildCsapStack( e ) ) ;

				return false ;

			}

		}

		return true ;

	}

	private boolean imageLoadOs ( String sourceTarPath ) {

		logger.info( "loading image using os shell: {}",
				sourceTarPath ) ;

		// String[] lines = {
		// "#!/bin/bash",
		// "docker load --input " + sourceTarPath,
		// "echo ",
		// "" };

		List<String> lines = osCommands.getDockerImageLoad( sourceTarPath ) ;

		String scriptOutput = "Failed to run" ;

		try {

			scriptOutput = osCommandRunner.runUsingRootUser( "dockerLoadTar", lines ) ;
			logger.info( "results: {}", scriptOutput ) ;

		} catch ( IOException e ) {

			logger.warn( "Failed to load docker image tar: {}, reason: {} ",
					sourceTarPath, CSAP.buildCsapStack( e ) ) ;
			scriptOutput += ", reason: " + e.getMessage( ) + " type: " + e.getClass( ).getName( ) ;
			return false ;

		}

		return true ;

	}

	// synchronized - not need due to use of pull lactc
	public ObjectNode imagePull (
									String imageName ,
									OutputFileMgr pullOutput ,
									int maxSecondsToBlockOnPull ,
									int maxAttempts ) {

		logger.info( "Pulling: {} , Waiting for: {} seconds, maxAttempts: {}",
				imageName, maxSecondsToBlockOnPull, maxAttempts ) ;

		ObjectNode pullReporting = jsonMapper.createObjectNode( ) ;

		if ( ! imageName.contains( ":" ) ) {

			logger.warn( "image does not contain a label, appending latest" ) ;
			imageName += ":latest" ;

		}

		pullReporting.put( "image", imageName ) ;

		try {

			if ( isPullInProgress( ) ) {

				pullReporting.put( DockerJson.error.json( ), "Failed to pull: " + imageName ) ;
				pullReporting.put( DockerJson.errorReason.json( ),
						"Docker pull already in progress: " + getLastImage( )
								+ " - wait a minute and try again later." ) ;

			} else {

				var pullHandler = buildPullHandler( imageName, pullOutput ) ;

				var pullImageCallback = dockerClient
						.pullImageCmd( imageName )
						.exec( pullHandler ) ;

				boolean isImagePullCompleted = false ;

				// on interactive pulls, queryies occur in background.
				// on csap deploy - lets retry
				int attempts = 1 ;

				while ( ! isImagePullCompleted ) {

					logger.warn(
							CsapApplication.header( "Attempt {} of max {} : Pulling {}, waiting for {} seconds " ),
							attempts, maxAttempts, imageName, maxSecondsToBlockOnPull ) ;

					isImagePullCompleted = docker3xPullCompleted( maxSecondsToBlockOnPull, pullHandler,
							pullImageCallback ) ;

					if ( attempts++ >= maxAttempts ) {

						logger.info( "Max attempts made waiting for pull to complete" ) ;
						break ;

					}

				}

				pullReporting.put( DockerJson.response_info.json( ), "pulling image: " + imageName + " ..." ) ;
				pullReporting.put( DockerJson.pull_complete.json( ), isImagePullCompleted ) ;
				pullReporting.put( "monitorProgress", true ) ;

				// error checking relies on callbacks to complete. Wait a few
				// seconds for them to complete
				pullLatch.await( 3, TimeUnit.SECONDS ) ;
				pullReporting.put( DockerJson.error.json( ), isFoundPullError( ) ) ;

			}

		} catch ( Exception e ) {

			logger.warn( "Failed to pull image, releasing latch, reason: {}", CSAP.buildCsapStack( e ) ) ;
			pullLatch.countDown( ) ; // release the latch for subsequent pulls
			pullReporting = buildErrorResponse( "Failed pulling: " + imageName, e ) ;

		} finally {

		}

		return pullReporting ;

	}

	private boolean docker3xPullCompleted (
											int maxSecondsToBlockOnPull ,
											PullHandler pullHandler ,
											PullImageResultCallback pullImageCallback )
		throws Exception {

		boolean isComplete = false ;

		try {

			var isStatusComplete = pullImageCallback.awaitCompletion( maxSecondsToBlockOnPull, TimeUnit.SECONDS ) ;

			if ( ! isStatusComplete ) {

				logger.warn( "Failed to query state of download" ) ;

			}

			// 3.2.x - we need to delay until completed
			for ( var statusAttempts = 0; statusAttempts < maxSecondsToBlockOnPull; statusAttempts++ ) {

				var lastProgressMessage = pullHandler.getLastStatus( ) ;
				logger.info( "{} lastProgressMessage: {}", statusAttempts, lastProgressMessage ) ;
				TimeUnit.SECONDS.sleep( 1 ) ;

				if ( lastProgressMessage.contains( "Image is up to date" ) ||
						lastProgressMessage.contains( "Downloaded newer image" ) ) {

					break ;

				}

			}
			//

		} catch ( Exception e ) {

			if ( ! e.getMessage( ).contains( "Could not pull image" ) ) {

				throw e ;

			}

			logger.info( "Docker Java 3.x Apis throws 'Could not pull image' exception on success: {}", e
					.getMessage( ) ) ;
			logger.debug( "Docker Java 3.x Apis throws exceptions everytime: {}", CSAP.buildCsapStack( e ) ) ;

			// if ( e.getMessage().contains( "pull access denied" ) ) {
			// throw e ;
			// }
			// logger.info( "Docker Java 3.x Apis throws exceptions everytime: {}",
			// e.getMessage() ) ;

		}

		logger.info( "last completed status: {}", pullHandler.getLastStatus( ) ) ;

		if ( pullHandler.getLastStatus( ).contains( "Image is up to date" ) ||
				pullHandler.getLastStatus( ).contains( "Downloaded newer image" ) ) {

			isComplete = true ;

		}

		return isComplete ;

	}

	private List<String> jsonStringList ( String jsonInput )
		throws Exception {

		String hostname = Application.getInstance( ).getHostFqdn( ) ;

		if ( Application.isRunningOnDesktop( ) ) {

			logger.debug( "Swapping hostname to csap-dev04" ) ;
			hostname = "csap-dev04.***REMOVED***" ;

		}

		jsonInput = jsonInput.replaceAll( "_HOST_NAME_", hostname ) ;
		JsonNode jsonArray = jsonMapper.readTree( jsonInput ) ;
		List<String> trimmedList = CsapCore
				.jsonStream( jsonArray )
				.map( JsonNode::asText )
				.map( String::trim )
				.collect( Collectors.toList( ) ) ;

		return trimmedList ;

	}

	public String jsonAsString ( JsonNode found , String defaultIfNull ) {

		if ( found == null )
			return defaultIfNull ;

		return found.toString( ) ;

	}

	public static String getNetworkSafeContainerName ( String name ) {

		return name
				.toLowerCase( )
				.trim( )
				.replaceAll(
						Matcher.quoteReplacement( "_" ),
						Matcher.quoteReplacement( "-" ) ) ;

	}

	public ObjectNode containerCreateAndStart (
												ServiceInstance serviceInstance ,
												ObjectNode dockerDetails )
		throws Exception {

		ObjectNode result = jsonMapper.createObjectNode( ) ;

		try {

			// String containerName = dockerDetails.path( DockerJson.containerName.json()
			// ).asText( "$instance" ) ;
			String containerName = serviceInstance.getDockerContainerName( ) ;
			containerName = getNetworkSafeContainerName(
					Application.getInstance( ).resolveDefinitionVariables( containerName, serviceInstance ) ) ;

			String imageName = dockerDetails
					.path( DockerJson.imageName.json( ) )
					.asText( DOCKER_DEFAULT_IMAGE ) ;

			result = containerCreate(
					serviceInstance,
					true,
					imageName,
					containerName,
					jsonAsString( dockerDetails.get( DockerJson.command.json( ) ), "[]" ),
					jsonAsString( dockerDetails.get( DockerJson.entryPoint.json( ) ), "[]" ),
					dockerDetails.path( DockerJson.workingDirectory.json( ) ).asText( "" ),
					jsonAsString( dockerDetails.get( DockerJson.network.json( ) ), "" ),
					dockerDetails.path( DockerJson.restartPolicy.json( ) ).asText( "unless-stopped" ),
					dockerDetails.path( DockerJson.runUser.json( ) ).asText( "" ),
					jsonAsString( dockerDetails.get( DockerJson.portMappings.json( ) ), "" ),
					jsonAsString( dockerDetails.get( DockerJson.volumes.json( ) ), "" ),
					jsonAsString( dockerDetails.get( DockerJson.environmentVariables.json( ) ), "" ),
					jsonAsString( dockerDetails.get( DockerJson.limits.json( ) ), "" ) ) ;

		} catch ( Exception e ) {

			result = buildErrorResponse( "Failed creating container for service:" + serviceInstance.getName( ), e ) ;

		}

		return result ;

	}

	public ObjectNode containerCreate (
										ServiceInstance serviceInstance ,
										boolean start ,
										String imageName ,
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

		logger.info( "Creating container named: {} using Image: {},"
				+ "\n\t command: {}, entry: {}"
				+ "\n\t workingDirectory: {}"
				+ "\n\t network: {}"
				+ "\n\t restartPolicy: {}"
				+ "\n\t runUser: {}"
				+ "\n\t ports: {}, \n\n\t volumes: {}, \n\n\t environmentVariables: {} , \n\n\t limits: {}",
				name, imageName, command, entry, workingDirectory,
				network, restartPolicy, runUser,
				ports, volumes, environmentVariables, limits ) ;

		Timer.Sample deployTimer = dockerConfig.getCsapApp( ).metrics( ).startTimer( ) ;

		if ( ! imageName.contains( ":" ) ) {

			logger.warn( "image does not contain a label, appending latest" ) ;
			imageName += ":latest" ;

		}

		StringBuilder creationMessage = new StringBuilder( ) ;

		ObjectNode result = jsonMapper.createObjectNode( ) ;

		try {

			CreateContainerCmd dockerCreateCommand = dockerClient
					.createContainerCmd( imageName ) ;

			HostConfig hostSettings = HostConfig.newHostConfig( ) ;
			dockerCreateCommand.withHostConfig( hostSettings ) ;

			creationMessage.append( "\n\t usingImage: " + imageName ) ;

			if ( StringUtils.isNotEmpty( name ) ) {

				dockerCreateCommand.withName( name ) ;

				if ( name.endsWith( "-root" ) ) {

					hostSettings.withPrivileged( true ) ;

				}

				creationMessage.append( "\n\t name: " + name ) ;

			}

			if ( StringUtils.isNotEmpty( workingDirectory ) ) {

				if ( serviceInstance != null ) {

					workingDirectory = Application.getInstance( ).resolveDefinitionVariables( workingDirectory,
							serviceInstance ) ;

				}

				dockerCreateCommand.withWorkingDir( workingDirectory ) ;
				creationMessage.append( "\n\t workingDirectory: " + workingDirectory ) ;

			}

			if ( StringUtils.isNotEmpty( network ) ) {

				ObjectNode networkResults = containerCreateNetwork(
						serviceInstance,
						network,
						hostSettings ) ;

				result.set( DockerJson.response_network_create.json( ), networkResults ) ;
				creationMessage.append( "\n\t networkResults: " + network.toString( ) ) ;

			}

			if ( StringUtils.isNotEmpty( restartPolicy ) ) {

				hostSettings.withRestartPolicy( RestartPolicy.parse( restartPolicy ) ) ;

			}

			if ( StringUtils.isNotEmpty( runUser ) ) {

				runUser = processContainerRunUser( runUser, creationMessage ) ;
				dockerCreateCommand.withUser( runUser ) ;

				// }
			}

			int jmxPort = -1 ;

			if ( StringUtils.isNotEmpty( command ) ) {

				List<String> commandList = jsonStringList( command ) ;

				if ( ! commandList.isEmpty( ) ) {

					updateDockerJavaParameter( serviceInstance, commandList ) ;

					int jmxPortc = updateJavaJmxAndTomcatParameters( serviceInstance, commandList ) ;
					if ( jmxPortc > 1000 )
						jmxPort = jmxPortc ;

					logger.debug( "commandList: {}", commandList ) ;
					creationMessage.append( "\n\n\t commandList: " + commandList.toString( ) ) ;
					dockerCreateCommand.withCmd( commandList ) ;

				}

			}

			if ( StringUtils.isNotEmpty( entry ) ) {

				List<String> entryList = jsonStringList( entry ) ;

				if ( ! entryList.isEmpty( ) ) {

					updateDockerJavaParameter( serviceInstance, entryList ) ;

					int jmxPortc = updateJavaJmxAndTomcatParameters( serviceInstance, entryList ) ;
					if ( jmxPortc > 1000 )
						jmxPort = jmxPortc ;

					logger.debug( "entryList: {}", entryList ) ;
					creationMessage.append( "\n\n\t entryList: " + entryList.toString( ) ) ;
					dockerCreateCommand.withEntrypoint( entryList ) ;

				}

			}

			jmxPort = containerEnvironmentVariables( environmentVariables, jmxPort, serviceInstance, creationMessage,
					dockerCreateCommand ) ;

			if ( jmxPort > 1000 || StringUtils.isNotEmpty( ports ) ) {

				List<ExposedPort> exposedList = containerPorts( jmxPort, ports, serviceInstance, creationMessage,
						hostSettings ) ;
				dockerCreateCommand.withExposedPorts( exposedList ) ;

			}

			if ( StringUtils.isNotEmpty( volumes ) ) {

				ObjectNode volumeResults = containerCreateVolumes(
						serviceInstance,
						volumes,
						dockerCreateCommand ) ;

				creationMessage.append( "\n\n\t volumeResults: " + volumeResults.toString( ) ) ;
				result.set( DockerJson.response_volume_create.json( ), volumeResults ) ;

			}

			if ( StringUtils.isNotEmpty( limits ) ) {

				processLimits( runUser, limits, creationMessage, hostSettings ) ;

			}

			logger.info( "Docker Container Being created: {}", creationMessage ) ;

			CreateContainerResponse createReponse = dockerCreateCommand.exec( ) ;

			result.set( "createResponse", jsonMapper.convertValue( createReponse, ObjectNode.class ) ) ;

			InspectContainerResponse containerInfo = dockerClient.inspectContainerCmd( createReponse.getId( ) )
					.exec( ) ;
			int numAttempts = 3 ;

			while ( containerInfo == null && numAttempts-- > 0 ) {

				Thread.sleep( 500 ) ;
				containerInfo = dockerClient.inspectContainerCmd( createReponse.getId( ) ).exec( ) ;
				logger.info( "Polling for creation complete" ) ;

			}

			if ( start ) {

				ObjectNode startResults = containerStart( containerInfo.getId( ), name ) ;
				result.set( DockerJson.response_start_results.json( ), startResults ) ;

			}

			result.set( "container", jsonMapper.convertValue( containerInfo, ObjectNode.class ) ) ;

			// OsCommandRunner
			// /sys/fs/cgroup/cpu,cpuacct/system.slice/docker-e0f2fd1010ce4d2b8f2ef566b5843c24a2006defa44ff456ad566f58a562ee30.scope
		} catch ( Exception e ) {

			String reason = CSAP.buildCsapStack( e ) ;
			logger.warn( "Failed creating {}, {}", name, reason ) ;

			if ( e.getClass( ).getSimpleName( ).toLowerCase( ).contains( "notmodified" ) ) {

				result.put( DockerJson.response_info.json( ), "Container was already running: " + name ) ;

			} else {

				result.setAll( buildErrorResponse( "Failed creating container: " + name, e ) ) ;

			}

		}

		dockerConfig.getCsapApp( ).metrics( ).stopTimer( deployTimer, DEPLOY_TIMER ) ;

		return result ;

	}

	private int containerEnvironmentVariables (
												String environmentVariables ,
												int jmxPort ,
												ServiceInstance serviceInstance ,
												StringBuilder creationMessage ,
												CreateContainerCmd dockerCreateCommand )
		throws Exception ,
		JsonParseException ,
		JsonMappingException ,
		IOException {

		List<String> variablesUpdated ;

		if ( StringUtils.isEmpty( environmentVariables ) ) {

			variablesUpdated = new ArrayList<>( ) ;

		} else {

			List<String> envList = jsonStringList( environmentVariables ) ;

			int jmxPortc = updateJavaJmxAndTomcatParameters( serviceInstance, envList ) ;

			if ( jmxPortc > 1000 ) {

				jmxPort = jmxPortc ;

			}

			variablesUpdated = envList ;

			if ( serviceInstance != null ) {

				variablesUpdated = envList
						.stream( )
						.map( variable -> Application.getInstance( ).resolveDefinitionVariables( variable,
								serviceInstance ) )
						.filter( variable -> {

							boolean isCorrect = true ;
							if ( variable.length( ) < 3 )
								isCorrect = false ;
							if ( ! variable.contains( "=" ) )
								isCorrect = false ;
							return isCorrect ;

						} )
						.collect( Collectors.toList( ) ) ;

			}

			logger.debug( "envList: {}, \n\n variablesUpdated: {}", envList, variablesUpdated ) ;

		}

		// default to eastern - override as needed
		// if needed - updated default: timedatectl --no-pager | grep 'Time
		// zone' | awk '{print $3}'
		variablesUpdated.add( 0, "TZ=US/Eastern" ) ;
		creationMessage.append( "\n\n\t environment variables: " + variablesUpdated.toString( ) ) ;
		dockerCreateCommand.withEnv( variablesUpdated ) ;
		return jmxPort ;

	}

	private String processContainerRunUser ( String runUser , StringBuilder creationMessage )
		throws IOException {

		if ( runUser.trim( ).equals( "$csapUser" ) ) {

			runUser = "$" + System.getProperty( "user.name" ) ;

		}

		runUser = runUser.trim( ) ;

		if ( runUser.startsWith( "$" ) && runUser.length( ) > 1 ) {

			String[] uidScript = {
					"#!/bin/bash",
					"id -u " + runUser.substring( 1 )
			} ;
			String uid = osCommandRunner.runUsingRootUser(
					"find-uid",
					Arrays.asList( uidScript ) )
					.trim( ) ;

			String[] groupScript = {
					"#!/bin/bash",
					"id -g " + runUser.substring( 1 )
			} ;

			String groupid = osCommandRunner.runUsingRootUser(
					"find-gid",
					Arrays.asList( groupScript ) )
					.trim( ) ;

			runUser = uid + ":" + groupid ;

		}

		logger.debug( "Setting user: {}", runUser ) ;
		creationMessage.append( "\n\t runUser: " + runUser ) ;

		// if ( Application.isRunningOnDesktop() ) {
		// logger.warn( "Skipping user on desktop" );
		// } else {

		if ( runUser.startsWith( "Desktop Stubbed out" ) ) {

			runUser = "1000:1000" ;
			logger.warn( "Found desktop message, using default uud:gid: '{}'", runUser ) ;

		}

		return runUser ;

	}

	private void processLimits (
									String runUser ,
									String limits ,
									StringBuilder creationMessage ,
									HostConfig hostSettings )
		throws IOException ,
		Exception {

		LogConfig logConfig = null ;

		JsonNode limitsObject = jsonMapper.readTree( limits ) ;

		if ( limitsObject.has( "cpuCoresAssigned" ) ) {

			hostSettings.withCpusetCpus( limitsObject.get( "cpuCoresAssigned" ).asText( ) ) ;

		}

		if ( limitsObject.has( "memoryInMb" ) ) {

			hostSettings.withMemory( limitsObject.get( "memoryInMb" ).asLong( ) * CsapCore.MB_FROM_BYTES ) ;

		}

		if ( limitsObject.has( "cpuCoresMax" ) ) {

			long coresMax = limitsObject.path( "cpuCoresMax" ).asLong( ) ;
			logger.info( "coresMax assigned via OS commands post startup: {}", coresMax ) ;
			var cpuIntervalMs = 100 ;
			var cpuQuotaMs = Math.toIntExact( Math.round( cpuIntervalMs * coresMax ) ) ;

			hostSettings.withCpuCount( coresMax ) ;

			// missing api for quota - reverting to native cgroup
			// command
			// int basePeriod = 100000 ; // 100 ms
			// dockerCreateCommand.withCpuPeriod( basePeriod ) ;
			// dockerCreateCommand.withCpuQuota( Math.toIntExact(
			// Math.round( basePeriod * coresMax) ) ) ;
		}

		if ( limitsObject.has( "ulimits" ) ) {

			JsonNode limtArray = limitsObject.get( "ulimits" ) ;

			List<Ulimit> ulimits = new ArrayList<>( ) ;

			limtArray.forEach( limitDef -> {

				ulimits.add(
						new Ulimit(
								limitDef.get( "name" ).asText( ),
								limitDef.get( "soft" ).asInt( ),
								limitDef.get( "hard" ).asInt( ) ) ) ;

			} ) ;
			hostSettings.withUlimits( ulimits ) ;

			if ( runUser != null && ! runUser.isEmpty( ) &&
					limitsObject.has( "skipValidation" ) && ! limitsObject.get( "skipValidation" ).asBoolean( ) ) {

				String warning = "Found ulimits and  user specified for container: causes start to fail unless extended ACLS."
						+ " Remove ulimits, or remove userid, or set skipValidation to try anyway." ;
				throw new Exception( warning ) ;

			}

		}

		if ( limitsObject.has( "logs" ) ) {

			JsonNode logSettings = limitsObject.get( "logs" ) ;

			if ( logSettings.has( "type" ) && logSettings.get( "type" ).asText( ).equals( "json-file" ) ) {

				Map<String, String> jsonLogConfig = new HashMap<>( ) ;
				jsonLogConfig.put( "max-size", "10m" ) ;
				jsonLogConfig.put( "max-file", "2" ) ;

				logSettings.fieldNames( ).forEachRemaining( logField -> {

					if ( ! logField.equals( "type" ) ) {

						jsonLogConfig.put( logField, logSettings.get( logField ).asText( ) ) ;

					}

				} ) ;

				logConfig = new LogConfig( LoggingType.JSON_FILE, jsonLogConfig ) ;

			}

		}

		if ( logConfig != null ) {

			logger.info( "Log type: {} , settings: {}", logConfig.getType( ), logConfig.getConfig( ) ) ;

			creationMessage.append( "\n\t  logConfig.getConfig(): " + logConfig.getConfig( ) ) ;
			hostSettings.withLogConfig( logConfig ) ;

		}

		return ;

	}

	private List<ExposedPort> containerPorts (
												int jmxPort ,
												String ports ,
												ServiceInstance serviceInstance ,
												StringBuilder creationMessage ,
												HostConfig hostSettings )
		throws IOException {

		List<ExposedPort> exposedList = new ArrayList<>( ) ;
		Ports portBindings = new Ports( ) ;

		ArrayNode portArray ;

		if ( ports == null || ports.isEmpty( ) ) {

			portArray = jsonMapper.createArrayNode( ) ;

		} else {

			portArray = (ArrayNode) jsonMapper.readTree( ports ) ;

		}

		if ( jmxPort > 1000 ) {

			ObjectNode portObject = portArray.addObject( ) ;
			portObject.put( DockerJson.privatePort.json( ), jmxPort ) ;
			portObject.put( DockerJson.publicPort.json( ), jmxPort ) ;

		}

		portArray.forEach( portItem -> {

			int privatePortInt = portItem.path( DockerJson.privatePort.json( ) ).asInt( 0 ) ;

			if ( privatePortInt != 0 ) {

				String privatePortString = portItem.path( DockerJson.privatePort.json( ) ).asText( ) ;

				if ( serviceInstance != null && privatePortString.startsWith( CsapCore.CSAP_VARIABLE_PREFIX ) ) {

					privatePortString = Application.getInstance( ).resolveDefinitionVariables( privatePortString,
							serviceInstance ) ;
					privatePortInt = Integer.parseInt( privatePortString ) ;

				}

				int publicPort = portItem.path( DockerJson.publicPort.json( ) ).asInt( ) ;

				String publicPortString = portItem.path( DockerJson.publicPort.json( ) ).asText( ) ;

				if ( serviceInstance != null && publicPortString.startsWith( CsapCore.CSAP_VARIABLE_PREFIX ) ) {

					publicPortString = Application.getInstance( ).resolveDefinitionVariables( publicPortString,
							serviceInstance ) ;
					publicPort = Integer.parseInt( publicPortString ) ;

				}

				var exposedPort = ExposedPort.tcp( privatePortInt ) ;
				var protocol = portItem.path( DockerJson.protocol.json( ) ).asText( "tcp" ) ;

				if ( protocol.equalsIgnoreCase( "udp" ) ) {

					exposedPort = ExposedPort.udp( privatePortInt ) ;

				}

				portBindings.bind(
						exposedPort,
						Ports.Binding.bindPort( publicPort ) ) ;

				exposedList.add( exposedPort ) ;

			} else {

				creationMessage.append(
						"\n\n\t WARNING: invalid value for: " + portItem + " key " + DockerJson.privatePort.json( )
								.toString( ) ) ;

			}

		} ) ;

		logger.debug( "portBindings: {}", portBindings ) ;

		creationMessage.append( "\n\n\t portBindings: " + portBindings.toString( ) ) ;
		hostSettings.withPortBindings( portBindings ) ;
		return exposedList ;

	}

	private ObjectNode containerCreateNetwork (
												ServiceInstance serviceInstance ,
												String network ,
												HostConfig hostConfig )

		throws JsonProcessingException ,
		IOException {

		ObjectNode result = jsonMapper.createObjectNode( ) ;
		JsonNode networkDefinition = jsonMapper.readTree( network ) ;

		if ( networkDefinition.has( DockerJson.network_name.json( ) ) ) {

			String networkMode = networkDefinition.get( DockerJson.network_name.json( ) ).asText( ) ;

			hostConfig.withNetworkMode( networkMode ) ;

			if ( networkDefinition.has( DockerJson.create_persistent.json( ) ) &&
					networkDefinition.get( DockerJson.create_persistent.json( ) ).has( "enabled" ) &&
					networkDefinition.get( DockerJson.create_persistent.json( ) ).get( "enabled" ).asBoolean( ) ) {

				result.set(
						DockerJson.create_persistent.json( ),
						networkCreate( networkMode,
								networkDefinition
										.get( DockerJson.create_persistent.json( ) )
										.get( DockerJson.network_driver.json( ) ).asText( ),
								true ) ) ;

			}

		}

		return result ;

	}

	private ObjectNode containerCreateVolumes (
												ServiceInstance serviceInstance ,
												String volumes ,
												CreateContainerCmd dockerCreateCommand )
		throws IOException ,
		JsonProcessingException {

		ObjectNode result = jsonMapper.createObjectNode( ) ;
		List<Bind> dockerVolumeBinds = new ArrayList<>( ) ;
		List<Volume> localVolumes = new ArrayList<>( ) ;

		ArrayNode volumeArray = (ArrayNode) jsonMapper.readTree( volumes ) ;

		ArrayNode createPersistentResults = result.putArray( DockerJson.create_persistent.json( ) ) ;

		// support customizations for volumes using default settings
		volumeArray.forEach( volumeDef -> {

			String hostPath = volumeDef.path( DockerJson.volume_host_path.json( ) ).asText( "" ) ;

			if ( serviceInstance != null ) {

				hostPath = Application.getInstance( ).resolveDefinitionVariables( hostPath, serviceInstance ) ;

			}

			String driver = volumeDef.at( "/" + DockerJson.create_persistent.json( ) + "/driver" ).asText(
					"no-driver-specified" ) ;

			File hostVolume = new File( hostPath ) ;

			if ( driver.equals( "host" ) && ! Application.isRunningOnDesktop( ) ) {

				// this is a OS folder
				try {

					hostVolume = hostVolume.getCanonicalFile( ) ;
					logger.debug( "hostVolume: {}", hostVolume.getAbsolutePath( ) ) ;
					hostPath = hostVolume.getPath( ) ;

				} catch ( IOException e ) {

					logger.warn( "Failed to get file: {} {}", hostVolume.getPath( ), CSAP.buildCsapStack( e ) ) ;

				}

			}

			if ( StringUtils.isEmpty( hostPath ) ) {

				String containerMount = volumeDef.path( "containerMount" ).asText( ) ;

				if ( StringUtils.isNotEmpty( containerMount ) ) {

					localVolumes.add( new Volume( containerMount ) ) ;

				}

			} else {

				if ( volumeDef.has( DockerJson.create_persistent.json( ) ) &&
						volumeDef.get( DockerJson.create_persistent.json( ) ).has( "driver" ) ) {

				}

				if ( volumeDef.has( DockerJson.create_persistent.json( ) ) &&
						volumeDef.get( DockerJson.create_persistent.json( ) ).has( "driver" ) &&
						volumeDef.get( DockerJson.create_persistent.json( ) ).has( "enabled" ) &&
						volumeDef.at( "/" + DockerJson.create_persistent.json( ) + "/enabled" ).asBoolean( ) ) {

					if ( ! driver.equals( "host" ) ) {

						createPersistentResults.add(
								volumeCreate( hostPath, driver, true ) ) ;

					} else {

						ObjectNode hostResult = createPersistentResults.addObject( ) ;
						hostResult.put( "name", hostVolume.getAbsolutePath( ) ) ;

						if ( hostVolume.isFile( ) ) {

							hostResult.setAll(
									buildErrorResponse( "Path requested is an existing file",
											new Exception( "Conflicting resource" ) ) ) ;

						} else if ( hostVolume.isDirectory( ) ) {

							hostResult.put( DockerJson.response_info.json( ), SKIPPING_VOLUME_CREATE ) ;

						} else {

							hostResult.put( DockerJson.response_info.json( ), "Creating os volume" ) ;
							boolean isHostVolumeCreated = hostVolume.mkdirs( ) ;

							if ( ! isHostVolumeCreated ) {

								String[] volumeCreateScript = {
										"#!/bin/bash",
										"mkdir --parents --verbose " + hostVolume.getAbsolutePath( ),
										"chmod 777 " + hostVolume.getAbsolutePath( ),
										"ls -ld " + hostVolume.getAbsolutePath( )
								} ;

								try {

									String folderCreateOutput = osCommandRunner.runUsingRootUser(
											"docker-folder-create",
											Arrays.asList( volumeCreateScript ) ) ;
									hostResult.put( "docker-folder-create", folderCreateOutput ) ;
									logger.info( "folderCreateOutput: {}", folderCreateOutput ) ;

								} catch ( Exception e ) {

									logger.warn( "Failed running volume script: {}", CSAP.buildCsapStack( e ) ) ;

								}

							}

							hostResult.put( "isCreated", hostVolume.exists( ) ) ;
							// if ( !isHostVolumeCreated ) {
							// hostResult.setAll(
							// buildErrorResponse( "Unable to create folder - verify path and permissions",
							// new Exception( "FailedToCreate" ) ) ) ;
							// }

						}

					}

				}

				AccessMode accessMode = AccessMode.rw ;

				if ( volumeDef.path( "readOnly" ).asBoolean( false ) ) {

					accessMode = AccessMode.ro ;

				}

				SELContext context = SELContext.DEFAULT ;

				if ( volumeDef.path( "sharedUser" ).asBoolean( false ) ) {

					context = SELContext.shared ;

				}

				String containerMount = volumeDef.path( "containerMount" ).asText( "/not.found" ) ;

				if ( serviceInstance != null ) {

					// hostPath = serviceInstance.resolveRuntimeVariables( hostPath ) ;
					containerMount = Application.getInstance( ).resolveDefinitionVariables( containerMount,
							serviceInstance ) ;

				}

				Volume theVolume = new Volume( containerMount ) ;
				Bind volumeBind = new Bind(
						hostPath,
						theVolume,
						accessMode, context ) ;

				dockerVolumeBinds.add( volumeBind ) ;

			}

		} ) ;

		logger.debug( "dockerVolumeBinds: {}", dockerVolumeBinds ) ;
		dockerCreateCommand.getHostConfig( ).withBinds( dockerVolumeBinds ) ;
		dockerCreateCommand.withVolumes( localVolumes ) ;

		return result ;

	}

	private void updateDockerJavaParameter ( ServiceInstance serviceInstance , List<String> commandOrEntryParameters ) {

		Optional<String> csapJavaParameter = commandOrEntryParameters
				.stream( )
				.filter( item -> item.contains( CsapCore.DOCKER_JAVA_PARAMETER ) )
				.findFirst( ) ;

		if ( csapJavaParameter.isPresent( ) ) {

			int javaCommandIndex = commandOrEntryParameters.indexOf( csapJavaParameter.get( ) ) ;

			String javaParam = csapJavaParameter.get( ).trim( ) ;

			boolean isShellWrapper = ! javaParam.equals( CsapCore.DOCKER_JAVA_PARAMETER ) ;

			if ( isShellWrapper ) {

				String dockerJavaParams = " -Djava.security.egd=file:/dev/./urandom " ;

				if ( serviceInstance != null ) {

					// Add csapProcessId
					dockerJavaParams += " -DcsapProcessId=" + serviceInstance.getName( ) + " " ;
					dockerJavaParams += " " + CsapCore.JMX_PARAMETER + serviceInstance.getJmxPort( ) + " " ;

				}

				String updatedCommand = csapJavaParameter.get( ).replaceAll( CsapCore.DOCKER_JAVA_PARAMETER,
						dockerJavaParams ) ;
				commandOrEntryParameters.set( javaCommandIndex, updatedCommand ) ;

			} else {

				commandOrEntryParameters.set( javaCommandIndex, "-Djava.security.egd=file:/dev/./urandom" ) ;

				if ( serviceInstance != null ) {

					// Add csapProcessId
					commandOrEntryParameters.add( javaCommandIndex, " -DcsapProcessId=" + serviceInstance.getName( ) ) ;
					commandOrEntryParameters.add( javaCommandIndex, CsapCore.JMX_PARAMETER + serviceInstance
							.getJmxPort( ) ) ;

				}

			}

		}

		return ;

	}

	/**
	 * 
	 * Two scenarios require updating parameters: -DcsapDockerJava
	 * -DcsapJmxPort=$jmxPort - this is short hand for adding required jmx
	 * configuration, including adding port to exposed list
	 * 
	 *
	 */
	private int updateJavaJmxAndTomcatParameters (
													ServiceInstance serviceInstance ,
													List<String> commandOrEntryOrEnvItems )
		throws JsonParseException ,
		JsonMappingException ,
		IOException {

		int jmxPort = -1 ;
		boolean isDockerShellWrapper = false ;
		String matchedFullParameter = null ;

		// Use the csap model for jmx ports if docker_java parameter is
		// specified
		Optional<String> javaDockerParameter = Optional.empty( ) ;

		if ( serviceInstance != null ) {

			javaDockerParameter = commandOrEntryOrEnvItems
					.stream( )
					.filter( item -> item.contains( CsapCore.DOCKER_JAVA_PARAMETER ) )
					.findFirst( ) ;

			if ( javaDockerParameter.isPresent( ) ) {

				if ( serviceInstance.isJavaJmxCollectionEnabled( ) ) {

					try {

						jmxPort = Integer.parseInt( serviceInstance.getJmxPort( ) ) ;

					} catch ( NumberFormatException e ) {

						logger.warn( "Failed parsing jmxPort: {}", serviceInstance.getJmxPort( ) ) ;

					}

				}

				matchedFullParameter = javaDockerParameter.get( ) ;
				// eg. JAVA_OPTS=-DcsapDockerJava
				isDockerShellWrapper = ! matchedFullParameter.startsWith( CsapCore.DOCKER_JAVA_PARAMETER ) ;

			}

		}

		//
		// Handle when image is started from Host dashboard
		//
		if ( ! javaDockerParameter.isPresent( ) ) {

			Optional<String> jmxPortParamOptional = commandOrEntryOrEnvItems
					.stream( )
					.filter( item -> item.contains( CsapCore.JMX_PARAMETER ) )
					.findFirst( ) ;

			if ( jmxPortParamOptional.isPresent( ) ) {

				try {

					matchedFullParameter = jmxPortParamOptional.get( ) ;
					String jmxPortParam = jmxPortParamOptional.get( ).trim( ) ;
					isDockerShellWrapper = ! jmxPortParam.startsWith( CsapCore.JMX_PARAMETER ) ;

					jmxPortParam = jmxPortParam.substring( jmxPortParam.indexOf( CsapCore.JMX_PARAMETER ) ) ;
					String portString = jmxPortParam.substring( CsapCore.JMX_PARAMETER.length( ) ).trim( ) ;

					int spaceIndex = portString.indexOf( " " ) ;

					if ( spaceIndex != -1 ) {

						// shellWrapper
						portString = portString.substring( 0, spaceIndex ) ;

					}

					if ( serviceInstance != null ) {

						portString = Application.getInstance( ).resolveDefinitionVariables( portString,
								serviceInstance ) ;

					}

					jmxPort = Integer.parseInt( portString ) ;

				} catch ( Exception e ) {

					String reason = CSAP.buildCsapStack( e ) ;
					logger.warn( "Failed parsing '{}', {}", jmxPortParamOptional.get( ), reason ) ;

				}

			}

		}

		boolean isDockerShell = isDockerShellWrapper ; // for stream

		if ( jmxPort > 1000 ) {

			int commandEntryEnv_index = commandOrEntryOrEnvItems.indexOf( matchedFullParameter ) ;

			JsonNode jmxParameterTemplate = Application.getInstance( ).getServiceTemplates( ).get( "javaJmx" ) ;
			ArrayList<String> jmxParams = jsonMapper.readValue(
					jmxParameterTemplate.traverse( ),
					new TypeReference<ArrayList<String>>( ) {
					} ) ;

			String portString = Integer.toString( jmxPort ) ;

			StringBuilder expandedParameterForShellLaunch = new StringBuilder( ) ;

			jmxParams
					.stream( )
					.map( jmxParam -> jmxParam.replaceAll( "_HOST_NAME_", Application.getInstance( ).getHostFqdn( ) ) )
					.map( jmxParam -> jmxParam.replaceAll( "_JMX_PORT_", portString ) )
					.forEach( jmxParam -> {

						if ( isDockerShell ) {

							// add to shell parameters
							expandedParameterForShellLaunch.append( jmxParam + " " ) ;

						} else {

							// update in place
							commandOrEntryOrEnvItems.add( commandEntryEnv_index, jmxParam ) ;

						}

					} ) ;

			// For shell - we are updating even more stuff...typically tomcat
			// env vars
			if ( isDockerShell ) {

				if ( serviceInstance != null && javaDockerParameter.isPresent( ) ) {

					// Add java flags if not updated previously
					expandedParameterForShellLaunch.append( " -DcsapProcessId=" + serviceInstance.getName( ) + " " ) ;
					expandedParameterForShellLaunch.append( " -Djava.security.egd=file:/dev/./urandom " ) ;

				}

				StringBuilder updatedItem = new StringBuilder( matchedFullParameter ) ;
				int insertPoint = updatedItem.indexOf( CsapCore.DOCKER_JAVA_PARAMETER ) ;

				if ( insertPoint == -1 ) {

					insertPoint = updatedItem.indexOf( CsapCore.JMX_PARAMETER ) ;

				}

				updatedItem.insert( insertPoint, expandedParameterForShellLaunch ) ;
				commandOrEntryOrEnvItems.set( commandEntryEnv_index, updatedItem.toString( ) ) ;

			}

		}

		// update service variables
		if ( serviceInstance != null ) {

			for ( int i = 0; i < commandOrEntryOrEnvItems.size( ); i++ ) {

				commandOrEntryOrEnvItems.set( i,
						Application.getInstance( ).resolveDefinitionVariables(
								commandOrEntryOrEnvItems.get( i ), serviceInstance ) ) ;

			}

		}

		logger.debug( "jmx string: {}, port: {}, commandlist: {}",
				matchedFullParameter, jmxPort, commandOrEntryOrEnvItems ) ;
		return jmxPort ;

	}

	OsCommandRunner osCommandRunner = new OsCommandRunner( 120, 2, this.getClass( ).getSimpleName( ) ) ;

	public String updateContainerCpuAllow ( int cpuPeriodMs , int cpuQuotaMs , String containerId ) {

		logger.info( "Updating quota: periodMs: {} , quotaMs: {}, containerId: {}",
				cpuPeriodMs, cpuQuotaMs, containerId ) ;

		String[] lines = {
				"#!/bin/bash",
				"cd /sys/fs/cgroup/cpu/system.slice/docker-" + containerId + ".scope",
				"echo  " + cpuPeriodMs * 1000 + " > cpu.cfs_period_us",
				"echo  " + cpuQuotaMs * 1000 + " > cpu.cfs_quota_us",
				"echo == updated container cpu period " + cpuPeriodMs + "ms and quota " + cpuQuotaMs + "ms in: `pwd` ",
				""
		} ;

		String scriptOutput = "Failed to run" ;

		try {

			scriptOutput = osCommandRunner.runUsingRootUser( "dockerCpu", Arrays.asList( lines ) ) ;

		} catch ( Exception e ) {

			logger.info( "Failed to update cpu settings: {} ", CSAP.buildCsapStack( e ) ) ;
			scriptOutput += ", reason: " + e.getMessage( ) + " type: " + e.getClass( ).getName( ) ;

		}

		return scriptOutput ;

	}

	public String dockerOpenSockets ( String pid ) {

		// String[] lines = {
		// "#!/bin/bash",
		// socketStatCommand( pid ) + "r", // resolve host names
		// "" };

		List<String> lines = osCommands.getDockerSocketStats( pid ) ;

		String scriptOutput = "Failed to run" ;

		var timer = getDockerConfig( ).getCsapApp( ).metrics( ).startTimer( ) ;

		try {

			scriptOutput = osCommandRunner.runUsingRootUser( "dockerSocketStat", lines ) ;
			logger.debug( "output from: {}  , \n{}", lines, scriptOutput ) ;

		} catch ( IOException e ) {

			logger.info( "Failed to run docker nsenter: {} , \n reason: {}", lines,
					CSAP.buildCsapStack( e ) ) ;
			scriptOutput += ", reason: " + e.getMessage( ) + " type: " +
					e.getClass( ).getName( ) ;

		}

		getDockerConfig( ).getCsapApp( ).metrics( ).stopTimer( timer, "collect-docker.socket-stat" ) ;
		return scriptOutput ;

	}

	private boolean isKubernetesPodWrapper ( Container container ) {

		// other labels to filer: "annotation.kubernetes.io/config.source", "k8s-app"
		if ( ( container.getLabels( ) != null ) ) {

			String k8Type = container.getLabels( ).get( "io.kubernetes.docker.type" ) ;

			if ( ( k8Type != null )
					&& k8Type.equals( "podsandbox" ) ) {

				return true ;

			}

		}

		return false ;

	}

	public ArrayNode containerListing ( boolean showFilteredItems ) {

		var containerListing = jsonMapper.createArrayNode( ) ;

		try {

			List<Container> containers = dockerClient.listContainersCmd( ).withShowAll( true ).exec( ) ;

			containers.forEach( container -> {

				// logger.debug( "container: \n {}", container.toString( ) ) ;

				if ( ! showFilteredItems &&
						isKubernetesPodWrapper( container ) ) {

					return ;

				}

				ObjectNode item = containerListing.addObject( ) ;

				String label = Arrays.asList( container.getNames( ) ).toString( ) ;

				if ( container.getNames( ).length == 1 ) {

					label = container.getNames( )[ 0 ] ;

				}

				long disk = 0 ;
				String usage = getLatestContainersDiskUsage( ) ;

				if ( StringUtils.isEmpty( usage ) ) {

					disk = -1 ;

				} else {

					String[] usageLines = usage.split( "\n" ) ;

					for ( String line : usageLines ) {

						if ( line.contains( label.substring( 1 ) ) ) {

							try {

								disk = Long.parseLong( line.split( " " )[ 0 ] ) ;

							} catch ( Exception e ) {

								logger.warn( "Failed to parse {}", CSAP.buildCsapStack( e ) ) ;

							}

						}

					}

				}

				item.put( "label", label ) ;

				ObjectNode attributes = jsonMapper.convertValue( container, ObjectNode.class ) ;
				attributes.put( "allVolumesInMb", disk ) ;
				item.set( "attributes", attributes ) ;
				attributes.put( "Create Date", getContainerCreationTime( container ) ) ;
				item.put( "folder", true ) ;
				item.put( "lazy", true ) ;

			} ) ;

			if ( containers.size( ) == 0 ) {

				ObjectNode item = containerListing.addObject( ) ;
				item.put( DockerJson.error.json( ), "No containers defined" ) ;

			}

		} catch ( Exception e ) {

			containerListing.add( buildErrorResponse( "VolumeList ", e ) ) ;

		}

		return containerListing ;

	}

	public ObjectNode containerRemove (
										String id ,
										String name ,
										boolean force )
		throws Exception {

		ObjectNode result = jsonMapper.createObjectNode( ) ;

		try {

			String containerId = getContainerId( id, name ) ;

			if ( containerId == null ) {

				result.put( DockerJson.error.json( ), "Failed removing: " + name ) ;
				result.put( DockerJson.errorReason.json( ), "Unable to locate container id" ) ;

			} else {

				Integer timeoutInSeconds = 20 ;

				// stop triggers gracefull shutdown of resources
				try {

					dockerClient
							.stopContainerCmd( containerId )
							.withTimeout( timeoutInSeconds )
							.exec( ) ;

				} catch ( Exception e ) {

					String reason = e.getMessage( ) ;

					if ( reason == null ) {

						reason = e.getClass( ).getSimpleName( ) ;

					}

					result.put( "stop-not-accepted", reason ) ;
					logger.info( "Failed to stop {}", CSAP.buildCsapStack( e ) ) ;

				}

				InspectContainerResponse containerDetails = dockerClient.inspectContainerCmd( containerId ).exec( ) ;
				result.put( "state-after-stop", containerDetails.getState( ).getStatus( ) ) ;

				// force will ensure container is not left around
				dockerClient
						.removeContainerCmd( containerId )
						.withForce( force )
						.withRemoveVolumes( true )
						.exec( ) ;
				result.put( DockerJson.response_info.json( ), "removed container: " + containerId ) ;

			}
			// InspectContainerResponse info = getContainerStatus(
			// containerName );
			// result.set( "state", jacksonMapper.convertValue(
			// info.getState(), ObjectNode.class ) );

		} catch ( Exception e ) {

			result = buildErrorResponse( "Failed removing: " + name, e ) ;

		}

		return result ;

	}

	public ObjectNode containerStart (
										String id ,
										String name )
		throws Exception {

		ObjectNode result = jsonMapper.createObjectNode( ) ;

		try {

			String targetId = getContainerId( id, name ) ;

			if ( targetId != null && ! targetId.isEmpty( ) ) {

				dockerClient.startContainerCmd( targetId ).exec( ) ;
				result.put( DockerJson.response_info.json( ), "Started container: " + name + " id:" + targetId ) ;
				InspectContainerResponse info = dockerClient.inspectContainerCmd( targetId ).exec( ) ;
				;
				result.set( "state", jsonMapper.convertValue( info.getState( ), ObjectNode.class ) ) ;

			} else {

				result.put( DockerJson.error.json( ), "Container not found: " + name ) ;

			}

		} catch ( Exception e ) {

			String reason = CSAP.buildCsapStack( e ) ;
			logger.warn( "Failed starting {}, {}", name, reason ) ;

			if ( e.getClass( ).getSimpleName( ).toLowerCase( ).contains( "notmodified" ) ) {

				result.put( DockerJson.response_info.json( ), "Container was already running: " + name ) ;

			} else {

				result = buildErrorResponse( "Failed starting: " + name, e ) ;

			}

		}

		return result ;

	}

	public String getContainerCreationTime ( Container container ) {

		long secondsSinceEpoch = container.getCreated( ) ;
		LocalDateTime createDT = LocalDateTime.ofInstant( Instant.ofEpochMilli( secondsSinceEpoch * 1000 ),
				ZoneId.systemDefault( ) ) ;
		return createDT.format( DateTimeFormatter.ofPattern( "HH:mm:ss MMM d, yyyy" ) ) ;

	}

	public File containerLogPath ( String containerName ) {

		File logPath = null ;

		InspectContainerResponse response = containerConfiguration( containerName ) ;

		if ( response != null ) {

			logPath = new File( response.getLogPath( ) ) ;

		} else {

			// kubernetes
		}

		return logPath ;

	}

	public InspectContainerResponse containerConfiguration ( String name ) {

		Optional<Container> matchContainer = findContainerByName( name ) ;

		if ( matchContainer.isPresent( ) ) {

			return dockerClient.inspectContainerCmd( matchContainer.get( ).getId( ) ).exec( ) ;

		}

		return null ;

	}

	public ObjectNode containerStop (
										String id ,
										String name ,
										boolean kill ,
										int stopSeconds ) {

		logger.info( "Stopping container: {} ", name ) ;

		ObjectNode result = jsonMapper.createObjectNode( ) ;

		try {

			Optional<Container> matchContainer = findContainerByName( name ) ;

			if ( matchContainer.isPresent( ) ) {

				if ( kill ) {

					dockerClient
							.killContainerCmd( matchContainer.get( ).getId( ) )
							.exec( ) ;

				} else {

					dockerClient
							.stopContainerCmd( matchContainer.get( ).getId( ) )
							.withTimeout( stopSeconds )
							.exec( ) ;

				}

				result.put( DockerJson.response_info.json( ), "Stopped container: " + name ) ;
				InspectContainerResponse info = containerConfiguration( name ) ;
				result.set( "state", jsonMapper.convertValue( info.getState( ), ObjectNode.class ) ) ;

			} else {

				result.put( DockerJson.error.json( ), "Container not found: " + name ) ;

			}

		} catch ( Exception e ) {

			if ( e.getClass( ).getSimpleName( ).toLowerCase( ).contains( "notmodified" ) ) {

				result.put( DockerJson.response_info.json( ), "Container was already stopped: " + name ) ;

			} else {

				result = buildErrorResponse( "Failed stopping: " + name, e ) ;

			}

		}

		return result ;

	}

	private String getContainerId ( String id , String name ) {

		String targetId = id ;

		if ( StringUtils.isEmpty( id ) ) {

			Optional<Container> matchContainer = findContainerByName( name ) ;

			if ( matchContainer.isPresent( ) ) {

				targetId = matchContainer.get( ).getId( ) ;

			} else {

				targetId = null ;

			}

		}

		return targetId ;

	}

	private ObjectNode buildErrorResponse ( String description , Exception failureExeption ) {

		String reason = CSAP.buildCsapStack( failureExeption ) ;

		check_docker_fatal_error( reason ) ;

		ObjectNode result = jsonMapper.createObjectNode( ) ;

		logger.warn( "Failure: {} {}", description, reason ) ;
		logger.debug( "detailed stack", failureExeption ) ;

		result.put( DockerJson.error.json( ), description ) ;

		String uiMessage = failureExeption.getMessage( ) ;

		try {

			String testParse = jsonMapper.readTree( uiMessage ).path( "message" ).asText( "" ) ;

			if ( testParse.length( ) > 5 ) {

				uiMessage = testParse ;

			}

		} catch ( Exception e1 ) {

			logger.error( "Failed to parse uiMessage message", CSAP.buildCsapStack( e1 ) ) ;

		}

		result.put( DockerJson.errorReason.json( ), uiMessage + ". Reference: " + failureExeption.getClass( )
				.getSimpleName( ) ) ;
		return result ;

	}

	public ObjectNode volumeCreate ( String name , String driver , boolean skipIfExists ) {

		ObjectNode result = jsonMapper.createObjectNode( ) ;
		result.put( "Name", name ) ;
		result.put( "driver", driver ) ;

		if ( ! driver.equals( "host" ) && ! driver.equals( "local" ) ) {

			result.put( DockerJson.warning.json( ), "Experimental driver detected: " + driver ) ;

		}

		try {

			if ( skipIfExists && volumeNames( ).contains( name ) ) {

				result.put( DockerJson.response_info.json( ), SKIPPING_VOLUME_CREATE ) ;
				result.set( DockerJson.response_volume_list.json( ), jsonMapper.convertValue( volumeNames( ),
						ArrayNode.class ) ) ;
				return result ;

			}

			CreateVolumeResponse response = dockerClient.createVolumeCmd( )
					.withDriver( driver )
					.withName( name )
					.exec( ) ;

			result.setAll( jsonMapper.convertValue( response, ObjectNode.class ) ) ;

		} catch ( Exception e ) {

			result.setAll( buildErrorResponse( "Failed creating volume", e ) ) ;

		}

		return result ;

	}

	public List<String> volumeNames ( ) {

		List<String> nameList ;

		ArrayNode listing = volumeList( ) ;

		nameList = CSAP.jsonStream( listing )
				.map( volume -> volume.get( DockerJson.list_label.json( ) ).asText( ) )
				.collect( Collectors.toList( ) ) ;

		return nameList ;

	}

	public ObjectNode volumeDelete ( String name ) {

		ObjectNode result = jsonMapper.createObjectNode( ) ;
		result.put( "name", name ) ;

		try {

			dockerClient.removeVolumeCmd( name )
					.exec( ) ;

			result.set(
					DockerJson.response_volume_list.json( ),
					jsonMapper.convertValue(
							volumeNames( ),
							ArrayNode.class ) ) ;

		} catch ( NotFoundException e ) {

			result.put( DockerJson.response_info.json( ), "Volume did not exist, command ignored" ) ;

		} catch ( Exception e ) {

			result = buildErrorResponse( "Removing Volume: " + name, e ) ;

		}

		return result ;

	}

	public ArrayNode volumeList ( ) {

		ArrayNode volumeListing = jsonMapper.createArrayNode( ) ;

		try {

			ListVolumesResponse volumeResponse = dockerClient.listVolumesCmd( ).exec( ) ;

			if ( volumeResponse.getVolumes( ) == null ) {

				// ObjectNode item = volumeListing.addObject();
				// item.put( DockerJson.error.json(), "No volumes defined" );

			} else {

				volumeResponse.getVolumes( ).forEach( volume -> {

					logger.debug( "volume name: {} \n {}",
							volume.getName( ), volume.toString( ) ) ;

					ObjectNode volumeJson = jsonMapper.convertValue( volume, ObjectNode.class ) ;

					logger.debug( "volume: \n {}", volume.toString( ) ) ;

					ObjectNode item = volumeListing.addObject( ) ;
					item.put( DockerJson.list_label.json( ), volume.getName( ) ) ;

					InspectVolumeResponse volumeInpect = dockerClient.inspectVolumeCmd( volume.getName( ) ).exec( ) ;
					ObjectNode inspectJson = jsonMapper.convertValue( volumeInpect, ObjectNode.class ) ;

					item.set( DockerJson.list_attributes.json( ), inspectJson ) ;
					item.put( "folder", true ) ;
					item.put( "lazy", true ) ;

				} ) ;

			}

		} catch ( Exception e ) {

			volumeListing.add( buildErrorResponse( "VolumeList ", e ) ) ;

		}

		return volumeListing ;

	}

	private String dockerDiskUsageCache = "" ;

	public String getLatestContainersDiskUsage ( ) {

		return dockerDiskUsageCache ;

	}

	public String collectContainersDiskUsage ( Application csapApp ) {

		String[] docker_collect_script = {
				"#!/bin/bash",
				csapApp.csapPlatformPath( ContainerIntegration.DOCKER_VOLUMES_SCRIPT ).getAbsolutePath( )
						+ " short", // short parameter does not detail all volume information
				""
		} ;

		var timer = getDockerConfig( ).getCsapApp( ).metrics( ).startTimer( ) ;

		try {

			var diskUsageInMbForAllContainers = osCommandRunner.runUsingRootUser(
					"du_for_docker",
					Arrays.asList( docker_collect_script ) ) ;

			logger.debug( "dockerContainerSizeTotaled: {}", diskUsageInMbForAllContainers ) ;

			diskUsageInMbForAllContainers = csapApp.check_for_stub( diskUsageInMbForAllContainers,
					"linux/ps-docker-volumes.txt" ) ;

			dockerDiskUsageCache = diskUsageInMbForAllContainers ;

		} catch ( Exception e ) {

			logger.warn( "Failed collecting diskusage", CSAP.buildCsapStack( e ) ) ;

		}

		getDockerConfig( ).getCsapApp( ).metrics( ).stopTimer( timer, "collect-docker.disk-usage" ) ;

		return dockerDiskUsageCache ;

	}

	public JsonNode networkCreate ( String name , String driver , boolean skipIfExists ) {

		ObjectNode result = jsonMapper.createObjectNode( ) ;
		result.put( "name", name ) ;

		try {

			if ( skipIfExists && networkNames( ).contains( name ) ) {

				result.put( DockerJson.response_info.json( ), SKIPPING_NETWORK_CREATE ) ;

				result.set( DockerJson.response_network_list.json( ),
						jsonMapper.convertValue( networkNames( ), ArrayNode.class ) ) ;

				return result ;

			}

			CreateNetworkResponse response = dockerClient.createNetworkCmd( )
					.withDriver( driver )
					.withName( name )
					.exec( ) ;

			result.setAll( jsonMapper.convertValue( response, ObjectNode.class ) ) ;

		} catch ( Exception e ) {

			result = buildErrorResponse( "Creating Network: " + name + " Driver: " + driver, e ) ;

		}

		return result ;

	}

	public ObjectNode networkDelete ( String nameOrId ) {

		ObjectNode result ;

		try {

			dockerClient.removeNetworkCmd( nameOrId )
					.exec( ) ;

			result = jsonMapper.createObjectNode( ) ;
			result.set(
					DockerJson.response_network_list.json( ),
					jsonMapper.convertValue(
							networkNames( ),
							ArrayNode.class ) ) ;

		} catch ( Exception e ) {

			result = buildErrorResponse( "Removing Network: " + nameOrId, e ) ;

		}

		return result ;

	}

	public List<String> networkNames ( ) {

		List<String> names = CSAP.jsonStream( networkList( ) )
				.map( volume -> volume.get( DockerJson.list_label.json( ) ).asText( ) )
				.collect( Collectors.toList( ) ) ;

		return names ;

	}

	public ArrayNode networkList ( ) {

		ArrayNode networkListing = jsonMapper.createArrayNode( ) ;

		try {

			List<Network> networks = dockerClient.listNetworksCmd( ).exec( ) ;

			networks.forEach( network -> {

				logger.debug( "network name: {}, \n {}",
						network.getName( ), network.toString( ) ) ;

				ObjectNode networkJson = jsonMapper.convertValue( network, ObjectNode.class ) ;
				ObjectNode networkContainers = (ObjectNode) networkJson.get( "Containers" ) ;

				ObjectNode item = networkListing.addObject( ) ;
				item.put( DockerJson.list_label.json( ), network.getName( ) ) ;

				// replace ID with container name if it exists
				if ( network.getContainers( ) != null ) {

					network.getContainers( ).entrySet( ).stream( )
							.forEach( containerEntry -> {

								String id = containerEntry.getKey( ) ;
								InspectContainerResponse containerResponse = dockerClient.inspectContainerCmd( id )
										.exec( ) ;
								logger.debug( "containerResponse: {}", containerResponse ) ;

								if ( containerResponse != null && containerResponse.getName( ) != null ) {

									ObjectNode networkContainer = (ObjectNode) networkContainers.remove( id ) ;
									networkContainers.set( containerResponse.getName( ), networkContainer ) ;

									networkContainer.set( "containerInfo",
											jsonMapper.convertValue( containerResponse, ObjectNode.class ) ) ;

								}

								// dockerHelper.findContainerByName(
								// containerEntry.getKey() ) ;
							} ) ;
					;

				}

				item.set( DockerJson.list_attributes.json( ), networkJson ) ;
				item.put( "folder", true ) ;
				item.put( "lazy", true ) ;

			} ) ;

		} catch ( Exception e ) {

			networkListing.add( buildErrorResponse( "Network listing failed ", e ) ) ;

		}

		return networkListing ;

	}

	public OutputFileMgr get_pullOutputManager ( ) {

		return _pullOutputManager ;

	}

	public void set_pullOutputManager ( OutputFileMgr outputFileMgr ) {

		this._pullOutputManager = outputFileMgr ;

	}

	public boolean isFoundPullError ( ) {

		return foundPullError ;

	}

	public void setFoundPullError ( boolean foundError ) {

		this.foundPullError = foundError ;

	}

	public boolean isPullSuccess ( ) {

		return pullSuccess ;

	}

	public void setPullSuccess ( boolean pullSuccess ) {

		this.pullSuccess = pullSuccess ;

	}

	public DockerSettings getSettings ( ) {

		return settings ;

	}

	public void setSettings ( DockerSettings settings ) {

		this.settings = settings ;

	}

	public DockerClient getDockerClient ( ) {

		return dockerClient ;

	}

	public DockerConfiguration getDockerConfig ( ) {

		return dockerConfig ;

	}

}
