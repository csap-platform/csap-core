//package org.csap.agent.integrations ;
//
//import java.io.BufferedWriter ;
//import java.io.File ;
//import java.io.IOException ;
//import java.io.Writer ;
//import java.util.ArrayList ;
//import java.util.Arrays ;
//import java.util.Collections ;
//import java.util.List ;
//
//import javax.servlet.http.HttpSession ;
//
//import org.apache.commons.io.FileUtils ;
//import org.csap.agent.CsapCore ;
//import org.csap.agent.linux.OsCommandRunner ;
//import org.csap.agent.linux.OutputFileMgr ;
//import org.csap.agent.model.Application ;
//import org.csap.agent.model.ServiceInstance ;
//import org.csap.helpers.CSAP ;
//import org.csap.helpers.CsapApplication ;
//import org.eclipse.jgit.api.CloneCommand ;
//import org.eclipse.jgit.api.Git ;
//import org.eclipse.jgit.api.PullCommand ;
//import org.eclipse.jgit.api.PullResult ;
//import org.eclipse.jgit.api.PushCommand ;
//import org.eclipse.jgit.diff.DiffEntry ;
//import org.eclipse.jgit.lib.ObjectId ;
//import org.eclipse.jgit.lib.ObjectReader ;
//import org.eclipse.jgit.lib.ProgressMonitor ;
//import org.eclipse.jgit.lib.Repository ;
//import org.eclipse.jgit.lib.TextProgressMonitor ;
//import org.eclipse.jgit.storage.file.FileRepositoryBuilder ;
//import org.eclipse.jgit.transport.PushResult ;
//import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider ;
//import org.eclipse.jgit.treewalk.CanonicalTreeParser ;
//import org.jasypt.encryption.pbe.StandardPBEStringEncryptor ;
//import org.slf4j.Logger ;
//import org.slf4j.LoggerFactory ;
//import org.springframework.beans.factory.annotation.Autowired ;
//import org.springframework.context.event.ContextRefreshedEvent ;
//import org.springframework.context.event.EventListener ;
//import org.springframework.stereotype.Service ;
//import org.tmatesoft.svn.core.SVNCancelException ;
//import org.tmatesoft.svn.core.SVNCommitInfo ;
//import org.tmatesoft.svn.core.SVNDepth ;
//import org.tmatesoft.svn.core.SVNException ;
//import org.tmatesoft.svn.core.SVNURL ;
//import org.tmatesoft.svn.core.internal.io.dav.DAVRepositoryFactory ;
//import org.tmatesoft.svn.core.wc.ISVNEventHandler ;
//import org.tmatesoft.svn.core.wc.SVNClientManager ;
//import org.tmatesoft.svn.core.wc.SVNCommitClient ;
//import org.tmatesoft.svn.core.wc.SVNEvent ;
//import org.tmatesoft.svn.core.wc.SVNRevision ;
//import org.tmatesoft.svn.core.wc.SVNUpdateClient ;
//import org.tmatesoft.svn.core.wc.SVNWCUtil ;
//
///**
// *
// * svnkit is key provider: reference http://svnkit.com/kb/
// *
// *
// * @author someDeveloper
// *
// */
//@Service
//public class VersionControl_Svn {
//
//	final Logger				logger						= LoggerFactory.getLogger( getClass() ) ;
//
//	public static final String	CONFIG_SUFFIX_FOR_UPDATE	= "_working" ;
//
//	@Autowired
//	public VersionControl_Svn( Application csapApp, StandardPBEStringEncryptor encryptor ) {
//
//		this.csapApp	= csapApp ;
//		this.encryptor	= encryptor ;
//
//	}
//
//	@EventListener ( { ContextRefreshedEvent.class } )
//	public void initialize_svn_if_enabled () {
//
//		if ( csapApp.isSvnEnabled() ) {
//			logger.info( "Initializing DAVRepository for use by svn" ) ;
//			DAVRepositoryFactory.setup() ;
//		}
//
//	}
//
//	private Application					csapApp ;
//	private StandardPBEStringEncryptor	encryptor ;
//
//	public enum ScmProvider {
//
//		svn( "svn" ), git( "git" );
//
//		public String key ;
//
//		private ScmProvider( String jsonKey ) {
//			this.key = jsonKey ;
//		}
//
//		public static ScmProvider parse ( String type ) {
//			if ( type.equals( ScmProvider.svn.key ) ) {
//				return svn ;
//			}
//			return git ;
//		}
//	}
//
//	public void checkOutFolder (	String scmUserid, String encodedPass,
//									String svnBranch, String svcName,
//									ServiceInstance serviceInstance,
//									BufferedWriter outputWriter )
//			throws Exception {
//
//		if ( serviceInstance.getScm().equals( ScmProvider.git.key ) ) {
//			checkOutFolderUsingGit( scmUserid, encodedPass, svnBranch, svcName, serviceInstance, outputWriter ) ;
//		} else {
//			checkOutFolderUsingSvn( scmUserid, encodedPass, svnBranch, svcName, serviceInstance, outputWriter ) ;
//		}
//
//	}
//
//	private void checkOutFolderUsingSvn (	String scmUserid, String encodedPass,
//											String svnBranch, String svcName, ServiceInstance serviceInstance,
//											BufferedWriter outputWriter )
//			throws Exception {
//
//		File svnCheckoutFolder = csapApp.getCsapBuildFolder( svcName ) ;
//
//		// Hook for application definitions, no longer needed as renames will be
//		// done.
//		if ( svnCheckoutFolder.exists()
//				&& (csapApp.getRootModelBuildLocation().contains( svcName ) || (csapApp
//					.getRootModelBuildLocation() + CONFIG_SUFFIX_FOR_UPDATE)
//						.contains( svcName )) ) {
//			try {
//				outputWriter.write( OutputFileMgr.build_shell_log( "Deleting: " + svnCheckoutFolder.getCanonicalPath() ) ) ;
//				FileUtils.deleteDirectory( svnCheckoutFolder ) ;
//			} catch ( Exception e ) {
//				logger.error(
//					"Failed to delete"
//							+ svnCheckoutFolder.getAbsolutePath(),
//					e ) ;
//			}
//		}
//
//		SVNURL url = SVNURL.parseURIEncoded( serviceInstance.getScmLocation()
//			.replace( "trunk", svnBranch ) ) ;
//		if ( svnBranch.startsWith( "http" ) ) {
//			// a full url specified
//			url = SVNURL.parseURIEncoded( svnBranch ) ;
//		}
//		final StringBuilder results = new StringBuilder(
//			"SVN check triggered on " + url + "\n\n" ) ;
//
//		logger.info( "svnCheckoutFolder:" + svnCheckoutFolder.getAbsolutePath() + " url:" + url ) ;
//
//		SVNClientManager cm = buildSvnCredentials( scmUserid, encodedPass ) ;
//
//		SVNUpdateClient svnUpdateClient = cm.getUpdateClient() ;
//		// cm.get
//		// VERY VERY slow
//
//		svnUpdateClient.setEventHandler( buildSvnEventHandler( OUTPUT_FILTER + "Checking out: ", outputWriter ) ) ;
//
//		// uc.doCheckout(url, dstPath, SVNRevision.UNDEFINED,
//		// SVNRevision.HEAD, true);
//		// session.setAttribute("line1", "Starting delete of previous" +
//		// instanceConfig.getScmLocation());
//		// deleteDir(svnCheckoutFolder ) ;
//		updateProgress( outputWriter,
//			"\n ==================== Starting Checkout of "
//					+ serviceInstance.getScmLocation() + " ==========" ) ;
//
//		results.append( "Starting Checkout\n\n" ) ;
//		logger.debug( "csapApp.getScmPath(): "
//				+ csapApp.getRootModelBuildLocation() + " svcName:" + svcName ) ;
//
//		if ( csapApp.getRootModelBuildLocation().contains( svcName ) ) {
//			// Svn export - excludes .svn folders
//			// Hook for Cluster definition files - only they will match the
//			// clause. Done this way since there are relatively few files in a
//			// cluster config,
//			// and they are copied verbatum to the staging/conf folder so it is
//			// not desirable to do incemental checkouts
//			//
//			List<String> parmList = new ArrayList<String>() ;
//			Collections.addAll( parmList, "bash", "-c", "rm -rf "
//					+ svnCheckoutFolder ) ;
//			results.append( executeShell( parmList, outputWriter ) + "\n" ) ;
//			logger.warn( "SVN Export of " + url + " to folder:"
//					+ svnCheckoutFolder.getAbsolutePath() ) ;
//			svnUpdateClient.doExport( url, svnCheckoutFolder, SVNRevision.HEAD,
//				SVNRevision.HEAD, System.getProperty( "line.separator" ),
//				true, SVNDepth.INFINITY ) ;
//		} else {
//			// Svn checkout (includes .svn folders)
//			// All JVMs and cluster updates end up here and do an incrementatl
//			// checkout.
//			logger.info( "SVN Update of " + url + " to folder: "
//					+ svnCheckoutFolder.getAbsolutePath() ) ;
//			svnUpdateClient
//				.doCheckout( url, svnCheckoutFolder, SVNRevision.HEAD,
//					SVNRevision.HEAD, SVNDepth.INFINITY, true ) ;
//		}
//
//		updateProgress( outputWriter, "Completed Checkout count: " + lineCount ) ;
//
//		results.append( "\nCompleted Checkout count: " + lineCount + "\n" ) ;
//
//		return ;
//	}
//
//	public void doSvnDelete (
//								String baseConfigUrl, List<File> filesToDelete,
//								String scmUserid, String encodedPass,
//								String svnBranch, String comment,
//								BufferedWriter outputWriter )
//			throws Exception {
//
//		updateProgress( outputWriter, "Starting delete of files: " + filesToDelete ) ;
//
//		// if ( !fileToDelete.exists() ) {
//		// updateProgress( outputWriter, "Skipping - file does not exist" );
//		// logger.info( "File does not exist - do nothing" );
//		// }
//		SVNClientManager cm = buildSvnCredentials( scmUserid, encodedPass ) ;
//		SVNCommitClient svnCommitClient = cm.getCommitClient() ;
//
//		// this will update output with files deleted
//		svnCommitClient.setEventHandler( buildSvnEventHandler( "Removing: ", outputWriter ) ) ;
//
//		svnDelete( outputWriter, null, filesToDelete, baseConfigUrl, svnCommitClient, comment ) ;
//	}
//
//	private void svnDelete (	BufferedWriter bw, File baseFolder, List<File> filesToDelete, String baseConfigUrl,
//								SVNCommitClient svnCommitClient, String comment )
//			throws SVNException {
//		List<SVNURL> urlsToDelete = new ArrayList<>() ;
//
//		filesToDelete.forEach( deleteFile -> {
//			try {
//				String deleteTarget = baseConfigUrl + "/" + deleteFile.getName() ;
//				if ( baseFolder != null ) {
//					String newPath = deleteFile.toURI().getPath().substring( baseFolder.toURI().getPath().length() ) ;
//					deleteTarget = baseConfigUrl + "/" + newPath ;
//				}
//				logger.info( "url to check: {}", deleteTarget ) ;
//				SVNURL url = SVNURL.parseURIEncoded( deleteTarget ) ;
//				urlsToDelete.add( url ) ;
//				// deleting from fs now.
//
//				updateProgress( bw, "* Deleting from File system: " + deleteFile.getAbsolutePath() ) ;
//				FileUtils.deleteQuietly( deleteFile ) ;
//			} catch ( SVNException ex ) {
//				logger.error( "Failed to generated svn url for {}", deleteFile.getAbsolutePath(), ex ) ;
//			}
//		} ) ;
//
//		svnCommitClient.doDelete( urlsToDelete.toArray( new SVNURL[ 0 ] ), comment ) ;
//	}
//
//	public void checkInFolder (
//								ScmProvider scmType, String applicationUrl,
//								File releasePackageFile,
//								String scmUserid, String encodedPass,
//								String svnBranch, String comment,
//								BufferedWriter outputWriter )
//			throws Exception {
//
//		if ( scmType == ScmProvider.git ) {
//
//			checkInFolderGit(
//				applicationUrl, releasePackageFile,
//				scmUserid, encodedPass, svnBranch,
//				comment,
//				outputWriter ) ;
//		} else {
//			// checkInFolderSvn(
//			// applicationUrl, releasePackageFile,
//			// scmUserid, encodedPass, svnBranch,
//			// comment,
//			// outputWriter ) ;
//
//		}
//
//	}
//
//	private void checkInFolderGit (
//									String applicationUrl, File releasePackageFile,
//									String scmUserid, String encodedPass,
//									String svnBranch, String comment,
//									BufferedWriter outputWriter )
//			throws Exception {
//
//		logger.info( " releasePackageFile: {} ", releasePackageFile.getAbsolutePath() ) ;
//
//		File applicationFolder = releasePackageFile.getParentFile() ;
//		final BufferedWriter bw = outputWriter ;
//
//		updateProgress( bw, CsapApplication.header( "Git commit: " + applicationFolder.getPath() ) ) ;
//
//		File[] commitFileArray = new File[ 1 ] ;
//		// ciFiles[0] = jsConfigFile;
//
//		// This will update ALL files found in config directory
//		commitFileArray[0] = applicationFolder ;
//		if ( releasePackageFile.canRead() ) {
//			// Support for arbitrary file checkins - this will search tree for
//			// .git
//			FileRepositoryBuilder repositoryBuilder = new FileRepositoryBuilder() ;
//			repositoryBuilder.findGitDir( releasePackageFile ) ;
//			File gitLocation = repositoryBuilder.getGitDir() ;
//			logger.info( "Resolved git repo location: {} ", gitLocation ) ;
//
//			// FileRepositoryBuilder builder = new FileRepositoryBuilder();
//			ObjectId oldHead = null ;
//			try (Repository repository = repositoryBuilder.build()) {
//				oldHead = repository.resolve( "HEAD^{tree}" ) ;
//			}
//
//			// if ( filesToDelete != null && filesToDelete.size() > 0 ) {
//			// filesToDelete.forEach( file -> {
//			// // File deleteFile = new File(gitLocation, ) ;
//			// FileUtils.deleteQuietly( file ) ;
//			// } ) ;
//			// }
//
//			try (Git git = Git.open( gitLocation )) {
//
//				git.add().addFilepattern( "." ).call() ;
//				// git.commit().setMessage( comment ).call() ;
//				git.commit()
//					.setAll( true )
//					.setAuthor( scmUserid, scmUserid + "@yourcompany.com" )
//					.setMessage( "CSAP:" + comment )
//					.call() ;
//
//				// revCommit.
//
//				PushCommand pushCommand = git.push() ;
//
//				pushCommand.setCredentialsProvider(
//					new UsernamePasswordCredentialsProvider(
//						scmUserid,
//						encryptor.decrypt( encodedPass ) ) ) ;
//
//				pushCommand.setProgressMonitor( gitMonitor( outputWriter ) ) ;
//
//				Iterable<PushResult> result = pushCommand.call() ;
//				// result.iterator().next().getTrackingRefUpdates()
//				result.forEach( pushResult -> {
//					logger.info( "push messages: {}", pushResult.getMessages() ) ;
//					logger.info( "push tracking: {}", pushResult.getTrackingRefUpdates() ) ;
//					try {
//						outputWriter.append( "\n push remote Updates: " + pushResult.getRemoteUpdates() ) ;
//						outputWriter.flush() ;
//					} catch ( IOException e ) {
//						// TODO Auto-generated catch block
//						e.printStackTrace() ;
//					}
//				} ) ;
//
//				printGitModifications( gitLocation, outputWriter, repositoryBuilder, oldHead, git ) ;
//			}
//
//		}
//
//		// List<String> fileList = Arrays.asList(svnCheckoutFolder.list());
//		updateProgress( outputWriter, CsapApplication.header( "Completed GIT checkin" ) ) ;
//	}
//
//	/**
//	 *
//	 * 
//	 * @see http://svnkit.com/kb/javadoc/index.html
//	 * @param applicationSvnUrl
//	 * @param releasePackageFile
//	 * @param scmUserid
//	 * @param encodedPass
//	 * @param svnBranch
//	 * @param comment
//	 * @param filesToAdd
//	 * @param filesToDelete
//	 * @param outputWriter
//	 * @throws Exception
//	 */
//	private void checkInFolderSvn (
//									String applicationSvnUrl, File releasePackageFile,
//									String scmUserid, String encodedPass,
//									String svnBranch, String comment,
//									List<File> filesToAdd,
//									List<File> filesToDelete,
//									BufferedWriter outputWriter )
//			throws Exception {
//
//		logger.info( " releasePackageFile: {} \n adds: {} \n deletes: {}",
//			releasePackageFile.getAbsolutePath(), filesToAdd, filesToDelete ) ;
//
//		File applicationFolder = releasePackageFile.getParentFile() ;
//		final BufferedWriter bw = outputWriter ;
//
//		// if ( Application.isRunningOnDesktop() ) {
//		// logger.warn( "Skipping check in on desktop" );
//		// bw.write( "Skipping checkin on Desktop" + "\n" );
//		// return ;
//		// }
//		SVNClientManager cm = buildSvnCredentials( scmUserid, encodedPass ) ;
//
//		SVNCommitClient svnCommitClient = cm.getCommitClient() ;
//
//		svnCommitClient.setEventHandler( buildSvnEventHandler( "updating: ", outputWriter ) ) ;
//
//		updateProgress( bw,
//			"\n ==================== Starting svn commit of release package: "
//					+ releasePackageFile.getAbsolutePath()
//					+ ".  Parent folder will be commited ==========" ) ;
//
//		updateProgress( bw, "\n New files being added: " + filesToAdd + "\n Old files being deleted: " + filesToDelete ) ;
//
//		File[] commitFileArray = new File[ 1 ] ;
//		// ciFiles[0] = jsConfigFile;
//
//		// This will update ALL files found in config directory
//		commitFileArray[0] = applicationFolder ;
//		if ( releasePackageFile.canRead() ) {
//
//			// Support for subpackages... we add config file and ignore the
//			// already exists error.
//			addToSvnIfNotThere( releasePackageFile.getParentFile(), releasePackageFile, applicationSvnUrl, svnCommitClient, comment ) ;
//
//			if ( filesToDelete != null && filesToDelete.size() > 0 ) {
//				svnDelete( bw, applicationFolder, filesToDelete, applicationSvnUrl, svnCommitClient, comment ) ;
//				// doSvnDelete( applicationSvnUrl, filesToDelete, scmUserid,
//				// encodedPass, svnBranch, comment, outputWriter );
//			}
//
//			if ( filesToAdd != null && filesToAdd.size() > 0 ) {
//				// how to get base ?
//				filesToAdd.forEach( fileToAdd -> {
//					addToSvnIfNotThere( applicationFolder, fileToAdd, applicationSvnUrl, svnCommitClient, comment ) ;
//				} ) ;
//			}
//
//			// SVNDepth.FILES - only immediated folder. SVNDepth.INFINITY will
//			// pick up property files as well
//			SVNCommitInfo commitResults = svnCommitClient.doCommit(
//				commitFileArray, false, comment, null,
//				null, false, false, SVNDepth.INFINITY ) ;
//
//			updateProgress( outputWriter, "\n === commit results ===\n " + commitResults.toString() ) ;
//		}
//
//		// List<String> fileList = Arrays.asList(svnCheckoutFolder.list());
//		updateProgress( outputWriter, "\n ==================== Completed SVN checkin" ) ;
//	}
//
//	private SVNClientManager buildSvnCredentials ( String scmUserid, String encodedPass ) {
//
//		SVNClientManager cm = SVNClientManager.newInstance() ;
//		cm.setAuthenticationManager( SVNWCUtil
//			.createDefaultAuthenticationManager( scmUserid, encryptor.decrypt( encodedPass ) ) ) ;
//		return cm ;
//	}
//
//	public void addToSvnIfNotThere (	File applicationFolder, File newOrUpdatedItem, String targetUrlFolder, SVNCommitClient svnCommitClient,
//										String comment ) {
//		try {
//			logger.info( "Checking for new file: {}", newOrUpdatedItem.getAbsolutePath() ) ;
//
//			// stripping off the parent path
//			String newPath = newOrUpdatedItem.toURI().getPath().substring( applicationFolder.toURI().getPath().length() ) ;
//			SVNURL url = SVNURL.parseURIEncoded( targetUrlFolder + "/" + newPath ) ;
//			logger.info( "url to check: {}", url ) ;
//			svnCommitClient.doImport( newOrUpdatedItem, url, "Agent adding proprs file: " + comment, null, true, true, SVNDepth.INFINITY ) ;
//
//		} catch ( Exception e ) {
//			logger.info( "File already present: {}, \n svn response: \n {}", newOrUpdatedItem.getAbsolutePath(), e.getMessage() ) ;
//		}
//	}
//
//	long				lastFlush		= System.currentTimeMillis() ;
//
//	private int			lineCount		= 0 ;
//	private final int	MAX_LINES		= 5 ;
//	final static String	OUTPUT_FILTER	= "OUTPUT_FILTER" ;
//
//	private void updateProgress ( BufferedWriter outputWriter, String content ) {
//
//		if ( outputWriter != null ) {
//			try {
//				lineCount++ ;
//				// do not bother with updating
//				if ( content.startsWith( OUTPUT_FILTER ) ) {
//					if ( lineCount > MAX_LINES )
//						return ;
//					outputWriter.write( content.substring( OUTPUT_FILTER.length() ) + "\n" ) ;
//					if ( MAX_LINES == lineCount ) {
//						outputWriter.write( "   ===== Remaining items are filtered ===== \n" ) ;
//					}
//				} else {
//					outputWriter.write( content + "\n" ) ;
//				}
//				if ( System.currentTimeMillis() - lastFlush > 5 * 1000 ) {
//					outputWriter.flush() ;
//					lastFlush = System.currentTimeMillis() ;
//				}
//			} catch ( IOException e ) {
//				logger.error( "Failed progress update", e ) ;
//			}
//		}
//
//	}
//
//	private OsCommandRunner osCommandRunner = new OsCommandRunner( 60, 2, "SrcManager" ) ;
//
//	/**
//	 * helper method for executing shell scripts
//	 *
//	 * @param response
//	 * @param resultsAcrossAllThreads
//	 * @param parmList
//	 */
//	public StringBuilder executeShell ( List<String> parmList,
//										BufferedWriter outputWriter ) {
//		StringBuilder results = new StringBuilder() ;
//		File workingDir2 = new File( csapApp.getCsapInstallFolder()
//			.getAbsolutePath() ) ;
//
//		if ( logger.isDebugEnabled() ) {
//			logger.debug( "Doing" + parmList ) ;
//		}
//
//		// results.append("Shell: " + parmList.toString() + "\n");
//		// results.append(osCommandRunner.executeString(parmList, workingDir2,
//		// null, null, null));
//		// Timeout after 60 seconds.
//		results.append( osCommandRunner.executeString( parmList, workingDir2,
//			null, null, 60, 1, outputWriter ) ) ;
//
//		if ( results.indexOf( CsapCore.CONFIG_PARSE_ERROR ) != -1 ) {
//			logger.error( "Found Errors in command execution: " + parmList
//					+ "\n results: " + results ) ;
//		}
//
//		results.append( "\n" ) ;
//
//		logger.debug( "resutls: \n{}", results.toString() ) ;
//
//		return results ;
//	}
//
//	/**
//	 * cvs logins are done up front so that the password does not appear in list
//	 *
//	 * @param cvsUser
//	 * @param cvsPass
//	 * @param workingDir
//	 * @param response
//	 * @return
//	 */
//	public String cvsLogin (	String cvsUser, String encodedPass, File workingDir,
//								HttpSession session ) {
//		String results = null ;
//		List<String> parmList = Arrays.asList( "cvs", "-d", ":pserver:"
//				+ cvsUser + ":" + encryptor.decrypt( encodedPass )
//				+ "@repository.yourcompany.com:2401/opt/cvsroot/Repository",
//			"login" ) ;
//
//		logger.info( "Logging into cvs" ) ;
//		results = osCommandRunner.executeString( parmList, workingDir, null,
//			null, null ) ;
//
//		logger.info( "results from Logging into cvs: " + results ) ;
//
//		return results ;
//
//	}
//
//	public void pullGitUpdate ( String scmUserid, String encodedPass, File sourceLocation,
//								Writer outputWriter )
//			throws Exception {
//
//		String message = CsapApplication.LINE + "\n Updating existing branch on git repository: "
//				+ sourceLocation.getAbsolutePath()
//				+ "\n Optional: use service clean to delete build location to force a new clone on new branch to be created."
//				+ CsapApplication.LINE ;
//
//		logger.info( "{}", message ) ;
//		outputWriter.append( "\n" + message ) ;
//		outputWriter.flush() ;
//
//		FileRepositoryBuilder repositoryBuilder = new FileRepositoryBuilder() ;
//		repositoryBuilder.findGitDir( sourceLocation ) ;
//		File gitLocation = repositoryBuilder.getGitDir() ;
//		ObjectId oldHead = null ;
//		try (Repository repository = repositoryBuilder.setWorkTree( gitLocation ).build()) {
//			oldHead = repository.resolve( "HEAD^{tree}" ) ;
//		}
//		try (Git git = Git.open( gitLocation )) {
//
//			// FetchCommand fetchCommand = git.fetch();
//			PullCommand pullCommand = git.pull() ;
//
//			if ( scmUserid.length() > 0 ) {
//				pullCommand.setCredentialsProvider(
//					new UsernamePasswordCredentialsProvider(
//						scmUserid,
//						encryptor.decrypt( encodedPass ) ) ) ;
//			}
//			pullCommand.setProgressMonitor( gitMonitor( outputWriter ) ) ;
//
//			PullResult result = pullCommand.call() ;
//			logger.info( "merge results: {}", result.getMergeResult() ) ;
//			outputWriter.append( "\n" + result.getMergeResult() + "\n\n Updated files:" ) ;
//			outputWriter.flush() ;
//
//			printGitModifications( gitLocation, outputWriter, repositoryBuilder, oldHead, git ) ;
//			// ResetCommand command = git.reset() ;
//			// command.setP
//			// command.setMode( ResetType.HARD ).call() ;
//		}
//
//		// catch (Exception e) {
//		// logger.error( "Failed to complete pull and diff of repository: {}",
//		// csapApp.getCsapFilteredStackTrace( e ) );
//		// isSuccessful = false;
//		// }
//
//		logger.info( "git sync complete" ) ;
//		outputWriter.append( OutputFileMgr.build_shell_log( "git sync complete" ) ) ;
//		outputWriter.flush() ;
//		return ;
//	}
//
//	private void printGitModifications ( File gitLocation, Writer outputWriter, FileRepositoryBuilder builder, ObjectId oldHead, Git git ) {
//		if ( oldHead == null ) {
//			logger.warn( "unable to determine git pull delta" ) ;
//		} else {
//			try (Repository repository = builder.setWorkTree( gitLocation ).build()) {
//				// The {tree} will return the underlying tree-id instead of
//				// the
//				// commit-id itself!
//				// For a description of what the carets do see e.g.
//				// http://www.paulboxley.com/blog/2011/06/git-caret-and-tilde
//				// This means we are selecting the parent of the parent of
//				// the
//				// parent of the parent of current HEAD and
//				// take the tree-ish of it
//				// ObjectId oldHead = repository.resolve( "HEAD^^^^{tree}"
//				// );
//				ObjectId head = repository.resolve( "HEAD^{tree}" ) ;
//
//				// System.out.println( "Printing diff between tree: " +
//				// oldHead + " and " + head );
//
//				// prepare the two iterators to compute the diff between
//				try (ObjectReader reader = repository.newObjectReader()) {
//					CanonicalTreeParser oldTreeIter = new CanonicalTreeParser() ;
//					oldTreeIter.reset( reader, oldHead ) ;
//					CanonicalTreeParser newTreeIter = new CanonicalTreeParser() ;
//					newTreeIter.reset( reader, head ) ;
//
//					// finally get the list of changed files
//					List<DiffEntry> diffs = git.diff()
//						.setNewTree( newTreeIter )
//						.setOldTree( oldTreeIter )
//						.call() ;
//					for ( DiffEntry entry : diffs ) {
//						outputWriter.append( "\n *** " + entry ) ;
//						// System.out.println( "Entry: " + entry );
//					}
//
//				}
//			} catch ( Exception e ) {
//				logger.warn( "Unable to determine git delta: {}",
//					CSAP.buildCsapStack( e ) ) ;
//			}
//		}
//	}
//
//	// http://www.codeaffine.com/2015/11/30/jgit-clone-repository/
//	// https://github.com/centic9/jgit-cookbook
//	// public void updateGitLocation (
//	// String scmUserid, String encodedPass,
//	// String scmBranch, String svcName, String scmLocation,
//	// BufferedWriter outputWriter )
//	// throws Exception {
//	private void checkOutFolderUsingGit (	String scmUserid, String encodedPass,
//											String branch, String svcName, ServiceInstance serviceInstance,
//											BufferedWriter outputWriter )
//			throws Exception {
//
//		String inputUrl = serviceInstance.getScmLocation() ;
//		// File outputFolder = new File( csapApp.getProcessingDir(),
//		// "/gitTest") ;
//
//		File outputFolder = csapApp.getCsapBuildFolder( svcName ) ;
//
//		if ( ! outputFolder.getParentFile().exists() ) {
//			logger.warn( "checkout folder does not exist: {}", outputFolder.getParentFile().getAbsolutePath() ) ;
//		}
//
//		if ( outputFolder.exists() ) {
//			logger.info( "Git build folder for {} exists: {}", svcName, outputFolder ) ;
//			FileRepositoryBuilder repositoryBuilder = new FileRepositoryBuilder() ;
//			repositoryBuilder.findGitDir( outputFolder ) ;
//
//			if ( repositoryBuilder.getGitDir() != null ) {
//				pullGitUpdate( scmUserid, encodedPass, repositoryBuilder.getGitDir().getParentFile(), outputWriter ) ;
//				return ;
//			}
//			// Optional<File> gitFolder = Files
//			// .list( outputFolder.toPath() )
//			// .map( Path::toFile )
//			// .filter( File::isDirectory )
//			// .filter( file -> file.getName().equals( ".git" ) )
//			// .findFirst();
//			//
//			// if ( gitFolder.isPresent() ) {
//			// pullGitUpdate( scmUserid, encodedPass, outputFolder, outputWriter
//			// );
//			// return;
//			// }
//			outputWriter.append( "\n Deleting previous version of folder: "
//					+ outputFolder.getAbsolutePath() ) ;
//			FileUtils.deleteQuietly( outputFolder ) ;
//
//		}
//
//		String message = "\n Performing git clone: " + inputUrl
//				+ "\n \t\t to destination: " + outputFolder.getAbsolutePath() + "\n\t\t branch: " + branch ;
//
//		logger.info( "scmLocation: {} scmBranch: {} operation: {}", inputUrl, branch, message ) ;
//		outputWriter.append( CsapApplication.LINE + "\n" + message + CsapApplication.LINE ) ;
//		outputWriter.flush() ;
//
//		// updateProgress( outputWriter,
//		// "\n ==================== GIT Checkout of "
//		// + serviceInstance.getScmLocation() + " ==========" );
//
//		CloneCommand cloneCommand = Git.cloneRepository()
//			.setURI( inputUrl )
//			.setDirectory( outputFolder ) ;
//
//		if ( encodedPass != null ) {
//			cloneCommand = cloneCommand.setCredentialsProvider(
//				new UsernamePasswordCredentialsProvider(
//					scmUserid,
//					encryptor.decrypt( encodedPass ) ) ) ;
//		}
//
//		cloneCommand.setProgressMonitor( gitMonitor( outputWriter ) ) ;
//		// http://www.codeaffine.com/2015/12/15/getting-started-with-jgit/
//
//		if ( branch != null && ! branch.equals( GIT_NO_BRANCH ) ) {
//			cloneCommand.setBranch( branch ) ;
//		}
//
//		try (Git gitRepository = cloneCommand.call()) {}
//		;
//
//		// catch (Exception e) {
//		// logger.warn( "Git checkout failed: {}",
//		// csapApp.getCsapFilteredStackTrace( e ) );
//		// outputWriter.append( "\n errors during checkout: " + e.getMessage()
//		// );
//		// }
//		logger.info( "\n git clone completed" ) ;
//		outputWriter.append( OutputFileMgr.build_shell_log( "git clone complete" ) ) ;
//		outputWriter.flush() ;
//
//	}
//
//	private ProgressMonitor gitMonitor ( Writer w ) {
//		return new TextProgressMonitor( w ) {
//
//		} ;
//		// return new ProgressMonitor() {
//		//
//		// @Override
//		// public void update ( int completed ) {
//		// // TODO Auto-generated method stub
//		// logger.info( "items completed: {}", completed );
//		// }
//		//
//		// @Override
//		// public void start ( int totalTasks ) {
//		// // TODO Auto-generated method stub
//		//
//		// }
//		//
//		// @Override
//		// public boolean isCancelled () {
//		// // TODO Auto-generated method stub
//		// return false;
//		// }
//		//
//		// @Override
//		// public void endTask () {
//		// // TODO Auto-generated method stub
//		//
//		// }
//		//
//		// @Override
//		// public void beginTask ( String title, int totalWork ) {
//		// // TODO Auto-generated method stub
//		// logger.info( "progress: {}, items remaining: {}", title, totalWork );
//		//
//		// }
//		// } ;
//	}
//
//	public static final String GIT_NO_BRANCH = "none" ;
//
//	private ISVNEventHandler buildSvnEventHandler ( String desc, BufferedWriter outputWriter ) {
//		lineCount = 0 ;
//		return new ISVNEventHandler() {
//
//			@Override
//			public void checkCancelled ()
//					throws SVNCancelException {
//
//			}
//
//			@Override
//			public void handleEvent ( SVNEvent event, double progress )
//					throws SVNException {
//				// logger.debug("progess: " + progress + " " + event);
//				if ( event.getURL() == null && event.getFile() != null ) {
//					updateProgress( outputWriter, desc + event.getFile().getName() ) ;
//				} else if ( event.getURL() != null ) {
//					updateProgress( outputWriter, desc + event.getURL() ) ;
//				} else {
//					updateProgress( outputWriter, desc + event ) ;
//				}
//			}
//		} ;
//	}
//}
