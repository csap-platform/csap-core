package org.csap.agent.ui.editor ;

import static org.csap.agent.model.DefinitionConstants.serviceResourcesFolder ;

import java.io.BufferedWriter ;
import java.io.File ;
import java.io.IOException ;
import java.nio.file.Files ;
import java.nio.file.StandardCopyOption ;

import org.apache.commons.io.FileUtils ;
import org.csap.helpers.CSAP ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;

public class PendingOperations {

	final Logger logger = LoggerFactory.getLogger( this.getClass( ) ) ;

	ServiceResources serviceResources ;

	PendingOperations ( ServiceResources serviceResources ) {

		this.serviceResources = serviceResources ;

	}

	abstract class FileOperation {

		abstract String apply_to_checked_out_folder ( BufferedWriter writer , File checked_out_definition )
			throws Exception ;

	}

	public class ServiceDelete extends FileOperation {
		String eolServiceName ;

		public ServiceDelete ( String eolServiceName ) {

			super( ) ;
			this.eolServiceName = eolServiceName ;

		}

		@Override
		String apply_to_checked_out_folder ( BufferedWriter writer , File checked_out_definition )
			throws Exception {

			String result ;
			File checked_out_service = new File( checked_out_definition, serviceResourcesFolder.key( ) + "/"
					+ eolServiceName ) ;

			logger.debug( "checking: {}", checked_out_service.getAbsolutePath( ) ) ;
			// writer.write( "\n Checking if service resource folder exists: " +
			// checked_out_service.getAbsolutePath() ) ;

			if ( checked_out_service.exists( ) ) {

				result = "Deleteing:" + checked_out_service.getAbsolutePath( ) ;
				FileUtils.deleteQuietly( checked_out_service ) ;

			} else {

				result = "\n no resources found, ignoring delete request: " + checked_out_service.getAbsolutePath( ) ;

			}

			return result ;

		}

		@Override
		public String toString ( ) {

			return "ServiceDelete [eolServiceName=" + eolServiceName + "]" ;

		}

	}

	public class ServiceCopy extends FileOperation {
		String currentName ;
		String copyName ;

		public ServiceCopy ( String currentName, String copyName ) {

			super( ) ;
			this.currentName = currentName ;
			this.copyName = copyName ;

		}

		@Override
		String apply_to_checked_out_folder ( BufferedWriter writer , File checked_out_definition )
			throws Exception {

			String result ;
			File checked_out__source_service = new File( checked_out_definition, serviceResourcesFolder.key( ) + "/"
					+ currentName ) ;

			logger.debug( "checking: {}", checked_out__source_service.getAbsolutePath( ) ) ;
			// writer.write( "\n Checking if service resource folder exists: " +
			// checked_out_service.getAbsolutePath() ) ;

			if ( checked_out__source_service.exists( ) ) {

				File copyLocation = new File( checked_out_definition, serviceResourcesFolder.key( ) + "/" + copyName ) ;

				if ( copyLocation.exists( ) ) {

					result = "Note target location already exists (assumed apply). Deleteing:"
							+ checked_out__source_service.getAbsolutePath( ) ;
					// FileUtils.deleteQuietly( checked_out__source_service ) ;

				} else {

					result = "\n moving service folder: "
							+ checked_out__source_service.getAbsolutePath( )
							+ "\t to: " + copyLocation.getAbsolutePath( ) ;

					try {

						CSAP.copyFolder( checked_out__source_service.toPath( ), copyLocation.toPath( ) ) ;

						// Files.copy( checked_out__source_service.toPath(), copyLocation.toPath(),
						// StandardCopyOption.REPLACE_EXISTING ) ;
					} catch ( IOException e ) {

						result = "FAILED: " + result ;
						logger.error( "service copy error: {}", CSAP.buildCsapStack( e ) ) ;

					}

				}

			} else {

				result = "\n no resources found, ignoring: " + checked_out__source_service.getAbsolutePath( ) ;

			}

			return result ;

		}

		@Override
		public String toString ( ) {

			return "ServiceCopy [currentName=" + currentName + ", copyName=" + copyName + "]" ;

		}

	}

	public class ServiceRename extends FileOperation {
		String currentName ;
		String newName ;

		public ServiceRename ( String currentName, String newName ) {

			super( ) ;
			this.currentName = currentName ;
			this.newName = newName ;

		}

		@Override
		String apply_to_checked_out_folder ( BufferedWriter writer , File checked_out_definition )
			throws Exception {

			String result ;
			File checked_out_service = new File( checked_out_definition, serviceResourcesFolder.key( ) + "/"
					+ currentName ) ;

			logger.debug( "checking: {}", checked_out_service.getAbsolutePath( ) ) ;
			// writer.write( "\n Checking if service resource folder exists: " +
			// checked_out_service.getAbsolutePath() ) ;

			if ( checked_out_service.exists( ) ) {

				File newLocation = new File( checked_out_definition, serviceResourcesFolder.key( ) + "/" + newName ) ;

				if ( newLocation.exists( ) ) {

					result = "Note target location already exists (assumed apply). Deleteing:" + checked_out_service
							.getAbsolutePath( ) ;
					FileUtils.deleteQuietly( checked_out_service ) ;

				} else {

					result = "\n moving service folder: "
							+ checked_out_service.getAbsolutePath( )
							+ "\t to: " + newLocation.getAbsolutePath( ) ;

					try {

						Files.move( checked_out_service.toPath( ), newLocation.toPath( ),
								StandardCopyOption.REPLACE_EXISTING ) ;

					} catch ( IOException e ) {

						result = "FAILED: " + result ;
						logger.error( "service move error: {}", CSAP.buildCsapStack( e ) ) ;

					}

				}

			} else {

				result = "\n no resources found, ignoring: " + checked_out_service.getAbsolutePath( ) ;

			}

			return result ;

		}

		@Override
		public String toString ( ) {

			return "ServiceRename [currentName=" + currentName + ", newName=" + newName + "]" ;

		}

	}

	public class ServiceResourceAdd extends FileOperation {

		String path_to_new_resource ;

		public ServiceResourceAdd ( String newResource ) {

			super( ) ;
			this.path_to_new_resource = newResource ;

		}

		@Override
		String apply_to_checked_out_folder ( BufferedWriter writer , File checked_out_definition )
			throws Exception {

			String result ;
			File resourcesWorkingFolder = serviceResources.workingFolder( ) ;

			if ( resourcesWorkingFolder.exists( ) && resourcesWorkingFolder.isDirectory( ) ) {

				File currentResourceFile = new File( resourcesWorkingFolder, path_to_new_resource ) ;

				File checked_out_resourceFolder = new File( checked_out_definition, serviceResourcesFolder.key( ) ) ;
				File checked_out_resource = new File( checked_out_resourceFolder, path_to_new_resource ) ;

				if ( ! currentResourceFile.exists( ) ) {

					result = "File not found, assuming previously applied: " + currentResourceFile.getAbsolutePath( ) ;

				} else {

					checked_out_resource.getParentFile( ).mkdirs( ) ;
					result = "File Added: " + checked_out_resource.getAbsolutePath( ) ;
					FileUtils.copyFile( currentResourceFile, checked_out_resource ) ;

				}

			} else {

				result = "\n Failed to process: " + path_to_new_resource + ". Reason: folder not found: "
						+ resourcesWorkingFolder.getAbsolutePath( ) ;

			}

			return result ;

		}

		@Override
		public String toString ( ) {

			return "ServiceAddOrUpdate [newResource=" + path_to_new_resource + "]" ;

		}

	}

	public class ServiceResourceDelete extends FileOperation {

		String eolResource ;

		public ServiceResourceDelete ( String eolResource ) {

			super( ) ;
			this.eolResource = eolResource ;

		}

		@Override
		String apply_to_checked_out_folder ( BufferedWriter writer , File checked_out_definition )
			throws Exception {

			File checked_out_resourceFolder = new File( checked_out_definition, serviceResourcesFolder.key( ) ) ;
			File serviceConfigFileToDelete = new File( checked_out_resourceFolder, eolResource ) ;

			FileUtils.deleteQuietly( serviceConfigFileToDelete ) ;

			return "File Deleted: " + serviceConfigFileToDelete.getAbsolutePath( ) ;

		}

		@Override
		public String toString ( ) {

			return "ServiceResourceDelete [eolResource=" + eolResource + "]" ;

		}

	}

}
