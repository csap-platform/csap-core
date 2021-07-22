package org.csap.agent ;

import java.text.MessageFormat ;
import java.util.Map ;
import java.util.regex.Matcher ;

import org.csap.helpers.CSAP ;
import org.springframework.boot.context.properties.ConfigurationProperties ;

@ConfigurationProperties ( CsapCoreService.CONFIGURATION_PREFIX + ".kubernetes" )
public class KubernetesSettings {

	private boolean enabled = false ;

	private boolean dnsLookup = true ;

	private int connectionTimeOutInMs = 1000 ;
	private int maxSessionSeconds = 10 ;

	private int connectionPoolIdleConnections = 6 ;
	private int connectionPoolIdleMinutes = 3 ;

	private String testCredentialUrl = "http://this.is.not.set/os/folderZip?path=.kube/config&token=584t76.b0b7c7r75rbc0ml0&service=kubelet" ;

	private Map<String, String> apiPaths = Map.of( ) ;

	public String getApiPath ( String apiKind , Object... parameters ) {

		var pathTemplate = apiPaths.get( apiKind ) ;
		String apiPath = null ;

		if ( pathTemplate != null ) {

			apiPath = MessageFormat.format(
					pathTemplate,
					parameters ) ;

		}

		return apiPath ;

	}

	public boolean isEnabled ( ) {

		return enabled ;

	}

	private int eventIncreaseLimit = 1 ;

	private int eventMaxBacklog = 300 ;

	public int getEventMaxBacklog ( ) {

		return eventMaxBacklog ;

	}

	public void setEventMaxBacklog ( int eventMaxBacklog ) {

		this.eventMaxBacklog = eventMaxBacklog ;

	}

	public int getEventIncreaseLimit ( ) {

		return eventIncreaseLimit ;

	}

	public void setEventIncreaseLimit ( int eventIncreaseLimit ) {

		this.eventIncreaseLimit = eventIncreaseLimit ;

	}

	public void setEnabled ( boolean enabled ) {

		this.enabled = enabled ;

	}

	private String configFile = System.getProperty( "user.home" ) + "/.kube/config" ;

	public int getConnectionTimeOutInMs ( ) {

		return connectionTimeOutInMs ;

	}

	public void setConnectionTimeOutInMs ( int connectionTimeOutInMs ) {

		this.connectionTimeOutInMs = connectionTimeOutInMs ;

	}

	public String getConfigFile ( ) {

		String target = configFile ;

		if ( configFile.startsWith( "~" ) ) {

			target = target.replaceAll( Matcher.quoteReplacement( "~" ), Matcher.quoteReplacement( System.getProperty(
					"user.home" ) ) ) ;

		}

		return target ;

	}

	public void setConfigFile ( String configFile ) {

		this.configFile = configFile ;

	}

	@Override
	public String toString ( ) {

		return CSAP.buildDescription(
				"KubernetesSettings",
				"enabled", enabled,
				"configFile", configFile,
				"connectionTimeOutInMs", connectionTimeOutInMs,
				"maxSessionSeconds", maxSessionSeconds,
				"connectionPoolIdleConnections", connectionPoolIdleConnections,
				"connectionPoolIdleMinutes", connectionPoolIdleMinutes,
				"testCredentialUrl", testCredentialUrl ) ;

	}

	public boolean isDnsLookup ( ) {

		return dnsLookup ;

	}

	public void setDnsLookup ( boolean dnsLookup ) {

		this.dnsLookup = dnsLookup ;

	}

	public int getMaxSessionSeconds ( ) {

		return maxSessionSeconds ;

	}

	public void setMaxSessionSeconds ( int maxSessionSeconds ) {

		this.maxSessionSeconds = maxSessionSeconds ;

	}

	public String getTestCredentialUrl ( ) {

		return testCredentialUrl ;

	}

	public void setTestCredentialUrl ( String testCredentialUrl ) {

		this.testCredentialUrl = testCredentialUrl ;

	}

	public void setApiPaths ( Map<String, String> apiPaths ) {

		this.apiPaths = apiPaths ;

	}

	public int getConnectionPoolIdleConnections ( ) {

		return connectionPoolIdleConnections ;

	}

	public void setConnectionPoolIdleConnections ( int connectionPoolIdleConnections ) {

		this.connectionPoolIdleConnections = connectionPoolIdleConnections ;

	}

	public int getConnectionPoolIdleMinutes ( ) {

		return connectionPoolIdleMinutes ;

	}

	public void setConnectionPoolIdleMinutes ( int connectionPoolIdleMinutes ) {

		this.connectionPoolIdleMinutes = connectionPoolIdleMinutes ;

	}

}
