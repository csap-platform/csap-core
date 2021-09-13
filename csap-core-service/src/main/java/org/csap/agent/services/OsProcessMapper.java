package org.csap.agent.services ;

import java.io.File ;
import java.util.ArrayList ;
import java.util.Arrays ;
import java.util.HashMap ;
import java.util.List ;
import java.util.Objects ;
import java.util.regex.Matcher ;
import java.util.regex.Pattern ;
import java.util.stream.Collectors ;

import org.apache.commons.lang3.StringUtils ;
import org.csap.agent.container.ContainerProcess ;
import org.csap.agent.container.DockerIntegration ;
import org.csap.agent.container.DockerJson ;
import org.csap.agent.model.ContainerState ;
import org.csap.agent.model.ServiceInstance ;
import org.csap.helpers.CSAP ;
import org.csap.helpers.CsapApplication ;
import org.csap.integations.CsapMicroMeter ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.util.LinkedMultiValueMap ;
import org.springframework.util.MultiValueMap ;

import com.fasterxml.jackson.databind.JsonNode ;
import com.fasterxml.jackson.databind.ObjectMapper ;
import com.fasterxml.jackson.databind.node.ArrayNode ;
import com.fasterxml.jackson.databind.node.ObjectNode ;

public class OsProcessMapper {

	final Logger logger = LoggerFactory.getLogger( this.getClass( ) ) ;

	public final static String MAPPER_TIMER = "collect-os.process-map-to-model" ;

	private static String LINE_SEPARATOR = "\n" ; // System.getProperty( "line.separator" );

	private ObjectMapper jsonMapper = new ObjectMapper( ) ;

	private File csapProcessingDirectory ;
	CsapMicroMeter.Utilities metricUtilities ;

	public OsProcessMapper ( File csapProcessingDirectory, CsapMicroMeter.Utilities metricUtilities ) {

		this.csapProcessingDirectory = csapProcessingDirectory ;
		this.metricUtilities = metricUtilities ;

	}

	private List<ContainerProcess> latestDiscoveredContainers  = new ArrayList<>() ;
	private List<OsProcess> latestDiscoveredProcesses = new ArrayList<>() ;

	private MultiValueMap<String, OsProcess> processByParent ;

	private volatile StringBuilder latestProcessSummary ;

	public String getLatestProcessSummary ( ) {

		return latestProcessSummary.toString( ) ;

	}

	/**
	 * Simple Wrapper so that we can block inside the method, and capture AOP
	 * timings
	 *
	 * @param ps_command_listing
	 * @param duResult
	 */
	public void process_find_all_service_matches (
													List<ServiceInstance> definitionServicesOnHost ,
													String ps_command_listing ,
													String du_and_df_and_docker_volume_output ,
													List<ContainerProcess> discoveredContainers ) {

		synchronized ( this ) {

			find_all_service_matches(
					definitionServicesOnHost,
					ps_command_listing,
					du_and_df_and_docker_volume_output,
					discoveredContainers ) ;

		}

	}

	private int duplicateRegistrationMessages = 0 ;

	/**
	 *
	 * stateful processsing - UI threads are polling for Updates 2. Metrics threads
	 * are polling to record results.
	 *
	 */
	private void find_all_service_matches (
											List<ServiceInstance> servicesOnCurrentHost ,
											String ps_command_listing ,
											String du_and_df_and_docker_volume_output ,
											List<ContainerProcess> discoveredContainers ) {

		// logger.info( "du: {} \n {}", CSAP.buildCsapStack( new Exception("demo") ),
		// duResult ) ;

		// clone because they will be updated
		List<ContainerProcess> workingContainers = new ArrayList<>( discoveredContainers ) ;

		var psParseTimer = metricUtilities.startTimer( ) ;
		List<OsProcess> osProcesses = parse_ps_output( ps_command_listing, LINE_SEPARATOR ) ;
		metricUtilities.stopTimer( psParseTimer, OsProcessMapper.MAPPER_TIMER + ".ps-parse" ) ;

		setLatestDiscoveredProcesses( osProcesses ) ;

		logger.debug(
				"Add org.csap.serviceDebug to debug in log4j.yml and add $PROCESSING/serviceName_port.debug to enable trace" ) ;

		String[] psLines = ps_command_listing.split( LINE_SEPARATOR ) ;
		String[] du_and_df_and_docker_volume_lines = du_and_df_and_docker_volume_output.split( LINE_SEPARATOR ) ;

		logger.debug( "Number of ps lines: {}", psLines.length ) ;

		// Multiple threads are checking status. So first we capture entire
		// state, then update the live instances.
		var serviceWorkingMetricsMap = new HashMap<String, ServiceInstance>( ) ;

		//
		// namespace monitors are processed last to avoid marking processes as
		// collected.
		//
		//
		var servicesWithNsMonitorsLast = servicesOnCurrentHost.stream( )
				.sorted( ( service1 , service2 ) -> Boolean.compare(
						service1.isKubernetesNamespaceMonitor( ),
						service2.isKubernetesNamespaceMonitor( ) ) )
				.collect( Collectors.toList( ) ) ;

		logger.debug( "servicesOnCurrentHost: {}, \n servicesWithNsMonitorsLast: {}", servicesOnCurrentHost,
				servicesWithNsMonitorsLast ) ;

		for ( var activeService : servicesWithNsMonitorsLast ) {

			// temporary collection instatnce
			var tempServiceForAggregation = new ServiceInstance( ) ;
			tempServiceForAggregation.setName( activeService.getName( ) ) ;

			var updateServiceKey = activeService.getServiceName_Port( ) ;

			if ( serviceWorkingMetricsMap.containsKey( updateServiceKey ) ) {

				if ( duplicateRegistrationMessages++ < 20 ) {

					logger.warn( "Duplicate service definition found: {}", updateServiceKey ) ;

				}

				continue ;

			}

			serviceWorkingMetricsMap.put( updateServiceKey, tempServiceForAggregation ) ;

			logger.debug( "Looking for: {}", activeService.getName( ) ) ;

			int matchesFound = 0 ;

			if ( isServiceDebug( activeService ) ) {

				logger.info( "Looking for service: {} , psOutput:\n{} \nlines Remaining:\n {}",
						activeService.getName( ), ps_command_listing, Arrays.asList( psLines ) ) ;

			}

			if ( ! activeService.is_files_only_package( ) ) {

				matchesFound = osProcesses.stream( )

						.filter( process -> {

							if ( activeService.isKubernetesNamespaceMonitor( ) ) {

								return process.isNotNamespaceMatched( ) ;

							}

							// allows single matching only!!!
							return process.isNotMatched( ) ;

						} )

						.mapToInt( process -> {

							return process_check_match_for_service(
									process,
									workingContainers,
									activeService,
									tempServiceForAggregation ) ;

						} )

						.sum( ) ;

				logger.debug( "{} Matches Found: {}", activeService.getName( ), matchesFound ) ;

			}

			var diskTimer = metricUtilities.startTimer( ) ;

			process_update_service_disk_usage( du_and_df_and_docker_volume_lines,
					activeService,
					tempServiceForAggregation ) ;

			metricUtilities.stopTimer( diskTimer, OsProcessMapper.MAPPER_TIMER + ".disk-usage" ) ;

		}

		var matchSummary = new StringBuilder( CsapApplication.LINE ) ;
		matchSummary.append( "\n Service Pids:" ) ;

		// Now we have end to end state - update the source of truth
		for ( var activeService : servicesWithNsMonitorsLast ) {

			var service_just_updated = serviceWorkingMetricsMap
					.get( activeService.getServiceName_Port( ) ) ;

			logger.debug( "updating service: {} in model with OS data:\n {}", activeService.getServiceName_Port( ),
					service_just_updated ) ;

			activeService.mergeContainerData( service_just_updated.getContainerStatusList( ) ) ;

			logger.debug( "active data: {}", activeService ) ;

			activeService.updateServiceManagementState( csapProcessingDirectory ) ;

			if ( ! activeService.is_files_only_package( ) ) {

				activeService.append_summary_information( matchSummary ) ;

			}

		}

		setLatestDiscoveredContainers( workingContainers ) ;
		matchSummary.append( CsapApplication.LINE ) ;
		latestProcessSummary = matchSummary ;

		matchSummary.append( CsapApplication.LINE ) ;
		matchSummary.append( "\n" ) ;
		matchSummary.append( CsapApplication.LINE ) ;
		matchSummary.append( "\n container:pid(s) not associated with a service definition:" ) ;

		addContainerLine( matchSummary, "CSAP Match Definition", "OS pid", "Docker Container Name" ) ;
		addContainerLine( matchSummary, "---------------------------", "------",
				"------------------------------------------------" ) ;

		workingContainers
				.stream( )
				.filter( ContainerProcess::isNotInDefinition )
				.forEach( container -> {

					addContainerLine( matchSummary, container.getMatchName( ), container.getPid( ), container
							.getContainerName( ) ) ;

					// matchSummary.append( "\n" + StringUtils.leftPad( container.getMatchName(), 30
					// )
					// + "\t: " + StringUtils.rightPad( container.getPid(), 10 )
					// + "\t" + container.getContainerName() );
				} ) ;

	}

	private List<OsProcess> parse_ps_output ( String ps_command_output , String lineDelim ) {

		// List<OsProcess> processList = new ArrayList<>() ;

		String[] psLines = ps_command_output.split( lineDelim ) ;

		List<OsProcess> processList = Arrays.asList( psLines ).stream( )

				.filter( StringUtils::isNotEmpty )

				.map( String::trim )
				.filter( StringUtils::isNotEmpty )

				.filter( line -> ! line.startsWith( "#" ) )

				.map( line -> Arrays.asList( line.split( "\\s+", 9 ) ) )
				.filter( fields -> fields.size( ) == 9 )

				.map( OsProcess::builder )
				// filter kernel processes
				.filter( osprocess -> ! osprocess.getParameters( ).startsWith( "[" ) )

				.collect( Collectors.toList( ) ) ;

		logger.debug( "process count: {}", processList.size( ) ) ;

		return processList ;

	}

	private void addContainerLine ( StringBuilder output , String column1 , String column2 , String column3 ) {

		output.append( "\n" + StringUtils.leftPad( column1, 30 )
				+ "\t\t" + StringUtils.rightPad( column2, 10 )
				+ "\t" + column3 ) ;

	}

	private int process_check_match_for_service (
													OsProcess process ,
													List<ContainerProcess> discoveredContainers ,
													ServiceInstance liveInstance ,
													ServiceInstance instanceWithUpdatedRuntime ) {

		int numberOfProcesses = 0 ;

		String process_filter_regex = liveInstance.getProcessFilter( ) ;

		if ( logger.isDebugEnabled( )
				|| isServiceDebug( liveInstance ) ) {

			logger.info( "Search for {} using {} in {}, docker: {}",
					liveInstance.getName( ), process_filter_regex, process.getParameters( ), liveInstance
							.is_docker_server( ) ) ;

		}

		if ( liveInstance.isRunUsingDocker( ) || liveInstance.is_docker_server( ) ) {

			var processTimer = metricUtilities.startTimer( ) ;
			container_match_check( process, discoveredContainers, liveInstance, instanceWithUpdatedRuntime,
					process_filter_regex ) ;
			metricUtilities.stopTimer( processTimer, OsProcessMapper.MAPPER_TIMER + ".container-mapping" ) ;

		} else if ( ! process.getParameters( ).matches( process_filter_regex ) ) {

			if ( logger.isDebugEnabled( ) ) {

				logger.debug( "\t NO Match for {} using {} in {}",
						liveInstance.getName( ), process_filter_regex, process ) ;

			}

		} else {

			if ( logger.isDebugEnabled( ) ) {

				logger.debug( "MATCH for {} using {} in {}",
						liveInstance.getName( ), process_filter_regex, process.getParameters( ) ) ;

			}

			// process_line_match( process, instanceWithUpdatedRuntime );
			instanceWithUpdatedRuntime.getDefaultContainer( ).addProcess( process ) ;
			numberOfProcesses++ ;

			if ( liveInstance.isAddChildProcesses( ) ) {

				for ( var childProcess : findChildren( process ) ) {

					instanceWithUpdatedRuntime.getDefaultContainer( ).addProcess( childProcess ) ;

				}

			}

		}
		// }

		return numberOfProcesses ;

	}

	private void container_match_check (
											OsProcess process ,
											List<ContainerProcess> discoveredContainers ,
											ServiceInstance activeService ,
											ServiceInstance serviceToUpdateRuntime ,
											String process_filter_regex ) {

		// var dockerSettings = serviceDefinition.getDockerSettingsOrMissing( ) ;
		JsonNode resolvedLocators = activeService.getResolvedLocators( ) ;
		String containerNameMatch = DockerIntegration.getProcessMatch( activeService ) ;

		Pattern testPattern = null ;

		if ( containerNameMatch.charAt( 0 ) == '(' ) {

			testPattern = Pattern.compile( containerNameMatch ) ;

		}

		Pattern matchPattern = testPattern ;

		// logger.debug( "service match path: {} discoveredContainers: {}",
		// serviceMatch, discoveredContainers );

		var namespaceMatching = resolvedLocators
				.path( DockerJson.podNamespace.json( ) )
				.asText( ) ;

		if ( logger.isDebugEnabled( )
				|| isServiceDebug( activeService ) ) {

			logger.info( "{}: namespaceMatching: {} containerNameMatch: {}", activeService.getName( ),
					namespaceMatching, containerNameMatch ) ;

		}

		discoveredContainers.stream( )

//				.filter( ContainerProcess::isNotInDefinition )

				.filter( containerProcess -> {

					if ( activeService.isKubernetesNamespaceMonitor( ) ) {

						return containerProcess.isNotNamespaceMatched( ) ;

					}

					// allows single matching only!!!
					return containerProcess.isNotInDefinition( ) ;

				} )

				.filter( containerProcess -> {

					var containerNamespace = containerProcess.getPodNamespace( ) ;

					if ( StringUtils.isNotEmpty( containerNamespace )
							&& containerNamespace.equals( "kube-system" )
							&& isServiceDebug( activeService ) ) {

						logger.info( "{}", containerProcess ) ;

					}

					if ( StringUtils.isNotEmpty( namespaceMatching )
							&& containerNamespace != null ) {

						return containerNamespace.matches( namespaceMatching ) ;

					}

					return true ;

				} )

				.filter( containerProcess -> {

					// @see Kub#isPodRunning

					// Use pod matching if specified

					var containerNamespace = containerProcess.getPodNamespace( ) ;

					if ( StringUtils.isNotEmpty( containerNamespace )
							&& containerNamespace.equals( "kube-system" )
							&& isServiceDebug( activeService ) ) {

						logger.info( "{}", containerProcess ) ;

					}

					var podMatching = resolvedLocators
							.path( DockerJson.podName.json( ) )
							.asText( ) ;

					if ( StringUtils.isNotEmpty( podMatching )
							&& containerProcess.getPodName( ) != null ) {
						//
						// use pod name label matching
						//

						// logger.info( "{} podMatching: {}", containerProcess.getPodName( ),
						// podMatching ) ;
						return containerProcess.getPodName( ).matches( podMatching ) ;

					} else if ( StringUtils.isNotEmpty( namespaceMatching )
							&& StringUtils.isNotEmpty( containerNamespace )
							&& containerNamespace.matches( namespaceMatching )
							&& containerNameMatch.equals( DockerJson.containerWildCard.json( ) ) ) {

						//
						// all containers in name space
						//
//						logger.info( "wildcard container matching: {} ", containerProcess.getContainerName( ) ) ;
						return true ;

					} else {
						//
						// use container label matching
						//

						if ( matchPattern != null ) {

							// support multiple container matches for kubernetes pods
							// return containerProcess.getMatchName( ).matches( containerNameMatch ) ;
							Matcher m = matchPattern.matcher( containerProcess.getMatchName( ) ) ;

							return m.matches( ) ;

						}

						var isMatched = containerProcess.getMatchName( ).equals( containerNameMatch ) ;

						return isMatched ;

					}

				} )

				.filter( Objects::nonNull )

				.forEach( containerProcess -> {

					if ( logger.isDebugEnabled( ) || isServiceDebug( activeService ) ) {

						logger.info( "containerNameMatch: {} containerPid: {} processPid: {}",
								containerNameMatch, containerProcess.getPid( ), process.getPid( ) ) ;

					}

					if ( containerProcess.getPid( ).equals( process.getPid( ) ) ) {

						if ( activeService.isKubernetesNamespaceMonitor( ) ) {

							containerProcess.setNameSpaceMatched( true ) ;
							process.setNamespaceMatched( true ) ;
							// mark all kubernetes containers as processed
							process.setMatched( true ) ;

						} else {

							containerProcess.setInDefinition( true ) ;
							process.setMatched( true ) ;

						}

						if ( logger.isDebugEnabled( )
								|| isServiceDebug( activeService ) ) {

							logger.info( "MATCH for {} using {} in {}",
									activeService.getName( ), containerProcess.getPid( ), process.getPid( ) ) ;

						}

						ContainerState containerState = serviceToUpdateRuntime.addContainerStatus( containerProcess
								.getContainerName( ) ) ;
						containerState.setPodNamespace( containerProcess.getPodNamespace( ) ) ;
						containerState.setPodName( containerProcess.getPodName( ) ) ;
						containerState.setContainerLabel( containerProcess.getMatchName( ) ) ;

						if ( activeService.isUseContainerPids( )
								||
								process.getParameters( ).matches( process_filter_regex ) ) {

							containerState.addProcess( process ) ;
							logger.debug( "Added process to container: {}", containerState ) ;

						}

						List<OsProcess> containerChildProcesses = findChildren( process ).stream( )
								.filter( childProcess -> {

									boolean addToResource = false ;
									logger.debug( "checking docker process '{}' for : '{}'",
											childProcess.getParameters( ),
											process_filter_regex ) ;

									if ( activeService.isUseContainerPids( )
											||
											childProcess.getParameters( ).matches( process_filter_regex ) ) {

										addToResource = true ;

									}

									return addToResource ;

								} )
								.collect( Collectors.toList( ) ) ;

						for ( OsProcess childProcess : containerChildProcesses ) {

							logger.debug( "Adding : {}", childProcess ) ;
							containerState.addProcess( childProcess ) ;

						}

						// return true;
					}

				} ) ;

	}

	private void process_update_service_disk_usage (
														String[] du_and_df_and_docker_volume_lines ,
														ServiceInstance serviceDefinition ,
														ServiceInstance serviceToUpdateRuntime ) {

		if ( logger.isDebugEnabled( ) ) {

			logger.debug( "{} \n du_and_df_output: {} ", serviceDefinition, Arrays.asList(
					du_and_df_and_docker_volume_lines ) ) ;

		}

		// initializes container state of non running services. This enables disk stats
		// to be captured
		serviceToUpdateRuntime.getDefaultContainer( ) ;

		// docker/k8s names are updated in updated runtime
		// inactive services

		for ( ContainerState container_state_to_update : serviceToUpdateRuntime.getContainerStatusList( ) ) {

			// full definition contains patterns for non-docker/k8s to use, so resolve
			// serviceMatch using both
			for ( String serviceMatch : serviceDefinition.getDiskUsageMatcher( container_state_to_update ) ) {

				for ( String diskLine : du_and_df_and_docker_volume_lines ) {

					logger.debug( "diskSearch: {}, curLine: {}", serviceMatch, diskLine ) ;

					// if ( serviceDefinition.getName().equals( CsapCore.AGENT_NAME ) ) {
					// logger.info( "{}: match: '{}' \t diskLine: '{}'",
					// serviceDefinition.getName(), serviceMatch, diskLine ) ;
					// }
					if ( isServiceDebug( serviceDefinition ) ) {

						// if ( serviceDefinition.getServiceName().contains( "crash" ) ) {
						logger.info( "{}:  match: '{}' \t diskLine: '{}'",
								serviceDefinition.getName( ), serviceMatch, diskLine ) ;

					}

					if ( diskLine.contains( serviceMatch ) ) {

						// logger.info("Match found") ;
						String[] duFields = diskLine.split( "\\s+" ) ;

						String diskUsedField = duFields[0] ;

						if ( diskUsedField.contains( "/" ) ) {

							// df output 815M/7942M /run 11% tmpfs
							diskUsedField = diskUsedField.split( "/" )[0] ;

						}

						container_state_to_update.addDiskUseage( serviceDefinition.toSummaryString( ), diskUsedField ) ;

						if ( logger.isDebugEnabled( ) ) {

							logger.debug( "{} Matched, diskSearch: '{}', Fields: {} line: '{}'",
									serviceDefinition.getName( ), serviceMatch, Arrays.asList( duFields ), diskLine ) ;

						}

						if ( isServiceDebug( serviceDefinition ) ) {
							// if ( serviceDefinition.getServiceName().contains( "crash" ) ) {

							logger.info( "{} Matched, diskSearch: {}, in  line: {}",
									serviceDefinition.getName( ), serviceMatch, diskLine ) ;

						}

						break ;

					}

				}

			}

		}

	}

	public List<ContainerProcess> getLatestDiscoveredContainers ( ) {

		return latestDiscoveredContainers ;

	}

	public String containerSummary ( ) {

		return CSAP.jsonPrint( jsonMapper.convertValue( getLatestDiscoveredContainers( ), ArrayNode.class ) ) ;

	}

	private void setLatestDiscoveredContainers ( List<ContainerProcess> latestDiscoveredServices ) {

		this.latestDiscoveredContainers = latestDiscoveredServices ;

	}

	final static Logger serviceDebugLogger = LoggerFactory.getLogger( "org.csap.serviceDebug" ) ;

	public boolean isServiceDebug ( ServiceInstance serviceInstance ) {

//		if ( serviceInstance.getName( ).startsWith( "namespace-kube" ) ) {
//
//			return true ;
//
//		}

		if ( serviceDebugLogger.isDebugEnabled( ) ) {

			File serviceDebugFile = new File(
					csapProcessingDirectory, "/" + serviceInstance.getName( ) + ".debug" ) ;

			// logger.info( "checking for {}",
			// serviceDebugFile.getAbsolutePath() );
			if ( serviceDebugFile.exists( ) ) {

				return true ;

			}

		}

		return false ;

	}

	public List<OsProcess> getLatestDiscoveredProcesses ( ) {

		return latestDiscoveredProcesses ;

	}

	public void setLatestDiscoveredProcesses ( List<OsProcess> latestDiscoveredProcesses ) {

		this.latestDiscoveredProcesses = latestDiscoveredProcesses ;

		processByParent = new LinkedMultiValueMap<>( ) ;

		latestDiscoveredProcesses.stream( ).forEach( process -> {

			processByParent.add( process.getParentPid( ), process ) ;

		} ) ;

	}

	private List<OsProcess> findChildren ( OsProcess process ) {

		List<OsProcess> descendants = new ArrayList<>( ) ;

		List<OsProcess> children = processByParent.get( process.getPid( ) ) ;

		logger.debug( "children: {}", children ) ;
		// descendants.addAll( processByParent.get( process.getProcessId() ) );

		if ( children != null ) {

			descendants.addAll( children ) ;
			List<OsProcess> m = children.stream( )
					.map( this::findChildren )
					.flatMap( List::stream )
					.collect( Collectors.toList( ) ) ;
			descendants.addAll( m ) ;

		}

		return descendants ;

	}

	public ObjectNode mapProcessToUiJson ( OsProcess osProcess ) {

		// ObjectNode processNode = osPerformanceData.putObject( osProcess.getPid() );
		ObjectNode rootNode = jsonMapper.createObjectNode( ) ;

		rootNode.put( "serviceName", "Pid: " + osProcess.getPid( ) ) ;

		ArrayNode containers = rootNode.putArray( ContainerState.JSON_KEY ) ;
		ObjectNode containerStats = containers.addObject( ) ;
		containerStats.put( "servletThreadCount", "" ) ;

		// hack for commands is to pass as disk for ui
		containerStats.put( "diskUtil", osProcess.getParameters( ) ) ;
		containerStats.put( "cpuUtil", osProcess.getCpu( ) ) ;
		containerStats.put( "currentProcessPriority", osProcess.getPriority( ) ) ;
		// processNode.put( "topCpu",
		// topStatsRunnable.getCpuForPid( Arrays.asList( osProcess.getPid() ) ) );
		containerStats.put( "threadCount", osProcess.getThreads( ) ) ;
		containerStats.put( "pid", osProcess.getPid( ) ) ;
		containerStats.put( "rssMemory", osProcess.getRssMemory( ) ) ;
		containerStats.put( "virtualMemory", osProcess.getVirtualMemory( ) ) ;
		containerStats.put( "fileCount", "" ) ;
		containerStats.put( "socketCount", "" ) ;
		containerStats.put( "runHeap", "" ) ;

		return rootNode ;

	}

}
