package org.csap.agent.ui.editor ;

import static org.csap.agent.model.DefinitionConstants.serviceResourcesFolder ;

import java.io.BufferedReader ;
import java.io.File ;
import java.io.FileReader ;
import java.io.IOException ;
import java.util.ArrayList ;
import java.util.Arrays ;
import java.util.HashSet ;
import java.util.List ;
import java.util.regex.Pattern ;

import org.apache.commons.lang3.StringUtils ;
import org.csap.agent.model.Application ;
import org.csap.agent.model.DefinitionConstants ;
import org.csap.agent.model.ServiceAttributes ;
import org.csap.helpers.CSAP ;
import org.csap.helpers.CsapApplication ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;

import com.fasterxml.jackson.databind.ObjectMapper ;
import com.fasterxml.jackson.databind.node.ArrayNode ;
import com.fasterxml.jackson.databind.node.ObjectNode ;

public class ServiceResources {

	static final Logger logger = LoggerFactory.getLogger( ServiceResources.class ) ;

	private File csapPlatformLocation ;
	private File working_folder ;
	private File checkout_folder ;
	static File active_folder ;
	private ObjectMapper jacksonMapper ;

	private static boolean junitMode = false ;

	public ServiceResources ( String definitionName, File csapPlatformLocation, ObjectMapper jacksonMapper ) {

		junitMode = false ; // critical
							// for unit
							// tests
							// this is
							// reset

		ServiceResources.active_folder = new File(
				csapPlatformLocation + "/" + CsapApplication.DEFINITION_FOLDER_NAME + "/" + serviceResourcesFolder
						.key( ) + "/" ) ;
		logger.info( "Initialized resource folder: {}", ServiceResources.active_folder.getAbsolutePath( ) ) ;

		this.csapPlatformLocation = csapPlatformLocation ;
		this.checkout_folder = new File( csapPlatformLocation,
				"build/" + definitionName + "/" + serviceResourcesFolder.key( ) + "/" ) ;
		this.working_folder = new File( csapPlatformLocation, "build/" + serviceResourcesFolder.key( ) + "/" ) ;
		this.jacksonMapper = jacksonMapper ;

	}

	public File workingFolder ( ) {

		return working_folder ;

	}

	public File getBuildPropertyOverrideFolder ( ) {

		if ( junitMode ) {

			return checkout_folder ;

		}

		return new File( Application.getInstance( ).getRootModelBuildLocation( ) + "/" + serviceResourcesFolder.key( )
				+ "/" ) ;

	}

	public File checkedOutFolderRoot ( ) {

		return getBuildPropertyOverrideFolder( ).getParentFile( ) ;

	}

	public File checkedOutFolderService ( String serviceName ) {

		// return new File( getBuildPropertyOverrideFolder(), serviceName + "/resources"
		// ) ;
		return new File( getBuildPropertyOverrideFolder( ), serviceName ) ;

	}

	public File workingFolder ( String serviceName ) {

		// return new File( working_folder, serviceName + "/resources" ) ;
		return new File( working_folder, serviceName ) ;

	}

	static boolean isPrintedOnce = false ;

	public static File serviceResourceFolder ( String serviceName ) {

		return serviceResourceTemplateFolder( "", serviceName ) ;

	}

	public static File serviceResourceTemplateFolder ( String template , String serviceName ) {

		var templateFolder = "" ;

		if ( StringUtils.isNotEmpty( template ) ) {

			templateFolder = template + "/" ;

		}

		// var fullpath = templateFolder +
		// DefinitionConstants.serviceResourcesFolder.key() + "/" + serviceName +
		// "/resources" ;
		var fullpath = templateFolder + DefinitionConstants.serviceResourcesFolder.key( ) + "/" + serviceName ;

		var resourceFolder = new File( Application.getInstance( ).getDefinitionFolder( ), fullpath ) ;

		if ( ! CsapApplication.isCsapFolderSet( ) ) {

			// JUNIT testing
			if ( ( ! junitMode ) && Application.getInstance( ).isDesktopProfileActive( ) ) {

				if ( ! isPrintedOnce ) {

					isPrintedOnce = true ;
					logger.warn( "reverting to definition for desktop testing" ) ;

				}

			} else {

				// resourceFolder = new File( active_folder, serviceName + "/resources" ) ;
				resourceFolder = new File( active_folder, serviceName ) ;

			}

		}

		return resourceFolder ;

	}

	public void addResourceFilesToDefinition (
												ObjectNode serviceNode ,
												String serviceName ,
												String filterEnv ,
												String filterName ) {

		HashSet<String> loadedFiles = new HashSet<>( ) ;
		ArrayList<File> allServiceFiles = new ArrayList<>( ) ;
		// String targetPath = serviceName + "/resources/" ;
		String targetPath = serviceName + "/" ;
		File serviceWorking = new File( workingFolder( ), targetPath ) ;

		// get all working - then the current files
		allServiceFiles.addAll( findAllFilesRecursively( serviceWorking ) ) ;
		allServiceFiles.addAll( findAllFilesRecursively( serviceResourceFolder( serviceName ) ) ) ;

		logger.debug(
				"Adding files from working folder: {}; if not there - then activeFolder {} to \n\t definition: {}",
				serviceWorking,
				serviceResourceFolder( serviceName ),
				allServiceFiles ) ;

		allServiceFiles.stream( )

				.forEach( resourceFile -> {

					if ( ! serviceNode.has( DefinitionRequests.DEFINITION_FILES ) ) {

						serviceNode.putArray( DefinitionRequests.DEFINITION_FILES ) ;

					}

					ArrayNode serviceFiles = (ArrayNode) serviceNode.get( DefinitionRequests.DEFINITION_FILES ) ;
					String resourceRelativePath = resourceFile.getParentFile( ).getName( ) ;

					String fullPathLife = resourceFile.getParentFile( ).getAbsolutePath( ) ;

					if ( fullPathLife.startsWith( serviceWorking.getAbsolutePath( ) ) ) {

						resourceRelativePath = fullPathLife.substring( serviceWorking.getAbsolutePath( ).length( ) ) ;

					} else if ( fullPathLife.startsWith( serviceResourceFolder( serviceName ).getAbsolutePath( ) ) ) {

						resourceRelativePath = fullPathLife.substring( serviceResourceFolder( serviceName )
								.getAbsolutePath( ).length( ) ) ;

					}

					if ( StringUtils.isEmpty( resourceRelativePath ) ) {

						resourceRelativePath = "." ;

					}

					// handle windows
					resourceRelativePath = resourceRelativePath.replaceAll( Pattern.quote( "\\" ), "/" ) ;

					// make paths relative to resource folder
					if ( resourceRelativePath.length( ) >= 2 && resourceRelativePath.startsWith( "/" ) ) {

						resourceRelativePath = resourceRelativePath.substring( 1 ) ;

					}

					// merge from working if it exists
					var resourceId = resourceRelativePath + "/" + resourceFile.getName( ) ;

					if ( ! loadedFiles.contains( resourceId ) ) {

						loadedFiles.add( resourceId ) ;
						serviceFiles.add( buildSeviceResourceFile( resourceFile, serviceName, resourceRelativePath,
								filterEnv, filterName ) ) ;

					}

				} ) ;

	}

	private List<File> findAllFilesRecursively ( File folder ) {

		if ( ! folder.isDirectory( ) ) {

			return new ArrayList<File>( ) ;

		}

		File[] folderItems = folder.listFiles( ) ;

		if ( folderItems == null ) {

			return new ArrayList<File>( ) ;

		}

		List<File> foundItems = new ArrayList<>( ) ;

		for ( File item : Arrays.asList( folderItems ) ) {

			if ( item.isDirectory( ) ) {

				foundItems.addAll( findAllFilesRecursively( item ) ) ;

			} else if ( item.isFile( ) ) {

				foundItems.add( item ) ;

			}

		}

		// String info = foundItems.stream().map( file -> file.isFile() + " - " +
		// file.getAbsolutePath() ).collect( Collectors.joining("\n")
		// ) ;
		// logger.debug( "details: {}", info );
		// .stream().filter( File::isFile ).collect( Collectors.toList() )
		// List<File> filteredItems = foundItems.stream().filter( file ->
		// file.isFolder() ).collect( Collectors.toList() ) ;
		// filteredItems.addAll( filteredItems ) ;

		return foundItems ;

	}

	private ObjectNode buildSeviceResourceFile (
													File resourceFile ,
													String serviceName ,
													String resourcePath ,
													String filterEnv ,
													String filterName ) {

		//
		ObjectNode propertyNode = jacksonMapper.createObjectNode( ) ;
		String targetName = resourceFile.getName( ) ;

		propertyNode.put( ServiceAttributes.FileAttributes.name.json, targetName ) ;
		propertyNode.put( ServiceAttributes.FileAttributes.lifecycle.json, resourcePath ) ;
		propertyNode.put( ServiceAttributes.FileAttributes.external.json, "true" ) ;

		// defer: lazy load for both speed and conserving updates
		if ( StringUtils.isNotEmpty( filterEnv )
				&& resourcePath.matches( filterEnv )
				&& targetName.matches( filterName ) ) {

			propertyNode.set( ServiceAttributes.FileAttributes.content.json, buildFileContent( resourceFile ) ) ;

		}
		//

		return propertyNode ;

	}

	public ArrayNode buildFileContent ( File resourceFile ) {

		ArrayNode content = jacksonMapper.createArrayNode( ) ;

		try ( BufferedReader br = new BufferedReader( new FileReader( resourceFile ) ) ) {

			String currentLine ;

			while ( ( currentLine = br.readLine( ) ) != null ) {

				if ( resourceFile.getName( ).endsWith( ".yml" ) ||
						resourceFile.getName( ).endsWith( ".yaml" ) ) {

					var line = currentLine ;

					if ( ! currentLine.contains( "$$" ) ) {

						// legacy migrations skipped if $$ found
						line = Application.getInstance( )
								.getProjectLoader( ).getProjectMigrator( )
								.migrateServiceVariables( currentLine ) ;

					}

					content.add( line ) ;

				} else {

					content.add( currentLine ) ;

				}

				// yaml = csapApp ;
			}

		} catch ( IOException e ) {

			logger.error( "Failed reading file: {}, {}", resourceFile, CSAP.buildCsapStack( e ) ) ;

		}

		return content ;

	}

	public File getCsapPlatformLocation ( ) {

		return csapPlatformLocation ;

	}

	public void setCsapPlatformLocation ( File csapPlatformLocation ) {

		this.csapPlatformLocation = csapPlatformLocation ;

	}

	public static File getActiveProperyOverrideFolder ( ) {

		return active_folder ;

	}

	public boolean isJunitMode ( ) {

		return junitMode ;

	}

	public void setJunitMode ( boolean junitMode ) {

		this.junitMode = junitMode ;

	}

}
