package org.csap.agent.linux ;

import java.io.BufferedWriter ;
import java.io.File ;
import java.io.IOException ;
import java.util.ArrayList ;
import java.util.Collections ;
import java.util.List ;
import java.util.concurrent.Callable ;
import java.util.concurrent.ExecutorCompletionService ;
import java.util.concurrent.ExecutorService ;
import java.util.concurrent.Executors ;
import java.util.concurrent.Future ;
import java.util.concurrent.TimeUnit ;

import org.apache.commons.lang3.concurrent.BasicThreadFactory ;
import org.csap.agent.CsapApis ;
import org.csap.agent.CsapConstants ;
import org.csap.agent.api.AgentApi ;
import org.csap.agent.model.Application ;
import org.csap.agent.services.OsManager ;
import org.csap.helpers.CSAP ;
import org.csap.helpers.CsapApplication ;
import org.csap.security.SpringAuthCachingFilter ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.core.io.FileSystemResource ;
import org.springframework.http.HttpStatus ;
import org.springframework.http.ResponseEntity ;
import org.springframework.http.client.SimpleClientHttpRequestFactory ;
import org.springframework.util.LinkedMultiValueMap ;
import org.springframework.web.client.RestTemplate ;

import com.fasterxml.jackson.core.JsonProcessingException ;
import com.fasterxml.jackson.databind.ObjectMapper ;
import com.fasterxml.jackson.databind.node.ArrayNode ;
import com.fasterxml.jackson.databind.node.ObjectNode ;

public class TransferManager {

	private static final String TRANSFER_RESULTS = "transferResults" ;

	final Logger logger = LoggerFactory.getLogger( TransferManager.class ) ;

	ExecutorCompletionService<String> fileTransferComplete ;
	ExecutorService fileTransferService ;
	int jobCount = 0 ;

	OsCommandRunner osCommandRunner ;

	BufferedWriter globalWriterForResults ;

	int timeOutSeconds = 120 ;
	CsapApis csapApis ;

	/**
	 * 
	 * Very transient
	 * 
	 * @param timeOutSeconds
	 * @param numberOfThreads
	 * @param outputWriter
	 */
	public TransferManager ( CsapApis csapApis, int timeOutSeconds, BufferedWriter outputWriter ) {

		this.csapApis = csapApis ;

		logger.debug( "Number of workers: {}", csapApis.application( ).rootProjectEnvSettings( )
				.getNumberWorkerThreads( ) ) ;
		this.timeOutSeconds = timeOutSeconds ;

		osCommandRunner = new OsCommandRunner( timeOutSeconds, 1, "TransferMgr" ) ;

		this.globalWriterForResults = outputWriter ;

		updateProgress( "" ) ; // blank line
		updateProgress( "Artifact Transfer: maximum " + csapApis.application( ).rootProjectEnvSettings( )
				.getNumberWorkerThreads( )
				+ " threads.\n\n" ) ;

		BasicThreadFactory schedFactory = new BasicThreadFactory.Builder( )
				.namingPattern( "CsapFileTransfer-%d" )
				.daemon( true )
				.priority( Thread.NORM_PRIORITY )
				.build( ) ;

		fileTransferService = Executors.newFixedThreadPool( csapApis.application( ).rootProjectEnvSettings( )
				.getNumberWorkerThreads( ),
				schedFactory ) ;

		fileTransferComplete = new ExecutorCompletionService<String>( fileTransferService ) ;

	}

	private boolean isDeleteExisting = false ;

	public void setDeleteExisting (
									boolean isDeleteExisting ) {

		this.isDeleteExisting = isDeleteExisting ;

	}

	long lastFlush = System.currentTimeMillis( ) ;

	public static String UI_INDENT = "  " ;

	private void updateProgress (
									String content ) {

		if ( globalWriterForResults != null ) {

			try {

				globalWriterForResults.write( UI_INDENT + content + "\n" ) ;
				globalWriterForResults.flush( ) ; // inefficient - but UI tracking
													// is desirable
				// if (System.currentTimeMillis() - lastFlush > 2 * 1000) {
				// globalWriterForResults.flush();
				// lastFlush = System.currentTimeMillis();
				// }

			} catch ( IOException e ) {

				logger.error( "Failed progress update", e ) ;

			}

		}

	}

	public void shutdown ( ) {

		logger.debug( "Shutdown of transfer Pool requested (normal)" ) ;
		fileTransferService.shutdownNow( ) ;

	}

	public void httpCopyViaCsAgent (
										String auditUser ,
										File sourceLocation ,
										String destName ,
										List<String> targetHostList )
		throws IOException {

		httpCopyViaCsAgent( auditUser, sourceLocation, destName, targetHostList, csapApis.application( )
				.getAgentRunUser( ) ) ;

	}

	// Combination of host and file name
	private volatile List<String> _itemsRemaining = Collections.synchronizedList( new ArrayList<String>( ) ) ;

	/**
	 * 
	 * Very difficult to test. Requires timeouts in network...Very cautios when
	 * altering.
	 * 
	 * @param host
	 * @param sourceLocation
	 */
	synchronized public void removeJob (
											String host ,
											File sourceLocation ) {

		logger.debug( "{} Removing item: {}", host, sourceLocation.getName( ) ) ;
		_itemsRemaining.remove( host + ":" + sourceLocation.getName( ) ) ;

	}

	public void httpCopyViaCsAgent (
										String auditUser ,
										File sourceLocation ,
										String destinationLocation ,
										List<String> destinationHosts ,
										String chownUserid )
		throws IOException {

		if ( ! CsapApplication.isCsapFolderSet( ) ) {

			if ( ! chownUserid.equals( "root" ) ) {

				logger.info( CsapApplication.testHeader( "Setting user to csap for desktop" ) ) ;
				chownUserid = "csap" ;

			}

		}

		// what about multiple lists?
		for ( var host : destinationHosts ) {

			// if ( ! host.equals(Application.getHOST_NAME()))
			_itemsRemaining.add( host + ":" + sourceLocation.getName( ) ) ;

		}

		File workingFolder = csapApis.application( ).csapPlatformTemp( ) ;

		if ( ! workingFolder.exists( ) ) {

			workingFolder.mkdirs( ) ;

		}

		var fileName = new File( sourceLocation.getName( ) + ".tgz" ) ;
		var zipLocation = new File( workingFolder, fileName.getName( ) ) ;
		var unzipAsRootScriptFile = new File( csapApis.application( ).getCsapInstallFolder( ),
				"/bin/csap-unzip-as-root.sh" ) ;

		var parmList = new ArrayList<String>( ) ;

		logger.warn( " Zipping: {} to {} \n\t then transferring to: {} ",
				sourceLocation.getName( ), zipLocation.getAbsolutePath( ), _itemsRemaining ) ;

		// Using linux to build a compressed file
		if ( csapApis.application( ).isRunningAsRoot( ) ) {

			parmList.add( "/usr/bin/sudo" ) ;
			parmList.add( unzipAsRootScriptFile.getAbsolutePath( ) ) ;
			parmList.add( sourceLocation.getAbsolutePath( ) ) ;
			parmList.add( zipLocation.getAbsolutePath( ) ) ;
			// parmList.add(chownUserid);

		} else {

			parmList.add( "bash" ) ;
			parmList.add( "-c" ) ;
			String command = unzipAsRootScriptFile.getAbsolutePath( )
					+ " " + sourceLocation.getAbsolutePath( )
					+ " " + zipLocation ;

			parmList.add( command ) ;

		}

		// Build a .tgz file

		var results = "" ;

		if ( csapApis.application( ).isJunit( ) ) {

			results = "junit profile: skipping execution of " + parmList ;

		} else {

			results = osCommandRunner.executeString( null, parmList ) ;

		}

		logger.info( results ) ;

		if ( Application.isRunningOnDesktop( ) ) {

			// Use a java based utility for windows desktop
			fileName = new File( sourceLocation.getName( ) + ".zip" ) ;
			zipLocation = new File( workingFolder, fileName.getName( ) ) ;
			ZipUtility.zipDirectory( sourceLocation, zipLocation ) ;

		}

		for ( var host : destinationHosts ) {

			//
			// filter hosts on desktop
			//
			if ( Application.isRunningOnDesktop( )
					&& ! host.equals( "csap-dev01" )
					&& ! host.equals( "csap-dev02" )
					&& ! host.equals( "csap-dev03" )
					&& ! host.equals( CsapApis.getInstance( ).application( ).getCsapHostName( ) ) ) {

				logger.info( CsapApplication.testHeader( "desktop development, skipping {}" ), host ) ;
				updateProgress( "\nDesktop development, skipping: " + host ) ;

				try {

					Thread.sleep( 5000 ) ;

				} catch ( InterruptedException e ) {

					logger.error( "Failed waiting for results on host: " + host, e ) ;

				}

				continue ;

			}

			// transferExecutorPool.execute(new HttpTransferRunnable(host,
			// zipLocation,
			// extractDir, reload));
			jobCount++ ;
			fileTransferComplete.submit(
					new HttpTransferRunnable(
							csapApis, auditUser,
							host, sourceLocation, zipLocation,
							destinationLocation, chownUserid ) ) ;

		}

	}

	public String waitForComplete ( ) {

		return waitForComplete( "\n", "\n" ) ;

	}

	ObjectMapper jacksonMapper = new ObjectMapper( ) ;

	public ArrayNode waitForCompleteJson ( ) {

		ArrayNode resultsNode = jacksonMapper.createArrayNode( ) ;

		try {

			showBlockingJobMessage( ) ;

			for ( int jobIndex = 1; jobIndex <= jobCount; jobIndex++ ) {

				Future<String> finishedJob = fileTransferComplete.take( ) ;

				String resultString = finishedJob.get( ) ;

				var resultReport = (ObjectNode) jacksonMapper.readTree( resultString ) ;
				resultsNode.add( resultReport ) ;

				processTransferResults( jobIndex, resultString, resultReport ) ;

			}

		} catch ( Exception e ) {

			logger.error( "One or more transfers failed to complete \n {}", CSAP.buildCsapStack( e ) ) ;

			resultsNode.add( CsapConstants.CONFIG_PARSE_ERROR
					+ CsapConstants.CONFIG_PARSE_ERROR
					+ ": One or more scps failed to complete" ) ;

		}

		shutdown( ) ;

		return resultsNode ;

	}

	void showBlockingJobMessage ( ) {

		logger.info( "jobCount: {}", jobCount ) ;

		// boolean status = transferExecutorPool.awaitTermination(300,
		// TimeUnit.SECONDS);
		updateProgress( "" ) ;
		updateProgress( "Waiting for Jobs to complete: " + jobCount ) ;
		updateProgress( "" ) ;

	}

	public String waitForComplete (
									String pre ,
									String post ) {

		showBlockingJobMessage( ) ;

		StringBuffer results = new StringBuffer( ) ;

		try {

			for ( int jobIndex = 1; jobIndex <= jobCount; jobIndex++ ) {

				Future<String> finishedJob = fileTransferComplete.take( ) ;
				results.append( pre ) ;
				String resultString = finishedJob.get( ) ;
				String summary = resultString ;
				ObjectNode resultReport = null ;

				try {

					resultReport = (ObjectNode) jacksonMapper.readTree( summary ) ;

				} catch ( Exception e ) {

					logger.error( "Failed parsing response" ) ;

				}

				processTransferResults( jobIndex, resultString, resultReport ) ;

				results.append( resultString ) ;
				results.append( post ) ;

				// Need to add progress indicator
			}

		} catch ( Exception e ) {

			logger.error( "One or more transfers failed to complete", e ) ;

			results.append( "\n\n" + CsapConstants.CONFIG_PARSE_ERROR
					+ CsapConstants.CONFIG_PARSE_ERROR
					+ ": One or more scps failed to complete" ) ;

		}

		shutdown( ) ;

		return results.toString( ) ;

	}

	private void processTransferResults (
											int jobIndex ,
											String rawResultText ,
											ObjectNode resultReport ) {

		var summary = rawResultText ;

		if ( summary.length( ) > 140 ) {

			summary = summary.substring( 0, 139 ) ;

		}

		if ( rawResultText.contains( CsapConstants.CONFIG_PARSE_ERROR )
				|| resultReport == null ) {

			var errorText = rawResultText ;

			if ( resultReport != null
					&& resultReport.path( "error" ).isTextual( ) ) {

				errorText = resultReport.path( "error" ).asText( ) ;

			}

			updateProgress( "" ) ;
			updateProgress( "Failed job: " + jobIndex + " of " + jobCount + ": " + errorText ) ;
			logger.error( "Failed job: " + jobIndex + " of " + jobCount + ": " + errorText ) ;

		} else {

			logger.debug( "resultReport: {}", CSAP.jsonPrint( resultReport ) ) ;

			var firstResultLines = resultReport.path( TRANSFER_RESULTS ).path( AgentApi.CORE_RESULTS ).path( 0 )
					.asText( ).split( "\n" ) ;
			var shortResults = summary ;

			if ( firstResultLines.length > 2 ) {

				shortResults = firstResultLines[0] ;

			}

			var progressMessage = "Completed " + jobIndex + " of " + jobCount + ": "
					+ " " + resultReport.path( "host" ).asText( )
					+ " response " + shortResults ;

			logger.info( progressMessage ) ;

			updateProgress( progressMessage ) ;

		}

		if ( jobCount - jobIndex <= 6
				&& _itemsRemaining.size( ) > 0 ) {

			updateProgress( "    Waiting for response from: " + _itemsRemaining ) ;

		}

	}

	final static int CHUNKING_SIZE = 16 * 1024 ;

	public class HttpTransferRunnable implements Callable<String> {

		private String extractDir = "" ;
		private String auditUser = "" ;
		private String host = "" ;
		private File zipLocation ;
		private String chownUserid ;
		private File sourceLocation ;
		private CsapApis csapApis ;

		public HttpTransferRunnable ( CsapApis csapApis, String auditUser, String host, File sourceLocation,
				File zipLocation, String extractDir, String chownUserid ) {

			this.csapApis = csapApis ;

			this.sourceLocation = sourceLocation ;
			this.host = host ;
			this.auditUser = auditUser ;
			this.zipLocation = zipLocation ;
			this.extractDir = extractDir ;

			// Convert x/y/z/CSAP_FOLDER/... to __platform__/...
			//
			// support for transfers of files with different installation (CSAP_FOLDER)
			// locations
			// platform will get resolved client side - based on local install folder
			// Mostly - for desktop - but some labs might do this as well

			var csapPlatformFolder = Application.filePathAllOs( csapApis.application( ).getCsapInstallFolder( ) ) ;

			if ( extractDir.startsWith( csapPlatformFolder ) ) {

				this.extractDir = Application.FileToken.PLATFORM.value + extractDir.substring( csapPlatformFolder
						.length( ) ) ;

			}

			logger.debug( "staging: {} extractDir: {}", csapPlatformFolder, this.extractDir ) ;
			this.chownUserid = chownUserid ;

		}

		public String call ( ) {

			ObjectNode transferResult = jacksonMapper.createObjectNode( ) ;

			var timer = csapApis.metrics( ).startTimer( ) ;

			// default to using csapApp connection
			RestTemplate restTemplate = csapApis.application( ).getAgentPooledConnection( zipLocation.length( ),
					timeOutSeconds ) ;
			String connectionType = "pooled" ;

			if ( restTemplate.getRequestFactory( ) instanceof SimpleClientHttpRequestFactory ) {

				connectionType = "transient" ;

			}

			logger.debug( "{} sending, timeout seconds: {}, file: {}, extractDir: {}, connection type: {}",
					host, timeOutSeconds, zipLocation, extractDir, connectionType ) ;

			transferResult.put( "host", host ) ;
			transferResult.put( "connectionType", connectionType ) ;

			updateProgress( "Sending to host: " + host + " file: " + zipLocation + " using connection: "
					+ connectionType ) ;

			// HashMap<String,String> urlVariables = new
			// HashMap<String,String>() ;
			var transferParameters = new LinkedMultiValueMap<String, Object>( ) ;
			transferParameters.add( "distFile", new FileSystemResource( zipLocation ) ) ;
			transferParameters.add( "extractDir", extractDir ) ;
			transferParameters.add( "timeoutSeconds", Integer.toString( timeOutSeconds ) ) ;
			transferParameters.add( "auditUser", auditUser ) ;
			transferParameters.add( "chownUserid", chownUserid ) ;

			transferParameters.set( SpringAuthCachingFilter.USERID, csapApis.application( ).rootProjectEnvSettings( )
					.getAgentUser( ) ) ;
			transferParameters.set( SpringAuthCachingFilter.PASSWORD, csapApis.application( ).rootProjectEnvSettings( )
					.getAgentPass( ) ) ;

			if ( isDeleteExisting ) {

				transferParameters.add( "deleteExisting", "deleteExisting" ) ;

			}

			var targetAgentUrl = csapApis.application( ).getAgentUrl( host, CsapConstants.API_AGENT_URL
					+ AgentApi.PLATFORM_UPDATE,
					true ) ;

			if ( csapApis.application( ).isJunit( ) ) {

				targetAgentUrl = "http://" + host + ":" + csapApis.application( ).flexFindFirstInstanceCurrentHost(
						CsapConstants.AGENT_NAME ).getPort( ) + CsapConstants.API_AGENT_URL + AgentApi.PLATFORM_UPDATE ;
				logger.warn( CsapApplication.testHeader( "junit agent url: {}" ), targetAgentUrl ) ;

			}

			// uncomment for local testing
			// if ( Application.isRunningOnDesktop() ) {
			// url = csapApp.getAgentUrl( "localhost", AgentMicroService.OS_URL
			// + HostRequests.UPDATE_PLATFORM );
			// }

			try {

				var details = CSAP.buildDescription( targetAgentUrl, transferParameters ) ;

				logger.info( "Updating {} ", details ) ;

				ResponseEntity<String> response = restTemplate.postForEntity( targetAgentUrl, transferParameters,
						String.class ) ;

				String agentResponse = response.getBody( ) ;

				if ( agentResponse != null
						&& agentResponse.contains( CsapConstants.CONFIG_PARSE_ERROR ) ) {

					int numRetries = 3 ;

					while ( agentResponse.contains( OsManager.MISSING_PARAM_HACK )
							&& ( numRetries-- > 0 ) ) {

						// Very rare, but 1 or more of params sent is an empty
						// string despite being set. Source of bug is unknown
						// (tomcat/spring/multipart/...)
						try {

							logger.warn( "Detected missing param in response, retrying  request :" + targetAgentUrl
									+ "\n Variables: " + transferParameters + "\n  Response: \n"
									+ agentResponse ) ;
							Thread.sleep( 3000 ) ;

						} catch ( InterruptedException e ) {

							logger.error( "Failed thread.sleep waiting to retry push of files: " + e.getMessage( ) ) ;

						}

						csapApis.metrics( ).incrementCounter( "java.TransferManager.retryFilePush" ) ;
						response = restTemplate.postForEntity( targetAgentUrl, transferParameters, String.class ) ;
						agentResponse = response.getBody( ) ;

					}

					if ( agentResponse != null
							&& agentResponse.contains( CsapConstants.CONFIG_PARSE_ERROR ) ) {

						logger.error( "Error transferring file(s)  {}  Response: \n {}",
								details,
								agentResponse ) ;

					}

				}

				// String agentResponse = restTemplate.postForObject(url,
				// urlVariables, String.class);

				logger.debug( "response from  url: {}, http status: {}, params: {}, agentResponse: {}",
						targetAgentUrl, response.getStatusCode( ), transferParameters, agentResponse ) ;
				var nanos = csapApis.metrics( ).stopTimer( timer, "java.TransferManager" ) ;

				if ( ! response.getStatusCode( ).equals( HttpStatus.OK ) ) {

					throw new Exception( "Failed to get HttpStatus.OK, status code: " + response.getStatusCode( ) ) ;

				}

				String duration = TimeUnit.NANOSECONDS.toSeconds( nanos ) + " seconds" ;

				// Need to search for missing operand?
				try {
					// logger.info("agentResponse\n" + agentResponse);

					ObjectNode hostResponse = (ObjectNode) jacksonMapper.readTree( agentResponse ) ;
					String timeTaken = "*** Transfer time on " + host + ",   item " + sourceLocation.getName( )
							+ " was: "
							+ duration ;

					logger.debug( timeTaken ) ;
					transferResult.put( "transferTime", duration ) ;

					transferResult.set( TRANSFER_RESULTS, hostResponse ) ;

				} catch ( Exception e ) {

					transferResult.put( TRANSFER_RESULTS, "Error - failed to parse transfer response: " + e ) ;
					logger.error( "Failed to json parse response: \n" + agentResponse, e ) ;

				}

			} catch ( Exception e ) {

				// DEFINITELY TEST THIS: on a target VM, edit
				// Staging/bin/unzipAsroot, and add a sleep 200
				//

				transferResult.put( "error", "\n\n**" + CsapConstants.CONFIG_PARSE_ERROR
						+ " Failed to transfer: " + sourceLocation.getName( ) + " to:" + targetAgentUrl + " Message: "
						+ e.getMessage( ) ) ;

				logger.warn(
						"Failed transferring to  host: {}  sourceLocation: {} \n Read timeout seconds: {} \n Exception: {}",
						host,
						sourceLocation.getName( ),
						timeOutSeconds,
						e.getLocalizedMessage( ) ) ;

				logger.debug( "{}", CSAP.buildCsapStack( e ) ) ;

			}

			removeJob( host, sourceLocation ) ;

			try {

				return jacksonMapper.writeValueAsString( transferResult ) ;

			} catch ( JsonProcessingException e ) {

				logger.error( "Failed to write response", e ) ;
				return "Error in connection" ;

			}

		}
	}

}
