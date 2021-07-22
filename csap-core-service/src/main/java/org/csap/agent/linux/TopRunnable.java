package org.csap.agent.linux ;

import java.io.BufferedReader ;
import java.io.File ;
import java.io.IOException ;
import java.io.InputStreamReader ;
import java.io.StringReader ;
import java.text.DecimalFormat ;
import java.util.Arrays ;
import java.util.List ;
import java.util.concurrent.ConcurrentHashMap ;
import java.util.concurrent.TimeUnit ;
import java.util.regex.Matcher ;

import org.csap.agent.model.Application ;
import org.csap.helpers.CSAP ;
import org.csap.helpers.CsapApplication ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;

public class TopRunnable implements Runnable {

	final Logger logger = LoggerFactory.getLogger( TopRunnable.class ) ;

	// every 5 seconds prints non 0 top
	// private String command = "top -b -d 5 | awk '($9>0){print $0}' 1>&2";
	// big hook: linux buffers all non terminal output. script allows buffer
	// flushing.
	private int collectionIntervalSeconds = 5 ;

	public int getCollectionIntervalSeconds ( ) {

		return collectionIntervalSeconds ;

	}

	// private String command = "script -c \"top -b -d " + TOP_SAMPLE_SECONDS
	// +" \" /dev/null";
	private List<String> command ;

	// private String
	// command="top -b -d 5 | awk '(($1 ~ /[0-9].*/)&&($9>0)){print $0}'" ;
	public TopRunnable ( int collectionIntervalSeconds, List<String> commandToRun ) {

		File toprc = new File( System.getenv( "HOME" ) + "/.toprc" ) ;

		if ( toprc.exists( ) ) {

			logger.error(
					"Found .toprc file -  CsAgent parses top output for stats collection. File will be renamed" ) ;

			File newName = new File( System.getenv( "HOME" ) + "/OLD.toprc" ) ;

			newName.delete( ) ;

			if ( ! toprc.renameTo( newName ) ) {

				logger.error( "Failed to rename. This will break service metrics." ) ;

			}

		}

		this.collectionIntervalSeconds = collectionIntervalSeconds ;
		command = commandToRun ;

		commandThread = new Thread( this ) ;
		commandThread.setDaemon( true ) ;
		commandThread.start( ) ;
		commandThread.setName( "CsapLinuxTopCommand_" + getCollectionIntervalSeconds( ) + "s" ) ;

		// commandThread.setPriority(Thread.MAX_PRIORITY) ;
		// logger.info("Spawning Top thread: with priority: "
		// + commandThread.getPriority());
	}

	Thread commandThread = null ;

	private volatile boolean isKeepRunning = true ;

	public void shutdown ( ) {

		logger.warn( " Shutting down" ) ;

		isKeepRunning = false ;
		lastAccessTime = 0 ;
		commandThread.interrupt( ) ;

	}

	@Override
	public void run ( ) {

		logger.info( "Starting thread: '{}', using: {}",
				Thread.currentThread( ).getName( ),
				command ) ;

		executeString( command ) ;

		logger.warn( "Exiting Thread" ) ;

	}

	private ConcurrentHashMap<String, String> pidToCpu = new ConcurrentHashMap<String, String>( ) ;

	private StringBuilder lastTopResponse = new StringBuilder( ) ;

	private int numRuns = 0 ;

	int topHeaderCount = 0 ;

	private String executeString ( List<String> commandList ) {

		ProcessBuilder processBuilder = new ProcessBuilder( commandList ) ;
		processBuilder.redirectErrorStream( true ) ;

		Process linuxProcess = null ;
		String commandOutputLine = null ;
		String result = "Result from executing: " ;
		BufferedReader stdOutAndErrReader = null ; // stdout and error combined
		InputStreamReader isReader = null ;

		try {

			while ( isKeepRunning ) {

				if ( numRuns > 100 ) {

					numRuns = 0 ;

				}

				long currTime = System.currentTimeMillis( ) ;

				// Hook to run continuously
				lastAccessTime = currTime ;

				// Top adds some overhead - do not run unless necessary
				if ( currTime - lastAccessTime < 30000 ) {
					// very important to pick up paths from parent!!

					logger.debug( "Resuming Command: {}", command ) ;

					if ( Application.isRunningOnDesktop( ) ) {

						var testData = Application.getInstance( ).check_for_stub( "", "linux/ps-top-results.txt" ) ;
						stdOutAndErrReader = new BufferedReader( new StringReader( testData ) ) ;
						logger.debug( CsapApplication.testHeader( testData ) ) ;

					} else {

						linuxProcess = processBuilder.start( ) ;
						logger.warn( " ****** Starting: {}", command ) ;
						isReader = new InputStreamReader( linuxProcess.getInputStream( ) ) ;
						stdOutAndErrReader = new BufferedReader( isReader ) ;

					}

					topHeaderCount = 0 ;

					while ( isKeepRunning
							&& ( commandOutputLine = stdOutAndErrReader.readLine( ) ) != null ) {

						processTopLine( commandOutputLine, linuxProcess, stdOutAndErrReader ) ;
						currTime = System.currentTimeMillis( ) ;

					}

					if ( ! Application.isRunningOnDesktop( ) ) {

						logger.warn( " Command Exited: {}", command ) ;

					}

					if ( linuxProcess != null ) {

						linuxProcess.destroy( ) ;

					}

				}

				try {

					// on windows - exit is done of the above
					if ( isKeepRunning ) {

						TimeUnit.SECONDS.sleep( collectionIntervalSeconds ) ;

					}

					// logger.info("Done Sleeping");
				} catch ( InterruptedException e1 ) {

					logger.error( "Got interuption while sleeping on top output" ) ;

				}

			}

			// logger.debug("Done " + params.get(0));
		} catch ( Exception e ) {

			logger.error(
					"critical failure: linux top process exited: {}",
					CSAP.buildCsapStack( e ) ) ;

		} finally {

			logger.warn( "Exiting monitors - restart is pending" ) ;

			if ( isReader != null ) {

				try {

					isReader.close( ) ;

				} catch ( IOException e ) {

					logger.error( "failed closing reader", e ) ;

				}

			}

			if ( linuxProcess != null ) {

				linuxProcess.destroy( ) ;

			}

			if ( stdOutAndErrReader != null ) {

				try {

					stdOutAndErrReader.close( ) ;

				} catch ( IOException e ) {

					logger.error( "failed closing reader", e ) ;

				}

			}

		}

		return result ;

	}

	public void processTopLine ( String commandOutputLine , Process linuxProcess , BufferedReader stdOutAndErrReader )
		throws IOException {

		String[] topOutputArray = commandOutputLine.trim( ).split( " +" ) ;

		if ( commandOutputLine.startsWith( "top" ) ) {

			if ( topHeaderCount++ >= 100 ) {

				// Clears are done here to get rid of pids for
				// processes
				// that have been killed. Linux has a lot of
				// transient processes, so clearing resulsts
				// is needed
				logger.info( "Clearing pid Map, {} entries", pidToCpu.size( ) ) ;
				pidToCpu.clear( ) ;
				topHeaderCount = 0 ;

			}

		}

		// logger.info(s);
		if ( logger.isDebugEnabled( ) ) {

			if ( commandOutputLine.startsWith( "top" ) ) {

				logger.info( "\n" + lastTopResponse.toString( ) ) ;
				lastTopResponse = new StringBuilder( ) ;

			}

			lastTopResponse.append( "\n" ) ;
			lastTopResponse.append( commandOutputLine ) ;

		}

		logger.debug( "***************** Parsing commandOutputLine: {}", commandOutputLine ) ;
		// Clears are triggered by client refresh states.
		// if (topOutputArray[0].contains("top")) {
		// if (logger.isDebugEnabled())
		// logger.debug("***************** Clearing pidMap " +
		// s);
		// // pidToCpu.clear();
		// continue;
		// }

		// skip past headers
		if ( ! isInteger( topOutputArray[0].trim( ) ) ) {

			checkLineForVmCpu( topOutputArray ) ;
			logger.debug( "Non Integer: {} ", topOutputArray[0] ) ;

			return ;

		}

		if ( topOutputArray.length < 10 ) {

			logger.debug( "Unexpected output from: {}, \n commandOutputLine: {} ", command,
					commandOutputLine ) ;
			return ;

		}

		logger.debug( "Pushing: {} , cpu: {}", topOutputArray[0].trim( ), topOutputArray[8].trim( ) ) ;
		pidToCpu.put( topOutputArray[0].trim( ), topOutputArray[8].trim( ) ) ;

		// lets run continuosly
		// if (currTime - lastAccessTime > 30000) {
		// break;
		// }

		while ( isKeepRunning &&
				! stdOutAndErrReader.ready( ) &&
				( linuxProcess == null || linuxProcess.isAlive( ) ) ) {

			try {

				// Only do work if needed
				logger.debug( "Sleeping for 1 seconds until next top output is available" ) ;
				Thread.sleep( 1000 ) ;

			} catch ( InterruptedException e1 ) {

				logger.error( "Got interuption while sleeping on top output" ) ;

			}

		}

		logger.debug( "outputAvailable" ) ;

	}

	public static String VM_TOTAL = "vmTotal" ;

	private void checkLineForVmCpu ( String[] topOutputArray ) {

		// Hook for System/Usr cpu
		if ( topOutputArray.length >= 1 && topOutputArray[0].contains( Matcher.quoteReplacement( "Cpu(s):" ) ) ) {

			float result = -1 ;

			// %Cpu(s): 20.7 us, 10.7 sy, 0.0 ni, 98.6 id, 0.0 wa, 0.0 hi, 0.0 si, 0.0 st
			try {

				float usr = Float.parseFloat( topOutputArray[1] ) ;
				float sys = Float.parseFloat( topOutputArray[3] ) ;
				result = usr + sys ;

			} catch ( Exception e ) {

				logger.error( "Failed to parse top output: '{}', {}", Arrays.asList( topOutputArray ), CSAP
						.buildCsapStack( e ) ) ;

			}

			DecimalFormat df = new DecimalFormat( ".#" ) ;
			pidToCpu.put( VM_TOTAL, df.format( result ) ) ;

		}

	}

	private long lastAccessTime = 0 ;

	public float getCpuForPid ( List<String> pidList ) {

		float result = 0.0f ;

		for ( String pid : pidList ) {

			logger.debug( "looking for pid(s): '{}', in map: {}", pid, pidToCpu ) ;
			// if (Application.isRunningOnDesktop()) {
			// Random rg = new Random(System.currentTimeMillis());
			// // hook for testing
			// result = rg.nextFloat() * 50;
			// // result = (System.currentTimeMillis()
			// // - lastAccessTime);
			// }
			lastAccessTime = System.currentTimeMillis( ) ; // keep Top Active

			if ( pidToCpu.containsKey( pid ) ) {

				try {

					float f = Float.parseFloat( pidToCpu.get( pid ) ) ;
					result += f ;

				} catch ( NumberFormatException e ) {

					logger.debug( "Skipping past: {} cpu: {}", pid, pidToCpu.get( pid ) ) ;

				}

			}

		}

		return result ;

	}

	public boolean isInteger ( String input ) {

		try {

			Integer.parseInt( input ) ;
			return true ;

		} catch ( Exception e ) {

			return false ;

		}

	}

}
