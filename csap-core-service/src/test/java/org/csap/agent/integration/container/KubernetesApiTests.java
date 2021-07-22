package org.csap.agent.integration.container ;

import static org.assertj.core.api.Assertions.assertThat ;

import java.io.ByteArrayOutputStream ;
import java.io.File ;
import java.io.IOException ;
import java.io.InputStream ;
import java.util.ArrayList ;
import java.util.Arrays ;
import java.util.Collections ;
import java.util.HashMap ;
import java.util.List ;
import java.util.Map ;
import java.util.Objects ;
import java.util.Optional ;
import java.util.concurrent.TimeUnit ;
import java.util.regex.Pattern ;
import java.util.stream.Collectors ;
import java.util.stream.IntStream ;

import org.apache.commons.lang3.StringUtils ;
import org.csap.agent.CsapBareTest ;
import org.csap.agent.CsapThinNoProfile ;
import org.csap.agent.container.kubernetes.ApiDirect ;
import org.csap.agent.container.kubernetes.KubernetesIntegration ;
import org.csap.agent.container.kubernetes.KubernetesIntegration.Propogation_Policy ;
import org.csap.agent.container.kubernetes.KubernetesJson ;
import org.csap.agent.services.ClassMatcher ;
import org.csap.helpers.CSAP ;
import org.csap.helpers.CsapApplication ;
import org.junit.jupiter.api.Assumptions ;
import org.junit.jupiter.api.BeforeAll ;
import org.junit.jupiter.api.BeforeEach ;
import org.junit.jupiter.api.DisplayName ;
import org.junit.jupiter.api.Nested ;
import org.junit.jupiter.api.Tag ;
import org.junit.jupiter.api.Test ;
import org.junit.jupiter.api.TestInstance ;
import org.springframework.boot.context.properties.ConfigurationProperties ;
import org.springframework.test.context.ActiveProfiles ;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties ;
import com.fasterxml.jackson.annotation.JsonProperty ;
import com.fasterxml.jackson.databind.JsonNode ;
import com.fasterxml.jackson.databind.node.ObjectNode ;
import com.google.gson.ExclusionStrategy ;
import com.google.gson.FieldAttributes ;
import com.google.gson.Gson ;
import com.google.gson.GsonBuilder ;
import com.google.gson.JsonSyntaxException ;
import com.jayway.jsonpath.JsonPath ;

import io.kubernetes.client.PodLogs ;
import io.kubernetes.client.custom.IntOrString ;
import io.kubernetes.client.custom.Quantity ;
import io.kubernetes.client.openapi.ApiClient ;
import io.kubernetes.client.openapi.ApiException ;
import io.kubernetes.client.openapi.ApiResponse ;
import io.kubernetes.client.openapi.Configuration ;
import io.kubernetes.client.openapi.apis.AppsV1Api ;
import io.kubernetes.client.openapi.apis.BatchV1Api ;
import io.kubernetes.client.openapi.apis.BatchV1beta1Api ;
import io.kubernetes.client.openapi.apis.CoreV1Api ;
import io.kubernetes.client.openapi.apis.NetworkingV1beta1Api ;
import io.kubernetes.client.openapi.apis.StorageV1Api ;
import io.kubernetes.client.openapi.apis.VersionApi ;
import io.kubernetes.client.openapi.models.CoreV1Event ;
import io.kubernetes.client.openapi.models.NetworkingV1beta1HTTPIngressPath ;
import io.kubernetes.client.openapi.models.NetworkingV1beta1HTTPIngressRuleValue ;
import io.kubernetes.client.openapi.models.NetworkingV1beta1Ingress ;
import io.kubernetes.client.openapi.models.NetworkingV1beta1IngressBackend ;
import io.kubernetes.client.openapi.models.NetworkingV1beta1IngressList ;
import io.kubernetes.client.openapi.models.NetworkingV1beta1IngressRule ;
import io.kubernetes.client.openapi.models.NetworkingV1beta1IngressSpec ;
import io.kubernetes.client.openapi.models.V1Container ;
import io.kubernetes.client.openapi.models.V1ContainerPort ;
import io.kubernetes.client.openapi.models.V1DeleteOptions ;
import io.kubernetes.client.openapi.models.V1Deployment ;
import io.kubernetes.client.openapi.models.V1DeploymentList ;
import io.kubernetes.client.openapi.models.V1DeploymentSpec ;
import io.kubernetes.client.openapi.models.V1EmptyDirVolumeSource ;
import io.kubernetes.client.openapi.models.V1EnvVar ;
import io.kubernetes.client.openapi.models.V1HTTPGetAction ;
import io.kubernetes.client.openapi.models.V1HostPathVolumeSource ;
import io.kubernetes.client.openapi.models.V1Job ;
import io.kubernetes.client.openapi.models.V1LabelSelector ;
import io.kubernetes.client.openapi.models.V1Namespace ;
import io.kubernetes.client.openapi.models.V1Node ;
import io.kubernetes.client.openapi.models.V1NodeList ;
import io.kubernetes.client.openapi.models.V1ObjectMeta ;
import io.kubernetes.client.openapi.models.V1PersistentVolume ;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaim ;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaimList ;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaimSpec ;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaimVolumeSource ;
import io.kubernetes.client.openapi.models.V1PersistentVolumeList ;
import io.kubernetes.client.openapi.models.V1Pod ;
import io.kubernetes.client.openapi.models.V1PodList ;
import io.kubernetes.client.openapi.models.V1PodSecurityContext ;
import io.kubernetes.client.openapi.models.V1PodSpec ;
import io.kubernetes.client.openapi.models.V1PodTemplateSpec ;
import io.kubernetes.client.openapi.models.V1Probe ;
import io.kubernetes.client.openapi.models.V1ReplicaSet ;
import io.kubernetes.client.openapi.models.V1ReplicaSetList ;
import io.kubernetes.client.openapi.models.V1ResourceRequirements ;
import io.kubernetes.client.openapi.models.V1Service ;
import io.kubernetes.client.openapi.models.V1ServiceList ;
import io.kubernetes.client.openapi.models.V1ServicePort ;
import io.kubernetes.client.openapi.models.V1ServiceSpec ;
import io.kubernetes.client.openapi.models.V1StatefulSet ;
import io.kubernetes.client.openapi.models.V1StatefulSetList ;
import io.kubernetes.client.openapi.models.V1Status ;
import io.kubernetes.client.openapi.models.V1StorageClass ;
import io.kubernetes.client.openapi.models.V1StorageClassList ;
import io.kubernetes.client.openapi.models.V1Volume ;
import io.kubernetes.client.openapi.models.V1VolumeMount ;
import io.kubernetes.client.openapi.models.V1beta1CronJob ;
import io.kubernetes.client.openapi.models.V1beta1CronJobList ;
import io.kubernetes.client.openapi.models.VersionInfo ;
import io.kubernetes.client.util.Yaml ;

/**
 * 
 * Test native apis to kubernetes
 * 
 * @see <a href=
 *      "https://kubernetes.io/docs/reference/generated/kubernetes-api/v1.16/">
 *      kubernetes api docs</a>
 * @see <a href=
 *      "https://github.com/kubernetes-client/java/tree/master/kubernetes/docs">
 *      kubernetes java client docs</a>
 * 
 * @see <a href= "https://github.com/kubernetes-client/java/issues"> java client
 *      open issues</a>
 * 
 *      <br>
 *      <br>
 *      Kubernetes Garbage Collection:
 * @see <a href=
 *      "https://kubernetes.io/docs/concepts/workloads/controllers/garbage-collection/">
 *      propogation policy</a>
 * @see <a href=
 *      "https://thenewstack.io/deletion-garbage-collection-kubernetes-objects/">
 *      propogation article</a>
 * 
 * 
 * @author peter.nightingale
 *
 */
@Tag ( "containers" )
@DisplayName ( "Kubernetes Api: io.kubernetes.client tests" )
@ActiveProfiles ( {
		"agent", CsapBareTest.PROFILE_JUNIT
} )
@ConfigurationProperties ( prefix = "test.junit.kubernetes" )
public class KubernetesApiTests extends CsapThinNoProfile {

	static ApiClient apiClient ;

	static CoreV1Api apiV1 ;

	static AppsV1Api apiApps ;

	static StorageV1Api storageV1 ;

	static BatchV1Api batchV1 ;

	static BatchV1beta1Api batchV1Beta1 ;

	final int gracePeriodSeconds_0 = 0 ;

	String dryRun_null = null ;
	String pretty_true = "true" ;
	Boolean orphanDependents_null = null ;
	String propagationPolicy_foreground = Propogation_Policy.foreground.apiValue( ) ;

	Boolean allowWatchBookmarks_null = null ;
	String _continue_null = null ;
	String fieldSelector_null = null ;
	String labelSelector_null = null ;

	Integer limit_500 = 500 ;
	String resourceVersion_null = null ;
	String resourceVersionMatch_null = null ;
	Integer timeoutSeconds_10 = 10 ;
	Boolean watch_null = null ;

	// static AppsV1beta2Api apiBeta2;

	// static ExtensionsV1beta1Api apiBetaExtensions ;
	static NetworkingV1beta1Api networkingApi ;

	String JUNIT_NFS_VOLUME = "junit-nfs-volume" ;

	String JUNIT_HOST_VOLUME = "junit-host-volume" ;

	String JUNIT_EMPTY_VOLUME = "junit-empty-volume" ;

	String configUrl ;

	Gson gson = new Gson( ) ;
	static Gson gsonWithExclusions ;

	static {

		ExclusionStrategy managedFieldsExclusion = new ExclusionStrategy( ) {
			@Override
			public boolean shouldSkipField ( FieldAttributes field ) {

				if ( field.getName( ).equals( "managedFields" ) ) {

					return true ;

				}

				return false ;

			}

			@Override
			public boolean shouldSkipClass ( Class<?> clazz ) {

				return false ;

			}
		} ;

		gsonWithExclusions = new GsonBuilder( )
				.setPrettyPrinting( )
				.setExclusionStrategies( managedFieldsExclusion ) // new JodaExclusion( ),
				.create( ) ;

	}

	File extractDir = new File( System.getProperty( "user.home" ), "agent-junit-folder" ) ;

	@BeforeAll
	public void beforeAll ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( "Retrieving kubernetes config: {}" ), getConfigUrl( ) ) ;

		apiClient = KubernetesIntegration.buildAndCacheDesktopCredentials( logger, getConfigUrl( ), extractDir ) ;

		// File k8Credentials = new File( System.getProperty( "user.home" ) +
		// "/csap/kube/config" );
		// Assumptions.assumeTrue( k8Credentials.canRead( ) );
		// apiClient = Config.fromConfig( k8Credentials.getAbsolutePath( ) );

		// apiClient.setDebugging( true );
		// apiClient.getHttpClient().setReadTimeout( 3, TimeUnit.SECONDS );
		// apiClient.getHttpClient().setConnectTimeout( 1, TimeUnit.SECONDS );
		// apiClient.getHttpClient().setWriteTimeout( 1, TimeUnit.SECONDS );
		// apiClient.getHttpClient().set
		Configuration.setDefaultApiClient( apiClient ) ;

		apiV1 = new CoreV1Api( apiClient ) ;
		// apiBeta2 = new AppsV1beta2Api( apiClient );

		networkingApi = new NetworkingV1beta1Api( apiClient ) ;

		apiApps = new AppsV1Api( apiClient ) ;

		storageV1 = new StorageV1Api( apiClient ) ;

		batchV1 = new BatchV1Api( apiClient ) ;

		batchV1Beta1 = new BatchV1beta1Api( apiClient ) ;

	}

	public V1Namespace create_namespace ( V1Namespace body )
		throws ApiException {

		String pretty = null ;
		String dryRun = null ;
		String fieldManager = null ;
		V1Namespace result = apiV1.createNamespace( body, pretty, dryRun, fieldManager ) ;
		return result ;

	}

	@JsonIgnoreProperties ( ignoreUnknown = true )
	public static class PodTinyListing {

		public List<PodFewFields> items ;

		@Override
		public String toString ( ) {

			return "PodTinyListing" + items.toString( ) ;

		}

	}

	@JsonIgnoreProperties ( ignoreUnknown = true )
	public static class PodFewFields {

		String name ;

		@JsonProperty ( "metadata" )
		public void unpackMetaData ( Map<String, Object> metadata ) {

			name = (String) metadata.get( "name" ) ;

		};

		@Override
		public String toString ( ) {

			return name ;

		}

	}

	@Nested
	@TestInstance ( TestInstance.Lifecycle.PER_CLASS )
	@DisplayName ( "Statefull tests (name spaces cleared)" )
	class Statefull {

		@BeforeEach
		public void beforeEach ( )
			throws Exception {

			reset_test_namespace( ) ;
			Assumptions.assumeFalse( abortTests ) ;

		}

		@Test
		public void namespace_add_and_delete ( )
			throws Exception {

			var testNamespace = "junit-test-namespace-ops" ;

			List<String> nsNames = namespaces( ) ;

			assertThat( nsNames.toString( ) )
					.as( "initial name spaces" )
					.doesNotContain( testNamespace ) ;

			var body = new V1Namespace( ) ;
			var metadata = new V1ObjectMeta( ) ;
			metadata.setName( testNamespace ) ;
			body.setMetadata( metadata ) ;

			try {
				// V1Namespace result = apiV1.createNamespace
				// V1Namespace result = apiV1.createNamespace( body, "true" ) ;

				V1Namespace result = create_namespace( body ) ;

				logger.info( "result: {} ", gsonWithExclusions.toJson( result ) ) ;

				nsNames = namespaces( ) ;
				assertThat( nsNames.toString( ) )
						.as( "initial name spaces" )
						.contains( testNamespace ) ;

				V1DeleteOptions deleteBody = new V1DeleteOptions( ) ;

				var deleteResult = apiV1.deleteNamespace(
						testNamespace, pretty_true, dryRun_null,
						gracePeriodSeconds_0,
						orphanDependents_null,
						propagationPolicy_foreground,
						deleteBody ) ;

				logger.info( "deleteResult: {} ", gsonWithExclusions.toJson( deleteResult ) ) ;

				var attempts = waitForNamespaceDeletedComplete( testNamespace ) ;
				Assumptions.assumeTrue( attempts < maxTries ) ;

				nsNames = namespaces( ) ;
				assertThat( nsNames.toString( ) )
						.as( "initial name spaces" )
						.doesNotContain( testNamespace ) ;

			} catch ( JsonSyntaxException e ) {

				logger.warn( "Refer to {}, {}", KubernetesIntegration.knownDeleteIssue, CSAP.buildCsapStack( e ) ) ;

			} catch ( Exception e ) {

				logger.error( "Failed to delete: {}",
						CSAP.buildCsapStack( e ), e ) ;
				throw e ;

			}

		}

		@Test
		public void yaml_create_multiple_specs ( )
			throws Exception {

			namespace_delete( "junit-tests" ) ;

			List<Object> yamlDocs = Yaml.loadAll(
					new File(
							getClass( ).getResource(
									"k8-nginx-multiple.yaml" )
									.getPath( ) ) ) ;

			List<String> docInfo = yamlDocs
					.stream( )
					.filter( Objects::nonNull )
					.map( doc -> doc.getClass( ).getName( ) )
					.collect( Collectors.toList( ) ) ;

			logger.info( "docs: {}", docInfo ) ;

			logger.warn( CsapApplication.testHeader( "starting yaml creates" ) ) ;

			yamlDocs
					.stream( )
					.filter( Objects::nonNull )
					.forEach( apiSpec -> {

						ClassMatcher.match( )
								.with( V1Namespace.class, this::yaml_namespace_create )
								.with( V1Deployment.class, this::yaml_deployment_create )
								.with( V1Service.class, this::yaml_service_create )
								// .with( ExtensionsV1beta1Ingress.class, this::yaml_ingress_create )
								.fallthrough( spec -> logger.warn( "Unknown type called to actor, cannot route: {}",
										spec ) )
								.exec( apiSpec ) ;

					} ) ;

			wait_for_pod_running( "nginx-junit-multiple-yaml", "junit-tests" ) ;

			logger.warn( CsapApplication.testHeader( "starting yaml deletes" ) ) ;

			Collections.reverse( yamlDocs ) ;
			yamlDocs
					.stream( )
					.filter( Objects::nonNull )
					.forEach( apiSpec -> {

						ClassMatcher.match( )
								.with( V1Namespace.class, this::yaml_namespace_delete )
								.with( V1Deployment.class, this::yaml_deployment_delete )
								.with( V1Service.class, this::yaml_service_delete )
								// .with( ExtensionsV1beta1Ingress.class, this::yaml_ingress_delete )
								.fallthrough( spec -> logger.warn( "Unknown type called to actor, cannot route: {}",
										spec ) )
								.exec( apiSpec ) ;

					} ) ;

		}

		private void yaml_namespace_create ( V1Namespace apiSpec ) {

			V1Namespace response = null ;

			try {

				response = create_namespace( apiSpec ) ;

				logger.info( "nsResponse: {} ", gsonWithExclusions.toJson( response ) ) ;

			} catch ( Exception e ) {

				logErrors( e, apiSpec.toString( ) ) ;

			}

			assertThat( response.getStatus( ).getPhase( ) ).isEqualTo( "Active" ) ;

		}

		private void yaml_namespace_delete ( V1Namespace apiSpec ) {

			try {

				var body = new V1DeleteOptions( ) ;
				var nsName = apiSpec.getMetadata( ).getName( ) ;

				logger.info( "deleting namespace: {} ", nsName ) ;

				var deleteResult = apiV1.deleteNamespace(
						nsName,
						pretty_true,
						dryRun_null,
						gracePeriodSeconds_0,
						orphanDependents_null,
						propagationPolicy_foreground, body ) ;

				logger.info( "nsResponse: {} ", gsonWithExclusions.toJson( deleteResult ) ) ;

			} catch ( Exception e ) {

				logErrors( e, apiSpec.toString( ) ) ;

			}

		}

		private void yaml_deployment_delete ( V1Deployment apiSpec ) {

			try {

				var deployname = apiSpec.getMetadata( ).getName( ) ;

				logger.info( "deleting deployname: {} ", deployname ) ;

				deployment_delete(
						deployname,
						apiSpec.getMetadata( ).getNamespace( ) ) ;

			} catch ( Exception e ) {

				logErrors( e, apiSpec.toString( ) ) ;

			}

		}

		private void yaml_service_delete ( V1Service apiSpec ) {

			try {

				var name = apiSpec.getMetadata( ).getName( ) ;

				logger.info( "deleting: {} ", name ) ;

				service_delete(
						name,
						apiSpec.getMetadata( ).getNamespace( ) ) ;

			} catch ( Exception e ) {

				logErrors( e, apiSpec.toString( ) ) ;

			}

		}

		private void yaml_ingress_delete ( NetworkingV1beta1Ingress apiSpec ) {

			try {

				var name = apiSpec.getMetadata( ).getName( ) ;

				logger.info( "deleting: {} ", name ) ;
				ingress_delete(
						name,
						apiSpec.getMetadata( ).getNamespace( ) ) ;

			} catch ( Exception e ) {

				logErrors( e, apiSpec.toString( ) ) ;

			}

		}

		private void yaml_ingress_create ( NetworkingV1beta1Ingress apiSpec ) {

			NetworkingV1beta1Ingress ingressResponse = null ;

			try {

				String dryRun = null ;
				String pretty = null ;
				String fieldManager = null ;
				networkingApi.createNamespacedIngress(
						apiSpec.getMetadata( ).getNamespace( ),
						apiSpec, pretty, dryRun, fieldManager ) ;
				logger.info( "ingressResponse: {} ", gsonWithExclusions.toJson( ingressResponse ) ) ;

			} catch ( Exception e ) {

				logErrors( e, apiSpec.toString( ) ) ;

			}

			assertThat( ingressResponse ).isNotNull( ) ;
			assertThat( ingressResponse.getSpec( ).getRules( ).toString( ) )
					.contains( "nginx-junit-multiple-yaml-service" ) ;

		}

		private void yaml_service_create ( V1Service apiSpec ) {

			V1Service serviceCreated = null ;

			try {

				String fieldManager = null ;

				serviceCreated = apiV1.createNamespacedService(
						apiSpec.getMetadata( ).getNamespace( ), apiSpec, pretty_true, dryRun_null, fieldManager ) ;

				logger.info( "exposedService: {} ", gsonWithExclusions.toJson( serviceCreated ) ) ;

			} catch ( Exception e ) {

				logErrors( e, apiSpec.toString( ) ) ;

			}

			assertThat( serviceCreated ).isNotNull( ) ;
			assertThat( serviceCreated.getSpec( ).getType( ) )
					.isEqualTo( KubernetesJson.CLUSTER_IP.json( ) ) ;

		}

		private void yaml_deployment_create ( V1Deployment deploymentSpec ) {

			V1Deployment deployResult = null ;

			try {

				String fieldManager = null ;

				deployResult = apiApps.createNamespacedDeployment(
						deploymentSpec.getMetadata( ).getNamespace( ),
						deploymentSpec,
						pretty_true, dryRun_null,
						fieldManager ) ;

				String deployResultJson = gsonWithExclusions.toJson( deployResult ) ;

				logger.info( "deployment create result: {} ",
						deployResultJson ) ;

				JsonNode deployNode = getJsonMapper( ).readTree( deployResultJson ) ;

				assertThat( deployNode.at( "/spec/replicas" ).asInt( ) )
						.as( "replicas create" )
						.isEqualTo( 2 ) ;

			} catch ( Exception e ) {

				logErrors( e, deploymentSpec.toString( ) ) ;

			}

			assertThat( deployResult.getSpec( ).getReplicas( ).intValue( ) ).isEqualTo( 2 ) ;

		}

		@Test
		public void yaml_deploy_nginx ( )
			throws Exception {

			logger.info( CsapApplication.testHeader( ) ) ;

			String namespace = "junit-yaml" ;
			namespace_delete( namespace ) ;
			namespace_create( namespace, true ) ;

			V1Deployment deployResult = yaml_deploy( namespace ) ;

			deployment_delete( deployResult.getMetadata( ).getName( ), namespace ) ;

		}

		private V1Deployment yaml_deploy ( String namespace )
			throws IOException ,
			ApiException {

			V1Deployment deployBody = Yaml.loadAs(
					new File(
							getClass( ).getResource(
									"k8-nginx-deploy.yaml" )
									.getPath( ) ),
					V1Deployment.class ) ;

			String dryRun = null ;
			String pretty = null ;
			String fieldManager = null ;

			V1Deployment deployResult = apiApps.createNamespacedDeployment(
					namespace,
					deployBody,
					pretty, dryRun, fieldManager ) ;

			String deployResultJson = gsonWithExclusions.toJson( deployResult ) ;

			logger.info( "deployment create result: {} ",
					gsonWithExclusions.toJson( deployBody ),
					deployResultJson ) ;

			JsonNode deployNode = getJsonMapper( ).readTree( deployResultJson ) ;

			assertThat( deployNode.at( "/spec/replicas" ).asInt( ) )
					.as( "replicas create" )
					.isEqualTo( 2 ) ;
			return deployResult ;

		}

		public final static String TEST_NAMESPACE_NAME = "junit-kubernetes-csap" ;
		public final static String TEST_POD_NAME = "junit-nginx" ;

		@Test
		public void pod_create_and_delete ( )
			throws Exception {

			logger.info( CsapApplication.testHeader( ) ) ;

			String podName = "junit-pod" ;
			// podName="demo-698c59d995-t5snm" ;
			assertThat( pod_create( podName ) ).isTrue( ) ;

			Thread.sleep( 500 ) ;

			pod_delete( podName ) ;

		}

		public boolean pod_create ( String podName ) {

			V1Pod podBody = buildPod( podName ) ;

			V1PodSpec podSpec = buildPodSpec( podName + "-container" ) ;

			podBody.setSpec( podSpec ) ;

			logger.info( "deployResult: {} ", podBody.toString( ) ) ;

			try {

				String dryRun = null ;
				String pretty = null ;
				String fieldManager = null ;

				V1Pod pod = apiV1.createNamespacedPod(
						TEST_NAMESPACE_NAME, podBody,
						pretty, dryRun, fieldManager ) ;
				logger.info( "deployResult: {} ", gsonWithExclusions.toJson( pod ) ) ;

				return wait_for_pod_running( pod.getMetadata( ).getName( ), pod.getMetadata( ).getNamespace( ) ) ;

			} catch ( ApiException e ) {

				logger.error( "Failed to deploy: {}",
						CSAP.buildCsapStack( e ), e ) ;

			}

			return false ;

		}

		public V1Pod buildPod ( String podName ) {

			V1Pod podBody = new V1Pod( ) ;
			// podBody.apiVersion( "v1" );
			// podBody.kind( "Pod" );

			V1ObjectMeta meta = new V1ObjectMeta( ) ;
			meta.name( podName ) ;
			podBody.metadata( meta ) ;
			return podBody ;

		}

		public V1PodSpec buildPodSpec ( String containerName ) {

			V1PodSpec podSpec = new V1PodSpec( ) ;

			Map<String, String> nodeSelector = new HashMap<>( ) ;
			// nodeSelector.put( "kubernetes.io/hostname", "csap-dev04.***REMOVED***" ) ;
			podSpec.setNodeSelector( nodeSelector ) ;

			V1PodSecurityContext securityContext = new V1PodSecurityContext( ) ;
			podSpec.setSecurityContext( securityContext ) ;

			V1Container container = new V1Container( ) ;
			podSpec.containers( Arrays.asList( container ) ) ;
			container.name( containerName ) ;
			container.image( "docker.io/nginx:1.16.1" ) ;

			V1ResourceRequirements resources = new V1ResourceRequirements( ) ;
			resources.putRequestsItem( "cpu", Quantity.fromString( "500m" ) ) ;
			resources.putRequestsItem( "memory", Quantity.fromString( "1G" ) ) ;
			resources.putLimitsItem( "cpu", Quantity.fromString( "2" ) ) ;
			resources.putLimitsItem( "memory", Quantity.fromString( "3G" ) ) ;
			container.setResources( resources ) ;

			V1Probe livenessProbe = new V1Probe( ) ;
			livenessProbe.setFailureThreshold( 10 ) ;
			livenessProbe.setInitialDelaySeconds( 30 ) ;
			livenessProbe.setPeriodSeconds( 30 ) ;
			livenessProbe.setTimeoutSeconds( 10 ) ;

			V1HTTPGetAction httpGet = new V1HTTPGetAction( ) ;
			httpGet.setPath( "/" ) ;
			httpGet.setPort( new IntOrString( 80 ) ) ;
			livenessProbe.setHttpGet( httpGet ) ;
			container.setLivenessProbe( livenessProbe ) ;

			V1Probe readinessProbe = new V1Probe( ) ;
			readinessProbe.setInitialDelaySeconds( 10 ) ;
			readinessProbe.setPeriodSeconds( 30 ) ;
			readinessProbe.setTimeoutSeconds( 10 ) ;
			readinessProbe.setHttpGet( httpGet ) ;

			container.setReadinessProbe( readinessProbe ) ;

			V1ContainerPort thePort = new V1ContainerPort( ) ;
			thePort.containerPort( 80 ) ;
			thePort.name( "nginx-80" ) ;
			container.ports( Arrays.asList( thePort ) ) ;

			V1EnvVar addr = new V1EnvVar( ) ;
			addr.name( "var1" ) ;
			addr.value( "value1" ) ;

			V1EnvVar port = new V1EnvVar( ) ;
			port.name( "var2" ) ;
			port.value( "value2" ) ;

			container.env( Arrays.asList( addr, port ) ) ;

			addVolumes( podSpec, container ) ;

			return podSpec ;

		}

		private void addVolumes ( V1PodSpec podSpec , V1Container container ) {

			List<V1Volume> volumes = new ArrayList<>( ) ;
			podSpec.setVolumes( volumes ) ;

			V1Volume emptyVolume = new V1Volume( ) ;
			volumes.add( emptyVolume ) ;
			emptyVolume.setName( JUNIT_EMPTY_VOLUME ) ;
			V1EmptyDirVolumeSource emptyDir = new V1EmptyDirVolumeSource( ) ;
			emptyDir.setSizeLimit( Quantity.fromString( "1Mi" ) ) ;
			emptyVolume.setEmptyDir( emptyDir ) ;

			V1Volume hostVolume = new V1Volume( ) ;
			volumes.add( hostVolume ) ;
			hostVolume.setName( JUNIT_HOST_VOLUME ) ;
			V1HostPathVolumeSource hostPath = new V1HostPathVolumeSource( ) ;
			hostVolume.setHostPath( hostPath ) ;
			hostPath.setType( "DirectoryOrCreate" ) ;
			hostPath.setPath( "/opt/csapUser/demo-k8s" ) ;

			List<V1VolumeMount> volumeMounts = new ArrayList<>( ) ;
			container.setVolumeMounts( volumeMounts ) ;

			V1VolumeMount emptyMount = new V1VolumeMount( ) ;
			volumeMounts.add( emptyMount ) ;
			emptyMount.setMountPath( "/mnt/emptySample" ) ;
			emptyMount.setName( JUNIT_EMPTY_VOLUME ) ;
			emptyMount.setReadOnly( false ) ;

			V1VolumeMount hostMount = new V1VolumeMount( ) ;
			volumeMounts.add( hostMount ) ;
			hostMount.setMountPath( "/mnt/hostSample" ) ;
			hostMount.setName( JUNIT_HOST_VOLUME ) ;
			hostMount.setReadOnly( false ) ;

			// pvc - create and use
			var pvcClaimName = "junit-claim" ;

			try {

				persistent_volume_claim_create( pvcClaimName, TEST_NAMESPACE_NAME ) ;

			} catch ( Exception e ) {

				logger.warn( "Failed creating pvc: {}", CSAP.buildCsapStack( e ) ) ;

			}

			V1Volume nfsVolume = new V1Volume( ) ;
			volumes.add( nfsVolume ) ;
			nfsVolume.setName( JUNIT_NFS_VOLUME ) ;
			V1PersistentVolumeClaimVolumeSource persistentVolumeClaim = new V1PersistentVolumeClaimVolumeSource( ) ;
			nfsVolume.setPersistentVolumeClaim( persistentVolumeClaim ) ;
			persistentVolumeClaim.setClaimName( pvcClaimName ) ;
			persistentVolumeClaim.setReadOnly( false ) ;

			V1VolumeMount nfsMount = new V1VolumeMount( ) ;
			volumeMounts.add( nfsMount ) ;
			nfsMount.setMountPath( "/mnt/nfsSample" ) ;
			nfsMount.setName( JUNIT_NFS_VOLUME ) ;
			nfsMount.setReadOnly( false ) ;

		}

		private void persistent_volume_claim_create ( String name , String namespace )
			throws Exception {

			V1PersistentVolumeClaim volumeClaimRequest = new V1PersistentVolumeClaim( ) ;

			V1ObjectMeta metadata = new V1ObjectMeta( ) ;
			volumeClaimRequest.setMetadata( metadata ) ;

			metadata.setName( name ) ;
			Map<String, String> annotations = new HashMap<>( ) ;
			// annotations.put( "volume.beta.kubernetes.io/storage-class",
			// "csap-nfs-storage-1" ) ;
			metadata.setAnnotations( annotations ) ;

			V1PersistentVolumeClaimSpec spec = new V1PersistentVolumeClaimSpec( ) ;

			List<String> accessModes = new ArrayList<>( ) ;
			accessModes.add( "ReadWriteMany" ) ;
			spec.setAccessModes( accessModes ) ;

			V1ResourceRequirements resources = new V1ResourceRequirements( ) ;
			Map<String, Quantity> requests = new HashMap<>( ) ;
			Quantity value = new Quantity( "100Mi" ) ;
			requests.put( "storage", value ) ;
			resources.setRequests( requests ) ;
			spec.setResources( resources ) ;

			volumeClaimRequest.setSpec( spec ) ;

			String dryRun = null ;
			String pretty = null ;
			String fieldManager = null ;

			V1PersistentVolumeClaim volumeClaimResult = apiV1.createNamespacedPersistentVolumeClaim(
					namespace,
					volumeClaimRequest,
					pretty, dryRun, fieldManager ) ;

			logger.info( "volumeClaimResult: {} ", gsonWithExclusions.toJson( volumeClaimResult ) ) ;

		}

		public void pod_delete ( String podName )
			throws Exception {

			V1DeleteOptions body = new V1DeleteOptions( ) ;
			// body.apiVersion( "v1" );

			logger.info( "body: {} ", body.toString( ) ) ;

			try {

				String pretty = "true" ;
				String dryRun = null ;
				Boolean orphanDependents = null ;
				var propagationPolicy = Propogation_Policy.foreground.apiValue( ) ;

				V1Pod pod = apiV1.deleteNamespacedPod(
						podName, TEST_NAMESPACE_NAME, pretty, dryRun, gracePeriodSeconds_0, orphanDependents,
						propagationPolicy, body ) ;

//				V1Status status = apiV1.deleteNamespacedPod(
//						podName, TEST_NAMESPACE_NAME, pretty, dryRun, gracePeriodSeconds, orphanDependents,
//						propagationPolicy, body ) ;

				logger.info( "deleted pod: {} ", pod.toString( ) ) ;
				logger.info( "delete result: {} ", pod.getStatus( ).toString( ) ) ;

			} catch ( Exception e ) {

				logger.error( "Failed to delete: {}",
						CSAP.buildCsapStack( e ), e ) ;
				throw e ;

			}

		}

		// @Disabled
		// @Test
		public void service_create ( )
			throws Exception {

			String service = service_create( "junit-service-create", TEST_NAMESPACE_NAME, 7081, 80 ) ;
			service_delete( service, TEST_NAMESPACE_NAME ) ;

		}

		private void service_delete ( String serviceName , String namespace )
			throws Exception {

			V1DeleteOptions body = new V1DeleteOptions( ) ;

			String pretty = "true" ;
			String dryRun = null ;
			Boolean orphanDependents = null ;
			var propagationPolicy = Propogation_Policy.foreground.apiValue( ) ;

			V1Status deleteResult = apiV1.deleteNamespacedService(
					serviceName, namespace, pretty,
					dryRun, gracePeriodSeconds_0, orphanDependents, propagationPolicy, body ) ;
			logger.info( "delete result: {} ", deleteResult.toString( ) ) ;

			assertThat( deleteResult.getStatus( ) )
					.as( "initial name spaces" )
					.isEqualToIgnoringCase( "success" ) ;

		}

		private String service_create ( String deploymentName , String namespace , int exposedPort , int servicePort ) {

			Map<String, String> deploymentSelector = buildLabels( "run", deploymentName ) ;

			String serviceName = deploymentName + "-service" ;

			V1Service serviceCreateRequest = new V1Service( ) ;
			V1ObjectMeta serviceMetaData = buildMetaData( serviceName, namespace ) ;
			serviceMetaData.setLabels( buildLabels( "csap-junit", "test-is-working" ) ) ;
			serviceCreateRequest.setMetadata( serviceMetaData ) ;

			V1ServiceSpec serviceSpec = new V1ServiceSpec( ) ;
			serviceSpec.setSelector( deploymentSelector ) ;
			serviceSpec.setType( KubernetesJson.NODE_PORT.json( ) ) ;
			serviceCreateRequest.setSpec( serviceSpec ) ;

			V1ServicePort servicePortSpec = new V1ServicePort( ) ;
			servicePortSpec.setTargetPort( new IntOrString( servicePort ) ) ;

			servicePortSpec.setPort( exposedPort ) ;
			serviceSpec.ports( Arrays.asList( servicePortSpec ) ) ;

			logger.info( "serviceCreateRequest: {} ", serviceCreateRequest.toString( ) ) ;

			try {

				String dryRun = null ;
				String pretty = null ;
				String fieldManager = null ;

				V1Service exposedService = apiV1.createNamespacedService( namespace, serviceCreateRequest,
						pretty, dryRun, fieldManager ) ;
				logger.info( "exposedService: {} ", gsonWithExclusions.toJson( exposedService ) ) ;

			} catch ( ApiException e ) {

				logger.error( "Failed to create exposedService: {}",
						CSAP.buildCsapStack( e ), e ) ;

			}

			return serviceName ;

		}

		@Test
		public void ingress_create_and_delete ( )
			throws Exception {

			logger.info( CsapApplication.testHeader( "Creating deployment" ) ) ;

			String deploymentName = "junit-test" ;
			int port = 80 ;
			String ingressPath = "/" ;
			deploymentName = "demo" ;
			port = 7080 ;
			String ingress = ingress_create( deploymentName, TEST_NAMESPACE_NAME, port, ingressPath,
					getApplication( ).getHostUsingFqdn( "*" ) ) ;

			ingress_delete( ingress, TEST_NAMESPACE_NAME ) ;

		}

		private void ingress_delete ( String ingressName , String namespace )
			throws Exception {

			V1DeleteOptions deleteOptions = new V1DeleteOptions( ) ;

			logger.debug( "deleting: {}", deleteOptions.toString( ) ) ;
			ApiResponse<V1Status> deleteResult ;

			try {

				String pretty = "true" ;
				String dryRun = null ;
				Boolean orphanDependents = null ;
				var propagationPolicy = Propogation_Policy.foreground.apiValue( ) ;

				deleteResult = networkingApi.deleteNamespacedIngressWithHttpInfo(
						ingressName, namespace, pretty,
						dryRun, gracePeriodSeconds_0, orphanDependents, propagationPolicy, deleteOptions ) ;

				logger.info( "response: {} ", gsonWithExclusions.toJson( deleteResult ) ) ;

				// } catch (JsonSyntaxException e) {
				// csapKubernetes.handleKubernetesApiBug( e );

			} catch ( Exception e ) {

				logger.error( "Failed to delete: {}",
						CSAP.buildCsapStack( e ), e ) ;
				throw e ;

			}

			assertThat( deleteResult.getData( ).getStatus( ) )
					.as( "initial name spaces" )
					.isEqualToIgnoringCase( "success" ) ;

		}

		public String ingress_create (
										String deploymentName ,
										String namespace ,
										int port ,
										String ingressPath ,
										String ingressHostPattern ) {

			String ingressName = deploymentName + "-ingress" ;
			String serviceName = deploymentName + "-service" ;

			// ExtensionsV1beta1Ingress ingressCreateRequest = new ExtensionsV1beta1Ingress(
			// ) ;

			//
			NetworkingV1beta1Ingress ingressCreateRequest = new NetworkingV1beta1Ingress( ) ;

			V1ObjectMeta deployMetaData = buildMetaData( ingressName, namespace ) ;
			// deployMetaData.setLabels( deploymentSelector );

			Map<String, String> annotations = new HashMap<>( ) ;
			annotations.put( "nginx.ingress.kubernetes.io/affinity", "cookie" ) ;
			annotations.put( "nginx.ingress.kubernetes.io/session-cookie-name", "k8_route" ) ;
			annotations.put( "nginx.ingress.kubernetes.io/session-cookie-hash", "sha1" ) ;
			deployMetaData.setAnnotations( annotations ) ;
			ingressCreateRequest.setMetadata( deployMetaData ) ;

			NetworkingV1beta1IngressSpec ingressSpec = new NetworkingV1beta1IngressSpec( ) ;
			ingressCreateRequest.setSpec( ingressSpec ) ;

			// List<ExtensionsV1beta1IngressRule> rules = new ArrayList<>() ;

			NetworkingV1beta1IngressRule ingress_rule = new NetworkingV1beta1IngressRule( ) ;

			NetworkingV1beta1HTTPIngressRuleValue httpRouting = new NetworkingV1beta1HTTPIngressRuleValue( ) ;

			// List<ExtensionsV1beta1HTTPIngressPath> ingressPaths = new ArrayList<>() ;

			NetworkingV1beta1IngressBackend backend = new NetworkingV1beta1IngressBackend( ) ;
			backend.setServiceName( serviceName ) ;
			backend.setServicePort( new IntOrString( port ) ) ;

			NetworkingV1beta1HTTPIngressPath httpIngress = new NetworkingV1beta1HTTPIngressPath( ) ;
			httpIngress.backend( backend ) ;
			httpIngress.setPath( ingressPath ) ;

			httpRouting.setPaths( Arrays.asList( httpIngress ) ) ;

			ingress_rule.setHttp( httpRouting ) ;
			ingress_rule.setHost( ingressHostPattern ) ;

			ingressSpec.setRules( Arrays.asList( ingress_rule ) ) ;

			logger.info( "ingressCreateRequest: {} ", ingressCreateRequest.toString( ) ) ;

			try {

				String dryRun = null ;
				String pretty = null ;
				String fieldManager = null ;

				NetworkingV1beta1Ingress ingressResponse = networkingApi.createNamespacedIngress( namespace,
						ingressCreateRequest,
						pretty, dryRun, fieldManager ) ;

				logger.info( "ingressResponse: {} ", gsonWithExclusions.toJson( ingressResponse ) ) ;

			} catch ( ApiException e ) {

				logger.error( "Failed to create ingress: {}",
						CSAP.buildCsapStack( e ), e ) ;

			}

			return ingressName ;

		}

		public Map<String, String> buildLabels ( String label , String value ) {

			Map<String, String> deploymentSelector = new HashMap<String, String>( ) ;
			deploymentSelector.put( label, value ) ;
			return deploymentSelector ;

		}

		@Test
		public void serviceTypes ( ) {

			logger.info( "serviceTypes: {}", KubernetesJson.k8TypeList( ) ) ;

		}

		@Test
		public void deployment_create_expose_and_delete ( )
			throws Exception {

			logger.info( CsapApplication.testHeader( ) ) ;

			// String now = LocalDateTime.now().format( DateTimeFormatter.ofPattern(
			// "mmss" ) );
			String deploymentName = "junit-k8" ;

			try {

				persistent_volume_claim_create( JUNIT_NFS_VOLUME + "-claim", TEST_NAMESPACE_NAME ) ;

			} catch ( Exception e ) {

				logger.info( "Failed creating volume claim: {}", CSAP.buildCsapStack( e ) ) ;

			}

			deployment_create( deploymentName, TEST_NAMESPACE_NAME ) ;

			String serviceName = service_create( deploymentName, TEST_NAMESPACE_NAME, 7081, 80 ) ;

			String ingressName = //
					ingress_create(
							deploymentName,
							TEST_NAMESPACE_NAME,
							80,
							"/", getApplication( ).getHostUsingFqdn( "*" ) ) ;

			ingress_delete( ingressName, TEST_NAMESPACE_NAME ) ;
			//
			service_delete( serviceName, TEST_NAMESPACE_NAME ) ;
			//
			deployment_delete( deploymentName, TEST_NAMESPACE_NAME ) ;

		}

		boolean doReset = true ;

		private void reset_test_namespace ( ) {

			if ( doReset ) {

				doReset = false ;
				namespace_delete( TEST_NAMESPACE_NAME ) ;
				namespace_create( TEST_NAMESPACE_NAME, true ) ;

			}

		}

		private void deployment_create ( String deploymentName , String namespace )
			throws Exception {

			V1Deployment deployBody = new V1Deployment( ) ;

			Map<String, String> matchLabels = buildLabels( "run", deploymentName ) ;

			// deployBody.setKind( "Deployment" );

			V1ObjectMeta deployMetaData = buildMetaData( deploymentName, namespace ) ;
			deployBody.setMetadata( deployMetaData ) ;
			deployMetaData.setLabels( matchLabels ) ;
			deployMetaData.setGeneration( 1L ) ;

			// metadata.setClusterName( TEST_POD_NAME + "-cluster" );

			V1DeploymentSpec deploySpec = new V1DeploymentSpec( ) ;
			deployBody.setSpec( deploySpec ) ;
			deploySpec.setReplicas( 1 ) ;

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
			// podMetaData.setCreationTimestamp( null );
			podMetaData.setLabels( matchLabels ) ;

			Map<String, String> annotations = new HashMap<>( ) ;
			annotations.put( "sample-annotation-1", "sample-value-1" ) ;
			podMetaData.setAnnotations( annotations ) ;

			podTemplate.setMetadata( podMetaData ) ;

			deploySpec.template( podTemplate ) ;

			V1PodSpec podSpec = buildPodSpec( deploymentName + "-container" ) ;
			podTemplate.setSpec( podSpec ) ;

			logger.info( "deploying: {}", deployBody.toString( ) ) ;

			String dryRun = null ;
			String pretty = null ;
			String fieldManager = null ;

			V1Deployment deployResult = apiApps.createNamespacedDeployment(
					namespace,
					deployBody,
					pretty, dryRun, fieldManager ) ;

			logger.info( "deployResult: {} ", gsonWithExclusions.toJson( deployResult ) ) ;

			assertThat( deployResult.getSpec( ).getTemplate( ).getMetadata( ).getName( ) )
					.as( "podTemplateName" )
					.contains( deploymentName ) ;

		}

		private V1ObjectMeta buildMetaData ( String itemName , String namespace ) {

			V1ObjectMeta deployMetaData = new V1ObjectMeta( ) ;
			deployMetaData.setName( itemName ) ;
			deployMetaData.setNamespace( namespace ) ;
			return deployMetaData ;

		}

		private void deployment_delete ( String deploymentName , String namespace )
			throws Exception {

			var deleteOptions = new V1DeleteOptions( ) ;
			deleteOptions.setPropagationPolicy( propagationPolicy_foreground ) ;

			logger.debug( "deleting: {}, options: {}", deploymentName, deleteOptions.toString( ) ) ;

//			ObjectNode deleteJson = null ;
			try {

				var deleteResult = apiApps.deleteNamespacedDeployment(
						deploymentName, namespace,
						pretty_true,
						dryRun_null, gracePeriodSeconds_0,
						orphanDependents_null,
						propagationPolicy_foreground,
						deleteOptions ) ;

				logger.info( "deleteResult: {} ", gsonWithExclusions.toJson( deleteResult ) ) ;

//			} catch ( JsonSyntaxException e ) {
//				deleteJson = csapKubernetes.handleKubernetesApiBug( e ) ;

			} catch ( Exception e ) {

				logger.error( "Failed to delete: {}",
						CSAP.buildCsapStack( e ), e ) ;
				throw e ;

			}

			waitForDeployDeletedComplete( deploymentName, namespace ) ;
			var latestDeployNames = deploymentNames( namespace ) ;
			assertThat( latestDeployNames ).isEmpty( ) ;

//			assertThat( deleteJson ).isNotNull( ) ;
//
//			assertThat( deleteJson.toString( ) )
//					.as( "Deletek8s bug" )
//					.contains( "Delete Succeeded - known issue" ) ;

		}

	}

	private int waitForDeployDeletedComplete ( String name , String namespace ) {

		var attempts = 0 ;
		var waitForIt = true ;

		while ( waitForIt && ( ++attempts <= maxTries ) ) {

			try {

				var latest = deploymentNames( namespace ) ;

				if ( latest.contains( name ) ) {

					logger.info( "Attempt: {} of max {} -  Waiting for deployment '{}' to be removed", attempts,
							maxTries, name ) ;
					sleep( 2 ) ;

				} else {

					logger.info( "name space not found" ) ;
					waitForIt = false ;

				}

			} catch ( Exception e ) {

				logger.info( "Failed listing namespaces {}", CSAP.buildCsapStack( e ) ) ;

			}

		}

		return attempts ;

	}

	private List<String> deploymentNames ( String namespace ) {

		var deployNames = (List<String>) new ArrayList<String>( ) ;

		try {

			var deploymentList = apiApps.listNamespacedDeployment(
					namespace,
					pretty_true,
					allowWatchBookmarks_null,
					_continue_null,
					fieldSelector_null,
					labelSelector_null,
					limit_500, resourceVersion_null,
					resourceVersionMatch_null,
					timeoutSeconds_10,
					watch_null ) ;

			logger.debug( "deploymentList details: {} ", gsonWithExclusions.toJson( deploymentList ) ) ;

			deployNames = deploymentList.getItems( ).stream( )
					.map( V1Deployment::getMetadata )
					.map( V1ObjectMeta::getName )
					.collect( Collectors.toList( ) ) ;

		} catch ( Exception e ) {

			logger.error( "Failed to list deployments: {}",
					CSAP.buildCsapStack( e ) ) ;

		}

		logger.info( "deployment names: {} ", deployNames ) ;

		return deployNames ;

	}

	@Nested
	@TestInstance ( TestInstance.Lifecycle.PER_CLASS )
	@DisplayName ( "Stateless tests" )
	class Stateless {

		@Test
		public void list_namespaces ( )
			throws Exception {

			List<String> nsNames = namespaces( ) ;

			assertThat( nsNames.toString( ) )
					.as( "initial name spaces" )
					.contains( "default", "kube-system" ) ;

		}

		@Test
		public void list_jobs ( )
			throws Exception {

			var jobs = batchV1.listJobForAllNamespaces( null, null, null, null, null, null, null, null, null, null ) ;

			logger.info( "jobs details: {} ", gsonWithExclusions.toJson( jobs ) ) ;

			List<String> jobNames = jobs.getItems( ).stream( )
					.map( V1Job::getMetadata )
					.map( V1ObjectMeta::getName )
					.collect( Collectors.toList( ) ) ;

			logger.info( "jobNames: {} ", jobNames ) ;

			// assertThat( jobNames.size() )
			// .as( "initial name spaces" )
			// .isGreaterThan( 0 ) ;

		}

		@Test
		public void list_cron_jobs ( )
			throws Exception {

			V1beta1CronJobList cronJobs = batchV1Beta1.listCronJobForAllNamespaces( null, null, null, null, null, null,
					null,
					null,
					null, null ) ;

			logger.info( "cronJobs details: {} ", gsonWithExclusions.toJson( cronJobs ) ) ;

			List<String> cronJobNames = cronJobs.getItems( ).stream( )
					.map( V1beta1CronJob::getMetadata )
					.map( V1ObjectMeta::getName )
					.collect( Collectors.toList( ) ) ;

			logger.info( "cronJobNames: {} ", cronJobNames ) ;

			assertThat( cronJobNames.size( ) )
					.isGreaterThan( 0 ) ;

		}

		@Test
		public void list_nodes ( )
			throws Exception {

			V1NodeList nodeList = apiV1.listNode( null, null, null, null, null, null, null, null, null, null ) ;

			logger.debug( "serviceList details: {} ", gsonWithExclusions.toJson( nodeList ) ) ;

			List<String> nodeNames = nodeList.getItems( ).stream( )
					.map( V1Node::getMetadata )
					.map( V1ObjectMeta::getName )
					.collect( Collectors.toList( ) ) ;

			logger.info( "nodeNames names: {} ", nodeNames ) ;

			assertThat( nodeNames.size( ) )
					.as( "initial name spaces" )
					.isGreaterThan( 0 ) ;

			V1Node dev4 = apiV1.readNodeStatus( "csap-dev04.***REMOVED***", null ) ;
			// s.getStatus().
			// dev4.getStatus().al

			logger.info( "dev4.getStatus : {} ", gsonWithExclusions.toJson( dev4.getStatus( ) ) ) ;

		}

		@Test
		public void services_list ( )
			throws Exception {

			logger.info( CsapApplication.testHeader( "Creating deployment" ) ) ;

			V1ServiceList serviceList = apiV1.listServiceForAllNamespaces(
					null, null, null, null, null, null, null, null, null, null ) ;

			logger.debug( "serviceList details: {} ", gsonWithExclusions.toJson( serviceList ) ) ;

			List<String> serviceNames = serviceList.getItems( ).stream( )
					.map( V1Service::getMetadata )
					.map( V1ObjectMeta::getName )
					.collect( Collectors.toList( ) ) ;

			logger.info( "service names: {} ", serviceNames ) ;

			assertThat( serviceNames.toString( ) )
					.as( "initial name spaces" )
					.contains( "kubernetes", "kube-dns" ) ;

		}

		@Test
		public void ingress_list ( )
			throws Exception {

			logger.info( CsapApplication.testHeader( "Creating deployment" ) ) ;

			NetworkingV1beta1IngressList ingressList = networkingApi.listIngressForAllNamespaces(
					null, null, null, null, null, null, null, null, null, null ) ;
			logger.info( "ingressList details: {} ", gsonWithExclusions.toJson( ingressList ) ) ;
			//
			List<String> ingressNames = ingressList.getItems( ).stream( )
					.map( NetworkingV1beta1Ingress::getMetadata )
					.map( V1ObjectMeta::getName )
					.collect( Collectors.toList( ) ) ;

			logger.info( "ingressNames names: {} ", ingressNames ) ;

		}

		@Test
		public void verify_generic_call ( )
			throws Exception {

			ApiDirect kubernetesDirect = new ApiDirect( ) ;

			var nodeInfo = kubernetesDirect.getJson( "/api/v1/nodes", apiClient, getJsonMapper( ) ) ;
			assertThat( nodeInfo.at( "/items/0/metadata/name" ).asText( ) ).contains( "csap-dev" ) ;

			var podMetrics = kubernetesDirect.getJson( "/apis/metrics.k8s.io/v1beta1/pods", apiClient,
					getJsonMapper( ) ) ;
			logger.info( "podInfo: {}", CSAP.jsonPrint( podMetrics ) ) ;

			var firstCpu = podMetrics.at( "/items/0/containers/0/usage/cpu" ).asText( ) ;
			logger.info( "firstCpu: {}", firstCpu ) ;
			assertThat( podMetrics.at( "/items/0/containers/0/usage" ).has( "cpu" ) ).isTrue( ) ;

		}

		@Test
		public void verify_generic_resource ( )
			throws Exception {

			ApiDirect kubernetesDirect = new ApiDirect( ) ;

			var apiResources = kubernetesDirect.getJson( "/apis", apiClient, getJsonMapper( ) ) ;

			logger.info( "resourceInfo: {}", CSAP.jsonPrint( apiResources ) ) ;

			var availableApisSummary = CSAP.jsonStream( apiResources.path( "groups" ) )
					.map( group -> group.at( "/preferredVersion/groupVersion" ).asText( ) )
					.collect( Collectors.joining( "\n" ) ) ;

			logger.info( "availableApisSummary: {}", availableApisSummary ) ;

			var availableApis = CSAP.jsonStream( apiResources.path( "groups" ) )
					.map( group -> group.at( "/preferredVersion/groupVersion" ).asText( ) )
					.collect( Collectors.toList( ) ) ;

			availableApis.add( "/api/v1/" ) ;

			assertThat( availableApis ).contains( "apps/v1" ) ;

			var allApiResources = availableApis.stream( )
					.map( resourceApi -> {

						List<String> resources ;

						try {

							if ( resourceApi.charAt( 0 ) != '/' ) {

								resourceApi = "/apis/" + resourceApi ;

							}

							var resourceDefinition = kubernetesDirect.getJson( resourceApi, apiClient,
									getJsonMapper( ) ) ;
							// apiNotes = CSAP.jsonPrint( resourceDefinition ) ;

							resources = CSAP.jsonStream( resourceDefinition.path( "resources" ) )
									.map( resource -> resource.path( "name" ).asText( ) )
									.collect( Collectors.toList( ) ) ;

						} catch ( Exception e ) {

							logger.warn( "failed to find resources{}", CSAP.buildCsapStack( e ) ) ;
							resources = List.of( resourceApi + "-failed-to-find-resources" ) ;

						}

						// if ( !resourceApi.contains( "node" ) ) {
						// apiNotes = "\n Skipping " + resourceApi ;
						// }
						return CSAP.padLine( resourceApi ) + resources.toString( ) ;

					} )
					.collect( Collectors.toList( ) ) ;

			logger.info( "allApiResources: {}", allApiResources ) ;
			assertThat( allApiResources.toString( ) ).contains( "namespaces" ) ;

		}

		@Test
		public void list_stateful_sets ( )
			throws Exception {

			V1StatefulSetList statefulSets = apiApps.listStatefulSetForAllNamespaces(
					null, null, null, null, null, null, null, null, null, null ) ;

			logger.info( "statefulSets: {} ", gsonWithExclusions.toJson( statefulSets ) ) ;

			List<String> statefuleSetNames = statefulSets.getItems( ).stream( )
					.map( V1StatefulSet::getMetadata )
					.map( V1ObjectMeta::getName )
					.collect( Collectors.toList( ) ) ;

			logger.info( "statefulSets names: {} ", statefuleSetNames ) ;

			// assertThat( statefuleSetNames.toString() )
			// .as( "replicatSetNames" )
			// .contains( "calico-kube-controllers", "kubernetes-dashboard" ) ;

		}

		@Test
		public void list_replicaSets ( )
			throws Exception {

			V1ReplicaSetList replicatSets = apiApps.listReplicaSetForAllNamespaces(
					null, null, null, null, null, null, null, null, null, null ) ;

			logger.info( "replicatSets: {} ", gsonWithExclusions.toJson( replicatSets ) ) ;

			List<String> replicatSetNames = replicatSets.getItems( ).stream( )
					.map( V1ReplicaSet::getMetadata )
					.map( V1ObjectMeta::getName )
					.collect( Collectors.toList( ) ) ;

			logger.info( "nameSpace names: {} ", replicatSetNames ) ;

			assertThat( replicatSetNames.toString( ) )
					.as( "replicatSetNames" )
					.contains( "calico-kube-controllers", "coredns" ) ;

		}

		@Test
		public void statefulset_list ( )
			throws Exception {

			V1StatefulSetList statefulsetList = apiApps.listStatefulSetForAllNamespaces(
					null, null, null, null, null, null, null, null, null, null ) ;

			logger.info( "serviceList details: {} ", gsonWithExclusions.toJson( statefulsetList ) ) ;

			List<String> statefulesetNames = statefulsetList.getItems( ).stream( )
					.map( V1StatefulSet::getMetadata )
					.map( V1ObjectMeta::getName )
					.collect( Collectors.toList( ) ) ;

			logger.info( "statefulesetNames: {} ", statefulesetNames ) ;

			// assertThat( statefulesetNames.toString() )
			// .as( "initial name spaces" )
			// .contains( "heapster", "coredns" ) ;

		}

		@Test
		public void deployment_list ( )
			throws Exception {

			V1DeploymentList deploymentList = apiApps.listDeploymentForAllNamespaces(
					null, null, null, null, null, null, null, null, null, null ) ;

			logger.info( "serviceList details: {} ", gsonWithExclusions.toJson( deploymentList ) ) ;

			List<String> deployNames = deploymentList.getItems( ).stream( )
					.map( V1Deployment::getMetadata )
					.map( V1ObjectMeta::getName )
					.collect( Collectors.toList( ) ) ;

			logger.info( "deployment names: {} ", deployNames ) ;

			assertThat( deployNames.toString( ) )
					.as( "initial name spaces" )
					.contains( "calico-kube-controllers", "coredns" ) ;

		}

		@Test
		public void persistent_claim_list ( )
			throws Exception {

			V1PersistentVolumeClaimList persistentVolumes = apiV1.listPersistentVolumeClaimForAllNamespaces( null, null,
					null, null,
					null, null, null,
					null, null, null ) ;

			List<String> pvClaimNames = persistentVolumes.getItems( ).stream( )
					.map( V1PersistentVolumeClaim::getMetadata )
					.map( V1ObjectMeta::getName )
					.collect( Collectors.toList( ) ) ;

			logger.info( "pvClaimNames: {} ", pvClaimNames ) ;

			// assertThat( storageClasses.toString() )
			// .as( "system pods" )
			// .contains( "kube-controller-manager-", "coredns-", "kube-proxy" ) ;

		}

		@Test
		public void persistent_volume_list ( )
			throws Exception {

			V1PersistentVolumeList persistentVolumes = apiV1.listPersistentVolume( null, null, null, null, null, null,
					null, null,
					null,
					null ) ;

			List<String> pvNames = persistentVolumes.getItems( ).stream( )
					.map( V1PersistentVolume::getMetadata )
					.map( V1ObjectMeta::getName )
					.collect( Collectors.toList( ) ) ;

			logger.info( "persistentVolumes: {} ", pvNames ) ;

			// assertThat( storageClasses.toString() )
			// .as( "system pods" )
			// .contains( "kube-controller-manager-", "coredns-", "kube-proxy" ) ;

		}

		@Test
		public void storage_class_list ( )
			throws Exception {

			V1StorageClassList podList = storageV1.listStorageClass( null, null, null, null, null, null, null, null,
					null,
					null ) ;

			List<String> storageClasses = podList.getItems( ).stream( )
					.map( V1StorageClass::getMetadata )
					.map( V1ObjectMeta::getName )
					.collect( Collectors.toList( ) ) ;

			logger.info( "storageClasses: {} ", storageClasses ) ;

			// assertThat( storageClasses.toString() )
			// .as( "system pods" )
			// .contains( "kube-controller-manager-", "coredns-", "kube-proxy" ) ;

		}

		@Test
		public void pod_list_raw ( )
			throws Exception {

			var kubernetesDirect = new ApiDirect( ) ;

			//
			var warmup = runPodTimings( kubernetesDirect, 2 ) ;

//			var podTimeingReport = runPodTimings( kubernetesDirect, 10 ) ;
//
//			logger.info( "{}", podTimeingReport ) ;

		}

		private String runPodTimings ( ApiDirect kubernetesDirect , int numLoops ) {

			var loopResults = IntStream.range( 0, numLoops )
					.mapToObj( iteration -> {

						var result = new StringBuilder( "\n" ) ;

						try {

							var jsonSample = getApplication( ).metrics( ).startTimer( ) ;

							var jsonPods = kubernetesDirect.getJson( "/api/v1/pods", apiClient, getJsonMapper( ) ) ;

							var jsonNanos = getApplication( ).metrics( ).stopTimer( jsonSample, "rawSample" ) ;
							var jsonMs = TimeUnit.NANOSECONDS.toMillis( jsonNanos ) ;

							result.append( CSAP.pad( jsonPods.path( "items" ).size( ) + " pods" ) ) ;
							result.append( CSAP.pad( "jsonMs: " + jsonMs ) ) ;

							logger.debug( "jsonPods: {} found in {} ms", jsonPods.path( "items" ).size( ), jsonMs ) ;
							logger.debug( "jsonPods: {}", jsonPods.toPrettyString( ) ) ;

							//
							//
							//

							var rawSample = getApplication( ).metrics( ).startTimer( ) ;

							var rawPods = kubernetesDirect.getRawResponse(
									apiClient,
//									"/api/v1/namespaces/kube-system/pods?limit=1",
									"/api/v1/pods?limit=1",
									null ) ;

							if ( kubernetesDirect.isLastSuccess( ) ) {
								// logger.info( "rawPods: {}", rawPods ) ;

								String jsonExp = "$.metadata.remainingItemCount" ;
								// var parsed = JsonPath.parse(rawPods).read(jsonExp, JsonNode.class) ;
								var remainingItemCount = JsonPath.parse( rawPods ).read( jsonExp, Integer.class ) ;

								var rawNanos = getApplication( ).metrics( ).stopTimer( rawSample, "rawSample" ) ;
								var rawMs = TimeUnit.NANOSECONDS.toMillis( rawNanos ) ;

								result.append( CSAP.pad( "rawMs: " + rawMs ) ) ;

								logger.info( "remainingItemCount: {} rawPods: {}  ms", remainingItemCount, rawMs ) ;

								var miniPods = getJsonMapper( ).readerFor( PodTinyListing.class )
										.readValue( rawPods ) ;

								logger.info( "miniPods: {}", miniPods ) ;

							} else {

								logger.warn( "Failed: {}", rawPods ) ;

							}

							//
							//
							//

							var parsedSample = getApplication( ).metrics( ).startTimer( ) ;

							var parsedPods = apiV1.listPodForAllNamespaces( null, null, null, null, null, null, null,
									null,
									null,
									null ) ;

							var parsedNanos = getApplication( ).metrics( ).stopTimer( parsedSample, "parsedSample" ) ;
							var kapiMs = TimeUnit.NANOSECONDS.toMillis( parsedNanos ) ;

							result.append( CSAP.pad( "kapiMs: " + kapiMs ) ) ;

							logger.debug( "rawPods: {} found in {} ms", parsedPods.getItems( ).size( ), kapiMs ) ;

						} catch ( Exception e ) {

							logger.warn( "Failed test {}", CSAP.buildCsapStack( e ) ) ;

						}

						return result.toString( ) ;

					} )
					.collect( Collectors.joining( "\n" ) ) ;

			return loopResults ;

		}

		@Test
		public void pod_list ( )
			throws Exception {

			V1PodList podList = apiV1.listPodForAllNamespaces( null, null, null, null, null, null, null, null, null,
					null ) ;

			List<String> podNames = podList.getItems( ).stream( )
					.map( V1Pod::getMetadata )
					.map( V1ObjectMeta::getName )
					.collect( Collectors.toList( ) ) ;

			logger.info( "pod count: {} , podNames: {} ", podNames.size( ), podNames ) ;

			assertThat( podNames.toString( ) )
					.as( "system pods" )
					.contains( "kube-controller-manager-", "coredns-", "kube-proxy" ) ;

			var limit = 5 ;
			podList = apiV1.listNamespacedPod( "kube-system", null, null, null, null, null, limit, null, null, null,
					null ) ;

			var systemPodNames = podList.getItems( ).stream( )
					.map( V1Pod::getMetadata )
					.map( V1ObjectMeta::getName )
					.collect( Collectors.toList( ) ) ;

			logger.info( "kube-system namespace pod count: {} , systemPodNames: {} ", systemPodNames.size( ),
					systemPodNames ) ;

			assertThat( systemPodNames.size( ) ).isGreaterThan( 3 ) ;

			// get single pod info
			var podNameFieldSelector = "metadata.name=" + systemPodNames.get( 0 ) ;
			logger.info( "Getting pod using selector: {}", podNameFieldSelector ) ;
			var podFirstList = apiV1.listNamespacedPod( "kube-system", null, null, null, podNameFieldSelector, null,
					null, null, null, null, null ) ;

			var firstPodReport = getJsonMapper( ).readTree(
					gsonWithExclusions.toJson(
							podFirstList
									.getItems( )
									.get( 0 ) ).toString( ) ) ;

			logger.info( "firstPodReport: {}", CSAP.jsonPrint( firstPodReport ) ) ;

		}

		@Test
		public void field_selectors ( )
			throws Exception {

			var podFieldSelector = "spec.nodeName=csap-dev04.***REMOVED***" ;
			var dev4PodCountRunning = apiV1.listPodForAllNamespaces(
					null, null, podFieldSelector + ",status.phase==Running", null, null, null, null, null, null, null )
					.getItems( ).size( ) ;
			var dev4PodCountNotRunning = apiV1.listPodForAllNamespaces(
					null, null, podFieldSelector + ",status.phase!=Running", null, null, null, null, null, null, null )
					.getItems( ).size( ) ;
			var allPodCount = apiV1.listPodForAllNamespaces( null, null, null, null, null, null, null, null, null,
					null )
					.getItems( )
					.size( ) ;

			logger.info( "allPodCount: {}, dev4PodCountRunning: {}, dev4PodCountNotRunning: {}", allPodCount,
					dev4PodCountRunning,
					dev4PodCountNotRunning ) ;

			// var eventFieldSelector = "source.host=csap-dev04.***REMOVED***" ;
			// var dev4EventCount = apiV1.listEventForAllNamespaces( null,
			// eventFieldSelector, null, null, null, null, null, null, null
			// ).getItems().size() ;
			var allEventCount = apiV1.listEventForAllNamespaces( null, null, null, null, null, null, null, null, null,
					null )
					.getItems( ).size( ) ;

			logger.info( "allEventCount: {}", allEventCount ) ;

		}

		@Test
		public void event_list ( )
			throws Exception {

			logger.info( CsapApplication.testHeader( ) ) ;

			var eventList = apiV1.listEventForAllNamespaces( null, null, null, null, 10, null, null, null, null,
					null ) ;

			var eventNames = eventList.getItems( ).stream( )
					.map( this::mapEventName )
					// .map( V1ObjectMeta::getName )
					// .filter( name -> name.startsWith( "test-k8s-activemq" ))
					// .map( this::mapEventName )
					.collect( Collectors.joining( "\n\t" ) ) ;

			logger.info( "eventNames: moreItems: {}\n\t{} ", eventList.getMetadata( ).getContinue( ), eventNames ) ;

			String eventMessages = eventList.getItems( ).stream( )
					.map( event -> {

						return event.getReason( ) + "\t" + event.getInvolvedObject( ).getKind( ) + "\t" + event
								.getMessage( ) ;

					} )
					.collect( Collectors.joining( "\n" ) ) ;

			logger.info( "eventMessages: {} ", eventMessages ) ;

			try {

				ObjectNode test = (ObjectNode) getJsonMapper( ).readTree( gsonWithExclusions.toJson( eventList
						.getItems( ).get(
								0 ) ) ) ;
				logger.info( "events: size:{}, first: {} ", eventList.getItems( ).size( ), CSAP.jsonPrint( test ) ) ;

				var eventCount = eventList.getItems( ).stream( )
						.map( CoreV1Event::getCount )
						.filter( Objects::nonNull )
						.mapToInt( Integer::intValue )
						.sum( ) ;

				logger.info( "eventCount: {} ", eventCount ) ;

			} catch ( Exception e ) {

				logger.info( "Failed iterating events: {}", CSAP.buildCsapStack( e ) ) ;

			}

			logger.info( "eventNames: moreItems: {}\n\t{} ", eventList.getMetadata( ).getContinue( ), eventNames ) ;

		}

		private String mapEventName ( CoreV1Event event ) {

			var name = event.getMetadata( ).getName( ) ;
			var mappedName = name.split( Pattern.quote( "." ) )[0] ;

			var involveType = event.getInvolvedObject( ).getKind( ) ;

			var samplePodSuffix = "-68c79575cc-95bwz" ;

			if ( involveType.equalsIgnoreCase( "Pod" )
					&& mappedName.length( ) > samplePodSuffix.length( ) + 5 ) {

				mappedName = mappedName.substring( 0, mappedName.length( ) - samplePodSuffix.length( ) ) ;

			}

			var sampleReplicaAndJobSuffix = "-68c79575cc" ;

			if ( ( involveType.equalsIgnoreCase( "ReplicaSet" )
					|| involveType.equalsIgnoreCase( "Job" ) )
					&& mappedName.length( ) > sampleReplicaAndJobSuffix.length( ) + 5 ) {

				mappedName = mappedName.substring( 0, mappedName.length( ) - sampleReplicaAndJobSuffix.length( ) ) ;

			}

			var typeDesc = StringUtils.rightPad( "[" + involveType + "]", 14 ) ;

			return StringUtils.rightPad( typeDesc + mappedName, 60 ) + event.getMessage( ) ;

		}

		@Test
		public void pod_status ( )
			throws Exception {

			logger.info( CsapApplication.testHeader( ) ) ;

			var podList = apiV1.listPodForAllNamespaces( null, null, null, null, null, null, null, null, null, null ) ;

			var podStatus = podList.getItems( ).stream( )
					.findFirst( )
					.map( pod -> {

						return pod.getStatus( ) ;

					} )
					.orElse( null ) ;

			logger.info( "podStatus: {} ", podStatus ) ;

			List<String> ips = podList.getItems( ).stream( )
					.map( pod -> {

						try {

							ObjectNode test = (ObjectNode) getJsonMapper( ).readTree( gsonWithExclusions.toJson(
									pod ) ) ;

						} catch ( IOException e ) {

							logger.info( "Failed parsing data" ) ;

						}

						return pod.getStatus( ).getHostIP( ) ;

					} )
					.collect( Collectors.toList( ) ) ;

			logger.info( "ips: count:{}, list: {} ", ips.size( ), ips ) ;

			// assertThat( podNames.toString() )
			// .as( "system pods" )
			// .contains( "kube-controller-manager-", "coredns-", "kube-proxy" );

		}

		@Test
		public void pod_logs_as_string ( )
			throws Exception {

			logger.info( CsapApplication.testHeader( ) ) ;

			var podList = apiV1.listPodForAllNamespaces( null, null, null, null, null, null, null, null, null, null ) ;

			String targetPod = "calico-node" ;

			Optional<V1Pod> podToLogOptional = podList.getItems( ).stream( )
					.filter( pod -> pod.getMetadata( ).getName( ).startsWith( targetPod ) )
					.findFirst( ) ;

			V1Pod podToLog = podToLogOptional.get( ) ;
			logger.info( "Getting logs for: '{}'", podToLog.getMetadata( ).getName( ) ) ;

			try {

				Boolean follow = false ; // Follow the log stream of the pod
				Integer limitBytes = null ;
				Boolean timestamps = false ;

				Boolean previousTerminated = false ;
				Integer tailLines = 10 ;

				Integer sinceSeconds = null ;

				String calicoLogs = apiV1.readNamespacedPodLog(
						podToLog.getMetadata( ).getName( ),
						podToLog.getMetadata( ).getNamespace( ),
						null, // first container
						follow,
						true, // insecureSkipTLSVerifyBackend
						limitBytes,
						pretty_true, previousTerminated, sinceSeconds, tailLines, timestamps ) ;

//			      String name,
//			      String namespace,
//			      String container,
//			      Boolean follow,
//			      Boolean insecureSkipTLSVerifyBackend,
//			      Integer limitBytes,
//			      String pretty,
//			      Boolean previous,
//			      Integer sinceSeconds,
//			      Integer tailLines,
//			      Boolean timestamps)

				logger.info( "logs: {}", calicoLogs ) ;

				assertThat( calicoLogs ).isNotEmpty( ) ;

			} catch ( ApiException e ) {

				logger.error( "Exception when calling CoreV1Api#readNamespacedPodLog", CSAP.buildCsapStack( e ) ) ;

			}

		}

		@Test
		public void pod_logs_as_stream ( )
			throws Exception {

			logger.info( CsapApplication.testHeader( ) ) ;

			var podList = apiV1.listPodForAllNamespaces( null, null, null, null, null, null, null, null, null, null ) ;

			List<String> podNames = podList.getItems( ).stream( )
					.map( V1Pod::getMetadata )
					.map( V1ObjectMeta::getName )
					.collect( Collectors.toList( ) ) ;

			logger.info( "podNames: {} ", podNames ) ;

			var podLogPrefix = "calico-node" ;

			// Optional<String> matchedPod = podNames.stream()
			// .filter( fullPodName -> fullPodName.startsWith( targetPod ) )
			// .findFirst();

			Optional<V1Pod> podToLogOptional = podList.getItems( ).stream( )
					.filter( pod -> pod.getMetadata( ).getName( ).startsWith( podLogPrefix ) )
					.findFirst( ) ;

			if ( podToLogOptional.isPresent( ) ) {

				V1Pod podToLog = podToLogOptional.get( ) ;
				logger.info( "Getting logs for: '{}'", podToLog.getMetadata( ).getName( ) ) ;

				// ApiClient logApiClient = Config.fromConfig( System.getProperty( "user.home" )
				// + "/csap/kube/config" ) ;
				ApiClient logApiClient = KubernetesIntegration.buildAndCacheDesktopCredentials( logger, getConfigUrl( ),
						extractDir ) ;
				logApiClient.setReadTimeout( (int) TimeUnit.SECONDS.toMillis( 2 ) ) ;
				logger.info( "Timeout is {}ms", logApiClient.getHttpClient( ).readTimeoutMillis( ) ) ;

				PodLogs podLogsHelper = new PodLogs( logApiClient ) ;

				logger.info( "Getting filtered logs" ) ;
				String container = null ;
				container = podToLog.getSpec( ).getContainers( ).get( 0 ).getName( ) ;
				boolean timestamps = false ;
				Integer sinceSeconds = null ;
				Integer tailLines = 2000 ;
				StringBuilder logOutput = new StringBuilder( 10000 ) ;

				try ( InputStream inputStream = podLogsHelper.streamNamespacedPodLog(
						podToLog.getMetadata( ).getNamespace( ),
						podToLog.getMetadata( ).getName( ),
						container, sinceSeconds, tailLines,
						timestamps ) ) {

					logger.info( "Starting read" ) ;

					ByteArrayOutputStream bytesReadSoFar = new ByteArrayOutputStream( ) ;
					byte[] buffer = new byte[1024 * 10] ;
					int numBytesRead ;

					while ( ( numBytesRead = inputStream.read( buffer ) ) != -1 ) {

						if ( numBytesRead > 0 ) {

							logger.debug( "Reading: {}", numBytesRead ) ;
							bytesReadSoFar.write( buffer, 0, numBytesRead ) ;
							bytesReadSoFar.flush( ) ;

							logOutput.append( bytesReadSoFar.toString( "UTF-8" ) ) ;

							logger.debug( "Writing: {}, {}",
									bytesReadSoFar.toString( "UTF-8" ).length( ),
									bytesReadSoFar.toString( "UTF-8" ) ) ;

							if ( logOutput.length( ) > 8000 ) {

								logger.info( "Chunk: {}", logOutput.length( ) ) ;
								// logger.info( "Chunk: {}" , logOutput);
								logOutput.delete( 0, logOutput.length( ) ) ;

							}

						} else {

							logger.info( "Read NOthing" ) ;

						}

					}

					inputStream.close( ) ;

				} catch ( Exception e ) {

					logger.info( "Streams forever unless timeout specified {}", CSAP.buildCsapStack( e ) ) ;

				}

				logger.info( "logOutput: {}", logOutput ) ;
				//

			}

			// assertThat( podNames.toString() )
			// .as( "system pods" )
			// .contains( "kube-controller-manager-", "kube-dns", "kube-proxy" );

		}

		int readInputStreamWithTimeout (
											InputStream inputStream ,
											byte[] byteArray ,
											int timeoutMillis )
			throws Exception {

			int bufferOffset = 0 ;
			long maxTimeMillis = System.currentTimeMillis( ) + timeoutMillis ;

			while ( System.currentTimeMillis( ) < maxTimeMillis && bufferOffset < byteArray.length ) {

				int readLength = java.lang.Math.min( inputStream.available( ), byteArray.length - bufferOffset ) ;

				logger.info( "inputStream.available {}, readLength: {}",
						inputStream.available( ), readLength ) ;

				// can alternatively use bufferedReader, guarded by isReady():

				if ( readLength == 0 ) {

					Thread.sleep( 500 ) ;
					continue ;

				}

				int readResult = inputStream.read( byteArray, bufferOffset, readLength ) ;
				if ( readResult == -1 )
					break ;
				bufferOffset += readResult ;

			}

			return bufferOffset ;

		}

		@Test
		public void kubernetesVersion ( )
			throws Exception {

			VersionApi apiInstance = new VersionApi( ) ;
			VersionInfo versionInfo = apiInstance.getCode( ) ;

			logger.info( "versionInfo: {} ", versionInfo.toString( ) ) ;

			assertThat( versionInfo.toString( ) )
					.as( "gitVersion" )
					.contains( "gitVersion" ) ;

		}
	}

	public List<String> namespaces ( ) {

		List<String> nsNames ;

		try {

			var nameSpaceList = apiV1.listNamespace( null, null, null, null, null, null, null, null, null,
					null ) ;

			logger.debug( "nameSpace details: {} ", gsonWithExclusions.toJson( nameSpaceList ) ) ;

			nsNames = nameSpaceList.getItems( ).stream( )
					.map( V1Namespace::getMetadata )
					.map( V1ObjectMeta::getName )
					.collect( Collectors.toList( ) ) ;

		} catch ( ApiException e ) {

			logger.info( "Failed getting namespaces" ) ;
			nsNames = List.of( ) ;

		}

		logger.info( "nameSpace names: {} ", nsNames ) ;
		return nsNames ;

	}

	final public static int maxTries = 100 ;

	public void namespace_delete ( String namespace ) {

		logger.info( "Deleting: {} ", namespace ) ;

		V1DeleteOptions deleteOptions = new V1DeleteOptions( ) ;
		deleteOptions.setPropagationPolicy( Propogation_Policy.foreground.apiValue( ) ) ;

		try {

			var deleteNamespaceResult = apiV1.deleteNamespace(
					namespace, pretty_true, dryRun_null,
					gracePeriodSeconds_0,
					orphanDependents_null, propagationPolicy_foreground,
					deleteOptions ) ;
			logger.info( "deleteNamespaceResult: {}", deleteNamespaceResult ) ;

		} catch ( Exception e ) {

			logger.error( "Known bug on delete: {}", CSAP.buildCsapStack( e ) ) ;

		}

		var attempts = waitForNamespaceDeletedComplete( namespace ) ;

		Assumptions.assumeTrue( attempts < maxTries ) ;

	}

	private int waitForNamespaceDeletedComplete ( String namespace ) {

		var attempts = 0 ;
		var waitForIt = true ;

		while ( waitForIt && ( ++attempts <= maxTries ) ) {

			try {

				var latest = namespaces( ) ;

				if ( latest.size( ) == 0
						|| latest.contains( namespace ) ) {

					logger.info( "Attempt: {} of max {} -  Waiting for namespace '{}' to be removed", attempts,
							maxTries, namespace ) ;
					sleep( 2 ) ;

				} else {

					logger.info( "name space not found" ) ;
					waitForIt = false ;

				}

			} catch ( Exception e ) {

				logger.info( "Failed listing namespaces {}", CSAP.buildCsapStack( e ) ) ;

			}

		}

		return attempts ;

	}

	public void sleep ( int seconds ) {

		try {

			logger.info( "Sleeping: {} second", seconds ) ;
			TimeUnit.SECONDS.sleep( seconds ) ;

		} catch ( InterruptedException e ) {

		}

	};

	public boolean abortTests = false ;

	public void namespace_create ( String namespace , boolean waitForIt ) {

		logger.info( "Creating: {} ", namespace ) ;
		V1Namespace body = new V1Namespace( ) ;
		V1ObjectMeta metadata = new V1ObjectMeta( ) ;
		metadata.setName( namespace ) ;
		body.setMetadata( metadata ) ;

		try {

			var includeUninitialized = false ;
			String pretty = "true" ;
			String dryRun = null ;

			V1Namespace nameSpaceResult = create_namespace( body ) ;

			logger.debug( "nameSpaceResult: {}", nameSpaceResult ) ;

		} catch ( Exception e ) {

			logger.info( "Failed creating namespace: {}, {}", namespace, CSAP.buildCsapStack( e ) ) ;
			abortTests = true ;
			sleep( 2 ) ;
			Assumptions.assumeTrue( false, "Failed creating namespace" ) ;

		}

		int attempts = 0 ;

		while ( waitForIt && ( ++attempts <= maxTries ) ) {

			try {

				if ( ! namespaces( ).contains( namespace ) ) {

					logger.info( "Query: {} Waiting for namespace '{}' to be created", attempts, namespace ) ;
					sleep( 2 ) ;

				} else {

					logger.info( "name space created" ) ;
					waitForIt = false ;

				}

			} catch ( Exception e ) {

				logger.info( "Failed listing namespaces {}", CSAP.buildCsapStack( e ) ) ;

			}

		}

		Assumptions.assumeTrue( attempts < maxTries ) ;

	}

	public boolean wait_for_pod_running ( String podName , String podNamespace ) {

		logger.info( "waiting for : {} to be started", podName ) ;

		V1DeleteOptions deleteOptions = new V1DeleteOptions( ) ;
		deleteOptions.setPropagationPolicy( Propogation_Policy.foreground.apiValue( ) ) ;

		boolean waitForIt = true ;

		int attempts = 0 ;

		while ( waitForIt && ( ++attempts <= maxTries ) ) {

			try {

				var podList = apiV1.listNamespacedPod( podNamespace, null, null, null, null, null, null, null,
						null, null, null ) ;

				String podInfo = podList.getItems( ).stream( )
						.map( v1Pod -> "name: " + v1Pod.getMetadata( ).getName( ) + "\t phase: \t" + v1Pod.getStatus( )
								.getPhase( ) )
						.collect( Collectors.joining( "\n" ) ) ;

				var podStatusOptional = podList.getItems( ).stream( )
						.filter( v1Pod -> v1Pod.getMetadata( ).getName( ).startsWith( podName ) )
						.findFirst( )
						.map( V1Pod::getStatus ) ;

				var state = "not-found" ;

				if ( podStatusOptional.isPresent( ) ) {

					state = podStatusOptional.get( ).getPhase( ) ;

				}

				if ( ! state.toLowerCase( ).equals( "running" ) ) {

					logger.info( "Attempt {} of max {} : Waiting for pod '{}' to be running: {}, {}", attempts,
							maxTries, podName,
							state, podInfo ) ;
					sleep( 2 ) ;

				} else {

					logger.info( "Found pod, and it is running: {}", podName ) ;
					waitForIt = false ;

				}

			} catch ( Exception e ) {

				logger.info( "Failed listing pods {}", CSAP.buildCsapStack( e ) ) ;
				sleep( 2 ) ;

			}

		}

		// Assumptions.assumeTrue( attempts < maxTries ) ;

		return attempts < maxTries ;

	}

	private String jsonAt ( String json , String jsonPtr ) {

		String result = "notFound" ;

		try {

			result = getJsonMapper( ).readTree( json ).at( jsonPtr ).asText( ) ;

		} catch ( IOException e ) {

			logger.error( CSAP.buildCsapStack( e ) ) ;

		}

		return result ;

	}

	private void logErrors ( Exception e , String spec ) {

		if ( e instanceof ApiException ) {

			ApiException k8sException = (ApiException) e ;
			logger.error( "Error: {} - {} \n spec: {}, \n  CSAP Stack: {}",
					k8sException.getMessage( ),
					jsonAt( k8sException.getResponseBody( ), "/message" ),
					spec,
					CSAP.buildCsapStack( k8sException ) ) ;

		} else {

			logger.error( "Api Failure: {} reason: {}",
					spec,
					CSAP.buildCsapStack( e ), e ) ;

		}

	}

	public CoreV1Api getApiV1 ( ) {

		return apiV1 ;

	}

	public void setApiV1 ( CoreV1Api apiV1 ) {

		KubernetesApiTests.apiV1 = apiV1 ;

	}

	public String getConfigUrl ( ) {

		return configUrl ;

	}

	public void setConfigUrl ( String configUrl ) {

		this.configUrl = configUrl ;

	}
}
