/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.csap.agent.integration.misc ;

import static org.assertj.core.api.Assertions.assertThat ;

import java.io.File ;
import java.util.List ;

import javax.inject.Inject ;

import org.apache.commons.io.FileUtils ;
import org.csap.agent.Agent_context_loaded ;
import org.csap.agent.CsapBareTest ;
import org.csap.agent.model.Application ;
import org.csap.agent.model.EnvironmentSettings ;
import org.csap.helpers.CsapApplication ;
import org.csap.integations.CsapEncryptionConfiguration ;
import org.eclipse.jgit.api.CloneCommand ;
import org.eclipse.jgit.api.Git ;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider ;
import org.junit.jupiter.api.Assumptions ;
import org.junit.jupiter.api.Disabled ;
import org.junit.jupiter.api.DisplayName ;
import org.junit.jupiter.api.Tag ;
import org.junit.jupiter.api.Test ;
import org.junit.jupiter.api.TestInstance ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.boot.SpringBootConfiguration ;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration ;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration ;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration ;
import org.springframework.boot.context.properties.ConfigurationProperties ;
import org.springframework.boot.test.context.SpringBootTest ;
import org.springframework.context.annotation.Import ;
import org.springframework.test.context.ActiveProfiles ;

/**
 *
 *
 *
 * https://github.com/centic9/jgit-cookbook
 * http://www.codeaffine.com/2015/11/30/jgit-clone-repository/
 *
 *
 * @author someDeveloper
 */

@Tag ( "git" )
@SpringBootTest ( classes = EclipseJGitTests.BareBoot.class )
@ConfigurationProperties ( prefix = "test.junit.git" )
@ActiveProfiles ( {
		CsapBareTest.PROFILE_JUNIT, "EclipseJGitTests"
} )

@TestInstance ( TestInstance.Lifecycle.PER_CLASS )
@DisplayName ( "Git: verifying org.eclipse.jgit " )
class EclipseJGitTests {

	Logger logger = LoggerFactory.getLogger( EclipseJGitTests.class ) ;

	static {

		CsapApplication.initialize( "Test Setup Complete" ) ;

	}

	String user ;
	String password ;
	String url ;

	@SpringBootConfiguration
	@ImportAutoConfiguration ( classes = {
			PropertyPlaceholderAutoConfiguration.class,
			ConfigurationPropertiesAutoConfiguration.class
	} )
	@Import ( CsapEncryptionConfiguration.class )
	public static class BareBoot {
	}

	String baseLocation = "target/agentUnitTests/junit-" + this.getClass( ).getSimpleName( ) + "/" ;

	String SAMPLE_GIT = "https://github.com/csap-platform/csap-packages" ;

	String SAMPLE_GIT_BRANCH = "refs/heads/master" ;

	@Test
	@DisplayName ( "anonymous checkout of public git" )
	public void simple_checkout_with_no_branch ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		File outputFolder = new File( baseLocation + "testGitNoBranch" ) ;

		FileUtils.deleteDirectory( outputFolder ) ;

		String message = "Perform git checkout of " + SAMPLE_GIT + " to destination: "
				+ outputFolder.getAbsolutePath( ) ;
		logger.info( CsapApplication.testHeader( "{}" ), message ) ;

		//
		Git r = Git
				.cloneRepository( )
				.setURI( SAMPLE_GIT )
				.setDirectory( outputFolder )
				.setCredentialsProvider(
						new UsernamePasswordCredentialsProvider( "dummy", "dummy" ) )
				.call( ) ;

		File buildPom = new File( outputFolder, "README.md" ) ;
		assertThat( buildPom )
				.as( "README found" )
				.exists( )
				.isFile( ) ;
		logger.info( "Done" ) ;

	}

	@Test
	@DisplayName ( "Branch checkout of public git" )
	public void simple_checkout_with_branch ( )
		throws Exception {

		File outputFolder = new File( baseLocation + "testGithubWithBranch" ) ;

		FileUtils.deleteDirectory( outputFolder ) ;

		String message = "Perform git checkout of " + SAMPLE_GIT + " to destination: "
				+ outputFolder.getAbsolutePath( ) ;
		logger.info( Agent_context_loaded.TC_HEAD + message ) ;

		Git r = Git
				.cloneRepository( )
				.setURI( SAMPLE_GIT )
				.setDirectory( outputFolder )
				// .setBranchesToClone( singleton( "refs/heads/" ) )
				.setBranch( SAMPLE_GIT_BRANCH )
				.call( ) ;

		File buildPom = new File( outputFolder, "README.md" ) ;
		assertThat( buildPom )
				.as( "Pom file found" )
				.exists( ).isFile( ) ;

	}

	// @Test
	// public void github_checkout_with_no_branch ()
	// throws Exception {
	//
	// File outputFolder = new File( baseLocation + "testGitNoBranch" );
	//
	// FileUtils.deleteDirectory( outputFolder );
	//
	// String targetGitUrl = "https://github.com/peterdnight/playground";
	//
	// String message = "Perform git checkout of " + targetGitUrl + " to
	// destination: "
	// + outputFolder.getAbsolutePath();
	// logger.info( Application_Context.TC_HEAD + message );
	//
	// //
	// Git r = Git
	// .cloneRepository()
	// .setURI( targetGitUrl )
	// .setDirectory( outputFolder )
	// .setCredentialsProvider(
	// new UsernamePasswordCredentialsProvider( "dummy", "dummy" ) )
	// .call();
	//
	// File buildPom = new File( outputFolder + "/README.md" );
	// assertThat( buildPom )
	// .as( "README.md file found" )
	// .exists().isFile();
	// }

	@Inject
	CsapEncryptionConfiguration csapEncrypt ;

	@Test
	public void your_company_checkout_with_authentication ( )
		throws Exception {

		Assumptions.assumeTrue( user != null && password != null && url != null ) ;

		File outputFolder = new File( baseLocation + "testGitCompanyWithPassword" ) ;
		FileUtils.deleteDirectory( outputFolder ) ;

		logger.info( CsapApplication.testHeader( "user: {} checkout: {} dest: {}" ),
				user, getUrl( ), outputFolder.getAbsolutePath( ) ) ;

		Application csapApp = Application.testBuilder( ) ;
		csapApp.setAgentRunHome( System.getProperty( "user.home" ) ) ;
		EnvironmentSettings settings = new EnvironmentSettings( ) ;
		settings.setGitSslVerificationDisabledUrls( List.of( getUrl( ) ) ) ;

		logger.info( "git ssl settings: {}", csapApp.checkGitSslVerificationSettings( settings ) ) ;

		CloneCommand cloneCommand = Git.cloneRepository( )
				.setURI( getUrl( ) )
				.setDirectory( outputFolder )
				.setCredentialsProvider(
						new UsernamePasswordCredentialsProvider(
								getUser( ),
								csapEncrypt.decodeIfPossible( getPassword( ), logger ) ) ) ;

		cloneCommand.call( ) ;

		File readme = new File( outputFolder, "README.md" ) ;

		assertThat( readme ).as( "readme found" ).exists( ) ;

		logger.info( "Verify git checkout was successful: {}", outputFolder.getAbsolutePath( ) ) ;

	}

	@Disabled
	@Test
	public void simple_checkout_with_authentication ( )
		throws Exception {

		File outputFolder = new File( baseLocation + "testGitWithPassword" ) ;
		FileUtils.deleteDirectory( outputFolder ) ;

		String authGitUrl = "https://stash-eng.yourcompany.com/sjc/shared/1/scm/eed/csap.git" ;
		String userid = "someDeveloper" ;
		String pass = "FIXME" ;

		assertThat( pass ).isNotEqualTo( "FIXME" ).as( "Update the password" ) ;

		String message = "Perform git checkout of " + authGitUrl + " to destination: "
				+ outputFolder.getAbsolutePath( ) ;
		logger.info( Agent_context_loaded.TC_HEAD + message ) ;

		CloneCommand cloneCommand = Git.cloneRepository( )
				.setURI( authGitUrl )
				.setDirectory( outputFolder )
				.setCredentialsProvider(
						new UsernamePasswordCredentialsProvider( userid, pass ) ) ;

		cloneCommand.call( ) ;
		File buildPom = new File( outputFolder + "/BootReference/pom.xml" ) ;
		assertThat( buildPom )
				.as( "Pom file found" )
				.exists( ).isFile( ) ;

	}

	@Disabled
	@Test
	public void simple_checkout_with_subFolder ( )
		throws Exception {

		File outputFolder = new File( baseLocation + "testGitWithSubFolder" ) ;
		FileUtils.deleteDirectory( outputFolder ) ;

		String authGitUrl = "https://stash-eng.yourcompany.com/sjc/shared/1/scm/eed/csap.git" ;
		String userid = "someDeveloper" ;
		String pass = "FIXME" ;

		assertThat( "jgit support for folders" ).isEqualTo( "false" ).as( "not supported" ) ;
		assertThat( pass ).isNotEqualTo( "FIXME" ).as( "Update the password" ) ;

		String message = "Perform git checkout of " + authGitUrl + " to destination: "
				+ outputFolder.getAbsolutePath( ) ;
		logger.info( Agent_context_loaded.TC_HEAD + message ) ;

		CloneCommand cloneCommand = Git.cloneRepository( )
				.setURI( authGitUrl )
				.setDirectory( outputFolder )
				.setCredentialsProvider(
						new UsernamePasswordCredentialsProvider( userid, pass ) ) ;

		Git gitRepo = cloneCommand.setNoCheckout( true ).call( ) ;
		gitRepo.checkout( ).setStartPoint( "master" ).call( ) ;
		gitRepo.getRepository( ).close( ) ;

		File buildPom = new File( outputFolder + "/pom.xml" ) ;
		assertThat( buildPom )
				.as( "Pom file found" )
				.exists( ).isFile( ) ;

	}

	public String getUser ( ) {

		return user ;

	}

	public void setUser ( String user ) {

		this.user = user ;

	}

	public String getPassword ( ) {

		return password ;

	}

	public void setPassword ( String password ) {

		this.password = password ;

	}

	public String getUrl ( ) {

		return url ;

	}

	public void setUrl ( String url ) {

		this.url = url ;

	}

}
