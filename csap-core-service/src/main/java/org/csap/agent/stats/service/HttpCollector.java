package org.csap.agent.stats.service ;

import java.io.IOException ;
import java.util.List ;
import java.util.regex.Matcher ;
import java.util.regex.Pattern ;

import org.apache.http.auth.AuthScope ;
import org.apache.http.auth.UsernamePasswordCredentials ;
import org.apache.http.client.CredentialsProvider ;
import org.apache.http.client.HttpClient ;
import org.apache.http.impl.client.BasicCredentialsProvider ;
import org.apache.http.impl.client.HttpClients ;
import org.csap.agent.CsapApis ;
import org.csap.agent.CsapConstants ;
import org.csap.agent.model.Application ;
import org.csap.agent.model.ContainerState ;
import org.csap.agent.model.ModelJson ;
import org.csap.agent.model.ServiceInstance ;
import org.csap.agent.stats.ServiceCollector ;
import org.csap.helpers.CSAP ;
import org.csap.helpers.CsapApplication ;
import org.csap.integations.micrometer.CsapMicroMeter ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.http.HttpStatus ;
import org.springframework.http.ResponseEntity ;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory ;
import org.springframework.web.client.RestTemplate ;

import com.fasterxml.jackson.databind.JsonNode ;
import com.fasterxml.jackson.databind.ObjectMapper ;
import com.fasterxml.jackson.databind.node.ObjectNode ;

import io.micrometer.core.instrument.util.StringUtils ;

public class HttpCollector {

	public static final String CSAP_SERVICE_COUNT = "csapServiceCount" ;

	private final Logger logger = LoggerFactory.getLogger( getClass( ) ) ;

	ObjectMapper jacksonMapper = new ObjectMapper( ) ;

	CsapApis csapApis ;
	private ServiceCollector serviceCollector ;

	private ObjectNode deltaLastCollected = jacksonMapper.createObjectNode( ) ;

	public HttpCollector ( CsapApis csapApis, ServiceCollector serviceCollector ) {

		this.csapApis = csapApis ;

		this.serviceCollector = serviceCollector ;

	}

	public void collectServicesOnHost ( ) {

		csapApis.application( )
				.getActiveProject( )
				.getServicesOnHost( csapApis.application( ).getCsapHostName( ) )
				.filter( serviceInstance -> serviceInstance.isHttpCollectionEnabled( ) )
				.forEach( this::executeHttpCollection ) ;

	}

	private void executeHttpCollection ( ServiceInstance serviceInstance ) {

		logger.debug( "{} titles: {} ", serviceInstance.toSummaryString( ), serviceInstance.getServiceMeterTitles( ) ) ;

		int containerNumber = 1 ;

		for ( ContainerState container : serviceInstance.getContainerStatusList( ) ) {

			String containerId = serviceInstance.getName( ) ;

			// String containerId = serviceInstance.getServiceName_Port() ;
			if ( serviceInstance.is_cluster_kubernetes( ) ) {

				containerId = serviceInstance.getName( ) + "-" + containerNumber ;

				if ( ! container.isRunning( ) ) {

					// hook for skipping data collection on hosts where kubernetes container is
					// inactive
					continue ;

				}

			}

			var applicationResults = new ServiceCollectionResults( serviceInstance, serviceCollector
					.getInMemoryCacheSize( ) ) ;

			// initialized to be 0
			serviceInstance
					.getServiceMeters( )
					.stream( )
					.filter( meter -> meter.getMeterType( ).isHttp( ) )
					.forEach( serviceMeter -> applicationResults.addCustomResultLong( serviceMeter.getCollectionId( ),
							0l ) ) ;

			if ( ! container.isRunning( ) && ! csapApis.application( ).isJunit( ) ) {

				logger.debug( "{} Skipping collections as service is down", serviceInstance.getName( ) ) ;
				container.setHealthReportCollected( null ) ;

			} else {

				var allTimer = csapApis.metrics( ).startTimer( ) ;
				var containerTimer = csapApis.metrics( ).startTimer( ) ;
				ObjectNode httpConfig = serviceInstance.getHttpCollectionSettings( ) ;

				try {

					String httpCollectionUrl = httpConfig.path( ModelJson.httpCollectionUrl.jpath( ) ).asText( ) ;
					String javaCollectionUrl = httpConfig.path( ModelJson.javaCollectionUrl.jpath( ) ).asText( ) ;
					String healthCollectionUrl = httpConfig.path( ModelJson.healthCollectionUrl.jpath( ) ).asText( ) ;
					String patternMatch = httpConfig.path( "patternMatch" ).asText( ) ;

					ResponseEntity<String> collectionResponse = null ;

					if ( StringUtils.isNotEmpty( httpCollectionUrl ) ) {

						collectionResponse = perform_collection( serviceInstance, httpCollectionUrl, httpConfig,
								container ) ;

						if ( collectionResponse != null && collectionResponse
								.getStatusCode( )
								.is2xxSuccessful( ) ) {

							processApplicationCollection( serviceInstance, containerId, applicationResults, httpConfig,
									patternMatch,
									collectionResponse ) ;

						} else {

							logger.warn( "Unable to collectionResponse: {}", collectionResponse ) ;

						}

					}

					if ( StringUtils.isNotEmpty( javaCollectionUrl ) ) {

						if ( collectionResponse == null || ! javaCollectionUrl.equals( httpCollectionUrl ) ) {

							collectionResponse = perform_collection( serviceInstance, javaCollectionUrl, httpConfig,
									container ) ;

						}

						if ( collectionResponse != null && collectionResponse
								.getStatusCode( )
								.is2xxSuccessful( ) ) {

							JsonNode jsonResponse = jacksonMapper.readTree( collectionResponse.getBody( ) ) ;
							processJavaCollection( serviceInstance.is_tomcat_collect( ), containerId,
									applicationResults, jsonResponse ) ;

						} else {

							logger.warn( "Unable to collectionResponse: {}", collectionResponse ) ;

						}

					}

					if ( StringUtils.isNotEmpty( healthCollectionUrl ) ) {

						if ( collectionResponse == null || ! javaCollectionUrl.equals( healthCollectionUrl ) ) {

							collectionResponse = perform_collection( serviceInstance, healthCollectionUrl, httpConfig,
									container ) ;

						}

						if ( collectionResponse != null && collectionResponse
								.getStatusCode( )
								.is2xxSuccessful( ) ) {

							JsonNode healthResponse = jacksonMapper.readTree( collectionResponse.getBody( ) ) ;
							JsonNode healthReport = healthResponse.path(
									CsapMicroMeter.HealthReport.Report.name.json ) ;

							if ( healthReport.isObject( ) ) {

								container.setHealthReportCollected( (ObjectNode) healthReport ) ;

							}

						} else {

							logger.warn( "Unable to collectionResponse: {}", collectionResponse ) ;

						}

					}

				} catch ( Exception e ) {

					csapApis.metrics( ).incrementCounter( "csap.collect-http.failures" ) ;
					csapApis.metrics( ).incrementCounter( "collect-http." + serviceInstance.getName( )
							+ ".failures" ) ;
					var message = "Collection failed for service '{}' \n configuration: {} \n\n reason: \"{}\";  ==> verify collection settings in definition" ;
					logger.warn( message,
							serviceInstance.getName( ), httpConfig, e.getMessage( ) ) ;

					logger.debug( "Reason: {} ", CSAP.buildCsapStack( e ) ) ;

				} finally {

					csapApis.metrics( ).stopTimer( containerTimer, "collect-http." + containerId ) ;
					csapApis.metrics( ).stopTimer( allTimer, "csap.collect-http" ) ;

				}

			}

			// will update based on collected values.
			applicationResults.add_results_to_application_collection( serviceCollector.getServiceToAppMetrics( ),
					containerId ) ;

			// other collection intervals will reuse the data from shorter intervals
			serviceCollector.getLastCollectedResults( ).put(
					containerId,
					applicationResults ) ;

			containerNumber++ ;

		}

	}

	private void processApplicationCollection (
												ServiceInstance serviceInstance ,
												String containerId ,
												ServiceCollectionResults applicationResults ,
												ObjectNode httpConfig ,
												String patternMatch ,
												ResponseEntity<String> collectionResponse )
		throws IOException {

		logger.debug( "{} processing using: {}", serviceInstance.getName( ), patternMatch ) ;

		if ( patternMatch.equalsIgnoreCase( "JSON" ) ) {

			JsonNode jsonResponse = jacksonMapper.readTree( collectionResponse.getBody( ) ) ;

			serviceInstance
					.getServiceMeters( )
					.stream( )
					.forEach(
							serviceMeter -> processHttpMeterUsingJson( serviceInstance.getName( ), serviceMeter,
									containerId, patternMatch,
									applicationResults,
									httpConfig, jsonResponse ) ) ;

		} else {

			String textResponse = collectionResponse.getBody( ) ;

			if ( serviceInstance.getName( ).equals( "httpd" ) ) {

				textResponse = csapApis.application( ).check_for_stub( textResponse, "httpCollect/httpdCollect.txt" ) ;
				;

			}

			final String collectedData = textResponse ;

			serviceInstance
					.getServiceMeters( )
					.stream( )
					.forEach( serviceMeter -> processHttpMeterUsingRegex( serviceMeter, containerId, patternMatch,
							applicationResults,
							httpConfig, collectedData ) ) ;

		}

	}

	private void processJavaCollection (
											boolean isTomcat ,
											String containerId ,
											ServiceCollectionResults applicationResults ,
											JsonNode jsonResponse ) {

		logger.debug( "{} isTomcat: {}", applicationResults.getServiceInstance( ).getName( ), isTomcat ) ;

		applicationResults.setCpuPercent( Math.round( jsonResponse.at( "/process.cpu.usage" ).asDouble( ) * 100 ) ) ;

		applicationResults.setJvmThreadCount( jsonResponse.at( "/jvm.threads.live" ).asLong( ) ) ;
		applicationResults.setJvmThreadMax( jsonResponse.at( "/jvm.threads.peak" ).asLong( ) ) ;
		applicationResults.setOpenFiles( jsonResponse.at( "/process.files.open" ).asLong( ) ) ;

		// Memory: jvm.memory.max.mb is incremented multiple times by csapmicrometer:
		// using jvm.gc.max.data.size.mb

		var heapUsed = jsonResponse.at( "/csap.heap.jvm.memory.used.mb" ).asLong( jsonResponse.at(
				"/jvm.memory.used.mb" ).asLong( ) ) ;
		applicationResults.setHeapUsed( heapUsed ) ;
		var heapMax = jsonResponse.at( "/csap.heap.jvm.memory.max.mb" ).asLong( jsonResponse.at(
				"/jvm.gc.max.data.size.mb" ).asLong( ) ) ;
		applicationResults.setHeapMax( heapMax ) ;

		// GC - use csap aggregate value if available, else use
		var minorGcPath = "/jvm.gc.pause[action=end of minor GC,cause=G1 Evacuation Pause]/total-ms" ;
		var csapMinorAggregatePath = "/csap.jvm.gc.pause.minor/total-ms" ;
		// var minorGcPath="/jvm.gc.pause[action=end of minor GC,cause=Allocation
		// Failure]/total-ms" ;
		long minorGcTotal = jsonResponse.at( csapMinorAggregatePath ).asLong( jsonResponse.at( minorGcPath ).asLong(
				0 ) ) ;
		long deltaMinorGc = javaDelta( containerId + "deltaMinorGc", minorGcTotal ) ;
		applicationResults.setMinorGcInMs( deltaMinorGc ) ;

		var majorGcPath = "/jvm.gc.pause[action=end of major GC,cause=G1 Evacuation Pause]/total-ms" ;
		var csapMajorAggregatePath = "/csap.jvm.gc.pause.major/total-ms" ;
		long majorGcTotal = jsonResponse.at( csapMajorAggregatePath ).asLong( jsonResponse.at( majorGcPath ).asLong(
				0 ) ) ;
		long deltaMajorGc = javaDelta( containerId + "deltaMajorGc", majorGcTotal ) ;
		applicationResults.setMajorGcInMs( deltaMajorGc ) ;

		//
		// Tomcat only
		//
		if ( isTomcat ) {

			// Sessions
			applicationResults.setSessionsActive( jsonResponse.at( "/tomcat.sessions.active.current" ).asLong( ) ) ;

			long deltaSessions = javaDelta( containerId + "deltaSessions",
					jsonResponse.at( "/tomcat.sessions.created" ).asLong( ) ) ;
			applicationResults.setSessionsCount( deltaSessions ) ;

			// tomcat threads
			applicationResults.setThreadsBusy( jsonResponse.at( "/tomcat.threads.busy" ).asLong( ) ) ;
			applicationResults.setThreadCount( jsonResponse.at( "/tomcat.threads.current" ).asLong( ) ) ;

			// overload: http is not collection this: instead
			applicationResults.setHttpConn( jsonResponse.at( "/tomcat.threads.busy" ).asLong( ) ) ;

			// http
			long deltaHttpRequests = javaDelta( containerId + "deltaHttpRequests",
					jsonResponse.at( "/tomcat.global.request/count" ).asLong( ) ) ;
			applicationResults.setHttpRequestCount( deltaHttpRequests ) ;

			long deltaHttpTime = javaDelta( containerId + "deltaHttpTime",
					jsonResponse.at( "/tomcat.global.request/total-ms" ).asLong( ) ) ;
			applicationResults.setHttpProcessingTime( deltaHttpTime ) ;

			// overload: http is not collection this: instead
			// double messagesPerSecond = ((double)deltaHttpRequests/deltaHttpTime)*1000;
			// applicationResults.setHttpConn( Math.round(CSAP.roundIt( messagesPerSecond, 2
			// )) ) ;

			long deltaSent = javaDelta( containerId + "deltaSent",
					jsonResponse.at( "/tomcat.global.sent.mb" ).asLong( ) * 1024 ) ;
			applicationResults.setHttpBytesSent( deltaSent ) ;

			long deltaReceived = javaDelta( containerId + "deltaReceived",
					jsonResponse.at( "/tomcat.global.received" ).asLong( ) ) ;
			applicationResults.setHttpBytesReceived( deltaReceived ) ;

		}

		applicationResults.add_results_to_java_collection( serviceCollector.getServiceToJavaMetrics( ), containerId ) ;

	}

	private long javaDelta ( String key , long collectedMetricAsLong ) {

		// logger.debug( "Servicekey: {} , collectedMetricAsLong: {}", key,
		// collectedMetricAsLong ) ;

		long last = collectedMetricAsLong ;

		if ( deltaLastCollected.has( key ) ) {

			collectedMetricAsLong = collectedMetricAsLong - deltaLastCollected.get( key ).asLong( ) ;

			if ( collectedMetricAsLong < 0 ) {

				collectedMetricAsLong = 0 ;

			}

		} else {

			collectedMetricAsLong = 0 ;

			if ( csapApis.application( ).isRunningOnDesktop( ) ) {

				last = 100 ;

			}

		}

		deltaLastCollected.put( key, last ) ;

		return collectedMetricAsLong ;

	}

	private boolean printLocalWarning = true ;

	private ResponseEntity<String> perform_collection (
														ServiceInstance serviceInstance ,
														String httpCollectionUrlRequested ,
														ObjectNode httpConfig ,
														ContainerState container )
		throws IOException {

		logger.debug( "httpCollectionUrl: {}", httpCollectionUrlRequested ) ;

		var httpCollectionUrl = serviceInstance.resolveRuntimeVariables( httpCollectionUrlRequested ) ;

		if ( serviceInstance.is_cluster_kubernetes( ) && httpCollectionUrl.contains( CsapConstants.K8_POD_IP ) ) {

			httpCollectionUrl = httpCollectionUrl.replaceAll(
					Matcher.quoteReplacement( CsapConstants.K8_POD_IP ),
					container.getPodIp( ) ) ;

		}

		var desktopTest = false ;

		if ( csapApis.application( ).isDesktopProfileActiveOrSpringNull( )
				&& httpCollectionUrl.contains( "localhost" )
				&& ( csapApis.application( ).isJunit( )
						|| ! serviceInstance.getName( ).equals( CsapConstants.AGENT_NAME ) ) ) {

			if ( printLocalWarning ) {

				logger.warn( CsapApplication.testHeader( "Switching localhost to csap-dev01: {}" ), serviceInstance
						.getName( ) ) ;
				printLocalWarning = false ;

			}

			desktopTest = true ;

			httpCollectionUrl = httpCollectionUrl.replaceAll(
					Matcher.quoteReplacement( "localhost.yourcompany.org:7011/api" ),
					Matcher.quoteReplacement( "csap-dev01.yourcompany.org:8011/api" ) ) ;

			httpCollectionUrl = httpCollectionUrl.replaceAll(
					Matcher.quoteReplacement( "localhost.yourcompany.org" ),
					Matcher.quoteReplacement( "csap-dev01.yourcompany.org" ) ) ;

			httpCollectionUrl = httpCollectionUrl.replaceAll(
					Matcher.quoteReplacement( "localhost." + CsapConstants.DEFAULT_DOMAIN ),
					Matcher.quoteReplacement( "csap-dev01.yourcompany.org" ) ) ;

			if ( httpCollectionUrl.contains( "server-status" ) ) {

				httpCollectionUrl = "http://csap-dev01.yourcompany.org:8011/service/httpd?auto" ;

			}

		}

		JsonNode user = httpConfig.get( "user" ) ;
		JsonNode pass = httpConfig.get( "pass" ) ;

		if ( httpConfig.has( csapApis.application( ).getCsapHostEnvironmentName( ) ) ) {

			user = httpConfig
					.get( csapApis.application( ).getCsapHostEnvironmentName( ) )
					.get( "user" ) ;
			pass = httpConfig
					.get( csapApis.application( ).getCsapHostEnvironmentName( ) )
					.get( "pass" ) ;

		}

		RestTemplate localRestTemplate ;

		var currentHostUrl = csapApis.application( ).getAgentUrl( "", "" ) ;

		if ( httpCollectionUrl.startsWith( currentHostUrl )
				|| desktopTest ) {

			localRestTemplate = csapApis.application( ).getAgentPooledConnection( 10l,
					(int) serviceCollector.getMaxCollectionAllowedInMs( ) / 1000 ) ;

		} else {

			localRestTemplate = getRestTemplate(
					serviceCollector.getMaxCollectionAllowedInMs( ),
					user,
					pass, serviceInstance.getName( ) + " collection password" ) ;

		}

		ResponseEntity<String> collectionResponse ;

		if ( Application.isRunningOnDesktop( ) && httpCollectionUrl.startsWith( "classpath" ) ) {
			// File stubResults = new File( getClass()
			// .getResource( httpCollectionUrl.substring(
			// httpCollectionUrl.indexOf( ":" ) + 1 ) )
			// .getFile() );

			String target = httpCollectionUrl.substring( httpCollectionUrl.indexOf( ":" ) + 1 ) ;
			String stubResults = csapApis.application( ).check_for_stub( "", target ) ;

			collectionResponse = new ResponseEntity<String>( stubResults,
					HttpStatus.OK ) ;

		} else {

			if ( logger.isDebugEnabled( ) &&
					serviceInstance.getName( ).contains( "by-spec" ) ) {

				logger.info( "httpCollectionUrl: {} ", httpCollectionUrl ) ;

			}

			logger.debug( "Performing collection from: {}", httpCollectionUrl ) ;
			collectionResponse = localRestTemplate.getForEntity( httpCollectionUrl, String.class ) ;
			logger.debug( "collectionResponse: {}", collectionResponse ) ;

			if ( logger.isDebugEnabled( ) &&
					serviceInstance.getName( ).contains( "by-spec" ) ) {

				logger.info( "collectionResponse: {} ", collectionResponse ) ;

			}

			// logger.debug("Raw Response: \n{}",
			// collectionResponse.toString());
		}

		return collectionResponse ;

	}

	private void processHttpMeterUsingRegex (
												ServiceMeter serviceMeter ,
												String containerId ,
												String patternMatch ,
												ServiceCollectionResults applicationResults ,
												JsonNode httpConfig ,
												String collectionResponse ) {

		var matcherSuffix = httpConfig.path( "patternMatch" ).asText( ) ;

		var matchedContents = "" ;

		if ( matcherSuffix.equals( "byWordIndex" ) ) {

			var words = List.of( collectionResponse.split( "\\s+" ) ) ;
			logger.debug( "matched words: {}", words ) ;

			try {

				var wordIndex = Integer.parseInt( serviceMeter.getHttpAttribute( ) ) ;
				matchedContents = words.get( wordIndex - 1 ) ;

			} catch ( Exception e ) {

				logger.warn( "Failed to parse {} using {}", serviceMeter.getHttpAttribute( ), patternMatch ) ;
				logger.debug( "Exception", e ) ;

			}

		} else {

			Pattern p = Pattern.compile( serviceMeter.getHttpAttribute( ) + matcherSuffix ) ;
			Matcher regExMatcher = p.matcher( collectionResponse ) ;

			if ( regExMatcher.find( ) ) {

				matchedContents = regExMatcher.group( 1 ) ;

			}

		}

		// logger.debug("{} Using match: {}" , collectionResponse,
		// httpConfig.get("patternMatch").asText()) ;
		if ( StringUtils.isNotEmpty( matchedContents ) ) {

			logger.debug( "{} matched {}", serviceMeter.getHttpAttribute( ), matchedContents ) ;

			try {

				double divideBy = serviceMeter.getDivideBy( serviceCollector.getCollectionIntervalSeconds( ) ) ;
				double multiplyBy = serviceMeter.getMultiplyBy( ) ;

				if ( serviceMeter.getDecimals( ) != 0 ) {

					Double collectedMetric = Double.parseDouble( matchedContents ) ;

					double roundedMetric = CSAP.roundIt( collectedMetric * multiplyBy / divideBy, serviceMeter
							.getDecimals( ) ) ;

					if ( serviceMeter.isDelta( ) ) {

						roundedMetric = deltaDecimal( serviceMeter, containerId, collectedMetric, divideBy,
								multiplyBy ) ;

					}

					applicationResults.addCustomResultDouble( serviceMeter.getCollectionId( ), roundedMetric ) ;

				} else {

					// default to round
					Double collectedMetric = Double.parseDouble( matchedContents ) ;
					long collectedMetricAsLong = Math.round( collectedMetric * multiplyBy / divideBy ) ;
					long last = collectedMetricAsLong ;

					if ( serviceMeter.isDelta( ) ) {

						String key = containerId
								+ serviceMeter.getCollectionId( ) ;

						if ( deltaLastCollected.has( key ) ) {

							collectedMetricAsLong = collectedMetricAsLong
									- deltaLastCollected
											.get( key )
											.asLong( ) ;

							if ( collectedMetricAsLong < 0 ) {

								collectedMetricAsLong = 0 ;

							}

						} else {

							collectedMetricAsLong = 0 ;

						}

						deltaLastCollected.put( key, last ) ;

					}

					applicationResults.addCustomResultLong( serviceMeter.getCollectionId( ), collectedMetricAsLong ) ;

				}

			} catch ( NumberFormatException e ) {

				logger.warn( "Failed to parse {} using {}", serviceMeter.getHttpAttribute( ), patternMatch ) ;
				logger.debug( "Exception", e ) ;

			}

		} else {

			logger.warn( "No match for: " + serviceMeter.getHttpAttribute( ) ) ;

		}

	}

	private void processHttpMeterUsingJson (
												String serviceName ,
												ServiceMeter serviceMeter ,
												String containerId ,
												String patternMatch ,
												ServiceCollectionResults applicationResults ,
												JsonNode httpConfig ,
												JsonNode collectedFromService ) {

		// support for JSON
		try {

			var collectedValueAsDouble = -1.0 ;

			if ( serviceMeter.getHttpAttribute( ).equals( "csapHostCpu" ) ) {

				collectedValueAsDouble = csapApis.application( ).metricManager( ).getLatestCpuUsage( ) ;

			} else if ( serviceMeter.getHttpAttribute( ).startsWith( CSAP_SERVICE_COUNT ) ) {

				var containerNameToCount = serviceMeter.getHttpAttribute( ).split( ":", 2 )[1] ;

				var dockerContainers = csapApis.osManager( ).getDockerContainerProcesses( ) ;

				if ( dockerContainers != null ) {

					collectedValueAsDouble = dockerContainers.stream( )
							.map( container -> container.getMatchName( ) )
							.filter( containerName -> containerNameToCount.equalsIgnoreCase( containerName ) )
							.count( ) ;

				}

				logger.debug( "containerNameToCount: {}, count: {}", containerNameToCount, collectedValueAsDouble ) ;

				// var instance = csapApis.application().findServiceByNameOnCurrentHost(
				// serviceNameToCount ) ;
//				if ( instance != null ) {
//					collectedValueAsDouble = instance.getContainerStatusList( ).size( ) ;
//				}

			} else if ( serviceMeter.getHttpAttribute( ).equals( "csapHostLoad" ) ) {

				collectedValueAsDouble = csapApis.application( ).metricManager( ).getLatestCpuLoad( ) ;

			} else {

				collectedValueAsDouble = collectedFromService.at( serviceMeter.getHttpAttribute( ) ).asDouble( ) ;

			}

			double divideBy = serviceMeter.getDivideBy( serviceCollector.getCollectionIntervalSeconds( ) ) ;
			double multiplyBy = serviceMeter.getMultiplyBy( ) ;

			if ( serviceMeter.getDecimals( ) != 0 ) {

				double roundedMetric = CSAP.roundIt( collectedValueAsDouble * multiplyBy / divideBy, serviceMeter
						.getDecimals( ) ) ;

				if ( serviceMeter.isDelta( ) ) {

					roundedMetric = deltaDecimal( serviceMeter, containerId, collectedValueAsDouble, divideBy,
							multiplyBy ) ;

				}

				applicationResults.addCustomResultDouble( serviceMeter.getCollectionId( ), roundedMetric ) ;

			} else {

				// default to round
				Double collectedMetric = collectedValueAsDouble ;
				long collectedMetricAsLong = Math.round( collectedMetric * multiplyBy / divideBy ) ;
				long last = collectedMetricAsLong ;

				if ( serviceMeter.isDelta( ) ) {

					String key = containerId + serviceMeter.getCollectionId( ) ;

					if ( deltaLastCollected.has( key ) ) {

						collectedMetricAsLong = collectedMetricAsLong
								- deltaLastCollected
										.get( key )
										.asLong( ) ;

						if ( collectedMetricAsLong < 0 ) {

							collectedMetricAsLong = 0 ;

						}

					} else {

						collectedMetricAsLong = 0 ;

					}

					deltaLastCollected.put( key, last ) ;

				}

				applicationResults.addCustomResultLong( serviceMeter.getCollectionId( ), collectedMetricAsLong ) ;

			}

		} catch ( Exception e ) {

			csapApis.metrics( ).incrementCounter( "csap.collect-http.failures" ) ;
			csapApis.metrics( ).incrementCounter( "collect-http.failures." + serviceName ) ;
			csapApis.metrics( ).incrementCounter( "collect-http.failures." + serviceName + "." + serviceMeter
					.getCollectionId( ) ) ;
			logger.debug( "Skipping attribute: \"" + serviceMeter.getHttpAttribute( ) + "\" Due to exception: " + e
					.getMessage( ) ) ;

		}

	}

	private double deltaDecimal (
									ServiceMeter serviceMeter ,
									String containerId ,
									double collectedValueAsDouble ,
									double divideBy ,
									double multiplyBy ) {

		double roundedMetric = 0.0 ;
		String deltaStorageKey = containerId + serviceMeter.getCollectionId( ) ;
		logger.debug( "deltaStorageKey: {}", deltaStorageKey ) ;

		if ( deltaLastCollected.has( deltaStorageKey ) ) {

			var deltaCollected = collectedValueAsDouble
					- deltaLastCollected
							.get( deltaStorageKey )
							.asDouble( ) ;
			roundedMetric = CSAP.roundIt( deltaCollected * multiplyBy / divideBy, serviceMeter.getDecimals( ) ) ;

			// restarts need to be reset to 0
			if ( roundedMetric < 0 ) {

				roundedMetric = 0.0 ;

			}

		}

		deltaLastCollected.put( deltaStorageKey, collectedValueAsDouble ) ;
		return roundedMetric ;

	}

	private RestTemplate getRestTemplate ( long maxConnectionInMs , JsonNode user , JsonNode pass , String desc ) {

		logger.debug( "maxConnectionInMs: {} , user: {} , Pass: {} ", maxConnectionInMs, user, pass ) ;

		HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory( ) ;

		// "user" : "$csapUser1", "pass" : "$csapPass1"
		if ( user != null && pass != null ) {

			CredentialsProvider credsProvider = new BasicCredentialsProvider( ) ;
			credsProvider.setCredentials(
					new AuthScope( null, -1 ),
					new UsernamePasswordCredentials(
							user.asText( ),
							csapApis.application( ).decode( pass.asText( ), desc ) ) ) ;

			HttpClient httpClient = HttpClients
					.custom( )
					.setDefaultCredentialsProvider( credsProvider )
					.build( ) ;
			factory.setHttpClient( httpClient ) ;

			// factory = new HttpComponentsClientHttpRequestFactory(httpClient);
		}

		factory.setConnectTimeout( (int) maxConnectionInMs ) ;
		factory.setReadTimeout( (int) maxConnectionInMs ) ;

		RestTemplate restTemplate = new RestTemplate( factory ) ;

		return restTemplate ;

	}
}
