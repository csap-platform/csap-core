package org.csap.agent.container.kubernetes ;

import java.io.IOException ;
import java.util.ArrayList ;
import java.util.Arrays ;
import java.util.HashMap ;
import java.util.List ;
import java.util.Map ;
import java.util.Optional ;
import java.util.stream.Collectors ;

import org.apache.commons.lang3.StringUtils ;
import org.csap.agent.container.C7 ;
import org.csap.agent.container.ContainerIntegration ;
import org.csap.agent.container.kubernetes.KubernetesIntegration.Propogation_Policy ;
import org.csap.agent.model.ServiceInstance ;
import org.csap.helpers.CSAP ;
import org.csap.helpers.CsapApplication ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;

import com.fasterxml.jackson.core.type.TypeReference ;
import com.fasterxml.jackson.databind.JsonNode ;
import com.fasterxml.jackson.databind.ObjectMapper ;
import com.fasterxml.jackson.databind.node.ArrayNode ;
import com.fasterxml.jackson.databind.node.ObjectNode ;
import com.google.gson.JsonSyntaxException ;

import io.kubernetes.client.custom.IntOrString ;
import io.kubernetes.client.custom.Quantity ;
import io.kubernetes.client.openapi.ApiResponse ;
import io.kubernetes.client.openapi.apis.AppsV1Api ;
import io.kubernetes.client.openapi.apis.CoreV1Api ;
import io.kubernetes.client.openapi.apis.NetworkingV1Api ;
import io.kubernetes.client.openapi.models.V1Container ;
import io.kubernetes.client.openapi.models.V1ContainerPort ;
import io.kubernetes.client.openapi.models.V1DeleteOptions ;
import io.kubernetes.client.openapi.models.V1Deployment ;
import io.kubernetes.client.openapi.models.V1DeploymentSpec ;
import io.kubernetes.client.openapi.models.V1EmptyDirVolumeSource ;
import io.kubernetes.client.openapi.models.V1EnvVar ;
import io.kubernetes.client.openapi.models.V1HTTPGetAction ;
import io.kubernetes.client.openapi.models.V1HTTPIngressPath ;
import io.kubernetes.client.openapi.models.V1HTTPIngressRuleValue ;
import io.kubernetes.client.openapi.models.V1HostPathVolumeSource ;
import io.kubernetes.client.openapi.models.V1Ingress ;
import io.kubernetes.client.openapi.models.V1IngressBackend ;
import io.kubernetes.client.openapi.models.V1IngressRule ;
import io.kubernetes.client.openapi.models.V1IngressServiceBackend ;
import io.kubernetes.client.openapi.models.V1IngressSpec ;
import io.kubernetes.client.openapi.models.V1LabelSelector ;
import io.kubernetes.client.openapi.models.V1Namespace ;
import io.kubernetes.client.openapi.models.V1ObjectMeta ;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaim ;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaimSpec ;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaimVolumeSource ;
import io.kubernetes.client.openapi.models.V1PodSecurityContext ;
import io.kubernetes.client.openapi.models.V1PodSpec ;
import io.kubernetes.client.openapi.models.V1PodTemplateSpec ;
import io.kubernetes.client.openapi.models.V1Probe ;
import io.kubernetes.client.openapi.models.V1ResourceRequirements ;
import io.kubernetes.client.openapi.models.V1Service ;
import io.kubernetes.client.openapi.models.V1ServiceBackendPort ;
import io.kubernetes.client.openapi.models.V1ServicePort ;
import io.kubernetes.client.openapi.models.V1ServiceSpec ;
import io.kubernetes.client.openapi.models.V1Status ;
import io.kubernetes.client.openapi.models.V1Volume ;
import io.kubernetes.client.openapi.models.V1VolumeMount ;
import io.micrometer.core.instrument.Timer ;

public class SpecBuilder {

	public static final String DEPLOY_TIMER = KubernetesIntegration.CSAP_KUBERNETES_METER + "deploy" ;

	Logger logger = LoggerFactory.getLogger( getClass( ) ) ;
	static final int gracePeriodSeconds = 0 ;

	public SpecBuilder (
			KubernetesIntegration kubernetesIntegration,
			MetricsBuilder kubernetesMetrics,
			ListingsBuilder listingsBuilder,
			ObjectMapper jsonMapper ) {

		this.kubernetes = kubernetesIntegration ;
		this.metrics = kubernetesMetrics ;
		this.listingsBuilder = listingsBuilder ;
		this.jsonMapper = jsonMapper ;

	}

	ObjectMapper jsonMapper ;

	KubernetesIntegration kubernetes ;
	ListingsBuilder listingsBuilder ;

	MetricsBuilder metrics ;

	public ObjectNode remove_csap_service ( ServiceInstance serviceInstance ) {

		ObjectNode result ;

		String deploymentName = ContainerIntegration.getNetworkSafeContainerName(
				serviceInstance.getDockerContainerPath( ).substring( 1 ) ) ;

		try {

			// String namespace = deploySettings.at( KubernetesJson.namespace.spath()
			// ).asText( "default" ) ;
			String namespace = serviceInstance.getKubernetesNamespace( ) ;
			result = deploymentDelete( deploymentName, namespace, true, true ) ;

		} catch ( Exception e ) {

			result = kubernetes.buildErrorResponse( "Failed removing deployment for service:"
					+ serviceInstance.getName( )
					+ "deployment: " + deploymentName,
					e ) ;

		}

		return result ;

	}

	public ObjectNode deploy_csap_service ( ServiceInstance service , JsonNode deploySettings ) {

		ObjectNode result = jsonMapper.createObjectNode( ) ;

		logger.info( "deploySettings: {}", CSAP.jsonPrint( deploySettings ) ) ;

		try {

			// String kubernetesDeployName = deploySettings.path(
			// DockerJson.containerName.json() ).asText( "$instance" ) ;
			String kubernetesDeployName = service.getDockerContainerName( ) ;

			kubernetesDeployName = ContainerIntegration.getNetworkSafeContainerName(
					service.resolveRuntimeVariables( kubernetesDeployName ) ) ;

			String imageName = deploySettings
					.path( C7.imageName.val( ) )
					.asText( ContainerIntegration.DOCKER_DEFAULT_IMAGE ) ;

			// String namespace = deploySettings.at( KubernetesJson.namespace.spath()
			// ).asText( "default" ) ;
			String namespace = service.getKubernetesNamespace( ) ;

			int replicaCount = deploySettings.at( K8.replicaCount.spath( ) ).asInt( 1 ) ;

			String serviceType = deploySettings.at( K8.serviceType.spath( ) )
					.asText( K8.None.val( ) ) ;

			// String portDefinition = deploySettings.path( DockerJson.portMappings.json()
			// ).asText( "" );
			String portDefinition = jsonAsString( service, deploySettings.get( C7.portMappings.val( ) ),
					"" ) ;

			String nodeSelectors = jsonAsString( service, deploySettings.at( K8.nodeSelectors.spath( ) ),
					"" ) ;
			String podAnnotations = jsonAsString( service, deploySettings.at( K8.podAnnotations.spath( ) ),
					"" ) ;
			String labelsByType = jsonAsString( service, deploySettings.at( K8.labelsByType.spath( ) ),
					"" ) ;
			String readinessProbe = jsonAsString( service, deploySettings.at( K8.readinessProbe.spath( ) ),
					"" ) ;
			String livenessProbe = jsonAsString( service, deploySettings.at( K8.livenessProbe.spath( ) ),
					"" ) ;

			String resources = jsonAsString( service, deploySettings.at( K8.resources.spath( ) ), "" ) ;

			String ingressPath = load( service, deploySettings, K8.ingressPath.spath( ) ) ;

			String ingressPort = load( service, deploySettings, K8.ingressPort.spath( ) ) ;

			String ingressHost = deploySettings.at( K8.ingressHost.spath( ) ).asText( "" ) ;

			String ingressAnnotationsDef = jsonAsString( service, deploySettings.at( K8.ingressAnnotations
					.spath( ) ), "" ) ;

			String k8Command = jsonAsString( service, deploySettings.get( C7.entryPoint.val( ) ), "[]" ) ;
			String command = jsonAsString( service, deploySettings.get( C7.command.val( ) ), "" ) ;

			if ( StringUtils.isNotEmpty( command ) ) {

				logger.debug( "Using docker command array as the k8s command" ) ;
				ArrayNode commandArray = loadArray( command ) ;

				if ( commandArray.size( ) > 0 ) {

					k8Command = command ;

				}

			}

			String arguments = jsonAsString( service, deploySettings.get( K8.arguments.spath( ) ), "[]" ) ;

			String workingDirectory = load( service, deploySettings, C7.workingDirectory.jpath( ) ) ;

			String network = deploySettings.path( C7.network.val( ) ).asText( "" ) ;

			String restartPolicy = deploySettings.path( C7.restartPolicy.val( ) ).asText( "" ) ;

			String runUser = deploySettings.path( C7.runUser.val( ) ).asText( "" ) ;

			String volumes = jsonAsString( service, deploySettings.get( C7.volumes.val( ) ), "" ) ;

			String environmentVariables = jsonAsString( service, deploySettings.get( C7.environmentVariables
					.val( ) ), "" ) ;

			String limits = jsonAsString( service, deploySettings.get( C7.limits.val( ) ), "" ) ;

			boolean addCsapTools = deploySettings.at( K8.addCsapTools.spath( ) ).asBoolean( false ) ;

			result = deploymentCreate( kubernetesDeployName, namespace, imageName,
					nodeSelectors, podAnnotations, labelsByType,
					resources,
					readinessProbe, livenessProbe,
					replicaCount, serviceType, ingressPath, ingressPort, ingressHost, ingressAnnotationsDef,
					k8Command, arguments, workingDirectory, network, restartPolicy, runUser,
					portDefinition, volumes, environmentVariables, limits, addCsapTools ) ;

		} catch ( Exception e ) {

			result = kubernetes.buildErrorResponse( "Failed creating deployment for service:" + service.getName( ),
					e ) ;

		}

		return result ;

	}

	private String load ( ServiceInstance instance , JsonNode definition , String jsonPath ) {

		String value = definition.at( jsonPath ).asText( "" ) ;
		return instance.resolveRuntimeVariables( value ) ;

	}

	private String jsonAsString ( ServiceInstance instance , JsonNode found , String defaultIfNull ) {

		if ( found == null )
			return defaultIfNull ;

		return instance.resolveRuntimeVariables( found.toString( ) ) ;

	}

	public ObjectNode deploymentCreate (
											String deploymentName ,
											String namespace ,
											String imageName ,
											String nodeSelectors ,
											String podAnnotations ,
											String labelsByType ,
											String resources ,
											String readinessProbe ,
											String livenessProbe ,
											int replicaCount ,
											String serviceType ,
											String ingressPath ,
											String ingressPort ,
											String ingressHost ,
											String ingressAnnotationsDef ,
											String commands ,
											String arguments ,
											String workingDirectory ,
											String network ,
											String restartPolicy ,
											String runUser ,
											String portDefinition ,
											String volumes ,
											String environmentVariables ,
											String limits ,
											boolean addCsapTools ) {

		Timer.Sample deployTimer = kubernetes.metrics( ).startTimer( ) ;

		StringBuilder builder = new StringBuilder( ) ;
		builder.append( "@deploymentCreate: " ) ;
		builder.append( CSAP.padLine( "imageName" ) + imageName ) ;
		builder.append( CSAP.padLine( "deploymentName" ) + deploymentName ) ;
		builder.append( CSAP.padLine( "namespace" ) + namespace ) ;
		builder.append( CSAP.padLine( "replicaCount" ) + replicaCount ) ;
		builder.append( CSAP.padLine( "commands" ) + commands ) ;
		builder.append( CSAP.padLine( "arguments" ) + arguments ) ;
		builder.append( CSAP.padLine( "environmentVariables" ) + environmentVariables ) ;
		builder.append( CSAP.padLine( "portDefinition" ) + portDefinition ) ;
		builder.append( CSAP.padLine( "volumes" ) + volumes ) ;
		builder.append( CSAP.padLine( "nodeSelectors" ) + nodeSelectors ) ;
		builder.append( CSAP.padLine( "readinessProbe" ) + readinessProbe ) ;
		builder.append( CSAP.padLine( "livenessProbe" ) + livenessProbe ) ;
		builder.append( CSAP.padLine( "serviceType" ) + serviceType ) ;
		builder.append( CSAP.padLine( "ingressPath" ) + ingressPath ) ;
		builder.append( CSAP.padLine( "ingressPort" ) + ingressPort ) ;
		builder.append( CSAP.padLine( "ingressHost" ) + ingressHost ) ;
		builder.append( CSAP.padLine( "ingressAnnotationsDef" ) + ingressAnnotationsDef ) ;

		logger.info( CsapApplication.header( builder.toString( ) ) ) ;

		if ( StringUtils.isEmpty( deploymentName ) ) {

			return kubernetes.buildErrorResponse( "deployment name not specified", null ) ;

		}

		ObjectNode result ;

		try {

			if ( ! deploymentName.toLowerCase( ).equals( deploymentName ) ) {

				result = kubernetes.buildErrorResponse( "Invalid name: must be lower case: " + deploymentName, null ) ;

			} else {

				ArrayNode portArray = loadArray( portDefinition ) ;

				var envVars = loadArray( environmentVariables ) ;
				result = deployment_create(
						deploymentName, namespace,
						imageName,
						nodeSelectors, podAnnotations, labelsByType,
						resources,
						readinessProbe, livenessProbe,
						volumes, replicaCount,
						workingDirectory,
						loadArray( commands ),
						loadArray( arguments ),
						portArray,
						envVars,
						addCsapTools ) ;

				if ( ! serviceType.equals( K8.None.val( ) ) ) {

					String serviceName = deploymentName + "-service" ;
					result.set( "create-service",
							serviceCreate(
									serviceType,
									deploymentName,
									serviceName,
									ingressPath,
									namespace,
									portArray,
									labelsByType ) ) ;

					if ( StringUtils.isNotBlank( ingressPath ) &&
							serviceType.equals( K8.NODE_PORT.val( ) ) ) {

						String ingressName = deploymentName + "-ingress" ;
						ObjectNode ingressResults = ingressCreate(
								ingressName,
								ingressPath,
								ingressPort,
								ingressHost,
								ingressAnnotationsDef,
								namespace,
								serviceName,
								envVars,
								labelsByType ) ;
						result.set( "create-ingress", ingressResults ) ;

					} else {

						result.put( "ingress-info",
								"skipped creation: service type must be NodePort, and ingress path not blank " ) ;

					}

				} else {

					result.put( "service-info", "skipped creation: specify service type: " + K8
							.k8TypeList( ) ) ;

				}

			}

		} catch ( Exception e ) {

			result = kubernetes.buildErrorResponse( "Failed to create deployment", e ) ;

		}

		kubernetes.metrics( ).stopTimer( deployTimer, DEPLOY_TIMER ) ;

		return result ;

	}

	@SuppressWarnings ( "unchecked" )
	private ObjectNode ingressCreate (
										String ingressName ,
										String ingressPath ,
										String ingressPort ,
										String ingressHost ,
										String ingressAnnotationsDef ,
										String namespace ,
										String serviceName ,
										ArrayNode environmentVariables ,
										String labelsByType )
		throws Exception {

		logger.debug( "ingressName: {}  ingressAnnotationsDef: {}", ingressName, ingressAnnotationsDef ) ;

		var ingressCreateRequest = new V1Ingress( ) ;

		V1ObjectMeta ingressMetaData = buildMetaData( ingressName, namespace ) ;

		Map<String, String> labels = new HashMap<>( ) ;
		labels.put( "csap-ingress", ingressName ) ;

		if ( StringUtils.isNoneEmpty( labelsByType ) ) {

			try {

				JsonNode labelDef = jsonMapper.readTree( labelsByType ) ;

				if ( labelDef.isObject( ) && labelDef.path( "service" ).isObject( ) ) {

					Map<String, String> customerLabels = jsonMapper.convertValue( labelDef.path( "service" ),
							Map.class ) ;
					labels.putAll( customerLabels ) ;

				}

			} catch ( Exception e ) {

				logger.info( "{} warn invalid labelsByType: {}", serviceName, labelsByType ) ;

			}

		}

		ingressMetaData.setLabels( labels ) ;

		// deployMetaData.setLabels( deploymentSelector );

		Map<String, String> annotations = new HashMap<String, String>( ) ;
		annotations.put( "nginx.ingress.kubernetes.io/affinity", "cookie" ) ;
		annotations.put( "nginx.ingress.kubernetes.io/use-regex", "true" ) ; // support host wild cards
		annotations.put( "nginx.ingress.kubernetes.io/session-cookie-name", "k8_route" ) ;
		annotations.put( "nginx.ingress.kubernetes.io/session-cookie-hash", "sha1" ) ;
		annotations.put( "nginx.ingress.kubernetes.io/session-cookie-path", ingressPath ) ;

		if ( StringUtils.isNotEmpty( ingressAnnotationsDef ) ) {

			JsonNode ingressAnnotations = jsonMapper.readTree( ingressAnnotationsDef ) ;

			if ( ingressAnnotations.isObject( ) ) {

				annotations = jsonMapper.convertValue( ingressAnnotations, Map.class ) ;

			}

		}

		ingressMetaData.setAnnotations( annotations ) ;
		ingressCreateRequest.setMetadata( ingressMetaData ) ;

		var ingressSpec = new V1IngressSpec( ) ;
		ingressCreateRequest.setSpec( ingressSpec ) ;

		// List<ExtensionsV1beta1IngressRule> rules = new ArrayList<>() ;

		var ingress_rule = new V1IngressRule( ) ;

		var httpRouting = new V1HTTPIngressRuleValue( ) ;

		// List<ExtensionsV1beta1HTTPIngressPath> ingressPaths = new ArrayList<>() ;

		var backend = new V1IngressBackend( ) ;
		var beService = new V1IngressServiceBackend( ) ;
		beService.setName( serviceName ) ;

		if ( StringUtils.isEmpty( ingressPort ) ) {

			ingressPort = "80" ;

		}
		
		var sbePort = new  V1ServiceBackendPort( );
		sbePort.setNumber( Integer.parseInt( ingressPort ) ) ;
		beService.setPort( sbePort ) ;
		
		backend.setService( beService ) ;
	

		var httpIngressPath = new V1HTTPIngressPath( ) ;
		httpIngressPath.backend( backend ) ;
		httpIngressPath.setPath( ingressPath ) ;
		// httpIngressPath.setPathType( "Prefix" );
		httpIngressPath.setPathType( "ImplementationSpecific" ) ;

		httpRouting.setPaths( Arrays.asList( httpIngressPath ) ) ;
		ingress_rule.setHttp( httpRouting ) ;

		if ( StringUtils.isEmpty( ingressHost ) ) {

			// ingressHost = "*." + domainName() ;
			Optional<String[]> ingressVariable = findIngressHostFromEnv( environmentVariables ) ;

			if ( ingressVariable.isPresent( ) ) {

				ingressHost = ingressVariable.get( )[1] ;

			}

		}

		ingress_rule.setHost( ingressHost ) ;
		// rules.add( rule ) ;
		ingressSpec.setRules( Arrays.asList( ingress_rule ) ) ;
		// ingressSpec.setBackend( backend );

		logger.info( "ingressCreateRequest: {} ", ingressCreateRequest.toString( ) ) ;

		ObjectNode result ;

		try {

			var networkApi = new NetworkingV1Api( kubernetes.apiClient( ) ) ;

			String dryRun = null ;
			String pretty = null ;
			String fieldManager = null ;

			var ingressResponse = networkApi.createNamespacedIngress(
					namespace, ingressCreateRequest,
					pretty, dryRun, fieldManager ) ;

			result = (ObjectNode) listingsBuilder.serializeToJson( ingressResponse ) ;
			logger.info( "ingressResponse: {} ", result ) ;

		} catch ( Exception e ) {

			result = kubernetes.buildErrorResponse( "Failed to create ingress", e ) ;

		}

		return result ;

	}

	private Optional<String[]> findIngressHostFromEnv ( ArrayNode environmentVariables ) {

		Optional<String[]> ingressVariable = CSAP.jsonStream( environmentVariables )
				.map( JsonNode::asText )
				.map( nameVal -> nameVal.split( "=", 2 ) )
				.filter( nameValArray -> nameValArray.length == 2 )
				.filter( nameValArray -> nameValArray[0].equals( KubernetesIntegration.CSAP_DEF_INGRESS_HOST ) )
				.findFirst( ) ;
		return ingressVariable ;

	}

	private ArrayNode loadArray ( String definition ) {

		ArrayNode result ;

		if ( StringUtils.isEmpty( definition ) ) {

			result = jsonMapper.createArrayNode( ) ;

		} else {

			try {

				result = (ArrayNode) jsonMapper.readTree( definition ) ;

			} catch ( Exception e ) {

				result = jsonMapper.createArrayNode( ) ;
				logger.warn( "Failed to parse: {}, {}",
						definition, CSAP.buildCsapStack( e ) ) ;

			}

		}

		return result ;

	}

	private ObjectNode deployment_create (
											String deploymentName ,
											String namespace ,
											String imageName ,
											String nodeSelectors ,
											String podAnnotations ,
											String labelsByType ,
											String resources ,
											String readinessProbeDef ,
											String livenessProbeDef ,

											String volumes ,
											int replicaCount ,

											String workingDir ,
											ArrayNode commands ,
											ArrayNode arguments ,
											ArrayNode ports ,
											ArrayNode environmentVariables ,

											boolean addCsapTools )
		throws Exception {

		ObjectNode result = jsonMapper.createObjectNode( ) ;

		if ( ! kubernetes.nameSpaces( ).contains( namespace ) ) {

			deploymentAddNamespace( namespace, result ) ;

		}

		AppsV1Api apiBeta = new AppsV1Api( kubernetes.apiClient( ) ) ;

		V1Deployment deployBody = new V1Deployment( ) ;

		Map<String, String> matchLabels = labelToLinkDeployToPodSpec( deploymentName ) ;

		Map<String, String> deploymentLabels = new HashMap( matchLabels ) ;

		// deployBody.setKind( "Deployment" );
		logger.debug( "labelsByType: {}", labelsByType ) ;

		if ( StringUtils.isNoneEmpty( labelsByType ) ) {

			JsonNode labelDef = jsonMapper.readTree( labelsByType ) ;

			if ( labelDef.isObject( ) && labelDef.path( "deployment" ).isObject( ) ) {

				Map<String, String> labels = jsonMapper.convertValue( labelDef.path( "deployment" ), Map.class ) ;
				deploymentLabels.putAll( labels ) ;

			}

		}

		V1ObjectMeta deployMetaData = buildMetaData( deploymentName, namespace ) ;
		deployBody.setMetadata( deployMetaData ) ;
		deployMetaData.setLabels( deploymentLabels ) ;
		deployMetaData.setGeneration( 1L ) ;
		// deployMetaData.setClusterName( deploymentName + "-cluster");

		// metadata.setClusterName( TEST_POD_NAME + "-cluster" );

		V1DeploymentSpec deploySpec = new V1DeploymentSpec( ) ;
		deployBody.setSpec( deploySpec ) ;
		deploySpec.setReplicas( replicaCount ) ;

		V1LabelSelector selector = new V1LabelSelector( ) ;
		selector.setMatchLabels( matchLabels ) ;
		deploySpec.setSelector( selector ) ;

		// V1beta2DeploymentStrategy strategy = new V1beta2DeploymentStrategy();
		// V1beta2RollingUpdateDeployment rollingUpdate = new
		// V1beta2RollingUpdateDeployment();
		// rollingUpdate.setMaxSurge( new IntOrString( 1 ));
		// rollingUpdate.setMaxUnavailable( new IntOrString( 1 ));
		// strategy.setRollingUpdate( rollingUpdate );
		// strategy.setType( "RollingUpdate" );
		// deploySpec.setStrategy( strategy );

		// deploySpec.

		V1PodTemplateSpec podTemplate = new V1PodTemplateSpec( ) ;
		V1ObjectMeta podMetaData = new V1ObjectMeta( ) ;
		podMetaData.setName( deploymentName + "-podTemplate" ) ;

		Map<String, String> podLabels = new HashMap( matchLabels ) ;

		if ( StringUtils.isNoneEmpty( labelsByType ) ) {

			JsonNode labelDef = jsonMapper.readTree( labelsByType ) ;

			if ( labelDef.isObject( ) && labelDef.path( "pod" ).isObject( ) ) {

				Map<String, String> labels = jsonMapper.convertValue( labelDef.path( "pod" ), Map.class ) ;
				podLabels.putAll( labels ) ;

			}

		}

		podMetaData.setLabels( podLabels ) ;

		if ( StringUtils.isNotEmpty( podAnnotations ) ) {

			JsonNode annotationDef = jsonMapper.readTree( podAnnotations ) ;

			if ( annotationDef.isObject( ) ) {

				Map<String, String> annotations = jsonMapper.convertValue( annotationDef, Map.class ) ;
				podMetaData.setAnnotations( annotations ) ;

			}

		}

		podTemplate.setMetadata( podMetaData ) ;

		deploySpec.template( podTemplate ) ;

		V1PodSpec podSpec = buildPodSpec( //
				deploymentName + C7.k8ContainerSuffix.val( ),
				imageName,
				workingDir,
				commands, arguments,
				ports, environmentVariables, addCsapTools ) ;

		addDeploymentOptions( nodeSelectors, resources, readinessProbeDef, livenessProbeDef, podSpec ) ;

		if ( StringUtils.isNotEmpty( volumes ) ) {

			result.set( "create-volume", addDeploymentVolumes( podSpec, volumes, namespace ) ) ;

		}

		podTemplate.setSpec( podSpec ) ;

		logger.debug( "deploying: {}", deployBody.toString( ) ) ;

		String dryRun = null ;
		String pretty = null ;
		String fieldManager = null ;

		V1Deployment deployResult = apiBeta.createNamespacedDeployment(
				namespace,
				deployBody,
				pretty, dryRun, fieldManager ) ;

		result.set( "create-deployment", (ObjectNode) listingsBuilder.serializeToJson( deployResult ) ) ;
		logger.info( "deployResult: {} ", CSAP.jsonPrint( result ) ) ;

		return result ;

	}

	private void deploymentAddNamespace ( String namespace , ObjectNode result )
		throws Exception {

		try {

			V1Namespace body = new V1Namespace( ) ;
			V1ObjectMeta metadata = new V1ObjectMeta( ) ;
			metadata.setName( namespace ) ;
			body.setMetadata( metadata ) ;

			CoreV1Api apiV1 = new CoreV1Api( kubernetes.apiClient( ) ) ;

			String dryRun = null ;
			String pretty = null ;
			String fieldManager = null ;

			V1Namespace nameSpaceResult = apiV1.createNamespace( body, pretty, dryRun, fieldManager ) ;

			var nsResult = listingsBuilder.serializeToJson( nameSpaceResult ) ;

			logger.info( "result: {} ", nsResult ) ;

			result.set( "create-namespace", nsResult ) ;

		} catch ( Exception e ) {

			logger.error( "Failed to add namespace: {}",
					CSAP.buildCsapStack( e ), e ) ;
			throw e ;

		}

	}

	private void addDeploymentOptions (
										String nodeSelectors ,
										String resourceDefString ,
										String readinessProbeDef ,
										String livenessProbeDef ,
										V1PodSpec podSpec )
		throws IOException {

		if ( StringUtils.isNotEmpty( nodeSelectors ) ) {

			JsonNode nodeSelectorDef = jsonMapper.readTree( nodeSelectors ) ;

			if ( nodeSelectorDef.isObject( ) ) {

				Map<String, String> nodeSelector = jsonMapper.convertValue( nodeSelectorDef, Map.class ) ;
				// nodeSelector.put( "kubernetes.io/hostname", "csap-dev04.yourcompany.org" ) ;
				podSpec.setNodeSelector( nodeSelector ) ;

			}

		}

		if ( StringUtils.isNotEmpty( resourceDefString ) ) {

			JsonNode resourceDef = jsonMapper.readTree( resourceDefString ) ;

			if ( resourceDef.isObject( ) ) {

				V1Container targetContainer = podSpec.getContainers( ).get( 0 ) ;
				V1ResourceRequirements resources = new V1ResourceRequirements( ) ;

				var requests = resourceDef.path( "requests" ) ;

				if ( requests.isObject( ) ) {

					requests.fieldNames( ).forEachRemaining( fieldName -> {

						resources.putRequestsItem( fieldName, Quantity.fromString( requests.path( fieldName )
								.asText( ) ) ) ;

					} ) ;

				}

				var limits = resourceDef.path( "limits" ) ;

				if ( limits.isObject( ) ) {

					limits.fieldNames( ).forEachRemaining( fieldName -> {

						resources.putLimitsItem( fieldName, Quantity.fromString( limits.path( fieldName )
								.asText( ) ) ) ;

					} ) ;

				}

				// resources.putRequestsItem( KubernetesJson.cpu.json(), Quantity.fromString(
				// "500m" ) ) ;
				// resources.putRequestsItem( "memory", Quantity.fromString( "1G" ) ) ;
				// resources.putLimitsItem( KubernetesJson.cpu.json(), Quantity.fromString( "2"
				// ) ) ;
				// resources.putLimitsItem( "memory", Quantity.fromString( "3G" ) ) ;
				targetContainer.setResources( resources ) ;

			}

		}

		if ( StringUtils.isNotEmpty( livenessProbeDef ) ) {

			// https://kubernetes.io/docs/tasks/configure-pod-container/configure-liveness-readiness-probes/#configure-probes
			V1Container targetContainer = podSpec.getContainers( ).get( 0 ) ;
			JsonNode definition = jsonMapper.readTree( livenessProbeDef ) ;
			V1Probe livenessProbe = new V1Probe( ) ;
			livenessProbe.setFailureThreshold( definition.path( "failureThreshold" ).asInt( 5 ) ) ;
			livenessProbe.setInitialDelaySeconds( definition.path( "initialDelaySeconds" ).asInt( 10 ) ) ;
			livenessProbe.setPeriodSeconds( definition.path( "periodSeconds" ).asInt( 30 ) ) ;
			livenessProbe.setTimeoutSeconds( definition.path( "timeoutSeconds" ).asInt( 1 ) ) ;

			V1HTTPGetAction httpGet = new V1HTTPGetAction( ) ;
			httpGet.setPath( definition.path( "http-path" ).asText( "/" ) ) ;
			httpGet.setPort(
					new IntOrString( definition.path( "http-port" ).asInt( targetContainer.getPorts( ).get( 0 )
							.getContainerPort( ) ) ) ) ;
			livenessProbe.setHttpGet( httpGet ) ;

			targetContainer.setLivenessProbe( livenessProbe ) ;

		}

		if ( StringUtils.isNotEmpty( readinessProbeDef ) ) {

			// https://kubernetes.io/docs/tasks/configure-pod-container/configure-liveness-readiness-probes/#configure-probes
			V1Container targetContainer = podSpec.getContainers( ).get( 0 ) ;
			JsonNode definition = jsonMapper.readTree( readinessProbeDef ) ;
			V1Probe livenessProbe = new V1Probe( ) ;
			livenessProbe.setFailureThreshold( definition.path( "failureThreshold" ).asInt( 5 ) ) ;
			livenessProbe.setInitialDelaySeconds( definition.path( "initialDelaySeconds" ).asInt( 10 ) ) ;
			livenessProbe.setPeriodSeconds( definition.path( "periodSeconds" ).asInt( 30 ) ) ;
			livenessProbe.setTimeoutSeconds( definition.path( "timeoutSeconds" ).asInt( 1 ) ) ;

			V1HTTPGetAction httpGet = new V1HTTPGetAction( ) ;
			httpGet.setPath( definition.path( "http-path" ).asText( "/" ) ) ;
			httpGet.setPort(
					new IntOrString( definition.path( "http-port" ).asInt( targetContainer.getPorts( ).get( 0 )
							.getContainerPort( ) ) ) ) ;
			livenessProbe.setHttpGet( httpGet ) ;

			targetContainer.setReadinessProbe( livenessProbe ) ;

		}

		// V1Probe readinessProbe = new V1Probe() ;
		// readinessProbe.setInitialDelaySeconds( 10 );
		// readinessProbe.setPeriodSeconds( 30 );
		// readinessProbe.setTimeoutSeconds( 10 );
		// readinessProbe.setHttpGet( httpGet );
	}

	private ObjectNode addDeploymentVolumes ( V1PodSpec podSpec , String volumeDefinitionText , String namespace )
		throws Exception {

		ObjectNode result = jsonMapper.createObjectNode( ) ;

		List<V1Volume> volumes = new ArrayList<>( ) ;
		podSpec.setVolumes( volumes ) ;

		JsonNode volumesDefinition = jsonMapper.readTree( volumeDefinitionText ) ;

		if ( volumesDefinition.isArray( ) ) {

			List<V1VolumeMount> volumeMounts = new ArrayList<>( ) ;

			V1Container container = podSpec.getContainers( ).get( 0 ) ;
			container.setVolumeMounts( volumeMounts ) ;

			CSAP.jsonStream( volumesDefinition ).forEach( volumeDef -> {

				JsonNode hostPath_definition = volumeDef.path( K8.hostPath.val( ) ) ;
				JsonNode emptyDir_definition = volumeDef.path( K8.emptyDir.val( ) ) ;
				JsonNode pvc_definition = volumeDef.path( K8.persistentVolumeClaim.val( ) ) ;

				V1Volume volume = null ;

				if ( hostPath_definition.isObject( ) ) {

					volume = new V1Volume( ) ;

					V1HostPathVolumeSource source = new V1HostPathVolumeSource( ) ;
					volume.setHostPath( source ) ;
					source.setType( hostPath_definition.path( K8.storageType.val( ) ).asText( ) ) ;
					source.setPath( hostPath_definition.path( K8.storagePath.val( ) ).asText( ) ) ;

				} else if ( emptyDir_definition.isObject( ) ) {

					volume = new V1Volume( ) ;
					V1EmptyDirVolumeSource source = new V1EmptyDirVolumeSource( ) ;
					var sizeLimit = emptyDir_definition.path( K8.sizeLimit.val( ) ).asText( ) ;
					source.setSizeLimit( Quantity.fromString( sizeLimit ) ) ;
					volume.setEmptyDir( source ) ;

				} else if ( pvc_definition.isObject( ) ) {

					volume = new V1Volume( ) ;

					V1PersistentVolumeClaimVolumeSource persistentVolumeClaim = new V1PersistentVolumeClaimVolumeSource( ) ;
					volume.setPersistentVolumeClaim( persistentVolumeClaim ) ;
					persistentVolumeClaim.setClaimName( pvc_definition.path( K8.claimName.val( ) )
							.asText( ) ) ;
					persistentVolumeClaim.setReadOnly( volumeDef.path( K8.storageName.val( ) ).asBoolean(
							false ) ) ;

					if ( pvc_definition.path( K8.createIfNotPresent.val( ) ).asBoolean( false ) ) {

						result.set( "create-persistent-claim", createPersistVolumeClaim( pvc_definition, namespace ) ) ;

					}

				}

				if ( volume != null ) {

					volumes.add( volume ) ;
					volume.setName( volumeDef.path( K8.storageName.val( ) ).asText( ) ) ;

					// add to container
					V1VolumeMount hostMount = new V1VolumeMount( ) ;
					volumeMounts.add( hostMount ) ;
					hostMount.setMountPath( volumeDef.path( K8.mountPath.val( ) ).asText( ) ) ;
					hostMount.setName( volumeDef.path( K8.storageName.val( ) ).asText( ) ) ;
					hostMount.setReadOnly( volumeDef.path( K8.storageName.val( ) ).asBoolean( false ) ) ;

				} else {

					logger.warn( "Failed to find supported volume type" ) ;

				}

			} ) ;

		}

		return result ;

	}

	private ObjectNode createPersistVolumeClaim ( JsonNode pvcDefinition , String namespace ) {

		V1PersistentVolumeClaim pvc = new V1PersistentVolumeClaim( ) ;

		V1ObjectMeta metadata = new V1ObjectMeta( ) ;
		pvc.setMetadata( metadata ) ;

		metadata.setName( pvcDefinition.path( K8.claimName.val( ) ).asText( ) ) ;
		Map<String, String> annotations = new HashMap<>( ) ;

		var storageClass = pvcDefinition.path( K8.storageClass.val( ) ) ;

		if ( storageClass.isValueNode( ) ) {

			annotations.put( "volume.beta.kubernetes.io/storage-class", storageClass.asText( "" ) ) ;

		}

		metadata.setAnnotations( annotations ) ;

		V1PersistentVolumeClaimSpec pvcSpec = new V1PersistentVolumeClaimSpec( ) ;

		JsonNode modes = pvcDefinition.path( K8.accessModes.val( ) ) ;

		if ( modes.isArray( ) ) {

			List<String> accessModes = new ArrayList<>( ) ;
			CSAP.jsonStream( modes ).forEach( mode -> {

				accessModes.add( mode.asText( ) ) ; // ReadWriteOnce ReadWriteMany

			} ) ;
			pvcSpec.setAccessModes( accessModes ) ;

		}

		JsonNode storage = pvcDefinition.path( K8.storage.val( ) ) ;

		if ( ! storage.isMissingNode( ) ) {

			V1ResourceRequirements resources = new V1ResourceRequirements( ) ;
			Map<String, Quantity> requests = new HashMap<>( ) ;
			Quantity value = new Quantity( storage.asText( ) ) ; // 100Mi, 10Gi
			requests.put( "storage", value ) ;
			resources.setRequests( requests ) ;
			pvcSpec.setResources( resources ) ;

		}

		var selectorLabels = pvcDefinition.path( K8.selectorMatchLabels.val( ) ) ;

		if ( selectorLabels.isObject( ) ) {

			try {

				Map<String, String> matchLabels = jsonMapper.readValue(
						selectorLabels.traverse( ),
						new TypeReference<Map<String, String>>( ) {
						} ) ;

				V1LabelSelector selector = new V1LabelSelector( ) ;
				selector.setMatchLabels( matchLabels ) ;
				pvcSpec.setSelector( selector ) ;

			} catch ( IOException e ) {

				logger.warn( "Failed parsing {} {} ", selectorLabels, CSAP.buildCsapStack( e ) ) ;

			}

		}

		pvc.setSpec( pvcSpec ) ;

		CoreV1Api api = new CoreV1Api( kubernetes.apiClient( ) ) ;

		ObjectNode pvcCreateResult = jsonMapper.createObjectNode( ) ;

		try {

			String dryRun = null ;
			String pretty = null ;
			String fieldManager = null ;

			V1PersistentVolumeClaim volumeClaimResult = api.createNamespacedPersistentVolumeClaim(
					namespace,
					pvc,
					pretty, dryRun, fieldManager ) ;

			pvcCreateResult = (ObjectNode) listingsBuilder.serializeToJson( volumeClaimResult ) ;
			logger.info( "volumeClaimCreateResult: {} ", pvcCreateResult ) ;

		} catch ( Exception e ) {

			pvcCreateResult = kubernetes.buildErrorResponse( "Failed to create persistent claim", e ) ;

		}

		logger.info( "volumeClaimResult: {} ", CSAP.jsonPrint( pvcCreateResult ) ) ;

		return pvcCreateResult ;

	}

	private Map<String, String> labelToLinkDeployToPodSpec ( String runTarget ) {

		Map<String, String> deploymentSelector = new HashMap<>( ) ;
		deploymentSelector.put( "csap-deploy", runTarget ) ;
		return deploymentSelector ;

	}

	private V1ObjectMeta buildMetaData ( String itemName , String namespace ) {

		V1ObjectMeta deployMetaData = new V1ObjectMeta( ) ;
		deployMetaData.setName( itemName ) ;
		deployMetaData.setNamespace( namespace ) ;
		return deployMetaData ;

	}

	private V1PodSpec buildPodSpec (
										String containerName ,
										String imageName ,
										String workingDir ,
										ArrayNode commands ,
										ArrayNode arguments ,
										ArrayNode ports ,
										ArrayNode environmentVariables ,
										boolean addCsapTools ) {

		V1PodSpec podSpec = new V1PodSpec( ) ;

		V1PodSecurityContext securityContext = new V1PodSecurityContext( ) ;
		podSpec.setSecurityContext( securityContext ) ;

		V1Container requestedContainer = new V1Container( ) ;

		List<V1Container> containers = new ArrayList<>( ) ;
		containers.add( requestedContainer ) ;

		if ( addCsapTools ) {

			V1Container csapToolsContainer = new V1Container( ) ;
			containers.add( csapToolsContainer ) ;
			csapToolsContainer.name( "csap-tools-container" ) ;
			csapToolsContainer.image( "csap/csap-tools:2.0.8" ) ;
			csapToolsContainer.command( Arrays.asList( "/bin/bash", "-c", "echo sleeping; sleep 1000" ) ) ;

		}

		podSpec.containers( containers ) ;

		requestedContainer.name( containerName ) ;
		requestedContainer.image( imageName ) ;

		if ( ! StringUtils.isEmpty( workingDir ) ) {

			requestedContainer.setWorkingDir( workingDir ) ;

		}

		List<String> commandList = CSAP.jsonStream( commands )
				.map( command -> {

					return command.asText( ) ;

				} )
				.collect( Collectors.toList( ) ) ;

		if ( ! commandList.isEmpty( ) ) {

			requestedContainer.command( commandList ) ;

		}

		List<String> argList = CSAP.jsonStream( arguments )
				.map( arg -> {

					return arg.asText( ) ;

				} )
				.collect( Collectors.toList( ) ) ;

		if ( ! argList.isEmpty( ) ) {

			requestedContainer.args( argList ) ;

		}

		List<V1ContainerPort> containerPorts = CSAP.jsonStream( ports )
				.map( port -> {

					V1ContainerPort thePort = new V1ContainerPort( ) ;

					thePort.containerPort( port.path( K8.containerPort.val( ) ).asInt( ) ) ;

					thePort.setName( port.path( K8.portName.val( ) ).asText( "default-" + thePort
							.getContainerPort( ) ) ) ;

					JsonNode hostPort = port.path( K8.hostPort.val( ) ) ;

					if ( ! hostPort.isMissingNode( ) ) {

						thePort.hostPort( hostPort.asInt( ) ) ;

					}

					JsonNode protocol = port.path( K8.protocol.val( ) ) ;

					if ( ! protocol.isMissingNode( ) ) {

						thePort.setProtocol( protocol.asText( ) ) ;

					}

					return thePort ;

				} )
				.collect( Collectors.toList( ) ) ;

		if ( ! containerPorts.isEmpty( ) ) {

			requestedContainer.ports( containerPorts ) ;

		}

		List<V1EnvVar> kubVars = CSAP.jsonStream( environmentVariables )
				.map( JsonNode::asText )
				.map( nameVal -> nameVal.split( "=", 2 ) )
				.filter( nameValArray -> nameValArray.length == 2 )
				.map( kubVariable -> {

					V1EnvVar addr = new V1EnvVar( ) ;
					addr.name( kubVariable[0] ) ;
					addr.value( kubVariable[1] ) ;
					return addr ;

				} )
				.collect( Collectors.toList( ) ) ;

		if ( ! kubVars.isEmpty( ) ) {

			requestedContainer.env( kubVars ) ;

		}

		logger.debug( "environmentVariables: {} \n\t parsed as: {}", environmentVariables, kubVars ) ;

		return podSpec ;

	}

	public ObjectNode deploymentDelete (
											String deploymentName ,
											String namespace ,
											boolean deleteService ,
											boolean deleteIngress )
		throws Exception {

		ObjectNode deleteSpecsResult = jsonMapper.createObjectNode( ) ;

		AppsV1Api apiBeta = new AppsV1Api( kubernetes.apiClient( ) ) ;
		V1DeleteOptions deleteOptions = new V1DeleteOptions( ) ;
		// deleteOptions.setOrphanDependents( false ) ;
		logger.info( "deleting: {} in {} , Options: {}", deploymentName, namespace, deleteOptions.toString( ) ) ;

		try {

			String pretty = "true" ;
			String dryRun = null ;

			Boolean orphanDependents = null ;
			var propagationPolicy = Propogation_Policy.foreground.apiValue( ) ;

			var deleteResult = apiBeta.deleteNamespacedDeployment(
					deploymentName, namespace, pretty,
					dryRun, gracePeriodSeconds, orphanDependents, propagationPolicy, deleteOptions ) ;

			deleteSpecsResult.set( "delete-deployment", listingsBuilder.serializeToJson( deleteResult ) ) ;

			if ( deleteService ) {

				deleteSpecsResult.set( "delete-service", serviceDelete( deploymentName + "-service", namespace ) ) ;

			}

			if ( deleteIngress ) {

				deleteSpecsResult.set( "delete-ingress", ingressDelete( deploymentName + "-ingress", namespace ) ) ;

			}

			logger.info( "deleteSpecsResult: {} ", CSAP.jsonPrint( deleteSpecsResult ) ) ;

		} catch ( JsonSyntaxException e ) {

			deleteSpecsResult = kubernetes.handleKubernetesApiBug( e ) ;

		} catch ( Exception e ) {

			deleteSpecsResult = kubernetes.buildErrorResponse( "Failed to delete deployment", e ) ;

		}

		return deleteSpecsResult ;

	}

	public ObjectNode serviceDelete ( String serviceName , String namespace ) {

		V1DeleteOptions body = new V1DeleteOptions( ) ;
		logger.info( "body: {} ", body.toString( ) ) ;

		ObjectNode result ;

		try {

			CoreV1Api apiV1 = new CoreV1Api( kubernetes.apiClient( ) ) ;

			String pretty = "true" ;
			String dryRun = null ;
			Boolean orphanDependents = null ;
			var propagationPolicy = Propogation_Policy.foreground.apiValue( ) ;

			var deleteResult = apiV1.deleteNamespacedService(
					serviceName, namespace, pretty,
					dryRun, gracePeriodSeconds, orphanDependents, propagationPolicy, body ) ;

			result = (ObjectNode) listingsBuilder.serializeToJson( deleteResult ) ;
			logger.info( "result: {} ", result ) ;

		} catch ( Exception e ) {

			result = kubernetes.buildErrorResponse( "Failed to delete service", e ) ;

		}

		return result ;

	}

	public ObjectNode serviceCreate (
										String type ,
										String targetName ,
										String serviceName ,
										String namespace ,
										String ports ,
										String labelsByType ) {

		return serviceCreate( type, targetName, serviceName, "", namespace, loadArray( ports ), labelsByType ) ;

	}

	private ObjectNode serviceCreate (
										String serviceType ,
										String targetName ,
										String serviceName ,
										String servicePath ,
										String namespace ,
										ArrayNode ports ,
										String labelsByType ) {

		V1Service serviceCreateRequest = new V1Service( ) ;

		V1ObjectMeta deployMetaData = buildMetaData( serviceName, namespace ) ;

		Map<String, String> labels = new HashMap<>( ) ;
		labels.put( "csap-service", serviceName ) ;

		if ( StringUtils.isNoneEmpty( labelsByType ) ) {

			try {

				JsonNode labelDef = jsonMapper.readTree( labelsByType ) ;

				if ( labelDef.isObject( ) && labelDef.path( "service" ).isObject( ) ) {

					Map<String, String> customerLabels = jsonMapper.convertValue(
							labelDef.path( "service" ),
							Map.class ) ;
					labels.putAll( customerLabels ) ;

				}

			} catch ( Exception e ) {

				logger.info( "{} warn invalid labelsByType: {}", serviceName, labelsByType ) ;

			}

		}

		deployMetaData.setLabels( labels ) ;

		Map<String, String> annotations = new HashMap<>( ) ;
		annotations.put( "csap-ui-launch-path", servicePath ) ;
		deployMetaData.setAnnotations( annotations ) ;

		serviceCreateRequest.setMetadata( deployMetaData ) ;

		Map<String, String> deploymentSelector = labelToLinkDeployToPodSpec( targetName ) ;
		V1ServiceSpec serviceSpec = new V1ServiceSpec( ) ;
		serviceSpec.setSelector( deploymentSelector ) ;

		serviceSpec.setType( serviceType ) ; // default ClusterIP

		serviceCreateRequest.setSpec( serviceSpec ) ;

		List<V1ServicePort> containerPorts = CSAP.jsonStream( ports )
				.map( port -> {

					V1ServicePort thePort = new V1ServicePort( ) ;
					thePort.setPort( port.path( K8.servicePort.val( ) ).asInt( ) ) ;
					thePort.setName( port.path( K8.portName.val( ) ).asText( "default-" + thePort
							.getPort( ) ) ) ;
					thePort.setTargetPort( new IntOrString( port.path( K8.containerPort.val( ) )
							.asInt( ) ) ) ;
					return thePort ;

				} )
				.collect( Collectors.toList( ) ) ;

		serviceSpec.ports( containerPorts ) ;

		ObjectNode result ;

		try {

			CoreV1Api apiV1 = new CoreV1Api( kubernetes.apiClient( ) ) ;

			String dryRun = null ;
			String pretty = null ;
			String fieldManager = null ;

			V1Service exposedService = apiV1.createNamespacedService( namespace, serviceCreateRequest,
					pretty, dryRun, fieldManager ) ;
			result = (ObjectNode) listingsBuilder.serializeToJson( exposedService ) ;
			logger.info( "result: {} ", result ) ;

		} catch ( Exception e ) {

			result = kubernetes.buildErrorResponse( "Failed to create service", e ) ;

		}

		return result ;

	}

	public JsonNode ingressDelete ( String ingressName , String namespace ) {

		V1DeleteOptions deleteOptions = new V1DeleteOptions( ) ;
		deleteOptions.setOrphanDependents( false ) ;

		logger.info( "deleteOptions: {} ", deleteOptions.toString( ) ) ;

		ObjectNode result ;

		try {

			var networkApi = new NetworkingV1Api( kubernetes.apiClient( ) ) ;

			String pretty = "true" ;
			String dryRun = null ;
			Boolean orphanDependents = null ;
			var propagationPolicy = Propogation_Policy.foreground.apiValue( ) ;

			ApiResponse<V1Status> deleteResult = networkApi.deleteNamespacedIngressWithHttpInfo(
					ingressName, namespace, pretty, dryRun, gracePeriodSeconds, orphanDependents,
					propagationPolicy, deleteOptions ) ;

			result = (ObjectNode) listingsBuilder.serializeToJson( deleteResult ) ;
			logger.info( "result: {} ", result ) ;

		} catch ( Exception e ) {

			result = kubernetes.buildErrorResponse( "Failed to delete ingress", e ) ;

		}

		return result ;

	}

	public ObjectNode replicaSetDelete ( String serviceName , String namespace ) {

		V1DeleteOptions body = new V1DeleteOptions( ) ;
		logger.info( "body: {} ", body.toString( ) ) ;

		ObjectNode result ;

		try {

			AppsV1Api appsApi = new AppsV1Api( kubernetes.apiClient( ) ) ;

			String pretty = "true" ;
			String dryRun = null ;
			Boolean orphanDependents = null ;
			var propagationPolicy = Propogation_Policy.foreground.apiValue( ) ;

			V1Status deleteResult = appsApi.deleteNamespacedReplicaSet(
					serviceName, namespace, pretty,
					dryRun, gracePeriodSeconds, orphanDependents, propagationPolicy, body ) ;

			result = (ObjectNode) listingsBuilder.serializeToJson( deleteResult ) ;
			logger.info( "result: {} ", result ) ;

		} catch ( Exception e ) {

			result = kubernetes.buildErrorResponse( "Failed to delete service", e ) ;

		}

		return result ;

	}

	public ObjectNode persistentVolumeClaimDelete ( ServiceInstance service )
		throws Exception {

		var deleteResults = jsonMapper.createObjectNode( ) ;
		var namespace = service.getKubernetesNamespace( ) ;
		var volumeDefinitionText = jsonAsString( service, service.getDockerSettings( ).get( C7.volumes
				.val( ) ), "" ) ;
		var volumesDefinition = jsonMapper.readTree( volumeDefinitionText ) ;

		CSAP.jsonStream( volumesDefinition ).forEach( volumeDef -> {

			JsonNode persistentVolume_definition = volumeDef.path( K8.persistentVolumeClaim.val( ) ) ;

			if ( persistentVolume_definition.isObject( ) ) {

				String claimName = persistentVolume_definition.path( K8.claimName.val( ) ).asText( ) ;
				ObjectNode results = persistentVolumeClaimDelete( claimName, namespace ) ;
				deleteResults.set( claimName, results ) ;

			}

		} ) ;

		return deleteResults ;

	}

	public ObjectNode persistentVolumeClaimDelete ( String claimName , String namespace ) {

		V1DeleteOptions body = new V1DeleteOptions( ) ;
		logger.info( "body: {} ", body.toString( ) ) ;

		ObjectNode result ;

		try {

			CoreV1Api apiV1 = new CoreV1Api( kubernetes.apiClient( ) ) ;

			String pretty = "true" ;
			String dryRun = null ;
			Boolean orphanDependents = null ;
			var propagationPolicy = Propogation_Policy.foreground.apiValue( ) ;

			var deleteResult = apiV1.deleteNamespacedPersistentVolumeClaim(
					claimName, namespace, pretty,
					dryRun, gracePeriodSeconds, orphanDependents, propagationPolicy, body ) ;

			result = (ObjectNode) listingsBuilder.serializeToJson( deleteResult ) ;
			logger.info( "result: {} ", result ) ;

		} catch ( Exception e ) {

			result = kubernetes.buildErrorResponse( "Failed to delete persistent volume claim", e ) ;

		}

		return result ;

	}
}
