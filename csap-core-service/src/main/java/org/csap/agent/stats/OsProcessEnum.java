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
public enum OsProcessEnum {
	topCpu(OsProcessEnum.TOP_CPU),
	threadCount(OsProcessEnum.THREAD_COUNT),
	fileCount(OsProcessEnum.FILE_COUNT),
	socketCount(OsProcessEnum.SOCKET_COUNT),
	rssMemory(OsProcessEnum.RSS_MEMORY),
	diskUsedInMb(OsProcessEnum.DISK_USED_IN_MB),
	diskReadKb(OsProcessEnum.DISK_READ_KB),
	diskWriteKb(OsProcessEnum.DISK_WRITE_KB);

	final public static String TOP_CPU = "topCpu" ;
	final public static String DISK_USED_IN_MB = "diskUtil" ;
	final public static String SOCKET_COUNT = "socketCount" ;
	final public static String FILE_COUNT = "fileCount" ;
	final public static String THREAD_COUNT = "threadCount" ;
	final public static String RSS_MEMORY = "rssMemory" ;
	final public static String DISK_READ_KB = "diskReadKb" ;
	final public static String DISK_WRITE_KB = "diskWriteKb" ;

	public String value ;

	public String json ( ) {

		return value ;

	}

	private OsProcessEnum ( String value ) {

		this.value = value ;

	}

	static ObjectMapper jacksonMapper = new ObjectMapper( ) ;

	static public ObjectNode graphLabels ( ) {

		ObjectNode labels = jacksonMapper.createObjectNode( ) ;

		for ( OsProcessEnum os : OsProcessEnum.values( ) ) {

			switch ( os ) {

			case topCpu:
				labels.put( os.value, "Cpu (Top)" ) ;
				break ;
			case threadCount:
				labels.put( os.value, "Threads" ) ;
				break ;
			case fileCount:
				labels.put( os.value, "Open Files" ) ;
				break ;
			case socketCount:
				labels.put( os.value, "Open Sockets" ) ;
				break ;
			case rssMemory:
				labels.put( os.value, "Memory RSS (MB)" ) ;
				break ;
			case diskUsedInMb:
				labels.put( os.value, "Disk Usage (MB)" ) ;
				break ;
			case diskReadKb:
				labels.put( os.value, "Disk Reads (KB/s)" ) ;
				break ;
			case diskWriteKb:
				labels.put( os.value, "Disk Writes (KB/s)" ) ;
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
