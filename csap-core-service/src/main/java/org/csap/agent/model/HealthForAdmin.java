package org.csap.agent.model ;

import java.io.IOException ;
import java.util.List ;
import java.util.Objects ;
import java.util.TreeMap ;
import java.util.concurrent.atomic.AtomicInteger ;

import org.csap.agent.CsapCore ;
import org.csap.agent.services.HostKeys ;
import org.csap.helpers.CSAP ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;

import com.fasterxml.jackson.core.JsonParseException ;
import com.fasterxml.jackson.databind.JsonMappingException ;
import com.fasterxml.jackson.databind.JsonNode ;
import com.fasterxml.jackson.databind.ObjectMapper ;
import com.fasterxml.jackson.databind.node.ObjectNode ;

public class HealthForAdmin {

	final Logger logger = LoggerFactory.getLogger( getClass( ) ) ;

	Application application ;
	ObjectMapper jsonMapper ;

	public HealthForAdmin ( Application application, ObjectMapper jsonMapper ) {

		this.application = application ;
		this.jsonMapper = jsonMapper ;

	}

	public void service_summary (
									ObjectNode service_summary ,
									boolean blocking ,
									String csapProject ,
									String clusterFilter ,
									List<String> lifeCycleHostList ,
									ObjectNode active_services_all_hosts ,
									TreeMap<String, Integer> serviceTotalCountMap ,
									TreeMap<String, String> serviceTypeMap ,
									TreeMap<String, String> serviceRuntimeMap ,
									ObjectNode hostMapNode )

		throws IOException ,
		JsonParseException ,
		JsonMappingException {

		logger.debug( "clusterFilter: {}, lifeCycleHostList: {}", clusterFilter, lifeCycleHostList ) ;
		long lastOpMills = application.getLastOpMillis( ) ;
		service_summary.put( "lastOp", application.getLastOpMessage( ) ) ;

		int totalHosts = lifeCycleHostList.size( ) ;
		AtomicInteger totalServicesActive = new AtomicInteger( 0 ) ;
		AtomicInteger totalHostsActive = new AtomicInteger( 0 ) ;
		//
		// Deploy manager case

		if ( blocking ) {

			application.getHostStatusManager( ).refreshAndWaitForComplete( null ) ;

		}

		// First build totals map
		int totalServiceCount = service_summary_for_admin_build_totals(
				csapProject, clusterFilter, active_services_all_hosts,
				serviceTotalCountMap, serviceTypeMap, serviceRuntimeMap ) ;

		StringBuilder clusterSynchronizationMessages = new StringBuilder( ) ;

		// ArrayList<String> pending_kubernetes_errors = new ArrayList<>() ;

		// find_errors_all_hosts( clusterFilter, lifeCycleHostList, errorList,
		// pending_kubernetes_errors ) ;

		for ( String host : lifeCycleHostList ) {

			ObjectNode hostStatusNode = jsonMapper.createObjectNode( ) ;

			AtomicInteger hostServiceActive = new AtomicInteger( 0 ) ;
			AtomicInteger hostServiceTotal = new AtomicInteger( 0 ) ;

			ObjectNode agent_status = application.getHostStatusManager( ).getHostAsJson( host ) ;

			if ( agent_status != null ) {

				find_unregistered_containers( agent_status, active_services_all_hosts, serviceTotalCountMap,
						serviceTypeMap, serviceRuntimeMap ) ;

				lastOpMills = find_host_summary( agent_status, lastOpMills, host, service_summary, hostStatusNode ) ;

				find_service_for_summary( agent_status, csapProject, clusterFilter, active_services_all_hosts,
						totalServicesActive,
						totalHostsActive, clusterSynchronizationMessages, host, hostServiceActive,
						hostServiceTotal ) ;

			}

			hostStatusNode.put( "serviceTotal", hostServiceTotal.get( ) ) ;
			hostStatusNode.put( "serviceActive", hostServiceActive.get( ) ) ;

			if ( hostMapNode != null ) {

				hostMapNode.set( host, hostStatusNode ) ;

			}

		}

		if ( clusterSynchronizationMessages.length( ) > 0
				&& clusterFilter.equals( application.getCsapHostEnvironmentName( ) ) ) {

			String error = "Found one or more services not in the cluster definition in localhost. Need to resync cluster ASAP: "
					+ clusterSynchronizationMessages ;

			( (ObjectNode) service_summary ).put( "clusterSyncMessage", error ) ;
			logger.error( error ) ;

		}

		( (ObjectNode) service_summary ).put( "totalHostsActive", totalHostsActive.get( ) ) ;

		( (ObjectNode) service_summary ).put( "totalServices", totalServiceCount ) ;

		( (ObjectNode) service_summary ).put( "totalServicesActive", totalServicesActive.get( ) ) ;
		( (ObjectNode) service_summary ).put( "totalHosts", totalHosts ) ;

		logger.debug( "Completed request, services: {}, active: {}", totalServiceCount, totalServicesActive ) ;

	}

	private void find_service_for_summary (
											ObjectNode agent_status ,
											String csapProject ,
											String clusterFilter ,
											ObjectNode active_services_all_hosts ,
											AtomicInteger totalServicesActive ,
											AtomicInteger totalHostsActive ,
											StringBuilder clusterSynchronizationMessages ,
											String host ,
											AtomicInteger hostServiceActive ,
											AtomicInteger hostServiceTotal ) {

		logger.debug( "clusterFilter {}", clusterFilter ) ;

		JsonNode collected_services = agent_status.path( HostKeys.services.json( ) ) ;

		collected_services
				.fieldNames( )
				.forEachRemaining( serviceInstanceName -> {

					if ( serviceInstanceName.startsWith( CsapCore.AGENT_NAME ) ) {

						totalHostsActive.incrementAndGet( ) ;

					}

					ServiceInstance serviceWithRuntimeStats = null ;

					try {

						serviceWithRuntimeStats = ServiceInstance.buildInstance( jsonMapper,
								collected_services.get( serviceInstanceName ) ) ;

					} catch ( IOException e ) {

						logger.warn( "Failed to parse service runtime: {}", CSAP.buildCsapStack( e ) ) ;

					}

					ServiceInstance serviceWithConfiguration = //
							application.getServiceInstance( serviceInstanceName, host, csapProject ) ;

					// Scripts are ignored from summarys
					if ( serviceWithConfiguration != null
							&& ! serviceWithConfiguration.isAggregateContainerMetrics( )
							&& ! serviceWithConfiguration.is_files_only_package( )
							&& serviceWithConfiguration.getLifecycle( ).startsWith( clusterFilter ) ) {

						hostServiceTotal.incrementAndGet( ) ;

					}

					if ( active_services_all_hosts.get( serviceWithRuntimeStats.getName( ) ) == null ) {

						clusterSynchronizationMessages.append( " " + serviceWithRuntimeStats.getName( ) ) ;

					} else {

						if ( serviceWithConfiguration != null
								&& serviceWithConfiguration.getLifecycle( ).startsWith( clusterFilter ) ) {

							// k8s has multiple containers, everything is 1
							// int numberToAdd = serviceWithRuntimeStats.getContainerStatusList().size() ;

							logger.debug( "{} remoteCollect: {}",
									serviceWithConfiguration.getName( ), serviceWithConfiguration
											.isRemoteCollection( ) ) ;

							var numberRunning = 0 ;
							var numberStopped = 0 ;

							for ( ContainerState containerState : serviceWithRuntimeStats.getContainerStatusList( ) ) {

								if ( containerState.isActive( ) ) {

									numberRunning++ ;

								} else {

									numberStopped++ ;

								}

							}

							//
							// Aggregated service containers count all services as one
							// namespace monitors typical case:
							//
							int active_service_all_hosts = active_services_all_hosts
									.path( serviceWithRuntimeStats.getName( ) )
									.asInt( ) ;

							logger.debug( "{} : active {} hostServiceTotal: {}", serviceWithRuntimeStats
									.toSummaryString( ), active_service_all_hosts, hostServiceTotal ) ;

							if ( serviceWithConfiguration.isAggregateContainerMetrics( ) ) {

								if ( numberRunning > 0 ) {

									totalServicesActive.incrementAndGet( ) ;
									hostServiceActive.incrementAndGet( ) ;
									active_service_all_hosts++ ;

								}

							} else {

								totalServicesActive.getAndAdd( numberRunning ) ;
								hostServiceActive.getAndAdd( numberRunning ) ;
								active_service_all_hosts += numberRunning ;

							}

							active_services_all_hosts.put(
									serviceWithRuntimeStats.getName( ),
									active_service_all_hosts ) ;

						}

					}

				} ) ;

	}

	private void find_unregistered_containers (
												ObjectNode agent_status ,
												ObjectNode active_services_all_hosts ,
												TreeMap<String, Integer> serviceTotalCountMap ,
												TreeMap<String, String> serviceTypeMap,
												TreeMap<String, String> serviceRuntimeMap  ) {
		// logger.debug( "{} json: {}", host, agent_status ) ;

		JsonNode unregisteredContainers = agent_status.path( HostKeys.unregisteredServices.jsonId ) ;

		if ( unregisteredContainers.isArray( ) && unregisteredContainers.size( ) > 0 ) {

			if ( ! serviceTotalCountMap.containsKey( ProcessRuntime.unregistered.getId( ) ) ) {

				serviceTypeMap.put( ProcessRuntime.unregistered.getId( ), ProcessRuntime.unregistered.getId( ) ) ;
				serviceTypeMap.put( ProcessRuntime.unregistered.getId( ), ProcessRuntime.unregistered.getId( ) ) ;
				serviceTotalCountMap.put( ProcessRuntime.unregistered.getId( ), unregisteredContainers.size( ) ) ;
				active_services_all_hosts.put( ProcessRuntime.unregistered.getId( ), unregisteredContainers.size( ) ) ;

			} else {

				// increment counters
				serviceTotalCountMap.put( ProcessRuntime.unregistered.getId( ),
						serviceTotalCountMap.get( ProcessRuntime.unregistered.getId( ) )
								+
								unregisteredContainers.size( ) ) ;

				active_services_all_hosts.put( ProcessRuntime.unregistered.getId( ),
						active_services_all_hosts.get( ProcessRuntime.unregistered.getId( ) ).asInt( )
								+
								unregisteredContainers.size( ) ) ;

			}

		}

	}

	private int service_summary_for_admin_build_totals (
															String releaseFilter ,
															String clusterFilter ,
															ObjectNode activeServices ,
															TreeMap<String, Integer> serviceTotalCountMap ,
															TreeMap<String, String> serviceTypeMap ,
															TreeMap<String, String> serviceRuntimeMap ) {

		AtomicInteger totalServices = new AtomicInteger( 0 ) ;

		TreeMap<String, List<ServiceInstance>> serviceName_instances = //
				application.getProject( releaseFilter )
						.serviceInstancesInCurrentLifeByName( ) ;

		serviceName_instances
				.keySet( ).stream( )
				.map( serviceName -> {

					ServiceInstance firstServiceInstance = null ;

					if ( serviceName_instances.get( serviceName ) != null
							&& serviceName_instances.get( serviceName ).size( ) > 0 ) {

						firstServiceInstance = serviceName_instances.get( serviceName ).get( 0 ) ;

						serviceTypeMap.put( serviceName, firstServiceInstance.getServerUiIconType( ) ) ;

						serviceRuntimeMap.put( serviceName, firstServiceInstance.getUiRuntime( )) ;

					}

					return firstServiceInstance ;

				} )
				.filter( Objects::nonNull )
				.forEach( serviceInstance -> {

					String serviceName = serviceInstance.getName( ) ;

					if ( clusterFilter.equals( application.getCsapHostEnvironmentName( ) ) ) {

						try {

							activeServices.put( serviceName, 0 ) ;

							logger.debug( "service: {}, instances: {}", serviceName, serviceName_instances.get(
									serviceName ) ) ;

							if ( serviceName_instances.get( serviceName ) != null ) {

								int totalServiceCount = serviceName_instances.get( serviceName ).size( ) ;

								if ( serviceInstance.is_cluster_kubernetes( ) ) {

									// check for override.
									totalServiceCount = serviceInstance.getKubernetesReplicaCount( ).asInt(
											totalServiceCount ) ;

								}

								if ( serviceInstance.isAggregateContainerMetrics( ) ) {

									totalServiceCount = 0 ;

								}

								serviceTotalCountMap.put( serviceName, totalServiceCount ) ;

								// ignore scripts
								if ( ! serviceInstance.is_files_only_package( ) ) {

									// totalServices += serviceName_instances.get( serviceName ).size();
									totalServices.addAndGet( serviceName_instances.get( serviceName ).size( ) ) ;

								}

							} else {

								logger.warn( "serviceName: " + serviceName
										+ " has a NULL map and is being excluded from totals" ) ;

							}

						} catch ( Exception e ) {

							logger.error( "Failed to get total for service: " + serviceName, e ) ;

						}

					} else {

						// a filter was applied, so we need to iterate over all
						// services.
						int matchesFound = 0 ;
						var isKubernetesSchema = false ;

						for ( ServiceInstance filterInstance : serviceName_instances.get( serviceName ) ) {

							logger.debug( "service: '{}', cluster: '{}' clusterFilter: '{}'",
									serviceName, filterInstance.getLifecycle( ), clusterFilter ) ;

							if ( filterInstance.getLifecycle( ).startsWith( clusterFilter ) ) {

								if ( serviceInstance.is_cluster_kubernetes( ) ) {

									// check for override.
									JsonNode replicaCount = serviceInstance.getKubernetesReplicaCount( ) ;

									if ( replicaCount.isMissingNode( ) ) {

										matchesFound++ ; // 1 per node

									} else {

										var replicasConfigured = replicaCount.asInt( ) ;

										if ( replicasConfigured <= 0 ) {

											isKubernetesSchema = true ;

										}

										matchesFound = replicasConfigured ;

									}

								} else {

									matchesFound++ ;

								}

							}

						}

						if ( serviceInstance.isAggregateContainerMetrics( ) && matchesFound >= 1 ) {

							matchesFound = 1 ;

						}

						if ( ( matchesFound > 0 ) || isKubernetesSchema ) {

							activeServices.put( serviceName, 0 ) ;
							serviceTotalCountMap.put( serviceName, matchesFound ) ;
							totalServices.addAndGet( matchesFound ) ;

						}

					}

				} ) ;
		return totalServices.get( ) ;

	}

	private long find_host_summary (
										ObjectNode agent_status ,
										long lastOpMills ,
										String host ,
										ObjectNode service_summary ,
										ObjectNode hostStatusNode ) {

		JsonNode hostStats = agent_status.path( HostKeys.hostStats.jsonId ) ;

		if ( hostStats != null ) {

			if ( hostStats.has( "lastOpMillis" ) ) {

				long hostOpMillis = hostStats.path( "lastOpMillis" ).longValue( ) ;

				if ( hostOpMillis > lastOpMills ) {

					lastOpMills = hostOpMillis ;
					service_summary.put( "lastOp", host + ":" + hostStats.path( "lastOp" ).asText( "-" ) ) ;

				}

			}

			try {

				hostStatusNode.put( "cpuCount",
						Integer.parseInt( hostStats.path( "cpuCount" ).asText( ) ) ) ;
				double newKB = Math.round( Double.parseDouble( hostStats.path( "cpuLoad" )
						.asText( ) ) * 10.0 ) / 10.0 ;
				hostStatusNode.put( "cpuLoad", newKB ) ;

				hostStatusNode.set( "vmLoggedIn", hostStats.path( "vmLoggedIn" ) ) ;

			} catch ( Exception e ) {

				logger.debug( "Failed parsing {}", hostStats.path( "cpuLoad" ).asText( ), e ) ;

			}

			hostStatusNode.put( "du", hostStats.path( "du" ).longValue( ) ) ;

		}

		return lastOpMills ;

	}

}
