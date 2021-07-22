package org.csap.agent.model ;

import java.io.IOException ;
import java.util.List ;
import java.util.TreeMap ;
import java.util.concurrent.atomic.AtomicInteger ;

import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;

import com.fasterxml.jackson.core.JsonParseException ;
import com.fasterxml.jackson.databind.JsonMappingException ;
import com.fasterxml.jackson.databind.ObjectMapper ;
import com.fasterxml.jackson.databind.node.ObjectNode ;

public class HealthForAgent {

	final Logger logger = LoggerFactory.getLogger( getClass( ) ) ;

	Application csapApp ;
	ObjectMapper jacksonMapper ;

	public HealthForAgent ( Application application, ObjectMapper jsonMapper ) {

		this.csapApp = application ;
		this.jacksonMapper = jsonMapper ;

	}

	public void service_summary (
									ObjectNode summaryJson ,
									boolean blocking ,
									String clusterFilter ,
									ObjectNode activeServiceReport ,
									TreeMap<String, Integer> serviceTotalCountMap ,
									TreeMap<String, String> serviceTypeMap ,
									ObjectNode hostMapNode )
		throws IOException ,
		JsonParseException ,
		JsonMappingException {

		logger.debug( "clusterFilter: {}", clusterFilter ) ;

		// int totalServicesActive = 0;
		AtomicInteger totalServicesActive = new AtomicInteger( 0 ) ;

		if ( blocking ) {

			csapApp.markServicesForFileSystemScan( false ) ;
			csapApp.run_application_scan( ) ;

		} else {

			csapApp.run_application_scan( ) ;

		}

		List<String> unregisteredContainers = csapApp.getOsManager( ).findUnregisteredContainerNames( ) ;

		if ( unregisteredContainers.size( ) > 0 ) {

			serviceTotalCountMap.put( ProcessRuntime.unregistered.getId( ), unregisteredContainers.size( ) ) ;
			serviceTypeMap.put( ProcessRuntime.unregistered.getId( ), ProcessRuntime.unregistered.getId( ) ) ;
			activeServiceReport.put( ProcessRuntime.unregistered.getId( ), unregisteredContainers.size( ) ) ;

		}

		csapApp
				.getActiveProject( )
				.getServicesWithKubernetesFiltering( csapApp.getCsapHostName( ) )
				.filter( service -> {

					return service.getLifecycle( ).startsWith( clusterFilter ) ;

				} )
				.forEach( serviceInstance -> {

					logger.debug( "{} : Active {}", serviceInstance.getName( ), serviceInstance.getDefaultContainer( )
							.isActive( ) ) ;

					var serviceName = serviceInstance.getName( ) ;
					var serviceWithConfiguration = csapApp.findServiceByNameOnCurrentHost( serviceName ) ;

					// k8s has multiple containers, everything is 1
					var numberToAdd = serviceInstance.getContainerStatusList( ).size( ) ;

					if ( serviceWithConfiguration != null
							&& serviceWithConfiguration.isAggregateContainerMetrics( ) ) {

						// only count aggregegated service as one
						numberToAdd = 1 ;

					}

					var totalServicesNow = 0 ;

					if ( serviceTotalCountMap.containsKey( serviceName ) ) {

						totalServicesNow = serviceTotalCountMap.get( serviceName ) ;

					} else {

						serviceTypeMap.put( serviceName, serviceInstance.getServerUiIconType( ) ) ;
						activeServiceReport.put( serviceName, 0 ) ;

					}

					serviceTotalCountMap.put( serviceName, numberToAdd + totalServicesNow ) ;

					if ( serviceInstance.is_cluster_kubernetes( ) && serviceInstance.isKubernetesMaster( ) ) {

						// check for override.
						int totalServiceCount = serviceInstance.getKubernetesReplicaCount( ).asInt( numberToAdd
								+ totalServicesNow ) ;
						serviceTotalCountMap.put( serviceName, totalServiceCount ) ;

					}

					if ( serviceInstance.is_files_only_package( )
							|| serviceInstance.getDefaultContainer( ).isActive( ) ) {

						int runningNow = activeServiceReport.path( serviceName ).asInt( ) ;
						totalServicesActive.incrementAndGet( ) ;
						activeServiceReport.put( serviceName, numberToAdd + runningNow ) ;

					}

				} ) ;

		ObjectNode hostStatusNode = hostMapNode.putObject( csapApp.getCsapHostName( ) ) ;
		hostStatusNode.put( "serviceTotal", csapApp.getServicesOnHost( ).size( ) ) ;
		hostStatusNode.put( "serviceActive", totalServicesActive.get( ) ) ;
		ObjectNode hostReport = csapApp.healthManager( ).build_host_status_using_cached_data( ) ;

		hostStatusNode.set( "vmLoggedIn", hostReport.path( "vmLoggedIn" ) ) ;

		hostStatusNode.put( "cpuCount",
				Integer.parseInt( hostReport.path( "cpuCount" ).asText( ) ) ) ;
		double newKB = Math
				.round( Double.parseDouble( hostReport.path( "cpuLoad" ).asText( ) ) * 10.0 ) / 10.0 ;
		hostStatusNode.put( "cpuLoad", newKB ) ;

		hostStatusNode.put( "du", hostReport.path( "du" ).longValue( ) ) ;

		int totalServices = csapApp.getServicesOnHost( ).size( ) ;
		int totalHosts = 1 ;

		summaryJson.put( "totalHostsActive", totalServicesActive.get( ) ) ;

		summaryJson.put( "totalServices", totalServices ) ;

		summaryJson.put( "totalServicesActive", totalServicesActive.get( ) ) ;

		summaryJson.put( "totalHosts", totalHosts ) ;

		return ;

	}

}
