package org.csap.agent.stats ;

import java.io.BufferedReader ;
import java.io.IOException ;
import java.io.InputStreamReader ;
import java.lang.management.ManagementFactory ;
import java.text.DecimalFormat ;
import java.util.Arrays ;
import java.util.Date ;
import java.util.Iterator ;
import java.util.List ;
import java.util.Random ;
import java.util.stream.IntStream ;

import org.csap.agent.integrations.CsapEvents ;
import org.csap.agent.model.Application ;
import org.csap.agent.services.OsManager ;
import org.csap.helpers.CSAP ;
import org.csap.helpers.CsapApplication ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;

import com.fasterxml.jackson.databind.JsonNode ;
import com.fasterxml.jackson.databind.node.ArrayNode ;
import com.fasterxml.jackson.databind.node.ObjectNode ;

public class OsSharedResourcesCollector extends HostCollector implements Runnable {

	private static final String IOSTAT_UTILIZATION = "ioPercentUsed" ;

	private static final String IO_WRITES = "ioWrites" ;
	private static final String IO_READS = "ioReads" ;

	private static final String NETWORK_RECEIVED = "networkReceived" ;
	private static final String NETWORK_TRANSMITTED = "networkTransmitted" ;

	final Logger logger = LoggerFactory.getLogger( OsSharedResourcesCollector.class ) ;

	private int[] openFiles ;
	private int[] totalThreads ;
	private int[] csapThreads ;
	private int[] totalFileDescriptors ;
	private int[] csapFileDescriptors ;
	private int[] networkConns ;
	private int[] networkWait ;
	private int[] networkTimeWait ;

	private int[] usrCpuLevel ;
	private int[] memoryAvailableLessCache ;
	private int[] memoryBufferCache ;
	private int[] sysCpuLevel ;
	private int[] ioLevel ;
	private double[] loadLevel ;

	private double[] cpuTestTime ;
	private double[] diskTestTime ;

	private int[] ioReads ;
	private int[] ioWrites ;

	private double[] networkReceived ;
	private double[] networkTransmited ;

	JsonNode deviceUtilizationReport = null ;
	private static int MAX_DEVICES = 5 ;
	private int[][] deviceUtilization ;

	private long[] collectionMs ;

	private int lastAddedElementIndex = 0 ;

	public int getLastAddedElementIndex ( ) {

		return lastAddedElementIndex ;

	}

	public void setLastAddedElementIndex ( int lastAddedElementIndex ) {

		this.lastAddedElementIndex = lastAddedElementIndex ;

	}

	private String command = "" ;

	private String uploadUrl = null ;

	public String getUploadUrl ( ) {

		return uploadUrl ;

	}

	public OsSharedResourcesCollector (
			Application csapApplication,
			OsManager osManager,
			int intervalSeconds,
			boolean publishSummary ) {

		super( csapApplication, osManager, intervalSeconds, publishSummary ) ;

		openFiles = new int[getInMemoryCacheSize( )] ;
		totalThreads = new int[getInMemoryCacheSize( )] ;
		csapThreads = new int[getInMemoryCacheSize( )] ;
		totalFileDescriptors = new int[getInMemoryCacheSize( )] ;
		csapFileDescriptors = new int[getInMemoryCacheSize( )] ;
		networkConns = new int[getInMemoryCacheSize( )] ;
		networkWait = new int[getInMemoryCacheSize( )] ;
		networkTimeWait = new int[getInMemoryCacheSize( )] ;

		usrCpuLevel = new int[getInMemoryCacheSize( )] ;
		memoryAvailableLessCache = new int[getInMemoryCacheSize( )] ;
		memoryBufferCache = new int[getInMemoryCacheSize( )] ;
		sysCpuLevel = new int[getInMemoryCacheSize( )] ;
		ioLevel = new int[getInMemoryCacheSize( )] ;
		loadLevel = new double[getInMemoryCacheSize( )] ;

		cpuTestTime = new double[getInMemoryCacheSize( )] ;
		diskTestTime = new double[getInMemoryCacheSize( )] ;

		ioReads = new int[getInMemoryCacheSize( )] ;
		ioWrites = new int[getInMemoryCacheSize( )] ;

		networkReceived = new double[getInMemoryCacheSize( )] ;
		networkTransmited = new double[getInMemoryCacheSize( )] ;

		// deviceUtilizationReport = osManager.device_utilization();
		deviceUtilization = new int[MAX_DEVICES][getInMemoryCacheSize( )] ;

		collectionMs = new long[getInMemoryCacheSize( )] ;

		this.command = "mpstat " + intervalSeconds ;
		// this.uploadUrl = uploadUrl;

		// Initialize values so that UI can display without waiting for first
		// interval to occur
		openFiles[0] = -1 ;
		totalThreads[0] = -1 ;
		csapThreads[0] = -1 ;
		totalFileDescriptors[0] = -1 ;
		csapFileDescriptors[0] = -1 ;

		networkConns[0] = -1 ;
		networkWait[0] = -1 ;
		networkTimeWait[0] = -1 ;

		usrCpuLevel[0] = -1 ;
		memoryAvailableLessCache[0] = -1 ;
		memoryBufferCache[0] = -1 ;
		sysCpuLevel[0] = -1 ;
		ioLevel[0] = -1 ;
		loadLevel[0] = 0 ;

		diskTestTime[0] = 0 ;
		cpuTestTime[0] = 0 ;

		ioReads[0] = 0 ;
		ioWrites[0] = 0 ;

		networkReceived[0] = 0 ;
		networkTransmited[0] = 0 ;

		IntStream.range( 0, MAX_DEVICES - 1 )
				.forEach( item -> {

					deviceUtilization[item][0] = 0 ;

				} ) ;
		;

		collectionMs[0] = new Date( ).getTime( ) ;

		setLastAddedElementIndex( 0 ) ;

		// logger.warn("Construction");
		// hack for for windows
		if ( Application.isRunningOnDesktop( ) ) {

			buildTestData( osManager, intervalSeconds ) ;

		}

		mpstatThread = new Thread( this ) ;
		mpstatThread.start( ) ;

	}

	Thread mpstatThread ;

	public void buildTestData ( OsManager osManager , int intervalSeconds ) {

		logger.info( CsapApplication.testHeader( "Generating test data." ) ) ;
		osManager.wait_for_initial_process_status_scan( 10 ) ;

		long currMs = System.currentTimeMillis( ) ;
		int collectionIndex = 0 ;
		Random rg = new Random( System.currentTimeMillis( ) ) ;

		while ( collectionIndex < getInMemoryCacheSize( ) ) {

			usrCpuLevel[collectionIndex] = rg.nextInt( 100 ) ;

			// memLevel[i] = rg.nextInt(2000);
			// bufLevel[i] = rg.nextInt(8000);
			try {

				openFiles[collectionIndex] = osManager.getHostSummaryItem( "openFiles" ) ;
				totalThreads[collectionIndex] = osManager.getHostSummaryItem( "totalThreads" ) ;
				csapThreads[collectionIndex] = osManager.getHostSummaryItem( "csapThreads" ) ;
				totalFileDescriptors[collectionIndex] = osManager.getHostSummaryItem( "totalFileDescriptors" ) ;
				csapFileDescriptors[collectionIndex] = osManager.getHostSummaryItem( "csapFileDescriptors" ) ;
				networkConns[collectionIndex] = osManager.getHostSummaryItem( "networkConns" ) ;
				networkWait[collectionIndex] = osManager.getHostSummaryItem( "networkWait" ) ;
				networkTimeWait[collectionIndex] = osManager.getHostSummaryItem( "networkTimeWait" ) ;

				memoryAvailableLessCache[collectionIndex] = osManager.getMemoryCacheSize( ) ;

				memoryBufferCache[collectionIndex] = osManager.getMemoryAvailbleLessCache( ) ;

			} catch ( Exception e ) {

				logger.error( "Failed parsing memory json: \n"
						+ osManager.getCachedMemoryMetrics( ),
						e ) ;
				memoryAvailableLessCache[collectionIndex] = -1 ;
				memoryBufferCache[collectionIndex] = -1 ;
				openFiles[collectionIndex] = -1 ;
				totalThreads[collectionIndex] = -1 ;
				csapThreads[collectionIndex] = -1 ;
				totalFileDescriptors[collectionIndex] = -1 ;
				csapFileDescriptors[collectionIndex] = -1 ;
				networkConns[collectionIndex] = -1 ;
				networkWait[collectionIndex] = -1 ;
				networkTimeWait[collectionIndex] = -1 ;

			}

			cpuTestTime[collectionIndex] = rg.nextInt( 6 ) + 1.1 ;
			diskTestTime[collectionIndex] = rg.nextInt( DISK_TEST_IO_MAX ) + 1.1 ;

			ioReads[collectionIndex] = rg.nextInt( DISK_TEST_IO_MAX ) ;
			ioWrites[collectionIndex] = rg.nextInt( DISK_TEST_IO_MAX ) ;

			networkReceived[collectionIndex] = rg.nextInt( DISK_TEST_IO_MAX ) ;
			networkTransmited[collectionIndex] = rg.nextInt( DISK_TEST_IO_MAX ) ;

			deviceUtilizationReport = osManager.device_utilization( ) ;

			for ( int deviceIndex = 0; deviceIndex < MAX_DEVICES && deviceIndex < deviceUtilizationReport
					.size( ); deviceIndex++ ) {

				deviceUtilization[deviceIndex][collectionIndex] = rg.nextInt( DISK_TEST_IO_MAX ) ;

			}

			sysCpuLevel[collectionIndex] = rg.nextInt( 100 ) ;
			ioLevel[collectionIndex] = rg.nextInt( 100 ) ;
			loadLevel[collectionIndex] = Double
					.valueOf( decimalFormat2Places.format( rg.nextDouble( ) * 4 ) ) ;
			collectionMs[collectionIndex] = currMs
					- ( ( getInMemoryCacheSize( ) - collectionIndex ) * intervalSeconds * 1000 ) ;
			collectionIndex++ ;

		}

		lastAddedElementIndex = getInMemoryCacheSize( ) - 1 ;

	}

	public final static int DISK_TEST_IO_MAX = 5 ;

	/**
	 * Kill off the spawned threads - triggered from ServiceRequests
	 */
	public void shutdown ( ) {

		logger.debug( "*************** Shutting down  **********************" ) ;

		if ( mpstatLinuxProcess != null ) {

			mpstatLinuxProcess.destroy( ) ;

		}

		super.shutdown( ) ;

		if ( mpstatThread != null ) {

			mpstatThread.interrupt( ) ;

		}

	}

	Process mpstatLinuxProcess = null ;

	@Override
	public void run ( ) {

		// runs continuously using MPstat output to control gathering.
		Thread.currentThread( ).setName( "Csap" +
				this.getClass( ).getSimpleName( ) + "_" + collectionIntervalSeconds ) ;

		try {

			// Spread out events
			Thread.sleep( 10000 ) ;

			if ( this.collectionIntervalSeconds >= 60 ) {

				Thread.sleep( ( 30 + rg.nextInt( 30 ) ) * 1000 ) ;

			}

		} catch ( InterruptedException e1 ) {

		}

		try {

			List<String> linuxCommandString ;
			linuxCommandString = Arrays.asList( "bash", "-c", command ) ;

			while ( isKeepRunning( ) ) {

				logger.warn( "Launching {}", command ) ;
				runLinuxMpStatAndMonitorOutput( linuxCommandString ) ;

			}

		} catch ( Exception e ) {

			logger.error( "Exception in processing", e ) ;

		}

	}

	final com.sun.management.OperatingSystemMXBean osStats = (com.sun.management.OperatingSystemMXBean) ManagementFactory
			.getOperatingSystemMXBean( ) ;

	DecimalFormat decimalFormat2Places = new DecimalFormat( "###.##" ) ;

	int allowPsDebug = 0 ;

	private String runLinuxMpStatAndMonitorOutput ( List<String> params ) {

		ProcessBuilder processBuilder = new ProcessBuilder( params ) ;
		processBuilder.redirectErrorStream( true ) ;

		mpstatLinuxProcess = null ;
		String result = "Result from executing: " ;
		BufferedReader stdOutAndErrReader = null ; // stdout and error combined
		InputStreamReader isReader = null ;

		try {
			// very important to pick up paths from parent!!
			// logger.info( "intervalSeconds " + intervalSeconds + " spawning: "
			// + params.get(0));

			if ( ! Application.isRunningOnDesktop( ) ) {

				mpstatLinuxProcess = processBuilder.start( ) ;
				isReader = new InputStreamReader(
						mpstatLinuxProcess.getInputStream( ) ) ;
				stdOutAndErrReader = new BufferedReader( isReader ) ;

			}

			while ( isKeepRunning( )
					&& ( mpstatLinuxProcess == null || mpstatLinuxProcess.isAlive( ) ) ) {

				collectOsResources( stdOutAndErrReader ) ;

			}

			logger.debug( "Done {}", params.get( 0 ) ) ;

		} catch ( Exception e ) {

			if ( ! isKeepRunning( ) ) {

				logger.warn( "Shutdown in progress" ) ;

			} else {

				logger.error(
						"This should never happen, maybe someone killed the mpstat",
						e ) ;

			}

		} finally {

			if ( isReader != null ) {

				try {

					isReader.close( ) ;

				} catch ( IOException e ) {

					logger.error( "failed closing reader", e ) ;

				}

			}

			if ( mpstatLinuxProcess != null ) {

				mpstatLinuxProcess.destroy( ) ;

			}

			if ( stdOutAndErrReader != null ) {

				try {

					stdOutAndErrReader.close( ) ;

				} catch ( IOException e ) {

					logger.error( "failed closing reader", e ) ;

				}

			}

		}

		logger.warn( CsapApplication.header( "MpStat Thread Exited" ) ) ;

		return result ;

	}

	private void collectOsResources ( BufferedReader stdOutAndErrReader )
		throws InterruptedException ,
		NumberFormatException ,
		IOException {

		String mpstatOutputLine ;

		if ( Application.isRunningOnDesktop( ) ) {

			mpstatOutputLine = "09:24:22 AM  all    50.35    0.00    30.35    5.06    0.00    0.05    0.00    0.00   98.19" ;
			Thread.sleep( collectionIntervalSeconds * 1000 ) ;

		} else {

			// Put thread to sleep unless command output is available
			while ( isKeepRunning( ) &&
					mpstatLinuxProcess.isAlive( ) && ! stdOutAndErrReader.ready( ) ) {

				try {
					// Only do work if needed

					logger.debug( "Sleeping for 1 seconds until next top output is available" ) ;

					Thread.sleep( 1000 ) ;

				} catch ( InterruptedException e1 ) {

					logger.error( "Got interuption while sleeping on top output" ) ;

				}

			}

			mpstatOutputLine = stdOutAndErrReader.readLine( ) ;

			if ( mpstatOutputLine == null ) {

				logger.error( "Null output from mpstat, did it crash? " ) ;

			}

		}

		logger.debug( "mpstat line: {}", mpstatOutputLine ) ;
		// logger.info( "Got a line for: " + intervalSeconds +
		// " lastAddedElementIndex: " + lastAddedElementIndex);
		int allTokenIndex = mpstatOutputLine.indexOf( "all" ) ;

		if ( allTokenIndex != -1 ) {

			// item has been read in.
			// Timestamp can mess with columns - so substring to strip
			// off
			// 12:56:41 all 0.15 0.00 0.00 1.10 0.00 0.05 0.00 98.70
			// 1057.77
			// 12:56:41 PM all 0.15 0.00 0.00 1.10 0.00 0.05 0.00 98.70
			// 1057.77
			String[] mpStatColumns = mpstatOutputLine
					.substring( allTokenIndex + 3 )
					.trim( )
					.split( " +" ) ;
			// logger.info(" mpstat line: " +
			// mpstatOutputLine.substring(allTokenIndex+3).trim() ) ;
			int nextIndex = getLastAddedElementIndex( ) + 1 ;

			if ( nextIndex >= getInMemoryCacheSize( ) ) {

				nextIndex = 0 ; // wrap the array

			}

			try {

				collectionMs[nextIndex] = new Date( ).getTime( ) ;

				usrCpuLevel[nextIndex] = Math.round( Float
						.parseFloat( mpStatColumns[0] ) ) ;
				sysCpuLevel[nextIndex] = Math.round( Float
						.parseFloat( mpStatColumns[2] ) ) ;
				ioLevel[nextIndex] = Math.round( Float
						.parseFloat( mpStatColumns[3] ) ) ;
				// collectionMs[ nextIndex ] = System.currentTimeMillis() ;

				openFiles[nextIndex] = osManager.getHostSummaryItem( "openFiles" ) ;
				totalThreads[nextIndex] = osManager.getHostSummaryItem( "totalThreads" ) ;
				csapThreads[nextIndex] = osManager.getHostSummaryItem( "csapThreads" ) ;
				totalFileDescriptors[nextIndex] = osManager.getHostSummaryItem( "totalFileDescriptors" ) ;
				csapFileDescriptors[nextIndex] = osManager.getHostSummaryItem( "csapFileDescriptors" ) ;
				networkConns[nextIndex] = osManager.getHostSummaryItem( "networkConns" ) ;
				networkWait[nextIndex] = osManager.getHostSummaryItem( "networkWait" ) ;
				networkTimeWait[nextIndex] = osManager.getHostSummaryItem( "networkTimeWait" ) ;

				diskTestTime[nextIndex] = Double.valueOf( decimalFormat2Places
						.format( osManager.getInfraRunner( ).getLastDiskTimeInSeconds( ) ) ) ;

				cpuTestTime[nextIndex] = Double.valueOf( decimalFormat2Places
						.format( osManager.getInfraRunner( ).getLastCpuTimeInSeconds( ) ) ) ;

				buildIoStatDeltaReport( nextIndex ) ;

				buildNetworkDeltaReport( nextIndex ) ;

				deviceUtilizationReport = osManager.device_utilization( ) ;

				for ( int j = 0; j < MAX_DEVICES && j < deviceUtilizationReport.size( ); j++ ) {

					deviceUtilization[j][nextIndex] = deviceUtilizationReport.get( j ).get( "percentCapacity" ).asInt(
							-1 ) ;

				}

				// aggregate memory
				memoryAvailableLessCache[nextIndex] = osManager.getMemoryAvailbleLessCache( ) ;

				checkForLowMemoryEventGeneration( nextIndex ) ;
				// + osManager.getMem().get("ram").get(5).asInt()
				// + osManager.getMem().get("ram").get(3)
				// .asInt();

				memoryBufferCache[nextIndex] = osManager.getMemoryCacheSize( ) ;

				loadLevel[nextIndex] = Double.valueOf( decimalFormat2Places
						.format( osStats.getSystemLoadAverage( ) ) ) ;

			} catch ( Exception e ) {

				logger.error( "Failed parsing OS stats: {}",
						CSAP.buildCsapStack( e ) ) ;
				memoryAvailableLessCache[nextIndex] = -1 ;
				memoryBufferCache[nextIndex] = -1 ;
				openFiles[nextIndex] = -1 ;
				totalThreads[nextIndex] = -1 ;
				csapThreads[nextIndex] = -1 ;
				totalFileDescriptors[nextIndex] = -1 ;
				csapFileDescriptors[nextIndex] = -1 ;
				networkConns[nextIndex] = -1 ;
				networkWait[nextIndex] = -1 ;
				networkTimeWait[nextIndex] = -1 ;
				diskTestTime[nextIndex] = 0 ;
				cpuTestTime[nextIndex] = 0 ;

				ioReads[nextIndex] = 0 ;
				ioWrites[nextIndex] = 0 ;

				networkReceived[nextIndex] = 0 ;
				networkTransmited[nextIndex] = 0 ;

				for ( int j = 0; j < MAX_DEVICES && j < deviceUtilizationReport.size( ); j++ ) {

					deviceUtilization[j][nextIndex] = 0 ;

				}

			}

			setLastAddedElementIndex( nextIndex ) ;

			peformUploadIfNeeded( ) ;

			logger.debug( "Got result, then blocking on next output: {}", mpstatOutputLine ) ;

			// Thread.currentThread().sleep( intervalSeconds * 1000 ) ;
		}

	}

	/**
	 * Special case: use iostat deltas to determine reads and writes across all
	 * disks in collection interval
	 */
	private JsonNode previousDiskActivity = null ;

	private void buildIoStatDeltaReport ( int nextIndex ) {

		// DeltaReports for disk read and writes
		int ioReadChange = 0 ;
		int ioWriteChange = 0 ;

		JsonNode latestDiskActivity = osManager.diskReport( ) ;

		if ( previousDiskActivity != null ) {

			ioReadChange = //
					latestDiskActivity.path( "reads" ).asInt( -1 )
							- previousDiskActivity.path( "reads" ).asInt( ) ;

			ioWriteChange = //
					latestDiskActivity.path( "writes" ).asInt( -1 )
							- previousDiskActivity.path( "writes" ).asInt( ) ;

		}

		previousDiskActivity = latestDiskActivity ;
		ioReads[nextIndex] = ioReadChange ;
		ioWrites[nextIndex] = ioWriteChange ;

	}

	/**
	 * Special case: use network transmit and receive relies on deltas
	 */
	private JsonNode previousNetworkActivity = null ;

	private void buildNetworkDeltaReport ( int nextIndex ) {

		// DeltaReports for disk read and writes
		double networkReadChange = 0 ;
		double networkTransmitChange = 0 ;
		JsonNode latestNetworkActivity = osManager.networkReceiveAndTransmit( ) ;

		if ( previousNetworkActivity != null ) {

			networkReadChange = //
					latestNetworkActivity.path( OsManager.RECEIVE_MB ).asDouble( -1 )
							- previousNetworkActivity.path( OsManager.RECEIVE_MB ).asDouble( ) ;
			networkTransmitChange = //
					latestNetworkActivity.path( OsManager.TRANSMIT_MB ).asDouble( -1 )
							- previousNetworkActivity.path( OsManager.TRANSMIT_MB ).asDouble( ) ;

		}

		previousNetworkActivity = latestNetworkActivity ;
		networkReceived[nextIndex] = CSAP.roundIt( networkReadChange, 2 ) ;
		networkTransmited[nextIndex] = CSAP.roundIt( networkTransmitChange, 2 ) ;

	}

	private void checkForLowMemoryEventGeneration ( int nextIndex ) {

		// selectively enabled
		if ( collectionIntervalSeconds == csapApplication
				.rootProjectEnvSettings( )
				.getPsDumpInterval( )
				&& allowPsDebug < csapApplication
						.rootProjectEnvSettings( )
						.getPsDumpCount( )
				&& memoryAvailableLessCache[nextIndex] < csapApplication
						.rootProjectEnvSettings( )
						.getPsDumpLowMemoryInMb( ) ) {

			// logger.warn("Low memory" + memLevel[nextIndex]);
			allowPsDebug++ ;

			String psOutput = osManager.performMemoryProcessList( false, false, true ) ;

			ObjectNode memoryWarning = jacksonMapper.createObjectNode( ) ;
			memoryWarning.put( "currentFree", memoryAvailableLessCache[nextIndex] ) ;
			memoryWarning.put( "configuredMinimum", csapApplication
					.rootProjectEnvSettings( )
					.getPsDumpLowMemoryInMb( ) ) ;
			memoryWarning.put( "csapText", psOutput ) ;

			csapApplication.getEventClient( ).publishEvent(
					CsapEvents.CSAP_SYSTEM_CATEGORY + "/memory/low",
					memoryAvailableLessCache[nextIndex] + " Remaining", null, memoryWarning ) ;

			logger.warn( "Low memory detected on VM, refer to events log: " + memoryAvailableLessCache[nextIndex]
					+ " MB remaining." ) ;

		}

	}

	public final static String VM_METRICS_EVENT = METRICS_EVENT + "/" + CsapApplication.COLLECTION_HOST + "/" ;

	protected String uploadMetrics ( int iterationsBetweenUploads ) {

		String result = "FAILED" ;

		try {

			allowPsDebug = 0 ;

			ObjectNode metricSample = buildCollectionReport( true, null, iterationsBetweenUploads, 0 ) ;

			// new Event publisher - it checks if publish is enabled. First time
			// full attributes, then only sub attributes
			if ( vmCorrellationAttributes == null ) {

				vmCorrellationAttributes = jacksonMapper.createObjectNode( ) ;
				vmCorrellationAttributes.put( "id", CsapApplication.COLLECTION_HOST + "_"
						+ collectionIntervalSeconds ) ;
				vmCorrellationAttributes.put( "source", VM_METRICS_EVENT + collectionIntervalSeconds ) ;
				vmCorrellationAttributes.put( "hostName", csapApplication.getCsapHostShortname( ) ) ;

				// full upload. We could make call to event service to see if
				// they match...for now we do on restarts
				logger.info( "Uploading VM attributes, count: {}", iterationsBetweenUploads ) ;

				csapApplication.getEventClient( ).publishEvent( VM_METRICS_EVENT + collectionIntervalSeconds
						+ "/attributes", "Modified",
						null,
						latestAttributeReport ) ;

			}

			// Send normalized data
			metricSample.set( "attributes", vmCorrellationAttributes ) ;

			csapApplication.getEventClient( ).publishEvent( VM_METRICS_EVENT + collectionIntervalSeconds + "/data",
					"Upload", null,
					metricSample ) ;

			publishSummaryReport( CsapApplication.COLLECTION_HOST ) ;

		} catch ( Exception e ) {

			result = "Failed Upload: "
					+ CSAP.buildCsapStack( e ) ;
			logger.error( "Failed upload {}", result ) ;

		}

		return result ;

	}

	private ObjectNode vmCorrellationAttributes = null ;

	public String getUsrCpuLevel ( ) {

		// logger.info("currIndex:" + lastAddedElementIndex) ;
		return Integer.toString( usrCpuLevel[getLastAddedElementIndex( )] ) ;

	}

	public String getSysCpuLevel ( ) {

		// logger.info("currIndex:" + lastAddedElementIndex) ;
		return Integer.toString( sysCpuLevel[getLastAddedElementIndex( )] ) ;

	}

	public int latestNetworkConnections ( ) {

		return networkConns[getLastAddedElementIndex( )] ;

	}

	public int latestNetworkWait ( ) {

		return networkWait[getLastAddedElementIndex( )] ;

	}

	public int latestNetworkTimeWait ( ) {

		return networkTimeWait[getLastAddedElementIndex( )] ;

	}

	public double latestNetworkReceived ( ) {

		return networkReceived[getLastAddedElementIndex( )] ;

	}

	public double latestNetworkTransmitted ( ) {

		return networkTransmited[getLastAddedElementIndex( )] ;

	}

	public int getLatestCpu ( ) {

		return usrCpuLevel[getLastAddedElementIndex( )] + sysCpuLevel[getLastAddedElementIndex( )] ;

	}

	public double getLatestLoad ( ) {

		return loadLevel[getLastAddedElementIndex( )] ;

	}

	public int getLatestIoWait ( ) {

		return ioLevel[getLastAddedElementIndex( )] ;

	}

	public void clear ( ) {

		setLastAddedElementIndex( 0 ) ;

		for ( int i = 0; i < collectionMs.length; i++ ) {

			collectionMs[i] = 0 ;

		}

	}

	// serviceName array is a dummy param so interface is consistent
	public ObjectNode buildCollectionReport (
												boolean isUpdateSummary ,
												String[] serviceNameArrayNotUsed ,
												int requestedSampleSize ,
												int skipFirstItems ,
												String... customArgs ) {

		var timer = csapApplication.metrics( ).startTimer( ) ;

		// logger.info("numSamples: " + numSamples + " skipFirstItems: " +
		// skipFirstItems ) ;
		var rootNode = jacksonMapper.createObjectNode( ) ;

		rootNode.set( "attributes", buildAttributeReport( requestedSampleSize, skipFirstItems ) ) ;

		var dataSection = rootNode.putObject( DATA_JSON ) ;

		var timestamps = dataSection.putArray( "timeStamp" ) ;
		var usrArray = dataSection.putArray( "usrCpu" ) ;
		var memArray = dataSection.putArray( "memFree" ) ;
		var bufArray = dataSection.putArray( "bufFree" ) ;
		var sysArray = dataSection.putArray( "sysCpu" ) ;
		var ioArray = dataSection.putArray( "IO" ) ;
		var loadArray = dataSection.putArray( "load" ) ;

		var openFilesArray = dataSection.putArray( "openFiles" ) ;
		var totalThreadsArray = dataSection.putArray( "totalThreads" ) ;
		var csapThreadsArray = dataSection.putArray( "csapThreads" ) ;
		var totalFileDescriptorsArray = dataSection.putArray( "totalFileDescriptors" ) ;
		var csapFileDescriptorsArray = dataSection.putArray( "csapFileDescriptors" ) ;
		var networkConnsArray = dataSection.putArray( "networkConns" ) ;
		var networkWaitArray = dataSection.putArray( "networkWait" ) ;
		var networkTimeWaitArray = dataSection.putArray( "networkTimeWait" ) ;

		var diskTestArray = dataSection.putArray( "diskTest" ) ;
		var cpuTestArray = dataSection.putArray( "cpuTest" ) ;

		var ioReadArray = dataSection.putArray( IO_READS ) ;
		var ioWriteArray = dataSection.putArray( IO_WRITES ) ;

		var networkReceivedArray = dataSection.putArray( NETWORK_RECEIVED ) ;
		var networkTransmittedArray = dataSection.putArray( NETWORK_TRANSMITTED ) ;

		for ( int j = 0; j < MAX_DEVICES && j < deviceUtilizationReport.size( ); j++ ) {

			dataSection.putArray( getIoUtilGraphKey( j ) ) ;

		}

		int i = getLastAddedElementIndex( ) ;
		boolean wrapped = false ;

		int curIndex = 0 ;
		int curSampleCount = 0 ;

		if ( requestedSampleSize == SHOW_ALL_DATA ) {

			curSampleCount = -999 ;

		}

		int totalUsrCpu = 0 ;
		int totalSysCpu = 0 ;
		int totalMemFree = 0 ;
		int totalBufFree = 0 ;
		int totalIo = 0 ;
		int alertsCount = 0 ;
		double totalLoad = 0 ;
		int totalFiles = 0 ;
		int threadsTotal = 0 ;
		int csapThreadsTotal = 0 ;
		int fdTotal = 0 ;
		int csapFdTotal = 0 ;
		int socketTotal = 0 ;
		int socketWaitTotal = 0 ;
		int socketTimeWaitTotal = 0 ;

		double totalDiskTestTime = 0 ;
		double totalCpuTestTime = 0 ;

		int totalIoReads = 0 ;
		int totalIoWrites = 0 ;

		double totalNetworkReceived = 0 ;
		double totalNetworkTransmitted = 0 ;

		int[] totalDeviceUtil = new int[deviceUtilizationReport.size( )] ;

		for ( int j = 0; j < MAX_DEVICES && j < deviceUtilizationReport.size( ); j++ ) {

			totalDeviceUtil[j] = 0 ;

		}

		int numberOfSamples = 0 ;

		while ( ( collectionMs[i] <= collectionMs[getLastAddedElementIndex( )] )
				&& ( collectionMs[i] != 0 )
				&& ( curSampleCount < requestedSampleSize ) ) {

			// logger.info("Adding Element " + i + " collectionMs[i] " +
			// collectionMs[i] ) ;
			if ( curIndex++ >= skipFirstItems ) {

				if ( requestedSampleSize != SHOW_ALL_DATA ) {

					curSampleCount++ ;

				}

				// long offset = collectionMs[i]-timeZoneMinutesFromGmt*60000 ;
				numberOfSamples++ ;
				timestamps.add( Long.toString( collectionMs[i] ) ) ;

				usrArray.add( usrCpuLevel[i] ) ;
				totalUsrCpu += usrCpuLevel[i] ;

				sysArray.add( sysCpuLevel[i] ) ;
				totalSysCpu += sysCpuLevel[i] ;

				// High CPU or load means we should look very closely
				if ( ( usrCpuLevel[i] + sysCpuLevel[i] ) > 60 || loadLevel[i] > osStats.getAvailableProcessors( ) ) {

					alertsCount++ ;

				}

				memArray.add( memoryAvailableLessCache[i] ) ;
				totalMemFree += memoryAvailableLessCache[i] ;

				bufArray.add( memoryBufferCache[i] ) ;
				totalBufFree += memoryBufferCache[i] ;

				ioArray.add( ioLevel[i] ) ;
				totalIo += ioLevel[i] ;

				loadArray.add( loadLevel[i] ) ;
				totalLoad += loadLevel[i] ;

				if ( loadLevel[i] == -1 ) {

					totalLoad += 1 ;

				}

				openFilesArray.add( openFiles[i] ) ;
				totalFiles += openFiles[i] ;

				totalThreadsArray.add( totalThreads[i] ) ;
				threadsTotal += totalThreads[i] ;

				csapThreadsArray.add( csapThreads[i] ) ;
				csapThreadsTotal += csapThreads[i] ;

				totalFileDescriptorsArray.add( totalFileDescriptors[i] ) ;
				fdTotal += totalFileDescriptors[i] ;

				csapFileDescriptorsArray.add( csapFileDescriptors[i] ) ;
				csapFdTotal += csapFileDescriptors[i] ;

				networkConnsArray.add( networkConns[i] ) ;
				socketTotal += networkConns[i] ;

				networkWaitArray.add( networkWait[i] ) ;
				socketWaitTotal += networkWait[i] ;

				networkTimeWaitArray.add( networkTimeWait[i] ) ;
				socketTimeWaitTotal += networkTimeWait[i] ;

				diskTestArray.add( diskTestTime[i] ) ;
				totalDiskTestTime += diskTestTime[i] ;

				cpuTestArray.add( cpuTestTime[i] ) ;
				totalCpuTestTime += cpuTestTime[i] ;

				ioReadArray.add( ioReads[i] ) ;
				totalIoReads += ioReads[i] ;

				ioWriteArray.add( ioWrites[i] ) ;
				totalIoWrites += ioWrites[i] ;

				networkReceivedArray.add( networkReceived[i] ) ;
				totalNetworkReceived += networkReceived[i] ;

				networkTransmittedArray.add( networkTransmited[i] ) ;
				totalNetworkTransmitted += networkTransmited[i] ;

				for ( int j = 0; j < MAX_DEVICES && j < deviceUtilizationReport.size( ); j++ ) {

					ArrayNode curArray = (ArrayNode) dataSection.get( getIoUtilGraphKey( j ) ) ;
					curArray.add( deviceUtilization[j][i] ) ;
					totalDeviceUtil[j] = totalDeviceUtil[j] + deviceUtilization[j][i] ;

				}

				// logger.debug(resultAL.get( resultAL.size()-1) );
			}

			i-- ;

			if ( i < 0 ) {

				wrapped = true ;
				i = getInMemoryCacheSize( ) - 1 ;

			}

			// only go around once
			if ( wrapped && ( i == getLastAddedElementIndex( ) ) ) {

				break ;

			}

		}

		// if ( isUpdateSummary ) {

		var summaryReport = jacksonMapper.createObjectNode( ) ;

		summaryReport.put( "cpuCountAvg", osStats.getAvailableProcessors( ) ) ;

		try {

			summaryReport.put( "memoryInMbAvg", osStats.getTotalPhysicalMemorySize( ) / 1024 / 1024 ) ;
			summaryReport.put( "swapInMbAvg", osStats.getTotalSwapSpaceSize( ) / 1024 / 1024 ) ;

		} catch ( Exception e ) {

			summaryReport.put( "memoryInMbAvg", -1 ) ;
			summaryReport.put( "swapInMbAvg", -1 ) ;

		}

		summaryReport.put( "totActivity", eventCount( ) ) ;
		summaryReport.put( OsSharedEnum.numberOfSamples.reportKey( ), numberOfSamples ) ;
		summaryReport.put( OsSharedEnum.totalUsrCpu.reportKey( ), totalUsrCpu ) ;
		summaryReport.put( OsSharedEnum.totalSysCpu.reportKey( ), totalSysCpu ) ;
		summaryReport.put( OsSharedEnum.totalMemFree.reportKey( ), totalMemFree ) ;
		summaryReport.put( OsSharedEnum.totalBufFree.reportKey( ), totalBufFree ) ;
		summaryReport.put( OsSharedEnum.totalIo.reportKey( ), totalIo ) ;
		summaryReport.put( OsSharedEnum.alertsCount.reportKey( ), alertsCount ) ;
		summaryReport.put( OsSharedEnum.totalLoad.reportKey( ), totalLoad ) ;
		summaryReport.put( OsSharedEnum.totalFiles.reportKey( ), totalFiles ) ;
		summaryReport.put( OsSharedEnum.threadsTotal.reportKey( ), threadsTotal ) ;
		summaryReport.put( OsSharedEnum.csapThreadsTotal.reportKey( ), csapThreadsTotal ) ;
		summaryReport.put( OsSharedEnum.fdTotal.reportKey( ), fdTotal ) ;
		summaryReport.put( OsSharedEnum.csapFdTotal.reportKey( ), csapFdTotal ) ;

		summaryReport.put( OsSharedEnum.socketsActive.reportKey( ), socketTotal ) ;
		summaryReport.put( OsSharedEnum.socketsCloseWait.reportKey( ), socketWaitTotal ) ;
		summaryReport.put( OsSharedEnum.socketsTimeWaitTotal.reportKey( ), socketTimeWaitTotal ) ;

		summaryReport.put( OsSharedEnum.totalDiskTestTime.reportKey( ), totalDiskTestTime ) ;
		summaryReport.put( OsSharedEnum.totalCpuTestTime.reportKey( ), totalCpuTestTime ) ;

		summaryReport.put( OsSharedEnum.totalIoReads.reportKey( ), totalIoReads ) ;
		summaryReport.put( OsSharedEnum.totalIoWrites.reportKey( ), totalIoWrites ) ;

		summaryReport.put( OsSharedEnum.totalNetworkReceived.reportKey( ), totalNetworkReceived ) ;
		summaryReport.put( OsSharedEnum.totalNetworkTransmitted.reportKey( ), totalNetworkTransmitted ) ;

		for ( int j = 0; j < MAX_DEVICES && j < deviceUtilizationReport.size( ); j++ ) {

			summaryReport.put( "total" + getIoUtilGraphKey( j ), totalDeviceUtil[j] ) ;

		}

		addSummary( summaryReport, isUpdateSummary ) ;
		// }

		csapApplication.metrics( ).stopTimer( timer, "collect.os-shared.build-report" ) ;

		return rootNode ;

	}

	// Simple Object Map
	@Override
	protected JsonNode buildSummaryReport ( boolean isSecondary ) {

		// Step 1 - build map with total for services
		ObjectNode summaryTotalJson = jacksonMapper.createObjectNode( ) ;

		ArrayNode cache = summary24HourCache ;

		if ( isSecondary ) {

			cache = summary24HourApplicationCache ;

		}

		for ( JsonNode intervalReport : cache ) {

			Iterator<String> fields = intervalReport.fieldNames( ) ;

			while ( fields.hasNext( ) ) {

				String field = fields.next( ) ;

				addItemToTotals( (ObjectNode) intervalReport, summaryTotalJson, field ) ;

			}

		}

		return summaryTotalJson ;

	}

	private String getIoUtilGraphKey ( int j ) {

		return deviceUtilizationReport.get( j ).path( "name" ).asText( "notFound" ) ;

	}

	/**
	 * Not using remote calls. Might be worth it if additional activities are
	 * published outside of agent
	 */
	private int lastCount = 0 ;

	public int eventCount ( ) {

		int count = getCsapApplication( ).getEventClient( ).getNumberPublished( ) - lastCount ;
		lastCount = getCsapApplication( ).getEventClient( ).getNumberPublished( ) ;
		return count ;

	}

	private ObjectNode latestAttributeReport = null ;

	private ObjectNode buildAttributeReport (
												int requestedSampleSize ,
												int skipFirstItems ) {

		var attributeReport = jacksonMapper.createObjectNode( ) ;

		attributeReport.put( "id", CsapApplication.COLLECTION_HOST + "_" + collectionIntervalSeconds ) ;
		attributeReport.put( "hostName", csapApplication.getCsapHostShortname( ) ) ;
		attributeReport.put( "metricName", "System Resource" ) ;
		attributeReport.put( "timezone", TIME_ZONE_OFFSET ) ;
		attributeReport.put( "description",
				"Contains usr,sys,io, and load level metrics" ) ;
		attributeReport.put( "sampleInterval", collectionIntervalSeconds ) ;
		attributeReport.put( "samplesRequested", requestedSampleSize ) ;
		attributeReport.put( "samplesOffset", skipFirstItems ) ;
		attributeReport.put( "currentTimeMillis", System.currentTimeMillis( ) ) ;
		attributeReport.put( "cpuCount", osStats.getAvailableProcessors( ) ) ;

		ObjectNode titlesObject = attributeReport.putObject( "titles" ) ;

		ObjectNode graphsObject = attributeReport.putObject( "graphs" ) ;

		titlesObject.put( "OS_MpStat", "Linux mpstat" ) ;
		ObjectNode resourceGraph = graphsObject.putObject( "OS_MpStat" ) ;
		resourceGraph.put( "usrCpu", "User CPU" ) ;
		resourceGraph.put( "sysCpu", "System CPU" ) ;
		resourceGraph.put( "IO", "Input Output" ) ;

		titlesObject.put( "OS_Load", "CPU Load" ) ;
		ObjectNode loadGraph = graphsObject.putObject( "OS_Load" ) ;
		loadGraph.put( "load", "Load" ) ;
		loadGraph.put( "attributes_cpuCount", "Cpu Count" ) ;

		titlesObject.put( "InfraTest", "Infrastructure Tests (seconds)" ) ;
		ObjectNode infraGraph = graphsObject.putObject( "InfraTest" ) ;
		infraGraph.put( "diskTest", "Disk Test" ) ;
		infraGraph.put( "cpuTest", "CPU Test" ) ;

		titlesObject.put( "VmFiles", "Files Open" ) ;
		ObjectNode vmGraph = graphsObject.putObject( "VmFiles" ) ;
		vmGraph.put( "openFiles", "/proc/sys/fs/file-nr" ) ;
		vmGraph.put( "totalFileDescriptors", "lsof - users" ) ;
		vmGraph.put( "csapFileDescriptors", "lsof - " + System.getenv( "USER" ) ) ;

		titlesObject.put( IOSTAT_UTILIZATION, "Disk IO % Busy" ) ;
		ObjectNode diskUtilGraph = graphsObject.putObject( IOSTAT_UTILIZATION ) ;

		if ( deviceUtilizationReport == null ) {

			deviceUtilizationReport = osManager.device_utilization( ) ;

		}

		for ( int deviceIndex = 0; deviceIndex < MAX_DEVICES && deviceIndex < deviceUtilizationReport
				.size( ); deviceIndex++ ) {

			diskUtilGraph.put( getIoUtilGraphKey( deviceIndex ),
					"Device: " + deviceUtilizationReport.get( deviceIndex ).get( "name" ).asText( ) ) ;

		}

		titlesObject.put( "iostat", "Device IO (MB)" ) ;
		ObjectNode ioGraph = graphsObject.putObject( "iostat" ) ;
		ioGraph.put( IO_READS, "Disk Reads" ) ;
		ioGraph.put( IO_WRITES, "Disk Writes" ) ;
		ioGraph.put( NETWORK_RECEIVED, "Network Recd" ) ;
		ioGraph.put( NETWORK_TRANSMITTED, "Network Sent" ) ;

		titlesObject.put( "Network", "Network Connections" ) ;
		ObjectNode socketGraph = graphsObject.putObject( "Network" ) ;
		socketGraph.put( "networkConns", "Sockets Active" ) ;
		socketGraph.put( "networkWait", "Sockets Close Wait" ) ;
		socketGraph.put( "networkTimeWait", "Sockets Time Wait" ) ;

		titlesObject.put( "VmThreads", "Threads" ) ;
		ObjectNode threadGraph = graphsObject.putObject( "VmThreads" ) ;
		threadGraph.put( "totalThreads", "ALL" ) ;
		threadGraph.put( "csapThreads", System.getenv( "USER" ) ) ;

		titlesObject.put( "Memory_Remaining", "Memory <span class='highlight'>Available</span> (Mb)" ) ;
		ObjectNode memGraph = graphsObject.putObject( "Memory_Remaining" ) ;
		memGraph.put( "memFree", "Memory Aggregate" ) ;
		memGraph.put( "bufFree", "Buffer Cache" ) ;

		latestAttributeReport = attributeReport ;

		return attributeReport ;

	}
}
