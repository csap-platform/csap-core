package org.csap.agent.services ;

import java.io.BufferedWriter ;
import java.io.File ;
import java.io.FileWriter ;
import java.io.IOException ;
import java.net.InetAddress ;
import java.net.NetworkInterface ;
import java.nio.file.Files ;
import java.text.DecimalFormat ;
import java.text.Format ;
import java.text.NumberFormat ;
import java.text.SimpleDateFormat ;
import java.time.LocalDateTime ;
import java.time.format.DateTimeFormatter ;
import java.util.ArrayList ;
import java.util.Arrays ;
import java.util.Date ;
import java.util.HashMap ;
import java.util.List ;
import java.util.Map ;
import java.util.Optional ;
import java.util.Spliterator ;
import java.util.Spliterators ;
import java.util.TreeMap ;
import java.util.concurrent.ConcurrentHashMap ;
import java.util.concurrent.Executors ;
import java.util.concurrent.ScheduledExecutorService ;
import java.util.concurrent.TimeUnit ;
import java.util.concurrent.locks.ReentrantLock ;
import java.util.regex.Matcher ;
import java.util.regex.Pattern ;
import java.util.stream.Collectors ;
import java.util.stream.Stream ;
import java.util.stream.StreamSupport ;

import javax.inject.Inject ;

import org.apache.commons.lang3.StringUtils ;
import org.apache.commons.lang3.concurrent.BasicThreadFactory ;
import org.csap.agent.CsapCore ;
import org.csap.agent.container.ContainerProcess ;
import org.csap.agent.container.DockerIntegration ;
import org.csap.agent.container.DockerJson ;
import org.csap.agent.container.kubernetes.KubernetesIntegration ;
import org.csap.agent.integrations.CsapEvents ;
import org.csap.agent.linux.InfrastructureRunner ;
import org.csap.agent.linux.LogRollerRunnable ;
import org.csap.agent.linux.OsCommandRunner ;
import org.csap.agent.linux.OutputFileMgr ;
import org.csap.agent.linux.ServiceJobRunner ;
import org.csap.agent.linux.ServiceResourceRunnable ;
import org.csap.agent.linux.TopRunnable ;
import org.csap.agent.linux.TransferManager ;
import org.csap.agent.linux.ZipUtility ;
import org.csap.agent.model.Application ;
import org.csap.agent.model.ContainerState ;
import org.csap.agent.model.ServiceInstance ;
import org.csap.agent.stats.HostCollector ;
import org.csap.agent.stats.MetricCategory ;
import org.csap.agent.stats.OsProcessCollector ;
import org.csap.agent.stats.OsProcessEnum ;
import org.csap.agent.stats.OsSharedResourcesCollector ;
import org.csap.agent.stats.ServiceCollector ;
import org.csap.helpers.CSAP ;
import org.csap.helpers.CsapApplication ;
import org.csap.helpers.CsapSimpleCache ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.beans.factory.annotation.Autowired ;
import org.springframework.boot.context.properties.EnableConfigurationProperties ;
import org.springframework.stereotype.Service ;
import org.springframework.web.multipart.MultipartFile ;

import com.fasterxml.jackson.core.JsonParseException ;
import com.fasterxml.jackson.core.type.TypeReference ;
import com.fasterxml.jackson.databind.JsonMappingException ;
import com.fasterxml.jackson.databind.JsonNode ;
import com.fasterxml.jackson.databind.ObjectMapper ;
import com.fasterxml.jackson.databind.node.ArrayNode ;
import com.fasterxml.jackson.databind.node.ObjectNode ;

import io.micrometer.core.annotation.Timed ;

@Service
@EnableConfigurationProperties ( OsCommands.class )
public class OsManager {

	private OsCommands osCommands ;

	public static final String IO_UTIL_IN_PERCENT = "ioUtilInPercent" ;
	public static final String SWAP = "swap" ;
	public static final String RAM = "ram" ;
	public static final String BUFFER = "buffer" ;

	final Logger logger = LoggerFactory.getLogger( this.getClass( ) ) ;

	private ObjectMapper jsonMapper = new ObjectMapper( ) ;

	private Application csapApp ;

	OsCommandRunner osCommandRunner = new OsCommandRunner( 120, 3, "OsMgr" ) ;
	OsCommandRunner kuberernetesRunner = new OsCommandRunner( 60, 1, "kuberernetesRunner" ) ;

	LogRollerRunnable logRoller = null ;
	ServiceJobRunner jobRunner = null ;
	InfrastructureRunner infraRunner = null ;

	OsProcessMapper processMapper ;

	@Autowired
	public OsManager ( Application csapApp, OsCommands osCommands ) {

		this.csapApp = csapApp ;
		this.osCommands = osCommands ;

		if ( csapApp != null ) {

			processMapper = new OsProcessMapper( csapApp.getCsapWorkingFolder( ), csapApp.metrics( ) ) ;

		} else {

			logger.warn( "OsProcessMapper not initialized" ) ;

		}

	}

	@Inject
	ServiceOsManager serviceManager ;

	public void setTestApp ( Application app ) {

		csapApp = app ;
		processMapper = new OsProcessMapper( csapApp.getCsapWorkingFolder( ), csapApp.metrics( ) ) ;
		setLinuxLineFormat( ) ;

	}

	public void startAgentResourceCollectors ( ) {

		StringBuilder startInfo = new StringBuilder( "Resource Collectors" ) ;

		int topSeconds = getTopIntervalSeconds( ) ;
		topStatsRunnable = new TopRunnable( topSeconds, osCommands.getSystemProcessMetrics( topSeconds ) ) ;
		startInfo.append( CSAP.padLine( "linux top" ) + topSeconds + " seconds" ) ;

		logRoller = new LogRollerRunnable( csapApp ) ;
		startInfo.append( CSAP.padLine( "linux logrotate" ) + csapApp.rootProjectEnvSettings( ).getLogRotationMinutes( )
				+ " minutes" ) ;

		if ( jobRunner == null ) {

			jobRunner = new ServiceJobRunner( csapApp ) ;
			startInfo.append( CSAP.padLine( "csap jobrunner" ) + "60 minutes" ) ;

		}

		infraRunner = new InfrastructureRunner( csapApp ) ;

		//
		var diskCollectionMinutes = csapApp.rootProjectEnvSettings( ).getDuIntervalMins( ) ;

		if ( diskCollectionMinutes > 0 ) {

			var initialDelay = 1 ;
			startInfo.append( CSAP.padLine( "os: disk, network, services" ) + diskCollectionMinutes + " minutes" ) ;
			intenseOsCommandExecutor.scheduleWithFixedDelay(
					( ) -> collect_disk_and_linux_package( ),
					initialDelay, diskCollectionMinutes, TimeUnit.MINUTES ) ; // initial,and interval

		} else {

			startInfo.append( CSAP.padLine( "disk usage (du and df)" ) + "disabled" ) ;

		}

		if ( csapApp.rootProjectEnvSettings( ).isLsofEnabled( ) ) {

			int lsofInterval = csapApp.rootProjectEnvSettings( )
					.getLsofIntervalMins( ) ;

			serviceResourceRunnable = new ServiceResourceRunnable( csapApp, osCommandRunner ) ;

			intenseOsCommandExecutor.scheduleWithFixedDelay(
					serviceResourceRunnable,
					1, lsofInterval, TimeUnit.MINUTES ) ;

			startInfo.append(
					CSAP.padLine( "service resources" ) + lsofInterval
							+ " minutes. Includes: minutes socket(ss), io(pidstat), files(/proc)" ) ;

			intenseOsCommandExecutor.scheduleWithFixedDelay(
					( ) -> collectHostSocketsThreadsFiles( ),
					1, lsofInterval, TimeUnit.MINUTES ) ;

			startInfo.append( CSAP.padLine( "host resources" ) + lsofInterval
					+ " minutes.  Includes: ss,ps,lsof,/proc)" ) ;

			intenseOsCommandExecutor.scheduleWithFixedDelay(
					( ) -> pingContainers( ),
					1, lsofInterval, TimeUnit.MINUTES ) ;

			startInfo.append( CSAP.padLine( "docker/kubernetes health" ) + lsofInterval
					+ " minutes. runs summary reports" ) ;

		} else {

			startInfo.append( "\n\t -  Sockets, IO, And file capture is disabled" ) ;

		}

		logger.info( startInfo.toString( ) ) ;

	}

	public void pingContainers ( ) {

		if ( csapApp.isKubernetesInstalledAndActive( ) ) {

			csapApp.getKubernetesIntegration( ).buildKubernetesHealthReport( ) ;

		}

		if ( csapApp.isDockerInstalledAndActive( ) ) {

			csapApp.getDockerIntegration( ).buildSummary( ) ;

		}

	}

	public void shutDown ( ) {

		if ( topStatsRunnable != null ) {

			topStatsRunnable.shutdown( ) ;

		}

		if ( serviceResourceRunnable != null ) {

			serviceResourceRunnable.shutDown( ) ;

		}

		// if (hostStatusManager != null)
		// hostStatusManager.stop();
		if ( logRoller != null ) {

			logRoller.shutdown( ) ;

		}

		intenseOsCommandExecutor.shutdownNow( ) ;

	}

	private Integer getTopIntervalSeconds ( ) {

		// Default is to poll every 1/2 of the service collection interval
		if ( csapApp.rootProjectEnvSettings( )
				.getMetricToSecondsMap( )
				.size( ) == 0
				|| ! csapApp.rootProjectEnvSettings( )
						.getMetricToSecondsMap( )
						.containsKey( "service" )
				|| csapApp.rootProjectEnvSettings( )
						.getMetricToSecondsMap( )
						.get( "service" )
						.size( ) == 0 ) {

			return 30 ;

		}

		return csapApp.rootProjectEnvSettings( )
				.getMetricToSecondsMap( )
				.get( "service" )
				.get( 0 )
				/ 2 ;

	}

	BasicThreadFactory openFilesThreadFactory = new BasicThreadFactory.Builder( )
			.namingPattern( "CsapOsManager-%d" )
			.daemon( true )
			.priority( Thread.NORM_PRIORITY )
			.build( ) ;

	// both du and lsof get invoked here. Note we do this to avoid overwhelming
	// the OS with concurrent commands
	private ScheduledExecutorService intenseOsCommandExecutor = Executors
			.newScheduledThreadPool( 3, openFilesThreadFactory ) ;

	public void resetAllCaches ( ) {

		logger.debug( "All caches reset" ) ;

		if ( diskStatisticsCache != null )
			diskStatisticsCache.expireNow( ) ;

		if ( processStatisticsCache != null )
			processStatisticsCache.expireNow( ) ;

		if ( memoryStatisticsCache != null )
			memoryStatisticsCache.expireNow( ) ;

		// du is long running - so it is scheduled in background
		try {

			scheduleDiskUsageCollection( ) ;

		} catch ( Exception e ) {

			logger.error( "Failed to schedule du", e ) ;

		}

		checkForProcessStatusUpdate( ) ;

	}

	TopRunnable topStatsRunnable = null ; // use a lazy load so that thread
	// priority

	public int getHostTotalTopCpu ( ) {

		Float f = Float.valueOf( -1 ) ;

		if ( topStatsRunnable == null ) {

			return -1 ;

		}

		try {

			String[] pids = {
					TopRunnable.VM_TOTAL
			} ;
			f = topStatsRunnable.getCpuForPid( Arrays.asList( pids ) ) ;

		} catch ( Exception e ) {

			logger.error( "Unable to determine {}", CSAP.buildCsapStack( e ) ) ;

		}

		return f.intValue( ) ;

	}

	public int numberOfProcesses ( ) {

		try {

			return lastProcessStatsCollected( ).split( LINE_SEPARATOR ).length ;

		} catch ( Exception e ) {

		}

		return -1 ;

	}

	public ArrayNode processStatus ( ) {

		return jsonMapper.convertValue( processMapper.getLatestDiscoveredProcesses( ), ArrayNode.class ) ;

	}

	/**
	 * Synchronized as multiple metrics collections will try to hit this frequently.
	 * This avoids costly ps calls
	 */
	public ObjectNode buildServiceStatsReportAndUpdateTopCpu (
																boolean isCsapDefinitionProcessesOnly ) {

		ObjectNode servicesJson = jsonMapper.createObjectNode( ) ;

		logger.debug( "Cache Refresh " ) ;

		checkForProcessStatusUpdate( ) ;

		logger.debug( "Cache Refresh: psResult: ", lastProcessStatsCollected( ) ) ;

		var osPerformanceData = servicesJson.putObject( "ps" ) ;

		if ( isCsapDefinitionProcessesOnly ) {

			csapApp.servicesOnHost( )
					.map( serviceInstance -> {

						// update top in date
						serviceInstance.getContainerStatusList( ).stream( )
								.forEach( container -> {

									var topCpu = 0.0f ;

									if ( topStatsRunnable != null ) {

										topCpu = topStatsRunnable.getCpuForPid( container.getPid( ) ) ;

									}

									container.setTopCpu( topCpu ) ;

								} ) ;
						return serviceInstance ;

					} )
					.forEach( serviceInstance -> {

						if ( serviceInstance.is_cluster_kubernetes( ) 
								&& ! serviceInstance.getDefaultContainer( ).isRunning( ) ) {

							// filter out inactive kubernetes processes
						} else {

							String id = serviceInstance.getPerformanceId( ) ;
							osPerformanceData.set( id, serviceInstance.buildRuntimeState( ) ) ;

						}

					} ) ;

		} else {

			// ObjectNode processNode = osPerformanceData.putObject( osProcess.getPid() );
			processMapper
					.getLatestDiscoveredProcesses( )
					.stream( )
					.map( processMapper::mapProcessToUiJson )
					.forEach( csapUiProcess -> {

						osPerformanceData.set( csapUiProcess.at( ContainerState.JSON_PATH_PID ).asText( ),
								csapUiProcess ) ;

					} ) ;
			;

		}

		servicesJson.set( "mp", getMpStateFromCache( ) ) ;

		return servicesJson ;

	}

	private CsapSimpleCache cpuStatisticsCache = null ;
	private ReentrantLock mpStatusLock = new ReentrantLock( ) ;

	private ObjectNode getMpStateFromCache ( ) {

		if ( cpuStatisticsCache == null ) {

			cpuStatisticsCache = CsapSimpleCache.builder(
					9,
					TimeUnit.SECONDS,
					OsManager.class,
					"CPU Statistics" ) ;
			cpuStatisticsCache.expireNow( ) ;

		}

		if ( ( cpuStatisticsCache.getCachedObject( ) != null )
				&& ! cpuStatisticsCache.isExpired( ) ) {

			logger.debug( "\n\n***** ReUsing  cpuStatisticsCache   *******\n\n" ) ;

		} else if ( mpStatusLock.tryLock( ) ) {

			logger.debug( "\n\n***** REFRESHING   cpuStatisticsCache   *******\n\n" ) ;
			var timer = csapApp.metrics( ).startTimer( ) ;

			try {

				cpuStatisticsCache.reset( updateMpCache( ) ) ;

			} catch ( Exception e ) {

				logger.info( "Failed refreshing runtime", e ) ;

			} finally {

				mpStatusLock.unlock( ) ;

			}

			csapApp.metrics( ).stopTimer( timer, "collect-os.cpu-metrics" ) ;

		}

		return (ObjectNode) cpuStatisticsCache.getCachedObject( ) ;

	}

	private ObjectNode updateMpCache ( ) {

		ObjectNode mpNode = jsonMapper.createObjectNode( ) ;
		String mpResult = "" ;
		List<String> parmList = Arrays.asList( "bash", "-c",
				"mpstat -P ALL  2 1| grep -i average | sed 's/  */ /g'" ) ;

		mpResult = osCommandRunner.executeString( null, parmList ) ;

		mpResult = csapApp.check_for_stub( mpResult, "linux/mpResults.txt" ) ;

		logger.debug( "mpResult: {}", mpResult ) ;

		if ( Application.isRunningOnDesktop( ) ) {

			updateCachesWithTestData( ) ;

		}

		// Skip past the header
		mpResult = mpResult.substring( mpResult.indexOf( "Average" ) ) ;

		String[] mpLines = mpResult.split( LINE_SEPARATOR ) ;

		for ( int i = 0; i < mpLines.length; i++ ) {

			String curline = mpLines[i].trim( ) ;
			String[] cols = curline.split( " " ) ;

			if ( cols.length < 11 || cols[1].equalsIgnoreCase( "cpu" )
					|| cols[0].startsWith( "_" ) ) {

				logger.debug( "Skipping line: {}", curline ) ;
				continue ;

			}

			String name = cols[1] ;

			ObjectNode cpuNode = mpNode.putObject( name ) ;

			cpuNode.put( "time", cols[0] + cols[1] ) ;

			if ( ! name.equals( "all" ) ) {

				name = "CPU -" + name ;

			}

			cpuNode.put( "cpu", name ) ;
			cpuNode.put( "puser", cols[2] ) ;
			cpuNode.put( "pnice", cols[3] ) ;
			cpuNode.put( "psys", cols[4] ) ;
			cpuNode.put( "pio", cols[5] ) ;
			cpuNode.put( "pirq", cols[6] ) ;
			cpuNode.put( "psoft", cols[7] ) ;
			cpuNode.put( "psteal", cols[8] ) ;
			cpuNode.put( "pidle", cols[9] ) ;
			cpuNode.put( "intr", cols[10] ) ;

		}

		return mpNode ;

	}

	private ServiceResourceRunnable serviceResourceRunnable = null ;

	private void updateCachesWithTestData ( ) {

		try {

			setLinuxLineFormat( ) ;
			processStatisticsCache.reset( csapApp.check_for_stub( "", "linux/ps-service-matching.txt" ) ) ;

			diskUsageForServicesCache = csapApp.check_for_stub( "", "linux/ps-service-disk.txt" ) ;
			// logger.debug( CsapApplication.testHeader( diskUsageForServicesCache ) ) ;

			diskUsageForServicesCache += csapApp.check_for_stub( "", "linux/ps-system-disk.txt" ) ;
			diskUsageForServicesCache += csapApp.check_for_stub( "", "linux/ps-docker-volumes.txt" ) ;
			// diskUsageForServicesCache += csapApp.check_for_stub( "",
			// "linux/dfResults.txt" ) ;

			diskUsageForServicesCache += collectDockerDiskUsage( ) ;

		} catch ( Exception e ) {

			logger.error( "Failed to load test data: {}",
					CSAP.buildCsapStack( e ) ) ;

		}

	}

	public static void setLinuxLineFormat ( ) {

		LINE_SEPARATOR = "\n" ;

	}

	// public void goActive() {
	// if (topStatsRunnable == null) {
	// topStatsRunnable = new TopRunnable();
	// // triggers the thread to go active
	// topStatsRunnable.getCpuForPid(Arrays.asList("dummy"));
	// }
	// }
	ArrayNode readOnlyFsResultsCache = null ;
	long readOnlyFsTimeStamp = 0 ;

	public ArrayNode getReadOnlyFs ( ) {

		logger.debug( "Getting getReadOnlyFs " ) ;

		// Use cache
		if ( System.currentTimeMillis( ) - readOnlyFsTimeStamp < 1000 * 60 ) {

			logger.debug( "\n\n***** ReUsing  readOnlyFsResultsCache  *******\n\n" ) ;

			return readOnlyFsResultsCache ;

		}

		// Lets refresh cache
		logger.debug( "\n\n***** Refreshing readOnlyFsResultsCache   *******\n\n" ) ;
		readOnlyFsTimeStamp = System.currentTimeMillis( ) ;

		try {

			List<String> parmList = Arrays.asList( "bash", "-c",
					"awk '$4~/(^|,)ro($|,)/' /proc/mounts | grep '^/dev/mapper' " ) ;
			String roResult = osCommandRunner.executeString( parmList, new File(
					"." ) ) ;

			logger.debug( "roResult: {}", roResult ) ;

			roResult = csapApp.check_for_stub( roResult, "linux/roResults.txt" ) ;

			// if ( Application.isRunningOnDesktop() ) {
			// roResult = Application.loadTestData( "linux/roResults.txt" ) ;
			// }

			ArrayNode readOnlyResults = jsonMapper.createArrayNode( ) ;

			String[] roLines = roResult.split( System
					.getProperty( "line.separator" ) ) ;

			for ( int i = 0; i < roLines.length; i++ ) {

				if ( roLines[i].trim( ).length( ) > 0
						&& ! roLines[i].contains( OsCommandRunner.HEADER_TOKEN ) ) {

					readOnlyResults.add( roLines[i].trim( ) ) ;

				}

			}

			readOnlyFsResultsCache = readOnlyResults ;

		} catch ( Exception e ) {

			logger.error( "Failed to write output", e ) ;

		}

		return readOnlyFsResultsCache ;

	}

	// @Inject
	// CsapEventClient csapEventClient;

	ArrayNode whoResultsCache = jsonMapper.createArrayNode( ) ;
	long lastWhoTimeStamp = 0 ;

	public ArrayNode getVmLoggedIn ( ) {

		logger.debug( "Entered " ) ;

		// Use cache
		if ( System.currentTimeMillis( ) - lastWhoTimeStamp < 1000 * 60 ) {

			logger.debug( "\n\n***** ReUsing  who cache   *******\n\n" ) ;

			return whoResultsCache ;

		}

		logger.debug( "\n\n***** Refreshing who cache   *******\n\n" ) ;
		lastWhoTimeStamp = System.currentTimeMillis( ) ;

		try {

			List<String> parmList = Arrays.asList( "bash", "-c",
					"who |sed 's/  */ /g'"
							+ "" ) ;
			String whoResult = osCommandRunner.executeString( parmList, new File( "." ) ) ;

			logger.debug( "whoResult: {}", whoResult ) ;

			if ( Application.isRunningOnDesktop( ) ) {

				if ( Application.isDisplayOnDesktop( ) ) {

					logger.warn( "Application.isRunningOnDesktop() - adding dummy login data" ) ;

				}

				whoResult = "csapUser  pts/0        2014-04-16 07:58 (rtp-someDeveloper-8811.yourcompany.com)"
						+ System.getProperty( "line.separator" )
						+ "csapUser pts/34 2014-02-07 06:51" ;

			}

			ArrayNode whoResults = jsonMapper.createArrayNode( ) ;

			String[] whoLines = whoResult.split( System
					.getProperty( "line.separator" ) ) ;

			for ( int i = 0; i < whoLines.length; i++ ) {

				String curline = whoLines[i].trim( ) ;

				String[] cols = curline.split( " " ) ;

				// some systems have a lot of non-external connections.
				// col 5 will contain host if it is external. So we ignore the
				// others
				// To focus on external traffic only
				if ( curline.length( ) == 0 || curline.contains( OsCommandRunner.HEADER_TOKEN )
						|| cols.length == 4 ) {

					continue ;

				}

				whoResults.add( curline ) ;

			}

			if ( ! whoResultsCache.toString( ).equals( whoResults.toString( ) ) ) {

				if ( whoResults.size( ) == 0 ) {

					csapApp.getActiveUsers( ).logSessionEnd( Application.SYS_USER,
							"No host sessions found, last found:\n"
									+ CSAP.jsonPrint( whoResultsCache ) ) ;

					// csapApp.getEventClient().publishUserEvent( CsapEvents.CSAP_ACCESS_CATEGORY +
					// "",
					// Application.SYS_USER,
					// "Host Session(s) Cleared", "Connections are no longer active:\n"
					// + CSAP.jsonPrint( whoResultsCache ) ) ;
				} else {

					csapApp.getActiveUsers( ).logSessionStart( Application.SYS_USER,
							"Host Session(s) Changed:\n"
									+ CSAP.jsonPrint( whoResults ) ) ;

					// csapApp.getEventClient().publishUserEvent( CsapEvents.CSAP_ACCESS_CATEGORY,
					// Application.SYS_USER,
					// "Host Session(s) Changed", "Updated output of linux \"who\": \n"
					// + CSAP.jsonPrint( whoResults ) ) ;
				}

			}

			whoResultsCache = whoResults ;

		} catch ( Exception e ) {

			logger.error( "Failed to build report {}", CSAP.buildCsapStack( e ) ) ;

		}

		return whoResultsCache ;

	}

	public static final String RECEIVE_MB = "receiveMb" ;
	public static final String TRANSMIT_MB = "transmitMb" ;
	NumberFormat twoDecimals = new DecimalFormat( "#0.00" ) ;

	public ObjectNode networkReceiveAndTransmit ( ) {

		ObjectNode networkIO = jsonMapper.createObjectNode( ) ;

		// ens192
		String interfacePattern = csapApp.rootProjectEnvSettings( ).getPrimaryNetwork( ) ;

		List<String> lines = osCommands.getSystemNetworkStats( interfacePattern ) ;

		try {

			String ioOutput = osCommandRunner.runUsingRootUser( "proc-net-dev", lines ) ;
			ioOutput = csapApp.check_for_stub( ioOutput, "linux/proc-net-dev.txt" ) ;

			// output' eth0: 757699260493 1264491650 0 6 0 0 0 176 756289173213
			// 982166397 0 0 0 0 0 0'
			String[] interfaces = ioOutput.split( LINE_SEPARATOR ) ;

			for ( String interfaceLine : interfaces ) {

				String[] interfaceColumns = interfaceLine.trim( ).split( " " ) ;
				logger.debug( "output from: {}  , columns: {} \n{}", lines, interfaceColumns.length, ioOutput ) ;

				if ( interfaceColumns.length == 17 ) {

					networkIO.put( RECEIVE_MB,
							twoDecimals.format(
									networkIO.path( RECEIVE_MB ).asDouble( 0 )
											+ Double.parseDouble( interfaceColumns[1] ) / CSAP.MB_FROM_BYTES ) ) ;

					networkIO.put( "readErrors",
							networkIO.path( "readErrors" ).asInt( 0 )
									+ Integer.parseInt( interfaceColumns[3] ) ) ;

					networkIO.put( TRANSMIT_MB,
							twoDecimals.format(
									networkIO.path( TRANSMIT_MB ).asDouble( 0 )
											+ Double.parseDouble( interfaceColumns[9] ) / CSAP.MB_FROM_BYTES ) ) ;

					networkIO.put( "transmitErrors",
							networkIO.path( "transmitErrors" ).asInt( 0 )
									+ Integer.parseInt( interfaceColumns[11] ) ) ;

				} else {

					networkIO.put( "error", true ) ;

				}

			}

		} catch ( IOException e ) {

			logger.info( "Failed to run docker nsenter: {} , \n reason: {}", lines,
					CSAP.buildCsapStack( e ) ) ;

			networkIO.put( "error", true ) ;

		}

		return networkIO ;

	}

	private final static String KUBERNETES_NODE_SCRIPT = "bin/collect-kubernetes.sh" ;

	// private OsCommandRunner hostRootCommands = new OsCommandRunner( 30, 1,
	// "OsManager" );

	public volatile CsapSimpleCache kubernetesNodeUsageReportCache = null ;

	public JsonNode getLatestKubernetesNodeReport ( String hostName ) {

		buildCachedKubernetesNodeUsageReport( ) ;
		return ( (ObjectNode) kubernetesNodeUsageReportCache.getCachedObject( ) ).path( hostName ) ;

	}

	public ObjectNode buildCachedKubernetesNodeUsageReport ( ) {

		if ( kubernetesNodeUsageReportCache == null ) {

			// typically - very very fast - but this will handle large concurrent requests
			// from hitting system
			kubernetesNodeUsageReportCache = CsapSimpleCache.builder(
					5,
					TimeUnit.SECONDS,
					this.getClass( ),
					"osmanager-kuberntes-node-usage" ) ;
			kubernetesNodeUsageReportCache.expireNow( ) ;

		}

		// Use cache
		if ( ! kubernetesNodeUsageReportCache.isExpired( ) ) {

			logger.debug( "ReUsing kubernetesNodeUsageReportCache" ) ;

			return (ObjectNode) kubernetesNodeUsageReportCache.getCachedObject( ) ;

		}

		// Lets refresh cache
		logger.debug( "Refreshing kubernetesNodeUsageReportCache" ) ;

		// logger.info( "Call path: {}",
		// Application.getCsapFilteredStackTrace( new Exception( "calltree"
		// ), "csap" ) );
		logger.debug( "refreshing host stats" ) ;
		var nodeReports = jsonMapper.createObjectNode( ) ;

		var commandOutput = "" ;

		try {

			// running as root to get access to all files on host.
			commandOutput = osCommandRunner.runUsingRootUser(
					csapApp.csapPlatformPath( KUBERNETES_NODE_SCRIPT ),
					null ) ;

			logger.debug( "commandOutput: {}", commandOutput ) ;

			commandOutput = csapApp.check_for_stub( commandOutput, "linux/kubernetes-describe-nodes.txt" ) ;
			logger.debug( "trimmed results: {} ", commandOutput ) ;
			String now = LocalDateTime.now( ).format( DateTimeFormatter.ofPattern( "HH:mm:ss" ) ) ;

			String[] nodeCommandLines = commandOutput.split( LINE_SEPARATOR ) ;

			Arrays.stream( nodeCommandLines )
					.filter( StringUtils::isNotEmpty )
					.map( line -> CsapCore.singleSpace( line ).split( " " ) )
					.filter( columns -> columns.length <= 5 )
					.forEach( columns -> {

						switch ( columns[0] ) {

						case "Name:":
							if ( columns.length == 2 ) {

								nodeReports.put( "nodeName", columns[1] ) ;
								nodeReports.putObject( columns[1] ) ;

							}
							break ;

						case "Capacity:":
						case "Allocatable:":
						case "Allocated": {

							var nodeName = nodeReports.path( "nodeName" ).asText( ) ;
							var nodeReport = nodeReports.path( nodeName ) ;

							if ( nodeReport.isObject( ) ) {

								var sectionName = columns[0].split( ":" )[0] ;
								nodeReports.put( "sectionName", sectionName ) ;
								var node = (ObjectNode) nodeReport ;
								node.putObject( sectionName ) ;

							}

						}
							break ;

						case "cpu:":
						case "memory:":
						case "cpu":
						case "memory": {

							if ( columns.length == 2
									|| columns.length == 5 ) {

								var nodeName = nodeReports.path( "nodeName" ).asText( ) ;
								var nodeReport = nodeReports.path( nodeName ) ;

								if ( nodeReport.isObject( ) ) {

									var sectionName = nodeReports.path( "sectionName" ).asText( ) ;
									var sectionReport = nodeReport.path( sectionName ) ;

									if ( sectionReport.isObject( ) ) {

										var section = (ObjectNode) sectionReport ;
										var metricName = columns[0].split( ":" )[0] ;

										if ( columns.length == 2 ) {

											section.put( metricName, columns[1] ) ;

										} else {

											var metricReport = section.putObject( metricName ) ;
											metricReport.put( "request", columns[1] ) ;
											metricReport.put( "requestPercent", stripParens( columns[2] ) ) ;
											metricReport.put( "limit", columns[3] ) ;
											metricReport.put( "limitPercent", stripParens( columns[4] ) ) ;

										}

									}

								}

							}

							break ;

						}

						}

					} ) ;

			kubernetesNodeUsageReportCache.reset( nodeReports ) ;

			// setLatestKubernetesNodeReport( nodeReports ) ;

		} catch ( Exception e ) {

			logger.error( "Failed to process output from {}: {}\n {}",
					SOCKETS_THREADS_FILES_SCRIPT, CSAP.buildCsapStack( e ), commandOutput ) ;

		}

		return (ObjectNode) kubernetesNodeUsageReportCache.getCachedObject( ) ;

	}

	private String stripParens ( String input ) {

		return input.replaceAll( "[()%]", "" ) ;

	}

	volatile ObjectNode hostResourceSummary = null ;

	public ObjectNode getHostResourceSummary ( ) {

		if ( hostResourceSummary == null ) {

			collectHostSocketsThreadsFiles( ) ;

		}

		return hostResourceSummary ;

	}

	public int getHostSummaryItem (
									String fieldName ) {

		if ( hostResourceSummary == null ) {

			collectHostSocketsThreadsFiles( ) ;

		}

		if ( hostResourceSummary.has( fieldName ) ) {

			return hostResourceSummary.path( fieldName ).asInt( 0 ) ;

		}

		return 0 ;

	}

	private final static String SOCKETS_THREADS_FILES_SCRIPT = "bin/collect-host-resources.sh" ;

	// private OsCommandRunner hostRootCommands = new OsCommandRunner( 30, 1,
	// "OsManager" );

	private void collectHostSocketsThreadsFiles ( ) {

		// logger.info( "Call path: {}",
		// Application.getCsapFilteredStackTrace( new Exception( "calltree"
		// ), "csap" ) );
		logger.debug( "refreshing host stats" ) ;
		String statsResult = null ;

		try {

			// running as root to get access to all files on host.
			statsResult = osCommandRunner.runUsingRootUser( csapApp.csapPlatformPath( SOCKETS_THREADS_FILES_SCRIPT ),
					null ) ;

			logger.debug( "statsResult: {}", statsResult ) ;

			statsResult = csapApp.check_for_stub( statsResult, "linux/vmStatsRoot.txt" ) ;

			statsResult = statsResult.substring( statsResult.indexOf( "openFiles:" ) ) ;
			logger.debug( "trimmed results: {} ", statsResult ) ;
			String now = LocalDateTime.now( ).format( DateTimeFormatter.ofPattern( "HH:mm:ss" ) ) ;

			ObjectNode updatedHostSummary = jsonMapper.createObjectNode( ) ;
			updatedHostSummary.put( "refreshed", now ) ;

			String[] cols = statsResult.split( " " ) ;

			if ( cols.length == 16 ) {

				updatedHostSummary.put( "openFiles", cols[1] ) ;
				updatedHostSummary.put( "totalThreads", cols[3] ) ;
				updatedHostSummary.put( "csapThreads", cols[5] ) ;
				updatedHostSummary.put( "totalFileDescriptors", cols[7] ) ;
				updatedHostSummary.put( "csapFileDescriptors", cols[9] ) ;
				updatedHostSummary.put( "networkConns", cols[11] ) ;
				updatedHostSummary.put( "networkWait", cols[13] ) ;
				updatedHostSummary.put( "networkTimeWait", StringUtils.strip( cols[15], "\n" ) ) ;

			} else {

				updatedHostSummary.put( "error", statsResult ) ;

			}

			hostResourceSummary = updatedHostSummary ;

		} catch ( Exception e ) {

			logger.error( "Failed to process output from {}: {}\n {}",
					SOCKETS_THREADS_FILES_SCRIPT, CSAP.buildCsapStack( e ), statsResult ) ;

		}

	}

	public String getJournal (
								String serviceName ,
								String since ,
								String numberOfLines ,
								boolean reverse ,
								boolean json ) {

		String serviceFilter = "" ;

		if ( ! serviceName.equalsIgnoreCase( "none" ) ) {

			serviceFilter = " --unit " + serviceName + " " ;

		}

		String fromFilter = "" ;

		if ( StringUtils.isNotEmpty( since ) ) {

			fromFilter = " --since '" + since + "' " ;

		}

		String reverseParam = "" ;
		if ( reverse )
			reverseParam = " -r " ;

		String jsonParam = "" ;
		if ( json )
			jsonParam = " -o json " ;

		var journalCollectionScript = List.of(
				"#!/bin/bash",
				"journalctl --no-pager -n " + numberOfLines + serviceFilter + fromFilter + reverseParam + jsonParam,
				"" ) ;

		String scriptOutput = "Failed to run" ;

		try {

			scriptOutput = osCommandRunner.runUsingRootUser( "journal", journalCollectionScript ) ;
			logger.debug( "journalCollectionScript: {}, \n scriptOutput: {}", journalCollectionScript, scriptOutput ) ;

		} catch ( IOException e ) {

			logger.info( "Failed to update: {} ", CSAP.buildCsapStack( e ) ) ;
			scriptOutput += ", reason: " + e.getMessage( ) + " type: " + e.getClass( ).getName( ) ;

		}

		return scriptOutput ;

	}

	private int cachedNetworkInterfaceCount = -1 ;

	public List<String> networkInterfaces ( ) {

		var timer = csapApp.metrics( ).startTimer( ) ;

		logger.debug( "Entered " ) ;

		List<String> networkDevices = Arrays.asList( "none" ) ;

		List<String> collectionScripts = osCommands.getSystemNetworkDevices( ) ;

		String scriptOutput = "Failed to run" ;

		try {

			scriptOutput = osCommandRunner.runUsingRootUser( "system interface list", collectionScripts ) ;
			scriptOutput = csapApp.check_for_stub( scriptOutput, "linux/network-devices.txt" ) ;
			String[] serviceLines = scriptOutput.split( LINE_SEPARATOR ) ;

			var mergedLines = new ArrayList<String>( ) ;
			Arrays.stream( serviceLines )
					.filter( StringUtils::isNotEmpty )
					.forEach( line -> {

						if ( line.charAt( 0 ) != ' ' ) {

							mergedLines.add( line.replaceAll( "<", "" ).replaceAll( ">", "" ) ) ;

						} else if ( mergedLines.size( ) > 0 && line.contains( "inet " ) ) {

							mergedLines.set( mergedLines.size( ) - 1, mergedLines.get( mergedLines.size( ) - 1 )
									+ line ) ;

						}

					} ) ;
			;

			networkDevices = mergedLines ;

//			networkDevices = Arrays.stream( serviceLines )
//					.filter( StringUtils::isNotEmpty )
//					.filter( line -> {
//						if ( line.charAt( 0 ) != ' ') {
//							return true;
//						}
//						return false;
//					})
//					.map( line -> line.replaceAll("<", "").replaceAll(">", "") )
//					.collect( Collectors.toList( ) ) ;

			setCachedNetworkInterfaceCount( networkDevices.size( ) ) ;

		} catch ( IOException e ) {

			logger.info( "Failed to update: {} ", CSAP.buildCsapStack( e ) ) ;
			scriptOutput += ", reason: " + e.getMessage( ) + " type: " + e.getClass( ).getName( ) ;

		}

		csapApp.metrics( ).stopTimer( timer, "collect-os.linux-devices-ip" ) ;

		return networkDevices ;

	}

	private ConcurrentHashMap<String, String> ip_to_hostname = new ConcurrentHashMap<String, String>( ) ;

	private String[] hostSplit ( String fqdn ) {

		String host = fqdn ;
		if ( host == null )
			host = "" ;

		return host.split( Pattern.quote( "." ) ) ;

	}

	public String ipToHostName ( String hostIP ) {

		if ( hostIP == null ) {

			return "host_not_found" ;

		}

		if ( hostIP.equals( "127.0.0.1" ) ) {

			return "localhost" ;

		}

		if ( ! ip_to_hostname.containsKey( hostIP ) ) {

			String name = hostIP ;

			if ( csapApp.isKubernetesInstalledAndActive( )
					&& csapApp.getKubernetesIntegration( ).getSettings( ).isDnsLookup( ) ) {

				try {

					// name = InetAddress.getByName( hostIP ).getHostName() ;
					name = InetAddress.getByName( hostIP ).getCanonicalHostName( ) ;
					logger.info( "Resolved name: {} - {}", hostIP, name ) ;

					if ( ! hostIP.equals( name ) ) {

						name = hostSplit( name )[0] ;

					} else {

						// localhost will not resolve if found in /etc/hosts
						// check for hostname
						Stream<NetworkInterface> niStream = StreamSupport.stream(
								Spliterators.spliteratorUnknownSize( NetworkInterface
										.getNetworkInterfaces( ).asIterator( ), Spliterator.ORDERED ),
								false ) ;

						Optional<String> niAddress = niStream.flatMap( ni -> ni.getInterfaceAddresses( ).stream( ) )
								.map( address -> address.getAddress( ).getCanonicalHostName( ) )
								.filter( niHostName -> niHostName.contains( hostIP ) )
								.findFirst( ) ;

						if ( niAddress.isPresent( ) ) {

							logger.info( "Found IP: {}, setting host to: {}", hostIP, csapApp.getCsapHostName( ) ) ;
							name = csapApp.getCsapHostName( ) ;

						}

						// .collect( Collectors.joining( "\n\t" ) ) ;
					}

				} catch ( Exception e ) {

					logger.info( "Failed getting host name {}", CSAP.buildCsapStack( e ) ) ;

				}

			} else {

				logger.debug( "DNS resolution disabled - ip addresses will be shown in ui" ) ;

			}

			ip_to_hostname.put( hostIP, name ) ;

		}

		return ip_to_hostname.get( hostIP ) ;

	}

	private String resolveSocketHost ( String hostPort ) {

		var resolvedHost = hostPort ;

		if ( StringUtils.isNotEmpty( hostPort ) ) {

			var tokens = hostPort.split( ":" ) ;

			if ( tokens.length == 2 ) {

				resolvedHost = ipToHostName( tokens[0] ) + ":" + tokens[1] ;

			}

			if ( tokens.length == 5 ) {

				resolvedHost = ipToHostName( tokens[3] ) + ":" + tokens[4] ;

			}

		}

		return resolvedHost ;

	}

	public ArrayNode socketConnections ( boolean isSummarize ) {

		ArrayNode portReport = jsonMapper.createArrayNode( ) ;

		var timer = csapApp.metrics( ).startTimer( ) ;
		logger.debug( "Entered " ) ;

		Map<String, ObjectNode> summaryReportCache = new HashMap<>( ) ;

		List<String> collectionScripts = osCommands.getSystemNetworkPorts( ) ;

		String scriptOutput = "Failed to run" ;

		try {

			scriptOutput = osCommandRunner.runUsingRootUser( "ss-ports", collectionScripts ) ;
			scriptOutput = csapApp.check_for_stub( scriptOutput, "linux/network-ports-connections.txt" ) ;
			String[] serviceLines = scriptOutput.split( LINE_SEPARATOR ) ;

			Arrays.stream( serviceLines )
					.filter( StringUtils::isNotEmpty )
					.forEach( portLine -> {

						String[] columns = CsapCore.singleSpace( portLine ).split( " ", 6 ) ;

						if ( columns.length == 6 ) {

							var portId = columns[3] ;

							try {

								var labels = portId.split( ":" ) ;

								if ( labels.length > 1 ) {

									portId = labels[labels.length - 1] ;

								}

							} catch ( Exception e ) {

								logger.info( "failed splitting '{}'", portId ) ;

							}

							var peer = columns[4] ;

							var processName = columns[5] ;
							var users = processName.split( "\"", 3 ) ;

							if ( users.length == 3 ) {

								processName = users[1] ;

							}

							var peerProcessKey = processName + peer ;

							ObjectNode portDetails ;

							if ( isSummarize ) {

								if ( summaryReportCache.containsKey( portId ) ) {

									var portPrimary = summaryReportCache.get( portId ) ;
									var portSecondary = portPrimary.path( "related" ) ;

									if ( ! portSecondary.isArray( ) ) {

										portSecondary = portPrimary.putArray( "related" ) ;

									}

									portDetails = ( (ArrayNode) portSecondary ).addObject( ) ;

								} else if ( summaryReportCache.containsKey( peerProcessKey ) ) {

									var portPrimary = summaryReportCache.get( peerProcessKey ) ;
									var portSecondary = portPrimary.path( "related" ) ;

									if ( ! portSecondary.isArray( ) ) {

										portSecondary = portPrimary.putArray( "related" ) ;

									}

									portDetails = ( (ArrayNode) portSecondary ).addObject( ) ;

								} else {

									portDetails = portReport.addObject( ) ;
									summaryReportCache.put( portId, portDetails ) ;
									summaryReportCache.put( peerProcessKey, portDetails ) ;

								}

							} else {

								portDetails = portReport.addObject( ) ;

							}

							var details = columns[5] ;
							var pid = findFirstPidInSSDetails( details ) ;
							portDetails.put( "csapNoSort", true ) ;
							portDetails.put( "port", portId ) ;
							portDetails.put( "pid", pid ) ;
							portDetails.put( "processName", processName ) ;
							portDetails.put( "state", columns[0] ) ;
							portDetails.put( "recv-q", columns[1] ) ;
							portDetails.put( "send-q", columns[2] ) ;
							portDetails.put( "local", resolveSocketHost( columns[3] ) ) ;
							portDetails.put( "peer", resolveSocketHost( peer ) ) ;
							portDetails.put( "details", details ) ;

						}

						// portDetails.put( "line", portLine ) ;
					} ) ;

		} catch ( IOException e ) {

			logger.info( "Failed to update: {} ", CSAP.buildCsapStack( e ) ) ;
			scriptOutput += ", reason: " + e.getMessage( ) + " type: " + e.getClass( ).getName( ) ;

		}

		csapApp.metrics( ).stopTimer( timer, "collect-os.socket-connections" ) ;

		return portReport ;

	}

	private String findFirstPidInSSDetails ( String details ) {

		var pid = "" ;
		var pidIndex = details.indexOf( "pid=" ) ;

		if ( pidIndex != -1 ) {

			pid = details.substring( pidIndex + 4 ) ;
			pid = pid.substring( 0, pid.indexOf( "," ) ) ;

		}

		return pid ;

	}

	public ArrayNode socketListeners ( boolean isSummarize ) {

		ArrayNode portReport = jsonMapper.createArrayNode( ) ;

		var timer = csapApp.metrics( ).startTimer( ) ;
		logger.debug( "Entered " ) ;

		List<String> collectionScripts = osCommands.getSystemNetworkListenPorts( ) ;

		Map<String, ObjectNode> summaryReportCache = new HashMap<>( ) ;

		String scriptOutput = "Failed to run" ;

		try {

			scriptOutput = osCommandRunner.runUsingRootUser( "ss-ports", collectionScripts ) ;
			scriptOutput = csapApp.check_for_stub( scriptOutput, "linux/network-ports-listen.txt" ) ;
			String[] serviceLines = scriptOutput.split( LINE_SEPARATOR ) ;

			Arrays.stream( serviceLines )
					.filter( StringUtils::isNotEmpty )
					.forEach( portLine -> {

						String[] columns = CsapCore.singleSpace( portLine ).split( " ", 6 ) ;

						if ( columns.length == 6 ) {

							var portId = columns[3] ;

							try {

								var labels = portId.split( ":" ) ;

								if ( labels.length > 1 ) {

									portId = labels[labels.length - 1] ;

								}

							} catch ( Exception e ) {

								logger.info( "failed splitting '{}'", portId ) ;

							}

							ObjectNode portDetails ;

							if ( isSummarize ) {

								if ( summaryReportCache.containsKey( portId ) ) {

									var portPrimary = summaryReportCache.get( portId ) ;
									var portSecondary = portPrimary.path( "related" ) ;

									if ( ! portSecondary.isArray( ) ) {

										portSecondary = portPrimary.putArray( "related" ) ;

									}

									portDetails = ( (ArrayNode) portSecondary ).addObject( ) ;

								} else {

									portDetails = portReport.addObject( ) ;
									summaryReportCache.put( portId, portDetails ) ;

								}

							} else {

								portDetails = portReport.addObject( ) ;

							}

							var details = columns[5] ;
							var pid = findFirstPidInSSDetails( details ) ;

							portDetails.put( "csapNoSort", true ) ;
							portDetails.put( "port", portId ) ;
							portDetails.put( "pid", pid ) ;
							portDetails.put( "state", columns[0] ) ;
							portDetails.put( "recv-q", columns[1] ) ;
							portDetails.put( "send-q", columns[2] ) ;
							portDetails.put( "local", columns[3] ) ;
							portDetails.put( "peer", columns[4] ) ;
							portDetails.put( "details", details ) ;

						}

						// portDetails.put( "line", portLine ) ;
					} ) ;

		} catch ( IOException e ) {

			logger.info( "Failed to update: {} ", CSAP.buildCsapStack( e ) ) ;
			scriptOutput += ", reason: " + e.getMessage( ) + " type: " + e.getClass( ).getName( ) ;

		}

		csapApp.metrics( ).stopTimer( timer, "collect-os.socket-listeners" ) ;

		return portReport ;

	}

	private int cachedLinuxPackageCount = -1 ;

	public List<String> getLinuxPackages ( ) {

		var timer = csapApp.metrics( ).startTimer( ) ;

		logger.debug( "Entered " ) ;

		List<String> linuxRpms = Arrays.asList( "none" ) ;

		List<String> lines = osCommands.getSystemPackages( ) ;

		String scriptOutput = "Failed to run" ;

		try {

			scriptOutput = osCommandRunner.runUsingRootUser( "system rpm list", lines ) ;
			scriptOutput = csapApp.check_for_stub( scriptOutput, "linux/rpmResults.txt" ) ;
			String[] serviceLines = scriptOutput.split( LINE_SEPARATOR ) ;

			linuxRpms = Arrays.stream( serviceLines )
					.filter( StringUtils::isNotEmpty )
					.collect( Collectors.toList( ) ) ;

			setCachedLinuxPackageCount( linuxRpms.size( ) ) ;

		} catch ( IOException e ) {

			logger.info( "Failed to update: {} ", CSAP.buildCsapStack( e ) ) ;
			scriptOutput += ", reason: " + e.getMessage( ) + " type: " + e.getClass( ).getName( ) ;

		}

		csapApp.metrics( ).stopTimer( timer, "collect-os.linux-packages" ) ;

		return linuxRpms ;

	}

	public String runFile (
							File commandFile ) {

		logger.debug( "Entered " ) ;

		String scriptOutput = "Failed to run" ;

		scriptOutput = osCommandRunner.runUsingRootUser( commandFile, null ) ;
		scriptOutput = csapApp.check_for_stub( scriptOutput, "linux/kubectl-dashboard.txt" ) ;

		return scriptOutput ;

	}

	public String getMountPath ( String mountSource ) {

		logger.debug( "Entered " ) ;

		List<String> lines = osCommands.getNfsMountLocation( mountSource ) ;

		String scriptOutput = "Failed to run" ;

		try {

			scriptOutput = osCommandRunner.runUsingRootUser( "mount-location", lines ) ;
			scriptOutput = csapApp.check_for_stub( scriptOutput, "linux/disk-nfs-mount-location.txt" ) ;

		} catch ( IOException e ) {

			logger.info( "Failed to update: {} ", CSAP.buildCsapStack( e ) ) ;
			scriptOutput += ", reason: " + e.getMessage( ) + " type: " + e.getClass( ).getName( ) ;

		}

		return scriptOutput.trim( ).replaceAll( "[\\n\\t ]", "" ) ;

	}

	public String getLinuxPackageInfo (
										String packageName ) {

		logger.debug( "Entered " ) ;

		List<String> lines = osCommands.getSystemPackageDetails( packageName ) ;

		String scriptOutput = "Failed to run" ;

		try {

			scriptOutput = osCommandRunner.runUsingRootUser( "system rpm info", lines ) ;
			scriptOutput = csapApp.check_for_stub( scriptOutput, "linux/disk-nfs-mount-location.txt" ) ;

		} catch ( IOException e ) {

			logger.info( "Failed to update: {} ", CSAP.buildCsapStack( e ) ) ;
			scriptOutput += ", reason: " + e.getMessage( ) + " type: " + e.getClass( ).getName( ) ;

		}

		return scriptOutput ;

	}

	private int cachedLinuxServiceCount = -1 ;

	public List<String> getLinuxServices ( ) {

		var timer = csapApp.metrics( ).startTimer( ) ;
		logger.debug( "Entered " ) ;

		List<String> linuxSystemdServices = Arrays.asList( "none" ) ;

		List<String> lines = osCommands.getSystemServices( ) ;

		String scriptOutput = "Failed to run" ;

		try {

			scriptOutput = osCommandRunner.runUsingRootUser( "system services list", lines ) ;
			scriptOutput = csapApp.check_for_stub( scriptOutput, "linux/systemctl-services.txt" ) ;
			String[] serviceLines = scriptOutput.split( LINE_SEPARATOR ) ;

			linuxSystemdServices = Arrays.stream( serviceLines )
					.filter( StringUtils::isNotEmpty )
					.collect( Collectors.toList( ) ) ;

			setCachedLinuxServiceCount( linuxSystemdServices.size( ) ) ;

		} catch ( IOException e ) {

			logger.info( "Failed to update: {} ", CSAP.buildCsapStack( e ) ) ;
			scriptOutput += ", reason: " + e.getMessage( ) + " type: " + e.getClass( ).getName( ) ;

		}

		csapApp.metrics( ).stopTimer( timer, "collect-os.linux-services" ) ;

		return linuxSystemdServices ;

	}

	public String getLinuxServiceStatus (
											String serviceName ) {

		logger.debug( "Entered " ) ;

		List<String> lines = osCommands.getSystemServiceDetails( serviceName ) ;

		String scriptOutput = "Failed to run" ;

		try {

			scriptOutput = osCommandRunner.runUsingRootUser( "system services list", lines ) ;
			scriptOutput = csapApp.check_for_stub( scriptOutput, "linux/systemctl-status.txt" ) ;

		} catch ( IOException e ) {

			logger.info( "Failed to update: {} ", CSAP.buildCsapStack( e ) ) ;
			scriptOutput += ", reason: " + e.getMessage( ) + " type: " + e.getClass( ).getName( ) ;

		}

		return scriptOutput ;

	}

	public String removeNonGitFiles (
										File folderWithGit ) {

		logger.debug( "Entered " ) ;

		File verifyGit = new File( folderWithGit, ".git" ) ;

		if ( ! verifyGit.isDirectory( ) )
			return "Skipping - did not find git files" ;

		List<String> lines = List.of(
				"cd " + folderWithGit.getAbsolutePath( ),
				"if test -d .git ; then " + folderWithGit.getAbsolutePath( ),
				"\\rm --recursive --verbose --force * ; ",
				"fi" ) ;

		String scriptOutput = "Failed to run" ;

		try {

			scriptOutput = osCommandRunner.runUsingDefaultUser( "system services list", lines ) ;
			scriptOutput = csapApp.check_for_stub( scriptOutput, "linux/systemctl-status.txt" ) ;

		} catch ( IOException e ) {

			logger.info( "Failed to update: {} ", CSAP.buildCsapStack( e ) ) ;
			scriptOutput += ", reason: " + e.getMessage( ) + " type: " + e.getClass( ).getName( ) ;

		}

		return "command: " + lines + "\n output:" + scriptOutput ;

	}

	private int diskCount = 0 ;

	public int getDiskCount ( ) {

		return diskCount ;

	}

	private volatile CsapSimpleCache kubernetesJoinCache = null ;

	public synchronized ObjectNode getCachedKubernetesJoin ( ) {

		logger.debug( "Entered " ) ;

		if ( kubernetesJoinCache == null ) {

			kubernetesJoinCache = CsapSimpleCache.builder(
					60,
					TimeUnit.SECONDS,
					OsManager.class,
					"Kubernetes Join" ) ;
			kubernetesJoinCache.expireNow( ) ;

		}

		// Use cache
		if ( ! kubernetesJoinCache.isExpired( ) ) {

			logger.debug( "ReUsing kubernetesJoinCache" ) ;

			return (ObjectNode) kubernetesJoinCache.getCachedObject( ) ;

		}

		// Lets refresh cache
		logger.debug( "Refreshing kubernetesJoinCache" ) ;

		try {

			File joinFile = new File( csapApp.kubeletInstance( ).getWorkingDirectory( ),
					"/scripts/cluster-join-commands.sh" ) ;

			// String joinOutput = runFile( joinFile ) ;

			List<String> scriptLines = List.of( "desktop-only" ) ;

			if ( joinFile.exists( ) ) {

				scriptLines = Files.readAllLines( joinFile.toPath( ) ) ;

			}

			String joinOutput = osCommandRunner.runUsingDefaultUser( "kubernetes-join-commands", scriptLines ) ;

			joinOutput = csapApp.check_for_stub( joinOutput, "linux/kubeadm-join.txt" ) ;
			//
			logger.debug( "joinOutput: {}", joinOutput ) ;

			String[] joinOutputLines = joinOutput.split( LINE_SEPARATOR ) ;

			ObjectNode joinCommands = jsonMapper.createObjectNode( ) ;

			for ( var line : joinOutputLines ) {

				line = CsapCore.singleSpace( line ) ;

				if ( line.startsWith( "joinWorkerCommand" ) ) {

					joinCommands.put( "worker", line.substring( line.indexOf( ':' ) + 1 ).trim( ) ) ;

				} else if ( line.startsWith( "joinControlCommand" ) ) {

					joinCommands.put( "master", line.substring( line.indexOf( ':' ) + 1 ).trim( ) ) ;

				}

			}

			if ( joinCommands.has( "master" ) && joinCommands.has( "worker" ) ) {

				logger.info( "primary kubernetes master resolved join; next master will join at least 60s from now" ) ;
//				kubernetesJoinCache.reset( joinCommands ) ;
				kubernetesJoinCache.reset( jsonMapper.createObjectNode( ) ) ;
				return joinCommands ;

			} else {

				logger.warn( "did not find master and work in output: {}", joinOutput ) ;
				return joinCommands ;

			}

		} catch ( Exception e ) {

			logger.error( "Failed to write output: {}", CSAP.buildCsapStack( e ) ) ;

		}

		return (ObjectNode) kubernetesJoinCache.getCachedObject( ) ;

	}

	private volatile CsapSimpleCache diskStatisticsCache = null ;

	public ObjectNode getCachedFileSystemInfo ( ) {

		logger.debug( "Entered " ) ;

		if ( diskStatisticsCache == null ) {

			diskStatisticsCache = CsapSimpleCache.builder(
					30,
					TimeUnit.SECONDS,
					OsManager.class,
					"Disk Statistics" ) ;
			diskStatisticsCache.expireNow( ) ;

		}

		// Use cache
		if ( ! diskStatisticsCache.isExpired( ) ) {

			logger.debug( "\n\n***** ReUsing  DF cache   *******\n\n" ) ;

			return (ObjectNode) diskStatisticsCache.getCachedObject( ) ;

		}

		// Lets refresh cache
		logger.debug( "\n\n***** Refreshing df cache   *******\n\n" ) ;

		try {

			// run as root to pick up docker and kubelet filesystems
			String dfResult = osCommandRunner.runUsingRootUser( "df-collect", osCommands.getDiskUsageSystem( ) ) ;

			dfResult = csapApp.check_for_stub( dfResult, "linux/df-run-as-root.txt" ) ;

			logger.debug( "dfResult: {}", dfResult ) ;

			String[] dfLines = dfResult.split( LINE_SEPARATOR ) ;

			ObjectNode svcToStatMap = jsonMapper.createObjectNode( ) ;

			int lastCount = 0 ;

			for ( int i = 0; i < dfLines.length; i++ ) {

				String curline = dfLines[i].trim( ) ;
				String[] cols = curline.split( " ", 7 ) ;

				if ( cols.length < 7 || ! cols[6].startsWith( "/" ) ) {

					logger.debug( "Skipping line: {}", curline ) ;
					continue ;

				}

				ObjectNode fsNode = svcToStatMap.putObject( cols[6] ) ;

				fsNode.put( "dev", cols[0] ) ;
				fsNode.put( "type", cols[1] ) ;
				fsNode.put( "sized", cols[2] ) ;
				fsNode.put( "used", cols[3] ) ;
				fsNode.put( "avail", cols[4] ) ;
				fsNode.put( "usedp", cols[5] ) ;
				fsNode.put( "mount", cols[6] ) ;
				lastCount++ ;

			}

			diskCount = lastCount ;

			diskStatisticsCache.reset( svcToStatMap ) ;

		} catch ( Exception e ) {

			logger.error( "Failed to write output: {}", CSAP.buildCsapStack( e ) ) ;

		}

		return (ObjectNode) diskStatisticsCache.getCachedObject( ) ;

	}

	long lastSummaryTimeStamp = 0 ;
	ObjectNode summaryCacheNode ;

	public ObjectNode getHostSummary ( ) {

		// Use cache
		if ( System.currentTimeMillis( ) - lastSummaryTimeStamp < 1000 * 90 ) {

			logger.debug( "\n\n***** ReUsing  Summary cache   *******\n\n" ) ;

			return summaryCacheNode ;

		}

		ObjectNode summaryNode = jsonMapper.createObjectNode( ) ;
		//
		List<String> parmList = Arrays.asList( "bash", "-c", "cat /etc/redhat-release" ) ;
		String commandResult = osCommandRunner.executeString( parmList, new File( "." ), null, null,
				600, 10, null ) ;

		logger.debug( "redhat release commandResult: {} ", commandResult ) ;

		commandResult = OsCommandRunner.trimHeader( commandResult ) ;
		// if (psResult.contains(LINE_SEPARATOR))
		// psResult = psResult.substring(
		// psResult.indexOf(LINE_SEPARATOR +1 ));
		summaryNode.put( "redhat", commandResult ) ;

		// w provides uptime and logged in users
		// parmList = Arrays.asList("bash", "-c", "w");
		parmList = Arrays.asList( "bash", "-c", "uptime" ) ;
		commandResult = osCommandRunner.executeString( parmList, new File( "." ), null, null, 600, 10,
				null ) ;

		logger.debug( "uptime commandResult: {} ", commandResult ) ;

		commandResult = OsCommandRunner.trimHeader( commandResult ) ;
		summaryNode.put( "uptime", commandResult ) ;

		//
		parmList = Arrays.asList( "bash", "-c", "uname -sr" ) ;
		commandResult = osCommandRunner.executeString( parmList, new File( "." ), null, null, 600, 10,
				null ) ;

		logger.debug( "uname commandResult: {} ", commandResult ) ;

		commandResult = OsCommandRunner.trimHeader( commandResult ) ;
		summaryNode.put( "uname", commandResult ) ;

		//
		var dfResult = osCommandRunner.executeString(
				osCommands.getDiskUsageAbout( ),
				new File( "." ), null, null, 600, 10,
				null ) ;

		dfResult = csapApp.check_for_stub( dfResult, "linux/df-about.txt" ) ;

		logger.debug( "df commandResult: {} ", dfResult ) ;

		dfResult = OsCommandRunner.trimHeader( dfResult ) ;

		summaryNode.put( "df", dfResult ) ;

		summaryCacheNode = summaryNode ;
		lastSummaryTimeStamp = System.currentTimeMillis( ) ;
		return summaryNode ;

	}

	/**
	 * 
	 * AgentStatus: core method for accessing host state
	 * 
	 */
	@Timed ( "csap.host-status" )
	public JsonNode getHostRuntime ( )
		throws IOException ,
		JsonParseException ,
		JsonMappingException {

		// update services status if needed
		checkForProcessStatusUpdate( ) ;

		// var agent = csapApp.flexFindFirstInstance( "csap-agent" ) ;
		// logger.info( "agent rss: {}", agent.getDefaultContainer().getRssMemory() );

		// updated service artifacts if needed
		csapApp.updateServiceTimeStamps( ) ;

		ObjectNode hostRuntime = jsonMapper.createObjectNode( ) ;

		// add time stamp
		Format tsFormater = new SimpleDateFormat( "HH:mm:ss" ) ;
		hostRuntime.put( "timeStamp", tsFormater.format( new Date( ) ) ) ;

		if ( serviceManager != null ) {

			hostRuntime.put( "serviceOpsQueue", serviceManager.getOpsQueued( ) ) ;

		}

		ObjectNode hostMetrics = csapApp.healthManager( ).build_host_status_using_cached_data( ) ;

		try {

			var hostCollector = csapApp.metricManager( ).getOsSharedCollector( ) ;

			if ( hostCollector != null ) {

				var ioReport = hostMetrics.putObject( "network" ) ;
				ioReport.put( "network-sent-mb", hostCollector.latestNetworkTransmitted( ) ) ;
				ioReport.put( "network-receive-mb", hostCollector.latestNetworkReceived( ) ) ;
				ioReport.put( "sockets-active", hostCollector.latestNetworkConnections( ) ) ;
				ioReport.put( "sockets-close-wait", hostCollector.latestNetworkWait( ) ) ;
				ioReport.put( "sockets-time-wait", hostCollector.latestNetworkTimeWait( ) ) ;

			}

			hostMetrics.put( "du", collectCsapFolderDiskAndCache( ) ) ;

			hostMetrics.set( IO_UTIL_IN_PERCENT, device_utilization( ) ) ;
			hostMetrics.set( "ioTotalInMb", diskReport( ) ) ;

			hostMetrics.set( "vmLoggedIn", getVmLoggedIn( ) ) ;

			ObjectNode dfNode = getCachedFileSystemInfo( ) ;
			ObjectNode dfFilterNode = jsonMapper.createObjectNode( ) ;

			if ( dfNode != null ) {

				for ( JsonNode node : dfNode ) {

					dfFilterNode.put( node.path( "mount" ).asText( ), node
							.path( "usedp" ).asText( ) ) ;

				}

				hostMetrics.set( "df", dfFilterNode ) ;

			}

			if ( getReadOnlyFs( ) != null ) {

				hostMetrics.set( "readOnlyFS", getReadOnlyFs( ) ) ;

			}

			if ( getMemoryAvailbleLessCache( ) < 0 ) {

				logger.error( "Get mem is invalid: " + getCachedMemoryMetrics( ) ) ;
				hostMetrics.put( "memoryAggregateFreeMb", -1 ) ;

			} else {

				hostMetrics.put( "memoryAggregateFreeMb",
						getMemoryAvailbleLessCache( ) ) ;

			}

		} catch ( Exception e ) {

			logger.warn( "Failed to get runtime time info", e ) ;

		}

		hostRuntime.set( HostKeys.hostStats.jsonId, hostMetrics ) ;

		try {

			hostRuntime.set( HostKeys.unregisteredServices.jsonId, null ) ;

			hostRuntime.set( HostKeys.services.jsonId, build_averaged_service_statistics( ) ) ;

			hostRuntime.set( HostKeys.lastCollected.jsonId, null ) ;

			if ( csapApp.rootProjectEnvSettings( ).areMetricsConfigured( ) ) {

				hostRuntime.set( HostKeys.lastCollected.jsonId, buildRealTimeCollectionReport( ) ) ;

			}

			hostRuntime.set( HostKeys.unregisteredServices.jsonId, jsonMapper.convertValue(
					findUnregisteredContainerNames( ), ArrayNode.class ) ) ;

		} catch ( Exception e ) {

			logger.error( "Failed getting collection average: {}", CSAP.buildCsapStack( e ) ) ;

		}

		return hostRuntime ;

	}

	public List<String> findUnregisteredContainerNames ( ) {

		return ContainerProcess.buildUnregisteredSummaryReport( processMapper.getLatestDiscoveredContainers( ) ) ;

	}

	private ObjectNode build_averaged_service_statistics ( ) {

		logger.debug( "got here" ) ;

		final int serviceSampleSize = csapApp.rootProjectEnvSettings( ).getLimitSamples( ) ;

		ObjectNode cached_service_statistics = load_cached_service_statistics( serviceSampleSize ) ;

		// note that admin api also uses this method - but average of data
		// cannot be used.

		ObjectNode servicesJson = jsonMapper.createObjectNode( ) ;
		csapApp.getActiveProject( )
				.getServicesWithKubernetesFiltering( csapApp.getCsapHostName( ) )
				.forEach( serviceInstance -> {

					String serviceId = serviceInstance.getServiceName_Port( ) ;

					// get the latest collection - use it as the default collection value.
					ObjectNode latestServiceStats = serviceInstance.buildRuntimeState( ) ;

					// handle memory

					logger.debug( "Before: {}, stats: {}", serviceId, latestServiceStats.toString( ) ) ;
					// if ( serviceInstance.getName().equals( "csap-agent" ) ) {
					// logger.info( "sample count: {} before csap-agent stats: {}",
					// numSamplesToTake, CSAP.jsonPrint( latestServiceStats ) ) ;
					// }

					var needToHandleRssMbConversion = true ;

					var numSamplesToTake = serviceSampleSize ;

					if ( serviceInstance.isAggregateContainerMetrics( ) ) {

						// os metrics are aggregated across all containers - not possible to pull and
						// average
						numSamplesToTake = 1 ;

					}

					if ( cached_service_statistics != null && ( numSamplesToTake > 1 ) ) {

						var containerStatuses = serviceInstance.getContainerStatusList( ) ;

						for ( int containerIndex = 0; containerIndex < containerStatuses.size( ); containerIndex++ ) {

							var containerLabel = containerStatuses.get( containerIndex ).getContainerLabel( ) ;

							if ( StringUtils.isNotEmpty( containerLabel ) && containerLabel.equals(
									"kube-apiserver" ) ) {

								logger.debug( "containerLabel: {}, numSamplesToTake: {}", containerLabel,
										numSamplesToTake ) ;

							}
							// boolean isKubernetes = serviceStatus.path( ClusterType.CLUSTER_TYPE ).asText(
							// "not" ).equals(
							// ClusterType.KUBERNETES.getJson() );

							// String serviceKey = serviceName;
							// if ( isKubernetes ) {
							// serviceKey += "-" + (i + 1);
							// kubernetes_services.put( serviceKey, serviceName ); // stored for cleanup
							// }

							logger.debug( "Using rolling averages for service meterics." ) ;

							for ( OsProcessEnum collectedMetric : OsProcessEnum.values( ) ) {

								if ( collectedMetric == OsProcessEnum.diskUsedInMb ) {

									// Disk can get large very quickly. Always report
									// the last collected
									continue ;

								}

								String statsPath = "/" + HostCollector.DATA_JSON + "/" + collectedMetric.value + "_"
										+ serviceInstance.getPerformanceId( ) ;

								if ( serviceInstance.is_cluster_kubernetes( ) ) {

									statsPath += "-" + ( containerIndex + 1 ) ;

								}

								JsonNode serviceData = cached_service_statistics.at( statsPath ) ;

								if ( ! serviceData.isArray( ) ) {

									logger.debug( "{} cached data not found", serviceInstance.getName( ) ) ;

								} else {
									// if ( serviceInstance.getName().equals( "csap-agent" ) && collectedMetric ==
									// OsProcessEnum.rssMemory ) {
									// logger.info( "csap-agent rss cached: {}", CSAP.jsonPrint( serviceData ) ) ;
									// }

									long total = 0 ;

									for ( int i = 0; i < serviceData.size( ); i++ ) {

										total += serviceData.get( i ).asLong( ) ;

									}

									long average = ( total / serviceData.size( ) ) ;
									logger.debug( "{} Total: {} , Average: {}", collectedMetric.value, total,
											average ) ;
									// latestServiceStats.put( os.value, average );
									JsonNode container = latestServiceStats.at( ContainerState.containerPath(
											containerIndex ) ) ;

									if ( container.isObject( ) ) {

										if ( collectedMetric == OsProcessEnum.rssMemory ) {

											needToHandleRssMbConversion = false ;

										}

										( (ObjectNode) container ).put( collectedMetric.value, average ) ;
										( (ObjectNode) container ).put( HostKeys.numberSamplesAveraged.jsonId,
												serviceData.size( ) ) ;

									} else {

										latestServiceStats.put( "error", "Did not find containers" ) ;

									}

								}

							}

						}

					}

					if ( needToHandleRssMbConversion ) {

						logger.debug( "Updating rss to mb: collection is stored in kb" ) ;

						for ( int containerIndex = 0; containerIndex < serviceInstance.getContainerStatusList( )
								.size( ); containerIndex++ ) {

							JsonNode container = latestServiceStats.at( ContainerState.containerPath(
									containerIndex ) ) ;
							( (ObjectNode) container ).put( OsProcessEnum.rssMemory.value,
									container.path( OsProcessEnum.rssMemory.value ).asLong( -1 ) / 1024 ) ;

						}

					}

					logger.debug( "After: {}, stats: {}", serviceId, latestServiceStats.toString( ) ) ;
					// if ( serviceInstance.getName().equals( "csap-agent" ) ) {
					// logger.info( "after csap-agent stats: {}", CSAP.jsonPrint( latestServiceStats
					// ) ) ;
					// }

					servicesJson.set( serviceId, latestServiceStats ) ;

				} ) ;

		return servicesJson ;

	}

	private ObjectNode load_cached_service_statistics (
														final int numSamplesToTake ) {

		ObjectNode cached_service_statistics = null ;

		if ( ! csapApp.isAdminProfile( ) ) {

			OsProcessCollector osProcessCollector ;

			if ( csapApp.isJunit( ) && csapApp.metricManager( ).getOsProcessCollector( -1 ) == null ) {

				osProcessCollector = new OsProcessCollector( csapApp,
						this, 30, false ) ;

				logger.warn( "\n\n\n JUNIT DETECTED - injecting stubbed OsProcessCollector" ) ;

				// osProcessCollector.testCollection();
			} else {

				osProcessCollector = csapApp.metricManager( ).getOsProcessCollector( -1 ) ;

			}

			cached_service_statistics = osProcessCollector
					.getCollection( numSamplesToTake, 0, OsProcessCollector.ALL_SERVICES ) ;

		}

		logger.debug( "Adding: {} ", cached_service_statistics ) ;
		return cached_service_statistics ;

	}

	public String getLastCollected (
										ServiceInstance service ,
										String searchKey ) {

		long result = 0 ;

		try {

			ServiceCollector applicationCollector = csapApp.metricManager( ).getServiceCollector( csapApp
					.metricManager( ).firstJavaCollectionInterval( ) ) ;

			String[] serviceArray = {
					service.getServiceName_Port( )
			} ;
			ObjectNode serviceData = applicationCollector.buildCollectionReport( false, serviceArray, 1, 0,
					"custom" ) ;
			logger.debug( "Collected: {}", serviceData ) ;

			result = serviceData.get( "data" ).get( searchKey ).get( 0 ).asLong( ) ;

		} catch ( Exception e ) {

			logger.warn( "{} Did not find: {}, \n {}",
					service.getServiceName_Port( ), searchKey, CSAP.buildCsapStack( e ) ) ;

		}

		return Long.toString( result ) ;

	}

	// For all services.
	public ObjectNode buildLatestCollectionReport ( ) {

		var collectionReport = jsonMapper.createObjectNode( ) ;

		if ( ! csapApp.isAdminProfile( ) && csapApp.rootProjectEnvSettings( ).areMetricsConfigured( ) ) {

			OsSharedResourcesCollector osSharedCollector = csapApp.metricManager( ).getOsSharedCollector(
					csapApp.metricManager( ).firstHostCollectionInterval( ) ) ;

			if ( osSharedCollector != null ) {

				var osSharedReport = osSharedCollector.buildCollectionReport( false, null, 1, 0 ) ;
				collectionReport.set( MetricCategory.osShared.json( ), osSharedReport.path( "data" ) ) ;

			} else {

				collectionReport.put( MetricCategory.osShared.json( ), "collector-not-available" ) ;

			}

			OsProcessCollector osProcessCollector = csapApp.metricManager( )
					.getOsProcessCollector( csapApp.metricManager( ).firstServiceCollectionInterval( ) ) ;
			// fullCollectionJson.set( MetricCategory.osProcess.json(),
			// serviceCollector.getCSVdata(
			// false, serviceNames, 1, 0 ).get( "data" ) );
			// this will grab all entries stored

			if ( osProcessCollector != null ) {

				// get all services, with the last collected item
				collectionReport.set( MetricCategory.osProcess.json( ),
						osProcessCollector.buildCollectionReport( false,
								osProcessCollector.getAllCollectedServiceNames( ),
								1, 0 ).path( "data" ) ) ;

			} else {

				collectionReport.set( MetricCategory.osProcess.json( ), null ) ;

			}

			ServiceCollector serviceCollector = csapApp.metricManager( )
					.getServiceCollector( csapApp.metricManager( ).firstJavaCollectionInterval( ) ) ;

			if ( serviceCollector != null ) {

				String[] javaServices = serviceCollector.getJavaServiceNames( ) ;

				ObjectNode latestServiceReport = serviceCollector.buildCollectionReport( false, javaServices, 1, 0 ) ;

				if ( latestServiceReport.has( "data" ) ) {

					collectionReport.set( MetricCategory.java.json( ), latestServiceReport.get( "data" ) ) ;

				} else {

					logger.warn( "java  collection does not contain data: {}. \n\t Services: {}",
							latestServiceReport.toString( ), Arrays.asList( javaServices ) ) ;
					collectionReport.set( MetricCategory.java.json( ), latestServiceReport ) ;

				}

				ObjectNode serviceReports = collectionReport.putObject( MetricCategory.application.json( ) ) ;

				csapApp.getActiveProject( )
						.getServicesOnHost( csapApp.getCsapHostName( ) )
						.filter( ServiceInstance::hasServiceMeters )
						.forEach( serviceInstance -> {

							String[] serviceArray = {
									serviceInstance.getName( )
							} ;
							serviceReports.set( serviceInstance.getName( ),
									serviceCollector.buildCollectionReport( false, serviceArray, 1, 0, "custom" )
											.get( "data" ) ) ;

						} ) ;

				logger.debug( "serviceReports: {}", serviceReports ) ;

			} else {

				logger.warn( "serviceCollector is null - verify configuration in definition" ) ;

			}

		} else {

			collectionReport.put( "warning", "VM is in manager mode" ) ;

		}

		return collectionReport ;

	}

	/**
	 * Gets the values for the real time meters defined in Application.json
	 *
	 * @return
	 */
	public ObjectNode buildRealTimeCollectionReport ( ) {

		ObjectNode configCollection = jsonMapper.createObjectNode( ) ;

		if ( ! csapApp.isAdminProfile( ) ) {

			ObjectNode latestCollectionReport = buildLatestCollectionReport( ) ;
			logger.debug( "latestCollectionReport: {}", CSAP.jsonPrint( latestCollectionReport ) ) ;

			ObjectNode osSharedReport = configCollection.putObject( MetricCategory.osShared.json( ) ) ;
			ObjectNode processMeterReport = configCollection.putObject( MetricCategory.osProcess.json( ) ) ;
			ObjectNode javaMeterReport = configCollection.putObject( MetricCategory.java.json( ) ) ;
			ObjectNode applicationReport = configCollection.putObject( MetricCategory.application.json( ) ) ;
			ArrayNode realTimeMeterDefinitions = csapApp.rootProjectEnvSettings( ).getRealTimeMeters( ) ;

			for ( JsonNode realTimeMeterDefn : realTimeMeterDefinitions ) {

				MetricCategory performanceCategory = MetricCategory.parse( realTimeMeterDefn ) ;
				String serviceName = performanceCategory.serviceName( realTimeMeterDefn ) ;

				try {

					String id = realTimeMeterDefn.get( "id" ).asText( ) ;
					String[] idComponents = id.split( Pattern.quote( "." ) ) ;
					String category = idComponents[0] ;
					String attribute = idComponents[1] ;
					logger.debug( "collector: {}, attribute: {} ", category, attribute ) ;
					// vm. process. jmxCommon. jmxCustom.Service.var
					// process.topCpu_CsAgent
					// process.topCpu_test-k8s-csap-reference

					switch ( performanceCategory ) {

					case osShared:

						var latestOsSharedReport = latestCollectionReport.path( category ) ;

						// logger.debug( "latestOsSharedReport: {}", CSAP.jsonPrint(
						// latestOsSharedReport ) ) ;

						if ( attribute.equals( "cpu" ) ) {

							int totalCpu = csapApp.metricManager( ).getLatestCpuUsage( ) ;
							osSharedReport.put( "cpu", totalCpu ) ;

						} else if ( attribute.equals( "coresActive" ) ) {

							int totalCpu = csapApp.metricManager( ).getLatestCpuUsage( ) ;

							double coresActive = totalCpu * csapApp.healthManager( ).getCpuCount( ) / 100D ;
							osSharedReport.put( "coresActive", CSAP.roundIt( coresActive, 2 ) ) ;
//								osSharedReport.put( "cpu", totalCpu ) ;
//								osSharedReport.put( "cores", csapApp.healthManager().getCpuCount() ) ;

						} else {

							osSharedReport.put( attribute,
									latestOsSharedReport.at( "/" + attribute + "/0" ).asDouble( 0 ) ) ;

						}

						break ;

					case osProcess:
						String csapId[] = attribute.split( "_" ) ;
						String osStat = csapId[0] ;

						addOsProcessMeterReport( serviceName, osStat,
								latestCollectionReport.path( category ), attribute, processMeterReport ) ;

						break ;

					case java:
						// jmxCommon.sessionsActive_test-k8s-csap-reference
						String javaId[] = attribute.split( "_" ) ;
						String javaStat = javaId[0] ;

						buildJavaMeterReport( serviceName, javaStat, attribute,
								latestCollectionReport.path( category ),
								javaMeterReport ) ;

						break ;

					case application:

						attribute = idComponents[2] ;
						String qualifiedName = attribute ;

						ServiceInstance service = csapApp.flexFindFirstInstanceCurrentHost( serviceName ) ;
						if ( service == null ) {

							logger.debug( "Unable to locate: {}. Assumed not deployed on host {}", serviceName,
									CSAP.jsonPrint( realTimeMeterDefn ) ) ;
							continue ;

						}

						var podIndex = 0 ;
						for ( ContainerState csapContainer : service.getContainerStatusList( ) ) {

							if ( service.is_cluster_kubernetes( ) ) {

								podIndex++ ;
								qualifiedName = attribute + "_" + serviceName + "-" + podIndex ;

							}

							logger.debug( "{} podIndex: {} qualifiedName: {}", serviceName, podIndex, qualifiedName ) ;

							if ( ! latestCollectionReport.get( category ).has( serviceName ) ) {

								continue ;

							}

							if ( ! latestCollectionReport.get( category ).get( serviceName ).has( qualifiedName ) ) {

								continue ;

							}

							if ( ! applicationReport.has( serviceName ) ) {

								applicationReport.putObject( serviceName ) ;

							}

							ObjectNode serviceReport = (ObjectNode) applicationReport.path( serviceName ) ;

							int hostTotal = latestCollectionReport.path( category ).path( serviceName ).path(
									qualifiedName )
									.get( 0 ).asInt( 0 ) ;

							hostTotal += serviceReport.path( attribute ).asInt( 0 ) ;

							serviceReport.put( attribute, hostTotal ) ;

						}
						break ;

					default:
						logger.warn( "Unexpected category type: {}", category ) ;

					}

				} catch ( Exception e ) {

					logger.error( "Failed parsing: {}, \n {}",
							realTimeMeterDefn,
							CSAP.buildCsapStack( e ) ) ;

				}

			}

		} else {

			configCollection.put( "warning", "VM is in manager mode" ) ;

		}

		return configCollection ;

	}

	private void buildJavaMeterReport (
										String javaServiceName ,
										String javaStat ,
										String attribute ,
										JsonNode javaCollectionReport ,
										ObjectNode filteredReport ) {

		logger.debug( "{} javaStat: {}, attribute: {}", javaServiceName, javaStat, attribute ) ;

		if ( MetricCategory.isAllServices( javaServiceName ) ) {

			Iterable<Map.Entry<String, JsonNode>> iterable = ( ) -> javaCollectionReport.fields( ) ;
			int allInstanceTotal = StreamSupport.stream( iterable.spliterator( ), false )
					.filter( osEntry -> osEntry.getKey( ).startsWith( javaStat ) )
					.mapToInt( osEntry -> osEntry.getValue( ).get( 0 ).asInt( 0 ) )
					.sum( ) ;
			logger.debug( "Total for {} is {}", javaServiceName, allInstanceTotal ) ;
			filteredReport.put( attribute, allInstanceTotal ) ;

		} else {

			ServiceInstance serviceInstance = csapApp.findServiceByNameOnCurrentHost( javaServiceName ) ;

			if ( serviceInstance != null ) {

				boolean foundData = false ;
				int allInstanceTotal = 0 ;

				if ( serviceInstance.is_cluster_kubernetes( ) ) {

					for ( int container = 1; container <= serviceInstance.getContainerStatusList( )
							.size( ); container++ ) {

						ContainerState containerState = serviceInstance.getContainerStatusList( ).get( container - 1 ) ;
						String stat_serviceName_id = javaStat + "_" + serviceInstance.getName( ) + "-" + container ;

						if ( containerState.isActive( ) && javaCollectionReport.has( stat_serviceName_id ) ) {

							foundData = true ;
							allInstanceTotal += javaCollectionReport.path( stat_serviceName_id ).path( 0 ).asInt( 0 ) ;

						}

					}

				} else {

					String javaIdWithPort = javaStat + "_" + serviceInstance.getName( ) ;

					if ( javaCollectionReport.has( javaIdWithPort ) ) {

						foundData = true ;
						allInstanceTotal += javaCollectionReport.path( javaIdWithPort ).path( 0 ).asInt( 0 ) ;

					}

				}

				if ( foundData ) {

					filteredReport.put( attribute, allInstanceTotal ) ;

				} else {

					logger.debug( "Did not find a match for {} on host", attribute ) ;

				}

			}

		}

		return ;

	}

	private void addOsProcessMeterReport (
											String csapServiceName ,
											String osStat ,
											JsonNode processCollectionReport ,
											String metric_servicename ,
											ObjectNode processMeterReport ) {

		// process.topCpu_CsAgent
		// process.topCpu_test-k8s-csap-reference

		if ( processCollectionReport.has( metric_servicename ) ) {

			// typical
			processMeterReport.put( metric_servicename,
					processCollectionReport.get( metric_servicename ).path( 0 ).asInt( 0 ) ) ;

		} else {

			if ( MetricCategory.isAllServices( csapServiceName ) ) {

				Iterable<Map.Entry<String, JsonNode>> iterable = ( ) -> processCollectionReport.fields( ) ;
				int allInstanceTotal = StreamSupport.stream( iterable.spliterator( ), false )
						.filter( osEntry -> osEntry.getKey( ).startsWith( osStat ) )
						.mapToInt( osEntry -> osEntry.getValue( ).path( 0 ).asInt( 0 ) )
						.sum( ) ;
				logger.debug( "Total for {} is {}", csapServiceName, allInstanceTotal ) ;
				processMeterReport.put( metric_servicename, allInstanceTotal ) ;

			} else {

				// handle kubernetes with multiple instances
				boolean isFoundOneOrMoreRunning = false ;
				int allInstanceTotal = 0 ;

				// kubernetes
				ServiceInstance serviceInstance = csapApp.findServiceByNameOnCurrentHost( csapServiceName ) ;

				if ( serviceInstance != null && serviceInstance.is_cluster_kubernetes( ) ) {

					for ( int container = 1; container <= serviceInstance.getContainerStatusList( )
							.size( ); container++ ) {

						ContainerState containerState = serviceInstance.getContainerStatusList( ).get( container - 1 ) ;
						String stat_serviceName_id = osStat + "_" + serviceInstance.getName( ) + "-" + container ;

						if ( containerState.isActive( ) && processCollectionReport.has( stat_serviceName_id ) ) {

							isFoundOneOrMoreRunning = true ;
							allInstanceTotal += processCollectionReport.path( stat_serviceName_id ).path( 0 ).asInt(
									0 ) ;

						}

					}

				}

				// @formatter:off
//				int			allInstanceTotal	= csapApp.getServicesOnHost().stream()
//					.filter( serviceinstance -> serviceinstance.getServiceName().matches( csapServiceName ) )
//					.mapToInt( serviceinstance -> {
//						
//						
//							String stat_serviceName_id = osStat + "_" + serviceinstance.getServiceName() + "_"
//									+ serviceinstance.getPort() ;
//
//							// logger.info("Checking for: {}", serviceAndPort);
//							if ( !processCollectionReport.has( stat_serviceName_id ) ) {
//								// logger.warn( "Did not find attribute: {}",
//								// attribute );
//								return 0 ;
//							}
//							isFoundAMatch.set( true );
//							int lastCollectedForPort = processCollectionReport.get( stat_serviceName_id )
//								.get( 0 ).asInt( 0 ) ;
//							return lastCollectedForPort ;
//							
//						} )
//					.sum() ;

				if ( isFoundOneOrMoreRunning ) {

					processMeterReport.put( metric_servicename, allInstanceTotal ) ;

				} else {

					logger.debug( "Did not find a match for {}", metric_servicename ) ;

				}

				// @formatter:on
			}

		}

		return ;

	}

	public ServiceOsManager getServiceManager ( ) {

		return serviceManager ;

	}

	public static String LINE_SEPARATOR = System.getProperty( "line.separator" ) ;

	// triggered after deployment activities
	private void scheduleDiskUsageCollection ( ) {

		// rawDuAndDfLinuxOutput = "";
		if ( ! csapApp.isAdminProfile( ) ) {

			if ( ! intenseOsCommandExecutor.isShutdown( ) ) {

				try {

					if ( logger.isDebugEnabled( ) ) {

						logger.debug( "{}", CSAP.buildCsapStack( new Exception( "scheduling cache refresh" ) ) ) ;

					}

					intenseOsCommandExecutor.execute( ( ) -> collect_disk_and_linux_package( ) ) ;

				} catch ( Exception e ) {

					logger.warn( "Failed to scheduler os command collection {}", CSAP.buildCsapStack( e ) ) ;

				}

			} else {

				logger.info(
						"Skipping due to intenseOsCommandExecutor is not running, assuming shutdown in progress" ) ;

			}

		}

	}

	private volatile String diskUsageForServicesCache = "" ;

	/**
	 * 
	 * ==== Disk is collected for both services and core file systems
	 * 
	 * 
	 */

	boolean printDiskOnce = true ;

	private void collect_disk_and_linux_package ( ) {

		var timer = csapApp.metrics( ).startTimer( ) ;
		// Updates service count
		getLinuxServices( ) ;

		// Updates package count
		getLinuxPackages( ) ;

		// Updates network cout
		networkInterfaces( ) ;

		// Updates port count
		// socketConnections() ;

		var diskTimer = csapApp.metrics( ).startTimer( ) ;

		logger.debug( "\n\n updating caches \n\n" ) ;

		StringBuilder diskCollection ;

		try {

			diskCollection = new StringBuilder( "\n" ) ;

			// String[] diskUsageScript = diskUsageScriptTemplate.clone();
			var servicePaths = csapApp
					.servicesOnHost( )
					.map( ServiceInstance::getDiskUsagePath )
					.distinct( )
					.collect( Collectors.joining( " " ) ) ;

			if ( printDiskOnce ) {

				logger.info( "Service disk locations\n{}", servicePaths ) ;

			}

			List<String> diskUsageScript = osCommands.getServiceDiskUsage( servicePaths ) ;

			// Step 1 - collect disk usage under csap processing. Some files may
			// be privelged - use root if available
			diskCollection.append( osCommandRunner.runUsingRootUser( "service-disk-usage", diskUsageScript ) ) ;

			// Step 2 - collect disk usage use df output, services can specify
			// device. Use default user to avoid seeing docker mounts
			List<String> diskFileSystemScript = osCommands.getServiceDiskUsageDf( ) ;
			diskCollection.append( osCommandRunner.runUsingDefaultUser( "service-disk-usage-df",
					diskFileSystemScript ) ) ;

			// Step 3 - disk usage from docker
			diskCollection.append( collectDockerDiskUsage( ) ) ;

			logger.debug( "service-disk-filesystem: {} \n\n diskFileSystemScript: {} \n\n diskCollection: {}",
					diskUsageScript,
					diskFileSystemScript,
					diskCollection.toString( ) ) ;

			if ( printDiskOnce ) {

				printDiskOnce = false ;

				logger.info(
						"service-disk-filesystem: {} \n\n diskFileSystemScript: {} \n\n diskCollection: {}",
						diskUsageScript,
						diskFileSystemScript,
						diskCollection.toString( ) ) ;

			}

			if ( Application.isRunningOnDesktop( ) ) {

				diskCollection = new StringBuilder( diskUsageForServicesCache ) ;

			}

			// Finally - update the cache
			diskUsageForServicesCache = diskCollection.toString( ) ;

		} catch ( Exception e ) {

			logger.error( "Failed getting disk: {}", CSAP.buildCsapStack( e ) ) ;

		}

		//
		// Collect docker container size
		//

		csapApp.metrics( ).stopTimer( diskTimer, "collect-os.service-folder-size" ) ;

		csapApp.metrics( ).stopTimer( timer, "csap.collect-os.disk-and-devices" ) ;

	}

	private String collectDockerDiskUsage ( ) {

		if ( ! csapApp.isDockerInstalledAndActive( ) ) {

			logger.debug( "Skipping docker collection because docker integration is disabled" ) ;
			return "" ;

		}

		return csapApp.getDockerIntegration( ).collectContainersDiskUsage( csapApp ) ;

	}

	private volatile List<ContainerProcess> docker_containerProcesses = new ArrayList<>( ) ;;

	public List<ContainerProcess> getDockerContainerProcesses ( ) {

		return docker_containerProcesses ;

	}

	public void buildDockerPidMapping ( ) {

		if ( csapApp.isDockerInstalledAndActive( ) || csapApp.isJunit( ) ) {

			if ( csapApp.getDockerIntegration( ) != null ) {

				docker_containerProcesses = csapApp.getDockerIntegration( ).build_process_info_for_containers( ) ;

			}

			if ( Application.isRunningOnDesktop( ) ) {

				// dumped via HostDashboard, os process tab
				String stubData = csapApp.check_for_stub( "", "linux/ps-docker-list.json" ) ;

				try {

					docker_containerProcesses = jsonMapper.readValue( stubData,
							new TypeReference<List<ContainerProcess>>( ) {
							} ) ;

					// since stubbed output is used - reset definition to false each time it is
					// loaded
					docker_containerProcesses.stream( ).forEach( dockerProcess -> {

						dockerProcess.setInDefinition( false ) ;

					} ) ;

				} catch ( Exception e ) {

					logger.warn( "Failed parsing stub data: {}", CSAP.buildCsapStack( e ) ) ;

				}

				// logger.info( "Stub Data: {}", WordUtils.wrap( stubContainers.toString(), 100)
				// );

			}

		}

	}

	private String lastProcessStatsCollected ( ) {

		// pcpu,rss,vsz,nlwp,ruser,pid,nice,ppid,args
		return (String) processStatisticsCache.getCachedObject( ) ;

	}

	private volatile CsapSimpleCache processStatisticsCache = null ;
	private volatile int processScanCount = 0 ;

	private boolean isInit_ps_complete ( ) {

		return processScanCount >= 2 ;

	}

	public boolean wait_for_initial_process_status_scan (
															int maxSeconds ) {

		logger.debug( CSAP.buildCsapStack( new Exception( "startup-stack-display" ) ) ) ;
		if ( isInit_ps_complete( ) )
			return true ;
		// possible race condition on initial lod
		int attempts = 0 ;

		while ( attempts < maxSeconds ) {

			attempts++ ;

			try {

				logger.info( "Waiting for initial process scan to complete: {} of {}",
						attempts, maxSeconds ) ;

				TimeUnit.SECONDS.sleep( 2 ) ;

				if ( ! isInit_ps_complete( ) ) {

					logger.info( "Triggering secondary ps scan to pickup docker container discovery" ) ;
					processStatisticsCache.expireNow( ) ;
					checkForProcessStatusUpdate( ) ;

				}

			} catch ( Exception e ) {

				logger.info( "Wait for ps to complete", CSAP.buildCsapStack( e ) ) ;

			}

			if ( isInit_ps_complete( ) )
				break ;

		}

		return isInit_ps_complete( ) ;

	}

	private ReentrantLock processStatusLock = new ReentrantLock( ) ;

	public void checkForProcessStatusUpdate ( ) {

		if ( processStatisticsCache == null ) {

			processStatisticsCache = CsapSimpleCache.builder(
					9,
					TimeUnit.SECONDS,
					OsManager.class,
					"Process Stats" ) ;
			// immediate expiration to force load
			processStatisticsCache.expireNow( ) ;

		}

		if ( ! processStatisticsCache.isExpired( ) ) {

			logger.debug( "\n\n***** ReUsing  processStatisticsCache   *******\n\n" ) ;

		} else if ( processStatusLock.tryLock( ) ) {

			if ( processStatisticsCache.isExpired( ) ) {

				logger.debug( "\n\n***** REFRESH  processStatisticsCache   *******\n\n" ) ;
				var allStepsTimer = csapApp.metrics( ).startTimer( ) ;

				try {

					csapApp.metrics( ).record( "collect-os.process-status", ( ) -> {

						String ps_command_output = osCommandRunner.executeString( null, osCommands
								.getProcessStatus( ) ) ;
						processStatisticsCache.reset( OsCommandRunner.trimHeader( ps_command_output ) ) ;

					} ) ;

					if ( Application.isRunningOnDesktop( ) ) {

						updateCachesWithTestData( ) ;

					}

					csapApp.metrics( ).record( "collect-os.process-details-docker", ( ) -> {

						buildDockerPidMapping( ) ;

					} ) ;

					csapApp.metrics( ).record( OsProcessMapper.MAPPER_TIMER, ( ) -> {

//						processMapper.process_find_wrapper(
//								(String) processStatisticsCache.getCachedObject( ),
//								diskUsageForServicesCache,
//								getDockerContainerProcesses( ),
//								csapApp.getServicesOnHost( ) ) ;
						processMapper.process_find_all_service_matches(
								csapApp.getServicesOnHost( ),
								(String) processStatisticsCache.getCachedObject( ),
								diskUsageForServicesCache,
								getDockerContainerProcesses( ) ) ;

					} ) ;

					// get podIps and Update pod Ips
					if ( csapApp.isKubernetesInstalledAndActive( ) ) {

						csapApp.metrics( ).record( "collect-os.process-map-pod-addresses", ( ) -> {

							csapApp.getKubernetesIntegration( ).updatePodIps( csapApp ) ;

						} ) ;

					}

					if ( ! isInit_ps_complete( ) ) {

						//
						processScanCount++ ;

					}

				} catch ( Exception e ) {

					logger.warn( "Failed refreshing runtime {}", CSAP.buildCsapStack( e ) ) ;

				} finally {

					processStatusLock.unlock( ) ;

				}

				csapApp.metrics( ).stopTimer( allStepsTimer, "csap.collect-os.process-to-model" ) ;

			}

		}

	}

	CsapSimpleCache csapFsCache ;

	private int collectCsapFolderDiskAndCache ( ) {

		if ( csapFsCache == null ) {

			csapFsCache = CsapSimpleCache.builder(
					60,
					TimeUnit.SECONDS,
					OsManager.class,
					"CsapFolderDf" ) ;
			csapFsCache.expireNow( ) ;

		}

		if ( ( csapFsCache.getCachedObject( ) != null )
				&& ! csapFsCache.isExpired( ) ) {

			logger.debug( "\n\n***** ReUsing  cpuStatisticsCache   *******\n\n" ) ;

		} else {

			logger.debug( "\n\n***** REFRESHING   cpuStatisticsCache   *******\n\n" ) ;
			var timer = csapApp.metrics( ).startTimer( ) ;
			var diskPercent = -1 ;
			var dfOutput = "not-run" ;

			try {

				dfOutput = osCommandRunner.runUsingDefaultUser( "csap-fs-collect",
						osCommands.getDiskUsageCsap( ) ) ;

				logger.debug( "dfOutput: \n{}\n---", dfOutput ) ;

				dfOutput = csapApp.check_for_stub( dfOutput, "linux/dfStaging.txt" ) ;

				dfOutput = osCommandRunner.trimHeader( dfOutput ) ;
				var lines = Arrays.stream( dfOutput.split( LINE_SEPARATOR ) )
						.filter( line -> line.contains( "%" ) )
						.map( String::trim )
						.map( line -> line.replace( Matcher.quoteReplacement( "%" ), "" ) )
						.findFirst( ) ;

				diskPercent = Integer.parseInt( lines.get( ) ) ;

			} catch ( Exception e ) {

				logger.warn( "Failed parsing df output {}", dfOutput ) ;

			}

			csapApp.metrics( ).stopTimer( timer, "collect-os.csap-fs" ) ;
			csapFsCache.reset( diskPercent ) ;

		}

		return (Integer) csapFsCache.getCachedObject( ) ;

	}

	/**
	 * Full Listing for traping memory usage
	 *
	 */
	public final static List<String> PS_MEMORY_LIST = Arrays
			.asList( "bash",
					"-c",
					"ps -e --sort -rss -o pmem,rss,vsz,size,nlwp,ruser,pid,args | sed 's/  */ /g' | sed 's/,/ /g' |awk '{ for(i=1;i<=7;i++){$i=$i\",\"}; print }'" ) ;

	public final static List<String> PS_PRIORITY_LIST = Arrays
			.asList( "bash",
					"-c",
					"ps -e --sort nice -o nice,pmem,rss,vsz,size,nlwp,ruser,pid,args | sed 's/  */ /g' | sed 's/,/ /g' |awk '{ for(i=1;i<=8;i++){$i=$i\",\"}; print }'" ) ;

	public final static List<String> FREE_LIST = Arrays.asList( "bash", "-c",
			"free -g" ) ;

	public final static List<String> FREE_BY_M_LIST = Arrays.asList( "bash",
			"-c", "free -m" ) ;

	public String performMemoryProcessList (
												boolean sortByPriority ,
												boolean isShowOnlyCsap ,
												boolean isShowOnlyUser ) {

		List<String> psList = PS_MEMORY_LIST ;

		if ( sortByPriority ) {

			psList = PS_PRIORITY_LIST ;

		}
		// ps -e --sort -rss -o pmem,rss,args | awk '{
		// for(i=0;i<=NF;i++){$i=$i","}; print }'
		// ps -e --sort -rss -o pmem,rss,vsz,size,nlwp,ruser,pid,args | sed
		// 's/ */ /g'
		// ps -e --sort -rss -o pmem,rss,vsz,size,nlwp,ruser,pid,args | sed
		// 's/ */ /g' |awk '{ for(i=0;i<=7;i++){$i=$i","}; print }'
		// size or rss...switch to size as it is bigger for now

		String psResult = osCommandRunner.executeString( null, psList ) ;
		psResult = csapApp.check_for_stub( psResult, "linux/psMemory.txt" ) ;

		if ( sortByPriority ) {

			psResult = csapApp.check_for_stub( psResult, "linux/psNice.txt" ) ;

		}

		String freeResult = osCommandRunner.executeString( FREE_LIST, new File( "." ) ) ;
		freeResult += osCommandRunner.executeString( FREE_BY_M_LIST, new File( "." ) ) ;
		freeResult = csapApp.check_for_stub( freeResult, "linux/freeResults.txt" ) ;

		// hook to display output nicely in browser
		String[] psLines = psResult.split( LINE_SEPARATOR ) ;
		StringBuilder psBuilder = new StringBuilder( ) ;

		String currUser = csapApp.getAgentRunUser( ) ;

		for ( int psIndex = 0; psIndex < psLines.length; psIndex++ ) {

			String currLine = psLines[psIndex].trim( ) ;
			String nameToken = "csapProcessId=" ;

			int nameStart = currLine.indexOf( nameToken ) ;
			int headerStart = currLine.indexOf( "RSS" ) ;
			int processingStart = currLine.indexOf( csapApp.getCsapWorkingFolder( ).getAbsolutePath( ) ) ;

			if ( Application.isRunningOnDesktop( ) ) {

				processingStart = currLine.indexOf( "/home/csapUser/processing" ) ;
				currUser = "csapUser" ;

			}

			// skip past any non csap processes
			if ( isShowOnlyCsap && nameStart == -1 && headerStart == -1 && processingStart == -1 ) {

				continue ;

			}

			// only show csapUser processes
			if ( currUser != null && isShowOnlyUser && headerStart == -1 && ! currLine.contains( currUser ) ) {

				continue ;

			}

			if ( nameStart != -1 ) {

				nameStart += nameToken.length( ) ;
				int nameEnd = currLine.substring( nameStart ).indexOf( " " )
						+ nameStart ;

				if ( nameEnd == -1 ) {

					nameEnd = nameStart + 5 ;

				}

				logger.debug( "currLine: {}, \n\t nameStart: {}, \t nameEnd: {}",
						currLine, nameStart, nameEnd ) ;

				String serviceName = currLine.substring( nameStart, nameEnd ) ;

				int insertIndex = currLine.indexOf( "/" ) ;

				if ( isShowOnlyCsap ) {

					currLine = currLine.substring( 0, insertIndex ) + serviceName ;

				} else {

					currLine = currLine.substring( 0, insertIndex )
							+ serviceName
							+ " : "
							+ currLine
									.substring( insertIndex, currLine.length( ) ) ;

				}

			}

			psBuilder.append( currLine + LINE_SEPARATOR ) ;

		}

		return freeResult + "\n\n" + psBuilder ;

	}

	public JsonNode diskReport ( ) {

		JsonNode result = null ;

		try {

			result = disk_reads_and_writes( ).get( "totalInMB" ) ;

		} catch ( Exception e ) {

			logger.warn( "Failed parsing iostat for io total reads and writes: {}", CSAP.buildCsapStack( e ) ) ;
			ObjectNode failureNode = jsonMapper.createObjectNode( ) ;
			failureNode.put( "reads", "-1" ) ;
			failureNode.put( "writes", -1 ) ;

			result = failureNode ;

		}

		return result ;

	}

	private CsapSimpleCache ioStatisticsCache = null ;
	private ReentrantLock ioStatusLock = new ReentrantLock( ) ;

	public ObjectNode disk_reads_and_writes ( ) {

		boolean initialRun = false ;

		if ( ioStatisticsCache == null ) {

			initialRun = true ;
			int cacheTime = 20 ;
			ioStatisticsCache = CsapSimpleCache.builder(
					cacheTime,
					TimeUnit.SECONDS,
					OsManager.class,
					"IO Statistics" ) ;
			ioStatisticsCache.expireNow( ) ;

		}

		if ( ! ioStatisticsCache.isExpired( ) ) {

			logger.debug( "\n\n***** ReUsing  ioStatisticsCache   *******\n\n" ) ;

		} else if ( ioStatusLock.tryLock( ) ) {

			if ( ioStatisticsCache.isExpired( ) ) {

				logger.debug( "\n\n***** REFRESHING   ioStatisticsCache   *******\n\n" ) ;

				var timer = csapApp.metrics( ).startTimer( ) ;

				try {

					ioStatisticsCache.reset( updateIoCache( initialRun ) ) ;

				} catch ( Exception e ) {

					logger.info( "Failed refreshing ioStatisticsCache {}",
							CSAP.buildCsapStack( e ) ) ;

				} finally {

					ioStatusLock.unlock( ) ;

				}

				csapApp.metrics( ).stopTimer( timer, "collect-os.device-read-write-rate" ) ;

			}

		} else {

			logger.debug( "Failed to get ioStatisticsCache lock" ) ;

		}

		return (ObjectNode) ioStatisticsCache.getCachedObject( ) ;

	}

	private ObjectNode updateIoCache (
										boolean isInitialRun ) {

		ObjectNode diskActivityReport = jsonMapper.createObjectNode( ) ;

		String iostatOutput = "" ;

		try {

			// iostatOutput = osCommandRunner.runUsingDefaultUser( "iostat_dm",
			// diskTestScript );
			iostatOutput = osCommandRunner.executeString( null, osCommands.getSystemDiskWithRateOnly( ) ) ;
			iostatOutput = csapApp.check_for_stub( iostatOutput, "linux/ioStatResults.txt" ) ;

			// Device: tps MB_read/s MB_wrtn/s MB_read MB_wrtn

			if ( isInitialRun ) {

				logger.info( "Results from {}, \n {}",
						osCommands.getSystemDiskWithRateOnly( ),
						CsapApplication.header( iostatOutput ) ) ;

			}

			String[] iostatLines = iostatOutput.split( LINE_SEPARATOR ) ;

			ArrayNode filteredLines = diskActivityReport.putArray( "filteredOutput" ) ;
			int totalDiskReadMb = 0 ;
			int totalDiskWriteMb = 0 ;

			for ( int i = 0; i < iostatLines.length; i++ ) {

				String curline = CsapCore.singleSpace( iostatLines[i] ) ;

				logger.debug( "Processing line: {}", curline ) ;

				if ( curline != null
						&& ! curline.isEmpty( )
						&& curline.matches( csapApp.rootProjectEnvSettings( ).getIostatDeviceFilter( ) ) ) {

					filteredLines.add( curline ) ;
					String[] fields = curline.split( " " ) ;

					if ( fields.length == 6 ) {

						totalDiskReadMb += Integer.parseInt( fields[4] ) ;
						totalDiskWriteMb += Integer.parseInt( fields[5] ) ;

					}

				}

			}

			ObjectNode total = diskActivityReport.putObject( "totalInMB" ) ;
			total.put( "reads", totalDiskReadMb ) ;
			total.put( "writes", totalDiskWriteMb ) ;

		} catch ( Exception e ) {

			logger.info( "Results from {}, \n {}, \n {}",
					osCommands.getSystemDiskWithRateOnly( ), iostatOutput,
					CSAP.buildCsapStack( e ) ) ;

		}

		return diskActivityReport ;

	}

	/**
	 * 
	 * 
	 * iostat -dx : determines disk utilization
	 * 
	 */

	private volatile CsapSimpleCache diskUtilizationCache = null ;
	private ReentrantLock diskUtilLock = new ReentrantLock( ) ;

	public ArrayNode device_utilization ( ) {

		ArrayNode result = null ;

		try {

			var current = disk_io_utilization( ) ;

			if ( current != null ) {

				result = (ArrayNode) current.path( "devices" ) ;

			} else {

				result = buildDeviceError( "Pending Initialization" ) ;

			}

		} catch ( Exception e ) {

			logger.warn( "Failed parsing iostat for io utilization: {}", CSAP.buildCsapStack( e ) ) ;

			result = buildDeviceError( CSAP.buildCsapStack( e ) ) ;

		}

		return result ;

	}

	private ArrayNode buildDeviceError ( String reason ) {

		ArrayNode result ;
		result = jsonMapper.createArrayNode( ) ;
		ObjectNode deviceData = result.addObject( ) ;
		deviceData.put( "name", "deviceNotFound" ) ;
		deviceData.put( "percentCapacity", -1 ) ;
		deviceData.put( "error-reason", reason ) ;
		return result ;

	}

	public ObjectNode disk_io_utilization ( ) {

		boolean initialRun = false ;

		if ( diskUtilizationCache == null ) {

			initialRun = true ;
			int cacheTime = 20 ;
			diskUtilizationCache = CsapSimpleCache.builder(
					cacheTime,
					TimeUnit.SECONDS,
					OsManager.class,
					"Disk Utilization Statistics" ) ;
			diskUtilizationCache.expireNow( ) ;

		}

		if ( ! diskUtilizationCache.isExpired( ) ) {

			logger.debug( "\n\n***** ReUsing  diskUtilizationCache   *******\n\n" ) ;

		} else if ( diskUtilLock.tryLock( ) ) {

			if ( diskUtilizationCache.isExpired( ) ) {

				logger.debug( "\n\n***** REFRESHING   diskUtilizationCache   *******\n\n" ) ;

				var timer = csapApp.metrics( ).startTimer( ) ;

				try {

					diskUtilizationCache.reset( updateDiskUtilCache( initialRun ) ) ;

				} catch ( Exception e ) {

					logger.info( "Failed refreshing diskUtilizationCache {}",
							CSAP.buildCsapStack( e ) ) ;

				} finally {

					diskUtilLock.unlock( ) ;

				}

				csapApp.metrics( ).stopTimer( timer, "collect-os.device-utilization-rate" ) ;

			}

		}

		return (ObjectNode) diskUtilizationCache.getCachedObject( ) ;

	}

	private ObjectNode updateDiskUtilCache (
												boolean isInitialRun ) {

		ObjectNode disk_io_utilization = jsonMapper.createObjectNode( ) ;

		List<String> diskUtilizationScript = osCommands.getSystemDiskWithUtilization( ) ;
		String iostatDiskUtilOutput = "" ;

		try {

			iostatDiskUtilOutput = osCommandRunner.runUsingDefaultUser(
					"iostat_dx",
					diskUtilizationScript ) ;

			iostatDiskUtilOutput = csapApp.check_for_stub( iostatDiskUtilOutput, "linux/iostat-with-util.txt" ) ;

			// Device: tps MB_read/s MB_wrtn/s MB_read MB_wrtn

			if ( isInitialRun ) {

				logger.info( "Results from {}, \n {}",
						Arrays.asList( diskUtilizationScript ),
						CsapApplication.header( iostatDiskUtilOutput ) ) ;

			}

			String[] iostatLines = iostatDiskUtilOutput.split( LINE_SEPARATOR ) ;

			ArrayNode filteredLines = disk_io_utilization.putArray( "filteredOutput" ) ;

			Map<String, Integer> deviceMap = new TreeMap<>( ) ;

			for ( int i = 0; i < iostatLines.length; i++ ) {

				String singleSpacedLine = CsapCore.singleSpace( iostatLines[i] ) ;

				logger.debug( "Processing line: {}", singleSpacedLine ) ;

				if ( singleSpacedLine != null
						&& ! singleSpacedLine.isEmpty( )
						&& singleSpacedLine.matches(
								csapApp.rootProjectEnvSettings( ).getIostatDeviceFilter( ) ) ) {

					filteredLines.add( singleSpacedLine ) ;
					String[] fields = singleSpacedLine.split( " " ) ;

					if ( fields.length == 2 ) {

						// centos 7
						String device = fields[0] ;
						int utilPercentage = -1 ;

						try {

							utilPercentage = Math.round( Float.parseFloat( fields[1] ) ) ;

						} catch ( Exception e ) {

							logger.error( CSAP.buildCsapStack( e ) ) ;

						}

						deviceMap.put( device, utilPercentage ) ;

					}

//					if ( fields.length == 14 ) {
//						// centos 7
//						String device = fields[0] ;
//						int utilPercentage = -1 ;
//						try {
//							utilPercentage = Math.round( Float.parseFloat( fields[13] ) ) ;
//						} catch ( Exception e ) {
//							logger.error( CSAP.buildCsapStack( e ) ) ;
//						}
//						deviceMap.put( device, utilPercentage ) ;
//
//					} else if ( fields.length == 16 ) {
//						String device = fields[0] ;
//						int utilPercentage = -1 ;
//						try {
//							utilPercentage = Math.round( Float.parseFloat( fields[15] ) ) ;
//						} catch ( Exception e ) {
//							logger.error( CSAP.buildCsapStack( e ) ) ;
//						}
//						deviceMap.put( device, utilPercentage ) ;
//
//					}
				}

			}

			// build sorted list
			ArrayNode devices = disk_io_utilization.putArray( "devices" ) ;
			deviceMap.entrySet( ).stream( ).forEach( deviceEntry -> {

				ObjectNode deviceData = devices.addObject( ) ;
				deviceData.put( "name", deviceEntry.getKey( ) ) ;
				deviceData.put( "percentCapacity", deviceEntry.getValue( ) ) ;

			} ) ;

		} catch ( Exception e ) {

			logger.info( "Results from {}, \n {}, \n {}",
					Arrays.asList( diskUtilizationScript ), iostatDiskUtilOutput,
					CSAP.buildCsapStack( e ) ) ;

		}

		return disk_io_utilization ;

	}

	public int getMemoryAvailbleLessCache ( ) {

		if ( getCachedMemoryMetrics( ) == null ) {

			return -1 ;

		}

		if ( isMemoryFreeAvailabe( ) ) {

			return getCachedMemoryMetrics( ).path( RAM ).path( 6 ).asInt( -1 ) ;

		}

		return getCachedMemoryMetrics( ).path( BUFFER ).path( 3 ).asInt( -1 ) ;

	}

	public int getMemoryCacheSize ( ) {

		if ( getCachedMemoryMetrics( ) == null ) {

			return -1 ;

		}

		if ( isMemoryFreeAvailabe( ) ) {

			return getCachedMemoryMetrics( ).path( RAM ).path( 5 ).asInt( ) ;

		}

		return getCachedMemoryMetrics( ).path( RAM ).path( 6 ).asInt( ) ;

	}

	// newer RH kernels have available as last column
	private boolean isMemoryFreeAvailabe ( ) {

		return getCachedMemoryMetrics( ).path( FREE_AVAILABLE ).asBoolean( ) ;

	}

	private volatile CsapSimpleCache memoryStatisticsCache = null ;

	public JsonNode getCachedMemoryMetrics ( ) {

		logger.debug( "Entered" ) ;

		// logger.info( "{}", Application.getCsapFilteredStackTrace( new
		// Exception( "calltree" ), "csap" )) ;
		if ( memoryStatisticsCache == null ) {

			memoryStatisticsCache = CsapSimpleCache.builder(
					4,
					TimeUnit.SECONDS,
					OsManager.class,
					"Memory Stats" ) ;
			memoryStatisticsCache.expireNow( ) ;

		}

		// Use cache
		if ( ! memoryStatisticsCache.isExpired( ) ) {

			logger.debug( "\n\n***** ReUsing  mem cache   *******\n\n" ) ;

			return (JsonNode) memoryStatisticsCache.getCachedObject( ) ;

		}

		logger.debug( "\n\n***** reloading  mem cache   *******\n\n" ) ;

		JsonNode rootNode ;

		List<String> parmList = Arrays.asList( "bash", "-c", "free -m" ) ;
		String freeResult = osCommandRunner.executeString( parmList, new File( "." ) ) ;
		freeResult = csapApp.check_for_stub( freeResult, "linux/freeResults.txt" ) ;

		parmList = Arrays.asList( "bash", "-c", "swapon -s " ) ;
		String swapResult = osCommandRunner.executeString( parmList, new File( "." ) ) ;
		swapResult = csapApp.check_for_stub( swapResult, "linux/swapResults.txt" ) ;

		try {

			// Lets refresh cache
			logger.debug( "\n\n***** Refreshing mem cache   *******\n\n" ) ;

			logger.debug( "freeResult: {}, \n swapResult: {} ", freeResult, swapResult ) ;

			String headers = freeResult.substring( 0, freeResult.indexOf( "Mem:" ) ) ;
			// handle newerKernel rh7+
			boolean isFreeAvailable = false ;

			if ( headers.contains( "available" ) ) {

				isFreeAvailable = true ;

			}

			// Strips off the headers
			String trimFree = freeResult.substring( freeResult.indexOf( "Mem:" ) ) ;
			String[] memLines = trimFree
					.split( LINE_SEPARATOR ) ;

			TreeMap<String, String[]> memResults = new TreeMap<String, String[]>( ) ;

			for ( int i = 0; i < memLines.length; i++ ) {

				if ( memLines[i].contains( "Mem:" ) ) {

					memResults.put( RAM,
							CsapCore.singleSpace( memLines[i] ).split( " " ) ) ;

					// default buffer to use RAM line. centos
					memResults.put( BUFFER, CsapCore.singleSpace( memLines[i] ).split( " " ) ) ;

				} else if ( memLines[i].contains( "cache:" ) ) {

					memResults.put( BUFFER, CsapCore.singleSpace( memLines[i] )
							.split( " " ) ) ;

				} else if ( memLines[i].contains( "Swap:" ) ) {

					memResults.put( SWAP, CsapCore.singleSpace( memLines[i] )
							.split( " " ) ) ;

				}

			}

			if ( ( swapResult.trim( ).length( ) == 0 ) || ( ! swapResult.contains( "Filename" ) ) ) {

				String[] noResults = {
						"no Swap Found"
				} ;
				memResults.put( "swapon1", noResults ) ;

			} else {

				String trimSwap = swapResult.substring( swapResult.indexOf( "Filename" ) ) ;
				String[] swapLines = trimSwap.split( System
						.getProperty( "line.separator" ) ) ;

				// skip past the header no matter what it is
				int i = 1 ;

				for ( String line : swapLines ) {

					ArrayList<String> swapList = new ArrayList<String>( ) ;
					String swapPer = "" ;
					// added host below for the simple view which needs to track
					// it
					String[] columns = CsapCore.singleSpace( line ).split( " " ) ;

					if ( columns.length == 5 && columns[0].startsWith( "/" ) ) {

						swapPer = "" ;

						try {

							float j = Float.parseFloat( columns[3] ) ;
							float k = Float.parseFloat( columns[2] ) ;
							swapPer = Integer.valueOf( Math.round( j / k * 100 ) ).toString( ) ;

						} catch ( Exception e ) {

							// ignore
						}

						swapList.add( columns[0] ) ;
						swapList.add( columns[1] ) ;
						swapList.add( swapPer ) ;
						swapList.add( columns[3] + " / " + columns[2] ) ;
						swapList.add( columns[4] ) ;
						memResults.put( "swapon" + i++,
								swapList.toArray( new String[swapList.size( )] ) ) ;

					}

				}

			}

			rootNode = jsonMapper.valueToTree( memResults ) ;

			Format tsFormater = new SimpleDateFormat( "HH:mm:ss" ) ;
			( (ObjectNode) rootNode ).put( "timestamp", tsFormater.format( new Date( ) ) ) ;
			( (ObjectNode) rootNode ).put( FREE_AVAILABLE, isFreeAvailable ) ;

			memoryStatisticsCache.reset( rootNode ) ;
			return rootNode ;

		} catch ( Exception e ) {

			logger.warn( "Failure parsing memory, free:\n {} \n swap:\n{}", freeResult, swapResult, e ) ;

		}

		return null ;

	}

	public static final String FREE_AVAILABLE = "isFreeAvailable" ;

	public String updatePlatformCore (
										MultipartFile multiPartFile ,
										String extractTargetPath ,
										boolean skipExtract ,
										String remoteServerName ,
										String chownUserid ,
										String auditUser ,
										String deleteExisting ,
										OutputFileMgr outputFileManager )
		throws IOException {

		StringBuilder results = new StringBuilder( "Host: " + remoteServerName ) ;

		if ( multiPartFile == null ) {

			results.append( "\n========== multiPartFile is null \n\n" ) ;
			return results.toString( ) ;

		}

		if ( extractTargetPath.trim( )
				.length( ) == 0
				|| chownUserid.trim( )
						.length( ) == 0
				|| auditUser.trim( )
						.length( ) == 0 ) {

			logger.error( "extractTargetPath is empty, must be corrected" ) ;
			results.append( "\n " + MISSING_PARAM_HACK
					+ " param was an empty string and is required. extractTargetPath: "
					+ extractTargetPath + ", chownUserid: " + chownUserid + ", auditUser:" + auditUser ) ;
			return results.toString( ) ;

		}
		// byte[] fileBytes = file.getBytes();

		// We temporarily extract all files to followingFolder, then copy to
		// target location
		// Note the subFolder MUST be different from original source folder, or
		// files could get overwritten
		File platformTempFolder = new File( csapApp.csapPlatformTemp( ), "/csap-agent-transfer-manager/" ) ;

		if ( ! platformTempFolder.exists( ) ) {

			logger.info( "Creating {}", platformTempFolder ) ;
			platformTempFolder.mkdirs( ) ;

		}

		File tempExtractLocation = new File( platformTempFolder, multiPartFile.getOriginalFilename( ) ) ;

		if ( Application.isRunningOnDesktop( ) && skipExtract ) {

			logger.warn( CsapApplication.testHeader( "desktop - modifying tempExtractLocation: {}" ),
					tempExtractLocation ) ;
			tempExtractLocation = new File( extractTargetPath ) ;

		}

		results.append( " uploaded file: " + multiPartFile.getOriginalFilename( ) ) ;
		results.append( " Size: " + multiPartFile.getSize( ) ) ;

		File extractTarget = new File( extractTargetPath ) ;

		if ( ! extractTarget.getParentFile( ).exists( ) ) {

			logger.warn( "parent folder for extraction does not exist: {} ",
					extractTarget.getParentFile( ).getAbsolutePath( ) ) ;

		}

		if ( extractTarget.exists( ) && extractTarget.isFile( ) ) {

			results.append( "\n ===> Destination exists and will be overwritten." ) ;

			if ( Application.isRunningOnDesktop( ) & ! skipExtract ) {

				logger.warn( CsapApplication.testHeader( "desktop - modifying tempExtractLocation: {}" ),
						tempExtractLocation ) ;

				tempExtractLocation = new File( extractTarget.getAbsolutePath( ) + ".windebug" ) ;
				results.append( "\n desktop destination for testing only: "
						+ tempExtractLocation.getAbsolutePath( ) ) ;

			}

		}

		try {

			outputFileManager.printImmediate( "\n\n *** Temporary upload location: " + tempExtractLocation
					.getAbsolutePath( ) ) ;
			multiPartFile.transferTo( tempExtractLocation ) ;

		} catch ( Exception e ) {

			logger.error( "multiPartFile.transferTo : {} {}", tempExtractLocation, CSAP.buildCsapStack( e ) ) ;

			return "\n== " + CsapCore.CONFIG_PARSE_ERROR
					+ " on multipart file transfer on Host " + csapApp.getCsapHostName( )
					+ ":" + e.getMessage( ) ;

		}

		if ( Application.isRunningOnDesktop( )
				&& ! skipExtract
				&& ( multiPartFile.getOriginalFilename( ).endsWith( ".zip" ) ) ) {

			logger.warn( CsapApplication.testHeader( "desktop - checking extractTarget: {}" ),
					extractTarget ) ;

			if ( extractTarget.exists( ) && extractTarget.isDirectory( ) ) {

				try {

					File desktopTransferFolder = new File( extractTarget, "csap-desktop-transfer" ) ;

					if ( csapApp.isJunit( ) ) {

						desktopTransferFolder = extractTarget ;

					}

					ZipUtility.unzip( tempExtractLocation, desktopTransferFolder ) ;
					results.append( "\n Unzipped to: " + desktopTransferFolder.getAbsolutePath( ) ) ;

				} catch ( Exception e ) {

					results.append( "\n Failed to unzip " + tempExtractLocation.getAbsolutePath( )
							+ " due to: " + e.getMessage( ) ) ;
					logger.error( "\n Failed to unzip " + tempExtractLocation.getAbsolutePath( ), e ) ;

				}

			} else {

				results.append( "\n== " + CsapCore.CONFIG_PARSE_ERROR
						+ " Windows extract target exists and is a file: "
						+ extractTarget.getAbsolutePath( ) ) ;

			}

		}

		if ( ! tempExtractLocation.exists( ) ) {

			results.append( " Could not run as root, extract file not located in "
					+ tempExtractLocation.getAbsolutePath( ) ) ;

		} else {

			var parmList = List.of( "echo skipping transfer" ) ;

			if ( csapApp.isJunit( ) ) {

				logger.info( CsapApplication.testHeader( "junit: skipping FS updates" ) ) ;

			} else {

				// backup existing
				if ( deleteExisting != null ) {

					csapApp.move_to_csap_saved_folder( extractTarget, results ) ;

				}

				// hook for root ownership, and script execution
				// ALWAYS use CSAP user if files are extracted
				String user = chownUserid ;

				if ( extractTargetPath.startsWith( csapApp.getAgentRunHome( ) ) ) {

					user = csapApp.getAgentRunUser( ) ;
					logger.info( "Specified directory starts with: {}, userid will be set to: {}", csapApp
							.getAgentRunHome( ), user ) ;

				}

				File scriptPath = csapApp.csapPlatformPath( "/bin/csap-unzip-as-root.sh" ) ;
				parmList = new ArrayList<String>( ) ;

				if ( csapApp.isRunningAsRoot( ) ) {

					parmList.add( "/usr/bin/sudo" ) ;
					parmList.add( scriptPath.getAbsolutePath( ) ) ;
					parmList.add( tempExtractLocation.getAbsolutePath( ) ) ;
					parmList.add( extractTargetPath ) ;
					parmList.add( user ) ;

					if ( skipExtract ) {

						parmList.add( "skipExtract" ) ;

					}

				} else {

					parmList.add( "bash" ) ;
					parmList.add( "-c" ) ;
					String command = scriptPath.getAbsolutePath( )
							+ " " + tempExtractLocation.getAbsolutePath( )
							+ " " + extractTargetPath
							+ " " + chownUserid ;

					if ( skipExtract ) {

						command += " skipExtract" ;

					}

					parmList.add( command ) ;

				}

			}

			var extractResults = osCommandRunner.executeString( null, parmList ) ;
			logger.debug( "script results: {}", extractResults ) ;

			results.append( "\n" + extractResults ) ;

		}

		csapApp.getEventClient( ).publishUserEvent( CsapEvents.CSAP_SYSTEM_CATEGORY + "/fileUpload", auditUser,
				multiPartFile.getOriginalFilename( ), results.toString( ) ) ;

		return results.toString( ) ;

	}

	public static String MISSING_PARAM_HACK = CsapCore.CONFIG_PARSE_ERROR + "-BlankParamFound" ;

	/**
	 * Note that cluster can be none, in which case command is only run on current
	 * VM.
	 *
	 * @param timeoutSeconds
	 * @param contents
	 * @param chownUserid
	 * @param clusterName
	 * @param scriptName
	 * @param outputFm
	 * @return
	 * @throws IOException
	 */
	public ObjectNode executeShellScriptClustered (
													String apiUser ,
													int timeoutSeconds ,
													String contents ,
													String chownUserid ,
													String[] hosts ,
													String scriptName ,
													OutputFileMgr outputFm )
		throws IOException {

		List<String> hostList = new ArrayList<>( Arrays.asList( hosts ) ) ;

		ObjectNode resultsNode = jsonMapper.createObjectNode( ) ;
		resultsNode.put( "scriptHost", csapApp.getCsapHostName( ) ) ;
		ArrayNode hostNode = resultsNode.putArray( "scriptOutput" ) ;

		logger.info( "scriptName: {}, apiUser: {} ,  chownUserid: {} , hosts: {}",
				scriptName, apiUser, chownUserid, hostList ) ;

		File scriptDir = csapApp.getScriptDir( ) ;

		if ( ! scriptDir.exists( ) ) {

			logger.info( "Making: " + scriptDir.getAbsolutePath( ) ) ;
			scriptDir.mkdirs( ) ;

		}

		File executableScript = new File( scriptDir, scriptName ) ;

		if ( executableScript.exists( ) ) {

			logger.info( "Deleting" + executableScript.getAbsolutePath( ) ) ;
			executableScript.delete( ) ;
			hostNode.add( "== Deleting existing script of same name: "
					+ executableScript.getAbsolutePath( ) ) ;

		}

		hostNode.add( "\n == Script Output: " + outputFm.getOutputFile( ).getAbsolutePath( ) + "\n\n" ) ;

		var scriptSummary = CSAP.buildDescription(
				"Executing : script",
				"user", chownUserid,
				"hosts", hostList,
				"time out seconds", timeoutSeconds,
				"location", executableScript.getAbsolutePath( ) ) ;

		logger.info( scriptName ) ;

		csapApp.getEventClient( ).publishUserEvent(
				CsapEvents.CSAP_OS_CATEGORY + "/execute",
				apiUser, executableScript.getName( ),
				scriptSummary + "\n\nscript: \n" + contents ) ;

		if ( ! executableScript.exists( ) ) {

			try ( FileWriter fstream = new FileWriter( executableScript );
					BufferedWriter out = new BufferedWriter( fstream ); ) {
				// Create file

				out.write( contents ) ;

			} catch ( Exception e ) {

				hostNode.add( "ERROR: failed to createfile due to: " + e.getMessage( ) ) ;
				;

			}

			hostNode.add( csapApp.getCsapHostName( ) + ":" + " Script copied" ) ;

		} else {

			hostNode.add( "ERROR: Script file still exists" ) ;

		}

		resultsNode.set( "otherHosts",
				zipAndTransfer( apiUser, timeoutSeconds, hostList,
						executableScript.getAbsolutePath( ), CsapCore.SAME_LOCATION, chownUserid, outputFm, null ) ) ;

		return resultsNode ;

	}

	/**
	 *
	 * Will zip and tar
	 *
	 * @param timeOutSeconds
	 * @param hostList
	 * @param locationToZip
	 * @param extractDir
	 * @param chownUserid
	 * @param auditUser
	 * @param outputFm
	 * @return
	 * @throws IOException
	 */
	public ArrayNode zipAndTransfer (
										String apiUser ,
										int timeOutSeconds ,
										List<String> hostList ,
										String locationToZip ,
										String extractDir ,
										String chownUserid ,
										OutputFileMgr outputFm ,
										String deleteExisting )
		throws IOException {

		logger.debug( "locationToZip: {}, extractDir: {}, chownUserid: {} , hosts: {}",
				locationToZip, extractDir, chownUserid, Arrays.asList( hostList )
						.toString( ) ) ;

		var results = jsonMapper.createArrayNode( ) ;

		if ( hostList == null || hostList.size( ) == 0 ) {

			return results ;

		}
		// return "No Additional Synchronization required";
		// logger.info("locationToZip" + locationToZip);

		var transferManager = new TransferManager( csapApp, timeOutSeconds, outputFm
				.getBufferedWriter( ) ) ;

		if ( deleteExisting != null ) {

			transferManager.setDeleteExisting( true ) ;

		}

		var zipLocation = new File( locationToZip ) ;

		var targetFolder = new File( extractDir ) ;

		if ( extractDir.equalsIgnoreCase( CsapCore.SAME_LOCATION ) ) {

			targetFolder = zipLocation ;

			if ( zipLocation.isFile( ) ) {

				// Hook when just a single file is being transferred
				targetFolder = zipLocation.getParentFile( ) ;

			}

		}

		var result = "Specified Location does not exist: " + locationToZip + " on host: "
				+ csapApp.getCsapHostName( ) ;

		if ( zipLocation.exists( ) ) {

			transferManager.httpCopyViaCsAgent(
					apiUser,
					zipLocation,
					Application.filePathAllOs( targetFolder ),
					hostList,
					chownUserid ) ;

			results = transferManager.waitForCompleteJson( ) ;

		} else {

			logger.error( result ) ;

		}

		return results ;

	}

	public String calico ( String parameters ) {

		var script = List.of(
				"#!/bin/bash",
				sourceCommonFunctions( ),
				"calico " + parameters,
				"" ) ;

		String scriptOutput = "Failed to run" ;

		try {

			scriptOutput = OsCommandRunner.trimHeader( osCommandRunner.runUsingDefaultUser( "calicoctl", script ) ) ;

			// scriptOutput = csapApp.check_for_stub( scriptOutput,
			// "linux/systemctl-status.txt" ) ;

			logger.debug( "output from: {}  , \n{}", script, scriptOutput ) ;

		} catch ( IOException e ) {

			logger.info( "Failed to run docker nsenter: {} , \n reason: {}", script,
					CSAP.buildCsapStack( e ) ) ;
			scriptOutput += ", reason: " + e.getMessage( ) + " type: " +
					e.getClass( ).getName( ) ;

		}

		return scriptOutput ;

	}

	public String systemStatus ( ) {

		List<String> lines = osCommands.getSystemServiceListing( csapApp.getCsapInstallFolder( ).getAbsolutePath( ) ) ;

		String scriptOutput = "Failed to run" ;

		try {

			scriptOutput = osCommandRunner.runUsingRootUser( "OsManagerProcessTree", lines ) ;

			scriptOutput = csapApp.check_for_stub( scriptOutput, "linux/systemctl-status.txt" ) ;

			logger.debug( "output from: {}  , \n{}", lines, scriptOutput ) ;

		} catch ( IOException e ) {

			logger.info( "Failed to run docker nsenter: {} , \n reason: {}", lines,
					CSAP.buildCsapStack( e ) ) ;
			scriptOutput += ", reason: " + e.getMessage( ) + " type: " +
					e.getClass( ).getName( ) ;

		}

		return scriptOutput ;

	}

	public ObjectNode buildProcessReport (
											String pid ,
											String description ) {

		ObjectNode processReport = jsonMapper.createObjectNode( ) ;

		var reportText = CsapApplication.header( "Process arguments" ) + collectProcessArguments( pid ) ;

		reportText += CsapApplication.header( "Process trees" ) + runProcessTree( pid, description ) ;

		processReport.put( DockerJson.response_plain_text.json( ), reportText ) ;

		return processReport ;

	}

	private String collectProcessArguments ( String pid ) {

		var script = List.of(
				"#!/bin/bash",
				sourceCommonFunctions( ),
				"print_separator \"ps -o pcpu,pid,args\"",
				"ps -o pcpu,pid,args -p " + pid,
				"" ) ;

		String scriptOutput = "Failed to run" ;

		try {

			scriptOutput = osCommandRunner.runUsingRootUser( "on-demand-ps", script ) ;
			logger.debug( "output from: {}  , \n{}", script, scriptOutput ) ;

		} catch ( IOException e ) {

			logger.info( "Failed to run : {} , \n reason: {}", script,
					CSAP.buildCsapStack( e ) ) ;

		}

		return scriptOutput ;

	}

	public String runProcessTree (
									String pid ,
									String serviceName ) {

		var bashScript = List.of(
				"#!/bin/bash",
				sourceCommonFunctions( ),

				"print_separator \"pstree: summary\"",
				"pstree -slp " + pid + " | head -1",

				"print_separator \"pstree: arguments\"",
				"pstree -sla " + pid,

				"print_separator \"pstree: full\"",
				"pstree",
				"" ) ;

		String scriptOutput = "Failed to run" ;

		try {

			scriptOutput = osCommandRunner.runUsingRootUser( "OsManagerProcessTree", bashScript ) ;
			logger.debug( "output from: {}  , \n{}", bashScript, scriptOutput ) ;

		} catch ( IOException e ) {

			logger.info( "Failed to run: {} , \n reason: {}", bashScript,
					CSAP.buildCsapStack( e ) ) ;

		}

		return scriptOutput ;

	}

	public ObjectNode runOsTopCommand ( ) {

		ObjectNode results = jsonMapper.createObjectNode( ) ;

		String[] lines = {
				"#!/bin/bash",
				sourceCommonFunctions( ),

				"print_with_head Running top in batch mode top -b -d 2 -n 1 -c",
				"export COLUMNS=500; top -b -d 2 -n 1 -c",
				""
		} ;

		String scriptOutput = "Failed to run" ;

		try {

			scriptOutput = osCommandRunner.runUsingRootUser( "on-demand-top",
					Arrays.asList( lines ) ) ;

			results.put( DockerJson.response_plain_text.json( ), scriptOutput ) ;
			logger.debug( "output from: {}  , \n{}", lines[1], scriptOutput ) ;

		} catch ( IOException e ) {

			logger.info( "Failed to run docker nsenter: {} , \n reason: {}", lines,
					CSAP.buildCsapStack( e ) ) ;
			results.put( "error", CSAP.buildCsapStack( e ) ) ;
			;

		}

		return results ;

	}

	public int kubernetes_certs_expiration_days ( int numDays ) {

		int nearestExpirationDays = -1 ;

		try {

			var script = List.of(
					"#!/bin/bash",
					sourceCommonFunctions( ),
					"export minimumExpirationDays=" + numDays,
					csapApp.csapPlatformPath( "bin/csap-check-certificates.sh" ).getAbsolutePath( ),
					"" ) ;

			String scriptOutput = "Failed to run" ;

			try {

				scriptOutput = OsCommandRunner.trimHeader( osCommandRunner.runUsingDefaultUser( "kubeadm-check-certs",
						script ) ) ;

				if ( scriptOutput.contains( "apiserver-kubelet-client" ) ) {

					logger.warn( "Certificates are nearing expiration: {}", scriptOutput ) ;
					String[] serviceLines = scriptOutput.split( LINE_SEPARATOR ) ;

					var shortestExpiration = Arrays.stream( serviceLines )
							.filter( StringUtils::isNotEmpty )
							.filter( line -> line.contains( "__required-action-days__:" ) )
							.mapToInt( line -> {

								String[] cols = line.trim( ).split( " " ) ;

								if ( cols.length == 2 ) {

									return Integer.parseInt( cols[1] ) ;

								}

								return -1 ;

							} )
							.findFirst( ) ;

					if ( shortestExpiration.isPresent( ) ) {

						nearestExpirationDays = shortestExpiration.getAsInt( ) ;

					}

				} else {

					logger.warn( "Failed to find expected output: ", scriptOutput ) ;

				}

				// scriptOutput = csapApp.check_for_stub( scriptOutput,
				// "linux/systemctl-status.txt" ) ;

				logger.debug( "output from: {}  , \n{}", script, scriptOutput ) ;

			} catch ( Exception e ) {

				logger.info( "Failed to run certificate checks: {} , \n reason: {}", script,
						CSAP.buildCsapStack( e ) ) ;

			}

		} catch ( Exception e ) {

			logger.info( "Failed to run kubeadm-check-certs: {}", CSAP.buildCsapStack( e ) ) ;

		}

		return nearestExpirationDays ;

	}

	public ObjectNode run_kernel_limits ( ) {

		ObjectNode results = jsonMapper.createObjectNode( ) ;

		try {

			List<String> lines = Files.readAllLines( csapApp.csapPlatformPath( "bin/admin-show-limits.sh" )
					.toPath( ) ) ;
			results.put( DockerJson.response_plain_text.json( ),
					osCommandRunner.runUsingRootUser( "adminlimits", lines ) ) ;

		} catch ( Exception e ) {

			logger.info( "Failed to run run_kernel_limits: {}",
					CSAP.buildCsapStack( e ) ) ;

			results.put( DockerJson.response_plain_text.json( ), CSAP.buildCsapStack( e ) ) ;

		}

		return results ;

	}

	public ObjectNode latest_process_discovery_results ( ) {

		ObjectNode results = jsonMapper.createObjectNode( ) ;

		results.put( DockerJson.response_plain_text.json( ),
				processMapper.getLatestProcessSummary( )
						+ "\n\n" + CsapApplication.LINE
						+ "\n\n linux ps output: \n\n"
						+ lastProcessStatsCollected( )
						+ "\n\n" + CsapApplication.LINE
						+ "\n Container Discovery: \n\n"
						+ processMapper.containerSummary( ) ) ;

		return results ;

	}

	public int getCachedLinuxPackageCount ( ) {

		return cachedLinuxPackageCount ;

	}

	public void setCachedLinuxPackageCount (
												int cachedLinuxPackageCount ) {

		this.cachedLinuxPackageCount = cachedLinuxPackageCount ;

	}

	public int getCachedLinuxServiceCount ( ) {

		return cachedLinuxServiceCount ;

	}

	public void setCachedLinuxServiceCount (
												int cachedLinuxServiceCount ) {

		this.cachedLinuxServiceCount = cachedLinuxServiceCount ;

	}

	public ObjectNode cleanImages (
									int days ,
									int minutes ) {

		ObjectNode results = jsonMapper.createObjectNode( ) ;

		String dockerWorking = csapApp.findFirstServiceInstanceInLifecycle( "docker" )
				.getWorkingDirectory( )
				.getAbsolutePath( ) ;

		int numSeconds = days * ( 24 * 60 * 60 ) + minutes * ( 60 ) ;

		String[] lines = {
				"#!/bin/bash",
				"echo Cleaning: " + days + " days and " + minutes + " minutes : " + numSeconds + " seconds",
				"dockerDir=" + dockerWorking,
				"export PID_DIR=$dockerDir/scripts",
				"export STATE_DIR=$dockerDir/docker-gc-state",
				"export GRACE_PERIOD_SECONDS=" + numSeconds,
				"$dockerDir/scripts/docker-gc.sh"
		} ;

		try {

			String cleanOutput = osCommandRunner.runUsingDefaultUser( "docker-image-clean", Arrays.asList( lines ) ) ;
			// cleanOutput = csapApp.check_for_stub( ioOutput,
			// "linux/proc-net-dev.txt" );

			results.put( DockerJson.response_plain_text.json( ), cleanOutput ) ;

		} catch ( IOException e ) {

			logger.info( "Failed to run docker nsenter: {} , \n reason: {}", lines,
					CSAP.buildCsapStack( e ) ) ;

			results.put( "error", true ) ;

		}

		return results ;

	}

	public ObjectNode killProcess (
									int pid ,
									String signal ) {

		ObjectNode results = jsonMapper.createObjectNode( ) ;

		String[] lines = {
				"#!/bin/bash",
				sourceCommonFunctions( ),
				"print_with_date killing pid: " + pid,
				"listing=$(ps -ef | grep " + pid + ")",
				"print_with_head \"pre-kill listing: $listing \"",
				"kill " + signal + " " + pid,
				"listing=$(ps -ef | grep " + pid + ")",
				"print_with_head \"post-kill listing (should be empty): \n'$listing' \"",
		} ;

		try {

			String cleanOutput = osCommandRunner.runUsingRootUser( "docker-image-clean", Arrays.asList( lines ) ) ;
			// cleanOutput = csapApp.check_for_stub( ioOutput,
			// "linux/proc-net-dev.txt" );

			results.put( DockerJson.response_plain_text.json( ), cleanOutput ) ;

		} catch ( IOException e ) {

			logger.info( "Failed to run docker nsenter: {} , \n reason: {}", lines,
					CSAP.buildCsapStack( e ) ) ;

			results.put( "error", true ) ;

		}

		return results ;

	}

	public String sourceCommonFunctions ( ) {

		return "source " + csapApp.csapPlatformPath( "bin/csap-environment.sh" ).getAbsolutePath( ) ;

	}

	public OsCommands getOsCommands ( ) {

		return osCommands ;

	}

	public void setOsCommands (
								OsCommands osSettings ) {

		this.osCommands = osSettings ;

	}

	public int getCachedNetworkInterfaceCount ( ) {

		return cachedNetworkInterfaceCount ;

	}

	public void setCachedNetworkInterfaceCount (
													int networkInterfaceCount ) {

		this.cachedNetworkInterfaceCount = networkInterfaceCount ;

	}

	public ObjectNode kubernetesCli (
										String command ,
										DockerJson responseType ) {

		ObjectNode results = jsonMapper.createObjectNode( ) ;

		List<String> parmList = List.of( "bash", "-c", KubernetesIntegration.CLI_COMMAND + " " + command ) ;

		var commandResults = OsCommandRunner.trimHeader(
				kuberernetesRunner.executeString( parmList, csapApp.getCsapSavedFolder( ) ) ) ;

		if ( csapApp.isDesktopHost( ) ) {

			if ( command.contains( "get -o=yaml" ) ) {

				commandResults = csapApp.check_for_stub( commandResults, "linux/pod-get.yml" ) ;

			} else {

				commandResults = csapApp.check_for_stub( commandResults, "linux/pod-describe.txt" ) ;

			}

		}

		results.put( responseType.json( ), commandResults ) ;

		return results ;

	}

	public ObjectNode dockerCli (
									String command ,
									DockerJson responseType ) {

		ObjectNode results = jsonMapper.createObjectNode( ) ;

		List<String> parmList = List.of( "bash", "-c", DockerIntegration.CLI_COMMAND + " " + command ) ;

		results.put(
				responseType.json( ),
				OsCommandRunner.trimHeader(
						kuberernetesRunner.executeString( parmList, csapApp.getCsapSavedFolder( ) ) ) ) ;

		return results ;

	}

	public InfrastructureRunner getInfraRunner ( ) {

		return infraRunner ;

	}

	public ServiceJobRunner getJobRunner ( ) {

		return jobRunner ;

	}

	public LogRollerRunnable getLogRoller ( ) {

		return logRoller ;

	}

	public ServiceResourceRunnable getServiceResourceRunnable ( ) {

		return serviceResourceRunnable ;

	}

	public void setServiceManager ( ServiceOsManager serviceManager ) {

		this.serviceManager = serviceManager ;

	}

}
