package org.csap.agent.model ;

import java.util.concurrent.atomic.AtomicInteger ;

import org.csap.agent.CsapCore ;
import org.csap.agent.CsapCoreService ;
import org.csap.agent.services.HostKeys ;
import org.csap.helpers.CSAP ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory ;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter ;
import org.springframework.web.client.RestTemplate ;

import com.fasterxml.jackson.databind.JsonNode ;
import com.fasterxml.jackson.databind.ObjectMapper ;
import com.fasterxml.jackson.databind.node.ObjectNode ;

public class ScoreReport {

	final Logger logger = LoggerFactory.getLogger( getClass( ) ) ;

	ObjectMapper jacksonMapper ;
	Application application ;

	private ObjectNode cachedAgentScoreReport = null ;

	private ObjectNode cachedContainerScoreReport = null ;
	private String csagentCachedRelease = "zz" ;
	private String dockerCachedRelease = "zz" ;
	private String kubeletCachedRelease = "zz" ;
	private String linuxCachedRelease = "zz" ;
	private String jdkCachedRelease = "zz" ;

	private long lastCsapToolsTimeStamp = 0 ;
	static final long CSAPTOOLS_REFRESH = 1000 * 60 * 30 ; // every 30 minutes
	final static String TOOLS_ID = Application.PERFORMANCE_ID + "remote.csaptools.runtime" ;

	public ScoreReport ( Application application, ObjectMapper jacksonMapper ) {

		this.application = application ;
		this.jacksonMapper = jacksonMapper ;

	}

	public String updatePlatformVersionsFromCsapTools (
														boolean forceUpdate ) {

		if ( ! application.rootProjectEnvSettings( ).isEventPublishEnabled( ) ) {

			logger.info( "Stubbing out data for trends - add csap events services" ) ;
			setMinVersionsUsingDefaults( ) ;
			return csagentCachedRelease + ", " + jdkCachedRelease + ", " + linuxCachedRelease ;

		}

		if ( ! forceUpdate && ( System.currentTimeMillis( ) - lastCsapToolsTimeStamp < CSAPTOOLS_REFRESH ) ) {

			logger.debug( "====> Cache Reuse or disabled" ) ;

			return csagentCachedRelease ;

		}

		HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory( ) ;
		// factory.setHttpClient(httpClient);
		// factory.getHttpClient().getConnectionManager().getSchemeRegistry().register(scheme);

		int timeInMs = application.rootProjectEnvSettings( ).getAdminToAgentTimeoutSeconds( )
				* Math.toIntExact( CSAP.ONE_SECOND_MS ) ;
		factory.setConnectTimeout( timeInMs ) ;
		factory.setReadTimeout( timeInMs ) ;

		RestTemplate restTemplate = new RestTemplate( factory ) ;
		restTemplate
				.getMessageConverters( )
				.clear( ) ;
		restTemplate
				.getMessageConverters( )
				.add( new MappingJackson2HttpMessageConverter( ) ) ;

		String adminRuntimeUrl = application.rootProjectEnvSettings( ).getCsapAnalyticsServerRootUrl( )
				+ "/" + CsapCore.ADMIN_NAME
				+ CsapCoreService.API_APPLICATION_URL
				+ "/scorecard/versions" ;

		var timer = application.metrics( ).startTimer( ) ;

		try {

			logger.debug( "Refreshing cache: {}", adminRuntimeUrl ) ;
			JsonNode minVersionReport = restTemplate.getForObject( adminRuntimeUrl, ObjectNode.class ) ;

			csagentCachedRelease = minVersionReport.path( "csap" ).asText( "notFound" ) ;
			dockerCachedRelease = minVersionReport.path( "docker" ).asText( "notFound" ) ;
			kubeletCachedRelease = minVersionReport.path( "kubelet" ).asText( "notFound" ) ;

		} catch ( Exception e ) {

			application.metrics( ).incrementCounter( TOOLS_ID + ".errors" ) ;
			logger.warn( "Failed getting platform version from:  {}, time out: '{}' seconds {} ",
					adminRuntimeUrl,
					application.rootProjectEnvSettings( ).getAdminToAgentTimeoutSeconds( ),
					CSAP.buildCsapStack( e ) ) ;

			setMinVersionsUsingDefaults( ) ;

		}

		application.metrics( ).stopTimer( timer, TOOLS_ID ) ;

		String result = csagentCachedRelease + ", " + dockerCachedRelease + ", " + kubeletCachedRelease ;

		logger.debug( "Result: {}", result ) ;

		return result ;

	}

	private void setMinVersionsUsingDefaults ( ) {

		csagentCachedRelease = application.getCsapCoreService( ).getMinVersionCsap( ) ;
		dockerCachedRelease = application.getCsapCoreService( ).getMinVersionDocker( ) ;
		kubeletCachedRelease = application.getCsapCoreService( ).getMinVersionKubelet( ) ;
		linuxCachedRelease = "6" ;
		jdkCachedRelease = "6" ;

	}

	void resetAppScoreCards ( ) {

		cachedContainerScoreReport = null ;
		cachedAgentScoreReport = null ;

	}

	public ObjectNode buildAgentScoreReport (
												boolean blocking ) {

		logger.debug( "Blocking: {}", blocking ) ;

		if ( cachedAgentScoreReport != null && ! blocking ) {

			return cachedAgentScoreReport ;

		}

		updatePlatformVersionsFromCsapTools( false ) ;

		ObjectNode updatedScoreCard = jacksonMapper.createObjectNode( ) ;

		ObjectNode hostServiceReports ;

		if ( application.isAdminProfile( ) ) {

			hostServiceReports = application.getHostStatusManager( ).serviceCollectionReport( null, null, null ) ;

		} else {

			hostServiceReports = jacksonMapper.createObjectNode( ) ;

			try {

				hostServiceReports.set( application.getCsapHostName( ), application.getOsManager( )
						.getHostRuntime( ) ) ;

			} catch ( Exception e ) {

				logger.warn( CSAP.buildCsapStack( e ) ) ;

			}

		}

		AtomicInteger agentTotal = new AtomicInteger( 0 ) ;
		AtomicInteger agentUpToDate = new AtomicInteger( 0 ) ;
		CSAP.asStreamHandleNulls( hostServiceReports ).forEach( hostName -> {

			var hostReport = hostServiceReports.path( hostName ) ;
			agentTotal.incrementAndGet( ) ;
			var jpath = "/services/" + CsapCore.AGENT_ID + "/containers/0/" + ContainerState.DEPLOYED_ARTIFACTS ;
			var hostVersion = hostReport.at( jpath ).asText( ) ;

			var agentVersionCompare = hostVersion.compareTo( csagentCachedRelease ) ;
			logger.debug( "host: {} compare: {} hostVersion: {}  minimumAgentVersion:{}",
					hostName, agentVersionCompare, hostVersion, csagentCachedRelease ) ;

			if ( agentVersionCompare >= 0 ) {

				agentUpToDate.incrementAndGet( ) ;

			}

		} ) ;

		updatedScoreCard.put( "csap", agentUpToDate.get( ) + " of " + agentTotal.get( ) ) ;
		// updatedScoreCard.put( "Redhat", rhUpToDate + " of " + redHatInstances.size()
		// ) ;
		// updatedScoreCard.put( "JDK", jdkUpToDate + " of " + jdkInstances.size() ) ;
		updatedScoreCard.put( "upToDate", agentUpToDate.get( ) ) ;
		updatedScoreCard.put( "total", agentTotal.get( ) ) ;

		cachedAgentScoreReport = updatedScoreCard ;
		return updatedScoreCard ;

	}

	public ObjectNode buildContainerScoreReport ( ) {

		if ( cachedContainerScoreReport != null ) {

			// nulled out when app is reloaded
			return cachedContainerScoreReport ;

		}

		updatePlatformVersionsFromCsapTools( false ) ;

		var applicationScoreJson = jacksonMapper.createObjectNode( ) ;

		ObjectNode hostServiceReports ;

		if ( application.isAdminProfile( ) ) {

			hostServiceReports = application.getHostStatusManager( ).serviceCollectionReport( null, HostKeys.hostStats
					.json( ), null ) ;

		} else {

			hostServiceReports = jacksonMapper.createObjectNode( ) ;

			try {

				hostServiceReports.set( application.getCsapHostName( ), application.getOsManager( )
						.getHostRuntime( ) ) ;

			} catch ( Exception e ) {

				logger.warn( CSAP.buildCsapStack( e ) ) ;

			}

		}

		AtomicInteger dockerTotal = new AtomicInteger( 0 ) ;
		AtomicInteger dockerUpToDate = new AtomicInteger( 0 ) ;

		AtomicInteger kubeletTotal = new AtomicInteger( 0 ) ;
		AtomicInteger kubeletUpToDate = new AtomicInteger( 0 ) ;

		CSAP.jsonStream( hostServiceReports ).forEach( hostReport -> {

			var dockerJPath = "/hostStats/docker/version" ;

			if ( ! hostReport.at( dockerJPath ).isMissingNode( ) ) {

				dockerTotal.incrementAndGet( ) ;
				var hostVersion = hostReport.at( dockerJPath ).asText( ) ;

				logger.debug( "hostVersion: {}  dockerCachedRelease:{}", hostVersion, dockerCachedRelease ) ;

				if ( hostVersion.compareTo( dockerCachedRelease ) >= 0 ) {

					dockerUpToDate.incrementAndGet( ) ;

				}

			}

			var kubeletJPath = "/hostStats/kubernetes/version" ;

			if ( ! hostReport.at( kubeletJPath ).isMissingNode( ) ) {

				kubeletTotal.incrementAndGet( ) ;
				var hostVersion = hostReport.at( kubeletJPath ).asText( ) ;

				if ( hostVersion.startsWith( "v" ) ) {

					hostVersion = hostVersion.substring( 1 ) ;

				}

				logger.debug( "hostVersion: {}  kubeletCachedRelease:{}", hostVersion, kubeletCachedRelease ) ;

				if ( hostVersion.compareTo( kubeletCachedRelease ) >= 0 ) {

					kubeletUpToDate.incrementAndGet( ) ;

				}

			}

		} ) ;

		applicationScoreJson.put( "dockerUpToDate", dockerUpToDate.get( ) ) ;
		applicationScoreJson.put( "dockerTotal", dockerTotal.get( ) ) ;
		applicationScoreJson.put( "kubeletUpToDate", kubeletUpToDate.get( ) ) ;
		applicationScoreJson.put( "kubeletTotal", kubeletTotal.get( ) ) ;
		// updatedScoreCard.put( "Redhat", rhUpToDate + " of " + redHatInstances.size()
		// ) ;
		// updatedScoreCard.put( "JDK", jdkUpToDate + " of " + jdkInstances.size() ) ;
		applicationScoreJson.put( "upToDate", dockerUpToDate.get( ) + kubeletUpToDate.get( ) ) ;
		applicationScoreJson.put( "total", dockerTotal.get( ) + kubeletTotal.get( ) ) ;

		cachedContainerScoreReport = applicationScoreJson ;

		return applicationScoreJson ;

	}

}
