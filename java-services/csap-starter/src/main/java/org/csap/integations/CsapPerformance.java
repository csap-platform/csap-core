package org.csap.integations ;

import org.csap.alerts.AlertSettings ;
import org.csap.alerts.AlertsConfiguration ;
import org.csap.alerts.MonitorMbean ;
import org.csap.helpers.CSAP ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.beans.factory.annotation.Autowired ;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty ;
import org.springframework.boot.context.properties.ConfigurationProperties ;
import org.springframework.context.annotation.Configuration ;
import org.springframework.context.annotation.Import ;

import com.fasterxml.jackson.databind.node.ObjectNode ;

@Configuration ( "CsapPerformance" )
@ConditionalOnProperty ( "csap.performance.enabled" )
// @ComponentScan ( "org.csap.alerts" )
@Import ( AlertsConfiguration.class )
@ConfigurationProperties ( prefix = "csap.performance" )
public class CsapPerformance {

	final static Logger logger = LoggerFactory.getLogger( CsapPerformance.class ) ;

	@Autowired
	AlertSettings alertSettings ;

	private String[] monitorUrls = {
			"/", "/security"
	} ;

	public String toString ( ) {

		StringBuilder infoBuilder = new StringBuilder( ) ;
		infoBuilder.append( "\n csap.performance:" ) ;

		infoBuilder.append( CSAP.padLine( "Monitored Beans" ) + "@CsapMonitor" ) ;
		infoBuilder.append( CSAP.padLine( "Monitored urls" ) ) ;
		for ( String url : getMonitorUrls( ) )
			infoBuilder.append( " " + url ) ;

		infoBuilder.append( CSAP.padLine( "CsapPerformance MBean" ) + MonitorMbean.PERFORMANCE_MBEAN ) ;
		infoBuilder.append( CSAP.padLine( "Alert Settings" ) + alertSettings ) ;
		infoBuilder.append( "\n" ) ;

		return infoBuilder.toString( ) ;

	}

	public String[] getMonitorUrls ( ) {

		return monitorUrls ;

	}

	public void setMonitorUrls ( String[] monitorUrls ) {

		this.monitorUrls = monitorUrls ;

	}

	/**
	 *
	 * Enable custom http access rules to be implemented. All instance of
	 * CustomHttpSecurity will be invoked
	 *
	 */
	public interface CustomHealth {

		public boolean isHealthy ( ObjectNode healthReport )
			throws Exception ;

		public String getComponentName ( ) ;

	}

}
