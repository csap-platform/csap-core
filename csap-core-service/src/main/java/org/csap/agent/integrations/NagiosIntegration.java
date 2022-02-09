package org.csap.agent.integrations ;

import java.io.IOException ;
import java.nio.charset.Charset ;
import java.util.Arrays ;
import java.util.HashMap ;
import java.util.Map ;
import java.util.stream.Collectors ;
import java.util.stream.Stream ;

import org.apache.commons.io.FileUtils ;
import org.apache.commons.lang3.StringEscapeUtils ;
import org.apache.http.conn.ssl.NoopHostnameVerifier ;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory ;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy ;
import org.apache.http.impl.client.CloseableHttpClient ;
import org.apache.http.impl.client.HttpClients ;
import org.apache.http.ssl.SSLContextBuilder ;
import org.csap.agent.CsapApis ;
import org.csap.agent.CsapTemplates ;
import org.csap.agent.model.Application ;
import org.csap.agent.model.Project ;
import org.csap.agent.model.ProjectLoader ;
import org.csap.agent.model.ServiceAlertsEnum ;
import org.csap.agent.model.ServiceInstance ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.http.HttpEntity ;
import org.springframework.http.HttpHeaders ;
import org.springframework.http.HttpMethod ;
import org.springframework.http.ResponseEntity ;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory ;
import org.springframework.http.converter.FormHttpMessageConverter ;
import org.springframework.http.converter.StringHttpMessageConverter ;
import org.springframework.security.crypto.codec.Base64 ;
import org.springframework.util.LinkedMultiValueMap ;
import org.springframework.util.MultiValueMap ;
import org.springframework.web.client.RestTemplate ;

import com.fasterxml.jackson.databind.JsonNode ;
import com.fasterxml.jackson.databind.node.ArrayNode ;
import com.fasterxml.jackson.databind.node.ObjectNode ;

/**
 * 
 * Simple class for containing nagios integration utilities
 * 
 * @author someDeveloper
 *
 */
public class NagiosIntegration {

	static final Logger logger = LoggerFactory.getLogger( NagiosIntegration.class ) ;

	private final static String HOST_GROUP = "HOSTGROUP" ;
	private final static String SERVICES = "SERVICES" ;

	static public String getNagiosDefinition ( Stream<Project> capabilityModelStream ) {

		StringBuilder nagiosHostGroupsForAllModels = new StringBuilder( ) ;
		StringBuilder nagiosServiceForAllModels = new StringBuilder( ) ;

		capabilityModelStream
				.map(
						NagiosIntegration::generateModelDefinitionMap )

				.filter( modelDefinitionMap -> modelDefinitionMap.get( HOST_GROUP ) != null )

				.collect( Collectors.toList( ) )

				.forEach( ( modelDefinitionMap ) -> {

					// we put host groups at the top of output; otherwise we
					// would make this a reduce
					nagiosHostGroupsForAllModels.append( modelDefinitionMap.get( HOST_GROUP ) ) ;
					nagiosServiceForAllModels.append( modelDefinitionMap.get( SERVICES ) ) ;

				} ) ;

		return nagiosHostGroupsForAllModels.toString( ) + nagiosServiceForAllModels.toString( ) ;

	}

	static private Map<String, String> generateModelDefinitionMap ( final Project model ) {

		HashMap<String, String> groupAndDefMap = new HashMap<String, String>( ) ;

		StringBuilder nagiosHostServiceDefinition = new StringBuilder( ) ;

		String modelShortName = model.getName( ).replaceAll( " ", "_" ) + "_"
				+ CsapApis.getInstance( ).application( ).getCsapHostEnvironmentName( ) ;

		int i = 1 ;
		StringBuilder hostMembers = new StringBuilder( ) ;

		for ( String host : model.getHostsForEnvironment( CsapApis.getInstance( ).application( )
				.getCsapHostEnvironmentName( ) ) ) {

			if ( host.equals( "localhost" ) )
				continue ; // skip because nagios probably has

			if ( i++ % 3 == 0 ) {

				// "\" indicates a line continuation. No SPACES after it
				// hostMembers.append(", \\\\\n\t\t");
				hostMembers.append( ", " ) ;
				i = 1 ;

			} else {

				hostMembers.append( ", " ) ;

			}

			hostMembers.append( host ) ;

			nagiosHostServiceDefinition.append( "\n#\n####################### " + host + " ###################\n#" ) ;
			nagiosHostServiceDefinition.append( "\ndefine host{" ) ;
			nagiosHostServiceDefinition.append( "\n\t use \t\t linux-server" ) ;
			nagiosHostServiceDefinition.append( "\n\t host_name \t " + host ) ;
			nagiosHostServiceDefinition.append( "\n\t alias \t " + host ) ;
			nagiosHostServiceDefinition.append( "\n\t }\n\n" ) ;

			var services = model.getServicesListOnHost( host ) ;

			// logger.info("host: " + host + " services: \n" +
			// instancesOnHost.toString() );

			String nagiosActiveMonitors = services
					.stream( )
					.map( NagiosIntegration::generateNagiosActiveMonitors )
					.reduce( "", ( a , b ) -> a + b ) ;

			nagiosHostServiceDefinition.append( nagiosActiveMonitors ) ;

			nagiosHostServiceDefinition.append( "\n#\n#\t Passive checks with NRDP\n#" ) ;

			String nagiosPassiveMonitors = Arrays.stream(
					MetricsPublisher.getNagiosServices( ) )
					.map( monitor -> generateNagiosPassiveMonitors( monitor, host, modelShortName ) )
					.reduce( "", ( a , b ) -> a + b ) ;

			nagiosHostServiceDefinition.append( nagiosPassiveMonitors ) ;

		}

		if ( hostMembers.length( ) > 0 ) {

			groupAndDefMap.put( HOST_GROUP, nagiosConfigTemplate
					.replaceAll( "__hostGroup__", modelShortName )
					.replaceAll( "__hostGroupMembers__", hostMembers.toString( ) ) ) ;

		}

		groupAndDefMap.put( SERVICES, nagiosHostServiceDefinition.toString( ) ) ;

		return groupAndDefMap ;

	}

	static private String generateNagiosPassiveMonitors ( String monitor , String host , String modelShortName ) {

		StringBuilder nagiosHostServiceDefinition = new StringBuilder( ) ;
		nagiosHostServiceDefinition.append( "\ndefine service{" ) ;
		nagiosHostServiceDefinition.append( "\n\t use \t\t generic-service" ) ;
		nagiosHostServiceDefinition.append( "\n\t host_name \t " + host ) ;
		nagiosHostServiceDefinition.append( "\n\t service_description \t  Csap_" + monitor ) ;
		nagiosHostServiceDefinition.append( "\n\t check_command \t  " + modelShortName + "_" + monitor ) ;
		ArrayNode nagiosOptionsFromCsapDefn = getNagiosOptionsFromCsapDefn( ) ;

		if ( nagiosOptionsFromCsapDefn != null && nagiosOptionsFromCsapDefn.size( ) > 0 ) {

			for ( JsonNode configLine : nagiosOptionsFromCsapDefn ) {

				if ( configLine.asText( ).contains( "active_checks_enabled" ) )
					continue ;
				nagiosHostServiceDefinition.append( "\n\t " + configLine.asText( ) ) ;

			}

		} else {

			nagiosHostServiceDefinition.append( "\n\t passive_checks_enabled \t  1" ) ;

		}

		nagiosHostServiceDefinition.append( "\n\t active_checks_enabled  \t  0" ) ;
		nagiosHostServiceDefinition.append( "\n\t }\n\n" ) ;

		return nagiosHostServiceDefinition.toString( ) ;

	}

	// private static final ClassPathResource configTemplateLocation = new
	// ClassPathResource( "nagiosTemplate.cfg" );
	// private static final ClassPathResource resultTemplateLocation = new
	// ClassPathResource( "nagiosResultTemplate.xml" );
	private static final String nagiosConfigTemplate ;
	private static final String nagiosResultTemplate ;
	static {

		try {

			// load template file and replace EOL characters "\\r|\\n"
			nagiosConfigTemplate = FileUtils.readFileToString( CsapTemplates.nagios_config.getFile( ) ).replaceAll(
					"\\r\\n|\\r|\\n", System.getProperty( "line.separator" ) ) ;
			nagiosResultTemplate = FileUtils.readFileToString( CsapTemplates.nagios_result.getFile( ) ).replaceAll(
					"\\r\\n|\\r|\\n", System.getProperty( "line.separator" ) ) ;

		} catch ( IOException e ) {

			throw new RuntimeException( "Could not init class.", e ) ;

		}

	}

	static private String generateNagiosActiveMonitors ( ServiceInstance instance ) {

		StringBuilder nagiosHostServiceDefinition = new StringBuilder( ) ;
		ObjectNode monitors = instance.getMonitors( ) ;

		logger.debug( "{} monitor: {}", instance.getName( ), monitors ) ;

		if ( monitors != null && monitors.has( "nagiosCommand" ) ) {

			String command = monitors.get( "nagiosCommand" ).asText( ) ;
			if ( command.indexOf( "-p" ) == -1 )
				command += " -p " + instance.getPort( ) ;

			nagiosHostServiceDefinition.append( "\n#\n#\t Active checks for " + instance.getName( )
					+ "\n#" ) ;
			nagiosHostServiceDefinition.append( "\ndefine service{" ) ;
			nagiosHostServiceDefinition.append( "\n\t use \t\t generic-service" ) ;
			nagiosHostServiceDefinition.append( "\n\t host_name \t " + instance.getHostName( ) ) ;
			nagiosHostServiceDefinition.append( "\n\t service_description \t   _"
					+ instance.getServiceName_Port( ) ) ;
			nagiosHostServiceDefinition.append( "\n\t check_command \t " + command ) ;
			ArrayNode nagiosOptionsFromCsapDefn = getNagiosOptionsFromCsapDefn( ) ;

			if ( nagiosOptionsFromCsapDefn != null && nagiosOptionsFromCsapDefn.size( ) > 0 ) {

				for ( JsonNode configLine : nagiosOptionsFromCsapDefn ) {

					if ( configLine.asText( ).contains( "passive_checks_enabled" ) )
						continue ;
					nagiosHostServiceDefinition.append( "\n\t " + configLine.asText( ) ) ;

				}

			} else {

				nagiosHostServiceDefinition.append( "\n\t active_checks_enabled  \t  1" ) ;

			}

			nagiosHostServiceDefinition.append( "\n\t passive_checks_enabled \t  0" ) ;
			nagiosHostServiceDefinition.append( "\n\t }\n\n" ) ;

		}

		return nagiosHostServiceDefinition.toString( ) ;

	}

	static private ArrayNode getNagiosOptionsFromCsapDefn ( ) {

		ArrayNode nagiosOptionsFromCsapDefn = null ;
		// jacksonMapper.createArrayNode();
		ArrayNode publishConfigJson = ProjectLoader
				.getModelConfiguration( )
				.getMetricsPublicationNode( ) ;

		// logger.info("Using nagios config from def: " +
		// jacksonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(publishConfigJson));
		if ( publishConfigJson != null ) {

			for ( JsonNode item : publishConfigJson ) {

				if ( "nagios".equals( item.get( "type" ).asText( ) ) && item.has( "config" ) ) {

					nagiosOptionsFromCsapDefn = (ArrayNode) item.get( "config" ) ;

					logger.debug( "Using nagios config from def: {}", nagiosOptionsFromCsapDefn ) ;

					break ;

				}

			}

		}

		return nagiosOptionsFromCsapDefn ;

	}

	static public String publishHealthReport (
												ObjectNode definitionNode ,
												CsapApis csapApis ,
												boolean isIntegrationEnabled ) {

		String restUrl = definitionNode.get( "url" ).asText( ) ;

		if ( restUrl.contains( "cstg-ps" ) ) {

			logger.warn( "Hack for CSTG  nagios, server is misconfigured - so ignoring SNI errors" ) ;
			System.setProperty( "jsse.enableSNIExtension", "false" ) ;

		}

		StringBuilder uploadResults = new StringBuilder( "**Uploaded Nagios data: " + restUrl ) ;

		double alertLevel = ServiceAlertsEnum.ALERT_LEVEL ;
		if ( definitionNode.has( "alertLevel" ) )
			alertLevel = definitionNode.get( "alertLevel" ).asDouble( ServiceAlertsEnum.ALERT_LEVEL ) ;
		ObjectNode vmStatus = csapApis.application( ).healthManager( ).statusForAdminOrAgent( alertLevel, false ) ;

		JsonNode servicesNode = vmStatus.get( "services" ) ;
		JsonNode stateNode = null ;

		if ( vmStatus.has( "errors" ) && vmStatus.get( "errors" ).has( "states" ) ) {

			stateNode = vmStatus.get( "errors" ).get( "states" ) ;

		}

		try {

			logger.debug( "vmStatus: {}", vmStatus ) ;
			// next gen: HttpComponentsClientHttpRequestFactory

			// System.setProperty("jsse.enableSNIExtension", "false");

			RestTemplate restTemplate = new RestTemplate( getFactoryDisabledSslChecks( 15000, 15000 ) ) ;
			restTemplate.getMessageConverters( ).clear( ) ;
			restTemplate.getMessageConverters( ).add( new FormHttpMessageConverter( ) ) ;
			restTemplate.getMessageConverters( ).add( new StringHttpMessageConverter( ) ) ;

			MultiValueMap<String, String> formParams = new LinkedMultiValueMap<String, String>( ) ;

			StringBuilder nagiosRequest = new StringBuilder( "<?xml version='1.0'?> <checkresults>" ) ;

			for ( String monitor : MetricsPublisher.getNagiosServices( ) ) {

				String state = "0" ;

				String output = "" ;

				switch ( monitor ) {

				case "memory":
					output += " Free Memory: "
							+ csapApis.osManager( ).getMemoryAvailbleLessCache( ) ;
					;
					break ;

				case "cpuLoad":
					output += " Total CPU: " + csapApis.osManager( ).getHostTotalTopCpu( )
							+ "% OS: "
							+ csapApis.osManager( ).getHostSummary( ).get( "redhat" ).asText( )
							+ " Uptime: "
							+ csapApis.osManager( ).getHostSummary( ).get( "uptime" ).asText( ) ;
					break ;

				case "disk":
					for ( JsonNode item : csapApis.osManager( ).getCachedFileSystemInfo( ) ) {

						output += item.get( "mount" ).asText( ) + ":" + item.get( "usedp" ).asText( )
								+ ",   " ;

					}
					break ;

				case "processes":
					output += " Active: " + servicesNode.get( "active" ) + " Total: "
							+ servicesNode.get( "total" ) ;

					break ;

				default:
					break ;

				}

				if ( stateNode != null && stateNode.has( monitor ) ) {

					output += "\n" + stateNode.get( monitor ).get( "message" ).asText( ) ;
					state = stateNode.get( monitor ).get( "status" ).asText( ) ;

				}

				// Hack for cleaning up the nagios newlines
				if ( output.endsWith( "\n" ) ) {

					output = output.substring( 0, output.length( ) - 1 ) ;

				}

				// nagios has problems with ; we hack by nesting styles
				output = "<a style=\"font-weight: bold\" href=\""
						+ csapApis.application( ).rootProjectEnvSettings( ).getLoadbalancerUrl( )
						+ "\" target=\"_blank\"><span style=\"color:green\"> CSAP Console:  </span></a>"
						+ output + "|" ;

				String hostName = CsapApis.getInstance( ).application( ).getCsapHostName( ) ;
				if ( Application.isRunningOnDesktop( ) )
					hostName = "csap-dev01" ;
				nagiosRequest
						.append(
								nagiosResultTemplate
										.replaceAll( "__HOST__", hostName )
										.replaceAll( "__SERVICE__", "Csap_" + monitor )
										.replaceAll( "__STATE__", state )
										.replaceAll(
												"__OUTPUT__",
												StringEscapeUtils.escapeXml10( output.replaceAll( "\n", "<br>" ) ) ) ) ;

			}

			nagiosRequest.append( "</checkresults>" ) ;

			formParams.add( "XMLDATA", nagiosRequest.toString( ) ) ;

			formParams.add( "cmd", "submitcheck" ) ;
			formParams.add( "token", definitionNode.get( "token" ).asText( ) ) ;

			logger.debug( "Posting to {} , params: {}", restUrl, formParams ) ;

			// logger.info("Posting to " + restUrl + "\n" + formParams);

			if ( Application.isRunningOnDesktop( ) && ! isIntegrationEnabled ) {

				uploadResults.append( "  -  Desktop detected, Skipping Uploaded Metrics" ) ;

			} else {
				// String result = restTemplate.postForObject(restUrl,
				// formParams, String.class);

				// Custom headers make this more challenging
				HttpHeaders headers = createHeaders( definitionNode.get( "user" ).asText( ),
						definitionNode.get( "pass" ).asText( ) ) ;

				HttpEntity<MultiValueMap<String, String>> requestEntity = new HttpEntity<MultiValueMap<String, String>>(
						formParams, headers ) ;

				ResponseEntity<String> response = restTemplate.exchange( restUrl, HttpMethod.POST,
						requestEntity, String.class ) ;

				if ( logger.isDebugEnabled( ) ) {

					uploadResults.append( "\nFull response: " + response.toString( ) + "\n" ) ;

				}

				uploadResults.append( "  -  Http Response: " + response.getStatusCode( ) ) ;

			}

		} catch ( Throwable e ) {

			uploadResults.append( "  -  Warning Failed response: " + e.getMessage( ) ) ;
			logger.error( "Failed to post status ", e ) ;

			if ( e.getMessage( ).contains( "handshake alert" ) ) {

				logger.warn(
						"Found handshake alert. This means nagios server is misconfigured. You can add: -Djsse.enableSNIExtension=false - but this will open security issues. "
								+ " for more info: http://stackoverflow.com/questions/7615645/ssl-handshake-alert-unrecognized-name-error-since-upgrade-to-java-1-7-0" ) ;

			}

		}

		logger.info( uploadResults.toString( ) ) ;

		return uploadResults.toString( ) ;

	}

	/**
	 * WARNING!!! disabling is not a good idea.
	 * 
	 * Support hardcode for ps team
	 * 
	 * Their server is pretty busted. - http://stackoverflow.com/questions/7615645
	 * /ssl-handshake-alert-unrecognized-name-error-since-upgrade-to-java-1-7-0 --
	 * -Djsse.enableSNIExtension=false very ugly workaround
	 * 
	 * @param restTemplate
	 * @throws Exception
	 */
	public static HttpComponentsClientHttpRequestFactory getFactoryDisabledSslChecks (
																						int connectTimeoutMs ,
																						int readTimeoutMs )
		throws Exception {

		SSLContextBuilder builder = new SSLContextBuilder( ) ;
		builder.loadTrustMaterial( null, new TrustSelfSignedStrategy( ) ) ;
		// builder.loadTrustMaterial(null, new TrustStrategy() {
		//
		// @Override
		// public boolean isTrusted(X509Certificate[] chain, String authType)
		// throws CertificateException {
		//
		// return true;
		// }
		// });

		SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(
				builder.build( ), new NoopHostnameVerifier( ) ) ;

		CloseableHttpClient httpClient = HttpClients.custom( ).setSSLSocketFactory(
				sslsf ).build( ) ;

		HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory( ) ;
		factory.setHttpClient( httpClient ) ;
		// factory.getHttpClient().getConnectionManager().getSchemeRegistry().register(scheme);

		factory.setConnectTimeout( connectTimeoutMs ) ;
		factory.setReadTimeout( readTimeoutMs ) ;

		// restTemplate.setRequestFactory(factory);
		return factory ;

	}

	static HttpHeaders createHeaders ( final String username , final String password ) {

		return new HttpHeaders( ) {
			/**
			 * 
			 */
			private static final long serialVersionUID = 1L ;

			{

				String auth = username + ":" + password ;
				byte[] encodedAuth = Base64.encode(
						auth.getBytes( Charset.forName( "US-ASCII" ) ) ) ;
				String authHeader = "Basic " + new String( encodedAuth ) ;
				set( "Authorization", authHeader ) ;

			}
		} ;

	}
}
