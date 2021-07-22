package org.csap.agent.linux ;

import java.io.File ;
import java.io.IOException ;
import java.nio.file.DirectoryStream ;
import java.nio.file.FileSystems ;
import java.nio.file.Files ;
import java.nio.file.Path ;
import java.util.ArrayList ;
import java.util.Arrays ;
import java.util.List ;

import org.csap.agent.model.Application ;
import org.csap.agent.model.ContainerState ;
import org.csap.agent.model.ServiceInstance ;
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
public class ServiceResourceRunnable implements Runnable {

	final Logger logger = LoggerFactory.getLogger( this.getClass( ) ) ;

	public ServiceResourceRunnable ( Application csapApp, OsCommandRunner osCommandRunner ) {

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

			var socketStatTimer = csapApp.metrics( ).startTimer( ) ;
			var socketCollectionForRootNamespace = executeSocketCollection( ) ;
			csapApp.metrics( ).stopTimer( socketStatTimer, "java.OsManager.socketstat.all" ) ;

			var pidStatTimer = csapApp.metrics( ).startTimer( ) ;
			String pidStatResult = executePidStatCollection( ) ;
			csapApp.metrics( ).stopTimer( pidStatTimer, "java.OsManager.pidstat" ) ;

			var allServicesTimer = csapApp.metrics( ).startTimer( ) ;

			logger.debug( "\n\n***** Refreshing open files cache   *******\n\n" ) ;

			var svcList = csapApp.getServicesOnHost( ) ;

			for ( ServiceInstance instance : svcList ) {

				var serviceTimer = csapApp.metrics( ).startTimer( ) ;

				if ( instance.is_files_only_package( )
						|| instance.isRemoteCollection( )
						|| ! instance.getDefaultContainer( ).isRunning( ) ) {

					// Set all to 0
					instance.getDefaultContainer( ).setFileCount( 0 ) ;
					instance.getDefaultContainer( ).setSocketCount( 0 ) ;
					instance.getDefaultContainer( ).setDiskReadKb( 0 ) ;
					instance.getDefaultContainer( ).setDiskWriteKb( 0 ) ;
					continue ;

				}

				getOpenFilesForInstance( instance ) ;
				updateInstanceSockets( socketCollectionForRootNamespace, instance ) ;
				updateInstanceFileIo( pidStatResult, instance ) ;

				csapApp.metrics( ).stopTimer( serviceTimer, "collect-os.open-files-" + instance.getName( ) ) ;

				try {

					// logger.info("Sleeping 5 seconds between each call to
					// allow system to queisce a bit");
					Thread.sleep( 500 ) ;

				} catch ( InterruptedException e ) {

					// TODO Auto-generated catch block
					e.printStackTrace( ) ;

				}

			}

			csapApp.metrics( ).stopTimer( allServicesTimer, "csap.collect-os.open-files" ) ;

		} catch ( Exception e ) {

			logger.error( "Exception in collection ", e ) ;

		}

	}

	private boolean isPidStatAvailable = ( new File( "/usr/bin/pidstat" ) ).exists( ) ;

	private String executePidStatCollection ( ) {

		String pidstatResults = "" ;

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

	public void testSocketParsing ( ServiceInstance service , boolean useRh6 ) {

		if ( useRh6 ) {

			SOCKET_STAT_STUB_FILE = SOCKET_STAT_STUB_FILE_RH6 ;

		} else {

			SOCKET_STAT_STUB_FILE = SOCKET_STAT_STUB_FILE_RH7 ;

		}

		updateInstanceSockets( executeSocketCollection( ), service ) ;

	}

	public void testFullCollection ( ) {

		run( ) ;

	}

	private String[] executeSocketCollection ( ) {

		String socketStatResult = "Failed to run" ;

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

		return socketStatResult.split( LINE_SEPARATOR ) ;

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

					var timer = csapApp.metrics( ).startTimer( ) ;

					var containerSocketCount = 0 ;

					try {

						var containerSocketOutput = osCommandRunner.runUsingRootUser( "dockerSocketStat",
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

					csapApp.metrics( ).stopTimer( timer, "java.OsManager.socketstat.docker" ) ;
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

						if ( rootNamespaceCollectionLines[lineNumber].contains( "pid=" + pid + "," ) ) {

							socketCount++ ;
							rootNamespaceCollectionLines[lineNumber] = "" ;

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

	private static String LINE_SEPARATOR = "\n" ;

	private void updateInstanceFileIo ( String pidStatResult , ServiceInstance instance ) {

		String[] pidstatLines = pidStatResult.split( LINE_SEPARATOR ) ;

		for ( ContainerState container : instance.getContainerStatusList( ) ) {

			int logsOutput = 10 ;
			float diskReadKb = 0 ;
			float diskWriteKb = 0 ;

			for ( String pid : container.getPid( ) ) {

				for ( int i = 0; i < pidstatLines.length; i++ ) {

					String curline = pidstatLines[i].trim( ) ;
					String[] cols = curline.split( " " ) ;

					if ( logger.isDebugEnabled( ) && logsOutput++ < 20 ) {

						logger.debug( "cols: " + Arrays.asList( cols ) ) ;

					}

					try {

						if ( cols.length == 6 ) {
							// rh 5 line: Time PID kB_rd/s kB_wr/s kB_ccwr/s Command

							String pidParsed = cols[1].trim( ) ;

							if ( logger.isDebugEnabled( ) && logsOutput++ < 20 ) {

								logger.debug(
										instance.getName( ) + " pidParsed: " + pidParsed + " search pid: " + pid ) ;

							}

							if ( ! pidParsed.equals( pid ) ) {

								continue ;

							}

							// rh 6 line: Time UID PID kB_rd/s kB_wr/s kB_ccwr/s
							// Command
							float kbRead = Float.parseFloat( cols[2] ) ;
							diskReadKb += kbRead ;

							float kbWrite = Float.parseFloat( cols[3] ) ;
							diskWriteKb += kbWrite ;

							double cancelledWrites = Double.parseDouble( cols[4] ) ;

						} else if ( cols.length == 7 ) {
							// rh 6 line: Time UID PID kB_rd/s kB_wr/s kB_ccwr/s
							// Command

							String pidParsed = cols[2].trim( ) ;

							if ( logger.isDebugEnabled( ) && logsOutput++ < 20 ) {

								logger.debug(
										instance.getName( ) + " pidParsed: " + pidParsed + " search pid: " + pid ) ;

							}

							if ( ! pidParsed.equals( pid ) ) {

								continue ;

							}

							float kbRead = Float.parseFloat( cols[3] ) ;
							diskReadKb += kbRead ;

							float kbWrite = Float.parseFloat( cols[4] ) ;
							diskWriteKb += kbWrite ;

							double cancelledWrites = Double.parseDouble( cols[5] ) ;

						}

					} catch ( NumberFormatException e ) {

						pidstatLines[i] = "" ; // wipe out unparsable lines
						// TODO Auto-generated catch block
						// e.printStackTrace();

						if ( logger.isDebugEnabled( ) ) {

							logger.debug( "pid is not an int, skipping" + pid ) ;

						}

						continue ;

					}

				}

			}

			container.setDiskReadKb( Math.round( diskReadKb ) ) ;
			container.setDiskWriteKb( Math.round( diskWriteKb ) ) ;

		}

		return ;

	}

	private void getOpenFilesForInstance ( ServiceInstance instance ) {

		int fileCount = 0 ;

		boolean isCsapProcess = ( instance.getUser( ) == null ) ;

		var timer = csapApp.metrics( ).startTimer( ) ;

		if ( isCsapProcess && ! instance.is_docker_server( ) ) {

			fileCount = getFilesUsingJavaNio( instance, fileCount ) ;
			instance.getDefaultContainer( ).setFileCount( fileCount ) ;

		} else {

			updateOpenFilesForNonCsapProcesses( instance ) ;

		}

		csapApp.metrics( ).stopTimer( timer, "java.OsManager.getOpenFiles." + instance.getName( ) ) ;

		if ( logger.isDebugEnabled( ) ) {

			logger.debug( instance.getName( ) + " javaFileCount : " + fileCount ) ;

		}

	}

	/**
	 * 
	 * services run using same user as agent can leverage java nios for getting file
	 * counts
	 * 
	 * @param instance
	 * @param fileCount
	 * @return
	 */
	private int getFilesUsingJavaNio ( ServiceInstance instance , int fileCount ) {

		if ( Application.isRunningOnDesktop( ) ) {

			return 123 ;

		}

		for ( String pid : instance.getDefaultContainer( ).getPid( ) ) {

			try {

				Integer.parseInt( pid ) ;

			} catch ( NumberFormatException e ) {

				// TODO Auto-generated catch block
				// e.printStackTrace();
				if ( logger.isDebugEnabled( ) ) {

					logger.debug( "pid is not an int, skipping" + pid ) ;

				}

				continue ;

			}

			// more defensive - nio handles large directorys
			try ( DirectoryStream<Path> ds = Files
					.newDirectoryStream( FileSystems.getDefault( ).getPath( "/proc/" + pid + "/fd" ) ) ) {

				for ( Path p : ds ) {

					if ( fileCount++ > 9999 ) {

						fileCount = -99 ;
						break ;

					}

				}

			} catch ( Exception e ) {

				fileCount = 0 ;

				if ( logger.isDebugEnabled( ) ) {

					logger.debug( "Failed getting count for: " + instance.getName( ) + " Due to:"
							+ e.getMessage( ) ) ;

				}

			}

		}

		return fileCount ;

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

		for ( ContainerState container : instance.getContainerStatusList( ) ) {

			int fileCount = 0 ;

			if ( ! Application.getInstance( ).isRunningAsRoot( ) ) {

				if ( numOpenWarnings++ < 10 ) {

					logger.warn( "Root access is not available to determine file counts" ) ;

				}

				container.setFileCount( -1 ) ;
				continue ;

			}

			List<String> lines = new ArrayList<String>( ) ;
			lines.add( "#!/bin/bash" ) ;

			StringBuilder lsCommandString = new StringBuilder( ) ;
			lsCommandString.append( "ls " ) ;

			container.getPid( ).forEach( pid -> {

				lsCommandString.append( " /proc/" ) ;
				lsCommandString.append( pid ) ;
				lsCommandString.append( "/fd " ) ;

			} ) ;
			// lsCommandString.append( " | grep -v /proc/ | wc -w \n" );
			lsCommandString.append( " | grep -v /proc/ | wc -w \n" ) ;
			lines.add( lsCommandString.toString( ) ) ;
			//
			// String[] lines = {
			// "#!/bin/bash",
			// lsCommandString.toString()
			// };

			try {

				String commandResult = osCommandRunner.runUsingRootUser( "open-files", lines ) ;

				logger.debug( "{} commandScript: \n {} \n\n commandResult:\n {}", instance.getName( ), lsCommandString,
						commandResult ) ;

				String[] lsoflines = commandResult.split( System
						.getProperty( "line.separator" ) ) ;

				for ( int i = 0; i < lsoflines.length; i++ ) {

					if ( lsoflines[i].trim( ).contains( " " ) ) {

						continue ;

					}

					try {

						fileCount += Integer.parseInt( lsoflines[i].trim( ) ) ;

					} catch ( Exception w ) {

					}

				}

			} catch ( Exception e ) {

				logger.warn( "Failed running commandScript: \n {}", lsCommandString, e ) ;
				fileCount = -2 ;

			}

			container.setFileCount( fileCount ) ;

		}

	}
}
