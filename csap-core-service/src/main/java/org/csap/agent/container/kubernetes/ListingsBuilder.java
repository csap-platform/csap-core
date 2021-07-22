package org.csap.agent.container.kubernetes ;

import java.lang.reflect.Type ;
import java.time.OffsetDateTime ;
import java.util.Arrays ;
import java.util.concurrent.TimeUnit ;
import java.util.stream.Collectors ;

import org.csap.agent.container.DockerJson ;
import org.csap.agent.container.kubernetes.KubernetesIntegration.Propogation_Policy ;
import org.csap.agent.model.Application ;
import org.csap.helpers.CSAP ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;

import com.fasterxml.jackson.databind.JsonNode ;
import com.fasterxml.jackson.databind.ObjectMapper ;
import com.fasterxml.jackson.databind.node.ArrayNode ;
import com.fasterxml.jackson.databind.node.MissingNode ;
import com.fasterxml.jackson.databind.node.ObjectNode ;
import com.google.gson.ExclusionStrategy ;
import com.google.gson.FieldAttributes ;
import com.google.gson.Gson ;
import com.google.gson.GsonBuilder ;
import com.google.gson.JsonElement ;
import com.google.gson.JsonObject ;
import com.google.gson.JsonSerializationContext ;
import com.google.gson.JsonSerializer ;

import io.kubernetes.client.openapi.apis.AppsV1Api ;
import io.kubernetes.client.openapi.apis.BatchV1Api ;
import io.kubernetes.client.openapi.apis.BatchV1beta1Api ;
import io.kubernetes.client.openapi.apis.CoreV1Api ;
import io.kubernetes.client.openapi.apis.NetworkingV1beta1Api ;

public class ListingsBuilder {

	Logger logger = LoggerFactory.getLogger( getClass( ) ) ;

	KubernetesIntegration kubernetes ;

	Gson gsonWithExclusions ;

//	public Gson gson ( ) {
//
//		return gsonWithExclusions ;
//
//	}

	public JsonNode buildResultReport ( Object apiResults ) {

		JsonNode report = MissingNode.getInstance( ) ;

		try {

			var reportString = gsonWithExclusions.toJson( apiResults ) ;
			report = jsonMapper.readTree( reportString ) ;

		} catch ( Exception e ) {

			logger.warn( "Failed converting to list", CSAP.buildCsapStack( e ) ) ;

		}

		return report ;

	}

	ObjectMapper jsonMapper ;

	public ListingsBuilder ( KubernetesIntegration kubernetesIntegration, ObjectMapper jsonMapper ) {

		this.kubernetes = kubernetesIntegration ;
		this.jsonMapper = jsonMapper ;

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

//				if ( clazz.getName( ).startsWith( "org.joda.time" ) ) {
//					return true ;
//				}
				return false ;

			}
		} ;

		JsonSerializer<OffsetDateTime> offSetHandler = new JsonSerializer<>( ) {
			@Override
			public JsonElement serialize ( OffsetDateTime src , Type typeOfSrc , JsonSerializationContext context ) {

				JsonObject jsonMerchant = new JsonObject( ) ;

				jsonMerchant.addProperty( "csap-host-local", kubernetes.eventShortLocalDateTime( src ) ) ;
				jsonMerchant.addProperty( "epoch-seconds", src.toEpochSecond( ) ) ;

				return jsonMerchant ;

			}
		} ;

		gsonWithExclusions = new GsonBuilder( )
				.setExclusionStrategies( managedFieldsExclusion ) // new JodaExclusion( ),
				.registerTypeAdapter( OffsetDateTime.class, offSetHandler )
				.create( ) ;

	}

	public static final String CSAP_KUBERNETES_METER = "csap.container.kubernetes-" ;

	//
	// Default values for kubernetes apis
	//
	final int gracePeriodSeconds_0 = 0 ;

	static final String dryRun_null = null ;
	static final String pretty_null = null ;
	static final Boolean orphanDependents_null = null ;
	static final String propagationPolicy_foreground = Propogation_Policy.foreground.apiValue( ) ;

	static final Boolean allowWatchBookmarks_null = null ;
	static final String _continue_null = null ;
	static final String fieldSelector_null = null ;
	static final String labelSelector_null = null ;

	static final Integer limit_max = 5000 ;
	static final String resourceVersion_null = null ;
	static final String resourceVersionMatch_null = null ;
	static final Integer timeoutSeconds_max = 30 ;
	static final Boolean watch_null = null ;

	///
	///
	///

	public ArrayNode podCsapListing ( String namespace , String podName ) {

		return buildCsapListing(
				ListingsBuilder.labelIsNameOnly,
				"pods",
				"listPodForAllNamespaces",
				"listNamespacedPod",
				kubernetes.buildV1Api( ),
				namespace ) ;

	}

	public int daemonSetCount ( String namespace ) {

		return countResourceItems( "listDaemonSetForAllNamespaces", "listNamespacedDaemonSet", new AppsV1Api(
				kubernetes.apiClient( ) ), namespace ) ;

	}

	public ArrayNode daemonSetListing ( String namespace ) {

		return buildCsapListing(
				ListingsBuilder.labelIncludesNamespace,
				"daemonsets",
				"listDaemonSetForAllNamespaces",
				"listNamespacedDaemonSet",
				new AppsV1Api( kubernetes.apiClient( ) ),
				namespace ) ;

	}

	public int statefulCount ( String namespace ) {

		return countResourceItems( "listStatefulSetForAllNamespaces", "listNamespacedStatefulSet",
				new AppsV1Api(
						kubernetes.apiClient( ) ), namespace ) ;

	}

	public ArrayNode statefulSetListing ( String namespace ) {

		return buildCsapListing(
				ListingsBuilder.labelIncludesNamespace,
				"statefulsets",
				"listStatefulSetForAllNamespaces",
				"listNamespacedStatefulSet",
				new AppsV1Api( kubernetes.apiClient( ) ),
				namespace ) ;

	}

	public int deploymentCount ( String namespace ) {

		return countResourceItems( "listDeploymentForAllNamespaces", "listNamespacedDeployment",
				new AppsV1Api(
						kubernetes.apiClient( ) ), namespace ) ;

	}

	public ArrayNode deploymentListing ( String namespace ) {

		return buildCsapListing(
				ListingsBuilder.labelIncludesNamespace,
				"deployments",
				"listDeploymentForAllNamespaces",
				"listNamespacedDeployment",
				new AppsV1Api( kubernetes.apiClient( ) ),
				namespace ) ;

	}

	public ArrayNode secretListing ( String namespace ) {

		return buildCsapListing(
				ListingsBuilder.labelIncludesNamespace,
				"secrets",
				"listSecretForAllNamespaces",
				"listNamespacedSecret",
				new CoreV1Api( kubernetes.apiClient( ) ), namespace ) ;

	}

	public int replicaSetCount ( String namespace ) {

		return countResourceItems( "listReplicaSetForAllNamespaces", "listNamespacedReplicaSet",
				new AppsV1Api(
						kubernetes.apiClient( ) ), namespace ) ;

	}

	public ArrayNode replicaSetListing ( String namespace ) {

		return buildCsapListing(
				ListingsBuilder.labelIncludesNamespace,
				"replicasets",
				"listReplicaSetForAllNamespaces",
				"listNamespacedReplicaSet",
				new AppsV1Api( kubernetes.apiClient( ) ), namespace ) ;

	}

	public int configMapCount ( String namespace ) {

		return countResourceItems( "listConfigMapForAllNamespaces", "listNamespacedConfigMap", null,
				namespace ) ;

	}

	public ArrayNode configMapListing ( String namespace ) {

		return buildCsapListing(
				ListingsBuilder.labelIncludesNamespace,
				"configmaps",
				"listConfigMapForAllNamespaces",
				"listNamespacedConfigMap",
				kubernetes.buildV1Api( ),
				namespace ) ;

	}

	public int ingressCount ( String namespace ) {

		return countResourceItems( "listIngressForAllNamespaces", "listNamespacedIngress",
				new NetworkingV1beta1Api(
						kubernetes.apiClient( ) ), namespace ) ;

	}

	public ArrayNode ingressListing ( String namespace ) {

		return buildCsapListing(
				ListingsBuilder.labelIsNameOnly,
				"ingresses",
				"listIngressForAllNamespaces",
				"listNamespacedIngress",
				new NetworkingV1beta1Api( kubernetes.apiClient( ) ),
				namespace ) ;

	}

	public int persistentVolumeCount ( String namespace ) {

		return countResourceItems( "listPersistentVolumeClaimForAllNamespaces",
				"listNamespacedPersistentVolumeClaim",
				null, namespace ) ;

	}

	public ArrayNode persistentVolumeClaimListing ( String namespace ) {

		return buildCsapListing(
				ListingsBuilder.labelIncludesNamespace,
				"persistentvolumeclaims",
				"listPersistentVolumeClaimForAllNamespaces",
				"listNamespacedPersistentVolumeClaim",
				kubernetes.buildV1Api( ),
				namespace ) ;

	}

	public BatchV1Api buildBatchV1Api ( ) {

		return new BatchV1Api( kubernetes.apiClient( ) ) ;

	}

	public BatchV1beta1Api buildBatchV1beta1Api ( ) {

		return new BatchV1beta1Api( kubernetes.apiClient( ) ) ;

	}

	public ArrayNode jobAndCronJobListing ( String namespace ) {

		return cronJobListing( namespace ).addAll( jobListing( namespace ) ) ;

	}

	public int cronJobCount ( String namespace ) {

		return countResourceItems(
				"listCronJobForAllNamespaces",
				"listNamespacedCronJob",
				buildBatchV1beta1Api( ),
				namespace ) ;

	}

	public ArrayNode cronJobListing ( String namespace ) {

		return buildCsapListing(
				ListingsBuilder.labelIsNameOnly,
				"cronjobs",
				"listCronJobForAllNamespaces",
				"listCronJobForAllNamespaces",
				buildBatchV1beta1Api( ),
				namespace ) ;

	}

	public int jobCount ( String namespace ) {

		return countResourceItems( "listJobForAllNamespaces", "listNamespacedJob", buildBatchV1Api( ),
				namespace ) ;

	}

	public ArrayNode jobListing ( String namespace ) {

		return buildCsapListing(
				ListingsBuilder.labelIsNameOnly,
				"jobs",
				"listJobForAllNamespaces",
				"listNamespacedJob",
				buildBatchV1Api( ),
				namespace ) ;

	}

	public int serviceCount ( String namespace ) {

		return countResourceItems( "listServiceForAllNamespaces", "listNamespacedService", null, namespace ) ;

	}

	public ArrayNode serviceAndIngressListing ( String namespace ) {

		var serviceAndIngesssListing = buildCsapListing(
				ListingsBuilder.labelIsNameOnly,
				"services",
				"listServiceForAllNamespaces",
				"listNamespacedService",
				kubernetes.buildV1Api( ), namespace ) ;

		serviceAndIngesssListing.addAll( ingressListing( namespace ) ) ;

		return serviceAndIngesssListing ;

	}

	public int endpointCount ( String namespace ) {

		return countResourceItems( "listEndpointsForAllNamespaces", "listNamespacedEndpoints", null,
				namespace ) ;

	}

	public ArrayNode endpointListing ( String namespace ) {

		return buildCsapListing(
				ListingsBuilder.labelIncludesNamespace,
				"endpoints",
				"listEndpointsForAllNamespaces",
				"listNamespacedEndpoints",
				kubernetes.buildV1Api( ),
				namespace ) ;

	}

	///
	///
	///

	public int countResourceItems (
									String allNamespaceMethod ,
									String singleNamespaceMethod ,
									Object api ,
									String namespace ) {

		var resourceCount = -1 ;
		var limit_for_count = 1 ;

		String methodToInvoke = allNamespaceMethod ;

		if ( ! isAllNamespaces( namespace ) ) {

			methodToInvoke = singleNamespaceMethod ;

		}

		var timer = Application.getInstance( ).metrics( ).startTimer( ) ;
		var resourceReport = buildResourceReport( methodToInvoke, api, namespace, limit_for_count,
				fieldSelector_null, labelSelector_null ) ;
		var nanos = Application.getInstance( ).metrics( ).stopTimer( timer, CSAP_KUBERNETES_METER
				+ "resource.count" ) ;

		logger.debug( "method: {}      duration: {}ms", methodToInvoke, TimeUnit.NANOSECONDS.toMillis( nanos ) ) ;
//		logger.info( "methodToInvoke: {}, \n {}", methodToInvoke, CSAP.jsonPrint( resourceReport )  );

		resourceCount = resourceReport.path( "items" ).size( )
				+ resourceReport.path( "metadata" ).path( "remainingItemCount" ).asInt( ) ;

		return resourceCount ;

	}

	private boolean isAllNamespaces ( String namespace ) {

		if ( namespace == null || namespace.equals( "all" ) )
			return true ;

		return false ;

	}

	final static boolean labelIsNameOnly = true ;
	final static boolean labelIncludesNamespace = false ;

	public ArrayNode buildCsapListing (
										boolean labelIsNameOnly ,
										String apiKey ,
										String allNamespaceMethod ,
										String singleNamespaceMethod ,
										Object api ,
										String namespace ) {

		var resourceReport = buildNamespacedResourceReport(
				allNamespaceMethod,
				singleNamespaceMethod,
				api,
				namespace,
				fieldSelector_null ) ;

		var resourceListing = jsonMapper.createArrayNode( ) ;

		CSAP.jsonStream( resourceReport.path( "items" ) )

				.filter( JsonNode::isObject )
				.map( resourceDetailReport -> (ObjectNode) resourceDetailReport )

				.forEach( resourceDetailReport -> {

					ObjectNode item = resourceListing.addObject( ) ;

					var name = resourceDetailReport.path( "metadata" ).path( "name" ).asText( ) ;
					var derivedNamespaceName = resourceDetailReport.path( "metadata" ).path( "namespace" ).asText( ) ;

					var label = name + "," + derivedNamespaceName ;

					if ( labelIsNameOnly ) {

						label = name ; // custom title generation

					}

					if ( apiKey.equals( "pods" ) ) {

						var hostIp = resourceDetailReport.path( "status" ).path( "hostIP" ).asText( ) ;
						resourceDetailReport.put( "hostname", kubernetes.podHostName( hostIp ) ) ;

					}

					item.put( DockerJson.list_label.json( ), label ) ;

					item.put( "folder", true ) ;
					item.put( "lazy", true ) ;

					item.set( DockerJson.list_attributes.json( ), resourceDetailReport ) ;

					kubernetes.addApiPath( resourceDetailReport,
							apiKey,
							derivedNamespaceName,
							name ) ;

				} ) ;

		return resourceListing ;

	}

	public ObjectNode buildNamespacedResourceReport (
														String allNamespaceMethod ,
														String singleNamespaceMethod ,
														Object api ,
														String namespace ,
														String fieldSelector ) {

		String methodToInvoke = allNamespaceMethod ;

		if ( ! isAllNamespaces( namespace ) ) {

			methodToInvoke = singleNamespaceMethod ;

		}

		var timer = Application.getInstance( ).metrics( ).startTimer( ) ;

		var resourceReport = buildResourceReport(
				methodToInvoke,
				api,
				namespace,
				limit_max,
				fieldSelector,
				labelSelector_null ) ;

		var nanos = Application.getInstance( ).metrics( ).stopTimer( timer,
				CSAP_KUBERNETES_METER + "resource.report" ) ;

		logger.debug( "method: {}      duration: {}ms", methodToInvoke, TimeUnit.NANOSECONDS.toMillis( nanos ) ) ;
//		logger.info( "methodToInvoke: {}, \n {}", methodToInvoke, CSAP.jsonPrint( resourceReport )  );

		return resourceReport ;

	}

	public ObjectNode buildResourceReport ( String methodName , Object api ) {

		return buildResourceReport( methodName, api, null ) ;

	}

	public ObjectNode buildResourceReport ( String methodName , Object api , String namespace ) {

		return buildResourceReport( methodName, api, namespace, limit_max, null, labelSelector_null ) ;

	}

	public ObjectNode buildResourceReport ( String methodName , Object api , String namespace , String labelSelector ) {

		return buildResourceReport(
				methodName,
				api,
				namespace,
				limit_max,
				fieldSelector_null,
				labelSelector ) ;

	}

	/*
	 * Reflection is preferred: - time as a percentage of procedure call is very
	 * small - provider api refactorings are frequent - normalization ensures
	 * consistency and ease of migrations
	 */
	public ObjectNode buildResourceReport (
											String methodName ,
											Object api ,
											String namespace ,
											int reportMaxSize ,
											String fieldSelector ,
											String labelSelector ) {

		if ( api == null ) {

			api = new CoreV1Api( kubernetes.apiClient( ) ) ;

		}

		ObjectNode listingReport = null ;

		var allMethods = Arrays.asList( api.getClass( ).getMethods( ) ).stream( )
				.filter( methodFound -> methodFound.getName( ).equals( methodName ) )
				.map( methodFound -> {

					return methodFound.toGenericString( ) ;

				} )
				.collect( Collectors.joining( "\n\t" ) ) ;

		logger.debug( "matching method(s): filter: '{}'     namespace: '{}' \n\t '{}'", methodName, namespace,
				allMethods ) ;

		try {

			var matchedMethods = Arrays.asList( api.getClass( ).getMethods( ) ).stream( )
					.filter( methodFound -> methodFound.getName( ).equals( methodName ) )
					.collect( Collectors.toList( ) ) ;

			if ( matchedMethods.size( ) == 1 ) {

				var apiMethod = matchedMethods.get( 0 ) ;

				Object listing ;

				if ( methodName.equals( "getCode" ) ) {

					listing = apiMethod.invoke( api ) ;

				} else if ( isAllNamespaces( namespace ) ) {

					logger.debug( "param 0 type: '{}'", apiMethod.getParameters( )[0].getType( ) ) ;

					if ( apiMethod.getParameters( )[0].getType( ) == String.class ) {

						// non namespace query
						listing = apiMethod.invoke( api,
								"true", allowWatchBookmarks_null, _continue_null,
								fieldSelector, labelSelector_null,
								reportMaxSize,
								resourceVersion_null, resourceVersionMatch_null,
								timeoutSeconds_max,
								watch_null ) ;

					} else {

						// all namespaced query
						listing = apiMethod.invoke( api,
								allowWatchBookmarks_null, _continue_null,
								fieldSelector, labelSelector,
								reportMaxSize,
								pretty_null, resourceVersion_null, resourceVersionMatch_null,
								timeoutSeconds_max, watch_null ) ;

					}

				} else {

					// namespaced query
					listing = apiMethod.invoke( api,
							namespace,
							pretty_null, allowWatchBookmarks_null, _continue_null,
							fieldSelector,
							labelSelector,
							reportMaxSize,
							resourceVersion_null, resourceVersionMatch_null,
							timeoutSeconds_max, watch_null ) ;

				}

				//
				// Double marshalling to json - optimize
				//
				listingReport = (ObjectNode) jsonMapper.readTree( gsonWithExclusions.toJson( listing ) ) ;

			} else {

				throw new Exception( "Invalid matchedMethods count " + matchedMethods.size( ) ) ;

			}

		} catch ( Exception e ) {

			logger.warn( "Failed invoking: {} {}", methodName, CSAP.buildCsapStack( e ) ) ;
			listingReport = kubernetes.buildErrorResponse( "Failed to retrieve listing", e ) ;

		}

		return listingReport ;

	}

}
