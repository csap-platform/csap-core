/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.csap.agent.stats ;

import com.fasterxml.jackson.databind.ObjectMapper ;
import com.fasterxml.jackson.databind.node.ObjectNode ;

/**
 *
 * @author someDeveloper
 */
public enum OsSharedEnum {
	cpuCountAvg("cpuCountAvg"), memoryInMbAvg("memoryInMbAvg"), swapInMbAvg("swapInMbAvg"), totActivity("totActivity"),
	numberOfSamples("numberOfSamples"), totalUsrCpu("totalUsrCpu"), totalSysCpu("totalSysCpu"),
	totalMemFree("totalMemFree"), totalBufFree("totalBufFree"), totalIo("totalIo"),
	alertsCount("alertsCount"), totalLoad("totalLoad"), totalFiles("totalFiles"),
	threadsTotal("threadsTotal"), csapThreadsTotal("csapThreadsTotal"), fdTotal("fdTotal"),
	csapFdTotal("csapFdTotal"),

	socketsActive("socketTotal"), socketsCloseWait("socketWaitTotal"), socketsTimeWaitTotal("socketTimeWaitTotal"),

	totalDiskTestTime("totalDiskTestTime"), totalCpuTestTime("totalCpuTestTime"),

	totalNetworkReceived("totalNetworkReceived"), totalNetworkTransmitted("totalNetworkTransmitted"),

	totalIoReads("totalIoReads"), totalIoWrites("totalIoWrites"),

	totalsda("totalsda"), totalsdb("totalsdb"), totalsdc("totalsdc"), totalsdd("totalsdd");

	public String value ;

	public String reportKey ( ) {

		return value ;

	}

	private OsSharedEnum ( String value ) {

		this.value = value ;

	}

	static ObjectMapper jacksonMapper = new ObjectMapper( ) ;

	static public ObjectNode realTimeLabelsForEditor ( ) {

		ObjectNode labels = jacksonMapper.createObjectNode( ) ;

		labels.put( "coresActive", "CPU Cores Busy" ) ;
		labels.put( "usrCpu", "Usr Cpu (mpstat)" ) ;
		labels.put( "sysCpu", "System Cpu (mpstat)" ) ;
		labels.put( "IO", "IO Wait (mpstat)" ) ;
		labels.put( "load", "CPU load" ) ;
		labels.put( "memFree", "Memory Free" ) ;
		labels.put( "bufFree", "Memory Buffer Free" ) ;

		labels.put( "openFiles", "Files - /proc" ) ;
		labels.put( "totalThreads", "Threads - All" ) ;
		labels.put( "csapThreads", "Threads - CSAP Services" ) ;
		labels.put( "totalFileDescriptors", "Files - lsof ALL" ) ;
		labels.put( "csapFileDescriptors", "Files - lsof CSAP" ) ;
		labels.put( "networkConns", "Sockets - Active" ) ;
		labels.put( "networkWait", "Sockets - Close Wait" ) ;
		labels.put( "networkTimeWait", "Sockets - Time Wait" ) ;
		labels.put( "networkReceived", "Network MB Received" ) ;
		labels.put( "networkTransmitted", "Network MB Sent" ) ;

		labels.put( "diskTest", "Disk Test Time" ) ;
		labels.put( "cpuTest", "CPU Test Time" ) ;
		labels.put( "ioReads", "Device Reads MB" ) ;
		labels.put( "ioWrites", "Device Writes MB" ) ;
		labels.put( "sda", "device: sda (%)" ) ;
		labels.put( "sdb", "device: sdb (%)" ) ;

		return labels ;

	}

	static public ObjectNode customReportLabels ( ) {

		ObjectNode labels = jacksonMapper.createObjectNode( ) ;
		labels.put( "coresUsed", "Active Cpu Cores" ) ;
		labels.put( "MeanSeconds", "Log Rotation Time" ) ;
		labels.put( "UnHealthyCount", "Host Alerts" ) ;

		return labels ;

	}

	static public ObjectNode hostReportLabels ( ) {

		//
		// must update "hostTable" in perf-main.html and table.js
		//

		ObjectNode labels = jacksonMapper.createObjectNode( ) ;

		for ( OsSharedEnum os : OsSharedEnum.values( ) ) {

			switch ( os ) {

			case cpuCountAvg:
				labels.put( os.value, "CPU: Cores Available" ) ;
				break ;

			case memoryInMbAvg:
				labels.put( os.value, "Memory: Configured (GB)" ) ;
				break ;
			case swapInMbAvg:
				labels.put( os.value, "Memory: Disk Swap (GB)" ) ;
				break ;
			case totActivity:
				labels.put( os.value, "CSAP: Eng User Activity" ) ;
				break ;
			case numberOfSamples:
				labels.put( os.value, "CSAP: Collection Count" ) ;
				break ;
			case totalUsrCpu:
				labels.put( os.value, "CPU: mpstat user %" ) ;
				break ;
			case totalSysCpu:
				labels.put( os.value, "CPU: mpstat system %" ) ;
				break ;
			case totalMemFree:
				labels.put( os.value, "Memory: Not Used (GB)" ) ;
				break ;
			case totalBufFree:
				labels.put( os.value, "Memory: Buffer Free" ) ;
				break ;
			case totalIo:
				labels.put( os.value, "CPU: mpstat IO wait % idle" ) ;
				break ;
			case alertsCount:
				labels.put( os.value, "CSAP: Host Alerts" ) ;
				break ;
			case totalLoad:
				labels.put( os.value, "CPU: Host Load" ) ;
				break ;
			case totalFiles:
				labels.put( os.value, "Sys: Files - /proc" ) ;
				break ;
			case threadsTotal:
				labels.put( os.value, "Sys: Threads - All" ) ;
				break ;
			case csapThreadsTotal:
				labels.put( os.value, "Sys: Threads - CSAP Services" ) ;
				break ;
			case fdTotal:
				labels.put( os.value, "Sys: Files - lsof ALL" ) ;
				break ;
			case csapFdTotal:
				labels.put( os.value, "Sys: Files - lsof CSAP" ) ;
				break ;

			case totalNetworkReceived:
				labels.put( os.value, "Network: Total MB Received" ) ;
				break ;

			case totalNetworkTransmitted:
				labels.put( os.value, "Network: Total MB Sent" ) ;
				break ;

			case socketsActive:
				labels.put( os.value, "Network: Sockets - open" ) ;
				break ;

			case socketsCloseWait:
				labels.put( os.value, "Network: Sockets - Close Wait" ) ;
				break ;

			case socketsTimeWaitTotal:
				labels.put( os.value, "Network: Sockets - Time Wait" ) ;
				break ;

			case totalDiskTestTime:
				labels.put( os.value, "CSAP: Test Disk (s)" ) ;
				break ;
			case totalCpuTestTime:
				labels.put( os.value, "CSAP: Test CPU (seconds)" ) ;
				break ;
			case totalIoReads:
				labels.put( os.value, "Disk: iostat MB Read" ) ;
				break ;
			case totalIoWrites:
				labels.put( os.value, "Disk: iostat MB Writes" ) ;
				break ;
			case totalsda:
				labels.put( os.value, "Disk: iostat sda util (%)" ) ;
				break ;
			case totalsdb:
				labels.put( os.value, "Disk: iostat sdb util (%)" ) ;
				break ;
			case totalsdc:
				labels.put( os.value, "Disk: iostat sdc util (%)" ) ;
				break ;
			case totalsdd:
				labels.put( os.value, "Disk: iostat sdd util (%)" ) ;
				break ;
			default:
				throw new AssertionError( os.name( ) ) ;

			}

		}

		return labels ;

	}
}

// totalMemFree: "Memory Not Used (GB)",
// fdTotal: "VM Open Files",
// csapFdTotal: "Application Open Files",
// totalUsrCpu: "Application CPU Percent",
// totalSysCpu: "OS Kernel CPU Percent",
// totalIo: "CPU Idle due to IO Wait"
