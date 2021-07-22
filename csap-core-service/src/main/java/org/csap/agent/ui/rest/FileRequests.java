package org.csap.agent.ui.rest ;

import java.io.BufferedWriter ;
import java.io.DataInputStream ;
import java.io.File ;
import java.io.FileInputStream ;
import java.io.FileNotFoundException ;
import java.io.FileWriter ;
import java.io.IOException ;
import java.io.PrintWriter ;
import java.io.RandomAccessFile ;
import java.nio.file.Files ;
import java.nio.file.Path ;
import java.text.DateFormat ;
import java.text.ParseException ;
import java.text.SimpleDateFormat ;
import java.time.LocalDateTime ;
import java.time.format.DateTimeFormatter ;
import java.util.ArrayList ;
import java.util.Arrays ;
import java.util.Date ;
import java.util.HashMap ;
import java.util.List ;
import java.util.Map ;
import java.util.TreeMap ;
import java.util.concurrent.atomic.AtomicReference ;
import java.util.regex.Matcher ;
import java.util.regex.Pattern ;
import java.util.stream.Collectors ;
import java.util.stream.Stream ;

import javax.inject.Inject ;
import javax.servlet.ServletOutputStream ;
import javax.servlet.http.HttpServletRequest ;
import javax.servlet.http.HttpServletResponse ;
import javax.servlet.http.HttpSession ;

import org.apache.commons.io.FileUtils ;
import org.apache.commons.lang3.StringUtils ;
import org.csap.agent.CsapCore ;
import org.csap.agent.CsapCoreService ;
import org.csap.agent.api.AgentApi ;
import org.csap.agent.container.DockerIntegration ;
import org.csap.agent.integrations.CsapEvents ;
import org.csap.agent.linux.OsCommandRunner ;
import org.csap.agent.model.Application ;
import org.csap.agent.model.Application.FileToken ;
import org.csap.agent.model.ProcessRuntime ;
import org.csap.agent.model.ServiceBase ;
import org.csap.agent.model.ServiceInstance ;
import org.csap.agent.services.OsManager ;
import org.csap.agent.ui.windows.CorePortals ;
import org.csap.docs.CsapDoc ;
import org.csap.helpers.CSAP ;
import org.csap.helpers.CsapApplication ;
import org.csap.integations.CsapInformation ;
import org.csap.integations.CsapWebServerConfig ;
import org.csap.security.CsapUser ;
import org.csap.security.config.CsapSecurityRoles ;
import org.csap.security.config.CsapSecuritySettings ;
import org.csap.security.oath2.CsapOauth2SecurityConfiguration ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.beans.factory.annotation.Autowired ;
import org.springframework.http.HttpStatus ;
import org.springframework.http.MediaType ;
import org.springframework.stereotype.Controller ;
import org.springframework.ui.ModelMap ;
import org.springframework.util.LinkedMultiValueMap ;
import org.springframework.util.MultiValueMap ;
import org.springframework.web.bind.annotation.DeleteMapping ;
import org.springframework.web.bind.annotation.GetMapping ;
import org.springframework.web.bind.annotation.PathVariable ;
import org.springframework.web.bind.annotation.PostMapping ;
import org.springframework.web.bind.annotation.PutMapping ;
import org.springframework.web.bind.annotation.RequestMapping ;
import org.springframework.web.bind.annotation.RequestParam ;
import org.springframework.web.bind.annotation.ResponseBody ;
import org.springframework.web.server.ResponseStatusException ;

import com.fasterxml.jackson.core.JsonProcessingException ;
import com.fasterxml.jackson.databind.JsonNode ;
import com.fasterxml.jackson.databind.ObjectMapper ;
import com.fasterxml.jackson.databind.node.ArrayNode ;
import com.fasterxml.jackson.databind.node.ObjectNode ;

/**
 *
 * UI controller for browsing/viewing/editing files
 *
 * @author someDeveloper
 *
 *
 * @see <a href=
 *      "http://static.springsource.org/spring/docs/current/spring-framework-reference/html/mvc.html">
 *      SpringMvc Docs </a>
 *
 * @see SpringContext_agentSvcServlet
 *
 *
 */
@Controller
@RequestMapping ( CsapCoreService.FILE_URL )
@CsapDoc ( title = "File Operations" , notes = {
		"File browser/manager, and associated rest operations. Includes viewing, saving, editing files",
		"<a class='csap-link' target='_blank' href='https://github.com/csap-platform/csap-core/wiki'>learn more</a>",
		"<img class='csapDocImage' src='CSAP_BASE/images/portals.png' />"
} )
public class FileRequests {

	public static final String FILE_CHANGES_URL = "/getFileChanges" ;

	private static final String DOCKER_HOST_FILE_TOKEN = "//" ;
	private static final String NAMESPACE_PVC_TOKEN = "namespacePvc:" ;

	final Logger logger = LoggerFactory.getLogger( FileRequests.class ) ;

	@Inject
	public FileRequests ( Application csapApp,
			CsapEvents csapEventClient ) {

		this.csapApp = csapApp ;
		this.csapEventClient = csapEventClient ;

	}

	Application csapApp ;

	@Autowired ( required = false )
	CsapSecuritySettings securitySettings ;

	CsapEvents csapEventClient ;

	OsCommandRunner osCommandRunner = new OsCommandRunner( 90, 3, "FileRequests" ) ;

	@RequestMapping ( "propertyEncoder" )
	public String propertyEncoder (
									ModelMap modelMap ,
									@RequestParam ( value = "path" , required = false , defaultValue = "none" ) String path ,
									HttpServletRequest request ,
									HttpSession session )
		throws IOException {

		setCommonAttributes( modelMap, request, "Property Encoder" ) ;
		modelMap.addAttribute( "name", csapApp.getCsapHostName( ) ) ;
		return "misc/property-encoder" ;

	}

	public static final String FILE_MANAGER = "/FileManager" ;

	@RequestMapping ( FILE_MANAGER )
	public String fileManager (
								@RequestParam ( value = CsapCore.SERVICE_PORT_PARAM , required = false ) String serviceFlexId ,
								@RequestParam ( value = "fromFolder" , defaultValue = "." ) String fromFolder ,
								@RequestParam ( value = "showDu" , required = false ) String showDu ,
								String nfs ,
								String containerName ,
								ModelMap modelMap ,
								HttpServletRequest request ,
								HttpSession session ) {

		ServiceInstance service = null ;

		if ( StringUtils.isNotEmpty( serviceFlexId ) ) {

			service = csapApp.flexFindFirstInstanceCurrentHost( serviceFlexId ) ;

		}

		if ( service != null ) {

			modelMap.addAttribute( "serviceName", service.getName( ) ) ;

		}

		if ( StringUtils.isNotEmpty( nfs ) ) {

			fromFolder = findNfsPath( fromFolder, nfs ) ;

		}

		if ( containerName != null ) {

			modelMap.addAttribute( "containerName", containerName ) ;

		}

		setCommonAttributes( modelMap, request, "File Manager" ) ;

		modelMap.addAttribute( "fromFolder", fromFolder ) ;

		// Tool tips
		Map<String, String> diskPathsForTips = new HashMap<>( ) ;
		modelMap.addAttribute( "diskPathsForTips", diskPathsForTips ) ;

		var workingFolder = csapApp.getRequestedFile( fromFolder, serviceFlexId, false ) ;

		if ( workingFolder.exists( ) ) {

			diskPathsForTips.put( "fromDisk", pathForTips( fromFolder, serviceFlexId ) ) ;

		} else {

			diskPathsForTips.put( "fromDisk", ServiceBase.CSAP_FOLDER_NOT_CONFIGURED ) ;

		}

		if ( StringUtils.isNotEmpty( nfs ) ) {

			// hook for nfs on ui in admin
			diskPathsForTips.put( "fromDisk", FileToken.ROOT.value + "/" + fromFolder ) ;

		}

		diskPathsForTips.put( "homeDisk", pathForTips( FileToken.HOME.value, serviceFlexId ) ) ;
		diskPathsForTips.put( "stagingDisk", pathForTips( FileToken.PLATFORM.value, serviceFlexId ) ) ;
		diskPathsForTips.put( "installDisk", pathForTips( FileToken.PLATFORM.value + "/..", serviceFlexId ) ) ;
		diskPathsForTips.put( "processingDisk", pathForTips( csapApp.getCsapWorkingFolder( ).getAbsolutePath( ),
				serviceFlexId ) ) ;

		if ( service != null ) {

//			diskPathsForTips.put( "appDisk", pathForTips( FileToken.ROOT.value, service.getAppDirectory( ) ) ) ;
//			diskPathsForTips.put( "propDisk", pathForTips( FileToken.PROPERTY.value, serviceFlexId ) ) ;

			diskPathsForTips.put( "fromDisk", service.getWorkingDirectory( ).getAbsolutePath( ) ) ;

			if ( ! service.getWorkingDirectory( ).exists( ) ) {

				diskPathsForTips.remove( "fromDisk" ) ;

			}

			if ( ! service.getAppDirectory( ).equals( ServiceBase.CSAP_FOLDER_NOT_CONFIGURED ) ) {

				diskPathsForTips.put( "appDisk", service.getAppDirectory( ) ) ;

			}

			// use fully resolved paths for configuration and app folders
			diskPathsForTips.put( "propDisk",
					csapApp.getRequestedFile(
							FileToken.PROPERTY.value, serviceFlexId, false )
							.getAbsolutePath( ) ) ;

			File jmeterReportFolder = new File( service.getWorkingDirectory( ), "jmeter/reports" ) ;
			logger.debug( "Checking: {}", jmeterReportFolder ) ;

			if ( jmeterReportFolder.exists( ) ) {

				diskPathsForTips.put( "jmeterDisk", pathForTips( fromFolder + "/jmeter/reports", serviceFlexId ) ) ;

			}

			if ( service.is_docker_server( )
					|| ( dockerIntegration != null
							&& dockerIntegration.is_docker_folder( service ) ) ) {

				String baseDocker = containerName ;

				if ( StringUtils.isEmpty( containerName ) ) {

					// legacy
					baseDocker = dockerIntegration.determineDockerContainerName( service ) ;

				}

				modelMap.addAttribute( "dockerBase", baseDocker ) ;

				// String baseDocker = dockerIntegration.determineDockerContainerName( service
				// );

				var serviceProperyFolder = csapApp.resolveDefinitionVariables( service.getPropDirectory( ), service ) ;

				if ( serviceProperyFolder.startsWith( NAMESPACE_PVC_TOKEN ) ) {

					serviceProperyFolder = buildNamespaceListingPath( service, serviceProperyFolder ) ;

					diskPathsForTips.put( "propDisk", serviceProperyFolder ) ;

				} else if ( ! serviceProperyFolder.startsWith( DOCKER_HOST_FILE_TOKEN ) ) {

					diskPathsForTips.put( "propDisk", "dockerContainer:" + serviceProperyFolder ) ;

				} else {

					diskPathsForTips.put( "propDisk", serviceProperyFolder.substring( 1 ) ) ;

				}

				var serviceAppFolder = csapApp.resolveDefinitionVariables( service.getAppDirectory( ), service ) ;

				if ( serviceAppFolder.startsWith( NAMESPACE_PVC_TOKEN ) ) {

					serviceAppFolder = buildNamespaceListingPath( service, serviceAppFolder ) ;

					diskPathsForTips.put( "appDisk", serviceAppFolder ) ;

				} else if ( ! serviceAppFolder.startsWith( DOCKER_HOST_FILE_TOKEN ) ) {

					if ( serviceAppFolder.contains( ServiceBase.CSAP_FOLDER_NOT_CONFIGURED ) ) {

						serviceAppFolder = "/" ;

					}

					;

					diskPathsForTips.put( "appDisk", "dockerContainer:" + serviceAppFolder ) ;

					String appPath = "dockerContainer:" + serviceAppFolder ;
					diskPathsForTips.put( "appDisk", appPath ) ;

				} else {

					diskPathsForTips.put( "appDisk", serviceAppFolder.substring( 1 ) ) ;

				}

			}

		}

		if ( csapApp.isDockerInstalledAndActive( ) ) {

			try {

				String dockerRoot = dockerIntegration.buildSummary( ).path( "rootDirectory" ).asText( "_error_" ) ;
				diskPathsForTips.put( "dockerDisk", dockerRoot ) ;

				if ( isDockerFolder( fromFolder ) ) {

					String dockerTarget = fromFolder.substring( Application.FileToken.DOCKER.value.length( ) ) ;
					diskPathsForTips.put( "fromDisk", dockerRoot + "~" + dockerTarget ) ;

				}

			} catch ( Exception e ) {

				logger.error( "Failed to parse docker information: {}", CSAP.buildCsapStack( e ) ) ;

			}

		}

		try {

			File kubletFolder = new File( "/var/lib/kubelet" ) ;

			if ( kubletFolder.isDirectory( ) ) {

				diskPathsForTips.put( "kubernetesDisk", "/var/lib/kubelet" ) ;

			}

		} catch ( Exception e ) {

			logger.error( "Failed to parse docker information: {}", CSAP.buildCsapStack( e ) ) ;

		}

		return CsapCoreService.FILE_URL + "/file-browser" ;

	}

	private String buildNamespaceListingPath ( ServiceInstance service , String serviceProperyFolder ) {

		var volumeReports = csapApp.getKubernetesIntegration( ).reportsBuilder( ).volumeReport( ) ;
		var podNamespace = service.getDefaultContainer( ).getPodNamespace( ) ;

		if ( StringUtils.isNotEmpty( podNamespace ) ) {

			if ( csapApp.isDesktopHost( ) ) {

				logger.warn( CsapApplication.testHeader( "setting test namespace for {}" ),
						serviceProperyFolder ) ;

			}

			var matchedVolumes = CSAP.jsonStream( volumeReports )
					.filter( volumeReport -> volumeReport.path( "ref-namespace" ).asText( ).startsWith(
							podNamespace ) )
					.collect( Collectors.toList( ) ) ;

			if ( matchedVolumes.size( ) != 1 ) {

				logger.warn( "Unexpected match count: {} \n {}",
						matchedVolumes.size( ),
						matchedVolumes ) ;

			}

			var nfsSubPath = serviceProperyFolder.substring( NAMESPACE_PVC_TOKEN.length( ) ) ;

			for ( var volumeReport : matchedVolumes ) {

				// var volumeReport = matchedVolumes.get( 0 ) ;

				var volumeNfsServer = volumeReport.path( "nfs-server" ).asText( "nfs-missing" ) ;

				var volumePath = volumeReport.path( "path" ).asText( "path-missing" ) ;

				var nfsPath = findNfsPath( volumePath, volumeNfsServer ) ;

				logger.info( CSAP.buildDescription(
						"NFS property",
						"volumeNfsServer", volumeNfsServer,
						"volumePath", volumePath,
						"nfsPath", nfsPath ) ) ;

				serviceProperyFolder = nfsPath + nfsSubPath ;

				var testFolder = new File( serviceProperyFolder ) ;

				if ( testFolder.exists( ) ) {

					var testFiles = new ArrayList<String>( ) ;

					buildLogFiles(
							testFolder,
							service,
							testFiles,
							null ) ;

					if ( testFiles.size( ) > 0 ) {

						logger.info( "serviceProperyFolder: {} \n {}", serviceProperyFolder, Arrays.asList(
								testFiles ) ) ;

						// use the first PVC that contains files
						break ;

					}

				}

			}

		}

		return serviceProperyFolder ;

	}

	private String findNfsPath ( String volumePath , String volumeNfsServer ) {

		var folders = volumePath.split( "/", 3 ) ;
		var folders2 = volumePath.split( "/", 4 ) ;

		// logger.info( "fromFolder: {}, folders2: {}", fromFolder, Arrays.asList(
		// folders2 ) );
		var firstFolder = folders[1] ;
		var mountSource = volumeNfsServer + ":/" + firstFolder ;

		var mountLocation = csapApp.getOsManager( ).getMountPath( mountSource ) ;

		volumePath = mountLocation + "/" + folders[2] ;
		File testNfsFolder = new File( volumePath ) ;

		if ( ! testNfsFolder.exists( ) ) {

			// some time nfs is subfoldered, strip off another level\
			volumePath = mountLocation + "/" + folders2[3] ;
			testNfsFolder = new File( volumePath ) ;

			if ( ! testNfsFolder.exists( ) ) {

				logger.warn( "Unable to locate nfs folder: {}, \n\tfromFolder: {}, folders2: {}", testNfsFolder,
						volumePath, Arrays.asList( folders2 ) ) ;

			}

		}

		// fromFolder = FileToken.ROOT.value + fromFolder ;
		logger.info( "nfs detected - mountSource: {}, fromFolder: {}", mountSource, volumePath ) ;
		return volumePath ;

	}

	private String pathForTips ( String location , String serviceName ) {

		return csapApp.getRequestedFile( location, serviceName, false ).getAbsolutePath( ) ;

	}

	@RequestMapping ( "/browser/{browseId}" )
	public String fileBrowser (
								@PathVariable ( value = "browseId" ) String browseId ,
								@RequestParam ( value = "showDu" , required = false ) String showDu ,
								ModelMap modelMap ,
								HttpServletRequest request ,
								HttpSession session ,
								PrintWriter writer ) {

		setCommonAttributes( modelMap, request, "File Browser" ) ;
		JsonNode browseSettings = getBrowseSettings( browseId ) ;

		if ( browseSettings.isMissingNode( )
				|| ! browseSettings.has( "group" ) ) {

			// logger.info( "settingsNode: {}", settingsNode );
			writer.println( "requested browse group not found: " + browseId ) ;
			writer.println( "Contact administrator" ) ;
			return null ;

		}

		if ( csapApp.isAdminProfile( ) ) {
			// csapApp.getRootModel().getAllPackagesModel().getServiceInstances(
			// serviceName )

			String cluster = browseSettings.get( "cluster" ).asText( ) ;
			ArrayList<String> clusterHosts = csapApp.getActiveProject( ).getAllPackagesModel( )
					.getLifeClusterToHostMap( ).get( cluster ) ;

			logger.debug( "specified: {}, Keys: {}", cluster, csapApp.getActiveProject( ).getAllPackagesModel( )
					.getLifeClusterToHostMap( ).keySet( ) ) ;

			if ( clusterHosts == null || clusterHosts.size( ) == 0 ) {

				writer.println( "Incorrect browser configuration - very settings: " + browseSettings.get( "cluster" )
						.asText( ) ) ;
				return null ;

			}

			return "redirect:" + csapApp.getAgentUrl( clusterHosts.get( 0 ), "/file/browser/" + browseId, false ) ;

		}

		securitySettings.getRoles( ).addRoleIfUserHasAccess( session, browseSettings.get( "group" ).asText( ) ) ;

		if ( ! hasBrowseAccess( session, browseId ) ) {

			logger.info( "Permission denied for accessing {}, Confirm: {} is a member of: {}",
					browseId, securitySettings.getRoles( ).getUserIdFromContext( ),
					browseSettings.get( "group" ).asText( ) ) ;
			return "csap/security/accessError" ;

		}

		modelMap.addAttribute( "serviceName", null ) ;
		modelMap.addAttribute( "browseId", browseId ) ;
		modelMap.addAttribute( "browseGroup", getBrowseSettings( browseId ).get( "group" ).asText( ) ) ;

		modelMap.addAttribute( "fromFolder", Application.FileToken.ROOT.value ) ;

		return CsapCoreService.FILE_URL + "/file-browser" ;

	}

	private boolean hasBrowseAccess ( HttpSession session , String browseId ) {

		JsonNode browseSettings = getBrowseSettings( browseId ) ;

		if ( browseSettings.isMissingNode( )
				|| ! browseSettings.has( "group" ) ) {

			return false ;

		}

		logger.info( "Checking access: {}", browseSettings ) ;

		return securitySettings.getRoles( ).hasCustomRole( session, browseSettings.get( "group" ).asText( ) ) ;

	}

	private JsonNode getBrowseSettings ( String browseId ) {

		JsonNode groupFileNode = (JsonNode) csapApp.rootProjectEnvSettings( ).getFileBrowserConfig( )
				.at( "/" + browseId ) ;
		return groupFileNode ;

	}

	@Autowired
	CsapInformation csapInformation ;

	@Autowired
	CorePortals corePortals ;

	private void setCommonAttributes ( ModelMap modelMap , HttpServletRequest session , String windowName ) {

		setCommonAttributes( modelMap, session, windowName, null ) ;

	}

	private void setCommonAttributes (
										ModelMap modelMap ,
										HttpServletRequest request ,
										String windowName ,
										String apiUser ) {

		if ( StringUtils.isNotEmpty( windowName ) ) {

			var auditName = CsapCoreService.FILE_URL + "/" + windowName ;
			var userName = CsapUser.currentUsersID( ) ;

			if ( StringUtils.isNotEmpty( apiUser ) ) {

				userName = apiUser ;
				corePortals.setCommonAttributes( modelMap, null ) ;

			} else {

				corePortals.setCommonAttributes( modelMap, request.getSession( ) ) ;

			}

			if ( csapApp.getActiveUsers( ).addTrail( userName, auditName ) ) {

				csapEventClient.publishUserEvent(
						CsapEvents.CSAP_OS_CATEGORY + "/accessed",
						userName,
						"User interface: " + windowName, "" ) ;

			}

		} else {

			corePortals.setCommonAttributes( modelMap, request.getSession( ) ) ;

		}

		modelMap.addAttribute( "host", csapApp.getCsapHostName( ) ) ;

		modelMap.addAttribute( "osUsers", csapApp.buildOsUsersList( ) ) ;

		modelMap.addAttribute( "dateTime",
				LocalDateTime.now( ).format( DateTimeFormatter.ofPattern( "HH:mm:ss,   MMMM d  uuuu " ) ) ) ;

		modelMap.addAttribute( "userid", CsapUser.currentUsersID( ) ) ;

		modelMap.addAttribute( "deskTop", csapApp.isDesktopHost( ) ) ;

		modelMap.addAttribute( "user", csapApp.getAgentRunUser( ) ) ;
		modelMap.addAttribute( "csapApp", csapApp ) ;

		modelMap.addAttribute( csapApp.rootProjectEnvSettings( ) ) ;
		modelMap.addAttribute( "analyticsUrl", csapApp.rootProjectEnvSettings( ).getAnalyticsUiUrl( ) ) ;
		modelMap.addAttribute( "eventApiUrl", csapApp.rootProjectEnvSettings( ).getEventApiUrl( ) ) ;

		modelMap.addAttribute( "eventApiUrl", csapApp.rootProjectEnvSettings( ).getEventApiUrl( ) ) ;

		modelMap.addAttribute( "eventMetricsUrl",
				csapApp.rootProjectEnvSettings( ).getEventMetricsUrl( ) ) ;
		modelMap.addAttribute( "eventUser", csapApp.rootProjectEnvSettings( ).getEventDataUser( ) ) ;
		modelMap.addAttribute( "life", csapApp.getCsapHostEnvironmentName( ) ) ;

	}

	@Autowired ( required = false )
	private CsapOauth2SecurityConfiguration csapOauthConfig ;

	@GetMapping ( "/remote/listing" )
	@ResponseBody
	public String remoteFileMonitorListing (
												@RequestParam ( value = CsapCore.SERVICE_PORT_PARAM , required = false ) String serviceName ,
												String hostName ,
												String containerName ,
												String fileName ,
												String podName ,
												ModelMap modelMap ,
												HttpServletRequest request ,
												HttpSession session ) {

		if ( csapApp.isAdminProfile( ) ) {

			var requestParameters = new LinkedMultiValueMap<String, String>( ) ;

			if ( serviceName != null ) {

				requestParameters.set( CsapCore.SERVICE_PORT_PARAM, serviceName ) ;

			} else {

				requestParameters.set( "fileName", fileName ) ;
				requestParameters.set( "podName", podName ) ;

			}

			requestParameters.set( "containerName", containerName ) ;
			requestParameters.set( "apiUser", CsapUser.currentUsersID( ) ) ;

//			if ( ( serviceName != null )
//					&& serviceName.equals( "unregistered" ) ) {
//				requestParameters = new LinkedMultiValueMap<String, String>( ) ;
//				requestParameters.set( "fileName", FileToken.DOCKER.value + "" + containerName ) ;
//			}

			String url = CsapCoreService.FILE_URL + FILE_REMOTE_MONITOR ;
			List<String> hosts = new ArrayList<>( ) ;
			hosts.add( hostName ) ;

			logger.debug( " hitting: {} with {}", hostName, requestParameters ) ;

			JsonNode remoteCall = csapApp.getOsManager( ).getServiceManager( ).remoteAgentsGet(
					hosts,
					url,
					requestParameters ) ;

			// logger.info( "remoteCall: {}", CSAP.jsonPrint( remoteCall ) );

			var remoteListing = remoteCall.path( hostName ).asText( ) ;

			if ( remoteListing.startsWith( CsapCore.CONFIG_PARSE_ERROR ) ) {

				remoteListing = "error: failed to get remote listing " + url ;
				remoteListing += "\n\n" + CSAP.jsonPrint( remoteCall ) ;

			}

			return remoteListing ;

		}

		return " Admin call only" ;

	}

	final public static String FILE_MONITOR = "/FileMonitor" ;
	final public static String FILE_REMOTE_MONITOR = "/FileRemoteMonitor" ;

	@GetMapping ( value = {
			FILE_MONITOR, FILE_REMOTE_MONITOR
	} )
	public String fileMonitor (
								@RequestParam ( value = "fromFolder" , required = false ) String fromFolder ,
								@RequestParam ( value = "fileName" , required = false ) String fileName ,
								@RequestParam ( value = CsapCore.SERVICE_PORT_PARAM , required = false ) String serviceName ,
								String podName ,
								boolean agentUi ,
								String apiUser ,
								@RequestParam ( value = "containerName" , required = false ) String containerIdOrPodLabel ,
								String corsHost ,
								ModelMap modelMap ,
								HttpServletRequest request ) {

		setCommonAttributes( modelMap, request, "FileMonitor", apiUser ) ;

		logger.info( CSAP.buildDescription(
				"File Monitor launched",
				"serviceName:", serviceName,
				"fileName", fileName,
				"podName", podName,
				"containerIdOrPodLabel", containerIdOrPodLabel ) ) ;

		modelMap.addAttribute( "serviceName", serviceName ) ;
		modelMap.addAttribute( "fromFolder", fromFolder ) ;

		var fileChangeUrl = FILE_CHANGES_URL.substring( 1 ) ;

		if ( csapOauthConfig != null && StringUtils.isNotEmpty( corsHost ) ) {

			fileChangeUrl = "http://" + corsHost + ":" + request.getServerPort( ) + request.getContextPath( )
					+ CsapCoreService.FILE_URL
					+ FILE_CHANGES_URL ;
			modelMap.addAttribute( "bearerAuth", csapOauthConfig.getAuthorizationHeader( ) ) ;
			logger.info( "bearer: '{}'", csapOauthConfig.getAuthorizationHeader( ) ) ;

		}

		modelMap.addAttribute( "fileChangeUrl", fileChangeUrl ) ;

		String shortName = "tail" ;

		if ( fileName != null ) {

			shortName = ( new File( fileName ) ).getName( ) ;

		} else if ( serviceName != null ) {

			shortName = "logs " + serviceName ;

		}

		modelMap.addAttribute( "shortName", shortName ) ;
		String initialLogFileToShow = "" ;

		List<String> logFileNames = null ;

		ServiceInstance serviceInstance = null ;

		if ( StringUtils.isNotEmpty( serviceName ) ) {

			if ( ! serviceName.equals( ProcessRuntime.unregistered.getId( ) ) ) {

				serviceInstance = csapApp.flexFindFirstInstanceCurrentHost( serviceName ) ;

				if ( serviceInstance == null ) {

					logger.warn( "Requested service not found using csap-agent" ) ;
					serviceInstance = csapApp.flexFindFirstInstanceCurrentHost( CsapCore.AGENT_NAME ) ;

				}

				initialLogFileToShow = serviceInstance.getDefaultLogToShow( ) ;

				if ( StringUtils.isNotEmpty( fileName ) ) {

					initialLogFileToShow = getShortNameFromCsapFilePath( fileName ) ;

				}

				var dockerContainerId = containerIdOrPodLabel ;

				if ( serviceInstance.is_cluster_kubernetes( ) ) {

					if ( StringUtils.isEmpty( containerIdOrPodLabel ) ) {

						// handle agent dashboard - look up container name
						dockerContainerId = serviceInstance.findContainerName( serviceName ) ;
						logger.warn( " podid: {} , container name located using: {}",
								serviceName,
								containerIdOrPodLabel ) ;

					}

				} else if ( StringUtils.isNotEmpty( containerIdOrPodLabel )
						&& containerIdOrPodLabel.equals( "default" ) ) {

					// occures when docker service is stopped; but it might be there - so use the
					// default name
					dockerContainerId = serviceInstance.getDockerContainerPath( ) ;

				}

				logFileNames = build_log_list_for_service( serviceInstance, dockerContainerId ) ;

			} else {

				//
				// Unregistered containers: docker or kubernetes
				//

				fileName = "unregistered-detected" ;

				logFileNames = new ArrayList<String>( ) ;
				logFileNames.add( fileName ) ;

				initialLogFileToShow = getShortNameFromCsapFilePath( fileName ) ;

			}

		} else if ( StringUtils.isNotEmpty( fileName ) ) {

			// k8 podName means we need to lookup the container name
			if ( StringUtils.isNotEmpty( podName ) ) {

				File containerNamePath = new File( fileName ) ;
				File podNamePath = new File( podName ) ;
				fileName = Application.FileToken.DOCKER.value
						+ dockerIntegration.findDockerContainerId(
								podNamePath.getName( ),
								containerNamePath.getName( ) ) ;

			}
			// Use case: Show files in folder selected From file Browser
			// file requested will be inserted at top of list

			initialLogFileToShow = getShortNameFromCsapFilePath( fileName ) ;

			if ( fileName.startsWith( Application.FileToken.DOCKER.value )
					|| fileName.startsWith( Application.FileToken.JOURNAL.value ) ) {

				logFileNames = new ArrayList<String>( ) ;
				logFileNames.add( fileName ) ;

			} else {

				File targetFile = csapApp.getRequestedFile( fileName, serviceName, false ) ;

				if ( targetFile.getParentFile( ).exists( ) ) {

					// populate drop down with files in same folder; convenience
					// for
					// browsing.
					File[] fileArray = targetFile.getParentFile( ).listFiles( ) ;

					if ( fileArray == null ) {

						// use root
						logFileNames = new ArrayList<String>( ) ;
						logFileNames.add( fileName ) ;

					} else {

						logFileNames = build_log_list_for_file_monitor( fileName, targetFile, fileArray ) ;

					}

				}

			}

		}

		String firstNonZipFile = null ;
		boolean foundInitialDisplay = false ;

		if ( logFileNames == null || logFileNames.size( ) == 0 ) {

			logger.error( "Failed to find any matching log files: '{}'", fileName ) ;

		} else {

			Map<String, String> logFileMap = new TreeMap<>( ) ;
			Map<String, String> serviceJobMap = new TreeMap<>( ) ;
			Map<String, String> journalMap = new TreeMap<>( ) ;
			Map<String, String> csapDeployMap = new TreeMap<>( ) ;
			Map<String, String> configMap = new TreeMap<>( ) ;

			for ( String fullFilePath : logFileNames ) {

				String label = getShortNameFromCsapFilePath( fullFilePath ) ;

				if ( firstNonZipFile == null
						&& ! label.endsWith( ".gz" )
						&& ! label.startsWith( "logRotate" )
						&& ! label.endsWith( ".pid" ) ) {

					firstNonZipFile = label ;

				}

				if ( label.endsWith( initialLogFileToShow ) ) {

					foundInitialDisplay = true ;

				}

				if ( fullFilePath.startsWith( Application.FileToken.JOURNAL.value ) ) {

					journalMap.put( label.substring( 1 ), fullFilePath ) ;

				} else if ( fullFilePath.startsWith( Application.FileToken.WORKING.value ) ) {

					csapDeployMap.put( label.substring( 1 ), fullFilePath ) ;

				} else if ( label.startsWith( "serviceJobs" ) ) {

					serviceJobMap.put( label.substring( "serviceJobs".length( ) + 1 ), fullFilePath ) ;

				} else if ( label.startsWith( "logRotate" ) || label.endsWith( ".pid" ) ) {

					configMap.put( label, fullFilePath ) ;

				} else if ( agentUi
						&& fullFilePath.equals( "kubernetes-pods-detected" ) ) {

					var podContainer = serviceInstance.findPodContainer( serviceName ) ;
					logger.info( "Locating container for {} : {}", serviceName, podContainer ) ;

					if ( podContainer.isPresent( ) ) {

						// tight coupling to agent-logs.js
						var pod = podContainer.get( ) ;

						if ( StringUtils.isNotEmpty( pod.getPodName( ) ) ) {

							modelMap.addAttribute( "podContainer", pod ) ;
							var podNameFields = pod.getPodName( ).split( "-" ) ;
							var csapContainerLabel = pod.getContainerLabel( ) + " ("
									+ podNameFields[podNameFields.length - 1] + ")" ;
							modelMap.addAttribute( "csapContainerLabel", csapContainerLabel ) ;
							modelMap.addAttribute( "container", pod.getContainerLabel( ) ) ;
							modelMap.addAttribute( "pod", pod.getPodName( ) ) ;
							modelMap.addAttribute( "namespace", pod.getPodNamespace( ) ) ;

						} else {

							logger.info( "podName not set - master?" ) ;

						}

					}

					logFileMap.put( label, fullFilePath ) ;

				} else {

					logFileMap.put( label, fullFilePath ) ;

				}

			}

			;

			if ( ! logFileMap.isEmpty( ) )
				modelMap.addAttribute( "logFileMap", logFileMap ) ;

			if ( ! serviceJobMap.isEmpty( ) ) {

				modelMap.addAttribute( "serviceJobMap", serviceJobMap ) ;

			} else if ( serviceInstance != null ) {

				// support for working dir for NON relative paths
				File jobLogDir = new File( csapApp.getWorkingLogDir( serviceInstance.getServiceName_Port( ) ),
						"serviceJobs" ) ;

				if ( jobLogDir.exists( ) ) {

					File[] jobItems = jobLogDir.listFiles( ) ;

					if ( jobItems != null ) {

						for ( File jobItem : jobItems ) {

							if ( jobItem.isFile( ) ) {

								String fullFilePath = Application.FileToken.ROOT.value + "/" + jobItem.getPath( ) ;
								String label = getShortNameFromCsapFilePath( fullFilePath ) ;
								serviceJobMap.put( label.substring( "serviceJobs".length( ) + 1 ), fullFilePath ) ;

							}

						}

					}

				}

				if ( ! serviceJobMap.isEmpty( ) ) {

					modelMap.addAttribute( "serviceJobMap", serviceJobMap ) ;

				}

			}

			if ( ! journalMap.isEmpty( ) )
				modelMap.addAttribute( "journalMap", journalMap ) ;

			if ( ! csapDeployMap.isEmpty( ) )
				modelMap.addAttribute( "csapDeployMap", csapDeployMap ) ;

			if ( ! configMap.isEmpty( ) )
				modelMap.addAttribute( "configMap", configMap ) ;

			// This is used to select file in UI
		}

		if ( ! foundInitialDisplay && firstNonZipFile != null ) {

			initialLogFileToShow = firstNonZipFile ;

		}

		modelMap.addAttribute( "initialLogFileToShow", initialLogFileToShow ) ;

		logger.info( "initialLogFileToShow: '{}', firstNonZipFile: {}", initialLogFileToShow, firstNonZipFile ) ;

		return CsapCoreService.FILE_URL + "/file-monitor" ;

	}

	private List<String> build_log_list_for_service ( ServiceInstance serviceInstance , String containerName ) {

		var logFileNames = new ArrayList<String>( ) ;

		var serviceWorkingFolder = csapApp.getCsapWorkingFolder( ) ;

		if ( serviceWorkingFolder.exists( ) ) {

			var serviceWorkingFiles = serviceWorkingFolder.listFiles( ) ;

			if ( serviceWorkingFiles != null ) {

				for ( var serviceWorkingFile : serviceWorkingFiles ) {

					if ( serviceWorkingFile.isFile( )
							&& serviceWorkingFile.getName( ).startsWith( serviceInstance.getName( ) )
							&& serviceWorkingFile.getName( ).endsWith( ".log" ) ) {

						logFileNames.add( Application.FileToken.WORKING.value + "/" + serviceWorkingFile.getName( ) ) ;

					}

				}

			}

		} else {

			logger.warn( "{} working folder does not exist: {}", serviceInstance.getName( ), serviceWorkingFolder ) ;

		}

//		File serviceFolder = csapApp.getLogDir( serviceInstance.getServiceName_Port( ) ) ;
		var serviceFolder = serviceInstance.getLogWorkingDirectory( ) ;

		if ( serviceInstance.isKubernetesMaster( ) ) {

			serviceFolder = serviceInstance.getWorkingDirectory( ) ;

		}

		var useContainerForLogs = ( dockerIntegration != null )
				&& dockerIntegration.is_docker_logging( serviceInstance ) ;

		var logPath = serviceInstance.getLogDirectory( ) ;

		if ( logPath.startsWith( NAMESPACE_PVC_TOKEN ) ) {

			useContainerForLogs = false ;
			var logListingPath = buildNamespaceListingPath( serviceInstance, logPath ) ;
			logger.info( "logListingPath: {}", logListingPath ) ;
			serviceFolder = new File( logListingPath ) ;

		}

		logger.info( CSAP.buildDescription(
				"File Listing",
				"service", serviceInstance,
				"serviceFolder", serviceFolder,
				"logDirectory", serviceInstance.getLogDirectory( ),
				"logPath", logPath,
				"getLogRegEx", serviceInstance.getLogRegEx( ),
				"useContainerForLogs", useContainerForLogs,
				"defaultLog", csapApp.getDefaultLogFileName( serviceInstance.getServiceName_Port( ) ) ) ) ;

		if ( useContainerForLogs ) {

			String baseDocker = Application.FileToken.DOCKER.value + containerName ;

			if ( StringUtils.isEmpty( containerName ) ) {

				// legacy
				baseDocker = Application.FileToken.DOCKER.value
						+ dockerIntegration.determineDockerContainerName( serviceInstance ) ;

			}

			if ( serviceInstance.is_cluster_kubernetes( ) ) {

				logFileNames.add( "kubernetes-pods-detected" ) ;

			} else {

				logFileNames.add( baseDocker ) ;

			}

			String dockerLogDir = "/var/log" ;

			if ( ! serviceInstance.getLogDirectory( ).equals( "logs" ) ) {

				dockerLogDir = serviceInstance.getLogDirectory( ) ;

			}

			String baseDockerLogs = baseDocker + dockerLogDir + "/" ;

			var fileListing = buildListingUsingDocker(
					baseDockerLogs.substring( Application.FileToken.DOCKER.value.length( ) ),
					new HashMap<String, String>( ),
					baseDockerLogs ) ;

			List<String> subFileNames = new ArrayList<String>( ) ;
			CsapCore.jsonStream( fileListing ).forEach( logListing -> {

				logger.debug( "logListing: {}", logListing ) ;

				if ( ( ! logListing.has( "folder" ) ) && logListing.has( "location" ) ) {

					String location = logListing.get( "location" ).asText( ) ;

					if ( ! location.contains( DockerIntegration.MISSING_FILE_NAME ) ) {

						subFileNames.add( location ) ;

					}

				}

			} ) ;
			logFileNames.addAll( subFileNames ) ;

		}

		var logFilesWithRootSupport = new ArrayList<String>( ) ;

		buildLogFiles(
				serviceFolder,
				serviceInstance,
				logFilesWithRootSupport,
				null ) ;

		logger.debug( "logFilesWithRootSupport: {}", logFilesWithRootSupport ) ;

		if ( serviceFolder.exists( )
				|| logFilesWithRootSupport.size( ) > 0 ) {

			var serviceFolderFiles = serviceFolder.listFiles( ) ;
			var logFileNamesInSubDir = new ArrayList<String>( ) ;

			if ( serviceFolderFiles == null ) {

				// might be a root listing
				logFileNamesInSubDir.addAll( logFilesWithRootSupport ) ;

			} else {

				for ( var itemInLogFolder : serviceFolderFiles ) {

					if ( itemInLogFolder.isFile( ) ) {
						// default to the first file found
						// look for a match

						if ( ! itemInLogFolder.getName( )
								.matches( serviceInstance.getLogRegEx( ) ) ) {

							// service definitions allow for optional selectors
							// for determing log files
							// some are .txt, some .log, etc.
							continue ;

						}

						String currentItemPath = itemInLogFolder.getAbsolutePath( ) ;

						try {

							currentItemPath = itemInLogFolder.getCanonicalPath( ) ;

						} catch ( IOException e ) {

							logger.error( "Reverting to absolute path: {}", CSAP.buildCsapStack( e ) ) ;

						}

						logFileNames.add( Application.FileToken.ROOT.value + currentItemPath ) ;

					} else if ( itemInLogFolder.isDirectory( ) ) {

						buildLogFiles(
								itemInLogFolder,
								serviceInstance,
								logFileNamesInSubDir,
								"" ) ;

					}

				}

			}

			if ( logFileNamesInSubDir.size( ) != 0 ) {

				logFileNames.addAll( logFileNamesInSubDir ) ;

			}

		} else {

			logger.warn( "{} working folder does not exist: {}", serviceInstance.getName( ), serviceWorkingFolder ) ;

		}

		if ( StringUtils.isNotEmpty( serviceInstance.getLogJournalServices( ) ) ) {

			String[] systemServices = serviceInstance.getLogJournalServices( ).split( "," ) ;

			for ( String svc : systemServices ) {

				logFileNames.add( Application.FileToken.JOURNAL.value + "/" + svc.trim( ) ) ;

			}

		}

		if ( logFileNames.size( ) == 0 ) {

			logger.error( "Failed to find any matching log files: " + serviceFolder.getAbsolutePath( )
					+ " \n Processing: " + csapApp.getCsapWorkingFolder( ).getAbsolutePath( ) ) ;

		}

		logger.debug( "logFileNames: {}", logFileNames ) ;

		return logFileNames ;

	}

	private List<String> build_log_list_for_file_monitor ( String fileName , File targetFile , File[] fileArray ) {

		List<String> logFileNames = new ArrayList<>( ) ;
		logFileNames.add( fileName ) ;

		for ( File itemInLogFolder : fileArray ) {

			if ( itemInLogFolder.isFile( )
					&& ! ( itemInLogFolder.equals( targetFile ) ) ) {
				// logFileNames.add(prefix +
				// itemInLogFolder.getName());

				// use full path for passing to other UIs
				try {

					logFileNames.add( Application.FileToken.ROOT.value + itemInLogFolder.getCanonicalPath( ) ) ;

				} catch ( IOException e ) {

					logger.error( "Reverting to absolute path {}", CSAP.buildCsapStack( e ) ) ;
					logFileNames.add( Application.FileToken.ROOT.value + itemInLogFolder.getAbsolutePath( ) ) ;

				}

			}

		}

		return logFileNames ;

	}

	private String buildLogFiles (
									File folderToSearch ,
									ServiceInstance instance ,
									List<String> logFileNamesInSubDir ,
									String firstFileNameInFirstDir ) {
		// Some services have multiple subfolders in log
		// directory.
		// One directory down will also be scanned for files.

		File[] subFiles = folderToSearch.listFiles( ) ;

		var firstFileAtomic = new AtomicReference<String>( ) ;
		firstFileAtomic.getAndSet( firstFileNameInFirstDir ) ;

		if ( subFiles == null ) {

			try {

				var fileListings = buildListingUsingRoot( folderToSearch, new HashMap<String, String>( ), "notUsed" ) ;

				CSAP.jsonStream( fileListings )

						.filter( fileListing -> ! fileListing.has( "folder" ) )

						.filter( fileListing -> fileListing.path( "name" ).asText( )
								.matches( instance.getLogRegEx( ) ) )

						.forEach( fileListing -> {

							var fileName = fileListing.path( "name" ).asText( ) ;

							var filePath = folderToSearch.getName( ) + "/" + fileName ;

							if ( StringUtils.isEmpty( firstFileAtomic.get( ) ) ) {

								firstFileAtomic.getAndSet( filePath ) ;

							}

							// logFileNamesInSubDir.add(path);
							// use full path for passing to other UIs
							try {

								logFileNamesInSubDir.add( Application.FileToken.ROOT.value
										+ folderToSearch.getCanonicalPath( ) + "/" + fileName ) ;

							} catch ( Exception e ) {

								logger.error( "Reverting to absolute path {}", CSAP.buildCsapStack( e ) ) ;
								logFileNamesInSubDir.add( Application.FileToken.ROOT.value
										+ folderToSearch.getAbsolutePath( ) + "/" + fileName ) ;

							}

						} ) ;

			} catch ( Exception e1 ) {

				logger.warn( "Failed getting log files: {}", CSAP.buildCsapStack( e1 ) ) ;

			}

		} else {

			for ( var subFile : subFiles ) {

				if ( subFile.isFile( ) ) {

					if ( ! subFile.getName( ).matches(
							instance.getLogRegEx( ) ) ) {

						continue ;

					}

					var filePath = folderToSearch.getName( ) + "/" + subFile.getName( ) ;

					if ( StringUtils.isEmpty( firstFileAtomic.get( ) ) ) {

						firstFileAtomic.getAndSet( filePath ) ;

					}

					// logFileNamesInSubDir.add(path);
					// use full path for passing to other UIs
					try {

						logFileNamesInSubDir.add( Application.FileToken.ROOT.value + subFile.getCanonicalPath( ) ) ;

					} catch ( IOException e ) {

						logger.error( "Reverting to absolute path", e ) ;
						logFileNamesInSubDir.add( Application.FileToken.ROOT.value + subFile.getAbsolutePath( ) ) ;

					}

				}

			}

		}

		return firstFileAtomic.get( ) ;

	}

	private String getShortNameFromCsapFilePath ( String logFileName ) {

		String shortName = logFileName ;

		for ( Application.FileToken token : Application.FileToken.values( ) ) {

			if ( logFileName.startsWith( token.value ) ) {

				shortName = logFileName.substring( token.value.length( ) ) ;
				break ;

			}

		}

		String endName = shortName ;

		// hook to shorten name
		if ( StringUtils.countMatches( shortName, "/" ) > 2 ) {

			endName = shortName.substring( 0, shortName.lastIndexOf( "/" ) ) ;
			endName = shortName.substring( endName.lastIndexOf( "/" ) + 1 ) ;

		}

		// windows
		if ( StringUtils.countMatches( shortName, "\\" ) > 2 ) {

			endName = shortName.substring( 0, shortName.lastIndexOf( "\\" ) ) ;
			endName = shortName.substring( endName.lastIndexOf( "\\" ) + 1 ) ;

		}

		if ( endName.startsWith( "logs" ) ) {

			endName = endName.substring( 5 ) ;

		}

		return endName ;

	}

	private ObjectMapper jacksonMapper = new ObjectMapper( ) ;

	@RequestMapping ( "/getFilesJson" )
	public void getFilesJson (
								@RequestParam ( value = "browseId" , required = true ) String browseId ,
								@RequestParam ( value = "fromFolder" , required = true ) String fromFolder ,
								@RequestParam ( value = "showDu" , required = false ) String showDu ,
								@RequestParam ( defaultValue = "false" ) boolean useRoot ,
								@RequestParam ( value = CsapCore.SERVICE_PORT_PARAM , required = false ) String serviceId ,

								String containerName ,
								HttpSession session ,
								HttpServletRequest request ,
								HttpServletResponse response )
		throws IOException {

		logger.debug( "fromFolder: {}, service: {}, showDu: {}, browseId: {}, useRoot: {}",
				fromFolder, serviceId, showDu, browseId, useRoot ) ;

		response.setHeader( "Cache-Control", "no-cache" ) ;
		response.setContentType( MediaType.APPLICATION_JSON_VALUE ) ;

		File targetFile = csapApp.getRequestedFile( fromFolder, serviceId, false ) ;

		auditTrail( targetFile, "listing" ) ;

		if ( StringUtils.isNotEmpty( browseId ) ) {
			// browse access requires explicit membership

			String browseFolder = getBrowseSettings( browseId ).get( "folder" ).asText( ) ;
			targetFile = new File( browseFolder,
					fromFolder.substring( browseId.length( ) ) ) ;

			if ( ! hasBrowseAccess( session, browseId )
					|| ( ! Application.isRunningOnDesktop( ) && ! targetFile.getCanonicalPath( ).startsWith(
							browseFolder ) ) ) {

				accessViolation( response, targetFile, browseFolder ) ;
				return ;

			}

		} else {

			// general access requires admin
			if ( ! securitySettings.getRoles( ).getAndStoreUserRoles( session )
					.contains( CsapSecurityRoles.ADMIN_ROLE ) ) {

				accessViolation( response, targetFile, "/" ) ;
				return ;

			}

		}

		Map<String, String> duLines = new HashMap<>( ) ;

		if ( showDu != null ) {

			duLines = runDiskUsage( targetFile ) ;

		}

		ArrayNode fileListing ;

		ServiceInstance service = null ;

		if ( StringUtils.isNotEmpty( serviceId ) ) {

			service = csapApp.flexFindFirstInstanceCurrentHost( serviceId ) ;

		}

		// Special handling for allowing specification of docker app and property
		// folders
		boolean useDockerBrowser = false ;

		var dockerContainerToken = "dockerContainer:/" ;

		if ( service != null
				&& dockerIntegration != null
				&& dockerIntegration.is_docker_folder( service ) ) {

			var prop_path = Application.FileToken.PROPERTY.value + "/" ;

			if ( fromFolder.equals( prop_path ) ) {

				var serviceProperyFolder = csapApp.resolveDefinitionVariables( service.getPropDirectory( ), service ) ;

				if ( serviceProperyFolder.startsWith( DOCKER_HOST_FILE_TOKEN ) ) {

					targetFile = new File( serviceProperyFolder,
							fromFolder.substring( FileToken.PROPERTY.value.length( ) ) ) ;

				} else {

					useDockerBrowser = true ;

				}

			} else if ( fromFolder.startsWith( dockerContainerToken ) ) {

				useDockerBrowser = true ;

			}

		}

		var propFolder = Application.FileToken.PROPERTY.value + "/" ;

		logger.info( CSAP.buildDescription(
				"File Browsing",
				"service", service,
				"useDockerBrowser", useDockerBrowser,
				"targetFile", targetFile,
				"targetFile.exists( )", targetFile.exists( ),
				"fromFolder", fromFolder,
				"dockerContainerToken", dockerContainerToken ) ) ;

		if ( fromFolder.startsWith( Application.FileToken.DOCKER.value ) ) {

			fileListing = buildListingUsingDocker(
					fromFolder.substring( Application.FileToken.DOCKER.value.length( ) ),
					duLines,
					fromFolder ) ;

		} else if ( useDockerBrowser ) {

			// handle file browser of docker services properties and app folder
			String pathToRequested = containerName ;

			if ( StringUtils.isEmpty( pathToRequested ) ) {

				var foundContainer = service.findContainerName( serviceId ) ;

				if ( StringUtils.isNotEmpty( foundContainer ) ) {

					pathToRequested = foundContainer ;

				}

				logger.info( "pathToRequested: {}", pathToRequested ) ;

				if ( StringUtils.isEmpty( pathToRequested ) ) {

					// handle kubernetes first service where id has been stripped
					pathToRequested = service.findContainerName( service.getName( ) + "-1" ) ;

				}

				// legacy
				if ( StringUtils.isEmpty( pathToRequested ) ) {

					pathToRequested = dockerIntegration.determineDockerContainerName( service ) ;

				}

				logger.info( "pathToRequested: {}", pathToRequested ) ;

			}
			// String pathToRequested = dockerIntegration.determineDockerContainerName(
			// service ) + "/";

			if ( fromFolder.startsWith( propFolder ) ) {

				pathToRequested += fromFolder.substring( propFolder.length( ) ) + service.getPropDirectory( ) + "/" ;

			} else if ( fromFolder.startsWith( dockerContainerToken ) ) {

//				pathToRequested += service.getAppDirectory( ) + "/" ;
				pathToRequested += fromFolder.substring( dockerContainerToken.length( ) - 1 ) ;

			}

			fileListing = buildListingUsingDocker(
					pathToRequested,
					duLines,
					Application.FileToken.DOCKER.value + pathToRequested ) ;

		} else if ( ! targetFile.exists( ) || ! targetFile.isDirectory( ) || useRoot ) {

			fileListing = buildListingUsingRoot( targetFile, duLines, fromFolder ) ;

		} else {

			// File[] filesInFolder = targetFile.listFiles();
			List<File> files = null ;

			try ( Stream<Path> pathStream = Files.list( targetFile.toPath( ) ) ) {

				files = pathStream
						.map( Path::toFile )
						.collect( Collectors.toList( ) ) ;

			} catch ( Exception e ) {

				logger.info( "Failed to get listing for {} reason\n {} ", targetFile, CSAP.buildCsapStack( e ) ) ;

			}

			if ( files == null
					|| files.size( ) == 0 ) {

				fileListing = buildListingUsingRoot( targetFile, duLines, fromFolder ) ;

			} else {

				fileListing = buildListingUsingJava( fromFolder, duLines, files ) ;

			}

		}
		// response.getWriter().println("</fromFolder>");

		// fileResponseJson folderJsonArray
		response.getWriter( ).println(
				jacksonMapper.writeValueAsString( fileListing ) ) ;

	}

	private void auditTrail ( File targetFile , String operation ) {

		var userName = CsapUser.currentUsersID( ) ;
		auditTrail( userName, targetFile, operation ) ;

	}

	private void auditTrail ( String userName , File targetFile , String operation ) {

		var auditName = CsapCoreService.FILE_URL + "/" + operation + "/" + targetFile.getAbsolutePath( ) ;

		if ( csapApp.getActiveUsers( ).addTrail( userName, auditName ) ) {

			csapEventClient.publishUserEvent(
					CsapEvents.CSAP_OS_CATEGORY + "/file/" + operation,
					userName,
					csapEventClient.fileName( targetFile, 60 ), targetFile.getAbsolutePath( ) ) ;

		}

	}

	private Map<String, String> runDiskUsage ( File targetFile )
		throws IOException {

		var collectScript = List.of(
				"#!/bin/bash",
				"timeout 60 du --summarize --block-size=1M --one-file-system "
						+ targetFile.getAbsolutePath( ) + "/*" ) ;

		var scriptOutput = osCommandRunner.runUsingRootUser( "ls-du", collectScript ) ;
		scriptOutput = csapApp.check_for_stub( scriptOutput, "linux/ls-du-using-root.txt" ) ;

		var scriptOutputLines = scriptOutput.split( "\n" ) ;

		logger.debug( "scriptOutputLines size: {}, collectScript: {} ",
				scriptOutputLines.length, collectScript ) ;

		var diskNameToSizeMap = Arrays.stream( scriptOutputLines )
				.filter( StringUtils::isNotEmpty )
				.map( CsapCore::singleSpace )
				.filter( line -> ! line.startsWith( "#" ) )
				.map( line -> line.split( " ", 2 ) )
				.filter( keyValueArray -> keyValueArray.length == 2 )
				.collect( Collectors.toMap( keyValueArray -> keyValueArray[1], keyValueArray -> keyValueArray[0] ) ) ;

		return diskNameToSizeMap ;

	}

	@Autowired ( required = false )
	DockerIntegration dockerIntegration ;

	private ArrayNode buildListingUsingDocker (
												String targetFolder ,
												Map<String, String> duLines ,
												String fromFolder ) {

		logger.info( "targetFolder: {} ", targetFolder ) ;

		ArrayNode fileListing ;

		if ( targetFolder.equals( "/" ) ) {

			ArrayNode containerListing = jacksonMapper.createArrayNode( ) ;
			dockerIntegration //

					.containerNames( true )
					// .containerNames( false )

					.forEach( fullName -> {

						ObjectNode itemJson = containerListing.addObject( ) ;
						String name = fullName.substring( 1 ) ; // strip off leading
																// slash added by ui
						itemJson.put( "folder", true ) ;
						itemJson.put( "lazy", true ) ;
						itemJson.put( "name", name ) ;
						itemJson.put( "location", fromFolder + name ) ;
						// itemJson.put("data", dataNode) ;
						itemJson.put( "title", name ) ;

					} ) ;

			fileListing = containerListing ;

		} else {
			// do docker ls & feed to OS listing

			String[] dockerContainerAndPath = splitDockerTarget( targetFolder ) ;
			String lsOutput = dockerIntegration.listFiles(
					dockerContainerAndPath[0],
					dockerContainerAndPath[1] ) ;

			fileListing = buildListingUsingOs( fromFolder, lsOutput, duLines ) ;

		}

		return fileListing ;

	}

	private String[] splitDockerTarget ( String targetFolder ) {

		int secondSlashIndex = targetFolder.substring( 1 ).indexOf( "/" ) ;

		if ( secondSlashIndex == -1 ) {

			return new String[] {
					targetFolder, ""
			} ;

		}

		String containerName = targetFolder.substring( 0, secondSlashIndex + 1 ) ;
		String path = targetFolder.substring( containerName.length( ) ) ;

		logger.debug( "containerName: {} , path: {} ", containerName, path ) ;
		return new String[] {
				containerName, path
		} ;

	}

	public static String pathWithSpacesEscaped ( File filePath ) {

		// return filePath.getAbsolutePath().replaceAll( REPLACE_SPACES, "\\\\ " ) ;
		return "'" + filePath.getAbsolutePath( ) + "'" ;

	}

	private ArrayNode buildListingUsingRoot ( File targetFolder , Map<String, String> duLines , String fromFolder )
		throws IOException {

		var path = pathWithSpacesEscaped( targetFolder ) ;
		var lsCommand = List.of(
				"#!/bin/bash",
				"ls -al " + path,
				"" ) ;

		logger.debug( "lsCommand: {}", lsCommand ) ;

		String lsOutput = osCommandRunner.runUsingRootUser( "file-ls", lsCommand ) ;
		lsOutput = csapApp.check_for_stub( lsOutput, "linux/ls-using-root.txt" ) ;

		ArrayNode fileListing = buildListingUsingOs( fromFolder, lsOutput, duLines ) ;

		return fileListing ;

	}

	final static String REPLACE_DEBIAN = Matcher.quoteReplacement( "1," ) ;

	private ArrayNode buildListingUsingOs (
											String fromFolder ,
											String lsOutput ,
											Map<String, String> duLines ) {

		logger.debug( CsapApplication.header( "ls: {} " ) + CsapApplication.header( "duLines: {} " ), lsOutput,
				duLines ) ;
		String[] lsOutputLines = lsOutput.split( "\n" ) ;
		ArrayNode fileListing = jacksonMapper.createArrayNode( ) ;

		for ( String line : lsOutputLines ) {

			String[] lsOutputWords = CsapCore.singleSpace( line
					.replaceAll( REPLACE_DEBIAN, "" ) )
					.split( " ", 9 ) ;

			logger.debug( "line: {} words: {} ", line, lsOutputWords.length ) ;

			if ( lsOutputWords.length == 9 && lsOutputWords[0].length( ) >= 10 ) {

				ObjectNode itemJson = fileListing.addObject( ) ;
				String currentItemName = lsOutputWords[8] ;
				itemJson.put( "name", currentItemName ) ;

				String fsize = lsOutputWords[4] + " b, " ;
				long fsizeNumeric = 0 ;

				try {

					Long fileSize = Long.parseLong( lsOutputWords[4] ) ;
					fsizeNumeric = fileSize.longValue( ) ;
					if ( fileSize > 1000 )
						fsize = fsizeNumeric / 1000 + "kb, " ;

				} catch ( NumberFormatException e ) {

					logger.info( "Unable to parse date from: '{}', reason: '{}' ",
							Arrays.asList( lsOutputWords ),
							CSAP.buildCsapStack( e ) ) ;

				}

				if ( lsOutputWords[0].contains( "d" ) ) {

					itemJson.put( "folder", true ) ;
					itemJson.put( "lazy", true ) ;

					var matchedDu = duLines.entrySet( ).stream( )
							.filter( entry -> entry.getKey( ).endsWith( "/" + currentItemName ) )
							.findFirst( ) ;

					if ( matchedDu.isPresent( ) ) {

						var reportSize = matchedDu.get( ).getValue( ) ;
						fsize = reportSize + "MB, " ;

						try {

							fsizeNumeric = Long.parseLong( reportSize ) ;
							fsizeNumeric = fsizeNumeric * 1000 * 1000 ;

							// gets to bytes
						} catch ( Exception e ) {

							logger.error( "Failed to parse to long" + fsize ) ;

						}

					}

				}

				itemJson.put( "restricted", true ) ;
				itemJson.put( "filter", false ) ;
				itemJson.put( "location",
						fromFolder + lsOutputWords[8] ) ;
				// itemJson.put("data", dataNode) ;
				itemJson.put( "title", lsOutputWords[8] ) ;
				itemJson.put(
						"meta",
						"~"
								+ fsize
								+ lsOutputWords[5] + " "
								+ lsOutputWords[6] + " "
								+ lsOutputWords[7] + ","
								+ lsOutputWords[0] + ","
								+ lsOutputWords[1] + ","
								+ lsOutputWords[2] + ","
								+ lsOutputWords[3] ) ;

				itemJson.put( "size", fsizeNumeric ) ;
				itemJson.put( "target", fromFolder + lsOutputWords[8]
						+ "/" ) ;

			}

		}

		if ( fileListing.size( ) == 0 ) {

			fileListing = jacksonMapper.createArrayNode( ) ;
			ObjectNode itemJson = fileListing.addObject( ) ;
			itemJson.put( "title", "Unable to get Listing" ) ;

		}

		return fileListing ;

	}

	private ArrayNode buildListingUsingJava (
												String fromFolder ,
												Map<String, String> duLines ,
												List<File> filesInFolder ) {

		ArrayNode fileListing = jacksonMapper.createArrayNode( ) ;

		for ( File itemInFolder : filesInFolder ) {

			String fsize = itemInFolder.length( ) + " b, " ;

			if ( itemInFolder.length( ) > 1000 ) {

				fsize = itemInFolder.length( ) / 1000 + "kb, " ;

			}

			Long fsizeNumeric = itemInFolder.length( ) ;

			ObjectNode itemJson = fileListing.addObject( ) ;
			// ObjectNode dataNode =jacksonMapper.createObjectNode() ;

			if ( itemInFolder.isDirectory( ) ) {

				itemJson.put( "folder", true ) ;
				itemJson.put( "lazy", true ) ;
				var matchedDu = duLines.entrySet( ).stream( )
						.filter( entry -> entry.getKey( ).endsWith( "/" + itemInFolder.getName( ) ) )
						.findFirst( ) ;

				if ( matchedDu.isPresent( ) ) {

					var reportSize = matchedDu.get( ).getValue( ) ;
					fsize = reportSize + "MB, " ;

					try {

						fsizeNumeric = Long.parseLong( reportSize ) ;
						fsizeNumeric = fsizeNumeric * 1000 * 1000 ;

						// gets to bytes
					} catch ( Exception e ) {

						logger.error( "Failed to parse to long" + fsize ) ;

					}

				}

			}

			boolean filtered = false ;

			if ( itemInFolder.getName( ).equals( ".ssh" ) ) {

				filtered = true ;

			}

			itemJson.put( "name", itemInFolder.getName( ) ) ;
			itemJson.put( "filter", filtered ) ;
			itemJson.put( "location",
					fromFolder + itemInFolder.getName( ) ) ;
			// itemJson.put("data", dataNode) ;
			itemJson.put( "title", itemInFolder.getName( ) ) ;
			itemJson.put(
					"meta",
					"~"
							+ fsize
							+ fileDateOutput.format( new Date( itemInFolder
									.lastModified( ) ) ) ) ;
			itemJson.put( "size", fsizeNumeric.longValue( ) ) ;
			itemJson.put( "target", fromFolder + itemInFolder.getName( )
					+ "/" ) ;

		}

		return fileListing ;

	}

	private void accessViolation ( HttpServletResponse response , File targetFile , String browseFolder )
		throws IOException ,
		JsonProcessingException {

		logger.debug( "Verify access: {} by {}", browseFolder, targetFile.getCanonicalPath( ) ) ;
		ArrayNode childArray = jacksonMapper.createArrayNode( ) ;
		ObjectNode itemJson = childArray.addObject( ) ;
		itemJson.put( "name", "permission denied" ) ;
		itemJson.put( "title", "permission denied" ) ;

		response.getWriter( ).println(
				jacksonMapper.writeValueAsString( childArray ) ) ;

	}

	private ThreadSafeSimpleDateFormat fileDateOutput = new ThreadSafeSimpleDateFormat(
			"MMM-d-yyyy H:mm:ss" ) ;

	public class ThreadSafeSimpleDateFormat {

		private DateFormat df ;

		public ThreadSafeSimpleDateFormat ( String format ) {

			this.df = new SimpleDateFormat( format ) ;

		}

		public synchronized String format ( Date date ) {

			return df.format( date ) ;

		}

		public synchronized Date parse ( String string )
			throws ParseException {

			return df.parse( string ) ;

		}
	}

	// private static final ThreadLocal<DateFormat> df = new
	// ThreadLocal<DateFormat>() {
	// @Override
	// protected DateFormat initialValue() {
	// return new SimpleDateFormat("yyyyMMM.d H:mm:ss");
	// }
	// };
	public static int BYTE_DOWNLOAD_CHUNK = 1024 * 10 ;

	@RequestMapping ( "/downloadFile/{fileName:.+}" )
	public void downloadFile (
								@PathVariable ( value = "fileName" ) String doNotUseThisAsItIsNotUrlEncoded ,
								@RequestParam ( value = "browseId" , required = false , defaultValue = "" ) String browseId ,
								@RequestParam ( defaultValue = "false" ) boolean forceText ,
								@RequestParam ( value = "fromFolder" , required = true ) String fromFolder ,
								@RequestParam ( value = "isBinary" , required = false , defaultValue = "false" ) boolean isBinary ,
								@RequestParam ( value = CsapCore.SERVICE_PORT_PARAM , required = false ) String svcName ,
								@RequestParam ( value = "isLogFile" , required = false , defaultValue = "false" ) boolean isLogFile ,
								HttpServletRequest request ,
								HttpServletResponse response ,
								HttpSession session )
		throws IOException {

		logger.info( "{} downloading service: {}, browseId: {} , fromFolder: {}, isBinary: {}",
				CsapUser.currentUsersID( ), svcName, browseId, fromFolder, isBinary ) ;

		if ( fromFolder.startsWith( Application.FileToken.JOURNAL.value ) ) {

			String journalService = fromFolder.substring( Application.FileToken.JOURNAL.value.length( ) + 1 ) ;
			String since = LocalDateTime.now( ).format( DateTimeFormatter.ofPattern( "yyyy-MM-dd" ) ) ;
			String target = "/os/journal?service="
					+ journalService
					+ "&since=" + since
					+ "&numberOfLines=500&reverse=false&json=false" ;

			response.sendRedirect( csapApp.getAgentUrl( csapApp.getCsapHostName( ), target ) ) ;
			return ;

		}

		File targetFile = csapApp.getRequestedFile( fromFolder, svcName, isLogFile ) ;

		// Restricted browse support
		if ( browseId != null && browseId.length( ) > 0 ) {
			// browse access requires explicit membership

			String browseFolder = getBrowseSettings( browseId ).get( "folder" ).asText( ) ;
			targetFile = new File( browseFolder,
					fromFolder.substring( browseId.length( ) ) ) ;

			if ( ! hasBrowseAccess( session, browseId ) || ! targetFile.getCanonicalPath( ).startsWith(
					browseFolder ) ) {

				if ( ! Application.isRunningOnDesktop( ) ) {

					accessViolation( response, targetFile, browseFolder ) ;
					return ;

				} else {

					logger.info( "Skipping access checks on desktop" ) ;

				}

			}

			if ( targetFile == null || ! targetFile.exists( ) ) {

				logger.warn( "Request file system does not exist: {}.  Check if {}  is bypassing security: ",
						targetFile.getCanonicalPath( ), CsapUser.currentUsersID( ) ) ;
				response.getWriter( ).println( "Invalid path " + fromFolder ) ;
				return ;

			}

		} else {

			if ( targetFile == null || ! targetFile.exists( ) ) {

				if ( ! securitySettings.getRoles( )
						.getAndStoreUserRoles( session )
						.contains( CsapSecurityRoles.INFRA_ROLE ) ) {

					logger.warn( "Requested file does not exist: {}.  Check if {}  is bypassing security: ",
							targetFile.getCanonicalPath( ), CsapUser.currentUsersID( ) ) ;
					response.getWriter( ).println( "Invalid path " + fromFolder ) ;
					return ;

				} else {

					logger.info(
							"Requested file not readable by csap: {}. Attempt to access with restricted permissions",
							targetFile.getAbsolutePath( ) ) ;

				}

			}

			// if it is not an admin - only allow viewing of files in processing
			// folder
			if ( ! securitySettings.getRoles( ).getAndStoreUserRoles( session )
					.contains( CsapSecurityRoles.ADMIN_ROLE ) ) {

				// run secondary check
				if ( ! targetFile.getCanonicalPath( ).startsWith(
						csapApp.getCsapWorkingFolder( ).getCanonicalPath( ) ) ) {

					logger.warn(
							"Attempt to access file system: {}. Only {} is permitted. Check if {}  is bypassing security: ",
							targetFile.getCanonicalPath( ), csapApp.getCsapWorkingFolder( ).getCanonicalPath( ),
							CsapUser.currentUsersID( ) ) ;
					response.getWriter( ).println( "*** Content protected: can be accessed by admins " + fromFolder ) ;
					return ;

				}

			}

			// Only allow infra admin to view security files.
			if ( ! securitySettings.getRoles( ).getAndStoreUserRoles( session )
					.contains( CsapSecurityRoles.INFRA_ROLE ) ) {

				// run secondary check
				if ( isInfraOnlyFile( targetFile ) ) {

					logger.warn( "Attempt to access security file: {}. Check if {}  is bypassing security.", fromFolder,
							CsapUser.currentUsersID( ) ) ;
					response.getWriter( ).println( "*** Content masked: can be accessed by infra admins "
							+ fromFolder ) ;
					return ;

				}

			}

		}

		String contentType = MediaType.TEXT_PLAIN_VALUE ;

		boolean isHtml = false ;

		if ( forceText ) {

			contentType = MediaType.TEXT_PLAIN_VALUE ;

		} else if ( targetFile.getName( ).endsWith( ".html" ) ) {

			contentType = "text/html" ;
			isHtml = true ;

		} else if ( targetFile.getName( ).endsWith( ".xml" ) || targetFile.getName( ).endsWith( ".jmx" ) ) {

			contentType = MediaType.APPLICATION_XML_VALUE ;

		} else if ( targetFile.getName( ).endsWith( ".json" ) ) {

			contentType = MediaType.APPLICATION_JSON_VALUE ;

		} else if ( targetFile.getName( ).endsWith( ".gif" ) ) {

			contentType = MediaType.IMAGE_GIF_VALUE ;

		} else if ( targetFile.getName( ).endsWith( ".png" ) ) {

			contentType = MediaType.IMAGE_PNG_VALUE ;

		} else if ( targetFile.getName( ).endsWith( ".jpg" ) ) {

			contentType = MediaType.IMAGE_JPEG_VALUE ;

		} else if ( targetFile.getName( ).endsWith( ".gz" ) || targetFile.getName( ).endsWith( ".zip" ) ) {

			isBinary = true ;

		}

		// User is downloading to their desktop
		if ( isBinary ) {

			contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE ;
			response.setContentLength( (int) targetFile.length( ) ) ;
			response.setHeader( "Content-disposition", "attachment; filename=\""
					+ targetFile.getName( ) + "\"" ) ;

		}

		response.setContentType( contentType ) ;
		response.setHeader( "Cache-Control", "no-cache" ) ;

		logger.debug( "file: {}", targetFile.getAbsolutePath( ) ) ;
		auditTrail( targetFile, "download" ) ;
		// csapEventClient.publishUserEvent( CsapEvents.CSAP_OS_CATEGORY +
		// "/file/download",
		// CsapUser.currentUsersID(),
		// csapEventClient.fileName( targetFile, 100 ),
		// targetFile.getAbsolutePath() ) ;

		if ( forceText && targetFile.length( ) > getMaxEditSize( ) ) {

			String contents = "Error: selected file has size " + targetFile.length( ) / 1024
					+ " kb; it exceeds the max allowed of: " + getMaxEditSize( ) / 1024
					+ "kb\n Use view or download to access on your desktop;  optionally CSAP upload can be used to update." ;

			logger.warn( "Failed to get file {}, reason: {}", targetFile.getAbsolutePath( ), contents ) ;

			response.getOutputStream( ).print( contents ) ;

		} else {

			if ( fromFolder.startsWith( Application.FileToken.DOCKER.value ) ) {

				String dockerTarget = fromFolder.substring( Application.FileToken.DOCKER.value.length( ) ) ;
				String[] dockerContainerAndPath = splitDockerTarget( dockerTarget ) ;

				long maxSizeForDocker = getMaxEditSize( ) ; // getMaxEditSize()
															// ;

				if ( ! forceText ) {

					maxSizeForDocker = 500 * maxSizeForDocker ; // still limit to
																// avoid heavy
																// reads

				}

				dockerIntegration.writeContainerFileToHttpResponse(
						isBinary,
						dockerContainerAndPath[0],
						dockerContainerAndPath[1],
						response,
						maxSizeForDocker,
						CHUNK_SIZE_PER_REQUEST ) ;

			} else {

				if ( ! targetFile.canRead( ) ) {

					addUserReadPermissions( targetFile ) ;

				}

				if ( csapApp.isAgentProfile( ) && isHtml && ! isBinary ) {

					csapApp.create_browseable_file_and_redirect_to_it( response, targetFile ) ;

				} else {

					writeFileToOutputStream( response, targetFile ) ;

				}

			}

		}

	}

	private void writeFileToOutputStream ( HttpServletResponse response , File targetFile )
		throws IOException {

		try ( DataInputStream in = new DataInputStream( new FileInputStream(
				targetFile.getAbsolutePath( ) ) );
				ServletOutputStream servletOutputStream = response.getOutputStream( ); ) {

			// if ( isHtml ) {
			// addHtmlBrowsingSupport( servletOutputStream, targetFile );
			// }

			byte[] bbuf = new byte[BYTE_DOWNLOAD_CHUNK] ;

			int numBytesRead ;
			long startingMax = targetFile.length( ) ;
			long totalBytesRead = 0L ; // hook for files that are being updated

			while ( ( in != null ) && ( ( numBytesRead = in.read( bbuf ) ) != -1 )
					&& ( startingMax > totalBytesRead ) ) {

				totalBytesRead += numBytesRead ;
				servletOutputStream.write( bbuf, 0, numBytesRead ) ;
				servletOutputStream.flush( ) ;

			}

		} catch ( Exception e ) {

			String reason = CSAP.buildCsapStack( e ) ;
			logger.warn(
					"Failed accessing file - suspected nfs - using root tail to load 3mb as a workaround. reason: {}",
					reason ) ;
			response.getWriter( ).println( CsapCore.CONFIG_PARSE_ERROR + "Failed accessing file: " + targetFile
					+ ". Attempting using root..." ) ;

			var tailScript = List.of(
					"#!/bin/bash",
					"tail -c 3m " + targetFile.getAbsolutePath( ) + " 2>&1" ) ;

			String tailOutput = "" ;

			try {

				logger.debug( "Running: {}", tailScript ) ;
				tailOutput = osCommandRunner.runUsingRootUser( "permissions", tailScript ) ;

			} catch ( Exception tailE ) {

				logger.warn( "Failed running: {}", CSAP.buildCsapStack( tailE ) ) ;

			}

			response.getWriter( ).println( tailOutput ) ;

		}

	}

	public final static String EDIT_URL = "/editFile" ;
	public final static String SAVE_URL = "/saveChanges" ;
	public final static String FILE_SYSTEM_URL = "/filesystem" ;

	private final boolean isDockerFolder ( String fromFolder ) {

		return fromFolder.startsWith( Application.FileToken.DOCKER.value ) ;

	}

	@PutMapping ( value = FILE_SYSTEM_URL , consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE )
	@ResponseBody
	public ObjectNode filesystem_rename (
											String fromFolder ,
											String newName ,
											boolean rename ,
											boolean root ,
											@RequestParam ( value = CsapCore.SERVICE_PORT_PARAM , required = false ) String svcNamePort )
		throws IOException {

		ObjectNode updateResults = jacksonMapper.createObjectNode( ) ;

		logger.info( "fromFolder: {}", fromFolder ) ;

		File workingItem = csapApp.getRequestedFile( fromFolder, svcNamePort, false ) ;
		File updatedItem = csapApp.getRequestedFile( newName, svcNamePort, false ) ;

		if ( workingItem.getAbsolutePath( ).startsWith( csapApp.getDefinitionFolder( ).getAbsolutePath( ) ) ) {

			csapApp.getRootProject( ).setEditUserid( CsapUser.currentUsersID( ) ) ;

		}

		var operation = "copy" ;

		if ( rename ) {

			operation = "rename" ;

		}

		csapEventClient.publishUserEvent( CsapEvents.CSAP_OS_CATEGORY + "/file/" + operation, CsapUser
				.currentUsersID( ),
				"Source: " + workingItem.getName( ) + " destination: " + updatedItem.getName( ),
				"Previous: " + workingItem.getAbsolutePath( ) + "\n New: " + updatedItem.getAbsolutePath( ) ) ;

		var message = operation + ": " + workingItem.getAbsolutePath( ) + " to: " + updatedItem.getAbsolutePath( ) ;

		try {

			if ( rename ) {

				if ( root ) {

					List<String> lines = List.of(
							"\\mv --verbose --force " + workingItem.getAbsolutePath( ) + " " + updatedItem
									.getAbsolutePath( ) ) ;

					String scriptOutput = osCommandRunner.runUsingRootUser( "file-move", lines ) ;

					message += "\n command: " + lines + "\n output:" + scriptOutput ;

				} else {

					if ( workingItem.renameTo( updatedItem ) ) {

						message += "  - SUCCESS" ;

					} else {

						message += "  - FAILED. Verify: csap user has r/w. Alternately - use command runner" ;

					}

				}

			} else {

				if ( root ) {

					List<String> lines = List.of(
							"\\cp --recursive --verbose --force " + workingItem.getAbsolutePath( ) + " " + updatedItem
									.getAbsolutePath( ) ) ;

					String scriptOutput = osCommandRunner.runUsingRootUser( "filesystem-copy", lines ) ;

					message += "\n command: " + lines + "\n output:" + scriptOutput ;

				} else {

					if ( workingItem.isDirectory( ) ) {

						FileUtils.copyDirectory( workingItem, updatedItem ) ;
						message += " Folder copy - SUCCESS" ;

					} else {

						Files.copy( workingItem.toPath( ), updatedItem.toPath( ) ) ;
						message += " File copy - SUCCESS" ;

					}

				}

			}

		} catch ( Exception e ) {

			var errMessage = CSAP.buildCsapStack( e ) ;
			logger.warn( "Failed renaming items: {}", errMessage ) ;

			message += " - FAILED " + "\n" + errMessage ;

		}

		updateResults.put( "plain-text", message ) ;

		return updateResults ;

	}

	@DeleteMapping ( value = FILE_SYSTEM_URL , consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE )
	@ResponseBody
	public ObjectNode filesystem_delete (
											String fromFolder ,
											boolean recursive ,
											boolean root ,
											@RequestParam ( value = CsapCore.SERVICE_PORT_PARAM , required = false ) String svcNamePort )
		throws IOException {

		ObjectNode updateResults = jacksonMapper.createObjectNode( ) ;

		logger.info( "fromFolder: {}, recursive:{} ", fromFolder, recursive ) ;

		File workingItem = csapApp.getRequestedFile( fromFolder, svcNamePort, false ) ;

		if ( workingItem.getAbsolutePath( ).startsWith( csapApp.getDefinitionFolder( ).getAbsolutePath( ) ) ) {

			csapApp.getRootProject( ).setEditUserid( CsapUser.currentUsersID( ) ) ;

		}

		var originalName = workingItem.getName( ) ;

		var message = "Deleting: " + workingItem.getAbsolutePath( ) ;

		try {

			csapEventClient.publishUserEvent( CsapEvents.CSAP_OS_CATEGORY + "/file/delete", CsapUser.currentUsersID( ),
					csapEventClient.fileName( workingItem, 60 ),
					workingItem.getAbsolutePath( ) ) ;

			if ( root ) {

				var recursiveOption = "" ;

				if ( recursive ) {

					recursiveOption = " --recursive " ;

				}

				List<String> lines = List.of(
						"\\rm --verbose --force " + recursiveOption + workingItem.getAbsolutePath( ) ) ;

				String scriptOutput = osCommandRunner.runUsingRootUser( "filesystem-remove", lines ) ;

				message = "command: " + lines + "\n output:" + scriptOutput ;

			} else {

				if ( recursive ) {

					FileUtils.forceDelete( workingItem ) ;
					message += " (recursive)" ;

				} else {

					if ( workingItem.delete( ) ) {

						message += "  - SUCCESS" ;

					} else {

						message += "  - FAILED. Verify: csap user has r/w, folders are empty(or recurse=true). Alternately - use delete++" ;

					}

				}

			}

		} catch ( Exception e ) {

			var errMessage = CSAP.buildCsapStack( e ) ;
			logger.warn( "Failed deleting items: {}", errMessage ) ;

			message += " - FAILED " + "\n" + errMessage ;

		}

		updateResults.put( "plain-text", message ) ;

		return updateResults ;

	}

	@PostMapping ( FILE_SYSTEM_URL )
	@ResponseBody
	public ObjectNode filesystem_create (
											String fromFolder ,
											String newFolder ,
											String newFile ,
											boolean root ,
											@RequestParam ( value = CsapCore.SERVICE_PORT_PARAM , required = false ) String svcNamePort )
		throws IOException {

		ObjectNode updateResults = jacksonMapper.createObjectNode( ) ;

		logger.info( "fromFolder: {}, newFolder: {}, newFile: {}, root:{} ", fromFolder, newFolder, newFile, root ) ;

		File workingFolder = csapApp.getRequestedFile( fromFolder, svcNamePort, false ) ;

		if ( workingFolder.getAbsolutePath( ).startsWith( csapApp.getDefinitionFolder( ).getAbsolutePath( ) ) ) {

			csapApp.getRootProject( ).setEditUserid( CsapUser.currentUsersID( ) ) ;

		}

		var originalName = workingFolder.getName( ) ;

		var message = "Creating: " ;

		csapEventClient.publishUserEvent( CsapEvents.CSAP_OS_CATEGORY + "/file/add", CsapUser.currentUsersID( ),
				"New Folder: " + newFolder + " file: " + newFile + " in " + workingFolder.getName( ),
				workingFolder.getAbsolutePath( ) ) ;

		try {

			if ( StringUtils.isNotEmpty( newFolder ) ) {

				workingFolder = new File( workingFolder, newFolder ) ;

				if ( root ) {

					List<String> lines = List.of(
							"mkdir --parents --verbose " + workingFolder.getAbsolutePath( ) ) ;

					String scriptOutput = osCommandRunner.runUsingRootUser( "filesystem-newfolder", lines ) ;
					message += "\n command: " + lines + "\n output:" + scriptOutput ;

				} else {

					message += "folder: " + newFolder + " " ;
					workingFolder.mkdirs( ) ;

				}

			}

			if ( StringUtils.isNotEmpty( newFile ) ) {

				File createdFile = new File( workingFolder, newFile ) ;

				if ( root ) {

					List<String> lines = List.of(
							"touch " + createdFile.getAbsolutePath( ) ) ;

					String scriptOutput = osCommandRunner.runUsingRootUser( "filesystem-new-file", lines ) ;
					message += "\n command: " + lines + "\n output:" + scriptOutput ;

				} else {

					message += "file: " + newFile + " " ;
					createdFile.createNewFile( ) ;

				}

			}

			message += "in: " + originalName ;

		} catch ( Exception e ) {

			var errMessage = CSAP.buildCsapStack( e ) ;
			logger.warn( "Failed creating items: {}", errMessage ) ;

			message = "Failed creating Items: Folders: " + newFolder + " files: " + newFile + "\n" + errMessage ;

		}

		updateResults.put( "plain-text", message ) ;

		return updateResults ;

	}

	@PostMapping ( "/update" )
	@ResponseBody
	public ObjectNode update (
								ModelMap modelMap ,
								boolean keepPermissions ,
//			@RequestParam ( value = "peter" , required = true ) String testParam ,
								@RequestParam ( value = "fromFolder" , required = false ) String fromFolder ,
								@RequestParam ( value = "chownUserid" , required = false ) String chownUserid ,
								@RequestParam ( value = CsapCore.SERVICE_PORT_PARAM , required = false ) String svcNamePort ,
								@RequestParam ( value = "contents" , required = false , defaultValue = "" ) String contents ,
								HttpServletRequest request ,
								HttpServletResponse response ,
								HttpSession session )
		throws IOException {

		if ( StringUtils.isEmpty( fromFolder ) ) {

			logger.warn( "Missing or empty file for fromFolder --" ) ;

			throw new ResponseStatusException( HttpStatus.BAD_REQUEST,
					"Missing or empty file specified. This can occur if file being updated is too large" ) ;

////			response.setStatus( HttpStatus.INTERNAL_SERVER_ERROR.value( ) ) ;
//			response.setStatus( HttpStatus.BAD_REQUEST.value( ) ) ;
////			response.getWriter( )
////					.print( HttpStatus.INTERNAL_SERVER_ERROR.value( )
////							+ " : Exception during processing, examine server Logs" ) ;
////			return null ;
//			var error = jacksonMapper.createObjectNode( ) ;
//			error.put( "message", "Missing or empty file specified. This can occur if file being updated is too large --" ) ;
		} else {

			var contentIgnoredOnSave = editFile( modelMap, keepPermissions, fromFolder, chownUserid, svcNamePort,
					contents, request, session ) ;

		}

		return (ObjectNode) modelMap.get( "result" ) ;

	}

	@RequestMapping ( {
			EDIT_URL, SAVE_URL
	} )
	public String editFile (
								ModelMap modelMap ,
								boolean keepPermissions ,
								@RequestParam ( value = "fromFolder" , required = false ) String fromFolder ,
								@RequestParam ( value = "chownUserid" , required = false ) String chownUserid ,
								@RequestParam ( value = CsapCore.SERVICE_PORT_PARAM , required = false ) String svcNamePort ,
								@RequestParam ( value = "contents" , required = false , defaultValue = "" ) String contents ,
								HttpServletRequest request ,
								HttpSession session )
		throws IOException {

		modelMap.addAttribute( "serviceName", svcNamePort ) ;
		modelMap.addAttribute( "fromFolder", fromFolder ) ;

		if ( StringUtils.isEmpty( svcNamePort ) // incontext editing
				|| ( svcNamePort != null
						&& svcNamePort.equals( "null" ) ) ) {

			svcNamePort = null ;

		}

		File targetFile = csapApp.getRequestedFile( fromFolder, svcNamePort, false ) ;

		setCommonAttributes( modelMap, request, null, null ) ;

		auditTrail( targetFile, "edit" ) ;
		logger.info( "fromFolder: {} \n\t targetFile: {} ", fromFolder, targetFile ) ;

		modelMap.addAttribute( "targetFile", targetFile ) ;

		if ( isDockerFolder( fromFolder ) ) {

			String dockerTarget = fromFolder.substring( Application.FileToken.DOCKER.value.length( ) ) ;
			String[] dockerContainerAndPath = splitDockerTarget( dockerTarget ) ;

			if ( StringUtils.isEmpty( contents ) ) {

				contents = dockerIntegration.writeContainerFileToString(
						modelMap,
						dockerContainerAndPath[0],
						dockerContainerAndPath[1],
						getMaxEditSize( ),
						CHUNK_SIZE_PER_REQUEST ).toString( ) ;

			} else {

				var dockerWriteResults = dockerIntegration.writeFileToContainer(
						contents,
						dockerContainerAndPath[0],
						dockerContainerAndPath[1],
						getMaxEditSize( ),
						CHUNK_SIZE_PER_REQUEST ) ;

				logger.info( "dockerWriteResults {}", dockerWriteResults ) ;

				modelMap.addAttribute( "result", dockerWriteResults ) ;

			}

		} else if ( ! keepPermissions && // support nfs mount updates via cat by root
				( targetFile == null || ! targetFile.exists( ) ) ) {

			logger.warn( "Request file system does not exist: {}.  Check if {}  is bypassing security: ",
					targetFile.getCanonicalPath( ), CsapUser.currentUsersID( ) ) ;
			contents = "Invalid path: " + fromFolder ;

		} else {

			logger.info( "targetFile: {}, length: {},  contents length: {}", targetFile, targetFile.length( ),
					contents.length( ) ) ;

			// Only allow infra admin to view security files.
			if ( ! securitySettings.getRoles( )
					.getAndStoreUserRoles( session )
					.contains( CsapSecurityRoles.INFRA_ROLE )
					&& ( isInfraOnlyFile( targetFile ) ) ) {

				logger.warn( "Attempt to access security file: {}. Check if {}  is bypassing security",
						fromFolder, CsapUser.currentUsersID( ) ) ;
				contents = "*** Content masked: can be accessed by infra admins: " + fromFolder ;

			} else if ( StringUtils.isEmpty( contents ) ) {

				if ( targetFile.length( ) > getMaxEditSize( ) ) {

					contents = "Error: selected file has size " + targetFile.length( ) / 1024
							+ " kb; it exceeds the max allowed of: " + getMaxEditSize( ) / 1024
							+ "kb\n CSAP download can be used to edit or view on desktop, then CSAP upload can be used." ;

				} else {

					if ( ! targetFile.canRead( ) ) {

						addUserReadPermissions( targetFile ) ;
						modelMap.addAttribute( "rootFile", "found" ) ;

					}

					contents = FileUtils.readFileToString( targetFile ) ;

					// } else {
					// File tempFile = createCsapUserReadableFile( targetFile );
					// contents = FileUtils.readFileToString( tempFile );
					// tempFile.delete();
					// modelMap.addAttribute( "rootFile", "found" );
					// }
				}

				csapEventClient.publishUserEvent( CsapEvents.CSAP_OS_CATEGORY + "/file/edit",
						CsapUser.currentUsersID( ),
						csapEventClient.fileName( targetFile, 100 ),
						targetFile.getAbsolutePath( ) ) ;

			} else {

				saveUpdatedFile( modelMap, chownUserid, svcNamePort, contents, targetFile, keepPermissions ) ;

				//
				// Avoid auto reloading - since context is a application being edited
				//
				logger.debug( "targetFile: {}, definition folder: {}", targetFile.getAbsolutePath( ), csapApp
						.getDefinitionFolder( ).getAbsolutePath( ) ) ;

				if ( targetFile.getAbsolutePath( ).startsWith( csapApp.getDefinitionFolder( ).getAbsolutePath( ) ) ) {

					csapApp.getRootProject( ).setEditUserid( CsapUser.currentUsersID( ) ) ;
					// subsequent scan will trigger restart.
					csapApp.resetTimeStampsToBypassReload( ) ;

				}

			}

		}

		modelMap.addAttribute( "contents", contents ) ;

		return CsapCoreService.FILE_URL + "/file-edit" ;

	}

	public static final String CSAP_SECURITYPROPERTIES = "csapSecurity.properties" ;
	public static final String CSAPTOKEN = "csap.token" ;

	private boolean isInfraOnlyFile ( File targetFile ) {

		String filePath = Application.filePathAllOs( targetFile ) ;

		if ( filePath.endsWith( CSAPTOKEN ) ) {

			return true ;

		}

		if ( filePath.endsWith( CSAP_SECURITYPROPERTIES ) ) {

			return true ;

		}

		if ( filePath.endsWith( ".yml" ) ) {

			String processing = Application.filePathAllOs( csapApp.getCsapWorkingFolder( ) ) ;

			if ( filePath.matches( processing + ".*admin.*application.*yml" ) ) {

				return true ;

			}

			if ( filePath.matches( processing + ".*CsAgent.*application.*yml" ) ) {

				return true ;

			}

		}

		if ( csapApp.isRunningOnDesktop( ) && filePath.endsWith( ".sh" ) ) {

			String processing = Application.filePathAllOs( csapApp.getCsapWorkingFolder( ) ) ;
			Pattern p = Pattern.compile( processing + ".*pTemp.*open.*sh" ) ;
			Matcher m = p.matcher( filePath ) ;
			logger.info( " Checking pattern {} for file in {}", p.toString( ), filePath ) ;

			if ( m.matches( ) ) {

				return true ;

			}

		}

		return false ;

	}

	private void saveUpdatedFile (
									ModelMap modelMap ,
									String chownUserid ,
									String serviceName_port ,
									String contents ,
									File targetFile ,
									boolean keepPermissions )
		throws IOException {

		// Updated file provided
		StringBuilder results = new StringBuilder( "Updating File: " ) ;
		results.append( targetFile.getAbsolutePath( ) ) ;

		results.append( "\n\n" ) ;

		auditTrail( targetFile, "save" ) ;

		// csapEventClient.publishUserEvent( CsapEvents.CSAP_OS_CATEGORY + "/edit/save",
		// CsapUser.currentUsersID(),
		// csapEventClient.fileName( targetFile, 100 ),
		// targetFile.getAbsolutePath() ) ;

		if ( serviceName_port != null ) {

			String desc = csapApp.getServiceInstanceCurrentHost( serviceName_port ).getName( ) + "/edit" ;

			csapEventClient.publishUserEvent( CsapEvents.CSAP_USER_SERVICE_CATEGORY + "/" + desc, CsapUser
					.currentUsersID( ),
					csapEventClient.fileName( targetFile, 100 ),
					targetFile.getAbsolutePath( ) ) ;

		} else if ( Application.isRunningOnDesktop( ) ) {

			File backupFile = new File( targetFile.getAbsolutePath( ) + "."
					+ CsapUser.currentUsersID( ) + "."
					+ System.currentTimeMillis( ) ) ;

			if ( ! backupFile.exists( ) ) {

				targetFile.renameTo( backupFile ) ;

			}

			try ( FileWriter fstream = new FileWriter( targetFile );
					BufferedWriter out = new BufferedWriter( fstream ); ) {
				// Create file

				out.write( contents ) ;

			}

		}

		File tempFolder = csapApp.csapPlatformTemp( ) ;

		if ( ! tempFolder.exists( ) ) {

			tempFolder.mkdirs( ) ;

		}

		File tempLocation = new File( tempFolder, "_" + targetFile.getName( ) ) ;

		try ( FileWriter fstream = new FileWriter( tempLocation );
				BufferedWriter out = new BufferedWriter( fstream ); ) {
			// Create file

			out.write( contents ) ;

		}

		File scriptPath = csapApp.csapPlatformPath( "/bin/csap-edit-file.sh" ) ;

		var backup_and_move_script = List.of(
				"#!/bin/bash",
				scriptPath.getAbsolutePath( )
						+ " " + pathWithSpacesEscaped( csapApp.getCsapInstallFolder( ) )
						+ " " + pathWithSpacesEscaped( tempLocation )
						+ " " + pathWithSpacesEscaped( targetFile )
						+ " " + chownUserid
						+ " " + CsapUser.currentUsersID( )
						+ " " + keepPermissions,
				"" ) ;

		results.append( osCommandRunner.runUsingRootUser( "backup-and-save-file", backup_and_move_script ) ) ;

		logger.debug( "result: {}", results ) ;

		ObjectNode resultObj = jacksonMapper.createObjectNode( ) ;
		resultObj.put( "plain-text", results.toString( ) ) ;
		modelMap.addAttribute( "result", resultObj ) ;

	}

	public final static String LAST_LINE_SESSION = "lastLineInSession" ;

	public final static String LOG_FILE_OFFSET_PARAM = "logFileOffset" ;
	public final static String LOG_SELECT_PARAM = "logSelect" ;
	public final static String PROP_SELECT_PARAM = "propSelect" ;

	final static String EOL = System.getProperty( "line.separator" ) ;

	final static int EOL_SIZE_BYTES = EOL.length( ) ;

	// final static int DEFAULT_TAIL = 1024 * 50;
	public final static int CHUNK_SIZE_PER_REQUEST = 1024 * 100 ; // 500k/time:
	// Some browsers will choke on large chunks.

	public final static int NUM_BYTES_TO_READ = 1024 ; // 5k/time
	public final static String PROGRESS_TOKEN = "*Progress:" ;
	public final static String OFFSET_TOKEN = "*Offset:" ;

	/**
	 *
	 * Key Use Cases: 1) Used to tail log files on FileMonitor UI 2) Used on MANY ui
	 * s to tail results of commands while they are running. This provides feedback
	 * to users
	 *
	 * @param fromFolder
	 * @param bufferSize
	 * @param hostNameArray
	 * @param serviceName_port
	 * @param isLogFile
	 * @param offsetLong
	 * @param request
	 * @param response
	 * @throws IOException
	 */
	@RequestMapping ( value = {
			FILE_CHANGES_URL
	} , produces = MediaType.APPLICATION_JSON_VALUE )
	@ResponseBody
	public JsonNode getFileChanges (
										@RequestParam ( value = "fromFolder" , required = false ) String fromFolder ,
										@RequestParam ( defaultValue = "0" ) int dockerLineCount ,
										@RequestParam ( defaultValue = "0" ) String dockerSince ,
										@RequestParam ( value = "bufferSize" , required = true ) long bufferSize ,
										@RequestParam ( value = CsapCore.HOST_PARAM , required = false ) String hostName ,
										@RequestParam ( value = CsapCore.SERVICE_PORT_PARAM , required = false ) String serviceName_port ,
										@RequestParam ( value = "isLogFile" , required = false , defaultValue = "false" ) boolean isLogFile ,
										@RequestParam ( value = LOG_FILE_OFFSET_PARAM , required = false , defaultValue = "-1" ) long offsetLong ,
										boolean useLocal ,
										String apiUser ,
										HttpServletRequest request ,
										HttpSession session )
		throws IOException {

		if ( hostName == null ) {

			hostName = csapApp.getCsapHostName( ) ;

		}

		var file_being_tailed = csapApp.getRequestedFile( fromFolder, serviceName_port, isLogFile ) ;
		var userName = CsapUser.currentUsersID( ) ;

		// debug only && ! csapApp.isDesktopHost()
		if ( csapApp.isAdminProfile( ) && ! useLocal ) {

			MultiValueMap<String, String> urlVariables = new LinkedMultiValueMap<String, String>( ) ;

			urlVariables.set( "apiUser", userName ) ;
			urlVariables.set( "fromFolder", fromFolder ) ;
			urlVariables.set( "dockerLineCount", Integer.toString( dockerLineCount ) ) ;
			urlVariables.set( "dockerSince", dockerSince ) ;
			urlVariables.set( "bufferSize", Long.toString( bufferSize ) ) ;
			urlVariables.set( CsapCore.HOST_PARAM, hostName ) ;
			urlVariables.set( "isLogFile", Boolean.toString( isLogFile ) ) ;
			urlVariables.set( "fromFolder", fromFolder ) ;
			urlVariables.set( CsapCore.SERVICE_NOPORT_PARAM, serviceName_port ) ;
			urlVariables.set( FileRequests.LOG_FILE_OFFSET_PARAM, Long.toString( offsetLong ) ) ;

			String url = CsapCoreService.API_AGENT_URL + AgentApi.LOG_CHANGES ;
			List<String> hosts = new ArrayList<>( ) ;
			hosts.add( hostName ) ;

			JsonNode changesFromRemoteAgentApi = csapApp.getOsManager( ).getServiceManager( )

					.remoteAgentsApi(
							csapApp.rootProjectEnvSettings( ).getAgentUser( ),
							csapApp.rootProjectEnvSettings( ).getAgentPass( ),
							hosts,
							url,
							urlVariables )

					.get( hostName ) ;

			if ( changesFromRemoteAgentApi.isTextual( )
					&& ( changesFromRemoteAgentApi.asText( ).startsWith( CsapCore.CONFIG_PARSE_ERROR )
							|| changesFromRemoteAgentApi.asText( ).startsWith( "Skipping host" ) ) ) {

				var errorReport = jacksonMapper.createObjectNode( ) ;

				errorReport.put( "error", "Failed to collect changes from remote agent" ) ;
				errorReport.put( "reason", changesFromRemoteAgentApi.asText( ) ) ;

				changesFromRemoteAgentApi = errorReport ;

			}

			return changesFromRemoteAgentApi ;

		}

		if ( StringUtils.isNotEmpty( apiUser ) ) {

			userName = apiUser ;

		}

		auditTrail( userName, file_being_tailed, "tail" ) ;

		if ( session == null ) {

			// access logged via client api

		} else {

			if ( ! securitySettings
					.getRoles( )
					.getAndStoreUserRoles( session )
					.contains( CsapSecurityRoles.ADMIN_ROLE ) ) {

				if ( ! file_being_tailed
						.getCanonicalPath( )
						.startsWith(
								csapApp.getCsapWorkingFolder( ).getCanonicalPath( ) ) ) {

					logger.warn(
							"Attempt to access file system: {}. Only {} is permitted. Check if {}  is bypassing security: ",
							file_being_tailed.getCanonicalPath( ),
							csapApp.getCsapWorkingFolder( ).getCanonicalPath( ),
							CsapUser.currentUsersID( ) ) ;

					ObjectNode errorResponse = jacksonMapper.createObjectNode( ) ;
					errorResponse
							.put( "error", "*** Content protected: can be accessed by admins " + fromFolder ) ;
					return errorResponse ;

				}

			}

			// Only allow infra admin to view security files.
			if ( ! securitySettings
					.getRoles( )
					.getAndStoreUserRoles( session )
					.contains( CsapSecurityRoles.INFRA_ROLE ) ) {

				// @formatter:on
				// run secondary check
				if ( isInfraOnlyFile( file_being_tailed ) ) {

					logger.warn( "Attempt to access security file: {}. Check if {}  is bypassing security.", fromFolder,
							CsapUser.currentUsersID( ) ) ;
					ObjectNode errorResponse = jacksonMapper.createObjectNode( ) ;
					errorResponse
							.put( "error", "*** Content masked: can be accessed by infra admins " + fromFolder ) ;
					return errorResponse ;

				}

			}

			// generate audit records as needed
			@SuppressWarnings ( "unchecked" )
			ArrayList<String> fileList = (ArrayList<String>) request.getSession( )
					.getAttribute( "FileAcess" ) ;

			if ( CsapCore.HOST_PARAM == null
					&& ( fileList == null || ! fileList.contains( file_being_tailed.getAbsolutePath( ) ) ) ) {

				if ( fileList == null ) {

					fileList = new ArrayList<String>( ) ;
					request.getSession( ).setAttribute( "FileAcess", fileList ) ;

				}

				fileList.add( file_being_tailed.getAbsolutePath( ) ) ;

				csapEventClient.publishUserEvent( CsapEvents.CSAP_OS_CATEGORY + "/file/tail",
						CsapUser.currentUsersID( ),
						csapEventClient.fileName( file_being_tailed, 100 ),
						file_being_tailed.getAbsolutePath( ) ) ;

			}

		}

		if ( fromFolder.startsWith( Application.FileToken.DOCKER.value ) ) {

			return tailUsingDocker( fromFolder, dockerLineCount, dockerSince ) ;

		} else if ( fromFolder.startsWith( Application.FileToken.JOURNAL.value ) ) {

			return tailUsingJournal( fromFolder, dockerLineCount, dockerSince ) ;

		} else {

			if ( file_being_tailed == null || ! file_being_tailed.isFile( ) || ! file_being_tailed.canRead( ) ) {

				addUserReadPermissions( file_being_tailed ) ;

			}

			return readFileChanges( bufferSize, offsetLong, file_being_tailed ) ;

		}

	}

	@Autowired
	OsManager osManager ;

	private ObjectNode tailUsingJournal ( String fromFolder , int numberOfLines , String dockerSince ) {

		ObjectNode fileChangesJson = jacksonMapper.createObjectNode( ) ;

		if ( numberOfLines == 0 ) {

			numberOfLines = 50 ;

		}

		fileChangesJson.put( "source", "journalctl" ) ;
		ArrayNode contentsJson = fileChangesJson.putArray( "contents" ) ;

		String journalService = fromFolder.substring( Application.FileToken.JOURNAL.value.length( ) + 1 ) ;

		try {

			String now = LocalDateTime.now( ).format( DateTimeFormatter.ofPattern( "yyyy-MM-dd HH:mm:ss" ) ) ;
			fileChangesJson.put( "since", now ) ;

			String since = "" ;

			if ( ! dockerSince.equals( "0" ) && ! dockerSince.equals( "-1" ) ) {

				since = dockerSince ;

			}

			String journalResults = osManager.getJournal(
					journalService, since,
					Integer.toString( numberOfLines ),
					false, false ) ;

			if ( ! journalResults.contains( "-- No entries --" ) ) {

				contentsJson.add( journalResults ) ;

				// String[] tailLines = journalResults.split( "\n" );
				// for ( String line : tailLines ) {
				// contentsJson.add( StringEscapeUtils.escapeHtml4( line ) + "\n" );
				// }
			}

		} catch ( Exception e ) {

			logger.error( "Failed tailing file: {}", CSAP.buildCsapStack( e ) ) ;

		}

		return fileChangesJson ;

	}

	private ObjectNode tailUsingDocker ( String fromFolder , int numberOfLines , String dockerSince ) {

		ObjectNode fileChangesJson = jacksonMapper.createObjectNode( ) ;

		if ( numberOfLines == 0 ) {

			numberOfLines = 50 ;

		}

		fileChangesJson.put( "source", "docker" ) ;
		ArrayNode contentsJson = fileChangesJson.putArray( "contents" ) ;

		String dockerTarget = fromFolder.substring( Application.FileToken.DOCKER.value.length( ) ) ;
		String[] dockerContainerAndPath = splitDockerTarget( dockerTarget ) ;

		try {

			if ( dockerContainerAndPath[1] == null || dockerContainerAndPath[1].trim( ).length( ) == 0 ) {

				// show container logs
				ObjectNode tailResult = dockerIntegration.containerTail(
						null,
						dockerContainerAndPath[0],
						numberOfLines,
						Integer.parseInt( dockerSince ) ) ;

				fileChangesJson.put( "since", tailResult.get( "since" ).asInt( ) ) ;

				String logsAsText = tailResult.get( "plainText" ).asText( ) ;

				if ( logsAsText.length( ) > 0 ) {

					contentsJson.add( logsAsText ) ;

					// for ( String line : logsAsText.split( "\n" ) ) {
					// contentsJson.add( StringEscapeUtils.escapeHtml4( line ) + "\n" );
					// }
				}

			} else {

				// tail on docker file
				String logsAsText = dockerIntegration
						.tailFile(
								dockerContainerAndPath[0],
								dockerContainerAndPath[1],
								numberOfLines ) ;

				fileChangesJson.put( "since", -1 ) ;

				if ( logsAsText.length( ) > 0 ) {

					contentsJson.add( logsAsText ) ;

					// for ( String line : logsAsText.split( "\n" ) ) {
					// contentsJson.add( StringEscapeUtils.escapeHtml4( line ) + "\n" );
					// }
				}

			}

		} catch ( Exception e ) {

			logger.error( "Failed tailing file: {}", CSAP.buildCsapStack( e ) ) ;

		}

		return fileChangesJson ;

	}

	public void addUserReadPermissions ( File targetFile ) {

		var readPermissionScriptResult = "" ;
		var setReadPermissionsScript = osManager.getOsCommands( ).getFileReadPermissions(
				csapApp.getAgentRunUser( ),
				targetFile.getAbsolutePath( ) ) ;

		try {

			readPermissionScriptResult = osCommandRunner.runUsingRootUser(
					"permissions",
					setReadPermissionsScript ) ;

		} catch ( IOException e ) {

			logger.warn( "Failed running: {}", CSAP.buildCsapStack( e ) ) ;

		}

		readPermissionScriptResult = csapApp.check_for_stub( readPermissionScriptResult, "linux/du-using-root.txt" ) ;

		StringBuilder results = new StringBuilder( "Updating read access using setfacl: " + targetFile
				.getAbsolutePath( ) ) ;

		results.append( "\nCommand: " + setReadPermissionsScript + "\n Result:" + readPermissionScriptResult ) ;

		//
		// After setting read permissions on the file, update parents until read access
		// is available
		//

		var parentFolder = targetFile.getParentFile( ) ;

		logger.debug( "parentFolderL {}, exists: {}, canExecute: {}, read: {}",
				parentFolder.getAbsolutePath( ), parentFolder.exists( ), parentFolder.canExecute( ), parentFolder
						.canRead( ) ) ;

		// this can be a very large number; limit to avoid recursive locks
		int maxDepth = 10 ;

		var parentDepth = parentFolder.getPath( ).split( "/" ).length ;

		if ( maxDepth < parentDepth ) {

			var message = "Bypassing facls as there are too many folders: " + parentDepth + " max allowed: "
					+ maxDepth ;
			results.append( message ) ;
			logger.warn( message ) ;

		} else {

			while ( ( maxDepth-- > 0 )
					&& ( parentFolder != null )
					&& ( ! parentFolder.canExecute( ) || ! parentFolder.canRead( ) ) ) {

				var parentLines = List.of(
						"#!/bin/bash",
						"setfacl -m u:" + csapApp.getAgentRunUser( ) + ":rx '" + parentFolder.getAbsolutePath( )
								+ "' 2>&1",
						"" ) ;

				String parentResult = "" ;

				try {

					parentResult = osCommandRunner.runUsingRootUser( "permissions", parentLines ) ;

				} catch ( Exception e ) {

					parentResult = CsapCore.CONFIG_PARSE_ERROR + "Failed running" ;

					logger.warn( "Failed running: {}", CSAP.buildCsapStack( e ) ) ;
					break ;

				}

				parentResult = csapApp.check_for_stub( parentResult, "linux/du-using-root.txt" ) ;

//				if ( StringUtils.isNotEmpty( parentResult ) ) {
//
//					logger.warn( "Non empty result when running: {}. Output: \n {}", parentLines, parentResult ) ;
//
//				}

				results.append( CSAP.buildDescription(
						"Adding read/execute permissions",
						"Command", parentLines,
						"Result", parentResult ) ) ;

				if ( parentResult.contains( "Operation not supported" )
						|| parentResult.contains( CsapCore.CONFIG_PARSE_ERROR ) ) {

					logger.warn( "Aborting facl request (suspected nfs folder)" ) ;
					break ;

				}

				parentFolder = parentFolder.getParentFile( ) ;

			}

		}

		csapEventClient.publishUserEvent( CsapEvents.CSAP_OS_CATEGORY + "/file/permissions",
				securitySettings.getRoles( ).getUserIdFromContext( ),
				"Adding read permissions: " + csapEventClient.fileName( targetFile, 50 ),
				results.toString( ) ) ;

		logger.debug( "Result: {}", results ) ;

		return ;

	}

	public ObjectNode readFileChanges ( long bufferSize , long offsetLong , File targetFile )
		throws IOException ,
		FileNotFoundException {

		ObjectNode fileChangesJson = jacksonMapper.createObjectNode( ) ;

		fileChangesJson.put( "source", "java" ) ;
		// getTail(targetFile, offsetLong, dirBuf, response);
		ArrayNode contentsJson = fileChangesJson.putArray( "contents" ) ;

		// || targetFile.getAbsolutePath().contains( "banner" )
		if ( targetFile == null || ! targetFile.isFile( ) || ! targetFile.canRead( ) ) {

			// UI is handling...
			logger.debug( "File not accessible: " + targetFile.getAbsolutePath( ) ) ;
			fileChangesJson
					.put( "error",
							"Warning: File does not exist or permission to read is denied. Try the root tail option or select another file\n" ) ;

			return fileChangesJson ;

		}

		// try with resource
		try ( RandomAccessFile randomAccessFile = new RandomAccessFile(
				targetFile, "r" ); ) {

			long fileLengthInBytes = randomAccessFile.length( ) ;

			Long lastPosition = offsetLong ;

			if ( lastPosition.longValue( ) == -1 ) { // -1 means default tail

				// size,
				// -2
				// is show whole file

				if ( fileLengthInBytes < bufferSize ) {

					lastPosition = new Long( 0 ) ; // start from the start of the
					// file

				} else {

					lastPosition = new Long( fileLengthInBytes - bufferSize ) ;

				}

			} else if ( lastPosition.longValue( ) == -2 ) { // show whole file

				// without
				// chunking
				lastPosition = new Long( 0 ) ;

			}

			// file may have been rolled by agent - emptying it , compressing
			// contents.
			if ( lastPosition.longValue( ) > fileLengthInBytes ) {

				contentsJson.add( "FILE ROLLED\n" ) ;

				if ( fileLengthInBytes < bufferSize ) {

					lastPosition = new Long( 0 ) ; // start from the start of the
					// file

				} else {

					lastPosition = new Long( fileLengthInBytes - bufferSize ) ;

				}

			}

			// Progress info is displayed on 1st line
			if ( offsetLong != -2 ) {

				fileChangesJson.put( "lastPosition", lastPosition.longValue( ) ) ;

			}

			if ( offsetLong != -2 ) {

				fileChangesJson.put( "fileLength", randomAccessFile.length( ) ) ;

			}

			// printWriter.print(PROGRESS_TOKEN + " " + lastPosition.longValue()
			// / 1024 + " of " + randomAccessFile.length() / 1024 + " Kb\n");
			// log.info("fileName: " + fileName + " Raf length of file:" +
			// currLength + " Offset:" + lastPosition) ;
			long currPosition = lastPosition.longValue( ) ;
			byte[] bufferAsByteArray = new byte[NUM_BYTES_TO_READ] ;
			int numBytes = NUM_BYTES_TO_READ ;

			// as the files roll
			String stringReadIn = null ;
			randomAccessFile.seek( currPosition ) ; // this goes to the byte
			// before
			int numBytesSent = 0 ;

			while ( currPosition < fileLengthInBytes ) {

				if ( ( fileLengthInBytes - currPosition ) < NUM_BYTES_TO_READ ) {

					long numBytesLong = fileLengthInBytes - currPosition ;
					numBytes = ( new Long( numBytesLong ) ).intValue( ) ;

				}

				randomAccessFile.read( bufferAsByteArray, 0, numBytes ) ;

				stringReadIn = new String( bufferAsByteArray, 0, numBytes ) ;
				// System.out.print(" ---- read in" + stringReadIn +
				// " at offset: "
				// + currPosition) ;

				currPosition += numBytes ;
				// we stream in the data, to keep server side as lean as
				// possible
				// StringEscapeUtils.escapeHtml4(
				contentsJson.add( stringReadIn ) ;
				// sbuf.append(stringReadIn);

				numBytesSent += numBytes ; // send on

				if ( numBytesSent > CHUNK_SIZE_PER_REQUEST - 1 ) {

					break ; // only send back limited at a time for

				} // responsiveness

			}

			long newOffset = lastPosition.longValue( ) + numBytesSent ;
			// printWriter.print("\n" + OFFSET_TOKEN + " " + newOffset +
			// " Total: "
			// + currLength);

			long numChunks = ( fileLengthInBytes / CHUNK_SIZE_PER_REQUEST ) + 1 ;

			fileChangesJson.put( "numChunks", numChunks ) ;
			fileChangesJson.put( "newOffset", newOffset ) ;
			fileChangesJson.put( "currLength", fileLengthInBytes ) ;

		}

		return fileChangesJson ;

	}

	public static long getMaxEditSize ( ) {

		// allow additional for other parameters
		return CsapWebServerConfig.getMaxPostInBytes( ) - 500 ;

	}

}
