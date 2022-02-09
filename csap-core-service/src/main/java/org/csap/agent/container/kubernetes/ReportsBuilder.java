package org.csap.agent.container.kubernetes ;

import java.util.Comparator ;
import java.util.HashMap ;
import java.util.Map ;
import java.util.concurrent.TimeUnit ;
import java.util.concurrent.atomic.AtomicLong ;
import java.util.stream.Collectors ;

import org.apache.commons.lang3.StringUtils ;
import org.csap.agent.CsapApis ;
import org.csap.helpers.CSAP ;
import org.csap.helpers.CsapSimpleCache ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;

import com.fasterxml.jackson.databind.JsonNode ;
import com.fasterxml.jackson.databind.ObjectMapper ;
import com.fasterxml.jackson.databind.node.ArrayNode ;
import com.fasterxml.jackson.databind.node.ObjectNode ;
import com.google.common.util.concurrent.AtomicDouble ;

import io.kubernetes.client.openapi.apis.CoreV1Api ;

public class ReportsBuilder {

	Logger logger = LoggerFactory.getLogger( getClass( ) ) ;

	public ReportsBuilder (
			KubernetesIntegration kubernetesIntegration,
			MetricsBuilder kubernetesMetrics,
			ObjectMapper jsonMapper ) {

		this.kubernetes = kubernetesIntegration ;
		this.metrics = kubernetesMetrics ;
		this.jsonMapper = jsonMapper ;

	}

	ObjectMapper jsonMapper ;

	KubernetesIntegration kubernetes ;

	MetricsBuilder metrics ;

	private volatile CsapSimpleCache kubernetesNodeReportCache = null ;

	public ArrayNode nodeReports ( ) {

		logger.debug( "Entered " ) ;

		if ( kubernetesNodeReportCache == null ) {

			kubernetesNodeReportCache = CsapSimpleCache.builder(
					5,
					TimeUnit.SECONDS,
					this.getClass( ),
					"Kubernetes Node Report" ) ;
			kubernetesNodeReportCache.expireNow( ) ;

		}

		// Use cache
		if ( ! kubernetesNodeReportCache.isExpired( ) ) {

			logger.debug( "ReUsing kubernetesNodeReportCache" ) ;

			return (ArrayNode) kubernetesNodeReportCache.getCachedObject( ) ;

		}

		// Lets refresh cache
		logger.debug( "Refreshing kubernetesNodeReportCache" ) ;

		ArrayNode nodeSummaryReport = jsonMapper.createArrayNode( ) ;

		CoreV1Api api = new CoreV1Api( kubernetes.apiClient( ) ) ;

		ObjectNode metricsReport = null ;

		try {

			metricsReport = kubernetes.metricsBuilder( ).nodeReport( null ) ;

		} catch ( Exception e ) {

			logger.info( "Failed to buildNodeMetrics: {}", CSAP.buildCsapStack( e ) ) ;

		}

		ObjectNode allNodeMetricReport = metricsReport ;

		try {

			var apiResult = api.listNode(
					ListingsBuilder.pretty_null, ListingsBuilder.allowWatchBookmarks_null,
					ListingsBuilder._continue_null,
					ListingsBuilder.fieldSelector_null, ListingsBuilder.labelSelector_null,
					ListingsBuilder.limit_max,
					ListingsBuilder.resourceVersion_null, ListingsBuilder.resourceVersionMatch_null,
					ListingsBuilder.timeoutSeconds_max,
					ListingsBuilder.allowWatchBookmarks_null ) ;

			var nodeReport = kubernetes.listingsBuilder( ).serializeToJson( apiResult ) ;
			logger.debug( "nodeInfo: {}", CSAP.jsonPrint( nodeReport ) ) ;

			var nodeItems = nodeReport.path( "items" ) ;

			if ( nodeItems.isArray( ) ) {

				CSAP.jsonStream( nodeItems ).forEach( nodeListing -> {

					var node = nodeSummaryReport.addObject( ) ;
					var nodeName = nodeListing.at( "/metadata/name" ).asText( ) ;
					node.put( "name", nodeName ) ;
					node.put( "ready", false ) ;

					node.put( "master",
							! nodeListing.at( "/metadata/labels" )
									.path( "node-role.kubernetes.io/master" )
									.isMissingNode( ) ) ;

					node.put( "taints", nodeListing.at( "/spec/taints/0/effect" ).asText( ) ) ;

					kubernetes.addApiPath( node,
							"nodes",
							nodeListing.at( "/metadata/name" ).asText( ) ) ;

					node.put( "conditions", false ) ;

					var statusListing = nodeListing.path( "status" ) ;
					var conditions = statusListing.path( "conditions" ) ;
					CSAP.jsonStream( conditions ).forEach( condition -> {

						var type = condition.path( "type" ).asText( ) ;
						var statusIsTrue = condition.path( "status" ).asText( ).equalsIgnoreCase( "true" ) ;
						var statusIsFalse = condition.path( "status" ).asText( ).equalsIgnoreCase( "false" ) ;

						if ( type.equalsIgnoreCase( "Ready" ) ) {

							node.put( "ready", statusIsTrue ) ;

						} else {

							node.put( "conditions",
									node.path( "conditions" ).asBoolean( )
											|| ! statusIsFalse ) ;

						}

					} ) ;

					var metricsFormat = jsonMapper.createObjectNode( ) ;
					var usage = metricsFormat.putObject( "usage" ) ;

					//
					// Capacity can be pulled from node listing; but overridden below if available
					// via
					//
					var capacity = node.putObject( "capacity" ) ;
					capacity.put( K8.pods.val( ), nodeListing.at( "/status/capacity/pods" ).asInt( ) ) ;
					capacity.put( K8.cores.val( ),
							nodeListing.at( "/status/capacity/cpu" ).asInt( ) ) ;
					usage.put( "memory", nodeListing.at( "/status/capacity/memory" ).asText( ) ) ;
					capacity.put( K8.memoryGb.val( ),
							metrics.memoryMbToGb( metrics.metricsServerMemoryInMb( metricsFormat ) ) ) ;

					//
					// Node metric report: report uses MB, converted to gb for conciseness
					//
					var coresActive = 0.0 ;
					var memoryInGbActive = 0.0 ;
					var podsActive = 0 ;

					if ( allNodeMetricReport != null ) {

						var nodeMetricReport = allNodeMetricReport.path( nodeName ) ;
						logger.debug( "nodeMetricReport: {}", nodeMetricReport ) ;

						if ( nodeMetricReport.isObject( ) ) {

							coresActive = nodeMetricReport.path( K8.cores.val( ) ).asDouble( ) ;
							podsActive = nodeMetricReport.path( K8.podsRunning.val( ) ).asInt( ) ;
//									+ nodeMetricReport.path( KubernetesJson.podsNotRunning.json( ) ).asInt( ) ;
							memoryInGbActive = metrics.memoryMbToGb(
									nodeMetricReport.path( K8.memoryGb.val( ) ).asLong( ) ) ;

						}

					}

					var activeReport = node.putObject( "active" ) ;
					activeReport.put( K8.pods.val( ), podsActive ) ;
					activeReport.put( K8.cores.val( ), coresActive ) ;
					activeReport.put( K8.memoryGb.val( ), memoryInGbActive ) ;

					//
					// node resource report
					//
					node.setAll( nodeUsageReport( nodeName ) ) ;

				} ) ;

			}

			kubernetesNodeReportCache.reset( nodeSummaryReport ) ;

		} catch ( Exception e ) {

			logger.info( "Failed to get nodes: {}", CSAP.buildCsapStack( e ) ) ;

		}

		// return nodeSummaryReport ;
		return (ArrayNode) kubernetesNodeReportCache.getCachedObject( ) ;

	}

	ObjectNode nodeUsageReport ( String nodeName ) {

		var usageNormalizeReport = jsonMapper.createObjectNode( ) ;
		var allocated = usageNormalizeReport.putObject( "resources" ) ;

		var formatOnlyMetrics = jsonMapper.createObjectNode( ) ;
		var formatOnlyUsage = formatOnlyMetrics.putObject( "usage" ) ;

		var nodeUsageReport = CsapApis.getInstance( ).osManager( ).buildCachedKubernetesNodeUsageReport( ) ;
		logger.debug( "nodeUsageReport: {}", CSAP.jsonPrint( nodeUsageReport ) ) ;

		if ( StringUtils.isEmpty( nodeName ) ) {
			// health reports will not have name when metrics service is down

			for ( var nameIter = nodeUsageReport.fieldNames( ); nameIter.hasNext( ); ) {

				var nodeReportName = nameIter.next( ) ;

				if ( nodeReportName.startsWith( CsapApis.getInstance( ).application( ).getCsapHostName( ) ) ) {

					nodeName = nodeReportName ;
					break ;

				}

			}

		}

		var currentNodeReport = nodeUsageReport.path( nodeName ) ;

		if ( currentNodeReport.isObject( ) ) {

			//
			// requests
			//
			formatOnlyUsage.put( K8.formatCpu.val( ), currentNodeReport.at( "/Allocated/cpu/request" )
					.asText( ) ) ;
			formatOnlyUsage.put( "memory", currentNodeReport.at( "/Allocated/memory/request" ).asText( ) ) ;
			var cores = CSAP.roundIt( metrics.metricsServerNormalizedCores( formatOnlyMetrics ), 2 ) ;
			var memoryGb = metrics.memoryMbToGb( metrics.metricsServerMemoryInMb( formatOnlyMetrics ) ) ;

			var requests = allocated.putObject( "requests" ) ;
			requests.put( K8.cores.val( ), cores ) ;
			requests.put( "coresPercent", currentNodeReport.at( "/Allocated/cpu/requestPercent" ).asInt( ) ) ;
			requests.put( K8.memoryGb.val( ), memoryGb ) ;
			requests.put( "memoryPercent", currentNodeReport.at( "/Allocated/memory/requestPercent" ).asInt( ) ) ;

			//
			// limits
			//
			formatOnlyUsage.put( K8.formatCpu.val( ), currentNodeReport.at( "/Allocated/cpu/limit" )
					.asText( ) ) ;
			formatOnlyUsage.put( "memory", currentNodeReport.at( "/Allocated/memory/limit" ).asText( ) ) ;
			cores = CSAP.roundIt( metrics.metricsServerNormalizedCores( formatOnlyMetrics ), 2 ) ;
			memoryGb = metrics.memoryMbToGb( metrics.metricsServerMemoryInMb( formatOnlyMetrics ) ) ;

			var limits = allocated.putObject( "limits" ) ;
			limits.put( K8.cores.val( ), cores ) ;
			limits.put( "coresPercent", currentNodeReport.at( "/Allocated/cpu/limitPercent" ).asInt( ) ) ;
			limits.put( K8.memoryGb.val( ), memoryGb ) ;
			limits.put( "memoryPercent", currentNodeReport.at( "/Allocated/memory/limitPercent" ).asInt( ) ) ;

			//
			// capacity
			//
			formatOnlyUsage.put( K8.formatCpu.val( ), currentNodeReport.at( "/Capacity/cpu" ).asText( ) ) ;
			formatOnlyUsage.put( "memory", currentNodeReport.at( "/Capacity/memory" ).asText( ) ) ;
			cores = CSAP.roundIt( metrics.metricsServerNormalizedCores( formatOnlyMetrics ), 2 ) ;
			memoryGb = metrics.memoryMbToGb( metrics.metricsServerMemoryInMb( formatOnlyMetrics ) ) ;

			var capacity = allocated.putObject( "capacity" ) ;
			capacity.put( K8.cores.val( ), cores ) ;
			capacity.put( K8.memoryGb.val( ), memoryGb ) ;

		}

		return usageNormalizeReport ;

	}

	public ArrayNode eventReport ( String namespace , int maxEvents ) {

		ArrayNode eventSummaryReport = jsonMapper.createArrayNode( ) ;

		var eventDetailReports = kubernetes.eventListing( namespace, maxEvents ) ;

		logger.debug( "eventDetailReports: {}", eventDetailReports ) ;

		CSAP.jsonStream( eventDetailReports )
				.forEach( eventDetailReport -> {

					logger.debug( "Event: {}", CSAP.jsonPrint( eventDetailReport ) ) ;

					var event = eventSummaryReport.addObject( ) ;

					// event.put( "name", eventDetailReport.at( "/attributes/metadata/name"
					// ).asText() ) ;

					var currentNamespace = eventDetailReport.at( "/attributes/metadata/namespace" ).asText( ) ;
					var currentName = eventDetailReport.at( "/attributes/metadata/name" ).asText( ) ;
					event.put( "namespace", currentNamespace ) ;
					var sourceHost = eventDetailReport.at( "/attributes/simpleHost" ).asText( ) ;
					var sourceReason = eventDetailReport.at( "/attributes/reason" ).asText( ) ;

					event.put( "host", sourceHost ) ;
					event.put( "reason", sourceReason ) ;
					event.put( "simpleName", eventDetailReport.at( "/attributes/simpleName" ).asText( ) ) ;
					event.put( "component", eventDetailReport.at( "/attributes/source/component" ).asText( ) ) ;
					var dateTime = eventDetailReport.at( "/attributes/timeOfLatestEvent" ).asText( ) ;

					if ( dateTime.split( " ", 2 ).length != 2 ) {

						dateTime = eventDetailReport.at( "/attributes/eventTime" ).asText( ) ;

					}

					event.put( "dateTime", dateTime ) ;
					event.put( "type", eventDetailReport.at( "/attributes/type" ).asText( ) ) ;
					event.put( "kind", eventDetailReport.at( "/attributes/involvedObject/kind" ).asText( ) ) ;
					event.put( "count", eventDetailReport.at( "/attributes/count" ).asInt( ) ) ;
					event.put( "message", eventDetailReport.at( "/attributes/message" ).asText( ) ) ;

					kubernetes.addApiPath( event,
							"events",
							currentNamespace, currentName ) ;

					if ( eventDetailReport.at( "/attributes/continue" ).asBoolean( ) ) {

						event.put( "message", "kubernetes api event retrieval limited to " + maxEvents + " events." ) ;
						event.put( "kind", "Increase Event Limit to view all events" ) ;
						event.put( "reason", sourceReason ) ;
						event.put( "component", "kubernertes api" ) ;
						event.put( "type", "Warning" ) ;

					}

				} ) ;

		// must be sorted client side to support filtered listings
		var eventList = CSAP.jsonStream( eventSummaryReport )
				.sorted(
						Comparator.comparing(
								event -> event.path( "dateTime" ).asText( ),
								Comparator.reverseOrder( ) ) )

				.collect( Collectors.toList( ) ) ;

		ArrayNode sortedEventReport = jsonMapper.createArrayNode( ) ;
		eventList.stream( ).forEach( sortedEventReport::add ) ;

		return sortedEventReport ;

	}

	public ArrayNode volumeReport ( ) {

		ArrayNode volumeSummaryReport = jsonMapper.createArrayNode( ) ;

		CoreV1Api api = new CoreV1Api( kubernetes.apiClient( ) ) ;

		try {

			var apiResult = api.listPersistentVolume(
					ListingsBuilder.pretty_null, ListingsBuilder.allowWatchBookmarks_null,
					ListingsBuilder._continue_null,
					ListingsBuilder.fieldSelector_null, ListingsBuilder.labelSelector_null,
					ListingsBuilder.limit_max,
					ListingsBuilder.resourceVersion_null, ListingsBuilder.resourceVersionMatch_null,
					ListingsBuilder.timeoutSeconds_max,
					ListingsBuilder.allowWatchBookmarks_null ) ;

			var volumeReport = kubernetes.listingsBuilder( ).serializeToJson( apiResult ) ;

			logger.debug( "volumeList: {}", CSAP.jsonPrint( volumeReport ) ) ;

			var nodeItems = volumeReport.path( "items" ) ;

			if ( nodeItems.isArray( ) ) {

				CSAP.jsonStream( nodeItems ).forEach( nodeListing -> {

					var node = volumeSummaryReport.addObject( ) ;
					node.put( "name", nodeListing.at( "/metadata/name" ).asText( ) ) ;
					node.put( "ready", nodeListing.at( "/status/phase" ).asText( ).equalsIgnoreCase( "Bound" ) ) ;

					node.put( "ref-name", nodeListing.at( "/spec/claimRef/name" ).asText( ) ) ;
					node.put( "ref-namespace", nodeListing.at( "/spec/claimRef/namespace" ).asText( ) ) ;
					node.put( "ref-kind", nodeListing.at( "/spec/claimRef/kind" ).asText( ) ) ;

					node.put( "nfs-server", nodeListing.at( "/spec/nfs/server" ).asText( ) ) ;
					var path = nodeListing.at( "/spec/nfs/path" ).asText( ) ;
					var localPath = nodeListing.at( "/spec/local/path" ).asText( ) ;

					if ( StringUtils.isNotEmpty( localPath ) ) {

						path = localPath ;
						node.put( "affinity",
								nodeListing.at(
										"/spec/nodeAffinity/required/nodeSelectorTerms/0/matchExpressions/0/values/0" )
										.asText( ) ) ;

					}

					node.put( "path", path ) ;

					node.put( "capacity", nodeListing.at( "/spec/capacity/storage" ).asText( ) ) ;

					kubernetes.addApiPath( node,
							"persistentvolumes",
							nodeListing.at( "/metadata/name" ).asText( ) ) ;

				} ) ;

			}

		} catch ( Exception e ) {

			logger.info( "Failed to get nodes: {}", CSAP.buildCsapStack( e ) ) ;

		}

		return volumeSummaryReport ;

	}

	public ArrayNode podSummaryReport ( String namespaceName , String podNameFilter ) {

		ArrayNode pods = kubernetes.listingsBuilder( ).podCsapListing( namespaceName, podNameFilter ) ;

		var podMetrics = metrics.cachedKubeletReport( ).path( K8.pods.val( ) ) ;

		ArrayNode podSummaryReport = jsonMapper.createArrayNode( ) ;

		Map<String, ObjectNode> podByOwner = new HashMap<>( ) ;

		logger.debug( "running report namespaceName: {}, podNameFilter: {}", namespaceName, podNameFilter ) ;

		CSAP.jsonStream( pods )
				.filter( JsonNode::isObject )
				.map( pod -> (ObjectNode) pod )
				.forEach( pod -> {

					// var podSummary = podSummaryReport.addObject() ;
					ObjectNode podSummary ;
					var ownerName = pod.at( "/attributes/metadata/ownerReferences/0/name" ).asText( "no-owner" ) ;
					var ownerKind = pod.at( "/attributes/metadata/ownerReferences/0/kind" ).asText( ) ;
					var ownerShortName = ownerName ;
					var lastHyphen = ownerShortName.lastIndexOf( '-' ) ;

					if ( ownerKind.equalsIgnoreCase( "Node" ) ) {

						ownerName = "kubernetes-node-pods" ;
						ownerShortName = "kubernetes-node-pods" ;

					} else if ( ( lastHyphen > 0 )
							&& ( ( ownerName.length( ) - lastHyphen ) > 8 )
							&& ! ownerKind.equalsIgnoreCase( "StatefulSet" ) ) {

						ownerShortName = ownerShortName.substring( 0, lastHyphen ) ;

					}

					if ( ownerName.equals( "no-owner" ) ) {

						var tier = pod.at( "/attributes/metadata/labels/tier" ).asText( ) ;

						if ( tier.equals( "control-plane" ) ) {

							ownerShortName = "control-plane" ;

						}

					}

					if ( podByOwner.containsKey( ownerShortName ) ) {

						podSummary = podByOwner.get( ownerShortName ) ;

					} else {

						podSummary = podSummaryReport.addObject( ) ;
						podByOwner.put( ownerShortName, podSummary ) ;

						podSummary.put( "ownerKind", ownerKind ) ;
						podSummary.put( "ownerName", ownerName ) ;
						podSummary.put( "owner", ownerShortName ) ;
						podSummary.put( K8.cores.val( ), 0.0 ) ;

					}

					var podInstances = podSummary.path( K8.pods.val( ) ) ;

					if ( ! podInstances.isArray( ) ) {

						podInstances = podSummary.putArray( K8.pods.val( ) ) ;

					}

					var podInstance = ( (ArrayNode) podInstances ).addObject( ) ;

					// logger.info( "pod: {}", CSAP.jsonPrint( pod ) );

					// podInstance.put( "name", pod.path( "label" ).asText() ) ;
					var podName = pod.at( "/attributes/metadata/name" ).asText( ) ;
					podInstance.put( "name", podName ) ;
					var podNamespace = pod.at( "/attributes/metadata/namespace" ).asText( ) ;
					podInstance.put( "namespace", podNamespace ) ;
					podInstance.put( "host", pod.at( "/attributes/hostname" ).asText( ) ) ;
					podInstance.put( "status", podState( pod ) ) ;
					podInstance.put( "container-count", podTypeCount( pod, K8.containers.val( ) ) ) ;

					var containerNames = podInitAndRunContainerNames( pod ) ;
					podInstance.set( "container-names", containerNames ) ;

					var firstImage = pod.at( "/attributes/spec/containers/0/image" ).asText( "not-found" ) ;

					if ( containerNames.size( ) != 1 ) {

						firstImage = "*multiple" ;

					}

					podInstance.put( "first-image", firstImage ) ;

					podInstance.put( "volumes", podTypeCount( pod, "volumes" ) ) ;
					podInstance.put( "containers-not-ready", podContainersNotReadyCount( pod ) ) ;
					podInstance.put( "conditions-failed", podFailedConditionCount( pod ) ) ;
					podInstance.put( "container-restarts", podContainersRestartCount( pod ) ) ;

					kubernetes.addApiPath( podInstance,
							K8.pods.val( ),
							podNamespace, podName ) ;

					var metrics = podMetrics.path( podName ) ;

					if ( metrics.isObject( ) ) {

						podInstance.set( "metrics", metrics ) ;
						podSummary.put( K8.cores.val( ), CSAP.roundIt(
								podSummary.path( K8.cores.val( ) ).asDouble( ) + metrics.path(
										K8.cores.val( ) ).asDouble( ),
								2 ) ) ;

					}

				} ) ;

		return podSummaryReport ;

	}

	public ArrayNode podNamespaceSummaryReport ( String namespaceName ) {

		ArrayNode namespaces = kubernetes.namespaceInfo( namespaceName ) ;
		var namespaceMetrics = metrics.cachedKubeletReport( ).path( "namespaces" ) ;

		ArrayNode namespaceSummaryReport = jsonMapper.createArrayNode( ) ;

		var allTotal = new AtomicLong( ) ;
		var allRunning = new AtomicLong( ) ;
		var allStopped = new AtomicLong( ) ;
		var allSuceeeded = new AtomicLong( ) ;
		var allPending = new AtomicLong( ) ;
		var allContainersNotReady = new AtomicLong( ) ;
		var allConditionsFailed = new AtomicLong( ) ;
		var allContainerRestarts = new AtomicLong( ) ;
		var allCores = new AtomicDouble( ) ;
		var allMemory = new AtomicLong( ) ;
		var allContainers = new AtomicLong( ) ;
		var allPodCount = new AtomicLong( ) ;

		CSAP.jsonStream( namespaces )
				.filter( JsonNode::isObject )
				.map( namespace -> (ObjectNode) namespace )
				.forEach( namespace -> {

					var namespaceSummary = namespaceSummaryReport.addObject( ) ;

					var theName = namespace.at( "/metadata/name" ).asText( ) ;
					namespaceSummary.put( "name", theName ) ;

					var pods = kubernetes.listingsBuilder( ).podCsapListing( theName, null ) ;

					// namespaceSummary.set( "pods", pods ) ;

					namespaceSummary.put( "total", pods.size( ) ) ;
					allTotal.addAndGet( pods.size( ) ) ;

					var runningPods = CSAP.jsonStream( pods )
							.map( this::podState )
							.filter( state -> state.equals( "running" ) )
							.count( ) ;

					namespaceSummary.put( "running", runningPods ) ;
					allRunning.addAndGet( runningPods ) ;

					var stoppedPods = CSAP.jsonStream( pods )
							.map( this::podState )
							.filter( state -> state.equals( "stopped" ) )
							.count( ) ;

					namespaceSummary.put( "stopped", stoppedPods ) ;
					allStopped.addAndGet( stoppedPods ) ;

					var succeededPods = CSAP.jsonStream( pods )
							.map( this::podState )
							.filter( state -> state.equals( "succeeded" ) )
							.count( ) ;

					namespaceSummary.put( "succeeded", succeededPods ) ;
					allSuceeeded.addAndGet( succeededPods ) ;

					var pendingPods = CSAP.jsonStream( pods )
							.map( this::podState )
							.filter( state -> state.equals( "pending" ) )
							.count( ) ;

					namespaceSummary.put( "pending", pendingPods ) ;
					allPending.addAndGet( pendingPods ) ;

					var containersNotReady = CSAP.jsonStream( pods )
							.mapToLong( this::podContainersNotReadyCount )
							.sum( ) ;

					namespaceSummary.put( "containers-not-ready", containersNotReady ) ;
					allContainersNotReady.addAndGet( containersNotReady ) ;

					var failedConditionCount = CSAP.jsonStream( pods )
							.mapToLong( this::podFailedConditionCount )
							.sum( ) ;
					namespaceSummary.put( "conditions-failed", failedConditionCount ) ;
					allConditionsFailed.addAndGet( failedConditionCount ) ;

					var restartCount = CSAP.jsonStream( pods )
							.mapToLong( this::podContainersRestartCount )
							.sum( ) ;
					namespaceSummary.put( "container-restarts", restartCount ) ;
					allContainerRestarts.addAndGet( restartCount ) ;

					var metrics = namespaceMetrics.path( theName ) ;

					if ( metrics.isObject( ) ) {

						namespaceSummary.set( "metrics", metrics ) ;
						allCores.addAndGet( metrics.path( K8.cores.val( ) ).asDouble( ) ) ;
						allMemory.addAndGet( metrics.path( K8.memoryInMb.val( ) ).asLong( ) ) ;
						allContainers.addAndGet( metrics.path( K8.containers.val( ) ).asLong( ) ) ;
						allPodCount.addAndGet( metrics.path( K8.pods.val( ) ).asLong( ) ) ;

					}

				} ) ;

		var allNameSpacesTotals = namespaceSummaryReport.addObject( ) ;
		allNameSpacesTotals.put( "name", "all-namespaces" ) ;
		allNameSpacesTotals.put( "total", allTotal.get( ) ) ;
		allNameSpacesTotals.put( "running", allRunning.get( ) ) ;
		allNameSpacesTotals.put( "stopped", allStopped.get( ) ) ;
		allNameSpacesTotals.put( "succeeded", allSuceeeded.get( ) ) ;
		allNameSpacesTotals.put( "pending", allPending.get( ) ) ;
		allNameSpacesTotals.put( "containers-not-ready", allContainersNotReady.get( ) ) ;
		allNameSpacesTotals.put( "conditions-failed", allConditionsFailed.get( ) ) ;
		allNameSpacesTotals.put( "container-restarts", allContainerRestarts.get( ) ) ;

		var allMetrics = allNameSpacesTotals.putObject( "metrics" ) ;
		allMetrics.put( K8.cores.val( ), CSAP.roundIt( allCores.get( ), 2 ) ) ;
		allMetrics.put( K8.memoryInMb.val( ), allMemory.get( ) ) ;
		allMetrics.put( K8.containers.val( ), allContainers.get( ) ) ;
		allMetrics.put( K8.pods.val( ), allPodCount.get( ) ) ;

		return namespaceSummaryReport ;

	}

	private String podState ( JsonNode pod ) {

		var state = "running" ;

		var podPhase = pod.at( "/attributes/status/phase" ).asText( ) ;

		logger.debug( "podPhase: {}", podPhase ) ;

		if ( ! podPhase.equals( "Running" ) ) {

			state = "stopped" ;

			if ( podPhase.equals( "Pending" ) ) {

				state = "pending" ;

			}

		}

		var containerStatuses = pod.at( "/attributes/status/containerStatuses" ) ;

		if ( containerStatuses.isArray( ) ) {

			var numContainersNotReady = podContainersNotReadyCount( pod ) ;

			if ( numContainersNotReady > 0 ) {

				state = "pending" ;

			}

		}

		var podConditions = pod.at( "/attributes/status/conditions" ) ;

		if ( podConditions.isArray( ) ) {

			var numConditionsFailed = podFailedConditionCount( pod ) ;

			if ( numConditionsFailed > 0 ) {

				state = "stopped" ;

			}

		}

		if ( podPhase.equalsIgnoreCase( "Succeeded" ) ) {

			state = "succeeded" ;

		}

		return state ;

	}

	private long podFailedConditionCount ( JsonNode pod ) {

		var numConditionsFailed = 0l ;

		var podPhase = pod.at( "/attributes/status/phase" ).asText( ) ;

		if ( ! podPhase.equalsIgnoreCase( "Succeeded" ) ) {

			var podConditions = pod.at( "/attributes/status/conditions" ) ;

			if ( podConditions.isArray( ) ) {

				numConditionsFailed = CSAP.jsonStream( podConditions )
						.filter( JsonNode::isObject )
						.map( podCondition -> (ObjectNode) podCondition )
						.filter( podCondition -> ! podCondition.path( "status" ).asText( ).equalsIgnoreCase( "True" ) )
						.count( ) ;

			}

		}

		return numConditionsFailed ;

	}

	private ArrayNode podInitAndRunContainerNames ( JsonNode pod ) {

		var names = jsonMapper.createArrayNode( ) ;
		var containers = pod.at( "/attributes/spec/containers" ) ;

		if ( containers.isArray( ) ) {

			CSAP.jsonStream( containers )
					.filter( JsonNode::isObject )
					.map( container -> container.path( "name" ).asText( ) )
					.forEach( name -> names.add( name ) ) ;

		}

		containers = pod.at( "/attributes/spec/initContainers" ) ;

		if ( containers.isArray( ) ) {

			CSAP.jsonStream( containers )
					.filter( JsonNode::isObject )
					.map( container -> container.path( "name" ).asText( ) )
					.forEach( name -> names.add( name ) ) ;

		}

		return names ;

	}

	private int podTypeCount ( JsonNode pod , String type ) {

		var numContainers = 0 ;
		var containers = pod.at( "/attributes/spec/" + type ) ;

		if ( containers.isArray( ) ) {

			numContainers = containers.size( ) ;

		}

		return numContainers ;

	}

	private int podContainersRestartCount ( JsonNode pod ) {

		var numContainersNotReady = 0 ;
		var containerStatuses = pod.at( "/attributes/status/containerStatuses" ) ;

		if ( containerStatuses.isArray( ) ) {

			numContainersNotReady = CSAP.jsonStream( containerStatuses )
					.filter( JsonNode::isObject )
					.map( containerStatus -> (ObjectNode) containerStatus )
					.mapToInt( containerStatus -> containerStatus.path( "restartCount" ).asInt( ) )
					.sum( ) ;

		}

		return numContainersNotReady ;

	}

	private long podContainersNotReadyCount ( JsonNode pod ) {

		var numContainersNotReady = 0l ;

		var podPhase = pod.at( "/attributes/status/phase" ).asText( ) ;

		if ( ! podPhase.equalsIgnoreCase( "Succeeded" ) ) {

			var containerStatuses = pod.at( "/attributes/status/containerStatuses" ) ;

			if ( containerStatuses.isArray( ) ) {

				numContainersNotReady = CSAP.jsonStream( containerStatuses )
						.filter( JsonNode::isObject )
						.map( containerStatus -> (ObjectNode) containerStatus )
						.filter( containerStatus -> ! containerStatus.path( "ready" ).asBoolean( ) )
						.count( ) ;

			}

		}

		return numContainersNotReady ;

	}

}
