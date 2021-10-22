package org.csap.agent.model ;

import java.util.ArrayList ;
import java.util.Arrays ;
import java.util.List ;

import org.apache.commons.lang3.StringUtils ;
import org.csap.agent.services.OsProcess ;
import org.csap.agent.stats.OsProcessEnum ;
import org.csap.agent.stats.service.JmxCommonEnum ;
import org.csap.helpers.CSAP ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;

import com.fasterxml.jackson.annotation.JsonIgnore ;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties ;
import com.fasterxml.jackson.annotation.JsonProperty ;
import com.fasterxml.jackson.databind.node.ObjectNode ;

/**
 * 
 * Stores latest collected data for container
 *
 */
@JsonIgnoreProperties ( ignoreUnknown = true )
public class ContainerState {

	final Logger logger = LoggerFactory.getLogger( getClass( ) ) ;

	public final static String JSON_KEY = "containers" ;
	public final static String PS_CPU = "cpuUtil" ;

	public final static String JSON_PATH_CPU = "/" + JSON_KEY + "/0/" + PS_CPU ;
	public final static String JSON_PATH_PID = "/" + JSON_KEY + "/0/pid" ;
	public final static String JSON_PATH_PRIORITY = "/" + JSON_KEY + "/0/currentProcessPriority" ;

	public final static String containerPath ( int containerIndex ) {

		return "/" + JSON_KEY + "/" + containerIndex ;

	}

	public final static String INACTIVE = "-" ;

	private static final String CPU_AUTO_MODE_TO_TRIGGER_HOUR_GLASS = "AUTO" ;

	public String deployedArtifacts = "" ;

	private String containerName ; // long name used in docker registry
	private String containerLabel ; // short name used in pod spec
	private String podNamespace ;
	private String podIp ;
	private String podName ; // may or may not be closely related to containerName

	private List<String> resourceViolations = new ArrayList<>( ) ;
	private String cpuUtil = INACTIVE ; // flag for inactive process
	private List<String> pid = new ArrayList<String>( ) ;
	private int rssMemory = 0 ;
	private int virtualMemory = 0 ;

	private boolean autoKillInProgress = false ;
	// Collected
	private long jmxHeartbeatMs = 0 ;
	private int numTomcatConns = 0 ;
	private int jvmThreadCount = 0 ;
	private int diskReadKb = 0 ;
	private int diskWriteKb = 0 ;
	private float topCpu = 0.0f ;
	private int diskUsageInMb = 0 ;
	private int fileCount = 0 ;
	private int socketCount = 0 ;
	private int threadCount = 0 ;

	private String currentProcessPriority = "0" ;
	private String runHeap = "" ;

	private ObjectNode healthReportCollected = null ;

	public void setHealthReportCollected ( ObjectNode healthReportCollected ) {

		this.healthReportCollected = healthReportCollected ;

	}

	// @JsonProperty ( HostKeys.healthReportCollected.jsonId )
	public ObjectNode getHealthReportCollected ( ) {

		// NEVER rename without updating healthReportKey
		return healthReportCollected ;

	}

	public ContainerState ( ) {

	}

	public ContainerState ( String containerName ) {

		this.containerName = containerName ;

	}

	public void addProcess ( OsProcess process ) {

		process.setMatched( true ) ;
		logger.debug( "Adding process resources: {}", process ) ;
		this.addCpuUtil( process.getCpu( ) ) ;
		this.addThreadCount( process.getThreads( ) ) ;
		this.addRssMemory( process.getRssMemory( ) ) ;
		this.addVirtualMemory( process.getVirtualMemory( ) ) ;
		this.addPid( process.getPid( ) ) ;
		this.currentProcessPriority = process.getPriority( ) ;

		this.currentProcessPriority = process.getPriority( ) ;

		int heapStart = process.getParameters( ).indexOf( "-Xmx" ) ;
		int heapEnd = process.getParameters( ).indexOf( " ", heapStart + 1 ) ;

		// logger.info("currLine: " + currLine + " heapCheck:" + heapStart + " heapEnd:
		// " + heapEnd) ;
		if ( heapStart != -1 && heapEnd != -1 ) {

			// addRunHeap( process.getParameters().substring( heapStart, heapEnd ) ) ;
			setRunHeap( process.getParameters( ).substring( heapStart, heapEnd ) ) ;

		} else {

			var args = process.getParameters( ) ;

			if ( args.length( ) > 25 ) {

				args = args.substring( 0, 25 ) ;

			}

			setRunHeap( args ) ;

		}

	}

	public void setPid ( List<String> pid ) {

		// new Error().printStackTrace();
		this.pid = pid ;

	}

	final public static String NO_PIDS = "-" ;

	public List<String> getPid ( ) {

		if ( pid.size( ) == 0 ) {

			return Arrays.asList( NO_PIDS ) ; // flag for empty pids

		}

		return new ArrayList<String>( pid ) ;

	}

	public String getPidsAsString ( ) {

		StringBuffer result = new StringBuffer( "" ) ;

		if ( pid.size( ) > 0 ) {

			pid.forEach( pid -> {

				if ( result.length( ) != 0 ) {

					result.append( " " ) ;

				}

				if ( pid.equals( NO_PIDS ) ) {

					result.append( "noMatches" ) ;

				} else {

					result.append( pid ) ;

				}

			} ) ;

		}

		return result.toString( ) ;

	}

	public void addPid ( String pid ) {

		this.pid.add( pid ) ;

	}

	public void setRssMemory ( int rssMemory ) {

		this.rssMemory = rssMemory ;

	}

	public void addRssMemory ( String rssMemory ) {

		try {

			int val = Integer.parseInt( rssMemory ) ;
			this.rssMemory += val ;

		} catch ( NumberFormatException e ) {

			logger.error( "Failed to parse rssMemory string: '{}' {}",
					rssMemory, CSAP.buildCsapStack( e ) ) ;

		}

	}

	public void setVirtualMemory ( int virtualMemory ) {

		this.virtualMemory = virtualMemory ;

	}

	public void addVirtualMemory ( String virtualMemory ) {

		try {

			int val = Integer.parseInt( virtualMemory ) ;
			this.virtualMemory += val ;

		} catch ( NumberFormatException e ) {

			logger.error( "Failed to parse virtualMemory string: '{}' {}",
					virtualMemory, CSAP.buildCsapStack( e ) ) ;

		}

	}

	@JsonProperty ( OsProcessEnum.RSS_MEMORY )
	public int getRssMemoryInKb ( ) {

		return rssMemory ; // / 1024 ?

	}

	public int getVirtualMemory ( ) {

		return virtualMemory ;

	}

	@JsonProperty ( JmxCommonEnum.TOMCAT_CONNECTIONS )
	public int getNumTomcatConns ( ) {

		return numTomcatConns ;

	}

	public void setNumTomcatConns ( Long numTomcatConns ) {

		this.numTomcatConns = numTomcatConns.intValue( ) ;

	}

	@JsonProperty ( JmxCommonEnum.JVM_THREAD_COUNT )
	public int getJvmThreadCount ( ) {

		return jvmThreadCount ;

	}

	public void setJvmThreadCount ( Long jvmThreadCount ) {

		this.jvmThreadCount = jvmThreadCount.intValue( ) ;

	}

	@JsonProperty ( ServiceAlertsEnum.JAVA_HEARTBEAT )
	public long getJmxHeartbeatMs ( ) {

		return jmxHeartbeatMs ;

	}

	public void setJmxHeartbeatMs ( long jmxHeartbeat ) {

		this.jmxHeartbeatMs = jmxHeartbeat ;

	}

	@JsonProperty ( OsProcessEnum.DISK_READ_KB )
	public int getDiskReadKb ( ) {

		return diskReadKb ;

	}

	public void setDiskReadKb ( int diskReadKb ) {

		this.diskReadKb = diskReadKb ;

	}

	@JsonProperty ( OsProcessEnum.DISK_WRITE_KB )
	public int getDiskWriteKb ( ) {

		return diskWriteKb ;

	}

	public void setDiskWriteKb ( int diskWriteKb ) {

		this.diskWriteKb = diskWriteKb ;

	}

	public void setCpuClean ( ) {

		this.cpuUtil = "CLEAN" ;

	}

	/**
	 * service is fully started
	 * 
	 * @return
	 */

	public boolean isRunning ( ) {

		if ( isInactive( )
				|| cpuUtil.equals( CPU_AUTO_MODE_TO_TRIGGER_HOUR_GLASS )
				|| cpuUtil.equals( "CLEAN" ) ) {

			return false ;

		}

		return true ;

	}

	@JsonIgnore
	public boolean isInactive ( ) {

		return cpuUtil.equals( INACTIVE ) ;

	}

	@JsonIgnore
	public boolean isActive ( ) {

		return ! isInactive( ) ;

	}

	public static final String REMOTE_UTIL = "REMOTE" ;

	@JsonProperty ( PS_CPU )
	public String getCpuUtil ( ) {

		// if ( getCollectHost() != null ) {
		// return REMOTE_UTIL;
		// }

		return cpuUtil ;

	}

	public void setCpuAuto ( ) {

		this.cpuUtil = CPU_AUTO_MODE_TO_TRIGGER_HOUR_GLASS ;

	}

	public void setCpuReset ( ) {

		this.cpuUtil = "-" ;

	}

	public boolean isAutoKillInProgress ( ) {

		return autoKillInProgress ;

	}

	public void setAutoKillInProgress ( boolean autoKillInProgress ) {

		this.autoKillInProgress = autoKillInProgress ;

	}

	public void setCpuUtil ( String collectedCpuUsage ) {

		// allows allow overwrites on admins
		if ( ( ! Application.getInstance( ).isAdminProfile( ) ) &&
				cpuUtil.equals( CPU_AUTO_MODE_TO_TRIGGER_HOUR_GLASS ) ||
				cpuUtil.equals( "CLEAN" ) ) {

			logger.info( "Ignoring cpu: {}, service is in startup mode: {}", collectedCpuUsage, cpuUtil ) ;
			return ;

		}

		this.cpuUtil = collectedCpuUsage ;

	}

	// Hook to add make cpu cumulative
	public void addCpuUtil ( String collectedCpuUsage ) {

		if ( cpuUtil.equals( CPU_AUTO_MODE_TO_TRIGGER_HOUR_GLASS ) || cpuUtil.equals( "CLEAN" ) ) {

			logger.debug( "Ignoring cpu: {}, service is in startup mode: {}", collectedCpuUsage, cpuUtil ) ;
			return ;

		}

		try {

			float currCpu = Float.valueOf( this.cpuUtil ) ;
			float testCpu = Float.valueOf( collectedCpuUsage ) ;
			this.cpuUtil = Float.toString( currCpu + testCpu ) ;
			return ;

		} catch ( NumberFormatException e ) {

			// logger.warn("Ignoring float arithmetic") ;
		}

		this.cpuUtil = collectedCpuUsage.trim( ) ;

	}

	// @JsonSetter ( "diskUtil" )
	public void setDiskUsageInMb ( int diskSpaceUsedInMb ) {

		this.diskUsageInMb = diskSpaceUsedInMb ;

	}

	public void addDiskUseage ( String desc , String diskSpaceUsedInMb ) {

		// input comes from 2 separate sources, formatted differently: du and df.
		int latestCollection = 0 ;

		if ( StringUtils.isNotEmpty( diskSpaceUsedInMb ) ) {

			try {

				// system disk output includes M
				latestCollection = Integer.parseInt( diskSpaceUsedInMb.replaceAll( "[\\D]", "" ) ) ;
				// latestCollection = Integer.parseInt( diskSpaceUsedInMb ) ;
				if ( latestCollection < 0 )
					latestCollection = 0 ;

			} catch ( Exception e ) {

				logger.warn( "{} Failed parsing disk: '{}', {}", desc, diskSpaceUsedInMb, CSAP.buildCsapStack( e ) ) ;

			}

		}

		this.diskUsageInMb = this.diskUsageInMb + latestCollection ;

		logger.debug( "{} Raw: {} Disk updated: {} ", desc, diskSpaceUsedInMb, diskUsageInMb ) ;

	}

	@JsonProperty ( OsProcessEnum.DISK_USED_IN_MB )
	public int getDiskUsageInMb ( ) {

		return diskUsageInMb ;

	}

	@JsonProperty ( OsProcessEnum.TOP_CPU )
	public float getTopCpu ( ) {

		return topCpu ;

	}

	public void setTopCpu ( float topCpu ) {

		this.topCpu = topCpu ;

	}

	/**
	 * @return the socketCount
	 */

	@JsonProperty ( OsProcessEnum.SOCKET_COUNT )
	public int getSocketCount ( ) {

		return socketCount ;

	}

	public String getCurrentProcessPriority ( ) {

		return currentProcessPriority ;

	}

	public void setCurrentProcessPriority ( String currentProcessPriority ) {

		this.currentProcessPriority = currentProcessPriority ;

	}

	/**
	 * @return the fileCount
	 */

	@JsonProperty ( OsProcessEnum.FILE_COUNT )
	public int getFileCount ( ) {

		return fileCount ;

	}

	public void setFileCount ( int fileCount ) {

		this.fileCount = fileCount ;

	}

	// public void addFileCount(int fileCount) {
	// this.fileCount += fileCount;
	// }
	public void setSocketCount ( int socketCount ) {

		this.socketCount = socketCount ;

	}

	@JsonProperty ( OsProcessEnum.THREAD_COUNT )
	public int getThreadCount ( ) {

		return threadCount ;

	}

	public void setThreadCount ( int threadCount ) {

		this.threadCount = threadCount ;

	}

	public void addThreadCount ( String threadCount ) {

		try {

			int val = Integer.parseInt( threadCount ) ;
			this.threadCount += val ;

		} catch ( NumberFormatException e ) {

			logger.error( " Failed to parse threadCount string: '{}' {}",
					threadCount, CSAP.buildCsapStack( e ) ) ;

		}

	}

	public String getRunHeap ( ) {

		return runHeap ;

	}

	public void setRunHeap ( String runHeap ) {

		this.runHeap = runHeap ;

	}

	public void addRunHeap ( String runHeap ) {

		// Handle for processes with multiple heaps specified
		if ( this.runHeap.length( ) != 0 ) {

			this.runHeap += "," ;

		}

		this.runHeap += runHeap ;

	}

	public static final String DEPLOYED_ARTIFACTS = "deployedArtifacts" ;

	@JsonProperty ( DEPLOYED_ARTIFACTS )
	public String getDeployedArtifacts ( ) {

		return deployedArtifacts ;

	}

	public void setDeployedArtifacts ( String deployedArtifacts ) {

		//
		// logger.info( "updated: {}", deployedArtifacts );
		this.deployedArtifacts = deployedArtifacts ;

	}

	public String getContainerName ( ) {

		return containerName ;

	}

	public void setContainerName ( String containerName ) {

		this.containerName = containerName ;

	}

	@Override
	public String toString ( ) {

		return CSAP.buildDescription( "ContainerState",
				"pid", pid,
				"cpuUtil", cpuUtil,
				"topCpu", topCpu,
				"threadCount", threadCount,
				"jvmThreadCount", jvmThreadCount,
				"rssMemory", rssMemory,
				"virtualMemory", virtualMemory,
				"runHeap", runHeap,
				"socketCount", socketCount,
				"deployedArtifacts", deployedArtifacts,
				"containerName", containerName,
				"containerLabel", containerLabel,
				"podNamespace", podNamespace,
				"podIp", podIp,
				"podName", podName,
				"diskReadKb", diskReadKb,
				"diskWriteKb", diskWriteKb,
				"diskUsageInMb", diskUsageInMb,
				"fileCount", fileCount,
				"resourceViolations", resourceViolations,
				"autoKillInProgress", autoKillInProgress,
				"jmxHeartbeatMs", jmxHeartbeatMs,
				"numTomcatConns", numTomcatConns,
				"currentProcessPriority", currentProcessPriority,
				"healthReportCollected", healthReportCollected ) ;

	}

	public List<String> getResourceViolations ( ) {

		return resourceViolations ;

	}

	public void setResourceViolations ( List<String> resourceViolations ) {

		this.resourceViolations = resourceViolations ;

	}

	public String getPodNamespace ( ) {

		return podNamespace ;

	}

	public void setPodNamespace ( String namespace ) {

		this.podNamespace = namespace ;

	}

	public String getPodIp ( ) {

		return podIp ;

	}

	public void setPodIp ( String podIp ) {

		this.podIp = podIp ;

	}

	public String getPodName ( ) {

		return podName ;

	}

	public void setPodName ( String podName ) {

		this.podName = podName ;

	}

	public String getContainerLabel ( ) {

		return containerLabel ;

	}

	public void setContainerLabel ( String containerLabel ) {

		this.containerLabel = containerLabel ;

	}

}
