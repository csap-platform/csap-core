package org.csap.agent.model ;

import static java.util.Comparator.comparing ;

import java.io.File ;
import java.io.IOException ;
import java.nio.file.Files ;
import java.nio.file.Path ;
import java.util.ArrayList ;
import java.util.Collections ;
import java.util.Comparator ;
import java.util.HashMap ;
import java.util.HashSet ;
import java.util.List ;
import java.util.Map ;
import java.util.TreeMap ;
import java.util.function.Consumer ;
import java.util.function.Predicate ;
import java.util.regex.Matcher ;
import java.util.stream.Collectors ;
import java.util.stream.Stream ;

import org.apache.commons.io.FileUtils ;
import org.apache.commons.lang3.StringUtils ;
import org.apache.commons.lang3.text.WordUtils ;
import org.csap.agent.CsapApis ;
import org.csap.agent.CsapConstants ;
import org.csap.agent.CsapTemplates ;
import org.csap.agent.container.C7 ;
import org.csap.agent.container.kubernetes.KubernetesIntegration ;
import org.csap.agent.container.kubernetes.K8 ;
import org.csap.agent.integrations.CsapEvents ;
import org.csap.agent.linux.HostInfo ;
import org.csap.agent.linux.ZipUtility ;
import org.csap.agent.stats.service.HttpCollector ;
import org.csap.agent.ui.editor.ServiceResources ;
import org.csap.helpers.CSAP ;
import org.csap.helpers.CsapApplication ;
import org.jasypt.encryption.pbe.StandardPBEStringEncryptor ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.beans.factory.annotation.Autowired ;

import com.fasterxml.jackson.core.JsonParseException ;
import com.fasterxml.jackson.core.JsonParser ;
import com.fasterxml.jackson.core.JsonProcessingException ;
import com.fasterxml.jackson.core.SerializableString ;
import com.fasterxml.jackson.core.io.CharacterEscapes ;
import com.fasterxml.jackson.core.type.TypeReference ;
import com.fasterxml.jackson.databind.JsonMappingException ;
import com.fasterxml.jackson.databind.JsonNode ;
import com.fasterxml.jackson.databind.ObjectMapper ;
import com.fasterxml.jackson.databind.node.ArrayNode ;
import com.fasterxml.jackson.databind.node.ObjectNode ;

/**
 * 
 *
 *
 * @author someDeveloper
 *
 *
 * @see <a href="doc-files/csagent.jpg"> Click to enlarge
 *      <IMG width=300 SRC="doc-files/csagent.jpg"></a>
 *
 * @see <a href="doc-files/spring.jpg" > Click to enlarge
 *      <IMG width=300 SRC="doc-files/modelDocs.jpg"></a>
 *
 */
public class ProjectLoader {

	public static final String NAMESPACE_MONITOR_TEMPLATE = "namespace-monitor-template" ;

	final Logger logger = LoggerFactory.getLogger( getClass( ) ) ;

	private ObjectMapper jsonMapper = new ObjectMapper( ) ;

	CsapApis csapApis ;

	ProjectMigrator projectMigrator = null ;
	ProjectOperators projectOperators = null ;

	//
	// generator namespace monitors
	//
	public static final String NAMESPACE_MONITORS = "namespace-monitors" ;
	public static final String NAMESPACE_MONITOR_PREFIX = "ns-" ;

	// Container for both global definition, and any children
	volatile private Project activeRootProject = null ;

	public static final String API_VERSION = "api-version" ;
	public static final String PROJECT_VERSION = "project-version" ;
	public static final double CURRENT_VERSION = 2.1 ;
	public static final String CSAP_SERVICE_FILE_LEGACY = "csap-service.json" ;
	public static final String CSAP_SERVICE_FILE = "csap-service.yaml" ;
	public static final String AGENT_AUTO_CLUSTER = "agent-auto-assign" ;
	public static final String PARSER_CLUSTER_VERSION = "version" ;

	public static final String SERVER = "server" ;
	// Only using this in a couple of places. Provides some abstraction to
	// spring object model
	static private EnvironmentSettings _lastLifeCycleConfig = null ;

	public static final String ENVIRONMENT_CLUSTER_DELIMITER = ":" ;

	public static final String APPLICATION = "application" ;
	public static final String PROJECT = "project" ;
	public static final String BASE_ENV_ONLY = "base-env-only" ;
	public static final String ENVIRONMENT_IMPORTS = "imports" ;

	public static final String SUB_PROJECTS = "sub-projects" ;

	public static final String ENVIRONMENTS = "environments" ;
	public static final String SETTINGS = "settings" ;
	public static final String SERVICE_TEMPLATES = "service-templates" ;

	public static final String DEFINITION_MONITORS = "monitors" ;
	public static final String ERROR_INVALID_CHARACTERS = "Service name contains invalid characters. Only Alphanumeric and '-' is permitted" ;
	public static final String WARNING_DUPLICATE_HOST_PORT = "found duplicate host / port combination" ;
	public static final String CLUSTER_TEMPLATE_REFERENCES = "template-references" ;
	public static final String CLUSTER_HOSTS = "hosts" ;
	public static final String CLUSTER_TYPE = "type" ;

	public static final String ENVIRONMENT_DEFAULTS = "defaults" ;

	public static EnvironmentSettings getModelConfiguration ( ) {

		return _lastLifeCycleConfig ;

	}

	static public String buildServicePtr ( String serviceName , boolean isJvm ) {

		// if ( isJvm ) {
		// return "/" + SERVICE_CATEGORY_JVMS + "/" + serviceName ;
		// }
		return "/" + SERVICE_TEMPLATES + "/" + serviceName ;

	}

	public String getEnvPath ( String environmentName ) {

		return "/" + ENVIRONMENTS + "/" + environmentName ;

	}

	public String getEnvPath ( ) {

		return "/" + ENVIRONMENTS ;

	}

	public ProjectLoader ( CsapApis csapApis ) {

		this.csapApis = csapApis ;

		projectMigrator = new ProjectMigrator( csapApis.application( ).isJunit( ) ) ;
		projectOperators = new ProjectOperators( jsonMapper ) ;

	}

	private ObjectNode rootEnvironmentDefaults = null ;

	public synchronized StringBuilder process ( boolean isTest , File rootDefinitionFile )
		throws JsonProcessingException ,
		IOException {

		isTest_FOR_LOGS_ONLY = isTest ;

		StringBuilder parsing_results = new StringBuilder( CsapApplication.header( "Definition Loaded" ) ) ;

		ObjectNode rootProjectDefinition = definition_file_reader( rootDefinitionFile, true ) ;

		parsing_results.append( checkForInvalidMongoKeys( rootProjectDefinition, rootDefinitionFile
				.getAbsolutePath( ) ) ) ;

		Project rootProject = new Project( rootProjectDefinition ) ;

		rootProject.initializeRoot(
				rootDefinitionFile,
				load_definition_templates( rootDefinitionFile, parsing_results ) ) ;

		// Ensures packages are only loaded once
		TreeMap<String, String> fileName_to_packageName = new TreeMap<String, String>( ) ;

		var environmentNames = CSAP
				.asStreamHandleNulls( rootProjectDefinition.path( ENVIRONMENTS ) ).collect( Collectors.toList( ) ) ;

		// only the application settings are globally reused, and may optionally be
		// overridden per project
		rootEnvironmentDefaults = jsonMapper.createObjectNode( ) ;
		rootEnvironmentDefaults.set( APPLICATION,
				rootProjectDefinition
						.path( ProjectLoader.ENVIRONMENTS )
						.path( ProjectLoader.ENVIRONMENT_DEFAULTS )
						.path( ProjectLoader.SETTINGS )
						.path( APPLICATION ) ) ;

		logger.debug( "environmentNames: {}", environmentNames ) ;

		for ( String environmentName : environmentNames ) {

			parsing_results.append( CsapApplication.LINE ) ;
			parsing_results.append( "\n Processing environment: " + environmentName + "\n" ) ;

			rootProject.getEnvironmentAndClusterNames( ).add( environmentName ) ;

			EnvironmentSettings rootEnvironmentSettings = buildEnvironmentSettings( environmentName, rootDefinitionFile
					.getName( ), null,
					rootProjectDefinition,
					parsing_results ) ;

			rootProject.getEnvironmentNameToSettings( ).put( environmentName, rootEnvironmentSettings ) ;

			//
			// load clusters in root definition first, as services may be referred to by non
			// root packages
			//
			load_project_environment(
					environmentName,
					rootProject,
					parsing_results,
					rootEnvironmentSettings ) ;

			rootEnvironmentSettings.getSubProjects( ).stream( )
					.forEach( projectFileName -> {

						load_child_projects(
								projectFileName,
								rootDefinitionFile,
								parsing_results,
								rootProject,
								fileName_to_packageName,
								environmentName, rootEnvironmentSettings ) ;

					} ) ;

			if ( parsing_results.indexOf( CsapConstants.CONFIG_PARSE_ERROR ) >= 0 ) {

				logger.error( "Found Errors in parsing: "
						+ parsing_results.substring( parsing_results.indexOf( CsapConstants.CONFIG_PARSE_ERROR ) ) ) ;

				return parsing_results ; // no point in continuing

			}
			// logger.info("Pushing " + platformLifeCycle + " with groups size "
			// + groupList.size()) ;

		}

		parsing_results.append( "\n\n Model validation and Agent Configuration" ) ;

		generateProjectHosts( rootProject, parsing_results ) ;

		validateRootProject( rootProject, parsing_results ) ;

		load_package_release_files( parsing_results, rootProject ) ;

		configureRemoteCollections( parsing_results, rootProject, rootDefinitionFile ) ;

		// logger.info("Parsed: " + sb);
		if ( ! isTest ) {

			var activateResults = activate( rootProject ) ;

			parsing_results.append( activateResults ) ;

			if ( ! csapApis.application( ).isJunit( )
					&& ( csapApis.application( ).isAdminProfile( )
							|| csapApis.application( ).isDesktopHost( )
							|| csapApis.application( ).getActiveProject( ).getAllPackagesModel( ).getHostsCurrentLc( )
									.size( ) == 1 ) ) {

				csapApis.events( ).publishEvent( CsapEvents.CSAP_SYSTEM_CATEGORY + "/model/summary",
						"Cluster Summary", null,
						csapApis.application( ).buildSummaryReport( false ) ) ;

				csapApis.events( ).publishEvent( CsapEvents.CSAP_SYSTEM_CATEGORY + "/model/definition",
						"Cluster Defintion", null,
						csapApis.application( ).getDefinitionForAllPackages( ) ) ;

			}

		}

		lastSummary = generateStatusMessage( rootProject ).toString( ) ;

		// logger.debug( "parsing_results: {}", parsing_results );

		return parsing_results ;

	}

	private String lastSummary = "" ;

	public String getLastSummary ( ) {

		return lastSummary ;

	}

	private EnvironmentSettings buildEnvironmentSettings (
															String environmentName ,
															String fileName ,
															ObjectNode rootProjectApplicationDefaults ,
															ObjectNode applicationDefinition ,
															StringBuilder parsing_results )
		throws IOException {

		// load lifecycle settings
		EnvironmentSettings projectEnvSettings = new EnvironmentSettings( ) ;

		// global application settings from root project
		if ( rootProjectApplicationDefaults != null ) {

			logger.debug( "Merging rootSettings: {} for environment: {}", rootProjectApplicationDefaults,
					ENVIRONMENT_DEFAULTS ) ;
			projectEnvSettings.loadSettings( fileName + " defaults", rootProjectApplicationDefaults, parsing_results,
					csapApis.application( ) ) ;

		}

		// logger.info( "{} application name: {} ", environmentName,
		// projectLifecycleSettings.getName() ) ;
		// import support
		var envPath = "/" + ProjectLoader.ENVIRONMENTS + "/" + environmentName + "/" + SETTINGS ;
		var importEnvPath = envPath + "/" + ENVIRONMENT_IMPORTS ;

		var imports = applicationDefinition.at( importEnvPath ) ;
		logger.trace( "path: {} imports: {}", importEnvPath, imports ) ;

		if ( imports.isArray( ) ) {

			CSAP.jsonStream( imports )
					.map( JsonNode::asText )
					.forEach( importName -> {

						var importPath = "/" + ProjectLoader.ENVIRONMENTS + "/" + importName + "/" + SETTINGS ;
						logger.debug( "Importing settings from another lifecycle: {}", importPath ) ;

						try {

							projectEnvSettings.loadSettings(
									fileName + " import: " + importPath,
									applicationDefinition.at( importPath ),
									parsing_results,
									csapApis.application( ) ) ;

						} catch ( Exception e ) {

							logger.warn( "Failed loading: {} ", importName ) ;

						}

					} ) ;

		}

		logger.debug( "loading: {}", envPath ) ;
		projectEnvSettings.loadSettings(
				fileName + " env: " + envPath,
				applicationDefinition.at( envPath ),
				parsing_results,
				csapApis.application( ) ) ;
		logger.debug( "environment: {} application  name: {} ", environmentName, projectEnvSettings
				.getApplicationName( ) ) ;

		return projectEnvSettings ;

	}

	static public ServiceInstance getDefaultService ( String name , ObjectMapper mapper ) {

		var theService = new ServiceInstance( ) ;
		theService.setName( name ) ;

		try {

			var csapDefaults = mapper.readTree( CsapTemplates.default_service_definitions.getFile( ) ) ;
			theService.parseDefinition(
					"test",
					csapDefaults.path( SERVICE_TEMPLATES ).path( theService.getName( ) ),
					new StringBuilder( ) ) ;

		} catch ( IOException e ) {

			// TODO Auto-generated catch block
			e.printStackTrace( ) ;

		}

		return theService ;

	}

	private ArrayNode load_definition_templates (
													File rootDefinitionFile ,
													StringBuilder parsingResults ) {

		logger.info( "Loading service templates from {}", rootDefinitionFile.getAbsolutePath( ) ) ;

		ArrayNode serviceTemplates = jsonMapper.createArrayNode( ) ;

		try {

			// handle junit resource path initialization
			if ( Application.isRunningOnDesktop( ) ) {

				logger.info( CsapApplication.testHeader( "Setting default resource for desktop" ) ) ;
				ServiceResources resource = new ServiceResources( "test", new File( "." ), jsonMapper ) ;

			}

			// load csap global templates
			var csapDefaults = definition_file_reader( CsapTemplates.default_service_definitions.getFile( ), false ) ;

			// load in source overrides for company specific settings
			var sourceOverrides = csapApis.application( ).getCsapCoreService( ).getSourceOverrides( ) ;

			if ( sourceOverrides != null ) {

				logger.info( "Found sourceOverrides, used for overridding source code location: {}", sourceOverrides ) ;

				for ( var serviceName : sourceOverrides.keySet( ) ) {

					var templateSource = csapDefaults.path( SERVICE_TEMPLATES )
							.path( serviceName )
							.path( ServiceAttributes.deployFromSource.json( ) ) ;

					var targetPath = templateSource.path( "path" ) ;

//					logger.debug( "targetPath: {}, targetPath: {}", targetPath.isValueNode( ), targetPath );

					if ( targetPath.isValueNode( ) ) {

						( (ObjectNode) templateSource ).put(
								"path",
								sourceOverrides.get( serviceName ) ) ;

					}

				}

			}

			var defaultTemplate = serviceTemplates.addObject( ) ;
			defaultTemplate.set( DefinitionConstants.definition.key( ), csapDefaults ) ;
			defaultTemplate.put( DefinitionConstants.pathToTemplate.key( ), "" ) ;

			// discover any definition templates
			var definitionTemplateFolder = new File( rootDefinitionFile.getParent( ),
					DefinitionConstants.projectsFolderName.key( ) ) ;
			var templateWorkingFolder = new File( rootDefinitionFile.getParent( ), DefinitionConstants.projectsExtracted
					.key( ) ) ;

			if ( definitionTemplateFolder.exists( ) ) {

				try (
						Stream<Path> filesystemPaths = Files.list( definitionTemplateFolder.toPath( ) ) ) {

					var templates = filesystemPaths
							.filter( Files::isRegularFile )
							.filter( path -> path.getFileName( ).toString( ).endsWith( ".zip" ) )
							.sorted( Comparator.comparing( path -> path.getFileName( ).toString( ) ) )
							.collect( Collectors.toList( ) ) ;

					if ( templates.size( ) > 0 ) {

						eolProcessTemplates( parsingResults, serviceTemplates, templateWorkingFolder, templates ) ;

					}

				} catch ( Exception e ) {

					logger.info( "failed getting listing: {}", CSAP.buildCsapStack( e ) ) ;

				}

			}

		} catch ( Exception e ) {

			var message = "Failed parsing default defintion, instead an empty default will be used." + CSAP
					.buildCsapStack( e ) ;
			parsingResults.append( message ) ;
			logger.warn( message ) ;

		}

		return serviceTemplates ;

	}

	private void eolProcessTemplates (
										StringBuilder parsingResults ,
										ArrayNode serviceTemplates ,
										File templateWorkingFolder ,
										List<Path> templates ) {

		var templateListing = templates.stream( )
				.map( Path::getFileName )
				.map( Path::toString )
				.collect( Collectors.joining( ", " ) ) ;

		logger.info( "template zips '{}'", templateListing ) ;

		FileUtils.deleteQuietly( templateWorkingFolder ) ;

		templateWorkingFolder.mkdirs( ) ;

		var unzipResults = templates.stream( )
				.map( zipPath -> {

					var results = "extracting \n\t source: '" + zipPath + "' \n\t destination: '"
							+ templateWorkingFolder + "'" ;

					try {

						ZipUtility.unzip( zipPath.toFile( ), templateWorkingFolder ) ;
						results += "\n\t result: success" ;

					} catch ( IOException e ) {

						logger.debug( "failed unzipping: {} {}", zipPath, CSAP.buildCsapStack( e ) ) ;
						results += "\n\t failed reason: " + CSAP.buildCsapStack( e ) ;

					}

					return results ;

				} )
				.collect( Collectors.joining( "\n\t" ) ) ;

		logger.info( "Template extraction results, {}", unzipResults ) ;

		try (
				Stream<Path> templatesExtractedPaths = Files.list( templateWorkingFolder.toPath( ) ) ) {

			var templatesExtracted = templatesExtractedPaths
					.filter( Files::isDirectory )
					.filter( path -> {

						var extractedDefinitionFile = csapApis.application( ).findFirstProjectFile( path
								.toFile( ) ) ;
						return ( extractedDefinitionFile != null )
								&& extractedDefinitionFile.isFile( ) ;

					} )
					.sorted( Comparator.comparing( path -> path.getFileName( ).toString( ) ) )
					.collect( Collectors.toList( ) ) ;

			var templateExtractedListingWithDefinition = templatesExtracted.stream( )
					.map( Path::getFileName )
					.map( Path::toString )
					.collect( Collectors.joining( "\n\t" ) ) ;

			logger.info( "templates with *-project.json: '{}'",
					templateExtractedListingWithDefinition ) ;

			var templateLoadResults = templatesExtracted.stream( )
					.map( extractedFolderName -> {

						var results = "Loaded project.json in: " + extractedFolderName.getFileName( )
								.toString( ) ;

						var extractedDefinitionFile = csapApis.application( ).findFirstProjectFile(
								extractedFolderName.toFile( ) ) ;

						try {

							var packageTemplateDefinition = definition_file_reader(
									extractedDefinitionFile,
									false ) ;

							var packageName = packageTemplateDefinition.path( ProjectLoader.PROJECT )
									.path(
											"name" )
									.asText( "package-not-found" ) ;
							results += "\t name: " + packageName ;

							var packageTemplate = serviceTemplates.addObject( ) ;
							packageTemplate.set( DefinitionConstants.definition.key( ),
									packageTemplateDefinition ) ;
							packageTemplate.put( DefinitionConstants.pathToTemplate.key( ),
									DefinitionConstants.projectsFolderName.key( ) + "/extracted/"
											+ extractedFolderName.getFileName( ).toString( ) ) ;

						} catch ( Exception e ) {

							results += "\n\t Failed loading template" + CSAP.buildCsapStack( e ) ;

						}

						return results ;

					} )
					.collect( Collectors.joining( "\n\t" ) ) ;

			logger.info( "template package load results \n {}", templateLoadResults ) ;
			parsingResults.append( "\n" + templateLoadResults + "\n" ) ;

		} catch ( Exception e ) {

			logger.info( "failed getting extracted listing: {}", CSAP.buildCsapStack( e ) ) ;

		}

	}

	private JsonNode get_environment_definition ( String environmentName , JsonNode projectDefinition ) {

		return projectDefinition
				.path( ENVIRONMENTS )
				.path( environmentName ) ;

	}

	public ObjectNode definition_string_reader ( String projectSource ) {

		ObjectNode projectDefinition = null ;

		try {

			projectDefinition = (ObjectNode) jsonMapper.readTree( projectSource ) ;

		} catch ( Exception e ) {

			logger.warn( "failed to load project: {}", CSAP.buildCsapStack( e ) ) ;

		}

		return projectDefinition ;

	}

	public static void addCsapJsonConfiguration ( ObjectMapper jsonMapper ) {

		jsonMapper.getFactory( ).enable( JsonParser.Feature.ALLOW_COMMENTS ) ;
		jsonMapper.getFactory( ).enable( JsonParser.Feature.ALLOW_SINGLE_QUOTES ) ;
		jsonMapper.getFactory( ).enable( JsonParser.Feature.ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER ) ;
		jsonMapper.getFactory( ).enable( JsonParser.Feature.AUTO_CLOSE_SOURCE ) ;
		var charEscapes = new HTMLCharacterEscapes( ) ;
		jsonMapper.getFactory( ).setCharacterEscapes( charEscapes ) ;

	}

	public ObjectNode definition_file_reader ( File definitionFile , boolean isPackageDefinition )
		throws JsonProcessingException ,
		IOException {

		ObjectNode projectDefinition = null ;

		if ( definitionFile.exists( ) ) {

			// String configJson = FileUtils.readFileToString( definitionFile ) ;
			addCsapJsonConfiguration( jsonMapper ) ;

			if ( definitionFile.getName( ).endsWith( "yml" )
					|| definitionFile.getName( ).endsWith( "yaml" ) ) {

				var resultReport = jsonMapper.createObjectNode( ) ;
				var projectDefinitions = getProjectOperators( ).loadYaml( definitionFile, resultReport ) ;

				if ( projectDefinitions.size( ) != 1 ) {

					logger.warn( "YAML docs should only be a equal to one, but found: {}",
							projectDefinitions.size( ) ) ;

				}

				projectDefinition = projectDefinitions.get( 0 ) ;

			} else {

				projectDefinition = (ObjectNode) jsonMapper.readTree( definitionFile ) ;

			}

			logger.trace( "Parsed Cluster from file: {} Contains: \n{}",
					definitionFile.getAbsolutePath( ),
					projectDefinition.toString( ) ) ;

			String packageName = projectDefinition.path( APPLICATION ).path( "name" ).asText( definitionFile
					.getName( ) ) ;

			if ( projectDefinition.has( ProjectLoader.PROJECT ) ) {

				packageName = projectDefinition.path( ProjectLoader.PROJECT )
						.path( Project.PARSER_RELEASE_PACKAGE_NAME )
						.asText( ) ;

			}

			//

			if ( isPackageDefinition ) {

				logger.info( CSAP.buildDescription(
						"Loaded application package",
						"packageName", packageName,
						"file", definitionFile.getName( ),
						"isTest_FOR_LOGS_ONLY", Boolean.toString( isPackageDefinition ),
						"Size", definitionFile.length( ),
						"Folder", definitionFile.getParentFile( ).getAbsolutePath( ) ) ) ;

				projectMigrator.migrateIfRequired( definitionFile, projectDefinition ) ;

				var autoPlayFile = csapApis.application( ).getAutoPlayFile( ) ;
				logger.debug( "Checking for {}", autoPlayFile.getAbsolutePath( ) ) ;

				if ( autoPlayFile.exists( ) ) {

					if ( csapApis.application( ).isRunningOnDesktop( ) ) {

						logger.info( CsapApplication.testHeader( "Running on desktop - auto play found - exiting {}" ),
								autoPlayFile.getAbsolutePath( ) ) ;
						Runtime.getRuntime( ).halt( 111 ) ;

					}

					projectOperators.processAutoPlay( autoPlayFile, definitionFile, projectDefinition ) ;

					writeDefinitionToDisk( definitionFile, projectDefinition ) ;

				}

			} else {

				logger.info( CSAP.buildDescription(
						"Loaded JSON",
						"packageName", packageName,
						"file", definitionFile.getName( ),
						"isTest_FOR_LOGS_ONLY", Boolean.toString( isPackageDefinition ),
						"Size", definitionFile.length( ),
						"Folder", definitionFile.getParentFile( ).getAbsolutePath( ) ) ) ;

			}

		} else {

			logger.warn( "Did not project file: {}", definitionFile.getAbsolutePath( ) ) ;
			throw new IOException( "File does not exist: " + definitionFile.getAbsolutePath( ) ) ;

		}

		return projectDefinition ;

	}

	public String convertDefinition ( JsonNode projectDefinition )
		throws JsonProcessingException {

		return CSAP.jsonPrint( jsonMapper, projectDefinition ) ;

	}

	public void writeDefinitionToDisk ( File definitionFile , JsonNode projectDefinition ) {

		try {

			FileUtils.write( definitionFile, CSAP.jsonPrint( jsonMapper, projectDefinition ) ) ;

		} catch ( Exception e ) {

			logger.warn( "Failed to update: {} {}", definitionFile, CSAP.buildCsapStack( e ) ) ;

		}

	}

	public Project getActiveRootProject ( ) {

		return activeRootProject ;

	}

	private void setActiveRootProject ( Project projectToActivate ) {

		activeRootProject = projectToActivate ;
		_lastLifeCycleConfig = projectToActivate.getEnvironmentNameToSettings( ).get( csapApis.application( )
				.getCsapHostEnvironmentName( ) ) ;
		return ;

	}

	/**
	 *
	 * CSAP Models optionally support 0 or more release packages - for very large
	 * deployments it offers separation of management/deployment
	 *
	 */
	private void load_child_projects (
										String projectFileName ,
										File rootProjectDefinitionFile ,
										StringBuilder parsingResults ,
										Project rootProject ,
										TreeMap<String, String> fileName_to_packageName ,
										String environmentName ,
										EnvironmentSettings rootLifecycleSettings ) {

		try {

			if ( ! fileName_to_packageName.containsKey( projectFileName ) ) {

				var generatedPackage = build_sub_project(
						projectFileName,
						rootProjectDefinitionFile,
						parsingResults,
						rootProject, fileName_to_packageName,
						environmentName ) ;

			}

			Project subProject = rootProject
					.getReleasePackage( fileName_to_packageName.get( projectFileName ) ) ;

			subProject.getLifeCycleToHostMap( ).put( environmentName, new ArrayList<String>( ) ) ;
			subProject.getEnvironmentAndClusterNames( ).add( environmentName ) ;

			var projectDefinition = (ObjectNode) subProject.getSourceDefinition( ) ;

			if ( projectDefinition.path( ENVIRONMENTS ).has( environmentName ) ) {

				logger.debug( "loading settings for: {} environment: {}", projectFileName, environmentName ) ;

				EnvironmentSettings projectLifecycleSettings = buildEnvironmentSettings(
						environmentName, projectFileName, rootEnvironmentDefaults,
						projectDefinition,
						parsingResults ) ;

				logger.debug( "Storing {} settings", environmentName ) ;

				subProject.getEnvironmentNameToSettings( ).put( environmentName, projectLifecycleSettings ) ;

				load_project_environment(
						environmentName,
						subProject,
						parsingResults,
						rootLifecycleSettings ) ;

				// If we find the current host name in release, then we
				// override
				if ( subProject.getHostToServicesMap( ).keySet( ).contains( csapApis.application( )
						.getCsapHostName( ) ) ) {

					rootProject.setActiveModel( subProject ) ;

				}

			}

		} catch ( Exception e ) {

			logger.info( "Got error parsing release package: '{}' reason: {}",
					projectFileName,
					CSAP.buildCsapStack( e ) ) ;
			throw new RuntimeException( e ) ;

		}

	}

	private Project build_sub_project (
										String projectFileName ,
										File rootProjectDefinitionFile ,
										StringBuilder parsingResults ,
										Project rootProject ,
										TreeMap<String, String> fileName_to_packageName ,
										String lifecycle )
		throws IOException {

		// Use path relative to parent to determin child
		File releasePackageFile = new File(
				rootProjectDefinitionFile.getAbsoluteFile( ).getParent( ),
				projectFileName ) ;

		if ( ! releasePackageFile.exists( )
				|| ! releasePackageFile.isFile( )
				|| ! releasePackageFile.isFile( ) ) {

			logger.warn( "Creating new release package as file does not exist: {}",
					releasePackageFile.getAbsolutePath( ) ) ;

			parsingResults.append( "\n Assuming new package was added:"
					+ releasePackageFile.getAbsolutePath( ) ) ;

			parsingResults.append( "\n Creating using template:"
					+ CsapTemplates.project_template.getFile( ).getAbsolutePath( ) + "\n" ) ;
			FileUtils.copyFile( CsapTemplates.project_template.getFile( ), releasePackageFile ) ;

		}

		ObjectNode subProjectDefinition = definition_file_reader( releasePackageFile, true ) ;
		Project subProject = new Project( subProjectDefinition, rootProject ) ;

		var isModelUpdated = subProject.updatePackageInformation( releasePackageFile ) ;

		if ( ! isModelUpdated ) {

			return null ;

		}

		rootProject.addProject( subProject.getName( ), subProject ) ;

		fileName_to_packageName.put( projectFileName, subProject.getName( ) ) ;

		return subProject ;

	}

	private void load_project_environment (
											String environmentName ,
											Project project ,
											StringBuilder parsingResultsBuffer ,
											EnvironmentSettings settings )
		throws JsonProcessingException ,
		IOException {

		portCheckList = new ArrayList<String>( ) ;

		if ( environmentName.equals( SETTINGS ) ) {

			logger.debug( "{} found settings: environment: {}", project.getSourceFileName( ), environmentName ) ;
			return ;

		}

		if ( project.getLifeCycleToHostMap( ).get( environmentName ) == null ) {

			project.getLifeCycleToHostMap( ).put( environmentName, new ArrayList<String>( ) ) ;

		}

		if ( project.getLifecycleToClusterMap( ).get( environmentName ) == null ) {

			ArrayList<String> clusterNames = new ArrayList<String>( ) ;
			project.getLifecycleToClusterMap( ).put( environmentName, clusterNames ) ;

		}

		if ( settings.isBaseEnvOnly( ) ) {

			logger.debug( "Environment '{}' marked as base environment - skipping processing", environmentName ) ;
			return ;

		}

		// kubernetes clusters require providers are processed first
		var defaultKubernetesProvider = "" ;

		var clusterNamesAdded = new ArrayList<String>( ) ;

		for ( var clusterName : clusterNamesWithKubernetesClustersLast( environmentName, project ) ) {

			String clusterPath = "/" + ENVIRONMENTS + "/" + environmentName + "/" + clusterName ;
			JsonNode rawCluster = project.getSourceDefinition( ).at( clusterPath ) ;
			logger.trace( "{} rawCluster: {}", clusterName, rawCluster ) ;

			var mergedCluster = buildMergedClusterDefinition( clusterName, project, environmentName ) ;

			if ( rawCluster.isObject( ) ) {

				mergedCluster.setAll( (ObjectNode) rawCluster ) ;

			}

			logger.trace( "{} environment: {} cluster: {} \n  cluster definition: {}",
					project.getName( ), environmentName, clusterName, mergedCluster ) ;

			if ( ! rawCluster.path( DefinitionConstants.enabled.key( ) ).asBoolean( true ) ) {

				continue ;

			}

			clusterNamesAdded.add( clusterName ) ;

			ClusterType clusterType = ClusterType.getPartitionType( mergedCluster, clusterPath ) ;

			if ( clusterType == ClusterType.KUBERNETES_PROVIDER
					&& StringUtils.isEmpty( defaultKubernetesProvider ) ) {

				defaultKubernetesProvider = clusterName ;

			}

			load_cluster(
					clusterName,
					mergedCluster,
					project,
					environmentName,
					settings,
					defaultKubernetesProvider,
					parsingResultsBuffer ) ;

		}

		if ( StringUtils.isNotEmpty( defaultKubernetesProvider ) ) {
			// enable namespace monitoring

			clusterNamesAdded.add( NAMESPACE_MONITORS ) ;

			var clusterDefinition = jsonMapper.createObjectNode( ) ;
			clusterDefinition.put( "type", ClusterType.KUBERNETES.getJson( ) ) ;
			clusterDefinition.put( "note", "auto-generated by " + getClass( ).getName( ) ) ;
			clusterDefinition.put( NAMESPACE_MONITORS, true ) ;

			var clusterServices = clusterDefinition.putArray( CLUSTER_TEMPLATE_REFERENCES ) ;

			namespaceMonitors( project.getName( ) ).stream( )

					.filter( name -> ! name.equals( "disabled" ) )

					.filter( StringUtils::isNotEmpty )

					.filter( this::is_valid_model_name )

					.forEach( namespace -> {

						clusterServices.add( getNsMonitorName( namespace ) ) ;

					} ) ;

			logger.info( "Generating namespace monitor cluster: {} {}", NAMESPACE_MONITORS, CSAP.jsonPrint(
					clusterDefinition ) ) ;

			load_cluster(
					NAMESPACE_MONITORS,
					clusterDefinition,
					project,
					environmentName,
					settings,
					defaultKubernetesProvider,
					parsingResultsBuffer ) ;

		}

		project.getLifecycleToClusterMap( ).get( environmentName ).addAll( clusterNamesAdded ) ;

		return ;

	}

	public String getNsMonitorName ( String serviceName ) {

		return NAMESPACE_MONITOR_PREFIX + serviceName ;

	}

	private int loadedNamespaceHash = -2 ;
	private int lastNamespaceHash = -1 ;

	//
	// polled by application worker thread
	//
	boolean isNamespacesStableAndModified ( ) {

		var namespacesModified = false ;

		var discoveredNamespaces = namespaceMonitors( Application.ALL_PACKAGES ) ;
		var currentNamespaceHash = discoveredNamespaces.hashCode( ) ;

		if ( currentNamespaceHash != loadedNamespaceHash
				&& currentNamespaceHash == lastNamespaceHash ) {

			namespacesModified = true ;
			loadedNamespaceHash = currentNamespaceHash ;

		}

		lastNamespaceHash = currentNamespaceHash ;

		logger.debug( CSAP.buildDescription( "Namespace hashes: reloads when current == last and not != loaded",
				"current", currentNamespaceHash,
				"last", lastNamespaceHash,
				"loaded", loadedNamespaceHash,
				"namespacesModified", namespacesModified,
				"discoveredNamespaces", discoveredNamespaces ) ) ;

		if ( namespacesModified ) {

			logger.info( CSAP.buildDescription( "Kubernetes Namespaces Changed",
					"namespacesModified", namespacesModified,
					"current", currentNamespaceHash,
					"last", lastNamespaceHash,
					"loaded", loadedNamespaceHash,
					"discoveredNamespaces", discoveredNamespaces ) ) ;

		}

		return namespacesModified ;

	}

	public List<String> namespaceMonitors ( String projectName ) {

		if ( csapApis.application( ).getRootProject( ) != null ) {

			var environmentNamespaces = csapApis.application( ).environmentSettings( ).getMonitoredNamespaces( ) ;

			//
			// Env settings can be used to either disable or hardcode a list of namespaces
			//
			if ( environmentNamespaces != null
					&& environmentNamespaces.size( ) > 0 ) {

				return environmentNamespaces ;

			}

		}

		//
		// If not specified - use the latest discovery
		//
		var discoveredNamespaces = new HashSet<String>( ) ;

		try {

			if ( csapApis.application( ).isAgentProfile( ) ) {

				for ( var container : csapApis.osManager( ).getDockerContainerProcesses( ) ) {

					if ( StringUtils.isNotEmpty( container.getPodNamespace( ) ) ) {

						discoveredNamespaces.add( container.getPodNamespace( ) ) ;

					}

				}

			} else {

				// admin uses ALL namespaces in ALL hosts in order to aggregate results on UI

				if ( csapApis.application( ).getRootProject( ) != null ) {

					var allHostReport = csapApis.application( ).healthManager( ).build_host_report( projectName ) ;

					var namespaceNodes = allHostReport.findValues( "namespaces" ).stream( )
							.collect( Collectors.toList( ) ) ;

					logger.debug( "namespaceNodes: {}", namespaceNodes ) ;

					namespaceNodes.stream( )

							.flatMap( namespaces -> CSAP.jsonStream( namespaces ) )

							.map( JsonNode::asText )

							.forEach( namespace -> discoveredNamespaces.add( namespace ) ) ;

				} else {

					logger.info(
							"Initial project load - namespace scanning deferred until initial agent reports have been received" ) ;

				}

			}

		} catch ( Exception e ) {

			logger.warn( "Failed scanning for namespaces {}", CSAP.buildCsapStack( e ) ) ;

		}

		var nsList = List.of( "default", K8.systemNamespace.val( ) ) ;

		if ( discoveredNamespaces.size( ) > 0 ) {

			// sort list for hashes
			nsList = new ArrayList<>( discoveredNamespaces ) ;
			Collections.sort( nsList ) ;

		}

		logger.debug( "discoveredNamespaces: {}", nsList ) ;

		return nsList ;

	}

	public List<String> clusterNamesAsDefined ( String environmentName , Project project ) {

		var environment = get_environment_definition( environmentName, project.getSourceDefinition( ) ) ;

		return clusterNames( environment ) ;

	}

	public List<String> clusterNames ( JsonNode environmentDefinition ) {

		var clusterNames = CSAP.asStreamHandleNulls( environmentDefinition )
				.filter( fieldName -> ! fieldName.equals( SETTINGS ) )
				.collect( Collectors.toList( ) ) ;
		return clusterNames ;

	}

	public List<String> clusterNamesWithKubernetesClustersLast ( String environmentName , Project project ) {

		var environments = get_environment_definition( environmentName, project.getSourceDefinition( ) ) ;

		var clusterNames = clusterNamesAsDefined( environmentName, project ) ;

		var clusterNamesWithKubernetesClustersLast = CSAP.asStreamHandleNulls( environments )
				.filter( clusterName -> ! clusterName.equals( SETTINGS ) )
				.sorted( comparing( clusterName -> sortWithKubernetesLast( environmentName, project, clusterName ) ) )
				.collect( Collectors.toList( ) ) ;

		logger.debug( "clusters discovered: {} \n\t sorted (kubernetes last): {}", clusterNames,
				clusterNamesWithKubernetesClustersLast ) ;
		return clusterNamesWithKubernetesClustersLast ;

	}

	private Boolean sortWithKubernetesLast ( String environmentName , Project project , String clusterName ) {

		String clusterPath = "/" + ENVIRONMENTS + "/" + environmentName + "/"
				+ clusterName ;

		JsonNode clusterDefinition = project.getSourceDefinition( )
				.at( clusterPath ) ;

		var mergedCluster = buildMergedClusterDefinition( clusterName, project, environmentName ) ;

		if ( clusterDefinition.isObject( ) ) {

			mergedCluster.setAll( (ObjectNode) clusterDefinition ) ;

		}

		ClusterType clusterType = ClusterType.getPartitionType( mergedCluster, "kubeClusterLast: " + clusterPath ) ;

		return clusterType == ClusterType.KUBERNETES ;

	}

	public static final String UNABLE_TO_ACTIVATE_ENV = "Unable to determine which application environment to activate using host" ;

	boolean isValidHostName ( String hostName ) {

		//
		// Loose checking - simple or fqdn names
		//
		if ( StringUtils.isEmpty( hostName )
				|| ! StringUtils.isAlpha( hostName.substring( 0, 1 ) )
				|| ! hostName.equals( hostName.toLowerCase( ) )
				|| ! StringUtils.isAlphanumeric(
						hostName
								.replace( "-", "" )
								.replace( ".", "" ) ) ) {

			return false ;

		}

		return true ;

	}

	private void validateRootProject ( Project rootProject , StringBuilder validationResults ) throws IOException {

		if ( rootProject.getHostsCurrentLc( ) == null ) {

			logger.warn(
					"\n\n\n " + UNABLE_TO_ACTIVATE_ENV
							+ " '{}', fqdn: '{}': ensure it is assigned to at least one cluster \n\n\n",
					csapApis.application( ).getCsapHostName( ), csapApis.application( ).getHostFqdn( ) ) ;

			// Runtime.getRuntime( ).halt( 999 ) ;

			throw new IOException(
					UNABLE_TO_ACTIVATE_ENV + " '"
							+ csapApis.application( ).getCsapHostName( )
							+ "': ensure it is assigned to at least one cluster" ) ;

		}

		for ( var host : rootProject.getHostsCurrentLc( ) ) {

			if ( ! isValidHostName( host ) ) {

				String message = CsapConstants.CONFIG_PARSE_WARN
						+ "Host name validation failure: " + host + " (lowercase alphanumeric, simple host or fqdn)\n" ;
				logger.warn( message ) ;
				validationResults.append( message ) ;

			}

		}

		var checkForDuplicateSuffix = new StringBuilder( "" ) ;

		var serviceSummaries = new ArrayList<String>( ) ;

		for ( var host : rootProject.getHostToServicesMap( ).keySet( ) ) {
			// sbuf.append("\n\t" + host + "\n\t\t");

			var servicesOnHost = rootProject.getServicesListOnHost( host ) ;

			// check for duplicate instances

			for ( var instance : servicesOnHost ) {

				if ( serviceSummaries.contains( instance.toSummaryString( ) ) ) {

					String message = CsapConstants.CONFIG_PARSE_WARN
							+ "Host: " + host + " has multiple instances of service: "
							+ instance.getName( )
							+ ". It is recommended assign each service once (via cluster assignment) \n" ;
					logger.warn( message ) ;
					validationResults.append( message ) ;

				} else {

					serviceSummaries.add( instance.toSummaryString( ) ) ;

				}

			}

			// check for Duplicate ports
			HashMap<String, String> portToLifeAndService = new HashMap<String, String>( ) ;

			for ( ServiceInstance instance : servicesOnHost ) {

				if ( portToLifeAndService.containsKey( instance.getPort( ) ) ) {

					String message = CsapConstants.CONFIG_PARSE_WARN
							+ instance.getLifecycle( )
							+ ":"
							+ instance.getName( )
							+ " on host "
							+ instance.getHostName( )
							+ " contains a duplicate port entry: "
							+ instance.getPort( )
							+ ". Port should be changed vi the UI to ensure it is unique on each host. The other instance with this port:"
							+ portToLifeAndService.get( instance.getPort( ) ) + "\n" ;
					logger.warn( message ) ;
					validationResults.append( message ) ;

				} else if ( ! instance.is_csap_api_server( ) && ! instance.getPort( ).equals( "0" ) ) {

					portToLifeAndService.put(
							instance.getPort( ),
							instance.getLifecycle( ) + ":" + instance.getName( ) ) ;

				}

			}

			// All services on host will have the same image
			boolean isFactory = servicesOnHost.get( 0 ).is_cluster_single_host_modjk( ) ;

			if ( isFactory ) {

				String hostSuffix = getPartitionRoutingId( host, validationResults ) ;

				if ( hostSuffix != null ) {

					if ( checkForDuplicateSuffix.indexOf( "," + hostSuffix + "," ) != -1 ) {

						validationResults
								.append( CsapConstants.CONFIG_PARSE_ERROR
										+ "Duplicate singleVmPartion host suffix found:"
										+ host
										+ " singleVmPartion host names must be in the form x-y where y is used for oracle instance name and modjk routing, and must be unique" ) ;

					} else {

						checkForDuplicateSuffix.append( "," + hostSuffix + "," ) ;

					}

				}

			}

		}

	}

	private String getPartitionRoutingId ( String host , StringBuilder resultsBuf ) {

		String factorySuffix = null ;
		String[] hostNameArray = host.split( "-" ) ;

		if ( hostNameArray.length != 2 && hostNameArray.length != 3 && ! host.equals( "localhost" ) ) {

			factorySuffix = host.trim( ).toLowerCase( ) ;
			logger.warn( "host name is not in the form of x-y. Defaulting to use host as the routing suffix: '{}'",
					factorySuffix ) ;

		} else {

			if ( hostNameArray.length == 2 ) {

				factorySuffix = hostNameArray[1] ;

			} else if ( hostNameArray.length == 3 ) {

				factorySuffix = hostNameArray[1] + hostNameArray[2] ;

			}

		}

		return factorySuffix ;

	}

	@Autowired
	StandardPBEStringEncryptor encryptor ;

	private StringBuilder activate ( Project rootProject ) {

		csapApis.application( ).setCsapHostEnvironmentName( rootProject.getHostEnvironmentName( ) ) ;

		if ( rootProject.getReleaseModelCount( ) > 1 ) {

			rootProject.buildAllProjectsModel( csapApis.application( ).applicationDefinition( ) ) ;

		}

		Collections.sort( rootProject.getVersionList( ) ) ;

		setActiveRootProject( rootProject ) ;

		StringBuilder modelActivationResults = generateStatusMessage( rootProject ) ;

		logger.warn( "Application Activation \n" + modelActivationResults.toString( ) ) ;

		StringBuilder activateResults = new StringBuilder( ) ;
		activateResults.append( modelActivationResults ) ;

		// New definition means new processes need to be scanned.
		if ( csapApis.osManager( ) == null ) {

			logger.warn( CsapApplication.testHeader(
					"Application manager has a null OsManger - OK for testing only" ) ) ;
			return activateResults ;

		}

		csapApis.application( ).activateProject( rootProject ) ;

		return activateResults ;

	}

	private StringBuilder generateStatusMessage ( Project rootProject ) {

		StringBuilder modelActivationResults = new StringBuilder( ) ;

		modelActivationResults
				.append( CSAP.padNoLine( "Application Name" )
						+ rootProject.getEnvironmentNameToSettings( ).firstEntry( ).getValue( )
								.getApplicationName( ) ) ;

		var serviceInstanceCount = rootProject.getAllPackagesModel( ).getServiceToAllInstancesMap( ).values( ).stream( )
				.mapToInt( ArrayList::size )
				.sum( ) ;
		modelActivationResults.append( CSAP.padLine( "Total Services" ) + serviceInstanceCount ) ;
		modelActivationResults.append( CSAP.padLine( "Total Hosts" ) + rootProject.getAllPackagesModel( )
				.getHostToServicesMap( ).size( ) ) ;

		modelActivationResults.append( "\n" + CSAP.padLine( "Current Host" ) + csapApis.application( )
				.getCsapHostName( ) ) ;

		modelActivationResults.append( CSAP.padLine( "Old Environment" ) + csapApis.application( )
				.getCsapHostEnvironmentName( ) ) ;
		modelActivationResults.append( CSAP.padLine( "New Environment" ) + rootProject.getHostEnvironmentName( ) ) ;

		modelActivationResults.append( CSAP.padLine( "Environments" ) + rootProject.getEnvironmentNameToSettings( )
				.keySet( ) ) ;

		var rootEnvName = rootProject.getHostEnvironmentName( ) ;

		var rootSettings = rootProject.getEnvironmentNameToSettings( ).get( rootEnvName ) ;
		modelActivationResults.append( CSAP.padLine( "Event url" ) + rootSettings.getEventUrl( ) ) ;

		// modelActivationResults.append( "\n" +
		// csapApis.application().rootProjectEnvSettings().summarySettings() ) ;

		String allModelsDescription = rootProject
				.getProjects( )
				.map( project -> {

					StringBuilder modelInfo = new StringBuilder( ) ;
					var packageTitle = project.getName( ) ;

					if ( project.getName( ).equals( rootProject.getActiveModel( ).getName( ) ) ) {

						packageTitle = "*" + packageTitle + "*" ;

					}

					var numServices = -1 ;

					if ( project.getEnvToServiceMap( ).containsKey( rootEnvName ) ) {

						numServices = project.getEnvToServiceMap( ).get( rootEnvName ).size( ) ;

					}

					modelInfo.append( "\n" + CSAP.padLine( "Project" ) + CsapConstants.pad( packageTitle ) ) ;
					modelInfo.append( "   Services " + rootEnvName + ": " + numServices ) ;

					var numHosts = -1 ;

					if ( project.getEnvironmentNameToHostInfo( ).containsKey( rootEnvName ) ) {

						numHosts = project.getEnvironmentNameToHostInfo( ).get( rootEnvName ).size( ) ;

					}

					modelInfo.append( "   Hosts " + rootEnvName + ":" + numHosts + ", All:" + project
							.getHostToServicesMap( ).size( ) ) ;

					if ( project.getInstanceTotalCountInCurrentLC( ) > 0 ) {

						modelInfo.append( "    Old instance count: "
								+ project.getInstanceTotalCountInCurrentLC( ) ) ;

					}

					return modelInfo.toString( ) ;

				} )
				.reduce( "", ( a , b ) -> a + b ) ;

		modelActivationResults.append( allModelsDescription ) ;

		// String lifecyleHosts = rootProject.getHostsCurrentLc().stream()
		String lifecyleHosts = rootProject.getHostsForEnvironment( rootEnvName ).stream( )
				.map( name -> CSAP.pad( name ) )
				.collect( Collectors.joining( ) ) ;

		modelActivationResults.append( "\n" + CSAP.padLine( "Hosts" ) ) ;
		modelActivationResults.append( "\n\t" ) ;
		modelActivationResults.append( WordUtils.wrap( lifecyleHosts, 100, "\n\t", false ) ) ;

		// var lifecycleClusters =
		// rootProject.getClustersToHostMapInCurrentLifecycle().keySet().stream()
		var lifecycleClusters = rootProject.getLifecycleToClusterMap( ).get( rootEnvName ).stream( )
				.map( name -> CSAP.pad( name ) )
				.collect( Collectors.joining( ) ) ;

		modelActivationResults.append( "\n" + CSAP.padLine( "Clusters" ) ) ;
		modelActivationResults.append( "\n\t" ) ;
		modelActivationResults.append( WordUtils.wrap( lifecycleClusters, 100, "\n\t", false ) ) ;

		if ( csapApis.application( ).isAgentProfile( ) ) {

			String servicesOnHost = rootProject
					.getActiveModel( )
					.getServicesOnHost( csapApis.application( ).getCsapHostName( ) )
					.map( ServiceInstance::getName )
					.map( name -> CSAP.pad( name ) )
					.collect( Collectors.joining( ) ) ;

			modelActivationResults.append( "\n" + CSAP.padLine( "Services assigned to " + csapApis.application( )
					.getCsapHostName( ) ) ) ;
			modelActivationResults.append( "\n\t" ) ;
			modelActivationResults.append( WordUtils.wrap( servicesOnHost, 100, "\n\t", false ) ) ;

		}

		return modelActivationResults ;

	}

	private String checkForInvalidMongoKeys ( JsonNode definitionNode , String location ) {

		StringBuilder resultsBuf = new StringBuilder( "" ) ;

		// for ( String name : fieldNames( definitionNode ) ) {
		CSAP.asStreamHandleNulls( definitionNode ).forEach( name -> {

			JsonNode fieldValue = definitionNode.get( name ) ;

			if ( fieldValue.isObject( ) ) {

				resultsBuf.append( checkForInvalidMongoKeys( fieldValue, location + "," + name ) ) ;

			} else if ( name.contains( "." ) && ! location.contains( ",docker," ) ) {

				resultsBuf.append( CsapConstants.CONFIG_PARSE_WARN + " - \".\"  should not appear in: \"" + name
						+ "\" in definition file: " + location + "\n" ) ;

			}

		} ) ;

		return resultsBuf.toString( ) ;

	}

	private void buildServiceParseError ( String serviceName , String packageName , String message )
		throws IOException {

		throw new IOException( " Service: '" + serviceName + "' in package '" + packageName + "' - " + message ) ;

	}

	private void updateParseResults (
										String messageType ,
										StringBuilder resultsBuf ,
										String serviceName ,
										String packageName ,
										String message ) {

		ServiceBaseParser.updateServiceParseResults(
				resultsBuf,
				messageType,
				" Service: " + serviceName + "(" + packageName + ") - " + message ) ;

	}

	volatile private boolean isTest_FOR_LOGS_ONLY = false ;

	private void load_cluster (
								String clusterName ,
								JsonNode clusterDefinition ,
								Project project ,
								String environmentName ,
								EnvironmentSettings settings ,
								String defaultKubernetesProvider ,
								StringBuilder parsingMessages )

		throws IOException ,
		JsonParseException ,
		JsonMappingException {

		var projectFileName = project.getSourceFileName( ) ;

		logger.debug( "Environment: {}, cluster: {}, source: {} content: {}",
				environmentName, clusterName,
				projectFileName, CSAP.jsonPrint( clusterDefinition ) ) ;

		var clusterType = ClusterType.getPartitionType( clusterDefinition, projectFileName ) ;

		if ( clusterType == ClusterType.unknown ) {

			logger.warn( "{} Invalid Specification: {}, {}", projectFileName, environmentName + ":" + clusterName,
					CSAP.jsonPrint( clusterDefinition ) ) ;
			parsingMessages.append( "\n\t" + CsapConstants.CONFIG_PARSE_WARN
					+ projectFileName
					+ " Invalid cluster definition " + environmentName + ":" + clusterName ) ;
			return ;

		}

		if ( clusterDefinition.path( DEFINITION_MONITORS ).isObject( ) ) {

			load_cluster_monitor_settings( settings, clusterName, clusterDefinition ) ;

		}

		parsingMessages.append( "\n \t " + project.getSourceFileName( ) + "\t - \t" + clusterName ) ;

		load_cluster_services( parsingMessages, project, environmentName, clusterName, clusterDefinition,
				defaultKubernetesProvider ) ;

		load_cluster_metadata( project, environmentName, clusterName, clusterDefinition ) ;

	}

	public ObjectNode buildMergedClusterDefinition ( String clusterName , Project project , String environmentName ) {

		var envPath = "/" + ProjectLoader.ENVIRONMENTS + "/" + environmentName ;
		var envSettignsPath = envPath + "/" + SETTINGS ;
		var importEnvPath = envSettignsPath + "/" + ENVIRONMENT_IMPORTS ;

		var imports = project.getSourceDefinition( ).at( importEnvPath ) ;
		logger.trace( "path '{}; : {}", importEnvPath, imports ) ;

		var mergedCluster = jsonMapper.createObjectNode( ) ;

		if ( imports.isArray( ) ) {

			CSAP.jsonStream( imports )
					.map( JsonNode::asText )
					.forEach( importEnvName -> {

						logger.trace( "Importing settings from another lifecycle: {}, {}, {}",
								ProjectLoader.ENVIRONMENTS, importEnvName, clusterName ) ;

						var importClusterDef = project.getSourceDefinition( )
								.path( ProjectLoader.ENVIRONMENTS )
								.path( importEnvName )
								.path( clusterName ) ;

						if ( importClusterDef.isObject( ) ) {

							logger.trace( "project: {} env: {} cluster: {} - building merged cluster: /{}/{}/{}",
									project.getName( ), environmentName, clusterName,
									ProjectLoader.ENVIRONMENTS, importEnvName, clusterName ) ;

							mergedCluster.setAll( (ObjectNode) importClusterDef ) ;

						}

					} ) ;

		}

		return mergedCluster ;

	}

	private void load_cluster_metadata (
											Project project ,
											String environmentName ,
											String clusterName ,
											JsonNode clusterDefinition )
		throws IOException ,
		JsonParseException ,
		JsonMappingException {

		logger.trace( "{} Building cluster metadata: {}, clusterName: {}, definition: {}",
				project.getSourceFileName( ),
				environmentName, clusterName, clusterDefinition ) ;

		var clusterType = clusterDefinition.path( DefinitionConstants.clusterType.key( ) ).asText( ) ;
		// JsonNode clusterNode = getClusterPartionNode( clusterDefinition ) ;
		List<String> hostList = clusterHosts( clusterDefinition ) ;

		var clusterHosts = new ArrayList<String>( hostList ) ;
		Collections.sort( clusterHosts ) ;

		logger.debug( "{} environment {}, cluster: {}, \n\thosts: {}",
				project.getSourceFileName( ),
				environmentName, clusterName, clusterHosts ) ;

		project.getLifeClusterToHostMap( ).put(
				environmentName + ENVIRONMENT_CLUSTER_DELIMITER + clusterName, clusterHosts ) ;

		if ( clusterDefinition.has( CLUSTER_TEMPLATE_REFERENCES ) ) {

			ArrayList<String> serviceNames = jsonMapper.readValue( clusterDefinition.path( CLUSTER_TEMPLATE_REFERENCES )
					.traverse( ),
					new TypeReference<ArrayList<String>>( ) {
					} ) ;

			project.getLifeClusterToOsServices( ).put(
					environmentName + clusterName, serviceNames ) ;

			if ( ! project.getEnvToServiceMap( ).containsKey( environmentName ) ) {

				logger.debug( "adding: {}", environmentName ) ;
				project.getEnvToServiceMap( ).put( environmentName, new ArrayList<String>( ) ) ;

			}

			var envNames = project.getEnvToServiceMap( ).get( environmentName ) ;
			envNames.addAll( serviceNames ) ;

		}

		var clusterKey = environmentName + ENVIRONMENT_CLUSTER_DELIMITER + clusterName ;
		logger.debug( "for summary ui - adding {} type: {} current: {} ", clusterKey, clusterType ) ;
		project.getLifeClusterToTypeMap( ).put( clusterKey, clusterType ) ;

	}

	private void configureRemoteCollections (
												StringBuilder parsingResults ,
												Project testRootModel ,
												File applicationDefinitionFile ) {

		StringBuilder summary = new StringBuilder( ) ;

		testRootModel
				.getServiceConfigStreamInCurrentLC( )
				.filter( entriesWithRemoteCollections( ) )
				.forEach( configureRemoteCollection( summary, parsingResults ) ) ;

		logger.debug( "Remote Collection Services: \n{}", summary ) ;

		// On agent only?
	}

	public static Consumer<Map.Entry<String, List<ServiceInstance>>> configureRemoteCollection (
																									StringBuilder summary ,
																									StringBuilder parsingResults ) {

		return serviceEntry -> {

			List<ServiceInstance> serviceInstances = serviceEntry.getValue( ) ;
			ServiceInstance firstInstance = serviceInstances.get( 0 ) ;
			JsonNode remoteCollections = serviceInstances.get( 0 )
					.getAttributeAsJson( ServiceAttributes.remoteCollections ) ;

			summary.append( "\n\n name:" ) ;
			summary.append( serviceEntry.getKey( ) ) ;
			summary.append( "\t remoteCollection: " ) ;
			summary.append( remoteCollections ) ;

			if ( serviceInstances.size( ) != remoteCollections.size( ) ) {

				String msg = firstInstance.getErrorHeader( )
						+ "Invalid format for " + ServiceAttributes.remoteCollections
						+ " expected: " + serviceInstances.size( ) + " items, but found: "
						+ remoteCollections.size( ) ;
				ServiceBaseParser.updateServiceParseResults( parsingResults, CsapConstants.CONFIG_PARSE_WARN, msg ) ;

			} else if ( ! remoteCollections.isArray( ) ) {

				ServiceBaseParser.updateServiceParseResults(
						parsingResults, CsapConstants.CONFIG_PARSE_WARN,
						firstInstance.getErrorHeader( )
								+ "Invalid format for " + ServiceAttributes.remoteCollections
								+ " expected: array, found: " + remoteCollections.toString( ) ) ;

			} else {

				int remoteIndex = 0 ;

				for ( ServiceInstance instance : serviceInstances ) {

					instance.configureRemoteCollection( remoteIndex, parsingResults ) ;
					remoteIndex++ ;

				}

			}

		} ;

	}

	public static Predicate<Map.Entry<String, List<ServiceInstance>>> entriesWithRemoteCollections ( ) {

		return serviceEntry -> {

			List<ServiceInstance> serviceInstances = serviceEntry.getValue( ) ;

			if ( serviceInstances != null && serviceInstances.size( ) > 0 ) {

				JsonNode remoteCollections = serviceInstances.get( 0 ).getAttributeAsJson(
						ServiceAttributes.remoteCollections ) ;

				if ( remoteCollections != null ) {

					return true ;

				}

			}

			return false ;

		} ;

	}

	private void load_package_release_files (
												StringBuilder parsingResults ,
												Project rootProject ) {

		StringBuilder releaseLoadResults = new StringBuilder( ) ;

		// Go throught all the models updating version where needed.
		rootProject
				.getProjects( )
				.filter( project -> ! project.getName( ).equals( Project.GLOBAL_PACKAGE ) )
				.forEach( project -> {

					// Use path relative to parent to determin child
					File projectReleaseFile = project.getReleaseFile( rootProject.getSourceFile( ) ) ;
					logger.debug( "model: {} releaseFile: {}", project.getName( ), projectReleaseFile ) ;

					// logger.info( "Call path: {}",
					// Application.getCsapFilteredStackTrace( new Exception(
					// "calltree" ), "." ) );
					logger.debug( "{} Check for release file: {}",
							project.getName( ), projectReleaseFile.getAbsolutePath( ) ) ;

					if ( projectReleaseFile.exists( ) ) {

						if ( releaseLoadResults.length( ) == 0 ) {

							releaseLoadResults.append( "\n\n ============ Release Files Found ==========" ) ;

						}

						releaseLoadResults.append( "\n"
								+ project.getName( ) + " Release File: " + projectReleaseFile.getAbsolutePath( ) ) ;

						try {

							load_package_release_file( releaseLoadResults, projectReleaseFile, project ) ;

						} catch ( Exception e ) {

							releaseLoadResults
									.append( "\n" + CsapConstants.CONFIG_PARSE_WARN + projectReleaseFile.getName( )
											+ " failed to parse due to: "
											+ e.getClass( ).getName( ) ) ;
							logger.error( "Failed to parse: {}", projectReleaseFile.getAbsolutePath( ), e ) ;

						}

					} else {

						logger.debug( "Release file not found" ) ;

					}

				} ) ;

		if ( releaseLoadResults.length( ) == 0 ) {

			releaseLoadResults.append( "No release files found" ) ;

		}

		releaseLoadResults.append( "\n" ) ;

		logger.debug( releaseLoadResults.toString( ) ) ;
		parsingResults.append( releaseLoadResults ) ;

	}

	private void load_package_release_file (
												StringBuilder processResults ,
												File projectReleaseFile ,
												Project project )
		throws IOException {

		ObjectNode releaseVersions = definition_file_reader( projectReleaseFile, false ) ;

		CSAP.asStreamHandleNulls( releaseVersions )
				.forEach( serviceName -> {

					logger.debug( "{} : namesInLifecycle: {}", serviceName, project.getServiceNamesInLifecycle( ) ) ;

					if ( project
							.getServiceNamesInLifecycle( )
							.contains( serviceName ) ) {

						ObjectNode versionNode = (ObjectNode) releaseVersions.path( serviceName ) ;

						logger.debug( "{} : versionNode: {}", serviceName, versionNode ) ;

						if ( versionNode.has( project.getRootPackage( ).getHostEnvironmentName( ) ) ) {

							var newVersion = versionNode.get( project.getRootPackage( ).getHostEnvironmentName( ) )
									.asText( ) ;
							var artifactPieces = newVersion.split( ":" ).length ;

							if ( artifactPieces < 2 || artifactPieces > 4 ) {

								processResults.append( "\n\t" + CsapConstants.CONFIG_PARSE_WARN
										+ projectReleaseFile.getName( )
										+ " Invalid artifact format: " + newVersion ) ;

							} else {

								processResults.append( "\n\t Updating: " + serviceName + " to version: "
										+ newVersion ) ;

								project.getServiceInstances( serviceName )
										.forEach( serviceInstance -> {

											if ( serviceInstance.isIsAllowReleaseFileToOverride( ) ) {

												if ( serviceInstance.is_docker_server( ) && ( artifactPieces == 2 ) ) {

													serviceInstance.overrideDockerImageName( newVersion ) ;

												} else if ( artifactPieces == 4 ) {

													serviceInstance.setMavenId( newVersion ) ;

												} else {

													processResults.append( "\n\t" + CsapConstants.CONFIG_PARSE_WARN
															+ projectReleaseFile.getName( )
															+ " service " + serviceInstance.getName( )
															+ " has invalid artifact format: "
															+ newVersion ) ;

												}

											}

										} ) ;

							}

						}

					} else {

						processResults.append( "\n\t" + CsapConstants.CONFIG_PARSE_WARN
								+ projectReleaseFile.getName( )
								+ " Did not find: " + serviceName ) ;

					}

				} ) ;

	}

	private ServiceInstance load_java_service (
												StringBuilder serviceParseResults ,
												Project csapProject ,
												String fullEnvironmentName ,
												String serviceName ,
												JsonNode serviceDefinition ,
												String clusterName ,
												String hostName ,
												String port_from_cluster ,
												ClusterType cluster ) {

		logger.debug(
				"service: {}, project: {}, fullEnvironmentName: {},  hostName:{}, cluster: {}, type: {}",
				serviceName, csapProject.getName( ), fullEnvironmentName, hostName, clusterName, cluster ) ;

		logger.trace( "serviceDefinition:\n {}", serviceDefinition ) ;

		ServiceInstance serviceInstance = new ServiceInstance( ) ;
		serviceInstance.setName( serviceName ) ;
		serviceInstance.setHostName( hostName ) ;
		serviceInstance.setClusterType( cluster ) ;
		serviceInstance.setPlatformVersion( fullEnvironmentName ) ;
		serviceInstance.setLifecycle( clusterName ) ;

		// legacy for junits
		if ( serviceDefinition.path( "port" ).isMissingNode( ) ) {

			serviceInstance.setPort( port_from_cluster ) ;

			if ( port_from_cluster.endsWith( "x" ) ) {

				serviceInstance.setPort( port_from_cluster.substring( 0, 3 ) + "1" ) ;

				// serviceInstance.setJmxPort( port_from_cluster.substring( 0, 3 ) + "6" ) ;
			}

		}

		//
		// parse the definition included in the project file
		//
		serviceInstance.parseDefinition( csapProject.getSourceFileName( ), serviceDefinition, serviceParseResults ) ;

		if ( serviceInstance.getContext( ).length( ) == 0 ) {

			if ( ( ! serviceInstance.is_springboot_server( ) )
					|| ( serviceInstance.is_springboot_server( ) && serviceInstance.isTomcatAjp( ) ) ) {

				serviceInstance.setContext( serviceName ) ;

			}

		}

		if ( serviceName.equals( CsapConstants.AGENT_NAME ) ) {

			// override with spring
			serviceInstance.setPort( Integer.toString( csapApis.application( ).getAgentPort( ) ) ) ;
			var webContext = csapApis.application( ).getAgentContextPath( ) ;

			if ( webContext.length( ) > 1 ) {

				serviceInstance.setContext( webContext.substring( 1 ) ) ;

			}

		}

		//
		// customize with either environment attributes, or load from disk
		//
		merge_service_overrides(
				serviceInstance, serviceDefinition,
				fullEnvironmentName, csapProject,
				serviceParseResults ) ;

		// Update Globals
		if ( serviceInstance.getDefaultBranch( ) == null ) {

			serviceInstance.setDefaultBranch( "HEAD" ) ;

		}

		if ( serviceInstance.is_cluster_single_host_modjk( ) ) {

			String hostSuffix = getPartitionRoutingId( hostName, serviceParseResults ) ;

			if ( hostSuffix != null ) {

				if ( serviceInstance.getContext( ).length( ) != 0
						&& ! serviceInstance.getContext( ).startsWith( "http:" ) ) {

					serviceInstance.setContext( serviceInstance.getContext( ) + "-" + hostSuffix ) ;

				}

			}

		} else if ( serviceInstance.is_cluster_multi_host_modjk( ) ) {

			serviceInstance.setContext( serviceInstance.getContext( ) + "-"
					+ serviceInstance.getPlatformVersion( ) ) ;

		} else if ( ! serviceName.contains( CsapConstants.AGENT_NAME ) ) {
			// Validate JVM name is unique when multiVM is in use on BOTH
			// releasePackages

			String currentModelName = csapProject.getName( ) ;

			csapProject
					.getProjects( )
					.forEach( model -> {

						if ( model.getName( ).equals( currentModelName ) ) {

							return ;

						}

						if ( model.getServiceToAllInstancesMap( ).containsKey( serviceName ) ) {

							for ( ServiceInstance checkInstance : model.getServiceToAllInstancesMap( ).get(
									serviceName ) ) {

								if ( checkInstance.is_cluster_modjk( ) ) {

									updateParseResults(
											CsapConstants.CONFIG_PARSE_ERROR, serviceParseResults,
											serviceName, csapProject.getSourceFileName( ),
											" service found in multiple release packages, it must be unique"
													+ ".  Reference found in: " + model.getSourceFileName( )
													+ ", cluster: " + clusterName ) ;
									return ;

								}

							}

						}

					} ) ;

		}

		if ( serviceInstance.getContext( ).startsWith( "http" ) ) {

			serviceInstance.setUrl( serviceInstance.getContext( ) ) ;

		} else {

			var url = csapApis.application( )
					.buildJavaLaunchUrl(
							serviceInstance.getHostName( ),
							serviceInstance.getPort( ), "/" + serviceInstance.getContext( ) ) ;

			serviceInstance.setUrl( url ) ;

		}

		// override if launchUrl specified
		if ( serviceDefinition.has( "launchUrl" ) ) {

			var launchUrl = serviceDefinition.path( "launchUrl" ).asText( ) ;

			if ( ! launchUrl.startsWith( "http" ) ) {

				launchUrl = csapApis.application( ).buildJavaLaunchUrl(
						serviceInstance.getHostName( ),
						serviceInstance.getPort( ), "/" + launchUrl ) ;

			}

			serviceInstance.setUrl( launchUrl ) ;

		}

		if ( serviceInstance.getName( ).equalsIgnoreCase( CsapConstants.AGENT_NAME ) ) {

			csapProject.getLifeCycleToHostMap( ).get( fullEnvironmentName ).add( hostName ) ;

			var restUrl = csapApis.application( ).getAgentUrl( hostName, "/api/" ) ;

			// hostList.add(hostName);
			csapProject
					.getEnvironmentNameToHostInfo( )
					.get( fullEnvironmentName )
					.add(
							new HostInfo( hostName, restUrl ) ) ;

			csapProject.getHostsInAllLifecycles( ).add( serviceInstance.getHostName( ) ) ;

		}

		return serviceInstance ;

	}

	private void checkForLegacyServiceMigration ( File serviceDefinitionYaml ) {

		if ( ! serviceDefinitionYaml.isFile( ) ) {

			var legacyJsonFile = new File( serviceDefinitionYaml.getParentFile( ), CSAP_SERVICE_FILE_LEGACY ) ;

			if ( legacyJsonFile.isFile( ) ) {

				logger.info( CSAP.buildDescription( "Migrating legacy json",
						"legacy", legacyJsonFile.getAbsolutePath( ),
						"new", serviceDefinitionYaml.getAbsolutePath( ) ) ) ;

				try {

					var serviceAsJson = jsonMapper.readTree( legacyJsonFile ) ;

					var serviceAsYamlString = getProjectOperators( ).generateYaml( serviceAsJson ) ;

					FileUtils.write( serviceDefinitionYaml, serviceAsYamlString ) ;

				} catch ( Exception e ) {

					logger.warn( "Failed converting file: {}", CSAP.buildCsapStack( e ) ) ;

				}

			}

		}

	}

	private void merge_service_overrides (
											ServiceInstance serviceInstance ,
											JsonNode serviceDefinition ,
											String environmentName ,
											Project csapProject ,
											StringBuilder results ) {

		var serviceLifecycleSettings = serviceDefinition
				.path( ServiceAttributes.environmentOverload.json( ) )
				.path( environmentName ) ;

		if ( serviceLifecycleSettings.isObject( ) ) {

			serviceInstance.parseDefinition(
					csapProject.getSourceFileName( ),
					serviceLifecycleSettings,
					null ) ;

		}

		//
		// Load service overides if available
		//
		var resourceFolder = ServiceResources.serviceResourceFolder( serviceInstance.getName( ) ) ;
		var commonDefinition = new File( resourceFolder, "/common/" + CSAP_SERVICE_FILE ) ;
		load_service_override_file( commonDefinition, serviceInstance, csapProject, results ) ;

		//
		// check for file in base definitions
		//

		var imports = csapProject.getImports( environmentName ) ;

		if ( imports.isArray( ) ) {

			CSAP.jsonStream( imports )
					.map( JsonNode::asText )
					.forEach( importEnvName -> {

						var importEnvOverrideFile = new File( resourceFolder, "/" + importEnvName + "/"
								+ CSAP_SERVICE_FILE ) ;

						load_service_override_file( importEnvOverrideFile, serviceInstance, csapProject, results ) ;

					} ) ;

		}

		var currentEnvOverrideFile = new File( resourceFolder, "/" + environmentName + "/" + CSAP_SERVICE_FILE ) ;
		load_service_override_file( currentEnvOverrideFile, serviceInstance, csapProject, results ) ;

	}

	private void load_service_override_file (
												File csapServiceDefinitionFile ,
												ServiceInstance serviceInstance ,
												Project csapProject ,
												StringBuilder results ) {

		checkForLegacyServiceMigration( csapServiceDefinitionFile ) ;

		if ( ! csapServiceDefinitionFile.isFile( ) ) {

			logger.trace( "override file not found, ignoring: {}", csapServiceDefinitionFile.getAbsolutePath( ) ) ;

			return ;

		}

		logger.debug( "loading currentEnvOverrideFile: {}", csapServiceDefinitionFile.getAbsolutePath( ) ) ;

		try {

//				var serviceDefinitionOverride = jsonMapper.readTree( lifeDefinition ) ;
			var processMessages = jsonMapper.createObjectNode( ) ;
			var serviceDefinitionOverride = getProjectOperators( )
					.loadYaml( csapServiceDefinitionFile, processMessages )
					.get( 0 ) ;
			logger.debug( "serviceDefinitionOverride: {}", CSAP.jsonPrint( serviceDefinitionOverride ) ) ;

			serviceInstance.parseDefinition(
					csapProject.getSourceFileName( ),
					serviceDefinitionOverride,
					null ) ;

		} catch ( Exception e ) {

			var message = "Failed parsing default defintion, instead an empty default will be used." + CSAP
					.buildCsapStack( e ) ;
			results.append( message ) ;
			logger.warn( message ) ;

		}

	}

	private void load_cluster_monitor_settings (
													EnvironmentSettings environmentSettings ,
													String clusterName ,
													JsonNode clusterDefinition ) {

		environmentSettings
				.addClusterMonitor(
						clusterName,
						(ObjectNode) clusterDefinition.path( DEFINITION_MONITORS ) ) ;

		// this is a hook for cluster level settings that overwrite
		// the defaults
		List<JsonNode> hostArrays = clusterDefinition.findValues( CLUSTER_HOSTS ) ;

		for ( JsonNode hostArray : hostArrays ) {

			if ( hostArray.isArray( ) ) {

				CSAP.jsonStream( hostArray )
						.map( JsonNode::asText )
						.map( this::resolveHostSpecification )
						.forEach( hostName -> {

							logger.debug( "Updating host '{}' lifecycleSettings, cluster: '{}', Monitors: '{}'",
									hostName, clusterName,
									clusterDefinition.path( DEFINITION_MONITORS ).toString( ) ) ;
							environmentSettings
									.addHostLimits(
											hostName,
											(ObjectNode) clusterDefinition.path( DEFINITION_MONITORS ) ) ;

						} ) ;

			}

		}

	}

	private String resolveHostSpecification ( String hostPatternByComma ) {

		String[] hostAndPatterns = hostPatternByComma.split( "," ) ;
		String resolvedHost = hostAndPatterns[0] ;

		resolvedHost = hostPatternByComma.replaceAll( Matcher.quoteReplacement( "csap_def_template_host" ),
				csapApis.application( ).getCsapHostName( ) ) ;

		// support for optional specification: defaultHostName, pattern1, pattern2, ...
		// if match set current hostname
		for ( int i = 1; i < hostAndPatterns.length; i++ ) {

			if ( csapApis.application( ).getCsapHostName( ).matches( hostAndPatterns[i] ) ) {

				resolvedHost = csapApis.application( ).getCsapHostName( ) ;

			}

		}

		return resolvedHost ;

	}

	// private JsonNode getClusterPartionNode ( JsonNode clusterDefinition,
	// StringBuilder parseingNotes ) {
	//
	// // logger.debug( "Determining cluster for : {}", node.toString() );
	//
	// if ( clusterDefinition.has( DefinitionConstants.clusterType.key() ) ) {
	// return clusterDefinition ;
	// }
	//
	// String clusterPath = ClusterType.getPartitionType( clusterDefinition
	// ).getJson() ;
	// logger.debug( "clusterPath: {}, \t node: {}", clusterPath, clusterDefinition
	// ) ;
	// JsonNode result = clusterDefinition.path( clusterPath ) ;
	//
	// if ( result.isMissingNode() ) {
	// logger.warn( "INVALID cluster specification - clusterPath: {}, \t node: {}",
	// clusterPath, clusterDefinition ) ;
	// return clusterDefinition.path( PARSER_CLUSTER_VERSION ) ;
	// }
	//
	// return result ;
	// }

	private boolean validateService (
										StringBuilder resultsBuf ,
										String serviceName ,
										JsonNode serviceDefinition ,
										Project model ,
										String platformLifeCycle ,
										String platformSubLife )
		throws IOException {

		if ( is_invalid_model_name( serviceName ) ) {

			// buildServiceParseError( serviceName, model.getReleasePackageFileName(),
			// ERROR_INVALID_CHARACTERS + ". Reference found in lifecycle: " +
			// platformLifeCycle
			// + ", cluster: " + platformSubLife ) ;

			updateParseResults(
					CsapConstants.CONFIG_PARSE_WARN, resultsBuf,
					serviceName, model.getSourceFileName( ),
					ERROR_INVALID_CHARACTERS + " Reference found in lifecycle: " + platformLifeCycle
							+ ", cluster: " + platformSubLife ) ;

		}

		if ( serviceDefinition.isMissingNode( ) ) {

			if ( serviceName.equals( CsapConstants.AGENT_NAME ) ) {

				buildServiceParseError( serviceName, model.getSourceFileName( ),
						CsapConstants.MISSING_SERVICE_MESSAGE + " Reference found in lifecycle: " + platformLifeCycle
								+ ", cluster: " + platformSubLife ) ;

			} else {

				// logger.warn( "Missing service: {}, in {}", serviceName,
				// model.getReleasePackageFileName() );

				updateParseResults(
						CsapConstants.CONFIG_PARSE_WARN, resultsBuf,
						serviceName, model.getSourceFileName( ),
						CsapConstants.MISSING_SERVICE_MESSAGE + " Reference found in lifecycle: " + platformLifeCycle
								+ ", cluster: " + platformSubLife ) ;

				return false ;

			}

		}

		return true ;

	}

	boolean allowLegacy = false ;

	public void setAllowLegacyNames ( boolean allow ) {

		this.allowLegacy = allow ;

	}

	static boolean legacyWarnings = true ;

	public boolean is_valid_model_name ( String modelAttributeName ) {

		return ! is_invalid_model_name( modelAttributeName ) ;

	}

	public boolean is_invalid_model_name ( String modelAttributeName ) {

		if ( ! modelAttributeName.equals( modelAttributeName.toLowerCase( ) ) ) {

			if ( allowLegacy ) {

				if ( legacyWarnings ) {

					legacyWarnings = false ;
					logger.warn( CsapApplication.testHeader(
							"deprecated attribute format: '{}', migrate to host-naming-convention" ),
							modelAttributeName ) ;

				}

			} else {

				return true ;

			}

		}

		return ! StringUtils.isAlphanumeric( modelAttributeName.replace( "-", "" ) ) ;

	}

	private List<String> clusterHosts ( JsonNode clusterNode ) {

		return CSAP.jsonStream( clusterNode.path( CLUSTER_HOSTS ) )
				.map( JsonNode::asText )
				.map( this::resolveHostSpecification )
				.collect( Collectors.toList( ) ) ;

	}

	private void load_cluster_services (
											StringBuilder resultsBuf ,
											Project project ,
											String environmentName ,
											String clusterName ,
											JsonNode clusterDefinition ,
											String defaultKubernetesProvider )
		throws IOException ,
		JsonParseException ,
		JsonMappingException {

		var serviceNames = CSAP.jsonStream(
				clusterDefinition.path( CLUSTER_TEMPLATE_REFERENCES ) )
				.map( JsonNode::asText )
				.collect( Collectors.toList( ) ) ;

		var clusterType = ClusterType.getPartitionType( clusterDefinition ) ;

		for ( var serviceName : serviceNames ) {

			logger.debug( "Loading: {} clusterType: {}, lifecycle: {} ", serviceName, clusterType.getJson( ),
					environmentName ) ;

			var serviceDefinition = project.load_service_definition( serviceName ) ;

			if ( clusterDefinition.path( NAMESPACE_MONITORS ).asBoolean( ) ) {

				serviceDefinition = buildNamespaceMonitor( project, serviceName ) ;

			}

			// core semantic checks
			var isValid = validateService( resultsBuf, serviceName, serviceDefinition, project, environmentName,
					clusterName ) ;

			if ( ! isValid ) {

				logger.info( "{} is not a valid service definition - skipping", serviceName ) ; //
				continue ;

			}

			var hostNames = clusterHosts( clusterDefinition ) ;

			var k8MasterHostNames = jsonMapper.createArrayNode( ) ;

			if ( clusterType == ClusterType.KUBERNETES ) {

				String k8Provider = clusterDefinition.path(
						ClusterType.KUBERNETES_PROVIDER.getJson( ) )
						.asText( defaultKubernetesProvider ).trim( ) ;

				JsonNode providerDefinition = get_environment_definition(
						environmentName,
						project.getSourceDefinition( ) )
								.path( k8Provider ) ;

				if ( providerDefinition.at( "/masters" ).isArray( ) ) {

					k8MasterHostNames = (ArrayNode) providerDefinition.at( "/masters" ) ;

				}

				if ( k8MasterHostNames.size( ) == 1 ) {

					// support single node template
					k8MasterHostNames.add( resolveHostSpecification( k8MasterHostNames.get( 0 ).asText( ) ) ) ;
					k8MasterHostNames.remove( 0 ) ;

					// k8MasterHostNames = resolveHostSpecification(
					// providerDefinition.at( "/masters/0" ).asText( "k8-master-not-defined" ) ) ;
				}

				logger.debug(
						"{} k8Provider: {} , k8MasterHostName: {} \n\t clusterMap: {}, \n\t providerDefinition: {}",
						serviceName,
						k8Provider,
						k8MasterHostNames.toString( ),
						project.getLifeCycleToHostMap( ),
						providerDefinition.toString( ) ) ;

				String providerLookup = environmentName + ENVIRONMENT_CLUSTER_DELIMITER + k8Provider ;
				logger.debug( "looking up hosts for provider: {}", providerLookup ) ;

				hostNames = project.getHostsForEnvironment( providerLookup ) ;

				if ( hostNames == null ) {

					updateParseResults(
							CsapConstants.CONFIG_PARSE_WARN, resultsBuf,
							serviceName, project.getSourceFileName( ),
							" Unable to locate kubernetes provider: '" + providerLookup
									+ "', ensure definition reference exists." ) ;

				}

			}

			if ( hostNames == null || hostNames.size( ) == 0 ) {

//
//				hostNames = new ArrayList<>( ) ;
				updateParseResults(
						CsapConstants.CONFIG_PARSE_WARN, resultsBuf,
						serviceName, project.getSourceFileName( ),
						" Unable to determine hosts - verify  cluster definition." ) ;

				logger.warn( "{} : unable to determine hosts in cluster {}", serviceName, clusterName ) ;

			}

			String fullClusterName = environmentName + ENVIRONMENT_CLUSTER_DELIMITER + clusterName ;
			logger.debug( "fullClusterName: {} hostList: {}", fullClusterName, hostNames ) ;

			project.getLifeCycleToHostMap( ).put( fullClusterName, new ArrayList<String>( hostNames ) ) ;

			if ( ! project.getEnvironmentAndClusterNames( ).contains( fullClusterName ) ) {

				project.getEnvironmentAndClusterNames( ).add( fullClusterName ) ;

			}

			load_service_instances( resultsBuf, project, environmentName, clusterName, clusterDefinition,
					clusterType, serviceName, serviceDefinition, hostNames, k8MasterHostNames,
					fullClusterName ) ;

		}

		return ;

	}

	private ObjectNode buildNamespaceMonitor ( Project project , String namespaceHyphenServiceName ) {

		var serviceName = namespaceHyphenServiceName.split( "-", 2 )[1] ;

		var testForOverrideMonitor = project.findAndCloneServiceDefinition( namespaceHyphenServiceName ) ;

		if ( testForOverrideMonitor.isMissingNode( ) ) {

			testForOverrideMonitor = project.findAndCloneServiceDefinition( NAMESPACE_MONITOR_TEMPLATE ) ;

		}

		var namespaceMonitor = (ObjectNode) testForOverrideMonitor ;

		var locator = (ObjectNode) namespaceMonitor.path( ServiceAttributes.dockerSettings.json( ) ).path(
				C7.locator.val( ) ) ;
		locator.put( C7.podNamespace.val( ), serviceName ) ;

		logger.debug( "{} - {} created: {}", namespaceHyphenServiceName, serviceName, CSAP.jsonPrint(
				namespaceMonitor ) ) ;

		return namespaceMonitor ;

	}

	// check for duplicate ports across all services
	ArrayList<String> portCheckList ;

	private void load_service_instances (
											StringBuilder resultsBuf ,
											Project project ,
											String environmentName ,
											String clusterName ,
											JsonNode clusterDefinition ,
											ClusterType clusterType ,
											String serviceName ,
											JsonNode serviceDefinition ,
											List<String> hostNames ,
											ArrayNode k8MasterHostNames ,
											String fullClusterName )
		throws IOException ,
		JsonParseException ,
		JsonMappingException {

		var serviceRuntime = serviceDefinition.path( SERVER ).asText( ProcessRuntime.csap_api.getId( ) ) ;

		for ( var serviceHostName : hostNames ) {

			if ( serviceName.equals( CsapConstants.AGENT_NAME ) ) {

				// Agent added at end of parsing, and assigned to every base os cluster
				if ( serviceDefinition.isObject( ) ) {

					if ( ! serviceDefinition.has( AGENT_AUTO_CLUSTER ) ) {

						( (ObjectNode) serviceDefinition ).putObject( AGENT_AUTO_CLUSTER ) ;

					}

					var agentAutoCluster = (ObjectNode) serviceDefinition.path( AGENT_AUTO_CLUSTER ) ;
					agentAutoCluster.put( environmentName + "Cluster", fullClusterName ) ;

				}

				continue ;

			}

			ServiceInstance serviceInstance ;
			StringBuilder serviceParseResults = new StringBuilder( ) ;

			if ( ProcessRuntime.isJavaServer( serviceRuntime ) ) {

				// legacy loader
				serviceInstance = load_java_service(
						serviceParseResults, project, environmentName, serviceName,
						serviceDefinition,
						fullClusterName, serviceHostName, "0",
						clusterType ) ;

			} else {

				serviceInstance = load_api_service(
						serviceHostName, serviceName,
						clusterType,
						serviceDefinition,
						serviceParseResults, project,
						fullClusterName,
						environmentName ) ;

				if ( clusterType == ClusterType.KUBERNETES_PROVIDER ) {

					addKubernetesPerformanceMonitors( project, environmentName, serviceName, serviceDefinition,
							serviceHostName, serviceInstance ) ;

				}

			}

			if ( serviceParseResults.indexOf( CsapConstants.CONFIG_PARSE_ERROR ) > 0 ) {

				logger.info( "{} Found errors during parsing, skipping: {}", serviceName, serviceParseResults ) ;
				var serviceResults = serviceParseResults.toString( ).replaceAll( CsapConstants.CONFIG_PARSE_ERROR,
						CsapConstants.CONFIG_PARSE_WARN ) ;
				resultsBuf.append( serviceResults ) ;
				break ;

			}

			// portChecks only done on non kubernetes services
			var servicePort = serviceInstance.getPort( ) ;

			if ( ! servicePort.equals( "0" ) && ! serviceInstance.is_cluster_kubernetes( ) ) {

				if ( portCheckList.contains( serviceHostName + servicePort ) ) {

					updateParseResults( CsapConstants.CONFIG_PARSE_WARN, resultsBuf,
							serviceName, project.getSourceFileName( ),
							WARNING_DUPLICATE_HOST_PORT + " " + serviceHostName + ":" + servicePort
									+ ".  Reference found in lifecycle: " + environmentName
									+ ", cluster: " + clusterName ) ;
					continue ;

				} else {

					portCheckList.add( serviceHostName + servicePort ) ;

				}

			}

			if ( k8MasterHostNames != null ) {

				serviceInstance.setKubernetesMasterHostNames( k8MasterHostNames ) ;

				ArrayList<String> k8Masters = jsonMapper.readValue( k8MasterHostNames.traverse( ),
						new TypeReference<ArrayList<String>>( ) {
						} ) ;

				if ( k8Masters.contains( serviceHostName ) ) {

					serviceInstance.setKubernetesMaster( true ) ;

				}

				var k8Namespace = clusterDefinition.path(
						K8.clusterNamespace.val( ) )
						.asText( "default" ).trim( ) ;

				//
				// support optional namespace overrides on a per service basis. eg. kubernetes dashboard
				//
				if ( serviceInstance.getDockerSettings( ) != null
						&& serviceInstance.getDockerSettings( ).has( C7.kubernetesNamespace.val( ) ) ) {

					k8Namespace = serviceInstance.getDockerSettings( ).path( C7.kubernetesNamespace.val( ) )
							.asText( "default" ) ;

				}

				serviceInstance.setKubernetesNamespace( k8Namespace ) ;

			}

			if ( clusterType == ClusterType.KUBERNETES_PROVIDER ) {

				configureKubernetesMasters( clusterDefinition, serviceHostName, serviceInstance ) ;

			}

			add_service_to_project( project, serviceInstance ) ;

		}

		return ;

	}

	private void configureKubernetesMasters (
												JsonNode clusterDefinition ,
												String serviceHostName ,
												ServiceInstance serviceInstance ) {

		if ( csapApis.kubernetes( ) != null
				&& serviceInstance.getName( ).matches( KubernetesIntegration.getServicePattern( ) )
				&& ( csapApis.application( ).getCsapHostName( ).equals( serviceHostName )
						|| csapApis.application( ).getCsapHostName( ).equals( csapApis.application( ).hostShortName(
								serviceHostName ) ) ) ) {

			csapApis.kubernetes( ).setDiscoveredName( serviceInstance.getName( ) ) ;

		}

		JsonNode masters = clusterDefinition.path( K8.masters.val( ) ) ;

		if ( masters.isArray( ) ) {

			serviceInstance.setKubernetesMasterHostNames( (ArrayNode) masters ) ;
			serviceInstance
					.setKubernetesMasterDns( clusterDefinition.path( K8.masterDns.val( ) )
							.asText( "not-specified" ) ) ;
			CSAP.jsonStream( masters )
					.map( JsonNode::asText )
					.map( this::resolveHostSpecification )
					.forEach( masterHost -> {

						if ( masterHost.equals( serviceHostName ) ) {

							serviceInstance.setKubernetesMaster( true ) ;

						}

					} ) ;

			// .filter( hostName::equals )
			// .findFirst()
			// .ifPresent( host -> {
			// serviceInstance.setKubernetesMaster( true );
			// });
		} else {

			// default to current host
			ArrayNode defaultMasters = jsonMapper.createArrayNode( ) ;
			defaultMasters.add( serviceHostName ) ;
			serviceInstance.setKubernetesMasterHostNames( defaultMasters ) ;

		}

	}

	private void addKubernetesPerformanceMonitors (
													Project project ,
													String environmentName ,
													String serviceName ,
													JsonNode serviceDefinition ,
													String serviceHostName ,
													ServiceInstance serviceInstance ) {

		// note there may be multiple services listed in the provider: this filters to
		// ONLY kublet
		var isKubeletService = ! serviceDefinition.at( "/performance/nodeCores" ).isMissingNode( ) ;

		if ( isKubeletService ) {

			project.setDefaultKubernetes( serviceName, serviceHostName, environmentName ) ;

			var settings = project.getEnvironmentNameToSettings( ).get( environmentName ) ;

			if ( settings.getKubernetesMeters( ).isArray( ) ) {

				logger.debug( "Adding kubernetes collection meters {}", CSAP.jsonPrint( settings
						.getKubernetesMeters( ) ) ) ;
				CSAP.jsonStream( settings.getKubernetesMeters( ) )
						.filter( JsonNode::isTextual )
						.map( JsonNode::asText )
						.forEach( containerName -> {

							var csapServiceName = containerName ;

							serviceInstance.addMeter( podHostCountName( containerName ),
									HttpCollector.CSAP_SERVICE_COUNT + ":" + csapServiceName,
									podHostTotalTitle( containerName ) ) ;

							serviceInstance.addMeter( podTotalCountName( containerName ),
									"/report/metrics/containers/" + containerName + "/"
											+ K8.containerCount.val( ),
									podTotalCountTitle( containerName ) ) ;

							serviceInstance.addMeter(
									podCoreName( containerName ),
									"/report/metrics/containers/" + containerName + "/"
											+ K8.cores.val( ),
									podCoreTotalTitle( containerName ) ) ;

							serviceInstance.addMeter(
									podMemoryName( containerName ),
									"/report/metrics/containers/" + containerName + "/"
											+ K8.memoryInMb.val( ),
									podMemoryTotalTitle( containerName ) ) ;

						} ) ;

			}

		}

	}

	public String podMemoryTotalTitle ( String podName ) {

		return podName + " memory (MB)" ;

	}

	public String podCoreTotalTitle ( String podName ) {

		return podName + " Cores Used" ;

	}

	public String podTotalCountTitle ( String podName ) {

		return podName + " Pod Total Count" ;

	}

	public String podHostTotalTitle ( String podName ) {

		return podName + " Host Total Count" ;

	}

	public String podHostCountName ( String podName ) {

		return podName.replaceAll( "-", "" ) + "Instances" ;

	}

	public String podTotalCountName ( String podName ) {

		return podName.replaceAll( "-", "" ) + "Counts" ;

	}

	public String podCoreName ( String podName ) {

		return podName.replaceAll( "-", "" ) + "Cores" ;

	}

	public String podMemoryName ( String podName ) {

		return podName.replaceAll( "-", "" ) + "Memory" ;

	}

	private ServiceInstance load_api_service (
												String hostName ,
												String apiServiceName ,
												ClusterType clusterType ,
												JsonNode serviceDefinition ,
												StringBuilder resultsBuf ,
												Project testProject ,
												String fullEnvironmentName ,
												String platformLifeCycle ) {

		logger.debug( "service: {} Project: {} host: {}, fullEnvironmentName: {}, clusterType: {}",
				apiServiceName, testProject.getName( ), hostName, fullEnvironmentName, clusterType ) ;

		ServiceInstance serviceInstance = new ServiceInstance( ) ;
		serviceInstance.setHostName( hostName.trim( ) ) ;
		serviceInstance.setName( apiServiceName ) ;
		serviceInstance.setClusterType( clusterType ) ;

		// defaults for csap-api services
		serviceInstance.setContext( apiServiceName ) ;
		serviceInstance.setPlatformVersion( platformLifeCycle ) ;

		if ( serviceDefinition.has( "user" ) ) {

			serviceInstance.setUser( serviceDefinition.path( "user" ).asText( ).trim( ) ) ;

		}

		if ( serviceDefinition.has( "scmVersion" ) ) {

			serviceInstance.setScmVersion( serviceDefinition.path( "scmVersion" ).asText( ).trim( ) ) ;

		}

		serviceInstance.setLifecycle( fullEnvironmentName ) ;

		//
		// load the service using the contents of the project.json file
		//
		serviceInstance.parseDefinition( testProject.getSourceFileName( ), serviceDefinition, resultsBuf ) ;

		merge_service_overrides( serviceInstance, serviceDefinition, platformLifeCycle, testProject,
				resultsBuf ) ;

		return serviceInstance ;

	}

	private void add_service_to_project ( Project csapProject , ServiceInstance serviceInstance ) {

//		var simpleHostName = Application.hostShortName( serviceInstance.getHostName( ) );
		var simpleHostName = serviceInstance.getHostName( ) ;

		var hostServices = csapProject.getServicesListOnHost( simpleHostName ) ;

		if ( hostServices == null ) {

			var newhostServices = new ArrayList<ServiceInstance>( ) ;
			csapProject.getHostToServicesMap( )
					.put( simpleHostName, newhostServices ) ;

			hostServices = newhostServices ;

		}

		hostServices.add( serviceInstance ) ;

		ArrayList<ServiceInstance> serviceInstances = csapProject.getServiceToAllInstancesMap( ).get(
				serviceInstance.getName( ) ) ;

		if ( serviceInstances == null ) {

			serviceInstances = new ArrayList<ServiceInstance>( ) ;
			csapProject.getServiceToAllInstancesMap( ).put( serviceInstance.getName( ),
					serviceInstances ) ;

		}

		serviceInstances.add( serviceInstance ) ;

	}

	private void addCsapAgents (
									String environmentName ,
									String environmentAndClusterName ,
									StringBuilder resultsBuf ,
									Project project ) {

		//
		// Iterate over all defined clusters: add agent to first
		//
		var clusterHosts = project.getHostsForEnvironment( environmentAndClusterName ) ;

		logger.trace( "Adding Agent instances: environmentName: '{}' hosts: '{}'",
				environmentAndClusterName,
				clusterHosts ) ;

		if ( ! project.getEnvironmentNameToHostInfo( ).containsKey( environmentName ) ) {

			ArrayList<HostInfo> hostInfoList = new ArrayList<HostInfo>( ) ;
			project.getEnvironmentNameToHostInfo( ).put( environmentName, hostInfoList ) ;

		}

		for ( String host : clusterHosts ) {

			if ( ! project.getHostsInAllLifecycles( ).contains( host ) ) {

				addCsapAgent( environmentName, resultsBuf, project, host ) ;

			}

		}

		//
		var agentDefinition = project.getSourceDefinition( ).path( SERVICE_TEMPLATES ).path(
				CsapConstants.AGENT_NAME ) ;

		if ( agentDefinition.isObject( ) ) {

			( (ObjectNode) agentDefinition ).remove( AGENT_AUTO_CLUSTER ) ;

		}

	}

	private void addCsapAgent ( String environmentName , StringBuilder resultsBuf , Project project , String host ) {

		ClusterType partitionTypeForFirstService = ClusterType.SIMPLE ;

		if ( project.getHostToServicesMap( ).containsKey( host )
				&& project.getHostToServicesMap( ).get( host ).size( ) != 0 ) {

			partitionTypeForFirstService = project.getHostToServicesMap( ).get( host )
					.get( 0 )
					.getClusterType( ) ;

		}

		JsonNode agentDefinition = project.load_service_definition(
				CsapConstants.AGENT_NAME ) ; // SERVICE_CATEGORY_JVMS

		String assigned_cluster = //
				agentDefinition
						.path( AGENT_AUTO_CLUSTER )
						.path( environmentName + "Cluster" )
						.asText( environmentName + ENVIRONMENT_CLUSTER_DELIMITER + "base-os" ) ;

		logger.debug( "Adding agent to host: '{}' in lifecycle: '{}', assigned_cluster '{}' ",
				host, environmentName, assigned_cluster ) ;

		ServiceInstance serviceInstance = load_java_service(
				resultsBuf, project, environmentName,
				CsapConstants.AGENT_NAME, agentDefinition,
				assigned_cluster,
				host, Integer.toString( csapApis.application( ).getAgentPort( ) ),
				partitionTypeForFirstService ) ;

		add_service_to_project( project, serviceInstance ) ;

		var csapHostShortNameInfo = new HostInfo( csapApis.application( ).getCsapHostName( ), "" ) ;
		var csapFqdnInfo = new HostInfo( csapApis.application( ).getDefinitionHostFqdn( ), "" ) ;
		var environmentHosts = project.getEnvironmentNameToHostInfo( ).get( environmentName ) ;

		logger.debug( "csapHostShortNameInfo: {}, \n csapFqdnInfo: {} {}",
				csapHostShortNameInfo,
				csapFqdnInfo ) ;
		logger.trace( "csapHostShortNameInfo: {}, \n csapFqdnInfo: {}, \n environmentHosts: {}",
				csapHostShortNameInfo,
				csapFqdnInfo,
				environmentHosts ) ;

		if ( environmentHosts.contains( csapHostShortNameInfo )
				|| environmentHosts.contains( csapFqdnInfo ) ) {

			project.getRootPackage( ).setHostEnvironmentName( environmentName ) ;

			if ( environmentHosts.contains( csapFqdnInfo ) ) {

				csapApis.application( ).setHostNameForDefinitionMapping( csapFqdnInfo.getName( ) ) ;

			}

			logger.debug( "{} active environment: '{}'", host, environmentName ) ;

		}

	}

	/**
	 * 
	 * @param rootProject
	 * @param parsingResults
	 */
	private void generateProjectHosts (
										Project rootProject ,
										StringBuilder parsingResults ) {

		rootProject
				.getProjects( )
				.forEach( project -> {

					project.getEnvironmentAndClusterNames( ).stream( )
							.filter( environmentAndClusterName -> environmentAndClusterName.contains(
									ENVIRONMENT_CLUSTER_DELIMITER ) )
							.forEach( environmentAndClusterName -> {

								String environmentName = environmentAndClusterName.split(
										ENVIRONMENT_CLUSTER_DELIMITER )[0] ;
								addCsapAgents( environmentName, environmentAndClusterName, parsingResults, project ) ;

							} ) ;

				} ) ;

		rootProject

				.getProjects( )

				.filter( project -> project.getHostsCurrentLc( ) != null )

				.forEach( project -> {

					project.getServiceNameStream( )
							.forEach( serviceName -> {

								buildServiceToHostList( serviceName, project ) ;

							} ) ;

				} ) ;

		// Ensure host is only contained in a single release package
		TreeMap<String, String> hostDuplicateCheck = new TreeMap<String, String>( ) ;

		rootProject
				.getProjects( )
				.forEach( project -> {

					project.getHostsInActiveLifecycleStream( )
							.forEach( adminHostName -> {

								ensureUniqueHost( adminHostName, parsingResults, hostDuplicateCheck, project ) ;

							} ) ;

				} ) ;

	}

	public void ensureUniqueHost (
									String adminHostName ,
									StringBuilder resultsBuf ,
									TreeMap<String, String> hostDuplicateCheck ,
									Project model ) {

		logger.debug( "checking {} in \n\t {}", adminHostName, hostDuplicateCheck ) ;

		if ( hostDuplicateCheck.containsKey( adminHostName ) ) {

			String message = CsapConstants.CONFIG_PARSE_ERROR
					+ " Host: "
					+ adminHostName
					+ " was found in multiple release packages: "
					+ hostDuplicateCheck.get( adminHostName )
					+ " and "
					+ model.getSourceFileName( )
					+ ". To ensure package isolation, a host can only appear in 1 package\n" ;
			resultsBuf.append( message ) ;
			logger.warn( message ) ;

		}

		hostDuplicateCheck.put( adminHostName, model.getSourceFileName( ) ) ;

	}

	private void buildServiceToHostList ( String service , Project project ) {

		logger.debug( "{}: environment: '{}', env hosts: {}",
				service,
				project.getRootPackage( ).getHostEnvironmentName( ),
				project.getHostsCurrentLc( ) ) ;

		List<ServiceInstance> instanceListLC = project.getServiceInstancesInAllLifecycles( service )
				.filter( serviceInstance -> project.getHostsCurrentLc( ).contains( serviceInstance
						.getHostName( ) ) )
				.collect( Collectors.toList( ) ) ;

		project.serviceInstancesInCurrentLifeByName( ).put( service, instanceListLC ) ;

		logger.debug( "{}  Instances: {}", service, instanceListLC ) ;

	}

	public static class HTMLCharacterEscapes extends CharacterEscapes {

		/**
		 *
		 */
		private static final long serialVersionUID = 1L ;
		private final int[] asciiEscapes ;

		public HTMLCharacterEscapes ( ) {

			// start with set of characters known to require escaping
			// (double-quote, backslash etc)
			int[] esc = CharacterEscapes.standardAsciiEscapesForJSON( ) ;
			// and force escaping of a few others:
			esc['<'] = CharacterEscapes.ESCAPE_STANDARD ;
			esc['>'] = CharacterEscapes.ESCAPE_STANDARD ;
			esc['&'] = CharacterEscapes.ESCAPE_STANDARD ;
			esc['\''] = CharacterEscapes.ESCAPE_STANDARD ;
			asciiEscapes = esc ;

		}

		// this method gets called for character codes 0 - 127
		@Override
		public int[] getEscapeCodesForAscii ( ) {

			return asciiEscapes ;

		}

		// and this for others; we don't need anything special here
		@Override
		public SerializableString getEscapeSequence ( int ch ) {

			// no further escaping (beyond ASCII chars) needed:
			return null ;

		}
	}

	public ProjectMigrator getProjectMigrator ( ) {

		return projectMigrator ;

	}

	public ProjectOperators getProjectOperators ( ) {

		return projectOperators ;

	}

	public void setProjectOperators ( ProjectOperators projectHelper ) {

		this.projectOperators = projectHelper ;

	}

}
