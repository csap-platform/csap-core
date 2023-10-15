package org.csap.alerts ;

import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty ;
import org.springframework.boot.context.properties.EnableConfigurationProperties ;
import org.springframework.context.annotation.ComponentScan ;
import org.springframework.context.annotation.Configuration ;

@Configuration
@ComponentScan
@ConditionalOnProperty ( prefix = "csap.performance" , name = "enabled" )
@EnableConfigurationProperties ( AlertSettings.class )
public class AlertsConfiguration {

	final Logger logger = LoggerFactory.getLogger( getClass( ) ) ;

	AlertSettings alertSettings ;

	public AlertsConfiguration ( AlertSettings alertSettings ) {

		logger.debug( "\n\n\n\n Created Alerts AlertsConfiguration \n\n\n\n" ) ;
		this.alertSettings = alertSettings ;

	}

}
