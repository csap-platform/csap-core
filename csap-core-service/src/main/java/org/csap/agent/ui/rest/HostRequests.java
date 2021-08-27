package org.csap.agent.ui.rest ;

import java.io.File ;
import java.io.IOException ;
import java.lang.management.ManagementFactory ;
import java.lang.management.OperatingSystemMXBean ;
import java.nio.charset.Charset ;
import java.nio.file.Files ;
import java.sql.Connection ;
import java.sql.DriverManager ;
import java.sql.ResultSet ;
import java.sql.SQLException ;
import java.text.Format ;
import java.text.SimpleDateFormat ;
import java.time.LocalDateTime ;
import java.time.format.DateTimeFormatter ;
import java.util.ArrayList ;
import java.util.Arrays ;
import java.util.Date ;
import java.util.LinkedHashMap ;
import java.util.List ;
import java.util.concurrent.TimeUnit ;
import java.util.regex.Matcher ;

import javax.inject.Inject ;
import javax.servlet.http.HttpServletRequest ;
import javax.servlet.http.HttpServletResponse ;
import javax.servlet.http.HttpSession ;

import org.apache.commons.io.FileUtils ;
import org.csap.agent.CsapCore ;
import org.csap.agent.CsapCoreService ;
import org.csap.agent.CsapTemplate ;
import org.csap.agent.integrations.CsapEvents ;
import org.csap.agent.linux.OsCommandRunner ;
import org.csap.agent.linux.OutputFileMgr ;
import org.csap.agent.linux.TransferManager ;
import org.csap.agent.model.Application ;
import org.csap.agent.model.ServiceInstance ;
import org.csap.agent.services.OsManager ;
import org.csap.agent.services.ServiceOsManager ;
import org.csap.agent.stats.HostCollector ;
import org.csap.agent.stats.OsSharedResourcesCollector ;
import org.csap.docs.CsapDoc ;
import org.csap.helpers.CSAP ;
import org.csap.helpers.CsapApplication ;
import org.csap.security.CsapUser ;
import org.csap.security.config.CsapSecurityRoles ;
import org.csap.security.config.CsapSecuritySettings ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.beans.factory.annotation.Autowired ;
import org.springframework.beans.factory.annotation.Qualifier ;
import org.springframework.http.HttpEntity ;
import org.springframework.http.HttpHeaders ;
import org.springframework.http.MediaType ;
import org.springframework.http.client.SimpleClientHttpRequestFactory ;
import org.springframework.ldap.core.LdapTemplate ;
import org.springframework.ldap.core.support.LdapContextSource ;
import org.springframework.ui.ModelMap ;
import org.springframework.util.LinkedMultiValueMap ;
import org.springframework.util.MultiValueMap ;
import org.springframework.web.bind.annotation.GetMapping ;
import org.springframework.web.bind.annotation.PathVariable ;
import org.springframework.web.bind.annotation.PostMapping ;
import org.springframework.web.bind.annotation.RequestMapping ;
import org.springframework.web.bind.annotation.RequestParam ;
import org.springframework.web.bind.annotation.RestController ;
import org.springframework.web.client.RestTemplate ;
import org.springframework.web.multipart.MultipartFile ;

import com.fasterxml.jackson.databind.JsonNode ;
import com.fasterxml.jackson.databind.ObjectMapper ;
import com.fasterxml.jackson.databind.node.ArrayNode ;
import com.fasterxml.jackson.databind.node.ObjectNode ;

import io.micrometer.core.instrument.util.StringUtils ;

/**
 *
 * HostRequests is a container for MVC actions targeting OS metric commands.
 * Note that it has more permissive timeouts for command execution to allow for
 * long running operations
 *
 * @author someDeveloper
 *
 * @see <a href=
 *      "http://static.springsource.org/spring/docs/current/spring-framework-reference/html/mvc.html">
 *      SpringMvc Docs </a>
 *
 * @see SpringContext_agentSvcServlet
 *
 */
@RestController
@RequestMapping ( CsapCoreService.OS_URL )
@CsapDoc ( title = "Host Operations" , notes = {
		"Comprehensive set of OS commands, including ps, top, file transfer, and many others",
		"<a class='csap-link' target='_blank' href='https://github.com/csap-platform/csap-core/wiki'>learn more</a>",
		"<img class='csapDocImage' src='CSAP_BASE/images/portals.png' />"
} )
public class HostRequests {

	final Logger logger = LoggerFactory.getLogger( this.getClass( ) ) ;

	ObjectMapper jacksonMapper = new ObjectMapper( ) ;

	@Inject
	public HostRequests ( Application csapApp, OsManager osManager,
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

	OsCommandRunner osCommandRunner = new OsCommandRunner( 60, 1, "HostRequests" ) ;

	private String getSearchTemplate ( )
		throws IOException {

		String searchTemplate = FileUtils.readFileToString( CsapTemplate.search.getFile( ) )
				.replaceAll(
						"\\r\\n|\\r|\\n", System.getProperty( "line.separator" ) ) ;

		return searchTemplate ;

	}

	// REST APIS below
	@PostMapping ( value = "/user/settings" , produces = MediaType.APPLICATION_JSON_VALUE )
	public ObjectNode settingsUpdate (
										@RequestParam ( value = "testUser" , required = false ) String testUser ,
										@RequestParam ( value = "new" , required = false ) String preferencesString )
		throws IOException {

		ObjectNode resultsJson = jacksonMapper.createObjectNode( ) ;
		String user = securitySettings.getRoles( ).getUserIdFromContext( ) ;

		ObjectNode preferences ;

		if ( testUser != null ) {

			preferences = jacksonMapper.createObjectNode( ) ;
			preferences.put( "test", "test insert by " + user ) ;
			user = testUser ;

		} else {

			preferences = (ObjectNode) jacksonMapper.readTree( preferencesString ) ;

		}

		csapEventClient.publishEvent( CsapEvents.CSAP_USER_SETTINGS_CATEGORY + user,
				"User Preferences", null,
				preferences ) ;

		resultsJson.put( "user", user ) ;
		resultsJson.set( "preferences", preferences ) ;
		return resultsJson ;

	}

	@Inject
	@Qualifier ( "csapEventsService" )
	private RestTemplate csapEventsService ;

	@GetMapping ( value = "/user/settings" , produces = MediaType.APPLICATION_JSON_VALUE )
	public ObjectNode settingsGet (
									@RequestParam ( value = "testUser" , required = false ) String testUser ) {

		ObjectNode resultsJson = jacksonMapper.createObjectNode( ) ;

		if ( ! csapApp.rootProjectEnvSettings( ).isEventPublishEnabled( ) ) {

			resultsJson.put( "info", "user preferences not available - event publishing is disabled" ) ;
			logger.info(
					"Skipping user settings as remote access is disabled by lifeCycleSettings().isEventPublishEnabled() " ) ;
			return resultsJson ;

		}

		String user = securitySettings.getRoles( ).getUserIdFromContext( ) ;

		if ( testUser != null ) {

			user = testUser ;

		}

		resultsJson.put( "userid", user ) ;

		String restUrl = csapApp.rootProjectEnvSettings( )
				.getEventApiUrl( ) + "/latest?keepMostRecent=5&category="
				+ CsapEvents.CSAP_USER_SETTINGS_CATEGORY + user ;

		resultsJson.put( "url", restUrl ) ;

		if ( ! restUrl.startsWith( "http" ) ) {

			logger.debug( "====> \n\n Cache Reuse or disabled" ) ;

			resultsJson.put( "message", "cache disabled: " + restUrl ) ;

		} else {

			try {

				ObjectNode restResponse = csapEventsService.getForObject( restUrl, ObjectNode.class ) ;

				if ( restResponse != null ) {

					resultsJson.set( "response", restResponse.get( "data" ) ) ;

				} else {

					resultsJson.put( "message", "Got a null response from url: " + restUrl ) ;

				}

			} catch ( Exception e ) {

				logger.error( "Failed getting user settings from url: " + restUrl, CSAP.buildCsapStack( e ) ) ;
				resultsJson.put( "url", restUrl ) ;
				resultsJson.put( "message", "Error during Access: " + e.getMessage( ) ) ;

			}

		}

		return resultsJson ;

	}

	@PostMapping ( value = "/logSearch" , produces = MediaType.APPLICATION_JSON_VALUE )
	public ObjectNode logSearch (
									ModelMap modelMap ,
									@RequestParam ( "searchTarget" ) String searchTarget ,
									@RequestParam ( "serviceName" ) String serviceName ,
									@RequestParam ( "fromFolder" ) String fromFolder ,
									@RequestParam ( "maxMatches" ) int maxMatches ,
									@RequestParam ( "maxDepth" ) String maxDepth ,
									@RequestParam ( "linesBefore" ) int linesBefore ,
									@RequestParam ( "linesAfter" ) int linesAfter ,
									@RequestParam ( "tailLines" ) int tailLines ,
									@RequestParam ( value = "ignoreCase" , required = false , defaultValue = "false" ) boolean ignoreCase ,
									@RequestParam ( value = "delim" , required = false , defaultValue = "false" ) boolean delim ,
									@RequestParam ( value = "reverseOrder" , required = false , defaultValue = "false" ) boolean reverseOrder ,
									@RequestParam ( value = "zipSearch" , required = false , defaultValue = "false" ) boolean zipSearch ,
									@RequestParam ( value = "cancel" , required = false , defaultValue = "none" ) String cancelFlag ,
									@RequestParam ( value = "jobId" , required = true ) String jobId ,
									@RequestParam ( "hosts" ) String[] hosts ,
									@RequestParam ( value = "scriptName" , required = true ) String scriptName ,
									@RequestParam ( value = "timeoutSeconds" , required = false , defaultValue = "30" ) int timeoutSeconds ,
									HttpSession session )
		throws IOException {

		if ( fromFolder.startsWith( "logs" ) ) {

			fromFolder = fromFolder.substring( 5 ) ;

		}

		File targetFile = csapApp.getRequestedFile( fromFolder, serviceName, false ) ;

		logger.debug( "Target: {},  processing absolutePath: {}", targetFile.getAbsolutePath( ),
				csapApp.getCsapWorkingFolder( )
						.getAbsolutePath( ) ) ;

		if ( searchTarget.length( ) == 0 || searchTarget.indexOf( ';' ) != -1 ) {

			ObjectNode resultsNode = jacksonMapper.createObjectNode( ) ;
			resultsNode.put( "error", "Add some text to search for. It is empty or contains invalid characters." ) ;
			return resultsNode ;

		}

		boolean isUserAnAdmin = securitySettings.getRoles( ).getAndStoreUserRoles( session )
				.contains( CsapSecurityRoles.ADMIN_ROLE ) ;

		var isFileInLogFolder = false ;

		if ( StringUtils.isNotEmpty( serviceName ) ) {

			isFileInLogFolder = targetFile.getParentFile( )
					.getAbsolutePath( )
					.startsWith( csapApp.getLogDir( serviceName ).getAbsolutePath( ) ) ;

		}

		boolean isSearchAccessAllowed = //
				isFileInLogFolder
						|| isUserAnAdmin ;

		if ( targetFile == null
				|| targetFile.getAbsolutePath( )
						.contains( ";" )
				|| ! targetFile.getParentFile( )
						.exists( )
				|| ! isSearchAccessAllowed ) {

			ObjectNode resultsNode = jacksonMapper.createObjectNode( ) ;
			resultsNode.put( "error", "Invalid path " + fromFolder ) ;
			logger.warn( "Target: {},  processing absolutePath: {}, isSearchAccessAllowed: {}",
					targetFile.getAbsolutePath( ),
					csapApp.getCsapWorkingFolder( ).getAbsolutePath( ),
					isSearchAccessAllowed ) ;
			;
			return resultsNode ;

		}

		String maxCommand = "" ;

		if ( maxMatches != 0 ) {

			maxCommand = "--max-count " + maxMatches ;

		}

		String ignoreCommand = "" ;

		if ( ignoreCase ) {

			ignoreCommand = "-i" ;

		}

		String delimCommand = "" ;

		if ( delim ) {

			delimCommand = "--group-separator=__CSAPDELIM__" ;

		}

		String tailCommand = "" ;

		if ( tailLines != 0 ) {

			tailCommand = Integer.toString( tailLines ) ;

		}

		String linesBeforeFinal = Integer.toString( linesBefore ) ;
		String linesAfterFinal = Integer.toString( linesAfter ) ;

		if ( reverseOrder ) {

			// File is being reversed, so reverse the search
			linesBeforeFinal = Integer.toString( linesAfter ) ;
			linesAfterFinal = Integer.toString( linesBefore ) ;

		}

		String contents = getSearchTemplate( )
				.replaceAll( "__searchTarget__", Matcher.quoteReplacement( searchTarget ) )
				.replaceAll( "__searchLocation__", Matcher.quoteReplacement( targetFile.getAbsolutePath( ) ) )
				.replaceAll( "__maxMatches__", maxCommand )
				.replaceAll( "__maxDepth__", maxDepth )
				.replaceAll( "__linesBefore__", linesBeforeFinal )
				.replaceAll( "__linesAfter__", linesAfterFinal )
				.replaceAll( "__ignoreCase__", ignoreCommand )
				.replaceAll( "__tailLines__", tailCommand )
				.replaceAll( "__delim__", delimCommand )
				.replaceAll( "__reverseOrder__", Boolean.toString( reverseOrder ) )
				.replaceAll( "__zipSearch__", Boolean.toString( zipSearch ) ) ;

		if ( ! delim ) {

			contents = contents.replaceAll( "__delim__", "" ) ;

		}

		scriptName += "_" + jobId ;

		if ( OsCommandRunner.CANCEL_SUFFIX.endsWith( cancelFlag ) ) {

			scriptName += OsCommandRunner.CANCEL_SUFFIX ;

		}

		logger.debug( "search Script: {}", contents ) ;

		String runUser = csapApp.getAgentRunUser( ) ;

		if ( isUserAnAdmin ) {

			// lots of files with restricted access
			runUser = "root" ;

		}

		OutputFileMgr outputFm = new OutputFileMgr( csapApp.getScriptDir( ), scriptName ) ;
		ObjectNode resultsNode = osManager.executeShellScriptClustered(
				securitySettings.getRoles( ).getUserIdFromContext( ),
				timeoutSeconds, contents, runUser,
				hosts,
				scriptName, outputFm ) ;
		outputFm.opCompleted( ) ;

		if ( Application.isRunningOnDesktop( ) ) {

			logger.info( "Dummy results for desktop, using script  contents:\n {}", contents ) ;
			ObjectNode transferResults = (ObjectNode) resultsNode.at( "/otherHosts/0/transferResults" ) ;
			ArrayNode scriptResults = transferResults.putArray( "scriptResults" ) ;
			// File desktopResults = (new ClassPathResource(
			// "/linux/searchResults.txt" )).getFile();

			if ( searchTarget.equals( "desktop-sample" ) ) {

				scriptResults.add( csapApp.check_for_stub( "", "linux/searchResults.txt" ) ) ;

			} else {

				scriptResults.add( "desktop-sample search will get more" ) ;

			}

		}

		return resultsNode ;

	}

	public final static String EXECUTE_URL = "/executeScript" ;

	/**
	 *
	 * If scriptName ends with .cancel, job with same name will be cancelled.
	 *
	 * @param modelMap
	 * @param contents
	 * @param chownUserid
	 * @param clusterName
	 * @param scriptName
	 * @param timeoutSeconds
	 * @param request
	 * @param response
	 * @throws IOException
	 */
	@RequestMapping ( value = EXECUTE_URL , produces = MediaType.APPLICATION_JSON_VALUE )
	public ObjectNode executeScript (
										@RequestParam ( "contents" ) String contents ,
										// @RequestParam ( value = "chownUserid" , required = false , defaultValue =
										// "dummy" ) String
										// chownUserid,
										@RequestParam ( value = "runAsRoot" , required = false ) boolean runAsRoot ,

										@RequestParam ( value = "cancel" , required = false , defaultValue = "none" ) String cancelFlag ,
										@RequestParam ( value = "jobId" , required = true ) String jobId ,
										@RequestParam ( "hosts" ) String[] hosts ,
										@RequestParam ( value = "scriptName" , required = true ) String scriptName ,
										@RequestParam ( value = "timeoutSeconds" , required = false , defaultValue = "120" ) int timeoutSeconds )
		throws Exception {

		scriptName += "_" + jobId ;

		if ( OsCommandRunner.CANCEL_SUFFIX.endsWith( cancelFlag ) ) {

			scriptName += OsCommandRunner.CANCEL_SUFFIX ;

		}

		String runUser = csapApp.getAgentRunUser( ) ;

		if ( runAsRoot ) {

			runUser = "root" ;

		}

		OutputFileMgr outputFm = new OutputFileMgr( csapApp.getScriptDir( ), scriptName ) ;
		ObjectNode resultsNode = osManager.executeShellScriptClustered(
				securitySettings.getRoles( ).getUserIdFromContext( ),
				timeoutSeconds, contents, runUser,
				hosts,
				scriptName, outputFm ) ;

		if ( csapApp.isDesktopProfileActive( ) ) {

			TimeUnit.SECONDS.sleep( 5 ) ;

		}

		outputFm.opCompleted( ) ;

		return resultsNode ;

	}

	@RequestMapping ( value = "/killScript" , produces = MediaType.APPLICATION_JSON_VALUE )
	public ObjectNode killScript (
									ModelMap modelMap ,
									@RequestParam ( "contents" ) String contents ,
									@RequestParam ( value = "chownUserid" , required = false , defaultValue = "dummy" ) String chownUserid ,
									@RequestParam ( "hosts" ) String[] hosts ,
									@RequestParam ( value = "scriptName" , required = true ) String scriptName ,
									@RequestParam ( value = "timeoutSeconds" , required = false , defaultValue = "120" ) int timeoutSeconds )
		throws IOException {

		OutputFileMgr outputFm = new OutputFileMgr( csapApp.getScriptDir( ), scriptName ) ;
		ObjectNode resultsNode = osManager.executeShellScriptClustered(
				securitySettings.getRoles( ).getUserIdFromContext( ),
				timeoutSeconds, contents, chownUserid,
				hosts,
				scriptName, outputFm ) ;
		outputFm.opCompleted( ) ;

		return resultsNode ;

	}

	public static final String MASTERS_READY_URL = "/kubernetes/masters/ready" ;

	@GetMapping ( MASTERS_READY_URL )
	public boolean allMastersReady (
										@RequestParam int numberExpected ) {

		if ( csapApp.getKubernetesIntegration( ).areMinimumMastersReady( numberExpected ) ) {

			return true ;

		} else {

			return false ;

		}

	}

	public static final String JOIN_URL = "/kubernetes/join" ;

	@GetMapping ( JOIN_URL )
	public String kubernetesJoin (
									@RequestParam String token ,
									@RequestParam String type )
		throws IOException {

		logger.debug( "token: {} type: {}", token, type ) ;

		String joinCommand = "not-authenticated" ;

		boolean isAuthenticated = false ;
		ServiceInstance kubletOnCurrentHost = csapApp.kubeletInstance( ) ;

		if ( kubletOnCurrentHost != null ) {

			joinCommand += "-" + kubletOnCurrentHost.getName( ) ;

			LinkedHashMap<String, String> environmentVariables = serviceOsManager.buildServiceEnvironmentVariables(
					kubletOnCurrentHost ) ;

			logger.debug( "environmentVariables: {}", environmentVariables ) ;

			// Optional<String> matched =
			String clusterToken = environmentVariables.get( "clusterToken" ) ;

			logger.debug( "clusterToken: {} token: {}", clusterToken, token ) ;
			isAuthenticated = StringUtils.isNotEmpty( clusterToken ) && clusterToken.equals( token ) ;

		}

		if ( isAuthenticated ) {

			joinCommand = osManager.getCachedKubernetesJoin( ).path( type ).asText( "not-found" ) ;

		} else {

			logger.warn( "Security Warning: invalid token" ) ;

		}

		return joinCommand ;

	}

	public static final String DEFINITION_ZIP_URL = "/definitionZip" ;

	@GetMapping ( DEFINITION_ZIP_URL )
	public void defintion (
							@RequestParam ( value = "path" , required = false , defaultValue = "" ) String path ,
							HttpServletRequest request ,
							HttpSession session ,
							HttpServletResponse response )
		throws IOException {

		File source = new File( csapApp.getDefinitionFolder( ), path ) ;
		csapApp.getOsManager( ).buildAndWriteZip( response, source ) ;

		return ;

	}

	public static final String FOLDER_ZIP_URL = "/folderZip" ;

	@GetMapping ( FOLDER_ZIP_URL )
	public void folderZip (
							@RequestParam String path ,
							@RequestParam String token ,
							@RequestParam String service ,
							HttpServletRequest request ,
							HttpSession session ,
							HttpServletResponse response )
		throws IOException {

		logger.debug( "path: {}, token: {} service: {}", path, token, service ) ;
		File source = new File( System.getProperty( "user.home" ), path ) ;

		boolean isAuthenticated = false ;
		ServiceInstance serviceInstance = csapApp.getServiceInstanceCurrentHost( service ) ;

		if ( serviceInstance != null ) {

			LinkedHashMap<String, String> environmentVariables = serviceOsManager.buildServiceEnvironmentVariables(
					serviceInstance ) ;

			// Optional<String> matched =
			String clusterToken = environmentVariables.get( "clusterToken" ) ;
			isAuthenticated = StringUtils.isNotEmpty( clusterToken ) && clusterToken.equals( token ) ;

		}

		if ( ! isAuthenticated ) {

			response.setStatus( HttpServletResponse.SC_FORBIDDEN ) ;
			response.getWriter( ).println( HttpServletResponse.SC_FORBIDDEN + ": NOT_AUTHORIZED" ) ;

		} else {

			logger.debug( "source: '{}' token: '{}'", source.getAbsolutePath( ), token ) ;

			csapApp.getOsManager( ).buildAndWriteZip( response, source ) ;

		}

		return ;

	}

	@RequestMapping ( value = "/showRoles" , produces = MediaType.TEXT_PLAIN_VALUE )
	public String showRoles ( HttpSession session ) {

		csapApp.metricManager( ).clearResourceStats( ) ;

		StringBuilder results = new StringBuilder( ) ;

		results.append( CsapApplication.LINE ) ;
		results.append( "\n CSAP User Groups:" ) ;
		results.append( "\n View Group: " + securitySettings.getRoles( ).getViewGroup( ) ) ;
		results.append( "\n Admin Group: " + securitySettings.getRoles( ).getAdminGroup( ) ) ;
		results.append( "\n Build Group: " + securitySettings.getRoles( ).getBuildGroup( ) ) ;
		results.append( "\n Infra Group: " + securitySettings.getRoles( ).getInfraGroup( ) ) ;
		results.append( CsapApplication.LINE ) ;

		results.append( "\n\n" + CsapApplication.LINE ) ;
		results.append( "\n Your Roles:" ) ;

		for ( String role : securitySettings.getRoles( ).getAndStoreUserRoles( session ) ) {

			results.append( "\n Role: " + role ) ;

		}

		results.append( CsapApplication.LINE ) ;

		return results.toString( ) ;

	}

	@Autowired ( required = false )
	private LdapTemplate csapLdap ;

	@SuppressWarnings ( "unchecked" )
	@RequestMapping ( value = "/testLdap" , produces = MediaType.TEXT_PLAIN_VALUE )
	public String testLdap (
								@RequestParam ( value = "numAttempts" , required = false , defaultValue = "5" ) int numAttempts ,
								String ldapPassword ,
								String ldapUser ,
								String ldapUrl )
		throws Exception {

		int maxAttempts = numAttempts ;
		int numFailures = 0 ;

		//
		if ( ldapUrl == null ) {

			ldapUrl = securitySettings.getProvider( ).getUrl( ) ;

		}

		if ( ldapUser == null ) {

			ldapUser = securitySettings.getProvider( ).getLdapSearchUser( ).toString( ) ;

		}

		StringBuffer resultsBuffer = new StringBuffer( "\n\n http request parameters can be updated as needed:" ) ;
		resultsBuffer.append( "\n\t ldapUrl: " + ldapUrl ) ;
		resultsBuffer.append( "\n\t ldapUser: " + ldapUser ) ;
		resultsBuffer.append( "\n\t ldapPassword: " + ldapPassword ) ;
		resultsBuffer.append( "\n\t numAttempts: " + numAttempts ) ;

		boolean addedEntry = false ;

		for ( int i = 0; i < maxAttempts; i++ ) {

			logger.debug( "\n\n *****************   Attempt: {}", i ) ;

			LdapTemplate testLdapTemplate = new LdapTemplate( ) ;

			if ( ldapPassword != null ) {

				LdapContextSource contextSource = new LdapContextSource( ) ;

				contextSource.setUserDn( ldapUser ) ;
				contextSource.setPassword( ldapPassword ) ;
				contextSource.setUrl( ldapUrl ) ;

				//
				contextSource.afterPropertiesSet( ) ;
				testLdapTemplate.setContextSource( contextSource ) ;
				testLdapTemplate.afterPropertiesSet( ) ;

				logger.debug( "\n\n Ldap connection: {}", contextSource.getBaseLdapPathAsString( ) ) ;

			} else {

				if ( ! addedEntry ) {

					resultsBuffer.append(
							"\n\n WARNING: ldapPassword not set - cached connection will be used. To validate connections add: &ldapPassword=CHANGE_ME" ) ;

				}

				testLdapTemplate = csapLdap ;

			}

			String dn = "uid=" + securitySettings.getRoles( ).getUserIdFromContext( ) + ","
					+ securitySettings.getProvider( ).getSearchUser( ) ;

			CsapUser csapUser ;

			try {

				csapUser = (CsapUser) testLdapTemplate.lookup( dn, new CsapUser( ) ) ;
				logger.debug( "CsapUser Raw Attributes: \n\t {}", csapUser.getAttibutesJson( ) ) ;

				csapUser = (CsapUser) testLdapTemplate.lookup( dn, CsapUser.PRIMARY_ATTRIBUTES, new CsapUser( ) ) ;
				logger.debug( "CsapUser.PRIMARY_ATTRIBUTES  filter: \n\t {}", csapUser.getAttibutesJson( ) ) ;

				if ( ! addedEntry ) {

					resultsBuffer.append( "\n\n First successful output shown: "
							+ jacksonMapper.writerWithDefaultPrettyPrinter( )
									.writeValueAsString(
											csapUser.getAttibutesJson( ) ) ) ;
					addedEntry = true ;

				}

			} catch ( Exception e ) {

				resultsBuffer.append( "\n\n failure reason: " + CSAP.buildCsapStack( e ) ) ;
				logger.error( "Failed LDAP: {}", CSAP.buildCsapStack( e ) ) ;
				numFailures++ ;

			}

		}

		resultsBuffer.insert( 0, "numAttempts: " + numAttempts + "        numFailures: " + numFailures ) ;
		logger.debug( resultsBuffer.toString( ) ) ;

		return resultsBuffer.toString( ) ;

	}

	@GetMapping ( "/getDf" )
	public ObjectNode getDf ( )
		throws IOException {

		return osManager.getCachedFileSystemInfo( ) ;

	}

	@GetMapping ( "/network/summary" )
	public ObjectNode networkReceiveAndTransmit ( )
		throws IOException {

		return osManager.networkReceiveAndTransmit( ) ;

	}

	final OperatingSystemMXBean osStats = ManagementFactory.getOperatingSystemMXBean( ) ;

	@GetMapping ( "/cached/status" )
	public ObjectNode cachedStatus ( String namespace ) {

		logger.debug( " Entered" ) ;

		return csapApp.healthManager( ).build_host_status_using_cached_data( namespace ) ;

	}

	@GetMapping ( value = "/lastCollected" )
	public ObjectNode getLastCollected ( ) {

		return osManager.buildLatestCollectionReport( ) ;

	}

	String memResultsCache = "" ;
	long lastMemTimeStamp = 0 ;

	// @GetMapping ( value = "/getHosts" , produces =
	// MediaType.APPLICATION_JSON_VALUE )
	// public ObjectNode getHosts (
	// @RequestParam ( value = "clusterName" , required = true ) String clusterName
	// ) {
	//
	// logger.debug( "clusterName: {} ", clusterName ) ;
	//
	// ObjectNode responseObject = jacksonMapper.createObjectNode() ;
	// responseObject.put( "cluster", clusterName ) ;
	//
	// responseObject.putArray( "hosts" ) ;
	//
	// responseObject.putPOJO( "hosts", csapApp.getMutableHostsInActivePackage(
	// clusterName ) ) ;
	//
	// return responseObject ;
	// }

	@GetMapping ( value = "/getMem" , produces = MediaType.APPLICATION_JSON_VALUE )
	public JsonNode getMem ( )
		throws IOException {

		return osManager.getCachedMemoryMetrics( ) ;

	}

	@GetMapping ( value = "/diskActivity" , produces = MediaType.APPLICATION_JSON_VALUE )
	public JsonNode diskActivity ( )
		throws IOException {

		return osManager.disk_reads_and_writes( ) ;

	}

	@GetMapping ( "/kernel/limits" )
	public ObjectNode kernelLimits ( )
		throws Exception {

		return osManager.run_kernel_limits( ) ;

	}

	@GetMapping ( "/processes/all" )
	public ObjectNode processesAll ( ) {

		ObjectNode serviceMetricsJson = osManager.buildServiceStatsReportAndUpdateTopCpu( false ) ;

		Format tsFormater = new SimpleDateFormat( "HH:mm:ss" ) ;
		// JsonNode rootNode = jacksonMapper.valueToTree(commandMap);
		( (ObjectNode) serviceMetricsJson ).put( "timestamp", tsFormater.format( new Date( ) ) ) ;

		return serviceMetricsJson ;

	}

	@GetMapping ( "/processes/csap" )
	public ObjectNode processesCsap ( ) {

		ObjectNode osProcessReport = osManager.buildServiceStatsReportAndUpdateTopCpu( true ) ;

		osProcessReport.put( "csapFiltered", true ) ;

		Format tsFormater = new SimpleDateFormat( "HH:mm:ss" ) ;
		// JsonNode rootNode = jacksonMapper.valueToTree(commandMap);
		( (ObjectNode) osProcessReport ).put( "timestamp", tsFormater.format( new Date( ) ) ) ;

		return osProcessReport ;

	}

	@GetMapping ( "/processes/top" )
	public ObjectNode processesTop ( ) {

		ObjectNode processInfo = osManager.runOsTopCommand( ) ;

		Format tsFormater = new SimpleDateFormat( "HH:mm:ss" ) ;
		// JsonNode rootNode = jacksonMapper.valueToTree(commandMap);
		( (ObjectNode) processInfo ).put( "timestamp", tsFormater.format( new Date( ) ) ) ;

		return processInfo ;

	}

	@GetMapping ( "/processes/ps/collected" )
	public ObjectNode processesLastCollected ( ) {

		ObjectNode processInfo = osManager.latest_process_discovery_results( ) ;

		Format tsFormater = new SimpleDateFormat( "HH:mm:ss" ) ;
		// JsonNode rootNode = jacksonMapper.valueToTree(commandMap);
		( (ObjectNode) processInfo ).put( "timestamp", tsFormater.format( new Date( ) ) ) ;

		return processInfo ;

	}

	@GetMapping ( "/process/report/{pid}" )
	public ObjectNode processReport ( @PathVariable String pid ) {

		return osManager.buildProcessReport( pid, "os-process" ) ;

	}

	@RequestMapping ( "/updatePriority" )
	public void updatePriority (
									@RequestParam ( value = "pid" ) String pid ,
									@RequestParam ( value = "priority" ) String priority ,
									HttpServletRequest request ,
									HttpServletResponse response ) {

		logger.debug( "Entered" ) ;

		response.setContentType( MediaType.APPLICATION_JSON_VALUE ) ;
		String output = "\n Renice not support on non-root installs" ;

		if ( csapApp.isRunningAsRoot( ) ) {

			File scriptPath = csapApp.csapPlatformPath( "/bin/csap-renice.sh" ) ;
			List<String> parmList = new ArrayList<String>( ) ;
			// parmList.add("bash");
			parmList.add( "/usr/bin/sudo" ) ;
			// parmList.add("-c") ;
			parmList.add( scriptPath.getAbsolutePath( ) ) ;
			parmList.add( "UiRequest" ) ;
			parmList.add( priority ) ;
			parmList.add( pid ) ;

			auditRecord( "updatePriority", "pid: " + pid + " os priority modified to: " + priority ) ;

			output = "\n" + osCommandRunner.executeString( null, parmList ) ;

		}

		logger.debug( "results: {} ", output ) ;

		ObjectNode resultsNode = jacksonMapper.createObjectNode( ) ;
		resultsNode.put( "results", output ) ;

		osManager.resetAllCaches( ) ;

		try {

			response.getWriter( )
					.println( jacksonMapper.writeValueAsString( resultsNode ) ) ;

			// response.getWriter().print(processesFound);
		} catch ( Exception e ) {

			logger.error( "Failed to write output", e ) ;

		}

	}

	public final static String HOST_INFO_URL = "/hostOverview" ;

	@Autowired
	ServiceOsManager serviceOsManager ;

	@RequestMapping ( value = HOST_INFO_URL , produces = MediaType.APPLICATION_JSON_VALUE )
	public ObjectNode getHostOverview (
										@RequestParam ( value = CsapCore.HOST_PARAM , required = false ) String host ,
										HttpServletRequest request )
		throws IOException {

		if ( csapApp.isAdminProfile( ) ) {

			if ( Application.isRunningOnDesktop( ) && host.equals( csapApp.getCsapHostName( ) ) ) {

				logger.info( "Desktop testing: using default" ) ;
				return osManager.getHostSummary( ) ;

			}

			MultiValueMap<String, String> urlVariables = new LinkedMultiValueMap<String, String>( ) ;

			if ( host != null ) {

				String url = csapApp.getAgentUrl( host, CsapCoreService.API_AGENT_URL + "/hostSummary", true ) ;
				return csapEventsService.getForObject( url, ObjectNode.class ) ;

			} else {

				ObjectNode error = jacksonMapper.createObjectNode( ) ;
				return error.put( "Error",
						CsapCore.CONFIG_PARSE_ERROR + " - Failed to find hosts parameter" ) ;

			}

		}

		return osManager.getHostSummary( ) ;

	}

	@RequestMapping ( "/getLsof" )
	public void getLsof (
							@RequestParam ( value = "pid" , required = true ) String pidCommaSeparated ,
							HttpServletRequest request ,
							HttpServletResponse response ) {

		logger.debug( " pid: {}", pidCommaSeparated ) ;

		try {

			response.setContentType( "text/plain" ) ;
			response.getWriter( )
					.print(
							"\n\n ========== lsof for pids: " + pidCommaSeparated + "========\n\n" ) ;
			String commandResult = null ;

			String[] pidArray = pidCommaSeparated.split( "," ) ;

			for ( String pid : pidArray ) {

				response.getWriter( )
						.print( "\n\n ========== lsof for pids: " + pid + "========\n\n" ) ;
				// Same host as login, so get the processes
				List<String> parmList = Arrays.asList( "bash", "-c", "/usr/sbin/lsof -p " + pid
						+ " | wc -l" ) ;

				auditRecord( "getLsof", parmList.toString( ) ) ;

				commandResult = osCommandRunner.executeString( parmList, new File( "." ) ) ;

				logger.debug( "result: {}", commandResult ) ;

				response.getWriter( )
						.println( commandResult ) ;

				parmList = Arrays.asList( "bash", "-c", "/usr/sbin/lsof -p " + pid ) ;
				commandResult = osCommandRunner.executeString( parmList, new File( "." ) ) ;

				logger.debug( "result: {}", commandResult ) ;

				response.getWriter( )
						.println( commandResult ) ;

				response.getWriter( )
						.print( "\n\n ========== ========\n\n" ) ;

			}

		} catch ( Exception e ) {

			logger.error( "Failed to rebuild", e ) ;

		}

	}

	@RequestMapping ( "/journal" )
	public void journal (
							@RequestParam ( defaultValue = "none" ) String service ,
							@RequestParam ( defaultValue = "" ) String since ,
							@RequestParam ( defaultValue = "100" ) String numberOfLines ,
							@RequestParam ( defaultValue = "false" ) boolean reverse ,
							@RequestParam ( defaultValue = "false" ) boolean json ,
							HttpServletRequest request ,
							HttpServletResponse response ,
							HttpSession session ) {

		logger.debug( " params: {} ", numberOfLines ) ;

		auditRecord( "top", numberOfLines ) ;

		try {

			if ( json ) {

				response.setContentType( MediaType.APPLICATION_JSON_VALUE ) ;

			} else {

				response.setContentType( "text/plain" ) ;

			}

			// if ( isAdmin)
			if ( ! securitySettings.getRoles( ).getAndStoreUserRoles( session )
					.contains( CsapSecurityRoles.ADMIN_ROLE ) ) {

				response.getWriter( ).println( "*Permission denied: only admins may access journal entries" ) ;
				return ;

			}

			if ( ! json ) {

				response.getWriter( )
						.print( "\n\n ========== journalctl ========\n\n" ) ;

			}

			String scriptOutput = osManager.getJournal( service, since, numberOfLines, reverse, json ) ;

			if ( json ) {

				response.getWriter( ).print( "{ \"results\": [" ) ;
				scriptOutput = scriptOutput.substring( scriptOutput.indexOf( "{" ) ) ;
				String[] jsonLines = scriptOutput.split( System.getProperty( "line.separator" ) ) ;

				for ( int i = 0; i < jsonLines.length; i++ ) {

					response.getWriter( ).print( jsonLines[i] ) ;

					if ( i != jsonLines.length - 1 ) {

						response.getWriter( ).println( "," ) ;

					}

				}

				response.getWriter( ).print( "]}" ) ;

			} else {

				response.getWriter( ).print( scriptOutput ) ;

			}

			if ( ! json ) {

				response.getWriter( )
						.print( "\n\n ========== ========\n\n" ) ;

			}

		} catch ( Exception e ) {

			logger.error( "Failed to rebuild", e ) ;

		}

	}

	@RequestMapping ( "/invalidateSession" )
	public void invalidateSession ( HttpServletRequest request , HttpServletResponse response ) {

		logger.debug( "Entered" ) ;

		try {

			response.setContentType( "text/plain" ) ;
			response.getWriter( )
					.print(
							"\n\n ========== request.getSession().invalidate() ========\n\n" ) ;
			// request.getSession().invalidate() ;

			request.getSession( )
					.invalidate( ) ;

		} catch ( Exception e ) {

			logger.error( "Failed to rebuild", e ) ;

		}

	}

	@GetMapping ( "/getMemInfo" )
	public String getMemInfo ( ) {

		logger.debug( "Entered" ) ;

		StringBuilder info = new StringBuilder( ) ;

		try {

			info.append( "\n\n ========== memInfo ========\n\n" ) ;
			info.append(
					"\n\n ===ref. http://linux-kb.blogspot.com/2009/09/free-memory-in-linux-explained.html \n\n" ) ;

			String psResult = null ;

			// Same host as login, so get the processes
			List<String> parmList = Arrays.asList( "bash", "-c", "cat /proc/meminfo" ) ;
			psResult = osCommandRunner.executeString( null, parmList ) ;

			logger.debug( "result: {}", psResult ) ;

			info.append( psResult ) ;

			info.append( "\n\n ========== ========\n\n" ) ;

		} catch ( Exception e ) {

			logger.error( "Failed to rebuild", e ) ;

		}

		return info.toString( ) ;

	}

	@GetMapping ( "/getMemFree" )
	public void getMemFree ( HttpServletRequest request , HttpServletResponse response ) {

		logger.debug( "Entered" ) ;

		try {

			response.getWriter( )
					.print( "\n\n ========== memInfo ========\n\n" ) ;
			response.getWriter( )
					.print( "\n\n ===ref. http://linux-kb.blogspot.com/2009/09/free-memory-in-linux-explained.html \n\n" ) ;

			String psResult = null ;

			// Same host as login, so get the processes
			List<String> parmList = Arrays.asList( "bash", "-c", "free -m" ) ;
			psResult = osCommandRunner.executeString( null, parmList ) ;

			logger.debug( "result: {}", psResult ) ;

			response.getWriter( )
					.println( psResult ) ;

			response.getWriter( )
					.print( "\n\n ========== ========\n\n" ) ;

		} catch ( Exception e ) {

			logger.error( "Failed to rebuild", e ) ;

		}

	}

	@RequestMapping ( "/getCpuInfo" )
	public void getCpuInfo ( HttpServletRequest request , HttpServletResponse response )
		throws IOException {

		logger.debug( " running getCpuInfo:" ) ;

		response.setContentType( "text/plain" ) ;
		response.getWriter( )
				.print( "\n\n ========== cpuInfo ========\n\n" ) ;
		String psResult = null ;

		// Same host as login, so get the processes
		List<String> parmList = Arrays.asList( "bash", "-c", "cat /proc/cpuinfo" ) ;
		psResult = osCommandRunner.executeString( null, parmList ) ;

		logger.debug( "result: {}", psResult ) ;

		response.getWriter( )
				.println( psResult ) ;

		response.getWriter( )
				.print( "\n\n ========== ========\n\n" ) ;

	}

	@RequestMapping ( "/getPsThreads" )
	public void getPsThreads (
								@RequestParam ( value = "pid" , required = true ) String pid ,
								HttpServletRequest request ,
								HttpServletResponse response )
		throws IOException {

		logger.debug( " pid: {}", pid ) ;

		response.setContentType( "text/plain" ) ;
		response.getWriter( )
				.print( "\n\n ========== lsof ========\n\n" ) ;
		String psResult = null ;

		// Same host as login, so get the processes
		List<String> parmList = Arrays.asList( "bash", "-c", "ps -Lo pcpu,pid,tid,state,nlwp -p "
				+ pid.replaceAll( ",", " -e " ) ) ;

		auditRecord( "psThreads", parmList.toString( ) ) ;

		psResult = osCommandRunner.executeString( null, parmList ) ;

		logger.debug( "result: {}", psResult ) ;

		response.getWriter( )
				.println( psResult ) ;

		response.getWriter( )
				.print( "\n\n ========== ========\n\n" ) ;

	}

	@RequestMapping ( "/showProcesses" )
	public void showProcesses (
								@RequestParam ( value = "sortByNice" , required = false , defaultValue = "false" ) boolean sortByNice ,
								@RequestParam ( value = "csapFilter" , required = false , defaultValue = "false" ) boolean csapFilter ,
								HttpServletRequest request ,
								HttpServletResponse response )
		throws IOException {

		response.setContentType( "text/plain" ) ;

		response.getWriter( )
				.println( osManager.performMemoryProcessList( sortByNice, csapFilter, false ) ) ;

	}

	@RequestMapping ( "/testOracle" )
	public void testOci (
							@RequestParam ( value = "url" , required = true ) String url ,
							@RequestParam ( value = "query" , required = true ) String query ,
							@RequestParam ( value = "user" , required = true ) String user ,
							@RequestParam ( value = "pass" , required = true ) String pass ,
							HttpServletRequest request ,
							HttpServletResponse response )
		throws IOException {

		logger.debug( "user: {},  url: {} , query: {}", user, url, query ) ;

		StringBuilder resultsBuff = new StringBuilder( "\n\nTesting connection: " ) ;

		try ( Connection jdbcConnection = DriverManager.getConnection( url, user, pass );
				ResultSet rs = jdbcConnection.createStatement( )
						.executeQuery( query ); ) {
			// Class.forName("oracle.jdbc.driver.OracleDriver");

			// resultsBuff.append(jdbcConnection.createStatement().executeQuery("select
			// count(*) from job_schedule").getString(1))
			// ;
			while ( rs.next( ) ) {

				resultsBuff.append( rs.getString( 1 ) ) ;

			}

		} catch ( SQLException e ) {

			// resultsBuff.append( "Got an SQL Exception" +
			// Application.getCustomStackTrace( e ) );
			resultsBuff.append( "Got an SQL Exception" + CSAP.buildCsapStack( e ) ) ;

		}

		response.setContentType( "text/plain" ) ;
		response.getWriter( )
				.print(
						"\n\n ========== Results from: " + " url:" + url + " query" + query + "\n\n" ) ;

		response.getWriter( )
				.println( resultsBuff ) ;

		response.getWriter( )
				.println( "\n===================\n\n" ) ;

	}

	@GetMapping ( "/metricIntervals/{type}" )
	public JsonNode getMetricsIntervals (
											@PathVariable ( value = "type" ) String collectionType ) {

		ArrayNode collectionIntervals = jacksonMapper.createArrayNode( ) ;
		String collectorID = collectionType ;

		if ( collectionType.equals( "java" ) ) {

			collectorID = CsapApplication.COLLECTION_APPLICATION ;

		}

		for ( Integer sampleInterval : csapApp.rootProjectEnvSettings( )
				.getMetricToSecondsMap( )
				.get( collectorID ) ) {

			collectionIntervals.add( sampleInterval ) ;

		}

		return collectionIntervals ;

	}

	@GetMapping ( value = "/metricsData" , produces = MediaType.APPLICATION_JSON_VALUE )
	public ObjectNode getMetricsData (
										@RequestParam ( value = CsapCore.HOST_PARAM , required = false ) String[] hostNameArray ,
										@RequestParam ( value = "metricChoice" , required = false , defaultValue = "resource" ) String requestedMetricChoice ,
										@RequestParam ( value = "id" , required = false , defaultValue = "resource" ) String historicalId ,
										@RequestParam ( value = "numSamples" , required = false , defaultValue = "5" ) int numSamples ,
										@RequestParam ( value = "skipFirstItems" , required = false , defaultValue = "0" ) int skipFirstItems ,
										@RequestParam ( value = "serviceName" , required = false ) String[] serviceNameArray ,
										@RequestParam ( value = "resourceTimer" , required = false , defaultValue = "-1" ) Integer resourceTimer ,
										@RequestParam ( value = "numberOfDays" , required = false , defaultValue = "-1" ) int numberOfDays ,
										@RequestParam ( value = "dayOffset" , required = false , defaultValue = "1" ) int dayOffset ,
										@RequestParam ( value = "isLastDay" , required = false , defaultValue = "false" ) boolean isLastDay ,
										HttpServletRequest request )
		throws IOException {

		var serviceNames = List.of( ) ;

		if ( serviceNameArray != null ) {

			serviceNames = List.of( serviceNameArray ) ;

		}

		Integer secondsBetweenCollections = resourceTimer ;

		String collectorID = requestedMetricChoice ;

		if ( requestedMetricChoice.equals( "java" ) ) {

			collectorID = CsapApplication.COLLECTION_APPLICATION ;

		}

		logger.debug(
				"   requestedMetricChoice: {}, collectorID: {}, resourceTimer: {}, \n\t numberOfDays: {} , numSamples: {}, dayOffset: {}",
				requestedMetricChoice, collectorID, resourceTimer, numberOfDays, numSamples, dayOffset ) ;

		// -1 indicates usage of the largest interval in the definition file
		// Real time graphs: numberOfDays== -1 , default to the shortest
		// collection interval
		if ( resourceTimer == -1 ) {

			switch ( collectorID ) {

			case CsapApplication.COLLECTION_HOST:
				secondsBetweenCollections = csapApp.metricManager( ).lastHostCollectionInterval( ) ;
				if ( numberOfDays == -1 ) {

					secondsBetweenCollections = csapApp.metricManager( ).firstHostCollectionInterval( ) ;

				}
				break ;

			case CsapApplication.COLLECTION_OS_PROCESS:
				secondsBetweenCollections = csapApp.metricManager( ).lastServiceCollectionInterval( ) ;
				if ( numberOfDays == -1 ) {

					secondsBetweenCollections = csapApp.metricManager( ).firstServiceCollectionInterval( ) ;

				}
				break ;

			case CsapApplication.COLLECTION_APPLICATION:
				secondsBetweenCollections = csapApp.metricManager( ).lastJavaCollectionInterval( ) ;
				if ( numberOfDays == -1 ) {

					secondsBetweenCollections = csapApp.metricManager( ).firstJavaCollectionInterval( ) ;

				}
				break ;

			default:
				logger.error( "Unknown metric selection: {}", collectorID ) ;
				break ;

			}

		}

		ObjectNode metricsReport = null ;

		// We do not got remote if we are getting historical, or if local
		if ( csapApp.isAdminProfile( )
				&& ! ( Application.isRunningOnDesktop( ) && hostNameArray[0].equals( "localhost" ) ) ) {

			logger.warn( "DEPRECATED - use analytics API" ) ;
			ObjectNode err = jacksonMapper.createObjectNode( ) ;
			err.put( "error", "Use Analytics apis" ) ;
			return err ;

		} else {

			// Used in Host Dashboards
			HostCollector hostCollector = null ;

			switch ( collectorID ) {

			case CsapApplication.COLLECTION_HOST:
				hostCollector = csapApp.metricManager( ).getOsSharedCollector( resourceTimer ) ;
				break ;

			case CsapApplication.COLLECTION_OS_PROCESS:
				hostCollector = csapApp.metricManager( ).getOsProcessCollector( resourceTimer ) ;
				break ;

			case CsapApplication.COLLECTION_APPLICATION:
				hostCollector = csapApp.metricManager( ).getServiceCollector( resourceTimer ) ;
				break ;

			default:
				logger.error( "Unknown metric selection: " + collectorID ) ;
				break ;

			}

			if ( hostCollector == null ) {

				logger.warn( "Should never happen, maybe wrong key was passed: " + resourceTimer ) ;

			} else if ( requestedMetricChoice.equals( CsapApplication.COLLECTION_APPLICATION ) ) {

				logger.debug( "services: {}, metricChoice: {}", serviceNames, hostCollector.toString( ), collectorID ) ;
				metricsReport = hostCollector.buildCollectionReport(
						false, serviceNameArray, numSamples,
						skipFirstItems, "customJmx" ) ;

			} else {

				metricsReport = hostCollector
						.buildCollectionReport( false, serviceNameArray, numSamples, skipFirstItems ) ;

			}

		}

		if ( metricsReport != null && metricsReport.path( "attributes" ).isObject( ) ) {

			// Agent Dashboard hooks: add sample times, and applicaiton services....
			ObjectNode reportAttributes = ( (ObjectNode) metricsReport.path( "attributes" ) ) ;
			reportAttributes.put( "sampleInterval", secondsBetweenCollections.intValue( ) ) ;

			ArrayNode samplesArray = reportAttributes.putArray( "samplesAvailable" ) ;

			for ( Integer sampleInterval : csapApp.rootProjectEnvSettings( )
					.getMetricToSecondsMap( )
					.get( collectorID ) ) {

				samplesArray.add( sampleInterval ) ;

			}

			// add application services
			var serviceNamesByType = csapApp.metricManager( ).getServiceCollector( resourceTimer )
					.buildServicesAvailableReport( ) ;
			serviceNamesByType.set( "os", csapApp.metricManager( ).getOsProcessCollector( resourceTimer )
					.buildServicesAvailableReport( ) ) ;
			reportAttributes.set( "serviceNames", serviceNamesByType ) ;

		} else {

			metricsReport = jacksonMapper.createObjectNode( ) ;
			metricsReport.put( "errors",
					"No data found. Try again in a 2 minutes. If this fails repeatedly, contact operations" ) ;

			logger.warn( CsapApplication.header(
					"{} No data found: requestedMetricChoice: {}, collectorID: {}, resourceTimer: {}, \n\t numberOfDays: {} , numSamples: {}, dayOffset: {}" ),
					serviceNames, requestedMetricChoice, collectorID, resourceTimer, numberOfDays, numSamples,
					dayOffset ) ;

			if ( csapApp.isDesktopHost( ) && ! serviceNames.isEmpty( ) ) {

				try {

					metricsReport = (ObjectNode) jacksonMapper
							.readTree( csapApp.check_for_stub( "", "metrics/" + serviceNames.get( 0 ) + ".json" ) ) ;

				} catch ( Exception e ) {

					logger.info( "No stubbed out data found for desktop tests {}", CSAP.buildCsapStack( e ) ) ;

				}

			}

		}

		// Prune any data
		// pruneHistoricalFeedsIfNeeded(svcNameArray, numberOfDays, statsMap);
		return metricsReport ;

	}

	@GetMapping ( "/testResourceMetricsUpload" )
	public void testResourceUpload (
										@RequestParam ( value = "numSamples" , required = false , defaultValue = "5" ) int numSamples ,
										HttpServletRequest request ,
										HttpServletResponse response )
		throws IOException {

		response.setContentType( "text/plain" ) ;

		OsSharedResourcesCollector statsRun = csapApp.metricManager( ).getOsSharedCollector( -1 ) ;

		response.getWriter( )
				.print( statsRun.uploadMetricsNow( ) ) ;

	}

	@RequestMapping ( "/testDummyMetricsUpload" )
	public void testMetricsUpload (
									@RequestParam ( value = "numSamples" , required = false , defaultValue = "5" ) int numSamples ,
									@RequestParam ( value = "numGraphs" , required = false , defaultValue = "5" ) int numGraphs ,
									@RequestParam ( value = "numServices" , required = false , defaultValue = "5" ) int numServices ,
									HttpServletRequest request ,
									HttpServletResponse response )
		throws IOException {

		// response.setContentType("text/plain");
		response.setContentType( MediaType.APPLICATION_JSON_VALUE ) ;

		ObjectNode metricsNode = jacksonMapper.createObjectNode( ) ;
		addDummyMetricAttributes( numSamples, 0, numServices, numGraphs, metricsNode ) ;

		addDummyMetricData( numSamples, numServices, numGraphs, metricsNode ) ;

		String result = "notSent" ;

		try {

			String restUrl = csapApp.rootProjectEnvSettings( )
					.getMetricsUrl( ) + "/"
					+ csapApp.getCsapHostName( ) ;
			SimpleClientHttpRequestFactory simpleClientRequestFactory = new SimpleClientHttpRequestFactory( ) ;
			simpleClientRequestFactory.setReadTimeout( 5000 ) ;
			simpleClientRequestFactory.setConnectTimeout( 5000 ) ;

			RestTemplate rest = new RestTemplate( simpleClientRequestFactory ) ;

			String jsonDoc = jacksonMapper.writeValueAsString( metricsNode ) ;

			HttpHeaders headers = new HttpHeaders( ) ;
			headers.setContentType( MediaType.APPLICATION_JSON ) ;
			HttpEntity<String> springEntity = new HttpEntity<String>( jsonDoc, headers ) ;

			result = rest.postForObject( restUrl, springEntity, String.class ) ;

			logger.info( "Uploaded Metrics, numSamples: " + numSamples + " numGraphs:" + numGraphs
					+ " numServices:" + numServices + "Response: \n" + result ) ;

		} catch ( Exception e ) {

			logger.error( "Failed upload", e ) ;
			result = "Failed, Exception:" + CSAP.buildCsapStack( e ) ;

		}

		// response.getWriter().print(statsRun.uploadMetrics(numSamples, 1));
		response.getWriter( )
				.print( jacksonMapper.writeValueAsString( metricsNode ) ) ;

	}

	private int futureOffset = 0 ;

	private void addDummyMetricData (
										int numSamples ,
										int numServices ,
										int numGraphs ,
										ObjectNode metricsNode ) {

		futureOffset = futureOffset + numSamples ; // for repeated calls, push
		// the offset so timestamps
		// are not duplicated ;

		ObjectNode dataNode = metricsNode.putObject( "data" ) ;

		ArrayNode timeStampNode = dataNode.putArray( "timeStamp" ) ;

		long currMs = System.currentTimeMillis( ) + ( futureOffset * 1000 ) ;

		for ( int i = 0; i < numSamples; i++ ) {

			// java script needs specialty classes to deal with longs. Must pass
			// in as string
			timeStampNode.add( Long.toString( currMs + i * 1000 ) ) ;

		}

		for ( int i = 0; i < numGraphs; i++ ) {

			String graphName = "DummyGraph" + i ;

			for ( int j = 0; j < numServices; j++ ) {

				ArrayNode serviceNode = dataNode.putArray( graphName + "Service" + j ) ;

				for ( int k = 0; k < numSamples; k++ ) {

					serviceNode.add( k ) ;

				}

			}

		}

	}

	private void addDummyMetricAttributes (
											int requestedSampleSize ,
											int skipFirstItems ,
											int numServices ,
											int numGraphs ,
											ObjectNode rootNode ) {

		ObjectNode descNode = rootNode.putObject( "attributes" ) ;

		descNode.put( "id", "dummy_99" ) ;
		descNode.put( "metricName", "System Resource" ) ;
		descNode.put( "description", "Contains usr,sys,io, and load level metrics" ) ;
		descNode.put( "hostName", csapApp.getCsapHostName( ) ) ;
		descNode.put( "sampleInterval", 99 ) ;
		descNode.put( "samplesRequested", requestedSampleSize ) ;
		descNode.put( "samplesOffset", skipFirstItems ) ;
		descNode.put( "currentTimeMillis", System.currentTimeMillis( ) ) ;
		descNode.put( "cpuCount", osStats.getAvailableProcessors( ) ) ;

		ObjectNode graphsArray = descNode.putObject( "graphs" ) ;

		for ( int i = 0; i < numGraphs; i++ ) {

			String graphName = "DummyGraph" + i ;
			ObjectNode resourceGraph = graphsArray.putObject( graphName ) ;

			for ( int j = 0; j < numServices; j++ ) {

				resourceGraph.put( graphName + "Service" + j, "Service " + j ) ;

			}

		}

	}

	private void auditRecord ( String commandDesc , String details ) {

		csapEventClient.publishUserEvent( CsapEvents.CSAP_OS_CATEGORY + "/" + commandDesc, CsapUser.currentUsersID( ),
				"Os Command", details ) ;

	}

	@RequestMapping ( "/delete" )
	public void delete (
							ModelMap modelMap ,
							@RequestParam ( "location" ) String location ,
							@RequestParam ( "hosts" ) String[] hosts ,
							@RequestParam ( value = "runAsRoot" , required = false ) boolean runAsRoot ,
							@RequestParam ( value = "timeoutSeconds" , required = false , defaultValue = "120" ) int timeoutSeconds ,
							HttpServletRequest request ,
							HttpServletResponse response )
		throws IOException {

		csapEventClient.publishUserEvent( CsapEvents.CSAP_OS_CATEGORY + "/delete",
				securitySettings.getRoles( ).getUserIdFromContext( ), location, "" ) ;

		response.setHeader( "Cache-Control", "no-cache" ) ;
		response.setContentType( MediaType.APPLICATION_JSON_VALUE ) ;

		OutputFileMgr outputFm = new OutputFileMgr( csapApp.getScriptDir( ),
				securitySettings.getRoles( ).getUserIdFromContext( ) + "_delete" ) ;

		File deleteTarget = new File( location ) ;

		// String htmlResult = "";
		ObjectNode resultsNode = jacksonMapper.createObjectNode( ) ;

		resultsNode.put( "scriptHost", csapApp.getCsapHostName( ) ) ;
		ArrayNode hostNode = resultsNode.putArray( "scriptOutput" ) ;

		if ( ! deleteTarget.exists( ) ) {

			hostNode.add( "User: " + Application.SYS_USER + " did not locate: " + deleteTarget.getAbsolutePath( ) ) ;

		}

		SimpleDateFormat df = new SimpleDateFormat( "MMM-d-HH-mm-ss" ) ;

		String user = csapApp.getAgentRunUser( ) ;

		if ( runAsRoot ) {

			user = "root" ;
			hostNode.add( "Performing delete using root user" ) ;

		}

		resultsNode = osManager.executeShellScriptClustered(
				securitySettings.getRoles( ).getUserIdFromContext( ),
				timeoutSeconds, "rm -rvf " + FileRequests.pathWithSpacesEscaped( deleteTarget ), user,
				hosts,
				"delete-" + df.format( new Date( ) ), outputFm ) ;

		outputFm.opCompleted( ) ;
		response.getWriter( )
				.println( resultsNode ) ;
		return ;

	}

	@RequestMapping ( "/syncFiles" )
	public void syncFiles (
							ModelMap modelMap ,
							@RequestParam ( "location" ) String locationToZip ,
							@RequestParam ( "hosts" ) String[] hosts ,
							@RequestParam ( value = "extractDir" , required = true ) String extractDir ,
							@RequestParam ( value = "chownUserid" , required = false ) String chownUserid ,
							@RequestParam ( value = "deleteExisting" , required = false ) String deleteExisting ,
							@RequestParam ( value = "timeoutSeconds" , required = false , defaultValue = "120" ) int timeoutSeconds ,
							HttpServletRequest request ,
							HttpServletResponse response )
		throws IOException {

		response.setHeader( "Cache-Control", "no-cache" ) ;
		response.setContentType( MediaType.APPLICATION_JSON_VALUE ) ;

		ObjectNode resultsNode = jacksonMapper.createObjectNode( ) ;
		List hostList = new ArrayList<String>( Arrays.asList( hosts ) ) ;

		if ( hostList.size( ) < 2 ) {

			resultsNode.put( "Failure", "Add at least one additional host" ) ;
			response.getWriter( )
					.println( resultsNode ) ;
			return ;

		}

		csapEventClient.publishUserEvent( CsapEvents.CSAP_OS_CATEGORY + "/sync",
				securitySettings.getRoles( ).getUserIdFromContext( ), locationToZip,
				"Syncing: " + locationToZip + " extractDir: " + extractDir + " Hosts: "
						+ hostList ) ;

		resultsNode.put( "scriptHost", csapApp.getCsapHostName( ) ) ;
		ArrayNode hostNode = resultsNode.putArray( "scriptOutput" ) ;

		File scriptDir = csapApp.getScriptDir( ) ;

		if ( ! scriptDir.exists( ) ) {

			logger.info( "Making: " + scriptDir.getAbsolutePath( ) ) ;
			scriptDir.mkdirs( ) ;

		}

		OutputFileMgr outputFm = new OutputFileMgr( csapApp.getScriptDir( ),
				securitySettings.getRoles( ).getUserIdFromContext( ) + "_sync" ) ;

		File zipLocation = new File( locationToZip ) ;
		hostNode.add( "\n Source Location: " + zipLocation.getAbsolutePath( ) ) ;

		// List<String> hostList = csapApp.getMutableHostsInActivePackage(
		// clusterName );
		if ( hostList != null && hostList.contains( csapApp.getCsapHostName( ) ) ) {

			logger.debug( "Removing : {}", csapApp.getCsapHostName( ) ) ;
			// always remove current host
			hostList.remove( csapApp.getCsapHostName( ) ) ;

		}

		resultsNode.set(
				"otherHosts",
				osManager.zipAndTransfer(
						securitySettings.getRoles( ).getUserIdFromContext( ),
						timeoutSeconds, hostList,
						locationToZip, extractDir, chownUserid,
						outputFm, deleteExisting ) ) ;

		outputFm.opCompleted( ) ;

		response.getWriter( )
				.println( resultsNode ) ;

		return ;

	}

	@RequestMapping ( "/uploadToFsValidate" )
	public ObjectNode uploadToFsValidate (
											@RequestParam String uploadFilePath ,
											@RequestParam String extractDir ,
											@RequestParam ( value = "skipExtract" , required = false ) boolean skipExtract ,
											@RequestParam ( value = "overwriteTarget" , required = false , defaultValue = "false" ) boolean overwriteTarget ,
											@RequestParam ( value = CsapCore.SERVICE_PORT_PARAM , required = false ) String svcName )
		throws IOException {

		// Default is to place in processing folder. Push uploaded files
		// with absolute paths into root. Handle windows as well
		if ( extractDir.startsWith( "/" ) || extractDir.contains( ":" ) ) {

			extractDir = "__root__" + extractDir ;

		}

		String fileName = new File( uploadFilePath ).getName( ) ;
		// Handle scenario where files are copied from a VM with alternate
		// csap install folder
		File targetExtractionDirectory = csapApp.getRequestedFile( extractDir, svcName, false ) ;

		File extractFullTarget = new File( targetExtractionDirectory, fileName ) ;

		String startTime = LocalDateTime.now( ).format( DateTimeFormatter.ofPattern( "HH:mm:ss MMM d" ) ) ;

		ObjectNode results = jacksonMapper.createObjectNode( ) ;
		OutputFileMgr outputFileManager = new OutputFileMgr( csapApp.getScriptDir( ),
				securitySettings.getRoles( ).getUserIdFromContext( ) + "_upload" ) ;
		outputFileManager.close( ) ;

		File progressFile = outputFileManager.getOutputFile( ) ;
		List<String> lines = Arrays.asList(
				"\n\n *** starting upload at: " + startTime,
				"*** target location: " + extractFullTarget.getAbsolutePath( ),
				"*** time to complete will vary based on network speed and size of file" ) ;

		Files.write( progressFile.toPath( ), lines, Charset.forName( "UTF-8" ) ) ;
		results.put( "progressFile", progressFile.getAbsolutePath( ) ) ;

		// results.put( "error", "Need to Implment" ) ;
		if ( Application.isRunningOnDesktop( ) ) {

			// windows will not allow shell scripts - so
			if ( skipExtract ) {

				extractFullTarget = new File( targetExtractionDirectory, fileName
						+ ".windebug" ) ;

				// extractFullTarget = new File( "/aTemp/peter.windebug" );
				targetExtractionDirectory = extractFullTarget ;

			} else {

				extractFullTarget = new File( targetExtractionDirectory, "windebug" ) ;

			}

		}

		results.put( "targetExtractionDirectory ", targetExtractionDirectory.getAbsolutePath( ) ) ;
		results.put( "extractFullTarget ", extractFullTarget.getAbsolutePath( ) ) ;

		if ( ( targetExtractionDirectory.exists( ) && targetExtractionDirectory.isFile( ) && ! overwriteTarget )
				|| ( extractFullTarget.exists( ) && extractFullTarget.isFile( ) && ! overwriteTarget ) ) {

			results.put( "error", "\n\nSpecified destination already exists:\n"
					+ extractFullTarget.getAbsolutePath( )
					+ "\n\n ===> UseOverwrite checkbox  to proceed\n" ) ;

		}

		return results ;

	}

	@RequestMapping ( "/uploadToFs" )
	public ObjectNode uploadToFs (
									ModelMap modelMap ,
									@RequestParam ( "distFile" ) MultipartFile multiPartFile ,
									@RequestParam ( "hosts" ) String[] hosts ,
									@RequestParam ( "extractDir" ) String extractTargetToken ,
									@RequestParam ( "chownUserid" ) String chownUserid ,
									@RequestParam ( value = "skipExtract" , required = false ) boolean skipExtract ,
									@RequestParam ( value = "deleteExisting" , required = false ) String deleteExisting ,
									@RequestParam ( value = "timeoutSeconds" , required = false , defaultValue = "120" ) int timeoutSeconds ,
									@RequestParam ( value = CsapCore.SERVICE_PORT_PARAM , required = false ) String svcName ,
									@RequestParam ( value = "overwriteTarget" , required = false , defaultValue = "false" ) boolean overwriteTarget ,
									HttpServletRequest request )
		throws IOException {

		List<String> hostList = new ArrayList<>( Arrays.asList( hosts ) ) ;

		logger.info(
				"svcName: {}, extractTargetToken: {}, chownUserid: {}, hostList: {},  skipExtract: {}, overwriteTarget: {} ",
				svcName, extractTargetToken, chownUserid, hostList, skipExtract, overwriteTarget ) ;

		var uploadResultReport = jacksonMapper.createObjectNode( ) ;

		var outputFileManager = new OutputFileMgr( csapApp.getScriptDir( ),
				securitySettings.getRoles( ).getUserIdFromContext( ) + "_upload" ) ;

		uploadResultReport.put( "scriptHost", csapApp.getCsapHostName( ) ) ;
		var scriptOutputArray = uploadResultReport.putArray( "scriptOutput" ) ;

		uploadResultReport.putArray( "otherHosts" ) ;
		// results. ( "otherHosts", jacksonMapper.createArrayNode() );

		String remoteServerName = request.getRemoteHost( ) ;

		if ( multiPartFile != null && multiPartFile.getSize( ) != 0 ) {

			// Default is to place in processing folder. Push uploaded files
			// with absolute paths into root. Handle windows as well
			if ( extractTargetToken.startsWith( "/" ) || extractTargetToken.contains( ":" ) ) {

				extractTargetToken = "__root__" + extractTargetToken ;

			}

			// Handle scenario where files are copied from a VM with alternate
			// csap install folder
			File targetExtractionDirectory = csapApp.getRequestedFile( extractTargetToken, svcName, false ) ;

			File extractFullTarget = new File( targetExtractionDirectory, multiPartFile.getOriginalFilename( ) ) ;

			if ( Application.isRunningOnDesktop( ) ) {

				// windows will not allow shell scripts - so
				if ( skipExtract ) {

					extractFullTarget = new File( targetExtractionDirectory, multiPartFile.getOriginalFilename( )
							+ ".windebug" ) ;

					// extractFullTarget = new File( "/aTemp/peter.windebug" );
					targetExtractionDirectory = extractFullTarget ;

				} else {

					extractFullTarget = new File( targetExtractionDirectory, "windebug" ) ;

				}

			}

			logger.debug( "File Upload target: {}", extractFullTarget.getAbsolutePath( ) ) ;

			if ( ( targetExtractionDirectory.exists( ) && targetExtractionDirectory.isFile( ) && ! overwriteTarget )
					|| ( extractFullTarget.exists( ) && extractFullTarget.isFile( ) && ! overwriteTarget ) ) {

				scriptOutputArray.add( "\n\n Specified destination already exists:\n"
						+ extractFullTarget.getAbsolutePath( )
						+ "\n\n ===> UseOverwrite checkbox  to proceed\n" ) ;

			} else {

				String platformUpdateResults = osManager.updatePlatformCore(
						multiPartFile, targetExtractionDirectory.getAbsolutePath( ),
						skipExtract, remoteServerName, chownUserid,
						securitySettings.getRoles( ).getUserIdFromContext( ), deleteExisting, outputFileManager ) ;

				scriptOutputArray.add( platformUpdateResults ) ;

				if ( hostList != null ) {

					hostList.remove( csapApp.getCsapHostName( ) ) ;

					if ( hostList.size( ) != 0 ) {

						if ( skipExtract ) {

							TransferManager transferManager = new TransferManager( csapApp, timeoutSeconds,
									outputFileManager.getBufferedWriter( ) ) ;

							if ( extractFullTarget.exists( ) ) {

								transferManager
										.httpCopyViaCsAgent(
												securitySettings.getRoles( ).getUserIdFromContext( ),
												extractFullTarget, targetExtractionDirectory.getAbsolutePath( ),
												hostList,
												chownUserid ) ;

								uploadResultReport.replace( "otherHosts",
										transferManager.waitForCompleteJson( ) ) ;

								// result.append(transferManager.waitForComplete(
								// "<pre class=\"result\">", "</pre>"));
							} else {

								scriptOutputArray.add( "\n ===> Did not find file to transfer: "
										+ extractFullTarget.getAbsolutePath( ) ) ;

							}

						} else {

							scriptOutputArray
									.add( "\n ===> Requested sync to other hosts, and extract. Only either option may be used." ) ;

						}

					}

				} else {

					// result.append("\n == no sync hosts specified");
				}

			}

		} else {

			uploadResultReport.put( "error", "Unable to process request: multiPartFile: " + multiPartFile ) ;
			logger.error( "Unable to process request due to null file" ) ;

		}

		// hook for displaying results
		ArrayNode otherHosts = (ArrayNode) uploadResultReport.get( "otherHosts" ) ;
		ObjectNode hostResponse = otherHosts.addObject( ) ;
		hostResponse.put( "host", csapApp.getCsapHostName( ) ) ;

		logger.info( "completed upload" ) ;
		outputFileManager.opCompleted( ) ;

		return uploadResultReport ;

	}

}
