package org.csap.agent.integration.container ;

import static org.assertj.core.api.Assertions.assertThat ;

import java.io.File ;
import java.util.Arrays ;
import java.util.List ;
import java.util.Optional ;
import java.util.Spliterator ;
import java.util.Spliterators ;
import java.util.concurrent.TimeUnit ;
import java.util.stream.Collectors ;
import java.util.stream.StreamSupport ;

import javax.inject.Inject ;

import org.csap.agent.CsapBareTest ;
import org.csap.agent.CsapThinNoProfile ;
import org.csap.agent.container.C7 ;
import org.csap.agent.container.kubernetes.KubernetesConfiguration ;
import org.csap.agent.container.kubernetes.KubernetesIntegration ;
import org.csap.agent.container.kubernetes.K8 ;
import org.csap.agent.container.kubernetes.KubernetesSettings ;
import org.csap.agent.model.Application ;
import org.csap.agent.model.ProjectLoader ;
import org.csap.agent.model.ServiceInstance ;
import org.csap.helpers.CSAP ;
import org.csap.helpers.CsapApplication ;
import org.junit.jupiter.api.AfterAll ;
import org.junit.jupiter.api.Assumptions ;
import org.junit.jupiter.api.BeforeAll ;
import org.junit.jupiter.api.Disabled ;
import org.junit.jupiter.api.DisplayName ;
import org.junit.jupiter.api.Nested ;
import org.junit.jupiter.api.Tag ;
import org.junit.jupiter.api.Test ;
import org.junit.jupiter.api.TestInstance ;
import org.springframework.beans.factory.annotation.Autowired ;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration ;
import org.springframework.boot.context.properties.ConfigurationProperties ;
import org.springframework.context.ApplicationContext ;
import org.springframework.context.annotation.Import ;
import org.springframework.core.env.Environment ;
import org.springframework.test.context.ActiveProfiles ;

import com.fasterxml.jackson.databind.JsonNode ;
import com.fasterxml.jackson.databind.node.ArrayNode ;
import com.fasterxml.jackson.databind.node.ObjectNode ;
import com.google.gson.JsonSyntaxException ;

import io.kubernetes.client.openapi.apis.VersionApi ;

/**
 * 
 * Test CSAP apis to kubernetes
 * 
 * @link https://kubernetes.io/docs/reference/generated/kubernetes-api/v1.12/
 * 
 * 
 * @author peter.nightingale
 *
 */
@Tag ( "containers" )
@DisplayName ( "Kubernetes Api: org.csap tests" )
@ConfigurationProperties ( prefix = "test.junit.kubernetes" )
@ActiveProfiles ( {
		"agent", CsapBareTest.PROFILE_JUNIT
} )
// configure configuration and required injected classes
@Import ( value = {
		KubernetesConfiguration.class, JacksonAutoConfiguration.class
} )

public class KubernetesCsapTests extends CsapThinNoProfile {

	@Autowired
	ApplicationContext applicationContext ;

	@Autowired
	Environment springEnvironment ;

	String TEST_NAMESPACE = "junit-kubernetes-csap" ;

	@Inject
	KubernetesSettings kubernetesSettings ; // binds to yml property

	@Inject
	KubernetesIntegration kubernetes = null ;

	@Inject
	KubernetesConfiguration kubernetesConfig = null ;

	String configUrl ;

	static ObjectNode test_definitions = null ;

	KubernetesApiTests apiTester = new KubernetesApiTests( ) ;

	@BeforeAll
	void beforeAll ( ) {

		logger.info( CsapApplication.testHeader( ) ) ;

		Assumptions.assumeTrue( kubernetesSettings != null && kubernetesSettings.isEnabled( ) ) ;

		try {

			File extractDir = new File( System.getProperty( "user.home" ), "agent-junit-folder" ) ;
			kubernetesSettings.setConfigFile( ( new File( extractDir, "config" ) ).getAbsolutePath( ) ) ;

			// get the latest security
			KubernetesIntegration.buildAndCacheDesktopCredentials( logger, getConfigUrl( ), extractDir ) ;

			// rebuild connection pools
			kubernetesConfig.buildApiClient( ) ;

			while ( ! kubernetes.areMinimumMastersReady( 1 ) ) {

				logger.info( "Waiting for kubenetes connection" ) ;
				apiTester.sleep( 2 ) ;

			}

			test_definitions = (ObjectNode) getJsonMapper( )
					.readTree( new File( getClass( )
							.getResource( "k8-test-definition.json" )
							.toURI( ) ) ) ;

			// verifies the build
			apiTester.setApiV1( kubernetes.buildV1Api( ) ) ;

			kubernetesConfig.setCsapApi( getCsapApis( ) ) ;

			assertThat( apiTester.namespaces( ) ).doesNotContain( "csap-test-ha" ) ;

		} catch ( Exception e ) {

			logger.warn( "{}", CSAP.buildCsapStack( e ) ) ;

		}

	}

	@Nested
	@TestInstance ( TestInstance.Lifecycle.PER_CLASS )
	@DisplayName ( "Stateless tests" )
	class Stateless {

		@Test
		void event_summary_report ( ) {

			logger.warn( CsapApplication.testHeader( ) ) ;

			var hourEvents = kubernetes.buildEventHealthReport( 60, 1000, List.of( ".*cron" ) ) ;

			var hourEventCount = CSAP.jsonStream( hourEvents )
					.mapToInt( JsonNode::asInt )
					.sum( ) ;
			logger.info( "hourEventCount: {},  hourEvents: \n\t{}", hourEventCount, hourEvents ) ;

			var formattedHourEvents = CSAP.asStreamHandleNulls( hourEvents )
					.map( eventName -> {

						var count = hourEvents.path( eventName ).asInt( ) ;
						return CSAP.pad( eventName ) + ": " + count ;

					} )
					.collect( Collectors.joining( "\n" ) ) ;

			logger.info( "formattedEvents: {}", formattedHourEvents ) ;

			var eventsLast10Minutes = kubernetes.buildEventHealthReport( 1, 10, List.of( ".*cron" ) ) ;

			logger.info( "eventsLast10Minutes: {} \n\t{}", eventsLast10Minutes.size( ), eventsLast10Minutes ) ;

		}

		@Test
		public void event_total_count ( )
			throws Exception {

			var allNamespaceCount = kubernetes.eventCount( null ) ;

			var csapTestCount = kubernetes.eventCount( "csap-test" ) ;

			logger.info( "allNamespaceCount: {}, csapTestCount: {}", allNamespaceCount, csapTestCount ) ;

		}

		@Test
		void events_listing ( ) {

			logger.info( CsapApplication.testHeader( ) ) ;

			var events = kubernetes.eventListing( null, 3 ) ;

			logger.debug( "events: {}", CSAP.jsonPrint( events ) ) ;

			assertThat( CSAP.jsonPrint( events ) ).doesNotContain( "Failed to retrieve event listing" ) ;

			var eventsLongListing = CSAP.jsonStream( events )
					.map( event -> {

						return event.path( "attributes" ).path( "simpleName" ).asText( ) + ": "
								+ event.path( "attributes" ).path( "message" ).asText( ) ;

					} )
					.collect( Collectors.joining( "\n\t" ) ) ;

			logger.info( "eventsLongListing: \n\t{}", eventsLongListing ) ;

			if ( events.size( ) > 2 ) {

				var firstEvent = (ObjectNode) events.path( 1 ) ;
				logger.info( "firstEvent: {}", CSAP.jsonPrint( firstEvent ) ) ;

				assertThat( firstEvent.path( "attributes" ).path( "eventTime" ).asText( ) ).isNotEmpty( ) ;

			}

		}

		@Test
		void labels_loaded ( ) {

			logger.info( CsapApplication.testHeader( ) ) ;

			var path = "/verify-simple" + K8.labelsByType.spath( ) ;
			var labelDef = test_definitions.at( path ).toString( ) ;

			logger.info( "path: {}, labelDef: {} ", path, labelDef ) ;

			assertThat( labelDef ).isNotEmpty( ) ;

		}

		@Test
		public void kubernetes_summary ( )
			throws Exception {

			logger.warn( CsapApplication.testHeader( ) ) ;

			var testMeters = getJsonMapper( ).createArrayNode( ) ;
			testMeters.add( "calico-node" ) ;
			testMeters.add( "coredns" ) ;
			testMeters.add( "etcd" ) ;

			kubernetes.metricsBuilder( ).setTestMeterNames( testMeters ) ;
			// kubernetes.setTestHost( "" ) ;

			var jsonSample = getCsapApis( ).metrics( ).startTimer( ) ;
			JsonNode allNameSpaceReport = kubernetes.buildSummaryReport( null ) ;
			var jsonNanos = getCsapApis( ).metrics( ).stopTimer( jsonSample, "rawSample" ) ;
			var summaryMs = TimeUnit.NANOSECONDS.toMillis( jsonNanos ) ;
			logger.info( "allNameSpaceReport: {} \n\n loaded in {} ms", CSAP.jsonPrint( allNameSpaceReport ),
					summaryMs ) ;

			// when this fails - time to read release notes - then update
			assertThat( allNameSpaceReport.path( "version" ).asText( ) )
					.as( "summary version" )
					.startsWith( "v1." ) ;

			JsonNode kubeSystemSummaryReport = kubernetes.buildSummaryReport( K8.systemNamespace.val( ) ) ;
			logger.info( "kubeSystemSummaryReport: {}", CSAP.jsonPrint( kubeSystemSummaryReport ) ) ;

			assertThat( kubeSystemSummaryReport.at( "/podReport/count" ).asInt( ) )
					.isLessThan( allNameSpaceReport.at( "/podReport/count" ).asInt( ) ) ;

			// metrics

			assertThat( kubeSystemSummaryReport.at( "/metrics/current/memoryGb" ).asDouble( ) )
					.isGreaterThan( 7 ) ;

			assertThat( kubeSystemSummaryReport.at( "/metrics/containers/calico-node/memoryInMb" ).asInt( ) )
					.isGreaterThan( 10 ) ;

			assertThat( kubeSystemSummaryReport.at( "/metrics/current/resources/requests/cores" ).asDouble( ) )
					.isEqualTo( 1.7 ) ;

		}

		@Test
		public void count_resources_using_reflection ( )
			throws Exception {

			logger.warn( CsapApplication.testHeader( ) ) ;

			var podsAllNamespaces = kubernetes.listingsBuilder( ).countResourceItems( "listPodForAllNamespaces",
					"listNamespacedPod", null,
					null ) ;
			var podsInDefault = kubernetes.listingsBuilder( ).countResourceItems( "listPodForAllNamespaces",
					"listNamespacedPod", null,
					K8.systemNamespace.val( ) ) ;

			logger.info( "podsAllNamespaces: {}, podsInDefault: {}", podsAllNamespaces, podsInDefault ) ;

			assertThat( podsAllNamespaces ).isGreaterThanOrEqualTo( 20 ) ;
			assertThat( podsAllNamespaces ).isGreaterThanOrEqualTo( 15 ) ;

			//
			//
			var eventCount = kubernetes.listingsBuilder( ).countResourceItems( "listEventForAllNamespaces",
					"listNamespacedEvent", null,
					null ) ;

			logger.info( "eventCount: {}", eventCount ) ;
			assertThat( eventCount ).isGreaterThanOrEqualTo( 2 ) ;

			//
			// general count methods
			//
			logger.info( "configMapCount: {} kube-system: {}", kubernetes.listingsBuilder( ).configMapCount( null ),
					kubernetes.listingsBuilder( ).configMapCount( K8.systemNamespace.val( ) ) ) ;
			logger.info( "ingressCount: {}", kubernetes.listingsBuilder( ).ingressCount( null ) ) ;
			logger.info( "persistentVolumeCount: {}", kubernetes.listingsBuilder( ).persistentVolumeCount( null ) ) ;
			logger.info( "cronJobCount: {}", kubernetes.listingsBuilder( ).cronJobCount( null ) ) ;
			logger.info( "jobCount: {}", kubernetes.listingsBuilder( ).jobCount( null ) ) ;
			logger.info( "serviceCount: {}", kubernetes.listingsBuilder( ).serviceCount( null ) ) ;
			logger.info( "endpointCount: {}", kubernetes.listingsBuilder( ).endpointCount( null ) ) ;
			logger.info( "daemonSetCount: {}", kubernetes.listingsBuilder( ).daemonSetCount( null ) ) ;
			logger.info( "statefulCount: {}", kubernetes.listingsBuilder( ).statefulCount( null ) ) ;
			logger.info( "deploymentCount: {}", kubernetes.listingsBuilder( ).deploymentCount( null ) ) ;
			logger.info( "replicaSetCount: {}", kubernetes.listingsBuilder( ).replicaSetCount( null ) ) ;

			assertThat( kubernetes.listingsBuilder( ).configMapCount( null ) ).isGreaterThanOrEqualTo( 2 ) ;
			assertThat( kubernetes.listingsBuilder( ).configMapCount( K8.systemNamespace.val( ) ) )
					.isGreaterThanOrEqualTo( 2 )
					.isLessThan( kubernetes.listingsBuilder( ).configMapCount( null ) ) ;
			assertThat( kubernetes.listingsBuilder( ).ingressCount( null ) ).isGreaterThanOrEqualTo( 2 ) ;
			assertThat( kubernetes.listingsBuilder( ).persistentVolumeCount( null ) ).isGreaterThanOrEqualTo( 2 ) ;
			assertThat( kubernetes.listingsBuilder( ).cronJobCount( null ) ).isGreaterThanOrEqualTo( 1 ) ;
			assertThat( kubernetes.listingsBuilder( ).jobCount( null ) ).isGreaterThanOrEqualTo( 0 ) ; // note jobs only
																										// run every
																										// hour
			assertThat( kubernetes.listingsBuilder( ).serviceCount( null ) ).isGreaterThanOrEqualTo( 2 ) ;
			assertThat( kubernetes.listingsBuilder( ).endpointCount( null ) ).isGreaterThanOrEqualTo( 2 ) ;
			assertThat( kubernetes.listingsBuilder( ).daemonSetCount( null ) ).isGreaterThanOrEqualTo( 2 ) ;
			assertThat( kubernetes.listingsBuilder( ).statefulCount( null ) ).isGreaterThanOrEqualTo( 2 ) ;
			assertThat( kubernetes.listingsBuilder( ).deploymentCount( null ) ).isGreaterThanOrEqualTo( 2 ) ;
			assertThat( kubernetes.listingsBuilder( ).replicaSetCount( null ) ).isGreaterThanOrEqualTo( 2 ) ;

		}

		@Test
		public void list_resources_using_reflection ( )
			throws Exception {

			logger.warn( CsapApplication.testHeader( ) ) ;

			//
			// namespace listing: non-namespace api
			//
			var namespaceReport = kubernetes.listingsBuilder( ).buildResourceReport( "listNamespace", null ) ;

			logger.debug( "namespaceReport: {}", namespaceReport ) ;

			var namespaceNames = CSAP.jsonStream( namespaceReport.path( "items" ) )
					.map( item -> item.path( "metadata" ).path( "name" ).asText( ) )
					.collect( Collectors.toList( ) ) ;

			logger.info( "namespaceListing: {}", namespaceNames ) ;

			assertThat( namespaceNames )
					.hasSizeGreaterThan( 5 )
					.contains( "default", K8.systemNamespace.val( ), "kube-public" ) ;

			//
			// version info listing: empty param api
			//
			var codeReport = kubernetes.listingsBuilder( ).buildResourceReport( "getCode", new VersionApi(
					kubernetesConfig
							.apiClient( ) ) ) ;

			logger.info( "codeReport: {}", codeReport ) ;
			assertThat( codeReport.path( "major" ).asInt( ) ).isEqualTo( 1 ) ;
			assertThat( codeReport.path( "minor" ).asInt( ) ).isGreaterThanOrEqualTo( 20 ) ;
			assertThat( codeReport.path( "compiler" ).asText( ) ).isEqualTo( "gc" ) ;

			//
			// service accounts: all namespace and specific namespace api
			//

			var serviceAccountForAll = kubernetes.listingsBuilder( ).buildResourceReport(
					"listServiceAccountForAllNamespaces", null ) ;
			logger.debug( "serviceAccountForAll: {}", serviceAccountForAll ) ;
			assertThat( serviceAccountForAll.path( "items" ).size( ) ).isGreaterThanOrEqualTo( 20 ) ;

			var serviceAccountForNamespace = kubernetes.listingsBuilder( )
					.buildResourceReport( "listNamespacedServiceAccount", null, K8.systemNamespace.val( ) ) ;
			logger.debug( "serviceAccountForNamespace: {}", serviceAccountForNamespace ) ;
			assertThat( serviceAccountForNamespace.path( "items" ).size( ) ).isGreaterThanOrEqualTo( 1 ) ;

		}

		@Test
		public void verify_system_summary_listing ( )
			throws Exception {

			logger.warn( CsapApplication.testHeader( ) ) ;

			ArrayNode configurationReport = kubernetes.buildSystemSummaryListing( null ) ;
			logger.debug( "configListing: {}", CSAP.jsonPrint( configurationReport ) ) ;

			//
			// namespace listing
			//
			var namespaceListing = CSAP.jsonStream( configurationReport )
					.filter( item -> item.path( "label" ).asText( ).equals( "Namespaces" ) )
					.findFirst( )
					.get( ) ;

			logger.debug( "namespaceListing: {}", CSAP.jsonPrint( namespaceListing ) ) ;

			var namespaceReports = namespaceListing.path( "attributes" ) ;

			logger.info( "namespaceReport size: {}", namespaceReports.size( ) ) ;
			assertThat( namespaceReports.size( ) ).isGreaterThan( 5 ) ;

			var defaultNamespaceReport = namespaceReports.path( "default" ) ;
			logger.info( "defaultNamespaceReport: {}", CSAP.jsonPrint( defaultNamespaceReport ) ) ;
			assertThat( defaultNamespaceReport.path( "apiPath" ).asText( ) )
					.isEqualTo( "/api/v1/namespaces/default" ) ;

			var namespaceNames = CSAP.jsonStream( configurationReport )
					.filter( category -> category.path( "label" ).asText( ).equals( "Namespaces" ) )
					.flatMap( category -> {

						var nameSpacesInAttributes = StreamSupport.stream(
								Spliterators.spliteratorUnknownSize( category.path( "attributes" ).fieldNames( ),
										Spliterator.ORDERED ),
								false ) ;

						return nameSpacesInAttributes ;

					} )
					.collect( Collectors.toList( ) ) ;

			logger.info( "namespaceNames: {}", namespaceNames ) ;

			assertThat( namespaceNames )
					.hasSizeGreaterThan( 5 )
					.contains( "default", K8.systemNamespace.val( ), "kube-public" ) ;

			//
			//
			//
			var storageListing = CSAP.jsonStream( configurationReport )
					.filter( item -> item.path( "label" ).asText( ).equals( "Storage: Classes" ) )
					.findFirst( )
					.get( ) ;

			logger.debug( "storageClasses: {}", CSAP.jsonPrint( storageListing ) ) ;
			logger.info( "storageClasses size: {}", storageListing.path( "attributes" ).size( ) ) ;
			assertThat( storageListing.path( "attributes" ).size( ) ).isGreaterThan( 0 ) ;

			//
			//
			//
			var authRolesListing = CSAP.jsonStream( configurationReport )
					.filter( item -> item.path( "label" ).asText( ).equals( "Auth: Roles" ) )
					.findFirst( )
					.get( ) ;

			logger.debug( "authRolesListing: {}", CSAP.jsonPrint( authRolesListing ) ) ;

			logger.info( "authRoles size: {}", authRolesListing.path( "attributes" ).size( ) ) ;
			assertThat( authRolesListing.path( "attributes" ).size( ) ).isGreaterThan( 10 ) ;

			//
			//
			//
			var roleBindingsListing = CSAP.jsonStream( configurationReport )
					.filter( item -> item.path( "label" ).asText( ).equals( "Auth: Role Bindings" ) )
					.findFirst( )
					.get( ) ;

			logger.info( "roleBindingsReport size: {}", roleBindingsListing.path( "attributes" ).size( ) ) ;
			assertThat( roleBindingsListing.path( "attributes" ).size( ) ).isGreaterThan( 2 ) ;

			logger.debug( "roleBindingsReport: {}", CSAP.jsonPrint( roleBindingsListing ) ) ;
			assertThat( roleBindingsListing
					.path( "attributes" )
					.path( "kube-proxy,kube-system" )
					.path( "apiPath" ).asText( ) )
							.isEqualTo(
									"/apis/rbac.authorization.k8s.io/v1/namespaces/kube-system/rolebindings/kube-proxy" ) ;

			//
			//
			//
			var secretsFolderOptional = CSAP.jsonStream( configurationReport )
					.filter( category -> category.path( "label" ).asText( ).equals( "Auth: Secrets" ) )
					.findFirst( ) ;

			assertThat( secretsFolderOptional ).isNotEmpty( ) ;

			var secretsFolder = secretsFolderOptional.get( ) ;

			logger.info( "secretsFolder: {}", CSAP.jsonPrint( secretsFolder ) ) ;

			//
			// Verify all the sections
			//
			var categorys = CSAP.jsonStream( configurationReport )
					.map( category -> category.path( "label" ).asText( ) )
					.collect( Collectors.toList( ) ) ;

			logger.info( "categorys: {}", categorys ) ;

			assertThat( categorys )
					.contains( "Version",
							"Namespaces",
							"Nodes",
							"Storage: Persistent Volumes",
							"Auth: Service Accounts",
							"Storage: Classes",
							"Auth: Secrets",
							"Auth: Roles",
							"Auth: Role Bindings",
							"Auth: Cluster Roles",
							"Auth: Cluster Role Bindings",
							"Api: Providers",
							"Api: Resources" ) ;

		}

		@Test
		public void list_resources_by_provider ( )
			throws Exception {

			logger.info( CsapApplication.testHeader( ) ) ;

			ArrayNode resourceListing = kubernetes.apiProviderResourceTypes_listing( "/api/v1/" ) ;
			logger.info( "resourceListing: {}", CSAP.jsonPrint( resourceListing ) ) ;
			assertThat( resourceListing.toString( ) )
					.as( "resourceListing" )
					.contains( "Pod", "Namespace" ) ;

		}

		@Test
		public void list_resources_types_browseable ( )
			throws Exception {

			logger.info( CsapApplication.testHeader( ) ) ;

			ArrayNode resourceTypeListing = kubernetes.apiResourceType_listing( true ) ;
			logger.info( "resourceListing: {}", CSAP.jsonPrint( resourceTypeListing ) ) ;
			assertThat( resourceTypeListing.toString( ) )
					.as( "resourceListing" )
					.contains( "PodMetrics", "Pod", "Namespace" )
					.contains( "/api/v1NAMESPACE/pods", "/apis/apps/v1NAMESPACE/replicasets" ) ;

		}

		@Test
		public void list_resource_browseable ( )
			throws Exception {

			logger.info( CsapApplication.testHeader( ) ) ;

			// generate path
			var kubePodCommand = kubernetes.buildCliCommand( "specification",
					"/apis/metrics.k8s.io/v1beta1/namespaces/csap-monitoring/pods/alertmanager-main-0" ) ;
			assertThat( kubePodCommand ).isEqualTo(
					"--namespace=csap-monitoring get -o=yaml pods.v1beta1.metrics.k8s.io alertmanager-main-0" ) ;

			ArrayNode podReportsUserNamespace = kubernetes.apiResource_listing( "/api/v1/namespaces/kube-system/pods",
					"/api/v1/namespaces/kube-system/pods", 2 ) ;
			logger.info( "podReportsUserNamespace: {}", CSAP.jsonPrint( podReportsUserNamespace ) ) ;
			assertThat( podReportsUserNamespace.at( "/0/attributes/metadata/name" ).asText( ) ).isNotEmpty( ) ;
			assertThat( podReportsUserNamespace.at( "/0/attributes/" + K8.apiPath.val( ) ).asText( ) )
					.isNotEmpty( ) ;

			assertThat( podReportsUserNamespace.at( "/0/attributes/" + K8.apiPath.val( ) ).asText( ) )
					.startsWith( "/api/v1/namespaces/kube-system/pods" ) ;

			ArrayNode podReportsAllNamespace = kubernetes.apiResource_listing( "/api/v1/pods", "/api/v1NAMESPACE/pods",
					2 ) ;
			logger.info( "podReportsAllNamespace: {}", CSAP.jsonPrint( podReportsAllNamespace ) ) ;
			assertThat( podReportsAllNamespace.at( "/0/attributes/" + K8.apiPath.val( ) ).asText( ) )
					.matches( "/api/v1/namespaces/.*/pods/.*" ) ;

		}

		@Test
		public void list_replicaSets ( )
			throws Exception {

			logger.info( CsapApplication.testHeader( ) ) ;

			ArrayNode replicaSetListing = kubernetes.listingsBuilder( ).replicaSetListing( null ) ;
			logger.debug( "replicaSetListing: {}", CSAP.jsonPrint( replicaSetListing ) ) ;
			assertThat( replicaSetListing.toString( ) )
					.as( "replicaSetListing" )
					.contains( "kubernetes", "coredns" ) ;

			var firstReplica = replicaSetListing.path( 0 ) ;

			logger.info( "firstReplica: {}", CSAP.jsonPrint( firstReplica ) ) ;

			assertThat( firstReplica.path( C7.list_label.val( ) ).asText( ) ).isNotEmpty( ) ;

			assertThat( firstReplica.path( "folder" ).asBoolean( ) ).isTrue( ) ;

			assertThat( firstReplica.path( "lazy" ).asBoolean( ) ).isTrue( ) ;

			assertThat(
					firstReplica.path( "attributes" ).path( K8.apiPath.val( ) ).asText( ) )
							.matches( "/apis/apps/v1/namespaces/.*/replicasets/.*" ) ;

			assertThat( firstReplica.has( C7.list_attributes.val( ) ) ).isTrue( ) ;

		}

		@Test
		public void volumeSummaryReport ( )
			throws Exception {

			logger.info( CsapApplication.testHeader( ) ) ;
			var volumeReport = kubernetes.reportsBuilder( ).volumeReport( ) ;
			// var nodeReport = kubernetes.eventReport( "nginx-simple-namespace", 500 ) ;

			logger.info( "volumeReport: {}", CSAP.jsonPrint( volumeReport ) ) ;

		}

		@Test
		public void eventSummaryReport ( )
			throws Exception {

			logger.warn( CsapApplication.testHeader( ) ) ;
			var eventReport = kubernetes.reportsBuilder( ).eventReport( null, 5 ) ;
			// var nodeReport = kubernetes.eventReport( "nginx-simple-namespace", 500 ) ;

			logger.info( "eventReport: {}", CSAP.jsonPrint( eventReport ) ) ;

			if ( eventReport.isArray( ) && eventReport.size( ) > 0 ) {

				var firstReport = (ObjectNode) eventReport.path( 0 ) ;
				assertThat( firstReport.path( "dateTime" ).asText( ) )
						.isNotEmpty( )
						.isNotEqualTo( "null" ) ;

			}

		}

		@Test
		public void nodeSummaryReport ( )
			throws Exception {

			logger.info( CsapApplication.testHeader( ) ) ;
			// namespace = null ;
			var nodeReport = kubernetes.reportsBuilder( ).nodeReports( ) ;

			logger.info( "nodeReport: {}", CSAP.jsonPrint( nodeReport ) ) ;

			var dev04Report = nodeReport.path( 0 ) ;
			assertThat( dev04Report.path( "ready" ).asBoolean( ) ).isTrue( ) ;
			assertThat( dev04Report.path( "apiPath" ).asText( ) ).startsWith( "/api/v1/nodes" ) ;
			assertThat( dev04Report.at( "/resources/requests/cores" ).asDouble( ) ).isEqualTo( 1.7 ) ;

		}

		@Test
		public void podNamespaceSummaryReport ( )
			throws Exception {

			logger.info( CsapApplication.testHeader( ) ) ;

			var namespace = "csap-test" ;
			namespace = null ;
			var namespaceSummaryReport = kubernetes.reportsBuilder( ).podNamespaceSummaryReport( namespace ) ;

			logger.info( "namespaceSummaryReport: {}", CSAP.jsonPrint( namespaceSummaryReport ) ) ;

			var kubeSystemNamespace = CSAP.jsonStream( namespaceSummaryReport )
					.filter( namespaceReport -> namespaceReport.path( "name" ).asText( ).equals( K8.systemNamespace
							.val( ) ) )
					.findFirst( ) ;

			assertThat( kubeSystemNamespace ).isNotEmpty( ) ;
			assertThat( kubeSystemNamespace.get( ).path( "total" ).asInt( ) ).isGreaterThan( 5 ) ;
			assertThat( kubeSystemNamespace.get( ).at( "/metrics/cores" ).isMissingNode( ) ).as( "found cores " )
					.isFalse( ) ;
			assertThat( kubeSystemNamespace.get( ).at( "/metrics/cores" ).asDouble( ) ).isGreaterThan( 0.0 ) ;

		}

		@Test
		public void podNamespaceDetailReport ( )
			throws Exception {

			logger.info( CsapApplication.testHeader( ) ) ;

			var namespace = K8.systemNamespace.val( ) ;

			var podReport = kubernetes.reportsBuilder( ).podSummaryReport( namespace, null ) ;

			logger.info( "podReport: {}", CSAP.jsonPrint( podReport ) ) ;

			var calicoDeployment = CSAP.jsonStream( podReport )
					.filter( pod -> pod.path( "owner" ).asText( ).equals( "calico-node" ) )
					.findFirst( ) ;

			assertThat( calicoDeployment ).isNotEmpty( ) ;

			logger.info( "calicoDeployment: {}", CSAP.jsonPrint( calicoDeployment.get( ) ) ) ;

			assertThat( calicoDeployment.get( ).at( "/pods/0/metrics/cores" ).isMissingNode( ) ).as( "found cores " )
					.isFalse( ) ;

		}

		@Test
		public void list_namespaces ( )
			throws Exception {

			logger.info( CsapApplication.testHeader( ) ) ;

			ArrayNode namespaces = kubernetes.namespaceInfo( null ) ;
			logger.info( "namespaces: {}", CSAP.jsonPrint( namespaces ) ) ;

			assertThat( namespaces.size( ) ).isGreaterThanOrEqualTo( 3 ).as( "minimum namespaces" ) ;

			ArrayNode kubeSystemNamespaces = kubernetes.namespaceInfo( K8.systemNamespace.val( ) ) ;
			logger.info( "single kubeSystemNamespace: {}", CSAP.jsonPrint( kubeSystemNamespaces ) ) ;

			var systemMatch = CSAP.jsonStream( kubeSystemNamespaces )
					.filter( namespace -> namespace.at( "/metadata/name" ).asText( ).equals( K8.systemNamespace
							.val( ) ) )
					.findFirst( ) ;

			assertThat( systemMatch.isPresent( ) ).isTrue( ).as( "kube-system found" ) ;

		}

		@Test
		public void configMap_list ( )
			throws Exception {

			logger.info( CsapApplication.testHeader( ) ) ;

			var configMapListing = kubernetes.listingsBuilder( ).configMapListing( null ) ;
			logger.debug( "configMapListing: {}", CSAP.jsonPrint( configMapListing ) ) ;
			logger.info( "configMapListing: found: '{}' config maps", configMapListing.size( ) ) ;

			logger.debug( CSAP.jsonPrint( configMapListing ) ) ;

			var configMapLabelNames = CSAP.jsonStream( configMapListing )
					.map( category -> category.path( "label" ).asText( ) )
					.sorted( )
					.collect( Collectors.toList( ) ) ;

			logger.info( "configMapLabelNames: {}", configMapLabelNames ) ;

			assertThat( configMapLabelNames )
					.as( "configMaps" )
					.contains( "calico-config,kube-system", "coredns,kube-system", "kube-proxy,kube-system" ) ;

		}

		@Test
		public void is_pod_running ( ) throws Exception {

			logger.info( CsapApplication.testHeader( ) ) ;

			var calico = ProjectLoader.getDefaultService( "calico-node", getJsonMapper( ) ) ;

			logger.info( "Calico default: {}", CSAP.jsonPrint( calico.getDockerSettings( ) ) ) ;
			var calicoLocator = (ObjectNode) calico.getDockerSettings( ).path( "locator" ) ;

			// simple match
			calicoLocator.put( "value", calico.getName( ) ) ;
			assertThat( kubernetes.isPodRunning( calico ) ).isTrue( ) ;

			// complex match
			calicoLocator.put( "value", "(dumyy1|" + calico.getName( ) + ")" ) ;
			calico.resetLocatorAndMatching( ) ;
			logger.info( "Calico complex: {}", CSAP.jsonPrint( calico.getDockerSettings( ) ) ) ;
			assertThat( kubernetes.isPodRunning( calico ) ).isTrue( ) ;

			// pod name match
			calicoLocator.remove( "value" ) ;
			calicoLocator.put( C7.podName.val( ), calico.getName( ) + ".*" ) ;
			calico.resetLocatorAndMatching( ) ;
			logger.info( "Calico podName: {}", CSAP.jsonPrint( calico.getDockerSettings( ) ) ) ;
			assertThat( kubernetes.isPodRunning( calico ) ).isTrue( ) ;

			var noServiceName = ProjectLoader.getDefaultService( "no-service-name", getJsonMapper( ) ) ;

			logger.info( "noServiceName: {}", CSAP.jsonPrint( noServiceName.getDockerSettingsOrMissing( ) ) ) ;
			assertThat( kubernetes.isPodRunning( noServiceName ) ).isFalse( ) ;

		}

		@Test
		public void pod_listing_by_label ( )
			throws Exception {

			logger.info( CsapApplication.testHeader( ) ) ;

			var calicoPods = kubernetes.podsByLabelSelector( K8.systemNamespace.val( ), "k8s-app=calico-node" ) ;
			logger.debug( "calicoPods: {} \n {}", calicoPods.size( ), CSAP.jsonPrint( calicoPods ) ) ;

			var containerNames = containerNames( calicoPods ) ;
			logger.info( CSAP.buildDescription( "calico pods found by label",
					"nodeNames", nodeNames( calicoPods ),
					"containerNames", containerNames ) ) ;

			assertThat( containerNames )
					.contains( "calico-node" )
					.doesNotContain( "etcd", "coredns" ) ;

			var metricsPods = kubernetes.podsByLabelSelector( K8.systemNamespace.val( ), K8.metricsServerLabel
					.val( ) ) ;
			var metricContainerNames = containerNames( metricsPods ) ;

			logger.info( "metric pod count: {} metricContainerNames: {} \n {}",
					metricsPods.size( ), metricContainerNames ) ;

		}

		@Test
		public void pod_raw_reports ( )
			throws Exception {

			logger.info( CsapApplication.testHeader( ) ) ;

			var systemPods = kubernetes.podRawReports( K8.systemNamespace.val( ), null ) ;
			logger.debug( "systemPods: {}", CSAP.jsonPrint( systemPods ) ) ;

			var systemPodNames = CSAP.jsonStream( systemPods )
					.map( podJson -> podJson.at( "/metadata/name" ).asText( ) )
					.collect( Collectors.toList( ) ) ;

			var systemPodContainerNames = containerNames( systemPods ) ;

			logger.info( "systemPods: found: '{}' containers: {}, systemPodNames: {}",
					systemPodContainerNames.size( ),
					systemPodContainerNames,
					systemPodNames ) ;

			assertThat( systemPodContainerNames )
					.as( "system pod container names" )
					.contains( "calico-node", "coredns", "kube-proxy" ) ;

			var calicoControllerName = systemPodNames.stream( ).filter( name -> name.startsWith(
					"calico-kube-controllers" ) ).findFirst( ).get( ) ;

			var calicoControllerMetricsReport = kubernetes.podContainerMetricsReport(
					K8.systemNamespace.val( ),
					calicoControllerName ) ;

			logger.debug( "calicoControllerMetricsReport: {}", CSAP.jsonPrint( calicoControllerMetricsReport ) ) ;

			assertThat( calicoControllerMetricsReport.isObject( ) ).isTrue( ) ;

			var fieldNames = CSAP.asStreamHandleNulls( calicoControllerMetricsReport ).collect( Collectors.toList( ) ) ;

			logger.info( "calicoControllerMetricsReport fieldNames: {}", fieldNames ) ;
			assertThat( fieldNames ).contains( "label", "metadata", "spec", "status", "hostname", "containerMetrics" ) ;

			//
			// containerMetrics
			//
			var metrics = calicoControllerMetricsReport.path( "containerMetrics" ) ;
			logger.info( "containerMetrics: {}", CSAP.jsonPrint( metrics ) ) ;

			assertThat( metrics.path( 0 ).path( "memoryInMb" ).asInt( ) ).isGreaterThanOrEqualTo( 10 ) ;

//			assertThat(fieldNames).containsExactly( "label", "metadata", "spec", "status", "hostname", "containerMetrics" ) ;

			assertThat( calicoControllerMetricsReport.path( "podMatches" ).asInt( ) ).isEqualTo( 1 ) ;

			//
			// Verify injected fields
			//

			assertThat( calicoControllerMetricsReport.path( C7.list_label.val( ) ).asText( ) )
					.isNotEmpty( ) ;

			assertThat( calicoControllerMetricsReport.path( "hostname" ).asText( ) )
					.isNotEmpty( ) ;

		}

		private List<String> containerNames ( JsonNode systemPods ) {

			var systemPodContainerNames = CSAP.jsonStream( systemPods )
					.flatMap( podJson -> CSAP.jsonStream( podJson.at( "/spec/containers" ) ) )
					.map( podJson -> podJson.path( "name" ).asText( ) )
					.collect( Collectors.toList( ) ) ;
			return systemPodContainerNames ;

		}

		private List<String> nodeNames ( JsonNode systemPods ) {

			var nodeNames = CSAP.jsonStream( systemPods )
					.map( podJson -> podJson.at( "/spec/nodeName" ) )
					.map( JsonNode::toString )
					.collect( Collectors.toList( ) ) ;
			return nodeNames ;

		}

		@Test
		public void pods_csap_list ( ) {

			logger.warn( CsapApplication.testHeader( ) ) ;

			ArrayNode podListing = kubernetes.listingsBuilder( ).podCsapListing( null, null ) ;
			logger.info( "podListing: found: '{}' pods", podListing.size( ) ) ;
			logger.debug( "podListing: {}", CSAP.jsonPrint( podListing ) ) ;
			assertThat( podListing.size( ) ).isGreaterThan( 10 ) ;

			List<String> podLabels = CSAP.jsonStream( podListing )
					.map( podJson -> podJson.path( C7.list_label.val( ) ).asText( ) )
					.collect( Collectors.toList( ) ) ;

			logger.info( "podLabels", podLabels ) ;

			// partial name match
			assertThat( podLabels.toString( ) )
					.contains( "kube-controller-manager-", "coredns", "kube-proxy" ) ;

			var calicoControllerPod = CSAP.jsonStream( podListing )
					.filter( pod -> pod.path( C7.list_label.val( ) ).asText( ).startsWith(
							"calico-kube-controllers" ) )
					.findFirst( )
					.get( ) ;

			logger.info( "FirstPod: {}", CSAP.jsonPrint( calicoControllerPod ) ) ;

			assertThat( calicoControllerPod.path( "missing" ).isMissingNode( ) ).isTrue( ) ;
			assertThat( calicoControllerPod.path( "label" ).asText( ) )
					.matches( "calico-kube-controllers.*" )
					.doesNotContain( "," ) ;

			var attributes = calicoControllerPod.path( C7.list_attributes.val( ) ) ;

			assertThat( attributes.path( K8.apiPath.val( ) ).asText( ) )
					.matches( "/api/v1/namespaces/kube-system/pods/calico-kube-controllers-.*" ) ;

			assertThat( attributes.path( "hostname" ).asText( ) )
					.isNotEmpty( ) ;

		}

		@Test
		public void pod_logs_simple ( ) {

			logger.info( CsapApplication.testHeader( ) ) ;

			String targetPod = "calico-node" ;

			String firstCalicoLogs = kubernetes.podLogs( K8.systemNamespace.val( ), targetPod, 10, true ) ;

			logger.info( "logs: {} ", firstCalicoLogs ) ;

		}

		@Test
		public void pod_sync_logs ( )
			throws Exception {

		}

		@Test
		public void pod_logs ( )
			throws Exception {

			logger.info( CsapApplication.testHeader( ) ) ;

			String targetPod = "calico-node" ;

			Optional<JsonNode> matchedPodOptional = CSAP.jsonStream( kubernetes.listingsBuilder( ).podCsapListing( null,
					null ) )
					.filter( podJson -> podJson.at( "/attributes/metadata/name" ).asText( ).startsWith( targetPod ) )
					.findFirst( ) ;

			if ( matchedPodOptional.isPresent( ) ) {

				JsonNode matchedPod = matchedPodOptional.get( ) ;
				logger.info( "getting logs for }", matchedPod.toString( ) ) ;

				ObjectNode currentLogs = kubernetes.podContainerTail(
						matchedPod.at( "/attributes/metadata/namespace" ).asText( ),
						matchedPod.at( "/attributes/metadata/name" ).asText( ),
						matchedPod.at( "/attributes/spec/containers/0/name" ).asText( ),
						false, 10, 0 ) ;
				logger.info( "podListing: {}", CSAP.jsonPrint( currentLogs ) ) ;

				ObjectNode previousLogs = kubernetes.podContainerTail(
						matchedPod.at( "/attributes/metadata/namespace" ).asText( ),
						matchedPod.at( "/attributes/metadata/name" ).asText( ),
						matchedPod.at( "/attributes/spec/containers/0/name" ).asText( ),
						true, 10, 0 ) ;

				logger.info( "podListing: {}", CSAP.jsonPrint( previousLogs ) ) ;

				// assertThat( podListing.toString() )
				// .as( "system pods" )
				// .contains( "kube-controller-manager-", "coredns", "kube-proxy" );
			} else {

				logger.info( "No matches {}", targetPod ) ;

			}

		}

		@Disabled
		@Test
		public void pods_delete ( )
			throws Exception {

			logger.info( CsapApplication.testHeader( ) ) ;

			String testPod = "demo-698c59d995-z4gdt" ;

			ObjectNode deleteResult = kubernetes.podDelete( testPod, TEST_NAMESPACE ) ;
			logger.info( "podListing: {}", CSAP.jsonPrint( deleteResult ) ) ;
			// assertThat( podListing.toString() )
			// .as( "system pods" )
			// .contains( "kube-controller-manager-", "coredns", "kube-proxy" );

		}

		@Test
		public void deployments_list ( )
			throws Exception {

			logger.info( CsapApplication.testHeader( ) ) ;

			ArrayNode deployListing = kubernetes.listingsBuilder( ).deploymentListing( null ) ;
			logger.info( "deployListing: {}", CSAP.jsonPrint( deployListing ) ) ;
			assertThat( deployListing.toString( ) )
					.as( "deployListing" )
					.contains( "kubernetes", "coredns" ) ;

		}

		@Test
		public void list_services ( )
			throws Exception {

			logger.info( CsapApplication.testHeader( ) ) ;

			var servicesAndIngresses = kubernetes.listingsBuilder( ).serviceAndIngressListing( null ) ;
			logger.debug( "serviceListing: {}", CSAP.jsonPrint( servicesAndIngresses ) ) ;

			assertThat( servicesAndIngresses.toString( ) )
					.as( "system services" )
					.contains( "kubernetes", "kube-dns" ) ;

			var optionalIngress = CSAP.jsonStream( servicesAndIngresses )
					.filter( item -> item.path( "label" ).asText( ).equals( "test-k8s-csap-reference-ingress" ) )
					.findFirst( ) ;

			if ( optionalIngress.isPresent( ) ) {

				var ingressAttributes = optionalIngress.get( ).path( C7.list_attributes.val( ) ) ;
				logger.info( "Found: {}", CSAP.jsonPrint( ingressAttributes ) ) ;

				assertThat( ingressAttributes.has( K8.apiPath.val( ) ) ).isTrue( ) ;

				assertThat( ingressAttributes.path( K8.apiPath.val( ) ).asText( ) )
						.isEqualTo(
								"/apis/networking.k8s.io/v1/namespaces/csap-test/ingresses/test-k8s-csap-reference-ingress" ) ;

			}

		}

		@Test
		public void verify_master_status ( )
			throws Exception {

			logger.info( CsapApplication.testHeader( ) ) ;

			assertThat( kubernetes.areMinimumMastersReady( 1 ) ).as( "single master ready" ).isTrue( ) ;

			assertThat( kubernetes.areMinimumMastersReady( 99 ) ).as( "99 masters ready" ).isFalse( ) ;

		}

		@Test
		public void metrics_node_listing ( )
			throws Exception {

			logger.info( CsapApplication.testHeader( ) ) ;

			var nodesReport = kubernetes.metricsBuilder( ).nodeReport( null ) ;
			logger.info( "nodesReport: {}", CSAP.jsonPrint( nodesReport ) ) ;

			assertThat( nodesReport ).isNotNull( ) ;

			var firstNode = nodesReport.path( nodesReport.fieldNames( ).next( ) ) ;

			assertThat( firstNode.path( "podsRunning" ).asLong( ) )
					.isGreaterThan( 5 ) ;

		}

		@Test
		public void metrics_listing ( )
			throws Exception {

			logger.warn( CsapApplication.testHeader( ) ) ;

			var metricReport = kubernetes.metricsBuilder( ).cachedKubeletReport( ) ;
			logger.info( "metricReport: {}", CSAP.jsonPrint( metricReport ) ) ;

			assertThat( ! metricReport.has( "error" ) ).as( "verify metrics server apis are available" ).isTrue( ) ;

			var nodesReport = metricReport.path( "nodes" ) ;
			assertThat( nodesReport ).isNotNull( ) ;
			assertThat( nodesReport.size( ) ).as( "verify metrics server is running" ).isGreaterThan( 0 ) ;

			var firstNode = nodesReport.path( nodesReport.fieldNames( ).next( ) ) ;

			assertThat( firstNode.path( "memoryGb" ).asDouble( ) )
					.as( "dev04 memory" )
					.isGreaterThan( 7 ) ;

			assertThat( firstNode.path( "podsRunning" ).asLong( ) )
					.as( "dev04 pods" )
					.isGreaterThan( 4 ) ;

			var calicoContainer = metricReport.at( "/containers/calico-node" ) ;
			logger.info( "calicoContainer: {}", calicoContainer ) ;
			assertThat( calicoContainer.path( "memoryInMb" ).asLong( ) )
					.as( "calico memory" )
					.isGreaterThan( 50 ) ;

			assertThat( calicoContainer.path( "containerCount" ).asLong( ) )
					.as( "calico count" )
					.isGreaterThan( 2 ) ;

			var pods = metricReport.path( "pods" ) ;
			var firstPodName = CSAP.asStreamHandleNulls( pods )
					.findFirst( ) ;

			var firstPodMetrics = pods.path( firstPodName.get( ) ) ;
			var firstPodCores = firstPodMetrics.path( "cores" ).asDouble( -1 ) ;
			logger.info( " pod: {} cores: {}", firstPodName.get( ), firstPodCores ) ;

			assertThat( firstPodCores )
					.isGreaterThanOrEqualTo( 0.0 ) ;

			assertThat( firstPodMetrics.path( "namespace" ).asText( ) )
					.isNotBlank( ) ;

			logger.info( "kube-system summary: {} ", CSAP.jsonPrint( metricReport.at( "/namespaces/kube-system" ) ) ) ;
			assertThat( metricReport.at( "/namespaces/kube-system/memoryInMb" ).asLong( ) )
					.as( "namespace summaries" )
					.isGreaterThan( 900 ) ;
//
			assertThat( metricReport.at( "/namespaces/kube-system/containerCount" ).asLong( ) )
					.as( "namespace summaries" )
					.isGreaterThan( 10 ) ;
//

//			logger.info( "csap-logging summary: {} ", CSAP.jsonPrint( metrics.at( "/namespaces/csap-logging" ) ) ) ;

//			logger.info( "backend-pod-5954b76769-kvmv9: {} ", CSAP.jsonPrint( metricReport.at(
//					"/pods/backend-pod-5954b76769-kvmv9" ) ) ) ;

			logger.info( "containerNamespace: {} ", CSAP.jsonPrint( metricReport.at(
					"/containerNamespace" ) ) ) ;

		}

		@Test
		public void verify_ingress_url ( )
			throws Exception {

			logger.info( CsapApplication.testHeader( ) ) ;

			var url = kubernetes.ingressUrl( Application.testBuilder( ), "/demo", null, false ) ;

			assertThat( url ).endsWith( "/demo" ) ;

			logger.info( "url: '{}'", url ) ;

		}

		@Test
		void verify_ingress_node_port_url ( )
			throws Exception {

			logger.info( CsapApplication.testHeader( ) ) ;

			var url = kubernetes.nodePortUrl(
					Application.testBuilder( ),
					K8.ingressService.val( ),
					"$host", "/path", false ) ;

			assertThat( url ).endsWith( "/path" ) ;

			logger.info( "url: '{}'", url ) ;

			var ingressPods = kubernetes.podsByLabelSelector(
					K8.ingressNamespace.val( ),
					K8.ingressControllerPodLabelSelector.val( ) ) ;

			logger.debug( "ingressPods count:{} , listing: {}", CSAP.jsonPrint( ingressPods ) ) ;

			var firstPod = ingressPods.path( 0 ) ;
			var containers = firstPod.at( "/spec/containers" ) ;
			logger.debug( "containers: {}", CSAP.jsonPrint( containers ) ) ;

			var hostNetwork = firstPod.at( "/spec/hostNetwork" ).asBoolean( false ) ;
			logger.info( "ingressPods count:{} , hostNetwork: {}",
					ingressPods.size( ),
					hostNetwork ) ;

			assertThat( hostNetwork ).isTrue( ) ;

		}
	}

	@Nested
	@TestInstance ( TestInstance.Lifecycle.PER_CLASS )
	@DisplayName ( "Statefull tests (name spaces cleared)" )
	class Statefull {

		@BeforeAll
		void beforeAll ( ) {

			apiTester.namespace_delete( TEST_NAMESPACE ) ;
			apiTester.namespace_create( TEST_NAMESPACE, true ) ;

		}

		@AfterAll
		void afterAll ( ) {

			//
			// This is painfully slow - do NOT wait for the complete to occur
			//
			var namespaceDeleteResults = kubernetes.namespace_delete( TEST_NAMESPACE ) ;
			logger.info( "namespaceDeleteResults: {}", CSAP.jsonPrint( namespaceDeleteResults ) ) ;

//			apiTester.namespace_delete( TEST_NAMESPACE ) ;

		}

		@Test
		@DisplayName ( "deploy from definition with pvc selectors" )
		public void verify_deployment_from_service_with_selectors ( )
			throws Exception {

			logger.info( CsapApplication.testHeader( ) ) ;

			var deployName = "verify-csap-with-pvc-selector" ;
			var deployDefinition = test_definitions.at( "/" + deployName ) ;
			var containerName = deployDefinition.at( "/docker/containerName" ).asText( "name-not-found" ) ;

			// deployment_delete( deployName, TEST_NAMESPACE );
			// Thread.sleep( 1000 );
			JsonNode definitionDeploymentResults = deployment_using_definition(
					deployName,
					TEST_NAMESPACE,
					deployDefinition ) ;

			assertThat( definitionDeploymentResults.path( "create-service" ).isMissingNode( ) )
					.as( "service deployed" )
					.isFalse( ) ;

			assertThat( apiTester.wait_for_pod_running( containerName, TEST_NAMESPACE ) ).isTrue( ) ;

			// if ( true )
			// return ;
			//
			//
			//
			logger.info( CsapApplication.testHeader( "Deleting deployment" ) ) ;

			var deleteReport = deployment_delete(
					definitionDeploymentResults.at( "/create-deployment/metadata/name" ).asText(
							"no-name-in-create-results" ),
					TEST_NAMESPACE ) ;
			
			logger.info( "deleteReport: {}", CSAP.jsonPrint( deleteReport ) ) ;

//			assertThat( deleteResult.at( "/delete-service/status" ).asText( ) )
//					.as( "deleteResult" )
//					.isEqualToIgnoringCase( "Success" ) ;

			assertThat( deleteReport.at( "/delete-service/apiVersion" ).asText( ) )
					.as( "deleteResult" )
					.isEqualToIgnoringCase( "v1" ) ;

			ServiceInstance serviceInstance = build_service_from_definition(
					deployName, TEST_NAMESPACE,
					test_definitions.at( "/verify-csap-test-app" ) ) ;

			ObjectNode delete_pvc_results = kubernetes.specBuilder( ).persistentVolumeClaimDelete( serviceInstance ) ;
			logger.info( "delete_pvc_results: {} ", CSAP.jsonPrint( delete_pvc_results ) ) ;

			JsonNode deleteClaimResults = kubernetes.specBuilder( )
					.persistentVolumeClaimDelete( definitionDeploymentResults.at( "/create-volume/metadata/name" )
							.asText( ), TEST_NAMESPACE ) ;
			logger.info( "deleteClaimResults: {}", CSAP.jsonPrint( deleteClaimResults ) ) ;

			assertThat(
					definitionDeploymentResults.at( "/create-deployment/spec/template/spec/containers/0/ports/0/name" )
							.asText( "not found" ) )
									.as( "service deployed" )
									.isEqualTo( "primary" ) ;

		}

		@Test
		@DisplayName ( "deploy from definition with test container, multiple volumes" )
		public void verify_deployment_from_service_with_all_options ( )
			throws Exception {

			logger.info( CsapApplication.testHeader( ) ) ;

			var deployName = "verify-csap-test-app" ;
			var deployDefinition = test_definitions.at( "/" + deployName ) ;
			var containerName = deployDefinition.at( "/docker/containerName" ).asText( "name-not-found" ) ;

			// deployment_delete( deployName, TEST_NAMESPACE );
			// Thread.sleep( 1000 );
			JsonNode definitionDeploymentResults = deployment_using_definition(
					deployName,
					TEST_NAMESPACE,
					deployDefinition ) ;

			assertThat( definitionDeploymentResults.path( "create-service" ).isMissingNode( ) )
					.as( "service deployed" )
					.isFalse( ) ;

			assertThat( definitionDeploymentResults.path( "create-ingress" ).isMissingNode( ) )
					.as( "ingress deployed" )
					.isFalse( ) ;

			assertThat( definitionDeploymentResults.at( "/create-ingress/error" ).isMissingNode( ) )
					.as( "no ingress errors" )
					.isTrue( ) ;

			assertThat( apiTester.wait_for_pod_running( containerName, TEST_NAMESPACE ) ).isTrue( ) ;

			// if (true) return;
			//
			//
			//
			logger.info( CsapApplication.testHeader( "Deleting deployment" ) ) ;

			JsonNode deleteResult = deployment_delete(
					definitionDeploymentResults.at( "/create-deployment/metadata/name" ).asText(
							"no-name-in-create-results" ),
					TEST_NAMESPACE ) ;

			// client 14.0 not showing result
//			assertThat( deleteResult.at( "/delete-service/status" ).asText( ) )
//					.as( "deleteResult" )
//					.isEqualToIgnoringCase( "Success" ) ;
			assertThat( deleteResult.at( "/delete-service/status" ).asText( ) )
					.as( "deleteResult" )
					.isEqualToIgnoringCase( "" ) ;

			var serviceInstance = build_service_from_definition(
					deployName, TEST_NAMESPACE,
					test_definitions.at( "/verify-csap-test-app" ) ) ;

			var delete_pvc_results = kubernetes.specBuilder( ).persistentVolumeClaimDelete( serviceInstance ) ;
			logger.info( "delete_pvc_results: {} ", CSAP.jsonPrint( delete_pvc_results ) ) ;

			JsonNode deleteClaimResults = kubernetes.specBuilder( )
					.persistentVolumeClaimDelete( definitionDeploymentResults.at( "/create-volume/metadata/name" )
							.asText( ), TEST_NAMESPACE ) ;
			logger.info( "deleteClaimResults: {}", CSAP.jsonPrint( deleteClaimResults ) ) ;

			assertThat(
					definitionDeploymentResults.at( "/create-deployment/spec/template/spec/containers/0/ports/0/name" )
							.asText( "not found" ) )
									.as( "service deployed" )
									.isEqualTo( "primary" ) ;

		}

		@Test
		public void deployment_create_and_delete_using_default_definition ( )
			throws Exception {

			//
			//
			//
			logger.info( CsapApplication.testHeader( "Creating deployment" ) ) ;

			String deployName = "junit-test-default" ;

			// deployment_delete( deployName, TEST_NAMESPACE );
			// Thread.sleep( 1000 );
			JsonNode deployResult = deployment_using_definition( deployName, TEST_NAMESPACE,
					test_definitions.at( "/verify-defaults" ) ) ;

			assertThat( deployResult.path( "create-service" ).isMissingNode( ) )
					.as( "service NOT deployed" )
					.isTrue( ) ;

			assertThat( apiTester.wait_for_pod_running( deployName, TEST_NAMESPACE ) ).isTrue( ) ;

			//
			//
			//
			logger.info( CsapApplication.testHeader( "Deleting deployment" ) ) ;

			JsonNode deleteResult = deployment_delete(
					deployResult.at( "/create-deployment/metadata/name" ).asText( "no-name-in-create-results" ),
					TEST_NAMESPACE ) ;

			assertThat( deleteResult.at( "/delete-service/reason" ).asText( ) )
					.as( "deleteResult" )
					.isEqualToIgnoringCase( "services \"junit-test-default-service\" not found" ) ;

		}

		@Test
		void event_summary_report_after_ops ( ) throws Exception {

			logger.warn( CsapApplication.testHeader( ) ) ;

			//
			// perform a deployment to push events
			//
			String deployName = "junit-test-summary" ;
			deployment_create_api( deployName, TEST_NAMESPACE ) ;
			assertThat( apiTester.wait_for_pod_running( deployName, TEST_NAMESPACE ) ).isTrue( ) ;

			//
			// Query events - last hour
			//
			var hourEvents = kubernetes.buildEventHealthReport( 60, 1000, List.of( ".*cron" ) ) ;

			var hourEventCount = CSAP.jsonStream( hourEvents )
					.mapToInt( JsonNode::asInt )
					.sum( ) ;
			logger.info( "hourEventCount: {},  hourEvents: \n\t{}", hourEventCount, hourEvents ) ;
			assertThat( hourEventCount ).isGreaterThanOrEqualTo( 1 ) ;

			var formattedHourEvents = CSAP.asStreamHandleNulls( hourEvents )
					.map( eventName -> {

						var count = hourEvents.path( eventName ).asInt( ) ;
						return CSAP.pad( eventName ) + ": " + count ;

					} )
					.collect( Collectors.joining( "\n" ) ) ;

			logger.info( "formattedEvents: {}", formattedHourEvents ) ;

			//
			// Query events - last hour
			//
			var eventsLast3Minutes = kubernetes.buildEventHealthReport( 1, 10, List.of( ".*cron" ) ) ;

			assertThat( eventsLast3Minutes.size( ) ).isGreaterThanOrEqualTo( 1 ) ;

			logger.info( "eventsLast3Minutes: {} \n\t{}", eventsLast3Minutes.size( ), eventsLast3Minutes ) ;

		}

		@Test
		public void deployment_create_and_delete_using_api ( )
			throws Exception {

			logger.info( CsapApplication.testHeader( ) ) ;

			String deployName = "junit-test-api" ;

			// deployment_delete( deployName, TEST_NAMESPACE );
			// Thread.sleep( 1000 );
			deployment_create_api( deployName, TEST_NAMESPACE ) ;

			assertThat( apiTester.wait_for_pod_running( deployName, TEST_NAMESPACE ) ).isTrue( ) ;

			var deleteReport = deployment_delete( deployName, TEST_NAMESPACE ) ;

//			assertThat( deleteResult.at( "/delete-service/status" ).asText( ) )
//					.as( "deleteResult" )
//					.isEqualToIgnoringCase( "Success" ) ;
			

			assertThat( deleteReport.at( "/delete-service/apiVersion" ).asText( ) )
					.as( "deleteResult" )
					.isEqualToIgnoringCase( "v1" ) ;

		}

	}

	private JsonNode deployment_using_definition ( String deployName , String namespace , JsonNode serviceDefinition ) {

		logger.info( "deployName: {}, namespace: {}", deployName, namespace ) ;

		ServiceInstance serviceInstance = build_service_from_definition( deployName, namespace, serviceDefinition ) ;
		// if (true ) return ;
		ObjectNode all_spec_results = (ObjectNode) kubernetes.specBuilder( ).deploy_csap_service(
				serviceInstance,
				serviceInstance.getDockerSettings( ) ) ;

		assertThat( all_spec_results.path( "create-deployment" ).isMissingNode( ) )
				.as( "no errors in deployment" )
				.isFalse( ) ;

		ObjectNode deployResult = (ObjectNode) all_spec_results.path( "create-deployment" ) ;

		logger.info( "\n\n\n all_spec_results: {}", CSAP.jsonPrint( all_spec_results ) ) ;

		assertThat( deployResult.path( "error" ).isMissingNode( ) )
				.as( "no errors in deployment" )
				.isTrue( ) ;

		assertThat( deployResult.path( "kind" ).asText( ) )
				.as( "deploy kind" )
				.isEqualToIgnoringCase( "Deployment" ) ;

		String image = serviceInstance.getDockerImageName( ) ;
		assertThat( deployResult.at( "/spec/template/spec/containers/0/image" ).asText( ) )
				.as( "image name in container spec" )
				.isEqualToIgnoringCase( image ) ;

		// assertThat( deployResult.at( "/spec/template/spec/containers/0/command"
		// ).toString() )
		// .as( "command" )
		// .contains( "daemon off" );

		// assertThat( deployResult.at( "/spec/template/spec/containers/0/args"
		// ).toString() )
		// .as( "arguments" )
		// .contains( "daemon off" );

		// assertThat( deployResult.at( "/spec/template/spec/containers/0/ports"
		// ).toString() )
		// .as( "port in container spec" )
		// .contains( "80" );

		// assertThat( deployResult.at( "/spec/template/spec/containers/0/env"
		// ).toString() )
		// .as( "environment variables" )
		// .contains( "NGINX_VERSION" );

		// assertThat( deployResult.at( "/spec/template/spec/containers/0/env"
		// ).toString() )
		// .as( "environment variables" )
		// .contains( "javaOptions" );

		int replica_count_from_definition = serviceInstance.getDockerSettings( ).at( K8.replicaCount
				.spath( ) ).asInt( 1 ) ;
		assertThat( deployResult.at( "/spec/replicas" ).asInt( ) )
				.as( "replica Count" )
				.isEqualTo( replica_count_from_definition ) ;

		// assertThat( deployResult.at( "/createService/kind" ).asText() )
		// .as( "createService/kind" )
		// .isEqualToIgnoringCase( "Service" );

		// assertThat( deployResult.at( "/createService/spec/ports/0/port" ).asInt() )
		// .as( "/createService/spec/ports/0/port" )
		// .isEqualTo( 7082 );

		return all_spec_results ;

	}

	private ServiceInstance build_service_from_definition (
															String name ,
															String namespace ,
															JsonNode serviceDefinition ) {

		ServiceInstance serviceInstance = new ServiceInstance( ) ;
		serviceInstance.setName( name ) ;
		serviceInstance.setHostName( "centos1.na.yourcompany.net" ) ;
		serviceInstance.parseDefinition( "junit-package", serviceDefinition, new StringBuilder( ) ) ;
		serviceInstance.setKubernetesNamespace( namespace ) ;
		// serviceInstance.setPort( "1234" );

		logger.info( "loaded: {}", serviceInstance.details( ) ) ;
		return serviceInstance ;

	}

	private void deployment_create_api ( String name , String namespace )
		throws Exception {

		String image = test_definitions.at( "/verify-simple/" + C7.imageName.val( ) ).asText( ) ;

		String ports = test_definitions.at( "/verify-simple/" + C7.portMappings.val( ) ).toString( ) ;
		String environmentVariables = test_definitions.at( "/verify-simple/" + C7.environmentVariables
				.val( ) )
				.toString( ) ;
		String command = test_definitions.at( "/verify-simple/" + C7.command.val( ) ).toString( ) ;
		// String entry = kubernetesSimple.path( "entry" ).toString();

		String k8Command = command ;
		String k8Args = "" ;

		String workingDirectory = "" ;
		String network = "" ;
		String restartPolicy = "" ;
		String runUser = "" ;
		String volumes = "" ;
		String limits = "" ;
		String nodeSelector = "" ;
		String resources = "" ;
		String annotations = "" ;
		String labelsByType = test_definitions.at( "/verify-simple" + K8.labelsByType.spath( ) )
				.toString( ) ;
		String readinessProbe = "" ;
		String livenessProbe = "" ;

		String ingressPath = "/" ;
		String ingressPort = null ;
		String ingressHost = null ;
		String ingressAnnotations = null ;

		int replicaCount = 1 ;

		ObjectNode deployResult = kubernetes.specBuilder( ).deploymentCreate(
				name, namespace,
				image, nodeSelector, annotations, labelsByType,
				resources,
				readinessProbe, livenessProbe,
				replicaCount,
				K8.NODE_PORT.val( ),
				ingressPath, ingressPort, ingressHost, ingressAnnotations,
				k8Command, k8Args,
				workingDirectory, network, restartPolicy,
				runUser, ports, volumes, environmentVariables, limits, true ) ;

		logger.info( "labelsByType: {} \n\t deployResult: {}", labelsByType, CSAP.jsonPrint( deployResult ) ) ;

		assertThat( deployResult.at( "/create-deployment/kind" ).asText( ) )
				.as( "deploy kind" )
				.isEqualToIgnoringCase( "Deployment" ) ;

		assertThat( deployResult.at( "/create-deployment/metadata/labels/test-deploy" ).asText( ) )
				.as( "deploy label" )
				.isEqualTo( "junit-demo-deploy" ) ;

		assertThat( deployResult.at( "/create-deployment/spec/template/spec/containers/0/image" ).asText( ) )
				.as( "image name in container spec" )
				.isEqualToIgnoringCase( image ) ;

		assertThat( deployResult.at( "/create-deployment/spec/template/spec/containers/0/command" ).toString( ) )
				.as( "command" )
				.contains( "daemon off" ) ;

		// assertThat( deployResult.at( "/spec/template/spec/containers/0/args"
		// ).toString() )
		// .as( "arguments" )
		// .contains( "daemon off" );

		assertThat( deployResult.at( "/create-deployment/spec/template/spec/containers/0/ports" ).toString( ) )
				.as( "port in container spec" )
				.contains( "80" ) ;

		assertThat( deployResult.at( "/create-deployment/spec/template/spec/containers/0/env" ).toString( ) )
				.as( "environment variables" )
				.contains( "NGINX_VERSION" ) ;

		// assertThat( deployResult.at( "/spec/template/spec/containers/0/env"
		// ).toString() )
		// .as( "environment variables" )
		// .contains( "javaOptions" );

		assertThat( deployResult.at( "/create-deployment/spec/replicas" ).asInt( ) )
				.as( "replica Count" )
				.isEqualTo( replicaCount ) ;

		assertThat( deployResult.at( "/create-service/kind" ).asText( ) )
				.as( "createService/kind" )
				.isEqualToIgnoringCase( "Service" ) ;

		assertThat( deployResult.at( "/create-service/spec/ports/0/port" ).asInt( ) )
				.as( "/createService/spec/ports/0/port" )
				.isEqualTo( 7082 ) ;

	}

	private JsonNode deployment_delete ( String name , String namespace )
		throws Exception {

		ObjectNode deleteResult = kubernetes.specBuilder( ).deploymentDelete( name, namespace, true, true ) ;

		logger.info( "deleteResult: {}", CSAP.jsonPrint( deleteResult ) ) ;

		assertThat( deleteResult.at( "/delete-deployment/status" ).asText( ) )
				.as( "delete-deployment/status" )
				.isEqualToIgnoringCase( "Success" ) ;

		return deleteResult ;

	}

	public void handleKubernetesApiBug ( JsonSyntaxException e ) {

		if ( e.getCause( ) instanceof IllegalStateException ) {

			IllegalStateException ise = (IllegalStateException) e.getCause( ) ;

			if ( ise.getMessage( ) != null && ise.getMessage( ).contains( "Expected a string but was BEGIN_OBJECT" ) ) {

				logger.warn( "Successful delete throws Json\n\t known issue: {}: {}",
						"https://github.com/kubernetes-client/java/issues/86",
						CSAP.buildCsapStack( e ) ) ;

			} else {

				logger.error( "Failed to delete: {}",
						CSAP.buildCsapStack( e ), e ) ;
				throw e ;

			}

		}

	}

	/**
	 * 
	 * 
	 * Setup
	 * 
	 * 
	 */

	@Test
	public void verify_spring_context_loaded ( ) {

		logger.info( CsapApplication.testHeader( ) ) ;

		logger.info( "count: '{}', kub enabled: '{}' \n names: '{}'",
				applicationContext.getBeanDefinitionCount( ),
				springEnvironment.getProperty( "csap-core.kubernetes.enabled" ),
				Arrays.asList( applicationContext.getBeanDefinitionNames( ) ) ) ;

	}

	public KubernetesSettings getKubernetesSettings ( ) {

		return kubernetesSettings ;

	}

	public void setKubernetes ( KubernetesSettings kubernetes ) {

		this.kubernetesSettings = kubernetes ;

	}

	public String getConfigUrl ( ) {

		return configUrl ;

	}

	public void setConfigUrl ( String configUrl ) {

		this.configUrl = configUrl ;

	}

}
