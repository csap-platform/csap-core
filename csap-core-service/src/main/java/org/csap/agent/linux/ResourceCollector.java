package org.csap.agent.linux ;

import java.io.File ;
import java.io.IOException ;
import java.nio.file.FileSystems ;
import java.nio.file.Files ;
import java.nio.file.Path ;
import java.util.ArrayList ;
import java.util.Arrays ;
import java.util.List ;

import org.apache.commons.lang3.StringUtils ;
import org.csap.agent.CsapCore ;
import org.csap.agent.model.Application ;
import org.csap.agent.model.ContainerState ;
import org.csap.agent.model.ServiceInstance ;
import org.csap.agent.services.OsManager ;
import org.csap.helpers.CSAP ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;

/**
 *
 * Service Instances will be updated with socket and file information
 *
 * @author someDeveloper
 *
 */
public class ResourceCollector implements Runnable {

	final Logger logger = LoggerFactory.getLogger( this.getClass( ) ) ;

	public ResourceCollector ( Application csapApp, OsCommandRunner osCommandRunner ) {

		this.osCommandRunner = osCommandRunner ;
		this.csapApp = csapApp ;

	}

	private Application csapApp ;
	private OsCommandRunner osCommandRunner ;

	public void shutDown ( ) {

		if ( osCommandRunner == null ) {

			return ;

		}

		try {

			osCommandRunner.shutDown( ) ;
			logger.info( "osCommandRunner shutdown" ) ;

		} catch ( Exception e ) {

			logger.error( "Failed shutting down", e ) ;

		}

	}

	public void run ( ) {

		try {

			//
			// Collect sockets
			//
			var socketCollectionForRootNamespace = executeSocketCollection( ) ;

			//
			// collect service IO : pidstat
			//
			var pidStatLines = executePidStatCollection( ).split( OsManager.LINE_SEPARATOR ) ;

			//
			// Process above reports, and collect File counts
			//
			var allServicesTimer = csapApp.metrics( ).startTimer( ) ;

			logger.debug( "\n\n***** Refreshing open files cache   *******\n\n" ) ;

			var hostServices = csapApp.getServicesOnHost( ) ;

			for ( var serviceInstance : hostServices ) {

				var serviceTimer = csapApp.metrics( ).startTimer( ) ;

				if ( serviceInstance.is_files_only_package( )
						|| serviceInstance.isRemoteCollection( )
						|| ! serviceInstance.getDefaultContainer( ).isRunning( ) ) {

					// Set all to 0
					serviceInstance.getDefaultContainer( ).setFileCount( 0 ) ;
					serviceInstance.getDefaultContainer( ).setSocketCount( 0 ) ;
					serviceInstance.getDefaultContainer( ).setDiskReadKb( 0 ) ;
					serviceInstance.getDefaultContainer( ).setDiskWriteKb( 0 ) ;
					continue ;

				}

				getOpenFilesForInstance( serviceInstance ) ;
				updateInstanceSockets( socketCollectionForRootNamespace, serviceInstance ) ;
				updateInstanceFileIo( pidStatLines, serviceInstance ) ;

				csapApp.metrics( ).stopTimer( serviceTimer, OsManager.COLLECT_OS + "resources-" + serviceInstance
						.getName( ) ) ;

			}

			csapApp.metrics( ).stopTimer( allServicesTimer, "csap." + OsManager.COLLECT_OS + "resources-all" ) ;

		} catch ( Exception e ) {

			logger.error( "Exception in collection ", e ) ;

		}

	}

	private boolean isPidStatAvailable = ( new File( "/usr/bin/pidstat" ) ).exists( ) ;

	private String executePidStatCollection ( ) {

		var pidstatResults = "" ;

		logger.debug( "***X Starting" ) ;

		if ( isPidStatAvailable ) {

			pidstatResults = "Failed to run" ;

			try {

				pidstatResults = osCommandRunner
						.runUsingRootUser(
								"service-disk-io",
								csapApp.getOsManager( ).getOsCommands( ).getServiceDiskIo( ) ) ;

				logger.debug( "output from: {}  , \n{}",
						csapApp.getOsManager( ).getOsCommands( ).getServiceDiskIo( ),
						pidstatResults ) ;

			} catch ( IOException e ) {

				logger.info( "Failed to collect pidstat info: {} , \n reason: {}",
						csapApp.getOsManager( ).getOsCommands( ).getServiceDiskIo( ),
						CSAP.buildCsapStack( e ) ) ;

			}

		}

		pidstatResults = csapApp.check_for_stub( pidstatResults, PIDSTAT_STUB_FILE_RH7 ) ;

		logger.debug( "***X ENDINF" ) ;
		return pidstatResults ;

	}

	final static String PIDSTAT_STUB_FILE = "linux/ps-pidstat-results.txt" ;
	final static String PIDSTAT_STUB_FILE_RH7 = "linux/ps-pidstat-results-rh7.txt" ;
	final static String SOCKET_STAT_STUB_FILE_RH6 = "/linux/socketStat.txt" ;
	final static String SOCKET_STAT_STUB_FILE_RH7 = "/linux/socketStat_rh7.txt" ;
	static String SOCKET_STAT_STUB_FILE = SOCKET_STAT_STUB_FILE_RH7 ;

	public void testSocketParsing ( ServiceInstance service ) {

//		if ( useRh6 ) {
//
//			SOCKET_STAT_STUB_FILE = SOCKET_STAT_STUB_FILE_RH6 ;
//
//		} else {

		SOCKET_STAT_STUB_FILE = SOCKET_STAT_STUB_FILE_RH7 ;

//		}

		updateInstanceSockets( executeSocketCollection( ), service ) ;

	}

	public void testFullCollection ( ) {

		run( ) ;

	}

	private String[] executeSocketCollection ( ) {

		var socketStatResult = "Failed to run" ;

		try {

			socketStatResult = osCommandRunner
					.runUsingRootUser( "host-socket-collection",
							csapApp.getOsManager( ).getOsCommands( ).getServiceSockets( ) ) ;

			logger.debug( "output from: {}  , \n{}",
					csapApp.getOsManager( ).getOsCommands( ).getServiceSockets( ),
					socketStatResult ) ;

		} catch ( Exception e ) {

			logger.info( "Failed to collect socket info: reason: {}",
					CSAP.buildCsapStack( e ) ) ;

		}

		socketStatResult = csapApp.check_for_stub( socketStatResult, "linux/ps-socket-stat.txt" ) ;

		// if ( socketStatResult.contains( "pid=" ) ) {
		// isPidEqualsFormat = true ;
		// }

		return socketStatResult.split( OsManager.LINE_SEPARATOR ) ;

	}

	static boolean isPidEqualsFormat = true ; // redhat 7 and others

	private void updateInstanceSockets ( String[] rootNamespaceCollectionLines , ServiceInstance serviceInstance ) {

		var socketCount = 0 ;
		var source = "global" ;

		var pidsInRootNamespace = serviceInstance.getDefaultContainer( ).getPid( ) ;

		if ( ( serviceInstance.is_docker_server( ) || serviceInstance.isRunUsingDocker( ) )
				&& ( serviceInstance.getDefaultContainer( ).getPid( ).size( ) > 0 )
				&& ( serviceInstance.getDefaultContainer( ).getPid( ).get( 0 ) != ContainerState.NO_PIDS ) ) {

			pidsInRootNamespace = new ArrayList<>( ) ;

			for ( ContainerState container : serviceInstance.getContainerStatusList( ) ) {

				if ( ! serviceInstance.isDockerNamespaceSocketCollection( ) ) {

					pidsInRootNamespace.addAll( container.getPid( ) ) ;

				} else {

					source = "container namespace" ;
					var containerPid = container.getPid( ).get( 0 ) ;
					List<String> socketCollectCommands = //
							csapApp.getOsManager( )
									.getOsCommands( )
									.getServiceSocketsDocker( containerPid ) ;

					var containerSocketCount = 0 ;

					try {

						var containerSocketOutput = osCommandRunner.runUsingRootUser( "root-socket-stat",
								socketCollectCommands ) ;
						logger.debug( "{} commands: {}  , result: '{}'",
								serviceInstance.getName( ),
								socketCollectCommands,
								containerSocketOutput ) ;
						containerSocketOutput = csapApp.check_for_stub( containerSocketOutput,
								"linux/docker-socket-stat.txt" ) ;
						containerSocketCount = Integer.parseInt( containerSocketOutput.trim( ) ) ;

					} catch ( Exception e ) {

						logger.info( "Failed to run docker nsenter: {} , \n reason: {}", socketCollectCommands,
								CSAP.buildCsapStack( e ) ) ;

					}

					container.setSocketCount( containerSocketCount ) ;

				}

			}

			// if ( foundContainerMatch ) {
			// return ;
			// }
			// logger.debug( "{} Docker service with no sockets, checking OS host
			// collection", serviceInstance.toSummaryString() ) ;
			// return ;

		}

		if ( ! pidsInRootNamespace.isEmpty( ) ) {

			for ( var lineNumber = 0; lineNumber < rootNamespaceCollectionLines.length; lineNumber++ ) {

				for ( var pid : pidsInRootNamespace ) {

					try {

						Integer.parseInt( pid ) ;

						if ( rootNamespaceCollectionLines[ lineNumber ].contains( "pid=" + pid + "," ) ) {

							socketCount++ ;
							rootNamespaceCollectionLines[ lineNumber ] = "" ;

						}

					} catch ( NumberFormatException e ) {

						if ( logger.isDebugEnabled( ) ) {

							logger.debug( "pid is not an int, skipping" + pid ) ;

						}

					}

				}

			}

			serviceInstance.getDefaultContainer( ).setSocketCount( socketCount ) ;

		}

		logger.debug( "{} sockets: {},  source: {}, pids: {}",
				serviceInstance.getName( ), socketCount, source, pidsInRootNamespace ) ;

		return ;

	}

	private void updateInstanceFileIo ( String[] pidStatLines , ServiceInstance instance ) {

//		if ( instance.getName( ).equals( CsapCore.AGENT_NAME ) ) {
//			logger.info( "pidStatLines: {}", Arrays.asList( pidStatLines ) ) ;
//		}

		instance.getContainerStatusList( ).stream( )
				.forEach( containerStatus -> {

					int logsOutput = 0 ;
					float diskReadKb = 0 ;
					float diskWriteKb = 0 ;

					for ( var pid : containerStatus.getPid( ) ) {

						var psIndex = 0 ;

						for ( var pidStatLine : pidStatLines ) {

							var currentIndex = psIndex++ ;

							String[] cols = pidStatLine.trim( ).split( " " ) ;

//							if ( instance.getName( ).equals( CsapCore.AGENT_NAME ) ) {
//								logger.info( "pid: {},  columns: {}", pid, Arrays.asList( cols ) ) ;
//							}

							if ( cols.length != 7
									|| ! StringUtils.isNumeric( cols[ 2 ] ) ) {

								pidStatLines[ currentIndex ] = "" ;

								continue ;

							}

							var pidParsed = cols[ 2 ].trim( ) ;

							if ( ! pidParsed.equals( pid ) ) {

								continue ;

							}

							try {

								var kbRead = Float.parseFloat( cols[ 3 ] ) ;
								diskReadKb += kbRead ;

								var kbWrite = Float.parseFloat( cols[ 4 ] ) ;
								diskWriteKb += kbWrite ;

								// var cancelledWrites = Double.parseDouble( cols[5] ) ;

							} catch ( Exception e ) {

								// failed to parse wipe it out

								if ( logsOutput++ == 0
										&& instance.getName( ).equals( CsapCore.AGENT_NAME ) ) {

									logger.warn( "Failed parsing: {}", pidStatLine ) ;

								}

								pidStatLines[ currentIndex ] = "" ;

							}

						}

					}

					containerStatus.setDiskReadKb( Math.round( diskReadKb ) ) ;
					containerStatus.setDiskWriteKb( Math.round( diskWriteKb ) ) ;

				} ) ;

		return ;

	}

	private void getOpenFilesForInstance ( ServiceInstance instance ) {

		boolean isCsapProcess = ( instance.getUser( ) == null ) ;

		var timer = csapApp.metrics( ).startTimer( ) ;

		if ( isCsapProcess && ! instance.is_docker_server( ) ) {

			instance.getDefaultContainer( ).setFileCount( getFilesUsingJavaNio( instance ) ) ;

		} else {

			updateOpenFilesForNonCsapProcesses( instance ) ;

		}

		csapApp.metrics( ).stopTimer( timer, OsManager.COLLECT_OS + "java-nio-files." + instance.getName( ) ) ;

	}

	/**
	 * 
	 * services run using same user as agent can leverage java nios for getting file
	 * counts - avoiding opening new folders
	 * 
	 * @param instance
	 * @param fileCount
	 * @return
	 */
	private int getFilesUsingJavaNio ( ServiceInstance instance ) {

		if ( Application.isRunningOnDesktop( ) ) {

			return 123 ;

		}

		var totalFiles = instance.getDefaultContainer( ).getPid( ).stream( )

				.filter( StringUtils::isNumeric )

				.mapToInt( pid -> {

					var lineTotal = 0 ;

					// more defensive - nio handles large directorys
					try ( var directoryStream = Files.newDirectoryStream(
							FileSystems.getDefault( ).getPath( "/proc/" + pid + "/fd" ) ) ) {

						for ( Path p : directoryStream ) {

							if ( lineTotal++ > 9999 ) {

								lineTotal = -99 ;
								break ;

							}

						}

					} catch ( Exception e ) {

						lineTotal = 0 ;

						if ( logger.isDebugEnabled( ) ) {

							logger.debug( "Failed getting count for: {} {}", instance.getName( ), CSAP.buildCsapStack(
									e ) ) ;

						}

					}

					return lineTotal ;

				} )

				.sum( ) ;

		return totalFiles ;

	}

	/**
	 * 
	 * In order to access files run under another user then agent, must run a root
	 * command
	 * 
	 * @param instance
	 * @return
	 */
	int numOpenWarnings = 0 ;

	private void updateOpenFilesForNonCsapProcesses ( ServiceInstance instance ) {

		logger.debug( "{} running open files script", instance.getName( ) ) ;

		if ( ! Application.getInstance( ).isRunningAsRoot( ) ) {

			if ( numOpenWarnings++ < 10 ) {

				logger.warn( "Root access is not available to determine file counts" ) ;

			}

			instance.getContainerStatusList( ).stream( ).forEach( containerState -> {

				containerState.setFileCount( -1 ) ;

			} ) ;

			return ;

		}

		//
		// double counts of namespace - ok because namespace filters will potentially
		// have more pids
		//

		instance.getContainerStatusList( ).stream( ).forEach( containerState -> {

			var fileCount = 0 ;
			var openFilesCollectScript = new ArrayList<String>( ) ;
			openFilesCollectScript.add( "#!/bin/bash" ) ;

			StringBuilder lsCommandString = new StringBuilder( ) ;
			lsCommandString.append( "ls " ) ;

			containerState.getPid( ).forEach( pid -> {

				lsCommandString.append( " /proc/" ) ;
				lsCommandString.append( pid ) ;
				lsCommandString.append( "/fd " ) ;

			} ) ;

			lsCommandString.append( " | grep -v /proc/ | wc -w \n" ) ;
			openFilesCollectScript.add( lsCommandString.toString( ) ) ;

			try {

				var commandResult = osCommandRunner.runUsingRootUser( "open-files-as-root", openFilesCollectScript ) ;

				commandResult = csapApp.check_for_stub( commandResult, "linux/open-files-as-root.txt" ) ;

				logger.debug( "{} commandScript: \n {} \n\n commandResult:\n {}", instance.getName( ), lsCommandString,
						commandResult ) ;

				fileCount = Arrays.stream( commandResult.split( OsManager.LINE_SEPARATOR ) )
						.map( String::trim )
						.filter( StringUtils::isNotEmpty )
						.filter( line -> ! line.contains( " " ) )
						.mapToInt( line -> {

							var lineTotal = 0 ;

							try {

								lineTotal = Integer.parseInt( line ) ;

							} catch ( Exception w ) {

							}

							return lineTotal ;

						} )

						.sum( ) ;

			} catch ( Exception e ) {

				logger.warn( "Failed running commandScript:  {} {}", openFilesCollectScript, CSAP.buildCsapStack(
						e ) ) ;
				fileCount = -2 ;

			}

			containerState.setFileCount( fileCount ) ;

		} ) ;

	}
}
