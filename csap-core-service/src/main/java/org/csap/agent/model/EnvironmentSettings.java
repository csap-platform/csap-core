package org.csap.agent.model ;

import java.io.IOException ;
import java.net.URI ;
import java.net.URISyntaxException ;
import java.text.NumberFormat ;
import java.text.ParseException ;
import java.util.ArrayList ;
import java.util.Arrays ;
import java.util.EnumMap ;
import java.util.HashMap ;
import java.util.LinkedHashMap ;
import java.util.List ;
import java.util.Map ;
import java.util.TreeMap ;
import java.util.regex.Matcher ;
import java.util.stream.Collectors ;

import org.apache.commons.lang3.text.WordUtils ;
import org.csap.agent.CsapCore ;
import org.csap.agent.CsapCoreService ;
import org.csap.agent.integrations.CsapEvents ;
import org.csap.agent.integrations.VersionControl ;
import org.csap.agent.integrations.Vsphere ;
import org.csap.helpers.CSAP ;
import org.csap.helpers.CsapApplication ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.util.LinkedMultiValueMap ;
import org.springframework.util.MultiValueMap ;
import org.springframework.web.util.UriTemplate ;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties ;
import com.fasterxml.jackson.core.type.TypeReference ;
import com.fasterxml.jackson.databind.JsonNode ;
import com.fasterxml.jackson.databind.ObjectMapper ;
import com.fasterxml.jackson.databind.node.ArrayNode ;
import com.fasterxml.jackson.databind.node.MissingNode ;
import com.fasterxml.jackson.databind.node.ObjectNode ;

/**
 *
 * Metadata associated with lifecycles
 *
 *
 * @author someDeveloper
 *
 */
public class EnvironmentSettings {
	public static final String LOADBALANCER_URL = "loadbalancer-url" ;
	public static final String CONFIGURATION_MAPS = "configuration-maps" ;
	public static final String GLOBAL_CONFIG_MAP_NAME = "global" ;

	private static final String UI_DEFAULT_VIEW = "uiDefaultView" ;

	final static Logger logger = LoggerFactory.getLogger( EnvironmentSettings.class ) ;

	private String helpUrBase = "https://github.com/csap-platform/csap-core/wiki#" ;

	// determines number of items for rolling average
	private int limitSamples = 5 ;

	ObjectMapper jacksonMapper = new ObjectMapper( ) ;

	//
	// Core json
	//

	private ObjectNode definition = null ;

	private ArrayNode eolJarPatterns ;
	private ObjectNode configurationMaps = null ;

	private ArrayNode logParsers = jacksonMapper.createArrayNode( ) ;
	private ArrayNode quickLaunchers = jacksonMapper.createArrayNode( ) ;
	private Map<String, String> kubernetesyamlReplacements = new HashMap<>( ) ;

	private ArrayNode trendingJson = jacksonMapper.createArrayNode( ) ;
	private ArrayNode realTimeMetersJson = jacksonMapper.createArrayNode( ) ;
	private JsonNode kubernetesMeters = MissingNode.getInstance( ) ;
	private JsonNode kubernetesNamespaceCollection = MissingNode.getInstance( ) ;

	//
	// csap data settings
	//

	private String secondaryEventUrl = "" ;
	private String secondaryEventUser = "" ;
	private String secondaryEventPass = "" ;
	private boolean secondaryEventPublishEnabled = false ;

	private String eventUser = CsapCore.EVENTS_DISABLED ;
	private String eventCredential = CsapCore.EVENTS_DISABLED ;
	private String eventServiceUrl = CsapCore.EVENTS_DISABLED ;
	private boolean eventPublishEnabled = false ;
	private boolean csapMetricsUploadEnabled = false ;

	private String eventUrl = "/api/event" ;
	private String eventMetricsUrl = "/api/metrics/" ;
	private URI reportURI ;
	private String performancePortalUrl = "/csap-admin/os/performance" ;
	private String historyUiUrl = "?appId={appId}&life={life}&category={category}&" ;

	//
	// Agent settings
	//
	private String mavenCommand = "-Dmaven.test.skip=true clean package" ;

	private String agentUser = "agentUser" ;
	private String agentPass = null ;
	private boolean agentLocalAuth = true ;
	private String iostatDeviceFilter = "^sd.*" ;
	private boolean autoRestartHttpdOnClusterReload = true ;
	private int lsofIntervalMins = 1 ;

	private ArrayNode newsJsonArray = null ;

	private ArrayNode emailJsonArray = null ;

	// UI
	private String resolvedUiCountUrl = null ;
	private String resolvedHostCountUrl = null ;
	private String resolvedQueryUrl = null ;

	//
	// Collection settings
	//

	private long maxJmxCollectionMs = 1000 ;

	private int defaultMinFreeMemoryMb = 500 ;
	private int defaultMaxDiskPercent = 90 ;
	private int defaultMaxDeviceIoPercent = 80 ;

	// Mpstat monitors
	private int defaultMaxHostCpuLoad = 4 ;
	private int defaultMaxHostCpu = 80 ;
	private int defaultMaxHostCpuIoWait = 10 ;

	private int kubernetesCertMinimumDays = 60 ;
	private int kubernetesEventInspectMinutes = 5 ;
	private int kubernetesEventInspectMax = 1000 ;
	private List<String> kubernetesEventExcludes = List.of( ".*cron" ) ;

	private int dockerMaxContainers = 100 ;
	private int dockerMaxImages = 100 ;
	private int dockerMaxVolumes = 100 ;

	private Map<String, ObjectNode> cluster_to_alertLimits = new HashMap<>( ) ;
	private Map<String, ObjectNode> host_to_alertLimits = new HashMap<>( ) ;

	public static final String ALL_IN_LIFECYCLE = "all" ;
	private String uiDefaultView = ALL_IN_LIFECYCLE ;
	private TreeMap<String, String> labelToServiceUrlLaunchMap = new TreeMap<String, String>( ) ;

	private String loadbalancerUrl = "http://needToAddYourLbToClusterFile" ;

	private String monitoringUrl = "none" ;

	private String metricsUrl = "no" ;

	private MultiValueMap<String, Integer> metricToSecondsMap = new LinkedMultiValueMap<String, Integer>( ) ;

	// Cluster wide defaults
	private Map<ServiceAlertsEnum, Long> serviceLimits = new EnumMap<ServiceAlertsEnum, Long>(
			ServiceAlertsEnum.class ) ;

	private int psDumpCount = 0 ;
	private int psDumpLowMemoryInMb = 1000 ;

	private int numberWorkerThreads = 5 ;

	boolean metricsPublication = false ;

	private int portStart = 8200 ;
	private int portEnd = 9000 ;

	private int duIntervalMins = 5 ;
	private String primaryNetwork = null ;

	private long logRotationMinutes = 60 ;

	private JsonNode vsphereConfiguration ;

	private boolean baseEnvOnly = false ;

	public EnvironmentSettings ( ) {

		newsJsonArray = jacksonMapper.createArrayNode( ) ;
		emailJsonArray = jacksonMapper.createArrayNode( ) ;

		// default values can be overwritten per cluster, per host, or per
		// service
		serviceLimits.put( ServiceAlertsEnum.diskSpace, 100l ) ;
		serviceLimits.put( ServiceAlertsEnum.diskWriteRate, 10l ) ;
		serviceLimits.put( ServiceAlertsEnum.openFileHandles, 400l ) ;
		serviceLimits.put( ServiceAlertsEnum.httpConnections, 40l ) ;
		serviceLimits.put( ServiceAlertsEnum.memory, convertUnitToKb( "765m", ServiceAlertsEnum.memory ) ) ;
		serviceLimits.put( ServiceAlertsEnum.sockets, 30l ) ;
		serviceLimits.put( ServiceAlertsEnum.threads, 100l ) ;
		serviceLimits.put( ServiceAlertsEnum.cpu, 200l ) ;

		eolJarPatterns = jacksonMapper.createArrayNode( ) ;
		eolJarPatterns.add( "commons-dbcp-1.*.jar" ) ;
		eolJarPatterns.add( "hibernate-core-4.*.jar" ) ;
		eolJarPatterns.add( "hibernate-core-3.*.jar" ) ;
		eolJarPatterns.add( "spring-boot-1.3.*.jar" ) ;
		eolJarPatterns.add( "org.springframework.*-3.*.jar" ) ; // osgi spring
		eolJarPatterns.add( "spring-core-3.*.jar" ) ;
		eolJarPatterns.add( "spring-security.*-3.*.jar" ) ;
		eolJarPatterns.add( "log4j-1.*jar" ) ;

		// eolJarPatterns.add("EasyCriteria-3.*.jar") ; // testing only
	}

	public ObjectNode getDefinition ( ) {

		return definition ;

	}

	public String uiConfigMapsFormatted ( ) {

		if ( configurationMaps == null ) {

			return "null" ;

		}

		return CSAP.jsonPrint( configurationMaps ) ;

	}

	public JsonNode getConfigurationMap ( String mapName ) {

		if ( configurationMaps == null ) {

			return MissingNode.getInstance( ) ;

		}

		return configurationMaps.path( mapName ) ;

	}

	public void setDefinition ( ObjectNode settingsDefinition ) {

		this.definition = settingsDefinition ;

	}

	Application csapApplication ;

	public void loadSettings (
								String location ,
								JsonNode environmentDefinition ,
								StringBuilder resultsBuf ,
								Application csapApplication )
		throws IOException {

		this.csapApplication = csapApplication ;

		if ( ! environmentDefinition.isObject( ) ) {

			resultsBuf.append(
					CsapCore.CONFIG_PARSE_WARN + " lifecycle settings not found in definition: " + location ) ;
			logger.warn( "lifecycle settings not found: {}. Default collection intervals are 30 seconds", location ) ;

			return ;

		}

		// if ( environmentDefinition.path("defaults").asBoolean() ) {
		// getMetricToSecondsMap().add( CsapApplication.COLLECTION_HOST, 30 );
		// getMetricToSecondsMap().add( CsapApplication.COLLECTION_OS_PROCESS, 30 );
		// getMetricToSecondsMap().add( CsapApplication.COLLECTION_APPLICATION, 30 );
		// }

		definition = (ObjectNode) environmentDefinition ;

		setBaseEnvOnly( definition.path( ProjectLoader.BASE_ENV_ONLY ).asBoolean( false ) ) ;

		processProjectSettings( ) ;

		processConfigurationMaps( ) ;

		processCoreSettings( definition ) ;

		processCsapEventSettings( definition ) ;

		processMonitors( definition ) ;

		processReports( definition ) ;

		processMetricsCollection( definition ) ;

	}

	private void processProjectSettings ( ) {

		var applicationSettings = getDefinition( ).path( ProjectLoader.APPLICATION ) ;

		logger.debug( "processing: {}", applicationSettings ) ;

		if ( applicationSettings.isObject( ) ) {

			applicationName = applicationSettings.path( "name" ).asText( applicationName ) ;

			definitionRepoUrl = applicationSettings.path( "definition-repo-url" ).asText( definitionRepoUrl ) ;
			definitionAbortOnBaseMismatch = applicationSettings.path( "definition-abort-on-version" ).asBoolean( ) ;
			definitionRepoType = applicationSettings.path( "definition-repo-type" ).asText( definitionRepoType ) ;
			definitionRepoBranch = applicationSettings.path( "definition-repo-branch" ).asText( definitionRepoBranch ) ;

			mavenRepositoryUrl = applicationSettings.path( "maven-url" ).asText( mavenRepositoryUrl ) ;

			tomcatAjpSecret = applicationSettings.path( "ajp-secret" ).asText( "demo-ajp-secret" ) ;
			// try {
			// capabilityAjpSecret = csapApplication encryptor.decrypt( capabilityAjpSecret
			// ) ;
			// } catch ( Exception e ) {
			// logger.debug( "ajpSecret is not encrypted. Use CSAP encrypt to generate" ) ;
			// }

			JsonNode subProjectDefinition = applicationSettings.path( ProjectLoader.SUB_PROJECTS ) ;

			if ( subProjectDefinition.isArray( ) ) {

				getSubProjects( ).clear( ) ;
				CSAP.jsonStream( subProjectDefinition )
						.filter( JsonNode::isTextual )
						.map( JsonNode::asText )
						.forEach( projectFileName -> {

							getSubProjects( ).add( projectFileName ) ;

						} ) ;
				logger.debug( "subProjects: {}, source: {}", getSubProjects( ), subProjectDefinition ) ;

			}

			JsonNode helpItems = applicationSettings.path( "help-menu" ) ;
			CSAP.asStreamHandleNulls( helpItems ).forEach( menuName -> {

				String menuUrl = helpItems.path( menuName ).textValue( ) ;

				logger.debug( "*** Menu Item: {} ,  target: ", menuName, menuUrl ) ;

				getHelpMenuUrlMap( ).put( menuName, menuUrl ) ;

			} ) ;

			JsonNode launches = applicationSettings.path( "quick-launches" ) ;

			if ( launches.isArray( ) ) {

				quickLaunchers = (ArrayNode) launches ;

			}

			JsonNode parsers = applicationSettings.path( "log-parsers" ) ;

			if ( parsers.isArray( ) ) {

				logParsers = (ArrayNode) parsers ;

			}

			JsonNode replacements = applicationSettings.path( "k8s-yaml-replacements" ) ;

			if ( replacements.isArray( ) ) {

				var yamlReplacments = (ArrayNode) replacements ;

				for ( var item : yamlReplacments ) {

					if ( item.isObject( )
							&& item.has( "original" )
							&& item.has( "replacement" ) ) {

						kubernetesyamlReplacements.put( item.get( "original" ).asText( ), item.get( "replacement" )
								.asText( ) ) ;

					} else {

						logger.warn( "Skipping item, invalid: {}", item ) ;

					}

				}

			}

		} else {

			logger.debug( "no application settings" ) ;

		}

	}

	public ArrayNode getQuickLaunchers ( ) {

		return quickLaunchers ;

	}

	public ArrayNode getLogParsers ( ) {

		return logParsers ;

	}

	public Map<String, String> getKubernetesYamlReplacements ( ) {

		return kubernetesyamlReplacements ;

	}

	private Map<String, String> helpMenuUrlMap = new LinkedHashMap<String, String>( ) ;

	public Map<String, String> getHelpMenuUrlMap ( ) {

		return helpMenuUrlMap ;

	}

	private void processConfigurationMaps ( ) {

		var latestMaps = definition.path( CONFIGURATION_MAPS ) ;

		if ( latestMaps.isObject( ) ) {

			if ( this.configurationMaps == null ) {

				this.configurationMaps = latestMaps.deepCopy( ) ;

			} else {

				var existingMaps = this.configurationMaps ;

				// handle unique map names reusing definition
				CSAP.asStreamHandleNulls( latestMaps )
						.filter( configMapName -> ! existingMaps.has( configMapName ) )
						.forEach( configMapName -> {

							existingMaps.set( configMapName, latestMaps.path( configMapName ) ) ;

						} ) ;

				// do merges
				CSAP.asStreamHandleNulls( latestMaps )
						.filter( configMapName -> existingMaps.has( configMapName ) )
						.forEach( configMapName -> {

							var overrideMap = latestMaps.path( configMapName ) ;
							var currentMap = existingMaps.path( configMapName ) ;

							if ( overrideMap.isObject( ) && currentMap.isObject( ) ) {

								( (ObjectNode) currentMap )
										.setAll( (ObjectNode) overrideMap ) ;

							}

						} ) ;

			}

		}

	}

	private ArrayNode loadBalanceVmFilter = null ;

	public boolean isLoadBalanceVmFilter ( String hostName ) {

		if ( loadBalanceVmFilter == null ) {

			return false ;

		}

		try {

			for ( JsonNode hostJson : loadBalanceVmFilter ) {

				if ( hostJson.asText( ).equals( hostName ) ) {

					return true ;

				}

			}

		} catch ( Exception e ) {

			logger.error( "Failed parsing " + loadBalanceVmFilter, e ) ;

		}

		return false ;

	}

	private void processCoreSettings ( JsonNode environmentDefinition )
		throws IOException {

		if ( environmentDefinition.has( "mavenCommand" ) ) {

			this.mavenCommand = environmentDefinition.path(
					"mavenCommand" ).asText( ) ;

		}

		uiDefaultView = environmentDefinition.path( UI_DEFAULT_VIEW ).asText( uiDefaultView ) ;

		if ( environmentDefinition.has( "autoRestartHttpdOnClusterReload" ) ) {

			if ( environmentDefinition
					.path( "autoRestartHttpdOnClusterReload" )
					.asText( )
					.startsWith( "n" )
					||
					environmentDefinition
							.path( "autoRestartHttpdOnClusterReload" )
							.asText( )
							.startsWith( "f" ) ) {

				autoRestartHttpdOnClusterReload = false ;

			}

		}

		if ( environmentDefinition.has( "portRange" ) ) {

			if ( environmentDefinition.path( "portRange" ).has( "start" ) ) {

				setPortStart( environmentDefinition.path( "portRange" ).path( "start" ).asInt( ) ) ;

			}

			if ( environmentDefinition.path( "portRange" ).has( "end" ) ) {

				setPortEnd( environmentDefinition.path( "portRange" ).path( "end" ).asInt( ) ) ;

			}

		}

		if ( environmentDefinition.has( "secureUrl" ) ) {

			secureUrl = environmentDefinition.path( "secureUrl" ).asText( ) ;

		}

		if ( environmentDefinition.has( LOADBALANCER_URL ) ) {

			loadbalancerUrl = trimSpacesAndVariables( environmentDefinition.path( LOADBALANCER_URL ).asText( ) ) ;

		}

		if ( environmentDefinition.has( "monitoringUrl" ) ) {

			monitoringUrl = trimSpacesAndVariables( environmentDefinition.path( "monitoringUrl" ).asText( ) ) ;

		}

		processAgentSettings( environmentDefinition ) ;

		processContainerLimits( environmentDefinition ) ;

		setVsphereConfiguration( environmentDefinition.path( Vsphere.VSPHERE_SETTINGS_NAME ) ) ;

		if ( environmentDefinition.has( "newsItems" ) ) {

			newsJsonArray = (ArrayNode) environmentDefinition.path( "newsItems" ) ;

		} else {

			newsJsonArray.removeAll( ).add( "News items may be added using Application Editor." ) ;

		}

		if ( environmentDefinition.has( "loadBalanceVmFilter" ) ) {

			loadBalanceVmFilter = (ArrayNode) environmentDefinition
					.get( "loadBalanceVmFilter" ) ;

		}

		if ( environmentDefinition.has( "operatorNotifications" ) ) {

			emailJsonArray = (ArrayNode) environmentDefinition
					.get( "operatorNotifications" ) ;

		} else {

			emailJsonArray.removeAll( ).add( "csapsupport@yourcompany.com" ) ;

		}

		// eol launchUrls
		var launchUrls = environmentDefinition.path( "launchTargets" ) ;

		labelToServiceUrlLaunchMap = new TreeMap<String, String>( ) ;

		if ( launchUrls.isArray( ) ) {

			CSAP.jsonStream( launchUrls )
					.forEach( launchItem -> {

						labelToServiceUrlLaunchMap.put( launchItem.get( "description" ).asText( ).trim( ),
								trimSpacesAndVariables( launchItem.get( "url" ).asText( ) ) ) ;

					} ) ;

		} else if ( ! launchUrls.isMissingNode( ) ) {

			logger.warn( "Found legacy lifecycle setting: 'launchUrls' . Switch to Array of objects, ignoring: {}",
					launchUrls ) ;

		}

		if ( labelToServiceUrlLaunchMap.size( ) == 0 ) {

			// default Settings
			labelToServiceUrlLaunchMap.put( "Default", "default" ) ;
			labelToServiceUrlLaunchMap.put( "CSAP Loadbalancer", getLoadbalancerUrl( ) ) ;

		}

	}

	private void processContainerLimits ( JsonNode lifecycleDefinition ) {

		var kubernetesSettings = lifecycleDefinition.path( "kubernetes" ) ;

		if ( kubernetesSettings.isObject( ) ) {

			setKubernetesCertMinimumDays(
					kubernetesSettings.path( "certificate-minimum-days" ).asInt( getKubernetesCertMinimumDays( ) ) ) ;

			setKubernetesEventInspectMax( //
					kubernetesSettings.path( "event-inspect-max-count" ).asInt( getKubernetesEventInspectMax( ) ) ) ;
			setKubernetesEventInspectMinutes(
					kubernetesSettings.path( "event-inspect-minutes" ).asInt( getKubernetesEventInspectMinutes( ) ) ) ;

			var excludePatterns = kubernetesSettings.path( "event-inspect-excludes" ) ;

			if ( excludePatterns.isArray( ) ) {

				try {

					setKubernetesEventExcludes( jacksonMapper.readValue( excludePatterns.traverse( ),
							new TypeReference<ArrayList<String>>( ) {
							} ) ) ;

				} catch ( Exception e ) {

					logger.warn( "excludePatterns: {}, stack: {}", excludePatterns, CSAP.buildCsapStack( e ) ) ;

				}

			}

		}

		var dockerSettings = lifecycleDefinition.path( "docker" ) ;
		logger.debug( "dockerSettings: {}", dockerSettings ) ;

		if ( dockerSettings.isObject( ) ) {

			setDockerMaxContainers(
					dockerSettings.path( "max-containers" ).asInt( getDockerMaxContainers( ) ) ) ;
			setDockerMaxImages(
					dockerSettings.path( "max-images" ).asInt( getDockerMaxImages( ) ) ) ;
			setDockerMaxVolumes(
					dockerSettings.path( "max-volumes" ).asInt( getDockerMaxVolumes( ) ) ) ;

		}

		logger.debug( "getDockerMaxContainers: {}", getDockerMaxContainers( ) ) ;

	}

	private void processReports ( JsonNode lifecycleSettings ) {

		var reportDefinition = lifecycleSettings.path( "reports" ) ;

		if ( reportDefinition.isObject( ) ) {

			if ( reportDefinition.has( "realTimeMeters" ) ) {

				if ( realTimeMetersJson.size( ) == 0 ) {

					realTimeMetersJson.addAll( (ArrayNode) reportDefinition.get( "realTimeMeters" ) ) ;

				} else {

					// Supports import and override

					var newMeters = reportDefinition.get( "realTimeMeters" ) ;
					CSAP.jsonStream( newMeters )
							.forEach( newMeter -> {

								// remove previous if it exist
								boolean foundCurrent = false ;

								// for (int currentIndex=0 ; currentIndex < realTimeMetersJson.size();
								// currentIndex++ ) {
								for ( JsonNode currentMeter : realTimeMetersJson ) {

									// var currentMeter = realTimeMetersJson.get( currentIndex ) ;
									if ( currentMeter.path( "label" ).asText( ).equals( newMeter.path( "label" )
											.asText( ) ) ) {

										logger.debug( "Found previous item with matching label: {}", currentMeter.path(
												"label" ).asText( ) ) ;
										// realTimeMetersJson.remove( currentIndex ) ;
										ObjectNode currentItem = (ObjectNode) currentMeter ;
										currentItem.setAll( (ObjectNode) newMeter ) ;
										foundCurrent = true ;
										break ;

									}

								}

								if ( ! foundCurrent ) {

									realTimeMetersJson.add( newMeter ) ;

								}

							} ) ;

				}

			}

			if ( reportDefinition.has( "trending" ) ) {

				// trendingJson.addAll( (ArrayNode) collectionAndLandingSettings.get( "trending"
				// ) ) ;
				if ( getTrendingConfig( ).size( ) == 0 ) {

					getTrendingConfig( ).addAll( (ArrayNode) reportDefinition.get( "trending" ) ) ;

				} else {

					var newTrends = reportDefinition.get( "trending" ) ;
					CSAP.jsonStream( newTrends )
							.forEach( newTrend -> {

								// remove previous if it exist
								boolean foundCurrent = false ;

								// for (int currentIndex=0 ; currentIndex < realTimeMetersJson.size();
								// currentIndex++ ) {
								for ( JsonNode currentTrends : getTrendingConfig( ) ) {

									// var currentMeter = realTimeMetersJson.get( currentIndex ) ;
									if ( currentTrends.path( "label" ).asText( ).equals( newTrend.path( "label" )
											.asText( ) ) ) {

										logger.debug( "Found previous item with matching label: {}", currentTrends.path(
												"label" ).asText( ) ) ;
										// realTimeMetersJson.remove( currentIndex ) ;
										ObjectNode currentItem = (ObjectNode) currentTrends ;
										currentItem.setAll( (ObjectNode) newTrend ) ;
										foundCurrent = true ;
										break ;

									}

								}

								if ( ! foundCurrent ) {

									getTrendingConfig( ).add( newTrend ) ;

								}

							} ) ;

				}

			}

		} else {

			logger.debug( "Failed to locate report settings - defaults used" ) ;

		}

	}

	private void processMetricsCollection ( JsonNode lifecycleSettings ) {

		logger.debug( "Entered" ) ;

		if ( lifecycleSettings.has( "metricsPublication" ) ) {

			if ( lifecycleSettings.path( "metricsPublication" ).isArray( ) ) {

				setMetricsPublicationNode( (ArrayNode) lifecycleSettings.path( "metricsPublication" ) ) ;

				if ( getMetricsPublicationNode( ).size( ) > 0 ) {

					setMetricsPublication( true ) ;

				}

			}

		}

		if ( lifecycleSettings.has( "useCsapMetrics" ) ) {

			setMetricsUrl( trimSpacesAndVariables( lifecycleSettings.path( "useCsapMetrics" ).asText( ) ) ) ;

		}

		var collectionAndLandingSettings = lifecycleSettings.path( "csap-collection" ) ;

		if ( collectionAndLandingSettings.isObject( ) ) {

			kubernetesMeters = collectionAndLandingSettings.path( "kubernetes" ) ;

			kubernetesNamespaceCollection = collectionAndLandingSettings.path( "kubernetes-namespaces" ) ;

			if ( collectionAndLandingSettings.has( "uploadIntervalsInHours" ) ) {

				uploadIntervalsInHoursJson = (ObjectNode) collectionAndLandingSettings.get( "uploadIntervalsInHours" ) ;

			} else {

				uploadIntervalsInHoursJson = null ;

			}

			if ( collectionAndLandingSettings.has( "processDumps" ) ) {

				JsonNode jsonNode = collectionAndLandingSettings.path( "processDumps" ) ;
				psDumpInterval = jsonNode.path( "resouceInterval" )
						.asInt( ) ;
				psDumpCount = jsonNode.path( "maxInInterval" ).asInt( ) ;
				psDumpLowMemoryInMb = jsonNode.path( "lowMemoryInMb" )
						.asInt( ) ;

			}

			if ( collectionAndLandingSettings.has( "inMemoryCacheSize" ) ) {

				JsonNode jsonNode = collectionAndLandingSettings.path( "inMemoryCacheSize" ) ;
				setInMemoryCacheSize( jsonNode.asInt( ) ) ;
				logger.warn( "Updated in memory cache size: {} ", getInMemoryCacheSize( ) ) ;

			}

			logger.debug( "metricsNodes is: ", collectionAndLandingSettings.textValue( ) ) ;

			collectionAndLandingSettings
					.fieldNames( )
					.forEachRemaining( fieldName -> {

						if ( ! fieldName.equals( CsapApplication.COLLECTION_HOST ) &&
								! fieldName.equals( CsapApplication.COLLECTION_OS_PROCESS ) &&
								! fieldName.equals( CsapApplication.COLLECTION_APPLICATION ) ) {

							return ;

						}

						if ( collectionAndLandingSettings.path( fieldName ).isArray( ) ) {

							if ( getMetricToSecondsMap( ).containsKey( fieldName ) ) {

								logger.warn( "previous settings for collection interval are being replaced: '{}'",
										fieldName ) ;
								getMetricToSecondsMap( ).remove( fieldName ) ;

							}

							for ( JsonNode collectionInterval : collectionAndLandingSettings.path( fieldName ) ) {

								if ( collectionInterval.asInt( ) > 0 ) {

									getMetricToSecondsMap( ).add( fieldName, collectionInterval.asInt( ) ) ;

								} else {

									logger.warn( "Metrics configuration ignored, only > 0 will be used: "
											+ collectionInterval.toString( ) + " when reading: "
											+ fieldName ) ;

								}

							}

						} else {

							logger.warn( "Found unexpected item in metricsCollectionInSeconds: "
									+ fieldName ) ;

						}

					} ) ;

		} else {

			logger.debug( "Missing csap-collection settings, using defaults" ) ;

		}
		// if ( metricToSecondsMap.size() == 0 ) {
		// // default Settings
		// metricToSecondsMap.add( "resource", 300 );
		// }

		logger.debug( "metricToSecondsMap: {}", metricToSecondsMap ) ;

	}

	public JsonNode getKubernetesMeters ( ) {

		return kubernetesMeters ;

	}

	public ArrayNode getTrendingConfig ( ) {

		return trendingJson ;

	}

	public ArrayNode getRealTimeMeters ( ) {

		return realTimeMetersJson ;

	}

	public List<String> getRealTimeMetersForView ( ) {

		return CSAP.jsonStream( realTimeMetersJson )
				.map( JsonNode::toString )
				.collect( Collectors.toList( ) ) ;

	}

	private ObjectNode uploadIntervalsInHoursJson = null ;

	public ObjectNode getUploadIntervalsInHoursJson ( ) {

		return uploadIntervalsInHoursJson ;

	}

	public void setUploadIntervalsInHoursJson ( ObjectNode uploadIntervalsInHoursJson ) {

		this.uploadIntervalsInHoursJson = uploadIntervalsInHoursJson ;

	}

	@JsonIgnoreProperties ( ignoreUnknown = true )
	public static class InfraTests {
		public int getCpuIntervalMinutes ( ) {

			return cpuIntervalMinutes ;

		}

		public int getCpuLoopsMillions ( ) {

			return cpuLoopsMillions ;

		}

		public int getDiskIntervalMinutes ( ) {

			return diskIntervalMinutes ;

		}

		public int getDiskWriteMb ( ) {

			return diskWriteMb ;

		}

		int cpuIntervalMinutes = 30 ;
		int cpuLoopsMillions = 1 ;
		int diskIntervalMinutes = 30 ;
		int diskWriteMb = 500 ;

		@Override
		public String toString ( ) {

			return "InfraTests [cpuIntervalMinutes=" + cpuIntervalMinutes + ", cpuLoopsMillions=" + cpuLoopsMillions
					+ ", diskIntervalMinutes=" + diskIntervalMinutes + ", diskWriteMb=" + diskWriteMb + "]" ;

		}

	}

	private InfraTests infraTests = new InfraTests( ) ;

	public InfraTests getInfraTests ( ) {

		return infraTests ;

	}

	List<String> gitSslVerificationDisabledUrls = new ArrayList<>( ) ;

	public void setGitSslVerificationDisabledUrls ( List<String> gitSslVerificationDisabledUrls ) {

		this.gitSslVerificationDisabledUrls = gitSslVerificationDisabledUrls ;

	}

	public List<String> getGitSslVerificationDisabledUrls ( ) {

		return gitSslVerificationDisabledUrls ;

	}

	private void processAgentSettings ( JsonNode lifecycleSettings )
		throws IOException {

		var agentSettings = lifecycleSettings.path( "csap-host-agent" ) ;

		if ( agentSettings.isObject( ) ) {

			if ( agentSettings.has( "infraHealth" ) ) {

				try {

					infraTests = jacksonMapper.treeToValue( agentSettings.path( "infraHealth" ), InfraTests.class ) ;

				} catch ( Exception e ) {

					logger.warn( "Failed parsing infraTests, {}", CSAP.buildCsapStack( e ) ) ;

				}

			}

			if ( agentSettings.has( "gitSslVerificationDisabledUrls" ) ) {

				try {

					CsapCore.jsonStream( agentSettings.path( "gitSslVerificationDisabledUrls" ) )
							.map( JsonNode::asText )
							.forEach( gitSslVerificationDisabledUrls::add ) ;

					// infraTests = jacksonMapper.treeToValue(
					// agentSettings.path( "infraTests" ), InfraTests.class );

				} catch ( Exception e ) {

					logger.warn( "Failed parsing gitSslVerificationDisabledUrls, {}", CSAP.buildCsapStack( e ) ) ;

				}

			}

			if ( agentSettings.has( "numberWorkerThreads" ) ) {

				numberWorkerThreads = agentSettings.path( "numberWorkerThreads" ).asInt( ) ;

				if ( numberWorkerThreads <= 0 ) {

					throw new IOException( "Invalid worker thread count: " + agentSettings.path(
							"numberWorkerThreads" ) ) ;

				}

			}

			if ( agentSettings.has( "primaryNetwork" ) ) {

				setPrimaryNetwork( agentSettings.path( "primaryNetwork" ).asText( "eth0" ) ) ;

			}

			if ( agentSettings.has( "logRotationMinutes" ) ) {

				logRotationMinutes = agentSettings.path( "logRotationMinutes" ).asLong( ) ;

				if ( logRotationMinutes <= 0 ) {

					throw new IOException( "Invalid logRotationMinutes: " + agentSettings.path(
							"logRotationMinutes" ) ) ;

				}

			}

			if ( agentSettings.has( "adminToAgentTimeoutInMs" ) ) {

				adminToAgentTimeout = agentSettings
						.path( "adminToAgentTimeoutInMs" ).asInt( ) ;

				if ( adminToAgentTimeout <= 1000 ) {

					throw new IOException(
							"Invalid agent timeout, must be at least 1000: " + agentSettings.path(
									"adminToAgentTimeoutInMs" ) ) ;

				}

			}

			if ( agentSettings.has( "apiLocal" ) ) {

				setAgentLocalAuth( agentSettings.path( "apiLocal" ).asBoolean( true ) ) ;

			}

			if ( agentSettings.has( "apiUser" ) ) {

				setAgentUser( trimSpacesAndVariables( agentSettings.path( "apiUser" ).asText( ) ) ) ;

			}

			if ( agentSettings.has( "apiPass" ) ) {

				setAgentPass( trimSpacesAndVariables( agentSettings.path( "apiPass" ).asText( ) ) ) ;

			}

			if ( agentSettings.has( "iostatFilter" ) ) {

				setIostatDeviceFilter( trimSpacesAndVariables( agentSettings.path( "iostatFilter" ).textValue( ) ) ) ;

			}

			if ( agentSettings.has( "eolJarPatterns" )
					&& agentSettings.get( "eolJarPatterns" ).isArray( )
					&& agentSettings.get( "eolJarPatterns" ).size( ) > 1 ) {

				eolJarPatterns = (ArrayNode) agentSettings.get( "eolJarPatterns" ) ;

			}

			if ( agentSettings.has( "duIntervalMins" ) ) {

				setDuIntervalMins( agentSettings.path( "duIntervalMins" ).asInt( ) ) ;

			}

			if ( agentSettings.has( "lsofIntervalMins" ) ) {

				setLsofIntervalMins( agentSettings.path( "lsofIntervalMins" ).asInt( ) ) ;

			}

			if ( agentSettings.has( "maxJmxCollectionMs" ) ) {

				maxJmxCollectionMs = agentSettings.path( "maxJmxCollectionMs" ).asLong( 2000 ) ;

			}

		} else {

			logger.debug( "Missing agent settings - using csap defaults" ) ;

		}

	}

	public String summarySettings ( ) {

		StringBuilder settings = new StringBuilder( ) ;
		settings.append( CSAP.padLine( "Lifecycle Settings" ) ) ;
		settings.append( CSAP.padLine( "\t Agent Workers" ) + getNumberWorkerThreads( ) ) ;
		settings.append( CSAP.padLine( "\t Agent Timeout" ) + getAdminToAgentTimeoutSeconds( ) + " seconds" ) ;
		settings.append( CSAP.padLine( "\t iostat filter" ) + getIostatDeviceFilter( ) ) ;
		return settings.toString( ) ;

	}

	private void processCsapEventSettings ( JsonNode lifecycleDefinition ) {

		var csapDataDefinition = lifecycleDefinition.path( "csap-data" ) ;

		if ( csapDataDefinition.isObject( ) ) {

			setEventUser(
					resolveVariables( "user",
							csapDataDefinition.path( "user" ).asText( CsapCore.EVENTS_DISABLED ) ) ) ;

			setEventCredential(
					resolveVariables( "credential",
							csapDataDefinition.path( "credential" ).asText( CsapCore.EVENTS_DISABLED ) ) ) ;

			logger.debug( "service-url: {} ", csapDataDefinition.path( "service-url" ).asText( ) ) ;
			setEventServiceUrl(
					resolveVariables( "service-url",
							csapDataDefinition.path( "service-url" ).asText( CsapCore.EVENTS_DISABLED ) ) ) ;

			//
			// These are defaulted, avoid setting unless testing
			//

			setEventUrl(
					resolveVariables( "eventUrl",
							csapDataDefinition.path( "eventUrl" ).asText( eventUrl ) ) ) ;

			setEventMetricsUrl(
					resolveVariables( "eventMetricsUrl",
							csapDataDefinition.path( "eventMetricsUrl" ).asText( eventMetricsUrl ) ) ) ;

			setHistoryUiUrl(
					resolveVariables( "historyUiUrl",
							csapDataDefinition.path( "historyUiUrl" ).asText( historyUiUrl ) ) ) ;

			setPerformancePortalUrl(
					resolveVariables( "performancePortalUrl",
							csapDataDefinition.path( "performancePortalUrl" ).asText( performancePortalUrl ) ) ) ;

			if ( csapDataDefinition.at( "/secondary-publish/enabled" ).asBoolean( false ) ) {

				secondaryEventPublishEnabled = true ;

				secondaryEventUrl = resolveVariables(
						"secondaryEventUrl",
						csapDataDefinition.at( "/secondary-publish/url" ).asText( ) ) ;

				secondaryEventUser = resolveVariables(
						"secondaryUser",
						csapDataDefinition.at( "/secondary-publish/user" ).asText( ) ) ;

				secondaryEventPass = resolveVariables(
						"secondaryPass",
						csapDataDefinition.at( "/secondary-publish/pass" ).asText( ) ) ;

			}

		} else {

			logger.debug( "Missing Event Settings" ) ;

		}

	}

	private String resolveVariables ( String description , String input ) {

		String result = trimSpacesAndVariables( input ) ;

		if ( result.startsWith( "$" ) ) {

			result = csapApplication
					.getCompanyConfiguration(
							"test.variables." + input.substring( 1 ),
							input ) ;
			logger.debug( "keyName: {}, input: {}, result: {}", description, input, result ) ;

		}

		logger.debug( "input: {}, result: {}", input, result ) ;
		return result ;

	}

	private void processMonitors ( JsonNode settingsDefinition ) {

		if ( settingsDefinition.has( "monitorDefaults" ) ) {

			JsonNode monitorDefaultsDefinition = settingsDefinition.path( "monitorDefaults" ) ;

			if ( monitorDefaultsDefinition.has( "autoStopServiceThreshold" ) ) {

				defaultAutoStopServiceThreshold = monitorDefaultsDefinition.path( "autoStopServiceThreshold" )
						.asDouble( ) ;

			}

			if ( monitorDefaultsDefinition.has( "maxDiskPercent" ) ) {

				defaultMaxDiskPercent = monitorDefaultsDefinition.path( "maxDiskPercent" ).asInt( ) ;

			}

			if ( monitorDefaultsDefinition.has( "maxDeviceIoPercent" ) ) {

				defaultMaxDeviceIoPercent = monitorDefaultsDefinition.path( "maxDeviceIoPercent" ).asInt( ) ;

			}

			if ( monitorDefaultsDefinition.has( "limitSamples" ) ) {

				limitSamples = monitorDefaultsDefinition.path( "limitSamples" ).asInt( ) ;

			}

			if ( monitorDefaultsDefinition.has( "maxDiskPercentIgnorePatterns" ) ) {

				defaultMaxDiskPercentIgnorePatterns = monitorDefaultsDefinition.path( "maxDiskPercentIgnorePatterns" )
						.asText( )
						.split( "," ) ;

			}

			// Mpstat max limits
			if ( monitorDefaultsDefinition.has( MAX_HOST_CPU ) ) {

				defaultMaxHostCpu = monitorDefaultsDefinition.path( MAX_HOST_CPU )
						.asInt( ) ;

			}

			if ( monitorDefaultsDefinition.has( MAX_HOST_CPU_LOAD ) ) {

				defaultMaxHostCpuLoad = monitorDefaultsDefinition.path( MAX_HOST_CPU_LOAD )
						.asInt( ) ;

			}

			if ( monitorDefaultsDefinition.has( MAX_HOST_CPU_IO_WAIT ) ) {

				defaultMaxHostCpuIoWait = monitorDefaultsDefinition.path( MAX_HOST_CPU_IO_WAIT )
						.asInt( ) ;

			}

			if ( monitorDefaultsDefinition.has( MIN_FREE_MEMORY_MB ) ) {

				defaultMinFreeMemoryMb = monitorDefaultsDefinition.path( MIN_FREE_MEMORY_MB ).asInt( ) ;

			}

			for ( ServiceAlertsEnum serviceLimit : ServiceAlertsEnum.values( ) ) {

				if ( monitorDefaultsDefinition.has( serviceLimit.maxId( ) ) ) {

					serviceLimits.put( serviceLimit,
							monitorDefaultsDefinition.path( serviceLimit.maxId( ) ).asLong( ) ) ;

					if ( serviceLimit == ServiceAlertsEnum.memory ) {

						serviceLimits.put( ServiceAlertsEnum.memory,
								convertUnitToKb(
										monitorDefaultsDefinition.path( ServiceAlertsEnum.memory.maxId( ) ).asText( ),
										ServiceAlertsEnum.memory ) / 1024 ) ;

					}

				}

			}

		}

	}

	private int adminToAgentTimeout = 5000 ;

	// DEFAULT: 24 hour upload intervals
	// - this heavily reduces number of documents aggregated when performing
	// long term trends
	final private static int PUBLICATION_INTERVAL_SECONDS = 24 * 60 * 60 ;

	public int getMetricsUploadSeconds ( int collectionIntervalSeconds ) {

		int numSeconds = PUBLICATION_INTERVAL_SECONDS ;

		if ( uploadIntervalsInHoursJson == null ) {

			if ( collectionIntervalSeconds <= 60 ) {

				numSeconds = 30 * 60 ; // 30 minutes

			}

		} else if ( uploadIntervalsInHoursJson.has( collectionIntervalSeconds + "seconds" ) ) {

			numSeconds = (int) ( uploadIntervalsInHoursJson.get( collectionIntervalSeconds + "seconds" ).asDouble( )
					* 60
					* 60 ) ;

		}

		return numSeconds ;

	}

	public String getEventDataUser ( ) {

		return eventUser ;

	}

	public void setEventUser ( String eventUser ) {

		this.eventUser = eventUser ;

	}

	public String getEventDataPass ( ) {

		return csapApplication.decode( eventCredential, "CSAP Events password" ) ;

	}

	public void setEventCredential ( String eventPass ) {

		this.eventCredential = eventPass ;

	}

	public String getIostatDeviceFilter ( ) {

		return iostatDeviceFilter ;

	}

	public void setIostatDeviceFilter ( String iostatDeviceFilter ) {

		this.iostatDeviceFilter = iostatDeviceFilter ;

	}

	private String secureUrl = null ;

	public String getSecureUrl ( ) {

		return secureUrl ;

	}

	public void setSecureUrl ( String secureUrl ) {

		this.secureUrl = secureUrl ;

	}

	Map<String, String> browseDisks = new HashMap<String, String>( ) ;

	public Map<String, String> getBrowseDisks ( ) {

		if ( browseDisks.isEmpty( )
				&& getFileBrowserConfig( ).isObject( ) ) {

			browseDisks = CSAP.asStreamHandleNulls( (ObjectNode) getFileBrowserConfig( ) )
					.collect(
							Collectors.toMap(
									diskId -> diskId,
									diskId -> "file/browser/" + diskId ) ) ;

			// JsonNode groupFileNode = getFileBrowserConfig() ;
			// groupFileNode.fieldNames().forEachRemaining( name -> {
			// logger.debug( "got name: {}", name ) ;
			// browseDisks.put( name, "file/browser/" + name ) ;
			// } ) ;

		}

		return browseDisks ;

	}

	Map<String, String> applicationDisks = new HashMap<String, String>( ) ;

	public Map<String, String> getApplicationDisks ( ) {

		if ( applicationDisks.isEmpty( )
				&& getApplicationDiskConfig( ).isArray( ) ) {

			applicationDisks = CSAP.jsonStream( getApplicationDiskConfig( ) )
					.collect(
							Collectors.toMap(
									diskSpec -> diskSpec.path( "name" ).asText( ),
									diskSpec -> buildFileManagerUrl( diskSpec ) ) ) ;

			// JsonNode groupFileNode = getFileBrowserConfig() ;
			// groupFileNode.fieldNames().forEachRemaining( name -> {
			// logger.debug( "got name: {}", name ) ;
			// browseDisks.put( name, "file/browser/" + name ) ;
			// } ) ;

		}

		return applicationDisks ;

	}

	public JsonNode findDiskConfiguration ( String name ) {

		if ( getFileBrowserConfig( ).isArray( ) ) {

			var matchedConfig = CSAP.jsonStream( getFileBrowserConfig( ) )
					.filter( diskConfig -> diskConfig.path( "name" ).asText( ).equals( name ) )
					.findFirst( ) ;

			if ( matchedConfig.isPresent( ) ) {

				return matchedConfig.get( ) ;

			}

		}

		return MissingNode.getInstance( ) ;

	}

	private String buildFileManagerUrl ( JsonNode diskSpec ) {

		String cluster = Application.getInstance( ).getCsapHostEnvironmentName( ) + "-" + diskSpec.path( "cluster" )
				.asText( ) ;
		ArrayList<String> clusterHosts = csapApplication.getActiveProject( ).getAllPackagesModel( )
				.getLifeClusterToHostMap( ).get( cluster ) ;

		var browserHost = Application.getInstance( ).getCsapHostName( ) ;

		logger.debug( "specified: {}, Keys: {}", cluster, csapApplication.getActiveProject( ).getAllPackagesModel( )
				.getLifeClusterToHostMap( ).keySet( ) ) ;

		if ( clusterHosts != null && clusterHosts.size( ) >= 1 ) {

			browserHost = clusterHosts.get( 0 ) ;

			// writer.println( "Incorrect browser configuration - very settings: " +
			// diskSpec.get( "cluster" ).asText() ) ;
			// return null ;
		}

		// return csapApplication.getAgentUrl( browserHost, CsapCoreService.FILE_URL +
		// FileRequests.BROWSER +
		// diskSpec.path("name").asText(), false );
		return csapApplication.getAgentUrl( browserHost,
				CsapCoreService.FILE_MANAGER_URL + "?quickview=" + diskSpec.path( "name" ).asText( )
						+ "&fromFolder=" + diskSpec.path( "path" ).asText( ),
				false ) ;

	}

	public JsonNode getApplicationDiskConfig ( ) {

		return getDefinition( ).path( "application-disks" ) ;

	}

	public JsonNode getFileBrowserConfig ( ) {

		return getDefinition( ).path( "file-browser" ) ;

	}

	public String getMavenCommand ( ) {

		return mavenCommand ;

	}

	public void setMavenCommand ( String mavenCommand ) {

		this.mavenCommand = mavenCommand ;

	}

	public int getPortStart ( ) {

		return portStart ;

	}

	public void setPortStart ( int portStart ) {

		this.portStart = portStart ;

	}

	public int getPortEnd ( ) {

		return portEnd ;

	}

	public void setPortEnd ( int portEnd ) {

		this.portEnd = portEnd ;

	}

	public int getDuIntervalMins ( ) {

		return duIntervalMins ;

	}

	public void setDuIntervalMins ( int duIntervalMins ) {

		this.duIntervalMins = duIntervalMins ;

	}

	public int getLsofIntervalMins ( ) {

		return lsofIntervalMins ;

	}

	public void setLsofIntervalMins ( int lsofIntervalMins ) {

		this.lsofIntervalMins = lsofIntervalMins ;

	}

	public boolean isLsofEnabled ( ) {

		return lsofIntervalMins > 0 ;

	}

	public boolean areMetricsConfigured ( ) {

		return metricToSecondsMap.size( ) > 0 ;

	}

	public ArrayNode getEmailJsonArray ( ) {

		return emailJsonArray ;

	}

	public ArrayNode getNewsJsonArray ( ) {

		return newsJsonArray ;

	}

	public int getPsDumpCount ( ) {

		return psDumpCount ;

	}

	public int getPsDumpLowMemoryInMb ( ) {

		return psDumpLowMemoryInMb ;

	}

	private int inMemoryCacheSize = 600 ;

	public int getInMemoryCacheSize ( ) {

		return inMemoryCacheSize ;

	}

	public void setInMemoryCacheSize ( int inMemoryCacheSize ) {

		this.inMemoryCacheSize = inMemoryCacheSize ;

	}

	private int psDumpInterval = -1 ;

	public int getPsDumpInterval ( ) {

		return psDumpInterval ;

	}

	private double defaultAutoStopServiceThreshold = 2.0 ;

	public double getAutoStopServiceThreshold ( String host ) {

		double result = defaultAutoStopServiceThreshold ;

		if ( cluster_to_alertLimits.containsKey( host )
				&& cluster_to_alertLimits.get( host ).has( "autoStopServiceThreshold" ) ) {

			result = cluster_to_alertLimits.get( host ).path( "autoStopServiceThreshold" ).asDouble( ) ;

		}

		// protect agains typos
		if ( result < 0.5 ) {

			logger.warn( "autoStopServiceThreshold probably incorrect. Using hardcoded lower boundary: " + result ) ;
			result = 0.5 ;

		}

		return result ;

	}

	public void addHostLimits ( String hostName , ObjectNode monitorDefaults ) {

		logger.debug( "hostName: {} defaults: {}", hostName, monitorDefaults ) ;

		if ( host_to_alertLimits.containsKey( hostName ) ) {

			host_to_alertLimits.get( hostName ).setAll( monitorDefaults ) ;
			logger.warn( "multiple clusters contain defaults for alerts. Merge result: \n{}", host_to_alertLimits.get(
					hostName ) ) ;

		} else {

			host_to_alertLimits.put( hostName, monitorDefaults ) ;

		}

	}

	public int getMaxDiskPercent ( String host ) {

		if ( host_to_alertLimits.containsKey( host )
				&& host_to_alertLimits.get( host ).has( "maxDiskPercent" ) ) {

			return host_to_alertLimits.get( host ).path( "maxDiskPercent" ).asInt( ) ;

		}

		return defaultMaxDiskPercent ;

	}

	public int getMaxDeviceIoPercent ( String host ) {

		if ( host_to_alertLimits.containsKey( host )
				&& host_to_alertLimits.get( host ).has( "maxDeviceIoPercent" ) ) {

			return host_to_alertLimits.get( host ).path( "maxDeviceIoPercent" ).asInt( ) ;

		}

		return defaultMaxDeviceIoPercent ;

	}

	private String[] defaultMaxDiskPercentIgnorePatterns = new String[0] ;

	public boolean is_disk_monitored ( String host , String mountPoint ) {

		logger.debug( "host: {}, mountPoint: '{}', maxDiskPercentIgnorePatterns: '{}'",
				host,
				mountPoint,
				Arrays.asList( defaultMaxDiskPercentIgnorePatterns ) ) ;

		for ( String ignorePattern : getHostIgnorePattern( host ) ) {

			// logger.info("ignorePattern: " + ignorePattern + " mount: " +
			// mountPoint);
			if ( mountPoint.matches( ignorePattern ) ) {

				return false ;

			}

		}

		return true ;

	}

	public String[] getHostIgnorePattern ( String host ) {

		if ( host_to_alertLimits.containsKey( host ) && host_to_alertLimits.get( host ).has(
				MAX_HOST_DISK_IGNORE_PATTERN ) ) {

			return host_to_alertLimits.get( host ).path( MAX_HOST_DISK_IGNORE_PATTERN ).asText( ).split( "," ) ;

		}

		return defaultMaxDiskPercentIgnorePatterns ;

	}

	public static final String MAX_HOST_DISK_IGNORE_PATTERN = "maxDiskPercentIgnorePatterns" ;

	public int getMaxHostCpuLoad ( String host ) {

		if ( host_to_alertLimits.containsKey( host ) && host_to_alertLimits.get( host ).has( MAX_HOST_CPU_LOAD ) ) {

			return host_to_alertLimits.get( host ).path( MAX_HOST_CPU_LOAD ).asInt( ) ;

		}

		return defaultMaxHostCpuLoad ;

	}

	public static final String MAX_HOST_CPU_LOAD = "maxHostCpuLoad" ;

	public int getMaxHostCpu ( String host ) {

		if ( host_to_alertLimits.containsKey( host ) && host_to_alertLimits.get( host ).has( MAX_HOST_CPU ) ) {

			return host_to_alertLimits.get( host ).path( MAX_HOST_CPU ).asInt( ) ;

		}

		return defaultMaxHostCpu ;

	}

	public static final String MAX_HOST_CPU = "maxHostCpu" ;

	public int getMaxHostCpuIoWait ( String host ) {

		if ( host_to_alertLimits.containsKey( host ) && host_to_alertLimits.get( host ).has( MAX_HOST_CPU_IO_WAIT ) ) {

			return host_to_alertLimits.get( host ).path( MAX_HOST_CPU_IO_WAIT ).asInt( ) ;

		}

		return defaultMaxHostCpuIoWait ;

	}

	public static final String MAX_HOST_CPU_IO_WAIT = "maxHostCpuIoWait" ;

	public int getMinFreeMemoryMb ( String host ) {

		if ( host_to_alertLimits.containsKey( host ) && host_to_alertLimits.get( host ).has( MIN_FREE_MEMORY_MB ) ) {

			return host_to_alertLimits.get( host ).path( MIN_FREE_MEMORY_MB ).asInt( ) ;

		}

		return defaultMinFreeMemoryMb ;

	}

	public static final String MIN_FREE_MEMORY_MB = "minFreeMemoryMb" ;

	public List<String> getMonitors ( ) {

		List<String> monitors = new ArrayList<>( ) ;

		monitors.add( "defaultMaxDiskPercent: " + defaultMaxDiskPercent ) ;
		monitors.add( "defaultMaxHostCpuLoad:" + defaultMaxHostCpuLoad ) ;
		monitors.add( "defaultMaxHostCpu:" + defaultMaxHostCpu ) ;
		monitors.add( "defaultMaxHostCpuIoWait:" + defaultMaxHostCpuIoWait ) ;
		monitors.add( "defaultMinFreeMemoryMb:" + defaultMinFreeMemoryMb ) ;

		monitors.add( "auto kill threshold:" + getAutoStopServiceThreshold( "xxx" ) ) ;

		monitors.add( "docker max - containers: " + getDockerMaxContainers( )
				+ ", images: " + getDockerMaxImages( )
				+ ", volumes: " + getDockerMaxVolumes( ) ) ;

		monitors.add( "kubelet inspect max: " + getKubernetesEventInspectMax( )
				+ ", minutes: " + getKubernetesEventInspectMinutes( )
				+ ", excludes: " + getKubernetesEventExcludes( ) ) ;

		serviceLimits.entrySet( ).stream( )
				.forEach( serviceLimitEntry -> {

					monitors.add( serviceLimitEntry.getKey( ).getName( ) + ":" + serviceLimitEntry.getValue( ) ) ;

				} ) ;

		return monitors ;

	}

	public void addClusterMonitor ( String clusterName , ObjectNode monitorDefaults ) {

		logger.debug( "clusterName: {} defaults: {}", clusterName, monitorDefaults ) ;

		if ( cluster_to_alertLimits.containsKey( clusterName ) ) {

			cluster_to_alertLimits.get( clusterName ).setAll( monitorDefaults ) ;
			logger.warn( "multiple clusters contain defaults for alerts. Merge result: \n{}", cluster_to_alertLimits
					.get( clusterName ) ) ;

		} else {

			cluster_to_alertLimits.put( clusterName, monitorDefaults ) ;

		}

	}

	//
	// limits are selectively overridden across all projects
	//
	public long getMonitorForCluster ( String clusterName , ServiceAlertsEnum alert ) {

		logger.debug( "clusterName: {}", clusterName ) ;

		// first check if value is in cluster defaults
		if ( cluster_to_alertLimits.containsKey( clusterName ) && cluster_to_alertLimits.get( clusterName ).has( alert
				.maxId( ) ) ) {

			if ( alert == ServiceAlertsEnum.diskSpace ) {

				var diskAlert = cluster_to_alertLimits.get( clusterName ).path( alert.maxId( ) ).asText( ) ;

				return convertUnitToKb( diskAlert, ServiceAlertsEnum.diskSpace ) / 1024 ;

			}

			var clusterVal = cluster_to_alertLimits.get( clusterName ).path( alert.maxId( ) ).asInt( ) ;

			return clusterVal ;

		}

		// if not found above - use the default (either hardcoded, or overridden
		// by lifecycle
		try {

			var environmentValue = serviceLimits.get( alert ) ;

			logger.debug( "clusterName: {} alert: {} environmentValue: {}", clusterName, alert, environmentValue ) ;

			return environmentValue ;

		} catch ( Exception e ) {

			logger.error( "Failed to find value", e ) ;

		}

		return 1 ;

	}
	
	//
	//  Manage limit units: note memory and disk have custom settings
	//
	public static long convertUnitToKb ( String alertSetting , ServiceAlertsEnum alert ) {

		String alertSettingLowerCase = alertSetting.toLowerCase( ) ;
		// NumberFormat.getInstance().parse(itemJson.asText()).intValue(),
		long resultInKb = -1 ;

		try {

			//
			// default unit is assumed kb for disk read/write limits
			//
			resultInKb = NumberFormat.getInstance( ).parse( alertSetting ).longValue( ) ;

			if ( alertSettingLowerCase.contains( "g" ) ) {

				if ( alert == ServiceAlertsEnum.diskSpace ) {

					resultInKb = resultInKb * 1024 ;

				} else {

					resultInKb = resultInKb * 1024 * 1024 ;

				}

			} else if ( alertSettingLowerCase.contains( "m" ) ) {

				if ( alert == ServiceAlertsEnum.diskSpace ) {

					resultInKb = resultInKb  ;

				} else {

					resultInKb = resultInKb * 1024 ;

				}

			} else {

				// no unit specified: disk is mb and memory is bytes

				if ( alert == ServiceAlertsEnum.memory ) {

					// baseunit is bytes, note kb
					resultInKb = resultInKb / 1024 ;

				} else if ( alert == ServiceAlertsEnum.diskSpace ) {

					// baseunit is mb, note kb
					resultInKb = resultInKb * 1024 / 1024 ;

				}

			}

		} catch ( ParseException e ) {

			logger.error( "Unexpected limit: \"{}\" . Verify in application definition ", alertSetting ) ;
			return resultInKb ;

		}

		logger.debug( "alertSetting: {} , Result: {}", alertSetting, resultInKb ) ;

		return resultInKb ;

	}

	public boolean isMetricsPublication ( ) {

		return metricsPublication ;

	}

	public void setMetricsPublication ( boolean metricsPublication ) {

		this.metricsPublication = metricsPublication ;

	}

	ArrayNode metricsPublicationNode = null ;

	public ArrayNode getMetricsPublicationNode ( ) {

		return metricsPublicationNode ;

	}

	public void setMetricsPublicationNode ( ArrayNode metricsPublicationNode ) {

		this.metricsPublicationNode = metricsPublicationNode ;

	}

	private String trimSpacesAndVariables ( String input ) {

		if ( input == null ) {

			return null ;

		}

		return input.trim( ).replaceAll( "\\$host",
				Application.getInstance( ).getCsapHostName( ) ) ;

	}

	public int getAdminToAgentTimeoutMs ( ) {

		return adminToAgentTimeout ;

	}

	public int getAdminToAgentTimeoutSeconds ( ) {

		return adminToAgentTimeout / 1000 ;

	}

	public String getEventServiceUrl ( ) {

		return eventServiceUrl ;

	}

	public String getEventUrl ( ) {

		return getEventServiceUrl( ) + eventUrl ;

	}

	private String resolvedHistoryUrl = null ;

	public String getHistoryUiUrl ( ) {

		if ( resolvedHistoryUrl == null ) {

			resolvedHistoryUrl = buildApplicationUrl( getEventServiceUrl( ) + historyUiUrl, CsapEvents.CSAP_UI_CATEGORY
					+ "/*" ) ;

		}

		return resolvedHistoryUrl ;

	}

	private String hostHealthUrl = null ;

	public String getHostHealthUrl ( ) {

		if ( hostHealthUrl == null ) {

			hostHealthUrl = buildApplicationUrl( getEventServiceUrl( ) + historyUiUrl, "/csap/reports/health" ) ;
			hostHealthUrl += "&hostName=" + Application.getInstance( ).getCsapHostName( ) ;

		}

		return hostHealthUrl ;

	}

	private String hostActivityUrl = null ;

	public String getHostActivityUrl ( ) {

		if ( hostActivityUrl == null ) {

			hostActivityUrl = buildApplicationUrl( getEventServiceUrl( ) + historyUiUrl, "/csap/*" ) ;
			hostActivityUrl += "&hostName=" + Application.getInstance( ).getCsapHostName( ) ;

		}

		return hostActivityUrl ;

	}

	public String getEventMetricsUrl ( ) {

		return getEventServiceUrl( ) + eventMetricsUrl ;

	}

	public URI getReportUrl ( ) {

		return reportURI.resolve( "../report/" ) ;

	}

	public String getEventUiCountUrl ( ) {

		if ( resolvedUiCountUrl == null ) {

			resolvedUiCountUrl = buildApplicationUrl( getEventUrl( )
					+ "/count?appId={appId}&life={life}&category={category}&",
					CsapEvents.CSAP_UI_CATEGORY ) ;

		}

		return resolvedUiCountUrl ;

	}

	public String getEnvDiscoveryUrl ( ) {

		return getEventUrl( ) + "/discovery" ;

	}

	public String getEventHostCountUrl ( ) {

		if ( resolvedHostCountUrl == null ) {

			resolvedHostCountUrl = buildApplicationUrl( getEventUrl( )
					+ "/count?appId={appId}&life={life}&host={host}&",
					CsapEvents.CSAP_CATEGORY ) ;

		}

		return resolvedHostCountUrl ;

	}

	// searchText=appId=SensusCsap,project=CSAP
	// Platform,lifecycle=dev,from=5/20/2020,to=5/27/2020,simpleSearchText=/csap/ui/*,eventReceivedOn=false,isDataRequired=false
	public String getEventQueryUrl ( ) {

		if ( resolvedQueryUrl == null ) {

			resolvedQueryUrl = buildApplicationUrl( getEventUrl( ),
					null ) ;

		}

		return resolvedQueryUrl ;

	}

	public String getEventApiUrl ( ) {

		return getEventUrl( ) ;

	}

	public String getCsapAnalyticsServerRootUrl ( ) {

		if ( getEventUrl( ).length( ) > 10 && getEventUrl( ).indexOf( "/", 10 ) != -1 )
			return getEventUrl( ).substring( 0, getEventUrl( ).indexOf( "/", 10 ) ) ;

		return getEventUrl( ) ;

	}

	private String resolvedAnalyticsUrl = null ;

	public String getAnalyticsUiUrl ( ) {

		if ( resolvedAnalyticsUrl == null ) {

			resolvedAnalyticsUrl = performancePortalUrl ;

			if ( ! resolvedAnalyticsUrl.startsWith( "http" ) ) {

				resolvedAnalyticsUrl = getEventServiceUrl( ) + "/.." + performancePortalUrl ;

			}

		}

		logger.debug( "eventServiceUrl: {}, resolvedAnalyticsUrl: {}", getEventServiceUrl( ), resolvedAnalyticsUrl ) ;
		return resolvedAnalyticsUrl ;

	}

	private String buildApplicationUrl ( String url , String category ) {

		if ( ! url.startsWith( "http" ) ) {

			url = getLoadbalancerUrl( ) + url ;

		}

		String resolvedUrl = url ;
		Map<String, String> urlVariables = new HashMap<String, String>( ) ;

		if ( url.contains( "{appId}" ) ) {

			urlVariables.put( "appId", getEventDataUser( ) ) ;

		}

		if ( url.contains( "{life}" ) ) {

			urlVariables.put( "life", Application.getInstance( ).getCsapHostEnvironmentName( ) ) ;

		}

		if ( url.contains( "{host}" ) ) {

			urlVariables.put( "host", Application.getInstance( ).getCsapHostName( ) ) ;

		}

		if ( url.contains( "{category}" ) ) {

			urlVariables.put( "category", category ) ;

		}

		if ( url.contains( "{project}" ) ) {

			urlVariables.put( "project", csapApplication.getActiveProject( ).getName( ) ) ;

		}

		try {

			URI expanded = new UriTemplate( url ).expand( urlVariables ) ;
			resolvedUrl = expanded.toURL( ).toString( ) ;
			logger.debug( "Url: {}, Resolved: {}", url, resolvedUrl ) ;

		} catch ( Exception e ) {

			logger.warn( "Failed to build url: " + url, e ) ;

		}

		return resolvedUrl ;

	}

	public TreeMap<String, String> getLabelToServiceUrlLaunchMap ( ) {

		return labelToServiceUrlLaunchMap ;

	}

	public String getMonitoringUrl ( ) {

		return monitoringUrl ;

	}

	public String getLoadbalancerUrl ( ) {

		return loadbalancerUrl ;

	}

	public String getLbServer ( ) {

		// eg. strip off http:// from
		int lastSlash = loadbalancerUrl.lastIndexOf( "/" ) + 1 ;

		logger.debug( "lastSlash: {}, in {}", lastSlash, loadbalancerUrl ) ;

		if ( loadbalancerUrl.length( ) > lastSlash ) {

			return loadbalancerUrl.substring( lastSlash ) ;

		}

		return loadbalancerUrl ;

	}

	public String getMetricsUrl ( ) {

		if ( ! metricsUrl.startsWith( "http" ) ) {

			return getLoadbalancerUrl( ) + metricsUrl ;

		}

		return metricsUrl ;

	}

	public MultiValueMap<String, Integer> getMetricToSecondsMap ( ) {

		return metricToSecondsMap ;

	}

	public int getNumberWorkerThreads ( ) {

		return numberWorkerThreads ;

	}

	public boolean isAutoRestartHttpdOnClusterReload ( ) {

		return autoRestartHttpdOnClusterReload ;

	}

	public boolean isEventPublishEnabled ( ) {

		if ( getEventDataUser( ).contains( CsapCore.EVENTS_DISABLED )
				|| getEventDataPass( ).contains( CsapCore.EVENTS_DISABLED ) ) {

			return false ;

		}

		return eventPublishEnabled ;

	}

	public boolean isCsapMetricsUploadEnabled ( ) {

		return csapMetricsUploadEnabled ;

	}

	public void setAdminToAgentTimeout ( int adminToAgentTimeout ) {

		this.adminToAgentTimeout = adminToAgentTimeout ;

	}

	// String prefixWithEventService ( String eventPath ) {
	// if ( ! eventPath.startsWith( "http" ) ) {
	// return getEventServiceUrl() + eventPath ;
	// } else {
	// return eventPath ;
	// }
	// }

	public void setEventUrl ( String eventUrl ) {

		// this.eventUrl = prefixWithEventService( eventUrl ) ;
		this.eventUrl = eventUrl ;

	}

	public void setEventMetricsUrl ( String eventMetricsUrl ) {

		// this.eventMetricsUrl = prefixWithEventService( eventMetricsUrl ) ;
		this.eventMetricsUrl = eventMetricsUrl ;

		try {

			reportURI = new URI( getEventMetricsUrl( ) ) ;

		} catch ( URISyntaxException e ) {

			logger.error( "Failed building report URI: {} {}", getEventMetricsUrl( ), CSAP.buildCsapStack( e ) ) ;

		}

	}

	public void setHistoryUiUrl ( String historyUiUrl ) {

		// this.historyUiUrl = prefixWithEventService( historyUiUrl ) ;
		this.historyUiUrl = historyUiUrl ;

	}

	public void setPerformancePortalUrl ( String performancePortalUrl ) {

		// this.performancePortalUrl = prefixWithEventService( performancePortalUrl ) ;
		this.performancePortalUrl = performancePortalUrl ;

		if ( this.performancePortalUrl.contains( "events-service" ) ) {

			this.performancePortalUrl = this.performancePortalUrl.replaceAll(
					Matcher.quoteReplacement( "/events-service" ),
					"" ) ;

		}

	}

	public void setEventServiceUrl ( String eventServiceUrl ) {

		if ( ! eventServiceUrl.equalsIgnoreCase( CsapCore.EVENTS_DISABLED ) ) {

			eventPublishEnabled = true ;

		}

		this.eventServiceUrl = eventServiceUrl ;

	}

	public void setAutoRestartHttpdOnClusterReload (
														boolean autoRestartHttpdOnClusterReload ) {

		this.autoRestartHttpdOnClusterReload = autoRestartHttpdOnClusterReload ;

	}

	public void setUiDefaultView ( String defaultUiDisplayCluster ) {

		this.uiDefaultView = defaultUiDisplayCluster ;

	}

	public void setLoadbalancerUrl ( String lbUrl ) {

		this.loadbalancerUrl = lbUrl ;

	}

	public void setMetricsUrl ( String metricsUrl ) {

		if ( ! metricsUrl.equalsIgnoreCase( "no" ) ) {

			csapMetricsUploadEnabled = true ;

		}

		this.metricsUrl = metricsUrl ;

	}

	public void setNumberWorkerThreads ( int numberWorkerThreads ) {

		this.numberWorkerThreads = numberWorkerThreads ;

	}

	public long getMaxJmxCollectionMs ( ) {

		return maxJmxCollectionMs ;

	}

	/**
	 * @return the helpUrBase
	 */
	public String getHelpUrBase ( ) {

		return helpUrBase ;

	}

	public String getUserLookupUrl ( String user ) {

		return helpUrBase + user ;

	}

	/**
	 * @param helpUrBase the helpUrBase to set
	 */
	public void setHelpUrBase ( String helpUrBase ) {

		this.helpUrBase = helpUrBase ;

	}

	/**
	 * @return the limitSamples
	 */
	public int getLimitSamples ( ) {

		return limitSamples ;

	}

	/**
	 * @param limitSamples the limitSamples to set
	 */
	public void setLimitSamples ( int limitSamples ) {

		this.limitSamples = limitSamples ;

	}

	/**
	 * @return the eolJarPatters
	 */
	public ArrayNode getEolJarPatterns ( ) {

		return eolJarPatterns ;

	}

	/**
	 * @param eolJarPatterns the eolJarPatters to set
	 */
	public void setEolJarPatterns ( ArrayNode eolJarPatterns ) {

		this.eolJarPatterns = eolJarPatterns ;

	}

	public String getAgentUser ( ) {

		return agentUser ;

	}

	public void setAgentUser ( String agentUser ) {

		this.agentUser = agentUser ;

	}

	public String getAgentPass ( ) {

		return agentPass ;

	}

	public void setAgentPass ( String agentPass ) {

		this.agentPass = agentPass ;

	}

	public boolean isAgentLocalAuth ( ) {

		return agentLocalAuth ;

	}

	public void setAgentLocalAuth ( boolean agentUseLdap ) {

		this.agentLocalAuth = agentUseLdap ;

	}

	public long getLogRotationMinutes ( ) {

		return logRotationMinutes ;

	}

	public Map<String, ObjectNode> getMonitorMap ( ) {

		return cluster_to_alertLimits ;

	}

	public String getUiDefaultView ( ) {

		return uiDefaultView ;

	}

	public String getPrimaryNetwork ( ) {

		if ( primaryNetwork == null ) {

			primaryNetwork = System.getenv( "csapPrimaryInterface" ) ;

		}

		if ( primaryNetwork == null ) {

			primaryNetwork = "eth0" ;

		}

		return primaryNetwork ;

	}

	public void setPrimaryNetwork ( String primaryNetwork ) {

		this.primaryNetwork = primaryNetwork ;

	}

	public List<String> getEditorNotes ( ) {

		JsonNode editorNotes = getDefinition( ).path( "editor-notes" ) ;

		if ( editorNotes.isArray( ) ) {

			try {

				return jacksonMapper.readValue( editorNotes.traverse( ),
						new TypeReference<ArrayList<String>>( ) {
						} ) ;

			} catch ( Exception e ) {

				logger.warn( "{}", CSAP.buildCsapStack( e ) ) ;

			}

		}

		return new ArrayList<>( ) ;

	}

	public String getSecondaryEventUrl ( ) {

		return secondaryEventUrl ;

	}

	public void setSecondaryEventUrl ( String secondaryEventUrl ) {

		this.secondaryEventUrl = secondaryEventUrl ;

	}

	public String getSecondaryEventUser ( ) {

		return secondaryEventUser ;

	}

	public void setSecondaryEventUser ( String secondaryEventUser ) {

		this.secondaryEventUser = secondaryEventUser ;

	}

	public String getSecondaryEventPass ( ) {

		return secondaryEventPass ;

	}

	public void setSecondaryEventPass ( String secondaryEventPass ) {

		this.secondaryEventPass = secondaryEventPass ;

	}

	public boolean isSecondaryEventPublishEnabled ( ) {

		return secondaryEventPublishEnabled ;

	}

	public void setSecondaryEventPublishEnabled ( boolean secondaryEventPublishEnabled ) {

		this.secondaryEventPublishEnabled = secondaryEventPublishEnabled ;

	}

	public boolean isVsphereConfigured ( ) {

		if ( ( vsphereConfiguration == null )
				|| ! vsphereConfiguration.isObject( )
				|| ! vsphereConfiguration.path( "env" ).isObject( ) ) {

			return false ;

		}

		return true ;

	}

	public Map<String, String> getVsphereEnv ( ) {

		var environmentVariables = //
				CSAP.asStreamHandleNulls( (ObjectNode) vsphereConfiguration.path( "env" ) )
						.collect( Collectors.toMap(
								name -> name,
								name -> vsphereConfiguration.path( "env" ).path( name ).asText( ) ) ) ;

		if ( environmentVariables.containsKey( "GOVC_PASSWORD" ) ) {

			environmentVariables.put(
					"GOVC_PASSWORD",
					csapApplication.decode( environmentVariables.get( "GOVC_PASSWORD" ),
							"vsphere integration password" ) ) ;

		}

		return environmentVariables ;

	}

	public JsonNode getVsphereConfiguration ( ) {

		return vsphereConfiguration ;

	}

	public void setVsphereConfiguration ( JsonNode vsphereConfiguration ) {

		this.vsphereConfiguration = vsphereConfiguration ;

	}

	private String tomcatAjpSecret = "demo-ajp-secret" ;

	private String applicationName = "DefaultName" ;

	List<String> subProjects = new ArrayList<String>( ) ;

	private String definitionRepoUrl = "NoSourceControl" ;
	boolean definitionAbortOnBaseMismatch = false ;
	private String definitionRepoType = VersionControl.ScmProvider.git.key ;
	private String definitionRepoBranch = "master" ;
	private String mavenRepositoryUrl = "" ;

	public String getMavenRepositoryUrl ( ) {

		return mavenRepositoryUrl ;

	}

	public void setMavenRepositoryUrl ( String defaultMavenRepo ) {

		this.mavenRepositoryUrl = defaultMavenRepo ;

	}

	public String getDefinitionRepoBranch ( ) {

		return definitionRepoBranch ;

	}

	public void setDefinitionRepoBranch ( String capabilityScmBranch ) {

		this.definitionRepoBranch = capabilityScmBranch ;

	}

	public String getDefinitionRepoType ( ) {

		return definitionRepoType ;

	}

	public void setDefinitionRepoType ( String capabilityScmType ) {

		this.definitionRepoType = capabilityScmType ;

	}

	public String getTomcatAjpSecret ( ) {

		return tomcatAjpSecret ;

	}

	public String getApplicationName ( ) {

		return applicationName ;

	}

	public String getDefinitionRepoUrl ( ) {

		return definitionRepoUrl ;

	}

	public boolean isDefinitionAbortOnBaseMismatch ( ) {

		return definitionAbortOnBaseMismatch ;

	}

	public void setDefinitionAbortOnBaseMismatch ( boolean definitionAbortOnBaseMismatch ) {

		this.definitionAbortOnBaseMismatch = definitionAbortOnBaseMismatch ;

	}

	public void setTomcatAjpSecret ( String capabilityAjpSecret ) {

		this.tomcatAjpSecret = capabilityAjpSecret ;

	}

	public void setApplicationName ( String capabilityName ) {

		this.applicationName = capabilityName ;

	}

	public void setDefinitionRepoUrl ( String capabilityScm ) {

		this.definitionRepoUrl = capabilityScm ;

	}

	public boolean isBaseEnvOnly ( ) {

		return baseEnvOnly ;

	}

	public void setBaseEnvOnly ( boolean baseEnvOnly ) {

		this.baseEnvOnly = baseEnvOnly ;

	}

	public List<String> getSubProjects ( ) {

		return subProjects ;

	}

	public void setSubProjects ( List<String> subProjects ) {

		this.subProjects = subProjects ;

	}

	public int getKubernetesEventInspectMinutes ( ) {

		return kubernetesEventInspectMinutes ;

	}

	public void setKubernetesEventInspectMinutes ( int kubernetesEventWarningMinutes ) {

		this.kubernetesEventInspectMinutes = kubernetesEventWarningMinutes ;

	}

	public int getKubernetesEventInspectMax ( ) {

		return kubernetesEventInspectMax ;

	}

	public void setKubernetesEventInspectMax ( int kubernetesEventWarningMax ) {

		this.kubernetesEventInspectMax = kubernetesEventWarningMax ;

	}

	public List<String> getKubernetesEventExcludes ( ) {

		return kubernetesEventExcludes ;

	}

	public void setKubernetesEventExcludes ( List<String> kubernetesEventWarningExclude ) {

		this.kubernetesEventExcludes = kubernetesEventWarningExclude ;

	}

	public int getDockerMaxContainers ( ) {

		return dockerMaxContainers ;

	}

	public void setDockerMaxContainers ( int dockerMaxContainers ) {

		this.dockerMaxContainers = dockerMaxContainers ;

	}

	public int getDockerMaxImages ( ) {

		return dockerMaxImages ;

	}

	public void setDockerMaxImages ( int dockerMaxImages ) {

		this.dockerMaxImages = dockerMaxImages ;

	}

	public int getDockerMaxVolumes ( ) {

		return dockerMaxVolumes ;

	}

	public void setDockerMaxVolumes ( int dockerMaxVolumes ) {

		this.dockerMaxVolumes = dockerMaxVolumes ;

	}

	@Override
	public String toString ( ) {

		return WordUtils.wrap( "EnvironmentSettings [helpUrBase=" + helpUrBase + ", limitSamples=" + limitSamples
				+ ", jacksonMapper="
				+ jacksonMapper
				+ ", definition=" + definition + ", eolJarPatterns=" + eolJarPatterns + ", configurationMaps="
				+ configurationMaps
				+ ", trendingJson=" + trendingJson + ", realTimeMetersJson=" + realTimeMetersJson
				+ ", secondaryEventUrl="
				+ secondaryEventUrl + ", secondaryEventUser=" + secondaryEventUser + ", secondaryEventPass="
				+ secondaryEventPass
				+ ", secondaryEventPublishEnabled=" + secondaryEventPublishEnabled + ", eventUser=" + eventUser
				+ ", eventCredential="
				+ eventCredential + ", eventServiceUrl=" + eventServiceUrl + ", eventPublishEnabled="
				+ eventPublishEnabled
				+ ", csapMetricsUploadEnabled=" + csapMetricsUploadEnabled + ", eventUrl=" + eventUrl
				+ ", eventMetricsUrl="
				+ eventMetricsUrl + ", reportURI=" + reportURI + ", performancePortalUrl=" + performancePortalUrl
				+ ", historyUiUrl="
				+ historyUiUrl + ", mavenCommand=" + mavenCommand + ", agentUser=" + agentUser + ", agentPass="
				+ agentPass
				+ ", agentLocalAuth=" + agentLocalAuth + ", iostatDeviceFilter=" + iostatDeviceFilter
				+ ", autoRestartHttpdOnClusterReload="
				+ autoRestartHttpdOnClusterReload + ", lsofIntervalMins=" + lsofIntervalMins + ", newsJsonArray="
				+ newsJsonArray
				+ ", emailJsonArray=" + emailJsonArray + ", resolvedUiCountUrl=" + resolvedUiCountUrl
				+ ", resolvedHostCountUrl="
				+ resolvedHostCountUrl + ", maxJmxCollectionMs=" + maxJmxCollectionMs + ", defaultMinFreeMemoryMb="
				+ defaultMinFreeMemoryMb
				+ ", defaultMaxDiskPercent=" + defaultMaxDiskPercent + ", defaultMaxDeviceIoPercent="
				+ defaultMaxDeviceIoPercent
				+ ", defaultMaxHostCpuLoad=" + defaultMaxHostCpuLoad + ", defaultMaxHostCpu=" + defaultMaxHostCpu
				+ ", defaultMaxHostCpuIoWait=" + defaultMaxHostCpuIoWait + ", kubernetesEventInspectMinutes="
				+ kubernetesEventInspectMinutes + ", kubernetesEventInspectMax=" + kubernetesEventInspectMax
				+ ", kubernetesEventExcludes="
				+ kubernetesEventExcludes + ", dockerMaxContainers=" + dockerMaxContainers + ", dockerMaxImages="
				+ dockerMaxImages
				+ ", dockerMaxVolumes=" + dockerMaxVolumes + ", cluster_to_alertLimits=" + cluster_to_alertLimits
				+ ", host_to_alertLimits="
				+ host_to_alertLimits + ", uiDefaultView=" + uiDefaultView + ", labelToServiceUrlLaunchMap="
				+ labelToServiceUrlLaunchMap
				+ ", loadbalancerUrl=" + loadbalancerUrl + ", monitoringUrl=" + monitoringUrl + ", metricsUrl="
				+ metricsUrl
				+ ", metricToSecondsMap=" + metricToSecondsMap + ", serviceLimits=" + serviceLimits + ", psDumpCount="
				+ psDumpCount
				+ ", psDumpLowMemoryInMb=" + psDumpLowMemoryInMb + ", numberWorkerThreads=" + numberWorkerThreads
				+ ", metricsPublication="
				+ metricsPublication + ", portStart=" + portStart + ", portEnd=" + portEnd + ", duIntervalMins="
				+ duIntervalMins
				+ ", primaryNetwork=" + primaryNetwork + ", logRotationMinutes=" + logRotationMinutes
				+ ", vsphereConfiguration="
				+ vsphereConfiguration + ", baseEnvOnly=" + baseEnvOnly + ", csapApplication=" + csapApplication
				+ ", helpMenuUrlMap="
				+ helpMenuUrlMap + ", loadBalanceVmFilter=" + loadBalanceVmFilter + ", uploadIntervalsInHoursJson="
				+ uploadIntervalsInHoursJson + ", infraTests=" + infraTests + ", gitSslVerificationDisabledUrls="
				+ gitSslVerificationDisabledUrls + ", adminToAgentTimeout=" + adminToAgentTimeout + ", secureUrl="
				+ secureUrl
				+ ", browseDisks=" + browseDisks + ", applicationDisks=" + applicationDisks + ", inMemoryCacheSize="
				+ inMemoryCacheSize
				+ ", psDumpInterval=" + psDumpInterval + ", defaultAutoStopServiceThreshold="
				+ defaultAutoStopServiceThreshold
				+ ", defaultMaxDiskPercentIgnorePatterns=" + Arrays.toString( defaultMaxDiskPercentIgnorePatterns )
				+ ", metricsPublicationNode=" + metricsPublicationNode + ", resolvedHistoryUrl=" + resolvedHistoryUrl
				+ ", hostHealthUrl="
				+ hostHealthUrl + ", hostActivityUrl=" + hostActivityUrl + ", resolvedAnalyticsUrl="
				+ resolvedAnalyticsUrl
				+ ", tomcatAjpSecret=" + tomcatAjpSecret + ", applicationName=" + applicationName + ", subProjects="
				+ subProjects
				+ ", definitionRepoUrl=" + definitionRepoUrl + ", definitionRepoType=" + definitionRepoType
				+ ", definitionRepoBranch="
				+ definitionRepoBranch + ", mavenRepositoryUrl=" + mavenRepositoryUrl + "]",
				50 ) ;

	}

	public int getKubernetesCertMinimumDays ( ) {

		return kubernetesCertMinimumDays ;

	}

	public void setKubernetesCertMinimumDays ( int kubernetesCertMinimumDays ) {

		this.kubernetesCertMinimumDays = kubernetesCertMinimumDays ;

	}

	public List<String> getMonitoredNamespaces ( ) {

		if ( getKubernetesNamespaceCollection( ).isArray( ) ) {

			return CSAP.jsonList( getKubernetesNamespaceCollection( ) ) ;

		}

		return null ;

	}

	public JsonNode getKubernetesNamespaceCollection ( ) {

		return kubernetesNamespaceCollection ;

	}

	public void setKubernetesNamespaceCollection ( JsonNode kubernetesNamespaceCollection ) {

		this.kubernetesNamespaceCollection = kubernetesNamespaceCollection ;

	}

}
