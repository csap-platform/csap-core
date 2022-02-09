package org.csap.agent.linux ;

import java.io.BufferedWriter ;
import java.io.File ;
import java.io.FileWriter ;
import java.io.IOException ;
import java.time.LocalDateTime ;
import java.time.format.DateTimeFormatter ;

import org.apache.commons.io.FileUtils ;
import org.csap.agent.CsapApis ;
import org.csap.agent.model.Application ;
import org.csap.helpers.CSAP ;
import org.csap.helpers.CsapApplication ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;

public class OutputFileMgr {

	final Logger logger = LoggerFactory.getLogger( OutputFileMgr.class ) ;

	private BufferedWriter bufferedWriter = null ;

	public BufferedWriter getBufferedWriter ( ) {

		try {

			bufferedWriter.flush( ) ;

		} catch ( IOException e ) {

			logger.error( "Failed flushing outputFile: {}", CSAP.buildCsapStack( e ) ) ;

		}

		return bufferedWriter ;

	}

	File outputFile ;

	public File getOutputFile ( ) {

		return outputFile ;

	}

	public void setOutputFile ( File outputFile ) {

		this.outputFile = outputFile ;

	}

	public String getContents ( ) {

		return Application.readFile( outputFile ) ;

	}

	public OutputFileMgr ( File rootFolder, String name ) throws IOException {

		FileWriter fstream ;

		if ( ! name.endsWith( ".log" ) ) {

			name = name + ".log" ;

		}

		outputFile = new File( rootFolder, name ) ;

		if ( ! outputFile.getParentFile( ).exists( ) ) {

			logger.warn( "Parent folder does not exist, creating: {}", outputFile.getParentFile( ).toURI( )
					.getPath( ) ) ;
			FileUtils.forceMkdir( outputFile.getParentFile( ) ) ;

		}

		logger.debug( "File is at: {}", outputFile.getAbsolutePath( ) ) ;

		fstream = new FileWriter( outputFile, false ) ;
		bufferedWriter = new BufferedWriter( fstream ) ;

		bufferedWriter.write( CsapApplication.header(
				CsapApis.getInstance( ).application( ).getCsapHostName( )
						+ "\t" +
						LocalDateTime.now( ).format(
								DateTimeFormatter.ofPattern( "HH:mm:ss \t MMMM d uuuu " ) ) ) ) ;

		bufferedWriter.write( "\n\n" ) ;

		bufferedWriter.flush( ) ;

	}

	public final static String OUTPUT_COMPLETE_TOKEN = "__COMPLETED__" ;

	public void opCompleted ( ) {

		print( "\n\n\n" ) ;
		print( OUTPUT_COMPLETE_TOKEN ) ;
		close( ) ;

	}

	public void close ( ) {

		if ( bufferedWriter != null ) {

			try {

				bufferedWriter.write( "\n\n *** " +
						LocalDateTime.now( ).format( DateTimeFormatter.ofPattern( "HH:mm:ss,   MMMM d  uuuu " ) )
						+ "\n\n" ) ;
				bufferedWriter.close( ) ;
				bufferedWriter = null ;

			} catch ( IOException e ) {

				logger.error( "Failed closing outputFile: {}", CSAP.buildCsapStack( e ) ) ;

			}

		}

	}

	protected void finalize ( )
		throws Throwable {

		close( ) ;

	};

	long lastFlush = System.currentTimeMillis( ) ;

	public void print ( String content ) {

		if ( bufferedWriter != null ) {

			try {

				bufferedWriter.write( content + "\n" ) ;

				if ( ( System.currentTimeMillis( ) - lastFlush > 5 * 1000 )
						|| isForceImmediate( ) ) {

					bufferedWriter.flush( ) ;
					lastFlush = System.currentTimeMillis( ) ;

				}

			} catch ( IOException e ) {

				logger.error( "Failed progress update: Content: {} \n reason:  {} ", content, CSAP.buildCsapStack(
						e ) ) ;

			}

		}

	}

	boolean forceImmediate = false ;

	public boolean isForceImmediate ( ) {

		return forceImmediate ;

	}

	public void setForceImmediate ( boolean forceImmediate ) {

		this.forceImmediate = forceImmediate ;

	}

	public void printImmediate ( String content ) {

		if ( bufferedWriter != null ) {

			try {

				bufferedWriter.write( content + "\n" ) ;
				bufferedWriter.flush( ) ;

			} catch ( IOException e ) {

				logger.error( "Failed progress update", e ) ;

			}

		}

	}

	public void printHeader ( String content ) {

		printImmediate( CsapApplication.header( content ) ) ;

	}

}
