package org.csap.agent.model ;

import static org.csap.agent.model.ProjectLoader.API_VERSION ;
import static org.csap.agent.model.ProjectLoader.APPLICATION ;
import static org.csap.agent.model.ProjectLoader.BASE_ENV_ONLY ;
import static org.csap.agent.model.ProjectLoader.CLUSTER_TEMPLATE_REFERENCES ;
import static org.csap.agent.model.ProjectLoader.CURRENT_VERSION ;
import static org.csap.agent.model.ProjectLoader.ENVIRONMENTS ;
import static org.csap.agent.model.ProjectLoader.ENVIRONMENT_DEFAULTS ;
import static org.csap.agent.model.ProjectLoader.ENVIRONMENT_IMPORTS ;
import static org.csap.agent.model.ProjectLoader.PROJECT ;
import static org.csap.agent.model.ProjectLoader.PROJECT_VERSION ;
import static org.csap.agent.model.ProjectLoader.SERVICE_TEMPLATES ;
import static org.csap.agent.model.ProjectLoader.SETTINGS ;
import static org.csap.agent.model.ProjectLoader.SUB_PROJECTS ;

import java.io.File ;
import java.util.List ;
import java.util.Map ;
import java.util.regex.Matcher ;
import java.util.stream.Collectors ;

import org.apache.commons.io.FileUtils ;
import org.csap.agent.CsapCore ;
import org.csap.agent.integrations.Vsphere ;
import org.csap.agent.linux.OsCommandRunner ;
import org.csap.agent.stats.MetricCategory ;
import org.csap.helpers.CSAP ;
import org.csap.helpers.CsapApplication ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;

import com.fasterxml.jackson.databind.JsonNode ;
import com.fasterxml.jackson.databind.ObjectMapper ;
import com.fasterxml.jackson.databind.node.ArrayNode ;
import com.fasterxml.jackson.databind.node.MissingNode ;
import com.fasterxml.jackson.databind.node.ObjectNode ;

public class ProjectMigrator {

	final Logger logger = LoggerFactory.getLogger( getClass( ) ) ;

	static boolean showTestSettingsMessage = true ;
	boolean testMode = false ;
	ObjectMapper jsonMapper = new ObjectMapper( ) ;

	public ProjectMigrator ( boolean testMode ) {

		if ( testMode ) {

			logger.info( CsapApplication.testHeader( "testMode: {}" ), testMode ) ;

		}

		this.testMode = testMode ;

	}

	public void migrateIfRequired ( File projectFile , ObjectNode projectDefinition ) {

		var modelVersion = projectDefinition.path( PROJECT ).path( API_VERSION ).asDouble( 1.0 ) ;

		if ( modelVersion < CURRENT_VERSION ) {

			logger.warn( CsapApplication.header( "Strict: {} Project: {} version: {}, required is: {}" ),
					Application.getInstance( ).getCsapCoreService( ).isDefinitionStrictMode( ),
					projectFile.getName( ),
					modelVersion, CURRENT_VERSION ) ;

		}

		if ( ! Application.getInstance( ).getCsapCoreService( ).isDefinitionStrictMode( ) ) {

			if ( modelVersion == 1.0 ) {

				try {

					migrate_2_0( projectFile.getName( ), projectDefinition ) ;

				} catch ( Exception e ) {

					logger.warn( CsapApplication.header( "Failed 2.0 migration: {} {}" ), CSAP.buildCsapStack( e ) ) ;

				}

			}

		}

		// for test
		try {

			if ( false && projectFile.getAbsolutePath( ).contains( "target" ) ) {

				logger.warn( CsapApplication.testHeader( "Exporting migrated definition: {}" ), projectFile
						.getAbsolutePath( ) ) ;
				ProjectLoader.addCsapJsonConfiguration( jsonMapper ) ;
				FileUtils.write( projectFile, CSAP.jsonPrint( jsonMapper, projectDefinition ) ) ;

			} else {

				logger.debug( "*** Skipping export: {}", projectFile.getAbsolutePath( ) ) ;

			}

		} catch ( Exception e ) {

			logger.warn( "Failed updating files: {}", CSAP.buildCsapStack( e ) ) ;

		}

	}

	private void migrate_2_0 ( String fileName , ObjectNode modelDefinition ) {

		logger.info( CsapApplication.header( "Starting migration..." ) ) ;
		StringBuilder info = new StringBuilder( ) ;

		if ( testMode ) {

			var clusters = modelDefinition.path( "clusterDefinitions" ) ;
			CSAP.asStreamHandleNulls( clusters )
					.map( clusterName -> clusters.path( clusterName ) )
					.filter( JsonNode::isObject )
					.map( cluster -> (ObjectNode) cluster )
					.filter( cluster -> ! cluster.has( "settings" ) )
					.forEach( cluster -> {

						File settingsFile = new File(
								getClass( ).getResource( "/definitions/test-lifecycle-settings.json" ).getPath( ) ) ;

						if ( showTestSettingsMessage ) {

							logger.info( CsapApplication.testHeader( "{} loading test settings from file: \n\t\t{}" ),
									fileName,
									settingsFile.getAbsolutePath( ) ) ;
							showTestSettingsMessage = false ;

						}

						if ( settingsFile.exists( ) ) {

							try {

								cluster.set( "settings", jsonMapper.readTree( settingsFile ).path( SETTINGS ) ) ;

							} catch ( Exception e ) {

								logger.warn( "Failed loading test settings {}", CSAP.buildCsapStack( e ) ) ;

							}

						}

					} ) ;

		}

		var project = modelDefinition.putObject( PROJECT ) ;
		project.put( API_VERSION, CURRENT_VERSION ) ;
		project.put( PROJECT_VERSION, 1.0 ) ;

		var legacyPackage = modelDefinition.remove( "packageDefinition" ) ;

		if ( ( legacyPackage != null ) && legacyPackage.isObject( ) ) {

			project.setAll( (ObjectNode) legacyPackage ) ;

		}

		info.append( CSAP.padLine( fileName ) + CSAP.pad( "packageDefinition" ) + PROJECT ) ;

		migrate_2_0_environments( info, fileName, modelDefinition ) ;

		migrate_2_0_settings( info, fileName, modelDefinition ) ;

		migrate_2_0_service_templates( info, fileName, modelDefinition ) ;

		migrate_2_0_csap_service_definitions( info, fileName, modelDefinition ) ;

		logger.warn( CsapApplication.header( "Application Definition migration : {}" ), info.toString( ) ) ;

		logger.debug( CsapApplication.header( "Migrated definition: {}" ), CSAP.jsonPrint( modelDefinition ) ) ;

	}

	private void migrate_2_0_csap_service_definitions (
														StringBuilder info ,
														String fileName ,
														ObjectNode modelDefinition ) {

		// find $CSAP_FOLDER/definition -name "csap-service.json" -type f -exec bash -c
		// 'sed "s|monitors|alerts|g" {}' \;
		OsCommandRunner runner = new OsCommandRunner( 10, 1, "application-migration" ) ;

		var script = List.of(
				"find $CSAP_FOLDER/definition -name \"csap-service.json\" -type f  -exec bash -c 'sed --in-place \"s|monitors|alerts|g\"  {}'  \\;" ) ;

		try {

			info.append( "\n\n\t running: " + script ) ;
			var migrateOutput = runner.runUsingDefaultUser( "migrating-definition", script ) ;

			info.append( CsapApplication.header( migrateOutput ) ) ;

		} catch ( Exception e ) {

			logger.warn( "Failed to migrate: {}", script, CSAP.buildCsapStack( e ) ) ;

		}

	}

	private void migrate_2_0_environments ( StringBuilder info , String fileName , ObjectNode modelDefinition ) {

		//
		// Migrate lifecycles
		//

		var legacyLifecycles = modelDefinition.remove( "clusterDefinitions" ) ;
		var environments = modelDefinition.putObject( ENVIRONMENTS ) ;

		// add default env
		var defaultEnv = environments.putObject( ENVIRONMENT_DEFAULTS ) ;
		var defaultSettings = defaultEnv.putObject( SETTINGS ) ;
		defaultSettings.put( BASE_ENV_ONLY, true ) ;

		// migrate base-os to use common template
		var defaultBaseOs = defaultEnv.putObject( "base-os" ) ;

		var legacyCapability = modelDefinition.remove( "capability" ) ;

		if ( ( legacyCapability != null ) && legacyCapability.isObject( ) ) {

			var application = defaultSettings.putObject( APPLICATION ) ;
			application.put( "name", legacyCapability.path( "name" ).asText( ) ) ;
			var subProjects = application.putArray( SUB_PROJECTS ) ;
			application.put( "definition-repo-url", legacyCapability.path( "scm" ).asText( ) ) ;
			// application.put( "definition-repo-type", legacyCapability.path( "scmType"
			// ).asText() ) ;
			application.put( "definition-repo-branch", legacyCapability.path( "scmBranch" ).asText( ) ) ;
			application.put( "maven-url", legacyCapability.path( "repoUrl" ).asText( ) ) ;
			// application.put( "ajp-secret", legacyCapability.path( "repoUrl" ).asText() )
			// ;
			// application.set( "helpMenuItems", legacyCapability.path( "helpMenuItems" ) )
			// ;
			var helpMenu = application.putObject( "help-menu-items" ) ;
			helpMenu.put( "User Guide", "https://***REMOVED***.atlassian.net/wiki/spaces/CSAP" ) ;
			helpMenu.put( "Release Notes",
					"https://***REMOVED***.atlassian.net/wiki/spaces/CSAP/pages/258211856/Release+Notes" ) ;
			helpMenu.put( "Quick Install",
					"https://***REMOVED***.atlassian.net/wiki/spaces/CSAP/pages/395282580/CSAP+Quick+Install" ) ;
			helpMenu.put( "Health Status",
					"https://***REMOVED***.atlassian.net/wiki/spaces/CSAP/pages/258211861/CSAP+Health" ) ;
			helpMenu.put( "Application Editor",
					"https://***REMOVED***.atlassian.net/wiki/spaces/CSAP/pages/258211875/CSAP+Application+Editor" ) ;
			helpMenu.put( "FAQ", "https://***REMOVED***.atlassian.net/wiki/spaces/CSAP/pages/347177032/CSAP+FAQ" ) ;

			// defaultSettings.set( DEFINITION_APPLICATION, legacyCapability ) ;
			info.append( CSAP.padLine( fileName ) + CSAP.pad( APPLICATION ) + "moved into defaults" ) ;

			var legacyPackages = legacyCapability.path( "releasePackages" ) ;

			if ( legacyPackages.isArray( ) ) {

				subProjects.addAll( (ArrayNode) legacyPackages ) ;

			}

		}

		if ( ( legacyLifecycles != null ) && ( legacyLifecycles.isObject( ) ) ) {

			// environments.setAll( (ObjectNode) legacyLifecycles ) ;
			// CSAP.camelToSnake( camel )
			CSAP.asStreamHandleNulls( legacyLifecycles )
					.forEach( lifeName -> {

						environments.set( CSAP.camelToSnake( lifeName ), legacyLifecycles.path( lifeName ) ) ;

					} ) ;

			CSAP.asStreamHandleNulls( legacyLifecycles )
					.map( envName -> environments.path( envName ) )
					.filter( JsonNode::isObject )
					.filter( legacyLife -> legacyLife.has( SETTINGS ) )
					.map( legacyLife -> legacyLife.path( SETTINGS ) )
					.filter( JsonNode::isObject )
					.map( legacySettings -> (ObjectNode) legacySettings )
					.findFirst( )
					.map( legacySettings -> {

						// first lifecycle with settings used to seed defaults
						defaultSettings.setAll( legacySettings.deepCopy( ) ) ;
						defaultSettings.remove( "import" ) ;
						defaultSettings.remove( "lbUrl" ) ;
						defaultSettings.remove( EnvironmentSettings.CONFIGURATION_MAPS ) ;
						defaultSettings.remove( Vsphere.VSPHERE_SETTINGS_NAME ) ;

						var configMaps = defaultSettings.putObject( EnvironmentSettings.CONFIGURATION_MAPS ) ;
						var globalMap = configMaps.putObject( EnvironmentSettings.GLOBAL_CONFIG_MAP_NAME ) ;
						globalMap.put( "csap_auto", "test_only" ) ;
						return "" ;

					} ) ;

		}

		info.append( CSAP.padLine( fileName ) + CSAP.pad( "clusterDefinitions" ) + ENVIRONMENTS ) ;

		//
		// Migrate lifecycle cluster definitions (fold jvms and os into templates)
		//
		CSAP.asStreamHandleNulls( environments )
				.filter( envName -> ! envName.equals( ENVIRONMENT_DEFAULTS ) )
				.map( envName -> environments.path( envName ) )
				.filter( JsonNode::isObject )
				.forEach( environmentDefinition -> {

					// add default import to settings
					CSAP.asStreamHandleNulls( environmentDefinition )
							.filter( clusterName -> clusterName.equals( SETTINGS ) )
							.map( clusterName -> environmentDefinition.path( SETTINGS ) )
							.filter( JsonNode::isObject )
							.map( envSettings -> (ObjectNode) envSettings )
							.forEach( envSettings -> {

								var legacySettings = envSettings.deepCopy( ) ;

								if ( envSettings.path( "clearExisting" ).asBoolean( true ) ) {

									envSettings.removeAll( ) ;

								}

								var imports = envSettings.putArray( ENVIRONMENT_IMPORTS ) ;
								imports.add( ENVIRONMENT_DEFAULTS ) ;
								envSettings.put( EnvironmentSettings.LOADBALANCER_URL, legacySettings.path( "lbUrl" )
										.asText( ) ) ;

								var newConfigMaps = envSettings.putObject( EnvironmentSettings.CONFIGURATION_MAPS ) ;
								var legacyMaps = legacySettings.path( EnvironmentSettings.CONFIGURATION_MAPS ) ;

								if ( legacyMaps.isObject( ) ) {

									var textDefinition = legacyMaps.toString( ) ;

									textDefinition = migrateServiceVariables( textDefinition ) ;

									// textDefinition = textDefinition.replaceAll(
									// Matcher.quoteReplacement( "$csap_def_" ),
									// Matcher.quoteReplacement( CsapCore.CSAP_VARIABLE_PREFIX ) ) ;
									//
									// textDefinition = textDefinition.replaceAll(
									// Matcher.quoteReplacement( "csap_def_" ),
									// Matcher.quoteReplacement( CsapCore.CSAP_VARIABLE_PREFIX ) ) ;

									try {

										newConfigMaps.setAll( (ObjectNode) jsonMapper.readTree( textDefinition ) ) ;

									} catch ( Exception e ) {

										logger.warn( "failed importing legacy maps: {}", CSAP.buildCsapStack( e ) ) ;

									}

								}

								if ( legacySettings.has( Vsphere.VSPHERE_SETTINGS_NAME ) ) {

									envSettings.set( Vsphere.VSPHERE_SETTINGS_NAME,
											legacySettings.path( Vsphere.VSPHERE_SETTINGS_NAME ) ) ;

								}

								// remove legacy fields as they are normalized

							} ) ;

					// migrate clusters
					CSAP.asStreamHandleNulls( environmentDefinition )
							.filter( clusterName -> ! clusterName.equals( SETTINGS ) )
							.map( clusterName -> environmentDefinition.path( clusterName ) )
							.filter( JsonNode::isObject )
							.map( clusterDefinition -> (ObjectNode) clusterDefinition )
							.forEach( clusterDefinition -> {

								var clusterTemplates = clusterDefinition.remove( "osProcessesList" ) ;
								clusterDefinition.remove( "lastModifiedBy" ) ;

								if ( clusterTemplates == null
										|| ! clusterTemplates.isArray( ) ) {

									clusterTemplates = jsonMapper.createArrayNode( ) ;

								}

								clusterDefinition.set( CLUSTER_TEMPLATE_REFERENCES, clusterTemplates ) ;

								var clusterServices = (ArrayNode) clusterTemplates ;
								var eolClusterJvms = clusterDefinition.remove( "jvmPorts" ) ;

								CSAP.asStreamHandleNulls( eolClusterJvms )
										.forEach( jvmName -> {

											if ( jvmName.equals( "CsAgent" ) ) {

												jvmName = CsapCore.AGENT_NAME ;

											} else if ( jvmName.equals( "admin" ) ) {

												jvmName = CsapCore.ADMIN_NAME ;

											}

											clusterServices.add( jvmName ) ;

											// set port ?
										} ) ;

							} ) ;

					if ( ! defaultBaseOs.has( CLUSTER_TEMPLATE_REFERENCES ) ) {

						// only first instance is used
						var envBaseOs = environmentDefinition.path( "base-os" ) ;

						if ( envBaseOs.isObject( ) && envBaseOs.has( CLUSTER_TEMPLATE_REFERENCES ) ) {

							var clone = (ObjectNode) envBaseOs ;
							defaultBaseOs.put( "description", "core services installed on every host" ) ;
							defaultBaseOs.setAll( clone.deepCopy( ) ) ;
							defaultBaseOs.remove( ProjectLoader.CLUSTER_HOSTS ) ;

						}

					}

					CSAP.asStreamHandleNulls( environmentDefinition )
							.filter( clusterName -> ! clusterName.equals( SETTINGS ) )
							.filter( clusterName -> clusterName.equals( "base-os" ) )
							.map( clusterName -> environmentDefinition.path( clusterName ) )
							.filter( JsonNode::isObject )
							.map( clusterDefinition -> (ObjectNode) clusterDefinition )
							.forEach( clusterDefinition -> {

								clusterDefinition.remove( ProjectLoader.CLUSTER_TYPE ) ;
								clusterDefinition.remove( ProjectLoader.CLUSTER_TEMPLATE_REFERENCES ) ;
								clusterDefinition.remove( "description" ) ;

							} ) ;

				} ) ;

	}

	private void migrate_2_0_service_templates ( StringBuilder info , String fileName , ObjectNode modelDefinition ) {

		//
		// Migrate services
		//
		var serviceTemplates = jsonMapper.createObjectNode( ) ;
		modelDefinition.set( SERVICE_TEMPLATES, serviceTemplates ) ;

		// Migrate jvms into generic service
		var legacyJvms = modelDefinition.remove( "jvms" ) ;

		if ( ( legacyJvms != null )
				&& legacyJvms.isObject( ) ) {

			info.append( CSAP.padLine( fileName ) + CSAP.pad( "jvms" ) + "Migrated to " + SERVICE_TEMPLATES ) ;
			serviceTemplates.setAll( (ObjectNode) legacyJvms ) ;

		}

		// migrate os processes
		var legacyOsProcesses = modelDefinition.remove( "osProcesses" ) ;

		if ( ( legacyOsProcesses != null )
				&& legacyOsProcesses.isObject( ) ) {

			info.append( CSAP.padLine( fileName ) + CSAP.pad( "osProcesses" ) + "Migrated to " + SERVICE_TEMPLATES ) ;
			serviceTemplates.setAll( (ObjectNode) legacyOsProcesses ) ;

		}

		migrateServiceTemplates( serviceTemplates ) ;

	}

	public String migrateServiceVariables ( String textDefinition ) {

		textDefinition = textDefinition.replaceAll(
				Matcher.quoteReplacement( "$processing" ),
				Matcher.quoteReplacement( CsapCore.CSAP_WORKING ) ) ;

		textDefinition = textDefinition.replaceAll(
				Matcher.quoteReplacement( "$staging" ),
				Matcher.quoteReplacement( CsapCore.CSAP_BASE ) ) ;

		textDefinition = textDefinition.replaceAll(
				Matcher.quoteReplacement( "$life" ),
				Matcher.quoteReplacement( CsapCore.SERVICE_ENV ) ) ;

		textDefinition = textDefinition.replaceAll(
				Matcher.quoteReplacement( "$dashboard" ),
				Matcher.quoteReplacement( CsapCore.K8_DASHBOARD ) ) ;

		textDefinition = textDefinition.replaceAll(
				Matcher.quoteReplacement( "$nodePort" ),
				Matcher.quoteReplacement( CsapCore.K8_NODE_PORT ) ) ;

		textDefinition = textDefinition.replaceAll(
				Matcher.quoteReplacement( "$podIp" ),
				Matcher.quoteReplacement( CsapCore.K8_POD_IP ) ) ;

		textDefinition = textDefinition.replaceAll(
				Matcher.quoteReplacement( "$ingress" ),
				Matcher.quoteReplacement( CsapCore.K8_INGRESS ) ) ;

		textDefinition = textDefinition.replaceAll(
				Matcher.quoteReplacement( "$kubeletConfigFolder" ),
				Matcher.quoteReplacement( CsapCore.K8_CONFIG ) ) ;

		textDefinition = textDefinition.replaceAll(
				Matcher.quoteReplacement( "$csap_def_fqdn_host" ),
				Matcher.quoteReplacement( CsapCore.CSAP_DEF_FQDN_HOST ) ) ;

		textDefinition = textDefinition.replaceAll(
				Matcher.quoteReplacement( "$csap_def_host" ),
				Matcher.quoteReplacement( CsapCore.CSAP_DEF_HOST ) ) ;

		textDefinition = textDefinition.replaceAll(
				Matcher.quoteReplacement( "$host" ),
				Matcher.quoteReplacement( CsapCore.CSAP_DEF_HOST ) ) ;

		textDefinition = textDefinition.replaceAll(
				Matcher.quoteReplacement( "$csap_def_namespace" ),
				Matcher.quoteReplacement( CsapCore.CSAP_DEF_NAMESPACE ) ) ;

		textDefinition = textDefinition.replaceAll(
				Matcher.quoteReplacement( "$csap_def_replica_count" ),
				Matcher.quoteReplacement( CsapCore.CSAP_DEF_REPLICA ) ) ;

		textDefinition = textDefinition.replaceAll(
				Matcher.quoteReplacement( "$csap_def_docker_image" ),
				Matcher.quoteReplacement( CsapCore.CSAP_DEF_IMAGE ) ) ;

		textDefinition = textDefinition.replaceAll(
				Matcher.quoteReplacement( "$csap_def_name" ),
				Matcher.quoteReplacement( CsapCore.CSAP_DEF_NAME ) ) ;

		textDefinition = textDefinition.replaceAll(
				Matcher.quoteReplacement( "$serviceName" ),
				Matcher.quoteReplacement( CsapCore.CSAP_DEF_NAME ) ) ;

		textDefinition = textDefinition.replaceAll(
				Matcher.quoteReplacement( "$csap_def_resource_folder" ),
				Matcher.quoteReplacement( CsapCore.CSAP_DEF_RESOURCE ) ) ;

		textDefinition = textDefinition.replaceAll(
				Matcher.quoteReplacement( "$resources" ),
				Matcher.quoteReplacement( CsapCore.CSAP_DEF_RESOURCE ) ) ;

		textDefinition = textDefinition.replaceAll(
				Matcher.quoteReplacement( "$workingFolder" ),
				Matcher.quoteReplacement( CsapCore.CSAP_DEF_WORKING ) ) ;

		textDefinition = textDefinition.replaceAll(
				Matcher.quoteReplacement( "$logFolder" ),
				Matcher.quoteReplacement( CsapCore.CSAP_DEF_LOGS ) ) ;

		textDefinition = textDefinition.replaceAll(
				Matcher.quoteReplacement( "$csap_def_parameters" ),
				Matcher.quoteReplacement( CsapCore.CSAP_DEF_PARAMETERS ) ) ;

		textDefinition = textDefinition.replaceAll(
				Matcher.quoteReplacement( "$parameters" ),
				Matcher.quoteReplacement( CsapCore.CSAP_DEF_PARAMETERS ) ) ;

		textDefinition = textDefinition.replaceAll(
				Matcher.quoteReplacement( "$csap_def_parameters" ),
				Matcher.quoteReplacement( CsapCore.CSAP_DEF_PARAMETERS ) ) ;

		textDefinition = textDefinition.replaceAll(
				Matcher.quoteReplacement( "$csap_def_instance" ),
				Matcher.quoteReplacement( CsapCore.CSAP_DEF_NAME ) ) ;

		textDefinition = textDefinition.replaceAll(
				Matcher.quoteReplacement( "$instance" ),
				Matcher.quoteReplacement( CsapCore.CSAP_DEF_NAME ) ) ;

		textDefinition = textDefinition.replaceAll(
				Matcher.quoteReplacement( "$port" ),
				Matcher.quoteReplacement( CsapCore.CSAP_DEF_PORT ) ) ;

		textDefinition = textDefinition.replaceAll(
				Matcher.quoteReplacement( "$csap_def_port" ),
				Matcher.quoteReplacement( CsapCore.CSAP_DEF_PORT ) ) ;

		textDefinition = textDefinition.replaceAll(
				Matcher.quoteReplacement( "$ajpPort" ),
				Matcher.quoteReplacement( CsapCore.CSAP_DEF_AJP_PORT ) ) ;

		textDefinition = textDefinition.replaceAll(
				Matcher.quoteReplacement( "$jmxPort" ),
				Matcher.quoteReplacement( CsapCore.CSAP_DEF_JMX_PORT ) ) ;

		textDefinition = textDefinition.replaceAll(
				Matcher.quoteReplacement( "$csap_def_replica_count" ),
				Matcher.quoteReplacement( CsapCore.CSAP_DEF_REPLICA ) ) ;

		textDefinition = textDefinition.replaceAll(
				Matcher.quoteReplacement( "$context" ),
				Matcher.quoteReplacement( CsapCore.CSAP_DEF_CONTEXT ) ) ;

		textDefinition = textDefinition.replaceAll(
				Matcher.quoteReplacement( "$serviceRef:" ),
				Matcher.quoteReplacement( CsapCore.SERVICE_HOSTS ) ) ;

		// migrate legacy variable prefixes
		textDefinition = textDefinition.replaceAll(
				Matcher.quoteReplacement( "$csap_def_" ),
				Matcher.quoteReplacement( CsapCore.CSAP_VARIABLE_PREFIX ) ) ;

		textDefinition = textDefinition.replaceAll(
				Matcher.quoteReplacement( "csap_def_" ),
				Matcher.quoteReplacement( CsapCore.CSAP_VARIABLE_PREFIX ) ) ;

		return textDefinition ;

	}

	public void migrateServiceTemplates ( ObjectNode serviceTemplates ) {

		//
		// migrate to new service names
		if ( serviceTemplates.has( "CsAgent" ) ) {

			serviceTemplates.set( CsapCore.AGENT_NAME, serviceTemplates.remove( "CsAgent" ) ) ;

		}

		if ( serviceTemplates.has( "admin" ) ) {

			serviceTemplates.set( CsapCore.ADMIN_NAME, serviceTemplates.remove( "admin" ) ) ;

		}

		//
		// sort all services
		//

		List<String> sortedServices = CSAP.asStreamHandleNulls( serviceTemplates )
				.sorted( )
				.collect( Collectors.toList( ) ) ;

		sortedServices.stream( ).forEach(
				serviceName -> //
				serviceTemplates.set( serviceName, serviceTemplates.remove( serviceName ) ) ) ;

		//
		// Migrate service template attributes
		//

		CSAP.asStreamHandleNulls( serviceTemplates )
				.map( serviceTemplates::path )
				.filter( JsonNode::isObject )
				.map( serviceTemplate -> (ObjectNode) serviceTemplate )
				.forEach( serviceTemplate -> {

					serviceTemplate.remove( "lastModifiedBy" ) ;
					serviceTemplate.remove( "notifications" ) ;

					if ( serviceTemplate.has( ServiceAttributes.port.json( ) ) ) {

						serviceTemplate.put( ServiceAttributes.port.json( ),
								serviceTemplate.path( ServiceAttributes.port.json( ) ).asInt( 0 ) ) ;

					}

					if ( serviceTemplate.has( ServiceAttributes.startOrder.json( ) ) ) {

						serviceTemplate.put( ServiceAttributes.startOrder.json( ),
								serviceTemplate.path( ServiceAttributes.startOrder.json( ) ).asInt( -1 ) ) ;

					}

					if ( serviceTemplate.has( ServiceAttributes.osProcessPriority.json( ) ) ) {

						var osPriority = serviceTemplate.path( ServiceAttributes.osProcessPriority.json( ) ) ;
						serviceTemplate.put( ServiceAttributes.osProcessPriority.json( ), osPriority.asInt( 0 ) ) ;

					}

					var performance = serviceTemplate.remove( "customMetrics" ) ;

					if ( performance != null && performance.isObject( ) ) {

						serviceTemplate.set( ServiceAttributes.performanceApplication.json( ), performance ) ;

					}

					var alerts = serviceTemplate.remove( "monitors" ) ;

					if ( alerts != null && alerts.isObject( ) ) {

						serviceTemplate.set( ServiceAttributes.osAlertLimits.json( ), alerts ) ;

					}

					var javaWarnings = serviceTemplate.remove( "standardJmx" ) ;

					if ( javaWarnings != null && javaWarnings.isObject( ) ) {

						serviceTemplate.set( ServiceAttributes.javaAlertWarnings.json( ), javaWarnings ) ;

					}

					var textDefinition = migrateServiceVariables( serviceTemplate.toString( ) ) ;
					JsonNode deepCopyClone = MissingNode.getInstance( ) ;

					try {

						deepCopyClone = jsonMapper.readTree( textDefinition ) ;

					} catch ( Exception e ) {

						logger.warn( "Failed migrating definition: {} {}", textDefinition, CSAP.buildCsapStack( e ) ) ;

					}

					if ( deepCopyClone.isObject( ) ) {

						serviceTemplate.removeAll( ) ;

						for ( String item : ServiceAttributes.preferredOrder( ) ) {

							if ( deepCopyClone.has( item ) ) {

								serviceTemplate.put( item, "-" ) ;

							}

						}

						serviceTemplate.setAll( (ObjectNode) deepCopyClone ) ;

					}

				} ) ;

	}

	Map<String, String> settingsFields = Map.of(
			"agent", "csap-host-agent",
			"lbUrl", EnvironmentSettings.LOADBALANCER_URL,
			"csapData", "csap-data",
			"metricsCollectionInSeconds", "csap-collection" ) ;

	List<String> settingsOrder = List.of(
			ENVIRONMENT_IMPORTS,
			BASE_ENV_ONLY,
			APPLICATION,
			"operatorNotifications",
			EnvironmentSettings.LOADBALANCER_URL,
			"csap-host-agent",
			"csap-data",
			"monitorDefaults",
			EnvironmentSettings.CONFIGURATION_MAPS,
			"csap-collection",
			"reports" ) ;

	private void migrate_2_0_settings ( StringBuilder info , String fileName , ObjectNode modelDefinition ) {

		//
		// Migrate lifecycle settings
		//
		for ( var settingsEntry : settingsFields.entrySet( ) ) {

			var oldKey = settingsEntry.getKey( ) ;
			var newKey = settingsEntry.getValue( ) ;

			modelDefinition.findValues( SETTINGS ).stream( )
					.filter( JsonNode::isObject )
					.map( settings -> (ObjectNode) settings )
					.filter( settings -> settings.has( oldKey ) )
					.forEach( settings -> {

						info.append( CSAP.padLine( fileName ) + CSAP.pad( oldKey ) + newKey ) ;
						var legacySettings = (ObjectNode) settings.remove( oldKey ) ;
						settings.set( newKey, legacySettings ) ;

						if ( newKey.equals( "csap-collection" ) ) {

							migrate_2_0_collectionSettings( fileName, info, settings, legacySettings ) ;

						} else if ( newKey.equals( "csap-data" ) ) {

							var newData = jsonMapper.createObjectNode( ) ;
							settings.set( newKey, newData ) ;
							newData.put( "user", legacySettings.path( "user" ).asText( CsapCore.EVENTS_DISABLED ) ) ;
							newData.put( "credential", legacySettings.path( "pass" ).asText(
									CsapCore.EVENTS_DISABLED ) ) ;

							var serviceUrl = CsapCore.EVENTS_DISABLED ;
							var eventLegacy = legacySettings.path( "eventUrl" ).asText( CsapCore.EVENTS_DISABLED ) ;

							if ( eventLegacy.contains( "/api/event" ) ) {

								serviceUrl = eventLegacy.replaceAll( Matcher.quoteReplacement( "/api/event" ), "" ) ;

							}

							newData.put( "service-url", serviceUrl ) ;

							var legacySecondary = legacySettings.path( "secondary-publish" ) ;

							if ( legacySecondary.isObject( ) ) {

								newData.set( "secondary-publish", legacySecondary ) ;

							}

						}

					} ) ;

		}

		// update order
		modelDefinition.findValues( SETTINGS ).stream( )
				.filter( JsonNode::isObject )
				.map( settings -> (ObjectNode) settings )
				.forEach( legacySettingsOrder -> {

					var orderedSettings = jsonMapper.createObjectNode( ) ;
					settingsOrder.stream( )
							.filter( settingsItem -> legacySettingsOrder.has( settingsItem ) )
							.forEach( settingsItem -> orderedSettings.put( settingsItem, "place-holder" ) ) ;

					orderedSettings.setAll( legacySettingsOrder ) ;
					legacySettingsOrder.removeAll( ) ;
					legacySettingsOrder.setAll( orderedSettings ) ;

				} ) ;

	}

	private void migrate_2_0_collectionSettings (
													String fileName ,
													StringBuilder info ,
													ObjectNode settings ,
													ObjectNode legacySettings ) {

		// rename jmx to application
		if ( legacySettings.has( "resource" ) ) {

			legacySettings.set( CsapApplication.COLLECTION_HOST, legacySettings.remove( "resource" ) ) ;

		}

		if ( legacySettings.has( "service" ) ) {

			legacySettings.set( CsapApplication.COLLECTION_OS_PROCESS, legacySettings.remove( "service" ) ) ;

		}

		if ( legacySettings.has( "jmx" ) ) {

			legacySettings.set( CsapApplication.COLLECTION_APPLICATION, legacySettings.remove( "jmx" ) ) ;

		}

		// move trending and realtime to reports
		var trends = legacySettings.remove( "trending" ) ;

		if ( trends != null
				&& trends.isArray( ) ) {

			var reports = settings.putObject( "reports" ) ;
			info.append( CSAP.padLine( fileName ) + CSAP.pad( "reports" ) + "trending" ) ;
			reports.set( "trending", trends ) ;

			// string legacy label
			CSAP.jsonStream( trends )
					.filter( JsonNode::isObject )
					.map( trend -> (ObjectNode) trend )
					.filter( trend -> trend.has( "report" ) )
					.forEach( trend -> {

						var report = trend.path( "report" ).asText( "" ) ;
						var newReport = report
								.replaceFirst( "host/", MetricCategory.osShared.json( ) + "/" )
								.replaceFirst( "service/", MetricCategory.osProcess.json( ) + "/" )
								.replaceFirst( "jmxCustom/", MetricCategory.application.json( ) + "/" )
								.replaceFirst( "jmx/", MetricCategory.java.json( ) + "/" ) ;

						trend.put( "report", newReport ) ;

						var serviceName = trend.path( "serviceName" ).asText( "" ) ;

						if ( serviceName.equals( "CsAgent" ) ) {

							trend.put( "serviceName", CsapCore.AGENT_NAME ) ;

						} else if ( serviceName.equals( "admin" ) ) {

							trend.put( "serviceName", CsapCore.ADMIN_NAME ) ;

						}

					} ) ;

		}

		var realTimeMeters = legacySettings.remove( "realTimeMeters" ) ;

		if ( realTimeMeters != null
				&& realTimeMeters.isArray( ) ) {

			var reports = settings.path( "reports" ) ;

			if ( ! reports.isObject( ) ) {

				reports = settings.putObject( "reports" ) ;

			}

			var reportSection = (ObjectNode) reports ;
			info.append( CSAP.padLine( fileName ) + CSAP.pad( "reports" ) + "realTimeMeters" ) ;
			reportSection.set( "realTimeMeters", realTimeMeters ) ;

			// string legacy label
			CSAP.jsonStream( realTimeMeters )
					.filter( JsonNode::isObject )
					.map( meter -> (ObjectNode) meter )
					.filter( meter -> meter.has( "id" ) )
					.forEach( meter -> {

						var id = meter.path( "id" ).asText( "" ) ;
						var newId = id
								.replaceFirst( "vm.", MetricCategory.osShared.json( ) + "." )
								.replaceFirst( "process.", MetricCategory.osProcess.json( ) + "." )
								.replaceFirst( "jmxCustom.", MetricCategory.application.json( ) + "." )
								.replaceFirst( "jmxCommon.", MetricCategory.java.json( ) + "." )
								.replaceFirst( "CsAgent", CsapCore.AGENT_NAME ) ;

						meter.put( "id", newId ) ;
						var divideBy = meter.path( MetricCategory.divideBy.json( ) ).asText( "" ) ;

						if ( divideBy.equals( "vmCount" ) ) {

							meter.put( MetricCategory.divideBy.json( ), MetricCategory.hostCount.json( ) ) ;

						}

					} ) ;

		}

	}

}
