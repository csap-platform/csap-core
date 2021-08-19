package org.csap.agent.container.kubernetes ;

import java.io.File ;
import java.io.FileNotFoundException ;
import java.io.FileOutputStream ;
import java.io.IOException ;
import java.io.InputStream ;
import java.io.OutputStream ;
import java.io.UnsupportedEncodingException ;
import java.net.URLDecoder ;
import java.nio.charset.StandardCharsets ;
import java.time.LocalDateTime ;
import java.time.OffsetDateTime ;
import java.time.ZoneId ;
import java.time.format.DateTimeFormatter ;
import java.util.ArrayList ;
import java.util.Arrays ;
import java.util.HashMap ;
import java.util.List ;
import java.util.Map ;
import java.util.Objects ;
import java.util.Optional ;
import java.util.concurrent.Executors ;
import java.util.concurrent.ScheduledExecutorService ;
import java.util.concurrent.TimeUnit ;
import java.util.regex.Matcher ;
import java.util.regex.Pattern ;
import java.util.stream.Collectors ;

import javax.servlet.http.HttpServletResponse ;

import org.apache.commons.io.FileUtils ;
import org.apache.commons.lang3.StringUtils ;
import org.csap.agent.KubernetesConfiguration ;
import org.csap.agent.KubernetesSettings ;
import org.csap.agent.container.DockerIntegration ;
import org.csap.agent.container.DockerJson ;
import org.csap.agent.linux.ZipUtility ;
import org.csap.agent.model.Application ;
import org.csap.agent.model.ContainerState ;
import org.csap.agent.model.ServiceInstance ;
import org.csap.agent.services.OsManager ;
import org.csap.agent.services.OsProcessMapper ;
import org.csap.agent.ui.explorer.KubernetesExplorer ;
import org.csap.helpers.CSAP ;
import org.csap.helpers.CsapApplication ;
import org.csap.helpers.CsapSimpleCache ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.beans.factory.annotation.Autowired ;
import org.springframework.boot.web.client.RestTemplateBuilder ;
import org.springframework.http.HttpEntity ;
import org.springframework.http.HttpHeaders ;
import org.springframework.http.HttpMethod ;
import org.springframework.http.HttpStatus ;
import org.springframework.http.MediaType ;
import org.springframework.http.ResponseEntity ;
import org.springframework.http.converter.ByteArrayHttpMessageConverter ;
import org.springframework.web.client.RestTemplate ;

import com.fasterxml.jackson.annotation.JsonInclude.Include ;
import com.fasterxml.jackson.databind.JsonNode ;
import com.fasterxml.jackson.databind.ObjectMapper ;
import com.fasterxml.jackson.databind.node.ArrayNode ;
import com.fasterxml.jackson.databind.node.ObjectNode ;
import com.google.gson.JsonSyntaxException ;
import com.jayway.jsonpath.JsonPath ;

import io.kubernetes.client.openapi.ApiCallback ;
import io.kubernetes.client.openapi.ApiClient ;
import io.kubernetes.client.openapi.ApiException ;
import io.kubernetes.client.openapi.Pair ;
import io.kubernetes.client.openapi.apis.AppsV1Api ;
import io.kubernetes.client.openapi.apis.CoreV1Api ;
import io.kubernetes.client.openapi.apis.RbacAuthorizationV1Api ;
import io.kubernetes.client.openapi.apis.StorageV1Api ;
import io.kubernetes.client.openapi.apis.VersionApi ;
import io.kubernetes.client.openapi.models.CoreV1Event ;
import io.kubernetes.client.openapi.models.CoreV1EventList ;
import io.kubernetes.client.openapi.models.V1DeleteOptions ;
import io.kubernetes.client.openapi.models.V1Namespace ;
import io.kubernetes.client.openapi.models.V1Node ;
import io.kubernetes.client.openapi.models.V1NodeStatus ;
import io.kubernetes.client.openapi.models.V1ObjectMeta ;
import io.kubernetes.client.openapi.models.V1Pod ;
import io.kubernetes.client.openapi.models.V1PodList ;
import io.kubernetes.client.openapi.models.V1Service ;
import io.kubernetes.client.openapi.models.VersionInfo ;
import io.kubernetes.client.util.Config ;
import io.micrometer.core.instrument.Timer ;
import okhttp3.Call ;
import okhttp3.ConnectionPool ;
import okhttp3.OkHttpClient ;
import okhttp3.Response ;
import okhttp3.logging.HttpLoggingInterceptor ;
import okhttp3.logging.HttpLoggingInterceptor.Level ;

/**
 *
 * See <a href=
 * "https://github.com/kubernetes-client/java/blob/master/kubernetes/README.md">client
 * docs</a>
 *
 * @author peter.nightingale
 *
 */
public class KubernetesIntegration {

	public static final String CSAP_KUBERNETES_METER = "csap.container.kubernetes-" ;
	public static final String SUMMARY_TIMER = CSAP_KUBERNETES_METER + "summary" ;

	public static final String CSAP_DEF_INGRESS_HOST = "ingress_host" ;
	public static final String CSAP_DEF_INGRESS_PORT = "ingress_port" ;

	public static final String INGRESS_NGINX_SERVICE = "ingress-nginx" ;

	static final Logger logger = LoggerFactory.getLogger( KubernetesIntegration.class ) ;
	static final int gracePeriodSeconds = 0 ;

	public static final String CLI_COMMAND = "kubectl" ;
	final public static String DEFAULT_SERVICE_NAME = "kubelet" ;
	final private static String COMMON_SERVICE_NAME_PATTERN = "(.*)" + DEFAULT_SERVICE_NAME + "(.*)" ;
	private String discoveredName = "not-found" ;

	// public static final String CSAP_KUBERNETES_SERVICE_NAME_PORT =
	// COMMON_SERVICE_NAME_PATTERN + "_8014" ;

	private KubernetesSettings settings ;
	private KubernetesConfiguration kubernetesConfig ;
	ApiDirect kubernetesDirect = new ApiDirect( ) ;

	private ListingsBuilder listingsBuilder ;
	private MetricsBuilder metricsBuilder ;
	private ReportsBuilder reportsBuilder ;
	private SpecBuilder specBuilder ;

	ObjectMapper jsonMapper ;
	ObjectMapper jsonExclusionMapper ;

	public KubernetesIntegration ( KubernetesSettings settings, KubernetesConfiguration kubernetesConfig,
			ObjectMapper jsonMapper ) {

		this.settings = settings ;
		this.kubernetesConfig = kubernetesConfig ;
		this.jsonMapper = jsonMapper ;

		jsonExclusionMapper = new ObjectMapper( ) ;
		jsonExclusionMapper.setSerializationInclusion( Include.NON_NULL ) ;

		listingsBuilder = new ListingsBuilder( this, jsonMapper ) ;

		metricsBuilder = new MetricsBuilder( this, jsonMapper ) ;
		reportsBuilder = new ReportsBuilder( this, metricsBuilder, jsonMapper ) ;
		specBuilder = new SpecBuilder( this, metricsBuilder, listingsBuilder, jsonMapper ) ;

	}

	static ApiClient desktopTestApiClient = null ;

	public static ApiClient buildAndCacheDesktopCredentials ( Logger logger , String credentialUrl , File extractDir )
		throws IOException ,
		FileNotFoundException {

		if ( Objects.nonNull( desktopTestApiClient ) )
			return desktopTestApiClient ;

		logger.info( "Getting kubernetes credentials\n\t from: {}, \n\t saved in: {}",
				credentialUrl, extractDir ) ;
		ApiClient theApiClient = null ;

		RestTemplate restTemplate = ( new RestTemplateBuilder( ) )
				.additionalMessageConverters( new ByteArrayHttpMessageConverter( ) )
				.build( ) ;

		HttpHeaders headers = new HttpHeaders( ) ;
		headers.setAccept( Arrays.asList( MediaType.APPLICATION_OCTET_STREAM ) ) ;
		HttpEntity<String> entity = new HttpEntity<>( headers ) ;
		ResponseEntity<byte[]> response = restTemplate.exchange( credentialUrl, HttpMethod.GET, entity, byte[].class,
				"1" ) ;

		if ( response.getStatusCode( ).equals( HttpStatus.OK ) ) {

			File testZipFile = new File( System.getProperty( "user.home" ), "agent-junit.zip" ) ;
			FileUtils.deleteQuietly( testZipFile ) ;

			try ( OutputStream opStream = new FileOutputStream( testZipFile ) ) {

				byte[] zipBytes = response.getBody( ) ;
				opStream.write( zipBytes ) ;
				opStream.flush( ) ;

			}

			ZipUtility.unzip( testZipFile, extractDir ) ;

			//

			File extractFile = new File( extractDir, "config" ) ;

			if ( credentialUrl.contains( "centos1.***REMOVED***" ) ) {

				String kubernetesCredentials = FileUtils.readFileToString( extractFile ) ;
				var updatedCredentials = kubernetesCredentials.replaceAll( "server: https.*",
						"server: https://centos1.***REMOVED***:6443" ) ;
				logger.warn( CsapApplication.testHeader(
						"Found centos1.lab - replacing ip with centos1.lab.***REMOVED***" ) ) ;
				// FileUtils.deleteQuietly( extractFile ) ;
				FileUtils.write( extractFile, updatedCredentials, false ) ;
				logger.debug( CsapApplication.testHeader(
						"Found centos1.lab - replacing ip with centos1.lab.***REMOVED***: \n {}" ), updatedCredentials ) ;

			}

			// String stringFromZipUnzippedAndRead = FileUtils.readFileToString( extractFile
			// ) ;
			logger.info( "loading: {}", extractFile.getAbsolutePath( ) ) ;
			theApiClient = Config.fromConfig( extractFile.getAbsolutePath( ) ) ;

			HttpLoggingInterceptor logging = new HttpLoggingInterceptor( ) ;
			logging.setLevel( Level.BASIC ) ;
			// logging.setLevel(Level.BODY);

			// theApiClient.getHttpClient().interceptors().add( logging ) ;

			// Customize timeouts
			OkHttpClient httpClientclient = theApiClient.getHttpClient( ).newBuilder( )
					.connectTimeout( 500, TimeUnit.MILLISECONDS )
					.writeTimeout( 5, TimeUnit.SECONDS )
					.readTimeout( 5, TimeUnit.SECONDS )
					.retryOnConnectionFailure( false )
					.connectionPool( new ConnectionPool( 10, 5L, TimeUnit.MINUTES ) )
					.build( ) ;
			theApiClient.setHttpClient( httpClientclient ) ;

		}

		return theApiClient ;

	}

	public static String getDefaultUrl ( String host ) {

		return "http://" + Application.getInstance( ).getHostUsingFqdn( host ) + ":8014/api/v1/namespaces" ;
//		return "http://" + host + "." + domainName( ) + ":8014/api/v1/namespaces/kube-system/services/" ;

	}

//	static private String domain = null ;
//
//	static public String domainName ( ) {
//
//		if ( domain != null )
//			return domain ;
//
//		try {
//
//			String parts[] = InetAddress.getLocalHost( ).getCanonicalHostName( ).split( Pattern.quote( "." ), 2 ) ;
//
//			if ( parts.length == 2 ) {
//
//				domain = parts[1] ;
//
//			} else {
//
//				domain = parts[0] ;
//
//			}
//
//		} catch ( Exception e ) {
//
//			domain = "localhost" ;
//			logger.info( "Failed getting domain name {}", CSAP.buildCsapStack( e ) ) ;
//
//		}
//
//		return domain ;
//
//	}

	public void setDiscoveredName ( String discoveredName ) {

		logger.info( "Updating kubelet service name: '{}'", discoveredName ) ;
		this.discoveredName = discoveredName ;

	}

	public String getDiscoveredServiceName ( ) {

		return discoveredName ;

	}

	static public String getServicePattern ( ) {

		return COMMON_SERVICE_NAME_PATTERN ;

	}

	void addApiPath ( JsonNode attributes , String apiType , V1ObjectMeta v1Meta ) {

		( (ObjectNode) attributes ).put( KubernetesJson.apiPath.json( ),
				getSettings( ).getApiPath(
						apiType,
						v1Meta.getNamespace( ),
						v1Meta.getName( ) ) ) ;

	}

	void addApiPath ( JsonNode attributes , String apiType , Object... params ) {

		( (ObjectNode) attributes ).put( KubernetesJson.apiPath.json( ),
				getSettings( ).getApiPath(
						apiType,
						params ) ) ;

	}

	ApiClient apiClient ( ) {

		return kubernetesConfig.apiClient( ) ;

	}

	public CoreV1Api buildV1Api ( ) {

		CoreV1Api api = new CoreV1Api( apiClient( ) ) ;
		return api ;

	}

	private boolean isAllNamespaces ( String namespace ) {

		if ( namespace == null || namespace.equals( "all" ) )
			return true ;

		return false ;

	}

	public long nodeCount ( ) {

		var api = new CoreV1Api( apiClient( ) ) ;
		var count = 0l ;

		try {

//			var nodeListing = api.listNode( null, null, null, null, null, 1, null, null, null ) ;
			var nodeListing = api.listNode(
					ListingsBuilder.pretty_null, ListingsBuilder.allowWatchBookmarks_null,
					ListingsBuilder._continue_null,
					ListingsBuilder.fieldSelector_null, ListingsBuilder.labelSelector_null,
					ListingsBuilder.limit_max,
					ListingsBuilder.resourceVersion_null, ListingsBuilder.resourceVersionMatch_null,
					ListingsBuilder.timeoutSeconds_max,
					ListingsBuilder.allowWatchBookmarks_null ) ;

			count = nodeListing.getItems( ).size( ) ;

			if ( nodeListing.getMetadata( ).getRemainingItemCount( ) != null ) {

				count += nodeListing.getMetadata( ).getRemainingItemCount( ) ;

			}

		} catch ( Exception e ) {

			logger.error( "Failed to query size: {}",
					CSAP.buildCsapStack( e ) ) ;

		}

		return count ;

	}

	String getRawPods ( String namespace , String fieldSelector ) throws ApiException , IOException {

		var podApi = "/api/v1/pods?limit=" + MAX_ITEMS_TO_RECEIVE ;

		if ( ! isAllNamespaces( namespace ) ) {

			podApi = "/api/v1/namespaces/" + namespace + "/pods?limit=" + MAX_ITEMS_TO_RECEIVE ;

		}

		List<Pair> localVarQueryParams = null ;

		if ( StringUtils.isNotEmpty( fieldSelector ) ) {

			localVarQueryParams = new ArrayList<>( ) ;
			localVarQueryParams.addAll( apiClient( ).parameterToPair( "pretty", "false" ) ) ;
			localVarQueryParams.addAll( apiClient( ).parameterToPair( "timeoutSeconds", "5" ) ) ;

			if ( StringUtils.isNotEmpty( fieldSelector ) ) {

				localVarQueryParams.addAll( apiClient( ).parameterToPair( "fieldSelector", fieldSelector ) ) ;

			}

		}

		return kubernetesDirect.getRawResponse( apiClient( ), podApi, localVarQueryParams ) ;

	}

	public ObjectNode podDelete ( String podName , String namespace ) {

		ObjectNode result ;

		// body.apiVersion( "v1" );

		try {

			V1DeleteOptions body = new V1DeleteOptions( ) ;

			CoreV1Api api = new CoreV1Api( apiClient( ) ) ;

			String pretty = "true" ;
			String dryRun = null ;
			Boolean orphanDependents = null ;
			var propagationPolicy = Propogation_Policy.foreground.apiValue( ) ;

			// V1Status
			var deleteResult = api.deleteNamespacedPod(
					podName, namespace, pretty, dryRun, gracePeriodSeconds, orphanDependents,
					propagationPolicy, body ) ;

			var deleteReport = listingsBuilder( ).buildResultReport( deleteResult ) ;
			logger.info( "result: {} ", deleteReport ) ;

			result = (ObjectNode) deleteReport ;

		} catch ( Exception e ) {

			result = buildErrorResponse( "Failed to delete deployment", e ) ;

		}

		return result ;

	}

	public void updatePodIps ( Application csapApplication ) {

		if ( Objects.isNull( apiClient( ) ) ) {

			logger.warn( "Skipping service kubernetes podIp assignment, apiClient is not initialized" ) ;
			return ;

		}

		V1PodList testPodList = null ;

		try {

			logger.debug( "Refreshing pod list" ) ;
			CoreV1Api api = new CoreV1Api( apiClient( ) ) ;
//			testPodList = api.listPodForAllNamespaces( null, null, null, null, null, null, null, null, null ) ;
			testPodList = api.listPodForAllNamespaces(
					ListingsBuilder.allowWatchBookmarks_null, ListingsBuilder._continue_null,
					ListingsBuilder.fieldSelector_null, ListingsBuilder.labelSelector_null,
					ListingsBuilder.limit_max, ListingsBuilder.pretty_null,
					ListingsBuilder.resourceVersion_null, ListingsBuilder.resourceVersionMatch_null,
					ListingsBuilder.timeoutSeconds_max,
					ListingsBuilder.allowWatchBookmarks_null ) ;

		} catch ( Exception e ) {

			logger.warn( "Failed getting pod list: {}", CSAP.buildCsapStack( e ) ) ;

		}

		V1PodList podList = testPodList ;

		if ( podList != null ) {

			csapApplication
					.getActiveProject( )
					.getServicesOnHost( csapApplication.getCsapHostName( ) )
					.filter( serviceInstance -> serviceInstance.is_cluster_kubernetes( ) )
					.forEach( serviceInstance -> {

						for ( ContainerState containerState : serviceInstance.getContainerStatusList( ) ) {

							Optional<V1Pod> matchedPod = podList.getItems( ).stream( )
									.filter( pod -> {

										// + "_" + pod.getMetadata().getNamespace() );
										// return containerName.contains( pod.getMetadata().getName() ) ;
										return containerState.getContainerName( ).contains( pod.getMetadata( )
												.getName( ) ) ;

									} )
									.findFirst( ) ;

							if ( matchedPod.isPresent( ) ) {

								V1Pod pod = matchedPod.get( ) ;
								// podIp = pod.getStatus().getPodIP() ;
								containerState.setPodIp( pod.getStatus( ).getPodIP( ) ) ;

							}

						}

					} ) ;

		}

		// return podIp ;
	}

	/**
	 *
	 * @see OsProcessMapper#container_match_check
	 */
	public boolean isPodRunning ( ServiceInstance csapService ) {

		try {

			var rawPodListing = getRawPods( null, null ) ; // really big string

			if ( kubernetesDirect.isLastSuccess( ) ) {

				MiniMappings.PodContainerNamesListing podContainerNamesListing = jsonMapper.readerFor(
						MiniMappings.PodContainerNamesListing.class )
						.readValue( rawPodListing ) ;

				logger.debug( "podContainerListing: {}", podContainerNamesListing ) ;

				JsonNode resolvedLocators = csapService.getResolvedLocators( ) ;

				// Pod Matching
				if ( resolvedLocators.isObject( ) ) {

					var servicePodMatchPattern = resolvedLocators
							.path( DockerJson.podName.json( ) )
							.asText( ) ;

					logger.info( "servicePodMatchPattern: {}", servicePodMatchPattern ) ;

					if ( StringUtils.isNotEmpty( servicePodMatchPattern ) ) {

						// containerProcess.getPodName( ).matches( podMatching )
						var podNameMatch = podContainerNamesListing.getItems( ).stream( )
								.filter( pod -> pod.name.matches( servicePodMatchPattern ) )
								.findFirst( ) ;
						return podNameMatch.isPresent( ) ;

					}

				}

				String containerNameMatch = DockerIntegration.getProcessMatch( csapService ) ;
				logger.info( "containerNameMatch: {}", containerNameMatch ) ;

				Pattern testPattern = null ;

				if ( containerNameMatch.charAt( 0 ) == '(' ) {

					testPattern = Pattern.compile( containerNameMatch ) ;

				}

				Pattern matchPattern = testPattern ;

				var podContainerNameMatch = podContainerNamesListing.getItems( ).stream( )
						.filter( pod -> {

							if ( matchPattern == null ) {

								// simple matching
								return pod.containerNames.contains( containerNameMatch ) ;

							} else {

								// complex patterns: (name1|name2|...)

								var containerPatternMatch = pod.containerNames.stream( )
										.filter( containerName -> {

											Matcher m = matchPattern.matcher( containerName ) ;
											return m.matches( ) ;

										} )
										.findFirst( ) ;

								return containerPatternMatch.isPresent( ) ;

							}

						} )
						.findFirst( ) ;

				return podContainerNameMatch.isPresent( ) ;

			} else {

				logger.warn( "Failed kuberntes call: {}", rawPodListing ) ;

			}

		} catch ( Exception e ) {

			logger.error( "Failed to get pod listing: {}",
					CSAP.buildCsapStack( e ) ) ;

		}

		return false ;

	}

	/*
	 * Used on kubernetes pod summary view to view pod container information
	 */
	public JsonNode podContainerMetricsReport ( String namespace , String podName ) {

		var podRawReports = podRawReports( namespace, podName ) ;
		var podReport = ( (ObjectNode) podRawReports.path( 0 ) ) ;
		podReport.put( "podMatches", podRawReports.size( ) ) ;

		if ( podRawReports.size( ) > 1 ) {

			logger.warn( "Unexpected size: '{}' for requested pod: {}", podRawReports.size( ), podName ) ;

		}

		var metricsUrl = "/apis/metrics.k8s.io/v1beta1/namespaces/"
				+ namespace
				+ "/pods/" + podName ;
		logger.debug( "metricsUrl: {}", metricsUrl ) ;

		try {

			var podMetrics = kubernetesDirect.getJson( metricsUrl,
					apiClient( ),
					jsonMapper ) ;

			logger.info( "podMetrics: {}", CSAP.jsonPrint( podMetrics ) ) ;
			// ( (ObjectNode) podReport ).set( "metrics", podMetrics ) ;
			var containerMetrics = podReport.putArray( "containerMetrics" ) ;

			CSAP.jsonStream( podMetrics.path( "containers" ) )

					// prometh adapter includes pod
					.filter( podContainerMetrics -> ! podContainerMetrics.path( "name" ).asText( ).equals( "POD" ) )

					.forEach( podContainerMetrics -> {

						// var containerReport = podMetrics.at( "/containers/0" ) ;
						var containerReport = containerMetrics.addObject( ) ;
						containerReport.put( "name", podContainerMetrics.path( "name" ).asText( ) ) ;

						containerReport.put( KubernetesJson.cores.json( ), CSAP.roundIt(
								metricsBuilder.metricsServerNormalizedCores( podContainerMetrics ),
								2 ) ) ;
						containerReport.put( KubernetesJson.memoryInMb.json( ),
								metricsBuilder.metricsServerMemoryInMb( podContainerMetrics ) ) ;

					} ) ;

			// set( "metrics", podMetrics.at( "/containers/usage" ) ) ;
			// logger.info( podName );
		} catch ( Exception e ) {

			logger.warn( "Failed getting podMetrics", CSAP.buildCsapStack( e ) ) ;

		}

		return podReport ;

	}

	public JsonNode podsByLabel ( String namespace , String label ) {

		var podReportByLabel = listingsBuilder( ).buildResourceReport(
				"listNamespacedPod",
				buildV1Api( ),
				namespace,
				label ) ;

		return podReportByLabel.path( "items" ) ;

	}

	public ArrayNode podRawReports ( String namespace , String podName ) {

		var podListing = jsonMapper.createArrayNode( ) ;

		String podNameFieldSelector = null ;

		if ( StringUtils.isNotEmpty( podName ) ) {

			podNameFieldSelector = "metadata.name=" + podName ;

		}

		var podReports = listingsBuilder.buildNamespacedResourceReport(
				"listPodForAllNamespaces",
				"listNamespacedPod",
				buildV1Api( ),
				namespace,
				podNameFieldSelector ) ;

//		logger.info( "podReports: {}", CSAP.jsonPrint( podReports ) );

		buildPodListing( podListing, podReports ) ;

		return podListing ;

	}

	private void buildPodListing ( ArrayNode podListing , ObjectNode podReports ) {

		CSAP.jsonStream( podReports.path( "items" ) )
				.filter( JsonNode::isObject )
				.map( podReport -> (ObjectNode) podReport )
				.forEach( podReport -> {

					ObjectNode podAmmendedReport = podListing.addObject( ) ;

					podAmmendedReport.put( DockerJson.list_label.json( ),
							podReport.path( "metadata" ).path( "name" ).asText( ) ) ;

					podAmmendedReport.put( "hostname",
							podHostName(
									podReport.path( "status" ).path( "hostIP" ).asText( ) ) ) ;

					addApiPath( podAmmendedReport,
							"pods",
							podReports.path( "metadata" ).path( "namespace" ).asText( ),
							podReports.path( "metadata" ).path( "name" ).asText( ) ) ;

					podAmmendedReport.setAll( podReport ) ;

				} ) ;

	}

	@Autowired ( required = false )
	OsManager osManager ;

	public String podHostName ( String hostIP ) {

		if ( getSettings( ).isDnsLookup( ) && osManager != null ) {

			return osManager.ipToHostName( hostIP ) ;

		} else {

			return hostIP ;

		}

	}

	public ObjectNode podCountsReport ( String namespace ) {

		var count = 0 ;
		var totalRestarts = 0 ;
		var podReport = jsonMapper.createObjectNode( ) ;

		try {

			count = listingsBuilder.countResourceItems( "listPodForAllNamespaces", "listNamespacedPod", null,
					namespace ) ;

			totalRestarts = runPodRestartReport( namespace ) ;

		} catch ( Exception e ) {

			logger.error( "Failed to get pod count: {}",
					CSAP.buildCsapStack( e ) ) ;

		}

		podReport.put( "count", count ) ;
		podReport.put( "restarts", totalRestarts ) ;

		return podReport ;

	}

	private int runPodRestartReport ( String namespace )
		throws Exception {

		var rawPods = getRawPods( namespace, null ) ;

		if ( kubernetesDirect.isLastSuccess( ) ) {

			List<Integer> counts = JsonPath.parse( rawPods ).read(
					"$.items[*].status.containerStatuses[*].restartCount" ) ;

			logger.debug( "counts: {}", counts ) ;
			var totalRestarts = counts.stream( )
					.mapToInt( Integer::intValue )
					.sum( ) ;

			return totalRestarts ;

		} else {

			logger.warn( "Failed kuberntes call: {}", rawPods ) ;
			return -1 ;

		}

	}

	public enum Propogation_Policy {

		orphan("orphan"), foreground("Foreground"), background("Background");

		private String value = "" ;

		Propogation_Policy ( String val ) {

			this.value = val ;

		}

		public String apiValue ( ) {

			return value ;

		}

	}

	public ObjectNode namespace_delete ( String namespace ) {

		logger.info( "Deleting: {} ", namespace ) ;

		ObjectNode result ;

		V1DeleteOptions deleteOptions = new V1DeleteOptions( ) ;
		deleteOptions.setPropagationPolicy( Propogation_Policy.foreground.apiValue( ) ) ;

		try {

			CoreV1Api apiV1 = new CoreV1Api( apiClient( ) ) ;

			var deleteNamespaceResult = apiV1.deleteNamespace(
					namespace, ListingsBuilder.pretty_null, ListingsBuilder.dryRun_null,
					listingsBuilder.gracePeriodSeconds_0,
					ListingsBuilder.orphanDependents_null, ListingsBuilder.propagationPolicy_foreground,
					deleteOptions ) ;

//			result = (ObjectNode) jsonMapper.readTree( apiReflection.gson( ).toJson( deleteNamespaceResult ) ) ;
			result = jsonExclusionMapper.convertValue( deleteNamespaceResult, ObjectNode.class ) ;
			logger.info( "result: {} ", result ) ;

		} catch ( Exception e ) {

			result = buildErrorResponse( "Failed to delete namespace", e ) ;

		}

		return result ;

	}

	public ObjectNode daemonSetDelete ( String serviceName , String namespace ) {

		V1DeleteOptions body = new V1DeleteOptions( ) ;
		logger.info( "body: {} ", body.toString( ) ) ;

		ObjectNode result ;

		try {

			AppsV1Api apiBeta = new AppsV1Api( apiClient( ) ) ;

			String pretty = "true" ;
			String dryRun = null ;
			Boolean orphanDependents = null ;
			var propagationPolicy = Propogation_Policy.foreground.apiValue( ) ;

			var deleteResult = apiBeta.deleteNamespacedDaemonSet(
					serviceName, namespace, pretty,
					dryRun, gracePeriodSeconds, orphanDependents, propagationPolicy, body ) ;

			var deleteReport = listingsBuilder( ).buildResultReport( deleteResult ) ;
			logger.info( "result: {} ", deleteReport ) ;
			result = (ObjectNode) deleteReport ;

		} catch ( Exception e ) {

			result = buildErrorResponse( "Failed to delete service", e ) ;

		}

		return result ;

	}

	public ObjectNode statefulSetDelete ( String serviceName , String namespace ) {

		V1DeleteOptions body = new V1DeleteOptions( ) ;
		logger.info( "body: {} ", body.toString( ) ) ;

		ObjectNode result ;

		try {

			AppsV1Api apiBeta = new AppsV1Api( apiClient( ) ) ;

			String pretty = "true" ;
			String dryRun = null ;
			Boolean orphanDependents = null ;
			var propagationPolicy = Propogation_Policy.foreground.apiValue( ) ;

			var deleteResult = apiBeta.deleteNamespacedStatefulSet(
					serviceName, namespace, pretty,
					dryRun, gracePeriodSeconds, orphanDependents, propagationPolicy, body ) ;

			var deleteReport = listingsBuilder( ).buildResultReport( deleteResult ) ;
			logger.info( "result: {} ", deleteReport ) ;

			result = (ObjectNode) deleteReport ;

		} catch ( Exception e ) {

			result = buildErrorResponse( "Failed to delete service", e ) ;

		}

		return result ;

	}

	public List<String> nameSpaces ( ) {

		CoreV1Api api = new CoreV1Api( apiClient( ) ) ;
		List<String> namespaces ;

		try {

			var namespaceList = api.listNamespace(
					ListingsBuilder.pretty_null, ListingsBuilder.allowWatchBookmarks_null,
					ListingsBuilder._continue_null,
					ListingsBuilder.fieldSelector_null, ListingsBuilder.labelSelector_null,
					ListingsBuilder.limit_max,
					ListingsBuilder.resourceVersion_null, ListingsBuilder.resourceVersionMatch_null,
					ListingsBuilder.timeoutSeconds_max,
					ListingsBuilder.watch_null ) ;

			namespaces = namespaceList.getItems( ).stream( )
					.map( V1Namespace::getMetadata )
					.map( V1ObjectMeta::getName )
					.collect( Collectors.toList( ) ) ;

		} catch ( Exception e ) {

			namespaces = Arrays.asList( "default" ) ;

		}

		return namespaces ;

	}

	public boolean areMinimumMastersReady ( int minimumMastersReady ) {

		CoreV1Api api = new CoreV1Api( apiClient( ) ) ;

		try {

//			var nodes = api.listNode( null, null, null, null, null, null, null, null, null ) ;
			var nodes = api.listNode(
					ListingsBuilder.pretty_null, ListingsBuilder.allowWatchBookmarks_null,
					ListingsBuilder._continue_null,
					ListingsBuilder.fieldSelector_null, ListingsBuilder.labelSelector_null,
					ListingsBuilder.limit_max,
					ListingsBuilder.resourceVersion_null, ListingsBuilder.resourceVersionMatch_null,
					ListingsBuilder.timeoutSeconds_max,
					ListingsBuilder.allowWatchBookmarks_null ) ;

			List<V1NodeStatus> mastersReady = nodes.getItems( ).stream( )
					.filter( node -> {

						var labels = node.getMetadata( ).getLabels( ) ;

						if ( labels.containsKey( "node-role.kubernetes.io/master" ) ) {

							return true ;

						}

						return false ;

					} )
					.map( V1Node::getStatus )
					.filter( nodeStatus -> {

						var readyCondition = nodeStatus.getConditions( ).stream( )
								.filter( condition -> condition.getReason( ).equals( "KubeletReady" ) )
								.findFirst( ) ;

						if ( readyCondition.isEmpty( ) || readyCondition.get( ).getStatus( ).equalsIgnoreCase(
								"true" ) ) {

							return true ;

						}

						return false ;

					} )
					.collect( Collectors.toList( ) ) ;

			if ( mastersReady.size( ) >= minimumMastersReady ) {

				// special hook : check for existance of credentials
				File configFile = new File( settings.getConfigFile( ) ) ;

				if ( configFile.exists( ) ) {

					return true ;

				}

			}

		} catch ( Exception e ) {

			logger.warn( "Failed checking if all masters were ready - verify kubelet available and initialized" ) ;
			logger.debug( "Failed checking if all masters were ready: {}", CSAP.buildCsapStack( e ) ) ;
			kubernetesConfig.buildApiClient( ) ;

		}

		return false ;

	}

	public ArrayNode namespaceInfo ( String namespace ) {

		ArrayNode namespaces = jsonMapper.createArrayNode( ) ;
		CoreV1Api api = new CoreV1Api( apiClient( ) ) ;

		try {

//			var namespaceList = api.listNamespace( null, null, null, null, null, null, null, null, null ) ;
			var namespaceList = api.listNamespace(
					ListingsBuilder.pretty_null, ListingsBuilder.allowWatchBookmarks_null,
					ListingsBuilder._continue_null,
					ListingsBuilder.fieldSelector_null, ListingsBuilder.labelSelector_null,
					ListingsBuilder.limit_max,
					ListingsBuilder.resourceVersion_null, ListingsBuilder.resourceVersionMatch_null,
					ListingsBuilder.timeoutSeconds_max,
					ListingsBuilder.watch_null ) ;

			if ( ! isAllNamespaces( namespace ) ) {

				ObjectNode item = namespaces.addObject( ) ;
				var namespaceItem = api.readNamespace( namespace, null, null, null ) ;

				var nsReport = listingsBuilder( ).buildResultReport( namespaceItem ) ;
				item.setAll( (ObjectNode) nsReport ) ;

			} else {

				namespaceList.getItems( ).stream( )
						.forEach( namespaceItem -> {

							ObjectNode item = namespaces.addObject( ) ;

							try {

								var nsReport = listingsBuilder( ).buildResultReport( namespaceItem ) ;
								item.setAll( (ObjectNode) nsReport ) ;

							} catch ( Exception e ) {

								namespaces.add( buildErrorResponse( "Failed to retrieve pod listing", e ) ) ;

							}

						} ) ;

			}

		} catch ( Exception e ) {

			namespaces.add( buildErrorResponse( "Failed to retrieve pod listing", e ) ) ;

		}

		return namespaces ;

	}

	public ArrayNode buildSystemSummaryListing ( String namespace ) {

		var configurationItems = jsonMapper.createArrayNode( ) ;

		try {

			var apiInstance = new VersionApi( apiClient( ) ) ;
//			var versionInfo = apiInstance.getCode( ) ;

			var api = new CoreV1Api( apiClient( ) ) ;

			//
			// for remote calls - performance is not impacted by use of reflection
			// reflection is preferred for consistency of api invokation
			//

			configurationItems.add( buildFolderNode( "", "Version",
					listingsBuilder.buildResourceReport( "getCode", apiInstance ) ) ) ;

			configurationItems.add(
					buildFolderNode( "namespaces",
							"Namespaces",
							listingsBuilder.buildResourceReport( "listNamespace", api ) ) ) ;

			configurationItems.add(
					buildFolderNode( "nodes",
							"Nodes",
							listingsBuilder.buildResourceReport( "listNode", api ) ) ) ;

			configurationItems.add(
					buildFolderNode( "persistentvolumes",
							"Storage: Persistent Volumes",
							listingsBuilder.buildResourceReport( "listPersistentVolume", api ) ) ) ;

			String methodToInvoke = "listServiceAccountForAllNamespaces" ;

			if ( ! isAllNamespaces( namespace ) ) {

				methodToInvoke = "listNamespacedServiceAccount" ;

			}

			configurationItems.add(
					buildFolderNode( "serviceaccounts",
							"Auth: Service Accounts",
							listingsBuilder.buildResourceReport( methodToInvoke, api, namespace ) ) ) ;

			configurationItems.add(
					buildFolderNode( "storageclasses",
							"Storage: Classes",
							listingsBuilder.buildResourceReport( "listStorageClass", new StorageV1Api(
									apiClient( ) ) ) ) ) ;

			//
			// Secrets are large - dynamically load
			//
			var secretFolder = configurationItems.addObject( ) ;
			secretFolder.put( DockerJson.list_label.json( ), "Auth: Secrets" ) ;
			secretFolder.put( "folder", true ) ;
			secretFolder.put( "lazy", true ) ;
			var secretAttributes = secretFolder.putObject( "attributes" ) ;
			secretAttributes.put( DockerJson.list_folderUrl.json( ), "kubernetes/api/secrets" ) ;

			//
			// auth api calls
			//
			var authApi = new RbacAuthorizationV1Api( apiClient( ) ) ;

			methodToInvoke = "listRoleForAllNamespaces" ;

			if ( ! isAllNamespaces( namespace ) ) {

				methodToInvoke = "listNamespacedRole" ;

			}

			configurationItems.add(
					buildFolderNode( "roles",
							"Auth: Roles",
							listingsBuilder.buildResourceReport( methodToInvoke, authApi, namespace ) ) ) ;

			methodToInvoke = "listRoleBindingForAllNamespaces" ;

			if ( ! isAllNamespaces( namespace ) ) {

				methodToInvoke = "listNamespacedRoleBinding" ;

			}

			configurationItems.add(
					buildFolderNode( "rolebindings",
							"Auth: Role Bindings",
							listingsBuilder.buildResourceReport( methodToInvoke, authApi, namespace ) ) ) ;

			configurationItems.add(
					buildFolderNode( "clusterroles",
							"Auth: Cluster Roles",
							listingsBuilder.buildResourceReport( "listClusterRole", authApi ) ) ) ;

			configurationItems.add(
					buildFolderNode( "clusterrolebindings",
							"Auth: Cluster Role Bindings",
							listingsBuilder.buildResourceReport( "listClusterRoleBinding", authApi ) ) ) ;

			var apiFolder = configurationItems.addObject( ) ;
			apiFolder.put( DockerJson.list_label.json( ), "Api: Providers" ) ;
			apiFolder.put( "folder", true ) ;
			apiFolder.put( "lazy", true ) ;
			var attributes = apiFolder.putObject( "attributes" ) ;
			attributes.put( DockerJson.list_folderUrl.json( ), "kubernetes/api/providers" ) ;

			var resourceFolder = configurationItems.addObject( ) ;
			resourceFolder.put( DockerJson.list_label.json( ), "Api: Resources" ) ;
			resourceFolder.put( "folder", true ) ;
			resourceFolder.put( "lazy", true ) ;
			attributes = resourceFolder.putObject( "attributes" ) ;
			attributes.put( DockerJson.list_folderUrl.json( ), "kubernetes/api/resources" ) ;

		} catch ( Exception e ) {

			configurationItems.add( buildErrorResponse( "Failed to retrieve configuration listing", e ) ) ;

		}

		return configurationItems ;

	}

	public ArrayNode apiProviders ( ) {

		ArrayNode apis = jsonMapper.createArrayNode( ) ;

		try {

			var apiResources = kubernetesDirect.getJson( "/apis", apiClient( ), jsonMapper ) ;

			var defaultGroup = ( (ArrayNode) apiResources.path( "groups" ) ).addObject( ) ;
			defaultGroup.put( "name", "/api/v1" ) ;
			defaultGroup.putObject( "preferredVersion" ).put( "groupVersion", "/api/v1" ) ;

			logger.debug( "resourceInfo: {}", CSAP.jsonPrint( apiResources ) ) ;

			CSAP.jsonStream( apiResources.path( "groups" ) )
					.forEach( group -> {

						var apiItem = apis.addObject( ) ;
						var apiName = group.at( "/name" ).asText( ) ;
						apiItem.put( DockerJson.list_label.json( ), apiName ) ;
						apiItem.put( "folder", true ) ;
						apiItem.put( "lazy", true ) ;
						var attributes = apiItem.putObject( "attributes" ) ;
						attributes.put( DockerJson.list_folderUrl.json( ), "kubernetes/api/provider/resources" ) ;

						var navPath = group.at( "/preferredVersion/groupVersion" ).asText( ) ;

						if ( navPath.charAt( 0 ) != '/' ) {

							navPath = "/apis/" + navPath ;

						}

						attributes.put( "path", navPath ) ;

					} ) ;

		} catch ( Exception e ) {

			apis.add( buildErrorResponse( "Failed to retrieve provider listing", e ) ) ;

		}

		return apis ;

	}

	public ArrayNode apiProviderResourceTypes_listing ( String providerUrl ) {

		ArrayNode resources = jsonMapper.createArrayNode( ) ;

		try {

			var resourceDefinition = kubernetesDirect.getJson( providerUrl, apiClient( ),
					jsonMapper ) ;

			CSAP.jsonStream( resourceDefinition.path( "resources" ) )
					.map( resource -> kubernetesResourceToJavaScriptListItem( providerUrl, resource, false ) )
					.filter( Objects::nonNull )
					.forEach( jsListItem -> resources.add( jsListItem ) ) ;

		} catch ( Exception e ) {

			logger.warn( "Failed getting resources: {}", CSAP.buildCsapStack( e ) ) ;
			resources.add( buildErrorResponse( "Failed to retrieve provider resources " + providerUrl, e ) ) ;

		}

		return resources ;

	}

	public ArrayNode apiResourceType_listing ( boolean addLinks ) {

		ArrayNode resources = jsonMapper.createArrayNode( ) ;

		try {

			var apiResources = kubernetesDirect.getJson( "/apis", apiClient( ), jsonMapper ) ;

			logger.debug( "resourceInfo: {}", CSAP.jsonPrint( apiResources ) ) ;

			var defaultGroup = ( (ArrayNode) apiResources.path( "groups" ) ).addObject( ) ;
			defaultGroup.put( "name", "/api/v1" ) ;
			defaultGroup.putObject( "preferredVersion" ).put( "groupVersion", "/api/v1" ) ;

			CSAP.jsonStream( apiResources.path( "groups" ) )
					.forEach( group -> {

						var groupName = group.at( "/name" ).asText( ) ;
						var groupUrl = group.at( "/preferredVersion/groupVersion" ).asText( ) ;

						try {

							if ( groupUrl.charAt( 0 ) != '/' ) {

								groupUrl = "/apis/" + groupUrl ;

							}

							var targetUrl = groupUrl ;
							var resourceDefinition = kubernetesDirect.getJson( groupUrl, apiClient( ),
									jsonMapper ) ;

							CSAP.jsonStream( resourceDefinition.path( "resources" ) )
									.map( resource -> kubernetesResourceToJavaScriptListItem( targetUrl, resource,
											addLinks ) )
									.filter( Objects::nonNull )
									.forEach( jsListItem -> resources.add( jsListItem ) ) ;

						} catch ( Exception e ) {

							var resourceItem = resources.addObject( ) ;
							logger.warn( "Failed getting resources: {}", CSAP.buildCsapStack( e ) ) ;
							resourceItem.put( DockerJson.error.json( ), "Failed getting resources for " + groupName ) ;

						}

					} ) ;

		} catch ( Exception e ) {

			resources.add( buildErrorResponse( "Failed to retrieve pod listing", e ) ) ;

		}

		return resources ;

	}

	private ObjectNode kubernetesResourceToJavaScriptListItem (
																String groupVersion ,
																JsonNode resource ,
																boolean addLinks ) {

		ObjectNode resourceItem = null ;
		var kind = resource.path( "kind" ).asText( ) ;
		var name = resource.path( "name" ).asText( ) ;
		( (ObjectNode) resource ).put( "csapLink", KubernetesExplorer.CSAP_PATH + kind ) ;

		if ( name.indexOf( '/' ) == -1 ) {

			// only root names displayed. eg. pod, not pod/status or pod/log
			resourceItem = jsonMapper.createObjectNode( ) ;
			// name is not unique
			resourceItem.put( DockerJson.list_label.json( ), kind ) ;
			resourceItem.put( "comment", groupVersion ) ;
			resourceItem.put( "folder", true ) ;
			resourceItem.put( "lazy", true ) ;
			resourceItem.set( DockerJson.list_attributes.json( ), resource ) ;

			boolean foundGets = resource.path( "verbs" ).toString( ).contains( "get" ) ;

			if ( ! foundGets ) {

				resourceItem.put( "comment", "* " + resourceItem.path( "comment" ).asText( "" ) ) ;

			} else {

				if ( addLinks ) {

					// add optional link
					( (ObjectNode) resource ).put( DockerJson.list_folderUrl.json( ), "kubernetes/api/resource" ) ;

					var path = groupVersion + "/" + name ;

					if ( resource.path( "namespaced" ).asBoolean( false ) ) {

						path = groupVersion + "NAMESPACE/" + name ;

					}

					if ( groupVersion.charAt( 0 ) != '/' ) {

						path = "/apis/" + path ;

					}

					( (ObjectNode) resource ).put( DockerJson.list_folderPath.json( ), path ) ;

				}

			}

		}

		return resourceItem ;

	}

	public String buildCliCommand ( String documentType , String resourcePath ) throws UnsupportedEncodingException {

		var decodedResourcePath = decodeUTFPath( resourcePath ) ;

		String[] resourceLinkPaths = decodedResourcePath.split( "/" ) ;

		// logger.info( "resourceLinkPath: {}, length: {}, items: {}",
		// decodedResourcePath, resourceLinkPaths.length, Arrays.asList(
		// resourceLinkPaths ) ) ;

		String namespace = "" ;
		String type = "" ;
		String source = "" ;

		if ( decodedResourcePath.startsWith( KubernetesExplorer.CSAP_PATH ) ) {

			// namespace = "--namespace=" + resourceLinkPaths[5] ;
			type = resourceLinkPaths[2] ;

			if ( resourceLinkPaths.length > 3 ) {

				namespace = "--namespace=" + resourceLinkPaths[3] ;

			}

		} else if ( resourceLinkPaths.length == 8 ) {

			namespace = "--namespace=" + resourceLinkPaths[5] ;
			type = resourceLinkPaths[6] ;
			source = resourceLinkPaths[7] ;

		} else if ( resourceLinkPaths.length == 7 ) {

			namespace = "--namespace=" + resourceLinkPaths[4] ;
			type = resourceLinkPaths[5] ;
			source = resourceLinkPaths[6] ;

		} else if ( resourceLinkPaths.length == 6 ) {

			type = resourceLinkPaths[4] ;
			source = resourceLinkPaths[5] ;

		} else if ( resourceLinkPaths.length == 5 ) {

			type = resourceLinkPaths[3] ;
			source = resourceLinkPaths[4] ;

		}

		// Support for beta api probing....
		// leave alone:
		// /apis/batch/v1beta1/namespaces/csap-logging/jobs/curator-cron-1619121600
		// supported: metrics.k8s.io/v1beta1

//		if ( resourceLinkPaths.length == 8
//				&& ! resourceLinkPaths[1].equals( "v1" ) ) {
		if ( resourceLinkPaths.length > 5
				&& resourceLinkPaths[2].equals( "metrics.k8s.io" ) ) {

			// /apis/metrics.k8s.io/v1beta1/namespaces/csap-monitoring/pods/alertmanager-main-0
			// becomes: pods.v1beta1.metrics.k8s.io

			type += "." + resourceLinkPaths[3] + "." + resourceLinkPaths[2] ;

		}

		String resourceLocator = type + " " + source ;
		// if ( type.equals( "deployments" ) ) {
		// resourceLocator = "deployment.extensions/" + source ;
		// }

		String command = namespace + " get -o=yaml " + resourceLocator ;

		if ( documentType.equals( "describe" ) ) {

			command = namespace + " describe " + resourceLocator ;

		}

		logger.info( "api path length: {} \n resourceLinkPath: {}, \n command: {}", resourceLinkPaths.length,
				decodedResourcePath, command ) ;

		return command ;

	}

	private String decodeUTFPath ( String value )
		throws UnsupportedEncodingException {

		return URLDecoder.decode( value, StandardCharsets.UTF_8.toString( ) ) ;

	}

	public ArrayNode apiResource_listing (
											String apiPathWithUserNamespace ,
											String apiPathWithPlaceholderNamespace ,
											int limit ) {

		logger.debug( "apiUrl: {}", apiPathWithUserNamespace ) ;

		ArrayNode apis = jsonMapper.createArrayNode( ) ;

		try {

			var limitUrl = apiPathWithUserNamespace + "?limit=" + limit ;
			var apiResources = kubernetesDirect.getJson( limitUrl, apiClient( ), jsonMapper ) ;

			logger.debug( "resourceInfo: {}", CSAP.jsonPrint( apiResources ) ) ;

			CSAP.jsonStream( apiResources.path( "items" ) ).forEach( itemDefinition -> {

				var apiItem = apis.addObject( ) ;
				var name = itemDefinition.at( "/metadata/name" ).asText( "notFound" ) ;
				var uiLabel = name ;
				var namespace = itemDefinition.at( "/metadata/namespace" ).asText( "" ) ;

				if ( StringUtils.isNotEmpty( namespace ) ) {

					uiLabel += "," + namespace ;

					var apiPath = apiPathWithUserNamespace ;

					if ( ! apiPath.contains( "/namespaces/" ) ) {

						apiPath = apiPathWithPlaceholderNamespace.replaceAll(
								"NAMESPACE",
								Matcher.quoteReplacement( "/namespaces/" + namespace ) ) ;

					}

					( (ObjectNode) itemDefinition ).put(
							KubernetesJson.apiPath.json( ),
							apiPath + "/" + name ) ;

				} else {

					( (ObjectNode) itemDefinition ).put(
							KubernetesJson.apiPath.json( ),
							apiPathWithUserNamespace + "/" + name ) ;

				}

				apiItem.put( DockerJson.list_label.json( ), uiLabel ) ;
				apiItem.put( "folder", true ) ;
				apiItem.put( "lazy", true ) ;
				apiItem.set( "attributes", itemDefinition ) ;

//				targetAttributes.set( uiLabel, itemDefinition ) ;
			} ) ;

//			CSAP.jsonStream( apiResources.path( "items" ) )
//					.forEach( attributes -> {
//						var apiItem = apis.addObject( ) ;
//						var apiName = attributes.at( "/metadata/name" ).asText( ) ;
//						apiItem.put( DockerJson.list_label.json( ), apiName ) ;
//						apiItem.put( "folder", true ) ;
//						apiItem.put( "lazy", true ) ;
//						apiItem.set( "attributes", attributes ) ;
//					} ) ;

		} catch ( Exception e ) {

			apis.add( buildErrorResponse( "Failed to retrieve pod listing", e ) ) ;

		}

		return apis ;

	}

//	org.joda.time.format.DateTimeFormatter jodaFormatter = DateTimeFormat.forPattern( "MM/dd HH:mm:ss" ) ;
	DateTimeFormatter eventTimeFormatter = DateTimeFormatter.ofPattern( "MM/dd HH:mm:ss" ) ; // "yyyy-MM-dd HH:mm:ss"

	static int MAX_ITEMS_TO_RECEIVE = 1000 ;

	public long eventCount ( String namespace ) {

		var count = 0l ;

		try {

			var eventRecords = listingsBuilder.countResourceItems( "listEventForAllNamespaces", "listNamespacedEvent",
					null,
					namespace ) ;

			if ( eventRecords > MAX_ITEMS_TO_RECEIVE ) {

				logger.warn( "Maximum event inspection exceeded" ) ;

			}

			// Sum up event instance count on each event item
			// raw output parsing used to save overhead
			var rawEventAsString = getRawEvents( namespace ) ;
			List<Integer> counts = JsonPath.parse( rawEventAsString ).read( "$.items[*].count" ) ;
			logger.debug( "Counts: {}", counts ) ;
			count = counts.stream( )
					.mapToInt( Integer::intValue )
					.sum( ) ;

		} catch ( Exception e ) {

			logger.error( "Unable to determine event count: {}",
					CSAP.buildCsapStack( e ) ) ;

		}

		return count ;

	}

	private String getRawEvents ( String namespace ) throws ApiException , IOException {

		var eventApi = "/api/v1/events?limit=" + MAX_ITEMS_TO_RECEIVE ;

		if ( ! isAllNamespaces( namespace ) ) {

			eventApi = "/api/v1/namespaces/" + namespace + "/events?limit=" + MAX_ITEMS_TO_RECEIVE ;

		}

		var rawEventAsString = kubernetesDirect.getRawResponse( apiClient( ), eventApi, null ) ;
		return rawEventAsString ;

	}

	public ArrayNode eventListing ( String namespace , int maxEventsFromServer ) {

		ArrayNode eventListing = jsonMapper.createArrayNode( ) ;
		CoreV1Api api = buildV1Api( ) ;

		try {

			CoreV1EventList eventList ;

			if ( isAllNamespaces( namespace ) ) {

				eventList = api.listEventForAllNamespaces(
						ListingsBuilder.allowWatchBookmarks_null, ListingsBuilder._continue_null,
						ListingsBuilder.fieldSelector_null, ListingsBuilder.labelSelector_null,
						maxEventsFromServer, ListingsBuilder.pretty_null,
						ListingsBuilder.resourceVersion_null, ListingsBuilder.resourceVersionMatch_null,
						ListingsBuilder.timeoutSeconds_max, ListingsBuilder.watch_null ) ;

			} else {

				eventList = api.listNamespacedEvent(
						namespace,
						ListingsBuilder.pretty_null, ListingsBuilder.allowWatchBookmarks_null,
						ListingsBuilder._continue_null,
						ListingsBuilder.fieldSelector_null, ListingsBuilder.labelSelector_null,
						maxEventsFromServer,
						ListingsBuilder.resourceVersion_null, ListingsBuilder.resourceVersionMatch_null,
						ListingsBuilder.timeoutSeconds_max,
						ListingsBuilder.watch_null ) ;

			}

			if ( eventList.getMetadata( ).getContinue( ) != null ) {

				ObjectNode eventReport = eventListing.addObject( ) ;
				eventReport.put( DockerJson.list_label.json( ), "ZZZZZ" ) ;
				ObjectNode eventAttributes = jsonMapper.createObjectNode( ) ;

				eventAttributes.put( "message", "Events limited to " + maxEventsFromServer + " events" ) ;
				eventAttributes.put( "reason", "more-events-warning" ) ;
				String now = LocalDateTime.now( ).format( DateTimeFormatter.ofPattern( "MM:dd HH:mm:ss" ) ) ;
				eventAttributes.put( "timeOfLatestEvent", now ) ;
				eventAttributes.put( "continue", true ) ;
				eventReport.set( DockerJson.list_attributes.json( ), eventAttributes ) ;

			}

			eventList.getItems( ).stream( )

					.limit( maxEventsFromServer )

					.forEach( event -> {

						ObjectNode eventReport = eventListing.addObject( ) ;

						try {

							var eventItemReport = listingsBuilder( ).buildResultReport( event ) ;

							ObjectNode attributes = jsonMapper.createObjectNode( ) ;
							eventReport.set( DockerJson.list_attributes.json( ), attributes ) ;

							logger.debug( "event: \n {}", CSAP.jsonPrint( eventItemReport ) ) ;

							String simpleHost = hostSplit( event.getSource( ).getHost( ) )[0] ;
							attributes.put( "simpleHost", simpleHost ) ;
							attributes.put( "simpleName", mapEventName( event ) ) ;

							addApiPath( attributes,
									"events",
									event.getMetadata( ) ) ;

							String eventTime = null ;

							if ( event.getEventTime( ) != null ) {

								eventTime = eventShortLocalDateTime( event.getEventTime( ) ) ;

//								logger.info( "offsettime: {}, local: {}, eventTime: {} \n\t {}",
//										event.getEventTime( ),
//										event.getEventTime( ).toLocalDateTime( ),
//										eventTime,
//										event.getMessage( ) ) ;
								// eventTime = event.getEventTime( ). ;
							}

							attributes.put( "eventTime", eventTime ) ;

							String latestOccurence = null ;

							if ( event.getLastTimestamp( ) != null ) {

								latestOccurence = eventShortLocalDateTime( event.getLastTimestamp( ) ) ;
								eventReport.put( DockerJson.list_label.json( ), latestOccurence ) ;

							} else if ( eventTime != null ) {

								eventReport.put( DockerJson.list_label.json( ), eventTime ) ;

							} else {

								eventReport.put( DockerJson.list_label.json( ), "ZZZ" ) ;

							}

							attributes.put( "timeOfLatestEvent", latestOccurence ) ;

							String firstOccurence = null ;

							if ( event.getLastTimestamp( ) != null ) {

								firstOccurence = eventShortLocalDateTime( event.getFirstTimestamp( ) ) ;

							}

							attributes.put( "timeOfFirstEvent", firstOccurence ) ;

							attributes.setAll( (ObjectNode) eventItemReport ) ;
							// need to update this to preserve conversion
							attributes.put( "eventTime", eventTime ) ;

						} catch ( Exception e ) {

							eventListing.add( buildErrorResponse( "Failed to retrieve event listing", e ) ) ;

						}

						eventReport.put( "folder", true ) ;
						eventReport.put( "lazy", true ) ;

					} ) ;

		} catch ( Exception e ) {

			eventListing.add( buildErrorResponse( "Fail to retrieve events", e ) ) ;

		}

		return eventListing ;

	}

	public String eventShortLocalDateTime ( OffsetDateTime gmtTime ) {

		var localTime = gmtTime.toZonedDateTime( ).withZoneSameInstant( ZoneId.systemDefault( ) ) ;

		return localTime.format( eventTimeFormatter ) ;

	}

	public ObjectNode buildEventHealthReport (
												int maxMinutes ,
												int maxEventsFromServer ,
												List<String> excludePatterns ) {

		var eventSourceToCount = new HashMap<String, Integer>( ) ;

		CoreV1Api api = buildV1Api( ) ;

		try {

			CoreV1EventList eventList = api.listEventForAllNamespaces(
					ListingsBuilder.allowWatchBookmarks_null, ListingsBuilder._continue_null,
					ListingsBuilder.fieldSelector_null, ListingsBuilder.labelSelector_null,
					maxEventsFromServer, ListingsBuilder.pretty_null,
					ListingsBuilder.resourceVersion_null, ListingsBuilder.resourceVersionMatch_null,
					ListingsBuilder.timeoutSeconds_max, ListingsBuilder.watch_null ) ;

			// if ( eventList.getMetadata( ).getContinue( ) != null ) {
			var remainingItems = eventList.getMetadata( ).getRemainingItemCount( ) ;

			if ( remainingItems != null
					&& remainingItems > 1l ) {

				eventSourceToCount.put( "max-events-limit-exceeded", maxEventsFromServer + remainingItems
						.intValue( ) ) ;

			}

			eventList.getItems( ).stream( )

					.filter( event -> {

						try {

							OffsetDateTime eventOffsetTime = event.getLastTimestamp( ) ;

							if ( eventOffsetTime == null ) {

								eventOffsetTime = event.getEventTime( ) ;

							}

							if ( eventOffsetTime != null ) {

								var earliestTimeRequested = OffsetDateTime.now( ).minusMinutes( maxMinutes ) ;

								// comparisons being done using GMT
								logger.debug( "eventOffsetTime: {} , earliestTimeRequested {} \n\t {}",
										eventOffsetTime, earliestTimeRequested,
										event.getMessage( ) ) ;

								if ( eventOffsetTime.isAfter( earliestTimeRequested ) ) {

									return true ;

								}

							} else {

								logger.warn( "Ignoring item - no time: {}", event.getMessage( ) ) ;

							}

						} catch ( Exception e ) {

							logger.warn( "Failed checking event time: {}", CSAP.buildCsapStack( e ) ) ;

						}

						return false ;

					} )

					.map( this::mapEventName )
					.filter( simpleName -> {

						var excludeMatches = excludePatterns.stream( )
								.filter( pattern -> simpleName.matches( pattern ) )
								.findFirst( ) ;
						return ! excludeMatches.isPresent( ) ;

					} )

					.forEach( simpleName -> {

						if ( ! eventSourceToCount.containsKey( simpleName ) ) {

							eventSourceToCount.put( simpleName, 1 ) ;

						} else {

							eventSourceToCount.put( simpleName, 1 + eventSourceToCount.get( simpleName ) ) ;

						}

					} ) ;

		} catch ( Exception e ) {

			logger.warn( "Failed to query events: {}", CSAP.buildCsapStack( e ) ) ;
			eventSourceToCount.put( "Failed to query events", 1 ) ;

		}

		// HashMap<String, Integer>

		return jsonMapper.convertValue( eventSourceToCount, ObjectNode.class ) ;

	}

	private String mapEventName ( CoreV1Event event ) {

		var name = event.getMetadata( ).getName( ) ;
		var mappedName = name ;

		try {

			mappedName = name.split( Pattern.quote( "." ) )[0] ;
			var involveType = event.getInvolvedObject( ).getKind( ) ;

			var samplePodSuffix = "-68c79575cc-95bwz" ;

			// name, xxx-service-6487f494d-wlpqs
			if ( involveType.equalsIgnoreCase( "Pod" ) ) {

				if ( mappedName.contains( "-" ) )
					mappedName = mappedName.substring( 0, mappedName.lastIndexOf( '-' ) ) ;

				if ( mappedName.contains( "-" ) )
					mappedName = mappedName.substring( 0, mappedName.lastIndexOf( '-' ) ) ;

				// mappedName = mappedName.substring( 0, mappedName.length() -
				// samplePodSuffix.length() ) ;
			}

			if ( involveType.equalsIgnoreCase( "ReplicaSet" )
					|| involveType.equalsIgnoreCase( "Job" ) ) {

				if ( mappedName.contains( "-" ) )
					mappedName = mappedName.substring( 0, mappedName.lastIndexOf( '-' ) ) ;

				// mappedName = mappedName.substring( 0, mappedName.length() -
				// samplePodSuffix.length() ) ;
			}

			// var sampleReplicaAndJobSuffix = "-68c79575cc" ;
			// if ( (involveType.equalsIgnoreCase( "ReplicaSet" )
			// || involveType.equalsIgnoreCase( "Job" ))
			// && mappedName.length() > sampleReplicaAndJobSuffix.length() + 5 ) {
			// if (mappedName.contains( '-' ))
			// mappedName = mappedName.substring( 0, mappedName.lastIndexOf( '-' ) ) ;
			// }
		} catch ( Exception e ) {

			logger.warn( "Failed to map name: {}", name ) ;

		}

		// var typeDesc = StringUtils.rightPad( "["+ involveType + "]", 14 ) ;
		//
		// return StringUtils.rightPad( typeDesc + mappedName, 60 ) + event.getMessage()
		// ;
		return mappedName ;

	}

	private String[] hostSplit ( String fqdn ) {

		String host = fqdn ;
		if ( host == null )
			host = "" ;

		return host.split( Pattern.quote( "." ) ) ;

	}

	private ObjectNode buildFolderNode ( String apiKey , String label , ObjectNode attributes ) {

		var folderReport = jsonMapper.createObjectNode( ) ;

		try {

			folderReport.put( DockerJson.list_label.json( ), label ) ;
			folderReport.put( "folder", true ) ;
			folderReport.put( "lazy", true ) ;

//			ObjectNode attributes = (ObjectNode) jsonMapper.readTree( jsonContent ) ;

			if ( ! attributes.has( "items" ) ) {

				folderReport.set( DockerJson.list_attributes.json( ), attributes ) ;

			} else {

				ObjectNode targetAttributes = jsonMapper.createObjectNode( ) ;
				ArrayNode items = (ArrayNode) attributes.remove( "items" ) ;

				CSAP.jsonStream( items ).forEach( itemDefinition -> {

					var name = itemDefinition.at( "/metadata/name" ).asText( "notFound" ) ;
					var uiLabel = name ;
					var namespace = itemDefinition.at( "/metadata/namespace" ).asText( "" ) ;

					if ( StringUtils.isNotEmpty( namespace ) ) {

						uiLabel += "," + namespace ;

						( (ObjectNode) itemDefinition ).put( KubernetesJson.apiPath.json( ),
								getSettings( ).getApiPath(
										apiKey,
										namespace, name ) ) ;

					} else {

						( (ObjectNode) itemDefinition ).put( KubernetesJson.apiPath.json( ),
								getSettings( ).getApiPath(
										apiKey,
										name ) ) ;

					}

					targetAttributes.set( uiLabel, itemDefinition ) ;

				} ) ;

				folderReport.set( DockerJson.list_attributes.json( ), targetAttributes ) ;

			}

		} catch ( Exception e ) {

			String reason = CSAP.buildCsapStack( e ) ;
			logger.warn( "Failure: {} {}", "failed buildAttributeItem", reason ) ;

		}

		return folderReport ;

	}

	public final static String knownDeleteIssue = "Delete Succeeded - known issue: https://github.com/kubernetes-client/java/issues/86" ;

	public ObjectNode handleKubernetesApiBug ( JsonSyntaxException e ) {

		ObjectNode result ;

		if ( e.getCause( ) instanceof IllegalStateException ) {

			IllegalStateException ise = (IllegalStateException) e.getCause( ) ;

			if ( ise.getMessage( ) != null && ise.getMessage( ).contains( "Expected a string but was BEGIN_OBJECT" ) ) {

				result = buildErrorResponse( knownDeleteIssue, e ) ;

			} else {

				result = buildErrorResponse( "Failed to delete deployment", e ) ;

			}

		} else {

			result = buildErrorResponse( "Failed to delete deployment", e ) ;

		}

		return result ;

	}

	public ObjectNode buildErrorResponse ( String description , Exception error_found ) {

		ObjectNode result = jsonMapper.createObjectNode( ) ;
		result.put( DockerJson.error.json( ), "" ) ;
		result.put( DockerJson.errorReason.json( ), "" ) ;
		String reason = "Command not issued." ;

		if ( error_found != null ) {

			String errMessage = error_found.getMessage( ) ;

			if ( error_found instanceof ApiException ) {

				ApiException apiError = (ApiException) error_found ;
				reason = error_found.getMessage( ) ;

				try {

					if ( apiError.getResponseBody( ) != null ) {

						JsonNode k8sError = jsonMapper.readTree( apiError.getResponseBody( ) ) ;

						if ( k8sError.has( "message" ) ) {

							reason = k8sError.get( "message" ).asText( ) ;

						}

						if ( ! reason.contains( "already exists" ) ) {

							result.set( "k8sError", k8sError ) ;

						}

					} else {

						result.put( "message", errMessage ) ;

					}

				} catch ( Exception e ) {

					logger.debug( "", e ) ;

				}

			} else if ( errMessage != null && errMessage.equals( "Not Found" ) ) {

				reason = "Resource was not found" ;

			} else if ( errMessage != null && error_found.getMessage( ).equals( "Unprocessable Entity" ) ) {

				reason = "Invalid kubernetes request specification. Verify syntax." ;
				logger.info( "Invalid specification: {} extended stack", error_found.getLocalizedMessage( ),
						error_found ) ;

			} else {

				reason = CSAP.buildCsapStack( error_found ) ;

			}

		}

		result.put( DockerJson.error.json( ), description ) ;
		result.put( DockerJson.errorReason.json( ), reason ) ;
		logger.warn( "Kubernetes Command Failure: {}, {}", description, reason ) ;

		return result ;

	}

	Map<String, CsapSimpleCache> cachedReports = new HashMap<>( ) ;

	public ObjectNode getCachedReport ( String reportKey ) {
		// avoid running reports

		CsapSimpleCache summaryCache = cachedReports.get( reportKey ) ;

		ObjectNode report ;

		if ( summaryCache != null ) {

			report = (ObjectNode) summaryCache.getCachedObject( ) ;

		} else {

			logger.warn( "Cached report not found - building node summary: {}", reportKey ) ;

			report = buildKubernetesHealthReport( ) ;

		}

		return report ;

	}

	public ObjectNode getCachedNodeHealthMetrics ( ) {

		CsapSimpleCache metricsReportCache = cachedReports.get( KubernetesJson.report_metrics.json( ) ) ;

		if ( metricsReportCache == null ) {

			metricsReportCache = CsapSimpleCache.builder(
					4,
					TimeUnit.SECONDS,
					this.getClass( ),
					"kubernetes-report-" + KubernetesJson.report_metrics.json( ) ) ;
			metricsReportCache.expireNow( ) ;
			cachedReports.put( KubernetesJson.report_metrics.json( ), metricsReportCache ) ;

		}

		if ( ( metricsReportCache.getCachedObject( ) != null )
				&& ! metricsReportCache.isExpired( ) ) {

			logger.debug( CsapApplication.header( "using cache" ) ) ;

		} else {

			var healthReport = metricsBuilder.nodeSummaryHealthMetrics( ) ;
			metricsReportCache.reset( healthReport ) ;

//			if ( ! healthReport.path( "metrics" ).path( "current" ).path( "error" ).isMissingNode( )
//					|| ! healthReport.path( "metrics" ).path( "error" ).isMissingNode( ) ) {

			//
			// No HeartBeat = need to try to reconnect
			//
			if ( ! healthReport.path( "metrics" ).path( KubernetesJson.heartbeat.json( ) ).asBoolean( ) ) {

				kubernetesConfig.buildApiClient( ) ;

			}

		}

		return (ObjectNode) metricsReportCache.getCachedObject( ) ;

	}

	public ObjectNode buildKubernetesHealthReport ( ) {

		ObjectNode nodeReport ;

		//
		// Master Reports contain more data: deploymentCounts, Application Definition
		// selections, etc.
		//

		if ( getCsapApp( ).kubeletInstance( ).isKubernetesMaster( ) ) {

			nodeReport = buildSummaryReport( "all" ) ;

			if ( getCsapApp( ).kubeletInstance( ).isKubernetesPrimaryMaster( ) ) {

				// run event audits
				CsapSimpleCache cachedEventAudit = cachedReports.get( KubernetesJson.report_events.json( ) ) ;

				if ( cachedEventAudit == null ) {

					cachedEventAudit = CsapSimpleCache.builder(
							30,
							TimeUnit.SECONDS,
							this.getClass( ),
							"kubernetes-report" + KubernetesJson.report_events.json( ) ) ;
					cachedEventAudit.expireNow( ) ;
					cachedReports.put( KubernetesJson.report_events.json( ), cachedEventAudit ) ;

				}

				var eventAuditReport = buildEventHealthReport(
						getCsapApp( ).environmentSettings( ).getKubernetesEventInspectMinutes( ),
						getCsapApp( ).environmentSettings( ).getKubernetesEventInspectMax( ),
						getCsapApp( ).environmentSettings( ).getKubernetesEventExcludes( ) ) ;

				cachedEventAudit.reset( eventAuditReport ) ;

			}

		} else {

			//
			// Non Masters: node only data
			//
			nodeReport = getCachedNodeHealthMetrics( ) ;

		}

		return nodeReport ;

	}

	Application getCsapApp ( ) {

		return kubernetesConfig.getCsapApp( ) ;

	}

	public ObjectNode buildSummaryReport ( String namespace ) {

		if ( namespace == null ) {

			namespace = "all" ;

		}

		// brief cache to manage large number of concurrent requests: NOTE format MUST
		// match
		var cacheKey = "namespace-" + namespace ; // KubernetesJson.report_namespace_all
		CsapSimpleCache namespaceReportCache = cachedReports.get( cacheKey ) ;

		if ( namespaceReportCache == null ) {

			namespaceReportCache = CsapSimpleCache.builder(
					4,
					TimeUnit.SECONDS,
					this.getClass( ),
					"kubernetes-report" + cacheKey ) ;
			namespaceReportCache.expireNow( ) ;
			cachedReports.put( cacheKey, namespaceReportCache ) ;

		}

		if ( ( namespaceReportCache.getCachedObject( ) != null )
				&& ! namespaceReportCache.isExpired( ) ) {

			logger.debug( CsapApplication.header( "using cache" ) ) ;

		} else {

			Timer.Sample summaryTimer = kubernetesConfig.getCsapApp( ).metrics( ).startTimer( ) ;
			logger.debug( CsapApplication.header( "flushing cache" ) ) ;

			ObjectNode summary = jsonMapper.createObjectNode( ) ;

			summary.put( KubernetesJson.heartbeat.json( ), false ) ;
			summary.put( "started", LocalDateTime.now( ).format( DateTimeFormatter.ofPattern( "HH:mm:ss" ) ) ) ;
			summary.put( "completed-ms", "-" ) ;
			summary.put( "version", "cert error: $HOME/.kube" ) ;

			try {

				VersionApi apiInstance = new VersionApi( apiClient( ) ) ;
				VersionInfo versionInfo = apiInstance.getCode( ) ;

				summary.put( "version", versionInfo.getGitVersion( ) ) ;
				summary.put( "nodeCount", nodeCount( ) ) ;
				summary.put( "eventCount", eventCount( namespace ) ) ;
				
				List<String> namespaces = nameSpaces( ) ;
				summary.put( "namespaceCount", namespaces.size( ) ) ;
				summary.set( "namespaces", jsonMapper.valueToTree( namespaces ) ) ;

				summary.set( "podReport", podCountsReport( namespace ) ) ;
				summary.put( "serviceCount", listingsBuilder.serviceCount( namespace ) ) ;
				summary.put( "ingressCount", listingsBuilder.ingressCount( namespace ) ) ;
				summary.put( "jobCount", listingsBuilder.jobCount( namespace ) ) ;
				summary.put( "cronJobCount", listingsBuilder.cronJobCount( namespace ) ) ;
				summary.put( "endpointCount", listingsBuilder.endpointCount( namespace ) ) ;
				summary.put( "configMapCount", listingsBuilder.configMapCount( namespace ) ) ;
				summary.put( "deploymentCount", listingsBuilder.deploymentCount( namespace ) ) ;
				summary.put( "statefulSetCount", listingsBuilder.statefulCount( namespace ) ) ;
				summary.put( "volumeClaimCount", listingsBuilder.persistentVolumeCount( namespace ) ) ;
				summary.put( "daemonSetCount", listingsBuilder.daemonSetCount( namespace ) ) ;
				summary.put( "replicaSetCount", listingsBuilder.replicaSetCount( namespace ) ) ;

				summary.setAll( getCachedNodeHealthMetrics( ) ) ;

				summary.put( "metricsAvailable", metricsBuilder( ).areMetricsAvailable( )  ) ;

			} catch ( Exception e ) {

				summary.set( "error", buildErrorResponse( "Build kubernetes summary", e ) ) ;

				String reason = CSAP.buildCsapStack( e ) ;
				logger.warn( "Failure: {} {}", "failed to build summary", reason ) ;
				kubernetesConfig.buildApiClient( ) ;

			}

			namespaceReportCache.reset( summary ) ;

			var nanos = kubernetesConfig.getCsapApp( ).metrics( ).stopTimer( summaryTimer, SUMMARY_TIMER ) ;
			var reportMs = TimeUnit.NANOSECONDS.toMillis( nanos ) ;
			logger.debug( "\n\n Report completed for namespace: {}, {} ms", namespace, reportMs ) ;
			summary.put( "completed-ms", reportMs ) ;
			summary.put( KubernetesJson.heartbeat.json( ), true ) ;

		}

		// return summary ;
		return (ObjectNode) namespaceReportCache.getCachedObject( ) ;

	}

	public String podLogs ( String namespace , String podName , int numberOfLines , boolean findFirst ) {

		if ( findFirst ) {

			var podsInNamespace = podRawReports( namespace, null ) ;
			var searchName = podName ;
			var podDetails = CSAP.jsonStream( podsInNamespace )
					.filter( podJson -> podJson.at( "/metadata/name" ).asText( ).startsWith( searchName ) )
					.findFirst( ) ;

			// logger.info( "podDetails: {}", podDetails );

			if ( podDetails.isPresent( ) ) {

				podName = podDetails.get( ).at( "/metadata/name" ).asText( ) ;

			}

		}

		if ( numberOfLines > 1000 ) {

			numberOfLines = 1000 ;

		}

		Boolean follow = false ;
		Boolean insecureSkipTLSVerifyBackend = false ;
		Integer limitBytes = null ;
		Boolean timestamps = false ;

		Boolean previousTerminated = false ;
		Integer tailLines = numberOfLines ;

		Integer sinceSeconds = null ;

		String prettyPrint = "true" ;

		CoreV1Api api = new CoreV1Api( apiClient( ) ) ;

		String logOutput = "" ;

		String containerName = null ;

		try {

			logOutput = api.readNamespacedPodLog(
					podName,
					namespace,
					containerName,
					follow,
					insecureSkipTLSVerifyBackend,
					limitBytes, prettyPrint, previousTerminated, sinceSeconds, tailLines, timestamps ) ;

		} catch ( ApiException e ) {

			logger.warn( "Failed getting logs: {} in namepace {}", podName, namespace ) ;
			logOutput = "-- no logs found --" ;

		}

		return logOutput ;

	}

	ScheduledExecutorService logSessionTerminationExecutor = Executors.newScheduledThreadPool( 1 ) ;

	public void podLogStream (
								HttpServletResponse response ,
								String namespace ,
								String podName ,
								String containerName ,
								int numberOfLines ,
								String since ,
								boolean previous )
		throws Exception {

		try {

			long totalBytesRead = 0 ;
			Boolean timestamps = false ;

			Integer tailLines = null ;

			if ( numberOfLines > 0 ) {

				tailLines = numberOfLines ;

			}

			Integer sinceSeconds = null ;

			try {

				if ( StringUtils.isNotEmpty( since ) ) {

					var numberSeconds = 0l ;
					var unit = since.charAt( since.length( ) - 1 ) ;
					var quantity = since.substring( 0, since.length( ) - 1 ) ;
					numberSeconds = Long.parseLong( quantity ) ;

					if ( unit == 'm' ) {

						numberSeconds = TimeUnit.MINUTES.toSeconds( numberSeconds ) ;

					} else if ( unit == 'h' ) {

						numberSeconds = TimeUnit.HOURS.toSeconds( numberSeconds ) ;

					} else if ( unit == 'd' ) {

						numberSeconds = TimeUnit.DAYS.toSeconds( numberSeconds ) ;

					}

					sinceSeconds = (int) ( numberSeconds ) ;

				}

			} catch ( Exception e1 ) {

				logger.error( "Failed parsing since '{}',  {}", since, CSAP.buildCsapStack( e1 ) ) ;

			}

			logger.info( "Streaming logs to output, max time 2s namespace:{}, podName: {}, containerName: {}",
					namespace, podName, containerName ) ;

			var browserContent = response.getOutputStream( ) ;

			var heading = new StringBuilder( "Pod Container Logs" ) ;
			heading.append( CSAP.padLine( "container" ) + containerName ) ;
			heading.append( CSAP.padLine( "pod" ) + podName ) ;
			heading.append( CSAP.padLine( "namespace" ) + namespace ) ;
			heading.append( CSAP.padLine( "numberOfLines" ) + numberOfLines + "\t\t\t(-1 for all)" ) ;
			heading.append( CSAP.padLine( "since" ) + since + "\t\t(eg. 1s, 2m, 3h, 4d)" ) ;
			heading.append( CSAP.padLine( "previous" ) + previous + "\t\t( show previous container)" ) ;

			browserContent.print( CsapApplication.header( heading.toString( ) ) + "\n\n\n" ) ;
			browserContent.flush( ) ;

			var timer = kubernetesConfig.getCsapApp( ).metrics( ).startTimer( ) ;

			try ( var podLogStream = streamNamespacedPodLog(
					namespace,
					podName,
					containerName,
					sinceSeconds,
					tailLines,
					previous,
					timestamps ) ) {

				logSessionTerminationExecutor.schedule( ( ) -> {

					// never let a session go this long. Note stream logs via apiserver can be
					// slow..

					try {

						podLogStream.close( ) ;

					} catch ( Exception e ) {

						logger.warn( "pod tail cleanup: {}", e ) ;

					}

				}, kubernetesConfig.getKubernetesSettings( ).getMaxSessionSeconds( ), TimeUnit.SECONDS ) ;

				byte[] buffer = new byte[1024 * 10] ;
				int numBytesRead = 0 ;

				while ( ( numBytesRead = podLogStream.read( buffer ) ) != -1 ) {

					totalBytesRead += numBytesRead ;
					// logger.info( "Reading: {}", numBytesRead ) ;
					browserContent.write( buffer, 0, numBytesRead ) ;
					// browserContent.flush( ) ; slows * 100x

				}

			} catch ( Exception e ) {

				logger.info( "Kubernetes streaming api  cleanup {}", CSAP.buildCsapStack( e ) ) ;

			}

			var logNanos = kubernetesConfig.getCsapApp( ).metrics( ).stopTimer( timer, CSAP_KUBERNETES_METER
					+ "logdownload" ) ;
			var timeTaken = CSAP.timeUnitPresent( TimeUnit.NANOSECONDS.toMillis( logNanos ) ) ;

			logger.info( "totalBytesRead: {} in {}", CSAP.printBytesWithUnits( totalBytesRead ), timeTaken ) ;
			browserContent.print( CsapApplication.header( "Log Size: " + CSAP.printBytesWithUnits( totalBytesRead )
					+ " in " + timeTaken ) ) ;
			response.getOutputStream( ).flush( ) ;

		} catch ( Exception e ) {

			response.getWriter( ).write( CSAP.jsonPrint( buildErrorResponse( "Failed tailing: " + podName, e ) ) ) ;

		}

	}

	// Important note. You must close this stream or else you can leak connections.
	public InputStream streamNamespacedPodLog (
												String namespace ,
												String name ,
												String container ,
												Integer sinceSeconds ,
												Integer tailLines ,
												boolean previous ,
												boolean timestamps )
		throws ApiException ,
		IOException {

		ApiClient apiClient = kubernetesConfig.buildApiClient( ) ;
		CoreV1Api api = new CoreV1Api( apiClient ) ;
		apiClient.setReadTimeout( kubernetesConfig.getKubernetesSettings( ).getConnectionTimeOutInMs( ) ) ;

		var follow = true ; // ?
		var insecureSkipTLSVerifyBackend = true ;
		Integer limitBytes = null ;
		ApiCallback<?> apiCallback = null ;

		Call call = api.readNamespacedPodLogCall(
				name,
				namespace,
				container,
				follow,
				insecureSkipTLSVerifyBackend,
				limitBytes,
				ListingsBuilder.pretty_null,
				previous,
				sinceSeconds,
				tailLines,
				timestamps,
				apiCallback ) ;
		Response response = call.execute( ) ;

		if ( ! response.isSuccessful( ) ) {

			throw new ApiException( response.code( ), "Logs request failed: " + response.code( ) ) ;

		}

		return response.body( ).byteStream( ) ;

	}

	public ObjectNode podContainerTail (
											String namespace ,
											String podName ,
											String containerName ,
											boolean previousTerminated ,
											int numberOfLines ,
											int since ) {

		ObjectNode result = jsonMapper.createObjectNode( ) ;
		result.put( "since", since ) ;
		result.put( "time", LocalDateTime.now( ).format( DateTimeFormatter.ofPattern( "HH:mm:ss" ) ) ) ;

		try {

			CoreV1Api api = new CoreV1Api( apiClient( ) ) ;

			int timestamp = (int) ( System.currentTimeMillis( ) / 1000 ) ;
			result.put( "since", timestamp ) ;

			Boolean follow = false ;
			Integer limitBytes = null ; // 100 * 1024;
			Boolean timestamps = false ;

			Integer tailLines = null ;

			if ( numberOfLines > 0 ) {

				tailLines = numberOfLines ;

			}

			Integer sinceSeconds = null ;

			if ( since > 0 ) {

				sinceSeconds = timestamp - since ;

				if ( sinceSeconds <= 0 ) {

					sinceSeconds = null ;

				}

			} else {
				// initialize with the last 500 lines

			}

			String prettyPrint = "false" ;
			var insecureSkipTLSVerifyBackend = true ;
			String logResult = api.readNamespacedPodLog(
					podName,
					namespace,
					containerName,
					follow,
					insecureSkipTLSVerifyBackend,
					limitBytes, prettyPrint, previousTerminated, sinceSeconds, tailLines, timestamps ) ;

			result.put( DockerJson.response_info.json( ), "View logs: " + containerName ) ;
			// String logOutput =loggingCallback.toString() ;

			// // Docker returns new empty line
			// if ( logOutput.length() == 1 && logOutput.equals( "\n" ) )
			// logOutput = "" ;

			result.put( "plainText", logResult ) ;

			String moreLogUrl = "/kubernetes/pods/logs/" + namespace + "/" + podName + "?numberOfLines="
					+ numberOfLines ;

			if ( StringUtils.isNotEmpty( containerName ) ) {

				moreLogUrl += "&containerName=" + containerName ;

			}

			result.put( "url", moreLogUrl ) ;

		} catch ( Exception e ) {

			logger.warn( "Failed getting logs: {}", CSAP.buildCsapStack( e ) ) ;

			var heading = new StringBuilder( "Unable to retrieve logs" ) ;
			heading.append( CSAP.padLine( "container" ) + containerName ) ;
			heading.append( CSAP.padLine( "pod" ) + podName ) ;
			heading.append( CSAP.padLine( "namespace" ) + namespace ) ;
			heading.append( CSAP.padLine( "numberOfLines" ) + numberOfLines ) ;
			heading.append( CSAP.padLine( "since" ) + since ) ;
			heading.append( CSAP.padLine( "previous" ) + previousTerminated
					+ "\t\t( show previous logs - exists only when failures occured)" ) ;

			result = buildErrorResponse( CsapApplication.header( heading.toString( ) ), e ) ;

		}

		return result ;

	}

	public KubernetesSettings getSettings ( ) {

		return settings ;

	}

	public void setSettings ( KubernetesSettings settings ) {

		this.settings = settings ;

	}

	public ListingsBuilder listingsBuilder ( ) {

		return listingsBuilder ;

	}

	public MetricsBuilder metricsBuilder ( ) {

		return metricsBuilder ;

	}

	public ReportsBuilder reportsBuilder ( ) {

		return reportsBuilder ;

	}

	public String ingressUrl ( Application csapApp , String path , String ingressHost ) {

		if ( StringUtils.isEmpty( ingressHost ) ) {

			ingressHost = ingressHost( ) ; // default to first active ingressHost

		}

		return nodePortUrl( csapApp, INGRESS_NGINX_SERVICE, ingressHost, path, false ) ;

	}

	public String nodePortUrl (
								Application csapApp ,
								String serviceName ,
								String hostAndMaybePort ,
								String path ,
								boolean isSsl ) {

		var hostPort = hostAndMaybePort.split( ":", 2 ) ;

		String location = csapApp.getAgentUrl( hostPort[0], path ) ;

		var serviceNodePort = nodePort( serviceName ) ;
		var serviceNodePortFull = ":" + serviceNodePort ;

		if ( serviceNodePort == 0 ) {

			serviceNodePortFull = "" ;

		}

		if ( hostPort.length == 2 ) {

			// ingress is on a host network
			serviceNodePortFull = ":" + hostPort[1] ;

		}

		String finalLocation = location.replaceAll(
				Matcher.quoteReplacement( csapApp.getAgentEndpoint( ) ),
				serviceNodePortFull ) ;

		if ( isSsl ) {

			finalLocation = finalLocation.replaceAll(
					Matcher.quoteReplacement( "http" ),
					"https" ) ;

		}

		return finalLocation ;

	}

	private int nodePort ( String serviceName ) {

		int nodePort = 0 ;

		CoreV1Api api = buildV1Api( ) ;

		try {
//			var services = api.listServiceForAllNamespaces( null, null, null, null, null, null, null, null, null ) ;

			var services = api.listServiceForAllNamespaces(
					ListingsBuilder.allowWatchBookmarks_null, ListingsBuilder._continue_null,
					ListingsBuilder.fieldSelector_null, ListingsBuilder.labelSelector_null,
					ListingsBuilder.limit_max,
					ListingsBuilder.pretty_null,
					ListingsBuilder.resourceVersion_null, ListingsBuilder.resourceVersionMatch_null,
					ListingsBuilder.timeoutSeconds_max,
					ListingsBuilder.watch_null ) ;

			Optional<Integer> portOptional = services.getItems( ).stream( )
					.map( service -> findPort( serviceName, service ) )
					.filter( port -> ! port.equals( 0 ) )
					.findFirst( ) ;

			if ( portOptional.isPresent( ) )
				nodePort = portOptional.get( ) ;

		} catch ( Exception e ) {

			logger.error( "Failed to get service listing: {}", CSAP.buildCsapStack( e ) ) ;

		}

		return nodePort ;

	}

	private Integer findPort ( String serviceName , V1Service service ) {

		int port = 0 ;

		if ( service.getMetadata( ).getName( ).contains( serviceName ) ) {

			logger.info( "service.getSpec().getPorts().size(): {}", service.getSpec( ).getPorts( ).size( ) ) ;

			if ( service.getSpec( ).getPorts( ).size( ) > 0 ) {

				Integer nodePort = service.getSpec( ).getPorts( ).get( 0 ).getNodePort( ) ;

				if ( nodePort != null ) {

					port = nodePort ;

				}

			}

		}

		return port ;

	}

	private String ingressHost ( ) {

		String ingressHost = "host-not-found" ;

		CoreV1Api api = buildV1Api( ) ;

		try {

//			V1PodList podList = api.listNamespacedPod( "ingress-nginx", null, null, null, null, null, null, null, null, null ) ;

			V1PodList podList = api.listNamespacedPod(
					"ingress-nginx",
					ListingsBuilder.pretty_null, ListingsBuilder.allowWatchBookmarks_null,
					ListingsBuilder._continue_null,
					ListingsBuilder.fieldSelector_null, ListingsBuilder.labelSelector_null,
					ListingsBuilder.limit_max,
					ListingsBuilder.resourceVersion_null, ListingsBuilder.resourceVersionMatch_null,
					ListingsBuilder.timeoutSeconds_max,
					ListingsBuilder.watch_null ) ;

			Optional<String> theHost = podList.getItems( ).stream( )
					.map( pod -> {

						String podName = pod.getMetadata( ).getName( ) ;

						logger.debug( "Found pod: {}", pod.getMetadata( ).getName( ) ) ;

						if ( podName.startsWith( "nginx-ingress-controller" ) ) {

							var hostName = podHostName( pod.getStatus( ).getHostIP( ) ) ;

							if ( pod.getSpec( ).getHostNetwork( ) ) {

								//
								// Handle non standard host ports
								//
								var firstContainer = pod.getSpec( ).getContainers( ).get( 0 ) ;

								for ( var port : firstContainer.getPorts( ) ) {

									if ( port.getName( ) != null
											&& port.getName( ).equals( "http" ) ) {

										var firstHttpPort = port.getHostPort( ) ;

										if ( firstHttpPort != 80 ) {

											logger.info( "Non standard port for ingress: {}", firstHttpPort ) ;
											hostName += ":" + firstHttpPort ;

										}

									}

								}

							}

							return hostName ;

						}

						return "" ;

					} )
					.filter( StringUtils::isNoneEmpty )
					.findFirst( ) ;

			if ( theHost.isPresent( ) ) {

				ingressHost = theHost.get( ) ;

			}

		} catch ( Exception e ) {

			logger.error( "Failed to get service listing: {}", CSAP.buildCsapStack( e ) ) ;

		}

		return ingressHost ;

	}

	public SpecBuilder specBuilder ( ) {

		return specBuilder ;

	}

}
