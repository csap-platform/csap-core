package org.csap.agent.container.kubernetes ;

import java.time.LocalDateTime ;
import java.time.format.DateTimeFormatter ;
import java.util.HashMap ;
import java.util.List ;
import java.util.Optional ;
import java.util.concurrent.TimeUnit ;
import java.util.concurrent.locks.ReentrantLock ;
import java.util.stream.Collectors ;

import org.apache.commons.lang3.StringUtils ;
import org.csap.agent.container.DockerJson ;
import org.csap.agent.model.Application ;
import org.csap.helpers.CSAP ;
import org.csap.helpers.CsapApplication ;
import org.csap.helpers.CsapSimpleCache ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;

import com.fasterxml.jackson.databind.JsonNode ;
import com.fasterxml.jackson.databind.ObjectMapper ;
import com.fasterxml.jackson.databind.node.ObjectNode ;

import io.kubernetes.client.openapi.apis.CoreV1Api ;
import io.micrometer.core.instrument.Timer ;

public class MetricsBuilder {
	Logger logger = LoggerFactory.getLogger( getClass( ) ) ;

	public static final String SUMMARY_TIMER = KubernetesIntegration.CSAP_KUBERNETES_METER + "summary" ;
	public static final String METRICS_TIMER = KubernetesIntegration.CSAP_KUBERNETES_METER + "metrics" ;

	public static final String PROMETHEUS_ADAPTER_LABEL = "app.kubernetes.io/name=prometheus-adapter" ;
	public static final String PROMETHEUS_ADAPTER_LABEL_NAMESPACE = "csap-monitoring" ;

	public MetricsBuilder ( KubernetesIntegration kubernetesIntegration, ObjectMapper jsonMapper ) {

		this.kubernetes = kubernetesIntegration ;
		this.jsonMapper = jsonMapper ;

	}

	ObjectMapper jsonMapper ;

	KubernetesIntegration kubernetes ;
	ApiDirect kubernetesDirect = new ApiDirect( ) ;

	double memoryMbToGb ( long mb ) {

		return CSAP.roundIt( mb / 1024.0, 2 ) ;

	}

	private String testHost = null ;

	private boolean printDesktopOnce = true ;

	private final double CORE_UNIT_FROM_N = 1000000 * 1000 ;
	private final double CORE_UNIT_FROM_M = 1000 ;
	private final double CORE_UNIT_FROM_U = 1000000 ;

	public JsonNode getTestMeterNames ( ) {

		return junitTestMeters ;

	}

	public void setTestMeterNames ( JsonNode testMeterNames ) {

		this.junitTestMeters = testMeterNames ;

	}

	private JsonNode junitTestMeters = null ;

	public ObjectNode nodeSummaryHealthMetrics ( ) {

		Timer.Sample summaryTimer = kubernetes.getCsapApp( ).metrics( ).startTimer( ) ;

		// summary.set( "metrics", metrics( ) ) ;
		var fullReport = jsonMapper.createObjectNode( ) ;
		var metricsReport = fullReport.putObject( "metrics" ) ;

		metricsReport.put( KubernetesJson.heartbeat.json( ), false ) ;
		metricsReport.put( "started", LocalDateTime.now( ).format( DateTimeFormatter.ofPattern( "HH:mm:ss" ) ) ) ;
		metricsReport.put( "completed-ms", "-" ) ;

		try {

			//
			// This only resolves AFTER metrics server is deployed....
			//
			var nodeMetrics = currentNodeReport( ) ;
			metricsReport.set( "current", nodeMetrics ) ; // node metrics

			//
			// Allocations: critical determination of whether kubernetes will allow
			// scheduling. Note: metrics is a later starter - so match will use csaphostname
			//
			String nodeName = null ; // nodeMetrics.path( "name" ).asText( "" )
//				if ( getCsapApp( ).isJunit( )
//						|| getCsapApp( ).kubeletInstance( ).isKubernetesMaster( ) ) {
			var usageReport = kubernetes.reportsBuilder( ).nodeUsageReport( nodeName ) ;
			nodeMetrics.setAll( usageReport ) ;

//				} else {
//					// use rest call to pull from master?
//					var usageReport = buildNodeUsageReport( nodeName ) ;
//					nodeMetrics.setAll( usageReport ) ;
//				}

			//
			// Sub Meters - used to collect subset of kubernetes containers definition in
			// application definition
			//
			if ( junitTestMeters != null
					|| kubernetes.getCsapApp( ).kubeletInstance( ).isKubernetesMaster( ) ) {

				JsonNode collectionMeterNames = junitTestMeters ;

				if ( junitTestMeters != null ) {

					logger.warn( CsapApplication.testHeader( "Junit - loading all containers: {}" ),
							junitTestMeters ) ;

				} else {

					collectionMeterNames = kubernetes.getCsapApp( ).environmentSettings( )
							.getKubernetesMeters( ) ;

				}

				if ( collectionMeterNames.isArray( ) ) {

					var containersToCollect = CSAP.jsonStream( collectionMeterNames )
							.filter( JsonNode::isTextual )
							.map( JsonNode::asText )
							.collect( Collectors.toList( ) ) ;

					metricsReport.set( KubernetesJson.containers.json( ),
							buildContainerMetrics( null,
									containersToCollect ) ) ;

				}

			}

			metricsReport.put( KubernetesJson.heartbeat.json( ), true ) ;

			logger.debug( "fullReport {}", CSAP.jsonPrint( fullReport ) ) ;

		} catch ( Exception e ) {

			metricsReport.set( "error", kubernetes.buildErrorResponse( "kubernetes node metrics", e ) ) ;

			String reason = CSAP.buildCsapStack( e ) ;
			logger.warn( "Failure: {} {}", "failed to build kubernetes node metrics", reason ) ;

		}

		var nanos = kubernetes.getCsapApp( ).metrics( ).stopTimer( summaryTimer, SUMMARY_TIMER
				+ ".metrics" ) ;
		var reportMs = TimeUnit.NANOSECONDS.toMillis( nanos ) ;
		logger.debug( "\n\n Report completed: {}, {} ms", reportMs ) ;
		metricsReport.put( "completed-ms", reportMs ) ;

		return fullReport ;

	}

	private CsapSimpleCache metricsCache = null ;
	private ReentrantLock metricsUpdateLock = new ReentrantLock( ) ;

	public ObjectNode cachedKubeletReport ( ) {

		if ( metricsCache == null ) {

			// typically - very very fast - but this will handle large concurrent requests
			// from hitting system
			metricsCache = CsapSimpleCache.builder(
					2,
					TimeUnit.SECONDS,
					KubernetesIntegration.class,
					"kubernetes-metric-server" ) ;

		}

		// ensure it is a object node - even if loading failed for some reason
		var cachedReport = metricsCache.getCachedObject( ) ;

		if ( cachedReport == null
				|| ! ObjectNode.class.isInstance( cachedReport ) ) {

			var initReport = jsonMapper.createObjectNode( ) ;
			initReport.put( "init", true ) ;
			metricsCache.setCachedObject( initReport ) ;

		}

		// lazy / persitent attemts to get metrics
		if ( ( (ObjectNode) metricsCache.getCachedObject( ) ).path( "init" ).asBoolean( ) ) {

			metricsCache.expireNow( ) ;

		}

		if ( ( metricsCache.getCachedObject( ) != null )
				&& ! metricsCache.isExpired( ) ) {

			logger.debug( "\n\n***** ReUsing  metricsCache   *******\n\n" ) ;

		} else if ( metricsUpdateLock.tryLock( ) ) {

			logger.debug( "\n\n***** REFRESHING   metricsCache   *******\n\n" ) ;
			var timer = kubernetes.getCsapApp( ).metrics( ).startTimer( ) ;

			try {

				metricsCache.reset( metricsRefreshCache( ) ) ;

			} catch ( Exception e ) {

				logger.info( "Failed refreshing runtime", e ) ;

			} finally {

				metricsUpdateLock.unlock( ) ;

			}

			kubernetes.getCsapApp( ).metrics( ).stopTimer( timer, METRICS_TIMER ) ;

		}

		return (ObjectNode) metricsCache.getCachedObject( ) ;

	}

	JsonNode metricsRefreshCache ( ) {

		ObjectNode metricsReport = null ;

		// body.apiVersion( "v1" );

		try {

			// var podStatus = podStatus( "system" ) ;

			//

			var metricsServerPods = kubernetes.podsByLabel( "kube-system", "k8s-app=metrics-server" ) ;

			if ( metricsServerPods.size( ) == 0 ) {

				metricsServerPods = kubernetes.podsByLabel( PROMETHEUS_ADAPTER_LABEL_NAMESPACE,
						PROMETHEUS_ADAPTER_LABEL ) ;

			}

			logger.info( "metricsServerPods: {}", metricsServerPods.size( ) ) ;

			if ( metricsServerPods.size( ) > 0 ) {

				var podReport = metricsServerPods.path( 0 ) ;
				var podConditions = podReport.at( "/status/conditions" ) ;

				if ( podConditions.isArray( ) ) {

					var metricsReportOptional = CSAP.jsonStream( podConditions )
							.filter( condition -> condition.path( "type" ).asText( ).equals( "Ready" ) )
							.filter( condition -> condition.path( "status" ).asText( ).equalsIgnoreCase( "true" ) )
							.findFirst( )
							.map( condition -> {

								Optional<ObjectNode> report = Optional.empty( ) ;

								try {

									report = Optional.of( buildMetricsServerReport( ) ) ;

								} catch ( Exception e ) {

									logger.error( "Failed building metrics report", CSAP.buildCsapStack( e ) ) ;

								}

								return report ;

							} ) ;

					if ( metricsReportOptional.isPresent( )
							&& metricsReportOptional.get( ).isPresent( ) ) {

						metricsReport = metricsReportOptional.get( ).get( ) ;

					}

				}

				// logger.info( "metrics details: {} ", CSAP.jsonPrint( podDetails.get() ) );
				// api.listPo
			} else {

				metricsReport = jsonMapper.createObjectNode( ) ;
				metricsReport.put( DockerJson.error.json( ), "Failed to find metrics-server or promethesius adapter"
						+ " condition Ready = true" ) ;

			}

		} catch ( Exception e ) {

			metricsReport = jsonMapper.createObjectNode( ) ;
			metricsReport = kubernetes.buildErrorResponse( "Failed to retrieve metrics", e ) ;

		}

		return metricsReport ;

	}

	public ObjectNode nodeReport ( String nodeFilter ) throws Exception {

		JsonNode metricsServerNodes = kubernetesDirect.getJson(
				"/apis/metrics.k8s.io/v1beta1/nodes",
				kubernetes.apiClient( ),
				jsonMapper ) ;

		logger.debug( "filter: {}, nodeMetrics: {} ", nodeFilter, metricsServerNodes ) ;

		ObjectNode nodeReport = jsonMapper.createObjectNode( ) ;

		CSAP.jsonStream( metricsServerNodes.path( "items" ) )
				.forEach( node -> {

					var nodeName = node.at( "/metadata/name" ).asText( ) ;

					if ( StringUtils.isEmpty( nodeFilter )
							|| nodeName.matches( nodeFilter ) ) {

						var stats = nodeReport.putObject( nodeName ) ;

						stats.put( KubernetesJson.cores.json( ), CSAP.roundIt(
								metricsServerNormalizedCores( node ),
								2 ) ) ;

						stats.put( KubernetesJson.memoryGb.json( ), memoryMbToGb( metricsServerMemoryInMb( node ) ) ) ;
						stats.put( KubernetesJson.podsRunning.json( ), podCountForHost( nodeName, "==Running" ) ) ;
						stats.put( KubernetesJson.podsNotRunning.json( ), podCountForHost( nodeName, "!=Running" ) ) ;

					}

				} ) ;

		// logger.info( "nodeReport: {}", nodeReport ) ;

		return nodeReport ;

	}

	int podCountForHost ( String host , String status ) {

		var count = 0 ;

		try {

			String fieldSelector = "spec.nodeName==" + host + ",status.phase" + status ;
			var rawPodListing = kubernetes.getRawPods( null, fieldSelector ) ;

			if ( kubernetesDirect.isLastSuccess( ) ) {

				MiniMappings.SimplePodListing podListing = jsonMapper.readerFor( MiniMappings.SimplePodListing.class )
						.readValue( rawPodListing ) ;

				logger.debug( "miniPods: {}", podListing ) ;
				count = podListing.getItems( ).size( ) ;

			} else {

				logger.warn( "Failed kuberntes call: {}", rawPodListing ) ;

			}

//			var podListing = api.listPodForAllNamespaces(
//					null, null,
//					fieldSelector, null, 1,
//					null, null, null, null ) ;
			// count = podList.getItems( ).size( ) ;
//			count = podListing.getItems( ).size( ) ;
//
//			if ( podListing.getMetadata( ).getRemainingItemCount( ) != null ) {
//				count += podListing.getMetadata( ).getRemainingItemCount( ) ;
//			}
		} catch ( Exception e ) {

			logger.error( "Failed to get pod count: {}",
					CSAP.buildCsapStack( e ) ) ;

		}

		return count ;

	}

	private ObjectNode currentNodeReport ( ) throws Exception {

		ObjectNode nodeMetrics = jsonMapper.createObjectNode( ) ;
		nodeMetrics.put( "error", "kubernetes not available" ) ;

		var filterHost = testHost ;

		if ( filterHost == null ) {

			filterHost = Application.getInstance( ).getCsapHostName( ) + ".*" ;

		}

		// gets the first matched items
		var fullNodeMetrics = nodeReport( filterHost ) ;

		if ( fullNodeMetrics == null
				|| ! fullNodeMetrics.fieldNames( ).hasNext( ) ) {

			if ( kubernetes.getCsapApp( ).isDesktopHost( ) ) {

				if ( printDesktopOnce ) {

					printDesktopOnce = false ;
					logger.warn( CsapApplication.testHeader( "Defaulting to first host" ) ) ;

				}

				fullNodeMetrics = nodeReport( null ) ;

			} else {

				logger.warn( CsapApplication.header( "Failed to get metrics: {}" ), filterHost ) ;

			}

		}

		var nodeName = fullNodeMetrics.fieldNames( ).next( ) ;
		logger.debug( "filterHost: {} fullNodeMetrics: {}", filterHost, fullNodeMetrics ) ;
		nodeMetrics = (ObjectNode) fullNodeMetrics.path( nodeName ) ;

		var api = new CoreV1Api( kubernetes.apiClient( ) ) ;
		var nodeNameFieldSelector = "metadata.name=" + nodeName ;
//			var nodeListing = api.listNode( null, null, null, nodeNameFieldSelector, null, 1, null, null, null ) ;

		var nodeListing = api.listNode(
				ListingsBuilder.pretty_null,
				ListingsBuilder.allowWatchBookmarks_null, ListingsBuilder._continue_null,
				nodeNameFieldSelector, ListingsBuilder.labelSelector_null,
				1,
				ListingsBuilder.resourceVersion_null, ListingsBuilder.resourceVersionMatch_null,
				ListingsBuilder.timeoutSeconds_max, ListingsBuilder.allowWatchBookmarks_null ) ;

		var nodeInfo = nodeListing.getItems( ).get( 0 ) ;

		nodeMetrics.put( "version", nodeInfo.getStatus( ).getNodeInfo( ).getKubeletVersion( ) ) ;

		var isHealthy = true ;

		for ( var condition : nodeInfo.getStatus( ).getConditions( ) ) {

			var conditionHealth = condition.getStatus( ).equalsIgnoreCase( "False" ) ;

			if ( condition.getType( ).equalsIgnoreCase( "Ready" ) ) {

				conditionHealth = condition.getStatus( ).equalsIgnoreCase( "True" ) ;

			}

			isHealthy = isHealthy && conditionHealth ;

		}

		nodeMetrics.put( "name", nodeName ) ;
		nodeMetrics.put( "healthy", isHealthy ) ;

		return nodeMetrics ;

	}

	JsonNode buildContainerMetrics ( JsonNode metricsServerPods , List<String> containerFilters )
		throws Exception {

		if ( metricsServerPods == null ) {

			// summary View
			metricsServerPods = kubernetesDirect.getJson( "/apis/metrics.k8s.io/v1beta1/pods", kubernetes
					.apiClient( ),
					jsonMapper ) ;

		}

		var containerReports = jsonMapper.createObjectNode( ) ;
		//
		// Container Summary Metrics
		//
		var containerToCores = new HashMap<String, Double>( ) ;
		var containerToMemory = new HashMap<String, Long>( ) ;
		var containerToCount = new HashMap<String, Integer>( ) ;

		CSAP.jsonStream( metricsServerPods.path( "items" ) )
				.flatMap( pod -> CSAP.jsonStream( pod.path( KubernetesJson.containers.json( ) ) ) )
				.forEach( podContainer -> {

					var containerName = podContainer.path( "name" ).asText( ) ;

					if ( containerFilters == null
							|| containerFilters.isEmpty( )
							|| containerFilters.contains( containerName ) ) {

						containerToCount.merge( containerName, 1, Integer::sum ) ;
						containerToCores.merge( containerName, metricsServerNormalizedCores( podContainer ),
								Double::sum ) ;
						containerToMemory.merge( containerName, metricsServerMemoryInMb( podContainer ), Long::sum ) ;

					}

				} ) ;

		// build containerReport
		containerToCount.keySet( ).stream( ).forEach( containerName -> {

			var containerReport = containerReports.putObject( containerName ) ;
			containerReport.put( KubernetesJson.containerCount.json( ), containerToCount.get( containerName ) ) ;
			containerReport.put( KubernetesJson.cores.json( ),
					CSAP.roundIt(
							containerToCores.get( containerName ),
							2 ) ) ;
			containerReport.put( KubernetesJson.memoryInMb.json( ), containerToMemory.get( containerName ) ) ;

		} ) ;

		return containerReports ;

	}

	ObjectNode buildMetricsServerReport ( )
		throws Exception {

		var metricsReport = jsonMapper.createObjectNode( ) ;

		// placeholders for ordering in output
		metricsReport.putObject( KubernetesJson.nodes.json( ) ) ;
		metricsReport.putObject( KubernetesJson.containers.json( ) ) ;

		var containerNamespaceSummary = metricsReport.putArray( "containerNamespace" ) ;
		var namespaceSummary = metricsReport.putObject( "namespaces" ) ;
		var podStats = metricsReport.putObject( KubernetesJson.pods.json( ) ) ;

		metricsReport.set( KubernetesJson.nodes.json( ), nodeReport( null ) ) ;

		JsonNode metricsServerPods = kubernetesDirect.getJson( "/apis/metrics.k8s.io/v1beta1/pods",
				kubernetes.apiClient( ),
				jsonMapper ) ;
		logger.debug( "podMetrics: {} ", CSAP.jsonPrint( metricsServerPods ) ) ;

		metricsReport.set( KubernetesJson.containers.json( ), buildContainerMetrics( metricsServerPods, null ) ) ;

		//
		// Namespace Summary Metrics
		//
		var namespaceToCores = new HashMap<String, Double>( ) ;
		var namespaceToMemory = new HashMap<String, Long>( ) ;
		var namespaceToContainers = new HashMap<String, Integer>( ) ;
		var namespaceToPods = new HashMap<String, Integer>( ) ;

		var pods = metricsServerPods.path( "items" ) ;

		CSAP.jsonStream( metricsServerPods.path( "items" ) ).forEach( pod -> {

			var podName = pod.at( "/metadata/name" ).asText( ) ;
			var podNamespace = pod.at( "/metadata/namespace" ).asText( ) ;

			namespaceToPods.merge( podNamespace, 1, Integer::sum ) ;

			CSAP.jsonStream( pod.path( KubernetesJson.containers.json( ) ) ).forEach( podContainer -> {

				namespaceToContainers.merge( podNamespace, 1, Integer::sum ) ;

				// addCpuAndMemory( podContainer, podStats );

				namespaceToCores.merge( podNamespace, metricsServerNormalizedCores( podContainer ), Double::sum ) ;
				namespaceToMemory.merge( podNamespace, metricsServerMemoryInMb( podContainer ), Long::sum ) ;

			} ) ;

		} ) ;

		// build namespaceReport
		namespaceToPods.keySet( ).stream( ).forEach( namespace -> {

			var namespaceReport = namespaceSummary.putObject( namespace ) ;
			namespaceReport.put( KubernetesJson.pods.json( ), namespaceToPods.get( namespace ) ) ;
			namespaceReport.put( KubernetesJson.containerCount.json( ), namespaceToContainers.get( namespace ) ) ;
			namespaceReport.put( KubernetesJson.cores.json( ),
					CSAP.roundIt(
							namespaceToCores.get( namespace ),
							2 ) ) ;
			namespaceReport.put( KubernetesJson.memoryInMb.json( ), namespaceToMemory.get( namespace ) ) ;

		} ) ;

		logger.debug(
				"\n namespaceToPods: {} \n namespaceToContainers: {} \n namespaceToCores: {} \n namespaceToMemory: {} ",
				namespaceToPods, namespaceToContainers, namespaceToCores, namespaceToMemory ) ;

		//
		// Pod & Container Metrics
		//

		var containerNsToCores = new HashMap<String, Double>( ) ;
		var containerNsToMemory = new HashMap<String, Long>( ) ;
		var containerNsToContainers = new HashMap<String, Integer>( ) ;
		var containerNsToNs = new HashMap<String, String>( ) ;
		var containerNsToContainer = new HashMap<String, String>( ) ;

		CSAP.jsonStream( metricsServerPods.path( "items" ) ).forEach( pod -> {

			var podName = pod.at( "/metadata/name" ).asText( ) ;
			var podNamespace = pod.at( "/metadata/namespace" ).asText( ) ;
			var podReport = podStats.putObject( podName ) ;
			podReport.put( "namespace", podNamespace ) ;
			podReport.put( KubernetesJson.cores.json( ), 0.0 ) ;
			podReport.put( KubernetesJson.memoryInMb.json( ), 0 ) ;
			var podContainerNames = podReport.putArray( KubernetesJson.containers.json( ) ) ;

			var podContainers = pod.path( KubernetesJson.containers.json( ) ) ;

			if ( podContainers.isArray( ) ) {

				var coreTotal = 0.0 ;
				long memoryTotal = 0 ;

				for ( var podContainer : podContainers ) {

					var containerName = podContainer.path( "name" ).asText( ) ;
					var containerCores = metricsServerNormalizedCores( podContainer ) ;
					var containerMemory = metricsServerMemoryInMb( podContainer ) ;

					podContainerNames.add( containerName ) ;
					coreTotal += containerCores ;
					memoryTotal += containerMemory ;

					// container ns totals
					var containerNsName = containerName + "-" + podNamespace ;
					containerNsToContainer.put( containerNsName, containerName ) ;
					containerNsToNs.put( containerNsName, podNamespace ) ;
					containerNsToContainers.merge( containerNsName, 1, Integer::sum ) ;
					containerNsToCores.merge( containerNsName, containerCores, Double::sum ) ;
					containerNsToMemory.merge( containerNsName, containerMemory, Long::sum ) ;

				}

				podReport.put( KubernetesJson.cores.json( ), CSAP.roundIt( coreTotal, 2 ) ) ;
				podReport.put( KubernetesJson.memoryInMb.json( ), memoryTotal ) ;

			}

		} ) ;

		// build containerNamespaceSummary
		containerNsToContainers.keySet( ).stream( ).forEach( containerNs -> {

			var containerNsReport = containerNamespaceSummary.addObject( ) ;
			containerNsReport.put( "name", containerNsToContainer.get( containerNs ) ) ;
			containerNsReport.put( "namespace", containerNsToNs.get( containerNs ) ) ;
			containerNsReport.put( KubernetesJson.containerCount.json( ), containerNsToContainers.get( containerNs ) ) ;
			containerNsReport.put( KubernetesJson.cores.json( ),
					CSAP.roundIt(
							containerNsToCores.get( containerNs ),
							2 ) ) ;
			containerNsReport.put( KubernetesJson.memoryInMb.json( ), containerNsToMemory.get( containerNs ) ) ;

		} ) ;

		return metricsReport ;

	}

	double metricsServerNormalizedCores ( JsonNode metricsServerContainer ) {

		var cpuWithKubernetesUnits = metricsServerContainer.at( "/usage/" + KubernetesJson.formatCpu.json( ) )
				.asText( ) ;
		var cpuAsCore = metricsServerContainer.at( "/usage/" + KubernetesJson.formatCpu.json( ) ).asDouble( 0.0 ) ;

		if ( cpuWithKubernetesUnits.endsWith( "n" ) ) {

			cpuAsCore = Long.parseLong( cpuWithKubernetesUnits.split( "n" )[0] ) / CORE_UNIT_FROM_N ;

		} else if ( cpuWithKubernetesUnits.endsWith( "m" ) ) {

			cpuAsCore = Long.parseLong( cpuWithKubernetesUnits.split( "m" )[0] ) / CORE_UNIT_FROM_M ;

		} else if ( cpuWithKubernetesUnits.endsWith( "u" ) ) {

			cpuAsCore = Long.parseLong( cpuWithKubernetesUnits.split( "u" )[0] ) / CORE_UNIT_FROM_U ;

		}

		return cpuAsCore ;

	}

	long metricsServerMemoryInMb ( JsonNode metricsServerContainer ) {

		var memoryWithKubernetesUnits = metricsServerContainer.at( "/usage/memory" ).asText( ) ;
		var memoryInMb = metricsServerContainer.at( "/usage/memory" ).asLong( 0 ) ;

		if ( memoryWithKubernetesUnits.endsWith( "Ki" ) ) {

			memoryInMb = Long.parseLong( memoryWithKubernetesUnits.split( "Ki" )[0] ) / 1024 ;

		} else if ( memoryWithKubernetesUnits.endsWith( "Mi" ) ) {

			memoryInMb = Long.parseLong( memoryWithKubernetesUnits.split( "Mi" )[0] ) ;

		} else if ( memoryWithKubernetesUnits.endsWith( "Gi" ) ) {

			memoryInMb = Long.parseLong( memoryWithKubernetesUnits.split( "Gi" )[0] ) ;

		} else {

			memoryInMb = memoryInMb / 1024 / 1024 ;

		}

		return memoryInMb ;

	}

	public String getTestHost ( ) {

		return testHost ;

	}

	public void setTestHost ( String testHost ) {

		this.testHost = testHost ;

	}

}
