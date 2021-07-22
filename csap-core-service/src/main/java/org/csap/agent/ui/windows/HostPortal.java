package org.csap.agent.ui.windows ;

import java.io.File ;
import java.io.IOException ;
import java.nio.file.Files ;
import java.time.LocalDateTime ;
import java.time.format.DateTimeFormatter ;
import java.util.ArrayList ;
import java.util.Arrays ;
import java.util.Collections ;
import java.util.List ;
import java.util.Optional ;
import java.util.stream.Stream ;

import javax.inject.Inject ;
import javax.servlet.http.HttpServletRequest ;
import javax.servlet.http.HttpSession ;

import org.apache.commons.io.FileUtils ;
import org.apache.commons.lang3.StringEscapeUtils ;
import org.apache.commons.lang3.StringUtils ;
import org.csap.agent.CsapCore ;
import org.csap.agent.CsapCoreService ;
import org.csap.agent.CsapTemplate ;
import org.csap.agent.container.DockerIntegration ;
import org.csap.agent.container.kubernetes.KubernetesJson ;
import org.csap.agent.integrations.CsapEvents ;
import org.csap.agent.model.Application ;
import org.csap.agent.model.Application.FileToken ;
import org.csap.agent.model.ServiceInstance ;
import org.csap.agent.services.OsManager ;
import org.csap.agent.ui.explorer.OsExplorer ;
import org.csap.agent.ui.rest.FileRequests ;
import org.csap.agent.ui.rest.ServiceRequests ;
import org.csap.docs.CsapDoc ;
import org.csap.helpers.CSAP ;
import org.csap.helpers.CsapApplication ;
import org.csap.integations.CsapInformation ;
import org.csap.security.CsapUser ;
import org.csap.security.config.CsapSecurityRoles ;
import org.csap.security.config.CsapSecuritySettings ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.beans.factory.annotation.Autowired ;
import org.springframework.stereotype.Controller ;
import org.springframework.ui.ModelMap ;
import org.springframework.web.bind.annotation.GetMapping ;
import org.springframework.web.bind.annotation.PathVariable ;
import org.springframework.web.bind.annotation.RequestMapping ;
import org.springframework.web.bind.annotation.RequestParam ;
import org.springframework.web.bind.annotation.ResponseBody ;
import org.springframework.web.servlet.ModelAndView ;
import org.springframework.web.servlet.mvc.support.RedirectAttributes ;

import com.fasterxml.jackson.databind.ObjectMapper ;
import com.fasterxml.jackson.databind.node.ArrayNode ;
import com.fasterxml.jackson.databind.node.ObjectNode ;

@Controller
@RequestMapping ( CsapCoreService.OS_URL )
@CsapDoc ( title = "Host and Perfomance Portals; including script execution" , notes = {
		"Update, Reload and similar operations to manage the running application",
		"<a class='csap-link' target='_blank' href='https://github.com/csap-platform/csap-core/wiki'>learn more</a>",
		"<img class='csapDocImage' src='CSAP_BASE/images/csapboot.png' />"
				+ "<img class='csapDocImage' src='CSAP_BASE/images/portals.png' />"
} )
public class HostPortal {

	final Logger logger = LoggerFactory.getLogger( CorePortals.class ) ;

	private static final String _METRICS = "_METRICS_" ;

	@Inject
	public HostPortal ( Application csapApp, OsManager osManager, CsapEvents csapEventClient ) {

		this.csapApp = csapApp ;
		this.osManager = osManager ;
		this.csapEventClient = csapEventClient ;

	}

	Application csapApp ;
	OsManager osManager ;

	@Autowired ( required = false )
	CsapSecuritySettings securitySettings ;

	CsapEvents csapEventClient ;
	// Circular dependencies arise with constructor injection - b
	@Inject
	ServiceRequests serviceController ;

	ObjectMapper jacksonMapper = new ObjectMapper( ) ;

	final static String COMMAND_URL = "command" ;
	public final static String COMMAND_SCREEN_URL = CsapCoreService.OS_URL + "/" + COMMAND_URL ;

	@RequestMapping ( COMMAND_URL )
	public ModelAndView osCommandExecutor (
											ModelMap modelMap ,
											HttpSession session ,
											@RequestParam ( value = "fromFolder" , required = false ) String fromFolder ,
											@RequestParam ( value = "location" , required = false ) String location ,
											@RequestParam ( value = "extractDir" , required = false ) String extractDir ,
											@RequestParam ( value = "searchText" , required = false , defaultValue = "" ) String searchText ,
											@RequestParam ( value = "pid" , required = false , defaultValue = "" ) String pid ,
											@RequestParam ( value = "template" , required = false , defaultValue = "" ) String template ,
											@RequestParam ( value = "serviceName" , required = false , defaultValue = "" ) String serviceName_port ,
											@RequestParam ( value = "contents" , required = false , defaultValue = "" ) String contents ,
											@RequestParam ( value = "command" , required = false , defaultValue = "script" ) String command )
		throws IOException {

		if ( fromFolder == null ) {

			fromFolder = Application.FileToken.HOME.value ;

		}

		// logger.info("Gpt here");
		setCommonAttributes( modelMap, session, "OS Commands" ) ;

		if ( StringUtils.isEmpty( serviceName_port ) ) {

			// needed only to resolve ser
			// serviceName_port = Application.AGENT_ID ;
		}

		File requestedFileOrFolder = csapApp.getRequestedFile( fromFolder, serviceName_port, false ) ;

		// modelMap.addAttribute("location", targetFile.getCanonicalPath());
		modelMap.addAttribute( "location", requestedFileOrFolder.toURI( ).getPath( ) ) ;
		modelMap.addAttribute( "sameLocation", CsapCore.SAME_LOCATION ) ;

		if ( command.equals( "logSearch" )
				&& fromFolder.startsWith( Application.FileToken.JOURNAL.value ) ) {

			command = "script" ;
			template = "linux-journalctl.sh" ;
			serviceName_port = fromFolder.substring( Application.FileToken.JOURNAL.value.length( ) + 1 ) ;

		}

		modelMap.addAttribute( "command", command ) ;
		String title = "" ;

		switch ( command ) {

		case "logSearch":
			title = csapApp.getCsapHostName( ) + " Search" ;
			break ;

		default:
			title = csapApp.getCsapHostName( ) + " " + command ;
			break ;

		}

		modelMap.addAttribute( "title", title ) ;
		modelMap.addAttribute( "scriptLabel", "loading" ) ;
		modelMap.addAttribute( "csapUser", csapApp.getAgentRunUser( ) ) ;

		// modelMap.addAttribute(
		// "clusterHostsMap",csapApp.buildClusterReportForAllPackagesInActiveLifecycle()
		// ) ;
		modelMap.addAttribute( "clusterHostsMap", csapApp.buildClusterByPackageInActiveLifecycleReport( ) ) ;

		modelMap.addAttribute( "allHosts",
				jacksonMapper.convertValue( csapApp.getAllHostsInAllPackagesInCurrentLifecycle( ), ArrayNode.class ) ) ;

		if ( ! serviceName_port.equals( "null" ) && serviceName_port.length( ) > 0 ) {

			try {

				ServiceInstance serviceOnHost = csapApp.getServiceInstanceCurrentHost( serviceName_port ) ;

				if ( serviceOnHost != null ) {

					logger.info( "serviceOnHost: {}", serviceName_port ) ;
					modelMap.addAttribute( "serviceHosts",
							jacksonMapper.convertValue(
									csapApp.getActiveProject( ).findHostsForService( serviceOnHost.getName( ) ),
									ArrayNode.class ) ) ;

				} else {

					logger.info( "Did not locate: {}", serviceName_port ) ;

				}

			} catch ( Exception e ) {

				logger.warn( "Failed to find service hosts {}", CSAP.buildCsapStack( e ) ) ;

			}

		}

		File scriptFolder = new File( csapApp.getDefinitionFolder( ), "scripts" ) ;

		String[] scriptNames = scriptFolder.list(
				( File dir , String name ) -> name.toLowerCase( ).endsWith( ".sh" )
						|| name.toLowerCase( ).endsWith( ".ksh" )
						|| name.toLowerCase( ).endsWith( ".py" ) ) ;

		if ( scriptNames == null ) {

			scriptNames = new String[0] ;

		}

		modelMap.addAttribute( "projectScripts", scriptNames ) ;

		modelMap.addAttribute( "scriptLabel", ".sh" ) ;

		if ( command.equals( "script" )
				&& ( requestedFileOrFolder.getName( ).endsWith( ".sh" )
						|| requestedFileOrFolder.getName( ).endsWith( ".ksh" )
						|| requestedFileOrFolder.getName( ).endsWith( ".py" ) ) ) {

			logger.info( "loading: {}", requestedFileOrFolder.getAbsolutePath( ) ) ;
			contents = FileUtils.readFileToString( requestedFileOrFolder ) ;
			modelMap.addAttribute( "scriptLabel", requestedFileOrFolder.getName( ) ) ;

		}

		modelMap.addAttribute( "contents", contents ) ;

		List<File> searchFiles = new ArrayList<>( ) ;
		String searchFolder = fromFolder ;

		if ( command.equals( "logSearch" ) ) {
			// file listing for ui selection

			File searchDir = csapApp.getLogDir( serviceName_port ) ;

			if ( fromFolder.startsWith( FileToken.DOCKER.value ) ) {

				var containerPath = fromFolder.substring( FileToken.DOCKER.value.length( ) ) ;

				if ( StringUtils.isNotEmpty( serviceName_port ) ) {

					ServiceInstance serviceOnHost = csapApp.getServiceInstanceCurrentHost( serviceName_port ) ;
					containerPath = serviceOnHost.getDefaultContainer( ).getContainerName( ) ;

				}

				File containerLog = dockerIntegration.containerLogPath( containerPath ) ;
				logger.info( "containerPath: {}, containerLog: {}", containerPath, containerLog.getAbsolutePath( ) ) ;
				searchFiles.add( containerLog.getParentFile( ) ) ;
				// searchDir = containerLog.getParentFile() ;
				searchFolder = FileToken.ROOT.value + containerLog.getAbsolutePath( ) ;

				if ( ! containerLog.getParentFile( ).canRead( ) ) {

					fileRequests.addUserReadPermissions( containerLog.getParentFile( ) ) ;

				}

				File[] files = containerLog.getParentFile( ).listFiles( ) ;

				if ( files != null ) {

					searchFiles.addAll( Arrays.asList( files ) ) ;

				}

				logger.info( "Updated searchFolder: {}", searchFolder ) ;

			} else {

				File[] files = requestedFileOrFolder.listFiles( ) ;

				if ( files == null ) {

					files = requestedFileOrFolder.getParentFile( ).listFiles( ) ;

				}

				if ( searchDir != null && searchDir.isDirectory( ) ) {

					files = searchDir.listFiles( ) ;

				}

				if ( files != null ) {

					searchFiles.addAll( Arrays.asList( files ) ) ;

				} else {

					logger.info( "No files found: {}", requestedFileOrFolder ) ;

				}

			}

			Collections.sort( searchFiles ) ;

			if ( searchFolder != null && searchFolder.equals( "defaultLog" ) ) {

				searchFolder = csapApp.getDefaultLogFile( serviceName_port ) ;

				// Arrays.sort(searchFiles, comparingLong(File::lastModified));
			}

		}

		modelMap.addAttribute( "searchFolder", searchFolder ) ;
		modelMap.addAttribute( "searchFiles", searchFiles ) ;

		if ( searchText != null ) {

			String searchTextJava = searchText.replaceAll( "\r\n.*", " " ) ;
			searchTextJava = searchTextJava.replaceAll( "\n.*", " " ) ;
			searchText = searchTextJava ;

		}

		modelMap.addAttribute( "searchText", searchText ) ;

		modelMap.addAttribute( "pid", pid ) ;

		modelMap.addAttribute( "serviceName", serviceName_port ) ;

		if ( StringUtils.isEmpty( template ) ) {

			template = "linux-uptime.sh" ;

		}

		modelMap.addAttribute( "template", template ) ;

		// logger.info( " location: {}", location );

		String defaultLocation = requestedFileOrFolder.getAbsolutePath( ) ;

		if ( location != null ) {

			defaultLocation = location ;

			if ( Application.isRunningOnDesktop( ) ) {

				defaultLocation.replaceAll( StringEscapeUtils.escapeJava( "\\" ),
						StringEscapeUtils.escapeJava( "\\\\" ) ) ;

			}

		}

		if ( extractDir != null ) {

			defaultLocation = extractDir ;

		}

		modelMap.addAttribute( "defaultLocation", defaultLocation ) ;

		modelMap.addAttribute( "osUsers", csapApp.buildOsUsersList( ) ) ;

		modelMap.addAttribute( "clusters", csapApp.getClustersForActivePackage( ).keySet( ) ) ;

		modelMap.addAttribute( "templateArray", buildTemplates( ) ) ;

		return new ModelAndView( CsapCoreService.OS_URL + "/command-body" ) ;

	}

	@Autowired ( required = false )
	DockerIntegration dockerIntegration ;

	@Autowired ( required = false )
	FileRequests fileRequests ;

	@GetMapping ( "command/template/{name}" )
	@ResponseBody
	public String commandTemplate ( @PathVariable String name )
		throws IOException {

		File varFile = new File( name ) ;
		File templateFile = new File( CsapTemplate.shell_scripts.getFile( ), varFile.getName( ) ) ;

		if ( ! templateFile.exists( ) ) {

			templateFile = new File( csapApp.getDefinitionFolder( ), "scripts/" + name ) ;

		}

		if ( ! templateFile.exists( ) ) {

			templateFile = new File( CsapTemplate.kubernetes_yaml.getFile( ), varFile.getName( ) ) ;

		}

		logger.info( "reading: {}", templateFile.getCanonicalPath( ) ) ;

		if ( templateFile.exists( ) ) {

			return FileUtils.readFileToString( templateFile ) ;

		} else {

			return "Requested template not found: " + name ;

		}

	}

	private ArrayNode buildTemplates ( ) {

		ArrayNode templates = jacksonMapper.createArrayNode( ) ;

		File scriptsFolder = CsapTemplate.shell_scripts.getFile( ) ;

		if ( scriptsFolder.exists( ) && scriptsFolder.isDirectory( ) ) {

			List<File> templateFiles = Arrays.asList( scriptsFolder.listFiles( ) ) ;

			Collections.sort( templateFiles ) ;

			templateFiles.stream( )
					.filter( file -> {

						return file.length( ) < 10000 ;

					} )
					.forEach( scriptFile -> {

						try ( Stream<String> lines = Files.lines( scriptFile.toPath( ) ) ) {

							logger.debug( "Reading: {}", scriptFile.getAbsolutePath( ) ) ;
							ObjectNode template = templates.addObject( ) ;

							template.put( "source", scriptFile.getName( ) ) ;
							template.put( "command", scriptFile.getName( ) ) ;
							template.put( "description", scriptFile.getName( ) ) ;

							// List<String> lines = Files.readAllLines(
							// scriptFile.toPath() );
							Optional<String> firstLine = lines.limit( 1 ).findFirst( ) ;

							if ( firstLine.isPresent( ) ) {

								String[] desc = firstLine.get( ).substring( 1 ).split( ",", 2 ) ;

								if ( desc.length == 2 ) {

									template.put( "command", desc[0].trim( ) ) ;
									template.put( "description", desc[1].trim( ) ) ;

								}

								// template.set( "lines", jacksonMapper.convertValue( lines, ArrayNode.class )
								// );
							}

						} catch ( Exception e ) {

							logger.warn( "Failed to get first line from: {} {}",
									scriptFile,
									CSAP.buildCsapStack( e ) ) ;

						}

					} ) ;

		}

		return templates ;

	}

	@GetMapping ( CsapApplication.COLLECTION_HOST )
	public ModelAndView vmScreen ( ModelMap modelMap , HttpSession session )
		throws IOException {

		modelMap.addAttribute( "name", "Host Shared Resource Graphs" ) ;

		setCommonAttributes( modelMap, session, "Host Graphs" ) ;

		return new ModelAndView( "/graphs/host" ) ;

	}

	@GetMapping ( CsapApplication.COLLECTION_OS_PROCESS )
	public ModelAndView processScreen ( ModelMap modelMap , HttpSession session )
		throws IOException {

		modelMap.addAttribute( "name", "OS Process Graphs" ) ;
		setCommonAttributes( modelMap, session, "OS Process Graphs" ) ;

		return new ModelAndView( "/graphs/process" ) ;

	}

	@GetMapping ( "java" )
	public ModelAndView javaScreen (
										@RequestParam ( value = "service" , required = false , defaultValue = "none" ) ArrayList<String> services ,
										ModelMap modelMap ,
										HttpSession session )
		throws IOException {

		modelMap.addAttribute( "name", "Java Graphs" ) ;

		var serviceName = "" ;

		if ( ! services.get( 0 ).equals( "none" ) ) {

			serviceName = csapApp.flexFindFirstInstanceCurrentHost( services.get( 0 ) ).getName( ) ;

		}

		modelMap.addAttribute( "pageTitle", "Java: " + serviceName ) ;
		modelMap.addAttribute( "csapPageLabel", "Java Performance: " + serviceName ) ;

		setCommonAttributes( modelMap, session, "Java Graphs" ) ;

		return new ModelAndView( "/graphs/java" ) ;

	}

	@GetMapping ( CsapApplication.COLLECTION_APPLICATION )
	public ModelAndView application (
										@RequestParam ( value = "service" ) String serviceName ,
										ModelMap modelMap ,
										HttpServletRequest request ,
										HttpSession session )
		throws IOException {

		modelMap.addAttribute( "name", "Application Graphs" ) ;

		// string off port if it is specified
		if ( serviceName.indexOf( "_" ) != -1 ) {

			serviceName = serviceName.substring( 0, serviceName.indexOf( "_" ) ) ;

		}

		modelMap.addAttribute( "csapPageLabel", "Application Performance for service: " + serviceName ) ;
		modelMap.addAttribute( "serviceName", serviceName ) ;

		corePortals.addTestHost( request, session ) ;
		setCommonAttributes( modelMap, session, "Application Graphs" ) ;

		return new ModelAndView( "/graphs/application" ) ;

	}

	@GetMapping ( "HostDashboard-eol" )
	public ModelAndView hostDashboard (
										@RequestParam ( value = "emptyCache" , required = false ) String emptyCache ,
										@RequestParam ( value = "clearStats" , required = false ) String clearStats ,
										@RequestParam ( value = CsapCore.SERVICE_NOPORT_PARAM , required = false ) String serviceParam ,
										ModelMap modelMap ,
										HttpSession session ) {

		setCommonAttributes( modelMap, session, "Host Dashboard" ) ;

		if ( emptyCache != null ) {

			osManager.resetAllCaches( ) ;

		}

		if ( clearStats != null ) {

			csapApp.metricManager( ).clearResourceStats( ) ;

		}

		modelMap.addAttribute( "explorerUrl", OsExplorer.EXPLORER_URL ) ;

		modelMap.addAttribute( "activityUrl", csapApp.rootProjectEnvSettings( ).getHostActivityUrl( ) ) ;
		modelMap.addAttribute( "healthUrl", csapApp.rootProjectEnvSettings( ).getHostHealthUrl( ) ) ;

		modelMap.addAttribute( "vsphereEnabled", csapApp.rootProjectEnvSettings( ).isVsphereConfigured( ) ) ;

		if ( csapApp.isDockerInstalledAndActive( ) ) {

			modelMap.addAttribute( "dockerUrl", csapApp.getDockerIntegration( ).getSettings( ).getUrl( ) ) ;
			modelMap.addAttribute( "dockerRepository", csapApp.getDockerIntegration( ).getSettings( )
					.getTemplateRepository( ) ) ;
			modelMap.addAttribute( "referenceImages", csapApp.getDockerUiDefaultImages( ) ) ;

		}

		if ( csapApp.isKubernetesInstalledAndActive( ) ) {

			ServiceInstance kubernetesInstance = csapApp.kubeletInstance( ) ;

			modelMap.addAttribute( "kubernetesApiUrl", kubernetesInstance.getUrl( ).split( "," )[0] ) ;
			modelMap.addAttribute( "kubernetesNamespaces", csapApp.getKubernetesIntegration( ).nameSpaces( ) ) ;
			modelMap.addAttribute( "kubernetesServiceTypes", KubernetesJson.k8TypeList( ) ) ;

		}

		modelMap.addAttribute( "serviceParam", serviceParam ) ;
		return new ModelAndView( "/os/dashboard" ) ;

	}

	@GetMapping ( "performance" )
	public String legacyPerformancePortalRedirect ( RedirectAttributes redirectAttributes , HttpServletRequest req ) {

		redirectAttributes.addAllAttributes( req.getParameterMap( ) ) ;

		return "redirect:/csap-analytics" ;

	}

	@Autowired
	CsapCoreService agentConfig ;

	@Autowired
	CsapInformation csapInformation ;

	@Autowired
	CorePortals corePortals ;

	private void setCommonAttributes ( ModelMap modelMap , HttpSession session , String windowName ) {

		// log access

		var auditTrail = CsapCoreService.OS_URL + '/' + windowName ;

		if ( csapApp.getActiveUsers( ).addTrail( auditTrail ) ) {

			csapEventClient.publishUserEvent( CsapEvents.CSAP_OS_CATEGORY + "/accessed", CsapUser.currentUsersID( ),
					"User interface: " + windowName, "" ) ;

		}

		corePortals.setCommonAttributes( modelMap, session ) ;

		modelMap.addAttribute( "toolsServer", csapApp.rootProjectEnvSettings( ).getCsapAnalyticsServerRootUrl( ) ) ;
		modelMap.addAttribute( "host", csapApp.getCsapHostName( ) ) ;
		modelMap.addAttribute( "version", csapInformation.getVersion( ) ) ;

		modelMap.addAttribute( "dateTime",
				LocalDateTime.now( ).format( DateTimeFormatter.ofPattern( "HH:mm:ss,   MMMM d  uuuu " ) ) ) ;

		modelMap.addAttribute( "userid", CsapUser.currentUsersID( ) ) ;

		modelMap.addAttribute( "scriptBase", csapApp.getScriptToken( ) ) ;
		modelMap.addAttribute( "common_scripts", csapApp.csapPlatformPath( "bin/csap-environment.sh" )
				.getAbsolutePath( ) ) ;

		modelMap.addAttribute( "adminRole", false ) ;

		if ( securitySettings.getRoles( ).getAndStoreUserRoles( session )
				.contains( CsapSecurityRoles.ADMIN_ROLE ) ) {

			modelMap.addAttribute( "adminRole", true ) ;

		}

		modelMap.addAttribute( "adminGroup", securitySettings.getRoles( ).getAdminGroup( ) ) ;

		session.setAttribute( CsapCore.ROLES, securitySettings.getRoles( ).getAndStoreUserRoles( session ) ) ;

		modelMap.addAttribute( "csapApp", csapApp ) ;

		modelMap.addAttribute( "life", csapApp.getCsapHostEnvironmentName( ) ) ;

		modelMap.addAttribute( "agentUser", csapApp.getAgentRunUser( ) ) ;

	}

}
