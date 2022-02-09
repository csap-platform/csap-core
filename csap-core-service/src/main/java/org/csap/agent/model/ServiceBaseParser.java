/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.csap.agent.model ;

import java.io.File ;
import java.time.LocalTime ;
import java.time.format.DateTimeFormatter ;
import java.util.ArrayList ;
import java.util.Arrays ;
import java.util.HashMap ;
import java.util.LinkedList ;
import java.util.List ;
import java.util.Map ;
import java.util.Optional ;
import java.util.regex.Matcher ;
import java.util.stream.Stream ;

import org.apache.commons.lang3.StringUtils ;
import org.apache.commons.lang3.text.WordUtils ;
import org.csap.agent.CsapApis ;
import org.csap.agent.CsapConstants ;
import org.csap.agent.CsapTemplates ;
import org.csap.agent.container.C7 ;
import org.csap.agent.container.ContainerIntegration ;
import org.csap.agent.container.kubernetes.KubernetesIntegration ;
import org.csap.agent.container.kubernetes.K8 ;
import org.csap.agent.integrations.MetricsPublisher ;
import org.csap.agent.linux.ServiceJobRunner ;
import org.csap.agent.model.Application.FileToken ;
import org.csap.agent.stats.service.ServiceMeter ;
import org.csap.helpers.CSAP ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;

import com.fasterxml.jackson.annotation.JsonIgnore ;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties ;
import com.fasterxml.jackson.annotation.JsonProperty ;
import com.fasterxml.jackson.databind.JsonNode ;
import com.fasterxml.jackson.databind.ObjectMapper ;
import com.fasterxml.jackson.databind.node.ArrayNode ;
import com.fasterxml.jackson.databind.node.MissingNode ;
import com.fasterxml.jackson.databind.node.ObjectNode ;

/**
 *
 * @author someDeveloper
 */
public class ServiceBaseParser extends ServiceBase {

	ObjectMapper jacksonMapper = new ObjectMapper( ) ;

	String packageName = "" ;

	static final Logger logger = LoggerFactory.getLogger( ServiceBaseParser.class ) ;

	// jvisualvm launches, etc
	public final static String ERRORS = "errors" ;
	public final static String NOT_FOUND = "notFound" ;
	private final static String NO_JMX_RMI = "noJmxRmiAvailable" ;

	private Map<ServiceAttributes, JsonNode> definitionAttributes = new HashMap<>( ) ;

	public String getAttribute ( ServiceAttributes attribute ) {

		if ( definitionAttributes.containsKey( attribute ) ) {

			return definitionAttributes.get( attribute ).asText( "Not Found" ).trim( ) ;

		}

		return NOT_FOUND ;

	}

	public String resolveRuntimeVariables ( String unparsedDefinition ) {

		if ( unparsedDefinition == null ) {

			return null ;

		}

		// handle legacy migration
		// unparsedDefinition =
		// CsapApis.getInstance().application().getProjectLoader().getProjectMigrator().migrateServiceVariables(
		// unparsedDefinition ) ;

		// logger.info( "input: {}", input );

		String input_with_variables_replaced = unparsedDefinition
				.trim( )
				.replaceAll(
						Matcher.quoteReplacement( CsapConstants.CSAP_DEF_PARAMETERS ),
						Matcher.quoteReplacement( getParameters( ) ) ) ;

		input_with_variables_replaced = input_with_variables_replaced
				.trim( )
				.replaceAll(
						Matcher.quoteReplacement( CsapConstants.CSAP_DEF_REPLICA ),
						Matcher.quoteReplacement( getKubernetesReplicaCount( ).asText( "1" ) ) ) ;

		if ( getKubernetesNamespace( ) != null ) {

			input_with_variables_replaced = input_with_variables_replaced
					.trim( )
					.replaceAll(
							Matcher.quoteReplacement( CsapConstants.CSAP_DEF_NAMESPACE ),
							Matcher.quoteReplacement( getKubernetesNamespace( ) ) ) ;

		}

		var dockerImage = getDockerImageName( ) ;

		if ( StringUtils.isEmpty( dockerImage ) ) {

			dockerImage = "image-not-specified" ;

		}

		input_with_variables_replaced = input_with_variables_replaced
				.trim( )
				.replaceAll(
						Matcher.quoteReplacement( CsapConstants.CSAP_DEF_IMAGE ),
						Matcher.quoteReplacement( dockerImage ) ) ;

		var helmChartName = getHelmChartName( ) ;

		if ( StringUtils.isEmpty( helmChartName ) ) {

			helmChartName = "chart-not-specified" ;

		}

		input_with_variables_replaced = input_with_variables_replaced
				.trim( )
				.replaceAll(
						Matcher.quoteReplacement( CsapConstants.CSAP_DEF_HELM_CHART_NAME ),
						Matcher.quoteReplacement( helmChartName ) ) ;

		input_with_variables_replaced = input_with_variables_replaced
				.trim( )
				.replaceAll(
						Matcher.quoteReplacement( CsapConstants.CSAP_DEF_HELM_CHART_VERSION ),
						Matcher.quoteReplacement( getHelmChartVersion( ) ) ) ;

		input_with_variables_replaced = input_with_variables_replaced
				.trim( )
				.replaceAll(
						Matcher.quoteReplacement( CsapConstants.CSAP_DEF_HELM_CHART_REPO ),
						Matcher.quoteReplacement( getHelmChartRepo( ) ) ) ;

		input_with_variables_replaced = resolveTemplateVariables( input_with_variables_replaced ) ;

		return input_with_variables_replaced ;

	}

	public String getParameters ( ) {

		// logger.info( "raw: {}", definitionAttributes.get(
		// ServiceAttributes.parameters ) );
		if ( definitionAttributes.containsKey( ServiceAttributes.parameters ) ) {

			return definitionAttributes.get( ServiceAttributes.parameters ).asText( "Not Found" ).trim( ) ;

		}

		return "" ;

	}

	public int getAttributeAsNumber ( ServiceAttributes attribute ) {

		if ( definitionAttributes.containsKey( attribute ) ) {

			return definitionAttributes.get( attribute ).asInt( -1 ) ;

		}

		return -1 ;

	}

	public ObjectNode getAttributeAsObject ( ServiceAttributes attribute ) {

		return (ObjectNode) definitionAttributes.get( attribute ) ;

	}

	public JsonNode getAttributeOrMissing ( ServiceAttributes attribute ) {

		JsonNode result = definitionAttributes.get( attribute ) ;

		if ( result == null ) {

			result = MissingNode.getInstance( ) ;

		}

		return result ;

	}

	public JsonNode getAttributeAsJson ( ServiceAttributes attribute ) {

		return definitionAttributes.get( attribute ) ;

	}

	public String getJavaVersion ( ) {

		String params = getParameters( ) ;

		if ( params.contains( "csapJava9" ) ) {

			return "9" ;

		}

		if ( params.contains( "csapJava8" ) ) {

			return "8" ;

		}

		if ( params.contains( "csapJava7" ) ) {

			return "7" ;

		}

		return "default" ;

	}

	public boolean isJmxRmi ( ) {

		String params = getParameters( ) ;

		// hook for disabling rmi,
		// https://github.com/csap-platform/csap-core/wiki#updateRefTomcat+Advanced+Configuration
		if ( params.contains( "DnoJmxFirewall" )
				|| is_springboot_server( )
				|| getMetaData( ).contains( NO_JMX_RMI ) ) {

			return false ;

		}

		return true ;

	}

	public Stream<String> environmentVariableNames ( ) {

		Stream<String> namesOnlys = CSAP.asStreamHandleNulls( getAttributeAsObject(
				ServiceAttributes.environmentVariables ) )
				.filter( name -> {

					return ! name.equals( "lifecycle" ) ;

				} ) ;

		return namesOnlys ;

	}

	public Stream<String> environmentLifeVariableNames ( ) {

		ObjectNode lifeVars = getLifeEnvironmentVariables( ) ;

		return CSAP.asStreamHandleNulls( lifeVars ) ;

	}

	public ObjectNode getLifeEnvironmentVariables ( ) {

		ObjectNode vars = getAttributeAsObject( ServiceAttributes.environmentVariables ) ;

		if ( vars != null ) {

			JsonNode lifeJson = vars.at( "/lifecycle/" + CsapApis.getInstance( ).application( )
					.getCsapHostEnvironmentName( ) ) ;

			if ( ! lifeJson.isMissingNode( ) && lifeJson.isObject( ) ) {

				return (ObjectNode) lifeJson ;

			}

		}

		return jacksonMapper.createObjectNode( ) ;

	}

	public ObjectNode getPerformanceConfiguration ( ) {

		return getAttributeAsObject( ServiceAttributes.performanceApplication ) ;

	}

	public boolean isApplicationHealthEnabled ( ) {

		return isHttpHealthReportEnabled( ) ;

	}

	public boolean isHttpHealthReportEnabled ( ) {

		boolean httpHealth = isHttpCollectionEnabled( ) && getHttpCollectionSettings( ).has(
				ModelJson.healthCollectionUrl.jpath( ) ) ;
		return httpHealth ;

	}

	public Stream<String> performanceAttributeNames ( ) {

		return CSAP.asStreamHandleNulls( getPerformanceConfiguration( ) ) ;

	}

	public boolean hasJobs ( ) {

		// logger.info( toSummaryString() );
		if ( getJobs( ).isEmpty( ) ) {

			return false ;

		}

		return true ;

	}

	private List<LogRotation> logsToRotate = new ArrayList<LogRotation>( ) ;

	public List<LogRotation> getLogsToRotate ( ) {

		return logsToRotate ;

	}

	@JsonIgnoreProperties ( ignoreUnknown = true )
	public static class LogRotation {
		public String getPath ( ) {

			return path ;

		}

		public void setPath ( String path ) {

			this.path = path ;

		}

		public String getLifecycles ( ) {

			return lifecycles ;

		}

		public void setLifecycles ( String lifecycle ) {

			this.lifecycles = lifecycle ;

		}

		public String getSettings ( ) {

			return settings ;

		}

		public void setSettings ( String settings ) {

			this.settings = settings ;

		}

		String path ;
		String lifecycles = "all" ;
		String settings ;

		@Override
		public String toString ( ) {

			return "LogRotation [path=" + path + ", lifecycle=" + lifecycles + ", settings=" + settings + "]" ;

		}

		public boolean isActive ( ) {

			if ( lifecycles.equalsIgnoreCase( "all" ) ||
					lifecycles.equalsIgnoreCase( CsapApis.getInstance( ).application( )
							.getCsapHostEnvironmentName( ) ) ) {

				return true ;

			}

			List<String> selectedLifes = Arrays.asList( getLifecycles( ).split( "," ) ) ;

			logger.debug( "Application.getCurrentLifeCycle: {}", CsapApis.getInstance( ).application( )
					.getCsapHostEnvironmentName( ) ) ;
			if ( selectedLifes.contains( CsapApis.getInstance( ).application( ).getCsapHostEnvironmentName( ) ) )
				return true ;

			return false ;

		}

	}

	private List<ServiceJob> serviceJobs = null ;

	@JsonIgnoreProperties ( ignoreUnknown = true )
	public static class ServiceJob {
		String script = "" ;

		String description = "" ;

		boolean runInBackground = false ;

		List<String> environmentFilters = List.of( ".*" ) ;

		List<String> hostFilters = List.of( ".*" ) ;

		List<String> parameters = List.of( ) ;

		public void setDescription ( String description ) {

			this.description = description ;

		}

		String frequency = ServiceJobRunner.Event.daily.json( ) ;
		String hour = "01" ;

		boolean pruneEmptyFolders = false ;

		boolean pruneByFolder = false ;

		public boolean isPruneByFolder ( ) {

			return pruneByFolder ;

		}

		public void setPruneByFolder ( boolean pruneByFolder ) {

			this.pruneByFolder = pruneByFolder ;

		}

		public boolean isPruneEmptyFolders ( ) {

			return pruneEmptyFolders ;

		}

		public void setPruneEmptyFolders ( boolean pruneEmptyFolders ) {

			this.pruneEmptyFolders = pruneEmptyFolders ;

		}

		// max depth for disk clean folders
		int maxDepth = 3 ;

		public int getMaxDepth ( ) {

			return maxDepth ;

		}

		public void setMaxDepth ( int maxDepth ) {

			this.maxDepth = maxDepth ;

		}

		public boolean isDiskCleanJob ( ) {

			return isDiskCleanJob ;

		}

		public boolean isMatchingJob ( String key ) {

			// logger.info( "Comparing key: {} to {} ", key, getDescription() );
			return description.equals( key ) ;

		}

		public void setDiskCleanJob ( boolean isDiskCleanJob ) {

			this.isDiskCleanJob = isDiskCleanJob ;

		}

		public String getPath ( ) {

			return path ;

		}

		public int getOlderThenDays ( ) {

			return olderThenDays ;

		}

		String path = "" ;

		public void setPath ( String path ) {

			this.path = path ;

		}

		int olderThenDays = -1 ;
		boolean isDiskCleanJob = false ;

		public void setScript ( String script ) {

			this.script = script ;

		}

		public String getHour ( ) {

			return hour ;

		}

		public String getScript ( ) {

			return script ;

		}

		public String getDescription ( ) {

			return description ;

		}

		public String getFrequency ( ) {

			return frequency ;

		}

		@Override
		public String toString ( ) {

			return "ServiceJob [script=" + script
					+ ", description=" + description
					+ ", parameters=" + getParameters( )
					+ ", lifecycle filters=" + getEnvironmentFilters( )
					+ ", frequency=" + frequency
					+ ", hour=" + hour + "]" ;

		}

		// support hourly or daily at given hour
		public boolean isTimeToRun ( ) {

			if ( getFrequency( ).trim( ).equals( ServiceJobRunner.Event.hourly.json( ) ) ) {

				return true ;

			} else if ( getFrequency( ).trim( ).equals( ServiceJobRunner.Event.daily.json( ) ) ) {

				LocalTime currentTime = LocalTime.now( ) ; // current time
				String currentHour = currentTime.format( DateTimeFormatter.ofPattern( "HH" ) ) ;

				if ( currentHour.matches( getHour( ) ) ) {

					return true ;

				}

			}

			return false ;

		}

		public List<String> getEnvironmentFilters ( ) {

			return environmentFilters ;

		}

		@JsonProperty ( "environment-filters" )
		public void setEnvironmentFilters ( List<String> lifecycleFilters ) {

			this.environmentFilters = lifecycleFilters ;

		}

		public void setFrequency ( String frequency ) {

			this.frequency = frequency ;

		}

		public List<String> getParameters ( ) {

			return parameters ;

		}

		@JsonProperty ( "parameters" )
		public void setParmaters ( List<String> params ) {

			this.parameters = params ;

		}

		public List<String> getHostFilters ( ) {

			return hostFilters ;

		}

		@JsonProperty ( "host-filters" )
		public void setHostFilters ( List<String> hostFilter ) {

			this.hostFilters = hostFilter ;

		}

		public boolean isRunInBackground ( ) {

			return runInBackground ;

		}

		@JsonProperty ( "background" )
		public void setRunInBackground ( boolean runInBackground ) {

			this.runInBackground = runInBackground ;

		}

	}

	/**
	 * @return the jobs
	 */
	public List<ServiceJob> getJobs ( ) {

		if ( serviceJobs == null ) {

			serviceJobs = new LinkedList<ServiceJob>( ) ;

			StringBuilder resultsBuffer = new StringBuilder( ) ;

			try {

				buildJobs( resultsBuffer ) ;

			} catch ( Exception e ) {

				// Add Warnings
				logger.error( "{} Failed parsing: {}", getName( ), CSAP.buildCsapStack( e ) ) ;
				updateServiceParseResults( resultsBuffer, CsapConstants.CONFIG_PARSE_WARN,
						getErrorHeader( ) + " not able to parse: " + ServiceAttributes.scheduledJobs.json( ) ) ;

			}

		}

		return serviceJobs ;

	}

	public ObjectNode getMonitors ( ) {

		return getAttributeAsObject( ServiceAttributes.osAlertLimits ) ;

	}

	public ObjectNode getDockerSettings ( ) {

		return getAttributeAsObject( ServiceAttributes.dockerSettings ) ;

	}

	public JsonNode getDockerSettingsOrMissing ( ) {

		return getAttributeOrMissing( ServiceAttributes.dockerSettings ) ;

	}

	public JsonNode getDockerLocator ( ) {

		return getAttributeOrMissing( ServiceAttributes.dockerSettings ).path( C7.locator.val( ) ) ;

	}

	public boolean isKubernetesNamespaceMonitor ( ) {

		return is_cluster_kubernetes( )
				&& getAttributeOrMissing( ServiceAttributes.dockerSettings )
						.path( K8.namespaceMonitor.val( ) )
						.asBoolean( false ) ;

	}

	public boolean isAggregateContainerMetrics ( ) {

		return is_cluster_kubernetes( )
				&& getAttributeOrMissing( ServiceAttributes.dockerSettings )
						.path( C7.aggregateContainers.val( ) )
						.asBoolean( false ) ;

	}

	@JsonIgnore
	public JsonNode getKubernetesDeploymentSpecifications ( ) {

		var deployFiles = getDockerSettingsOrMissing( ).path( C7.deploymentFileNames.val( ) ) ;

		//
		// handle helm deploy injection
		//

		if ( isHelmConfigured( ) ) {

			var addHelmDeployTemplate = false ;

			if ( deployFiles.isMissingNode( ) ) {

				addHelmDeployTemplate = true ;

				deployFiles = jacksonMapper.createArrayNode( ) ;

			} else {

				var shellScriptOptional = CSAP.jsonStream( deployFiles )
						.map( JsonNode::asText )
						.filter( fileName -> fileName.endsWith( ".sh" ) )
						.findFirst( ) ;

				if ( shellScriptOptional.isEmpty( ) ) {

					addHelmDeployTemplate = true ;

				}

			}

			if ( addHelmDeployTemplate
					&& deployFiles.isArray( ) ) {

				( (ArrayNode) deployFiles ).add(
						CsapTemplates.helmDeploy.getFile( ).getAbsolutePath( ) ) ;

			}

		}

		return deployFiles ;

	}

	public boolean isSkipSpecificationGeneration ( ) {

		return getDockerSettingsOrMissing( ).path( C7.isSkipSpecGeneration.val( ) ).asBoolean( ) ;

	}

	public boolean isRunningAsRoot ( ) {

		if ( isRunUsingDocker( ) ) {

			if ( getDockerSettings( ) != null && getDockerSettings( ).has( C7.runUser.val( ) ) ) {

				String user = getDockerSettings( ).get( C7.runUser.val( ) ).asText( ) ;

				if ( user.length( ) == 0 || user.startsWith( "0" ) ) {

					return true ;

				}

			}

		}

		return false ;

	}

	public String[] getDiskUsageMatcher ( ContainerState container ) {

		String matchString = getResolvedDisk( ) ;

		if ( getDisk( ).equals( CsapConstants.CSAP_DEF_WORKING ) &&
				( is_docker_server( ) || isRunUsingDocker( ) ) ) {

			matchString = getDockerContainerName( ) ;

			if ( container != null
					&& StringUtils.isNotEmpty( container.getContainerName( ) )
					&& ! container.getContainerName( ).equals( "default" ) ) {

				matchString = container.getContainerName( ).substring( 1 ) ;

			}

		}

		return matchString.split( " " ) ;

	}

	public String getDiskUsagePath ( ) {

		logger.debug( "{} getDisk {}, {}", getName( ), getDisk( ) ) ;

		if ( ( is_docker_server( ) ||
				isRunUsingDocker( ) )
				&& StringUtils.isEmpty( getRawDisk( ) ) ) {

			// disk usage for containers or device names handled separately
			return "" ;

		}

		return getResolvedDisk( ) ;

	}

	private String getResolvedDisk ( ) {

		String duPathForService = resolveTemplateVariables( getDisk( ) ) ;

		if ( duPathForService.contains( CsapConstants.CSAP_VARIABLE_PREFIX ) ) {

			// assuming env var
			try {

				duPathForService = CsapApis.getInstance( ).application( ).resolveDefinitionVariables( duPathForService,
						(ServiceInstance) this ) ;

			} catch ( Exception e ) {

				logger.warn( "{} Failed parsing {} {}", getName( ), duPathForService, CSAP.buildCsapStack( e ) ) ;

			}

		}

		if ( duPathForService.contains( "$" ) ) {

			logger.warn( "{} Failed to resolve {}", getName( ), duPathForService ) ;
			duPathForService = resolveTemplateVariables( CsapConstants.CSAP_DEF_WORKING ) ;

		}

		if ( duPathForService.startsWith( "/mnt/" ) ) {

			File mntTest = new File( duPathForService ) ;

			if ( mntTest.getParentFile( ).getName( ).equals( "mnt" ) ) {

				// assuming mnted file system, avoiding scanning and relying on df instead
				duPathForService = duPathForService.substring( 1 ) ;

			}

		}

		return duPathForService ;

	}

	public String getDockerContainerPath ( ) {

		return "/" + getDockerContainerName( ) ;

	}

	public String getDockerContainerName ( ) {

		// String name = getServiceName_Port() ;
		String name = getName( ) ;

		if ( getDockerSettings( ) != null && getDockerSettings( ).has( C7.containerName.val( ) ) ) {

			name = getDockerSettings( ).path( C7.containerName.val( ) ).asText( ) ;

		}

		return ContainerIntegration.getNetworkSafeContainerName(
				resolveTemplateVariables( name ) ) ;

	}

	public String getDockerVersionCommand ( ) {

		if ( getDockerSettings( ) != null && getDockerSettings( ).has( C7.versionCommand.val( ) ) ) {

			return getDockerSettings( ).get( C7.versionCommand.val( ) ).asText( ) ;

		}

		return null ;

	}

	public JsonNode getKubernetesReplicaCount ( ) {

		JsonNode replicaCount = MissingNode.getInstance( ) ;

		if ( getDockerSettings( ) != null ) {

			replicaCount = getDockerSettings( ).at( K8.replicaCount.spath( ) ) ;

			if ( replicaCount.isMissingNode( ) ) {

				replicaCount = getDockerSettings( ).path( C7.containerCount.val( ) ) ;

			}

		}

		return replicaCount ;

	}

	public boolean isKubernetesSchemaDeployment ( ) {

		return getKubernetesReplicaCount( ).asInt( -1 ) == 0 ;

	}

	public boolean is_Admin_UI_Package ( ) {

		return StringUtils.isEmpty( getProcessFilter( ) ) ||
				isKubernetesSchemaDeployment( ) ;

	}

	public boolean isDockerNamespaceSocketCollection ( ) {

		if ( getDockerSettings( ) != null ) {

			return ! getDockerSettings( )
					.path( C7.socketNamespace.val( ) ).asText( )
					.equals( "global" ) ;

		}

		return true ;

	}

	public String getDockerImageName ( ) {

		if ( getDockerSettings( ) != null && getDockerSettings( ).has( C7.imageName.val( ) ) ) {

			return getDockerSettings( ).path( C7.imageName.val( ) ).asText( ) ;

		}

		return ContainerIntegration.DOCKER_DEFAULT_IMAGE ;

	}

	public String getHelmChartName ( ) {

		if ( getDockerSettings( ) != null && getDockerSettings( ).has( C7.helmChartName.val( ) ) ) {

			return getDockerSettings( ).path( C7.helmChartName.val( ) ).asText( ) ;

		}

		return null ;

	}

	public boolean isHelmConfigured ( ) {

		return StringUtils.isNotEmpty( getHelmChartName( ) ) ;

	}

	public String getHelmChartVersion ( ) {

		if ( getDockerSettings( ) != null && getDockerSettings( ).has( C7.helmChartVersion.val( ) ) ) {

			return getDockerSettings( ).path( C7.helmChartVersion.val( ) ).asText( ) ;

		}

		return "latest" ;

	}

	public String getHelmChartRepo ( ) {

		if ( getDockerSettings( ) != null && getDockerSettings( ).has( C7.helmChartRepo.val( ) ) ) {

			return getDockerSettings( ).path( C7.helmChartRepo.val( ) ).asText( ) ;

		}

		return "none" ;

	}

	// URL or text
	public String getReadme ( ) {

		// logger.info( "raw: {}", definitionAttributes.get(
		// ServiceAttributes.parameters ) );
		if ( definitionAttributes.containsKey( ServiceAttributes.readme ) ) {

			return definitionAttributes.get( ServiceAttributes.readme ).asText( ).trim( ) ;

		}

		return "" ;

	}

	public boolean isReadmeConfigured ( ) {

		return StringUtils.isNotEmpty( getReadme( ) ) ;

	}

	public void overrideDockerImageName ( String image ) {

		logger.debug( "image: {}", image ) ;

		if ( getDockerSettings( ) != null && getDockerSettings( ).has( C7.imageName.val( ) ) ) {

			getDockerSettings( ).put( C7.imageName.val( ), image ) ;

		}

	}

	/**
	 * Parsing
	 */
	/**
	 *
	 * resultsBuffer set to null will ignore errors
	 *
	 * @param definitionNode
	 * @param resultsBuffer
	 */
	public void parseDefinition (
									String packageName ,
									JsonNode definitionNode ,
									StringBuilder resultsBuffer ) {

		this.packageName = packageName ;

		try {

			ServiceAttributes
					.stream( )
					.forEach(
							serviceAttribute -> processAttributeDefinition(
									serviceAttribute,
									definitionNode,
									resultsBuffer ) ) ;

			if ( ! definitionAttributes.containsKey( ServiceAttributes.processFilter ) ) {

				if ( is_csap_api_server( ) ) {

					setProcessFilter( ".*" + getName( ) + ".*" ) ;

				} else if ( is_java_application_server( ) ) {

					if ( is_springboot_server( ) ) {

						// handle spawned docker container on different port
						setProcessFilter( ".*java.*csapProcessId=" + getName( ) + ".*" + getPort( ) + ".*" ) ;

					} else {

						setProcessFilter( ".*java.*csapProcessId=" + getName( ) + ".*" ) ;

					}

				}

				// logger.info( "{} defaulting {}", getServiceName(),
				// getProcessFilter() );
			}

			if ( is_docker_server( ) || isRunUsingDocker( ) ) {

				if ( ! definitionAttributes.containsKey( ServiceAttributes.logJournalServices ) ) {

					setLogJournalServices( C7.dockerService.val( ) ) ;

				}

				if ( ! definitionAttributes.containsKey( ServiceAttributes.serviceUrl ) ) {

					setLogJournalServices( C7.dockerService.val( ) ) ;

				}

			}

			if ( CsapApis.getInstance( ).kubernetes( ) != null
					&& ! definitionAttributes.containsKey( ServiceAttributes.serviceUrl ) ) {

				if ( is_cluster_kubernetes( ) || getName( ).matches( KubernetesIntegration.getServicePattern( ) ) ) {

					setUrl( KubernetesIntegration.getDefaultUrl( getHostName( ) ) ) ;

				}

			}

		} catch ( Exception e ) {

			logger.error( "failed parsing service: '{}' \n definition: {} \n reason: {} ",
					getName( ),
					CSAP.jsonPrint( definitionNode ),
					CSAP.buildCsapStack( e ) ) ;

			resultsBuffer.append( CsapConstants.CONFIG_PARSE_ERROR
					+ getErrorHeader( )
					+ " could not be parsed." ) ;

		}

	}

	private void processAttributeDefinition (
												ServiceAttributes attribute ,
												JsonNode definitionNode ,
												StringBuilder resultsBuffer ) {

		String attributeText = attribute.json( ) ;

		if ( definitionNode.has( attributeText ) ) {

			attributeLoad( attribute, definitionNode, resultsBuffer ) ;

		} else {

			attributeMissingMessage( attribute, resultsBuffer ) ;

		}

	}

	public void attributeLoad (
								ServiceAttributes attribute ,
								JsonNode serviceDefinition ,
								StringBuilder resultsBuffer ) {

		String attributeName = attribute.json( ) ;
		JsonNode attributeDefinition = serviceDefinition.path( attributeName ) ;

		//
		// Environment processing: merge in changes
		//
		if ( ( attributeDefinition.isObject( ) )
				&& definitionAttributes.containsKey( attribute ) ) {

			// recursive support for overriding
			JsonNode previouslySetAttribute = definitionAttributes.get( attribute ) ;

			if ( previouslySetAttribute.isObject( ) ) {

				var mergedDefinition = previouslySetAttribute.deepCopy( ) ;
				var results = CSAP.mergeAttributes(
						(ObjectNode) mergedDefinition,
						(ObjectNode) attributeDefinition ) ;
				attributeDefinition = mergedDefinition ;
				logger.debug( "{} merge results: {}", getName( ), results ) ;

			} else {

				logger.warn( "Unexpected override type: {}, {}", this.toString( ), attribute ) ;

			}

		}

		if ( getName( ).equals( "etcd" ) && attribute.json( ).equals( C7.dockerService.val( ) ) ) {

			logger.debug( "etcd adding: {}", attributeDefinition ) ;

		}

		definitionAttributes.put( attribute, attributeDefinition ) ;

		switch ( attribute ) {

		case environmentVariables:
		case environmentOverload:
		case parameters:
		case readme:
		case webServerTomcat:
		case webServerReWrite:
		case osAlertLimits:
		case dockerSettings:
		case notifications:
		case javaAlertWarnings:
			break ;

		case scheduledJobs:
			// (ArrayNode) definitionAttributes.get( ServiceAttributes.jobs )
			// try {
			// buildJobs( resultsBuffer ) ;
			// } catch ( Exception e ) {
			// // Add Warnings
			// logger.error( "{} Failed parsing: {}", getName(), CSAP.buildCsapStack( e ) )
			// ;
			// updateServiceParseResults( resultsBuffer, CsapCore.CONFIG_PARSE_WARN,
			// getErrorHeader() + " not able to parse: " + attribute.json() ) ;
			// }
			break ;

		case performanceApplication:
			buildServiceMeters( resultsBuffer ) ;
			break ;

		case runUsingDocker:
			setRunUsingDocker( serviceDefinition.path( attributeName ).asBoolean( false ) ) ;
			break ;

		case isDataStore:
			setDataStore( serviceDefinition.path( attributeName ).asBoolean( false ) ) ;
			break ;

		case isMessaging:
			setMessaging( serviceDefinition.path( attributeName ).asBoolean( false ) ) ;
			break ;

		case isTomcatAjp:
			setTomcatAjp( serviceDefinition.path( attributeName ).asBoolean( false ) ) ;
			break ;

		/**
		 * Using explicit member
		 */
		case serviceType:
			configureServiceType( serviceDefinition, attributeName, resultsBuffer ) ;
			break ;

		case port:
			setPort( serviceDefinition.path( attributeName ).asText( "0" ) ) ;
			break ;

		case processFilter:
			setProcessFilter( serviceDefinition.path( attributeName ).asText( ) ) ;
			break ;

		case processChildren:
			setAddChildProcesses( serviceDefinition.path( attributeName ).asBoolean( ) ) ;
			break ;

		case startOrder:
			setAutoStart( serviceDefinition.path( attributeName ).asInt( -1 ) ) ;
			break ;

		case folderToMonitor:
			setDisk( serviceDefinition.path( attributeName ).asText( ) ) ;
			break ;

		case description:
			setDescription( serviceDefinition.path( attributeName ).asText( ) ) ;
			break ;

		case deploymentNotes:
			setDeploymentNotes( serviceDefinition.path( attributeName ).asText( ) ) ;
			break ;

		case serviceUrl:
			setUrl( serviceDefinition.path( attributeName ).asText( ) ) ;
			break ;

		case documentation:
			setDocUrl( serviceDefinition.path( attributeName ).asText( ) ) ;
			break ;

		case osProcessPriority:
			setOsProcessPriority( serviceDefinition.path( attributeName ).asInt( 0 ) ) ;
			break ;

		case logFolder:
			setLogDirectory( serviceDefinition.path( attributeName ).asText( ) ) ;
			break ;

		case logDefaultFile:
			setDefaultLogToShow( serviceDefinition.path( attributeName ).asText( ) ) ;
			break ;

		case logFilter:
			setLogRegEx( serviceDefinition.path( attributeName ).asText( ) ) ;
			break ;

		case logJournalServices:
			setLogJournalServices( serviceDefinition.path( attributeName ).asText( ) ) ;
			break ;

		case applicationFolder:
			setAppDirectory( serviceDefinition.path( attributeName ).asText( ) ) ;
			break ;

		case propertyFolder:
			setPropDirectory( serviceDefinition.path( attributeName ).asText( ) ) ;
			break ;

		case libraryFolder:
			setLibDirectory( serviceDefinition.path( attributeName ).asText( ) ) ;
			break ;

		case metaData:
			setMetaData( serviceDefinition.path( attributeName ).asText( ) ) ;
			break ;

		case remoteCollections:
			break ;

		case deployFromSource:
			configureDeployFromSource( serviceDefinition, attributeName ) ;
			break ;

		case deployFromRepository:
			configureDeployFromRepo( serviceDefinition, attributeName, resultsBuffer ) ;
			break ;

		case deployTimeMinutes:
			setDeployTimeOutMinutes( serviceDefinition.path( attributeName ).asText( ) ) ;
			break ;

		case javaJmxPort:
			setJmxPort( serviceDefinition.path( attributeName ).asText( ) ) ;
			break ;

		default:
			updateServiceParseResults( resultsBuffer, CsapConstants.CONFIG_PARSE_WARN,
					getErrorHeader( ) + "Unexpected attribute: " + attribute ) ;
			break ;

		}

	}

	public ObjectNode getJobsDefinition ( )
		throws Exception {

		return getAttributeAsObject( ServiceAttributes.scheduledJobs ) ;

	}

	private void buildJobs ( StringBuilder resultsBuffer ) {

		ObjectNode jobs = getAttributeAsObject( ServiceAttributes.scheduledJobs ) ;

		if ( jobs == null ) {

			return ;

		}

		// resolveRuntimeVariables
		try {

			jobs = (ObjectNode) jacksonMapper.readTree( resolveRuntimeVariables( jobs.toString( ) ) ) ;

		} catch ( Exception e1 ) {

			logger.warn( "Failed resolving run variablses: {}", CSAP.buildCsapStack( e1 ) ) ;

		}

		logger.debug( "{} building jobs", toSummaryString( ) ) ;

		serviceJobs.clear( ) ; // if overridden - we need to override all

		var scripts = jobs.path( "scripts" ) ;

		if ( scripts.isArray( ) ) {

			CSAP.jsonStream( scripts )
					.filter( JsonNode::isObject )
					.map( scriptDefinition -> (ObjectNode) scriptDefinition )
					.forEach( scriptDefinition -> {

						try {

							logger.debug( "{} script: {}", getName( ), scriptDefinition ) ;
							ServiceJob serviceJob = jacksonMapper.readValue( scriptDefinition.toString( ),
									ServiceJob.class ) ;
							serviceJob.setScript( serviceJob.getScript( ) ) ;

							if ( serviceJob.getDescription( ).isEmpty( ) ) {

								serviceJob.setDescription( "Service Script: " + serviceJob.getScript( ) ) ;

							}

							logger.debug( "{} loaded job: {}",
									getName( ), serviceJob ) ;
							serviceJobs.add( serviceJob ) ;

						} catch ( Exception e ) {

							logger.error( "{} Failed parsing jobs: {}, reason{}",
									getName( ),
									CSAP.jsonPrint( scriptDefinition ),
									CSAP.buildCsapStack( e ) ) ;

						}

					} ) ;

		} else {

			logger.debug( "{} Warning: Expected array for job scripts, but not found", getErrorHeader( ) ) ;

			updateServiceParseResults( resultsBuffer, CsapConstants.CONFIG_PARSE_WARN,
					getErrorHeader( ) + "Expected array for job scripts" ) ;

		}

		CSAP.jsonStream( jobs.path( "diskCleanUp" ) ).forEach( diskCleanUp -> {

			try {

				logger.debug( "Job: {}", diskCleanUp ) ;
				ServiceJob serviceJob = jacksonMapper.treeToValue( diskCleanUp, ServiceJob.class ) ;

				if ( ! serviceJob.getPath( ).isEmpty( ) && serviceJob.getOlderThenDays( ) >= 0 ) {

					serviceJob.setPath( serviceJob.getPath( ) ) ;

					if ( serviceJob.getDescription( ).isEmpty( ) ) {

						serviceJob.setDescription( serviceJob.getPath( ) ) ;

					}

					serviceJob.setDiskCleanJob( true ) ;
					logger.debug( "{} loaded job: {}",
							getServiceName_Port( ), serviceJob ) ;
					serviceJobs.add( serviceJob ) ;

				}

			} catch ( Exception e ) {

				logger.error( "{} Failed parsing jobs: {}",
						getServiceName_Port( ),
						CSAP.buildCsapStack( e ) ) ;

			}

		} ) ;

		CSAP.jsonStream( jobs.path( "logRotation" ) ).forEach( logRotationConfig -> {

			try {

				logger.debug( "Job: {}", logRotationConfig ) ;
				LogRotation logRotation = jacksonMapper.treeToValue( logRotationConfig, LogRotation.class ) ;
				String logPath = logRotation.getPath( ) ;
				logPath = logPath.trim( ).replaceAll(
						Matcher.quoteReplacement( CsapConstants.CSAP_DEF_LOGS ),
						getLogWorkingDirectory( ).getAbsolutePath( ) ) ;
				logRotation.setPath( logPath ) ;
				logger.debug( "{} loaded logRotation: {}",
						getServiceName_Port( ), logRotation ) ;
				logsToRotate.add( logRotation ) ;

			} catch ( Exception e ) {

				logger.error( "{} Failed parsing jobs: {}",
						getServiceName_Port( ),
						CSAP.buildCsapStack( e ) ) ;

			}

		} ) ;

	}

	public File getLogWorkingDirectory ( ) {

		File logDir = new File( getWorkingDirectory( ), "/" + getLogDirectory( ) ) ;

		if ( getLogDirectory( )
				.startsWith( "/" ) ) {

			logDir = new File( getLogDirectory( ) ) ;

		}

		logger.debug( "{} log directory: {}", getServiceName_Port( ), logDir.getAbsolutePath( ) ) ;

		if ( is_docker_server( )
				&& ! getLogDirectory( ).startsWith( ContainerIntegration.DOCKER_LOG_HOST ) ) {

			logDir = new File( FileToken.DOCKER.value, getDockerContainerPath( ) ) ;

		} else if ( Application.isRunningOnDesktop( ) ) {

			logger.debug( "Stubbing logs on desktop" ) ;
			logDir = new File( "logs" ) ;

		}

		return logDir ;

	}

	public File getServiceJobsLogDirectory ( ) {

		File logDir = new File( getWorkingDirectory( ), "/" + getLogDirectory( ) + "/serviceJobs/" ) ;

		return logDir ;

	}

	private void attributeMissingMessage ( ServiceAttributes attribute , StringBuilder resultsBuffer ) {

		switch ( attribute ) {

		case serviceUrl:
		case port:
		case startOrder:
		case folderToMonitor:
		case osProcessPriority:
		case processChildren:

		case environmentVariables:
		case environmentOverload:
		case readme:
		case description:
		case deploymentNotes:
		case documentation:
		case parameters:
		case osAlertLimits:
		case dockerSettings:
		case runUsingDocker:

		case isDataStore:
		case isMessaging:
		case isTomcatAjp:

		case notifications:
		case performanceApplication:
		case javaAlertWarnings:
		case javaJmxPort:
		case scheduledJobs:
		case deployTimeMinutes:
		case logFolder:
		case logDefaultFile:
		case logFilter:
		case logJournalServices:
		case propertyFolder:
		case applicationFolder:
		case remoteCollections:
		case libraryFolder:
		case webServerTomcat:
		case webServerReWrite:
		case metaData:
			// optional params do not need to be present
			break ;

		case serviceType:
			updateServiceParseResults(
					resultsBuffer,
					CsapConstants.CONFIG_PARSE_ERROR,
					getErrorHeader( )
							+ " Missing required attribute: '" + attribute.json( ) + "' description: " + attribute ) ;

			break ;

		case processFilter:
			if ( is_csap_api_server( ) ) {

				updateServiceParseResults(
						resultsBuffer,
						CsapConstants.CONFIG_PARSE_WARN,
						getErrorHeader( )
								+ " Missing Attribute: '" + attribute + "' It is strongly recommended to set" ) ;

			}
			break ;

		case deployFromRepository:
		case deployFromSource:
			// allow optional deploy options
			// if ( !isDockerContainer() && !isOs() && !isRemoteCollection() ) {
			//
			// updateServiceParseResults(
			// resultsBuffer,
			// CONFIG_PARSE_WARN,
			// getErrorHeader()
			// + " Missing attribute: " + attribute.json() + " It is strongly
			// recommended to set" );
			// }
			break ;

		// only output warning messages for mandatory attributes
		default:
			updateServiceParseResults(
					resultsBuffer,
					CsapConstants.CONFIG_PARSE_WARN,
					getErrorHeader( )
							+ "Missing Attribute: " + attribute.json( ) + " description: " + attribute ) ;

		}

	}

	public String getErrorHeader ( ) {

		return " Service: " + getName( ) + "(" + packageName + ") - " ;

	}

	public static void updateServiceParseResults (
													StringBuilder resultsBuffer ,
													String messageType ,
													String messageDescription ) {

		if ( resultsBuffer != null
				&& resultsBuffer.indexOf( messageDescription ) == -1 ) {

			resultsBuffer.append( "\n" ) ;
			resultsBuffer.append( messageType ) ;
			resultsBuffer.append( messageDescription ) ;
			resultsBuffer.append( "\n" ) ;

		}

	}

	private boolean applicationHealthMeter = false ;
	private ObjectNode httpCollectionSettings = null ;

	public boolean isHttpCollectionEnabled ( ) {

		return httpCollectionSettings != null ;

	}

	public boolean isJavaCollectionEnabled ( ) {

		return isJavaOverHttpCollectionEnabled( ) || isJavaJmxCollectionEnabled( ) ;

	}

	public boolean isJavaOverHttpCollectionEnabled ( ) {

		//
		return isHttpCollectionEnabled( ) &&
				getHttpCollectionSettings( ).has( ModelJson.javaCollectionUrl.jpath( ) ) ;

	}

	public boolean is_tomcat_collect ( ) {

		// assume all java collect is tomcat
		if ( isJavaOverHttpCollectionEnabled( ) ) {

			return ! getHttpCollectionSettings( ).has( "noTomcat" ) ;

		}

		return getProcessRuntime( ).isJava( ) ;

	}

	private ObjectNode serviceMeterTitles = jacksonMapper.createObjectNode( ) ;

	public ObjectNode getServiceMeterTitles ( ) {

		return serviceMeterTitles ;

	}

	private List<ServiceMeter> serviceMeters = new ArrayList<>( ) ;

	public List<ServiceMeter> getServiceMeters ( ) {

		// lazy construction
		return serviceMeters ;

	}

	public boolean hasServiceMeters ( ) {

		return serviceMeters.size( ) > 0 ;

	}

	public boolean hasMeter ( String id ) {

		Optional<ServiceMeter> theMeter = getServiceMeters( ).stream( ).filter( meter -> meter.getCollectionId( )
				.equals( id ) ).findFirst( ) ;

		if ( theMeter.isPresent( ) ) {

			return true ;

		}

		return false ;

	}

	// public ServiceMeter getServiceMeter( String id) {
	//
	// Optional<ServiceMeter> theMeter = getServiceMeters().stream().filter(
	// meter -> meter.getCollectionId().equals( id ) ).findFirst() ;
	//
	// if ( theMeter.isPresent() ) {
	// return theMeter.get() ;
	// }
	//
	// return null;
	// }
	private void buildServiceMeters ( StringBuilder resultsBuf ) {

		ObjectNode serviceMetersDefinition = getPerformanceConfiguration( ) ;

		if ( serviceMetersDefinition.path( ModelJson.config.jpath( ) ).isObject( ) ) {

			// http collection
			var httpSettings = jacksonMapper.createObjectNode( ) ;

			// set defaults
			httpSettings.put( ModelJson.patternMatch.jpath( ), ModelJson.json.jpath( ) ) ;
			var csapMicroUrl = serviceMetersDefinition.at( ModelJson.csapMicroUrl.cpath( ) ).asText( ) ;

			if ( StringUtils.isNotEmpty( csapMicroUrl ) ) {

				httpSettings.put( ModelJson.httpCollectionUrl.jpath( ), csapMicroUrl + ModelJson.csapMicroSuffix
						.jpath( ) ) ;
				httpSettings.put( ModelJson.javaCollectionUrl.jpath( ), csapMicroUrl + ModelJson.csapMicroSuffix
						.jpath( ) ) ;
				httpSettings.put( ModelJson.healthCollectionUrl.jpath( ), csapMicroUrl + ModelJson.csapMicroSuffix
						.jpath( ) ) ;

			}

			// override with specific settings
			httpSettings.setAll( (ObjectNode) serviceMetersDefinition.get( ModelJson.config.jpath( ) ) ) ;

			boolean httpCollection = true ;

			if ( StringUtils.isEmpty( httpSettings.path( ModelJson.httpCollectionUrl.jpath( ) ).asText( ) ) ) {

				httpCollection = false ;
				updateServiceParseResults( resultsBuf, CsapConstants.CONFIG_PARSE_WARN,
						getErrorHeader( )
								+ "Invalid http configuration: Missing attribute: httpCollectionUrl" ) ;

			}

			if ( httpCollection ) {

				httpCollectionSettings = httpSettings ;

			}

		}

		performanceAttributeNames( )
				.filter( name -> isAlphaNumeric( resultsBuf, serviceMetersDefinition, name ) )
				.filter( name -> ! ModelJson.config.jpath( ).equals( name ) )
				.map( metricId -> buildServiceMeter( metricId, serviceMetersDefinition, resultsBuf ) )
				.filter( serviceMeter -> serviceMeter != null )
				.forEach( serviceMeter -> {

					serviceMeters.add( serviceMeter ) ;

					if ( serviceMeter.getCollectionId( ).equals( ServiceAlertsEnum.JAVA_HEARTBEAT ) ) {

						setApplicationHealthMeter( true ) ;

					}

				} ) ;

		//
		serviceMeters.forEach( meter -> {

			serviceMeterTitles.put( meter.getCollectionId( ), meter.getTitle( ) ) ;

		} ) ;

	}

	public void addMeter ( String id , String attribute , String title ) {

		var serviceMeter = new ServiceMeter( id, title, attribute ) ;
		serviceMeters.add( serviceMeter ) ;
		serviceMeterTitles.put( id, title ) ;

	}

	private boolean isAlphaNumeric ( StringBuilder resultsBuf , ObjectNode serviceMetersDefinition , String name ) {

		if ( ! StringUtils.isAlphanumeric( name ) ) {

			ObjectNode metricSettings = (ObjectNode) serviceMetersDefinition.get( name ) ;
			metricSettings.put( "errors", true ) ;

			updateServiceParseResults( resultsBuf, CsapConstants.CONFIG_PARSE_WARN,
					getErrorHeader( )
							+ "Invalid attribute name: " + name
							+ ", must be alphaNumeric only. Removing" ) ;

			return false ;

		}

		return true ;

	}

	private ServiceMeter buildServiceMeter (
												String metricId ,
												ObjectNode serviceMetersDefinition ,
												StringBuilder resultsBuf ) {

		ServiceMeter serviceMeter = null ;

		JsonNode metricSettings = serviceMetersDefinition.get( metricId ) ;

		if ( serviceMetersDefinition.has( ModelJson.config.jpath( ) ) ) {

			// http checks
			if ( ! metricSettings.has( "attribute" ) ) {

				updateServiceParseResults( resultsBuf, CsapConstants.CONFIG_PARSE_WARN,
						getErrorHeader( )
								+ metricId + " is missing attribute field (http collection)" ) ;

			} else {

				// ServiceMeter
				serviceMeter = new ServiceMeter( metricId, (ObjectNode) metricSettings ) ;

			}

		} else {

			// JMX checks
			if ( metricSettings.has( "mbean" ) && ! metricSettings.has( "attribute" ) ) {

				updateServiceParseResults( resultsBuf, CsapConstants.CONFIG_PARSE_WARN,
						getErrorHeader( )
								+ metricId + " is missing attribute field (java collection)" ) ;

			} else if ( metricSettings.isObject( ) ) {

				// ServiceMeter
				serviceMeter = new ServiceMeter( metricId, (ObjectNode) metricSettings ) ;

			}

		}

		if ( metricSettings.has( "divideBy" ) ) {

			if ( metricSettings.get( "divideBy" ).asText( ).equalsIgnoreCase( "interval" ) ) {

				// interval will be used
			} else {

				double d = metricSettings.get( "divideBy" ).asDouble( ) ;

				if ( d == 0 ) {

					updateServiceParseResults( resultsBuf, CsapConstants.CONFIG_PARSE_WARN,
							getErrorHeader( )
									+ metricId
									+ " Invalid divideBy attribute: " + metricSettings
									+ " Resolving to 0" ) ;

				}

			}

		}

		if ( serviceMeter != null && serviceMeter.getMbeanName( ) != null ) {

			serviceMeter.setMbeanName( resolveRuntimeVariables( serviceMeter.getMbeanName( ) ) ) ;

		}

		return serviceMeter ;

	}

	public void configureServiceType ( JsonNode definitionNode , String attributeText , StringBuilder resultsBuffer ) {

		setProcessRuntime( definitionNode.path( attributeText ).asText( ) ) ;

		if ( ! is_docker_server( ) && ! is_springboot_server( ) && ! is_java_application_server( )
				&& ! is_csap_api_server( ) && ! is_os_process_monitor( ) ) {

			updateServiceParseResults(
					resultsBuffer, CsapConstants.CONFIG_PARSE_WARN,
					getErrorHeader( ) + "Unexpected: " + attributeText + " found: "
							+ getRuntime( ) ) ;

		}

	}

	public boolean isRemoteCollection ( ) {

		// logger.info("{} , {}", getServiceName() , getAttributeAsJson(
		// ServiceAttributesEnum.remoteCollections )) ;
		if ( getAttributeAsJson( ServiceAttributes.remoteCollections ) != null ) {

			return true ;

		}

		return false ;

	}

	public boolean configureRemoteCollection ( int collectHostIndex , StringBuilder resultsBuf ) {

		JsonNode remoteCollectionsDefinition = getAttributeAsJson( ServiceAttributes.remoteCollections ) ;

		JsonNode remoteDefinition = remoteCollectionsDefinition.get( collectHostIndex ) ;

		if ( ! remoteDefinition.isObject( ) || ! remoteDefinition.has( "host" ) || ! remoteDefinition.has( "port" ) ) {

			logger.error( "Invalid configuration: {}", remoteDefinition.toString( ) ) ;
			updateServiceParseResults(
					resultsBuf, CsapConstants.CONFIG_PARSE_WARN,
					getErrorHeader( )
							+ "Invalid format for " + ServiceAttributes.remoteCollections
							+ " expected: host and port, found: " + remoteCollectionsDefinition.toString( ) ) ;
			return false ;

		}

		setCollectHost( remoteDefinition.get( "host" ).asText( ) ) ;
		setCollectPort( remoteDefinition.get( "port" ).asText( ) ) ;
		return true ;

	}

	public void configureDeployFromRepo ( JsonNode definitionNode , String attributeText , StringBuilder resultsBuf ) {

		JsonNode mavenNode = definitionNode.get( attributeText ) ;

		if ( mavenNode.has( "dependency" ) ) {

			setMavenId( mavenNode
					.path( "dependency" )
					.asText( ) ) ;

			if ( getMavenId( ).split( ":" ).length != 4 ) {

				updateServiceParseResults(
						resultsBuf, CsapConstants.CONFIG_PARSE_WARN,
						getErrorHeader( )
								+ "Invalid format for " + attributeText
								+ " expected: group:artifact:version:type, found: " + getMavenId( ) ) ;

			}

		}

		if ( mavenNode.has( "repo" ) ) {

			setMavenRepo( mavenNode
					.path( "repo" )
					.asText( ) ) ;

		}

		if ( mavenNode.has( "secondary" ) ) {

			setMavenSecondary( mavenNode.path( "secondary" ).asText( ) ) ;

		}

		if ( mavenNode.has( "enableReleaseFile" ) ) {

			setAllowReleaseFileToOverride( mavenNode.path( "enableReleaseFile" ).asBoolean( true ) ) ;

		}

	}

	public void configureDeployFromSource ( JsonNode definitionNode , String attributeText ) {

		JsonNode sourceNode = definitionNode.get( attributeText ) ;

		if ( sourceNode.has( "path" ) ) {

			setScmLocation( sourceNode.path( "path" ).asText( ) ) ;

		}

		if ( sourceNode.has( "branch" ) ) {

			setDefaultBranch( sourceNode.path( "branch" ).asText( ) ) ;

		}

		if ( sourceNode.has( "scm" ) ) {

			setScm( sourceNode.path( "scm" ).asText( ) ) ;

		}

		if ( sourceNode.has( "buildLocation" ) ) {

			setScmBuildLocation( sourceNode.path( "buildLocation" ).asText( ) ) ;

		}

	}

	public ObjectNode getHttpCollectionSettings ( ) {

		return httpCollectionSettings ;

	}

	public void setHttpCollectionSettings ( ObjectNode httpConfig ) {

		this.httpCollectionSettings = httpConfig ;

	}

	public boolean isApplicationHealthMeter ( ) {

		return applicationHealthMeter ;

	}

	public void setApplicationHealthMeter ( boolean applicationHealthMeter ) {

		this.applicationHealthMeter = applicationHealthMeter ;

	}

	public String getHealthUrl ( String podId ) {

		if ( ! isApplicationHealthEnabled( ) ) {

			return "" ;

		}

		String healthUrl = getUrl( ) ;

		if ( ! healthUrl.endsWith( "/" ) ) {

			healthUrl += "/" ;

		}

		healthUrl += MetricsPublisher.CSAP_HEALTH.substring( 1 ) ;

		if ( is_cluster_kubernetes( ) ) {

			healthUrl = CsapApis.getInstance( ).application( ).getAgentUrl( getHostName( ), "/csap/health?pod="
					+ podId ) ;

		} else if ( isHttpHealthReportEnabled( ) ) {

			var csapMicroUrl = resolveRuntimeVariables( getHttpCollectionSettings( ).path( ModelJson.csapMicroUrl
					.jpath( ) ).asText( ) ) ;

			if ( StringUtils.isNotEmpty( csapMicroUrl ) ) {

				// csap alert portal
				healthUrl = csapMicroUrl + "/csap/health" ;

			} else {

				// csap collection url
				healthUrl = resolveRuntimeVariables( getHttpCollectionSettings( ).path( ModelJson.healthCollectionUrl
						.jpath( ) ).asText( ) ) ;

				if ( healthUrl.indexOf( '?' ) == -1 && healthUrl.contains( CsapConstants.API_AGENT_URL
						+ "/health" ) ) {

					// helper for kubelet and docker health wrappers in AgentApi
					healthUrl += "?csapui=true" ;

				}

			}

		}

		return healthUrl ;

	}

	@Override
	public String toString ( ) {

		StringBuilder builder = new StringBuilder( ) ;
		builder.append( "ServiceBaseParser [" ) ;

		if ( packageName != null ) {

			builder.append( "packageName=" ) ;
			builder.append( packageName ) ;
			builder.append( ", " ) ;

		}

		if ( definitionAttributes != null ) {

			builder.append( "definitionAttributes=" ) ;
			builder.append( definitionAttributes ) ;
			builder.append( ", " ) ;

		}

		if ( logsToRotate != null ) {

			builder.append( "logsToRotate=" ) ;
			builder.append( logsToRotate ) ;
			builder.append( ", " ) ;

		}

		if ( serviceJobs != null ) {

			builder.append( "serviceJobs=" ) ;
			builder.append( serviceJobs ) ;
			builder.append( ", " ) ;

		}

		builder.append( "applicationHealthMeter=" ) ;
		builder.append( applicationHealthMeter ) ;
		builder.append( ", " ) ;

		if ( httpCollectionSettings != null ) {

			builder.append( "httpMeterCollectionConfig=" ) ;
			builder.append( httpCollectionSettings ) ;
			builder.append( ", " ) ;

		}

		if ( serviceMeterTitles != null ) {

			builder.append( "serviceMeterTitles=" ) ;
			builder.append( serviceMeterTitles ) ;
			builder.append( ", " ) ;

		}

		if ( serviceMeters != null ) {

			builder.append( "serviceMeters=" ) ;
			builder.append( serviceMeters ) ;

		}

		builder.append( "]" ) ;

		return WordUtils.wrap( builder.toString( ), 140, "\n\t\t", false ) + "\n\t" + super.toString( ) ;

	}

}
