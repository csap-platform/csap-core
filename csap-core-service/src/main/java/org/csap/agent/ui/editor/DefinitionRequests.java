package org.csap.agent.ui.editor ;

import static org.csap.agent.integrations.VersionControl.CONFIG_SUFFIX_FOR_UPDATE ;
import static org.csap.agent.model.Application.VALIDATION_ERRORS ;

import java.io.BufferedWriter ;
import java.io.File ;
import java.io.FileFilter ;
import java.io.IOException ;
import java.util.ArrayList ;
import java.util.Arrays ;
import java.util.Collections ;
import java.util.List ;
import java.util.Set ;
import java.util.concurrent.atomic.AtomicInteger ;
import java.util.concurrent.locks.Lock ;
import java.util.concurrent.locks.ReentrantLock ;

import javax.inject.Inject ;
import javax.servlet.http.HttpServletRequest ;

import org.apache.commons.io.FileUtils ;
import org.apache.commons.lang3.StringUtils ;
import org.csap.agent.CsapCore ;
import org.csap.agent.CsapCoreService ;
import org.csap.agent.container.DockerJson ;
import org.csap.agent.integrations.CsapEvents ;
import org.csap.agent.integrations.VersionControl ;
import org.csap.agent.integrations.VersionControl.ScmProvider ;
import org.csap.agent.linux.OutputFileMgr ;
import org.csap.agent.linux.TransferManager ;
import org.csap.agent.model.Application ;
import org.csap.agent.model.DefinitionConstants ;
import org.csap.agent.model.EnvironmentSettings ;
import org.csap.agent.model.ProcessRuntime ;
import org.csap.agent.model.Project ;
import org.csap.agent.model.ProjectLoader ;
import org.csap.agent.model.ServiceAttributes ;
import org.csap.agent.model.ServiceInstance ;
import org.csap.agent.stats.MetricCategory ;
import org.csap.agent.stats.OsProcessEnum ;
import org.csap.agent.stats.OsSharedEnum ;
import org.csap.agent.stats.service.JmxCommonEnum ;
import org.csap.agent.ui.windows.CorePortals ;
import org.csap.docs.CsapDoc ;
import org.csap.helpers.CSAP ;
import org.csap.helpers.CsapApplication ;
import org.csap.integations.CsapEncryptionConfiguration ;
import org.csap.security.CsapUser ;
import org.eclipse.jgit.api.errors.GitAPIException ;
import org.eclipse.jgit.api.errors.TransportException ;
import org.jasypt.encryption.pbe.StandardPBEStringEncryptor ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.beans.factory.annotation.Autowired ;
import org.springframework.context.event.ContextRefreshedEvent ;
import org.springframework.context.event.EventListener ;
import org.springframework.core.annotation.Order ;
import org.springframework.core.io.ByteArrayResource ;
import org.springframework.http.MediaType ;
import org.springframework.mail.javamail.JavaMailSender ;
import org.springframework.mail.javamail.MimeMessageHelper ;
import org.springframework.web.bind.annotation.GetMapping ;
import org.springframework.web.bind.annotation.PostMapping ;
import org.springframework.web.bind.annotation.RequestMapping ;
import org.springframework.web.bind.annotation.RequestMethod ;
import org.springframework.web.bind.annotation.RequestParam ;
import org.springframework.web.bind.annotation.RestController ;
import org.thymeleaf.context.Context ;
import org.thymeleaf.spring5.SpringTemplateEngine ;

import com.fasterxml.jackson.databind.JsonNode ;
import com.fasterxml.jackson.databind.ObjectMapper ;
import com.fasterxml.jackson.databind.node.ArrayNode ;
import com.fasterxml.jackson.databind.node.ObjectNode ;

/**
 *
 * UI controller for managing cluster definition files
 *
 * @author someDeveloper
 *
 *
 * @see <a href=
 *      "http://static.springsource.org/spring/docs/current/spring-framework-reference/html/mvc.html">
 *      SpringMvc Docs </a>
 *
 * @see SpringContext_agentSvcServlet
 *
 *
 */
@RestController
@RequestMapping ( CsapCoreService.DEFINITION_URL )
@CsapDoc ( title = "Application Definition Operations" , notes = {
		"Update, Reload and similar operations to manage the running application",
		"<a class='csap-link' target='_blank' href='https://github.com/csap-platform/csap-core/wiki'>learn more</a>",
		"<img class='csapDocImage' src='CSAP_BASE/images/portals.png' />"
} )
public class DefinitionRequests {

	private static final String GIT_SOURCE = "git-source" ;

	public static final String DEFINITION_FILES = "files" ;

	final Logger logger = LoggerFactory.getLogger( getClass( ) ) ;

	public DefinitionRequests (
			Application csapApp,
			CsapEvents csapEventClient,
			VersionControl sourceControlManager,
			StandardPBEStringEncryptor encryptor,
			CsapEncryptionConfiguration csapEncProps,
			CorePortals corePortals ) {

		this.csapApp = csapApp ;
		this.corePortals = corePortals ;
		this.csapEventClient = csapEventClient ;
		this.sourceControlManager = sourceControlManager ;
		this.encryptor = encryptor ;
		this.csapEncProps = csapEncProps ;

	}

	@EventListener ( {
			ContextRefreshedEvent.class
	} )
	@Order ( CsapCore.CSAP_UI_LOAD_ORDER )
	public void initialize ( ) {

		this.servicesResources = new ServiceResources(
				csapApp.getRootModelBuildLocation( ),
				csapApp.getCsapInstallFolder( ),
				jsonMapper ) ;

		this.pendingDefinitionManager = new PendingDefinitionManager( servicesResources, jsonMapper ) ;

	}

	@Autowired ( required = false )
	JavaMailSender csapMailSender ;
	// standalone csap may optionally configure notifications.

	CorePortals corePortals ;
	Application csapApp ;

	PendingDefinitionManager pendingDefinitionManager ;
	ServiceResources servicesResources ;

	CsapEvents csapEventClient ;

	VersionControl sourceControlManager ;

	StandardPBEStringEncryptor encryptor ;
	CsapEncryptionConfiguration csapEncProps ;

	ObjectMapper jsonMapper = new ObjectMapper( ) ;

	@RequestMapping ( value = {
			CsapCoreService.ENCODE_URL, CsapCoreService.DECODE_URL
	} , produces = MediaType.APPLICATION_JSON_VALUE )
	public ObjectNode getSecureProperties (
											@RequestParam ( "propertyFileContents" ) String propertyFileContents ,
											@RequestParam ( value = "customToken" , defaultValue = "" ) String customToken ,
											HttpServletRequest request ) {

		String command = ( new File( request.getRequestURI( ) ) ).getName( ) ;
		logger.debug( "propertyFileContents: {}", propertyFileContents ) ;

		csapEventClient.publishUserEvent( CsapEvents.CSAP_OS_CATEGORY + "/getSecureProperties",
				CsapUser.currentUsersID( ),
				command, "Token Length" + customToken.length( ) ) ;

		ObjectNode resultNode = jsonMapper.createObjectNode( ) ;
		ArrayNode codeLines = resultNode.putArray( "converted" ) ;
		ArrayNode ignoredLines = resultNode.putArray( "ignored" ) ;

		StringBuilder updatedContent = new StringBuilder( ) ;

		String[] lines = propertyFileContents.split( "\\r?\\n" ) ;
		// String[] lines = propertyFileContents.split( "\\r?\\n" );

		boolean isYaml = false ;

		if ( lines[0].contains( "yaml" ) ) {

			isYaml = true ;

		}

		StandardPBEStringEncryptor csapEncrypter = encryptor ;

		if ( customToken.length( ) > 0 ) {

			csapEncrypter = new StandardPBEStringEncryptor( ) ;
			csapEncrypter.setAlgorithm( csapEncProps.getAlgorithm( ) ) ;
			csapEncrypter.setPassword( customToken ) ;

		}

		logger.info( "command: {}, isYaml: {} Token Length: {}, lines of input: {}",
				command, isYaml, customToken.length( ), lines.length ) ;

		for ( int i = 0; i < lines.length; i++ ) {

			String currentLine = lines[i] ;

			logger.info( "line index {} to be transformed: {}", i, currentLine ) ;

			// empty lines
			if ( currentLine.trim( ).length( ) == 0
					|| currentLine.trim( ).startsWith( "#" )
					|| ( isYaml && currentLine.trim( ).split( " " ).length <= 1 )
					|| ( isYaml && ! currentLine.contains( ":" ) ) ) {

				ignoredLines.add( currentLine ) ;
				updatedContent.append( currentLine ) ;
				updatedContent.append( "\n" ) ;
				continue ;

			}

			if ( command.equalsIgnoreCase( "decode" ) ) {

				codeLines.add( decodeLine( isYaml, currentLine, csapEncrypter, updatedContent ) ) ;

			} else {

				codeLines.add( encodeLine( isYaml, currentLine, csapEncrypter, updatedContent ) ) ;

			}

		}

		resultNode.put( "updatedContent", updatedContent.toString( ) ) ;

		return resultNode ;

	}

	private ObjectNode decodeLine (
									boolean isYaml ,
									String line ,
									StandardPBEStringEncryptor csapEncrypter ,
									StringBuilder updatedContent ) {

		ObjectNode decodeResults = jsonMapper.createObjectNode( ) ;
		String result = "decoding did not work, verify input is correct, and algorithm and token are consistent" ;

		if ( isYaml ) {

			// YAML files
			String propKey = line.substring( 0, line.indexOf( ":" ) ).trim( ) ;
			String propValue = line.substring( line.indexOf( ":" ) + 1 ).trim( ) ;
			decodeResults.put( "key", propKey ) ;
			// encrypting the value
			decodeResults.put( "original", propValue ) ;
			String encryptVal = propValue ;

			try {

				result = csapEncrypter.decrypt( encryptVal ) ;

			} catch ( Exception e ) {

				logger.debug( "Failed to decrypt", e ) ;

			}

			decodeResults.put( "encrypted", encryptVal ) ;

			updatedContent.append( line.substring( 0, line.indexOf( ":" ) + 2 ) + result ) ;
			updatedContent.append( "\n" ) ;

		} else if ( isPropertyFile( line ) ) {

			String propKey = line.substring( 0, line.indexOf( "=" ) ) ;
			String propValue = line.substring( line.indexOf( "=" ) + 1 ) ;
			decodeResults.put( "key", propKey ) ;
			// encrypting the value
			decodeResults.put( "original", propValue ) ;

			if ( propValue.startsWith( "ENC(" ) ) {

				propValue = propValue.substring( 4, propValue.length( ) - 1 ) ;

				try {

					result = csapEncrypter.decrypt( propValue ) ;

				} catch ( Exception e ) {

					logger.info( "Failed to decrypt:{}", CSAP.buildCsapStack( e ) ) ;

				}

			} else {

				try {

					result = csapEncrypter.decrypt( propValue ) ;

				} catch ( Exception e ) {

					logger.info( "Failed to decrypt:{}", CSAP.buildCsapStack( e ) ) ;

				}

			}

			updatedContent.append( propKey + "=" + result ) ;
			updatedContent.append( "\n" ) ;

		} else {

			decodeResults.put( "key", "none" ) ;
			// encrypting the value
			decodeResults.put( "original", line ) ;

			try {

				result = csapEncrypter.decrypt( line ) ;

			} catch ( Exception e ) {

				logger.debug( "Failed to decrypt", e ) ;

			}

		}

		decodeResults.put( "decrypted", result ) ;

		return decodeResults ;

	}

	private ObjectNode encodeLine (
									boolean isYaml ,
									String line ,
									StandardPBEStringEncryptor csapEncrypter ,
									StringBuilder updatedContent ) {

		ObjectNode encodeResults = jsonMapper.createObjectNode( ) ;

		if ( isYaml ) {

			// YAML files
			String propKey = line.substring( 0, line.indexOf( ":" ) ).trim( ) ;
			String propValue = line.substring( line.indexOf( ":" ) + 1 ).trim( ) ;
			encodeResults.put( "key", propKey ) ;
			// encrypting the value
			encodeResults.put( "original", propValue ) ;
			String encryptVal = propValue ;

			if ( ! propValue.startsWith( "ENC(" ) ) {

				encryptVal = csapEncrypter.encrypt( propValue ) ;

			}

			encodeResults.put( "encrypted", encryptVal ) ;

			updatedContent.append( line.substring( 0, line.indexOf( ":" ) + 2 ) + encryptVal ) ;
			updatedContent.append( "\n" ) ;

		} else if ( isPropertyFile( line ) ) {
			// a property file

			String propKey = line.substring( 0, line.indexOf( "=" ) ) ;
			String propValue = line.substring( line.indexOf( "=" ) + 1 ) ;
			encodeResults.put( "key", propKey ) ;
			// encrypting the value
			encodeResults.put( "original", propValue ) ;
			String encryptVal = propValue ;

			if ( ! propValue.startsWith( "ENC(" ) ) {

				encryptVal = csapEncrypter.encrypt( propValue ) ;

			}

			encodeResults.put( "encrypted", encryptVal ) ;

			updatedContent.append( propKey + "=ENC(" + encryptVal + ")" ) ;
			updatedContent.append( "\n" ) ;

		} else {

			// Encrypt entire line
			encodeResults.put( "key", "none" ) ;
			// encrypting the value
			encodeResults.put( "original", line ) ;
			encodeResults.put( "encrypted", csapEncrypter.encrypt( line ) ) ;

		}

		return encodeResults ;

	}

	private boolean isPropertyFile ( String line ) {

		if ( line.contains( "ENC(" ) )
			return true ;

		return line.contains( "=" ) && line.indexOf( "=" ) + 1 < line.length( ) ;

	}

	@PostMapping ( value = "/project/source" , produces = MediaType.APPLICATION_JSON_VALUE )
	public JsonNode updateProjectSource (
											String source ,
											@RequestParam ( value = CsapCore.PROJECT_PARAMETER , required = false ) String csapProjectName ) {

		logger.info( "project: '{}'", csapProjectName ) ;

		if ( csapProjectName == null ) {

			csapProjectName = csapApp.getActiveProjectName( ) ;

		}

		var definition = csapApp.getProjectLoader( ).definition_string_reader( source.replaceAll( "\r", "\n" ) ) ;

		ObjectNode changeReport = null ;

		if ( csapApp.getProject( csapProjectName ) != null ) {

			changeReport = csapApp.getProject( csapProjectName ).editSource( CsapUser.currentUsersID( ), definition ) ;

		} else {

			logger.warn( "Did not find requested model: {}", csapProjectName ) ;
			changeReport = jsonMapper.createObjectNode( ) ;
			changeReport.put( "error", "Release package not found: " + csapProjectName ) ;

		}

		return changeReport ;

	}

	@GetMapping ( value = "/project/source" , produces = MediaType.APPLICATION_JSON_VALUE )
	public JsonNode getProjectSource (
										@RequestParam ( value = CsapCore.PROJECT_PARAMETER , required = false ) String csapProjectName ) {

		logger.info( "project: '{}'", csapProjectName ) ;

		if ( csapProjectName == null ) {

			csapProjectName = csapApp.getActiveProjectName( ) ;

		}

		csapApp.run_application_scan( ) ;

		ObjectNode modelObject = jsonMapper.createObjectNode( ) ;

		if ( csapApp.getProject( csapProjectName ) != null ) {

			modelObject = csapApp.getProject( csapProjectName ).getSource( ) ;

		} else {

			logger.warn( "Did not find requested model: {}", csapProjectName ) ;
			modelObject = jsonMapper.createObjectNode( ) ;
			modelObject.put( "error", "Release package not found: " + csapProjectName ) ;

		}

		return modelObject ;

	}

	@RequestMapping ( value = "/getDefinition" , produces = MediaType.APPLICATION_JSON_VALUE )
	public ObjectNode getDefinition (
										@RequestParam ( value = CsapCore.PROJECT_PARAMETER , required = false ) String csapProjectName ) {

		logger.info( "releasePackage: '{}'", csapProjectName ) ;

		if ( csapProjectName == null ) {

			csapProjectName = csapApp.getActiveProjectName( ) ;

		}

		csapApp.run_application_scan( ) ;

		ObjectNode modelObject ;

		if ( csapApp.getProject( csapProjectName ) != null ) {

			modelObject = (ObjectNode) csapApp.getProject( csapProjectName ).getSourceDefinition( ) ;

		} else {

			logger.warn( "Did not find requested model: {}", csapProjectName ) ;
			modelObject = jsonMapper.createObjectNode( ) ;
			modelObject.put( "error", "Release package not found: " + csapProjectName ) ;

		}

		return modelObject ;

	}

	@GetMapping ( "/service-definitions" )
	public ObjectNode serviceDefinitions (
											@RequestParam ( value = CsapCore.PROJECT_PARAMETER , required = false ) String csapProjectName )
		throws IOException {

		logger.info( "releasePackage {}", csapProjectName ) ;

		if ( csapProjectName == null ) {

			csapProjectName = csapApp.getActiveProjectName( ) ;

		}

		Project currentPackage = csapApp.getProject( csapProjectName ) ;

		ObjectNode serviceConfigurations = jsonMapper.createObjectNode( ) ;
		currentPackage.getServiceNameStream( ).forEach( serviceName -> {

			serviceConfigurations.set( serviceName,
					currentPackage.findAndCloneServiceDefinition( serviceName ) ) ;

		} ) ;

		return serviceConfigurations ;

	}

	@GetMapping ( "/external-release" )
	public ObjectNode getReleaseFile (
										@RequestParam ( value = CsapCore.PROJECT_PARAMETER , required = false ) String csapProjectName )
		throws IOException {

		logger.info( "project: {}", csapProjectName ) ;

		if ( csapProjectName == null ) {

			csapProjectName = csapApp.getActiveProjectName( ) ;

		}

		csapApp.run_application_scan( ) ;

		File releaseFile = csapApp.getProject( csapProjectName ).getReleaseFile( csapApp.applicationDefinition( ) ) ;

		ObjectNode externalReleaseInfo = jsonMapper.createObjectNode( ) ;
		externalReleaseInfo.put( "source", releaseFile.getName( ) ) ;

		if ( releaseFile.exists( ) ) {

			try {

				externalReleaseInfo.set(
						ProjectLoader.SERVICE_TEMPLATES,
						csapApp.getProjectLoader( ).definition_file_reader( releaseFile, false ) ) ;

			} catch ( IOException iOException ) {

				externalReleaseInfo.put( "error", iOException.getMessage( ) ) ;

			}

		} else {

			externalReleaseInfo.put( "error", "release file not found" ) ;

		}

		return externalReleaseInfo ;

	}

	@PostMapping ( "/lifecycle" )
	public ObjectNode lifecycleAddUpdateDelete (
													@RequestParam ( value = "lifeToEdit" , required = false ) String lifeToEdit ,
													@RequestParam ( "operation" ) String operation ,
													@RequestParam ( value = "newName" , required = false , defaultValue = "dummy" ) String newName ,
													@RequestParam ( value = CsapCore.PROJECT_PARAMETER , required = false ) String csapProjectName ) {

		if ( lifeToEdit == null ) {

			lifeToEdit = csapApp.getCsapHostEnvironmentName( ) ;

		}

		if ( csapProjectName == null ) {

			csapProjectName = csapApp.getActiveProjectName( ) ;

		}

		logger.info( "operation: {} , newName: {},  lifeToEdit: {} , releasePackage: {}, isUpdate: {}",
				operation, newName, lifeToEdit, csapProjectName ) ;

		ObjectNode updateResultNode = jsonMapper.createObjectNode( ) ;

		try {

			Project selectedProject = csapApp.getProject( csapProjectName ) ;
			ObjectNode selectedProjectSource = (ObjectNode) selectedProject.getSourceDefinition( ) ;

			ObjectNode testUpdatedSource = selectedProjectSource.deepCopy( ) ;
			ObjectNode testClusterBase = (ObjectNode) testUpdatedSource.at( csapApp.getProjectLoader( )
					.getEnvPath( ) ) ;

			JsonNode testLifeCycleDefinition = testUpdatedSource.at( csapApp.getProjectLoader( ).getEnvPath(
					lifeToEdit ) ) ;

			if ( operation.equals( "add" ) ) {

				validate_model_name_format_and_unused( selectedProject, newName, false ) ;

				ObjectNode newLife = testLifeCycleDefinition.deepCopy( ) ;
				testClusterBase.set( newName, newLife ) ;

				// replace all hosts
				AtomicInteger hostCount = new AtomicInteger( 1 ) ;
				String sampleHost = "ChangeMe-" + newName ;
				newLife.findValues( "hosts" ).forEach( hostContainer -> {

					logger.info( "Removing hosts: " + hostContainer.toString( ) ) ;

					if ( hostContainer.isArray( ) ) {

						( (ArrayNode) hostContainer ).removeAll( ) ;
						( (ArrayNode) hostContainer ).add( sampleHost + hostCount.getAndIncrement( ) ) ;

					}

				} ) ;

			} else if ( operation.equals( "delete" ) ) {

				if ( ! testClusterBase.has( lifeToEdit ) ) {

					throw new IOException(
							"LifeCycle name not found: "
									+ lifeToEdit ) ;

				}

				List<String> lifeNames = new ArrayList<>( ) ;
				testClusterBase
						.fieldNames( )
						.forEachRemaining( name -> {

							if ( ! name.equals( "settings" ) ) {

								lifeNames.add( name ) ;

							}

						} ) ;

				if ( lifeNames.size( ) <= 1 ) {

					throw new IOException(
							"Application  must contain at least 1 lifecycle. Add at least"
									+ " one new lifecycle prior to removing: " + lifeToEdit ) ;

				}

				testClusterBase.remove( lifeToEdit ) ;

			} else {

				throw new IOException(
						"Un supported  operation:"
								+ operation ) ;

			}

			updateResultNode.put( "lifeToEdit", lifeToEdit ) ;
			// logger.debug( "updateNode: \n{}",
			// updatedClusterDefinition.toString() );

			ObjectNode validationResults = csapApp.checkDefinitionForParsingIssues(
					testUpdatedSource.toString( ),
					selectedProject.getName( ) ) ;

			updateResultNode.set( "validationResults", validationResults ) ;

			boolean validatePassed = ( (ArrayNode) validationResults.get( Application.VALIDATION_ERRORS ) )
					.size( ) == 0 ;

			if ( validatePassed ) {

				updateResultNode.put( "updatedHost", csapApp.getCsapHostName( ) ) ;
				// currentPackage.setSourceDefinition( testPackageModel ) ;
				selectedProject.editSource( CsapUser.currentUsersID( ), testUpdatedSource ) ;

			}

		} catch ( Throwable ex ) {

			logger.error( "Failed to parse {}", CSAP.buildCsapStack( ex ) ) ;
			return buildEditingErrorResponse( ex, updateResultNode ) ;

		}

		return updateResultNode ;

	}

	@GetMapping ( "/cluster" )
	public ObjectNode clusterGet (
									@RequestParam ( "clusterName" ) String clusterName ,
									@RequestParam ( value = "lifeToEdit" , required = false ) String lifeToEdit ,
									@RequestParam ( value = CsapCore.PROJECT_PARAMETER , required = false ) String csapProjectName ) {

		if ( lifeToEdit == null ) {

			lifeToEdit = csapApp.getCsapHostEnvironmentName( ) ;

		}

		if ( csapProjectName == null ) {

			csapProjectName = csapApp.getActiveProjectName( ) ;

		}

		logger.info( "lifeToEdit: {}, clusterPath: {}, releasePackage: {}",
				lifeToEdit, clusterName, csapProjectName ) ;

		// ReleasePackage package = csapApp.getModel( releasePackage );
		Project currentPackage = csapApp.getProject( csapProjectName ) ;

		csapApp.run_application_scan( ) ;

		ObjectNode modelObject = (ObjectNode) currentPackage.getSourceDefinition( ) ;
		// //ReleasePackage serviceModel = csapApp.getModel( hostName,
		// serviceName ) ;
		// //logger.info( "Found model: {}",
		// serviceModel.getReleasePackageName() );
		ObjectNode clusterNode = (ObjectNode) modelObject.at(
				csapApp.getProjectLoader( ).getEnvPath( lifeToEdit ) + "/" + clusterName ) ;

		return clusterNode ;

	}

	@PostMapping ( "/cluster" )
	public ObjectNode clusterAddUpdateDeleteCopy (
													@RequestParam ( value = "lifeToEdit" , required = false ) String environmentName ,
													@RequestParam ( "clusterName" ) String clusterName ,
													@RequestParam ( "operation" ) String operation ,
													@RequestParam ( value = "newName" , required = false , defaultValue = "" ) String newName ,
													@RequestParam ( value = CsapCore.PROJECT_PARAMETER , required = false ) String csapProjectName ,
													@RequestParam ( "definition" ) String clusterDefinitionAsText ,
													@RequestParam ( value = "isUpdate" , required = false ) String isUpdate ) {

		if ( environmentName == null ) {

			environmentName = csapApp.getCsapHostEnvironmentName( ) ;

		}

		if ( csapProjectName == null ) {

			csapProjectName = csapApp.getActiveProjectName( ) ;

		}

		logger.info( "operation: {} , newName: {},  lifeToEdit: {} , clusterName: {}, releasePackage: {}, isUpdate: {}",
				operation, newName, environmentName, clusterName, csapProjectName, isUpdate ) ;
		logger.debug( "clusterDefinitionAsText: \n{}", clusterDefinitionAsText ) ;

		var results = jsonMapper.createObjectNode( ) ;

		try {

			var updatedClusterDefinition = (ObjectNode) jsonMapper.readTree( clusterDefinitionAsText ) ;

			Project currentProject = csapApp.getProject( csapProjectName ) ;

			var testProjectSource = currentProject.getSourceDefinition( ).deepCopy( ) ;

			var environment = (ObjectNode) testProjectSource.at( csapApp.getProjectLoader( ).getEnvPath(
					environmentName ) ) ;

			var clusterNames = csapApp.getProjectLoader( ).clusterNames( environment ) ;

			if ( operation.equals( "delete" ) ) {

				environment.remove( clusterName ) ;

			} else if ( operation.equals( "rename" ) ) {

				if ( validate_model_name_format_and_unused( currentProject, newName, true ) ) {

					// throw new IOException( "Found existing cluster, try another name: " + newName
					// ) ;
					results.put( "notes", "Warning - found references to " + newName ) ;

				}

				if ( clusterNames.contains( newName ) ) {

					throw new IOException( "Found existing cluster, try another name: " + newName ) ;

				}

				environment.remove( clusterName ) ;
				environment.set( newName, updatedClusterDefinition ) ;

			} else if ( operation.equals( "add" ) ) {

				if ( validate_model_name_format_and_unused( currentProject, clusterName, true ) ) {

					// throw new IOException( "Found existing cluster, try another name: " + newName
					// ) ;
					results.put( "notes", "Warning - found references to " + clusterName ) ;

				}

				if ( clusterNames.contains( clusterName ) ) {

					throw new IOException( "Found existing cluster, try another name: " + clusterName ) ;

				}

				environment.set( clusterName, updatedClusterDefinition ) ;

			} else if ( operation.equals( "copy" ) ) {

				if ( validate_model_name_format_and_unused( currentProject, newName, true ) ) {

					// throw new IOException( "Found existing cluster, try another name: " + newName
					// ) ;
					results.put( "notes", "Warning - found references to " + newName ) ;

				}

				if ( clusterNames.contains( newName ) ) {

					throw new IOException( "Found existing cluster, try another name: " + newName ) ;

				}

				environment.set( newName, updatedClusterDefinition ) ;

			} else if ( operation.equals( "push" ) ) {

				var pushResults = csapApp.getProjectLoader( ).getProjectOperators( ).pushDown( clusterName,
						environmentName, newName,
						testProjectSource ) ;
				logger.info( "push {} from: {} to {}, results : {}", clusterName, environmentName, newName,
						CSAP.jsonPrint( pushResults ) ) ;

				if ( pushResults.has( "error" ) ) {

					throw new IOException( "push down error: " + pushResults.path( "error" ).asText( ) ) ;

				}

				results.set( "notes", pushResults.path( "updates" ) ) ;

			} else { // modify

				environment.set( clusterName, updatedClusterDefinition ) ;

			}

			results.put( "lifeToEdit", environmentName ) ;
			logger.debug( "updateNode: \n{}", updatedClusterDefinition.toString( ) ) ;

			ObjectNode validationResults = csapApp.checkDefinitionForParsingIssues(
					testProjectSource.toString( ),
					currentProject.getName( ) ) ;

			results.set( "validationResults", validationResults ) ;

			boolean validatePassed = ( (ArrayNode) validationResults.get( Application.VALIDATION_ERRORS ) )
					.size( ) == 0 ;

			if ( isUpdate != null && validatePassed ) {

				results.put( "updatedHost", csapApp.getCsapHostName( ) ) ;
				// currentPackage.setSourceDefinition( testProjectDefinition ) ;
				currentProject.editSource( CsapUser.currentUsersID( ), testProjectSource ) ;

			}

		} catch ( Throwable ex ) {

			logger.error( "Failed to parse {}", CSAP.buildCsapStack( ex ) ) ;
			return buildEditingErrorResponse( ex, results ) ;

		}

		return results ;

	}

	@GetMapping ( "/settings/config" )
	public ObjectNode settingsConfig (
										@RequestParam ( value = "lifeToEdit" , required = false ) String lifeToEdit ) {

		logger.debug( "lifeToEdit: {}", lifeToEdit ) ;

		if ( lifeToEdit == null ) {

			lifeToEdit = csapApp.getCsapHostEnvironmentName( ) ;

		}

		ObjectNode config = jsonMapper.createObjectNode( ) ;
		ArrayNode services = config.putArray( "services" ) ;

		try {

			ObjectNode performanceCollections = jsonMapper.createObjectNode( ) ;

			performanceCollections.set( "Custom", OsSharedEnum.customReportLabels( ) ) ;
			performanceCollections.set( "Host", OsSharedEnum.hostReportLabels( ) ) ;
			performanceCollections.set( "HostRealTime", OsSharedEnum.realTimeLabelsForEditor( ) ) ;

			performanceCollections.set( "OS Process", OsProcessEnum.graphLabels( ) ) ;
			performanceCollections.set( "Java", JmxCommonEnum.graphLabels( ) ) ;
			performanceCollections.set( "Application", csapApp.servicePerformanceLabels( ) ) ;
			config.set( "performanceLabels", performanceCollections ) ;

		} catch ( Exception e ) {

			logger.error( "Failed configuring dialog", e ) ;

		}

		csapApp.getActiveProject( )
				.getAllPackagesModel( )
				.serviceNamesInModel( true, ProjectLoader.SERVICE_TEMPLATES ).stream( )
				.forEach( services::add ) ;
		// .findServiceNamesInLifecycle( lifeToEdit )
		// .forEach( services::add ) ;

		return config ;

	}

	@GetMapping ( "/settings" )
	public JsonNode settingsGet (
									@RequestParam ( value = "lifeToEdit" , required = false ) String lifeToEdit ,
									@RequestParam ( value = CsapCore.PROJECT_PARAMETER , required = false ) String csapProjectName ) {

		logger.debug( "lifeToEdit: {}, csapProjectName: {}", lifeToEdit, csapProjectName ) ;

		if ( lifeToEdit == null ) {

			lifeToEdit = csapApp.getCsapHostEnvironmentName( ) ;

		}

		csapApp.run_application_scan( ) ;

		JsonNode selectedModelDefinition = getModelOrRoot( csapProjectName ).getSourceDefinition( ) ;

		JsonNode settingsDefinition = selectedModelDefinition.at(
				csapApp.getProjectLoader( ).getEnvPath( lifeToEdit ) + "/"
						+ ProjectLoader.SETTINGS ) ;

		if ( settingsDefinition.isMissingNode( ) ) {

			settingsDefinition = jsonMapper.createObjectNode( ) ;

		}

		JsonNode realtimeMeterDefinition = settingsDefinition.at( "/metricsCollectionInSeconds/realTimeMeters" ) ;

		if ( realtimeMeterDefinition.isArray( ) ) {

			( (ArrayNode) realtimeMeterDefinition ).elements( ).forEachRemaining( realTimeDef -> {

				MetricCategory performanceCategory = MetricCategory.parse( realTimeDef ) ;

				if ( performanceCategory != MetricCategory.notDefined ) {

					if ( performanceCategory == MetricCategory.java ) {

						String id = realTimeDef.get( "id" ).asText( ) ;
						String[] ids = id.split( "\\." ) ;
						String[] attributes = ids[1].split( "_" ) ;

						if ( attributes.length == 3 ) {

							logger.info( "Stripping off port from {}, no longer needed", List.of( attributes ) ) ;
							( (ObjectNode) realTimeDef ).put( "id", id.substring( 0, id.lastIndexOf( "_" ) ) ) ;

						}

					}

				}

			} ) ;

		}

		JsonNode configMapDefinition = settingsDefinition.path( EnvironmentSettings.CONFIGURATION_MAPS ) ;

		if ( configMapDefinition.isObject( ) ) {

			CSAP.asStream( configMapDefinition.fieldNames( ) )
					.forEach( configMapName -> {

						JsonNode configMap = configMapDefinition.path( configMapName ) ;

						if ( configMap.isObject( ) ) {

							ArrayNode references = ( (ObjectNode) configMap ).putArray( "found-in" ) ;

							if ( configMapName.equals( "global" ) ) {

								references.add( "* all services" ) ;

							} else {

								getModelOrRoot( csapProjectName ).getServiceToAllInstancesMap( ).entrySet( ).stream( )
										.map( nameToList -> nameToList.getValue( ).get( 0 ) )
										.forEach( serviceInstance -> {

											JsonNode serviceEnvVarDefinition = serviceInstance
													.getAttributeOrMissing( ServiceAttributes.environmentVariables ) ;
											JsonNode serviceConfigArray = serviceEnvVarDefinition.path(
													EnvironmentSettings.CONFIGURATION_MAPS ) ;

											if ( serviceConfigArray.isArray( ) ) {

												CSAP.jsonStream( serviceConfigArray )
														.map( JsonNode::asText )
														.filter( configMapName::equals )
														.findFirst( )
														.ifPresent( mapName -> references.add( serviceInstance
																.getName( ) ) ) ;

											}

										} ) ;

							}

						}

					} ) ;

		}

		return settingsDefinition ;

	}

	private Project getModelOrRoot ( String releasePackage ) {

		Project model = csapApp.getRootProject( ) ;

		if ( releasePackage != null ) {

			Project serviceModel = csapApp.getProject( releasePackage ) ;

			if ( serviceModel != null ) {

				logger.info( "Found model: {}", serviceModel.getName( ) ) ;
				model = serviceModel ;

			}

		}

		return model ;

	}

	@PostMapping ( "/settings" )
	public ObjectNode settingsUpdate (
										@RequestParam ( "lifeToEdit" ) String lifeToEdit ,
										@RequestParam ( "definition" ) String definitionText ,
										@RequestParam ( value = CsapCore.PROJECT_PARAMETER , required = false ) String csapProjectName ,
										@RequestParam ( value = "isUpdate" , required = false ) String isUpdate ) {

		if ( lifeToEdit == null ) {

			lifeToEdit = csapApp.getCsapHostEnvironmentName( ) ;

		}

		logger.info( "lifeToEdit: {} , isUpdate: {}, releasePackage", lifeToEdit, isUpdate, csapProjectName ) ;
		logger.debug( "definitionText: \n{}", definitionText ) ;

		ObjectNode settingsUpdateResults = jsonMapper.createObjectNode( ) ;

		try {

			ObjectNode updatedSettingsDefinition = (ObjectNode) jsonMapper.readTree( definitionText ) ;

			// remove found-in field added for info only during download
			JsonNode configMapDefinition = updatedSettingsDefinition.path( EnvironmentSettings.CONFIGURATION_MAPS ) ;

			if ( configMapDefinition.isObject( ) ) {

				CSAP.asStream( configMapDefinition.fieldNames( ) )
						.forEach( configMapName -> {

							JsonNode configMap = configMapDefinition.path( configMapName ) ;

							if ( configMap.isObject( ) ) {

								( (ObjectNode) configMap ).remove( "found-in" ) ;

							}

						} ) ;

			}

			// ObjectNode modelNode = (ObjectNode)
			// csapApp.getRootModel().getJsonModelDefinition() ;
			Project currentProject = getModelOrRoot( csapProjectName ) ;
			ObjectNode testProjectSource = currentProject.getSourceDefinition( ).deepCopy( ) ;

			var environmentDefinition = testProjectSource.at( csapApp.getProjectLoader( ).getEnvPath( lifeToEdit ) ) ;
			settingsUpdateResults.put( "lifeToEdit", lifeToEdit ) ;

			if ( environmentDefinition.isMissingNode( ) ) {

				var validationResults = settingsUpdateResults.putObject( "validationResults" ) ;
				ArrayNode errors = validationResults.putArray( Application.VALIDATION_ERRORS ) ;
				errors.add( "Specified lifecycle: '" + lifeToEdit + "' is not created yet for selected project: '"
						+ csapProjectName
						+ "'. Create it using the application editor" ) ;

			} else {

				( (ObjectNode) environmentDefinition ).set( ProjectLoader.SETTINGS, updatedSettingsDefinition ) ;
				logger.debug( "updateNode: \n{}", updatedSettingsDefinition.toString( ) ) ;

				ObjectNode validationResults = csapApp.checkDefinitionForParsingIssues(
						testProjectSource.toString( ),
						currentProject.getName( ) ) ;

				settingsUpdateResults.set( "validationResults", validationResults ) ;

				boolean validatePassed = ( (ArrayNode) validationResults.get( Application.VALIDATION_ERRORS ) )
						.size( ) == 0 ;

				if ( validatePassed ) {

					validateTrendingAndRealTimeDefinition( lifeToEdit, updatedSettingsDefinition, validationResults ) ;

				}

				if ( isUpdate != null && validatePassed ) {

					settingsUpdateResults.put( "updatedHost", csapApp.getCsapHostName( ) ) ;
					settingsUpdateResults.put( "updatedPackage", currentProject.getName( ) ) ;
					// packageUpdated.setSourceDefinition( testPackageDefinition ) ;
					currentProject.editSource( CsapUser.currentUsersID( ), testProjectSource ) ;

				}

			}

		} catch ( Throwable ex ) {

			logger.error( "Failed to parse: {}", CSAP.buildCsapStack( ex ) ) ;
			return buildEditingErrorResponse( ex, settingsUpdateResults ) ;

		}

		return settingsUpdateResults ;

	}

	public void validateTrendingAndRealTimeDefinition (
														String lifeToEdit ,
														ObjectNode updatedSettingsDefinition ,
														ObjectNode validationResults ) {

		// these checks require LC be specified
		Set<String> allowedNames = csapApp.getActiveProject( ).getAllPackagesModel( ).findServiceNamesInLifecycle(
				lifeToEdit ) ;

		JsonNode trending = updatedSettingsDefinition.at( "/metricsCollectionInSeconds/trending" ) ;

		final String lc = lifeToEdit ;

		if ( ! trending.isMissingNode( ) && trending.isArray( ) ) {

			( (ArrayNode) trending ).elements( ).forEachRemaining( trendDef -> {

				if ( trendDef.has( "serviceName" ) ) {

					String serviceNameCommaSeparated = trendDef.get( "serviceName" ).asText( ) ;
					String[] serviceNames = serviceNameCommaSeparated.split( "," ) ;

					for ( String serviceName : serviceNames ) {

						if ( ! allowedNames.contains( serviceName ) && ! "all".equals( serviceName ) ) {

							( (ArrayNode) validationResults.get( Application.VALIDATION_WARNINGS ) )
									.add( "Service: " + serviceName
											+ " - found in trending definition but not found in lifecycle: " + lc ) ;

						}

					}

				}

			} ) ;

		}

		JsonNode realtime = updatedSettingsDefinition.at( "/metricsCollectionInSeconds/realTimeMeters" ) ;

		if ( ! realtime.isMissingNode( ) && realtime.isArray( ) ) {

			( (ArrayNode) realtime ).elements( ).forEachRemaining( realTimeDef -> {

				MetricCategory performanceCategory = MetricCategory.parse( realTimeDef ) ;

				if ( performanceCategory != MetricCategory.notDefined
						&& performanceCategory != MetricCategory.osShared ) {

					String serviceName = performanceCategory.serviceName( realTimeDef ) ;

					if ( ! allowedNames.contains( serviceName ) && ! "all".equals( serviceName ) ) {

						( (ArrayNode) validationResults.get( Application.VALIDATION_WARNINGS ) )
								.add( "Service: " + serviceName
										+ " - found in real time definition but not found in lifecycle: "
										+ lc + ", item:" + realTimeDef.toString( ) ) ;

					}

				} else if ( performanceCategory == MetricCategory.notDefined ) {

					( (ArrayNode) validationResults.get( Application.VALIDATION_WARNINGS ) )
							.add( "Real time meters in life cycle: " + lc + " contains unexpected category: "
									+ realTimeDef.toString( ) ) ;

				}

			} ) ;

		}

	}

	@GetMapping ( value = "/service/template" , produces = MediaType.APPLICATION_JSON_VALUE )
	public JsonNode serviceTemplate (
										String templateName )

		throws IOException {

		String sourceDir = csapApp.findFirstServiceInstanceInLifecycle( CsapCore.AGENT_NAME ).getScmLocation( ) ;

		return csapApp.getTemplateAndUpdateVariables( templateName, sourceDir ) ;

	}

	@GetMapping ( "/service/resource" )
	public JsonNode serviceResourceGet (
											@RequestParam ( "serviceName" ) String serviceName ,
											@RequestParam ( "hostName" ) String hostName ,
											@RequestParam ( "fileName" ) String fileName ,
											@RequestParam ( "environment" ) String environment ,
											@RequestParam ( value = CsapCore.PROJECT_PARAMETER , required = false ) String csapProjectName )
		throws IOException {

		Project serviceModel = null ;

		if ( csapProjectName == null ) {

			serviceModel = csapApp.getModel( hostName, serviceName ) ;

		} else {

			serviceModel = csapApp.getProject( csapProjectName ) ;

		}

		// resources are loaded
		var qualifiedServiceDefinition = serviceModel.findAndCloneServiceDefinition( serviceName ) ;
		ObjectNode serviceDefinition = (ObjectNode) qualifiedServiceDefinition ;
		servicesResources.addResourceFilesToDefinition( serviceDefinition, serviceName, environment, fileName ) ;

		logger.debug( "serviceDefinition: {}", CSAP.jsonPrint( serviceDefinition ) ) ;

		JsonNode content = null ;
		var optionalFileNode = CSAP.jsonStream( serviceDefinition.path( DefinitionRequests.DEFINITION_FILES ) )
				.filter( fileResource -> {

					return fileResource.path( ServiceAttributes.FileAttributes.lifecycle.json ).asText( ).equals(
							environment ) ;

				} )
				.filter( fileResource -> {

					return fileResource.path( ServiceAttributes.FileAttributes.name.json ).asText( ).equals(
							fileName ) ;

				} )
				.findFirst( ) ;

		if ( optionalFileNode.isPresent( ) ) {

			content = optionalFileNode.get( ).path( ServiceAttributes.FileAttributes.content.json ) ;

		}

		logger.debug( "content: {}", content ) ;

		return content ;

	}

	@GetMapping ( "/service" )
	public ObjectNode serviceGet (
									@RequestParam ( "serviceName" ) String serviceName ,
									@RequestParam ( "hostName" ) String hostName ,
									@RequestParam ( value = CsapCore.PROJECT_PARAMETER , required = false ) String csapProjectName )
		throws IOException {

		logger.info( "serviceName: {}, hostName: {}, releasePackage {} ", serviceName, hostName, csapProjectName ) ;

		if ( serviceName.contains( "_" ) ) {

			serviceName = serviceName.split( "_" )[0] ;

		}

		csapApp.run_application_scan( ) ;

		Project serviceModel = null ;

		if ( csapProjectName == null ) {

			serviceModel = csapApp.getModel( hostName, serviceName ) ;

		} else {

			serviceModel = csapApp.getProject( csapProjectName ) ;

		}

		logger.debug( "Found model: {}", serviceModel.getName( ) ) ;

		// clone the root object because it is updated with property files
		var qualifiedServiceDefinition = serviceModel.findAndCloneServiceDefinition( serviceName ) ;

		ObjectNode serviceDefinition ;

		if ( qualifiedServiceDefinition.isObject( ) ) {

			serviceDefinition = (ObjectNode) qualifiedServiceDefinition ;
			servicesResources.addResourceFilesToDefinition( serviceDefinition, serviceName, null, null ) ;

		} else {

			serviceDefinition = jsonMapper.createObjectNode( ) ;
			serviceDefinition.put( "message",
					"Service not found in current package, templates, or root package" ) ;

		}

		return serviceDefinition ;

	}

	@PostMapping ( "/service" )
	public ObjectNode serviceAddUpdateDelete (
												@RequestParam ( "serviceName" ) String serviceName ,
												@RequestParam ( "operation" ) String operation ,
												@RequestParam ( "hostName" ) String hostName ,
												@RequestParam ( value = "newName" , required = false , defaultValue = "dummy" ) String newName ,
												@RequestParam ( value = CsapCore.PROJECT_PARAMETER , required = false ) String csapProjectName ,
												@RequestParam ( "definition" ) String definition ,
												@RequestParam ( value = "isUpdate" , required = false ) String isUpdate ) {

		if ( serviceName.contains( "_" ) ) {

			serviceName = serviceName.split( "_" )[0] ;

		}

		logger.info( "releasePackage: {}, serviceName: {} , isUpdate: {}, operation: {}",
				csapProjectName, serviceName, isUpdate, operation ) ;

		ObjectNode updateResultNode = jsonMapper.createObjectNode( ) ;

		try {

			ObjectNode updatedServiceDefinition = (ObjectNode) jsonMapper.readTree( definition ) ;

			var updatedSource = updatedServiceDefinition.path( Project.DEFINITION_SOURCE ).asText( ) ;

			var isExternalDefinition = updatedSource.contains( Project.DEFINITION_TEMPLATE )
					|| updatedSource.contains( Project.DEFINITION_COPY ) ;

			updatedServiceDefinition.remove( Project.DEFINITION_SOURCE ) ;

			Project currentProject = null ;

			if ( csapProjectName == null ) {

				currentProject = csapApp.getModel( hostName, serviceName ) ;

			} else {

				currentProject = csapApp.getProject( csapProjectName ) ;

			}

			// ObjectNode modelNode = (ObjectNode) currentProject.getSourceDefinition() ;
			ObjectNode currentProjectDefinition = (ObjectNode) ( currentProject.getSource( ).path( "source" ) ) ;

			ObjectNode testProjectDefinition = currentProjectDefinition.deepCopy( ) ;

			String serviceType = updatedServiceDefinition.path( "server" ).asText( "notFound" ) ;
			boolean isJavaServiceProfile = ProcessRuntime.isJavaServer( serviceType ) ;

			logger.info( "serviceType: {}, isJavaServiceProfile: {}", serviceType, isJavaServiceProfile ) ;

			// var servicesContainerType = DefinitionParser.DEFINITION_SERVICES ;
			//// if ( ! isJavaServiceProfile ) {
			//// servicesContainerType = DefinitionParser.SERVICE_CATEGORY_OS
			// ;SERVICE_CATEGORY_JVMS
			//// }
			var serviceDefinitions = testProjectDefinition.path( ProjectLoader.SERVICE_TEMPLATES ) ;

			logger.info( "serviceDefinitions: {}", serviceDefinitions.getClass( ) ) ;

			if ( ! serviceDefinitions.isObject( ) ) {

				serviceDefinitions = testProjectDefinition.putObject( ProjectLoader.SERVICE_TEMPLATES ) ;

			}

			logger.info( "serviceDefinitions: {}", serviceDefinitions.getClass( ) ) ;

			if ( ! operation.equals( "modify" ) && ! isExternalDefinition ) {

				if ( serviceName.equalsIgnoreCase( CsapCore.AGENT_NAME ) ) {

					throw new IOException( "Agent only supports modify operation: " + newName ) ;

				}

			}

			if ( isExternalDefinition ) {

				if ( operation.equals( "delete" ) ) {

					logger.info( "Updating service template - removing original service" ) ;
					( (ObjectNode) serviceDefinitions ).remove( serviceName ) ;

					updateResultNode.put( "message",
							"Removed service from application: service definition template will be used if available." ) ;

				} else if ( operation.equals( "add" ) || operation.equals( "modify" ) ) {

					if ( operation.equals( "add" ) ) {

						validate_model_name_format_and_unused( currentProject, serviceName, false ) ;
						( (ObjectNode) serviceDefinitions ).putObject( serviceName ) ;

					}

					var updated = false ;
					var message = "" ;
					var currentServiceDefinition = serviceDefinitions.path( serviceName ) ;

					if ( updatedServiceDefinition.has( Project.DEFINITION_COPY ) ) {

						updated = true ;
						( (ObjectNode) currentServiceDefinition ).put(
								Project.DEFINITION_COPY,
								updatedServiceDefinition.path( Project.DEFINITION_COPY ).asText( ) ) ;
						message = "updated " + Project.DEFINITION_COPY ;

					}

					if ( updatedServiceDefinition.has( DEFINITION_FILES ) ) {

						updated = true ;

						message += "Service definition template resources updated." ;
						logger.info( "Updating service template - service resources only" ) ;
						pendingDefinitionManager.processServiceResourceEdits( updatedServiceDefinition, serviceName ) ;

					}

					if ( ! updated ) {

						message = "service using a template - no changes found" ;

					}

					updateResultNode.put( "message", message ) ;

				} else {

					throw new IOException(
							"Service definitions with source set to csap-template only support update or delete" ) ;

				}

			} else if ( operation.equals( "delete" ) ) {

				( (ObjectNode) serviceDefinitions ).remove( serviceName ) ;

				String oldServiceName = serviceName ;

				AtomicInteger numOsClusters = removeDefinitionReferences(
						ProjectLoader.CLUSTER_TEMPLATE_REFERENCES, testProjectDefinition,
						oldServiceName ) ;

				updateResultNode.put( "message", "Cluster references removed: " + numOsClusters.toString( ) ) ;

				pendingDefinitionManager.addServiceDelete( serviceName ) ;

			} else if ( operation.equals( "add" ) ) {

				validate_model_name_format_and_unused( currentProject, serviceName, false ) ;
				( (ObjectNode) serviceDefinitions ).set( serviceName, updatedServiceDefinition ) ;

				if ( updatedServiceDefinition.has( DEFINITION_FILES ) ) {

					pendingDefinitionManager.processServiceResourceEdits( updatedServiceDefinition, serviceName ) ;

				}

			} else if ( operation.equals( "copy" ) ) {

				String oldServiceName = serviceName ;

				validate_model_name_format_and_unused( currentProject, newName, false ) ;
				( (ObjectNode) serviceDefinitions ).set( newName, updatedServiceDefinition ) ;

				pendingDefinitionManager.addServiceCopy( oldServiceName, newName ) ;

			} else if ( operation.equals( "rename" ) ) {

				String oldServiceName = serviceName ;
				validate_model_name_format_and_unused( currentProject, newName, false ) ;

				( (ObjectNode) serviceDefinitions ).remove( serviceName ) ;
				( (ObjectNode) serviceDefinitions ).set( newName, updatedServiceDefinition ) ;

				AtomicInteger numOsClusters = updateDefinitionWithRenameReferences(
						ProjectLoader.CLUSTER_TEMPLATE_REFERENCES,
						testProjectDefinition, oldServiceName, newName ) ;

				updateResultNode.put( "message",
						"OS Cluster references updated: " + numOsClusters.toString( ) ) ;

				pendingDefinitionManager.addServiceRename( oldServiceName, newName ) ;

			} else {

				updatedServiceDefinition.remove( Project.DEFINITION_COPY ) ;
				( (ObjectNode) serviceDefinitions ).set( serviceName, updatedServiceDefinition ) ;

				if ( updatedServiceDefinition.has( DEFINITION_FILES ) ) {

					pendingDefinitionManager.processServiceResourceEdits( updatedServiceDefinition, serviceName ) ;

				}

			}

			/**
			 * Remove files from definition - all files are demarked above
			 */
			updatedServiceDefinition.remove( DEFINITION_FILES ) ;

			updateResultNode.put( "releasePackage", currentProject.getName( ) ) ;
			logger.debug( "updateNode: \n{}", updatedServiceDefinition.toString( ) ) ;

			ObjectNode validationResults = csapApp.checkDefinitionForParsingIssues(
					testProjectDefinition.toString( ),
					currentProject.getName( ) ) ;

			updateResultNode.set( "validationResults", validationResults ) ;

			boolean validatePassed = ( (ArrayNode) validationResults.get( Application.VALIDATION_ERRORS ) )
					.size( ) == 0 ;

			if ( isUpdate != null && validatePassed ) {

				updateResultNode.put( "updatedHost", csapApp.getCsapHostName( ) ) ;
				// currentProject.setSourceDefinition( testProjectDefinition ) ;
				currentProject.editSource( CsapUser.currentUsersID( ), testProjectDefinition ) ;

			}

		} catch ( Throwable ex ) {

			logger.error( "Failed to parse: {}", CSAP.buildCsapStack( ex ) ) ;
			return buildEditingErrorResponse( ex, updateResultNode ) ;

		}

		return updateResultNode ;

	}

	public ObjectNode buildEditingErrorResponse ( Throwable ex , ObjectNode updateResultNode ) {

		ObjectNode resultNode = jsonMapper.createObjectNode( ) ;
		ArrayNode errorNode = resultNode.putArray( VALIDATION_ERRORS ) ;
		String errMessage = ex.getMessage( ) ;

		if ( errMessage == null ) {

			errMessage = ex.getClass( ).getSimpleName( ) ;

		}

		errorNode.add( errMessage ) ;
		updateResultNode.set( "validationResults", resultNode ) ;
		updateResultNode.put( "Stage", "Merged Validation" ) ;

		if ( ex.getCause( ) != null ) {

			updateResultNode.put( "Cause", ex.getCause( ).getClass( ).getName( ) ) ;

		}

		updateResultNode.put( "Type", ex.getClass( ).getName( ) ) ;
		updateResultNode.put( "Message", ex.getMessage( ) ) ;
		return updateResultNode ;

	}

	public AtomicInteger removeDefinitionReferences (
														String containerId ,
														ObjectNode testPackageModel ,
														String oldServiceName ) {

		AtomicInteger numClusters = new AtomicInteger( 0 ) ;
		testPackageModel.findValues( containerId ).forEach( serviceNode -> {

			if ( serviceNode.has( oldServiceName ) && serviceNode.isObject( ) ) {

				numClusters.getAndIncrement( ) ;
				( (ObjectNode) serviceNode ).remove( oldServiceName ) ;
				;

			} else if ( serviceNode.isArray( ) ) {

				// This is a OS node
				ArrayNode osNode = (ArrayNode) serviceNode ;

				for ( int i = 0; i < osNode.size( ); i++ ) {

					String curItem = osNode.get( i ).asText( ) ;

					if ( curItem.equals( oldServiceName ) ) {

						numClusters.getAndIncrement( ) ;
						osNode.remove( i ) ;
						break ;

					}

				}

			}

		} ) ;
		return numClusters ;

	}

	private AtomicInteger updateDefinitionWithRenameReferences (
																	String containerId ,
																	ObjectNode testPackageModel ,
																	String oldServiceName ,
																	String newName ) {

		// find and replace references
		StringBuilder replaceResults = new StringBuilder( "Updated Clusters: " ) ;
		AtomicInteger numClusters = new AtomicInteger( 0 ) ;
		testPackageModel.findValues( containerId ).forEach( serviceJson -> {

			if ( serviceJson.has( oldServiceName ) && serviceJson.isObject( ) ) {

				// this is a jvm node
				numClusters.getAndIncrement( ) ;
				JsonNode currentJvmPort = serviceJson.get( oldServiceName ) ;
				( (ObjectNode) serviceJson ).remove( oldServiceName ) ;
				( (ObjectNode) serviceJson ).set( newName, currentJvmPort ) ;
				replaceResults.append( "\n" + currentJvmPort.toString( ) ) ;

			} else if ( serviceJson.isArray( ) ) {

				// This is a OS node
				ArrayNode osNode = (ArrayNode) serviceJson ;

				for ( int i = 0; i < osNode.size( ); i++ ) {

					String curItem = osNode.get( i ).asText( ) ;

					if ( curItem.equals( oldServiceName ) ) {

						numClusters.getAndIncrement( ) ;
						osNode.remove( i ) ;
						osNode.add( newName ) ;
						break ;

					}

				}

			}

		} ) ;
		logger.info( "Number of Updates: {}", numClusters ) ;
		return numClusters ;

	}

	private boolean validate_model_name_format_and_unused (
															Project currentPackageModel ,
															String newName ,
															boolean nameWarningOnly )
		throws IOException {

		if ( csapApp.getProjectLoader( ).is_invalid_model_name( newName ) ) {

			throw new IOException( "attribute names must be alpha numeric, and optionally hyphens: " + newName ) ;

		}

		var nameInPackage = currentPackageModel.isNameInPackageDefinitions( newName ) ;

		if ( nameInPackage && ! nameWarningOnly ) {

			throw new IOException( "Found an item in project with the specified name: '"
					+ newName + "'. Try another name" ) ;

		}

		return nameInPackage ;

	}

	@PostMapping ( "/validateDefinition" )
	synchronized public ObjectNode validateDefinition (
														@RequestParam ( "updatedConfig" ) String updatedConfig ,
														@RequestParam ( value = CsapCore.PROJECT_PARAMETER , required = false ) String csapProjectName ,
														HttpServletRequest request ) {

		logger.info( " releasePackage: {} ", csapProjectName ) ;

		return csapApp.checkDefinitionForParsingIssues( updatedConfig, csapProjectName ) ;

	}

	@PostMapping ( "autoplay" )
	synchronized public ObjectNode //
			auto_play (
						boolean isApply ,
						String filePath ,
						HttpServletRequest request )
				throws Exception {

		logger.info( "filePath:{}", filePath ) ;

		csapEventClient.publishUserEvent( CsapEvents.CSAP_OS_CATEGORY + "/autoplay",
				CsapUser.currentUsersID( ),
				filePath, "auto play preview request" ) ;

		var autoplayResult = jsonMapper.createObjectNode( ) ;

		if ( StringUtils.isNotEmpty( filePath ) ) {

			File autoplayFile = new File( filePath ) ;

			if ( filePath.startsWith( "__" ) ) {

				autoplayFile = csapApp.getRequestedFile( filePath, null, false ) ;

			}

			autoplayResult.put( "autoplayFile", autoplayFile.getAbsolutePath( ) ) ;

			if ( autoplayFile.exists( ) && autoplayFile.isFile( ) && autoplayFile.length( ) > 10 ) {

				var rootProject = csapApp.getRootProject( ) ;

				if ( isApply ) {

					autoplayApply( autoplayResult, autoplayFile, rootProject ) ;

				} else {

					autoplayPreview( autoplayResult, autoplayFile, rootProject ) ;

				}

			} else {

				autoplayResult.put( "error", "invalid contents" ) ;

			}

		} else {

			autoplayResult.put( "error", "missing parameter filePath" ) ;

		}

		return autoplayResult ;

	}

	private void autoplayApply (
									ObjectNode autoplayResult ,
									File autoplayFile ,
									Project rootProject )
		throws IOException {

		var homeAutoPlayFile = csapApp.getAutoPlayFile( ) ;

		if ( homeAutoPlayFile.exists( ) ) {

			autoplayResult.put( "delete", homeAutoPlayFile.getAbsolutePath( ) ) ;
			FileUtils.deleteQuietly( homeAutoPlayFile ) ;

		}

		autoplayResult.put( "creating", homeAutoPlayFile.getAbsolutePath( ) ) ;
		FileUtils.copyFile( autoplayFile, homeAutoPlayFile ) ;

		var liveDefinitionFile = rootProject.getSourceFile( ) ;

		//
		// Desktop testing support
		//
		if ( csapApp.isDesktopHost( ) ) {

			var desktopDefinition = new File( homeAutoPlayFile.getParentFile( ), "auto-play-desktop" ) ;

			if ( desktopDefinition.exists( ) ) {

				autoplayResult.put( "delete", desktopDefinition.getAbsolutePath( ) ) ;
				FileUtils.deleteQuietly( desktopDefinition ) ;

			}

			autoplayResult.put( "desktop-clone", desktopDefinition.getAbsolutePath( ) ) ;
			FileUtils.copyDirectory( csapApp.getDefinitionFolder( ), desktopDefinition ) ;
			liveDefinitionFile = new File( desktopDefinition, liveDefinitionFile.getName( ) ) ;

		}

		ObjectNode projectDefinition = rootProject.getSourceDefinition( ).deepCopy( ) ;

		var autoplayResults = csapApp.getProjectLoader( ).getProjectOperators( ).processAutoPlay(
				homeAutoPlayFile,
				liveDefinitionFile,
				projectDefinition ) ;

		autoplayResult.set( "autoplay-results", autoplayResults ) ;

		logger.info( "autoplayResults: ${}", autoplayResults ) ;

		// csapApp.getProjectLoader().writeDefinitionToDisk( liveDefinitionFile,
		// projectDefinition ) ;

		try {

			var applyResults = application_apply_or_checkin(
					CsapUser.currentUsersID( ),
					null, true,
					null,
					"from autoplay apply",
					csapApp.getProjectLoader( ).convertDefinition( projectDefinition ),
					rootProject.getName( ),
					true, null ) ;

			autoplayResult.put( "parsing-summary", csapApp.getProjectLoader( ).getLastSummary( ) ) ;
			autoplayResult.put( "parsing-results", applyResults.path( DockerJson.response_plain_text.json( ) )
					.asText( ) ) ;

		} catch ( Exception e ) {

			var header = CsapCore.CONFIG_PARSE_ERROR ;
			header += "\n\t For test purposes - ensure that the console host is included in at least one cluster" ;
			var errorMessage = header + CSAP.buildCsapStack( e ) ;
			logger.warn( "{}", errorMessage ) ;
			autoplayResult.put( "parsing-results", errorMessage ) ;

		}

	}

	private void autoplayPreview ( ObjectNode autoplayResult , File autoplayFile , Project rootProject )
		throws IOException {

		var previewFolder = new File( autoplayFile.getParentFile( ), autoplayFile.getName( ) + "-preview" ) ;
		autoplayResult.put( "previewFolder", previewFolder.getAbsolutePath( ) ) ;

		if ( previewFolder.exists( ) ) {

			autoplayResult.put( "delete", previewFolder.getAbsolutePath( ) ) ;
			FileUtils.deleteQuietly( previewFolder ) ;

		}

		autoplayResult.put( "cloning", csapApp.getDefinitionFolder( ).getAbsolutePath( ) ) ;
		FileUtils.copyDirectory( csapApp.getDefinitionFolder( ), previewFolder ) ;

		var testAutoPlayFile = new File( previewFolder, autoplayFile.getName( ) ) ;
		FileUtils.copyFile( autoplayFile, testAutoPlayFile ) ;
		autoplayResult.put( "creating", testAutoPlayFile.getAbsolutePath( ) ) ;

		File definitionFile = new File( previewFolder, rootProject.getSourceFileName( ) ) ;

		ObjectNode projectDefinition = rootProject.getSourceDefinition( ).deepCopy( ) ;
		var results = csapApp.getProjectLoader( ).getProjectOperators( ).processAutoPlay(
				testAutoPlayFile, definitionFile,
				projectDefinition ) ;
		autoplayResult.set( "autoplay-results", results ) ;

		csapApp.getProjectLoader( ).writeDefinitionToDisk( definitionFile, projectDefinition ) ;

		StringBuilder parsingResults ;

		try {

			parsingResults = csapApp.getProjectLoader( ).process( true, definitionFile ) ;
			autoplayResult.put( "parsing-summary", csapApp.getProjectLoader( ).getLastSummary( ) ) ;

		} catch ( Exception e ) {

			var header = CsapCore.CONFIG_PARSE_ERROR ;
			header += "\n\t For test purposes - ensure that the console host is included in at least one cluster" ;
			var errorMessage = header + CSAP.buildCsapStack( e ) ;
			parsingResults = new StringBuilder( errorMessage ) ;
			logger.warn( "{}", errorMessage ) ;

		}

		autoplayResult.put( "parsing-results", parsingResults.toString( ) ) ;

	}

	/**
	 *
	 * Method for applying changes made in browser to server. - Optional support for
	 * checkin
	 *
	 */
	public static final String APPICATION_APPLY = "/applicationApply" ;
	public static final String APPICATION_CHECKIN = "/applicationCheckIn" ;

	@PostMapping ( value = {
			APPICATION_APPLY, APPICATION_CHECKIN
	} )
	synchronized public ObjectNode //
			application_apply_or_checkin (
											@RequestParam ( "scmUserid" ) String scmUserid ,
											@RequestParam ( "scmPass" ) String rawPass ,
											@RequestParam ( defaultValue = "false" ) boolean isUpdateAll ,

											@RequestParam ( "scmBranch" ) String scmBranch ,
											@RequestParam ( value = "comment" , required = false ) String comment ,
											@RequestParam ( value = "updatedConfig" , required = false ) String projectDefinition ,
											@RequestParam ( value = CsapCore.PROJECT_PARAMETER , required = false ) String csapProjectName ,
											@RequestParam ( value = "applyButNoCheckin" , defaultValue = "false" ) boolean isApplyButNoCheckin ,
											HttpServletRequest request )
				throws Exception {

		String encryptedPass = encryptor.encrypt( rawPass ) ; // immediately
		// encrypt pass

		logger.info( "user:{}, branch: {}, package: {}, applyOnly: {} ",
				scmUserid, scmBranch, csapProjectName, isApplyButNoCheckin ) ;

		ServiceInstance dummyServiceInstanceForApp = new ServiceInstance( ) ;
		dummyServiceInstanceForApp.setScmLocation( csapApp.getRootProjectDefinitionUrl( ) ) ;
		dummyServiceInstanceForApp.setScm( csapApp.getSourceType( ) ) ;

		File globalModelBuildFolder = new File( csapApp.getRootModelBuildLocation( ) ) ;

		String targetProjectFileNameToUpdate = csapApp.getProject( csapProjectName ).getSourceFileName( ) ;

		// Critical hook - need to blow away previous folder since there is no
		// clean
		String command = APPICATION_APPLY ;

		if ( ( request != null )
				&& ( request.getRequestURI( ).contains( APPICATION_CHECKIN ) ) ) {

			command = APPICATION_CHECKIN ;

		}

		OutputFileMgr outputManager = new OutputFileMgr( csapApp.getCsapWorkingFolder( ),
				command.substring( 1 ) ) ;

		try {

			// Create a new empty working folder for the uploaded file
			// Working folder is used solely to validate contents, then will be
			// moved to build folder prior to triggering reload
			File defWorkingFolder = new File( csapApp.getRootModelBuildLocation( ) + CONFIG_SUFFIX_FOR_UPDATE ) ;

			FileUtils.deleteQuietly( defWorkingFolder ) ; // maybe just be doing
															// updates instead
															// of deletes here.

			if ( ! isApplyButNoCheckin ) {

				check_out_definition( scmUserid, isUpdateAll, scmBranch,
						encryptedPass, dummyServiceInstanceForApp,
						outputManager, defWorkingFolder ) ;

			} else {

				// createWorkingFolder using existing live files
				File sourceLocation = csapApp.getDefinitionFolder( ) ;

				// FileUtils.copyDirectory( sourceLocation, workingFolder,
				// FileFilterUtils.suffixFileFilter( ".js" ) );
				// using all files found.....
				FileUtils.copyDirectory( sourceLocation, defWorkingFolder ) ;
				outputManager.print( "Created working folder: " + defWorkingFolder.getAbsolutePath( )
						+ "\n initialized from: " + sourceLocation.getAbsolutePath( )
						+ "\n containing: " + Arrays.asList( defWorkingFolder.list( ) ) ) ;

			}

			// First put the uploaded file into working directory
			File testProjectFile = new File( defWorkingFolder, targetProjectFileNameToUpdate ) ;
			File testRootProjectFile = new File( defWorkingFolder, csapApp
					.getRootProject( )
					.getSourceFileName( ) ) ;

			if ( projectDefinition == null ) {

				// iterate over all projects, writing them
				csapApp.getRootProject( )
						.getProjects( )
						.filter( Project::isModified )
						.forEach( project -> {

							var modifiedprojectFile = new File( defWorkingFolder, project.getSourceFileName( ) ) ;

							logger.info( "Found edits for: {},  writing source to disk: {}", project.getName( ),
									modifiedprojectFile ) ;
							csapApp.getProjectLoader( ).writeDefinitionToDisk( modifiedprojectFile, project
									.getSourceDefinition( ) ) ;
							// FileUtils.writeStringToFile( modifiedprojectFile,
							// project.getEditInProgressDefinition() ) ;

						} ) ;

			} else {

				// legacy
				FileUtils.writeStringToFile( testProjectFile, projectDefinition.replaceAll( "\r", "\n" ) ) ;
				outputManager.print( "Pushed updated definition File to : " + testProjectFile.getAbsolutePath( ) ) ;

			}

			//
			// Run the parser
			//
			try {

				// first we run in test mode to verify content
				StringBuilder parsingResultsBuffer = csapApp.getProjectLoader( ).process( true, testRootProjectFile ) ;

				if ( ( parsingResultsBuffer != null )
						&& parsingResultsBuffer.indexOf( CsapCore.CONFIG_PARSE_ERROR ) == -1 ) {

					activate_definition(
							outputManager, parsingResultsBuffer,
							isApplyButNoCheckin, testProjectFile, scmBranch,
							defWorkingFolder, scmUserid, encryptedPass, comment,
							csapProjectName, globalModelBuildFolder, targetProjectFileNameToUpdate ) ;

				} else {

					logger.error( "Failed to parse" ) ;

					if ( ( parsingResultsBuffer != null ) ) {

						outputManager.print( "-" ) ;
						outputManager
								.print( "\n\n============= Found Semantic Errors !! ====================\n"
										+ "Filtered output for :"
										+ CsapCore.CONFIG_PARSE_ERROR ) ;
						List<String> parmList = new ArrayList<String>( ) ;
						Collections.addAll(
								parmList,
								"bash",
								"-c",
								"diff  " + csapApp.applicationDefinition( ).getAbsolutePath( ) + " "
										+ testProjectFile.getAbsolutePath( ) ) ;
						sourceControlManager.executeShell( parmList,
								outputManager.getBufferedWriter( ) ) ;

						csapApp.updateOutputWithLimitedInfo( CsapCore.CONFIG_PARSE_ERROR, 999, outputManager,
								parsingResultsBuffer, null ) ;

					}

				}

			} catch ( Exception parseException ) {

				String errorStack = CSAP.buildCsapStack( parseException ) ;
				logger.error( "Parsing error in application: {}", errorStack ) ;
				List<String> parmList = new ArrayList<String>( ) ;
				Collections.addAll( parmList, "bash", "-c", "diff  "
						+ csapApp.applicationDefinition( ).getAbsolutePath( ) + " "
						+ testProjectFile.getAbsolutePath( ) ) ;
				sourceControlManager.executeShell( parmList, outputManager.getBufferedWriter( ) ) ;

				outputManager.print( CsapCore.CONFIG_PARSE_ERROR
						+ "Parsing error in application: " + errorStack ) ;
				// Application.getCustomStackTrace(e1)
				outputManager.print( "\n     ACTION Required: Fix the error, and try again." ) ;

			}

		} catch ( GitAPIException gitApiException ) {

			// git failures come in here
			String errorStack = CSAP.buildCsapStack( gitApiException ) ;

			csapEventClient.publishUserEvent( CsapEvents.CSAP_OS_CATEGORY + "/definition/checkin",
					CsapUser.currentUsersID( ),
					"Failure: git access failure", errorStack ) ;

			logger.error( "Failed updating:  {}", errorStack ) ;

			// outputManager.print( "\n\n" + CsapCore.CONFIG_PARSE_ERROR
			// + "Failed updating application definition:\n" + errorStack ) ;
			outputManager.print( CsapCore.CONFIG_PARSE_ERROR ) ;

			var message = "git access failure - troubleshooting steps: \n\t - verify password \n\t - verify git service provider\n\t - verify network connectivity" ;
			outputManager.print( CsapApplication.header( message ) ) ;

		} catch ( Exception e ) {

			// other failures come in here
			String errorStack = CSAP.buildCsapStack( e ) ;
			logger.error( "Failed updating:  {}", errorStack ) ;

			csapEventClient.publishUserEvent( CsapEvents.CSAP_OS_CATEGORY + "/definition/checkin",
					CsapUser.currentUsersID( ),
					"Failure: " + e.getMessage( ), errorStack ) ;

			outputManager.print( CsapCore.CONFIG_PARSE_ERROR
					+ "Failed updating application definition:\n" + errorStack ) ;

		} finally {

			outputManager.close( ) ;

		}

		// this is what triggers local host to update.
		// in case of password update --- this will occur AFTER transfers have
		// completed
		csapApp.run_application_scan( ) ;

		if ( isApplyButNoCheckin ) {

			// user is testing changes - set their id...
			csapApp.getRootProject( ).setEditUserid( CsapUser.currentUsersID( ) ) ;

		}

		ObjectNode resultJson = jsonMapper.createObjectNode( ) ;
		resultJson.put( DockerJson.response_plain_text.json( ), outputManager.getContents( ) ) ;

		return resultJson ;

	}

	private void check_out_definition (
										String scmUserid ,
										boolean isUpdateAll ,
										String scmBranch ,
										String encryptedPass ,
										ServiceInstance dummyServiceInstanceForApp ,
										OutputFileMgr outputManager ,
										File defWorkingFolder )
		throws Exception ,
		IOException {

		sourceControlManager.checkOutFolder(
				scmUserid, encryptedPass, scmBranch,
				defWorkingFolder.getName( ),
				dummyServiceInstanceForApp, outputManager.getBufferedWriter( ) ) ;

		var workingHeadFile = printGitHead( outputManager, defWorkingFolder, scmBranch ) ;
		outputManager.print( CSAP.padLine( "Working Folder" ) + defWorkingFolder.getAbsolutePath( ) ) ;
		outputManager.print( CSAP.padLine( "Replaced" ) + "using content retrieved from source control system" ) ;

		var activeDefinitionFolder = csapApp.getDefinitionFolder( ) ;
		//
		// Verify working version was checked out of same version as active
		//
		var activeHeadFile = new File( activeDefinitionFolder, GIT_SOURCE ) ;

		outputManager.print( CSAP.padLine( "activeHeadFile" ) + activeHeadFile.getAbsolutePath( ) ) ;

		if ( workingHeadFile.exists( ) && activeHeadFile.exists( ) ) {

			var workingHash = Application.readFile( workingHeadFile ) ;
			var activeHash = Application.readFile( activeHeadFile ) ;
			outputManager.print( CSAP.padLine( "checked out hash" ) + workingHash ) ;
			outputManager.print( CSAP.padLine( "current hash" ) + activeHash ) ;

			if ( ! workingHash.equals( activeHash ) ) {

				outputManager.print( CSAP.padLine( CsapCore.CONFIG_PARSE_WARN )
						+ "GIT version mismatch. To ignore - delete active head file" ) ;

				if ( csapApp.environmentSettings( ).isDefinitionAbortOnBaseMismatch( ) ) {

					throw new IOException(
							"GIT has mismatch detected: the application definition on disk was not checked out from the selected branch"
									+ "\n to ignore, delete: " + activeHeadFile.getAbsolutePath( ) ) ;

				}

			}

		} else {

			outputManager.print( CSAP.padLine( CsapCore.CONFIG_PARSE_WARN ) + "git head file not found." ) ;

		}

		if ( isUpdateAll ) {

			if ( csapApp.getSourceType( ).equals( ScmProvider.git.key ) ) {

				logger.warn( "replacing source control files checked out with current definition on disk" ) ;
				outputManager.print( CsapApplication.header( "Merging working definition into checked out folder" ) ) ;

				String deleteOutput = csapApp.getOsManager( ).removeNonGitFiles( defWorkingFolder ) ;

				if ( logger.isDebugEnabled( ) ) {

					logger.debug( CsapApplication.header( "{}" ), deleteOutput ) ;

				}

				// outputManager.print( CSAP.pad( "deleteOutput" ) + deleteOutput ) ;
				outputManager.print( CSAP.pad( "delete" ) + "removing everything but hidden files from "
						+ defWorkingFolder.getName( ) ) ;

				FileUtils.copyDirectory( activeDefinitionFolder, defWorkingFolder, getGitFilter( ) ) ;

				outputManager.print( CSAP.pad( "source" ) + activeDefinitionFolder.getAbsolutePath( ) ) ;
				outputManager.print( CSAP.pad( "destination" ) + defWorkingFolder.getAbsolutePath( ) ) ;

			} else {

				outputManager.print( "\n **WARNING: only git supports the update all option" ) ;

			}

		}

	}

	private void activate_definition (
										OutputFileMgr outputManager ,
										StringBuilder parsingResultsBuffer ,
										boolean isApplyOnly ,
										File workingPackageDefinitionFile ,
										String scmBranch ,
										File workingApplicationFolder ,
										String scmUserid ,
										String encryptedPass ,
										String comment ,
										String csapProjectName ,
										File globalModelBuildFolder ,
										String selectedConfig )
		throws IOException ,
		Exception {

		outputManager.print( CsapApplication.header( "Activating application definition: " + workingApplicationFolder
				.getPath( ) ) ) ;

		if ( parsingResultsBuffer.indexOf( CsapCore.CONFIG_PARSE_WARN ) != -1 ) {

			csapApp.updateOutputWithLimitedInfo( CsapCore.CONFIG_PARSE_WARN, 25, outputManager,
					parsingResultsBuffer, null ) ;

		}

		if ( ! isApplyOnly ) {

			logger.info( "checking updated config into source control:"
					+ workingPackageDefinitionFile.getAbsolutePath( ) ) ;

			// Only used if new package has been added
			String scmLocation = csapApp.getRootProjectDefinitionUrl( ) ;

			if ( csapApp.getSourceType( ).equals( VersionControl.ScmProvider.svn.key ) ) {

				scmLocation = csapApp.getRootProjectDefinitionUrl( ).replace( "trunk", scmBranch ) ;

			}

			pendingDefinitionManager.apply_to_checked_out_folder( outputManager.getBufferedWriter( ),
					workingApplicationFolder ) ;

			var templateExtractionFolder = new File( workingApplicationFolder, DefinitionConstants.projectsExtracted
					.key( ) ) ;

			printGitHead( outputManager, workingApplicationFolder, scmBranch ) ;

			var isExtractFolderRemoved = FileUtils.deleteQuietly( templateExtractionFolder ) ;

			if ( isExtractFolderRemoved ) {

				outputManager
						.print( CSAP.padLine( "deleting" ) + templateExtractionFolder.getPath( ) ) ;

			} else {

				logger.info( "skipped {}", templateExtractionFolder.getPath( ) ) ;

			}

			sourceControlManager.checkInFolder(
					VersionControl.ScmProvider.parse( csapApp.getSourceType( ) ),
					scmLocation, workingPackageDefinitionFile,
					scmUserid, encryptedPass,
					scmBranch, comment,
					outputManager.getBufferedWriter( ) ) ;

			updateWorkingGitSource( outputManager, scmBranch, workingApplicationFolder ) ;

			pendingDefinitionManager.clearAll( ) ;

			String details = "\nFull Path: " + workingPackageDefinitionFile.getAbsolutePath( ) ;
			details += "\n Comments: " + comment ;
			csapEventClient.publishUserEvent( CsapEvents.CSAP_OS_CATEGORY + "/definition/checkin",
					CsapUser.currentUsersID( ),
					csapProjectName, details ) ;

		} else {

			logger.warn( "Applying definition changes to the file system without checking in."
					+ " Changes will be lost if definion reload occures, or edits on another admin instance" ) ;

			pendingDefinitionManager.apply_to_checked_out_folder( outputManager.getBufferedWriter( ),
					workingApplicationFolder ) ;

			if ( csapApp.getRootProjectDefinitionUrl( ).contains( "update-with-your-repo" ) ) {

				logger.warn( "Git is NOT configured: '{}'. Clearing pending operations", csapApp
						.getRootProjectDefinitionUrl( ) ) ;
				pendingDefinitionManager.clearAll( ) ;

			}

			csapEventClient.publishUserEvent( CsapEvents.CSAP_OS_CATEGORY + "/definition/apply",
					CsapUser.currentUsersID( ),
					csapProjectName, "No check in, warning." ) ;

		}

		// Finally - lets reload
		File liveConfigFile = new File( globalModelBuildFolder, selectedConfig ) ;

		// create the scm folder if it does not exist - would occur
		// on a new host
		if ( ! liveConfigFile.getParentFile( ).exists( ) ) {

			logger.warn( "Did not find application definition: " + liveConfigFile.getAbsolutePath( ) ) ;

		}

		// StringBuilder output = new StringBuilder();
		// csapApp.move_to_csap_saved_folder( globalModelBuildFolder, output );
		// outputManager.print( output.toString() );

		outputManager.print( CSAP.padLine( "Deleting BuildFolder" ) + globalModelBuildFolder.getAbsolutePath( ) ) ;
		FileUtils.deleteQuietly( globalModelBuildFolder ) ;

		outputManager.print( CSAP.padLine( "Copying to BuildFolder" ) + workingApplicationFolder.getAbsolutePath( ) ) ;
		FileUtils.copyDirectory( workingApplicationFolder, globalModelBuildFolder ) ;

		// results pushed directly onto httpResponseBuffeer
		activateDefinitionAndTransfer(
				csapApp.getProject( csapProjectName ),
				liveConfigFile.getParentFile( ), parsingResultsBuffer,
				outputManager, comment ) ;

	}

	private void updateWorkingGitSource (
											OutputFileMgr outputManager ,
											String scmBranch ,
											File workingApplicationFolder )
		throws IOException {

		var workingHeadFile = printGitHead( outputManager, workingApplicationFolder, scmBranch ) ;
		File activeHeadFile = new File( workingApplicationFolder, GIT_SOURCE ) ;
		outputManager.print( CSAP.padLine( "Creating" ) + activeHeadFile.getAbsolutePath( ) ) ;
		FileUtils.copyFile( workingHeadFile, activeHeadFile ) ;

	}

	private File printGitHead ( OutputFileMgr outputManager , File workingApplicationFolder , String scmBranch ) {

		File gitHeadFile = new File( workingApplicationFolder, ".git/refs/heads/" + scmBranch ) ;
		outputManager.print( CSAP.padLine( "gitHeadFile" ) + gitHeadFile.getAbsolutePath( ) ) ;
		outputManager.print( CSAP.padLine( "content" ) + Application.readFile( gitHeadFile ) ) ;
		return gitHeadFile ;

	}

	private Lock configLock = new ReentrantLock( ) ;

	private String configLockMessage = "" ;

	/**
	 * Method for getting the latest server from CVS
	 */

	public final static String APPLICATION_RELOAD = "/applicationReload" ;

	@PostMapping ( APPLICATION_RELOAD )
	synchronized public ObjectNode application_reload (
														@RequestParam ( "scmUserid" ) String scmUserid ,
														@RequestParam ( "scmPass" ) String rawPass ,
														@RequestParam ( "scmBranch" ) String scmBranch ,
														@RequestParam ( value = CsapCore.SERVICE_PORT_PARAM , required = false ) String svcName )
		throws Exception {

		String scmPass = encryptor.encrypt( rawPass ) ; // immediately encrypt
		// pass

		StringBuilder results = new StringBuilder( ) ;

		OutputFileMgr outputManager = new OutputFileMgr( csapApp.getCsapWorkingFolder( ),
				APPLICATION_RELOAD ) ;

		logger.info( "scmUserid: " + scmUserid + " scmBranch: " + scmBranch + " svcName: " + svcName ) ;

		String buildItem = " User: " + scmUserid + " Service: " + svcName ;

		if ( configLock.tryLock( ) ) {

			try {

				configLockMessage = buildItem ;
				boolean checkOutSuccess = reloadUsingSourceControl(
						scmUserid, scmPass, scmBranch,
						svcName, outputManager.getBufferedWriter( ) ) ;

				if ( checkOutSuccess ) {

					reloadParseAndStore( scmUserid, scmBranch, results, outputManager ) ;
					csapApp.run_application_scan( ) ;
					pendingDefinitionManager.clearAll( ) ;

				} else {

					results.append( "CS-AP Reload error - check out Failed: " + outputManager.getContents( ) ) ;

				}

			} catch ( Throwable e ) {

				results.append( "CS-AP Reload error - got an exception due to cluster reload. Please try again" ) ;
				logger.error( "Failed to reload", e ) ;

			} finally {

				configLock.unlock( ) ;
				configLockMessage = "" ;

			}

		} else {

			results.append( CsapCore.CONFIG_PARSE_WARN
					+ "\nPlease try again in a few minutes, reload already in progress on host: "
					+ configLockMessage ) ;

		}

		outputManager.close( ) ;

		ObjectNode resultJson = jsonMapper.createObjectNode( ) ;
		resultJson.put( DockerJson.response_plain_text.json( ), outputManager.getContents( ) ) ;

		return resultJson ;

	}

	private void reloadParseAndStore (
										String scmUserid ,
										String scmBranch ,
										StringBuilder results ,
										OutputFileMgr outputMgr )
		throws IOException {

		File rootApplicationDefinition = new File( csapApp.getRootModelBuildLocation( )
				+ "/" + csapApp.applicationDefinition( ).getName( ) ) ;

		try {

			StringBuilder resultBuf = csapApp.getProjectLoader( ).process( true, rootApplicationDefinition ) ;

			if ( ( resultBuf != null )
					&& resultBuf.indexOf( CsapCore.CONFIG_PARSE_ERROR ) == -1 ) {

				// results are auto appended to resultsBuf
				updateWorkingGitSource( outputMgr, scmBranch, rootApplicationDefinition.getParentFile( ) ) ;

				activateDefinitionAndTransfer(
						csapApp.getRootProject( ),
						rootApplicationDefinition.getParentFile( ), resultBuf, outputMgr,
						"Restoring definition from source control system." ) ;

			} else {

				logger.error( "Failed to parse" ) ;

				if ( ( resultBuf != null ) ) {

					outputMgr.print( "-" ) ;
					outputMgr.print(
							"\n\n =============  Parsing file errors \n\n" + resultBuf ) ;

					outputMgr.print( "-" ) ;

					outputMgr.print( "-" ) ;
					outputMgr.print( "\n\n============= Found Semantic Errors !! ====================\n"
							+ "Filtered output for :"
							+ CsapCore.CONFIG_PARSE_ERROR ) ;
					List<String> parmList = new ArrayList<String>( ) ;
					Collections
							.addAll( parmList,
									"bash",
									"-c",
									"diff  " + csapApp.applicationDefinition( ).getAbsolutePath( ) + " "
											+ rootApplicationDefinition.getAbsolutePath( ) ) ;
					results.append( sourceControlManager.executeShell( parmList,
							outputMgr.getBufferedWriter( ) ) ) ;

					csapApp.updateOutputWithLimitedInfo( CsapCore.CONFIG_PARSE_ERROR, 999, outputMgr, resultBuf,
							null ) ;

				}

			}

		} catch ( Exception e1 ) {

			logger.error( "Definition reload failed: {}", CSAP.buildCsapStack( e1 ) ) ;
			// List<String> parmList = new ArrayList<String>();
			// Collections.addAll( parmList, "bash", "-c",
			// "diff " + csapApp.getDefinitionFile().getAbsolutePath()
			// + " " + jsConfigFile.getAbsolutePath() );
			// results.append( sourceControlManager.executeShell( parmList,
			// outputMgr.getBufferedWriter() ) );

			outputMgr.print( CsapCore.CONFIG_PARSE_ERROR +
					" Failed to load updated definition: " + CSAP.buildCsapStack( e1 ) ) ;
			// Application.getCustomStackTrace(e1)
			outputMgr.print( "===== ACTION Required: Fix the error, reload config" ) ;

		}

	}

	/**
	 * Currently svn Update
	 *
	 *
	 * @param scmUserid
	 * @param scmPass
	 * @param scmBranch
	 * @param requireXml
	 * @param svcName
	 * @param request
	 * @param response
	 * @return
	 * @throws IOException
	 */
	private boolean reloadUsingSourceControl (
												String scmUserid ,
												String encryptedPass ,
												String scmBranch ,
												String svcName ,
												BufferedWriter outputWriter )
		throws IOException {

		try {

			ServiceInstance definitionInstance = new ServiceInstance( ) ;
			definitionInstance.setScmLocation( csapApp.getRootProjectDefinitionUrl( ) ) ;
			definitionInstance.setScm( csapApp.getSourceType( ) ) ;

			File definitionFolder = new File( csapApp.getRootModelBuildLocation( ) ) ;

			// back_up_to_csap_saved( clusterFileName, outputWriter );
			StringBuilder output = new StringBuilder( ) ;
			csapApp.move_to_csap_saved_folder( definitionFolder, output ) ;
			outputWriter.append( output.toString( ) ) ;

			sourceControlManager.checkOutFolder(
					scmUserid, encryptedPass, scmBranch,
					definitionFolder.getName( ),
					definitionInstance, outputWriter ) ;

		} catch ( TransportException gitException ) {

			logger.error( "Definition reload failed: {}", CSAP.buildCsapStack( gitException ) ) ;

			outputWriter.write( "\n\n" + CsapCore.CONFIG_PARSE_ERROR
					+ "Git Access Error: Verify credentials and path is correct:\n" + gitException.getMessage( ) ) ;

			return false ;

		} catch ( Exception e ) {

			logger.error( "Definition reload failed: {}", CSAP.buildCsapStack( e ) ) ;

			outputWriter.write( "\n\n" + CsapCore.CONFIG_PARSE_ERROR
					+ "SVN Failure: Verify password and target is correct:\n" + e ) ;

			if ( e.toString( ).indexOf( "is already a working copy for a different URL" ) != -1 ) {

				File svnCheckoutFolder = csapApp.getCsapBuildFolder( svcName ) ;
				outputWriter.write( "Blowing away previous build folder, try again:"
						+ svnCheckoutFolder ) ;
				FileUtils.deleteQuietly( svnCheckoutFolder ) ;

			}

			return false ;

		}

		return true ;

	}

	private void activateDefinitionAndTransfer (
													Project project ,
													File workingFolder ,
													StringBuilder resultBuf ,
													OutputFileMgr outputMgr ,
													String comment )
		throws IOException {

		StringBuilder results = new StringBuilder( ) ;

		File liveDefinitionFolder = csapApp.getDefinitionFolder( ) ;

		logger.info( "Parsing file success: \n" + resultBuf ) ;

		outputMgr.print( CsapApplication.header( "Activating Application Definition" ) ) ;
		outputMgr.print( CSAP.padLine( "Updating" ) + csapApp.applicationDefinition( ).getAbsolutePath( ) ) ;

//		outputMgr.print( "\n\n =============  Parsing file success,  Overwriting: "
//				+ csapApp.applicationDefinition().getAbsolutePath() + "\n\n" ) ;

		if ( Application.isRunningOnDesktop( ) && ! csapApp.isTestModeToSkipActivate( ) ) {

			File testLocation = new File( workingFolder.getParentFile( ), workingFolder.getName( ) + ".desktop" ) ;

			FileUtils.deleteQuietly( testLocation ) ;
			logger.warn( "Exiting early on desktop. Filtered copy location: {}", testLocation ) ;
			FileUtils.copyDirectory( workingFolder, testLocation, getGitFilter( ) ) ;

			outputMgr.print( "Copying checked out location: " + workingFolder.getAbsolutePath( )
					+ " to testLocation: " + testLocation ) ;

			sendEmailToInfraAdmin(
					project,
					"Updated definition, diff is attached. Refer to source history for more information",
					"Exiting early on desktop ===> " + comment,
					resultBuf.toString( ),
					"resultBuf.txt" ) ;
			outputMgr.print( CsapApplication.header( "WARNING: Desktop detected, skipping reload and transfer" ) ) ;
			return ;

		}

		StringBuffer commandResultsBuf = new StringBuffer(
				"\n\n========================= saveAndLoadConfig ====================\n" ) ;

		// Folder level comparison via OS
		List<String> parmList = new ArrayList<String>( ) ;
		Collections.addAll(
				parmList,
				"bash",
				"-c",
				"diff  " + liveDefinitionFolder.getAbsolutePath( ) + " "
						+ workingFolder.getAbsolutePath( ) ) ;

		commandResultsBuf.append(
				sourceControlManager
						.executeShell( parmList, outputMgr.getBufferedWriter( ) ) ) ;

		logger.info( "Diff results in output buffer" ) ;

		String emailResults = sendEmailToInfraAdmin(
				project,
				"Updated definition, diff is attached. Refer to source history for more information",
				comment,
				commandResultsBuf.toString( ),
				project.getName( ) + "_diff.txt" ) ;

		outputMgr.print( emailResults ) ;

		results.append( commandResultsBuf ) ; // only adding the diff, others

		// back_up_to_csap_saved( liveDefinitionFolder,
		// outputMgr.getBufferedWriter() );
		StringBuilder output = new StringBuilder( ) ;
		csapApp.move_to_csap_saved_folder( liveDefinitionFolder, output ) ;
		outputMgr.print( output.toString( ) ) ;

		liveDefinitionFolder.mkdir( ) ;

		// FileUtils.copyDirectory( updatedDefinitionFile.getParentFile(),
		// liveDefinitionFolder );
		outputMgr.print( "Copying checked out location: " + workingFolder.getAbsolutePath( )
				+ " to live location: " + liveDefinitionFolder.getAbsolutePath( ) ) ;
		FileUtils.copyDirectory( workingFolder, liveDefinitionFolder, getGitFilter( ) ) ;

		if ( csapApp.isAdminProfile( ) ) {

			TransferManager transferManager = new TransferManager( csapApp, 120, outputMgr.getBufferedWriter( ) ) ;

			transferManager.setDeleteExisting( true ) ;

			transferManager.httpCopyViaCsAgent( CsapUser.currentUsersID( ),
					liveDefinitionFolder,
					csapApp.getDefinitionToken( ),
					new ArrayList<String>( csapApp
							.getAllHostsInAllPackagesInCurrentLifecycle( ) ) ) ;

			// in case of agent password update is there a race condition?
			String transferResults = transferManager.waitForComplete( ) ;

			if ( transferResults.contains( CsapCore.CONFIG_PARSE_ERROR ) ) {

				results.append( transferResults ) ;

			}

			commandResultsBuf.append( transferResults ) ;

		} else {

			outputMgr.print( "\n Running on Agent - Definition has ONLY been updated on: "
					+ csapApp.getCsapHostName( ) ) ;
			commandResultsBuf.append( "\n Running on Agent - Definition has ONLY been updated on: "
					+ csapApp.getCsapHostName( ) ) ;

		}

		commandResultsBuf
				.append( "\n\n ============       definitions updated, reloads will occur within 60 seconds \n\n" ) ;

		// Scince not all output is logged to console, we dump to logs
		// if (logger.isDebugEnabled())
		// logger.debug(commandResultsBuf);
		csapEventClient.publishUserEvent(
				CsapEvents.CSAP_OS_CATEGORY + "/definition/reload",
				CsapUser.currentUsersID( ),
				"Definition reloaded",
				commandResultsBuf.toString( ) ) ;

		outputMgr
				.print( "\n\n ============       definitions updated, reloads will occur within 60 seconds \n\n" ) ;

		if ( resultBuf.indexOf( CsapCore.CONFIG_PARSE_WARN ) != -1 ) {

			csapApp.updateOutputWithLimitedInfo( CsapCore.CONFIG_PARSE_WARN, 25, outputMgr, resultBuf, null ) ;

		}

		return ;

	}

	@Inject
	SpringTemplateEngine springTemplateEngine ;

	public static String EMAIL_DISABLED = "Email notifications disabled" ;

	private String sendEmailToInfraAdmin (
											Project currentPackageModel ,
											String message ,
											String comment ,
											String attachment ,
											String attachmentName ) {

		String results = "emailNotifications: " ;
		// CustomUserDetails person = CsapUser.securityUser();

		try {

			// TemplateEngine engine = new TemplateEngine();
			// engine.addTemplateResolver( templateResolver );
			Context context = new Context( ) ;
			context.setVariable( "name", CsapUser.currentUsersID( ) ) ;
			context.setVariable( "appUrl", csapApp.rootProjectEnvSettings( ).getLoadbalancerUrl( ) ) ;
			context.setVariable( "sourceUrl", csapApp.getRootProjectDefinitionUrl( ) ) ;
			context.setVariable( "life", csapApp.getCsapHostEnvironmentName( ) ) ;
			context.setVariable( "package", currentPackageModel.getName( ) ) ;
			context.setVariable( "message", message ) ;
			context.setVariable( "comment", comment ) ;
			String testBody = springTemplateEngine.process( "infraEmail", context ) ;

			logger.info( "{} package {} : \n\t to: {}\n\t message: {}",
					csapApp.getName( ), currentPackageModel.getName( ), currentPackageModel.getEmailNotifications( ),
					message ) ;

			if ( ( csapMailSender == null ) || ( currentPackageModel.getEmailNotifications( ) == null ) ) {

				results += EMAIL_DISABLED ;
				logger.warn(
						"Email notifications are not configured. Ensure that mail server is in application.yml and Application.json contacts are configured." ) ;

			} else {

				results += "Email has been sent to: " + currentPackageModel.getEmailNotifications( ) ;
				csapMailSender.send( mimeMessage -> {

					MimeMessageHelper messageHelper = new MimeMessageHelper( mimeMessage, true, "UTF-8" ) ;
					messageHelper.setTo( currentPackageModel.getEmailNotifications( ).split( "," ) ) ;

					if ( ! currentPackageModel.getEmailNotifications( ).contains( CsapUser
							.currentUsersEmailAddress( ) ) ) {

						messageHelper.setCc( CsapUser.currentUsersEmailAddress( ) ) ;

					}

					messageHelper.setFrom( CsapUser.currentUsersEmailAddress( ) ) ;
					messageHelper.setSubject( "CSAP Notification: " + csapApp.getName( ) ) ;
					messageHelper.setText( testBody, true ) ;
					messageHelper.addAttachment( attachmentName,
							new ByteArrayResource( attachment.getBytes( ) ) ) ;

				} ) ;

			}

		} catch ( Exception e ) {

			results += "Failed to notify, contact your administrator for assistance: "
					+ e.getMessage( ) ;

			logger.error( "Failed to send message - verify settings in application.yml;  Error: \n {}",
					CSAP.buildCsapStack( e ) ) ;

		}

		return results ;

	}

	@RequestMapping ( value = CsapCoreService.NOTIFY_URL , produces = MediaType.APPLICATION_JSON_VALUE , method = RequestMethod.POST )
	public ObjectNode notifyAdmin (
									@RequestParam ( "itemName" ) String itemName ,
									@RequestParam ( "hostName" ) String hostName ,
									@RequestParam ( value = CsapCore.PROJECT_PARAMETER , required = false ) String csapProjectName ,
									@RequestParam ( "message" ) String message ,
									@RequestParam ( "definition" ) String definition ) {

		ObjectNode updateResultNode = jsonMapper.createObjectNode( ) ;

		logger.debug( "Sending email to admin: {}", message ) ;

		Project currentPackageModel = null ;

		if ( csapProjectName == null ) {

			currentPackageModel = csapApp.getModel( hostName, itemName ) ;

		} else {

			currentPackageModel = csapApp.getProject( csapProjectName ) ;

		}

		updateResultNode.put( "Results",
				sendEmailToInfraAdmin( currentPackageModel, message,
						"Admin: Please review request and contact the user", definition,
						itemName + ".json" ) ) ;

		return updateResultNode ;

	}

	private FileFilter getGitFilter ( ) {

		FileFilter gitFilter = new FileFilter( ) {
			public boolean accept ( File file ) {

				logger.debug( "name: {}, dir: {}", file.getName( ), file.isDirectory( ) ) ;
				if ( file.getName( ).startsWith( "." ) )
					return false ;
				return true ;

			}
		} ;
		return gitFilter ;

	}

}
