/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.csap.agent.project.loader ;

import static org.assertj.core.api.Assertions.assertThat ;

import java.io.File ;
import java.net.URI ;
import java.util.List ;
import java.util.stream.Collectors ;

import org.csap.agent.CsapBareTest ;
import org.csap.agent.CsapCore ;
import org.csap.agent.api.ApplicationApi ;
import org.csap.agent.api.ModelApi ;
import org.csap.agent.integrations.CsapEvents ;
import org.csap.agent.linux.HostStatusManager ;
import org.csap.agent.model.Application ;
import org.csap.agent.model.EnvironmentSettings ;
import org.csap.agent.model.ModelJson ;
import org.csap.agent.model.Project ;
import org.csap.agent.model.ProjectLoader ;
import org.csap.agent.model.ServiceAlertsEnum ;
import org.csap.agent.model.ServiceAttributes ;
import org.csap.agent.model.ServiceInstance ;
import org.csap.agent.stats.MetricCategory ;
import org.csap.agent.stats.OsProcessEnum ;
import org.csap.agent.stats.service.JmxCommonEnum ;
import org.csap.helpers.CSAP ;
import org.csap.helpers.CsapApplication ;
import org.junit.jupiter.api.AfterAll ;
import org.junit.jupiter.api.BeforeAll ;
import org.junit.jupiter.api.BeforeEach ;
import org.junit.jupiter.api.Disabled ;
import org.junit.jupiter.api.DisplayName ;
import org.junit.jupiter.api.Nested ;
import org.junit.jupiter.api.Test ;
import org.junit.jupiter.api.TestInstance ;

import com.fasterxml.jackson.core.type.TypeReference ;
import com.fasterxml.jackson.databind.JsonNode ;

/**
 *
 * @author someDeveloper
 */

class Csap_Reference_Tests extends CsapBareTest {

	public EnvironmentSettings getAppEnv ( ) {

		return getApplication( ).rootProjectEnvSettings( ) ;

	}

	String definitionPath = "/definitions/csap-reference/csap-dev-project.json" ;

	File csapReferenceDefinition = new File( getClass( ).getResource( definitionPath ).getPath( ) ) ;

	// Application csapApp = new Application() ;
	ModelApi modelApi ;
	ApplicationApi applicationApi ;

	@BeforeAll
	void beforeAll ( )
		throws Exception {

		CsapApplication.initialize( Csap_Reference_Tests.class.getSimpleName( ) ) ;

		HostStatusManager testHostStatusManager = new HostStatusManager( "admin/csap-reference-status.json",
				getApplication( ) ) ;
		getApplication( ).setHostStatusManager( testHostStatusManager ) ;

		modelApi = new ModelApi( getApplication( ) ) ;
		applicationApi = new ApplicationApi( getApplication( ), new CsapEvents( ), null ) ;

		getApplication( ).setAgentRunHome( System.getProperty( "user.home" ) ) ;

	}

	@BeforeEach
	void beforeEach ( ) {

		logger.info( CsapApplication.testHeader( ) ) ;
		getApplication( ).setJvmInManagerMode( false ) ;

	}

	@AfterAll
	void afterAll ( ) {

		logger.info( CsapApplication.testHeader( "Restoring default host name to localhost for tests" ) ) ;
		getApplication( ).setHostNameForDefinitionMapping( "localhost" ) ;

	}

	@Nested
	@TestInstance ( TestInstance.Lifecycle.PER_CLASS )
	@DisplayName ( "Dev Lifecycle: HA project" )
	class Dev_Lifecycle_HA_Project {

		@BeforeAll
		void beforeAll ( )
			throws Exception {

			logger.info( CsapApplication.testHeader( ) ) ;
			var hostForTest = "csap-dev07" ;

			getApplication( ).setHostNameForDefinitionMapping( hostForTest ) ;

			assertThat( getApplication( ).loadDefinitionForJunits( false, csapReferenceDefinition ) )
					.as( "No Errors or warnings" )
					.isTrue( ) ;

			logger.info( "Last parse results: {}", getApplication( ).getLastTestParseResults( ) ) ;

			CSAP.setLogToInfo( ProjectLoader.class.getName( ) ) ;

			logger.info( CsapApplication.header( "loaded: {}" ), csapReferenceDefinition.getAbsolutePath( ) ) ;

		}

		@Test
		@DisplayName ( "dev ha lifecycle: verify storage settings overridden" )
		void verify_config_map_merged ( ) {

			logger.info( CsapApplication.testHeader( ) ) ;

			var globalMap = getAppEnv( ).getConfigurationMap( EnvironmentSettings.GLOBAL_CONFIG_MAP_NAME ) ;
			logger.info( "globalMap: {}", globalMap ) ;
			assertThat( globalMap.toString( ) ).contains( "csap_auto" ) ;

		}

		@Test
		@DisplayName ( "dev ha lifecycle: verify storage settings overridden" )
		void verify_kubernetes_service ( ) {

			logger.info( CsapApplication.testHeader( ) ) ;

			var kubeletServiceName = getApplication( ).getActiveProject( ).getKubernetesServiceName( "dev" ) ;
			var kubeletMaster = getApplication( ).getActiveProject( ).getKubernetesMasterHost( "dev" ) ;
			logger.info( "kubernetes services: {}, kubeletMaster: {}", kubeletServiceName, kubeletMaster ) ;
			assertThat( kubeletServiceName ).contains( "kubelet-ha" ) ;
			assertThat( kubeletMaster ).contains( "csap-dev07" ) ;

		}

		@Test
		@DisplayName ( "dev ha lifecycle: verify storage settings overridden" )
		void verify_storage_settings_overridden ( ) {

			logger.info( CsapApplication.testHeader( ) ) ;

			// var globaleStorageSettings = getAppEnv().getConfigurationMap(
			// "storage-settings" ) ;
			var globaleStorageSettings = getAppEnv( ).getConfigurationMap( "storage-settings" ) ;

			var haStorageSettings = getApplication( ).environmentSettings( ).getConfigurationMap( "storage-settings" ) ;

			logger.info( "globaleStorageSettings: {} \n haStorageSettings: {} ", globaleStorageSettings,
					haStorageSettings ) ;

			assertThat( globaleStorageSettings.path( "$$storage-type" ).asText( ) ).isEqualTo( "nfs" ) ;

			assertThat( haStorageSettings.path( "$$storage-type" ).asText( ) ).isEqualTo( "nfs" ) ;

		}

		@Disabled
		@Test
		@DisplayName ( "dev ha lifecycle: verify vsphere settings overridden" )
		void verify_vsphere_settings_overridden ( ) {

			logger.info( CsapApplication.testHeader( ) ) ;

			var vsphereEnvironmentVariables = getAppEnv( ).getVsphereEnv( ) ;

			logger.info( "vsphereEnvironmentVariables: {}", vsphereEnvironmentVariables ) ;

			assertThat( vsphereEnvironmentVariables.toString( ) )
					.contains( "GOVC_PASSWORD=xxx" ) ;

			vsphereEnvironmentVariables = getApplication( ).environmentSettings( ).getVsphereEnv( ) ;

			logger.info( "vsphereEnvironmentVariables: {}", vsphereEnvironmentVariables ) ;

			assertThat( vsphereEnvironmentVariables.toString( ) )
					.contains( "GOVC_PASSWORD=yyy" ) ;

		}

		@Test
		@DisplayName ( "dev ha lifecycle: spec service masters" )
		void verify_dev_ha_spec_instances ( ) {

			logger.info( CsapApplication.testHeader( ) ) ;

			ServiceInstance k8SpecService = getApplication( ).findServiceByNameOnCurrentHost( "test-k8s-by-spec-ha" ) ;

			logger.info( "k8SpecService: {}, masters: {}", k8SpecService, k8SpecService
					.getKubernetesMasterHostNames( ) ) ;

			assertThat( CSAP.jsonList( k8SpecService.getKubernetesMasterHostNames( ) ) )
					.contains( "csap-dev07", "csap-dev08", "csap-dev09" ) ;

			logger.info( "k8SpecService: {}", CSAP.jsonPrint( k8SpecService.getCoreRuntime( ) ) ) ;

			assertThat( CSAP.jsonList( k8SpecService.getCoreRuntime( ).path( "kubernetes-masters" ) ) )
					.contains( "csap-dev07", "csap-dev08", "csap-dev09" ) ;

			// k8SpecService.build_ui_service_instance( container, containerIndex )

		}

		@Test
		@DisplayName ( "dev ha agent instances" )
		void verify_dev_ha_agent_instances ( )
			throws Exception {

			logger.info( CsapApplication.testHeader( ) ) ;

			logger.info( "active model, current lifecycle hosts: {}", getApplication( ).getActiveProject( )
					.getHostsCurrentLc( ) ) ;

			assertThat( getApplication( ).getActiveProject( ).getHostsCurrentLc( ) )
					.contains( "csap-dev07", "csap-dev12" )
					.doesNotContain( "csap-stg01" ) ;

		}
	}

	@Nested
	@TestInstance ( TestInstance.Lifecycle.PER_CLASS )
	@DisplayName ( "Stage Lifecycle: CSAP Project" )
	class Stage_Lifecycle {

		@BeforeAll
		void beforeAll ( )
			throws Exception {

			logger.info( CsapApplication.testHeader( ) ) ;
			var targetLife = "csap-stg01" ;

			getApplication( ).setHostNameForDefinitionMapping( targetLife ) ;

			assertThat( getApplication( ).loadDefinitionForJunits( false, csapReferenceDefinition ) )
					.as( "No Errors or warnings" )
					.isTrue( ) ;

			logger.info( "Last parse results: {}", getApplication( ).getLastTestParseResults( ) ) ;

			CSAP.setLogToInfo( ProjectLoader.class.getName( ) ) ;

			logger.info( CsapApplication.header( "loaded: {}" ), csapReferenceDefinition.getAbsolutePath( ) ) ;

		}

		@Test
		@DisplayName ( "stage lifecycle: support for import settings" )
		void verify_stage_lifecycle_base_setting ( ) {

			logger.info( CsapApplication.testHeader( ) ) ;

			logger.info( "stage settings: {}", getAppEnv( ) ) ;
			assertThat( getAppEnv( ).getNumberWorkerThreads( ) ).as( "open file collect" ).isEqualTo( 20 ) ;

			assertThat( getAppEnv( ).getLsofIntervalMins( ) ).as( "open file collect" ).isEqualTo( 1 ) ;

			var realTimeDefs = getAppEnv( ).getRealTimeMeters( ) ;
			logger.info( "realTimeDefs: {}", CSAP.jsonPrint( realTimeDefs ) ) ;
			assertThat( realTimeDefs.size( ) ).as( "real time meters" ).isEqualTo( 18 ) ;

			var trendDefs = getAppEnv( ).getTrendingConfig( ) ;
			assertThat( trendDefs.size( ) ).as( "trends" ).isEqualTo( 24 ) ;

		}

		@Test
		@DisplayName ( "stage agent instances" )
		void verify_stage_agent_instances ( )
			throws Exception {

			logger.info( CsapApplication.testHeader( ) ) ;

			logger.info( "active model, current lifecycle hosts: {}", getApplication( ).getActiveProject( )
					.getHostsCurrentLc( ) ) ;

			assertThat( getApplication( ).getActiveProject( ).getHostsCurrentLc( ) )
					.contains( "csap-stg01" )
					.doesNotContain( "csap-dev01" ) ;

		}
	}

	@Nested
	@TestInstance ( TestInstance.Lifecycle.PER_CLASS )
	@DisplayName ( "Development Lifecycle: CSAP Project" )
	class Dev_Lifecycle {

		@BeforeAll
		void beforeAll ( )
			throws Exception {

			logger.info( CsapApplication.testHeader( ) ) ;
			var hostNameForEnvResolution = "csap-dev01.***REMOVED***" ;

			getApplication( ).setHostNameForDefinitionMapping( hostNameForEnvResolution ) ;

			// var testDefintionFolder = getApplication().configureForDefinitionOverideTest(
			// csapReferenceDefinition ) ;
			// ServiceResources resource = new ServiceResources( "test",
			// testDefintionFolder.getParentFile(), getJsonMapper() ) ;
			// resource.setCsapApp( getApplication() ) ;
			//
			// var testDefinition = new File( testDefintionFolder,
			// csapReferenceDefinition.getName() ) ;

			assertThat( getApplication( ).loadDefinitionForJunits( false, csapReferenceDefinition ) )
					.as( "No Errors or warnings" )
					.isTrue( ) ;

			logger.debug( "parse results: {}", getApplication( ).getLastTestParseResults( ) ) ;

			CSAP.setLogToInfo( ProjectLoader.class.getName( ) ) ;

			logger.info( CsapApplication.header( "loaded: {}" ), csapReferenceDefinition.getAbsolutePath( ) ) ;

		}

		@Test
		void verify_kubernetes_cluster_sorting ( ) {

			logger.info( CsapApplication.testHeader( ) ) ;

			var defaultClusterOrder = getApplication( ).getProjectLoader( ).clusterNamesAsDefined( "dev",
					getApplication( ).getActiveProject( ) ) ;

			var sortedClusters = getApplication( ).getProjectLoader( ).clusterNamesWithKubernetesClustersLast( "dev",
					getApplication( ).getActiveProject( ) ) ;

			logger.info( CSAP.buildDescription( "cluster sorting",
					"defaultClusterOrder:", defaultClusterOrder,
					"sortedClusters: {}", sortedClusters ) ) ;
//			
			assertThat( sortedClusters.indexOf( "csap-events" ) ).isLessThan( defaultClusterOrder.indexOf(
					"csap-events" ) ) ;
			assertThat( sortedClusters.indexOf( "csap-events" ) ).isLessThan( sortedClusters.indexOf(
					"kubernetes-system-services" ) ) ;

		}

		@Test
		void verify_legacy_config_migrated ( )
			throws Exception {

			logger.info( CsapApplication.testHeader( ) ) ;

			var globalVariables = getActiveProjectSettings( ).getConfigurationMap(
					EnvironmentSettings.GLOBAL_CONFIG_MAP_NAME ) ;

			var allMapsFormatted = getActiveProjectSettings( ).uiConfigMapsFormatted( ) ;

			logger.info( "allMapsFormatted: {}", allMapsFormatted ) ;
			assertThat( allMapsFormatted )
					.doesNotContain( "csap_def_" )
					.doesNotContain( "$$$" )
					.contains( "\"$$nfs-mount/kubernetes-backups\"" ) ;

		}

		@Test
		@DisplayName ( "dev lifecycle: verify legacy settings migrated" )
		void verify_legacy_attributes_migrated ( )
			throws Exception {

			logger.info( CsapApplication.testHeader( ) ) ;

			assertThat( getAppEnv( ).getApplicationName( ) ).isEqualTo( "Sensus Integration" ) ;

			var baseOsDefinition = getApplication( ).getActiveProject( ).getCluster( "dev", "base-os" ) ;

			logger.info( "baseOsDefinition: {}", CSAP.jsonPrint( baseOsDefinition ) ) ;

			assertThat( baseOsDefinition.has( ProjectLoader.CLUSTER_TEMPLATE_REFERENCES ) ).isFalse( ) ;

			var haLifes = getApplication( ).getProject( "CSAP HA Demo" ).getEnvironmentNameToSettings( ) ;
			logger.info( "ha lifes: {}", haLifes.keySet( ) ) ;
			var packageEnvSettings = haLifes.get( "dev" ) ;
			assertThat( packageEnvSettings.getApplicationName( ) ).isEqualTo( "Sensus Integration" ) ;

			assertThat( getApplication( ).getActiveProject( )
					.getSourceDefinition( )
					.path( ProjectLoader.PROJECT )
					.path( ProjectLoader.API_VERSION )
					.asDouble( ) ).isEqualTo( ProjectLoader.CURRENT_VERSION ) ;

			var trends = getAppEnv( ).getTrendingConfig( ) ;
			logger.info( "trends {}", CSAP.jsonPrint( trends ) ) ;

			assertThat( trends.at( "/0/label" ).asText( ) ).isEqualTo( "Kubernetes: html hits (SpringBoot, 1000s)" ) ;

			assertThat( trends.at( "/0/report" ).asText( ) ).isEqualTo( "application" ) ;

			assertThat( trends.at( "/1/report" ).asText( ) ).isEqualTo( "application" ) ;

			assertThat( trends.toString( ) ).contains( CsapCore.ADMIN_NAME ).contains( CsapCore.AGENT_NAME ) ;

			var realTimeMeters = getAppEnv( ).getRealTimeMeters( ) ;

			logger.info( "real time meters {}", CSAP.jsonPrint( realTimeMeters ) ) ;
			assertThat( realTimeMeters.at( "/0/label" ).asText( ) ).isEqualTo( "Kubernetes Events" ) ;

			assertThat( realTimeMeters.at( "/0/id" ).asText( ) ).isEqualTo( "application.kubelet.eventCount" ) ;

			assertThat( realTimeMeters.at( "/0/" + MetricCategory.divideBy.json( ) ).asText( ) )
					.isEqualTo( MetricCategory.hostCount.json( ) ) ;

			assertThat( realTimeMeters.toString( ) ).contains( CsapCore.AGENT_NAME ) ;

			logger.info(
					"Event Service: {}, user: {}, pass: {}, eventUrl: {}, metricsUrl: {}, analyticsUrl: {}, reportUri: {}",
					getAppEnv( ).getEventServiceUrl( ),
					getAppEnv( ).getEventDataUser( ),
					getAppEnv( ).getEventDataPass( ),
					getAppEnv( ).getEventApiUrl( ),
					getAppEnv( ).getEventMetricsUrl( ),
					getAppEnv( ).getAnalyticsUiUrl( ),
					getAppEnv( ).getReportUrl( ) ) ;

			assertThat( getAppEnv( ).getEventApiUrl( ) ).isEqualTo( CsapCore.EVENTS_DISABLED + "/api/event" ) ;

			assertThat( getAppEnv( ).getEventMetricsUrl( ) ).isEqualTo( CsapCore.EVENTS_DISABLED + "/api/metrics/" ) ;
			assertThat( getAppEnv( ).getReportUrl( ) ).isEqualTo( new URI( CsapCore.EVENTS_DISABLED
					+ "/api/report/" ) ) ;
			assertThat( getAppEnv( ).getAnalyticsUiUrl( ) )
					.isEqualTo( "events-disabled/../csap-admin/os/performance" ) ;

			assertThat( getAppEnv( ).getNumberWorkerThreads( ) )
					.isEqualTo( 20 ) ;

			assertThat( getAppEnv( ).getPsDumpCount( ) )
					.isEqualTo( 3 ) ;

			var hostCollection = getAppEnv( ).getMetricToSecondsMap( ).get( CsapApplication.COLLECTION_HOST ) ;
			var processCollection = getAppEnv( ).getMetricToSecondsMap( )
					.get( CsapApplication.COLLECTION_OS_PROCESS ) ;
			var appCollection = getAppEnv( ).getMetricToSecondsMap( ).get( CsapApplication.COLLECTION_APPLICATION ) ;

			logger.info( "hostCollection: {}", hostCollection ) ;
			assertThat( hostCollection.toString( ) )
					.isEqualTo( "[30, 300, 3600]" ) ;

			assertThat( processCollection.toString( ) )
					.isEqualTo( "[30, 300, 3600]" ) ;

			assertThat( appCollection.toString( ) )
					.isEqualTo( "[30, 300, 3600]" ) ;

			var csapDockerReferenceService = getApplication( ).findFirstServiceInstanceInLifecycle(
					"test-docker-csap-reference" ) ;
			var performanceSettings = csapDockerReferenceService.getPerformanceConfiguration( ) ;
			logger.info( "metrics: {}", performanceSettings ) ;

			assertThat( performanceSettings ).isNotNull( ) ;
			assertThat( performanceSettings.path( ModelJson.config.jpath( ) ).isObject( ) ).isTrue( ) ;

			var alertSettings = csapDockerReferenceService.getMonitors( ) ;
			logger.info( "alertSettings: {}", alertSettings ) ;

			assertThat( alertSettings ).isNotNull( ) ;
			
//			var diskLimit = ServiceAlertsEnum.getMaxDiskInMb(
//					csapDockerReferenceService,
//					getAppEnv( ) ) ;
			
			var maxAllowedDisk = ServiceAlertsEnum.getEffectiveLimit( csapDockerReferenceService, getAppEnv( ), ServiceAlertsEnum.diskSpace ) ;
			
			assertThat( maxAllowedDisk ).isEqualTo( 1024L ) ;

			var serviceLimitsForUi = ServiceAlertsEnum.getAdminUiLimits( csapDockerReferenceService,
					getAppEnv( ) ) ;
			logger.info( "adminLimits: {}", serviceLimitsForUi ) ;
			assertThat( serviceLimitsForUi ).isNotNull( ) ;
			assertThat( serviceLimitsForUi.path( JmxCommonEnum.jvmThreadCount.name( ) ).asInt( ) ).isEqualTo( 999 ) ;
			assertThat( serviceLimitsForUi.path( OsProcessEnum.threadCount.name( ) ).asInt( ) ).isEqualTo( 200 ) ;

		}

		@Test
		@DisplayName ( "verify clusters in current lifecycle" )
		public void verify_cluster_reports ( ) {

			logger.info( CsapApplication.testHeader( ) ) ;

			var clustersAllPackages = getApplication( ).buildClusterReportForAllPackagesInActiveLifecycle( ) ;

			logger.info( "clusters: {}", CSAP.jsonPrint( clustersAllPackages ) ) ;

			assertThat( clustersAllPackages.path( "base-os" ).size( ) )
					.as( "No Errors or warnings" )
					.isEqualTo( 13 ) ;

			var clustersByPackageReport = getApplication( ).buildClusterByPackageInActiveLifecycleReport( ) ;

			logger.info( "clustersByPackageReport: {}", CSAP.jsonPrint( clustersByPackageReport ) ) ;

			assertThat( clustersByPackageReport.at( "/CSAP HA Demo/base-os" ).size( ) )
					.as( "No Errors or warnings" )
					.isEqualTo( 6 ) ;

		}

		@Test
		@DisplayName ( "Health: admin mode" )
		void verify_health_admin ( )
			throws Exception {

			logger.info( CsapApplication.testHeader( ) ) ;
			getApplication( ).setJvmInManagerMode( true ) ;

			// CSAP.setLogToDebug( Application.class.getName() ) ;
			var healthReport = applicationApi.health( 0.8, true, Application.ALL_PACKAGES ) ;
			// CSAP.setLogToInfo( Application.class.getName() ) ;

			logger.info( "healthReport: {}", CSAP.jsonPrint( healthReport ) ) ;

			assertThat( healthReport.path( "Healthy" ).asBoolean( true ) ).isFalse( ) ;
			assertThat( healthReport.at( "/details" ).size( ) ).isEqualTo( 13 ) ;

		}

		@Test
		@DisplayName ( "Collection Summary: admin mode" )
		void verify_collection_summary_admin ( )
			throws Exception {

			logger.info( CsapApplication.testHeader( ) ) ;
			getApplication( ).setJvmInManagerMode( true ) ;

			// CSAP.setLogToDebug( Application.class.getName() ) ;
			var summaryReport = applicationApi.collection( true, Application.ALL_PACKAGES ) ;
			// CSAP.setLogToInfo( Application.class.getName() ) ;

			logger.debug( "summaryReport: {}", CSAP.jsonPrint( summaryReport ) ) ;

			assertThat( summaryReport.at( "/services/csap-verify-service/instances/0/thread-count" ).asInt( ) )
					.isEqualTo( 80 ) ;

			var fullReport = applicationApi.collection( false, Application.ALL_PACKAGES ) ;
			// CSAP.setLogToInfo( Application.class.getName() ) ;

			logger.debug( "fullReport: {}", CSAP.jsonPrint( fullReport ) ) ;

			assertThat( fullReport.at( "/csap-dev01/hostStats/cpuCount" ).asInt( ) ).isEqualTo( 16 ) ;

		}

		@Test
		@DisplayName ( "dev lifecycle settings" )
		void verify_dev_lifecycle_base_setting ( ) {

			logger.info( CsapApplication.testHeader( ) ) ;

			// logger.info( "stage settings: {}", getAppEnv() ) ;

			assertThat( getAppEnv( ).getLsofIntervalMins( ) ).as( "open file collect" ).isEqualTo( 1 ) ;

			var realTimeDefs = getAppEnv( ).getRealTimeMeters( ) ;
			assertThat( realTimeDefs.size( ) ).as( "real time meters" ).isEqualTo( 18 ) ;

			var trendDefs = getAppEnv( ).getTrendingConfig( ) ;
			assertThat( trendDefs.size( ) ).as( "trends" ).isEqualTo( 24 ) ;

		}

		@Test
		@DisplayName ( "verify csap_def_port+1 parsing" )
		void verify_plus_one_ports ( ) {

			logger.info( CsapApplication.testHeader( ) ) ;

			var service = getApplication( ).findFirstServiceInstanceInLifecycle( "ha-proxy-kubernetes" ) ;

			assertThat( service.getDockerSettings( ).toString( ) ).as( "missing definition source" ).contains(
					"$$service-primary-port+1" ) ;

			var updatedSettings = service.resolveRuntimeVariables( service.getDockerSettings( ).toString( ) ) ;
			logger.info( "dockerSettings:\n\n {} \n\n with variables updated:\n {}", service.getDockerSettings( )
					.toString( ),
					updatedSettings ) ;

			assertThat( updatedSettings ).as( "missing definition source" ).contains( "\"PublicPort\":\"6444\"" ) ;

			logger.info( "url: {} ", service.getUrl( ) ) ;

			assertThat( service.getUrl( ) ).as( "url with port" ).contains(
					"http://csap-dev01.***REMOVED***:6444/stats" ) ;

		}

		@Test
		@DisplayName ( "kubelet host" )
		void verify_kubelet_hosts ( ) {

			List<String> kubeletMasterHosts = getApplication( ).getActiveProject( ).getServiceInstances( "kubelet" )
					.flatMap( serviceInstance -> {

						List<String> s = CSAP.jsonStream( serviceInstance.getKubernetesMasterHostNames( ) )
								.map( JsonNode::asText )
								.collect( Collectors.toList( ) ) ;

						return s.stream( ) ;

					} )
					.distinct( )
					.collect( Collectors.toList( ) ) ;

			logger.info( "kubelet master hosts: {}", kubeletMasterHosts ) ;

			assertThat( kubeletMasterHosts )
					.contains( "csap-dev04" ) ;

			var kubeletMaster = getApplication( ).findFirstServiceInstanceInLifecycle( "kubelet" ) ;
			logger.info( "kubelet meters: {} ", kubeletMaster.getServiceMeters( ) ) ;

			assertThat( kubeletMaster.getServiceMeters( ).stream( )
					.filter( meter -> meter.getCollectionId( ).equals( "testk8scsapreferencecontainerCounts" ) )
					.count( ) )
							.isEqualTo( 1 ) ;

			logger.info( "kubelet meter titles for editor: {} ", CSAP.jsonPrint( kubeletMaster
					.getServiceMeterTitles( ) ) ) ;
			assertThat( kubeletMaster.getServiceMeterTitles( ).path( "testk8scsapreferencecontainerCounts" )
					.isTextual( ) )
							.isTrue( ) ;

			var appLabels = getApplication( ).servicePerformanceLabels( ).path( "kubelet" ) ;
			logger.info( "appLabels for editor: {} ", CSAP.jsonPrint( appLabels ) ) ;
			assertThat( appLabels.path( "testk8scsapreferencecontainerCounts" ).isTextual( ) )
					.isTrue( ) ;

		}

		@Test
		@DisplayName ( "active services loaded from application-default" )
		void verify_service_load_from_csap_default_template ( )
			throws Exception {

			logger.info( CsapApplication.testHeader( ) ) ;

			logger.info( "active model, all lifecycles: {}", getApplication( ).getActiveProject( )
					.getHostsInAllLifecycles( ) ) ;

			var testService = getApplication( ).findServiceByNameOnCurrentHost( "csap-verify-service" ) ;

			logger.info( "testService: {}", testService.toSummaryString( ) ) ;

			assertThat( testService.toSummaryString( ) )
					.isEqualTo( "csap-verify-service@csap-dev01:7011" ) ;

			var allTestInstancesDesc = getApplication( ).getActiveProject( ).getAllPackagesModel( )
					.getServiceInstances( testService.getName( ) )
					.map( ServiceInstance::toSummaryString )
					.collect( Collectors.toList( ) ) ;

			logger.info( "allTestInstancesDesc: {}", allTestInstancesDesc ) ;

			assertThat( allTestInstancesDesc )
					.contains( "csap-verify-service@csap-dev01:7011", "csap-verify-service@csap-dev12:7011" ) ;

			var serviceLabels = getApplication( ).servicePerformanceLabels( ) ;

			var agentLabels = serviceLabels.path( CsapCore.AGENT_NAME ) ;
			logger.info( "agentLabels: {}", CSAP.jsonPrint( agentLabels ) ) ;

			assertThat( agentLabels.isObject( ) ).isTrue( ) ;

			var agentDefinition = getApplication( ).getActiveProject( ).findAndCloneServiceDefinition(
					CsapCore.AGENT_NAME ) ;
			assertThat( agentDefinition.path( Project.DEFINITION_SOURCE ).asText( ) ).isEqualTo( "csap-templates" ) ;

		}

		// @Disabled
		@Test
		@DisplayName ( "active service instances from application-default, with overrides" )
		void verify_instance_definition_overrides ( )
			throws Exception {

			logger.info( CsapApplication.testHeader( ) ) ;

			var agentDefinition = getApplication( ).getActiveProject( ).findAndCloneServiceDefinition(
					CsapCore.AGENT_NAME ) ;
			assertThat( agentDefinition.path( Project.DEFINITION_SOURCE ).asText( ) ).isEqualTo( "csap-templates" ) ;

			var agentInstance = getApplication( ).getLocalAgent() ;
			var agentMonitors = agentInstance.getMonitors( ) ;

			logger.info( "agentMonitors: {}", CSAP.jsonPrint( agentMonitors ) ) ;

			assertThat( agentMonitors.path( ServiceAlertsEnum.diskSpace.maxId( ) ).asLong( ) ).isEqualTo( 199 ) ;

			logger.info( "agent description: {}", agentInstance.getDescription( ) ) ;
			assertThat( agentInstance.getDescription( ) ).isEqualTo( "junit-lifecycle-override" ) ;

			var dockerInstance = getApplication( ).findServiceByNameOnCurrentHost( "docker" ) ;
			assertThat( dockerInstance.getDescription( ) ).isEqualTo( "junit-docker-description-override" ) ;

		}

		@Test
		@DisplayName ( "service is not in application.json - loaded from disk" )
		void verify_service_from_disk ( )
			throws Exception {

			logger.info( CsapApplication.testHeader( ) ) ;

			var serviceFromFile = getApplication( ).getActiveProject( ).findAndCloneServiceDefinition(
					"service-from-file" ) ;
			logger.info( "serviceFromFile: {}", serviceFromFile.toString( ) ) ;
			assertThat( serviceFromFile.path( ServiceAttributes.description.json( ) ).asText( ) ).isEqualTo(
					"junit-service-from-file" ) ;
			// assertThat( agentDefinition.path( ReleasePackage.DEFINITION_SOURCE ).asText()
			// ).isEqualTo( "csap-templates" ) ;

			var fromFileInstance = getApplication( ).findServiceByNameOnCurrentHost( "service-from-file" ) ;
			logger.info( "fromFileInstance description: {}", fromFileInstance.getDescription( ) ) ;
			assertThat( fromFileInstance.getDescription( ) ).isEqualTo( "junit-service-from-file" ) ;

		}

		@Test
		@DisplayName ( "launch urls" )
		void verify_launch_url ( )
			throws Exception {

			logger.info( CsapApplication.testHeader( ) ) ;

			var firstAgentUrl = getApplication( ).getActiveProject( ).getServiceInstances( CsapCore.AGENT_NAME )
					.findFirst( ).get( ).getUrl( ) ;

			logger.info( "first agent launch url: {}", firstAgentUrl ) ;

			assertThat( firstAgentUrl )
					.isEqualTo( "http://csap-dev01.yourorg.org:8011/" ) ;

			var firstPostgres = getApplication( ).getActiveProject( ).getServiceInstances( "test-k8s-postgres" )
					.findFirst( ).get( ) ;

			logger.info( "{} launch url: {}", firstPostgres, firstPostgres.getUrl( ) ) ;

			// postgres url pattern: "url":
			// "http://$$service-fqdn-host:8014/api/v1/namespaces/$$service-namespace/services/$$service-name-service",

			assertThat( firstPostgres.getUrl( ) )
					.isEqualTo(
							"http://csap-dev04.***REMOVED***:8014/api/v1/namespaces/csap-test/services/test-k8s-postgres-service" ) ;

		}

		@Test
		@DisplayName ( "active model agent instances" )
		void verify_active_model_agent_instances ( )
			throws Exception {

			logger.info( CsapApplication.testHeader( ) ) ;

			logger.info( "active model, all lifecycles: {}", getApplication( ).getActiveProject( )
					.getHostsInAllLifecycles( ) ) ;

			assertThat( getApplication( ).getActiveProject( ).getHostsInAllLifecycles( ) )
					.contains( "csap-dev01", "csap-dev20", "csap-stg01" ) ;

			logger.info( "all model, all lifecycles: {}", getApplication( ).getAllPackages( )
					.getHostsInAllLifecycles( ) ) ;

			assertThat( getApplication( ).getAllPackages( ).getHostsInAllLifecycles( ) )
					.contains( "csap-dev01", "csap-dev20", "csap-dev07", "csap-stg01" ) ;

			assertThat( getApplication( ).getActiveProject( ).getHostsCurrentLc( ) )
					.contains( "csap-dev01", "csap-dev20" )
					.doesNotContain( "csap-stg01" ) ;

			String activeModelAgents = getApplication( ).getActiveProject( ).getServiceInstances( CsapCore.AGENT_NAME )
					.map( ServiceInstance::toString )
					.collect( Collectors.joining( "\n" ) ) ;

			logger.info( "activeModelAgents: {}", activeModelAgents ) ;

			List<String> hosts = getApplication( ).getActiveProject( ).getServiceInstances( CsapCore.AGENT_NAME )
					.map( ServiceInstance::getHostName )
					.collect( Collectors.toList( ) ) ;

			assertThat( hosts )
					.contains( "csap-dev01", "csap-dev20" )
					.doesNotContain( "csap-stg01" ) ;

		}

		@Test
		@DisplayName ( "model api - host for cluster" )
		void verify_modelapi_host_for_cluster ( )
			throws Exception {

			logger.info( CsapApplication.testHeader( ) ) ;

			String activeModelAgents = getApplication( ).getActiveProject( ).getServiceInstances( CsapCore.AGENT_NAME )
					.map( ServiceInstance::toString )
					.collect( Collectors.joining( "\n" ) ) ;

			logger.info( "activeModelAgents: {}", activeModelAgents ) ;

			JsonNode hostDef = modelApi.hostsForCluster( "base-os" ) ;

			logger.info( "hosts: {}", CSAP.jsonPrint( hostDef ) ) ;
			List<String> hosts = getJsonMapper( ).readValue( hostDef.path( "hosts" ).traverse( ),
					new TypeReference<List<String>>( ) {
					} ) ;

			assertThat( hosts )
					.contains( "csap-dev07", "csap-dev01" ) ;

			var activeClusters = getApplication( ).getActiveProject( ).getClustersToHostMapInCurrentLifecycle( ) ;
			logger.info( "activeClusters: {}", activeClusters ) ;
			assertThat( activeClusters.get( "base-os" ) )
					.contains( "csap-dev01" )
					.doesNotContain( "csap-dev12" ) ;

			var clustersByPackage = modelApi.clustersByPackage( ) ;
			logger.info( "clustersByPackage: {}", CSAP.jsonPrint( clustersByPackage ) ) ;
			var baseOsHosts = CSAP.jsonList( clustersByPackage.at( "/CSAP Platform/base-os" ) ) ;

			assertThat( baseOsHosts )
					.contains( "csap-dev01" )
					.doesNotContain( "csap-dev12" ) ;

			logger.info( "getLifeClusterToHostMap: {}, \n\n getLifeCycleToHostMap: {}",
					getApplication( ).getActiveProject( ).getLifeClusterToHostMap( ),
					getApplication( ).getActiveProject( ).getLifeCycleToHostMap( ) ) ;

		}

		@Test
		@DisplayName ( "model api - service ids" )
		void verify_modelapi_serviceIds ( ) {

			logger.info( CsapApplication.testHeader( ) ) ;

			JsonNode ids = modelApi.serviceIds( null, null, false, false ) ;

			logger.info( "ids: {}", CSAP.jsonPrint( ids ) ) ;

			assertThat( ids.path( 0 ).asText( ) )
					.isEqualTo( "csap-package-linux_0" ) ;

		}

		@Test
		void verify_modelapi_serviceOrder ( ) {

			logger.info( CsapApplication.testHeader( ) ) ;

			JsonNode allServicesInOrder = modelApi.serviceNames( null, null, true, false ) ;

			logger.info( "order: {}", CSAP.jsonPrint( allServicesInOrder ) ) ;

			assertThat( allServicesInOrder.get( 0 ).asText( ) )
					.isEqualTo( "csap-package-linux       0                        1                        base-os" ) ;

			JsonNode filteredIds = modelApi.serviceNames( null, List.of( "base-os" ), true, false ) ;

			logger.info( "filteredIds: {}", CSAP.jsonPrint( filteredIds ) ) ;

			assertThat( filteredIds.size( ) )
					.isEqualTo( 6 ) ;

			JsonNode reversed = modelApi.serviceNames( null, List.of( "base-os" ), true, true ) ;

			logger.info( "reversed: {}", CSAP.jsonPrint( reversed ) ) ;
			assertThat( reversed.get( 0 ).asText( ) )
					.isEqualTo( "service-from-file        7081                     98                       base-os" ) ;

		}
	}

}
