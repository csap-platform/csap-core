package org.csap.agent.services ;

import java.util.ArrayList ;
import java.util.Arrays ;
import java.util.List ;

import org.apache.commons.lang3.StringUtils ;
import org.csap.agent.CsapCore ;
import org.csap.agent.CsapCoreService ;
import org.csap.agent.api.AgentApi ;
import org.csap.agent.integrations.CsapEvents ;
import org.csap.agent.model.Application ;
import org.csap.agent.model.ServiceInstance ;
import org.csap.helpers.CSAP ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.beans.factory.annotation.Autowired ;
import org.springframework.stereotype.Service ;
import org.springframework.util.LinkedMultiValueMap ;
import org.springframework.util.MultiValueMap ;

import com.fasterxml.jackson.core.JsonProcessingException ;
import com.fasterxml.jackson.databind.ObjectMapper ;
import com.fasterxml.jackson.databind.node.ArrayNode ;
import com.fasterxml.jackson.databind.node.ObjectNode ;

@Service
public class ServiceCommands {

	public static final String AUDIT_USERID = "auditUserid" ;
	final Logger logger = LoggerFactory.getLogger( this.getClass( ) ) ;

	@Autowired
	public ServiceCommands (
			Application csapApp,
			ServiceOsManager serviceManager,
			CsapEvents csapEventClient ) {

		this.csapApp = csapApp ;
		this.serviceOsManager = serviceManager ;
		this.csapEventClient = csapEventClient ;

	}

	private ServiceOsManager serviceOsManager ;
	private CsapEvents csapEventClient ;
	private Application csapApp ;

	ObjectMapper jacksonMapper = new ObjectMapper( ) ;

	public ObjectNode killRemoteRequests (
											String auditUserid ,
											List<String> services ,
											List<String> hosts ,
											String clean ,
											String keepLogs ,
											String apiUserid ,
											String apiPass ) {

		ObjectNode resultJson = jacksonMapper.createObjectNode( ) ;

		ArrayNode servicesArray = resultJson.putArray( "services" ) ;
		services.forEach( servicesArray::add ) ;
		ArrayNode hostsArray = resultJson.putArray( "hosts" ) ;
		hosts.forEach( hostsArray::add ) ;

		if ( ! csapApp.isAdminProfile( ) ) {

			resultJson
					.put( "error",
							"refer to /api/deploy/host/* to deploy on hosts" ) ;

		} else if ( hosts.size( ) == 0 || services.size( ) == 0 ) {

			resultJson
					.put( "error",
							"missing cluster parameter" ) ;

		} else {

			MultiValueMap<String, String> stopParameters = new LinkedMultiValueMap<String, String>( ) ;
			stopParameters.set( AUDIT_USERID, auditUserid ) ;
			stopParameters.put( "services", services ) ;
			stopParameters.put( "hosts", hosts ) ;
			;

			if ( isPresent( clean ) ) {

				stopParameters.set( "clean", clean ) ;

			}

			if ( isPresent( keepLogs ) ) {

				stopParameters.set( "keepLogs", keepLogs ) ;

			}

			logger.debug( "* Stopping to: {}, params: {}", hosts, stopParameters ) ;

			ObjectNode clusterResponse = serviceOsManager.remoteAgentsApi(
					apiUserid,
					apiPass,
					hosts,
					CsapCoreService.API_AGENT_URL + AgentApi.KILL_SERVICES_URL,
					stopParameters ) ;

			logger.debug( "Results: {}", clusterResponse ) ;

			resultJson.set( "clusteredResults", clusterResponse ) ;

			csapApp.getHostStatusManager( ).refreshAndWaitForComplete( null ) ;

		}

		return resultJson ;

	}

	public ObjectNode killRequest (
									String apiUserid ,
									List<String> services ,
									String clean ,
									String keepLogs ,
									String auditUserid )
		throws JsonProcessingException {

		ObjectNode resultJson = jacksonMapper.createObjectNode( ) ;

		if ( csapApp.isAdminProfile( ) ) {

			resultJson
					.put( CsapCore.CONFIG_PARSE_ERROR,
							"Common only valid on agents" ) ;

		} else {

			services.stream( ).forEach( service_port -> {

				resultJson.put( "serviceName", service_port ) ;

				try {

					ServiceInstance instance = csapApp.getServiceInstanceCurrentHost( service_port ) ;

					if ( instance == null ) {

						resultJson.put( "error", "Service requested does not exist: " + service_port ) ;

					} else {

						// (userid, serviceNamePort, params, javaOpts, runtime)
						ArrayList<String> params = new ArrayList<String>( ) ;

						if ( isPresent( clean ) ) {

							params.add( "-cleanType" ) ;
							params.add( clean ) ;

						}

						if ( isPresent( keepLogs ) ) {

							params.add( "-keepLogs" ) ;

						}

						if ( isPresent( auditUserid ) ) {

							csapEventClient.publishUserEvent(
									CsapEvents.CSAP_USER_SERVICE_CATEGORY + "/" + instance.getName( ),
									auditUserid, "Kill Request Received", " clean:" + clean ) ;

							// log the originator of request
						}

						serviceOsManager.submitKillJob( apiUserid, service_port, params ) ;

						resultJson
								.put( "results", "Request queued" ) ;

					}

				} catch ( Exception e ) {

					logger.error( "Failed deployment", e ) ;

				}

			} ) ;

		}

		return resultJson ;

	}

	public ObjectNode stopRequest ( String apiUserid , List<String> services , String auditUserid ) {

		ObjectNode stopResultReport = jacksonMapper.createObjectNode( ) ;

		if ( csapApp.isAdminProfile( ) ) {

			stopResultReport
					.put( CsapCore.CONFIG_PARSE_ERROR,
							"Common only valid on agents" ) ;

		}

		if ( ! csapApp.isBootstrapComplete( ) ) {

			stopResultReport
					.put( CsapCore.CONFIG_PARSE_WARN,
							"Agent is currently restarting - wait a few minutes and try again" ) ;

		} else {

			services.stream( ).forEach( service_port -> {

				stopResultReport.put( "serviceName", service_port ) ;

				try {

					ServiceInstance instance = csapApp.getServiceInstanceCurrentHost( service_port ) ;

					if ( instance == null ) {

						stopResultReport.put( "error", "Service requested does not exist: " + service_port ) ;

					} else {

						// (userid, serviceNamePort, params, javaOpts, runtime)
						ArrayList<String> params = new ArrayList<String>( ) ;

						logger.debug( "params: {}", params ) ;

						// resultJson.put( service_port,
						// "Queueing start, service configuration: " + params +
						// " commandArguments:" + commandArguments );

						if ( serviceOsManager.getOpsQueued( ) > 0 ) {

							stopResultReport.put( CsapCore.CONFIG_PARSE_WARN,
									" Multiple jobs are currently queued: \n"
											+ serviceOsManager.getQueuedDeployments( ) ) ;

						}

						if ( auditUserid != null && auditUserid.length( ) > 0 ) {

							csapEventClient.publishUserEvent(
									CsapEvents.CSAP_USER_SERVICE_CATEGORY + "/" + instance.getName( ),
									auditUserid, "Start Request Received", " params:" + params ) ;

							// log the originator of request
						}

						if ( instance.is_cluster_kubernetes( ) ) {

							MultiValueMap<String, String> rebuildVariables = new LinkedMultiValueMap<String, String>( ) ;
							serviceOsManager.submitDeployJob( auditUserid, instance, rebuildVariables, false, true ) ;

						} else {

							serviceOsManager.submitStopJob( apiUserid, service_port, params ) ;

						}

						stopResultReport.put( "results", "Request queued" ) ;

					}

				} catch ( Exception e ) {

					logger.error( "Failed deployment", e ) ;

				}

			} ) ;

		}

		logger.debug( "Results: {}", stopResultReport ) ;

		return stopResultReport ;

	}

	public ObjectNode stopRemoteRequests (
											String auditUserid ,
											List<String> services ,
											List<String> hosts ,
											String apiUserid ,
											String apiPass ) {

		ObjectNode resultJson = jacksonMapper.createObjectNode( ) ;

		ArrayNode servicesArray = resultJson.putArray( "services" ) ;
		services.forEach( servicesArray::add ) ;
		ArrayNode hostsArray = resultJson.putArray( "hosts" ) ;
		hosts.forEach( hostsArray::add ) ;

		if ( ! csapApp.isAdminProfile( ) ) {

			resultJson
					.put( "error",
							"refer to /api/deploy/host/* to deploy on hosts" ) ;

		} else if ( hosts.size( ) == 0 || services.size( ) == 0 ) {

			resultJson
					.put( "error",
							"missing cluster parameter" ) ;

		} else {

			MultiValueMap<String, String> startParameters = new LinkedMultiValueMap<String, String>( ) ;
			startParameters.set( AUDIT_USERID, auditUserid ) ;
			startParameters.put( "services", services ) ;
			startParameters.put( "hosts", hosts ) ;

			logger.debug( "* Stopping to: {}, params: {}", Arrays.asList( hosts ), startParameters ) ;

			ObjectNode clusterResponse = serviceOsManager.remoteAgentsApi(
					apiUserid,
					apiPass,
					hosts,
					CsapCoreService.API_AGENT_URL + AgentApi.STOP_SERVICES_URL,
					startParameters ) ;

			logger.debug( "Results: {}", clusterResponse ) ;

			resultJson.set( "clusteredResults", clusterResponse ) ;

			csapApp.getHostStatusManager( ).refreshAndWaitForComplete( null ) ;

		}

		return resultJson ;

	}

	public ObjectNode startRequest (
										String apiUserid ,
										List<String> services ,
										String commandArguments ,
										String hotDeploy ,
										String startClean ,
										String startNoDeploy ,
										String auditUserid ,
										String deployId ) {

		ObjectNode resultJson = jacksonMapper.createObjectNode( ) ;

		if ( csapApp.isAdminProfile( ) ) {

			resultJson
					.put( CsapCore.CONFIG_PARSE_ERROR,
							"Common only valid on agents" ) ;

		}

		if ( ! csapApp.isBootstrapComplete( ) ) {

			resultJson
					.put( CsapCore.CONFIG_PARSE_WARN,
							"Agent is currently restarting - wait a few minutes and try again" ) ;

		} else {

			services.stream( ).forEach( service_port -> {

				resultJson.put( "serviceName", service_port ) ;

				try {

					ServiceInstance instance = csapApp.getServiceInstanceCurrentHost( service_port ) ;

					if ( instance == null ) {

						resultJson.put( "error", "Service requested does not exist: " + service_port ) ;

					} else {

						// (userid, serviceNamePort, params, javaOpts, runtime)
						ArrayList<String> params = new ArrayList<String>( ) ;

						if ( isPresent( startClean ) ) {

							params.add( "-cleanType" ) ;
							params.add( "clean" ) ;

						}

						if ( isPresent( hotDeploy ) ) {

							params.add( "-hotDeploy" ) ;

						}

						if ( isPresent( startNoDeploy ) ) {

							params.add( "-skipDeployment" ) ;

						}

						logger.debug( "params: {}", params ) ;

						// resultJson.put( service_port,
						// "Queueing start, service configuration: " + params +
						// " commandArguments:" + commandArguments );

						if ( serviceOsManager.getOpsQueued( ) > 0 ) {

							resultJson.put( CsapCore.CONFIG_PARSE_WARN,
									" Multiple jobs are currently queued: \n"
											+ serviceOsManager.getQueuedDeployments( ) ) ;

						}

						if ( auditUserid != null && auditUserid.length( ) > 0 ) {

							csapEventClient.publishUserEvent(
									CsapEvents.CSAP_USER_SERVICE_CATEGORY + "/" + instance.getName( ),
									auditUserid, "Start Request Received", " params:" + params ) ;

							// log the originator of request
						}

						if ( instance.is_cluster_kubernetes( ) ) {

							MultiValueMap<String, String> rebuildVariables = new LinkedMultiValueMap<String, String>( ) ;
							serviceOsManager.submitDeployJob( auditUserid, instance, rebuildVariables, false, true ) ;
							;

						} else {

							serviceOsManager.submitStartJob( apiUserid, service_port, params, commandArguments,
									deployId ) ;

						}

						resultJson
								.put( "results", "Request queued" ) ;

					}

				} catch ( Exception e ) {

					logger.error( "Failed deployment", e ) ;

				}

			} ) ;

		}

		logger.debug( "Results: {}", resultJson ) ;

		return resultJson ;

	}

	public ObjectNode startRemoteRequests (
											String auditUserid ,
											List<String> services ,
											List<String> hosts ,
											String commandArguments ,
											String hotDeploy ,
											String startClean ,
											String noDeploy ,
											String apiUserid ,
											String apiPass ,
											String deployId ) {

		ObjectNode resultJson = jacksonMapper.createObjectNode( ) ;

		ArrayNode servicesArray = resultJson.putArray( "services" ) ;
		services.forEach( servicesArray::add ) ;
		ArrayNode hostsArray = resultJson.putArray( "hosts" ) ;
		hosts.forEach( hostsArray::add ) ;

		if ( ! csapApp.isAdminProfile( ) ) {

			resultJson
					.put( "error",
							"refer to /api/deploy/host/* to deploy on hosts" ) ;

		} else if ( hosts.size( ) == 0 || services.size( ) == 0 ) {

			resultJson
					.put( "error",
							"missing cluster parameter" ) ;

		} else {

			MultiValueMap<String, String> startParameters = new LinkedMultiValueMap<String, String>( ) ;
			startParameters.set( AUDIT_USERID, auditUserid ) ;
			startParameters.put( "services", services ) ;
			startParameters.put( "hosts", hosts ) ;

			if ( isPresent( deployId ) ) {

				startParameters.set( "deployId", deployId ) ;

			}

			if ( isPresent( commandArguments ) ) {

				startParameters.add( "commandArguments", commandArguments ) ;

			}

			if ( isPresent( startClean ) ) {

				startParameters.add( "startClean", startClean ) ;

			}

			if ( isPresent( hotDeploy ) ) {

				startParameters.add( "hotDeploy", hotDeploy ) ;

			}

			if ( isPresent( noDeploy ) ) {

				startParameters.add( "noDeploy", noDeploy ) ;

			}

			logger.debug( "* Stopping to: {}, params: {}", Arrays.asList( hosts ), startParameters ) ;

			ObjectNode clusterResponse = serviceOsManager.remoteAgentsApi(
					apiUserid,
					apiPass,
					hosts,
					CsapCoreService.API_AGENT_URL + AgentApi.START_SERVICES_URL,
					startParameters ) ;

			logger.debug( "Results: {}", clusterResponse ) ;

			resultJson.set( "clusteredResults", clusterResponse ) ;

			csapApp.getHostStatusManager( ).refreshAndWaitForComplete( null ) ;

		}

		return resultJson ;

	}

	private boolean isPresent ( String parameter ) {

		return StringUtils.isNoneEmpty( parameter ) ;

	}

	/**
	 * 
	 * if more then 1 hosts are specified and transfersHosts is null, primaryHost
	 * will be first host and remainder will be synced
	 * 
	 * 
	 */
	public ObjectNode deployRemoteRequests (
												String deployId ,
												String auditUserid ,
												List<String> services ,
												List<String> hosts ,

												String scmUserid ,
												String scmPass ,
												String scmBranch ,
												String commandArguments ,
												String hotDeploy ,
												String mavenDeployArtifact ,
												String scmCommand ,
												String transferHostsSpaceSeparated ,

												String apiUserid ,
												String apiPass ) {

		ObjectNode resultJson = jacksonMapper.createObjectNode( ) ;

		ArrayNode servicesArray = resultJson.putArray( "services" ) ;
		services.forEach( servicesArray::add ) ;
		ArrayNode hostsArray = resultJson.putArray( "hosts" ) ;

		List<String> primaryHost = hosts ;

		if ( hosts.size( ) > 1 && transferHostsSpaceSeparated == null ) {

			StringBuilder syncHosts = new StringBuilder( ) ;
			hosts
					.stream( )
					.distinct( )
					.map( host -> host + " " )
					.forEach( syncHosts::append ) ;

			// strip off first host
			String firstHost = syncHosts.substring( 0, syncHosts.indexOf( " " ) ).trim( ) ;
			transferHostsSpaceSeparated = syncHosts.substring( syncHosts.indexOf( " " ) ).trim( ) ;

			primaryHost = new ArrayList<>( ) ;
			primaryHost.add( firstHost ) ;

			logger.info( CSAP.buildDescription(
					"Deployment",
					"services", services,
					"primaryHost", primaryHost,
					"transfer hosts", transferHostsSpaceSeparated ) ) ;

		}

		hosts.forEach( hostsArray::add ) ;

		if ( ! csapApp.isAdminProfile( ) ) {

			resultJson
					.put( "error",
							"refer to /api/deploy/host/* to deploy on hosts" ) ;

		} else if ( hosts.size( ) == 0 || services.size( ) == 0 ) {

			resultJson
					.put( "error",
							"missing cluster parameter" ) ;

		} else {

			MultiValueMap<String, String> deployParameters = new LinkedMultiValueMap<String, String>( ) ;
			deployParameters.set( AUDIT_USERID, auditUserid ) ;
			deployParameters.set( "primaryHost", primaryHost.get( 0 ) ) ;
			deployParameters.set( "deployId", deployId ) ;
			deployParameters.put( "services", services ) ;
			deployParameters.put( "hosts", hosts ) ;

			if ( isPresent( commandArguments ) ) {

				deployParameters.set( "commandArguments", commandArguments ) ;

			}

			if ( isPresent( scmUserid ) ) {

				deployParameters.set( "scmUserid", scmUserid ) ;

			}

			if ( isPresent( scmPass ) ) {

				deployParameters.set( "scmPass", scmPass ) ;

			}

			if ( isPresent( scmBranch ) ) {

				deployParameters.set( "scmBranch", scmBranch ) ;

			}

			if ( isPresent( hotDeploy ) ) {

				deployParameters.set( "hotDeploy", hotDeploy ) ;

			}

			if ( isPresent( mavenDeployArtifact ) ) {

				deployParameters.set( "mavenDeployArtifact", mavenDeployArtifact ) ;

			}

			if ( isPresent( scmCommand ) ) {

				deployParameters.set( "scmCommand", scmCommand ) ;

			}

			if ( isPresent( transferHostsSpaceSeparated ) ) {

				deployParameters.set( "targetScpHosts", transferHostsSpaceSeparated ) ;

			}

			logger.debug( "* Stopping to: {}, params: {}", Arrays.asList( hosts ), deployParameters ) ;

			ObjectNode clusterResponse = serviceOsManager.remoteAgentsApi(
					apiUserid,
					apiPass,
					hosts,
					CsapCoreService.API_AGENT_URL + AgentApi.DEPLOY_SERVICES_URL,
					deployParameters ) ;

			logger.debug( "Results: {}", clusterResponse ) ;

			resultJson.set( "clusteredResults", clusterResponse ) ;

			csapApp.getHostStatusManager( ).refreshAndWaitForComplete( null ) ;

		}

		return resultJson ;

	}

	public ObjectNode deployRequest (
										String apiUserid ,
										String primaryHost ,
										String deployId ,
										List<String> services ,
										String scmUserid ,
										String scmPass ,
										String scmBranch ,
										String commandArguments ,
										String hotDeploy ,
										String mavenDeployArtifact ,
										String scmCommand ,
										String targetScpHosts ,
										String auditUserid ) {

		ObjectNode resultJson = jacksonMapper.createObjectNode( ) ;

		if ( csapApp.isAdminProfile( ) ) {

			resultJson
					.put( CsapCore.CONFIG_PARSE_ERROR,
							"Common only valid on agents" ) ;

		}

		if ( ! csapApp.isBootstrapComplete( ) ) {

			resultJson
					.put( CsapCore.CONFIG_PARSE_WARN,
							"Agent is currently restarting - wait a few minutes and try again" ) ;

		} else {

			services.stream( ).forEach( service_port -> {

				resultJson.put( "serviceName", service_port ) ;

				try {

					ServiceInstance instance = csapApp.getServiceInstanceCurrentHost( service_port ) ;

					if ( instance == null ) {

						// some deploys select different ports - bad practice -
						// but handled
						instance = csapApp.findServiceByNameOnCurrentHost( service_port.split( "_" )[0] ) ;

						if ( instance != null ) {

							resultJson.put( "serviceName", instance.getServiceName_Port( ) ) ;

						}

					}

					if ( instance == null ) {

						resultJson.put( "error", "Service requested does not exist: " + service_port ) ;

					} else {

						MultiValueMap<String, String> rebuildVariables = new LinkedMultiValueMap<String, String>( ) ;

						// all variables are added even if null as they are used
						// in deploy
						rebuildVariables.set( "primaryHost", primaryHost ) ;
						rebuildVariables.set( "deployId", deployId ) ;
						rebuildVariables.set( "scmCommand", scmCommand ) ;
						rebuildVariables.set( "mavenDeployArtifact", mavenDeployArtifact ) ;
						rebuildVariables.set( "javaOpts", commandArguments ) ;
						rebuildVariables.set( "scmUserid", scmUserid ) ;
						rebuildVariables.set( "scmPass", scmPass ) ;
						rebuildVariables.set( "scmBranch", scmBranch ) ;
						rebuildVariables.set( "hotDeploy", hotDeploy ) ;
						rebuildVariables.set( "targetScpHosts", targetScpHosts ) ;

						String filteredVariables = ServiceOsManager.filterField( rebuildVariables.toString( ),
								"scmPass" ) ;
						logger.debug( "rebuildVariables: {}", filteredVariables ) ;

						if ( serviceOsManager.getOpsQueued( ) > 1 ) {

							resultJson.put( CsapCore.CONFIG_PARSE_WARN,
									"Request Queued behind others:\n" + serviceOsManager.getQueuedDeployments( ) ) ;

						}

						if ( auditUserid != null && auditUserid.length( ) > 0 ) {

							csapEventClient.publishUserEvent(
									CsapEvents.CSAP_USER_SERVICE_CATEGORY + "/" + instance.getName( ),
									auditUserid, "Deploy Request Received", " rebuildVariables:" + filteredVariables ) ;

							// log the originator of request
						}

						instance.getDefaultContainer( ).setCpuAuto( ) ; // will show hour class on ui
						// apiUserid or auditUserid
						serviceOsManager.submitDeployJob( auditUserid, instance, rebuildVariables, false, true ) ;
						resultJson
								.put( "results", "Request queued" ) ;

					}

				} catch ( Exception e ) {

					logger.error( "Failed deployment: {}", service_port, e ) ;

				}

			} ) ;

		}

		if ( resultJson.has( "error" ) ) {

			logger.warn( "Found errors: {}", resultJson ) ;

		}

		logger.debug( "Results: {}", resultJson ) ;

		return resultJson ;

	}

	public ObjectNode toggleRemoteDeploymentProcessing (
															String auditUserid ,
															List<String> hosts ,
															String apiUserid ,
															String apiPass ) {

		ObjectNode resultJson = jacksonMapper.createObjectNode( ) ;

		MultiValueMap<String, String> pauseParameters = new LinkedMultiValueMap<String, String>( ) ;
		pauseParameters.set( AUDIT_USERID, auditUserid ) ;
		pauseParameters.put( "hosts", hosts ) ;

		ObjectNode clusterResponse = serviceOsManager.remoteAgentsApi(
				apiUserid,
				apiPass,
				hosts,
				CsapCoreService.API_AGENT_URL + AgentApi.PAUSE_DEPLOYMENTS_URL,
				pauseParameters ) ;

		logger.debug( "Results: {}", clusterResponse ) ;

		resultJson.set( "clusteredResults", clusterResponse ) ;

		csapApp.getHostStatusManager( ).refreshAndWaitForComplete( null ) ;

		return resultJson ;

	}

	public ObjectNode toggleDeploymentProcessing (
													String auditUserid ,
													String apiUserid ) {

		var result = serviceOsManager.togglePauseOnDeployments( ) ;
		csapEventClient.publishUserEvent(
				CsapEvents.CSAP_USER_SERVICE_CATEGORY + "/deployments",
				auditUserid, "Deployement Paused: " + result.path( "isPaused" ).asBoolean( ), result ) ;

		return result ;

	}

	public ObjectNode deleteRemoteDeployments (
												String auditUserid ,
												List<String> hosts ,
												String apiUserid ,
												String apiPass ) {

		ObjectNode resultJson = jacksonMapper.createObjectNode( ) ;

		MultiValueMap<String, String> pauseParameters = new LinkedMultiValueMap<String, String>( ) ;
		pauseParameters.set( AUDIT_USERID, auditUserid ) ;
		pauseParameters.put( "hosts", hosts ) ;

		ObjectNode clusterResponse = serviceOsManager.remoteAgentsApi(
				apiUserid,
				apiPass,
				hosts,
				CsapCoreService.API_AGENT_URL + AgentApi.CLEAR_DEPLOYMENTS_URL,
				pauseParameters ) ;

		logger.debug( "Results: {}", clusterResponse ) ;

		resultJson.set( "clusteredResults", clusterResponse ) ;

		csapApp.getHostStatusManager( ).refreshAndWaitForComplete( null ) ;

		return resultJson ;

	}

	public ObjectNode deleteDeployments (
											String auditUserid ,
											String apiUserid ) {

		var result = serviceOsManager.cancelAllDeployments( ) ;
		csapEventClient.publishUserEvent(
				CsapEvents.CSAP_USER_SERVICE_CATEGORY + "/deployments",
				auditUserid, "Deployement Queue Emptied", result ) ;

		return result ;

	}

	public ObjectNode runRemoteJob (
										String serviceName ,
										String jobToRun ,
										String jobParameters ,
										String auditUserid ,
										List<String> hosts ,
										String apiUserid ,
										String apiPass ) {

		ObjectNode resultJson = jacksonMapper.createObjectNode( ) ;

		MultiValueMap<String, String> pauseParameters = new LinkedMultiValueMap<String, String>( ) ;
		pauseParameters.set( AUDIT_USERID, auditUserid ) ;
		pauseParameters.put( "hosts", hosts ) ;
		pauseParameters.set( "service", serviceName ) ;
		pauseParameters.set( "job", jobToRun ) ;
		pauseParameters.set( "jobParameters", jobParameters ) ;

		ObjectNode clusterResponse = serviceOsManager.remoteAgentsApi(
				apiUserid,
				apiPass,
				hosts,
				CsapCoreService.API_AGENT_URL + AgentApi.RUN_SERVICE_JOB,
				pauseParameters ) ;

		logger.debug( "Results: {}", clusterResponse ) ;

		resultJson.set( "clusteredResults", clusterResponse ) ;

		csapApp.getHostStatusManager( ).refreshAndWaitForComplete( null ) ;

		return resultJson ;

	}

	public ObjectNode runJob (
								String serviceName_port ,
								String jobToRun ,
								String jobParameters ,
								String auditUserid ,
								String apiUserid ) {

		var service = csapApp.flexFindFirstInstanceCurrentHost( serviceName_port ) ;

		ObjectNode resultsJson = jacksonMapper.createObjectNode( ) ;

		ServiceInstance serviceInstance = csapApp.getServiceInstanceCurrentHost( serviceName_port ) ;

		String jobResults = serviceOsManager.getOsManager( ).getJobRunner( )
				.runJobUsingDescription(
						serviceInstance,
						jobToRun,
						jobParameters ) ;

		resultsJson.put( serviceName_port, jobResults ) ;

		csapEventClient.publishUserEvent(
				CsapEvents.CSAP_USER_SERVICE_CATEGORY + "/" + service.getName( ) + "/job",
				auditUserid, "Job: " + jobToRun, resultsJson ) ;

		return resultsJson ;

	}
}
