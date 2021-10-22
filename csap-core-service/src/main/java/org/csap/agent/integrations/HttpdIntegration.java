package org.csap.agent.integrations ;

import java.io.BufferedWriter ;
import java.io.File ;
import java.io.FileWriter ;
import java.io.IOException ;
import java.nio.file.Files ;
import java.nio.file.Paths ;
import java.nio.file.StandardCopyOption ;
import java.util.ArrayList ;
import java.util.Arrays ;
import java.util.HashMap ;
import java.util.List ;
import java.util.Map ;
import java.util.TreeMap ;

import org.csap.agent.CsapCore ;
import org.csap.agent.linux.OsCommandRunner ;
import org.csap.agent.model.Application ;
import org.csap.agent.model.Project ;
import org.csap.agent.model.ProjectLoader ;
import org.csap.agent.model.ServiceAttributes ;
import org.csap.agent.model.ServiceInstance ;
import org.csap.helpers.CSAP ;
import org.csap.integations.CsapWebSettings.SslSettings ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.core.io.DefaultResourceLoader ;

import com.fasterxml.jackson.databind.JsonNode ;
import com.fasterxml.jackson.databind.ObjectMapper ;
import com.fasterxml.jackson.databind.node.ArrayNode ;
import com.fasterxml.jackson.databind.node.ObjectNode ;

/**
 *
 * Utility class for generating httpd configuration from CSAP Model
 *
 * @author someDeveloper
 * @see <a href="doc-files/csagent.jpg"> Click to enlarge
 *      <IMG width=300 SRC="doc-files/csagent.jpg"></a>
 *
 * @see <a href="doc-files/spring.jpg" > Click to enlarge
 *      <IMG width=300 SRC="doc-files/modelDocs.jpg"></a>
 */
public class HttpdIntegration {

	final Logger logger = LoggerFactory.getLogger( this.getClass( ) ) ;

	private static int NUM_WORKERS_PER_LINE = 5 ;
	public static String HTTP_WORKER_EXPORT_FILE ;
	public static String HTTP_WORKER_FILE ;
	public static String HTTP_MODJK_EXPORT_FILE ;
	public static String HTTP_MODJK_FILE ;
	public static String EXPORT_TRIGGER_FILE ;
	public static String EXPORT_WEB_TAG = "exportWeb" ;
	public static String PROXY_FILE ;
	public static String REWRITE_FILE ;
	public static String SSL_CERT__FILE ;
	public static String SSL_KEY__FILE ;
	public static String SSL_P12__FILE ;
	public static String SSL_PEM__FILE ;

	public static final String GENERATE_WORKER_PROPERTIES = "generateWorkerProperties" ;
	public static String SKIP_INTERNAL_AJP_TAG = "skipInternalAjp" ;
	public static String SKIP_INTERNAL_HTTP_TAG = "skipInternalHttp" ;

	OsCommandRunner osCommandRunner = new OsCommandRunner( 90, 3, "HttpdIntegration" ) ; // apachectl

	Application csapApp = null ;

	public HttpdIntegration ( Application csapApplication ) {

		this.csapApp = csapApplication ;

	}

	private String csap_web_folder ;

	public void updateConstants ( ) {

		csap_web_folder = csapApp.getInstallationFolderAsString( ) + "/httpdConf/" ;
		REWRITE_FILE = csap_web_folder + "csspRewrite.conf" ;
		HTTP_WORKER_FILE = csap_web_folder + "worker.properties" ;
		HTTP_WORKER_EXPORT_FILE = csapApp.getInstallationFolderAsString( ) + "/httpdConf/csspWorkerExport.properties" ;
		HTTP_MODJK_FILE = csap_web_folder + "csspJkMount.conf" ;
		EXPORT_TRIGGER_FILE = csap_web_folder + "exportTrigger.txt" ;
		HTTP_MODJK_EXPORT_FILE = csap_web_folder + "csspJkMountExport.conf" ;
		PROXY_FILE = csap_web_folder + "proxy.conf" ;
		SSL_CERT__FILE = csap_web_folder + "csap.crt" ;
		SSL_KEY__FILE = csap_web_folder + "csap.key" ;
		SSL_P12__FILE = csap_web_folder + "csap.p12" ;
		SSL_PEM__FILE = csap_web_folder + "csap.pem" ;

		logger.debug( "Http Configuration Folder: {}", csap_web_folder ) ;

	}

	private void ensure_csap_web_configuration_folder_exists ( ) {

		File webLocation = new File( csap_web_folder ) ;

		if ( ! webLocation.exists( ) ) {

			logger.info( "Creating csap web configuration folder: '{}' ", webLocation.getAbsolutePath( ) ) ;

			if ( ! webLocation.mkdirs( ) ) {

				logger.error( "Failed creating " + webLocation ) ;

			}

		}

	}

	/**
	 * Generates the apache modjk and worker.properties files.
	 *
	 * For testing in eclipse, update the eclipse/getIntancesResults.txt file
	 *
	 * @return
	 */

	public File getHttpdWorkersFile ( ) {

		return new File( HTTP_WORKER_FILE ) ;

	}

	public String reload_csap_web_integration ( ) {

		var hostInstances = csapApp.getServicesOnHost( ) ;

		// Not efficient, but infrequent so iterate
		ServiceInstance httpdInstance = null ;

		for ( ServiceInstance svcInstance : hostInstances ) {

			if ( svcInstance.isGenerateWebMappings( ) ) {

				httpdInstance = svcInstance ;
				break ;

			}

		}

		if ( httpdInstance == null ) {

			return "Httpd not configured on host: updated service definitions with service metadata: "
					+ GENERATE_WORKER_PROPERTIES ;

		}

		ensure_csap_web_configuration_folder_exists( ) ;

		logger.debug( "\n ============= Updating Loadbalanceing configs ========\n", csap_web_folder ) ;

		// csapApp.getEventClient().generateEvent(
		// CsapEventClient.CSAP_SYSTEM_CATEGORY + "/httpd/update",
		// Application.SYS_USER, "configuration files modified",
		// "Httpd Updating: " + HTTP_MODJK_FILE + ", " + HTTP_WORKER_FILE );

		StringBuffer workerSettingsBuffer = new StringBuffer( "" ) ;
		StringBuffer workerSettingsExportBuffer = new StringBuffer( "" ) ;
		// updateHostInfo(true, hostFilter);
		// for (String host : getEnvHosts( Application.getEnv() )) {

		StringBuffer jkMountBuffer = new StringBuffer( "\n\n# Mod_jk httpd.conf for lifecycle: "
				+ csapApp.getCsapHostEnvironmentName( ) + "\n\n" ) ;

		StringBuffer jkMountExportBuffer = new StringBuffer(
				"\n\n# Mod_jk Secure Exports (Usually for export to OAM Server): "
						+ csapApp.getCsapHostEnvironmentName( ) + "\n\n" ) ;

		StringBuffer workerListBuffer = new StringBuffer( "\n\n# Mod_jk worker.properties:\n\n" ) ;
		workerListBuffer
				.append( "# ref. http://tomcat.apache.org/connectors-doc/generic_howto/loadbalancers.html\n\n" ) ;

		StringBuffer workerListExportBuffer = new StringBuffer(
				"\n\n# Mod_jk worker.properties Secure Exports (Usually for export to OAM Server):\n\n" ) ;
		workerListExportBuffer
				.append( "# ref. http://tomcat.apache.org/connectors-doc/generic_howto/loadbalancers.html\n\n" ) ;

		if ( csapApp.serviceNameToAllInstances( ).keySet( ).size( ) == 0 ) {

			// lets reload - only happens in eclipse
			csapApp.run_application_scan( ) ;

		}

		var progress = new StringBuilder( ) ;

		var sslSettings = csapApp.getCsapCoreService( ).getCsapWebServer( ).getSettings( ).getSsl( ) ;

		if ( sslSettings.isEnabled( ) ) {

			generateSslFiles( progress, sslSettings ) ;

		}

		generateHttpProxy( progress ) ;

		// 3 clustering models supported

		buildHttpdConfigForStandardClusters( workerSettingsBuffer, workerSettingsExportBuffer, jkMountBuffer,
				jkMountExportBuffer, workerListBuffer, workerListExportBuffer ) ;

		// generateHttpdConfigForMultipleVmPartition( workerSettingsBuffer,
		// workerSettingsExportBuffer,
		// jkMountBuffer,
		// jkMountExportBuffer, workerListBuffer, workerListExportBuffer );

		buildHttpdConfigForSingleVmPartition( workerSettingsBuffer, jkMountBuffer, workerListBuffer,
				SKIP_INTERNAL_AJP_TAG ) ;

		buildHttpdConfigForSingleVmPartition( workerSettingsExportBuffer, jkMountExportBuffer,
				workerListExportBuffer, EXPORT_WEB_TAG ) ;

		// need to strip the newline
		workerListBuffer.deleteCharAt( workerListBuffer.length( ) - 2 ) ;

		// modjk status hooks
		workerListBuffer.append( ", mystatus" ) ;
		workerSettingsBuffer.append( "\nworker.mystatus.type=status\n" ) ;
		workerSettingsBuffer.append( "\nworker.mystatus.css=/css/modjk.css\n" ) ;
		// Use CsAgent to protect the url
		// workerSettingsBuffer.append("\nworker.mystatus.read_only=true\n");
		workerListBuffer.append( "\n\n" ) ;
		jkMountBuffer.append( "\nJkMount /status* mystatus\n" ) ;

		workerListExportBuffer.append( ", mystatus" ) ;
		workerSettingsExportBuffer.append( "\nworker.mystatus.type=status\n" ) ;
		workerSettingsExportBuffer.append( "\nworker.mystatus.css=/css/modjk.css\n" ) ;
		// Use CsAgent to protect the url
		// workerSettingsExportBuffer.append("\nworker.mystatus.read_only=true\n");
		workerListExportBuffer.append( "\n\n" ) ;
		jkMountExportBuffer.append( "\nJkMount /status* mystatus\n" ) ;

		generateModJKFile( jkMountBuffer, jkMountExportBuffer, progress ) ;

		generateWorkerFile( workerListBuffer, workerListExportBuffer, workerSettingsBuffer,
				workerSettingsExportBuffer ) ;
		generateRewriteFile( progress ) ;

		if ( csapApp.rootProjectEnvSettings( ).isAutoRestartHttpdOnClusterReload( ) ) {

			if ( csapApp.is_service_running( httpdInstance.getName( ) ) ) {

				logger.debug( "Doing a graceful restart on apache" ) ;
				List<String> parmList ;
				parmList = Arrays.asList( "bash", "-c", "apachectl graceful" ) ;

				progress.append( osCommandRunner.executeString( parmList, csapApp.getCsapWorkingFolder( ) ) ) ;

				csapApp.getEventClient( ).publishUserEvent( CsapEvents.CSAP_SYSTEM_CATEGORY + "/httpd/restart",
						Application.SYS_USER,
						"Graceful restart triggered", progress.toString( ) ) ;

			} else {

				progress.append( "\n" + httpdInstance.getName( )
						+ " is currently not running. Restart is not issued." ) ;
				csapApp.getEventClient( )
						.publishUserEvent( CsapEvents.CSAP_SYSTEM_CATEGORY + "/httpd/restart",
								Application.SYS_USER,
								httpdInstance.getName( ) + " is currently not running",
								"Verify that service is running. If host has been booted - it will be autostarted." ) ;

			}

		} else {

			progress
					.append( "\n Auto restarts are disabled in lifecycle settings; manual restart is required to route to any new services" ) ;
			csapApp.getEventClient( )
					.publishUserEvent( CsapEvents.CSAP_SYSTEM_CATEGORY + "/httpd/restart",
							Application.SYS_USER,
							"Auto restart disabled in definition",
							"Operator should manually restart httpds 1 at a time if any new services have been added." ) ;

		}

		// Finally
		logger.debug( "Updating status file - used to ripple export of modjk" ) ;

		// header.append("JkRequestLogFormat \"%w %V %T\"\n");
		try {

			File statusFile = new File( EXPORT_TRIGGER_FILE ) ;
			progress.append( CSAP.padLine( "Timestamp updated" ) + statusFile.getAbsolutePath( ) ) ;

			FileWriter fstream = new FileWriter( statusFile ) ;
			BufferedWriter out = new BufferedWriter( fstream ) ;
			out.write( Long.toString( System.currentTimeMillis( ) ) ) ;
			out.close( ) ;

		} catch ( IOException e ) {

			logger.error( "Failed to write file, {}", CSAP.buildCsapStack( e ) ) ;

		}

		logger.warn( "Apache Configuration {}",
				progress.toString( ) ) ;

		return jkMountBuffer.toString( ) + workerListBuffer.toString( )
				+ workerSettingsBuffer.toString( ) ;

	}

	private void buildHttpdConfigForStandardClusters (
														StringBuffer workerSettingsBuffer ,
														StringBuffer workerSettingsSecureBuffer ,
														StringBuffer jkMountBuffer ,
														StringBuffer jkMountSecureBuffer ,
														StringBuffer workerListBuffer ,
														StringBuffer workerListSecureBuffer ) {

		int workerListCount = 0 ;
		int workerListSecureCount = 0 ;

		// @formatter:off
		csapApp.getRootProject( )
				.getProjects( )
				.forEach( model -> {

					model.getServiceNameStream( )
							.forEach( serviceName -> {

								buildHttpdConfigForStandardClustersModel(
										model, serviceName,
										workerSettingsBuffer, workerSettingsSecureBuffer, jkMountBuffer,
										jkMountSecureBuffer, workerListBuffer, workerListSecureBuffer, workerListCount,
										workerListSecureCount ) ;

							} ) ;

				} ) ;

		// @formatter:on
	}

	private ObjectMapper jacksonMapper = new ObjectMapper( ) ;

	private void buildHttpdConfigForStandardClustersModel (
															Project model ,
															String serviceName ,
															StringBuffer workerSettingsBuffer ,
															StringBuffer workerSettingsSecureBuffer ,
															StringBuffer jkMountBuffer ,
															StringBuffer jkMountSecureBuffer ,
															StringBuffer workerListBuffer ,
															StringBuffer workerListSecureBuffer ,
															int workerListCount ,
															int workerListSecureCount ) {

		// logger.debug("Service Name: " + serviceName);
		// sbuf.append("\n\t" + service + "\n\t\t");
		if ( serviceName.equalsIgnoreCase( "httpd" ) ) {

			return ;

		}

		String workerName = serviceName + "_" ;
		String workerId = ( "worker." + workerName ) ;
		String workerLB = workerId + "LB" ;
		String workerLBHeader = workerName + "LB" ;
		// results.append("\n\t" + serviceName + "\n\t\t");
		// ArrayList<File> svcList =
		// svcNameToSvcPathList.get(serviceName);
		StringBuffer instances = new StringBuffer( ) ;
		workerSettingsBuffer.append( "\n ##### Service: " + serviceName + "\n\n" ) ;
		workerSettingsSecureBuffer.append( "\n ##### Service: " + serviceName + "\n\n" ) ;
		ArrayList<ServiceInstance> svcList = model.getServiceToAllInstancesMap( ).get( serviceName ) ;
		String context = serviceName ;

		// httpd filtering as not everything is exported
		boolean isIncludedInInternal = false ;
		boolean isIncludedInExport = false ;

		String cookieName = "" ;
		ObjectNode apacheModjkIntegration = jacksonMapper.createObjectNode( ) ;

		for ( ServiceInstance svcInstance : svcList ) {

			StringBuffer tempWorkerSettingsBuffer = new StringBuffer( ) ;

			if ( csapApp.rootProjectEnvSettings( ).isLoadBalanceVmFilter( svcInstance.getHostName( ) ) ) {

				continue ;

			}

			if ( ( ! checkInstanceInCurrentLifecycle( svcInstance )
					|| ( ! svcInstance.is_cluster_modjk( ) )
					|| svcInstance.is_os_process_monitor( )
					|| svcInstance.is_csap_api_server( )
					|| svcInstance.is_docker_server( )
					|| ( svcInstance.is_springboot_server( ) && ! svcInstance.isTomcatAjp( ) ) ) ) {

				continue ;

			}

			// Need to ignore CsAgent on sandboxes in dev.
			if ( serviceName.equalsIgnoreCase( CsapCore.AGENT_NAME ) ) {

				// InstanceConfig testInstance =
				// model.getHostToConfigMap()
				// .get(svcInstance.getHostName()).get(0);
				// if
				// (testInstance.getLifecycle().toLowerCase().indexOf("sandbox")
				// != -1
				// || !testInstance.isConfigureAsFactory() )
				continue ;

			}

			// factory instances are hooked in special below
			String host = svcInstance.getHostName( ) ;
			String svcPort = svcInstance.getPort( ) ;
			String ajpPort = svcPort ;

			try {

				ajpPort = ajpPort.substring( 0, 3 ) + "2" ;

			} catch ( Exception e ) {

				logger.error( "Failed to parse port: " + ajpPort ) ;

			}

			String instanceId = workerId + svcPort + host ;
			cookieName = svcInstance.getCookieName( ) ;
			ObjectNode serviceWeb = svcInstance.getAttributeAsObject( ServiceAttributes.webServerTomcat ) ;

			if ( serviceWeb != null ) {

				apacheModjkIntegration = serviceWeb ;

			}

			instances.append( serviceName + "_" + svcPort + host + "," ) ;
			tempWorkerSettingsBuffer.append( instanceId + ".port=" + ajpPort + "\n" ) ;
			tempWorkerSettingsBuffer.append( instanceId + ".host=" + host + "\n" ) ;
			tempWorkerSettingsBuffer.append( instanceId + ".type=ajp13\n" ) ;

			// refer to CsapWebServer.DEFAULT_AJP_VARIABLE_IN_YAML, this is passed via
			// ServiceOsManager
			tempWorkerSettingsBuffer.append( instanceId
					+ ".secret=" + csapApp.getTomcatAjpKey( ) + "\n" ) ;

			// adding in lifecycle to prevent cross-infra calls
			tempWorkerSettingsBuffer.append( instanceId + ".lbfactor=1\n" ) ;
			tempWorkerSettingsBuffer.append( instanceId + ".socket_keepalive=True\n" ) ;
			tempWorkerSettingsBuffer.append( instanceId + ".connection_pool_timeout=10\n" ) ;
			tempWorkerSettingsBuffer.append( instanceId + ".connection_pool_minsize=0\n" ) ;

			if ( apacheModjkIntegration.has( "connection" ) && apacheModjkIntegration.get( "connection" ).isArray( ) ) {

				ArrayNode connectRules = (ArrayNode) apacheModjkIntegration.get( "connection" ) ;
				// http://tomcat.apache.org/connectors-doc/reference/workers.html
				tempWorkerSettingsBuffer
						.append( "# Custom Settings Used, refer to http://tomcat.apache.org/connectors-doc/reference/workers.html \n" ) ;

				for ( JsonNode configItem : connectRules ) {

					tempWorkerSettingsBuffer.append( instanceId + "." + configItem.asText( ) + "\n" ) ;

				}

			}

			tempWorkerSettingsBuffer.append( "\n\n" ) ;

			if ( ! svcInstance.getMetaData( ).contains( SKIP_INTERNAL_AJP_TAG ) ) {

				workerSettingsBuffer.append( tempWorkerSettingsBuffer ) ;
				isIncludedInInternal = true ;

			}

			if ( svcInstance.getMetaData( ).contains( EXPORT_WEB_TAG ) ) {

				workerSettingsSecureBuffer.append( tempWorkerSettingsBuffer ) ;
				isIncludedInExport = true ;

			}

			context = svcInstance.getContext( ) ;

		}

		// only wire in services for which there is at least one
		// instance
		// configured
		if ( instances.length( ) > 0 ) {

			// Get rid of trailing ,
			instances.deleteCharAt( instances.length( ) - 1 ) ;

			if ( isIncludedInInternal ) {

				jkMountBuffer.append( "JkMount /" + context + "* " + workerLBHeader + "\n" ) ;

				if ( workerListCount++ % NUM_WORKERS_PER_LINE == 0 ) {

					workerListBuffer.append( "\nworker.list=" ) ;

				}

				workerListBuffer.append( workerLBHeader + ", " ) ;

				workerSettingsBuffer.append( workerLB + ".type=lb\n" ) ;
				workerSettingsBuffer.append( workerLB + ".balance_workers=" + instances
						+ "\n" ) ;

				if ( cookieName.length( ) > 0 ) {

					workerSettingsBuffer.append( workerLB + ".session_cookie=" + cookieName
							+ "\n" ) ;

				}
				// logger.info("{} apacheModjkIntegration: {}", serviceName,
				// apacheModjkIntegration.toString() ) ;

				if ( apacheModjkIntegration.has( "loadBalance" ) && apacheModjkIntegration.get( "loadBalance" )
						.isArray( ) ) {

					ArrayNode lbRules = (ArrayNode) apacheModjkIntegration.get( "loadBalance" ) ;
					// http://tomcat.apache.org/connectors-doc/reference/workers.html
					workerSettingsBuffer
							.append( "# Custom Settings Used, refer to http://tomcat.apache.org/connectors-doc/reference/workers.html \n" ) ;

					for ( JsonNode configItem : lbRules ) {

						workerSettingsBuffer.append( workerLB + "." + configItem.asText( ) + "\n" ) ;

					}

					workerSettingsBuffer.append( "\n\n" ) ;

				} else {

					workerSettingsBuffer.append( workerLB + ".sticky_session=1\n\n" ) ;

				}

			}

			if ( isIncludedInExport ) {

				jkMountSecureBuffer.append( "JkMount /" + context + "* " + workerLBHeader
						+ "\n" ) ;

				if ( workerListSecureCount++ % NUM_WORKERS_PER_LINE == 0 ) {

					workerListSecureBuffer.append( "\nworker.list=" ) ;

				}

				workerListSecureBuffer.append( workerLBHeader + ", " ) ;

				workerSettingsSecureBuffer.append( workerLB + ".type=lb\n" ) ;
				// instances.deleteCharAt(instances.length() - 1);
				workerSettingsSecureBuffer.append( workerLB + ".balance_workers="
						+ instances + "\n" ) ;

				if ( apacheModjkIntegration.size( ) > 0 ) {

					for ( JsonNode configItem : apacheModjkIntegration ) {

						workerSettingsSecureBuffer.append( workerLB + "." + configItem.asText( ) + "\n" ) ;

					}

				} else {

					workerSettingsSecureBuffer.append( workerLB + ".sticky_session=1\n\n" ) ;

				}

			} else {

				workerSettingsSecureBuffer
						.append( "### Skipping service - add the secure flag to metadata in clusterConfig to include\n\n" ) ;

			}

		} else {

			workerSettingsBuffer
					.append( "### Service not configured\n\n" ) ;
			workerSettingsSecureBuffer
					.append( "### Service not configured\n\n" ) ;

		}

	}

	private boolean checkInstanceInCurrentLifecycle ( ServiceInstance svcInstance ) {

		if ( svcInstance.getLifecycle( ).startsWith( csapApp.getCsapHostEnvironmentName( )
				+ ProjectLoader.ENVIRONMENT_CLUSTER_DELIMITER ) ) {

			return true ;

		} else {

			return false ;

		}

		// return getCurrentLifeCycle().startsWith(svcInstance.getLifecycle());
		// return svcInstance.getLifecycle().equalsIgnoreCase(
		// getCurrentLifeCycle());
	}

	/*
	 * 
	 * Runs in one of 2 modes: SKIP_INTERNAL_AJP_TAG EXPORT_WEB_TAG
	 */
	private void buildHttpdConfigForSingleVmPartition (
														StringBuffer workerConfigBuffer ,
														StringBuffer modjkMountsBuffer ,
														StringBuffer workerListBuffer ,
														String filter ) {

		//
		csapApp.getRootProject( )
				.getProjects( )
				.forEach(
						model -> {

							model.getHostsInActiveLifecycleStream( )
									.forEach(
											hostName -> {

												buildHttpdConfigSingleVmModel(
														hostName,
														workerConfigBuffer,
														modjkMountsBuffer,
														workerListBuffer, filter, model ) ;

											} ) ;

						} ) ;

	}

	private void buildHttpdConfigSingleVmModel (
													String lifecycleHost ,
													StringBuffer workerConfigBuffer ,
													StringBuffer modjkMountsBuffer ,
													StringBuffer workerListBuffer ,
													String filter ,
													Project model ) {

		int workerListCount = 0 ;

		Map<String, String> svcToLBMap = new HashMap<String, String>( ) ;
		Map<String, String> svcToCookieNameMap = new HashMap<String, String>( ) ;
		String workerName ;
		String workerId = "" ;
		String workerLB = "" ;
		String workerLBHeader = "" ;
		// results.append("\n\t" + serviceName + "\n\t\t");
		// ArrayList<File> svcList =
		// svcNameToSvcPathList.get(serviceName);
		// workerSettings.append("\n ##### Service: " + serviceName +
		// "\n\n");
		var services = model.getServicesListOnHost( lifecycleHost ) ;

		for ( ServiceInstance svcInstance : services ) {

			if ( ( ! svcInstance.is_cluster_single_host_modjk( ) )
					|| svcInstance.is_os_process_monitor( )
					|| svcInstance.is_csap_api_server( )
					|| svcInstance.is_docker_server( )
					|| ( svcInstance.is_springboot_server( ) && ! svcInstance.isTomcatAjp( ) )
					|| svcInstance.getName( ).startsWith( CsapCore.AGENT_NAME ) ) {

				continue ;

			}

			if ( filter.equals( SKIP_INTERNAL_AJP_TAG )
					&& svcInstance.getMetaData( ).contains( filter ) ) {

				continue ;

			}

			if ( filter.equals( EXPORT_WEB_TAG )
					&& ! svcInstance.getMetaData( ).contains( filter ) ) {

				continue ;

			}

			String svcName = svcInstance.getContext( ) ;
			// String svcName = svcInstance.getServiceName();
			// String svcContext = svcInstance.getContext();
			String host = svcInstance.getHostName( ) ;
			String svcPort = svcInstance.getPort( ) ;
			String ajpPort = svcPort ;

			if ( ! svcToLBMap.containsKey( svcName ) ) {

				workerName = svcName + "_" ;
				workerId = ( "worker." + workerName ) ;
				workerLB = workerId + "LB" ;
				workerLBHeader = workerName + "LB" ;

				if ( workerListCount++ % NUM_WORKERS_PER_LINE == 0 ) {

					workerListBuffer.append( "\nworker.list=" ) ;

				}

				workerListBuffer.append( workerLBHeader + ", " ) ;

				svcToLBMap.put( svcName, workerLB + ".balance_workers=" ) ;
				modjkMountsBuffer.append( "JkMount /" + svcInstance.getContext( ) + "* "
						+ workerLBHeader + "\n" ) ;

			}

			try {

				ajpPort = ajpPort.substring( 0, 3 ) + "2" ;

			} catch ( Exception e ) {

				logger.error( "Failed to parse port: " + ajpPort ) ;

			}

			String instanceId = workerLB + svcPort ;
			workerConfigBuffer.append( instanceId + ".port=" + ajpPort + "\n" ) ;
			workerConfigBuffer.append( instanceId + ".host=" + host + "\n" ) ;
			workerConfigBuffer.append( instanceId + ".type=ajp13\n" ) ;
			workerConfigBuffer.append( instanceId + ".secret=" + csapApp.getTomcatAjpKey( )
					+ "\n" ) ;
			workerConfigBuffer.append( instanceId + ".lbfactor=1\n" ) ;
			workerConfigBuffer.append( instanceId + ".socket_keepalive=True\n" ) ;
			workerConfigBuffer.append( instanceId + ".connection_pool_timeout=60\n\n" ) ;

			// may have multiple factory instances, append to previous
			// entry
			String newLBline = svcToLBMap.get( svcName ) + workerLBHeader + svcPort + "," ;
			svcToLBMap.put( svcName, newLBline ) ;
			svcToCookieNameMap.put( svcName, svcInstance.getCookieName( ) ) ;

		}

		// only wire in services for which there is at least one
		// instance
		// configured
		for ( String svcKey : svcToLBMap.keySet( ) ) {

			logger.debug( " lbSvcString: " + svcKey ) ;
			String lbString = svcToLBMap.get( svcKey ) ;
			String prefix = lbString.substring( 0, lbString.indexOf( ".balance" ) ) ;
			workerConfigBuffer.append( prefix + ".type=lb\n" ) ;

			workerConfigBuffer
					.append( lbString.subSequence( 0, lbString.length( ) - 1 ) + "\n" ) ;

			// Factories hack: Smart Dispatcher relies on
			// modjk/modrewrite
			// "customerIds" getting inserted before the
			// context name. in order for this to work, all cookies on
			// all
			// services in factorys
			// must use the "/" target, which then requires them to have
			// a
			// unique name
			workerConfigBuffer.append( prefix + ".session_cookie="
					+ svcToCookieNameMap.get( svcKey ) + "\n" ) ;
			workerConfigBuffer.append( prefix + ".sticky_session=1\n\n" ) ;

		}

	}

	private void generateHttpdConfigForMultipleVmPartition (
																StringBuffer workerSettingsBuffer ,
																StringBuffer workerSettingsSecureBuffer ,
																StringBuffer jkMountBuffer ,
																StringBuffer jkMountSecureBuffer ,
																StringBuffer workerListBuffer ,
																StringBuffer workerListSecureBuffer ) {

		csapApp.getRootProject( )
				.getProjects( )
				.forEach(
						model -> {

							for ( int partionCount = 0; partionCount < 10; partionCount++ ) {

								final int id = partionCount ;
								model.getServiceNameStream( )
										.forEach(
												serviceName -> {

													generateHttpdConfigForMultiVmModel( serviceName, model,
															id, workerSettingsBuffer,
															workerSettingsSecureBuffer, jkMountBuffer,
															jkMountSecureBuffer, workerListBuffer,
															workerListSecureBuffer ) ;

												} ) ;

							}

						} ) ;

	}

	private void generateHttpdConfigForMultiVmModel (
														String serviceName ,
														Project model ,
														int partionCount ,
														StringBuffer workerSettingsBuffer ,
														StringBuffer workerSettingsSecureBuffer ,
														StringBuffer jkMountBuffer ,
														StringBuffer jkMountSecureBuffer ,
														StringBuffer workerListBuffer ,
														StringBuffer workerListSecureBuffer ) {

		int workerListCount = 0 ;
		int workerListSecureCount = 0 ;

		if ( serviceName.equalsIgnoreCase( "httpd" ) ) {

			return ;

		}

		String workerName = serviceName + "-" + partionCount + "_" ;
		String workerId = ( "worker." + workerName ) ;
		String workerLB = workerId + "LB" ;
		String workerLBHeader = workerName + "LB" ;

		StringBuffer instances = new StringBuffer( ) ;

		ArrayList<ServiceInstance> svcList = model.getServiceToAllInstancesMap( ).get( serviceName ) ;
		String context = serviceName ;

		boolean isIncludedInInternal = false ;
		boolean isIncludedInExport = false ;

		String cookieName = "" ;

		for ( ServiceInstance svcInstance : svcList ) {

			// The hook for enterprise partitions
			if ( ! svcInstance.getPlatformVersion( )
					.equals( Integer.toString( partionCount ) ) ) {

				continue ;

			}

			StringBuffer tempWorkerSettingsBuffer = new StringBuffer( ) ;

			if ( ( ! checkInstanceInCurrentLifecycle( svcInstance )
					|| ! svcInstance.is_cluster_multi_host_modjk( )
					|| svcInstance.is_os_process_monitor( )
					|| svcInstance.is_csap_api_server( )
					|| svcInstance.is_docker_server( )
					|| ( svcInstance.is_springboot_server( ) && ! svcInstance.isTomcatAjp( ) )
					|| ( svcInstance.getLifecycle( )
							.toLowerCase( ).indexOf( "sandbox" ) != -1 ) ) ) {

				continue ;

			}

			// Need to ignore CsAgent on sandboxes in dev.
			if ( serviceName.equalsIgnoreCase( CsapCore.AGENT_NAME ) ) {

				// InstanceConfig testInstance =
				// model.getHostToConfigMap()
				// .get(svcInstance.getHostName()).get(0);
				// if
				// (testInstance.getLifecycle().toLowerCase().indexOf("sandbox")
				// != -1
				// || !testInstance.isConfigureAsFactory() )
				continue ;

			}

			// factory instances are hooked in special below
			String host = svcInstance.getHostName( ) ;
			String svcPort = svcInstance.getPort( ) ;
			String ajpPort = svcPort ;

			try {

				ajpPort = ajpPort.substring( 0, 3 ) + "2" ;

			} catch ( Exception e ) {

				logger.error( "Failed to parse port: " + ajpPort ) ;

			}

			String instanceId = workerId + svcPort + host ;
			cookieName = svcInstance.getCookieName( ) ;
			instances.append( workerName + svcPort + host + "," ) ;
			// instances.append(serviceName + "_" + svcPort + host +
			// ",");
			tempWorkerSettingsBuffer.append( instanceId + ".port=" + ajpPort + "\n" ) ;
			tempWorkerSettingsBuffer.append( instanceId + ".host=" + host + "\n" ) ;
			tempWorkerSettingsBuffer.append( instanceId + ".type=ajp13\n" ) ;
			tempWorkerSettingsBuffer.append( instanceId + ".secret="
					+ csapApp.getTomcatAjpKey( ) + "\n" ) ;

			// adding in lifecycle to prevent cross-infra calls
			tempWorkerSettingsBuffer.append( instanceId + ".lbfactor=1\n" ) ;
			tempWorkerSettingsBuffer.append( instanceId + ".socket_keepalive=True\n" ) ;
			tempWorkerSettingsBuffer.append( instanceId
					+ ".connection_pool_timeout=10\n\n" ) ;
			tempWorkerSettingsBuffer.append( instanceId
					+ ".connection_pool_minsize=0\n\n" ) ;

			if ( ! svcInstance.getMetaData( ).contains( SKIP_INTERNAL_AJP_TAG ) ) {

				workerSettingsBuffer.append( tempWorkerSettingsBuffer ) ;
				isIncludedInInternal = true ;

			}

			if ( svcInstance.getMetaData( ).contains( EXPORT_WEB_TAG ) ) {

				workerSettingsSecureBuffer.append( tempWorkerSettingsBuffer ) ;
				isIncludedInExport = true ;

			}

			context = svcInstance.getContext( ) ;

		}

		// only wire in services for which there is at least one
		// instance
		// configured
		if ( instances.length( ) > 0 ) {

			// Get rid of trailing ,
			instances.deleteCharAt( instances.length( ) - 1 ) ;

			if ( isIncludedInInternal ) {

				jkMountBuffer.append( "JkMount /" + context + "* " + workerLBHeader
						+ "\n" ) ;

				if ( workerListCount++ % NUM_WORKERS_PER_LINE == 0 ) {

					workerListBuffer.append( "\nworker.list=" ) ;

				}

				workerListBuffer.append( workerLBHeader + ", " ) ;

				workerSettingsBuffer.append( workerLB + ".type=lb\n" ) ;
				workerSettingsBuffer.append( workerLB + ".balance_workers=" + instances
						+ "\n" ) ;

				if ( cookieName.length( ) > 0 ) {

					workerSettingsBuffer.append( workerLB + ".session_cookie="
							+ cookieName
							+ "\n" ) ;

				}

				workerSettingsBuffer.append( workerLB + ".sticky_session=1\n\n" ) ;

			}

			if ( isIncludedInExport ) {

				jkMountSecureBuffer.append( "JkMount /" + context + "* "
						+ workerLBHeader
						+ "\n" ) ;

				if ( workerListSecureCount++ % NUM_WORKERS_PER_LINE == 0 ) {

					workerListSecureBuffer.append( "\nworker.list=" ) ;

				}

				workerListSecureBuffer.append( workerLBHeader + ", " ) ;

				workerSettingsSecureBuffer.append( workerLB + ".type=lb\n" ) ;
				// instances.deleteCharAt(instances.length() - 1);
				workerSettingsSecureBuffer.append( workerLB + ".balance_workers="
						+ instances + "\n" ) ;

				workerSettingsSecureBuffer.append( workerLB + ".sticky_session=1\n\n" ) ;

			} else {

				workerSettingsSecureBuffer
						.append( "### Skipping service - add the secure flag to metadata in clusterConfig to include\n\n" ) ;

			}

		} else {

			logger.debug( "service not configured" ) ;

		}

	}

	public File getHttpdModjkFile ( ) {

		return new File( HTTP_MODJK_FILE ) ;

	}

	private void generateModJKFile (
										StringBuffer jkMountBuffer ,
										StringBuffer jkMountSecureBuffer ,
										StringBuilder progress ) {

		logger.debug( "Generating modJK mount file: {} ", getHttpdModjkFile( ).getAbsolutePath( ) ) ;
		progress.append( CSAP.padLine( "modJK mount" ) + getHttpdModjkFile( ).getAbsolutePath( ) ) ;

		StringBuffer header = new StringBuffer( "# Generated by " + getClass( ).getCanonicalName( )
				+ "\n" ) ;

		try {

			FileWriter fstream = new FileWriter( getHttpdModjkFile( ) ) ;
			BufferedWriter out = new BufferedWriter( fstream ) ;
			out.write( header.toString( ) ) ;
			out.write( jkMountBuffer.toString( ) ) ;
			out.close( ) ;

			fstream = new FileWriter( new File( HTTP_MODJK_EXPORT_FILE ) ) ;
			out = new BufferedWriter( fstream ) ;
			out.write( header.toString( ) ) ;
			out.write( jkMountSecureBuffer.toString( ) ) ;
			out.close( ) ;

		} catch ( IOException e ) {

			logger.error( "Failed to write file, {}", CSAP.buildCsapStack( e ) ) ;

		}

	}

	public File getHttpdModReWriteFile ( ) {

		return new File( REWRITE_FILE ) ;

	}

	/**
	 * generate a test file for customers
	 */
	private void generateRewriteFile ( StringBuilder progress ) {

		File generatedRewriteFile = getHttpdModReWriteFile( ) ;
		logger.debug( "Generating Mod Rewrite file: {}", generatedRewriteFile.getAbsolutePath( ) ) ;
		progress.append( CSAP.padLine( "modJK rewrite" ) + generatedRewriteFile.getAbsolutePath( ) ) ;

		StringBuffer mappingsBuffer = new StringBuffer( "# Generated by "
				+ getClass( ).getCanonicalName( ) + "\n" ) ;
		mappingsBuffer.append( "RewriteEngine on" + "\n\n" ) ;
		mappingsBuffer.append( "# CsAgent LB is handled by admin service\n" ) ;
		// mappingsBuffer.append( "RewriteRule ^/CsAgent/(.*)$ /admin/$1
		// [R]\n\n" );
		mappingsBuffer.append( "RewriteRule ^/CsAgent/(.*)$ http://%{SERVER_NAME}/admin/$1 [R]\n\n" ) ;

		TreeMap<String, List<ServiceInstance>> serviceToConfigs = csapApp.getRootProject( )
				.serviceInstancesInCurrentLifeByName( ) ;

		for ( String svcName : serviceToConfigs.keySet( ) ) {

			for ( int customerId = 0; customerId < 10; ) {

				if ( svcName.equals( CsapCore.AGENT_NAME ) ) {

					break ;

				}

				List<ServiceInstance> svcConfigList = serviceToConfigs.get( svcName ) ;

				if ( svcConfigList.size( ) == 0 )
					break ;

				ServiceInstance firstService = svcConfigList.get( 0 ) ;

				JsonNode reWrites = firstService.getAttributeAsJson( ServiceAttributes.webServerReWrite ) ;

				if ( reWrites != null ) {

					mappingsBuffer.append( "# Custom service rewrite from service: " + firstService.getName( )
							+ "\n" ) ;
					reWrites.forEach( line -> {

						mappingsBuffer.append( line.asText( ) + "\n" ) ;

					} ) ;
					mappingsBuffer.append( "\n" ) ;

				}

				if ( ! firstService.is_cluster_single_host_modjk( )
						|| firstService.is_csap_api_server( ) || firstService.is_docker_server( ) || firstService
								.is_os_process_monitor( ) ) {

					break ;

				}

				// logger.info("==========" + svcName + " customerId " +
				// customerId + " svcConfigList.size " + svcConfigList.size());
				boolean foundFactory = false ; // hook to skip services without
				// any filter matches

				for ( ServiceInstance svcInstance : svcConfigList ) {

					foundFactory = true ;
					customerId++ ;
					String svcContext = svcInstance.getContext( ) ;

					// String svcContext = svcInstance.getContext();
					// RewriteRule ^/customer1/ims/(.*)$
					// /CsspFactorySampleV1-tory01/$1 [PT]
					if ( svcContext.indexOf( "-" ) != -1 ) {

						mappingsBuffer.append( "RewriteRule " ) ;

						mappingsBuffer.append( "^/csagenttestcust" + customerId + "/"
								+ svcContext.substring( 0, svcContext.indexOf( "-" ) ) + "/(.*)$ /"
								+ svcContext + "/$1" ) ;

						mappingsBuffer.append( " [PT]\n" ) ;

					}

				}

				if ( ! foundFactory ) {

					break ;

				}

			}

			try {

				FileWriter fstream = new FileWriter( generatedRewriteFile ) ;
				BufferedWriter out = new BufferedWriter( fstream ) ;
				out.write( mappingsBuffer.toString( ) ) ;
				out.close( ) ;

			} catch ( IOException e ) {

				logger.error( "Failed to write file, {}", CSAP.buildCsapStack( e ) ) ;

			}

		}

	}

	private void generateWorkerFile (
										StringBuffer workerListBuffer ,
										StringBuffer workerListSecureBuffer ,
										StringBuffer workerSettingsBuffer ,
										StringBuffer workerSettingsSecureBuffer ) {

		try {

			FileWriter fstream = new FileWriter( getHttpdWorkersFile( ) ) ;
			BufferedWriter out = new BufferedWriter( fstream ) ;
			out.write( workerListBuffer.toString( ) ) ;
			out.write( "\n\n" ) ;
			out.write( workerSettingsBuffer.toString( ) ) ;
			out.close( ) ;
			// fstream = new FileWriter(new File(WORKER_EXPORT_FILE.replace(
			// "/cssp", "/" + ajpExportPrefix)));
			fstream = new FileWriter( new File( HTTP_WORKER_EXPORT_FILE ) ) ;
			out = new BufferedWriter( fstream ) ;
			out.write( workerListSecureBuffer.toString( ) ) ;
			out.write( workerSettingsSecureBuffer.toString( ) ) ;
			out.close( ) ;

		} catch ( Exception e ) {

			logger.error( "Failed to write file: {}", CSAP.buildCsapStack( e ) ) ;

		}

	}

	private void generateSslFiles ( StringBuilder progress , SslSettings sslSettings ) {

		progress.append( CSAP.buildDescription( "copying",
				"source", sslSettings.getKeystoreFile( ),
				"dest", SSL_P12__FILE ) ) ;

		// build certs

//		var p12Url = new URL( sslSettings.getKeystoreFile( ) ) ;

		try {

			var resourceLoader = new DefaultResourceLoader( ) ;
			var cp = resourceLoader.getResource( sslSettings.getKeystoreFile( ) ) ;

			var src = cp.getInputStream( ) ;
			Files.copy( src, Paths.get( SSL_P12__FILE ), StandardCopyOption.REPLACE_EXISTING ) ;

			var script = List.of(
					"bash",
					"-c",
					"openssl pkcs12 -in " + SSL_P12__FILE
							+ " -out " + SSL_PEM__FILE
							+ " -nodes -passin pass:" + sslSettings.getKeystorePassword( ) ) ;

			progress.append( osCommandRunner.executeString( script, csapApp.getCsapWorkingFolder( ) ) ) ;

		} catch ( Exception e ) {

			logger.warn( "Failed loading: {} {}", sslSettings.getKeystoreFile( ), CSAP.buildCsapStack( e ) ) ;

		}

	}

	/**
	 * Method for creating a proxy file that utilizes httpd mod_proxy to direct
	 * CsAgent requests through the LB to a Specific host
	 */
	private void generateHttpProxy ( StringBuilder progress ) {

		StringBuffer proxyLines = new StringBuffer( "# Proxys Not In use.\n\n" ) ;

		proxyLines.append( "# End OF Proxy config \n\n" ) ;
		generateProxyFile( proxyLines, progress ) ;

	}

	private void generateProxyFile ( StringBuffer mappingsBuffer , StringBuilder progress ) {

		logger.debug( "Generating Proxy file: ", PROXY_FILE ) ;
		progress.append( CSAP.padLine( "Proxy file" ) + PROXY_FILE + " from buffer sized: " + mappingsBuffer
				.length( ) ) ;

		File proxyFile = new File( PROXY_FILE ) ;

		StringBuffer header = new StringBuffer( "# Generated by " + getClass( ).getCanonicalName( )
				+ "\n" ) ;

		// The following is loaded in the template in staging/bin
		// header.append("LoadModule jk_module modules/mod_jk.so\n");
		// header.append("JkWorkersFile conf/worker.properties\n");
		// header.append("JkLogFile logs/mod_jk.log\n");
		// header.append("JkLogLevel info\n"); // debug info
		// header.append("JkLogStampFormat \"[%a %b %d %H:%M:%S %Y] \"\n");
		// header.append("JkOptions +ForwardKeySize +ForwardURICompat
		// -ForwardDirectories\n");
		// header.append("JkRequestLogFormat \"%w %V %T\"\n");
		try {

			FileWriter fstream = new FileWriter( proxyFile ) ;
			BufferedWriter out = new BufferedWriter( fstream ) ;
			out.write( header.toString( ) ) ;
			out.write( mappingsBuffer.toString( ) ) ;
			out.close( ) ;

		} catch ( IOException e ) {

			logger.error( "Failed to write file, {}", CSAP.buildCsapStack( e ) ) ;

		}

	}

}
