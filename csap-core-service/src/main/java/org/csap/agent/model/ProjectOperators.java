package org.csap.agent.model ;

import static org.csap.agent.model.ProjectLoader.CLUSTER_HOSTS ;
import static org.csap.agent.model.ProjectLoader.CLUSTER_TEMPLATE_REFERENCES ;
import static org.csap.agent.model.ProjectLoader.ENVIRONMENTS ;
import static org.csap.agent.model.ProjectLoader.ENVIRONMENT_DEFAULTS ;
import static org.csap.agent.model.ProjectLoader.ENVIRONMENT_IMPORTS ;
import static org.csap.agent.model.ProjectLoader.SETTINGS ;

import java.io.File ;
import java.util.ArrayList ;
import java.util.List ;
import java.util.regex.Pattern ;
import java.util.stream.Collectors ;

import org.apache.commons.io.FileUtils ;
import org.apache.commons.lang3.StringUtils ;
import org.csap.agent.CsapCore ;
import org.csap.agent.container.kubernetes.KubernetesJson ;
import org.csap.helpers.CSAP ;
import org.csap.helpers.CsapApplication ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;

import com.fasterxml.jackson.core.type.TypeReference ;
import com.fasterxml.jackson.databind.JsonNode ;
import com.fasterxml.jackson.databind.ObjectMapper ;
import com.fasterxml.jackson.databind.node.ArrayNode ;
import com.fasterxml.jackson.databind.node.ObjectNode ;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory ;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper ;
import com.fasterxml.jackson.dataformat.yaml.YAMLParser ;

public class ProjectOperators {

	public static final String VALUE = "value" ;

	public static final String PATH = "path" ;

	public static final String AUTOPLAY_ERRORS = "autoplay-errors" ;

	public static final String PROCESSED = "processed" ;

	final Logger logger = LoggerFactory.getLogger( getClass( ) ) ;

	ObjectMapper jsonMapper ;

	YAMLFactory yaml = new YAMLFactory( ) ;

	public ProjectOperators ( ObjectMapper jsonMapper ) {

		this.jsonMapper = jsonMapper ;

	}

	public ObjectNode processAutoPlay ( File autoplayFile , File projectFile , ObjectNode projectDefinition ) {

		logger.info( "{} processing: {}", projectFile.getName( ), autoplayFile ) ;

		var results = jsonMapper.createObjectNode( ) ;

		var autoPlays = loadYaml( autoplayFile, results ) ;

		for ( var autoPlay : autoPlays ) {

			var target = autoPlay.path( "target" ).asText( ) ;
			var operator = autoPlay.path( "operator" ).asText( ) ;

			switch ( operator ) {

			case "modify":
				results.set( "modify", performModifyOperations( autoPlay, projectDefinition ) ) ;
				break ;

			case "delete":
				var deleteFile = new File( projectFile.getParentFile( ), target ) ;
				results.set( "delete", performDeleteOperation( deleteFile ) ) ;
				break ;

			case "create":
				var createFile = new File( projectFile.getParentFile( ), target ) ;
				results.set( "create", performCreateOperation( createFile, autoPlay ) ) ;
				break ;

			default:
				break ;

			}

		}

		var prefix = CsapCore.AUTO_PLAY_FILE.split( Pattern.quote( "." ) )[0] ;
		var newName = new File( autoplayFile.getParentFile( ), prefix + "-completed.yaml" ) ;
		autoplayFile.renameTo( newName ) ;
		results.put( "renamed", newName.getAbsolutePath( ) ) ;

		var resultsFile = new File( autoplayFile.getParentFile( ), prefix + "-results.yaml" ) ;
		var yamlResults = generateYaml( results ) ;

		try {

			FileUtils.write( resultsFile, yamlResults ) ;

		} catch ( Exception e ) {

			logger.warn( "Failed to create: {} {}", resultsFile, CSAP.buildCsapStack( e ) ) ;

		}

		logger.info( CsapApplication.header( "{}" ), CSAP.jsonPrint( results ) ) ;

		return results ;

	}

	public ObjectNode performCreateOperation ( File createFile , ObjectNode autoPlay ) {

		ObjectNode createResults = jsonMapper.createObjectNode( ) ;

		autoPlay.remove( "operator" ) ;
		autoPlay.remove( "target" ) ;

		JsonNode content = autoPlay ;

		if ( autoPlay.has( "content" ) ) {

			content = autoPlay.path( "content" ) ;

		}

		try {

			if ( createFile.exists( ) ) {

				FileUtils.deleteQuietly( createFile ) ;
				createResults.put( "deleted", createFile.getAbsolutePath( ) ) ;

			}

			var name = createFile.getName( ) ;
			String fileContent ;

			if ( name.endsWith( "yml" ) || name.endsWith( "yaml" ) ) {

				fileContent = generateYaml( content ) ;

			} else if ( name.endsWith( "json" ) || name.endsWith( "json" ) ) {

				// fileContent = CSAP.jsonPrint( content ) ;
				// uses unformatted
				fileContent = CSAP.jsonPrint( jsonMapper, content ) ;

			} else {

				fileContent = content.asText( ) ;

			}

			FileUtils.write( createFile, fileContent ) ;
			createResults.put( PROCESSED, createFile.getAbsolutePath( ) ) ;

		} catch ( Exception e ) {

			logger.warn( "Failed to create: {} {}", createFile, CSAP.buildCsapStack( e ) ) ;
			createResults.put( AUTOPLAY_ERRORS, "Failed to write file: " + createFile.getAbsolutePath( ) ) ;

		}

		return createResults ;

	}

	public ObjectNode performDeleteOperation ( File targetFile ) {

		ObjectNode deleteResults = jsonMapper.createObjectNode( ) ;

		if ( targetFile.exists( ) ) {

			if ( targetFile.delete( ) ) {

				deleteResults.put( PROCESSED, targetFile.getAbsolutePath( ) ) ;

			} else {

				deleteResults.put( AUTOPLAY_ERRORS, "failed to delete target: " + targetFile.getAbsolutePath( ) ) ;

			}

			;

		} else {

			deleteResults.put( AUTOPLAY_ERRORS, "target does not exist: " + targetFile.getAbsolutePath( ) ) ;

		}

		return deleteResults ;

	}

	public List<ObjectNode> loadYaml ( File yamlFile , ObjectNode results ) {

		var documents = new ArrayList<ObjectNode>( ) ;

		try {

			YAMLParser yamlParser = yaml.createParser( yamlFile ) ;

			var valueType = new TypeReference<ObjectNode>( ) {
			} ;
			var reader = jsonMapper.readerFor( ObjectNode.class ).forType( valueType ).readValues( yamlParser ) ;

			while ( reader.hasNext( ) ) {

				ObjectNode x = (ObjectNode) reader.next( ) ;
				documents.add( x ) ;

			}

		} catch ( Exception e ) {

			var error = CSAP.buildCsapStack( e ) ;
			logger.warn( "Failed to load: {}: {}", yamlFile, error ) ;
			results.put( AUTOPLAY_ERRORS, "Failed to parse autoplay file, reason: " + e.getMessage( ) ) ;

		}

		return documents ;

	}

	public String generateYaml ( JsonNode doc ) {

		var results = " Failed to process" ;

		try {

			results = new YAMLMapper( ).writeValueAsString( doc ) ;

		} catch ( Exception e ) {

			logger.warn( "Failed to load: {}: {}", CSAP.jsonPrint( doc ), CSAP.buildCsapStack( e ) ) ;

		}

		return results ;

	}

	public ObjectNode performModifyOperations ( ObjectNode modifyOperations , ObjectNode projectDefinition ) {

		logger.debug( "processing: {}", modifyOperations ) ;

		var results = jsonMapper.createObjectNode( ) ;

		try {

			var environments = (ObjectNode) projectDefinition.path( ENVIRONMENTS ) ;
			var templates = (ObjectNode) projectDefinition.path( ProjectLoader.SERVICE_TEMPLATES ) ;

			var environmentModifies = modifyOperations.path( ENVIRONMENTS ) ;
			var baseEnvironmentName = environmentModifies.path( "base" ).asText( ) ;

			if ( StringUtils.isNotEmpty( baseEnvironmentName ) ) {

				results.set( ENVIRONMENTS,
						processEnvironment( projectDefinition, environments, templates, environmentModifies,
								baseEnvironmentName ) ) ;

			}

			var serviceModifies = modifyOperations.path( ProjectLoader.SERVICE_TEMPLATES ) ;

			if ( serviceModifies.isObject( ) ) {

				processModificationsWithDotSupport( projectDefinition, ProjectLoader.SERVICE_TEMPLATES, results,
						serviceModifies ) ;

			}

			var definitionOperations = modifyOperations.path( "operations" ) ;

			if ( definitionOperations.isArray( ) ) {

				var operationsResults = results.putArray( "operations" ) ;

				try {

					for ( var operation : definitionOperations ) {

						var deletes = operation.path( "deletes" ) ;

						if ( deletes.isArray( ) ) {

							operationsResults.addObject( ).set(
									"deletes",
									processDeletes( deletes, projectDefinition ) ) ;

						}

						var updates = operation.path( "updates" ) ;

						if ( updates.isArray( ) ) {

							operationsResults.addObject( ).set(
									"updates",
									processUpdates( updates, projectDefinition ) ) ;

						}

						var inserts = operation.path( "inserts" ) ;

						if ( inserts.isArray( ) ) {

							operationsResults.addObject( ).set(
									"inserts",
									processInserts( inserts, projectDefinition ) ) ;

						}

					}

				} catch ( Exception e ) {

					var failedOperations = operationsResults.addObject( ) ;
					failedOperations.put( AUTOPLAY_ERRORS, "Failed processing operations: " + e.getMessage( ) ) ;
					failedOperations.put( "details", CSAP.buildCsapStack( e ) ) ;
					logger.warn( "Failed to load: {}: {}", CSAP.jsonPrint( definitionOperations ), CSAP.buildCsapStack(
							e ) ) ;

				}

			}

		} catch ( Exception e ) {

			results.put( AUTOPLAY_ERRORS, "Failed processing modify environment: " + e.getMessage( ) ) ;
			results.put( "details", CSAP.buildCsapStack( e ) ) ;
			logger.warn( "Failed to load: {}: {}", CSAP.jsonPrint( modifyOperations ), CSAP.buildCsapStack( e ) ) ;

		}

		return results ;

	}

	private void processModificationsWithDotSupport (
														JsonNode containerDefinition ,
														String containerAttributeToUpdate ,
														ObjectNode results ,
														JsonNode updatesDefinition ) {

		var templateResults = results.putArray( containerAttributeToUpdate ) ;
		CSAP.asStreamHandleNulls( updatesDefinition )
				.forEach( attributeName -> {

					var attributeUpdates = updatesDefinition.path( attributeName ) ;

					if ( attributeName.contains( "." ) ) {

						var fullPath = attributeName ;
						var serviceKey = fullPath.substring( 0, fullPath.indexOf( "." ) ) ;
						var templatePath = fullPath.substring( fullPath.indexOf( "." ) + 1 ) ;

						var csapCompressedUpdates = jsonMapper.createObjectNode( ) ;
						csapCompressedUpdates.set( templatePath, attributeUpdates ) ;

						var serviceDefTest = containerDefinition.path( containerAttributeToUpdate ).path( serviceKey ) ;

						if ( serviceDefTest.isObject( ) ) {

							var serviceDefinition = (ObjectNode) serviceDefTest ;
							applyUpdates( serviceKey, csapCompressedUpdates, serviceDefinition ) ;
							templateResults.add( serviceKey + " - merged settings" ) ;

						} else {

							templateResults.add( serviceKey + " - ignored, did not locate in definition" ) ;

						}

					} else if ( attributeUpdates.isObject( ) ) {

						var serviceDefTest = containerDefinition.path( containerAttributeToUpdate ).path(
								attributeName ) ;

						if ( serviceDefTest.isObject( ) ) {

							var serviceDefinition = (ObjectNode) serviceDefTest ;

							applyUpdates( attributeName, attributeUpdates, serviceDefinition ) ;

							templateResults.add( attributeName + " - merged settings" ) ;

						} else {

							if ( containerAttributeToUpdate.equals( ProjectLoader.SERVICE_TEMPLATES ) ) {

								var serviceTemplates = ( (ObjectNode) containerDefinition.path(
										containerAttributeToUpdate ) ) ;
								// insert it
								serviceTemplates.set( attributeName, attributeUpdates ) ;
								templateResults.add( attributeName
										+ " - inserted, did not locate existing in definition" ) ;

							} else {

								templateResults.add( attributeName + " - ignored, did not locate in definition" ) ;

							}

						}

					} else if ( attributeUpdates.isValueNode( ) ) {

						logger.info( "attributeName: {}, ", attributeName ) ;

						var container = containerDefinition.path( containerAttributeToUpdate ) ;

						if ( container.isObject( ) ) {

							( (ObjectNode) container ).set( attributeName, attributeUpdates ) ;

						} else {

							templateResults.add( attributeName + " - ignored, update invalid syntax" ) ;

						}

					} else {

						templateResults.add( attributeName + " - ignored, update invalid syntax" ) ;

					}

				} ) ;

	}

	private void applyUpdates ( String attributeName , JsonNode attributeUpdates , ObjectNode attributeDefinition ) {

		// top level replacements
		CSAP.asStreamHandleNulls( attributeUpdates )
				.filter( fieldName -> ! fieldName.contains( "." ) )
				.forEach( fieldName -> {

					logger.debug( "{} field: {} value: {}", attributeName, fieldName, attributeUpdates.path(
							fieldName ) ) ;
					attributeDefinition.set( fieldName, attributeUpdates.path( fieldName ) ) ;

				} ) ;

		// csap merged values
		CSAP.asStreamHandleNulls( attributeUpdates )
				.filter( fieldName -> fieldName.contains( "." ) )
				.forEach( fieldName -> {

					var parentPath = "/" + fieldName.substring( 0, fieldName.lastIndexOf( "." ) ) ;
					var lastField = fieldName.substring( fieldName.lastIndexOf( "." ) + 1 ) ;

					var defPath = parentPath.replaceAll(
							Pattern.quote( "." ),
							"/" ) ;

					logger.debug( "fieldName: {}, parentPath: {}, lastField: {}", fieldName, parentPath, lastField ) ;

					var updateItem = attributeDefinition.at( defPath ) ;

					if ( updateItem.isObject( ) ) {

						( (ObjectNode) updateItem ).set( lastField, attributeUpdates.path( fieldName ) ) ;

					}

				} ) ;

	}

	public ObjectNode processEnvironment (
											ObjectNode definitionFull ,
											ObjectNode definitionEnvironments ,
											ObjectNode definitionTemplates ,
											JsonNode autoplayEnvironment ,
											String autoplayBaseName ) {

		var envResults = jsonMapper.createObjectNode( ) ;

		var activeEnv = definitionEnvironments.path( autoplayBaseName ) ;

		if ( ! activeEnv.isObject( ) ) {

			envResults.put( AUTOPLAY_ERRORS, "did not find active environment: " + autoplayBaseName ) ;

		} else {

			envResults.put( "base", autoplayBaseName ) ;

			boolean removeEnvironments = autoplayEnvironment.path( "remove-inactive" ).asBoolean( true ) ;

			if ( removeEnvironments ) {

				envResults.setAll( removeEnvironments( definitionEnvironments, autoplayBaseName ) ) ;

			}

			// process clusters first - so they are available for host resolution
			var clusterDefinitions = autoplayEnvironment.path( "clusters" ) ;

			if ( clusterDefinitions.isObject( ) ) {

				envResults.set( "clusters", updateClusters( (ObjectNode) clusterDefinitions,
						(ObjectNode) activeEnv ) ) ;

			}

			// process hosts - applying to clusters as either literals or as index
			var clusterToHosts = autoplayEnvironment.path( ProjectLoader.CLUSTER_HOSTS ) ;

			if ( clusterToHosts.isObject( ) ) {

				envResults.set( "hosts", updateClusterHosts( clusterToHosts, (ObjectNode) activeEnv ) ) ;

			}

			var newEnvName = autoplayEnvironment.path( "name" ).asText( ) ;

			if ( StringUtils.isNotEmpty( newEnvName ) ) {

				// Updated environments
				envResults.put( "name", newEnvName ) ;
				definitionEnvironments.set( newEnvName, definitionEnvironments.remove( autoplayBaseName ) ) ;
				activeEnv = definitionEnvironments.path( newEnvName ) ;

				// Update service templates
				CSAP.asStreamHandleNulls( definitionTemplates )
						.map( serviceName -> definitionTemplates.path( serviceName ) )
						.filter( JsonNode::isObject )
						.map( service -> (ObjectNode) service )
						.map( service -> service.path( ServiceAttributes.environmentOverload.json( ) ) )
						.filter( JsonNode::isObject )

						.filter( envOverloads -> {

							logger.debug( "autoplayBaseName: {}, overload: {}", autoplayBaseName, envOverloads ) ;
							return true ;

						} )
						.filter( envOverloads -> envOverloads.has( autoplayBaseName ) )
						.filter( JsonNode::isObject )
						.map( envOverloads -> (ObjectNode) envOverloads )
						.forEach( envOverloads -> {

							logger.debug( "newEnvName: {}, autoplayBaseName:{} ", newEnvName, autoplayBaseName ) ;
							envOverloads.set( newEnvName, envOverloads.remove( autoplayBaseName ) ) ;

						} ) ;

			}

			if ( activeEnv.path( ProjectLoader.SETTINGS ).isObject( ) ) {

				updateSettings( (ObjectNode) activeEnv, definitionEnvironments, definitionFull, autoplayEnvironment,
						envResults ) ;

			}

		}

		return envResults ;

	}

	private void updateSettings (
									ObjectNode activeEnv ,
									ObjectNode definitionEnvironments ,
									ObjectNode completDefinition ,
									JsonNode autoplayEnvironment ,
									ObjectNode envResults ) {

		var projectSection = (ObjectNode) completDefinition.path( ProjectLoader.PROJECT ) ;

		//
		// Updated project
		//
		var updatedProject = jsonMapper.createObjectNode( ) ;

		var projectName = autoplayEnvironment.path( "project-name" ).asText( ) ;

		if ( StringUtils.isNotEmpty( projectName ) ) {

			updatedProject.put( "name", projectName ) ;
			envResults.put( "project-name", projectName ) ;

		}

		var contact = autoplayEnvironment.path( "contact" ).asText( ) ;

		if ( StringUtils.isNotEmpty( contact ) ) {

			updatedProject.put( "architect", contact ) ;
			updatedProject.put( "emailNotifications", contact ) ;
			envResults.put( "contact", contact ) ;

		}

		if ( ! updatedProject.isEmpty( ) ) {

			projectSection.setAll( updatedProject ) ;

		}

		//
		// update default settings
		//
		var autoplayDefaults = autoplayEnvironment.path( "default-settings" ) ;

		if ( autoplayDefaults.isObject( ) ) {

			var defaultsEnv = definitionEnvironments.path( ProjectLoader.ENVIRONMENT_DEFAULTS ) ;
			var defaultSettings = (ObjectNode) defaultsEnv.path( ProjectLoader.SETTINGS ) ;

			envResults.set( ProjectLoader.SETTINGS,
					CSAP.mergeAttributes(
							defaultSettings,
							(ObjectNode) autoplayDefaults ) ) ;

		}

		var activeSettings = (ObjectNode) activeEnv.path( ProjectLoader.SETTINGS ) ;

		//
		// update application
		//
		var updatedApplication = jsonMapper.createObjectNode( ) ;
		var appName = autoplayEnvironment.path( "application-name" ).asText( ) ;

		if ( StringUtils.isNotEmpty( appName ) ) {

			updatedApplication.put( "name", appName ) ;
			envResults.put( "application-name", appName ) ;

		}

		var git = autoplayEnvironment.path( "git" ).asText( ) ;

		if ( StringUtils.isNotEmpty( git ) ) {

			updatedApplication.put( "definition-repo-url", git ) ;
			envResults.put( "definition-repo-url", git ) ;

		}

		var branch = autoplayEnvironment.path( "branch" ).asText( ) ;

		if ( StringUtils.isNotEmpty( branch ) ) {

			updatedApplication.put( "definition-repo-branch", branch ) ;
			envResults.put( "definition-repo-branch", branch ) ;

		}

		if ( ! updatedApplication.isEmpty( ) ) {

			var appSettings = activeSettings.path( ProjectLoader.APPLICATION ) ;

			if ( ! appSettings.isObject( ) ) {

				appSettings = activeSettings.putObject( ProjectLoader.APPLICATION ) ;

			}

			( (ObjectNode) appSettings ).setAll( updatedApplication ) ;

		}

		//
		// updated specified environment settings
		//
		var autoplaySettings = autoplayEnvironment.path( ProjectLoader.SETTINGS ) ;

		if ( autoplaySettings.isObject( ) ) {

			envResults.set( ProjectLoader.SETTINGS,
					CSAP.mergeAttributes(
							activeSettings,
							(ObjectNode) autoplaySettings ) ) ;

		}

	}

	public ObjectNode updateClusterHosts ( JsonNode clusterToHosts , ObjectNode activeEnvironment ) {

		ObjectNode results = jsonMapper.createObjectNode( ) ;
		logger.info( CsapApplication.header( "hosts: {}" ), clusterToHosts ) ;

		var baseOsHosts = clusterToHosts.path( "base-os" ) ;
		var kubernetesMasters = clusterToHosts.path( "kubernetes-masters" ) ;

		CSAP.asStreamHandleNulls( clusterToHosts )
				.filter( clusterName -> activeEnvironment.path( clusterName ).isObject( ) )
				.filter( clusterName -> ! "kubernetes-masters".equals( clusterName ) )
				.forEach( clusterName -> {

					var cluster = (ObjectNode) activeEnvironment.path( clusterName ) ;

					var clusterHosts = mapIndexedHosts( clusterToHosts.path( clusterName ), baseOsHosts, clusterName ) ;
					results.set( clusterName, clusterHosts ) ;
					cluster.set( CLUSTER_HOSTS, clusterHosts ) ;

					if ( cluster.has( KubernetesJson.masters.json( ) )
							&& kubernetesMasters.isArray( ) ) {

						var mappedMasters = mapIndexedHosts( kubernetesMasters, baseOsHosts, clusterName ) ;
						cluster.set( KubernetesJson.masters.json( ), mappedMasters ) ;

					}

				} ) ;

		return results ;

	}

	private ObjectNode updateClusters (
										ObjectNode autoplayClusters ,
										ObjectNode activeEnvironment ) {

		logger.info( CsapApplication.header( "Adding/updating cluster definitions: {}" ), CSAP.jsonPrint(
				autoplayClusters ) ) ;

		logger.info( "activeEnvironment: {}", CSAP.jsonPrint( activeEnvironment ) ) ;

		ObjectNode results = jsonMapper.createObjectNode( ) ;

		var autoPlayNewClusters = new ArrayList<String>( ) ;

		// handle new clusters
		CSAP.asStreamHandleNulls( autoplayClusters )
				.filter( clusterName -> autoplayClusters.path( clusterName ).isObject( ) )
				.filter( clusterName -> activeEnvironment.path( clusterName ).isMissingNode( ) )
				.forEach( clusterName -> {

					var autoPlayCluster = autoplayClusters.path( clusterName ) ;

					results.put( clusterName, "added new cluster" ) ;
					activeEnvironment.set( clusterName, autoPlayCluster ) ;

					// avoid processing again below
					autoPlayNewClusters.add( clusterName ) ;

				} ) ;

		// updates or deletes
		CSAP.asStreamHandleNulls( autoplayClusters )
				.filter( clusterName -> ! autoPlayNewClusters.contains( clusterName ) )
				.filter( clusterName -> autoplayClusters.path( clusterName ).isObject( ) )
				.filter( clusterName -> activeEnvironment.path( clusterName ).isObject( ) )
				.forEach( clusterName -> {

					var autoPlayCluster = autoplayClusters.path( clusterName ) ;

					if ( autoPlayCluster.path( "delete" ).asBoolean( ) ) {

						results.put( clusterName, "removed cluster" ) ;
						activeEnvironment.remove( clusterName ) ;

					} else {

						results.put( clusterName, "updated cluster" ) ;
						activeEnvironment.set( clusterName, autoPlayCluster ) ;

					}

				} ) ;

		return results ;

	}

	public JsonNode mapIndexedHosts ( JsonNode hosts , JsonNode baseOsHosts , String clusterName ) {

		if ( baseOsHosts.isArray( ) && ! clusterName.equals( "base-os" ) ) {

			var reqHosts = hosts ;
			var mappedHosts = jsonMapper.createArrayNode( ) ;
			CSAP.jsonStream( hosts )
					.forEach( hostName -> {

						var hostIndex = hostName.asInt( -1 ) ;

						if ( hostIndex < 0 ) {

							mappedHosts.add( hostName.asText( ) ) ;

						} else {

							var host = baseOsHosts.path( hostIndex - 1 )
									.asText( "unmapped-host-" + reqHosts.toString( ) ) ;
							mappedHosts.add( host ) ;

						}

					} ) ;
			hosts = mappedHosts ;

		}

		return hosts ;

	}

	public ObjectNode processDeletes ( JsonNode deletes , ObjectNode projectDefinition ) {

		ObjectNode results = jsonMapper.createObjectNode( ) ;
		var deleteMissingResults = results.putArray( AUTOPLAY_ERRORS ) ;
		CSAP.jsonStream( deletes )
				.map( JsonNode::asText )
				.filter( deletePath -> projectDefinition.at( deletePath ).isMissingNode( ) )
				.forEach( deletePath -> {

					deleteMissingResults.add( deletePath ) ;

				} ) ;

		if ( deleteMissingResults.isEmpty( ) ) {

			results.remove( AUTOPLAY_ERRORS ) ;

		}

		var deleteResults = results.putArray( PROCESSED ) ;
		logger.debug( "deletes: {}", deletes ) ;

		CSAP.jsonStream( deletes )
				.map( JsonNode::asText )
				.filter( deletePath -> ! projectDefinition.at( deletePath ).isMissingNode( ) )
				.forEach( deletePath -> {

					deleteResults.add( deletePath ) ;
					var fullPath = new File( deletePath ) ;
					var parentPath = fullPath.getParent( ).replaceAll( Pattern.quote( "\\" ), "/" ) ;
					var parentTarget = (ObjectNode) projectDefinition.at( parentPath ) ;
					parentTarget.remove( fullPath.getName( ) ) ;

				} ) ;

		return results ;

	}

	public void buildMissingPaths ( JsonNode deletes , ObjectNode projectDefinition , ArrayNode deleteMissingResults ) {

		CSAP.jsonStream( deletes )
				.filter( JsonNode::isObject )
				.map( update -> (ObjectNode) update )
				.filter( update -> update.has( PATH ) && update.has( VALUE ) )
				.filter( update -> projectDefinition.at( update.path( PATH ).asText( ) ).isMissingNode( ) )
				.forEach( update -> {

					var updatePath = update.path( PATH ).asText( ) ;
					deleteMissingResults.add( updatePath ) ;

				} ) ;

	}

	public ObjectNode processInserts ( JsonNode inserts , ObjectNode projectDefinition ) {

		ObjectNode results = jsonMapper.createObjectNode( ) ;
		var errors = results.putArray( AUTOPLAY_ERRORS ) ;

		var insertResults = results.putArray( PROCESSED ) ;
		logger.debug( "inserts: {}", inserts ) ;

		CSAP.jsonStream( inserts )
				.filter( JsonNode::isObject )
				.map( update -> (ObjectNode) update )
				.filter( update -> update.has( PATH ) )
				.forEach( update -> {

					var insertValue = update.path( VALUE ) ;

					var insertPath = update.path( PATH ).asText( ) ;
					var insertTarget = projectDefinition.at( insertPath ) ;

					var fullPath = new File( insertPath ) ;
					var parentPath = fullPath.getParent( ).replaceAll( Pattern.quote( "\\" ), "/" ) ;
					var parentTarget = projectDefinition.at( parentPath ) ;

					if ( parentTarget.isMissingNode( ) ) {

						errors.add( parentPath ) ;

					} else if ( insertTarget.isObject( ) && insertValue.isObject( ) ) {

						// add to existing map
						insertResults.add( insertPath ) ;
						( (ObjectNode) insertTarget ).setAll( (ObjectNode) insertValue ) ;

					} else if ( insertTarget.isArray( ) && insertValue.isTextual( ) ) {

						// add to existing map
						// insertResults.add( insertPath ) ;
						( (ArrayNode) insertTarget ).add( insertValue ) ;

					} else {

						insertResults.add( parentPath ) ;
						( (ObjectNode) parentTarget ).set( fullPath.getName( ), insertValue ) ;

					}

				} ) ;

		if ( errors.isEmpty( ) ) {

			results.remove( AUTOPLAY_ERRORS ) ;

		}

		return results ;

	}

	public ObjectNode processUpdates ( JsonNode updates , ObjectNode projectDefinition ) {

		ObjectNode results = jsonMapper.createObjectNode( ) ;
		var updateMissingResults = results.putArray( AUTOPLAY_ERRORS ) ;
		buildMissingPaths( updates, projectDefinition, updateMissingResults ) ;

		var updateResults = results.putArray( PROCESSED ) ;
		logger.debug( "updates: {}", updates ) ;
		CSAP.jsonStream( updates )
				.filter( JsonNode::isObject )
				.map( update -> (ObjectNode) update )
				.filter( update -> update.has( PATH ) && update.has( VALUE ) )
				.filter( update -> ! projectDefinition.at( update.path( PATH ).asText( ) ).isMissingNode( ) )
				.forEach( update -> {

					var updatePath = update.path( PATH ).asText( ) ;
					updateResults.add( updatePath ) ;
					var fullPath = new File( updatePath ) ;
					var parentPath = fullPath.getParent( ).replaceAll( Pattern.quote( "\\" ), "/" ) ;
					var parentTarget = (ObjectNode) projectDefinition.at( parentPath ) ;
					parentTarget.set( fullPath.getName( ), update.path( VALUE ) ) ;

				} ) ;

		if ( updateMissingResults.isEmpty( ) ) {

			results.remove( AUTOPLAY_ERRORS ) ;

		}

		return results ;

	}

	public ObjectNode removeEnvironments ( ObjectNode environments , String activeName ) {

		var results = jsonMapper.createObjectNode( ) ;

		var envsToKeep = new ArrayList<String>( List.of( activeName ) ) ;

		var activeEnvironment = environments.path( activeName ) ;
		var sourceSettings = activeEnvironment.path( SETTINGS ) ;
		var sourceImports = sourceSettings.path( ENVIRONMENT_IMPORTS ) ;

		if ( sourceImports.isArray( ) ) {

			var imports = CSAP.jsonStream( sourceImports )
					.map( JsonNode::asText )
					.collect( Collectors.toList( ) ) ;
			envsToKeep.addAll( imports ) ;

			var envsToDelete = CSAP.asStreamHandleNulls( environments )
					.filter( environmentName -> ! envsToKeep.contains( environmentName ) )
					.collect( Collectors.toList( ) ) ;

			var envDeleteResults = results.putArray( "deleted" ) ;

			for ( var envDelete : envsToDelete ) {

				envDeleteResults.add( envDelete ) ;
				environments.remove( envDelete ) ;

			}

		}

		CSAP.asStreamHandleNulls( environments )
				.filter( environmentName -> ! environmentName.equals( ENVIRONMENT_DEFAULTS ) ) ;

		return results ;

	}

	public ObjectNode pushDown (
									String sourceClusterName ,
									String sourceEnvironmentName ,
									String targetEnvironmentName ,
									ObjectNode projectDefinition ) {

		var results = jsonMapper.createObjectNode( ) ;

		var request = results.putObject( "request" ) ;
		request.put( "sourceClusterName", sourceClusterName ) ;
		request.put( "sourceEnvironmentName", sourceEnvironmentName ) ;

		var environments = projectDefinition.path( ENVIRONMENTS ) ;

		var sourceEnvironment = environments.path( sourceEnvironmentName ) ;

		var sourceSettings = sourceEnvironment.path( SETTINGS ) ;

		var sourceCluster = sourceEnvironment.path( sourceClusterName ) ;

		var sourceImports = sourceSettings.path( ENVIRONMENT_IMPORTS ) ;

		if ( sourceCluster.isObject( )
				&& sourceCluster.has( CLUSTER_TEMPLATE_REFERENCES )
				&& sourceImports.isArray( )
				&& sourceImports.size( ) > 0 ) {

			if ( StringUtils.isEmpty( targetEnvironmentName ) ) {

				targetEnvironmentName = sourceImports.get( 0 ).asText( ) ;

			}

			request.put( "targetEnvironmentName", targetEnvironmentName ) ;

			var pushEnvironment = environments.path( targetEnvironmentName ) ;

			if ( pushEnvironment.isObject( ) ) {

				var pushEnv = (ObjectNode) pushEnvironment ;

				var newBaseCluster = (ObjectNode) sourceCluster.deepCopy( ) ;
				newBaseCluster.remove( CLUSTER_HOSTS ) ;

				var updates = results.putObject( "updates" ) ;

				if ( pushEnv.has( sourceClusterName ) ) {

					updates.put( "operation", "replaced " + sourceClusterName + " in " + targetEnvironmentName ) ;

				} else {

					updates.put( "operation", "added " + sourceClusterName + " to " + targetEnvironmentName ) ;

				}

				var targetEnvWrapped = targetEnvironmentName ;
				CSAP.asStreamHandleNulls( environments )
						.filter( environmentName -> ! environmentName.equals( ENVIRONMENT_DEFAULTS ) )
						.filter( environmentName -> environments.path( environmentName ).path( sourceClusterName )
								.isObject( ) )
						.filter( environmentName -> envContainsImport( environments, environmentName,
								targetEnvWrapped ) )
						.forEach( environmentName -> {

							var selectEnvironment = environments.path( environmentName ) ;
							var clusterWithReference = selectEnvironment.path( sourceClusterName ) ;

							if ( clusterWithReference.has( CLUSTER_TEMPLATE_REFERENCES ) ) {

								var clusterMatch = (ObjectNode) clusterWithReference ;
								var hosts = clusterMatch.path( CLUSTER_HOSTS ) ;
								clusterMatch.removeAll( ) ;

								if ( hosts.isArray( ) ) {

									clusterMatch.set( CLUSTER_HOSTS, hosts ) ;

								}

								updates.put( environmentName, "removed type and templates" ) ;

							}

						} ) ;

				pushEnv.set( sourceClusterName, newBaseCluster ) ;

			}

		} else {

			results.put( "error", "No imports found in source environment" ) ;

		}

		return results ;

	}

	public boolean envContainsImport ( JsonNode environments , String environmentName , String targetEnvironmentName ) {

		var envSettings = environments.path( environmentName ).path( SETTINGS ) ;
		var envImports = envSettings.path( ENVIRONMENT_IMPORTS ) ;

		if ( envImports.isArray( )
				&& envImports.size( ) > 0 ) {

			try {

				List<String> envs = jsonMapper.readValue( envImports.traverse( ),
						new TypeReference<ArrayList<String>>( ) {
						} ) ;
				return envs.contains( targetEnvironmentName ) ;

			} catch ( Exception e ) {

				logger.warn( "Failed parsing imports : {}", CSAP.buildCsapStack( e ) ) ;

			}

			return true ;

		}

		return false ;

	}

}
