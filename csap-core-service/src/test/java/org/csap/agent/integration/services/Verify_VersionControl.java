package org.csap.agent.integration.services ;

import static org.assertj.core.api.Assertions.assertThat ;

import java.io.BufferedWriter ;
import java.io.File ;
import java.io.IOException ;
import java.io.PrintWriter ;
import java.io.StringWriter ;
import java.io.Writer ;
import java.nio.charset.Charset ;
import java.nio.file.Files ;
import java.time.LocalDateTime ;
import java.time.format.DateTimeFormatter ;
import java.util.Arrays ;
import java.util.List ;
import java.util.regex.Pattern ;

import org.apache.commons.io.FileUtils ;
import org.apache.commons.lang3.StringUtils ;
import org.csap.agent.CsapBareTest ;
import org.csap.agent.CsapCore ;
import org.csap.agent.CsapThinNoProfile ;
import org.csap.agent.integrations.VersionControl ;
import org.csap.agent.integrations.VersionControl.ScmProvider ;
import org.csap.agent.linux.OutputFileMgr ;
import org.csap.agent.model.ServiceInstance ;
import org.csap.helpers.CSAP ;
import org.csap.helpers.CsapApplication ;
import org.junit.jupiter.api.Assertions ;
import org.junit.jupiter.api.Assumptions ;
import org.junit.jupiter.api.BeforeAll ;
import org.junit.jupiter.api.Disabled ;
import org.junit.jupiter.api.DisplayName ;
import org.junit.jupiter.api.Nested ;
import org.junit.jupiter.api.Tag ;
import org.junit.jupiter.api.Test ;
import org.junit.jupiter.api.TestInstance ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;

@Tag ( "git" )
@CsapBareTest.ActiveProfiles_JunitOverRides
class Verify_VersionControl extends CsapThinNoProfile {

	Logger logger = LoggerFactory.getLogger( getClass( ) ) ;

	// @Inject
	VersionControl versionControl ;

	// updated by @ConfigurationProperties
	String privateFileValidate = null ;

	String getPrivateFileValidate ( ) {

		return privateFileValidate ;

	}

	public void setPrivateFileValidate ( String privateFileValidate ) {

		this.privateFileValidate = privateFileValidate ;

	}

	String privateRepository = "" ;

	public String getPrivateRepository ( ) {

		return privateRepository ;

	}

	public void setPrivateRepository ( String privateRepository ) {

		this.privateRepository = privateRepository ;

	}

	private String scmUserid = null ;

	public void setScmUserid ( String scmUserid ) {

		this.scmUserid = scmUserid ;

	}

	String scmPass = null ;

	public void setScmPass ( String scmPass ) {

		this.scmPass = scmPass ;

	}

	@BeforeAll
	void beforeAll ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		loadDefaultCsapApplicationDefinition( ) ;

		versionControl = new VersionControl( getApplication( ), getEncryptor( ) ) ;

		ServiceInstance serviceInstance = getApplication( ).findFirstServiceInstanceInLifecycle( "simple-service" ) ;

		if ( serviceInstance == null ) {

			logger.warn( "Missing setup: scmUserid: {}, serviceInstance: {}, getPrivateRepository: {}", scmUserid,
					serviceInstance,
					getPrivateRepository( ) ) ;
			Assumptions.assumeTrue( false ) ;

		}

	}

	// @Test
	// public void verify_git_sourceControlManager_default_branch_checkout ()
	// throws Exception {
	//
	// String csapService_Port = "springmvc-showcase_8061";
	// String scmBranch = sourceControlManager.GIT_NO_BRANCH;
	// String scmLocation =
	// "https://github.com/spring-projects/spring-mvc-showcase.git";
	//
	// File checkOutLocation = new File( getApplication().getBUILD_DIR() +
	// csapService_Port );
	// FileUtils.deleteQuietly( checkOutLocation );
	//
	// OutputFileMgr outputFm = new OutputFileMgr(
	// getApplication().getProcessingDir(), "/"
	// + csapService_Port
	// + "_testDeploy" );
	// String message = "Perform git checkout of " + csapService_Port + " to
	// destination: ";
	// logger.info( InitializeLogging.TC_HEAD + message );
	//
	// CSAP.setLogToInfo( "org.eclipse.jgit" );
	//
	// ServiceInstance serviceInstance = new ServiceInstance();
	// serviceInstance.setScmLocation( scmLocation );
	// serviceInstance.setScm( SourceControlManager.ScmProvider.git.key );
	// serviceInstance.setDefaultBranch( scmBranch );
	// ;
	//
	// sourceControlManager.checkOutFolder(
	// scmUserid, getEncryptor().encrypt( scmPass ),
	// scmBranch, csapService_Port, serviceInstance,
	// outputFm.getBufferedWriter() );
	//
	// File buildPom = new File( getApplication().getBUILD_DIR() + csapService_Port
	// +
	// "/pom.xml" );
	// assertThat( buildPom )
	// .as( "Pom file found" )
	// .exists().isFile();
	//
	// }

	@Disabled
	@Test
	public void verify_git_update_with_authentication_required ( )
		throws Exception {

		String csapService_Port = "BootReference_7111" ;
		File checkOutLocation = getApplication( ).getCsapBuildFolder( csapService_Port ) ;

		OutputFileMgr outputFm = new OutputFileMgr(
				getApplication( ).getCsapWorkingFolder( ), "/"
						+ csapService_Port
						+ "_gitUpdate" ) ;
		Writer w = new PrintWriter( System.err ) ;
		versionControl.pullGitUpdate( scmUserid, getEncryptor( ).encrypt( scmPass ), checkOutLocation, w ) ;
		outputFm.opCompleted( ) ;

	}

	@Nested
	@TestInstance ( TestInstance.Lifecycle.PER_CLASS )
	@DisplayName ( "Public Repositories" )
	class PublicRepositorys {

		@Test
		public void verify_anonymous_checkout_of_agent_at_github ( )
			throws Exception {

			logger.info( CsapApplication.TC_HEAD ) ;

			ServiceInstance serviceInstance = getApplication( ).findFirstServiceInstanceInLifecycle(
					CsapCore.AGENT_NAME ) ;
			serviceInstance.setScmLocation( "https://github.com/csap-platform/csap-core.git" ) ;
			checkOutAndVerifyService( serviceInstance, null ) ;

		}

	}

	@Nested
	@TestInstance ( TestInstance.Lifecycle.PER_CLASS )
	@DisplayName ( "Private Repositories" )
	class PrivateRepositorys {

		@BeforeAll
		void beforeAll ( )
			throws Exception {

			if ( StringUtils.isEmpty( scmUserid )
					|| StringUtils.isEmpty( scmPass )
					|| StringUtils.isEmpty( getPrivateFileValidate( ) )
					|| StringUtils.isEmpty( getPrivateRepository( ) ) ) {

				logger.warn( "Missing setup: scmUserid: {}, getPrivateRepository: {}, getPrivateFileValidate {} ",
						scmUserid,
						getPrivateRepository( ),
						getPrivateFileValidate( ) ) ;

				Assumptions.assumeTrue( false ) ;

			}

			if ( scmPass.equals( "changeme" ) || scmUserid.equals( "changeme" ) ) {

				logger.info( "scm-userid is not set" ) ;
				Assumptions.assumeTrue( false ) ;

			}

		}

		@Test
		public void verify_git_checkout_of_private_repo ( )
			throws Exception {

			logger.info( CsapApplication.testHeader( ) ) ;

			File serviceBuildFolder = checkOutPrivateRepository( ) ;

			logger.info( "Deleting: {}", serviceBuildFolder.getAbsolutePath( ) ) ;
			FileUtils.deleteQuietly( serviceBuildFolder ) ;

		}

		@Disabled
		@Test
		public void verify_git_checkin ( )
			throws Exception {

			logger.info( CsapApplication.testHeader( ) ) ;

			File serviceBuildFolder = checkOutPrivateRepository( ) ;

			// update the folder with new content, files, etc
			File definitionFile = new File( serviceBuildFolder, getPrivateFileValidate( ) ) ;
			//
			checkInFileWithNoChanges(
					VersionControl.ScmProvider.git,
					getPrivateRepository( ),
					scmUserid, getEncryptor( ).encrypt( scmPass ),
					VersionControl.GIT_NO_BRANCH,
					definitionFile ) ;
			//
			checkinFileWithChanges(
					VersionControl.ScmProvider.git,
					getPrivateRepository( ),
					scmUserid, getEncryptor( ).encrypt( scmPass ),
					VersionControl.GIT_NO_BRANCH,
					definitionFile ) ;

			// //
			File fileCreatedByJunit = checkinWithNewFileAdded(
					VersionControl.ScmProvider.git,
					getPrivateRepository( ),
					scmUserid, getEncryptor( ).encrypt( scmPass ),
					VersionControl.GIT_NO_BRANCH,
					definitionFile.getParentFile( ) ) ;

			// // File newFile = new File(sourceFolderOnFileSystem,
			// // "testJan.18-11.42.58");
			deleteGitFile(
					definitionFile,
					getPrivateRepository( ),
					scmUserid, getEncryptor( ).encrypt( scmPass ),
					VersionControl.GIT_NO_BRANCH,
					fileCreatedByJunit ) ;

			logger.info( "Deleting: {}", serviceBuildFolder.getAbsolutePath( ) ) ;
			FileUtils.deleteQuietly( serviceBuildFolder ) ;

		}

		private File checkOutPrivateRepository ( )
			throws IOException ,
			Exception {

			String scmBranch = versionControl.GIT_NO_BRANCH ;

			ServiceInstance serviceInstance = new ServiceInstance( ) ;
			serviceInstance.setScmLocation( getPrivateRepository( ) ) ;
			serviceInstance.setScm( VersionControl.ScmProvider.git.key ) ;
			serviceInstance.setName( "privaterepotest" ) ;
			serviceInstance.setPort( "6060" ) ;

			File serviceBuildFolder = getApplication( ).getCsapBuildFolder( serviceInstance.getServiceName_Port( ) ) ;
			File validationFile = new File( serviceBuildFolder, serviceInstance.getScmBuildLocation( )
					+ getPrivateFileValidate( ) ) ;

			boolean forceCloneEveryTime = false ;

			if ( forceCloneEveryTime ) {

				FileUtils.deleteQuietly( serviceBuildFolder ) ;

				if ( serviceBuildFolder.exists( ) ) {

					assertThat( true ).as( "Unable to delete: " + serviceBuildFolder.getCanonicalPath( ) ).isFalse( ) ;

				}

			}

			var resultOutputFileManager = new OutputFileMgr(
					getApplication( ).getCsapWorkingFolder( ), "/"
							+ serviceInstance.getServiceName_Port( )
							+ "_testclone" ) ;

			logger.info( "Perform git checkout of {} to destination: {}",
					serviceInstance.getServiceName_Port( ),
					serviceBuildFolder.getAbsolutePath( ) ) ;

			CSAP.setLogToInfo( "org.eclipse.jgit" ) ;

			versionControl.checkOutFolder(
					scmUserid, getEncryptor( ).encrypt( scmPass ),
					scmBranch,
					serviceInstance.getServiceName_Port( ),
					serviceInstance,
					resultOutputFileManager.getBufferedWriter( ) ) ;

			resultOutputFileManager.close( ) ;

			var gitResults = FileUtils.readFileToString( resultOutputFileManager.getOutputFile( ) ) ;
			logger.info( "gitResults: {}", gitResults ) ;

			logger.info( "Listing for: {}",
					validationFile.getParentFile( ).getAbsolutePath( ) ) ;

			assertThat( validationFile )
					.as( "validationFile found" )
					.exists( ).isFile( ) ;

			Files.list( validationFile.getParentFile( ).toPath( ) )
					.forEach( System.out::println ) ;
			return serviceBuildFolder ;

		}

		@Test
		public void verify_git_service_checkout ( )
			throws Exception {

			logger.info( CsapApplication.testHeader( ) ) ;

			ServiceInstance serviceInstance = getApplication( ).findFirstServiceInstanceInLifecycle(
					"simple-service" ) ;
			checkOutAndVerifyService( serviceInstance, scmPass ) ;

		}

		@Test
		public void verify_git_service_checkout_default_branch ( )
			throws Exception {

			logger.info( CsapApplication.testHeader( ) ) ;

			ServiceInstance sourceInstance = getApplication( ).findFirstServiceInstanceInLifecycle( "simple-service" ) ;

			ServiceInstance serviceInstance = new ServiceInstance( ) ;
			serviceInstance.setName( "copy-for-junit" ) ;
			serviceInstance.setScmLocation( sourceInstance.getScmLocation( ) ) ;
			serviceInstance.setScmBuildLocation( sourceInstance.getScmBuildLocation( ) ) ;
			serviceInstance.setScm( sourceInstance.getScm( ) ) ;

			File serviceBuildFolder = getApplication( ).getCsapBuildFolder( serviceInstance.getServiceName_Port( ) ) ;
			File buildPom = new File( serviceBuildFolder, serviceInstance.getScmBuildLocation( ) + "/pom.xml" ) ;

			FileUtils.deleteQuietly( serviceBuildFolder ) ;

			var resultOutputFileManager = new OutputFileMgr(
					getApplication( ).getCsapWorkingFolder( ),
					"/" + serviceInstance.getServiceName_Port( ) + "_testDeploy" ) ;

			logger.info( "Perform git checkout of {} to destination: {}",
					serviceInstance.getServiceName_Port( ),
					serviceBuildFolder.getAbsolutePath( ) ) ;

			versionControl.checkOutFolder(
					scmUserid, getEncryptor( ).encrypt( scmPass ),
					VersionControl.GIT_NO_BRANCH, serviceInstance.getServiceName_Port( ), serviceInstance,
					resultOutputFileManager.getBufferedWriter( ) ) ;

			resultOutputFileManager.close( ) ;

			var gitResults = FileUtils.readFileToString( resultOutputFileManager.getOutputFile( ) ) ;
			logger.info( "gitResults: {}", gitResults ) ;

			assertThat( buildPom )
					.as( "Pom file found" )
					.exists( ).isFile( ) ;

			logger.info( "Deleting: {}", serviceBuildFolder.getAbsolutePath( ) ) ;
			Files.list( serviceBuildFolder.toPath( ) )
					.forEach( System.out::println ) ;

			FileUtils.deleteDirectory( serviceBuildFolder ) ;

		}

		@Disabled
		@Test
		public void verify_git_checkin_of_definition ( )
			throws Exception {

			logger.info( CsapApplication.testHeader( ) ) ;

			// String definitionUrl =
			// "https://bitbucket.yourcompany.com/bitbucket/scm/csap/agent.git" ;
			String definitionUrl = getPrivateRepository( ) ;

			String svnBranch = VersionControl.GIT_NO_BRANCH ;
			String svcName = "git_junit_Definition" ;

			File sourceFolderOnFileSystem = getApplication( ).getCsapBuildFolder( svcName ) ;

			// FileUtils.deleteDirectory( sourceFolderOnFileSystem ); // comment out
			// for speed

			// InstanceConfig instanceConfig=
			// getApplication().getServiceInstance(CsapCore.AGENT_ID);
			ServiceInstance serviceInstance = new ServiceInstance( ) ;
			serviceInstance.setScmLocation( definitionUrl ) ;
			serviceInstance.setScm( VersionControl.ScmProvider.git.key ) ;

			String encryptedPass = getEncryptor( ).encrypt( scmPass ) ; // immediately
			// String definitionLocation =
			// "src/test/java/org/csap/test/data/gitDefinition/Application.json" ;
			String definitionLocation = "/Application.json" ;

			checkOutFolder( scmUserid, encryptedPass, svnBranch, svcName,
					serviceInstance, sourceFolderOnFileSystem, definitionLocation ) ;

			// update the folder with new content, files, etc
			File definitionFile = new File( sourceFolderOnFileSystem, definitionLocation ) ;
			//
			checkInFileWithNoChanges( VersionControl.ScmProvider.git, definitionUrl, scmUserid, encryptedPass,
					svnBranch,
					definitionFile ) ;
			//
			checkinFileWithChanges( VersionControl.ScmProvider.git, definitionUrl, scmUserid, encryptedPass, svnBranch,
					definitionFile ) ;
			//
			File newFile = checkinWithNewFileAdded(
					ScmProvider.git,
					definitionUrl, scmUserid, encryptedPass, svnBranch,
					definitionFile.getParentFile( ) ) ;

			deleteGitFile( definitionFile, definitionUrl, scmUserid, encryptedPass, svnBranch, newFile ) ;

		}

		private void deleteGitFile (
										File definitionFile ,
										String definitionUrl ,
										String scmUserid ,
										String encryptedPass ,
										String svnBranch ,
										File deleteFile )
			throws IOException ,
			Exception {

			StringWriter sw = new StringWriter( ) ;
			BufferedWriter stringWriter = new BufferedWriter( sw ) ;

			FileUtils.deleteQuietly( deleteFile ) ;

			versionControl.checkInFolder(
					ScmProvider.git,
					definitionUrl, definitionFile, scmUserid, encryptedPass,
					svnBranch, "Deleteing test file", stringWriter ) ;

			stringWriter.flush( ) ;
			String ciResults = sw.toString( ) ;
			logger.info( "output messages: {}", ciResults ) ;

			assertThat( ciResults )
					.as( "git messages" )
					.contains(
							"DiffEntry[DELETE" ) ;

		}

		private void checkOutFolder (
										String scmUserid ,
										String encryptedPass ,
										String svnBranch ,
										String svcName ,
										ServiceInstance instanceConfig ,
										File sourceFolderOnFileSystem ,
										String definitionLocation )

			throws IOException ,
			Exception {

			StringWriter sw = new StringWriter( ) ;
			BufferedWriter stringWriter = new BufferedWriter( sw ) ;

			versionControl.checkOutFolder(
					scmUserid, encryptedPass, svnBranch, svcName,
					instanceConfig, stringWriter ) ;

			assertThat( sourceFolderOnFileSystem )
					.as( "checkout folder created" )
					.exists( ) ;

			assertThat( new File( sourceFolderOnFileSystem, definitionLocation ) )
					.as( "Application created" )
					.exists( ) ;

			stringWriter.flush( ) ;
			String coResults = sw.toString( ) ;
			logger.debug( "output messages: {}", coResults ) ;

			if ( instanceConfig.isGit( ) ) {

				assertThat( coResults )
						.as( "git messages" )
						.containsPattern(
								".*git.*complete.*" ) ;

				Assertions.assertTrue( coResults.contains( "git clone complete" ) || coResults.contains(
						"git sync complete" ) ) ;

			} else {

				assertThat( coResults )
						.as( "svn messages" )
						.contains(
								"Checking out: https://svn.yourcompany.com/svn/csap/trunk/core/Agent/src/test/java/org/csap/test/data/fullSample" ) ;

			}

		}

		private void checkInFileWithNoChanges (
												ScmProvider scmType ,
												String definitionUrl ,
												String scmUserid ,
												String encryptedPass ,
												String svnBranch ,
												File definitionFile )
			throws IOException ,
			Exception {

			StringWriter sw = new StringWriter( ) ;
			BufferedWriter stringWriter = new BufferedWriter( sw ) ;

			logger.info( "Checkin - no changes." ) ;
			versionControl.checkInFolder(
					scmType,
					definitionUrl, definitionFile, scmUserid, encryptedPass,
					svnBranch, "No changes test comment", stringWriter ) ;

			stringWriter.flush( ) ;
			String ciResults = sw.toString( ) ;
			logger.info( "output messages: {}", ciResults ) ;

			if ( scmType == ScmProvider.svn ) {

				assertThat( ciResults )
						.as( "svn messages" )
						.contains( "EMPTY COMMIT" ) ;

			} else {

				assertThat( ciResults )
						.as( "git messages" )
						.contains( "Completed GIT checkin" ) ; // UP_TO_DATE

			}

		}

		private void checkinFileWithChanges (
												ScmProvider scmType ,
												String definitionUrl ,
												String scmUserid ,
												String encryptedPass ,
												String branch ,
												File definitionFile )

			throws IOException ,
			Exception {

			List<String> lines = Files.readAllLines( definitionFile.toPath( ), Charset.forName( "UTF-8" ) ) ;
			String now = LocalDateTime.now( ).format( DateTimeFormatter.ofPattern( "MMM.d-HH.mm.ss" ) ) ;
			lines.set( 0, "{ \"junit-csap-core-git-integration\": \"" + now + "\", " ) ;

			logger.info( "updating line 1: {}", lines.get( 0 ) ) ;

			try {

				Files.write( definitionFile.toPath( ), lines, Charset.forName( "UTF-8" ) ) ;

			} catch ( IOException ex ) {

				logger.error( "Failed creating def file", ex ) ;

			}

			StringWriter sw = new StringWriter( ) ;
			BufferedWriter stringWriter = new BufferedWriter( sw ) ;

			String comment = "Junit git update test" ;

			versionControl.checkInFolder(
					scmType,
					definitionUrl, definitionFile,
					scmUserid, encryptedPass,
					branch,
					comment,
					stringWriter ) ;

			stringWriter.flush( ) ;
			String checkInResponseText = sw.toString( ) ;
			logger.info( "output messages: {}", checkInResponseText ) ;

			if ( scmType == ScmProvider.svn ) {

				assertThat( checkInResponseText )
						.as( "svn messages" )
						.contains(
								comment ) ;

			} else {

				Pattern searchWithNewLinesPattern = Pattern.compile(
						".*"
								+ Pattern.quote( "DiffEntry[MODIFY" )
								+ ".*"
								+ definitionFile.getName( )
								+ ".*",
						Pattern.DOTALL ) ;

				assertThat( searchWithNewLinesPattern.matcher( checkInResponseText ).find( ) )
						.as( "git messages" )
						.isTrue( ) ;

			}

		}

		// private void deleteSvnFile (
		// String definitionUrl, String scmUserid, String encryptedPass,
		// String svnBranch, File deleteFile )
		// throws IOException, Exception {
		//
		// StringWriter sw = new StringWriter() ;
		// BufferedWriter stringWriter = new BufferedWriter( sw ) ;
		//
		// List<File> filesToDelete = new ArrayList<>() ;
		//
		// filesToDelete.add( deleteFile ) ;
		//
		// versionControl.doSvnDelete(
		// definitionUrl, filesToDelete,
		// scmUserid, encryptedPass,
		// svnBranch, "Deleting files for test", stringWriter ) ;
		//
		// stringWriter.flush() ;
		// String ciResults = sw.toString() ;
		// logger.info( "output messages: {}", ciResults ) ;
		//
		// assertThat( ciResults )
		// .as( "svn messages" )
		// .contains(
		// deleteFile.getName(), "commit_completed" ) ;
		//
		// }

		private File checkinWithNewFileAdded (
												ScmProvider scmType ,
												String definitionUrl ,
												String scmUserid ,
												String encryptedPass ,
												String svnBranch ,
												File definitionFolder )

			throws Exception {

			String now = LocalDateTime.now( ).format( DateTimeFormatter.ofPattern( "MMM.d-HH.mm.ss" ) ) ;

			File newDefinitionFile = new File( definitionFolder, "test" + now + ".json" ) ;

			logger.info( "creating new file: {}", newDefinitionFile ) ;
			List<String> lines = Arrays.asList(
					"TestNewFile",
					now ) ;

			File delFolder = new File( definitionFolder, "test1" ) ;

			if ( delFolder.exists( ) ) {

				// delete 1st file found
				File delFile = new File( delFolder, delFolder.list( )[0] ) ;
				FileUtils.deleteQuietly( delFile ) ;

				// filesToDelete.add( delFile ) ;
				// note delete folders work as well
			} else {

				logger.info( "Need to add test1 folder for testing" ) ;

			}

			File newPropFile = new File(
					definitionFolder,
					"test1/testProp" + now + "/test" + now + ".properties" ) ;

			newPropFile.getParentFile( ).mkdirs( ) ;
			// filesToAdd.add( newPropFile ) ;

			try {

				Files.write( newDefinitionFile.toPath( ), lines, Charset.forName( "UTF-8" ) ) ;
				Files.write( newPropFile.toPath( ), lines, Charset.forName( "UTF-8" ) ) ;

			} catch ( IOException ex ) {

				logger.error( "Failed creating def file", ex ) ;

			}

			StringWriter sw = new StringWriter( ) ;
			BufferedWriter stringWriter = new BufferedWriter( sw ) ;

			versionControl.checkInFolder(
					scmType,
					definitionUrl,
					newDefinitionFile,
					scmUserid, encryptedPass,
					svnBranch,
					"Adding new file for test",
					stringWriter ) ;

			stringWriter.flush( ) ;

			String checkinResultText = sw.toString( ) ;
			logger.info( "checkinResultText: {}", checkinResultText ) ;

			if ( scmType == ScmProvider.svn ) {

				assertThat( checkinResultText )
						.as( "svn messages" )
						.contains(
								"updating: " + newDefinitionFile.getName( ),
								"updating: " + newPropFile.getName( ) ) ;

			} else {

				assertThat( checkinResultText )
						.as( "git messages" )
						.contains( "DiffEntry[ADD" ) ; // DiffEntry[DELETE

			}

			return newDefinitionFile ;

		}

	}

	private void checkOutAndVerifyService ( ServiceInstance serviceInstance , String password )
		throws Exception {

		File serviceBuildFolder = getApplication( ).getCsapBuildFolder( serviceInstance.getServiceName_Port( ) ) ;
		File buildPom = new File( serviceBuildFolder, serviceInstance.getScmBuildLocation( ) + "/pom.xml" ) ;

		FileUtils.deleteDirectory( serviceBuildFolder ) ;

		var resultOutputFileManager = new OutputFileMgr(
				getApplication( ).getCsapWorkingFolder( ), "/"
						+ serviceInstance.getServiceName_Port( )
						+ "_testDeploy" ) ;

		logger.info( "Perform git checkout of {} to destination: {}",
				serviceInstance.getServiceName_Port( ),
				serviceBuildFolder.getAbsolutePath( ) ) ;

		versionControl.checkOutFolder(
				scmUserid, getEncryptor( ).encrypt( password ),
				serviceInstance.getDefaultBranch( ),
				serviceInstance.getServiceName_Port( ),
				serviceInstance,
				resultOutputFileManager.getBufferedWriter( ) ) ;

		resultOutputFileManager.close( ) ;

		var gitResults = FileUtils.readFileToString( resultOutputFileManager.getOutputFile( ) ) ;
		logger.info( "gitResults: {}", gitResults ) ;

		assertThat( buildPom )
				.as( "Pom file found" )
				.exists( ).isFile( ) ;

		logger.info( "Found: {}; performing cleanup - deleting: {}", buildPom.getAbsolutePath( ), serviceBuildFolder
				.getAbsolutePath( ) ) ;
		Files.list( serviceBuildFolder.toPath( ) )
				.forEach( System.out::println ) ;

		FileUtils.deleteDirectory( serviceBuildFolder ) ;

	}

	@Disabled // requires a legit id for checkout
	@Test
	public void verify_svn_checkout_of_service ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		String rawPass = scmPass ;

		String svnBranch = "trunk" ;
		String svcName = "TestCheckoutFolder" ;

		File svnCheckoutFolder = getApplication( ).getCsapBuildFolder( svcName ) ;
		FileUtils.deleteDirectory( svnCheckoutFolder ) ;
		// InstanceConfig instanceConfig=
		// getApplication().getServiceInstance(CsapCore.AGENT_ID);
		ServiceInstance instanceConfig = new ServiceInstance( ) ;
		instanceConfig.setScmLocation(
				"https://svn.yourcompany.com/svn/csap/trunk/public/javaProjects/BootReference" ) ;
		instanceConfig.setScm( VersionControl.ScmProvider.svn.key ) ;

		String encryptedPass = getEncryptor( ).encrypt( rawPass ) ; // immediately

		// BufferedWriter outputWriter = new BufferedWriter(new
		// OutputStreamWriter(System.out));
		// OutputFileMgr outputManager = new OutputFileMgr(
		// getApplication().getProcessingDir(),
		// "unitTestFor" + this.getClass().getSimpleName() );
		StringWriter sw = new StringWriter( ) ;
		BufferedWriter stringWriter = new BufferedWriter( sw ) ;

		versionControl.checkOutFolder( scmUserid, encryptedPass, svnBranch, svcName,
				instanceConfig, stringWriter ) ;

		assertThat( svnCheckoutFolder )
				.as( "checkout folder created" )
				.exists( ) ;

		Assertions.assertTrue( svnCheckoutFolder.exists( ) ) ;
		// FileUtils.deleteDirectory(svnCheckoutFolder);
		stringWriter.flush( ) ;
		String results = sw.toString( ) ;
		logger.info( "output messages: {}", results ) ;

		assertThat( results )
				.as( "svn messages" )
				.contains(
						"Checking out: https://svn.yourcompany.com/svn/csap/trunk/public/javaProjects/BootReference" ) ;

	}

	// @Disabled // requires a legit id for checkout
	// @Test
	// public void verify_svn_checkin_of_definition ()
	// throws Exception {
	//
	// logger.info( InitializeLogging.TC_HEAD + "Scenario: SVN Definition" );
	//
	// String rawPass = scmPass;
	// String definitionUrl =
	// "https://svn.yourcompany.com/svn/csap/trunk/core/Agent/src/test/java/org/csap/test/data/fullSample";
	// assertThat( rawPass ).isNotEqualTo( "FIXME" ).as( "Update the password"
	// );
	//
	// String svnBranch = "trunk";
	// String svcName = "Svn_junit_Definition";
	//
	// File sourceFolderOnFileSystem = new File( Application.BUILD_DIR + svcName
	// );
	// FileUtils.deleteDirectory( sourceFolderOnFileSystem );
	// // InstanceConfig instanceConfig=
	// // getApplication().getServiceInstance(CsapCore.AGENT_ID);
	// ServiceInstance instanceConfig = new ServiceInstance();
	// instanceConfig.setScmLocation( definitionUrl );
	// instanceConfig.setScm( SourceControlManager.ScmProvider.svn.key );
	//
	// String encryptedPass = getEncryptor().encrypt( rawPass ); // immediately
	//
	// definitionCheckOut( scmUserid, encryptedPass, svnBranch, svcName,
	// instanceConfig, sourceFolderOnFileSystem, "Application.json" );
	//
	// // update the folder with new content, files, etc
	// File definitionFile = new File( sourceFolderOnFileSystem,
	// "Application.json" );
	//
	// definitionCheckInPlain( SourceControlManager.ScmProvider.svn,
	// definitionUrl, scmUserid, encryptedPass, svnBranch, definitionFile );
	//
	// definitionCheckInChanges( SourceControlManager.ScmProvider.svn,
	// definitionUrl, scmUserid, encryptedPass, svnBranch,
	// definitionFile );
	//
	// File newFile = verify_application_update_with_adds_and_deletes(
	// ScmProvider.svn,
	// definitionUrl, scmUserid, encryptedPass, svnBranch,
	// definitionFile.getParentFile() );
	// // File newFile = new File(sourceFolderOnFileSystem,
	// // "testJan.18-11.42.58");
	// deleteSvnFile( definitionUrl, scmUserid, encryptedPass, svnBranch,
	// newFile );
	//
	// }

}
